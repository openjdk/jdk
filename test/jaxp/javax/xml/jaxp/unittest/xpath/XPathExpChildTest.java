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
 * @bug 8289511
 * @run testng/othervm xpath.XPathExpChildTest
 * @summary Tests for XPath child axis specifier.
 */
public class XPathExpChildTest {

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
    private static final String AUTHOR_1 = "author_1";
    private static final String AUTHOR_2 = "author_2";
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
     * DataProvider: provides XPath expression and expected result
     */
    @DataProvider(name = "parameters")
    public Object[][] getXPathExpression() {
        return new Object[][]{
                // abbreviated text
                {"/store/book/author", AUTHOR_1},
                {"/child::store/child::book/child::author", AUTHOR_1},
                {"/store/child::book/author", AUTHOR_1},

                // any nodes
                {"/store/book/child::*[2]", AUTHOR_1},
                {"/store/child::*[child::author]/author", AUTHOR_1},
                {"/store/child::*[child::author][2]/author", AUTHOR_2},
                {"/store/child::node()/child::author", AUTHOR_1},
                {"/store/child::node()[child::author]/author", AUTHOR_1},
                {"/store/child::node()[child::author][2]/author", AUTHOR_2},

                // position
                {"/store/child::book[position()=1]/author", AUTHOR_1},
                {"/store/child::book[last()]/author", AUTHOR_2},

                // descendant
                {"//book/child::*[2]", AUTHOR_1},
                {"//child::*[child::author]/author", AUTHOR_1},
                {"//child::*[child::author][2]/author", AUTHOR_2},
                {"//child::node()/child::author", AUTHOR_1},
                {"//child::node()[child::author]/author", AUTHOR_1},
                {"//child::node()[child::author][2]/author", AUTHOR_2},

                // parent node
                {"//child::book/../child::book/child::author", AUTHOR_1},

                // dot reference
                {"//child::book/./child::author", AUTHOR_1},
                {"//child::node()/./child::author", AUTHOR_1},
                {"//././/./child::author", AUTHOR_1},

                // attributes
                {"/store/child::book[@id=1]/author", AUTHOR_1},
                {"/store/child::book[attribute::id=1]/author", AUTHOR_1},
                {"/store/child::book[@id]/author", AUTHOR_1},
                {"/store/child::book[@id=1][@lang='en']/author", AUTHOR_1},
                {"/store/child::book[@lang='en'][1]/author", AUTHOR_1},
                {"/store/child::book[child::isbn='1234']/author", AUTHOR_1},
                {"/store/child::book[@lang='en' and " +
                        "child::isbn='1234']/author", AUTHOR_1},
                {"/store/child::*[@lang='en'][2]/author", AUTHOR_2},
                {"/store/child::node()[@id='1']/author", AUTHOR_1},
                {"/store/child::node()[@lang='en'][2]/author", AUTHOR_2},
                {"/store/child::*[child::author][child::title][@id='2']/author",
                        AUTHOR_2},
                {"/store/child::*[child::author or child::ssn][@id='2']/author",
                        AUTHOR_2},
                {"/store/child::*[child::*]/author", AUTHOR_1},
                {"/store/child::*[attribute::*]/author", AUTHOR_1},
                {"/store/*[*][*][*][*][*][*][*][*]/author", AUTHOR_1},
                {"/store/*[@*][@*][@*][@*][@*][@*][@*][@*]/author", AUTHOR_1},
                {"//author[@*]", AUTHOR_1},

                // text node
                {"/store/book[1]/isbn/child::text()/../../author", AUTHOR_1},
                {"/store/book/isbn[child::text()='5678']/../author", AUTHOR_2},
                {"/store/book/isbn[.='5678']/../author", AUTHOR_2},

                // count child nodes
                {"/store/book[count(./child::author)]/author", AUTHOR_1},
                {"/store/book[count(child::author)]/author", AUTHOR_1},
                {"/store/book[count(../child::book)]/author", AUTHOR_2},
        };
    }

    /*
     * DataProvider: provides XPath expressions that return zero children
     */
    @DataProvider(name = "zeroChildrenExp")
    public Object[][] getZeroChildrenExp() {
        return new Object[][]{
                {"/store/book[3]/author"},
                {"/store/book/author/ssn"},
                {"/store/child[book]/author"},
                {"/store/child[@id='1']/book/author"},
                {"/store/child::*[@category]/author"},
                {"//author[*]/../author"},
                {"//title[@*]/../author"},
                {"/store/book[-1]/author"},
                {"/store/child:book/author"},
                {"//book[.='1']/author"},
        };
    }

    /*
     * DataProvider: provides invalid XPath expression and expected exception
     *  to be thrown
     */
    @DataProvider(name = "invalidExp")
    public Object[][] getInvalidExp() {
        return new Object[][]{
                // XPathExpressionException
                {"/store/*[child::author] and [child::title]/author",
                        XPathExpressionException.class},
                {"//book[@id='en'] and book[@lang='en']/author",
                        XPathExpressionException.class},
                {"/store/book[child::count()]/author",
                        XPathExpressionException.class},
                {"//book[child::position()=1]", XPathExpressionException.class},
        };
    }

    /**
     * Verifies XPath child axis specifier.
     *
     * @param exp      XPath expression
     * @param expected expected result
     * @throws Exception
     */
    @Test(dataProvider = "parameters")
    void testXPathEvaluate(String exp, String expected) throws Exception {
        XPath xPath = XPathFactory.newInstance().newXPath();
        NodeList nl = (NodeList) xPath.evaluate(exp, doc,
                XPathConstants.NODESET);
        Node node = xPath.evaluateExpression(exp, doc, Node.class);
        Assert.assertEquals(nl.item(0).getNodeName(), node.getNodeName());
        Assert.assertEquals(nl.item(0).getNodeValue(), node.getNodeValue());
        Assert.assertEquals(nl.item(0).getAttributes(), node.getAttributes());

        Assert.assertEquals(node.getNodeName() + "_" +
                        node.getAttributes().item(0).getNodeValue(),
                expected);
    }

    /**
     * Verifies no child nodes returned from the XPath expression.
     *
     * @param exp XPath expression
     * @throws Exception
     */
    @Test(dataProvider = "zeroChildrenExp")
    void testZeroChildrenExp(String exp) throws Exception {
        XPath xPath = XPathFactory.newInstance().newXPath();
        Node node = xPath.evaluateExpression(exp, doc, Node.class);
        Assert.assertNull(node);
    }

    /**
     * Verifies exception thrown for invalid expression.
     *
     * @param exp            XPath expression
     * @param throwableClass expected exception
     * @throws Exception
     */
    @Test(dataProvider = "invalidExp")
    void testInvalidExp(String exp, Class throwableClass) throws Exception {
        XPath xPath = XPathFactory.newInstance().newXPath();
        Assert.assertThrows(throwableClass,
                () -> ((NodeList) xPath.evaluate(exp, doc,
                        XPathConstants.NODESET)).item(0).getNodeName());
    }
}
