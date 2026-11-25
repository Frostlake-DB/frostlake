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

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * ARTIFACT_REPOSITORY_PACKAGES on a Python routine (live-verified): a parenthesized list of strings, taken only beside
 * an ARTIFACT_REPOSITORY and only by a Python routine. The repository's packages are told as
 * artifact_repository_packages and listed by INFORMATION_SCHEMA; the PACKAGES ones beside them are told as packages,
 * and GET_DDL writes those before the repository and the repository's after it. A procedure has its Snowpark package
 * when either list names it.
 */
public class RoutineArtifactRepositoryPackagesTest extends MeasuredCellsTestSupport {

    @Test
    public void functionsTellBothLists() {
        engine.execute("CREATE OR REPLACE FUNCTION f1(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.11' HANDLER = 'h' PACKAGES = ('numpy') ARTIFACT_REPOSITORY = "
            + "snowflake.snowpark.pypi_shared_repository ARTIFACT_REPOSITORY_PACKAGES = ('scikit-learn') "
            + "COMMENT = 'c' AS $$\ndef h(x):\n    return x\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION f2(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.11' HANDLER = 'h' ARTIFACT_REPOSITORY = snowflake.snowpark.pypi_shared_repository "
            + "ARTIFACT_REPOSITORY_PACKAGES = () AS $$\ndef h(x):\n    return x\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION f3(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.11' HANDLER = 'h' ARTIFACT_REPOSITORY = snowflake.snowpark.pypi_shared_repository "
            + "PACKAGES = ('numpy') ARTIFACT_REPOSITORY_PACKAGES = () AS $$\ndef h(x):\n    return x\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION f4(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.11' HANDLER = 'h' ARTIFACT_REPOSITORY = snowflake.snowpark.pypi_shared_repository "
            + "PACKAGES = () ARTIFACT_REPOSITORY_PACKAGES = ('requests') AS $$\ndef h(x):\n    return "
            + "x\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION f5(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.11' HANDLER = 'h' ARTIFACT_REPOSITORY = snowflake.snowpark.pypi_shared_repository "
            + "artifact_repository_packages = ('a') ARTIFACT_REPOSITORY_PACKAGES = ($$requests$$) AS "
            + "$$\ndef h(x):\n    return x\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION f6(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.11' HANDLER = 'h' ARTIFACT_REPOSITORY_PACKAGES = ('scikit-learn', 'requests') "
            + "ARTIFACT_REPOSITORY = snowflake.snowpark.pypi_shared_repository AS $$\ndef h(x):\n    "
            + "return x\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION f7(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.11' HANDLER = 'h' PACKAGES = ('numpy') AS $$\ndef h(x):\n    return x\n$$");
        assertCells(new String[][] {
            {"DESCRIBE FUNCTION f1(NUMBER) ->> SELECT LISTAGG(\"property\", '/') FROM $1",
                "signature/returns/language/null "
                + "handling/volatility/body/imports/handler/runtime_version/packages/artifact_repository/"
                + "artifact_repository_packages/installed_packages/is_aggregate/"
                + "artifact_repository_installed_packages"},
            {"DESCRIBE FUNCTION f1(NUMBER) ->> SELECT LISTAGG(\"property\" || '=' || \"value\", ' / ') "
                + "WITHIN GROUP (ORDER BY \"property\") FROM $1 WHERE \"property\" IN ('packages', "
                + "'artifact_repository', 'artifact_repository_packages')",
                "artifact_repository=snowflake.snowpark.pypi_shared_repository / "
                + "artifact_repository_packages=['scikit-learn'] / packages=['numpy']"},
            {"DESCRIBE FUNCTION f2(NUMBER) ->> SELECT LISTAGG(\"property\" || '=' || \"value\", ' / ') "
                + "WITHIN GROUP (ORDER BY \"property\") FROM $1 WHERE \"property\" IN ('packages', "
                + "'artifact_repository', 'artifact_repository_packages')",
                "artifact_repository=snowflake.snowpark.pypi_shared_repository / "
                + "artifact_repository_packages=[] / packages=[]"},
            {"DESCRIBE FUNCTION f3(NUMBER) ->> SELECT LISTAGG(\"property\" || '=' || \"value\", ' / ') "
                + "WITHIN GROUP (ORDER BY \"property\") FROM $1 WHERE \"property\" IN ('packages', "
                + "'artifact_repository', 'artifact_repository_packages')",
                "artifact_repository=snowflake.snowpark.pypi_shared_repository / "
                + "artifact_repository_packages=[] / packages=['numpy']"},
            {"DESCRIBE FUNCTION f4(NUMBER) ->> SELECT LISTAGG(\"property\" || '=' || \"value\", ' / ') "
                + "WITHIN GROUP (ORDER BY \"property\") FROM $1 WHERE \"property\" IN ('packages', "
                + "'artifact_repository', 'artifact_repository_packages')",
                "artifact_repository=snowflake.snowpark.pypi_shared_repository / "
                + "artifact_repository_packages=['requests'] / packages=[]"},
            {"DESCRIBE FUNCTION f5(NUMBER) ->> SELECT LISTAGG(\"property\" || '=' || \"value\", ' / ') "
                + "WITHIN GROUP (ORDER BY \"property\") FROM $1 WHERE \"property\" IN ('packages', "
                + "'artifact_repository', 'artifact_repository_packages')",
                "artifact_repository=snowflake.snowpark.pypi_shared_repository / "
                + "artifact_repository_packages=['requests'] / packages=[]"},
            {"DESCRIBE FUNCTION f6(NUMBER) ->> SELECT LISTAGG(\"property\" || '=' || \"value\", ' / ') "
                + "WITHIN GROUP (ORDER BY \"property\") FROM $1 WHERE \"property\" IN ('packages', "
                + "'artifact_repository', 'artifact_repository_packages')",
                "artifact_repository=snowflake.snowpark.pypi_shared_repository / "
                + "artifact_repository_packages=['scikit-learn','requests'] / packages=[]"},
            {"DESCRIBE FUNCTION f7(NUMBER) ->> SELECT LISTAGG(\"property\" || '=' || \"value\", ' / ') "
                + "WITHIN GROUP (ORDER BY \"property\") FROM $1 WHERE \"property\" IN ('packages', "
                + "'artifact_repository', 'artifact_repository_packages')",
                "artifact_repository=snowflake.snowpark.pypi_shared_repository / "
                + "artifact_repository_packages=['numpy'] / packages=[]"},
            {"SELECT GET_DDL('FUNCTION', 'f1(NUMBER)')",
                "CREATE OR REPLACE FUNCTION \"F1\"(\"X\" NUMBER(38,0)) | RETURNS NUMBER(38,0) | LANGUAGE "
                + "PYTHON | RUNTIME_VERSION = '3.11' | PACKAGES = ('numpy') | ARTIFACT_REPOSITORY = "
                + "snowflake.snowpark.pypi_shared_repository | ARTIFACT_REPOSITORY_PACKAGES = ('scikit-learn') "
                + "| HANDLER = 'h' | COMMENT='c' | AS ' | def h(x): |     return x | ';"},
            {"SELECT GET_DDL('FUNCTION', 'f2(NUMBER)')",
                "CREATE OR REPLACE FUNCTION \"F2\"(\"X\" NUMBER(38,0)) | RETURNS NUMBER(38,0) | LANGUAGE "
                + "PYTHON | RUNTIME_VERSION = '3.11' | ARTIFACT_REPOSITORY = "
                + "snowflake.snowpark.pypi_shared_repository | HANDLER = 'h' | AS ' | def h(x): |     return x "
                + "| ';"},
            {"SELECT GET_DDL('FUNCTION', 'f3(NUMBER)')",
                "CREATE OR REPLACE FUNCTION \"F3\"(\"X\" NUMBER(38,0)) | RETURNS NUMBER(38,0) | LANGUAGE "
                + "PYTHON | RUNTIME_VERSION = '3.11' | PACKAGES = ('numpy') | ARTIFACT_REPOSITORY = "
                + "snowflake.snowpark.pypi_shared_repository | HANDLER = 'h' | AS ' | def h(x): |     return x "
                + "| ';"},
            {"SELECT GET_DDL('FUNCTION', 'f4(NUMBER)')",
                "CREATE OR REPLACE FUNCTION \"F4\"(\"X\" NUMBER(38,0)) | RETURNS NUMBER(38,0) | LANGUAGE "
                + "PYTHON | RUNTIME_VERSION = '3.11' | ARTIFACT_REPOSITORY = "
                + "snowflake.snowpark.pypi_shared_repository | PACKAGES = ('requests') | HANDLER = 'h' | AS ' |"
                + " def h(x): |     return x | ';"},
            {"SELECT GET_DDL('FUNCTION', 'f6(NUMBER)')",
                "CREATE OR REPLACE FUNCTION \"F6\"(\"X\" NUMBER(38,0)) | RETURNS NUMBER(38,0) | LANGUAGE "
                + "PYTHON | RUNTIME_VERSION = '3.11' | ARTIFACT_REPOSITORY = "
                + "snowflake.snowpark.pypi_shared_repository | PACKAGES = ('scikit-learn','requests') | HANDLER"
                + " = 'h' | AS ' | def h(x): |     return x | ';"},
            {"SELECT packages FROM information_schema.functions WHERE function_name = 'F1'",
                "[\"scikit-learn\"]"},
            {"SELECT packages FROM information_schema.functions WHERE function_name = 'F2'",
                "[]"},
            {"SELECT packages FROM information_schema.functions WHERE function_name = 'F3'",
                "[]"},
            {"SELECT packages FROM information_schema.functions WHERE function_name = 'F4'",
                "[\"requests\"]"},
            {"SELECT packages FROM information_schema.functions WHERE function_name = 'F6'",
                "[\"scikit-learn\",\"requests\"]"},
            {"SELECT packages FROM information_schema.functions WHERE function_name = 'F7'",
                "[\"numpy\"]"},
            {"SELECT artifact_repository FROM information_schema.functions WHERE function_name = 'F1'",
                "snowflake.snowpark.pypi_shared_repository"},
        });
    }

