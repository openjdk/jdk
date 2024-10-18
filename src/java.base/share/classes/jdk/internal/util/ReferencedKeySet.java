/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * This class provides management of {@link Set set} where it is desirable to
 * remove elements automatically when the element is garbage collected. This is
 * accomplished by using a backing map where the keys and values are either a
 * {@link WeakReference} or a {@link SoftReference}.
 * <p>
 * To create a {@link ReferencedKeySet} the user must provide a {@link Supplier}
 * of the backing map and whether {@link WeakReference} or
 * {@link SoftReference} is to be used.
 * {@snippet :
 * Set<Long> set;
 *
 * set = ReferencedKeySet.create(false, HashMap::new);
 * set.add(30_000_000L);
 * set.add(30_000_001L);
 * set.add(30_000_002L);
 * set.add(30_000_003L);
 * set.add(30_000_004L);
 *
 * set = ReferencedKeySet.create(true, ConcurrentHashMap::new);
 * set.add(40_000_000L);
 * set.add(40_000_001L);
 * set.add(40_000_002L);
 * set.add(40_000_003L);
 * set.add(40_000_004L);
 * }
 *
 * @implNote Care must be given that the backing map does replacement by
 * replacing the value in the map entry instead of deleting the old entry and
 * adding a new entry, otherwise replaced entries may end up with a strongly
 * referenced key. {@link HashMap} and {@link ConcurrentHashMap} are known
 * to be safe.
 *
 * @param <T> the type of elements maintained by this set
 */
public final class ReferencedKeySet<T> extends AbstractSet<T> {
    /**
     * Backing {@link ReferencedKeySet} map.
     */
    final ReferencedKeyMap<T, ReferenceKey<T>> map;

    /**
     * @return a supplier to create a {@code ConcurrentHashMap} appropriate for use in the
     *         create methods.
     * @param <E> the type of elements maintained by this set
     */
    public static <E> Supplier<Map<ReferenceKey<E>, ReferenceKey<E>>> concurrentHashMapSupplier() {
        return ReferencedKeyMap.concurrentHashMapSupplier();
    }

    /**
     * Private constructor.
     *
     * @param map     backing map
     */
    private ReferencedKeySet(ReferencedKeyMap<T, ReferenceKey<T>> map) {
        this.map = map;
    }

    /**
     * Create a new {@link ReferencedKeySet} elements.
     *
     * @param isSoft          true if {@link SoftReference} elements are to
     *                        be used, {@link WeakReference} otherwise.
     * @param supplier        {@link Supplier} of the backing map
     *
     * @return a new set with {@link Reference} elements
     *
     * @param <E> the type of elements maintained by this set
     */
    public static <E> ReferencedKeySet<E>
    create(boolean isSoft, Supplier<Map<ReferenceKey<E>, ReferenceKey<E>>> supplier) {
        return create(isSoft, false, supplier);
    }

    /**
     * Create a new {@link ReferencedKeySet} elements.
     *
     * @param isSoft          true if {@link SoftReference} elements are to
     *                        be used, {@link WeakReference} otherwise.
     * @param useNativeQueue  true if uses NativeReferenceQueue
     *                        otherwise use {@link ReferenceQueue}.
     * @param supplier        {@link Supplier} of the backing map
     *
     * @return a new set with {@link Reference} elements
     *
     * @param <E> the type of elements maintained by this set
     */
    public static <E> ReferencedKeySet<E>
    create(boolean isSoft, boolean useNativeQueue, Supplier<Map<ReferenceKey<E>, ReferenceKey<E>>> supplier) {
         return new ReferencedKeySet<>(ReferencedKeyMap.create(isSoft, useNativeQueue, supplier));
    }

    /**
     * Removes enqueued weak references from set.
     */
    public void removeStaleReferences() {
        map.removeStaleReferences();
    }

    @Override
    public Iterator<T> iterator() {
        return map.keySet().iterator();
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean contains(Object o) {
        return map.containsKey((T)o);
    }

    @Override
    public boolean add(T e) {
        return ReferencedKeyMap.internAddKey(map, e);
    }

    @Override
    public boolean remove(Object o) {
        return map.remove(o) != null;
    }

    @Override
    public void clear() {
        map.clear();
    }

    /**
     * Gets an existing element from the set, returning null if not present or
     * the old element if previously added.
     *
     * @param e  element to get
     *
     * @return the old element if present, otherwise, null
     */
    public T get(T e) {
        ReferenceKey<T> key = map.get(e);

        return key == null ? null : key.get();
    }

    /**
     * Intern an element to the set, returning the element if newly added or the
     * old element if previously added.
     *
     * @param e  element to add
     *
     * @return the old element if present, otherwise, the new element
     */
    public T intern(T e) {
        return ReferencedKeyMap.intern(map, e);
    }

    /**
     * Intern an element to the set, returning the element if newly added or the
     * old element if previously added.
     *
     * @param e         element to add
     * @param interner  operation to apply to key before adding to set
     *
     * @return the old element if present, otherwise, the new element
     *
     * @implNote This version of intern should not be called during phase1
     * using a lambda. Use an UnaryOperator instance instead.
     */
    public T intern(T e, UnaryOperator<T> interner) {
        return ReferencedKeyMap.intern(map, e, interner);
    }
}
