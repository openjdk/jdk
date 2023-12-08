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

import jdk.internal.management.remote.rest.json.JSONObject;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import jdk.internal.management.remote.rest.PlatformRestAdapter;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A utility class for commonly used operations on {@link HttpExchange}
 */
public class HttpUtil {

    public static String getRequestCharset(HttpExchange ex) {
        String charset = null;
        List<String> contentType = ex.getRequestHeaders().get("Content-type");
        if (contentType != null) {
            for (String kv : contentType) {
                for (String value : kv.split(";")) {
                    value = value.trim();
                    if (value.toLowerCase().startsWith("charset=")) {
                        charset = value.substring("charset=".length());
                    }
                }
            }
        }
        return charset;
    }

    public static String getAcceptCharset(HttpExchange ex) {
        List<String> acceptCharset = ex.getRequestHeaders().get("Accept-Charset");
        if (acceptCharset != null && acceptCharset.size() > 0) {
            return acceptCharset.get(0);
        }
        return null;
    }

    public static String getGetRequestResource(HttpExchange ex) throws UnsupportedEncodingException {
        String charset = getRequestCharset(ex);
        String httpHandlerPath = ex.getHttpContext().getPath();
        String requestURIpath = ex.getRequestURI().getPath();
        String getRequestPath = requestURIpath.substring(httpHandlerPath.length());
        if (charset != null) {
            return URLDecoder.decode(getRequestPath, charset);
        } else {
            return getRequestPath;
        }
    }

    public static String getGetRequestQuery(HttpExchange ex) throws UnsupportedEncodingException {
        String charset = getRequestCharset(ex);
        String query = ex.getRequestURI().getQuery();
        if (charset != null && query != null) {
            return URLDecoder.decode(query, charset);
        } else {
            return query;
        }
    }

    public static Map<String, String> getGetRequestQueryMap(HttpExchange ex)
            throws UnsupportedEncodingException {
        String query = ex.getRequestURI().getQuery();
        Map<String, String> queryParams = new LinkedHashMap<>();

        if (query == null || query.isEmpty()) {
            return queryParams;
        }
        query = URLDecoder.decode(query, StandardCharsets.UTF_8.displayName());
        String[] params = query.trim().split("&");
        for (String param : params) {
            int idx = param.indexOf('=');
            if (idx != -1) {
                queryParams.put(param.substring(0, idx), param.substring(idx + 1));
            }
        }
        return queryParams;
    }

    public static String getCredentials(HttpExchange exchange) {
        Headers rmap = exchange.getRequestHeaders();
        String auth = rmap.getFirst("Authorization");
        if (auth != null && !auth.isEmpty()) {
            int sp = auth.indexOf(' ');
            byte[] b = Base64.getDecoder().decode(auth.substring(sp + 1));
            String authCredentials = new String(b);
            int colon = authCredentials.indexOf(':');
            return authCredentials.substring(0, colon);
        }
        return "";
    }

    public static String readRequestBody(HttpExchange he) throws IOException {
        String charset = getRequestCharset(he);
        StringBuilder stringBuilder = new StringBuilder();
        InputStreamReader in = charset != null ? new InputStreamReader(he.getRequestBody(), charset) : new InputStreamReader(he.getRequestBody());
        BufferedReader br = new BufferedReader(in);
        String line;
        while ((line = br.readLine()) != null) {
            String decode = charset != null ? URLDecoder.decode(line, charset) : line;
            stringBuilder.append(decode);
        }
        return stringBuilder.toString();
    }

    public static void sendResponse(HttpExchange exchange, HttpResponse response) throws IOException {
        String charset = getRequestCharset(exchange);
        String acceptCharset = HttpUtil.getAcceptCharset(exchange);
        if (acceptCharset != null) {
            charset = acceptCharset;
        }

        // Set response headers explicitly
        String msg = charset == null ? response.getBody() : URLEncoder.encode(response.getBody(), charset);
        byte[] bytes = msg.getBytes();
        Headers resHeaders = exchange.getResponseHeaders();
        if (charset != null && !charset.isEmpty()) {
            resHeaders.add("Content-Type", "application/json; charset=" + charset);
        } else {
            resHeaders.add("Content-Type", "application/json;");
        }

        exchange.sendResponseHeaders(response.getCode(), bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static <T> List<T> filterByPage(HttpExchange exchange, List<T> input, int pageSize) throws UnsupportedEncodingException {

        Map<String, String> queryParams = HttpUtil.getGetRequestQueryMap(exchange);
        int currPage = 1;
        if (queryParams != null && !queryParams.isEmpty()) {
            String pageStr = queryParams.get("page");
            if (pageStr != null && !pageStr.isEmpty()) {
                currPage = Integer.parseInt(pageStr);
                currPage = currPage > 1 ? currPage : 1;
            }
        }

        if (input.size() <= pageSize) {
            return input;
        }

        int start = (currPage - 1) * pageSize;
        int end = Math.min(input.size(), start + pageSize);
        if (start < end) {
            return input.subList(start, end);
        } else {
            return null;
        }
    }

    public static <T> JSONObject getPaginationLinks(HttpExchange exchange, List<T> input, int pageSize) throws UnsupportedEncodingException {

        if (pageSize >= input.size()) {
            return null;
        }

        Map<String, String> queryParams = HttpUtil.getGetRequestQueryMap(exchange);
        int currPage = 1;
        if (queryParams != null && !queryParams.isEmpty()) {
            String pageStr = queryParams.get("page");
            if (pageStr != null && !pageStr.isEmpty()) {
                currPage = Integer.parseInt(pageStr);
            }
        }
        String path = PlatformRestAdapter.getDomain() + exchange.getRequestURI().getPath() + "?";
        Map<String, String> queryMap = getGetRequestQueryMap(exchange);
        if (queryMap != null) {
            queryMap.remove("page");
            if (!queryMap.isEmpty()) {
                String query = queryMap.keySet().stream()
                        .map(k -> k + "=" + queryMap.get(k))
                        .collect(Collectors.joining("&"));
                path = path + query + "&";
            }
        }
        int totalPages = (input.size() % pageSize == 0) ? input.size() / pageSize : input.size() / pageSize + 1;

        JSONObject jobj = new JSONObject();
        jobj.put("first", path.replaceAll(".$", ""));
        if (currPage == 2) {
            jobj.put("prev", path.replaceAll(".$", ""));
        } else if (currPage > 2) {
            jobj.put("prev", path + "page=" + (currPage - 1));
        }
        if (currPage < totalPages) {
            jobj.put("next", path + "page=" + (currPage + 1));
        }
        jobj.put("last", path + "page=" + totalPages);

        return jobj;
    }

    @SuppressWarnings("deprecation")
    public static String escapeUrl(String input) {
        try {
            URL url = new URL(input);
            URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef());
            return uri.toURL().toString();
        } catch (Exception ex) {
            return null;
        }
    }

}
