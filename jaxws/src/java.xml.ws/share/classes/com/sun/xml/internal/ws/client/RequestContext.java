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

package com.sun.xml.internal.ws.client;

import com.oracle.webservices.internal.api.message.BaseDistributedPropertySet;
import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.EndpointAddress;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.transport.Headers;

import javax.xml.ws.BindingProvider;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;


import static javax.xml.ws.BindingProvider.*;
import static javax.xml.ws.handler.MessageContext.HTTP_REQUEST_HEADERS;

/**
 * Request context implementation.
 *
 * <h2>Why a custom map?</h2>
 * <p>
 * The JAX-WS spec exposes properties as a {@link Map}, but if we just use
 * an ordinary {@link HashMap} for this, it doesn't work as fast as we'd like
 * it to be. Hence we have this class.
 *
 * <p>
 * We expect the user to set a few properties and then use that same
 * setting to make a bunch of invocations. So we'd like to take some hit
 * when the user actually sets a property to do some computation,
 * then use that computed value during a method invocation again and again.
 *
 * <p>
 * For this goal, we use {@link com.sun.xml.internal.ws.api.PropertySet} and implement some properties
 * as virtual properties backed by methods. This allows us to do the computation
 * in the setter, and store it in a field.
 *
 * <p>
 * These fields are used by {@link Stub#process} to populate a {@link Packet}.
 *
 * <h2>How it works?</h2>
 * <p>
 * For better performance, we wan't use strongly typed field as much as possible
 * to avoid reflection and unnecessary collection iterations;
 *
 * Using {@link com.oracle.webservices.internal.api.message.BasePropertySet.MapView} implementation allows client to use {@link Map} interface
 * in a way that all the strongly typed properties are reflected to the fields
 * right away. Any additional (extending) properties can be added by client as well;
 * those would be processed using iterating the {@link MapView} and their processing,
 * of course, would be slower.
 * <p>
 * The previous implementation with fallback mode has been removed to simplify
 * the code and remove the bugs.
 *
 * @author Kohsuke Kawaguchi
 */
@SuppressWarnings({"SuspiciousMethodCalls"})
public final class RequestContext extends BaseDistributedPropertySet {
    private static final Logger LOGGER = Logger.getLogger(RequestContext.class.getName());

    /**
     * The default value to be use for {@link #contentNegotiation} obtained
     * from a system property.
     * <p>
     * This enables content negotiation to be easily switched on by setting
     * a system property on the command line for testing purposes tests.
     */
    private static ContentNegotiation defaultContentNegotiation =
            ContentNegotiation.obtainFromSystemProperty();

    /**
     * @deprecated
     */
    public void addSatellite(@NotNull com.sun.xml.internal.ws.api.PropertySet satellite) {
        super.addSatellite(satellite);
    }

    /**
     * The endpoint address to which this message is sent to.
     *
     * <p>
     * This is the actual data store for {@link BindingProvider#ENDPOINT_ADDRESS_PROPERTY}.
     */
    private @NotNull EndpointAddress endpointAddress;

    /**
     * Creates {@link BindingProvider#ENDPOINT_ADDRESS_PROPERTY} view
     * on top of {@link #endpointAddress}.
     *
     * @deprecated
     *      always access {@link #endpointAddress}.
     */
    @Property(ENDPOINT_ADDRESS_PROPERTY)
    public String getEndPointAddressString() {
        return endpointAddress != null ? endpointAddress.toString() : null;
    }

    public void setEndPointAddressString(String s) {
        if (s == null) {
            throw new IllegalArgumentException();
        } else {
            this.endpointAddress = EndpointAddress.create(s);
        }
    }

    public void setEndpointAddress(@NotNull EndpointAddress epa) {
        this.endpointAddress = epa;
    }

    public @NotNull EndpointAddress getEndpointAddress() {
        return endpointAddress;
    }

