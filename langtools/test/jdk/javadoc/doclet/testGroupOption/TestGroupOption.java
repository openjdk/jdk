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
 * @bug      4924383
 * @summary  Test to make sure the -group option does not cause a bad warning
 *           to be printed. Test for the group defined using patterns.
 * @author   jamieh
 * @library  ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    JavadocTester
 * @run main TestGroupOption
 */

public class TestGroupOption extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestGroupOption tester = new TestGroupOption();
        tester.runTests();
    }

    @Test
    void test1() {
        // Make sure the warning is not printed when -group is used correctly.
        javadoc("-d", "out-1",
                "-sourcepath", testSrc,
                "-group", "Package One", "pkg1",
                "-group", "Package Two", "pkg2",
                "-group", "Package Three", "pkg3",
                "pkg1", "pkg2", "pkg3");
        checkExit(Exit.OK);

        checkOutput(Output.OUT, false,
                "-group");
    }

    // @Test
    // @ignore 8149402
    // Make sure the "Other packages" section is printed and the header for empty section is not.
    // Make sure that the headers of group that is defined using patterns are printed.
    void test2() {
        javadoc("-d", "out-2",
                "-sourcepath", testSrc,
                "-group", "Group pkg*", "pkg*",
                "-group", "Group abc*", "abc*",
                "-group", "Empty group", "qwerty*",
                "-group", "Group a*", "a*",
                "pkg1", "pkg2", "pkg3", "abc1",  "abc2", "abc3", "other", testSrc("C.java"));
        checkExit(Exit.OK);

        checkOutput("overview-summary.html", true, "Group pkg*", "Group abc*", "Other Packages");
        checkOutput("overview-summary.html", false, "Empty group", "Group a*");
    }

    @Test
    void test3() {
        // Make sure the warning is printed when -group is not used correctly.
        javadoc("-d", "out-3",
                "-sourcepath", testSrc,
                "-group", "Package One", "pkg1",
                "-group", "Package One", "pkg2",
                "-group", "Package One", "pkg3",
                "pkg1", "pkg2", "pkg3");
        checkExit(Exit.OK);

        checkOutput(Output.OUT, true,
                "-group");

    }
}
