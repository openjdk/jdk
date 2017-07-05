/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.util.Map;

/** The <code>BindingProvider</code> interface provides access to the
 *  protocol binding and associated context objects for request and
 *  response message processing.
 *
 *  @since JAX-WS 2.0
 *
 *  @see javax.xml.ws.Binding
**/
public interface BindingProvider {
    /** Standard property: User name for authentication.
     *  <p>Type: java.lang.String
    **/
    public static final String USERNAME_PROPERTY =
                      "javax.xml.ws.security.auth.username";

    /** Standard property: Password for authentication.
     *  <p>Type: java.lang.String
    **/
    public static final String PASSWORD_PROPERTY =
                      "javax.xml.ws.security.auth.password";

    /** Standard property: Target service endpoint address. The
     *  URI scheme for the endpoint address specification must
     *  correspond to the protocol/transport binding for the
     *  binding in use.
     *  <p>Type: java.lang.String
    **/
    public static final String ENDPOINT_ADDRESS_PROPERTY =
                      "javax.xml.ws.service.endpoint.address";

    /** Standard property: This boolean property is used by a service
     *  client to indicate whether or not it wants to participate in
     *  a session with a service endpoint. If this property is set to
     *  true, the service client indicates that it wants the session
     *  to be maintained. If set to false, the session is not maintained.
     *  The default value for this property is false.
     *  <p>Type: java.lang.Boolean
    **/
    public static final String SESSION_MAINTAIN_PROPERTY =
                      "javax.xml.ws.session.maintain";

    /** Standard property for SOAPAction. This boolean property
     *  indicates whether or not SOAPAction is to be used. The
     *  default value of this property is false indicating that
     *  the SOAPAction is not used.
     *  <p>Type: <code>java.lang.Boolean</code>
    **/
    public static final String SOAPACTION_USE_PROPERTY =
                      "javax.xml.ws.soap.http.soapaction.use";

    /** Standard property for SOAPAction. Indicates the SOAPAction
     *  URI if the <code>javax.xml.ws.soap.http.soapaction.use</code>
     *  property is set to <code>true</code>.
     *  <p>Type: <code>java.lang.String</code>
    **/
    public static final String SOAPACTION_URI_PROPERTY =
                      "javax.xml.ws.soap.http.soapaction.uri";

    /** Get the context that is used to initialize the message context
     *  for request messages.
     *
     * Modifications to the request context do not affect the message context of
     * either synchronous or asynchronous operations that have already been
     * started.
     *
     * @return The context that is used in processing request messages.
    **/
    Map<String, Object> getRequestContext();

    /** Get the context that resulted from processing a response message.
     *
     * The returned context is for the most recently completed synchronous
     * operation. Subsequent synchronous operation invocations overwrite the
     * response context. Asynchronous operations return their response context
     * via the Response interface.
     *
     * @return The context that resulted from processing the latest
     * response messages.
    **/
    Map<String, Object> getResponseContext();

    /** Get the Binding for this binding provider.
     *
     * @return The Binding for this binding provider.
    **/
    Binding getBinding();
}
