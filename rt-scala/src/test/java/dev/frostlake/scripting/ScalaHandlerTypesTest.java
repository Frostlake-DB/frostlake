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
import org.junit.jupiter.api.Test;

/**
 * A Scala handler's types against its routine's, judged at CREATE as a Java handler's are, and the Scala values
 * that cross: Scala's own classes a handler may not declare ({@code scala.math.BigDecimal}, {@code Seq},
 * {@code List}, {@code Vector}, a mutable {@code Map}) refused by name, a pair of types the account does not map
 * refused in the Java sentences, an immutable {@code Map[String, String]} taken for an OBJECT both ways and a
 * primitive {@code Array} for an ARRAY. Every answer is live-verified.
 */
public class ScalaHandlerTypesTest extends MeasuredCellsTestSupport {

    @Test
    public void aScalaHandlersTypesAreJudgedAndConverted() {
        engine.execute("CREATE OR REPLACE FUNCTION sc_bd10() RETURNS NUMBER(10,2) LANGUAGE SCALA RUNTIME_VERSION = "
            + "'2.12' HANDLER = 'H.f' AS $$ object H { def f(): java.math.BigDecimal = new "
            + "java.math.BigDecimal(\"1.005\") } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sc_op8(x INT) RETURNS INT LANGUAGE SCALA RUNTIME_VERSION = '2.12'"
            + " HANDLER = 'H.f' AS $$ object H { def f(x: Int): java.lang.Integer = null } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sc_op9(x INT) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = "
            + "'2.12' HANDLER = 'H.f' AS $$ object H { def f(x: Int): String = null } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sc_mp1() RETURNS OBJECT LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
            + "HANDLER = 'H.f' AS $$ object H { def f(): Map[String, String] = Map(\"a\" -> \"1\", \"b\" ->"
            + " \"x\") } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sc_mp3() RETURNS OBJECT LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
            + "HANDLER = 'H.f' AS $$ object H { def f(): java.util.Map[String, String] = { val m = new "
            + "java.util.HashMap[String, String](); m.put(\"a\", \"1\"); m } } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sc_mp4(x OBJECT) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION ="
            + " '2.12' HANDLER = 'H.f' AS $$ object H { def f(x: Map[String, String]): String = "
            + "x.toSeq.sortBy(_._1).mkString(\",\") } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sc_mp5(x OBJECT) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION ="
            + " '2.12' HANDLER = 'H.f' AS $$ object H { def f(x: java.util.Map[String, String]): String = "
            + "new java.util.TreeMap[String, String](x).toString } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sc_mp6() RETURNS OBJECT LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
            + "HANDLER = 'H.f' AS $$ object H { def f(): Map[String, String] = Map(\"a\" -> null) } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sc_mp7() RETURNS OBJECT LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
            + "HANDLER = 'H.f' AS $$ object H { def f(): Map[String, Int] = Map(\"a\" -> 1) } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sc_mp9() RETURNS OBJECT LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
            + "HANDLER = 'H.f' AS $$ object H { def f(): Map[String, String] = Map() } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sc_ar1() RETURNS ARRAY LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
            + "HANDLER = 'H.f' AS $$ object H { def f(): Array[String] = Array(\"a\", \"1\") } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sc_ar5() RETURNS ARRAY LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
            + "HANDLER = 'H.f' AS $$ object H { def f(): Array[Int] = Array(1, 2) } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sc_ar7(x ARRAY) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = "
            + "'2.12' HANDLER = 'H.f' AS $$ object H { def f(x: Array[String]): String = x.mkString(\"|\") "
            + "} $$");
        engine.execute("CREATE OR REPLACE FUNCTION sc_ar11() RETURNS ARRAY LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
            + "HANDLER = 'H.f' AS $$ object H { def f(): Array[String] = Array(\"a\", null) } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sc_ar13() RETURNS ARRAY LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
            + "HANDLER = 'H.f' AS $$ object H { def f(): Array[String] = null } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sc_ar14(x ARRAY) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION ="
            + " '2.12' HANDLER = 'H.f' AS $$ object H { def f(x: Array[String]): String = x.length.toString"
            + " } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sd_j5(x INT) RETURNS BOOLEAN LANGUAGE SCALA RUNTIME_VERSION = "
            + "'2.12' HANDLER = 'H.f' AS $$ object H { def f(x: Int): Boolean = x > 1 } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sd_j7(x INT) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = "
            + "'2.12' HANDLER = 'H.f' AS $$ object H { def f(x: Long): String = x.toString } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sd_j9(x INT) RETURNS NUMBER(10,2) LANGUAGE SCALA RUNTIME_VERSION "
            + "= '2.12' HANDLER = 'H.f' AS $$ object H { def f(x: java.math.BigDecimal): "
            + "java.math.BigDecimal = x } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sd_a1(x ARRAY) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = "
            + "'2.12' HANDLER = 'H.f' AS $$ object H { def f(x: Array[Int]): String = x.mkString(\"|\") } "
            + "$$");
        engine.execute("CREATE OR REPLACE FUNCTION sd_a2() RETURNS ARRAY LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
            + "HANDLER = 'H.f' AS $$ object H { def f(): Array[Float] = Array(1.5f, 2f) } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sd_a3() RETURNS ARRAY LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
            + "HANDLER = 'H.f' AS $$ object H { def f(): Array[Double] = Array(1.5, 2.0) } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sd_a4() RETURNS ARRAY LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
            + "HANDLER = 'H.f' AS $$ object H { def f(): Array[Long] = Array(1L) } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sd_a6() RETURNS ARRAY LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
            + "HANDLER = 'H.f' AS $$ object H { def f(): Array[Int] = Array() } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sd_a7() RETURNS ARRAY LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
            + "HANDLER = 'H.f' AS $$ object H { def f(): Array[Int] = null } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sd_a8(x ARRAY) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = "
            + "'2.12' HANDLER = 'H.f' AS $$ object H { def f(x: Array[Float]): String = x.mkString(\"|\") }"
            + " $$");
        engine.execute("CREATE OR REPLACE FUNCTION sd_m4(x OBJECT) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = "
            + "'2.12' HANDLER = 'H.f' AS $$ object H { def f(x: Map[String, Int]): String = x.toString } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sd_m5(x OBJECT) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = "
            + "'2.12' HANDLER = 'H.f' AS $$ object H { def f(x: Map[String, String]): String = "
            + "x.getOrElse(\"a\", \"none\") + \"/\" + x.size } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sd_m6(x OBJECT) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = "
            + "'2.12' HANDLER = 'H.f' AS $$ object H { def f(x: Map[String, String]): String = "
            + "String.valueOf(x) } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sd_m8() RETURNS OBJECT LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
            + "HANDLER = 'H.f' AS $$ object H { def f(): Map[Int, String] = Map(1 -> \"x\") } $$");
        engine.execute("CREATE OR REPLACE PROCEDURE sp_p5() RETURNS OBJECT LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
            + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.f' AS $$ import "
            + "com.snowflake.snowpark.Session; object H { def f(s: Session): Map[String, String] = "
            + "Map(\"a\" -> \"1\") } $$");
        engine.execute("CREATE OR REPLACE PROCEDURE sp_p6() RETURNS ARRAY LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
            + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.f' AS $$ import "
            + "com.snowflake.snowpark.Session; object H { def f(s: Session): Array[Int] = Array(1, 2) } $$");
        engine.execute("CREATE OR REPLACE PROCEDURE sp_p8(x OBJECT) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION ="
            + " '2.12' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.f' AS $$ import "
            + "com.snowflake.snowpark.Session; object H { def f(s: Session, x: Map[String, String]): String"
            + " = x.toSeq.sortBy(_._1).mkString(\",\") } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sa1(x ARRAY) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = "
            + "'2.12' HANDLER = 'H.f' AS $$ object H { def f(x: Array[Long]): String = x.mkString(\"|\") } "
            + "$$");
        engine.execute("CREATE OR REPLACE FUNCTION sa2(x ARRAY) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = "
            + "'2.12' HANDLER = 'H.f' AS $$ object H { def f(x: Array[Double]): String = x.mkString(\"|\") "
            + "} $$");
        engine.execute("CREATE OR REPLACE FUNCTION sa4() RETURNS ARRAY LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
            + "HANDLER = 'H.f' AS $$ object H { def f(): Array[Short] = Array(1) } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sa5() RETURNS ARRAY LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
            + "HANDLER = 'H.f' AS $$ object H { def f(): Array[Boolean] = Array(true) } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sa7(x OBJECT) RETURNS OBJECT LANGUAGE SCALA RUNTIME_VERSION = "
            + "'2.12' HANDLER = 'H.f' AS $$ object H { def f(x: Map[String, String]): Map[String, String] ="
            + " x + (\"z\" -> \"9\") } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sa8() RETURNS ARRAY LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
            + "HANDLER = 'H.f' AS $$ object H { def f(): Array[Float] = Array(0.1f) } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sa9() RETURNS ARRAY LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
            + "HANDLER = 'H.f' AS $$ object H { def f(): Array[Double] = Array(0.1, 1e300, Double.NaN) } $$");
        engine.execute("CREATE OR REPLACE FUNCTION sa10(x ARRAY) RETURNS ARRAY LANGUAGE SCALA RUNTIME_VERSION = "
            + "'2.12' HANDLER = 'H.f' AS $$ object H { def f(x: Array[String]): Array[String] = x } $$");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION sc_bd1() RETURNS NUMBER(10,3) LANGUAGE SCALA RUNTIME_VERSION = "
                + "'2.12' HANDLER = 'H.f' AS $$ object H { def f(): BigDecimal = BigDecimal(\"12.345\") } $$",
                "REFUSED Unsupported return type: scala.math.BigDecimal in function SC_BD1 with handler H.f"},
            {"SELECT sc_bd1()",
                "REFUSED SQL compilation error: | Unknown function SC_BD1."},
            {"CREATE OR REPLACE FUNCTION sc_bd2() RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(): BigDecimal = BigDecimal(\"12.5\") } $$",
                "REFUSED Unsupported return type: scala.math.BigDecimal in function SC_BD2 with handler H.f"},
            {"SELECT sc_bd2()",
                "REFUSED SQL compilation error: | Unknown function SC_BD2."},
            {"CREATE OR REPLACE FUNCTION sc_bd3() RETURNS NUMBER(10,2) LANGUAGE SCALA RUNTIME_VERSION = "
                + "'2.12' HANDLER = 'H.f' AS $$ object H { def f(): BigDecimal = BigDecimal(\"1.005\") } $$",
                "REFUSED Unsupported return type: scala.math.BigDecimal in function SC_BD3 with handler H.f"},
            {"SELECT sc_bd3()",
                "REFUSED SQL compilation error: | Unknown function SC_BD3."},
            {"CREATE OR REPLACE FUNCTION sc_bd4() RETURNS FLOAT LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(): BigDecimal = BigDecimal(\"1.25\") } $$",
                "REFUSED Unsupported return type: scala.math.BigDecimal in function SC_BD4 with handler H.f"},
            {"SELECT sc_bd4()",
                "REFUSED SQL compilation error: | Unknown function SC_BD4."},
            {"CREATE OR REPLACE FUNCTION sc_bd5() RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(): BigDecimal = BigDecimal(\"1.250\") } $$",
                "REFUSED Unsupported return type: scala.math.BigDecimal in function SC_BD5 with handler H.f"},
            {"SELECT sc_bd5()",
                "REFUSED SQL compilation error: | Unknown function SC_BD5."},
            {"CREATE OR REPLACE FUNCTION sc_bd6(x NUMBER(10,2)) RETURNS NUMBER(38,2) LANGUAGE SCALA "
                + "RUNTIME_VERSION = '2.12' HANDLER = 'H.f' AS $$ object H { def f(x: BigDecimal): BigDecimal ="
                + " x * 2 } $$",
                "REFUSED Unsupported argument type: scala.math.BigDecimal in function SC_BD6 with handler H.f"},
            {"SELECT sc_bd6(1.25::NUMBER(10,2))",
                "REFUSED SQL compilation error: | Unknown function SC_BD6."},
            {"CREATE OR REPLACE FUNCTION sc_bd7(x NUMBER(10,2)) RETURNS NUMBER(38,2) LANGUAGE SCALA "
                + "RUNTIME_VERSION = '2.12' HANDLER = 'H.f' AS $$ object H { def f(x: BigDecimal): BigDecimal ="
                + " x } $$",
                "REFUSED Unsupported argument type: scala.math.BigDecimal in function SC_BD7 with handler H.f"},
            {"SELECT sc_bd7(NULL::NUMBER(10,2))",
                "REFUSED SQL compilation error: | Unknown function SC_BD7."},
            {"CREATE OR REPLACE FUNCTION sc_bd8() RETURNS NUMBER(38,0) LANGUAGE SCALA RUNTIME_VERSION = "
                + "'2.12' HANDLER = 'H.f' AS $$ object H { def f(): BigDecimal = null } $$",
                "REFUSED Unsupported return type: scala.math.BigDecimal in function SC_BD8 with handler H.f"},
            {"SELECT sc_bd8()",
                "REFUSED SQL compilation error: | Unknown function SC_BD8."},
            {"CREATE OR REPLACE FUNCTION sc_bd9() RETURNS NUMBER(3,0) LANGUAGE SCALA RUNTIME_VERSION = "
                + "'2.12' HANDLER = 'H.f' AS $$ object H { def f(): BigDecimal = BigDecimal(\"12345\") } $$",
                "REFUSED Unsupported return type: scala.math.BigDecimal in function SC_BD9 with handler H.f"},
            {"SELECT sc_bd9()",
                "REFUSED SQL compilation error: | Unknown function SC_BD9."},
            {"SELECT sc_bd10()",
                "1.00"},
            {"CREATE OR REPLACE FUNCTION sc_bd11(x NUMBER(10,2)) RETURNS VARCHAR LANGUAGE SCALA "
                + "RUNTIME_VERSION = '2.12' HANDLER = 'H.f' AS $$ object H { def f(x: BigDecimal): String = "
                + "x.toString } $$",
                "REFUSED Unsupported argument type: scala.math.BigDecimal in function SC_BD11 with handler "
                + "H.f"},
            {"SELECT sc_bd11(1.50::NUMBER(10,2))",
                "REFUSED SQL compilation error: | Unknown function SC_BD11."},
            {"SELECT sc_op8(4)",
                "null"},
            {"SELECT sc_op9(4)",
                "null"},
            {"SELECT TO_JSON(sc_mp1())",
                "{\"a\":\"1\",\"b\":\"x\"}"},
            {"SELECT TYPEOF(sc_mp1()::VARIANT)",
                "OBJECT"},
            {"CREATE OR REPLACE FUNCTION sc_mp2() RETURNS OBJECT LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(): scala.collection.mutable.Map[String, String] = "
                + "scala.collection.mutable.Map(\"a\" -> \"1\") } $$",
                "REFUSED Unsupported return type: scala.collection.mutable.Map in function SC_MP2 with "
                + "handler H.f"},
            {"SELECT sc_mp2()",
                "REFUSED SQL compilation error: | Unknown function SC_MP2."},
            {"SELECT TYPEOF(sc_mp2()::VARIANT)",
                "REFUSED SQL compilation error: | Unknown function SC_MP2."},
            {"SELECT TO_JSON(sc_mp3())",
                "{\"a\":\"1\"}"},
            {"SELECT TYPEOF(sc_mp3()::VARIANT)",
                "OBJECT"},
            {"SELECT sc_mp4(OBJECT_CONSTRUCT('a', 1, 'b', 'x'))",
                "(a,1),(b,x)"},
            {"SELECT TO_JSON(sc_mp6())",
                "{\"a\":null}"},
            {"SELECT TYPEOF(sc_mp6()::VARIANT)",
                "OBJECT"},
            {"SELECT TO_JSON(sc_mp7())",
                "{\"a\":\"1\"}"},
            {"SELECT TYPEOF(sc_mp7()::VARIANT)",
                "OBJECT"},
            {"CREATE OR REPLACE FUNCTION sc_mp8() RETURNS VARIANT LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(): Map[String, String] = Map(\"a\" -> \"1\") } $$",
                "REFUSED Snowflake type VARIANT[LOB](38,0){nullable} is not supported for Java return type "
                + "scala.collection.immutable.Map[String, String] in function SC_MP8 with handler H.f"},
            {"SELECT sc_mp8()",
                "REFUSED SQL compilation error: | Unknown function SC_MP8."},
            {"SELECT TYPEOF(sc_mp8()::VARIANT)",
                "REFUSED SQL compilation error: | Unknown function SC_MP8."},
            {"SELECT TO_JSON(sc_mp9())",
                "{}"},
            {"SELECT TYPEOF(sc_mp9()::VARIANT)",
                "OBJECT"},
            {"SELECT TO_JSON(sc_ar1())",
                "[\"a\",\"1\"]"},
            {"SELECT TYPEOF(sc_ar1()::VARIANT)",
                "ARRAY"},
            {"CREATE OR REPLACE FUNCTION sc_ar2() RETURNS ARRAY LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(): Seq[String] = Seq(\"a\", \"1\") } $$",
                "REFUSED Unsupported return type: scala.collection.Seq in function SC_AR2 with handler H.f"},
            {"SELECT sc_ar2()",
                "REFUSED SQL compilation error: | Unknown function SC_AR2."},
            {"SELECT TYPEOF(sc_ar2()::VARIANT)",
                "REFUSED SQL compilation error: | Unknown function SC_AR2."},
            {"CREATE OR REPLACE FUNCTION sc_ar3() RETURNS ARRAY LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(): List[String] = List(\"a\", \"1\") } $$",
                "REFUSED Unsupported return type: scala.collection.immutable.List in function SC_AR3 with "
                + "handler H.f"},
            {"SELECT sc_ar3()",
                "REFUSED SQL compilation error: | Unknown function SC_AR3."},
            {"SELECT TYPEOF(sc_ar3()::VARIANT)",
                "REFUSED SQL compilation error: | Unknown function SC_AR3."},
            {"CREATE OR REPLACE FUNCTION sc_ar4() RETURNS ARRAY LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(): java.util.List[String] = "
                + "java.util.Arrays.asList(\"a\", \"1\") } $$",
                "REFUSED Unsupported return type: java.util.List in function SC_AR4 with handler H.f"},
            {"SELECT sc_ar4()",
                "REFUSED SQL compilation error: | Unknown function SC_AR4."},
            {"SELECT TYPEOF(sc_ar4()::VARIANT)",
                "REFUSED SQL compilation error: | Unknown function SC_AR4."},
            {"SELECT TO_JSON(sc_ar5())",
                "[1,2]"},
            {"SELECT TYPEOF(sc_ar5()::VARIANT)",
                "ARRAY"},
            {"CREATE OR REPLACE FUNCTION sc_ar6() RETURNS ARRAY LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(): Vector[String] = Vector(\"a\") } $$",
                "REFUSED Unsupported return type: scala.collection.immutable.Vector in function SC_AR6 with "
                + "handler H.f"},
            {"SELECT sc_ar6()",
                "REFUSED SQL compilation error: | Unknown function SC_AR6."},
            {"SELECT TYPEOF(sc_ar6()::VARIANT)",
                "REFUSED SQL compilation error: | Unknown function SC_AR6."},
            {"SELECT sc_ar7(ARRAY_CONSTRUCT('a', 1, NULL))",
                "a|1|null"},
            {"CREATE OR REPLACE FUNCTION sc_ar8(x ARRAY) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = "
                + "'2.12' HANDLER = 'H.f' AS $$ object H { def f(x: Seq[String]): String = x.mkString(\"|\") } "
                + "$$",
                "REFUSED Unsupported argument type: scala.collection.Seq in function SC_AR8 with handler H.f"},
            {"SELECT sc_ar8(ARRAY_CONSTRUCT('a', 1))",
                "REFUSED SQL compilation error: | Unknown function SC_AR8."},
            {"CREATE OR REPLACE FUNCTION sc_ar9(x ARRAY) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = "
                + "'2.12' HANDLER = 'H.f' AS $$ object H { def f(x: List[String]): String = x.mkString(\"|\") }"
                + " $$",
                "REFUSED Unsupported argument type: scala.collection.immutable.List in function SC_AR9 with "
                + "handler H.f"},
            {"SELECT sc_ar9(ARRAY_CONSTRUCT('a', 1))",
                "REFUSED SQL compilation error: | Unknown function SC_AR9."},
            {"CREATE OR REPLACE FUNCTION sc_ar10(x ARRAY) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION ="
                + " '2.12' HANDLER = 'H.f' AS $$ object H { def f(x: java.util.List[String]): String = "
                + "x.toString } $$",
                "REFUSED Snowflake type ARRAY[LOB](38,0){nullable} is not supported as input to argument at "
                + "index 0 with Java type java.util.Map<String, String> in function SC_AR10 with handler H.f"},
            {"SELECT sc_ar10(ARRAY_CONSTRUCT('a', 1))",
                "REFUSED SQL compilation error: | Unknown function SC_AR10."},
            {"SELECT TO_JSON(sc_ar11())",
                "[\"a\",undefined]"},
            {"SELECT TYPEOF(sc_ar11()::VARIANT)",
                "ARRAY"},
            {"CREATE OR REPLACE FUNCTION sc_ar12() RETURNS VARIANT LANGUAGE SCALA RUNTIME_VERSION = '2.12'"
                + " HANDLER = 'H.f' AS $$ object H { def f(): Array[String] = Array(\"a\", \"1\") } $$",
                "REFUSED Snowflake type VARIANT[LOB](38,0){nullable} is not supported for Java return type "
                + "String[] in function SC_AR12 with handler H.f"},
            {"SELECT sc_ar12()",
                "REFUSED SQL compilation error: | Unknown function SC_AR12."},
            {"SELECT TYPEOF(sc_ar12()::VARIANT)",
                "REFUSED SQL compilation error: | Unknown function SC_AR12."},
            {"SELECT sc_ar13()",
                "null"},
            {"SELECT TYPEOF(sc_ar13()::VARIANT)",
                "null"},
            {"SELECT sc_ar14(ARRAY_CONSTRUCT())",
                "0"},
            {"CREATE OR REPLACE FUNCTION sd_j1(x INT) RETURNS NUMBER(10,2) LANGUAGE SCALA RUNTIME_VERSION "
                + "= '2.12' HANDLER = 'H.f' AS $$ object H { def f(x: Int): Int = x } $$",
                "REFUSED Cannot encode number with a non-zero scale as an int in function SD_J1 with handler "
                + "H.f"},
            {"SELECT sd_j1(3)",
                "REFUSED SQL compilation error: | Unknown function SD_J1."},
            {"CREATE OR REPLACE FUNCTION sd_j2(x INT) RETURNS NUMBER LANGUAGE SCALA RUNTIME_VERSION = "
                + "'2.12' HANDLER = 'H.f' AS $$ object H { def f(x: Int): Double = x } $$",
                "REFUSED Snowflake type FIXED[SB16](38,0){nullable} is not supported for Java return type "
                + "double in function SD_J2 with handler H.f"},
            {"SELECT sd_j2(3)",
                "REFUSED SQL compilation error: | Unknown function SD_J2."},
            {"CREATE OR REPLACE FUNCTION sd_j3(x VARCHAR) RETURNS INT LANGUAGE SCALA RUNTIME_VERSION = "
                + "'2.12' HANDLER = 'H.f' AS $$ object H { def f(x: Int): Int = x } $$",
                "REFUSED Snowflake type TEXT[LOB](134217728){nullable} is not supported as input to argument "
                + "at index 0 with Java type int in function SD_J3 with handler H.f"},
            {"SELECT sd_j3('3')",
                "REFUSED SQL compilation error: | Unknown function SD_J3."},
            {"CREATE OR REPLACE FUNCTION sd_j4(x NUMBER(10,2)) RETURNS INT LANGUAGE SCALA RUNTIME_VERSION "
                + "= '2.12' HANDLER = 'H.f' AS $$ object H { def f(x: Int): Int = x } $$",
                "REFUSED Cannot encode number with a non-zero scale as an int in function SD_J4 with handler "
                + "H.f"},
            {"SELECT sd_j4(3)",
                "REFUSED SQL compilation error: | Unknown function SD_J4."},
            {"CREATE OR REPLACE FUNCTION sd_j6(x INT) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = "
                + "'2.12' HANDLER = 'H.f' AS $$ object H { def f(x: Int): Any = x } $$",
                "REFUSED Unsupported return type: java.lang.Object in function SD_J6 with handler H.f"},
            {"SELECT sd_j6(3)",
                "REFUSED SQL compilation error: | Unknown function SD_J6."},
            {"SELECT sd_j7(3)",
                "3"},
            {"SELECT sd_j9(3)",
                "3.00"},
            {"CREATE OR REPLACE FUNCTION sd_j10(x INT) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = "
                + "'2.12' HANDLER = 'H.f' AS $$ object H { def f(x: Char): String = \"a\" } $$",
                "REFUSED Unsupported argument type: char in function SD_J10 with handler H.f"},
            {"SELECT sd_j10(3)",
                "REFUSED SQL compilation error: | Unknown function SD_J10."},
            {"SELECT sd_a1(ARRAY_CONSTRUCT(1, 2))",
                "1|2"},
            {"SELECT TO_JSON(sd_a2())",
                "[1.500000000000000e+00,2.000000000000000e+00]"},
            {"SELECT TYPEOF(sd_a2()::VARIANT)",
                "ARRAY"},
            {"SELECT TO_JSON(sd_a3())",
                "[1.500000000000000e+00,2.000000000000000e+00]"},
            {"SELECT TYPEOF(sd_a3()::VARIANT)",
                "ARRAY"},
            {"SELECT TO_JSON(sd_a4())",
                "[1]"},
            {"SELECT TYPEOF(sd_a4()::VARIANT)",
                "ARRAY"},
            {"CREATE OR REPLACE FUNCTION sd_a5() RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(): Array[Int] = Array(1, 2) } $$",
                "REFUSED Snowflake type TEXT[LOB](134217728){nullable} is not supported for Java return type "
                + "int[] in function SD_A5 with handler H.f"},
            {"SELECT sd_a5()",
                "REFUSED SQL compilation error: | Unknown function SD_A5."},
            {"SELECT TO_JSON(sd_a6())",
                "[]"},
            {"SELECT TYPEOF(sd_a6()::VARIANT)",
                "ARRAY"},
            {"SELECT sd_a7()",
                "null"},
            {"SELECT TYPEOF(sd_a7()::VARIANT)",
                "null"},
            {"SELECT sd_a8(ARRAY_CONSTRUCT(1.5, 2))",
                "1.5|2.0"},
            {"CREATE OR REPLACE FUNCTION sd_a9() RETURNS ARRAY LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(): Array[Any] = Array(1, \"a\") } $$",
                "REFUSED Unsupported return type: java.lang.Object[] in function SD_A9 with handler H.f"},
            {"SELECT sd_a9()",
                "REFUSED SQL compilation error: | Unknown function SD_A9."},
            {"SELECT TYPEOF(sd_a9()::VARIANT)",
                "REFUSED SQL compilation error: | Unknown function SD_A9."},
            {"CREATE OR REPLACE FUNCTION sd_m1(x VARCHAR) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION ="
                + " '2.12' HANDLER = 'H.f' AS $$ object H { def f(x: Map[String, String]): String = \"a\" } $$",
                "REFUSED Snowflake type TEXT[LOB](134217728){nullable} is not supported as input to argument "
                + "at index 0 with Java type scala.collection.immutable.Map[String, String] in function SD_M1 "
                + "with handler H.f"},
            {"SELECT sd_m1('a')",
                "REFUSED SQL compilation error: | Unknown function SD_M1."},
            {"CREATE OR REPLACE FUNCTION sd_m2() RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(): Map[String, String] = Map(\"a\" -> \"1\") } $$",
                "REFUSED Snowflake type TEXT[LOB](134217728){nullable} is not supported for Java return type "
                + "scala.collection.immutable.Map[String, String] in function SD_M2 with handler H.f"},
            {"SELECT sd_m2()",
                "REFUSED SQL compilation error: | Unknown function SD_M2."},
            {"CREATE OR REPLACE FUNCTION sd_m3(x OBJECT) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = "
                + "'2.12' HANDLER = 'H.f' AS $$ object H { def f(x: scala.collection.Map[String, String]): "
                + "String = \"a\" } $$",
                "REFUSED Unsupported argument type: scala.collection.Map in function SD_M3 with handler H.f"},
            {"SELECT sd_m3(OBJECT_CONSTRUCT())",
                "REFUSED SQL compilation error: | Unknown function SD_M3."},
            {"SELECT sd_m4(OBJECT_CONSTRUCT('a', 1))",
                "Map(a -> 1)"},
            {"SELECT sd_m5(OBJECT_CONSTRUCT('a', OBJECT_CONSTRUCT('b', 2), 'c', NULL))",
                "{\"b\":2}/1"},
            {"SELECT sd_m6(NULL)",
                "null"},
            {"SELECT TO_JSON(sd_m8())",
                "{\"1\":\"x\"}"},
            {"SELECT TYPEOF(sd_m8()::VARIANT)",
                "OBJECT"},
            {"CREATE OR REPLACE FUNCTION sd_u1(x INT) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = "
                + "'2.12' HANDLER = 'H.f' AS $$ object H { def f(x: Int): Unit = () } $$",
                "REFUSED Unsupported return type: void in function SD_U1 with handler H.f"},
            {"SELECT sd_u1(4)",
                "REFUSED SQL compilation error: | Unknown function SD_U1."},
            {"CREATE OR REPLACE FUNCTION sd_s1(x INT) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = "
                + "'2.12' HANDLER = 'H.f' AS $$ object H { def f(x: Int): Seq[String] = Seq(\"a\") } $$",
                "REFUSED Unsupported return type: scala.collection.Seq in function SD_S1 with handler H.f"},
            {"SELECT sd_s1(4)",
                "REFUSED SQL compilation error: | Unknown function SD_S1."},
            {"CREATE OR REPLACE PROCEDURE sp_p1() RETURNS NUMBER(10,2) LANGUAGE SCALA RUNTIME_VERSION = "
                + "'2.12' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.f' AS $$ import "
                + "com.snowflake.snowpark.Session; object H { def f(s: Session): BigDecimal = "
                + "BigDecimal(\"1.5\") } $$",
                "REFUSED Unsupported return type: scala.math.BigDecimal in function SP_P1 with handler H.f"},
            {"CALL sp_p1()",
                "REFUSED SQL compilation error: | Unknown function SP_P1."},
            {"CREATE OR REPLACE PROCEDURE sp_p2() RETURNS ARRAY LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.f' AS $$ import "
                + "com.snowflake.snowpark.Session; object H { def f(s: Session): Seq[String] = Seq(\"a\") } $$",
                "REFUSED Unsupported return type: scala.collection.Seq in function SP_P2 with handler H.f"},
            {"CALL sp_p2()",
                "REFUSED SQL compilation error: | Unknown function SP_P2."},
            {"CREATE OR REPLACE PROCEDURE sp_p3(x INT) RETURNS VARCHAR LANGUAGE SCALA RUNTIME_VERSION = "
                + "'2.12' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.f' AS $$ import "
                + "com.snowflake.snowpark.Session; object H { def f(x: Int): String = \"a\" } $$",
                "REFUSED Failed to find a public method named \"f\" with 2 arguments in function SP_P3 with "
                + "handler H.f"},
            {"CALL sp_p3(1)",
                "REFUSED SQL compilation error: | Unknown function SP_P3."},
            {"CREATE OR REPLACE PROCEDURE sp_p4() RETURNS NUMBER(10,2) LANGUAGE SCALA RUNTIME_VERSION = "
                + "'2.12' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.f' AS $$ import "
                + "com.snowflake.snowpark.Session; object H { def f(s: Session): Int = 1 } $$",
                "REFUSED Cannot encode number with a non-zero scale as an int in function SP_P4 with handler "
                + "H.f"},
            {"CALL sp_p4()",
                "REFUSED SQL compilation error: | Unknown function SP_P4."},
            {"CREATE OR REPLACE PROCEDURE sp_p7() RETURNS VARIANT LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.f' AS $$ import "
                + "com.snowflake.snowpark.Session; object H { def f(s: Session): Map[String, String] = "
                + "Map(\"a\" -> \"1\") } $$",
                "REFUSED Snowflake type VARIANT[LOB](38,0){nullable} is not supported for Java return type "
                + "scala.collection.immutable.Map[String, String] in function SP_P7 with handler H.f"},
            {"CALL sp_p7()",
                "REFUSED SQL compilation error: | Unknown function SP_P7."},
            {"CALL sp_p8(OBJECT_CONSTRUCT('a', 1))",
                "(a,1)"},
            {"SELECT sa1(ARRAY_CONSTRUCT(1, 2))",
                "1|2"},
            {"SELECT sa2(ARRAY_CONSTRUCT(1, 2))",
                "1.0|2.0"},
            {"CREATE OR REPLACE FUNCTION sa3() RETURNS VARIANT LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(): Array[Long] = Array(1L) } $$",
                "REFUSED Snowflake type VARIANT[LOB](38,0){nullable} is not supported for Java return type "
                + "long[] in function SA3 with handler H.f"},
            {"CREATE OR REPLACE FUNCTION sa6() RETURNS ARRAY LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H.f' AS $$ object H { def f(): Array[java.lang.Integer] = Array(1) } $$",
                "REFUSED Unsupported return type: java.lang.Integer[] in function SA6 with handler H.f"},
            {"SELECT TO_JSON(sa7(OBJECT_CONSTRUCT('a', 1, 'b', 'x')))",
                "{\"a\":\"1\",\"b\":\"x\",\"z\":\"9\"}"},
            {"SELECT TO_JSON(sa8())",
                "[1.000000014901161e-01]"},
            {"SELECT TO_JSON(sa9())",
                "[1.000000000000000e-01,1.000000000000000e+300,NaN]"},
            {"SELECT TO_JSON(sa10(ARRAY_CONSTRUCT(1, 'a', NULL, OBJECT_CONSTRUCT('k', 1))))",
                "[\"1\",\"a\",undefined,\"{\\\"k\\\":1}\"]"},
        });
    }
}
