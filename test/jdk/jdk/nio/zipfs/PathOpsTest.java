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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.ProviderMismatchException;

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
 * @run junit PathOpsTest
 * @run junit/othervm PathOpsTest
 */
public class PathOpsTest {

    static FileSystem fs;

    @BeforeAll
    static void setup() throws IOException {
        // create empty JAR file, test doesn't require any contents
        Path emptyJar = Utils.createJarFile("empty.jar");
        fs = FileSystems.newFileSystem(emptyJar);
    }

    @AfterAll
    static void cleanup() throws IOException {
        fs.close();
    }

    @Test
    void nullPointerTest() {
        Path path = fs.getPath("foo");
        assertThrows(NullPointerException.class, () -> path.resolve((String) null));
        assertThrows(NullPointerException.class, () -> path.relativize(null));
        assertThrows(NullPointerException.class, () -> path.compareTo(null));
        assertThrows(NullPointerException.class, () -> path.startsWith((Path) null));
        assertThrows(NullPointerException.class, () -> path.endsWith((Path) null));
    }

    @Test
    void mismatchedProvidersTest() {
        Path path = fs.getPath("foo");
        Path other = Paths.get("foo");
        assertThrows(ProviderMismatchException.class, () -> path.compareTo(other));
        assertThrows(ProviderMismatchException.class, () -> path.resolve(other));
        assertThrows(ProviderMismatchException.class, () -> path.relativize(other));
        assertFalse(path.startsWith(other), "providerMismatched startsWith() returns true ");
        assertFalse(path.endsWith(other), "providerMismatched endsWith() returns true ");
    }

