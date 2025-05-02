/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8200337 8307377 8306578
 * @summary Generalize see and link tags for user-defined anchors
 * @library /tools/lib ../../lib
 * @modules
 *      jdk.javadoc/jdk.javadoc.internal.tool
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build javadoc.tester.*
 * @run main TestSeeLinkAnchor
 */

import java.nio.file.Path;
import java.nio.file.Paths;

import builder.ClassBuilder;
import builder.ClassBuilder.*;
import toolbox.ModuleBuilder;
import toolbox.ToolBox;

import javadoc.tester.JavadocTester;

public class TestSeeLinkAnchor extends JavadocTester {

    final ToolBox tb;
    private final Path src;

    public static void main(String... args) throws Exception {
        var tester = new TestSeeLinkAnchor();
        tester.runTests();
    }

    TestSeeLinkAnchor() throws Exception {
        tb = new ToolBox();
        src = Paths.get("src");
        generateModuleSources();
        generatePackageSources();
        generateInvalidLinkSource();
        generateMissingLabelSource();
    }

    @Test
    public void testPackage(Path base) throws Exception {
        Path out = base.resolve("out");

        javadoc("-d", out.toString(),
                "-sourcepath", src.toString(),
                "--no-platform-links",
                "p1", "p2");

        checkExit(Exit.OK);
        checkOrder("p1/Class1.html",
            """
                    Link to <a href="../p2/package-summary.html#package-p2-heading"><code>heading in package p2</code></a>""",
            """
                    Plain link to <a href="../p2/Class2.html#class2-sub-heading">sub heading above</a></div>""",
            """
                    <li><a href="../p2/Class2.html#class2main">See main heading in p2.Class2</a></li>
                    <li><a href="../p2/package-summary.html#package-p2-heading">See heading in p2</a></li>
                    """);
        checkOrder("p2/Class2.html",
            """
                    Link to <a href="#class2main"><code>local anchor</code></a>""",
            """
                    Plain link <a href="../p1/Class1.html#main">to Class1</a>.""");
        checkOrder("p2/package-summary.html",
            """
                    <a href="Class2.html#class2-sub-heading">See sub heading in p2.Class2</a>""");

        checkOrder("p2/doc-files/file.html",
            """
                    Plain link to <a href="../../p1/Class1.html#main">heading in p1.ClassA</a>.""",
            """
                    <a href="../Class2.html#class2main">See main heading in p2.ClassB</a>""");
    }

    @Test
    public void testModule(Path base) throws Exception {
        Path out = base.resolve("out");

        javadoc("-d", out.toString(),
                "--module-source-path", src.toString(),
                "--no-platform-links",
                "--module", "m1,m2",
                "m2/com.m2");

        checkExit(Exit.OK);
        checkOrder("m1/module-summary.html",
            """
                    <a href="../m2/com/m2/Class2.html#main-heading">See main heading in Class2</a>""");
        checkOrder("m1/com/m1/Class1.html",
            """
                    <a href="../../../m2/com/m2/Class2.html#sub"><code>sub heading in Class2</code></a>.""",
            """
                    <li><a href="../../../m2/com/m2/Class2.html#main-heading">See main heading in Class2</a></li>
                    <li><a href="../../module-summary.html#module-m1-heading">See heading in module m1</a></li>
                    """);
        checkOrder("m2/com/m2/Class2.html",
            """
                    Link to <a href="../../../m1/module-summary.html#module-m1-heading"><code>heading in module m1</code></a>""",
            """
                    Plain link to <a href="#sub">sub heading above</a>.""");
        checkOrder("m2/doc-files/file.html",
            """
                    Link to <a href="../com/m2/Class2.html#main-heading"><code>heading in Class2</code></a>.""",
            """
                    <li><a href="../../m1/module-summary.html#module-m1-heading">Heading in module m1</a></li>""");
    }

    @Test
    public void testMissingLabel(Path base) throws Exception {
        Path out = base.resolve("out");

        javadoc("-d", out.toString(),
                "-sourcepath", src.toString(),
                "--no-platform-links",
                "nolabel");

        checkExit(Exit.ERROR);
        checkOutput(Output.OUT, true, """
                    error: missing reference label
                    Link with missing label: {@link ##main}.
                                             ^
                    """,
            """
                    Class1.java:5: error: missing reference label
                    @see ##main
                    ^
                    """);
        checkOutput("nolabel/Class1.html", true, """
                    Link with missing label:\s
                    <details class="invalid-tag">
                    <summary>missing reference label</summary>
                    <pre>##main</pre>
                    </details>
                    .</div>
                    """,
           """
                    <details class="invalid-tag">
                    <summary>missing reference label</summary>
                    <pre>##main</pre>
                    """);
    }

