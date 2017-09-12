/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8159058
 * @summary Test that empty default namespace declaration clears the
 *          default namespace value
 * @modules java.xml.ws/com.sun.xml.internal.ws.api
 *          java.xml.ws/com.sun.xml.internal.ws.api.message.saaj
 *          java.xml.ws/com.sun.xml.internal.ws.message.stream
 * @run testng/othervm SaajEmptyNamespaceTest
 */

import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.message.saaj.SAAJFactory;
import com.sun.xml.internal.ws.message.stream.StreamMessage;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import javax.xml.namespace.QName;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.w3c.dom.Node;

public class SaajEmptyNamespaceTest {

    /*
     * Test that SOAP message with default namespace declaration that contains empty
     * string is properly processed by SAAJ reader.
     */
    @Test
    public void testResetDefaultNamespaceSAAJ() throws Exception {
        // Create SOAP message from XML string and process it with SAAJ reader
        XMLStreamReader envelope = XMLInputFactory.newFactory().createXMLStreamReader(
                new StringReader(INPUT_SOAP_MESSAGE));
        StreamMessage streamMessage = new StreamMessage(SOAPVersion.SOAP_11,
                envelope, null);
        SAAJFactory saajFact = new SAAJFactory();
        SOAPMessage soapMessage = saajFact.readAsSOAPMessage(SOAPVersion.SOAP_11, streamMessage);

        // Check if constructed object model meets local names and namespace expectations
        SOAPElement request = (SOAPElement) soapMessage.getSOAPBody().getFirstChild();
        // Check top body element name
        Assert.assertEquals(request.getLocalName(), "SampleServiceRequest");
        // Check top body element namespace
        Assert.assertEquals(request.getNamespaceURI(), TEST_NS);
        SOAPElement params = (SOAPElement) request.getFirstChild();
        // Check first child name
        Assert.assertEquals(params.getLocalName(), "RequestParams");
        // Check if first child namespace is null
        Assert.assertNull(params.getNamespaceURI());

        // Check inner elements of the first child
        SOAPElement param1 = (SOAPElement) params.getFirstChild();
        Assert.assertEquals(param1.getLocalName(), "Param1");
        Assert.assertNull(param1.getNamespaceURI());
        SOAPElement param2 = (SOAPElement) params.getChildNodes().item(1);
        Assert.assertEquals(param2.getLocalName(), "Param2");
        Assert.assertNull(param2.getNamespaceURI());
        // Check full content of SOAP body
        Assert.assertEquals(nodeToText(request), EXPECTED_RESULT);
    }

    /*
     * Test that adding element with explicitly null namespace URI shall put the
     * element into global namespace. Namespace declarations are not added explicitly.
     */
    @Test
    public void testAddElementToNullNsNoDeclarations() throws Exception {
        // Create empty SOAP message
        SOAPMessage msg = createSoapMessage();
        SOAPBody body = msg.getSOAPPart().getEnvelope().getBody();

        // Add elements
        SOAPElement parentExplicitNS = body.addChildElement("content", "", TEST_NS);
        SOAPElement childGlobalNS = parentExplicitNS.addChildElement("global-child", "", null);
        SOAPElement childDefaultNS = parentExplicitNS.addChildElement("default-child");

        // Check namespace URIs
        Assert.assertNull(childGlobalNS.getNamespaceURI());
        Assert.assertEquals(childDefaultNS.getNamespaceURI(), TEST_NS);
    }

    /*
     * Test that adding element with explicitly empty namespace URI shall put
     * the element into global namespace. Namespace declarations are not added
     * explicitly.
     */
    @Test
    public void testAddElementToGlobalNsNoDeclarations() throws Exception {
        // Create empty SOAP message
        SOAPMessage msg = createSoapMessage();
        SOAPBody body = msg.getSOAPPart().getEnvelope().getBody();

        // Add elements
        SOAPElement parentExplicitNS = body.addChildElement("content", "", TEST_NS);
        SOAPElement childGlobalNS = parentExplicitNS.addChildElement("global-child", "", "");
        SOAPElement childDefaultNS = parentExplicitNS.addChildElement("default-child");

        // Check namespace URIs
        Assert.assertNull(childGlobalNS.getNamespaceURI());
        Assert.assertEquals(childDefaultNS.getNamespaceURI(), TEST_NS);
    }

    /*
     * Test that adding element with explicitly empty namespace URI set via QName
     * shall put the element into global namespace.
     */
    @Test
    public void testAddElementToNullNsQName() throws Exception {
        // Create empty SOAP message
        SOAPMessage msg = createSoapMessage();
        SOAPBody body = msg.getSOAPPart().getEnvelope().getBody();

        // Add elements
        SOAPElement parentExplicitNS = body.addChildElement("content", "", TEST_NS);
        parentExplicitNS.addNamespaceDeclaration("", TEST_NS);
        SOAPElement childGlobalNS = parentExplicitNS.addChildElement(new QName(null, "global-child"));
        childGlobalNS.addNamespaceDeclaration("", "");
        SOAPElement grandChildGlobalNS = childGlobalNS.addChildElement("global-grand-child");
        SOAPElement childDefaultNS = parentExplicitNS.addChildElement("default-child");

        // Check namespace URIs
        Assert.assertNull(childGlobalNS.getNamespaceURI());
        Assert.assertNull(grandChildGlobalNS.getNamespaceURI());
        Assert.assertEquals(childDefaultNS.getNamespaceURI(), TEST_NS);
    }

