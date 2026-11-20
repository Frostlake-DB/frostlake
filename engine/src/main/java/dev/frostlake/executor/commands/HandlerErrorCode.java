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

import dev.frostlake.executor.CallDepth;
import dev.frostlake.executor.SqlAccessControlError;
import dev.frostlake.executor.StatementCountMismatch;
import dev.frostlake.metastore.HybridKeys;
import dev.frostlake.transaction.TransactionManager;

/**
 * The SQLCODE and SQLSTATE a block's handler reads for a refusal the engine raised, by the refusal's sentence
 * (live-verified): a syntax error — one in the text EXECUTE IMMEDIATE runs, a string LIMIT, a routine's body — is
 * 1003 / 42000 and a SQL function's or a policy's body that does not compile 90203 / 42601; an invalid identifier is
 * 904 / 42000, a missing object 2003 / 42S02 and a schema USE cannot enter 2043 / 02000; a name already taken is
 * 2002 / 42710, a column's 1430 / 42601 and an overload that would compete with another 949 / 42723; an unknown
 * function or procedure 2139 / 02000 and a wrong argument count 938 or 939 / 22023 (909 / 22023 where the sentence
 * reads "invalid number of arguments"); a column alias list of another length than its relation's columns
 * 1087 / 42601; a text that is no number
 * 100038 / 22018 and no date 100040 / 22007; a division by zero 100051 / 22012; a scalar subquery of several rows
 * 90150 / 22000; a column outside the grouping 1104 / 42601; a SELECT … INTO of several rows 92219 / 22000, an
 * ambiguous column 2028 / 42601 and an infinity a statement binds 2086 / 22000; a statement refused because the
 * session's transaction was aborted 624 / 57014 and the COMMIT of such a transaction 670 / 57014; a statement that
 * waited for another transaction's table lock until its LOCK_TIMEOUT passed 627 / 57014, one that could not wait
 * 626 / 57014 and one refused as a deadlock's victim 628 / 40P01; a statement that would end another scope's transaction 90239 / 25000 and a block or a procedure that
 * left its own open 90232 / 25000; a body ended past the depth bodies run to 90235 / 42601; an expression a VALUES
 * clause does not take 2014 / 22000, an INSERT whose values do not match its columns 2020 / 21S01, a result asked of a
 * statement that failed 711 / 02000, a text that is no JSON 100069 / 22P02 and a staged file INFER_SCHEMA cannot type
 * 110001 / 22000, a staged file its format's COMPRESSION cannot read 100079 / 22000, and a PUT's compression it does
 * not know 200006 / 0A000 or does not upload 200004 / 0A000; an OPEN of an open cursor 92215 / P0000
 * and a CLOSE of one that is not open 92201 / P0000; a RESULTSET read before anything was given to it 92212 / P0000;
 * a BREAK, a CONTINUE or one of their other spellings outside a loop 92208 / P0000 and naming no loop around it
 * 92209 / P0000;
 * the report of an exception a Java or a Scala procedure's handler raised, a statement it ran that failed included,
 * 100315 / P0000, and of one a Python handler raised, or of a value it returned that the procedure's RETURNS does not
 * take, 100357 / P0000; a table a RETURNS TABLE procedure returned in a shape it does not declare 92230 / 42804. A
 * routine's or a policy's signature refused is
 * 42601, by what it got wrong: a required argument after an optional one 946, a repeated argument name 90200, a
 * repeated column name 90211, an argument with no type 90201, an external function's type 2176 for an argument and
 * 2177 for its result. A constraint that is not there is 1450 / 42601, a key that is not deferrable and initially
 * deferred all the same 1460 and key properties that contradict each other 1461 / 42601, and a column of the
 * clustering key renamed 3320 and dropped 3203 / 42601; a user named by more parts than a name has 906 / 42000. A refusal of no known family reads 100351 / P0000.
 * A hybrid table's primary key already held is 200001 / 22000 and a foreign key it breaks 200009 / 22000, and a
 * privilege refusal - an access-control error, {@code Insufficient privileges to operate on …} - 3001 / 42501
 * (documented). A REVOKE that skipped grants others depend on, {@code Revoke partially executed: …}, is 3935 / 23001.
 *
 * <p>A client's driver reads the same pairs, and more families besides (live-verified): a missing object
 * 2003 / 42S02 where the sentence calls it an object, a table or a view, 2003 / 02000 where it names any other kind
 * - a schema, a database, a stage, a sequence, a routine, a file format, a stream, a task, a pipe, a materialized
 * view, a policy, a tag - and 2001 / 02000 for a routine named by its signature; a missing column 1431 / 42601 and
 * a dropped table no UNDROP finds 90025 / 02000; a text that is no timestamp 100035 / 22007, no time 100108 /
 * 22007, no BOOLEAN 100037 / 22018, a number out of its column's range 100039 or 100046 / 22003, a text too long
 * for its column 100078 / 22000, a VARIANT no cast takes 100071 / 22000, no hex 100115 and no base64 100114 /
 * 22000, no XML 100100 / 22P02 and no regular expression 100048 / 2201B, a text no format parses 100096 as a
 * timestamp and 100097 as a date / 22007; a MERGE matching a row twice 100090 / 42P18; an unknown table function
 * 2142 / 42601; an expression no GROUP BY or ORDER BY takes 979 or 2024 / 42601, a duplicate column 2025 / 42S21,
 * an unsupported type 2040 / 42601, an unknown parameter 1006 and a value it does not take 1008 / 22023, a type a
 * call or a cast does not take 1065, 1007 / 22023 and 1044 / 42P13, a cumulative frame a window function does not take 2303 / 0A000, a WITHIN GROUP a function does not take 2070 / 42601, a file
 * format's type changed 2134 and an option its type does not take 2135 / 42601, a set operation's column counts 1789 / 42601,
 * a parameter out of range 1817 / 42601, time travel past what is kept 707 / 02000, a date part no function knows
 * 2151 / 22023, a predicate of no BOOLEAN 2034 / 22000, a ranking window with no ORDER BY 2061 / 42601, a table
 * built of no named column 2022 / 42601, a table's last column dropped 1432 / 42601 and a column's type changed
 * to one it cannot hold 2108 / 22000, a NOT NULL over a column holding a NULL 1471 / 42601, a MERGE's ALL BY NAME
 * over a source of another column count 2032 / 21000 and one missing a target column 2033 / 42601, a WHEN
 * clause no row reaches 2045 / 42601, an INSERT into no table 1759 / 42601, nested aggregates 2082 / 42601, a
 * USING column no side holds 2004 / 42601 and a statement no ID names 709 / 02000. A NULL in a NOT NULL column is
 * 22000 to the client where the handler reads 00000.
 */
