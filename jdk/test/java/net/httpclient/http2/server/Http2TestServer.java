/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SNIServerName;

/**
 * Waits for incoming TCP connections from a client and establishes
 * a HTTP2 connection. Two threads are created per connection. One for reading
 * and one for writing. Incoming requests are dispatched to the supplied
 * Http2Handler on additional threads. All threads
 * obtained from the supplied ExecutorService.
 */
public class Http2TestServer implements AutoCloseable {
    final ServerSocket server;
    volatile boolean secure;
    final ExecutorService exec;
    volatile boolean stopping = false;
    final Map<String,Http2Handler> handlers;
    final SSLContext sslContext;
    final String serverName;
    final HashMap<InetSocketAddress,Http2TestServerConnection> connections;

    private static ThreadFactory defaultThreadFac =
        (Runnable r) -> {
            Thread t = new Thread(r);
            t.setName("Test-server-pool");
            return t;
        };


    private static ExecutorService getDefaultExecutor() {
        return Executors.newCachedThreadPool(defaultThreadFac);
    }

    public Http2TestServer(String serverName, boolean secure, int port) throws Exception {
        this(serverName, secure, port, getDefaultExecutor(), null);
    }

    public Http2TestServer(boolean secure, int port) throws Exception {
        this(null, secure, port, getDefaultExecutor(), null);
    }

    public InetSocketAddress getAddress() {
        return (InetSocketAddress)server.getLocalSocketAddress();
    }

    public Http2TestServer(boolean secure,
                           SSLContext context) throws Exception {
        this(null, secure, 0, null, context);
    }

    public Http2TestServer(String serverName, boolean secure,
                           SSLContext context) throws Exception {
        this(serverName, secure, 0, null, context);
    }

    public Http2TestServer(boolean secure,
                           int port,
                           ExecutorService exec,
                           SSLContext context) throws Exception {
        this(null, secure, port, exec, context);
    }

    /**
     * Create a Http2Server listening on the given port. Currently needs
     * to know in advance whether incoming connections are plain TCP "h2c"
     * or TLS "h2"/
     *
     * @param serverName SNI servername
     * @param secure https or http
     * @param port listen port
     * @param exec executor service (cached thread pool is used if null)
     * @param context the SSLContext used when secure is true
     */
    public Http2TestServer(String serverName,
                           boolean secure,
                           int port,
                           ExecutorService exec,
                           SSLContext context)
        throws Exception
    {
        this.serverName = serverName;
        if (secure) {
            server = initSecure(port);
        } else {
            server = initPlaintext(port);
        }
        this.secure = secure;
        this.exec = exec == null ? getDefaultExecutor() : exec;
        this.handlers = Collections.synchronizedMap(new HashMap<>());
        this.sslContext = context;
        this.connections = new HashMap<>();
    }

    /**
     * Adds the given handler for the given path
     */
    public void addHandler(Http2Handler handler, String path) {
        handlers.put(path, handler);
    }

    Http2Handler getHandlerFor(String path) {
        if (path == null || path.equals(""))
            path = "/";

        final String fpath = path;
        AtomicReference<String> bestMatch = new AtomicReference<>("");
        AtomicReference<Http2Handler> href = new AtomicReference<>();

        handlers.forEach((key, value) -> {
            if (fpath.startsWith(key) && key.length() > bestMatch.get().length()) {
                bestMatch.set(key);
                href.set(value);
            }
        });
        Http2Handler handler = href.get();
        if (handler == null)
            throw new RuntimeException("No handler found for path " + path);
        System.err.println("Using handler for: " + bestMatch.get());
        return handler;
    }

    final ServerSocket initPlaintext(int port) throws Exception {
        return new ServerSocket(port);
    }

    public void stop() {
        // TODO: clean shutdown GoAway
        stopping = true;
        for (Http2TestServerConnection connection : connections.values()) {
            connection.close();
        }
        try {
            server.close();
        } catch (IOException e) {}
        exec.shutdownNow();
    }


    final ServerSocket initSecure(int port) throws Exception {
        ServerSocketFactory fac;
        if (sslContext != null) {
            fac = sslContext.getServerSocketFactory();
        } else {
            fac = SSLServerSocketFactory.getDefault();
        }
        SSLServerSocket se = (SSLServerSocket) fac.createServerSocket(port);
        SSLParameters sslp = se.getSSLParameters();
        sslp.setApplicationProtocols(new String[]{"h2"});
        se.setSSLParameters(sslp);
        se.setEnabledCipherSuites(se.getSupportedCipherSuites());
        se.setEnabledProtocols(se.getSupportedProtocols());
        // other initialisation here
        return se;
    }

    public String serverName() {
        return serverName;
    }

    /**
     * Starts a thread which waits for incoming connections.
     */
    public void start() {
        exec.submit(() -> {
            try {
                while (!stopping) {
                    Socket socket = server.accept();
                    InetSocketAddress addr = (InetSocketAddress) socket.getRemoteSocketAddress();
                    Http2TestServerConnection c = new Http2TestServerConnection(this, socket);
                    connections.put(addr, c);
                    c.run();
                }
            } catch (Throwable e) {
                if (!stopping) {
                    System.err.println("TestServer: start exception: " + e);
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void close() throws Exception {
        stop();
    }
}
