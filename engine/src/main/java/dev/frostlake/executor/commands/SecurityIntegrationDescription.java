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

import dev.frostlake.metastore.model.Integration;
import dev.frostlake.metastore.model.PropertyValue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * What DESCRIBE lists for a Snowflake OAuth, an External OAuth, a SAML2 or a SCIM security integration: every
 * property of the type, set or not, each with its type, its value and its default, and beside them what the account
 * generates for the integration — an OAuth client's id and endpoints, a SAML2 service provider's URLs and metadata.
 * The rows are the documented properties of each type, in the order clients of the account read them; none of them
 * is live-verified, since integrations of these types are not created on an account used for verification. The
 * property that names the variant ({@code OAUTH_CLIENT}, {@code SCIM_CLIENT}, {@code EXTERNAL_OAUTH_TYPE}) has no row
 * of its own: SHOW INTEGRATIONS tells it in the type. Frostlake authenticates nobody through an integration, so a
 * generated value is a stand-in of the documented shape.
 */
final class SecurityIntegrationDescription {

    private static final List<String> TYPES = Collections.unmodifiableList(Arrays.asList("OAUTH", "EXTERNAL_OAUTH",
        "SAML2", "SCIM"));

    /** The roles an OAuth integration blocks while the account adds its privileged roles to the list. */
    private static final List<String> PRIVILEGED_ROLES = Collections.unmodifiableList(Arrays.asList("ACCOUNTADMIN",
        "GLOBALORGADMIN", "ORGADMIN", "SECURITYADMIN"));

    private static final String NAMEID_FORMAT = "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress";

    private static final String[] ENABLED = {"ENABLED", "Boolean", "false"};
    private static final String[] NETWORK_POLICY = {"NETWORK_POLICY", "String", ""};

    private static final String[][] SCIM = {ENABLED, NETWORK_POLICY, {"RUN_AS_ROLE", "String", ""},
        {"SYNC_PASSWORD", "Boolean", "false"}};

    private static final String[][] OAUTH_CUSTOM = {ENABLED, {"OAUTH_REDIRECT_URI", "String", ""},
        {"OAUTH_CLIENT_TYPE", "String", "CONFIDENTIAL"}, {"OAUTH_ISSUE_REFRESH_TOKENS", "Boolean", "true"},
        {"OAUTH_REFRESH_TOKEN_VALIDITY", "Integer", "7776000"}, {"OAUTH_ENFORCE_PKCE", "Boolean", "false"},
        {"OAUTH_USE_SECONDARY_ROLES", "String", "NONE"}, {"PRE_AUTHORIZED_ROLES_LIST", "List", "[]", ""},
        {"BLOCKED_ROLES_LIST", "List", "[]", ""}, {"ALLOWED_ROLES_LIST", "List", "[]", ""},
        {"OAUTH_ALLOW_NON_TLS_REDIRECT_URI", "Boolean", "false"}, {"OAUTH_CLIENT_RSA_PUBLIC_KEY_FP", "String", ""},
        {"OAUTH_CLIENT_RSA_PUBLIC_KEY_2_FP", "String", ""}, NETWORK_POLICY,
        {"OAUTH_CLIENT_ID", "String", ""}, {"OAUTH_AUTHORIZATION_ENDPOINT", "String", ""},
        {"OAUTH_TOKEN_ENDPOINT", "String", ""}, {"OAUTH_ALLOWED_AUTHORIZATION_ENDPOINTS", "List", "[]"},
        {"OAUTH_ALLOWED_TOKEN_ENDPOINTS", "List", "[]"}};

    private static final String[][] OAUTH_PARTNER = {ENABLED, {"OAUTH_CLIENT_TYPE", "String", "CONFIDENTIAL"},
        {"OAUTH_ISSUE_REFRESH_TOKENS", "Boolean", "true"}, {"OAUTH_REFRESH_TOKEN_VALIDITY", "Integer", "7776000"},
        {"OAUTH_USE_SECONDARY_ROLES", "String", "NONE"}, {"BLOCKED_ROLES_LIST", "List", "[]", ""},
        {"ALLOWED_ROLES_LIST", "List", "[]", ""}, NETWORK_POLICY, {"OAUTH_CLIENT_ID", "String", ""},
        {"OAUTH_AUTHORIZATION_ENDPOINT", "String", ""}, {"OAUTH_TOKEN_ENDPOINT", "String", ""},
        {"OAUTH_ALLOWED_AUTHORIZATION_ENDPOINTS", "List", "[]"}, {"OAUTH_ALLOWED_TOKEN_ENDPOINTS", "List", "[]"}};

