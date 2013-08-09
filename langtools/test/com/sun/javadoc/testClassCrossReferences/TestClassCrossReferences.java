/*
 * Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4652655 4857717
 * @summary This test verifies that class cross references work properly.
 * @author jamieh
 * @library ../lib/
 * @build JavadocTester
 * @build TestClassCrossReferences
 * @run main TestClassCrossReferences
 */

public class TestClassCrossReferences extends JavadocTester {

    private static final String BUG_ID = "4652655-4857717";
    private static final String[][] TEST = {
        {BUG_ID + FS + "C.html",
            "<a href=\"http://java.sun.com/j2se/1.4/docs/api/java/math/package-summary.html?is-external=true\"><code>Link to math package</code></a>"},
        {BUG_ID + FS + "C.html",
            "<a href=\"http://java.sun.com/j2se/1.4/docs/api/javax/swing/text/AbstractDocument.AttributeContext.html?is-external=true\" " +
            "title=\"class or interface in javax.swing.text\"><code>Link to AttributeContext innerclass</code></a>"},
        {BUG_ID + FS + "C.html",
            "<a href=\"http://java.sun.com/j2se/1.4/docs/api/java/math/BigDecimal.html?is-external=true\" " +
                "title=\"class or interface in java.math\"><code>Link to external class BigDecimal</code></a>"},
        {BUG_ID + FS + "C.html",
            "<a href=\"http://java.sun.com/j2se/1.4/docs/api/java/math/BigInteger.html?is-external=true#gcd(java.math.BigInteger)\" " +
                "title=\"class or interface in java.math\"><code>Link to external member gcd</code></a>"},
        {BUG_ID + FS + "C.html",
            "<dl>" + NL + "<dt><span class=\"strong\">Overrides:</span></dt>" + NL +
            "<dd><code>toString</code>&nbsp;in class&nbsp;<code>java.lang.Object</code></dd>" + NL +
            "</dl>"}
    };
    private static final String[][] NEGATED_TEST = NO_TEST;
    private static final String[] ARGS =
        new String[] {
            "-d", BUG_ID, "-sourcepath", SRC_DIR,
            "-linkoffline", "http://java.sun.com/j2se/1.4/docs/api/",
            SRC_DIR, SRC_DIR + FS + "C.java"};

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestClassCrossReferences tester = new TestClassCrossReferences();
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
