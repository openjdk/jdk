/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @library  ../lib/
 * @ignore
 * @build    JavadocTester TestTypeAnnotations
 * @run main TestTypeAnnotations
 */

public class TestTypeAnnotations extends JavadocTester {

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", OUTPUT_DIR, "-sourcepath", SRC_DIR, "-private", "typeannos"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        // Test for type annotations on Class Extends (ClassExtends.java).
        { "typeannos/MyClass.html",
            "extends <a href=\"../typeannos/ClassExtA.html\" title=\"annotation " +
            "in typeannos\">@ClassExtA</a> <a href=\"../typeannos/ParameterizedClass.html\" " +
            "title=\"class in typeannos\">ParameterizedClass</a>&lt;<a href=\"" +
            "../typeannos/ClassExtB.html\" title=\"annotation in typeannos\">" +
            "@ClassExtB</a> java.lang.String&gt;"
        },
        { "typeannos/MyClass.html",
            "implements <a href=\"../typeannos/ClassExtB.html\" title=\"" +
            "annotation in typeannos\">@ClassExtB</a> java.lang.CharSequence, " +
            "<a href=\"../typeannos/ClassExtA.html\" title=\"annotation in " +
            "typeannos\">@ClassExtA</a> <a href=\"../typeannos/ParameterizedInterface.html\" " +
            "title=\"interface in typeannos\">ParameterizedInterface</a>&lt;" +
            "<a href=\"../typeannos/ClassExtB.html\" title=\"annotation in " +
            "typeannos\">@ClassExtB</a> java.lang.String&gt;</pre>"
        },
        { "typeannos/MyInterface.html",
            "extends <a href=\"../typeannos/ClassExtA.html\" title=\"annotation " +
            "in typeannos\">@ClassExtA</a> <a href=\"../typeannos/" +
            "ParameterizedInterface.html\" title=\"interface in typeannos\">" +
            "ParameterizedInterface</a>&lt;<a href=\"../typeannos/ClassExtA.html\" " +
            "title=\"annotation in typeannos\">@ClassExtA</a> java.lang.String&gt;, " +
            "<a href=\"../typeannos/ClassExtB.html\" title=\"annotation in " +
            "typeannos\">@ClassExtB</a> java.lang.CharSequence</pre>"
        },

        // Test for type annotations on Class Parameters (ClassParameters.java).
        { "typeannos/ExtendsBound.html",
            "class <span class=\"typeNameLabel\">ExtendsBound&lt;K extends <a " +
            "href=\"../typeannos/ClassParamA.html\" title=\"annotation in " +
            "typeannos\">@ClassParamA</a> java.lang.String&gt;</span>"
        },
        { "typeannos/ExtendsGeneric.html",
            "<pre>class <span class=\"typeNameLabel\">ExtendsGeneric&lt;K extends " +
            "<a href=\"../typeannos/ClassParamA.html\" title=\"annotation in " +
            "typeannos\">@ClassParamA</a> <a href=\"../typeannos/Unannotated.html\" " +
            "title=\"class in typeannos\">Unannotated</a>&lt;<a href=\"" +
            "../typeannos/ClassParamB.html\" title=\"annotation in typeannos\">" +
            "@ClassParamB</a> java.lang.String&gt;&gt;</span>"
        },
        { "typeannos/TwoBounds.html",
            "<pre>class <span class=\"typeNameLabel\">TwoBounds&lt;K extends <a href=\"" +
            "../typeannos/ClassParamA.html\" title=\"annotation in typeannos\">" +
            "@ClassParamA</a> java.lang.String,V extends <a href=\"../typeannos/" +
            "ClassParamB.html\" title=\"annotation in typeannos\">@ClassParamB" +
            "</a> java.lang.String&gt;</span>"
        },
        { "typeannos/Complex1.html",
            "class <span class=\"typeNameLabel\">Complex1&lt;K extends <a href=\"../" +
            "typeannos/ClassParamA.html\" title=\"annotation in typeannos\">" +
            "@ClassParamA</a> java.lang.String &amp; java.lang.Runnable&gt;</span>"
        },
        { "typeannos/Complex2.html",
            "class <span class=\"typeNameLabel\">Complex2&lt;K extends java.lang." +
            "String &amp; <a href=\"../typeannos/ClassParamB.html\" title=\"" +
            "annotation in typeannos\">@ClassParamB</a> java.lang.Runnable&gt;</span>"
        },
        { "typeannos/ComplexBoth.html",
            "class <span class=\"typeNameLabel\">ComplexBoth&lt;K extends <a href=\"" +
            "../typeannos/ClassParamA.html\" title=\"annotation in typeannos\"" +
            ">@ClassParamA</a> java.lang.String &amp; <a href=\"../typeannos/" +
            "ClassParamA.html\" title=\"annotation in typeannos\">@ClassParamA" +
            "</a> java.lang.Runnable&gt;</span>"
        },

