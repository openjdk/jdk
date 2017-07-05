/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.xml.internal.ws.binding;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.client.HandlerConfiguration;
import com.sun.xml.internal.ws.encoding.soap.streaming.SOAP12NamespaceConstants;
import com.sun.xml.internal.ws.resources.ClientMessages;

import javax.xml.namespace.QName;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPFactory;

import javax.xml.ws.WebServiceException;
import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.handler.Handler;
import javax.xml.ws.soap.MTOMFeature;
import javax.xml.ws.soap.SOAPBinding;
import java.util.*;

/**
 * @author WS Development Team
 */
public final class SOAPBindingImpl extends BindingImpl implements SOAPBinding {

    public static final String X_SOAP12HTTP_BINDING =
        "http://java.sun.com/xml/ns/jaxws/2003/05/soap/bindings/HTTP/";

    private static final String ROLE_NONE = SOAP12NamespaceConstants.ROLE_NONE;
    //protected boolean enableMtom;
    protected final SOAPVersion soapVersion;

    private Set<QName> portKnownHeaders = Collections.emptySet();
    private Set<QName> bindingUnderstoodHeaders = new HashSet<QName>();

    /**
     * Use {@link BindingImpl#create(BindingID)} to create this.
     *
     * @param bindingId SOAP binding ID
     */
    SOAPBindingImpl(BindingID bindingId) {
        this(bindingId,EMPTY_FEATURES);
    }

    /**
     * Use {@link BindingImpl#create(BindingID)} to create this.
     *
     * @param bindingId binding id
     * @param features
     *      These features have a precedence over
     *      {@link BindingID#createBuiltinFeatureList() the implicit features}
     *      associated with the {@link BindingID}.
     */
    SOAPBindingImpl(BindingID bindingId, WebServiceFeature... features) {
        super(bindingId, features);
        this.soapVersion = bindingId.getSOAPVersion();
        //populates with required roles and updates handlerConfig
        setRoles(new HashSet<String>());
        //Is this still required? comment out for now
        //setupSystemHandlerDelegate(serviceName);

        this.features.addAll(bindingId.createBuiltinFeatureList());
    }

    /**
     *  This method should be called if the binding has SOAPSEIModel
     *  The Headers understood by the Port are set, so that they can be used for MU
     *  processing.
     *
     * @param headers SOAP header names
     */
    public void setPortKnownHeaders(@NotNull Set<QName> headers) {
        this.portKnownHeaders = headers;
    }

    /**
     * TODO A feature should be created to configure processing of MU headers.
     * @param header
     * @return
     */
    public boolean understandsHeader(QName header) {
        return serviceMode == javax.xml.ws.Service.Mode.MESSAGE
                || portKnownHeaders.contains(header)
                || bindingUnderstoodHeaders.contains(header);

    }

    /**
     * Sets the handlers on the binding and then sorts the handlers in to logical and protocol handlers.
     * Creates a new HandlerConfiguration object and sets it on the BindingImpl. Also parses Headers understood by
     * Protocol Handlers and sets the HandlerConfiguration.
     */
    public void setHandlerChain(List<Handler> chain) {
        setHandlerConfig(new HandlerConfiguration(getHandlerConfig().getRoles(), chain));
    }

    protected void addRequiredRoles(Set<String> roles) {
        roles.addAll(soapVersion.requiredRoles);
    }

    public Set<String> getRoles() {
        return getHandlerConfig().getRoles();
    }

    /**
     * Adds the next and other roles in case this has
     * been called by a user without them.
     * Creates a new HandlerConfiguration object and sets it on the BindingImpl.
     */
    public void setRoles(Set<String> roles) {
        if (roles == null) {
            roles = new HashSet<String>();
        }
        if (roles.contains(ROLE_NONE)) {
            throw new WebServiceException(ClientMessages.INVALID_SOAP_ROLE_NONE());
        }
        addRequiredRoles(roles);
        setHandlerConfig(new HandlerConfiguration(roles, getHandlerConfig()));
    }


    /**
     * Used typically by the runtime to enable/disable Mtom optimization
     */
    public boolean isMTOMEnabled() {
        return isFeatureEnabled(MTOMFeature.class);
    }

    /**
     * Client application can override if the MTOM optimization should be enabled
     */
    public void setMTOMEnabled(boolean b) {
        features.setMTOMEnabled(b);
    }

    public SOAPFactory getSOAPFactory() {
        return soapVersion.getSOAPFactory();
    }

    public MessageFactory getMessageFactory() {
        return soapVersion.getMessageFactory();
    }

}
