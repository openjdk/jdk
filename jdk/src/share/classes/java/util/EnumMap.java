/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map.Entry;
import sun.misc.SharedSecrets;

/**
 * A specialized {@link Map} implementation for use with enum type keys.  All
 * of the keys in an enum map must come from a single enum type that is
 * specified, explicitly or implicitly, when the map is created.  Enum maps
 * are represented internally as arrays.  This representation is extremely
 * compact and efficient.
 *
 * <p>Enum maps are maintained in the <i>natural order</i> of their keys
 * (the order in which the enum constants are declared).  This is reflected
 * in the iterators returned by the collections views ({@link #keySet()},
 * {@link #entrySet()}, and {@link #values()}).
 *
 * <p>Iterators returned by the collection views are <i>weakly consistent</i>:
 * they will never throw {@link ConcurrentModificationException} and they may
 * or may not show the effects of any modifications to the map that occur while
 * the iteration is in progress.
 *
 * <p>Null keys are not permitted.  Attempts to insert a null key will
 * throw {@link NullPointerException}.  Attempts to test for the
 * presence of a null key or to remove one will, however, function properly.
 * Null values are permitted.

 * <P>Like most collection implementations <tt>EnumMap</tt> is not
 * synchronized. If multiple threads access an enum map concurrently, and at
 * least one of the threads modifies the map, it should be synchronized
 * externally.  This is typically accomplished by synchronizing on some
 * object that naturally encapsulates the enum map.  If no such object exists,
 * the map should be "wrapped" using the {@link Collections#synchronizedMap}
 * method.  This is best done at creation time, to prevent accidental
 * unsynchronized access:
 *
 * <pre>
 *     Map&lt;EnumKey, V&gt; m
 *         = Collections.synchronizedMap(new EnumMap&lt;EnumKey, V&gt;(...));
 * </pre>
 *
 * <p>Implementation note: All basic operations execute in constant time.
 * They are likely (though not guaranteed) to be faster than their
 * {@link HashMap} counterparts.
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @author Josh Bloch
 * @see EnumSet
 * @since 1.5
 */
