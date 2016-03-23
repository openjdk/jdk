/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test for nested inline tags. *
 * @author jamieh
 * @library ../lib
 * @modules jdk.javadoc/com.sun.tools.doclets.internal.toolkit
 *          jdk.javadoc/com.sun.tools.doclets.internal.toolkit.taglets
 *          jdk.javadoc/com.sun.tools.doclets.internal.toolkit.util
 * @build JavadocTester
 * @build testtaglets.UnderlineTaglet
 * @build testtaglets.BoldTaglet
 * @build testtaglets.GreenTaglet
 * @run main TestNestedInlineTag
 */

/**
 * This should be green, underlined and bold (Class): {@underline {@bold {@green My test}}} .
 */
public class TestNestedInlineTag extends JavadocTester {
    /**
     * This should be green, underlined and bold (Field): {@underline {@bold {@green My test}}} .
     */
    public int field;

    /**
     * This should be green, underlined and bold (Constructor): {@underline {@bold {@green My test}}} .
     */
    public TestNestedInlineTag(){}

    /**
     * This should be green, underlined and bold (Method): {@underline {@bold {@green My test}}} .
     */
    public void method(){}

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     * @throws Exception if the test fails
     */
    public static void main(String... args) throws Exception {
        TestNestedInlineTag tester = new TestNestedInlineTag();
        tester.runTests();
    }

    @Test
    void test() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "-taglet", "testtaglets.UnderlineTaglet",
                "-taglet", "testtaglets.BoldTaglet",
                "-taglet", "testtaglets.GreenTaglet",
                "-XDaccessInternalAPI",
                testSrc("TestNestedInlineTag.java"));
        checkExit(Exit.OK);

        checkOutput("TestNestedInlineTag.html", true,
                //Test nested inline tag in class description.
                "This should be green, underlined and bold (Class): <u><b><font color=\"green\">My test</font></b></u>",
                //Test nested inline tag in field description.
                "This should be green, underlined and bold (Field): <u><b><font color=\"green\">My test</font></b></u>",
                //Test nested inline tag in constructor description.
                "This should be green, underlined and bold (Constructor): <u><b><font color=\"green\">My test</font></b></u>",
                //Test nested inline tag in method description.
                "This should be green, underlined and bold (Method): <u><b><font color=\"green\">My test</font></b></u>"
        );
    }
}
