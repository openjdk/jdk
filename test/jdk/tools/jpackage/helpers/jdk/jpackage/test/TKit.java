/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.test;

import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.StandardCopyOption;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.toSet;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.util.function.ExceptionBox;
import jdk.jpackage.internal.util.function.ThrowingConsumer;
import jdk.jpackage.internal.util.function.ThrowingRunnable;
import jdk.jpackage.internal.util.function.ThrowingSupplier;

public final class TKit {

    public static final Path TEST_SRC_ROOT = Functional.identity(() -> {
        Path root = Path.of(System.getProperty("test.src"));

        for (int i = 0; i != 10; ++i) {
            if (root.resolve("apps").toFile().isDirectory()) {
                return root.normalize().toAbsolutePath();
            }
            root = root.resolve("..");
        }

        throw new RuntimeException("Failed to locate apps directory");
    }).get();

    public static final Path SRC_ROOT = Functional.identity(() -> {
        return TEST_SRC_ROOT.resolve("../../../../src/jdk.jpackage").normalize().toAbsolutePath();
    }).get();

    public static final String ICON_SUFFIX = Functional.identity(() -> {
        if (isOSX()) {
            return ".icns";
        }

        if (isLinux()) {
            return ".png";
        }

        if (isWindows()) {
            return ".ico";
        }

        throw throwUnknownPlatformError();
    }).get();

    static void withExtraLogStream(ThrowingRunnable action) {
        if (extraLogStream != null) {
            ThrowingRunnable.toRunnable(action).run();
        } else {
            try (PrintStream logStream = openLogStream()) {
                withExtraLogStream(action, logStream);
            }
        }
    }

    static void withExtraLogStream(ThrowingRunnable action, PrintStream logStream) {
        var oldExtraLogStream = extraLogStream;
        try {
            extraLogStream = logStream;
            ThrowingRunnable.toRunnable(action).run();
        } finally {
            extraLogStream = oldExtraLogStream;
        }
    }

    static void runTests(List<TestInstance> tests) {
        if (currentTest != null) {
            throw new IllegalStateException(
                    "Unexpected nested or concurrent Test.run() call");
        }

        withExtraLogStream(() -> {
            tests.stream().forEach(test -> {
                currentTest = test;
                try {
                    ignoreExceptions(test).run();
                } finally {
                    currentTest = null;
                    if (extraLogStream != null) {
                        extraLogStream.flush();
                    }
                }
            });
        });
    }

    static Runnable ignoreExceptions(ThrowingRunnable action) {
        return () -> {
            try {
                try {
                    action.run();
                } catch (Throwable ex) {
                    unbox(ex);
                }
            } catch (Throwable throwable) {
                printStackTrace(throwable);
            }
        };
    }

    static void unbox(Throwable throwable) throws Throwable {
        try {
            throw throwable;
        } catch (ExceptionBox | InvocationTargetException ex) {
            unbox(ex.getCause());
        }
    }

    public static Path workDir() {
        return currentTest.workDir();
    }

    static String getCurrentDefaultAppName() {
        // Construct app name from swapping and joining test base name
        // and test function name.
        // Say the test name is `FooTest.testBasic`. Then app name would be `BasicFooTest`.
        String appNamePrefix = currentTest.functionName();
        if (appNamePrefix != null && appNamePrefix.startsWith("test")) {
            appNamePrefix = appNamePrefix.substring("test".length());
        }
        return Stream.of(appNamePrefix, currentTest.baseName()).filter(
                v -> v != null && !v.isEmpty()).collect(Collectors.joining());
    }

    public static boolean isWindows() {
        return OperatingSystem.isWindows();
    }

    public static boolean isOSX() {
        return OperatingSystem.isMacOS();
    }

    public static boolean isLinux() {
        return OperatingSystem.isLinux();
    }

    public static boolean isLinuxAPT() {
        return isLinux() && Files.exists(Path.of("/usr/bin/apt-get"));
    }

