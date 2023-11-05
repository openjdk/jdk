/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package xpath;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.w3c.dom.Document;

import javax.xml.xpath.XPathExpressionException;
import java.util.stream.IntStream;

/*
 * @test
 * @bug 8290838
 * @library /javax/xml/jaxp/unittest
 * @run testng xpath.XPathNumberFnTest
 * @summary Tests the XPath Number Functions
 */
public class XPathNumberFnTest extends XPathTestBase {

    private static final Document doc = getDtdDocument();

    /*
     * DataProvider for testing the number, sum, floor, ceiling and round
     * functions.
     * Data columns:
     *  see parameters of the test "testNumberFn"
     */
    @DataProvider(name = "numberExpTestCases")
    public Object[][] getNumberExp() {
        return new Object[][]{
                {"number(1)", 1.0},
                {"number(-1)", -1.0},
                {"number(0)", 0},
                {"number(//Customer[2]/Age)", CUSTOMER_AGES[1]},
                {"number(//Customer[1]/Age + //Customer[2]/Age)",
                        CUSTOMER_AGES[0] + CUSTOMER_AGES[1]},
                {"number('abc')", Double.NaN},
                {"number('')", Double.NaN},
                {String.format("number(//Age[number()=%d])", CUSTOMER_AGES[1]),
                        CUSTOMER_AGES[1]},
                {String.format("number(//Age[number(.)=%d])", CUSTOMER_AGES[1]),
                        CUSTOMER_AGES[1]},
                {"number(//Customer[1]/Name)", Double.NaN},
                {"number(//Customer[2]/Age + //Customer[1]/Name)", Double.NaN},
                {"number(true())", 1},
                {"number(false())", 0},

                {"sum(//Age)", IntStream.of(CUSTOMER_AGES).sum()},
                {"sum(//Customer[2]/Age)", CUSTOMER_AGES[1]},

                {"floor(1.1)", 1.0},
                {"floor(-1.6)", -2.0},
                {"floor(1.0 div 0)", Double.POSITIVE_INFINITY},
                {"floor(-1.0 div 0)", Double.NEGATIVE_INFINITY},
                {"floor(true())", 1},
                {"floor(false())", 0},
                {"floor(abc)", Double.NaN},
                {"floor('')", Double.NaN},
                {"floor(//Customer[2]/Age)", CUSTOMER_AGES[1]},
                {"floor(//Customer[1]/Name)", Double.NaN},
                {String.format("number(//Age[floor(.)=%d])", CUSTOMER_AGES[1]),
                        CUSTOMER_AGES[1]},

                {"ceiling(1.1)", 2.0},
                {"ceiling(-1.4)", -1.0},
                {"ceiling(1.0 div 0)", Double.POSITIVE_INFINITY},
                {"ceiling(-1.0 div 0)", Double.NEGATIVE_INFINITY},
                {"ceiling(true())", 1},
                {"ceiling(false())", 0},
                {"ceiling(abc)", Double.NaN},
                {"ceiling('')", Double.NaN},
                {"ceiling(//Customer[2]/Age)", CUSTOMER_AGES[1]},
                {"ceiling(//Customer[1]/Name)", Double.NaN},
                {String.format("number(//Age[ceiling(.)=%d])",
                        CUSTOMER_AGES[1]), CUSTOMER_AGES[1]},

                {"round(1.49)", 1.0},
                {"round(1.5)", 2.0},
                {"round(-1.5)", -1.0},
                {"round(-1.51)", -2.0},
                {"round(1.0 div 0)", Double.POSITIVE_INFINITY},
                {"round(-1.0 div 0)", Double.NEGATIVE_INFINITY},
                {"round(true())", 1},
                {"round(false())", 0},
                {"round(abc)", Double.NaN},
                {"round('')", Double.NaN},
                {"round(//Customer[2]/Age)", CUSTOMER_AGES[1]},
                {"round(//Customer[1]/Name)", Double.NaN},
                {String.format("number(//Age[round(.)=%d])",
                        CUSTOMER_AGES[1]), CUSTOMER_AGES[1]},
        };
    }

    /*
     * DataProvider for testing XPathExpressionException being thrown on
     * invalid number function usage.
     * Data columns:
     *  see parameters of the test "testExceptionOnEval"
     */
    @DataProvider(name = "exceptionExpTestCases")
    public Object[][] getExceptionExp() {
        return new Object[][]{
                // Argument is required for these functions
                {"//Age[floor()=1.0]"},
                {"(/Age[ceiling()=1.0]"},
                {"//Age[round()=1.0]"},
                {"//Age[sum()]"},

                // Node-set argument is required for these functions
                {"sum(1)"},
                {"sum(true())"},
                {"sum('')"},
                {"sum('abc')"},
        };
    }

    /**
     * Verifies that the result of evaluating the number, sum, floor, ceiling
     * and round functions matches the expected result.
     *
     * @param exp      XPath expression
     * @param expected expected result
     * @throws Exception if test fails
     */
    @Test(dataProvider = "numberExpTestCases")
    void testNumberFn(String exp, double expected) throws Exception {
        testExp(doc, exp, expected, Double.class);
    }

    /**
     * Verifies that XPathExpressionException is thrown on xpath evaluation.
     *
     * @param exp XPath expression
     */
    @Test(dataProvider = "exceptionExpTestCases")
    void testExceptionOnEval(String exp) {
        Assert.assertThrows(XPathExpressionException.class, () -> testEval(doc,
                exp));
    }
}
