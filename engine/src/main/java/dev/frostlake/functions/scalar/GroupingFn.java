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

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.IntegerResultWidths;

import java.util.List;

/**
 * GROUPING(e1, …, en) — 1 for each argument a ROLLUP, CUBE or GROUPING SETS row aggregates over, 0 for each it groups
 * by. A super-group row's mask is read from its grouping set wherever the call stands (see
 * {@code GroupingSetScope}); this base result is the one of a plain GROUP BY, which groups by every key: 0, whether
 * or not the key's value is NULL.
 */
public class GroupingFn extends BuiltInFunction {

    public GroupingFn() {
        super("GROUPING", IntegerResultWidths.COUNTER);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        return 0L;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return Integer.MAX_VALUE; }
}