final class HandlerErrorCode {

    private static final String UNINITIALIZED_RESULTSET = "Stored procedure execution error: uninitialized resultSet '";

    /** How the report of an exception a Java or a Scala handler raised starts. */
    private static final String USER_ERROR_REPORT = "User Error Report: ";

    /** How the refusal of a value a Python handler returned that its procedure's RETURNS does not take starts. */
    private static final String PYTHON_RETURN_REFUSAL = "Return value should be a ";

    /** How the refusal of a table a RETURNS TABLE procedure returned in a shape it does not declare starts. */
    private static final String RETURNED_TABLE_MISMATCH =
        "Stored procedure execution error: data type of returned table does not match";

    /** How this engine's report of an exception a Python handler raised starts. */
    private static final String PYTHON_REPORT = "Error executing Python ";

    /** How the account's report of an exception a Python handler raised starts. */
    private static final String PYTHON_INTERPRETER_ERROR = "Python Interpreter Error:";

    private static final HandlerErrorCode OTHER = new HandlerErrorCode(100351, "P0000");

    /** How a privilege refusal's sentence starts after its access-control prefix. */
    private static final String INSUFFICIENT_PRIVILEGES = "Insufficient privileges to operate on ";

    /** How a missing object's sentence goes on after its name. */
    private static final String NOT_AUTHORIZED = "' does not exist or not authorized";

    private final int code;
    private final String state;
    private final String clientState;

    private HandlerErrorCode(final int code, final String state) {
        this(code, state, state);
    }

    private HandlerErrorCode(final int code, final String state, final String clientState) {
        this.code = code;
        this.state = state;
        this.clientState = clientState;
    }

    /**
     * The code and state of a refusal's sentence.
     *
     * @param message the refusal as the handler's SQLERRM reads it
     * @return the pair
     */
    static HandlerErrorCode of(final String message) {
        final HandlerErrorCode known = known(message);
        return known != null ? known : OTHER;
    }

