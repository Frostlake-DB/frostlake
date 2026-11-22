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

package dev.frostlake.functions.scalar;

import java.util.IdentityHashMap;

/**
 * The fraction digits a bare FF prints while one value is formatted: the scale of the value's own type, a
 * TIMESTAMP_NTZ(3)'s three digits, a TIME(1)'s one, a scale of 0's none (live-verified, TO_CHAR over each type, and a
 * session output format over a cast, TO_VARCHAR and a VARIANT's member). Per thread, as statements run on several at
 * once; -1 while no scale is known, and FF then prints nine digits. A call that renders several values as text knows
 * each one's scale by the value itself (see {@link #enterValues}).
 */
public final class FractionScale {

    /** The scales of the values one call renders, each value by its identity; null while none is known. */
    private static final ThreadLocal<IdentityHashMap<Object, Integer>> VALUES =
        new ThreadLocal<IdentityHashMap<Object, Integer>>();

    private static final ThreadLocal<int[]> SCALE = new ThreadLocal<int[]>() {
        @Override
        protected int[] initialValue() {
            return new int[] {-1};
        }
    };

    private FractionScale() {
    }

    /**
     * Sets the scale a bare FF prints until {@link #exit}.
     *
     * @param scale the digits, or -1 for none known
     * @return the scale displaced, for exit
     */
    public static int enter(final int scale) {
        final int[] held = SCALE.get();
        final int displaced = held[0];
        held[0] = scale;
        return displaced;
    }

    /**
     * Puts back the scale {@link #enter} displaced.
     *
     * @param displaced the scale enter returned
     */
    public static void exit(final int displaced) {
        SCALE.get()[0] = displaced;
    }

    /** @return the scale a bare FF prints, or -1 for nine digits */
    public static int current() {
        return SCALE.get()[0];
    }

    /**
     * Sets the scales of the values a call renders until {@link #exitValues}.
     *
     * @param scales each value, by its identity, to the scale of its type
     * @return the scales displaced, for exitValues
     */
    public static IdentityHashMap<Object, Integer> enterValues(final IdentityHashMap<Object, Integer> scales) {
        final IdentityHashMap<Object, Integer> displaced = VALUES.get();
        VALUES.set(scales);
        return displaced;
    }

    /**
     * Puts back the scales {@link #enterValues} displaced.
     *
     * @param displaced the scales enterValues returned
     */
    public static void exitValues(final IdentityHashMap<Object, Integer> displaced) {
        VALUES.set(displaced);
    }

    /**
     * The scale a bare FF prints for one value: the scale its call knows it by, else {@link #current}.
     *
     * @param value the value
     * @return the digits, or -1 for nine
     */
    public static int of(final Object value) {
        final IdentityHashMap<Object, Integer> scales = VALUES.get();
        final Integer scale = scales == null ? null : scales.get(value);
        return scale != null ? scale.intValue() : current();
    }
}
