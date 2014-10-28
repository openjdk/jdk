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
package org.xml.sax.ptests;

import java.io.FileInputStream;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import static jaxp.library.JAXPTestUtilities.failUnexpected;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DeclHandler;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.XMLFilterImpl;
import static org.xml.sax.ptests.SAXTestConst.XML_DIR;

/**
 * Class containing the test cases for SAXParser API
 */
public class XMLReaderTest {
    /**
     * XML namespaces.
     */
    private static final String NAMESPACES =
                "http://xml.org/sax/features/namespaces";

    /**
     * XML namespaces prefixes.
     */
    private static final String NAMESPACE_PREFIXES =
                "http://xml.org/sax/features/namespace-prefixes";

    /**
     * A string intern name.
     */
    private static final String STRING_INTERNING =
                "http://xml.org/sax/features/string-interning";

    /**
     * Validation name.
     */
    private static final String VALIDATION =
                "http://xml.org/sax/features/validation";

    /**
     * A general external entities name
     */
    private static final String EXTERNAL_G_ENTITIES =
                "http://xml.org/sax/features/external-general-entities";

    /**
     * A external parameter entities name
     */
    private static final String EXTERNAL_P_ENTITIES =
                "http://xml.org/sax/features/external-parameter-entities";

    /**
     * XML DOM node name.
     */
    private static final String DOM_NODE = "http://xml.org/sax/properties/dom-node";

    /**
     * XML String name.
     */
    private static final String XML_STRING = "http://xml.org/sax/properties/xml-string";

    /**
     * Declare handler name
     */
    private static final String DECL_HANDLER =
                        "http://xml.org/sax/properties/declaration-handler";

    /**
     * Lexical handler name
     */
    private static final String LEXICAL_HANDLER =
                        "http://xml.org/sax/properties/lexical-handler";

