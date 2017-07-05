/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.util;

import com.sun.istack.internal.NotNull;

import javax.xml.namespace.QName;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Map keyed by {@link QName}.
 *
 * This specialized map allows a look up operation without constructing
 * a new QName instance, for a performance reason. This {@link Map} assumes
 * that both namespace URI and local name are {@link String#intern() intern}ed.
 *
 * @since JAXB 2.0
 */
public final class QNameMap<V> {
    /**
     * The default initial capacity - MUST be a power of two.
     */
    private static final int DEFAULT_INITIAL_CAPACITY = 16;

    /**
     * The maximum capacity, used if a higher value is implicitly specified
     * by either of the constructors with arguments.
     * MUST be a power of two <= 1<<30.
     */
    private static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * The table, resized as necessary. Length MUST Always be a power of two.
     */
    transient Entry<V>[] table = new Entry[DEFAULT_INITIAL_CAPACITY];

    /**
     * The number of key-value mappings contained in this identity hash map.
     */
    transient int size;

    /**
     * The next size value at which to resize . Taking it as
     * MAXIMUM_CAPACITY
     * @serial
     */
    private int threshold;

    /**
     * The load factor used when none specified in constructor.
     **/
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;



    /**
     * Gives an entrySet view of this map
     */
    private Set<Entry<V>> entrySet = null;

    public QNameMap() {
        threshold = (int)(DEFAULT_INITIAL_CAPACITY * DEFAULT_LOAD_FACTOR);
        table = new Entry[DEFAULT_INITIAL_CAPACITY];

    }

    /**
     * Associates the specified value with the specified keys in this map.
     * If the map previously contained a mapping for this key, the old
     * value is replaced.
     *
     * @param namespaceUri First key with which the specified value is to be associated.
     * @param localname Second key with which the specified value is to be associated.
     * @param value value to be associated with the specified key.
     *
     */
    public void put(String namespaceUri,String localname, V value ) {
        //keys cannot be null
        assert localname !=null;
        assert namespaceUri !=null;

        int hash = hash(localname);
        int i = indexFor(hash, table.length);

        for (Entry<V> e = table[i]; e != null; e = e.next) {
            if (e.hash == hash && localname.equals(e.localName) && namespaceUri.equals(e.nsUri)) {
                e.value = value;
                return;
            }
        }

        addEntry(hash, namespaceUri,localname, value, i);

    }

    public void put(QName name, V value ) {
        put(name.getNamespaceURI(),name.getLocalPart(),value);
    }

    /**
     * Returns the value to which the specified keys are mapped in this QNameMap,
     * or {@code null} if the map contains no mapping for this key.
     *
     * @param   nsUri the namespaceUri key whose associated value is to be returned.
     * @param   localPart the localPart key whose associated value is to be returned.
     * @return  the value to which this map maps the specified set of keya, or
     *          {@code null} if the map contains no mapping for this set of keys.
     * @see #put(String,String, Object)
     */
    public V get( @NotNull String nsUri, String localPart ) {
        Entry<V> e = getEntry(nsUri,localPart);
        if(e==null) return null;
        else        return e.value;
    }

    public V get( QName name ) {
        return get(name.getNamespaceURI(),name.getLocalPart());
    }

    /**
     * Returns the number of keys-value mappings in this map.
     *
     * @return the number of keys-value mappings in this map.
     */
    public int size() {
        return size;
    }

    /**
     * Copies all of the mappings from the specified map to this map
     * These mappings will replace any mappings that
     * this map had for any of the keys currently in the specified map.
     *
     * @param map mappings to be stored in this map.
     *
     */
    public QNameMap<V> putAll(QNameMap<? extends V> map) {
        int numKeysToBeAdded = map.size();
        if (numKeysToBeAdded == 0)
            return this;


        if (numKeysToBeAdded > threshold) {
            int targetCapacity = numKeysToBeAdded;
            if (targetCapacity > MAXIMUM_CAPACITY)
                targetCapacity = MAXIMUM_CAPACITY;
            int newCapacity = table.length;
            while (newCapacity < targetCapacity)
                newCapacity <<= 1;
            if (newCapacity > table.length)
                resize(newCapacity);
        }

        for( Entry<? extends V> e : map.entrySet() )
            put(e.nsUri,e.localName,e.getValue());
        return this;
    }

    public QNameMap<V> putAll(Map<QName,? extends V> map) {
        for (Map.Entry<QName, ? extends V> e : map.entrySet()) {
            QName qn = e.getKey();
            put(qn.getNamespaceURI(),qn.getLocalPart(),e.getValue());
        }
        return this;
    }


    /**
     * Returns a hash value for the specified object.The hash value is computed
     * for the localName.
     */
    private static int hash(String x) {
        int h = x.hashCode();

        h += ~(h << 9);
        h ^=  (h >>> 14);
        h +=  (h << 4);
        h ^=  (h >>> 10);
        return h;
    }

    /**
     * Returns index for hash code h.
     */
    private static int indexFor(int h, int length) {
        return h & (length-1);
    }

    /**
     * Add a new entry with the specified keys, value and hash code to
     * the specified bucket.  It is the responsibility of this
     * method to resize the table if appropriate.
     *
     */
    private void addEntry(int hash, String nsUri, String localName, V value, int bucketIndex) {
        Entry<V> e = table[bucketIndex];
        table[bucketIndex] = new Entry<V>(hash, nsUri, localName, value, e);
        if (size++ >= threshold)
            resize(2 * table.length);
    }


    /**
     * Rehashes the contents of this map into a new array with a
     * larger capacity.  This method is called automatically when the
     * number of keys in this map reaches its threshold.
     */
    private void resize(int newCapacity) {
        Entry[] oldTable = table;
        int oldCapacity = oldTable.length;
        if (oldCapacity == MAXIMUM_CAPACITY) {
            threshold = Integer.MAX_VALUE;
            return;
        }

        Entry[] newTable = new Entry[newCapacity];
        transfer(newTable);
        table = newTable;
        threshold = newCapacity;
    }

    /**
     * Transfer all entries from current table to newTable.
     */
    private void transfer(Entry<V>[] newTable) {
        Entry<V>[] src = table;
        int newCapacity = newTable.length;
        for (int j = 0; j < src.length; j++) {
            Entry<V> e = src[j];
            if (e != null) {
                src[j] = null;
                do {
                    Entry<V> next = e.next;
                    int i = indexFor(e.hash, newCapacity);
                    e.next = newTable[i];
                    newTable[i] = e;
                    e = next;
                } while (e != null);
            }
        }
    }

    /**
     * Returns one random item in the map.
     * If this map is empty, return null.
     *
     * <p>
     * This method is useful to obtain the value from a map that only contains one element.
     */
    public Entry<V> getOne() {
        for( Entry<V> e : table ) {
            if(e!=null)
                return e;
        }
        return null;
    }

    public Collection<QName> keySet() {
        Set<QName> r = new HashSet<QName>();
        for (Entry<V> e : entrySet()) {
            r.add(e.createQName());
        }
        return r;
    }

    public Iterable<V> values() {
        return views;
    }

    private transient Iterable<V> views = new Iterable<V>() {
        public Iterator<V> iterator() {
            return new ValueIterator();
        }
    };

    private abstract class HashIterator<E> implements Iterator<E> {
        Entry<V> next;  // next entry to return
        int index;              // current slot

        HashIterator() {
            Entry<V>[] t = table;
            int i = t.length;
            Entry<V> n = null;
            if (size != 0) { // advance to first entry
                while (i > 0 && (n = t[--i]) == null)
                    ;
            }
            next = n;
            index = i;
        }

        public boolean hasNext() {
            return next != null;
        }

        Entry<V> nextEntry() {
            Entry<V> e = next;
            if (e == null)
                throw new NoSuchElementException();

            Entry<V> n = e.next;
            Entry<V>[] t = table;
            int i = index;
            while (n == null && i > 0)
                n = t[--i];
            index = i;
            next = n;
            return e;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private class ValueIterator extends HashIterator<V> {
        public V next() {
            return nextEntry().value;
        }
    }

    public boolean containsKey(@NotNull String nsUri,String localName) {
        return getEntry(nsUri,localName)!=null;
    }


    /**
     * Returns true if this map is empty.
     */
    public boolean isEmpty() {
        return size == 0;
    }


    public static final class Entry<V>  {
        /** The namespace URI. */
        public final String nsUri;

        /** The localPart. */
        public final String localName;

        V value;
        final int hash;
        Entry<V> next;

        /**
         * Create new entry.
         */
        Entry(int h, String nsUri, String localName, V v, Entry<V> n) {
            value = v;
            next = n;
            this.nsUri = nsUri;
            this.localName = localName;
            hash = h;
        }

        /**
         * Creates a new QName object from {@link #nsUri} and {@link #localName}.
         */
        public QName createQName() {
            return new QName(nsUri,localName);
        }

        public V getValue() {
            return value;
        }

        public V setValue(V newValue) {
            V oldValue = value;
            value = newValue;
            return oldValue;
        }

        public boolean equals(Object o) {
            if (!(o instanceof Entry))
                return false;
            Entry e = (Entry)o;
            String k1 = nsUri;
            String k2 = e.nsUri;
            String k3 = localName;
            String k4 = e.localName;
            if (k1.equals(k2) && k3.equals(k4)) {
                Object v1 = getValue();
                Object v2 = e.getValue();
                if (v1 == v2 || (v1 != null && v1.equals(v2)))
                    return true;
            }
            return false;
        }

        public int hashCode() {
            return ( localName.hashCode()) ^
                    (value==null   ? 0 : value.hashCode());
        }

        public String toString() {
            return '"'+nsUri +"\",\"" +localName + "\"=" + getValue();
        }
    }

    public Set<Entry<V>> entrySet() {
        Set<Entry<V>> es = entrySet;
        return es != null ? es : (entrySet = new EntrySet());
    }

    private Iterator<Entry<V>> newEntryIterator() {
        return new EntryIterator();
    }

    private class EntryIterator extends HashIterator<Entry<V>> {
        public Entry<V> next() {
            return nextEntry();
        }
    }
    private class EntrySet extends AbstractSet<Entry<V>> {
        public Iterator<Entry<V>> iterator() {
            return newEntryIterator();
        }
        public boolean contains(Object o) {
            if (!(o instanceof Entry))
                return false;
            Entry<V> e = (Entry<V>) o;
            Entry<V> candidate = getEntry(e.nsUri,e.localName);
            return candidate != null && candidate.equals(e);
        }
        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }
        public int size() {
            return size;
        }
    }

    private Entry<V> getEntry(@NotNull String nsUri,String localName) {
        int hash = hash(localName);
        int i = indexFor(hash, table.length);
        Entry<V> e = table[i];
        while (e != null && !(localName.equals(e.localName) && nsUri.equals(e.nsUri)))
            e = e.next;
        return e;
    }

    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append('{');

        for( Entry<V> e : entrySet() ) {
            if(buf.length()>1)
                buf.append(',');
            buf.append('[');
            buf.append(e);
            buf.append(']');
        }

        buf.append('}');
        return buf.toString();
    }
}
