/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.util.test;

import static org.junit.Assert.assertEquals;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.Equivalence;
import org.graalvm.compiler.test.GraalTest;
import org.graalvm.util.ObjectSizeEstimate;
import org.junit.Assume;
import org.junit.Test;

public class CollectionSizeTest {

    /**
     * Tests the memory size of an empty map and a map with only one or two entries.
     */
    @Test
    public void testSize() {
        Assume.assumeTrue("Not working in JDK9 due to module visibility.", GraalTest.Java8OrEarlier);
        EconomicMap<Object, Object> map = EconomicMap.create(Equivalence.IDENTITY);
        assertEquals(49, ObjectSizeEstimate.forObject(map).getTotalBytes());

        Integer value = 1;
        map.put(value, value);
        assertEquals(153, ObjectSizeEstimate.forObject(map).getTotalBytes());

        Integer secondValue = 2;
        map.put(secondValue, secondValue);
        assertEquals(153 + 20, ObjectSizeEstimate.forObject(map).getTotalBytes());
    }

    /**
     * Tests whether the map actually compresses the entries array when a large number of entries
     * are deleted.
     */
    @Test
    public void testCompress() {
        Assume.assumeTrue("Not working in JDK9 due to module visibility.", GraalTest.Java8OrEarlier);
        EconomicMap<Object, Object> map = EconomicMap.create();

        // Measuring size of map with one entry.
        Object firstValue = 0;
        map.put(firstValue, firstValue);
        ObjectSizeEstimate afterFirstValue = ObjectSizeEstimate.forObject(map);

        // Add 999 more entries.
        for (int i = 1; i < 1000; ++i) {
            Object value = i;
            map.put(value, value);
        }
        ObjectSizeEstimate beforeRemove = ObjectSizeEstimate.forObject(map);

        // Remove 999 first entries.
        for (int i = 0; i < 999; ++i) {
            map.removeKey(i);
        }
        ObjectSizeEstimate afterRemove = ObjectSizeEstimate.forObject(map);

        // Check that size is same size as with one entry.
        assertEquals(afterFirstValue, afterRemove);

        // Add 999 new entries.
        for (int i = 0; i < 999; ++i) {
            Object value = i;
            map.put(value, value);
        }
        ObjectSizeEstimate afterAdd = ObjectSizeEstimate.forObject(map);

        // Check that entries array is same size again.
        assertEquals(beforeRemove.getPointerCount(), afterAdd.getPointerCount());
    }

}
