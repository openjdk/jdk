/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.cli.StandardOption;
import jdk.jpackage.internal.util.FileUtils;
import jdk.jpackage.internal.util.RetryExecutor;
import jdk.jpackage.internal.util.function.ThrowingFunction;
import jdk.jpackage.internal.util.function.ThrowingSupplier;
import jdk.jpackage.test.PathDeletionPreventer;
import jdk.jpackage.test.PathDeletionPreventer.ReadOnlyDirectoryPathDeletionPreventer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

public class TempDirectoryTest {

    @Test
    void test_directory_use(@TempDir Path tempDirPath) throws IOException {
        try (var tempDir = new TempDirectory(Optional.of(tempDirPath), RetryExecutorFactory.DEFAULT)) {
            assertEquals(tempDir.path(), tempDirPath);
            assertFalse(tempDir.deleteOnClose());

            var cmdline = Options.of(Map.of());
            assertSame(cmdline, tempDir.map(cmdline));
        }

        assertTrue(Files.isDirectory(tempDirPath));
    }

    @Test
    void test_directory_new() throws IOException {
        var tempDir = new TempDirectory(Optional.empty(), RetryExecutorFactory.DEFAULT);
        try (tempDir) {
            assertTrue(Files.isDirectory(tempDir.path()));
            assertTrue(tempDir.deleteOnClose());

            var cmdline = Options.of(Map.of());
            var mappedCmdline = tempDir.map(cmdline);
            assertEquals(tempDir.path(), StandardOption.TEMP_ROOT.getFrom(mappedCmdline));
        }

        assertFalse(Files.isDirectory(tempDir.path()));
    }

    @SuppressWarnings("try")
    @ParameterizedTest
    @MethodSource
    void test_close(CloseType closeType, @TempDir Path root) {
        Globals.main(ThrowingSupplier.toSupplier(() -> {
            test_close_impl(closeType, root);
            return 0;
        }));
    }

    @ParameterizedTest
    @MethodSource
    void test_DirectoryListing_listFilesAndEmptyDirectories(
            ListFilesAndEmptyDirectoriesTestSpec test, @TempDir Path root) throws IOException {
        test.run(root);
    }

    @Test
    void test_DirectoryListing_listFilesAndEmptyDirectories_negative(@TempDir Path root) throws IOException {
        assertThrowsExactly(IllegalArgumentException.class, () -> {
            TempDirectory.DirectoryListing.listFilesAndEmptyDirectories(root, -1);
        });
    }

    @ParameterizedTest
    @CsvSource({"100", "101", "1", "0"})
    void test_DirectoryListing_listFilesAndEmptyDirectories_nonexistent(int limit, @TempDir Path root) throws IOException {

        var path = root.resolve("foo");

        var listing = TempDirectory.DirectoryListing.listFilesAndEmptyDirectories(path, limit);
        assertTrue(listing.complete());

        List<Path> expected;
        if (limit == 0) {
            expected = List.of();
        } else {
            expected = List.of(path);
        }
        assertEquals(expected, listing.paths());
    }

    private static Stream<CloseType> test_close() {
        switch (PathDeletionPreventer.DEFAULT.implementation()) {
            case READ_ONLY_NON_EMPTY_DIRECTORY -> {
                return Stream.of(CloseType.values());
            }
            default -> {
                return Stream.of(CloseType.values())
                        .filter(Predicate.not(Set.of(
                                CloseType.FAIL_NO_LEFTOVER_FILES,
                                CloseType.FAIL_NO_LEFTOVER_FILES_VERBOSE)::contains));
            }
        }
    }

