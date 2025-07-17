/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.httpclient.test.lib.http2;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;

import jdk.internal.net.http.frame.ErrorFrame;

/**
 * Waits for incoming TCP connections from a client and establishes
 * a HTTP2 connection. Two threads are created per connection. One for reading
 * and one for writing. Incoming requests are dispatched to the supplied
 * Http2Handler on additional threads. All threads
 * obtained from the supplied ExecutorService.
 */
public class Http2TestServer implements AutoCloseable {
    static final AtomicLong IDS = new AtomicLong();
    final long id = IDS.incrementAndGet();
    final ServerSocket server;
    final boolean supportsHTTP11;
    volatile boolean secure;
    final ExecutorService exec;
    private volatile boolean stopping = false;
    final Map<String,Http2Handler> handlers;
    final SSLContext sslContext;
    final String serverName;
    final Set<Http2TestServerConnection> connections;
    final Properties properties;
    final String name;
    // request approver which takes the server connection key as the input
    private volatile Predicate<String> newRequestApprover;

    private static ExecutorService createExecutor(String name) {
        String threadNamePrefix = "%s-pool".formatted(name);
        ThreadFactory threadFactory = Thread.ofPlatform().name(threadNamePrefix, 0).factory();
        return Executors.newCachedThreadPool(threadFactory);
    }

    public Http2TestServer(String serverName, boolean secure, int port) throws Exception {
        this(serverName, secure, port, null, 50, null, null);
    }

    public Http2TestServer(boolean secure, int port) throws Exception {
        this(null, secure, port, null, 50, null, null);
    }

    public InetSocketAddress getAddress() {
        return (InetSocketAddress)server.getLocalSocketAddress();
    }

    public String serverAuthority() {
        final InetSocketAddress inetSockAddr = getAddress();
        final String hostIP = inetSockAddr.getAddress().getHostAddress();
        // escape for ipv6
        final String h = hostIP.contains(":") ? "[" + hostIP + "]" : hostIP;
        return h + ":" + inetSockAddr.getPort();
    }

    public Http2TestServer(boolean secure,
                           SSLContext context) throws Exception {
        this(null, secure, 0, null, 50, null, context);
    }

    public Http2TestServer(String serverName, boolean secure,
                           SSLContext context) throws Exception {
        this(serverName, secure, 0, null, 50, null, context);
    }

    public Http2TestServer(boolean secure,
                           int port,
                           ExecutorService exec,
                           SSLContext context) throws Exception {
        this(null, secure, port, exec, 50, null, context);
    }

    public Http2TestServer(String serverName,
                           boolean secure,
                           int port,
                           ExecutorService exec,
                           SSLContext context)
        throws Exception
    {
        this(serverName, secure, port, exec, 50, null, context);
    }

    public Http2TestServer(String serverName,
                           boolean secure,
                           int port,
                           ExecutorService exec,
                           int backlog,
                           Properties properties,
                           SSLContext context)
        throws Exception
    {
        this(serverName, secure, port, exec, backlog, properties, context, false);
    }

    public Http2TestServer(String serverName,
                           boolean secure,
                           int port,
                           ExecutorService exec,
                           int backlog,
                           Properties properties,
                           SSLContext context,
                           boolean supportsHTTP11)
        throws Exception
    {
        this(InetAddress.getLoopbackAddress(), serverName, secure, port, exec,
                backlog, properties, context, supportsHTTP11);
    }

    /**
     * Create a Http2Server listening on the given port. Currently needs
     * to know in advance whether incoming connections are plain TCP "h2c"
     * or TLS "h2".
     *
     * The HTTP/1.1 support, when supportsHTTP11 is true, is currently limited
     * to a canned 0-length response that contains the following headers:
     *       "X-Magic", "HTTP/1.1 request received by HTTP/2 server",
     *       "X-Received-Body", <the request body>);
     *
     * @param localAddr local address to bind to
     * @param serverName SNI servername
     * @param secure https or http
     * @param port listen port
     * @param exec executor service (cached thread pool is used if null)
     * @param backlog the server socket backlog
     * @param properties additional configuration properties
     * @param context the SSLContext used when secure is true
     * @param supportsHTTP11 if true, the server may issue an HTTP/1.1 response
     *        to either 1) a non-Upgrade HTTP/1.1 request, or 2) a secure
     *        connection without the h2 ALPN. Otherwise, false to operate in
     *        HTTP/2 mode exclusively.
     */
    public Http2TestServer(InetAddress localAddr,
                           String serverName,
                           boolean secure,
                           int port,
                           ExecutorService exec,
                           int backlog,
                           Properties properties,
                           SSLContext context,
                           boolean supportsHTTP11)
        throws Exception
    {
        this.name = "TestServer(%d)".formatted(id);
        this.serverName = serverName;
        this.supportsHTTP11 = supportsHTTP11;
        if (secure) {
           if (context != null)
               this.sslContext = context;
           else
               this.sslContext = SSLContext.getDefault();
            server = initSecure(localAddr, port, backlog);
        } else {
            this.sslContext = context;
            server = initPlaintext(port, backlog);
        }
        this.secure = secure;
        this.exec = exec == null ? createExecutor(name) : exec;
        this.handlers = Collections.synchronizedMap(new HashMap<>());
        this.properties = properties == null ? new Properties() : properties;
        this.connections = ConcurrentHashMap.newKeySet();
    }

    /**
     * Adds the given handler for the given path
     */
    public void addHandler(Http2Handler handler, String path) {
        handlers.put(path, handler);
    }

    volatile Http2TestExchangeSupplier exchangeSupplier = Http2TestExchangeSupplier.ofDefault();

