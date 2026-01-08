/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.util;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.access.SharedSecrets;
import jdk.internal.misc.CDS;

/**
 * This class provides management of {@link Map maps} where it is desirable to
 * remove entries automatically when the key is garbage collected. This is
 * accomplished by using a backing map where the keys are either a
 * {@link WeakReference} or a {@link SoftReference}.
 * <p>
 * To create a {@link ReferencedKeyMap} the user must provide a {@link Supplier}
 * of the backing map and whether {@link WeakReference} or
 * {@link SoftReference} is to be used.
 *
 * {@snippet :
 * // Use HashMap and WeakReference
 * Map<Long, String> map = ReferencedKeyMap.create(false, HashMap::new);
 * map.put(10_000_000L, "a");
 * map.put(10_000_001L, "b");
 * map.put(10_000_002L, "c");
 * map.put(10_000_003L, "d");
 * map.put(10_000_004L, "e");
 *
 * // Use ConcurrentHashMap and SoftReference
 * map = ReferencedKeyMap.create(true, ConcurrentHashMap::new);
 * map.put(20_000_000L, "v");
 * map.put(20_000_001L, "w");
 * map.put(20_000_002L, "x");
 * map.put(20_000_003L, "y");
 * map.put(20_000_004L, "z");
 * }
 *
 * @implNote Care must be given that the backing map does replacement by
 * replacing the value in the map entry instead of deleting the old entry and
 * adding a new entry, otherwise replaced entries may end up with a strongly
 * referenced key. {@link HashMap} and {@link ConcurrentHashMap} are known
 * to be safe.
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 *
 * @since 21
 */
public final class ReferencedKeyMap<K, V> implements Map<K, V> {
    /**
     * true if {@link SoftReference} keys are to be used,
     * {@link WeakReference} otherwise.
     */
    private final boolean isSoft;

    /**
     * Backing {@link Map}.
     */
    private final Map<ReferenceKey<K>, V> map;

    /**
     * {@link ReferenceQueue} for cleaning up entries.
     */
    private final ReferenceQueue<K> stale;

    /**
     * @return a supplier to create a {@code ConcurrentHashMap} appropriate for use in the
     *         create methods.
     * @param <K> the type of keys maintained by the new map
     * @param <V> the type of mapped values
     */
    public static <K, V> Supplier<Map<ReferenceKey<K>, V>> concurrentHashMapSupplier() {
        return new Supplier<>() {
            @Override
            public Map<ReferenceKey<K>, V> get() {
                return new ConcurrentHashMap<>();
            }
        };
    }

    /**
     * Private constructor.
     *
     * @param isSoft          true if {@link SoftReference} keys are to
     *                        be used, {@link WeakReference} otherwise.
     * @param map             backing map
     * @param stale           {@link ReferenceQueue} for cleaning up entries
     */
    private ReferencedKeyMap(boolean isSoft, Map<ReferenceKey<K>, V> map, ReferenceQueue<K> stale) {
        this.isSoft = isSoft;
        this.map = map;
        this.stale = stale;
    }

    /**
     * Create a new {@link ReferencedKeyMap} map.
     *
     * @param isSoft          true if {@link SoftReference} keys are to
     *                        be used, {@link WeakReference} otherwise.
     * @param supplier        {@link Supplier} of the backing map
     *
     * @return a new map with {@link Reference} keys
     *
     * @param <K> the type of keys maintained by the new map
     * @param <V> the type of mapped values
     */
    public static <K, V> ReferencedKeyMap<K, V>
    create(boolean isSoft, Supplier<Map<ReferenceKey<K>, V>> supplier) {
        return new ReferencedKeyMap<K, V>(isSoft, supplier.get(), new ReferenceQueue<>());
    }

    /**
     * {@return a key suitable for a map entry}
     *
     * @param key unwrapped key
     */
    @SuppressWarnings("unchecked")
    private ReferenceKey<K> entryKey(Object key) {
        if (isSoft) {
            return new SoftReferenceKey<>((K)key, stale);
        } else {
            return new WeakReferenceKey<>((K)key, stale);
        }
    }

    /**
     * {@return a key suitable for lookup}
     *
     * @param key unwrapped key
     */
    @SuppressWarnings("unchecked")
    private ReferenceKey<K> lookupKey(Object key) {
        return new StrongReferenceKey<>((K)key);
    }

    @Override
    public int size() {
        removeStaleReferences();
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        removeStaleReferences();
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        Objects.requireNonNull(key, "key must not be null");
        removeStaleReferences();
        return map.containsKey(lookupKey(key));
    }

