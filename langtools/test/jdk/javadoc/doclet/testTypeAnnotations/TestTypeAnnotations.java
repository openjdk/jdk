/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8005091 8009686 8025633 8026567
 * @summary  Make sure that type annotations are displayed correctly
 * @author   Bhavesh Patel
 * @library  ../lib
 * @modules jdk.javadoc
 * @build    JavadocTester
 * @run main TestTypeAnnotations
 */

public class TestTypeAnnotations extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestTypeAnnotations tester = new TestTypeAnnotations();
        tester.runTests();
    }

    @Test
    void test() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "-private",
                "typeannos");
        checkExit(Exit.OK);

        // Test for type annotations on Class Extends (ClassExtends.java).
        checkOutput("typeannos/MyClass.html", true,
                "extends <a href=\"../typeannos/ClassExtA.html\" title=\"annotation "
                + "in typeannos\">@ClassExtA</a> <a href=\"../typeannos/ParameterizedClass.html\" "
                + "title=\"class in typeannos\">ParameterizedClass</a>&lt;<a href=\""
                + "../typeannos/ClassExtB.html\" title=\"annotation in typeannos\">"
                + "@ClassExtB</a> java.lang.String&gt;",

                "implements <a href=\"../typeannos/ClassExtB.html\" title=\""
                + "annotation in typeannos\">@ClassExtB</a> java.lang.CharSequence, "
                + "<a href=\"../typeannos/ClassExtA.html\" title=\"annotation in "
                + "typeannos\">@ClassExtA</a> <a href=\"../typeannos/ParameterizedInterface.html\" "
                + "title=\"interface in typeannos\">ParameterizedInterface</a>&lt;"
                + "<a href=\"../typeannos/ClassExtB.html\" title=\"annotation in "
                + "typeannos\">@ClassExtB</a> java.lang.String&gt;</pre>");

        checkOutput("typeannos/MyInterface.html", true,
                "extends <a href=\"../typeannos/ClassExtA.html\" title=\"annotation "
                + "in typeannos\">@ClassExtA</a> <a href=\"../typeannos/"
                + "ParameterizedInterface.html\" title=\"interface in typeannos\">"
                + "ParameterizedInterface</a>&lt;<a href=\"../typeannos/ClassExtA.html\" "
                + "title=\"annotation in typeannos\">@ClassExtA</a> java.lang.String&gt;, "
                + "<a href=\"../typeannos/ClassExtB.html\" title=\"annotation in "
                + "typeannos\">@ClassExtB</a> java.lang.CharSequence</pre>");

        // Test for type annotations on Class Parameters (ClassParameters.java).
        checkOutput("typeannos/ExtendsBound.html", true,
                "class <span class=\"typeNameLabel\">ExtendsBound&lt;K extends <a "
                + "href=\"../typeannos/ClassParamA.html\" title=\"annotation in "
                + "typeannos\">@ClassParamA</a> java.lang.String&gt;</span>");

        checkOutput("typeannos/ExtendsGeneric.html", true,
                "<pre>class <span class=\"typeNameLabel\">ExtendsGeneric&lt;K extends "
                + "<a href=\"../typeannos/ClassParamA.html\" title=\"annotation in "
                + "typeannos\">@ClassParamA</a> <a href=\"../typeannos/Unannotated.html\" "
                + "title=\"class in typeannos\">Unannotated</a>&lt;<a href=\""
                + "../typeannos/ClassParamB.html\" title=\"annotation in typeannos\">"
                + "@ClassParamB</a> java.lang.String&gt;&gt;</span>");

        checkOutput("typeannos/TwoBounds.html", true,
                "<pre>class <span class=\"typeNameLabel\">TwoBounds&lt;K extends <a href=\""
                + "../typeannos/ClassParamA.html\" title=\"annotation in typeannos\">"
                + "@ClassParamA</a> java.lang.String,V extends <a href=\"../typeannos/"
                + "ClassParamB.html\" title=\"annotation in typeannos\">@ClassParamB"
                + "</a> java.lang.String&gt;</span>");

        checkOutput("typeannos/Complex1.html", true,
                "class <span class=\"typeNameLabel\">Complex1&lt;K extends <a href=\"../"
                + "typeannos/ClassParamA.html\" title=\"annotation in typeannos\">"
                + "@ClassParamA</a> java.lang.String &amp; java.lang.Runnable&gt;</span>");

        checkOutput("typeannos/Complex2.html", true,
                "class <span class=\"typeNameLabel\">Complex2&lt;K extends java.lang."
                + "String &amp; <a href=\"../typeannos/ClassParamB.html\" title=\""
                + "annotation in typeannos\">@ClassParamB</a> java.lang.Runnable&gt;</span>");

        checkOutput("typeannos/ComplexBoth.html", true,
                "class <span class=\"typeNameLabel\">ComplexBoth&lt;K extends <a href=\""
                + "../typeannos/ClassParamA.html\" title=\"annotation in typeannos\""
                + ">@ClassParamA</a> java.lang.String &amp; <a href=\"../typeannos/"
                + "ClassParamA.html\" title=\"annotation in typeannos\">@ClassParamA"
                + "</a> java.lang.Runnable&gt;</span>");

        // Test for type annotations on fields (Fields.java).
        checkOutput("typeannos/DefaultScope.html", true,
                "<pre><a href=\"../typeannos/Parameterized.html\" title=\"class in "
                + "typeannos\">Parameterized</a>&lt;<a href=\"../typeannos/FldA.html\" "
                + "title=\"annotation in typeannos\">@FldA</a> java.lang.String,<a "
                + "href=\"../typeannos/FldB.html\" title=\"annotation in typeannos\">"
                + "@FldB</a> java.lang.String&gt; bothTypeArgs</pre>",

                "<pre><a href=\"../typeannos/FldA.html\" title=\"annotation in "
                + "typeannos\">@FldA</a> java.lang.String <a href=\"../typeannos/"
                + "FldB.html\" title=\"annotation in typeannos\">@FldB</a> [] "
                + "array1Deep</pre>",

                "<pre>java.lang.String <a href=\"../typeannos/FldB.html\" "
                + "title=\"annotation in typeannos\">@FldB</a> [][] array2SecondOld</pre>",

                // When JDK-8068737, we should change the order
                "<pre><a href=\"../typeannos/FldD.html\" title=\"annotation in typeannos\">"
                + "@FldD</a> java.lang.String "
                + "<a href=\"../typeannos/FldC.html\" title=\"annotation in typeannos\">@FldC</a> "
                + "<a href=\"../typeannos/FldB.html\" title=\"annotation in typeannos\">@FldB</a> [] "
                + "<a href=\"../typeannos/FldC.html\" title=\"annotation in typeannos\">@FldC</a> "
                + "<a href=\"../typeannos/FldA.html\" title=\"annotation in typeannos\">@FldA</a> [] "
                + "array2Deep</pre>");

        checkOutput("typeannos/ModifiedScoped.html", true,
                "<pre>public final&nbsp;<a href=\"../typeannos/Parameterized.html\" "
                + "title=\"class in typeannos\">Parameterized</a>&lt;<a href=\"../"
                + "typeannos/FldA.html\" title=\"annotation in typeannos\">@FldA</a> "
                + "<a href=\"../typeannos/Parameterized.html\" title=\"class in "
                + "typeannos\">Parameterized</a>&lt;<a href=\"../typeannos/FldA.html\" "
                + "title=\"annotation in typeannos\">@FldA</a> java.lang.String,<a "
                + "href=\"../typeannos/FldB.html\" title=\"annotation in typeannos\">"
                + "@FldB</a> java.lang.String&gt;,<a href=\"../typeannos/FldB.html\" "
                + "title=\"annotation in typeannos\">@FldB</a> java.lang.String&gt; "
                + "nestedParameterized</pre>",

                "<pre>public final&nbsp;<a href=\"../typeannos/FldA.html\" "
                + "title=\"annotation in typeannos\">@FldA</a> java.lang.String[][] "
                + "array2</pre>");

        // Test for type annotations on method return types (MethodReturnType.java).
        checkOutput("typeannos/MtdDefaultScope.html", true,
                "<pre>public&nbsp;&lt;T&gt;&nbsp;<a href=\"../typeannos/MRtnA.html\" "
                + "title=\"annotation in typeannos\">@MRtnA</a> java.lang.String"
                + "&nbsp;method()</pre>",

                // When JDK-8068737 is fixed, we should change the order
                "<pre><a href=\"../typeannos/MRtnA.html\" title=\"annotation in typeannos\">"
                + "@MRtnA</a> java.lang.String "
                + "<a href=\"../typeannos/MRtnB.html\" title=\"annotation in typeannos\">@MRtnB</a> [] "
                + "<a href=\"../typeannos/MRtnA.html\" title=\"annotation in typeannos\">@MRtnA</a> []"
                + "&nbsp;array2Deep()</pre>",

                "<pre><a href=\"../typeannos/MRtnA.html\" title=\"annotation in "
                + "typeannos\">@MRtnA</a> java.lang.String[][]&nbsp;array2()</pre>");

        checkOutput("typeannos/MtdModifiedScoped.html", true,
                "<pre>public final&nbsp;<a href=\"../typeannos/MtdParameterized.html\" "
                + "title=\"class in typeannos\">MtdParameterized</a>&lt;<a href=\"../"
                + "typeannos/MRtnA.html\" title=\"annotation in typeannos\">@MRtnA</a> "
                + "<a href=\"../typeannos/MtdParameterized.html\" title=\"class in "
                + "typeannos\">MtdParameterized</a>&lt;<a href=\"../typeannos/MRtnA."
                + "html\" title=\"annotation in typeannos\">@MRtnA</a> java.lang."
                + "String,<a href=\"../typeannos/MRtnB.html\" title=\"annotation in "
                + "typeannos\">@MRtnB</a> java.lang.String&gt;,<a href=\"../typeannos/"
                + "MRtnB.html\" title=\"annotation in typeannos\">@MRtnB</a> java."
                + "lang.String&gt;&nbsp;nestedMtdParameterized()</pre>");

        // Test for type annotations on method type parameters (MethodTypeParameters.java).
        checkOutput("typeannos/UnscopedUnmodified.html", true,
                "<pre>&lt;K extends <a href=\"../typeannos/MTyParamA.html\" title=\""
                + "annotation in typeannos\">@MTyParamA</a> java.lang.String&gt;"
                + "&nbsp;void&nbsp;methodExtends()</pre>",

                "<pre>&lt;K extends <a href=\"../typeannos/MTyParamA.html\" title=\""
                + "annotation in typeannos\">@MTyParamA</a> <a href=\"../typeannos/"
                + "MtdTyParameterized.html\" title=\"class in typeannos\">"
                + "MtdTyParameterized</a>&lt;<a href=\"../typeannos/MTyParamB.html\" "
                + "title=\"annotation in typeannos\">@MTyParamB</a> java.lang.String"
                + "&gt;&gt;&nbsp;void&nbsp;nestedExtends()</pre>");

        checkOutput("typeannos/PublicModifiedMethods.html", true,
                "<pre>public final&nbsp;&lt;K extends <a href=\"../typeannos/"
                + "MTyParamA.html\" title=\"annotation in typeannos\">@MTyParamA</a> "
                + "java.lang.String&gt;&nbsp;void&nbsp;methodExtends()</pre>",

                "<pre>public final&nbsp;&lt;K extends <a href=\"../typeannos/"
                + "MTyParamA.html\" title=\"annotation in typeannos\">@MTyParamA</a> "
                + "java.lang.String,V extends <a href=\"../typeannos/MTyParamA.html\" "
                + "title=\"annotation in typeannos\">@MTyParamA</a> <a href=\"../"
                + "typeannos/MtdTyParameterized.html\" title=\"class in typeannos\">"
                + "MtdTyParameterized</a>&lt;<a href=\"../typeannos/MTyParamB.html\" "
                + "title=\"annotation in typeannos\">@MTyParamB</a> java.lang.String"
                + "&gt;&gt;&nbsp;void&nbsp;dual()</pre>");

        // Test for type annotations on parameters (Parameters.java).
        checkOutput("typeannos/Parameters.html", true,
                "<pre>void&nbsp;unannotated(<a href=\"../typeannos/"
                + "ParaParameterized.html\" title=\"class in typeannos\">"
                + "ParaParameterized</a>&lt;java.lang.String,java.lang.String&gt;"
                + "&nbsp;a)</pre>",

                "<pre>void&nbsp;nestedParaParameterized(<a href=\"../typeannos/"
                + "ParaParameterized.html\" title=\"class in typeannos\">"
                + "ParaParameterized</a>&lt;<a href=\"../typeannos/ParamA.html\" "
                + "title=\"annotation in typeannos\">@ParamA</a> <a href=\"../"
                + "typeannos/ParaParameterized.html\" title=\"class in typeannos\">"
                + "ParaParameterized</a>&lt;<a href=\"../typeannos/ParamA.html\" "
                + "title=\"annotation in typeannos\">@ParamA</a> java.lang.String,"
                + "<a href=\"../typeannos/ParamB.html\" title=\"annotation in "
                + "typeannos\">@ParamB</a> java.lang.String&gt;,<a href=\"../"
                + "typeannos/ParamB.html\" title=\"annotation in typeannos\">@ParamB"
                + "</a> java.lang.String&gt;&nbsp;a)</pre>",

                // When JDK-8068737 is fixed, we should change the order
                "<pre>void&nbsp;array2Deep(<a href=\"../typeannos/ParamA.html\" "
                + "title=\"annotation in typeannos\">@ParamA</a> java.lang.String "
                + "<a href=\"../typeannos/ParamB.html\" title=\"annotation in typeannos\">"
                + "@ParamB</a> [] "
                + "<a href=\"../typeannos/ParamA.html\" title=\"annotation in typeannos\">"
                + "@ParamA</a> []"
                + "&nbsp;a)</pre>");

        // Test for type annotations on throws (Throws.java).
        checkOutput("typeannos/ThrDefaultUnmodified.html", true,
                "<pre>void&nbsp;oneException()\n"
                + "           throws <a href=\"../typeannos/ThrA.html\" title=\""
                + "annotation in typeannos\">@ThrA</a> java.lang.Exception</pre>",

                "<pre>void&nbsp;twoExceptions()\n"
                + "            throws <a href=\"../typeannos/ThrA.html\" title=\""
                + "annotation in typeannos\">@ThrA</a> java.lang.RuntimeException,\n"
                + "                   <a href=\"../typeannos/ThrA.html\" title=\""
                + "annotation in typeannos\">@ThrA</a> java.lang.Exception</pre>");

        checkOutput("typeannos/ThrPublicModified.html", true,
                "<pre>public final&nbsp;void&nbsp;oneException(java.lang.String&nbsp;a)\n"
                + "                        throws <a href=\"../typeannos/ThrA.html\" "
                + "title=\"annotation in typeannos\">@ThrA</a> java.lang.Exception</pre>",

                "<pre>public final&nbsp;void&nbsp;twoExceptions(java.lang.String&nbsp;a)\n"
                + "                         throws <a href=\"../typeannos/ThrA.html\" "
                + "title=\"annotation in typeannos\">@ThrA</a> java.lang.RuntimeException,\n"
                + "                                <a href=\"../typeannos/ThrA.html\" "
                + "title=\"annotation in typeannos\">@ThrA</a> java.lang.Exception</pre>");

        checkOutput("typeannos/ThrWithValue.html", true,
                "<pre>void&nbsp;oneException()\n"
                + "           throws <a href=\"../typeannos/ThrB.html\" title=\""
                + "annotation in typeannos\">@ThrB</a>(<a href=\"../typeannos/"
                + "ThrB.html#value--\">value</a>=\"m\") java.lang.Exception</pre>",

                "<pre>void&nbsp;twoExceptions()\n"
                + "            throws <a href=\"../typeannos/ThrB.html\" title=\""
                + "annotation in typeannos\">@ThrB</a>(<a href=\"../typeannos/"
                + "ThrB.html#value--\">value</a>=\"m\") java.lang.RuntimeException,\n"
                + "                   <a href=\"../typeannos/ThrA.html\" title=\""
                + "annotation in typeannos\">@ThrA</a> java.lang.Exception</pre>");

        // Test for type annotations on type parameters (TypeParameters.java).
        checkOutput("typeannos/TestMethods.html", true,
                "<pre>&lt;K,<a href=\"../typeannos/TyParaA.html\" title=\"annotation in typeannos\">"
                + "@TyParaA</a> V extends <a href=\"../typeannos/TyParaA.html\" "
                + "title=\"annotation in typeannos\">@TyParaA</a> "
                + "java.lang.String&gt;&nbsp;void&nbsp;secondAnnotated()</pre>"
        );

        // Test for type annotations on wildcard type (Wildcards.java).
        checkOutput("typeannos/BoundTest.html", true,
                "<pre>void&nbsp;wcExtends(<a href=\"../typeannos/MyList.html\" "
                + "title=\"class in typeannos\">MyList</a>&lt;? extends <a href=\""
                + "../typeannos/WldA.html\" title=\"annotation in typeannos\">@WldA"
                + "</a> java.lang.String&gt;&nbsp;l)</pre>",

                "<pre><a href=\"../typeannos/MyList.html\" title=\"class in "
                + "typeannos\">MyList</a>&lt;? super <a href=\"../typeannos/WldA.html\" "
                + "title=\"annotation in typeannos\">@WldA</a> java.lang.String&gt;"
                + "&nbsp;returnWcSuper()</pre>");

        checkOutput("typeannos/BoundWithValue.html", true,
                "<pre>void&nbsp;wcSuper(<a href=\"../typeannos/MyList.html\" title=\""
                + "class in typeannos\">MyList</a>&lt;? super <a href=\"../typeannos/"
                + "WldB.html\" title=\"annotation in typeannos\">@WldB</a>(<a href=\""
                + "../typeannos/WldB.html#value--\">value</a>=\"m\") java.lang."
                + "String&gt;&nbsp;l)</pre>",

                "<pre><a href=\"../typeannos/MyList.html\" title=\"class in "
                + "typeannos\">MyList</a>&lt;? extends <a href=\"../typeannos/WldB."
                + "html\" title=\"annotation in typeannos\">@WldB</a>(<a href=\"../"
                + "typeannos/WldB.html#value--\">value</a>=\"m\") java.lang.String"
                + "&gt;&nbsp;returnWcExtends()</pre>");

        // Test for receiver annotations (Receivers.java).
        checkOutput("typeannos/DefaultUnmodified.html", true,
                "<pre>void&nbsp;withException(<a href=\"../typeannos/RcvrA.html\" "
                + "title=\"annotation in typeannos\">@RcvrA</a>&nbsp;"
                + "DefaultUnmodified&nbsp;this)\n"
                + "            throws java."
                + "lang.Exception</pre>",

                "<pre>java.lang.String&nbsp;nonVoid(<a href=\"../typeannos/RcvrA."
                + "html\" title=\"annotation in typeannos\">@RcvrA</a> <a href=\"../"
                + "typeannos/RcvrB.html\" title=\"annotation in typeannos\">@RcvrB"
                + "</a>(<a href=\"../typeannos/RcvrB.html#value--\">value</a>=\"m\")"
                + "&nbsp;DefaultUnmodified&nbsp;this)</pre>",

                "<pre>&lt;T extends java.lang.Runnable&gt;&nbsp;void&nbsp;accept("
                + "<a href=\"../typeannos/RcvrA.html\" title=\"annotation in "
                + "typeannos\">@RcvrA</a>&nbsp;DefaultUnmodified&nbsp;this,\n"
                + "                                           T&nbsp;r)\n"
                + "                                    throws java.lang.Exception</pre>");

        checkOutput("typeannos/PublicModified.html", true,
                "<pre>public final&nbsp;java.lang.String&nbsp;nonVoid(<a href=\""
                + "../typeannos/RcvrA.html\" title=\"annotation in typeannos\">"
                + "@RcvrA</a>&nbsp;PublicModified&nbsp;this)</pre>",

                "<pre>public final&nbsp;&lt;T extends java.lang.Runnable&gt;&nbsp;"
                + "void&nbsp;accept(<a href=\"../typeannos/RcvrA.html\" title=\""
                + "annotation in typeannos\">@RcvrA</a>&nbsp;PublicModified&nbsp;this,\n"
                + "                                                        T&nbsp;r)\n"
                + "                                                 throws java.lang.Exception</pre>");

        checkOutput("typeannos/WithValue.html", true,
                "<pre>&lt;T extends java.lang.Runnable&gt;&nbsp;void&nbsp;accept("
                + "<a href=\"../typeannos/RcvrB.html\" title=\"annotation in "
                + "typeannos\">@RcvrB</a>(<a href=\"../typeannos/RcvrB.html#value--\">"
                + "value</a>=\"m\")&nbsp;WithValue&nbsp;this,\n"
                + "                                           T&nbsp;r)\n"
                + "                                    throws java.lang.Exception</pre>");

        checkOutput("typeannos/WithFinal.html", true,
                "<pre>java.lang.String&nbsp;nonVoid(<a href=\"../typeannos/RcvrB."
                + "html\" title=\"annotation in typeannos\">@RcvrB</a>(<a href=\"../"
                + "typeannos/RcvrB.html#value--\">value</a>=\"m\")&nbsp;WithFinal"
                + "&nbsp;this)</pre>");

        checkOutput("typeannos/WithBody.html", true,
                "<pre>void&nbsp;field(<a href=\"../typeannos/RcvrA.html\" title=\""
                + "annotation in typeannos\">@RcvrA</a>&nbsp;WithBody&nbsp;this)</pre>");

        checkOutput("typeannos/Generic2.html", true,
                "<pre>void&nbsp;test2(<a href=\"../typeannos/RcvrA.html\" title=\""
                + "annotation in typeannos\">@RcvrA</a>&nbsp;Generic2&lt;X&gt;&nbsp;this)</pre>");
    }
}
