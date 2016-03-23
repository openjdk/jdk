/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8006248
 * @summary  Test custom tag. Verify that an unknown tag generates appropriate warnings.
 * @author   Bhavesh Patel
 * @library  ../lib
 * @modules jdk.javadoc/com.sun.tools.doclets.internal.toolkit
 *          jdk.javadoc/com.sun.tools.doclets.internal.toolkit.taglets
 *          jdk.javadoc/com.sun.tools.doclets.internal.toolkit.util
 * @build    JavadocTester taglets.CustomTag
 * @run main TestCustomTag
 */

public class TestCustomTag extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestCustomTag tester = new TestCustomTag();
        tester.runTests();
    }

    @Test
    void test1() {
        javadoc("-Xdoclint:none",
                "-d", "out-1",
                "-tagletpath", testSrc, // TODO: probably useless
                "-taglet", "taglets.CustomTag",
                "-sourcepath",  testSrc,
                "-XDaccessInternalAPI",
                testSrc("TagTestClass.java"));
        checkExit(Exit.OK);

        checkOutput(Output.WARNING, true,
                "warning - @unknownTag is an unknown tag.");
    }

    @Test
    void test2() {
        javadoc("-d", "out-2",
                "-tagletpath", testSrc,  // TODO: probably useless
                "-taglet", "taglets.CustomTag",
                "-sourcepath", testSrc,
                "-XDaccessInternalAPI",
                testSrc("TagTestClass.java"));
        checkExit(Exit.FAILED);

        checkOutput(Output.ERROR, true,
                "error: unknown tag: unknownTag");
    }

    @Test
    void test3() {
        javadoc("-Xdoclint:none",
                "-d", "out-3",
                "-sourcepath", testSrc,
                testSrc("TagTestClass.java"));
        checkExit(Exit.OK);

        checkOutput(Output.WARNING,  true,
            "warning - @customTag is an unknown tag.",
            "warning - @unknownTag is an unknown tag.");
    }

    @Test
    void test4() {
        javadoc("-d", "out-4",
                "-sourcepath",  testSrc,
                testSrc("TagTestClass.java"));
        checkExit(Exit.FAILED);

        checkOutput(Output.ERROR, true,
            "error: unknown tag: customTag",
            "error: unknown tag: unknownTag");
    }
}
