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

/**
 * A body compiled whole although the statement reads only some of its items: a view's body planned as written
 * beside the text with its unread columns blanked, or an EXISTS's body whose select list stands unread. Live
 * removes what nobody reads before it folds the constants that remain, so the constant arithmetic of such a body
 * is not refused while it compiles (see {@code executor.expressions.ConstantArithmeticFold}): {@code SELECT y FROM
 * v} answers over a view whose other column is a constant product its type cannot hold, and so does {@code EXISTS
 * (SELECT <that product>)} (live-verified).
 */
public final class UnfoldedBody {

    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<Boolean>();

    private UnfoldedBody() {
    }

    /** Whether the current thread compiles such a body. */
    public static boolean isActive() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }

    /**
     * Enters such a body.
     *
     * @return the state to put back with {@link #end}
     */
    public static boolean begin() {
        final boolean previous = isActive();
        ACTIVE.set(Boolean.TRUE);
        return previous;
    }

    /**
     * Leaves such a body.
     *
     * @param previous the state {@link #begin} handed back
     */
    public static void end(final boolean previous) {
        if (previous) {
            ACTIVE.set(Boolean.TRUE);
        } else {
            ACTIVE.remove();
        }
    }
}
