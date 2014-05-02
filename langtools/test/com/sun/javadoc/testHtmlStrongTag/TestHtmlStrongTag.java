/*
 * Copyright (c) 1999, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @bug 6786028 8026567
 * @summary This test verifys the use of <strong> HTML tag instead of <B> by Javadoc std doclet.
 * @author Bhavesh Patel
 * @library ../lib/
 * @build JavadocTester
 * @build TestHtmlStrongTag
 * @run main TestHtmlStrongTag
 */

public class TestHtmlStrongTag extends JavadocTester {

    private static final String[][] TEST1 = {
        { "pkg1/C1.html",
            "<span class=\"seeLabel\">See Also:</span>"}};
    private static final String[][] NEGATED_TEST1 = {
        { "pkg1/C1.html", "<STRONG>Method Summary</STRONG>"},
        { "pkg1/C1.html", "<B>"},
        { "pkg1/package-summary.html",
            "<STRONG>Class Summary</STRONG>"}};
    private static final String[][] TEST2 = {
        { "pkg2/C2.html", "<B>Comments:</B>"}};
    private static final String[][] NEGATED_TEST2 = {
        { "pkg2/C2.html", "<STRONG>Method Summary</STRONG>"}};

    private static final String[] ARGS1 =
        new String[] {
            "-d", OUTPUT_DIR + "-1", "-sourcepath", SRC_DIR, "pkg1"};
    private static final String[] ARGS2 =
        new String[] {
            "-d", OUTPUT_DIR + "-2", "-sourcepath", SRC_DIR, "pkg2"};

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestHtmlStrongTag tester = new TestHtmlStrongTag();
        tester.run(ARGS1, TEST1, NEGATED_TEST1);
        tester.run(ARGS2, TEST2, NEGATED_TEST2);
        tester.printSummary();
    }
}
