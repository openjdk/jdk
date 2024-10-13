/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package jdk.vm.ci.services;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jdk.internal.misc.Unsafe;

/**
 * Unmodifiable map for storing system properties read from native memory whose values have their
 * string representation constructed on first access.
 */
final class SystemProperties implements Map<String, String> {

    private final Unsafe unsafe;
    private final Map<String, Value> entries;
    private Set<Entry<String, String>> entrySet;
    private Collection<String> values;

    SystemProperties(Unsafe unsafe, Map<String, Value> entries) {
        this.unsafe = unsafe;
        this.entries = entries;
    }

    @Override
    public int size() {
        return entries.size();
    }

    @Override
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return entries.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        for (Value v : entries.values()) {
            if (v.getString(unsafe).equals(value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String get(Object key) {
        Value v = entries.get(key);
        if (v != null) {
            return v.getString(unsafe);
        }
        return null;
    }

    @Override
    public String put(String key, String value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(Map<? extends String, ? extends String> m) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> keySet() {
        return entries.keySet();
    }

    @Override
    public Collection<String> values() {
        if (values == null) {
            values = entries.values().stream().map(v -> v.getString(unsafe)).collect(Collectors.toUnmodifiableList());
        }
        return values;
    }

    static class Property implements Map.Entry<String, String> {
        private final Unsafe unsafe;
        private final String key;
        private final Value value;

        Property(Unsafe unsafe, Map.Entry<String, Value> e) {
            this.unsafe = unsafe;
            this.key = e.getKey();
            this.value = e.getValue();
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public String getValue() {
            return value.getString(unsafe);
        }

        @Override
        public String setValue(String value) {
            throw new UnsupportedOperationException();
        }
    };

    @Override
    public Set<Entry<String, String>> entrySet() {
        if (entrySet == null) {
            entrySet = entries.entrySet().stream().map(e -> new Property(unsafe, e)).collect(Collectors.toUnmodifiableSet());
        }
        return entrySet;
    }

    /**
     * Represents a value in {@link SystemProperties}.
     */
    static class Value {
        private final long cstring;
        private volatile String string;

        /**
         * Creates a value whose string representation will be lazily constructed from {@code cstring}.
         */
        Value(Unsafe unsafe, long cstring) {
            this.cstring = cstring;
        }

        /**
         * Creates a value whose string representation is known at construction time.
         */
        Value(String string) {
            this.cstring = 0;
            this.string = string;
        }

        String getString(Unsafe unsafe) {
            if (string == null) {
                // Racy but it doesn't matter.
                string = Services.toJavaString(unsafe, cstring);
            }
            return string;
        }
    }
}
