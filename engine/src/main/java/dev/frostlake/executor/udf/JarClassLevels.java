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

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LineMap;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import javax.tools.JavaCompiler;

/**
 * The refusal of an inline body whose classes reach a class of an imported jar compiled for a later Java than the
 * routine's: the account compiles the body with the compiler of its routine's level, which cannot read the newer class
 * file, and reports it at the body's first use of the class (live-verified):
 *
 * <pre>
 *   /InlineCode.java:1: error: cannot access Helper
 *   class J { public static int run(int x) { return Helper.thrice(x); } }
 *                                                   ^
 *     bad class file: /home/udf/0/h.jar(/Helper.class)
 *       class file has wrong version 61.0, should be 55.0
 *       Please remove or make sure it appears in the correct subdirectory of the classpath.
 *   1 error
 * </pre>
 *
 * <p>The engine's own compiler reads such a class file, so the body's compiled classes are read for the classes they
 * name, and each one a jar holds for its level. A class of a named package is left alone.
 */
final class JarClassLevels {

    /** A class file's major version is its Java level and this: Java 11 writes 55. */
    private static final int CLASS_FILE_VERSION_OF_JAVA_ZERO = 44;

    /** The constant pool tags, as the class file format numbers them. */
    private static final int UTF8 = 1;
    private static final int INTEGER = 3;
    private static final int FLOAT = 4;
    private static final int LONG = 5;
    private static final int DOUBLE = 6;
    private static final int CLASS = 7;
    private static final int STRING = 8;
    private static final int FIELD_REF = 9;
    private static final int METHOD_REF = 10;
    private static final int INTERFACE_METHOD_REF = 11;
    private static final int NAME_AND_TYPE = 12;
    private static final int METHOD_HANDLE = 15;
    private static final int METHOD_TYPE = 16;
    private static final int DYNAMIC = 17;
    private static final int INVOKE_DYNAMIC = 18;
    private static final int MODULE = 19;
    private static final int PACKAGE = 20;

    private JarClassLevels() {
    }

    /**
     * The refusal of a body compiled at a release whose classes reach a jar's class of a later level, or null.
     *
     * @param compiler the compiler that compiled the body
     * @param source   the body
     * @param classes  the body's compiled classes, by binary name
     * @param jars     the jars the routine imports, in class path order
     * @param release  the release the body was compiled at
     * @return the compiler's report as the account's prints it, or null when every class it reaches is readable
     */
    static String refusal(final JavaCompiler compiler, final String source, final Map<String, byte[]> classes,
                          final List<File> jars, final int release) {
        final int readable = release + CLASS_FILE_VERSION_OF_JAVA_ZERO;
        final Set<String> named = new LinkedHashSet<String>();
        for (final byte[] compiled : classes.values()) {
            named.addAll(classNames(compiled));
        }
        final List<String> newer = new ArrayList<String>();
        final List<int[]> found = new ArrayList<int[]>();
        for (final String name : named) {
            if (name.indexOf('/') >= 0 || classes.containsKey(name)) {
                continue;
            }
            final int[] version = versionInJars(name, jars);
            if (version != null && version[1] > readable) {
                newer.add(name);
                found.add(version);
            }
        }
        if (newer.isEmpty()) {
            return null;
        }
        final long[] first = new long[] {-1L, 0L, 0L};
        int chosen = -1;
        for (int i = 0; i < newer.size(); i++) {
            final long[] use = firstUse(compiler, source, simpleName(newer.get(i)));
            if (use != null && (first[0] < 0 || use[0] < first[0])) {
                System.arraycopy(use, 0, first, 0, first.length);
                chosen = i;
            }
        }
        if (chosen < 0) {
            return null;
        }
        final String name = newer.get(chosen);
        final int[] version = found.get(chosen);
        final String[] lines = source.split("\n", -1);
        final int line = (int) first[1];
        final String written = line - 1 < lines.length ? lines[line - 1].replace("\r", "") : "";
        return "/InlineCode.java:" + line + ": error: cannot access " + simpleName(name) + "\n"
            + written + "\n"
            + String.join("", Collections.nCopies((int) first[2] - 1, " ")) + "^\n"
            + "  bad class file: /home/udf/" + version[0] + "/" + jars.get(version[0]).getName() + "(/" + name
            + ".class)\n"
            + "    class file has wrong version " + version[1] + "." + version[2] + ", should be " + readable
            + ".0\n"
            + "    Please remove or make sure it appears in the correct subdirectory of the classpath.\n"
            + "1 error\n";
    }

