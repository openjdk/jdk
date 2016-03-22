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
 * @bug      8007687
 * @summary  Make sure that the -X option works properly.
 * @library ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    JavadocTester
 * @run main TestXOption
 */

public class TestXOption extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestXOption tester = new TestXOption();
        tester.runTests();
    }

    @Test
    void testWithOption() {
        javadoc("-d", "out1",
                "-sourcepath", testSrc,
                "-X",
                testSrc("TestXOption.java"));
        checkExit(Exit.OK);
        checkOutput(true);
    }

    @Test
    void testWithoutOption() {
        javadoc("-d", "out2",
                "-sourcepath", testSrc,
                testSrc("TestXOption.java"));
        checkExit(Exit.OK);
        checkOutput(false);
    }

    private void checkOutput(boolean expectFound) {
        // TODO: It's an ugly hidden side-effect of the current doclet API
        // that the -X output from the tool and the -X output from the doclet
        // come out on different streams!
        // When we clean up the doclet API, this should be rationalized.
        checkOutput(Output.OUT, expectFound,
                "-Xmaxerrs ",
                "-Xmaxwarns ");
        checkOutput(Output.OUT, expectFound,
                "-Xdocrootparent ",
                "-Xdoclint ",
                "-Xdoclint:");
    }
}
