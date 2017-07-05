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

import com.sun.xml.internal.ws.spi.runtime.Invoker;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import javax.xml.ws.handler.LogicalMessageContext;
import com.sun.xml.internal.ws.spi.runtime.MessageContext;

import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.ws.encoding.soap.internal.InternalMessage;
import com.sun.xml.internal.ws.encoding.xml.XMLMessage;
import java.lang.reflect.Method;


/**
 * Version of {@link HandlerContext} for XML/HTTP binding that
 * only deals with logical messages.
 *
 * <p>Class has to defer information to HandlerContext so that properties
 * are shared between this and SOAPMessageContext.
 *
 * @see HandlerContext
 *
 * @author WS Development Team
 * @author WS Development Team
 */
public class XMLHandlerContext extends HandlerContext {

    private XMLMessage xmlMessage;
    private LogicalMessageContext logicalContext;
    private SHDXMLMessageContext shdXmlContext;

    public XMLHandlerContext(MessageInfo messageInfo,
            InternalMessage internalMessage,
            XMLMessage xmlMessage) {
        super(messageInfo, internalMessage);
        this.xmlMessage = xmlMessage;
    }

    public LogicalMessageContext getLogicalMessageContext() {
        if (logicalContext == null) {
            logicalContext = new XMLLogicalMessageContextImpl(this);
        }
        return logicalContext;
    }

    /**
     * @return Returns XMLMessage
     */
    public XMLMessage getXMLMessage() {
        return xmlMessage;
    }

    /**
     * @param xmlMessage The xmlMessage to set.
     */
    public void setXMLMessage(XMLMessage xmlMessage) {
        this.xmlMessage = xmlMessage;
    }

    public SHDXMLMessageContext getSHDXMLMessageContext() {
        if (shdXmlContext == null) {
            shdXmlContext = new SHDXMLMessageContext(this);
        }
        return shdXmlContext;
    }

    private static class SHDXMLMessageContext extends XMLLogicalMessageContextImpl implements com.sun.xml.internal.ws.spi.runtime.MessageContext {

        XMLHandlerContext handlerCtxt;

        public SHDXMLMessageContext(XMLHandlerContext handlerCtxt) {
            super(handlerCtxt);
            this.handlerCtxt = handlerCtxt;
        }

        public String getBindingId() {
            return handlerCtxt.getBindingId();
        }

        public Method getMethod() {
            return handlerCtxt.getMethod();
        }

        public void setCanonicalization(String algorithm) {
            handlerCtxt.setCanonicalization(algorithm);
        }

        public Invoker getInvoker() {
            return handlerCtxt.getInvoker();
        }

        public boolean isMtomEnabled() {
            return false;
        }

    }

}
