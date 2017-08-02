/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import jdk.internal.misc.SharedSecrets;
import jdk.internal.vm.annotation.Stable;

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
        long nt = System.nanoTime();
        SALT = (int)((nt >>> 32) ^ nt);
    }

    /** No instances. */
    private ImmutableCollections() { }

    /**
     * The reciprocal of load factor. Given a number of elements
     * to store, multiply by this factor to get the table size.
     */
    static final int EXPAND_FACTOR = 2;

    static UnsupportedOperationException uoe() { return new UnsupportedOperationException(); }

    // ---------- List Implementations ----------

    abstract static class AbstractImmutableList<E> extends AbstractList<E>
                                                implements RandomAccess, Serializable {
        @Override public boolean add(E e) { throw uoe(); }
        @Override public boolean addAll(Collection<? extends E> c) { throw uoe(); }
        @Override public boolean addAll(int index, Collection<? extends E> c) { throw uoe(); }
        @Override public void    clear() { throw uoe(); }
        @Override public boolean remove(Object o) { throw uoe(); }
        @Override public boolean removeAll(Collection<?> c) { throw uoe(); }
        @Override public boolean removeIf(Predicate<? super E> filter) { throw uoe(); }
        @Override public void    replaceAll(UnaryOperator<E> operator) { throw uoe(); }
        @Override public boolean retainAll(Collection<?> c) { throw uoe(); }
        @Override public void    sort(Comparator<? super E> c) { throw uoe(); }
    }

    static final class List0<E> extends AbstractImmutableList<E> {
        private static final List0<?> INSTANCE = new List0<>();

        @SuppressWarnings("unchecked")
        static <T> List0<T> instance() {
            return (List0<T>) INSTANCE;
        }

        private List0() { }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public E get(int index) {
            Objects.checkIndex(index, 0); // always throws IndexOutOfBoundsException
            return null;                  // but the compiler doesn't know this
        }

        @Override
        public Iterator<E> iterator() {
            return Collections.emptyIterator();
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new InvalidObjectException("not serial proxy");
        }

        private Object writeReplace() {
            return new CollSer(CollSer.IMM_LIST);
        }

        @Override
        public boolean contains(Object o) {
            Objects.requireNonNull(o);
            return false;
        }

        @Override
        public boolean containsAll(Collection<?> o) {
            return o.isEmpty(); // implicit nullcheck of o
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }

    static final class List1<E> extends AbstractImmutableList<E> {
        @Stable
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
            return e0;
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new InvalidObjectException("not serial proxy");
        }

        private Object writeReplace() {
            return new CollSer(CollSer.IMM_LIST, e0);
        }

        @Override
        public boolean contains(Object o) {
            return o.equals(e0); // implicit nullcheck of o
        }

        @Override
        public int hashCode() {
            return 31 + e0.hashCode();
        }
    }

    static final class List2<E> extends AbstractImmutableList<E> {
        @Stable
        private final E e0;
        @Stable
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

        @Override
        public boolean contains(Object o) {
            return o.equals(e0) || o.equals(e1); // implicit nullcheck of o
        }

        @Override
        public int hashCode() {
            int hash = 31 + e0.hashCode();
            return 31 * hash + e1.hashCode();
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new InvalidObjectException("not serial proxy");
        }

        private Object writeReplace() {
            return new CollSer(CollSer.IMM_LIST, e0, e1);
        }
    }

    static final class ListN<E> extends AbstractImmutableList<E> {
        @Stable
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

        @Override
        public boolean contains(Object o) {
            for (E e : elements) {
                if (o.equals(e)) { // implicit nullcheck of o
                    return true;
                }
            }
            return false;
        }

        @Override
        public int hashCode() {
            int hash = 1;
            for (E e : elements) {
                hash = 31 * hash + e.hashCode();
            }
            return hash;
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new InvalidObjectException("not serial proxy");
        }

        private Object writeReplace() {
            return new CollSer(CollSer.IMM_LIST, elements);
        }
    }

    // ---------- Set Implementations ----------

    abstract static class AbstractImmutableSet<E> extends AbstractSet<E> implements Serializable {
        @Override public boolean add(E e) { throw uoe(); }
        @Override public boolean addAll(Collection<? extends E> c) { throw uoe(); }
        @Override public void    clear() { throw uoe(); }
        @Override public boolean remove(Object o) { throw uoe(); }
        @Override public boolean removeAll(Collection<?> c) { throw uoe(); }
        @Override public boolean removeIf(Predicate<? super E> filter) { throw uoe(); }
        @Override public boolean retainAll(Collection<?> c) { throw uoe(); }
    }

    static final class Set0<E> extends AbstractImmutableSet<E> {
        private static final Set0<?> INSTANCE = new Set0<>();

        @SuppressWarnings("unchecked")
        static <T> Set0<T> instance() {
            return (Set0<T>) INSTANCE;
        }

        private Set0() { }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean contains(Object o) {
            Objects.requireNonNull(o);
            return false;
        }

        @Override
        public boolean containsAll(Collection<?> o) {
            return o.isEmpty(); // implicit nullcheck of o
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

        @Override
        public int hashCode() {
            return 0;
        }
    }

    static final class Set1<E> extends AbstractImmutableSet<E> {
        @Stable
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
            return o.equals(e0); // implicit nullcheck of o
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

        @Override
        public int hashCode() {
            return e0.hashCode();
        }
    }

    static final class Set2<E> extends AbstractImmutableSet<E> {
        @Stable
        final E e0;
        @Stable
        final E e1;

        Set2(E e0, E e1) {
            if (e0.equals(Objects.requireNonNull(e1))) { // implicit nullcheck of e0
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
            return o.equals(e0) || o.equals(e1); // implicit nullcheck of o
        }

        @Override
        public int hashCode() {
            return e0.hashCode() + e1.hashCode();
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
    static final class SetN<E> extends AbstractImmutableSet<E> {
        @Stable
        final E[] elements;
        @Stable
        final int size;

        @SafeVarargs
        @SuppressWarnings("unchecked")
        SetN(E... input) {
            size = input.length; // implicit nullcheck of input

            elements = (E[])new Object[EXPAND_FACTOR * input.length];
            for (int i = 0; i < input.length; i++) {
                E e = input[i];
                int idx = probe(e); // implicit nullcheck of e
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
            return probe(o) >= 0; // implicit nullcheck of o
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

        @Override
        public int hashCode() {
            int h = 0;
            for (E e : elements) {
                if (e != null) {
                    h += e.hashCode();
                }
            }
            return h;
        }

        // returns index at which element is present; or if absent,
        // (-i - 1) where i is location where element should be inserted.
        // Callers are relying on this method to perform an implicit nullcheck
        // of pe
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

    abstract static class AbstractImmutableMap<K,V> extends AbstractMap<K,V> implements Serializable {
        @Override public void clear() { throw uoe(); }
        @Override public V compute(K key, BiFunction<? super K,? super V,? extends V> rf) { throw uoe(); }
        @Override public V computeIfAbsent(K key, Function<? super K,? extends V> mf) { throw uoe(); }
        @Override public V computeIfPresent(K key, BiFunction<? super K,? super V,? extends V> rf) { throw uoe(); }
        @Override public V merge(K key, V value, BiFunction<? super V,? super V,? extends V> rf) { throw uoe(); }
        @Override public V put(K key, V value) { throw uoe(); }
        @Override public void putAll(Map<? extends K,? extends V> m) { throw uoe(); }
        @Override public V putIfAbsent(K key, V value) { throw uoe(); }
        @Override public V remove(Object key) { throw uoe(); }
        @Override public boolean remove(Object key, Object value) { throw uoe(); }
        @Override public V replace(K key, V value) { throw uoe(); }
        @Override public boolean replace(K key, V oldValue, V newValue) { throw uoe(); }
        @Override public void replaceAll(BiFunction<? super K,? super V,? extends V> f) { throw uoe(); }
    }

    static final class Map0<K,V> extends AbstractImmutableMap<K,V> {
        private static final Map0<?,?> INSTANCE = new Map0<>();

        @SuppressWarnings("unchecked")
        static <K,V> Map0<K,V> instance() {
            return (Map0<K,V>) INSTANCE;
        }

        private Map0() { }

        @Override
        public Set<Map.Entry<K,V>> entrySet() {
            return Set.of();
        }

        @Override
        public boolean containsKey(Object o) {
            Objects.requireNonNull(o);
            return false;
        }

        @Override
        public boolean containsValue(Object o) {
            Objects.requireNonNull(o);
            return false;
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new InvalidObjectException("not serial proxy");
        }

        private Object writeReplace() {
            return new CollSer(CollSer.IMM_MAP);
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    static final class Map1<K,V> extends AbstractImmutableMap<K,V> {
        @Stable
        private final K k0;
        @Stable
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
            return o.equals(k0); // implicit nullcheck of o
        }

        @Override
        public boolean containsValue(Object o) {
            return o.equals(v0); // implicit nullcheck of o
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            throw new InvalidObjectException("not serial proxy");
        }

        private Object writeReplace() {
            return new CollSer(CollSer.IMM_MAP, k0, v0);
        }

        @Override
        public int hashCode() {
            return k0.hashCode() ^ v0.hashCode();
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
    static final class MapN<K,V> extends AbstractImmutableMap<K,V> {
        @Stable
        final Object[] table; // pairs of key, value
        @Stable
        final int size; // number of pairs

        MapN(Object... input) {
            if ((input.length & 1) != 0) { // implicit nullcheck of input
                throw new InternalError("length is odd");
            }
            size = input.length >> 1;

            int len = EXPAND_FACTOR * input.length;
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
            return probe(o) >= 0; // implicit nullcheck of o
        }

        @Override
        public boolean containsValue(Object o) {
            for (int i = 1; i < table.length; i += 2) {
                Object v = table[i];
                if (v != null && o.equals(v)) { // implicit nullcheck of o
                    return true;
                }
            }
            return false;
        }

        @Override
        public int hashCode() {
            int hash = 0;
            for (int i = 0; i < table.length; i += 2) {
                Object k = table[i];
                if (k != null) {
                    hash += k.hashCode() ^ table[i + 1].hashCode();
                }
            }
            return hash;
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
        // (-i - 1) where i is location where element should be inserted.
        // Callers are relying on this method to perform an implicit nullcheck
        // of pk.
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
 * A unified serialization proxy class for the immutable collections.
 *
 * @serial
 * @since 9
 */
final class CollSer implements Serializable {
    private static final long serialVersionUID = 6309168927139932177L;

    static final int IMM_LIST = 1;
    static final int IMM_SET = 2;
    static final int IMM_MAP = 3;

    /**
     * Indicates the type of collection that is serialized.
     * The low order 8 bits have the value 1 for an immutable
     * {@code List}, 2 for an immutable {@code Set}, and 3 for
     * an immutable {@code Map}. Any other value causes an
     * {@link InvalidObjectException} to be thrown. The high
     * order 24 bits are zero when an instance is serialized,
     * and they are ignored when an instance is deserialized.
     * They can thus be used by future implementations without
     * causing compatibility issues.
     *
     * <p>The tag value also determines the interpretation of the
     * transient {@code Object[] array} field.
     * For {@code List} and {@code Set}, the array's length is the size
     * of the collection, and the array contains the elements of the collection.
     * Null elements are not allowed. For {@code Set}, duplicate elements
     * are not allowed.
     *
     * <p>For {@code Map}, the array's length is twice the number of mappings
     * present in the map. The array length is necessarily even.
     * The array contains a succession of key and value pairs:
     * {@code k1, v1, k2, v2, ..., kN, vN.} Nulls are not allowed,
     * and duplicate keys are not allowed.
     *
     * @serial
     * @since 9
     */
    private final int tag;

    /**
     * @serial
     * @since 9
     */
    private transient Object[] array;

    CollSer(int t, Object... a) {
        tag = t;
        array = a;
    }

    /**
     * Reads objects from the stream and stores them
     * in the transient {@code Object[] array} field.
     *
     * @serialData
     * A nonnegative int, indicating the count of objects,
     * followed by that many objects.
     *
     * @param ois the ObjectInputStream from which data is read
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if a serialized class cannot be loaded
     * @throws InvalidObjectException if the count is negative
     * @since 9
     */
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        int len = ois.readInt();

        if (len < 0) {
            throw new InvalidObjectException("negative length " + len);
        }

        SharedSecrets.getJavaObjectInputStreamAccess().checkArray(ois, Object[].class, len);
        Object[] a = new Object[len];
        for (int i = 0; i < len; i++) {
            a[i] = ois.readObject();
        }

        array = a;
    }

    /**
     * Writes objects to the stream from
     * the transient {@code Object[] array} field.
     *
     * @serialData
     * A nonnegative int, indicating the count of objects,
     * followed by that many objects.
     *
     * @param oos the ObjectOutputStream to which data is written
     * @throws IOException if an I/O error occurs
     * @since 9
     */
    private void writeObject(ObjectOutputStream oos) throws IOException {
        oos.defaultWriteObject();
        oos.writeInt(array.length);
        for (int i = 0; i < array.length; i++) {
            oos.writeObject(array[i]);
        }
    }

    /**
     * Creates and returns an immutable collection from this proxy class.
     * The instance returned is created as if by calling one of the
     * static factory methods for
     * <a href="List.html#immutable">List</a>,
     * <a href="Map.html#immutable">Map</a>, or
     * <a href="Set.html#immutable">Set</a>.
     * This proxy class is the serial form for all immutable collection instances,
     * regardless of implementation type. This is necessary to ensure that the
     * existence of any particular implementation type is kept out of the
     * serialized form.
     *
     * @return a collection created from this proxy object
     * @throws InvalidObjectException if the tag value is illegal or if an exception
     *         is thrown during creation of the collection
     * @throws ObjectStreamException if another serialization error has occurred
     * @since 9
     */
    private Object readResolve() throws ObjectStreamException {
        try {
            if (array == null) {
                throw new InvalidObjectException("null array");
            }

            // use low order 8 bits to indicate "kind"
            // ignore high order 24 bits
            switch (tag & 0xff) {
                case IMM_LIST:
                    return List.of(array);
                case IMM_SET:
                    return Set.of(array);
                case IMM_MAP:
                    if (array.length == 0) {
                        return ImmutableCollections.Map0.instance();
                    } else if (array.length == 2) {
                        return new ImmutableCollections.Map1<>(array[0], array[1]);
                    } else {
                        return new ImmutableCollections.MapN<>(array);
                    }
                default:
                    throw new InvalidObjectException(String.format("invalid flags 0x%x", tag));
            }
        } catch (NullPointerException|IllegalArgumentException ex) {
            InvalidObjectException ioe = new InvalidObjectException("invalid object");
            ioe.initCause(ex);
            throw ioe;
        }
    }
}
