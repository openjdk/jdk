/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
 * A Map that has a well-defined iteration order and that is reversible.
 *
 * <p>Depending upon the underlying implementation, the Map.Entry instances
 * returned by methods in this interface might or might not be connected
 * to actual map entries. In particular, it is not specified by this interface
 * whether the {@code setValue} method of an entry instance will update a
 * mapping in the underlying map, or whether changes to the underlying map
 * are visible in a returned Map.Entry instance.
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 * @since XXX
 */
public interface SequencedMap<K, V> extends Map<K, V> {
    /**
     * Returns a reverse-ordered view of this map. If the implementation
     * permits modifications to this view, the modifications "write through"
     * to the underlying map. Depending upon the implementation's
     * concurrent modification policy, changes to the underlying map
     * may be visible in this reversed view.
     * @return a reverse-ordered view of this map
     */
    SequencedMap<K, V> reversed();

    /**
     * Returns the first key currently in this map.
     *
     * @implSpec Obtains an iterator of the keySet of
     * this Map and returns the result of calling its
     * {@code next} method.
     *
     * @return the first key currently in this map
     * @throws NoSuchElementException if this map is empty
     */
    default K firstKey() {
        return keySet().iterator().next();
    }

    /**
     * Returns the last key currently in this map.
     *
     * @implSpec Obtains an iterator of the keySet of
     * a reversed view of this Map and returns the result of calling its
     * {@code next} method.
     *
     * @return the last key currently in this map
     * @throws NoSuchElementException if this map is empty
     */
    default K lastKey() {
        return reversed().keySet().iterator().next();
    }

    /**
     * Returns the first key-value mapping in this map,
     * or {@code null} if the map is empty.
     *
     * @implSpec XXX
     *
     * @return the first key-value mapping,
     *         or {@code null} if this map is empty
     */
    default Map.Entry<K,V> firstEntry() {
        var it = entrySet().iterator();
        return it.hasNext() ? it.next() : null;
    }

    /**
     * Returns the last key-value mapping in this map,
     * or {@code null} if the map is empty.
     *
     * @implSpec XXX
     *
     * @return the last key-value mapping,
     *         or {@code null} if this map is empty
     */
    default Map.Entry<K,V> lastEntry() {
        var it = reversed().entrySet().iterator();
        return it.hasNext() ? it.next() : null;
    }

    /**
     * Removes and returns the first key-value mapping in this map,
     * or {@code null} if the map is empty (optional operation).
     *
     * @implSpec XXX
     *
     * @return the removed first entry of this map,
     *         or {@code null} if this map is empty
     */
    default Map.Entry<K,V> pollFirstEntry() {
        var it = entrySet().iterator();
        if (it.hasNext()) {
            var entry = it.next();
            it.remove();
            return entry;
        } else {
            return null;
        }
    }

    /**
     * Removes and returns the last key-value mapping in this map,
     * or {@code null} if the map is empty (optional operation).
     *
     * @implSpec XXX
     *
     * @return the removed last entry of this map,
     *         or {@code null} if this map is empty
     */
    default Map.Entry<K,V> pollLastEntry() {
        var it = reversed().entrySet().iterator();
        if (it.hasNext()) {
            var entry = it.next();
            it.remove();
            return entry;
        } else {
            return null;
        }
    }

    /**
     * Inserts this mapping into the map if not already present (optional operation).
     * Moves the mapping to be the first in iteration order.
     *
     * @implSpec The implementation of this method in this class always throws
     * UnsupportedOperationException.
     *
     * @param k the key
     * @param v the value
     * @return the value previously associated with k, or null if none
     */
    default V putFirst(K k, V v) {
        throw new UnsupportedOperationException();
    }

    /**
     * Inserts this mapping into the map if not already present (optional operation).
     * Moves the mapping to be the last in iteration order.
     *
     * @implSpec The implementation of this method in this class always throws
     * UnsupportedOperationException.
     *
     * @param k the key
     * @param v the value
     * @return the value previously associated with k, or null if none
     */
    default V putLast(K k, V v) {
        throw new UnsupportedOperationException();
    }
}