    /**
     * According to the SAX2 specs, All XMLReaders are required to recognize the
     * http://xml.org/sax/features/namespaces feature names.
     * This test case is to test this.
     */
    @Test
    public void featureNS01() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();
            assertFalse(xmlReader.getFeature(NAMESPACES));
        } catch (ParserConfigurationException | SAXException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * According to the SAX2 specs, All XMLReaders are required to recognize the
     * http://xml.org/sax/features/namespaces feature names.
     * This test case is to test this.
     */
    @Test
    public void featureNS02() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();
            assertTrue(xmlReader.getFeature(NAMESPACES));
        } catch (ParserConfigurationException | SAXException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * Obtain http://xml.org/sax/features/namespaces feature name after it's
     * just set. Expect it's same as set value.
     */
    @Test
    public void featureNS03() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();
            xmlReader.setFeature(NAMESPACES, true);
            assertTrue(xmlReader.getFeature(NAMESPACES));
            xmlReader.setFeature(NAMESPACES, false);
            assertFalse(xmlReader.getFeature(NAMESPACES));
        } catch (ParserConfigurationException | SAXException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * According to the SAX2 specs, All XMLReaders are required to recognize the
     * http://xml.org/sax/features/namespace-prefixes feature names.
     * This test case is to test this.
     */
    @Test
    public void featureNSP01() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();
            assertTrue(xmlReader.getFeature(NAMESPACE_PREFIXES));

        } catch (ParserConfigurationException | SAXException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * According to the SAX2 specs, All XMLReaders are required to recognize the
     * http://xml.org/sax/features/namespace-prefixes feature names.
     * This test case is to test this.
     */
    @Test
    public void featureNSP02() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();
            assertFalse(xmlReader.getFeature(NAMESPACE_PREFIXES));
        } catch (ParserConfigurationException | SAXException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * Obtain http://xml.org/sax/features/namespaces-prefixes feature name after
     * it's just set. Expect it's same as set value.
     */
    @Test
    public void featureNSP03() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();
            xmlReader.setFeature(NAMESPACE_PREFIXES, true);
            assertTrue(xmlReader.getFeature(NAMESPACE_PREFIXES));
            xmlReader.setFeature(NAMESPACE_PREFIXES, false);
            assertFalse(xmlReader.getFeature(NAMESPACE_PREFIXES));
        } catch (ParserConfigurationException | SAXException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * getFeature returns true if a feature has not been preset when namespace
     * awareness is set.
     */
    @Test
    public void featureSI01() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();
            assertTrue(xmlReader.getFeature(STRING_INTERNING));
        } catch (SAXException | ParserConfigurationException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * getFeature with validation feature name returns the value that
     * setValidation set.
     */
    @Test
    public void featureV01() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            assertFalse(spf.newSAXParser().getXMLReader().getFeature(VALIDATION));
            spf.setValidating(true);
            assertTrue(spf.newSAXParser().getXMLReader().getFeature(VALIDATION));
        } catch (SAXException | ParserConfigurationException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * getFeature returns the value that a feature has been preset as  when
     * namespace awareness is set.
     */
    @Test
    public void featureV02() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();

            xmlReader.setFeature(VALIDATION, true);
            assertTrue(xmlReader.getFeature(VALIDATION));

            xmlReader.setFeature(VALIDATION, false);
            assertFalse(xmlReader.getFeature(VALIDATION));
        } catch (SAXException | ParserConfigurationException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * getFeature returns true if a feature has not been preset when namespace
     * awareness is set.
     */
    @Test
    public void featureEGE01() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();
            assertTrue(xmlReader.getFeature(EXTERNAL_G_ENTITIES));
        } catch (SAXException | ParserConfigurationException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * getFeature returns false if a feature has been preset as false  when
     * namespace awareness is set.
     */
    @Test
    public void featureEGE02() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();
            xmlReader.setFeature(EXTERNAL_G_ENTITIES, false);
            assertFalse(xmlReader.getFeature(EXTERNAL_G_ENTITIES));
        } catch (SAXException | ParserConfigurationException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * getFeature returns true if a feature has not been preset when namespace
     * awareness is set.
     */
    @Test
    public void featureEPE01() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();
            assertTrue(xmlReader.getFeature(EXTERNAL_P_ENTITIES));
        } catch (SAXException | ParserConfigurationException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * getFeature returns false if a feature has been preset as false  when
     * namespace awareness is set.
     */
    @Test
    public void featureEPE02() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            XMLReader xmlReader = spf.newSAXParser().getXMLReader();
            xmlReader.setFeature(EXTERNAL_P_ENTITIES, false);
            assertFalse(xmlReader.getFeature(EXTERNAL_P_ENTITIES));
        } catch (SAXException | ParserConfigurationException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * getFeature with a unknown feature name throws SAXNotRecognizedException.
     * @throws SAXNotRecognizedException If the feature value can't be assigned
     *                                   or retrieved.
     */
    @Test(expectedExceptions = SAXNotRecognizedException.class)
    public void featureNE01() throws SAXNotRecognizedException  {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();
            boolean noMeaningFeature = xmlReader.getFeature("no-meaning-feature");
        } catch(SAXNotRecognizedException ex) {
            throw ex;
        } catch (SAXException | ParserConfigurationException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * No exception expected when set entity resolver as simple entity resolver.
     */
    @Test
    public void entity01() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();
            XMLFilterImpl xmlFilter = new XMLFilterImpl();
            xmlReader.setEntityResolver(xmlFilter);
            assertNotNull(xmlReader.getEntityResolver());
        } catch (SAXException | ParserConfigurationException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * No NPE expected when set entity resolver as null.
     */
    @Test
    public void entity02() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();
            xmlReader.setEntityResolver(null);
        } catch (SAXException | ParserConfigurationException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * No exception expected when set DTD handler as simple DTD handler.
     */
    @Test
    public void dtdhandler01() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();
            XMLFilterImpl xmlFilter = new XMLFilterImpl();
            xmlReader.setDTDHandler(xmlFilter);
            assertNotNull(xmlReader.getDTDHandler());
        } catch (SAXException | ParserConfigurationException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * No NPE expected when set DTD handler as null.
     */
    @Test
    public void dtdhandler02() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();
            xmlReader.setDTDHandler(null);
        } catch (SAXException | ParserConfigurationException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * No exception expected when set content handler as simple content handler.
     */
    @Test
    public void contenthandler01() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();
            XMLFilterImpl xmlFilter = new XMLFilterImpl();
            xmlReader.setContentHandler(xmlFilter);
            assertNotNull(xmlReader.getContentHandler());
        } catch (SAXException | ParserConfigurationException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * No NPE expected when set content handler as null.
     */
    @Test
    public void contenthandler02() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();
            xmlReader.setContentHandler(null);
        } catch (SAXException | ParserConfigurationException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * No exception expected when set content handler as simple error handler.
     */
    @Test
    public void errorhandler01() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();
            xmlReader.setErrorHandler(new XMLFilterImpl());
            assertNotNull(xmlReader.getErrorHandler());
        } catch (SAXException | ParserConfigurationException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * No NPE expected when set error handler as null.
     */
    @Test
    public void errorhandler02() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();
            xmlReader.setErrorHandler(null);
        } catch (SAXException | ParserConfigurationException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * Parse a null input source throw NPE.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void parse01() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();
            xmlReader.parse((InputSource)null);
        } catch (SAXException | ParserConfigurationException | IOException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * Unit test for parse a error-formatted file. SAXException is expected.
     * @throws org.xml.sax.SAXException parsing failed.
     */
    @Test(expectedExceptions = SAXException.class)
    public void parse02() throws SAXException {
        try (FileInputStream fis = new FileInputStream(XML_DIR + "invalid.xml")){
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();
            InputSource is = new InputSource(fis);
            xmlReader.parse(is);
        } catch (ParserConfigurationException | IOException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * Unit test for parse a well-formatted file. No exception is expected.
     */
    @Test
    public void parse03(){
        try (FileInputStream fis = new FileInputStream(XML_DIR + "correct2.xml")) {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();
            InputSource is = new InputSource(fis);
            xmlReader.parse(is);
        } catch (IOException | SAXException | ParserConfigurationException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * Modified by IBM
     * Xerces does not support this feature and it is not mandatory
     * @throws org.xml.sax.SAXNotSupportedException
     */
    @Test(expectedExceptions = SAXNotSupportedException.class)
    public void xrProperty01() throws SAXNotSupportedException  {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();
            xmlReader.getProperty(XML_STRING);
        } catch(SAXNotSupportedException ex) {
            throw ex;
        } catch (SAXException | ParserConfigurationException ex){
            failUnexpected(ex);
        }
    }

    /**
     * SAXNotSupportedException thrown if property name is known but no value
     * assigned to this property.
     * @throws org.xml.sax.SAXNotSupportedException when XMLReader recognizes
     * the property name but cannot determine its value at this time.
     */
    @Test(expectedExceptions = SAXNotSupportedException.class)
    public void xrProperty02() throws SAXNotSupportedException {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();
            assertNull(xmlReader.getProperty(DOM_NODE));
        } catch (SAXNotSupportedException ex) {
            throw ex;
        } catch (SAXException | ParserConfigurationException ex){
            failUnexpected(ex);
        }
    }


    /**
     * XMLReader.getProperty returns null if LEXICAL_HANDLER wasn't set.
     */
    @Test
    public void xrProperty03() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();
            assertNull(xmlReader.getProperty(LEXICAL_HANDLER));
        } catch (SAXException | ParserConfigurationException ex){
            failUnexpected(ex);
        }
    }

    /**
     * XMLReader.getProperty returns null if DECL_HANDLER wasn't set.
     */
    @Test
    public void xrProperty04() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();
            assertNull(xmlReader.getProperty(DECL_HANDLER));
        } catch (SAXException | ParserConfigurationException ex){
            failUnexpected(ex);
        }
    }

    /**
     * XMLReader.setProperty/getProperty for LEXICAL_HANDLER unit test.
     */
    @Test
    public void xrProperty05() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();
            MyLexicalHandler myLexicalHandler = new MyLexicalHandler();
            xmlReader.setProperty(LEXICAL_HANDLER, myLexicalHandler);
            assertNotNull(xmlReader.getProperty(LEXICAL_HANDLER));
        } catch (SAXException | ParserConfigurationException ex){
            failUnexpected(ex);
        }
    }

    /**
     * XMLReader.setProperty/getProperty for DECL_HANDLER unit test.
     */
    @Test
    public void xrProperty06() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            XMLReader xmlReader =  spf.newSAXParser().getXMLReader();
            MyDeclHandler myDeclHandler = new MyDeclHandler();
            xmlReader.setProperty(DECL_HANDLER, myDeclHandler);
            assertNotNull(xmlReader.getProperty(DECL_HANDLER));
        } catch (ParserConfigurationException | SAXException ex){
            failUnexpected(ex);
        }
    }
}