public class EnumMap<K extends Enum<K>, V> extends AbstractMap<K, V>
    implements java.io.Serializable, Cloneable
{
    /**
     * The <tt>Class</tt> object for the enum type of all the keys of this map.
     *
     * @serial
     */
    private final Class<K> keyType;

    /**
     * All of the values comprising K.  (Cached for performance.)
     */
    private transient K[] keyUniverse;

    /**
     * Array representation of this map.  The ith element is the value
     * to which universe[i] is currently mapped, or null if it isn't
     * mapped to anything, or NULL if it's mapped to null.
     */
    private transient Object[] vals;

    /**
     * The number of mappings in this map.
     */
    private transient int size = 0;

    /**
     * Distinguished non-null value for representing null values.
     */
    private static final Object NULL = new Object();

    private Object maskNull(Object value) {
        return (value == null ? NULL : value);
    }

    private V unmaskNull(Object value) {
        return (V) (value == NULL ? null : value);
    }

    private static Enum[] ZERO_LENGTH_ENUM_ARRAY = new Enum[0];

    /**
     * Creates an empty enum map with the specified key type.
     *
     * @param keyType the class object of the key type for this enum map
     * @throws NullPointerException if <tt>keyType</tt> is null
     */
    public EnumMap(Class<K> keyType) {
        this.keyType = keyType;
        keyUniverse = getKeyUniverse(keyType);
        vals = new Object[keyUniverse.length];
    }

    /**
     * Creates an enum map with the same key type as the specified enum
     * map, initially containing the same mappings (if any).
     *
     * @param m the enum map from which to initialize this enum map
     * @throws NullPointerException if <tt>m</tt> is null
     */
    public EnumMap(EnumMap<K, ? extends V> m) {
        keyType = m.keyType;
        keyUniverse = m.keyUniverse;
        vals = m.vals.clone();
        size = m.size;
    }

    /**
     * Creates an enum map initialized from the specified map.  If the
     * specified map is an <tt>EnumMap</tt> instance, this constructor behaves
     * identically to {@link #EnumMap(EnumMap)}.  Otherwise, the specified map
     * must contain at least one mapping (in order to determine the new
     * enum map's key type).
     *
     * @param m the map from which to initialize this enum map
     * @throws IllegalArgumentException if <tt>m</tt> is not an
     *     <tt>EnumMap</tt> instance and contains no mappings
     * @throws NullPointerException if <tt>m</tt> is null
     */
    public EnumMap(Map<K, ? extends V> m) {
        if (m instanceof EnumMap) {
            EnumMap<K, ? extends V> em = (EnumMap<K, ? extends V>) m;
            keyType = em.keyType;
            keyUniverse = em.keyUniverse;
            vals = em.vals.clone();
            size = em.size;
        } else {
            if (m.isEmpty())
                throw new IllegalArgumentException("Specified map is empty");
            keyType = m.keySet().iterator().next().getDeclaringClass();
            keyUniverse = getKeyUniverse(keyType);
            vals = new Object[keyUniverse.length];
            putAll(m);
        }
    }

    // Query Operations

    /**
     * Returns the number of key-value mappings in this map.
     *
     * @return the number of key-value mappings in this map
     */
    public int size() {
        return size;
    }

    /**
     * Returns <tt>true</tt> if this map maps one or more keys to the
     * specified value.
     *
     * @param value the value whose presence in this map is to be tested
     * @return <tt>true</tt> if this map maps one or more keys to this value
     */
    public boolean containsValue(Object value) {
        value = maskNull(value);

        for (Object val : vals)
            if (value.equals(val))
                return true;

        return false;
    }

    /**
     * Returns <tt>true</tt> if this map contains a mapping for the specified
     * key.
     *
     * @param key the key whose presence in this map is to be tested
     * @return <tt>true</tt> if this map contains a mapping for the specified
     *            key
     */
    public boolean containsKey(Object key) {
        return isValidKey(key) && vals[((Enum)key).ordinal()] != null;
    }

    private boolean containsMapping(Object key, Object value) {
        return isValidKey(key) &&
            maskNull(value).equals(vals[((Enum)key).ordinal()]);
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or {@code null} if this map contains no mapping for the key.
     *
     * <p>More formally, if this map contains a mapping from a key
     * {@code k} to a value {@code v} such that {@code (key == k)},
     * then this method returns {@code v}; otherwise it returns
     * {@code null}.  (There can be at most one such mapping.)
     *
     * <p>A return value of {@code null} does not <i>necessarily</i>
     * indicate that the map contains no mapping for the key; it's also
     * possible that the map explicitly maps the key to {@code null}.
     * The {@link #containsKey containsKey} operation may be used to
     * distinguish these two cases.
     */
    public V get(Object key) {
        return (isValidKey(key) ?
                unmaskNull(vals[((Enum)key).ordinal()]) : null);
    }

    // Modification Operations

    /**
     * Associates the specified value with the specified key in this map.
     * If the map previously contained a mapping for this key, the old
     * value is replaced.
     *
     * @param key the key with which the specified value is to be associated
     * @param value the value to be associated with the specified key
     *
     * @return the previous value associated with specified key, or
     *     <tt>null</tt> if there was no mapping for key.  (A <tt>null</tt>
     *     return can also indicate that the map previously associated
     *     <tt>null</tt> with the specified key.)
     * @throws NullPointerException if the specified key is null
     */
    public V put(K key, V value) {
        typeCheck(key);

        int index = key.ordinal();
        Object oldValue = vals[index];
        vals[index] = maskNull(value);
        if (oldValue == null)
            size++;
        return unmaskNull(oldValue);
    }

    /**
     * Removes the mapping for this key from this map if present.
     *
     * @param key the key whose mapping is to be removed from the map
     * @return the previous value associated with specified key, or
     *     <tt>null</tt> if there was no entry for key.  (A <tt>null</tt>
     *     return can also indicate that the map previously associated
     *     <tt>null</tt> with the specified key.)
     */
    public V remove(Object key) {
        if (!isValidKey(key))
            return null;
        int index = ((Enum)key).ordinal();
        Object oldValue = vals[index];
        vals[index] = null;
        if (oldValue != null)
            size--;
        return unmaskNull(oldValue);
    }

    private boolean removeMapping(Object key, Object value) {
        if (!isValidKey(key))
            return false;
        int index = ((Enum)key).ordinal();
        if (maskNull(value).equals(vals[index])) {
            vals[index] = null;
            size--;
            return true;
        }
        return false;
    }

    /**
     * Returns true if key is of the proper type to be a key in this
     * enum map.
     */
    private boolean isValidKey(Object key) {
        if (key == null)
            return false;

        // Cheaper than instanceof Enum followed by getDeclaringClass
        Class keyClass = key.getClass();
        return keyClass == keyType || keyClass.getSuperclass() == keyType;
    }

    // Bulk Operations

    /**
     * Copies all of the mappings from the specified map to this map.
     * These mappings will replace any mappings that this map had for
     * any of the keys currently in the specified map.
     *
     * @param m the mappings to be stored in this map
     * @throws NullPointerException the specified map is null, or if
     *     one or more keys in the specified map are null
     */
    public void putAll(Map<? extends K, ? extends V> m) {
        if (m instanceof EnumMap) {
            EnumMap<? extends K, ? extends V> em =
                (EnumMap<? extends K, ? extends V>)m;
            if (em.keyType != keyType) {
                if (em.isEmpty())
                    return;
                throw new ClassCastException(em.keyType + " != " + keyType);
            }

            for (int i = 0; i < keyUniverse.length; i++) {
                Object emValue = em.vals[i];
                if (emValue != null) {
                    if (vals[i] == null)
                        size++;
                    vals[i] = emValue;
                }
            }
        } else {
            super.putAll(m);
        }
    }

    /**
     * Removes all mappings from this map.
     */
    public void clear() {
        Arrays.fill(vals, null);
        size = 0;
    }

    // Views

    /**
     * This field is initialized to contain an instance of the entry set
     * view the first time this view is requested.  The view is stateless,
     * so there's no reason to create more than one.
     */
    private transient Set<Map.Entry<K,V>> entrySet = null;

    /**
     * Returns a {@link Set} view of the keys contained in this map.
     * The returned set obeys the general contract outlined in
     * {@link Map#keySet()}.  The set's iterator will return the keys
     * in their natural order (the order in which the enum constants
     * are declared).
     *
     * @return a set view of the keys contained in this enum map
     */
    public Set<K> keySet() {
        Set<K> ks = keySet;
        if (ks != null)
            return ks;
        else
            return keySet = new KeySet();
    }

    private class KeySet extends AbstractSet<K> {
        public Iterator<K> iterator() {
            return new KeyIterator();
        }
        public int size() {
            return size;
        }
        public boolean contains(Object o) {
            return containsKey(o);
        }
        public boolean remove(Object o) {
            int oldSize = size;
            EnumMap.this.remove(o);
            return size != oldSize;
        }
        public void clear() {
            EnumMap.this.clear();
        }
    }

    /**
     * Returns a {@link Collection} view of the values contained in this map.
     * The returned collection obeys the general contract outlined in
     * {@link Map#values()}.  The collection's iterator will return the
     * values in the order their corresponding keys appear in map,
     * which is their natural order (the order in which the enum constants
     * are declared).
     *
     * @return a collection view of the values contained in this map
     */
    public Collection<V> values() {
        Collection<V> vs = values;
        if (vs != null)
            return vs;
        else
            return values = new Values();
    }

    private class Values extends AbstractCollection<V> {
        public Iterator<V> iterator() {
            return new ValueIterator();
        }
        public int size() {
            return size;
        }
        public boolean contains(Object o) {
            return containsValue(o);
        }
        public boolean remove(Object o) {
            o = maskNull(o);

            for (int i = 0; i < vals.length; i++) {
                if (o.equals(vals[i])) {
                    vals[i] = null;
                    size--;
                    return true;
                }
            }
            return false;
        }
        public void clear() {
            EnumMap.this.clear();
        }
    }

    /**
     * Returns a {@link Set} view of the mappings contained in this map.
     * The returned set obeys the general contract outlined in
     * {@link Map#keySet()}.  The set's iterator will return the
     * mappings in the order their keys appear in map, which is their
     * natural order (the order in which the enum constants are declared).
     *
     * @return a set view of the mappings contained in this enum map
     */
    public Set<Map.Entry<K,V>> entrySet() {
        Set<Map.Entry<K,V>> es = entrySet;
        if (es != null)
            return es;
        else
            return entrySet = new EntrySet();
    }

    private class EntrySet extends AbstractSet<Map.Entry<K,V>> {
        public Iterator<Map.Entry<K,V>> iterator() {
            return new EntryIterator();
        }
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry entry = (Map.Entry)o;
            return containsMapping(entry.getKey(), entry.getValue());
        }
        public boolean remove(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry entry = (Map.Entry)o;
            return removeMapping(entry.getKey(), entry.getValue());
        }
        public int size() {
            return size;
        }
        public void clear() {
            EnumMap.this.clear();
        }
        public Object[] toArray() {
            return fillEntryArray(new Object[size]);
        }
        @SuppressWarnings("unchecked")
        public <T> T[] toArray(T[] a) {
            int size = size();
            if (a.length < size)
                a = (T[])java.lang.reflect.Array
                    .newInstance(a.getClass().getComponentType(), size);
            if (a.length > size)
                a[size] = null;
            return (T[]) fillEntryArray(a);
        }
        private Object[] fillEntryArray(Object[] a) {
            int j = 0;
            for (int i = 0; i < vals.length; i++)
                if (vals[i] != null)
                    a[j++] = new AbstractMap.SimpleEntry<>(
                        keyUniverse[i], unmaskNull(vals[i]));
            return a;
        }
    }

    private abstract class EnumMapIterator<T> implements Iterator<T> {
        // Lower bound on index of next element to return
        int index = 0;

        // Index of last returned element, or -1 if none
        int lastReturnedIndex = -1;

        public boolean hasNext() {
            while (index < vals.length && vals[index] == null)
                index++;
            return index != vals.length;
        }

        public void remove() {
            checkLastReturnedIndex();

            if (vals[lastReturnedIndex] != null) {
                vals[lastReturnedIndex] = null;
                size--;
            }
            lastReturnedIndex = -1;
        }

        private void checkLastReturnedIndex() {
            if (lastReturnedIndex < 0)
                throw new IllegalStateException();
        }
    }

    private class KeyIterator extends EnumMapIterator<K> {
        public K next() {
            if (!hasNext())
                throw new NoSuchElementException();
            lastReturnedIndex = index++;
            return keyUniverse[lastReturnedIndex];
        }
    }

    private class ValueIterator extends EnumMapIterator<V> {
        public V next() {
            if (!hasNext())
                throw new NoSuchElementException();
            lastReturnedIndex = index++;
            return unmaskNull(vals[lastReturnedIndex]);
        }
    }

    /**
     * Since we don't use Entry objects, we use the Iterator itself as entry.
     */
    private class EntryIterator extends EnumMapIterator<Map.Entry<K,V>>
        implements Map.Entry<K,V>
    {
        public Map.Entry<K,V> next() {
            if (!hasNext())
                throw new NoSuchElementException();
            lastReturnedIndex = index++;
            return this;
        }

        public K getKey() {
            checkLastReturnedIndexForEntryUse();
            return keyUniverse[lastReturnedIndex];
        }

        public V getValue() {
            checkLastReturnedIndexForEntryUse();
            return unmaskNull(vals[lastReturnedIndex]);
        }

        public V setValue(V value) {
            checkLastReturnedIndexForEntryUse();
            V oldValue = unmaskNull(vals[lastReturnedIndex]);
            vals[lastReturnedIndex] = maskNull(value);
            return oldValue;
        }

        public boolean equals(Object o) {
            if (lastReturnedIndex < 0)
                return o == this;

            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry e = (Map.Entry)o;
            V ourValue = unmaskNull(vals[lastReturnedIndex]);
            Object hisValue = e.getValue();
            return e.getKey() == keyUniverse[lastReturnedIndex] &&
                (ourValue == hisValue ||
                 (ourValue != null && ourValue.equals(hisValue)));
        }

        public int hashCode() {
            if (lastReturnedIndex < 0)
                return super.hashCode();

            Object value = vals[lastReturnedIndex];
            return keyUniverse[lastReturnedIndex].hashCode()
                ^ (value == NULL ? 0 : value.hashCode());
        }

        public String toString() {
            if (lastReturnedIndex < 0)
                return super.toString();

            return keyUniverse[lastReturnedIndex] + "="
                + unmaskNull(vals[lastReturnedIndex]);
        }

        private void checkLastReturnedIndexForEntryUse() {
            if (lastReturnedIndex < 0)
                throw new IllegalStateException("Entry was removed");
        }
    }

    // Comparison and hashing

    /**
     * Compares the specified object with this map for equality.  Returns
     * <tt>true</tt> if the given object is also a map and the two maps
     * represent the same mappings, as specified in the {@link
     * Map#equals(Object)} contract.
     *
     * @param o the object to be compared for equality with this map
     * @return <tt>true</tt> if the specified object is equal to this map
     */
    public boolean equals(Object o) {
        if (!(o instanceof EnumMap))
            return super.equals(o);

        EnumMap em = (EnumMap)o;
        if (em.keyType != keyType)
            return size == 0 && em.size == 0;

        // Key types match, compare each value
        for (int i = 0; i < keyUniverse.length; i++) {
            Object ourValue =    vals[i];
            Object hisValue = em.vals[i];
            if (hisValue != ourValue &&
                (hisValue == null || !hisValue.equals(ourValue)))
                return false;
        }
        return true;
    }

    /**
     * Returns a shallow copy of this enum map.  (The values themselves
     * are not cloned.
     *
     * @return a shallow copy of this enum map
     */
    public EnumMap<K, V> clone() {
        EnumMap<K, V> result = null;
        try {
            result = (EnumMap<K, V>) super.clone();
        } catch(CloneNotSupportedException e) {
            throw new AssertionError();
        }
        result.vals = result.vals.clone();
        return result;
    }

    /**
     * Throws an exception if e is not of the correct type for this enum set.
     */
    private void typeCheck(K key) {
        Class keyClass = key.getClass();
        if (keyClass != keyType && keyClass.getSuperclass() != keyType)
            throw new ClassCastException(keyClass + " != " + keyType);
    }

    /**
     * Returns all of the values comprising K.
     * The result is uncloned, cached, and shared by all callers.
     */
    private static <K extends Enum<K>> K[] getKeyUniverse(Class<K> keyType) {
        return SharedSecrets.getJavaLangAccess()
                                        .getEnumConstantsShared(keyType);
    }

    private static final long serialVersionUID = 458661240069192865L;

    /**
     * Save the state of the <tt>EnumMap</tt> instance to a stream (i.e.,
     * serialize it).
     *
     * @serialData The <i>size</i> of the enum map (the number of key-value
     *             mappings) is emitted (int), followed by the key (Object)
     *             and value (Object) for each key-value mapping represented
     *             by the enum map.
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException
    {
        // Write out the key type and any hidden stuff
        s.defaultWriteObject();

        // Write out size (number of Mappings)
        s.writeInt(size);

        // Write out keys and values (alternating)
        for (Map.Entry<K,V> e :  entrySet()) {
            s.writeObject(e.getKey());
            s.writeObject(e.getValue());
        }
    }

    /**
     * Reconstitute the <tt>EnumMap</tt> instance from a stream (i.e.,
     * deserialize it).
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException
    {
        // Read in the key type and any hidden stuff
        s.defaultReadObject();

        keyUniverse = getKeyUniverse(keyType);
        vals = new Object[keyUniverse.length];

        // Read in size (number of Mappings)
        int size = s.readInt();

        // Read the keys and values, and put the mappings in the HashMap
        for (int i = 0; i < size; i++) {
            K key = (K) s.readObject();
            V value = (V) s.readObject();
            put(key, value);
        }
    }
}
