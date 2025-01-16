/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.InaccessibleObjectException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import jdk.internal.module.Modules;

/**
 * @test
 * @bug 8065552 8309532
 * @summary test that all public fields returned by getDeclaredFields() can
 *          be set accessible; this test
 *          loads all classes and get their declared fields
 *          and call setAccessible(false) followed by setAccessible(true);
 * @modules java.base/jdk.internal.module
 * @run main/othervm --add-modules=ALL-SYSTEM FieldSetAccessibleTest
 *
 * @author danielfuchs
 */
public class FieldSetAccessibleTest {

    static final List<String> cantread = new ArrayList<>();
    static final List<String> failed = new ArrayList<>();
    static final AtomicLong classCount = new AtomicLong();
    static final AtomicLong fieldCount = new AtomicLong();
    static long startIndex = 0;
    static long maxSize = Long.MAX_VALUE;
    static long maxIndex = Long.MAX_VALUE;
    static final ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();


    // Test that all fields for any given class can be made accessibles
    static void testSetFieldsAccessible(Class<?> c) {
        Module self = FieldSetAccessibleTest.class.getModule();
        Module target = c.getModule();
        String pn = c.getPackageName();
        boolean exported = self.canRead(target) && target.isExported(pn, self);
        for (Field f : c.getDeclaredFields()) {
            fieldCount.incrementAndGet();

            // setAccessible succeeds only if it's exported and the member
            // is public and of a public class, or it's opened
            // otherwise it would fail.
            boolean isPublic = Modifier.isPublic(f.getModifiers()) &&
                Modifier.isPublic(c.getModifiers());
            boolean access = (exported && isPublic) || target.isOpen(pn, self);
            try {
                f.setAccessible(false);
                f.setAccessible(true);
                if (!access) {
                    throw new RuntimeException(
                        String.format("Expected InaccessibleObjectException is not thrown "
                                      + "for field %s in class %s%n", f.getName(), c.getName()));
                }
            } catch (InaccessibleObjectException expected) {
                if (access) {
                    throw new RuntimeException(expected);
                }
            }
        }
    }

    // Performs a series of test on the given class.
    // At this time, we only call testSetFieldsAccessible(c)
    public static boolean test(Class<?> c, boolean addExports) {
        Module self = FieldSetAccessibleTest.class.getModule();
        Module target = c.getModule();
        String pn = c.getPackageName();
        boolean exported = self.canRead(target) && target.isExported(pn, self);
        if (addExports && !exported) {
            Modules.addExports(target, pn, self);
            exported = true;
        }

        classCount.incrementAndGet();

        // Call getDeclaredFields() and try to set their accessible flag.
        testSetFieldsAccessible(c);

        // add more tests here...

        return c == Class.class;
    }

    // Prints a summary at the end of the test.
    static void printSummary(long secs, long millis, long nanos) {
        System.out.println("Tested " + fieldCount.get() + " fields of "
                + classCount.get() + " classes in "
                + secs + "s " + millis + "ms " + nanos + "ns");
    }


    /**
     * @param args the command line arguments:
     *
     *     [startIndex (default=0)] [maxSize (default=Long.MAX_VALUE)]
     *
     * @throws java.lang.Exception if the test fails
     */
    public static void main(String[] args) throws Exception {
        if (args == null || args.length == 0) {
            args = new String[] {"0"};
        } else if (args.length > 2) {
            throw new RuntimeException("Expected at most one argument. Found "
                    + Arrays.asList(args));
        }
        try {
            if (args.length > 0) {
                startIndex = Long.parseLong(args[0]);
                if (startIndex < 0) {
                    throw new IllegalArgumentException("startIndex args[0]: "
                            + startIndex);
                }
            }
            if (args.length > 1) {
                maxSize = Long.parseLong(args[1]);
                if (maxSize <= 0) {
                    maxSize = Long.MAX_VALUE;
                }
                maxIndex = (Long.MAX_VALUE - startIndex) < maxSize
                        ? Long.MAX_VALUE : startIndex + maxSize;
            }
            test(listAllClassNames());
        } catch (OutOfMemoryError oome) {
            System.err.println(classCount.get());
            throw oome;
        }
    }

    static Iterable<String> listAllClassNames() {
        return new ClassNameJrtStreamBuilder();
    }

