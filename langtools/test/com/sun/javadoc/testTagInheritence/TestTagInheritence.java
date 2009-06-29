/*
 * Copyright 2002-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug     4496223 4496270 4618686 4720974 4812240 6253614 6253604
 * @summary <DESC>
 * @author  jamieh
 * @library ../lib/
 * @build JavadocTester
 * @build TestTagInheritence
 * @run main TestTagInheritence
 */

public class TestTagInheritence extends JavadocTester {

    private static final String BUG_ID = "4496223-4496270-4618686-4720974-4812240-6253614-6253604";
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg", "firstSentence", "firstSentence2"
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        String[][] tests = new String[42][2];
        //Test bad inheritDoc tag warning.
        tests[0][0]= WARNING_OUTPUT;
        tests[0][1] = "warning - @inheritDoc used but testBadInheritDocTag() " +
            "does not override or implement any method.";

        //Test valid usage of inheritDoc tag.
        for (int i = 1; i < tests.length-2; i++) {
            tests[i][0] = BUG_ID + FS + "pkg" + FS + "TestTagInheritence.html";
            tests[i][1] = "Test " + i + " passes";
        }

        //First sentence test (6253614)
        tests[tests.length - 2][0] =BUG_ID + FS + "firstSentence" + FS +
            "B.html";
        tests[tests.length - 2][1] =  "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;First sentence.</TD>";

        //Another first sentence test (6253604)
        tests[tests.length - 1][0] =BUG_ID + FS + "firstSentence2" + FS +
            "C.html";
        tests[tests.length - 1][1] =  "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;First sentence.</TD>";

        TestTagInheritence tester = new TestTagInheritence();
        run(tester, ARGS, tests, NO_TEST);
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
