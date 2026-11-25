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

import dev.frostlake.functions.TextArgumentFunction;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.ObjectType;

import java.util.List;

/**
 * TRY_PARSE_IP(ip_address, type) — {@link ParseIp} that answers NULL instead of refusing a malformed address.
 * It takes no permissive flag, and a wrong ip type is still refused (live-verified).
 */
public class TryParseIp extends TextArgumentFunction {

    public TryParseIp() { super("TRY_PARSE_IP", ObjectType.OBJECT); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) return null;
        final String type = ParseIp.ipType(args.get(1));
        try {
            return ArrayFunctionHelper.toCanonicalVariant(
                ParseIp.parse(SharedFunctionHelpers.textOf(args.get(0)), type));
        } catch (final IllegalArgumentException malformed) {
            return null;
        }
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }
}
