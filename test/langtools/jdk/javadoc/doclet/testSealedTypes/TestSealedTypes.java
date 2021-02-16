/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug      8227047
 * @summary  Sealed types
 * @library  /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestSealedTypes
 */

import java.io.IOException;
import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestSealedTypes extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestSealedTypes tester = new TestSealedTypes();
        tester.runTests(m -> new Object[] { Path.of(m.getName()) });
    }

    private final ToolBox tb = new ToolBox();

    @Test
    public void testSealedModifierClass(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "package p; public sealed class A { }");

        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "--enable-preview", "--source", thisRelease,
                "p");
        checkExit(Exit.OK);

        checkOutput("p/A.html", true,
                """
                    <div class="type-signature"><span class="modifiers">public sealed<sup><a href="#\
                    preview-p.A">PREVIEW</a></sup> class </span><span class="element-name type-name-\
                    label">A</span>""");
    }

    @Test
    public void testSealedModifierInterface(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "package p; public sealed interface A { }");

        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "--enable-preview", "--source", thisRelease,
                "p");
        checkExit(Exit.OK);

        checkOutput("p/A.html", true,
                """
                    <div class="type-signature"><span class="modifiers">public sealed<sup><a href="#\
                    preview-p.A">PREVIEW</a></sup> interface </span><span class="element-name type-n\
                    ame-label">A</span>""");
    }

    @Test
    public void testNonSealedModifierClass(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "package p; public sealed class A permits B { }",
                "package p; public non-sealed class B extends A { }");

        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "--enable-preview", "--source", thisRelease,
                "p");
        checkExit(Exit.OK);

        checkOutput("p/A.html", true,
                """
                    <div class="type-signature"><span class="modifiers">public sealed<sup><a href="#\
                    preview-p.A">PREVIEW</a></sup> class </span><span class="element-name type-name-\
                    label">A</span>""");

        checkOutput("p/B.html", true,
                """
                    <div class="type-signature"><span class="modifiers">public non-sealed<sup><a hre\
                    f="#preview-p.B">PREVIEW</a></sup> class </span><span class="element-name type-n\
                    ame-label">B</span>""");
    }

    @Test
    public void testNonSealedModifierInterface(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "package p; public sealed interface A permits B { }",
                "package p; public non-sealed interface B extends A { }");

        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "--enable-preview", "--source", thisRelease,
                "p");
        checkExit(Exit.OK);

        checkOutput("p/A.html", true,
                """
                    <div class="type-signature"><span class="modifiers">public sealed<sup><a href="#\
                    preview-p.A">PREVIEW</a></sup> interface </span><span class="element-name type-n\
                    ame-label">A</span>""");

        checkOutput("p/B.html", true,
                """
                    <div class="type-signature"><span class="modifiers">public non-sealed<sup><a hre\
                    f="#preview-p.B">PREVIEW</a></sup> interface </span><span class="element-name ty\
                    pe-name-label">B</span>""");
    }

    @Test
    public void testSealedSubtypeModifierClass(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "package p; public sealed class A permits B { }",
                "package p; public sealed abstract class B extends A { }");

        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "--enable-preview", "--source", thisRelease,
                "p");
        checkExit(Exit.OK);

        checkOutput("p/A.html", true,
                """
                    <div class="type-signature"><span class="modifiers">public sealed<sup><a href="#\
                    preview-p.A">PREVIEW</a></sup> class </span><span class="element-name type-name-\
                    label">A</span>""");

        checkOutput("p/B.html", true,
                """
                    <div class="type-signature"><span class="modifiers">public abstract sealed<sup><\
                    a href="#preview-p.B">PREVIEW</a></sup> class </span><span class="element-name t\
                    ype-name-label">B</span>""");
    }

    @Test
    public void testSealedSubtypeInterface(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "package p; public sealed interface A permits B { }",
                "package p; public sealed interface B extends A { }");

        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "--enable-preview", "--source", thisRelease,
                "p");
        checkExit(Exit.OK);

        checkOutput("p/A.html", true,
                """
                    <div class="type-signature"><span class="modifiers">public sealed<sup><a href="#\
                    preview-p.A">PREVIEW</a></sup> interface </span><span class="element-name type-n\
                    ame-label">A</span>""");

        checkOutput("p/B.html", true,
                """
                    <div class="type-signature"><span class="modifiers">public sealed<sup><a href="#\
                    preview-p.B">PREVIEW</a></sup> interface </span><span class="element-name type-n\
                    ame-label">B</span>""");
    }

    @Test
    public void testSinglePermits(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "package p; public sealed class A permits B { }",
                "package p; public final class B extends A { }");

        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "--no-platform-links",
                "--enable-preview", "--source", thisRelease,
                "p");
        checkExit(Exit.OK);

        checkOutput("p/A.html", true,
                """
                    <div class="type-signature"><span class="modifiers">public sealed<sup><a href="#\
                    preview-p.A">PREVIEW</a></sup> class </span><span class="element-name type-name-\
                    label">A</span>
                    <span class="extends-implements">extends java.lang.Object</span>
                    <span class="permits">permits<sup><a href="#preview-p.A">PREVIEW</a></sup> <a hr\
                    ef="B.html" title="class in p">B</a><sup><a href="B.html#preview-p.B">PREVIEW</a\
                    ></sup></span></div>""");
    }

    @Test
    public void testMultiplePermits(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "package p; public sealed class A permits B,C,D { }",
                "package p; public final class B extends A { }",
                "package p; public final class C extends A { }",
                "package p; public final class D extends A { }");

        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "--no-platform-links",
                "--enable-preview", "--source", thisRelease,
                "p");
        checkExit(Exit.OK);

        checkOutput("p/A.html", true,
                """
                    <div class="type-signature"><span class="modifiers">public sealed<sup><a href="#\
                    preview-p.A">PREVIEW</a></sup> class </span><span class="element-name type-name-\
                    label">A</span>
                    <span class="extends-implements">extends java.lang.Object</span>
                    <span class="permits">permits<sup><a href="#preview-p.A">PREVIEW</a></sup> <a hr\
                    ef="B.html" title="class in p">B</a><sup><a href="B.html#preview-p.B">PREVIEW</a\
                    ></sup>, <a href="C.html" title="class in p">C</a><sup><a href="C.html#preview-p\
                    .C">PREVIEW</a></sup>, <a href="D.html" title="class in p">D</a><sup><a href="D.\
                    html#preview-p.D">PREVIEW</a></sup></span></div>""");
    }

    @Test
    public void testPartialMultiplePermits(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "package p; public sealed class A permits B,C,D { }",
                "package p; public final class B extends A { }",
                "package p; public final class C extends A { }",
                "package p;        final class D extends A { }");

        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "--no-platform-links",
                "--enable-preview", "--source", thisRelease,
                "p");
        checkExit(Exit.OK);

        checkOutput("p/A.html", true,
                """
                    <div class="type-signature"><span class="modifiers">public sealed<sup><a href="#\
                    preview-p.A">PREVIEW</a></sup> class </span><span class="element-name type-name-\
                    label">A</span>
                    <span class="extends-implements">extends java.lang.Object</span>
                    <span class="permits">permits<sup><a href="#preview-p.A">PREVIEW</a></sup> <a hr\
                    ef="B.html" title="class in p">B</a><sup><a href="B.html#preview-p.B">PREVIEW</a\
                    ></sup>, <a href="C.html" title="class in p">C</a><sup><a href="C.html#preview-p\
                    .C">PREVIEW</a></sup> <span class="permits-note">(not exhaustive)</span></span><\
                    /div>""");
    }

    // @Test // javac incorrectly rejects the source
    public void testPartialMultiplePermitsWithSubtypes1(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "package p; public sealed class A permits B,C,D { }",
                "package p; public final  class B extends A { }",
                "package p; public final  class C extends A { }",
                "package p;        sealed class D extends A permits D1, D2 { }",
                "package p; public final  class D1 extends D { }",
                "package p; public final  class D2 extends D { }");

        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "--enable-preview", "--source", thisRelease,
                "p");
        checkExit(Exit.OK);

        checkOutput("p/A.html", true,
                """
                    <div class="type-signature"><span class="modifiers">public sealed<sup><a href="#\
                    preview">PREVIEW</a></sup> class </span><span class="element-name type-name-labe\
                    l">A</span>
                    <span class="extends-implements">extends java.lang.Object</span>
                    <span class="permits">permits<sup><a href="#preview">PREVIEW</a></sup> <a href="\
                    A.B.html" title="class in p">A.B</a><sup><a href="B.html#preview">PREVIEW</a></s\
                    up>, <a href="A.C.html" title="class in p">A.C</a><sup><a href="C.html#preview">\
                    PREVIEW</a></sup>, <a href="A.D.html" title="class in p">A.D</a></span></div>""");
    }

    @Test
    public void testPartialMultiplePermitsWithSubtypes2(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "package p; public sealed class A permits B,C,D { }",
                "package p; public final  class B extends A { }",
                "package p; public final  class C extends A { }",
                "package p;    non-sealed class D extends A { }",
                "package p; public final  class D1 extends D { }",
                "package p; public final  class D2 extends D { }");

        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "--no-platform-links",
                "--enable-preview", "--source", thisRelease,
                "p");
        checkExit(Exit.OK);

        checkOutput("p/A.html", true,
                """
                <div class="type-signature"><span class="modifiers">public sealed<sup><a href="#prev\
                iew-p.A">PREVIEW</a></sup> class </span><span class="element-name type-name-label">A\
                </span>
                <span class="extends-implements">extends java.lang.Object</span>
                <span class="permits">permits<sup><a href="#preview-p.A">PREVIEW</a></sup> <a href="\
                B.html" title="class in p">B</a><sup><a href="B.html#preview-p.B">PREVIEW</a></sup>,\
                 <a href="C.html" title="class in p">C</a><sup><a href="C.html#preview-p.C">PREVIEW<\
                /a></sup> <span class="permits-note">(not exhaustive)</span></span></div>""");
    }

    @Test
    public void testImplicitPermitsAuxiliary(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "package p; public sealed class A { }\n"
                + "final class B extends A { }\n"
                + "final class C extends A { }\n"
                + "final class D extends A { }\n");

        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "--no-platform-links",
                "-package",
                "--enable-preview", "--source", thisRelease,
                "p");
        checkExit(Exit.OK);

        checkOutput("p/A.html", true,
                """
                    <div class="type-signature"><span class="modifiers">public sealed<sup><a href="#\
                    preview-p.A">PREVIEW</a></sup> class </span><span class="element-name type-name-\
                    label">A</span>
                    <span class="extends-implements">extends java.lang.Object</span>
                    <span class="permits">permits<sup><a href="#preview-p.A">PREVIEW</a></sup> <a hr\
                    ef="B.html" title="class in p">B</a><sup><a href="B.html#preview-p.B">PREVIEW</a\
                    ></sup>, <a href="C.html" title="class in p">C</a><sup><a href="C.html#preview-p\
                    .C">PREVIEW</a></sup>, <a href="D.html" title="class in p">D</a><sup><a href="D.\
                    html#preview-p.D">PREVIEW</a></sup></span></div>""");
    }

    @Test
    public void testImplicitPermitsNested(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "package p; public sealed class A {\n"
                + "  public static final class B extends A { }\n"
                + "  public static final class C extends A { }\n"
                + "  public static final class D extends A { }\n"
                + "}");

        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "--no-platform-links",
                "--enable-preview", "--source", thisRelease,
                "p");
        checkExit(Exit.OK);

        checkOutput("p/A.html", true,
                """
                    <div class="type-signature"><span class="modifiers">public sealed<sup><a href="#\
                    preview-p.A">PREVIEW</a></sup> class </span><span class="element-name type-name-\
                    label">A</span>
                    <span class="extends-implements">extends java.lang.Object</span>
                    <span class="permits">permits<sup><a href="#preview-p.A">PREVIEW</a></sup> <a hr\
                    ef="A.B.html" title="class in p">A.B</a><sup><a href="A.B.html#preview-p.A.B">PR\
                    EVIEW</a></sup>, <a href="A.C.html" title="class in p">A.C</a><sup><a href="A.C.\
                    html#preview-p.A.C">PREVIEW</a></sup>, <a href="A.D.html" title="class in p">A.D\
                    </a><sup><a href="A.D.html#preview-p.A.D">PREVIEW</a></sup></span></div>""");
    }
}
