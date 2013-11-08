/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @library  ../lib/
 * @build    JavadocTester TestValueTag
 * @run main TestValueTag
 */

public class TestValueTag extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "4764045";

    //Javadoc arguments.
    private static final String[] ARGS =
        new String[] {
            "-d", BUG_ID, "-sourcepath", SRC_DIR, "-tag",
            "todo", "pkg1", "pkg2"
        };

    private static final String[] ARGS1 =
        new String[] {
            "-Xdoclint:none",
            "-d", BUG_ID + "-1", "-sourcepath", SRC_DIR, "-tag",
            "todo", "pkg1", "pkg2"
        };

    //Input for string search tests.
    private static final String[][] TEST = {
        //Base case:  using @value on a constant.
        {BUG_ID + FS + "pkg1" + FS + "Class1.html",
            "Result:  \"Test 1 passes\""},
        //Retrieve value of constant in same class.
        {BUG_ID + FS + "pkg1" + FS + "Class1.html",
            "Result:  <a href=\"../pkg1/Class1.html#TEST_2_PASSES\">\"Test 2 passes\"</a>"},
        {BUG_ID + FS + "pkg1" + FS + "Class1.html",
            "Result:  <a href=\"../pkg1/Class1.html#TEST_3_PASSES\">\"Test 3 passes\"</a>"},
        {BUG_ID + FS + "pkg1" + FS + "Class1.html",
            "Result:  <a href=\"../pkg1/Class1.html#TEST_4_PASSES\">\"Test 4 passes\"</a>"},
        {BUG_ID + FS + "pkg1" + FS + "Class1.html",
            "Result:  <a href=\"../pkg1/Class1.html#TEST_5_PASSES\">\"Test 5 passes\"</a>"},
        {BUG_ID + FS + "pkg1" + FS + "Class1.html",
            "Result:  <a href=\"../pkg1/Class1.html#TEST_6_PASSES\">\"Test 6 passes\"</a>"},
        //Retrieve value of constant in different class.
        {BUG_ID + FS + "pkg1" + FS + "Class2.html",
            "Result:  <a href=\"../pkg1/Class1.html#TEST_7_PASSES\">\"Test 7 passes\"</a>"},
        {BUG_ID + FS + "pkg1" + FS + "Class2.html",
            "Result:  <a href=\"../pkg1/Class1.html#TEST_8_PASSES\">\"Test 8 passes\"</a>"},
        {BUG_ID + FS + "pkg1" + FS + "Class2.html",
            "Result:  <a href=\"../pkg1/Class1.html#TEST_9_PASSES\">\"Test 9 passes\"</a>"},
        {BUG_ID + FS + "pkg1" + FS + "Class2.html",
            "Result:  <a href=\"../pkg1/Class1.html#TEST_10_PASSES\">\"Test 10 passes\"</a>"},
        {BUG_ID + FS + "pkg1" + FS + "Class2.html",
            "Result:  <a href=\"../pkg1/Class1.html#TEST_11_PASSES\">\"Test 11 passes\"</a>"},
        //Retrieve value of constant in different package
        {BUG_ID + FS + "pkg1" + FS + "Class2.html",
            "Result:  <a href=\"../pkg2/Class3.html#TEST_12_PASSES\">\"Test 12 passes\"</a>"},
        {BUG_ID + FS + "pkg1" + FS + "Class2.html",
            "Result:  <a href=\"../pkg2/Class3.html#TEST_13_PASSES\">\"Test 13 passes\"</a>"},
        {BUG_ID + FS + "pkg1" + FS + "Class2.html",
            "Result:  <a href=\"../pkg2/Class3.html#TEST_14_PASSES\">\"Test 14 passes\"</a>"},
        {BUG_ID + FS + "pkg1" + FS + "Class2.html",
            "Result:  <a href=\"../pkg2/Class3.html#TEST_15_PASSES\">\"Test 15 passes\"</a>"},
        {BUG_ID + FS + "pkg1" + FS + "Class2.html",
            "Result:  <a href=\"../pkg2/Class3.html#TEST_16_PASSES\">\"Test 16 passes\"</a>"},
        //Retrieve value of constant from a package page
        {BUG_ID + FS + "pkg2" + FS + "package-summary.html",
            "Result: <a href=\"../pkg2/Class3.html#TEST_17_PASSES\">\"Test 17 passes\"</a>"},
        //Test @value tag used with custom tag.
        {BUG_ID + FS + "pkg1" + FS + "CustomTagUsage.html",
            "<dt><span class=\"simpleTagLabel\">Todo:</span></dt>" + NL +
                "<dd>the value of this constant is 55.</dd>"},
        //Test @value errors printed dues to invalid use or when used with
        //non-constant or with bad references.
        {ERROR_OUTPUT,"error: value does not refer to a constant" + NL +
            "     * Result:  {@value TEST_12_ERROR}"
        },
        {ERROR_OUTPUT,"error: {@value} not allowed here" + NL +
            "     * Result:  {@value}"
        },
        {ERROR_OUTPUT,"error: value does not refer to a constant" + NL +
            "     * Result:  {@value NULL}"
        },
        {ERROR_OUTPUT,"error: {@value} not allowed here" + NL +
            "     * Invalid (null): {@value}"
        },
        {ERROR_OUTPUT,"error: {@value} not allowed here" + NL +
            "     * Invalid (non-constant field): {@value}"
        },
        {ERROR_OUTPUT,"error: value does not refer to a constant" + NL +
            "     * Here is a bad value reference: {@value UnknownClass#unknownConstant}"
        },
        {ERROR_OUTPUT,"error: reference not found" + NL +
            "     * Here is a bad value reference: {@value UnknownClass#unknownConstant}"
        },
        {ERROR_OUTPUT,"error: {@value} not allowed here" + NL +
            "     * @todo the value of this constant is {@value}"
        }
    };
    private static final String[][] TEST1 = {
        //Test @value warning printed when used with non-constant.
        {WARNING_OUTPUT,"warning - @value tag (which references nonConstant) " +
            "can only be used in constants."
        },
        {WARNING_OUTPUT,"warning - @value tag (which references NULL) " +
            "can only be used in constants."
        },
        {WARNING_OUTPUT,"warning - @value tag (which references TEST_12_ERROR) " +
            "can only be used in constants."
        },
        //Test warning printed for bad reference.
        {WARNING_OUTPUT,"warning - UnknownClass#unknownConstant (referenced by " +
            "@value tag) is an unknown reference."
        },
        //Test warning printed for invalid use of @value.
        {WARNING_OUTPUT,"warning - @value tag cannot be used here."
        }
    };
    private static final String[][] NEGATED_TEST = {
        //Base case:  using @value on a constant.
        {BUG_ID + FS + "pkg1" + FS + "Class1.html",
            "Result:  <a href=\"../pkg1/Class1.html#TEST_12_ERROR\">\"Test 12 " +
            "generates an error message\"</a>"},
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestValueTag tester = new TestValueTag();
        run(tester, ARGS, TEST, NEGATED_TEST);
        checkForException(tester);
        run(tester, ARGS1, TEST1, NO_TEST);
        checkForException(tester);
        tester.printSummary();
    }

    public static void checkForException(TestValueTag tester) {
        if (tester.getErrorOutput().contains("DocletAbortException")) {
            throw new AssertionError("javadoc threw DocletAbortException");
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getBugId() {
        return BUG_ID;
    }

    /**
     * {@inheritDoc}
     */
    public String getBugName() {
        return getClass().getName();
    }
}
