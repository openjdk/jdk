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
 * @run testng/othervm xpath.XPathExpFollowingTest
 * @summary Tests for XPath following/following-sibling axis specifier.
 */
public class XPathExpFollowingTest extends XPathTestBase {
    /*
     * DataProvider: provides XPath Axis following expressions and equivalent xpath expression.
     */
    @DataProvider(name = "followingXpath")
    public Object[][] getFollowingXpathExpression() {
        return new Object[][] {
                {"/Customers/following::*", "/None"},
                {"/Customers/Customer/following::Customer", "//Customer[@id != 'x1']"},
                {"/Customers/Customer/following::foo:Customer", "//foo:Customer"},
                {"/Customers/Customer[@id='x1']/following::Address",
                        "/Customers/Customer[@id != 'x1']/Address"},
                {"/Customers/Customer[@id='x1']/following::Street",
                        "/Customers/Customer[@id != 'x1']/Address/Street"},
                {"/Customers/Customer[@id='x1']/following::Street[2]",
                        "/Customers/Customer[@id='x2']/Address/Street"},
                {"/Customers/Customer[@id='x1']/following::*",
                        "/Customers/Customer[@id != 'x1']/descendant-or-self::*" +
                                " | /Customers/foo:Customer/descendant-or-self::*"},
                {"/Customers/foo:Customer/foo:Address/following::*",
                        "/Customers/foo:Customer/foo:Age | /Customers/foo:Customer/foo:ClubMember"},
                {"/Customers/Customer[@id = 'x1']/*[following::Street]", "/Customers/Customer[@id = 'x1']/*"},
                {"/Customers/foo:Customer/*[following::foo:Name]", "/None"},
                {"/Customers/foo:Customer/*[not(following::foo:Name)]", "/Customers/foo:Customer/*"},
                {"/Customers/following-sibling::*", "/None"},
                {"/Customers/Customer/following-sibling::Customer",
                        "/Customers/Customer[@id != 'x1']"},
                {"/Customers/Customer/following-sibling::foo:Customer",
                        "/Customers/foo:Customer"},
                {"/Customers/Customer[@id='x1']/Name/following-sibling::Address",
                        "/Customers/Customer[@id='x1']/Address"},
                {"/Customers/Customer/Name/following-sibling::Address",
                        "/Customers//Address"},
                {"(/Customers/Customer/Address/Street/following-sibling::State)[3]",
                        "/Customers/Customer[@id='x3']/Address/State"},
                {"/Customers/Customer[@id='x1']/Address/Street/following-sibling::*[2]",
                        "/Customers/Customer[@id='x3']/Address/State"},
                {"/Customers/Customer[@id='x1']/following-sibling::*",
                        "/Customers/Customer[@id != 'x1'] | /Customers/foo:Customer"},
                {"/Customers/foo:Customer/foo:Address/following-sibling::*",
                        "/Customers/foo:Customer/foo:Age | /Customers/foo:Customer/foo:ClubMember"},
                {"/Customers/Customer[@id = 'x1']/*[following-sibling::Street]", "/None"},
                {"/Customers/foo:Customer/*[following-sibling::foo:Address]", "/Customers/foo:Customer/foo:Name |" +
                        "/Customers/foo:Customer/foo:Phone | /Customers/foo:Customer/foo:Email"},
                {"/Customers/foo:Customer/*[not(following-sibling::foo:Address)]", "/Customers/foo:Customer/foo:Age | " +
                        "/Customers/foo:Customer/foo:ClubMember | /Customers/foo:Customer/foo:Address"}
        };
    }

    /*
     * DataProvider: provides XPath following expressions and expected number of following nodes returned
     */
    @DataProvider(name = "followingXpathNodeCount")
    public Object[][] getFollowingXpathExpressionNodeCount() {
        return new Object[][] {
                {"/Customers/following::*", 0},
                {"/Customers/Customer/following::*", 30},
                {"/Customers/Customer/following::Customer", 2},
                {"/Customers/Customer/following::foo:Customer", 1},
                {"/Customers/Customer[@id='x1']/Name/following::*", 38},
                {"/Customers/Customer/Address/following::*", 32},
                {"/Customers/foo:Customer/foo:Address/following::*", 2},
                {"/Customers/foo:Customer/foo:Name/following::*", 8},
                {"/Customers/foo:Customer/*[following::foo:Name]", 0},
                {"/Customers/foo:Customer/*[not(following::foo:Name)]", 6},
                {"/Customers/following-sibling::*", 0},
                {"/Customers/Customer/following-sibling::*", 3},
                {"/Customers/Customer/following-sibling::Customer", 2},
                {"/Customers/Customer/following-sibling::foo:Customer", 1},
                {"/Customers/Customer[@id='x1']/Name/following-sibling::*", 5},
                {"/Customers/Customer/Address/following-sibling::*", 6},
                {"/Customers/Customer[@id='x1']/Address/following-sibling::*", 2},
                {"/Customers/foo:Customer/foo:Address/following-sibling::*", 2},
                {"/Customers/Customer[@id = 'x1']/*[following-sibling::Street]", 0},
                {"/Customers/foo:Customer/*[following-sibling::foo:Address]", 3},
                {"/Customers/foo:Customer/*[not(following-sibling::foo:Address)]", 3}
        };
    }

    /*
     * DataProvider: provides XPath following expressions which should not return any node.
     */
    @DataProvider(name = "followingXpathEmpty")
    public Object[][] getFollowingXpathExpressionEmpty() {
        return new Object[][] {
                {"/Customers/following::*"},
                {"/Customers/foo:Customer/following::*"},
                {"/Customers/Customer[@id = 'x3' ]/following::Customer"},
                {"/Customers/following::id"},
                {"/Customers/Customer[@id = 'x3' ]/following-sibling::Customer"},
                {"/Customers/foo:Customer/following-sibling::*"},
                {"/Customers/Customer/following-sibling::foo:Name"},
                {"/Customers/following-sibling::id"}
        };
    }

    /**
     * Verifies Axis following xpath expression returns same nodes as returns when used normal xpath expression
     * @param  descexp  Axis following XPath expression.
     * @param  expath   normal xPath expression
     * @throws XPathExpressionException
     */
    @Test(dataProvider = "followingXpath")
    public void followingExpTests(String descexp, String expath) throws XPathExpressionException {
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
     * Verifies following xpath expression return following nodes list with correct number of nodes.
     * @param  exp       XPath expression.
     * @param  nodeCount number of following nodes in nodelist.
     * @throws XPathExpressionException
     */
    @Test(dataProvider = "followingXpathNodeCount")
    public void followingNodesCountTests(String exp, int nodeCount) throws XPathExpressionException {
        Document doc = documentOf(DECLARATION + RAW_XML);
        XPath xPath = XPathFactory.newInstance().newXPath();
        NodeList nodeList = (NodeList) xPath.evaluate(exp, doc, XPathConstants.NODESET);
        Assert.assertEquals(nodeList.getLength(), nodeCount);
    }

    /**
     * Verifies following xpath expression return no nodes if following expression context nodes don't have matching following elements.
     * @param  exp     XPath expression.
     * @throws XPathExpressionException
     */
    @Test(dataProvider = "followingXpathEmpty")
    public void FollowingScopeTests(String exp) throws XPathExpressionException {
        Document doc = documentOf(DECLARATION + RAW_XML);
        XPath xPath = XPathFactory.newInstance().newXPath();
        Node node = xPath.evaluateExpression(exp, doc, Node.class);
        Assert.assertNull(node);
    }
}
