/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4313887 6838333 6925932
 * @summary Unit test for java.nio.file.Path path operations
 */

import java.nio.file.*;

public class PathOps {

    static final java.io.PrintStream out = System.out;

    private String input;
    private Path path;
    private Exception exc;

    private PathOps(String s) {
        out.println();
        input = s;
        try {
            path = FileSystems.getDefault().getPath(s);
            out.format("%s -> %s", s, path);
        } catch (Exception x) {
            exc = x;
            out.format("%s -> %s", s, x);
        }
        out.println();
    }

    Path path() {
        return path;
    }

    void fail() {
        throw new RuntimeException("PathOps failed");
    }

    void checkPath() {
        if (path == null) {
            throw new InternalError("path is null");
        }
    }

    void check(Object result, String expected) {
        out.format("\tExpected: %s\n", expected);
        out.format("\tActual: %s\n",  result);
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
        check(path.getName(), expected);
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
        Path s = FileSystems.getDefault().getPath(prefix);
        check(path.startsWith(s), true);
        return this;
    }

    PathOps notStarts(String prefix) {
        out.format("test not startsWith with %s\n", prefix);
        checkPath();
        Path s = FileSystems.getDefault().getPath(prefix);
        check(path.startsWith(s), false);
        return this;
    }

    PathOps ends(String suffix) {
        out.format("test endsWith %s\n", suffix);
        checkPath();
        Path s = FileSystems.getDefault().getPath(suffix);
        check(path.endsWith(s), true);
        return this;
    }

