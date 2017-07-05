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

import java.util.*;
import com.sun.net.httpserver.*;

class UnmodifiableHeaders extends Headers {

        Headers map;

        UnmodifiableHeaders(Headers map) {
            this.map = map;
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

        public String getFirst (String key) {
            return map.getFirst(key);
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

        public Set<String> keySet() {
            return Collections.unmodifiableSet (map.keySet());
        }

        public Collection<List<String>> values() {
            return Collections.unmodifiableCollection(map.values());
        }

        /* TODO check that contents of set are not modifable : security */

        public Set<Map.Entry<String, List<String>>> entrySet() {
            return Collections.unmodifiableSet (map.entrySet());
        }

        public boolean equals(Object o) {return map.equals(o);}

        public int hashCode() {return map.hashCode();}
    }