    private static final String[][] EXTERNAL_OAUTH = {ENABLED, {"EXTERNAL_OAUTH_ISSUER", "String", ""},
        {"EXTERNAL_OAUTH_JWS_KEYS_URL", "Object", ""}, {"EXTERNAL_OAUTH_ANY_ROLE_MODE", "String", "DISABLE"},
        {"EXTERNAL_OAUTH_RSA_PUBLIC_KEY", "String", ""}, {"EXTERNAL_OAUTH_RSA_PUBLIC_KEY_2", "String", ""},
        {"EXTERNAL_OAUTH_BLOCKED_ROLES_LIST", "List", "[]", ""},
        {"EXTERNAL_OAUTH_ALLOWED_ROLES_LIST", "List", "[]", ""}, {"EXTERNAL_OAUTH_AUDIENCE_LIST", "List", "[]", ""},
        {"EXTERNAL_OAUTH_TOKEN_USER_MAPPING_CLAIM", "Object", ""},
        {"EXTERNAL_OAUTH_SNOWFLAKE_USER_MAPPING_ATTRIBUTE", "String", ""},
        {"EXTERNAL_OAUTH_SCOPE_DELIMITER", "String", ","}};

    private static final String[][] SAML2 = {{"SAML2_X509_CERT", "String", ""}, {"SAML2_PROVIDER", "String", ""},
        {"SAML2_ENABLE_SP_INITIATED", "Boolean", "false"}, {"SAML2_SP_INITIATED_LOGIN_PAGE_LABEL", "String", ""},
        {"SAML2_SSO_URL", "String", ""}, {"SAML2_ISSUER", "String", ""},
        {"SAML2_REQUESTED_NAMEID_FORMAT", "String", NAMEID_FORMAT}, {"SAML2_FORCE_AUTHN", "Boolean", "false"},
        {"SAML2_POST_LOGOUT_REDIRECT_URL", "String", ""}, {"SAML2_SIGN_REQUEST", "Boolean", "false"},
        {"SAML2_SNOWFLAKE_ACS_URL", "String", ""}, {"SAML2_SNOWFLAKE_ISSUER_URL", "String", ""},
        {"SAML2_SNOWFLAKE_METADATA", "String", ""},
        {"SAML2_DIGEST_METHODS_USED", "String", "", "http://www.w3.org/2001/04/xmlenc#sha256"},
        {"SAML2_SIGNATURE_METHODS_USED", "String", "",
            "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"},
        {"ALLOWED_USER_DOMAINS", "List", "[]"}, {"ALLOWED_EMAIL_PATTERNS", "List", "[]"}};

    private SecurityIntegrationDescription() {
    }

    /** Whether the integration is of a type described here. */
    static boolean covers(final Integration integration) {
        return TYPES.contains(String.valueOf(integration.text("TYPE")));
    }

    /**
     * The rows of the integration's type, in order, each as {name, type, default} and, where a property left out
     * reads as something other than its default, that reading as a fourth cell. The comment follows them.
     */
    static String[][] layout(final Integration integration) {
        final String type = String.valueOf(integration.text("TYPE"));
        if ("SCIM".equals(type)) {
            return SCIM;
        }
        if ("SAML2".equals(type)) {
            return SAML2;
        }
        if ("EXTERNAL_OAUTH".equals(type)) {
            return EXTERNAL_OAUTH;
        }
        if (custom(integration)) {
            return OAUTH_CUSTOM;
        }
        if (integration.property("OAUTH_REDIRECT_URI") == null) {
            return OAUTH_PARTNER;
        }
        // A partner application reached at a URI of its own lists it where a custom client's is listed.
        final String[][] rows = new String[OAUTH_PARTNER.length + 1][];
        rows[0] = OAUTH_PARTNER[0];
        rows[1] = OAUTH_CUSTOM[1];
        System.arraycopy(OAUTH_PARTNER, 1, rows, 2, OAUTH_PARTNER.length - 1);
        return rows;
    }

