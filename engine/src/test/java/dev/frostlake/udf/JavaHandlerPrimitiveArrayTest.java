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

package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A Java handler's primitive arrays: an {@code int[]}, {@code long[]}, {@code short[]}, {@code float[]},
 * {@code double[]} or {@code boolean[]} taken for an ARRAY argument and answered for an ARRAY result, each element
 * read as its class takes it, and refused where it cannot be. Every answer is live-verified.
 */
public class JavaHandlerPrimitiveArrayTest extends MeasuredCellsTestSupport {

    @Test
    public void primitiveArraysCrossAsArrays() {
        engine.execute("CREATE OR REPLACE FUNCTION jr1() RETURNS ARRAY LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H {"
            + " public static int[] f() { return new int[] {1, 2}; } } $$");
        engine.execute("CREATE OR REPLACE FUNCTION jr2() RETURNS ARRAY LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H {"
            + " public static float[] f() { return new float[] {1.5f, 2f}; } } $$");
        engine.execute("CREATE OR REPLACE FUNCTION jr3() RETURNS ARRAY LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H {"
            + " public static long[] f() { return new long[] {1L}; } } $$");
        engine.execute("CREATE OR REPLACE FUNCTION jr4() RETURNS ARRAY LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H {"
            + " public static double[] f() { return new double[] {1.5}; } } $$");
        engine.execute("CREATE OR REPLACE FUNCTION ja1(x ARRAY) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
            + "class H { public static String f(int[] x) { return java.util.Arrays.toString(x); } } $$");
        engine.execute("CREATE OR REPLACE FUNCTION ja2(x ARRAY) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
            + "class H { public static String f(float[] x) { return java.util.Arrays.toString(x); } } $$");
        engine.execute("CREATE OR REPLACE FUNCTION ja3(x ARRAY) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
            + "class H { public static String f(long[] x) { return java.util.Arrays.toString(x); } } $$");
        engine.execute("CREATE OR REPLACE FUNCTION ja4(x ARRAY) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
            + "class H { public static String f(double[] x) { return java.util.Arrays.toString(x); } } $$");
        engine.execute("CREATE OR REPLACE FUNCTION k1() RETURNS ARRAY LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H { "
            + "public static short[] f() { return new short[] {1, 2}; } } $$");
        engine.execute("CREATE OR REPLACE FUNCTION k2() RETURNS ARRAY LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H { "
            + "public static boolean[] f() { return new boolean[] {true}; } } $$");
        engine.execute("CREATE OR REPLACE FUNCTION k4(x ARRAY) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
            + "class H { public static String f(short[] x) { return java.util.Arrays.toString(x); } } $$");
        engine.execute("CREATE OR REPLACE FUNCTION k5(x ARRAY) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
            + "class H { public static String f(boolean[] x) { return java.util.Arrays.toString(x); } } $$");
        engine.execute("CREATE OR REPLACE FUNCTION ja4(x ARRAY) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
            + "class H { public static String f(double[] x) { return java.util.Arrays.toString(x); } } $$");
        engine.execute("CREATE OR REPLACE FUNCTION ja1(x ARRAY) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
            + "class H { public static String f(int[] x) { return java.util.Arrays.toString(x); } } $$");
        engine.execute("CREATE OR REPLACE FUNCTION jr9() RETURNS ARRAY LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H {"
            + " public static double[] f() { return new double[] {Double.POSITIVE_INFINITY, -0.0, 3}; } } "
            + "$$");
        assertCells(new String[][] {
            {"SELECT TO_JSON(jr1())",
                "[1,2]"},
            {"SELECT TO_JSON(jr2())",
                "[1.500000000000000e+00,2.000000000000000e+00]"},
            {"SELECT TO_JSON(jr3())",
                "[1]"},
            {"SELECT TO_JSON(jr4())",
                "[1.500000000000000e+00]"},
            {"CREATE OR REPLACE FUNCTION jr5() RETURNS VARIANT LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H"
                + " { public static int[] f() { return new int[] {1}; } } $$",
                "REFUSED Snowflake type VARIANT[LOB](38,0){nullable} is not supported for Java return type "
                + "int[] in function JR5 with handler H.f"},
            {"CREATE OR REPLACE FUNCTION jr6(x INT) RETURNS INT LANGUAGE JAVA HANDLER = 'H.f' AS $$ class "
                + "H { public static int f(int[] x) { return 1; } } $$",
                "REFUSED Snowflake type FIXED[SB16](38,0){nullable} is not supported as input to argument at "
                + "index 0 with Java type int[] in function JR6 with handler H.f"},
            {"CREATE OR REPLACE FUNCTION jr7() RETURNS OBJECT LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H "
                + "{ public static float[] f() { return null; } } $$",
                "REFUSED Snowflake type OBJECT[LOB](38,0){nullable} is not supported for Java return type "
                + "float[] in function JR7 with handler H.f"},
            {"SELECT TO_JSON(k1())",
                "[1,2]"},
            {"SELECT TO_JSON(k2())",
                "[true]"},
            {"CREATE OR REPLACE FUNCTION k6(x ARRAY) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                + "class H { public static String f(Integer[] x) { return java.util.Arrays.toString(x); } } $$",
                "REFUSED Unsupported argument type: java.lang.Integer[] in function K6 with handler H.f"},
            {"CREATE OR REPLACE FUNCTION k7(x ARRAY) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H.f' AS $$ "
                + "class H { public static String f(char[] x) { return \"a\"; } } $$",
                "REFUSED Unsupported argument type: char[] in function K7 with handler H.f"},
            {"CREATE OR REPLACE FUNCTION k8() RETURNS ARRAY LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H { "
                + "public static byte[] f() { return new byte[] {1}; } } $$",
                "REFUSED Snowflake type ARRAY[LOB](38,0){nullable} is not supported for Java return type "
                + "byte[] in function K8 with handler H.f"},
            {"SELECT ja4(ARRAY_CONSTRUCT(1.5, '2', NULL))",
                "REFUSED Failed to cast variant value of type TEXT to REAL in function JA4 with handler H.f"},
            {"SELECT ja4(ARRAY_CONSTRUCT(1.5, '2'))",
                "REFUSED Failed to cast variant value of type TEXT to REAL in function JA4 with handler H.f"},
            {"SELECT ja4(ARRAY_CONSTRUCT(TRUE))",
                "REFUSED Failed to cast variant value of type BOOLEAN to REAL in function JA4 with handler "
                + "H.f"},
            {"SELECT ja1(ARRAY_CONSTRUCT(TRUE))",
                "REFUSED Failed to cast variant value of type BOOLEAN to FIXED in function JA1 with handler "
                + "H.f"},
            {"SELECT ja1(ARRAY_CONSTRUCT(2.0))",
                "[2]"},
            {"SELECT ja1(ARRAY_CONSTRUCT(ARRAY_CONSTRUCT(1)))",
                "REFUSED Failed to cast variant value of type ARRAY to FIXED in function JA1 with handler H.f"},
            {"SELECT ja1(PARSE_JSON('[1e2]'))",
                "REFUSED Failed to cast variant value of type REAL to FIXED in function JA1 with handler H.f"},
            {"SELECT ja1(NULL)",
                "null"},
            {"SELECT ja1(ARRAY_CONSTRUCT(-2147483648))",
                "[-2147483648]"},
            {"SELECT TO_JSON(jr9())",
                "[Infinity,-0.000000000000000e+00,3.000000000000000e+00]"},
            {"SELECT ja1(ARRAY_CONSTRUCT(1.5, 'x'))",
                "REFUSED Failed to cast variant value with non-zero scale to FIXED in function JA1 with "
                + "handler H.f"},
            {"SELECT ja1(ARRAY_CONSTRUCT(1, NULL))",
                "REFUSED Argument type int[] cannot have a null element in function JA1 with handler H.f"},
            {"SELECT ja2(ARRAY_CONSTRUCT('a'))",
                "REFUSED Failed to cast variant value of type TEXT to REAL in function JA2 with handler H.f"},
            {"SELECT ja1(ARRAY_CONSTRUCT())",
                "[]"},
            {"SELECT ja1(ARRAY_CONSTRUCT(3000000000))",
                "REFUSED Number out of representable range: type int32, value 3000000000 in function JA1 with"
                + " handler H.f"},
            {"SELECT ja1(ARRAY_CONSTRUCT('7'))",
                "REFUSED Failed to cast variant value of type TEXT to FIXED in function JA1 with handler H.f"},
            {"SELECT ja1(ARRAY_CONSTRUCT(1.5))",
                "REFUSED Failed to cast variant value with non-zero scale to FIXED in function JA1 with "
                + "handler H.f"},
        });
    }
}
