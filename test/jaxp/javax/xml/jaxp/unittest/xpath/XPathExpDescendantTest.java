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


import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/*
 * @test
 * @bug 8289509
 * @library /javax/xml/jaxp/unittest
 * @run testng/othervm xpath.XPathExpDescendantTest
 * @summary Tests for XPath descendant/descendant-or-self axis specifier.
 */
public class XPathExpDescendantTest extends XPathTestBase {

    /*
     * DataProvider: provides XPath Axis descendant expressions and equivalent xpath expression.
     */
    @DataProvider(name = "descendantXpath")
    public Object[][] getDescendantXpathExpression() {
        return new Object[][] {
                {"/Customers/descendant::*", "/Customers//*"},
                {"/Customers/descendant::Customer", "//Customer"},
                {"/Customers/descendant::foo:Customer", "//foo:Customer"},
                {"/Customers/Customer[@id='x1']/descendant::Address",
                        "/Customers/Customer[@id='x1']/Address"},
                {"/Customers/Customer[@id='x1']/descendant::*",
                        "/Customers/Customer[@id='x1']//*"},
                {"/Customers/foo:Customer/foo:Address/descendant::*",
                        "/Customers/foo:Customer/foo:Address//*"},
                {"/Customers/descendant::Name", "/Customers//Name"},
                {"/Customers/descendant::Street", "/Customers//Street"},
                {"/Customers/descendant::Street[2]", "Customers/Customer[@id='x2']/Address/Street"},
                {"/Customers/descendant::Street[2]", "(Customers//Street)[2]"},
                {"/Customers/descendant::Street[position() = 2]",
                        "Customers/Customer[@id='x2']/Address/Street"},
                {"/Customers/descendant-or-self::*", "//*"},
                {"/Customers/descendant-or-self::Customer", "/Customers/Customer"},
                {"/Customers/descendant-or-self::foo:Customer", "/Customers/foo:Customer"},
                {"/Customers/Customer[@id='x1']/descendant-or-self::Address",
                        "/Customers/Customer[@id = 'x1']/Address"},
                {"/Customers/Customer[@id='x1']/descendant-or-self::*",
                        "/Customers/Customer[@id='x1'] | /Customers/Customer[@id = 'x1']//*"},
                {"/Customers/foo:Customer/foo:Address/descendant-or-self::*",
                        "/Customers/foo:Customer/foo:Address | /Customers/foo:Customer/foo:Address//*"},
                {"/Customers/Customer/*[descendant::Street]", "/Customers/Customer/Address"},
                {"/Customers/Customer/*[not(descendant::Street)]", "/Customers/Customer/*[name() != \"Address\"]"},
                {"/Customers/Customer/*[descendant-or-self::Street]", "/Customers/Customer/Address"},
                {"/Customers/Customer/*[not(descendant-or-self::Street)]",
                        "/Customers/Customer/*[name() != \"Address\"]"}
        };
    }

    /*
     * DataProvider: provides XPath descendant expressions and expected number of descendant nodes returned
     */
    @DataProvider(name = "descendantXpathNodeCount")
    public Object[][] getDescendantXpathExpressionNodeCount() {
        return new Object[][] {
                {"/Customers/descendant::*", 40},
                {"/Customers/descendant::Customer", 3},
                {"/Customers/descendant::foo:Customer", 1},
                {"/Customers/Customer[@id='x1']/descendant::Address", 1},
                {"/Customers/Customer[@id='x1']/descendant::*", 9},
                {"/Customers/foo:Customer/foo:Address/descendant::*", 3},
                {"/Customers/Customer[@id='x1']/Address/descendant::Address", 0},
                {"/Customers/descendant-or-self::*", 41},
                {"/Customers/descendant-or-self::Customer", 3},
                {"/Customers/descendant-or-self::foo:Customer", 1},
                {"/Customers/Customer[@id='x1']/descendant-or-self::Address", 1},
                {"/Customers/Customer[@id='x1']/Address/descendant-or-self::Address", 1},
                {"/Customers/Customer[@id='x1']/descendant-or-self::*", 10},
                {"/Customers/foo:Customer/foo:Address/descendant-or-self::*", 4},
                {"/Customers/*[descendant::Name]", 3},
                {"/Customers/foo:Customer/*[descendant-or-self::foo:Street]", 1}
        };
    }

    /*
     * DataProvider: provides XPath descendant expressions which should return null.
     */
    @DataProvider(name = "descendantXpathEmpty")
    public Object[][] getDescendantXpathExpressionEmpty() {
        return new Object[][] {
                {"/Customers/Customer/Name/descendant::*"},
                {"/Customers/foo:Customer/descendant::Name"},
                {"/Customers/Customer/descendant::foo:Name"},
                {"/Customers/descendant::id"},
                {"/Customers/Customer/Name/descendant-or-self::id"},
                {"/Customers/foo:Customer/descendant-or-self::Name"},
                {"/Customers/Customer/descendant-or-self::foo:Name"},
                {"/Customers/descendant-or-self::id"}
        };
    }

    /**
     * Verifies descendant xpath expression returns same nodes as returns when used normal xpath expression
     * @param  descexp  descendant XPath expression.
     * @param  expath   normal xPath expression
     * @throws XPathExpressionException
     */
    @Test(dataProvider = "descendantXpath")
    public void descendantExpTests(String descexp, String expath) throws XPathExpressionException {
        Document doc = documentOf(DECLARATION + RAW_XML);
        XPath xPath = XPathFactory.newInstance().newXPath();
        NodeList actualNodeList = (NodeList) xPath.evaluate(descexp, doc, XPathConstants.NODESET);
        NodeList expectedNodeList = (NodeList) xPath.evaluate(expath, doc, XPathConstants.NODESET);
        Assert.assertEquals(actualNodeList.getLength(), expectedNodeList.getLength());

        for(int i = 0; i < actualNodeList.getLength(); i++) {
            actualNodeList.item(i).equals(expectedNodeList.item(i));
        }
    }

    /**
     * Verifies descendant xpath expression return descendant nodes list with correct number of nodes.
     * @param  exp       XPath expression.
     * @param  nodeCount number of descendant nodes in nodelist.
     * @throws XPathExpressionException
     */
    @Test(dataProvider = "descendantXpathNodeCount")
    public void descendantNodesCountTests(String exp, int nodeCount) throws XPathExpressionException {
        Document doc = documentOf(DECLARATION + RAW_XML);
        XPath xPath = XPathFactory.newInstance().newXPath();
        NodeList nodeList = (NodeList) xPath.evaluate(exp, doc, XPathConstants.NODESET);
        Assert.assertEquals(nodeList.getLength(), nodeCount);
    }

    /**
     * Verifies descendant xpath expression return no nodes if descendant expression context nodes don't have matching descendants
     * @param  exp     XPath expression.
     * @throws XPathExpressionException
     */
    @Test(dataProvider = "descendantXpathEmpty")
    public void DescendantScopeTests(String exp) throws XPathExpressionException {
        Document doc = documentOf(DECLARATION + RAW_XML);
        XPath xPath = XPathFactory.newInstance().newXPath();
        Node node = xPath.evaluateExpression(exp, doc, Node.class);
        Assert.assertNull(node);
    }
}
