/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8176231
 * @summary  Test JavaFX property.
 * @library  ../lib/
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    JavadocTester TestProperty
 * @run main TestProperty
 */

public class TestProperty extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestProperty tester = new TestProperty();
        tester.runTests();
    }

    @Test
    void testArrays() {
        javadoc("-d", "out",
                "-javafx",
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkOutput("pkg/MyClass.html", true,
                "<pre>public final&nbsp;<a href=\"../pkg/ObjectProperty.html\" "
                + "title=\"class in pkg\">ObjectProperty</a>"
                + "&lt;<a href=\"../pkg/MyObj.html\" "
                + "title=\"class in pkg\">MyObj</a>&gt; goodProperty</pre>\n"
                + "<div class=\"block\">This is an Object property where the "
                + "Object is a single Object.</div>\n"
                + "<dl>\n"
                + "<dt><span class=\"seeLabel\">See Also:</span></dt>\n"
                + "<dd><a href=\"../pkg/MyClass.html#getGood--\"><code>getGood()</code></a>, \n"
                + "<a href=\"../pkg/MyClass.html#setGood-pkg.MyObj-\">"
                + "<code>setGood(MyObj)</code></a></dd>\n"
                + "</dl>",

                "<pre>public final&nbsp;<a href=\"../pkg/ObjectProperty.html\" "
                + "title=\"class in pkg\">ObjectProperty</a>"
                + "&lt;<a href=\"../pkg/MyObj.html\" "
                + "title=\"class in pkg\">MyObj</a>[]&gt; badProperty</pre>\n"
                + "<div class=\"block\">This is an Object property where the "
                + "Object is an array.</div>\n"
                + "<dl>\n"
                + "<dt><span class=\"seeLabel\">See Also:</span></dt>\n"
                + "<dd><a href=\"../pkg/MyClass.html#getBad--\"><code>getBad()</code></a>, \n"
                + "<a href=\"../pkg/MyClass.html#setBad-pkg.MyObj:A-\">"
                + "<code>setBad(MyObj[])</code></a></dd>\n"
                + "</dl>"
        );

        checkOutput("pkg/MyClassT.html", true,
                "<pre>public final&nbsp;<a href=\"../pkg/ObjectProperty.html\" "
                + "title=\"class in pkg\">ObjectProperty</a>"
                + "&lt;java.util.List&lt;<a href=\"../pkg/MyClassT.html\" "
                + "title=\"type parameter in MyClassT\">T</a>&gt;&gt; "
                + "listProperty</pre>\n"
                + "<div class=\"block\">This is an Object property where the "
                + "Object is a single <code>List&lt;T&gt;</code>.</div>\n"
                + "<dl>\n"
                + "<dt><span class=\"seeLabel\">See Also:</span></dt>\n"
                + "<dd><a href=\"../pkg/MyClassT.html#getList--\">"
                + "<code>getList()</code></a>, \n"
                + "<a href=\"../pkg/MyClassT.html#setList-java.util.List-\">"
                + "<code>setList(List)</code></a></dd>\n"
                + "</dl>"
        );
    }
}