/**
 * Simple LexicalHandler that skips every lexical event.
 */
class MyLexicalHandler implements LexicalHandler {
    /**
     * Report an XML comment anywhere in the document.
     *
     * @param ch An array holding the characters in the comment.
     * @param start The starting position in the array.
     * @param length The number of characters to use from the array.
     */
    @Override
    public void comment(char[] ch, int start, int length) {
    }

    /**
     * Report the end of a CDATA section.
     */
    @Override
    public void endCDATA() {
    }

    /**
     * Report the end of DTD declarations.
     */
    @Override
    public void endDTD() {
    }

    /**
     * Report the end of an entity.
     *
     * @param name The name of the entity that is ending.
     */
    @Override
    public void endEntity(String name) {
    }

    /**
     * Report the start of a CDATA section.
     */
    @Override
    public void startCDATA() {
    }

    /**
     * Report the start of DTD declarations, if any.
     *
     * @param name The document type name.
     * @param publicId The declared public identifier for the external DTD subset.
     * @param systemId The declared system identifier for the external DTD subset.
     */
    @Override
    public void startDTD(String name, String publicId, String systemId) {
    }

    /**
     * Report the beginning of some internal and external XML entities.
     *
     * @param name The name of the entity.
     */
    @Override
    public void startEntity(String name) {
    }
}