    /**
     * The families a running object's state, an overflowing sum or a property's value is refused with
     * (live-verified): a non-root task's own retry parameter 91449 / 55000, a graph changed under a root that is not
     * suspended 91421 / 22000, a triggered serverless task without its completion interval 91865 / 55000, an alert's
     * CONFIG that spells no JSON object 91461 / 42601 and a change to a started alert 392005 / 55000; a SUM's raw
     * total past its carrier 100058 / 22000 and an ordered window's intermediate result past it 100045 / 22003; a
     * retention past 90 days 1048 / 22023; an address a network policy's IP list cannot hold 3043 / 22023 and a
     * stage's credentials left blank 3003 / 42501; a required option left out 2029 / 42601 and an object of another
     * kind than the statement names 2203 / 02000; a stage location that is no URL 1021 / 42601 and a bucket name it
     * does not take 91006 / 22000; a stage's ENDPOINT beside a location of another storage type
     * 1074 / 22023, one the account does not allow 1075 / 22023 and one that is no host 3647 / 42601; a stream on an
     * external table that is not INSERT_ONLY 91104 / 22023 and an INSERT_ONLY stream on anything else 91938 / 0A000;
     * a dynamic table's definition holding a function that answers a new value each call 2711 / 42601, and a query
     * change tracking does not follow for a function whose value moves 91912, a subquery 91927 and a LIMIT 91908, all
     * 0A000;
     * a value a property does not take 1422 / 22023
     * and a name that is no property of its kind 1420 / 22023; a statement of no supported type 2041 / 42601, a
     * listing for want of a current pipe 1017 / 42601, a parameter a level does not take 1009 / 22023, and a word
     * that is no log level 394007 or no trace level 394011 / 42601.
     *
     * @param message the refusal's sentence
     * @return the pair, or null for a sentence of none of these families
     */
    private static HandlerErrorCode stateRefusal(final String message) {
        if (message.contains("Cannot set parameter ") && message.contains(" on non-root task ")) {
            return new HandlerErrorCode(91449, "55000");
        }
        if (message.contains("Unable to update graph with root task ")) {
            return new HandlerErrorCode(91421, "22000");
        }
        if (message.contains("TARGET_COMPLETION_INTERVAL must be set for serverless triggered task ")) {
            return new HandlerErrorCode(91865, "55000");
        }
        if (message.contains("Invalid config. Must be a string representation of a valid JSON Object.")) {
            return new HandlerErrorCode(91461, "42601");
        }
        if (message.contains("Unable to alter property for alert ")) {
            return new HandlerErrorCode(392005, "55000");
        }
        if (message.contains("Value overflow in a SUM aggregate")) {
            return new HandlerErrorCode(100058, "22000");
        }
        if (message.contains("Numeric value is out of range, error: Window function intermediate result too large")) {
            return new HandlerErrorCode(100045, "22003");
        }
        if (message.contains("Not supported IPv4 format for values '")) {
            return new HandlerErrorCode(3043, "22023");
        }
        if (message.contains("invalid value '") && message.contains("' for property '")) {
            return new HandlerErrorCode(1422, "22023");
        }
        if (message.contains("invalid property '") && message.contains("' for '")) {
            return new HandlerErrorCode(1420, "22023");
        }
        if (message.equals(StageCredentials.REQUIRED)) {
            return new HandlerErrorCode(3003, "42501");
        }
        if (message.contains("Missing option(s): ")) {
            return new HandlerErrorCode(2029, "42601");
        }
        if (message.contains("Object found is of type '") && message.contains("', not specified type '")) {
            return new HandlerErrorCode(2203, "02000");
        }
        if (message.contains("invalid URL: ") && !message.contains("invalid URL: [")) {
            return new HandlerErrorCode(1021, "42601");
        }
        if (message.startsWith("Bucket name '") && message.contains("' in the stage location is not supported.")) {
            return new HandlerErrorCode(91006, "22000");
        }
        if (message.contains("Endpoint cannot be used with storage type ")) {
            return new HandlerErrorCode(1074, "22023");
        }
        if (message.contains("Endpoint ") && message.endsWith(" not allowed.")) {
            return new HandlerErrorCode(1075, "22023");
        }
        if (message.contains("' invalid. Expect no scheme endpoint, for example: ")) {
            return new HandlerErrorCode(3647, "42601");
        }
        if (message.startsWith("Streams on External Tables or Iceberg tables with an external catalog must have"
                + " INSERT_ONLY set to true.")) {
            return new HandlerErrorCode(91104, "22023");
        }
        if (message.startsWith("Streams of type INSERT_ONLY can only be created on external tables")) {
            return new HandlerErrorCode(91938, "0A000");
        }
        if (message.contains("Invalid Dynamic Table definition. Non-deterministic function '")) {
            return new HandlerErrorCode(2711, "42601");
        }
        if (message.contains("but change tracking is not supported on queries with non-deterministic functions.")) {
            return new HandlerErrorCode(91912, "0A000");
        }
        if (message.contains("Change tracking is not supported on queries with subquery expressions.")) {
            return new HandlerErrorCode(91927, "0A000");
        }
        if (message.contains("Change tracking is not supported on queries with LIMIT.")) {
            return new HandlerErrorCode(91908, "0A000");
        }
        if (message.contains("Exceeds maximum allowable retention time (")) {
            return new HandlerErrorCode(1048, "22023");
        }
        if (message.contains("Unsupported statement type '")) {
            return new HandlerErrorCode(2041, "42601");
        }
        if (message.contains("This session does not have a current pipe")) {
            return new HandlerErrorCode(1017, "42601");
        }
        if (message.contains("cannot set parameter '")) {
            return new HandlerErrorCode(1009, "22023");
        }
        if (message.startsWith("Invalid value for log level.")) {
            return new HandlerErrorCode(394007, "42601");
        }
        if (message.startsWith("Invalid value for trace level.")) {
            return new HandlerErrorCode(394011, "42601");
        }
        return null;
    }

