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

import static com.sun.org.apache.xerces.internal.jaxp.JAXPConstants.JAXP_SCHEMA_LANGUAGE;
import static com.sun.org.apache.xerces.internal.jaxp.JAXPConstants.JAXP_SCHEMA_SOURCE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Paths;
import java.util.GregorianCalendar;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import static jaxp.library.JAXPTestUtilities.bomStream;
import static jaxp.library.JAXPTestUtilities.failUnexpected;
import org.testng.annotations.Test;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.TypeInfo;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.SAXException;
import static test.auctionportal.HiBidConstants.PORTAL_ACCOUNT_NS;
import static test.auctionportal.HiBidConstants.XML_DIR;

/**
 * This is the user controller  class for the Auction portal HiBid.com.
 */
public class AuctionController {
    /**
     * Check for DOMErrorHandler handling DOMError. Before fix of bug 4890927
     * DOMConfiguration.setParameter("well-formed",true) throws an exception.
     */
    @Test
    public void testCreateNewItem2Sell() {
        String xmlFile = XML_DIR + "novelsInvalid.xml";

        try {
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(xmlFile);

            document.getDomConfig().setParameter("well-formed", true);

            DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();
            DOMImplementationLS impl = (DOMImplementationLS) registry.getDOMImplementation("LS");
            MyDOMOutput domOutput = new MyDOMOutput();
            domOutput.setByteStream(System.out);
            LSSerializer writer = impl.createLSSerializer();
            writer.write(document, domOutput);
        } catch (ParserConfigurationException | SAXException | IOException
                | ClassNotFoundException | InstantiationException
                | IllegalAccessException | ClassCastException e) {
            failUnexpected(e);
        }
    }

