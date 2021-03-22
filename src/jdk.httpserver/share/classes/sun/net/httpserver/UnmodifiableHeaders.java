/*
 * Copyright (c) 2005, 2021, Oracle and/or its affiliates. All rights reserved.
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

        private Map<String, List<String>> map;

        public UnmodifiableHeaders(Headers map) {
            this.map = Collections.unmodifiableMap(map);
        }

        public int size() {return map.size();}

        public boolean isEmpty() {return map.isEmpty();}

        public boolean containsKey(Object key) {
            return map.containsKey (key);
        }

        public boolean containsValue(Object value) {
            return map.containsValue(value);
        }

        public List<String> get(Object key) {
            return map.get(key);
        }

        public String getFirst(String key) {
            final var headers = new Headers();
            map.forEach((k, v) -> headers.add(k, v.get(0)));
            return headers.getFirst(key);
        }

        public List<String> put(String key, List<String> value) {
            return map.put (key, value);
        }

        public void add (String key, String value) {
            throw new UnsupportedOperationException ("unsupported operation");
        }

        public void set (String key, String value) {
            throw new UnsupportedOperationException ("unsupported operation");
        }

        public List<String> remove(Object key) {
            throw new UnsupportedOperationException ("unsupported operation");
        }

        public void putAll(Map<? extends String,? extends List<String>> t)  {
            throw new UnsupportedOperationException ("unsupported operation");
        }

        public void clear() {
            throw new UnsupportedOperationException ("unsupported operation");
        }

        public Set<String> keySet() { return map.keySet(); }

        public Collection<List<String>> values() { return map.values(); }

        /* TODO check that contents of set are not modifable : security */

        public Set<Map.Entry<String, List<String>>> entrySet() { return map.entrySet(); }

        public List<String> replace(String key, List<String> value) {
            throw new UnsupportedOperationException("unsupported operation");
        }

        public boolean replace(String key, List<String> oldValue, List<String> newValue) {
            throw new UnsupportedOperationException ("unsupported operation");
        }

        public void replaceAll(BiFunction<? super String, ? super List<String>, ? extends List<String>> function) {
            throw new UnsupportedOperationException ("unsupported operation");
        }

        public boolean equals(Object o) {return map.equals(o);}

        public int hashCode() {return map.hashCode();}
    }
