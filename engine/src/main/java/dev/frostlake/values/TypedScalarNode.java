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

package dev.frostlake.values;

import dev.frostlake.functions.scalar.FractionScale;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.StringNode;

/**
 * A JSON string node that remembers the TYPED value it was rendered from.
 *
 * <p>Snowflake's VARIANT is a superset of JSON: a DATE, TIME, TIMESTAMP or BINARY keeps its own type
 * inside an OBJECT or ARRAY, so {@code TYPEOF(OBJECT_CONSTRUCT('b', TO_BINARY('CAFE','HEX')):b)} is
 * {@code BINARY} and {@code AS_BINARY} over that member returns the bytes. JSON has no such types —
 * it can only carry the rendered text — so a plain string node loses the distinction.
 *
 * <p>This node closes that gap without changing a single byte of the JSON text: it IS a string node,
 * so it serializes, canonicalizes, compares and hashes exactly as the text form always did (TO_JSON,
 * {@code ::VARCHAR}, OBJECT_KEYS and the stored/WAL text are all untouched). Extraction —
 * {@code ArrayFunctionHelper.fromNode} and the colon-path extractor — recognises it and hands back
 * the original typed value instead of the text, which is what makes TYPEOF and the AS_* / IS_* family
 * answer like live.
 *
 * <p>The typed value rides along in memory only. A variant that has been re-parsed from its canonical
 * text (a persistence or wire round-trip) carries plain string nodes again and reports VARCHAR, the
 * same answer as before this node existed — recovering the type through the text alone would need a
 * tagged wire encoding, which would change what TO_JSON and {@code ::VARCHAR} return.
 */
public final class TypedScalarNode extends StringNode {

    private static final long serialVersionUID = 1L;

    private final transient Object value;

    /** The fraction digits of the time or timestamp's type, or -1 where none is known (see FractionScale). */
    private final int scale;

    public TypedScalarNode(final String text, final Object value) {
        this(text, value, -1);
    }

    /**
     * A member that also keeps the scale of its time or timestamp type, which a bare FF of the session's output format
     * prints once the member is read back and rendered as text.
     *
     * @param text  the member's JSON text
     * @param value the typed value
     * @param scale the fraction digits of its type, or -1 where none is known
     */
    public TypedScalarNode(final String text, final Object value, final int scale) {
        super(text);
        this.value = value;
        this.scale = scale;
    }

    /** The typed runtime value this member was built from (a BinaryValue, LocalDate, LocalTime, …). */
    public Object typedValue() {
        return value;
    }

    /**
     * Deep copies keep the type. Both this node and its value are immutable, so the copy can be the
     * node itself — losing the type here would make a canonicalized or copied container answer
     * VARCHAR while an untouched one answers BINARY.
     */
    @Override
    public TypedScalarNode deepCopy() {
        return this;
    }

    /**
     * Whether a typed member sits anywhere in {@code node}: it prints as the string of its text and still
     * differs from that string, so two variants of the same text are not yet known to be equal.
     */
    public static boolean anywhereIn(final JsonNode node) {
        if (node instanceof TypedScalarNode) {
            return true;
        }
        if (node == null || !node.isContainer()) {
            return false;
        }
        for (final JsonNode member : node) {
            if (anywhereIn(member)) {
                return true;
            }
        }
        return false;
    }

    /** The fraction digits of the member's time or timestamp type, or -1 where none is known. */
    public int scale() {
        return scale;
    }

    /**
     * The typed value of {@code node} when it carries one, else null. A time or timestamp read back this way keeps the
     * scale of its type for its later rendering as text (see FractionScale#carry).
     */
    public static Object typedValueOf(final Object node) {
        if (!(node instanceof TypedScalarNode)) {
            return null;
        }
        final TypedScalarNode typed = (TypedScalarNode) node;
        FractionScale.carry(typed.value, typed.scale);
        return typed.value;
    }
}
