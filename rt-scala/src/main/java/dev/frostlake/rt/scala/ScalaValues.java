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

package dev.frostlake.rt.scala;

import scala.collection.JavaConverters;

import java.util.Map;

/**
 * Scala's own types at a handler's edges, where the account takes them as their Java counterparts (live-verified):
 * an immutable {@code Map[String, String]} parameter holds an OBJECT's members as a {@code java.util.Map} one
 * does, and a Scala {@code Map} a handler hands back is read as a {@code java.util.Map}.
 */
public final class ScalaValues {

    private ScalaValues() {
    }

    /**
     * The handler's parameter classes as the Java converter reads them: an immutable Scala {@code Map} a
     * {@code java.util.Map}.
     *
     * @param declared the handler method's parameter classes
     * @return the classes to convert the arguments to
     */
    public static Class<?>[] javaClasses(final Class<?>[] declared) {
        final Class<?>[] classes = declared.clone();
        for (int i = 0; i < classes.length; i++) {
            if (classes[i] == scala.collection.immutable.Map.class) {
                classes[i] = Map.class;
            }
        }
        return classes;
    }

    /**
     * The converted arguments as the handler declares them: a {@code java.util.Map} for an immutable Scala
     * {@code Map} parameter becomes one, its members in the same order.
     *
     * @param declared  the handler method's parameter classes
     * @param converted the arguments as {@link #javaClasses} classes them
     * @return the values the method is invoked with
     */
    public static Object[] toScala(final Class<?>[] declared, final Object[] converted) {
        final Object[] values = converted.clone();
        for (int i = 0; i < values.length && i < declared.length; i++) {
            if (declared[i] == scala.collection.immutable.Map.class && values[i] instanceof Map) {
                values[i] = immutableMap((Map<?, ?>) values[i]);
            }
        }
        return values;
    }

    /**
     * What a handler hands back, a Scala {@code Map} read as a {@code java.util.Map}.
     *
     * @param answered the handler's value
     * @return the value the Java converter reads
     */
    public static Object toJava(final Object answered) {
        return answered instanceof scala.collection.Map
            ? JavaConverters.mapAsJavaMap((scala.collection.Map<?, ?>) answered) : answered;
    }

    private static scala.collection.immutable.Map<Object, Object> immutableMap(final Map<?, ?> members) {
        scala.collection.immutable.Map<Object, Object> map = scala.collection.immutable.Map$.MODULE$.empty();
        for (final Map.Entry<?, ?> member : members.entrySet()) {
            map = map.updated(member.getKey(), member.getValue());
        }
        return map;
    }
}
