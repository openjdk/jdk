/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6786682
 * @summary This test verifies the use of lang attribute by <HTML>.
 * @author Bhavesh Patel
 * @library ../lib/
 * @build JavadocTester
 * @build TestHtmlTag
 * @run main TestHtmlTag
 */

import java.util.Locale;

public class TestHtmlTag extends JavadocTester {

    private static final String BUG_ID = "6786682";
    private static final String[][] TEST1 = {
        {BUG_ID + FS + "pkg1" + FS + "C1.html", "<HTML lang=\"" + Locale.getDefault().getLanguage() + "\">"},
        {BUG_ID + FS + "pkg1" + FS + "package-summary.html", "<HTML lang=\"" + Locale.getDefault().getLanguage() + "\">"}};
    private static final String[][] NEGATED_TEST1 = {
        {BUG_ID + FS + "pkg1" + FS + "C1.html", "<HTML>"}};
    private static final String[][] TEST2 = {
        {BUG_ID + FS + "pkg2" + FS + "C2.html", "<HTML lang=\"ja\">"},
        {BUG_ID + FS + "pkg2" + FS + "package-summary.html", "<HTML lang=\"ja\">"}};
    private static final String[][] NEGATED_TEST2 = {
        {BUG_ID + FS + "pkg2" + FS + "C2.html", "<HTML>"}};
    private static final String[][] TEST3 = {
        {BUG_ID + FS + "pkg1" + FS + "C1.html", "<HTML lang=\"en\">"},
        {BUG_ID + FS + "pkg1" + FS + "package-summary.html", "<HTML lang=\"en\">"}};
    private static final String[][] NEGATED_TEST3 = {
        {BUG_ID + FS + "pkg1" + FS + "C1.html", "<HTML>"}};

    private static final String[] ARGS1 =
        new String[] {
            "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg1"};
    private static final String[] ARGS2 =
        new String[] {
            "-locale", "ja", "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg2"};
    private static final String[] ARGS3 =
        new String[] {
            "-locale", "en_US", "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg1"};

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestHtmlTag tester = new TestHtmlTag();
        run(tester, ARGS1, TEST1, NEGATED_TEST1);
        run(tester, ARGS2, TEST2, NEGATED_TEST2);
        run(tester, ARGS3, TEST3, NEGATED_TEST3);
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
