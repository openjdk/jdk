/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.*;
import jaxp.library.JUnitTestUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8354084
 * @summary Verifies that extensions to XPathFunction.
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest /test/lib
 * @run junit xpath.XPathFunctionTest
 */
public class XPathFunctionTest {
    /*
     * Arguments for XPath Extension Function Test, refer to the test below.
     */
    private static Stream<Arguments> testData() {
        // expected result when a resolver is set properly
        String result = "id=2 price=20";
        return Stream.of(
                // cases where the result is as expected when a resolver is set
                Arguments.of(true, false, false, false, false, result, null),
                Arguments.of(true, true, false, false, false, result, null),
                Arguments.of(true, true, false, true, false, result, null),
                Arguments.of(true, true, false, true, true, result, null),
                Arguments.of(true, true, true, true, true, result, null),
                // cases XPathExpressionException was thrown before the change even though there's a resolver
                Arguments.of(true, true, true, false, false, result, null),
                Arguments.of(true, true, true, true, false, result, null),
                // XPathExpressionException will continue to be thrown due to missing resolver, though it was
                // thrown for a different reason (FSP is turned on) before the change
                Arguments.of(false, false, false, false, false, result, XPathExpressionException.class),
                Arguments.of(false, true, true, false, false, result, XPathExpressionException.class),
                Arguments.of(false, true, true, true, false, result, XPathExpressionException.class)
        );
    }

    /**
     * Verifies the control over XPath Extension Functions.
     * @param useResolver indicates whether there is a custom resolver
     * @param setFSP indicates whether FSP is to be set
     * @param FSPValue the FSP value
     * @param setProperty indicates whether the property {@code jdk.xml.enableExtensionFunctions}
     *                   is to be set
     * @param propertyValue the property value
     * @param expected the expected result
     * @param expectedType the expected throw type
     * @throws Exception if the test fails other than the expected Exception, which
     * would indicate an issue in configuring the test
     */
    @ParameterizedTest
    @MethodSource("testData")
    public void test(boolean useResolver, boolean setFSP, boolean FSPValue,
            boolean setProperty, boolean propertyValue, String expected, Class<Throwable> expectedType) throws Exception {
        if (expectedType != null) {
            assertThrows(expectedType, () -> findToy(useResolver, setFSP, FSPValue, setProperty, propertyValue, expectedType));
        } else {
            String result = findToy(useResolver, setFSP, FSPValue, setProperty, propertyValue, expectedType);
            assertEquals(expected, result);
        }
    }

    public String findToy(boolean useResolver, boolean setFSP, boolean FSPValue,
            boolean setProperty, boolean propertyValue, Class<Throwable> expectedType)
            throws Exception {

            Document doc = getDocument(JUnitTestUtil.SRC_DIR + "/XPathFunctionTest.xml");
            XPathFactory xpf = XPathFactory.newDefaultInstance();
            if (useResolver) {
                xpf.setXPathFunctionResolver(new FunctionResolver(doc));
            }
            if (setFSP) {
                xpf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, FSPValue);
            }
            if (setProperty) {
                xpf.setFeature("jdk.xml.enableExtensionFunctions", propertyValue);
            }

            XPath xPath = xpf.newXPath();
            XPathExpression exp = xPath.compile("ext:findToy('name', 'Another toy')");
            Node toyNode = (Node)exp.evaluate(doc, XPathConstants.NODE);
            String id = "", price = "";
            if (toyNode != null && toyNode.getNodeType() == Node.ELEMENT_NODE) {
                Element toyElement = (Element)toyNode;
                id = toyElement.getAttribute("id");
                price = toyElement.getElementsByTagName("price").item(0).getTextContent();

            }
            return "id=" + id + " price=" + price;

    }

    /**
     * Returns a DOM Document.
     * @param xmlFile the XML document to be parsed
     * @return a DOM Document
     * @throws Exception if error occurs
     */
    Document getDocument(String xmlFile)
            throws Exception {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder();
            Document out = builder.parse(xmlFile);
            return out;
        } catch (Exception e) {
            // won't happen, parsing a valid file
        }
        return null;
    }

    // XPathFunctionResolver customized for the FindFunction
    class FunctionResolver implements XPathFunctionResolver {
        private final Document doc;

        public FunctionResolver(Document doc) {
            this.doc = doc;
        }

        @Override
        public XPathFunction resolveFunction(QName functionName, int arity) {
            if ("findToy".equals(functionName.getLocalPart()) && arity == 2) {
                return new FindFunction(doc);
            }

            return null;
        }
    }

    // The Find function
    class FindFunction implements XPathFunction {
        private final Document doc;

        public FindFunction(Document doc) {
            this.doc = doc;
        }

        @SuppressWarnings("rawtypes")
        public Object evaluate(List list) throws XPathFunctionException {
            if (list == null || list.size() != 2) {
                throw new XPathFunctionException("FindToy requires two args: name and value");
            }

            String eleName = (String)list.get(0);
            String eleValue = (String)list.get(1);
            NodeList toys = doc.getElementsByTagName("toy");

            for (int i = 0; i<toys.getLength(); i++) {
                Element toy = (Element)toys.item(i);
                NodeList children = toy.getElementsByTagName(eleName);

                if (children.getLength() > 0) {
                    String text = children.item(0).getTextContent();
                    if (eleValue.equals(text)) {
                        return toy;
                    }
                }
            }
            return null;
        }
    }
}
