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
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

/*
 * @test
 * @bug 8289508
 * @run testng/othervm xpath.XPathExpChildTest
 * @summary Tests for XPath ancestor, ancestor-or-self, preceding, and preceding-sibling axis specifiers.
 */
public class XPathAncestorsPrecedingTest {

    private static final String XML = """
            <store>
               <book id="1" lang="en">
                  <title/>
                  <author id="1"/>
                  <isbn>1234</isbn>
               </book>
               <book id="2" lang="en">
                  <title/>
                  <author id="2"/>
                  <isbn>5678</isbn>
               </book>
            </store>
            """;
    private static final Document doc;

    static {
        try {
            var builder =
                    DocumentBuilderFactory.newInstance().newDocumentBuilder();
            InputStream s = new ByteArrayInputStream(XML.getBytes());
            doc = builder.parse(s);
        } catch (Exception e) {
            System.out.println("Exception while initializing XML document");
            throw new RuntimeException(e.getMessage());
        }
    }

    /*
     * DataProvider: provides XPath expression that returns a node
     */
    @DataProvider(name = "ancestors_preceding")
    public Object[][] getXPathAncestorsPreceding() {
        return new Object[][]{
                //test ancestor
                {"//author/ancestor::book/ancestor::store", "/store"},
                {"//isbn/ancestor::store", "/store"},
                {"//ancestor::book", "//book"},
                {"//book/ancestor::*", "/store"},
                {"//author/ancestor::*[ancestor::store]/ancestor::store", "/store"},
                {"//author/ancestor::node()[ancestor::store]", "//book"},
                {"//author/ancestor::book/..", "/store"},
                {"//author/ancestor::*[ancestor::store]/..", "/store"},
                {"//ancestor::book/..", "/store"},
                {"//author/ancestor::*[@id]/parent::*", "/store"},
                {"//author/parent::*[@id]/ancestor::*", "/store"},
                {"//author[@id='1']/ancestor::book", "//book[1]"},
                {"//author[@*]/ancestor::book", "//book"},

                //test ancestor-or-self
                {"/store/ancestor-or-self::*", "/store"},
                {"//book[*]/ancestor-or-self::book", "//book"},
                {"/store/book[@*]/ancestor-or-self::book", "//book"},
                {"//book[@id='1']/ancestor-or-self::book", "//book[@id=1]"},
                {"//author[@id='2']/ancestor-or-self::book"},
                {"//book[1]/ancestor-or-self::store", "/store"},

                //test preceding
                {"/store/book[1]/author/preceding::*", "//book[1]/title"},
                {"//author/preceding::title", "//title"},
                {"//isbn/preceding::book", "//book[1]"},
                {"//book[2]/preceding::book", "//book[1]"},
                {"/store/book[preceding::book]", "//book[2]"},
                {"/store/book[preceding::book]/preceding::book", "//book[1]"},
                {"//author[@id='2']/../preceding::book", "//book[1]"},
                {"//author[@id='2']/preceding::node()/preceding::book", "//book[1]"},
                {"//author[@id='1']/preceding::title", "//book[1]/title"},
                
                //test preceding-sibling
                {"/store/book[1]/author/preceding-sibling::*", "//book[1]/title"},
                {"/store/book[2]/preceding-sibling::*", "//book[1]"},
                {"/store/book[preceding::book]/preceding-sibling::book", "//book[1]"},
                {"/store/book[1]/isbn[preceding-sibling::author[@id='1']]"},
                {"//author/preceding-sibling::*", "//title"},
                
        };
    }

    /*
     * DataProvider: provides XPath expressions that return null
     */
    @DataProvider(name = "noResults")
    public Object[][] getNoResults() {
        return new Object[][]{
                //test ancestor
                {"/store/ancestor::*"},
                {"/store/book[3]/ancestor::*"},
                {"/store/book[@id='3']/ancestor::*"},
                {"//book[*]/../ancestor::*"},
                {"/store[@*]/ancestor::*"},
                {"/book[@*]/ancestor::*/ancestor::*"},
                {"/store/book/ancestor::book"},
                {"//author/ancestor::store[2]"},
                {"//author[3]/ancestor::store"},
                {"//book/ssn/ancestor::*"},
                {"//book[@category]/ancestor::*"},
                {"//author/ancestor::*[ancestor::isbn]"},
                {"/store/../ancestor::*"},
                {"//ancestor::author"},

                //test ancestor-or-self
                {"/store/../ancestor-or-self::*"},
                {"//book[3]/ancestor-or-self::*"},
                {"//author/ancestor-or-self::title"},
                {"//author[@id='2']/ancestor-or-self::*[@id='1']"},

                //test preceding
                {"/store/preceding::book"},
                {"/store/book[1]/preceding::*"},
                {"/store/book[1]/title/preceding::*"},
                {"/store/book[1]/author/preceding::author"},
                {"/store/book[@id='1']/preceding::*"},

                //test preceding-sibling
                {"/store/book[1]/preceding-sibling::*"},
                {"/store/book[1]/author/preceding-sibling::isbn"},
                {"/store/book[2]/title/preceding-sibling::*"},
                {"//author[@id='2']/preceding-sibling::book"},
                {"//author[@id='2']/preceding-sibling::node()/preceding-sibling::author"},
        };
    }

    /**
     * Verifies XPath ancestor and ancestor-or-self axis specifiers.
     * @param exp      XPath expression
     * @param expected expected result
     * @throws Exception if test failed
     */
    @Test(dataProvider = "ancestors_preceding")
    void testXPathAncestors(String exp, String expected) throws Exception {
        XPath xPath = XPathFactory.newInstance().newXPath();
        NodeList nl = (NodeList) xPath.evaluate(exp, doc, XPathConstants.NODESET);
        Node node = xPath.evaluateExpression(exp, doc, Node.class);
        Assert.assertEquals(nl.item(0).getNodeName(), node.getNodeName());
        Assert.assertEquals(nl.item(0).getNodeValue(), node.getNodeValue());
        Assert.assertEquals(nl.item(0).getAttributes(), node.getAttributes());

        Assert.assertEquals(node.getNodeName() + "_" +
                        node.getAttributes().item(0).getNodeValue(),
                expected);
    }

    /**
     * Verifies no nodes returned from the XPath expression.
     *
     * @param exp XPath expression
     * @throws Exception
     */
    @Test(dataProvider = "noResults")
    void testNoResults(String exp) throws Exception {
        XPath xPath = XPathFactory.newInstance().newXPath();
        Node node = xPath.evaluateExpression(exp, doc, Node.class);
        Assert.assertNull(node);
    }
}