    /** A class's name as the source spells it: its binary name past its package and before its nesting. */
    private static String simpleName(final String internal) {
        final String last = internal.substring(internal.lastIndexOf('/') + 1);
        final int nested = last.indexOf('$');
        return nested > 0 ? last.substring(0, nested) : last;
    }

    /**
     * The first jar that holds a class, with the class file's version.
     *
     * @return the jar's place in the class path, the major and the minor version; null when no jar holds it
     */
    private static int[] versionInJars(final String internal, final List<File> jars) {
        for (int i = 0; i < jars.size(); i++) {
            try (JarFile jar = new JarFile(jars.get(i))) {
                final ZipEntry entry = jar.getEntry(internal + ".class");
                if (entry == null) {
                    continue;
                }
                try (InputStream in = jar.getInputStream(entry)) {
                    final byte[] head = in.readNBytes(8);
                    if (head.length < 8) {
                        return null;
                    }
                    return new int[] {i, u2(head, 6), u2(head, 4)};
                }
            } catch (final IOException unreadable) {
                return null;
            }
        }
        return null;
    }

    /**
     * Where the source first names a class: the identifier's place in the text, its line and its column, both from
     * one; null when the source does not name it.
     */
    private static long[] firstUse(final JavaCompiler compiler, final String source, final String simple) {
        final JavacTask task = (JavacTask) compiler.getTask(new StringWriter(), null, null, null, null,
            Collections.singletonList(new InMemoryJavaFile(source)));
        final long[] first = new long[] {-1L, 0L, 0L};
        try {
            final SourcePositions positions = Trees.instance(task).getSourcePositions();
            for (final CompilationUnitTree unit : task.parse()) {
                final LineMap lines = unit.getLineMap();
                new TreeScanner<Void, Void>() {
                    @Override
                    public Void visitIdentifier(final IdentifierTree node, final Void unused) {
                        final long start = positions.getStartPosition(unit, node);
                        if (node.getName().contentEquals(simple) && start >= 0
                                && (first[0] < 0 || start < first[0])) {
                            first[0] = start;
                            first[1] = lines.getLineNumber(start);
                            first[2] = lines.getColumnNumber(start);
                        }
                        return super.visitIdentifier(node, unused);
                    }
                }.scan(unit, null);
            }
        } catch (final IOException unread) {
            return null;
        }
        return first[0] < 0 ? null : first;
    }

    /** The names of the classes a class file's constant pool holds, in their internal form. */
    private static List<String> classNames(final byte[] bytes) {
        final List<String> names = new ArrayList<String>();
        final int count = u2(bytes, 8);
        final String[] utf8 = new String[count];
        final int[] classIndexes = new int[count];
        int classes = 0;
        int at = 10;
        for (int index = 1; index < count; index++) {
            final int tag = bytes[at] & 0xFF;
            switch (tag) {
                case UTF8: {
                    final int length = u2(bytes, at + 1);
                    utf8[index] = new String(bytes, at + 3, length, StandardCharsets.UTF_8);
                    at += 3 + length;
                    break;
                }
                case CLASS:
                    classIndexes[classes++] = u2(bytes, at + 1);
                    at += 3;
                    break;
                case STRING:
                case METHOD_TYPE:
                case MODULE:
                case PACKAGE:
                    at += 3;
                    break;
                case METHOD_HANDLE:
                    at += 4;
                    break;
                case INTEGER:
                case FLOAT:
                case FIELD_REF:
                case METHOD_REF:
                case INTERFACE_METHOD_REF:
                case NAME_AND_TYPE:
                case DYNAMIC:
                case INVOKE_DYNAMIC:
                    at += 5;
                    break;
                case LONG:
                case DOUBLE:
                    at += 9;
                    index++;
                    break;
                default:
                    return names;
            }
        }
        for (int i = 0; i < classes; i++) {
            final String name = utf8[classIndexes[i]];
            if (name != null && !name.startsWith("[")) {
                names.add(name);
            }
        }
        return names;
    }

    private static int u2(final byte[] bytes, final int at) {
        return (bytes[at] & 0xFF) << 8 | bytes[at + 1] & 0xFF;
    }
}
