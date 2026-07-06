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
package test.auctionportal;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;

import static javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static test.auctionportal.HiBidConstants.GOLDEN_DIR;
import static test.auctionportal.HiBidConstants.JAXP_SCHEMA_LANGUAGE;
import static test.auctionportal.HiBidConstants.JAXP_SCHEMA_SOURCE;
import static test.auctionportal.HiBidConstants.SP_ENTITY_EXPANSION_LIMIT;
import static test.auctionportal.HiBidConstants.SP_MAX_OCCUR_LIMIT;
import static test.auctionportal.HiBidConstants.XML_DIR;

/**
 * This is a test class for the Auction portal HiBid.com.
 */
/*
 * @test
 * @library /javax/xml/jaxp/libs
 * @run junit/othervm test.auctionportal.AuctionItemRepository
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
     *
     */
    @Test
    public void testEntityExpansionSAXPos() throws Exception {
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
    }
    /**
     * Setting the EntityExpansion Limit to 2 and checks if the XML
     * document that has more than two levels of entity expansion is parsed or
     * not. Previous system property was changed to jdk.xml.entityExpansionLimit
     * see http://docs.oracle.com/javase/tutorial/jaxp/limits/limits.html.
     *
     */
    @Test
    public void testEntityExpansionSAXNeg() throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        // Secure processing will limit XML processing to conform to
        // implementation limits.
        factory.setFeature(FEATURE_SECURE_PROCESSING, true);
        // Set entityExpansionLimit as 2 should expect SAXParseException.
        System.setProperty(SP_ENTITY_EXPANSION_LIMIT, String.valueOf(2));

        SAXParser parser = factory.newSAXParser();
        MyErrorHandler fatalHandler = new MyErrorHandler();
        assertThrows(SAXParseException.class, () -> parser.parse(new File(ENTITY_XML), fatalHandler));
    }

    /**
     * Testing set MaxOccursLimit to 10000 in the secure processing enabled for
     * SAXParserFactory.
     *
     */
    @Test
    public void testMaxOccurLimitPos() throws Exception {
        String schema_file = XML_DIR + "toys.xsd";
        String xml_file = XML_DIR + "toys.xml";
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setValidating(true);
        factory.setFeature(FEATURE_SECURE_PROCESSING, true);
        System.setProperty(SP_MAX_OCCUR_LIMIT, String.valueOf(10000));
        SAXParser parser = factory.newSAXParser();
        parser.setProperty(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA_NS_URI);
        parser.setProperty(JAXP_SCHEMA_SOURCE, new File(schema_file));
        try (InputStream is = new FileInputStream(xml_file)) {
            MyErrorHandler eh = new MyErrorHandler();
            parser.parse(is, eh);
            assertFalse(eh.isAnyError());
        }
    }

    /**
     * Use a DocumentBuilder to create a DOM object and see if Secure Processing
     * feature affects the entity expansion.
     *
     */
    @Test
    public void testEntityExpansionDOMPos() throws Exception  {
        DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
        dfactory.setFeature(FEATURE_SECURE_PROCESSING, true);
        System.setProperty(SP_ENTITY_EXPANSION_LIMIT, String.valueOf(10000));
        DocumentBuilder dBuilder = dfactory.newDocumentBuilder();
        MyErrorHandler eh = new MyErrorHandler();
        dBuilder.setErrorHandler(eh);
        dBuilder.parse(ENTITY_XML);
        assertFalse(eh.isAnyError());
    }

    /**
     * Use a DocumentBuilder to create a DOM object and see how does the Secure
     * Processing feature and entityExpansionLimit value affects output.
     * Negative test that when entityExpansionLimit is too small.
     *
     */
    @Test
    public void testEntityExpansionDOMNeg() throws Exception {
        DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();
        dfactory.setFeature(FEATURE_SECURE_PROCESSING, true);
        System.setProperty(SP_ENTITY_EXPANSION_LIMIT, String.valueOf(2));
        DocumentBuilder dBuilder = dfactory.newDocumentBuilder();
        MyErrorHandler eh = new MyErrorHandler();
        dBuilder.setErrorHandler(eh);
        assertThrows(SAXParseException.class, () -> dBuilder.parse(ENTITY_XML));
    }

    /**
     * Test xi:include with a SAXParserFactory.
     *
     */
    @Test
    public void testXIncludeSAXPos() throws Exception {
        String resultFile = "doc_xinclude.out";
        String goldFile = GOLDEN_DIR + "doc_xincludeGold.xml";
        String xmlFile = XML_DIR + "doc_xinclude.xml";

        try(FileOutputStream fos = new FileOutputStream(resultFile)) {
            XInclHandler xh = new XInclHandler(fos, null);
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            spf.setXIncludeAware(true);
            spf.setFeature(FEATURE_NAME, true);
            spf.newSAXParser().parse(new File(xmlFile), xh);
        }
        assertTrue(compareDocumentWithGold(goldFile, resultFile));
    }

    /**
     * Test the simple case of including a document using xi:include using a
     * DocumentBuilder.
     *
     */
    @Test
    public void testXIncludeDOMPos() throws Exception {
        String resultFile = "doc_xincludeDOM.out";
        String goldFile = GOLDEN_DIR + "doc_xincludeGold.xml";
        String xmlFile = XML_DIR + "doc_xinclude.xml";
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
    }

    /**
     * Test the simple case of including a document using xi:include within a
     * xi:fallback using a DocumentBuilder.
     *
     */
    @Test
    public void testXIncludeFallbackDOMPos() throws Exception {
        String resultFile = "doc_fallbackDOM.out";
        String goldFile = GOLDEN_DIR + "doc_fallbackGold.xml";
        String xmlFile = XML_DIR + "doc_fallback.xml";
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
    }

    /**
     * Test for xi:fallback where the fall back text is parsed as text. This
     * test uses a nested xi:include for the fallback test.
     *
     */
    @Test
    public void testXIncludeFallbackTextPos() throws Exception {
        String resultFile = "doc_fallback_text.out";
        String goldFile = GOLDEN_DIR + "doc_fallback_textGold.xml";
        String xmlFile = XML_DIR + "doc_fallback_text.xml";
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
    }

    /**
     * Test the XPointer element() framework with XInclude.
     *
     */
    @Test
    public void testXpointerElementPos() throws Exception {
        String resultFile = "doc_xpointer_element.out";
        String goldFile = GOLDEN_DIR + "doc_xpointerGold.xml";
        String xmlFile = XML_DIR + "doc_xpointer_element.xml";
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
    }

    /**
     * Test the XPointer framework with a SAX object.
     *
     */
    @Test
    public void testXPointerPos() throws Exception {
        String resultFile = "doc_xpointer.out";
        String goldFile = GOLDEN_DIR + "doc_xpointerGold.xml";
        String xmlFile = XML_DIR + "doc_xpointer.xml";

        try (FileOutputStream fos = new FileOutputStream(resultFile)) {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            spf.setXIncludeAware(true);
            spf.setFeature(FEATURE_NAME, true);
            // parse the file
            spf.newSAXParser().parse(new File(xmlFile), new XInclHandler(fos, null));
        }
        assertTrue(compareDocumentWithGold(goldFile, resultFile));
    }

    /**
     * Test if xi:include may reference the doc containing the include if the
     * parse type is text.
     *
     */
    @Test
    public void testXIncludeLoopPos() throws Exception {
        String resultFile = "doc_xinc_loops.out";
        String goldFile = GOLDEN_DIR + "doc_xinc_loopGold.xml";
        String xmlFile = XML_DIR + "doc_xinc_loops.xml";

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
    }

    /**
     * Test if two non nested xi:include elements can include the same document
     * with an xi:include statement.
     *
     */
    @Test
    public void testXIncludeNestedPos() throws Exception {
        String resultFile = "schedule.out";
        String goldFile = GOLDEN_DIR + "scheduleGold.xml";
        String xmlFile = XML_DIR + "schedule.xml";

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
    }

    public static boolean compareDocumentWithGold(String goldfile, String resultFile)
            throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setCoalescing(true);
        factory.setIgnoringElementContentWhitespace(true);
        factory.setIgnoringComments(true);
        DocumentBuilder db = factory.newDocumentBuilder();

        Document goldD = db.parse(Paths.get(goldfile).toFile());
        goldD.normalizeDocument();
        Document resultD = db.parse(Paths.get(resultFile).toFile());
        resultD.normalizeDocument();
        return goldD.isEqualNode(resultD);
    }
}
