/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.ws.spi.http;

import javax.xml.ws.handler.MessageContext;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.security.Principal;

/**
 * This class encapsulates a HTTP request received and a
 * response to be generated in one exchange. It provides methods
 * for examining the request from the client, and for building and
 * sending the response.
 * <p>
 * A {@code HttpExchange} must be closed to free or reuse
 * underlying resources. The effect of failing to close an exchange
 * is undefined.
 *
 * @author Jitendra Kotamraju
 * @since 1.7, JAX-WS 2.2
 */
public abstract class HttpExchange {

    /**
     * Standard property: cipher suite value when the request is received
     * over HTTPS
     * <p>Type: String
     */
    public static final String REQUEST_CIPHER_SUITE =
            "javax.xml.ws.spi.http.request.cipher.suite";

    /**
     * Standard property: bit size of the algorithm when the request is
     * received over HTTPS
     * <p>Type: Integer
     */
    public static final String REQUEST_KEY_SIZE =
            "javax.xml.ws.spi.http.request.key.size";

    /**
     * Standard property: A SSL certificate, if any, associated with the request
     *
     * <p>Type: java.security.cert.X509Certificate[]
     * The order of this array is defined as being in ascending order of trust.
     * The first certificate in the chain is the one set by the client, the next
     * is the one used to authenticate the first, and so on.
     */
    public static final String REQUEST_X509CERTIFICATE =
            "javax.xml.ws.spi.http.request.cert.X509Certificate";

    /**
     * Returns an immutable Map containing the HTTP headers that were
     * included with this request. The keys in this Map will be the header
     * names, while the values will be a List of Strings containing each value
     * that was included (either for a header that was listed several times,
     * or one that accepts a comma-delimited list of values on a single line).
     * In either of these cases, the values for the header name will be
     * presented in the order that they were included in the request.
     * <p>
     * The keys in Map are case-insensitive.
     *
     * @return an immutable Map which can be used to access request headers
     */
    public abstract Map<String, List<String>> getRequestHeaders();

    /**
     * Returns the value of the specified request header. If the request
     * did not include a header of the specified name, this method returns
     * null. If there are multiple headers with the same name, this method
     * returns the first header in the request. The header name is
     * case-insensitive. This is a convienence method to get a header
     * (instead of using the {@link #getRequestHeaders}).
     *
     * @param name the name of the request header
     * @return returns the value of the requested header,
     *         or null if the request does not have a header of that name
     */
     public abstract String getRequestHeader(String name);

    /**
     * Returns a mutable Map into which the HTTP response headers can be stored
     * and which will be transmitted as part of this response. The keys in the
     * Map will be the header names, while the values must be a List of Strings
     * containing each value that should be included multiple times
     * (in the order that they should be included).
     * <p>
     * The keys in Map are case-insensitive.
     *
     * @return a mutable Map which can be used to set response headers.
     */
    public abstract Map<String, List<String>> getResponseHeaders();

    /**
     * Adds a response header with the given name and value. This method
     * allows a response header to have multiple values. This is a
     * convenience method to add a response header(instead of using the
     * {@link #getResponseHeaders()}).
     *
     * @param name the name of the header
     * @param value the additional header value. If it contains octet string,
     *        it should be encoded according to
     *        RFC 2047 (http://www.ietf.org/rfc/rfc2047.txt)
     *
     * @see #getResponseHeaders
     */
    public abstract void addResponseHeader(String name, String value);

    /**
     * Returns the part of the request's URI from the protocol
     * name up to the query string in the first line of the HTTP request.
     * Container doesn't decode this string.
     *
     * @return the request URI
     */
    public abstract String getRequestURI();

    /**
     * Returns the context path of all the endpoints in an application.
     * This path is the portion of the request URI that indicates the
     * context of the request. The context path always comes first in a
     * request URI. The path starts with a "/" character but does not
     * end with a "/" character. If this method returns "", the request
     * is for default context. The container does not decode this string.
     *
     * <p>
     * Context path is used in computing the endpoint address. See
     * {@link HttpContext#getPath}
     *
     * @return context path of all the endpoints in an application
     * @see HttpContext#getPath
     */
    public abstract String getContextPath();

    /**
     * Get the HTTP request method
     *
     * @return the request method
     */
    public abstract String getRequestMethod();

    /**
     * Returns a {@link HttpContext} for this exchange.
     * Container matches the request with the associated Endpoint's HttpContext
     *
     * @return the HttpContext for this exchange
     */
    public abstract HttpContext getHttpContext();

