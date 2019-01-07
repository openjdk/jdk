/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4780441 4874845 4978816 8014017 8016328 8025633 8026567 8175200 8182765
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
 * @library  ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    javadoc.tester.*
 * @run main TestPrivateClasses
 */
import javadoc.tester.JavadocTester;

public class TestPrivateClasses extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestPrivateClasses tester = new TestPrivateClasses();
        tester.runTests();
    }

    @Test
    public void testDefault() {
        javadoc("-d", "out-default",
                "-sourcepath", testSrc,
                "pkg", "pkg2");
        checkExit(Exit.OK);

        checkOutput("pkg/PublicChild.html", true,
                // Field inheritence from non-public superclass.
                "<a href=\"#fieldInheritedFromParent\">"
                + "fieldInheritedFromParent</a>",
                // Method inheritance from non-public superclass.
                "<a href=\"#methodInheritedFromParent(int)\">"
                + "methodInheritedFromParent</a>",
                // private class does not show up in tree
                "<ul class=\"inheritance\">\n"
                + "<li>java.lang.Object</li>\n"
                + "<li>\n"
                + "<ul class=\"inheritance\">\n"
                + "<li>pkg.PublicChild</li>\n"
                + "</ul>\n"
                + "</li>\n"
                + "</ul>",
                // Method is documented as though it is declared in the inheriting method.
                "<pre class=\"methodSignature\">public&nbsp;void&nbsp;methodInheritedFromParent&#8203;(int&nbsp;p1)",
                "<dl>\n"
                + "<dt>All Implemented Interfaces:</dt>\n"
                + "<dd><code><a href=\"PublicInterface.html\" title=\"interface in pkg\">"
                + "PublicInterface</a></code></dd>\n"
                + "</dl>");

        checkOutput("pkg/PublicChild.html", false,
                // Should not document that a method overrides method from private class.
                "<span class=\"overrideSpecifyLabel\">Overrides:</span>",
                // Should not document that a method specified by private interface.
                "<span class=\"overrideSpecifyLabel\">Specified by:</span>",
                // Should not mention that any documentation was copied.
                "Description copied from",
                // Don't extend private classes or interfaces
                "PrivateParent",
                "PrivateInterface");

        checkOutput("pkg/PublicChild.html", false,
                // Should not document comments from private inherited interfaces
                "<td class=\"colLast\"><code><span class=\"memberNameLink\">"
                + "<a href=\"#methodInterface(int)\">"
                + "methodInterface</a></span>&#8203;(int&nbsp;p1)</code>\n"
                + "<div class=\"block\">Comment from interface.</div>\n</td>",
                // and similarly one more
                "<td class=\"colLast\"><code><span class=\"memberNameLink\">"
                + "<a href=\"#methodInterface2(int)\">"
                + "methodInterface2</a></span>&#8203;(int&nbsp;p1)</code>\n"
                + "<div class=\"block\">Comment from interface.</div>\n</td>"
        );

        checkOutput("pkg/PublicInterface.html", true,
                // Field inheritance from non-public superinterface.
                "<a href=\"#fieldInheritedFromInterface\">"
                + "fieldInheritedFromInterface</a>",
                // Method inheritance from non-public superinterface.
                "<a href=\"#methodInterface(int)\">"
                + "methodInterface</a>",
                //Make sure implemented interfaces from private superclass are inherited
                "<dl>\n"
                + "<dt>All Known Implementing Classes:</dt>\n"
                + "<dd><code><a href=\"PublicChild.html\" title=\"class in pkg\">"
                + "PublicChild</a></code></dd>\n"
                + "</dl>");

        checkOutput("pkg/PublicInterface.html", false,
                "<span class=\"overrideSpecifyLabel\">Specified by:</span>",
                "Description copied from",
                "PrivateInterface",
                "All Superinterfaces");

        checkOutput("pkg2/C.html", false,
                //Generic interface method test.
                "This comment should get copied to the implementing class");

        checkOutput("pkg2/C.html", false,
                //Do not inherit private interface method with generic parameters.
                //This method has been implemented.
                "<span class=\"memberNameLink\"><a href=\"I.html#hello(T)\">hello</a></span>");

        checkOutput("constant-values.html", false,
                // Make inherited constant are documented correctly.
                "PrivateInterface");
    }

    @Test
    public void testDefault_html4() {
        javadoc("-d", "out-default-html4",
                "-html4",
                "-sourcepath", testSrc,
                "pkg", "pkg2");
        checkExit(Exit.OK);

        checkOutput("pkg/PublicChild.html", true,
                // Method inheritance from non-public superclass.
                "<a href=\"#methodInheritedFromParent-int-\">");

        checkOutput("pkg/PublicChild.html", false,
                // Should not document comments from private inherited interfaces
                "<td class=\"colLast\"><code><span class=\"memberNameLink\">"
                + "<a href=\"#methodInterface-int-\">"
                + "methodInterface</a></span>&#8203;(int&nbsp;p1)</code>\n"
                + "<div class=\"block\">Comment from interface.</div>\n</td>",
                // and similarly one more
                "<td class=\"colLast\"><code><span class=\"memberNameLink\">"
                + "<a href=\"#methodInterface2-int-\">"
                + "methodInterface2</a></span>&#8203;(int&nbsp;p1)</code>\n"
                + "<div class=\"block\">Comment from interface.</div>\n</td>"
        );

        checkOutput("pkg/PublicInterface.html", true,
                // Method inheritance from non-public superinterface.
                "<a href=\"#methodInterface-int-\">"
                + "methodInterface</a>");

        checkOutput("pkg2/C.html", false,
                //Do not inherit private interface method with generic parameters.
                //This method has been implemented.
                "<span class=\"memberNameLink\"><a href=\"I.html#hello-T-\">hello</a></span>");
    }

    @Test
    public void testPrivate() {
        javadoc("-d", "out-private",
                "-sourcepath", testSrc,
                "-private",
                "pkg", "pkg2");
        checkExit(Exit.OK);

        checkOutput("pkg/PublicChild.html", true,
                // Field inheritence from non-public superclass.
                "Fields inherited from class&nbsp;pkg."
                + "<a href=\"PrivateParent.html\" title=\"class in pkg\">"
                + "PrivateParent</a>",
                "<a href=\"PrivateParent.html#fieldInheritedFromParent\">"
                + "fieldInheritedFromParent</a>",
                // Method inheritence from non-public superclass.
                "Methods inherited from class&nbsp;pkg."
                + "<a href=\"PrivateParent.html\" title=\"class in pkg\">"
                + "PrivateParent</a>",
                "<a href=\"PrivateParent.html#methodInheritedFromParent(int)\">"
                + "methodInheritedFromParent</a>",
                // Should document that a method overrides method from private class.
                "<dt><span class=\"overrideSpecifyLabel\">Overrides:</span></dt>\n"
                + "<dd><code><a href=\"PrivateParent.html#methodOverridenFromParent(char%5B%5D,int,T,V,java.util.List)\">"
                + "methodOverridenFromParent</a></code>&nbsp;in class&nbsp;<code>"
                + "<a href=\"PrivateParent.html\" title=\"class in pkg\">"
                + "PrivateParent</a></code></dd>",
                // Should document that a method is specified by private interface.
                "<dt><span class=\"overrideSpecifyLabel\">Specified by:</span></dt>\n"
                + "<dd><code><a href=\"PrivateInterface.html#methodInterface(int)\">"
                + "methodInterface</a></code>&nbsp;in interface&nbsp;<code>"
                + "<a href=\"PrivateInterface.html\" title=\"interface in pkg\">"
                + "PrivateInterface</a></code></dd>",
                // Should mention that any documentation was copied.
                "Description copied from",
                // Extend documented private classes or interfaces
                "extends",
                "<dl>\n"
                + "<dt>All Implemented Interfaces:</dt>\n"
                + "<dd><code><a href=\"PrivateInterface.html\" title=\"interface in pkg\">"
                + "PrivateInterface</a></code>, "
                + "<code><a href=\"PublicInterface.html\" title=\"interface in pkg\">"
                + "PublicInterface</a></code></dd>\n"
                + "</dl>",
                "<pre>public class <span class=\"typeNameLabel\">PublicChild</span>");

        checkOutput("pkg/PublicInterface.html", true,
                // Field inheritence from non-public superinterface.
                "Fields inherited from interface&nbsp;pkg."
                + "<a href=\"PrivateInterface.html\" title=\"interface in pkg\">"
                + "PrivateInterface</a>",
                "<a href=\"PrivateInterface.html#fieldInheritedFromInterface\">"
                + "fieldInheritedFromInterface</a>",
                // Method inheritance from non-public superinterface.
                "Methods inherited from interface&nbsp;pkg."
                + "<a href=\"PrivateInterface.html\" title=\"interface in pkg\">"
                + "PrivateInterface</a>",
                // Extend documented private classes or interfaces
                "extends",
                "All Superinterfaces",
                //Make sure implemented interfaces from private superclass are inherited
                "<dl>\n"
                + "<dt>All Known Implementing Classes:</dt>\n"
                + "<dd><code><a href=\"PrivateParent.html\" title=\"class in pkg\">"
                + "PrivateParent</a></code>, "
                + "<code><a href=\"PublicChild.html\" title=\"class in pkg\">PublicChild"
                + "</a></code></dd>\n"
                + "</dl>");

        checkOutput("pkg/PrivateInterface.html", true,
                "<a href=\"#methodInterface(int)\">"
                + "methodInterface</a>"
        );

        checkOutput("pkg2/C.html", true,
                //Since private flag is used, we can document that private interface method
                //with generic parameters has been implemented.
                "<span class=\"descfrmTypeLabel\">Description copied from interface:&nbsp;<code>"
                + "<a href=\"I.html#hello(T)\">I</a></code></span>",
                "<dt><span class=\"overrideSpecifyLabel\">Specified by:</span></dt>\n"
                + "<dd><code><a href=\"I.html#hello(T)\">hello</a></code>"
                + "&nbsp;in interface&nbsp;<code>"
                + "<a href=\"I.html\" title=\"interface in pkg2\">I</a>"
                + "&lt;java.lang.String&gt;</code></dd>");

        checkOutput("pkg/PrivateParent.html", true,
                //Make sure when no modifier appear in the class signature, the
                //signature is displayed correctly without extra space at the beginning.
                "<pre>class <span class=\"typeNameLabel\">PrivateParent</span>");

        checkOutput("pkg/PrivateParent.html", false,
                "<pre> class <span class=\"typeNameLabel\">PrivateParent</span>");
    }

    @Test
    public void testPrivate_html4() {
        javadoc("-d", "out-private-html4",
                "-html4",
                "-sourcepath", testSrc,
                "-private",
                "pkg", "pkg2");
        checkExit(Exit.OK);

        checkOutput("pkg/PublicChild.html", true,
                "<a href=\"PrivateParent.html#methodInheritedFromParent-int-\">"
                + "methodInheritedFromParent</a>",
                // Should document that a method overrides method from private class.
                "<dt><span class=\"overrideSpecifyLabel\">Overrides:</span></dt>\n"
                + "<dd><code><a href=\"PrivateParent.html#methodOverridenFromParent-char:A-int-T-V-java.util.List-\">"
                + "methodOverridenFromParent</a></code>&nbsp;in class&nbsp;<code>"
                + "<a href=\"PrivateParent.html\" title=\"class in pkg\">"
                + "PrivateParent</a></code></dd>",
                // Should document that a method is specified by private interface.
                "<dt><span class=\"overrideSpecifyLabel\">Specified by:</span></dt>\n"
                + "<dd><code><a href=\"PrivateInterface.html#methodInterface-int-\">"
                + "methodInterface</a></code>&nbsp;in interface&nbsp;<code>"
                + "<a href=\"PrivateInterface.html\" title=\"interface in pkg\">"
                + "PrivateInterface</a></code></dd>");

        checkOutput("pkg/PrivateInterface.html", true,
                "<a href=\"#methodInterface-int-\">"
                + "methodInterface</a>"
        );

        checkOutput("pkg2/C.html", true,
                //Since private flag is used, we can document that private interface method
                //with generic parameters has been implemented.
                "<span class=\"descfrmTypeLabel\">Description copied from interface:&nbsp;<code>"
                + "<a href=\"I.html#hello-T-\">I</a></code></span>",
                "<dt><span class=\"overrideSpecifyLabel\">Specified by:</span></dt>\n"
                + "<dd><code><a href=\"I.html#hello-T-\">hello</a></code>"
                + "&nbsp;in interface&nbsp;<code>"
                + "<a href=\"I.html\" title=\"interface in pkg2\">I</a>"
                + "&lt;java.lang.String&gt;</code></dd>");
    }
}
