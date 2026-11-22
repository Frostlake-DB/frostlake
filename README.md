# Frostlake

An embeddable SQL engine that emulates core Snowflake features — for fast, credit-free
local and CI testing. Ships an in-process `DatabaseEngine`, a JDBC driver, an HTTP server,
and console clients.

- **Maven:** `dev.frostlake:frostlake-db`
- **License:** [Apache-2.0](LICENSE)
- **Homepage:** <https://frostlake.dev>

## What it is

Frostlake runs Snowflake-compatible SQL — DDL/DML, CTEs, window functions, PIVOT/UNPIVOT,
MATCH_RECOGNIZE, Snowflake Scripting procedures, JS/Python/Scala UDFs, streams, tasks, and role-based
security — against a lightweight in-memory engine, so you can develop and test Snowflake
SQL locally without a Snowflake account or credits.

## Build

```bash
mvn clean package -DskipTests   # build the jar
mvn test                        # full test suite
mvn verify                      # + Apache RAT license-header audit
```

The suite's other modes — through Frostlake's own JDBC driver, against a live Snowflake account, and the
language-neutral testkit corpus — are described in [`docs/testing.md`](docs/testing.md).

## Snowflake's JDBC driver

Snowflake's own JDBC driver (`net.snowflake:snowflake-jdbc`) works with Frostlake unchanged. Start the HTTP
server (`data/start-http-server.sh`, port 18082) and connect to
`jdbc:snowflake://127.0.0.1:18082/?account=frostlake&ssl=off` with any user and password, or start one in-process
in a test:

```java
try (SnowflakeEndpoint endpoint = SnowflakeEndpoint.start("mydb");
     Connection conn = DriverManager.getConnection(endpoint.jdbcUrl(), "any", "any");
     Statement stmt = conn.createStatement()) {
    stmt.execute("CREATE TABLE t (id INT, name VARCHAR)");
    stmt.execute("INSERT INTO t VALUES (1, 'a'), (2, 'b')");
    try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM t")) {
        rs.next();
        System.out.println(rs.getLong(1));   // 2
    }
}
```

As against Snowflake, the driver reads its default Arrow results on JDK 16+ only with
`--add-opens=java.base/java.nio=ALL-UNNAMED`; append `&JDBC_QUERY_RESULT_FORMAT=JSON` to the URL to do without.
Details in [`docs/http-api.md`](docs/http-api.md#the-snowflake-jdbc-driver).

A driver that can carry its requests through an HTTP transport needs no server at all: with the optional
`frostlake-sf-transport` module on the class path, `jdbc:snowflake://mydb/?account=frostlake&httpTransport=frostlake`
reaches the engine `jdbc:frostlake:direct:mydb` connections share, in process. No released Snowflake driver offers the
transport yet - see [`docs/http-api.md`](docs/http-api.md#without-a-socket).

## Snowflake's Python connector and Snowflake CLI

Snowflake's Python connector (`snowflake-connector-python`) and Snowflake CLI (`snow`) connect to the same HTTP
server. Give the connector the server's host and port with `protocol='http'`; the account, user and password are
not checked:

```python
import snowflake.connector

conn = snowflake.connector.connect(account='frostlake', user='any', password='any',
                                   host='127.0.0.1', port=18082, protocol='http')
print(conn.cursor().execute("SELECT CURRENT_VERSION()").fetchone())
```

For `snow`, add a connection to its `config.toml` and pass `-c frostlake` to a command — `snow sql`, `snow object`
and `snow stage create`, `copy` and `list-files` work:

```toml
[connections.frostlake]
account = "frostlake"
user = "any"
password = "any"
host = "127.0.0.1"
port = 18082
protocol = "http"
```

REMOVE, which `snow stage remove` sends, is refused until the server's `frostlake.properties` sets
`command.removeEnabled=true`.


## Snowflake's Terraform provider

The Snowflake Terraform provider (`snowflakedb/snowflake`) plans, applies and destroys against the same HTTP server:
give it the server's `host` and `port` with `protocol = "http"`. The provider block, a first configuration, the
resources that run their whole lifecycle and the ones that do not are in [`docs/terraform.md`](docs/terraform.md).

## Trademarks & Disclaimer

Frostlake is an independent project and is **not affiliated with, endorsed by, or sponsored
by Snowflake Inc.** "Snowflake" and "Snowpark" are trademarks of Snowflake Inc., used here
only nominatively, to describe compatibility.

See [`PROVENANCE.md`](PROVENANCE.md) for clean-room / independence details and [`NOTICE`](NOTICE)
for third-party attributions.

## Security

The HTTP server is **unauthenticated by design** and binds **loopback** (`http.host=localhost`) by
default, so it is reachable only from the machine running it. Setting `http.host=0.0.0.0` serves an
engine that will run any SQL it is sent, to anything that can reach the port — see
[`docs/scope.md`](docs/scope.md).

## Changes

[`CHANGELOG.md`](CHANGELOG.md) records what each release changed. Read the "Now refused" section
before upgrading: Frostlake aims to answer SQL the way a real account does, so closing a fidelity gap
can mean rejecting SQL an earlier version accepted.
