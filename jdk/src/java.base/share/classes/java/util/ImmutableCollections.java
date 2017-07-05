/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package java.util;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;

/**
 * Container class for immutable collections. Not part of the public API.
 * Mainly for namespace management and shared infrastructure.
 *
 * Serial warnings are suppressed throughout because all implementation
 * classes use a serial proxy and thus have no need to declare serialVersionUID.
 */
@SuppressWarnings("serial")
class ImmutableCollections {
    /**
     * A "salt" value used for randomizing iteration order. This is initialized once
     * and stays constant for the lifetime of the JVM. It need not be truly random, but
     * it needs to vary sufficiently from one run to the next so that iteration order
     * will vary between JVM runs.
     */
    static final int SALT;
    static {
        SALT = new Random().nextInt();
    }

    /** No instances. */
    private ImmutableCollections() { }

    /**
     * The reciprocal of load factor. Given a number of elements
     * to store, multiply by this factor to get the table size.
     */
    static final double EXPAND_FACTOR = 2.0;

    // ---------- List Implementations ----------

    static final class List0<E> extends AbstractList<E> implements RandomAccess, Serializable {
        List0() { }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public E get(int index) {
            Objects.checkIndex(index, 0); // always throws IndexOutOfBoundsException
            return null;                  // but the compiler doesn't know this
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new InvalidObjectException("not serial proxy");
        }

        private Object writeReplace() {
            return new CollSer(CollSer.IMM_LIST);
        }
    }

    static final class List1<E> extends AbstractList<E> implements RandomAccess, Serializable {
        private final E e0;

        List1(E e0) {
            this.e0 = Objects.requireNonNull(e0);
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public E get(int index) {
            Objects.checkIndex(index, 1);
            // assert index == 0
            return e0;
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new InvalidObjectException("not serial proxy");
        }

        private Object writeReplace() {
            return new CollSer(CollSer.IMM_LIST, e0);
        }
    }

    static final class List2<E> extends AbstractList<E> implements RandomAccess, Serializable {
        private final E e0;
        private final E e1;

        List2(E e0, E e1) {
            this.e0 = Objects.requireNonNull(e0);
            this.e1 = Objects.requireNonNull(e1);
        }

        @Override
        public int size() {
            return 2;
        }

        @Override
        public E get(int index) {
            Objects.checkIndex(index, 2);
            if (index == 0) {
                return e0;
            } else { // index == 1
                return e1;
            }
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new InvalidObjectException("not serial proxy");
        }

        private Object writeReplace() {
            return new CollSer(CollSer.IMM_LIST, e0, e1);
        }
    }

    static final class ListN<E> extends AbstractList<E> implements RandomAccess, Serializable {
        private final E[] elements;

        @SafeVarargs
        ListN(E... input) {
            // copy and check manually to avoid TOCTOU
            @SuppressWarnings("unchecked")
            E[] tmp = (E[])new Object[input.length]; // implicit nullcheck of input
            for (int i = 0; i < input.length; i++) {
                tmp[i] = Objects.requireNonNull(input[i]);
            }
            this.elements = tmp;
        }

        @Override
        public int size() {
            return elements.length;
        }

        @Override
        public E get(int index) {
            Objects.checkIndex(index, elements.length);
            return elements[index];
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new InvalidObjectException("not serial proxy");
        }

        private Object writeReplace() {
            return new CollSer(CollSer.IMM_LIST, elements);
        }
    }

    // ---------- Set Implementations ----------

    static final class Set0<E> extends AbstractSet<E> implements Serializable {
        Set0() { }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean contains(Object o) {
            return super.contains(Objects.requireNonNull(o));
        }

        @Override
        public Iterator<E> iterator() {
            return Collections.emptyIterator();
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new InvalidObjectException("not serial proxy");
        }

        private Object writeReplace() {
            return new CollSer(CollSer.IMM_SET);
        }
    }

    static final class Set1<E> extends AbstractSet<E> implements Serializable {
        private final E e0;

        Set1(E e0) {
            this.e0 = Objects.requireNonNull(e0);
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public boolean contains(Object o) {
            return super.contains(Objects.requireNonNull(o));
        }

        @Override
        public Iterator<E> iterator() {
            return Collections.singletonIterator(e0);
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new InvalidObjectException("not serial proxy");
        }

        private Object writeReplace() {
            return new CollSer(CollSer.IMM_SET, e0);
        }
    }

    static final class Set2<E> extends AbstractSet<E> implements Serializable {
        private final E e0;
        private final E e1;

        Set2(E e0, E e1) {
            Objects.requireNonNull(e0);
            Objects.requireNonNull(e1);

            if (e0.equals(e1)) {
                throw new IllegalArgumentException("duplicate element: " + e0);
            }

            if (SALT >= 0) {
                this.e0 = e0;
                this.e1 = e1;
            } else {
                this.e0 = e1;
                this.e1 = e0;
            }
        }

