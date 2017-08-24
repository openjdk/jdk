/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.SSLEngine;
import jdk.incubator.http.internal.common.ExceptionallyCloseable;


/**
 * Asynchronous version of SSLConnection.
 *
 * There are two concrete implementations of this class: AsyncSSLConnection
 * and AsyncSSLTunnelConnection.
 * This abstraction is useful when downgrading from HTTP/2 to HTTP/1.1 over
 * an SSL connection. See ExchangeImpl::get in the case where an ALPNException
 * is thrown.
 *
 * Note: An AsyncSSLConnection wraps a PlainHttpConnection, while an
 *       AsyncSSLTunnelConnection wraps a PlainTunnelingConnection.
 *       If both these wrapped classes where made to inherit from a
 *       common abstraction then it might be possible to merge
 *       AsyncSSLConnection and AsyncSSLTunnelConnection back into
 *       a single class - and simply use different factory methods to
 *       create different wrappees, but this is left up for further cleanup.
 *
 */
abstract class AbstractAsyncSSLConnection extends HttpConnection
               implements AsyncConnection, ExceptionallyCloseable {


    AbstractAsyncSSLConnection(InetSocketAddress addr, HttpClientImpl client) {
        super(addr, client);
    }

    abstract SSLEngine getEngine();
    abstract AsyncSSLDelegate sslDelegate();
    abstract HttpConnection plainConnection();
    abstract HttpConnection downgrade();

    @Override
    final boolean isSecure() {
        return true;
    }

    // Blocking read functions not used here
    @Override
    protected final ByteBuffer readImpl() throws IOException {
        throw new UnsupportedOperationException("Not supported.");
    }

    // whenReceivedResponse only used in HTTP/1.1 (Http1Exchange)
    // AbstractAsyncSSLConnection is only used with HTTP/2
    @Override
    final CompletableFuture<Void> whenReceivingResponse() {
        throw new UnsupportedOperationException("Not supported.");
    }

}
