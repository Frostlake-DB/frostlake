# Frostlake with the Snowflake Terraform provider

The Snowflake Terraform provider (`snowflakedb/snowflake`) connects to Frostlake's HTTP server the way it connects
to an account: it logs in, sends SQL, and reads the objects back with `SHOW` and `DESCRIBE`. Point it at a running
server and a configuration can be planned, applied, changed and destroyed without an account.

Checked with provider **v2.23.0** and Terraform **1.15**.

## 1. Start the server

```bash
cd frostlake
./data/start-http-server.sh            # port 18082
```

The server speaks plain HTTP. Its state lives in memory unless persistence is configured in
`frostlake.properties`, so a restarted server has none of the objects a Terraform state file still lists; run
`terraform destroy` before stopping it, or delete the state file afterwards.

## 2. Configure the provider

```hcl
terraform {
  required_providers {
    snowflake = {
      source = "snowflakedb/snowflake"
    }
  }
}

provider "snowflake" {
  account_name      = "frostlake"
  organization_name = "frostlake"
  user              = "any"
  password          = "any"
  host              = "localhost"
  port              = 18082
  protocol          = "http"
  role              = "ACCOUNTADMIN"

  # Resources the provider still marks as preview name themselves here.
  preview_features_enabled = [
    "snowflake_table_resource",
    "snowflake_sequence_resource",
  ]
}
```

What matters:

- **`protocol = "http"`** with `host` and `port`. The provider's driver defaults to HTTPS, which the server does not
  speak; without the setting the provider hangs at login.
- **`account_name` and `organization_name`** take any text. The user and the password are not checked.
- **`preview_features_enabled`**: a resource the provider treats as a preview feature fails with a message that
  names the entry to add, for example `snowflake_table_resource`.

The same block works for a real account once the host, the port and the protocol are taken out and real
credentials are put in, so one configuration can be tried here first.

## 3. A first configuration

```hcl
resource "snowflake_database" "db" {
  name    = "TF_DB"
  comment = "made by terraform"
}

resource "snowflake_schema" "sch" {
  database = snowflake_database.db.name
  name     = "TF_SCHEMA"
}

resource "snowflake_table" "t" {
  database = snowflake_database.db.name
  schema   = snowflake_schema.sch.name
  name     = "TF_TABLE"

  column {
    name = "ID"
    type = "NUMBER(38,0)"
  }
  column {
    name = "NAME"
    type = "VARCHAR(100)"
  }
}

resource "snowflake_warehouse" "wh" {
  name           = "TF_WH"
  warehouse_size = "XSMALL"
}

resource "snowflake_account_role" "r" {
  name = "TF_ROLE"
}

resource "snowflake_grant_privileges_to_account_role" "g" {
  account_role_name = snowflake_account_role.r.name
  privileges        = ["USAGE"]

  on_schema {
    schema_name = snowflake_schema.sch.fully_qualified_name
  }
}
```

```bash
terraform init
terraform apply
terraform plan      # "No changes." - what was written reads back the same
terraform destroy
```

## 4. What works

Each kind below was run through its whole lifecycle: create, a plan that finds no drift, a change in place, a
second plan, and destroy.

