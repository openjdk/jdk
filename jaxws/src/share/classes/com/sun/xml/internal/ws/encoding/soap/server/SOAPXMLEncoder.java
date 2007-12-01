/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.xml.internal.ws.encoding.soap.server;

import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.bind.api.BridgeContext;
import com.sun.xml.internal.ws.client.BindingProviderProperties;
import static com.sun.xml.internal.ws.client.BindingProviderProperties.*;
import com.sun.xml.internal.ws.encoding.JAXWSAttachmentMarshaller;
import com.sun.xml.internal.ws.encoding.jaxb.JAXBBridgeInfo;
import com.sun.xml.internal.ws.encoding.soap.SOAPConstants;
import com.sun.xml.internal.ws.encoding.soap.SOAPEncoder;
import com.sun.xml.internal.ws.encoding.soap.internal.InternalMessage;
import com.sun.xml.internal.ws.encoding.soap.message.SOAPFaultInfo;
import com.sun.xml.internal.ws.encoding.soap.streaming.SOAPNamespaceConstants;
import com.sun.xml.internal.ws.handler.MessageContextUtil;
import com.sun.xml.internal.ws.server.RuntimeContext;
import com.sun.xml.internal.ws.server.ServerRtException;
import com.sun.xml.internal.ws.spi.runtime.WSConnection;
import com.sun.xml.internal.ws.streaming.DOMStreamReader;
import com.sun.xml.internal.ws.streaming.XMLStreamWriterFactory;
import com.sun.xml.internal.ws.util.ByteArrayBuffer;
import com.sun.xml.internal.ws.util.MessageInfoUtil;
import com.sun.xml.internal.ws.util.SOAPUtil;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.soap.Detail;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPMessage;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.soap.SOAPBinding;

/**
 * @author WS Development Team
 */
public class SOAPXMLEncoder extends SOAPEncoder {

    protected static final String FAULTCODE_NAME   = "faultcode";
    protected static final String FAULTSTRING_NAME = "faultstring";
    protected static final String FAULTACTOR_NAME  = "faultactor";
    protected static final String DETAIL_NAME      = "detail";

    public SOAPXMLEncoder() {
    }

    public SOAPMessage toSOAPMessage(InternalMessage response, MessageInfo messageInfo) {
        XMLStreamWriter writer = null;
        JAXWSAttachmentMarshaller marshaller = null;
        boolean xopEnabled = false;

        try {
            setAttachmentsMap(messageInfo, response);
            ByteArrayBuffer bab = new ByteArrayBuffer();

            if (messageInfo.getMetaData(CONTENT_NEGOTIATION_PROPERTY) == "optimistic") {
                writer = XMLStreamWriterFactory.createFIStreamWriter(bab);

                // Turn XOP off for FI
                marshaller = getAttachmentMarshaller(messageInfo);
                if (marshaller != null) {
                    xopEnabled = marshaller.isXOPPackage();     // last value
                    marshaller.setXOPPackage(false);
                }
            }
            else {
                // Store output stream to use in JAXB bridge (not with FI)
                messageInfo.setMetaData(JAXB_OUTPUTSTREAM, bab);
                writer = XMLStreamWriterFactory.createXMLStreamWriter(bab);
            }

            writer.writeStartDocument();
            startEnvelope(writer);
            writeEnvelopeNamespaces(writer, messageInfo);
            writeHeaders(writer, response, messageInfo);
            writeBody(writer, response, messageInfo);
            endEnvelope(writer);
            writer.writeEndDocument();
            writer.close();

            MimeHeaders mh = new MimeHeaders();
            mh.addHeader("Content-Type", getContentType(messageInfo, marshaller));
            SOAPMessage msg = SOAPUtil.createMessage(mh, bab.newInputStream(), getBindingId());
            processAttachments(response, msg);

            // Restore default XOP processing before returning
            if (marshaller != null) {
                marshaller.setXOPPackage(xopEnabled);
            }

            return msg;
        }
        catch (Exception e) {
            throw new ServerRtException("soapencoder.err", new Object[]{e});
        }
        finally {
            if (writer != null) {
                try {
                    writer.close();
                }
                catch (XMLStreamException e) {
                    throw new ServerRtException(e);
                }
            }
        }
    }

    protected JAXWSAttachmentMarshaller getAttachmentMarshaller(MessageInfo messageInfo) {
        Object rtc = messageInfo.getMetaData(BindingProviderProperties.JAXWS_RUNTIME_CONTEXT);
        if (rtc != null) {
            BridgeContext bc = ((RuntimeContext) rtc).getBridgeContext();
            if (bc != null) {
                return (JAXWSAttachmentMarshaller) bc.getAttachmentMarshaller();
            }
        }
        return null;
    }

