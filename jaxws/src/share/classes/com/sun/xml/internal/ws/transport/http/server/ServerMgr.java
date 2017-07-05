/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.xml.internal.ws.transport.http.server;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpServer;
import com.sun.xml.internal.ws.server.ServerRtException;

import java.net.InetSocketAddress;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Manages all the WebService HTTP servers created by JAXWS runtime.
 *
 * @author Jitendra Kotamraju
 */
final class ServerMgr {

    private static final ServerMgr serverMgr = new ServerMgr();
    private static final Logger logger =
        Logger.getLogger(
            com.sun.xml.internal.ws.util.Constants.LoggingDomain + ".server.http");
    private final Map<InetSocketAddress,ServerState> servers = new HashMap<InetSocketAddress,ServerState>();

    private ServerMgr() {}

    /**
     * Gets the singleton instance.
     * @return manager instance
     */
    static ServerMgr getInstance() {
        return serverMgr;
    }

    /*
     * Creates a HttpContext at the given address. If there is already a server
     * it uses that server to create a context. Otherwise, it creates a new
     * HTTP server. This sever is added to servers Map.
     */
    /*package*/ HttpContext createContext(String address) {
        try {
            HttpServer server;
            ServerState state;
            URL url = new URL(address);
            int port = url.getPort();
            if (port == -1) {
                port = url.getDefaultPort();
            }
            InetSocketAddress inetAddress = new InetSocketAddress(url.getHost(),
                port);
            synchronized(servers) {
                state = servers.get(inetAddress);
                if (state == null) {
                    logger.fine("Creating new HTTP Server at "+inetAddress);
                    server = HttpServer.create(inetAddress, 5);
                    server.setExecutor(Executors.newCachedThreadPool());
                    String path = url.toURI().getPath();
                    logger.fine("Creating HTTP Context at = "+path);
                    HttpContext context = server.createContext(path);
                    server.start();
                    logger.fine("HTTP server started = "+inetAddress);
                    state = new ServerState(server);
                    servers.put(inetAddress, state);
                    return context;
                }
            }
            server = state.getServer();
            logger.fine("Creating HTTP Context at = "+url.getPath());
            HttpContext context = server.createContext(url.getPath());
            state.oneMoreContext();
            return context;
        } catch(Exception e) {
            throw new ServerRtException("server.rt.err",e );
        }
    }

    /*
     * Removes a context. If the server doesn't have anymore contexts, it
     * would stop the server and server is removed from servers Map.
     */
    /*package*/ void removeContext(HttpContext context) {
        InetSocketAddress inetAddress = context.getServer().getAddress();
        synchronized(servers) {
            ServerState state = servers.get(inetAddress);
            int instances = state.noOfContexts();
            if (instances < 2) {
                ((ExecutorService)state.getServer().getExecutor()).shutdown();
                state.getServer().stop(0);
                servers.remove(inetAddress);
            } else {
                state.getServer().removeContext(context);
                state.oneLessContext();
            }
        }
    }

    private static final class ServerState {
        private final HttpServer server;
        private int instances;

        ServerState(HttpServer server) {
            this.server = server;
            this.instances = 1;
        }

        public HttpServer getServer() {
            return server;
        }

        public void oneMoreContext() {
            ++instances;
        }

        public void oneLessContext() {
            --instances;
        }

        public int noOfContexts() {
            return instances;
        }
    }
}
