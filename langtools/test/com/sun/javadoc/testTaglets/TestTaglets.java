/*
 * Copyright (c) 2003, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4654308 4767038 8025633
 * @summary  Use a Taglet and include some inline tags such as {@link}.  The
 *           inline tags should be interpreted properly.
 *           Run Javadoc on some sample source that uses {@inheritDoc}.  Make
 *           sure that only the first sentence shows up in the summary table.
 * @author   jamieh
 * @library  ../lib
 * @modules jdk.javadoc/com.sun.tools.doclets.internal.toolkit
 *          jdk.javadoc/com.sun.tools.doclets.internal.toolkit.taglets
 *          jdk.javadoc/com.sun.tools.doclets.internal.toolkit.util
 * @build    JavadocTester taglets.Foo
 * @run main TestTaglets
 */

public class TestTaglets extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestTaglets tester = new TestTaglets();
        tester.runTests();
    }

    @Test
    void test_4654308() {
        javadoc("-d", "out-4654308",
                "-tagletpath", testSrc, // TODO: probably does no good
                "-taglet", "taglets.Foo",
                "-sourcepath", testSrc,
                "-XDaccessInternalAPI",
                testSrc("C.java"));
        checkExit(Exit.OK);

        checkOutput("C.html", true,
                "<span class=\"simpleTagLabel\">Foo:</span></dt>"
                + "<dd>my only method is <a href=\"C.html#method--\"><code>here"
                + "</code></a></dd></dl>");
    }

    @Test
    void test_4767038() {
        javadoc("-d", "out-4767038",
                "-sourcepath", testSrc,
                testSrc("Parent.java"), testSrc("Child.java"));
        checkExit(Exit.OK);

        checkOutput("Child.html", true,
                "This is the first sentence.");
    }
}