    private static String addTimestamp(String msg) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
        Date time = new Date(System.currentTimeMillis());
        return String.format("[%s] %s", sdf.format(time), msg);
    }

    static void log(String v) {
        v = addTimestamp(v);
        System.out.println(v);
        if (extraLogStream != null) {
            extraLogStream.println(v);
        }
    }

    static Path removeRootFromAbsolutePath(Path v) {
        if (!v.isAbsolute()) {
            throw new IllegalArgumentException();
        }

        if (v.getNameCount() == 0) {
            return Path.of("");
        }
        return v.subpath(0, v.getNameCount());
    }

    public static void createTextFile(Path filename, Collection<String> lines) {
        createTextFile(filename, lines.stream());
    }

    public static void createTextFile(Path filename, Stream<String> lines) {
        trace(String.format("Create [%s] text file...",
                filename.toAbsolutePath().normalize()));
        ThrowingRunnable.toRunnable(() -> Files.write(filename,
                lines.peek(TKit::trace).collect(Collectors.toList()))).run();
        trace("Done");
    }

    public static void createPropertiesFile(Path propsFilename,
            Collection<Map.Entry<String, String>> props) {
        trace(String.format("Create [%s] properties file...",
                propsFilename.toAbsolutePath().normalize()));
        ThrowingRunnable.toRunnable(() -> Files.write(propsFilename,
                props.stream().map(e -> String.join("=", e.getKey(),
                e.getValue())).peek(TKit::trace).collect(Collectors.toList()))).run();
        trace("Done");
    }

    public static void traceFileContents(Path path, String label) throws IOException {
        assertFileExists(path);
        trace(String.format("Dump [%s] %s...", path, label));
        Files.readAllLines(path).forEach(TKit::trace);
        trace("Done");
    }

    public static void createPropertiesFile(Path propsFilename,
            Map.Entry<String, String>... props) {
        createPropertiesFile(propsFilename, List.of(props));
    }

    public static void createPropertiesFile(Path propsFilename,
            Map<String, String> props) {
        createPropertiesFile(propsFilename, props.entrySet());
    }

    public static void trace(String v) {
        if (TRACE) {
            log("TRACE: " + v);
        }
    }

    private static void traceAssert(String v) {
        if (TRACE_ASSERTS) {
            log("TRACE: " + v);
        }
    }

    public static void error(String v) {
        log("ERROR: " + v);
        throw new AssertionError(v);
    }

    static void assertAssert(boolean expectedSuccess, Runnable runnable) {
        try {
            runnable.run();
        } catch (AssertionError err) {
            if (expectedSuccess) {
                assertUnexpected("Assertion failed");
            } else {
                return;
            }
        }

        if (!expectedSuccess) {
            assertUnexpected("Assertion passed");
        }
    }

    private static final String TEMP_FILE_PREFIX = null;

    private static Path createUniqueFileName(String defaultName) {
        final String[] nameComponents;

        int separatorIdx = defaultName.lastIndexOf('.');
        final String baseName;
        if (separatorIdx == -1) {
            baseName = defaultName;
            nameComponents = new String[]{baseName};
        } else {
            baseName = defaultName.substring(0, separatorIdx);
            nameComponents = new String[]{baseName, defaultName.substring(
                separatorIdx + 1)};
        }

        final Path basedir = workDir();
        int i = 0;
        for (; i < 100; ++i) {
            Path path = basedir.resolve(String.join(".", nameComponents));
            if (!path.toFile().exists()) {
                return path;
            }
            // Don't use period (.) as a separator. OSX codesign fails to sign folders
            // with subfolders with names like "input.0".
            nameComponents[0] = String.format("%s-%d", baseName, i);
        }
        throw new IllegalStateException(String.format(
                "Failed to create unique file name from [%s] basename after %d attempts",
                baseName, i));
    }

    public static Path createTempDirectory(String role) throws IOException {
        if (role == null) {
            return Files.createTempDirectory(workDir(), TEMP_FILE_PREFIX);
        }
        return Files.createDirectory(createUniqueFileName(role));
    }

    public static Path createTempFile(Path templateFile) throws
            IOException {
        return Files.createFile(createUniqueFileName(
                templateFile.getFileName().toString()));
    }

    public static Path withTempFile(Path templateFile,
            ThrowingConsumer<Path> action) {
        final Path tempFile = ThrowingSupplier.toSupplier(() -> createTempFile(
                templateFile)).get();
        boolean keepIt = true;
        try {
            ThrowingConsumer.toConsumer(action).accept(tempFile);
            keepIt = false;
            return tempFile;
        } finally {
            if (tempFile != null && !keepIt) {
                ThrowingRunnable.toRunnable(() -> Files.deleteIfExists(tempFile)).run();
            }
        }
    }

    public static Path withTempDirectory(String role,
            ThrowingConsumer<Path> action) {
        final Path tempDir = ThrowingSupplier.toSupplier(
                () -> createTempDirectory(role)).get();
        boolean keepIt = true;
        try {
            ThrowingConsumer.toConsumer(action).accept(tempDir);
            keepIt = false;
            return tempDir;
        } finally {
            if (tempDir != null && tempDir.toFile().isDirectory() && !keepIt) {
                deleteDirectoryRecursive(tempDir, "");
            }
        }
    }

    private static class DirectoryCleaner implements Consumer<Path> {
        DirectoryCleaner traceMessage(String v) {
            msg = v;
            return this;
        }

        DirectoryCleaner contentsOnly(boolean v) {
            contentsOnly = v;
            return this;
        }

        @Override
        public void accept(Path root) {
            if (msg == null) {
                if (contentsOnly) {
                    msg = String.format("Cleaning [%s] directory recursively",
                            root);
                } else {
                    msg = String.format("Deleting [%s] directory recursively",
                            root);
                }
            }

            if (!msg.isEmpty()) {
                trace(msg);
            }

            List<Throwable> errors = new ArrayList<>();
            try {
                final List<Path> paths;
                if (contentsOnly) {
                    try (var pathStream = Files.list(root)) {
                        paths = pathStream.collect(Collectors.toList());
                    }
                } else {
                    paths = List.of(root);
                }

                for (var path : paths) {
                    try (var pathStream = Files.walk(path)) {
                        pathStream
                        .sorted(Comparator.reverseOrder())
                        .sequential()
                        .forEachOrdered(file -> {
                            try {
                                if (isWindows()) {
                                    Files.setAttribute(file, "dos:readonly", false);
                                }
                                Files.delete(file);
                            } catch (IOException ex) {
                                errors.add(ex);
                            }
                        });
                    }
                }

            } catch (IOException ex) {
                errors.add(ex);
            }
            errors.forEach(error -> trace(error.toString()));
        }

        private String msg;
        private boolean contentsOnly;
    }

    public static boolean deleteIfExists(Path path) throws IOException {
        if (isWindows()) {
            if (path.toFile().exists()) {
                Files.setAttribute(path, "dos:readonly", false);
            }
        }
        return Files.deleteIfExists(path);
    }

    /**
     * Deletes contents of the given directory recursively. Shortcut for
     * <code>deleteDirectoryContentsRecursive(path, null)</code>
     *
     * @param path path to directory to clean
     */
    public static void deleteDirectoryContentsRecursive(Path path) {
        deleteDirectoryContentsRecursive(path, null);
    }

    /**
     * Deletes contents of the given directory recursively. If <code>path<code> is not a
     * directory, request is silently ignored.
     *
     * @param path path to directory to clean
     * @param msg log message. If null, the default log message is used. If
     * empty string, no log message will be saved.
     */
    public static void deleteDirectoryContentsRecursive(Path path, String msg) {
        if (path.toFile().isDirectory()) {
            new DirectoryCleaner().contentsOnly(true).traceMessage(msg).accept(
                    path);
        }
    }

    /**
     * Deletes the given directory recursively. Shortcut for
     * <code>deleteDirectoryRecursive(path, null)</code>
     *
     * @param path path to directory to delete
     */
    public static void deleteDirectoryRecursive(Path path) {
        deleteDirectoryRecursive(path, null);
    }

    /**
     * Deletes the given directory recursively. If <code>path<code> is not a
     * directory, request is silently ignored.
     *
     * @param path path to directory to delete
     * @param msg log message. If null, the default log message is used. If
     * empty string, no log message will be saved.
     */
    public static void deleteDirectoryRecursive(Path path, String msg) {
        if (path.toFile().isDirectory()) {
            new DirectoryCleaner().traceMessage(msg).accept(path);
        }
    }

    public static RuntimeException throwUnknownPlatformError() {
        if (isWindows() || isLinux() || isOSX()) {
            throw new IllegalStateException(
                    "Platform is known. throwUnknownPlatformError() called by mistake");
        }
        throw new IllegalStateException("Unknown platform");
    }

    public static RuntimeException throwSkippedException(String reason) {
        trace("Skip the test: " + reason);
        RuntimeException ex = ThrowingSupplier.toSupplier(
                () -> (RuntimeException) Class.forName("jtreg.SkippedException").getConstructor(
                        String.class).newInstance(reason)).get();

        currentTest.notifySkipped(ex);
        throw ex;
    }

    public static Path createRelativePathCopy(final Path file) {
        Path fileCopy = ThrowingSupplier.toSupplier(() -> {
            Path localPath = createTempFile(file);
            Files.copy(file, localPath, StandardCopyOption.REPLACE_EXISTING);
            return localPath;
        }).get().toAbsolutePath().normalize();

        final Path basePath = Path.of(".").toAbsolutePath().normalize();
        try {
            return basePath.relativize(fileCopy);
        } catch (IllegalArgumentException ex) {
            // May happen on Windows: java.lang.IllegalArgumentException: 'other' has different root
            trace(String.format("Failed to relativize [%s] at [%s]", fileCopy,
                    basePath));
            printStackTrace(ex);
        }
        return file;
    }

    static void waitForFileCreated(Path fileToWaitFor,
            long timeoutSeconds) throws IOException {

        trace(String.format("Wait for file [%s] to be available",
                                                fileToWaitFor.toAbsolutePath()));

        WatchService ws = FileSystems.getDefault().newWatchService();

        Path watchDirectory = fileToWaitFor.toAbsolutePath().getParent();
        watchDirectory.register(ws, ENTRY_CREATE, ENTRY_MODIFY);

        long waitUntil = System.currentTimeMillis() + timeoutSeconds * 1000;
        for (;;) {
            long timeout = waitUntil - System.currentTimeMillis();
            assertTrue(timeout > 0, String.format(
                    "Check timeout value %d is positive", timeout));

            WatchKey key = ThrowingSupplier.toSupplier(() -> ws.poll(timeout,
                    TimeUnit.MILLISECONDS)).get();
            if (key == null) {
                if (fileToWaitFor.toFile().exists()) {
                    trace(String.format(
                            "File [%s] is available after poll timeout expired",
                            fileToWaitFor));
                    return;
                }
                assertUnexpected(String.format("Timeout expired", timeout));
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }
                Path contextPath = (Path) event.context();
                if (Files.isSameFile(watchDirectory.resolve(contextPath),
                        fileToWaitFor)) {
                    trace(String.format("File [%s] is available", fileToWaitFor));
                    return;
                }
            }

            if (!key.reset()) {
                assertUnexpected("Watch key invalidated");
            }
        }
    }

    static void printStackTrace(Throwable throwable) {
        if (extraLogStream != null) {
            throwable.printStackTrace(extraLogStream);
        }
        throwable.printStackTrace();
    }

    private static String concatMessages(String msg, String msg2) {
        if (msg2 != null && !msg2.isBlank()) {
            return msg + ": " + msg2;
        }
        return msg;
    }

    public static void assertEquals(long expected, long actual, String msg) {
        currentTest.notifyAssert();
        if (expected != actual) {
            error(concatMessages(String.format(
                    "Expected [%d]. Actual [%d]", expected, actual),
                    msg));
        }

        traceAssert(concatMessages(String.format("assertEquals(%d)", expected), msg));
    }

    public static void assertNotEquals(long expected, long actual, String msg) {
        currentTest.notifyAssert();
        if (expected == actual) {
            error(concatMessages(String.format("Unexpected [%d] value", actual),
                    msg));
        }

        traceAssert(concatMessages(String.format("assertNotEquals(%d, %d)", expected,
                actual), msg));
    }

    public static void assertEquals(String expected, String actual, String msg) {
        currentTest.notifyAssert();
        if ((actual != null && !actual.equals(expected))
                || (expected != null && !expected.equals(actual))) {
            error(concatMessages(String.format(
                    "Expected [%s]. Actual [%s]", expected, actual),
                    msg));
        }

        traceAssert(concatMessages(String.format("assertEquals(%s)", expected), msg));
    }

    public static void assertNotEquals(String expected, String actual, String msg) {
        currentTest.notifyAssert();
        if ((actual != null && !actual.equals(expected))
                || (expected != null && !expected.equals(actual))) {

            traceAssert(concatMessages(String.format("assertNotEquals(%s, %s)", expected,
                actual), msg));
            return;
        }

        error(concatMessages(String.format("Unexpected [%s] value", actual), msg));
    }

    public static void assertNull(Object value, String msg) {
        currentTest.notifyAssert();
        if (value != null) {
            error(concatMessages(String.format("Unexpected not null value [%s]",
                    value), msg));
        }

        traceAssert(concatMessages("assertNull()", msg));
    }

    public static void assertNotNull(Object value, String msg) {
        currentTest.notifyAssert();
        if (value == null) {
            error(concatMessages("Unexpected null value", msg));
        }

        traceAssert(concatMessages(String.format("assertNotNull(%s)", value), msg));
    }

    public static void assertTrue(boolean actual, String msg) {
        assertTrue(actual, msg, null);
    }

    public static void assertFalse(boolean actual, String msg) {
        assertFalse(actual, msg, null);
    }

    public static void assertTrue(boolean actual, String msg, Runnable onFail) {
        currentTest.notifyAssert();
        if (!actual) {
            if (onFail != null) {
                onFail.run();
            }
            error(concatMessages("Failed", msg));
        }

        traceAssert(concatMessages("assertTrue()", msg));
    }

    public static void assertFalse(boolean actual, String msg, Runnable onFail) {
        currentTest.notifyAssert();
        if (actual) {
            if (onFail != null) {
                onFail.run();
            }
            error(concatMessages("Failed", msg));
        }

        traceAssert(concatMessages("assertFalse()", msg));
    }

    public static void assertPathExists(Path path, boolean exists) {
        if (exists) {
            assertTrue(path.toFile().exists(), String.format(
                    "Check [%s] path exists", path));
        } else {
            assertTrue(!path.toFile().exists(), String.format(
                    "Check [%s] path doesn't exist", path));
        }
    }

    public static void assertDirectoryNotEmpty(Path path) {
        assertDirectoryExists(path, Optional.of(false));
    }

    public static void assertDirectoryEmpty(Path path) {
        assertDirectoryExists(path, Optional.of(true));
    }

    public static void assertDirectoryExists(Path path, Optional<Boolean> isEmptyCheck) {
        assertPathExists(path, true);
        boolean isDirectory = Files.isDirectory(path);
        if (isEmptyCheck.isEmpty() || !isDirectory) {
            assertTrue(isDirectory, String.format("Check [%s] is a directory", path));
        } else {
            ThrowingRunnable.toRunnable(() -> {
                try (var files = Files.list(path)) {
                    boolean actualIsEmpty = files.findFirst().isEmpty();
                    if (isEmptyCheck.get()) {
                        TKit.assertTrue(actualIsEmpty, String.format("Check [%s] is not an empty directory", path));
                    } else {
                        TKit.assertTrue(!actualIsEmpty, String.format("Check [%s] is an empty directory", path));
                    }
                }
            }).run();
        }
    }

    public static void assertDirectoryExists(Path path) {
        assertDirectoryExists(path, Optional.empty());
    }

    public static void assertSymbolicLinkExists(Path path) {
        assertPathExists(path, true);
        assertTrue(Files.isSymbolicLink(path), String.format
                ("Check [%s] is a symbolic link", path));
    }

    public static void assertFileExists(Path path) {
        assertPathExists(path, true);
        assertTrue(path.toFile().isFile(), String.format("Check [%s] is a file",
                path));
    }

    public static void assertExecutableFileExists(Path path) {
        assertFileExists(path);
        assertTrue(path.toFile().canExecute(), String.format(
                "Check [%s] file is executable", path));
    }

    public static void assertReadableFileExists(Path path) {
        assertFileExists(path);
        assertTrue(path.toFile().canRead(), String.format(
                "Check [%s] file is readable", path));
    }

    public static void assertUnexpected(String msg) {
        currentTest.notifyAssert();
        error(concatMessages("Unexpected", msg));
    }

    public static DirectoryContentVerifier assertDirectoryContent(Path dir) {
        return new DirectoryContentVerifier(dir);
    }

    public static final class DirectoryContentVerifier {
        public DirectoryContentVerifier(Path baseDir) {
            this(baseDir, ThrowingSupplier.toSupplier(() -> {
                try (var files = Files.list(baseDir)) {
                    return files.map(Path::getFileName).collect(toSet());
                }
            }).get());
        }

        public void match(Path ... expected) {
            DirectoryContentVerifier.this.match(Set.of(expected));
        }

        public void match(Set<Path> expected) {
            currentTest.notifyAssert();

            var comm = Comm.compare(content, expected);
            if (!comm.unique1().isEmpty() && !comm.unique2().isEmpty()) {
                error(String.format(
                        "assertDirectoryContentEquals(%s): Some expected %s. Unexpected %s. Missing %s",
                        baseDir, format(comm.common()), format(comm.unique1()), format(comm.unique2())));
            } else if (!comm.unique1().isEmpty()) {
                error(String.format(
                        "assertDirectoryContentEquals(%s): Expected %s. Unexpected %s",
                        baseDir, format(comm.common()), format(comm.unique1())));
            } else if (!comm.unique2().isEmpty()) {
                error(String.format(
                        "assertDirectoryContentEquals(%s): Some expected %s. Missing %s",
                        baseDir, format(comm.common()), format(comm.unique2())));
            } else {
                traceAssert(String.format(
                        "assertDirectoryContentEquals(%s): Expected %s",
                        baseDir, format(expected)));
            }
        }

        public void contains(Path ... expected) {
            contains(Set.of(expected));
        }

        public void contains(Set<Path> expected) {
            currentTest.notifyAssert();

            var comm = Comm.compare(content, expected);
            if (!comm.unique2().isEmpty()) {
                error(String.format(
                        "assertDirectoryContentContains(%s): Some expected %s. Missing %s",
                        baseDir, format(comm.common()), format(comm.unique2())));
            } else {
                traceAssert(String.format(
                        "assertDirectoryContentContains(%s): Expected %s",
                        baseDir, format(expected)));
            }
        }

        public DirectoryContentVerifier removeAll(Collection<Path> paths) {
            Set<Path> newContent = new HashSet<>(content);
            newContent.removeAll(paths);
            return new DirectoryContentVerifier(baseDir, newContent);
        }

        public DirectoryContentVerifier removeAll(Path ... paths) {
            return removeAll(List.of(paths));
        }

        public Set<Path> items() {
            return content;
        }

        private DirectoryContentVerifier(Path baseDir, Set<Path> contents) {
            this.baseDir = baseDir;
            this.content = contents;
        }

        private static String format(Set<Path> paths) {
            return Arrays.toString(
                    paths.stream().sorted().map(Path::toString).toArray(
                            String[]::new));
        }

        private final Path baseDir;
        private final Set<Path> content;
    }

    public static void assertStringListEquals(List<String> expected,
            List<String> actual, String msg) {
        currentTest.notifyAssert();

        traceAssert(concatMessages("assertStringListEquals()", msg));

        String idxFieldFormat = Functional.identity(() -> {
            int listSize = expected.size();
            int width = 0;
            while (listSize != 0) {
                listSize = listSize / 10;
                width++;
            }
            return "%" + width + "d";
        }).get();

        AtomicInteger counter = new AtomicInteger(0);
        Iterator<String> actualIt = actual.iterator();
        expected.stream().sequential().filter(expectedStr -> actualIt.hasNext()).forEach(expectedStr -> {
            int idx = counter.incrementAndGet();
            String actualStr = actualIt.next();

            if ((actualStr != null && !actualStr.equals(expectedStr))
                    || (expectedStr != null && !expectedStr.equals(actualStr))) {
                error(concatMessages(String.format(
                        "(" + idxFieldFormat + ") Expected [%s]. Actual [%s]",
                        idx, expectedStr, actualStr), msg));
            }

            traceAssert(String.format(
                    "assertStringListEquals(" + idxFieldFormat + ", %s)", idx,
                    expectedStr));
        });

        if (actual.size() > expected.size()) {
            // Actual string list is longer than expected
            error(concatMessages(String.format(
                    "Actual list is longer than expected by %d elements",
                    actual.size() - expected.size()), msg));
        }

        if (actual.size() < expected.size()) {
            // Actual string list is shorter than expected
            error(concatMessages(String.format(
                    "Actual list is shorter than expected by %d elements",
                    expected.size() - actual.size()), msg));
        }
    }

    /**
     * Creates a directory by creating all nonexistent parent directories first
     * just like java.nio.file.Files#createDirectories() and returns
     * java.io.Closeable that will delete all created nonexistent parent
     * directories.
     */
    public static Closeable createDirectories(Path dir) throws IOException {
        Objects.requireNonNull(dir);

        Collection<Path> dirsToDelete = new ArrayList<>();

        Path curDir = dir;
        while (!Files.exists(curDir)) {
            dirsToDelete.add(curDir);
            curDir = curDir.getParent();
        }
        Files.createDirectories(dir);

        return new Closeable() {
            @Override
            public void close() throws IOException {
                for (var dirToDelete : dirsToDelete) {
                    Files.deleteIfExists(dirToDelete);
                }
            }
        };
    }

    public static final class TextStreamVerifier {
        TextStreamVerifier(String value) {
            this.value = value;
            predicate(String::contains);
        }

        public TextStreamVerifier label(String v) {
            label = v;
            return this;
        }

        public TextStreamVerifier predicate(BiPredicate<String, String> v) {
            predicate = v;
            return this;
        }

        public TextStreamVerifier negate() {
            negate = true;
            return this;
        }

        public TextStreamVerifier andThen(Consumer<? super Stream<String>> anotherVerifier) {
            this.anotherVerifier = anotherVerifier;
            return this;
        }

        public TextStreamVerifier andThen(TextStreamVerifier anotherVerifier) {
            this.anotherVerifier = anotherVerifier::apply;
            return this;
        }

        public TextStreamVerifier orElseThrow(RuntimeException v) {
            return orElseThrow(() -> v);
        }

        public TextStreamVerifier orElseThrow(Supplier<RuntimeException> v) {
            createException = v;
            return this;
        }

        public void apply(Stream<String> lines) {
            final String matchedStr;

            lines = lines.dropWhile(line -> !predicate.test(line, value));
            if (anotherVerifier == null) {
                matchedStr = lines.findFirst().orElse(null);
            } else {
                var tail = lines.toList();
                if (tail.isEmpty()) {
                    matchedStr = null;
                } else {
                    matchedStr = tail.get(0);
                }
                lines = tail.stream().skip(1);
            }

            final String labelStr = Optional.ofNullable(label).orElse("output");
            if (negate) {
                String msg = String.format(
                        "Check %s doesn't contain [%s] string", labelStr, value);
                if (createException == null) {
                    assertNull(matchedStr, msg);
                } else {
                    trace(msg);
                    if (matchedStr != null) {
                        throw createException.get();
                    }
                }
            } else {
                String msg = String.format("Check %s contains [%s] string",
                        labelStr, value);
                if (createException == null) {
                    assertNotNull(matchedStr, msg);
                } else {
                    trace(msg);
                    if (matchedStr == null) {
                        throw createException.get();
                    }
                }
            }

            if (anotherVerifier != null) {
                anotherVerifier.accept(lines);
            }
        }

        private BiPredicate<String, String> predicate;
        private String label;
        private boolean negate;
        private Supplier<RuntimeException> createException;
        private Consumer<? super Stream<String>> anotherVerifier;
        private final String value;
    }

    public static TextStreamVerifier assertTextStream(String what) {
        return new TextStreamVerifier(what);
    }

    private static PrintStream openLogStream() {
        if (LOG_FILE == null) {
            return null;
        }

        return ThrowingSupplier.toSupplier(() -> new PrintStream(
                new FileOutputStream(LOG_FILE.toFile(), true))).get();
    }

    private static TestInstance currentTest;
    private static PrintStream extraLogStream;

    private static final boolean TRACE;
    private static final boolean TRACE_ASSERTS;

    static final boolean VERBOSE_JPACKAGE;
    static final boolean VERBOSE_TEST_SETUP;

    static String getConfigProperty(String propertyName) {
        return System.getProperty(getConfigPropertyName(propertyName));
    }

    static String getConfigPropertyName(String propertyName) {
        return "jpackage.test." + propertyName;
    }

    static List<String> tokenizeConfigPropertyAsList(String propertyName) {
        final String val = TKit.getConfigProperty(propertyName);
        if (val == null) {
            return null;
        }
        return Stream.of(val.toLowerCase().split(","))
                .map(String::strip)
                .filter(Predicate.not(String::isEmpty))
                .collect(Collectors.toList());
    }

    static Set<String> tokenizeConfigProperty(String propertyName) {
        List<String> tokens = tokenizeConfigPropertyAsList(propertyName);
        if (tokens == null) {
            return null;
        }
        return tokens.stream().collect(Collectors.toSet());
    }

    static final Path LOG_FILE = Functional.identity(() -> {
        String val = getConfigProperty("logfile");
        if (val == null) {
            return null;
        }
        return Path.of(val);
    }).get();

    static {
        Set<String> logOptions = tokenizeConfigProperty("suppress-logging");
        if (logOptions == null) {
            TRACE = true;
            TRACE_ASSERTS = true;
            VERBOSE_JPACKAGE = true;
            VERBOSE_TEST_SETUP = true;
        } else if (logOptions.contains("all")) {
            TRACE = false;
            TRACE_ASSERTS = false;
            VERBOSE_JPACKAGE = false;
            VERBOSE_TEST_SETUP = false;
        } else {
            Predicate<Set<String>> isNonOf = options -> {
                return Collections.disjoint(logOptions, options);
            };

            TRACE = isNonOf.test(Set.of("trace", "t"));
            TRACE_ASSERTS = isNonOf.test(Set.of("assert", "a"));
            VERBOSE_JPACKAGE = isNonOf.test(Set.of("jpackage", "jp"));
            VERBOSE_TEST_SETUP = isNonOf.test(Set.of("init", "i"));
        }
    }
}
