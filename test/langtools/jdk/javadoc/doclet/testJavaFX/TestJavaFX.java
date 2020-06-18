/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @library ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.*
 * @run main TestJavaFX
 */

import javadoc.tester.JavadocTester;

public class TestJavaFX extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestJavaFX tester = new TestJavaFX();
        tester.setAutomaticCheckAccessibility(false);
        tester.setAutomaticCheckLinks(false);
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
                """
                    <dt>See Also:</dt>
                    <dd><a href="#getRate()"><code>getRate()</code></a>,\s
                    <a href="#setRate(double)"><code>setRate(double)</code></a></dd>""",
                """
                    <div class="member-signature"><span class="modifiers">public final</span>&nbsp;<\
                    span class="return-type">void</span>&nbsp;<span class="member-name">setRate</spa\
                    n>&#8203;(<span class="parameters">double&nbsp;value)</span></div>
                    <div class="block">Sets the value of the property rate.</div>
                    <dl class="notes">
                    <dt>Property description:</dt>""",
                """
                    <div class="member-signature"><span class="modifiers">public final</span>&nbsp;<\
                    span class="return-type">double</span>&nbsp;<span class="member-name">getRate</s\
                    pan>()</div>
                    <div class="block">Gets the value of the property rate.</div>
                    <dl class="notes">
                    <dt>Property description:</dt>""",
                """
                    <td class="col-first"><code><a href="C.DoubleProperty.html" title="class in pkg1">C.DoubleProperty</a></code></td>
                    <th class="col-second" scope="row"><code><span class="member-name-link"><a href=\
                    "#rateProperty">rate</a></span></code></th>
                    <td class="col-last">
                    <div class="block">Defines the direction/speed at which the <code>Timeline</code> is expected to
                     be played.</div>
                    </td>""",
                "<dt>Default value:</dt>",
                """
                    <dt>Since:</dt>
                    <dd>JavaFX 8.0</dd>""",
                "<dt>Property description:</dt>",
                """
                    <th class="col-second" scope="row"><code><span class="member-name-link"><a href=\
                    "#setTestMethodProperty()">setTestMethodProperty</a></span>()</code></th>""",
                """
                    <th class="col-second" scope="row"><code><span class="member-name-link"><a href=\
                    "#pausedProperty">paused</a></span></code></th>
                    <td class="col-last">
                    <div class="block">Defines if paused.</div>""",
                """
                    <section class="detail" id="pausedProperty">
                    <h3>paused</h3>
                    <div class="member-signature"><span class="modifiers">public final</span>&nbsp;<\
                    span class="return-type"><a href="C.BooleanProperty.html" title="class in pkg1">\
                    C.BooleanProperty</a></span>&nbsp;<span class="member-name">pausedProperty</span\
                    ></div>
                    <div class="block">Defines if paused. The second line.</div>""",
                """
                    <section class="detail" id="isPaused()">
                    <h3>isPaused</h3>
                    <div class="member-signature"><span class="modifiers">public final</span>&nbsp;<\
                    span class="return-type">double</span>&nbsp;<span class="member-name">isPaused</\
                    span>()</div>
                    <div class="block">Gets the value of the property paused.</div>""",
                """
                    <section class="detail" id="setPaused(boolean)">
                    <h3>setPaused</h3>
                    <div class="member-signature"><span class="modifiers">public final</span>&nbsp;<\
                    span class="return-type">void</span>&nbsp;<span class="member-name">setPaused</s\
                    pan>&#8203;(<span class="parameters">boolean&nbsp;value)</span></div>
                    <div class="block">Sets the value of the property paused.</div>
                    <dl class="notes">
                    <dt>Property description:</dt>
                    <dd>Defines if paused. The second line.</dd>
                    <dt>Default value:</dt>
                    <dd>false</dd>""",
                """
                    <section class="detail" id="isPaused()">
                    <h3>isPaused</h3>
                    <div class="member-signature"><span class="modifiers">public final</span>&nbsp;<\
                    span class="return-type">double</span>&nbsp;<span class="member-name">isPaused</\
                    span>()</div>
                    <div class="block">Gets the value of the property paused.</div>
                    <dl class="notes">
                    <dt>Property description:</dt>
                    <dd>Defines if paused. The second line.</dd>
                    <dt>Default value:</dt>
                    <dd>false</dd>""",
                """
                    <section class="detail" id="rateProperty">
                    <h3>rate</h3>
                    <div class="member-signature"><span class="modifiers">public final</span>&nbsp;<\
                    span class="return-type"><a href="C.DoubleProperty.html" title="class in pkg1">C\
                    .DoubleProperty</a></span>&nbsp;<span class="member-name">rateProperty</span></d\
                    iv>
                    <div class="block">Defines the direction/speed at which the <code>Timeline</code> is expected to
                     be played. This is the second line.</div>""",
                """
                    <section class="detail" id="setRate(double)">
                    <h3>setRate</h3>
                    <div class="member-signature"><span class="modifiers">public final</span>&nbsp;<\
                    span class="return-type">void</span>&nbsp;<span class="member-name">setRate</spa\
                    n>&#8203;(<span class="parameters">double&nbsp;value)</span></div>
                    <div class="block">Sets the value of the property rate.</div>
                    <dl class="notes">
                    <dt>Property description:</dt>
                    <dd>Defines the direction/speed at which the <code>Timeline</code> is expected to
                     be played. This is the second line.</dd>
                    <dt>Default value:</dt>
                    <dd>11</dd>
                    <dt>Since:</dt>
                    <dd>JavaFX 8.0</dd>""",
                """
                    <section class="detail" id="getRate()">
                    <h3>getRate</h3>
                    <div class="member-signature"><span class="modifiers">public final</span>&nbsp;<\
                    span class="return-type">double</span>&nbsp;<span class="member-name">getRate</s\
                    pan>()</div>
                    <div class="block">Gets the value of the property rate.</div>
                    <dl class="notes">
                    <dt>Property description:</dt>
                    <dd>Defines the direction/speed at which the <code>Timeline</code> is expected to
                     be played. This is the second line.</dd>
                    <dt>Default value:</dt>
                    <dd>11</dd>
                    <dt>Since:</dt>
                    <dd>JavaFX 8.0</dd>""",
                """
                    <section class="property-summary" id="property.summary">
                    <h2>Property Summary</h2>
                    <div class="member-summary">
                    <table class="summary-table">
                    <caption><span>Properties</span></caption>""",
                """
                    <tr class="alt-color">
                    <td class="col-first"><code><a href="C.BooleanProperty.html" title="class in pkg1">C.BooleanProperty</a></code></td>
                    """,
                """
                    <tr class="row-color">
                    <td class="col-first"><code><a href="C.DoubleProperty.html" title="class in pkg1">C.DoubleProperty</a></code></td>
                    """);

        checkOutput("pkg1/C.html", false,
                "A()",
                """
                    <h2 id="property.summary">Property Summary</h2>
                    <div class="member-summary">
                    <div role="tablist" aria-orientation="horizontal"><button role="tab" aria-select\
                    ed="true" aria-controls="member-summary_tabpanel" tabindex="0" onkeydown="switch\
                    Tab(event)" id="t0" class="active-table-tab">All Methods</button><button role="t\
                    ab" aria-selected="false" aria-controls="member-summary_tabpanel" tabindex="-1" \
                    onkeydown="switchTab(event)" id="t2" class="table-tab" onclick="show(2);">Instan\
                    ce Methods</button><button role="tab" aria-selected="false" aria-controls="membe\
                    r-summary_tabpanel" tabindex="-1" onkeydown="switchTab(event)" id="t4" class="ta\
                    ble-tab" onclick="show(8);">Concrete Methods</button></div>""",
                """
                    <tr id="i0" class="alt-color">
                    <td class="col-first"><code><a href="C.BooleanProperty.html" title="class in pkg1">C.BooleanProperty</a></code></td>
                    """,
                """
                    <tr id="i1" class="row-color">
                    <td class="col-first"><code><a href="C.DoubleProperty.html" title="class in pkg1">C.DoubleProperty</a></code></td>
                    """);

        checkOutput("index-all.html", true,
                """
                    <div class="block">Gets the value of the property paused.</div>""",
                """
                    <div class="block">Defines if paused.</div>""");

        checkOutput("pkg1/D.html", true,
                """
                    <h3 id="properties.inherited.from.class.pkg1.C">Properties inherited from class&\
                    nbsp;pkg1.<a href="C.html" title="class in pkg1">C</a></h3>
                    <code><a href="C.html#pausedProperty">paused</a>, <a href="C.html#rateProperty">rate</a></code></div>""");

        checkOutput("pkg1/D.html", false, "shouldNotAppear");
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
                """
                    <section class="property-details" id="property.detail">
                    <h2>Property Details</h2>
                    <ul class="member-list">
                    <li>
                    <section class="detail" id="betaProperty">
                    <h3>beta</h3>
                    <div class="member-signature"><span class="modifiers">public</span>&nbsp;<span c\
                    lass="return-type">java.lang.Object</span>&nbsp;<span class="member-name">betaPr\
                    operty</span></div>
                    </section>
                    </li>
                    <li>
                    <section class="detail" id="gammaProperty">
                    <h3>gamma</h3>
                    <div class="member-signature"><span class="modifiers">public final</span>&nbsp;<\
                    span class="return-type">java.util.List&lt;java.lang.String&gt;</span>&nbsp;<spa\
                    n class="member-name">gammaProperty</span></div>
                    </section>
                    </li>
                    <li>
                    <section class="detail" id="deltaProperty">
                    <h3>delta</h3>
                    <div class="member-signature"><span class="modifiers">public final</span>&nbsp;<\
                    span class="return-type">java.util.List&lt;java.util.Set&lt;? super java.lang.Ob\
                    ject&gt;&gt;</span>&nbsp;<span class="member-name">deltaProperty</span></div>
                    </section>
                    </li>
                    </ul>
                    </section>""",
                """
                    <section class="property-summary" id="property.summary">
                    <h2>Property Summary</h2>
                    <div class="member-summary">
                    <table class="summary-table">
                    <caption><span>Properties</span></caption>""");

        checkOutput("pkg2/Test.html", false,
                """
                    <h2>Property Summary</h2>
                    <div class="member-summary">
                    <div role="tablist" aria-orientation="horizontal"><button role="tab" aria-select\
                    ed="true" aria-controls="member-summary_tabpanel" tabindex="0" onkeydown="switch\
                    Tab(event)" id="t0" class="active-table-tab">All Methods</button><button role="t\
                    ab" aria-selected="false" aria-controls="member-summary_tabpanel" tabindex="-1" \
                    onkeydown="switchTab(event)" id="t2" class="table-tab" onclick="show(2);">Instan\
                    ce Methods</button><button role="tab" aria-selected="false" aria-controls="membe\
                    r-summary_tabpanel" tabindex="-1" onkeydown="switchTab(event)" id="t4" class="ta\
                    ble-tab" onclick="show(8);">Concrete Methods</button></div>""");
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
        checkOutput("pkg2/Test.html", false, "<h2>Property Summary</h2>");
        checkOutput("pkg2/Test.html", true,
                """
                    <thead>
                    <tr>
                    <th class="col-first" scope="col">Modifier and Type</th>
                    <th class="col-second" scope="col">Method</th>
                    <th class="col-last" scope="col">Description</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr class="alt-color" id="i0">
                    <td class="col-first"><code>&lt;T&gt;&nbsp;java.lang.Object</code></td>
                    <th class="col-second" scope="row"><code><span class="member-name-link"><a href=\
                    "#alphaProperty(java.util.List)">alphaProperty</a></span>&#8203;(java.util.List&\
                    lt;T&gt;&nbsp;foo)</code></th>
                    <td class="col-last">&nbsp;</td>
                    </tr>
                    <tr class="row-color" id="i1">
                    <td class="col-first"><code>java.lang.Object</code></td>
                    <th class="col-second" scope="row"><code><span class="member-name-link"><a href=\
                    "#betaProperty()">betaProperty</a></span>()</code></th>
                    <td class="col-last">&nbsp;</td>
                    </tr>
                    <tr class="alt-color" id="i2">
                    <td class="col-first"><code>java.util.List&lt;java.util.Set&lt;? super java.lang.Object&gt;&gt;</code></td>
                    <th class="col-second" scope="row"><code><span class="member-name-link"><a href=\
                    "#deltaProperty()">deltaProperty</a></span>()</code></th>
                    <td class="col-last">&nbsp;</td>
                    </tr>
                    <tr class="row-color" id="i3">
                    <td class="col-first"><code>java.util.List&lt;java.lang.String&gt;</code></td>
                    <th class="col-second" scope="row"><code><span class="member-name-link"><a href=\
                    "#gammaProperty()">gammaProperty</a></span>()</code></th>
                    <td class="col-last">&nbsp;</td>"""
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
        checkOutput(Output.OUT, true, "C.java:0: warning - invalid usage of tag <");
    }
}