    @Test
    public void testInvalidLink(Path base) throws Exception {
        Path out = base.resolve("out");

        javadoc("-d", out.toString(),
                "-sourcepath", src.toString(),
                "--no-platform-links",
                "inv");

        checkExit(Exit.ERROR);
        checkOutput(Output.OUT, true, "error: reference not found");
        checkOutput("inv/Class1.html", true, """
                     Invalid link to\s
                     <details class="invalid-tag">
                     <summary>invalid reference</summary>
                     <pre><code>main heading</code></pre>
                     </details>""");
    }

    void generatePackageSources() throws Exception {
        MethodBuilder mb = MethodBuilder.parse("public String method(String s) { return s; }")
                .setComments("""
                    @see p2.Class2##class2main See main heading in p2.Class2
                    @see p2##package-p2-heading See heading in p2
                    """);
        new ClassBuilder(tb, "p1.Class1")
                .setModifiers("public", "class")
                .setComments("""
                    <h2 id="main">Class1 Main</h2>
                    Link to {@link p2##package-p2-heading heading in package p2}
                    <h3>Class1 Sub</h3>
                    Plain link to {@linkplain p2.Class2##class2-sub-heading sub heading above}
                    """)
                .addMembers(mb)
                .write(src);
        new ClassBuilder(tb, "p2.Class2")
                .setModifiers("public", "class")
                .setComments("""
                    <h2 id="class2main">Class2 Main</h2>
                    Link to {@link ##class2main local anchor}
                    <h3>Class2 Sub</h3>
                    Plain link {@linkplain p1.Class1##main to Class1}.
                    """)
                .write(src);
        tb.writeFile(src.resolve("p2").resolve("package-info.java"),
                """
                    /**
                     * <h2>Package p2</h2>
                     *
                     * @see p2.Class2##class2-sub-heading See sub heading in p2.Class2
                     */
                    package p2;
                    """);
        Path docFiles = src.resolve("p2").resolve("doc-files");
        tb.writeFile(docFiles.resolve("file.html"),
                """
                    <html>
                    <head><title>Package p2 HTML File</title></head>
                    <body><h1>Package p2 HTML File</h1>
                    Plain link to {@linkplain p1.Class1##main heading in p1.ClassA}.
                    @see p2.Class2##class2main See main heading in p2.ClassB
                    </body>
                    </html>
                    """);
    }

    void generateModuleSources() throws Exception {
        new ModuleBuilder(tb, "m1")
                .exports("com.m1")
                .classes("""
                    package com.m1;
                    /**
                     * Link to the {@link m2/com.m2.Class2##sub sub heading in Class2}.
                     *
                     * @see m2/com.m2.Class2##main-heading See main heading in Class2
                     * @see m1/##module-m1-heading See heading in module m1
                     */
                    public class Class1 {}
                    """)
                .comment("""
                    <h2>Module m1</h2>
                    @see m2/com.m2.Class2##main-heading See main heading in Class2
                    """)
                .write(src);
        new ModuleBuilder(tb, "m2")
                .exports("com.m2")
                .classes("""
                    package com.m2;
                    /**
                     * <h2>Main</h2>
                     * Link to {@link m1/##module-m1-heading heading in module m1}
                     *
                     * <h3 id="sub">Sub</h3>
                     * Plain link to {@linkplain Class2##sub sub heading above}.
                     */
                    public class Class2 {}
                    """)
                .write(src);
        Path docFiles = src.resolve("m2").resolve("doc-files");
        tb.writeFile(docFiles.resolve("file.html"),
                """
                    <html>
                    <head><title>Module m2 HTML File</title></head>
                    <body><h1>Module m2 HTML File</h1>
                    Link to {@link com.m2.Class2##main-heading heading in Class2}.
                    @see m1/##module-m1-heading Heading in module m1
                    </body>
                    </html>
                    """);
    }

    void generateInvalidLinkSource() throws Exception {
        new ClassBuilder(tb, "inv.Class1")
                .setModifiers("public", "class")
                .setComments("""
                    <h2 id="main">Class1 Main</h2>
                    Invalid link to {@link #main main heading}.
                    """)
                .write(src);
    }

    void generateMissingLabelSource() throws Exception {
        new ClassBuilder(tb, "nolabel.Class1")
                .setModifiers("public", "class")
                .setComments("""
                    <h2 id="main">Class1 Main</h2>
                    Link with missing label: {@link ##main}.
                    @see ##main
                    """)
                .write(src);
    }
}