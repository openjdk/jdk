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
 * @bug      4764045 8004825 8026567
 * @summary  This test ensures that the value tag works in all
 * use cases. The explainations for each test case are written below.
 * @author   jamieh
 * @library ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    JavadocTester
 * @run main TestValueTag
 */

public class TestValueTag extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestValueTag tester = new TestValueTag();
        tester.runTests();
    }

    @Test
    void test1() {
        javadoc("-d", "out1",
                "-sourcepath", testSrc,
                "-tag", "todo",
                "pkg1", "pkg2");
        checkExit(Exit.FAILED);

        checkOutput("pkg1/Class1.html", true,
                // Base case:  using @value on a constant.
                "Result:  \"Test 1 passes\"",
                // Retrieve value of constant in same class.
                "Result:  <a href=\"../pkg1/Class1.html#TEST_2_PASSES\">\"Test 2 passes\"</a>",
                "Result:  <a href=\"../pkg1/Class1.html#TEST_3_PASSES\">\"Test 3 passes\"</a>",
                "Result:  <a href=\"../pkg1/Class1.html#TEST_4_PASSES\">\"Test 4 passes\"</a>",
                "Result:  <a href=\"../pkg1/Class1.html#TEST_5_PASSES\">\"Test 5 passes\"</a>",
                "Result:  <a href=\"../pkg1/Class1.html#TEST_6_PASSES\">\"Test 6 passes\"</a>");

        checkOutput("pkg1/Class2.html", true,
                // Retrieve value of constant in different class.
                "Result:  <a href=\"../pkg1/Class1.html#TEST_7_PASSES\">\"Test 7 passes\"</a>",
                "Result:  <a href=\"../pkg1/Class1.html#TEST_8_PASSES\">\"Test 8 passes\"</a>",
                "Result:  <a href=\"../pkg1/Class1.html#TEST_9_PASSES\">\"Test 9 passes\"</a>",
                "Result:  <a href=\"../pkg1/Class1.html#TEST_10_PASSES\">\"Test 10 passes\"</a>",
                "Result:  <a href=\"../pkg1/Class1.html#TEST_11_PASSES\">\"Test 11 passes\"</a>",
                // Retrieve value of constant in different package
                "Result:  <a href=\"../pkg2/Class3.html#TEST_12_PASSES\">\"Test 12 passes\"</a>",
                "Result:  <a href=\"../pkg2/Class3.html#TEST_13_PASSES\">\"Test 13 passes\"</a>",
                "Result:  <a href=\"../pkg2/Class3.html#TEST_14_PASSES\">\"Test 14 passes\"</a>",
                "Result:  <a href=\"../pkg2/Class3.html#TEST_15_PASSES\">\"Test 15 passes\"</a>",
                "Result:  <a href=\"../pkg2/Class3.html#TEST_16_PASSES\">\"Test 16 passes\"</a>");

        checkOutput("pkg2/package-summary.html", true,
                // Retrieve value of constant from a package page
                "Result: <a href=\"../pkg2/Class3.html#TEST_17_PASSES\">\"Test 17 passes\"</a>");

        checkOutput("pkg1/CustomTagUsage.html", true,
                // Test @value tag used with custom tag.
                "<dt><span class=\"simpleTagLabel\">Todo:</span></dt>\n" +
                "<dd>the value of this constant is 55.</dd>");

        checkOutput(Output.OUT, true,
                // Test @value errors printed due to invalid use or when used with
                // non-constant or with bad references.
                "error: value does not refer to a constant\n"
                + "     * Result:  {@value TEST_12_ERROR}",
                "error: {@value} not allowed here\n"
                + "     * Result:  {@value}",
                "error: value does not refer to a constant\n"
                + "     * Result:  {@value NULL}",
                "error: {@value} not allowed here\n"
                + "     * Invalid (null): {@value}",
                "error: {@value} not allowed here\n"
                + "     * Invalid (non-constant field): {@value}",
                "error: value does not refer to a constant\n"
                + "     * Here is a bad value reference: {@value UnknownClass#unknownConstant}",
                "error: reference not found\n"
                + "     * Here is a bad value reference: {@value UnknownClass#unknownConstant}",
                "error: {@value} not allowed here\n"
                + "     * @todo the value of this constant is {@value}"
        );

        checkOutput("pkg1/Class1.html", false,
                //Base case:  using @value on a constant.
                "Result:  <a href=\"../pkg1/Class1.html#TEST_12_ERROR\">\"Test 12 "
                + "generates an error message\"</a>");

        checkForException();
    }

    @Test()
    void test2() {
        javadoc("-Xdoclint:none",
                "-d", "out2",
                "-sourcepath", testSrc,
                "-tag", "todo",
                "pkg1", "pkg2");
        checkExit(Exit.OK);
        checkOutput(Output.OUT, true,
                //Test @value warning printed when used with non-constant.
                "warning - @value tag (which references nonConstant) "
                + "can only be used in constants.",
                "warning - @value tag (which references NULL) "
                + "can only be used in constants.",
                "warning - @value tag (which references TEST_12_ERROR) "
                + "can only be used in constants."
// TODO: re-examine these.
//                //Test warning printed for bad reference.
//                "warning - UnknownClass#unknownConstant (referenced by "
//                + "@value tag) is an unknown reference.",
//                //Test warning printed for invalid use of @value.
//                "warning - @value tag cannot be used here."
        );
        checkForException();
    }

    void checkForException() {
        checkOutput(Output.STDERR, false, "DocletAbortException");
    }
}