    /** The properties the integration was written with that have no row of their own. */
    static List<String> unlisted(final Integration integration) {
        final String type = String.valueOf(integration.text("TYPE"));
        if ("SCIM".equals(type)) {
            return Collections.singletonList("SCIM_CLIENT");
        }
        if ("EXTERNAL_OAUTH".equals(type)) {
            return Collections.singletonList("EXTERNAL_OAUTH_TYPE");
        }
        if ("OAUTH".equals(type)) {
            // A client's key is told by its fingerprint.
            return Arrays.asList("OAUTH_CLIENT", "OAUTH_CLIENT_RSA_PUBLIC_KEY", "OAUTH_CLIENT_RSA_PUBLIC_KEY_2");
        }
        return Collections.emptyList();
    }

    /**
     * The value of one row.
     *
     * @param integration       the integration
     * @param key               the row's property
     * @param accountUrl        the account's URL
     * @param locatorUrl        the account's URL by its locator
     * @param privilegedBlocked whether the account adds its privileged roles to a blocked roles list
     * @return the value, never null
     */
    static String value(final Integration integration, final String key, final String accountUrl,
                        final String locatorUrl, final boolean privilegedBlocked) {
        final PropertyValue written = integration.property(key);
        if ("BLOCKED_ROLES_LIST".equals(key) || "EXTERNAL_OAUTH_BLOCKED_ROLES_LIST".equals(key)) {
            return blockedRoles(written, privilegedBlocked);
        }
        if ("OAUTH_CLIENT_RSA_PUBLIC_KEY_FP".equals(key)) {
            return fingerprint(integration.text("OAUTH_CLIENT_RSA_PUBLIC_KEY"));
        }
        if ("OAUTH_CLIENT_RSA_PUBLIC_KEY_2_FP".equals(key)) {
            return fingerprint(integration.text("OAUTH_CLIENT_RSA_PUBLIC_KEY_2"));
        }
        if ("SAML2_SNOWFLAKE_METADATA".equals(key)) {
            return metadata(integration, accountUrl);
        }
        if (written == null) {
            return unset(integration, key, accountUrl, locatorUrl);
        }
        if ("RUN_AS_ROLE".equals(key) || "NETWORK_POLICY".equals(key)) {
            return objectName(written.getText());
        }
        if ("EXTERNAL_OAUTH_TOKEN_USER_MAPPING_CLAIM".equals(key)) {
            return quotedList(written.strings());
        }
        if ("ALLOWED_USER_DOMAINS".equals(key) || "ALLOWED_EMAIL_PATTERNS".equals(key)) {
            return written.strings().toString();
        }
        return written.describe();
    }

    /** What a property left out reads as: what the account generates for it, else its row's own reading. */
    private static String unset(final Integration integration, final String key, final String accountUrl,
                                final String locatorUrl) {
        if ("OAUTH_CLIENT_TYPE".equals(key)) {
            return "TABLEAU_DESKTOP".equals(integration.text("OAUTH_CLIENT")) ? "PUBLIC" : "CONFIDENTIAL";
        }
        if ("OAUTH_CLIENT_ID".equals(key)) {
            return clientId(integration);
        }
        if ("OAUTH_AUTHORIZATION_ENDPOINT".equals(key)) {
            return accountUrl + "/oauth/authorize";
        }
        if ("OAUTH_TOKEN_ENDPOINT".equals(key)) {
            return accountUrl + "/oauth/token-request";
        }
        if ("OAUTH_ALLOWED_AUTHORIZATION_ENDPOINTS".equals(key)) {
            return accountUrl + "/oauth/authorize," + locatorUrl + "/oauth/authorize";
        }
        if ("OAUTH_ALLOWED_TOKEN_ENDPOINTS".equals(key)) {
            return accountUrl + "/oauth/token-request," + locatorUrl + "/oauth/token-request";
        }
        if ("SAML2_SNOWFLAKE_ACS_URL".equals(key)) {
            return accountUrl + "/fed/login";
        }
        if ("SAML2_SNOWFLAKE_ISSUER_URL".equals(key)) {
            return accountUrl;
        }
        for (final String[] row : layout(integration)) {
            if (row[0].equals(key)) {
                return row[row.length - 1];
            }
        }
        return "";
    }

