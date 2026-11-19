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
package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A Scala procedure written against the Scala Snowpark API, {@code com.snowflake.snowpark.Session}, DataFrame and
 * Row: it runs statements through its session, reads rows by place and by typed accessor, and hands a DataFrame back
 * as a table procedure's answer. Every answer is live-verified.
 */
public class ScalaSnowparkApiTest extends MeasuredCellsTestSupport {

    /** A procedure's clauses and the head of its handler, which takes the Scala API's session. */
    private static final String HANDLER = " LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
        + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ "
        + "import com.snowflake.snowpark.Session; import com.snowflake.snowpark.DataFrame; "
        + "import com.snowflake.snowpark.Row; object H { def run(s: Session";

    @BeforeEach
    public void aTableOfTwoRows() {
        engine.execute("CREATE OR REPLACE TABLE st (id INT, v VARCHAR, d DOUBLE, b BOOLEAN, dt DATE, n NUMBER(10,2))");
        engine.execute("INSERT INTO st VALUES (1, 'a', 1.5, TRUE, '2024-01-02', 3.25), "
            + "(2, NULL, NULL, NULL, NULL, NULL)");
    }

    @Test
    public void aHandlerRunsAStatementAndReadsItsRow() {
        engine.execute("CREATE OR REPLACE PROCEDURE a1() RETURNS VARCHAR" + HANDLER
            + "): String = s.sql(\"SELECT 1\").collect()(0).getInt(0).toString } $$");
        assertAnswer("1", "CALL a1()");
        engine.execute("CREATE OR REPLACE PROCEDURE a9(x INT, y VARCHAR) RETURNS VARCHAR" + HANDLER
            + ", x: Int, y: String): String = s.sql(\"SELECT \" + x + \" + 1\").collect()(0).getInt(0) + y } $$");
        assertAnswer("5z", "CALL a9(4, 'z')");
    }

    @Test
    public void aTableProcedureAnswersAScalaDataFrame() {
        engine.execute("CREATE OR REPLACE PROCEDURE a2() RETURNS TABLE()" + HANDLER
            + "): DataFrame = s.sql(\"SELECT id, v FROM st ORDER BY id\") } $$");
        assertAnswer("1|a ;; 2|null", "CALL a2()");
    }

    @Test
    public void aRowIsReadByPlaceAndByType() {
        engine.execute("CREATE OR REPLACE PROCEDURE a3() RETURNS VARCHAR" + HANDLER
            + "): String = { val r = s.sql(\"SELECT id, v, d, b, dt, n FROM st ORDER BY id\").collect()(0); "
            + "r.getLong(0) + \"|\" + r.getString(1) + \"|\" + r.getDouble(2) + \"|\" + r.getBoolean(3) + \"|\" + "
            + "r.getDate(4) + \"|\" + r.getDecimal(5) + \"|\" + r.length + \"|\" + r.size + \"|\" + r(0) + \"|\" + "
            + "r.get(1) + \"|\" + r.isNullAt(1) } } $$");
        assertAnswer("1|a|1.5|true|2024-01-02|3.25|6|6|1|a|false", "CALL a3()");
        engine.execute("CREATE OR REPLACE PROCEDURE a13() RETURNS VARCHAR" + HANDLER
            + "): String = { val r = s.sql(\"SELECT 1::INT, 2::NUMBER(10,2), 'x', 1.5::FLOAT, TRUE\").collect()(0); "
            + "(0 until r.length).map(i => r.get(i).getClass.getName).mkString(\",\") } } $$");
        assertAnswer("java.lang.Long,java.math.BigDecimal,java.lang.String,java.lang.Double,java.lang.Boolean",
            "CALL a13()");
    }

    @Test
    public void aNullCellAndAPlacePastTheRow() {
        engine.execute("CREATE OR REPLACE PROCEDURE a4() RETURNS VARCHAR" + HANDLER
            + "): String = { val r = s.sql(\"SELECT id, v, d, b, dt, n FROM st ORDER BY id\").collect()(1); "
            + "r.isNullAt(1) + \"|\" + r.get(1) + \"|\" + r.getString(1) + \"|\" + r.toSeq.length + \"|\" + r "
            + "} } $$");
        assertAnswer("true|null|null|6|Row[2,null,null,null,null,null]", "CALL a4()");
        engine.execute("CREATE OR REPLACE PROCEDURE a8() RETURNS VARCHAR" + HANDLER
            + "): String = { val r = s.sql(\"SELECT id, v FROM st ORDER BY id\").collect()(1); "
            + "try { r.getInt(1).toString } catch { case e: Throwable => e.getClass.getName } } } $$");
        assertAnswer("java.lang.NullPointerException", "CALL a8()");
        engine.execute("CREATE OR REPLACE PROCEDURE a12() RETURNS VARCHAR" + HANDLER
            + "): String = { val r = s.sql(\"SELECT id, v FROM st ORDER BY id\").collect()(0); "
            + "try { r.getString(5) } catch { case e: Throwable => e.getClass.getName + \": \" + e.getMessage } } } $$");
        assertAnswer("java.lang.ArrayIndexOutOfBoundsException: Index 5 out of bounds for length 2", "CALL a12()");
    }

    @Test
    public void aTableIsCountedAndItsFirstRowRead() {
        engine.execute("CREATE OR REPLACE PROCEDURE a6() RETURNS VARCHAR" + HANDLER
            + "): String = s.table(\"st\").count().toString + \"|\" + s.sql(\"SELECT 1 a\").first().map(_.getInt(0)) "
            + "+ \"|\" + s.table(\"st\").collect().map(_.getInt(0)).sum + \"|\" + "
            + "s.sql(\"SELECT 1 a WHERE FALSE\").first().isEmpty + \"|\" + s.table(\"st\").limit(1).count() } $$");
        assertAnswer("2|Some(1)|3|true|1", "CALL a6()");
    }
}
