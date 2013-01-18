/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8004891
 * @summary  Make sure that the abstract method is identified correctly
 *           if the abstract modifier is present explicitly or implicitly.
 * @author   bpatel
 * @library  ../lib/
 * @build    JavadocTester TestAbstractMethod
 * @run main TestAbstractMethod
 */

public class TestAbstractMethod extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "8004891";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        {BUG_ID + FS + "pkg" + FS + "A.html",
            "<td class=\"colFirst\"><code>default void</code></td>"},
        {BUG_ID + FS + "pkg" + FS + "A.html",
            "<caption><span id=\"t0\" class=\"activeTableTab\"><span>" +
            "All Methods</span><span class=\"tabEnd\">&nbsp;</span></span>" +
            "<span id=\"t2\" class=\"tableTab\"><span>" +
            "<a href=\"javascript:show(2);\">Instance Methods</a></span>" +
            "<span class=\"tabEnd\">&nbsp;</span></span><span id=\"t3\" " +
            "class=\"tableTab\"><span><a href=\"javascript:show(4);\">" +
            "Abstract Methods</a></span><span class=\"tabEnd\">&nbsp;</span>" +
            "</span><span id=\"t5\" class=\"tableTab\"><span>" +
            "<a href=\"javascript:show(16);\">Default Methods</a></span>" +
            "<span class=\"tabEnd\">&nbsp;</span></span></caption>"},
        {BUG_ID + FS + "pkg" + FS + "B.html",
            "<caption><span id=\"t0\" class=\"activeTableTab\"><span>" +
            "All Methods</span><span class=\"tabEnd\">&nbsp;</span></span>" +
            "<span id=\"t2\" class=\"tableTab\"><span>" +
            "<a href=\"javascript:show(2);\">Instance Methods</a></span>" +
            "<span class=\"tabEnd\">&nbsp;</span></span><span id=\"t3\" " +
            "class=\"tableTab\"><span><a href=\"javascript:show(4);\">Abstract " +
            "Methods</a></span><span class=\"tabEnd\">&nbsp;</span></span>" +
            "<span id=\"t4\" class=\"tableTab\"><span>" +
            "<a href=\"javascript:show(8);\">Concrete Methods</a></span>" +
            "<span class=\"tabEnd\">&nbsp;</span></span></caption>"},
        {BUG_ID + FS + "pkg" + FS + "B.html",
            "<td class=\"colFirst\"><code>abstract void</code></td>"},
        {BUG_ID + FS + "pkg" + FS + "C.html",
            "<caption><span id=\"t0\" class=\"activeTableTab\"><span>" +
            "All Methods</span><span class=\"tabEnd\">&nbsp;</span></span>" +
            "<span id=\"t2\" class=\"tableTab\"><span>" +
            "<a href=\"javascript:show(2);\">Instance Methods</a></span>" +
            "<span class=\"tabEnd\">&nbsp;</span></span>" +
            "<span id=\"t5\" class=\"tableTab\"><span>" +
            "<a href=\"javascript:show(16);\">Default Methods</a></span>" +
            "<span class=\"tabEnd\">&nbsp;</span></span></caption>"},
        {BUG_ID + FS + "pkg" + FS + "C.html",
            "<td class=\"colFirst\"><code>default void</code></td>"}
    };
    private static final String[][] NEGATED_TEST = {
        {BUG_ID + FS + "pkg" + FS + "A.html",
            "<td class=\"colFirst\"><code>abstract void</code></td>"},
        {BUG_ID + FS + "pkg" + FS + "B.html",
            "<span><a href=\"javascript:show(16);\">Default Methods</a></span>" +
            "<span class=\"tabEnd\">&nbsp;</span>"},
        {BUG_ID + FS + "pkg" + FS + "B.html",
            "<td class=\"colFirst\"><code>default void</code></td>"},
        {BUG_ID + FS + "pkg" + FS + "C.html",
            "<span><a href=\"javascript:show(4);\">Abstract Methods</a></span>" +
            "<span class=\"tabEnd\">&nbsp;</span>"}
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestAbstractMethod tester = new TestAbstractMethod();
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
