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
package com.sun.xml.internal.ws.client;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.EndpointAddress;
import com.sun.xml.internal.ws.api.PropertySet;
import com.sun.xml.internal.ws.api.message.Packet;

import javax.xml.ws.BindingProvider;
import javax.xml.ws.WebServiceException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

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
 * For this goal, we use {@link PropertySet} and implement some properties
 * as virtual properties backed by methods. This allows us to do the computation
 * in the setter, and store it in a field.
 *
 * <p>
 * These fields are used by {@link Stub#process} to populate a {@link Packet}.
 *
 *
 *
 * <h2>How it works?</h2>
 * <p>
 * We make an assumption that a request context is mostly used to just
 * get and put values, not really for things like enumerating or size.
 *
 * <p>
 * So we start by maintaining state as a combination of {@link #others}
 * bag and strongly-typed fields. As long as the application uses
 * just {@link Map#put}, {@link Map#get}, and {@link Map#putAll}, we can
 * do things in this way. In this mode a {@link Map} we return works as
 * a view into {@link RequestContext}, and by itself it maintains no state.
 *
 * <p>
 * If {@link RequestContext} is in this mode, its state can be copied
 * efficiently into {@link Packet}.
 *
 * <p>
 * Once the application uses any other {@link Map} method, we move to
 * the "fallback" mode, where the data is actually stored in a {@link HashMap},
 * this is necessary for implementing the map interface contract correctly.
 *
 * <p>
 * To be safe, once we fallback, we'll never come back to the efficient state.
 *
 *
 *
 * <h2>Caution</h2>
 * <p>
 * Once we are in the fallback mode, none of the strongly typed field will
 * be used, and they may contain stale values. So the only method
 * the code outside this class can safely use is {@link #copy()},
 * {@link #fill(Packet)}, and constructors. Do not access the strongly
 * typed fields nor {@link #others} directly.
 *
 * @author Kohsuke Kawaguchi
 */
@SuppressWarnings({"SuspiciousMethodCalls"})
public final class RequestContext extends PropertySet {
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
     * Stores properties that don't fit the strongly-typed fields.
     */
    private final Map<String,Object> others;

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
    @Property(BindingProvider.ENDPOINT_ADDRESS_PROPERTY)
    public String getEndPointAddressString() {
        return endpointAddress.toString();
    }

    public void setEndPointAddressString(String s) {
        if(s==null)
            throw new IllegalArgumentException();
        else
            this.endpointAddress = EndpointAddress.create(s);
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
        if(s==null)
            contentNegotiation = ContentNegotiation.none;
        else {
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
     * If this field is null, the transport may choose to send <tt>""</tt>
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

    @Property(BindingProvider.SOAPACTION_URI_PROPERTY)
    public String getSoapAction(){
        return soapAction;
    }
    public void setSoapAction(String sAction){
        if(sAction == null) {
            throw new IllegalArgumentException("SOAPAction value cannot be null");
        }
        soapAction = sAction;
    }


    /**
     * {@link Map} exposed to the user application.
     */
    private final MapView mapView = new MapView();

    /**
     * Creates an empty {@link RequestContext}.
     */
    /*package*/ RequestContext() {
        others = new HashMap<String, Object>();
    }

    /**
     * Copy constructor.
     */
    private RequestContext(RequestContext that) {
        others = new HashMap<String,Object>(that.others);
        endpointAddress = that.endpointAddress;
        soapAction = that.soapAction;
        contentNegotiation = that.contentNegotiation;
        // this is fragile, but it works faster
    }

    /**
     * The efficient get method that reads from {@link RequestContext}.
     */
    public Object get(Object key) {
        if(super.supports(key))
            return super.get(key);
        else
            return others.get(key);
    }

    /**
     * The efficient put method that updates {@link RequestContext}.
     */
    public Object put(String key, Object value) {
        if(super.supports(key))
            return super.put(key,value);
        else
            return others.put(key,value);
    }

    /**
     * Gets the {@link Map} view of this request context.
     *
     * @return
     *      Always same object. Returned map is live.
     */
    public Map<String,Object> getMapView() {
        return mapView;
    }

    /**
     * Fill a {@link Packet} with values of this {@link RequestContext}.
     */
    public void fill(Packet packet) {
        if(mapView.fallbackMap==null) {
            if (endpointAddress != null)
                packet.endpointAddress = endpointAddress;
            packet.contentNegotiation = contentNegotiation;
            if (soapAction != null) {
                packet.soapAction = soapAction;
            }
            if(!others.isEmpty()) {
                packet.invocationProperties.putAll(others);
                //if it is not standard property it deafults to Scope.HANDLER
                packet.getHandlerScopePropertyNames(false).addAll(others.keySet());
            }
        } else {
            Set<String> handlerScopePropertyNames = new HashSet<String>();
            // fallback mode, simply copy map in a slow way
            for (Entry<String,Object> entry : mapView.fallbackMap.entrySet()) {
                String key = entry.getKey();
                if(packet.supports(key))
                    packet.put(key,entry.getValue());
                else
                    packet.invocationProperties.put(key,entry.getValue());

                //if it is not standard property it deafults to Scope.HANDLER
                if(!super.supports(key)) {
                    handlerScopePropertyNames.add(key);
                }
            }

            if(!handlerScopePropertyNames.isEmpty())
                packet.getHandlerScopePropertyNames(false).addAll(handlerScopePropertyNames);
        }
    }

    public RequestContext copy() {
        return new RequestContext(this);
    }


    private final class MapView implements Map<String,Object> {
        private Map<String,Object> fallbackMap;

        private Map<String,Object> fallback() {
            if(fallbackMap==null) {
                // has to fall back. fill in fallbackMap
                fallbackMap = new HashMap<String,Object>(others);
                // then put all known properties
                for (Map.Entry<String,Accessor> prop : propMap.entrySet()) {
                    fallbackMap.put(prop.getKey(),prop.getValue().get(RequestContext.this));
                }
            }
            return fallbackMap;
        }

        public int size() {
            return fallback().size();
        }

        public boolean isEmpty() {
            return fallback().isEmpty();
        }

        public boolean containsKey(Object key) {
            return fallback().containsKey(key);
        }

        public boolean containsValue(Object value) {
            return fallback().containsValue(value);
        }

        public Object get(Object key) {
            if (fallbackMap ==null) {
                return RequestContext.this.get(key);
            } else {
                return fallback().get(key);
            }
        }

        public Object put(String key, Object value) {
            if(fallbackMap ==null)
                return RequestContext.this.put(key,value);
            else
                return fallback().put(key, value);
        }

        public Object remove(Object key) {
            if (fallbackMap ==null) {
                return RequestContext.this.remove(key);
            } else {
                return fallback().remove(key);
            }
        }

        public void putAll(Map<? extends String, ? extends Object> t) {
            for (Entry<? extends String, ? extends Object> e : t.entrySet()) {
                put(e.getKey(),e.getValue());
            }
        }

        public void clear() {
            fallback().clear();
        }

        public Set<String> keySet() {
            return fallback().keySet();
        }

        public Collection<Object> values() {
            return fallback().values();
        }

        public Set<Entry<String, Object>> entrySet() {
            return fallback().entrySet();
        }
    }

    protected PropertyMap getPropertyMap() {
        return propMap;
    }

    private static final PropertyMap propMap = parse(RequestContext.class);
}
