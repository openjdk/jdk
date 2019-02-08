/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8157000 8192850 8182765
 * @summary  test the behavior of --override-methods option
 * @library  ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    javadoc.tester.*
 * @run main TestOverrideMethods
 */

import javadoc.tester.JavadocTester;

public class TestOverrideMethods  extends JavadocTester {
    public static void main(String... args) throws Exception {
        TestOverrideMethods tester = new TestOverrideMethods();
        tester.runTests();
    }

    @Test
    public void testInvalidOption() {
        // Make sure an invalid argument fails
        javadoc("-d", "out-bad-option",
                "-sourcepath", testSrc,
                "-javafx",
                "--disable-javafx-strict-checks",
                "--override-methods=nonsense",
                "pkg5");

        checkExit(Exit.CMDERR);
    }

    @Test
    public void testDetail() {
        // Make sure the option works
        javadoc("-d", "out-detail",
                "-sourcepath", testSrc,
                "-javafx",
                "--disable-javafx-strict-checks",
                "--override-methods=detail",
                "pkg5");

        checkExit(Exit.OK);
    }

    @Test
    public void testSummary() {
        javadoc("-d", "out-summary",
                "-sourcepath", testSrc,
                "-javafx",
                "--disable-javafx-strict-checks",
                "--override-methods=summary",
                "pkg5");

        checkExit(Exit.OK);

        checkOrder("pkg5/Classes.C.html",
                // Check properties
                "Properties declared in class&nbsp;pkg5.<a href=\"Classes.P.html",
                "Classes.P",
                "Classes.P.html#rateProperty\">rate",

                // Check nested classes
                "Nested classes/interfaces declared in class&nbsp;pkg5.",
                "Classes.P",
                "Classes.P.PN.html",
                "Classes.P.PN.html",
                "type parameter in Classes.P.PN\">K",
                "type parameter in Classes.P.PN",
                "V",

                // Check fields
                "Fields declared in class&nbsp;pkg5.<a href=\"Classes.P.html",
                "Classes.P",
                "Classes.P.html#field0\">field0",

                // Check method summary
                "Method Summary",
                "void",
                "#m1()\">m1",
                "A modified method",

                "void",
                "#m4(java.lang.String,java.lang.String)\">m4",
                "java.lang.String&nbsp;k,",
                "java.lang.String",
                "&nbsp;v)",

                // Check footnotes
                "Methods declared in class&nbsp;pkg5.<a href=\"Classes.GP.html",
                "Classes.GP",
                "Classes.GP.html#m0()\">m0",

                // Check method details for override
                "overrideSpecifyLabel",
                "Overrides:",
                "Classes.GP.html#m7()\">m7",
                "in class",
                "Classes.GP.html",
                "Classes.GP"
        );

        checkOrder("pkg5/Classes.C.html",
                // Check footnotes 2
                "Methods declared in class&nbsp;pkg5.",
                "Classes.P.html#getRate()\">getRate",
                "Classes.P.html#m2()\">m2",
                "Classes.P.html#m3()\">m3",
                "Classes.P.html#m4(K,V)\">m4",
                "Classes.P.html#rateProperty()\">rateProperty",
                "Classes.P.html#setRate(double)\">setRate",

                // Check @link
                "A test of links to the methods in this class. <p>\n",
                "Classes.GP.html#m0()",
                "Classes.GP.m0()",
                "#m1()",
                "m1()",
                "Classes.P.html#m2()",
                "Classes.P.m2()",
                "Classes.P.html#m3()",
                "Classes.P.m3()",
                "m4(java.lang.String,java.lang.String)",
                "Classes.P.html#m5()",
                "Classes.P.m5()",
                "#m6()",
                "m6()",
                "#m7()",
                "m7()",
                "End of links",

                // Check @see
                "See Also:",
                "Classes.GP.html#m0()",
                "Classes.GP.m0()",
                "#m1()",
                "m1()",
                "Classes.P.html#m2()",
                "Classes.P.m2()",
                "Classes.P.html#m3()",
                "Classes.P.m3()",
                "#m4(java.lang.String,java.lang.String)",
                "m4(String k, String v)",
                "Classes.P.html#m5()\"><code>Classes.P.m5()",
                "#m6()\"><code>m6()",
                "#m7()\"><code>m7()"
        );

        // Tests for interfaces

        // Make sure the static methods in the super interface
        // do not make it to this interface
        checkOutput("pkg5/Interfaces.D.html", false,
            "msd", "msn");

        checkOrder("pkg5/Interfaces.D.html",
                "Start of links <p>",
                "Interfaces.A.html#m0()\"><code>Interfaces.A.m0()",
                "Interfaces.A.html#m1()\"><code>Interfaces.A.m1()",
                "Interfaces.A.html#m2()\"><code>Interfaces.A.m2()",
                "Interfaces.A.html#m3()\"><code>Interfaces.A.m3()",
                "#m()\"><code>m()",
                "#n()\"><code>n()",
                "Interfaces.C.html#o()\"><code>Interfaces.C.o()",
                "End of links",

                // Check @see links
                "See Also:",
                "Interfaces.A.html#m0()\"><code>Interfaces.A.m0()",
                "Interfaces.A.html#m1()\"><code>Interfaces.A.m1()",
                "Interfaces.A.html#m2()\"><code>Interfaces.A.m2()",
                "Interfaces.A.html#m3()\"><code>Interfaces.A.m3()",
                "#m()\"><code>m()",
                "#n()\"><code>n()",
                "Interfaces.C.html#o()\"><code>Interfaces.C.o()",

                // Check properties
                "Properties declared in interface&nbsp;pkg5.<a href=\"Interfaces.A.html\" "
                + "title=\"interface in pkg5\">Interfaces.A</a>",

                // Check nested classes
                "Nested classes/interfaces declared in interface&nbsp;pkg5.",
                "Interfaces.A",
                "Interfaces.A.AA.html",
                "Interfaces.A.AA",

                // Check Fields
                "Fields declared in interface&nbsp;pkg5.<a href=\"Interfaces.A.html",
                "Interfaces.A.html#f",
                "Interfaces.A.html#QUOTE\">QUOTE",
                "Interfaces.A.html#rate\">rate",

                // Check Method Summary
                "Method Summary",
                "#m()\">m",
                "#n()\">n",

                // Check footnotes
                "Methods declared in interface&nbsp;pkg5.<a href=\"Interfaces.A.html",
                "Interfaces.A.html#getRate()\">getRate",
                "Interfaces.A.html#rateProperty()\">rateProperty",
                "Interfaces.A.html#setRate(double)",
                "Methods declared in interface&nbsp;pkg5.<a href=\"Interfaces.B.html",
                "Interfaces.B.html#m1()\">m1",
                "Interfaces.B.html#m3()\">m3",
                "Methods declared in interface&nbsp;pkg5.<a href=\"Interfaces.C.html",
                "<a href=\"Interfaces.C.html#o()\">o</a>"
        );

        // Test synthetic values and valuesof of an enum.
        checkOrder("index-all.html",
                "<h2 class=\"title\">M</h2>",
                "<a href=\"pkg5/Interfaces.C.html#m()\">m()",
                "<a href=\"pkg5/Interfaces.D.html#m()\">m()</a>",
                "<a href=\"pkg5/Classes.GP.html#m0()\">m0()",
                "<a href=\"pkg5/Interfaces.A.html#m0()\">m0()</a>",
                "<a href=\"pkg5/Classes.C.html#m1()\">m1()</a>",
                "<a href=\"pkg5/Classes.P.html#m1()\">m1()</a>",
                "<a href=\"pkg5/Interfaces.A.html#m1()\">m1()</a>",
                "<a href=\"pkg5/Interfaces.B.html#m1()\">m1()</a>",
                "<a href=\"pkg5/Classes.P.html#m2()\">m2()</a>",
                "<a href=\"pkg5/Interfaces.A.html#m2()\">m2()</a>",
                "<a href=\"pkg5/Classes.P.html#m3()\">m3()</a>",
                "<a href=\"pkg5/Interfaces.A.html#m3()\">m3()</a>",
                "<a href=\"pkg5/Interfaces.B.html#m3()\">m3()</a>",
                "<a href=\"pkg5/Classes.C.html#m4(java.lang.String,java.lang.String)\">m4(String, String)</a>",
                "<a href=\"pkg5/Classes.P.html#m4(K,V)\">m4(K, V)</a>",
                "<a href=\"pkg5/Classes.P.html#m5()\">m5()</a>",
                "<a href=\"pkg5/Classes.C.html#m6()\">m6()</a>",
                "<a href=\"pkg5/Classes.P.html#m6()\">m6()</a>",
                "<a href=\"pkg5/Classes.C.html#m7()\">m7()</a>",
                "<a href=\"pkg5/Classes.GP.html#m7()\">m7()</a>",
                "Returns the enum constant of this type with the specified name.",
                "Returns an array containing the constants of this enum type, in\n" +
                        "the order they are declared."
        );
    }
}
