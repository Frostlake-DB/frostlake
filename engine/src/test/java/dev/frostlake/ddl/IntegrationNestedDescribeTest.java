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
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * DESCRIBE of the integrations that hold a nested property list — a REST catalog's REST_CONFIG and
 * REST_AUTHENTICATION, an API integration's API_USER_AUTHENTICATION — and the two statements that write one: an
 * external MCP server's API integration and a JWT bearer flow's security integration. A nested list is one row, its
 * value the members as {@code {NAME=value, LIST=[a, b]}} without any secret and with what the service fills in for a
 * member left out. The rows follow the statements' documentation and the form the Snowflake Terraform provider reads
 * them in; they are not a measurement. Engine only: integrations are not created on the live account.
 */
public class IntegrationNestedDescribeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        assumeFalse(isLiveSnowflake(), "integrations are not created on the live account");
    }

    /** DESCRIBE's rows, each as {@code property|type|value|default}; no cell is NULL. */
    private List<String> described(final String kind, final String name) {
        final ResultSet rs = engine.executeQuery("DESCRIBE " + kind + " INTEGRATION " + name);
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
    private String value(final String kind, final String name, final String property) {
        for (final String row : described(kind, name)) {
            final String[] cells = row.split("\\|", -1);
            if (cells[0].equals(property)) {
                return cells[2];
            }
        }
        throw new AssertionError("no row " + property);
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    @Test
    public void anOpenCatalogTellsItsDefaultsAndNoSecret() {
        engine.executeQuery("CREATE CATALOG INTEGRATION nd_open CATALOG_SOURCE = POLARIS TABLE_FORMAT = ICEBERG"
            + " CATALOG_NAMESPACE = 'ns' REST_CONFIG = (CATALOG_URI = 'https://o-a.example.com/polaris/api/catalog'"
            + " CATALOG_NAME = 'mycat') REST_AUTHENTICATION = (TYPE = OAUTH OAUTH_CLIENT_ID = 'cid'"
            + " OAUTH_CLIENT_SECRET = 'csec' OAUTH_ALLOWED_SCOPES = ('PRINCIPAL_ROLE:ALL', 'other')) ENABLED = TRUE");
        assertEquals(Arrays.asList(
            "ENABLED|Boolean|true|true",
            "CATALOG_SOURCE|String|POLARIS|",
            "CATALOG_NAMESPACE|String|ns|",
            "TABLE_FORMAT|String|ICEBERG|",
            "REFRESH_INTERVAL_SECONDS|Integer|30|30",
            "REST_CONFIG|Object|{CATALOG_URI=https://o-a.example.com/polaris/api/catalog, CATALOG_NAME=mycat,"
                + " CATALOG_API_TYPE=PUBLIC, ACCESS_DELEGATION_MODE=EXTERNAL_VOLUME_CREDENTIALS}|",
            "REST_AUTHENTICATION|Object|{TYPE=OAUTH,"
                + " OAUTH_TOKEN_URI=https://o-a.example.com/polaris/api/catalog/v1/oauth/tokens,"
                + " OAUTH_CLIENT_ID=cid, OAUTH_ALLOWED_SCOPES=[PRINCIPAL_ROLE:ALL, other]}|",
            "COMMENT|String||"), described("CATALOG", "nd_open"));
        engine.executeQuery("ALTER CATALOG INTEGRATION nd_open SET REST_AUTHENTICATION ="
            + " (OAUTH_CLIENT_SECRET = 'rotated')");
        final String rotated = value("CATALOG", "nd_open", "REST_AUTHENTICATION");
        assertTrue(rotated.startsWith("{TYPE=OAUTH, ") && rotated.contains("OAUTH_CLIENT_ID=cid"), rotated);
        assertFalse(rotated.contains("rotated") || rotated.contains("SECRET"), rotated);
        engine.executeQuery("DROP INTEGRATION nd_open");
    }

    @Test
    public void anOpenCatalogTellsWhatItsStatementWrote() {
        engine.executeQuery("CREATE CATALOG INTEGRATION nd_open2 CATALOG_SOURCE = POLARIS TABLE_FORMAT = ICEBERG"
            + " REST_CONFIG = (CATALOG_URI = 'https://o-a.example.com/polaris/api/catalog' CATALOG_API_TYPE = private"
            + " CATALOG_NAME = 'mycat' ACCESS_DELEGATION_MODE = vended_credentials)"
            + " REST_AUTHENTICATION = (TYPE = oauth OAUTH_TOKEN_URI = 'https://idp.example.com/token'"
            + " OAUTH_CLIENT_ID = 'cid' OAUTH_CLIENT_SECRET = 'csec' OAUTH_ALLOWED_SCOPES = ('s')) ENABLED = FALSE");
        assertEquals("{CATALOG_URI=https://o-a.example.com/polaris/api/catalog, CATALOG_NAME=mycat,"
            + " CATALOG_API_TYPE=PRIVATE, ACCESS_DELEGATION_MODE=VENDED_CREDENTIALS}",
            value("CATALOG", "nd_open2", "REST_CONFIG"));
        assertEquals("{TYPE=OAUTH, OAUTH_TOKEN_URI=https://idp.example.com/token, OAUTH_CLIENT_ID=cid,"
            + " OAUTH_ALLOWED_SCOPES=[s]}", value("CATALOG", "nd_open2", "REST_AUTHENTICATION"));
        engine.executeQuery("DROP INTEGRATION nd_open2");
    }

    @Test
    public void anIcebergRestCatalogTellsItsAuthenticationByType() {
        final String head = "CATALOG_SOURCE = ICEBERG_REST TABLE_FORMAT = ICEBERG"
            + " REST_CONFIG = (CATALOG_URI = 'https://catalog.example.com/api/catalog'";
        engine.executeQuery("CREATE CATALOG INTEGRATION nd_oauth " + head + ") REST_AUTHENTICATION = (TYPE = OAUTH"
            + " OAUTH_CLIENT_ID = 'cid' OAUTH_CLIENT_SECRET = 'csec' OAUTH_ALLOWED_SCOPES = ('PRINCIPAL_ROLE:ALL'))"
            + " ENABLED = TRUE");
        assertEquals("{CATALOG_URI=https://catalog.example.com/api/catalog, PREFIX=null, CATALOG_NAME=null,"
            + " CATALOG_API_TYPE=PUBLIC, ACCESS_DELEGATION_MODE=EXTERNAL_VOLUME_CREDENTIALS}",
            value("CATALOG", "nd_oauth", "REST_CONFIG"));
        assertEquals("{TYPE=OAUTH, OAUTH_TOKEN_URI=https://catalog.example.com/api/catalog/v1/oauth/tokens,"
            + " OAUTH_CLIENT_ID=cid, OAUTH_ALLOWED_SCOPES=[PRINCIPAL_ROLE:ALL]}",
            value("CATALOG", "nd_oauth", "REST_AUTHENTICATION"));

        engine.executeQuery("CREATE CATALOG INTEGRATION nd_bearer " + head + " PREFIX = 'p' CATALOG_NAME = 'c')"
            + " REST_AUTHENTICATION = (TYPE = BEARER BEARER_TOKEN = 'tok123') ENABLED = TRUE");
        assertEquals("{CATALOG_URI=https://catalog.example.com/api/catalog, PREFIX=p, CATALOG_NAME=c,"
            + " CATALOG_API_TYPE=PUBLIC, ACCESS_DELEGATION_MODE=EXTERNAL_VOLUME_CREDENTIALS}",
            value("CATALOG", "nd_bearer", "REST_CONFIG"));
        assertEquals("{TYPE=BEARER}", value("CATALOG", "nd_bearer", "REST_AUTHENTICATION"));
        engine.executeQuery("ALTER CATALOG INTEGRATION nd_bearer SET REST_AUTHENTICATION = (BEARER_TOKEN = 'other')");
        assertEquals("{TYPE=BEARER}", value("CATALOG", "nd_bearer", "REST_AUTHENTICATION"));

        engine.executeQuery("CREATE CATALOG INTEGRATION nd_sigv4 " + head + " CATALOG_API_TYPE = AWS_API_GATEWAY)"
            + " REST_AUTHENTICATION = (TYPE = SIGV4 SIGV4_IAM_ROLE = 'arn:aws:iam::123456789012:role/r'"
            + " SIGV4_SIGNING_REGION = 'us-west-2' SIGV4_EXTERNAL_ID = 'ext') ENABLED = TRUE");
        assertEquals("{TYPE=SIGV4, SIGV4_IAM_ROLE=arn:aws:iam::123456789012:role/r, SIGV4_SIGNING_REGION=us-west-2}",
            value("CATALOG", "nd_sigv4", "REST_AUTHENTICATION"));
        assertTrue(value("CATALOG", "nd_sigv4", "REST_CONFIG").contains("CATALOG_API_TYPE=AWS_API_GATEWAY,"));
        for (final String name : Arrays.asList("nd_oauth", "nd_bearer", "nd_sigv4")) {
            engine.executeQuery("DROP INTEGRATION " + name);
        }
    }

    @Test
    public void aGitIntegrationTellsItsUserAuthentication() {
        engine.executeQuery("CREATE API INTEGRATION nd_git API_PROVIDER = git_https_api"
            + " API_ALLOWED_PREFIXES = ('https://gitlab.com/example') API_USER_AUTHENTICATION = (TYPE = OAUTH2"
            + " OAUTH_AUTHORIZATION_ENDPOINT = 'https://gitlab.com/oauth/authorize'"
            + " OAUTH_TOKEN_ENDPOINT = 'https://gitlab.com/oauth/token' OAUTH_CLIENT_ID = 'cid'"
            + " OAUTH_CLIENT_SECRET = 'csec') ENABLED = TRUE");
        assertEquals(Arrays.asList(
            "ENABLED|Boolean|true|true",
            "USE_PRIVATELINK_ENDPOINT|Boolean|false|false",
            "API_PROVIDER|String|GIT_HTTPS_API|",
            "API_ALLOWED_PREFIXES|List|https://gitlab.com/example|[]",
            "API_BLOCKED_PREFIXES|List||[]",
            "TLS_TRUSTED_CERTIFICATES|List|[]|[]",
            "API_USER_AUTHENTICATION|Object|{TYPE=OAUTH2, OAUTH_GRANT=AUTHORIZATION_CODE, OAUTH_CLIENT_ID=cid,"
                + " OAUTH_CLIENT_AUTH_METHOD=null, OAUTH_TOKEN_ENDPOINT=https://gitlab.com/oauth/token,"
                + " OAUTH_AUTHORIZATION_ENDPOINT=https://gitlab.com/oauth/authorize, OAUTH_ACCESS_TOKEN_VALIDITY=0,"
                + " OAUTH_REFRESH_TOKEN_VALIDITY=0, OAUTH_ALLOWED_SCOPES=[], OAUTH_USERNAME=null}|",
            "COMMENT|String||"), described("API", "nd_git"));

        engine.executeQuery("CREATE API INTEGRATION nd_git2 API_PROVIDER = git_https_api"
            + " API_ALLOWED_PREFIXES = ('https://gitlab.com/example') API_USER_AUTHENTICATION = (TYPE = OAUTH2"
            + " OAUTH_AUTHORIZATION_ENDPOINT = 'https://gitlab.com/oauth/authorize'"
            + " OAUTH_TOKEN_ENDPOINT = 'https://gitlab.com/oauth/token' OAUTH_CLIENT_ID = 'cid'"
            + " OAUTH_CLIENT_SECRET = 'csec' OAUTH_ACCESS_TOKEN_VALIDITY = 3600 OAUTH_REFRESH_TOKEN_VALIDITY = 86400"
            + " OAUTH_ALLOWED_SCOPES = ('read_api', 'read_repository') OAUTH_USERNAME = 'user@example.com')"
            + " ENABLED = TRUE");
        assertEquals("{TYPE=OAUTH2, OAUTH_GRANT=AUTHORIZATION_CODE, OAUTH_CLIENT_ID=cid,"
            + " OAUTH_CLIENT_AUTH_METHOD=null, OAUTH_TOKEN_ENDPOINT=https://gitlab.com/oauth/token,"
            + " OAUTH_AUTHORIZATION_ENDPOINT=https://gitlab.com/oauth/authorize, OAUTH_ACCESS_TOKEN_VALIDITY=3600,"
            + " OAUTH_REFRESH_TOKEN_VALIDITY=86400, OAUTH_ALLOWED_SCOPES=[read_api, read_repository],"
            + " OAUTH_USERNAME=user@example.com}", value("API", "nd_git2", "API_USER_AUTHENTICATION"));

        engine.executeQuery("CREATE API INTEGRATION nd_app API_PROVIDER = git_https_api"
            + " API_ALLOWED_PREFIXES = ('https://github.com/example')"
            + " API_USER_AUTHENTICATION = (TYPE = snowflake_github_app) ENABLED = TRUE");
        assertEquals("{TYPE=SNOWFLAKE_GITHUB_APP}", value("API", "nd_app", "API_USER_AUTHENTICATION"));
        for (final String name : Arrays.asList("nd_git", "nd_git2", "nd_app")) {
            engine.executeQuery("DROP INTEGRATION " + name);
        }
    }

    @Test
    public void anExternalMcpServerTakesAnOauth2Client() {
        engine.executeQuery("CREATE API INTEGRATION nd_mcp API_PROVIDER = external_mcp"
            + " API_ALLOWED_PREFIXES = ('https://mcp.example.com/') API_USER_AUTHENTICATION = (TYPE = OAUTH2"
            + " OAUTH_CLIENT_ID = 'cid' OAUTH_CLIENT_SECRET = 'csec'"
            + " OAUTH_TOKEN_ENDPOINT = 'https://mcp.example.com/token'"
            + " OAUTH_AUTHORIZATION_ENDPOINT = 'https://mcp.example.com/authorize') ENABLED = TRUE");
        assertEquals(Arrays.asList(
            "ENABLED|Boolean|true|true",
            "API_PROVIDER|String|EXTERNAL_MCP|",
            "API_USER_AUTHENTICATION|Object|{TYPE=OAUTH2, OAUTH_GRANT=AUTHORIZATION_CODE, OAUTH_CLIENT_ID=cid,"
                + " OAUTH_CLIENT_AUTH_METHOD=null, OAUTH_TOKEN_ENDPOINT=https://mcp.example.com/token,"
                + " OAUTH_AUTHORIZATION_ENDPOINT=https://mcp.example.com/authorize, OAUTH_ACCESS_TOKEN_VALIDITY=0,"
                + " OAUTH_REFRESH_TOKEN_VALIDITY=0, OAUTH_ALLOWED_SCOPES=[], OAUTH_USERNAME=null}|",
            "API_ALLOWED_PREFIXES|List|https://mcp.example.com/|[]",
            "API_BLOCKED_PREFIXES|List||[]",
            "COMMENT|String||"), described("API", "nd_mcp"));
        final ResultSet shown = engine.executeQuery("SHOW API INTEGRATIONS LIKE 'ND_MCP'");
        assertEquals("EXTERNAL_API", shown.getRows().get(0).getValue(1));

        // A user authentication set again keeps the members it does not name.
        engine.executeQuery("ALTER API INTEGRATION nd_mcp SET API_USER_AUTHENTICATION = (TYPE = OAUTH2"
            + " OAUTH_CLIENT_ID = 'cid2' OAUTH_CLIENT_SECRET = 'csec2'"
            + " OAUTH_TOKEN_ENDPOINT = 'https://mcp.example.com/token'"
            + " OAUTH_AUTHORIZATION_ENDPOINT = 'https://mcp.example.com/authorize'"
            + " OAUTH_CLIENT_AUTH_METHOD = client_secret_post OAUTH_REFRESH_TOKEN_VALIDITY = 86400)"
            + " COMMENT = 'two'");
        final String altered = value("API", "nd_mcp", "API_USER_AUTHENTICATION");
        assertTrue(altered.contains("OAUTH_CLIENT_ID=cid2, OAUTH_CLIENT_AUTH_METHOD=CLIENT_SECRET_POST,"), altered);
        assertTrue(altered.contains("OAUTH_REFRESH_TOKEN_VALIDITY=86400,"), altered);
        assertFalse(altered.contains("csec"), altered);
        assertEquals("two", value("API", "nd_mcp", "COMMENT"));
        engine.executeQuery("DROP INTEGRATION nd_mcp");
    }

    @Test
    public void anExternalMcpServerTakesADynamicallyRegisteredClient() {
        engine.executeQuery("CREATE API INTEGRATION nd_dcr API_PROVIDER = external_mcp"
            + " API_ALLOWED_PREFIXES = ('https://mcp.example.com/') API_USER_AUTHENTICATION ="
            + " (TYPE = OAUTH_DYNAMIC_CLIENT OAUTH_RESOURCE_URL = 'https://mcp.example.com/mcp') ENABLED = TRUE"
            + " COMMENT = 'one'");
        assertEquals(Arrays.asList(
            "ENABLED|Boolean|true|true",
            "API_PROVIDER|String|EXTERNAL_MCP|",
            "API_USER_AUTHENTICATION|Object|{TYPE=OAUTH_DYNAMIC_CLIENT,"
                + " OAUTH_RESOURCE_URL=https://mcp.example.com/mcp}|",
            "API_ALLOWED_PREFIXES|List|https://mcp.example.com/|[]",
            "API_BLOCKED_PREFIXES|List||[]",
            "COMMENT|String|one|"), described("API", "nd_dcr"));
        engine.executeQuery("ALTER API INTEGRATION nd_dcr SET API_BLOCKED_PREFIXES = ('https://mcp.example.com/x')"
            + " COMMENT = 'two'");
        assertEquals("https://mcp.example.com/x", value("API", "nd_dcr", "API_BLOCKED_PREFIXES"));
        engine.executeQuery("DROP INTEGRATION nd_dcr");
    }

    @Test
    public void aGatewayIntegrationTakesNoUserAuthenticationAfterwards() {
        engine.executeQuery("CREATE API INTEGRATION nd_gw API_PROVIDER = git_https_api"
            + " API_ALLOWED_PREFIXES = ('https://gitlab.com/example') ENABLED = TRUE");
        assertEquals("SQL compilation error:\ninvalid property 'API_USER_AUTHENTICATION' for 'INTEGRATION -"
            + " EXTERNAL_API'", refusal("ALTER API INTEGRATION nd_gw SET API_USER_AUTHENTICATION ="
            + " (TYPE = SNOWFLAKE_GITHUB_APP)"));
        engine.executeQuery("DROP INTEGRATION nd_gw");
    }

    @Test
    public void aJwtBearerFlowWritesItsAssertionIssuer() {
        engine.executeQuery("CREATE SECURITY INTEGRATION nd_jwt TYPE = API_AUTHENTICATION AUTH_TYPE = OAUTH2"
            + " ENABLED = TRUE OAUTH_ASSERTION_ISSUER = 'issuer' OAUTH_TOKEN_ENDPOINT = 'https://example.com/token'"
            + " OAUTH_CLIENT_ID = 'cid' OAUTH_CLIENT_SECRET = 'csec' OAUTH_GRANT = JWT_BEARER COMMENT = 'one'");
        final List<String> rows = described("SECURITY", "nd_jwt");
        assertTrue(rows.contains("OAUTH_ASSERTION_ISSUER|String|issuer|"), rows.toString());
        assertTrue(rows.contains("OAUTH_GRANT|String|JWT_BEARER|"), rows.toString());
        assertTrue(rows.contains("OAUTH_TOKEN_ENDPOINT|String|https://example.com/token|"), rows.toString());
        assertTrue(rows.contains("OAUTH_CLIENT_ID|String|cid|"), rows.toString());
        assertFalse(rows.toString().contains("csec"), rows.toString());
        engine.executeQuery("ALTER SECURITY INTEGRATION nd_jwt SET COMMENT = 'two'");
        assertEquals("issuer", value("SECURITY", "nd_jwt", "OAUTH_ASSERTION_ISSUER"));
        engine.executeQuery("DROP INTEGRATION nd_jwt");

        // Under another grant, and under none, the issuer stays a property no statement sets.
        final String head = "CREATE SECURITY INTEGRATION nd_jwt2 TYPE = API_AUTHENTICATION AUTH_TYPE = OAUTH2"
            + " ENABLED = TRUE OAUTH_ASSERTION_ISSUER = 'issuer' OAUTH_CLIENT_ID = 'cid' OAUTH_CLIENT_SECRET = 'csec'";
        final String refused = "SQL compilation error:\ninvalid property 'OAUTH_ASSERTION_ISSUER' for 'INTEGRATION -"
            + " API_AUTHENTICATION'";
        assertEquals(refused, refusal(head));
        assertEquals(refused, refusal(head + " OAUTH_GRANT = 'CLIENT_CREDENTIALS'"));
    }
}
