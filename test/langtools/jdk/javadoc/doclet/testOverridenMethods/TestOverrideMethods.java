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
 * @bug 8157000
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

        checkOutput("pkg5/Classes.C.html", true,
                // Check properties
                "<h3>Properties declared in class&nbsp;pkg5.<a href=\"../pkg5/Classes.P.html\" "
                + "title=\"class in pkg5\">Classes.P</a></h3>\n"
                + "<code><a href=\"../pkg5/Classes.P.html#rateProperty\">rate</a>",

                // Check nested classes
                "<h3>Nested classes/interfaces declared in class&nbsp;pkg5."
                + "<a href=\"../pkg5/Classes.P.html\" title=\"class in pkg5\">Classes.P</a></h3>\n"
                + "<code><a href=\"../pkg5/Classes.P.PN.html\" title=\"class in pkg5\">"
                + "Classes.P.PN</a>&lt;<a href=\"../pkg5/Classes.P.PN.html\" "
                + "title=\"type parameter in Classes.P.PN\">K</a>,"
                + "<a href=\"../pkg5/Classes.P.PN.html\" title=\"type parameter in Classes.P.PN\">"
                + "V</a>&gt;</code></li>\n",

                // Check fields
                "<h3>Fields declared in class&nbsp;pkg5.<a href=\"../pkg5/Classes.P.html\" "
                + "title=\"class in pkg5\">Classes.P</a></h3>\n"
                + "<code><a href=\"../pkg5/Classes.P.html#field0\">field0</a></code></li>\n",

                // Check method summary
                "<td class=\"colFirst\"><code>void</code></td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><code><span class=\"memberNameLink\">"
                + "<a href=\"../pkg5/Classes.C.html#m1--\">m1</a></span>()</code></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\">A modified method</div>\n",

                "<td class=\"colFirst\"><code>void</code></td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><code><span class=\"memberNameLink\">"
                + "<a href=\"../pkg5/Classes.C.html#m4-java.lang.String-java.lang.String-\">m4"
                + "</a></span>&#8203;(java.lang.String&nbsp;k,\n"
                + "  java.lang.String&nbsp;v)</code></th>\n",

                // Check footnotes
                "<h3>Methods declared in class&nbsp;pkg5.<a href=\"../pkg5/Classes.GP.html\" "
                + "title=\"class in pkg5\">Classes.GP</a></h3>\n"
                + "<code><a href=\"../pkg5/Classes.GP.html#m0--\">m0</a>",

                // Check method details for override
                "<dt><span class=\"overrideSpecifyLabel\">Overrides:</span></dt>\n"
                + "<dd><code><a href=\"../pkg5/Classes.GP.html#m7--\">m7</a>"
                + "</code>&nbsp;in class&nbsp;<code><a href=\"../pkg5/Classes.GP.html\" "
                + "title=\"class in pkg5\">Classes.GP</a></code></dd>\n"
        );

        // Check footnotes 2
        checkOrder("pkg5/Classes.C.html",
                "Methods declared in class&nbsp;pkg5.",
                "<code><a href=\"../pkg5/Classes.P.html#getRate--\">getRate</a>, ",
                "<a href=\"../pkg5/Classes.P.html#m2--\">m2</a>, ",
                "<a href=\"../pkg5/Classes.P.html#m3--\">m3</a>, ",
                "<a href=\"../pkg5/Classes.P.html#m4-K-V-\">m4</a>, ",
                "<a href=\"../pkg5/Classes.P.html#rateProperty--\">rateProperty</a>, ",
                "<a href=\"../pkg5/Classes.P.html#setRate-double-\">setRate</a>"
        );

        // Check @link
        checkOrder("pkg5/Classes.C.html",
                "A test of links to the methods in this class. <p>\n",
                "<a href=\"../pkg5/Classes.GP.html#m0--\"><code>Classes.GP.m0()</code></a>",
                "<a href=\"../pkg5/Classes.C.html#m1--\"><code>m1()</code></a>",
                "<a href=\"../pkg5/Classes.P.html#m2--\"><code>Classes.P.m2()</code></a>",
                "<a href=\"../pkg5/Classes.P.html#m3--\"><code>Classes.P.m3()</code></a>",
                "<code>m4(java.lang.String,java.lang.String)</code></a>",
                "<a href=\"../pkg5/Classes.P.html#m5--\"><code>Classes.P.m5()</code></a>",
                "<a href=\"../pkg5/Classes.C.html#m6--\"><code>m6()</code></a>",
                "<a href=\"../pkg5/Classes.C.html#m7--\"><code>m7()</code></a>",
                "End of links"
        );

        // Check @see
        checkOrder("pkg5/Classes.C.html",
                "See Also:",
                "<a href=\"../pkg5/Classes.GP.html#m0--\"><code>Classes.GP.m0()</code>",
                "<a href=\"../pkg5/Classes.C.html#m1--\"><code>m1()</code></a>",
                "<a href=\"../pkg5/Classes.P.html#m2--\"><code>Classes.P.m2()</code></a>",
                "<a href=\"../pkg5/Classes.P.html#m3--\"><code>Classes.P.m3()</code></a>",
                "<a href=\"../pkg5/Classes.C.html#m4-java.lang.String-java.lang.String-\">"
                + "<code>m4(String k, String v)</code>",
                "<a href=\"../pkg5/Classes.P.html#m5--\"><code>Classes.P.m5()</code></a>",
                "<a href=\"../pkg5/Classes.C.html#m6--\"><code>m6()</code></a>",
                "<a href=\"../pkg5/Classes.C.html#m7--\"><code>m7()</code></a>"
        );

        checkOutput("pkg5/Interfaces.D.html", true,
                // Check properties
                "<h3>Properties declared in interface&nbsp;pkg5.<a href=\"../pkg5/Interfaces.A.html\" "
                + "title=\"interface in pkg5\">Interfaces.A</a>",

                // Check nested classes
                "<h3>Nested classes/interfaces declared in interface&nbsp;pkg5."
                + "<a href=\"../pkg5/Interfaces.A.html\" title=\"interface in pkg5\">"
                + "Interfaces.A</a></h3>\n"
                + "<code><a href=\"../pkg5/Interfaces.A.AA.html\" "
                + "title=\"interface in pkg5\">Interfaces.A.AA</a>",

                // Check fields
                "<h3>Fields declared in interface&nbsp;pkg5.<a href=\"../pkg5/Interfaces.A.html\" "
                + "title=\"interface in pkg5\">Interfaces.A</a></h3>\n"
                + "<code><a href=\"../pkg5/Interfaces.A.html#f\">f</a>",

                // Check method summary
                "<td class=\"colFirst\"><code>void</code></td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><code><span class=\"memberNameLink\">"
                + "<a href=\"../pkg5/Interfaces.D.html#m--\">m</a></span>()</code></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\">m in D</div>\n",

                "<td class=\"colFirst\"><code>void</code></td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><code><span class=\"memberNameLink\">"
                + "<a href=\"../pkg5/Interfaces.D.html#n--\">n</a></span>()</code></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\">n in D</div>\n",

                // Check footnote
                "<h3>Methods declared in interface&nbsp;pkg5.<a href=\"../pkg5/Interfaces.A.html\" "
                + "title=\"interface in pkg5\">Interfaces.A</a></h3>\n"
                + "<code><a href=\"../pkg5/Interfaces.A.html#getRate--\">getRate</a>, "
                + "<a href=\"../pkg5/Interfaces.A.html#rateProperty--\">rateProperty</a>, "
                + "<a href=\"../pkg5/Interfaces.A.html#setRate-double-\">setRate</a>",

                "<h3>Methods declared in interface&nbsp;pkg5.<a href=\"../pkg5/Interfaces.B.html\" "
                + "title=\"interface in pkg5\">Interfaces.B</a></h3>\n"
                + "<code><a href=\"../pkg5/Interfaces.B.html#m1--\">m1</a>, "
                + "<a href=\"../pkg5/Interfaces.B.html#m3--\">m3</a>",

                "<h3>Methods declared in interface&nbsp;pkg5.<a href=\"../pkg5/Interfaces.C.html\" "
                + "title=\"interface in pkg5\">Interfaces.C</a></h3>\n"
                + "<code><a href=\"../pkg5/Interfaces.C.html#o--\">o</a>"
        );

        checkOrder("pkg5/Interfaces.D.html",
                "Start of links <p>",
                "<a href=\"../pkg5/Interfaces.A.html#m0--\"><code>Interfaces.A.m0()</code></a>",
                "<a href=\"../pkg5/Interfaces.A.html#m1--\"><code>Interfaces.A.m1()</code></a>",
                "<a href=\"../pkg5/Interfaces.A.html#m2--\"><code>Interfaces.A.m2()</code></a>",
                "<a href=\"../pkg5/Interfaces.A.html#m3--\"><code>Interfaces.A.m3()</code></a>",
                "<a href=\"../pkg5/Interfaces.D.html#m--\"><code>m()</code></a>",
                "<a href=\"../pkg5/Interfaces.D.html#n--\"><code>n()</code></a>",
                "<a href=\"../pkg5/Interfaces.C.html#o--\"><code>Interfaces.C.o()</code></a>",
                "End of links");

        checkOrder("pkg5/Interfaces.D.html",
                "See Also:",
                "<a href=\"../pkg5/Interfaces.A.html#m0--\"><code>Interfaces.A.m0()</code></a>",
                "<a href=\"../pkg5/Interfaces.A.html#m1--\"><code>Interfaces.A.m1()</code></a>",
                "<a href=\"../pkg5/Interfaces.A.html#m2--\"><code>Interfaces.A.m2()</code></a>",
                "<a href=\"../pkg5/Interfaces.A.html#m3--\"><code>Interfaces.A.m3()</code></a>",
                "<a href=\"../pkg5/Interfaces.D.html#m--\"><code>m()</code></a>",
                "<a href=\"../pkg5/Interfaces.D.html#n--\"><code>n()</code></a>",
                "<a href=\"../pkg5/Interfaces.C.html#o--\"><code>Interfaces.C.o()</code></a>");

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
