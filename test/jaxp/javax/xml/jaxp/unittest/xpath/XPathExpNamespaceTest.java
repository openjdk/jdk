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
 * @bug 8289510
 * @library /javax/xml/jaxp/unittest
 * @run testng/othervm xpath.XPathExpNamespaceTest
 * @summary Tests for XPath namespace axis specifier.
 */
public class XPathExpNamespaceTest extends XPathTestBase {
    static final String RAW_XML
            = "<Customers xmlns:foo=\"www.foo.com\">"
            + "    <Customer id=\"x1\">"
            + "        <Name>name1</Name>"
            + "        <Phone>1111111111</Phone>"
            + "        <Email id=\"x\">123@xyz.com</Email>"
            + "        <Address>"
            + "            <Street xmlns:street=\"www.street.com\" xmlns:bstreet=\"www.bstreet.com\">1111 111st ave</Street>"
            + "            <City>The City</City>"
            + "            <State>The State</State>"
            + "        </Address>"
            + "    </Customer>"
            + "    <Customer id=\"x2\" xmlns:dog=\"www.pets.com\">"
            + "        <Name>name2</Name>"
            + "        <Phone>2222222222</Phone>"
            + "        <Email id=\"y\">123@xyz.com</Email>"
            + "        <dog:Address>"
            + "            <Street>2222 222nd ave</Street>"
            + "            <City>The City</City>"
            + "            <State>The State</State>"
            + "        </dog:Address>"
            + "    </Customer>"
            + "    <Customer id=\"x3\">"
            + "        <Name>name3</Name>"
            + "        <Phone>3333333333</Phone>"
            + "        <Email id=\"z\">123@xyz.com</Email>"
            + "        <Address>"
            + "            <Street>3333 333rd ave</Street>"
            + "            <City>The City</City>"
            + "            <State>The State</State>"
            + "        </Address>"
            + "    </Customer>"
            + "    <foo:Customer foo:id=\"x1\">"
            + "        <foo:Name>name1</foo:Name>"
            + "        <foo:Phone>1111111111</foo:Phone>"
            + "        <foo:Email foo:id=\"x\">123@xyz.com</foo:Email>"
            + "        <foo:Address>"
            + "            <foo:Street>1111 111st ave</foo:Street>"
            + "            <foo:City>The City</foo:City>"
            + "            <foo:State>The State</foo:State>"
            + "        </foo:Address>"
            + "    </foo:Customer>"
            + "    <VendCustomer id=\"vx1\">"
            + "        <Name>name3</Name>"
            + "        <Phone>3333333333</Phone>"
            + "        <Email id=\"z\">123@xyz.com</Email>"
            + "        <Address xmlns=\"default-namespace-uri\">"
            + "            <Street>3333 333rd ave</Street>"
            + "            <City xmlns=\"redeclared-namespace-uri\">The City</City>"
            + "            <State>The State</State>"
            + "        </Address>"
            + "    </VendCustomer>"
            + "</Customers>";

    /*
     * DataProvider: provides XPath namespace expressions and expected local name, expected namespace prefix, expected
     * namespace node name, and expected namespace Uri.
     */
    @DataProvider(name = "namespaceXpath")
    public Object[][] getNamespaceXpathExpression() {
        return new Object[][] {
                {"/Customers/namespace::foo", "foo", "xmlns", "xmlns:foo","www.foo.com"},
                {"/Customers/namespace::xml", "xml", "xml", "xmlns:xml", "http://www.w3.org/XML/1998/namespace"},
                {"//Customer/Name/namespace::foo", "foo", "xmlns", "xmlns:foo","www.foo.com"},
                {"/Customers/Customer/Name/namespace::foo", "foo", "xmlns", "xmlns:foo","www.foo.com"},
                {"//Customer/Name/namespace::xml", "xml", "xml", "xmlns:xml","http://www.w3.org/XML/1998/namespace"},
                {"/Customers/Customer/Name/namespace::xml", "xml", "xml", "xmlns:xml","http://www.w3.org/XML/1998/namespace"},
                {"//Customer/Name/namespace::dog", "dog", "xmlns", "xmlns:dog","www.pets.com"},
                {"/Customers/Customer/Name/namespace::dog", "dog", "xmlns", "xmlns:dog","www.pets.com"},
                {"//www.foo.com:Customer/namespace::foo", "foo", "xmlns", "xmlns:foo", "www.foo.com"},
                {"/Customers/*[name() = 'foo:Customer']/namespace::foo", "foo", "xmlns", "xmlns:foo", "www.foo.com"},
                {"/Customers/*[namespace-uri() = 'www.foo.com']/namespace::foo", "foo", "xmlns", "xmlns:foo", "www.foo.com"},
                {"/Customers/*[contains(name(.), 'foo:')]/namespace::foo", "foo", "xmlns", "xmlns:foo", "www.foo.com"},
                {"/Customers/*[starts-with(name(.), 'foo:')]/namespace::foo", "foo", "xmlns", "xmlns:foo", "www.foo.com"},
                {"//*[local-name()='Customer' and namespace-uri() = 'www.foo.com']/namespace::foo", "foo", "xmlns", "xmlns:foo", "www.foo.com"},
                {"/Customers/VendCustomer/default-namespace-uri:Address/namespace::*[name()='']", "xmlns", null, "xmlns","default-namespace-uri"},
                {"/Customers/VendCustomer/default-namespace-uri:Address/redeclared-namespace-uri:City/namespace::*[name()='']", "xmlns", null, "xmlns","redeclared-namespace-uri"}
        };
    }

