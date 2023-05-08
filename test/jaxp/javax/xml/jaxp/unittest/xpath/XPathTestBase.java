/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.*;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/*
 * Base class for XPath test
 */
class XPathTestBase {
    static final String DECLARATION = "<?xml version=\"1.0\" " +
            "encoding=\"UTF-8\" standalone=\"yes\"?>";

    static final String DTD = """
            <!DOCTYPE Customers [
               <!ELEMENT Customers (Customer*)>
               <!ELEMENT Customer (Name, Phone, Email, Address, Age, ClubMember)>
               <!ELEMENT Name (#PCDATA)>
               <!ELEMENT Phone (#PCDATA)>
               <!ELEMENT Email (#PCDATA)>
               <!ELEMENT Address (Street, City, State)>
               <!ELEMENT Age (#PCDATA)>
               <!ELEMENT ClubMember (#PCDATA)>
               <!ELEMENT Street (#PCDATA)>
               <!ELEMENT City (#PCDATA)>
               <!ELEMENT State (#PCDATA)>
               <!ATTLIST Customer id ID #REQUIRED>
               <!ATTLIST Email id ID #REQUIRED>
            ]>

            """;

    static final String RAW_XML
            = "<Customers xmlns:foo=\"foo\" xml:lang=\"en\">"
            + "<!-- This is a comment -->"
            + "    <Customer id=\"x1\">"
            + "        <Name>name1</Name>"
            + "        <Phone>1111111111</Phone>"
            + "        <Email id=\"x\">123@xyz.com</Email>"
            + "        <Address>"
            + "            <Street>1111 111st ave</Street>"
            + "            <City>The City</City>"
            + "            <State>The State</State>"
            + "        </Address>"
            + "        <Age>0</Age>"
            + "        <ClubMember>true</ClubMember>"
            + "    </Customer>"
            + "    <Customer id=\"x2\">"
            + "        <Name>name2</Name>"
            + "        <Phone>2222222222</Phone>"
            + "        <Email id=\"y\">123@xyz.com</Email>"
            + "        <Address>"
            + "            <Street>  2222 222nd ave  </Street>"
            + "            <City>The City</City>"
            + "            <State>The State</State>"
            + "        </Address>"
            + "        <Age>100</Age>"
            + "        <ClubMember>false</ClubMember>"
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
            + "        <Age>-100</Age>"
            + "        <ClubMember>false</ClubMember>"
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
            + "        <foo:Age>0</foo:Age>"
            + "        <foo:ClubMember>true</foo:ClubMember>"
            + "    </foo:Customer>"
            + "</Customers>";

    // Number of root element.
    final int ROOT = 1;
    // Number of Customer elements.
    final int LANG_ATTRIBUTES = 1;
    final int CUSTOMERS = 3;
    // Number of id attributes.
    final int ID_ATTRIBUTES = 6;
    // Number of child elements of Customer.
    final int CUSTOMER_ELEMENTS = 6;
    // Number of Address elements.
    final int ADDRESS_ELEMENTS = 3;
    // Number of Customer in the foo namespace.
    final int FOO_CUSTOMERS = 1;
    // Number of id attributes in the foo namespace.
    final int FOO_ID_ATTRIBUTES = 2;
    // Customer Ages
    final int[] CUSTOMER_AGES = {0, 100, -100, 0};

    /**
     * Returns a {@link org.w3c.dom.Document} for XML with DTD.
     * @return a DOM Document
     * @throws RuntimeException if any error occurred during document
     *  initialization.
     */
    public static Document getDtdDocument() throws RuntimeException {
        return documentOf(DECLARATION + DTD + RAW_XML);
    }

    /**
     * Returns a {@link org.w3c.dom.Document} for raw XML.
     * @return a DOM Document
     * @throws RuntimeException if any error occurred during document
     *  initialization.
     */
    public static Document getDocument() throws RuntimeException {
        return documentOf(DECLARATION + RAW_XML);
    }

    /**
     * Returns a {@link org.w3c.dom.Document} for input XML string.
     * @param xml the input xml string.
     * @return a DOM Document.
     * @throws RuntimeException if any error occurred during document
     *  initialization.
     */
    public static Document documentOf(String xml) throws RuntimeException {
        try {
            var dBF = DocumentBuilderFactory.newInstance();
            dBF.setValidating(false);
            dBF.setNamespaceAware(true);
            return dBF.newDocumentBuilder().parse(
                    new ByteArrayInputStream(xml.getBytes("UTF-8")));
        } catch (Exception e) {
            System.out.println("Exception while initializing XML document");
            throw new RuntimeException(e.getMessage());
        }
    }

    void verifyResult(XPathEvaluationResult<?> result, Object expected) {
        switch (result.type()) {
            case BOOLEAN:
                assertTrue(((Boolean) result.value()).equals(expected));
                return;
            case NUMBER:
                assertTrue(((Double) result.value()).equals(expected));
                return;
            case STRING:
                assertTrue(((String) result.value()).equals(expected));
                return;
            case NODESET:
                XPathNodes nodes = (XPathNodes) result.value();
                for (Node n : nodes) {
                    assertEquals(n.getLocalName(), expected);
                }
                return;
            case NODE:
                assertTrue(((Node) result.value()).getLocalName().equals(expected));
                return;
        }
        assertFalse(true, "Unsupported type");
    }

    /**
     * Evaluates XPath expression and checks if it matches the expected result.
     *
     * @param doc xml document {@link org.w3c.dom.Document}
     * @param exp xpath expression string
     * @param expected expected result
     * @param clazz expected result type for evaluation.
     * @param <T> expected result type
     *
     * @throws Exception if test fails
     */
    static <T> void testExp(Document doc, String exp, T expected,
                          Class<T> clazz) throws Exception {
        XPath xPath = XPathFactory.newInstance().newXPath();

        T result = xPath.evaluateExpression(exp, doc, clazz);
        T result2 = (T) xPath.evaluate(exp, doc,
                XPathEvaluationResult.XPathResultType.getQNameType(clazz));

        Assert.assertEquals(result, expected);
        Assert.assertEquals(result2, result);
    }

    /**
     * Evaluates XPath expression.
     *
     * @param doc xml document {@link org.w3c.dom.Document}
     * @param exp xpath expression string
     *
     * @throws Exception if test fails
     */
    static void testEval(Document doc, String exp) throws Exception {
        XPath xPath = XPathFactory.newInstance().newXPath();

        try {
            xPath.evaluateExpression(exp, doc);
        } catch (XPathExpressionException e) {
            xPath.evaluate(exp, doc);
        }
    }

    /*
     * DataProvider: XPath object
     */
    @DataProvider(name = "xpath")
    public Object[][] getXPath() {
        return new Object[][]{{XPathFactory.newInstance().newXPath()}};
    }

    /*
     * DataProvider: Numeric types not supported
     */
    @DataProvider(name = "invalidNumericTypes")
    public Object[][] getInvalidNumericTypes() {
        XPath xpath = XPathFactory.newInstance().newXPath();
        return new Object[][]{{xpath, AtomicInteger.class},
                {xpath, AtomicInteger.class},
                {xpath, AtomicLong.class},
                {xpath, BigDecimal.class},
                {xpath, BigInteger.class},
                {xpath, Byte.class},
                {xpath, Float.class},
                {xpath, Short.class}
        };
    }

    /*
     * DataProvider: XPath and Document objects
     */
    @DataProvider(name = "document")
    public Object[][] getDocuments() throws RuntimeException {
        Document doc = getDocument();
        return new Object[][]{{XPathFactory.newInstance().newXPath(), doc}};
    }
}
