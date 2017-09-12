/*
 * Copyright (c) 1999, 2004, Oracle and/or its affiliates. All rights reserved.
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

/*
 * Licensed Materials - Property of IBM
 * RMI-IIOP v1.0
 * Copyright IBM Corp. 1998 1999  All Rights Reserved
 *
 */

package com.sun.corba.se.impl.util;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * IdentityHashtable is a modified copy of the 1.1.6 Hashtable class which
 * does not rely on the hashCode() and equals() methods of the key or value;
 * instead, it uses the System.identityHashcode() method and pointer comparison.
 * In addition, all synchronization has been removed.
 */
public final class IdentityHashtable extends Dictionary {
    /**
     * The hash table data.
     */
    private transient IdentityHashtableEntry table[];

    /**
     * The total number of entries in the hash table.
     */
    private transient int count;

    /**
     * Rehashes the table when count exceeds this threshold.
     */
    private int threshold;

    /**
     * The load factor for the hashtable.
     */
    private float loadFactor;

    /**
     * Constructs a new, empty hashtable with the specified initial
     * capacity and the specified load factor.
     *
     * @param      initialCapacity   the initial capacity of the hashtable.
     * @param      loadFactor        a number between 0.0 and 1.0.
     * @exception  IllegalArgumentException  if the initial capacity is less
     *               than or equal to zero, or if the load factor is less than
     *               or equal to zero.
     * @since      JDK1.0
     */
    public IdentityHashtable(int initialCapacity, float loadFactor) {
        if ((initialCapacity <= 0) || (loadFactor <= 0.0)) {
            throw new IllegalArgumentException();
        }
        this.loadFactor = loadFactor;
        table = new IdentityHashtableEntry[initialCapacity];
        threshold = (int)(initialCapacity * loadFactor);
    }

    /**
     * Constructs a new, empty hashtable with the specified initial capacity
     * and default load factor.
     *
     * @param   initialCapacity   the initial capacity of the hashtable.
     * @since   JDK1.0
     */
    public IdentityHashtable(int initialCapacity) {
        this(initialCapacity, 0.75f);
    }

    /**
     * Constructs a new, empty hashtable with a default capacity and load
     * factor.
     *
     * @since   JDK1.0
     */
    public IdentityHashtable() {
        this(101, 0.75f);
    }

    /**
     * Returns the number of keys in this hashtable.
     *
     * @return  the number of keys in this hashtable.
     * @since   JDK1.0
     */
    public int size() {
        return count;
    }

    /**
     * Tests if this hashtable maps no keys to values.
     *
     * @return  <code>true</code> if this hashtable maps no keys to values;
     *          <code>false</code> otherwise.
     * @since   JDK1.0
     */
    public boolean isEmpty() {
        return count == 0;
    }

    /**
     * Returns an enumeration of the keys in this hashtable.
     *
     * @return  an enumeration of the keys in this hashtable.
     * @see     java.util.Enumeration
     * @see     java.util.Hashtable#elements()
     * @since   JDK1.0
     */
    public Enumeration keys() {
        return new IdentityHashtableEnumerator(table, true);
    }

    /**
     * Returns an enumeration of the values in this hashtable.
     * Use the Enumeration methods on the returned object to fetch the elements
     * sequentially.
     *
     * @return  an enumeration of the values in this hashtable.
     * @see     java.util.Enumeration
     * @see     java.util.Hashtable#keys()
     * @since   JDK1.0
     */
    public Enumeration elements() {
        return new IdentityHashtableEnumerator(table, false);
    }

