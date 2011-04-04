/*
 * Copyright (c) 2003, 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4789689 4905985 4927164 4827184 4993906 5004549 7025314 7010344
 * @summary  Run Javadoc on a set of source files that demonstrate new
 *           language features.  Check the output to ensure that the new
 *           language features are properly documented.
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester TestNewLanguageFeatures
 * @run main TestNewLanguageFeatures
 */

public class TestNewLanguageFeatures extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4789689-4905985-4927164-4827184-4993906";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-use", "-source", "1.5", "-sourcepath", SRC_DIR, "pkg", "pkg1", "pkg2"
    };

    //Input for string search tests.
    private static final String[][] TEST =
        {
            //=================================
            // ENUM TESTING
            //=================================
            //Make sure enum header is correct.
            {BUG_ID + FS + "pkg" + FS + "Coin.html", "Enum Coin</h2>"},
            //Make sure enum signature is correct.
            {BUG_ID + FS + "pkg" + FS + "Coin.html", "<pre>public enum " +
                     "<span class=\"strong\">Coin</span>" + NL +
                     "extends java.lang.Enum&lt;<a href=\"../pkg/Coin.html\" " +
                     "title=\"enum in pkg\">Coin</a>&gt;</pre>"
            },
            //Check for enum constant section
            {BUG_ID + FS + "pkg" + FS + "Coin.html", "<caption><span>Enum Constants" +
                     "</span><span class=\"tabEnd\">&nbsp;</span></caption>"},
            //Detail for enum constant
            {BUG_ID + FS + "pkg" + FS + "Coin.html",
                "<strong><a href=\"../pkg/Coin.html#Dime\">Dime</a></strong>"},
            //Automatically insert documentation for values() and valueOf().
            {BUG_ID + FS + "pkg" + FS + "Coin.html",
                "Returns an array containing the constants of this enum type,"},
            {BUG_ID + FS + "pkg" + FS + "Coin.html",
                "Returns the enum constant of this type with the specified name"},
            {BUG_ID + FS + "pkg" + FS + "Coin.html", "for (Coin c : Coin.values())"},
            {BUG_ID + FS + "pkg" + FS + "Coin.html", "Overloaded valueOf() method has correct documentation."},
            {BUG_ID + FS + "pkg" + FS + "Coin.html", "Overloaded values method  has correct documentation."},

            //=================================
            // TYPE PARAMETER TESTING
            //=================================
            //Make sure the header is correct.
            {BUG_ID + FS + "pkg" + FS + "TypeParameters.html",
                "Class TypeParameters&lt;E&gt;</h2>"},
            //Check class type parameters section.
            {BUG_ID + FS + "pkg" + FS + "TypeParameters.html",
                "<dt><span class=\"strong\">Type Parameters:</span></dt><dd><code>E</code> - " +
                "the type parameter for this class."},
            //Type parameters in @see/@link
            {BUG_ID + FS + "pkg" + FS + "TypeParameters.html",
                "<dl><dt><span class=\"strong\">See Also:</span></dt><dd>" +
                "<a href=\"../pkg/TypeParameters.html\" title=\"class in pkg\">" +
                "<code>TypeParameters</code></a></dd></dl>"},
            //Method that uses class type parameter.
            {BUG_ID + FS + "pkg" + FS + "TypeParameters.html",
                "(<a href=\"../pkg/TypeParameters.html\" title=\"type " +
                    "parameter in TypeParameters\">E</a>&nbsp;param)"},
            //Method type parameter section.
            {BUG_ID + FS + "pkg" + FS + "TypeParameters.html",
                "<span class=\"strong\">Type Parameters:</span></dt><dd><code>T</code> - This is the first " +
                    "type parameter.</dd><dd><code>V</code> - This is the second type " +
                    "parameter."},
            //Signature of method with type parameters
            {BUG_ID + FS + "pkg" + FS + "TypeParameters.html",
                "public&nbsp;&lt;T extends java.util.List,V&gt;&nbsp;" +
                "java.lang.String[]&nbsp;methodThatHasTypeParameters"},
            //Wildcard testing.
            {BUG_ID + FS + "pkg" + FS + "Wildcards.html",
                "<a href=\"../pkg/TypeParameters.html\" title=\"class in pkg\">" +
                "TypeParameters</a>&lt;? super java.lang.String&gt;&nbsp;a"},
            {BUG_ID + FS + "pkg" + FS + "Wildcards.html",
                "<a href=\"../pkg/TypeParameters.html\" title=\"class in pkg\">" +
                "TypeParameters</a>&lt;? extends java.lang.StringBuffer&gt;&nbsp;b"},
            {BUG_ID + FS + "pkg" + FS + "Wildcards.html",
                "<a href=\"../pkg/TypeParameters.html\" title=\"class in pkg\">" +
                    "TypeParameters</a>&nbsp;c"},
            //Bad type parameter warnings.
            {WARNING_OUTPUT, "warning - @param argument " +
                "\"<BadClassTypeParam>\" is not a type parameter name."},
            {WARNING_OUTPUT, "warning - @param argument " +
                "\"<BadMethodTypeParam>\" is not a type parameter name."},

            //Signature of subclass that has type parameters.
            {BUG_ID + FS + "pkg" + FS + "TypeParameterSubClass.html",
                "<pre>public class <span class=\"strong\">TypeParameterSubClass&lt;T extends " +
                "java.lang.String&gt;</span>" + NL + "extends " +
                "<a href=\"../pkg/TypeParameterSuperClass.html\" title=\"class in pkg\">" +
                "TypeParameterSuperClass</a>&lt;T&gt;</pre>"},

            //Interface generic parameter substitution
            //Signature of subclass that has type parameters.
            {BUG_ID + FS + "pkg" + FS + "TypeParameters.html",
                "<dl>" + NL + "<dt>All Implemented Interfaces:</dt>" + NL +
                "<dd><a href=\"../pkg/SubInterface.html\" title=\"interface in pkg\">" +
                "SubInterface</a>&lt;E&gt;, <a href=\"../pkg/SuperInterface.html\" " +
                "title=\"interface in pkg\">SuperInterface</a>&lt;E&gt;</dd>" + NL +
                "</dl>"},
            {BUG_ID + FS + "pkg" + FS + "SuperInterface.html",
                "<dl>" + NL + "<dt>All Known Subinterfaces:</dt>" + NL +
                "<dd><a href=\"../pkg/SubInterface.html\" title=\"interface in pkg\">" +
                "SubInterface</a>&lt;V&gt;</dd>" + NL + "</dl>"},
            {BUG_ID + FS + "pkg" + FS + "SubInterface.html",
                "<dl>" + NL + "<dt>All Superinterfaces:</dt>" + NL +
                "<dd><a href=\"../pkg/SuperInterface.html\" title=\"interface in pkg\">" +
                "SuperInterface</a>&lt;V&gt;</dd>" + NL + "</dl>"},

            //=================================
            // VAR ARG TESTING
            //=================================
            {BUG_ID + FS + "pkg" + FS + "VarArgs.html", "(int...&nbsp;i)"},
            {BUG_ID + FS + "pkg" + FS + "VarArgs.html", "(int[][]...&nbsp;i)"},
            {BUG_ID + FS + "pkg" + FS + "VarArgs.html", "(int[]...)"},
            {BUG_ID + FS + "pkg" + FS + "VarArgs.html",
                "<a href=\"../pkg/TypeParameters.html\" title=\"class in pkg\">" +
                "TypeParameters</a>...&nbsp;t"},

            //=================================
            // ANNOTATION TYPE TESTING
            //=================================
            //Make sure the summary links are correct.
            {BUG_ID + FS + "pkg" + FS + "AnnotationType.html",
                "<li>Summary:&nbsp;</li>" + NL +
                "<li><a href=\"#annotation_type_required_element_summary\">" +
                "Required</a>&nbsp;|&nbsp;</li>" + NL + "<li>" +
                "<a href=\"#annotation_type_optional_element_summary\">Optional</a></li>"},
            //Make sure the detail links are correct.
            {BUG_ID + FS + "pkg" + FS + "AnnotationType.html",
                "<li>Detail:&nbsp;</li>" + NL +
                "<li><a href=\"#annotation_type_element_detail\">Element</a></li>"},
            //Make sure the heading is correct.
            {BUG_ID + FS + "pkg" + FS + "AnnotationType.html",
                "Annotation Type AnnotationType</h2>"},
            //Make sure the signature is correct.
            {BUG_ID + FS + "pkg" + FS + "AnnotationType.html",
                "public @interface <span class=\"strong\">AnnotationType</span>"},
            //Make sure member summary headings are correct.
            {BUG_ID + FS + "pkg" + FS + "AnnotationType.html",
                "<h3>Required Element Summary</h3>"},
            {BUG_ID + FS + "pkg" + FS + "AnnotationType.html",
                "<h3>Optional Element Summary</h3>"},
            //Make sure element detail heading is correct
            {BUG_ID + FS + "pkg" + FS + "AnnotationType.html",
                "Element Detail"},
            //Make sure default annotation type value is printed when necessary.
            {BUG_ID + FS + "pkg" + FS + "AnnotationType.html",
                "<dl>" + NL + "<dt>Default:</dt>" + NL + "<dd>\"unknown\"</dd>" + NL +
                "</dl>"},

            //=================================
            // ANNOTATION TYPE USAGE TESTING
            //=================================

            //PACKAGE
            {BUG_ID + FS + "pkg" + FS + "package-summary.html",
                "<a href=\"../pkg/AnnotationType.html\" title=\"annotation in pkg\">@AnnotationType</a>(<a href=\"../pkg/AnnotationType.html#optional()\">optional</a>=\"Package Annotation\"," + NL +
                "                <a href=\"../pkg/AnnotationType.html#required()\">required</a>=1994)"},

            //CLASS
            {BUG_ID + FS + "pkg" + FS + "AnnotationTypeUsage.html",
                "<pre><a href=\"../pkg/AnnotationType.html\" " +
                "title=\"annotation in pkg\">@AnnotationType</a>(" +
                "<a href=\"../pkg/AnnotationType.html#optional()\">optional</a>" +
                "=\"Class Annotation\"," + NL +
                "                <a href=\"../pkg/AnnotationType.html#required()\">" +
                "required</a>=1994)" + NL + "public class <span class=\"strong\">" +
                "AnnotationTypeUsage</span>" + NL + "extends java.lang.Object</pre>"},

            //FIELD
            {BUG_ID + FS + "pkg" + FS + "AnnotationTypeUsage.html",
                "<pre><a href=\"../pkg/AnnotationType.html\" " +
                "title=\"annotation in pkg\">@AnnotationType</a>(" +
                "<a href=\"../pkg/AnnotationType.html#optional()\">optional</a>" +
                "=\"Field Annotation\"," + NL +
                "                <a href=\"../pkg/AnnotationType.html#required()\">" +
                "required</a>=1994)" + NL + "public&nbsp;int field</pre>"},

            //CONSTRUCTOR
            {BUG_ID + FS + "pkg" + FS + "AnnotationTypeUsage.html",
                "<pre><a href=\"../pkg/AnnotationType.html\" " +
                "title=\"annotation in pkg\">@AnnotationType</a>(" +
                "<a href=\"../pkg/AnnotationType.html#optional()\">optional</a>" +
                "=\"Constructor Annotation\"," + NL +
                "                <a href=\"../pkg/AnnotationType.html#required()\">" +
                "required</a>=1994)" + NL + "public&nbsp;AnnotationTypeUsage()</pre>"},

            //METHOD
            {BUG_ID + FS + "pkg" + FS + "AnnotationTypeUsage.html",
                "<pre><a href=\"../pkg/AnnotationType.html\" " +
                "title=\"annotation in pkg\">@AnnotationType</a>(" +
                "<a href=\"../pkg/AnnotationType.html#optional()\">optional</a>" +
                "=\"Method Annotation\"," + NL +
                "                <a href=\"../pkg/AnnotationType.html#required()\">" +
                "required</a>=1994)" + NL + "public&nbsp;void&nbsp;method()</pre>"},

            //METHOD PARAMS
            {BUG_ID + FS + "pkg" + FS + "AnnotationTypeUsage.html",
                "<pre>public&nbsp;void&nbsp;methodWithParams(" +
                "<a href=\"../pkg/AnnotationType.html\" title=\"annotation in pkg\">" +
                "@AnnotationType</a>(<a href=\"../pkg/AnnotationType.html#optional()\">" +
                "optional</a>=\"Parameter Annotation\",<a " +
                "href=\"../pkg/AnnotationType.html#required()\">required</a>=1994)" + NL +
                "                    int&nbsp;documented," + NL +
                "                    int&nbsp;undocmented)</pre>"},

            //CONSTRUCTOR PARAMS
            {BUG_ID + FS + "pkg" + FS + "AnnotationTypeUsage.html",
                "<pre>public&nbsp;AnnotationTypeUsage(<a " +
                "href=\"../pkg/AnnotationType.html\" title=\"annotation in pkg\">" +
                "@AnnotationType</a>(<a href=\"../pkg/AnnotationType.html#optional()\">" +
                "optional</a>=\"Constructor Param Annotation\",<a " +
                "href=\"../pkg/AnnotationType.html#required()\">required</a>=1994)" + NL +
                "                   int&nbsp;documented," + NL +
                "                   int&nbsp;undocmented)</pre>"},

            //=================================
            // ANNOTATION TYPE USAGE TESTING (All Different Types).
            //=================================

            //Integer
            {BUG_ID + FS + "pkg1" + FS + "B.html",
                "<a href=\"../pkg1/A.html#d()\">d</a>=3.14,"},

            //Double
            {BUG_ID + FS + "pkg1" + FS + "B.html",
                "<a href=\"../pkg1/A.html#d()\">d</a>=3.14,"},

            //Boolean
            {BUG_ID + FS + "pkg1" + FS + "B.html",
                "<a href=\"../pkg1/A.html#b()\">b</a>=true,"},

            //String
            {BUG_ID + FS + "pkg1" + FS + "B.html",
                "<a href=\"../pkg1/A.html#s()\">s</a>=\"sigh\","},

            //Class
            {BUG_ID + FS + "pkg1" + FS + "B.html",
                "<a href=\"../pkg1/A.html#c()\">c</a>=<a href=\"../pkg2/Foo.html\" title=\"class in pkg2\">Foo.class</a>,"},

            //Bounded Class
            {BUG_ID + FS + "pkg1" + FS + "B.html",
                "<a href=\"../pkg1/A.html#w()\">w</a>=<a href=\"../pkg/TypeParameterSubClass.html\" title=\"class in pkg\">TypeParameterSubClass.class</a>,"},

            //Enum
            {BUG_ID + FS + "pkg1" + FS + "B.html",
                "<a href=\"../pkg1/A.html#e()\">e</a>=<a href=\"../pkg/Coin.html#Penny\">Penny</a>,"},

            //Annotation Type
            {BUG_ID + FS + "pkg1" + FS + "B.html",
                "<a href=\"../pkg1/A.html#a()\">a</a>=<a href=\"../pkg/AnnotationType.html\" title=\"annotation in pkg\">@AnnotationType</a>(<a href=\"../pkg/AnnotationType.html#optional()\">optional</a>=\"foo\",<a href=\"../pkg/AnnotationType.html#required()\">required</a>=1994),"},

            //String Array
            {BUG_ID + FS + "pkg1" + FS + "B.html",
                "<a href=\"../pkg1/A.html#sa()\">sa</a>={\"up\",\"down\"},"},

            //Primitive
            {BUG_ID + FS + "pkg1" + FS + "B.html",
                "<a href=\"../pkg1/A.html#primitiveClassTest()\">primitiveClassTest</a>=boolean.class,"},

            //XXX:  Add array test case after this if fixed:
            //5020899: Incorrect internal representation of class-valued annotation elements

            //Make sure that annotations are surrounded by <pre> and </pre>
            {BUG_ID + FS + "pkg1" + FS + "B.html",
                "<pre><a href=\"../pkg1/A.html\" title=\"annotation in pkg1\">@A</a>"},
            {BUG_ID + FS + "pkg1" + FS + "B.html",
                "public interface <span class=\"strong\">B</span></pre>"},


            //==============================================================
            // Handle multiple bounds.
            //==============================================================
            {BUG_ID + FS + "pkg" + FS + "MultiTypeParameters.html",
                "public&nbsp;&lt;T extends java.lang.Number & java.lang.Runnable&gt;&nbsp;T&nbsp;foo(T&nbsp;t)"},

            //==============================================================
            // Test Class-Use Documenation for Type Parameters.
            //==============================================================

            //ClassUseTest1: <T extends Foo & Foo2>
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "Foo.html",
                     "<caption><span>Classes in <a href=\"../../pkg2/" +
                     "package-summary.html\">pkg2</a> with type parameters of " +
                     "type <a href=\"../../pkg2/Foo.html\" title=\"class in pkg2\">" +
                     "Foo</a></span><span class=\"tabEnd\">&nbsp;</span></caption>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "Foo.html",
                     "<td class=\"colLast\"><code><strong><a href=\"../../pkg2/ClassUseTest1.html\" " +
                     "title=\"class in pkg2\">ClassUseTest1</a>&lt;T extends " +
                     "<a href=\"../../pkg2/Foo.html\" title=\"class in pkg2\">Foo" +
                     "</a> & <a href=\"../../pkg2/Foo2.html\" title=\"interface in pkg2\">" +
                     "Foo2</a>&gt;</strong></code>&nbsp;</td>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "Foo.html",
                     "<caption><span>Methods in <a href=\"../../pkg2/" +
                     "package-summary.html\">pkg2</a> with type parameters of " +
                     "type <a href=\"../../pkg2/Foo.html\" title=\"class in " +
                     "pkg2\">Foo</a></span><span class=\"tabEnd\">&nbsp;</span></caption>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "Foo.html",
                     "<td class=\"colLast\"><span class=\"strong\">ClassUseTest1." +
                     "</span><code><strong><a href=\"../../pkg2/" +
                     "ClassUseTest1.html#method(T)\">method</a></strong>" +
                     "(T&nbsp;t)</code>&nbsp;</td>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "Foo.html",
                     "<caption><span>Fields in <a href=\"../../pkg2/" +
                     "package-summary.html\">pkg2</a> with type parameters of " +
                     "type <a href=\"../../pkg2/Foo.html\" title=\"class in pkg2\">" +
                     "Foo</a></span><span class=\"tabEnd\">&nbsp;</span></caption>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "Foo.html",
                     "td class=\"colFirst\"><code><a href=\"../../pkg2/" +
                     "ParamTest.html\" title=\"class in pkg2\">ParamTest</a>" +
                     "&lt;<a href=\"../../pkg2/Foo.html\" title=\"class in pkg2\"" +
                     ">Foo</a>&gt;</code></td>"
            },

            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "ParamTest.html",
                     "<caption><span>Fields in <a href=\"../../pkg2/" +
                     "package-summary.html\">pkg2</a> declared as <a href=\"../" +
                     "../pkg2/ParamTest.html\" title=\"class in pkg2\">ParamTest" +
                     "</a></span><span class=\"tabEnd\">&nbsp;</span></caption>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "ParamTest.html",
                     "<td class=\"colFirst\"><code><a href=\"../../pkg2/" +
                     "ParamTest.html\" title=\"class in pkg2\">ParamTest</a>&lt;<a " +
                     "href=\"../../pkg2/Foo.html\" title=\"class in pkg2\">Foo</a" +
                     ">&gt;</code></td>"
            },

           {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "Foo2.html",
                    "<caption><span>Classes in <a href=\"../../pkg2/" +
                    "package-summary.html\">pkg2</a> with type parameters of " +
                    "type <a href=\"../../pkg2/Foo2.html\" title=\"interface " +
                    "in pkg2\">Foo2</a></span><span class=\"tabEnd\">&nbsp;" +
                    "</span></caption>"
           },
           {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "Foo2.html",
                    "<td class=\"colLast\"><code><strong><a href=\"../../pkg2/ClassUseTest1.html\" " +
                     "title=\"class in pkg2\">ClassUseTest1</a>&lt;T extends " +
                     "<a href=\"../../pkg2/Foo.html\" title=\"class in pkg2\">Foo" +
                     "</a> & <a href=\"../../pkg2/Foo2.html\" title=\"interface in pkg2\">" +
                     "Foo2</a>&gt;</strong></code>&nbsp;</td>"
           },
           {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "Foo2.html",
                    "<caption><span>Methods in <a href=\"../../pkg2/" +
                    "package-summary.html\">pkg2</a> with type parameters of " +
                    "type <a href=\"../../pkg2/Foo2.html\" title=\"interface " +
                    "in pkg2\">Foo2</a></span><span class=\"tabEnd\">&nbsp;" +
                    "</span></caption>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "Foo2.html",
                     "<td class=\"colLast\"><span class=\"strong\">" +
                     "ClassUseTest1.</span><code><strong><a href=\"../../" +
                     "pkg2/ClassUseTest1.html#method(T)\">method</a></strong>" +
                     "(T&nbsp;t)</code>&nbsp;</td>"
            },

            //ClassUseTest2: <T extends ParamTest<Foo3>>
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "ParamTest.html",
                     "<caption><span>Classes in <a href=\"../../pkg2/" +
                     "package-summary.html\">pkg2</a> with type parameters of " +
                     "type <a href=\"../../pkg2/ParamTest.html\" title=\"class " +
                     "in pkg2\">ParamTest</a></span><span class=\"tabEnd\">" +
                     "&nbsp;</span></caption>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "ParamTest.html",
                     "<td class=\"colLast\"><code><strong><a href=\"../../pkg2/ClassUseTest2.html\" " +
                     "title=\"class in pkg2\">ClassUseTest2</a>&lt;T extends " +
                     "<a href=\"../../pkg2/ParamTest.html\" title=\"class in pkg2\">" +
                     "ParamTest</a>&lt;<a href=\"../../pkg2/Foo3.html\" title=\"class in pkg2\">" +
                     "Foo3</a>&gt;&gt;</strong></code>&nbsp;</td>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "ParamTest.html",
                     "<caption><span>Methods in <a href=\"../../pkg2/" +
                     "package-summary.html\">pkg2</a> with type parameters of " +
                     "type <a href=\"../../pkg2/ParamTest.html\" title=\"class " +
                     "in pkg2\">ParamTest</a></span><span class=\"tabEnd\">" +
                     "&nbsp;</span></caption>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "ParamTest.html",
                     "<td class=\"colLast\"><span class=\"strong\">ClassUseTest2." +
                     "</span><code><strong><a href=\"../../pkg2/" +
                     "ClassUseTest2.html#method(T)\">method</a></strong>" +
                     "(T&nbsp;t)</code>&nbsp;</td>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "ParamTest.html",
                     "<caption><span>Fields in <a href=\"../../pkg2/" +
                     "package-summary.html\">pkg2</a> declared as <a href=\"../" +
                     "../pkg2/ParamTest.html\" title=\"class in pkg2\">ParamTest" +
                     "</a></span><span class=\"tabEnd\">&nbsp;</span></caption>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "ParamTest.html",
                     "<td class=\"colFirst\"><code><a href=\"../../pkg2/" +
                     "ParamTest.html\" title=\"class in pkg2\">ParamTest</a>" +
                     "&lt;<a href=\"../../pkg2/Foo.html\" title=\"class in pkg2\">" +
                     "Foo</a>&gt;</code></td>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "ParamTest.html",
                     "<caption><span>Methods in <a href=\"../../pkg2/" +
                     "package-summary.html\">pkg2</a> with type parameters of " +
                     "type <a href=\"../../pkg2/ParamTest.html\" title=\"class " +
                     "in pkg2\">ParamTest</a></span><span class=\"tabEnd\">" +
                     "&nbsp;</span></caption>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "ParamTest.html",
                     "<td class=\"colFirst\"><code>&lt;T extends <a href=\"../" +
                     "../pkg2/ParamTest.html\" title=\"class in pkg2\">ParamTest" +
                     "</a>&lt;<a href=\"../../pkg2/Foo3.html\" title=\"class in " +
                     "pkg2\">Foo3</a>&gt;&gt;&nbsp;<br><a href=\"../../pkg2/" +
                     "ParamTest.html\" title=\"class in pkg2\">ParamTest</a>" +
                     "&lt;<a href=\"../../pkg2/Foo3.html\" title=\"class in " +
                     "pkg2\">Foo3</a>&gt;</code></td>"
            },

            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "Foo3.html",
                     "<caption><span>Classes in <a href=\"../../pkg2/" +
                     "package-summary.html\">pkg2</a> with type parameters of " +
                     "type <a href=\"../../pkg2/Foo3.html\" title=\"class in pkg2\">" +
                     "Foo3</a></span><span class=\"tabEnd\">&nbsp;</span></caption>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "Foo3.html",
                     "<td class=\"colLast\"><code><strong><a href=\"../../pkg2/ClassUseTest2.html\" " +
                     "title=\"class in pkg2\">ClassUseTest2</a>&lt;T extends " +
                     "<a href=\"../../pkg2/ParamTest.html\" title=\"class in pkg2\">" +
                     "ParamTest</a>&lt;<a href=\"../../pkg2/Foo3.html\" title=\"class in pkg2\">" +
                     "Foo3</a>&gt;&gt;</strong></code>&nbsp;</td>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "Foo3.html",
                     "<caption><span>Methods in <a href=\"../../pkg2/" +
                     "package-summary.html\">pkg2</a> with type parameters of " +
                     "type <a href=\"../../pkg2/Foo3.html\" title=\"class in " +
                     "pkg2\">Foo3</a></span><span class=\"tabEnd\">&nbsp;" +
                     "</span></caption>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "Foo3.html",
                     "<td class=\"colLast\"><span class=\"strong\">ClassUseTest2." +
                     "</span><code><strong><a href=\"../../pkg2/" +
                     "ClassUseTest2.html#method(T)\">method</a></strong>" +
                     "(T&nbsp;t)</code>&nbsp;</td>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "Foo3.html",
                     "<caption><span>Methods in <a href=\"../../pkg2/" +
                     "package-summary.html\">pkg2</a> that return types with " +
                     "arguments of type <a href=\"../../pkg2/Foo3.html\" title" +
                     "=\"class in pkg2\">Foo3</a></span><span class=\"tabEnd\">" +
                     "&nbsp;</span></caption>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "Foo3.html",
                     "<td class=\"colFirst\"><code>&lt;T extends <a href=\"../../" +
                     "pkg2/ParamTest.html\" title=\"class in pkg2\">ParamTest</a>&lt;" +
                     "<a href=\"../../pkg2/Foo3.html\" title=\"class in pkg2\">Foo3" +
                     "</a>&gt;&gt;&nbsp;<br><a href=\"../../pkg2/ParamTest.html\" " +
                     "title=\"class in pkg2\">ParamTest</a>&lt;<a href=\"../../pkg2/" +
                     "Foo3.html\" title=\"class in pkg2\">Foo3</a>&gt;</code></td>"
            },

            //ClassUseTest3: <T extends ParamTest2<List<? extends Foo4>>>
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "ParamTest2.html",
                     "<caption><span>Classes in <a href=\"../../pkg2/" +
                     "package-summary.html\">pkg2</a> with type parameters of " +
                     "type <a href=\"../../pkg2/ParamTest2.html\" title=\"class " +
                     "in pkg2\">ParamTest2</a></span><span class=\"tabEnd\">" +
                     "&nbsp;</span></caption>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "ParamTest2.html",
                     "<td class=\"colLast\"><code><strong><a href=\"../../pkg2/ClassUseTest3.html\" " +
                     "title=\"class in pkg2\">ClassUseTest3</a>&lt;T extends " +
                     "<a href=\"../../pkg2/ParamTest2.html\" title=\"class in pkg2\">" +
                     "ParamTest2</a>&lt;java.util.List&lt;? extends " +
                     "<a href=\"../../pkg2/Foo4.html\" title=\"class in pkg2\">" +
                     "Foo4</a>&gt;&gt;&gt;</strong></code>&nbsp;</td>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "ParamTest2.html",
                     "<caption><span>Methods in <a href=\"../../pkg2/" +
                     "package-summary.html\">pkg2</a> with type parameters of " +
                     "type <a href=\"../../pkg2/ParamTest2.html\" title=\"class " +
                     "in pkg2\">ParamTest2</a></span><span class=\"tabEnd\">" +
                     "&nbsp;</span></caption>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "ParamTest2.html",
                     "<td class=\"colLast\"><span class=\"strong\">ClassUseTest3" +
                     ".</span><code><strong><a href=\"../../pkg2/ClassUseTest3." +
                     "html#method(T)\">method</a></strong>(T&nbsp;t)</code>&nbsp;</td>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "ParamTest2.html",
                     "<td class=\"colFirst\"><code>&lt;T extends <a href=\"../" +
                     "../pkg2/ParamTest2.html\" title=\"class in pkg2\">" +
                     "ParamTest2</a>&lt;java.util.List&lt;? extends <a href=\".." +
                     "/../pkg2/Foo4.html\" title=\"class in pkg2\">Foo4</a>&gt;" +
                     "&gt;&gt;&nbsp;<br><a href=\"../../pkg2/ParamTest2.html\" " +
                     "title=\"class in pkg2\">ParamTest2</a>&lt;java.util.List" +
                     "&lt;? extends <a href=\"../../pkg2/Foo4.html\" title=\"" +
                     "class in pkg2\">Foo4</a>&gt;&gt;</code></td>"
            },

            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "Foo4.html",
                     "<caption><span>Classes in <a href=\"../../pkg2/" +
                     "package-summary.html\">pkg2</a> with type parameters of " +
                     "type <a href=\"../../pkg2/Foo4.html\" title=\"class in " +
                     "pkg2\">Foo4</a></span><span class=\"tabEnd\">&nbsp;" +
                     "</span></caption>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "Foo4.html",
                     "<td class=\"colLast\"><code><strong><a href=\"../../pkg2/ClassUseTest3.html\" " +
                     "title=\"class in pkg2\">ClassUseTest3</a>&lt;T extends " +
                     "<a href=\"../../pkg2/ParamTest2.html\" title=\"class in pkg2\">" +
                     "ParamTest2</a>&lt;java.util.List&lt;? extends " +
                     "<a href=\"../../pkg2/Foo4.html\" title=\"class in pkg2\">" +
                     "Foo4</a>&gt;&gt;&gt;</strong></code>&nbsp;</td>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "Foo4.html",
                     "<caption><span>Methods in <a href=\"../../pkg2/" +
                     "package-summary.html\">pkg2</a> with type parameters of " +
                     "type <a href=\"../../pkg2/Foo4.html\" title=\"class in " +
                     "pkg2\">Foo4</a></span><span class=\"tabEnd\">&nbsp;</span></caption>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "Foo4.html",
                     "<td class=\"colLast\"><span class=\"strong\">ClassUseTest3." +
                     "</span><code><strong><a href=\"../../pkg2/ClassUseTest3." +
                     "html#method(T)\">method</a></strong>(T&nbsp;t)</code>" +
                     "&nbsp;</td>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "Foo4.html",
                     "<caption><span>Methods in <a href=\"../../pkg2/" +
                     "package-summary.html\">pkg2</a> that return types with " +
                     "arguments of type <a href=\"../../pkg2/Foo4.html\" " +
                     "title=\"class in pkg2\">Foo4</a></span><span class=\"" +
                     "tabEnd\">&nbsp;</span></caption>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "Foo4.html",
                     "<td class=\"colFirst\"><code>&lt;T extends <a href=\"../" +
                     "../pkg2/ParamTest2.html\" title=\"class in pkg2\">" +
                     "ParamTest2</a>&lt;java.util.List&lt;? extends <a href=\".." +
                     "/../pkg2/Foo4.html\" title=\"class in pkg2\">Foo4</a>&gt;" +
                     "&gt;&gt;&nbsp;<br><a href=\"../../pkg2/ParamTest2.html\" " +
                     "title=\"class in pkg2\">ParamTest2</a>&lt;java.util.List" +
                     "&lt;? extends <a href=\"../../pkg2/Foo4.html\" title=\"" +
                     "class in pkg2\">Foo4</a>&gt;&gt;</code></td>"
            },

            //Type parameters in constructor and method args
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "Foo4.html",
                     "<caption><span>Method parameters in <a href=\"../../pkg2/" +
                     "package-summary.html\">pkg2</a> with type arguments of " +
                     "type <a href=\"../../pkg2/Foo4.html\" title=\"class in " +
                     "pkg2\">Foo4</a></span><span class=\"tabEnd\">&nbsp;" +
                     "</span></caption>" + NL + "<tr>" + NL +
                     "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>" + NL +
                     "<th class=\"colLast\" scope=\"col\">Method and Description</th>" + NL +
                     "</tr>" + NL + "<tbody>" + NL + "<tr class=\"altColor\">" + NL +
                     "<td class=\"colFirst\"><code>void</code></td>" + NL +
                     "<td class=\"colLast\"><span class=\"strong\">ClassUseTest3." +
                     "</span><code><strong><a href=\"../../pkg2/ClassUseTest3." +
                     "html#method(java.util.Set)\">method</a></strong>(java." +
                     "util.Set&lt;<a href=\"../../pkg2/Foo4.html\" title=\"" +
                     "class in pkg2\">Foo4</a>&gt;&nbsp;p)</code>&nbsp;</td>" + NL +
                     "</tr>" + NL + "</tbody>"
            },
            {BUG_ID + FS + "pkg2" + FS + "class-use" + FS + "Foo4.html",
                     "<caption><span>Constructor parameters in <a href=\"../../" +
                     "pkg2/package-summary.html\">pkg2</a> with type arguments " +
                     "of type <a href=\"../../pkg2/Foo4.html\" title=\"class in " +
                     "pkg2\">Foo4</a></span><span class=\"tabEnd\">&nbsp;" +
                     "</span></caption>"
            },

            //=================================
            // Annotatation Type Usage
            //=================================
            {BUG_ID + FS + "pkg" + FS + "class-use" + FS + "AnnotationType.html",
                     "<caption><span>Packages with annotations of type <a href=\"" +
                     "../../pkg/AnnotationType.html\" title=\"annotation in pkg\">" +
                     "AnnotationType</a></span><span class=\"tabEnd\">&nbsp;" +
                     "</span></caption>"
            },

            {BUG_ID + FS + "pkg" + FS + "class-use" + FS + "AnnotationType.html",
                     "<caption><span>Classes in <a href=\"../../pkg/" +
                     "package-summary.html\">pkg</a> with annotations of type " +
                     "<a href=\"../../pkg/AnnotationType.html\" title=\"" +
                     "annotation in pkg\">AnnotationType</a></span><span class" +
                     "=\"tabEnd\">&nbsp;</span></caption>"
            },

            {BUG_ID + FS + "pkg" + FS + "class-use" + FS + "AnnotationType.html",
                     "<caption><span>Fields in <a href=\"../../pkg/" +
                     "package-summary.html\">pkg</a> with annotations of type " +
                     "<a href=\"../../pkg/AnnotationType.html\" title=\"annotation " +
                     "in pkg\">AnnotationType</a></span><span class=\"tabEnd\">" +
                     "&nbsp;</span></caption>"
            },

            {BUG_ID + FS + "pkg" + FS + "class-use" + FS + "AnnotationType.html",
                     "<caption><span>Methods in <a href=\"../../pkg/" +
                     "package-summary.html\">pkg</a> with annotations of type " +
                     "<a href=\"../../pkg/AnnotationType.html\" title=\"annotation " +
                     "in pkg\">AnnotationType</a></span><span class=\"tabEnd\">" +
                     "&nbsp;</span></caption>"
            },

            {BUG_ID + FS + "pkg" + FS + "class-use" + FS + "AnnotationType.html",
                     "<caption><span>Method parameters in <a href=\"../../pkg/" +
                     "package-summary.html\">pkg</a> with annotations of type " +
                     "<a href=\"../../pkg/AnnotationType.html\" title=\"annotation " +
                     "in pkg\">AnnotationType</a></span><span class=\"tabEnd\">" +
                     "&nbsp;</span></caption>"
            },

            {BUG_ID + FS + "pkg" + FS + "class-use" + FS + "AnnotationType.html",
                     "<caption><span>Constructors in <a href=\"../../pkg/" +
                     "package-summary.html\">pkg</a> with annotations of type " +
                     "<a href=\"../../pkg/AnnotationType.html\" title=\"annotation " +
                     "in pkg\">AnnotationType</a></span><span class=\"tabEnd\">" +
                     "&nbsp;</span></caption>"
            },

            {BUG_ID + FS + "pkg" + FS + "class-use" + FS + "AnnotationType.html",
                     "<caption><span>Constructor parameters in <a href=\"../../" +
                     "pkg/package-summary.html\">pkg</a> with annotations of " +
                     "type <a href=\"../../pkg/AnnotationType.html\" title=\"" +
                     "annotation in pkg\">AnnotationType</a></span><span class=\"" +
                     "tabEnd\">&nbsp;</span></caption>"
            },

            //=================================
            // TYPE PARAMETER IN INDEX
            //=================================
            {BUG_ID + FS + "index-all.html",
                "<span class=\"strong\"><a href=\"./pkg2/Foo.html#method(java.util.Vector)\">" +
                "method(Vector&lt;Object&gt;)</a></span>"
            },
            //=================================
            // TYPE PARAMETER IN INDEX
            //=================================
            {BUG_ID + FS + "index-all.html",
                "<span class=\"strong\"><a href=\"./pkg2/Foo.html#method(java.util.Vector)\">" +
                "method(Vector&lt;Object&gt;)</a></span>"
            },
        };
    private static final String[][] NEGATED_TEST = {
        //=================================
        // ENUM TESTING
        //=================================
        //NO constructor section
        {BUG_ID + FS + "pkg" + FS + "Coin.html", "<span class=\"strong\">Constructor Summary</span>"},
        //=================================
        // TYPE PARAMETER TESTING
        //=================================
        //No type parameters in class frame.
        {BUG_ID + FS + "allclasses-frame.html",
            "<a href=\"../pkg/TypeParameters.html\" title=\"class in pkg\">" +
                    "TypeParameters</a>&lt;<a href=\"../pkg/TypeParameters.html\" " +
                    "title=\"type parameter in TypeParameters\">E</a>&gt;"
        },

        //==============================================================
        // ANNOTATION TYPE USAGE TESTING (When @Documented is omitted)
        //===============================================================

        //CLASS
        {BUG_ID + FS + "pkg" + FS + "AnnotationTypeUsage.html",
            "<a href=\"../pkg/AnnotationTypeUndocumented.html\" title=\"annotation in pkg\">@AnnotationTypeUndocumented</a>(<a href=\"../pkg/AnnotationType.html#optional\">optional</a>=\"Class Annotation\"," + NL +
            "                <a href=\"../pkg/AnnotationType.html#required\">required</a>=1994)" + NL +
            "public class <strong>AnnotationTypeUsage</strong></dt><dt>extends java.lang.Object</dt>"},

        //FIELD
        {BUG_ID + FS + "pkg" + FS + "AnnotationTypeUsage.html",
            "<a href=\"../pkg/AnnotationTypeUndocumented.html\" title=\"annotation in pkg\">@AnnotationTypeUndocumented</a>(<a href=\"../pkg/AnnotationType.html#optional\">optional</a>=\"Field Annotation\"," + NL +
            "                <a href=\"../pkg/AnnotationType.html#required\">required</a>=1994)" + NL +
            "public int <strong>field</strong>"},

        //CONSTRUCTOR
        {BUG_ID + FS + "pkg" + FS + "AnnotationTypeUsage.html",
            "<a href=\"../pkg/AnnotationTypeUndocumented.html\" title=\"annotation in pkg\">@AnnotationTypeUndocumented</a>(<a href=\"../pkg/AnnotationType.html#optional\">optional</a>=\"Constructor Annotation\"," + NL +
            "                <a href=\"../pkg/AnnotationType.html#required\">required</a>=1994)" + NL +
            "public <strong>AnnotationTypeUsage</strong>()"},

        //METHOD
        {BUG_ID + FS + "pkg" + FS + "AnnotationTypeUsage.html",
            "<a href=\"../pkg/AnnotationTypeUndocumented.html\" title=\"annotation in pkg\">@AnnotationTypeUndocumented</a>(<a href=\"../pkg/AnnotationType.html#optional\">optional</a>=\"Method Annotation\"," + NL +
            "                <a href=\"../pkg/AnnotationType.html#required\">required</a>=1994)" + NL +
            "public void <strong>method</strong>()"},

        //=================================
        // Make sure annotation types do not
        // trigger this warning.
        //=================================
        {WARNING_OUTPUT,
            "Internal error: package sets don't match: [] with: null"
        },
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestNewLanguageFeatures tester = new TestNewLanguageFeatures();
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