    @SuppressWarnings({ "try" })
    private void test_close_impl(CloseType closeType, Path root) throws IOException {
        var logSink = new StringWriter();
        var logPrintWriter = new PrintWriter(logSink, true);
        Globals.instance().loggerOutputStreams(logPrintWriter, logPrintWriter);
        if (closeType.isVerbose()) {
            Globals.instance().loggerVerbose();
        }

        final var workDir = root.resolve("workdir");
        Files.createDirectories(workDir);

        final Path leftoverPath;
        final TempDirectory tempDir;

        switch (closeType) {
            case FAIL_NO_LEFTOVER_FILES_VERBOSE, FAIL_NO_LEFTOVER_FILES -> {
                leftoverPath = workDir;
                tempDir = new TempDirectory(workDir, true, new RetryExecutorFactory() {
                    @Override
                    public <T, E extends Exception> RetryExecutor<T, E> retryExecutor(Class<? extends E> exceptionType) {
                        return new RetryExecutor<T, E>(exceptionType).setSleepFunction(_ -> {});
                    }
                });

                // Lock the parent directory of the work directory and don't create any files in the work directory.
                // This should trigger the error message about the failure to delete the empty work directory.
                try (var lockWorkDir = ReadOnlyDirectoryPathDeletionPreventer.INSTANCE.preventPathDeletion(workDir.getParent())) {
                    tempDir.close();
                }
            }
            default -> {
                Files.createFile(workDir.resolve("b"));

                final var lockedPath = workDir.resolve("a");
                switch (PathDeletionPreventer.DEFAULT.implementation()) {
                    case FILE_CHANNEL_LOCK -> {
                        Files.createFile(lockedPath);
                        leftoverPath = lockedPath;
                    }
                    case READ_ONLY_NON_EMPTY_DIRECTORY -> {
                        Files.createDirectories(lockedPath);
                        leftoverPath = lockedPath.resolve("a");
                        Files.createFile(leftoverPath);
                    }
                    default -> {
                        throw new AssertionError();
                    }
                }

                tempDir = new TempDirectory(workDir, true, new RetryExecutorFactory() {
                    @Override
                    public <T, E extends Exception> RetryExecutor<T, E> retryExecutor(Class<? extends E> exceptionType) {
                        var config = new RetryExecutorMock.Config(lockedPath, closeType.isSuccess());
                        return new RetryExecutorMock<>(exceptionType, config);
                    }
                });

                tempDir.close();
            }
        }

        logPrintWriter.flush();
        var logMessages = new BufferedReader(new StringReader(logSink.toString())).lines().toList();

        assertTrue(Files.isDirectory(root));

        if (closeType.isSuccess()) {
            assertFalse(Files.exists(tempDir.path()));
            assertEquals(List.of(), logMessages);
        } else {
            assertTrue(Files.isDirectory(tempDir.path()));
            assertTrue(Files.exists(leftoverPath));
            assertFalse(Files.exists(tempDir.path().resolve("b")));

            String errMessage;
            switch (closeType) {
                case FAIL_SOME_LEFTOVER_FILES_VERBOSE, FAIL_SOME_LEFTOVER_FILES -> {
                    errMessage = "warning.tempdir.cleanup-file-failed";
                }
                case FAIL_NO_LEFTOVER_FILES_VERBOSE, FAIL_NO_LEFTOVER_FILES -> {
                    errMessage = "warning.tempdir.cleanup-failed";
                }
                default -> {
                    throw new AssertionError();
                }
            }
            assertEquals(List.of(I18N.format(errMessage, leftoverPath)), logMessages.subList(0, 1));

            if (closeType.isVerbose()) {
                // Check the log contains a stacktrace
                assertNotEquals(1, logMessages.size());
            }
            FileUtils.deleteRecursive(tempDir.path());
        }
    }

