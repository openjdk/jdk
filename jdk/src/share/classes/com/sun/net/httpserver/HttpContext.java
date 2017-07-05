/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.net.httpserver;
import java.net.*;
import java.io.*;
import java.util.*;

/**
 * HttpContext represents a mapping between the root URI path of an application
 * to a {@link HttpHandler} which is invoked to handle requests destined
 * for that path on the associated HttpServer or HttpsServer.
 * <p>
 * HttpContext instances are created by the create methods in HttpServer
 * and HttpsServer
 * <p>
 * A chain of {@link Filter} objects can be added to a HttpContext. All exchanges processed by the
 * context can be pre- and post-processed by each Filter in the chain.
 * @since 1.6
 */
@jdk.Exported
public abstract class HttpContext {

    protected HttpContext () {
    }

    /**
     * returns the handler for this context
     * @return the HttpHandler for this context
     */
    public abstract HttpHandler getHandler () ;

    /**
     * Sets the handler for this context, if not already set.
     * @param h the handler to set for this context
     * @throws IllegalArgumentException if this context's handler is already set.
     * @throws NullPointerException if handler is <code>null</code>
     */
    public abstract void setHandler (HttpHandler h) ;

    /**
     * returns the path this context was created with
     * @return this context's path
     */
    public abstract String getPath() ;

    /**
     * returns the server this context was created with
     * @return this context's server
     */
    public abstract HttpServer getServer () ;

    /**
     * returns a mutable Map, which can be used to pass
     * configuration and other data to Filter modules
     * and to the context's exchange handler.
     * <p>
     * Every attribute stored in this Map will be visible to
     * every HttpExchange processed by this context
     */
    public abstract Map<String,Object> getAttributes() ;

    /**
     * returns this context's list of Filters. This is the
     * actual list used by the server when dispatching requests
     * so modifications to this list immediately affect the
     * the handling of exchanges.
     */
    public abstract List<Filter> getFilters();

    /**
     * Sets the Authenticator for this HttpContext. Once an authenticator
     * is establised on a context, all client requests must be
     * authenticated, and the given object will be invoked to validate each
     * request. Each call to this method replaces any previous value set.
     * @param auth the authenticator to set. If <code>null</code> then any
     *         previously set authenticator is removed,
     *         and client authentication will no longer be required.
     * @return the previous Authenticator, if any set, or <code>null</code>
     *         otherwise.
     */
    public abstract Authenticator setAuthenticator (Authenticator auth);

    /**
     * Returns the currently set Authenticator for this context
     * if one exists.
     * @return this HttpContext's Authenticator, or <code>null</code>
     *         if none is set.
     */
    public abstract Authenticator getAuthenticator ();
}
