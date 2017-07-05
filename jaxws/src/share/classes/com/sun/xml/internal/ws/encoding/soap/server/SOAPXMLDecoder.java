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

import com.sun.xml.internal.ws.handler.MessageContextUtil;
import com.sun.xml.internal.ws.model.JavaMethod;
import com.sun.xml.internal.ws.model.RuntimeModel;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.ws.soap.SOAPFaultException;
import javax.xml.ws.soap.SOAPBinding;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.Source;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamConstants;

import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.ws.encoding.soap.SOAPConstants;
import com.sun.xml.internal.ws.encoding.soap.SOAPDecoder;
import com.sun.xml.internal.ws.encoding.soap.internal.InternalMessage;
import com.sun.xml.internal.ws.encoding.soap.message.SOAPFaultInfo;
import com.sun.xml.internal.ws.handler.HandlerContext;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderUtil;
import com.sun.xml.internal.ws.streaming.SourceReaderFactory;
import com.sun.xml.internal.ws.util.MessageInfoUtil;
import com.sun.xml.internal.ws.util.SOAPUtil;
import com.sun.xml.internal.ws.server.*;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderException;
import javax.xml.stream.XMLStreamException;
import javax.xml.ws.handler.MessageContext;


/**
 * @author WS Development Team
 */
public class SOAPXMLDecoder extends SOAPDecoder {

    private static final Set<String> requiredRoles = new HashSet<String>();
    private static final QName emptyBodyName = new QName("");

    public SOAPXMLDecoder() {
        requiredRoles.add("http://schemas.xmlsoap.org/soap/actor/next");
        requiredRoles.add("");
    }

    /*
     *
     * @throws ServerRtException
     * @see SOAPDecoder#toInternalMessage(SOAPMessage)
     */
    public InternalMessage toInternalMessage(SOAPMessage soapMessage, MessageInfo messageInfo) {
        // TODO handle exceptions, attachments
        XMLStreamReader reader = null;
        try {
            InternalMessage request = new InternalMessage();
            processAttachments(messageInfo, request, soapMessage);
            Source source = soapMessage.getSOAPPart().getContent();
            reader = SourceReaderFactory.createSourceReader(source, true,getSOAPMessageCharsetEncoding(soapMessage));
            XMLStreamReaderUtil.nextElementContent(reader);
            decodeEnvelope(reader, request, false, messageInfo);
            return request;
        } catch(Exception e) {
            if (isBadXML(e)) {
                RuntimeContext rtCtxt = MessageInfoUtil.getRuntimeContext(messageInfo);
                HandlerContext handlerCtxt = rtCtxt.getHandlerContext();
                raiseBadXMLFault(handlerCtxt);
            }
            throw new ServerRtException("soapdecoder.err", new Object[]{e});
        } finally {
            if (reader != null) {
                XMLStreamReaderUtil.close(reader);
            }
        }
    }

    protected boolean isBadXML(Exception e) {
        while (e != null) {
            if (e instanceof XMLStreamException) {
                return true;
            }
            e = (e.getCause() instanceof Exception) ? (Exception)e.getCause() : null;
        }
        return false;
    }


    /*
     * Headers from SOAPMesssage are mapped to HeaderBlocks in InternalMessage
     * Body from SOAPMessage is skipped
     * BodyBlock in InternalMessage is converted to JAXBTypeInfo or RpcLitPayload
     *
     * @throws ServerRtException
     * @see SOAPDecoder#toInternalMessage(SOAPMessage, InternalMessage)
     */
    public InternalMessage toInternalMessage(SOAPMessage soapMessage,
            InternalMessage request, MessageInfo messageInfo) {

        // TODO handle exceptions, attachments
        XMLStreamReader reader = null;
        try {
            processAttachments(messageInfo, request, soapMessage);
            Source source = soapMessage.getSOAPPart().getContent();
            reader = SourceReaderFactory.createSourceReader(source, true,getSOAPMessageCharsetEncoding(soapMessage));
            XMLStreamReaderUtil.nextElementContent(reader);
            decodeEnvelope(reader, request, true, messageInfo);
            convertBodyBlock(request, messageInfo);
        } catch(Exception e) {
            if (isBadXML(e)) {
                RuntimeContext rtCtxt = MessageInfoUtil.getRuntimeContext(messageInfo);
                HandlerContext handlerCtxt = rtCtxt.getHandlerContext();
                raiseBadXMLFault(handlerCtxt);
            }
            throw new ServerRtException("soapdecoder.err", new Object[]{e});
        } finally {
            if (reader != null) {
                XMLStreamReaderUtil.close(reader);
            }
        }
        return request;
    }

    @Override
    public void decodeDispatchMethod(XMLStreamReader reader, InternalMessage request, MessageInfo messageInfo) {
        // Operation's QName. takes care of <body/>
        QName name = (reader.getEventType() == XMLStreamConstants.START_ELEMENT) ? reader.getName() : emptyBodyName;
        RuntimeContext rtCtxt = MessageInfoUtil.getRuntimeContext(messageInfo);
        RuntimeModel rtModel = rtCtxt.getModel();
        JavaMethod javaMethod = rtModel.getJavaMethod(name);
        Method method = (javaMethod == null) ? null : javaMethod.getMethod();
        if (method == null) {
            raiseFault(getSenderFaultCode(), "Cannot find the dispatch method");
        }
        MessageContext msgCtxt = MessageInfoUtil.getMessageContext(messageInfo);
        if (msgCtxt != null) {
            String opNsUri = rtModel.getPortTypeName().getNamespaceURI();
            String opName = javaMethod.getOperationName();
            MessageContextUtil.setWsdlOperation(msgCtxt, new QName(opNsUri, opName));
        }
        messageInfo.setMethod(method);
    }

    protected SOAPFaultInfo decodeFault(XMLStreamReader reader, InternalMessage internalMessage,
        MessageInfo messageInfo) {
        raiseFault(getSenderFaultCode(), "Server cannot handle fault message");
        return null;
    }

    @Override
    protected void raiseBadXMLFault(HandlerContext ctxt) {
        MessageContextUtil.setHttpStatusCode(ctxt.getMessageContext(), 400);
        raiseFault(getSenderFaultCode(), "Bad request");
    }

    public Set<String> getRequiredRoles() {
        return requiredRoles;
    }

    @Override
    public String getBindingId() {
        return SOAPBinding.SOAP11HTTP_BINDING;
    }

    @Override
    protected QName getSenderFaultCode() {
        return SOAPConstants.FAULT_CODE_CLIENT;
    }

    @Override
    protected QName getReceiverFaultCode() {
        return SOAPConstants.FAULT_CODE_SERVER;
    }

    @Override
    protected QName getVersionMismatchFaultCode() {
        return SOAPConstants.FAULT_CODE_VERSION_MISMATCH;
    }

}
