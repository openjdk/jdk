/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4632553 4973607
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
        "-d", BUG_ID, "-source",  "1.5","-sourcepath", SRC_DIR, "pkg"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        {BUG_ID + FS + "pkg" + FS + "package-tree.html",
            "<LI TYPE=\"circle\">pkg.<A HREF=\"../pkg/ParentClass.html\" " +
            "title=\"class in pkg\"><STRONG>ParentClass</STRONG></A><UL>"},

        {BUG_ID + FS + "pkg" + FS + "package-tree.html",
            "Annotation Type Hierarchy" + NL + "</H2>" + NL + "<UL>" + NL +
            "<LI TYPE=\"circle\">pkg.<A HREF=\"../pkg/AnnotationType.html\" " +
            "title=\"annotation in pkg\"><STRONG>AnnotationType</STRONG></A> " +
            "(implements java.lang.annotation.Annotation)" + NL + "</UL>"},

        {BUG_ID + FS + "pkg" + FS + "package-tree.html",
            "<H2>" + NL +
            "Enum Hierarchy" + NL +
            "</H2>" + NL +
            "<UL>" + NL +
            "<LI TYPE=\"circle\">java.lang.Object<UL>" + NL +
            "<LI TYPE=\"circle\">java.lang.Enum&lt;E&gt; (implements java.lang.Comparable&lt;T&gt;, java.io.Serializable)" + NL +
            "<UL>" + NL +
            "<LI TYPE=\"circle\">pkg.<A HREF=\"../pkg/Coin.html\" title=\"enum in pkg\"><STRONG>Coin</STRONG></A></UL>" + NL +
            "</UL>" + NL +
            "</UL>"
        },
    };
    private static final String[][] NEGATED_TEST = {
        {BUG_ID + FS + "pkg" + FS + "package-tree.html",
            "<LI TYPE=\"circle\">class pkg.<A HREF=\"../pkg/ParentClass.html\" " +
            "title=\"class in pkg\"><STRONG>ParentClass</STRONG></A><UL>"}
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
