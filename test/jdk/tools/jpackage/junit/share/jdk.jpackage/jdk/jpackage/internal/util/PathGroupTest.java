/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal.util;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;


public class PathGroupTest {

    @Test
    public void testNullId() {
        assertThrowsExactly(NullPointerException.class, () -> new PathGroup(Map.of()).getPath(null));
    }

    @Test
    public void testEmptyPathGroup() {
        PathGroup pg = new PathGroup(Map.of());

        assertNull(pg.getPath("foo"));

        assertEquals(0, pg.paths().size());
        assertEquals(0, pg.roots().size());
    }

    @Test
    public void testRootsSinglePath() {
        final PathGroup pg = new PathGroup(Map.of("main", PATH_FOO));

        List<Path> paths = pg.paths();
        assertEquals(1, paths.size());
        assertEquals(PATH_FOO, paths.iterator().next());

        List<Path> roots = pg.roots();
        assertEquals(1, roots.size());
        assertEquals(PATH_FOO, roots.iterator().next());
    }

    @Test
    public void testDuplicatedRoots() {
        final PathGroup pg = new PathGroup(Map.of(
                "main", PATH_FOO,
                "another", PATH_FOO,
                "root", PATH_EMPTY));

        List<Path> paths = pg.paths();
        paths = paths.stream().sorted().toList();

        assertEquals(3, paths.size());
        assertEquals(PATH_EMPTY, paths.get(0));
        assertEquals(PATH_FOO, paths.get(1));
        assertEquals(PATH_FOO, paths.get(2));

        List<Path> roots = pg.roots();
        assertEquals(1, roots.size());
        assertEquals(PATH_EMPTY, roots.get(0));
    }

    @Test
    public void testRoots() {
        final PathGroup pg = new PathGroup(Map.of(
                1, Path.of("foo"),
                2, Path.of("foo", "bar"),
                3, Path.of("foo", "bar", "buz")));

        List<Path> paths = pg.paths();
        assertEquals(3, paths.size());
        assertTrue(paths.contains(Path.of("foo")));
        assertTrue(paths.contains(Path.of("foo", "bar")));
        assertTrue(paths.contains(Path.of("foo", "bar", "buz")));

        List<Path> roots = pg.roots();
        assertEquals(1, roots.size());
        assertEquals(Path.of("foo"), roots.get(0));
    }

    public record TestRootsSpec(Collection<Path> paths, Set<Path> expectedRoots) {
        public TestRootsSpec {
            paths.forEach(Objects::requireNonNull);
            expectedRoots.forEach(Objects::requireNonNull);
        }

        void test() {
            final var pg = new PathGroup(paths.stream().collect(toMap(x -> new Object(), x -> x)));
            final var actualRoots = pg.roots().stream().collect(toSet());
            assertEquals(expectedRoots, actualRoots);
        }

        static TestRootsSpec create(Collection<String> paths, Set<String> expectedRoots) {
            return new TestRootsSpec(paths.stream().map(Path::of).toList(), expectedRoots.stream().map(Path::of).collect(toSet()));
        }
    }

    @ParameterizedTest
    @MethodSource("testRootsValues")
    public void testRoots(TestRootsSpec testSpec) {
        testSpec.test();
    }

