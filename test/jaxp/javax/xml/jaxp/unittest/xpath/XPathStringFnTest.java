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
 * @bug 8290836
 * @library /javax/xml/jaxp/unittest
 * @run testng xpath.XPathStringFnTest
 * @summary Tests the XPath String Functions
 */
public class XPathStringFnTest extends XPathTestBase {

    private static final Document doc = getDtdDocument();

    /*
     * DataProvider for testing the string function.
     * Data columns:
     *  see parameters of the test "testStringFn"
     */
    @DataProvider(name = "stringExpTestCases")
    public Object[][] getStringExp() {
        return new Object[][]{
                {"string(-0.0)", "0"},
                {"string(0-1)", "-1"},
                {"string(1=1)", "true"},
                {"string(1>2)", "false"},
                {"string(1+a)", "NaN"},
                {"string(1.0 div 0)", "Infinity"},
                {"string(-1.0 div 0)", "-Infinity"},
                {"string(//Customer/Name)", "name1"},
                {"string(//Customer[1]/@id)", "x1"},
                {"string(//Customer/LastName)", ""},
                {"string(//Customer[1]/Age)",
                        Integer.toString(CUSTOMER_AGES[0])},
                {"string(number(//Customer[2]/Age))",
                        Integer.toString(CUSTOMER_AGES[1])},
                {"string(//Customer[1]/Age + //Customer[2]/Age)",
                        Integer.toString(CUSTOMER_AGES[0] + CUSTOMER_AGES[1])},
                {"string(//Customer[1]/Age + //Customer[1]/Name)", "NaN"},
                {"string(//Customer[1]/ClubMember='true')", "true"},
                {"string(//Customer[2]/ClubMember='true')", "false"},
                {"string(//Customer[1]/ClubMember or " +
                        "//Customer[2]/ClubMember)", "true"},
                {"string(//Customer[1]/ClubMember and " +
                        "//Customer[2]/ClubMember='true')", "false"},
                {"string(//*[string()='name2'])", "name2"},
                {"string(//*[string(.)='name2'])", "name2"},
                {"string(//*[string(Name)='name2']/Name)", "name2"},
                {"string(//*[string(@id)='x1']//Street)", "1111 111st ave"},
                {"string(//*[string(../@id)='x1']/Street)", "1111 111st ave"},
        };
    }

    /*
     * DataProvider for testing the concat function.
     * Data columns:
     *  see parameters of the test "testConcatFn"
     */
    @DataProvider(name = "concatExpTestCases")
    public Object[][] getConcatExp() {
        return new Object[][]{
                {"concat('Hello', ' name', 1, true())", "Hello name1true"},
                {"concat('Hello ', //Customer/Name)", "Hello name1"},
                {"concat('Hello ', //Customer/LastName)", "Hello "},
                {"concat('(', //@id, ')')", "(x1)"},
        };
    }

    /*
     * DataProvider for testing the substring, substring-before and
     * substring-after functions.
     * Data columns:
     *  see parameters of the test "testSubstringFn"
     */
    @DataProvider(name = "substringExpTestCases")
    public Object[][] getSubstringExp() {

        return new Object[][]{
                {"substring('123@xyz.com', 5, 7)", "xyz.com"},
                {"substring('123@xyz.com', 5, 10)", "xyz.com"},
                {"substring(//Email, 5, 7)", "xyz.com"},
                {"substring(//Email, 5)", "xyz.com"},
                {"substring(//Email, 1, string-length(.))", "123@xyz.com"},
                {"substring(//Email, 1, string-length(.)+1)", "123@xyz.com"},
                {"substring(//LastName, 1)", ""},
                {"string(//*[substring(., 1)='name2'])", "name2"},

                {"substring-before('123@xyz.com', '@')", "123"},
                {"substring-before(//Email, '@')", "123"},
                {"substring-before(//Email, '?')", ""},
                {"substring-before(//Email, '')", ""},
                {"substring-before(//LastName, '')", ""},
                {"string(//*[substring-before(., ' ')='1111'])", "1111 111st " +
                        "ave"},

                {"substring-after('123@xyz.com', '@')", "xyz.com"},
                {"substring-after(//Email, '@')", "xyz.com"},
                {"substring-after(//Email, '?')", ""},
                {"substring-after(//Email, '')", "123@xyz.com"},
                {"substring-after(//LastName, '')", ""},
                {"string(//*[substring-after(., ' ')='111st ave'])", "1111 " +
                        "111st ave"},
        };
    }