        // Test for type annotations on fields (Fields.java).
        { "typeannos/DefaultScope.html",
            "<pre><a href=\"../typeannos/Parameterized.html\" title=\"class in " +
            "typeannos\">Parameterized</a>&lt;<a href=\"../typeannos/FldA.html\" " +
            "title=\"annotation in typeannos\">@FldA</a> java.lang.String,<a " +
            "href=\"../typeannos/FldB.html\" title=\"annotation in typeannos\">" +
            "@FldB</a> java.lang.String&gt; bothTypeArgs</pre>"
        },
        { "typeannos/DefaultScope.html",
            "<pre><a href=\"../typeannos/FldA.html\" title=\"annotation in " +
            "typeannos\">@FldA</a> java.lang.String <a href=\"../typeannos/" +
            "FldB.html\" title=\"annotation in typeannos\">@FldB</a> [] " +
            "array1Deep</pre>"
        },
        { "typeannos/DefaultScope.html",
            "<pre>java.lang.String[] <a href=\"../typeannos/FldB.html\" " +
            "title=\"annotation in typeannos\">@FldB</a> [] array2SecondOld</pre>"
        },
        { "typeannos/DefaultScope.html",
            "<pre><a href=\"../typeannos/FldD.html\" title=\"annotation in " +
            "typeannos\">@FldD</a> java.lang.String <a href=\"../typeannos/" +
            "FldC.html\" title=\"annotation in typeannos\">@FldC</a> <a href=\"" +
            "../typeannos/FldA.html\" title=\"annotation in typeannos\">@FldA" +
            "</a> [] <a href=\"../typeannos/FldC.html\" title=\"annotation in " +
            "typeannos\">@FldC</a> <a href=\"../typeannos/FldB.html\" title=\"" +
            "annotation in typeannos\">@FldB</a> [] array2Deep</pre>"
        },
        { "typeannos/ModifiedScoped.html",
            "<pre>public final&nbsp;<a href=\"../typeannos/Parameterized.html\" " +
            "title=\"class in typeannos\">Parameterized</a>&lt;<a href=\"../" +
            "typeannos/FldA.html\" title=\"annotation in typeannos\">@FldA</a> " +
            "<a href=\"../typeannos/Parameterized.html\" title=\"class in " +
            "typeannos\">Parameterized</a>&lt;<a href=\"../typeannos/FldA.html\" " +
            "title=\"annotation in typeannos\">@FldA</a> java.lang.String,<a " +
            "href=\"../typeannos/FldB.html\" title=\"annotation in typeannos\">" +
            "@FldB</a> java.lang.String&gt;,<a href=\"../typeannos/FldB.html\" " +
            "title=\"annotation in typeannos\">@FldB</a> java.lang.String&gt; " +
            "nestedParameterized</pre>"
        },
        { "typeannos/ModifiedScoped.html",
            "<pre>public final&nbsp;<a href=\"../typeannos/FldA.html\" " +
            "title=\"annotation in typeannos\">@FldA</a> java.lang.String[][] " +
            "array2</pre>"
        },

        // Test for type annotations on method return types (MethodReturnType.java).
        { "typeannos/MtdDefaultScope.html",
            "<pre>public&nbsp;&lt;T&gt;&nbsp;<a href=\"../typeannos/MRtnA.html\" " +
            "title=\"annotation in typeannos\">@MRtnA</a> java.lang.String" +
            "&nbsp;method()</pre>"
        },
        { "typeannos/MtdDefaultScope.html",
            "<pre><a href=\"../typeannos/MRtnA.html\" title=\"annotation in " +
            "typeannos\">@MRtnA</a> java.lang.String <a href=\"../typeannos/" +
            "MRtnA.html\" title=\"annotation in typeannos\">@MRtnA</a> [] <a " +
            "href=\"../typeannos/MRtnB.html\" title=\"annotation in typeannos\">" +
            "@MRtnB</a> []&nbsp;array2Deep()</pre>"
        },
        { "typeannos/MtdDefaultScope.html",
            "<pre><a href=\"../typeannos/MRtnA.html\" title=\"annotation in " +
            "typeannos\">@MRtnA</a> java.lang.String[][]&nbsp;array2()</pre>"
        },
        { "typeannos/MtdModifiedScoped.html",
            "<pre>public final&nbsp;<a href=\"../typeannos/MtdParameterized.html\" " +
            "title=\"class in typeannos\">MtdParameterized</a>&lt;<a href=\"../" +
            "typeannos/MRtnA.html\" title=\"annotation in typeannos\">@MRtnA</a> " +
            "<a href=\"../typeannos/MtdParameterized.html\" title=\"class in " +
            "typeannos\">MtdParameterized</a>&lt;<a href=\"../typeannos/MRtnA." +
            "html\" title=\"annotation in typeannos\">@MRtnA</a> java.lang." +
            "String,<a href=\"../typeannos/MRtnB.html\" title=\"annotation in " +
            "typeannos\">@MRtnB</a> java.lang.String&gt;,<a href=\"../typeannos/" +
            "MRtnB.html\" title=\"annotation in typeannos\">@MRtnB</a> java." +
            "lang.String&gt;&nbsp;nestedMtdParameterized()</pre>"
        },