    private static Collection<TestRootsSpec> testRootsValues() {
        return List.of(
                TestRootsSpec.create(List.of(""), Set.of("")),
                TestRootsSpec.create(List.of("/", "a/b/c", "a/b", "a/b", "a/b/"), Set.of("a/b", "/"))
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testSizeInBytes(List<Path> paths, @TempDir Path tempDir) throws IOException {
        final var files = Set.of("AA.txt", "a/b/c/BB.txt", "a/b/c/DD.txt", "d/foo.txt").stream().map(Path::of).toList();

        int counter = 0;
        for (var file : files) {
            file = tempDir.resolve(file);
            Files.createDirectories(file.getParent());
            Files.writeString(file, "x".repeat(++counter * 100));
        }

        final var expectedSize = Stream.of(walkFiles(tempDir))
                .map(tempDir::resolve)
                .filter(Files::isRegularFile)
                .map(toFunction(Files::size))
                .mapToLong(Long::longValue).sum();

        final var pg = new PathGroup(paths.stream().collect(toMap(x -> new Object(), x -> x))).resolveAt(tempDir);

        assertEquals(expectedSize, pg.sizeInBytes());
    }

    private static Collection<List<Path>> testSizeInBytes() {
        return Stream.of(
                List.of(""),
                List.of("AA.txt", "a/b", "d", "non-existant", "non-existant/foo/bar"),
                List.of("AA.txt", "a/b", "d", "a/b/c/BB.txt", "a/b/c/BB.txt")
        ).map(v -> {
            return v.stream().map(Path::of).toList();
        }).toList();
    }

    @Test
    public void testResolveAt() {
        final PathGroup pg = new PathGroup(Map.of(
                0, PATH_FOO,
                1, PATH_BAR,
                2, PATH_EMPTY));

        final Path aPath = Path.of("a");

        final PathGroup pg2 = pg.resolveAt(aPath);
        assertNotEquals(pg, pg2);

        List<Path> paths = pg.paths();
        assertEquals(3, paths.size());
        assertTrue(paths.contains(PATH_EMPTY));
        assertTrue(paths.contains(PATH_FOO));
        assertTrue(paths.contains(PATH_BAR));
        assertEquals(PATH_EMPTY, pg.roots().get(0));

        paths = pg2.paths();
        assertEquals(3, paths.size());
        assertTrue(paths.contains(aPath.resolve(PATH_EMPTY)));
        assertTrue(paths.contains(aPath.resolve(PATH_FOO)));
        assertTrue(paths.contains(aPath.resolve(PATH_BAR)));
        assertEquals(aPath, pg2.roots().get(0));
    }

    enum TransformType { COPY, MOVE, HANDLER }

    private static Stream<Object[]> testTransform() {
        return Stream.of(TransformType.values()).flatMap(transform -> {
            return Stream.of(true, false).map(withExcludes -> {
                return new Object[]{withExcludes,transform};
            });
        });
    }

    @ParameterizedTest
    @MethodSource("testTransform")
    public void testTransform(boolean withExcludes, TransformType transform, @TempDir Path tempDir) throws IOException {

        final Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);

        final Path dstDir = tempDir.resolve("dst");
        Files.createDirectories(dstDir);

        final PathGroup pg = new PathGroup(Map.of(
                0, PATH_FOO,
                1, PATH_BAR,
                2, PATH_EMPTY,
                3, PATH_BAZ));

        Files.createDirectories(srcDir.resolve(PATH_FOO).resolve("a/b/c/d"));
        Files.createFile(srcDir.resolve(PATH_FOO).resolve("a/b/c/file1"));
        Files.createFile(srcDir.resolve(PATH_FOO).resolve("a/b/file2"));
        Files.createFile(srcDir.resolve(PATH_FOO).resolve("a/b/file3"));
        Files.createFile(srcDir.resolve(PATH_BAR));
        Files.createFile(srcDir.resolve(PATH_EMPTY).resolve("file4"));
        Files.createDirectories(srcDir.resolve(PATH_BAZ).resolve("1/2/3"));

        var dst = pg.resolveAt(dstDir);
        var src = pg.resolveAt(srcDir);
        if (withExcludes) {
            // Exclude from transformation.
            src.setPath(new Object(), srcDir.resolve(PATH_FOO).resolve("a/b/c"));
            src.setPath(new Object(), srcDir.resolve(PATH_EMPTY).resolve("file4"));
        }

        var srcFilesBeforeTransform = walkFiles(srcDir);

        if (transform == TransformType.HANDLER) {
            List<Map.Entry<Path, Path>> copyFile = new ArrayList<>();
            List<Path> createDirectory = new ArrayList<>();
            src.transform(dst, new PathGroup.TransformHandler() {
                @Override
                public void copyFile(Path src, Path dst) throws IOException {
                    copyFile.add(Map.entry(src, dst));
                }

                @Override
                public void createDirectory(Path dir) throws IOException {
                    createDirectory.add(dir);
                }
            });

            Consumer<Path> assertFile = path -> {
                var entry = Map.entry(srcDir.resolve(path), dstDir.resolve(path));
                assertTrue(copyFile.contains(entry));
            };

            Consumer<Path> assertDir = path -> {
                assertTrue(createDirectory.contains(dstDir.resolve(path)));
            };

            assertEquals(withExcludes ? 3 : 5, copyFile.size());
            assertEquals(withExcludes ? 8 : 10, createDirectory.size());

            assertFile.accept(PATH_FOO.resolve("a/b/file2"));
            assertFile.accept(PATH_FOO.resolve("a/b/file3"));
            assertFile.accept(PATH_BAR);
            assertDir.accept(PATH_FOO.resolve("a/b"));
            assertDir.accept(PATH_FOO.resolve("a"));
            assertDir.accept(PATH_FOO);
            assertDir.accept(PATH_BAZ);
            assertDir.accept(PATH_BAZ.resolve("1"));
            assertDir.accept(PATH_BAZ.resolve("1/2"));
            assertDir.accept(PATH_BAZ.resolve("1/2/3"));
            assertDir.accept(PATH_EMPTY);

            if (!withExcludes) {
                assertFile.accept(PATH_FOO.resolve("a/b/c/file1"));
                assertFile.accept(PATH_EMPTY.resolve("file4"));
                assertDir.accept(PATH_FOO.resolve("a/b/c/d"));
                assertDir.accept(PATH_FOO.resolve("a/b/c"));
            }

            assertArrayEquals(new Path[] { Path.of("") }, walkFiles(dstDir));
            return;
        }

        if (transform == TransformType.COPY) {
            src.copy(dst);
        } else if (transform == TransformType.MOVE) {
            src.move(dst);
        }

        final List<Path> excludedPaths;
        if (withExcludes) {
            excludedPaths = List.of(
                PATH_EMPTY.resolve("file4"),
                PATH_FOO.resolve("a/b/c")
            );
        } else {
            excludedPaths = Collections.emptyList();
        }
        UnaryOperator<Path[]> removeExcludes = paths -> {
            return Stream.of(paths)
                    .filter(path -> !excludedPaths.stream().anyMatch(path::startsWith))
                    .toArray(Path[]::new);
        };

        var dstFiles = walkFiles(dstDir);
        assertArrayEquals(removeExcludes.apply(srcFilesBeforeTransform), dstFiles);

        if (transform == TransformType.COPY) {
            assertArrayEquals(dstFiles, removeExcludes.apply(walkFiles(srcDir)));
        } else if (transform == TransformType.MOVE) {
            assertFalse(Files.exists(srcDir));
        }
    }

    private enum PathRole {
        DESKTOP,
        LINUX_APPLAUNCHER_LIB,
    }

    private static PathGroup linuxAppImage() {
        return new PathGroup(Map.of(
                PathRole.DESKTOP, Path.of("lib"),
                PathRole.LINUX_APPLAUNCHER_LIB, Path.of("lib/libapplauncher.so")
        ));
    }

    private static PathGroup linuxUsrTreePackageImage(Path prefix, String packageName) {
        final Path lib = prefix.resolve(Path.of("lib", packageName));
        return new PathGroup(Map.of(
                PathRole.DESKTOP, lib,
                PathRole.LINUX_APPLAUNCHER_LIB, lib.resolve("lib/libapplauncher.so")
        ));
    }

    @Test
    public void testLinuxLibapplauncher(@TempDir Path tempDir) throws IOException {
        final Path srcDir = tempDir.resolve("src");
        final Path dstDir = tempDir.resolve("dst");

        final Map<PathRole, List<Path>> files = Map.of(
                PathRole.LINUX_APPLAUNCHER_LIB, List.of(Path.of("")),
                PathRole.DESKTOP, List.of(Path.of("UsrUsrTreeTest.png"))
        );

        final var srcAppLayout = linuxAppImage().resolveAt(srcDir);
        final var dstAppLayout = linuxUsrTreePackageImage(dstDir.resolve("usr"), "foo");

        for (final var e : files.entrySet()) {
            final var pathRole = e.getKey();
            final var basedir = srcAppLayout.getPath(pathRole);
            for (var file : e.getValue().stream().map(basedir::resolve).toList()) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, "foo");
            }
        }

