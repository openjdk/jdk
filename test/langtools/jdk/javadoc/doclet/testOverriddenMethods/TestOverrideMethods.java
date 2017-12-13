/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8157000 8192850
 * @summary  test the behavior of --override-methods option
 * @library  ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    JavadocTester
 * @run main TestOverrideMethods
 */

public class TestOverrideMethods  extends JavadocTester {
    public static void main(String... args) throws Exception {
        TestOverrideMethods tester = new TestOverrideMethods();
        tester.runTests();
    }

    @Test
    void testInvalidOption() {
        // Make sure an invalid argument fails
        javadoc("-d", "out-bad-option",
                "-sourcepath", testSrc,
                "-javafx",
                "--override-methods=nonsense",
                "pkg5");

        checkExit(Exit.CMDERR);
    }

    @Test
    void testDetail() {
        // Make sure the option works
        javadoc("-d", "out-detail",
                "-sourcepath", testSrc,
                "-javafx",
                "--override-methods=detail",
                "pkg5");

        checkExit(Exit.OK);
    }

    @Test
    void testSummary() {
        javadoc("-d", "out-summary",
                "-sourcepath", testSrc,
                "-javafx",
                "--override-methods=summary",
                "pkg5");

        checkExit(Exit.OK);

        checkOrder("pkg5/Classes.C.html",
                // Check properties
                "Properties declared in class&nbsp;pkg5.<a href=\"../pkg5/Classes.P.html",
                "Classes.P",
                "../pkg5/Classes.P.html#rateProperty\">rate",

                // Check nested classes
                "Nested classes/interfaces declared in class&nbsp;pkg5.",
                "Classes.P",
                "./pkg5/Classes.P.PN.html",
                "Classes.P.PN.html",
                "type parameter in Classes.P.PN\">K",
                "type parameter in Classes.P.PN",
                "V",

                // Check fields
                "Fields declared in class&nbsp;pkg5.<a href=\"../pkg5/Classes.P.html",
                "Classes.P",
                "../pkg5/Classes.P.html#field0\">field0",

                // Check method summary
                "Method Summary",
                "void",
                "../pkg5/Classes.C.html#m1--\">m1",
                "A modified method",

                "void",
                "../pkg5/Classes.C.html#m4-java.lang.String-java.lang.String-\">m4",
                "java.lang.String&nbsp;k,",
                "java.lang.String",
                "&nbsp;v)",

                // Check footnotes
                "Methods declared in class&nbsp;pkg5.<a href=\"../pkg5/Classes.GP.html",
                "Classes.GP",
                "../pkg5/Classes.GP.html#m0--\">m0",

                // Check method details for override
                "overrideSpecifyLabel",
                "Overrides:",
                "../pkg5/Classes.GP.html#m7--\">m7",
                "in class",
                "../pkg5/Classes.GP.html",
                "Classes.GP"
        );

        checkOrder("pkg5/Classes.C.html",
                // Check footnotes 2
                "Methods declared in class&nbsp;pkg5.",
                "../pkg5/Classes.P.html#getRate--\">getRate",
                "../pkg5/Classes.P.html#m2--\">m2",
                "../pkg5/Classes.P.html#m3--\">m3",
                "../pkg5/Classes.P.html#m4-K-V-\">m4",
                "../pkg5/Classes.P.html#rateProperty--\">rateProperty",
                "../pkg5/Classes.P.html#setRate-double-\">setRate",

                // Check @link
                "A test of links to the methods in this class. <p>\n",
                "../pkg5/Classes.GP.html#m0--",
                "Classes.GP.m0()",
                "../pkg5/Classes.C.html#m1--",
                "m1()",
                "../pkg5/Classes.P.html#m2--",
                "Classes.P.m2()",
                "../pkg5/Classes.P.html#m3--",
                "Classes.P.m3()",
                "m4(java.lang.String,java.lang.String)",
                "../pkg5/Classes.P.html#m5--",
                "Classes.P.m5()",
                "../pkg5/Classes.C.html#m6--",
                "m6()",
                "../pkg5/Classes.C.html#m7--",
                "m7()",
                "End of links",

                // Check @see
                "See Also:",
                "../pkg5/Classes.GP.html#m0--",
                "Classes.GP.m0()",
                "../pkg5/Classes.C.html#m1--",
                "m1()",
                "../pkg5/Classes.P.html#m2--",
                "Classes.P.m2()",
                "../pkg5/Classes.P.html#m3--",
                "Classes.P.m3()",
                "../pkg5/Classes.C.html#m4-java.lang.String-java.lang.String-",
                "m4(String k, String v)",
                "../pkg5/Classes.P.html#m5--\"><code>Classes.P.m5()",
                "../pkg5/Classes.C.html#m6--\"><code>m6()",
                "../pkg5/Classes.C.html#m7--\"><code>m7()"
        );

        // Tests for interfaces

        // Make sure the static methods in the super interface
        // do not make it to this interface
        checkOutput("pkg5/Interfaces.D.html", false,
            "msd", "msn");

        checkOrder("pkg5/Interfaces.D.html",
                "Start of links <p>",
                "../pkg5/Interfaces.A.html#m0--\"><code>Interfaces.A.m0()",
                "../pkg5/Interfaces.A.html#m1--\"><code>Interfaces.A.m1()",
                "../pkg5/Interfaces.A.html#m2--\"><code>Interfaces.A.m2()",
                "../pkg5/Interfaces.A.html#m3--\"><code>Interfaces.A.m3()",
                "../pkg5/Interfaces.D.html#m--\"><code>m()",
                "../pkg5/Interfaces.D.html#n--\"><code>n()",
                "../pkg5/Interfaces.C.html#o--\"><code>Interfaces.C.o()",
                "End of links",

                // Check @see links
                "See Also:",
                "../pkg5/Interfaces.A.html#m0--\"><code>Interfaces.A.m0()",
                "../pkg5/Interfaces.A.html#m1--\"><code>Interfaces.A.m1()",
                "../pkg5/Interfaces.A.html#m2--\"><code>Interfaces.A.m2()",
                "../pkg5/Interfaces.A.html#m3--\"><code>Interfaces.A.m3()",
                "../pkg5/Interfaces.D.html#m--\"><code>m()",
                "../pkg5/Interfaces.D.html#n--\"><code>n()",
                "../pkg5/Interfaces.C.html#o--\"><code>Interfaces.C.o()",

                // Check properties
                "Properties declared in interface&nbsp;pkg5.<a href=\"../pkg5/Interfaces.A.html\" "
                + "title=\"interface in pkg5\">Interfaces.A</a>",

                // Check nested classes
                "Nested classes/interfaces declared in interface&nbsp;pkg5.",
                "Interfaces.A",
                "../pkg5/Interfaces.A.AA.html",
                "Interfaces.A.AA",

                // Check Fields
                "Fields declared in interface&nbsp;pkg5.<a href=\"../pkg5/Interfaces.A.html",
                "../pkg5/Interfaces.A.html#f",
                "../pkg5/Interfaces.A.html#QUOTE\">QUOTE",
                "../pkg5/Interfaces.A.html#rate\">rate",

                // Check Method Summary
                "Method Summary",
                "../pkg5/Interfaces.D.html#m--\">m",
                "../pkg5/Interfaces.D.html#n--\">n",

                // Check footnotes
                "Methods declared in interface&nbsp;pkg5.<a href=\"../pkg5/Interfaces.A.html",
                "../pkg5/Interfaces.A.html#getRate--\">getRate",
                "../pkg5/Interfaces.A.html#rateProperty--\">rateProperty",
                "../pkg5/Interfaces.A.html#setRate-double-",
                "Methods declared in interface&nbsp;pkg5.<a href=\"../pkg5/Interfaces.B.html",
                "../pkg5/Interfaces.B.html#m1--\">m1",
                "../pkg5/Interfaces.B.html#m3--\">m3",
                "Methods declared in interface&nbsp;pkg5.<a href=\"../pkg5/Interfaces.C.html",
                "<a href=\"../pkg5/Interfaces.C.html#o--\">o</a>"
        );

        // Test synthetic values and valuesof of an enum.
        checkOrder("index-all.html",
                "<h2 class=\"title\">M</h2>",
                "<a href=\"pkg5/Interfaces.C.html#m--\">m()",
                "<a href=\"pkg5/Interfaces.D.html#m--\">m()</a>",
                "<a href=\"pkg5/Classes.GP.html#m0--\">m0()",
                "<a href=\"pkg5/Interfaces.A.html#m0--\">m0()</a>",
                "<a href=\"pkg5/Classes.C.html#m1--\">m1()</a>",
                "<a href=\"pkg5/Classes.P.html#m1--\">m1()</a>",
                "<a href=\"pkg5/Interfaces.A.html#m1--\">m1()</a>",
                "<a href=\"pkg5/Interfaces.B.html#m1--\">m1()</a>",
                "<a href=\"pkg5/Classes.P.html#m2--\">m2()</a>",
                "<a href=\"pkg5/Interfaces.A.html#m2--\">m2()</a>",
                "<a href=\"pkg5/Classes.P.html#m3--\">m3()</a>",
                "<a href=\"pkg5/Interfaces.A.html#m3--\">m3()</a>",
                "<a href=\"pkg5/Interfaces.B.html#m3--\">m3()</a>",
                "<a href=\"pkg5/Classes.C.html#m4-java.lang.String-java.lang.String-\">m4(String, String)</a>",
                "<a href=\"pkg5/Classes.P.html#m4-K-V-\">m4(K, V)</a>",
                "<a href=\"pkg5/Classes.P.html#m5--\">m5()</a>",
                "<a href=\"pkg5/Classes.C.html#m6--\">m6()</a>",
                "<a href=\"pkg5/Classes.P.html#m6--\">m6()</a>",
                "<a href=\"pkg5/Classes.C.html#m7--\">m7()</a>",
                "<a href=\"pkg5/Classes.GP.html#m7--\">m7()</a>",
                "Returns the enum constant of this type with the specified name.",
                "Returns an array containing the constants of this enum type, in\n" +
                        "the order they are declared."
        );
    }
}
