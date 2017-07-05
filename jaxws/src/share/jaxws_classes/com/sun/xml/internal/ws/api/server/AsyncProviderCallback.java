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

package com.sun.xml.internal.ws.api.server;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;

/**
 * Callback interface to signal JAX-WS RI that the processing of an asynchronous request is complete.
 *
 * <p>
 * The application is responsible for invoking one of the two defined methods to
 * indicate the result of the request processing.
 *
 * <p>
 * Both methods will return immediately, and the JAX-WS RI will
 * send out an actual response at some later point.
 *
 * @author Jitendra Kotamraju
 * @author Kohsuke Kawaguchi
 * @since 2.1
 * @see AsyncProvider
 */
public interface AsyncProviderCallback<T> {
    /**
     * Indicates that a request was processed successfully.
     *
     * @param response
     *      Represents an object to be sent back to the client
     *      as a response. To indicate one-way, response needs to be null
     */
    void send(@Nullable T response);

    /**
     * Indicates that an error had occured while processing a request.
     *
     * @param t
     *      The error is propagated to the client. For example, if this is
     *      a SOAP-based web service, the server will send back a SOAP fault.
     */
    void sendError(@NotNull Throwable t);
}
