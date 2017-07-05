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
import java.util.List;
import com.sun.xml.internal.ws.spi.runtime.MessageContext;
import com.sun.xml.internal.ws.encoding.soap.internal.InternalMessage;
import com.sun.xml.internal.ws.encoding.soap.SOAPEPTFactory;
import com.sun.xml.internal.ws.encoding.JAXWSAttachmentMarshaller;
import com.sun.xml.internal.ws.spi.runtime.InternalSoapEncoder;
import com.sun.xml.internal.ws.spi.runtime.Invoker;
import com.sun.xml.internal.ws.util.MessageInfoUtil;

import java.lang.reflect.Method;

/**
 * Implementation of SOAPMessageContext. This class is used at runtime
 * to pass to the handlers for processing soap messages.
 *
 * @see MessageContextImpl
 *
 * @author WS Development Team
 */
public class SHDSOAPMessageContext extends SOAPMessageContextImpl implements com.sun.xml.internal.ws.spi.runtime.SOAPMessageContext {

    SOAPHandlerContext handlerCtxt;

    public SHDSOAPMessageContext(SOAPHandlerContext handlerCtxt) {
        super(handlerCtxt);
        this.handlerCtxt = handlerCtxt;
    }

    /**
     * If there is a SOAPMessage already, use getSOAPMessage(). Ignore all other
     * methods
     */
    public boolean isAlreadySoap() {
        return handlerCtxt.getSOAPMessage() != null;
    }

    /*
     * Returns InternalMessage's BodyBlock value
     */
    public Object getBody() {
        return handlerCtxt.getBody();
    }

    /*
     * Returns InternalMessage's HeaderBlock values
     */
    public List getHeaders() {
        return handlerCtxt.getHeaders();
    }

    /*
     * Use this MessageInfo to pass to InternalSoapEncoder write methods
     */
    public Object getMessageInfo() {
        return handlerCtxt.getMessageInfo();
    }

    /*
     * Encoder to marshall all JAXWS objects: RpcLitPayload, JAXBBridgeInfo etc
     */
    public InternalSoapEncoder getEncoder() {
        return (InternalSoapEncoder)((SOAPEPTFactory)handlerCtxt.getMessageInfo().getEPTFactory()).getSOAPEncoder();
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

    /**
     * Returns if MTOM is anbled
     *
     * @return true if MTOM is enabled otherwise returns false;
     */
    public boolean isMtomEnabled() {
        JAXWSAttachmentMarshaller am = MessageInfoUtil.getAttachmentMarshaller(handlerCtxt.getMessageInfo());
        return (am != null)?am.isXOPPackage():false;
    }

}
