/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.handler.MessageHandler;
import com.sun.xml.internal.ws.client.HandlerConfiguration;
import com.sun.xml.internal.ws.encoding.soap.streaming.SOAP12NamespaceConstants;
import com.sun.xml.internal.ws.handler.HandlerException;
import com.sun.xml.internal.ws.resources.ClientMessages;

import javax.xml.namespace.QName;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPFactory;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.handler.Handler;
import javax.xml.ws.handler.LogicalHandler;
import javax.xml.ws.handler.soap.SOAPHandler;
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
    private Set<String> roles;
    //protected boolean enableMtom;
    protected final SOAPVersion soapVersion;

    private Set<QName> portKnownHeaders = Collections.emptySet();

    /**
     * Use {@link BindingImpl#create(BindingID)} to create this.
     */
    SOAPBindingImpl(BindingID bindingId) {
        this(bindingId,EMPTY_FEATURES);
    }

    /**
     * Use {@link BindingImpl#create(BindingID)} to create this.
     *
     * @param features
     *      These features have a precedence over
     *      {@link BindingID#createBuiltinFeatureList() the implicit features}
     *      associated with the {@link BindingID}.
     */
    SOAPBindingImpl(BindingID bindingId, WebServiceFeature... features) {
        super(bindingId);
        this.soapVersion = bindingId.getSOAPVersion();
        roles = new HashSet<String>();
        addRequiredRoles();
        //Is this still required? comment out for now
        //setupSystemHandlerDelegate(serviceName);

        setFeatures(features);
        this.features.addAll(bindingId.createBuiltinFeatureList());
    }

    /**
     *  This method should be called if the binding has SOAPSEIModel
     *  The Headers understood by the Port are set, so that they can be used for MU
     *  processing.
     *
     * @param headers
     */
    public void setPortKnownHeaders(@NotNull Set<QName> headers) {
        this.portKnownHeaders = headers;
        // apply this change to HandlerConfiguration
        setHandlerConfig(createHandlerConfig(getHandlerChain()));
    }

    /**
     * This method separates the logical and protocol handlers.
     * Also parses Headers understood by SOAPHandlers and
     * sets the HandlerConfiguration.
     */
    protected HandlerConfiguration createHandlerConfig(List<Handler> handlerChain) {
        List<LogicalHandler> logicalHandlers = new ArrayList<LogicalHandler>();
        List<SOAPHandler> soapHandlers = new ArrayList<SOAPHandler>();
        List<MessageHandler> messageHandlers = new ArrayList<MessageHandler>();
        Set<QName> handlerKnownHeaders = new HashSet<QName>();

        for (Handler handler : handlerChain) {
            if (handler instanceof LogicalHandler) {
                logicalHandlers.add((LogicalHandler) handler);
            } else if (handler instanceof SOAPHandler) {
                soapHandlers.add((SOAPHandler) handler);
                Set<QName> headers = ((SOAPHandler<?>) handler).getHeaders();
                if (headers != null) {
                    handlerKnownHeaders.addAll(headers);
                }
            } else if (handler instanceof MessageHandler) {
                messageHandlers.add((MessageHandler) handler);
                Set<QName> headers = ((MessageHandler<?>) handler).getHeaders();
                if (headers != null) {
                    handlerKnownHeaders.addAll(headers);
                }
            }else {
                throw new HandlerException("handler.not.valid.type",
                    handler.getClass());
            }
        }
        return new HandlerConfiguration(roles,portKnownHeaders,handlerChain,
                logicalHandlers,soapHandlers,messageHandlers,handlerKnownHeaders);
    }

    protected void addRequiredRoles() {
        roles.addAll(soapVersion.requiredRoles);
    }

    public Set<String> getRoles() {
        return roles;
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
        this.roles = roles;
        addRequiredRoles();
        HandlerConfiguration oldConfig = getHandlerConfig();
        setHandlerConfig(new HandlerConfiguration(this.roles, portKnownHeaders, oldConfig.getHandlerChain(),
                oldConfig.getLogicalHandlers(),oldConfig.getSoapHandlers(), oldConfig.getMessageHandlers(),
                oldConfig.getHandlerKnownHeaders()));
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
        setFeatures(new MTOMFeature(b));
    }

    public SOAPFactory getSOAPFactory() {
        return soapVersion.saajSoapFactory;
    }

    public MessageFactory getMessageFactory() {
        return soapVersion.saajMessageFactory;
    }

    private static final WebServiceFeature[] EMPTY_FEATURES = new WebServiceFeature[0];
}
