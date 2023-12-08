/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.management.remote.rest.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/**
 *
 */
public interface RestResource extends HttpHandler {
    @Override
    public default void handle(HttpExchange exchange) throws IOException {
        HttpResponse httpResponse = HttpResponse.METHOD_NOT_ALLOWED;
        switch (exchange.getRequestMethod()) {
            case "GET":
                httpResponse = doGet(exchange);
                break;
            case "POST":
                httpResponse = doPost(exchange);
                break;
            case "PUT":
                httpResponse = doPut(exchange);
                break;
            case "DELETE":
                httpResponse = doDelete(exchange);
                break;
            case "HEAD":
                httpResponse = doHead(exchange);
                break;
            case "PATCH":
                httpResponse = doPatch(exchange);
                break;
            case "CONNET":
                httpResponse = doConnect(exchange);
                break;
            case "TRACE":
                httpResponse = doTrace(exchange);
                break;
            case "OPTIONS":
                httpResponse = doOptions(exchange);
                break;
        }
        HttpUtil.sendResponse(exchange, httpResponse);
    }

    public default HttpResponse doGet(HttpExchange exchange) {
        return HttpResponse.METHOD_NOT_ALLOWED;
    }

    public default HttpResponse doPut(HttpExchange exchange) {
        return HttpResponse.METHOD_NOT_ALLOWED;
    }

    public default HttpResponse doPost(HttpExchange exchange) {
        return HttpResponse.METHOD_NOT_ALLOWED;
    }

    public default HttpResponse doDelete(HttpExchange exchange) {
        return HttpResponse.METHOD_NOT_ALLOWED;
    }

    public default HttpResponse doHead(HttpExchange exchange) {
        return HttpResponse.METHOD_NOT_ALLOWED;
    }

    public default HttpResponse doConnect(HttpExchange exchange) {
        return HttpResponse.METHOD_NOT_ALLOWED;
    }

    public default HttpResponse doOptions(HttpExchange exchange) {
        return HttpResponse.METHOD_NOT_ALLOWED;
    }

    public default HttpResponse doTrace(HttpExchange exchange) {
        return HttpResponse.METHOD_NOT_ALLOWED;
    }

    public default HttpResponse doPatch(HttpExchange exchange) {
        return HttpResponse.METHOD_NOT_ALLOWED;
    }
}
