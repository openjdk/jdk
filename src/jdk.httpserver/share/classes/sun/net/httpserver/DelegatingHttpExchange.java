/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package sun.net.httpserver;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;

public abstract class DelegatingHttpExchange extends HttpExchange {

    private final HttpExchange exchange;

    public DelegatingHttpExchange(HttpExchange ex) {
        this.exchange = ex;
    }

    public abstract Headers getRequestHeaders();

    public abstract String getRequestMethod();

    public abstract URI getRequestURI();

    public Headers getResponseHeaders() {
        return exchange.getResponseHeaders();
    }

    public HttpContext getHttpContext() {
        return exchange.getHttpContext();
    }

    public void close() {
        exchange.close();
    }

    public InputStream getRequestBody() {
        return exchange.getRequestBody();
    }

    public int getResponseCode() {
        return exchange.getResponseCode();
    }

    public OutputStream getResponseBody() {
        return exchange.getResponseBody();
    }

    public void sendResponseHeaders(int rCode, long contentLen) throws IOException {
        exchange.sendResponseHeaders(rCode, contentLen);
    }

    public InetSocketAddress getRemoteAddress() {
        return exchange.getRemoteAddress();
    }

    public InetSocketAddress getLocalAddress() {
        return exchange.getLocalAddress();
    }

    public String getProtocol() {
        return exchange.getProtocol();
    }

    public Object getAttribute(String name) {
        return exchange.getAttribute(name);
    }

    public void setAttribute(String name, Object value) {
        exchange.setAttribute(name, value);
    }

    public void setStreams(InputStream i, OutputStream o) {
        exchange.setStreams(i, o);
    }

    public HttpPrincipal getPrincipal() {
        return exchange.getPrincipal();
    }
}
