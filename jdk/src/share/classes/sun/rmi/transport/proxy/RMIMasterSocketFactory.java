/*
 * Copyright (c) 1996, 2013, Oracle and/or its affiliates. All rights reserved.
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
package sun.rmi.transport.proxy;

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.rmi.server.LogStream;
import java.rmi.server.RMISocketFactory;
import sun.rmi.runtime.Log;
import sun.rmi.runtime.NewThreadAction;
import sun.security.action.GetBooleanAction;
import sun.security.action.GetLongAction;
import sun.security.action.GetPropertyAction;

/**
 * RMIMasterSocketFactory attempts to create a socket connection to the
 * specified host using successively less efficient mechanisms
 * until one succeeds.  If the host is successfully connected to,
 * the factory for the successful mechanism is stored in an internal
 * hash table keyed by the host name, so that future attempts to
 * connect to the same host will automatically use the same
 * mechanism.
 */
@SuppressWarnings("deprecation")
public class RMIMasterSocketFactory extends RMISocketFactory {

    /** "proxy" package log level */
    static int logLevel = LogStream.parseLevel(getLogLevel());

    private static String getLogLevel() {
        return java.security.AccessController.doPrivileged(
            new sun.security.action.GetPropertyAction("sun.rmi.transport.proxy.logLevel"));
    }

    /* proxy package log */
    static final Log proxyLog =
        Log.getLog("sun.rmi.transport.tcp.proxy",
                   "transport", RMIMasterSocketFactory.logLevel);

    /** timeout for attemping direct socket connections */
    private static long connectTimeout = getConnectTimeout();

    private static long getConnectTimeout() {
        return java.security.AccessController.doPrivileged(
                new GetLongAction("sun.rmi.transport.proxy.connectTimeout",
                              15000)).longValue(); // default: 15 seconds
    }

    /** whether to fallback to HTTP on general connect failures */
    private static final boolean eagerHttpFallback =
        java.security.AccessController.doPrivileged(new GetBooleanAction(
            "sun.rmi.transport.proxy.eagerHttpFallback")).booleanValue();

    /** table of hosts successfully connected to and the factory used */
    private Hashtable<String, RMISocketFactory> successTable =
        new Hashtable<>();

    /** maximum number of hosts to remember successful connection to */
    private static final int MaxRememberedHosts = 64;

    /** list of the hosts in successTable in initial connection order */
    private Vector<String> hostList = new Vector<>(MaxRememberedHosts);

    /** default factory for initial use for direct socket connection */
    protected RMISocketFactory initialFactory = new RMIDirectSocketFactory();

    /** ordered list of factories to try as alternate connection
      * mechanisms if a direct socket connections fails */
    protected Vector<RMISocketFactory> altFactoryList;

    /**
     * Create a RMIMasterSocketFactory object.  Establish order of
     * connection mechanisms to attempt on createSocket, if a direct
     * socket connection fails.
     */
    public RMIMasterSocketFactory() {
        altFactoryList = new Vector<>(2);
        boolean setFactories = false;

        try {
            String proxyHost;
            proxyHost = java.security.AccessController.doPrivileged(
                new GetPropertyAction("http.proxyHost"));

            if (proxyHost == null)
                proxyHost = java.security.AccessController.doPrivileged(
                    new GetPropertyAction("proxyHost"));

            boolean disable = java.security.AccessController.doPrivileged(
                new GetPropertyAction("java.rmi.server.disableHttp", "true"))
                .equalsIgnoreCase("true");

            if (!disable && proxyHost != null && proxyHost.length() > 0) {
                setFactories = true;
            }
        } catch (Exception e) {
            // unable to obtain the properties, so use the default behavior.
        }

        if (setFactories) {
            altFactoryList.addElement(new RMIHttpToPortSocketFactory());
            altFactoryList.addElement(new RMIHttpToCGISocketFactory());
        }
    }

