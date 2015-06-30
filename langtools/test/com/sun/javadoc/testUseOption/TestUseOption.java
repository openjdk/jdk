/*
 * Copyright (c) 2002, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4496290 4985072 7006178 7068595 8016328 8050031 8048351 8081854
 * @summary A simple test to ensure class-use files are correct.
 * @author jamieh
 * @library ../lib
 * @modules jdk.javadoc
 * @build JavadocTester
 * @run main TestUseOption
 */

public class TestUseOption extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestUseOption tester = new TestUseOption();
        tester.runTests();
    }

    @Test
    void test1() {
        javadoc("-d", "out-1",
                "-sourcepath", testSrc,
                "-use",
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
          "<a href=\"../../pkg1/C1.html#methodInC1ReturningType--\">methodInC1ReturningType</a>"
        );
        checkOutput("pkg1/class-use/UsedInterface.html", true,
          "Classes in <a href=\"../../pkg1/package-summary.html\">pkg1</a> that implement " +
          "<a href=\"../../pkg1/UsedInterface.html\" title=\"interface in pkg1\">UsedInterface</a>"
        );
        checkOutput("pkg1/class-use/UsedInterfaceA.html", true,
          "Classes in <a href=\"../../pkg1/package-summary.html\">pkg1</a> that implement " +
          "<a href=\"../../pkg1/UsedInterfaceA.html\" title=\"interface in pkg1\">UsedInterfaceA</a>"
        );
        checkOutput("pkg1/class-use/UsedClass.html", false,
           "methodInC1Protected"
        );
        checkOutput("pkg1/class-use/UsedInterface.html", true,
           "<a href=\"../../pkg1/AnAbstract.html\" title=\"class in pkg1\">AnAbstract</a>"
        );
        checkOutput("pkg1/class-use/UsedInterface.html", true,
            "../../pkg1/C10.html#withReturningTypeParameters--"
        );
        checkOutput("pkg1/class-use/UsedInterface.html", true,
            "../../pkg1/C10.html#withTypeParametersOfType-java.lang.Class-"
        );
        checkOutput("pkg1/class-use/UsedInterface.html", true,
            "\"../../pkg1/package-summary.html\">pkg1</a> that return " +
            "<a href=\"../../pkg1/UsedInterface.html\" title=\"interface in pkg1\""
        );
        checkOutput("pkg1/class-use/UsedInterface.html", true,
            "<a href=\"../../pkg1/C10.html#addAll-pkg1.UsedInterface...-\">addAll</a>"
        );
        checkOutput("pkg1/class-use/UsedInterface.html", true,
            "<a href=\"../../pkg1/C10.html#create-pkg1.UsedInterfaceA-pkg1." +
            "UsedInterface-java.lang.String-\">"
        );
        checkOutput("pkg1/class-use/UsedInterface.html", true,
            "<a href=\"../../pkg1/C10.html#withTypeParametersOfType-java.lang.Class-\">" +
            "withTypeParametersOfType</a>"
        );
    }

    @Test
    void test2() {
        javadoc("-d", "out-2",
                "-sourcepath", testSrc,
                "-use",
                testSrc("C.java"), testSrc("UsedInC.java"));
        checkExit(Exit.OK);

        checkOutput("class-use/UsedInC.html", true,
                "Uses of <a href=\"../UsedInC.html\" title=\"class in &lt;Unnamed&gt;\">"
                + "UsedInC</a> in <a href=\"../package-summary.html\">&lt;Unnamed&gt;</a>"
        );
        checkOutput("class-use/UsedInC.html", true,
                "<li class=\"blockList\"><a name=\"unnamed.package\">"
        );
        checkOutput("package-use.html", true,
                "<td class=\"colOne\">"
                + "<a href=\"class-use/UsedInC.html#unnamed.package\">UsedInC</a>&nbsp;</td>"
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
                "<a href=\"../../unique/C1.html#umethod1-unique.UseMe-unique.UseMe:A-\">",
                "<a href=\"../../unique/C1.html#umethod2-unique.UseMe-unique.UseMe-\">",
                "<a href=\"../../unique/C1.html#umethod3-unique.UseMe-unique.UseMe-\">",
                "<a href=\"../../unique/C1.html#C1-unique.UseMe-unique.UseMe-\">");
    }
}
