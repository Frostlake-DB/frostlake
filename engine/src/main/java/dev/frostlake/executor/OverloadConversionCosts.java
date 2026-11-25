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

package dev.frostlake.executor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What an argument's conversion to a parameter's type weighs when a call chooses among a routine's overloads. A
 * call runs the overload whose conversions weigh least in total, over all its arguments together — not the one its
 * first argument prefers — and an untyped NULL weighs nothing anywhere (live-verified over pairs and triples of
 * two- and three-argument overloads, of functions and of procedures, called by position and by name):
 *
 * <pre>
 *   argument   parameter, with its weight
 *   NUMBER     NUMBER 0, FLOAT 1, BOOLEAN 2, VARIANT 2, VARCHAR 4
 *   FLOAT      FLOAT 0, BOOLEAN 2, VARIANT 2, VARCHAR 4, NUMBER N
 *   VARCHAR    VARCHAR 0, BOOLEAN 1, FLOAT 2, DATE 2, TIMESTAMP 2, NUMBER 3, TIME 3
 *   BOOLEAN    BOOLEAN 0, VARCHAR 1, VARIANT 1
 *   DATE       DATE 0, TIMESTAMP 1, VARCHAR 4
 *   TIME       TIME 0, VARCHAR 4
 *   TIMESTAMP  TIMESTAMP 0, VARCHAR 4, DATE N, TIME N
 *   VARIANT    VARIANT 0, ARRAY 1, OBJECT 1, BOOLEAN 1, VARCHAR 1, DATE 2, TIME 2, TIMESTAMP 2, FLOAT N, NUMBER 2N
 *   ARRAY      ARRAY 0, VARIANT 1
 *   OBJECT     OBJECT 0, VARIANT 1
 *   BINARY     BINARY 0
 * </pre>
 *
 * <p>Every weight is measured against the others in whole steps of a NUMBER read as a FLOAT, by pairs of overloads
 * called by name whose arguments disagree; a function and a procedure's CALL weigh alike. N, a narrowing
 * conversion, weighs more than forty such steps together, and two of them weigh what a VARIANT read as a NUMBER
 * does. Overloads that weigh alike are told apart by how the call was written: by position the first in the order
 * of their signatures runs — the account's internal type names in alphabetical order, parameter by parameter,
 * whatever order the overloads were created in — and by name the call is refused, {@code More than one function
 * of F are resolved in prepare state.} The pairs a row leaves out are refused outright, beside an overload that
 * takes the call too (a text is no VARIANT argument, a BOOLEAN no FLOAT one, an ARRAY no OBJECT one, a BINARY
 * reaches only a BINARY). A TIME reaches a timestamp parameter of any kind before a text
 * one, and is then refused there, since no TIME converts to a timestamp: {@code incompatible types: [TIME(9)] and
 * [TIMESTAMP_NTZ(9)]}.
 *
 * <p>A text weighs more for a TIME than for a timestamp, a DATE or a FLOAT, and goes to a NUMBER before a TIME:
 * {@code ('10:00:00')} runs the TIMESTAMP_NTZ, the TIMESTAMP_LTZ or the NUMBER overload beside a TIME one. The
 * timestamp kinds sort among themselves by their own names, so a text or a NULL runs TIMESTAMP_NTZ before
 * TIMESTAMP_TZ, and a NULL TIME before any of them (live-verified).
 */
public final class OverloadConversionCosts {

    /**
     * What a narrowing conversion weighs — a FLOAT read as a NUMBER, a VARIANT as a FLOAT, a timestamp as a DATE or
     * a TIME: more than any sum of the other conversions a call can make, and a VARIANT read as a NUMBER weighs
     * two of them.
     */
    private static final int NARROWING = 1024;

    /** The family of an untyped NULL, which weighs nothing for any parameter. */
    public static final String UNTYPED_NULL = "NULL";

    /** The parameter families in the order of the account's internal type names, which is how signatures sort. */
    private static final List<String> SIGNATURE_ORDER = Arrays.asList("ARRAY", "BINARY", "BOOLEAN", "DATE",
        "NUMBER", "OBJECT", "FLOAT", "VARCHAR", "TIME", "TIMESTAMP_LTZ", "TIMESTAMP_NTZ", "TIMESTAMP_TZ", "VARIANT");

    /** The families whose rows name every conversion the account makes between them. */
    private static final Set<String> COMPLETE_ROWS = new HashSet<String>(Arrays.asList("NUMBER", "FLOAT", "VARCHAR",
        "BOOLEAN", "DATE", "TIME", "TIMESTAMP", "VARIANT", "ARRAY", "OBJECT", "BINARY"));

