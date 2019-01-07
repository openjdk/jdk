/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
 *      8167967 8172528 8175200 8178830 8182257 8186332 8182765 8025091
 *      8203791 8184205
 * @summary Test of the JavaFX doclet features.
 * @author jvalenta
 * @library ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.*
 * @run main TestJavaFX
 */

import javadoc.tester.JavadocTester;

public class TestJavaFX extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestJavaFX tester = new TestJavaFX();
        tester.runTests();
    }

    @Test
    public void test1() {
        javadoc("-d", "out1",
                "-sourcepath", testSrc,
                "-javafx",
                "--disable-javafx-strict-checks",
                "-package",
                "pkg1");
        checkExit(Exit.OK);

        checkOutput("pkg1/C.html", true,
                "<dt><span class=\"seeLabel\">See Also:</span></dt>\n"
                + "<dd><a href=\"#getRate()\"><code>getRate()</code></a>, \n"
                + "<a href=\"#setRate(double)\"><code>setRate(double)</code></a></dd>",
                "<pre class=\"methodSignature\">public final&nbsp;void&nbsp;setRate&#8203;(double&nbsp;value)</pre>\n"
                + "<div class=\"block\">Sets the value of the property rate.</div>\n"
                + "<dl>\n"
                + "<dt><span class=\"simpleTagLabel\">Property description:</span></dt>",
                "<pre class=\"methodSignature\">public final&nbsp;double&nbsp;getRate()</pre>\n"
                + "<div class=\"block\">Gets the value of the property rate.</div>\n"
                + "<dl>\n"
                + "<dt><span class=\"simpleTagLabel\">Property description:</span></dt>",
                "<td class=\"colFirst\"><code><a href=\"C.DoubleProperty.html\" "
                + "title=\"class in pkg1\">C.DoubleProperty</a></code></td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><code><span class=\"memberNameLink\">"
                + "<a href=\"#rateProperty\">rate</a></span></code></th>\n"
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
                + "<a href=\"#setTestMethodProperty()\">"
                + "setTestMethodProperty</a></span>()</code></th>",
                "<th class=\"colSecond\" scope=\"row\"><code><span class=\"memberNameLink\">"
                + "<a href=\"#pausedProperty\">paused</a></span></code></th>\n"
                + "<td class=\"colLast\">\n"
                + "<div class=\"block\">Defines if paused.</div>",
                "<h4>paused</h4>\n"
                + "<pre>public final&nbsp;<a href=\"C.BooleanProperty.html\" "
                + "title=\"class in pkg1\">C.BooleanProperty</a> pausedProperty</pre>\n"
                + "<div class=\"block\">Defines if paused. The second line.</div>",
                "<h4>isPaused</h4>\n"
                + "<pre class=\"methodSignature\">public final&nbsp;double&nbsp;isPaused()</pre>\n"
                + "<div class=\"block\">Gets the value of the property paused.</div>",
                "<h4>setPaused</h4>\n"
                + "<pre class=\"methodSignature\">public final&nbsp;void&nbsp;setPaused&#8203;(boolean&nbsp;value)</pre>\n"
                + "<div class=\"block\">Sets the value of the property paused.</div>\n"
                + "<dl>\n"
                + "<dt><span class=\"simpleTagLabel\">Property description:</span></dt>\n"
                + "<dd>Defines if paused. The second line.</dd>\n"
                + "<dt><span class=\"simpleTagLabel\">Default value:</span></dt>\n"
                + "<dd>false</dd>",
                "<h4>isPaused</h4>\n"
                + "<pre class=\"methodSignature\">public final&nbsp;double&nbsp;isPaused()</pre>\n"
                + "<div class=\"block\">Gets the value of the property paused.</div>\n"
                + "<dl>\n"
                + "<dt><span class=\"simpleTagLabel\">Property description:</span></dt>\n"
                + "<dd>Defines if paused. The second line.</dd>\n"
                + "<dt><span class=\"simpleTagLabel\">Default value:</span></dt>\n"
                + "<dd>false</dd>",
                "<h4>rate</h4>\n"
                + "<pre>public final&nbsp;<a href=\"C.DoubleProperty.html\" "
                + "title=\"class in pkg1\">C.DoubleProperty</a> rateProperty</pre>\n"
                + "<div class=\"block\">Defines the direction/speed at which the "
                + "<code>Timeline</code> is expected to\n"
                + " be played. This is the second line.</div>",
                "<h4>setRate</h4>\n"
                + "<pre class=\"methodSignature\">public final&nbsp;void&nbsp;setRate&#8203;(double&nbsp;value)</pre>\n"
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
                + "<pre class=\"methodSignature\">public final&nbsp;double&nbsp;getRate()</pre>\n"
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
                + "<div class=\"memberSummary\">\n<table>\n"
                + "<caption><span>Properties</span><span class=\"tabEnd\">&nbsp;</span></caption>",
                "<tr class=\"altColor\">\n"
                + "<td class=\"colFirst\"><code><a href=\"C.BooleanProperty.html\" title=\"class in pkg1\">C.BooleanProperty</a></code></td>\n",
                "<tr class=\"rowColor\">\n"
                + "<td class=\"colFirst\"><code><a href=\"C.DoubleProperty.html\" title=\"class in pkg1\">C.DoubleProperty</a></code></td>\n");

        checkOutput("pkg1/C.html", false,
                "A()",
                "<h3>Property Summary</h3>\n"
                + "<div class=\"memberSummary\">\n"
                + "<div role=\"tablist\" aria-orientation=\"horizontal\"><button role=\"tab\""
                + " aria-selected=\"true\" aria-controls=\"memberSummary_tabpanel\" tabindex=\"0\""
                + " onkeydown=\"switchTab(event)\" id=\"t0\" class=\"activeTableTab\">All Methods"
                + "</button><button role=\"tab\" aria-selected=\"false\""
                + " aria-controls=\"memberSummary_tabpanel\" tabindex=\"-1\" onkeydown=\"switchTab(event)\""
                + " id=\"t2\" class=\"tableTab\" onclick=\"show(2);\">Instance Methods</button>"
                + "<button role=\"tab\" aria-selected=\"false\" aria-controls=\"memberSummary_tabpanel\""
                + " tabindex=\"-1\" onkeydown=\"switchTab(event)\" id=\"t4\" class=\"tableTab\""
                + " onclick=\"show(8);\">Concrete Methods</button></div>",
                "<tr id=\"i0\" class=\"altColor\">\n"
                + "<td class=\"colFirst\"><code><a href=\"C.BooleanProperty.html\" title=\"class in pkg1\">C.BooleanProperty</a></code></td>\n",
                "<tr id=\"i1\" class=\"rowColor\">\n"
                + "<td class=\"colFirst\"><code><a href=\"C.DoubleProperty.html\" title=\"class in pkg1\">C.DoubleProperty</a></code></td>\n");

        checkOutput("index-all.html", true,
                "<div class=\"block\">Gets the value of the property paused.</div>",
                "<div class=\"block\">Defines if paused.</div>");

        checkOutput("pkg1/D.html", true,
                "<h3>Properties inherited from class&nbsp;pkg1."
                    + "<a href=\"C.html\" title=\"class in pkg1\">C</a></h3>\n"
                    + "<code><a href=\"C.html#pausedProperty\">"
                    + "paused</a>, <a href=\"C.html#rateProperty\">rate</a></code></li>");

        checkOutput("pkg1/D.html", false, "shouldNotAppear");
    }

    @Test
    public void test1_html4() {
        javadoc("-d", "out1-html4",
                "-html4",
                "-sourcepath", testSrc,
                "-javafx",
                "--disable-javafx-strict-checks",
                "-package",
                "pkg1");
        checkExit(Exit.OK);

        checkOutput("pkg1/C.html", true,
                "<dt><span class=\"seeLabel\">See Also:</span></dt>\n"
                + "<dd><a href=\"#getRate--\"><code>getRate()</code></a>, \n"
                + "<a href=\"#setRate-double-\">"
                + "<code>setRate(double)</code></a></dd>",
                "<th class=\"colSecond\" scope=\"row\"><code><span class=\"memberNameLink\">"
                + "<a href=\"#setTestMethodProperty--\">"
                + "setTestMethodProperty</a></span>()</code></th>",
                "<h3>Property Summary</h3>\n"
                + "<div class=\"memberSummary\">\n<table summary=\"Property Summary table, listing properties, and an explanation\">\n"
                + "<caption><span>Properties</span><span class=\"tabEnd\">&nbsp;</span></caption>");
    }

    /*
     * Test with -javafx option enabled, to ensure property getters and setters
     * are treated correctly.
     */
    @Test
    public void test2() {
        javadoc("-d", "out2a",
                "-sourcepath", testSrc,
                "-javafx",
                "--disable-javafx-strict-checks",
                "-package",
                "pkg2");
        checkExit(Exit.OK);
        checkOutput("pkg2/Test.html", true,
                "<h3>Property Detail</h3>\n"
                + "<a id=\"betaProperty\">\n"
                + "<!--   -->\n"
                + "</a>\n"
                + "<ul class=\"blockList\">\n"
                + "<li class=\"blockList\">\n"
                + "<h4>beta</h4>\n"
                + "<pre>public&nbsp;java.lang.Object betaProperty</pre>\n"
                + "</li>\n"
                + "</ul>\n"
                + "<a id=\"gammaProperty\">\n"
                + "<!--   -->\n"
                + "</a>\n"
                + "<ul class=\"blockList\">\n"
                + "<li class=\"blockList\">\n"
                + "<h4>gamma</h4>\n"
                + "<pre>public final&nbsp;java.util.List&lt;java.lang.String&gt; gammaProperty</pre>\n"
                + "</li>\n"
                + "</ul>\n"
                + "<a id=\"deltaProperty\">\n"
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
                + "<div class=\"memberSummary\">\n<table>\n"
                + "<caption><span>Properties</span><span class=\"tabEnd\">&nbsp;</span></caption>");

        checkOutput("pkg2/Test.html", false,
                "<h3>Property Summary</h3>\n"
                + "<div class=\"memberSummary\">\n"
                + "<div role=\"tablist\" aria-orientation=\"horizontal\"><button role=\"tab\""
                + " aria-selected=\"true\" aria-controls=\"memberSummary_tabpanel\" tabindex=\"0\""
                + " onkeydown=\"switchTab(event)\" id=\"t0\" class=\"activeTableTab\">All Methods"
                + "</button><button role=\"tab\" aria-selected=\"false\""
                + " aria-controls=\"memberSummary_tabpanel\" tabindex=\"-1\" onkeydown=\"switchTab(event)\""
                + " id=\"t2\" class=\"tableTab\" onclick=\"show(2);\">Instance Methods</button>"
                + "<button role=\"tab\" aria-selected=\"false\" aria-controls=\"memberSummary_tabpanel\""
                + " tabindex=\"-1\" onkeydown=\"switchTab(event)\" id=\"t4\" class=\"tableTab\""
                + " onclick=\"show(8);\">Concrete Methods</button></div>");
    }

    @Test
    public void test2_html4() {
        javadoc("-d", "out2a-html4",
                "-html4",
                "-sourcepath", testSrc,
                "-javafx",
                "--disable-javafx-strict-checks",
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
                + "<div class=\"memberSummary\">\n<table summary=\"Property Summary table, listing properties, and an explanation\">\n"
                + "<caption><span>Properties</span><span class=\"tabEnd\">&nbsp;</span></caption>");
    }

    /*
     * Test without -javafx option, to ensure property getters and setters
     * are treated just like any other java method.
     */
    @Test
    public void test3() {
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
                + "<tbody>\n"
                + "<tr class=\"altColor\" id=\"i0\">\n"
                + "<td class=\"colFirst\"><code>&lt;T&gt;&nbsp;java.lang.Object</code></td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><code><span class=\"memberNameLink\">"
                + "<a href=\"#alphaProperty(java.util.List)\">alphaProperty</a>"
                + "</span>&#8203;(java.util.List&lt;T&gt;&nbsp;foo)</code></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>\n"
                + "</tr>\n"
                + "<tr class=\"rowColor\" id=\"i1\">\n"
                + "<td class=\"colFirst\"><code>java.lang.Object</code></td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><code><span class=\"memberNameLink\">"
                + "<a href=\"#betaProperty()\">betaProperty</a></span>()</code></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>\n"
                + "</tr>\n"
                + "<tr class=\"altColor\" id=\"i2\">\n"
                + "<td class=\"colFirst\"><code>java.util.List&lt;java.util.Set&lt;? super java.lang.Object&gt;&gt;"
                + "</code></td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><code><span class=\"memberNameLink\">"
                + "<a href=\"#deltaProperty()\">deltaProperty</a></span>()</code></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>\n"
                + "</tr>\n"
                + "<tr class=\"rowColor\" id=\"i3\">\n"
                + "<td class=\"colFirst\"><code>java.util.List&lt;java.lang.String&gt;</code></td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><code><span class=\"memberNameLink\">"
                + "<a href=\"#gammaProperty()\">gammaProperty</a></span>()</code></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>"
        );
    }

    @Test
    public void test3_html4() {
        javadoc("-d", "out2b-html4",
                "-html4",
                "-sourcepath", testSrc,
                "-package",
                "pkg2");
        checkExit(Exit.OK);
        checkOutput("pkg2/Test.html", true,
                "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>\n"
                + "<th class=\"colSecond\" scope=\"col\">Method</th>\n"
                + "<th class=\"colLast\" scope=\"col\">Description</th>\n"
                + "</tr>\n"
                + "<tbody>\n"
                + "<tr class=\"altColor\" id=\"i0\">\n"
                + "<td class=\"colFirst\"><code>&lt;T&gt;&nbsp;java.lang.Object</code></td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><code><span class=\"memberNameLink\">"
                + "<a href=\"#alphaProperty-java.util.List-\">alphaProperty</a>"
                + "</span>&#8203;(java.util.List&lt;T&gt;&nbsp;foo)</code></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>\n"
                + "</tr>\n"
                + "<tr class=\"rowColor\" id=\"i1\">\n"
                + "<td class=\"colFirst\"><code>java.lang.Object</code></td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><code><span class=\"memberNameLink\">"
                + "<a href=\"#betaProperty--\">betaProperty</a></span>()</code></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>\n"
                + "</tr>\n"
                + "<tr class=\"altColor\" id=\"i2\">\n"
                + "<td class=\"colFirst\"><code>java.util.List&lt;java.util.Set&lt;? super java.lang.Object&gt;&gt;"
                + "</code></td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><code><span class=\"memberNameLink\">"
                + "<a href=\"#deltaProperty--\">deltaProperty</a></span>()</code></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>\n"
                + "</tr>\n"
                + "<tr class=\"rowColor\" id=\"i3\">\n"
                + "<td class=\"colFirst\"><code>java.util.List&lt;java.lang.String&gt;</code></td>\n"
                + "<th class=\"colSecond\" scope=\"row\"><code><span class=\"memberNameLink\">"
                + "<a href=\"#gammaProperty--\">gammaProperty</a></span>()</code></th>\n"
                + "<td class=\"colLast\">&nbsp;</td>"
        );
    }

    /*
     * Force the doclet to emit a warning when processing a synthesized,
     * DocComment, and ensure that the run succeeds, using the newer
     * --javafx flag.
     */
    @Test
    public void test4() {
        javadoc("-d", "out4",
                "--javafx",
                "--disable-javafx-strict-checks",
                "-Xdoclint:none",
                "-sourcepath", testSrc,
                "-package",
                "pkg4");
        checkExit(Exit.OK);

        // make sure the doclet indeed emits the warning
        checkOutput(Output.OUT, true, "C.java:0: warning - invalid usage of tag >");
    }
}