    /**
     * The value of {@link ContentNegotiation#PROPERTY}
     * property.
     */
    public ContentNegotiation contentNegotiation = defaultContentNegotiation;

    @Property(ContentNegotiation.PROPERTY)
    public String getContentNegotiationString() {
        return contentNegotiation.toString();
    }

    public void setContentNegotiationString(String s) {
        if (s == null) {
            contentNegotiation = ContentNegotiation.none;
        } else {
            try {
                contentNegotiation = ContentNegotiation.valueOf(s);
            } catch (IllegalArgumentException e) {
                // If the value is not recognized default to none
                contentNegotiation = ContentNegotiation.none;
            }
        }
    }

    /**
     * The value of the SOAPAction header associated with the message.
     *
     * <p>
     * For outgoing messages, the transport may sends out this value.
     * If this field is null, the transport may choose to send {@code ""}
     * (quoted empty string.)
     *
     * For incoming messages, the transport will set this field.
     * If the incoming message did not contain the SOAPAction header,
     * the transport sets this field to null.
     *
     * <p>
     * If the value is non-null, it must be always in the quoted form.
     * The value can be null.
     *
     * <p>
     * Note that the way the transport sends this value out depends on
     * transport and SOAP version.
     *
     * For HTTP transport and SOAP 1.1, BP requires that SOAPAction
     * header is present (See {@BP R2744} and {@BP R2745}.) For SOAP 1.2,
     * this is moved to the parameter of the "application/soap+xml".
     */

    private String soapAction;

    @Property(SOAPACTION_URI_PROPERTY)
    public String getSoapAction() {
        return soapAction;
    }

    public void setSoapAction(String sAction) {
        soapAction = sAction;
    }

    /**
     * This controls whether BindingProvider.SOAPACTION_URI_PROPERTY is used.
     * See BindingProvider.SOAPACTION_USE_PROPERTY for details.
     *
     * This only control whether value of BindingProvider.SOAPACTION_URI_PROPERTY is used or not and not
     * if it can be sent if it can be obtained by other means such as WSDL binding
     */
    private Boolean soapActionUse;

    @Property(SOAPACTION_USE_PROPERTY)
    public Boolean getSoapActionUse() {
        return soapActionUse;
    }

    public void setSoapActionUse(Boolean sActionUse) {
        soapActionUse = sActionUse;
    }

    /**
     * Creates an empty {@link RequestContext}.
     */
    RequestContext() {
    }

    /**
     * Copy constructor.
     */
    private RequestContext(RequestContext that) {
        for (Map.Entry<String, Object> entry : that.asMapLocal().entrySet()) {
            if (!propMap.containsKey(entry.getKey())) {
                asMap().put(entry.getKey(), entry.getValue());
            }
        }
        endpointAddress = that.endpointAddress;
        soapAction = that.soapAction;
        soapActionUse = that.soapActionUse;
        contentNegotiation = that.contentNegotiation;
        that.copySatelliteInto(this);
    }

    /**
     * The efficient get method that reads from {@link RequestContext}.
     */
    @Override
    public Object get(Object key) {
        if(supports(key)) {
            return super.get(key);
        } else {
            // use mapView to get extending property
            return asMap().get(key);
        }
    }

    /**
     * The efficient put method that updates {@link RequestContext}.
     */
    @Override
    public Object put(String key, Object value) {

        if(supports(key)) {
            return super.put(key,value);
        } else {
            // use mapView to put extending property (if the map allows that)
            return asMap().put(key, value);
        }
    }