    /**
     * Sets an explicit exchange handler to be used for all future connections.
     * Useful for testing scenarios where non-standard or specific server
     * behaviour is required, either direct control over the frames sent, "bad"
     * behaviour, or something else.
     */
    public void setExchangeSupplier(Http2TestExchangeSupplier exchangeSupplier) {
        this.exchangeSupplier = exchangeSupplier;
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
        System.err.println(name + ": Using handler for: " + bestMatch.get());
        return handler;
    }

    final ServerSocket initPlaintext(int port, int backlog) throws Exception {
        ServerSocket ss = new ServerSocket();
        ss.setReuseAddress(false);
        ss.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), backlog);
        return ss;
    }

    public synchronized void stop() {
        // TODO: clean shutdown GoAway
        stopping = true;
        System.err.printf("%s: stopping %d connections\n", name, connections.size());
        for (Http2TestServerConnection connection : connections) {
            connection.close(ErrorFrame.NO_ERROR);
        }
        try {
            server.close();
        } catch (IOException e) {}
        exec.shutdownNow();
    }


    final ServerSocket initSecure(InetAddress localAddr, int port, int backlog) throws Exception {
        ServerSocketFactory fac;
        SSLParameters sslp = null;
        fac = sslContext.getServerSocketFactory();
        sslp = sslContext.getSupportedSSLParameters();
        SSLServerSocket se = (SSLServerSocket) fac.createServerSocket();
        se.setReuseAddress(false);
        se.bind(new InetSocketAddress(localAddr, 0), backlog);
        if (supportsHTTP11) {
            sslp.setApplicationProtocols(new String[]{"h2", "http/1.1"});
        } else {
            sslp.setApplicationProtocols(new String[]{"h2"});
        }
        sslp.setEndpointIdentificationAlgorithm("HTTPS");
        se.setSSLParameters(sslp);
        se.setEnabledCipherSuites(se.getSupportedCipherSuites());
        se.setEnabledProtocols(se.getSupportedProtocols());
        // other initialisation here
        return se;
    }

    public String serverName() {
        return serverName;
    }

    public void setRequestApprover(final Predicate<String> approver) {
        this.newRequestApprover = approver;
    }

    Predicate<String> getRequestApprover() {
        return this.newRequestApprover;
    }

    private synchronized void putConnection(InetSocketAddress addr, Http2TestServerConnection c) {
        if (!stopping)
            connections.add(c);
    }

    private synchronized void removeConnection(InetSocketAddress addr, Http2TestServerConnection c) {
        connections.remove(c);
    }

    record AcceptedConnection(Http2TestServer server,
                              Socket socket) {
        void startConnection() {
            String name = server.name;
            Http2TestServerConnection c = null;
            InetSocketAddress addr = null;
            try {
                addr = (InetSocketAddress) socket.getRemoteSocketAddress();
                System.err.println(name + ": creating connection");
                c = server.createConnection(server, socket, server.exchangeSupplier);
                server.putConnection(addr, c);
                System.err.println(name + ": starting connection");
                c.run();
                System.err.println(name + ": connection started");
            } catch (Throwable e) {
                boolean stopping = server.stopping;
                if (!stopping) {
                    System.err.println(name + ": unexpected exception: " + e);
                    e.printStackTrace();
                }
                // we should not reach here, but if we do
                // the connection might not have been closed
                // and if so then the client might wait
                // forever.
                if (c != null) {
                    server.removeConnection(addr, c);
                }
                try {
                    if (c != null) c.close(ErrorFrame.PROTOCOL_ERROR);
                } catch (Exception x) {
                    if (!stopping)
                        System.err.println(name + ": failed to close connection: " + e);
                } finally {
                    try {
                        socket.close();
                    } catch (IOException x) {
                        if (!stopping)
                            System.err.println(name + ": failed to close socket: " + e);
                    }
                }
                System.err.println(name + ": failed to start connection: " + e);
            }
        }
    }

    /**
     * Starts a thread which waits for incoming connections.
     */
    public void start() {
        exec.submit(() -> {
            try {
                while (!stopping) {
                    System.err.println(name + ": accepting connections");
                    Socket socket = server.accept();
                    System.err.println(name + ": connection accepted");
                    try {
                        var accepted = new AcceptedConnection(this, socket);
                        exec.submit(accepted::startConnection);
                    } catch (Throwable e) {
                        if (!stopping) {
                            System.err.println(name + ": unexpected exception: " + e);
                            e.printStackTrace();
                        }
                        // we should not reach here, but if we do
                        // the connection might not have been closed
                        // and if so then the client might wait
                        // forever.
                        System.err.println(name + ": start exception: " + e);
                        e.printStackTrace();
                    }
                    System.err.println(name + ": stopping is: " + stopping);
                }
            } catch (SecurityException se) {
                System.err.println(name + ": terminating, caught " + se);
                se.printStackTrace();
                stopping = true;
                try { server.close(); } catch (IOException ioe) { /* ignore */}
            } catch (Throwable e) {
                if (!stopping) {
                    System.err.println(name + ": terminating, caught " + e);
                    e.printStackTrace();
                }
            } finally {
                System.err.println(name + ": finished");
            }
        });
    }

    protected Http2TestServerConnection createConnection(Http2TestServer http2TestServer,
                                                         Socket socket,
                                                         Http2TestExchangeSupplier exchangeSupplier)
            throws IOException {
        return new Http2TestServerConnection(http2TestServer, socket, exchangeSupplier, properties);
    }

    @Override
    public void close() throws Exception {
        System.err.println(name + ": closing");
        stop();
    }
}