    protected String getContentType(MessageInfo messageInfo,
        JAXWSAttachmentMarshaller marshaller)
    {
        String contentNegotiation = (String)
            messageInfo.getMetaData(BindingProviderProperties.CONTENT_NEGOTIATION_PROPERTY);

        if (marshaller == null) {
            marshaller = getAttachmentMarshaller(messageInfo);
        }

        if (marshaller != null && marshaller.isXopped()) {
            return XOP_SOAP11_XML_TYPE_VALUE;
        }
        else {
            return (contentNegotiation == "optimistic") ?
                FAST_INFOSET_TYPE_SOAP11 : XML_CONTENT_TYPE_VALUE;
        }
    }

    /*
     * writes <env:Fault> ... </env:Fault>. JAXB serializes the contents
     * in the <detail> for service specific exceptions. We serialize protocol
     * specific exceptions ourselves
     */
    protected void writeFault(SOAPFaultInfo instance, MessageInfo messageInfo, XMLStreamWriter writer) {
        try {
            // Set a status code for Fault
            MessageContext ctxt = MessageInfoUtil.getMessageContext(messageInfo);
            if (MessageContextUtil.getHttpStatusCode(ctxt) == null) {
                MessageContextUtil.setHttpStatusCode(ctxt, WSConnection.INTERNAL_ERR);
            }

            writer.writeStartElement(SOAPNamespaceConstants.NSPREFIX_SOAP_ENVELOPE,
                SOAPConstants.QNAME_SOAP_FAULT.getLocalPart(),
                SOAPConstants.QNAME_SOAP_FAULT.getNamespaceURI());
            // Writing NS since this may be called without writing envelope
            writer.writeNamespace(SOAPNamespaceConstants.NSPREFIX_SOAP_ENVELOPE,
                    SOAPConstants.QNAME_SOAP_FAULT.getNamespaceURI());

            writer.writeStartElement(FAULTCODE_NAME);   // <faultcode>
            String prefix = SOAPNamespaceConstants.NSPREFIX_SOAP_ENVELOPE;
            QName faultCode = instance.getCode();
            String nsURI = faultCode.getNamespaceURI();
            if (!nsURI.equals(SOAPNamespaceConstants.ENVELOPE)) {
                    // Need to add namespace declaration for this custom fault code
                if (nsURI.equals(XMLConstants.NULL_NS_URI)) {
                    prefix = XMLConstants.DEFAULT_NS_PREFIX;
                } else {
                    prefix = faultCode.getPrefix();
                    if (prefix.equals(XMLConstants.DEFAULT_NS_PREFIX)) {
                        prefix = "ans";
                    }
                    writer.setPrefix(prefix, nsURI);
                    writer.writeNamespace(prefix, nsURI);
                }
            }
            if (prefix.equals(XMLConstants.DEFAULT_NS_PREFIX)) {
                writer.writeCharacters(instance.getCode().getLocalPart());
            } else {
                    writer.writeCharacters(prefix+":"+instance.getCode().getLocalPart());
            }
            writer.writeEndElement();                    // </faultcode>

            writer.writeStartElement(FAULTSTRING_NAME);
            writer.writeCharacters(instance.getString());
            writer.writeEndElement();

            if (instance.getActor() != null) {
                writer.writeStartElement(FAULTACTOR_NAME);
                writer.writeCharacters(instance.getActor());
                writer.writeEndElement();
            }

            Object detail = instance.getDetail();
            if (detail != null) {
                // Not RuntimeException, Not header fault
                if (detail instanceof Detail) {
                    // SOAPFaultException
                    encodeDetail((Detail)detail, writer);
                } else if (detail instanceof JAXBBridgeInfo) {
                    // Service specific exception
                    writer.writeStartElement(DETAIL_NAME);
                    writeJAXBBridgeInfo((JAXBBridgeInfo)detail, messageInfo, writer);
                    writer.writeEndElement();        // </detail>
                }
            }

            writer.writeEndElement();                // </env:Fault>
        }
        catch (XMLStreamException e) {
            throw new ServerRtException(e);
        }
    }

    /*
     * Serializes javax.xml.soap.Detail. Detail is of type SOAPElement.
     * XmlTreeReader is used to traverse the SOAPElement/DOM Node and serializes
     * the XML.
     */
    protected void encodeDetail(Detail detail, XMLStreamWriter writer) {
        serializeReader(new DOMStreamReader(detail), writer);
    }

    /**
     * This method is used to create the appropriate SOAPMessage (1.1 or 1.2 using SAAJ api).
     * @return the BindingID associated with this encoder
     */
    protected String getBindingId(){
        return SOAPBinding.SOAP11HTTP_BINDING;
    }
}