    /**
     * Fill a {@link Packet} with values of this {@link RequestContext}.
     *
     * @param packet              to be filled with context values
     * @param isAddressingEnabled flag if addressing enabled (to provide warning if necessary)
     */
    @SuppressWarnings("unchecked")
    public void fill(Packet packet, boolean isAddressingEnabled) {

        // handling as many properties as possible (all in propMap.keySet())
        // to avoid slow Packet.put()
        if (endpointAddress != null) {
            packet.endpointAddress = endpointAddress;
        }
        packet.contentNegotiation = contentNegotiation;
        fillSOAPAction(packet, isAddressingEnabled);
        mergeRequestHeaders(packet);

        Set<String> handlerScopeNames = new HashSet<String>();

        copySatelliteInto(packet);

        // extending properties ...
        for (String key : asMapLocal().keySet()) {

            //if it is not standard property it defaults to Scope.HANDLER
            if (!supportsLocal(key)) {
                handlerScopeNames.add(key);
            }

            // to avoid slow Packet.put(), handle as small number of props as possible
            // => only properties not from RequestContext object
            if (!propMap.containsKey(key)) {
                Object value = asMapLocal().get(key);
                if (packet.supports(key)) {
                    // very slow operation - try to avoid it!
                    packet.put(key, value);
                } else {
                    packet.invocationProperties.put(key, value);
                }
            }
        }

        if (!handlerScopeNames.isEmpty()) {
            packet.getHandlerScopePropertyNames(false).addAll(handlerScopeNames);
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeRequestHeaders(Packet packet) {
        //for bug 12883765
        //retrieve headers which is set in soap message
        Headers packetHeaders = (Headers) packet.invocationProperties.get(HTTP_REQUEST_HEADERS);
        //retrieve headers from request context
        Map<String, List<String>> myHeaders = (Map<String, List<String>>) asMap().get(HTTP_REQUEST_HEADERS);
        if ((packetHeaders != null) && (myHeaders != null)) {
            //update the headers set in soap message with those in request context
            for (Entry<String, List<String>> entry : myHeaders.entrySet()) {
                String key = entry.getKey();
                if (key != null && key.trim().length() != 0) {
                    List<String> listFromPacket = packetHeaders.get(key);
                    //if the two headers contain the same key, combine the value
                    if (listFromPacket != null) {
                        listFromPacket.addAll(entry.getValue());
                    } else {
                        //add the headers  in request context to those set in soap message
                        packetHeaders.put(key, myHeaders.get(key));
                    }
                }
            }
            // update headers in request context with those set in soap message since it may contain other properties..
            asMap().put(HTTP_REQUEST_HEADERS, packetHeaders);
        }
    }

    private void fillSOAPAction(Packet packet, boolean isAddressingEnabled) {
        final boolean p = packet.packetTakesPriorityOverRequestContext;
        final String  localSoapAction    = p ? packet.soapAction : soapAction;
        final Boolean localSoapActionUse = p ? (Boolean) packet.invocationProperties.get(BindingProvider.SOAPACTION_USE_PROPERTY)
                                             : soapActionUse;

        //JAX-WS-596: Check the semantics of SOAPACTION_USE_PROPERTY before using the SOAPACTION_URI_PROPERTY for
        // SoapAction as specified in the javadoc of BindingProvider. The spec seems to be little contradicting with
        //  javadoc and says that the use property effects the sending of SOAPAction property.
        // Since the user has the capability to set the value as "" if needed, implement the javadoc behavior.
        if ((localSoapActionUse != null && localSoapActionUse) || (localSoapActionUse == null && isAddressingEnabled)) {
            if (localSoapAction != null) {
                packet.soapAction = localSoapAction;
            }
        }

        if ((!isAddressingEnabled && (localSoapActionUse == null || !localSoapActionUse)) && localSoapAction != null) {
            LOGGER.warning("BindingProvider.SOAPACTION_URI_PROPERTY is set in the RequestContext but is ineffective," +
                    " Either set BindingProvider.SOAPACTION_USE_PROPERTY to true or enable AddressingFeature");
        }
    }

    public RequestContext copy() {
        return new RequestContext(this);
    }

    @Override
    protected PropertyMap getPropertyMap() {
        return propMap;
    }

    private static final PropertyMap propMap = parse(RequestContext.class);

    @Override
    protected boolean mapAllowsAdditionalProperties() {
        return true;
    }
}
