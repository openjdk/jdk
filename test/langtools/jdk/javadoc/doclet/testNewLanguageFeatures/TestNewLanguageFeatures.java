/*
 * Copyright (c) 2003, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4789689 4905985 4927164 4827184 4993906 5004549 7025314 7010344 8025633 8026567 8162363
 *           8175200 8186332 8182765 8196202 8187288 8173730 8215307
 * @summary  Run Javadoc on a set of source files that demonstrate new
 *           language features.  Check the output to ensure that the new
 *           language features are properly documented.
 * @library  ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    javadoc.tester.*
 * @run main TestNewLanguageFeatures
 */

import javadoc.tester.JavadocTester;

public class TestNewLanguageFeatures extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestNewLanguageFeatures tester = new TestNewLanguageFeatures();
        tester.runTests();
    }

    @Test
    public void test() {
        javadoc("-Xdoclint:none",
                "-d", "out",
                "-use",
                "-sourcepath", testSrc,
                "pkg", "pkg1", "pkg2");
        checkExit(Exit.OK);

        checkEnums();
        checkTypeParameters();
        checkVarArgs();
        checkAnnotationTypeUsage();
    }

    //=================================
    // ENUM TESTING
    //=================================
    void checkEnums() {
       checkOutput("pkg/Coin.html", true,
                // Make sure enum header is correct.
                "Enum Coin</h1>",
                // Make sure enum signature is correct.
                """
                    <pre>public enum <span class="type-name-label">Coin</span>
                    extends java.lang.Enum&lt;<a href="Coin.html" title="enum in pkg">Coin</a>&gt;</pre>""",
                // Check for enum constant section
                "<caption><span>Enum Constants</span></caption>",
                // Detail for enum constant
                """
                    <span class="member-name-link"><a href="#Dime">Dime</a></span>""",
                // Automatically insert documentation for values() and valueOf().
                "Returns an array containing the constants of this enum type,",
                "Returns the enum constant of this type with the specified name",
                "Overloaded valueOf() method has correct documentation.",
                "Overloaded values method  has correct documentation.",
                """
                    <div class="member-signature"><span class="modifiers">public static</span>&nbsp;\
                    <span class="return-type"><a href="Coin.html" title="enum in pkg">Coin</a></span\
                    >&nbsp;<span class="member-name">valueOf</span>&#8203;(<span class="parameters">\
                    java.lang.String&nbsp;name)</span></div>
                    <div class="block">Returns the enum constant of this type with the specified name.
                    The string must match <i>exactly</i> an identifier used to declare an
                    enum constant in this type.  (Extraneous whitespace characters are\s
                    not permitted.)</div>
                    <dl class="notes">
                    <dt>Parameters:</dt>
                    <dd><code>name</code> - the name of the enum constant to be returned.</dd>
                    <dt>Returns:</dt>
                    <dd>the enum constant with the specified name</dd>
                    <dt>Throws:</dt>
                    <dd><code>java.lang.IllegalArgumentException</code> - if this enum type has no constant with the specified name</dd>
                    <dd><code>java.lang.NullPointerException</code> - if the argument is null</dd>""");

        // NO constructor section
        checkOutput("pkg/Coin.html", false,
                "<h3>Constructor Summary</h3>");
    }

    //=================================
    // TYPE PARAMETER TESTING
    //=================================

    void checkTypeParameters() {
        checkOutput("pkg/TypeParameters.html", true,
                // Make sure the header is correct.
                "Class TypeParameters&lt;E&gt;</h1>",
                // Check class type parameters section.
                """
                    <dt>Type Parameters:</dt>
                    <dd><code>E</code> - the type parameter for this class.""",
                // Type parameters in @see/@link
                """
                    <dl class="notes">
                    <dt>See Also:</dt>
                    <dd><a href="TypeParameters.html" title="class in pkg"><code>TypeParameters</code></a></dd>
                    </dl>""",
                // Method that uses class type parameter.
                """
                    (<a href="TypeParameters.html" title="type parameter in TypeParameters">E</a>&nbsp;param)""",
                // Method type parameter section.
                """
                    <dt>Type Parameters:</dt>
                    <dd><code>T</code> - This is the first type parameter.</dd>
                    <dd><code>V</code> - This is the second type parameter.""",
                // Signature of method with type parameters
                """
                    <div class="member-signature"><span class="modifiers">public</span>&nbsp;<span c\
                    lass="type-parameters">&lt;T extends java.util.List,&#8203;
                    V&gt;</span>
                    <span class="return-type">java.lang.String[]</span>&nbsp;<span class="member-nam\
                    e">methodThatHasTypeParameters</span>&#8203;(<span class="parameters">T&nbsp;par\
                    am1,
                    V&nbsp;param2)</span></div>""",
                // Method that returns TypeParameters
                """
                    <td class="col-first"><code><a href="TypeParameters.html" title="type parameter in TypeParameters">E</a>[]</code></td>
                    <th class="col-second" scope="row"><code><span class="member-name-link"><a href=\
                    "#methodThatReturnsTypeParameterA(E%5B%5D)">methodThatReturnsTypeParameterA</a><\
                    /span>&#8203;(<a href="TypeParameters.html" title="type parameter in TypeParamet\
                    ers">E</a>[]&nbsp;e)</code>""",
                """
                    <div class="member-signature"><span class="modifiers">public</span>&nbsp;<span c\
                    lass="return-type"><a href="TypeParameters.html" title="type parameter in TypePa\
                    rameters">E</a>[]</span>&nbsp;<span class="member-name">methodThatReturnsTypePar\
                    ameterA</span>&#8203;(<span class="parameters"><a href="TypeParameters.html" tit\
                    le="type parameter in TypeParameters">E</a>[]&nbsp;e)</span></div>
                    """,
                """
                    <td class="col-first"><code>&lt;T extends java.lang.Object &amp; java.lang.Compa\
                    rable&lt;? super T&gt;&gt;<br>T</code></td>
                    <th class="col-second" scope="row"><code><span class="member-name-link"><a href=\
                    "#methodtThatReturnsTypeParametersB(java.util.Collection)">methodtThatReturnsTyp\
                    eParametersB</a></span>&#8203;(java.util.Collection&lt;? extends T&gt;&nbsp;coll\
                    )</code>""",
                """
                    <div class="block">Returns TypeParameters</div>
                    """,
                // Method takes a TypeVariable
                """
                    <td class="col-first"><code>&lt;X extends java.lang.Throwable&gt;<br><a href="Ty\
                    peParameters.html" title="type parameter in TypeParameters">E</a></code></td>
                    <th class="col-second" scope="row"><code><span class="member-name-link"><a href=\
                    "#orElseThrow(java.util.function.Supplier)">orElseThrow</a></span>&#8203;(java.u\
                    til.function.Supplier&lt;? extends X&gt;&nbsp;exceptionSupplier)</code>"""
                );

        checkOutput("pkg/Wildcards.html", true,
                // Wildcard testing.
                """
                    <a href="TypeParameters.html" title="class in pkg">TypeParameters</a>&lt;? super java.lang.String&gt;&nbsp;a""",
                """
                    <a href="TypeParameters.html" title="class in pkg">TypeParameters</a>&lt;? extends java.lang.StringBuffer&gt;&nbsp;b""",
                """
                    <a href="TypeParameters.html" title="class in pkg">TypeParameters</a>&nbsp;c""");

        checkOutput(Output.OUT, true,
                // Bad type parameter warnings.
                """
                    warning - @param argument "<BadClassTypeParam>" is not the name of a type parameter.""",
                """
                    warning - @param argument "<BadMethodTypeParam>" is not the name of a type parameter.""");

        // Signature of subclass that has type parameters.
        checkOutput("pkg/TypeParameterSubClass.html", true,
                """
                    <pre>public class <span class="type-name-label">TypeParameterSubClass&lt;T extends java.lang.String&gt;</span>
                    extends <a href="TypeParameterSuperClass.html" title="class in pkg">TypeParameterSuperClass</a>&lt;T&gt;</pre>""");

        // Interface generic parameter substitution
        // Signature of subclass that has type parameters.
        checkOutput("pkg/TypeParameters.html", true,
                """
                    <dl class="notes">
                    <dt>All Implemented Interfaces:</dt>
                    <dd><code><a href="SubInterface.html" title="interface in pkg">SubInterface</a>&\
                    lt;E&gt;</code>, <code><a href="SuperInterface.html" title="interface in pkg">Su\
                    perInterface</a>&lt;E&gt;</code></dd>
                    </dl>""");

        checkOutput("pkg/SuperInterface.html", true,
                """
                    <dl class="notes">
                    <dt>All Known Subinterfaces:</dt>
                    <dd><code><a href="SubInterface.html" title="interface in pkg">SubInterface</a>&lt;V&gt;</code></dd>
                    </dl>""");
        checkOutput("pkg/SubInterface.html", true,
                """
                    <dl class="notes">
                    <dt>All Superinterfaces:</dt>
                    <dd><code><a href="SuperInterface.html" title="interface in pkg">SuperInterface</a>&lt;V&gt;</code></dd>
                    </dl>""");

        //==============================================================
        // Handle multiple bounds.
        //==============================================================
        checkOutput("pkg/MultiTypeParameters.html", true,
                """
                    <div class="member-signature"><span class="modifiers">public</span>&nbsp;<span c\
                    lass="type-parameters">&lt;T extends java.lang.Number &amp; java.lang.Runnable&g\
                    t;</span>
                    <span class="return-type">T</span>&nbsp;<span class="member-name">foo</span>&#82\
                    03;(<span class="parameters">T&nbsp;t)</span></div>""");

        //==============================================================
        // Test Class-Use Documentation for Type Parameters.
        //==============================================================
        // ClassUseTest1: <T extends Foo & Foo2>
        checkOutput("pkg2/class-use/Foo.html", true,
                """
                    <caption><span>Classes in <a href="../package-summary.html">pkg2</a> with type p\
                    arameters of type <a href="../Foo.html" title="class in pkg2">Foo</a></span></ca\
                    ption>""",
                """
                    <th class="col-second" scope="row"><code><span class="member-name-link"><a href=\
                    "../ClassUseTest1.html" title="class in pkg2">ClassUseTest1</a>&lt;T extends <a \
                    href="../Foo.html" title="class in pkg2">Foo</a> &amp; <a href="../Foo2.html" ti\
                    tle="interface in pkg2">Foo2</a>&gt;</span></code></th>""",
                """
                    <caption><span>Methods in <a href="../package-summary.html">pkg2</a> with type p\
                    arameters of type <a href="../Foo.html" title="class in pkg2">Foo</a></span></ca\
                    ption>""",
                """
                    <th class="col-second" scope="row"><span class="type-name-label">ClassUseTest1.<\
                    /span><code><span class="member-name-link"><a href="../ClassUseTest1.html#method\
                    (T)">method</a></span>&#8203;(T&nbsp;t)</code></th>""",
                """
                    <caption><span>Fields in <a href="../package-summary.html">pkg2</a> with type pa\
                    rameters of type <a href="../Foo.html" title="class in pkg2">Foo</a></span></cap\
                    tion>""",
                """
                    td class="col-first"><code><a href="../ParamTest.html" title="class in pkg2">Par\
                    amTest</a>&lt;<a href="../Foo.html" title="class in pkg2">Foo</a>&gt;</code></td\
                    >"""
        );

        checkOutput("pkg2/class-use/ParamTest.html", true,
                """
                    <caption><span>Fields in <a href="../package-summary.html">pkg2</a> declared as \
                    <a href="../ParamTest.html" title="class in pkg2">ParamTest</a></span></caption>""",
                """
                    <td class="col-first"><code><a href="../ParamTest.html" title="class in pkg2">Pa\
                    ramTest</a>&lt;<a href="../Foo.html" title="class in pkg2">Foo</a>&gt;</code></t\
                    d>"""
        );

        checkOutput("pkg2/class-use/Foo2.html", true,
                """
                    <caption><span>Classes in <a href="../package-summary.html">pkg2</a> with type p\
                    arameters of type <a href="../Foo2.html" title="interface in pkg2">Foo2</a></spa\
                    n></caption>""",
                """
                    <th class="col-second" scope="row"><code><span class="member-name-link"><a href=\
                    "../ClassUseTest1.html" title="class in pkg2">ClassUseTest1</a>&lt;T extends <a \
                    href="../Foo.html" title="class in pkg2">Foo</a> &amp; <a href="../Foo2.html" ti\
                    tle="interface in pkg2">Foo2</a>&gt;</span></code></th>""",
                """
                    <caption><span>Methods in <a href="../package-summary.html">pkg2</a> with type p\
                    arameters of type <a href="../Foo2.html" title="interface in pkg2">Foo2</a></spa\
                    n></caption>""",
                """
                    <th class="col-second" scope="row"><span class="type-name-label">ClassUseTest1.<\
                    /span><code><span class="member-name-link"><a href="../ClassUseTest1.html#method\
                    (T)">method</a></span>&#8203;(T&nbsp;t)</code></th>"""
        );

        // ClassUseTest2: <T extends ParamTest<Foo3>>
        checkOutput("pkg2/class-use/ParamTest.html", true,
                """
                    <caption><span>Classes in <a href="../package-summary.html">pkg2</a> with type p\
                    arameters of type <a href="../ParamTest.html" title="class in pkg2">ParamTest</a\
                    ></span></caption>""",
                """
                    <th class="col-second" scope="row"><code><span class="member-name-link"><a href=\
                    "../ClassUseTest2.html" title="class in pkg2">ClassUseTest2</a>&lt;T extends <a \
                    href="../ParamTest.html" title="class in pkg2">ParamTest</a>&lt;<a href="../Foo3\
                    .html" title="class in pkg2">Foo3</a>&gt;&gt;</span></code></th>""",
                """
                    <caption><span>Methods in <a href="../package-summary.html">pkg2</a> with type p\
                    arameters of type <a href="../ParamTest.html" title="class in pkg2">ParamTest</a\
                    ></span></caption>""",
                """
                    <th class="col-second" scope="row"><span class="type-name-label">ClassUseTest2.<\
                    /span><code><span class="member-name-link"><a href="../ClassUseTest2.html#method\
                    (T)">method</a></span>&#8203;(T&nbsp;t)</code></th>""",
                """
                    <caption><span>Fields in <a href="../package-summary.html">pkg2</a> declared as \
                    <a href="../ParamTest.html" title="class in pkg2">ParamTest</a></span></caption>""",
                """
                    <td class="col-first"><code><a href="../ParamTest.html" title="class in pkg2">Pa\
                    ramTest</a>&lt;<a href="../Foo.html" title="class in pkg2">Foo</a>&gt;</code></t\
                    d>""",
                """
                    <caption><span>Methods in <a href="../package-summary.html">pkg2</a> with type p\
                    arameters of type <a href="../ParamTest.html" title="class in pkg2">ParamTest</a\
                    ></span></caption>""",
                """
                    <td class="col-first"><code>&lt;T extends <a href="../ParamTest.html" title="cla\
                    ss in pkg2">ParamTest</a>&lt;<a href="../Foo3.html" title="class in pkg2">Foo3</\
                    a>&gt;&gt;<br><a href="../ParamTest.html" title="class in pkg2">ParamTest</a>&lt\
                    ;<a href="../Foo3.html" title="class in pkg2">Foo3</a>&gt;</code></td>"""
        );

        checkOutput("pkg2/class-use/Foo3.html", true,
                """
                    <caption><span>Classes in <a href="../package-summary.html">pkg2</a> with type p\
                    arameters of type <a href="../Foo3.html" title="class in pkg2">Foo3</a></span></\
                    caption>""",
                """
                    <th class="col-second" scope="row"><code><span class="member-name-link"><a href=\
                    "../ClassUseTest2.html" title="class in pkg2">ClassUseTest2</a>&lt;T extends <a \
                    href="../ParamTest.html" title="class in pkg2">ParamTest</a>&lt;<a href="../Foo3\
                    .html" title="class in pkg2">Foo3</a>&gt;&gt;</span></code></th>""",
                """
                    <caption><span>Methods in <a href="../package-summary.html">pkg2</a> with type p\
                    arameters of type <a href="../Foo3.html" title="class in pkg2">Foo3</a></span></\
                    caption>""",
                """
                    <th class="col-second" scope="row"><span class="type-name-label">ClassUseTest2.<\
                    /span><code><span class="member-name-link"><a href="../ClassUseTest2.html#method\
                    (T)">method</a></span>&#8203;(T&nbsp;t)</code></th>""",
                """
                    <caption><span>Methods in <a href="../package-summary.html">pkg2</a> that return\
                     types with arguments of type <a href="../Foo3.html" title="class in pkg2">Foo3<\
                    /a></span></caption>""",
                """
                    <td class="col-first"><code>&lt;T extends <a href="../ParamTest.html" title="cla\
                    ss in pkg2">ParamTest</a>&lt;<a href="../Foo3.html" title="class in pkg2">Foo3</\
                    a>&gt;&gt;<br><a href="../ParamTest.html" title="class in pkg2">ParamTest</a>&lt\
                    ;<a href="../Foo3.html" title="class in pkg2">Foo3</a>&gt;</code></td>"""
        );

        // ClassUseTest3: <T extends ParamTest2<List<? extends Foo4>>>
        checkOutput("pkg2/class-use/ParamTest2.html", true,
                """
                    <caption><span>Classes in <a href="../package-summary.html">pkg2</a> with type p\
                    arameters of type <a href="../ParamTest2.html" title="class in pkg2">ParamTest2<\
                    /a></span></caption>""",
                """
                    <th class="col-second" scope="row"><code><span class="member-name-link"><a href=\
                    "../ClassUseTest3.html" title="class in pkg2">ClassUseTest3</a>&lt;T extends <a \
                    href="../ParamTest2.html" title="class in pkg2">ParamTest2</a>&lt;java.util.List\
                    &lt;? extends <a href="../Foo4.html" title="class in pkg2">Foo4</a>&gt;&gt;&gt;<\
                    /span></code></th>""",
                """
                    <caption><span>Methods in <a href="../package-summary.html">pkg2</a> with type p\
                    arameters of type <a href="../ParamTest2.html" title="class in pkg2">ParamTest2<\
                    /a></span></caption>""",
                """
                    <th class="col-second" scope="row"><span class="type-name-label">ClassUseTest3.<\
                    /span><code><span class="member-name-link"><a href="../ClassUseTest3.html#method\
                    (T)">method</a></span>&#8203;(T&nbsp;t)</code></th>""",
                """
                    <td class="col-first"><code>&lt;T extends <a href="../ParamTest2.html" title="cl\
                    ass in pkg2">ParamTest2</a>&lt;java.util.List&lt;? extends <a href="../Foo4.html\
                    " title="class in pkg2">Foo4</a>&gt;&gt;&gt;<br><a href="../ParamTest2.html" tit\
                    le="class in pkg2">ParamTest2</a>&lt;java.util.List&lt;? extends <a href="../Foo\
                    4.html" title="class in pkg2">Foo4</a>&gt;&gt;</code></td>"""
        );

        checkOutput("pkg2/class-use/Foo4.html", true,
                """
                    <caption><span>Classes in <a href="../package-summary.html">pkg2</a> with type p\
                    arameters of type <a href="../Foo4.html" title="class in pkg2">Foo4</a></span></\
                    caption>""",
                """
                    <th class="col-second" scope="row"><code><span class="member-name-link"><a href=\
                    "../ClassUseTest3.html" title="class in pkg2">ClassUseTest3</a>&lt;T extends <a \
                    href="../ParamTest2.html" title="class in pkg2">ParamTest2</a>&lt;java.util.List\
                    &lt;? extends <a href="../Foo4.html" title="class in pkg2">Foo4</a>&gt;&gt;&gt;<\
                    /span></code></th>""",
                """
                    <caption><span>Methods in <a href="../package-summary.html">pkg2</a> with type p\
                    arameters of type <a href="../Foo4.html" title="class in pkg2">Foo4</a></span></\
                    caption>""",
                """
                    <th class="col-second" scope="row"><span class="type-name-label">ClassUseTest3.<\
                    /span><code><span class="member-name-link"><a href="../ClassUseTest3.html#method\
                    (T)">method</a></span>&#8203;(T&nbsp;t)</code></th>""",
                """
                    <caption><span>Methods in <a href="../package-summary.html">pkg2</a> that return\
                     types with arguments of type <a href="../Foo4.html" title="class in pkg2">Foo4<\
                    /a></span></caption>""",
                """
                    <td class="col-first"><code>&lt;T extends <a href="../ParamTest2.html" title="cl\
                    ass in pkg2">ParamTest2</a>&lt;java.util.List&lt;? extends <a href="../Foo4.html\
                    " title="class in pkg2">Foo4</a>&gt;&gt;&gt;<br><a href="../ParamTest2.html" tit\
                    le="class in pkg2">ParamTest2</a>&lt;java.util.List&lt;? extends <a href="../Foo\
                    4.html" title="class in pkg2">Foo4</a>&gt;&gt;</code></td>"""
        );

        // Type parameters in constructor and method args
        checkOutput("pkg2/class-use/Foo4.html", true,
                """
                    <caption><span>Method parameters in <a href="../package-summary.html">pkg2</a> w\
                    ith type arguments of type <a href="../Foo4.html" title="class in pkg2">Foo4</a>\
                    </span></caption>
                    <thead>
                    <tr>
                    <th class="col-first" scope="col">Modifier and Type</th>
                    <th class="col-second" scope="col">Method</th>
                    <th class="col-last" scope="col">Description</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr class="alt-color">
                    <td class="col-first"><code>void</code></td>
                    <th class="col-second" scope="row"><span class="type-name-label">ClassUseTest3.<\
                    /span><code><span class="member-name-link"><a href="../ClassUseTest3.html#method\
                    (java.util.Set)">method</a></span>&#8203;(java.util.Set&lt;<a href="../Foo4.html\
                    " title="class in pkg2">Foo4</a>&gt;&nbsp;p)</code></th>""",
                """
                    <caption><span>Constructor parameters in <a href="../package-summary.html">pkg2<\
                    /a> with type arguments of type <a href="../Foo4.html" title="class in pkg2">Foo\
                    4</a></span></caption>"""
        );

        //=================================
        // TYPE PARAMETER IN INDEX
        //=================================
        checkOutput("index-all.html", true,
                """
                    <span class="member-name-link"><a href="pkg2/Foo.html#method(java.util.Vector)">method(Vector&lt;Object&gt;)</a></span>"""
        );

        // TODO: duplicate of previous case; left in delibarately for now to simplify comparison testing
        //=================================
        // TYPE PARAMETER IN INDEX
        //=================================
        checkOutput("index-all.html", true,
                """
                    <span class="member-name-link"><a href="pkg2/Foo.html#method(java.util.Vector)">method(Vector&lt;Object&gt;)</a></span>"""
        );

    }

    //=================================
    // VAR ARG TESTING
    //=================================
    void checkVarArgs() {
        checkOutput("pkg/VarArgs.html", true,
                "(int...&nbsp;i)",
                "(int[][]...&nbsp;i)",
                "(int[]...)",
                """
                    <a href="TypeParameters.html" title="class in pkg">TypeParameters</a>...&nbsp;t""");
    }

    //=================================
    // ANNOTATION TYPE TESTING
    //=================================
    void checkAnnotationTypes() {
        checkOutput("pkg/AnnotationType.html", true,
                // Make sure the summary links are correct.
                """
                    <li>Summary:&nbsp;</li>
                    <li>Field&nbsp;|&nbsp;</li>
                    <li><a href="#annotation.type.required.element.summary">Required</a>&nbsp;|&nbsp;</li>
                    <li><a href="#annotation.type.optional.element.summary">Optional</a></li>""",
                // Make sure the detail links are correct.
                """
                    <li>Detail:&nbsp;</li>
                    <li>Field&nbsp;|&nbsp;</li>
                    <li><a href="#annotation.type.element.detail">Element</a></li>""",
                // Make sure the heading is correct.
                "Annotation Type AnnotationType</h2>",
                // Make sure the signature is correct.
                """
                    public @interface <span class="member-name-label">AnnotationType</span>""",
                // Make sure member summary headings are correct.
                "<h3>Required Element Summary</h3>",
                "<h3>Optional Element Summary</h3>",
                // Make sure element detail heading is correct
                "Element Detail",
                // Make sure default annotation type value is printed when necessary.
                """
                    <dl>
                    <dt>Default:</dt>
                    <dd>"unknown"</dd>
                    </dl>""");
    }

    //=================================
    // ANNOTATION TYPE USAGE TESTING
    //=================================
    void checkAnnotationTypeUsage() {
        checkOutput("pkg/package-summary.html", true,
                // PACKAGE
                """
                    <a href="AnnotationType.html" title="annotation in pkg">@AnnotationType</a>(<a h\
                    ref="AnnotationType.html#optional()">optional</a>="Package Annotation",
                                    <a href="AnnotationType.html#required()">required</a>=1994)""");

        checkOutput("pkg/AnnotationTypeUsage.html", true,
                // CLASS
                """
                    <pre><a href="AnnotationType.html" title="annotation in pkg">@AnnotationType</a>\
                    (<a href="AnnotationType.html#optional()">optional</a>="Class Annotation",
                                    <a href="AnnotationType.html#required()">required</a>=1994)
                    public class <span class="type-name-label">AnnotationTypeUsage</span>
                    extends java.lang.Object</pre>""",
                // FIELD
                """
                    <div class="member-signature"><span class="annotations"><a href="AnnotationType.\
                    html" title="annotation in pkg">@AnnotationType</a>(<a href="AnnotationType.html\
                    #optional()">optional</a>="Field Annotation",
                                    <a href="AnnotationType.html#required()">required</a>=1994)
                    </span><span class="modifiers">public</span>&nbsp;<span class="return-type">int<\
                    /span>&nbsp;<span class="member-name">field</span></div>""",
                // CONSTRUCTOR
                """
                    <div class="member-signature"><span class="annotations"><a href="AnnotationType.\
                    html" title="annotation in pkg">@AnnotationType</a>(<a href="AnnotationType.html\
                    #optional()">optional</a>="Constructor Annotation",
                                    <a href="AnnotationType.html#required()">required</a>=1994)
                    </span><span class="modifiers">public</span>&nbsp;<span class="member-name">AnnotationTypeUsage</span>()</div>""",
                // METHOD
                """
                    <div class="member-signature"><span class="annotations"><a href="AnnotationType.\
                    html" title="annotation in pkg">@AnnotationType</a>(<a href="AnnotationType.html\
                    #optional()">optional</a>="Method Annotation",
                                    <a href="AnnotationType.html#required()">required</a>=1994)
                    </span><span class="modifiers">public</span>&nbsp;<span class="return-type">void\
                    </span>&nbsp;<span class="member-name">method</span>()</div>""",
                // METHOD PARAMS
                """
                    <div class="member-signature"><span class="modifiers">public</span>&nbsp;<span c\
                    lass="return-type">void</span>&nbsp;<span class="member-name">methodWithParams</\
                    span>&#8203;(<span class="parameters"><a href="AnnotationType.html" title="annot\
                    ation in pkg">@AnnotationType</a>(<a href="AnnotationType.html#optional()">optio\
                    nal</a>="Parameter Annotation",<a href="AnnotationType.html#required()">required\
                    </a>=1994)
                    int&nbsp;documented,
                    int&nbsp;undocmented)</span></div>""",
                // CONSTRUCTOR PARAMS
                """
                    <div class="member-signature"><span class="modifiers">public</span>&nbsp;<span c\
                    lass="member-name">AnnotationTypeUsage</span>&#8203;(<span class="parameters"><a\
                     href="AnnotationType.html" title="annotation in pkg">@AnnotationType</a>(<a hre\
                    f="AnnotationType.html#optional()">optional</a>="Constructor Param Annotation",<\
                    a href="AnnotationType.html#required()">required</a>=1994)
                    int&nbsp;documented,
                    int&nbsp;undocmented)</span></div>""");

        //=================================
        // Annotatation Type Usage
        //=================================
        checkOutput("pkg/class-use/AnnotationType.html", true,
                """
                    <caption><span>Packages with annotations of type <a href="../AnnotationType.html\
                    " title="annotation in pkg">AnnotationType</a></span></caption>""",
                """
                    <caption><span>Classes in <a href="../package-summary.html">pkg</a> with annotat\
                    ions of type <a href="../AnnotationType.html" title="annotation in pkg">Annotati\
                    onType</a></span></caption>""",
                """
                    <caption><span>Fields in <a href="../package-summary.html">pkg</a> with annotati\
                    ons of type <a href="../AnnotationType.html" title="annotation in pkg">Annotatio\
                    nType</a></span></caption>""",
                """
                    <caption><span>Methods in <a href="../package-summary.html">pkg</a> with annotat\
                    ions of type <a href="../AnnotationType.html" title="annotation in pkg">Annotati\
                    onType</a></span></caption>""",
                """
                    <caption><span>Method parameters in <a href="../package-summary.html">pkg</a> wi\
                    th annotations of type <a href="../AnnotationType.html" title="annotation in pkg\
                    ">AnnotationType</a></span></caption>""",
                """
                    <caption><span>Constructors in <a href="../package-summary.html">pkg</a> with an\
                    notations of type <a href="../AnnotationType.html" title="annotation in pkg">Ann\
                    otationType</a></span></caption>""",
                """
                    <caption><span>Constructor parameters in <a href="../package-summary.html">pkg</\
                    a> with annotations of type <a href="../AnnotationType.html" title="annotation i\
                    n pkg">AnnotationType</a></span></caption>"""
        );

        //==============================================================
        // ANNOTATION TYPE USAGE TESTING (When @Documented is omitted)
        //===============================================================
        checkOutput("pkg/AnnotationTypeUsage.html", false,
                // CLASS
                """
                    <a href="AnnotationTypeUndocumented.html" title="annotation in pkg">@AnnotationT\
                    ypeUndocumented</a>(<a href="AnnotationType.html#optional">optional</a>="Class A\
                    nnotation",
                                    <a href="AnnotationType.html#required">required</a>=1994)
                    public class <span class="type-name-label">AnnotationTypeUsage</span></dt><dt>extends java.lang.Object</dt>""",
                // FIELD
                """
                    <a href="AnnotationTypeUndocumented.html" title="annotation in pkg">@AnnotationT\
                    ypeUndocumented</a>(<a href="AnnotationType.html#optional">optional</a>="Field A\
                    nnotation",
                                    <a href="AnnotationType.html#required">required</a>=1994)
                    public int <span class="member-name-label">field</span>""",
                // CONSTRUCTOR
                """
                    <a href="AnnotationTypeUndocumented.html" title="annotation in pkg">@AnnotationT\
                    ypeUndocumented</a>(<a href="AnnotationType.html#optional">optional</a>="Constru\
                    ctor Annotation",
                                    <a href="AnnotationType.html#required">required</a>=1994)
                    public <span class="type-name-label">AnnotationTypeUsage</span>()""",
                // METHOD
                """
                    <a href="AnnotationTypeUndocumented.html" title="annotation in pkg">@AnnotationT\
                    ypeUndocumented</a>(<a href="AnnotationType.html#optional">optional</a>="Method \
                    Annotation",
                                    <a href="AnnotationType.html#required">required</a>=1994)
                    public void <span class="member-name-label">method</span>()""");

        //=================================
        // Make sure annotation types do not
        // trigger this warning.
        //=================================
        checkOutput(Output.OUT, false,
                "Internal error: package sets don't match: [] with: null");

        //=================================
        // ANNOTATION TYPE USAGE TESTING (All Different Types).
        //=================================
        checkOutput("pkg1/B.html", true,
                // Integer
                "<a href=\"A.html#d()\">d</a>=3.14,",
                // Double
                "<a href=\"A.html#d()\">d</a>=3.14,",
                // Boolean
                "<a href=\"A.html#b()\">b</a>=true,",
                // String
                """
                    <a href="A.html#s()">s</a>="sigh",""",
                // Class
                """
                    <a href="A.html#c()">c</a>=<a href="../pkg2/Foo.html" title="class in pkg2">Foo.class</a>,""",
                // Bounded Class
                """
                    <a href="A.html#w()">w</a>=<a href="../pkg/TypeParameterSubClass.html" title="cl\
                    ass in pkg">TypeParameterSubClass.class</a>,""",
                // Enum
                """
                    <a href="A.html#e()">e</a>=<a href="../pkg/Coin.html#Penny">Penny</a>,""",
                // Annotation Type
                """
                    <a href="A.html#a()">a</a>=<a href="../pkg/AnnotationType.html" title="annotatio\
                    n in pkg">@AnnotationType</a>(<a href="../pkg/AnnotationType.html#optional()">op\
                    tional</a>="foo",<a href="../pkg/AnnotationType.html#required()">required</a>=19\
                    94),""",
                // String Array
                """
                    <a href="A.html#sa()">sa</a>={"up","down"},""",
                // Primitive
                """
                    <a href="A.html#primitiveClassTest()">primitiveClassTest</a>=boolean.class,""");

        // XXX:  Add array test case after this if fixed:
        //5020899: Incorrect internal representation of class-valued annotation elements
        // Make sure that annotations are surrounded by <pre> and </pre>
        checkOutput("pkg1/B.html", true,
                """
                    <pre><a href="A.html" title="annotation in pkg1">@A</a>""",
                """
                    public interface <span class="type-name-label">B</span></pre>""");

    }
}
