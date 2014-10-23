/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import static javax.xml.xpath.XPathConstants.BOOLEAN;
import static javax.xml.xpath.XPathConstants.NODE;
import static javax.xml.xpath.XPathConstants.NODESET;
import static javax.xml.xpath.XPathConstants.NUMBER;
import static javax.xml.xpath.XPathConstants.STRING;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import static javax.xml.xpath.ptests.XPathTestConst.XML_DIR;
import static jaxp.library.JAXPTestUtilities.failUnexpected;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Class containing the test cases for XPathExpression API.
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
    private static final Path XML_PATH = Paths.get(XML_DIR + "widgets.xml");

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
     * @throws ParserConfigurationException If the factory class cannot be
     *                                      loaded, instantiated
     * @throws SAXException If any parse errors occur.
     * @throws IOException If operation on xml file failed.
     */
    @BeforeTest
    public void setup() throws ParserConfigurationException, SAXException, IOException {
        document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(XML_PATH.toFile());
        xpath = XPathFactory.newInstance().newXPath();
    }

    /**
     * Test for evaluate(java.lang.Object item,QName returnType)throws
     * XPathExpressionException.
     */
    @Test
    public void testCheckXPathExpression01() {
        try {
            assertEquals(xpath.compile(EXPRESSION_NAME_A).
                    evaluate(document, STRING), "6");
        } catch (XPathExpressionException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * evaluate(java.lang.Object item,QName returnType) throws NPE if input
     * source is null.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testCheckXPathExpression02() {
        try {
            xpath.compile(EXPRESSION_NAME_A).evaluate(null, STRING);
        } catch (XPathExpressionException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * evaluate(java.lang.Object item,QName returnType) throws NPE if returnType
     * is null.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testCheckXPathExpression03() {
        try {
            xpath.compile(EXPRESSION_NAME_A).evaluate(document, null);
        } catch (XPathExpressionException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * Test for method evaluate(java.lang.Object item,QName returnType).If a
     * request is made to evaluate the expression in the absence of a context
     * item, simple expressions, such as "1+1", can be evaluated.
     */
    @Test
    public void testCheckXPathExpression04() {
        try {
            assertEquals(xpath.compile("1+1").evaluate(document, STRING), "2");
        } catch (XPathExpressionException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * evaluate(java.lang.Object item,QName returnType) throws IAE If returnType
     * is not one of the types defined in XPathConstants.
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testCheckXPathExpression05() {
        try {
            xpath.compile(EXPRESSION_NAME_A).evaluate(document, TEST_QNAME);
        } catch (XPathExpressionException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * evaluate(java.lang.Object item,QName returnType) return correct boolean
     * value if returnType is Boolean.
     */
    @Test
    public void testCheckXPathExpression06() {
        try {
            assertEquals(xpath.compile(EXPRESSION_NAME_A).
                evaluate(document, BOOLEAN), true);
        } catch (XPathExpressionException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * evaluate(java.lang.Object item,QName returnType) return correct boolean
     * value if returnType is Boolean.
     */
    @Test
    public void testCheckXPathExpression07() {
        try {
            assertEquals(xpath.compile(EXPRESSION_NAME_B).
                evaluate(document, BOOLEAN), false);
        } catch (XPathExpressionException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * evaluate(java.lang.Object item,QName returnType) return correct number
     * value when return type is Double.
     */
    @Test
    public void testCheckXPathExpression08() {
        try {
            assertEquals(xpath.compile(EXPRESSION_NAME_A).
                evaluate(document, NUMBER), 6d);
        } catch (XPathExpressionException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * evaluate(java.lang.Object item,QName returnType) evaluate an attribute
     * value which returnType is Node.
     */
    @Test
    public void testCheckXPathExpression09() {
        try {
            Attr attr = (Attr) xpath.compile(EXPRESSION_NAME_A).
                    evaluate(document, NODE);
            assertEquals(attr.getValue(), "6");
        } catch (XPathExpressionException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * evaluate(java.lang.Object item,QName returnType) evaluate an attribute
     * value which returnType is NodeList.
     */
    @Test
    public void testCheckXPathExpression10() {
        try {
            NodeList nodeList = (NodeList) xpath.compile(EXPRESSION_NAME_A).
                    evaluate(document, NODESET);
            Attr attr = (Attr) nodeList.item(0);
            assertEquals(attr.getValue(), "6");
        } catch (XPathExpressionException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * Test for evaluate(java.lang.Object item) when returnType is left off of
     * the XPath.evaluate method, all expressions are evaluated to a String
     * value.
     */
    @Test
    public void testCheckXPathExpression11() {
        try {
            assertEquals(xpath.compile(EXPRESSION_NAME_A).evaluate(document), "6");
        } catch (XPathExpressionException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * evaluate(java.lang.Object item) throws NPE if expression is null.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testCheckXPathExpression12() {
        try {
            xpath.compile(null).evaluate(document);
        } catch (XPathExpressionException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * evaluate(java.lang.Object item) when a request is made to evaluate the
     * expression in the absence of a context item, simple expressions, such as
     * "1+1", can be evaluated.
     */
    @Test
    public void testCheckXPathExpression13() {
        try {
            assertEquals(xpath.compile("1+1").evaluate(document), "2");
        } catch (XPathExpressionException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * evaluate(java.lang.Object item) throws NPE if document is null.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testCheckXPathExpression14() {
        try {
            xpath.compile(EXPRESSION_NAME_A).evaluate(null);
        } catch (XPathExpressionException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * valuate(InputSource source) return a string value if return type is
     * String.
     */
    @Test
    public void testCheckXPathExpression15() {
        try (InputStream is = Files.newInputStream(XML_PATH)) {
            assertEquals(xpath.compile(EXPRESSION_NAME_A).
                    evaluate(new InputSource(is)), "6");
        } catch (XPathExpressionException | IOException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * evaluate(InputSource source) throws NPE if input source is null.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testCheckXPathExpression16() {
        try {
            xpath.compile(EXPRESSION_NAME_A).evaluate(null);
        } catch (XPathExpressionException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * evaluate(InputSource source) throws NPE if expression is null
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testCheckXPathExpression17() {
        try (InputStream is = Files.newInputStream(XML_PATH)) {
            xpath.compile(null).evaluate(new InputSource(is));
        } catch (XPathExpressionException | IOException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * evaluate(InputSource source) throws XPathExpressionException if
     * returnType is String junk characters.
     *
     * @throws XPathExpressionException
     */
    @Test(expectedExceptions = XPathExpressionException.class)
    public void testCheckXPathExpression18() throws XPathExpressionException {
        try (InputStream is = Files.newInputStream(XML_PATH)) {
            xpath.compile("-*&").evaluate(new InputSource(is));
        } catch (IOException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * evaluate(InputSource source) throws XPathExpressionException if
     * expression is a blank string " ".
     *
     * @throws XPathExpressionException
     */
    @Test(expectedExceptions = XPathExpressionException.class)
    public void testCheckXPathExpression19() throws XPathExpressionException {
        try (InputStream is = Files.newInputStream(XML_PATH)) {
            xpath.compile(" ").evaluate(new InputSource(is));
        } catch (IOException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * Test for evaluate(InputSource source,QName returnType) returns a string
     * value if returnType is String.
     */
    @Test
    public void testCheckXPathExpression20() {
        try (InputStream is = Files.newInputStream(XML_PATH)) {
            assertEquals(xpath.compile(EXPRESSION_NAME_A).
                evaluate(new InputSource(is), STRING), "6");
        } catch (XPathExpressionException | IOException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * evaluate(InputSource source,QName returnType) throws NPE if source is
     * null.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testCheckXPathExpression21() {
        try {
            xpath.compile(EXPRESSION_NAME_A).evaluate(null, STRING);
        } catch (XPathExpressionException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * evaluate(InputSource source,QName returnType) throws NPE if expression is
     * null.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testCheckXPathExpression22() {
        try (InputStream is = Files.newInputStream(XML_PATH)) {
            xpath.compile(null).evaluate(new InputSource(is), STRING);
        } catch (XPathExpressionException | IOException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * evaluate(InputSource source,QName returnType) throws NPE if returnType is
     * null.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testCheckXPathExpression23() {
        try (InputStream is = Files.newInputStream(XML_PATH)) {
            xpath.compile(EXPRESSION_NAME_A).evaluate(new InputSource(is), null);
        } catch (XPathExpressionException | IOException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * evaluate(InputSource source,QName returnType) throws
     * XPathExpressionException if expression is junk characters.
     *
     * @throws XPathExpressionException
     */
    @Test(expectedExceptions = XPathExpressionException.class)
    public void testCheckXPathExpression24() throws XPathExpressionException {
        try (InputStream is = Files.newInputStream(XML_PATH)) {
            xpath.compile("-*&").evaluate(new InputSource(is), STRING);
        } catch (IOException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * evaluate(InputSource source,QName returnType) throws
     * XPathExpressionException if expression is blank " ".
     *
     * @throws XPathExpressionException
     */
    @Test(expectedExceptions = XPathExpressionException.class)
    public void testCheckXPathExpression25() throws XPathExpressionException {
        try (InputStream is = Files.newInputStream(XML_PATH)) {
            xpath.compile(" ").evaluate(new InputSource(is), STRING);
        } catch (IOException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * evaluate(InputSource source,QName returnType) throws
     * IllegalArgumentException if returnType is not one of the types defined
     * in XPathConstants.
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testCheckXPathExpression26() {
        try (InputStream is = Files.newInputStream(XML_PATH)) {
            xpath.compile(EXPRESSION_NAME_A).evaluate(new InputSource(is), TEST_QNAME);
        } catch (XPathExpressionException | IOException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * evaluate(InputSource source,QName returnType) return a correct boolean
     * value if returnType is Boolean.
     */
    @Test
    public void testCheckXPathExpression27() {
        try (InputStream is = Files.newInputStream(XML_PATH)) {
            assertEquals(xpath.compile(EXPRESSION_NAME_A).
                evaluate(new InputSource(is), BOOLEAN), true);
        } catch (XPathExpressionException | IOException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * evaluate(InputSource source,QName returnType) return a correct boolean
     * value if returnType is Boolean.
     */
    @Test
    public void testCheckXPathExpression28() {
        try (InputStream is = Files.newInputStream(XML_PATH)) {
            assertEquals(xpath.compile(EXPRESSION_NAME_B).
                evaluate(new InputSource(is), BOOLEAN), false);
        } catch (XPathExpressionException | IOException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * evaluate(InputSource source,QName returnType) return a correct number
     * value if returnType is Number.
     */
    @Test
    public void testCheckXPathExpression29() {
        try (InputStream is = Files.newInputStream(XML_PATH)) {
            assertEquals(xpath.compile(EXPRESSION_NAME_A).
                evaluate(new InputSource(is), NUMBER), 6d);
        } catch (XPathExpressionException | IOException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * Test for evaluate(InputSource source,QName returnType) returns a node if
     * returnType is Node.
     */
    @Test
    public void testCheckXPathExpression30() {
        try (InputStream is = Files.newInputStream(XML_PATH)) {
            Attr attr = (Attr) xpath.compile(EXPRESSION_NAME_A).
                evaluate(new InputSource(is), NODE);
            assertEquals(attr.getValue(), "6");
        } catch (XPathExpressionException | IOException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * Test for evaluate(InputSource source,QName returnType) return a node list
     * if returnType is NodeList.
     */
    @Test
    public void testCheckXPathExpression31() {
        try (InputStream is = Files.newInputStream(XML_PATH)) {
            NodeList nodeList = (NodeList) xpath.compile(EXPRESSION_NAME_A).
                evaluate(new InputSource(is), NODESET);
            assertEquals(((Attr) nodeList.item(0)).getValue(), "6");
        } catch (XPathExpressionException | IOException  ex) {
            failUnexpected(ex);
        }
    }
}
