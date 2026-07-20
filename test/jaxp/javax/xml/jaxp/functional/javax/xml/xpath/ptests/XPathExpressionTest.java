/*
 * Copyright (c) 2003, 2026, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.xpath.ptests;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static javax.xml.xpath.XPathConstants.BOOLEAN;
import static javax.xml.xpath.XPathConstants.NODE;
import static javax.xml.xpath.XPathConstants.NODESET;
import static javax.xml.xpath.XPathConstants.NUMBER;
import static javax.xml.xpath.XPathConstants.STRING;
import static javax.xml.xpath.ptests.XPathTestConst.XML_DIR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Class containing the test cases for XPathExpression API.
 */
/*
 * @test
 * @library /javax/xml/jaxp/libs
 * @run junit/othervm javax.xml.xpath.ptests.XPathExpressionTest
 */
public class XPathExpressionTest {
    /**
     * Document object for testing XML file.
     */
    private Document document;

    /**
     * A XPath for evaluation environment and expressions.
     */
    private XPath xpath;

    /**
     * A QName using default name space.
     */
    private static final QName TEST_QNAME = new QName(XMLConstants.XML_NS_URI, "");

    /**
     * XML File Path.
     */
    private static final Path XML_PATH = XML_DIR.resolve("widgets.xml");

    /**
     * An expression name which locate at "/widgets/widget[@name='a']/@quantity"
     */
    private static final String EXPRESSION_NAME_A = "/widgets/widget[@name='a']/@quantity";

    /**
     * An expression name which locate at "/widgets/widget[@name='b']/@quantity"
     */
    private static final String EXPRESSION_NAME_B = "/widgets/widget[@name='b']/@quantity";

    /**
     * Create Document object and XPath object for every time
     * @throws Exception If any errors occur.
     */
    @BeforeEach
    public void setup() throws Exception {
        document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(XML_PATH.toFile());
        xpath = XPathFactory.newInstance().newXPath();
    }

    /**
     * Test for evaluate(java.lang.Object item,QName returnType)throws
     * XPathExpressionException.
     */
    @Test
    public void testCheckXPathExpression01() throws XPathExpressionException {
        assertEquals("6", xpath.compile(EXPRESSION_NAME_A).
                evaluate(document, STRING));
    }

    /**
     * evaluate(java.lang.Object item,QName returnType) throws NPE if input
     * source is null.
     */
    @Test
    public void testCheckXPathExpression02() throws XPathExpressionException {
        XPathExpression expr = xpath.compile(EXPRESSION_NAME_A);
        assertThrows(NullPointerException.class, () -> expr.evaluate(null, STRING));
    }

    /**
     * evaluate(java.lang.Object item,QName returnType) throws NPE if returnType
     * is null.
     */
    @Test
    public void testCheckXPathExpression03() throws XPathExpressionException {
        XPathExpression expr = xpath.compile(EXPRESSION_NAME_A);
        assertThrows(NullPointerException.class, () -> expr.evaluate(document, null));
    }

    /**
     * Test for method evaluate(java.lang.Object item,QName returnType).If a
     * request is made to evaluate the expression in the absence of a context
     * item, simple expressions, such as "1+1", can be evaluated.
     */
    @Test
    public void testCheckXPathExpression04() throws XPathExpressionException {
        assertEquals("2", xpath.compile("1+1").evaluate(document, STRING));
    }

    /**
     * evaluate(java.lang.Object item,QName returnType) throws IAE If returnType
     * is not one of the types defined in XPathConstants.
     */
    @Test
    public void testCheckXPathExpression05() throws XPathExpressionException {
        XPathExpression expr = xpath.compile(EXPRESSION_NAME_A);
        assertThrows(IllegalArgumentException.class, () -> expr.evaluate(document, TEST_QNAME));
    }

    /**
     * evaluate(java.lang.Object item,QName returnType) return correct boolean
     * value if returnType is Boolean.
     */
    @Test
    public void testCheckXPathExpression06() throws XPathExpressionException {
        assertEquals(true, xpath.compile(EXPRESSION_NAME_A).
                evaluate(document, BOOLEAN));
    }

    /**
     * evaluate(java.lang.Object item,QName returnType) return correct boolean
     * value if returnType is Boolean.
     */
    @Test
    public void testCheckXPathExpression07() throws XPathExpressionException {
        assertEquals(false, xpath.compile(EXPRESSION_NAME_B).
                evaluate(document, BOOLEAN));
    }

    /**
     * evaluate(java.lang.Object item,QName returnType) return correct number
     * value when return type is Double.
     */
    @Test
    public void testCheckXPathExpression08() throws XPathExpressionException {
        assertEquals(6d, xpath.compile(EXPRESSION_NAME_A).
                evaluate(document, NUMBER));
    }

    /**
     * evaluate(java.lang.Object item,QName returnType) evaluate an attribute
     * value which returnType is Node.
     */
    @Test
    public void testCheckXPathExpression09() throws XPathExpressionException {
        Attr attr = (Attr) xpath.compile(EXPRESSION_NAME_A).
                evaluate(document, NODE);
        assertEquals("6", attr.getValue());
    }

    /**
     * evaluate(java.lang.Object item,QName returnType) evaluate an attribute
     * value which returnType is NodeList.
     */
    @Test
    public void testCheckXPathExpression10() throws XPathExpressionException {
        NodeList nodeList = (NodeList) xpath.compile(EXPRESSION_NAME_A).
                evaluate(document, NODESET);
        Attr attr = (Attr) nodeList.item(0);
        assertEquals("6", attr.getValue());
    }

