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
import java.nio.file.FileSystem;
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

    @Test
    void mismatchedProvidersTest() throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            Path path = fs.getPath("foo");
            Path other = Paths.get("foo");
            assertThrows(ProviderMismatchException.class, () -> path.compareTo(other));
            assertThrows(ProviderMismatchException.class, () -> path.resolve(other));
            assertThrows(ProviderMismatchException.class, () -> path.relativize(other));
            assertFalse(path.startsWith(other), "providerMismatched startsWith() returns true ");
            assertFalse(path.endsWith(other), "providerMismatched endsWith() returns true ");
        }
    }

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

    @Test
    void allComponentsTest() throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            var path = fs.getPath("/a/b/c");
            root(path, "/");
            parent(path, "/a/b");
            name(path, "c");
        }
    }

    @ParameterizedTest
    @MethodSource
    void nameCountTest(String first, String root, String parent, String name, int nameCount) throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            var path = fs.getPath(first);
            root(path, root);
            parent(path, parent);
            name(path, name);
            nameCount(path, nameCount);
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

    @ParameterizedTest
    @MethodSource
    void startsWithTest(String first, String prefix) throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            starts(fs.getPath(first), prefix, fs);
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

    @ParameterizedTest
    @MethodSource
    void notStartsWithTest(String first, String prefix) throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            notStarts(fs.getPath(first), prefix, fs);
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

    @ParameterizedTest
    @MethodSource
    void endsWithTest(String first, String suffix) throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            ends(fs.getPath(first), suffix, fs);
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

    @ParameterizedTest
    @MethodSource
    void notEndsWithTest(String first, String suffix) throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            notEnds(fs.getPath(first), suffix, fs);
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

    @ParameterizedTest
    @MethodSource
    void elementTest(int index, String expected) throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            element(fs.getPath("a/b/c"), index, expected);
        }
    }

    static Stream<Arguments> elementTest() {
        return Stream.of(
                Arguments.of(0, "a"),
                Arguments.of(1, "b"),
                Arguments.of(2, "c")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/tmp"} )
    void isAbsoluteTest(String first) throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            absolute(fs.getPath(first));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"tmp", ""} )
    void notAbsoluteTest(String first) throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            notAbsolute(fs.getPath(first));
        }
    }

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

    @ParameterizedTest
    @MethodSource
    void resolvePathTest(String first, String other, String expected) throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            resolvePath(fs.getPath(first), other, expected, fs);
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

    @Test
    void resolveSiblingAndResolveTest() throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            var path = fs.getPath("");
            resolveSibling(path, "foo", "foo");
            resolveSibling(path, "/foo", "/foo");
            resolve(path, "", "");
        }
    }

    @ParameterizedTest
    @MethodSource
    void relativizeTest(String first, String other, String expected) throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            relativize(fs.getPath(first), other, expected, fs);
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

    @ParameterizedTest
    @MethodSource
    void normalizeTest(String first, String expected) throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            normalize(fs.getPath(first), expected);
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

    @Test
    void isSameFileTest() throws IOException {
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            isSameFile(fs.getPath("/fileDoesNotExist"), "/fileDoesNotExist", fs);
        }
    }

    @Test
    void getNameCountTest() throws IOException {
        // 8139956
        try (var fs = FileSystems.newFileSystem(emptyJar)) {
            System.out.println("check getNameCount");
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

    static void check(Object result, int expected) {
        check(result, Integer.toString(expected));
    }

    static void root(Path path, String expected) {
        System.out.println("check root");
        checkPath(path);
        check(path.getRoot(), expected);
    }

    static void parent(Path path, String expected) {
        System.out.println("check parent");
        checkPath(path);
        check(path.getParent(), expected);
    }

    static void name(Path path, String expected) {
        System.out.println("check name");
        checkPath(path);
        check(path.getFileName(), expected);
    }

    static void nameCount(Path path, int expected) {
        System.out.println("check nameCount");
        checkPath(path);
        check(path.getNameCount(), expected);
    }

    static void element(Path path, int index, String expected) {
        System.out.format("check element %d\n", index);
        checkPath(path);
        check(path.getName(index), expected);
    }

    static void subpath(Path path, int startIndex, int endIndex, String expected) {
        System.out.format("test subpath(%d,%d)\n", startIndex, endIndex);
        checkPath(path);
        check(path.subpath(startIndex, endIndex), expected);
    }

    static void starts(Path path, String prefix, FileSystem fs) {
        System.out.format("test startsWith with %s\n", prefix);
        checkPath(path);
        Path s = fs.getPath(prefix);
        check(path.startsWith(s), true);
    }

    static void notStarts(Path path, String prefix, FileSystem fs) {
        System.out.format("test not startsWith with %s\n", prefix);
        checkPath(path);
        Path s = fs.getPath(prefix);
        check(path.startsWith(s), false);
    }

    static void ends(Path path, String suffix, FileSystem fs) {
        System.out.format("test endsWith %s\n", suffix);
        checkPath(path);
        Path s = fs.getPath(suffix);
        check(path.endsWith(s), true);
    }

    static void notEnds(Path path, String suffix, FileSystem fs) {
        System.out.format("test not endsWith %s\n", suffix);
        checkPath(path);
        Path s = fs.getPath(suffix);
        check(path.endsWith(s), false);
    }

    static void absolute(Path path) {
        System.out.println("check path is absolute");
        checkPath(path);
        check(path.isAbsolute(), true);
    }

    static void notAbsolute(Path path) {
        System.out.println("check path is not absolute");
        checkPath(path);
        check(path.isAbsolute(), false);
    }

    static void resolve(Path path, String other, String expected) {
        System.out.format("test resolve %s\n", other);
        checkPath(path);
        check(path.resolve(other), expected);
    }

    static void resolvePath(Path path, String other, String expected, FileSystem fs) {
        System.out.format("test resolve %s\n", other);
        checkPath(path);
        check(path.resolve(fs.getPath(other)), expected);
    }

    static void resolveSibling(Path path, String other, String expected) {
        System.out.format("test resolveSibling %s\n", other);
        checkPath(path);
        check(path.resolveSibling(other), expected);

    }

    static void relativize(Path path, String other, String expected, FileSystem fs) {
        System.out.format("test relativize %s\n", other);
        checkPath(path);
        Path that = fs.getPath(other);
        check(path.relativize(that), expected);
    }

    static void normalize(Path path, String expected) {
        System.out.println("check normalized path");
        checkPath(path);
        check(path.normalize(), expected);

    }

    static void string(Path path, String expected) {
        System.out.println("check string representation");
        checkPath(path);
        check(path, expected);
    }

    static void isSameFile(Path path, String target, FileSystem fs) {
        try {
            System.out.println("check two paths are same");
            checkPath(path);
            check(Files.isSameFile(path, fs.getPath(target)), true);
        } catch (IOException ioe) {
            fail(ioe);
        }
    }
}
