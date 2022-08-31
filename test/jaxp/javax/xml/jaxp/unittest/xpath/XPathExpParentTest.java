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

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathNodes;

/*
 * @test
 * @bug     8292990
 * @summary Tests the XPath parent axis
 * @library /javax/xml/jaxp/unittest
 *
 * @run testng xpath.XPathExpParentTest
 */
public class XPathExpParentTest extends XPathTestBase {
    private static final Document doc = getDocument();

    /*
     * DataProvider for XPath expressions which should provide one and only
     * one parent node.
     * Data columns:
     *  see parameters of the test "testOneParentNodeExp"
     */
    @DataProvider(name = "oneParentNode")
    public Object[][] getOneParentNodeExp() {
        return new Object[][]{
                {"//Customer/parent::*", "//Customers"},
                {"//Customer[1]/text()/parent::*", "//Customer"},
                {"//Customer[1]/@id/parent::*", "//Customer"},
                {"//Customer[1]/namespace::*/parent::*", "//Customers"},
                {"/Customers/comment()/parent::*", "//Customers"},
                {"//Customer[1]/..", "//Customers"},
                {"//Customer[1]/text()/..", "//Customer"},
                {"//Customer[1]/@id/..", "//Customer"},
                {"//Customer[1]/namespace::*/..", "//Customers"},
                {"/Customers/comment()/..", "//Customers"},
                {"//*[parent::Customers][1]", "//Customer"},
                {"//*[parent::Customers and @id='x1']", "//Customer"},

                // document root
                {"/Customers/parent::node()", "/"},
                {"/Customers/..", "/"},
        };
    }

    /*
     * DataProvider for XPath expressions which should provide no parent node.
     * Data columns:
     *  see parameters of the test "testZeroParentNodeExp"
     */
    @DataProvider(name = "noParentNode")
    public Object[][] getZeroParentNodeExp() {
        return new Object[][]{
                // parent::* never return document root
                {"/Customers/parent::*"},

                // parent of document root
                {"/.."},
        };
    }

    /*
     * DataProvider for XPath relative expressions which should provide a parent node.
     * Data columns:
     *  see parameters of the test "testRelativeParentExp"
     */
    @DataProvider(name = "relativeParent")
    public Object[][] getRelativeParentExp() {
        return new Object[][]{
                {"/Customers", "comment()/parent::*"},
                {"/Customers/Customer[1]", "Name/parent::*"},
                {"/Customers/Customer[1]", "text()/parent::*"},
                {"/Customers/Customer[1]", "@id/parent::*"},
                {"/Customers", "namespace::*/parent::*"},
                {"/Customers", "comment()/parent::*"},
                {"/Customers/Customer[1]", "../Customer[1]/Name/.."},
                {"/Customers/Customer[1]", "../Customer[1]/text()/.."},
                {"/Customers/Customer[1]", "../Customer[1]/@id/.."},
                {"/Customers", "namespace::*/.."},
        };
    }

    /**
     * Verifies that XPath expressions provide one and only parent node.
     *
     * @param exp       XPath expression
     * @param parentExp XPath parent node expression
     * @throws Exception if test failed
     */
    @Test(dataProvider = "oneParentNode")
    void testOneParentNodeExp(String exp, String parentExp)
            throws Exception {
        XPath xPath = XPathFactory.newInstance().newXPath();

        Node expected = xPath.evaluateExpression(parentExp, doc, Node.class);

        testExp(doc, exp, expected, Node.class);

        XPathNodes nodes = xPath.evaluateExpression(exp, doc, XPathNodes.class);

        Assert.assertEquals(nodes.size(), 1);
    }

    /**
     * Verifies that XPath expressions provide no parent node.
     *
     * @param exp XPath expression
     * @throws Exception if test failed
     */
    @Test(dataProvider = "noParentNode")
    void testZeroParentNodeExp(String exp)
            throws Exception {
        testExp(doc, exp, null, Node.class);
    }

    /**
     * Verifies that XPath relative expressions provide a parent node.
     *
     * @param exp         XPath expression
     * @param relativeExp XPath relative expression
     * @throws Exception if test failed
     */
    @Test(dataProvider = "relativeParent")
    void testRelativeParentExp(String exp, String relativeExp) throws Exception {
        XPath xPath = XPathFactory.newInstance().newXPath();
        Node contextNode = xPath.evaluateExpression(exp, doc, Node.class);
        Node relativeNode = xPath.evaluateExpression(relativeExp, contextNode, Node.class);

        Assert.assertEquals(relativeNode, contextNode);
    }
}
