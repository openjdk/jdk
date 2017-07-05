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
package com.sun.xml.internal.ws.protocol.xml.server;

import com.sun.xml.internal.ws.server.provider.ProviderModel;
import javax.xml.ws.Provider;
import javax.xml.ws.Service;
import javax.xml.ws.ServiceMode;
import javax.xml.transform.Source;

import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.ws.pept.presentation.MessageStruct;
import com.sun.xml.internal.ws.encoding.xml.XMLMessage;
import com.sun.xml.internal.ws.handler.XMLHandlerContext;
import com.sun.xml.internal.ws.server.RuntimeContext;
import com.sun.xml.internal.ws.server.RuntimeEndpointInfo;
import com.sun.xml.internal.ws.server.provider.ProviderPeptTie;
import com.sun.xml.internal.ws.util.MessageInfoUtil;
import javax.activation.DataSource;

import static com.sun.xml.internal.ws.client.BindingProviderProperties.*;

/**
 * @author WS Development Team
 *
 */

public class ProviderXMLMD extends XMLMessageDispatcher {

    /*
     * Fill the parameters, method in MessageInfo for Provider interface.
     * invoke(Source, XMLHandlerContext) to Object[]
     * invoke(SOAPMessage, XMLHandlerContext) to Object[]
     */
    @Override
    protected void toMessageInfo(MessageInfo messageInfo, XMLHandlerContext context) {
        Object[] data = new Object[1];
        RuntimeContext rtCtxt = MessageInfoUtil.getRuntimeContext(messageInfo);
        RuntimeEndpointInfo endpointInfo = rtCtxt.getRuntimeEndpointInfo();
        Provider provider = (Provider)endpointInfo.getImplementor();
        Class providerClass = provider.getClass();
        ProviderModel model = endpointInfo.getProviderModel();
        boolean isSource = model.isSource();
        Service.Mode mode = model.getServiceMode();
        XMLMessage xmlMessage = context.getXMLMessage();
        try {
            if (isSource) {
                data[0] = xmlMessage.getSource();
            } else {
                data[0] = xmlMessage.getDataSource();
            }
        } catch(Exception e) {
            messageInfo.setResponseType(MessageStruct.UNCHECKED_EXCEPTION_RESPONSE);
            messageInfo.setResponse(e);
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
            XMLHandlerContext context) {
        Object obj = messageInfo.getResponse();
        RuntimeContext rtCtxt = MessageInfoUtil.getRuntimeContext(messageInfo);
        RuntimeEndpointInfo endpointInfo = rtCtxt.getRuntimeEndpointInfo();
        Provider provider = (Provider)endpointInfo.getImplementor();
        Class providerClass = provider.getClass();

        boolean useFastInfoset =
            messageInfo.getMetaData(CONTENT_NEGOTIATION_PROPERTY) == "optimistic";

        XMLMessage xmlMessage = null;
        if (messageInfo.getResponseType() == MessageInfo.NORMAL_RESPONSE) {
            xmlMessage = (obj instanceof DataSource)
                ? new XMLMessage((DataSource)obj, useFastInfoset)
                : new XMLMessage((Source)obj, useFastInfoset);
        } else {
            xmlMessage = new XMLMessage((Exception)obj, useFastInfoset);
        }
        context.setXMLMessage(xmlMessage);
        context.setInternalMessage(null);
    }

}
