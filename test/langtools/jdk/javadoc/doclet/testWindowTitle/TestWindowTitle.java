/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8016675 8026736 8196202
 * @summary Test for window title.
 * @author Bhavesh Patel
 * @library ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build JavadocTester
 * @run main TestWindowTitle
 */
public class TestWindowTitle extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestWindowTitle tester = new TestWindowTitle();
        tester.runTests();
        tester.printSummary();
    }

    @Test
    void testJavaScriptChars() {
        // Window title with JavaScript special characters.
        String title = "Testing \"Window 'Title'\" with a \\ backslash and a / "
                + "forward slash and a \u00e8 unicode char also a    tab and also a "
                + "\t special character another \u0002 unicode)";

        javadoc("-d", "out-js-chars",
                "-windowtitle", title,
                "--frames",
                "-sourcepath", testSrc,
                "p1", "p2");
        checkExit(Exit.OK);

        checkOutput("overview-summary.html", true,
                "parent.document.title=\"Overview (Testing \\\"Window \\\'Title\\\'\\\" "
                + "with a \\\\ backslash and a / forward slash and a \\u00E8 unicode char "
                + "also a    tab and also a \\t special character another \\u0002 unicode))\";"
        );

        checkOutput("overview-summary.html", false,
                "parent.document.title=\"Overview (Testing \"Window \'Title\'\" "
                + "with a \\ backslash and a / forward slash and a \u00E8 unicode char "
                + "also a    tab and also a \t special character another \u0002 unicode))\";"
        );
    }

    @Test
    void testScriptTag() {
        // Window title with a script tag.
        String title = "Testing script tag in title </title><script>alert(\"Should not pop up\")</script>.";

        javadoc("-d", "out-script",
                "-windowtitle", title,
                "--frames",
                "-sourcepath", testSrc,
                "p1", "p2");
        checkExit(Exit.OK);

        checkOutput("overview-summary.html", true,
                "parent.document.title=\"Overview (Testing script tag in title alert"
                + "(\\\"Should not pop up\\\").)\";"
        );

        checkOutput("p2/C2.html", true,
                "parent.document.title=\"C2 (Testing script tag in title alert"
                + "(\\\"Should not pop up\\\").)\";"
        );

        checkOutput("overview-summary.html", false,
                "parent.document.title=\"Overview (Testing script tag in title </title><script>"
                + "alert(\\\"Should not pop up\\\")</script>.)\";"
        );

        checkOutput("p2/C2.html", false,
                "parent.document.title=\"C2 (Testing script tag in title </title><script>"
                + "alert(\\\"Should not pop up\\\")</script>.)\";"
        );
    }

    @Test
    void testHtmlTags() {
        // Window title with other HTML tags.
        String title = "Testing another <p>HTML</p> tag. Another <h1>tag</h1>. A "
                + "<span id=\"testTag\">tag with attributes</span>. <script and </p are not tags.";

        javadoc("-d", "out-html-tags",
                "-windowtitle", title,
                "--frames",
                "-sourcepath", testSrc,
                "p1", "p2");
        checkExit(Exit.OK);

        checkOutput("overview-summary.html", true,
            "parent.document.title=\"Overview (Testing another HTML tag. Another tag. A "
            + "tag with attributes. <script and </p are not tags.)\";"
        );

        checkOutput("overview-summary.html", false,
            "parent.document.title=\"Overview (Testing another <p>HTML</p> tag. Another "
            + "<h1>tag</h1>. A <span id=\"testTag\">tag with attributes</span>. <script and "
            + "</p are not tags.)\";"
        );
    }

    @Test
    void testHtmlEntities() {
        // Window title using entities.
        String title = "Testing entities &lt;script&gt;alert(\"Should not pop up\")&lt;/script&gt;.";

        javadoc("-d", "out-html-entities",
                "-windowtitle", title,
                "--frames",
                "-sourcepath", testSrc,
                "p1", "p2");

        checkOutput("overview-summary.html", true,
            "parent.document.title=\"Overview (Testing entities &lt;script&gt;alert(\\\"Should "
            + "not pop up\\\")&lt;/script&gt;.)\";"
        );

        checkOutput("overview-summary.html", false,
            "parent.document.title=\"Overview (Testing entities alert(\\\"Should not pop up\\\").)\";"
        );
    }

    @Test
    void testEmptyTags() {
        // Window title with just empty HTML tags.
        String title = "</title><script></script>";

        javadoc("-d", "out-empty-tags",
                "-windowtitle", title,
                "--frames",
                "-sourcepath", testSrc,
                "p1", "p2");

        checkOutput("overview-summary.html", true,
            "parent.document.title=\"Overview\";"
        );

        checkOutput("overview-summary.html", false,
            "parent.document.title=\"Overview (</title><script></script>)\";"
        );
    }

    @Test
    void testUnicode() {
        //Window title with unicode characters.
        String title = "Testing unicode \u003cscript\u003ealert(\"Should not pop up\")\u003c/script\u003e.";

        javadoc("-d", "out-unicode",
                "-windowtitle", title,
                "--frames",
                "-sourcepath", testSrc,
                "p1", "p2");
        checkExit(Exit.OK);

        checkOutput("overview-summary.html", true,
            "parent.document.title=\"Overview (Testing unicode alert(\\\"Should "
            + "not pop up\\\").)\";"
        );

        checkOutput("overview-summary.html", false,
            "parent.document.title=\"Overview (Testing unicode <script>alert(\\\"Should not pop up\\\")"
            + "</script>.)\";"
        );
    }

    @Test
    void testEmpty() {
        // An empty window title.
        String title = "";
        javadoc("-d", "out-empty",
                "-windowtitle", title,
                "--frames",
                "-sourcepath", testSrc, "p1", "p2");
        checkExit(Exit.OK);

        checkOutput("overview-summary.html", true,
                "parent.document.title=\"Overview\";"
        );
    }

    @Test
    void testDocTitle() {
        // Window title with JavaScript special characters, specified with -doctitle
        String title = "Testing \"Window 'Title'\" with a \\ backslash and a / "
                + "forward slash and a \u00e8 unicode char also a    tab and also a "
                + "\t special character another \u0002 unicode)";

        javadoc("-d", "out-doctitle",
                "-doctitle", title,
                "--frames",
                "-sourcepath", testSrc,
                "p1", "p2");
        checkExit(Exit.OK);

        checkOutput("overview-summary.html", false,
            "parent.document.title=\"Overview (Testing \\\"Window \\\'Title\\\'\\\" "
            + "with a \\\\ backslash and a / forward slash and a \\u00E8 unicode char "
            + "also a    tab and also a \\t special character another \\u0002 unicode)\";"
        );
    }
}