/**
 * Simple DeclHandler that skips every DTD declaration event.
 */
class MyDeclHandler implements DeclHandler {
    /**
     * Report an attribute type declaration.
     * @param eName The name of the associated element.
     * @param aName The name of the attribute.
     * @param type A string representing the attribute type.
     * @param mode A string representing the attribute defaulting mode
     *        ("#IMPLIED", "#REQUIRED", or "#FIXED") or null if
     *        none of these applies.
     * @param value A string representing the attribute's default value,
     *        or null if there is none.
     */
    @Override
    public void attributeDecl(String eName, String aName, String type,
            String valueDefault, String value) {
    }

    /**
     * Report an element type declaration.
     * @param name The element type name.
     * @param model The content model as a normalized string.
     */
    @Override
    public void elementDecl(String name, String model) {
    }

    /**
     * Report a parsed external entity declaration.
     * @param name The name of the entity.  If it is a parameter
     *        entity, the name will begin with '%'.
     * @param publicId The entity's public identifier, or null if none
     *        was given.
     * @param systemId The entity's system identifier.
     */
    @Override
    public void externalEntityDecl(String name, String publicId,
            String systemId) {
    }

    /**
     * Report an internal entity declaration.
     * @param name The name of the entity.  If it is a parameter
     *        entity, the name will begin with '%'.
     * @param value The replacement text of the entity.
     */
    @Override
    public void internalEntityDecl(String name, String value) {
    }
}
