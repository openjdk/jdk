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
 * @bug 8290837
 * @library /javax/xml/jaxp/unittest
 * @run testng xpath.XPathBooleanFnTest
 * @summary Tests the XPath Boolean Functions
 */
public class XPathBooleanFnTest extends XPathTestBase {

    private static final Document doc = getDtdDocument();

    /*
     * DataProvider for testing the boolean, not, true, false and lang
     * functions.
     * Data columns:
     *  see parameters of the test "testBooleanFn"
     */
    @DataProvider(name = "booleanExpTestCases")
    public Object[][] getBooleanExp() {
        return new Object[][]{
                {"true()", true},
                {"false()", false},

                {"boolean(true())", true},
                {"boolean(false())", false},
                {"boolean(1)", true},
                {"boolean(0)", false},
                {"boolean(-1)", true},
                {"boolean(1+1)", true},
                {"boolean(1-1)", false},
                {"boolean(1+'abc')", false},
                {"boolean('abc')", true},
                {"boolean('')", false},
                {"boolean(//Name)", true},
                {"boolean(//LastName)", false},
                {"boolean(//Customer[1]/ClubMember)", true},
                {"boolean(//Customer[2]/ClubMember)", true},
                {"boolean(//Customer[2]/ClubMember='true')", false},
                {"boolean(//Customer[1]/ClubMember or " +
                        "//Customer[2]/ClubMember)", true},
                {"boolean(//Customer[1]/ClubMember and " +
                        "//Customer[2]/ClubMember)", true},
                {"boolean(//*[boolean(.)=true()])", true},
                {"boolean(//*[boolean(.)=false()])", false},

                {"not(1)", false},
                {"not(-1)", false},
                {"not(0)", true},
                {"not(true())", false},
                {"not(false())", true},
                {"not(//Customer[1]/ClubMember)", false},
                {"not(//Customer[2]/ClubMember)", false},
                {"not(//Customer[2]/ClubMember='true')", true},
                {"boolean(//*[not(.)=true()])", false},
                {"boolean(//*[not(.)=false()])", true},

                {"boolean(//*[lang('en')])", true},
                {"boolean(//*[lang('es')])", false},
        };
    }

    /*
     * DataProvider for testing XPathExpressionException being thrown on
     * invalid boolean function usage.
     * Data columns:
     *  see parameters of the test "testExceptionOnEval"
     */
    @DataProvider(name = "exceptionExpTestCases")
    public Object[][] getExceptionExp() {
        return new Object[][]{
                // Argument is required for these functions
                {"boolean()"},
                {"//*[boolean()=true()]"},
                {"not()"},
                {"//*[not()=true()]"},
                {"lang()"},
                {"/*[lang()=true()]"},

                // No arguments should be passed to these functions
                {"true(1)"},
                {"false(0)"},
                {"//*[true(.)=true()]"},
                {"//*[false(.)=false()]"},
        };
    }

    /**
     * Verifies that the result of evaluating the boolean, not, true, false
     * and lang functions matches the expected result.
     *
     * @param exp      XPath expression
     * @param expected expected result
     * @throws Exception if test fails
     */
    @Test(dataProvider = "booleanExpTestCases")
    void testBooleanFn(String exp, boolean expected) throws Exception {
        testExp(doc, exp, expected, Boolean.class);
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
