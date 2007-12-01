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
package com.sun.xml.internal.ws.protocol.soap.server;

import javax.xml.ws.Service;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.Source;

import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.ws.pept.presentation.MessageStruct;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.encoding.internal.InternalEncoder;
import com.sun.xml.internal.ws.handler.SOAPHandlerContext;
import com.sun.xml.internal.ws.handler.LogicalMessageImpl;
import com.sun.xml.internal.ws.encoding.soap.internal.InternalMessage;
import com.sun.xml.internal.ws.encoding.soap.SOAPEPTFactory;
import com.sun.xml.internal.ws.encoding.soap.SOAPEncoder;
import com.sun.xml.internal.ws.server.RuntimeContext;
import com.sun.xml.internal.ws.server.RuntimeEndpointInfo;
import com.sun.xml.internal.ws.server.ServerRtException;
import com.sun.xml.internal.ws.util.MessageInfoUtil;
import com.sun.xml.internal.ws.util.SOAPUtil;
import com.sun.xml.internal.ws.util.FastInfosetUtil;

import static com.sun.xml.internal.ws.developer.JAXWSProperties.*;
import com.sun.xml.internal.ws.server.provider.ProviderModel;
import com.sun.xml.internal.ws.server.provider.ProviderPeptTie;

public class ProviderSOAPMD extends SOAPMessageDispatcher {

    /*
     * Fill the parameters, method in MessageInfo for Provider interface.
     * invoke(Source, HandlerContext) to Object[]
     * invoke(SOAPMessage, HandlerContext) to Object[]
     */
    @Override
    protected void toMessageInfo(MessageInfo messageInfo, SOAPHandlerContext context) {
        Object[] data = new Object[1];
        RuntimeContext rtCtxt = MessageInfoUtil.getRuntimeContext(messageInfo);
        RuntimeEndpointInfo endpointInfo = rtCtxt.getRuntimeEndpointInfo();
        Class providerClass = endpointInfo.getImplementorClass();
        ProviderModel model = endpointInfo.getProviderModel();
        boolean isSource = model.isSource();
        Service.Mode mode = model.getServiceMode();

        if (mode == Service.Mode.PAYLOAD) {
            if (isSource) {
                data[0] = new LogicalMessageImpl(context).getPayload();
            }
            // else doesn't happen and it is checked while creating the model
        } else {
            InternalMessage internalMessage = context.getInternalMessage();
            SOAPMessage soapMessage = context.getSOAPMessage();
            try {
                if (internalMessage != null) {
                    // SOAPMessage's body is replaced by InternalMessage's BodyBlock
                    SOAPEPTFactory eptf = (SOAPEPTFactory)messageInfo.getEPTFactory();
                    SOAPEncoder encoder = eptf.getSOAPEncoder();
                    soapMessage = encoder.toSOAPMessage(internalMessage, soapMessage);
                }
                if (isSource) {
                    // Get SOAPMessage's SOAPPart as Source
                    data[0]= soapMessage.getSOAPPart().getContent();
                } else {
                    data[0] = soapMessage;
                }
            } catch(Exception e) {
                messageInfo.setResponseType(MessageStruct.UNCHECKED_EXCEPTION_RESPONSE);
                messageInfo.setResponse(e);
            }
        }
        messageInfo.setData(data);
        messageInfo.setMethod(ProviderPeptTie.invoke_Method);
    }

    /*
     * MessageInfo contains the endpoint invocation results. If the endpoint
     * returns a SOAPMessage, just set the object in HandlerContext. If the
     * endpoint returns a Source in Mode.MESSAGE, it is converted to SOAPMessage
     * and set in HandlerContext. If the endpoint returns a Source in
     * Mode.PAYLOAD, it is set in InternalMessage, and InternalMessage is set
     * in HandlerContext
     */
    @Override
    protected void setResponseInContext(MessageInfo messageInfo,
            SOAPHandlerContext context)
    {
        Object obj = messageInfo.getResponse();
        RuntimeContext rtCtxt = MessageInfoUtil.getRuntimeContext(messageInfo);
        RuntimeEndpointInfo endpointInfo = rtCtxt.getRuntimeEndpointInfo();
        Class providerClass = endpointInfo.getImplementorClass();
        ProviderModel model = endpointInfo.getProviderModel();
        Service.Mode mode = model.getServiceMode();

        if (messageInfo.getResponseType() == MessageInfo.NORMAL_RESPONSE &&
                mode == Service.Mode.MESSAGE) {
            SOAPMessage soapMessage = null;
            if (obj instanceof SOAPMessage) {
                soapMessage = (SOAPMessage)obj;
            } else {
                // put Source into SOAPPart of SOAPMessage
                try {
                    Source source = (Source)obj;
                    String bindingId = ((BindingImpl)endpointInfo.getBinding()).getBindingId();
                    soapMessage = SOAPUtil.createMessage(bindingId);
                    soapMessage.getSOAPPart().setContent(source);
                } catch(SOAPException e) {
                    throw new ServerRtException("soapencoder.err", new Object[]{e});
                }
            }

            // Ensure message is encoded according to conneg
            FastInfosetUtil.ensureCorrectEncoding(messageInfo, soapMessage);

            context.setSOAPMessage(soapMessage);
            context.setInternalMessage(null);
        }
        else {
            // set Source or any Exception in InternalMessage's BodyBlock
            SOAPEPTFactory eptf = (SOAPEPTFactory)messageInfo.getEPTFactory();
            InternalEncoder ine = eptf.getInternalEncoder();
            InternalMessage internalMessage =
                (InternalMessage)ine.toInternalMessage(messageInfo);
            // set handler context
            context.setInternalMessage(internalMessage);
            context.setSOAPMessage(null);
        }
    }

}
