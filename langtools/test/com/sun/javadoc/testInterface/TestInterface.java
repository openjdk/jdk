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
 * @bug      4682448 4947464 5029946
 * @summary  Verify that the public modifier does not show up in the
 *           documentation for public methods, as recommended by the JLS.
 *           If A implements I and B extends A, B should be in the list of
 *           implementing classes in the documentation for I.
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester
 * @build    TestInterface
 * @run main TestInterface
 */

public class TestInterface extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4682448-4947464-5029946";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        {BUG_ID + FS + "pkg" + FS + "Interface.html",
            "<pre>int&nbsp;method()</pre>"},
        {BUG_ID + FS + "pkg" + FS + "Interface.html",
            "<pre>static final&nbsp;int field</pre>"},


        // Make sure known implementing class list is correct and omits type parameters.
        {BUG_ID + FS + "pkg" + FS + "Interface.html",
            "<dl>" + NL + "<dt>All Known Implementing Classes:</dt>" + NL +
            "<dd><a href=\"../pkg/Child.html\" title=\"class in pkg\">Child" +
            "</a>, <a href=\"../pkg/Parent.html\" title=\"class in pkg\">Parent" +
            "</a></dd>" + NL + "</dl>"},

         // Make sure "All Implemented Interfaces": has substituted type parameters
         {BUG_ID + FS + "pkg" + FS + "Child.html",
            "<dl>" + NL + "<dt>All Implemented Interfaces:</dt>" + NL +
            "<dd><a href=\"../pkg/Interface.html\" title=\"interface in pkg\">" +
            "Interface</a>&lt;T&gt;</dd>" + NL + "</dl>"
         },
         //Make sure Class Tree has substituted type parameters.
         {BUG_ID + FS + "pkg" + FS + "Child.html",
            "<ul class=\"inheritance\">" + NL + "<li>java.lang.Object</li>" + NL +
            "<li>" + NL + "<ul class=\"inheritance\">" + NL +
            "<li><a href=\"../pkg/Parent.html\" title=\"class in pkg\">" +
            "pkg.Parent</a>&lt;T&gt;</li>" + NL + "<li>" + NL +
            "<ul class=\"inheritance\">" + NL + "<li>pkg.Child&lt;T&gt;</li>" + NL +
            "</ul>" + NL + "</li>" + NL + "</ul>" + NL + "</li>" + NL + "</ul>"
         },
         //Make sure "Direct Know Subclasses" omits type parameters
        {BUG_ID + FS + "pkg" + FS + "Parent.html",
            "<dl>" + NL + "<dt>Direct Known Subclasses:</dt>" + NL +
            "<dd><a href=\"../pkg/Child.html\" title=\"class in pkg\">Child" +
            "</a></dd>" + NL + "</dl>"
        },
        //Make sure "Specified By" has substituted type parameters.
        {BUG_ID + FS + "pkg" + FS + "Child.html",
            "<dt><strong>Specified by:</strong></dt>" + NL +
            "<dd><code><a href=\"../pkg/Interface.html#method()\">method</a>" +
            "</code>&nbsp;in interface&nbsp;<code>" +
            "<a href=\"../pkg/Interface.html\" title=\"interface in pkg\">" +
            "Interface</a>&lt;<a href=\"../pkg/Child.html\" title=\"type parameter in Child\">" +
            "T</a>&gt;</code></dd>"
         },
        //Make sure "Overrides" has substituted type parameters.
        {BUG_ID + FS + "pkg" + FS + "Child.html",
            "<dt><strong>Overrides:</strong></dt>" + NL +
            "<dd><code><a href=\"../pkg/Parent.html#method()\">method</a>" +
            "</code>&nbsp;in class&nbsp;<code><a href=\"../pkg/Parent.html\" " +
            "title=\"class in pkg\">Parent</a>&lt;<a href=\"../pkg/Child.html\" " +
            "title=\"type parameter in Child\">T</a>&gt;</code></dd>"
         },
    };
    private static final String[][] NEGATED_TEST = {
        {BUG_ID + FS + "pkg" + FS + "Interface.html",
            "public int&nbsp;method()"},
        {BUG_ID + FS + "pkg" + FS + "Interface.html",
            "public static final&nbsp;int field"},
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestInterface tester = new TestInterface();
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
