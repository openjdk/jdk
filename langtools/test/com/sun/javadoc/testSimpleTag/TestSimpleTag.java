/*
 * Copyright (c) 2002, 2004, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4695326 4750173 4920381
 * @summary Test the declarartion of simple tags using -tag. Verify that
 * "-tag name" is a shortcut for "-tag name:a:Name:".  Also verity that
 * you can escape the ":" character with a back slash so that it is not
 * considered a separator when parsing the simple tag argument.
 * @author jamieh
 * @library ../lib/
 * @build JavadocTester
 * @build TestSimpleTag
 * @run main TestSimpleTag
 */

public class TestSimpleTag extends JavadocTester {

    private static final String BUG_ID = "4695326-4750173-4920381";

    private static final String[][] TEST =
        new String[][] {
            {"./" + BUG_ID + "/C.html",
                "<STRONG>Todo:</STRONG>"},
            {"./" + BUG_ID + "/C.html",
                "<STRONG>EJB Beans:</STRONG>"},
            {"./" + BUG_ID + "/C.html",
                "<STRONG>Regular Tag:</STRONG>"},
            {"./" + BUG_ID + "/C.html",
                "<STRONG>Back-Slash-Tag:</STRONG>"},
        };

    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR,
        "-tag", "todo",
        "-tag", "ejb\\:bean:a:EJB Beans:",
        "-tag", "regular:a:Regular Tag:",
        "-tag", "back-slash\\:tag\\\\:a:Back-Slash-Tag:",
        SRC_DIR + FS + "C.java"
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestSimpleTag tester = new TestSimpleTag();
        run(tester, ARGS, TEST, NO_TEST);
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
