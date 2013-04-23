/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.developer;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.ComponentRegistry;
import com.sun.xml.internal.ws.api.message.Header;
import com.sun.xml.internal.ws.api.message.Headers;
import com.sun.xml.internal.ws.api.addressing.WSEndpointReference;
import com.sun.xml.internal.ws.api.client.WSPortInfo;
import javax.xml.bind.JAXBContext;
import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Service;
import javax.xml.ws.Service.Mode;
import java.util.List;
import java.io.Closeable;
import com.sun.org.glassfish.gmbal.ManagedObjectManager;

/**
 * {@link BindingProvider} with JAX-WS RI's extension methods.
 *
 * @author Kohsuke Kawaguchi
 * @author Jitendra Kotamraju
 * @since 2.1EA3
 */
public interface WSBindingProvider extends BindingProvider, Closeable, ComponentRegistry {
    /**
     * Sets the out-bound headers to be added to messages sent from
     * this {@link BindingProvider}.
     *
     * <p>
     * Calling this method would discard any out-bound headers
     * that were previously set.
     *
     * <p>
     * A new {@link Header} object can be created by using
     * one of the methods on {@link Headers}.
     *
     * @param headers
     *      The headers to be added to the future request messages.
     *      To clear the outbound headers, pass in either null
     *      or empty list.
     * @throws IllegalArgumentException
     *      if the list contains null item.
     */
    void setOutboundHeaders(List<Header> headers);

    /**
     * Sets the out-bound headers to be added to messages sent from
     * this {@link BindingProvider}.
     *
     * <p>
     * Works like {@link #setOutboundHeaders(List)} except
     * that it accepts a var arg array.
     *
     * @param headers
     *      Can be null or empty.
     */
    void setOutboundHeaders(Header... headers);

    /**
     * Sets the out-bound headers to be added to messages sent from
     * this {@link BindingProvider}.
     *
     * <p>
     * Each object must be a JAXB-bound object that is understood
     * by the {@link JAXBContext} object known by this {@link WSBindingProvider}
     * (that is, if this is a {@link Dispatch} with JAXB, then
     * {@link JAXBContext} given to {@link Service#createDispatch(QName,JAXBContext,Mode)}
     * and if this is a typed proxy, then {@link JAXBContext}
     * implicitly created by the JAX-WS RI.)
     *
     * @param headers
     *      Can be null or empty.
     * @throws UnsupportedOperationException
     *      If this {@link WSBindingProvider} is a {@link Dispatch}
     *      that does not use JAXB.
     */
    void setOutboundHeaders(Object... headers);

    List<Header> getInboundHeaders();

    /**
     * Sets the endpoint address for all the invocations that happen
     * from {@link BindingProvider}. Instead of doing the following
     *
     * <p>
     * ((BindingProvider)proxy).getRequestContext().put(
     *      BindingProvider.ENDPOINT_ADDRESS_PROPERTY, "...")
     * <p>
     * you could do this:
     *
     * <p>
     * ((WSBindingProvider)proxy).setAddress("...");
     *
     * @param address Address of the service
     */
    void setAddress(String address);

    /**
     * Similar to {link BindingProvider#getEndpointReference(}, but returns WSEndpointReference that has more
     * convenience methods
     *
     * @return WSEndpointReference of the target servcie endpoint
     *
     * @since JAX-WS 2.2
     */
    WSEndpointReference getWSEndpointReference();

    /**
     *
     * @return WSPortInfo object that captures the port information for which the stub is created.
     * @since JAX-WS 2.2
     */
    WSPortInfo getPortInfo();

    /**
     * Get the ManagedObjectManager for this provider.
     */
    public @NotNull ManagedObjectManager getManagedObjectManager();
}
