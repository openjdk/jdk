/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.net.httpserver;

import java.net.*;
import java.io.*;
import java.nio.*;
import java.security.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import com.sun.net.httpserver.*;
import com.sun.net.httpserver.spi.*;

public class HttpsServerImpl extends HttpsServer {

    ServerImpl server;

    HttpsServerImpl () throws IOException {
        this (new InetSocketAddress(443), 0);
    }

    HttpsServerImpl (
        InetSocketAddress addr, int backlog
    ) throws IOException {
        server = new ServerImpl (this, "https", addr, backlog);
    }

    public void setHttpsConfigurator (HttpsConfigurator config) {
        server.setHttpsConfigurator (config);
    }

    public HttpsConfigurator getHttpsConfigurator () {
        return server.getHttpsConfigurator();
    }

    public void bind (InetSocketAddress addr, int backlog) throws IOException {
        server.bind (addr, backlog);
    }

    public void start () {
        server.start();
    }

    public void setExecutor (Executor executor) {
        server.setExecutor(executor);
    }

    public Executor getExecutor () {
        return server.getExecutor();
    }

    public void stop (int delay) {
        server.stop (delay);
    }

    public HttpContextImpl createContext (String path, HttpHandler handler) {
        return server.createContext (path, handler);
    }

    public HttpContextImpl createContext (String path) {
        return server.createContext (path);
    }

    public void removeContext (String path) throws IllegalArgumentException {
        server.removeContext (path);
    }

    public void removeContext (HttpContext context) throws IllegalArgumentException {
        server.removeContext (context);
    }

    public InetSocketAddress getAddress() {
        return server.getAddress();
    }
}
