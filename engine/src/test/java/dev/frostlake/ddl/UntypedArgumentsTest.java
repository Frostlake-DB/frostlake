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
 * A routine's or a policy's argument written without a type parses, and the statement is refused for the first such
 * argument, {@code Missing data type for argument 'A'}, once what it says of itself is judged: a required argument
 * after an optional one, a width, the language, the schema, an unknown type anywhere in the signature, then a repeated
 * name and a returned column's repeated name all come first, a policy's taken name too, while a returned column with
 * no type comes after. A type slot reads any name as a type, so a fault after a short one is told where it stands,
 * the escape words d, t, ts, fn and oj being no names there, and an external function refuses a name that is no type
 * in a sentence of its own (all live-verified).
 */
public class UntypedArgumentsTest extends MeasuredCellsTestSupport {

    private static final String MISSING_A = "REFUSED Missing data type for argument 'A'";

    private static final String MISSING_V = "REFUSED Missing data type for argument 'V'";

    private static final String REPEATS_V = "REFUSED Argument 'V' repeats in the function signature.";

    private static final String NO_SUCH_TYPE = "REFUSED SQL compilation error: | Unsupported data type 'NOSUCH'.";

    private static final String NO_SUCH_SCHEMA = "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA' does "
        + "not exist or not authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on "
        + "SCHEMA TEST_DB.NOSUCH_SCHEMA.";

    private static final String EXTERNAL = " API_INTEGRATION = nosuch_api AS 'https://example.com/x'";

    /** A syntax error's answer at a position of the statement's first line. */
    private static String syntaxError(final int position, final String token) {
        return "REFUSED SQL compilation error: | syntax error line 1 at position " + position + " unexpected '"
            + token + "'.";
    }

    /** The answer of a width out of range at a position of the statement's first line. */
    private static String zeroWidth(final int position) {
        return "REFUSED SQL compilation error: error line 1 at position " + position + " | Invalid character length: "
            + "0. Must be between 1 and 134,217,728.";
    }

    /** The answer of a required argument written after an optional one. */
    private static String requiredAfterOptional(final int position, final String required, final String optional) {
        return "REFUSED SQL compilation error: error line 1 at position " + position + " | required argument "
            + required + " cannot follow optional argument " + optional;
    }

    /** The answer of an external function's name that is no type. */
    private static String externalType(final String name, final String place) {
        return "REFUSED SQL compilation error: External functions may not have datatype '" + name + "' as " + place;
    }

    @Test
    public void aRoutinesArgumentWithoutAType() {
        assertCells(new String[][] {
            {"CREATE FUNCTION fp2(a) RETURNS INT AS '1'", MISSING_A},
            {"CREATE FUNCTION fp3(a INT, b) RETURNS INT AS '1'", "REFUSED Missing data type for argument 'B'"},
            {"CREATE FUNCTION fp4(a, b INT) RETURNS INT AS '1'", MISSING_A},
            {"CREATE FUNCTION fq24(a, b) RETURNS INT AS '1'", MISSING_A},
            {"CREATE FUNCTION fq25(\"a\") RETURNS INT AS '1'", "REFUSED Missing data type for argument 'a'"},
            {"CREATE FUNCTION fq29(case) RETURNS INT AS '1'", "REFUSED Missing data type for argument 'CASE'"},
            {"CREATE FUNCTION fq8(a) RETURNS INT LANGUAGE JAVASCRIPT AS 'return 1;'", MISSING_A},
            {"CREATE FUNCTION fq41(a) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' HANDLER = 'h' AS 'def "
                + "h(a): return 1'", MISSING_A},
            {"CREATE FUNCTION fq42(a) RETURNS INT LANGUAGE JAVA HANDLER = 'X.h' AS 'class X { public static int "
                + "h(int a) { return 1; } }'", MISSING_A},
            {"CREATE FUNCTION fq9(a) RETURNS INT AS 'SELECT nosuch_col'", MISSING_A},
            {"CREATE OR REPLACE FUNCTION fq10(a) RETURNS INT AS '1'", MISSING_A},
            {"CREATE FUNCTION IF NOT EXISTS fq11(a) RETURNS INT AS '1'", MISSING_A},
            {"CREATE FUNCTION fq26(a) RETURNS TABLE (c INT) AS 'SELECT 1'", MISSING_A},
            {"CREATE FUNCTION fq38(a) RETURNS TABLE (c) AS 'SELECT 1'", MISSING_A},
            {"CREATE FUNCTION IDENTIFIER(abc) RETURNS INT AS '1'", "REFUSED Missing data type for argument 'ABC'"},
            {"CREATE PROCEDURE pp2(a) RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'", MISSING_A},
            {"CREATE PROCEDURE pq1(a) RETURNS INT LANGUAGE JAVASCRIPT AS 'return 1;'", MISSING_A},
            {"CREATE PROCEDURE pq2(a INT, b) RETURNS INT LANGUAGE SQL AS $$BEGIN RETURN 1; END$$",
                "REFUSED Missing data type for argument 'B'"},
            {"WITH pw AS PROCEDURE (a) RETURNS INT LANGUAGE SQL AS $$BEGIN RETURN 1; END$$ CALL pw(1)", MISSING_A},
            {"CREATE FUNCTION fp8(a INT DEFAULT 1, b) RETURNS INT AS '1'", requiredAfterOptional(37, "B", "A")},
        });
    }