    /**
     * This must be called to end an exchange. Container takes care of
     * closing request and response streams. This must be called so that
     * the container can free or reuse underlying resources.
     *
     * @throws IOException if any i/o error
     */
    public abstract void close() throws IOException;

    /**
     * Returns a stream from which the request body can be read.
     * Multiple calls to this method will return the same stream.
     *
     * @return the stream from which the request body can be read.
     * @throws IOException if any i/o error during request processing
     */
    public abstract InputStream getRequestBody() throws IOException;

    /**
     * Returns a stream to which the response body must be
     * written. {@link #setStatus}) must be called prior to calling
     * this method. Multiple calls to this method (for the same exchange)
     * will return the same stream.
     *
     * @return the stream to which the response body is written
     * @throws IOException if any i/o error during response processing
     */
    public abstract OutputStream getResponseBody() throws IOException;

    /**
     * Sets the HTTP status code for the response.
     *
     * <p>
     * This method must be called prior to calling {@link #getResponseBody}.
     *
     * @param status the response code to send
     * @see #getResponseBody
     */
    public abstract void setStatus(int status);

    /**
     * Returns the unresolved address of the remote entity invoking
     * this request.
     *
     * @return the InetSocketAddress of the caller
     */
    public abstract InetSocketAddress getRemoteAddress();

    /**
     * Returns the unresolved local address on which the request was received.
     *
     * @return the InetSocketAddress of the local interface
     */
    public abstract InetSocketAddress getLocalAddress();

    /**
     * Returns the protocol string from the request in the form
     * <i>protocol/majorVersion.minorVersion</i>. For example,
     * "HTTP/1.1"
     *
     * @return the protocol string from the request
     */
    public abstract String getProtocol();

    /**
     * Returns the name of the scheme used to make this request,
     * for example: http, or https.
     *
     * @return name of the scheme used to make this request
     */
    public abstract String getScheme();

    /**
     * Returns the extra path information that follows the web service
     * path but precedes the query string in the request URI and will start
     * with a "/" character.
     *
     * <p>
     * This can be used for {@link MessageContext#PATH_INFO}
     *
     * @return decoded extra path information of web service.
     *         It is the path that comes
     *         after the web service path but before the query string in the
     *         request URI
     *         {@code null} if there is no extra path in the request URI
     */
    public abstract String getPathInfo();

    /**
     * Returns the query string that is contained in the request URI
     * after the path.
     *
     * <p>
     * This can be used for {@link MessageContext#QUERY_STRING}
     *
     * @return undecoded query string of request URI, or
     *         {@code null} if the request URI doesn't have one
     */
    public abstract String getQueryString();

    /**
     * Returns an attribute that is associated with this
     * {@code HttpExchange}. JAX-WS handlers and endpoints may then
     * access the attribute via {@link MessageContext}.
     * <p>
     * Servlet containers must expose {@link MessageContext#SERVLET_CONTEXT},
     * {@link MessageContext#SERVLET_REQUEST}, and
     * {@link MessageContext#SERVLET_RESPONSE}
     * as attributes.
     *
     * <p>If the request has been received by the container using HTTPS, the
     * following information must be exposed as attributes. These attributes
     * are {@link #REQUEST_CIPHER_SUITE}, and {@link #REQUEST_KEY_SIZE}.
     * If there is a SSL certificate associated with the request, it must
     * be exposed using {@link #REQUEST_X509CERTIFICATE}
     *
     * @param name attribute name
     * @return the attribute value, or {@code null} if the attribute doesn't
     *         exist
     */
    public abstract Object getAttribute(String name);

    /**
     * Gives all the attribute names that are associated with
     * this {@code HttpExchange}.
     *
     * @return set of all attribute names
     * @see #getAttribute(String)
     */
    public abstract Set<String> getAttributeNames();

    /**
     * Returns the {@link Principal} that represents the authenticated
     * user for this {@code HttpExchange}.
     *
     * @return Principal for an authenticated user, or
     *         {@code null} if not authenticated
     */
    public abstract Principal getUserPrincipal();

    /**
     * Indicates whether an authenticated user is included in the specified
     * logical "role".
     *
     * @param role specifies the name of the role
     * @return {@code true} if the user making this request belongs to a
     *         given role
     */
    public abstract boolean isUserInRole(String role);

}
