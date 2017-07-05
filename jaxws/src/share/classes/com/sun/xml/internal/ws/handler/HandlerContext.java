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
package com.sun.xml.internal.ws.handler;
import javax.xml.ws.handler.MessageContext;

import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.ws.encoding.soap.internal.InternalMessage;
import com.sun.xml.internal.ws.encoding.soap.internal.HeaderBlock;
import com.sun.xml.internal.ws.encoding.soap.internal.AttachmentBlock;
import com.sun.xml.internal.ws.spi.runtime.InternalSoapEncoder;
import com.sun.xml.internal.ws.spi.runtime.Invoker;
import java.lang.reflect.Method;
import java.util.List;
import java.util.ArrayList;

/**
 * The HandlerContext is used in the client and server runtime
 * in {@link com.sun.xml.internal.ws.protocol.soap.client.SOAPMessageDispatcher} and
 * {@link com.sun.xml.internal.ws.protocol.soap.server.SOAPMessageDispatcher} to hold
 * information about the current message.
 *
 * <p>It stores a {@link com.sun.xml.internal.ws.pept.ept.MessageInfo} and
 * {@link com.sun.xml.internal.ws.encoding.soap.internal.InternalMessage}
 * which are used by the rest of the runtime, and provides a bridge
 * between these and the soap and logical message contexts that
 * are used by the handlers.
 *
 * @see LogicalMessageContextImpl
 * @see MessageContextImpl
 * @see SOAPMessageContextImpl
 *
 * @author WS Development Team
 */
public class HandlerContext {

    private MessageInfo messageInfo;
    private InternalMessage internalMessage;
    private MessageContext msgContext;

    private Method method;
    private Invoker invoker;
    private String algorithm;
    private String bindingId;

    public HandlerContext(MessageInfo messageInfo,
                          InternalMessage internalMessage) {
        this.messageInfo = messageInfo;
        this.internalMessage = internalMessage;
        this.msgContext = new MessageContextImpl();
        //populateAttachmentMap();
    }

    /**
     * @return Returns the soapMessage.
     */
    public MessageContext getMessageContext() {
        return msgContext;
    }

    public void setMessageContext(MessageContext msgContext) {
        this.msgContext = msgContext;
    }

    public InternalMessage getInternalMessage() {
        return internalMessage;
    }

    /**
    * @param internalMessage The internalMessage to set.
    */
    public void setInternalMessage(InternalMessage internalMessage) {
        this.internalMessage = internalMessage;
        populateAttachmentMap();
    }

    public MessageInfo getMessageInfo() {
        return messageInfo;
    }

    /**
    * @param messageInfo The messageInfo to set.
    */
    public void setMessageInfo(MessageInfo messageInfo) {
        this.messageInfo = messageInfo;
    }

    /*
     * Returns the invocation method
     */
    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    /*
    * Returns InternalMessage's BodyBlock value. It can be null for empty body.
    */
    public Object getBody() {
        return (internalMessage == null) ? null : ((internalMessage.getBody() == null)?null:internalMessage.getBody().getValue());
    }

    /*
    * Returns InternalMessage's HeaderBlock values
    */
    public List getHeaders() {
        List<HeaderBlock> headerBlocks =
            (internalMessage == null) ? null : internalMessage.getHeaders();
        if (headerBlocks != null) {
             List headers = new ArrayList();
             for (HeaderBlock headerBlock : headerBlocks) {
                if (headerBlock.getValue() != null) {
                    headers.add(headerBlock.getValue());
                }
             }
             return headers;
        }
        return null;
    }

    public String getBindingId() {
        return bindingId;
    }

    public void setBindingId(String bindingID) {
        bindingId = bindingID;
    }

    public void setCanonicalization(String algorithm) {
        this.algorithm = algorithm;
    }

    public Invoker getInvoker() {
        return invoker;
    }

    public void setInvoker(Invoker invoker) {
        this.invoker = invoker;
    }

    public void populateAttachmentMap(){
        //populate the attachment map
        if(internalMessage != null){
            for(AttachmentBlock ab: internalMessage.getAttachments().values()){
                MessageContextUtil.addMessageAttachment(msgContext, ab.getId(), ab.asDataHandler());
            }
        }
    }

}