    /**
     * The families a routine's or a policy's signature and a table's key or clustering columns are refused with
     * (live-verified, by a handler and a client's driver alike): see the class's own list.
     *
     * @param message the refusal's sentence
     * @return the pair, or null for a sentence of none of these families
     */
    private static HandlerErrorCode signatureRefusal(final String message) {
        if (message.contains(" cannot follow optional argument ")) {
            return new HandlerErrorCode(946, "42601");
        }
        if (message.contains("' repeats in the function signature.")) {
            return new HandlerErrorCode(90200, "42601");
        }
        if (message.contains("Return signature contains a duplicate column name '")) {
            return new HandlerErrorCode(90211, "42601");
        }
        if (message.startsWith("Missing data type for argument '")) {
            return new HandlerErrorCode(90201, "42601");
        }
        if (message.contains("External functions may not have datatype '")) {
            return new HandlerErrorCode(message.endsWith("' as arguments") ? 2176 : 2177, "42601");
        }
        if (message.contains("constraint '") && message.endsWith("' does not exist")) {
            return new HandlerErrorCode(1450, "42601");
        }
        if (message.contains("user identifier '") && message.endsWith("' has too many qualifiers")) {
            return new HandlerErrorCode(906, "42000");
        }
        if (message.endsWith("Constraint is not deferrable")) {
            return new HandlerErrorCode(1460, "42601");
        }
        if (message.endsWith("Conflicting constraint properties")) {
            return new HandlerErrorCode(1461, "42601");
        }
        if (message.contains("' which belongs to a clustering key")) {
            return new HandlerErrorCode(message.startsWith("Cannot rename column '") ? 3320 : 3203, "42601");
        }
        return null;
    }

    /**
     * The families a geospatial input or operation is refused with (live-verified, all P0000): an input that is no
     * GeoJSON, (E)WKT or (E)WKB 100256, a BINARY that is no well-formed WKB 100254, a GEOGRAPHY coordinate outside
     * the longitude or latitude range 100205, a GEOMETRY coordinate that is no number 100395, and a GeometryCollection
     * given to ST_CONTAINS / ST_WITHIN 100249.
     *
     * @param message the refusal's sentence
     * @return the pair, or null for a sentence of none of these families
     */
    private static HandlerErrorCode geoRefusal(final String message) {
        if (message.startsWith("Error parsing Geo input: ")
                && message.endsWith(". Did not recognize valid GeoJSON, (E)WKT or (E)WKB.")) {
            return new HandlerErrorCode(100256, "P0000");
        }
        if (message.startsWith("Error parsing WKB input: ")) {
            return new HandlerErrorCode(100254, "P0000");
        }
        if (message.startsWith("GeoJSON::") && message.contains(": Invalid Lng/Lat pair: '")) {
            return new HandlerErrorCode(100205, "P0000");
        }
        if (message.startsWith("Invalid GeoJSON. Coordinate value is not numeric: '")) {
            return new HandlerErrorCode(100395, "P0000");
        }
        if (message.startsWith("Operation '")
                && message.endsWith("' is not supported for GeometryCollection and FeatureCollection.")) {
            return new HandlerErrorCode(100249, "P0000");
        }
        return null;
    }

    /**
     * The families a materialized view's definition is refused with (live-verified): more than one table 2212, a
     * source no table is read through 2213, a view read 2214, a window function 2216, a subquery 2219, a function
     * that is not deterministic 2220, a DISTINCT aggregate 2221, HAVING 2222, a LIMIT 2223, a GROUP BY key the select
     * list leaves out 2225, QUALIFY 2253, time travel 2274 and GROUP BY ALL 2282, all 42601; a join or a set
     * operation other than UNION 2235, ORDER BY 2239, an aggregate the view cannot keep 2242 and a grouping set
     * 2247, all 0A000; and COPY GRANTS with nothing replaced 2008 / 55000. Beside them, the families of tag-based
     * masking (live-verified, all 23001): a tag given a second policy of one type 391837, one statement setting two
     * policies of one type 391841, a column the tags give two policies 391842 and a tag carrying a policy that is
     * dropped 391846; a policy that is attached somewhere and is dropped or replaced 3531.
     *
     * @param message the refusal's sentence
     * @return the pair, or null for a sentence of none of these families
     */
    private static HandlerErrorCode viewAndTagPolicyRefusal(final String message) {
        if (message.contains("Invalid operation COPY GRANTS without specifying source object.")) {
            return new HandlerErrorCode(2008, "55000");
        }
        if (message.contains("Tag is already assigned to a masking policy ")) {
            return new HandlerErrorCode(391837, "23001");
        }
        if (message.contains("Multiple masking policies are specified with same data type ")) {
            return new HandlerErrorCode(391841, "23001");
        }
        if (message.contains(" is mapped to multiple masking policies by tags.")) {
            return new HandlerErrorCode(391842, "23001");
        }
        if (message.contains(" cannot be dropped/replaced as it is associated with one or more policies.")) {
            return new HandlerErrorCode(391846, "23001");
        }
        if (message.contains(" cannot be dropped/replaced as it is associated with one or more entities.")) {
            return new HandlerErrorCode(3531, "23001");
        }
        if (!message.contains("Invalid materialized view")) {
            return null;
        }
        final String[] sentences = {
            "More than one table referenced in the view definition",
            "Source table used in the definition is not accessible or been deleted.",
            "' should be a table.",
            "window functions not allowed in view definition.",
            "sub-queries not allowed in view materialized definition.",
            "having clause not allowed in view definition.",
            "limit clause not allowed in view definition.",
            "is not in the view definition's select list.",
            "qualify clause not allowed in view definition.",
            "Time travel on base table in line ",
            "GROUP BY ALL not allowed in MV definition.",
            "Join types [",
            "ORDER BY not allowed in view definition.",
            "Unsupported aggregate expression ",
            "Grouping sets, cube, rollup are not supported:",
        };
        final int[] codes = {2212, 2213, 2214, 2216, 2219, 2222, 2223, 2225, 2253, 2274, 2282, 2235, 2239, 2242, 2247};
        final String[] states = {"42601", "42601", "42601", "42601", "42601", "42601", "42601", "42601", "42601",
            "42601", "42601", "0A000", "0A000", "0A000", "0A000"};
        for (int i = 0; i < sentences.length; i++) {
            if (message.contains(sentences[i])) {
                return new HandlerErrorCode(codes[i], states[i]);
            }
        }
        if (message.contains("non deterministic function '")) {
            return new HandlerErrorCode(2220, "42601");
        }
        if (message.contains("distinct aggregate '")) {
            return new HandlerErrorCode(2221, "42601");
        }
        return null;
    }