    @Test
    public void whatARoutineIsRefusedForFirst() {
        assertCells(new String[][] {
            {"CREATE FUNCTION fq3(a, b nosuch) RETURNS INT AS '1'", NO_SUCH_TYPE},
            {"CREATE FUNCTION fq4(a nosuch, b) RETURNS INT AS '1'", NO_SUCH_TYPE},
            {"CREATE FUNCTION fq5(a) RETURNS nosuch AS '1'", NO_SUCH_TYPE},
            {"CREATE FUNCTION fq7(a, a INT) RETURNS INT AS '1'", "REFUSED Argument 'A' repeats in the function "
                + "signature."},
            {"CREATE FUNCTION fq33(a) RETURNS INT LANGUAGE NOSUCHLANG AS '1'",
                "REFUSED SQL compilation error: | Unknown function language: NOSUCHLANG."},
            {"CREATE PROCEDURE pq3(a) RETURNS INT LANGUAGE NOSUCHLANG AS '1'",
                "REFUSED SQL compilation error: | Unknown function language: NOSUCHLANG."},
            {"CREATE FUNCTION nosuch_schema.fq34(a) RETURNS INT AS '1'", NO_SUCH_SCHEMA},
            {"CREATE FUNCTION fq35(a VARCHAR(0), b) RETURNS INT AS '1'", zeroWidth(31)},
            {"CREATE FUNCTION fq36(a) RETURNS VARCHAR(0) AS '1'", zeroWidth(40)},
            {"CREATE FUNCTION fq37(a) RETURNS INT EXECUTE AS CALLER AS '1'",
                "REFUSED Unsupported invocation type for function."},
            {"CREATE FUNCTION fq39(a) RETURNS TABLE (c INT, c INT) AS 'SELECT 1, 2'",
                "REFUSED Return signature contains a duplicate column name 'C'."},
            {"CREATE FUNCTION fq40(a, b INT DEFAULT 1, c) RETURNS INT AS '1'", requiredAfterOptional(41, "C", "B")},
            {"CREATE FUNCTION fz3(a INT) RETURNS TABLE (c, c INT) AS 'SELECT 1, 2'",
                "REFUSED Return signature contains a duplicate column name 'C'."},
            {"CREATE FUNCTION fz4(a) RETURNS TABLE (c INT DEFAULT 1, d INT) AS 'SELECT 1, 2'",
                requiredAfterOptional(55, "D", "C")},
            {"CREATE FUNCTION fz5(a) RETURNS TABLE (c INT, d, c INT) AS 'SELECT 1, 2, 3'",
                "REFUSED Return signature contains a duplicate column name 'C'."},
            {"CREATE FUNCTION fz6(a INT) RETURNS TABLE (c INT DEFAULT 1, d, d INT) AS 'SELECT 1, 2, 3'",
                requiredAfterOptional(59, "D", "C")},
            {"CREATE FUNCTION fz7(a INT, a INT) RETURNS TABLE (c INT DEFAULT 1, d INT) AS 'SELECT 1, 2'",
                requiredAfterOptional(66, "D", "C")},
            {"CREATE FUNCTION fq18(a) RETURNS INT AS '1'; SELECT 1 +", syntaxError(54, "<EOF>")},
            {"CREATE FUNCTION fq23(a) RETURNS INT AS '1' garbage", syntaxError(43, "garbage")},
        });
    }

