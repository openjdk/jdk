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

package com.sun.xml.internal.ws.binding;

import com.sun.xml.internal.ws.binding.http.HTTPBindingImpl;
import com.sun.xml.internal.ws.binding.soap.SOAPBindingImpl;
import com.sun.xml.internal.ws.handler.HandlerChainCaller;
import com.sun.xml.internal.ws.modeler.RuntimeModeler;
import com.sun.xml.internal.ws.spi.runtime.SystemHandlerDelegate;

import javax.xml.ws.Binding;
import javax.xml.ws.handler.Handler;
import java.util.ArrayList;
import java.util.List;
import javax.xml.ws.http.HTTPBinding;
import javax.xml.ws.soap.SOAPBinding;
import javax.xml.namespace.QName;

/**
 * Instances are created by the service, which then
 * sets the handler chain on the binding impl. The handler
 * caller class actually creates and manages the handlers.
 *
 * <p>Also used on the server side, where non-api calls such as
 * getHandlerChainCaller cannot be used. So the binding impl
 * now stores the handler list rather than deferring to the
 * handler chain caller.
 *
 * <p>This class is made abstract as we dont see a situation when a BindingImpl has much meaning without binding id.
 * IOw, for a specific binding there will be a class extending BindingImpl, for example SOAPBindingImpl.
 *
 * <p>The spi Binding interface extends Binding.
 *
 * @author WS Development Team
 */
public abstract class BindingImpl implements
    com.sun.xml.internal.ws.spi.runtime.Binding {

    // caller ignored on server side
    protected HandlerChainCaller chainCaller;

    private SystemHandlerDelegate systemHandlerDelegate;
    private List<Handler> handlers;
    private String bindingId;
    protected QName serviceName;

   // called by DispatchImpl
    public BindingImpl(String bindingId, QName serviceName) {
        this.bindingId = bindingId;
        this.serviceName = serviceName;
    }

    public BindingImpl(List<Handler> handlerChain, String bindingId, QName serviceName) {
        handlers = handlerChain;
        this.bindingId = bindingId;
        this.serviceName = serviceName;
    }


    /**
     * Return a copy of the list. If there is a handler chain caller,
     * this is the proper list. Otherwise, return a copy of 'handlers'
     * or null if list is null. The RuntimeEndpointInfo.init() method
     * relies on this list being null if there were no handlers
     * in the deployment descriptor file.
     *
     * @return The list of handlers. This can be null if there are
     * no handlers. The list may have a different order depending on
     * whether or not the handlers have been called yet, since the
     * logical and protocol handlers will be sorted before calling them.
     *
     * @see com.sun.xml.internal.ws.server.RuntimeEndpointInfo#init
     */
    public List<Handler> getHandlerChain() {
        if (chainCaller != null) {
            return new ArrayList(chainCaller.getHandlerChain());
        }
        if (handlers == null) {
            return null;
        }
        return new ArrayList(handlers);
    }

    public boolean hasHandlers() {
        if (handlers == null || handlers.size() == 0) {
            return false;
        }
        return true;
    }

    /**
     * Sets the handlers on the binding. If the handler chain
     * caller already exists, then the handlers will be set on
     * the caller and the handler chain held by the binding will
     * be the sorted list.
     */
    public void setHandlerChain(List<Handler> chain) {
        if (chainCaller != null) {
            chainCaller = new HandlerChainCaller(chain);
            handlers = chainCaller.getHandlerChain();
        } else {
            handlers = chain;
        }
    }

    /**
     * Creates the handler chain caller if needed and returns
     * it. Once the handler chain caller exists, this class
     * defers getHandlers() calls to it to get the new sorted
     * list of handlers.
     */
    public HandlerChainCaller getHandlerChainCaller() {
        if (chainCaller == null) {
            chainCaller = new HandlerChainCaller(handlers);
        }
        return chainCaller;
    }

    public String getBindingId(){
        return bindingId;
    }

    public String getActualBindingId() {
        return bindingId;
    }

    public void setServiceName(QName serviceName){
        this.serviceName = serviceName;
    }

    public SystemHandlerDelegate getSystemHandlerDelegate() {
        return systemHandlerDelegate;
    }

    public void setSystemHandlerDelegate(SystemHandlerDelegate delegate) {
        systemHandlerDelegate = delegate;
    }

    public static com.sun.xml.internal.ws.spi.runtime.Binding getBinding(String bindingId,
                                                                Class implementorClass, QName serviceName, boolean tokensOK) {

        if (bindingId == null) {
            // Gets bindingId from @BindingType annotation
            bindingId = RuntimeModeler.getBindingId(implementorClass);
            if (bindingId == null) {            // Default one
                bindingId = SOAPBinding.SOAP11HTTP_BINDING;
            }
        }
        if (tokensOK) {
            if (bindingId.equals("##SOAP11_HTTP")) {
                bindingId = SOAPBinding.SOAP11HTTP_BINDING;
            } else if (bindingId.equals("##SOAP11_HTTP_MTOM")) {
                bindingId = SOAPBinding.SOAP11HTTP_MTOM_BINDING;
            } else if (bindingId.equals("##SOAP12_HTTP")) {
                bindingId = SOAPBinding.SOAP12HTTP_BINDING;
            } else if (bindingId.equals("##SOAP12_HTTP_MTOM")) {
                bindingId = SOAPBinding.SOAP12HTTP_MTOM_BINDING;
            } else if (bindingId.equals("##XML_HTTP")) {
                bindingId = HTTPBinding.HTTP_BINDING;
            }
        }
        if (bindingId.equals(SOAPBinding.SOAP11HTTP_BINDING)
            || bindingId.equals(SOAPBinding.SOAP11HTTP_MTOM_BINDING)
            || bindingId.equals(SOAPBinding.SOAP12HTTP_BINDING)
            || bindingId.equals(SOAPBinding.SOAP12HTTP_MTOM_BINDING)
            || bindingId.equals(SOAPBindingImpl.X_SOAP12HTTP_BINDING)) {
            return new SOAPBindingImpl(bindingId, serviceName);
        } else if (bindingId.equals(HTTPBinding.HTTP_BINDING)) {
            return new HTTPBindingImpl();
        } else {
            throw new IllegalArgumentException("Wrong bindingId "+bindingId);
        }
    }

    public static Binding getDefaultBinding() {
        return new SOAPBindingImpl(SOAPBinding.SOAP11HTTP_BINDING);
    }

    public static Binding getDefaultBinding(QName serviceName) {
        return new SOAPBindingImpl(SOAPBinding.SOAP11HTTP_BINDING, serviceName);
    }
}
