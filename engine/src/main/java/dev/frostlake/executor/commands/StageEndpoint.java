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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.parser.FrostlakeParser;
import java.util.Locale;

/**
 * The ENDPOINT of a stage on S3-compatible storage, {@code URL = 's3compat://bucket/path/' ENDPOINT = 'host'}, as
 * CREATE STAGE and ALTER STAGE … SET judge it (live-verified):
 *
 * <pre>
 *   s3compat:// location without an ENDPOINT, or with ''   Missing option(s): ENDPOINT
 *   ENDPOINT beside an s3:// or azure:// location           Endpoint cannot be used with storage type S3.
 *   ENDPOINT = 'https://host' | 'a_b.host'                   Endpoint 'https://host' invalid. Expect no scheme
 *                                                            endpoint, for example: my.storage.com
 *   ENDPOINT = 'storage.example.com'                         Endpoint storage.example.com not allowed.
 * </pre>
 *
 * An endpoint is allowed when it is one of the S3-compatible services the account knows, matched by the host's
 * shape and in its own case: AWS ({@code s3.amazonaws.com}, {@code s3.<region>.amazonaws.com},
 * {@code s3-<region>.amazonaws.com}), Wasabi ({@code s3.<region>.wasabisys.com}), MinIO ({@code <name>.min.io}),
 * Cloudflare R2 ({@code <account>.r2.cloudflarestorage.com}), DigitalOcean Spaces
 * ({@code <region>.digitaloceanspaces.com}) and Backblaze B2 ({@code <name>.backblazeb2.com}); a port or a path after
 * the host is not allowed. An internal stage keeps an ENDPOINT it is given without judging it.
 */
final class StageEndpoint {

    /** The property's name. */
    static final String PROPERTY = "ENDPOINT";

    /** The scheme of a location on S3-compatible storage. */
    private static final String S3_COMPATIBLE = "s3compat";

    private StageEndpoint() {
    }

    /**
     * The endpoint a property's value writes: a text as its contents, a word as written.
     *
     * @param value the property's value
     * @return the endpoint
     */
    static String read(final FrostlakeParser.CopyOptionValueContext value) {
        if (value == null) {
            return null;
        }
        if (value.STRING_LITERAL() != null) {
            return SqlStringLiterals.decode(value.STRING_LITERAL().getText());
        }
        return ParseTreeText.getOriginalText(value);
    }

    /**
     * Whether a location is on S3-compatible storage, by its scheme in any case.
     *
     * @param url the location, or null for an internal stage
     * @return whether it is
     */
    static boolean isS3Compatible(final String url) {
        return S3_COMPATIBLE.equals(scheme(url));
    }

    /**
     * Refuses an endpoint a stage at a location cannot take, or the lack of one a location needs.
     *
     * @param endpoint the endpoint written, or null for none
     * @param url      the stage's location, or null for an internal stage
     */
    static void require(final String endpoint, final String url) {
        if (url == null) {
            return;
        }
        final String scheme = scheme(url);
        if (!S3_COMPATIBLE.equals(scheme)) {
            final String storage = storageType(scheme);
            if (endpoint != null && storage != null) {
                throw new RuntimeException(SqlCompilationError.of("Endpoint cannot be used with storage type "
                    + storage + "."));
            }
            return;
        }
        if (endpoint == null || endpoint.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.of("Missing option(s): " + PROPERTY));
        }
        requireAllowed(endpoint);
    }

    /**
     * Refuses an endpoint that is no host or that the account does not allow.
     *
     * @param endpoint the endpoint written
     */
    static void requireAllowed(final String endpoint) {
        if (endpoint.contains("://") || !isHostText(hostOf(endpoint))) {
            throw new RuntimeException(SqlCompilationError.PREFIX + " Endpoint '" + endpoint
                + "' invalid. Expect no scheme endpoint, for example: my.storage.com");
        }
        if (!isAllowed(endpoint)) {
            throw new RuntimeException(SqlCompilationError.of("Endpoint " + endpoint + " not allowed."));
        }
    }

    /** The host an endpoint names: what is before a port or a path. */
    private static String hostOf(final String endpoint) {
        int end = endpoint.length();
        for (int i = 0; i < endpoint.length(); i++) {
            final char c = endpoint.charAt(i);
            if (c == ':' || c == '/') {
                end = i;
                break;
            }
        }
        return endpoint.substring(0, end);
    }

    /** Letters, digits, hyphens and periods, at least one of them. */
    private static boolean isHostText(final String host) {
        if (host.isEmpty()) {
            return false;
        }
        for (int i = 0; i < host.length(); i++) {
            final char c = host.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '-' && c != '.') {
                return false;
            }
        }
        return true;
    }

    /** Whether the endpoint is a host of one of the S3-compatible services the account knows. */
    private static boolean isAllowed(final String endpoint) {
        final String[] labels = endpoint.split("\\.", -1);
        for (final String label : labels) {
            if (label.isEmpty()) {
                return false;
            }
        }
        final int count = labels.length;
        if ("s3.amazonaws.com".equals(endpoint)) {
            return true;
        }
        if (endsWith(labels, "amazonaws", "com") && count >= 3
                && (labels[0].equals("s3") || labels[0].startsWith("s3-"))) {
            return true;
        }
        if (endsWith(labels, "wasabisys", "com")) {
            return count == 4 && labels[0].equals("s3");
        }
        if (endsWith(labels, "min", "io") || endsWith(labels, "digitaloceanspaces", "com")
                || endsWith(labels, "backblazeb2", "com")) {
            return count >= 3;
        }
        return count >= 4 && labels[count - 3].equals("r2") && endsWith(labels, "cloudflarestorage", "com");
    }

    private static boolean endsWith(final String[] labels, final String domain, final String top) {
        return labels.length >= 2 && labels[labels.length - 2].equals(domain)
            && labels[labels.length - 1].equals(top);
    }

    /** The storage type a refusal names for a location's scheme, or null for one it does not judge. */
    private static String storageType(final String scheme) {
        if ("s3".equals(scheme) || "s3gov".equals(scheme) || "s3china".equals(scheme)) {
            return "S3";
        }
        if ("azure".equals(scheme)) {
            return "AZURE";
        }
        if ("gcs".equals(scheme)) {
            return "GCS";
        }
        return null;
    }

    private static String scheme(final String url) {
        if (url == null) {
            return null;
        }
        final int end = url.indexOf("://");
        return end < 0 ? "" : url.substring(0, end).toLowerCase(Locale.ROOT);
    }
}
