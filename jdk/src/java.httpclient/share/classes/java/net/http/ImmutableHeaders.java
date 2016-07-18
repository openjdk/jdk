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
 * questions.
 */

package java.net.http;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Immutable HttpHeaders constructed from mutable HttpHeadersImpl.
 */

class ImmutableHeaders implements HttpHeaders {

    private final Map<String,List<String>> map;

    @SuppressWarnings("unchecked")
    ImmutableHeaders() {
        map = (Map<String,List<String>>)Collections.EMPTY_MAP;
    }
    // TODO: fix lower case issue. Must be lc for http/2 compares ignoreCase for http/1
    ImmutableHeaders(HttpHeadersImpl h, Predicate<String> keyAllowed) {
        Map<String,List<String>> src = h.directMap();
        Map<String,List<String>> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        src.forEach((key, value) -> {
            if (keyAllowed.test(key))
                m.put(key, Collections.unmodifiableList(value));
        });
        map = Collections.unmodifiableMap(m);
    }

    @Override
    public Optional<String> firstValue(String name) {
        List<String> l = map.get(name);
        String v = l == null ? null : l.get(0);
        return Optional.ofNullable(v);
    }

    @Override
    public Optional<Long> firstValueAsLong(String name) {
        return firstValue(name).map((v -> Long.parseLong(v)));
    }

    @Override
    public List<String> allValues(String name) {
        return map.get(name);
    }

    @Override
    public Map<String, List<String>> map() {
        return map;
    }
}
