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
import java.lang.System.Logger.Level;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import jdk.incubator.http.internal.common.SSLTube;
import jdk.incubator.http.internal.common.Utils;


/**
 * Asynchronous version of SSLConnection.
 */
class AsyncSSLConnection extends AbstractAsyncSSLConnection {

    final PlainHttpConnection plainConnection;
    final PlainHttpPublisher writePublisher;
    private volatile SSLTube flow;

    AsyncSSLConnection(InetSocketAddress addr,
                       HttpClientImpl client,
                       String[] alpn) {
        super(addr, client, Utils.getServerName(addr), alpn);
        plainConnection = new PlainHttpConnection(addr, client);
        writePublisher = new PlainHttpPublisher();
    }

    @Override
    PlainHttpConnection plainConnection() {
        return plainConnection;
    }

    @Override
    public CompletableFuture<Void> connectAsync() {
        return plainConnection
                .connectAsync()
                .thenApply( unused -> {
                    // create the SSLTube wrapping the SocketTube, with the given engine
                    flow = new SSLTube(engine,
                                       client().theExecutor(),
                                       plainConnection.getConnectionFlow());
                    return null; } );
    }

    @Override
    boolean connected() {
        return plainConnection.connected();
    }

    @Override
    HttpPublisher publisher() { return writePublisher; }

    @Override
    boolean isProxied() {
        return false;
    }

    @Override
    SocketChannel channel() {
        return plainConnection.channel();
    }

    @Override
    ConnectionPool.CacheKey cacheKey() {
        return ConnectionPool.cacheKey(address, null);
    }

    @Override
    public void close() {
        plainConnection.close();
    }

    @Override
    void shutdownInput() throws IOException {
        debug.log(Level.DEBUG, "plainConnection.channel().shutdownInput()");
        plainConnection.channel().shutdownInput();
    }

    @Override
    void shutdownOutput() throws IOException {
        debug.log(Level.DEBUG, "plainConnection.channel().shutdownOutput()");
        plainConnection.channel().shutdownOutput();
    }

   @Override
   SSLTube getConnectionFlow() {
       return flow;
   }
}
