/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8016675 8026736
 * @summary Test for window title.
 * @author Bhavesh Patel
 * @library ../lib/
 * @build JavadocTester TestWindowTitle
 * @run main TestWindowTitle
 */

public class TestWindowTitle extends JavadocTester {

    private static final String BUG_ID = "8016675";
    //Window title with JavaScript special characters.
    private static final String TITLE_JS_CHARS =
            "Testing \"Window 'Title'\" with a \\ backslash and a / " +
            "forward slash and a \u00e8 unicode char also a    tab and also a " +
            "\t special character another \u0002 unicode)";
    private static final String[] ARGS_JS_CHARS = new String[]{
        "-d", BUG_ID + "-1", "-windowtitle", TITLE_JS_CHARS, "-sourcepath", SRC_DIR, "p1", "p2"
    };
    private static final String[][] TEST_JS_CHARS = {
        {BUG_ID + "-1" + FS + "overview-summary.html",
            "parent.document.title=\"Overview (Testing \\\"Window \\\'Title\\\'\\\" " +
            "with a \\\\ backslash and a / forward slash and a \\u00E8 unicode char " +
            "also a    tab and also a \\t special character another \\u0002 unicode))\";"
        },
    };
    private static final String[][] NEG_TEST_JS_CHARS = {
        {BUG_ID + "-1" + FS + "overview-summary.html",
            "parent.document.title=\"Overview (Testing \"Window \'Title\'\" " +
            "with a \\ backslash and a / forward slash and a \u00E8 unicode char " +
            "also a    tab and also a \t special character another \u0002 unicode))\";"
        }
    };

    //Window title with a script tag.
    private static final String TITLE_SCRIPT_TAG =
            "Testing script tag in title </title><script>alert(\"Should not pop up\")</script>.";
    private static final String[] ARGS_SCRIPT_TAG = new String[]{
        "-d", BUG_ID + "-2", "-windowtitle", TITLE_SCRIPT_TAG, "-sourcepath", SRC_DIR, "p1", "p2"
    };
    private static final String[][] TEST_SCRIPT_TAG = {
        {BUG_ID + "-2" + FS + "overview-summary.html",
            "parent.document.title=\"Overview (Testing script tag in title alert" +
            "(\\\"Should not pop up\\\").)\";"
        },
        {BUG_ID + "-2" + FS + "p2" + FS + "C2.html",
            "parent.document.title=\"C2 (Testing script tag in title alert" +
            "(\\\"Should not pop up\\\").)\";"
        }
    };
    private static final String[][] NEG_TEST_SCRIPT_TAG = {
        {BUG_ID + "-2" + FS + "overview-summary.html",
            "parent.document.title=\"Overview (Testing script tag in title </title><script>" +
            "alert(\\\"Should not pop up\\\")</script>.)\";"
        },
        {BUG_ID + "-2" + FS + "p2" + FS + "C2.html",
            "parent.document.title=\"C2 (Testing script tag in title </title><script>" +
            "alert(\\\"Should not pop up\\\")</script>.)\";"
        }
    };

    //Window title with other HTML tags.
    private static final String TITLE_HTML_TAGS =
            "Testing another <p>HTML</p> tag. Another <h1>tag</h1>. A " +
            "<span id=\"testTag\">tag with attributes</span>. <script and </p are not tags.";
    private static final String[] ARGS_HTML_TAGS = new String[]{
        "-d", BUG_ID + "-3", "-windowtitle", TITLE_HTML_TAGS, "-sourcepath", SRC_DIR, "p1", "p2"
    };
    private static final String[][] TEST_HTML_TAGS = {
        {BUG_ID + "-3" + FS + "overview-summary.html",
            "parent.document.title=\"Overview (Testing another HTML tag. Another tag. A " +
            "tag with attributes. <script and </p are not tags.)\";"
        }
    };
    private static final String[][] NEG_TEST_HTML_TAGS = {
        {BUG_ID + "-3" + FS + "overview-summary.html",
            "parent.document.title=\"Overview (Testing another <p>HTML</p> tag. Another " +
            "<h1>tag</h1>. A <span id=\"testTag\">tag with attributes</span>. <script and " +
            "</p are not tags.)\";"
        }
    };

    //Window title using entities.
    private static final String TITLE_HTML_ENTITIES =
            "Testing entities &lt;script&gt;alert(\"Should not pop up\")&lt;/script&gt;.";
    private static final String[] ARGS_HTML_ENTITIES = new String[]{
        "-d", BUG_ID + "-4", "-windowtitle", TITLE_HTML_ENTITIES, "-sourcepath", SRC_DIR, "p1", "p2"
    };
    private static final String[][] TEST_HTML_ENTITIES = {
        {BUG_ID + "-4" + FS + "overview-summary.html",
            "parent.document.title=\"Overview (Testing entities &lt;script&gt;alert(\\\"Should " +
            "not pop up\\\")&lt;/script&gt;.)\";"
        }
    };
    private static final String[][] NEG_TEST_HTML_ENTITIES = {
        {BUG_ID + "-4" + FS  + "overview-summary.html",
            "parent.document.title=\"Overview (Testing entities alert(\\\"Should not pop up\\\").)\";"
        }
    };

