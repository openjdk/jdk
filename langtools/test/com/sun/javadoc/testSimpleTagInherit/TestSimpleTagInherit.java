/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8008768 8026567
 * @summary  Using {@inheritDoc} in simple tag defined via -tag fails
 * @library  ../lib/
 * @build    JavadocTester TestSimpleTagInherit
 * @run main TestSimpleTagInherit
 */

public class TestSimpleTagInherit extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "8008768";
    private static final String OUTPUT_DIR = BUG_ID;

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", OUTPUT_DIR, "-sourcepath", SRC_DIR,
        "-tag", "custom:optcm:<em>Custom:</em>",
        "p"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        { BUG_ID + FS + "p" + FS + "TestClass.html",
          "<dt><span class=\"simpleTagLabel\"><em>Custom:</em></span></dt>" + NL +
          "<dd>doc for BaseClass class</dd>" },
        { BUG_ID + FS + "p" + FS + "TestClass.html",
          "<dt><span class=\"simpleTagLabel\"><em>Custom:</em></span></dt>" + NL +
          "<dd>doc for BaseClass method</dd>" }
    };
    private static final String[][] NEGATED_TEST = NO_TEST;

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestSimpleTagInherit tester = new TestSimpleTagInherit();
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
