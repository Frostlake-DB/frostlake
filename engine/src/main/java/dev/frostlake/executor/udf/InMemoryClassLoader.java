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
import java.util.Map;

/**
 * Loads the classes one compilation of an inline source produced: the handler's class and every class beside
 * it — a nested or an inner class, an enum, an anonymous class, another top-level class of the same source.
 * Each is defined when the handler first needs it. The classes of the jars the routine imports are defined here too,
 * ahead of a class of the source of the same name, so a class of the source reaches a class of a jar its package
 * keeps to itself, as one package of the account's does (live-verified).
 */
public class InMemoryClassLoader extends URLClassLoader {

    private final Map<String, byte[]> classes;

    /** Whether the jars' classes are read ahead of the parent's, as {@link ChildFirstUdfClassLoader} reads them. */
    private final boolean jarsFirst;

    /**
     * @param classes the compilation's classes, by binary name
     */
    public InMemoryClassLoader(final Map<String, byte[]> classes) {
        super(new URL[0]);
        this.classes = classes;
        this.jarsFirst = false;
    }

    /**
     * @param classes the compilation's classes, by binary name
     * @param parent  the loader every other class comes from
     */
    public InMemoryClassLoader(final Map<String, byte[]> classes, final ClassLoader parent) {
        super(new URL[0], parent);
        this.classes = classes;
        this.jarsFirst = false;
    }

    /**
     * @param classes the compilation's classes, by binary name
     * @param jars    the jars the routine imports, in class path order
     * @param parent  the loader the engine's classes and every class neither reads come from
     */
    public InMemoryClassLoader(final Map<String, byte[]> classes, final URL[] jars, final ClassLoader parent) {
        super(jars, parent);
        this.classes = classes;
        this.jarsFirst = true;
    }

    @Override
    protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
        if (!jarsFirst) {
            return super.loadClass(name, resolve);
        }
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            // A jar's class is read ahead of one of the source's that shares its name (live-verified).
            if (loaded == null && !ChildFirstUdfClassLoader.isSharedWithEngine(name)) {
                try {
                    loaded = super.findClass(name);
                } catch (final ClassNotFoundException notInJars) {
                    loaded = null;
                }
            }
            if (loaded == null && classes.containsKey(name)) {
                final byte[] bytes = classes.get(name);
                loaded = defineClass(name, bytes, 0, bytes.length);
            }
            if (loaded == null) {
                loaded = getParent().loadClass(name);
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    /**
     * @param name a class's binary name
     * @return whether the compilation produced it
     */
    public boolean defines(final String name) {
        return classes.containsKey(name);
    }

    /**
     * @param name a class's binary name
     * @return the major version of the class file the compilation produced for it, 0 when it produced none
     */
    public int classFileVersion(final String name) {
        final byte[] bytes = classes.get(name);
        return bytes == null || bytes.length < 8 ? 0 : (bytes[6] & 0xFF) << 8 | bytes[7] & 0xFF;
    }

    @Override
    protected Class<?> findClass(final String name) throws ClassNotFoundException {
        final byte[] bytes = classes.get(name);
        if (bytes != null) {
            return defineClass(name, bytes, 0, bytes.length);
        }
        return super.findClass(name);
    }
}
