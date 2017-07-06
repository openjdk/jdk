/*
 * Copyright (c) 2005, 2017, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.ws.handler;
import java.util.Map;

/**
 * The interface {@code MessageContext} abstracts the message
 * context that is processed by a handler in the {@code handle}
 * method.
 *
 * <p>The {@code MessageContext} interface provides methods to
 * manage a property set. {@code MessageContext} properties
 * enable handlers in a handler chain to share processing related
 * state.
 *
 * @since 1.6, JAX-WS 2.0
 */
public interface MessageContext extends Map<String, Object> {

    /**
     * Standard property: message direction, {@code true} for
     * outbound messages, {@code false} for inbound.
     * <p>Type: boolean
     */
    public static final String MESSAGE_OUTBOUND_PROPERTY =
            "javax.xml.ws.handler.message.outbound";

    /**
     * Standard property: Map of attachments to a message for the inbound
     * message, key is  the MIME Content-ID, value is a DataHandler.
     * <p>Type: {@code java.util.Map<String, DataHandler>}
     */
    public static final String INBOUND_MESSAGE_ATTACHMENTS =
            "javax.xml.ws.binding.attachments.inbound";

    /**
     * Standard property: Map of attachments to a message for the outbound
     * message, key is the MIME Content-ID, value is a DataHandler.
     * <p>Type: {@code java.util.Map<String, DataHandler>}
     */
    public static final String OUTBOUND_MESSAGE_ATTACHMENTS =
            "javax.xml.ws.binding.attachments.outbound";

    /**
     * Standard property: input source for WSDL document.
     * <p>Type: org.xml.sax.InputSource
     */
    public static final String WSDL_DESCRIPTION =
            "javax.xml.ws.wsdl.description";

    /**
     * Standard property: name of WSDL service.
     * <p>Type: javax.xml.namespace.QName
     */
    public static final String WSDL_SERVICE =
            "javax.xml.ws.wsdl.service";

    /**
     * Standard property: name of WSDL port.
     * <p>Type: javax.xml.namespace.QName
     */
    public static final String WSDL_PORT =
            "javax.xml.ws.wsdl.port";

    /**
     * Standard property: name of wsdl interface (2.0) or port type (1.1).
     * <p>Type: javax.xml.namespace.QName
     */
    public static final String WSDL_INTERFACE =
            "javax.xml.ws.wsdl.interface";

    /**
     * Standard property: name of WSDL operation.
     * <p>Type: javax.xml.namespace.QName
     */
    public static final String WSDL_OPERATION =
            "javax.xml.ws.wsdl.operation";

    /**
     * Standard property: HTTP response status code.
     * <p>Type: java.lang.Integer
     */
    public static final String HTTP_RESPONSE_CODE =
            "javax.xml.ws.http.response.code";

    /**
     * Standard property: HTTP request headers.
     * <p>Type: {@code java.util.Map<java.lang.String, java.util.List<java.lang.String>>}
     */
    public static final String HTTP_REQUEST_HEADERS =
            "javax.xml.ws.http.request.headers";

    /**
     * Standard property: HTTP response headers.
     * <p>Type: {@code java.util.Map<java.lang.String, java.util.List<java.lang.String>>}
     */
    public static final String HTTP_RESPONSE_HEADERS =
            "javax.xml.ws.http.response.headers";

    /**
     * Standard property: HTTP request method.
     * <p>Type: java.lang.String
     */
    public static final String HTTP_REQUEST_METHOD =
            "javax.xml.ws.http.request.method";

    /**
     * Standard property: servlet request object.
     * <p>Type: javax.servlet.http.HttpServletRequest
     */
    public static final String SERVLET_REQUEST =
            "javax.xml.ws.servlet.request";

    /**
     * Standard property: servlet response object.
     * <p>Type: javax.servlet.http.HttpServletResponse
     */
    public static final String SERVLET_RESPONSE =
            "javax.xml.ws.servlet.response";

    /**
     * Standard property: servlet context object.
     * <p>Type: javax.servlet.ServletContext
     */
    public static final String SERVLET_CONTEXT =
            "javax.xml.ws.servlet.context";

    /**
     * Standard property: Query string for request.
     * <p>Type: String
     **/
    public static final String QUERY_STRING =
            "javax.xml.ws.http.request.querystring";

    /**
     * Standard property: Request Path Info
     * <p>Type: String
     */
    public static final String PATH_INFO =
            "javax.xml.ws.http.request.pathinfo";

    /**
     * Standard property: WS Addressing Reference Parameters.
     * The list MUST include all SOAP headers marked with the
     * wsa:IsReferenceParameter="true" attribute.
     * <p>Type: {@code List<Element>}
     *
     * @since 1.6, JAX-WS 2.1
     */
    public static final String REFERENCE_PARAMETERS =
            "javax.xml.ws.reference.parameters";

    /**
     * Property scope. Properties scoped as {@code APPLICATION} are
     * visible to handlers,
     * client applications and service endpoints; properties scoped as
     * {@code HANDLER}
     * are only normally visible to handlers.
     */
    public enum Scope {

        /**
         * Application visibility.
         */
        APPLICATION,

        /**
         * Handler visibility.
         */
        HANDLER};

    /**
     * Sets the scope of a property.
     *
     * @param name Name of the property associated with the
     *             {@code MessageContext}
     * @param scope Desired scope of the property
     * @throws java.lang.IllegalArgumentException if an illegal
     *             property name is specified
     */
    public void setScope(String name,  Scope scope);

    /**
     * Gets the scope of a property.
     *
     * @param name Name of the property
     * @return Scope of the property
     * @throws java.lang.IllegalArgumentException if a non-existant
     *             property name is specified
     */
    public Scope getScope(String  name);
}
