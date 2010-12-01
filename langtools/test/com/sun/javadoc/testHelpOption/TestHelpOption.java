/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4934778 4777599
 * @summary  Make sure that the -help option works properly.  Make sure
 *           the help link appears in the documentation.
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester
 * @build    TestHelpOption
 * @run main TestHelpOption
 */

public class TestHelpOption extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4934778-4777599";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "-help",
            SRC_DIR + FS + "TestHelpOption.java"
    };

    private static final String[] ARGS2 = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR,
            SRC_DIR + FS + "TestHelpOption.java"
    };

    private static final String[][] TEST = {
        {STANDARD_OUTPUT, "-d "},
        {STANDARD_OUTPUT, "-use "},
        {STANDARD_OUTPUT, "-version "},
        {STANDARD_OUTPUT, "-author "},
        {STANDARD_OUTPUT, "-docfilessubdirs "},
        {STANDARD_OUTPUT, "-splitindex "},
        {STANDARD_OUTPUT, "-windowtitle "},
        {STANDARD_OUTPUT, "-doctitle "},
        {STANDARD_OUTPUT, "-header "},
        {STANDARD_OUTPUT, "-footer "},
        {STANDARD_OUTPUT, "-bottom "},
        {STANDARD_OUTPUT, "-link "},
        {STANDARD_OUTPUT, "-linkoffline "},
        {STANDARD_OUTPUT, "-excludedocfilessubdir "},
        {STANDARD_OUTPUT, "-group "},
        {STANDARD_OUTPUT, "-nocomment "},
        {STANDARD_OUTPUT, "-nodeprecated "},
        {STANDARD_OUTPUT, "-noqualifier "},
        {STANDARD_OUTPUT, "-nosince "},
        {STANDARD_OUTPUT, "-notimestamp "},
        {STANDARD_OUTPUT, "-nodeprecatedlist "},
        {STANDARD_OUTPUT, "-notree "},
        {STANDARD_OUTPUT, "-noindex "},
        {STANDARD_OUTPUT, "-nohelp "},
        {STANDARD_OUTPUT, "-nonavbar "},
        {STANDARD_OUTPUT, "-serialwarn "},
        {STANDARD_OUTPUT, "-tag "},
        {STANDARD_OUTPUT, "-taglet "},
        {STANDARD_OUTPUT, "-tagletpath "},
        {STANDARD_OUTPUT, "-charset "},
        {STANDARD_OUTPUT, "-helpfile "},
        {STANDARD_OUTPUT, "-linksource "},
        {STANDARD_OUTPUT, "-sourcetab "},
        {STANDARD_OUTPUT, "-keywords "},
        {STANDARD_OUTPUT, "-stylesheetfile "},
        {STANDARD_OUTPUT, "-docencoding "},
    };
    private static final String[][] NEGATED_TEST = NO_TEST;

    private static final String[][] TEST2 = {
        {BUG_ID + FS + "TestHelpOption.html",
            "<li><a href=\"help-doc.html\">Help</a></li>"
        },
    };
    private static final String[][] NEGATED_TEST2 = NO_TEST;

    //The help option should not crash the doclet.
    private static final int EXPECTED_EXIT_CODE = 0;

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestHelpOption tester = new TestHelpOption();
        int actualExitCode = run(tester, ARGS, TEST, NEGATED_TEST);
        tester.checkExitCode(EXPECTED_EXIT_CODE, actualExitCode);
        run(tester, ARGS2, TEST2, NEGATED_TEST2);
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
