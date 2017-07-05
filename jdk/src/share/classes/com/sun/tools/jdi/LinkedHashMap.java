/*
 * Copyright (c) 1998, 2000, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.tools.jdi;

import java.io.*;
import java.util.*;

/**
 * Hash table based implementation of the Map interface.  This implementation
 * provides all of the optional Map operations, and permits null values and
 * the null key.  (HashMap is roughly equivalent to Hashtable, except that it
 * is unsynchronized and permits nulls.) In addition, elements in the map are
 * ordered and doubly linked together.
 * <p>
 * This implementation provides constant-time performance for the basic
 * operations (get and put), assuming the the hash function disperses the
 * elements properly among the buckets.  Iteration over Collection views
 * requires time proportional to its size (the number of key-value mappings)
 * and returns elements in the order they are linked. In a HashMap the
 * iteration would require time  proportional to the capacity of the map
 * plus the map size.
 * <p>
 * An instance of LinkedHashMap has two parameters that affect its efficiency:
 * its <i>capacity</i> and its <i>load factor</i>. The load factor should be
 * between 0.0 and 1.0. When the number of mappings in the LinkedHashMap exceeds
 * the product of the load factor and the current capacity, the capacity is
 * increased by calling the rehash method which requires time proportional
 * to the number of key-value mappings in the map. Larger load factors
 * use memory more efficiently, at the expense of larger expected time per
 * lookup.
 * <p>
 * If many mappings are to be stored in a LinkedHashMap, creating it with a
 * sufficiently large capacity will allow the mappings to be stored more
 * efficiently than letting it perform automatic rehashing as needed to grow
 * the table.
 * <p>
 * <strong>Note that this implementation is not synchronized.</strong> If
 * multiple threads access a LinkedHashMap concurrently, and at least one of the
 * threads modifies the LinkedHashMap structurally, it <em>must</em> be
 * synchronized externally.  (A structural modification is any operation that
 * adds or deletes one or more mappings; merely changing the value associated
 * with a key that is already contained in the Table is not a structural
 * modification.)  This is typically accomplished by synchronizing on some
 * object that naturally encapsulates the LinkedHashMap.  If no such object
 * exists, the LinkedHashMap should be "wrapped" using the
 * Collections.synchronizedSet method.  This is best done at creation time, to
 * prevent accidental unsynchronized access to the LinkedHashMap:
 * <pre>
 *      Map m = Collections.synchronizedMap(new LinkedHashMap(...));
 * </pre>
 * <p>
 * The Iterators returned by the iterator methods of the Collections returned
 * by all of LinkedHashMap's "collection view methods" are <em>fail-fast</em>:
 * if the LinkedHashMap is structurally modified at any time after the Iterator
 * is created, in any way except through the Iterator's own remove or add
 * methods, the Iterator will throw a ConcurrentModificationException.  Thus,
 * in the face of concurrent modification, the Iterator fails quickly and
 * cleanly, rather than risking arbitrary, non-deterministic behavior at an
 * undetermined time in the future.
 *
 * @author  Josh Bloch
 * @author  Arthur van Hoff
 * @author  Zhenghua Li
 * @see     Object#hashCode()
 * @see     java.util.Collection
 * @see     java.util.Map
 * @see     java.util.TreeMap
 * @see     java.util.Hashtable
 * @see     java.util.HashMap
 */

import java.io.Serializable;

public class LinkedHashMap extends AbstractMap implements Map, Serializable {
    /**
     * The hash table data.
     */
    private transient Entry table[];

    /**
     * The head of the double linked list.
     */
    private transient Entry header;

    /**
     * The total number of mappings in the hash table.
     */
    private transient int count;

    /**
     * Rehashes the table when count exceeds this threshold.
     */
    private int threshold;

    /**
     * The load factor for the LinkedHashMap.
     */
    private float loadFactor;

    /**
     * The number of times this LinkedHashMap has been structurally modified
     * Structural modifications are those that change the number of mappings in
     * the LinkedHashMap or otherwise modify its internal structure (e.g.,
     * rehash).  This field is used to make iterators on Collection-views of
     * the LinkedHashMap fail-fast.  (See ConcurrentModificationException).
     */
    private transient int modCount = 0;

