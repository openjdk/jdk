/*
 * Copyright (c) 2004, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4665566 4855876 7025314 8012375 8015997 8016328 8024756
 * @summary  Verify that the output has the right javascript.
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester
 * @build    TestJavascript
 * @run main TestJavascript
 */

public class TestJavascript extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4665566-4855876-8012375";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg", SRC_DIR +
        "/TestJavascript.java"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        {BUG_ID + "/pkg/C.html",
            "<a href=\"../index.html?pkg/C.html\" target=\"_top\">Frames</a>"},
        {BUG_ID + "/TestJavascript.html",
            "<a href=\"index.html?TestJavascript.html\" target=\"_top\">Frames</a>"},
        {BUG_ID + "/index.html",
            "<script type=\"text/javascript\">\n" +
                        "    targetPage = \"\" + window.location.search;\n" +
            "    if (targetPage != \"\" && targetPage != \"undefined\")\n" +
            "        targetPage = targetPage.substring(1);\n" +
            "    if (targetPage.indexOf(\":\") != -1 || (targetPage != \"\" && !validURL(targetPage)))\n" +
            "        targetPage = \"undefined\";\n" +
            "    function validURL(url) {\n" +
            "        try {\n" +
            "            url = decodeURIComponent(url);\n" +
            "        }\n" +
            "        catch (error) {\n" +
            "            return false;\n" +
            "        }\n" +
            "        var pos = url.indexOf(\".html\");\n" +
            "        if (pos == -1 || pos != url.length - 5)\n" +
            "            return false;\n" +
            "        var allowNumber = false;\n" +
            "        var allowSep = false;\n" +
            "        var seenDot = false;\n" +
            "        for (var i = 0; i < url.length - 5; i++) {\n" +
            "            var ch = url.charAt(i);\n" +
            "            if ('a' <= ch && ch <= 'z' ||\n" +
            "                    'A' <= ch && ch <= 'Z' ||\n" +
            "                    ch == '$' ||\n" +
            "                    ch == '_' ||\n" +
            "                    ch.charCodeAt(0) > 127) {\n" +
            "                allowNumber = true;\n" +
            "                allowSep = true;\n" +
            "            } else if ('0' <= ch && ch <= '9'\n" +
            "                    || ch == '-') {\n" +
            "                if (!allowNumber)\n" +
            "                     return false;\n" +
            "            } else if (ch == '/' || ch == '.') {\n" +
            "                if (!allowSep)\n" +
            "                    return false;\n" +
            "                allowNumber = false;\n" +
            "                allowSep = false;\n" +
            "                if (ch == '.')\n" +
            "                     seenDot = true;\n" +
            "                if (ch == '/' && seenDot)\n" +
            "                     return false;\n" +
            "            } else {\n" +
            "                return false;\n" +
            "            }\n" +
            "        }\n" +
            "        return true;\n" +
            "    }\n" +
            "    function loadFrames() {\n" +
            "        if (targetPage != \"\" && targetPage != \"undefined\")\n" +
            "             top.classFrame.location = top.targetPage;\n" +
            "    }\n" +
            "</script>"},

        //Make sure title javascript only runs if is-external is not true
        {BUG_ID + "/pkg/C.html",
            "    try {\n" +
            "        if (location.href.indexOf('is-external=true') == -1) {\n" +
            "            parent.document.title=\"C\";\n" +
            "        }\n" +
            "    }\n" +
            "    catch(err) {\n" +
            "    }"},
    };

    private static final String[][] NEGATED_TEST = NO_TEST;

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestJavascript tester = new TestJavascript();
        tester.run(ARGS, TEST, NEGATED_TEST);
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
