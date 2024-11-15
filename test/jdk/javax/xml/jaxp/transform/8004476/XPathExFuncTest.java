/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;
import javax.xml.xpath.XPathFunction;
import javax.xml.xpath.XPathFunctionException;
import javax.xml.xpath.XPathFunctionResolver;
import org.w3c.dom.Document;

/**
 * @test
 * @bug 8004476
 * @summary test XPath extension functions
 * @run main/othervm XPathExFuncTest
 */
public class XPathExFuncTest extends TestBase {

    final static String ENABLE_EXTENSION_FUNCTIONS = "http://www.oracle.com/xml/jaxp/properties/enableExtensionFunctions";
    final static String CLASSNAME = "DocumentBuilderFactoryImpl";
    final String XPATH_EXPRESSION = "ext:helloWorld()";

    /**
     * Creates a new instance of StreamReader
     */
    public XPathExFuncTest(String name) {
        super(name);
    }

    String xslFile, xslFileId;
    String xmlFile, xmlFileId;

    protected void setUp() {
        super.setUp();
        xmlFile = filepath + "/SecureProcessingTest.xml";

    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        XPathExFuncTest test = new XPathExFuncTest("OneTest");
        test.setUp();

        test.testExtFunc();
        test.testExtFuncNotAllowed();
        test.testEnableExtFunc();
        test.tearDown();

    }

    /**
     * by default, extension function is enabled
     */
    public void testExtFunc() {

        try {
            evaluate(false, false);
            System.out.println("testExtFunc: OK");
        } catch (XPathFactoryConfigurationException e) {
            fail(e.getMessage());
        } catch (XPathExpressionException e) {
            fail(e.getMessage());
        }
    }

    /**
     * Security is enabled, extension function not allowed.
     * Note: removing Security Manager, use FEATURE_SECURE_PROCESSING instead.
     */
    public void testExtFuncNotAllowed() {
        try {
            evaluate(true, false);
        } catch (XPathFactoryConfigurationException e) {
            fail(e.getMessage());
        } catch (XPathExpressionException ex) {
            //expected since extension function is disallowed
            System.out.println("testExtFuncNotAllowed: OK");
        }
    }

    /**
     * Security is enabled, use new feature: enableExtensionFunctions.
     * Note: removing Security Manager, use FEATURE_SECURE_PROCESSING instead.
     */
    public void testEnableExtFunc() {
        try {
            evaluate(true, true);
            System.out.println("testEnableExt: OK");
        } catch (XPathFactoryConfigurationException e) {
            fail(e.getMessage());
        } catch (XPathExpressionException e) {
            fail(e.getMessage());
        }
    }

    Document getDocument() {
        // the xml source
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = null;
        Document document = null;

        try {
            documentBuilder = documentBuilderFactory.newDocumentBuilder();
            InputStream xmlStream = new FileInputStream(xmlFile);
            document = documentBuilder.parse(xmlStream);
        } catch (Exception e) {
            fail(e.toString());
        }
        return document;
    }

    void evaluate(boolean secureMode, boolean enableExt)
            throws XPathFactoryConfigurationException, XPathExpressionException {
        Document document = getDocument();

        XPathFactory xPathFactory = XPathFactory.newInstance();
        if (secureMode) {
            xPathFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } else {
            xPathFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
        }

        /**
         * Use of the extension function 'http://exslt.org/strings:tokenize' is
         * not allowed when the secure processing feature is set to true.
         * Attempt to use the new property to enable extension function
         */
        if (enableExt) {
            boolean isExtensionSupported = enableExtensionFunction(xPathFactory);
        }

        xPathFactory.setXPathFunctionResolver(new MyXPathFunctionResolver());

        XPath xPath = xPathFactory.newXPath();
        xPath.setNamespaceContext(new MyNamespaceContext());

        String xPathResult = xPath.evaluate(XPATH_EXPRESSION, document);
        System.out.println(
                "XPath result (enableExtensionFunction == " + enableExt + ") = \""
                + xPathResult
                + "\"");
    }

    public class MyXPathFunctionResolver
            implements XPathFunctionResolver {

        public XPathFunction resolveFunction(QName functionName, int arity) {

            // not a real ewsolver, always return a default XPathFunction
            return new MyXPathFunction();
        }
    }

    public class MyXPathFunction
            implements XPathFunction {

        public Object evaluate(List list) throws XPathFunctionException {

            return "Hello World";
        }
    }

    public class MyNamespaceContext implements NamespaceContext {

        public String getNamespaceURI(String prefix) {
            if (prefix == null) {
                throw new IllegalArgumentException("The prefix cannot be null.");
            }

            if (prefix.equals("ext")) {
                return "http://ext.com";
            } else {
                return null;
            }
        }

        public String getPrefix(String namespace) {

            if (namespace == null) {
                throw new IllegalArgumentException("The namespace uri cannot be null.");
            }

            if (namespace.equals("http://ext.com")) {
                return "ext";
            } else {
                return null;
            }
        }

        public Iterator getPrefixes(String namespace) {
            return null;
        }
    }

    boolean enableExtensionFunction(XPathFactory factory) {
        boolean isSupported = true;
        try {
            factory.setFeature(ENABLE_EXTENSION_FUNCTIONS, true);
        } catch (XPathFactoryConfigurationException ex) {
            isSupported = false;
        }
        return isSupported;
    }
}