    /**
     * Test for evaluate(java.lang.Object item) when returnType is left off of
     * the XPath.evaluate method, all expressions are evaluated to a String
     * value.
     */
    @Test
    public void testCheckXPathExpression11() throws XPathExpressionException {
        assertEquals("6", xpath.compile(EXPRESSION_NAME_A).evaluate(document));
    }

    /**
     * evaluate(java.lang.Object item) when a request is made to evaluate the
     * expression in the absence of a context item, simple expressions, such as
     * "1+1", can be evaluated.
     */
    @Test
    public void testCheckXPathExpression13() throws XPathExpressionException {
        assertEquals("2", xpath.compile("1+1").evaluate(document));
    }

    /**
     * evaluate(java.lang.Object item) throws NPE if document is null.
     */
    @Test
    public void testCheckXPathExpression14() throws XPathExpressionException {
        XPathExpression expr = xpath.compile(EXPRESSION_NAME_A);
        assertThrows(NullPointerException.class, () -> expr.evaluate(null));
    }

    /**
     * valuate(InputSource source) return a string value if return type is
     * String.
     */
    @Test
    public void testCheckXPathExpression15() throws Exception {
        try (InputStream is = Files.newInputStream(XML_PATH)) {
            assertEquals("6", xpath.compile(EXPRESSION_NAME_A).
                    evaluate(new InputSource(is)));
        }
    }

    /**
     * evaluate(InputSource source) throws NPE if input source is null.
     */
    @Test
    public void testCheckXPathExpression16() throws XPathExpressionException {
        XPathExpression expr = xpath.compile(EXPRESSION_NAME_A);
        assertThrows(NullPointerException.class, () -> expr.evaluate(null));
    }

    /**
     * Test for evaluate(InputSource source,QName returnType) returns a string
     * value if returnType is String.
     */
    @Test
    public void testCheckXPathExpression20() throws Exception {
        try (InputStream is = Files.newInputStream(XML_PATH)) {
            assertEquals("6", xpath.compile(EXPRESSION_NAME_A).
                    evaluate(new InputSource(is), STRING));
        }
    }

    /**
     * evaluate(InputSource source,QName returnType) throws NPE if source is
     * null.
     */
    @Test
    public void testCheckXPathExpression21() throws XPathExpressionException {
        XPathExpression expr = xpath.compile(EXPRESSION_NAME_A);
        assertThrows(NullPointerException.class, () -> expr.evaluate(null, STRING));
    }

    /**
     * evaluate(InputSource source,QName returnType) throws NPE if returnType is
     * null.
     */
    @Test
    public void testCheckXPathExpression23() throws Exception {
        try (InputStream is = Files.newInputStream(XML_PATH)) {
            XPathExpression expr = xpath.compile(EXPRESSION_NAME_A);
            InputSource source = new InputSource(is);
            assertThrows(
                    NullPointerException.class,
                    () -> expr.evaluate(source, null));
        }
    }

    /**
     * evaluate(InputSource source,QName returnType) throws
     * IllegalArgumentException if returnType is not one of the types defined
     * in XPathConstants.
     */
    @Test
    public void testCheckXPathExpression26() throws Exception {
        try (InputStream is = Files.newInputStream(XML_PATH)) {
            XPathExpression expr = xpath.compile(EXPRESSION_NAME_A);
            InputSource source = new InputSource(is);
            assertThrows(IllegalArgumentException.class, () -> expr.evaluate(source, TEST_QNAME));
        }
    }

    /**
     * evaluate(InputSource source,QName returnType) return a correct boolean
     * value if returnType is Boolean.
     */
    @Test
    public void testCheckXPathExpression27() throws Exception {
        try (InputStream is = Files.newInputStream(XML_PATH)) {
            assertEquals(true, xpath.compile(EXPRESSION_NAME_A).
                    evaluate(new InputSource(is), BOOLEAN));
        }
    }

    /**
     * evaluate(InputSource source,QName returnType) return a correct boolean
     * value if returnType is Boolean.
     */
    @Test
    public void testCheckXPathExpression28() throws Exception {
        try (InputStream is = Files.newInputStream(XML_PATH)) {
            assertEquals(false, xpath.compile(EXPRESSION_NAME_B).
                    evaluate(new InputSource(is), BOOLEAN));
        }
    }

    /**
     * evaluate(InputSource source,QName returnType) return a correct number
     * value if returnType is Number.
     */
    @Test
    public void testCheckXPathExpression29() throws Exception {
        try (InputStream is = Files.newInputStream(XML_PATH)) {
            assertEquals(6d, xpath.compile(EXPRESSION_NAME_A).
                    evaluate(new InputSource(is), NUMBER));
        }
    }

    /**
     * Test for evaluate(InputSource source,QName returnType) returns a node if
     * returnType is Node.
     */
    @Test
    public void testCheckXPathExpression30() throws Exception {
        try (InputStream is = Files.newInputStream(XML_PATH)) {
            Attr attr = (Attr) xpath.compile(EXPRESSION_NAME_A).
                    evaluate(new InputSource(is), NODE);
            assertEquals("6", attr.getValue());
        }
    }

    /**
     * Test for evaluate(InputSource source,QName returnType) return a node list
     * if returnType is NodeList.
     */
    @Test
    public void testCheckXPathExpression31() throws Exception {
        try (InputStream is = Files.newInputStream(XML_PATH)) {
            NodeList nodeList = (NodeList) xpath.compile(EXPRESSION_NAME_A).
                    evaluate(new InputSource(is), NODESET);
            assertEquals("6", ((Attr) nodeList.item(0)).getValue());
        }
    }
}
