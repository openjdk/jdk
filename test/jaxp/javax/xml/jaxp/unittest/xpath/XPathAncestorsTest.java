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
 * @run testng/othervm xpath.XPathAncestorsTest
 * @summary Tests for XPath ancestor and ancestor-or-self axis specifiers.
 */
public class XPathAncestorsTest {

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
     * DataProvider:provides XPath expression using ancestor/ancestor-or-self
     * and the expected node(s) from the expression
     */
    @DataProvider(name = "ancestors_axes")
    public Object[][] getXPathAncestors() {
        return new Object[][]{
                //test ancestor

                // abbreviated text
                {"//author/ancestor::book/ancestor::store", "/store"},
                {"//isbn/ancestor::store", "/store"},
                {"//ancestor::book[1]", "//book[1]"},

                // any node
                {"//book/ancestor::*", "/store"},
                {"//author/ancestor::*[ancestor::store]/ancestor::store", "/store"},
                {"//author/ancestor::node()[ancestor::store]", "//book"},

                // dot reference
                {"//author/ancestor::book/..", "/store"},
                {"//author/ancestor::*[ancestor::store]/..", "/store"},
                {"//ancestor::book/..", "/store"},

                // attributes
                {"//author/ancestor::*[@id]/parent::*", "/store"},
                {"//author/parent::*[@id]/ancestor::*", "/store"},
                {"//author[@id='1']/ancestor::book[1]", "//book[1]"},
                {"//author[@*]/ancestor::book[1]", "//book[1]"},

                //test ancestor-or-self

                // any node, indexing, id
                {"/store/ancestor-or-self::*", "/store"},
                {"//book[*]/ancestor-or-self::book[1]", "//book[1]"},
                {"/store/book[@*]/ancestor-or-self::book[1]", "//book[1]"},
                {"//book[@id='1']/ancestor-or-self::book[1]", "//book[1]"},
                {"//author[@id='2']/ancestor-or-self::book", "//book[2]"},
                {"//book[1]/ancestor-or-self::store", "/store"},

        };
    }

    /*
     * DataProvider: provides XPath expressions that return empty NodeSet
     */
    @DataProvider(name = "emptyNodeSet")
    public Object[][] getEmptyNodeSet() {
        return new Object[][]{
                // test ancestor

                // abbreviated text
                {"/store/book/ancestor::book"},
                {"//author/ancestor::store[2]"},
                {"//author[3]/ancestor::store"},

                // any nodes
                {"/store/ancestor::*"},
                {"/store/book[3]/ancestor::*"},
                {"//book[*]/../ancestor::*"},
                {"/store/book[@id='3']/ancestor::*"},
                {"//book/ssn/ancestor::*"},
                {"//author/ancestor::*[ancestor::isbn]"},
                {"/store/../ancestor::*"},
                {"//ancestor::author"},

                // id
                {"/store/book[@id='3']/ancestor::*"},
                {"/store[@*]/ancestor::*"},
                {"/book[@*]/ancestor::*/ancestor::*"},
                {"//book[@category]/ancestor::*"},

                //test ancestor-or-self

                // any nodes, id
                {"/store/../ancestor-or-self::*"},
                {"//book[3]/ancestor-or-self::*"},
                {"//author/ancestor-or-self::title"},
                {"//author[@id='2']/ancestor-or-self::*[@id='1']"},
        };
    }

    /**
     * Verifies XPath ancestor and ancestor-or-self axis specifiers
     * by comparing expression and expected result.
     * @param exp      XPath expression
     * @param expected expected result
     * @throws Exception if test failed
     */
    @Test(dataProvider = "ancestors_axes")
    void testXPathAncestors(String exp, String parent) throws Exception {
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