    /**
     * The code and state of a refusal's sentence, or null for a sentence of no known family.
     *
     * @param message the refusal's sentence
     * @return the pair, or null
     */
    static HandlerErrorCode known(final String message) {
        if (message == null) {
            return null;
        }
        final HandlerErrorCode geo = geoRefusal(message);
        if (geo != null) {
            return geo;
        }
        final HandlerErrorCode materializedView = viewAndTagPolicyRefusal(message);
        if (materializedView != null) {
            return materializedView;
        }
        if (message.startsWith(USER_ERROR_REPORT)) {
            return new HandlerErrorCode(100315, "P0000");
        }
        if (message.startsWith(PYTHON_REPORT) || message.startsWith(PYTHON_INTERPRETER_ERROR)
                || message.startsWith(PYTHON_RETURN_REFUSAL)) {
            return new HandlerErrorCode(100357, "P0000");
        }
        if (message.startsWith(RETURNED_TABLE_MISMATCH)) {
            return new HandlerErrorCode(92230, "42804");
        }
        if (message.startsWith("Compilation of SQL UDF failed")) {
            return new HandlerErrorCode(90203, "42601");
        }
        if (message.equals(HybridKeys.primaryKeyExists())) {
            return new HandlerErrorCode(200001, "22000");
        }
        if (message.startsWith("Foreign key constraint \"SYS_INDEX_") && message.endsWith("\" was violated.")) {
            return new HandlerErrorCode(200009, "22000");
        }
        if (message.startsWith("Cannot apply projection policy on '")
                && message.endsWith("' because it is used as an argument by one or more policies.")) {
            return new HandlerErrorCode(506002, "23001");
        }
        if (message.startsWith("Revoke partially executed: ")) {
            return new HandlerErrorCode(3935, "23001");
        }
        if (message.contains(SqlAccessControlError.PREFIX) && message.contains(INSUFFICIENT_PRIVILEGES)) {
            return new HandlerErrorCode(3001, "42501");
        }
        if (message.contains("Object type or Class '") && message.contains(NOT_AUTHORIZED)) {
            // A class that is missing, of a listing, a DROP, an ALTER or a COMMENT ON alike (live-verified).
            return new HandlerErrorCode(398014, "02000");
        }
        if (message.contains("syntax error line ")) {
            return new HandlerErrorCode(1003, "42000");
        }
        if (message.contains("invalid identifier '")) {
            return new HandlerErrorCode(904, "42000");
        }
        if (message.contains("Invalid identifier ")) {
            return new HandlerErrorCode(2004, "42601");
        }
        if (message.contains("Division by zero")) {
            return new HandlerErrorCode(100051, "22012");
        }
        final HandlerErrorCode stateRefusal = stateRefusal(message);
        if (stateRefusal != null) {
            return stateRefusal;
        }
        final HandlerErrorCode signature = signatureRefusal(message);
        if (signature != null) {
            return signature;
        }
        if (message.contains("This session does not have a current schema.")) {
            // Every statement refused for want of a current schema (live-verified).
            return new HandlerErrorCode(90106, "22000");
        }
        if (message.contains("NULL result in a non-nullable column")) {
            return new HandlerErrorCode(100072, "00000", "22000");
        }
        final HandlerErrorCode value = unreadValue(message);
        if (value != null) {
            return value;
        }
        if (message.contains("Invalid intermediate datatype: ")) {
            return new HandlerErrorCode(1814, "22000");
        }
        if (message.contains("Unknown table function ")) {
            return new HandlerErrorCode(2142, "42601");
        }
        if (message.contains("Unknown function ") || message.contains("Unknown functions ")
                || message.contains("Unknown user-defined function ")) {
            return new HandlerErrorCode(2139, "02000");
        }
        if (message.contains("Cannot overload ") && message.contains(" as it would cause ambiguous ")) {
            return new HandlerErrorCode(949, "42723");
        }
        if (message.contains("column '") && message.contains("already exists")) {
            return new HandlerErrorCode(1430, "42601");
        }
        if (message.contains("already exists")) {
            return new HandlerErrorCode(2002, "42710");
        }
        if (message.contains("Object does not exist, or operation cannot be performed")) {
            return new HandlerErrorCode(2043, "02000");
        }
        if (message.contains("column '") && message.contains("' does not exist")) {
            return new HandlerErrorCode(1431, "42601");
        }
        final HandlerErrorCode missing = missingObject(message);
        if (missing != null) {
            return missing;
        }
        if (message.contains("does not exist")) {
            return new HandlerErrorCode(2003, "42S02");
        }
        if (message.contains(" did not exist or was purged.")) {
            return new HandlerErrorCode(90025, "02000");
        }
        if (message.contains("Mismatch between number of columns produced by '")) {
            return new HandlerErrorCode(1087, "42601");
        }
        if (message.contains("invalid number of arguments for [")) {
            return new HandlerErrorCode(909, "22023");
        }
        if (message.contains("not enough arguments for function")) {
            return new HandlerErrorCode(938, "22023");
        }
        if (message.contains("too many arguments for function")) {
            return new HandlerErrorCode(939, "22023");
        }
        if (message.contains("Single-row subquery returns more than one row")) {
            return new HandlerErrorCode(90150, "22000");
        }
        if (message.contains("is neither an aggregate nor in the group by clause")) {
            return new HandlerErrorCode(1104, "42601");
        }
        if (message.contains("A SELECT INTO statement expects exactly 1 returned row")) {
            return new HandlerErrorCode(92219, "22000");
        }
        if (message.contains("ambiguous column name '")) {
            return new HandlerErrorCode(2028, "42601");
        }
        if (message.contains("Invalid bind value (")) {
            return new HandlerErrorCode(2086, "22000");
        }
        if (message.contains("Your transaction was aborted.")) {
            return new HandlerErrorCode(624, "57014");
        }
        if (message.contains("COMMIT failed. Your transaction '")) {
            return new HandlerErrorCode(670, "57014");
        }
        if (message.contains("' was aborted because a deadlock with this lock was detected.")) {
            return new HandlerErrorCode(628, "40P01");
        }
        if (message.contains("' was aborted because waiting for this lock is ")) {
            return new HandlerErrorCode(message.endsWith("currently not allowed.") ? 626 : 627, "57014");
        }
        if (message.contains(TransactionManager.OTHER_SCOPE)) {
            return new HandlerErrorCode(90239, "25000");
        }
        if (message.contains(CallDepth.REFUSAL)) {
            return new HandlerErrorCode(90235, "42601");
        }
        if (message.contains("Stored procedure execution error: CURSOR '") && message.contains("' is already open.")) {
            return new HandlerErrorCode(92215, "P0000");
        }
        if (message.contains("CURSOR ") && message.contains(" is not open")) {
            return new HandlerErrorCode(92201, "P0000");
        }
        if (message.contains(UNINITIALIZED_RESULTSET)) {
            return new HandlerErrorCode(92212, "P0000");
        }
        if (message.contains(LoopControlPlacement.OUTSIDE_LOOP)) {
            return new HandlerErrorCode(92208, "P0000");
        }
        if (message.contains(LoopControlPlacement.LABEL_OPENING)
                && message.endsWith(LoopControlPlacement.LABEL_CLOSING)) {
            return new HandlerErrorCode(92209, "P0000");
        }
        if (message.contains("Invalid expression [") && message.contains("] in VALUES clause")) {
            return new HandlerErrorCode(2014, "22000");
        }
        if (message.contains("Insert value list does not match column list")) {
            return new HandlerErrorCode(2020, "21S01");
        }
        if (message.contains(" has no result because it failed")) {
            return new HandlerErrorCode(711, "02000");
        }
        if (message.contains("Error parsing JSON: ")) {
            return new HandlerErrorCode(100069, "22P02");
        }
        final HandlerErrorCode path = extractionPathRefusal(message);
        if (path != null) {
            return path;
        }
        if (message.contains("Error parsing URL: ")) {
            return new HandlerErrorCode(100139, "22000");
        }
        if (message.startsWith("Schema Inference failed: ")) {
            return new HandlerErrorCode(110001, "22000");
        }
        if (message.contains(TransactionManager.SCOPE_LEFT_OPEN)) {
            return new HandlerErrorCode(90232, "25000");
        }
        if (message.startsWith("Unknown compression method")) {
            return new HandlerErrorCode(100194, "42P19");
        }
        if (message.startsWith("Can't compress data")) {
            return new HandlerErrorCode(100195, "22000");
        }
        if (message.startsWith("Invalid data encountered during decompression for file: ")) {
            return new HandlerErrorCode(100079, "22000");
        }
        if (message.startsWith("Copy command does not recognize compression type ")) {
            return new HandlerErrorCode(200006, "0A000");
        }
        if (message.startsWith("Copy command does not support compression type ")) {
            return new HandlerErrorCode(200004, "0A000");
        }
        return refusedShape(message);
    }

