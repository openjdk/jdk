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
package com.sun.xml.internal.ws.util;

import com.sun.xml.internal.bind.api.BridgeContext;
import com.sun.xml.internal.ws.client.BindingProviderProperties;
import com.sun.xml.internal.ws.encoding.JAXWSAttachmentMarshaller;
import com.sun.xml.internal.ws.encoding.soap.SOAPDecoder;
import com.sun.xml.internal.ws.encoding.soap.internal.HeaderBlock;
import com.sun.xml.internal.ws.handler.HandlerChainCaller;
import com.sun.xml.internal.ws.handler.HandlerContext;
import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.ws.server.RuntimeContext;

import javax.xml.ws.handler.MessageContext;
import javax.xml.bind.Marshaller;
import java.util.Set;

/**
 * @author WS RI Development Team
 */
public class MessageInfoUtil {

    public static void setRuntimeContext(MessageInfo messageInfo,
                                         RuntimeContext runtimeContext) {
        messageInfo.setMetaData(BindingProviderProperties.JAXWS_RUNTIME_CONTEXT, runtimeContext);
    }

    public static RuntimeContext getRuntimeContext(MessageInfo messageInfo) {
        return (RuntimeContext) messageInfo.getMetaData(BindingProviderProperties.JAXWS_RUNTIME_CONTEXT);
    }

    public static MessageContext getMessageContext(MessageInfo messageInfo) {
        RuntimeContext rtCtxt = getRuntimeContext(messageInfo);
        HandlerContext hdCtxt = null;
        if (rtCtxt != null)
            hdCtxt = rtCtxt.getHandlerContext();
        else
            hdCtxt = (HandlerContext)
                messageInfo.getMetaData(BindingProviderProperties.JAXWS_HANDLER_CONTEXT_PROPERTY);
        return (hdCtxt == null) ? null : hdCtxt.getMessageContext();
    }

    public static HandlerChainCaller getHandlerChainCaller(
        MessageInfo messageInfo) {
        return (HandlerChainCaller) messageInfo.getMetaData(
            HandlerChainCaller.HANDLER_CHAIN_CALLER);
    }

    public static void setHandlerChainCaller(MessageInfo messageInfo,
                                             HandlerChainCaller caller) {
        messageInfo.setMetaData(HandlerChainCaller.HANDLER_CHAIN_CALLER,
            caller);
    }

    public static JAXWSAttachmentMarshaller getAttachmentMarshaller(MessageInfo messageInfo) {
        Object rtc = messageInfo.getMetaData(BindingProviderProperties.JAXWS_RUNTIME_CONTEXT);
        if (rtc != null) {
            BridgeContext bc = ((RuntimeContext) rtc).getBridgeContext();
            if (bc != null) {
                return (JAXWSAttachmentMarshaller) bc.getAttachmentMarshaller();
            }
        } else {
             Marshaller m = (Marshaller)messageInfo.getMetaData(BindingProviderProperties.DISPATCH_MARSHALLER);
            if (m != null) {
                return (JAXWSAttachmentMarshaller) m.getAttachmentMarshaller();
            }
        }
        return null;
    }

    public static void setNotUnderstoodHeaders(MessageInfo messageInfo,
                                               Set<HeaderBlock> headers) {

        messageInfo.setMetaData(SOAPDecoder.NOT_UNDERSTOOD_HEADERS, headers);
    }

    public static Set<HeaderBlock> getNotUnderstoodHeaders(
        MessageInfo messageInfo) {

        return (Set<HeaderBlock>) messageInfo.getMetaData(
            SOAPDecoder.NOT_UNDERSTOOD_HEADERS);
    }
}
