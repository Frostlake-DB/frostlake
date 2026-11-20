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

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * How DESCRIBE shows the nested property lists of an integration — a REST catalog's REST_CONFIG and
 * REST_AUTHENTICATION, an API integration's API_USER_AUTHENTICATION: one row each, its value the members as
 * {@code {NAME=value, NAME=value}}, a list among them as {@code [a, b]} and a member that was left out and has no
 * default as {@code null}. A secret — a client's secret, a bearer token, an external ID — is not among the members
 * at all, and what the service fills in for a member left out is told: the catalog's API type and access delegation
 * mode, the OAuth token endpoint of the catalog itself, the signing region of the account, the grant of an OAuth2
 * user authentication and its validities of 0.
 *
 * <p>The members and their texts follow the statements' documentation and the form the Snowflake Terraform provider
 * reads them in; their order within a row is the order the documentation lists them in.
 */
final class IntegrationNestedDescription {

    private static final String NULL = "null";

    private IntegrationNestedDescription() {
    }

    /**
     * A REST catalog's REST_CONFIG: its URI, the prefix of an Iceberg REST catalog, its name, and the API type and
     * access delegation mode, which default to PUBLIC and EXTERNAL_VOLUME_CREDENTIALS.
     *
     * @param integration the catalog integration
     * @param config      what its statement wrote
     */
    static String restConfig(final Integration integration, final PropertyValue config) {
        final Map<String, String> members = new LinkedHashMap<>();
        members.put("CATALOG_URI", text(config, "CATALOG_URI", NULL));
        if (!"POLARIS".equals(integration.text("CATALOG_SOURCE"))) {
            members.put("PREFIX", text(config, "PREFIX", NULL));
        }
        members.put("CATALOG_NAME", text(config, "CATALOG_NAME", NULL));
        members.put("CATALOG_API_TYPE", word(config, "CATALOG_API_TYPE", "PUBLIC"));
        members.put("ACCESS_DELEGATION_MODE", word(config, "ACCESS_DELEGATION_MODE", "EXTERNAL_VOLUME_CREDENTIALS"));
        return members.toString();
    }

    /**
     * A REST catalog's REST_AUTHENTICATION: its type and what the type is reached with, never a secret. An OAuth
     * client written without a token endpoint is given the catalog's own, and a SigV4 role written without a signing
     * region the account's.
     *
     * @param integration   the catalog integration
     * @param auth          what its statement wrote
     * @param accountRegion the account's region as its cloud names it, or empty
     */
    static String restAuthentication(final Integration integration, final PropertyValue auth,
                                     final String accountRegion) {
        final Map<String, String> members = new LinkedHashMap<>();
        final String type = word(auth, "TYPE", NULL);
        members.put("TYPE", type);
        if ("OAUTH".equals(type)) {
            final PropertyValue config = integration.property("REST_CONFIG");
            final String catalog = config == null ? NULL : text(config, "CATALOG_URI", NULL);
            members.put("OAUTH_TOKEN_URI", text(auth, "OAUTH_TOKEN_URI", catalog + "/v1/oauth/tokens"));
            members.put("OAUTH_CLIENT_ID", text(auth, "OAUTH_CLIENT_ID", NULL));
            members.put("OAUTH_ALLOWED_SCOPES", list(auth, "OAUTH_ALLOWED_SCOPES"));
        } else if ("SIGV4".equals(type)) {
            members.put("SIGV4_IAM_ROLE", text(auth, "SIGV4_IAM_ROLE", NULL));
            members.put("SIGV4_SIGNING_REGION", text(auth, "SIGV4_SIGNING_REGION",
                accountRegion.isEmpty() ? NULL : accountRegion));
        }
        return members.toString();
    }

    /**
     * An API integration's API_USER_AUTHENTICATION: its type, and for an OAuth2 client its grant — an authorization
     * code's, there being no other —, its endpoints and what it asked for, for a dynamically registered client the
     * resource it registers at.
     *
     * @param auth what the integration's statement wrote
     */
    static String userAuthentication(final PropertyValue auth) {
        final Map<String, String> members = new LinkedHashMap<>();
        final String type = word(auth, "TYPE", NULL);
        members.put("TYPE", type);
        if ("OAUTH2".equals(type)) {
            members.put("OAUTH_GRANT", "AUTHORIZATION_CODE");
            members.put("OAUTH_CLIENT_ID", text(auth, "OAUTH_CLIENT_ID", NULL));
            members.put("OAUTH_CLIENT_AUTH_METHOD", word(auth, "OAUTH_CLIENT_AUTH_METHOD", NULL));
            members.put("OAUTH_TOKEN_ENDPOINT", text(auth, "OAUTH_TOKEN_ENDPOINT", NULL));
            members.put("OAUTH_AUTHORIZATION_ENDPOINT", text(auth, "OAUTH_AUTHORIZATION_ENDPOINT", NULL));
            members.put("OAUTH_ACCESS_TOKEN_VALIDITY", text(auth, "OAUTH_ACCESS_TOKEN_VALIDITY", "0"));
            members.put("OAUTH_REFRESH_TOKEN_VALIDITY", text(auth, "OAUTH_REFRESH_TOKEN_VALIDITY", "0"));
            members.put("OAUTH_ALLOWED_SCOPES", list(auth, "OAUTH_ALLOWED_SCOPES"));
            members.put("OAUTH_USERNAME", text(auth, "OAUTH_USERNAME", NULL));
        } else if ("OAUTH_DYNAMIC_CLIENT".equals(type)) {
            members.put("OAUTH_RESOURCE_URL", text(auth, "OAUTH_RESOURCE_URL", NULL));
        }
        return members.toString();
    }

    /** A member's text as written, or the text of one left out. */
    private static String text(final PropertyValue nested, final String key, final String absent) {
        final PropertyValue member = nested.entry(key);
        return member == null || member.getText() == null ? absent : member.getText();
    }

    /** A member that chooses among words, in upper case, or the word of one left out. */
    private static String word(final PropertyValue nested, final String key, final String absent) {
        final PropertyValue member = nested.entry(key);
        return member == null || member.getText() == null ? absent : member.getText().toUpperCase(Locale.ROOT);
    }

    /** A list member as {@code [a, b]}; {@code []} for one left out. */
    private static String list(final PropertyValue nested, final String key) {
        final PropertyValue member = nested.entry(key);
        return member == null ? "[]" : member.strings().toString();
    }
}