    /**
     * The pair of a path into a semi-structured value refused, or null for none (live-verified): a literal path is
     * 22023 with a code per fault - an invalid field 1840, an invalid number 1841, an empty unquoted name 1842, a
     * dangling quote 1843, a dangling square bracket 1849, an invalid token 1851, a position past the array's limits
     * 1852 and a dangling dot 1853 - and a path read with the row that names nothing readable 100073 / 22000.
     */
    private static HandlerErrorCode extractionPathRefusal(final String message) {
        if (message.contains("Bad compound object's field path name '")) {
            return new HandlerErrorCode(100073, "22000");
        }
        if (!message.contains("Invalid extraction path '")) {
            return null;
        }
        if (message.contains(" is off limits; must be between ")) {
            return new HandlerErrorCode(1852, "22023");
        }
        final String[] faults = {"invalid field at position ", "invalid number at position ",
            "empty (unquoted) field name at position ", "dangling quote at position ",
            "dangling square bracket at position ", "invalid token at position ", "dangling dot at position "};
        final int[] codes = {1840, 1841, 1842, 1843, 1849, 1851, 1853};
        for (int i = 0; i < faults.length; i++) {
            if (message.contains("': " + faults[i])) {
                return new HandlerErrorCode(codes[i], "22023");
            }
        }
        return null;
    }

