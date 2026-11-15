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
 * An inline Java body is compiled as the account's compiler of its routine's level compiles it (live-verified): at
 * RUNTIME_VERSION 11, the default, a class of an imported jar compiled for Java 17 cannot be read and is reported at
 * the body's first use of it, and a body that opens with no type is "class, interface, or enum expected", where 17 and
 * 21 name records too. A body's class reaches a class of a jar it imports that its package keeps to itself.
 */
public class JavaInlineLevelTest extends MeasuredCellsTestSupport {

    @Test
    public void aBodyIsCompiledAtItsRoutinesLevel() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE STAGE st77",
                "Stage area ST77 successfully created."},
            {"CREATE OR REPLACE FUNCTION h77(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '17' HANDLER = "
                + "'Helper77v.thrice' TARGET_PATH = '@st77/h77v.jar' AS 'class Helper77v { public static int "
                + "thrice(int x) { return 3 * x; } }'",
                "Function H77 successfully created."},
            {"SELECT h77(5)",
                "15"},
            {"CREATE OR REPLACE FUNCTION j77c(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('@st77/h77v.jar') HANDLER = 'J77.run' AS 'class J77 { public static int run(int x) { return "
                + "Helper77v.thrice(x); } }'",
                "REFUSED Error while compiling source: /InlineCode.java:1: error: cannot access Helper77v | class "
                    + "J77 { public static int run(int x) { return Helper77v.thrice(x); } } |                       "
                    + "                            ^ |   bad class file: /home/udf/0/h77v.jar(/Helper77v.class) |   "
                    + "  class file has wrong version 61.0, should be 55.0 |     Please remove or make sure it "
                    + "appears in the correct subdirectory of the classpath. | 1 error |  in function J77C with "
                    + "handler J77.run"},
            {"CREATE OR REPLACE FUNCTION j77d(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' IMPORTS = "
                + "('@st77/h77v.jar') HANDLER = 'J77d.run' AS 'class J77d {\\n  static int keep = 1;\\n  public "
                + "static int run(int x) {\\n    int y = x + keep;\\n    return Helper77v.thrice(y) + "
                + "Helper77v.thrice(1);\\n  }\\n}'",
                "REFUSED Error while compiling source: /InlineCode.java:5: error: cannot access Helper77v |     "
                    + "return Helper77v.thrice(y) + Helper77v.thrice(1); |            ^ |   bad class file: "
                    + "/home/udf/0/h77v.jar(/Helper77v.class) |     class file has wrong version 61.0, should be "
                    + "55.0 |     Please remove or make sure it appears in the correct subdirectory of the "
                    + "classpath. | 1 error |  in function J77D with handler J77d.run"},
            {"CREATE OR REPLACE FUNCTION j77e(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '17' IMPORTS = "
                + "('@st77/h77v.jar') HANDLER = 'J77e.run' AS 'class J77e { public static int run(int x) { return "
                + "Helper77v.thrice(x); } }'",
                "Function J77E successfully created."},
            {"SELECT j77e(5)",
                "15"},
            {"CREATE OR REPLACE FUNCTION j77f(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' HANDLER = "
                + "'J77f.run' AS 'not java at all'",
                "REFUSED Error while compiling source: /InlineCode.java:1: error: class, interface, or enum "
                    + "expected | not java at all | ^ | 1 error |  in function J77F with handler J77f.run"},
            {"CREATE OR REPLACE FUNCTION j77g(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '17' HANDLER = "
                + "'J77g.run' AS 'not java at all'",
                "REFUSED Error while compiling source: /InlineCode.java:1: error: class, interface, enum, or record "
                    + "expected | not java at all | ^ | 1 error |  in function J77G with handler J77g.run"},
            {"CREATE OR REPLACE FUNCTION j77h(x INT) RETURNS INT LANGUAGE JAVA HANDLER = 'J77h.run' AS 'not java at "
                + "all'",
                "REFUSED Error while compiling source: /InlineCode.java:1: error: class, interface, or enum "
                    + "expected | not java at all | ^ | 1 error |  in function J77H with handler J77h.run"},
            {"CREATE OR REPLACE FUNCTION j77i(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' HANDLER = "
                + "'J77i.run' AS 'int x = 1;'",
                "REFUSED Error while compiling source: /InlineCode.java:1: error: class, interface, or enum "
                    + "expected | int x = 1; | ^ | 1 error |  in function J77I with handler J77i.run"},
            {"CREATE OR REPLACE FUNCTION j77j(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '11' HANDLER = "
                + "'J77j.run' AS 'class J77j { } }'",
                "REFUSED Error while compiling source: /InlineCode.java:1: error: class, interface, or enum "
                    + "expected | class J77j { } } |                ^ | 1 error |  in function J77J with handler "
                    + "J77j.run"},
            {"CREATE OR REPLACE FUNCTION j77k(x INT) RETURNS INT LANGUAGE JAVA RUNTIME_VERSION = '21' HANDLER = "
                + "'J77k.run' AS 'not java at all'",
                "REFUSED Error while compiling source: /InlineCode.java:1: error: class, interface, enum, or record "
                    + "expected | not java at all | ^ | 1 error |  in function J77K with handler J77k.run"},
        });
    }
}