    @Test
    void doPathOpTests() {
        // construction
        PathOps.test("/")
                .string("/");
        PathOps.test("/", "")
                .string("/");
        PathOps.test("/", "foo")
                .string("/foo");
        PathOps.test("/", "/foo")
                .string("/foo");
        PathOps.test("/", "foo/")
                .string("/foo");
        PathOps.test("foo", "bar", "gus")
                .string("foo/bar/gus");
        PathOps.test("")
                .string("");
        PathOps.test("", "/")
                .string("/");
        PathOps.test("", "foo", "", "bar", "", "/gus")
                .string("foo/bar/gus");

        // all components
        PathOps.test("/a/b/c")
                .root("/")
                .parent("/a/b")
                .name("c");

        // root component only
        PathOps.test("/")
                .root("/")
                .parent(null)
                .name(null)
                .nameCount(0);

        // empty name
        PathOps.test("")
                .root(null)
                .parent(null)
                .name("")
                .nameCount(1);

        // no root component
        PathOps.test("a/b")
                .root(null)
                .parent("a")
                .name("b");

        // name component only
        PathOps.test("foo")
                .root(null)
                .parent(null)
                .name("foo");

        // startsWith
        PathOps.test("")
                .starts("")
                .notStarts("/");
        PathOps.test("/")
                .starts("/")
                .notStarts("/foo");
        PathOps.test("/foo")
                .starts("/")
                .starts("/foo")
                .notStarts("/f")
                .notStarts("");
        PathOps.test("/foo/bar")
                .starts("/")
                .starts("/foo")
                .starts("/foo/")
                .starts("/foo/bar")
                .notStarts("/f")
                .notStarts("foo")
                .notStarts("foo/bar")
                .notStarts("");
        PathOps.test("foo")
                .starts("foo")
                .notStarts("f");
        PathOps.test("foo/bar")
                .starts("foo")
                .starts("foo/")
                .starts("foo/bar")
                .notStarts("f")
                .notStarts("/foo")
                .notStarts("/foo/bar");

        // endsWith
        PathOps.test("")
                .ends("")
                .notEnds("/");
        PathOps.test("/")
                .ends("/")
                .notEnds("foo")
                .notEnds("/foo");
        PathOps.test("/foo")
                .ends("foo")
                .ends("/foo")
                .notEnds("/");
        PathOps.test("/foo/bar")
                .ends("bar")
                .ends("foo/bar")
                .ends("foo/bar/")
                .ends("/foo/bar")
                .notEnds("/bar");
        PathOps.test("/foo/bar/")
                .ends("bar")
                .ends("foo/bar")
                .ends("foo/bar/")
                .ends("/foo/bar")
                .notEnds("/bar");
        PathOps.test("foo")
                .ends("foo");
        PathOps.test("foo/bar")
                .ends("bar")
                .ends("bar/")
                .ends("foo/bar/")
                .ends("foo/bar");

        // elements
        PathOps.test("a/b/c")
                .element(0, "a")
                .element(1, "b")
                .element(2, "c");

        // isAbsolute
        PathOps.test("/")
                .absolute();
        PathOps.test("/tmp")
                .absolute();
        PathOps.test("tmp")
                .notAbsolute();
        PathOps.test("")
                .notAbsolute();

        // resolve
        PathOps.test("/tmp")
                .resolve("foo", "/tmp/foo")
                .resolve("/foo", "/foo")
                .resolve("", "/tmp");
        PathOps.test("tmp")
                .resolve("foo", "tmp/foo")
                .resolve("/foo", "/foo")
                .resolve("", "tmp");
        PathOps.test("")
                .resolve("", "")
                .resolve("foo", "foo")
                .resolve("/foo", "/foo");
        PathOps.test("/")
                .resolve("", "/")
                .resolve("foo", "/foo")
                .resolve("/foo", "/foo")
                .resolve("/foo/", "/foo");

        // resolve(Path)
        PathOps.test("/tmp")
                .resolvePath("foo", "/tmp/foo")
                .resolvePath("/foo", "/foo")
                .resolvePath("", "/tmp");
        PathOps.test("tmp")
                .resolvePath("foo", "tmp/foo")
                .resolvePath("/foo", "/foo")
                .resolvePath("", "tmp");
        PathOps.test("")
                .resolvePath("", "")
                .resolvePath("foo", "foo")
                .resolvePath("/foo", "/foo");
        PathOps.test("/")
                .resolvePath("", "/")
                .resolvePath("foo", "/foo")
                .resolvePath("/foo", "/foo")
                .resolvePath("/foo/", "/foo");

        // resolveSibling
        PathOps.test("foo")
                .resolveSibling("bar", "bar")
                .resolveSibling("/bar", "/bar")
                .resolveSibling("", "");
        PathOps.test("foo/bar")
                .resolveSibling("gus", "foo/gus")
                .resolveSibling("/gus", "/gus")
                .resolveSibling("", "foo");
        PathOps.test("/foo")
                .resolveSibling("gus", "/gus")
                .resolveSibling("/gus", "/gus")
                .resolveSibling("", "/");
        PathOps.test("/foo/bar")
                .resolveSibling("gus", "/foo/gus")
                .resolveSibling("/gus", "/gus")
                .resolveSibling("", "/foo");
        PathOps.test("")
                .resolveSibling("foo", "foo")
                .resolveSibling("/foo", "/foo")
                .resolve("", "");

        // relativize
        PathOps.test("/a/b/c")
                .relativize("/a/b/c", "")
                .relativize("/a/b/c/d/e", "d/e")
                .relativize("/a/x", "../../x")
                .relativize("/x", "../../../x");
        PathOps.test("a/b/c")
                .relativize("a/b/c/d", "d")
                .relativize("a/x", "../../x")
                .relativize("x", "../../../x")
                .relativize("", "../../..");
        PathOps.test("")
                .relativize("a", "a")
                .relativize("a/b/c", "a/b/c")
                .relativize("", "");
        PathOps.test("/")
                .relativize("/a", "a")
                .relativize("/a/c", "a/c");
        // 8146754
        PathOps.test("/tmp/path")
                .relativize("/tmp/path/a.txt", "a.txt");
        PathOps.test("/tmp/path/")
                .relativize("/tmp/path/a.txt", "a.txt");

        // normalize
        PathOps.test("/")
                .normalize("/");
        PathOps.test("foo")
                .normalize("foo");
        PathOps.test("/foo")
                .normalize("/foo");
        PathOps.test(".")
                .normalize("");
        PathOps.test("..")
                .normalize("..");
        PathOps.test("/..")
                .normalize("/");
        PathOps.test("/../..")
                .normalize("/");
        PathOps.test("foo/.")
                .normalize("foo");
        PathOps.test("./foo")
                .normalize("foo");
        PathOps.test("foo/..")
                .normalize("");
        PathOps.test("../foo")
                .normalize("../foo");
        PathOps.test("../../foo")
                .normalize("../../foo");
        PathOps.test("foo/bar/..")
                .normalize("foo");
        PathOps.test("foo/bar/gus/../..")
                .normalize("foo");
        PathOps.test("/foo/bar/gus/../..")
                .normalize("/foo");
        PathOps.test("/./.")
                .normalize("/");
        PathOps.test("/.")
                .normalize("/");
        PathOps.test("/./abc")
                .normalize("/abc");
        // invalid
        PathOps.test("foo\u0000bar")
                .invalid();
        PathOps.test("\u0000foo")
                .invalid();
        PathOps.test("bar\u0000")
                .invalid();
        PathOps.test("//foo\u0000bar")
                .invalid();
        PathOps.test("//\u0000foo")
                .invalid();
        PathOps.test("//bar\u0000")
                .invalid();

        // normalization
        PathOps.test("//foo//bar")
                .string("/foo/bar")
                .root("/")
                .parent("/foo")
                .name("bar");

        // isSameFile
        PathOps.test("/fileDoesNotExist")
                .isSameFile("/fileDoesNotExist");

        // 8139956
        System.out.println("check getNameCount");
        int nc = fs.getPath("/").relativize(fs.getPath("/")).getNameCount();
        assertEquals(1, nc , "getNameCount of empty path failed");
    }

