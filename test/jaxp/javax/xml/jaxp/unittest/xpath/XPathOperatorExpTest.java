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

/*
 * @test
 * @bug     8289949
 * @summary Tests the XPath operator expressions
 * @library /javax/xml/jaxp/unittest
 *
 * @run testng xpath.XPathOperatorExpTest
 */
public class XPathOperatorExpTest extends XPathTestBase {
    private static final Document doc = getDocument();

    /*
     * DataProvider for testing the XPath operator expressions.
     * Data columns:
     *  see parameters of the test "testOperatorExp"
     */
    @DataProvider(name = "operatorExpTestCases")
    public Object[][] getOperatorExp() {
        return new Object[][]{
                // boolean and relational operators: or, and, =, !=, <, <=, >, >=
                {"string(//Customer[Age > 0]/Name)", "name2"},
                {"string(//Customer[Age < 0]/Name)", "name3"},
                {"string(//Customer[Age = 0]/Name)", "name1"},
                {"count(//Customer[Age >= 0 and Age <= 0])", 1},
                {"count(//Customer[Age >= 0][Age <= 0])", 1},
                {"count(//Customer[Age > 0 or Age < 0])", 2},
                {"count(//Customer[Age != 0])", 2},

                // arithmetic operators: +, -, *, div, mod
                {"string(//Customer[last() div 2]/Name)", "name1"},
                {"string(//Customer[position() * 2 > last()]/Name)", "name2"},
                {"string(//Customer[position() + 1 < last()]/Name)", "name1"},
                {"string(//Customer[last() - 1]/Name)", "name2"},
                {"string(//Customer[last() mod 2]/Name)", "name1"},

                // union operator: |
                {"count(//Customer[Name='name1'] | //Customer[Name='name2'])",
                        2},
                {"count(//Customer[Name='name1'] | //Customer[Name='name2'] |" +
                        " //Customer[Name='name3'])", 3},

                // operator precedence
                {"1 + 2 * 3 + 3", 10.0},
                {"1 + 1 div 2 + 2", 3.5},
                {"1 + 1 mod 2 + 2", 4.0},
                {"1 * 1 mod 2 div 2", 0},
                {"1 * (1 mod 2) div 2", 0.5},
                {"(1 + 2) * (3 + 3)", 18.0},
                {"(1 + 2) div (3 + 3)", 0.5},
                {"1 - 2 < 3 + 3", true},
                {"1 * 2 >= 3 div 3", true},
                {"3 > 2 > 1", false},
                {"3 > (2 > 1)", true},
                {"3 > 2 = 1", true},
                {"1 = 3 > 2", true},
                {"1 = 2 or 1 <= 2 and 2 != 2", false},
        };
    }

    /*
     * DataProvider for testing XPathExpressionException being thrown on
     * invalid operator usage.
     * Data columns:
     *  see parameters of the test "testExceptionOnEval"
     */
    @DataProvider(name = "exceptionExpTestCases")
    public Object[][] getExceptionExp() {
        return new Object[][]{
                // invalid operators
                {"string(//Customer[last() / 2]/Name)"},
                {"string(//Customer[last() % 2]/Name)"},
                {"count(//Customer[Name='name1'] & //Customer[Name='name2'])"},
                {"count(//Customer[Name='name1'] && //Customer[Name='name2'])"},
                {"count(//Customer[Name='name1'] || //Customer[Name='name2'])"},

                // union operator only works for node-sets
                {"//Customer[Name='name1'] | string(//Customer[Name='name2']))"},
        };
    }

    /**
     * Verifies that the result of evaluating XPath operators matches the
     * expected result.
     *
     * @param exp      XPath expression
     * @param expected expected result
     * @throws Exception if test fails
     */
    @Test(dataProvider = "operatorExpTestCases")
    void testOperatorExp(String exp, Object expected) throws Exception {
        if (expected instanceof Double d) {
            testExp(doc, exp, d, Double.class);
        } else if (expected instanceof String s) {
            testExp(doc, exp, s, String.class);
        } else if (expected instanceof  Boolean b) {
            testExp(doc, exp, b, Boolean.class);
        }
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
