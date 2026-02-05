/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.jimage.BasicImageReader;
import jtreg.SkippedException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.joining;

/*
 * @test id=load
 * @summary Load all classes defined in JRT file system.
 * @library /test/lib
 * @modules java.base/jdk.internal.jimage
 * @run main/othervm --add-modules ALL-SYSTEM VerifyJimage
 */

/*
 * @test id=compare
 * @summary Compare an exploded directory of module classes with the system jimage.
 * @library /test/lib
 * @modules java.base/jdk.internal.jimage
 * @run main/othervm --add-modules ALL-SYSTEM -Djdk.test.threads=10 VerifyJimage ../../jdk/modules
 */
public abstract class VerifyJimage implements Runnable {
    private static final String MODULE_INFO = "module-info.class";

    public static void main(String... args) throws Exception {
        // Best practice is to read "test.jdk" in preference to "java.home".
        String testJdk = System.getProperty("test.jdk", System.getProperty("java.home"));
        Path jdkRoot = Path.of(testJdk);
        Path bootimagePath = jdkRoot.resolve("lib", "modules");
        if (Files.notExists(bootimagePath)) {
            throw new SkippedException("No boot image: " + bootimagePath);
        }

        FileSystem jrtFs = FileSystems.getFileSystem(URI.create("jrt:/"));
        Path modulesRoot = jrtFs.getPath("/").resolve("modules");
        List<String> modules;
        try (Stream<Path> moduleDirs = Files.list(modulesRoot)) {
            modules = moduleDirs.map(Path::getFileName).map(Object::toString).toList();
        }
        VerifyJimage verifier;
        if (args.length == 0) {
            verifier = new ClassLoadingVerifier(modules, modulesRoot);
        } else {
            Path pathArg = Path.of(args[0].replace("/", FileSystems.getDefault().getSeparator()));
            // The path argument may be relative.
            Path rootDir = jdkRoot.resolve(pathArg);
            if (!Files.isDirectory(rootDir)) {
                throw new SkippedException("No modules directory found: " + rootDir);
            }
            int maxThreads = Integer.getInteger("jdk.test.threads", 1);
            verifier = new DirectoryContentVerifier(modules, rootDir, maxThreads, bootimagePath);
        }
        verifier.verify();
    }

    final List<String> modules;
    // Count of items which have passed verification.
    final AtomicInteger verifiedCount = new AtomicInteger(0);
    // Error messages for verification failures.
    final Deque<String> failed = new ConcurrentLinkedDeque<>();

    private VerifyJimage(List<String> modules) {
        this.modules = modules;
    }

    void verify() {
        long start = System.nanoTime();
        run();
        long end = System.nanoTime();

        System.out.format("Verified %d entries: %d ms, %d errors%n",
                verifiedCount.get(),
                TimeUnit.NANOSECONDS.toMillis(end - start),
                failed.size());
        if (!failed.isEmpty()) {
            failed.forEach(System.err::println);
            throw new AssertionError("Test failed");
        }
    }

    private static final class DirectoryContentVerifier extends VerifyJimage {
        private final Path rootDir;
        private final ExecutorService pool;
        private final Path jimagePath;

        DirectoryContentVerifier(List<String> modules, Path rootDir, int maxThreads, Path jimagePath) {
            super(modules);
            this.rootDir = rootDir;
            this.pool = Executors.newFixedThreadPool(maxThreads);
            this.jimagePath = jimagePath;
        }

