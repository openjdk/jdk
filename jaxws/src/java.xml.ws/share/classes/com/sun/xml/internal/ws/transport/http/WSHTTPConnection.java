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

package com.sun.xml.internal.ws.transport.http;

import com.oracle.webservices.internal.api.message.BasePropertySet;
import com.oracle.webservices.internal.api.message.PropertySet;
import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.server.WebServiceContextDelegate;

import javax.xml.ws.WebServiceContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * The view of an HTTP exchange from the point of view of JAX-WS.
 *
 * <p>
 * Different HTTP server layer uses different implementations of this class
 * so that JAX-WS can be shielded from individuality of such layers.
 * This is an interface implemented as an abstract class, so that
 * future versions of the JAX-WS RI can add new methods.
 *
 * <p>
 * This class extends {@link PropertySet} so that a transport can
 * expose its properties to the application and pipes. (This object
 * will be added to {@link Packet#addSatellite(PropertySet)}.)
 *
 * @author Jitendra Kotamraju
 */
public abstract class WSHTTPConnection extends BasePropertySet {

    public static final int OK=200;
    public static final int ONEWAY=202;
    public static final int UNSUPPORTED_MEDIA=415;
    public static final int MALFORMED_XML=400;
    public static final int INTERNAL_ERR=500;

    /**
     * Overwrites all the HTTP response headers written thus far.
     *
     * <p>
     * The implementation should copy the contents of the {@link Map},
     * rather than retaining a reference. The {@link Map} passed as a
     * parameter may change after this method is invoked.
     *
     * <p>
     * This method may be called repeatedly, although in normal use
     * case that's rare (so the implementation is encourage to take
     * advantage of this usage pattern to improve performance, if possible.)
     *
     * <p>
     * Initially, no header is set.
     *
     * <p>
     * This parameter is usually exposed to {@link WebServiceContext}
     * as {@link Packet#OUTBOUND_TRANSPORT_HEADERS}, and thus it
     * should ignore {@code Content-Type} and {@code Content-Length} headers.
     *
     * @param headers
     *      See {@link HttpURLConnection#getHeaderFields()} for the format.
     *      This parameter may not be null, but since the user application
     *      code may invoke this method, a graceful error checking with
     *      an helpful error message should be provided if it's actually null.
     * @see #setContentTypeResponseHeader(String)
     */
    public abstract void setResponseHeaders(@NotNull Map<String,List<String>> headers);

    public void setResponseHeader(String key, String value) {
        setResponseHeader(key, Collections.singletonList(value));
    }

    public abstract void setResponseHeader(String key, List<String> value);

    /**
     * Sets the {@code "Content-Type"} header.
     *
     * <p>
     * If the Content-Type header has already been set, this method will overwrite
     * the previously set value. If not, this method adds it.
     *
     * <p>
     * Note that this method and {@link #setResponseHeaders(java.util.Map)}
     * may be invoked in any arbitrary order.
     *
     * @param value
     *      strings like {@code "application/xml; charset=UTF-8"} or
     *      {@code "image/jpeg"}.
     */
    public abstract void setContentTypeResponseHeader(@NotNull String value);

    /**
     * Sets the HTTP response code like {@link #OK}.
     *
     * <p>
     * While JAX-WS processes a {@link WSHTTPConnection}, it
     * will at least call this method once to set a valid HTTP response code.
     * Note that this method may be invoked multiple times (from user code),
     * so do not consider the value to be final until {@link #getOutput()}
     * is invoked.
     */

    public abstract void setStatus(int status);

    /**
     * Gets the last value set by {@link #setStatus(int)}.
     *
     * @return
     *      if {@link #setStatus(int)} has not been invoked yet,
     *      return 0.
     */
    // I know this is ugly method!
    public abstract int getStatus();

    /**
     * Transport's underlying input stream.
     *
     * <p>
     * This method will be invoked at most once by the JAX-WS RI to
     * read the request body. If there's no request body, this method
     * should return an empty {@link InputStream}.
     *
     * @return
     *      the stream from which the request body will be read.
     */
    public abstract @NotNull InputStream getInput() throws IOException;

    /**
     * Transport's underlying output stream
     *
     * <p>
     * This method will be invoked exactly once by the JAX-WS RI
     * to start writing the response body (unless the processing aborts abnormally.)
     * Even if there's no response body to write, this method will
     * still be invoked only to be closed immediately.
     *
     * <p>
     * Once this method is called, the status code and response
     * headers will never change (IOW {@link #setStatus(int)},
     * {@link #setResponseHeaders}, and {@link #setContentTypeResponseHeader(String)}
     * will never be invoked.
     */
    public abstract @NotNull OutputStream getOutput() throws IOException;

    /**
     * Returns the {@link WebServiceContextDelegate} for this connection.
     */
    public abstract @NotNull WebServiceContextDelegate getWebServiceContextDelegate();

    /**
     * HTTP request method, such as "GET" or "POST".
     */
    public abstract @NotNull String getRequestMethod();

    /**
     * HTTP request headers.
     *
     * @deprecated
     *      This is a potentially expensive operation.
     *      Programs that want to access HTTP headers should consider using
     *      other methods such as {@link #getRequestHeader(String)}.
     *
     * @return
     *      can be empty but never null.
     */
    public abstract @NotNull Map<String,List<String>> getRequestHeaders();

    /**
     * HTTP request header names.
     *
     * @deprecated
     *      This is a potentially expensive operation.
     *      Programs that want to access HTTP headers should consider using
     *      other methods such as {@link #getRequestHeader(String)}.
     *
     * @return
     *      can be empty but never null.
     */
    public abstract @NotNull Set<String> getRequestHeaderNames();

    /**
     * @return
     *      HTTP response headers.
     */
    public abstract Map<String,List<String>> getResponseHeaders();

    /**
     * Gets an HTTP request header.
     *
     * <p>
     * if multiple headers are present, this method returns one of them.
     * (The implementation is free to choose which one it returns.)
     *
     * @return
     *      null if no header exists.
     */
    public abstract @Nullable String getRequestHeader(@NotNull String headerName);

    /**
     * Gets an HTTP request header.
     *
     * @return
     *      null if no header exists.
     */
    public abstract @Nullable List<String> getRequestHeaderValues(@NotNull String headerName);

    /**
     * HTTP Query string, such as "foo=bar", or null if none exists.
     */
    public abstract @Nullable String getQueryString();

    /**
     * Extra portion of the request URI after the end of the expected address of the service
     * but before the query string
     */
    public abstract @Nullable String getPathInfo();

    /**
     * Requested path. A string like "/foo/bar/baz"
     */
    public abstract @NotNull String getRequestURI();

    /**
     * Requested scheme, e.g. "http" or "https"
     */
    public abstract @NotNull String getRequestScheme();

    /**
     * Server name
     */
    public abstract @NotNull String getServerName();

    /**
     * Server port
     */
    public abstract int getServerPort();

    /**
     * Portion of the request URI that groups related service addresses.  The value, if non-empty, will
     * always begin with '/', but will never end with '/'.  Environments that do not support
     * context paths must return an empty string.
     */
    public @NotNull String getContextPath() {
        return "";
    }

    /**
     * Environment specific context , if available
     */
    public Object getContext() {
        return null;
    }

    /**
     * Gets the absolute URL up to the context path.
     * @return
     *      String like "http://myhost/myapp"
     * @since 2.1.2
     */
    public @NotNull String getBaseAddress() {
        throw new UnsupportedOperationException();
    }

    /**
     * Whether connection is HTTPS or not
     *
     * @return if the received request is on HTTPS, return true
     *         else false
     */
    public abstract boolean isSecure();

    /**
     * User principal associated with the request
     *
     * @return user principal
     */
    public Principal getUserPrincipal() {
        return null;
    }

    /**
     * Whether user associated with the request holds the given role
     *
     * @param role Role to check
     * @return if the caller holds the role
     */
    public boolean isUserInRole(String role) {
        return false;
    }

    /**
     * Gets request metadata attribute
     * @param key Request metadata key
     * @return Value of metadata attribute or null, if no value present
     */
    public Object getRequestAttribute(String key) {
        return null;
    }

    private volatile boolean closed;

    /**
     * Close the connection
     */
    public void close() {
        this.closed = true;
    }

    /**
     * Retuns whether connection is closed or not.
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Subclasses are expected to override
     *
     * @return a {@link String} containing the protocol name and version number
     */
    public String getProtocol() {
        return "HTTP/1.1";
    }

    /**
     * Subclasses are expected to override
     *
     * @since JAX-WS RI 2.2.2
     * @return value of given cookie
     */
    public String getCookie(String name) {
        return null;
    }

    /**
     * Subclasses are expected to override
     *
     *
     * @since JAX-WS RI 2.2.2
     */
    public void setCookie(String name, String value) {
    }

    /**
     * Subclasses are expected to override
     */
    public void setContentLengthResponseHeader(int value) {
    }

}