        @Override
        public int size() {
            return 2;
        }

        @Override
        public boolean contains(Object o) {
            return super.contains(Objects.requireNonNull(o));
        }

        @Override
        public Iterator<E> iterator() {
            return new Iterator<E>() {
                private int idx = 0;

                @Override
                public boolean hasNext() {
                    return idx < 2;
                }

                @Override
                public E next() {
                    if (idx == 0) {
                        idx = 1;
                        return e0;
                    } else if (idx == 1) {
                        idx = 2;
                        return e1;
                    } else {
                        throw new NoSuchElementException();
                    }
                }
            };
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new InvalidObjectException("not serial proxy");
        }

        private Object writeReplace() {
            return new CollSer(CollSer.IMM_SET, e0, e1);
        }
    }

    /**
     * An array-based Set implementation. The element array must be strictly
     * larger than the size (the number of contained elements) so that at
     * least one null is always present.
     * @param <E> the element type
     */
    static final class SetN<E> extends AbstractSet<E> implements Serializable {
        private final E[] elements;
        private final int size;

        @SafeVarargs
        @SuppressWarnings("unchecked")
        SetN(E... input) {
            size = input.length; // implicit nullcheck of input

            elements = (E[])new Object[(int)Math.ceil(EXPAND_FACTOR * input.length)];
            for (int i = 0; i < input.length; i++) {
                E e = Objects.requireNonNull(input[i]);
                int idx = probe(e);
                if (idx >= 0) {
                    throw new IllegalArgumentException("duplicate element: " + e);
                } else {
                    elements[-(idx + 1)] = e;
                }
            }
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean contains(Object o) {
            Objects.requireNonNull(o);
            return probe(o) >= 0;
        }

        @Override
        public Iterator<E> iterator() {
            return new Iterator<E>() {
                private int idx = 0;

                @Override
                public boolean hasNext() {
                    while (idx < elements.length) {
                        if (elements[idx] != null)
                            return true;
                        idx++;
                    }
                    return false;
                }

                @Override
                public E next() {
                    if (! hasNext()) {
                        throw new NoSuchElementException();
                    }
                    return elements[idx++];
                }
            };
        }

        // returns index at which element is present; or if absent,
        // (-i - 1) where i is location where element should be inserted
        private int probe(Object pe) {
            int idx = Math.floorMod(pe.hashCode() ^ SALT, elements.length);
            while (true) {
                E ee = elements[idx];
                if (ee == null) {
                    return -idx - 1;
                } else if (pe.equals(ee)) {
                    return idx;
                } else if (++idx == elements.length) {
                    idx = 0;
                }
            }
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new InvalidObjectException("not serial proxy");
        }

        private Object writeReplace() {
            Object[] array = new Object[size];
            int dest = 0;
            for (Object o : elements) {
                if (o != null) {
                    array[dest++] = o;
                }
            }
            return new CollSer(CollSer.IMM_SET, array);
        }
    }

    // ---------- Map Implementations ----------

    static final class Map0<K,V> extends AbstractMap<K,V> implements Serializable {
        Map0() { }

        @Override
        public Set<Map.Entry<K,V>> entrySet() {
            return Set.of();
        }

        @Override
        public boolean containsKey(Object o) {
            return super.containsKey(Objects.requireNonNull(o));
        }

        @Override
        public boolean containsValue(Object o) {
            return super.containsValue(Objects.requireNonNull(o));
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new InvalidObjectException("not serial proxy");
        }

        private Object writeReplace() {
            return new CollSer(CollSer.IMM_MAP);
        }
    }

    static final class Map1<K,V> extends AbstractMap<K,V> implements Serializable {
        private final K k0;
        private final V v0;

        Map1(K k0, V v0) {
            this.k0 = Objects.requireNonNull(k0);
            this.v0 = Objects.requireNonNull(v0);
        }

        @Override
        public Set<Map.Entry<K,V>> entrySet() {
            return Set.of(new KeyValueHolder<>(k0, v0));
        }

        @Override
        public boolean containsKey(Object o) {
            return super.containsKey(Objects.requireNonNull(o));
        }

        @Override
        public boolean containsValue(Object o) {
            return super.containsValue(Objects.requireNonNull(o));
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new InvalidObjectException("not serial proxy");
        }

        private Object writeReplace() {
            return new CollSer(CollSer.IMM_MAP, k0, v0);
        }
    }

    /**
     * An array-based Map implementation. There is a single array "table" that
     * contains keys and values interleaved: table[0] is kA, table[1] is vA,
     * table[2] is kB, table[3] is vB, etc. The table size must be even. It must
     * also be strictly larger than the size (the number of key-value pairs contained
     * in the map) so that at least one null key is always present.
     * @param <K> the key type
     * @param <V> the value type
     */
    static final class MapN<K,V> extends AbstractMap<K,V> implements Serializable {
        private final Object[] table; // pairs of key, value
        private final int size; // number of pairs