        @Override
        public void run() {
            System.out.println("Comparing jimage with: " + rootDir);
            try (BasicImageReader jimage = BasicImageReader.open(jimagePath)) {
                for (String modName : modules) {
                    Path modDir = rootDir.resolve(modName);
                    if (!Files.isDirectory(modDir)) {
                        failed.add("Missing module directory: " + modDir);
                    } else {
                        pool.execute(new ModuleResourceComparator(rootDir, modName, jimage));
                    }
                }
                pool.close();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        /**
         * Verifies the contents of the current runtime jimage file by comparing
         * entries with the on-disk resources in a given directory.
         */
        private class ModuleResourceComparator implements Runnable {
            private final Path rootDir;
            private final String moduleName;
            private final BasicImageReader jimage;
            private final String moduleInfoName;
            // Entries we expect to find in the jimage module.
            private final Set<String> moduleEntries;
            private final Set<String> handledEntries = new HashSet<>();

            public ModuleResourceComparator(Path rootDir, String moduleName, BasicImageReader jimage) {
                this.rootDir = rootDir;
                this.moduleName = moduleName;
                this.jimage = jimage;
                String moduleEntryPrefix = "/" + moduleName + "/";
                this.moduleInfoName = moduleEntryPrefix + MODULE_INFO;
                this.moduleEntries =
                        Arrays.stream(jimage.getEntryNames())
                                .filter(n -> n.startsWith(moduleEntryPrefix))
                                .filter(n -> !isJimageOnly(n))
                                .collect(Collectors.toSet());
            }

            @Override
            public void run() {
                try (Stream<Path> files = Files.walk(rootDir.resolve(moduleName))) {
                    files.filter(this::shouldVerify).forEach(this::compareEntry);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                moduleEntries.stream()
                        .filter(n -> !handledEntries.contains(n))
                        .sorted()
                        .forEach(n -> failed.add("Untested jimage entry: " + n));
            }

            void compareEntry(Path path) {
                String entryName = getEntryName(path);
                if (!moduleEntries.contains(entryName)) {
                    // Corresponds to an on-disk file which is not expected to
                    // be present in the jimage. This is normal and is skipped.
                    return;
                }
                // Mark valid entries as "handled" to track if we've seen them
                // (even if we don't test their content).
                if (!handledEntries.add(entryName)) {
                    failed.add("Duplicate entry name: " + entryName);
                    return;
                }
                if (isExpectedToDiffer(entryName)) {
                    return;
                }
                try {
                    int mismatch = Arrays.mismatch(
                            Files.readAllBytes(path),
                            jimage.getResource(entryName));
                    if (mismatch == -1) {
                        verifiedCount.incrementAndGet();
                    } else {
                        failed.add("Content diff (byte offset " + mismatch + "): " + entryName);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            /**
             * Predicate for files which correspond to entries in the jimage.
             *
             * <p>This should be a narrow test with minimal chance of
             * false-negative matching, primarily focusing on excluding build
             * artifacts.
             */
            boolean shouldVerify(Path path) {
                // Use the entry name because we know it uses the '/' separator.
                String entryName = getEntryName(path);
                return Files.isRegularFile(path)
                        && !entryName.contains("/_the.")
                        && !entryName.contains("/_element_lists.");
            }

            /**
             * Predicate for the limited subset of entries which are expected to
             * exist in the file system, but are not expected to have the same
             * content as the associated jimage entry. This is to handle files
             * which are modified/patched by jlink plugins.
             *
             * <p>This should be a narrow test with minimal chance of
             * false-positive matching.
             */
            private boolean isExpectedToDiffer(String entryName) {
                return entryName.equals(moduleInfoName)
                        || (entryName.startsWith("/java.base/java/lang/invoke/") && entryName.endsWith("$Holder.class"))
                        || entryName.equals("/java.base/jdk/internal/module/SystemModulesMap.class");
            }

            /**
             * Predicate for the limited subset of entries which are not expected
             * to exist in the file system, such as those created synthetically
             * by jlink plugins.
             *
             * <p>This should be a narrow test with minimal chance of
             * false-positive matching.
             */
            private boolean isJimageOnly(String entryName) {
                return entryName.startsWith("/java.base/jdk/internal/module/SystemModules$")
                        || entryName.startsWith("/java.base/java/lang/invoke/BoundMethodHandle$Species_");
            }

            private String getEntryName(Path path) {
                return StreamSupport.stream(rootDir.relativize(path).spliterator(), false)
                        .map(Object::toString).collect(joining("/", "/", ""));
            }
        }
    }

    /**
     * Verifies the contents of the current runtime jimage file by attempting to
     * load every available class based on the content of the JRT file system.
     */
    static final class ClassLoadingVerifier extends VerifyJimage {
        private static final String CLASS_SUFFIX = ".class";

        private final Path modulesRoot;

        ClassLoadingVerifier(List<String> modules, Path modulesRoot) {
            super(modules);
            this.modulesRoot = modulesRoot;
        }

        @Override
        public void run() {
            ClassLoader loader = ClassLoader.getSystemClassLoader();
            for (String modName : modules) {
                Path modDir = modulesRoot.resolve(modName);
                try (Stream<Path> files = Files.walk(modDir)) {
                    files.map(modDir::relativize)
                            .filter(ClassLoadingVerifier::isClassFile)
                            .map(ClassLoadingVerifier::toClassName)
                            .forEach(cn -> loadClass(cn, loader));
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }
        }

        private void loadClass(String cn, ClassLoader loader) {
            try {
                Class.forName(cn, false, loader);
                verifiedCount.incrementAndGet();
            } catch (VerifyError ve) {
                System.err.println("VerifyError for " + cn);
                failed.add("Class: " + cn + " not verified: " + ve.getMessage());
            } catch (ClassNotFoundException e) {
                failed.add("Class: " + cn + " not found");
            }
        }

        /**
         * Maps a module-relative JRT path of a class file to its corresponding
         * fully-qualified class name.
         */
        private static String toClassName(Path path) {
            // JRT uses '/' as the separator, and relative paths don't start with '/'.
            String s = path.toString();
            return s.substring(0, s.length() - CLASS_SUFFIX.length()).replace('/', '.');
        }

        /** Whether a module-relative JRT file system path is a class file. */
        private static boolean isClassFile(Path path) {
            String classFileName = path.getFileName().toString();
            return classFileName.endsWith(CLASS_SUFFIX)
                    && !classFileName.equals(MODULE_INFO);
        }
    }
}