    /** What each conversion weighs, by the argument's family and then the parameter's. */
    private static final Map<String, Map<String, Integer>> WEIGHTS = new HashMap<String, Map<String, Integer>>();

    static {
        row("NUMBER", new String[] {"NUMBER", "FLOAT", "BOOLEAN", "VARIANT", "VARCHAR"}, new int[] {0, 1, 2, 2, 4});
        row("FLOAT", new String[] {"FLOAT", "BOOLEAN", "VARIANT", "VARCHAR", "NUMBER"},
            new int[] {0, 2, 2, 4, NARROWING});
        row("VARCHAR", new String[] {"VARCHAR", "BOOLEAN", "FLOAT", "DATE", "TIMESTAMP", "NUMBER", "TIME"},
            new int[] {0, 1, 2, 2, 2, 3, 3});
        row("DECFLOAT", new String[] {"DECFLOAT", "NUMBER", "FLOAT", "VARCHAR", "VARIANT", "BOOLEAN"},
            new int[] {0, 1, 2, 3, 4, 5});
        row("UUID", new String[] {"UUID", "VARCHAR", "BOOLEAN", "FLOAT", "NUMBER", "VARIANT"},
            new int[] {0, 1, 2, 3, 4, 5});
        row("BOOLEAN", new String[] {"BOOLEAN", "VARCHAR", "VARIANT"}, new int[] {0, 1, 1});
        row("DATE", new String[] {"DATE", "TIMESTAMP", "VARCHAR"}, new int[] {0, 1, 4});
        row("TIME", new String[] {"TIME", "TIMESTAMP", "VARCHAR"}, new int[] {0, 1, 4});
        row("TIMESTAMP", new String[] {"TIMESTAMP", "VARCHAR", "DATE", "TIME"},
            new int[] {0, 4, NARROWING, NARROWING});
        row("VARIANT", new String[] {"VARIANT", "ARRAY", "OBJECT", "BOOLEAN", "VARCHAR", "DATE", "TIME", "TIMESTAMP",
            "FLOAT", "NUMBER"}, new int[] {0, 1, 1, 1, 1, 2, 2, 2, NARROWING, 2 * NARROWING});
        row("ARRAY", new String[] {"ARRAY", "VARIANT"}, new int[] {0, 1});
        row("OBJECT", new String[] {"OBJECT", "VARIANT"}, new int[] {0, 1});
        row("BINARY", new String[] {"BINARY"}, new int[] {0});
    }

    private OverloadConversionCosts() {
    }

    private static void row(final String argument, final String[] parameters, final int[] weights) {
        final Map<String, Integer> row = new HashMap<String, Integer>();
        for (int i = 0; i < parameters.length; i++) {
            row.put(parameters[i], weights[i]);
        }
        WEIGHTS.put(argument, row);
    }

    /**
     * What an argument's conversion to a parameter weighs.
     *
     * @param argument  the argument's family, {@link #UNTYPED_NULL} for an untyped NULL, or null for one no row
     *                  names
     * @param parameter the parameter's family
     * @return the weight, lower being preferred, or -1 when the argument's row does not name the parameter
     */
    public static int of(final String argument, final String parameter) {
        if (UNTYPED_NULL.equals(argument)) {
            // An untyped NULL reaches every parameter but a DECFLOAT one (live-verified).
            return "DECFLOAT".equals(parameter) ? -1 : 0;
        }
        final Map<String, Integer> row = argument == null ? null : WEIGHTS.get(argument);
        final Integer weight = row == null || parameter == null ? null : row.get(parameter);
        return weight == null ? -1 : weight;
    }

    /**
     * Whether the account refuses an argument's conversion to a parameter outright, an overload asking for it
     * being out of the running however its other arguments weigh: a pair between two families whose rows are
     * complete that the argument's row does not name — a VARIANT read as a BINARY, a BOOLEAN as a FLOAT.
     *
     * @param argument  the argument's family, or null for one no row names
     * @param parameter the parameter's family
     * @return whether the conversion is refused
     */
    public static boolean refuses(final String argument, final String parameter) {
        return argument != null && parameter != null && COMPLETE_ROWS.contains(argument)
            && COMPLETE_ROWS.contains(parameter) && of(argument, parameter) < 0;
    }

    /**
     * Where a parameter's family stands in the order signatures sort by, a timestamp by its kind.
     *
     * @param parameter the parameter's family, or a timestamp's kind (TIMESTAMP_LTZ, TIMESTAMP_NTZ or TIMESTAMP_TZ)
     * @return the position, a family the order does not name last
     */
    public static int signatureOrder(final String parameter) {
        final int at = SIGNATURE_ORDER.indexOf(parameter);
        return at < 0 ? SIGNATURE_ORDER.size() : at;
    }
}
