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

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.xml.internal.ws.resources.HttpserverMessages;
import com.sun.xml.internal.ws.transport.http.HttpAdapter;
import com.sun.xml.internal.ws.transport.http.WSHTTPConnection;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * {@link HttpHandler} implementation that serves the actual request.
 *
 * @author Jitendra Kotamraju
 * @author Kohsuke Kawaguhi
 */
final class WSHttpHandler implements HttpHandler {

    private static final String GET_METHOD = "GET";
    private static final String POST_METHOD = "POST";
    private static final String HEAD_METHOD = "HEAD";
    private static final String PUT_METHOD = "PUT";
    private static final String DELETE_METHOD = "DELETE";

    private static final Logger logger =
        Logger.getLogger(
            com.sun.xml.internal.ws.util.Constants.LoggingDomain + ".server.http");

    private final HttpAdapter adapter;
    private final Executor executor;

    public WSHttpHandler(@NotNull HttpAdapter adapter, @Nullable Executor executor) {
        assert adapter!=null;
        this.adapter = adapter;
        this.executor = executor;
    }

    /**
     * Called by HttpServer when there is a matching request for the context
     */
    public void handle(HttpExchange msg) {
        try {
            logger.fine("Received HTTP request:"+msg.getRequestURI());
            if (executor != null) {
                // Use application's Executor to handle request. Application may
                // have set an executor using Endpoint.setExecutor().
                executor.execute(new HttpHandlerRunnable(msg));
            } else {
                handleExchange(msg);
            }
        } catch(Throwable e) {
            // Dont't propagate the exception otherwise it kills the httpserver
            e.printStackTrace();
        }
    }

    public void handleExchange(HttpExchange msg) throws IOException {
        WSHTTPConnection con = new ServerConnectionImpl(adapter,msg);
        try {
            logger.fine("Received HTTP request:"+msg.getRequestURI());
            String method = msg.getRequestMethod();
            if (method.equals(GET_METHOD)) {
                String queryString = msg.getRequestURI().getQuery();
                logger.fine("Query String for request ="+queryString);
                if (adapter.isMetadataQuery(queryString)) {
                    adapter.publishWSDL(con,getRequestAddress(msg), msg.getRequestURI().getQuery());
                } else {
                    adapter.handle(con);
                }
            } else if (method.equals(POST_METHOD) || method.equals(HEAD_METHOD)
                        || method.equals(PUT_METHOD) || method.equals(DELETE_METHOD)) {
                adapter.handle(con);
            } else {
                logger.warning(HttpserverMessages.UNEXPECTED_HTTP_METHOD(method));
            }
        } finally {
            msg.close();
        }
    }

    /**
     * Wrapping the processing of request in a Runnable so that it can be
     * executed in Executor.
     */
    class HttpHandlerRunnable implements Runnable {
        final HttpExchange msg;

        HttpHandlerRunnable(HttpExchange msg) {
            this.msg = msg;
        }

        public void run() {
            try {
                handleExchange(msg);
            } catch (Throwable e) {
                // Does application's executor handle this exception ?
                e.printStackTrace();
            }
        }
    }


    /**
     * Computes the Endpoint's address from the request. Use "Host" header
     * so that it has correct address(IP address or someother hostname) through
     * which the application reached the endpoint.
     *
     * @return
     *      a string like "http://foo.bar:1234/abc/def"
     */
    static @NotNull String getRequestAddress(HttpExchange msg) {
        StringBuilder strBuf = new StringBuilder();
        strBuf.append((msg instanceof HttpsExchange) ? "https" : "http");
        strBuf.append("://");

        List<String> hostHeader = msg.getRequestHeaders().get("Host");
        if (hostHeader != null) {
            strBuf.append(hostHeader.get(0));   // Uses Host header
        } else {
            strBuf.append(msg.getLocalAddress().getHostName());
            strBuf.append(":");
            strBuf.append(msg.getLocalAddress().getPort());
        }
        strBuf.append(msg.getRequestURI().getPath());

        return strBuf.toString();
    }
}