    @Test
    public void proceduresTellBothLists() {
        engine.execute("CREATE OR REPLACE PROCEDURE p1(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.11' HANDLER = 'h' PACKAGES = ('snowflake-snowpark-python') ARTIFACT_REPOSITORY = "
            + "snowflake.snowpark.pypi_shared_repository ARTIFACT_REPOSITORY_PACKAGES = ('scikit-learn') "
            + "EXECUTE AS CALLER AS $$\ndef h(session, x):\n    return x\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE p2(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.11' HANDLER = 'h' ARTIFACT_REPOSITORY = snowflake.snowpark.pypi_shared_repository "
            + "ARTIFACT_REPOSITORY_PACKAGES = ('snowflake-snowpark-python') AS $$\ndef h(session, x):\n  "
            + "  return x\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE p3(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.11' HANDLER = 'h' PACKAGES = ('numpy') ARTIFACT_REPOSITORY = "
            + "snowflake.snowpark.pypi_shared_repository ARTIFACT_REPOSITORY_PACKAGES = "
            + "('snowflake-snowpark-python') AS $$\ndef h(session, x):\n    return x\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE p4(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.11' HANDLER = 'h' ARTIFACT_REPOSITORY = snowflake.snowpark.pypi_shared_repository "
            + "ARTIFACT_REPOSITORY_PACKAGES = ('requests') AS $$\ndef h(session, x):\n    return x\n$$");
        assertCells(new String[][] {
            {"DESCRIBE PROCEDURE p1(NUMBER) ->> SELECT LISTAGG(\"property\", '/') FROM $1",
                "signature/returns/language/null handling/volatility/execute "
                + "as/body/imports/handler/runtime_version/packages/artifact_repository/"
                + "artifact_repository_packages/installed_packages/artifact_repository_installed_packages"},
            {"DESCRIBE PROCEDURE p1(NUMBER) ->> SELECT LISTAGG(\"property\" || '=' || \"value\", ' / ') "
                + "WITHIN GROUP (ORDER BY \"property\") FROM $1 WHERE \"property\" IN ('packages', "
                + "'artifact_repository', 'artifact_repository_packages')",
                "artifact_repository=snowflake.snowpark.pypi_shared_repository / "
                + "artifact_repository_packages=['scikit-learn'] / packages=['snowflake-snowpark-python']"},
            {"DESCRIBE PROCEDURE p2(NUMBER) ->> SELECT LISTAGG(\"property\" || '=' || \"value\", ' / ') "
                + "WITHIN GROUP (ORDER BY \"property\") FROM $1 WHERE \"property\" IN ('packages', "
                + "'artifact_repository', 'artifact_repository_packages')",
                "artifact_repository=snowflake.snowpark.pypi_shared_repository / "
                + "artifact_repository_packages=['snowflake-snowpark-python'] / packages=[]"},
            {"DESCRIBE PROCEDURE p3(NUMBER) ->> SELECT LISTAGG(\"property\" || '=' || \"value\", ' / ') "
                + "WITHIN GROUP (ORDER BY \"property\") FROM $1 WHERE \"property\" IN ('packages', "
                + "'artifact_repository', 'artifact_repository_packages')",
                "artifact_repository=snowflake.snowpark.pypi_shared_repository / "
                + "artifact_repository_packages=['snowflake-snowpark-python'] / packages=['numpy']"},
            {"SELECT packages FROM information_schema.procedures WHERE procedure_name = 'P1'",
                "[\"scikit-learn\"]"},
            {"CREATE OR REPLACE PROCEDURE e1(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'h' ARTIFACT_REPOSITORY = snowflake.snowpark.pypi_shared_repository "
                + "ARTIFACT_REPOSITORY_PACKAGES = () AS $$\ndef h(session, x):\n    return x\n$$",
                "REFUSED Python Interpreter Error: | ModuleNotFoundError: No module named "
                + "'snowflake.snowpark' in function E1 with handler h"},
        });
    }

