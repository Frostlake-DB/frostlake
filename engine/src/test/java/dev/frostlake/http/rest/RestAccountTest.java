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

package dev.frostlake.http.rest;

import dev.frostlake.http.SessionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The account endpoints ({@code account.yaml}): account records over CREATE, DROP, UNDROP and SHOW ACCOUNTS, called in
 * a session whose role is ORGADMIN, the organization's administrator, who alone manages its accounts.
 */
public class RestAccountTest extends BaseRestTest {

    private SessionContext session;

    @BeforeEach
    public void openOrganizationAdministratorsSession() {
        session = server.getEngine().createSession();
        assertTrue(server.getEngine().execute("USE ROLE ORGADMIN", session).isSuccess());
    }

    @AfterEach
    public void closeSession() {
        server.getEngine().removeSession(session.getSessionId());
    }

    /** Each call runs in the organization's administrator's session. */
    @Override
    protected HttpResponse<String> request(final String method, final String path, final String body)
            throws Exception {
        final HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(baseUrl + path));
        if (body != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        builder.header("Accept", "application/json");
        builder.header("X-Sfc-Session", session.getSessionId());
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static final String BODY = """
        {"name":"%s","admin_name":"admin","admin_password":"TestPassword1","email":"admin@example.com",
         "edition":"ENTERPRISE","region":"AWS_US_WEST_2","comment":"rest","must_change_password":true}""";

    @Test
    public void anAccountIsCreatedListedDroppedAndRestored() throws Exception {
        assertEquals("Account ACCT_BASIC successfully created.",
            ok(post("/api/v2/accounts", String.format(BODY, "acct_basic"))).path("status").asString());
        final JsonNode accounts = ok(get("/api/v2/accounts?like=acct_bas%25"));
        assertEquals("ACCT_BASIC", names(accounts));
        final JsonNode account = accounts.get(0);
        assertEquals("ENTERPRISE", account.path("edition").asString());
        assertEquals("AWS_US_WEST_2", account.path("region").asString());
        assertEquals("rest", account.path("comment").asString());
        assertFalse(account.path("is_org_admin").asBoolean());
        assertTrue(account.path("account_locator").asString().length() > 0, account.toString());
        assertTrue(account.get("admin_password").isNull(), "a password is never answered: " + account);
        assertEquals("PUBLIC", account.path("region_group").asString(), account.toString());
        assertFalse(account.path("polaris").asBoolean(), account.toString());
        assertTrue(ok(get("/api/v2/accounts")).size() >= 2, "this account and the new one");
        assertEquals(1, ok(get("/api/v2/accounts?showLimit=1")).size());
        error(409, post("/api/v2/accounts", String.format(BODY, "acct_basic")));

        error(400, delete("/api/v2/accounts/acct_basic"));
        ok(delete("/api/v2/accounts/acct_basic?gracePeriodInDays=5"));
        assertEquals("", names(ok(get("/api/v2/accounts?like=ACCT_BASIC"))));
        final JsonNode history = ok(get("/api/v2/accounts?like=ACCT_BASIC&history=true"));
        assertEquals("ACCT_BASIC", names(history));
        assertTrue(history.get(0).path("dropped_on").asString().contains("T"), history.toString());
        assertTrue(history.get(0).has("scheduled_deletion_time"), history.toString());
        error(404, delete("/api/v2/accounts/acct_basic?gracePeriodInDays=5"));
        ok(delete("/api/v2/accounts/acct_basic?gracePeriodInDays=5&ifExists=true"));

        ok(post("/api/v2/accounts/acct_basic:undrop", null));
        assertEquals("ACCT_BASIC", names(ok(get("/api/v2/accounts?like=ACCT_BASIC"))));
        error(409, post("/api/v2/accounts/acct_basic:undrop", null));
        // To the organization's administrator, an account that is not there does not exist.
        error(404, post("/api/v2/accounts/no_such_account:undrop", null));
    }

    @Test
    public void aCreateMissingARequiredPropertyIsRefused() throws Exception {
        error(400, post("/api/v2/accounts", "{\"name\":\"acct_bad\",\"admin_name\":\"a\",\"email\":\"e\"}"));
        error(400, post("/api/v2/accounts", "{\"name\":\"acct_bad\",\"admin_name\":\"a\",\"admin_password\":\"p\","
            + "\"email\":\"e\",\"edition\":\"ENTERPRISE; DROP\"}"));
        error(400, post("/api/v2/accounts", "{\"admin_name\":\"a\"}"));
    }
}
