/*
 * Copyright (c) 2004, 2013, Oracle and/or its affiliates. All rights reserved.
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
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg", SRC_DIR + FS + "TestJavascript.java"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        {BUG_ID + FS + "pkg" + FS + "C.html",
            "<a href=\"../index.html?pkg/C.html\" target=\"_top\">Frames</a>"},
        {BUG_ID + FS + "TestJavascript.html",
            "<a href=\"index.html?TestJavascript.html\" target=\"_top\">Frames</a>"},
        {BUG_ID + FS + "index.html",
            "<script type=\"text/javascript\">" + NL +
                        "    targetPage = \"\" + window.location.search;" + NL +
            "    if (targetPage != \"\" && targetPage != \"undefined\")" + NL +
            "        targetPage = targetPage.substring(1);" + NL +
            "    if (targetPage.indexOf(\":\") != -1 || (targetPage != \"\" && !validURL(targetPage)))" + NL +
            "        targetPage = \"undefined\";" + NL +
            "    function validURL(url) {" + NL +
            "        try {" + NL +
            "            url = decodeURIComponent(url);" + NL +
            "        }" + NL +
            "        catch (error) {" + NL +
            "            return false;" + NL +
            "        }" + NL +
            "        var pos = url.indexOf(\".html\");" + NL +
            "        if (pos == -1 || pos != url.length - 5)" + NL +
            "            return false;" + NL +
            "        var allowNumber = false;" + NL +
            "        var allowSep = false;" + NL +
            "        var seenDot = false;" + NL +
            "        for (var i = 0; i < url.length - 5; i++) {" + NL +
            "            var ch = url.charAt(i);" + NL +
            "            if ('a' <= ch && ch <= 'z' ||" + NL +
            "                    'A' <= ch && ch <= 'Z' ||" + NL +
            "                    ch == '$' ||" + NL +
            "                    ch == '_' ||" + NL +
            "                    ch.charCodeAt(0) > 127) {" + NL +
            "                allowNumber = true;" + NL +
            "                allowSep = true;" + NL +
            "            } else if ('0' <= ch && ch <= '9'" + NL +
            "                    || ch == '-') {" + NL +
            "                if (!allowNumber)" + NL +
            "                     return false;" + NL +
            "            } else if (ch == '/' || ch == '.') {" + NL +
            "                if (!allowSep)" + NL +
            "                    return false;" + NL +
            "                allowNumber = false;" + NL +
            "                allowSep = false;" + NL +
            "                if (ch == '.')" + NL +
            "                     seenDot = true;" + NL +
            "                if (ch == '/' && seenDot)" + NL +
            "                     return false;" + NL +
            "            } else {" + NL +
            "                return false;" + NL +
            "            }" + NL +
            "        }" + NL +
            "        return true;" + NL +
            "    }" + NL +
            "    function loadFrames() {" + NL +
            "        if (targetPage != \"\" && targetPage != \"undefined\")" + NL +
            "             top.classFrame.location = top.targetPage;" + NL +
            "    }" + NL +
            "</script>"},

        //Make sure title javascript only runs if is-external is not true
        {BUG_ID + FS + "pkg" + FS + "C.html",
            "    try {" + NL +
            "        if (location.href.indexOf('is-external=true') == -1) {" + NL +
            "            parent.document.title=\"C\";" + NL +
            "        }" + NL +
            "    }" + NL +
            "    catch(err) {" + NL +
            "    }"},
    };

    private static final String[][] NEGATED_TEST = NO_TEST;

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestJavascript tester = new TestJavascript();
        run(tester, ARGS, TEST, NEGATED_TEST);
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