| Area | Resources |
| --- | --- |
| Containers and relations | `database`, `schema`, `table`, `view`, `materialized_view`, `dynamic_table`, `hybrid_table`, `external_table`, `sequence` |
| Compute and users | `warehouse`, `warehouse_adaptive`, `user`, `service_user`, `legacy_service_user`, `user_public_keys` |
| Roles and grants | `account_role`, `database_role`, `grant_privileges_to_account_role`, `grant_privileges_to_database_role`, `grant_account_role`, `grant_database_role`, `grant_ownership` |
| Stages and formats | `stage`, `stage_internal`, `stage_external_s3`, `stage_external_azure`, `stage_external_gcs`, `stage_external_s3_compatible`, `file_format`, `file_format_csv`, `file_format_json`, `file_format_avro`, `file_format_orc`, `file_format_parquet`, `file_format_xml` |
| Routines | `function_sql`, `external_function`, `function_javascript`, `function_python`, `function_java`, `function_scala`, `procedure_sql`, `procedure_javascript`, `procedure_java`, `procedure_scala` |
| Pipelines | `task`, `stream_on_table`, `stream_on_view`, `stream_on_directory_table`, `stream_on_external_table`, `pipe`, `alert` |
| Policies and tags | `masking_policy`, `row_access_policy`, `projection_policy`, `password_policy`, `authentication_policy`, `session_policy`, `network_policy`, `network_rule`, `network_policy_attachment`, `user_password_policy_attachment`, `account_authentication_policy_attachment`, `user_authentication_policy_attachment`, `account_session_policy_attachment`, `user_session_policy_attachment`, `account_password_policy_attachment`, `table_column_masking_policy_application`, `storage_lifecycle_policy`, `table_storage_lifecycle_policy_attachment`, `tag`, `tag_association` |
| Integrations | `api_integration`, `api_integration_amazon_api_gateway`, `api_integration_azure_api_management`, `api_integration_google_cloud_api_gateway`, `api_integration_git_repository_github_app`, `api_integration_git_repository_oauth2`, `api_integration_git_repository_private_link`, `api_integration_git_repository_token`, `api_integration_external_mcp_dynamic_client`, `api_integration_external_mcp_oauth2`, `storage_integration`, `storage_integration_aws`, `storage_integration_azure`, `storage_integration_gcs`, `catalog_integration_object_storage`, `catalog_integration_aws_glue`, `catalog_integration_iceberg_rest`, `catalog_integration_open_catalog`, `notification_integration`, `email_notification_integration`, `external_access_integration`, `scim_integration`, `oauth_integration_for_custom_clients`, `oauth_integration_for_partner_applications`, `external_oauth_integration`, `saml2_integration`, `api_authentication_integration_with_client_credentials`, `api_authentication_integration_with_authorization_code_grant`, `api_authentication_integration_with_jwt_bearer` |
| Account objects | `resource_monitor`, `share`, `failover_group`, `secondary_database`, `external_volume`, `compute_pool`, `image_repository`, `secret_with_generic_string`, `secret_with_basic_authentication`, `secret_with_client_credentials`, `secret_with_authorization_code_grant` |
| Other | `streamlit`, `notebook`, `cortex_search_service`, `execute`, `account_parameter`, `object_parameter` |

The data sources for these kinds (`snowflake_databases`, `snowflake_schemas`, `snowflake_tables`,
`snowflake_grants`, `snowflake_pipes` and the rest) answer as well.

## 5. What does not work yet

These resources fail at this provider version, each at the statement or the read named:

| Resources | Why |
| --- | --- |
| `grant_privileges_to_share`, `shared_database`, `listing`, `managed_account` | a grant to a share does not find the share; `CREATE DATABASE … FROM SHARE`, `CREATE EXTERNAL LISTING` and `DROP MANAGED ACCOUNT IF EXISTS` are not read |
| `replication_group`, `replication_group_secondary`, `primary_connection`, `secondary_connection` | `CREATE REPLICATION GROUP` and `CREATE CONNECTION` are not read |
| `iceberg_table` and the four `iceberg_table_from_*` | the provider's quoted external volume name inside a string is not found; `CREATE ICEBERG TABLE` without columns is not read |
| `warehouse_interactive`, `service`, `job_service`, `cortex_agent`, `mcp_server`, `code_bundle`, `grant_application_role`, `backup_policy`, `user_programmatic_access_token`, `postgres_*`, `openflow_*`, `current_account`, `current_organization_account` | the kind is not modelled |
| `semantic_view` | `CREATE SEMANTIC VIEW` is not read |

`account` works when the provider's role is ORGADMIN.

## 6. Things to know

- **`snowflake_procedure_python` fails at its first read**, `expected at least snowpark package, got []`. Frostlake
  answers `DESCRIBE PROCEDURE` as a current account does, the package listed under
  `artifact_repository_packages`, and the provider at this version does not read it there.
- **`snowflake_table_constraint` reports success and creates no constraint.** The provider adds
  `DEFERRABLE INITIALLY DEFERRED ENABLE RELY` to the constraint, and with those properties Snowflake accepts the
  statement and leaves the constraint out. Frostlake does the same. Declare keys in the table's own definition.
- **`snowflake_stage_external_s3_compatible` takes only an endpoint Snowflake knows** (an AWS S3, Wasabi, MinIO,
  Cloudflare R2, DigitalOcean Spaces or Backblaze B2 host); any other host is refused, `Endpoint … not allowed.`, as
  on Snowflake.
- **Some objects are a catalog entry and nothing more.** A resource monitor, a share, a failover group and a
  secondary database are stored and listed; no credit is counted and nothing is shared or replicated. An external
  table is created and listed, and reads no rows. These objects are not written to a persistence snapshot.
- **`snowflake_git_repository`** is refused unless the server can reach the repository's origin.
- **Two resources that manage one attribute drift on Frostlake as they do on Snowflake**: a stream or a dynamic
  table turns a table's `change_tracking` on, so declare `change_tracking = true` on the table.

The lists above are kept honest by a test project beside the engine, `frostlake-terraform`: one case per resource
kind, run through the whole lifecycle against a server, with the cases known not to pass named in its
`expected.tsv`.

What Frostlake models, and where it stops, is described in [`scope.md`](scope.md).