        // Test for type annotations on method type parameters (MethodTypeParameters.java).
        { "typeannos/UnscopedUnmodified.html",
            "<pre>&lt;K extends <a href=\"../typeannos/MTyParamA.html\" title=\"" +
            "annotation in typeannos\">@MTyParamA</a> java.lang.String&gt;" +
            "&nbsp;void&nbsp;methodExtends()</pre>"
        },
        { "typeannos/UnscopedUnmodified.html",
            "<pre>&lt;K extends <a href=\"../typeannos/MTyParamA.html\" title=\"" +
            "annotation in typeannos\">@MTyParamA</a> <a href=\"../typeannos/" +
            "MtdTyParameterized.html\" title=\"class in typeannos\">" +
            "MtdTyParameterized</a>&lt;<a href=\"../typeannos/MTyParamB.html\" " +
            "title=\"annotation in typeannos\">@MTyParamB</a> java.lang.String" +
            "&gt;&gt;&nbsp;void&nbsp;nestedExtends()</pre>"
        },
        { "typeannos/PublicModifiedMethods.html",
            "<pre>public final&nbsp;&lt;K extends <a href=\"../typeannos/" +
            "MTyParamA.html\" title=\"annotation in typeannos\">@MTyParamA</a> " +
            "java.lang.String&gt;&nbsp;void&nbsp;methodExtends()</pre>"
        },
        { "typeannos/PublicModifiedMethods.html",
            "<pre>public final&nbsp;&lt;K extends <a href=\"../typeannos/" +
            "MTyParamA.html\" title=\"annotation in typeannos\">@MTyParamA</a> " +
            "java.lang.String,V extends <a href=\"../typeannos/MTyParamA.html\" " +
            "title=\"annotation in typeannos\">@MTyParamA</a> <a href=\"../" +
            "typeannos/MtdTyParameterized.html\" title=\"class in typeannos\">" +
            "MtdTyParameterized</a>&lt;<a href=\"../typeannos/MTyParamB.html\" " +
            "title=\"annotation in typeannos\">@MTyParamB</a> java.lang.String" +
            "&gt;&gt;&nbsp;void&nbsp;dual()</pre>"
        },

        // Test for type annotations on parameters (Parameters.java).
        { "typeannos/Parameters.html",
            "<pre>void&nbsp;unannotated(<a href=\"../typeannos/" +
            "ParaParameterized.html\" title=\"class in typeannos\">" +
            "ParaParameterized</a>&lt;java.lang.String,java.lang.String&gt;" +
            "&nbsp;a)</pre>"
        },
        { "typeannos/Parameters.html",
            "<pre>void&nbsp;nestedParaParameterized(<a href=\"../typeannos/" +
            "ParaParameterized.html\" title=\"class in typeannos\">" +
            "ParaParameterized</a>&lt;<a href=\"../typeannos/ParamA.html\" " +
            "title=\"annotation in typeannos\">@ParamA</a> <a href=\"../" +
            "typeannos/ParaParameterized.html\" title=\"class in typeannos\">" +
            "ParaParameterized</a>&lt;<a href=\"../typeannos/ParamA.html\" " +
            "title=\"annotation in typeannos\">@ParamA</a> java.lang.String," +
            "<a href=\"../typeannos/ParamB.html\" title=\"annotation in " +
            "typeannos\">@ParamB</a> java.lang.String&gt;,<a href=\"../" +
            "typeannos/ParamB.html\" title=\"annotation in typeannos\">@ParamB" +
            "</a> java.lang.String&gt;&nbsp;a)</pre>"
        },
        { "typeannos/Parameters.html",
            "<pre>void&nbsp;array2Deep(<a href=\"../typeannos/ParamA.html\" " +
            "title=\"annotation in typeannos\">@ParamA</a> java.lang.String " +
            "<a href=\"../typeannos/ParamA.html\" title=\"annotation in " +
            "typeannos\">@ParamA</a> [] <a href=\"../typeannos/ParamB.html\" " +
            "title=\"annotation in typeannos\">@ParamB</a> []&nbsp;a)</pre>"
        },