    /*
     * DataProvider for testing the normalize-space function.
     * Data columns:
     *  see parameters of the test "testNormalizeSpaceFn"
     */
    @DataProvider(name = "normalizeExpTestCases")
    public Object[][] getNormalizeExp() {
        return new Object[][]{
                {"normalize-space('  1111   111st   ave  ')", "1111 111st ave"},
                {"normalize-space(true())", "true"},
                {"normalize-space(1.234)", "1.234"},
                {"normalize-space(//Customer[1]//Street)", "1111 111st ave"},
                {"normalize-space(//Customer[2]//Street)", "2222 222nd ave"},
                {"normalize-space(//LastName)", ""},
                {"string(//*[normalize-space()='name1'])", "name1"},
                {"string(//*[normalize-space(.)='name1'])", "name1"},
                {"string(//*[normalize-space(Name)='name2']/Name)", "name2"},
                {"string(//*[normalize-space(@id)='x1']//Street)", "1111 " +
                        "111st ave"},
                {"string(//*[normalize-space(../@id)='x1']/Street)", "1111 " +
                        "111st ave"},
        };
    }

    /*
     * DataProvider for testing the translate function.
     * Data columns:
     *  see parameters of the test "testTranslateFn"
     */
    @DataProvider(name = "translateExpTestCases")
    public Object[][] getTranslateExp() {
        return new Object[][]{
                {"translate('1111 111st ave', ' ', '')", "1111111stave"},
                {"translate('1111 111st ave', '', '')", "1111 111st ave"},
                {"translate('1111 111st ave', '1 ', '')", "stave"},
                {"translate('abcabcdcd', 'abcd', 'xyz')", "xyzxyzz"},
                {"translate('abcabcdcd', 'bcd', 'uvwxyz')", "auvauvwvw"},
                {"translate('aabccdacbabcb', 'aaccbbdd', 'wxyz')", "wwyywywy"},
                {"translate(//Customer[1]//Street, " +
                        "'abcdefghijklmnopqrstuvwxyz', " +
                        "'ABCDEFGHIJKLMNOPQRSTUVWXYZ')",
                        "1111 111ST AVE"},
                {"translate(//LastName, 'name', 'NAME')", ""},
                {"translate(true(), true(), false())", "fals"},
                {"translate(123, 2, 3)", "133"},
        };
    }

    /*
     * DataProvider for testing the string-length function.
     * Data columns:
     *  see parameters of the test "testStringLengthFn"
     */
    @DataProvider(name = "stringLengthExpTestCases")
    public Object[][] getStringLengthExp() {
        return new Object[][]{
                {"string-length('')", 0},
                {"string-length(123)", 3},
                {"string-length(true())", 4},
                {"string-length('1111 111st ave')", 14.0},
                {"string-length(//Customer[1]//Street)", 14.0},
                {"string-length(//LastName)", 0},
                {"string-length(//Customer[1]/Name)", 5},
                {"string-length(//Customer[1]/@id)", 2},
                {"string-length(name(//*[string-length()=10]))", 5},
                {"string-length(name(//*[string-length(.)=10]))", 5},
        };
    }

    /*
     * DataProvider for testing the starts-with function.
     * Data columns:
     *  see parameters of the test "testStartsWithFn"
     */
    @DataProvider(name = "startsWithExpTestCases")
    public Object[][] getStartsWithExp() {
        return new Object[][]{
                {"starts-with(//Email, '123')", true},
                {"starts-with(//Email, 123)", true},
                {"starts-with(//Email, '?')", false},
                {"starts-with(//Email, '')", true},
                {"starts-with(//Customer/@id, 'x')", true},
                {"starts-with(//LastName, '')", true},
                {"boolean(//*[starts-with(., 'name2')]='name2')", true},
                {"boolean(//*[starts-with(text(), 'name')]='name2')", true},
                {"boolean(//*[starts-with(., 'name1')]/.='name2')", false},
        };
    }