    /** The pair of a value no conversion reads, or null for none. */
    private static HandlerErrorCode unreadValue(final String message) {
        if (message.contains("Reason: Only [left | right] is allowed.")
                || message.contains("Reason: shift parameter must be greater or equal to zero")) {
            // A bitwise function's padding side or shift count it does not take (live-verified).
            return new HandlerErrorCode(100050, "22023");
        }
        if (message.contains("The lengths of two variable-sized fields do not match")) {
            return new HandlerErrorCode(100544, "22026");
        }
        if (message.contains("Numeric value '") && message.contains("is not recognized")) {
            return new HandlerErrorCode(100038, "22018");
        }
        if (message.contains("Numeric value '") && message.contains("' is out of range")) {
            return new HandlerErrorCode(100039, "22003");
        }
        if (message.contains("Date '") && message.contains("is not recognized")) {
            return new HandlerErrorCode(100040, "22007");
        }
        if (message.contains("Timestamp '") && message.contains("' is not recognized")) {
            return new HandlerErrorCode(100035, "22007");
        }
        if (message.contains("Time '") && message.contains("' is not recognized")) {
            return new HandlerErrorCode(100108, "22007");
        }
        if (message.contains("Boolean value '") && message.contains("' is not recognized")) {
            return new HandlerErrorCode(100037, "22018");
        }
        if (message.contains("Can't parse '") && message.contains("' as timestamp with format '")) {
            return new HandlerErrorCode(100096, "22007");
        }
        if (message.contains("Can't parse '") && message.contains("' as date with format '")) {
            return new HandlerErrorCode(100097, "22007");
        }
        if (message.contains("Number out of representable range")) {
            return new HandlerErrorCode(100046, "22003");
        }
        if (message.contains("String '") && message.contains("' is too long and would be truncated")) {
            return new HandlerErrorCode(100078, "22000");
        }
        if (message.contains("Failed to cast variant value ")) {
            return new HandlerErrorCode(100071, "22000");
        }
        if (message.contains("The following string is not a legal hex-encoded value")) {
            return new HandlerErrorCode(100115, "22000");
        }
        if (message.contains("The following string is not a legal base64-encoded value")) {
            return new HandlerErrorCode(100114, "22000");
        }
        if (message.contains("Error parsing XML: ")) {
            return new HandlerErrorCode(100100, "22P02");
        }
        if (message.contains("Invalid regular expression: ")) {
            return new HandlerErrorCode(100048, "2201B");
        }
        if (message.contains("Duplicate row detected during DML action")) {
            return new HandlerErrorCode(100090, "42P18");
        }
        if (message.startsWith("Duplicate field key '")) {
            // A key an OBJECT_CONSTRUCT, a MAP_CONSTRUCT, a MAP_INSERT or an OBJECT_AGG meets twice.
            return new HandlerErrorCode(100103, "22000");
        }
        return null;
    }

    /**
     * The pair of a missing object's sentence by the kind it names - {@code Schema 'S' does not exist or not
     * authorized.} - or null for no such sentence.
     */
    private static HandlerErrorCode missingObject(final String message) {
        final int end = message.indexOf(NOT_AUTHORIZED);
        final int open = end < 0 ? -1 : message.lastIndexOf(" '", end);
        if (open < 0) {
            return null;
        }
        int from = message.lastIndexOf('\n', open) + 1;
        final int colon = message.lastIndexOf(": ", open);
        if (colon + 2 > from) {
            from = colon + 2;
        }
        final String kind = message.substring(Math.min(from, open), open).trim();
        if ("Object".equals(kind) && message.substring(open + 2, end).endsWith(")")) {
            // A routine named by its signature, as GET_DDL names one.
            return new HandlerErrorCode(2001, "02000");
        }
        return "Object".equals(kind) || "Table".equals(kind) || "View".equals(kind)
            ? new HandlerErrorCode(2003, "42S02") : new HandlerErrorCode(2003, "02000");
    }