        // Test for type annotations on throws (Throws.java).
        { "typeannos/ThrDefaultUnmodified.html",
            "<pre>void&nbsp;oneException()\n" +
            "           throws <a href=\"../typeannos/ThrA.html\" title=\"" +
            "annotation in typeannos\">@ThrA</a> java.lang.Exception</pre>"
        },
        { "typeannos/ThrDefaultUnmodified.html",
            "<pre>void&nbsp;twoExceptions()\n" +
            "            throws <a href=\"../typeannos/ThrA.html\" title=\"" +
            "annotation in typeannos\">@ThrA</a> java.lang.RuntimeException,\n" +
            "                   <a href=\"../typeannos/ThrA.html\" title=\"" +
            "annotation in typeannos\">@ThrA</a> java.lang.Exception</pre>"
        },
        { "typeannos/ThrPublicModified.html",
            "<pre>public final&nbsp;void&nbsp;oneException(java.lang.String&nbsp;a)\n" +
            "                        throws <a href=\"../typeannos/ThrA.html\" " +
            "title=\"annotation in typeannos\">@ThrA</a> java.lang.Exception</pre>"
        },
        { "typeannos/ThrPublicModified.html",
            "<pre>public final&nbsp;void&nbsp;twoExceptions(java.lang.String&nbsp;a)\n" +
            "                         throws <a href=\"../typeannos/ThrA.html\" " +
            "title=\"annotation in typeannos\">@ThrA</a> java.lang.RuntimeException,\n" +
            "                                <a href=\"../typeannos/ThrA.html\" " +
            "title=\"annotation in typeannos\">@ThrA</a> java.lang.Exception</pre>"
        },
        { "typeannos/ThrWithValue.html",
            "<pre>void&nbsp;oneException()\n" +
            "           throws <a href=\"../typeannos/ThrB.html\" title=\"" +
            "annotation in typeannos\">@ThrB</a>(<a href=\"../typeannos/" +
            "ThrB.html#value--\">value</a>=\"m\") java.lang.Exception</pre>"
        },
        { "typeannos/ThrWithValue.html",
            "<pre>void&nbsp;twoExceptions()\n" +
            "            throws <a href=\"../typeannos/ThrB.html\" title=\"" +
            "annotation in typeannos\">@ThrB</a>(<a href=\"../typeannos/" +
            "ThrB.html#value--\">value</a>=\"m\") java.lang.RuntimeException,\n" +
            "                   <a href=\"../typeannos/ThrA.html\" title=\"" +
            "annotation in typeannos\">@ThrA</a> java.lang.Exception</pre>"
        },

        // Test for type annotations on type parameters (TypeParameters.java).
        { "typeannos/TestMethods.html",
            "<pre>&lt;K,V extends <a href=\"../typeannos/TyParaA.html\" title=\"" +
            "annotation in typeannos\">@TyParaA</a> java.lang.String&gt;&nbsp;" +
            "void&nbsp;secondAnnotated()</pre>"
        },

        // Test for type annotations on wildcard type (Wildcards.java).
        { "typeannos/BoundTest.html",
            "<pre>void&nbsp;wcExtends(<a href=\"../typeannos/MyList.html\" " +
            "title=\"class in typeannos\">MyList</a>&lt;? extends <a href=\"" +
            "../typeannos/WldA.html\" title=\"annotation in typeannos\">@WldA" +
            "</a> java.lang.String&gt;&nbsp;l)</pre>"
        },
        { "typeannos/BoundTest.html",
            "<pre><a href=\"../typeannos/MyList.html\" title=\"class in " +
            "typeannos\">MyList</a>&lt;? super <a href=\"../typeannos/WldA.html\" " +
            "title=\"annotation in typeannos\">@WldA</a> java.lang.String&gt;" +
            "&nbsp;returnWcSuper()</pre>"
        },
        { "typeannos/BoundWithValue.html",
            "<pre>void&nbsp;wcSuper(<a href=\"../typeannos/MyList.html\" title=\"" +
            "class in typeannos\">MyList</a>&lt;? super <a href=\"../typeannos/" +
            "WldB.html\" title=\"annotation in typeannos\">@WldB</a>(<a href=\"" +
            "../typeannos/WldB.html#value--\">value</a>=\"m\") java.lang." +
            "String&gt;&nbsp;l)</pre>"
        },
        { "typeannos/BoundWithValue.html",
            "<pre><a href=\"../typeannos/MyList.html\" title=\"class in " +
            "typeannos\">MyList</a>&lt;? extends <a href=\"../typeannos/WldB." +
            "html\" title=\"annotation in typeannos\">@WldB</a>(<a href=\"../" +
            "typeannos/WldB.html#value--\">value</a>=\"m\") java.lang.String" +
            "&gt;&nbsp;returnWcExtends()</pre>"
        },

