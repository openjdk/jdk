/*
 * Copyright (c) 2004, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4632553 4973607 8026567
 * @summary  No need to include type name (class, interface, etc.) before
 *           every single type in class tree.
 *           Make sure class tree includes heirarchy for enums and annotation
 *           types.
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester
 * @build    TestClassTree
 * @run main TestClassTree
 */

public class TestClassTree extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4632553-4973607";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        {BUG_ID + FS + "pkg" + FS + "package-tree.html",
            "<ul>" + NL + "<li type=\"circle\">pkg.<a href=\"../pkg/ParentClass.html\" " +
            "title=\"class in pkg\"><span class=\"typeNameLink\">ParentClass</span></a>"},

        {BUG_ID + FS + "pkg" + FS + "package-tree.html",
            "<h2 title=\"Annotation Type Hierarchy\">Annotation Type Hierarchy</h2>" + NL +
            "<ul>" + NL + "<li type=\"circle\">pkg.<a href=\"../pkg/AnnotationType.html\" " +
            "title=\"annotation in pkg\"><span class=\"typeNameLink\">AnnotationType</span></a> " +
            "(implements java.lang.annotation.Annotation)</li>" + NL + "</ul>"},

        {BUG_ID + FS + "pkg" + FS + "package-tree.html",
            "<h2 title=\"Enum Hierarchy\">Enum Hierarchy</h2>" + NL + "<ul>" + NL +
            "<li type=\"circle\">java.lang.Object" + NL + "<ul>" + NL +
            "<li type=\"circle\">java.lang.Enum&lt;E&gt; (implements java.lang." +
            "Comparable&lt;T&gt;, java.io.Serializable)" + NL + "<ul>" + NL +
            "<li type=\"circle\">pkg.<a href=\"../pkg/Coin.html\" " +
            "title=\"enum in pkg\"><span class=\"typeNameLink\">Coin</span></a></li>" + NL +
            "</ul>" + NL + "</li>" + NL + "</ul>" + NL + "</li>" + NL + "</ul>"
        },
    };
    private static final String[][] NEGATED_TEST = {
        {BUG_ID + FS + "pkg" + FS + "package-tree.html",
            "<li type=\"circle\">class pkg.<a href=\"../pkg/ParentClass.html\" " +
            "title=\"class in pkg\"><span class=\"typeNameLink\">ParentClass</span></a></li>"}
        };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestClassTree tester = new TestClassTree();
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
