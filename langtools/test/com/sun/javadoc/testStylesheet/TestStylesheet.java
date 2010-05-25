/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4494033
 * @summary  Run tests on doclet stylesheet.
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester
 * @build    TestStylesheet
 * @run main TestStylesheet
 */

public class TestStylesheet extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4494033";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        {BUG_ID + FS + "stylesheet.css",
                "body { background-color: #FFFFFF; color:#000000 }"},
        {BUG_ID + FS + "stylesheet.css",
                ".TableHeadingColor     { background: #CCCCFF; color:#000000 }"},
        {BUG_ID + FS + "stylesheet.css",
                ".TableSubHeadingColor  { background: #EEEEFF; color:#000000 }"},
        {BUG_ID + FS + "stylesheet.css",
                ".TableRowColor         { background: #FFFFFF; color:#000000 }"},
        {BUG_ID + FS + "stylesheet.css",
                ".FrameTitleFont   { font-size: 100%; font-family: Helvetica, Arial, sans-serif; color:#000000 }"},
        {BUG_ID + FS + "stylesheet.css",
                ".FrameHeadingFont { font-size:  90%; font-family: Helvetica, Arial, sans-serif; color:#000000 }"},
        {BUG_ID + FS + "stylesheet.css",
                ".FrameItemFont    { font-size:  90%; font-family: Helvetica, Arial, sans-serif; color:#000000 }"},
        {BUG_ID + FS + "stylesheet.css",
                ".NavBarCell1    { background-color:#EEEEFF; color:#000000}"},
        {BUG_ID + FS + "stylesheet.css",
                ".NavBarCell1Rev { background-color:#00008B; color:#FFFFFF}"},
        {BUG_ID + FS + "stylesheet.css",
                ".NavBarFont1    { font-family: Arial, Helvetica, sans-serif; color:#000000;color:#000000;}"},
        {BUG_ID + FS + "stylesheet.css",
                ".NavBarFont1Rev { font-family: Arial, Helvetica, sans-serif; color:#FFFFFF;color:#FFFFFF;}"},
        {BUG_ID + FS + "stylesheet.css",
                ".NavBarCell2    { font-family: Arial, Helvetica, sans-serif; background-color:#FFFFFF; color:#000000}"},
        {BUG_ID + FS + "stylesheet.css",
                ".NavBarCell3    { font-family: Arial, Helvetica, sans-serif; background-color:#FFFFFF; color:#000000}"},

    };
    private static final String[][] NEGATED_TEST = NO_TEST;

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestStylesheet tester = new TestStylesheet();
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
