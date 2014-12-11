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

import static jaxp.library.JAXPTestUtilities.failUnexpected;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.xml.sax.Parser;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DeclHandler;
import org.xml.sax.ext.LexicalHandler;

/**
 * Class contains the test cases for SAXParser API
 */
public class SAXParserTest02 {
    final String DOM_NODE = "http://xml.org/sax/properties/dom-node";
    final String XML_STRING = "http://xml.org/sax/properties/xml-string";
    final String DECL_HANDLER = "http://xml.org/sax/properties/declaration-handler";
    final String LEXICAL_HANDLER = "http://xml.org/sax/properties/lexical-handler";

    /**
     * Provide SAXParser.
     *
     * @throws SAXException
     * @throws ParserConfigurationException
     */
    @DataProvider(name = "parser-provider")
    public Object[][] getParser() throws ParserConfigurationException, SAXException {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        SAXParser saxparser = spf.newSAXParser();
        return new Object[][] { { saxparser } };
    }

    /**
     * Testcase to test the default functionality (No validation) of the parser.
     */
    @Test(dataProvider = "parser-provider")
    public void testValidate01(SAXParser saxparser) {
        try {
            assertFalse(saxparser.isValidating());
        } catch (FactoryConfigurationError e) {
            failUnexpected(e);
        }

    }

    /**
     * Testcase to test the functionality of setValidating and isvalidating
     * methods.
     */
    @Test
    public void testValidate02() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setValidating(true);
            spf.newSAXParser();
            assertTrue(spf.isValidating());
        } catch (FactoryConfigurationError | ParserConfigurationException | SAXException e) {
            failUnexpected(e);
        }

    }

    /**
     * Test case to test isNamespaceAware() method. By default, namespaces are
     * not supported.
     */
    @Test(dataProvider = "parser-provider")
    public void testNamespace01(SAXParser saxparser) {
        try {
            assertFalse(saxparser.isNamespaceAware());
        } catch (FactoryConfigurationError e) {
            failUnexpected(e);
        }

    }

    /**
     * Test case to test setnamespaceAware() method.
     */
    @Test
    public void testNamespace02() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            SAXParser saxparser = spf.newSAXParser();
            assertTrue(saxparser.isNamespaceAware());
        } catch (FactoryConfigurationError | ParserConfigurationException | SAXException e) {
            failUnexpected(e);
        }

    }

    /**
     * Test case to test if the getParser() method returns instance of Parser.
     */
    @Test(dataProvider = "parser-provider")
    public void testParser01(SAXParser saxparser) {
        try {
            Parser parser = saxparser.getParser();
        } catch (FactoryConfigurationError | SAXException e) {
            failUnexpected(e);
        }

    }

    /**
     * Test case to test if the getXMLReader() method returns instance of
     * XMLReader.
     */
    @Test(dataProvider = "parser-provider")
    public void testXmlReader01(SAXParser saxparser) {
        try {
            XMLReader xmlReader = saxparser.getXMLReader();
        } catch (FactoryConfigurationError | SAXException e) {
            failUnexpected(e);
        }
    }

    /**
     * Test whether the xml-string property is not supported.
     *
     * @throws SAXNotSupportedException
     */
    @Test(expectedExceptions = SAXNotSupportedException.class, dataProvider = "parser-provider")
    public void testProperty01(SAXParser saxparser) throws SAXNotSupportedException {
        try {
            Object object = saxparser.getProperty(XML_STRING);
        } catch (SAXNotRecognizedException e) {
            failUnexpected(e);
        }
    }

    /**
     * Test whether the dom-node property is not supported.
     *
     * @throws SAXNotSupportedException
     */
    @Test(expectedExceptions = SAXNotSupportedException.class, dataProvider = "parser-provider")
    public void testProperty02(SAXParser saxparser) throws SAXNotSupportedException {
        try {
            Object object = saxparser.getProperty(DOM_NODE);
        } catch (SAXNotRecognizedException e) {
            failUnexpected(e);
        }
    }

    /**
     * Test the default lexical-handler not exists.
     */
    @Test(dataProvider = "parser-provider")
    public void testProperty03(SAXParser saxparser) {
        try {
            assertNull(saxparser.getProperty(LEXICAL_HANDLER));
        } catch (SAXException e) {
            failUnexpected(e);
        }

    }

    /**
     * Test the default declaration-handler not exists.
     */
    @Test(dataProvider = "parser-provider")
    public void testProperty04(SAXParser saxparser) {

        try {
            assertNull(saxparser.getProperty(DECL_HANDLER));
        } catch (SAXException e) {
            failUnexpected(e);
        }
    }

    /**
     * Test to set and get the lexical-handler.
     */
    @Test(dataProvider = "parser-provider")
    public void testProperty05(SAXParser saxparser) {
        try {
            MyLexicalHandler myLexicalHandler = new MyLexicalHandler();
            saxparser.setProperty(LEXICAL_HANDLER, myLexicalHandler);
            Object object = saxparser.getProperty(LEXICAL_HANDLER);
            assertTrue(object instanceof LexicalHandler);
        } catch (SAXException e) {
            failUnexpected(e);
        }
    }

    /**
     * Test to set and get the declaration-handler.
     */
    @Test(dataProvider = "parser-provider")
    public void testProperty06(SAXParser saxparser) {
        try {
            MyDeclHandler myDeclHandler = new MyDeclHandler();
            saxparser.setProperty(DECL_HANDLER, myDeclHandler);
            Object object = saxparser.getProperty(DECL_HANDLER);
            assertTrue(object instanceof DeclHandler);
        } catch (SAXException e) {
            failUnexpected(e);
        }

    }

    /**
     * Customized LexicalHandler used for test.
     */
    private class MyLexicalHandler implements LexicalHandler {

        public void comment(char[] ch, int start, int length) {
        }

        public void endCDATA() {
        }

        public void endDTD() {
        }

        public void endEntity(String name) {
        }

        public void startCDATA() {
        }

        public void startDTD(String name, String publicId, String systemId) {
        }

        public void startEntity(String name) {
        }
    }

    /**
     * Customized DeclHandler used for test.
     */
    private class MyDeclHandler implements DeclHandler {

        public void attributeDecl(String eName, String aName, String type, String valueDefault, String value) {
        }

        public void elementDecl(String name, String model) {
        }

        public void externalEntityDecl(String name, String publicId, String systemId) {
        }

        public void internalEntityDecl(String name, String value) {
        }
    }
}
