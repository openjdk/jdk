/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A handler which is invoked to process HTTP protocol upgrade requests.
 * 
 * <p>When an HTTP upgrade is initiated via {@link HttpExchange#upgrade(int, HttpUpgradeHandler)},
 * the server will invoke the {@link #handle(InputStream, OutputStream)} method
 * of this handler, providing direct access to the underlying socket streams.
 * 
 * <p>The handler is responsible for:
 * <ul>
 *   <li>Implementing the upgraded protocol (e.g., WebSocket)
 *   <li>Managing the lifecycle of the upgraded connection
 *   <li>Closing the streams when done
 * </ul>
 * 
 * <p>Once the upgrade is complete, the HTTP server will no longer manage
 * the connection, and the handler has full control over the socket.
 * 
 * @since 26
 */
@FunctionalInterface
public interface HttpUpgradeHandler {

    /**
     * Handles the upgraded protocol on the given streams.
     * 
     * <p>This method is called after a successful HTTP upgrade response has
     * been sent to the client. The provided streams give direct access to
     * the underlying socket, allowing the implementation to communicate
     * using the upgraded protocol.
     * 
     * <p>The implementation is responsible for closing both streams when
     * the upgraded protocol session is complete. The streams may be the
     * same object if using a socket channel.
     * 
     * @param input  the input stream from the client
     * @param output the output stream to the client
     * @throws IOException if an I/O error occurs during protocol handling
     */
    void handle(InputStream input, OutputStream output) throws IOException;
}
