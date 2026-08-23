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

package jdk.jpackage.internal.util;

import static jdk.jpackage.internal.util.ExplodedPath.copy;
import static jdk.jpackage.internal.util.ExplodedPath.copySpec;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import jdk.jpackage.internal.util.ExplodedPath.Node;
import jdk.jpackage.test.JUnitUtils.ArrayConverter;
import jdk.jpackage.test.PathDeletionPreventer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;


class ExplodedPathTest {

    @Test
    void test_of_file(@TempDir Path workdir) throws IOException {

        var path = workdir.resolve("foo");

        Files.createFile(path);

        var exp = ExplodedPath.of(path);

        assertEquals(path, exp.root());
        assertEquals(1, exp.children().size());
        assertEquals(file(""), exp.children().iterator().next());
    }

    @Test
    void test_of_empty_dir(@TempDir Path path) throws IOException {

        var exp = ExplodedPath.of(path);

        assertEquals(path, exp.root());
        assertEquals(1, exp.children().size());
        assertEquals(dir(""), exp.children().iterator().next());
    }

    @Test
    void test_of_non_empty_dir(@TempDir Path workdir) throws IOException {

        initTestWorkload(workdir);

        var exp = ExplodedPath.of(workdir);

        assertEquals(workdir, exp.root());
        assertEquals(Set.of(
                file("a/b/c/foo"),
                file("a/b/foo"),
                dir(""),
                dir("a"),
                dir("a/b"),
                dir("a/b/c"),
                dir("a/b/empty-dir")), Set.copyOf(exp.children()));
    }

    @Test
    void test_getParent() {

        var exp = new ExplodedPath(Path.of("a/b"), List.of(file("foo"), file(""))).getParent();

        assertEquals(new ExplodedPath(Path.of("a"), List.of(file("b/foo"), file("b"))), exp);
    }

    @Test
    void test_Node_ctor_absolute() {
        assertThrowsExactly(IllegalArgumentException.class, () -> {
            new Node(Path.of("").toAbsolutePath(), true);
        });
    }

    @Test
    void test_copy_simple(@TempDir Path workdir) throws IOException {

        initTestWorkload(workdir);

        var dstDir = workdir.resolve("dst");

        var src = ExplodedPath.of(workdir);

        copy(List.of(copySpec(src, dstDir)));

        assertContentCopied(toUniquePaths(src), dstDir);
    }

    @Test
    void test_copy_merge(@TempDir Path workdir) throws IOException {

        initTestWorkload(workdir);

        var src = ExplodedPath.of(workdir);

        var dstDir = workdir.resolve("dst");
        initTestFile(dstDir.resolve("a/bar"));

        copy(List.of(copySpec(src, dstDir)));

        assertContentCopied(SetBuilder.build(toUniquePaths(src))
                .add(dstDir.resolve("a/bar"))
                .create(), dstDir);
    }

    @ParameterizedTest
    @CsvSource(delimiter = ':', value = {
        "true:,",
        "false:,",
        "true:REPLACE_EXISTING",
        "false:REPLACE_EXISTING",
    })
    void test_copy_with_multiple_src_file_overlap(
            boolean directOrder,
            @ConvertWith(ArrayConverter.class) StandardCopyOption copyOptions[],
            @TempDir Path workdir) throws IOException {

        initTestWorkload(workdir.resolve("1"));
        initTestFile(workdir.resolve("2/a/b/foo"));

        var dstDir = workdir.resolve("dst");

        var src = ExplodedPath.of(workdir.resolve("1"));

        var specs = List.of(
                copySpec(src, dstDir),
                copySpec(ExplodedPath.of(workdir.resolve("2")), dstDir)
        );

        copy(directOrder ? specs : specs.reversed(), copyOptions);

        assertContentCopied(SetBuilder.build(toUniquePaths(src)).mutate(builder -> {
            if (!directOrder) {
                builder.remove(workdir.resolve("1/a/b/foo"));
                builder.add(workdir.resolve("2/a/b/foo"));
            }
        }).create(), dstDir);
    }

    @ParameterizedTest
    @CsvSource(delimiter = ':', value = {
        "true:,",
        "false:,",
        "true:REPLACE_EXISTING",
        "false:REPLACE_EXISTING",
    })
    void test_copy_with_multiple_src_directory_overlap(
            boolean directOrder,
            @ConvertWith(ArrayConverter.class) StandardCopyOption copyOptions[],
            @TempDir Path workdir) throws IOException {

        // "a/b/foo" path is a file in the "1" exploded directory.
        // "a/b/foo" path is a directory in the "2" exploded directory.
        initTestWorkload(workdir.resolve("1"));
        initTestFile(workdir.resolve("2/a/b/foo/bar"));

        var dstDir = workdir.resolve("dst");

        var src = ExplodedPath.of(workdir.resolve("1"));

        var specs = List.of(
                copySpec(src, dstDir),
                copySpec(ExplodedPath.of(workdir.resolve("2")), dstDir)
        );

        copy(directOrder ? specs : specs.reversed(), copyOptions);

        assertContentCopied(SetBuilder.build(toUniquePaths(src)).mutate(builder -> {
            if (!directOrder) {
                builder.remove(workdir.resolve("1/a/b/foo"));
                builder.add(workdir.resolve("2/a/b/foo"));
                builder.add(workdir.resolve("2/a/b/foo/bar"));
            }
        }).create(), dstDir);
    }

