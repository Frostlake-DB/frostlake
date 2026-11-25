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

package dev.frostlake.executor.expressions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Texts that differ only in their literals share one parse tree, and each still builds the very expression a parse
 * of its own builds: the same nodes, values, types and positions, and the same refusal where the build refuses.
 */
public class ShapedExpressionParserTest {

    /** Groups of texts of one shape, read in order so that every later one reuses the first one's tree. */
    private static final String[][] SHAPES = {
        {"1::NUMBER(38,0) + 1", "123456789::NUMBER(38,0) + 1", "7::NUMBER(10,0) + 42"},
        {"1.5::NUMBER(38,1) * 2", "12345.123456::NUMBER(20,6) * 3"},
        {"'a' || '-' || UPPER('v1') || x", "'abcdef' || '-' || UPPER('v123456') || x", "'' || '-' || UPPER('') || x"},
        {"CASE WHEN 1 > x THEN 'small' ELSE 'big' END", "CASE WHEN 100000 > x THEN 'tiny' ELSE 'huge one' END"},
        {"f(1, 'a', 2.5e3)", "f(12345, 'bcd', 1.0e-3)"},
        {"TO_DATE('2024-01-05', 'YYYY-MM-DD') + 1", "TO_DATE('1999-12-31', 'YYYY-MM-DD') + 12"},
        {"x IN (1, 2, 3) AND y LIKE 'a%'", "x IN (100, 200, 300) AND y LIKE 'abc%'"},
        {"(-5) + x", "(-123456) + x"},
        {"PARSE_JSON('[1,2]')::VECTOR(FLOAT, 2)", "PARSE_JSON('[10.5,20.25]')::VECTOR(FLOAT, 2)"},
        {"(SELECT 1 + x)", "(SELECT 22 + x)"},
    };

    @Test
    public void everyTextOfAShapeBuildsItsOwnExpression() {
        for (final String[] group : SHAPES) {
            for (final String text : group) {
                assertSameExpression(text, AntlrExpressionParser.parse(text), ShapedExpressionParser.parse(text));
            }
            // Again in reverse, every one now read from the kept tree.
            for (int i = group.length - 1; i >= 0; i--) {
                assertSameExpression(group[i], AntlrExpressionParser.parse(group[i]),
                    ShapedExpressionParser.parse(group[i]));
            }
        }
    }

    @Test
    public void aRefusalOfTheBuildIsTheParsesOwn() {
        final String[] texts = {"CAST(1 AS NUMBER(38,0))", "CAST(1 AS NUMBER(39,0))", "CAST(1 AS NUMBER(38,0))"};
        for (final String text : texts) {
            String plain = null;
            try {
                AntlrExpressionParser.parse(text);
            } catch (final RuntimeException refused) {
                plain = String.valueOf(refused.getMessage());
            }
            if (plain == null) {
                assertSameExpression(text, AntlrExpressionParser.parse(text), ShapedExpressionParser.parse(text));
            } else {
                final RuntimeException shaped = assertThrows(RuntimeException.class, new Executable() {
                    @Override
                    public void execute() {
                        ShapedExpressionParser.parse(text);
                    }
                });
                assertEquals(plain, String.valueOf(shaped.getMessage()), text);
            }
        }
    }

    @Test
    public void aTextTheGrammarRefusesIsRefusedAsThePlainParseRefusesIt() {
        final String text = "1 + + * 2";
        final RuntimeException plain = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                AntlrExpressionParser.parse(text);
            }
        });
        final RuntimeException shaped = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                ShapedExpressionParser.parse(text);
            }
        });
        assertEquals(plain.getClass(), shaped.getClass());
        assertEquals(plain.getMessage(), shaped.getMessage());
    }

    private static void assertSameExpression(final String text, final Expression expected, final Expression actual) {
        assertTrue(expected != actual, text);
        assertEquals(AstPrinterVisitor.print(expected), AstPrinterVisitor.print(actual), text);
        assertDeepEquals(text, "", expected, actual, new IdentityHashMap<Object, Object>());
    }

    /** Field by field through the engine's own objects, so that every value, type and position is compared. */
    private static void assertDeepEquals(final String text, final String path, final Object expected,
                                         final Object actual, final Map<Object, Object> seen) {
        if (expected == null || actual == null) {
            assertEquals(expected, actual, text + " at " + path);
            return;
        }
        assertEquals(expected.getClass(), actual.getClass(), text + " at " + path);
        if (expected instanceof List) {
            final List<?> left = (List<?>) expected;
            final List<?> right = (List<?>) actual;
            assertEquals(left.size(), right.size(), text + " at " + path);
            for (int i = 0; i < left.size(); i++) {
                assertDeepEquals(text, path + "[" + i + "]", left.get(i), right.get(i), seen);
            }
            return;
        }
        if (!expected.getClass().getName().startsWith("dev.frostlake.") || expected.getClass().isEnum()) {
            assertEquals(expected, actual, text + " at " + path);
            return;
        }
        if (seen.put(expected, actual) != null) {
            return;
        }
        for (Class<?> type = expected.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (final Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    assertDeepEquals(text, path + "." + field.getName(), field.get(expected), field.get(actual), seen);
                } catch (final IllegalAccessException unreadable) {
                    throw new IllegalStateException(unreadable);
                }
            }
        }
    }
}