    /** The pair of a statement refused for its shape, or null for a sentence of no known family. */
    private static HandlerErrorCode refusedShape(final String message) {
        if (message.contains("is not a valid group by expression")) {
            return new HandlerErrorCode(979, "42601");
        }
        if (message.contains("is not a valid order by expression")) {
            return new HandlerErrorCode(2024, "42601");
        }
        if (message.contains("duplicate column name '")) {
            return new HandlerErrorCode(2025, "42S21");
        }
        if (message.contains("Unsupported data type '")) {
            return new HandlerErrorCode(2040, "42601");
        }
        if (message.contains(" cannot be used with arguments of types ")) {
            return new HandlerErrorCode(1065, "22023");
        }
        if (message.contains("invalid parameter '")) {
            return new HandlerErrorCode(1006, "22023");
        }
        if (message.contains("invalid value [") && message.contains("] for parameter '")) {
            return new HandlerErrorCode(1008, "22023");
        }
        if (message.contains("invalid type [") && message.contains("] for parameter '")) {
            return new HandlerErrorCode(1007, "22023");
        }
        if (message.contains("Invalid argument types for function '")) {
            return new HandlerErrorCode(1044, "42P13");
        }
        if (message.contains("File format type cannot be changed.")) {
            return new HandlerErrorCode(2134, "42601");
        }
        if (message.contains(" is not valid for file format type ")) {
            return new HandlerErrorCode(2135, "42601");
        }
        if (message.contains("Cumulative window frame unsupported for function ")) {
            return new HandlerErrorCode(2303, "0A000");
        }
        if (message.contains(" does not support WITHIN GROUP clause.")) {
            return new HandlerErrorCode(2070, "42601");
        }
        if (message.contains("invalid number of result columns for set operator input branches")) {
            return new HandlerErrorCode(1789, "42601");
        }
        if (message.contains("parameter value out of range: ")) {
            return new HandlerErrorCode(1817, "42601");
        }
        if (message.contains("Time travel data is not available for ")) {
            return new HandlerErrorCode(707, "02000");
        }
        if (message.contains("Unsupported subquery type cannot be evaluated inside VIEW object: ")
                || message.contains("Unsupported subquery type cannot be evaluated")
                && !message.contains("Unsupported subquery type cannot be evaluated inside ")) {
            return new HandlerErrorCode(2031, "42601");
        }
        if (message.contains("is not a valid date/time component for function ")) {
            return new HandlerErrorCode(2151, "22023");
        }
        if (message.contains("Date/time component [")
                && message.contains("needs to be an identifier or a string literal.")) {
            return new HandlerErrorCode(2150, "42601");
        }
        if (message.contains("Invalid data type [") && message.contains("] for predicate [")) {
            return new HandlerErrorCode(2034, "22000");
        }
        if (message.contains("requires ORDER BY in window specification")) {
            return new HandlerErrorCode(2061, "42601");
        }
        if (message.contains("Missing column specification")) {
            return new HandlerErrorCode(2022, "42601");
        }
        if (message.contains("cannot drop all the columns of a table")) {
            return new HandlerErrorCode(1432, "42601");
        }
        if (message.contains("cannot change column ") && message.contains(" from type ")) {
            return new HandlerErrorCode(2108, "22000");
        }
        if (message.contains("INSERT statement's target must be a table")) {
            return new HandlerErrorCode(1759, "42601");
        }
        if (message.contains("Aggregate functions cannot be nested: ")) {
            return new HandlerErrorCode(2082, "42601");
        }
        if (message.contains(" contains null values. Not null constraint cannot be added.")) {
            return new HandlerErrorCode(1471, "42601");
        }
        if (message.contains("Column count mismatch in MERGE statement with ALL BY NAME subclause")) {
            return new HandlerErrorCode(2032, "21000");
        }
        if (message.contains("Missing column in MERGE statement with ALL BY NAME subclause")) {
            return new HandlerErrorCode(2033, "42601");
        }
        if (message.endsWith("Unreachable merge case.")) {
            return new HandlerErrorCode(2045, "42601");
        }
        if (message.startsWith("Statement ") && message.endsWith(" not found")) {
            return new HandlerErrorCode(709, "02000");
        }
        if (message.startsWith("Actual statement count ")
                && message.contains(" did not match the desired statement count ")) {
            return new HandlerErrorCode(StatementCountMismatch.CODE, StatementCountMismatch.STATE);
        }
        // A bound value the compile cannot convert, and a LIMIT's or an OFFSET's bound row count it cannot read
        // (live-verified through the account's driver).
        if (message.contains("Can not convert parameter ") && message.contains(" into expected type [")) {
            return new HandlerErrorCode(1038, "22023");
        }
        if (message.contains("Invalid row count '") && message.contains("' in limit clause")) {
            return new HandlerErrorCode(2010, "2201W");
        }
        if (message.contains("Invalid row count '") && message.contains("' in result offset clause")) {
            return new HandlerErrorCode(2011, "2201X");
        }
        return null;
    }

    int code() {
        return code;
    }

    String state() {
        return state;
    }

    /** The state a client's driver reads, where it differs from the handler's. */
    String clientState() {
        return clientState;
    }
}
