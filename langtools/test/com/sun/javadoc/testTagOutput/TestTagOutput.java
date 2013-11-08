/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8026370 8026567
 * @summary This test checks the generated tag output.
 * @author Bhavesh Patel
 * @library ../lib/
 * @build JavadocTester TestTagOutput
 * @run main TestTagOutput
 */

public class TestTagOutput extends JavadocTester {

    private static final String BUG_ID = "8026370";
    private static final String[][] TEST = {
        {BUG_ID + FS + "pkg1" + FS + "DeprecatedTag.html",
            "<div class=\"block\"><span class=\"deprecatedLabel\">Deprecated.</span>&nbsp;</div>"},
        {BUG_ID + FS + "pkg1" + FS + "DeprecatedTag.html",
            "<div class=\"block\"><span class=\"deprecatedLabel\">Deprecated.</span>&nbsp;" +
            "<span class=\"deprecationComment\">Do not use this.</span></div>"}};

    private static final String[][] NEGATED_TEST = {
        {BUG_ID + FS + "pkg1" + FS + "DeprecatedTag.html",
            "<div class=\"block\"><span class=\"deprecatedLabel\">Deprecated." +
            "</span>&nbsp;<span class=\"deprecationComment\"></span></div>"}};

    private static final String[] ARGS =
        new String[] {
            "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg1"};

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestTagOutput tester = new TestTagOutput();
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
