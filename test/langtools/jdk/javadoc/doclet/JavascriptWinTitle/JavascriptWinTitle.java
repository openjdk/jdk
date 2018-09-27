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
 * @bug 4645058 4747738 4855054 8024756 8141492 8196202 8205593
 * @summary  Javascript IE load error when linked by -linkoffline
 *           Window title shouldn't change when loading left frames (javascript)
 * @author dkramer
 * @library ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build JavadocTester
 * @run main JavascriptWinTitle
 */

public class JavascriptWinTitle extends JavadocTester {

    public static void main(String... args) throws Exception {
        JavascriptWinTitle tester = new JavascriptWinTitle();
        tester.runTests();
    }

    @Test
    void test() {
        javadoc("-d", "out",
                "-source", "8",
                "--frames",
                "-doctitle", "Document Title",
                "-windowtitle", "Window Title",
                "-overview", testSrc("overview.html"),
                "-linkoffline", "http://java.sun.com/j2se/1.4/docs/api", testSrc,
                "-sourcepath", testSrc,
                "p1", "p2");
        checkExit(Exit.OK);
        checkOutput("overview-summary.html", true,
                "<script type=\"text/javascript\">",
                "<body>");

        // Test that "onload" is not present in BODY tag:
        checkOutput("p1/package-summary.html", true, "<body>");
        checkOutput("overview-frame.html", true, "<body>");
        checkOutput("allclasses-frame.html", true, "<body>");
        checkOutput("p1/package-frame.html", true, "<body>");

        // Test that win title javascript is followed by NOSCRIPT code.
        checkOutput("p1/C.html", true,
                "<script type=\"text/javascript\"><!--\n"
                + "    try {\n"
                + "        if (location.href.indexOf('is-external=true') == -1) {\n"
                + "            parent.document.title=\"C (Window Title)\";\n"
                + "        }\n"
                + "    }\n"
                + "    catch(err) {\n"
                + "    }\n"
                + "//-->\n");
    }
}
