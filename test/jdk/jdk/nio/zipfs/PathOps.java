/*
 * Copyright (c) 2009, 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.ProviderMismatchException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * @test
 * @bug 8038500 8040059 8139956 8146754 8172921 8186142 8326487
 * @summary Tests path operations for zip provider.
 * @modules jdk.zipfs
 * @run junit PathOps
 * @run junit/othervm PathOps
 */
public class PathOps {

    // create empty JAR file, testing doesn't require any contents
    static Path emptyJar;

    @BeforeAll
    static void setup() throws IOException {
        emptyJar = Utils.createJarFile("empty.jar");
    }

    // Ensure NPEs are thrown for null inputs on Path ops
    @Test
    void nullPointerTest() throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            Path path = fs.getPath("foo");
            assertThrows(NullPointerException.class, () -> path.resolve((String) null));
            assertThrows(NullPointerException.class, () -> path.relativize(null));
            assertThrows(NullPointerException.class, () -> path.compareTo(null));
            assertThrows(NullPointerException.class, () -> path.startsWith((Path) null));
            assertThrows(NullPointerException.class, () -> path.endsWith((Path) null));
        }
    }

    // Ensure correct behavior when paths are provided by mismatched providers
    @Test
    void mismatchedProvidersTest() throws IOException {
        Path other = Paths.get("foo");
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            Path path = fs.getPath("foo");
            assertThrows(ProviderMismatchException.class, () -> path.compareTo(other));
            assertThrows(ProviderMismatchException.class, () -> path.resolve(other));
            assertThrows(ProviderMismatchException.class, () -> path.relativize(other));
            assertFalse(path.startsWith(other), "providerMismatched startsWith() returns true ");
            assertFalse(path.endsWith(other), "providerMismatched endsWith() returns true ");
        }
    }

    // Ensure correct construction of paths when given sequence of strings
    @ParameterizedTest
    @MethodSource
    void constructionTest(String first, String[] more, String expected) throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            string(fs.getPath(first, more), expected);
        }
    }

    static Stream<Arguments> constructionTest() {
        return Stream.of(
                Arguments.of("/", new String[]{}, "/"),
                Arguments.of("/", new String[]{""}, "/"),
                Arguments.of("/", new String[]{"foo"}, "/foo"),
                Arguments.of("/", new String[]{"/foo"}, "/foo"),
                Arguments.of("/", new String[]{"foo/"}, "/foo"),
                Arguments.of("foo", new String[]{"bar", "gus"}, "foo/bar/gus"),
                Arguments.of("", new String[]{},  ""),
                Arguments.of("", new String[]{"/"}, "/"),
                Arguments.of("", new String[]{"foo", "", "bar", "", "/gus"}, "foo/bar/gus")
        );
    }

    // Ensure proper root, parent, and name components
    @Test
    void allComponentsTest() throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            var path = fs.getPath("/a/b/c");
            root(path, "/");
            parent(path, "/a/b");
            name(path, "c");
        }
    }

    // Ensure correct name count for root only and empty name
    @ParameterizedTest
    @MethodSource
    void nameCountTest(String first, String root, String parent, String name, int nameCount) throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            var path = fs.getPath(first);
            root(path, root);
            parent(path, parent);
            name(path, name);
            checkPath(path);
            check(path.getNameCount(), Integer.toString(nameCount));
        }
    }

    static Stream<Arguments> nameCountTest() {
        return Stream.of(
                // root component only
                Arguments.of("/", "/", null, null, 0),
                // empty name
                Arguments.of("", null, null, "", 1)
        );
    }

    // Ensure correct parent and name behavior for no root and name only
    @ParameterizedTest
    @MethodSource
    void parentNameTest(String first, String root, String parent, String name) throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            var path = fs.getPath(first);
            root(path, root);
            parent(path, parent);
            name(path, name);
        }
    }

    static Stream<Arguments> parentNameTest() {
        return Stream.of(
                // no root component
                Arguments.of("a/b", null, "a", "b"),
                // name component only
                Arguments.of("foo", null, null, "foo")
        );
    }

    // Ensure correct (positive) `startsWith` behavior
    @ParameterizedTest
    @MethodSource
    void startsWithTest(String first, String prefix) throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            var path = fs.getPath(first);
            checkPath(path);
            Path s = fs.getPath(prefix);
            check(path.startsWith(s), true);
        }
    }

    static Stream<Arguments> startsWithTest() {
        return Stream.of(
                Arguments.of("", ""),
                Arguments.of("/", "/"),
                Arguments.of("foo", "foo"),
                Arguments.of("/foo", "/"),
                Arguments.of("/foo", "/foo"),
                Arguments.of("/foo/bar", "/"),
                Arguments.of("/foo/bar", "/foo"),
                Arguments.of("/foo/bar", "/foo/"),
                Arguments.of("/foo/bar", "/foo/bar"),
                Arguments.of("foo/bar", "foo"),
                Arguments.of("foo/bar", "foo/"),
                Arguments.of("foo/bar", "foo/bar")
        );
    }

    // Ensure correct (negative) `startsWith` behavior
    @ParameterizedTest
    @MethodSource
    void notStartsWithTest(String first, String prefix) throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            var path = fs.getPath(first);
            checkPath(path);
            Path s = fs.getPath(prefix);
            check(path.startsWith(s), false);
        }
    }

    static Stream<Arguments> notStartsWithTest() {
        return Stream.of(
                Arguments.of("", "/"),
                Arguments.of("/", "/foo"),
                Arguments.of("foo", "f"),
                Arguments.of("/foo", "/f"),
                Arguments.of("/foo", ""),
                Arguments.of("/foo/bar", "/f"),
                Arguments.of("/foo/bar", "foo"),
                Arguments.of("/foo/bar", "foo/bar"),
                Arguments.of("/foo/bar", ""),
                Arguments.of("foo/bar", "f"),
                Arguments.of("foo/bar", "/foo"),
                Arguments.of("foo/bar", "/foo/bar")
        );
    }

    // Ensure correct (positive) `endsWith` behavior
    @ParameterizedTest
    @MethodSource
    void endsWithTest(String first, String suffix) throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            var path = fs.getPath(first);
            checkPath(path);
            Path s = fs.getPath(suffix);
            check(path.endsWith(s), true);
        }
    }

    static Stream<Arguments> endsWithTest() {
        return Stream.of(
                Arguments.of("", ""),
                Arguments.of("/", "/"),
                Arguments.of("/foo", "foo"),
                Arguments.of("/foo","/foo"),
                Arguments.of("/foo/bar", "bar"),
                Arguments.of("/foo/bar", "foo/bar"),
                Arguments.of("/foo/bar", "foo/bar/"),
                Arguments.of("/foo/bar", "/foo/bar"),
                Arguments.of("/foo/bar/", "bar"),
                Arguments.of("/foo/bar/", "foo/bar"),
                Arguments.of("/foo/bar/", "foo/bar/"),
                Arguments.of("/foo/bar/", "/foo/bar"),
                Arguments.of("foo", "foo"),
                Arguments.of("foo/bar", "bar"),
                Arguments.of("foo/bar", "bar/"),
                Arguments.of("foo/bar", "foo/bar/"),
                Arguments.of("foo/bar", "foo/bar")
        );
    }

    // Ensure correct (negative) `endsWith` behavior
    @ParameterizedTest
    @MethodSource
    void notEndsWithTest(String first, String suffix) throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            var path = fs.getPath(first);
            checkPath(path);
            Path s = fs.getPath(suffix);
            check(path.endsWith(s), false);
        }
    }

    static Stream<Arguments> notEndsWithTest() {
        return Stream.of(
                Arguments.of("", "/"),
                Arguments.of("/", "foo"),
                Arguments.of("/", "/foo"),
                Arguments.of("/foo", "/"),
                Arguments.of("/foo/bar", "/bar"),
                Arguments.of("/foo/bar/", "/bar")
        );
    }

    // Ensure `getName` returns correct String at index
    @ParameterizedTest
    @MethodSource
    void elementTest(int index, String expected) throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            var path = fs.getPath("a/b/c");
            checkPath(path);
            check(path.getName(index), expected);
        }
    }

    static Stream<Arguments> elementTest() {
        return Stream.of(
                Arguments.of(0, "a"),
                Arguments.of(1, "b"),
                Arguments.of(2, "c")
        );
    }

    // Ensure expected behavior for absolute paths
    @ParameterizedTest
    @ValueSource(strings = {"/", "/tmp"} )
    void isAbsoluteTest(String first) throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            var path = fs.getPath(first);
            checkPath(path);
            check(path.isAbsolute(), true);
        }
    }

    // Ensure expected behavior for non-absolute paths
    @ParameterizedTest
    @ValueSource(strings = {"tmp", ""} )
    void notAbsoluteTest(String first) throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            var path = fs.getPath(first);
            checkPath(path);
            check(path.isAbsolute(), false);
        }
    }

    // Ensure correct append and replacement behavior for `resolve(String)`
    @ParameterizedTest
    @MethodSource
    void resolveTest(String first, String other, String expected) throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            resolve(fs.getPath(first), other, expected);
        }
    }

    static Stream<Arguments> resolveTest() {
        return Stream.of(
                Arguments.of("/tmp", "foo", "/tmp/foo"),
                Arguments.of("/tmp", "/foo", "/foo"),
                Arguments.of("/tmp", "", "/tmp"),
                Arguments.of("tmp", "foo", "tmp/foo"),
                Arguments.of("tmp", "/foo", "/foo"),
                Arguments.of("tmp", "", "tmp"),
                Arguments.of("", "", ""),
                Arguments.of("", "foo", "foo"),
                Arguments.of("", "/foo", "/foo"),
                Arguments.of("/", "", "/"),
                Arguments.of("/", "foo", "/foo"),
                Arguments.of("/", "/foo", "/foo"),
                Arguments.of("/", "/foo/", "/foo")
        );
    }

    // Ensure correct append and replacement behavior for `resolve(Path)`
    @ParameterizedTest
    @MethodSource
    void resolvePathTest(String first, String other, String expected) throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            var path = fs.getPath(first);
            checkPath(path);
            check(path.resolve(fs.getPath(other)), expected);
        }
    }

    static Stream<Arguments> resolvePathTest() {
        return Stream.of(
                Arguments.of("/tmp", "foo", "/tmp/foo"),
                Arguments.of("/tmp", "/foo", "/foo"),
                Arguments.of("/tmp", "", "/tmp"),
                Arguments.of("tmp", "foo", "tmp/foo"),
                Arguments.of("tmp", "/foo", "/foo"),
                Arguments.of("tmp", "", "tmp"),
                Arguments.of("", "", ""),
                Arguments.of("", "foo", "foo"),
                Arguments.of("", "/foo", "/foo"),
                Arguments.of("/", "", "/"),
                Arguments.of("/", "foo", "/foo"),
                Arguments.of("/",  "/foo", "/foo"),
                Arguments.of("/", "/foo/", "/foo")
        );
    }

    // Ensure correct behavior for `resolveSibling`
    @ParameterizedTest
    @MethodSource
    void resolveSiblingTest(String first, String other, String expected) throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            resolveSibling(fs.getPath(first), other, expected);
        }
    }

    static Stream<Arguments> resolveSiblingTest() {
        return Stream.of(
                Arguments.of("foo", "bar", "bar"),
                Arguments.of("foo", "/bar", "/bar"),
                Arguments.of("foo", "", ""),
                Arguments.of("foo/bar", "gus", "foo/gus"),
                Arguments.of("foo/bar", "/gus", "/gus"),
                Arguments.of("foo/bar", "", "foo"),
                Arguments.of("/foo", "gus", "/gus"),
                Arguments.of("/foo", "/gus", "/gus"),
                Arguments.of("/foo", "", "/"),
                Arguments.of("/foo/bar", "gus", "/foo/gus"),
                Arguments.of("/foo/bar", "/gus", "/gus"),
                Arguments.of("/foo/bar", "", "/foo")
        );
    }

    // Checking `resolve` and `resolveSibling` behavior for empty path
    @Test
    void resolveSiblingAndResolveTest() throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            var path = fs.getPath("");
            resolveSibling(path, "foo", "foo");
            resolveSibling(path, "/foo", "/foo");
            resolve(path, "", "");
        }
    }

    // Ensure correct behavior of `relativize`. i.e. Relative path should be
    // produced between two given paths
    @ParameterizedTest
    @MethodSource
    void relativizeTest(String first, String other, String expected) throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            var path = fs.getPath(first);
            checkPath(path);
            Path that = fs.getPath(other);
            check(path.relativize(that), expected);
        }
    }

    static Stream<Arguments> relativizeTest() {
        return Stream.of(
                Arguments.of("/a/b/c", "/a/b/c", ""),
                Arguments.of("/a/b/c", "/a/b/c/d/e", "d/e"),
                Arguments.of("/a/b/c", "/a/x", "../../x"),
                Arguments.of("/a/b/c", "/x", "../../../x"),
                Arguments.of("a/b/c", "a/b/c/d", "d"),
                Arguments.of("a/b/c", "a/x", "../../x"),
                Arguments.of("a/b/c", "x", "../../../x"),
                Arguments.of("a/b/c", "", "../../.."),
                Arguments.of("", "a", "a"),
                Arguments.of("", "a/b/c", "a/b/c"),
                Arguments.of("", "", ""),
                Arguments.of("/", "/a", "a"),
                Arguments.of("/", "/a/c", "a/c"),
                // 8146754
                Arguments.of("/tmp/path", "/tmp/path/a.txt", "a.txt"),
                Arguments.of("/tmp/path/", "/tmp/path/a.txt", "a.txt")
        );
    }

    // Ensure correct behavior of `normalize`. i.e. redundant elements should be removed.
    @ParameterizedTest
    @MethodSource
    void normalizeTest(String first, String expected) throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            var path = fs.getPath(first);
            checkPath(path);
            check(path.normalize(), expected);
        }
    }

    static Stream<Arguments> normalizeTest() {
        return Stream.of(
                Arguments.of("/", "/"),
                Arguments.of("foo", "foo"),
                Arguments.of("/foo", "/foo"),
                Arguments.of(".", ""),
                Arguments.of("..", ".."),
                Arguments.of("/..", "/"),
                Arguments.of("/../..", "/"),
                Arguments.of("foo/.", "foo"),
                Arguments.of("./foo", "foo"),
                Arguments.of("foo/..", ""),
                Arguments.of("../foo", "../foo"),
                Arguments.of("../../foo", "../../foo"),
                Arguments.of("foo/bar/..", "foo"),
                Arguments.of("foo/bar/gus/../..", "foo"),
                Arguments.of("/foo/bar/gus/../..", "/foo"),
                Arguments.of("/./.", "/"),
                Arguments.of("/.", "/"),
                Arguments.of("/./abc", "/abc")
        );
    }

    // Check IPE is thrown for invalid path Strings
    @ParameterizedTest
    @MethodSource
    void invalidTest(String first) throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            assertThrows(InvalidPathException.class, () -> fs.getPath(first));
        }
    }

    static Stream<String> invalidTest() {
        return Stream.of(
                "foo\u0000bar",
                "\u0000foo",
                "bar\u0000",
                "//foo\u0000bar",
                "//\u0000foo",
                "//bar\u0000"
        );
    }

    // Check that repeated forward slash is normalized correctly
    @Test
    void normalizationTest() throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            var path = fs.getPath("//foo//bar");
            string(path, "/foo/bar");
            root(path, "/");
            parent(path, "/foo");
            name(path, "bar");
        }
    }

    @Test // Check that identical paths refer to the same file
    void isSameFileTest() throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            var path = fs.getPath("/fileDoesNotExist");
            checkPath(path);
            check(Files.isSameFile(path, fs.getPath("/fileDoesNotExist")), true);
        }
    }

    // Regression test for 8139956: Ensure `relativize` of equivalent paths
    // produces an empty path -> `getNameCount` returns 1
    @Test
    void getNameCountTest() throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            int nc = fs.getPath("/").relativize(fs.getPath("/")).getNameCount();
            assertEquals(1, nc, "getNameCount of empty path failed");
        }
    }

    // Utilities for testing

    static void checkPath(Path path) {
        assertNotNull(path, "path is null");
    }

    static void check(Object result, String expected) {
        if (result == null) {
            if (expected == null) return;
        } else {
            // compare string representations
            if (expected != null && result.toString().equals(expected))
                return;
        }
        fail("Expected " + expected + " but instead got : " + result);
    }

    static void check(Object result, boolean expected) {
        check(result, Boolean.toString(expected));
    }

    static void root(Path path, String expected) {
        checkPath(path);
        check(path.getRoot(), expected);
    }

    static void parent(Path path, String expected) {
        checkPath(path);
        check(path.getParent(), expected);
    }

    static void name(Path path, String expected) {
        checkPath(path);
        check(path.getFileName(), expected);
    }

    static void resolve(Path path, String other, String expected) {
        checkPath(path);
        check(path.resolve(other), expected);
    }

    static void resolveSibling(Path path, String other, String expected) {
        checkPath(path);
        check(path.resolveSibling(other), expected);
    }

    static void string(Path path, String expected) {
        checkPath(path);
        check(path, expected);
    }
}