    /**
     * Check for DOMErrorHandler handling DOMError. Before fix of bug 4896132
     * test throws DOM Level 1 node error.
     */
    @Test
    public void testCreateNewItem2SellRetry() {
        String xmlFile = XML_DIR + "accountInfo.xml";

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document document = dbf.newDocumentBuilder().parse(xmlFile);

            DOMConfiguration domConfig = document.getDomConfig();
            MyDOMErrorHandler errHandler = new MyDOMErrorHandler();
            domConfig.setParameter("error-handler", errHandler);

            DOMImplementationLS impl =
                 (DOMImplementationLS) DOMImplementationRegistry.newInstance()
                         .getDOMImplementation("LS");
            LSSerializer writer = impl.createLSSerializer();
            MyDOMOutput domoutput = new MyDOMOutput();

            domoutput.setByteStream(System.out);
            writer.write(document, domoutput);

            document.normalizeDocument();
            writer.write(document, domoutput);
            assertFalse(errHandler.isError());
        } catch (ParserConfigurationException | SAXException | IOException
                | ClassNotFoundException | InstantiationException
                | IllegalAccessException | ClassCastException e) {
            failUnexpected(e);
        }
    }

    /**
     * Check if setting the attribute to be of type ID works. This will affect
     * the Attr.isID method according to the spec.
     */
    @Test
    public void testCreateID() {
        String xmlFile = XML_DIR + "accountInfo.xml";

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);

            Document document = dbf.newDocumentBuilder().parse(xmlFile);
            Element account = (Element)document
                .getElementsByTagNameNS(PORTAL_ACCOUNT_NS, "Account").item(0);

            account.setIdAttributeNS(PORTAL_ACCOUNT_NS, "accountID", true);
            Attr aID = account.getAttributeNodeNS(PORTAL_ACCOUNT_NS, "accountID");
            assertTrue(aID.isId());
        } catch (ParserConfigurationException | SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Check the user data on the node.
     */
    @Test
    public void testCheckingUserData() {
        String xmlFile = XML_DIR + "accountInfo.xml";

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);

            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            Document document = docBuilder.parse(xmlFile);

            Element account = (Element)document.getElementsByTagNameNS(PORTAL_ACCOUNT_NS, "Account").item(0);
            assertEquals(account.getNodeName(), "acc:Account");
            Element firstName = (Element) document.getElementsByTagNameNS(PORTAL_ACCOUNT_NS, "FirstName").item(0);
            assertEquals(firstName.getNodeName(), "FirstName");

            Document doc1 = docBuilder.newDocument();
            Element someName = doc1.createElement("newelem");

            someName.setUserData("mykey", "dd",
                (operation, key,  data, src,  dst) ->  {
                    System.err.println("In UserDataHandler" + key);
                    System.out.println("In UserDataHandler");
                });
            Element impAccount = (Element)document.importNode(someName, true);
            assertEquals(impAccount.getNodeName(), "newelem");
            document.normalizeDocument();
            String data = (someName.getUserData("mykey")).toString();
            assertEquals(data, "dd");
        } catch (ParserConfigurationException | SAXException | IOException e) {
            failUnexpected(e);
        }
    }


    /**
     * Check the UTF-16 XMLEncoding xml file.
     * @see <a href="content/movies.xml">movies.xml</a>
     */
    @Test
    public void testCheckingEncoding() {
        // Note since movies.xml is UTF-16 encoding. We're not using stanard XML
        // file suffix.
        String xmlFile = XML_DIR + "movies.xml.data";

        //try (FileInputStream is = new FileInputStream(xmlFile)) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            InputStream source = bomStream("UTF-16", xmlFile);
            Document document = dbf.newDocumentBuilder().parse(source);
            assertEquals(document.getXmlEncoding(), "UTF-16");
            assertEquals(document.getXmlStandalone(), true);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Check validation API features. A schema which is including in Bug 4909119
     * used to be testing for the functionalities.
     * @see <a href="content/userDetails.xsd">userDetails.xsd</a>
     */
    @Test
    public void testGetOwnerInfo() {
        String schemaFile = XML_DIR + "userDetails.xsd";
        String xmlFile = XML_DIR + "userDetails.xml";

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setAttribute(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA_NS_URI);

            SchemaFactory schemaFactory = SchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI);
            Schema schema = schemaFactory.newSchema(Paths.get(schemaFile).toFile());

            Validator validator = schema.newValidator();
            MyErrorHandler eh = new MyErrorHandler();
            validator.setErrorHandler(eh);

            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            docBuilder.setErrorHandler(eh);

            Document document = docBuilder.parse(new FileInputStream(xmlFile));
            DOMResult dResult = new DOMResult();
            DOMSource domSource = new DOMSource(document);
            validator.validate(domSource, dResult);
            assertFalse(eh.isAnyError());
        } catch (SAXException | ParserConfigurationException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Check grammar caching with imported schemas.
     * @see <a href="content/coins.xsd">coins.xsd</a>
     * @see <a href="content/coinsImportMe.xsd">coinsImportMe.xsd</a>
     */
    @Test
    public void testGetOwnerItemList() {
        String xsdFile = XML_DIR + "coins.xsd";
        String xmlFile = XML_DIR + "coins.xml";

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setAttribute(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA_NS_URI);
            dbf.setValidating(false);

            SchemaFactory schemaFactory = SchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI);
            Schema schema = schemaFactory.newSchema(new File(((xsdFile))));

            MyErrorHandler eh = new MyErrorHandler();
            Validator validator = schema.newValidator();
            validator.setErrorHandler(eh);

            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            Document document = docBuilder.parse(new FileInputStream(xmlFile));
            validator.validate(new DOMSource(document), new DOMResult());
            assertFalse(eh.isAnyError());
        } catch (SAXException | ParserConfigurationException | IOException e) {
            failUnexpected(e);
        }
    }


    /**
     * Check for the same imported schemas but will use SAXParserFactory and try
     * parsing using the SAXParser. SCHEMA_SOURCE attribute is using for this
     * test.
     * @see <a href="content/coins.xsd">coins.xsd</a>
     * @see <a href="content/coinsImportMe.xsd">coinsImportMe.xsd</a>
     */

    @Test
    public void testGetOwnerItemList1() {
        String xsdFile = XML_DIR + "coins.xsd";
        String xmlFile = XML_DIR + "coins.xml";

        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            spf.setValidating(true);

            SAXParser sp = spf.newSAXParser();
            sp.setProperty(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA_NS_URI);
            sp.setProperty(JAXP_SCHEMA_SOURCE, xsdFile);

            MyErrorHandler eh = new MyErrorHandler();
            sp.parse(new File(xmlFile), eh);
            assertFalse(eh.isAnyError());
        } catch (ParserConfigurationException | SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Check usage of javax.xml.datatype.Duration class.
     */
    @Test
    public void testGetItemDuration() {
        String xmlFile = XML_DIR + "itemsDuration.xml";

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document document = dbf.newDocumentBuilder().parse(xmlFile);

            Element durationElement = (Element) document.getElementsByTagName("sellDuration").item(0);

            NodeList childList = durationElement.getChildNodes();

            for (int i = 0; i < childList.getLength(); i++) {
                System.out.println("child " + i + childList.item(i));
            }

            Duration duration = DatatypeFactory.newInstance().newDuration("P365D");
            Duration sellDuration = DatatypeFactory.newInstance().newDuration(childList.item(0).getNodeValue());
            assertFalse(sellDuration.isShorterThan(duration));
            assertFalse(sellDuration.isLongerThan(duration));
            assertEquals(sellDuration.getField(DatatypeConstants.DAYS), BigInteger.valueOf(365));
            assertEquals(sellDuration.normalizeWith(new GregorianCalendar(1999, 2, 22)), duration);

            Duration myDuration = sellDuration.add(duration);
            assertEquals(myDuration.normalizeWith(new GregorianCalendar(2003, 2, 22)),
                    DatatypeFactory.newInstance().newDuration("P730D"));
        } catch (ParserConfigurationException | DatatypeConfigurationException
                | SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Check usage of TypeInfo interface introduced in DOM L3.
     */
    @Test
    public void testGetTypeInfo() {
        String xmlFile = XML_DIR + "accountInfo.xml";

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setValidating(true);
            dbf.setAttribute(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA_NS_URI);

            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            docBuilder.setErrorHandler(new MyErrorHandler());

            Document document = docBuilder.parse(xmlFile);
            Element userId = (Element)document.getElementsByTagNameNS(PORTAL_ACCOUNT_NS, "UserID").item(0);
            TypeInfo typeInfo = userId.getSchemaTypeInfo();
            assertTrue(typeInfo.getTypeName().equals("nonNegativeInteger"));
            assertTrue(typeInfo.getTypeNamespace().equals(W3C_XML_SCHEMA_NS_URI));

            Element role = (Element)document.getElementsByTagNameNS(PORTAL_ACCOUNT_NS, "Role").item(0);
            TypeInfo roletypeInfo = role.getSchemaTypeInfo();
            assertTrue(roletypeInfo.getTypeName().equals("BuyOrSell"));
            assertTrue(roletypeInfo.getTypeNamespace().equals(PORTAL_ACCOUNT_NS));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            failUnexpected(e);
        }
    }
}
