/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8005091 8009686 8025633 8026567 6469562 8071982 8071984 8162363 8175200 8186332 8182765
 *           8187288 8241969 8259216
 * @summary  Make sure that type annotations are displayed correctly
 * @library  ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    javadoc.tester.*
 * @run main TestTypeAnnotations
 */

import javadoc.tester.JavadocTester;

public class TestTypeAnnotations extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestTypeAnnotations tester = new TestTypeAnnotations();
        tester.runTests();
    }

    @Test
    public void test() {
        javadoc("-d", "out",
                "--no-platform-links",
                "-sourcepath", testSrc,
                "-private",
                "typeannos");
        checkExit(Exit.OK);

        // Test for type annotations on Class Extends (ClassExtends.java).
        checkOutput("typeannos/MyClass.html", true,
                """
                    extends <a href="ClassExtA.html" title="annotation in typeannos">@ClassExtA</a> \
                    <a href="ParameterizedClass.html" title="class in typeannos">ParameterizedClass<\
                    /a>&lt;<a href="ClassExtB.html" title="annotation in typeannos">@ClassExtB</a> j\
                    ava.lang.String&gt;""",

                """
                    implements <a href="ClassExtB.html" title="annotation in typeannos">@ClassExtB</\
                    a> java.lang.CharSequence, <a href="ClassExtA.html" title="annotation in typeann\
                    os">@ClassExtA</a> <a href="ParameterizedInterface.html" title="interface in typ\
                    eannos">ParameterizedInterface</a>&lt;<a href="ClassExtB.html" title="annotation\
                     in typeannos">@ClassExtB</a> java.lang.String&gt;</span></div>""");

        checkOutput("typeannos/MyInterface.html", true,
                """
                    extends <a href="ClassExtA.html" title="annotation in typeannos">@ClassExtA</a> \
                    <a href="ParameterizedInterface.html" title="interface in typeannos">Parameteriz\
                    edInterface</a>&lt;<a href="ClassExtA.html" title="annotation in typeannos">@Cla\
                    ssExtA</a> java.lang.String&gt;, <a href="ClassExtB.html" title="annotation in t\
                    ypeannos">@ClassExtB</a> java.lang.CharSequence</span></div>""");

        // Test for type annotations on Class Parameters (ClassParameters.java).
        checkOutput("typeannos/ExtendsBound.html", true,
                """
                    class </span><span class="element-name type-name-label">ExtendsBound&lt;K extend\
                    s <a href="ClassParamA.html" title="annotation in typeannos">@ClassParamA</a> ja\
                    va.lang.String&gt;</span>""");

        checkOutput("typeannos/ExtendsGeneric.html", true,
                """
                    <div class="type-signature"><span class="modifiers">class </span><span class="el\
                    ement-name type-name-label">ExtendsGeneric&lt;K extends <a href="ClassParamA.htm\
                    l" title="annotation in typeannos">@ClassParamA</a> <a href="Unannotated.html" t\
                    itle="class in typeannos">Unannotated</a>&lt;<a href="ClassParamB.html" title="a\
                    nnotation in typeannos">@ClassParamB</a> java.lang.String&gt;&gt;</span>""");

        checkOutput("typeannos/TwoBounds.html", true,
                """
                    <div class="type-signature"><span class="modifiers">class </span><span class="el\
                    ement-name type-name-label">TwoBounds&lt;K extends <a href="ClassParamA.html" ti\
                    tle="annotation in typeannos">@ClassParamA</a> java.lang.String,&#8203;V extends\
                     <a href="ClassParamB.html" title="annotation in typeannos">@ClassParamB</a> jav\
                    a.lang.String&gt;</span>""");

        checkOutput("typeannos/Complex1.html", true,
                """
                    class </span><span class="element-name type-name-label">Complex1&lt;K extends <a\
                     href="ClassParamA.html" title="annotation in typeannos">@ClassParamA</a> java.l\
                    ang.String &amp; java.lang.Runnable&gt;</span>""");

        checkOutput("typeannos/Complex2.html", true,
                """
                    class </span><span class="element-name type-name-label">Complex2&lt;K extends ja\
                    va.lang.String &amp; <a href="ClassParamB.html" title="annotation in typeannos">\
                    @ClassParamB</a> java.lang.Runnable&gt;</span>""");

        checkOutput("typeannos/ComplexBoth.html", true,
                """
                    class </span><span class="element-name type-name-label">ComplexBoth&lt;K extends\
                     <a href="ClassParamA.html" title="annotation in typeannos">@ClassParamA</a> jav\
                    a.lang.String &amp; <a href="ClassParamA.html" title="annotation in typeannos">@\
                    ClassParamA</a> java.lang.Runnable&gt;</span>""");

        // Test for type annotations on fields (Fields.java).
        checkOutput("typeannos/DefaultScope.html", true,
                """
                    <div class="member-signature"><span class="return-type"><a href="Parameterized.h\
                    tml" title="class in typeannos">Parameterized</a>&lt;<a href="FldA.html" title="\
                    annotation in typeannos">@FldA</a> java.lang.String,&#8203;<a href="FldB.html" t\
                    itle="annotation in typeannos">@FldB</a> java.lang.String&gt;</span>&nbsp;<span \
                    class="element-name">bothTypeArgs</span></div>""",

                """
                    <div class="member-signature"><span class="return-type"><a href="FldA.html" titl\
                    e="annotation in typeannos">@FldA</a> java.lang.String <a href="FldB.html" title\
                    ="annotation in typeannos">@FldB</a> []</span>&nbsp;<span class="element-name">a\
                    rray1Deep</span></div>""",

                """
                    <div class="member-signature"><span class="return-type">java.lang.String <a href\
                    ="FldB.html" title="annotation in typeannos">@FldB</a> [][]</span>&nbsp;<span cl\
                    ass="element-name">array2SecondOld</span></div>""",

                // When JDK-8068737, we should change the order
                """
                    <div class="member-signature"><span class="return-type"><a href="FldD.html" titl\
                    e="annotation in typeannos">@FldD</a> java.lang.String <a href="FldC.html" title\
                    ="annotation in typeannos">@FldC</a> <a href="FldB.html" title="annotation in ty\
                    peannos">@FldB</a> [] <a href="FldC.html" title="annotation in typeannos">@FldC<\
                    /a> <a href="FldA.html" title="annotation in typeannos">@FldA</a> []</span>&nbsp\
                    ;<span class="element-name">array2Deep</span></div>""");

        checkOutput("typeannos/ModifiedScoped.html", true,
                """
                    <div class="member-signature"><span class="modifiers">public final</span>&nbsp;<\
                    span class="return-type"><a href="Parameterized.html" title="class in typeannos"\
                    >Parameterized</a>&lt;<a href="FldA.html" title="annotation in typeannos">@FldA<\
                    /a> <a href="Parameterized.html" title="class in typeannos">Parameterized</a>&lt\
                    ;<a href="FldA.html" title="annotation in typeannos">@FldA</a> java.lang.String,\
                    &#8203;<a href="FldB.html" title="annotation in typeannos">@FldB</a> java.lang.S\
                    tring&gt;,&#8203;<a href="FldB.html" title="annotation in typeannos">@FldB</a> j\
                    ava.lang.String&gt;</span>&nbsp;<span class="element-name">nestedParameterized</\
                    span></div>""",

                """
                    <div class="member-signature"><span class="modifiers">public final</span>&nbsp;<\
                    span class="return-type"><a href="FldA.html" title="annotation in typeannos">@Fl\
                    dA</a> java.lang.String[][]</span>&nbsp;<span class="element-name">array2</span>\
                    </div>""");

        // Test for type annotations on method return types (MethodReturnType.java).
        checkOutput("typeannos/MtdDefaultScope.html", true,
                """
                    <div class="member-signature"><span class="modifiers">public</span>&nbsp;<span c\
                    lass="type-parameters">&lt;T&gt;</span>&nbsp;<span class="return-type"><a href="\
                    MRtnA.html" title="annotation in typeannos">@MRtnA</a> java.lang.String</span>&n\
                    bsp;<span class="element-name">method</span>()</div>""",

                // When JDK-8068737 is fixed, we should change the order
                """
                    <div class="member-signature"><span class="return-type"><a href="MRtnA.html" tit\
                    le="annotation in typeannos">@MRtnA</a> java.lang.String <a href="MRtnB.html" ti\
                    tle="annotation in typeannos">@MRtnB</a> [] <a href="MRtnA.html" title="annotati\
                    on in typeannos">@MRtnA</a> []</span>&nbsp;<span class="element-name">array2Deep\
                    </span>()</div>""",

                """
                    <div class="member-signature"><span class="return-type"><a href="MRtnA.html" tit\
                    le="annotation in typeannos">@MRtnA</a> java.lang.String[][]</span>&nbsp;<span c\
                    lass="element-name">array2</span>()</div>""");

        checkOutput("typeannos/MtdModifiedScoped.html", true,
                """
                    <div class="member-signature"><span class="modifiers">public final</span>&nbsp;<\
                    span class="return-type"><a href="MtdParameterized.html" title="class in typeann\
                    os">MtdParameterized</a>&lt;<a href="MRtnA.html" title="annotation in typeannos"\
                    >@MRtnA</a> <a href="MtdParameterized.html" title="class in typeannos">MtdParame\
                    terized</a>&lt;<a href="MRtnA.html" title="annotation in typeannos">@MRtnA</a> j\
                    ava.lang.String,&#8203;<a href="MRtnB.html" title="annotation in typeannos">@MRt\
                    nB</a> java.lang.String&gt;,&#8203;<a href="MRtnB.html" title="annotation in typ\
                    eannos">@MRtnB</a> java.lang.String&gt;</span>&nbsp;<span class="element-name">n\
                    estedMtdParameterized</span>()</div>""");

        // Test for type annotations on method type parameters (MethodTypeParameters.java).
        checkOutput("typeannos/UnscopedUnmodified.html", true,
                """
                    <div class="member-signature"><span class="type-parameters">&lt;K extends <a hre\
                    f="MTyParamA.html" title="annotation in typeannos">@MTyParamA</a> java.lang.Stri\
                    ng&gt;</span>&nbsp;<span class="return-type">void</span>&nbsp;<span class="eleme\
                    nt-name">methodExtends</span>()</div>""",

                """
                    <div class="member-signature"><span class="type-parameters-long">&lt;K extends <\
                    a href="MTyParamA.html" title="annotation in typeannos">@MTyParamA</a> <a href="\
                    MtdTyParameterized.html" title="class in typeannos">MtdTyParameterized</a>&lt;<a\
                     href="MTyParamB.html" title="annotation in typeannos">@MTyParamB</a> java.lang.\
                    String&gt;&gt;</span>
                    <span class="return-type">void</span>&nbsp;<span class="element-name">nestedExtends</span>()</div>""");

        checkOutput("typeannos/PublicModifiedMethods.html", true,
                """
                    <div class="member-signature"><span class="modifiers">public final</span>&nbsp;<\
                    span class="type-parameters">&lt;K extends <a href="MTyParamA.html" title="annot\
                    ation in typeannos">@MTyParamA</a> java.lang.String&gt;</span>
                    <span class="return-type">void</span>&nbsp;<span class="element-name">methodExtends</span>()</div>""",

                """
                    <div class="member-signature"><span class="modifiers">public final</span>&nbsp;<\
                    span class="type-parameters-long">&lt;K extends <a href="MTyParamA.html" title="\
                    annotation in typeannos">@MTyParamA</a> java.lang.String,&#8203;
                    V extends <a href="MTyParamA.html" title="annotation in typeannos">@MTyParamA</a\
                    > <a href="MtdTyParameterized.html" title="class in typeannos">MtdTyParameterize\
                    d</a>&lt;<a href="MTyParamB.html" title="annotation in typeannos">@MTyParamB</a>\
                     java.lang.String&gt;&gt;</span>
                    <span class="return-type">void</span>&nbsp;<span class="element-name">dual</span>()</div>""");

        // Test for type annotations on parameters (Parameters.java).
        checkOutput("typeannos/Parameters.html", true,
                """
                    <div class="member-signature"><span class="return-type">void</span>&nbsp;<span c\
                    lass="element-name">unannotated</span>&#8203;<span class="parameters">(<a href="\
                    ParaParameterized.html" title="class in typeannos">ParaParameterized</a>&lt;java\
                    .lang.String,&#8203;java.lang.String&gt;&nbsp;a)</span></div>""",

                """
                    <div class="member-signature"><span class="return-type">void</span>&nbsp;<span c\
                    lass="element-name">nestedParaParameterized</span>&#8203;<span class="parameters\
                    ">(<a href="ParaParameterized.html" title="class in typeannos">ParaParameterized\
                    </a>&lt;<a href="ParamA.html" title="annotation in typeannos">@ParamA</a> <a hre\
                    f="ParaParameterized.html" title="class in typeannos">ParaParameterized</a>&lt;<\
                    a href="ParamA.html" title="annotation in typeannos">@ParamA</a> java.lang.Strin\
                    g,&#8203;<a href="ParamB.html" title="annotation in typeannos">@ParamB</a> java.\
                    lang.String&gt;,&#8203;<a href="ParamB.html" title="annotation in typeannos">@Pa\
                    ramB</a> java.lang.String&gt;&nbsp;a)</span></div>""",

                // When JDK-8068737 is fixed, we should change the order
                """
                    <div class="member-signature"><span class="return-type">void</span>&nbsp;<span c\
                    lass="element-name">array2Deep</span>&#8203;<span class="parameters">(<a href="P\
                    aramA.html" title="annotation in typeannos">@ParamA</a> java.lang.String <a href\
                    ="ParamB.html" title="annotation in typeannos">@ParamB</a> [] <a href="ParamA.ht\
                    ml" title="annotation in typeannos">@ParamA</a> []&nbsp;a)</span></div>""");

        // Test for type annotations on throws (Throws.java).
        checkOutput("typeannos/ThrDefaultUnmodified.html", true,
                """
                    <div class="member-signature"><span class="return-type">void</span>&nbsp;<span class="element-name">oneException</span>()
                               throws <span class="exceptions"><a href="ThrA.html" title="annotation\
                     in typeannos">@ThrA</a> java.lang.Exception</span></div>""",

                """
                    <div class="member-signature"><span class="return-type">void</span>&nbsp;<span c\
                    lass="element-name">twoExceptions</span>()
                                throws <span class="exceptions"><a href="ThrA.html" title="annotatio\
                    n in typeannos">@ThrA</a> java.lang.RuntimeException,
                    <a href="ThrA.html" title="annotation in typeannos">@ThrA</a> java.lang.Exception</span></div>""");

        checkOutput("typeannos/ThrPublicModified.html", true,
                """
                    <div class="member-signature"><span class="modifiers">public final</span>&nbsp;<\
                    span class="return-type">void</span>&nbsp;<span class="element-name">oneException</span>\
                    &#8203;<span class="parameters">(java.lang.String&nbsp;a)</span>
                                            throws <span class="exceptions"><a href="ThrA.html" titl\
                    e="annotation in typeannos">@ThrA</a> java.lang.Exception</span></div>""",

                """
                    <div class="member-signature"><span class="modifiers">public final</span>&nbsp;<\
                    span class="return-type">void</span>&nbsp;<span class="element-name">twoExceptions</span\
                    >&#8203;<span class="parameters">(java.lang.String&nbsp;a)</span>
                                             throws <span class="exceptions"><a href="ThrA.html" tit\
                    le="annotation in typeannos">@ThrA</a> java.lang.RuntimeException,
                    <a href="ThrA.html" title="annotation in typeannos">@ThrA</a> java.lang.Exception</span></div>""");

        checkOutput("typeannos/ThrWithValue.html", true,
                """
                    <div class="member-signature"><span class="return-type">void</span>&nbsp;<span class="element-name">oneException</span>()
                               throws <span class="exceptions"><a href="ThrB.html" title="annotation\
                     in typeannos">@ThrB</a>("m") java.lang.Exception</span></div>""",

                """
                    <div class="member-signature"><span class="return-type">void</span>&nbsp;<span c\
                    lass="element-name">twoExceptions</span>()
                                throws <span class="exceptions"><a href="ThrB.html" title="annotatio\
                    n in typeannos">@ThrB</a>("m") java.lang.RuntimeException,
                    <a href="ThrA.html" title="annotation in typeannos">@ThrA</a> java.lang.Exception</span></div>""");

        // Test for type annotations on type parameters (TypeParameters.java).
        checkOutput("typeannos/TestMethods.html", true,
                """
                    <div class="member-signature"><span class="type-parameters">&lt;K,&#8203;
                    <a href="TyParaA.html" title="annotation in typeannos">@TyParaA</a> V extends <a\
                     href="TyParaA.html" title="annotation in typeannos">@TyParaA</a> java.lang.Stri\
                    ng&gt;</span>
                    <span class="return-type">void</span>&nbsp;<span class="element-name">secondAnnotated</span>()</div>"""
        );

        // Test for type annotations on wildcard type (Wildcards.java).
        checkOutput("typeannos/BoundTest.html", true,
                """
                    <div class="member-signature"><span class="return-type">void</span>&nbsp;<span c\
                    lass="element-name">wcExtends</span>&#8203;<span class="parameters">(<a href="My\
                    List.html" title="class in typeannos">MyList</a>&lt;? extends <a href="WldA.html\
                    " title="annotation in typeannos">@WldA</a> java.lang.String&gt;&nbsp;l)</span><\
                    /div>""",

                """
                    <div class="member-signature"><span class="return-type"><a href="MyList.html" ti\
                    tle="class in typeannos">MyList</a>&lt;? super <a href="WldA.html" title="annota\
                    tion in typeannos">@WldA</a> java.lang.String&gt;</span>&nbsp;<span class="element-name"\
                    >returnWcSuper</span>()</div>""");

        checkOutput("typeannos/BoundWithValue.html", true,
                """
                    <div class="member-signature"><span class="return-type">void</span>&nbsp;<span c\
                    lass="element-name">wcSuper</span>&#8203;<span class="parameters">(<a href="MyLi\
                    st.html" title="class in typeannos">MyList</a>&lt;? super <a href="WldB.html" ti\
                    tle="annotation in typeannos">@WldB</a>("m") java.lang.String&gt;&nbsp;l)</span>\
                    </div>""",

                """
                    <div class="member-signature"><span class="return-type"><a href="MyList.html" ti\
                    tle="class in typeannos">MyList</a>&lt;? extends <a href="WldB.html" title="anno\
                    tation in typeannos">@WldB</a>("m") java.lang.String&gt;</span>&nbsp;<span class\
                    ="element-name">returnWcExtends</span>()</div>""");

        checkOutput("typeannos/SelfTest.html", true,
                """
                    <div class="member-signature"><span class="return-type"><a href="MyList.html" ti\
                    tle="class in typeannos">MyList</a>&lt;<a href="WldA.html" title="annotation in \
                    typeannos">@WldA</a> ?&gt;</span>&nbsp;<span class="element-name">returnWcExtends</span>\
                    ()</div>""",
                """
                    <div class="member-signature"><span class="return-type"><a href="MyList.html" ti\
                    tle="class in typeannos">MyList</a>&lt;<a href="WldA.html" title="annotation in \
                    typeannos">@WldA</a> ? extends <a href="WldA.html" title="annotation in typeanno\
                    s">@WldA</a> <a href="MyList.html" title="class in typeannos">MyList</a>&lt;<a h\
                    ref="WldB.html" title="annotation in typeannos">@WldB</a>("m") ?&gt;&gt;</span>&\
                    nbsp;<span class="element-name">complex</span>()</div>""");

        checkOutput("typeannos/SelfWithValue.html", true,
                """
                    <div class="member-signature"><span class="return-type"><a href="MyList.html" ti\
                    tle="class in typeannos">MyList</a>&lt;<a href="WldB.html" title="annotation in \
                    typeannos">@WldB</a>("m") ?&gt;</span>&nbsp;<span class="element-name">returnWcExtends</\
                    span>()</div>""",
                """
                    <div class="member-signature"><span class="return-type"><a href="MyList.html" ti\
                    tle="class in typeannos">MyList</a>&lt;<a href="WldB.html" title="annotation in \
                    typeannos">@WldB</a>("m") ? extends <a href="MyList.html" title="class in typean\
                    nos">MyList</a>&lt;<a href="WldB.html" title="annotation in typeannos">@WldB</a>\
                    ("m") ? super java.lang.String&gt;&gt;</span>&nbsp;<span class="element-name">complex</s\
                    pan>()</div>""");


        // Test for receiver annotations (Receivers.java).
        checkOutput("typeannos/DefaultUnmodified.html", true,
                """
                    <div class="member-signature"><span class="return-type">void</span>&nbsp;<span c\
                    lass="element-name">withException</span>&#8203;<span class="parameters">(<a href\
                    ="RcvrA.html" title="annotation in typeannos">@RcvrA</a> DefaultUnmodified&\
                    nbsp;this)</span>
                                throws <span class="exceptions">java.lang.Exception</span></div>""",

                """
                    <div class="member-signature"><span class="return-type">java.lang.String</span>&\
                    nbsp;<span class="element-name">nonVoid</span>&#8203;<span class="parameters">(<a href="\
                    RcvrA.html" title="annotation in typeannos">@RcvrA</a> <a href="RcvrB.html" titl\
                    e="annotation in typeannos">@RcvrB</a>("m") DefaultUnmodified&nbsp;this)</s\
                    pan></div>""",

                """
                    <div class="member-signature"><span class="type-parameters">&lt;T extends java.l\
                    ang.Runnable&gt;</span>&nbsp;<span class="return-type">void</span>&nbsp;<span cl\
                    ass="element-name">accept</span>&#8203;<span class="parameters">(<a href="RcvrA.\
                    html" title="annotation in typeannos">@RcvrA</a> DefaultUnmodified&nbsp;thi\
                    s,
                     T&nbsp;r)</span>
                                                        throws <span class="exceptions">java.lang.Exception</span></div>""");

        checkOutput("typeannos/PublicModified.html", true,
                """
                    <div class="member-signature"><span class="modifiers">public final</span>&nbsp;<\
                    span class="return-type">java.lang.String</span>&nbsp;<span class="element-name">nonVoid\
                    </span>&#8203;<span class="parameters">(<a href="RcvrA.html" title="annotation i\
                    n typeannos">@RcvrA</a> PublicModified&nbsp;this)</span></div>""",

                """
                    <div class="member-signature"><span class="modifiers">public final</span>&nbsp;<\
                    span class="type-parameters">&lt;T extends java.lang.Runnable&gt;</span>&nbsp;<s\
                    pan class="return-type">void</span>&nbsp;<span class="element-name">accept</span>&#8203;\
                    <span class="parameters">(<a href="RcvrA.html" title="annotation in typea\
                    nnos">@RcvrA</a> PublicModified&nbsp;this,
                     T&nbsp;r)</span>
                                                                     throws <span class="exceptions">java.lang.Exception</span></div>""");

        checkOutput("typeannos/WithValue.html", true,
                """
                    <div class="member-signature"><span class="type-parameters">&lt;T extends java.l\
                    ang.Runnable&gt;</span>&nbsp;<span class="return-type">void</span>&nbsp;<span cl\
                    ass="element-name">accept</span>&#8203;<span class="parameters">(<a href="RcvrB.\
                    html" title="annotation in typeannos">@RcvrB</a>("m") WithValue&nbsp;this,
                     T&nbsp;r)</span>
                                                        throws <span class="exceptions">java.lang.Exception</span></div>""");

        checkOutput("typeannos/WithFinal.html", true,
                """
                    <div class="member-signature"><span class="return-type">java.lang.String</span>&\
                    nbsp;<span class="element-name">nonVoid</span>&#8203;<span class="parameters">(<a href="\
                    RcvrB.html" title="annotation in typeannos">@RcvrB</a>("m") <a href="WithFinal.h\
                    tml" title="class in typeannos">WithFinal</a>&nbsp;afield)</span></div>""");

        checkOutput("typeannos/WithBody.html", true,
                """
                    <div class="member-signature"><span class="return-type">void</span>&nbsp;<span c\
                    lass="element-name">field</span>&#8203;<span class="parameters">(<a href="RcvrA.\
                    html" title="annotation in typeannos">@RcvrA</a> WithBody&nbsp;this)</span>\
                    </div>""");

        checkOutput("typeannos/Generic2.html", true,
                """
                    <div class="member-signature"><span class="return-type">void</span>&nbsp;<span c\
                    lass="element-name">test1</span>()</div>""",
                """
                    <div class="member-signature"><span class="return-type">void</span>&nbsp;<span c\
                    lass="element-name">test2</span>&#8203;<span class="parameters">(<a href="RcvrA.\
                    html" title="annotation in typeannos">@RcvrA</a> Generic2&lt;X&gt;&nbsp;thi\
                    s)</span></div>""",
                """
                    <div class="member-signature"><span class="return-type">void</span>&nbsp;<span c\
                    lass="element-name">test3</span>&#8203;<span class="parameters">(Generic2&lt;<a \
                    href="RcvrA.html" title="annotation in typeannos">@RcvrA</a> X&gt;&nbsp;this)</s\
                    pan></div>""",
                """
                   <div class="member-signature"><span class="return-type">void</span>&nbsp;<span cl\
                   ass="element-name">test4</span>&#8203;<span class="parameters">(<a href="RcvrA.ht\
                   ml" title="annotation in typeannos">@RcvrA</a> Generic2&lt;<a href="RcvrA.html" t\
                   itle="annotation in typeannos">@RcvrA</a> X&gt;&nbsp;this)</span></div>""");


        // Test for repeated type annotations (RepeatedAnnotations.java).
        checkOutput("typeannos/RepeatingAtClassLevel.html", true,
                """
                    <div class="type-signature"><span class="annotations"><a href="RepTypeA.html" ti\
                    tle="annotation in typeannos">@RepTypeA</a> <a href="RepTypeA.html" title="annot\
                    ation in typeannos">@RepTypeA</a>
                    <a href="RepTypeB.html" title="annotation in typeannos">@RepTypeB</a> <a href="R\
                    epTypeB.html" title="annotation in typeannos">@RepTypeB</a>
                    </span><span class="modifiers">class </span><span class="element-name type-name-\
                    label">RepeatingAtClassLevel</span>
                    <span class="extends-implements">extends java.lang.Object</span></div>""");

// @ignore 8146008
//        checkOutput("typeannos/RepeatingAtClassLevel2.html", true,
//                "<pre><a href=\"RepTypeUseA.html\" title=\"annotation "
//                + "in typeannos\">@RepTypeUseA</a> <a href=\"RepTypeUseA.html"
//                + "\" title=\"annotation in typeannos\">@RepTypeUseA</a>\n<a href="
//                + "\"RepTypeUseB.html\" title=\"annotation in typeannos"
//                + "\">@RepTypeUseB</a> <a href=\"RepTypeUseB.html\" "
//                + "title=\"annotation in typeannos\">@RepTypeUseB</a>\nclass <span "
//                + "class=\"type-name-label\">RepeatingAtClassLevel2</span>\nextends "
//                + "java.lang.Object</pre>");
//
//        checkOutput("typeannos/RepeatingAtClassLevel2.html", true,
//                "<pre><a href=\"RepAllContextsA.html\" title=\"annotation"
//                + " in typeannos\">@RepAllContextsA</a> <a href=\"RepAllContextsA.html"
//                + "\" title=\"annotation in typeannos\">@RepAllContextsA</a>\n<a href="
//                + "\"RepAllContextsB.html\" title=\"annotation in typeannos"
//                + "\">@RepAllContextsB</a> <a href=\"RepAllContextsB.html"
//                + "\" title=\"annotation in typeannos\">@RepAllContextsB</a>\n"
//                + "class <span class=\"type-name-label\">RepeatingAtClassLevel3</span>\n"
//                + "extends java.lang.Object</pre>");

        checkOutput("typeannos/RepeatingOnConstructor.html", true,
                """
                    <div class="member-signature"><span class="annotations"><a href="RepConstructorA\
                    .html" title="annotation in typeannos">@RepConstructorA</a> <a href="RepConstruc\
                    torA.html" title="annotation in typeannos">@RepConstructorA</a>
                    <a href="RepConstructorB.html" title="annotation in typeannos">@RepConstructorB<\
                    /a> <a href="RepConstructorB.html" title="annotation in typeannos">@RepConstruct\
                    orB</a>
                    </span><span class="element-name">RepeatingOnConstructor</span>()</div>""",

                """
                    <div class="member-signature"><span class="annotations"><a href="RepConstructorA\
                    .html" title="annotation in typeannos">@RepConstructorA</a> <a href="RepConstruc\
                    torA.html" title="annotation in typeannos">@RepConstructorA</a>
                    <a href="RepConstructorB.html" title="annotation in typeannos">@RepConstructorB<\
                    /a> <a href="RepConstructorB.html" title="annotation in typeannos">@RepConstruct\
                    orB</a>
                    </span><span class="element-name">RepeatingOnConstructor</span>&#8203;<span class="parameters">(int&nbsp;i,
                     int&nbsp;j)</span></div>""",

                """
                    <div class="member-signature"><span class="annotations"><a href="RepAllContextsA\
                    .html" title="annotation in typeannos">@RepAllContextsA</a> <a href="RepAllConte\
                    xtsA.html" title="annotation in typeannos">@RepAllContextsA</a>
                    <a href="RepAllContextsB.html" title="annotation in typeannos">@RepAllContextsB<\
                    /a> <a href="RepAllContextsB.html" title="annotation in typeannos">@RepAllContex\
                    tsB</a>
                    </span><span class="element-name">RepeatingOnConstructor</span>&#8203;<span class="parameters">(int&nbsp;i,
                     int&nbsp;j,
                     int&nbsp;k)</span></div>""",

                """
                    <div class="member-signature"><span class="element-name">RepeatingOnConstructor</span>&#\
                    8203;<span class="parameters">(<a href="RepParameterA.html" title="annotation in\
                     typeannos">@RepParameterA</a> <a href="RepParameterA.html" title="annotation in\
                     typeannos">@RepParameterA</a> <a href="RepParameterB.html" title="annotation in\
                     typeannos">@RepParameterB</a> <a href="RepParameterB.html" title="annotation in\
                     typeannos">@RepParameterB</a>
                     java.lang.String&nbsp;parameter,
                     <a href="RepParameterA.html" title="annotation in typeannos">@RepParameterA</a> \
                    <a href="RepParameterA.html" title="annotation in typeannos">@RepParameterA</a> \
                    <a href="RepParameterB.html" title="annotation in typeannos">@RepParameterB</a> \
                    <a href="RepParameterB.html" title="annotation in typeannos">@RepParameterB</a>
                     java.lang.String <a href="RepTypeUseA.html" title="annotation in typeannos">@Rep\
                    TypeUseA</a> <a href="RepTypeUseA.html" title="annotation in typeannos">@RepType\
                    UseA</a> <a href="RepTypeUseB.html" title="annotation in typeannos">@RepTypeUseB\
                    </a> <a href="RepTypeUseB.html" title="annotation in typeannos">@RepTypeUseB</a>\
                     ...&nbsp;vararg)</span></div>"""
        );

        checkOutput("typeannos/RepeatingOnConstructor.Inner.html", true,
                """
                    <code><a href="#%3Cinit%3E(java.lang.String,java.lang.String...)" class="member-\
                    name-link">Inner</a>&#8203;(java.lang.String&nbsp;parameter,
                     java.lang.String <a href="RepTypeUseA.html" title="annotation in typeannos">@Rep\
                    TypeUseA</a> <a href="RepTypeUseA.html" title="annotation in typeannos">@RepType\
                    UseA</a> <a href="RepTypeUseB.html" title="annotation in typeannos">@RepTypeUseB\
                    </a> <a href="RepTypeUseB.html" title="annotation in typeannos">@RepTypeUseB</a>\
                     ...&nbsp;vararg)</code>""",
                """
                    Inner</span>&#8203;<span class="parameters">(<a href="RepTypeUseA.html" title="a\
                    nnotation in typeannos">@RepTypeUseA</a> <a href="RepTypeUseA.html" title="annot\
                    ation in typeannos">@RepTypeUseA</a> <a href="RepTypeUseB.html" title="annotatio\
                    n in typeannos">@RepTypeUseB</a> <a href="RepTypeUseB.html" title="annotation in\
                     typeannos">@RepTypeUseB</a> <a href="RepeatingOnConstructor.html" title="class \
                    in typeannos">RepeatingOnConstructor</a>&nbsp;RepeatingOnConstructor.this,
                     <a href="RepParameterA.html" title="annotation in typeannos">@RepParameterA</a> \
                    <a href="RepParameterA.html" title="annotation in typeannos">@RepParameterA</a> \
                    <a href="RepParameterB.html" title="annotation in typeannos">@RepParameterB</a> \
                    <a href="RepParameterB.html" title="annotation in typeannos">@RepParameterB</a>
                     java.lang.String&nbsp;parameter,
                     <a href="RepParameterA.html" title="annotation in typeannos">@RepParameterA</a> \
                    <a href="RepParameterA.html" title="annotation in typeannos">@RepParameterA</a> \
                    <a href="RepParameterB.html" title="annotation in typeannos">@RepParameterB</a> \
                    <a href="RepParameterB.html" title="annotation in typeannos">@RepParameterB</a>
                     java.lang.String <a href="RepTypeUseA.html" title="annotation in typeannos">@Rep\
                    TypeUseA</a> <a href="RepTypeUseA.html" title="annotation in typeannos">@RepType\
                    UseA</a> <a href="RepTypeUseB.html" title="annotation in typeannos">@RepTypeUseB\
                    </a> <a href="RepTypeUseB.html" title="annotation in typeannos">@RepTypeUseB</a>\
                     ...&nbsp;vararg)</span>""");

        checkOutput("typeannos/RepeatingOnField.html", true,
                """
                    <div class="col-first even-row-color"><code>(package private) java.lang.Integer</code></div>
                    <div class="col-second even-row-color"><code><a href="#i1" class="member-name-li\
                    nk">i1</a></code></div>""",

                """
                    <div class="col-first odd-row-color"><code>(package private) <a href="RepTypeUseA.ht\
                    ml" title="annotation in typeannos">@RepTypeUseA</a> <a href="RepTypeUseA.html"\
                     title="annotation in typeannos">@RepTypeUseA</a> <a href="RepTypeUseB.html" tit\
                    le="annotation in typeannos">@RepTypeUseB</a> <a href="RepTypeUseB.html" title="\
                    annotation in typeannos">@RepTypeUseB</a> java.lang.Integer</code></div>
                    <div class="col-second odd-row-color"><code><a href="\
                    #i2" class="member-name-link">i2</a></code></div>
                    <div class="col-last odd-row-color">&nbsp;</div>""",

                """
                    <div class="col-first even-row-color"><code>(package private) <a href="RepTypeUseA.ht\
                    ml" title="annotation in typeannos">@RepTypeUseA</a> <a href="RepTypeUseA.html"\
                     title="annotation in typeannos">@RepTypeUseA</a> <a href="RepTypeUseB.html" tit\
                    le="annotation in typeannos">@RepTypeUseB</a> <a href="RepTypeUseB.html" title="\
                    annotation in typeannos">@RepTypeUseB</a> java.lang.Integer</code></div>
                    <div class="col-second even-row-color"><code><a href="#i3" class="member-name-li\
                    nk">i3</a></code></div>""",

                """
                    <div class="col-first odd-row-color"><code>(package private) <a href="RepAllContexts\
                    A.html" title="annotation in typeannos">@RepAllContextsA</a> <a href="RepAllCont\
                    extsA.html" title="annotation in typeannos">@RepAllContextsA</a> <a href="RepAll\
                    ContextsB.html" title="annotation in typeannos">@RepAllContextsB</a> <a href="Re\
                    pAllContextsB.html" title="annotation in typeannos">@RepAllContextsB</a> java.la\
                    ng.Integer</code></div>
                    <div class="col-second odd-row-color"><code><a href="#i4" class="member-name-lin\
                    k">i4</a></code></div>""",

                """
                    <div class="col-first even-row-color"><code>(package private) java.lang.String <a hre\
                    f="RepTypeUseA.html" title="annotation in typeannos">@RepTypeUseA</a> <a href="R\
                    epTypeUseA.html" title="annotation in typeannos">@RepTypeUseA</a> <a href="RepTy\
                    peUseB.html" title="annotation in typeannos">@RepTypeUseB</a> <a href="RepTypeUs\
                    eB.html" title="annotation in typeannos">@RepTypeUseB</a> [] <a href="RepTypeUse\
                    A.html" title="annotation in typeannos">@RepTypeUseA</a> <a href="RepTypeUseA.ht\
                    ml" title="annotation in typeannos">@RepTypeUseA</a> <a href="RepTypeUseB.html"\
                     title="annotation in typeannos">@RepTypeUseB</a> <a href="RepTypeUseB.html" tit\
                    le="annotation in typeannos">@RepTypeUseB</a> []</code></div>
                    <div class="col-second even-row-color"><code><a href="#sa" class="member-name-li\
                    nk">sa</a></code></div>""",

                """
                    <div class="member-signature"><span class="annotations"><a href="RepFieldA.html"\
                     title="annotation in typeannos">@RepFieldA</a> <a href="RepFieldA.html" title="\
                    annotation in typeannos">@RepFieldA</a>
                    <a href="RepFieldB.html" title="annotation in typeannos">@RepFieldB</a> <a href=\
                    "RepFieldB.html" title="annotation in typeannos">@RepFieldB</a>
                    </span><span class="return-type">java.lang.Integer</span>&nbsp;<span class="elem\
                    ent-name">i1</span></div>""",

                """
                    <div class="member-signature"><span class="return-type"><a href="RepTypeUseA.htm\
                    l" title="annotation in typeannos">@RepTypeUseA</a> <a href="RepTypeUseA.html" t\
                    itle="annotation in typeannos">@RepTypeUseA</a> <a href="RepTypeUseB.html" title\
                    ="annotation in typeannos">@RepTypeUseB</a> <a href="RepTypeUseB.html" title="an\
                    notation in typeannos">@RepTypeUseB</a> java.lang.Integer</span>&nbsp;<span clas\
                    s="element-name">i2</span></div>""",

                """
                    <div class="member-signature"><span class="annotations"><a href="RepFieldA.html"\
                     title="annotation in typeannos">@RepFieldA</a> <a href="RepFieldA.html" title="\
                    annotation in typeannos">@RepFieldA</a>
                    <a href="RepFieldB.html" title="annotation in typeannos">@RepFieldB</a> <a href=\
                    "RepFieldB.html" title="annotation in typeannos">@RepFieldB</a>
                    </span><span class="return-type"><a href="RepTypeUseA.html" title="annotation in\
                     typeannos">@RepTypeUseA</a> <a href="RepTypeUseA.html" title="annotation in typ\
                    eannos">@RepTypeUseA</a> <a href="RepTypeUseB.html" title="annotation in typeann\
                    os">@RepTypeUseB</a> <a href="RepTypeUseB.html" title="annotation in typeannos">\
                    @RepTypeUseB</a> java.lang.Integer</span>&nbsp;<span class="element-name">i3</spa\
                    n></div>""",

                """
                    <div class="member-signature"><span class="annotations"><a href="RepAllContextsA\
                    .html" title="annotation in typeannos">@RepAllContextsA</a> <a href="RepAllConte\
                    xtsA.html" title="annotation in typeannos">@RepAllContextsA</a>
                    <a href="RepAllContextsB.html" title="annotation in typeannos">@RepAllContextsB<\
                    /a> <a href="RepAllContextsB.html" title="annotation in typeannos">@RepAllContex\
                    tsB</a>
                    </span><span class="return-type"><a href="RepAllContextsA.html" title="annotatio\
                    n in typeannos">@RepAllContextsA</a> <a href="RepAllContextsA.html" title="annot\
                    ation in typeannos">@RepAllContextsA</a> <a href="RepAllContextsB.html" title="a\
                    nnotation in typeannos">@RepAllContextsB</a> <a href="RepAllContextsB.html" titl\
                    e="annotation in typeannos">@RepAllContextsB</a> java.lang.Integer</span>&nbsp;<\
                    span class="element-name">i4</span></div>""",

                """
                    <div class="member-signature"><span class="return-type">java.lang.String <a href\
                    ="RepTypeUseA.html" title="annotation in typeannos">@RepTypeUseA</a> <a href="Re\
                    pTypeUseA.html" title="annotation in typeannos">@RepTypeUseA</a> <a href="RepTyp\
                    eUseB.html" title="annotation in typeannos">@RepTypeUseB</a> <a href="RepTypeUse\
                    B.html" title="annotation in typeannos">@RepTypeUseB</a> [] <a href="RepTypeUseA\
                    .html" title="annotation in typeannos">@RepTypeUseA</a> <a href="RepTypeUseA.htm\
                    l" title="annotation in typeannos">@RepTypeUseA</a> <a href="RepTypeUseB.html" t\
                    itle="annotation in typeannos">@RepTypeUseB</a> <a href="RepTypeUseB.html" title\
                    ="annotation in typeannos">@RepTypeUseB</a> []</span>&nbsp;<span class="element-\
                    name">sa</span></div>""");

        checkOutput("typeannos/RepeatingOnMethod.html", true,
                """
                    <code>(package private) java.lang.String</code></div>
                    <div class="col-second even-row-color method-summary-table method-summary-table-\
                    tab2 method-summary-table-tab4"><code><a href="#t\
                    est1()" class="member-name-link">test1</a>()</code>""",

                """
                    <code>(package private) <a href="RepTypeUseA.html" title="annotation in typeanno\
                    s">@RepTypeUseA</a> <a href="RepTypeUseA.html" title="annotation in typeannos">@\
                    RepTypeUseA</a> <a href="RepTypeUseB.html" title="annotation in typeannos">@RepT\
                    ypeUseB</a> <a href="RepTypeUseB.html" title="annotation in typeannos">@RepTypeU\
                    seB</a> java.lang.String</code></div>
                    <div class="col-second odd-row-color method-summary-table method-summary-table-t\
                    ab2 method-summary-table-tab4"><code><a href="#te\
                    st2()" class="member-name-link">test2</a>()</code>""",

                """
                    <code>(package private) <a href="RepTypeUseA.html" title="annotation in typeanno\
                    s">@RepTypeUseA</a> <a href="RepTypeUseA.html" title="annotation in typeannos">@\
                    RepTypeUseA</a> <a href="RepTypeUseB.html" title="annotation in typeannos">@RepT\
                    ypeUseB</a> <a href="RepTypeUseB.html" title="annotation in typeannos">@RepTypeU\
                    seB</a> java.lang.String</code></div>
                    <div class="col-second even-row-color method-summary-table method-summary-table-\
                    tab2 method-summary-table-tab4"><code><a href="#t\
                    est3()" class="member-name-link">test3</a>()</code>""",

                """
                    <code>(package private) <a href="RepAllContextsA.html" title="annotation in type\
                    annos">@RepAllContextsA</a> <a href="RepAllContextsA.html" title="annotation in\
                     typeannos">@RepAllContextsA</a> <a href="RepAllContextsB.html" title="annotatio\
                    n in typeannos">@RepAllContextsB</a> <a href="RepAllContextsB.html" title="annot\
                    ation in typeannos">@RepAllContextsB</a> java.lang.String</code></div>
                    <div class="col-second odd-row-color method-summary-table method-summary-table-t\
                    ab2 method-summary-table-tab4"><code><a href="#te\
                    st4()" class="member-name-link">test4</a>()</code>""",

                """
                    <code><a href="#test5(java.lang.String,java.lang.\
                    String...)" class="member-name-link">test5</a>&#8203;(java.lang.String&nbsp;parameter,
                     java.lang.String <a href="RepTypeUseA.html" title="annotation in typeannos">@Re\
                    pTypeUseA</a> <a href="RepTypeUseA.html" title="annotation in typeannos">@RepTyp\
                    eUseA</a> <a href="RepTypeUseB.html" title="annotation in typeannos">@RepTypeUse\
                    B</a> <a href="RepTypeUseB.html" title="annotation in typeannos">@RepTypeUseB</a\
                    > ...&nbsp;vararg)</code>""",

                """
                    <a href="RepMethodA.html" title="annotation in typeannos">@RepMethodA</a> <a hre\
                    f="RepMethodA.html" title="annotation in typeannos">@RepMethodA</a>
                    <a href="RepMethodB.html" title="annotation in typeannos">@RepMethodB</a> <a hre\
                    f="RepMethodB.html" title="annotation in typeannos">@RepMethodB</a>
                    </span><span class="return-type">java.lang.String</span>&nbsp;<span class="element-name">test1</span>()""",

                """
                    <a href="RepTypeUseA.html" title="annotation in typeannos">@RepTypeUseA</a> <a h\
                    ref="RepTypeUseA.html" title="annotation in typeannos">@RepTypeUseA</a> <a href=\
                    "RepTypeUseB.html" title="annotation in typeannos">@RepTypeUseB</a> <a href="Rep\
                    TypeUseB.html" title="annotation in typeannos">@RepTypeUseB</a> java.lang.String\
                    </span>&nbsp;<span class="element-name">test2</span>()""",

                """
                    <a href="RepMethodA.html" title="annotation in typeannos">@RepMethodA</a> <a hre\
                    f="RepMethodA.html" title="annotation in typeannos">@RepMethodA</a>
                    <a href="RepMethodB.html" title="annotation in typeannos">@RepMethodB</a> <a hre\
                    f="RepMethodB.html" title="annotation in typeannos">@RepMethodB</a>
                    </span><span class="return-type"><a href="RepTypeUseA.html" title="annotation in\
                     typeannos">@RepTypeUseA</a> <a href="RepTypeUseA.html" title="annotation in typ\
                    eannos">@RepTypeUseA</a> <a href="RepTypeUseB.html" title="annotation in typeann\
                    os">@RepTypeUseB</a> <a href="RepTypeUseB.html" title="annotation in typeannos">\
                    @RepTypeUseB</a> java.lang.String</span>&nbsp;<span class="element-name">test3</s\
                    pan>()""",

                """
                    <a href="RepAllContextsA.html" title="annotation in typeannos">@RepAllContextsA<\
                    /a> <a href="RepAllContextsA.html" title="annotation in typeannos">@RepAllContex\
                    tsA</a>
                    <a href="RepAllContextsB.html" title="annotation in typeannos">@RepAllContextsB<\
                    /a> <a href="RepAllContextsB.html" title="annotation in typeannos">@RepAllContex\
                    tsB</a>
                    </span><span class="return-type"><a href="RepAllContextsA.html" title="annotatio\
                    n in typeannos">@RepAllContextsA</a> <a href="RepAllContextsA.html" title="annot\
                    ation in typeannos">@RepAllContextsA</a> <a href="RepAllContextsB.html" title="a\
                    nnotation in typeannos">@RepAllContextsB</a> <a href="RepAllContextsB.html" titl\
                    e="annotation in typeannos">@RepAllContextsB</a> java.lang.String</span>&nbsp;<s\
                    pan class="element-name">test4</span>()""",

                """
                    java.lang.String</span>&nbsp;<span class="element-name">test5</span>&#8203;<span\
                     class="parameters">(<a href="RepTypeUseA.html" title="annotation in typeannos">@\
                    RepTypeUseA</a> <a href="RepTypeUseA.html" title="annotation in typeannos">@RepT\
                    ypeUseA</a> <a href="RepTypeUseB.html" title="annotation in typeannos">@RepTypeU\
                    seB</a> <a href="RepTypeUseB.html" title="annotation in typeannos">@RepTypeUseB<\
                    /a> RepeatingOnMethod&nbsp;this,
                     <a href="RepParameterA.html" title="annotation in typeannos">@RepParameterA</a> \
                    <a href="RepParameterA.html" title="annotation in typeannos">@RepParameterA</a> \
                    <a href="RepParameterB.html" title="annotation in typeannos">@RepParameterB</a> \
                    <a href="RepParameterB.html" title="annotation in typeannos">@RepParameterB</a>
                     java.lang.String&nbsp;parameter,
                     <a href="RepParameterA.html" title="annotation in typeannos">@RepParameterA</a>\
                     <a href="RepParameterA.html" title="annotation in typeannos">@RepParameterA</a>\
                     <a href="RepParameterB.html" title="annotation in typeannos">@RepParameterB</a>\
                     <a href="RepParameterB.html" title="annotation in typeannos">@RepParameterB</a>
                     java.lang.String <a href="RepTypeUseA.html" title="annotation in typeannos">@Re\
                    pTypeUseA</a> <a href="RepTypeUseA.html" title="annotation in typeannos">@RepTyp\
                    eUseA</a> <a href="RepTypeUseB.html" title="annotation in typeannos">@RepTypeUse\
                    B</a> <a href="RepTypeUseB.html" title="annotation in typeannos">@RepTypeUseB</a\
                    > ...&nbsp;vararg)</span></div>""");

        checkOutput("typeannos/RepeatingOnTypeParametersBoundsTypeArgumentsOnMethod.html", true,
                """
                    <code>(package private) &lt;T&gt;&nbsp;java.lang.String</code></div>
                    <div class="col-second even-row-color method-summary-table method-summary-table-\
                    tab2 method-summary-table-tab4"><code><a href="#genericMethod(T)" class="member-\
                    name-link">genericMethod</a>&#8203;(T&nbsp;t)</code>""",

                """
                    <code>(package private) &lt;T&gt;&nbsp;java.lang.String</code></div>
                    <div class="col-second odd-row-color method-summary-table method-summary-table-t\
                    ab2 method-summary-table-tab4"><code><a href="#genericMethod2(T)" class="member-\
                    name-link">genericMethod2</a>&#8203;(<a href="RepTypeUseA.html" title="annotatio\
                    n in typeannos">@RepTypeUseA</a> <a href="RepTypeUseA.html" title="anno\
                    tation in typeannos">@RepTypeUseA</a> <a href="RepTypeUseB.html" title="annotati\
                    on in typeannos">@RepTypeUseB</a> <a href="RepTypeUseB.html" title="annotation i\
                    n typeannos">@RepTypeUseB</a> T&nbsp;t)</code>""",

                """
                    <code>(package private) java.lang.String</code></div>
                    <div class="col-second even-row-color method-summary-table method-summary-table-\
                    tab2 method-summary-table-tab4"><code><a href="#test()" class="member-name-link"\
                    >test</a>()</code>""",

                """
                    <span class="return-type">java.lang.String</span>&nbsp;<span class="element-name">test</\
                    span>&#8203;<span class="parameters">(<a href="RepTypeUseA.html" title="annotati\
                    on in typeannos">@RepTypeUseA</a> <a href="RepTypeUseA.html" title="annotation i\
                    n typeannos">@RepTypeUseA</a> <a href="RepTypeUseB.html" title="annotation in ty\
                    peannos">@RepTypeUseB</a> <a href="RepTypeUseB.html" title="annotation in typean\
                    nos">@RepTypeUseB</a> RepeatingOnTypeParametersBoundsTypeArgumentsOnMethod&\
                    lt;<a href="RepTypeUseA.html" title="annotation in typeannos">@RepTypeUseA</a> <\
                    a href="RepTypeUseA.html" title="annotation in typeannos">@RepTypeUseA</a> <a hr\
                    ef="RepTypeUseB.html" title="annotation in typeannos">@RepTypeUseB</a> <a href="\
                    RepTypeUseB.html" title="annotation in typeannos">@RepTypeUseB</a> T&gt;&nbsp;th\
                    is)""");

        checkOutput("typeannos/RepeatingOnVoidMethodDeclaration.html", true,
                """
                    <a href="RepMethodA.html" title="annotation in typeannos">@RepMethodA</a> <a hre\
                    f="RepMethodA.html" title="annotation in typeannos">@RepMethodA</a>
                    <a href="RepMethodB.html" title="annotation in typeannos">@RepMethodB</a> <a hre\
                    f="RepMethodB.html" title="annotation in typeannos">@RepMethodB</a>
                    </span><span class="return-type">void</span>&nbsp;<span class="element-name">test</span>()""");
    }
}