    /*
     * Test that adding element with explicitly empty namespace URI shall put
     * the element into global namespace.
     */
    @Test
    public void testAddElementToGlobalNs() throws Exception {
        // Create empty SOAP message
        SOAPMessage msg = createSoapMessage();
        SOAPBody body = msg.getSOAPPart().getEnvelope().getBody();

        // Add elements
        SOAPElement parentExplicitNS = body.addChildElement("content", "", TEST_NS);
        parentExplicitNS.addNamespaceDeclaration("", TEST_NS);
        SOAPElement childGlobalNS = parentExplicitNS.addChildElement("global-child", "", "");
        childGlobalNS.addNamespaceDeclaration("", "");
        SOAPElement grandChildGlobalNS = childGlobalNS.addChildElement("global-grand-child");
        SOAPElement childDefaultNS = parentExplicitNS.addChildElement("default-child");

        // Check namespace URIs
        Assert.assertNull(childGlobalNS.getNamespaceURI());
        Assert.assertNull(grandChildGlobalNS.getNamespaceURI());
        Assert.assertEquals(childDefaultNS.getNamespaceURI(), TEST_NS);
    }

    /*
     * Test that adding element with explicitly null namespace URI shall put
     * the element into global namespace.
     */
    @Test
    public void testAddElementToNullNs() throws Exception {
        // Create empty SOAP message
        SOAPMessage msg = createSoapMessage();
        SOAPBody body = msg.getSOAPPart().getEnvelope().getBody();

        // Add elements
        SOAPElement parentExplicitNS = body.addChildElement("content", "", TEST_NS);
        parentExplicitNS.addNamespaceDeclaration("", TEST_NS);
        SOAPElement childGlobalNS = parentExplicitNS.addChildElement("global-child", "", null);
        childGlobalNS.addNamespaceDeclaration("", null);
        SOAPElement grandChildGlobalNS = childGlobalNS.addChildElement("global-grand-child");
        SOAPElement childDefaultNS = parentExplicitNS.addChildElement("default-child");

        // Check namespace URIs
        Assert.assertNull(childGlobalNS.getNamespaceURI());
        Assert.assertNull(grandChildGlobalNS.getNamespaceURI());
        Assert.assertEquals(TEST_NS, childDefaultNS.getNamespaceURI());
    }

    /*
     * Test that adding element with explicitly empty namespace URI via QName
     * shall put the element in global namespace.
     */
    @Test
    public void testAddElementToGlobalNsQName() throws Exception {
        // Create empty SOAP message
        SOAPMessage msg = createSoapMessage();
        SOAPBody body = msg.getSOAPPart().getEnvelope().getBody();

        // Add elements
        SOAPElement parentExplicitNS = body.addChildElement("content", "", TEST_NS);
        parentExplicitNS.addNamespaceDeclaration("", TEST_NS);
        SOAPElement childGlobalNS = parentExplicitNS.addChildElement(new QName("", "global-child"));
        childGlobalNS.addNamespaceDeclaration("", "");
        SOAPElement grandChildGlobalNS = childGlobalNS.addChildElement("global-grand-child");
        SOAPElement childDefaultNS = parentExplicitNS.addChildElement("default-child");

        // Check namespace URIs
        Assert.assertNull(childGlobalNS.getNamespaceURI());
        Assert.assertNull(grandChildGlobalNS.getNamespaceURI());
        Assert.assertEquals(childDefaultNS.getNamespaceURI(),TEST_NS);
    }

    // Convert DOM node to text representation
    private String nodeToText(Node node) throws TransformerException {
        Transformer trans = TransformerFactory.newInstance().newTransformer();
        trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        trans.transform(new DOMSource(node), result);
        String bodyContent = writer.toString();
        System.out.println("SOAP body content read by SAAJ:"+bodyContent);
        return bodyContent;
    }

    // Create SOAP message with empty body
    private static SOAPMessage createSoapMessage() throws SOAPException, UnsupportedEncodingException {
        String xml = "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                    +"<SOAP-ENV:Body/></SOAP-ENV:Envelope>";
        MessageFactory mFactory = MessageFactory.newInstance();
        SOAPMessage msg = mFactory.createMessage();
        msg.getSOAPPart().setContent(new StreamSource(new ByteArrayInputStream(xml.getBytes("utf-8"))));
        return msg;
    }

    // Namespace value used in tests
    private static String TEST_NS = "http://example.org/test";

    // Content of SOAP message passed to SAAJ factory
    private static String INPUT_SOAP_MESSAGE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + "<s:Body>"
            + "<SampleServiceRequest xmlns=\"http://example.org/test\""
            + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
            + "<RequestParams xmlns=\"\">"
            + "<Param1>hogehoge</Param1>"
            + "<Param2>fugafuga</Param2>"
            + "</RequestParams>"
            + "</SampleServiceRequest>"
            + "</s:Body>"
            + "</s:Envelope>";

    // Expected body content after SAAJ processing
    private static String EXPECTED_RESULT = "<SampleServiceRequest"
            +" xmlns=\"http://example.org/test\">"
            + "<RequestParams xmlns=\"\">"
            + "<Param1>hogehoge</Param1>"
            + "<Param2>fugafuga</Param2>"
            + "</RequestParams>"
            + "</SampleServiceRequest>";
}
