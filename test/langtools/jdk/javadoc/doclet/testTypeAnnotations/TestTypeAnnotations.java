/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
 *           8187288
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
                "-sourcepath", testSrc,
                "-private",
                "typeannos");
        checkExit(Exit.OK);

        // Test for type annotations on Class Extends (ClassExtends.java).
        checkOutput("typeannos/MyClass.html", true,
                "extends <a href=\"ClassExtA.html\" title=\"annotation "
                + "in typeannos\">@ClassExtA</a> <a href=\"ParameterizedClass.html\" "
                + "title=\"class in typeannos\">ParameterizedClass</a>&lt;<a href=\""
                + "ClassExtB.html\" title=\"annotation in typeannos\">"
                + "@ClassExtB</a> java.lang.String&gt;",

                "implements <a href=\"ClassExtB.html\" title=\""
                + "annotation in typeannos\">@ClassExtB</a> java.lang.CharSequence, "
                + "<a href=\"ClassExtA.html\" title=\"annotation in "
                + "typeannos\">@ClassExtA</a> <a href=\"ParameterizedInterface.html\" "
                + "title=\"interface in typeannos\">ParameterizedInterface</a>&lt;"
                + "<a href=\"ClassExtB.html\" title=\"annotation in "
                + "typeannos\">@ClassExtB</a> java.lang.String&gt;</pre>");

        checkOutput("typeannos/MyInterface.html", true,
                "extends <a href=\"ClassExtA.html\" title=\"annotation "
                + "in typeannos\">@ClassExtA</a> <a href=\""
                + "ParameterizedInterface.html\" title=\"interface in typeannos\">"
                + "ParameterizedInterface</a>&lt;<a href=\"ClassExtA.html\" "
                + "title=\"annotation in typeannos\">@ClassExtA</a> java.lang.String&gt;, "
                + "<a href=\"ClassExtB.html\" title=\"annotation in "
                + "typeannos\">@ClassExtB</a> java.lang.CharSequence</pre>");

        // Test for type annotations on Class Parameters (ClassParameters.java).
        checkOutput("typeannos/ExtendsBound.html", true,
                "class <span class=\"type-name-label\">ExtendsBound&lt;K extends <a "
                + "href=\"ClassParamA.html\" title=\"annotation in "
                + "typeannos\">@ClassParamA</a> java.lang.String&gt;</span>");

        checkOutput("typeannos/ExtendsGeneric.html", true,
                "<pre>class <span class=\"type-name-label\">ExtendsGeneric&lt;K extends "
                + "<a href=\"ClassParamA.html\" title=\"annotation in "
                + "typeannos\">@ClassParamA</a> <a href=\"Unannotated.html\" "
                + "title=\"class in typeannos\">Unannotated</a>&lt;<a href=\""
                + "ClassParamB.html\" title=\"annotation in typeannos\">"
                + "@ClassParamB</a> java.lang.String&gt;&gt;</span>");

        checkOutput("typeannos/TwoBounds.html", true,
                "<pre>class <span class=\"type-name-label\">TwoBounds&lt;K extends <a href=\""
                + "ClassParamA.html\" title=\"annotation in typeannos\">"
                + "@ClassParamA</a> java.lang.String,&#8203;V extends <a href=\""
                + "ClassParamB.html\" title=\"annotation in typeannos\">@ClassParamB"
                + "</a> java.lang.String&gt;</span>");

        checkOutput("typeannos/Complex1.html", true,
                "class <span class=\"type-name-label\">Complex1&lt;K extends <a href=\""
                + "ClassParamA.html\" title=\"annotation in typeannos\">"
                + "@ClassParamA</a> java.lang.String &amp; java.lang.Runnable&gt;</span>");

        checkOutput("typeannos/Complex2.html", true,
                "class <span class=\"type-name-label\">Complex2&lt;K extends java.lang."
                + "String &amp; <a href=\"ClassParamB.html\" title=\""
                + "annotation in typeannos\">@ClassParamB</a> java.lang.Runnable&gt;</span>");

        checkOutput("typeannos/ComplexBoth.html", true,
                "class <span class=\"type-name-label\">ComplexBoth&lt;K extends <a href=\""
                + "ClassParamA.html\" title=\"annotation in typeannos\""
                + ">@ClassParamA</a> java.lang.String &amp; <a href=\""
                + "ClassParamA.html\" title=\"annotation in typeannos\">@ClassParamA"
                + "</a> java.lang.Runnable&gt;</span>");

        // Test for type annotations on fields (Fields.java).
        checkOutput("typeannos/DefaultScope.html", true,
                "<div class=\"member-signature\"><span class=\"return-type\"><a href=\"Parameterized.html\" "
                + "title=\"class in typeannos\">Parameterized</a>&lt;<a href=\"FldA.html\" "
                + "title=\"annotation in typeannos\">@FldA</a> java.lang.String,&#8203;"
                + "<a href=\"FldB.html\" title=\"annotation in typeannos\">@FldB</a> java.lang.String&gt;"
                + "</span>&nbsp;<span class=\"member-name\">bothTypeArgs</span></div>",

                "<div class=\"member-signature\"><span class=\"return-type\"><a href=\"FldA.html\" "
                + "title=\"annotation in typeannos\">@FldA</a> java.lang.String <a href=\"FldB.html\" "
                + "title=\"annotation in typeannos\">@FldB</a> []</span>&nbsp;"
                + "<span class=\"member-name\">array1Deep</span></div>",

                "<div class=\"member-signature\"><span class=\"return-type\">java.lang.String "
                + "<a href=\"FldB.html\" title=\"annotation in typeannos\">@FldB</a> [][]</span>&nbsp;"
                + "<span class=\"member-name\">array2SecondOld</span></div>",

                // When JDK-8068737, we should change the order
                "<div class=\"member-signature\"><span class=\"return-type\"><a href=\"FldD.html\" "
                + "title=\"annotation in typeannos\">@FldD</a> java.lang.String <a href=\"FldC.html\" "
                + "title=\"annotation in typeannos\">@FldC</a> <a href=\"FldB.html\" "
                + "title=\"annotation in typeannos\">@FldB</a> [] <a href=\"FldC.html\" "
                + "title=\"annotation in typeannos\">@FldC</a> <a href=\"FldA.html\" "
                + "title=\"annotation in typeannos\">@FldA</a> []</span>&nbsp;"
                + "<span class=\"member-name\">array2Deep</span></div>");

        checkOutput("typeannos/ModifiedScoped.html", true,
                "<div class=\"member-signature\"><span class=\"modifiers\">public final</span>"
                + "&nbsp;<span class=\"return-type\"><a href=\"Parameterized.html\" "
                + "title=\"class in typeannos\">Parameterized</a>&lt;<a href=\"FldA.html\" "
                + "title=\"annotation in typeannos\">@FldA</a> <a href=\"Parameterized.html\" "
                + "title=\"class in typeannos\">Parameterized</a>&lt;<a href=\"FldA.html\" "
                + "title=\"annotation in typeannos\">@FldA</a> java.lang.String,&#8203;"
                + "<a href=\"FldB.html\" title=\"annotation in typeannos\">@FldB</a> "
                + "java.lang.String&gt;,&#8203;<a href=\"FldB.html\" "
                + "title=\"annotation in typeannos\">@FldB</a> java.lang.String&gt;"
                + "</span>&nbsp;<span class=\"member-name\">nestedParameterized</span></div>",

                "<div class=\"member-signature\"><span class=\"modifiers\">public final</span>&nbsp;"
                + "<span class=\"return-type\"><a href=\"FldA.html\" title=\"annotation in typeannos\">"
                + "@FldA</a> java.lang.String[][]</span>&nbsp;"
                + "<span class=\"member-name\">array2</span></div>");

        // Test for type annotations on method return types (MethodReturnType.java).
        checkOutput("typeannos/MtdDefaultScope.html", true,
                "<div class=\"member-signature\"><span class=\"modifiers\">public</span>"
                + "&nbsp;<span class=\"type-parameters\">&lt;T&gt;</span>&nbsp;<span "
                + "class=\"return-type\"><a href=\"MRtnA.html\" title=\"annotation in typeannos\">"
                + "@MRtnA</a> java.lang.String</span>&nbsp;"
                + "<span class=\"member-name\">method</span>()</div>",

                // When JDK-8068737 is fixed, we should change the order
                "<div class=\"member-signature\"><span class=\"return-type\"><a href=\"MRtnA.html\" "
                + "title=\"annotation in typeannos\">@MRtnA</a> java.lang.String <a href=\"MRtnB.html\" "
                + "title=\"annotation in typeannos\">@MRtnB</a> [] <a href=\"MRtnA.html\" "
                + "title=\"annotation in typeannos\">@MRtnA</a> []</span>&nbsp;<span class=\"member-name\">"
                + "array2Deep</span>()</div>",

                "<div class=\"member-signature\"><span class=\"return-type\"><a href=\"MRtnA.html\" "
                + "title=\"annotation in typeannos\">@MRtnA</a> java.lang.String[][]</span>&nbsp;"
                + "<span class=\"member-name\">array2</span>()</div>");

        checkOutput("typeannos/MtdModifiedScoped.html", true,
                "<div class=\"member-signature\"><span class=\"modifiers\">public final</span>&nbsp;"
                + "<span class=\"return-type\"><a href=\"MtdParameterized.html\" "
                + "title=\"class in typeannos\">MtdParameterized</a>&lt;<a href=\"MRtnA.html\" "
                + "title=\"annotation in typeannos\">@MRtnA</a> <a href=\"MtdParameterized.html\" "
                + "title=\"class in typeannos\">MtdParameterized</a>&lt;<a href=\"MRtnA.html\" "
                + "title=\"annotation in typeannos\">@MRtnA</a> java.lang.String,&#8203;"
                + "<a href=\"MRtnB.html\" title=\"annotation in typeannos\">@MRtnB</a> "
                + "java.lang.String&gt;,&#8203;<a href=\"MRtnB.html\" title=\"annotation in typeannos\">"
                + "@MRtnB</a> java.lang.String&gt;</span>&nbsp;<span class=\"member-name\">"
                + "nestedMtdParameterized</span>()</div>");

        // Test for type annotations on method type parameters (MethodTypeParameters.java).
        checkOutput("typeannos/UnscopedUnmodified.html", true,
                "<div class=\"member-signature\"><span class=\"type-parameters\">&lt;K extends "
                + "<a href=\"MTyParamA.html\" title=\"annotation in typeannos\">@MTyParamA</a> "
                + "java.lang.String&gt;</span>&nbsp;<span class=\"return-type\">void</span>&nbsp;"
                + "<span class=\"member-name\">methodExtends</span>()</div>",

                "<div class=\"member-signature\"><span class=\"type-parameters-long\">&lt;K extends "
                + "<a href=\"MTyParamA.html\" title=\"annotation in typeannos\">@MTyParamA</a> "
                + "<a href=\"MtdTyParameterized.html\" title=\"class in typeannos\">MtdTyParameterized</a>"
                + "&lt;<a href=\"MTyParamB.html\" title=\"annotation in typeannos\">@MTyParamB</a> "
                + "java.lang.String&gt;&gt;</span>\n<span class=\"return-type\">void</span>"
                + "&nbsp;<span class=\"member-name\">nestedExtends</span>()</div>");

        checkOutput("typeannos/PublicModifiedMethods.html", true,
                "<div class=\"member-signature\"><span class=\"modifiers\">public final</span>&nbsp;"
                + "<span class=\"type-parameters\">&lt;K extends <a href=\"MTyParamA.html\" "
                + "title=\"annotation in typeannos\">@MTyParamA</a> java.lang.String&gt;</span>\n"
                + "<span class=\"return-type\">void</span>&nbsp;"
                + "<span class=\"member-name\">methodExtends</span>()</div>",

                "<div class=\"member-signature\"><span class=\"modifiers\">public final</span>"
                + "&nbsp;<span class=\"type-parameters-long\">&lt;K extends <a href=\"MTyParamA.html\" "
                + "title=\"annotation in typeannos\">@MTyParamA</a> java.lang.String,&#8203;\n"
                + "V extends <a href=\"MTyParamA.html\" title=\"annotation in typeannos\">"
                + "@MTyParamA</a> <a href=\"MtdTyParameterized.html\" title=\"class in typeannos\">"
                + "MtdTyParameterized</a>&lt;<a href=\"MTyParamB.html\" title=\"annotation in typeannos\">"
                + "@MTyParamB</a> java.lang.String&gt;&gt;</span>\n<span class=\"return-type\">void</span>"
                + "&nbsp;<span class=\"member-name\">dual</span>()</div>");

        // Test for type annotations on parameters (Parameters.java).
        checkOutput("typeannos/Parameters.html", true,
                "<div class=\"member-signature\"><span class=\"return-type\">void</span>&nbsp;"
                + "<span class=\"member-name\">unannotated</span>&#8203;(<span class=\"arguments\">"
                + "<a href=\"ParaParameterized.html\" title=\"class in typeannos\">ParaParameterized</a>"
                + "&lt;java.lang.String,&#8203;java.lang.String&gt;&nbsp;a)</span></div>",

                "<div class=\"member-signature\"><span class=\"return-type\">void</span>&nbsp;"
                + "<span class=\"member-name\">nestedParaParameterized</span>&#8203;"
                + "(<span class=\"arguments\"><a href=\"ParaParameterized.html\" "
                + "title=\"class in typeannos\">ParaParameterized</a>&lt;<a href=\"ParamA.html\" "
                + "title=\"annotation in typeannos\">@ParamA</a> <a href=\"ParaParameterized.html\" "
                + "title=\"class in typeannos\">ParaParameterized</a>&lt;<a href=\"ParamA.html\" "
                + "title=\"annotation in typeannos\">@ParamA</a> java.lang.String,&#8203;"
                + "<a href=\"ParamB.html\" title=\"annotation in typeannos\">@ParamB</a> "
                + "java.lang.String&gt;,&#8203;<a href=\"ParamB.html\" title=\"annotation in typeannos\">"
                + "@ParamB</a> java.lang.String&gt;&nbsp;a)</span></div>",

                // When JDK-8068737 is fixed, we should change the order
                "<div class=\"member-signature\"><span class=\"return-type\">void</span>&nbsp;"
                + "<span class=\"member-name\">array2Deep</span>&#8203;(<span class=\"arguments\">"
                + "<a href=\"ParamA.html\" title=\"annotation in typeannos\">@ParamA</a> "
                + "java.lang.String <a href=\"ParamB.html\" title=\"annotation in typeannos\">"
                + "@ParamB</a> [] <a href=\"ParamA.html\" title=\"annotation in typeannos\">"
                + "@ParamA</a> []&nbsp;a)</span></div>");

        // Test for type annotations on throws (Throws.java).
        checkOutput("typeannos/ThrDefaultUnmodified.html", true,
                "<div class=\"member-signature\"><span class=\"return-type\">void</span>&nbsp;"
                + "<span class=\"member-name\">oneException</span>()\n"
                + "           throws <span class=\"exceptions\"><a href=\"ThrA.html\" "
                + "title=\"annotation in typeannos\">@ThrA</a> java.lang.Exception</span></div>",

                "<div class=\"member-signature\"><span class=\"return-type\">void</span>&nbsp;"
                + "<span class=\"member-name\">twoExceptions</span>()\n"
                + "            throws <span class=\"exceptions\"><a href=\"ThrA.html\" "
                + "title=\"annotation in typeannos\">@ThrA</a> java.lang.RuntimeException,\n"
                + "<a href=\"ThrA.html\" title=\"annotation in typeannos\">@ThrA</a> "
                + "java.lang.Exception</span></div>");

        checkOutput("typeannos/ThrPublicModified.html", true,
                "<div class=\"member-signature\"><span class=\"modifiers\">public final</span>&nbsp;"
                + "<span class=\"return-type\">void</span>&nbsp;<span class=\"member-name\">"
                + "oneException</span>&#8203;(<span class=\"arguments\">java.lang.String&nbsp;a)</span>\n"
                + "                        throws <span class=\"exceptions\"><a href=\"ThrA.html\" "
                + "title=\"annotation in typeannos\">@ThrA</a> java.lang.Exception</span></div>",

                "<div class=\"member-signature\"><span class=\"modifiers\">public final</span>&nbsp;"
                + "<span class=\"return-type\">void</span>&nbsp;<span class=\"member-name\">"
                + "twoExceptions</span>&#8203;(<span class=\"arguments\">java.lang.String&nbsp;a)</span>\n"
                + "                         throws <span class=\"exceptions\"><a href=\"ThrA.html\" "
                + "title=\"annotation in typeannos\">@ThrA</a> java.lang.RuntimeException,\n"
                + "<a href=\"ThrA.html\" title=\"annotation in typeannos\">@ThrA</a> "
                + "java.lang.Exception</span></div>");

        checkOutput("typeannos/ThrWithValue.html", true,
                "<div class=\"member-signature\"><span class=\"return-type\">void</span>&nbsp;"
                + "<span class=\"member-name\">oneException</span>()\n"
                + "           throws <span class=\"exceptions\"><a href=\"ThrB.html\" "
                + "title=\"annotation in typeannos\">@ThrB</a>(\"m\") java.lang.Exception</span></div>",

                "<div class=\"member-signature\"><span class=\"return-type\">void</span>&nbsp;"
                + "<span class=\"member-name\">twoExceptions</span>()\n"
                + "            throws <span class=\"exceptions\"><a href=\"ThrB.html\" "
                + "title=\"annotation in typeannos\">@ThrB</a>(\"m\") java.lang.RuntimeException,\n"
                + "<a href=\"ThrA.html\" title=\"annotation in typeannos\">@ThrA</a> "
                + "java.lang.Exception</span></div>");

        // Test for type annotations on type parameters (TypeParameters.java).
        checkOutput("typeannos/TestMethods.html", true,
                "<div class=\"member-signature\"><span class=\"type-parameters\">&lt;K,&#8203;\n"
                + "<a href=\"TyParaA.html\" title=\"annotation in typeannos\">@TyParaA</a> V extends "
                + "<a href=\"TyParaA.html\" title=\"annotation in typeannos\">@TyParaA</a> "
                + "java.lang.String&gt;</span>\n<span class=\"return-type\">void</span>&nbsp;"
                + "<span class=\"member-name\">secondAnnotated</span>()</div>"
        );

        // Test for type annotations on wildcard type (Wildcards.java).
        checkOutput("typeannos/BoundTest.html", true,
                "<div class=\"member-signature\"><span class=\"return-type\">void</span>&nbsp;"
                + "<span class=\"member-name\">wcExtends</span>&#8203;(<span class=\"arguments\">"
                + "<a href=\"MyList.html\" title=\"class in typeannos\">MyList</a>&lt;? extends "
                + "<a href=\"WldA.html\" title=\"annotation in typeannos\">@WldA</a> "
                + "java.lang.String&gt;&nbsp;l)</span></div>",

                "<div class=\"member-signature\"><span class=\"return-type\"><a href=\"MyList.html\" "
                + "title=\"class in typeannos\">MyList</a>&lt;? super <a href=\"WldA.html\" "
                + "title=\"annotation in typeannos\">@WldA</a> java.lang.String&gt;</span>&nbsp;"
                + "<span class=\"member-name\">returnWcSuper</span>()</div>");

        checkOutput("typeannos/BoundWithValue.html", true,
                "<div class=\"member-signature\"><span class=\"return-type\">void</span>&nbsp;"
                + "<span class=\"member-name\">wcSuper</span>&#8203;(<span class=\"arguments\">"
                + "<a href=\"MyList.html\" title=\"class in typeannos\">MyList</a>&lt;? super "
                + "<a href=\"WldB.html\" title=\"annotation in typeannos\">@WldB</a>(\"m\") "
                + "java.lang.String&gt;&nbsp;l)</span></div>",

                "<div class=\"member-signature\"><span class=\"return-type\"><a href=\"MyList.html\" "
                + "title=\"class in typeannos\">MyList</a>&lt;? extends <a href=\"WldB.html\" "
                + "title=\"annotation in typeannos\">@WldB</a>(\"m\") java.lang.String&gt;</span>"
                + "&nbsp;<span class=\"member-name\">returnWcExtends</span>()</div>");

        // Test for receiver annotations (Receivers.java).
        checkOutput("typeannos/DefaultUnmodified.html", true,
                "<div class=\"member-signature\"><span class=\"return-type\">void</span>&nbsp;"
                + "<span class=\"member-name\">withException</span>&#8203;(<span class=\"arguments\">"
                + "<a href=\"RcvrA.html\" title=\"annotation in typeannos\">@RcvrA</a>"
                + "&nbsp;DefaultUnmodified&nbsp;this)</span>\n"
                + "            throws <span class=\"exceptions\">java.lang.Exception</span></div>",

                "<div class=\"member-signature\"><span class=\"return-type\">java.lang.String</span>&nbsp;"
                + "<span class=\"member-name\">nonVoid</span>&#8203;(<span class=\"arguments\">"
                + "<a href=\"RcvrA.html\" title=\"annotation in typeannos\">@RcvrA</a> "
                + "<a href=\"RcvrB.html\" title=\"annotation in typeannos\">@RcvrB</a>(\"m\")"
                + "&nbsp;DefaultUnmodified&nbsp;this)</span></div>",

                "<div class=\"member-signature\"><span class=\"type-parameters\">&lt;T extends "
                + "java.lang.Runnable&gt;</span>&nbsp;<span class=\"return-type\">void</span>&nbsp;"
                + "<span class=\"member-name\">accept</span>&#8203;(<span class=\"arguments\">"
                + "<a href=\"RcvrA.html\" title=\"annotation in typeannos\">@RcvrA</a>&nbsp;"
                + "DefaultUnmodified&nbsp;this,\nT&nbsp;r)</span>\n"
                + "                                    throws <span class=\"exceptions\">"
                + "java.lang.Exception</span></div>");

        checkOutput("typeannos/PublicModified.html", true,
                "<div class=\"member-signature\"><span class=\"modifiers\">public final</span>&nbsp;"
                + "<span class=\"return-type\">java.lang.String</span>&nbsp;<span class=\"member-name\">"
                + "nonVoid</span>&#8203;(<span class=\"arguments\"><a href=\"RcvrA.html\" "
                + "title=\"annotation in typeannos\">@RcvrA</a>&nbsp;PublicModified&nbsp;this)"
                + "</span></div>",

                "<div class=\"member-signature\"><span class=\"modifiers\">public final</span>&nbsp;"
                + "<span class=\"type-parameters\">&lt;T extends java.lang.Runnable&gt;</span>&nbsp;"
                + "<span class=\"return-type\">void</span>&nbsp;<span class=\"member-name\">accept"
                + "</span>&#8203;(<span class=\"arguments\"><a href=\"RcvrA.html\" "
                + "title=\"annotation in typeannos\">@RcvrA</a>&nbsp;PublicModified&nbsp;this,\n"
                + "T&nbsp;r)</span>\n                                                 throws "
                + "<span class=\"exceptions\">java.lang.Exception</span></div>");

        checkOutput("typeannos/WithValue.html", true,
                "<div class=\"member-signature\"><span class=\"type-parameters\">&lt;T extends "
                + "java.lang.Runnable&gt;</span>&nbsp;<span class=\"return-type\">void</span>&nbsp;"
                + "<span class=\"member-name\">accept</span>&#8203;(<span class=\"arguments\">"
                + "<a href=\"RcvrB.html\" title=\"annotation in typeannos\">@RcvrB</a>(\"m\")"
                + "&nbsp;WithValue&nbsp;this,\nT&nbsp;r)</span>\n"
                + "                                    throws <span class=\"exceptions\">"
                + "java.lang.Exception</span></div>");

        checkOutput("typeannos/WithFinal.html", true,
                "<div class=\"member-signature\"><span class=\"return-type\">java.lang.String</span>"
                + "&nbsp;<span class=\"member-name\">nonVoid</span>&#8203;(<span class=\"arguments\">"
                + "<a href=\"RcvrB.html\" title=\"annotation in typeannos\">@RcvrB</a>(\"m\") "
                + "<a href=\"WithFinal.html\" title=\"class in typeannos\">WithFinal</a>"
                + "&nbsp;afield)</span></div>");

        checkOutput("typeannos/WithBody.html", true,
                "<div class=\"member-signature\"><span class=\"return-type\">void</span>&nbsp;"
                + "<span class=\"member-name\">field</span>&#8203;(<span class=\"arguments\">"
                + "<a href=\"RcvrA.html\" title=\"annotation in typeannos\">@RcvrA</a>"
                + "&nbsp;WithBody&nbsp;this)</span></div>");

        checkOutput("typeannos/Generic2.html", true,
                "<div class=\"member-signature\"><span class=\"return-type\">void</span>&nbsp;"
                + "<span class=\"member-name\">test2</span>&#8203;(<span class=\"arguments\">"
                + "<a href=\"RcvrA.html\" title=\"annotation in typeannos\">@RcvrA</a>"
                + "&nbsp;Generic2&lt;X&gt;&nbsp;this)</span></div>");


        // Test for repeated type annotations (RepeatedAnnotations.java).
        checkOutput("typeannos/RepeatingAtClassLevel.html", true,
                "<pre><a href=\"RepTypeA.html\" title=\"annotation in "
                + "typeannos\">@RepTypeA</a> <a href=\"RepTypeA.html\" "
                + "title=\"annotation in typeannos\">@RepTypeA</a>\n<a href="
                + "\"RepTypeB.html\" title=\"annotation in typeannos\">"
                + "@RepTypeB</a> <a href=\"RepTypeB.html\" title="
                + "\"annotation in typeannos\">@RepTypeB</a>\nclass <span class="
                + "\"type-name-label\">RepeatingAtClassLevel</span>\nextends "
                + "java.lang.Object</pre>");

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
                "<div class=\"member-signature\"><span class=\"annotations\">"
                + "<a href=\"RepConstructorA.html\" title=\"annotation in typeannos\">"
                + "@RepConstructorA</a> <a href=\"RepConstructorA.html\" "
                + "title=\"annotation in typeannos\">@RepConstructorA</a>\n"
                + "<a href=\"RepConstructorB.html\" title=\"annotation in typeannos\">"
                + "@RepConstructorB</a> <a href=\"RepConstructorB.html\" "
                + "title=\"annotation in typeannos\">@RepConstructorB</a>\n"
                + "</span><span class=\"member-name\">RepeatingOnConstructor</span>()</div>",

                "<div class=\"member-signature\"><span class=\"annotations\">"
                + "<a href=\"RepConstructorA.html\" title=\"annotation in typeannos\">"
                + "@RepConstructorA</a> <a href=\"RepConstructorA.html\" "
                + "title=\"annotation in typeannos\">@RepConstructorA</a>\n"
                + "<a href=\"RepConstructorB.html\" title=\"annotation in typeannos\">"
                + "@RepConstructorB</a> <a href=\"RepConstructorB.html\" "
                + "title=\"annotation in typeannos\">@RepConstructorB</a>\n"
                + "</span><span class=\"member-name\">RepeatingOnConstructor</span>"
                + "&#8203;(<span class=\"arguments\">int&nbsp;i,\n"
                + "int&nbsp;j)</span></div>",

                "<div class=\"member-signature\"><span class=\"annotations\">"
                + "<a href=\"RepAllContextsA.html\" title=\"annotation in typeannos\">"
                + "@RepAllContextsA</a> <a href=\"RepAllContextsA.html\" "
                + "title=\"annotation in typeannos\">@RepAllContextsA</a>\n"
                + "<a href=\"RepAllContextsB.html\" title=\"annotation in typeannos\">"
                + "@RepAllContextsB</a> <a href=\"RepAllContextsB.html\" "
                + "title=\"annotation in typeannos\">@RepAllContextsB</a>\n"
                + "</span><span class=\"member-name\">RepeatingOnConstructor</span>"
                + "&#8203;(<span class=\"arguments\">int&nbsp;i,\n"
                + "int&nbsp;j,\nint&nbsp;k)</span></div>",

                "<div class=\"member-signature\"><span class=\"member-name\">RepeatingOnConstructor</span>"
                + "&#8203;(<span class=\"arguments\"><a href=\"RepParameterA.html\" "
                + "title=\"annotation in typeannos\">@RepParameterA</a> "
                + "<a href=\"RepParameterA.html\" title=\"annotation in typeannos\">@RepParameterA</a> "
                + "<a href=\"RepParameterB.html\" title=\"annotation in typeannos\">@RepParameterB</a> "
                + "<a href=\"RepParameterB.html\" title=\"annotation in typeannos\">@RepParameterB</a>\n"
                + "java.lang.String&nbsp;parameter,\n<a href=\"RepParameterA.html\" "
                + "title=\"annotation in typeannos\">@RepParameterA</a> "
                + "<a href=\"RepParameterA.html\" title=\"annotation in typeannos\">@RepParameterA</a> "
                + "<a href=\"RepParameterB.html\" title=\"annotation in typeannos\">@RepParameterB</a> "
                + "<a href=\"RepParameterB.html\" title=\"annotation in typeannos\">@RepParameterB</a>\n"
                + "java.lang.String <a href=\"RepTypeUseA.html\" title=\"annotation in typeannos\">"
                + "@RepTypeUseA</a> <a href=\"RepTypeUseA.html\" title=\"annotation in typeannos\">"
                + "@RepTypeUseA</a> <a href=\"RepTypeUseB.html\" title=\"annotation in typeannos\">"
                + "@RepTypeUseB</a> <a href=\"RepTypeUseB.html\" title=\"annotation in typeannos\">"
                + "@RepTypeUseB</a> ...&nbsp;vararg)</span></div>"
        );

        checkOutput("typeannos/RepeatingOnConstructor.Inner.html", true,
                "<code><span class=\"member-name-link\"><a href=\"#%3Cinit%3E(java.lang.String,"
                + "java.lang.String...)\">Inner</a></span>&#8203;(java.lang.String&nbsp;parameter,\n"
                + "java.lang.String <a href=\"RepTypeUseA.html\" title=\"annotation in typeannos\">"
                + "@RepTypeUseA</a> <a href=\"RepTypeUseA.html\" title=\"annotation in typeannos\">"
                + "@RepTypeUseA</a> <a href=\"RepTypeUseB.html\" title=\"annotation in typeannos\">"
                + "@RepTypeUseB</a> <a href=\"RepTypeUseB.html\" title=\"annotation in typeannos\">"
                + "@RepTypeUseB</a> ...&nbsp;vararg)</code>",
                "Inner</span>&#8203;(<span class=\"arguments\"><a href=\"RepTypeUseA.html\" "
                + "title=\"annotation in typeannos\">@RepTypeUseA</a> <a href=\"RepTypeUseA.html\" "
                + "title=\"annotation in typeannos\">@RepTypeUseA</a> <a href=\"RepTypeUseB.html\" "
                + "title=\"annotation in typeannos\">@RepTypeUseB</a> <a href=\"RepTypeUseB.html\" "
                + "title=\"annotation in typeannos\">@RepTypeUseB</a>&nbsp;RepeatingOnConstructor&nbsp;this,\n"
                + "<a href=\"RepParameterA.html\" title=\"annotation in typeannos\">@RepParameterA</a> "
                + "<a href=\"RepParameterA.html\" title=\"annotation in typeannos\">@RepParameterA</a> "
                + "<a href=\"RepParameterB.html\" title=\"annotation in typeannos\">@RepParameterB</a> "
                + "<a href=\"RepParameterB.html\" title=\"annotation in typeannos\">@RepParameterB</a>\n"
                + "java.lang.String&nbsp;parameter,\n<a href=\"RepParameterA.html\" "
                + "title=\"annotation in typeannos\">@RepParameterA</a> "
                + "<a href=\"RepParameterA.html\" title=\"annotation in typeannos\">@RepParameterA</a> "
                + "<a href=\"RepParameterB.html\" title=\"annotation in typeannos\">@RepParameterB</a> "
                + "<a href=\"RepParameterB.html\" title=\"annotation in typeannos\">@RepParameterB</a>\n"
                + "java.lang.String <a href=\"RepTypeUseA.html\" title=\"annotation in typeannos\">"
                + "@RepTypeUseA</a> <a href=\"RepTypeUseA.html\" title=\"annotation in typeannos\">"
                + "@RepTypeUseA</a> <a href=\"RepTypeUseB.html\" title=\"annotation in typeannos\">"
                + "@RepTypeUseB</a> <a href=\"RepTypeUseB.html\" title=\"annotation in typeannos\">"
                + "@RepTypeUseB</a> ...&nbsp;vararg)</span>");

        checkOutput("typeannos/RepeatingOnField.html", true,
                "<code>(package private) java.lang.Integer</code></td>\n<th class=\"col-second\" scope=\"row\">"
                + "<code><span class=\"member-name-link\"><a href=\"#i1"
                + "\">i1</a></span></code>",

                "<code>(package private) <a href=\"RepTypeUseA.html\" "
                + "title=\"annotation in typeannos\">@RepTypeUseA</a> <a href=\""
                + "RepTypeUseA.html\" title=\"annotation in typeannos\">"
                + "@RepTypeUseA</a> <a href=\"RepTypeUseB.html\" title="
                + "\"annotation in typeannos\">@RepTypeUseB</a> <a href=\"RepTypeUseB.html"
                + "\" title=\"annotation in typeannos\">@RepTypeUseB</a> java.lang.Integer</code></td>\n"
                + "<th class=\"col-second\" scope=\"row\"><code><span class=\"member-name-link\"><a href="
                + "\"#i2\">i2</a></span></code>",

                "<code>(package private) <a href=\"RepTypeUseA.html\" title="
                + "\"annotation in typeannos\">@RepTypeUseA</a> <a href=\"RepTypeUseA.html\" "
                + "title=\"annotation in typeannos\">@RepTypeUseA</a> <a href="
                + "\"RepTypeUseB.html\" title=\"annotation in typeannos\">"
                + "@RepTypeUseB</a> <a href=\"RepTypeUseB.html\" title="
                + "\"annotation in typeannos\">@RepTypeUseB</a> java.lang.Integer</code>"
                + "</td>\n<th class=\"col-second\" scope=\"row\"><code><span class=\"member-name-link\">"
                + "<a href=\"#i3\">i3</a></span></code>",

                "<code>(package private) <a href=\"RepAllContextsA.html\" title=\""
                + "annotation in typeannos\">@RepAllContextsA</a> <a href=\"RepAllContextsA.html"
                + "\" title=\"annotation in typeannos\">@RepAllContextsA</a> <a href="
                + "\"RepAllContextsB.html\" title=\"annotation in typeannos\">"
                + "@RepAllContextsB</a> <a href=\"RepAllContextsB.html\" title="
                + "\"annotation in typeannos\">@RepAllContextsB</a> java.lang.Integer</code>"
                + "</td>\n<th class=\"col-second\" scope=\"row\"><code><span class=\"member-name-link\">"
                + "<a href=\"#i4\">i4</a></span></code>",

                "<code>(package private) java.lang.String <a href=\"RepTypeUseA.html"
                + "\" title=\"annotation in typeannos\">@RepTypeUseA</a> <a href="
                + "\"RepTypeUseA.html\" title=\"annotation in typeannos\">"
                + "@RepTypeUseA</a> <a href=\"RepTypeUseB.html\" title="
                + "\"annotation in typeannos\">@RepTypeUseB</a> <a href=\"RepTypeUseB.html"
                + "\" title=\"annotation in typeannos\">@RepTypeUseB</a> [] <a href="
                + "\"RepTypeUseA.html\" title=\"annotation in typeannos\">"
                + "@RepTypeUseA</a> <a href=\"RepTypeUseA.html\" title="
                + "\"annotation in typeannos\">@RepTypeUseA</a> <a href=\"RepTypeUseB.html"
                + "\" title=\"annotation in typeannos\">@RepTypeUseB</a> <a href="
                + "\"RepTypeUseB.html\" title=\"annotation in typeannos\">"
                + "@RepTypeUseB</a> []</code></td>\n<th class=\"col-second\" scope=\"row\"><code><span class="
                + "\"member-name-link\"><a href=\"#sa"
                + "\">sa</a></span></code>",

                "<div class=\"member-signature\"><span class=\"annotations\">"
                + "<a href=\"RepFieldA.html\" title=\"annotation in typeannos\">@RepFieldA</a> "
                + "<a href=\"RepFieldA.html\" title=\"annotation in typeannos\">@RepFieldA</a>\n"
                + "<a href=\"RepFieldB.html\" title=\"annotation in typeannos\">@RepFieldB</a> "
                + "<a href=\"RepFieldB.html\" title=\"annotation in typeannos\">@RepFieldB</a>\n"
                + "</span><span class=\"return-type\">java.lang.Integer</span>&nbsp;"
                + "<span class=\"member-name\">i1</span></div>",

                "<div class=\"member-signature\"><span class=\"return-type\">"
                + "<a href=\"RepTypeUseA.html\" title=\"annotation in typeannos\">@RepTypeUseA</a> "
                + "<a href=\"RepTypeUseA.html\" title=\"annotation in typeannos\">@RepTypeUseA</a> "
                + "<a href=\"RepTypeUseB.html\" title=\"annotation in typeannos\">@RepTypeUseB</a> "
                + "<a href=\"RepTypeUseB.html\" title=\"annotation in typeannos\">@RepTypeUseB</a> "
                + "java.lang.Integer</span>&nbsp;<span class=\"member-name\">i2</span></div>",

                "<div class=\"member-signature\"><span class=\"annotations\">"
                + "<a href=\"RepFieldA.html\" title=\"annotation in typeannos\">@RepFieldA</a> "
                + "<a href=\"RepFieldA.html\" title=\"annotation in typeannos\">@RepFieldA</a>\n"
                + "<a href=\"RepFieldB.html\" title=\"annotation in typeannos\">@RepFieldB</a> "
                + "<a href=\"RepFieldB.html\" title=\"annotation in typeannos\">@RepFieldB</a>\n"
                + "</span><span class=\"return-type\"><a href=\"RepTypeUseA.html\" "
                + "title=\"annotation in typeannos\">@RepTypeUseA</a> "
                + "<a href=\"RepTypeUseA.html\" title=\"annotation in typeannos\">@RepTypeUseA</a> "
                + "<a href=\"RepTypeUseB.html\" title=\"annotation in typeannos\">@RepTypeUseB</a> "
                + "<a href=\"RepTypeUseB.html\" title=\"annotation in typeannos\">@RepTypeUseB</a> "
                + "java.lang.Integer</span>&nbsp;<span class=\"member-name\">i3</span></div>",

                "<div class=\"member-signature\"><span class=\"annotations\">"
                + "<a href=\"RepAllContextsA.html\" title=\"annotation in typeannos\">@RepAllContextsA</a> "
                + "<a href=\"RepAllContextsA.html\" title=\"annotation in typeannos\">@RepAllContextsA</a>\n"
                + "<a href=\"RepAllContextsB.html\" title=\"annotation in typeannos\">@RepAllContextsB</a> "
                + "<a href=\"RepAllContextsB.html\" title=\"annotation in typeannos\">@RepAllContextsB</a>\n"
                + "</span><span class=\"return-type\"><a href=\"RepAllContextsA.html\" "
                + "title=\"annotation in typeannos\">@RepAllContextsA</a> "
                + "<a href=\"RepAllContextsA.html\" title=\"annotation in typeannos\">@RepAllContextsA</a> "
                + "<a href=\"RepAllContextsB.html\" title=\"annotation in typeannos\">@RepAllContextsB</a> "
                + "<a href=\"RepAllContextsB.html\" title=\"annotation in typeannos\">@RepAllContextsB</a> "
                + "java.lang.Integer</span>&nbsp;<span class=\"member-name\">i4</span></div>",

                "<div class=\"member-signature\"><span class=\"return-type\">java.lang.String "
                + "<a href=\"RepTypeUseA.html\" title=\"annotation in typeannos\">@RepTypeUseA</a> "
                + "<a href=\"RepTypeUseA.html\" title=\"annotation in typeannos\">@RepTypeUseA</a> "
                + "<a href=\"RepTypeUseB.html\" title=\"annotation in typeannos\">@RepTypeUseB</a> "
                + "<a href=\"RepTypeUseB.html\" title=\"annotation in typeannos\">@RepTypeUseB</a> [] "
                + "<a href=\"RepTypeUseA.html\" title=\"annotation in typeannos\">@RepTypeUseA</a> "
                + "<a href=\"RepTypeUseA.html\" title=\"annotation in typeannos\">@RepTypeUseA</a> "
                + "<a href=\"RepTypeUseB.html\" title=\"annotation in typeannos\">@RepTypeUseB</a> "
                + "<a href=\"RepTypeUseB.html\" title=\"annotation in typeannos\">@RepTypeUseB</a> []"
                + "</span>&nbsp;<span class=\"member-name\">sa</span></div>");

        checkOutput("typeannos/RepeatingOnMethod.html", true,
                "<code>(package private) java.lang.String</code></td>\n<th class=\"col-second\" scope=\"row\">"
                + "<code><span class=\"member-name-link\"><a href="
                + "\"#test1()\">test1</a></span>()</code>",

                "<code>(package private) <a href=\"RepTypeUseA.html\" "
                + "title=\"annotation in typeannos\">@RepTypeUseA</a> <a href="
                + "\"RepTypeUseA.html\" title=\"annotation in typeannos\">"
                + "@RepTypeUseA</a> <a href=\"RepTypeUseB.html\" title="
                + "\"annotation in typeannos\">@RepTypeUseB</a> <a href=\"RepTypeUseB.html"
                + "\" title=\"annotation in typeannos\">@RepTypeUseB</a> java.lang.String</code>"
                + "</td>\n<th class=\"col-second\" scope=\"row\"><code><span class=\"member-name-link\">"
                + "<a href=\"#test2()\">test2</a>"
                + "</span>()</code>",

                "<code>(package private) <a href=\"RepTypeUseA.html\" "
                + "title=\"annotation in typeannos\">@RepTypeUseA</a> <a href="
                + "\"RepTypeUseA.html\" title=\"annotation in typeannos\">"
                + "@RepTypeUseA</a> <a href=\"RepTypeUseB.html\" title="
                + "\"annotation in typeannos\">@RepTypeUseB</a> <a href=\"RepTypeUseB.html\" "
                + "title=\"annotation in typeannos\">@RepTypeUseB</a> java.lang.String</code>"
                + "</td>\n<th class=\"col-second\" scope=\"row\"><code><span class=\"member-name-link\">"
                + "<a href=\"#test3()\">test3</a>"
                + "</span>()</code>",

                "<code>(package private) <a href=\"RepAllContextsA.html\" "
                + "title=\"annotation in typeannos\">@RepAllContextsA</a> <a href="
                + "\"RepAllContextsA.html\" title=\"annotation in typeannos\">"
                + "@RepAllContextsA</a> <a href=\"RepAllContextsB.html\" "
                + "title=\"annotation in typeannos\">@RepAllContextsB</a> <a href="
                + "\"RepAllContextsB.html\" title=\"annotation in typeannos\">"
                + "@RepAllContextsB</a> java.lang.String</code></td>\n<th class=\"col-second\" scope=\"row\">"
                + "<code><span class=\"member-name-link\"><a href=\""
                + "#test4()\">test4</a></span>()</code>",

                "<code><span class=\"member-name-link\"><a href=\""
                + "#test5(java.lang.String,java.lang.String...)\">test5</a></span>"
                + "&#8203;(java.lang.String&nbsp;parameter,\njava.lang.String <a href="
                + "\"RepTypeUseA.html\" title=\"annotation in typeannos\">"
                + "@RepTypeUseA</a> <a href=\"RepTypeUseA.html\" title="
                + "\"annotation in typeannos\">@RepTypeUseA</a> <a href=\"RepTypeUseB.html"
                + "\" title=\"annotation in typeannos\">@RepTypeUseB</a> <a href="
                + "\"RepTypeUseB.html\" title=\"annotation in typeannos\">"
                + "@RepTypeUseB</a> ...&nbsp;vararg)</code>",

                "<a href=\"RepMethodA.html\" title=\"annotation in typeannos\">@RepMethodA</a> "
                + "<a href=\"RepMethodA.html\" title=\"annotation in typeannos\">@RepMethodA</a>\n"
                + "<a href=\"RepMethodB.html\" title=\"annotation in typeannos\">@RepMethodB</a> "
                + "<a href=\"RepMethodB.html\" title=\"annotation in typeannos\">@RepMethodB</a>\n"
                + "</span><span class=\"return-type\">java.lang.String</span>&nbsp;"
                + "<span class=\"member-name\">test1</span>()",

                "<a href=\"RepTypeUseA.html\" title=\"annotation in typeannos\">"
                + "@RepTypeUseA</a> <a href=\"RepTypeUseA.html\" title="
                + "\"annotation in typeannos\">@RepTypeUseA</a> <a href=\"RepTypeUseB.html\" "
                + "title=\"annotation in typeannos\">@RepTypeUseB</a> <a href=\"RepTypeUseB.html\" "
                + "title=\"annotation in typeannos\">@RepTypeUseB</a> java.lang.String</span>"
                + "&nbsp;<span class=\"member-name\">test2</span>()",

                "<a href=\"RepMethodA.html\" title=\"annotation in typeannos\">@RepMethodA</a> "
                + "<a href=\"RepMethodA.html\" title=\"annotation in typeannos\">@RepMethodA</a>\n"
                + "<a href=\"RepMethodB.html\" title=\"annotation in typeannos\">@RepMethodB</a> "
                + "<a href=\"RepMethodB.html\" title=\"annotation in typeannos\">@RepMethodB</a>\n"
                + "</span><span class=\"return-type\"><a href=\"RepTypeUseA.html\" "
                + "title=\"annotation in typeannos\">@RepTypeUseA</a> <a href=\"RepTypeUseA.html\" "
                + "title=\"annotation in typeannos\">@RepTypeUseA</a> <a href=\"RepTypeUseB.html\" "
                + "title=\"annotation in typeannos\">@RepTypeUseB</a> <a href=\"RepTypeUseB.html\" "
                + "title=\"annotation in typeannos\">@RepTypeUseB</a> java.lang.String</span>&nbsp;"
                + "<span class=\"member-name\">test3</span>()",

                "<a href=\"RepAllContextsA.html\" title=\"annotation in typeannos\">@RepAllContextsA</a> "
                + "<a href=\"RepAllContextsA.html\" title=\"annotation in typeannos\">@RepAllContextsA</a>\n"
                + "<a href=\"RepAllContextsB.html\" title=\"annotation in typeannos\">@RepAllContextsB</a> "
                + "<a href=\"RepAllContextsB.html\" title=\"annotation in typeannos\">@RepAllContextsB</a>\n"
                + "</span><span class=\"return-type\"><a href=\"RepAllContextsA.html\" "
                + "title=\"annotation in typeannos\">@RepAllContextsA</a> <a href=\"RepAllContextsA.html\" "
                + "title=\"annotation in typeannos\">@RepAllContextsA</a> <a href=\"RepAllContextsB.html\" "
                + "title=\"annotation in typeannos\">@RepAllContextsB</a> <a href=\"RepAllContextsB.html\" "
                + "title=\"annotation in typeannos\">@RepAllContextsB</a> java.lang.String</span>&nbsp;"
                + "<span class=\"member-name\">test4</span>()",

                "java.lang.String</span>&nbsp;<span class=\"member-name\">test5</span>&#8203;("
                + "<span class=\"arguments\"><a href=\"RepTypeUseA.html\" "
                + "title=\"annotation in typeannos\">@RepTypeUseA</a> "
                + "<a href=\"RepTypeUseA.html\" title=\"annotation in typeannos\">@RepTypeUseA</a> "
                + "<a href=\"RepTypeUseB.html\" title=\"annotation in typeannos\">@RepTypeUseB</a> "
                + "<a href=\"RepTypeUseB.html\" title=\"annotation in typeannos\">@RepTypeUseB</a>"
                + "&nbsp;RepeatingOnMethod&nbsp;this,\n"
                + "<a href=\"RepParameterA.html\" title=\"annotation in typeannos\">@RepParameterA</a> "
                + "<a href=\"RepParameterA.html\" title=\"annotation in typeannos\">@RepParameterA</a> "
                + "<a href=\"RepParameterB.html\" title=\"annotation in typeannos\">@RepParameterB</a> "
                + "<a href=\"RepParameterB.html\" title=\"annotation in typeannos\">@RepParameterB</a>\n"
                + "java.lang.String&nbsp;parameter,\n"
                + "<a href=\"RepParameterA.html\" title=\"annotation in typeannos\">@RepParameterA</a> "
                + "<a href=\"RepParameterA.html\" title=\"annotation in typeannos\">@RepParameterA</a> "
                + "<a href=\"RepParameterB.html\" title=\"annotation in typeannos\">@RepParameterB</a> "
                + "<a href=\"RepParameterB.html\" title=\"annotation in typeannos\">@RepParameterB</a>\n"
                + "java.lang.String <a href=\"RepTypeUseA.html\" title=\"annotation in typeannos\">"
                + "@RepTypeUseA</a> <a href=\"RepTypeUseA.html\" title=\"annotation in typeannos\">"
                + "@RepTypeUseA</a> <a href=\"RepTypeUseB.html\" title=\"annotation in typeannos\">"
                + "@RepTypeUseB</a> <a href=\"RepTypeUseB.html\" title=\"annotation in typeannos\">"
                + "@RepTypeUseB</a> ...&nbsp;vararg)");

        checkOutput("typeannos/RepeatingOnTypeParametersBoundsTypeArgumentsOnMethod.html", true,
                "<code>(package private) &lt;T&gt;&nbsp;java.lang.String</code></td>\n"
                + "<th class=\"col-second\" scope=\"row\"><code><span class=\"member-name-link\"><a href="
                + "\"#"
                + "genericMethod(T)\">genericMethod</a></span>&#8203;(T&nbsp;t)</code>",

                "<code>(package private) &lt;T&gt;&nbsp;java.lang.String</code></td>\n"
                + "<th class=\"col-second\" scope=\"row\"><code><span class=\"member-name-link\"><a href="
                + "\"#"
                + "genericMethod2(T)\">genericMethod2</a></span>&#8203;(<a href=\"RepTypeUseA.html"
                + "\" title=\"annotation in typeannos\">@RepTypeUseA</a> <a href=\"RepTypeUseA.html"
                + "\" title=\"annotation in typeannos\">@RepTypeUseA</a> <a href=\"RepTypeUseB.html"
                + "\" title=\"annotation in typeannos\">@RepTypeUseB</a> <a href=\"RepTypeUseB.html"
                + "\" title=\"annotation in typeannos\">@RepTypeUseB</a> T&nbsp;t)</code>",

                "<code>(package private) java.lang.String</code></td>\n<th class=\"col-second\" scope=\"row\"><code>"
                + "<span class=\"member-name-link\"><a href=\"#"
                + "test()\">test</a></span>()</code>",

                "<span class=\"return-type\">java.lang.String</span>&nbsp;"
                + "<span class=\"member-name\">test</span>"
                + "&#8203;(<span class=\"arguments\"><a href=\"RepTypeUseA.html\" "
                + "title=\"annotation in typeannos\">@RepTypeUseA</a> <a href="
                + "\"RepTypeUseA.html\" title=\"annotation in typeannos\">"
                + "@RepTypeUseA</a> <a href=\"RepTypeUseB.html\" title="
                + "\"annotation in typeannos\">@RepTypeUseB</a> <a href=\"RepTypeUseB.html\" "
                + "title=\"annotation in typeannos\">@RepTypeUseB</a>&nbsp;"
                + "RepeatingOnTypeParametersBoundsTypeArgumentsOnMethod&lt;<a href="
                + "\"RepTypeUseA.html\" title=\"annotation in typeannos\">"
                + "@RepTypeUseA</a> <a href=\"RepTypeUseA.html\" title="
                + "\"annotation in typeannos\">@RepTypeUseA</a> <a href=\"RepTypeUseB.html"
                + "\" title=\"annotation in typeannos\">@RepTypeUseB</a> <a href=\"RepTypeUseB.html\" "
                + "title=\"annotation in typeannos\">@RepTypeUseB</a> T&gt;&nbsp;this)");

        checkOutput("typeannos/RepeatingOnVoidMethodDeclaration.html", true,
                "<a href=\"RepMethodA.html\" title=\"annotation in typeannos\">@RepMethodA</a> "
                + "<a href=\"RepMethodA.html\" title=\"annotation in typeannos\">@RepMethodA</a>\n"
                + "<a href=\"RepMethodB.html\" title=\"annotation in typeannos\">@RepMethodB</a> "
                + "<a href=\"RepMethodB.html\" title=\"annotation in typeannos\">@RepMethodB</a>\n"
                + "</span><span class=\"return-type\">void</span>&nbsp;"
                + "<span class=\"member-name\">test</span>()");
    }
}
