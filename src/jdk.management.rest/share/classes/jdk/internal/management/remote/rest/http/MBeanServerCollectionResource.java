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

import jdk.internal.management.remote.rest.json.JSONArray;
import jdk.internal.management.remote.rest.json.JSONObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import jdk.internal.management.remote.rest.PlatformRestAdapter;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * This class handles all the HTTP requests for the base URL
 * for REST adapter.
 */
public class MBeanServerCollectionResource implements RestResource {

    private final List<MBeanServerResource> restAdapters;
    private final int pageSize = 5;

    public MBeanServerCollectionResource(List<MBeanServerResource> adapters, HttpServer server) {
        this.restAdapters = adapters;
        server.createContext("/jmx/servers", this);
    }

    @Override
    public HttpResponse doGet(HttpExchange exchange) {
        try {
            JSONObject _links = HttpUtil.getPaginationLinks(exchange, restAdapters, pageSize);
            List<MBeanServerResource> filteredList = HttpUtil.filterByPage(exchange, restAdapters, pageSize);
            if (filteredList == null) {
                return HttpResponse.OK;
            }
            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                return HttpResponse.BAD_REQUEST;
            }
            String exchangePath = URLDecoder.decode(exchange.getRequestURI().getPath(), StandardCharsets.UTF_8.displayName())
                    .replaceAll("/$", "");
            if (!exchangePath.equalsIgnoreCase("/jmx/servers")) {
                return HttpResponse.REQUEST_NOT_FOUND;
            }
            final String path = PlatformRestAdapter.getDomain() + exchangePath;

            JSONObject root = new JSONObject();
            if (_links != null && !_links.isEmpty()) {
                root.put("_links", _links);
            }

            root.put("mBeanServerCount", Integer.toString(restAdapters.size()));

            JSONArray list = new JSONArray();
            filteredList.stream().map((adapter) -> {
                JSONObject result = new JSONObject();
                result.put("name", adapter.getContext());
                result.put("href", path + "/" + adapter.getContext());
                return result;
            }).forEachOrdered((result) -> {
                list.add(result);
            });
            root.put("mBeanServers", list);
            return new HttpResponse(HttpURLConnection.HTTP_OK, root.toJsonString());
        } catch (UnsupportedEncodingException e) {
            return new HttpResponse(HttpResponse.BAD_REQUEST,
                    HttpUtil.getRequestCharset(exchange) + " is not supported");
        }
    }
}
