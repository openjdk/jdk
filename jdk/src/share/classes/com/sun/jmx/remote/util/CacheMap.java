/*
 * Copyright (c) 2003, 2006, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.jmx.remote.util;

import java.lang.ref.SoftReference;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.WeakHashMap;

import com.sun.jmx.mbeanserver.Util;

/**
 * <p>Like WeakHashMap, except that the keys of the <em>n</em> most
 * recently-accessed entries are kept as {@link SoftReference soft
 * references}.  Accessing an element means creating it, or retrieving
 * it with {@link #get(Object) get}.  Because these entries are kept
 * with soft references, they will tend to remain even if their keys
 * are not referenced elsewhere.  But if memory is short, they will
 * be removed.</p>
 */
public class CacheMap<K, V> extends WeakHashMap<K, V> {
    /**
     * <p>Create a <code>CacheMap</code> that can keep up to
     * <code>nSoftReferences</code> as soft references.</p>
     *
     * @param nSoftReferences Maximum number of keys to keep as soft
     * references.  Access times for {@link #get(Object) get} and
     * {@link #put(Object, Object) put} have a component that scales
     * linearly with <code>nSoftReferences</code>, so this value
     * should not be too great.
     *
     * @throws IllegalArgumentException if
     * <code>nSoftReferences</code> is negative.
     */
    public CacheMap(int nSoftReferences) {
        if (nSoftReferences < 0) {
            throw new IllegalArgumentException("nSoftReferences = " +
                                               nSoftReferences);
        }
        this.nSoftReferences = nSoftReferences;
    }

    public V put(K key, V value) {
        cache(key);
        return super.put(key, value);
    }

    public V get(Object key) {
        cache(Util.<K>cast(key));
        return super.get(key);
    }

    /* We don't override remove(Object) or try to do something with
       the map's iterators to detect removal.  So we may keep useless
       entries in the soft reference list for keys that have since
       been removed.  The assumption is that entries are added to the
       cache but never removed.  But the behavior is not wrong if
       they are in fact removed -- the caching is just less
       performant.  */

    private void cache(K key) {
        Iterator<SoftReference<K>> it = cache.iterator();
        while (it.hasNext()) {
            SoftReference<K> sref = it.next();
            K key1 = sref.get();
            if (key1 == null)
                it.remove();
            else if (key.equals(key1)) {
                // Move this element to the head of the LRU list
                it.remove();
                cache.add(0, sref);
                return;
            }
        }

        int size = cache.size();
        if (size == nSoftReferences) {
            if (size == 0)
                return;  // degenerate case, equivalent to WeakHashMap
            it.remove();
        }

        cache.add(0, new SoftReference<K>(key));
    }

    /* List of soft references for the most-recently referenced keys.
       The list is in most-recently-used order, i.e. the first element
       is the most-recently referenced key.  There are never more than
       nSoftReferences elements of this list.

       If we didn't care about J2SE 1.3 compatibility, we could use
       LinkedHashSet in conjunction with a subclass of SoftReference
       whose equals and hashCode reflect the referent.  */
    private final LinkedList<SoftReference<K>> cache =
            new LinkedList<SoftReference<K>>();
    private final int nSoftReferences;
}