        srcAppLayout.copy(dstAppLayout);

        Stream.of(walkFiles(dstDir)).filter(p -> {
            return p.getFileName().equals(dstAppLayout.getPath(PathRole.LINUX_APPLAUNCHER_LIB).getFileName());
        }).reduce((x, y) -> {
            throw new AssertionError(String.format("Multiple libapplauncher: [%s], [%s]", x, y));
        });
    }


    public enum TestFileContent {
        A,
        B,
        C;

        void assertFileContent(Path path) {
            try {
                final var expected = name();
                final var actual = Files.readString(path);
                assertEquals(expected, actual);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        void createFile(Path path) {
            try {
                Files.createDirectories(path.getParent());
                Files.writeString(path, name());
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }

    public record PathGroupCopy(Path from, Path to) {
        public PathGroupCopy {
            Objects.requireNonNull(from);
        }

        public PathGroupCopy(Path from) {
            this(from, null);
        }
    }

    public record TestFile(Path path, TestFileContent content) {
        public TestFile {
            Objects.requireNonNull(content);
            if (path.isAbsolute()) {
                throw new IllegalArgumentException();
            }
        }

        void assertFileContent(Path basedir) {
            content.assertFileContent(basedir.resolve(path));
        }

        void create(Path basedir) {
            content.createFile(basedir.resolve(path));
        }
    }

    public record TestCopySpec(List<PathGroupCopy> pathGroupSpecs, Collection<TestFile> src, Collection<TestFile> dst) {
        public TestCopySpec {
            pathGroupSpecs.forEach(Objects::requireNonNull);
            src.forEach(Objects::requireNonNull);
            dst.forEach(Objects::requireNonNull);
        }

        PathGroup from() {
            return createPathGroup(PathGroupCopy::from);
        }

        PathGroup to() {
            return createPathGroup(PathGroupCopy::to);
        }

        void test(Path tempDir) throws IOException {
            final Path srcDir = tempDir.resolve("src");
            final Path dstDir = tempDir.resolve("dst");

            src.stream().forEach(testFile -> {
                testFile.create(srcDir);
            });

            from().resolveAt(srcDir).copy(to().resolveAt(dstDir));

            dst.stream().forEach(testFile -> {
                testFile.assertFileContent(dstDir);
            });

            Files.createDirectories(dstDir);
            final var actualFiles = Stream.of(walkFiles(dstDir)).filter(path -> {
                return Files.isRegularFile(dstDir.resolve(path));
            }).collect(toSet());
            final var expectedFiles = dst.stream().map(TestFile::path).collect(toSet());

            assertEquals(expectedFiles, actualFiles);
        }

        static Builder build() {
            return new Builder();
        }

        static class Builder {
            Builder addPath(String from, String to) {
                pathGroupSpecs.add(new PathGroupCopy(Path.of(from), Optional.ofNullable(to).map(Path::of).orElse(null)));
                return this;
            }

            Builder file(TestFileContent content, String srcPath, String ...dstPaths) {
                srcFiles.putAll(Map.of(Path.of(srcPath), content));
                dstFiles.putAll(Stream.of(dstPaths).collect(toMap(Path::of, x -> content)));
                return this;
            }

            TestCopySpec create() {
                return new TestCopySpec(pathGroupSpecs, convert(srcFiles), convert(dstFiles));
            }

            private static Collection<TestFile> convert(Map<Path, TestFileContent> map) {
                return map.entrySet().stream().map(e -> {
                    return new TestFile(e.getKey(), e.getValue());
                }).toList();
            }

            private final List<PathGroupCopy> pathGroupSpecs = new ArrayList<>();
            private final Map<Path, TestFileContent> srcFiles = new HashMap<>();
            private final Map<Path, TestFileContent> dstFiles = new HashMap<>();
        }

        private PathGroup createPathGroup(Function<PathGroupCopy, Path> keyFunc) {
            return new PathGroup(IntStream.range(0, pathGroupSpecs.size()).mapToObj(Integer::valueOf).map(i -> {
                return Optional.ofNullable(keyFunc.apply(pathGroupSpecs.get(i))).map(path -> {
                    return Map.entry(i, path);
                });
            }).filter(Optional::isPresent).map(Optional::orElseThrow).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }
    }

    @ParameterizedTest
    @MethodSource
    public void testCopy(TestCopySpec testSpec, @TempDir Path tempDir) throws IOException {
        testSpec.test(tempDir);
    }

    private static Collection<TestCopySpec> testCopy() {
        return List.of(
                TestCopySpec.build().create(),
                TestCopySpec.build()
                        .addPath("a/b/c", "a")
                        .file(TestFileContent.A, "a/b/c/j/k/foo", "a/j/k/foo")
                        .create(),
                TestCopySpec.build()
                        .addPath("a/b/c", "a")
                        .addPath("a/b/c", "d")
                        .file(TestFileContent.A, "a/b/c/j/k/foo", "a/j/k/foo", "d/j/k/foo")
                        .create(),
                TestCopySpec.build()
                        .addPath("a/b/c", "")
                        .addPath("a/b/c", "d")
                        .file(TestFileContent.A, "a/b/c/j/k/foo", "j/k/foo", "d/j/k/foo")
                        .create(),
                TestCopySpec.build()
                        .addPath("a/b/c", "cc")
                        .addPath("a/b/c", "dd")
                        .addPath("a/b/c/foo", null)
                        .file(TestFileContent.A, "a/b/c/foo")
                        .create(),
                TestCopySpec.build()
                        .addPath("a/b/c", "cc")
                        .addPath("a/b/c", "dd")
                        .addPath("a/b/c/foo", null)
                        .file(TestFileContent.A, "a/b/c/foo")
                        .file(TestFileContent.B, "a/b/c/bar", "cc/bar", "dd/bar")
                        .create(),
                TestCopySpec.build()
                        .addPath("a/b/c", "cc")
                        .addPath("a/b/c", "dd")
                        .addPath("a/b/c/bar", "dd/buz")
                        .addPath("a/b/c/foo", null)
                        .file(TestFileContent.A, "a/b/c/foo")
                        .file(TestFileContent.B, "a/b/c/bar", "dd/buz")
                        .create(),
                TestCopySpec.build()
                        .addPath("a/b/c", "cc")
                        .addPath("a/b/c", "dd")
                        .addPath("a/b/c/bar", "dd/buz")
                        .addPath("a/b/c/bar", "cc/rab")
                        .addPath("a/b/c/foo", null)
                        .file(TestFileContent.A, "a/b/c/foo")
                        .file(TestFileContent.B, "a/b/c/bar", "cc/rab", "dd/buz")
                        .create(),
                TestCopySpec.build()
                        .addPath("a/b", null)
                        .addPath("a/b/c", "cc")
                        .addPath("a/b/c", "dd")
                        .addPath("a/b/c/bar", "dd/buz")
                        .addPath("a/b/c/bar", "cc/rab")
                        .file(TestFileContent.A, "a/b/c/foo")
                        .file(TestFileContent.B, "a/b/c/bar")
                        .create()
        );
    }

    private static Path[] walkFiles(Path root) throws IOException {
        try (var files = Files.walk(root)) {
            return files.map(root::relativize).sorted().toArray(Path[]::new);
        }
    }

    private static final Path PATH_FOO = Path.of("foo");
    private static final Path PATH_BAR = Path.of("bar");
    private static final Path PATH_BAZ = Path.of("baz");
    private static final Path PATH_EMPTY = Path.of("");
}
