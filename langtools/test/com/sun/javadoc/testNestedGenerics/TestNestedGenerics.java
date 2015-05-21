/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      6758050 8025633
 * @summary  Test HTML output for nested generic types.
 * @author   bpatel
 * @library  ../lib
 * @modules jdk.javadoc
 * @build    JavadocTester
 * @run main TestNestedGenerics
 */

public class TestNestedGenerics extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestNestedGenerics tester = new TestNestedGenerics();
        tester.runTests();
    }

    @Test
    void test() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkOutput("pkg/NestedGenerics.html", true,
            "<div class=\"block\">Contains <a " +
            "href=\"../pkg/NestedGenerics.html#foo-java.util.Map-\"><code>foo" +
            "(java.util.Map&lt;A, java.util.Map&lt;A, A&gt;&gt;)</code></a></div>");
    }
}
