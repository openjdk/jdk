/*
 * Copyright (c) 2001, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4341304 4485668 4966728 8032066 8071982 8192933 8215307
 * @summary Test that methods readResolve and writeReplace show
 * up in serialized-form.html the same way that readObject and writeObject do.
 * If the doclet includes readResolve and writeReplace in the serialized-form
 * documentation that same way the it includes readObject and writeObject, the
 * test passes.  This also tests that throws tag information is correctly shown
 * in the serialized form page.
 * Make sure see tags work in serialized form.
 * @library ../../lib/
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.*
 * @build TestSerializedForm
 * @run main TestSerializedForm
 */

import java.io.*;

import javadoc.tester.JavadocTester;

public class TestSerializedForm extends JavadocTester {
    public static void main(String... args) throws Exception {
        TestSerializedForm tester = new TestSerializedForm();
        tester.runTests();
//        tester.run(ARGS, TEST, NEGATED_TEST);
//        tester.run(ARGS_PRIVATE, TEST_PRIVATE, NEGATED_TEST_PRIVATE);
//        tester.printSummary();
    }

    // @ignore 8146022
    // @Test
    public void testDefault() {
        javadoc("-d", "out-default", "-serialwarn", "-Xdoclint:none",
                "-sourcepath", testSrc,
                testSrc("SerializedForm.java"), testSrc("ExternalizedForm.java"), "pkg1");
        checkExit(Exit.OK);

        checkOutput("serialized-form.html", true,
                "protected&nbsp;java.lang.Object&nbsp;readResolve()",
                "protected&nbsp;java.lang.Object&nbsp;writeReplace()",
                "protected&nbsp;java.lang.Object&nbsp;readObjectNoData()",
                """
                    <h3>Serialization Overview</h3>
                    <ul class="block-list">
                    <li class="block-list">
                    <div class="block"><span class="deprecated-label">Deprecated.</span>&nbsp;</div>
                    <dl>
                    <dt><span class="seeLabel">See Also:</span></dt>
                    <dd><code>TestSerializedForm</code></dd>
                    </dl>""",
                "<h3>Class pkg1.NestedInnerClass.InnerClass.ProNestedInnerClass "
                + "extends java.lang.Object implements Serializable</h3>",
                "<h3>Class pkg1.PrivateIncludeInnerClass.PriInnerClass extends "
                + "java.lang.Object implements Serializable</h3>",
                "<h3>Class pkg1.ProtectedInnerClass.ProInnerClass extends "
                + "java.lang.Object implements Serializable</h3>");

        checkOutput("serialized-form.html", false,
                """
                    <h3>Class <a href="pkg1/NestedInnerClass.InnerClass.ProNestedInnerClass.html" ti\
                    tle="class in pkg1">pkg1.NestedInnerClass.InnerClass.ProNestedInnerClass</a> ext\
                    ends java.lang.Object implements Serializable</h3>""",
                """
                    <h3>Class <a href="pkg1/PrivateInnerClass.PriInnerClass.html" title="class in pk\
                    g1">pkg1.PrivateInnerClass.PriInnerClass</a> extends java.lang.Object implements\
                     Serializable</h3>""",
                """
                    <h3>Class <a href="pkg1/ProtectedInnerClass.ProInnerClass.html" title="class in \
                    pkg1">pkg1.ProtectedInnerClass.ProInnerClass</a> extends java.lang.Object implem\
                    ents Serializable</h3>""",
                "<h3>Class pkg1.PublicExcludeInnerClass.PubInnerClass extends java.lang.Object implements "
                + "Serializable</h3>");

        checkOutput("serialized-form.html", true,
                """
                    <h3>Serialized Fields</h3>
                    <ul class="block-list">
                    <li class="block-list">
                    <h4>longs</h4>
                    <pre>Long[] longs</pre>
                    <div class="block">the longs</div>
                    </li>
                    <li class="block-list">
                    <h4>name</h4>
                    <pre>java.lang.String name</pre>
                    <div class="block">a test</div>""");
    }

    @Test
    public void testPrivate() {
        javadoc("-private",
                "-d", "out-private",
                "-sourcepath", testSrc,
                testSrc("SerializedForm.java"), testSrc("ExternalizedForm.java"), "pkg1");
        checkExit(Exit.OK);

        showHeadings("serialized-form.html");

        checkOutput("serialized-form.html", true,
                """
                    <h3>Class <a href="pkg1/NestedInnerClass.InnerClass.ProNestedInnerClass.html" ti\
                    tle="class in pkg1">pkg1.NestedInnerClass.InnerClass.ProNestedInnerClass</a> ext\
                    ends java.lang.Object implements Serializable</h3>""",
                """
                    <h3>Class <a href="pkg1/PrivateIncludeInnerClass.PriInnerClass.html" title="clas\
                    s in pkg1">pkg1.PrivateIncludeInnerClass.PriInnerClass</a> extends java.lang.Obj\
                    ect implements Serializable</h3>""",
                """
                    <h3>Class <a href="pkg1/ProtectedInnerClass.ProInnerClass.html" title="class in \
                    pkg1">pkg1.ProtectedInnerClass.ProInnerClass</a> extends java.lang.Object implem\
                    ents Serializable</h3>""");

        checkOutput("serialized-form.html", false,
                "<h3>Class pkg1.NestedInnerClass.InnerClass.ProNestedInnerClass "
                + "extends java.lang.Object implements Serializable</h3>",
                "<h3>Class pkg1.PrivateInnerClass.PriInnerClass extends "
                + "java.lang.Object implements Serializable</h3>",
                "<h3>Class pkg1.ProtectedInnerClass.ProInnerClass extends "
                + "java.lang.Object implements Serializable</h3>",
                """
                    <h3>Class <a href="pkg1/PublicExcludeInnerClass.PubInnerClass.html" title="class\
                     in pkg1">pkg1.PublicExcludeInnerClass.PubInnerClass</a> extends java.lang.Objec\
                    t implements Serializable</h3>""");

        checkOutput("serialized-form.html", true,
                """
                    <h4>Serialized Fields</h4>
                    <ul class="block-list">
                    <li class="block-list">
                    <h5>longs</h5>
                    <pre>Long[] longs</pre>
                    <div class="block">the longs</div>
                    </li>
                    <li class="block-list">
                    <h5>name</h5>
                    <pre>java.lang.String name</pre>
                    <div class="block">a test</div>""");
    }

    @Test
    public void test2() {
        javadoc("-private",
                "-d", "out-2",
                "-sourcepath", testSrc,
                "pkg2");
        checkExit(Exit.OK);

        checkOrder("serialized-form.html",
                "int[] a1",
                "int[][] a2",
                """
                    <a href="pkg2/Fields.html" title="class in pkg2">Fields</a>[][] doubleArray""",
                """
                    <a href="pkg2/Fields.html" title="class in pkg2">Fields</a>[] singleArray""",
                """
                    java.lang.Class&lt;<a href="pkg2/Fields.html" title="type parameter in Fields">E</a>&gt; someClass""");
    }
}