    @Override
    public boolean containsValue(Object value) {
        Objects.requireNonNull(value, "value must not be null");
        removeStaleReferences();
        return map.containsValue(value);
    }

    @Override
    public V get(Object key) {
        removeStaleReferences();
        return getNoCheckStale(key);
    }

    // Internal get(key) without removing stale references that would modify the keyset.
    // Use when iterating or streaming over the keys to avoid ConcurrentModificationException.
    private V getNoCheckStale(Object key) {
        Objects.requireNonNull(key, "key must not be null");
        return map.get(lookupKey(key));
    }

    @Override
    public V put(K key, V newValue) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(newValue, "value must not be null");
        removeStaleReferences();
        ReferenceKey<K> entryKey = entryKey(key);
        // If {@code put} returns non-null then was actually a {@code replace}
        // and older key was used. In that case the new key was not used and the
        // reference marked stale.
        V oldValue = map.put(entryKey, newValue);
        if (oldValue != null) {
            entryKey.unused();
        }
        return oldValue;
    }

    @Override
    public V remove(Object key) {
        // Rely on gc to clean up old key.
        return map.remove(lookupKey(key));
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        removeStaleReferences();
        for (Entry<? extends K, ? extends V> entry : m.entrySet()) {
            K key = entry.getKey();
            V value = entry.getValue();
            put(key, value);
        }
    }

    @Override
    public void clear() {
        removeStaleReferences();
        // Rely on gc to clean up old keys.
        map.clear();
    }

    /**
     * Common routine for collecting the current set of keys.
     *
     * @return {@link Stream} of valid keys (unwrapped)
     */
    private Stream<K> filterKeySet() {
        return map.keySet()
                .stream()
                .map(ReferenceKey::get)
                .filter(Objects::nonNull);
    }

    @Override
    public Set<K> keySet() {
        removeStaleReferences();
        return filterKeySet().collect(Collectors.toSet());
    }

    @Override
    public Collection<V> values() {
        removeStaleReferences();
        return map.values();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        removeStaleReferences();
        return filterKeySet()
                .map(k -> new AbstractMap.SimpleEntry<>(k, getNoCheckStale(k)))
                .collect(Collectors.toSet());
    }

    @Override
    public V putIfAbsent(K key, V newValue) {
        removeStaleReferences();
        ReferenceKey<K> entryKey = entryKey(key);
        // If {@code putIfAbsent} returns non-null then was actually a
        // {@code replace}  and older key was used. In that case the new key was
        // not used and the reference marked stale.
        V oldValue = map.putIfAbsent(entryKey, newValue);
        if (oldValue != null) {
            entryKey.unused();
        }
        return oldValue;
    }

    @Override
    public boolean remove(Object key, Object value) {
        // Rely on gc to clean up old key.
        return map.remove(lookupKey(key), value);
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        removeStaleReferences();
        // If replace is successful then the older key will be used and the
        // lookup key will suffice.
        return map.replace(lookupKey(key), oldValue, newValue);
    }

    @Override
    public V replace(K key, V value) {
        removeStaleReferences();
        // If replace is successful then the older key will be used and the
        // lookup key will suffice.
        return map.replace(lookupKey(key), value);
    }

    @Override
    public String toString() {
        removeStaleReferences();
        return filterKeySet()
                .map(k -> k + "=" + getNoCheckStale(k))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    /**
     * Removes enqueued weak references from map.
     */
    public void removeStaleReferences() {
        while (true) {
            Object key = stale.poll();
            if (key == null) {
                break;
            }
            map.remove(key);
        }
    }

    @SuppressWarnings("unchecked")
    public void prepareForAOTCache() {
        // We are running the AOT assembly phase. The JVM has a single Java thread, so
        // we don't have any concurrent threads that may modify the map while we are
        // iterating its keys.
        //
        // Also, the java.lang.ref.Reference$ReferenceHandler thread is not running,
        // so even if the GC has put some of the keys on the pending ReferencePendingList,
        // none of the keys would have been added to the stale queue yet.
        assert CDS.isSingleThreadVM();

        for (ReferenceKey<K> key : map.keySet()) {
            Object referent = key.get();
            if (referent == null) {
                // We don't need this key anymore. Add to stale queue
                ((Reference)key).enqueue();
            } else {
                // Make sure the referent cannot be collected. Otherwise, when
                // the referent is collected, the GC may push the key onto
                // Universe::reference_pending_list() at an unpredictable time,
                // making it difficult to correctly serialize the key's
                // state into the CDS archive.
                //
                // See aotReferenceObjSupport.cpp for more info.
                CDS.keepAlive(referent);
            }
            Reference.reachabilityFence(referent);
        }

        // Remove all keys enqueued above
        removeStaleReferences();
    }


    /**
     * Puts an entry where the key and the value are the same. Used for
     * interning values in a set.
     *
     * @implNote Requires a {@link ReferencedKeyMap} whose {@code V} type
     * is a {@code ReferenceKey<K>}. Otherwise, a {@link ClassCastException} will
     * be thrown.
     *
     * @param setMap    {@link ReferencedKeyMap} where interning takes place
     * @param key       key to add
     *
     * @param <T> type of key
     *
     * @return the old key instance if found otherwise the new key instance
     *
     * @throws ClassCastException if {@code V} is not {@code EntryKey<T>}
     */
    static <T> T intern(ReferencedKeyMap<T, ReferenceKey<T>> setMap, T key) {
        T value = existingKey(setMap, key);
        if (value != null) {
            return value;
        }
        return internKey(setMap, key);
    }

    /**
     * Puts an entry where the key and the value are the same. Used for
     * interning values in a set.
     *
     * @implNote Requires a {@link ReferencedKeyMap} whose {@code V} type
     * is a {@code ReferenceKey<K>}. Otherwise, a {@link ClassCastException} will
     * be thrown.
     *
     * @param setMap    {@link ReferencedKeyMap} where interning takes place
     * @param key       key to add
     * @param interner  operation to apply to key before adding to map
     *
     * @param <T> type of key
     *
     * @return the old key instance if found otherwise the new key instance
     *
     * @throws ClassCastException if {@code V} is not {@code EntryKey<T>}
     *
     * @implNote This version of intern should not be called during phase1
     * using a lambda. Use an UnaryOperator instance instead.
     */
    static <T> T intern(ReferencedKeyMap<T, ReferenceKey<T>> setMap, T key, UnaryOperator<T> interner) {
        T value = existingKey(setMap, key);
        if (value != null) {
            return value;
        }
        key = interner.apply(key);
        return internKey(setMap, key);
    }

    /**
     * Check if the key already exists in the map.
     *
     * @param setMap    {@link ReferencedKeyMap} where interning takes place
     * @param key       key to test
     *
     * @param <T> type of key
     *
     * @return key if found otherwise null
     */
    private static <T> T existingKey(ReferencedKeyMap<T, ReferenceKey<T>> setMap, T key) {
        setMap.removeStaleReferences();
        ReferenceKey<T> entryKey = setMap.get(setMap.lookupKey(key));
        return entryKey != null ? entryKey.get() : null;
    }

    /**
     * Attempt to add key to map.
     *
     * @param setMap    {@link ReferencedKeyMap} where interning takes place
     * @param key       key to add
     *
     * @param <T> type of key
     *
     * @return the old key instance if found otherwise the new key instance
     */
    private static <T> T internKey(ReferencedKeyMap<T, ReferenceKey<T>> setMap, T key) {
        ReferenceKey<T> entryKey = setMap.entryKey(key);
        T interned;
        do {
            setMap.removeStaleReferences();
            ReferenceKey<T> existing = setMap.map.putIfAbsent(entryKey, entryKey);
            if (existing == null) {
                return key;
            } else {
                // If {@code putIfAbsent} returns non-null then was actually a
                // {@code replace} and older key was used. In that case the new
                // key was not used and the reference marked stale.
                interned = existing.get();
                if (interned != null) {
                    entryKey.unused();
                }
            }
        } while (interned == null);
        return interned;
    }


    /**
     * Attempt to add key to map if absent.
     *
     * @param setMap    {@link ReferencedKeyMap} where interning takes place
     * @param key       key to add
     *
     * @param <T> type of key
     *
     * @return true if the key was added
     */
    static <T> boolean internAddKey(ReferencedKeyMap<T, ReferenceKey<T>> setMap, T key) {
        ReferenceKey<T> entryKey = setMap.entryKey(key);
        setMap.removeStaleReferences();
        ReferenceKey<T> existing = setMap.map.putIfAbsent(entryKey, entryKey);
        if (existing == null) {
            return true;
        } else {
            // If {@code putIfAbsent} returns non-null then was actually a
            // {@code replace} and older key was used. In that case the new
            // key was not used and the reference marked stale.
            entryKey.unused();
            return false;
        }
     }

}
