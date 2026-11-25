/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.frostlake.aws;

import dev.frostlake.config.S3ObjectSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AwsS3ObjectSource} over a client that answers from memory, so no request leaves the JVM: an object is
 * written to its target, a missing key (NoSuchKey, or a bare 404) is not one, access refused is an error, and a
 * listing follows every page.
 */
public class AwsS3ObjectSourceTest {

    @TempDir
    Path dir;

    /** A client holding one object, {@code b/jars/a.jar}, and listing {@code b/p/} over pages of two keys. */
    private static S3Client client(final List<ListObjectsV2Request> listed) {
        return new S3Client() {
            @Override
            public String serviceName() {
                return "s3";
            }

            @Override
            public void close() {
                // nothing to release
            }

            @Override
            public <T> T getObject(final GetObjectRequest request,
                                   final ResponseTransformer<GetObjectResponse, T> transformer) {
                if ("refused".equals(request.key())) {
                    throw (S3Exception) S3Exception.builder().statusCode(403)
                        .awsErrorDetails(AwsErrorDetails.builder().errorCode("AccessDenied").build())
                        .message("Access Denied").build();
                }
                if ("gone".equals(request.key())) {
                    throw (S3Exception) S3Exception.builder().statusCode(404).message("Not Found").build();
                }
                if (!"b".equals(request.bucket()) || !"jars/a.jar".equals(request.key())) {
                    throw NoSuchKeyException.builder().statusCode(404).message("NoSuchKey").build();
                }
                final byte[] body = "jar bytes".getBytes(StandardCharsets.UTF_8);
                final GetObjectResponse response = GetObjectResponse.builder().contentLength((long) body.length).build();
                try {
                    return transformer.transform(response,
                        AbortableInputStream.create(new ByteArrayInputStream(body)));
                } catch (final Exception e) {
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public ResponseInputStream<GetObjectResponse> getObject(final GetObjectRequest request) {
                throw new UnsupportedOperationException("the source writes objects through a transformer");
            }

            @Override
            public ListObjectsV2Response listObjectsV2(final ListObjectsV2Request request) {
                listed.add(request);
                final List<String> all = Arrays.asList("p/1.csv", "p/2.csv", "p/sub/3.csv", "p/4.csv", "p/5.csv");
                final int from = request.continuationToken() == null ? 0 : Integer.parseInt(request.continuationToken());
                final int to = Math.min(from + 2, all.size());
                final List<S3Object> page = new ArrayList<S3Object>();
                for (int i = from; i < to; i++) {
                    page.add(S3Object.builder().key(all.get(i)).build());
                }
                final ListObjectsV2Response.Builder answer = ListObjectsV2Response.builder().contents(page)
                    .isTruncated(Boolean.valueOf(to < all.size()));
                if (to < all.size()) {
                    answer.nextContinuationToken(String.valueOf(to));
                }
                return answer.build();
            }
        };
    }

    @Test
    public void anObjectIsWrittenToItsTarget() throws IOException {
        final Path target = dir.resolve("a.jar");
        assertTrue(new AwsS3ObjectSource(client(new ArrayList<ListObjectsV2Request>())).fetch("b", "jars/a.jar", target));
        assertEquals("jar bytes", new String(Files.readAllBytes(target), StandardCharsets.UTF_8));
    }

    @Test
    public void aMissingKeyIsNoObject() throws IOException {
        final AwsS3ObjectSource source = new AwsS3ObjectSource(client(new ArrayList<ListObjectsV2Request>()));
        assertFalse(source.fetch("b", "jars/none.jar", dir.resolve("none.jar")));
        assertFalse(source.fetch("b", "gone", dir.resolve("gone")));
        assertFalse(Files.exists(dir.resolve("none.jar")));
    }

    @Test
    public void refusedAccessIsAnError() {
        final AwsS3ObjectSource source = new AwsS3ObjectSource(client(new ArrayList<ListObjectsV2Request>()));
        final IOException refused = assertThrows(IOException.class, new Executable() {
            @Override
            public void execute() throws IOException {
                source.fetch("b", "refused", dir.resolve("refused"));
            }
        });
        assertTrue(refused.getMessage().contains("Access Denied"), refused.getMessage());
    }

    @Test
    public void aListingFollowsEveryPage() throws IOException {
        final List<ListObjectsV2Request> listed = new ArrayList<ListObjectsV2Request>();
        final List<String> keys = new AwsS3ObjectSource(client(listed)).list("b", "p/");
        assertEquals(Arrays.asList("p/1.csv", "p/2.csv", "p/sub/3.csv", "p/4.csv", "p/5.csv"), keys);
        assertEquals(3, listed.size());
        assertEquals("p/", listed.get(0).prefix());
        assertEquals("2", listed.get(1).continuationToken());
    }

    @Test
    public void theModuleRegistersTheSourceForServiceLoader() {
        boolean found = false;
        for (final S3ObjectSource source : ServiceLoader.load(S3ObjectSource.class)) {
            found = found || source instanceof AwsS3ObjectSource;
        }
        assertTrue(found);
    }
}
