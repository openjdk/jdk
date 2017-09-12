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

package com.sun.xml.internal.ws.api.server;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.Pipe;

import javax.xml.ws.WebServiceContext;
import javax.xml.ws.WebServiceException;
import java.security.Principal;

/**
 * This object is set to {@link Packet#webServiceContextDelegate}
 * to serve {@link WebServiceContext} methods for a {@link Packet}.
 *
 * <p>
 * When the user application calls a method on {@link WebServiceContext},
 * the JAX-WS RI goes to the {@link Packet} that represents the request,
 * then check {@link Packet#webServiceContextDelegate}, and forwards
 * the method calls to {@link WebServiceContextDelegate}.
 *
 * <p>
 * All the methods defined on this interface takes {@link Packet}
 * (whose {@link Packet#webServiceContextDelegate} points to
 * this object), so that a single stateless {@link WebServiceContextDelegate}
 * can be used to serve multiple concurrent {@link Packet}s,
 * if the implementation wishes to do so.
 *
 * <p>
 * (It is also allowed to create one instance of
 * {@link WebServiceContextDelegate} for each packet,
 * and thus effectively ignore the packet parameter.)
 *
 * <p>
 * Attaching this on a {@link Packet} allows {@link Pipe}s to
 * intercept and replace them, if they wish.
 *
 *
 * @author Kohsuke Kawaguchi
 */
public interface WebServiceContextDelegate {
    /**
     * Implements {@link WebServiceContext#getUserPrincipal()}
     * for the given packet.
     *
     * @param request
     *      Always non-null. See class javadoc.
     * @see WebServiceContext#getUserPrincipal()
     */
    Principal getUserPrincipal(@NotNull Packet request);

    /**
     * Implements {@link WebServiceContext#isUserInRole(String)}
     * for the given packet.
     *
     * @param request
     *      Always non-null. See class javadoc.
     * @see WebServiceContext#isUserInRole(String)
     */
    boolean isUserInRole(@NotNull Packet request,String role);

    /**
     * Gets the address of the endpoint.
     *
     * <p>
     * The "address" of endpoints is always affected by a particular
     * client being served, hence it's up to transport to provide this
     * information.
     *
     * @param request
     *      Always non-null. See class javadoc.
     * @param endpoint
     *      The endpoint whose address will be returned.
     *
     * @throws WebServiceException
     *      if this method could not compute the address for some reason.
     * @return
     *      Absolute URL of the endpoint. This shold be an address that the client
     *      can use to talk back to this same service later.
     *
     * @see WebServiceContext#getEndpointReference
     */
    @NotNull String getEPRAddress(@NotNull Packet request, @NotNull WSEndpoint endpoint);

    /**
     * Gets the address of the primary WSDL.
     *
     * <p>
     * If a transport supports publishing of WSDL by itself (instead/in addition to MEX),
     * then it should implement this method so that the rest of the JAX-WS RI can
     * use that information.
     *
     * For example, HTTP transports often use the convention {@code getEPRAddress()+"?wsdl"}
     * for publishing WSDL on HTTP.
     *
     * <p>
     * Some transports may not have such WSDL publishing mechanism on its own.
     * Those transports may choose to return null, indicating that WSDL
     * is not published. If such transports are always used in conjunction with
     * other transports that support WSDL publishing (such as SOAP/TCP used
     * with Servlet transport), then such transport may
     * choose to find the corresponding servlet endpoint by {@link Module#getBoundEndpoints()}
     * and try to obtain the address from there.
     *
     * <p>
     * This information is used to put a metadata reference inside an EPR,
     * among other things. Clients that do not support MEX rely on this
     * WSDL URL to retrieve metadata, it is desirable for transports to support
     * this, but not mandatory.
     *
     * <p>
     * This method will be never invoked if the {@link WSEndpoint}
     * does not have a corresponding WSDL to begin with
     * (IOW {@link WSEndpoint#getServiceDefinition() returning null}.
     *
     * @param request
     *      Always non-null. See class javadoc.
     * @param endpoint
     *      The endpoint whose address will be returned.
     *
     * @return
     *      null if the implementation does not support the notion of
     *      WSDL publishing.
     */
    @Nullable String getWSDLAddress(@NotNull Packet request, @NotNull WSEndpoint endpoint);
}
