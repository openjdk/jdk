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
import org.w3c.dom.Node;

import javax.xml.xpath.*;

/*
 * @test
 * @bug 8289948
 * @library /javax/xml/jaxp/unittest
 * @run testng xpath.XPathNodeSetFnTest
 * @summary Tests the XPath Node Set Functions
 */
public class XPathNodeSetFnTest extends XPathTestBase {

    private static final Document doc = getDtdDocument();

    /*
     * DataProvider for testing the id function.
     * Data columns:
     *  see parameters of the test "testIdFn"
     */
    @DataProvider(name = "idExpTestCases")
    public Object[][] getIdExp() {
        return new Object[][]{
                {"id('x3')", "Customer_x3"},
                {"id('x1 x2 x3')[3]", "Customer_x3"},
                {"id('x1 | x2 | x3')[3]", "Customer_x3"},
                {"id('x')", "Email_x"},
                {"id(//Customer[3]/@id)", "Customer_x3"},
                {"id(//*[.='123@xyz.com']/@id)", "Email_x"},
        };
    }

    /*
     * DataProvider for testing the count function.
     * Data columns:
     *  see parameters of the test "testCountFn"
     */
    @DataProvider(name = "countExpTestCases")
    public Object[][] getCountExp() {
        return new Object[][]{
                {"count(//Customer)", CUSTOMERS},
                {"count(//@id)", ID_ATTRIBUTES},
                {"count(//Customer/@id)", CUSTOMERS},
                {"count(//@*)", ID_ATTRIBUTES + FOO_ID_ATTRIBUTES},
                {"count(//*)",
                        ROOT + CUSTOMERS + FOO_CUSTOMERS +
                                (CUSTOMERS + FOO_CUSTOMERS) *
                                        CUSTOMER_ELEMENTS},
                {"count(//*[@id])", ID_ATTRIBUTES},
                {"count(./*)", ROOT},
                {"count(//Customer[1]/following::*)",
                        CUSTOMERS - 1 + FOO_CUSTOMERS +
                                (CUSTOMERS - 1 + FOO_CUSTOMERS) *
                                        CUSTOMER_ELEMENTS},
                {"count(//Customer[1]/following-sibling::*)",
                        CUSTOMERS - 1 + FOO_CUSTOMERS},
                {"count(//Customer[3]/preceding::*)",
                        CUSTOMERS - 1 + (CUSTOMERS - 1) * CUSTOMER_ELEMENTS},
                {"count(//Customer[3]/preceding-sibling::*)", CUSTOMERS - 1},
                {"count(//Customer[1]/ancestor::*)", ROOT},
                {"count(//Customer[1]/ancestor-or-self::*)", ROOT + 1},
                {"count(//Customer[1]/descendant::*)", CUSTOMER_ELEMENTS},
                {"count(//Customer[1]/descendant-or-self::*)",
                        CUSTOMER_ELEMENTS + 1},
                {"count(//Customer/node())",
                        ID_ATTRIBUTES + CUSTOMERS * CUSTOMER_ELEMENTS},
        };
    }

    /*
     * DataProvider for testing the position function.
     * Data columns:
     *  see parameters of the test "testPositionFn"
     */
    @DataProvider(name = "positionExpTestCases")
    public Object[][] getPositionExp() {
        return new Object[][]{
                {"//Customer[position()=1]", "Customer_x1"},
                {"//Customer[position()=last()]", "Customer_x3"},
                {"//Customer[position()>1 and position()<last()]",
                        "Customer_x2"},
                {"//Customer[position() mod 2 =0]", "Customer_x2"},
                {"//Customer[last()]", "Customer_x3"},
        };
    }

    /*
     * DataProvider for testing the name and local-name functions.
     * Data columns:
     *  see parameters of the test "testNameFn"
     */
    @DataProvider(name = "nameExpTestCases")
    public Object[][] getNameExp() {
        return new Object[][]{
                {"local-name(//Customer)", "Customer"},
                {"local-name(//foo:Customer)", "Customer"},
                {"local-name(//Customer/@id)", "id"},
                {"local-name(//foo:Customer/@foo:id)", "id"},
                {"local-name(//*[local-name()='Customer'])", "Customer"},
                {"namespace-uri(.)", ""},
                {"namespace-uri(//Customers)", ""},
                {"namespace-uri(//Customer)", ""},
                {"namespace-uri(//foo:Customer)", "foo"},
                {"namespace-uri(//@id)", ""},
                {"namespace-uri(//@foo:id)", "foo"},
                {"name(//*[namespace-uri()=\"foo\"])", "foo:Customer"},
                {"name(//Customer)", "Customer"},
                {"name(//foo:Customer)", "foo:Customer"},
                {"name(//Customer/@id)", "id"},
                {"name(//foo:Customer/@foo:id)", "foo:id"},
                {"name(//*[name()='foo:Customer'])", "foo:Customer"},
        };
    }

    /**
     * Verifies that the result of evaluating the id function matches the
     * expected result.
     *
     * @param exp      XPath expression
     * @param expected expected result
     * @throws Exception if test fails
     */
    @Test(dataProvider = "idExpTestCases")
    void testIdFn(String exp, String expected) throws Exception {
        XPath xPath = XPathFactory.newInstance().newXPath();

        Node node = xPath.evaluateExpression(exp, doc, Node.class);
        Node node2 = (Node) xPath.evaluate(exp, doc, XPathConstants.NODE);

        Assert.assertEquals(node.getNodeName() + "_" +
                        node.getAttributes().item(0).getNodeValue()
                , expected);
        Assert.assertEquals(node2, node);
    }

    /**
     * Verifies that the result of evaluating the count function matches the
     * expected result.
     *
     * @param exp      XPath expression
     * @param expected expected result
     * @throws Exception if test fails
     */
    @Test(dataProvider = "countExpTestCases")
    void testCountFn(String exp, int expected) throws Exception {
        XPath xPath = XPathFactory.newInstance().newXPath();

        double num = xPath.evaluateExpression(exp, doc, Double.class);
        double num2 = (double) xPath.evaluate(exp, doc, XPathConstants.NUMBER);

        Assert.assertEquals(num, expected);
        Assert.assertEquals(num2, num);
    }

    /**
     * Verifies that the result of evaluating the position function matches the
     * expected result.
     *
     * @param exp      XPath expression
     * @param expected expected result
     * @throws Exception if test fails
     */
    @Test(dataProvider = "positionExpTestCases")
    void testPositionFn(String exp, String expected) throws Exception {
        XPath xPath = XPathFactory.newInstance().newXPath();

        Node node = xPath.evaluateExpression(exp, doc, Node.class);
        Node node2 = (Node) xPath.evaluate(exp, doc, XPathConstants.NODE);

        Assert.assertEquals(node.getNodeName() + "_" +
                        node.getAttributes().item(0).getNodeValue()
                , expected);
        Assert.assertEquals(node2, node);
    }

    /**
     * Verifies that the result of evaluating the name and local-name functions
     * matches the expected result.
     *
     * @param exp      XPath expression
     * @param expected expected result
     * @throws Exception if test fails
     */
    @Test(dataProvider = "nameExpTestCases")
    void testNameFn(String exp, String expected) throws Exception {
        XPath xPath = XPathFactory.newInstance().newXPath();

        String s = xPath.evaluateExpression(exp, doc, String.class);
        String s2 = (String) xPath.evaluate(exp, doc, XPathConstants.STRING);

        Assert.assertEquals(s, expected);
        Assert.assertEquals(s2, s);
    }
}
