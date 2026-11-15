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

package dev.frostlake.executor.udf;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * Classloader for IMPORTS handler jars that prefers the jar (and its stage-sibling dependency jars)
 * over the engine's classpath — the equivalent of Snowflake running a UDF against the dependency set
 * its PACKAGES clause supplies, not against whatever the warehouse happens to have loaded. Without
 * this, a handler built against one library version silently ran against the engine's copy: the
 * vendor plugin-output parsers (generated with ANTLR 4.11) picked up the engine's ANTLR 4.13 runtime,
 * warning on every call and mis-recovering on inputs their own runtime parses cleanly.
 *
 * <p>Classes the handler must SHARE with the engine stay parent-first: JDK classes, the engine's own
 * {@code dev.frostlake} types, the {@code com.snowflake} Snowpark bridge (a procedure's Session
 * argument must be the engine's class, not a copy from the jar), and SLF4J so logging binds once.
 */
public final class ChildFirstUdfClassLoader extends URLClassLoader {

    /** The Java level of a routine whose packages do not name Snowpark, or -1 where Snowpark is supplied. */
    private final int withheldLevel;

    public ChildFirstUdfClassLoader(final URL[] urls, final ClassLoader parent) {
        this(urls, parent, -1);
    }

    /**
     * A loader that supplies Snowpark's classes only to a routine whose packages name Snowpark: for any other, a
     * Snowpark class is one no jar holds, refused as the account's loader refuses it (see
     * {@link JavaHandlerFailure#unsupplied}).
     *
     * @param urls          the jars
     * @param parent        the loader every class the jars do not hold comes from
     * @param withheldLevel the routine's Java level where its packages do not name Snowpark, else -1
     */
    public ChildFirstUdfClassLoader(final URL[] urls, final ClassLoader parent, final int withheldLevel) {
        super(urls, parent);
        this.withheldLevel = withheldLevel;
    }

    @Override
    protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            final Class<?> alreadyLoaded = findLoadedClass(name);
            if (alreadyLoaded != null) {
                if (resolve) {
                    resolveClass(alreadyLoaded);
                }
                return alreadyLoaded;
            }
            if (withheldLevel >= 0 && isSnowpark(name)) {
                throw JavaHandlerFailure.unsupplied(name, withheldLevel, readFrom(new Throwable().getStackTrace()));
            }
            if (isSharedWithEngine(name)) {
                return super.loadClass(name, resolve);
            }
            try {
                final Class<?> fromJars = findClass(name);
                if (resolve) {
                    resolveClass(fromJars);
                }
                return fromJars;
            } catch (final ClassNotFoundException notInJars) {
                return super.loadClass(name, resolve);
            }
        }
    }

    /** Whether a class is one of Snowpark's, the Java API's or the Scala API's. */
    private static boolean isSnowpark(final String name) {
        return name.startsWith("com.snowflake.snowpark_java.") || name.startsWith("com.snowflake.snowpark.");
    }

    /** The frames below a class loader's own: those of the code that read the class. */
    private static StackTraceElement[] readFrom(final StackTraceElement[] trace) {
        int first = 0;
        while (first < trace.length && isLoaderFrame(trace[first])) {
            first++;
        }
        final StackTraceElement[] reading = new StackTraceElement[trace.length - first];
        System.arraycopy(trace, first, reading, 0, reading.length);
        return reading;
    }

    private static boolean isLoaderFrame(final StackTraceElement frame) {
        final String owner = frame.getClassName();
        return owner.equals(ChildFirstUdfClassLoader.class.getName()) || owner.equals("java.lang.ClassLoader")
            || owner.equals("java.net.URLClassLoader") || owner.equals("java.security.SecureClassLoader")
            || owner.startsWith("jdk.internal.loader.");
    }

    /**
     * Whether a class is one a handler shares with the engine, read from the engine's loader before any jar's.
     *
     * @param name the class's binary name
     * @return whether it is
     */
    static boolean isSharedWithEngine(final String name) {
        return name.startsWith("java.")
            || name.startsWith("javax.")
            || name.startsWith("jdk.")
            || name.startsWith("sun.")
            || name.startsWith("com.sun.")
            || name.startsWith("dev.frostlake.")
            || name.startsWith("com.snowflake.")
            || name.startsWith("org.slf4j.");
    }
}
