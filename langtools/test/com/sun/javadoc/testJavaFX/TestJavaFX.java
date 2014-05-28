/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7112427 8012295 8025633 8026567
 * @summary Test of the JavaFX doclet features.
 * @author jvalenta
 * @library ../lib
 * @build JavadocTester
 * @run main TestJavaFX
 */

public class TestJavaFX extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestJavaFX tester = new TestJavaFX();
        tester.runTests();
    }

    @Test
    void test() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "-javafx",
                testSrc("C.java"), testSrc("D.java"));
        checkExit(Exit.FAILED); // should be EXIT_OK -- need to fix C.java

        checkOutput("C.html", true,
                "<dt><span class=\"seeLabel\">See Also:</span></dt>\n"
                + "<dd><a href=\"C.html#getRate--\"><code>getRate()</code></a>, \n"
                + "<a href=\"C.html#setRate-double-\"><code>setRate(double)</code></a></dd>",
                "<pre>public final&nbsp;void&nbsp;setRate(double&nbsp;value)</pre>\n"
                + "<div class=\"block\">Sets the value of the property rate.</div>\n"
                + "<dl>\n"
                + "<dt><span class=\"simpleTagLabel\">Property description:</span></dt>",
                "<pre>public final&nbsp;double&nbsp;getRate()</pre>\n"
                + "<div class=\"block\">Gets the value of the property rate.</div>\n"
                + "<dl>\n"
                + "<dt><span class=\"simpleTagLabel\">Property description:</span></dt>",
                "<td class=\"colLast\"><code><span class=\"memberNameLink\"><a href=\"C.html#rateProperty\">rate</a></span></code>\n"
                + "<div class=\"block\">Defines the direction/speed at which the <code>Timeline</code> is expected to",
                "<span class=\"simpleTagLabel\">Default value:</span>",
                "<span class=\"simpleTagLabel\">Since:</span></dt>\n"
                + "<dd>JavaFX 8.0</dd>",
                "<p>Sets the value of the property <code>Property</code>",
                "<p>Gets the value of the property <code>Property</code>",
                "<span class=\"simpleTagLabel\">Property description:</span>",
                "<td class=\"colLast\"><code><span class=\"memberNameLink\"><a href=\"C.html#setTestMethodProperty--\">setTestMethodProperty</a></span>()</code>&nbsp;</td>",
                "<h4>isPaused</h4>\n"
                + "<pre>public final&nbsp;double&nbsp;isPaused()</pre>\n"
                + "<div class=\"block\">Gets the value of the property paused.</div>");

        checkOutput("C.html", false,
                "A()");

        checkOutput("D.html", true,
                "<h3>Properties inherited from class&nbsp;<a href=\"C.html\" title=\"class in &lt;Unnamed&gt;\">C</a></h3>\n"
                + "<code><a href=\"C.html#pausedProperty\">paused</a>, <a href=\"C.html#rateProperty\">rate</a></code></li>");
    }

}
