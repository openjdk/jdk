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

package com.sun.xml.internal.ws.developer;

import com.sun.xml.internal.ws.api.message.Header;
import com.sun.xml.internal.ws.api.message.Headers;

import javax.xml.bind.JAXBContext;
import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Service;
import javax.xml.ws.Service.Mode;
import java.util.List;

/**
 * {@link BindingProvider} with JAX-WS RI's extension methods.
 *
 * @author Kohsuke Kawaguchi
 * @author Jitendra Kotamraju
 * @since 2.1EA3
 */
public interface WSBindingProvider extends BindingProvider {
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
     *      If this {@lini WSBindingProvider} is a {@link Dispatch}
     *      that does not use JAXB.
     */
    void setOutboundHeaders(Object... headers);

    List<Header> getInboundHeaders();
}
