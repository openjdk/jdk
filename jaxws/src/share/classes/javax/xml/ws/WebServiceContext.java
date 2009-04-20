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

package javax.xml.ws;

import java.security.Principal;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.wsaddressing.W3CEndpointReference;
import org.w3c.dom.Element;


/**
 *  A <code>WebServiceContext</code> makes it possible for
 *  a web service endpoint implementation class to access
 *  message context and security information relative to
 *  a request being served.
 *
 *  Typically a <code>WebServiceContext</code> is injected
 *  into an endpoint implementation class using the
 *  <code>Resource</code> annotation.
 *
 *  @since JAX-WS 2.0
 *
 *  @see javax.annotation.Resource
 **/
public interface WebServiceContext {

    /**
     * Returns the <code>MessageContext</code> for the request being served
     * at the time this method is called. Only properties with
     * APPLICATION scope will be visible to the application.
     *
     * @return MessageContext The message context.
     *
     * @throws IllegalStateException This exception is thrown
     *         if the method is called while no request is
     *         being serviced.
     *
     * @see javax.xml.ws.handler.MessageContext
     * @see javax.xml.ws.handler.MessageContext.Scope
     * @see java.lang.IllegalStateException
     **/
    public MessageContext getMessageContext();

    /**
     * Returns the Principal that identifies the sender
     * of the request currently being serviced. If the
     * sender has not been authenticated, the method
     * returns <code>null</code>.
     *
     * @return Principal The principal object.
     *
     * @throws IllegalStateException This exception is thrown
     *         if the method is called while no request is
     *         being serviced.
     *
     * @see java.security.Principal
     * @see java.lang.IllegalStateException
     **/
    public Principal getUserPrincipal();

    /**
     * Returns a boolean indicating whether the
     * authenticated user is included in the specified
     * logical role. If the user has not been
     * authenticated, the method returns </code>false</code>.
     *
     * @param role  A <code>String</code> specifying the name of the role
     *
     * @return a <code>boolean</code> indicating whether
     * the sender of the request belongs to a given role
     *
     * @throws IllegalStateException This exception is thrown
     *         if the method is called while no request is
     *         being serviced.
     **/
    public boolean isUserInRole(String role);

    /**
     * Returns the <code>EndpointReference</code> for this
     * endpoint.
     * <p>
     * If the {@link Binding} for this <code>bindingProvider</code> is
     * either SOAP1.1/HTTP or SOAP1.2/HTTP, then a
     * <code>W3CEndpointReference</code> MUST be returned.
     *
     * @param referenceParameters Reference parameters to be associated with the
     * returned <code>EndpointReference</code> instance.
     * @return EndpointReference of the endpoint associated with this
     * <code>WebServiceContext</code>.
     * If the returned <code>EndpointReference</code> is of type
     * <code>W3CEndpointReference</code> then it MUST contain the
     * the specified <code>referenceParameters</code>.
     *
     * @throws IllegalStateException This exception is thrown
     *         if the method is called while no request is
     *         being serviced.
     *
     * @see W3CEndpointReference
     *
     * @since JAX-WS 2.1
     */
    public EndpointReference getEndpointReference(Element... referenceParameters);

    /**
     * Returns the <code>EndpointReference</code> associated with
     * this endpoint.
     *
     * @param clazz The type of <code>EndpointReference</code> that
     * MUST be returned.
     * @param referenceParameters Reference parameters to be associated with the
     * returned <code>EndpointReference</code> instance.
     * @return EndpointReference of type <code>clazz</code> of the endpoint
     * associated with this <code>WebServiceContext</code> instance.
     * If the returned <code>EndpointReference</code> is of type
     * <code>W3CEndpointReference</code> then it MUST contain the
     * the specified <code>referenceParameters</code>.
     *
     * @throws IllegalStateException This exception is thrown
     *         if the method is called while no request is
     *         being serviced.
     * @throws WebServiceException If the <code>clazz</code> type of
     * <code>EndpointReference</code> is not supported.
     *
     * @since JAX-WS 2.1
     **/
    public <T extends EndpointReference> T getEndpointReference(Class<T> clazz,
            Element... referenceParameters);
}
