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
 * A JAR handler reads Snowpark's classes only where its routine's PACKAGES name Snowpark: elsewhere the routine is
 * created, and a call that reads a Snowpark class fails as the account's class loader refuses one no jar holds, a
 * NoClassDefFoundError caused by a ClassNotFoundException (live-verified).
 */
public class JarHandlerSnowparkPackageTest extends MeasuredCellsTestSupport {

    private static final String READS_SESSION = "$$ import com.snowflake.snowpark_java.Session; class Sp1 { "
        + "public static String run(int x) { return Session.class.getName() + x; } } $$";

    @Test
    public void aJarHandlerReadsSnowparkOnlyWhereItsPackagesNameIt() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE STAGE jst", "Stage area JST successfully created."},
            {"CREATE OR REPLACE FUNCTION jsp1(x INT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' PACKAGES = "
                + "('com.snowflake:snowpark:latest') HANDLER = 'Sp1.run' TARGET_PATH = '@jst/sp1.jar' AS "
                + READS_SESSION, "Function JSP1 successfully created."},
            {"SELECT jsp1(1)", "com.snowflake.snowpark_java.Session1"},
            {"CREATE OR REPLACE FUNCTION jsp2(x INT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('@jst/sp1.jar') HANDLER = 'Sp1.run'", "Function JSP2 successfully created."},
            {"SELECT jsp2(1)",
                "REFUSED User Error Report:  | Java Stack Trace: | java.lang.NoClassDefFoundError: "
                    + "com/snowflake/snowpark_java/Session | \tat function_handler_0//Sp1.run(InlineCode.java:1) | "
                    + "Caused by: java.lang.ClassNotFoundException: com.snowflake.snowpark_java.Session | \tat "
                    + "java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:476) | \tat "
                    + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:589) | \tat "
                    + "java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:522) | \t... 1 more |  in "
                    + "function JSP2 with handler Sp1.run"},
            {"CREATE OR REPLACE FUNCTION jsp3(x INT) RETURNS VARCHAR LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('@jst/sp1.jar') PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'Sp1.run'",
                "Function JSP3 successfully created."},
            {"SELECT jsp3(1)", "com.snowflake.snowpark_java.Session1"},
        });
    }
}