    private static Collection<ListFilesAndEmptyDirectoriesTestSpec> test_DirectoryListing_listFilesAndEmptyDirectories() {

        var testCases = new ArrayList<ListFilesAndEmptyDirectoriesTestSpec>();

        Supplier<ListFilesAndEmptyDirectoriesTestSpec.Builder> builder = ListFilesAndEmptyDirectoriesTestSpec::build;

        Stream.of(
                builder.get().dirs("").complete(),
                builder.get().dirs("").limit(0),
                builder.get().dirs("foo").complete(),
                builder.get().dirs("foo").limit(0),
                builder.get().dirs("foo").limit(1).complete(),
                builder.get().dirs("foo").limit(2).complete(),
                builder.get().dirs("a/b/c").files("foo").files("b/b", "b/c").complete(),
                builder.get().dirs("a/b/c").files("foo").files("b/b", "b/c").limit(4).complete(),
                builder.get().dirs("a/b/c").files("foo").files("b/b", "b/c").limit(3)
        ).map(ListFilesAndEmptyDirectoriesTestSpec.Builder::create).forEach(testCases::add);

        if (!OperatingSystem.isWindows()) {
            Stream.of(
                    // A directory with the sibling symlink pointing to this directory
                    builder.get().dirs("foo").symlink("foo-symlink", "foo").complete(),
                    // A file with the sibling symlink pointing to this file
                    builder.get().symlink("foo-symlink", "foo").files("foo").complete(),
                    // A dangling symlink
                    builder.get().nonexistent("foo/bar/buz").symlink("dangling-symlink", "foo/bar/buz").complete()
            ).map(ListFilesAndEmptyDirectoriesTestSpec.Builder::create).forEach(testCases::add);
        }

        return testCases;
    }

    enum CloseType {
        SUCCEED,
        FAIL_SOME_LEFTOVER_FILES,
        FAIL_SOME_LEFTOVER_FILES_VERBOSE,
        FAIL_NO_LEFTOVER_FILES,
        FAIL_NO_LEFTOVER_FILES_VERBOSE,
        ;

        boolean isSuccess() {
            return this == SUCCEED;
        }

        boolean isVerbose() {
            return name().endsWith("_VERBOSE");
        }
    }

    private static final class RetryExecutorMock<T, E extends Exception> extends RetryExecutor<T, E> {

        RetryExecutorMock(Class<? extends E> exceptionType, Config config) {
            super(exceptionType);
            setSleepFunction(_ -> {});
            this.config = Objects.requireNonNull(config);
        }

        @SuppressWarnings({ "try", "unchecked" })
        @Override
        public RetryExecutor<T,E> setExecutable(ThrowingFunction<Context<RetryExecutor<T, E>>, T, E> v) {
            return super.setExecutable(context -> {
                if (context.isLastAttempt() && config.unlockOnLastAttempt()) {
                    return v.apply(context);
                } else {
                    try (var lock = PathDeletionPreventer.DEFAULT.preventPathDeletion(config.lockedPath())) {
                        return v.apply(context);
                    } catch (IOException ex) {
                        if (exceptionType().isInstance(ex)) {
                            throw (E)ex;
                        } else {
                            throw new AssertionError();
                        }
                    }
                }
            });
        };

        private final Config config;

        record Config(Path lockedPath, boolean unlockOnLastAttempt) {
            Config {
                Objects.requireNonNull(lockedPath);
            }
        }
    }

    sealed interface FileSpec extends Comparable<FileSpec> {
        Path path();
        Path create(Path root) throws IOException;
        public default int compareTo(FileSpec other) {
            return path().compareTo(other.path());
        }

        static File file(Path path) {
            return new File(path);
        }

        static Directory dir(Path path) {
            return new Directory(path);
        }

        static Nonexistent nonexistent(Path path) {
            return new Nonexistent(path);
        }

        static Symlink symlink(Path path, FileSpec target) {
            return new Symlink(path, target);
        }
    };

    record File(Path path) implements FileSpec {
        File {
            path = normalizePath(path);
            if (path.getNameCount() == 0) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public Path create(Path root) throws IOException {
            var resolvedPath = root.resolve(path);
            if (!Files.isRegularFile(resolvedPath)) {
                Files.createDirectories(resolvedPath.getParent());
                Files.createFile(resolvedPath);
            }
            return resolvedPath;
        }

        @Override
        public String toString() {
            return String.format("f:%s", path);
        }
    }

    record Nonexistent(Path path) implements FileSpec {
        Nonexistent {
            path = normalizePath(path);
            if (path.getNameCount() == 0) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public Path create(Path root) throws IOException {
            return root.resolve(path);
        }

        @Override
        public String toString() {
            return String.format("x:%s", path);
        }
    }

    record Symlink(Path path, FileSpec target) implements FileSpec {
        Symlink {
            path = normalizePath(path);
            if (path.getNameCount() == 0) {
                throw new IllegalArgumentException();
            }
            Objects.requireNonNull(target);
        }

