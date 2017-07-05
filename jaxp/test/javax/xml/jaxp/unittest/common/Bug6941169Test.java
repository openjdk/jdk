/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.io.InputStream;
import java.io.StringWriter;
import java.security.AllPermission;
import java.security.Permission;
import java.security.Permissions;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/*
 * @bug 6941169
 * @summary Test use-service-mechanism feature.
 */
public class Bug6941169Test {
    static final String SCHEMA_LANGUAGE = "http://java.sun.com/xml/jaxp/properties/schemaLanguage";
    static final String SCHEMA_SOURCE = "http://java.sun.com/xml/jaxp/properties/schemaSource";

    private static final String DOM_FACTORY_ID = "javax.xml.parsers.DocumentBuilderFactory";
    private static final String SAX_FACTORY_ID = "javax.xml.parsers.SAXParserFactory";

    // impl specific feature
    final String ORACLE_FEATURE_SERVICE_MECHANISM = "http://www.oracle.com/feature/use-service-mechanism";

    static String _xml = Bug6941169Test.class.getResource("Bug6941169.xml").getPath();
    static String _xsd = Bug6941169Test.class.getResource("Bug6941169.xsd").getPath();

    @Test
    public void testValidation_SAX_withoutServiceMech() {
        System.out.println("Validation using SAX Source;  Service mechnism is turned off;  SAX Impl should be the default:");
        InputSource is = new InputSource(Bug6941169Test.class.getResourceAsStream("Bug6941169.xml"));
        SAXSource ss = new SAXSource(is);
        System.setProperty(SAX_FACTORY_ID, "MySAXFactoryImpl");
        long start = System.currentTimeMillis();
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setFeature(ORACLE_FEATURE_SERVICE_MECHANISM, false);
            Schema schema = factory.newSchema(new StreamSource(_xsd));
            Validator validator = schema.newValidator();
            validator.validate(ss, null);
        } catch (Exception e) {
            // e.printStackTrace();
            String error = e.getMessage();
            if (error.indexOf("javax.xml.parsers.FactoryConfigurationError: Provider MySAXFactoryImpl not found") > 0) {
                Assert.fail(e.getMessage());
            } else {
                System.out.println("Default impl is used");
            }

            // System.out.println(e.getMessage());

        }
        long end = System.currentTimeMillis();
        double elapsedTime = ((end - start));
        System.out.println("Time elapsed: " + elapsedTime);
        System.clearProperty(SAX_FACTORY_ID);
    }

    @Test
    public void testValidation_SAX_withServiceMech() {
        System.out.println("Validation using SAX Source. Using service mechnism (by default) to find SAX Impl:");
        InputSource is = new InputSource(Bug6941169Test.class.getResourceAsStream("Bug6941169.xml"));
        SAXSource ss = new SAXSource(is);
        System.setProperty(SAX_FACTORY_ID, "MySAXFactoryImpl");
        long start = System.currentTimeMillis();
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = factory.newSchema(new StreamSource(_xsd));
            Validator validator = schema.newValidator();
            validator.validate(ss, null);
            Assert.fail("User impl MySAXFactoryImpl should be used.");
        } catch (Exception e) {
            String error = e.getMessage();
            if (error.indexOf("javax.xml.parsers.FactoryConfigurationError: Provider MySAXFactoryImpl not found") > 0) {
                // expected
            }
            // System.out.println(e.getMessage());

        }
        long end = System.currentTimeMillis();
        double elapsedTime = ((end - start));
        System.out.println("Time elapsed: " + elapsedTime);
        System.clearProperty(SAX_FACTORY_ID);
    }

    @Test
    public void testValidation_SAX_withSM() {
        System.out.println("Validation using SAX Source with security manager:");
        InputSource is = new InputSource(Bug6941169Test.class.getResourceAsStream("Bug6941169.xml"));
        SAXSource ss = new SAXSource(is);
        System.setProperty(SAX_FACTORY_ID, "MySAXFactoryImpl");
        Permissions granted = new java.security.Permissions();
        granted.add(new AllPermission());
        System.setSecurityManager(new MySM(granted));

        long start = System.currentTimeMillis();
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setFeature(ORACLE_FEATURE_SERVICE_MECHANISM, false);
            Schema schema = factory.newSchema(new StreamSource(_xsd));
            Validator validator = schema.newValidator();
            validator.validate(ss, null);
        } catch (Exception e) {
            String error = e.getMessage();
            if (error.indexOf("javax.xml.parsers.FactoryConfigurationError: Provider MySAXFactoryImpl not found") > 0) {
                Assert.fail(e.getMessage());
            } else {
                System.out.println("Default impl is used");
            }

            // System.out.println(e.getMessage());

        } finally {
            System.clearProperty(SAX_FACTORY_ID);
            System.setSecurityManager(null);
        }
        long end = System.currentTimeMillis();
        double elapsedTime = ((end - start));
        System.out.println("Time elapsed: " + elapsedTime);
        System.setSecurityManager(null);

    }

    @Test
    public void testTransform_DOM_withoutServiceMech() {
        System.out.println("Transform using DOM Source;  Service mechnism is turned off;  Default DOM Impl should be the default:");
        DOMSource domSource = new DOMSource();
        domSource.setSystemId(_xml);

        // DOMSource domSource = new
        // DOMSource(getDocument(Bug6941169Test.class.getResourceAsStream("Bug6941169.xml")));
        System.setProperty(DOM_FACTORY_ID, "MyDOMFactoryImpl");
        long start = System.currentTimeMillis();
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(ORACLE_FEATURE_SERVICE_MECHANISM, false);

            Transformer t = factory.newTransformer();

            StringWriter result = new StringWriter();
            StreamResult streamResult = new StreamResult(result);
            t.transform(domSource, streamResult);
            System.out.println("Writing to " + result.toString());

        } catch (Exception e) {
            // e.printStackTrace();
            String error = e.getMessage();
            if (error.indexOf("Provider MyDOMFactoryImpl not found") > 0) {
                Assert.fail(e.getMessage());
            } else {
                System.out.println("Default impl is used");
            }

            // System.out.println(e.getMessage());

        } catch (Error e) {
            // e.printStackTrace();
            String error = e.getMessage();
            if (error.indexOf("Provider MyDOMFactoryImpl not found") > 0) {
                Assert.fail(e.getMessage());
            } else {
                System.out.println("Default impl is used");
            }

            // System.out.println(e.getMessage());

        }

        long end = System.currentTimeMillis();
        double elapsedTime = ((end - start));
        System.out.println("Time elapsed: " + elapsedTime);
        System.clearProperty(DOM_FACTORY_ID);
    }

    /** this is by default */
    @Test
    public void testTransform_DOM_withServiceMech() {
        System.out.println("Transform using DOM Source;  By default, the factory uses services mechanism to look up impl:");
        DOMSource domSource = new DOMSource();
        domSource.setSystemId(_xml);

        // DOMSource domSource = new
        // DOMSource(getDocument(Bug6941169Test.class.getResourceAsStream("Bug6941169.xml")));
        System.setProperty(DOM_FACTORY_ID, "MyDOMFactoryImpl");
        long start = System.currentTimeMillis();
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer t = factory.newTransformer();

            StringWriter result = new StringWriter();
            StreamResult streamResult = new StreamResult(result);
            t.transform(domSource, streamResult);
            System.out.println("Writing to " + result.toString());

            Assert.fail("User impl MyDOMFactoryImpl should be used.");

        } catch (Exception e) {
            String error = e.getMessage();
            if (error.indexOf("Provider MyDOMFactoryImpl not found") > 0) {
                // expected
            }
            System.out.println(error);

        } catch (Error e) {
            String error = e.getMessage();
            if (error.indexOf("Provider MyDOMFactoryImpl not found") > 0) {
                // expected
            }
            System.out.println(error);

        }

        long end = System.currentTimeMillis();
        double elapsedTime = ((end - start));
        System.out.println("Time elapsed: " + elapsedTime);
        System.clearProperty(DOM_FACTORY_ID);
    }

    @Test
    public void testTransform_DOM_withSM() {
        System.out.println("Transform using DOM Source;  Security Manager is set:");
        DOMSource domSource = new DOMSource();
        domSource.setSystemId(_xml);

        // DOMSource domSource = new
        // DOMSource(getDocument(Bug6941169Test.class.getResourceAsStream("Bug6941169.xml")));
        Permissions granted = new java.security.Permissions();
        granted.add(new AllPermission());
        System.setSecurityManager(new MySM(granted));
        System.setProperty(DOM_FACTORY_ID, "MyDOMFactoryImpl");
        long start = System.currentTimeMillis();
        try {
            TransformerFactory factory = TransformerFactory.newInstance("com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl",
                    TransformerFactory.class.getClassLoader());
            Transformer t = factory.newTransformer();

            StringWriter result = new StringWriter();
            StreamResult streamResult = new StreamResult(result);
            t.transform(domSource, streamResult);
            System.out.println("Writing to " + result.toString());

        } catch (Exception e) {
            String error = e.getMessage();
            if (error.indexOf("Provider MyDOMFactoryImpl not found") > 0) {
                Assert.fail(e.getMessage());
            } else {
                System.out.println("Default impl is used");
            }

            // System.out.println(e.getMessage());

        } catch (Error e) {
            String error = e.getMessage();
            if (error.indexOf("Provider MyDOMFactoryImpl not found") > 0) {
                Assert.fail(e.getMessage());
            } else {
                System.out.println("Default impl is used");
            }

            // System.out.println(e.getMessage());

        } finally {
            System.clearProperty(DOM_FACTORY_ID);
            System.setSecurityManager(null);
        }
        long end = System.currentTimeMillis();
        double elapsedTime = ((end - start));
        System.out.println("Time elapsed: " + elapsedTime);
        System.clearProperty(DOM_FACTORY_ID);
    }

    @Test
    public void testXPath_DOM_withoutServiceMech() {
        final String XPATH_EXPRESSION = "/fooTest";
        System.out.println("Evaluate DOM Source;  Service mechnism is turned off;  Default DOM Impl should be used:");
        Document doc = getDocument(Bug6941169Test.class.getResourceAsStream("Bug6941169.xml"));
        System.setProperty(DOM_FACTORY_ID, "MyDOMFactoryImpl");
        long start = System.currentTimeMillis();
        try {
            XPathFactory xPathFactory = XPathFactory.newInstance();
            xPathFactory.setFeature(ORACLE_FEATURE_SERVICE_MECHANISM, false);

            XPath xPath = xPathFactory.newXPath();

            String xPathResult = xPath.evaluate(XPATH_EXPRESSION, doc);

        } catch (Exception e) {
            // e.printStackTrace();
            String error = e.getMessage();
            if (error.indexOf("MyDOMFactoryImpl not found") > 0) {
                Assert.fail(e.getMessage());
            } else {
                System.out.println("Default impl is used");
            }

            // System.out.println(e.getMessage());

        } catch (Error e) {
            // e.printStackTrace();
            String error = e.getMessage();
            if (error.indexOf("MyDOMFactoryImpl not found") > 0) {
                Assert.fail(e.getMessage());
            } else {
                System.out.println("Default impl is used");
            }

            // System.out.println(e.getMessage());

        }

        long end = System.currentTimeMillis();
        double elapsedTime = ((end - start));
        System.out.println("Time elapsed: " + elapsedTime);
        System.clearProperty(DOM_FACTORY_ID);
    }

    @Test
    public void testXPath_DOM_withServiceMech() {
        final String XPATH_EXPRESSION = "/fooTest";
        System.out.println("Evaluate DOM Source;  Service mechnism is on by default;  It would try to use MyDOMFactoryImpl:");
        InputStream input = getClass().getResourceAsStream("Bug6941169.xml");
        InputSource source = new InputSource(input);
        System.setProperty(DOM_FACTORY_ID, "MyDOMFactoryImpl");
        long start = System.currentTimeMillis();
        try {
            XPathFactory xPathFactory = XPathFactory.newInstance();

            XPath xPath = xPathFactory.newXPath();

            String xPathResult = xPath.evaluate(XPATH_EXPRESSION, source);
            Assert.fail("User impl MyDOMFactoryImpl should be used.");

        } catch (Exception e) {
            // e.printStackTrace();
            String error = e.getMessage();
            if (error.indexOf("MyDOMFactoryImpl not found") > 0) {
                System.out.println("Tried to locate MyDOMFactoryImpl");
            } else {
                Assert.fail(e.getMessage());

            }

            // System.out.println(e.getMessage());

        } catch (Error e) {
            // e.printStackTrace();
            String error = e.getMessage();
            if (error.indexOf("MyDOMFactoryImpl not found") > 0) {
                System.out.println("Tried to locate MyDOMFactoryImpl");
            } else {
                Assert.fail(e.getMessage());

            }

            // System.out.println(e.getMessage());

        }

        long end = System.currentTimeMillis();
        double elapsedTime = ((end - start));
        System.out.println("Time elapsed: " + elapsedTime);
        System.clearProperty(DOM_FACTORY_ID);
    }

    @Test
    public void testXPath_DOM_withSM() {
        final String XPATH_EXPRESSION = "/fooTest";
        System.out.println("Evaluate DOM Source;  Security Manager is set:");
        Permissions granted = new java.security.Permissions();
        granted.add(new AllPermission());
        System.setSecurityManager(new MySM(granted));
        InputStream input = getClass().getResourceAsStream("Bug6941169.xml");
        InputSource source = new InputSource(input);
        System.setProperty(DOM_FACTORY_ID, "MyDOMFactoryImpl");
        long start = System.currentTimeMillis();
        try {
            XPathFactory xPathFactory = XPathFactory.newInstance("http://java.sun.com/jaxp/xpath/dom",
                    "com.sun.org.apache.xpath.internal.jaxp.XPathFactoryImpl", null);

            XPath xPath = xPathFactory.newXPath();

            String xPathResult = xPath.evaluate(XPATH_EXPRESSION, source);
            System.out.println("Use default impl");
        } catch (Exception e) {
            // e.printStackTrace();
            String error = e.getMessage();
            if (error.indexOf("MyDOMFactoryImpl not found") > 0) {
                Assert.fail(e.getMessage());
            } else {
                System.out.println("Default impl should be used");
            }

            // System.out.println(e.getMessage());

        } catch (Error e) {
            // e.printStackTrace();
            String error = e.getMessage();
            if (error.indexOf("MyDOMFactoryImpl not found") > 0) {
                Assert.fail(e.getMessage());
            } else {
                System.out.println("Default impl should be used");
            }

            // System.out.println(e.getMessage());

        } finally {
            System.clearProperty(DOM_FACTORY_ID);
            System.setSecurityManager(null);
        }
        long end = System.currentTimeMillis();
        double elapsedTime = ((end - start));
        System.out.println("Time elapsed: " + elapsedTime);
        System.clearProperty(DOM_FACTORY_ID);
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

    class MySM extends SecurityManager {
        Permissions granted;

        public MySM(Permissions perms) {
            granted = perms;
        }

        @Override
        public void checkPermission(Permission perm) {
            if (granted.implies(perm)) {
                return;
            }
            super.checkPermission(perm);
        }

    }

}
