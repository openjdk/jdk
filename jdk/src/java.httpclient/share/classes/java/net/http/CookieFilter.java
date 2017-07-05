/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 */

package java.net.http;

import java.io.IOException;
import java.net.CookieManager;
import java.util.List;
import java.util.Map;
import java.util.Set;

class CookieFilter implements HeaderFilter {

    final HttpClientImpl client;
    final CookieManager cookieMan;

    CookieFilter(HttpClientImpl client) {
        this.client = client;
        this.cookieMan = client.cookieManager().orElseThrow(
                () -> new IllegalArgumentException("no cookie manager"));
    }

    @Override
    public void request(HttpRequestImpl r) throws IOException {
        Map<String,List<String>> userheaders, cookies;
        userheaders = r.getUserHeaders().map();
        cookies = cookieMan.get(r.uri(), userheaders);
        // add the returned cookies
        HttpHeadersImpl systemHeaders = r.getSystemHeaders();
        Set<String> keys = cookies.keySet();
        for (String hdrname : keys) {
            List<String> vals = cookies.get(hdrname);
            for (String val : vals) {
                systemHeaders.addHeader(hdrname, val);
            }
        }
    }

    @Override
    public HttpRequestImpl response(HttpResponseImpl r) throws IOException {
        HttpHeaders hdrs = r.headers();
        cookieMan.put(r.uri(), hdrs.map());
        return null;
    }
}
