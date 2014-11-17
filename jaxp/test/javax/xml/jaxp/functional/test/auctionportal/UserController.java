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
import static org.testng.Assert.assertFalse;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import static jaxp.library.JAXPTestUtilities.compareDocumentWithGold;
import static jaxp.library.JAXPTestUtilities.failCleanup;
import static jaxp.library.JAXPTestUtilities.failUnexpected;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSParser;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.SAXException;
import static test.auctionportal.HiBidConstants.CLASS_DIR;
import static test.auctionportal.HiBidConstants.GOLDEN_DIR;
import static test.auctionportal.HiBidConstants.PORTAL_ACCOUNT_NS;
import static test.auctionportal.HiBidConstants.XML_DIR;

/**
 * This is the user controller class for the Auction portal HiBid.com.
 */
public class UserController {
    /**
     * Checking when creating an XML document using DOM Level 2 validating
     * it without having a schema source or a schema location It must throw a
     * sax parse exception.
     */
    @Test
    public void testCreateNewUser() {
        String resultFile = CLASS_DIR + "accountInfoOut.xml";
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setValidating(true);

            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            MyErrorHandler eh = new MyErrorHandler();
            docBuilder.setErrorHandler(eh);

            Document document = docBuilder.newDocument();

            Element account = document.createElementNS(PORTAL_ACCOUNT_NS, "acc:Account");
            Attr accountID = document.createAttributeNS(PORTAL_ACCOUNT_NS, "acc:accountID");
            account.setAttributeNode(accountID);

            account.appendChild(document.createElement("FirstName"));
            account.appendChild(document.createElementNS(PORTAL_ACCOUNT_NS, "acc:LastName"));
            account.appendChild(document.createElement("UserID"));

            DOMImplementationLS impl
                    = (DOMImplementationLS) DOMImplementationRegistry
                            .newInstance().getDOMImplementation("LS");
            LSSerializer writer = impl.createLSSerializer();
            LSParser builder = impl.createLSParser(DOMImplementationLS.MODE_SYNCHRONOUS, null);
            FileOutputStream output = new FileOutputStream(resultFile);
            MyDOMOutput domOutput = new MyDOMOutput();

            domOutput.setByteStream(output);
            writer.write(account, domOutput);
            docBuilder.parse(resultFile);

            assertTrue(eh.isAnyError());
        } catch (ParserConfigurationException | ClassNotFoundException |
                InstantiationException | IllegalAccessException
                | ClassCastException | SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Checking conflicting namespaces and use renameNode and normalizeDocument.
     * @see <a href="content/accountInfo.xml">accountInfo.xml</a>
     */
    @Test
    public void testAddUser() {
        String resultFile = CLASS_DIR + "accountRole.out";
        String xmlFile = XML_DIR + "accountInfo.xml";

        try {
            // Copy schema for outputfile
            Files.copy(Paths.get(XML_DIR, "accountInfo.xsd"),
                    Paths.get(CLASS_DIR, "accountInfo.xsd"),
                    StandardCopyOption.REPLACE_EXISTING);
            MyErrorHandler eh = new MyErrorHandler();
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

            dbf.setAttribute(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA_NS_URI);
            dbf.setNamespaceAware(true);
            dbf.setValidating(true);

            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            docBuilder.setErrorHandler(eh);

            Document document = docBuilder.parse(xmlFile);
            Element sell = (Element) document.getElementsByTagNameNS(PORTAL_ACCOUNT_NS, "Sell").item(0);
            Element role = (Element) sell.getParentNode();

            Element buy = (Element) document.renameNode(sell, PORTAL_ACCOUNT_NS, "acc:Buy");
            role.appendChild(buy);

            DOMImplementationLS impl
                    = (DOMImplementationLS) DOMImplementationRegistry
                            .newInstance().getDOMImplementation("LS");
            LSSerializer writer = impl.createLSSerializer();


            try(FileOutputStream output = new FileOutputStream(resultFile)) {
                MyDOMOutput mydomoutput = new MyDOMOutput();
                mydomoutput.setByteStream(output);
                writer.write(document, mydomoutput);
            }

            docBuilder.parse(resultFile);
            assertFalse(eh.isAnyError());
        } catch (ParserConfigurationException | SAXException | IOException
                | ClassNotFoundException | InstantiationException
                | IllegalAccessException | ClassCastException e) {
            failUnexpected(e);
        }
    }

    /**
     * Checking Text content in XML file.
     * @see <a href="content/accountInfo.xml">accountInfo.xml</a>
     */
    @Test
    public void testMoreUserInfo() {
        String xmlFile = XML_DIR + "accountInfo.xml";

        try {
            System.out.println("Checking additional user info");

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

            dbf.setAttribute(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA_NS_URI);
            dbf.setNamespaceAware(true);
            dbf.setValidating(true);

            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            MyErrorHandler eh = new MyErrorHandler();
            docBuilder.setErrorHandler(eh);

            Document document = docBuilder.parse(xmlFile);
            Element account = (Element)document
                    .getElementsByTagNameNS(PORTAL_ACCOUNT_NS, "Account").item(0);
            String textContent = account.getTextContent();
            assertTrue(textContent.trim().regionMatches(0, "Rachel", 0, 6));
            assertEquals(textContent, "RachelGreen744");

            Attr accountID = account.getAttributeNodeNS(PORTAL_ACCOUNT_NS, "accountID");
            assertTrue(accountID.getTextContent().trim().equals("1"));

            assertFalse(eh.isAnyError());
        } catch (ParserConfigurationException | SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * This will check if adoptNode works will adoptNode from
     * @see <a href="content/userInfo.xml">userInfo.xml</a>
     * @see <a href="content/accountInfo.xml">accountInfo.xml</a>. This is
     * adopting a node from the XML file which is validated by a DTD and
     * into an XML file which is validated by the schema This covers Row 5
     * for the table
     * http://javaweb.sfbay/~jsuttor/JSR206/jsr-206-html/ch03s05.html. Filed
     * bug 4893745 because there was a difference in behavior
     */
    @Test
    public void testCreateUserAccount() {
        System.out.println("Creating user account");
        String userXmlFile = XML_DIR + "userInfo.xml";
        String accountXmlFile = XML_DIR + "accountInfo.xml";

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setValidating(true);

            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            MyErrorHandler eh = new MyErrorHandler();
            docBuilder.setErrorHandler(eh);

            Document document = docBuilder.parse(userXmlFile);
            Element user = (Element) document.getElementsByTagName("FirstName").item(0);
            // Set schema after parsing userInfo.xml. Otherwise it will conflict
            // with DTD validation.
            dbf.setAttribute(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA_NS_URI);
            DocumentBuilder docBuilder1 = dbf.newDocumentBuilder();
            docBuilder1.setErrorHandler(eh);
            Document accDocument = docBuilder1.parse(accountXmlFile);

            Element firstName = (Element) accDocument
                    .getElementsByTagNameNS(PORTAL_ACCOUNT_NS, "FirstName").item(0);
            Element adoptedAccount = (Element) accDocument.adoptNode(user);

            Element parent = (Element) firstName.getParentNode();
            parent.replaceChild(adoptedAccount, firstName);

            DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();
            DOMImplementationLS impl = (DOMImplementationLS) registry.getDOMImplementation("LS");
            LSSerializer writer = impl.createLSSerializer();

            MyDOMOutput mydomoutput = new MyDOMOutput();
            mydomoutput.setByteStream(System.out);

            writer.write(document, mydomoutput);
            writer.write(accDocument, mydomoutput);

            assertFalse(eh.isAnyError());
        } catch (ParserConfigurationException | SAXException | IOException
                | ClassNotFoundException | InstantiationException
                | IllegalAccessException | ClassCastException e) {
            failUnexpected(e);
        }
    }

    /**
     * Checking for Row 8 from the schema table when setting the schemaSource
     * without the schemaLanguage must report an error.
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testUserError() throws IllegalArgumentException {
        System.out.println("Creating an error in user account");

        String xmlFile = XML_DIR + "userInfo.xml";
        String schema = "http://java.sun.com/xml/jaxp/properties/schemaSource";
        String schemaValue = "http://dummy.com/dummy.xsd";

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setValidating(true);
            dbf.setAttribute(schema, schemaValue);

            DocumentBuilder docBuilder = dbf.newDocumentBuilder();
            MyErrorHandler eh = new MyErrorHandler();
            docBuilder.setErrorHandler(eh);
            Document document = docBuilder.parse(xmlFile);
            assertFalse(eh.isAnyError());
        } catch (ParserConfigurationException | SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Checking for namespace normalization.
     * @see <a href="content/screenName.xml">screenName.xml</a> has prefix of
     * userName is bound to "http://hibid.com/user" namespace normalization
     * will create a namespace of prefix us and attach userEmail.
     */
    @Test
    public void testCheckScreenNameExists() {
        String resultFile = CLASS_DIR + "screenName.out";
        String xmlFile = XML_DIR + "screenName.xml";
        String goldFile = GOLDEN_DIR + "screenNameGold.xml";

        String nsTagName = "http://hibid.com/screenName";
        String userNs = "http://hibid.com/user";

        try (FileOutputStream output = new FileOutputStream(resultFile)) {
            DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();
            DOMImplementationLS impl = (DOMImplementationLS) registry.getDOMImplementation("LS");
            LSSerializer writer = impl.createLSSerializer();
            LSParser builder = impl.createLSParser(DOMImplementationLS.MODE_SYNCHRONOUS, null);
            Document document = builder.parseURI(xmlFile);
            NodeList nl = document.getElementsByTagNameNS(nsTagName, "screen-name");
            assertEquals(nl.getLength(), 1);
            Element screenName = (Element)nl.item(0);
            Element userEmail = document.createElementNS(userNs, "userEmail");
            assertTrue(userEmail.isDefaultNamespace(userNs));

            Text email = document.createTextNode("myid@hibid.com");
            userEmail.appendChild(email);
            screenName.appendChild(userEmail);
            document.normalizeDocument();

            MyDOMOutput domoutput = new MyDOMOutput();
            domoutput.setByteStream(output);
            writer.write(document, domoutput);

            assertTrue(compareDocumentWithGold(goldFile, resultFile));
        } catch (ClassNotFoundException | InstantiationException
                | IllegalAccessException | ClassCastException | IOException
                | ParserConfigurationException | SAXException e) {
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