    @Test
    public void aPolicysArgumentWithoutATypeOrRepeated() {
        engine.execute("CREATE MASKING POLICY mp1 AS (v STRING) RETURNS STRING -> v");
        assertCells(new String[][] {
            {"CREATE MASKING POLICY mpp2 AS (v) RETURNS STRING -> v", MISSING_V},
            {"CREATE MASKING POLICY mq1 AS (v, w INT) RETURNS STRING -> v", MISSING_V},
            {"CREATE MASKING POLICY mq5 AS (v) RETURNS STRING -> w", MISSING_V},
            {"CREATE OR REPLACE MASKING POLICY mq12 AS (v) RETURNS STRING -> v", MISSING_V},
            {"CREATE MASKING POLICY IF NOT EXISTS mq13 AS (v) RETURNS STRING -> v", MISSING_V},
            {"CREATE MASKING POLICY mp1 AS (v) RETURNS STRING -> v",
                "REFUSED SQL compilation error: | Object 'MP1' already exists."},
            {"CREATE MASKING POLICY mq6 AS (v, v STRING) RETURNS STRING -> v", REPEATS_V},
            {"CREATE MASKING POLICY mq8 AS (v STRING, v STRING) RETURNS STRING -> v", REPEATS_V},
            {"CREATE MASKING POLICY mq9 AS (v VARCHAR(0), w) RETURNS STRING -> v", zeroWidth(40)},
            {"CREATE MASKING POLICY mq10 AS (v) RETURNS VARCHAR(0) -> v", zeroWidth(50)},
            {"CREATE MASKING POLICY nosuch_schema.mq11 AS (v) RETURNS STRING -> v", NO_SUCH_SCHEMA},
            {"CREATE MASKING POLICY mq7 AS (v) RETURNS nosuch -> v", NO_SUCH_TYPE},
            {"CREATE ROW ACCESS POLICY rq1 AS (v) RETURNS BOOLEAN -> TRUE", MISSING_V},
            {"CREATE ROW ACCESS POLICY rq2 AS (v) RETURNS STRING -> TRUE", MISSING_V},
            {"CREATE ROW ACCESS POLICY rq8 AS (v STRING, v STRING) RETURNS BOOLEAN -> TRUE", REPEATS_V},
            {"CREATE ROW ACCESS POLICY rq9 AS (v STRING, v) RETURNS BOOLEAN -> TRUE", REPEATS_V},
            {"CREATE PROJECTION POLICY jq1 AS (v) RETURNS PROJECTION_CONSTRAINT -> PROJECTION_CONSTRAINT(ALLOW => "
                + "TRUE)", MISSING_V},
            {"CREATE AGGREGATION POLICY gq1 AS (v) RETURNS AGGREGATION_CONSTRAINT -> NO_AGGREGATION_CONSTRAINT()",
                MISSING_V},
            {"CREATE JOIN POLICY jnq AS (v) RETURNS JOIN_CONSTRAINT -> JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)",
                MISSING_V},
            {"CREATE PRIVACY POLICY pvq AS (v) RETURNS PRIVACY_BUDGET -> NO_PRIVACY_POLICY()", MISSING_V},
            {"CREATE STORAGE LIFECYCLE POLICY slq AS (v) RETURNS BOOLEAN -> TRUE", MISSING_V},
            {"CREATE STORAGE LIFECYCLE POLICY slq2 AS (v STRING, v) RETURNS BOOLEAN -> TRUE", REPEATS_V},
        });
    }