    /*
     * DataProvider: provides XPath namespace expressions which should return no namespace nodes.
     */
    @DataProvider(name = "namespaceXpathEmpty")
    public Object[][] getNamespaceXpathExpressionEmpty() {
        return new Object[][] {
                {"//VendCustomer/Name/namespace::dog"},
                {"/Customers/VendCustomer/Name/namespace::dog"},
                {"//Customer/Name/namespace::cat"},
                {"/Customers/Customer/Name/namespace::cat"},
                {"//Customer/Address/namespace::street"},
                {"/Customers/Customer/Address/namespace::street"},
                {"//VendCustomer/Address/namespace::street"},
                {"/Customers/VendCustomer/Address/namespace::street"},
                {"//@id/namespace::*"},
                {"//@*[name() = 'foo:id']/namespace::*"},
                {"//@*[local-name() = 'id' and namespace-uri() = 'www.foo.com']/namespace::*"}
        };

    }

    /*
     * DataProvider: provides XPath namespace expressions and expected total number of namespace nodes count.
     */
    @DataProvider(name = "namespaceXpathNodeCount")
    public Object[][] getNamespaceXpathExpressionNodeCount() {
        return new Object[][] {
                {"/Customers/namespace::*", 2},
                {"/Customers/Customer/namespace::*", 3},
                {"/Customers/Customer/Address/namespace::*", 2},
                {"/Customers/www.foo.com:Customer/www.foo.com:Address/namespace::*", 2},
                {"/Customers/*[name()='foo:Customer']/*[name() = 'foo:Address']/namespace::*", 2},
                {"/Customers/Customer/Address/Street/namespace::*", 4},
                {"/Customers/Customer/Address/City/namespace::*", 2},
                {"/Customers/VendCustomer/namespace::*", 2},
                {"/Customers/VendCustomer/default-namespace-uri:Address/namespace::*", 3},
                {"/Customers/VendCustomer/default-namespace-uri:Address/redeclared-namespace-uri:City/namespace::*", 3},
                {"/Customers/Customer[@id='x1']/namespace::*", 2}
        };
    }

    /*
     * DataProvider: provides node functions and expected return of different node functions.
     */
    @DataProvider(name = "namespaceUsingNodeFunctions")
    public Object[][] getNamespaceNodeNameAndUri() {
        return new Object[][] {
                {"namespace-uri(//www.foo.com:Customer)","www.foo.com" },
                {"namespace-uri(//www.pets.com:Address)","www.pets.com" },
                {"namespace-uri(/Customers/www.foo.com:Customer)","www.foo.com" },
                {"namespace-uri(/Customers/Customer/www.pets.com:Address)","www.pets.com" },
                {"namespace-uri(/Customers/VendCustomer/Email)","" },
                {"namespace-uri(//@*[name() = 'id'])", ""},
                {"namespace-uri(//@*[name() = 'foo:id'])", "www.foo.com"}
        };
    }

    /**
     * Verifies namespace expression retrieves different namespace nodes.
     * @param  exp        XPath expression.
     * @param  localName  expected local name of namespace node.
     * @param  nsPrefix   expected namespace prefix of namespace node.
     * @param  nsNodeName expected name of namespace node.
     * @param  nsUri      expected namespace Uri.
     * @throws XPathExpressionException
     */
    @Test(dataProvider = "namespaceXpath")
    public void namespaceExpTests(String exp, String localName, String nsPrefix, String nsNodeName, String nsUri) throws XPathExpressionException {
        Document doc = documentOf(DECLARATION + RAW_XML);
        XPath xPath = XPathFactory.newInstance().newXPath();
        Node node = xPath.evaluateExpression(exp, doc, Node.class);
        Assert.assertEquals(node.getLocalName(), localName);
        Assert.assertEquals(node.getPrefix(), nsPrefix);
        Assert.assertEquals(node.getNodeName(), nsNodeName);
        Assert.assertEquals(node.getNodeValue(),nsUri);
    }

    /**
     * Verifies namespace path expression return no nodes if namespace expression context nodes don't have namespace
     * @param  exp     XPath expression.
     * @throws XPathExpressionException
     */
    @Test(dataProvider = "namespaceXpathEmpty")
    public void NamespaceScopeTests(String exp) throws XPathExpressionException {
        Document doc = documentOf(DECLARATION + RAW_XML);
        XPath xPath = XPathFactory.newInstance().newXPath();
        Node node = xPath.evaluateExpression(exp, doc, Node.class);
        Assert.assertNull(node);
    }

    /**
     * Verifies namespace path expression return namespace nodes list with correct number of nodes.
     * @param  exp       XPath expression.
     * @param  nodeCount number of namespace nodes in nodelist.
     * @throws XPathExpressionException
     */
    @Test(dataProvider = "namespaceXpathNodeCount")
    public void NamespaceNodesCountTests(String exp, int nodeCount) throws XPathExpressionException {
        Document doc = documentOf(DECLARATION + RAW_XML);
        XPath xPath = XPathFactory.newInstance().newXPath();
        NodeList nodeList = (NodeList) xPath.evaluate(exp, doc, XPathConstants.NODESET);
        Assert.assertEquals(nodeList.getLength(), nodeCount);
    }

    /**
     * Verifies namespace-uri functions returns the correct namespace uri.
     * @param  exp          XPath expression.
     * @param  expectedName number of namespace nodes in nodelist.
     * @throws XPathExpressionException
     */
    @Test(dataProvider = "namespaceUsingNodeFunctions")
    public void NamespaceNodeFunctionsTests(String exp, String expectedName) throws XPathExpressionException {
        Document doc = documentOf(DECLARATION + RAW_XML);
        XPath xPath = XPathFactory.newInstance().newXPath();
        String s = xPath.evaluateExpression(exp, doc, String.class);
        String s2 = (String) xPath.evaluate(exp, doc, XPathConstants.STRING);
        Assert.assertEquals(s, expectedName);
        Assert.assertEquals(s2, s);
    }

}
