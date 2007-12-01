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

import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.ws.soap.SOAPBinding;

import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.ws.encoding.soap.message.SOAPFaultInfo;
import com.sun.xml.internal.ws.encoding.soap.message.SOAP12FaultInfo;
import com.sun.xml.internal.ws.encoding.soap.streaming.SOAP12NamespaceConstants;
import com.sun.xml.internal.ws.encoding.soap.streaming.SOAPNamespaceConstants;
import com.sun.xml.internal.ws.encoding.JAXWSAttachmentMarshaller;
import com.sun.xml.internal.ws.util.MessageInfoUtil;
import com.sun.xml.internal.ws.client.BindingProviderProperties;
import com.sun.xml.internal.ws.server.*;

import static com.sun.xml.internal.ws.client.BindingProviderProperties.*;
import com.sun.xml.internal.ws.handler.MessageContextUtil;
import com.sun.xml.internal.ws.spi.runtime.WSConnection;
import javax.xml.ws.handler.MessageContext;

public class SOAP12XMLEncoder extends SOAPXMLEncoder {
    /*
     * @see SOAPEncoder#startEnvelope(XMLStreamWriter)
     */
    @Override
    protected void startEnvelope(XMLStreamWriter writer) {
        try {
            writer.writeStartElement(SOAPNamespaceConstants.NSPREFIX_SOAP_ENVELOPE,
                SOAPNamespaceConstants.TAG_ENVELOPE, SOAP12NamespaceConstants.ENVELOPE);
            writer.setPrefix(SOAPNamespaceConstants.NSPREFIX_SOAP_ENVELOPE,
                             SOAP12NamespaceConstants.ENVELOPE);
            writer.writeNamespace(SOAPNamespaceConstants.NSPREFIX_SOAP_ENVELOPE,
                                  SOAP12NamespaceConstants.ENVELOPE);
        }
        catch (XMLStreamException e) {
            throw new ServerRtException(e);
        }
    }

    /*
     * @see SOAPEncoder#startBody(XMLStreamWriter)
     */
    @Override
    protected void startBody(XMLStreamWriter writer) {
        try {
            writer.writeStartElement(SOAPNamespaceConstants.NSPREFIX_SOAP_ENVELOPE,
                SOAPNamespaceConstants.TAG_BODY, SOAP12NamespaceConstants.ENVELOPE);
        }
        catch (XMLStreamException e) {
            throw new ServerRtException(e);
        }
    }

    /*
     * @see SOAPEncoder#startHeader(XMLStreamWriter)
     */
    @Override
    protected void startHeader(XMLStreamWriter writer) {
        try {
            writer.writeStartElement(SOAPNamespaceConstants.NSPREFIX_SOAP_ENVELOPE,
                SOAPNamespaceConstants.TAG_HEADER, SOAP12NamespaceConstants.ENVELOPE); // <env:Header>
        }
        catch (XMLStreamException e) {
            throw new ServerRtException(e);
        }
    }

    /* (non-Javadoc)
     * @see com.sun.xml.internal.ws.rt.server.SOAPXMLEncoder#writeFault(com.sun.xml.internal.ws.soap.message.SOAPFaultInfo, com.sun.pept.ept.MessageInfo, com.sun.xml.internal.ws.streaming.XMLStreamWriter)
     */
    @Override
    protected void writeFault(SOAPFaultInfo faultInfo, MessageInfo messageInfo, XMLStreamWriter writer) {
        if(!(faultInfo instanceof SOAP12FaultInfo))
            return;
        // Set a status code for Fault
        MessageContext ctxt = MessageInfoUtil.getMessageContext(messageInfo);
        if (MessageContextUtil.getHttpStatusCode(ctxt) == null) {
            MessageContextUtil.setHttpStatusCode(ctxt, WSConnection.INTERNAL_ERR);
        }

        ((SOAP12FaultInfo)faultInfo).write(writer, messageInfo);
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
            return XOP_SOAP12_XML_TYPE_VALUE;
        }
        else {
            return (contentNegotiation == "optimistic") ?
                FAST_INFOSET_TYPE_SOAP12 : SOAP12_XML_CONTENT_TYPE_VALUE;
        }
    }

    /**
     * This method is used to create the appropriate SOAPMessage (1.1 or 1.2 using SAAJ api).
     * @return the BindingID associated with this encoder
     */
    protected String getBindingId(){
        return SOAPBinding.SOAP12HTTP_BINDING;
    }
}