    static void test(Iterable<String> iterable) {
        final long start = System.nanoTime();
        boolean classFound = false;
        int index = 0;
        for (String s : iterable) {
            if (index == maxIndex) break;
            try {
                if (index < startIndex) continue;
                if (test(s, false)) {
                    classFound = true;
                }
            } finally {
                index++;
            }
        }

        // Re-test with all packages exported
        for (String s : iterable) {
            test(s, true);
        }

        classCount.set(classCount.get() / 2);
        fieldCount.set(fieldCount.get() / 2);
        long elapsed = System.nanoTime() - start;
        long secs = elapsed / 1000_000_000;
        long millis = (elapsed % 1000_000_000) / 1000_000;
        long nanos  = elapsed % 1000_000;
        System.out.println("Unreadable path elements: " + cantread);
        System.out.println("Failed path elements: " + failed);
        printSummary(secs, millis, nanos);

        if (!failed.isEmpty()) {
            throw new RuntimeException("Test failed for the following classes: " + failed);
        }
        if (!classFound && startIndex == 0 && index < maxIndex) {
            // this is just to verify that we have indeed parsed the java.base module
            throw  new RuntimeException("Test failed: Class.class not found...");
        }
        if (classCount.get() == 0 && startIndex == 0) {
            throw  new RuntimeException("Test failed: no class found?");
        }
    }

    static boolean test(String s, boolean addExports) {
        String clsName = s.replace('/', '.').substring(0, s.length() - 6);
        try {
            System.out.println("Loading " + clsName);
            final Class<?> c = Class.forName(
                    clsName,
                    false,
                    systemClassLoader);
            return test(c, addExports);
        } catch (VerifyError ve) {
            System.err.println("VerifyError for " + clsName);
            ve.printStackTrace(System.err);
            failed.add(s);
        } catch (Exception t) {
            t.printStackTrace(System.err);
            failed.add(s);
        } catch (NoClassDefFoundError e) {
            e.printStackTrace(System.err);
            failed.add(s);
        }
        return false;
    }

    static class ClassNameJrtStreamBuilder implements Iterable<String>{

        final FileSystem jrt;
        final Path root;
        final Set<String> modules;
        ClassNameJrtStreamBuilder() {
            jrt = FileSystems.getFileSystem(URI.create("jrt:/"));
            root = jrt.getPath("/modules");
            modules = systemModules();
        }

        @Override
        public Iterator<String> iterator() {
            try {
                return Files.walk(root)
                        .filter(p -> p.getNameCount() > 2)
                        .filter(p -> modules.contains(p.getName(1).toString()))
                        .map(p -> p.subpath(2, p.getNameCount()))
                        .map(p -> p.toString())
                        .filter(s -> s.endsWith(".class") && !s.endsWith("module-info.class"))
                    .iterator();
            } catch(IOException x) {
                throw new UncheckedIOException("Unable to walk \"/modules\"", x);
            }
        }

        /*
         * Filter JVMCI module and its transitive dependences
         */
        static Set<String> systemModules() {
            // Build module graph and inverse dependences
            Set<String> modules = new HashSet<>();
            Map<String, Set<String>> moduleToDeps = new HashMap<>();
            Map<String, Set<String>> inverseDeps = new HashMap<>();
            for (ModuleReference mref : ModuleFinder.ofSystem().findAll()) {
                var md = mref.descriptor();
                modules.add(md.name());
                Set<String> deps = md.requires().stream().map(ModuleDescriptor.Requires::name)
                                                .collect(Collectors.toSet());
                moduleToDeps.put(md.name(), deps);
                inverseDeps.put(md.name(), new HashSet<>());
            }

            // reverse edges
            moduleToDeps.keySet().forEach(u -> {
                moduleToDeps.get(u)
                            .forEach(v -> inverseDeps.get(v)
                                                     .add(u));
            });

            Set<String> mods = Set.of(
                    // All JVMCI packages other than jdk.vm.ci.services are dynamically
                    // exported to Graal
                    "jdk.graal.compiler", "jdk.graal.compiler.management"
            );
            // Filters all modules that directly or indirectly require Graal modules
            // as these are upgradeable and also provide APIs to add qualified exports dynamically
            Set<String> filters = mods.stream().flatMap(mn -> findDeps(mn, inverseDeps).stream())
                                      .collect(Collectors.toSet());
            System.out.println("Filtered modules: " + filters);
            return modules.stream()
                          .filter(mn -> !filters.contains(mn))
                          .collect(Collectors.toSet());
        }

        /*
         * Traverse the graph to find all the dependences from the given name.
         */
        static Set<String> findDeps(String name, Map<String, Set<String>> graph) {
            Set<String> visited = new HashSet<>();
            Deque<String> deque = new LinkedList<>();
            deque.add(name);
            String node;
            while (!deque.isEmpty()) {
                node = deque.pop();
                if (visited.contains(node))
                    continue;

                visited.add(node);
                Set<String> deps = graph.get(node);
                if (deps != null)
                    deque.addAll(deps);
            }
            return visited;
        }
    }
}
