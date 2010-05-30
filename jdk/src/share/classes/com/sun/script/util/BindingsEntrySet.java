/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.script.util;
import java.util.*;

/**
 * Entry set implementation for Bindings implementations
 *
 * @author Mike Grogan
 * @since 1.6
 */
public class BindingsEntrySet extends AbstractSet<Map.Entry<String, Object>> {

    private BindingsBase base;
    private String[] keys;

    public BindingsEntrySet(BindingsBase base) {
        this.base = base;
        keys = base.getNames();
    }

    public int size() {
        return keys.length;
    }

    public Iterator<Map.Entry<String, Object>> iterator() {
        return new BindingsIterator();
    }

    public class BindingsEntry implements Map.Entry<String, Object> {
        private String key;
        public BindingsEntry(String key) {
            this.key = key;
        }

        public Object setValue(Object value) {
            throw new UnsupportedOperationException();
        }

        public String getKey() {
            return key;
        }

        public Object getValue() {
            return base.get(key);
        }

    }

    public class BindingsIterator implements Iterator<Map.Entry<String, Object>> {

        private int current = 0;
        private boolean stale = false;

        public boolean hasNext() {
            return (current < keys.length);
        }

        public BindingsEntry next() {
            stale = false;
            return new BindingsEntry(keys[current++]);
        }

        public void remove() {
            if (stale || current == 0) {
                throw new IllegalStateException();
            }

            stale = true;
            base.remove(keys[current - 1]);
        }

    }

}
