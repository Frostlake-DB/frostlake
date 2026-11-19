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
package dev.frostlake.metastore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The account's objects of one kind that are kept as metadata alone - its resource monitors, its shares, its
 * failover groups - by canonical name.
 *
 * @param <T> the kind's model
 */
public final class AccountObjectRegistry<T extends SqlObject> {

    private final Map<String, T> objects = new ConcurrentHashMap<>();

    /** The object of that canonical name, or null. */
    public T find(final String name) {
        return objects.get(name);
    }

    /** Adds an object, or replaces the one of its name. */
    public void put(final T object) {
        objects.put(object.getName(), object);
    }

    /**
     * Removes the object of that name.
     *
     * @return the removed object, or null when there was none
     */
    public T remove(final String name) {
        return objects.remove(name);
    }

    /** Every object, by name. */
    public List<T> all() {
        final List<T> out = new ArrayList<>(objects.values());
        out.sort(new Comparator<T>() {
            @Override
            public int compare(final T a, final T b) {
                return a.getName().compareTo(b.getName());
            }
        });
        return out;
    }
}
