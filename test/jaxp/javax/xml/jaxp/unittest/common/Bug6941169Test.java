/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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

package common;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;

import static jaxp.library.JAXPTestUtilities.clearSystemProperty;
import static jaxp.library.JAXPTestUtilities.setSystemProperty;
import org.testng.Assert;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/*
 * @test
 * @bug 6941169
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run testng/othervm -DrunSecMngr=true common.Bug6941169Test
 * @run testng/othervm common.Bug6941169Test
 * @summary Test use-service-mechanism feature.
 */
@Test(singleThreaded = true)
@Listeners({ jaxp.library.FilePolicy.class })
public class Bug6941169Test {
    private static final String SCHEMA_LANGUAGE = "http://java.sun.com/xml/jaxp/properties/schemaLanguage";
    private static final String SCHEMA_SOURCE = "http://java.sun.com/xml/jaxp/properties/schemaSource";

    private static final String DOM_FACTORY_ID = "javax.xml.parsers.DocumentBuilderFactory";
    private static final String SAX_FACTORY_ID = "javax.xml.parsers.SAXParserFactory";
    private static final String SAX_FACTORY_IMP = "MySAXFactoryImpl";
    private static final String DOM_FACTORY_IMP = "MyDOMFactoryImpl";

    // impl specific feature
    private static final String ORACLE_FEATURE_SERVICE_MECHANISM = "http://www.oracle.com/feature/use-service-mechanism";

    private static final String _XML = Bug6941169Test.class.getResource("Bug6941169.xml").getPath();
    private static final String _XSD = Bug6941169Test.class.getResource("Bug6941169.xsd").getPath();
    private static final String FACT_CONF_ERR_MSG = "javax.xml.parsers.FactoryConfigurationError: Provider MySAXFactoryImpl not found";
    private static final String DOM_FACT_ERR_MSG = "Provider MyDOMFactoryImpl not found";

    @Test
    public void testValidation_SAX_withoutServiceMech() throws FileNotFoundException {
        System.out.println("Validation using SAX Source;  Service mechnism is turned off;  SAX Impl should be the default:");
        InputSource is = new InputSource(new FileInputStream(_XML));
        SAXSource ss = new SAXSource(is);
        setSystemProperty(SAX_FACTORY_ID, SAX_FACTORY_IMP);
        long start = System.currentTimeMillis();
        try {
            saxValidator(ss, true);
        } catch (Exception e) {
            domSaxErrorHandler(e, FACT_CONF_ERR_MSG, "Default impl is used");
        } finally {
            clearSystemProperty(SAX_FACTORY_ID);
        }
        testExecutionTime(start);
    }

    @Test
    public void testValidation_SAX_withServiceMech() {
        System.out.println("Validation using SAX Source. Using service mechnism (by default) to find SAX Impl:");
        InputSource is = new InputSource(Bug6941169Test.class.getResourceAsStream("Bug6941169.xml"));
        SAXSource ss = new SAXSource(is);
        setSystemProperty(SAX_FACTORY_ID, SAX_FACTORY_IMP);
        long start = System.currentTimeMillis();
        try {
            saxValidator(ss, false);
            Assert.fail("User impl MySAXFactoryImpl should be used.");
        } catch (Exception e) {
            String error = e.getMessage();
            if (error.contains(FACT_CONF_ERR_MSG)) {
                // expected
            }
        } finally {
            clearSystemProperty(SAX_FACTORY_ID);
        }
        testExecutionTime(start);
    }

    @Test
    public void testValidation_SAX_withSM() throws Exception {
        if(System.getSecurityManager() == null) {
            return;
        }
        System.out.println("Validation using SAX Source with security manager:");
        InputSource is = new InputSource(Bug6941169Test.class.getResourceAsStream("Bug6941169.xml"));
        SAXSource ss = new SAXSource(is);
        setSystemProperty(SAX_FACTORY_ID, SAX_FACTORY_IMP);
        long start = System.currentTimeMillis();
        try {
            saxValidator(ss, false);
        } catch (Exception e) {
            domSaxErrorHandler(e, FACT_CONF_ERR_MSG, "Default impl is used");
        } finally {
            clearSystemProperty(SAX_FACTORY_ID);
        }
        testExecutionTime(start);
    }

