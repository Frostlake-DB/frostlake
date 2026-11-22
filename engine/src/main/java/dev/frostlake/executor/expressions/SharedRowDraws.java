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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The values ENCRYPT and ENCRYPT_RAW drew for the row being evaluated. A plan computes two calls written alike once
 * for a row, so both read one ciphertext, while the next row draws its own (live-verified):
 * {@code ENCRYPT('msg', 'key') = ENCRYPT('msg', 'key')} is true, and so is {@code ENCRYPT(m, k) = ENCRYPT(t.m, K)}
 * over one relation's row. Calls are alike when they take as many arguments and each pair is the same column of
 * the row, the same constant of the same type - {@code 'ms' || 'g'} is {@code 'msg'}, where {@code 'msg'::VARCHAR}
 * and {@code 'AES-GCM'} beside {@code 'aes-gcm'} are not - or the same expression as written.
 */
final class SharedRowDraws {

    /** The functions whose equal calls share one drawn value in a row. */
    private static final Set<String> SHARED = Set.of("ENCRYPT", "ENCRYPT_RAW");

    private final Map<String, Object> draws = new HashMap<String, Object>();
    /** What the values were drawn for: a relation's row, or the one row of a query without a FROM. */
    private Object row;

    /**
     * @param call a call
     * @return true when the calls written like it share one value in a row
     */
    static boolean shares(final FunctionCallExpression call) {
        return call.getNameExpression() == null && call.getFunctionName() != null && !call.isStar()
            && SHARED.contains(call.getFunctionName().toUpperCase(Locale.ROOT));
    }

    /**
     * The value a call reads for a row: the one an equal call drew for the row before, else the one just drawn.
     *
     * @param current the row being evaluated - a query without a FROM is one row whatever item reads it - or null
     * @param key     what tells the call from one written otherwise
     * @param drawn   the value the call just drew
     * @return the value the row's equal calls share
     */
    Object shared(final Object current, final String key, final Object drawn) {
        if (current != row) {
            draws.clear();
            row = current;
        }
        if (drawn == null || current == null) {
            return drawn;
        }
        final Object earlier = draws.get(key);
        if (earlier != null) {
            return earlier;
        }
        draws.put(key, drawn);
        return drawn;
    }
}
