/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8005092 6469562
 * @summary  Test repeated annotations output.
 * @author   bpatel
 * @library  ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    JavadocTester
 * @run main TestRepeatedAnnotations
 */

public class TestRepeatedAnnotations extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestRepeatedAnnotations tester = new TestRepeatedAnnotations();
        tester.runTests();
    }

    @Test
    void test() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "pkg", "pkg1");
        checkExit(Exit.OK);

        checkOutput("pkg/C.html", true,
                "<a href=\"../pkg/ContaineeSynthDoc.html\" "
                + "title=\"annotation in pkg\">@ContaineeSynthDoc</a> "
                + "<a href=\"../pkg/ContaineeSynthDoc.html\" "
                + "title=\"annotation in pkg\">@ContaineeSynthDoc</a>",
                "<a href=\"../pkg/ContaineeRegDoc.html\" "
                + "title=\"annotation in pkg\">@ContaineeRegDoc</a> "
                + "<a href=\"../pkg/ContaineeRegDoc.html\" "
                + "title=\"annotation in pkg\">@ContaineeRegDoc</a>",
                "<a href=\"../pkg/RegContainerDoc.html\" "
                + "title=\"annotation in pkg\">@RegContainerDoc</a>"
                + "({"
                + "<a href=\"../pkg/RegContaineeNotDoc.html\" "
                + "title=\"annotation in pkg\">@RegContaineeNotDoc</a>,"
                + "<a href=\"../pkg/RegContaineeNotDoc.html\" "
                + "title=\"annotation in pkg\">@RegContaineeNotDoc</a>})",
                "<a href=\"../pkg/ContaineeSynthDoc.html\" "
                + "title=\"annotation in pkg\">@ContaineeSynthDoc</a> "
                + "<a href=\"../pkg/ContaineeSynthDoc.html\" "
                + "title=\"annotation in pkg\">@ContaineeSynthDoc</a> "
                + "<a href=\"../pkg/ContaineeSynthDoc.html\" "
                + "title=\"annotation in pkg\">@ContaineeSynthDoc</a>",
                "<a href=\"../pkg/ContainerSynthDoc.html\" "
                + "title=\"annotation in pkg\">@ContainerSynthDoc</a>("
                + ""
                + "<a href=\"../pkg/ContaineeSynthDoc.html\" "
                + "title=\"annotation in pkg\">@ContaineeSynthDoc</a>)",
                "<a href=\"../pkg/ContaineeSynthDoc.html\" "
                + "title=\"annotation in pkg\">@ContaineeSynthDoc</a> "
                + "<a href=\"../pkg/ContaineeSynthDoc.html\" "
                + "title=\"annotation in pkg\">@ContaineeSynthDoc</a>");

        checkOutput("pkg/D.html", true,
                "<a href=\"../pkg/RegDoc.html\" title=\"annotation in pkg\">@RegDoc</a>"
                + "(<a href=\"../pkg/RegDoc.html#x--\">x</a>=1)",
                "<a href=\"../pkg/RegArryDoc.html\" title=\"annotation in pkg\">@RegArryDoc</a>"
                + "(<a href=\"../pkg/RegArryDoc.html#y--\">y</a>=1)",
                "<a href=\"../pkg/RegArryDoc.html\" title=\"annotation in pkg\">@RegArryDoc</a>"
                + "(<a href=\"../pkg/RegArryDoc.html#y--\">y</a>={1,2})",
                "<a href=\"../pkg/NonSynthDocContainer.html\" "
                + "title=\"annotation in pkg\">@NonSynthDocContainer</a>"
                + "("
                + "<a href=\"../pkg/RegArryDoc.html\" title=\"annotation in pkg\">@RegArryDoc</a>)");

        checkOutput("pkg1/C.html", true,
                "<a href=\"../pkg1/RegContainerValDoc.html\" "
                + "title=\"annotation in pkg1\">@RegContainerValDoc</a>"
                + "(<a href=\"../pkg1/RegContainerValDoc.html#value--\">value</a>={"
                + "<a href=\"../pkg1/RegContaineeNotDoc.html\" "
                + "title=\"annotation in pkg1\">@RegContaineeNotDoc</a>,"
                + "<a href=\"../pkg1/RegContaineeNotDoc.html\" "
                + "title=\"annotation in pkg1\">@RegContaineeNotDoc</a>},"
                + "<a href=\"../pkg1/RegContainerValDoc.html#y--\">y</a>=3)",
                "<a href=\"../pkg1/ContainerValDoc.html\" "
                + "title=\"annotation in pkg1\">@ContainerValDoc</a>"
                + "(<a href=\"../pkg1/ContainerValDoc.html#value--\">value</a>={"
                + "<a href=\"../pkg1/ContaineeNotDoc.html\" "
                + "title=\"annotation in pkg1\">@ContaineeNotDoc</a>,"
                + "<a href=\"../pkg1/ContaineeNotDoc.html\" "
                + "title=\"annotation in pkg1\">@ContaineeNotDoc</a>},"
                + "<a href=\"../pkg1/ContainerValDoc.html#x--\">x</a>=1)");

        checkOutput("pkg/C.html", false,
                "<a href=\"../pkg/RegContaineeDoc.html\" "
                + "title=\"annotation in pkg\">@RegContaineeDoc</a> "
                + "<a href=\"../pkg/RegContaineeDoc.html\" "
                + "title=\"annotation in pkg\">@RegContaineeDoc</a>",
                "<a href=\"../pkg/RegContainerNotDoc.html\" "
                + "title=\"annotation in pkg\">@RegContainerNotDoc</a>"
                + "(<a href=\"../pkg/RegContainerNotDoc.html#value--\">value</a>={"
                + "<a href=\"../pkg/RegContaineeNotDoc.html\" "
                + "title=\"annotation in pkg\">@RegContaineeNotDoc</a>,"
                + "<a href=\"../pkg/RegContaineeNotDoc.html\" "
                + "title=\"annotation in pkg\">@RegContaineeNotDoc</a>})");

        checkOutput("pkg1/C.html", false,
                "<a href=\"../pkg1/ContaineeSynthDoc.html\" "
                + "title=\"annotation in pkg1\">@ContaineeSynthDoc</a> "
                + "<a href=\"../pkg1/ContaineeSynthDoc.html\" "
                + "title=\"annotation in pkg1\">@ContaineeSynthDoc</a>",
                "<a href=\"../pkg1/RegContainerValNotDoc.html\" "
                + "title=\"annotation in pkg1\">@RegContainerValNotDoc</a>"
                + "(<a href=\"../pkg1/RegContainerValNotDoc.html#value--\">value</a>={"
                + "<a href=\"../pkg1/RegContaineeDoc.html\" "
                + "title=\"annotation in pkg1\">@RegContaineeDoc</a>,"
                + "<a href=\"../pkg1/RegContaineeDoc.html\" "
                + "title=\"annotation in pkg1\">@RegContaineeDoc</a>},"
                + "<a href=\"../pkg1/RegContainerValNotDoc.html#y--\">y</a>=4)",
                "<a href=\"../pkg1/ContainerValNotDoc.html\" "
                + "title=\"annotation in pkg1\">@ContainerValNotDoc</a>"
                + "(<a href=\"../pkg1/ContainerValNotDoc.html#value--\">value</a>={"
                + "<a href=\"../pkg1/ContaineeNotDoc.html\" "
                + "title=\"annotation in pkg1\">@ContaineeNotDoc</a>,"
                + "<a href=\"../pkg1/ContaineeNotDoc.html\" "
                + "title=\"annotation in pkg1\">@ContaineeNotDoc</a>},"
                + "<a href=\"../pkg1/ContainerValNotDoc.html#x--\">x</a>=2)",
                "<a href=\"../pkg1/ContainerSynthNotDoc.html\" "
                + "title=\"annotation in pkg1\">@ContainerSynthNotDoc</a>("
                + "<a href=\"../pkg1/ContainerSynthNotDoc.html#value--\">value</a>="
                + "<a href=\"../pkg1/ContaineeSynthDoc.html\" "
                + "title=\"annotation in pkg1\">@ContaineeSynthDoc</a>)");
    }
}
