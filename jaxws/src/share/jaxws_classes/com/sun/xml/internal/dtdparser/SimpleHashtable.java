/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.dtdparser;

import java.util.Enumeration;


// This could be replaced by Collections class unless we want
// to be able to run on JDK 1.1


/**
 * This class implements a special purpose hashtable.  It works like a
 * normal <code>java.util.Hashtable</code> except that: <OL>
 * <p/>
 * <LI> Keys to "get" are strings which are known to be interned,
 * so that "==" is used instead of "String.equals".  (Interning
 * could be document-relative instead of global.)
 * <p/>
 * <LI> It's not synchronized, since it's to be used only by
 * one thread at a time.
 * <p/>
 * <LI> The keys () enumerator allocates no memory, with live
 * updates to the data disallowed.
 * <p/>
 * <LI> It's got fewer bells and whistles:  fixed threshold and
 * load factor, no JDK 1.2 collection support, only keys can be
 * enumerated, things can't be removed, simpler inheritance; more.
 * <p/>
 * </OL>
 * <p/>
 * <P> The overall result is that it's less expensive to use these in
 * performance-critical locations, in terms both of CPU and memory,
 * than <code>java.util.Hashtable</code> instances.  In this package
 * it makes a significant difference when normalizing attributes,
 * which is done for each start-element construct.
 *
 * @version $Revision: 1.2 $
 */
final class SimpleHashtable implements Enumeration {
    // entries ...
    private Entry table[];

    // currently enumerated key
    private Entry current = null;
    private int currentBucket = 0;

    private int count;
    private int threshold;

    private static final float loadFactor = 0.75f;


    /**
     * Constructs a new, empty hashtable with the specified initial
     * capacity.
     *
     * @param initialCapacity the initial capacity of the hashtable.
     */
    public SimpleHashtable(int initialCapacity) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal Capacity: " +
                    initialCapacity);
        if (initialCapacity == 0)
            initialCapacity = 1;
        table = new Entry[initialCapacity];
        threshold = (int) (initialCapacity * loadFactor);
    }

    /**
     * Constructs a new, empty hashtable with a default capacity.
     */
    public SimpleHashtable() {
        this(11);
    }

    /**
     */
    public void clear() {
        count = 0;
        currentBucket = 0;
        current = null;
        for (int i = 0; i < table.length; i++)
            table[i] = null;
    }

    /**
     * Returns the number of keys in this hashtable.
     *
     * @return the number of keys in this hashtable.
     */
    public int size() {
        return count;
    }

    /**
     * Returns an enumeration of the keys in this hashtable.
     *
     * @return an enumeration of the keys in this hashtable.
     * @see Enumeration
     */
    public Enumeration keys() {
        currentBucket = 0;
        current = null;
        return this;
    }

    /**
     * Used to view this as an enumeration; returns true if there
     * are more keys to be enumerated.
     */
    public boolean hasMoreElements() {
        if (current != null)
            return true;
        while (currentBucket < table.length) {
            current = table[currentBucket++];
            if (current != null)
                return true;
        }
        return false;
    }

    /**
     * Used to view this as an enumeration; returns the next key
     * in the enumeration.
     */
    public Object nextElement() {
        Object retval;

        if (current == null)
            throw new IllegalStateException();
        retval = current.key;
        current = current.next;
        return retval;
    }


    /**
     * Returns the value to which the specified key is mapped in this hashtable.
     */
    public Object get(String key) {
        Entry tab[] = table;
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        for (Entry e = tab[index]; e != null; e = e.next) {
            if ((e.hash == hash) && (e.key == key))
                return e.value;
        }
        return null;
    }

    /**
     * Returns the value to which the specified key is mapped in this
     * hashtable ... the key isn't necessarily interned, though.
     */
    public Object getNonInterned(String key) {
        Entry tab[] = table;
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        for (Entry e = tab[index]; e != null; e = e.next) {
            if ((e.hash == hash) && e.key.equals(key))
                return e.value;
        }
        return null;
    }

    /**
     * Increases the capacity of and internally reorganizes this
     * hashtable, in order to accommodate and access its entries more
     * efficiently.  This method is called automatically when the
     * number of keys in the hashtable exceeds this hashtable's capacity
     * and load factor.
     */
    private void rehash() {
        int oldCapacity = table.length;
        Entry oldMap[] = table;

        int newCapacity = oldCapacity * 2 + 1;
        Entry newMap[] = new Entry[newCapacity];

        threshold = (int) (newCapacity * loadFactor);
        table = newMap;

        /*
        System.out.println("rehash old=" + oldCapacity
            + ", new=" + newCapacity
            + ", thresh=" + threshold
            + ", count=" + count);
        */

        for (int i = oldCapacity; i-- > 0;) {
            for (Entry old = oldMap[i]; old != null;) {
                Entry e = old;
                old = old.next;

                int index = (e.hash & 0x7FFFFFFF) % newCapacity;
                e.next = newMap[index];
                newMap[index] = e;
            }
        }
    }

    /**
     * Maps the specified <code>key</code> to the specified
     * <code>value</code> in this hashtable. Neither the key nor the
     * value can be <code>null</code>.
     * <p/>
     * <P>The value can be retrieved by calling the <code>get</code> method
     * with a key that is equal to the original key.
     */
    public Object put(Object key, Object value) {
        // Make sure the value is not null
        if (value == null) {
            throw new NullPointerException();
        }

        // Makes sure the key is not already in the hashtable.
        Entry tab[] = table;
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        for (Entry e = tab[index]; e != null; e = e.next) {
            // if ((e.hash == hash) && e.key.equals(key)) {
            if ((e.hash == hash) && (e.key == key)) {
                Object old = e.value;
                e.value = value;
                return old;
            }
        }

        if (count >= threshold) {
            // Rehash the table if the threshold is exceeded
            rehash();

            tab = table;
            index = (hash & 0x7FFFFFFF) % tab.length;
        }

        // Creates the new entry.
        Entry e = new Entry(hash, key, value, tab[index]);
        tab[index] = e;
        count++;
        return null;
    }


    /**
     * Hashtable collision list.
     */
    private static class Entry {
        int hash;
        Object key;
        Object value;
        Entry next;

        protected Entry(int hash, Object key, Object value, Entry next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }
}