    private static boolean custom(final Integration integration) {
        return "CUSTOM".equals(integration.text("OAUTH_CLIENT"));
    }

    /** The roles written, behind the privileged roles the account blocks of its own accord. */
    private static String blockedRoles(final PropertyValue written, final boolean privilegedBlocked) {
        final List<String> roles = new ArrayList<>();
        if (privilegedBlocked) {
            roles.addAll(PRIVILEGED_ROLES);
        }
        if (written != null) {
            for (final String role : written.strings()) {
                if (!roles.contains(role)) {
                    roles.add(role);
                }
            }
        }
        return String.join(",", roles);
    }

    /** A role's or a network policy's name as the account keeps it: a quoted one as quoted, any other in capitals. */
    private static String objectName(final String text) {
        if (text == null) {
            return "";
        }
        if (text.length() >= 2 && text.charAt(0) == '"' && text.charAt(text.length() - 1) == '"') {
            return text.substring(1, text.length() - 1).replace("\"\"", "\"");
        }
        return text.toUpperCase(Locale.ROOT);
    }

    private static String quotedList(final List<String> items) {
        final StringBuilder out = new StringBuilder("[");
        for (final String item : items) {
            if (out.length() > 1) {
                out.append(',');
            }
            out.append('\'').append(item).append('\'');
        }
        return out.append(']').toString();
    }

    /** The id the account generates for an OAuth client; a partner application is its own. */
    private static String clientId(final Integration integration) {
        if (!custom(integration)) {
            return String.valueOf(integration.text("OAUTH_CLIENT"));
        }
        final String seed = integration.getIdentitySeed();
        return Base64.getEncoder().encodeToString(digest("SHA-1",
            ("OAUTH CLIENT:" + integration.getName() + (seed == null ? "" : ':' + seed))
                .getBytes(StandardCharsets.UTF_8)));
    }

    /** A public key's fingerprint, {@code SHA256:} and the digest of the key; empty for no key. */
    private static String fingerprint(final String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(key);
        } catch (final IllegalArgumentException notBase64) {
            bytes = key.getBytes(StandardCharsets.UTF_8);
        }
        return "SHA256:" + Base64.getEncoder().encodeToString(digest("SHA-256", bytes));
    }

    private static byte[] digest(final String algorithm, final byte[] bytes) {
        try {
            return MessageDigest.getInstance(algorithm).digest(bytes);
        } catch (final NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }

    /** The service provider's metadata an identity provider is configured with. */
    private static String metadata(final Integration integration, final String accountUrl) {
        final String issuer = written(integration, "SAML2_SNOWFLAKE_ISSUER_URL", accountUrl);
        final String consumer = written(integration, "SAML2_SNOWFLAKE_ACS_URL", accountUrl + "/fed/login");
        final String format = written(integration, "SAML2_REQUESTED_NAMEID_FORMAT", NAMEID_FORMAT);
        final PropertyValue signs = integration.property("SAML2_SIGN_REQUEST");
        final boolean signed = signs != null && Boolean.TRUE.equals(signs.flag());
        return "<md:EntityDescriptor entityID=\"" + xml(issuer)
            + "\" xmlns:md=\"urn:oasis:names:tc:SAML:2.0:metadata\"><md:SPSSODescriptor AuthnRequestsSigned=\""
            + signed + "\" protocolSupportEnumeration=\"urn:oasis:names:tc:SAML:2.0:protocol\"><md:NameIDFormat>"
            + xml(format) + "</md:NameIDFormat><md:AssertionConsumerService"
            + " Binding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\" Location=\"" + xml(consumer)
            + "\" index=\"0\" isDefault=\"true\"/></md:SPSSODescriptor></md:EntityDescriptor>";
    }

    private static String written(final Integration integration, final String key, final String otherwise) {
        final String text = integration.text(key);
        return text == null ? otherwise : text;
    }

    private static String xml(final String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
