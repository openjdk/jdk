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

package com.sun.xml.internal.ws.transport.http;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.Component;
import com.sun.xml.internal.ws.api.server.WSEndpoint;

import java.io.IOException;

/**
 * Intercepts GET HTTP requests to process the requests.
 *
 * <p>
 * {@link HttpAdapter} looks for this SPI in {@link WSEndpoint#getComponents()}
 * to allow components to expose additional information through HTTP.
 *
 * @author Kohsuke Kawaguchi
 * @see Component#getSPI(Class)
 * @since 2.1.2
 */
public abstract class HttpMetadataPublisher {
    /**
     * When {@link HttpAdapter} receives a GET request with a query string
     * (which is a convention for metadata requests, such as '?wsdl' or '?xsd=...'),
     * then this method is invoked to allow components to intercept the request.
     *
     * @param adapter
     *      Adapter that accepted the connection.
     * @param connection
     *      Represents the current connection.
     * @return
     *      true if the request is processed. If false is returned the default processing kicks in.
     */
    public abstract boolean handleMetadataRequest(@NotNull HttpAdapter adapter, @NotNull WSHTTPConnection connection) throws IOException;
}
