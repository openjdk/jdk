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
package com.sun.xml.internal.ws.server;

import java.lang.reflect.Method;

import javax.xml.namespace.QName;

import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.bind.api.BridgeContext;
import com.sun.xml.internal.ws.model.RuntimeModel;
import com.sun.xml.internal.ws.handler.HandlerContext;




/**
 * $author: WS Development Team
 */
public class RuntimeContext {

    public RuntimeContext(RuntimeModel model) {
        this.model = model;
    }

    /**
     * @return Returns the model.
     */
    public RuntimeModel getModel() {
        return model;
    }

    /**
     * @return Returns info about endpoint
     */
    public RuntimeEndpointInfo getRuntimeEndpointInfo() {
        return endpointInfo;
    }

    /**
     * sets info about endpoint
     */
    public void setRuntimeEndpointInfo(RuntimeEndpointInfo endpointInfo) {
        this.endpointInfo = endpointInfo;
    }

    /**
     * @param name
     * @param mi
     * @return the <code>Method</code> associated with the operation named name
     */
    public Method getDispatchMethod(QName name, MessageInfo mi) {
        return getDispatchMethod(name);
    }

    /**
     * @param name
     * @return the <code>Method</code> associated with the operation named name
     */
    public Method getDispatchMethod(QName name){
        return model.getDispatchMethod(name);
    }

    /**
     * @param qname
     * @param mi
     */
    public void setMethodAndMEP(QName qname, MessageInfo mi) {
        if (model != null) {
            mi.setMethod(model.getDispatchMethod(qname));

            // if null, default MEP is ok
            if (qname != null && model.getJavaMethod(qname) != null) {
                mi.setMEP(model.getJavaMethod(qname).getMEP());
            }
        }
    }

    /**
     * @param name
     * @return the decoder Info associated with operation named name
     */
    public Object getDecoderInfo(QName name) {
        return model.getDecoderInfo(name);
    }

    public BridgeContext getBridgeContext() {
        return (model != null)?model.getBridgeContext():null;
    }

    public HandlerContext getHandlerContext() {
        return handlerContext;
    }

    public void setHandlerContext(HandlerContext handlerContext) {
        this.handlerContext = handlerContext;
    }

    private RuntimeModel model;
    private HandlerContext handlerContext;
    private RuntimeEndpointInfo endpointInfo;
}
