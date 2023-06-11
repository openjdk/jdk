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

import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import jdk.internal.misc.Unsafe;

/**
 * Unmodifiable map for storing system properties read from native memory whose values have their
 * string representation constructed on first access.
 */
final class SystemProperties implements Map<String, String> {

    final Map<String, Value> entries;
    EntrySet entrySet;
    Values values;

    SystemProperties(Map<String, Value> entries) {
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
            if (v.getString().equals(value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String get(Object key) {
        Value v = entries.get(key);
        if (v != null) {
            return v.getString();
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
        Values vs;
        return (vs = values) == null ? (values = new Values(this)) : vs;
    }

    @Override
    public Set<Entry<String, String>> entrySet() {
        EntrySet es;
        return (es = entrySet) == null ? (entrySet = new EntrySet(this)) : es;
    }

    /**
     * Represents a value in {@link SystemProperties}.
     */
    static class Value {
        private final Unsafe unsafe;
        private final long cstring;
        private volatile String string;

        /**
         * Creates a value whose string representation will be lazily constructed from {@code cstring}.
         */
        Value(Unsafe unsafe, long cstring) {
            this.unsafe = unsafe;
            this.cstring = cstring;
        }

        /**
         * Creates a value whose string representation is known at construction time.
         */
        Value(String string) {
            this.unsafe = null;
            this.cstring = 0;
            this.string = string;
        }

        String getString() {
            if (string == null) {
                // Racy but it doesn't matter.
                string = Services.toJavaString(unsafe, cstring);
            }
            return string;
        }
    }

    static final class EntrySet extends AbstractSet<Entry<String, String>> {

        final SystemProperties sp;

        EntrySet(SystemProperties sp) {
            this.sp = sp;
        }

        public final int size() {
            return sp.size();
        }

        public final void clear() {
            throw new UnsupportedOperationException();
        }

        public final Iterator<Entry<String, String>> iterator() {
            return new Iterator<Entry<String, String>>() {
                Iterator<Entry<String, Value>> entriesIter = sp.entries.entrySet().iterator();

                @Override
                public boolean hasNext() {
                    return entriesIter.hasNext();
                }

                @Override
                public Entry<String, String> next() {
                    Entry<String, Value> next = entriesIter.next();
                    return new Node(next.getKey(), next.getValue());
                }
            };
        }

        public final boolean contains(Object o) {
            return sp.entries.entrySet().contains(o);
        }

        public final boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        public final void forEach(Consumer<? super Entry<String, String>> action) {
            for (Entry<String, String> e : this) {
                action.accept(e);
            }
        }
    }

    static class Node implements Map.Entry<String, String> {
        final String key;
        final Value value;

        Node(String key, Value value) {
            this.key = key;
            this.value = value;
        }


        public final String getKey() {
            return key;
        }

        public final String getValue() {
            return value.getString();
        }

        public final String toString() {
            return key + "=" + getValue();
        }

        public final int hashCode() {
            return Objects.hashCode(key) ^ Long.hashCode(value.cstring);
        }

        public final String setValue(String newValue) {
            throw new UnsupportedOperationException();
        }

        public final boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            return o instanceof Node e
                    && Objects.equals(key, e.getKey())
                    && Objects.equals(value.getString(), e.value.getString());
        }
    }

    static final class Values extends AbstractCollection<String> {
        final SystemProperties sp;

        Values(SystemProperties sp) {
            this.sp = sp;
        }

        @Override
        public int size() {
            return sp.size();
        }

        @Override
        public boolean isEmpty() {
            return sp.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return sp.containsValue(o);
        }

        @Override
        public Iterator<String> iterator() {
            Iterator<Entry<String, Value>> entriesIter = sp.entries.entrySet().iterator();
            return new Iterator<String>() {
                @Override
                public boolean hasNext() {
                    return entriesIter.hasNext();
                }

                @Override
                public String next() {
                    Entry<String, Value> next = entriesIter.next();
                    return next.getValue().getString();
                }
            };
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(Collection<? extends String> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }
    }
}
