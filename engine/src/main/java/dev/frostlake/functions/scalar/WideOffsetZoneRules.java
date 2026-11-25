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

import java.time.ZoneOffset;
import java.time.zone.ZoneRules;
import java.time.zone.ZoneRulesProvider;
import java.util.LinkedHashSet;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * The rules behind the zones that carry a TIMESTAMP_TZ offset wider than a zone offset holds: each such zone keeps
 * the instant at UTC, while its name keeps the offset the value was written with (see {@link WideOffsetZone}).
 */
public final class WideOffsetZoneRules extends ZoneRulesProvider {

    /** The rules every wide-offset zone shares: the instant at UTC. */
    private static final ZoneRules AT_UTC = ZoneRules.of(ZoneOffset.UTC);

    @Override
    protected Set<String> provideZoneIds() {
        final Set<String> ids = new LinkedHashSet<>();
        for (int minutes = WideOffsetZone.NARROWEST_WIDE_MINUTES; minutes <= WideOffsetZone.WIDEST_MINUTES;
                minutes++) {
            ids.add(WideOffsetZone.idOf(minutes));
            ids.add(WideOffsetZone.idOf(-minutes));
        }
        return ids;
    }

    @Override
    protected ZoneRules provideRules(final String zoneId, final boolean forCaching) {
        return AT_UTC;
    }

    @Override
    protected NavigableMap<String, ZoneRules> provideVersions(final String zoneId) {
        final NavigableMap<String, ZoneRules> versions = new TreeMap<>();
        versions.put("1", AT_UTC);
        return versions;
    }
}