    /**
     * Create a new client socket.  If we remember connecting to this host
     * successfully before, then use the same factory again.  Otherwise,
     * try using a direct socket connection and then the alternate factories
     * in the order specified in altFactoryList.
     */
    public Socket createSocket(String host, int port)
        throws IOException
    {
        if (proxyLog.isLoggable(Log.BRIEF)) {
            proxyLog.log(Log.BRIEF, "host: " + host + ", port: " + port);
        }

        /*
         * If we don't have any alternate factories to consult, short circuit
         * the fallback procedure and delegate to the initial factory.
         */
        if (altFactoryList.size() == 0) {
            return initialFactory.createSocket(host, port);
        }

        RMISocketFactory factory;

        /*
         * If we remember successfully connecting to this host before,
         * use the same factory.
         */
        factory = successTable.get(host);
        if (factory != null) {
            if (proxyLog.isLoggable(Log.BRIEF)) {
                proxyLog.log(Log.BRIEF,
                    "previously successful factory found: " + factory);
            }
            return factory.createSocket(host, port);
        }

        /*
         * Next, try a direct socket connection.  Open socket in another
         * thread and only wait for specified timeout, in case the socket
         * would otherwise spend minutes trying an unreachable host.
         */
        Socket initialSocket = null;
        Socket fallbackSocket = null;
        final AsyncConnector connector =
            new AsyncConnector(initialFactory, host, port,
                AccessController.getContext());
                // connection must be attempted with
                // this thread's access control context
        IOException initialFailure = null;

        try {
            synchronized (connector) {

                Thread t = java.security.AccessController.doPrivileged(
                    new NewThreadAction(connector, "AsyncConnector", true));
                t.start();

                try {
                    long now = System.currentTimeMillis();
                    long deadline = now + connectTimeout;
                    do {
                        connector.wait(deadline - now);
                        initialSocket = checkConnector(connector);
                        if (initialSocket != null)
                            break;
                        now = System.currentTimeMillis();
                    } while (now < deadline);
                } catch (InterruptedException e) {
                    throw new InterruptedIOException(
                        "interrupted while waiting for connector");
                }
            }

            // assume no route to host (for now) if no connection yet
            if (initialSocket == null)
                throw new NoRouteToHostException(
                    "connect timed out: " + host);

            proxyLog.log(Log.BRIEF, "direct socket connection successful");

            return initialSocket;

        } catch (UnknownHostException | NoRouteToHostException e) {
            initialFailure = e;
        } catch (SocketException e) {
            if (eagerHttpFallback) {
                initialFailure = e;
            } else {
                throw e;
            }
        } finally {
            if (initialFailure != null) {

                if (proxyLog.isLoggable(Log.BRIEF)) {
                    proxyLog.log(Log.BRIEF,
                        "direct socket connection failed: ", initialFailure);
                }

                // Finally, try any alternate connection mechanisms.
                for (int i = 0; i < altFactoryList.size(); ++ i) {
                    factory = altFactoryList.elementAt(i);
                    if (proxyLog.isLoggable(Log.BRIEF)) {
                        proxyLog.log(Log.BRIEF,
                            "trying with factory: " + factory);
                    }
                    try (Socket testSocket =
                            factory.createSocket(host, port)) {
                        // For HTTP connections, the output (POST request) must
                        // be sent before we verify a successful connection.
                        // So, sacrifice a socket for the sake of testing...
                        // The following sequence should verify a successful
                        // HTTP connection if no IOException is thrown.
                        InputStream in = testSocket.getInputStream();
                        int b = in.read(); // probably -1 for EOF...
                    } catch (IOException ex) {
                        if (proxyLog.isLoggable(Log.BRIEF)) {
                            proxyLog.log(Log.BRIEF, "factory failed: ", ex);
                        }

                        continue;
                    }
                    proxyLog.log(Log.BRIEF, "factory succeeded");

                    // factory succeeded, open new socket for caller's use
                    try {
                        fallbackSocket = factory.createSocket(host, port);
                    } catch (IOException ex) {  // if it fails 2nd time,
                    }                           // just give up
                    break;
                }
            }
        }

        synchronized (successTable) {
            try {
                // check once again to see if direct connection succeeded
                synchronized (connector) {
                    initialSocket = checkConnector(connector);
                }
                if (initialSocket != null) {
                    // if we had made another one as well, clean it up...
                    if (fallbackSocket != null)
                        fallbackSocket.close();
                    return initialSocket;
                }
                // if connector ever does get socket, it won't be used
                connector.notUsed();
            } catch (UnknownHostException | NoRouteToHostException e) {
                initialFailure = e;
            } catch (SocketException e) {
                if (eagerHttpFallback) {
                    initialFailure = e;
                } else {
                    throw e;
                }
            }
            // if we had found an alternate mechanism, go and use it
            if (fallbackSocket != null) {
                // remember this successful host/factory pair
                rememberFactory(host, factory);
                return fallbackSocket;
            }
            throw initialFailure;
        }
    }

