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

package dev.frostlake.functions.scalar.string;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The case-folding orbits the account's matcher folds a caseless pattern by: Unicode simple case folding, where
 * {@code É} and {@code é}, {@code Σ}, {@code σ} and {@code ς}, or {@code ß} and {@code ẞ} are one letter, while the
 * dotted capital {@code İ} and the dotless small {@code ı} fold only to themselves (live-verified).
 */
final class CaseFoldOrbits {

    private static final int DOTTED_CAPITAL_I = 0x130;
    private static final int DOTLESS_SMALL_I = 0x131;

    /** Every character whose orbit holds another, ascending. */
    private static final int[] FOLDING;
    /** The orbit of each of those characters, ascending. */
    private static final Map<Integer, int[]> ORBITS = new HashMap<>();

    static {
        final Map<Integer, List<Integer>> byKey = new HashMap<>();
        for (int c = 0; c <= Character.MAX_CODE_POINT; c++) {
            final int key = key(c);
            if (key == c || c >= Character.MIN_SURROGATE && c <= Character.MAX_SURROGATE) {
                continue;
            }
            List<Integer> members = byKey.get(Integer.valueOf(key));
            if (members == null) {
                members = new ArrayList<>(3);
                members.add(Integer.valueOf(key));
                byKey.put(Integer.valueOf(key), members);
            }
            members.add(Integer.valueOf(c));
        }
        final TreeSet<Integer> folding = new TreeSet<>();
        for (final List<Integer> members : byKey.values()) {
            if (members.size() < 2) {
                continue;
            }
            final int[] orbit = new int[members.size()];
            for (int i = 0; i < orbit.length; i++) {
                orbit[i] = members.get(i).intValue();
            }
            Arrays.sort(orbit);
            for (final int member : orbit) {
                ORBITS.put(Integer.valueOf(member), orbit);
                folding.add(Integer.valueOf(member));
            }
        }
        FOLDING = new int[folding.size()];
        int i = 0;
        for (final Integer c : folding) {
            FOLDING[i++] = c.intValue();
        }
    }

    private CaseFoldOrbits() {
    }

    /** The letter a character folds to; characters with one key share an orbit. */
    private static int key(final int c) {
        if (c == DOTTED_CAPITAL_I || c == DOTLESS_SMALL_I) {
            return c;
        }
        return Character.toLowerCase(Character.toUpperCase(c));
    }

    /**
     * The characters a character folds with, itself included, ascending.
     *
     * @param c the character
     * @return its orbit
     */
    static int[] orbit(final int c) {
        final int[] orbit = ORBITS.get(Integer.valueOf(c));
        return orbit != null ? orbit : new int[] {c};
    }

    /**
     * The characters outside a range that fold with a character inside it.
     *
     * @param low  the range's first character
     * @param high the range's last character
     * @return those characters, ascending
     */
    static List<Integer> foldedOutside(final int low, final int high) {
        final TreeSet<Integer> outside = new TreeSet<>();
        int i = Arrays.binarySearch(FOLDING, low);
        if (i < 0) {
            i = -i - 1;
        }
        for (; i < FOLDING.length && FOLDING[i] <= high; i++) {
            for (final int member : ORBITS.get(Integer.valueOf(FOLDING[i]))) {
                if (member < low || member > high) {
                    outside.add(Integer.valueOf(member));
                }
            }
        }
        return new ArrayList<>(outside);
    }
}