    PathOps notEnds(String suffix) {
        out.format("test not endsWith %s\n", suffix);
        checkPath();
        Path s = FileSystems.getDefault().getPath(suffix);
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

    PathOps relativize(String other, String expected) {
        out.format("test relativize %s\n", other);
        checkPath();
        Path that = FileSystems.getDefault().getPath(other);
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

    // -- PathOpss --

    static void header(String s) {
        out.println();
        out.println();
        out.println("-- " + s + " --");
    }

    static void doWindowsTests() {
        header("Windows specific tests");

        // all components present
        test("C:\\a\\b\\c")
            .root("C:\\")
            .parent("C:\\a\\b")
            .name("c");
        test("C:a\\b\\c")
            .root("C:")
            .parent("C:a\\b")
            .name("c");
        test("\\\\server\\share\\a")
            .root("\\\\server\\share\\")
            .parent("\\\\server\\share\\")
            .name("a");

        // root component only
        test("C:\\")
            .root("C:\\")
            .parent(null)
            .name(null);
        test("C:")
            .root("C:")
            .parent(null)
            .name(null);
        test("\\\\server\\share\\")
            .root("\\\\server\\share\\")
            .parent(null)
            .name(null);

        // no root component
        test("a\\b")
            .root(null)
            .parent("a")
            .name("b");

        // name component only
        test("foo")
            .root(null)
            .parent(null)
            .name("foo");

        // startsWith
        test("C:\\")
            .starts("C:\\")
            .starts("c:\\")
            .notStarts("C")
            .notStarts("C:");
        test("C:")
            .starts("C:")
            .starts("c:")
            .notStarts("C");
        test("\\")
            .starts("\\");
        test("C:\\foo\\bar")
            .starts("C:\\")
            .starts("C:\\foo")
            .starts("C:\\FOO")
            .starts("C:\\foo\\bar")
            .starts("C:\\Foo\\Bar")
            .notStarts("C:")
            .notStarts("C")
            .notStarts("C:foo");
        test("\\foo\\bar")
            .starts("\\")
            .starts("\\foo")
            .starts("\\foO")
            .starts("\\foo\\bar")
            .starts("\\fOo\\BaR")
            .notStarts("foo")
            .notStarts("foo\\bar");
        test("foo\\bar")
            .starts("foo")
            .starts("foo\\bar")
            .notStarts("\\");
        test("\\\\server\\share")
            .starts("\\\\server\\share")
            .starts("\\\\server\\share\\")
            .notStarts("\\");

        // endsWith
        test("C:\\")
            .ends("C:\\")
            .ends("c:\\")
            .notEnds("\\");
        test("C:")
            .ends("C:")
            .ends("c:");
        test("\\")
            .ends("\\");
        test("C:\\foo\\bar")
            .ends("bar")
            .ends("BAR")
            .ends("foo\\bar")
            .ends("Foo\\Bar")
            .ends("C:\\foo\\bar")
            .ends("c:\\foO\\baR")
            .notEnds("r")
            .notEnds("\\foo\\bar");
        test("\\foo\\bar")
            .ends("bar")
            .ends("BaR")
            .ends("foo\\bar")
            .ends("foO\\baR")
            .ends("\\foo\\bar")
            .ends("\\Foo\\Bar")
            .notEnds("oo\\bar");
        test("foo\\bar")
            .ends("bar")
            .ends("BAR")
            .ends("foo\\bar")
            .ends("Foo\\Bar")
            .notEnds("ar");
        test("\\\\server\\share")
            .ends("\\\\server\\share")
            .ends("\\\\server\\share\\")
            .notEnds("shared")
            .notEnds("\\");

        // elements
        test("C:\\a\\b\\c")
            .element(0, "a")
            .element(1, "b")
            .element(2, "c");
        test("foo.bar\\gus.alice")
            .element(0, "foo.bar")
            .element(1, "gus.alice");

        // subpath
        test("C:\\foo")
            .subpath(0, 1, "foo");
        test("C:foo")
            .subpath(0, 1, "foo");
        test("foo")
            .subpath(0, 1, "foo");
        test("C:\\foo\\bar\\gus")
            .subpath(0, 1, "foo")
            .subpath(0, 2, "foo\\bar")
            .subpath(0, 3, "foo\\bar\\gus")
            .subpath(1, 2, "bar")
            .subpath(1, 3, "bar\\gus")
            .subpath(2, 3, "gus");
        test("\\\\server\\share\\foo")
            .subpath(0, 1, "foo");

        // isAbsolute
        test("foo").notAbsolute();
        test("C:").notAbsolute();
        test("C:\\").absolute();
        test("C:\\abc").absolute();
        test("\\\\server\\share\\").absolute();

        // resolve
        test("C:\\")
            .resolve("foo", "C:\\foo")
            .resolve("D:\\bar", "D:\\bar")
            .resolve("\\\\server\\share\\bar", "\\\\server\\share\\bar")
            .resolve("C:foo", "C:\\foo")
            .resolve("D:foo", "D:foo");
        test("\\")
            .resolve("foo", "\\foo")
            .resolve("D:bar", "D:bar")
            .resolve("C:\\bar", "C:\\bar")
            .resolve("\\\\server\\share\\bar", "\\\\server\\share\\bar")
            .resolve("\\foo", "\\foo");
        test("\\foo")
            .resolve("bar", "\\foo\\bar")
            .resolve("D:bar", "D:bar")
            .resolve("C:\\bar", "C:\\bar")
            .resolve("\\\\server\\share\\bar", "\\\\server\\share\\bar")
            .resolve("\\bar", "\\bar");
        test("foo")
            .resolve("bar", "foo\\bar")
            .resolve("D:\\bar", "D:\\bar")
            .resolve("\\\\server\\share\\bar", "\\\\server\\share\\bar")
            .resolve("C:bar", "C:bar")
            .resolve("D:foo", "D:foo");
        test("C:")
            .resolve("foo", "C:foo");
        test("\\\\server\\share\\foo")
            .resolve("bar", "\\\\server\\share\\foo\\bar")
            .resolve("\\bar", "\\\\server\\share\\bar")
            .resolve("D:\\bar", "D:\\bar")
            .resolve("\\\\other\\share\\bar", "\\\\other\\share\\bar")
            .resolve("D:bar", "D:bar");

        // relativize
        test("foo\\bar")
            .relativize("foo\\bar", null)
            .relativize("foo", "..");
        test("C:\\a\\b\\c")
            .relativize("C:\\a", "..\\..");
        test("\\\\server\\share\\foo")
            .relativize("\\\\server\\share\\bar", "..\\bar");

        // normalize
        test("C:\\")
            .normalize("C:\\");
        test("C:\\.")
            .normalize("C:\\");
        test("C:\\..")
            .normalize("C:\\");
        test("\\\\server\\share")
            .normalize("\\\\server\\share\\");
        test("\\\\server\\share\\.")
            .normalize("\\\\server\\share\\");
        test("\\\\server\\share\\..")
            .normalize("\\\\server\\share\\");
        test("C:")
            .normalize("C:");
        test("C:.")
            .normalize("C:");
        test("C:..")
            .normalize("C:..");
        test("\\")
            .normalize("\\");
        test("\\.")
            .normalize("\\");
        test("\\..")
            .normalize("\\");
        test("foo")
            .normalize("foo");
        test("foo\\.")
            .normalize("foo");
        test("foo\\..")
            .normalize(null);
        test("C:\\foo")
            .normalize("C:\\foo");
        test("C:\\foo\\.")
            .normalize("C:\\foo");
        test("C:\\.\\foo")
            .normalize("C:\\foo");
        test("C:\\foo\\..")
            .normalize("C:\\");
        test("C:\\..\\foo")
            .normalize("C:\\foo");
        test("\\\\server\\share\\foo")
            .normalize("\\\\server\\share\\foo");
        test("\\\\server\\share\\foo\\.")
            .normalize("\\\\server\\share\\foo");
        test("\\\\server\\share\\.\\foo")
            .normalize("\\\\server\\share\\foo");
        test("\\\\server\\share\\foo\\..")
            .normalize("\\\\server\\share\\");
        test("\\\\server\\share\\..\\foo")
            .normalize("\\\\server\\share\\foo");
        test("C:foo")
            .normalize("C:foo");
        test("C:foo\\.")
            .normalize("C:foo");
        test("C:.\\foo")
            .normalize("C:foo");
        test("C:foo\\..")
            .normalize("C:");
        test("C:..\\foo")
            .normalize("C:..\\foo");
        test("\\foo")
            .normalize("\\foo");
        test("\\foo\\.")
            .normalize("\\foo");
        test("\\.\\foo")
            .normalize("\\foo");
        test("\\foo\\..")
            .normalize("\\");
        test("\\..\\foo")
            .normalize("\\foo");
        test(".")
            .normalize(null);
        test("..")
            .normalize("..");
        test("\\..\\..")
            .normalize("\\");
        test("..\\..\\foo")
            .normalize("..\\..\\foo");
        test("foo\\bar\\..")
            .normalize("foo");
        test("foo\\bar\\.\\..")
            .normalize("foo");
        test("foo\\bar\\gus\\..\\..")
            .normalize("foo");
        test(".\\foo\\.\\bar\\.\\gus\\..\\.\\..")
            .normalize("foo");

        // UNC corner cases
        test("\\\\server\\share\\")
            .root("\\\\server\\share\\")
            .parent(null)
            .name(null);
        test("\\\\server")
            .invalid();
        test("\\\\server\\")
            .invalid();
        test("\\\\server\\share")
            .root("\\\\server\\share\\")
            .parent(null)
            .name(null);

        // invalid
        test(":\\foo")
            .invalid();
        test("C::")
            .invalid();
        test("C:\\?")           // invalid character
            .invalid();
        test("C:\\*")           // invalid character
            .invalid();
        test("C:\\abc\u0001\\foo")
            .invalid();
        test("C:\\\u0019\\foo")
            .invalid();
        test("\\\\server\u0019\\share")
            .invalid();
        test("\\\\server\\share\u0019")
            .invalid();
        test("foo\u0000\bar")
            .invalid();
        test("C:\\foo ")                // trailing space
             .invalid();
        test("C:\\foo \\bar")
            .invalid();
        //test("C:\\foo.")              // trailing dot
            //.invalid();
        //test("C:\\foo...\\bar")
            //.invalid();

        // normalization at construction time (remove redundant and replace slashes)
        test("C:/a/b/c")
            .string("C:\\a\\b\\c")
            .root("C:\\")
            .parent("C:\\a\\b");
        test("C://a//b//c")
            .string("C:\\a\\b\\c")
            .root("C:\\")
            .parent("C:\\a\\b");

        // hashCode
        header("hashCode");
        int h1 = test("C:\\foo").path().hashCode();
        int h2 = test("c:\\FOO").path().hashCode();
        if (h1 != h2)
            throw new RuntimeException("PathOps failed");
    }

    static void doUnixTests() {
        header("Unix specific tests");

        // all components
        test("/a/b/c")
            .root("/")
            .parent("/a/b")
            .name("c");

        // root component only
        test("/")
            .root("/")
            .parent(null)
            .name(null);

        // no root component
        test("a/b")
            .root(null)
            .parent("a")
            .name("b");

        // name component only
        test("foo")
            .root(null)
            .parent(null)
            .name("foo");

        // startsWith
        test("/")
            .starts("/")
            .notStarts("/foo");
        test("/foo")
            .starts("/")
            .starts("/foo")
            .notStarts("/f");
        test("/foo/bar")
            .starts("/")
            .starts("/foo")
            .starts("/foo/bar")
            .notStarts("/f")
            .notStarts("foo")
            .notStarts("foo/bar");
        test("foo")
            .starts("foo")
            .notStarts("f");
        test("foo/bar")
            .starts("foo")
            .starts("foo/bar")
            .notStarts("f")
            .notStarts("/foo")
            .notStarts("/foo/bar");

        // endsWith
        test("/")
            .ends("/")
            .notEnds("foo")
            .notEnds("/foo");
        test("/foo")
            .ends("foo")
            .ends("/foo")
            .notEnds("fool");
        test("/foo/bar")
            .ends("bar")
            .ends("foo/bar")
            .ends("/foo/bar")
            .notEnds("ar")
            .notEnds("barack")
            .notEnds("/bar")
            .notEnds("o/bar");
        test("foo")
            .ends("foo")
            .notEnds("oo")
            .notEnds("oola");
        test("foo/bar")
            .ends("bar")
            .ends("foo/bar")
            .notEnds("r")
            .notEnds("barmaid")
            .notEnds("/bar");
        test("foo/bar/gus")
            .ends("gus")
            .ends("bar/gus")
            .ends("foo/bar/gus")
            .notEnds("g")
            .notEnds("/gus")
            .notEnds("r/gus")
            .notEnds("barack/gus")
            .notEnds("bar/gust");

        // elements
        test("a/b/c")
            .element(0,"a")
            .element(1,"b")
            .element(2,"c");

        // isAbsolute
        test("/")
            .absolute();
        test("/tmp")
            .absolute();
        test("tmp")
            .notAbsolute();

        // resolve
        test("/tmp")
            .resolve("foo", "/tmp/foo")
            .resolve("/foo", "/foo");
        test("tmp")
            .resolve("foo", "tmp/foo")
            .resolve("/foo", "/foo");

        // relativize
        test("/a/b/c")
            .relativize("/a/b/c", null)
            .relativize("/a/b/c/d/e", "d/e")
            .relativize("/a/x", "../../x");

        // normalize
        test("/")
            .normalize("/");
        test("foo")
            .normalize("foo");
        test("/foo")
            .normalize("/foo");
        test(".")
            .normalize(null);
        test("..")
            .normalize("..");
        test("/..")
            .normalize("/");
        test("/../..")
            .normalize("/");
        test("foo/.")
            .normalize("foo");
        test("./foo")
            .normalize("foo");
        test("foo/..")
            .normalize(null);
        test("../foo")
            .normalize("../foo");
        test("../../foo")
            .normalize("../../foo");
        test("foo/bar/..")
            .normalize("foo");
        test("foo/bar/gus/../..")
            .normalize("foo");
        test("/foo/bar/gus/../..")
            .normalize("/foo");

        // invalid
        test("foo\u0000bar")
            .invalid();
        test("\u0000foo")
            .invalid();
        test("bar\u0000")
            .invalid();
        test("//foo\u0000bar")
            .invalid();
        test("//\u0000foo")
            .invalid();
        test("//bar\u0000")
            .invalid();

        // normalization
        test("//foo//bar")
            .string("/foo/bar")
            .root("/")
            .parent("/foo")
            .name("bar");
    }

    static void npes() {
        header("NullPointerException");

        Path path = FileSystems.getDefault().getPath("foo");

        try {
            path.resolve((String)null);
            throw new RuntimeException("NullPointerException not thrown");
        } catch (NullPointerException npe) {
        }

        try {
            path.relativize(null);
            throw new RuntimeException("NullPointerException not thrown");
        } catch (NullPointerException npe) {
        }

        try {
            path.compareTo(null);
            throw new RuntimeException("NullPointerException not thrown");
        } catch (NullPointerException npe) {
        }

        try {
            path.startsWith(null);
            throw new RuntimeException("NullPointerException not thrown");
        } catch (NullPointerException npe) {
        }

        try {
            path.endsWith(null);
            throw new RuntimeException("NullPointerException not thrown");
        } catch (NullPointerException npe) {
        }

    }

    public static void main(String[] args) {
        // all platforms
        npes();

        // operating system specific
        String osname = System.getProperty("os.name");
        if (osname.startsWith("Windows")) {
            doWindowsTests();
        }
        if (osname.equals("SunOS") || osname.equals("Linux")) {
            doUnixTests();
        }

    }
}