    /**
     * Constructs a new, empty LinkedHashMap with the specified initial
     * capacity and the specified load factor.
     *
     * @param      initialCapacity   the initial capacity of the LinkedHashMap.
     * @param      loadFactor        a number between 0.0 and 1.0.
     * @exception  IllegalArgumentException  if the initial capacity is less
     *               than or equal to zero, or if the load factor is less than
     *               or equal to zero.
     */
    public LinkedHashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal Initial Capacity: "+
                                               initialCapacity);
        if ((loadFactor > 1) || (loadFactor <= 0))
            throw new IllegalArgumentException("Illegal Load factor: "+
                                               loadFactor);
        if (initialCapacity==0)
            initialCapacity = 1;
        this.loadFactor = loadFactor;
        table = new Entry[initialCapacity];
        threshold = (int)(initialCapacity * loadFactor);
        header = new Entry(-1, null, null, null);
        header.before = header.after = header;
    }

    /**
     * Constructs a new, empty LinkedHashMap with the specified initial capacity
     * and default load factor.
     *
     * @param   initialCapacity   the initial capacity of the LinkedHashMap.
     */
    public LinkedHashMap(int initialCapacity) {
        this(initialCapacity, 0.75f);
    }

    /**
     * Constructs a new, empty LinkedHashMap with a default capacity and load
     * factor.
     */
    public LinkedHashMap() {
        this(101, 0.75f);
    }

    /**
     * Constructs a new LinkedHashMap with the same mappings as the given
     * Map.  The LinkedHashMap is created with a capacity of thrice the number
     * of mappings in the given Map or 11 (whichever is greater), and a
     * default load factor.
     */
    public LinkedHashMap(Map t) {
        this(Math.max(3*t.size(), 11), 0.75f);
        putAll(t);
    }

    /**
     * Returns the number of key-value mappings in this Map.
     */
    public int size() {
        return count;
    }

    /**
     * Returns true if this Map contains no key-value mappings.
     */
    public boolean isEmpty() {
        return count == 0;
    }

    /**
     * Returns true if this LinkedHashMap maps one or more keys to the specified
     * value.
     *
     * @param value value whose presence in this Map is to be tested.
     */
    public boolean containsValue(Object value) {
        if (value==null) {
            for (Entry e = header.after; e != header; e = e.after)
                if (e.value==null)
                    return true;
        } else {
            for (Entry e = header.after; e != header; e = e.after)
                if (value.equals(e.value))
                    return true;
        }
        return false;
    }

    /**
     * Returns true if this LinkedHashMap contains a mapping for the specified
     * key.
     *
     * @param key key whose presence in this Map is to be tested.
     */
    public boolean containsKey(Object key) {
        Entry tab[] = table;
        if (key != null) {
            int hash = key.hashCode();
            int index = (hash & 0x7FFFFFFF) % tab.length;
            for (Entry e = tab[index]; e != null; e = e.next)
                if (e.hash==hash && e.key.equals(key))
                    return true;
        } else {
            for (Entry e = tab[0]; e != null; e = e.next)
                if (e.key==null)
                    return true;
        }

        return false;
    }

    /**
     * Returns the value to which this LinkedHashMap maps the specified key.
     * Returns null if the LinkedHashMap contains no mapping for this key.
     * A return value of null does not <em>necessarily</em> indicate that the
     * LinkedHashMap contains no mapping for the key; it's also possible that
     * the LinkedHashMap explicitly maps the key to null.  The containsKey
     * operation may be used to distinguish these two cases.
     *
     * @param key key whose associated value is to be returned.
     */
    public Object get(Object key) {
        Entry e = getEntry(key);
        return e==null ? null : e.value;
    }

    /**
     * Returns the entry associated with the specified key in the LinkedHashMap.
     * Returns null if the LinkedHashMap contains no mapping for this key.
     */
    private Entry getEntry(Object key) {
        Entry tab[] = table;

        if (key != null) {
            int hash = key.hashCode();
            int index = (hash & 0x7FFFFFFF) % tab.length;
            for (Entry e = tab[index]; e != null; e = e.next)
                if ((e.hash == hash) && e.key.equals(key))
                    return e;
        } else {
            for (Entry e = tab[0]; e != null; e = e.next)
                if (e.key==null)
                    return e;
        }

        return null;
    }

    /**
     * Rehashes the contents of the LinkedHashMap into a LinkedHashMap with a
     * larger capacity. This method is called automatically when the
     * number of keys in the LinkedHashMap exceeds this LinkedHashMap's capacity
     * and load factor.
     */
    private void rehash() {
        int oldCapacity = table.length;
        Entry oldMap[] = table;

        int newCapacity = oldCapacity * 2 + 1;
        Entry newMap[] = new Entry[newCapacity];

        modCount++;
        threshold = (int)(newCapacity * loadFactor);
        table = newMap;

        for (Entry e = header.after; e != header; e = e.after) {
            int index = (e.hash & 0x7FFFFFFF) % newCapacity;
            e.next = newMap[index];
            newMap[index] = e;
        }
    }

    /**
     * Remove an entry from the linked list.
     */
    private void listRemove(Entry entry) {
        if (entry == null) {
            return;
        }
        entry.before.after = entry.after;
        entry.after.before = entry.before;
    }

   /**
    * Add the specified entry before the specified existing entry to
    * the linked list.
    */
    private void listAddBefore(Entry entry, Entry existEntry) {
        entry.after = existEntry;
        entry.before = existEntry.before;
        entry.before.after = entry;
        entry.after.before = entry;
    }

    /**
     * Returns the position of the mapping for the specified key
     * in the ordered map.
     *
     * @param key the specified key.
     * @return index of the key mapping.
     */
    public int indexOf(Object key) {
        int i = 0;
        if (key == null) {
            for (Entry e = header.after; e != header; e = e.after, i++)
                if (e.key == null)
                    return i;
        } else {
            for (Entry e = header.after; e != header; e = e.after, i++)
                if(key.equals(e.key))
                    return i;
        }
        return -1;
    }

    /**
     * Associates the specified value with the specified key in this
     * LinkedHashMap. If the LinkedHashMap previously contained a mapping for
     * this key, the old value is replaced and the position of this mapping
     * entry in the double linked list remains the same. Otherwise, a new
     * mapping entry is created and inserted into the list before the specified
     * existing mapping entry. The method returns the previous value associated
     * with the specified key, or null if there was no mapping for key.  A null
     * return can also indicate that the LinkedHashMap previously associated
     * null with the specified key.
     */
    private Object putAhead(Object key, Object value, Entry existEntry) {
        // Makes sure the key is not already in the LinkedHashMap.
        Entry tab[] = table;
        int hash = 0;
        int index = 0;

        if (key != null) {
            hash = key.hashCode();
            index = (hash & 0x7FFFFFFF) % tab.length;
            for (Entry e = tab[index] ; e != null ; e = e.next) {
                if ((e.hash == hash) && e.key.equals(key)) {
                    Object old = e.value;
                    e.value = value;
                    return old;
                }
            }
        } else {
            for (Entry e = tab[0] ; e != null ; e = e.next) {
                if (e.key == null) {
                    Object old = e.value;
                    e.value = value;
                    return old;
                }
            }
        }

        modCount++;
        if (count >= threshold) {
            // Rehash the table if the threshold is exceeded
            rehash();
            tab = table;
            index = (hash & 0x7FFFFFFF) % tab.length;
        }

        // Creates the new entry.
        Entry e = new Entry(hash, key, value, tab[index]);
        tab[index] = e;
        listAddBefore(e, existEntry);
        count++;
        return null;
    }

    /**
     * Associates the specified value with the specified key in this
     * LinkedHashMap and position the mapping at the specified index.
     * If the LinkedHashMap previously contained a mapping for this key,
     * the old value is replaced and the position of this mapping entry
     * in the double linked list remains the same. Otherwise, a new mapping
     * entry is created and inserted into the list at the specified
     * position.
     *
     * @param index     the position to put the key-value mapping.
     * @param key       key with which the specified value is to be associated.
     * @param value     value to be associated with the specified key.
     * @return previous value associated with specified key, or null if there
     *         was no mapping for key.  A null return can also indicate that
     *         the LinkedHashMap previously associated null with the specified
     *         key.
     */
    public Object put(int index, Object key, Object value) {
        if (index < 0 || index > count)
            throw new IndexOutOfBoundsException();
        Entry e = header.after;
        if (index == count)
            return putAhead(key, value, header); //fast approach for append
        else {
            for (int i = 0; i < index; i++)
                e = e.after;
            return putAhead(key, value, e);
        }
    }


    /**
     * Associates the specified value with the specified key in this
     * LinkedHashMap. If the LinkedHashMap previously contained a mapping for
     * this key, the old value is replaced. The mapping entry is also appended
     * to the end of the ordered linked list.
     *
     * @param key key with which the specified value is to be associated.
     * @param value value to be associated with the specified key.
     * @return previous value associated with specified key, or null if there
     *         was no mapping for key.  A null return can also indicate that
     *         the LinkedHashMap previously associated null with the specified
     *         key.
     */
    public Object put(Object key, Object value) {
        return putAhead(key, value, header);
    }

    /**
     * Removes the mapping for this key from this LinkedHashMap if present.
     * The mapping would also be removed from the double linked list.
     *
     * @param key key whose mapping is to be removed from the Map.
     * @return previous value associated with specified key, or null if there
     *         was no mapping for key.  A null return can also indicate that
     *         the LinkedHashMap previously associated null with the specified
     *         key.
     */
    public Object remove(Object key) {
        Entry tab[] = table;

        if (key != null) {
            int hash = key.hashCode();
            int index = (hash & 0x7FFFFFFF) % tab.length;

            for (Entry e = tab[index], prev = null; e != null;
                 prev = e, e = e.next) {
                if ((e.hash == hash) && e.key.equals(key)) {
                    modCount++;
                    if (prev != null)
                        prev.next = e.next;
                    else
                        tab[index] = e.next;

                    count--;
                    Object oldValue = e.value;
                    e.value = null;

                    listRemove(e);
                    return oldValue;
                }
            }
        } else {
            for (Entry e = tab[0], prev = null; e != null;
                 prev = e, e = e.next) {
                if (e.key == null) {
                    modCount++;
                    if (prev != null)
                        prev.next = e.next;
                    else
                        tab[0] = e.next;

                    count--;
                    Object oldValue = e.value;
                    e.value = null;

                    listRemove(e);
                    return oldValue;
                }
            }
        }

        return null;
    }

    /**
     * Copies all of the mappings from the specified Map to this LinkedHashMap
     * These mappings will replace any mappings that this LinkedHashMap had for
     * any of the keys currently in the specified Map.
     *
      * @param t Mappings to be stored in this Map.
     */
    public void putAll(Map t) {
        Iterator i = t.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry e = (Map.Entry) i.next();
            put(e.getKey(), e.getValue());
        }
    }

    /**
     * Removes all mappings from this LinkedHashMap.
     */
    public void clear() {
        Entry tab[] = table;
        modCount++;
        for (int index = tab.length; --index >= 0; )
            tab[index] = null;
        count = 0;
        header.before = header.after = header;
    }

    /**
     * Returns a shallow copy of this LinkedHashMap. The keys and values
     * themselves are not cloned.
     */
    public Object clone() {
        return new LinkedHashMap(this);
    }

    // Views

    private transient Set keySet = null;
    private transient Set entries = null;
    private transient Collection values = null;

    /**
     * Returns a Set view of the keys contained in this LinkedHashMap.  The Set
     * is backed by the LinkedHashMap, so changes to the LinkedHashMap are
     * reflected in the Set, and vice-versa.  The Set supports element removal,
     * which removes the corresponding mapping from the LinkedHashMap, via the
     * Iterator.remove, Set.remove, removeAll retainAll, and clear operations.
     * It does not support the add or addAll operations.
     */
    public Set keySet() {
        if (keySet == null) {
            keySet = new AbstractSet() {
                public Iterator iterator() {
                    return new HashIterator(KEYS);
                }
                public int size() {
                    return count;
                }
                public boolean contains(Object o) {
                    return containsKey(o);
                }
                public boolean remove(Object o) {
                    return LinkedHashMap.this.remove(o) != null;
                }
                public void clear() {
                    LinkedHashMap.this.clear();
                }
            };
        }
        return keySet;
    }

    /**
     * Returns a Collection view of the values contained in this LinkedHashMap.
     * The Collection is backed by the LinkedHashMap, so changes to the
     * LinkedHashMap are reflected in the Collection, and vice-versa.  The
     * Collection supports element removal, which removes the corresponding
     * mapping from the LinkedHashMap, via the Iterator.remove,
     * Collection.remove, removeAll, retainAll and clear operations.  It does
     * not support the add or addAll operations.
     */
    public Collection values() {
        if (values==null) {
            values = new AbstractCollection() {
                public Iterator iterator() {
                    return new HashIterator(VALUES);
                }
                public int size() {
                    return count;
                }
                public boolean contains(Object o) {
                    return containsValue(o);
                }
                public void clear() {
                    LinkedHashMap.this.clear();
                }
            };
        }
        return values;
    }

    /**
     * Returns a Collection view of the mappings contained in this
     * LinkedHashMap. Each element in the returned collection is a Map.Entry.
     * The Collection is backed by the LinkedHashMap, so changes to the
     * LinkedHashMap are reflected in the Collection, and vice-versa.  The
     * Collection supports element removal, which removes the corresponding
     * mapping from the LinkedHashMap, via the Iterator.remove,
     * Collection.remove, removeAll, retainAll and clear operations.  It does
     * not support the add or addAll operations.
     *
     * @see   java.util.Map.Entry
     */
    public Set entrySet() {
        if (entries==null) {
            entries = new AbstractSet() {
                public Iterator iterator() {
                    return new HashIterator(ENTRIES);
                }

                public boolean contains(Object o) {
                    if (!(o instanceof Map.Entry))
                        return false;
                    Map.Entry entry = (Map.Entry)o;
                    Object key = entry.getKey();
                    Entry tab[] = table;
                    int hash = (key==null ? 0 : key.hashCode());
                    int index = (hash & 0x7FFFFFFF) % tab.length;

                    for (Entry e = tab[index]; e != null; e = e.next)
                        if (e.hash==hash && e.equals(entry))
                            return true;
                    return false;
                }

                public boolean remove(Object o) {
                    if (!(o instanceof Map.Entry))
                        return false;
                    Map.Entry entry = (Map.Entry)o;
                    Object key = entry.getKey();
                    Entry tab[] = table;
                    int hash = (key==null ? 0 : key.hashCode());
                    int index = (hash & 0x7FFFFFFF) % tab.length;

                    for (Entry e = tab[index], prev = null; e != null;
                         prev = e, e = e.next) {
                        if (e.hash==hash && e.equals(entry)) {
                            modCount++;
                            if (prev != null)
                                prev.next = e.next;
                            else
                                tab[index] = e.next;

                            count--;
                            e.value = null;
                            listRemove(e);
                            return true;
                        }
                    }
                    return false;
                }

                public int size() {
                    return count;
                }

                public void clear() {
                    LinkedHashMap.this.clear();
                }
            };
        }

        return entries;
    }

    /**
     * Compares the specified Object with this Map for equality.
     * Returns true if the given object is also a LinkedHashMap and the two
     * Maps represent the same mappings in the same order.  More formally,
     * two Maps <code>t1</code> and <code>t2</code> represent the same mappings
     * if <code>t1.keySet().equals(t2.keySet())</code> and for every
     * key <code>k</code> in <code>t1.keySet()</code>, <code>
     * (t1.get(k)==null ? t2.get(k)==null : t1.get(k).equals(t2.get(k)))
     * </code>.
     * <p>
     * This implementation first checks if the specified Object is this Map;
     * if so it returns true.  Then, it checks if the specified Object is
     * a Map whose size is identical to the size of this Set; if not, it
     * it returns false.  If so, it iterates over this Map and the specified
     * Map's entrySet() Collection, and checks that the specified Map contains
     * each mapping that this Map contains at the same position.  If the
     * specified Map fails to contain such a mapping in the right order, false
     * is returned.  If the iteration completes, true is returned.
     *
     * @param o Object to be compared for equality with this Map.
     * @return true if the specified Object is equal to this Map.
     *
     */
    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof LinkedHashMap))
            return false;
        LinkedHashMap t = (LinkedHashMap) o;
        if (t.size() != size())
            return false;

        Iterator i1 = entrySet().iterator();
        Iterator i2 = t.entrySet().iterator();

        while (i1.hasNext()) {
            Entry e1 = (Entry) i1.next();
            Entry e2 = (Entry) i2.next();

            Object key1 = e1.getKey();
            Object value1 = e1.getValue();
            Object key2 = e2.getKey();
            Object value2 = e2.getValue();

            if ((key1 == null ? key2 == null : key1.equals(key2)) &&
                (value1 == null ? value2 == null : value1.equals(value2))) {
                continue;
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * LinkedHashMap collision list entry.
     */
    private static class Entry implements Map.Entry {
        int hash;
        Object key;
        Object value;
        Entry next;

        // These fields comprise the doubly linked list that is used for
        // iteration.
        Entry before, after;

        Entry(int hash, Object key, Object value, Entry next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }

        // Map.Entry Ops

        public Object getKey() {
            return key;
        }

        public Object getValue() {
            return value;
        }

        public Object setValue(Object value) {
            Object oldValue = this.value;
            this.value = value;
            return oldValue;
        }

        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry e = (Map.Entry)o;

            return (key==null ? e.getKey()==null : key.equals(e.getKey())) &&
               (value==null ? e.getValue()==null : value.equals(e.getValue()));
        }

        public int hashCode() {
            return hash ^ (value==null ? 0 : value.hashCode());
        }

        public String toString() {
            return key+"="+value;
        }
    }

    // Types of Iterators
    private static final int KEYS = 0;
    private static final int VALUES = 1;
    private static final int ENTRIES = 2;

    private class HashIterator implements Iterator {
        private Entry[] table = LinkedHashMap.this.table;
        private Entry entry = null;
        private Entry lastReturned = null;
        private int type;

        /**
         * The modCount value that the iterator believes that the backing
         * List should have.  If this expectation is violated, the iterator
         * has detected concurrent modification.
         */
        private int expectedModCount = modCount;

        HashIterator(int type) {
            this.type = type;
            this.entry = LinkedHashMap.this.header.after;
        }

        public boolean hasNext() {
            return entry != header;
        }

        public Object next() {
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            if (entry == LinkedHashMap.this.header)
                throw new NoSuchElementException();

            Entry e = lastReturned = entry;
            entry = e.after;
            return type == KEYS ? e.key : (type == VALUES ? e.value : e);
        }

        public void remove() {
            if (lastReturned == null)
                throw new IllegalStateException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();

            Entry[] tab = LinkedHashMap.this.table;
            int index = (lastReturned.hash & 0x7FFFFFFF) % tab.length;

            for (Entry e = tab[index], prev = null; e != null;
                 prev = e, e = e.next) {
                if (e == lastReturned) {
                    modCount++;
                    expectedModCount++;
                    if (prev == null)
                        tab[index] = e.next;
                    else
                        prev.next = e.next;
                    count--;
                    listRemove(e);
                    lastReturned = null;
                    return;
                }
            }
            throw new ConcurrentModificationException();
        }
    }

    /**
     * Save the state of the LinkedHashMap to a stream (i.e., serialize it).
     * The objects will be written out in the order they are linked
     * in the list.
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws IOException
    {
        // Write out the threshold, loadfactor, and any hidden stuff
        s.defaultWriteObject();

        // Write out number of buckets
        s.writeInt(table.length);

        // Write out size (number of Mappings)
        s.writeInt(count);

        // Write out keys and values (alternating)
        for (Entry e = header.after; e != header; e = e.after) {
            s.writeObject(e.key);
            s.writeObject(e.value);
        }
    }

    /**
     * Reconstitute the LinkedHashMap from a stream (i.e., deserialize it).
     */
    private void readObject(java.io.ObjectInputStream s)
         throws IOException, ClassNotFoundException
    {
        // Read in the threshold, loadfactor, and any hidden stuff
        s.defaultReadObject();

        // Read in number of buckets and allocate the bucket array;
        int numBuckets = s.readInt();
        table = new Entry[numBuckets];
        header = new Entry(-1, null, null, null);
        header.before = header;
        header.after = header;

        // Read in size (number of Mappings)
        int size = s.readInt();

        // Read the keys and values, and put the mappings in the LinkedHashMap
        for (int i=0; i<size; i++) {
            Object key = s.readObject();
            Object value = s.readObject();
            put(key, value);
        }
    }
}
