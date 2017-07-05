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
package test.auctionportal;

import static com.sun.org.apache.xerces.internal.impl.Constants.SP_ENTITY_EXPANSION_LIMIT;
import static com.sun.org.apache.xerces.internal.impl.Constants.SP_MAX_OCCUR_LIMIT;
import static com.sun.org.apache.xerces.internal.jaxp.JAXPConstants.JAXP_SCHEMA_LANGUAGE;
import static com.sun.org.apache.xerces.internal.jaxp.JAXPConstants.JAXP_SCHEMA_SOURCE;
import static org.testng.Assert.assertTrue;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import static jaxp.library.JAXPTestUtilities.compareDocumentWithGold;
import static jaxp.library.JAXPTestUtilities.failCleanup;
import static jaxp.library.JAXPTestUtilities.failUnexpected;
import static org.testng.Assert.assertFalse;

import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import static test.auctionportal.HiBidConstants.CLASS_DIR;
import static test.auctionportal.HiBidConstants.GOLDEN_DIR;
import static test.auctionportal.HiBidConstants.XML_DIR;

/**
 * This is a test class for the Auction portal HiBid.com.
 */
public class AuctionItemRepository {
    /**
     * XML file for parsing.
     */
    private final static String ENTITY_XML = XML_DIR + "entity.xml";

    /**
     * Feature name.
     */
    private final static String FEATURE_NAME = "http://xml.org/sax/features/namespace-prefixes";