    /**
     * Remember a successful factory for connecting to host.
     * Currently, excess hosts are removed from the remembered list
     * using a Least Recently Created strategy.
     */
    void rememberFactory(String host, RMISocketFactory factory) {
        synchronized (successTable) {
            while (hostList.size() >= MaxRememberedHosts) {
                successTable.remove(hostList.elementAt(0));
                hostList.removeElementAt(0);
            }
            hostList.addElement(host);
            successTable.put(host, factory);
        }
    }

    /**
     * Check if an AsyncConnector succeeded.  If not, return socket
     * given to fall back to.
     */
    Socket checkConnector(AsyncConnector connector)
        throws IOException
    {
        Exception e = connector.getException();
        if (e != null) {
            e.fillInStackTrace();
            /*
             * The AsyncConnector implementation guaranteed that the exception
             * will be either an IOException or a RuntimeException, and we can
             * only throw one of those, so convince that compiler that it must
             * be one of those.
             */
            if (e instanceof IOException) {
                throw (IOException) e;
            } else if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new Error("internal error: " +
                    "unexpected checked exception: " + e.toString());
            }
        }
        return connector.getSocket();
    }

    /**
     * Create a new server socket.
     */
    public ServerSocket createServerSocket(int port) throws IOException {
        //return new HttpAwareServerSocket(port);
        return initialFactory.createServerSocket(port);
    }


    /**
     * AsyncConnector is used by RMIMasterSocketFactory to attempt socket
     * connections on a separate thread.  This allows RMIMasterSocketFactory
     * to control how long it will wait for the connection to succeed.
     */
    private class AsyncConnector implements Runnable {

        /** what factory to use to attempt connection */
        private RMISocketFactory factory;

        /** the host to connect to */
        private String host;

        /** the port to connect to */
        private int port;

        /** access control context to attempt connection within */
        private AccessControlContext acc;

        /** exception that occurred during connection, if any */
        private Exception exception = null;

        /** the connected socket, if successful */
        private Socket socket = null;

        /** socket should be closed after created, if ever */
        private boolean cleanUp = false;

        /**
         * Create a new asynchronous connector object.
         */
        AsyncConnector(RMISocketFactory factory, String host, int port,
                       AccessControlContext acc)
        {
            this.factory = factory;
            this.host    = host;
            this.port    = port;
            this.acc     = acc;
            SecurityManager security = System.getSecurityManager();
            if (security != null) {
                security.checkConnect(host, port);
            }
        }

        /**
         * Attempt socket connection in separate thread.  If successful,
         * notify master waiting,
         */
        public void run() {
            try {
                /*
                 * Using the privileges of the thread that wants to make the
                 * connection is tempting, but it will fail with applets with
                 * the current applet security manager because the applet
                 * network connection policy is not captured in the permission
                 * framework of the access control context we have.
                 *
                 * java.security.AccessController.beginPrivileged(acc);
                 */
                try {
                    Socket temp = factory.createSocket(host, port);
                    synchronized (this) {
                        socket = temp;
                        notify();
                    }
                    rememberFactory(host, factory);
                    synchronized (this) {
                        if (cleanUp)
                          try {
                              socket.close();
                          } catch (IOException e) {
                          }
                    }
                } catch (Exception e) {
                    /*
                     * Note that the only exceptions which could actually have
                     * occurred here are IOException or RuntimeException.
                     */
                    synchronized (this) {
                        exception = e;
                        notify();
                    }
                }
            } finally {
                /*
                 * See above comments for matching beginPrivileged() call that
                 * is also commented out.
                 *
                 * java.security.AccessController.endPrivileged();
                 */
            }
        }

        /**
         * Get exception that occurred during connection attempt, if any.
         * In the current implementation, this is guaranteed to be either
         * an IOException or a RuntimeException.
         */
        private synchronized Exception getException() {
            return exception;
        }

        /**
         * Get successful socket, if any.
         */
        private synchronized Socket getSocket() {
            return socket;
        }

        /**
         * Note that this connector's socket, if ever successfully created,
         * will not be used, so it should be cleaned up quickly
         */
        synchronized void notUsed() {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
            cleanUp = true;
        }
    }
}
