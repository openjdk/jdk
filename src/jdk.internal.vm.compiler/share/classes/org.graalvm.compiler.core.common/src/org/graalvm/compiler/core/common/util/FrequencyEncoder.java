/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


package org.graalvm.compiler.core.common.util;

import java.util.ArrayList;
import java.util.List;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.Equivalence;

/**
 * Creates an array of T objects order by the occurrence frequency of each object. The most
 * frequently used object is the first one, the least frequently used the last one. If {@code null}
 * is added, it is always the first element.
 *
 * Either object {@link #createIdentityEncoder() identity} or object {@link #createEqualityEncoder()
 * equality} can be used to build the array and count the frequency.
 */
public class FrequencyEncoder<T> {

    static class Entry<T> {
        protected final T object;
        protected int frequency;
        protected int index;

        protected Entry(T object) {
            this.object = object;
            this.index = -1;
        }
    }

    protected final EconomicMap<T, Entry<T>> map;
    protected boolean containsNull;

    /**
     * Creates an encoder that uses object identity.
     */
    public static <T> FrequencyEncoder<T> createIdentityEncoder() {
        return new FrequencyEncoder<>(EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE));
    }

    /**
     * Creates an encoder that uses {@link Object#equals(Object) object equality}.
     */
    public static <T> FrequencyEncoder<T> createEqualityEncoder() {
        return new FrequencyEncoder<>(EconomicMap.create(Equivalence.DEFAULT));
    }

    protected FrequencyEncoder(EconomicMap<T, Entry<T>> map) {
        this.map = map;
    }

    /**
     * Adds an object to the array.
     */
    public void addObject(T object) {
        if (object == null) {
            containsNull = true;
            return;
        }

        Entry<T> entry = map.get(object);
        if (entry == null) {
            entry = new Entry<>(object);
            map.put(object, entry);
        }
        entry.frequency++;
    }

    /**
     * Returns the index of an object in the array. The object must have been
     * {@link #addObject(Object) added} before.
     */
    public int getIndex(T object) {
        if (object == null) {
            assert containsNull;
            return 0;
        }
        Entry<T> entry = map.get(object);
        assert entry != null && entry.index >= 0;
        return entry.index;
    }

    /**
     * Returns the number of distinct objects that have been added, i.e., the length of the array.
     */
    public int getLength() {
        return map.size() + (containsNull ? 1 : 0);
    }

    /**
     * Fills the provided array with the added objects. The array must have the {@link #getLength()
     * correct length}.
     */
    public T[] encodeAll(T[] allObjects) {
        assert allObjects.length == getLength();
        List<Entry<T>> sortedEntries = new ArrayList<>(allObjects.length);
        for (Entry<T> value : map.getValues()) {
            sortedEntries.add(value);
        }
        sortedEntries.sort((e1, e2) -> -Integer.compare(e1.frequency, e2.frequency));

        int offset = 0;
        if (containsNull) {
            allObjects[0] = null;
            offset = 1;
        }
        for (int i = 0; i < sortedEntries.size(); i++) {
            Entry<T> entry = sortedEntries.get(i);
            int index = i + offset;
            entry.index = index;
            allObjects[index] = entry.object;
            assert entry.object != null;
        }
        return allObjects;
    }
}
