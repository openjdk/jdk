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
 * @run testng xpath.XPathExpFnTest
 * @summary Test for XPath functions
 */
public class XPathExpFnTest extends XPathTestBase {

    private static final Document doc = getDtdDocument();

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

    @DataProvider(name = "countExpTestCases")
    public Object[][] getCountExp() {
        return new Object[][]{
                {"count(//Customer)", CUSTOMERS},
                {"count(//@id)", CUSTOMERS + EMAILS},
                {"count(//Customer/@id)", CUSTOMERS},
                {"count(//@*)", CUSTOMERS + EMAILS},
                {"count(//*)",
                        1 + CUSTOMERS + CUSTOMERS * CUSTOMER_ATTRIBUTES},
                {"count(//*[@id])", CUSTOMERS + EMAILS},
                {"count(./*)", 1},
                {"count(//Customer[1]/following::*)",
                        CUSTOMERS - 1 + (CUSTOMERS - 1) * CUSTOMER_ATTRIBUTES},
                {"count(//Customer[1]/following-sibling::*)", CUSTOMERS - 1},
                {"count(//Customer[3]/preceding::*)",
                        CUSTOMERS - 1 + (CUSTOMERS - 1) * CUSTOMER_ATTRIBUTES},
                {"count(//Customer[3]/preceding-sibling::*)", CUSTOMERS - 1},
                {"count(//Customer[1]/ancestor::*)", 1},
                {"count(//Customer[1]/ancestor-or-self::*)", 2},
                {"count(//Customer[1]/descendant::*)", CUSTOMER_ATTRIBUTES},
                {"count(//Customer[1]/descendant-or-self::*)",
                        CUSTOMER_ATTRIBUTES + 1},
                {"count(//Customer/node())",
                        CUSTOMERS + EMAILS + CUSTOMERS * CUSTOMER_ATTRIBUTES},
        };
    }

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

    @DataProvider(name = "nameExpTestCases")
    public Object[][] getNameExp() {
        return new Object[][]{
                {"local-name(//Customer)", "Customer"},
                {"local-name(//Customer/@id)", "id"},
                {"local-name(//*[local-name()='Customer'])", "Customer"},
                {"namespace-uri(//Customer)", ""},
                {"name(//Customer)", "Customer"},
                {"name(//Customer/@id)", "id"},
                {"name(//*[name()='Customer'])", "Customer"},
        };
    }

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

    @Test(dataProvider = "countExpTestCases")
    void testCountFn(String exp, int expected) throws Exception {
        XPath xPath = XPathFactory.newInstance().newXPath();

        double num = xPath.evaluateExpression(exp, doc, Double.class);
        double num2 = (double) xPath.evaluate(exp, doc, XPathConstants.NUMBER);

        Assert.assertEquals(num, expected);
        Assert.assertEquals(num2, num);
    }

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

    @Test(dataProvider = "nameExpTestCases")
    void testNameFn(String exp, String expected) throws Exception {
        XPath xPath = XPathFactory.newInstance().newXPath();

        String s = xPath.evaluateExpression(exp, doc, String.class);
        String s2 = (String) xPath.evaluate(exp, doc, XPathConstants.STRING);

        Assert.assertEquals(s, expected);
        Assert.assertEquals(s2, s);
    }
}