        @Override
        public Path create(Path root) throws IOException {
            var resolvedPath = root.resolve(path);
            var targetPath = target.create(root);
            Files.createDirectories(resolvedPath.getParent());
            return Files.createSymbolicLink(resolvedPath, targetPath);
        }

        @Override
        public String toString() {
            return String.format("s:%s->%s", path, target);
        }
    }

    record Directory(Path path) implements FileSpec {
        Directory {
            path = normalizePath(path);
        }

        @Override
        public Path create(Path root) throws IOException {
            return Files.createDirectories(root.resolve(path));
        }

        @Override
        public String toString() {
            return String.format("d:%s", path);
        }
    }

    private static Path normalizePath(Path path) {
        path = path.normalize();
        if (path.isAbsolute()) {
            throw new IllegalArgumentException();
        }
        return path;
    }

    private record ListFilesAndEmptyDirectoriesTestSpec(Set<FileSpec> input, int limit, boolean complete) {

        ListFilesAndEmptyDirectoriesTestSpec {
            Objects.requireNonNull(input);

            if (!(input instanceof SortedSet)) {
                input = new TreeSet<>(input);
            }
        }

        static Builder build() {
            return new Builder();
        }

        static final class Builder {

            ListFilesAndEmptyDirectoriesTestSpec create() {
                return new ListFilesAndEmptyDirectoriesTestSpec(Set.copyOf(input), limit, complete);
            }

            Builder files(String... paths) {
                Stream.of(paths).map(Path::of).map(FileSpec::file).forEach(input::add);
                return this;
            }

            Builder dirs(String... paths) {
                Stream.of(paths).map(Path::of).map(FileSpec::dir).forEach(input::add);
                return this;
            }

            Builder nonexistent(String... paths) {
                Stream.of(paths).map(Path::of).map(FileSpec::nonexistent).forEach(input::add);
                return this;
            }

            Builder symlink(String path, String target) {
                Objects.requireNonNull(target);

                var targetSpec = input.stream().filter(v -> {
                    return v.path().equals(Path.of(target));
                }).findFirst();

                if (targetSpec.isEmpty()) {
                    var v = FileSpec.file(Path.of(target));
                    input.add(v);
                    targetSpec = Optional.ofNullable(v);
                }

                input.add(FileSpec.symlink(Path.of(path), targetSpec.get()));

                return this;
            }

            Builder limit(int v) {
                limit = v;
                return this;
            }

            Builder complete(boolean v) {
                complete = v;
                return this;
            }

            Builder complete() {
                return complete(true);
            }

            private final Set<FileSpec> input = new HashSet<>();
            private int limit = Integer.MAX_VALUE;
            private boolean complete;
        }

        void run(Path root) throws IOException {
            for (var v : input) {
                v.create(root);
            }

            for (var v : input) {
                Predicate<Path> validator;
                switch (v) {
                    case File _ -> {
                        validator = Files::isRegularFile;
                    }
                    case Directory _ -> {
                        validator = Files::isDirectory;
                    }
                    case Symlink _ -> {
                        validator = Files::isSymbolicLink;
                    }
                    case Nonexistent _ -> {
                        validator = Predicate.not(Files::exists);
                    }
                }
                assertTrue(validator.test(root.resolve(v.path())));
            }

            var listing = TempDirectory.DirectoryListing.listFilesAndEmptyDirectories(root, limit);
            assertEquals(complete, listing.complete());

            if (complete) {
                var actual = listing.paths().stream().peek(p -> {
                    assertTrue(p.startsWith(root));
                }).map(root::relativize).sorted().toList();
                var expected = input.stream()
                        .filter(Predicate.not(Nonexistent.class::isInstance))
                        .map(FileSpec::path)
                        .sorted()
                        .toList();
                assertEquals(expected, actual);
            } else {
                assertEquals(limit, listing.paths().size());
            }
        }

        @Override
        public String toString() {
            return String.format("%s; limit=%d; complete=%s", input, limit, complete);
        }
    }
}
