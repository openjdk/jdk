/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7112427 8012295 8025633 8026567 8061305 8081854 8150130 8162363
 *      8167967 8172528 8175200 8178830
 * @summary Test of the JavaFX doclet features.
 * @author jvalenta
 * @library ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build JavadocTester
 * @run main TestJavaFX
 */

public class TestJavaFX extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestJavaFX tester = new TestJavaFX();
        tester.runTests();
    }

    @Test
    void test1() {
        javadoc("-d", "out1",
                "-sourcepath", testSrc,
                "-javafx",
                "-package",
                "pkg1");
        checkExit(Exit.OK);

        checkOutput("pkg1/C.html", true,
                "<dt><span class=\"seeLabel\">See Also:</span></dt>\n"
                + "<dd><a href=\"../pkg1/C.html#getRate--\"><code>getRate()</code></a>, \n"
                + "<a href=\"../pkg1/C.html#setRate-double-\">"
                + "<code>setRate(double)</code></a></dd>",
                "<pre>public final&nbsp;void&nbsp;setRate&#8203;(double&nbsp;value)</pre>\n"
                + "<div class=\"block\">Sets the value of the property rate.</div>\n"
                + "<dl>\n"
                + "<dt><span class=\"simpleTagLabel\">Property description:</span></dt>",
                "<pre>public final&nbsp;double&nbsp;getRate&#8203;()</pre>\n"
                + "<div class=\"block\">Gets the value of the property rate.</div>\n"
                + "<dl>\n"
                + "<dt><span class=\"simpleTagLabel\">Property description:</span></dt>",
                "<td class=\"colFirst\"><code><a href=\"../pkg1/C.DoubleProperty.html\" "
                + "title=\"class in pkg1\">C.DoubleProperty</a></code></td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><code><span class=\"memberNameLink\">"
                + "<a href=\"../pkg1/C.html#rateProperty\">rate</a></span></code></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\">Defines the direction/speed at which the "
                + "<code>Timeline</code> is expected to\n"
                + " be played.</div>\n</td>",
                "<span class=\"simpleTagLabel\">Default value:</span>",
                "<span class=\"simpleTagLabel\">Since:</span></dt>\n"
                + "<dd>JavaFX 8.0</dd>",
                "<p>Sets the value of the property <code>Property</code>",
                "<p>Gets the value of the property <code>Property</code>",
                "<span class=\"simpleTagLabel\">Property description:</span>",
                "<th class=\"colSecond\" scope=\"row\"><code><span class=\"memberNameLink\">"
                + "<a href=\"../pkg1/C.html#setTestMethodProperty--\">"
                + "setTestMethodProperty</a></span>&#8203;()</code></th>",
                "<th class=\"colSecond\" scope=\"row\"><code><span class=\"memberNameLink\">"
                + "<a href=\"../pkg1/C.html#pausedProperty\">paused</a></span></code></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\">Defines if paused.</div>",
                "<h4>paused</h4>\n"
                + "<pre>public final&nbsp;<a href=\"../pkg1/C.BooleanProperty.html\" "
                + "title=\"class in pkg1\">C.BooleanProperty</a> pausedProperty</pre>\n"
                + "<div class=\"block\">Defines if paused. The second line.</div>",
                "<h4>isPaused</h4>\n"
                + "<pre>public final&nbsp;double&nbsp;isPaused&#8203;()</pre>\n"
                + "<div class=\"block\">Gets the value of the property paused.</div>",
                "<h4>setPaused</h4>\n"
                + "<pre>public final&nbsp;void&nbsp;setPaused&#8203;(boolean&nbsp;value)</pre>\n"
                + "<div class=\"block\">Sets the value of the property paused.</div>\n"
                + "<dl>\n"
                + "<dt><span class=\"simpleTagLabel\">Property description:</span></dt>\n"
                + "<dd>Defines if paused. The second line.</dd>\n"
                + "<dt><span class=\"simpleTagLabel\">Default value:</span></dt>\n"
                + "<dd>false</dd>",
                "<h4>isPaused</h4>\n"
                + "<pre>public final&nbsp;double&nbsp;isPaused&#8203;()</pre>\n"
                + "<div class=\"block\">Gets the value of the property paused.</div>\n"
                + "<dl>\n"
                + "<dt><span class=\"simpleTagLabel\">Property description:</span></dt>\n"
                + "<dd>Defines if paused. The second line.</dd>\n"
                + "<dt><span class=\"simpleTagLabel\">Default value:</span></dt>\n"
                + "<dd>false</dd>",
                "<h4>rate</h4>\n"
                + "<pre>public final&nbsp;<a href=\"../pkg1/C.DoubleProperty.html\" "
                + "title=\"class in pkg1\">C.DoubleProperty</a> rateProperty</pre>\n"
                + "<div class=\"block\">Defines the direction/speed at which the "
                + "<code>Timeline</code> is expected to\n"
                + " be played. This is the second line.</div>",
                "<h4>setRate</h4>\n"
                + "<pre>public final&nbsp;void&nbsp;setRate&#8203;(double&nbsp;value)</pre>\n"
                + "<div class=\"block\">Sets the value of the property rate.</div>\n"
                + "<dl>\n"
                + "<dt><span class=\"simpleTagLabel\">Property description:</span></dt>\n"
                + "<dd>Defines the direction/speed at which the <code>Timeline</code> is expected to\n"
                + " be played. This is the second line.</dd>\n"
                + "<dt><span class=\"simpleTagLabel\">Default value:</span></dt>\n"
                + "<dd>11</dd>\n"
                + "<dt><span class=\"simpleTagLabel\">Since:</span></dt>\n"
                + "<dd>JavaFX 8.0</dd>",
                "<h4>getRate</h4>\n"
                + "<pre>public final&nbsp;double&nbsp;getRate&#8203;()</pre>\n"
                + "<div class=\"block\">Gets the value of the property rate.</div>\n"
                + "<dl>\n"
                + "<dt><span class=\"simpleTagLabel\">Property description:</span></dt>\n"
                + "<dd>Defines the direction/speed at which the <code>Timeline</code> is expected to\n"
                + " be played. This is the second line.</dd>\n"
                + "<dt><span class=\"simpleTagLabel\">Default value:</span></dt>\n"
                + "<dd>11</dd>\n"
                + "<dt><span class=\"simpleTagLabel\">Since:</span></dt>\n"
                + "<dd>JavaFX 8.0</dd>",
                "<h3>Property Summary</h3>\n"
                + "<table class=\"memberSummary\" summary=\"Property Summary table, listing properties, and an explanation\">\n"
                + "<caption><span>Properties</span><span class=\"tabEnd\">&nbsp;</span></caption>",
                "");

        checkOutput("pkg1/C.html", false,
                "A()",
                "<h3>Property Summary</h3>\n"
                + "<table class=\"memberSummary\" summary=\"Property Summary table, listing properties, and an explanation\">\n"
                + "<caption><span id=\"t0\" class=\"activeTableTab\"><span>All Methods</span><span class=\"tabEnd\">&nbsp;</span>"
                + "</span><span id=\"t2\" class=\"tableTab\"><span><a href=\"javascript:show(2);\">Instance Methods</a>"
                + "</span><span class=\"tabEnd\">&nbsp;</span></span><span id=\"t4\" class=\"tableTab\"><span>"
                + "<a href=\"javascript:show(8);\">Concrete Methods</a></span><span class=\"tabEnd\">&nbsp;</span></span>"
                + "</caption>");

        checkOutput("index-all.html", true,
                "<div class=\"block\">Gets the value of the property paused.</div>",
                "<div class=\"block\">Defines if paused.</div>");

        checkOutput("pkg1/D.html", true,
                "<h3>Properties inherited from class&nbsp;pkg1."
                    + "<a href=\"../pkg1/C.html\" title=\"class in pkg1\">C</a></h3>\n"
                    + "<code><a href=\"../pkg1/C.html#pausedProperty\">"
                    + "paused</a>, <a href=\"../pkg1/C.html#rateProperty\">rate</a></code></li>");

        checkOutput("pkg1/D.html", false, "shouldNotAppear");
    }
    /*
     * Test with -javafx option enabled, to ensure property getters and setters
     * are treated correctly.
     */
    @Test
    void test2() {
        javadoc("-d", "out2a",
                "-sourcepath", testSrc,
                "-javafx",
                "-package",
                "pkg2");
        checkExit(Exit.OK);
        checkOutput("pkg2/Test.html", true,
                "<h3>Property Detail</h3>\n"
                + "<a name=\"betaProperty\">\n"
                + "<!--   -->\n"
                + "</a>\n"
                + "<ul class=\"blockList\">\n"
                + "<li class=\"blockList\">\n"
                + "<h4>beta</h4>\n"
                + "<pre>public&nbsp;java.lang.Object betaProperty</pre>\n"
                + "</li>\n"
                + "</ul>\n"
                + "<a name=\"gammaProperty\">\n"
                + "<!--   -->\n"
                + "</a>\n"
                + "<ul class=\"blockList\">\n"
                + "<li class=\"blockList\">\n"
                + "<h4>gamma</h4>\n"
                + "<pre>public final&nbsp;java.util.List&lt;java.lang.String&gt; gammaProperty</pre>\n"
                + "</li>\n"
                + "</ul>\n"
                + "<a name=\"deltaProperty\">\n"
                + "<!--   -->\n"
                + "</a>\n"
                + "<ul class=\"blockListLast\">\n"
                + "<li class=\"blockList\">\n"
                + "<h4>delta</h4>\n"
                + "<pre>public final&nbsp;java.util.List&lt;"
                + "java.util.Set&lt;? super java.lang.Object&gt;&gt; deltaProperty</pre>\n"
                + "</li>\n"
                + "</ul>\n"
                + "</li>\n"
                + "</ul>",
                "<h3>Property Summary</h3>\n"
                + "<table class=\"memberSummary\" summary=\"Property Summary table, listing properties, and an explanation\">\n"
                + "<caption><span>Properties</span><span class=\"tabEnd\">&nbsp;</span></caption>");

        checkOutput("pkg2/Test.html", false,
                "<h3>Property Summary</h3>\n"
                + "<table class=\"memberSummary\" summary=\"Property Summary table, listing properties, and an explanation\">\n"
                + "<caption><span id=\"t0\" class=\"activeTableTab\"><span>All Methods</span><span class=\"tabEnd\">&nbsp;</span>"
                + "</span><span id=\"t2\" class=\"tableTab\"><span><a href=\"javascript:show(2);\">Instance Methods</a>"
                + "</span><span class=\"tabEnd\">&nbsp;</span></span><span id=\"t4\" class=\"tableTab\"><span>"
                + "<a href=\"javascript:show(8);\">Concrete Methods</a></span><span class=\"tabEnd\">&nbsp;</span></span>"
                + "</caption>");
    }

    /*
     * Test without -javafx option, to ensure property getters and setters
     * are treated just like any other java method.
     */
    @Test
    void test3() {
        javadoc("-d", "out2b",
                "-sourcepath", testSrc,
                "-package",
                "pkg2");
        checkExit(Exit.OK);
        checkOutput("pkg2/Test.html", false, "<h3>Property Summary</h3>");
        checkOutput("pkg2/Test.html", true,
                "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>\n"
                + "<th class=\"colSecond\" scope=\"col\">Method</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "<tr id=\"i0\" class=\"altColor\">\n"
                + "<td class=\"colFirst\"><code>&lt;T&gt;&nbsp;java.lang.Object</code></td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><code><span class=\"memberNameLink\">"
                + "<a href=\"../pkg2/Test.html#alphaProperty-java.util.List-\">alphaProperty</a>"
                + "</span>&#8203;(java.util.List&lt;T&gt;&nbsp;foo)</code></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>\n"
                + "</tr>\n"
                + "<tr id=\"i1\" class=\"rowColor\">\n"
                + "<td class=\"colFirst\"><code>java.lang.Object</code></td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><code><span class=\"memberNameLink\">"
                + "<a href=\"../pkg2/Test.html#betaProperty--\">betaProperty</a></span>&#8203;()</code></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>\n"
                + "</tr>\n"
                + "<tr id=\"i2\" class=\"altColor\">\n"
                + "<td class=\"colFirst\"><code>java.util.List&lt;java.util.Set&lt;? super java.lang.Object&gt;&gt;"
                + "</code></td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><code><span class=\"memberNameLink\">"
                + "<a href=\"../pkg2/Test.html#deltaProperty--\">deltaProperty</a></span>&#8203;()</code></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>\n"
                + "</tr>\n"
                + "<tr id=\"i3\" class=\"rowColor\">\n"
                + "<td class=\"colFirst\"><code>java.util.List&lt;java.lang.String&gt;</code></td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><code><span class=\"memberNameLink\">"
                + "<a href=\"../pkg2/Test.html#gammaProperty--\">gammaProperty</a></span>&#8203;()</code></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>"
        );
    }

    /*
     * Force the doclet to emit a warning when processing a synthesized,
     * DocComment, and ensure that the run succeeds, using the newer
     * --javafx flag.
     */
    @Test
    void test4() {
        javadoc("-d", "out4",
                "--javafx",
                "-Xdoclint:none",
                "-sourcepath", testSrc,
                "-package",
                "pkg4");
        checkExit(Exit.OK);

        // make sure the doclet indeed emits the warning
        checkOutput(Output.OUT, true, "C.java:0: warning - invalid usage of tag >");
    }
}
