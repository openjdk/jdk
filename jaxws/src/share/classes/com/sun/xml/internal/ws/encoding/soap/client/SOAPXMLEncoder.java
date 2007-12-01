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
package com.sun.xml.internal.ws.encoding.soap.client;

import com.sun.xml.internal.ws.client.BindingProviderProperties;
import static com.sun.xml.internal.ws.client.BindingProviderProperties.*;
import com.sun.xml.internal.ws.client.RequestContext;
import com.sun.xml.internal.ws.client.SenderException;
import com.sun.xml.internal.ws.client.dispatch.DispatchContext;
import com.sun.xml.internal.ws.encoding.JAXWSAttachmentMarshaller;
import com.sun.xml.internal.ws.encoding.internal.InternalEncoder;
import com.sun.xml.internal.ws.encoding.jaxb.JAXBBeanInfo;
import com.sun.xml.internal.ws.encoding.soap.SOAPConstants;
import com.sun.xml.internal.ws.encoding.soap.SOAPEPTFactory;
import com.sun.xml.internal.ws.encoding.soap.SOAPEncoder;
import com.sun.xml.internal.ws.encoding.soap.internal.AttachmentBlock;
import com.sun.xml.internal.ws.encoding.soap.internal.BodyBlock;
import com.sun.xml.internal.ws.encoding.soap.internal.InternalMessage;
import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.ws.streaming.XMLStreamWriterFactory;
import com.sun.xml.internal.ws.util.ByteArrayBuffer;
import com.sun.xml.internal.ws.util.MessageInfoUtil;
import com.sun.xml.internal.ws.util.SOAPUtil;

import javax.activation.DataHandler;
import javax.xml.bind.JAXBContext;
import javax.xml.namespace.QName;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;
import javax.xml.ws.Service;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.soap.SOAPBinding;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;
import static java.util.logging.Logger.getLogger;

/**
 * @author WS Development Team
 */
public class SOAPXMLEncoder extends SOAPEncoder {

    private static final Logger logger =
        getLogger(new StringBuffer().append(com.sun.xml.internal.ws.util.Constants.LoggingDomain).append(".client.dispatch.util").toString());

    public SOAPXMLEncoder() {
    }
    /* moved to super
    protected JAXBContext getJAXBContext(MessageInfo messageInfo) {
        JAXBContext jc = null;
        RequestContext context = (RequestContext) messageInfo.getMetaData(BindingProviderProperties.JAXWS_CONTEXT_PROPERTY);
        if (context != null)
            jc = (JAXBContext) context.get(BindingProviderProperties.JAXB_CONTEXT_PROPERTY);

        return jc;
    }
    */
    protected boolean skipHeader(MessageInfo messageInfo) {
        if (messageInfo.getMetaData(DispatchContext.DISPATCH_MESSAGE_MODE) ==
            Service.Mode.PAYLOAD) {
            return true;
        }
        return false;
    }

    protected QName getHeaderTag() {
        return SOAPConstants.QNAME_SOAP_HEADER;
    }

    protected void skipHeader(XMLStreamReader writer) {
        //XMLStreamReaderUtil.verifyReaderState(reader, START_ELEMENT);
        //if (!SOAPNamespaceConstants.TAG_HEADER.equals(reader.getLocalName())) {
        //  return;
        //}
        //XMLStreamReaderUtil.verifyTag(reader, getHeaderTag());
        //XMLStreamReaderUtil.skipElement(reader);    // Moves to </Header>
        //XMLStreamReaderUtil.nextElementContent(reader);
    }

    @Override
    public InternalMessage toInternalMessage(MessageInfo messageInfo) {
        InternalMessage internalMessage = new InternalMessage();
        DispatchContext context = (DispatchContext) messageInfo.getMetaData(BindingProviderProperties.DISPATCH_CONTEXT);
        if (context != null) {
            DispatchContext.MessageType type =
                (DispatchContext.MessageType) context.getProperty(DispatchContext.DISPATCH_MESSAGE);
            Object[] data = messageInfo.getData();
            BodyBlock bodyBlock = null;
            switch (type) {
                case JAXB_MESSAGE:
                    break;
                case JAXB_PAYLOAD:
                    JAXBBeanInfo jaxbInfo = new JAXBBeanInfo(data[0], getJAXBContext(messageInfo));
                    bodyBlock = new BodyBlock(jaxbInfo);
                    break;
                case SOURCE_PAYLOAD:
                    data = messageInfo.getData();
                    bodyBlock = new BodyBlock((Source) data[0]);
                    break;
                default:
            }
            if (bodyBlock != null)
                internalMessage.setBody(bodyBlock);

            //look for attachments here
            Map<String, DataHandler> attMap = (Map<String, DataHandler>) ((Map<String, Object>)
                messageInfo.getMetaData(BindingProviderProperties.JAXWS_CONTEXT_PROPERTY)).get(MessageContext.OUTBOUND_MESSAGE_ATTACHMENTS);

            if (attMap != null)
                for (Map.Entry<String, DataHandler> att : attMap.entrySet()) {
                    internalMessage.addAttachment(AttachmentBlock.fromDataHandler(att.getKey(), att.getValue()));
                }

        } else {
            SOAPEPTFactory eptf = (SOAPEPTFactory) messageInfo.getEPTFactory();
            InternalEncoder internalEncoder = eptf.getInternalEncoder();
            //processProperties(messageInfo);
            return (InternalMessage) internalEncoder.toInternalMessage(messageInfo);
        }
        return internalMessage;
    }

