/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4780441 4874845 4978816
 * @summary  Make sure that when the -private flag is not used, members
 *           inherited from package private class are documented in the child.
 *
 *           Make sure that when a method inherits documentation from a method
 *           in a non-public class/interface, the non-public class/interface
 *           is not mentioned anywhere (not even in the signature or tree).
 *
 *           Make sure that when a private interface method with generic parameters
 *           is implemented, the comments can be inherited properly.
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester
 * @build    TestPrivateClasses
 * @run main TestPrivateClasses
 */

public class TestPrivateClasses extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4780441-4874845-4978816";

    //Javadoc arguments.
    private static final String[] ARGS1 = new String[] {
        "-d", BUG_ID + "-1", "-sourcepath", SRC_DIR, "-source", "1.5", "pkg", "pkg2"
    };
    private static final String[] ARGS2 = new String[] {
        "-d", BUG_ID + "-2", "-sourcepath", SRC_DIR, "-private",
            "-source", "1.5", "pkg", "pkg2"
    };

    // Test output when -private flag is not used.
    private static final String[][] TEST1 = {
        // Field inheritence from non-public superclass.
        {BUG_ID + "-1" + FS + "pkg" + FS + "PublicChild.html",
            "<a href=\"../pkg/PublicChild.html#fieldInheritedFromParent\">" +
                "fieldInheritedFromParent</a>"
        },

        // Method inheritence from non-public superclass.
        {BUG_ID + "-1" + FS + "pkg" + FS + "PublicChild.html",
            "<a href=\"../pkg/PublicChild.html#methodInheritedFromParent(int)\">" +
                "methodInheritedFromParent</a>"
        },

        // Field inheritence from non-public superinterface.
        {BUG_ID + "-1" + FS + "pkg" + FS + "PublicInterface.html",
            "<a href=\"../pkg/PublicInterface.html#fieldInheritedFromInterface\">" +
                "fieldInheritedFromInterface</a>"
        },

        // Method inheritence from non-public superinterface.
        {BUG_ID + "-1" + FS + "pkg" + FS + "PublicInterface.html",
            "<a href=\"../pkg/PublicInterface.html#methodInterface(int)\">" +
                "methodInterface</a>"
        },

        // private class does not show up in tree
        {BUG_ID + "-1" + FS + "pkg" + FS + "PublicChild.html",
            "<ul class=\"inheritance\">" + NL + "<li>java.lang.Object</li>" + NL +
            "<li>" + NL + "<ul class=\"inheritance\">" + NL + "<li>pkg.PublicChild</li>" + NL +
            "</ul>" + NL + "</li>" + NL + "</ul>"
        },

        // Method is documented as though it is declared in the inheriting method.
        {BUG_ID + "-1" + FS + "pkg" + FS + "PublicChild.html",
            "<pre>public&nbsp;void&nbsp;methodInheritedFromParent(int&nbsp;p1)"
        },

        //Make sure implemented interfaces from private superclass are inherited
        {BUG_ID + "-1" + FS + "pkg" + FS + "PublicInterface.html",
            "<dl>" + NL + "<dt>All Known Implementing Classes:</dt>" + NL +
            "<dd><a href=\"../pkg/PublicChild.html\" title=\"class in pkg\">" +
            "PublicChild</a></dd>" + NL + "</dl>"},

        {BUG_ID + "-1" + FS + "pkg" + FS + "PublicChild.html",
            "<dl>" + NL + "<dt>All Implemented Interfaces:</dt>" + NL +
            "<dd><a href=\"../pkg/PublicInterface.html\" title=\"interface in pkg\">" +
            "PublicInterface</a></dd>" + NL + "</dl>"},

        //Generic interface method test.
        {BUG_ID + "-1" + FS + "pkg2" + FS + "C.html",
            "This comment should get copied to the implementing class"},
    };
    private static final String[][] NEGATED_TEST1 = {
       // Should not document that a method overrides method from private class.
      {BUG_ID + "-1" + FS + "pkg" + FS + "PublicChild.html",
        "<strong>Overrides:</strong>"},
      // Should not document that a method specified by private interface.
      {BUG_ID + "-1" + FS + "pkg" + FS + "PublicChild.html",
        "<strong>Specified by:</strong>"},
      {BUG_ID + "-1" + FS + "pkg" + FS + "PublicInterface.html",
        "<strong>Specified by:</strong>"},
      // Should not mention that any documentation was copied.
      {BUG_ID + "-1" + FS + "pkg" + FS + "PublicChild.html",
        "Description copied from"},
      {BUG_ID + "-1" + FS + "pkg" + FS + "PublicInterface.html",
        "Description copied from"},
      // Don't extend private classes or interfaces
      {BUG_ID + "-1" + FS + "pkg" + FS + "PublicChild.html",
        "PrivateParent"},
      {BUG_ID + "-1" + FS + "pkg" + FS + "PublicInterface.html",
        "PrivateInterface"},
      {BUG_ID + "-1" + FS + "pkg" + FS + "PublicChild.html",
        "PrivateInterface"},
      {BUG_ID + "-1" + FS + "pkg" + FS + "PublicInterface.html",
        "All Superinterfaces"},
      // Make inherited constant are documented correctly.
      {BUG_ID + "-1" + FS + "constant-values.html",
        "PrivateInterface"},

        //Do not inherit private interface method with generic parameters.
        //This method has been implemented.
        {BUG_ID + "-1" + FS + "pkg2" + FS + "C.html",
            "<strong><a href=\"../pkg2/I.html#hello(T)\">hello</a></strong>"},
    };

    // Test output when -private flag is used.
    private static final String[][] TEST2 = {
        // Field inheritence from non-public superclass.
        {BUG_ID + "-2" + FS + "pkg" + FS + "PublicChild.html",
            "Fields inherited from class&nbsp;pkg." +
            "<a href=\"../pkg/PrivateParent.html\" title=\"class in pkg\">" +
            "PrivateParent</a>"
        },
        {BUG_ID + "-2" + FS + "pkg" + FS + "PublicChild.html",
            "<a href=\"../pkg/PrivateParent.html#fieldInheritedFromParent\">" +
                "fieldInheritedFromParent</a>"
        },
        // Field inheritence from non-public superinterface.
        {BUG_ID + "-2" + FS + "pkg" + FS + "PublicInterface.html",
            "Fields inherited from interface&nbsp;pkg." +
            "<a href=\"../pkg/PrivateInterface.html\" title=\"interface in pkg\">" +
            "PrivateInterface</a>"
        },
        {BUG_ID + "-2" + FS + "pkg" + FS + "PublicInterface.html",
            "<a href=\"../pkg/PrivateInterface.html#fieldInheritedFromInterface\">" +
                "fieldInheritedFromInterface</a>"
        },
        // Method inheritence from non-public superclass.
        {BUG_ID + "-2" + FS + "pkg" + FS + "PublicChild.html",
            "Methods inherited from class&nbsp;pkg." +
            "<a href=\"../pkg/PrivateParent.html\" title=\"class in pkg\">" +
            "PrivateParent</a>"
        },
        {BUG_ID + "-2" + FS + "pkg" + FS + "PublicChild.html",
            "<a href=\"../pkg/PrivateParent.html#methodInheritedFromParent(int)\">" +
                "methodInheritedFromParent</a>"
        },
        // Should document that a method overrides method from private class.
       {BUG_ID + "-2" + FS + "pkg" + FS + "PublicChild.html",
            "<dt><strong>Overrides:</strong></dt>" + NL +
            "<dd><code><a href=\"../pkg/PrivateParent.html#methodOverridenFromParent(char[], int, T, V, java.util.List)\">" +
            "methodOverridenFromParent</a></code>&nbsp;in class&nbsp;<code>" +
            "<a href=\"../pkg/PrivateParent.html\" title=\"class in pkg\">" +
            "PrivateParent</a></code></dd>"},
       // Should document that a method is specified by private interface.
       {BUG_ID + "-2" + FS + "pkg" + FS + "PublicChild.html",
            "<dt><strong>Specified by:</strong></dt>" + NL +
            "<dd><code><a href=\"../pkg/PrivateInterface.html#methodInterface(int)\">" +
            "methodInterface</a></code>&nbsp;in interface&nbsp;<code>" +
            "<a href=\"../pkg/PrivateInterface.html\" title=\"interface in pkg\">" +
            "PrivateInterface</a></code></dd>"},
       // Method inheritence from non-public superinterface.
       {BUG_ID + "-2" + FS + "pkg" + FS + "PublicInterface.html",
            "Methods inherited from interface&nbsp;pkg." +
            "<a href=\"../pkg/PrivateInterface.html\" title=\"interface in pkg\">" +
            "PrivateInterface</a>"
        },
        {BUG_ID + "-2" + FS + "pkg" + FS + "PrivateInterface.html",
            "<a href=\"../pkg/PrivateInterface.html#methodInterface(int)\">" +
                "methodInterface</a>"
        },
      // Should mention that any documentation was copied.
      {BUG_ID + "-2" + FS + "pkg" + FS + "PublicChild.html",
        "Description copied from"},
      // Extend documented private classes or interfaces
      {BUG_ID + "-2" + FS + "pkg" + FS + "PublicChild.html",
        "extends"},
      {BUG_ID + "-2" + FS + "pkg" + FS + "PublicInterface.html",
        "extends"},
      {BUG_ID + "-2" + FS + "pkg" + FS + "PublicInterface.html",
        "All Superinterfaces"},

      //Make sure implemented interfaces from private superclass are inherited
      {BUG_ID + "-2" + FS + "pkg" + FS + "PublicInterface.html",
        "<dl>" + NL + "<dt>All Known Implementing Classes:</dt>" + NL +
        "<dd><a href=\"../pkg/PrivateParent.html\" title=\"class in pkg\">" +
        "PrivateParent</a>, " +
        "<a href=\"../pkg/PublicChild.html\" title=\"class in pkg\">PublicChild" +
        "</a></dd>" + NL + "</dl>"},

      {BUG_ID + "-2" + FS + "pkg" + FS + "PublicChild.html",
        "<dl>" + NL + "<dt>All Implemented Interfaces:</dt>" + NL +
        "<dd><a href=\"../pkg/PrivateInterface.html\" title=\"interface in pkg\">" +
        "PrivateInterface</a>, " +
        "<a href=\"../pkg/PublicInterface.html\" title=\"interface in pkg\">" +
        "PublicInterface</a></dd>" + NL + "</dl>"},

      //Since private flag is used, we can document that private interface method
      //with generic parameters has been implemented.
      {BUG_ID + "-2" + FS + "pkg2" + FS + "C.html",
            "<strong>Description copied from interface:&nbsp;<code>" +
            "<a href=\"../pkg2/I.html#hello(T)\">I</a></code></strong>"},

      {BUG_ID + "-2" + FS + "pkg2" + FS + "C.html",
            "<dt><strong>Specified by:</strong></dt>" + NL +
            "<dd><code><a href=\"../pkg2/I.html#hello(T)\">hello</a></code>" +
            "&nbsp;in interface&nbsp;<code>" +
            "<a href=\"../pkg2/I.html\" title=\"interface in pkg2\">I</a>" +
            "&lt;java.lang.String&gt;</code></dd>"},
    };
    private static final String[][] NEGATED_TEST2 = NO_TEST;

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestPrivateClasses tester = new TestPrivateClasses();
        run(tester, ARGS1, TEST1, NEGATED_TEST1);
        run(tester, ARGS2, TEST2, NEGATED_TEST2);
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
