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
package com.sun.xml.internal.ws.binding.soap;

import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.encoding.soap.streaming.SOAPNamespaceConstants;
import com.sun.xml.internal.ws.encoding.soap.streaming.SOAP12NamespaceConstants;
import com.sun.xml.internal.ws.handler.HandlerChainCaller;
import com.sun.xml.internal.ws.spi.runtime.SystemHandlerDelegate;
import com.sun.xml.internal.ws.spi.runtime.SystemHandlerDelegateFactory;
import com.sun.xml.internal.ws.util.SOAPUtil;

import com.sun.xml.internal.ws.util.localization.Localizable;
import com.sun.xml.internal.ws.util.localization.LocalizableMessageFactory;
import com.sun.xml.internal.ws.util.localization.Localizer;

import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPFactory;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.handler.Handler;
import javax.xml.ws.soap.SOAPBinding;
import javax.xml.namespace.QName;
import java.util.HashSet;
import java.util.List;
import java.util.Set;



/**
 * @author WS Development Team
 */
public class SOAPBindingImpl extends BindingImpl implements SOAPBinding {


    public static final String X_SOAP12HTTP_BINDING =
        "http://java.sun.com/xml/ns/jaxws/2003/05/soap/bindings/HTTP/";

    protected static String ROLE_NONE;

    protected Set<String> requiredRoles;
    protected Set<String> roles;
    protected boolean enableMtom = false;


     // called by DispatchImpl
    public SOAPBindingImpl(String bindingId, QName serviceName) {
        super(bindingId, serviceName);
        setup(getBindingId(), getActualBindingId());
        setupSystemHandlerDelegate(serviceName);
    }

     public SOAPBindingImpl(String bindingId) {
        super(bindingId, null);
        setup(getBindingId(), getActualBindingId());
        setupSystemHandlerDelegate(null);
    }

    public SOAPBindingImpl(List<Handler> handlerChain, String bindingId, QName serviceName) {
        super(handlerChain, bindingId, serviceName);
        setup(getBindingId(), getActualBindingId());
        setupSystemHandlerDelegate(serviceName);
    }

    // if the binding id is unknown, no roles are added
    protected void setup(String bindingId, String actualBindingId) {
        requiredRoles = new HashSet<String>();
        if (bindingId.equals(SOAPBinding.SOAP11HTTP_BINDING)) {
            requiredRoles.add(SOAPNamespaceConstants.ACTOR_NEXT);
        } else if (bindingId.equals(SOAPBinding.SOAP12HTTP_BINDING)) {
            requiredRoles.add(SOAP12NamespaceConstants.ROLE_NEXT);
            requiredRoles.add(SOAP12NamespaceConstants.ROLE_ULTIMATE_RECEIVER);
        }
        ROLE_NONE = SOAP12NamespaceConstants.ROLE_NONE;
        roles = new HashSet<String>();
        addRequiredRoles();
        setRolesOnHandlerChain();
        if (actualBindingId.equals(SOAP11HTTP_MTOM_BINDING)
            || actualBindingId.equals(SOAP12HTTP_MTOM_BINDING)) {
            setMTOMEnabled(true);
        }
    }

    /**
     * For a non standard SOAP1.2 binding, return actual SOAP1.2 binding
     * For SOAP 1.1 MTOM binding, return SOAP1.1 binding
     * For SOAP 1.2 MTOM binding, return SOAP 1.2 binding
     */
    @Override
    public String getBindingId() {
        String bindingId = super.getBindingId();
        if (bindingId.equals(SOAPBindingImpl.X_SOAP12HTTP_BINDING)) {
            return SOAP12HTTP_BINDING;
        }
        if (bindingId.equals(SOAPBinding.SOAP11HTTP_MTOM_BINDING)) {
            return SOAP11HTTP_BINDING;
        }
        if (bindingId.equals(SOAPBinding.SOAP12HTTP_MTOM_BINDING)) {
            return SOAP12HTTP_BINDING;
        }
        return bindingId;
    }

    /*
    * Use this to distinguish SOAP12HTTP_BINDING or X_SOAP12HTTP_BINDING
    */
    @Override
    public String getActualBindingId() {
        return super.getBindingId();
    }

    /*
     * When client sets a new handler chain, must also set roles on
     * the new handler chain caller that gets created.
     */
    public void setHandlerChain(List<Handler> chain) {
        super.setHandlerChain(chain);
        setRolesOnHandlerChain();
    }

    protected void addRequiredRoles() {
        roles.addAll(requiredRoles);
    }

    public java.util.Set<String> getRoles() {
        return roles;
    }

    /*
     * Adds the next and other roles in case this has
     * been called by a user without them.
     */
    public void setRoles(Set<String> roles) {
        if (roles == null) {
            roles = new HashSet<String>();
        }
        if (roles.contains(ROLE_NONE)) {
            LocalizableMessageFactory messageFactory =
                new LocalizableMessageFactory("com.sun.xml.internal.ws.resources.client");
            Localizer localizer = new Localizer();
            Localizable locMessage =
                messageFactory.getMessage("invalid.soap.role.none");
            throw new WebServiceException(localizer.localize(locMessage));
        }
        this.roles = roles;
        addRequiredRoles();
        setRolesOnHandlerChain();
    }


    /**
     * Used typically by the runtime to enable/disable Mtom optimization
     *
     * @return true or false
     */
    public boolean isMTOMEnabled() {
        return enableMtom;
    }

    /**
     * Client application can set if the Mtom optimization should be enabled
     *
     * @param b
     */
    public void setMTOMEnabled(boolean b) {
        this.enableMtom = b;
    }

    public SOAPFactory getSOAPFactory() {
        return SOAPUtil.getSOAPFactory(getBindingId());
    }


    public MessageFactory getMessageFactory() {
        return SOAPUtil.getMessageFactory(getBindingId());
    }

    /**
     * This call defers to the super class to get the
     * handler chain caller. It then sets the roles on the
     * caller before returning it.
     *
     * @see com.sun.xml.internal.ws.binding.BindingImpl#getHandlerChainCaller
     */
    public HandlerChainCaller getHandlerChainCaller() {
        HandlerChainCaller caller = super.getHandlerChainCaller();
        caller.setRoles(roles);
        return chainCaller;
    }

    protected void setRolesOnHandlerChain() {
        if (chainCaller != null) {
            chainCaller.setRoles(roles);
        }
    }

    protected void setupSystemHandlerDelegate(QName serviceName) {
        SystemHandlerDelegateFactory shdFactory =
            SystemHandlerDelegateFactory.getFactory();
        if (shdFactory != null) {
            setSystemHandlerDelegate((SystemHandlerDelegate)
                shdFactory.getDelegate(serviceName));
        }
    }
}
