/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4232882 8014636
 * @summary Javadoc strips all of the leading spaces when the comment
 *    does not begin with a star.  This RFE allows users to
 *    begin their comment without a leading star without leading
 *    spaces stripped
 * @author jamieh
 * @library ../lib/
 * @build JavadocTester
 * @build LeadingSpaces
 * @run main LeadingSpaces
 */

public class LeadingSpaces extends JavadocTester {

    private static final String BUG_ID = "4232882-8014636";
    private static final String[][] TEST = {
        {BUG_ID + FS + "LeadingSpaces.html",
"        1" + NL +
"          2" + NL +
"            3" + NL +
"              4" + NL +
"                5" + NL +
"                  6" + NL +
"                    7"}
    };
    private static final String[][] NEGATED_TEST = NO_TEST;
    private static final String[] ARGS =
        new String[] {
            "-d", BUG_ID, "-sourcepath", SRC_DIR,
        SRC_DIR + FS + "LeadingSpaces.java"};

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        LeadingSpaces tester = new LeadingSpaces();
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

    /**
       This leading spaces in the &lt;pre&gt; block below should be
       preserved.
       <pre>
        1
          2
            3
              4
                5
                  6
                    7
       </pre>
     */
    public void method(){}

}