    /*
     * DataProvider for testing the contains function.
     * Data columns:
     *  see parameters of the test "testContainsFn"
     */
    @DataProvider(name = "containsExpTestCases")
    public Object[][] getContainsExp() {
        return new Object[][]{
                {"contains(//Email, '123')", true},
                {"contains(//Email, 123)", true},
                {"contains(//Email, '?')", false},
                {"contains(//Email, '')", true},
                {"contains(//Customer/@id, 'x')", true},
                {"contains(//LastName, '')", true},
                {"boolean(//*[contains(., 'name2')]='name2')", true},
                {"boolean(//*[contains(text(), 'name')]='name2')", true},
                {"boolean(//*[contains(., 'name1')]/.='name2')", false},
        };
    }

    /*
     * DataProvider for testing XPathExpressionException being thrown on
     * invalid string function usage.
     * Data columns:
     *  see parameters of the test "testExceptionOnEval"
     */
    @DataProvider(name = "exceptionExpTestCases")
    public Object[][] getExceptionExp() {
        return new Object[][]{
                // At least two arguments are required for these functions
                {"//*[concat()='name2']"},
                {"//*[concat(.)='name2']"},
                {"//*[substring()='name2']"},
                {"//*[substring(.)='name2']"},
                {"//*[substring-before()='name2']"},
                {"//*[substring-before(.)='name2']"},
                {"//*[substring-after()='name2']"},
                {"//*[substring-after(.)='name2']"},
                {"//*[translate()='name2']"},
                {"//*[translate(.)='name2']"},
                {"//*[contains()]"},
                {"//*[contains(.)]"},
                {"//*[starts-with()]"},
                {"//*[starts-with(.)]"},
        };
    }

    /**
     * Verifies that the result of evaluating the string function matches
     * the expected result.
     *
     * @param exp      XPath expression
     * @param expected expected result
     * @throws Exception if test fails
     */
    @Test(dataProvider = "stringExpTestCases")
    void testStringFn(String exp, String expected) throws Exception {
        testExp(doc, exp, expected, String.class);
    }

    /**
     * Verifies that the result of evaluating the concat function matches
     * the expected result.
     *
     * @param exp      XPath expression
     * @param expected expected result
     * @throws Exception if test fails
     */
    @Test(dataProvider = "concatExpTestCases")
    void testConcatFn(String exp, String expected) throws Exception {
        testExp(doc, exp, expected, String.class);
    }

    /**
     * Verifies that the result of evaluating the substring, substring-before
     * and substring-after functions matches the expected result.
     *
     * @param exp      XPath expression
     * @param expected expected result
     * @throws Exception if test fails
     */
    @Test(dataProvider = "substringExpTestCases")
    void testSubstringFn(String exp, String expected) throws Exception {
        testExp(doc, exp, expected, String.class);
    }

    /**
     * Verifies that the result of evaluating the normalize-space function
     * matches the expected result.
     *
     * @param exp      XPath expression
     * @param expected expected result
     * @throws Exception if test fails
     */
    @Test(dataProvider = "normalizeExpTestCases")
    void testNormalizeSpaceFn(String exp, String expected) throws Exception {
        testExp(doc, exp, expected, String.class);
    }

    /**
     * Verifies that the result of evaluating the translate function matches
     * the expected result.
     *
     * @param exp      XPath expression
     * @param expected expected result
     * @throws Exception if test fails
     */
    @Test(dataProvider = "translateExpTestCases")
    void testTranslateFn(String exp, String expected) throws Exception {
        testExp(doc, exp, expected, String.class);
    }

    /**
     * Verifies that the result of evaluating the string-length function matches
     * the expected result.
     *
     * @param exp      XPath expression
     * @param expected expected result
     * @throws Exception if test fails
     */
    @Test(dataProvider = "stringLengthExpTestCases")
    void testStringLengthFn(String exp, double expected) throws Exception {
        testExp(doc, exp, expected, Double.class);
    }

    /**
     * Verifies that the result of evaluating the starts-with function
     * matches the expected result.
     *
     * @param exp      XPath expression
     * @param expected expected result
     * @throws Exception if test fails
     */
    @Test(dataProvider = "startsWithExpTestCases")
    void testStartsWithFn(String exp, boolean expected) throws Exception {
        testExp(doc, exp, expected, Boolean.class);
    }

    /**
     * Verifies that the result of evaluating the contains function matches
     * the expected result.
     *
     * @param exp      XPath expression
     * @param expected expected result
     * @throws Exception if test fails
     */
    @Test(dataProvider = "containsExpTestCases")
    void testContainsFn(String exp, Boolean expected) throws Exception {
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