    @Test
    public void anExternalFunctionsNameThatIsNoType() {
        assertCells(new String[][] {
            {"CREATE EXTERNAL FUNCTION efp1(a b) RETURNS INT" + EXTERNAL, externalType("B", "arguments")},
            {"CREATE EXTERNAL FUNCTION efq5(c x.y) RETURNS INT" + EXTERNAL, externalType("Y", "arguments")},
            {"CREATE EXTERNAL FUNCTION efq9(a nosuch, b) RETURNS INT" + EXTERNAL, externalType("NOSUCH", "arguments")},
            {"CREATE EXTERNAL FUNCTION efq10(a nosuch) RETURNS nosuch2" + EXTERNAL,
                externalType("NOSUCH", "arguments")},
            {"CREATE OR REPLACE EXTERNAL FUNCTION efq12(a \"nosuch\") RETURNS INT" + EXTERNAL,
                externalType("nosuch", "arguments")},
            {"CREATE EXTERNAL FUNCTION efq6(a INT) RETURNS \"q\"" + EXTERNAL, externalType("q", "its return type")},
            {"CREATE EXTERNAL FUNCTION efq7(a INT) RETURNS x.y" + EXTERNAL, externalType("Y", "its return type")},
            {"CREATE EXTERNAL FUNCTION efq14(a, b) RETURNS nosuch" + EXTERNAL,
                externalType("NOSUCH", "its return type")},
            {"CREATE EXTERNAL FUNCTION efq2(a nosuch(5)) RETURNS INT" + EXTERNAL,
                "REFUSED SQL compilation error: | Unsupported data type 'nosuch(5)'."},
            {"CREATE EXTERNAL FUNCTION efq8(a INT) RETURNS nosuch(5)" + EXTERNAL,
                "REFUSED SQL compilation error: | Unsupported data type 'nosuch(5)'."},
            {"CREATE EXTERNAL FUNCTION efq1(a) RETURNS INT" + EXTERNAL,
                "REFUSED SQL compilation error: | Integration 'NOSUCH_API' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on INTEGRATION NOSUCH_API."},
        });
    }

    @Test
    public void aShortNameAndAnEscapeWordWhereATypeStands() {
        assertCells(new String[][] {
            {"CREATE FUNCTION fp1(a b) RETURNS INT AS '1'", "REFUSED SQL compilation error: | Unsupported data type "
                + "'B'."},
            {"CREATE FUNCTION fp6(a b c) RETURNS INT AS '1'", syntaxError(24, "c")},
            {"CREATE TABLE tq2 (c b c2)", syntaxError(22, "c2")},
            {"CREATE FUNCTION fq22(a b) RETURNS INT AS '1'; SELECT 1 +", syntaxError(56, "<EOF>")},
            {"CREATE FUNCTION fq20(a b(1) c) RETURNS INT AS '1'", syntaxError(28, "c")},
            {"SELECT 1::d", syntaxError(10, "d")},
            {"SELECT 1::D", syntaxError(10, "D")},
            {"SELECT CAST(1 AS t)", syntaxError(17, "t")},
            {"CREATE TABLE tq3 (a ts)", syntaxError(20, "ts")},
            {"CREATE TABLE tq4 (a fn)", syntaxError(20, "fn")},
            {"CREATE TABLE tq5 (a oj)", syntaxError(20, "oj")},
            {"CREATE TABLE tq6 (a dd)", "REFUSED SQL compilation error: | Unsupported data type 'DD'."},
            {"CREATE TABLE tq7 (a INT, b d)", syntaxError(27, "d")},
            {"CREATE FUNCTION fq51(a INT) RETURNS t AS '1'", syntaxError(36, "t")},
        });
    }
}