        // Test for receiver annotations (Receivers.java).
        { "typeannos/DefaultUnmodified.html",
            "<pre>void&nbsp;withException(<a href=\"../typeannos/RcvrA.html\" " +
            "title=\"annotation in typeannos\">@RcvrA</a>&nbsp;" +
            "DefaultUnmodified&nbsp;this)\n" +
            "            throws java." +
            "lang.Exception</pre>"
        },
        { "typeannos/DefaultUnmodified.html",
            "<pre>java.lang.String&nbsp;nonVoid(<a href=\"../typeannos/RcvrA." +
            "html\" title=\"annotation in typeannos\">@RcvrA</a> <a href=\"../" +
            "typeannos/RcvrB.html\" title=\"annotation in typeannos\">@RcvrB" +
            "</a>(<a href=\"../typeannos/RcvrB.html#value--\">value</a>=\"m\")" +
            "&nbsp;DefaultUnmodified&nbsp;this)</pre>"
        },
        { "typeannos/DefaultUnmodified.html",
            "<pre>&lt;T extends java.lang.Runnable&gt;&nbsp;void&nbsp;accept(" +
            "<a href=\"../typeannos/RcvrA.html\" title=\"annotation in " +
            "typeannos\">@RcvrA</a>&nbsp;DefaultUnmodified&nbsp;this,\n" +
            "                                           T&nbsp;r)\n" +
            "                                    throws java.lang.Exception</pre>"
        },
        { "typeannos/PublicModified.html",
            "<pre>public final&nbsp;java.lang.String&nbsp;nonVoid(<a href=\"" +
            "../typeannos/RcvrA.html\" title=\"annotation in typeannos\">" +
            "@RcvrA</a>&nbsp;PublicModified&nbsp;this)</pre>"
        },
        { "typeannos/PublicModified.html",
            "<pre>public final&nbsp;&lt;T extends java.lang.Runnable&gt;&nbsp;" +
            "void&nbsp;accept(<a href=\"../typeannos/RcvrA.html\" title=\"" +
            "annotation in typeannos\">@RcvrA</a>&nbsp;PublicModified&nbsp;this,\n" +
            "                                                        T&nbsp;r)\n" +
            "                                                 throws java.lang.Exception</pre>"
        },
        { "typeannos/WithValue.html",
            "<pre>&lt;T extends java.lang.Runnable&gt;&nbsp;void&nbsp;accept(" +
            "<a href=\"../typeannos/RcvrB.html\" title=\"annotation in " +
            "typeannos\">@RcvrB</a>(<a href=\"../typeannos/RcvrB.html#value--\">" +
            "value</a>=\"m\")&nbsp;WithValue&nbsp;this,\n" +
            "                                           T&nbsp;r)\n" +
            "                                    throws java.lang.Exception</pre>"
        },
        { "typeannos/WithFinal.html",
            "<pre>java.lang.String&nbsp;nonVoid(<a href=\"../typeannos/RcvrB." +
            "html\" title=\"annotation in typeannos\">@RcvrB</a>(<a href=\"../" +
            "typeannos/RcvrB.html#value--\">value</a>=\"m\")&nbsp;WithFinal" +
            "&nbsp;this)</pre>"
        },
        { "typeannos/WithBody.html",
            "<pre>void&nbsp;field(<a href=\"../typeannos/RcvrA.html\" title=\"" +
            "annotation in typeannos\">@RcvrA</a>&nbsp;WithBody&nbsp;this)</pre>"
        },
        { "typeannos/Generic2.html",
            "<pre>void&nbsp;test2(<a href=\"../typeannos/RcvrA.html\" title=\"" +
            "annotation in typeannos\">@RcvrA</a>&nbsp;Generic2&lt;X&gt;&nbsp;this)</pre>"
        }
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestTypeAnnotations tester = new TestTypeAnnotations();
        tester.run(ARGS, TEST, NO_TEST);
        tester.printSummary();
    }
}
