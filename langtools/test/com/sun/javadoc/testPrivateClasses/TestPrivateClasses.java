/*
 * Copyright (c) 2003, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4780441 4874845 4978816 8014017 8016328 8025633 8026567
 * @summary  Make sure that when the -private flag is not used, members
 *           inherited from package private class are documented in the child.
 *
 *           Make sure that when a method inherits documentation from a method
 *           in a non-public class/interface, the non-public class/interface
 *           is not mentioned anywhere (not even in the signature or tree).
 *
 *           Make sure that when a private interface method with generic parameters
 *           is implemented, the comments can be inherited properly.
 *
 *           Make sure when no modifier appear in the class signature, the
 *           signature is displayed correctly without extra space at the beginning.
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester TestPrivateClasses
 * @run main TestPrivateClasses
 */

public class TestPrivateClasses extends JavadocTester {

    //Javadoc arguments.
    private static final String[] ARGS1 = new String[] {
        "-d", OUTPUT_DIR + "-1", "-sourcepath", SRC_DIR, "pkg", "pkg2"
    };
    private static final String[] ARGS2 = new String[] {
        "-d", OUTPUT_DIR + "-2", "-sourcepath", SRC_DIR, "-private",
            "pkg", "pkg2"
    };

    // Test output when -private flag is not used.
    private static final String[][] TEST1 = {
        // Field inheritence from non-public superclass.
        { "pkg/PublicChild.html",
            "<a href=\"../pkg/PublicChild.html#fieldInheritedFromParent\">" +
                "fieldInheritedFromParent</a>"
        },

        // Method inheritence from non-public superclass.
        { "pkg/PublicChild.html",
            "<a href=\"../pkg/PublicChild.html#methodInheritedFromParent-int-\">" +
                "methodInheritedFromParent</a>"
        },

        // Field inheritence from non-public superinterface.
        { "pkg/PublicInterface.html",
            "<a href=\"../pkg/PublicInterface.html#fieldInheritedFromInterface\">" +
                "fieldInheritedFromInterface</a>"
        },

        // Method inheritence from non-public superinterface.
        { "pkg/PublicInterface.html",
            "<a href=\"../pkg/PublicInterface.html#methodInterface-int-\">" +
                "methodInterface</a>"
        },

        // private class does not show up in tree
        { "pkg/PublicChild.html",
            "<ul class=\"inheritance\">\n" +
            "<li>java.lang.Object</li>\n" +
            "<li>\n" +
            "<ul class=\"inheritance\">\n" +
            "<li>pkg.PublicChild</li>\n" +
            "</ul>\n" +
            "</li>\n" +
            "</ul>"
        },

        // Method is documented as though it is declared in the inheriting method.
        { "pkg/PublicChild.html",
            "<pre>public&nbsp;void&nbsp;methodInheritedFromParent(int&nbsp;p1)"
        },

        //Make sure implemented interfaces from private superclass are inherited
        { "pkg/PublicInterface.html",
            "<dl>\n" +
            "<dt>All Known Implementing Classes:</dt>\n" +
            "<dd><a href=\"../pkg/PublicChild.html\" title=\"class in pkg\">" +
            "PublicChild</a></dd>\n" +
            "</dl>"},

        { "pkg/PublicChild.html",
            "<dl>\n" +
            "<dt>All Implemented Interfaces:</dt>\n" +
            "<dd><a href=\"../pkg/PublicInterface.html\" title=\"interface in pkg\">" +
            "PublicInterface</a></dd>\n" +
            "</dl>"},

        //Generic interface method test.
        { "pkg2/C.html",
            "This comment should get copied to the implementing class"},
    };
    private static final String[][] NEGATED_TEST1 = {
       // Should not document that a method overrides method from private class.
      { "pkg/PublicChild.html",
        "<span class=\"overrideSpecifyLabel\">Overrides:</span>"},
      // Should not document that a method specified by private interface.
      { "pkg/PublicChild.html",
        "<span class=\"overrideSpecifyLabel\">Specified by:</span>"},
      { "pkg/PublicInterface.html",
        "<span class=\"overrideSpecifyLabel\">Specified by:</span>"},
      // Should not mention that any documentation was copied.
      { "pkg/PublicChild.html",
        "Description copied from"},
      { "pkg/PublicInterface.html",
        "Description copied from"},
      // Don't extend private classes or interfaces
      { "pkg/PublicChild.html",
        "PrivateParent"},
      { "pkg/PublicInterface.html",
        "PrivateInterface"},
      { "pkg/PublicChild.html",
        "PrivateInterface"},
      { "pkg/PublicInterface.html",
        "All Superinterfaces"},
      // Make inherited constant are documented correctly.
      { "constant-values.html",
        "PrivateInterface"},

        //Do not inherit private interface method with generic parameters.
        //This method has been implemented.
        { "pkg2/C.html",
            "<span class=\"memberNameLink\"><a href=\"../pkg2/I.html#hello-T-\">hello</a></span>"},
    };

