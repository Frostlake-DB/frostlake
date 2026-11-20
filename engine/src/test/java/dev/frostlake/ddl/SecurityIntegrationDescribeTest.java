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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * DESCRIBE of a Snowflake OAuth, an External OAuth, a SAML2 and a SCIM security integration: every property of the
 * type has a row — set or not — with its type, its value and its default, each a text and never NULL, and the
 * property naming the variant has none. The rows are the documented properties of each type and the ones clients of
 * the account read; they are not live-verified. Engine only: integrations are not created on the live account.
 */
public class SecurityIntegrationDescribeTest extends BaseDatabaseTest {

    private static final String ACCOUNT = "https://abcorg-abc12345.snowflakecomputing.com";
    private static final String LOCATOR = "https://abc12345.us-east-1.snowflakecomputing.com";
    private static final String PRIVILEGED = "ACCOUNTADMIN,GLOBALORGADMIN,ORGADMIN,SECURITYADMIN";

    @Override
    protected void setupTest() {
        assumeFalse(isLiveSnowflake(), "integrations are not created on the live account");
    }

    /** DESCRIBE's rows, each as {@code property|type|value|default}; no cell is NULL. */
    private List<String> described(final String name) {
        final ResultSet rs = engine.executeQuery("DESCRIBE SECURITY INTEGRATION " + name);
        assertEquals(4, rs.getColumns().size());
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            for (int i = 0; i < 4; i++) {
                assertNotNull(row.getValue(i), "cell " + i + " of " + row.getValue(0));
            }
            out.add(row.getValue(0) + "|" + row.getValue(1) + "|" + row.getValue(2) + "|" + row.getValue(3));
        }
        return out;
    }

    /** The value of one row. */
    private String value(final String name, final String property) {
        for (final String row : described(name)) {
            final String[] cells = row.split("\\|", -1);
            if (cells[0].equals(property)) {
                return cells[2];
            }
        }
        throw new AssertionError("no row " + property);
    }

    @Test
    public void aScimIntegrationListsItsNetworkPolicyAndPasswordSyncUnset() {
        engine.execute("CREATE SECURITY INTEGRATION sd_scim TYPE = SCIM ENABLED = TRUE SCIM_CLIENT = 'GENERIC'"
            + " RUN_AS_ROLE = 'GENERIC_SCIM_PROVISIONER'");
        assertEquals(Arrays.asList(
            "ENABLED|Boolean|true|false",
            "NETWORK_POLICY|String||",
            "RUN_AS_ROLE|String|GENERIC_SCIM_PROVISIONER|",
            "SYNC_PASSWORD|Boolean|false|false",
            "COMMENT|String||"), described("sd_scim"));
        engine.execute("""
            ALTER SECURITY INTEGRATION sd_scim SET SYNC_PASSWORD = TRUE NETWORK_POLICY = '"my np"' COMMENT = 'c'""");
        assertEquals(Arrays.asList(
            "ENABLED|Boolean|true|false",
            "NETWORK_POLICY|String|my np|",
            "RUN_AS_ROLE|String|GENERIC_SCIM_PROVISIONER|",
            "SYNC_PASSWORD|Boolean|true|false",
            "COMMENT|String|c|"), described("sd_scim"));
    }

    @Test
    public void aCustomOauthClientListsEveryPropertyAndWhatTheAccountGenerates() {
        engine.execute("CREATE SECURITY INTEGRATION sd_cust TYPE = OAUTH OAUTH_CLIENT = CUSTOM"
            + " OAUTH_CLIENT_TYPE = 'PUBLIC' OAUTH_REDIRECT_URI = 'https://example.com/cb'");
        final List<String> rows = described("sd_cust");
        final String clientId = value("sd_cust", "OAUTH_CLIENT_ID");
        assertFalse(clientId.isEmpty(), "a client id is generated");
        assertEquals(Arrays.asList(
            "ENABLED|Boolean|true|false",
            "OAUTH_REDIRECT_URI|String|https://example.com/cb|",
            "OAUTH_CLIENT_TYPE|String|PUBLIC|CONFIDENTIAL",
            "OAUTH_ISSUE_REFRESH_TOKENS|Boolean|true|true",
            "OAUTH_REFRESH_TOKEN_VALIDITY|Integer|7776000|7776000",
            "OAUTH_ENFORCE_PKCE|Boolean|false|false",
            "OAUTH_USE_SECONDARY_ROLES|String|NONE|NONE",
            "PRE_AUTHORIZED_ROLES_LIST|List||[]",
            "BLOCKED_ROLES_LIST|List|" + PRIVILEGED + "|[]",
            "ALLOWED_ROLES_LIST|List||[]",
            "OAUTH_ALLOW_NON_TLS_REDIRECT_URI|Boolean|false|false",
            "OAUTH_CLIENT_RSA_PUBLIC_KEY_FP|String||",
            "OAUTH_CLIENT_RSA_PUBLIC_KEY_2_FP|String||",
            "NETWORK_POLICY|String||",
            "OAUTH_CLIENT_ID|String|" + clientId + "|",
            "OAUTH_AUTHORIZATION_ENDPOINT|String|" + ACCOUNT + "/oauth/authorize|",
            "OAUTH_TOKEN_ENDPOINT|String|" + ACCOUNT + "/oauth/token-request|",
            "OAUTH_ALLOWED_AUTHORIZATION_ENDPOINTS|List|" + ACCOUNT + "/oauth/authorize," + LOCATOR
                + "/oauth/authorize|[]",
            "OAUTH_ALLOWED_TOKEN_ENDPOINTS|List|" + ACCOUNT + "/oauth/token-request," + LOCATOR
                + "/oauth/token-request|[]",
            "COMMENT|String||"), rows);
        // The id stays the client's through an ALTER and is another's once the integration is created again.
        engine.execute("ALTER SECURITY INTEGRATION sd_cust SET COMMENT = 'k'");
        assertEquals(clientId, value("sd_cust", "OAUTH_CLIENT_ID"));
    }

    @Test
    public void aCustomOauthClientShowsWhatItWasWrittenWith() {
        engine.execute("CREATE SECURITY INTEGRATION sd_set TYPE = OAUTH OAUTH_CLIENT = CUSTOM"
            + " OAUTH_CLIENT_TYPE = 'CONFIDENTIAL' OAUTH_REDIRECT_URI = 'https://example.com/cb' ENABLED = FALSE"
            + " OAUTH_ISSUE_REFRESH_TOKENS = FALSE OAUTH_REFRESH_TOKEN_VALIDITY = 86400 OAUTH_ENFORCE_PKCE = TRUE"
            + " OAUTH_USE_SECONDARY_ROLES = IMPLICIT PRE_AUTHORIZED_ROLES_LIST = ('R1', 'R2')"
            + " BLOCKED_ROLES_LIST = ('SYSADMIN') OAUTH_CLIENT_RSA_PUBLIC_KEY = 'MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A'"
            + " COMMENT = 'k'");
        final List<String> rows = described("sd_set");
        assertEquals(20, rows.size());
        assertEquals("ENABLED|Boolean|false|false", rows.get(0));
        assertEquals("OAUTH_CLIENT_TYPE|String|CONFIDENTIAL|CONFIDENTIAL", rows.get(2));
        assertEquals("OAUTH_ISSUE_REFRESH_TOKENS|Boolean|false|true", rows.get(3));
        assertEquals("OAUTH_REFRESH_TOKEN_VALIDITY|Integer|86400|7776000", rows.get(4));
        assertEquals("OAUTH_ENFORCE_PKCE|Boolean|true|false", rows.get(5));
        assertEquals("OAUTH_USE_SECONDARY_ROLES|String|IMPLICIT|NONE", rows.get(6));
        assertEquals("PRE_AUTHORIZED_ROLES_LIST|List|R1,R2|[]", rows.get(7));
        assertEquals("BLOCKED_ROLES_LIST|List|" + PRIVILEGED + ",SYSADMIN|[]", rows.get(8));
        // A key is told by its fingerprint, never as written.
        assertTrue(rows.get(11).startsWith("OAUTH_CLIENT_RSA_PUBLIC_KEY_FP|String|SHA256:"), rows.get(11));
        assertFalse(rows.toString().contains("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A"));
        assertEquals("COMMENT|String|k|", rows.get(19));
        // The account's privileged roles leave the list with the parameter that adds them.
        engine.execute("ALTER ACCOUNT SET OAUTH_ADD_PRIVILEGED_ROLES_TO_BLOCKED_LIST = FALSE");
        try {
            assertEquals("SYSADMIN", value("sd_set", "BLOCKED_ROLES_LIST"));
        } finally {
            engine.execute("ALTER ACCOUNT UNSET OAUTH_ADD_PRIVILEGED_ROLES_TO_BLOCKED_LIST");
        }
        // A client created again is given another id.
        final String first = value("sd_set", "OAUTH_CLIENT_ID");
        engine.execute("CREATE OR REPLACE SECURITY INTEGRATION sd_set TYPE = OAUTH OAUTH_CLIENT = CUSTOM"
            + " OAUTH_CLIENT_TYPE = 'CONFIDENTIAL' OAUTH_REDIRECT_URI = 'https://example.com/cb'");
        assertNotEquals(first, value("sd_set", "OAUTH_CLIENT_ID"));
    }

    @Test
    public void aPartnerApplicationListsItsOwnProperties() {
        engine.execute("CREATE SECURITY INTEGRATION sd_tab TYPE = OAUTH OAUTH_CLIENT = TABLEAU_DESKTOP");
        assertEquals(Arrays.asList(
            "ENABLED|Boolean|true|false",
            "OAUTH_CLIENT_TYPE|String|PUBLIC|CONFIDENTIAL",
            "OAUTH_ISSUE_REFRESH_TOKENS|Boolean|true|true",
            "OAUTH_REFRESH_TOKEN_VALIDITY|Integer|7776000|7776000",
            "OAUTH_USE_SECONDARY_ROLES|String|NONE|NONE",
            "BLOCKED_ROLES_LIST|List|" + PRIVILEGED + "|[]",
            "ALLOWED_ROLES_LIST|List||[]",
            "NETWORK_POLICY|String||",
            "OAUTH_CLIENT_ID|String|TABLEAU_DESKTOP|",
            "OAUTH_AUTHORIZATION_ENDPOINT|String|" + ACCOUNT + "/oauth/authorize|",
            "OAUTH_TOKEN_ENDPOINT|String|" + ACCOUNT + "/oauth/token-request|",
            "OAUTH_ALLOWED_AUTHORIZATION_ENDPOINTS|List|" + ACCOUNT + "/oauth/authorize," + LOCATOR
                + "/oauth/authorize|[]",
            "OAUTH_ALLOWED_TOKEN_ENDPOINTS|List|" + ACCOUNT + "/oauth/token-request," + LOCATOR
                + "/oauth/token-request|[]",
            "COMMENT|String||"), described("sd_tab"));
        // One reached at a URI of its own lists the URI after ENABLED.
        engine.execute("CREATE SECURITY INTEGRATION sd_look TYPE = OAUTH OAUTH_CLIENT = LOOKER"
            + " OAUTH_REDIRECT_URI = 'https://looker.example.com/cb'");
        final List<String> looker = described("sd_look");
        assertEquals(15, looker.size());
        assertEquals("OAUTH_REDIRECT_URI|String|https://looker.example.com/cb|", looker.get(1));
        assertEquals("OAUTH_CLIENT_TYPE|String|CONFIDENTIAL|CONFIDENTIAL", looker.get(2));
        assertEquals("OAUTH_CLIENT_ID|String|LOOKER|", looker.get(9));
    }

    @Test
    public void anExternalOauthIntegrationListsItsServersProperties() {
        engine.execute("CREATE SECURITY INTEGRATION sd_ext TYPE = EXTERNAL_OAUTH ENABLED = TRUE"
            + " EXTERNAL_OAUTH_TYPE = OKTA EXTERNAL_OAUTH_ISSUER = 'https://okta.example.com'"
            + " EXTERNAL_OAUTH_TOKEN_USER_MAPPING_CLAIM = ('sub', 'upn')"
            + " EXTERNAL_OAUTH_SNOWFLAKE_USER_MAPPING_ATTRIBUTE = 'LOGIN_NAME'"
            + " EXTERNAL_OAUTH_JWS_KEYS_URL = 'https://okta.example.com/keys'"
            + " EXTERNAL_OAUTH_AUDIENCE_LIST = ('https://a', 'https://b')");
        assertEquals(Arrays.asList(
            "ENABLED|Boolean|true|false",
            "EXTERNAL_OAUTH_ISSUER|String|https://okta.example.com|",
            "EXTERNAL_OAUTH_JWS_KEYS_URL|Object|https://okta.example.com/keys|",
            "EXTERNAL_OAUTH_ANY_ROLE_MODE|String|DISABLE|DISABLE",
            "EXTERNAL_OAUTH_RSA_PUBLIC_KEY|String||",
            "EXTERNAL_OAUTH_RSA_PUBLIC_KEY_2|String||",
            "EXTERNAL_OAUTH_BLOCKED_ROLES_LIST|List|" + PRIVILEGED + "|[]",
            "EXTERNAL_OAUTH_ALLOWED_ROLES_LIST|List||[]",
            "EXTERNAL_OAUTH_AUDIENCE_LIST|List|https://a,https://b|[]",
            "EXTERNAL_OAUTH_TOKEN_USER_MAPPING_CLAIM|Object|['sub','upn']|",
            "EXTERNAL_OAUTH_SNOWFLAKE_USER_MAPPING_ATTRIBUTE|String|LOGIN_NAME|",
            "EXTERNAL_OAUTH_SCOPE_DELIMITER|String|,|,",
            "COMMENT|String||"), described("sd_ext"));
        engine.execute("ALTER ACCOUNT SET EXTERNAL_OAUTH_ADD_PRIVILEGED_ROLES_TO_BLOCKED_LIST = FALSE");
        try {
            assertEquals("", value("sd_ext", "EXTERNAL_OAUTH_BLOCKED_ROLES_LIST"));
        } finally {
            engine.execute("ALTER ACCOUNT UNSET EXTERNAL_OAUTH_ADD_PRIVILEGED_ROLES_TO_BLOCKED_LIST");
        }
    }

    @Test
    public void aSaml2IntegrationListsTheIdentityProviderAndTheServiceProvider() {
        engine.execute("CREATE SECURITY INTEGRATION sd_saml TYPE = SAML2 ENABLED = TRUE SAML2_ISSUER = 'https://idp'"
            + " SAML2_SSO_URL = 'https://idp/sso' SAML2_PROVIDER = 'OKTA' SAML2_X509_CERT = 'MIIC'"
            + " ALLOWED_USER_DOMAINS = ('a.com', 'b.com') SAML2_FORCE_AUTHN = TRUE");
        final String format = "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress";
        final List<String> rows = described("sd_saml");
        final String metadata = value("sd_saml", "SAML2_SNOWFLAKE_METADATA");
        assertTrue(metadata.startsWith("<md:EntityDescriptor entityID=\"" + ACCOUNT + "\""), metadata);
        assertTrue(metadata.contains("Location=\"" + ACCOUNT + "/fed/login\""), metadata);
        assertEquals(Arrays.asList(
            "SAML2_X509_CERT|String|MIIC|",
            "SAML2_PROVIDER|String|OKTA|",
            "SAML2_ENABLE_SP_INITIATED|Boolean|false|false",
            "SAML2_SP_INITIATED_LOGIN_PAGE_LABEL|String||",
            "SAML2_SSO_URL|String|https://idp/sso|",
            "SAML2_ISSUER|String|https://idp|",
            "SAML2_REQUESTED_NAMEID_FORMAT|String|" + format + "|" + format,
            "SAML2_FORCE_AUTHN|Boolean|true|false",
            "SAML2_POST_LOGOUT_REDIRECT_URL|String||",
            "SAML2_SIGN_REQUEST|Boolean|false|false",
            "SAML2_SNOWFLAKE_ACS_URL|String|" + ACCOUNT + "/fed/login|",
            "SAML2_SNOWFLAKE_ISSUER_URL|String|" + ACCOUNT + "|",
            "SAML2_SNOWFLAKE_METADATA|String|" + metadata + "|",
            "SAML2_DIGEST_METHODS_USED|String|http://www.w3.org/2001/04/xmlenc#sha256|",
            "SAML2_SIGNATURE_METHODS_USED|String|http://www.w3.org/2001/04/xmldsig-more#rsa-sha256|",
            "ALLOWED_USER_DOMAINS|List|[a.com, b.com]|[]",
            "ALLOWED_EMAIL_PATTERNS|List|[]|[]",
            "COMMENT|String||"), rows);
    }
}
