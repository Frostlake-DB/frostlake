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

import dev.frostlake.config.EngineConfig;
import dev.frostlake.config.S3ObjectSource;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The AWS SDK {@link S3ObjectSource}: reads objects with an {@link S3Client} on the SDK's default credentials chain, so
 * environment keys, a profile, a container role or an IRSA web identity all work as they do for any SDK client.
 * {@code stage.s3.region} names the region (otherwise the SDK's default region chain decides) and
 * {@code stage.s3.endpoint} an S3-compatible endpoint, addressed path-style. The client is made on the first read.
 */
public class AwsS3ObjectSource implements S3ObjectSource {

    private static final int NOT_FOUND = 404;

    private S3Client client;
    private String region = "";
    private String endpoint = "";

    /** For ServiceLoader: the client is made from the engine's settings on the first read. */
    public AwsS3ObjectSource() {
        // configured by configure(EngineConfig)
    }

    /**
     * Reads through a client made elsewhere, for tests and for callers that build their own.
     *
     * @param client the client
     */
    public AwsS3ObjectSource(final S3Client client) {
        this.client = client;
    }

    @Override
    public void configure(final EngineConfig config) {
        this.region = config.getStageS3Region().trim();
        this.endpoint = config.getStageS3Endpoint().trim();
    }

    private synchronized S3Client client() {
        if (client == null) {
            final S3ClientBuilder builder = S3Client.builder();
            if (!region.isEmpty()) {
                builder.region(Region.of(region));
            }
            if (!endpoint.isEmpty()) {
                builder.endpointOverride(URI.create(endpoint)).forcePathStyle(Boolean.TRUE);
            }
            client = builder.build();
        }
        return client;
    }

    @Override
    public boolean fetch(final String bucket, final String key, final Path target) throws IOException {
        final GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key).build();
        try {
            client().getObject(request, ResponseTransformer.toFile(target));
            return true;
        } catch (final NoSuchKeyException missing) {
            return false;
        } catch (final S3Exception refused) {
            if (refused.statusCode() == NOT_FOUND) {
                return false;
            }
            throw new IOException("s3://" + bucket + "/" + key + ": " + refused.getMessage(), refused);
        } catch (final SdkException failed) {
            throw new IOException("s3://" + bucket + "/" + key + ": " + failed.getMessage(), failed);
        }
    }

    @Override
    public List<String> list(final String bucket, final String prefix) throws IOException {
        final List<String> keys = new ArrayList<String>();
        String continuation = null;
        try {
            do {
                final ListObjectsV2Request.Builder request = ListObjectsV2Request.builder().bucket(bucket).prefix(prefix);
                if (continuation != null) {
                    request.continuationToken(continuation);
                }
                final ListObjectsV2Response page = client().listObjectsV2(request.build());
                for (final S3Object object : page.contents()) {
                    keys.add(object.key());
                }
                continuation = Boolean.TRUE.equals(page.isTruncated()) ? page.nextContinuationToken() : null;
            } while (continuation != null);
        } catch (final SdkException failed) {
            throw new IOException("s3://" + bucket + "/" + prefix + ": " + failed.getMessage(), failed);
        }
        return keys;
    }
}
