/*
 * Copyright (c) 1995, 1996, Oracle and/or its affiliates. All rights reserved.
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

package sun.misc;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * Caches the collision list.
 */
class CacheEntry extends Ref {
    int hash;
    Object key;
    CacheEntry next;
    public Object reconstitute() {
        return null;
    }
}

/**
 * The Cache class. Maps keys to values. Any object can be used as
 * a key and/or value.  This is very similar to the Hashtable
 * class, except that after putting an object into the Cache,
 * it is not guaranteed that a subsequent get will return it.
 * The Cache will automatically remove entries if memory is
 * getting tight and if the entry is not referenced from outside
 * the Cache.<p>
 *
 * To sucessfully store and retrieve objects from a hash table the
 * object used as the key must implement the hashCode() and equals()
 * methods.<p>
 *
 * This example creates a Cache of numbers. It uses the names of
 * the numbers as keys:
 * <pre>
 *      Cache numbers = new Cache();
 *      numbers.put("one", new Integer(1));
 *      numbers.put("two", new Integer(1));
 *      numbers.put("three", new Integer(1));
 * </pre>
 * To retrieve a number use:
 * <pre>
 *      Integer n = (Integer)numbers.get("two");
 *      if (n != null) {
 *          System.out.println("two = " + n);
 *      }
 * </pre>
 *
 * @see java.lang.Object#hashCode
 * @see java.lang.Object#equals
 * @see sun.misc.Ref
 */
public
class Cache extends Dictionary {
    /**
     * The hash table data.
     */
    private CacheEntry table[];

    /**
     * The total number of entries in the hash table.
     */
    private int count;

    /**
     * Rehashes the table when count exceeds this threshold.
     */
    private int threshold;

    /**
     * The load factor for the hashtable.
     */
    private float loadFactor;

    private void init(int initialCapacity, float loadFactor) {
        if ((initialCapacity <= 0) || (loadFactor <= 0.0)) {
            throw new IllegalArgumentException();
        }
        this.loadFactor = loadFactor;
        table = new CacheEntry[initialCapacity];
        threshold = (int) (initialCapacity * loadFactor);
    }

    /**
     * Constructs a new, empty Cache with the specified initial
     * capacity and the specified load factor.
     * @param initialCapacity the initial number of buckets
     * @param loadFactor a number between 0.0 and 1.0, it defines
     *          the threshold for rehashing the Cache into
     *          a bigger one.
     * @exception IllegalArgumentException If the initial capacity
     * is less than or equal to zero.
     * @exception IllegalArgumentException If the load factor is
     * less than or equal to zero.
     */
    public Cache (int initialCapacity, float loadFactor) {
        init(initialCapacity, loadFactor);
    }

    /**
     * Constructs a new, empty Cache with the specified initial
     * capacity.
     * @param initialCapacity the initial number of buckets
     */
    public Cache (int initialCapacity) {
        init(initialCapacity, 0.75f);
    }

    /**
     * Constructs a new, empty Cache. A default capacity and load factor
     * is used. Note that the Cache will automatically grow when it gets
     * full.
     */
    public Cache () {
        try {
            init(101, 0.75f);
        } catch (IllegalArgumentException ex) {
            // This should never happen
            throw new Error("panic");
        }
    }

    /**
     * Returns the number of elements contained within the Cache.
     */
    public int size() {
        return count;
    }

    /**
     * Returns true if the Cache contains no elements.
     */
    public boolean isEmpty() {
        return count == 0;
    }

    /**
     * Returns an enumeration of the Cache's keys.
     * @see Cache#elements
     * @see Enumeration
     */
    public synchronized Enumeration keys() {
        return new CacheEnumerator(table, true);
    }

    /**
     * Returns an enumeration of the elements. Use the Enumeration methods
     * on the returned object to fetch the elements sequentially.
     * @see Cache#keys
     * @see Enumeration
     */
    public synchronized Enumeration elements() {
        return new CacheEnumerator(table, false);
    }

    /**
     * Gets the object associated with the specified key in the Cache.
     * @param key the key in the hash table
     * @returns the element for the key or null if the key
     *          is not defined in the hash table.
     * @see Cache#put
     */
    public synchronized Object get(Object key) {
        CacheEntry tab[] = table;
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        for (CacheEntry e = tab[index]; e != null; e = e.next) {
            if ((e.hash == hash) && e.key.equals(key)) {
                return e.check();
            }
        }
        return null;
    }

