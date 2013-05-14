/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7112427 8012295
 * @summary Test of the JavaFX doclet features.
 * @author jvalenta
 * @library ../lib/
 * @build JavadocTester TestJavaFX
 * @run main TestJavaFX
 */

public class TestJavaFX extends JavadocTester {

    private static final String BUG_ID = "7112427";

    private static final String[][] TEST =
        new String[][] {
            {"./" + BUG_ID + "/C.html",
                "<dt><span class=\"strong\">See Also:</span></dt>" + NL + "<dd><a href=\"C.html#getRate()\"><code>getRate()</code></a>, " + NL +
                "<a href=\"C.html#setRate(double)\"><code>setRate(double)</code></a></dd>"},
            {"./" + BUG_ID + "/C.html",
                "<pre>public final&nbsp;void&nbsp;setRate(double&nbsp;value)</pre>" + NL +
                "<div class=\"block\">Sets the value of the property rate.</div>" + NL +
                "<dl>" + NL + "<dt><span class=\"strong\">Property description:</span></dt>" },
            {"./" + BUG_ID + "/C.html",
                "<pre>public final&nbsp;double&nbsp;getRate()</pre>" + NL +
                "<div class=\"block\">Gets the value of the property rate.</div>" + NL +
                "<dl>" + NL + "<dt><span class=\"strong\">Property description:</span></dt>" },
            {"./" + BUG_ID + "/C.html",
                "<td class=\"colLast\"><code><strong><a href=\"C.html#rateProperty\">rate</a></strong></code>" + NL +
                "<div class=\"block\">Defines the direction/speed at which the <code>Timeline</code> is expected to"},

            {"./" + BUG_ID + "/C.html",
                "<span class=\"strong\">Default value:</span>"},
            {"./" + BUG_ID + "/C.html",
                "<p>Sets the value of the property <code>Property</code>"},
            {"./" + BUG_ID + "/C.html",
                "<p>Gets the value of the property <code>Property</code>"},
            {"./" + BUG_ID + "/C.html",
                "<span class=\"strong\">Property description:</span>"},
            {"./" + BUG_ID + "/C.html",
                "<td class=\"colLast\"><code><strong><a href=\"C.html#setTestMethodProperty()\">setTestMethodProperty</a></strong>()</code>&nbsp;</td>" },
            {"./" + BUG_ID + "/C.html",
                "<h4>isPaused</h4>" + NL +
                "<pre>public final&nbsp;double&nbsp;isPaused()</pre>" + NL +
                "<div class=\"block\">Gets the value of the property paused.</div>" },
            {"./" + BUG_ID + "/D.html",
                "<h3>Properties inherited from class&nbsp;<a href=\"C.html\" title=\"class in &lt;Unnamed&gt;\">C</a></h3>" + NL +
                "<code><a href=\"C.html#pausedProperty\">paused</a>, <a href=\"C.html#rateProperty\">rate</a></code></li>" },
        };
    private static final String[][] NO_TEST =
        new String[][] {
            {"./" + BUG_ID + "/C.html",
                "A()"},
        };


    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "-private", "-javafx",
        SRC_DIR + FS + "C.java", SRC_DIR + FS + "D.java"
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestJavaFX tester = new TestJavaFX();
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
