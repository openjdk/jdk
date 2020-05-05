/*
 * Copyright (c) 2004, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4632553 4973607 8026567
 * @summary  No need to include type name (class, interface, etc.) before
 *           every single type in class tree.
 *           Make sure class tree includes heirarchy for enums and annotation
 *           types.
 * @library  ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    javadoc.tester.*
 * @run main TestClassTree
 */

import javadoc.tester.JavadocTester;

public class TestClassTree extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestClassTree tester = new TestClassTree();
        tester.runTests();
    }

    @Test
    public void test() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkOutput("pkg/package-tree.html", true,
                """
                    <ul>
                    <li class="circle">pkg.<a href="ParentClass.html" title="class in pkg"><span cla\
                    ss="type-name-link">ParentClass</span></a>""",
                """
                    <h2 title="Annotation Type Hierarchy">Annotation Type Hierarchy</h2>
                    <ul>
                    <li class="circle">pkg.<a href="AnnotationType.html" title="annotation in pkg"><\
                    span class="type-name-link">AnnotationType</span></a> (implements java.lang.anno\
                    tation.Annotation)</li>
                    </ul>""",
                """
                    <h2 title="Enum Hierarchy">Enum Hierarchy</h2>
                    <ul>
                    <li class="circle">java.lang.Object
                    <ul>
                    <li class="circle">java.lang.Enum&lt;E&gt; (implements java.lang.Comparable&lt;T\
                    &gt;, java.lang.constant.Constable, java.io.Serializable)
                    <ul>
                    <li class="circle">pkg.<a href="Coin.html" title="enum in pkg"><span class="type-name-link">Coin</span></a></li>
                    </ul>
                    </li>
                    </ul>
                    </li>
                    </ul>""");

        checkOutput("pkg/package-tree.html", false,
                """
                    <li class="circle">class pkg.<a href=".ParentClass.html" title="class in pkg"><s\
                    pan class="type-name-link">ParentClass</span></a></li>""");
    }
}