    //Window title with just empty HTML tags.
    private static final String TITLE_EMPTY_TAGS =
            "</title><script></script>";
    private static final String[] ARGS_EMPTY_TAGS = new String[]{
        "-d", BUG_ID + "-5", "-windowtitle", TITLE_EMPTY_TAGS, "-sourcepath", SRC_DIR, "p1", "p2"
    };
    private static final String[][] TEST_EMPTY_TAGS = {
        {BUG_ID + "-5" + FS + "overview-summary.html",
            "parent.document.title=\"Overview\";"
        }
    };
    private static final String[][] NEG_TEST_EMPTY_TAGS = {
        {BUG_ID + "-5" + FS + "overview-summary.html",
            "parent.document.title=\"Overview (</title><script></script>)\";"
        }
    };

    //Window title with unicode characters.
    private static final String TITLE_UNICODE_CHARS =
            "Testing unicode \u003cscript\u003ealert(\"Should not pop up\")\u003c/script\u003e.";
    private static final String[] ARGS_UNICODE_CHARS = new String[]{
        "-d", BUG_ID + "-6", "-windowtitle", TITLE_UNICODE_CHARS, "-sourcepath", SRC_DIR, "p1", "p2"
    };
    private static final String[][] TEST_UNICODE_CHARS = {
        {BUG_ID + "-6" + FS + "overview-summary.html",
            "parent.document.title=\"Overview (Testing unicode alert(\\\"Should " +
            "not pop up\\\").)\";"
        }
    };
    private static final String[][] NEG_TEST_UNICODE_CHARS = {
        {BUG_ID + "-6" + FS + "overview-summary.html",
            "parent.document.title=\"Overview (Testing unicode <script>alert(\\\"Should not pop up\\\")" +
            "</script>.)\";"
        }
    };

    //An empty window title.
    private static final String TITLE_EMPTY =
            "";
    private static final String[] ARGS_EMPTY_TITLE = new String[]{
        "-d", BUG_ID + "-7", "-windowtitle", TITLE_EMPTY, "-sourcepath", SRC_DIR, "p1", "p2"
    };
    private static final String[][] TEST_EMPTY = {
        {BUG_ID + "-7" + FS + "overview-summary.html",
            "parent.document.title=\"Overview\";"
        }
    };

    //Test doctitle.
    private static final String[] ARGS_DOCTITLE = new String[]{
        "-d", BUG_ID + "-8", "-doctitle", TITLE_JS_CHARS, "-sourcepath", SRC_DIR, "p1", "p2"
    };
    private static final String[][] NEG_TEST_DOCTITLE = {
        {BUG_ID + "-8" + FS + "overview-summary.html",
            "parent.document.title=\"Overview (Testing \\\"Window \\\'Title\\\'\\\" " +
            "with a \\\\ backslash and a / forward slash and a \\u00E8 unicode char " +
            "also a    tab and also a \\t special character another \\u0002 unicode)\";"
        },
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestWindowTitle tester = new TestWindowTitle();
        run(tester, ARGS_JS_CHARS, TEST_JS_CHARS, NEG_TEST_JS_CHARS);
        run(tester, ARGS_SCRIPT_TAG, TEST_SCRIPT_TAG, NEG_TEST_SCRIPT_TAG);
        run(tester, ARGS_HTML_TAGS, TEST_HTML_TAGS, NEG_TEST_HTML_TAGS);
        run(tester, ARGS_HTML_ENTITIES, TEST_HTML_ENTITIES, NEG_TEST_HTML_ENTITIES);
        run(tester, ARGS_EMPTY_TAGS, TEST_EMPTY_TAGS, NEG_TEST_EMPTY_TAGS);
        run(tester, ARGS_UNICODE_CHARS, TEST_UNICODE_CHARS, NEG_TEST_UNICODE_CHARS);
        run(tester, ARGS_EMPTY_TITLE, TEST_EMPTY, NO_TEST);
        run(tester, ARGS_DOCTITLE, NO_TEST, NEG_TEST_DOCTITLE);
        tester.printSummary();
    }

    /**
     * {@inheritDoc}
     */
    public String getBugId() {
        return BUG_ID;
    }

    /**
     * {@inheritDoc}
     */
    public String getBugName() {
        return getClass().getName();
    }
}
