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

package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * ARRAY_APPEND and its neighbours ARRAY_PREPEND, ARRAY_INSERT, ARRAY_CAT and ARRAY_CONSTRUCT keep an added element's
 * type: a VARIANT number, boolean or JSON null stays one, a DATE or BINARY keeps its type, a SQL NULL is undefined, and
 * a VARCHAR stays a string however much its text looks like JSON. Live-verified cells.
 */
public class ArrayAppendElementTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void appendedElementKeepsItsType() {
        assertCells(new String[][] {
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_APPEND(ARRAY_CONSTRUCT(1), PARSE_JSON('1.5')) a)",
                "[1,1.5] DECIMAL"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_APPEND(ARRAY_CONSTRUCT(1), PARSE_JSON('7')) a)",
                "[1,7] INTEGER"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_APPEND(ARRAY_CONSTRUCT(1), PARSE_JSON('true')) a)",
                "[1,true] BOOLEAN"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_APPEND(ARRAY_CONSTRUCT(1), PARSE_JSON('null')) a)",
                "[1,null] NULL_VALUE"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_APPEND(ARRAY_CONSTRUCT(1), PARSE_JSON('\"s\"')) a)",
                "[1,\"s\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_APPEND(ARRAY_CONSTRUCT(1), PARSE_JSON('[9]')) a)",
                "[1,[9]] ARRAY"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_APPEND(ARRAY_CONSTRUCT(1), PARSE_JSON('{\"x\":1}')) a)",
                "[1,{\"x\":1}] OBJECT"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_APPEND(ARRAY_CONSTRUCT(1), TO_VARIANT(2.5)) a)",
                "[1,2.5] DECIMAL"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_APPEND(ARRAY_CONSTRUCT(1), TO_VARIANT('[3]')) a)",
                "[1,\"[3]\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_APPEND(ARRAY_CONSTRUCT(1), '[1]') a)",
                "[1,\"[1]\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_APPEND(ARRAY_CONSTRUCT(1), '{\"a\":1}') a)",
                "[1,\"{\\\"a\\\":1}\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_APPEND(ARRAY_CONSTRUCT(1), 'null') a)",
                "[1,\"null\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_APPEND(ARRAY_CONSTRUCT(1), '1') a)",
                "[1,\"1\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_APPEND(ARRAY_CONSTRUCT(1), 'true') a)",
                "[1,\"true\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_APPEND(ARRAY_CONSTRUCT(1), ' [2] ') a)",
                "[1,\" [2] \"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_APPEND(ARRAY_CONSTRUCT(1), TRUE) a)",
                "[1,true] BOOLEAN"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_APPEND(ARRAY_CONSTRUCT(1), 2.50) a)",
                "[1,2.5] DECIMAL"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_APPEND(ARRAY_CONSTRUCT(1), NULL) a)",
                "[1,undefined] -"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_APPEND(ARRAY_CONSTRUCT(1), v) a FROM (SELECT PARSE_JSON('1.5') v))",
                "[1,1.5] DECIMAL"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_APPEND(ARRAY_CONSTRUCT(1), v) a FROM (SELECT PARSE_JSON('true') v))",
                "[1,true] BOOLEAN"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_APPEND(ARRAY_CONSTRUCT(1), v) a FROM (SELECT PARSE_JSON('null') v))",
                "[1,null] NULL_VALUE"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_APPEND(ARRAY_CONSTRUCT(1), v) a FROM (SELECT '[9]' v))",
                "[1,\"[9]\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_PREPEND(ARRAY_CONSTRUCT(1), PARSE_JSON('1.5')) a)",
                "[1.5,1] INTEGER"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_PREPEND(ARRAY_CONSTRUCT(1), PARSE_JSON('7')) a)",
                "[7,1] INTEGER"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_PREPEND(ARRAY_CONSTRUCT(1), PARSE_JSON('true')) a)",
                "[true,1] INTEGER"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_PREPEND(ARRAY_CONSTRUCT(1), PARSE_JSON('null')) a)",
                "[null,1] INTEGER"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_PREPEND(ARRAY_CONSTRUCT(1), PARSE_JSON('\"s\"')) a)",
                "[\"s\",1] INTEGER"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_PREPEND(ARRAY_CONSTRUCT(1), PARSE_JSON('[9]')) a)",
                "[[9],1] INTEGER"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_PREPEND(ARRAY_CONSTRUCT(1), PARSE_JSON('{\"x\":1}')) a)",
                "[{\"x\":1},1] INTEGER"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_PREPEND(ARRAY_CONSTRUCT(1), TO_VARIANT(2.5)) a)",
                "[2.5,1] INTEGER"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_PREPEND(ARRAY_CONSTRUCT(1), TO_VARIANT('[3]')) a)",
                "[\"[3]\",1] INTEGER"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_PREPEND(ARRAY_CONSTRUCT(1), '[1]') a)",
                "[\"[1]\",1] INTEGER"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_PREPEND(ARRAY_CONSTRUCT(1), '{\"a\":1}') a)",
                "[\"{\\\"a\\\":1}\",1] INTEGER"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_PREPEND(ARRAY_CONSTRUCT(1), 'null') a)",
                "[\"null\",1] INTEGER"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_PREPEND(ARRAY_CONSTRUCT(1), '1') a)",
                "[\"1\",1] INTEGER"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_PREPEND(ARRAY_CONSTRUCT(1), 'true') a)",
                "[\"true\",1] INTEGER"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_PREPEND(ARRAY_CONSTRUCT(1), ' [2] ') a)",
                "[\" [2] \",1] INTEGER"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_PREPEND(ARRAY_CONSTRUCT(1), TRUE) a)",
                "[true,1] INTEGER"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_PREPEND(ARRAY_CONSTRUCT(1), 2.50) a)",
                "[2.5,1] INTEGER"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_PREPEND(ARRAY_CONSTRUCT(1), NULL) a)",
                "[undefined,1] INTEGER"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_PREPEND(ARRAY_CONSTRUCT(1), v) a FROM (SELECT PARSE_JSON('1.5') v))",
                "[1.5,1] INTEGER"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_PREPEND(ARRAY_CONSTRUCT(1), v) a FROM (SELECT PARSE_JSON('true') v))",
                "[true,1] INTEGER"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_PREPEND(ARRAY_CONSTRUCT(1), v) a FROM (SELECT PARSE_JSON('null') v))",
                "[null,1] INTEGER"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_PREPEND(ARRAY_CONSTRUCT(1), v) a FROM (SELECT '[9]' v))",
                "[\"[9]\",1] INTEGER"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_INSERT(ARRAY_CONSTRUCT(1), 1, PARSE_JSON('1.5')) a)",
                "[1,1.5] DECIMAL"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_INSERT(ARRAY_CONSTRUCT(1), 1, PARSE_JSON('7')) a)",
                "[1,7] INTEGER"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_INSERT(ARRAY_CONSTRUCT(1), 1, PARSE_JSON('true')) a)",
                "[1,true] BOOLEAN"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_INSERT(ARRAY_CONSTRUCT(1), 1, PARSE_JSON('null')) a)",
                "[1,null] NULL_VALUE"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_INSERT(ARRAY_CONSTRUCT(1), 1, PARSE_JSON('\"s\"')) a)",
                "[1,\"s\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_INSERT(ARRAY_CONSTRUCT(1), 1, PARSE_JSON('[9]')) a)",
                "[1,[9]] ARRAY"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_INSERT(ARRAY_CONSTRUCT(1), 1, PARSE_JSON('{\"x\":1}')) a)",
                "[1,{\"x\":1}] OBJECT"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_INSERT(ARRAY_CONSTRUCT(1), 1, TO_VARIANT(2.5)) a)",
                "[1,2.5] DECIMAL"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_INSERT(ARRAY_CONSTRUCT(1), 1, TO_VARIANT('[3]')) a)",
                "[1,\"[3]\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_INSERT(ARRAY_CONSTRUCT(1), 1, '[1]') a)",
                "[1,\"[1]\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_INSERT(ARRAY_CONSTRUCT(1), 1, '{\"a\":1}') a)",
                "[1,\"{\\\"a\\\":1}\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_INSERT(ARRAY_CONSTRUCT(1), 1, 'null') a)",
                "[1,\"null\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_INSERT(ARRAY_CONSTRUCT(1), 1, '1') a)",
                "[1,\"1\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_INSERT(ARRAY_CONSTRUCT(1), 1, 'true') a)",
                "[1,\"true\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_INSERT(ARRAY_CONSTRUCT(1), 1, ' [2] ') a)",
                "[1,\" [2] \"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_INSERT(ARRAY_CONSTRUCT(1), 1, TRUE) a)",
                "[1,true] BOOLEAN"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_INSERT(ARRAY_CONSTRUCT(1), 1, 2.50) a)",
                "[1,2.5] DECIMAL"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_INSERT(ARRAY_CONSTRUCT(1), 1, NULL) a)",
                "[1,undefined] -"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_INSERT(ARRAY_CONSTRUCT(1), 1, v) a FROM (SELECT PARSE_JSON('1.5') v))",
                "[1,1.5] DECIMAL"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_INSERT(ARRAY_CONSTRUCT(1), 1, v) a FROM (SELECT PARSE_JSON('true') v))",
                "[1,true] BOOLEAN"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_INSERT(ARRAY_CONSTRUCT(1), 1, v) a FROM (SELECT PARSE_JSON('null') v))",
                "[1,null] NULL_VALUE"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_INSERT(ARRAY_CONSTRUCT(1), 1, v) a FROM (SELECT '[9]' v))",
                "[1,\"[9]\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_CAT(ARRAY_CONSTRUCT(1), ARRAY_CONSTRUCT(PARSE_JSON('1.5'))) a)",
                "[1,1.5] DECIMAL"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_CAT(ARRAY_CONSTRUCT(1), ARRAY_CONSTRUCT(PARSE_JSON('7'))) a)",
                "[1,7] INTEGER"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_CAT(ARRAY_CONSTRUCT(1), ARRAY_CONSTRUCT(PARSE_JSON('true'))) a)",
                "[1,true] BOOLEAN"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_CAT(ARRAY_CONSTRUCT(1), ARRAY_CONSTRUCT(PARSE_JSON('null'))) a)",
                "[1,null] NULL_VALUE"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_CAT(ARRAY_CONSTRUCT(1), ARRAY_CONSTRUCT(PARSE_JSON('\"s\"'))) a)",
                "[1,\"s\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_CAT(ARRAY_CONSTRUCT(1), ARRAY_CONSTRUCT(PARSE_JSON('[9]'))) a)",
                "[1,[9]] ARRAY"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_CAT(ARRAY_CONSTRUCT(1), ARRAY_CONSTRUCT(PARSE_JSON('{\"x\":1}'))) a)",
                "[1,{\"x\":1}] OBJECT"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_CAT(ARRAY_CONSTRUCT(1), ARRAY_CONSTRUCT(TO_VARIANT(2.5))) a)",
                "[1,2.5] DECIMAL"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_CAT(ARRAY_CONSTRUCT(1), ARRAY_CONSTRUCT(TO_VARIANT('[3]'))) a)",
                "[1,\"[3]\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_CAT(ARRAY_CONSTRUCT(1), ARRAY_CONSTRUCT('[1]')) a)",
                "[1,\"[1]\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_CAT(ARRAY_CONSTRUCT(1), ARRAY_CONSTRUCT('{\"a\":1}')) a)",
                "[1,\"{\\\"a\\\":1}\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_CAT(ARRAY_CONSTRUCT(1), ARRAY_CONSTRUCT('null')) a)",
                "[1,\"null\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_CAT(ARRAY_CONSTRUCT(1), ARRAY_CONSTRUCT('1')) a)",
                "[1,\"1\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_CAT(ARRAY_CONSTRUCT(1), ARRAY_CONSTRUCT('true')) a)",
                "[1,\"true\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_CAT(ARRAY_CONSTRUCT(1), ARRAY_CONSTRUCT(' [2] ')) a)",
                "[1,\" [2] \"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_CAT(ARRAY_CONSTRUCT(1), ARRAY_CONSTRUCT(TRUE)) a)",
                "[1,true] BOOLEAN"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_CAT(ARRAY_CONSTRUCT(1), ARRAY_CONSTRUCT(2.50)) a)",
                "[1,2.5] DECIMAL"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_CAT(ARRAY_CONSTRUCT(1), ARRAY_CONSTRUCT(NULL)) a)",
                "[1,undefined] -"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_CAT(ARRAY_CONSTRUCT(1), ARRAY_CONSTRUCT(v)) a FROM (SELECT PARSE_JSON('1.5') v))",
                "[1,1.5] DECIMAL"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_CAT(ARRAY_CONSTRUCT(1), ARRAY_CONSTRUCT(v)) a FROM (SELECT PARSE_JSON('true') v))",
                "[1,true] BOOLEAN"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_CAT(ARRAY_CONSTRUCT(1), ARRAY_CONSTRUCT(v)) a FROM (SELECT PARSE_JSON('null') v))",
                "[1,null] NULL_VALUE"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_CAT(ARRAY_CONSTRUCT(1), ARRAY_CONSTRUCT(v)) a FROM (SELECT '[9]' v))",
                "[1,\"[9]\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT ARRAY_CONSTRUCT(1, "
                + "PARSE_JSON('1.5')) a)",
                "[1,1.5] DECIMAL"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT ARRAY_CONSTRUCT(1, "
                + "PARSE_JSON('7')) a)",
                "[1,7] INTEGER"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT ARRAY_CONSTRUCT(1, "
                + "PARSE_JSON('true')) a)",
                "[1,true] BOOLEAN"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT ARRAY_CONSTRUCT(1, "
                + "PARSE_JSON('null')) a)",
                "[1,null] NULL_VALUE"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT ARRAY_CONSTRUCT(1, "
                + "PARSE_JSON('\"s\"')) a)",
                "[1,\"s\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT ARRAY_CONSTRUCT(1, "
                + "PARSE_JSON('[9]')) a)",
                "[1,[9]] ARRAY"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT ARRAY_CONSTRUCT(1, "
                + "PARSE_JSON('{\"x\":1}')) a)",
                "[1,{\"x\":1}] OBJECT"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT ARRAY_CONSTRUCT(1, "
                + "TO_VARIANT(2.5)) a)",
                "[1,2.5] DECIMAL"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT ARRAY_CONSTRUCT(1, "
                + "TO_VARIANT('[3]')) a)",
                "[1,\"[3]\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT ARRAY_CONSTRUCT(1, "
                + "'[1]') a)",
                "[1,\"[1]\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT ARRAY_CONSTRUCT(1, "
                + "'{\"a\":1}') a)",
                "[1,\"{\\\"a\\\":1}\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT ARRAY_CONSTRUCT(1, "
                + "'null') a)",
                "[1,\"null\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT ARRAY_CONSTRUCT(1, '1')"
                + " a)",
                "[1,\"1\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT ARRAY_CONSTRUCT(1, "
                + "'true') a)",
                "[1,\"true\"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT ARRAY_CONSTRUCT(1, ' "
                + "[2] ') a)",
                "[1,\" [2] \"] VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT ARRAY_CONSTRUCT(1, "
                + "TRUE) a)",
                "[1,true] BOOLEAN"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT ARRAY_CONSTRUCT(1, "
                + "2.50) a)",
                "[1,2.5] DECIMAL"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT ARRAY_CONSTRUCT(1, "
                + "NULL) a)",
                "[1,undefined] -"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT ARRAY_CONSTRUCT(1, v) a"
                + " FROM (SELECT PARSE_JSON('1.5') v))",
                "[1,1.5] DECIMAL"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT ARRAY_CONSTRUCT(1, v) a"
                + " FROM (SELECT PARSE_JSON('true') v))",
                "[1,true] BOOLEAN"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT ARRAY_CONSTRUCT(1, v) a"
                + " FROM (SELECT PARSE_JSON('null') v))",
                "[1,null] NULL_VALUE"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT ARRAY_CONSTRUCT(1, v) a"
                + " FROM (SELECT '[9]' v))",
                "[1,\"[9]\"] VARCHAR"},
            {"SELECT TO_JSON(r) || ' ' || TYPEOF(r[0]) FROM (SELECT REDUCE(ARRAY_CONSTRUCT(1,2,3), "
                + "ARRAY_CONSTRUCT(), (acc, x) -> ARRAY_APPEND(acc, x)) r)",
                "[1,2,3] INTEGER"},
            {"SELECT TO_JSON(r) || ' ' || TYPEOF(r[0]) FROM (SELECT REDUCE(ARRAY_CONSTRUCT(1,2,3), "
                + "ARRAY_CONSTRUCT(), (acc, x) -> ARRAY_PREPEND(acc, x)) r)",
                "[3,2,1] INTEGER"},
            {"SELECT TYPEOF(ARRAY_APPEND(ARRAY_CONSTRUCT(1), '{\"x\":1}')[1])",
                "VARCHAR"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_APPEND(ARRAY_CONSTRUCT(1), '2024-01-02'::DATE) a)",
                "[1,\"2024-01-02\"] DATE"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_APPEND(ARRAY_CONSTRUCT(1), TO_BINARY('AB','HEX')) a)",
                "[1,\"AB\"] BINARY"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_APPEND(ARRAY_CONSTRUCT(1), 1.5::FLOAT) a)",
                "[1,1.500000000000000e+00] DOUBLE"},
            {"SELECT TO_JSON(a) || ' ' || COALESCE(TYPEOF(a[1]), '-') FROM (SELECT "
                + "ARRAY_APPEND(PARSE_JSON('[1]'), PARSE_JSON('false')) a)",
                "[1,false] BOOLEAN"},
        });
    }
}
