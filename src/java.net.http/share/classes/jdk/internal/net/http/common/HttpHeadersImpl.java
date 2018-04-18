/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.net.http.common;

import java.net.http.HttpHeaders;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Implementation of HttpHeaders.
 *
 * The public HttpHeaders API provides a read-only view, while the
 * non-HttpHeaders members allow for implementation specific mutation, e.g.
 * during creation, etc.
 */
public class HttpHeadersImpl extends HttpHeaders {

    private final TreeMap<String, List<String>> headers;

    public HttpHeadersImpl() {
        headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }

    @Override
    public Map<String, List<String>> map() {
        return Collections.unmodifiableMap(headersMap());
    }

    // non-HttpHeaders private mutators

    public HttpHeadersImpl deepCopy() {
        HttpHeadersImpl h1 = newDeepCopy();
        for (Map.Entry<String, List<String>> entry : headersMap().entrySet()) {
            List<String> valuesCopy = new ArrayList<>(entry.getValue());
            h1.headersMap().put(entry.getKey(), valuesCopy);
        }
        return h1;
    }

    public void addHeader(String name, String value) {
        headersMap().computeIfAbsent(name, k -> new ArrayList<>(1))
                    .add(value);
    }

    public void setHeader(String name, String value) {
        // headers typically have one value
        List<String> values = new ArrayList<>(1);
        values.add(value);
        headersMap().put(name, values);
    }

    public void clear() {
        headersMap().clear();
    }

    protected HttpHeadersImpl newDeepCopy() {
        return new HttpHeadersImpl();
    }

    protected Map<String, List<String>> headersMap() {
        return headers;
    }
}
