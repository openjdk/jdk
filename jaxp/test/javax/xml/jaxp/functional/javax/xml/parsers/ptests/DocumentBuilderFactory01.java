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

package javax.xml.parsers.ptests;

import static jaxp.library.JAXPTestUtilities.FILE_SEP;
import static jaxp.library.JAXPTestUtilities.failUnexpected;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * This checks the methods of DocumentBuilderFactoryImpl
 */
public class DocumentBuilderFactory01 {
    /**
     * Testcase to test the default functionality of schema support method.
     */
    @Test
    public void testCheckSchemaSupport1() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setValidating(true);
            dbf.setNamespaceAware(true);
            dbf.setAttribute("http://java.sun.com/xml/jaxp/properties/schemaLanguage", "http://www.w3.org/2001/XMLSchema");
            MyErrorHandler eh = MyErrorHandler.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            db.setErrorHandler(eh);
            Document doc = db.parse(new File(TestUtils.XML_DIR, "test.xml"));
            assertFalse(eh.errorOccured);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase to test the default functionality of schema support method. In
     * this case the schema source property is set.
     */
    @Test
    public void testCheckSchemaSupport2() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setValidating(true);
            dbf.setNamespaceAware(true);
            dbf.setAttribute("http://java.sun.com/xml/jaxp/properties/schemaLanguage", "http://www.w3.org/2001/XMLSchema");
            dbf.setAttribute("http://java.sun.com/xml/jaxp/properties/schemaSource", new InputSource(new FileInputStream(
                    new File(TestUtils.XML_DIR, "test.xsd"))));
            MyErrorHandler eh = MyErrorHandler.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            db.setErrorHandler(eh);
            Document doc = db.parse(new File(TestUtils.XML_DIR, "test1.xml"));
            assertFalse(eh.errorOccured);
        } catch (IllegalArgumentException | ParserConfigurationException | SAXException | IOException e) {
            failUnexpected(e);
        }

    }

    /**
     * Testcase to test the default functionality of schema support method. In
     * this case the schema source property is set.
     */
    @Test
    public void testCheckSchemaSupport3() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            spf.setValidating(true);
            spf.setNamespaceAware(true);
            SAXParser sp = spf.newSAXParser();
            sp.setProperty("http://java.sun.com/xml/jaxp/properties/schemaLanguage", "http://www.w3.org/2001/XMLSchema");
            sp.setProperty("http://java.sun.com/xml/jaxp/properties/schemaSource",
                    new InputSource(new FileInputStream(new File(TestUtils.XML_DIR, "test.xsd"))));
            DefaultHandler dh = new DefaultHandler();
            sp.parse(new File(TestUtils.XML_DIR, "test1.xml"), dh);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase to test the default functionality of newInstance method. To test
     * the isCoalescing method and setCoalescing This checks to see if the CDATA
     * and text nodes got combined In that case it will print "&lt;xml&gt;This
     * is not parsed&lt;/xml&gt; yet".
     */
    @Test
    public void testCheckDocumentBuilderFactory02() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setCoalescing(true);
            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            Document doc = docBuilder.parse(new File(TestUtils.XML_DIR, "DocumentBuilderFactory01.xml"));
            Element e = (Element) doc.getElementsByTagName("html").item(0);
            NodeList nl = e.getChildNodes();
            assertEquals(nl.item(0).getNodeValue().trim(), "<xml>This is not parsed</xml> yet");
        } catch (IOException | SAXException | ParserConfigurationException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase to test the isIgnoringComments. By default it is false.
     */
    @Test
    public void testCheckDocumentBuilderFactory03() {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        assertFalse(dbf.isIgnoringComments());
    }

    /**
     * Testcase to test the isValidating. By default it is false, set it to true
     * and then use a document which is not valid. It should throw a warning or
     * an error at least. The test passes in case retval 0 is set in the error
     * method .
     */
    @Test
    public void testCheckDocumentBuilderFactory04() {
        try {
            MyErrorHandler eh = MyErrorHandler.newInstance();
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setValidating(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            db.setErrorHandler(eh);
            Document doc = db.parse(new File(TestUtils.XML_DIR, "DocumentBuilderFactory05.xml"));
            assertTrue(eh.errorOccured);
        } catch (ParserConfigurationException | IOException | SAXException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase to test the setValidating. By default it is false, use a
     * document which is not valid. It should not throw a warning or an error.
     * The test passes in case the retval equals 1 .
     */
    @Test
    public void testCheckDocumentBuilderFactory16() {
        try {
            MyErrorHandler eh = MyErrorHandler.newInstance();
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            db.setErrorHandler(eh);
            Document doc = db.parse(new File(TestUtils.XML_DIR, "DocumentBuilderFactory05.xml"));
            assertFalse(eh.errorOccured);
        } catch (ParserConfigurationException | IOException | SAXException e) {
            failUnexpected(e);
        }

    }

    /**
     * Testcase to test the setValidating. By default it is false, use a
     * document which is valid. It should not throw a warning or an error. The
     * test passes in case the retval equals 1.
     */
    @Test
    public void testCheckDocumentBuilderFactory17() {
        try {
            MyErrorHandler eh = MyErrorHandler.newInstance();
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            db.setErrorHandler(eh);
            Document doc = db.parse(new File(TestUtils.XML_DIR, "DocumentBuilderFactory04.xml"));
            assertFalse(eh.errorOccured);
        } catch (ParserConfigurationException | IOException | SAXException e) {
            failUnexpected(e);
        }

    }

    /**
     * To test the isExpandEntityReferences. By default it is true.
     */
    @Test
    public void testCheckDocumentBuilderFactory05() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            Document doc = docBuilder.parse(new FileInputStream(new File(TestUtils.XML_DIR, "DocumentBuilderFactory02.xml")));
            Element e = (Element) doc.getElementsByTagName("title").item(0);
            NodeList nl = e.getChildNodes();
            assertTrue(dbf.isExpandEntityReferences());
            assertEquals(nl.item(0).getNodeValue().trim().charAt(0), 'W');
        } catch (ParserConfigurationException | IOException | SAXException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase to test the default functionality of setValidating method. The
     * xml file has a DTD which has namespaces defined. The parser takes care to
     * check if the namespaces using elements and defined attributes are there
     * or not.
     */
    @Test
    public void testCheckDocumentBuilderFactory06() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setValidating(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            MyErrorHandler eh = MyErrorHandler.newInstance();
            db.setErrorHandler(eh);
            Document doc = db.parse(new File(TestUtils.XML_DIR, "DocumentBuilderFactory04.xml"));
            assertTrue(doc instanceof Document);
            assertFalse(eh.errorOccured);
        } catch (ParserConfigurationException | IOException | SAXException e) {
            failUnexpected(e);
        }

    }

    /**
     * Testcase to test the setExpandEntityReferences.
     */
    @Test
    public void testCheckDocumentBuilderFactory07() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setExpandEntityReferences(true);
            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            Document doc = docBuilder.parse(new FileInputStream(new File(TestUtils.XML_DIR, "DocumentBuilderFactory02.xml")));
            Element e = (Element) doc.getElementsByTagName("title").item(0);
            NodeList nl = e.getChildNodes();
            assertTrue(dbf.isExpandEntityReferences());
            assertEquals(nl.item(0).getNodeValue().trim().charAt(0), 'W');
        } catch (ParserConfigurationException | IOException | SAXException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase to test the setExpandEntityReferences.
     */
    @Test
    public void testCheckDocumentBuilderFactory08() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setExpandEntityReferences(false);
            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            Document doc = docBuilder.parse(new FileInputStream(new File(TestUtils.XML_DIR, "DocumentBuilderFactory02.xml")));
            Element e = (Element) doc.getElementsByTagName("title").item(0);
            NodeList nl = e.getChildNodes();
            assertNull(nl.item(0).getNodeValue());
        } catch (ParserConfigurationException | IOException | SAXException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase to test the setIgnoringComments. By default it is set to false.
     * explicitly setting it to false, it recognizes the comment which is in
     * Element Node Hence the Element's child node is not null.
     */
    @Test
    public void testCheckDocumentBuilderFactory09() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setIgnoringComments(false);
            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            Document doc = docBuilder.parse(new FileInputStream(new File(TestUtils.XML_DIR, "DocumentBuilderFactory07.xml")));
            Element e = (Element) doc.getElementsByTagName("body").item(0);
            NodeList nl = e.getChildNodes();
            assertNotNull(nl.item(0).getNodeValue());
        } catch (ParserConfigurationException | IOException | SAXException e) {
            failUnexpected(e);
        }

    }

    /**
     * This tests for the parse(InputSource).
     */
    @Test
    public void testCheckDocumentBuilderFactory10() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            Document doc = docBuilder.parse(new InputSource(new BufferedReader(new FileReader(new File(TestUtils.XML_DIR, "DocumentBuilderFactory07.xml")))));
            assertTrue(doc instanceof Document);
        } catch (IllegalArgumentException | ParserConfigurationException | IOException | SAXException e) {
            failUnexpected(e);
        }
    }

    /**
     * This tests for the parse InputStream with SystemID as a second parameter.
     */
    @Test
    public void testCheckDocumentBuilderFactory11() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            Document doc = docBuilder.parse(new FileInputStream(new File(TestUtils.XML_DIR, "dbf10import.xsl")), new File(TestUtils.XML_DIR).toURI()
                    .toASCIIString());
            assertTrue(doc instanceof Document);
        } catch (IllegalArgumentException | ParserConfigurationException | IOException | SAXException e) {
            failUnexpected(e);
        }
    }

    /**
     * This tests for the parse InputStream with empty SystemID as a second
     * parameter.
     */
    @Test
    public void testCheckDocumentBuilderFactory12() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            Document doc = docBuilder.parse(new FileInputStream(new File(TestUtils.XML_DIR, "dbf10import.xsl")), " ");
            assertTrue(doc instanceof Document);
        } catch (IllegalArgumentException | ParserConfigurationException | IOException | SAXException e) {
            failUnexpected(e);
        }
    }

    /**
     * This tests for the parse(uri).
     */
    @Test
    public void testCheckDocumentBuilderFactory13() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            Document doc = docBuilder.parse(new File(TestUtils.XML_DIR + FILE_SEP + "dbf10import.xsl").toURI().toASCIIString());
            assertTrue(doc instanceof Document);
        } catch (IllegalArgumentException | ParserConfigurationException | IOException | SAXException e) {
            failUnexpected(e);
        }
    }

    /**
     * This tests for the parse (uri) with empty string as parameter should
     * throw Sax Exception.
     *
     * @throws SAXException
     *             If any parse errors occur.
     */
    @Test(expectedExceptions = SAXException.class)
    public void testCheckDocumentBuilderFactory14() throws SAXException {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            docBuilder.parse("");
        } catch (ParserConfigurationException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * This tests for the parse (uri) with null uri as parameter should throw
     * IllegalArgumentException.
     *
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testCheckDocumentBuilderFactory15() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            String uri = null;
            docBuilder.parse(uri);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase to test the setIgnoringComments. By default it is set to false,
     * setting this to true, It does not recognize the comment, Here the
     * nodelist has a length 0 because the ignoring comments is true.
     */
    @Test
    public void testCheckIgnoringComments() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setIgnoringComments(true);
            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            Document doc = docBuilder.parse(new FileInputStream(new File(TestUtils.XML_DIR, "DocumentBuilderFactory08.xml")));
            Element e = (Element) doc.getElementsByTagName("body").item(0);
            NodeList nl = e.getChildNodes();
            assertEquals(nl.getLength(), 0);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            failUnexpected(e);
        }

    }

    /**
     * Testcase to test the default behaviour of setIgnoringComments. By default
     * it is set to false, this is similar to case 9 but not setIgnoringComments
     * explicitly, it does not recognize the comment.
     */
    @Test
    public void testCheckIgnoringComments1() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            Document doc = docBuilder.parse(new FileInputStream(new File(TestUtils.XML_DIR, "DocumentBuilderFactory07.xml")));
            Element e = (Element) doc.getElementsByTagName("body").item(0);
            NodeList nl = e.getChildNodes();
            assertFalse(dbf.isIgnoringComments());
            assertNotNull(nl.item(0).getNodeValue());
        } catch (ParserConfigurationException | SAXException | IOException e) {
            failUnexpected(e);
        }
    }
}