    /**
     * Rehashes the contents of the table into a bigger table.
     * This is method is called automatically when the Cache's
     * size exceeds the threshold.
     */
    protected void rehash() {
        int oldCapacity = table.length;
        CacheEntry oldTable[] = table;

        int newCapacity = oldCapacity * 2 + 1;
        CacheEntry newTable[] = new CacheEntry[newCapacity];

        threshold = (int) (newCapacity * loadFactor);
        table = newTable;

        // System.out.println("rehash old=" + oldCapacity + ", new=" +
        // newCapacity + ", thresh=" + threshold + ", count=" + count);

        for (int i = oldCapacity; i-- > 0;) {
            for (CacheEntry old = oldTable[i]; old != null;) {
                CacheEntry e = old;
                old = old.next;
                if (e.check() != null) {
                    int index = (e.hash & 0x7FFFFFFF) % newCapacity;
                    e.next = newTable[index];
                    newTable[index] = e;
                } else
                    count--;    /* remove entries that have disappeared */
            }
        }
    }

    /**
     * Puts the specified element into the Cache, using the specified
     * key.  The element may be retrieved by doing a get() with the same
     * key.  The key and the element cannot be null.
     * @param key the specified hashtable key
     * @param value the specified element
     * @return the old value of the key, or null if it did not have one.
     * @exception NullPointerException If the value of the specified
     * element is null.
     * @see Cache#get
     */
    public synchronized Object put(Object key, Object value) {
        // Make sure the value is not null
        if (value == null) {
            throw new NullPointerException();
        }
        // Makes sure the key is not already in the cache.
        CacheEntry tab[] = table;
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        CacheEntry ne = null;
        for (CacheEntry e = tab[index]; e != null; e = e.next) {
            if ((e.hash == hash) && e.key.equals(key)) {
                Object old = e.check();
                e.setThing(value);
                return old;
            } else if (e.check() == null)
                ne = e;         /* reuse old flushed value */
        }

        if (count >= threshold) {
            // Rehash the table if the threshold is exceeded
            rehash();
            return put(key, value);
        }
        // Creates the new entry.
        if (ne == null) {
            ne = new CacheEntry ();
            ne.next = tab[index];
            tab[index] = ne;
            count++;
        }
        ne.hash = hash;
        ne.key = key;
        ne.setThing(value);
        return null;
    }

    /**
     * Removes the element corresponding to the key. Does nothing if the
     * key is not present.
     * @param key the key that needs to be removed
     * @return the value of key, or null if the key was not found.
     */
    public synchronized Object remove(Object key) {
        CacheEntry tab[] = table;
        int hash = key.hashCode();
        int index = (hash & 0x7FFFFFFF) % tab.length;
        for (CacheEntry e = tab[index], prev = null; e != null; prev = e, e = e.next) {
            if ((e.hash == hash) && e.key.equals(key)) {
                if (prev != null) {
                    prev.next = e.next;
                } else {
                    tab[index] = e.next;
                }
                count--;
                return e.check();
            }
        }
        return null;
    }
}

/**
 * A Cache enumerator class.  This class should remain opaque
 * to the client. It will use the Enumeration interface.
 */
class CacheEnumerator implements Enumeration {
    boolean keys;
    int index;
    CacheEntry table[];
    CacheEntry entry;

    CacheEnumerator (CacheEntry table[], boolean keys) {
        this.table = table;
        this.keys = keys;
        this.index = table.length;
    }

    public boolean hasMoreElements() {
        while (index >= 0) {
            while (entry != null)
                if (entry.check() != null)
                    return true;
                else
                    entry = entry.next;
            while (--index >= 0 && (entry = table[index]) == null) ;
        }
        return false;
    }

    public Object nextElement() {
        while (index >= 0) {
            if (entry == null)
                while (--index >= 0 && (entry = table[index]) == null) ;
            if (entry != null) {
                CacheEntry e = entry;
                entry = e.next;
                if (e.check() != null)
                    return keys ? e.key : e.check();
            }
        }
        throw new NoSuchElementException("CacheEnumerator");
    }

}