    @Test
    public void theClauseIsRefusedInTheAccountsWords() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION e1(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'h' ARTIFACT_REPOSITORY_PACKAGES = ('scikit-learn') AS $$\ndef h(x):\n    "
                + "return x\n$$",
                "REFUSED Artifact Repository Packages requires a valid Artifact Repository."},
            {"CREATE OR REPLACE FUNCTION e2(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'h' PACKAGES = ('numpy') ARTIFACT_REPOSITORY_PACKAGES = () AS $$\ndef "
                + "h(x):\n    return x\n$$",
                "REFUSED Artifact Repository Packages requires a valid Artifact Repository."},
            {"CREATE OR REPLACE FUNCTION e3(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'h' ARTIFACT_REPOSITORY = snowflake.snowpark.pypi_shared_repository "
                + "ARTIFACT_REPOSITORY_PACKAGES = 'requests' AS $$\ndef h(x):\n    return x\n$$",
                "REFUSED Invalid value specified for property 'ARTIFACT_REPOSITORY_PACKAGES', use 'constant "
                + "list' instead."},
            {"CREATE OR REPLACE FUNCTION e4(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'h' ARTIFACT_REPOSITORY = snowflake.snowpark.pypi_shared_repository "
                + "ARTIFACT_REPOSITORY_PACKAGES = requests AS $$\ndef h(x):\n    return x\n$$",
                "REFUSED Invalid value specified for property 'ARTIFACT_REPOSITORY_PACKAGES', use 'constant "
                + "list' instead."},
            {"CREATE OR REPLACE FUNCTION e5(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'h' ARTIFACT_REPOSITORY = snowflake.snowpark.pypi_shared_repository "
                + "ARTIFACT_REPOSITORY_PACKAGES = (requests) AS $$\ndef h(x):\n    return x\n$$",
                "REFUSED Invalid value specified for property 'ARTIFACT_REPOSITORY_PACKAGES', use 'single "
                + "quoted string literals' instead."},
            {"CREATE OR REPLACE FUNCTION e6(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'h' ARTIFACT_REPOSITORY = snowflake.snowpark.pypi_shared_repository "
                + "ARTIFACT_REPOSITORY_PACKAGES = (1) AS $$\ndef h(x):\n    return x\n$$",
                "REFUSED Invalid value specified for property 'ARTIFACT_REPOSITORY_PACKAGES', use 'single "
                + "quoted string literals' instead."},
            {"CREATE OR REPLACE FUNCTION e7(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'h' ARTIFACT_REPOSITORY = snowflake.snowpark.pypi_shared_repository "
                + "ARTIFACT_REPOSITORY_PACKAGES = (NULL) AS $$\ndef h(x):\n    return x\n$$",
                "REFUSED Invalid value specified for property 'ARTIFACT_REPOSITORY_PACKAGES', use 'single "
                + "quoted string literals' instead."},
            {"CREATE OR REPLACE FUNCTION e8(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'h' ARTIFACT_REPOSITORY = snowflake.snowpark.pypi_shared_repository "
                + "ARTIFACT_REPOSITORY_PACKAGES = (a.b) AS $$\ndef h(x):\n    return x\n$$",
                "REFUSED Invalid value specified for property 'ARTIFACT_REPOSITORY_PACKAGES', use 'single "
                + "quoted string literals' instead."},
            {"CREATE OR REPLACE FUNCTION e9(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'h' ARTIFACT_REPOSITORY = snowflake.snowpark.pypi_shared_repository "
                + "ARTIFACT_REPOSITORY_PACKAGES = ('a', 2.5) AS $$\ndef h(x):\n    return x\n$$",
                "REFUSED Invalid value specified for property 'ARTIFACT_REPOSITORY_PACKAGES', use 'single "
                + "quoted string literals' instead."},
            {"CREATE OR REPLACE FUNCTION e10(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'h' ARTIFACT_REPOSITORY_PACKAGES = 'requests' ARTIFACT_REPOSITORY = 'x' AS "
                + "$$\ndef h(x):\n    return x\n$$",
                "REFUSED Invalid value specified for property 'ARTIFACT_REPOSITORY_PACKAGES', use 'constant "
                + "list' instead."},
            {"CREATE OR REPLACE FUNCTION e11(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'h' ARTIFACT_REPOSITORY = 'x' ARTIFACT_REPOSITORY_PACKAGES = ('requests') "
                + "AS $$\ndef h(x):\n    return x\n$$",
                "REFUSED SQL compilation error: | invalid value ''x'' for property 'ARTIFACT_REPOSITORY'"},
            {"CREATE OR REPLACE FUNCTION s1(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'h' ARTIFACT_REPOSITORY = snowflake.snowpark.pypi_shared_repository "
                + "ARTIFACT_REPOSITORY_PACKAGES ('requests') AS $$\ndef h(x):\n    return x\n$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 203 unexpected '('."},
            {"CREATE OR REPLACE FUNCTION s2(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'h' ARTIFACT_REPOSITORY = snowflake.snowpark.pypi_shared_repository "
                + "ARTIFACT_REPOSITORY_PACKAGES = ('requests',) AS $$\ndef h(x):\n    return x\n$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 217 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION s3(x NUMBER) RETURNS NUMBER LANGUAGE PYTHON RUNTIME_VERSION = "
                + "'3.11' HANDLER = 'h' ARTIFACT_REPOSITORY = snowflake.snowpark.pypi_shared_repository "
                + "ARTIFACT_REPOSITORY_PACKAGES = ('a' 'b') AS $$\ndef h(x):\n    return x\n$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 210 unexpected ''b''."},
            {"CREATE OR REPLACE FUNCTION j1(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.h' "
                + "ARTIFACT_REPOSITORY = snowflake.snowpark.pypi_shared_repository ARTIFACT_REPOSITORY_PACKAGES"
                + " = ('a') AS $$ class H { public static long h(long x) { return x; } } $$",
                "REFUSED SQL compilation error: | invalid property 'ARTIFACT_REPOSITORY_PACKAGES' for "
                + "'FUNCTION'"},
            {"CREATE OR REPLACE FUNCTION j2(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.h' "
                + "ARTIFACT_REPOSITORY_PACKAGES = ('a') ARTIFACT_REPOSITORY = "
                + "snowflake.snowpark.pypi_shared_repository AS $$ class H { public static long h(long x) { "
                + "return x; } } $$",
                "REFUSED SQL compilation error: | invalid property 'ARTIFACT_REPOSITORY_PACKAGES' for "
                + "'FUNCTION'"},
            {"CREATE OR REPLACE FUNCTION j3(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.h' "
                + "ARTIFACT_REPOSITORY_PACKAGES = ('a') AS $$ class H { public static long h(long x) { return "
                + "x; } } $$",
                "REFUSED Artifact Repository Packages requires a valid Artifact Repository."},
            {"CREATE OR REPLACE FUNCTION j4(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H.h' "
                + "ARTIFACT_REPOSITORY_PACKAGES = 'a' AS $$ class H { public static long h(long x) { return x; "
                + "} } $$",
                "REFUSED Invalid value specified for property 'ARTIFACT_REPOSITORY_PACKAGES', use 'constant "
                + "list' instead."},
            {"CREATE OR REPLACE FUNCTION j5(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT ARTIFACT_REPOSITORY"
                + " = snowflake.snowpark.pypi_shared_repository ARTIFACT_REPOSITORY_PACKAGES = ('a') AS 'return"
                + " X;'",
                "REFUSED SQL compilation error: | invalid property 'ARTIFACT_REPOSITORY_PACKAGES' for "
                + "'FUNCTION'"},
            {"CREATE OR REPLACE FUNCTION j6(x NUMBER) RETURNS NUMBER ARTIFACT_REPOSITORY_PACKAGES = ('a') "
                + "AS 'x'",
                "REFUSED Artifact Repository Packages requires a valid Artifact Repository."},
            {"CREATE OR REPLACE PROCEDURE j7() RETURNS VARCHAR LANGUAGE SQL ARTIFACT_REPOSITORY = "
                + "snowflake.snowpark.pypi_shared_repository ARTIFACT_REPOSITORY_PACKAGES = ('a') AS 'BEGIN "
                + "RETURN 1; END'",
                "REFUSED SQL compilation error: | invalid property 'ARTIFACT_REPOSITORY_PACKAGES' for "
                + "'FUNCTION'"},
            {"CREATE OR REPLACE FUNCTION j8(x NUMBER) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = "
                + "'2.12' HANDLER = 'H.h' ARTIFACT_REPOSITORY = snowflake.snowpark.pypi_shared_repository "
                + "ARTIFACT_REPOSITORY_PACKAGES = ('a') AS $$ object H { def h(x: Long): Long = x } $$",
                "REFUSED SQL compilation error: | invalid property 'ARTIFACT_REPOSITORY_PACKAGES' for "
                + "'FUNCTION'"},
        });
    }
}
