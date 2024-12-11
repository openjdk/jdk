/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import java.util.function.BiFunction;
import com.sun.net.httpserver.*;

public class UnmodifiableHeaders extends Headers {

    private final Headers headers;  // modifiable, but no reference to it escapes
    private final Map<String, List<String>> unmodifiableView;  // unmodifiable

    public UnmodifiableHeaders(Headers headers) {
        var h = headers;
        var unmodHeaders = new Headers();
        h.forEach((k, v) -> unmodHeaders.put(k, Collections.unmodifiableList(v)));
        this.unmodifiableView = Collections.unmodifiableMap(unmodHeaders);
        this.headers = unmodHeaders;
    }

    @Override
    public int size() {return headers.size();}

    @Override
    public boolean isEmpty() {return headers.isEmpty();}

    @Override
    public boolean containsKey(Object key) { return headers.containsKey(key); }

    @Override
    public boolean containsValue(Object value) { return headers.containsValue(value); }

    @Override
    public List<String> get(Object key) { return headers.get(key); }

    @Override
    public String getFirst(String key) { return headers.getFirst(key); }

    @Override
    public List<String> put(String key, List<String> value) {
        throw new UnsupportedOperationException ("unsupported operation");
    }

    @Override
    public void add(String key, String value) {
        throw new UnsupportedOperationException ("unsupported operation");
    }

    @Override
    public void set(String key, String value) {
        throw new UnsupportedOperationException ("unsupported operation");
    }

    @Override
    public List<String> remove(Object key) {
        throw new UnsupportedOperationException ("unsupported operation");
    }

    @Override
    public void putAll(Map<? extends String,? extends List<String>> t)  {
        throw new UnsupportedOperationException ("unsupported operation");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException ("unsupported operation");
    }

    @Override
    public Set<String> keySet() { return unmodifiableView.keySet(); }

    @Override
    public Collection<List<String>> values() { return unmodifiableView.values(); }

    @Override
    public Set<Map.Entry<String, List<String>>> entrySet() { return unmodifiableView.entrySet(); }

    @Override
    public List<String> replace(String key, List<String> value) {
        throw new UnsupportedOperationException("unsupported operation");
    }

    @Override
    public boolean replace(String key, List<String> oldValue, List<String> newValue) {
        throw new UnsupportedOperationException ("unsupported operation");
    }

    @Override
    public void replaceAll(BiFunction<? super String, ? super List<String>, ? extends List<String>> function) {
        throw new UnsupportedOperationException ("unsupported operation");
    }

    @Override
    public boolean equals(Object o) {return headers.equals(o);}

    @Override
    public int hashCode() {return headers.hashCode();}

    @Override
    public String toString() {
        return headers.toString();
    }
}