    @ParameterizedTest
    @MethodSource
    void test_copy_with_existing_path_overlap(
            ExistingPathOverlap type,
            boolean replaceExisting,
            @TempDir Path workdir) throws IOException {

        initTestWorkload(workdir);
        final var src = ExplodedPath.of(workdir);
        final var dstContent = SetBuilder.build(toUniquePaths(src));

        final var dstDir = workdir.resolve("dst");
        switch (type) {
            case FILE_ON_FILE -> {
                initTestFile(dstDir.resolve("a/b/foo"));
                if (!replaceExisting) {
                    dstContent.remove(workdir.resolve("a/b/foo"));
                    dstContent.add(dstDir.resolve("a/b/foo"));
                }
            }
            case FILE_ON_DIR -> {
                // "a/b/foo" is a source file.
                // Create a non-empty "a/b/foo" subdirectory in the destination directory.
                initTestFile(dstDir.resolve("a/b/foo/bar"));
                initTestFile(dstDir.resolve("a/b/foo/buz"));
                if (!replaceExisting) {
                    dstContent.remove(workdir.resolve("a/b/foo"));
                    dstContent.add(dstDir.resolve("a/b/foo"));
                    dstContent.add(dstDir.resolve("a/b/foo/bar"));
                    dstContent.add(dstDir.resolve("a/b/foo/buz"));
                }
            }
            case DIR_ON_FILE -> {
                initTestFile(dstDir.resolve("a/b"));
                if (!replaceExisting) {
                    dstContent.remove(workdir.resolve("a/b"));
                    dstContent.remove(workdir.resolve("a/b/foo"));
                    dstContent.remove(workdir.resolve("a/b/c/foo"));
                    dstContent.remove(workdir.resolve("a/b/c"));
                    dstContent.remove(workdir.resolve("a/b/empty-dir"));
                    dstContent.add(dstDir.resolve("a/b"));
                }
            }
            case DIR_ON_DIR -> {
                initTestFile(dstDir.resolve("a/b/c/bar"));
                initTestFile(dstDir.resolve("a/b/c/d/far"));
                initTestFile(dstDir.resolve("a/b/buz"));

                dstContent.add(dstDir.resolve("a/b/c/bar"));
                dstContent.add(dstDir.resolve("a/b/c/d"));
                dstContent.add(dstDir.resolve("a/b/c/d/far"));
                dstContent.add(dstDir.resolve("a/b/buz"));
            }
        }

        if (replaceExisting) {
            copy(List.of(copySpec(src, dstDir)), StandardCopyOption.REPLACE_EXISTING);
        } else {
            copy(List.of(copySpec(src, dstDir)));
        }

        assertContentCopied(dstContent.create(), dstDir);
    }

    @SuppressWarnings({ "try" })
    @Test
    void test_copy_IOException(@TempDir Path workdir) throws IOException {

        initTestWorkload(workdir);

        var src = ExplodedPath.of(workdir);

        var dstDir = workdir.resolve("dst");

        var lockPath = dstDir.resolve("a/b/foo");

        Files.createDirectories(lockPath.getParent());

        try (final var lock = PathDeletionPreventer.DEFAULT.preventPathDeletion(lockPath)) {
            assertThrows(IOException.class, () -> {
                copy(List.of(copySpec(src, dstDir)), StandardCopyOption.REPLACE_EXISTING);
            });
        }

    }

    enum ExistingPathOverlap {
        FILE_ON_FILE,
        FILE_ON_DIR,
        DIR_ON_FILE,
        DIR_ON_DIR,
        ;
    }

    private static Collection<Arguments> test_copy_with_existing_path_overlap() {

        Collection<Arguments> testCases = new ArrayList<>();

        for (var replaceExisting : List.of(true, false)) {
            for (var type : ExistingPathOverlap.values()) {
                testCases.add(Arguments.of(type, replaceExisting));
            }
        }

        return testCases;
    }

    private static ExplodedPath.Node dir(Path path) {
        return new Node(path, true);
    }

    private static ExplodedPath.Node dir(String path) {
        return dir(Path.of(path));
    }

    private static ExplodedPath.Node file(Path path) {
        return new Node(path, false);
    }

    private static ExplodedPath.Node file(String path) {
        return file(Path.of(path));
    }

    private static void initTestWorkload(Path root) throws IOException {
        Files.createDirectories(root.resolve("a/b/c"));
        initTestFile(root.resolve("a/b/c/foo"));
        initTestFile(root.resolve("a/b/foo"));
        Files.createDirectories(root.resolve("a/b/empty-dir"));
    }

    private static void initTestFile(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, path.toString());
    }

    private static void assertFileCopied(Path src, Path dst) throws IOException {
        assertTrue(Files.isRegularFile(dst));
        assertEquals(Files.readAllLines(src), Files.readAllLines(dst));
    }

    private static void assertContentCopied(Set<Path> sources, Path dstDir) throws IOException {

        var dst = ExplodedPath.of(dstDir);

        assertEquals(sources.size(), dst.children().size());

        UnaryOperator<Path> srcToDst = path -> {
            return dstDir.resolve(dst.children().stream()
                    .map(Node::path)
                    .filter(v -> {
                        return path.endsWith(v) || v.equals(Path.of(""));
                    })
                    .sorted(Comparator.comparing(Path::getNameCount).reversed())
                    .findFirst().orElseThrow());
        };

        for (var srcPath : sources) {
            var dstPath = srcToDst.apply(srcPath);
            if (Files.isDirectory(srcPath)) {
                assertTrue(Files.isDirectory(dstPath));
            } else {
                assertFileCopied(srcPath, dstPath);
            }
        }
    }

    private static Set<Path> toUniquePaths(ExplodedPath exp) {
        return exp.children().stream()
                .map(Node::path)
                .map(exp.root()::resolve)
                .collect(Collectors.toMap(x -> x, x -> x)).keySet();
    }
}