    @Test
    public void testTransform_DOM_withoutServiceMech() {
        System.out.println("Transform using DOM Source;  Service mechnism is turned off;  Default DOM Impl should be the default:");
        DOMSource domSource = new DOMSource(getDocument(Bug6941169Test.class.getResourceAsStream("Bug6941169.xml")));
        setSystemProperty(DOM_FACTORY_ID, DOM_FACTORY_IMP);
        long start = System.currentTimeMillis();
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(ORACLE_FEATURE_SERVICE_MECHANISM, false);

            Transformer t = factory.newTransformer();

            StringWriter result = new StringWriter();
            StreamResult streamResult = new StreamResult(result);
            t.transform(domSource, streamResult);
            System.out.println("Writing to " + result.toString());

        } catch (Exception | Error e) {
            // e.printStackTrace();
            domSaxErrorHandler(e, DOM_FACT_ERR_MSG, "Default impl is used");
        } finally {
            clearSystemProperty(DOM_FACTORY_ID);
        }
        testExecutionTime(start);
    }

    /** this is by default */
    @Test
    public void testTransform_DOM_withServiceMech() {
        System.out.println("Transform using DOM Source;  By default, the factory uses services mechanism to look up impl:");
        DOMSource domSource = new DOMSource();
        domSource.setSystemId(_XML);
        setSystemProperty(DOM_FACTORY_ID, DOM_FACTORY_IMP);
        long start = System.currentTimeMillis();
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            domTransformer(domSource, factory);
            Assert.fail("User impl MyDOMFactoryImpl should be used.");
        } catch (Exception | Error e) {
            String error = e.getMessage();
            if (error.contains(DOM_FACT_ERR_MSG)) {
                // expected
            }
            System.out.println(error);
        } finally {
            clearSystemProperty(DOM_FACTORY_ID);
        }
        testExecutionTime(start);
    }

    @Test
    public void testTransform_DOM_withSM() {
        if(System.getSecurityManager() == null)
            return;
        System.out.println("Transform using DOM Source;  Security Manager is set:");
        DOMSource domSource = new DOMSource();
        domSource.setSystemId(_XML);
        setSystemProperty(DOM_FACTORY_ID, DOM_FACTORY_IMP);
        long start = System.currentTimeMillis();
        try {
            TransformerFactory factory = TransformerFactory.newInstance("com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl",
                    TransformerFactory.class.getClassLoader());
            domTransformer(domSource, factory);
        } catch (Exception | Error e) {
            domSaxErrorHandler(e, DOM_FACT_ERR_MSG, "Default impl is used");
        } finally {
            clearSystemProperty(DOM_FACTORY_ID);
        }
        testExecutionTime(start);
    }

    @Test
    public void testXPath_DOM_withoutServiceMech() {
        final String XPATH_EXPRESSION = "/fooTest";
        System.out.println("Evaluate DOM Source;  Service mechnism is turned off;  Default DOM Impl should be used:");
        Document doc = getDocument(Bug6941169Test.class.getResourceAsStream("Bug6941169.xml"));
        setSystemProperty(DOM_FACTORY_ID, DOM_FACTORY_IMP);
        long start = System.currentTimeMillis();
        try {
            XPathFactory xPathFactory = XPathFactory.newInstance();
            xPathEvaluator(XPATH_EXPRESSION, doc, xPathFactory, true, null);
        } catch (Exception | Error e) {
            // e.printStackTrace();
            domSaxErrorHandler(e, DOM_FACT_ERR_MSG, "Default impl is used");
        } finally {
            clearSystemProperty(DOM_FACTORY_ID);
        }
        testExecutionTime(start);
    }

    @Test
    public void testXPath_DOM_withServiceMech() {
        /**
         * This is in conflict with the test testXPath_DOM_withSM where the system
         * default parser is used when the security manager is present. The test
         * is therefore skipped when the security manager is present.
         */
        if (System.getSecurityManager() != null) {
            return;
        }
        final String XPATH_EXPRESSION = "/fooTest";
        System.out.println("Evaluate DOM Source;  Service mechnism is on by default;  It would try to use MyDOMFactoryImpl:");
        InputStream input = getClass().getResourceAsStream("Bug6941169.xml");
        InputSource source = new InputSource(input);
        setSystemProperty(DOM_FACTORY_ID, DOM_FACTORY_IMP);
        long start = System.currentTimeMillis();
        try {
            XPathFactory xPathFactory = XPathFactory.newInstance();
            xPathEvaluator(XPATH_EXPRESSION, null, xPathFactory, false, source);
            Assert.fail("User impl MyDOMFactoryImpl should be used.");
        } catch (Exception  | Error e) {
            // e.printStackTrace();
            String error = e.getMessage();
            if (error.contains(DOM_FACT_ERR_MSG)) {
                System.out.println("Tried to locate MyDOMFactoryImpl");
            } else {
                Assert.fail(e.getMessage());
            }
        } finally {
            clearSystemProperty(DOM_FACTORY_ID);
        }
        testExecutionTime(start);
    }

    @Test
    public void testXPath_DOM_withSM() throws Exception {
        if(System.getSecurityManager() == null)
            return;
        final String XPATH_EXPRESSION = "/fooTest";
        System.out.println("Evaluate DOM Source;  Security Manager is set:");
        InputStream input = getClass().getResourceAsStream("Bug6941169.xml");
        InputSource source = new InputSource(input);
        setSystemProperty(DOM_FACTORY_ID, DOM_FACTORY_IMP);
        long start = System.currentTimeMillis();
        try {
            XPathFactory xPathFactory = XPathFactory.newInstance("http://java.sun.com/jaxp/xpath/dom",
                    "com.sun.org.apache.xpath.internal.jaxp.XPathFactoryImpl", null);
            xPathEvaluator(XPATH_EXPRESSION, null, xPathFactory, false, source);
            System.out.println("Use default impl");
        } catch (Exception | Error e) {
            domSaxErrorHandler(e, DOM_FACT_ERR_MSG, "Default impl should be used");
        } finally {
            clearSystemProperty(DOM_FACTORY_ID);
        }
        testExecutionTime(start);
    }

    @Test
    public void testSM() {
        SecurityManager sm = System.getSecurityManager();
        if (System.getSecurityManager() != null) {
            System.out.println("Security manager not cleared: " + sm.toString());
        } else {
            System.out.println("Security manager cleared: ");
        }
    }

    private void domSaxErrorHandler(Throwable e, String domFactErrMsg, String s) {
        String error = e.getMessage();
        if (error.contains(domFactErrMsg)) {
            Assert.fail(e.getMessage());
        } else {
            System.out.println(s);
        }
    }

    private void saxValidator(SAXSource ss, boolean setFeature) throws SAXException, IOException {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        if(setFeature) {
            factory.setFeature(ORACLE_FEATURE_SERVICE_MECHANISM, false);
        }
        Schema schema = factory.newSchema(new StreamSource(_XSD));
        Validator validator = schema.newValidator();
        validator.validate(ss, null);
    }

    private void xPathEvaluator(String XPATH_EXPRESSION, Document doc, XPathFactory xPathFactory, boolean setFeature, InputSource source) throws
            XPathFactoryConfigurationException, XPathExpressionException {
        if(setFeature) {
            xPathFactory.setFeature(ORACLE_FEATURE_SERVICE_MECHANISM, false);
        }
        XPath xPath = xPathFactory.newXPath();
        String xPathResult;
        if(doc != null) {
            xPathResult = xPath.evaluate(XPATH_EXPRESSION, doc);
        } else {
            xPathResult = xPath.evaluate(XPATH_EXPRESSION, source);
        }
    }

    private void testExecutionTime(long start) {
        long end = System.currentTimeMillis();
        double elapsedTime = ((end - start));
        System.out.println("Time elapsed: " + elapsedTime + " milli seconds");
    }

    private void domTransformer(DOMSource domSource, TransformerFactory factory) throws TransformerException {
        Transformer t = factory.newTransformer();
        StringWriter result = new StringWriter();
        StreamResult streamResult = new StreamResult(result);
        t.transform(domSource, streamResult);
        System.out.println("Writing to " + result.toString());
    }

    private static Document getDocument(InputStream in) {
        Document document = null;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            document = db.parse(in);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.toString());
        }
        return document;
    }
}
