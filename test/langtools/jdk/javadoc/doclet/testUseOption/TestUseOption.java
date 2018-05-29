/*
 * Copyright (c) 2002, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4496290 4985072 7006178 7068595 8016328 8050031 8048351 8081854 8071982 8162363 8175200 8186332
 *      8182765 8196202
 * @summary A simple test to ensure class-use files are correct.
 * @author jamieh
 * @library ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build JavadocTester
 * @run main TestUseOption
 */

public class TestUseOption extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestUseOption tester = new TestUseOption();
        tester.setAutomaticCheckLinks(false); // @ignore JDK-8202626
        tester.runTests();
    }

    @Test
    void test1() {
        javadoc("-d", "out-1",
                "-sourcepath", testSrc,
                "-use",
                "--frames",
                "pkg1", "pkg2");
        checkExit(Exit.OK);

        // Eight tests for class use.
        for (int i = 1; i <= 8; i++) {
            checkOutput("pkg1/class-use/C1.html", true,
                    "Test " + i + " passes");
        }

        // Three more tests for package use.
        for (int i = 1; i <= 3; i++) {
            checkOutput("pkg1/package-use.html", true,
                    "Test " + i + " passes");
        }

        checkOrder("pkg1/class-use/UsedClass.html",
                "Field in C1.",
                "Field in C2.",
                "Field in C4.",
                "Field in C5.",
                "Field in C6.",
                "Field in C7.",
                "Field in C8.",
                "Method in C1.",
                "Method in C2.",
                "Method in C4.",
                "Method in C5.",
                "Method in C6.",
                "Method in C7.",
                "Method in C8."
        );

        checkOutput("pkg2/class-use/C3.html", true,
                "<a href=\"../../index.html?pkg2/class-use/C3.html\" target=\"_top\">"
                + "Frames</a></li>"
        );
        checkOutput("pkg1/class-use/UsedClass.html", true,
          "that return types with arguments of type"
        );
        checkOutput("pkg1/class-use/UsedClass.html", true,
          "<a href=\"../C1.html#methodInC1ReturningType()\">methodInC1ReturningType</a>"
        );
        checkOutput("pkg1/class-use/UsedInterface.html", true,
          "Classes in <a href=\"../package-summary.html\">pkg1</a> that implement " +
          "<a href=\"../UsedInterface.html\" title=\"interface in pkg1\">UsedInterface</a>"
        );
        checkOutput("pkg1/class-use/UsedInterfaceA.html", true,
          "Classes in <a href=\"../package-summary.html\">pkg1</a> that implement " +
          "<a href=\"../UsedInterfaceA.html\" title=\"interface in pkg1\">UsedInterfaceA</a>"
        );
        checkOutput("pkg1/class-use/UsedClass.html", false,
           "methodInC1Protected"
        );
        checkOutput("pkg1/class-use/UsedInterface.html", true,
           "<a href=\"../AnAbstract.html\" title=\"class in pkg1\">AnAbstract</a>"
        );
        checkOutput("pkg1/class-use/UsedInterface.html", true,
            "../C10.html#withReturningTypeParameters()"
        );
        checkOutput("pkg1/class-use/UsedInterface.html", true,
            "../C10.html#withTypeParametersOfType(java.lang.Class)"
        );
        checkOutput("pkg1/class-use/UsedInterface.html", true,
            "\"../package-summary.html\">pkg1</a> that return " +
            "<a href=\"../UsedInterface.html\" title=\"interface in pkg1\""
        );
        checkOutput("pkg1/class-use/UsedInterface.html", true,
            "<a href=\"../C10.html#addAll(pkg1.UsedInterface...)\">addAll</a>"
        );
        checkOutput("pkg1/class-use/UsedInterface.html", true,
            "<a href=\"../C10.html#create(pkg1.UsedInterfaceA,pkg1." +
            "UsedInterface,java.lang.String)\">"
        );
        checkOutput("pkg1/class-use/UsedInterface.html", true,
            "<a href=\"../C10.html#withTypeParametersOfType(java.lang.Class)\">" +
            "withTypeParametersOfType</a>"
        );
        checkOutput("pkg1/class-use/UsedInterface.html", true,
            "Subinterfaces of <a href=\"../UsedInterface.html\" title=\"interface in pkg1\">"
            + "UsedInterface</a> in <a href=\"../package-summary.html\">pkg1",
            "<td class=\"colFirst\"><code>interface&nbsp;</code></td>\n<th class=\"colSecond\" scope=\"row\">"
            + "<code><span class=\"memberNameLink\"><a href=\"../SubInterface.html\" "
            + "title=\"interface in pkg1\">SubInterface</a>&lt;T&gt;</span></code></th>"
        );
        checkOutput("pkg1/class-use/UsedThrowable.html", true,
            "Methods in <a href=\"../package-summary.html\">pkg1</a> that throw "
            + "<a href=\"../UsedThrowable.html\" title=\"class in pkg1\">UsedThrowable</a>",
            "<td class=\"colFirst\"><code>void</code></td>\n<th class=\"colSecond\" scope=\"row\"><span class="
            + "\"typeNameLabel\">C1.</span><code><span class=\"memberNameLink\">"
            + "<a href=\"../C1.html#methodInC1ThrowsThrowable()\">methodInC1ThrowsThrowable"
            + "</a></span>()</code></th>"
        );
    }

    @Test
    void test1_html4() {
        javadoc("-d", "out-1-html4",
                "-html4",
                "-sourcepath", testSrc,
                "-use",
                "pkg1", "pkg2");
        checkExit(Exit.OK);

        checkOutput("pkg1/class-use/UsedClass.html", true,
          "<a href=\"../C1.html#methodInC1ReturningType--\">methodInC1ReturningType</a>"
        );
        checkOutput("pkg1/class-use/UsedInterface.html", true,
            "../C10.html#withReturningTypeParameters--"
        );
        checkOutput("pkg1/class-use/UsedInterface.html", true,
            "../C10.html#withTypeParametersOfType-java.lang.Class-"
        );
        checkOutput("pkg1/class-use/UsedInterface.html", true,
            "<a href=\"../C10.html#addAll-pkg1.UsedInterface...-\">addAll</a>"
        );
        checkOutput("pkg1/class-use/UsedInterface.html", true,
            "<a href=\"../C10.html#create-pkg1.UsedInterfaceA-pkg1." +
            "UsedInterface-java.lang.String-\">"
        );
        checkOutput("pkg1/class-use/UsedInterface.html", true,
            "<a href=\"../C10.html#withTypeParametersOfType-java.lang.Class-\">" +
            "withTypeParametersOfType</a>"
        );
        checkOutput("pkg1/class-use/UsedThrowable.html", true,
            "Methods in <a href=\"../package-summary.html\">pkg1</a> that throw "
            + "<a href=\"../UsedThrowable.html\" title=\"class in pkg1\">UsedThrowable</a>",
            "<td class=\"colFirst\"><code>void</code></td>\n<th class=\"colSecond\" scope=\"row\"><span class="
            + "\"typeNameLabel\">C1.</span><code><span class=\"memberNameLink\">"
            + "<a href=\"../C1.html#methodInC1ThrowsThrowable--\">methodInC1ThrowsThrowable"
            + "</a></span>()</code></th>"
        );
    }

    @Test
    void test2() {
        javadoc("-d", "out-2",
                "-sourcepath", testSrc,
                "-use",
                testSrc("C.java"), testSrc("UsedInC.java"), "pkg3");
        checkExit(Exit.OK);

        checkOutput("class-use/UsedInC.html", true,
                "Uses of <a href=\"../UsedInC.html\" title=\"class in &lt;Unnamed&gt;\">"
                + "UsedInC</a> in <a href=\"../package-summary.html\">&lt;Unnamed&gt;</a>"
        );
        checkOutput("class-use/UsedInC.html", true,
                "<li class=\"blockList\">\n"
                + "<section role=\"region\"><a id=\"unnamed.package\">"
        );
        checkOutput("package-use.html", true,
                "<th class=\"colFirst\" scope=\"row\">"
                + "<a href=\"class-use/UsedInC.html#unnamed.package\">UsedInC</a></th>",
                "<th class=\"colFirst\" scope=\"row\"><a href=\"#%3CUnnamed%3E\">&lt;Unnamed&gt;</a></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>"
        );
    }

    @Test
    void test2_html4() {
        javadoc("-d", "out-2-html4",
                "-html4",
                "-sourcepath", testSrc,
                "-use",
                testSrc("C.java"), testSrc("UsedInC.java"), "pkg3");
        checkExit(Exit.OK);

        checkOutput("class-use/UsedInC.html", true,
                "<li class=\"blockList\"><a name=\"unnamed.package\">"
        );
        checkOutput("package-use.html", true,
                "<th class=\"colFirst\" scope=\"row\"><a href=\"#-Unnamed-\">&lt;Unnamed&gt;</a></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>"
        );
    }

    @Test
    void test3() {
        javadoc("-d", "out-3",
                "-sourcepath", testSrc,
                "-use",
                "-package", "unique");
        checkExit(Exit.OK);
        checkUnique("unique/class-use/UseMe.html",
                "<a href=\"../C1.html#umethod1(unique.UseMe,unique.UseMe%5B%5D)\">",
                "<a href=\"../C1.html#umethod2(unique.UseMe,unique.UseMe)\">",
                "<a href=\"../C1.html#umethod3(unique.UseMe,unique.UseMe)\">",
                "<a href=\"../C1.html#%3Cinit%3E(unique.UseMe,unique.UseMe)\">");
    }

    @Test
    void test3_html4() {
        javadoc("-d", "out-3-html4",
                "-html4",
                "-sourcepath", testSrc,
                "-use",
                "-package", "unique");
        checkExit(Exit.OK);
        checkUnique("unique/class-use/UseMe.html",
                "<a href=\"../C1.html#umethod1-unique.UseMe-unique.UseMe:A-\">",
                "<a href=\"../C1.html#umethod2-unique.UseMe-unique.UseMe-\">",
                "<a href=\"../C1.html#umethod3-unique.UseMe-unique.UseMe-\">",
                "<a href=\"../C1.html#C1-unique.UseMe-unique.UseMe-\">");
    }
}
