/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8005091 8009686
 * @summary  Make sure that type annotations are displayed correctly
 * @author   Bhavesh Patel
 * @library  ../lib/
 * @build    JavadocTester TestTypeAnnotations
 * @run main TestTypeAnnotations
 */

public class TestTypeAnnotations extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "8005091-8009686";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "-private", "typeannos"
    };

    //Input for string search tests.
    private static final String[][] NEGATED_TEST = NO_TEST;
    private static final String[][] TEST = {
        // Test for type annotations on Class Extends (ClassExtends.java).
        {BUG_ID + FS + "typeannos" + FS + "MyClass.html",
            "extends <a href=\"../typeannos/ClassExtA.html\" title=\"annotation " +
            "in typeannos\">@ClassExtA</a> <a href=\"../typeannos/ParameterizedClass.html\" " +
            "title=\"class in typeannos\">ParameterizedClass</a>&lt;<a href=\"" +
            "../typeannos/ClassExtB.html\" title=\"annotation in typeannos\">" +
            "@ClassExtB</a> java.lang.String&gt;"
        },
        {BUG_ID + FS + "typeannos" + FS + "MyClass.html",
            "implements <a href=\"../typeannos/ClassExtB.html\" title=\"" +
            "annotation in typeannos\">@ClassExtB</a> java.lang.CharSequence, " +
            "<a href=\"../typeannos/ClassExtA.html\" title=\"annotation in " +
            "typeannos\">@ClassExtA</a> <a href=\"../typeannos/ParameterizedInterface.html\" " +
            "title=\"interface in typeannos\">ParameterizedInterface</a>&lt;" +
            "<a href=\"../typeannos/ClassExtB.html\" title=\"annotation in " +
            "typeannos\">@ClassExtB</a> java.lang.String&gt;</pre>"
        },
        {BUG_ID + FS + "typeannos" + FS + "MyInterface.html",
            "extends <a href=\"../typeannos/ClassExtA.html\" title=\"annotation " +
            "in typeannos\">@ClassExtA</a> <a href=\"../typeannos/" +
            "ParameterizedInterface.html\" title=\"interface in typeannos\">" +
            "ParameterizedInterface</a>&lt;<a href=\"../typeannos/ClassExtA.html\" " +
            "title=\"annotation in typeannos\">@ClassExtA</a> java.lang.String&gt;, " +
            "<a href=\"../typeannos/ClassExtB.html\" title=\"annotation in " +
            "typeannos\">@ClassExtB</a> java.lang.CharSequence</pre>"
        },

        // Test for type annotations on Class Parameters (ClassParameters.java).
        {BUG_ID + FS + "typeannos" + FS + "ExtendsBound.html",
            "class <span class=\"strong\">ExtendsBound&lt;K extends <a " +
            "href=\"../typeannos/ClassParamA.html\" title=\"annotation in " +
            "typeannos\">@ClassParamA</a> java.lang.String&gt;</span>"
        },
        {BUG_ID + FS + "typeannos" + FS + "ExtendsGeneric.html",
            "<pre> class <span class=\"strong\">ExtendsGeneric&lt;K extends " +
            "<a href=\"../typeannos/ClassParamA.html\" title=\"annotation in " +
            "typeannos\">@ClassParamA</a> <a href=\"../typeannos/Unannotated.html\" " +
            "title=\"class in typeannos\">Unannotated</a>&lt;<a href=\"" +
            "../typeannos/ClassParamB.html\" title=\"annotation in typeannos\">" +
            "@ClassParamB</a> java.lang.String&gt;&gt;</span>"
        },
        {BUG_ID + FS + "typeannos" + FS + "TwoBounds.html",
            "<pre> class <span class=\"strong\">TwoBounds&lt;K extends <a href=\"" +
            "../typeannos/ClassParamA.html\" title=\"annotation in typeannos\">" +
            "@ClassParamA</a> java.lang.String,V extends <a href=\"../typeannos/" +
            "ClassParamB.html\" title=\"annotation in typeannos\">@ClassParamB" +
            "</a> java.lang.String&gt;</span>"
        },
        {BUG_ID + FS + "typeannos" + FS + "Complex1.html",
            "class <span class=\"strong\">Complex1&lt;K extends <a href=\"../" +
            "typeannos/ClassParamA.html\" title=\"annotation in typeannos\">" +
            "@ClassParamA</a> java.lang.String &amp; java.lang.Runnable&gt;</span>"
        },
        {BUG_ID + FS + "typeannos" + FS + "Complex2.html",
            "class <span class=\"strong\">Complex2&lt;K extends java.lang." +
            "String &amp; <a href=\"../typeannos/ClassParamB.html\" title=\"" +
            "annotation in typeannos\">@ClassParamB</a> java.lang.Runnable&gt;</span>"
        },
        {BUG_ID + FS + "typeannos" + FS + "ComplexBoth.html",
            "class <span class=\"strong\">ComplexBoth&lt;K extends <a href=\"" +
            "../typeannos/ClassParamA.html\" title=\"annotation in typeannos\"" +
            ">@ClassParamA</a> java.lang.String &amp; <a href=\"../typeannos/" +
            "ClassParamA.html\" title=\"annotation in typeannos\">@ClassParamA" +
            "</a> java.lang.Runnable&gt;</span>"
        },

        // Test for type annotations on fields (Fields.java).
        {BUG_ID + FS + "typeannos" + FS + "DefaultScope.html",
            "<pre><a href=\"../typeannos/Parameterized.html\" title=\"class in " +
            "typeannos\">Parameterized</a>&lt;<a href=\"../typeannos/FldA.html\" " +
            "title=\"annotation in typeannos\">@FldA</a> java.lang.String,<a " +
            "href=\"../typeannos/FldB.html\" title=\"annotation in typeannos\">" +
            "@FldB</a> java.lang.String&gt; bothTypeArgs</pre>"
        },
        {BUG_ID + FS + "typeannos" + FS + "DefaultScope.html",
            "<pre><a href=\"../typeannos/FldA.html\" title=\"annotation in " +
            "typeannos\">@FldA</a> java.lang.String <a href=\"../typeannos/" +
            "FldB.html\" title=\"annotation in typeannos\">@FldB</a> [] " +
            "array1Deep</pre>"
        },
        {BUG_ID + FS + "typeannos" + FS + "DefaultScope.html",
            "<pre>java.lang.String[] <a href=\"../typeannos/FldB.html\" " +
            "title=\"annotation in typeannos\">@FldB</a> [] array2SecondOld</pre>"
        },
        {BUG_ID + FS + "typeannos" + FS + "DefaultScope.html",
            "<pre><a href=\"../typeannos/FldD.html\" title=\"annotation in " +
            "typeannos\">@FldD</a> java.lang.String <a href=\"../typeannos/" +
            "FldC.html\" title=\"annotation in typeannos\">@FldC</a> <a href=\"" +
            "../typeannos/FldA.html\" title=\"annotation in typeannos\">@FldA" +
            "</a> [] <a href=\"../typeannos/FldC.html\" title=\"annotation in " +
            "typeannos\">@FldC</a> <a href=\"../typeannos/FldB.html\" title=\"" +
            "annotation in typeannos\">@FldB</a> [] array2Deep</pre>"
        },
        {BUG_ID + FS + "typeannos" + FS + "ModifiedScoped.html",
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
        {BUG_ID + FS + "typeannos" + FS + "ModifiedScoped.html",
            "<pre>public final&nbsp;<a href=\"../typeannos/FldA.html\" " +
            "title=\"annotation in typeannos\">@FldA</a> java.lang.String[][] " +
            "array2</pre>"
        },

        // Test for type annotations on method return types (MethodReturnType.java).
        {BUG_ID + FS + "typeannos" + FS + "MtdDefaultScope.html",
            "<pre>public&nbsp;&lt;T&gt;&nbsp;<a href=\"../typeannos/MRtnA.html\" " +
            "title=\"annotation in typeannos\">@MRtnA</a> java.lang.String" +
            "&nbsp;method()</pre>"
        },
        {BUG_ID + FS + "typeannos" + FS + "MtdDefaultScope.html",
            "<pre><a href=\"../typeannos/MRtnA.html\" title=\"annotation in " +
            "typeannos\">@MRtnA</a> java.lang.String <a href=\"../typeannos/" +
            "MRtnA.html\" title=\"annotation in typeannos\">@MRtnA</a> [] <a " +
            "href=\"../typeannos/MRtnB.html\" title=\"annotation in typeannos\">" +
            "@MRtnB</a> []&nbsp;array2Deep()</pre>"
        },
        {BUG_ID + FS + "typeannos" + FS + "MtdDefaultScope.html",
            "<pre><a href=\"../typeannos/MRtnA.html\" title=\"annotation in " +
            "typeannos\">@MRtnA</a> java.lang.String[][]&nbsp;array2()</pre>"
        },
        {BUG_ID + FS + "typeannos" + FS + "MtdModifiedScoped.html",
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
        {BUG_ID + FS + "typeannos" + FS + "UnscopedUnmodified.html",
            "<pre>&lt;K extends <a href=\"../typeannos/MTyParamA.html\" title=\"" +
            "annotation in typeannos\">@MTyParamA</a> java.lang.String&gt;" +
            "&nbsp;void&nbsp;methodExtends()</pre>"
        },
        {BUG_ID + FS + "typeannos" + FS + "UnscopedUnmodified.html",
            "<pre>&lt;K extends <a href=\"../typeannos/MTyParamA.html\" title=\"" +
            "annotation in typeannos\">@MTyParamA</a> <a href=\"../typeannos/" +
            "MtdTyParameterized.html\" title=\"class in typeannos\">" +
            "MtdTyParameterized</a>&lt;<a href=\"../typeannos/MTyParamB.html\" " +
            "title=\"annotation in typeannos\">@MTyParamB</a> java.lang.String" +
            "&gt;&gt;&nbsp;void&nbsp;nestedExtends()</pre>"
        },
        {BUG_ID + FS + "typeannos" + FS + "PublicModifiedMethods.html",
            "<pre>public final&nbsp;&lt;K extends <a href=\"../typeannos/" +
            "MTyParamA.html\" title=\"annotation in typeannos\">@MTyParamA</a> " +
            "java.lang.String&gt;&nbsp;void&nbsp;methodExtends()</pre>"
        },
        {BUG_ID + FS + "typeannos" + FS + "PublicModifiedMethods.html",
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
        {BUG_ID + FS + "typeannos" + FS + "Parameters.html",
            "<pre>void&nbsp;unannotated(<a href=\"../typeannos/" +
            "ParaParameterized.html\" title=\"class in typeannos\">" +
            "ParaParameterized</a>&lt;java.lang.String,java.lang.String&gt;" +
            "&nbsp;a)</pre>"
        },
        {BUG_ID + FS + "typeannos" + FS + "Parameters.html",
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
        {BUG_ID + FS + "typeannos" + FS + "Parameters.html",
            "<pre>void&nbsp;array2Deep(<a href=\"../typeannos/ParamA.html\" " +
            "title=\"annotation in typeannos\">@ParamA</a> java.lang.String " +
            "<a href=\"../typeannos/ParamA.html\" title=\"annotation in " +
            "typeannos\">@ParamA</a> [] <a href=\"../typeannos/ParamB.html\" " +
            "title=\"annotation in typeannos\">@ParamB</a> []&nbsp;a)</pre>"
        },

        // Test for type annotations on throws (Throws.java).
        {BUG_ID + FS + "typeannos" + FS + "ThrDefaultUnmodified.html",
            "<pre>void&nbsp;oneException()" + NL +
            "           throws <a href=\"../typeannos/ThrA.html\" title=\"" +
            "annotation in typeannos\">@ThrA</a> java.lang.Exception</pre>"
        },
        {BUG_ID + FS + "typeannos" + FS + "ThrDefaultUnmodified.html",
            "<pre>void&nbsp;twoExceptions()" + NL +
            "            throws <a href=\"../typeannos/ThrA.html\" title=\"" +
            "annotation in typeannos\">@ThrA</a> java.lang.RuntimeException," + NL +
            "                   <a href=\"../typeannos/ThrA.html\" title=\"" +
            "annotation in typeannos\">@ThrA</a> java.lang.Exception</pre>"
        },
        {BUG_ID + FS + "typeannos" + FS + "ThrPublicModified.html",
            "<pre>public final&nbsp;void&nbsp;oneException(java.lang.String&nbsp;a)" + NL +
            "                        throws <a href=\"../typeannos/ThrA.html\" " +
            "title=\"annotation in typeannos\">@ThrA</a> java.lang.Exception</pre>"
        },
        {BUG_ID + FS + "typeannos" + FS + "ThrPublicModified.html",
            "<pre>public final&nbsp;void&nbsp;twoExceptions(java.lang.String&nbsp;a)" + NL +
            "                         throws <a href=\"../typeannos/ThrA.html\" " +
            "title=\"annotation in typeannos\">@ThrA</a> java.lang.RuntimeException," + NL +
            "                                <a href=\"../typeannos/ThrA.html\" " +
            "title=\"annotation in typeannos\">@ThrA</a> java.lang.Exception</pre>"
        },
        {BUG_ID + FS + "typeannos" + FS + "ThrWithValue.html",
            "<pre>void&nbsp;oneException()" + NL +
            "           throws <a href=\"../typeannos/ThrB.html\" title=\"" +
            "annotation in typeannos\">@ThrB</a>(<a href=\"../typeannos/" +
            "ThrB.html#value()\">value</a>=\"m\") java.lang.Exception</pre>"
        },
        {BUG_ID + FS + "typeannos" + FS + "ThrWithValue.html",
            "<pre>void&nbsp;twoExceptions()" + NL +
            "            throws <a href=\"../typeannos/ThrB.html\" title=\"" +
            "annotation in typeannos\">@ThrB</a>(<a href=\"../typeannos/" +
            "ThrB.html#value()\">value</a>=\"m\") java.lang.RuntimeException," + NL +
            "                   <a href=\"../typeannos/ThrA.html\" title=\"" +
            "annotation in typeannos\">@ThrA</a> java.lang.Exception</pre>"
        },

        // Test for type annotations on type parameters (TypeParameters.java).
        {BUG_ID + FS + "typeannos" + FS + "TestMethods.html",
            "<pre>&lt;K,V extends <a href=\"../typeannos/TyParaA.html\" title=\"" +
            "annotation in typeannos\">@TyParaA</a> java.lang.String&gt;&nbsp;" +
            "void&nbsp;secondAnnotated()</pre>"
        },

        // Test for type annotations on wildcard type (Wildcards.java).
        {BUG_ID + FS + "typeannos" + FS + "BoundTest.html",
            "<pre>void&nbsp;wcExtends(<a href=\"../typeannos/MyList.html\" " +
            "title=\"class in typeannos\">MyList</a>&lt;? extends <a href=\"" +
            "../typeannos/WldA.html\" title=\"annotation in typeannos\">@WldA" +
            "</a> java.lang.String&gt;&nbsp;l)</pre>"
        },
        {BUG_ID + FS + "typeannos" + FS + "BoundTest.html",
            "<pre><a href=\"../typeannos/MyList.html\" title=\"class in " +
            "typeannos\">MyList</a>&lt;? super <a href=\"../typeannos/WldA.html\" " +
            "title=\"annotation in typeannos\">@WldA</a> java.lang.String&gt;" +
            "&nbsp;returnWcSuper()</pre>"
        },
        {BUG_ID + FS + "typeannos" + FS + "BoundWithValue.html",
            "<pre>void&nbsp;wcSuper(<a href=\"../typeannos/MyList.html\" title=\"" +
            "class in typeannos\">MyList</a>&lt;? super <a href=\"../typeannos/" +
            "WldB.html\" title=\"annotation in typeannos\">@WldB</a>(<a href=\"" +
            "../typeannos/WldB.html#value()\">value</a>=\"m\") java.lang." +
            "String&gt;&nbsp;l)</pre>"
        },
        {BUG_ID + FS + "typeannos" + FS + "BoundWithValue.html",
            "<pre><a href=\"../typeannos/MyList.html\" title=\"class in " +
            "typeannos\">MyList</a>&lt;? extends <a href=\"../typeannos/WldB." +
            "html\" title=\"annotation in typeannos\">@WldB</a>(<a href=\"../" +
            "typeannos/WldB.html#value()\">value</a>=\"m\") java.lang.String" +
            "&gt;&nbsp;returnWcExtends()</pre>"
        },

        // Test for receiver annotations (Receivers.java).
        {BUG_ID + FS + "typeannos" + FS + "DefaultUnmodified.html",
            "<pre>void&nbsp;withException(<a href=\"../typeannos/RcvrA.html\" " +
            "title=\"annotation in typeannos\">@RcvrA</a>&nbsp;" +
            "DefaultUnmodified&nbsp;this)" + NL + "            throws java." +
            "lang.Exception</pre>"
        },
        {BUG_ID + FS + "typeannos" + FS + "DefaultUnmodified.html",
            "<pre>java.lang.String&nbsp;nonVoid(<a href=\"../typeannos/RcvrA." +
            "html\" title=\"annotation in typeannos\">@RcvrA</a> <a href=\"../" +
            "typeannos/RcvrB.html\" title=\"annotation in typeannos\">@RcvrB" +
            "</a>(<a href=\"../typeannos/RcvrB.html#value()\">value</a>=\"m\")" +
            "&nbsp;DefaultUnmodified&nbsp;this)</pre>"
        },
        {BUG_ID + FS + "typeannos" + FS + "DefaultUnmodified.html",
            "<pre>&lt;T extends java.lang.Runnable&gt;&nbsp;void&nbsp;accept(" +
            "<a href=\"../typeannos/RcvrA.html\" title=\"annotation in " +
            "typeannos\">@RcvrA</a>&nbsp;DefaultUnmodified&nbsp;this," + NL +
            "                                           T&nbsp;r)" + NL +
            "                                    throws java.lang.Exception</pre>"
        },
        {BUG_ID + FS + "typeannos" + FS + "PublicModified.html",
            "<pre>public final&nbsp;java.lang.String&nbsp;nonVoid(<a href=\"" +
            "../typeannos/RcvrA.html\" title=\"annotation in typeannos\">" +
            "@RcvrA</a>&nbsp;PublicModified&nbsp;this)</pre>"
        },
        {BUG_ID + FS + "typeannos" + FS + "PublicModified.html",
            "<pre>public final&nbsp;&lt;T extends java.lang.Runnable&gt;&nbsp;" +
            "void&nbsp;accept(<a href=\"../typeannos/RcvrA.html\" title=\"" +
            "annotation in typeannos\">@RcvrA</a>&nbsp;PublicModified&nbsp;this," + NL +
            "                                                        T&nbsp;r)" + NL +
            "                                                 throws java.lang.Exception</pre>"
        },
        {BUG_ID + FS + "typeannos" + FS + "WithValue.html",
            "<pre>&lt;T extends java.lang.Runnable&gt;&nbsp;void&nbsp;accept(" +
            "<a href=\"../typeannos/RcvrB.html\" title=\"annotation in " +
            "typeannos\">@RcvrB</a>(<a href=\"../typeannos/RcvrB.html#value()\">" +
            "value</a>=\"m\")&nbsp;WithValue&nbsp;this," + NL +
            "                                           T&nbsp;r)" + NL +
            "                                    throws java.lang.Exception</pre>"
        },
        {BUG_ID + FS + "typeannos" + FS + "WithFinal.html",
            "<pre>java.lang.String&nbsp;nonVoid(<a href=\"../typeannos/RcvrB." +
            "html\" title=\"annotation in typeannos\">@RcvrB</a>(<a href=\"../" +
            "typeannos/RcvrB.html#value()\">value</a>=\"m\")&nbsp;WithFinal" +
            "&nbsp;this)</pre>"
        },
        {BUG_ID + FS + "typeannos" + FS + "WithBody.html",
            "<pre>void&nbsp;field(<a href=\"../typeannos/RcvrA.html\" title=\"" +
            "annotation in typeannos\">@RcvrA</a>&nbsp;WithBody&nbsp;this)</pre>"
        },
        {BUG_ID + FS + "typeannos" + FS + "Generic2.html",
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
        run(tester, ARGS, TEST, NEGATED_TEST);
        tester.printSummary();
    }

    /**
     * {@inheritDoc}
     */
    public String getBugId() {
        return BUG_ID;
    }

    /**
     * {@inheritDoc}
     */
    public String getBugName() {
        return getClass().getName();
    }
}
