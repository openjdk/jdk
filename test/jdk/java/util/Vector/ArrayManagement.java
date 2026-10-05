/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2016 Google, Inc.  All Rights Reserved.
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

/*
 * @test
 * @bug 8148174
 * @summary brittle white box test of internal array management
 * @run junit ArrayManagement
 */

import java.lang.reflect.Field;
import java.util.AbstractList;
import java.util.Vector;
import java.util.Collections;
import java.util.List;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class ArrayManagement {

    /**
     * A Vector that exposes all protected elements, and checks class
     * invariants.
     */
    static class PublicVector<E> extends Vector<E> {
        public PublicVector() { super(); }
        public PublicVector(int capacity) { super(capacity); }
        public PublicVector(int capacity, int capacityIncrement) {
            super(capacity, capacityIncrement);
        }
        public Object[] elementData()  { return elementData; }
        public int modCount()          { return modCount; }
        public int capacityIncrement() { return capacityIncrement; }
        public int capacity()          { return elementData.length; }

        public void ensureCapacity(int minCapacity) {
            int oldCapacity = capacity();
            int oldModCount = modCount();
            super.ensureCapacity(minCapacity);
            assertTrue(capacity() >= minCapacity);
            if (minCapacity <= oldCapacity)
                assertEquals(oldCapacity, capacity());
            if (minCapacity > 0)
                assertEquals(oldModCount + 1, modCount());
        }
    }

    static final int DEFAULT_CAPACITY = 10;
    static final SplittableRandom rnd = new SplittableRandom();

    static int newCapacity(int oldCapacity) {
        return 2 * oldCapacity;
    }

    static List<Object> singletonList() {
        return Collections.singletonList(Boolean.TRUE);
    }

    /** Opportunistically randomly test various add operations. */
    static void addOneElement(PublicVector<Object> list) {
        int size = list.size();
        int modCount = list.modCount();
        switch (rnd.nextInt(4)) {
        case 0: assertTrue(list.add(Boolean.TRUE)); break;
        case 1: list.add(size, Boolean.TRUE); break;
        case 2: assertTrue(list.addAll(singletonList())); break;
        case 3: assertTrue(list.addAll(size, singletonList())); break;
        default: throw new AssertionError();
        }
        assertEquals(modCount + 1, list.modCount());
        assertEquals(size + 1, list.size());
    }

    @Test public void defaultCapacity() {
        PublicVector<Object> list = new PublicVector<>();
        assertEquals(DEFAULT_CAPACITY, new PublicVector<Object>().capacity());
        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            addOneElement(list);
            assertEquals(DEFAULT_CAPACITY, list.capacity());
        }
        addOneElement(list);
        assertEquals(newCapacity(DEFAULT_CAPACITY), list.capacity());
    }

    @Test public void defaultCapacityEnsureCapacity() {
        PublicVector<Object> list = new PublicVector<>();
        for (int i = 0; i <= DEFAULT_CAPACITY; i++) {
            list.ensureCapacity(i);     // no-op!
            assertEquals(DEFAULT_CAPACITY, list.capacity());
        }
        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            addOneElement(list);
            assertEquals(DEFAULT_CAPACITY, list.capacity());
        }
        addOneElement(list);
        assertEquals(newCapacity(DEFAULT_CAPACITY), list.capacity());
        {
            int capacity = list.capacity();
            list.ensureCapacity(capacity + 1);
            assertEquals(newCapacity(capacity), list.capacity());
        }
        {
            int capacity = list.capacity();
            list.ensureCapacity(3 * capacity);
            assertEquals(3 * capacity, list.capacity());
        }
    }

    @Test public void ensureCapacityBeyondDefaultCapacity() {
        PublicVector<Object> list = new PublicVector<>();
        list.ensureCapacity(DEFAULT_CAPACITY + 1);
        assertEquals(newCapacity(DEFAULT_CAPACITY), list.capacity());
    }

    @Test public void explicitZeroCapacity() {
        PublicVector<Object> list = new PublicVector<>(0);
        assertEquals(0, list.capacity());
        addOneElement(list);
        assertEquals(1, list.capacity());
        addOneElement(list);
        assertEquals(2, list.capacity());
        addOneElement(list);
        assertEquals(4, list.capacity());
        addOneElement(list);
        assertEquals(4, list.capacity());
        addOneElement(list);
        assertEquals(8, list.capacity());
        addOneElement(list);
        assertEquals(8, list.capacity());
        addOneElement(list);
        assertEquals(8, list.capacity());
        list.clear();
        assertEquals(8, list.capacity());
    }

    @Test public void explicitZeroCapacityWithCapacityIncrement() {
        PublicVector<Object> list = new PublicVector<>(0, 2);
        assertEquals(0, list.capacity());
        addOneElement(list);
        assertEquals(2, list.capacity());
        addOneElement(list);
        assertEquals(2, list.capacity());
        addOneElement(list);
        assertEquals(4, list.capacity());
        addOneElement(list);
        assertEquals(4, list.capacity());
        addOneElement(list);
        assertEquals(6, list.capacity());
        addOneElement(list);
        assertEquals(6, list.capacity());
        addOneElement(list);
        assertEquals(8, list.capacity());
        list.clear();
        assertEquals(8, list.capacity());
    }

    @Test public void explicitLargeCapacity() {
        int n = DEFAULT_CAPACITY * 3;
        PublicVector<Object> list = new PublicVector<>(n);
        assertEquals(n, list.capacity());
        list.ensureCapacity(0);
        list.ensureCapacity(n);
        for (int i = 0; i < n; i++) addOneElement(list);
        assertEquals(n, list.capacity());

        addOneElement(list);
        assertEquals(newCapacity(n), list.capacity());
    }

    @Test public void explicitLargeCapacityWithCapacityIncrement() {
        int n = DEFAULT_CAPACITY * 3;
        PublicVector<Object> list = new PublicVector<>(n, 2);
        assertEquals(n, list.capacity());
        list.ensureCapacity(0);
        list.ensureCapacity(n);
        for (int i = 0; i < n; i++) addOneElement(list);
        assertEquals(n, list.capacity());

        addOneElement(list);
        assertEquals(n + 2, list.capacity());
    }

    @Test public void emptyArraysAreNotShared() {
        assertNotSame(new PublicVector<Object>(0).elementData(),
                      new PublicVector<Object>(0).elementData());
    }

    @Test public void negativeCapacity() {
        for (int capacity : new int[] { -1, Integer.MIN_VALUE }) {
            try {
                new Vector<Object>(capacity);
                fail("should throw");
            } catch (IllegalArgumentException success) {}
        }
    }
}