        MapN(Object... input) {
            Objects.requireNonNull(input);
            if ((input.length & 1) != 0) {
                throw new InternalError("length is odd");
            }
            size = input.length >> 1;

            int len = (int)Math.ceil(EXPAND_FACTOR * input.length);
            len = (len + 1) & ~1; // ensure table is even length
            table = new Object[len];

            for (int i = 0; i < input.length; i += 2) {
                @SuppressWarnings("unchecked")
                    K k = Objects.requireNonNull((K)input[i]);
                @SuppressWarnings("unchecked")
                    V v = Objects.requireNonNull((V)input[i+1]);
                int idx = probe(k);
                if (idx >= 0) {
                    throw new IllegalArgumentException("duplicate key: " + k);
                } else {
                    int dest = -(idx + 1);
                    table[dest] = k;
                    table[dest+1] = v;
                }
            }
        }

        @Override
        public boolean containsKey(Object o) {
            return probe(Objects.requireNonNull(o)) >= 0;
        }

        @Override
        public boolean containsValue(Object o) {
            return super.containsValue(Objects.requireNonNull(o));
        }

        @Override
        @SuppressWarnings("unchecked")
        public V get(Object o) {
            int i = probe(o);
            if (i >= 0) {
                return (V)table[i+1];
            } else {
                return null;
            }
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public Set<Map.Entry<K,V>> entrySet() {
            return new AbstractSet<Map.Entry<K,V>>() {
                @Override
                public int size() {
                    return MapN.this.size;
                }

                @Override
                public Iterator<Map.Entry<K,V>> iterator() {
                    return new Iterator<Map.Entry<K,V>>() {
                        int idx = 0;

                        @Override
                        public boolean hasNext() {
                            while (idx < table.length) {
                                if (table[idx] != null)
                                    return true;
                                idx += 2;
                            }
                            return false;
                        }

                        @Override
                        public Map.Entry<K,V> next() {
                            if (hasNext()) {
                                @SuppressWarnings("unchecked")
                                Map.Entry<K,V> e =
                                    new KeyValueHolder<>((K)table[idx], (V)table[idx+1]);
                                idx += 2;
                                return e;
                            } else {
                                throw new NoSuchElementException();
                            }
                        }
                    };
                }
            };
        }

        // returns index at which the probe key is present; or if absent,
        // (-i - 1) where i is location where element should be inserted
        private int probe(Object pk) {
            int idx = Math.floorMod(pk.hashCode() ^ SALT, table.length >> 1) << 1;
            while (true) {
                @SuppressWarnings("unchecked")
                K ek = (K)table[idx];
                if (ek == null) {
                    return -idx - 1;
                } else if (pk.equals(ek)) {
                    return idx;
                } else if ((idx += 2) == table.length) {
                    idx = 0;
                }
            }
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new InvalidObjectException("not serial proxy");
        }

        private Object writeReplace() {
            Object[] array = new Object[2 * size];
            int len = table.length;
            int dest = 0;
            for (int i = 0; i < len; i += 2) {
                if (table[i] != null) {
                    array[dest++] = table[i];
                    array[dest++] = table[i+1];
                }
            }
            return new CollSer(CollSer.IMM_MAP, array);
        }
    }
}

// ---------- Serialization Proxy ----------

/**
 * Serialization proxy class for immutable collections.
 */
final class CollSer implements Serializable {
    private static final long serialVersionUID = 6309168927139932177L;

    static final int IMM_LIST = 1;
    static final int IMM_SET = 2;
    static final int IMM_MAP = 3;

    private final int flags;
    private final Object[] array;

    CollSer(int f, Object... a) {
        flags = f;
        array = a;
    }

    private Object readResolve() throws ObjectStreamException {
        try {
            if (array == null) {
                throw new InvalidObjectException("null array");
            }

            // use low order 8 bits to indicate "kind"
            // ignore high order bits
            switch (flags & 0xff) {
                case IMM_LIST:
                    return List.of(array);
                case IMM_SET:
                    return Set.of(array);
                case IMM_MAP:
                    if (array.length == 0) {
                        return new ImmutableCollections.Map0<>();
                    } else if (array.length == 2) {
                        return new ImmutableCollections.Map1<>(array[0], array[1]);
                    } else {
                        return new ImmutableCollections.MapN<>(array);
                    }
                default:
                    throw new InvalidObjectException(String.format("invalid flags 0x%x", flags));
            }
        } catch (NullPointerException|IllegalArgumentException ex) {
            InvalidObjectException ioe = new InvalidObjectException("invalid object");
            ioe.initCause(ex);
            throw ioe;
        }
    }
}