    /**
     * Tests if some key maps into the specified value in this hashtable.
     * This operation is more expensive than the <code>containsKey</code>
     * method.
     *
     * @param      value   a value to search for.
     * @return     <code>true</code> if some key maps to the
     *             <code>value</code> argument in this hashtable;
     *             <code>false</code> otherwise.
     * @exception  NullPointerException  if the value is <code>null</code>.
     * @see        java.util.Hashtable#containsKey(java.lang.Object)
     * @since      JDK1.0
     */
    public boolean contains(Object value) {
        if (value == null) {
            throw new NullPointerException();
        }

        IdentityHashtableEntry tab[] = table;
        for (int i = tab.length ; i-- > 0 ;) {
            for (IdentityHashtableEntry e = tab[i] ; e != null ; e = e.next) {
                if (e.value == value) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Tests if the specified object is a key in this hashtable.
     *
     * @param   key   possible key.
     * @return  <code>true</code> if the specified object is a key in this
     *          hashtable; <code>false</code> otherwise.
     * @see     java.util.Hashtable#contains(java.lang.Object)
     * @since   JDK1.0
     */
    public boolean containsKey(Object key) {
        IdentityHashtableEntry tab[] = table;
        int hash = System.identityHashCode(key);
        int index = (hash & 0x7FFFFFFF) % tab.length;
        for (IdentityHashtableEntry e = tab[index] ; e != null ; e = e.next) {
            if ((e.hash == hash) && e.key == key) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the value to which the specified key is mapped in this hashtable.
     *
     * @param   key   a key in the hashtable.
     * @return  the value to which the key is mapped in this hashtable;
     *          <code>null</code> if the key is not mapped to any value in
     *          this hashtable.
     * @see     java.util.Hashtable#put(java.lang.Object, java.lang.Object)
     * @since   JDK1.0
     */
    public Object get(Object key) {
        IdentityHashtableEntry tab[] = table;
        int hash = System.identityHashCode(key);
        int index = (hash & 0x7FFFFFFF) % tab.length;
        for (IdentityHashtableEntry e = tab[index] ; e != null ; e = e.next) {
            if ((e.hash == hash) && e.key == key) {
                return e.value;
            }
        }
        return null;
    }

    /**
     * Rehashes the contents of the hashtable into a hashtable with a
     * larger capacity. This method is called automatically when the
     * number of keys in the hashtable exceeds this hashtable's capacity
     * and load factor.
     *
     * @since   JDK1.0
     */
    protected void rehash() {
        int oldCapacity = table.length;
        IdentityHashtableEntry oldTable[] = table;

        int newCapacity = oldCapacity * 2 + 1;
        IdentityHashtableEntry newTable[] = new IdentityHashtableEntry[newCapacity];

        threshold = (int)(newCapacity * loadFactor);
        table = newTable;

        //System.out.println("rehash old=" + oldCapacity + ", new=" + newCapacity + ", thresh=" + threshold + ", count=" + count);

        for (int i = oldCapacity ; i-- > 0 ;) {
            for (IdentityHashtableEntry old = oldTable[i] ; old != null ; ) {
                IdentityHashtableEntry e = old;
                old = old.next;

                int index = (e.hash & 0x7FFFFFFF) % newCapacity;
                e.next = newTable[index];
                newTable[index] = e;
            }
        }
    }

    /**
     * Maps the specified <code>key</code> to the specified
     * <code>value</code> in this hashtable. Neither the key nor the
     * value can be <code>null</code>.
     * <p>
     * The value can be retrieved by calling the <code>get</code> method
     * with a key that is equal to the original key.
     *
     * @param      key     the hashtable key.
     * @param      value   the value.
     * @return     the previous value of the specified key in this hashtable,
     *             or <code>null</code> if it did not have one.
     * @exception  NullPointerException  if the key or value is
     *               <code>null</code>.
     * @see     java.util.Hashtable#get(java.lang.Object)
     * @since   JDK1.0
     */
    public Object put(Object key, Object value) {
        // Make sure the value is not null
        if (value == null) {
            throw new NullPointerException();
        }

        // Makes sure the key is not already in the hashtable.
        IdentityHashtableEntry tab[] = table;
        int hash = System.identityHashCode(key);
        int index = (hash & 0x7FFFFFFF) % tab.length;
        for (IdentityHashtableEntry e = tab[index] ; e != null ; e = e.next) {
            if ((e.hash == hash) && e.key == key) {
                Object old = e.value;
                e.value = value;
                return old;
            }
        }

        if (count >= threshold) {
            // Rehash the table if the threshold is exceeded
            rehash();
            return put(key, value);
        }

        // Creates the new entry.
        IdentityHashtableEntry e = new IdentityHashtableEntry();
        e.hash = hash;
        e.key = key;
        e.value = value;
        e.next = tab[index];
        tab[index] = e;
        count++;
        return null;
    }

        /**
     * Removes the key (and its corresponding value) from this
     * hashtable. This method does nothing if the key is not in the hashtable.
     *
     * @param   key   the key that needs to be removed.
     * @return  the value to which the key had been mapped in this hashtable,
     *          or <code>null</code> if the key did not have a mapping.
     * @since   JDK1.0
     */
    public Object remove(Object key) {
        IdentityHashtableEntry tab[] = table;
        int hash = System.identityHashCode(key);
        int index = (hash & 0x7FFFFFFF) % tab.length;
        for (IdentityHashtableEntry e = tab[index], prev = null ; e != null ; prev = e, e = e.next) {
            if ((e.hash == hash) && e.key == key) {
                if (prev != null) {
                    prev.next = e.next;
                } else {
                    tab[index] = e.next;
                }
                count--;
                return e.value;
            }
        }
        return null;
    }

    /**
     * Clears this hashtable so that it contains no keys.
     *
     * @since   JDK1.0
     */
    public void clear() {
        IdentityHashtableEntry tab[] = table;
        for (int index = tab.length; --index >= 0; )
            tab[index] = null;
        count = 0;
    }

    /**
     * Returns a rather long string representation of this hashtable.
     *
     * @return  a string representation of this hashtable.
     * @since   JDK1.0
     */
    public String toString() {
        int max = size() - 1;
        StringBuffer buf = new StringBuffer();
        Enumeration k = keys();
        Enumeration e = elements();
        buf.append("{");

        for (int i = 0; i <= max; i++) {
            String s1 = k.nextElement().toString();
            String s2 = e.nextElement().toString();
            buf.append(s1 + "=" + s2);
            if (i < max) {
                buf.append(", ");
            }
        }
        buf.append("}");
        return buf.toString();
    }
}