    /**
     * Setting the EntityExpansion Limit to 128000 and checks if the XML
     * document that has more than two levels of entity expansion is parsed or
     * not. Previous system property was changed to jdk.xml.entityExpansionLimit
     * see http://docs.oracle.com/javase/tutorial/jaxp/limits/limits.html.
     */
    @Test
    public void testEntityExpansionSAXPos() {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            // Secure processing will limit XML processing to conform to
            // implementation limits.
            factory.setFeature(FEATURE_SECURE_PROCESSING, true);
            // Set entityExpansionLimit as 2 should expect fatalError
            System.setProperty(SP_ENTITY_EXPANSION_LIMIT, String.valueOf(128000));
            SAXParser parser = factory.newSAXParser();

            MyErrorHandler fatalHandler = new MyErrorHandler();
            parser.parse(new File(ENTITY_XML), fatalHandler);
            assertFalse(fatalHandler.isAnyError());
        } catch (ParserConfigurationException | SAXException | IOException e) {
            failUnexpected(e);
        }
    }
    /**
     * Setting the EntityExpansion Limit to 2 and checks if the XML
     * document that has more than two levels of entity expansion is parsed or
     * not. Previous system property was changed to jdk.xml.entityExpansionLimit
     * see http://docs.oracle.com/javase/tutorial/jaxp/limits/limits.html.
     */
    @Test(expectedExceptions = SAXParseException.class)
    public void testEntityExpansionSAXNeg() throws SAXParseException {
        //
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            // Secure processing will limit XML processing to conform to
            // implementation limits.
            factory.setFeature(FEATURE_SECURE_PROCESSING, true);
            // Set entityExpansionLimit as 2 should expect SAXParseException
            System.setProperty(SP_ENTITY_EXPANSION_LIMIT, String.valueOf(2));
            SAXParser parser = factory.newSAXParser();

            MyErrorHandler fatalHandler = new MyErrorHandler();
            parser.parse(new File(ENTITY_XML), fatalHandler);
        } catch (SAXParseException e) {
            throw e;
        } catch (ParserConfigurationException | SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testing set MaxOccursLimit to 10000 in the secure processing enabled for
     * SAXParserFactory.
     */
    @Test
    public void testMaxOccurLimitPos() {
        String schema_file = XML_DIR + "toys.xsd";
        String xml_file = XML_DIR + "toys.xml";

        try (InputStream is = new FileInputStream(xml_file)) {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setValidating(true);
            factory.setFeature(FEATURE_SECURE_PROCESSING, true);
            System.setProperty(SP_MAX_OCCUR_LIMIT, String.valueOf(10000));
            SAXParser parser = factory.newSAXParser();
            parser.setProperty(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA_NS_URI);
            parser.setProperty(JAXP_SCHEMA_SOURCE, new File(schema_file));
            MyErrorHandler eh = new MyErrorHandler();
            parser.parse(is, eh);
            assertFalse(eh.isAnyError());
        } catch (ParserConfigurationException | SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Use a DocumentBuilder to create a DOM object and see if Secure Processing
     * feature affects the entity expansion.
     */
    @Test
    public void testEntityExpansionDOMPos()  {
        try {
            DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
            dfactory.setFeature(FEATURE_SECURE_PROCESSING, true);
            System.setProperty(SP_ENTITY_EXPANSION_LIMIT, String.valueOf(10000));
            DocumentBuilder dBuilder = dfactory.newDocumentBuilder();
            MyErrorHandler eh = new MyErrorHandler();
            dBuilder.setErrorHandler(eh);
            dBuilder.parse(ENTITY_XML);
            assertFalse(eh.isAnyError());
        } catch (ParserConfigurationException | IOException | SAXException e) {
            failUnexpected(e);
        }
    }

    /**
     * Use a DocumentBuilder to create a DOM object and see how does the Secure
     * Processing feature and entityExpansionLimit value affects output.
     * Negative test that when entityExpansionLimit is too small.
     */
    @Test(expectedExceptions = SAXParseException.class)
    public void testEntityExpansionDOMNeg() throws SAXParseException {
        try {
            DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
            dfactory.setFeature(FEATURE_SECURE_PROCESSING, true);
            System.setProperty(SP_ENTITY_EXPANSION_LIMIT, String.valueOf(2));
            DocumentBuilder dBuilder = dfactory.newDocumentBuilder();
            MyErrorHandler eh = new MyErrorHandler();
            dBuilder.setErrorHandler(eh);
            dBuilder.parse(ENTITY_XML);
        } catch (SAXParseException e) {
            throw e;
        } catch (ParserConfigurationException | IOException | SAXException e) {
            failUnexpected(e);
        }
    }

    /**
     * Test xi:include with a SAXParserFactory.
     */
    @Test
    public void testXIncludeSAXPos() {
        String resultFile = CLASS_DIR + "doc_xinclude.out";
        String goldFile = GOLDEN_DIR + "doc_xincludeGold.xml";
        String xmlFile = XML_DIR + "doc_xinclude.xml";

        try {
            try(FileOutputStream fos = new FileOutputStream(resultFile)) {
                XInclHandler xh = new XInclHandler(fos, null);
                SAXParserFactory spf = SAXParserFactory.newInstance();
                spf.setNamespaceAware(true);
                spf.setXIncludeAware(true);
                spf.setFeature(FEATURE_NAME, true);
                spf.newSAXParser().parse(new File(xmlFile), xh);
            }
            assertTrue(compareDocumentWithGold(goldFile, resultFile));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            failUnexpected(e);
        } finally {
            try {
                Path resultPath = Paths.get(resultFile);
                if (Files.exists(resultPath)) {
                    Files.delete(resultPath);
                }
            } catch (IOException ex) {
                failCleanup(ex, resultFile);
            }
        }
    }

    /**
     * Test the simple case of including a document using xi:include using a
     * DocumentBuilder.
     */
    @Test
    public void testXIncludeDOMPos() {
        String resultFile = CLASS_DIR + "doc_xincludeDOM.out";
        String goldFile = GOLDEN_DIR + "doc_xincludeGold.xml";
        String xmlFile = XML_DIR + "doc_xinclude.xml";
        try {
            try (FileOutputStream fos = new FileOutputStream(resultFile)) {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setXIncludeAware(true);
                dbf.setNamespaceAware(true);

                Document doc = dbf.newDocumentBuilder().parse(new File(xmlFile));
                doc.setXmlStandalone(true);

                TransformerFactory.newInstance().newTransformer().
                        transform(new DOMSource(doc), new StreamResult(fos));
            }
            assertTrue(compareDocumentWithGold(goldFile, resultFile));
        } catch (ParserConfigurationException | SAXException | IOException
                | TransformerException e) {
            failUnexpected(e);
        } finally {
            try {
                Path resultPath = Paths.get(resultFile);
                if (Files.exists(resultPath)) {
                    Files.delete(resultPath);
                }
            } catch (IOException ex) {
                failCleanup(ex, resultFile);
            }
        }
    }

    /**
     * Test the simple case of including a document using xi:include within a
     * xi:fallback using a DocumentBuilder.
     */
    @Test
    public void testXIncludeFallbackDOMPos() {
        String resultFile = CLASS_DIR + "doc_fallbackDOM.out";
        String goldFile = GOLDEN_DIR + "doc_fallbackGold.xml";
        String xmlFile = XML_DIR + "doc_fallback.xml";
        try{
            try (FileOutputStream fos = new FileOutputStream(resultFile)) {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setXIncludeAware(true);
                dbf.setNamespaceAware(true);

                Document doc = dbf.newDocumentBuilder().parse(new File(xmlFile));
                doc.setXmlStandalone(true);
                TransformerFactory.newInstance().newTransformer()
                        .transform(new DOMSource(doc), new StreamResult(fos));
            }
            assertTrue(compareDocumentWithGold(goldFile, resultFile));
        } catch (ParserConfigurationException | SAXException | IOException
                | TransformerException e) {
            failUnexpected(e);
        } finally {
            try {
                Path resultPath = Paths.get(resultFile);
                if (Files.exists(resultPath)) {
                    Files.delete(resultPath);
                }
            } catch (IOException ex) {
                failCleanup(ex, resultFile);
            }
        }
    }

    /**
     * Test for xi:fallback where the fall back text is parsed as text. This
     * test uses a nested xi:include for the fallback test.
     */
    @Test
    public void testXIncludeFallbackTextPos() {
        String resultFile = CLASS_DIR + "doc_fallback_text.out";
        String goldFile = GOLDEN_DIR + "doc_fallback_textGold.xml";
        String xmlFile = XML_DIR + "doc_fallback_text.xml";

        try{
            try (FileOutputStream fos = new FileOutputStream(resultFile)) {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setXIncludeAware(true);
                dbf.setNamespaceAware(true);

                Document doc = dbf.newDocumentBuilder().parse(new File(xmlFile));
                doc.setXmlStandalone(true);
                TransformerFactory.newInstance().newTransformer()
                        .transform(new DOMSource(doc), new StreamResult(fos));
            }
            assertTrue(compareDocumentWithGold(goldFile, resultFile));
        } catch (ParserConfigurationException | SAXException | IOException
                | TransformerException e) {
            failUnexpected(e);
        } finally {
            try {
                Path resultPath = Paths.get(resultFile);
                if (Files.exists(resultPath)) {
                    Files.delete(resultPath);
                }
            } catch (IOException ex) {
                failCleanup(ex, resultFile);
            }
        }
    }

    /**
     * Test the XPointer element() framework with XInclude.
     */
    @Test
    public void testXpointerElementPos() {
        String resultFile = CLASS_DIR + "doc_xpointer_element.out";
        String goldFile = GOLDEN_DIR + "doc_xpointerGold.xml";
        String xmlFile = XML_DIR + "doc_xpointer_element.xml";

        try{
            try (FileOutputStream fos = new FileOutputStream(resultFile)) {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setXIncludeAware(true);
                dbf.setNamespaceAware(true);

                DocumentBuilder db = dbf.newDocumentBuilder();

                TransformerFactory.newInstance().newTransformer()
                        .transform(new DOMSource(db.parse(new File(xmlFile))),
                                new StreamResult(fos));
            }
            assertTrue(compareDocumentWithGold(goldFile, resultFile));
        } catch (ParserConfigurationException | SAXException | IOException
                | TransformerException e) {
            failUnexpected(e);
        } finally {
            try {
                Path resultPath = Paths.get(resultFile);
                if (Files.exists(resultPath)) {
                    Files.delete(resultPath);
                }
            } catch (IOException ex) {
                failCleanup(ex, resultFile);
            }
        }
    }

    /**
     * Test the XPointer framework with a SAX object.
     */
    @Test
    public void testXPointerPos() {
        String resultFile = CLASS_DIR + "doc_xpointer.out";
        String goldFile = GOLDEN_DIR + "doc_xpointerGold.xml";
        String xmlFile = XML_DIR + "doc_xpointer.xml";

        try{
            try (FileOutputStream fos = new FileOutputStream(resultFile)) {
                SAXParserFactory spf = SAXParserFactory.newInstance();
                spf.setNamespaceAware(true);
                spf.setXIncludeAware(true);
                spf.setFeature(FEATURE_NAME, true);
                // parse the file
                spf.newSAXParser().parse(new File(xmlFile), new XInclHandler(fos, null));
            }
            assertTrue(compareDocumentWithGold(goldFile, resultFile));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            failUnexpected(e);
        } finally {
            try {
                Path resultPath = Paths.get(resultFile);
                if (Files.exists(resultPath)) {
                    Files.delete(resultPath);
                }
            } catch (IOException ex) {
                failCleanup(ex, resultFile);
            }
        }
    }

    /**
     * Test if xi:include may reference the doc containing the include if the
     * parse type is text.
     */
    @Test
    public void testXIncludeLoopPos() {
        String resultFile = CLASS_DIR + "doc_xinc_loops.out";
        String goldFile = GOLDEN_DIR + "doc_xinc_loopGold.xml";
        String xmlFile = XML_DIR + "doc_xinc_loops.xml";

        try{
            try (FileOutputStream fos = new FileOutputStream(resultFile)) {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setXIncludeAware(true);
                dbf.setNamespaceAware(true);
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document doc = db.parse(new File(xmlFile));
                doc.normalizeDocument();
                doc.setXmlStandalone(true);

                TransformerFactory.newInstance().newTransformer()
                        .transform(new DOMSource(doc), new StreamResult(fos));
            }
            assertTrue(compareDocumentWithGold(goldFile, resultFile));
        } catch (ParserConfigurationException | SAXException | IOException
                | TransformerException e) {
            failUnexpected(e);
        } finally {
            try {
                Path resultPath = Paths.get(resultFile);
                if (Files.exists(resultPath)) {
                    Files.delete(resultPath);
                }
            } catch (IOException ex) {
                failCleanup(ex, resultFile);
            }
        }
    }

    /**
     * Test if two non nested xi:include elements can include the same document
     * with an xi:include statement.
     */
    @Test
    public void testXIncludeNestedPos() {
        String resultFile = CLASS_DIR + "schedule.out";
        String goldFile = GOLDEN_DIR + "scheduleGold.xml";
        String xmlFile = XML_DIR + "schedule.xml";

        try{
            try (FileOutputStream fos = new FileOutputStream(resultFile)) {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setXIncludeAware(true);
                dbf.setNamespaceAware(true);

                Document doc = dbf.newDocumentBuilder().parse(new File(xmlFile));
                doc.setXmlStandalone(true);
                TransformerFactory.newInstance().newTransformer()
                        .transform(new DOMSource(doc), new StreamResult(fos));
            }
            assertTrue(compareDocumentWithGold(goldFile, resultFile));
        } catch (ParserConfigurationException | SAXException | IOException
                | TransformerException e) {
            failUnexpected(e);
        } finally {
            try {
                Path resultPath = Paths.get(resultFile);
                if (Files.exists(resultPath)) {
                    Files.delete(resultPath);
                }
            } catch (IOException ex) {
                failCleanup(ex, resultFile);
            }
        }
    }
}