    @Override
    public SOAPMessage toSOAPMessage(InternalMessage internalMessage,
                                     MessageInfo messageInfo) {
        SOAPMessage message = null;
        XMLStreamWriter writer = null;
        JAXWSAttachmentMarshaller marshaller = null;
        boolean xopEnabled = false;

        try {
            setAttachmentsMap(messageInfo, internalMessage);
            ByteArrayBuffer bab = new ByteArrayBuffer();

            if (messageInfo.getMetaData(CONTENT_NEGOTIATION_PROPERTY) == "optimistic")
            {
                writer = XMLStreamWriterFactory.createFIStreamWriter(bab);

                // Turn XOP off for FI
                marshaller = MessageInfoUtil.getAttachmentMarshaller(messageInfo);
                if (marshaller != null) {
                    xopEnabled = marshaller.isXOPPackage();     // last value
                    marshaller.setXOPPackage(false);
                }

            } else {
                // Store output stream to use in JAXB bridge (not with FI)
                messageInfo.setMetaData(JAXB_OUTPUTSTREAM, bab);
                writer = XMLStreamWriterFactory.createXMLStreamWriter(bab);
            }

            writer.writeStartDocument();
            startEnvelope(writer);
            writeEnvelopeNamespaces(writer, messageInfo);
            if (!skipHeader(messageInfo))
                writeHeaders(writer, internalMessage, messageInfo);
            writeBody(writer, internalMessage, messageInfo);
            endEnvelope(writer);
            writer.writeEndDocument();
            writer.close();

            // TODO: Copy the mime headers from messageInfo.METADATA
            MimeHeaders mh = new MimeHeaders();
            mh.addHeader("Content-Type", getContentType(messageInfo, marshaller));
            message = SOAPUtil.createMessage(mh, bab.newInputStream(), getBindingId());
            processAttachments(internalMessage, message);

            // Restore default XOP processing before returning
            if (marshaller != null) {
                marshaller.setXOPPackage(xopEnabled);
            }
        }
        catch (IOException e) {
            throw new SenderException("sender.request.messageNotReady", e);
        }
        catch (SOAPException e) {
            throw new SenderException(e);
        }
        catch (XMLStreamException e) {
            throw new SenderException(e);
        }
        finally {
            if (writer != null) {
                try {
                    writer.close();
                }
                catch (XMLStreamException e) {
                    throw new SenderException(e);
                }
            }
        }

        return message;
    }

    public InternalMessage createInternalMessage(MessageInfo messageInfo) {

        InternalMessage internalMessage = new InternalMessage();
        Object response = messageInfo.getResponse();

        BodyBlock bodyBlock = null;
        if (getJAXBContext(messageInfo) != null) {
            JAXBBeanInfo jaxbBean = new JAXBBeanInfo(response, getJAXBContext(messageInfo));
            bodyBlock = new BodyBlock(jaxbBean);
        } else if (response instanceof Source) {
            bodyBlock = new BodyBlock((Source) response);
        }

        internalMessage.setBody(bodyBlock);
        return internalMessage;
    }

    protected String getContentType(MessageInfo messageInfo,
                                    JAXWSAttachmentMarshaller marshaller) {
        String contentNegotiation = (String)
            messageInfo.getMetaData(BindingProviderProperties.CONTENT_NEGOTIATION_PROPERTY);

        if (marshaller == null) {
            marshaller = MessageInfoUtil.getAttachmentMarshaller(messageInfo);
        }

        if (marshaller != null && marshaller.isXopped()) {
            return XOP_SOAP11_XML_TYPE_VALUE;
        } else {
            return (contentNegotiation == "optimistic") ?
                FAST_INFOSET_TYPE_SOAP11 : XML_CONTENT_TYPE_VALUE;
        }
    }

    /**
     * This method is used to create the appropriate SOAPMessage (1.1 or 1.2 using SAAJ api).
     *
     * @return the BindingID associated with this encoder
     */
    protected String getBindingId() {
        return SOAPBinding.SOAP11HTTP_BINDING;
    }
}
