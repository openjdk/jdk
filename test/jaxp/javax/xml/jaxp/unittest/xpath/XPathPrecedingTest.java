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
 * @library /javax/xml/jaxp/unittest
 * @run testng/othervm xpath.XPathPrecedingTest
 * @summary Tests for XPath preceding and preceding-sibling axis specifiers.
 */
public class XPathPrecedingTest {

    private static final String XML = """
            <store>
               <book id="1" lang="en">
                  <title>Book1</title>
                  <author id="1"/>
                  <isbn>1234</isbn>
               </book>
               <book id="2" lang="en" xmlns="www.foo.com">
                  <title>Book2</title>
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
     * DataProvider: provides XPath expression using preceding/preceding-sibling
     * and the expected node(s) from the expression
     */
    @DataProvider(name = "preceding_axes")
    public Object[][] getXPathPreceding() {
        return new Object[][]{
                // test preceding

                // any nodes
                {"/store/book[1]/author/preceding::*", "/store/book[1]/title"},

                // abbreviated text
                {"/store/book[1]/isbn/preceding::*[1]", "/store/book[1]/author"},
                {"(/store/book[1]/isbn/preceding::*)[1]", "/store/book[1]/title"},
                {"//isbn/preceding::book", "//book[1]"},
                {"//book[2]/preceding::book", "//book[1]"},
                {"/store/book[preceding::book]", "//book[2]"},
                {"/store/book[preceding::book]/preceding::book", "//book[1]"},

                // id
                {"//author[@id='2']/../preceding::book", "//book[1]"},
                {"//author[@id='2']/preceding::node()/preceding::book", "//book[1]"},
                {"//author[@id='1']/preceding::title", "//book[1]/title"},

                //test preceding-sibling

                // any node
                {"/store/book[1]/author/preceding-sibling::*", "/store/book[1]/title"},
                {"/store/book[2]/preceding-sibling::*", "//book[1]"},
                {"//author/preceding-sibling::*", "//title"},

                // abbreviated text
                {"/store/book[preceding::book]/preceding-sibling::book", "//book[1]"},

                // id
                {"/store/book[1]/isbn[preceding-sibling::author[@id='1']]", "/store/book[1]/isbn"},

        };
    }

    /*
     * DataProvider: provides XPath expressions that return empty NodeSet
     */
    @DataProvider(name = "emptyNodeSet")
    public Object[][] getEmptyNodeSet() {
        return new Object[][]{
                //test preceding

                // abbreviated text
                {"/store/preceding::book"},
                {"/store/book[1]/author/preceding::author"},

                // any nodes/id
                {"/store/book[1]/preceding::*"},
                {"/store/book[1]/title/preceding::*"},
                {"/store/book[@id='1']/preceding::*"},

                //test preceding-sibling

                // any nodes
                {"/store/book[1]/preceding-sibling::*"},
                {"/store/book[2]/title/preceding-sibling::*"},

                // abbreviated text / id
                {"/store/book[1]/author/preceding-sibling::isbn"},
                {"//author[@id='2']/preceding-sibling::book"},
                {"//author[@id='2']/preceding-sibling::node()/preceding-sibling::author"},

                // attribute / namespace
                {"/store/book[2]/@id/preceding-sibling::*"},
                {"/store/book/@lang/preceding-sibling::*"},
                {"/store/book[2]/namespace::*/preceding-sibling::*"},

                // text node
                {"/store/book[2]/isbn/text()/preceding-sibling::*"},

        };
    }

    /**
     * Verifies XPath preceding and preceding-sibling axis specifiers by
     * comparing expression and expected result.
     * @param exp      XPath expression
     * @param expected expected result
     * @throws Exception if test failed
     */
    @Test(dataProvider = "preceding_axes")
    void testXPathPreceding(String exp, String parent) throws Exception {
        XPath xPath = XPathFactory.newInstance().newXPath();
        Node result = xPath.evaluateExpression(exp, doc, Node.class);
        Node expected = xPath.evaluateExpression(parent, doc, Node.class);
        Assert.assertEquals(result, expected);
    }

    /**
     * Verifies no nodes returned from the XPath expression.
     *
     * @param exp XPath expression
     * @throws Exception
     */
    @Test(dataProvider = "emptyNodeSet")
    void testEmptyNodeSet(String exp) throws Exception {
        XPath xPath = XPathFactory.newInstance().newXPath();
        Node result = xPath.evaluateExpression(exp, doc, Node.class);
        Assert.assertEquals(result, null);
    }
}

