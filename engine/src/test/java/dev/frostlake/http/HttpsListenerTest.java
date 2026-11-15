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
package dev.frostlake.http;

import dev.frostlake.LiveSnowflake;
import dev.frostlake.config.EngineConfig;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * The HTTPS listener {@code http.tls.port} opens with the keystore {@code http.tls.keystore} names serves every
 * endpoint the plain listener serves: a client that trusts the server's certificate reaches the health check and the
 * Snowflake driver's login over TLS. Engine only: the server under test is the local one.
 */
public class HttpsListenerTest {

    private static final Logger logger = LoggerFactory.getLogger(HttpsListenerTest.class);
    private static final String STORE_PASSWORD = "frostlake";

    @Test
    public void theTlsListenerServesTheSameEndpoints() throws Exception {
        assumeFalse(LiveSnowflake.enabled(), "the server under test is the local one");
        final Path work = Files.createTempDirectory("fl-tls");
        try {
            final Path keystore = work.resolve("server.p12");
            final Process keytool = new ProcessBuilder(
                Paths.get(System.getProperty("java.home"), "bin", "keytool").toString(), "-genkeypair", "-alias",
                "frostlake", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2", "-dname", "CN=localhost",
                "-ext", "SAN=ip:127.0.0.1,dns:localhost", "-keystore", keystore.toString(), "-storetype", "PKCS12",
                "-storepass", STORE_PASSWORD, "-keypass", STORE_PASSWORD).redirectErrorStream(true).start();
            keytool.getInputStream().readAllBytes();
            assertEquals(0, keytool.waitFor());
            final EngineConfig config = new EngineConfig();
            config.setProperty(EngineConfig.PROP_HTTP_PORT, String.valueOf(freePort()));
            config.setProperty(EngineConfig.PROP_HTTP_TLS_PORT, String.valueOf(freePort()));
            config.setProperty(EngineConfig.PROP_HTTP_TLS_KEYSTORE, keystore.toString());
            config.setProperty(EngineConfig.PROP_HTTP_TLS_KEYSTORE_PASSWORD, STORE_PASSWORD);
            final DatabaseHttpServer server = new DatabaseHttpServer(config);
            server.start();
            try {
                final SSLContext trusting = trusting(keystore);
                final String base = "https://127.0.0.1:" + server.getTlsPort();
                final HttpsURLConnection health = open(base + "/api/health", trusting);
                assertEquals(200, health.getResponseCode());
                final HttpsURLConnection login = open(base + "/session/v1/login-request", trusting);
                login.setRequestMethod("POST");
                login.setDoOutput(true);
                login.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = login.getOutputStream()) {
                    out.write("{\"data\": {\"LOGIN_NAME\": \"any_user\"}}".getBytes(StandardCharsets.UTF_8));
                }
                assertEquals(200, login.getResponseCode());
                try (InputStream in = login.getInputStream()) {
                    final String answer = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    assertTrue(answer.contains("\"success\":true"), answer);
                    assertTrue(answer.contains("\"token\""), answer);
                }
            } finally {
                server.stop();
            }
        } finally {
            deleteRecursively(work.toFile());
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** A TLS context that trusts the certificate a keystore holds. */
    private static SSLContext trusting(final Path keystore) throws Exception {
        final KeyStore trust = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(keystore)) {
            trust.load(in, STORE_PASSWORD.toCharArray());
        }
        final TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(trust);
        final SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, factory.getTrustManagers(), null);
        return context;
    }

    private static HttpsURLConnection open(final String url, final SSLContext context) throws Exception {
        final HttpsURLConnection connection = (HttpsURLConnection) URI.create(url).toURL().openConnection();
        connection.setSSLSocketFactory(context.getSocketFactory());
        return connection;
    }

    private static void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            logger.debug("Could not delete {}", file);
        }
    }
}