    static class PathOps {

        static final java.io.PrintStream out = System.out;

        private Path path;
        private Exception exc;

        // This is new instance per test
        private PathOps(String first, String... more) {
            out.println();
            try {
                path = fs.getPath(first, more);
                out.format("%s -> %s", first, path);
            } catch (Exception x) {
                exc = x;
                out.format("%s -> %s", first, x);
            }
            out.println();
        }

        Path path() {
            return path;
        }

        void checkPath() {
            assertNotNull(path, "path is null");
        }

        void check(Object result, String expected) {
            out.format("\tExpected: %s\n", expected);
            out.format("\tActual: %s\n", result);
            if (result == null) {
                if (expected == null) return;
            } else {
                // compare string representations
                if (expected != null && result.toString().equals(expected.toString()))
                    return;
            }
            fail();
        }

        void check(Object result, boolean expected) {
            check(result, Boolean.toString(expected));
        }

        void check(Object result, int expected) {
            check(result, Integer.toString(expected));
        }

        PathOps root(String expected) {
            out.println("check root");
            checkPath();
            check(path.getRoot(), expected);
            return this;
        }

        PathOps parent(String expected) {
            out.println("check parent");
            checkPath();
            check(path.getParent(), expected);
            return this;
        }

        PathOps name(String expected) {
            out.println("check name");
            checkPath();
            check(path.getFileName(), expected);
            return this;
        }

        PathOps nameCount(int expected) {
            out.println("check nameCount");
            checkPath();
            check(path.getNameCount(), expected);
            return this;
        }

        PathOps element(int index, String expected) {
            out.format("check element %d\n", index);
            checkPath();
            check(path.getName(index), expected);
            return this;
        }

        PathOps subpath(int startIndex, int endIndex, String expected) {
            out.format("test subpath(%d,%d)\n", startIndex, endIndex);
            checkPath();
            check(path.subpath(startIndex, endIndex), expected);
            return this;
        }

        PathOps starts(String prefix) {
            out.format("test startsWith with %s\n", prefix);
            checkPath();
            Path s = fs.getPath(prefix);
            check(path.startsWith(s), true);
            return this;
        }

        PathOps notStarts(String prefix) {
            out.format("test not startsWith with %s\n", prefix);
            checkPath();
            Path s = fs.getPath(prefix);
            check(path.startsWith(s), false);
            return this;
        }

        PathOps ends(String suffix) {
            out.format("test endsWith %s\n", suffix);
            checkPath();
            Path s = fs.getPath(suffix);
            check(path.endsWith(s), true);
            return this;
        }

        PathOps notEnds(String suffix) {
            out.format("test not endsWith %s\n", suffix);
            checkPath();
            Path s = fs.getPath(suffix);
            check(path.endsWith(s), false);
            return this;
        }

        PathOps absolute() {
            out.println("check path is absolute");
            checkPath();
            check(path.isAbsolute(), true);
            return this;
        }

        PathOps notAbsolute() {
            out.println("check path is not absolute");
            checkPath();
            check(path.isAbsolute(), false);
            return this;
        }

        PathOps resolve(String other, String expected) {
            out.format("test resolve %s\n", other);
            checkPath();
            check(path.resolve(other), expected);
            return this;
        }

        PathOps resolvePath(String other, String expected) {
            out.format("test resolve %s\n", other);
            checkPath();
            check(path.resolve(fs.getPath(other)), expected);
            return this;
        }

        PathOps resolveSibling(String other, String expected) {
            out.format("test resolveSibling %s\n", other);
            checkPath();
            check(path.resolveSibling(other), expected);
            return this;
        }

        PathOps relativize(String other, String expected) {
            out.format("test relativize %s\n", other);
            checkPath();
            Path that = fs.getPath(other);
            check(path.relativize(that), expected);
            return this;
        }

        PathOps normalize(String expected) {
            out.println("check normalized path");
            checkPath();
            check(path.normalize(), expected);
            return this;
        }

        PathOps string(String expected) {
            out.println("check string representation");
            checkPath();
            check(path, expected);
            return this;
        }

        PathOps isSameFile(String target) {
            try {
                out.println("check two paths are same");
                checkPath();
                check(Files.isSameFile(path, test(target).path()), true);
            } catch (IOException ioe) {
                fail();
            }
            return this;
        }

        PathOps invalid() {
            if (!(exc instanceof InvalidPathException)) {
                out.println("InvalidPathException not thrown as expected");
                fail();
            }
            return this;
        }

        static PathOps test(String s) {
            return new PathOps(s);
        }

        static PathOps test(String first, String... more) {
            return new PathOps(first, more);
        }
    }
}