    // Test output when -private flag is used.
    private static final String[][] TEST2 = {
        // Field inheritence from non-public superclass.
        { "pkg/PublicChild.html",
            "Fields inherited from class&nbsp;pkg." +
            "<a href=\"../pkg/PrivateParent.html\" title=\"class in pkg\">" +
            "PrivateParent</a>"
        },
        { "pkg/PublicChild.html",
            "<a href=\"../pkg/PrivateParent.html#fieldInheritedFromParent\">" +
                "fieldInheritedFromParent</a>"
        },
        // Field inheritence from non-public superinterface.
        { "pkg/PublicInterface.html",
            "Fields inherited from interface&nbsp;pkg." +
            "<a href=\"../pkg/PrivateInterface.html\" title=\"interface in pkg\">" +
            "PrivateInterface</a>"
        },
        { "pkg/PublicInterface.html",
            "<a href=\"../pkg/PrivateInterface.html#fieldInheritedFromInterface\">" +
                "fieldInheritedFromInterface</a>"
        },
        // Method inheritence from non-public superclass.
        { "pkg/PublicChild.html",
            "Methods inherited from class&nbsp;pkg." +
            "<a href=\"../pkg/PrivateParent.html\" title=\"class in pkg\">" +
            "PrivateParent</a>"
        },
        { "pkg/PublicChild.html",
            "<a href=\"../pkg/PrivateParent.html#methodInheritedFromParent-int-\">" +
                "methodInheritedFromParent</a>"
        },
        // Should document that a method overrides method from private class.
       { "pkg/PublicChild.html",
            "<dt><span class=\"overrideSpecifyLabel\">Overrides:</span></dt>\n" +
            "<dd><code><a href=\"../pkg/PrivateParent.html#methodOverridenFromParent-char:A-int-T-V-java.util.List-\">" +
            "methodOverridenFromParent</a></code>&nbsp;in class&nbsp;<code>" +
            "<a href=\"../pkg/PrivateParent.html\" title=\"class in pkg\">" +
            "PrivateParent</a></code></dd>"},
       // Should document that a method is specified by private interface.
       { "pkg/PublicChild.html",
            "<dt><span class=\"overrideSpecifyLabel\">Specified by:</span></dt>\n" +
            "<dd><code><a href=\"../pkg/PrivateInterface.html#methodInterface-int-\">" +
            "methodInterface</a></code>&nbsp;in interface&nbsp;<code>" +
            "<a href=\"../pkg/PrivateInterface.html\" title=\"interface in pkg\">" +
            "PrivateInterface</a></code></dd>"},
       // Method inheritence from non-public superinterface.
       { "pkg/PublicInterface.html",
            "Methods inherited from interface&nbsp;pkg." +
            "<a href=\"../pkg/PrivateInterface.html\" title=\"interface in pkg\">" +
            "PrivateInterface</a>"
        },
        { "pkg/PrivateInterface.html",
            "<a href=\"../pkg/PrivateInterface.html#methodInterface-int-\">" +
                "methodInterface</a>"
        },
      // Should mention that any documentation was copied.
      { "pkg/PublicChild.html",
        "Description copied from"},
      // Extend documented private classes or interfaces
      { "pkg/PublicChild.html",
        "extends"},
      { "pkg/PublicInterface.html",
        "extends"},
      { "pkg/PublicInterface.html",
        "All Superinterfaces"},

      //Make sure implemented interfaces from private superclass are inherited
      { "pkg/PublicInterface.html",
        "<dl>\n" +
        "<dt>All Known Implementing Classes:</dt>\n" +
        "<dd><a href=\"../pkg/PrivateParent.html\" title=\"class in pkg\">" +
        "PrivateParent</a>, " +
        "<a href=\"../pkg/PublicChild.html\" title=\"class in pkg\">PublicChild" +
        "</a></dd>\n" +
        "</dl>"},

      { "pkg/PublicChild.html",
        "<dl>\n" +
        "<dt>All Implemented Interfaces:</dt>\n" +
        "<dd><a href=\"../pkg/PrivateInterface.html\" title=\"interface in pkg\">" +
        "PrivateInterface</a>, " +
        "<a href=\"../pkg/PublicInterface.html\" title=\"interface in pkg\">" +
        "PublicInterface</a></dd>\n" +
        "</dl>"},

      //Since private flag is used, we can document that private interface method
      //with generic parameters has been implemented.
      { "pkg2/C.html",
            "<span class=\"descfrmTypeLabel\">Description copied from interface:&nbsp;<code>" +
            "<a href=\"../pkg2/I.html#hello-T-\">I</a></code></span>"},

      { "pkg2/C.html",
            "<dt><span class=\"overrideSpecifyLabel\">Specified by:</span></dt>\n" +
            "<dd><code><a href=\"../pkg2/I.html#hello-T-\">hello</a></code>" +
            "&nbsp;in interface&nbsp;<code>" +
            "<a href=\"../pkg2/I.html\" title=\"interface in pkg2\">I</a>" +
            "&lt;java.lang.String&gt;</code></dd>"},

      //Make sure when no modifier appear in the class signature, the
      //signature is displayed correctly without extra space at the beginning.
      { "pkg/PrivateParent.html",
            "<pre>class <span class=\"typeNameLabel\">PrivateParent</span>"},

      { "pkg/PublicChild.html",
            "<pre>public class <span class=\"typeNameLabel\">PublicChild</span>"},
    };
    private static final String[][] NEGATED_TEST2 = {
        { "pkg/PrivateParent.html",
            "<pre> class <span class=\"typeNameLabel\">PrivateParent</span>"},
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestPrivateClasses tester = new TestPrivateClasses();
        tester.run(ARGS1, TEST1, NEGATED_TEST1);
        tester.run(ARGS2, TEST2, NEGATED_TEST2);
        tester.printSummary();
    }
}
