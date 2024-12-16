/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4682448 4947464 5029946 8025633 8026567 8035473 8139101 8175200
             8186332 8186703 8182765 8187288 8261976 8303349 8319988
 * @summary  Verify that the public modifier does not show up in the
 *           documentation for public methods, as recommended by the JLS.
 *           If A implements I and B extends A, B should be in the list of
 *           implementing classes in the documentation for I.
 * @library  ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    javadoc.tester.*
 * @run main TestInterface
 */

/*
 * TODO: make it Interface<PE> ie. fix all ParameterTypes, likely should get
 * fixed when Doc is replace by j.l.m, but meanwhile this test has been adjusted
 * take the current format this is better than @ignore because we can follow the
 * differences as the work progress.
 *
 * The consensus is that we should have something as follows:
 * In Child.html
 *  Specified by:  method in interface<IE>
 *  Overrides:     method in class Parent<PE>
 * In other words the TypeParameter in scope should be used ex: Interface<IE>, Parent<PE>
 * and Child<CE>
 */

import javadoc.tester.JavadocTester;

public class TestInterface extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestInterface();
        tester.runTests();
    }

    @Test
    public void test() {
        javadoc("-d", "out",
                "--no-platform-links",
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkOutput("pkg/Interface.html", true,
                """
                    <div class="member-signature"><span class="return-type">int</span>&nbsp;<span class="element-name">method</span>()</div>""",
                """
                    <div class="member-signature"><span class="modifiers">static final</span>&nbsp;<\
                    span class="return-type">int</span>&nbsp;<span class="element-name">field</span></div>""",
                // Make sure known implementing class list is correct and omits type parameters.
                """
                    <dl class="notes">
                    <dt>All Known Implementing Classes:</dt>
                    <dd><code><a href="Child.html" title="class in pkg">Child</a></code>, <code><a h\
                    ref="Parent.html" title="class in pkg">Parent</a></code></dd>
                    </dl>""");

        checkOutput("pkg/Child.html", true,
                // Make sure "All Implemented Interfaces": has substituted type parameters
                """
                    <dl class="notes">
                    <dt>All Implemented Interfaces:</dt>
                    <dd><code><a href="Interface.html" title="interface in pkg">Interface</a>&lt;CE&gt;</code></dd>
                    </dl>""",
                //Make sure Class Tree has substituted type parameters.
                """
                    <div class="inheritance" title="Inheritance Tree">java.lang.Object
                    <div class="inheritance"><a href="Parent.html" title="class in pkg">pkg.Parent</a>&lt;CE&gt;
                    <div class="inheritance">pkg.Child&lt;CE&gt;</div>
                    </div>
                    </div>""",
                //Make sure "Specified By" has substituted type parameters.
                """
                    <dt>Specified by:</dt>
                    <dd><code><a href="Interface.html#method()">method</a></code>&nbsp;in interface&\
                    nbsp;<code><a href="Interface.html" title="interface in pkg">Interface</a>&lt;<a\
                     href="#type-param-CE" title="type parameter in Child">CE</a>&gt;</code></dd>""",
                //Make sure "Overrides" has substituted type parameters.
                """
                    <dt>Overrides:</dt>
                    <dd><code><a href="Parent.html#method()">method</a></code>&nbsp;in class&nbsp;<c\
                    ode><a href="Parent.html" title="class in pkg">Parent</a>&lt;<a href="#type-param-CE\
                    " title="type parameter in Child">CE</a>&gt;</code></dd>""");

        checkOutput("pkg/Parent.html", true,
                //Make sure "Direct Known Subclasses" omits type parameters
                """
                    <dl class="notes">
                    <dt>Direct Known Subclasses:</dt>
                    <dd><code><a href="Child.html" title="class in pkg">Child</a></code></dd>
                    </dl>""");

        checkOutput("pkg/Interface.html", false,
                "public int&nbsp;method--",
                "public static final&nbsp;int field");

        checkOutput("pkg/ClassWithStaticMembers.html", false,
                //Make sure "Specified By" does not appear on class documentation when
                //the method is a static method in the interface.
                "<dt>Specified by:</dt>\n");

        checkOutput("pkg/ClassWithStaticMembers.html", true,
                """
                    <section class="detail" id="f">
                    <h3>f</h3>
                    <div class="horizontal-scroll">
                    <div class="member-signature"><span class="modifiers">public static</span>&nbsp;\
                    <span class="return-type">int</span>&nbsp;<span class="element-name">f</span></div>
                    <div class="block">A hider field</div>""",

                """
                    <div class="col-first even-row-color method-summary-table method-summary-table-t\
                    ab1 method-summary-table-tab4"><code>static void</code></div>
                    <div class="col-second even-row-color method-summary-table method-summary-table-\
                    tab1 method-summary-table-tab4"><code><a href="#m()" class="member-name-link">m<\
                    /a>()</code></div>
                    <div class="col-last even-row-color method-summary-table method-summary-table-ta\
                    b1 method-summary-table-tab4">
                    <div class="block">A hider method</div>
                    </div>
                    """,

                """
                    <section class="detail" id="staticMethod()">
                    <h3>staticMethod</h3>
                    <div class="horizontal-scroll">
                    <div class="member-signature"><span class="modifiers">public static</span>&nbsp;\
                    <span class="return-type">void</span>&nbsp;<span class="element-name">staticMethod</span\
                    >()</div>
                    """
        );

        checkOutput("pkg/ClassWithStaticMembers.html", false,
                """
                    <section class="detail" id="staticMethod()">
                    <h3>staticMethod</h3>
                    <div class="horizontal-scroll">
                    <div class="member-signature"><span class="modifiers">public static</span>&nbsp;\
                    <span class="return-type">void</span>&nbsp;<span class="element-name">staticMethod</span\
                    >()</div>
                    <div class="block"><span class="description-from-type-label">Description copied from inte\
                    rface:&nbsp;<code><a href="InterfaceWithStaticMembers.html#staticMethod()">Inter\
                    faceWithStaticMembers</a></code></span></div>
                    <div class="block">A static method</div>
                    """);

        checkOutput("pkg/ClassWithStaticMembers.InnerClass.html", true,
                """
                    <div class="type-signature"><span class="modifiers">public static class </span><\
                    span class="element-name type-name-label">ClassWithStaticMembers.InnerClass</span>
                    <span class="extends-implements">extends java.lang.Object</span></div>
                    <div class="block">A hider inner class</div>""");
    }

    @Test
    public void test1() {
        javadoc("-d", "out-1",
                "-sourcepath", testSrc,
                "pkg1");
        checkExit(Exit.OK);

        checkOutput("pkg1/Child.html", true,
            // Ensure the correct Overrides in the inheritance hierarchy is reported
            """
                <dt>Overrides:</dt>
                <dd><code><a href="GrandParent.html#method1()">method1</a></code>&nbsp;in class&\
                nbsp;<code><a href="GrandParent.html" title="class in pkg1">GrandParent</a>&lt;<\
                a href="#type-param-CE" title="type parameter in Child">CE</a>&gt;</code>""");
    }

    @Test
    public void test2() {
        javadoc("-d", "out-2",
                "-sourcepath", testSrc,
                "pkg2");

        checkExit(Exit.OK);

        checkOutput("pkg2/Spliterator.OfDouble.html", true,
            // Ensure the correct type parameters are displayed correctly
            """
                <h3 id="nested-classes-inherited-from-class-pkg2.Spliterator">Nested classes/int\
                erfaces inherited from interface&nbsp;pkg2.<a href="Spliterator.html" title="int\
                erface in pkg2">Spliterator</a></h3>
                <code><a href="Spliterator.OfDouble.html" title="interface in pkg2">Spliterator.\
                OfDouble</a>, <a href="Spliterator.OfInt.html" title="interface in pkg2">Spliter\
                ator.OfInt</a>&lt;<a href="Spliterator.OfInt.html#type-param-Integer" title="type parameter in Spli\
                terator.OfInt">Integer</a>&gt;, <a href="Spliterator.OfPrimitive.html" title="in\
                terface in pkg2">Spliterator.OfPrimitive</a>&lt;<a href="Spliterator.OfPrimitive\
                .html#type-param-T" title="type parameter in Spliterator.OfPrimitive">T</a>, <a href="Spl\
                iterator.OfPrimitive.html#type-param-T_CONS" title="type parameter in Spliterator.OfPrimitive">T_C\
                ONS</a>, <a href="Spliterator.OfPrimitive.html#type-param-T_SPLITR" title="type parameter in Spl\
                iterator.OfPrimitive">T_SPLITR</a> extends <a href="Spliterator.OfPrimitive.html\
                " title="interface in pkg2">Spliterator.OfPrimitive</a>&lt;<a href="Spliterator.\
                OfPrimitive.html#type-param-T" title="type parameter in Spliterator.OfPrimitive">T</a>,<wbr><\
                a href="Spliterator.OfPrimitive.html#type-param-T_CONS" title="type parameter in Spliterator.OfPri\
                mitive">T_CONS</a>,<wbr><a href="Spliterator.OfPrimitive.html#type-param-T_SPLITR" title="type param\
                eter in Spliterator.OfPrimitive">T_SPLITR</a>&gt;&gt;</code>""");
        checkOutput("pkg2/Spliterator.html", true,
            """
                <div class="caption"><span>Nested Classes</span></div>
                <div class="summary-table three-column-summary">
                <div class="table-header col-first">Modifier and Type</div>
                <div class="table-header col-second">Interface</div>
                <div class="table-header col-last">Description</div>
                <div class="col-first even-row-color"><code>static interface&nbsp;</code></div>
                <div class="col-second even-row-color"><code><a href="Spliterator.OfDouble.html"\
                 class="type-name-link" title="interface in pkg2">Spliterator.OfDouble</a></code\
                ></div>
                <div class="col-last even-row-color">&nbsp;</div>
                <div class="col-first odd-row-color"><code>static interface&nbsp;</code></div>
                <div class="col-second odd-row-color"><code><a href="Spliterator.OfInt.html" cla\
                ss="type-name-link" title="interface in pkg2">Spliterator.OfInt</a>&lt;<a href="\
                Spliterator.OfInt.html#type-param-Integer" title="type parameter in Spliterator.OfInt">Integer</a>&\
                gt;</code></div>
                <div class="col-last odd-row-color">&nbsp;</div>
                <div class="col-first even-row-color"><code>static interface&nbsp;</code></div>
                <div class="col-second even-row-color"><code><a href="Spliterator.OfPrimitive.ht\
                ml" class="type-name-link" title="interface in pkg2">Spliterator.OfPrimitive</a>\
                &lt;<a href="Spliterator.OfPrimitive.html#type-param-T" title="type parameter in Spliterator.\
                OfPrimitive">T</a>, <a href="Spliterator.OfPrimitive.html#type-param-T_CONS" title="type param\
                eter in Spliterator.OfPrimitive">T_CONS</a>, <a href="Spliterator.OfPrimitiv\
                e.html#type-param-T_SPLITR" title="type parameter in Spliterator.OfPrimitive">T_SPLITR</a> extends <\
                a href="Spliterator.OfPrimitive.html" title="interface in pkg2">Spliterator.OfPr\
                imitive</a>&lt;<a href="Spliterator.OfPrimitive.html#type-param-T" title="type parameter in S\
                pliterator.OfPrimitive">T</a>,<wbr><a href="Spliterator.OfPrimitive.html#type-param-T_CONS" title=\
                "type parameter in Spliterator.OfPrimitive">T_CONS</a>,<wbr><a href="Spliterator\
                .OfPrimitive.html#type-param-T_SPLITR" title="type parameter in Spliterator.OfPrimitive">T_SPLITR</a\
                >&gt;&gt;</code></div>
                <div class="col-last even-row-color">&nbsp;</div>
                </div>""");
        checkOutput("allclasses-index.html", true,
                """
                <div class="col-first even-row-color all-classes-table all-classes-table-tab2"><\
                a href="pkg2/Abstract.html" title="class in pkg2">Abstract</a></div>
                <div class="col-last even-row-color all-classes-table all-classes-table-tab2">&n\
                bsp;</div>
                <div class="col-first odd-row-color all-classes-table all-classes-table-tab1"><a\
                 href="pkg2/Spliterator.html" title="interface in pkg2">Spliterator&lt;T&gt;</a>\
                </div>
                <div class="col-last odd-row-color all-classes-table all-classes-table-tab1">&nb\
                sp;</div>
                <div class="col-first even-row-color all-classes-table all-classes-table-tab1"><\
                a href="pkg2/Spliterator.OfDouble.html" title="interface in pkg2">Spliterator.Of\
                Double</a></div>
                <div class="col-last even-row-color all-classes-table all-classes-table-tab1">&n\
                bsp;</div>
                <div class="col-first odd-row-color all-classes-table all-classes-table-tab1"><a\
                 href="pkg2/Spliterator.OfInt.html" title="interface in pkg2">Spliterator.OfInt&\
                lt;Integer&gt;</a></div>
                <div class="col-last odd-row-color all-classes-table all-classes-table-tab1">&nb\
                sp;</div>
                <div class="col-first even-row-color all-classes-table all-classes-table-tab1"><\
                a href="pkg2/Spliterator.OfPrimitive.html" title="interface in pkg2">Spliterator\
                .OfPrimitive&lt;T,<wbr>T_CONS,<wbr>T_SPLITR&gt;</a></div>
                <div class="col-last even-row-color all-classes-table all-classes-table-tab1">&n\
                bsp;</div>""");
        checkOutput("index-all.html", true,
                """
                <dt><a href="pkg2/Spliterator.html" class="type-name-link" title="interface in p\
                kg2">Spliterator&lt;T&gt;</a> - Interface in <a href="pkg2/package-summary.html"\
                >pkg2</a></dt>
                <dd>&nbsp;</dd>
                <dt><a href="pkg2/Spliterator.OfDouble.html" class="type-name-link" title="inter\
                face in pkg2">Spliterator.OfDouble</a> - Interface in <a href="pkg2/package-summ\
                ary.html">pkg2</a></dt>
                <dd>&nbsp;</dd>
                <dt><a href="pkg2/Spliterator.OfInt.html" class="type-name-link" title="interfac\
                e in pkg2">Spliterator.OfInt&lt;Integer&gt;</a> - Interface in <a href="pkg2/pac\
                kage-summary.html">pkg2</a></dt>
                <dd>&nbsp;</dd>
                <dt><a href="pkg2/Spliterator.OfPrimitive.html" class="type-name-link" title="in\
                terface in pkg2">Spliterator.OfPrimitive&lt;T,<wbr>T_CONS,<wbr>T_SPLITR&gt;</a> \
                - Interface in <a href="pkg2/package-summary.html">pkg2</a></dt>
                <dd>&nbsp;</dd>""");
    }

    @Test
    public void test3() {
        javadoc("-d", "out-3",
                "--no-platform-links", // disable links to simplify output matching
                "-sourcepath", testSrc,
                "pkg3");

        checkExit(Exit.OK);

        checkOutput("pkg3/I.html", true,
                """
                <li>
                <section class="detail" id="hashCode()">
                <h3>hashCode</h3>
                <div class="horizontal-scroll">
                <div class="member-signature"><span class="return-type">\
                int</span>&nbsp;<span class="element-name">hashCode</span>()</div>
                <dl class="notes">
                <dt>Overrides:</dt>
                <dd><code>hashCode</code>&nbsp;in class&nbsp;<code>java.lang.Object</code></dd>
                </dl>
                </div>
                </section>
                </li>
                <li>
                <section class="detail" id="equals(java.lang.Object)">
                <h3>equals</h3>
                <div class="horizontal-scroll">
                <div class="member-signature"><span class="return-type">\
                boolean</span>&nbsp;<span class="element-name">equals</span>\
                <wbr><span class="parameters">(java.lang.Object&nbsp;obj)</span></div>
                <dl class="notes">
                <dt>Overrides:</dt>
                <dd><code>equals</code>&nbsp;in class&nbsp;<code>java.lang.Object</code></dd>
                </dl>
                </div>
                </section>
                </li>
                <li>
                <section class="detail" id="toString()">
                <h3>toString</h3>
                <div class="horizontal-scroll">
                <div class="member-signature"><span class="return-type">\
                java.lang.String</span>&nbsp;<span class="element-name">toString</span>()</div>
                <dl class="notes">
                <dt>Overrides:</dt>
                <dd><code>toString</code>&nbsp;in class&nbsp;<code>java.lang.Object</code></dd>
                </dl>
                </div>
                </section>
                </li>
                <li>
                <section class="detail" id="clone()">
                <h3>clone</h3>
                <div class="horizontal-scroll">
                <div class="member-signature"><span class="return-type">\
                java.lang.Object</span>&nbsp;<span class="element-name">clone</span>()</div>
                </div>
                </section>
                </li>
                """);
    }
}
