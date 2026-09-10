/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.tests.java.util.stream;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.*;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/*
 * @test
 * @summary Tests laziness of stream operations
 * @bug 8336672
 */

/**
 * Tests laziness of stream operations -- mutations to the source after the stream() but prior to terminal operations
 * are reflected in the stream contents.
 */
@Test
public class CollectionAndMapModifyStreamTest {
    // Well known Identity instances; needed for IdentityHashMap
    static List<String> CONTENT = IntStream.range(0, 10).mapToObj(i -> "BASE-" + i).toList();

    @DataProvider(name = "collections")
    public Object[][] createCollections() {

        List<Collection<String>> collections = new ArrayList<>();
        collections.add(new ArrayList<>(CONTENT));
        collections.add(new LinkedList<>(CONTENT));
        collections.add(new Vector<>(CONTENT));

        collections.add(new HashSet<>(CONTENT));
        collections.add(new LinkedHashSet<>(CONTENT));
        collections.add(new TreeSet<>(CONTENT));

        Stack<String> stack = new Stack<>();
        stack.addAll(CONTENT);
        collections.add(stack);
        collections.add(new PriorityQueue<>(CONTENT));
        collections.add(new ArrayDeque<>(CONTENT));

        // Concurrent collections

        collections.add(new ConcurrentSkipListSet<>(CONTENT));

        ArrayBlockingQueue<String> arrayBlockingQueue = new ArrayBlockingQueue<>(CONTENT.size());
        for (String i : CONTENT)
            arrayBlockingQueue.add(i);
        collections.add(arrayBlockingQueue);
        collections.add(new PriorityBlockingQueue<>(CONTENT));
        collections.add(new LinkedBlockingQueue<>(CONTENT));
        collections.add(new LinkedTransferQueue<>(CONTENT));
        collections.add(new ConcurrentLinkedQueue<>(CONTENT));
        collections.add(new LinkedBlockingDeque<>(CONTENT));
        collections.add(new ConcurrentLinkedDeque<>(CONTENT));

        Object[][] params = new Object[collections.size()][];
        for (int i = 0; i < collections.size(); i++) {
            params[i] = new Object[]{collections.get(i).getClass().getName(), collections.get(i)};
        }

        return params;
    }

    @Test(dataProvider = "collections")
    public void testCollectionSizeRemove(String name, Collection<String> c) {
        assertTrue(c.remove(CONTENT.get(0)));
        Stream<String> s = c.stream();
        assertTrue(c.remove(CONTENT.get(1)));
        Object[] result = s.toArray();
        assertEquals(result.length, c.size());
    }

    @DataProvider(name = "maps")
    public Object[][] createMaps() {
        Map<String, String> content = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            var ix = CONTENT.get(i);
            content.put(ix, ix);
        }

        Map<String, Supplier<Map<String, String>>> maps = new HashMap<>();

        maps.put(HashMap.class.getName(), () -> new HashMap<>(content));
        maps.put(LinkedHashMap.class.getName(), () -> new LinkedHashMap<>(content));
        maps.put(IdentityHashMap.class.getName(), () -> new IdentityHashMap<>(content));
        maps.put(WeakHashMap.class.getName(), () -> new WeakHashMap<>(content));

        maps.put(TreeMap.class.getName(), () -> new TreeMap<>(content));
        maps.put(TreeMap.class.getName() + ".descendingMap()", () -> new TreeMap<>(content).descendingMap());

        // The following are not lazy
//        maps.put(TreeMap.class.getName() + ".descendingMap().descendingMap()", () -> new TreeMap<>(content).descendingMap().descendingMap());
//        maps.put(TreeMap.class.getName() + ".headMap()", () -> new TreeMap<>(content).headMap(content.size() - 1));
//        maps.put(TreeMap.class.getName() + ".descendingMap().headMap()", () -> new TreeMap<>(content).descendingMap().tailMap(content.size() - 1, false));

        // Concurrent collections

        maps.put(ConcurrentHashMap.class.getName(), () -> new ConcurrentHashMap<>(content));
        maps.put(ConcurrentSkipListMap.class.getName(), () -> new ConcurrentSkipListMap<>(content));

        Object[][] params = new Object[maps.size()][];
        int i = 0;
        for (Map.Entry<String, Supplier<Map<String, String>>> e : maps.entrySet()) {
            params[i++] = new Object[]{e.getKey(), e.getValue()};
        }

        return params;
    }

    @Test(dataProvider = "maps", groups = { "serialization-hostile" })
    public void testMapKeysSizeRemove(String name, Supplier<Map<String, String>> c) {
        testCollectionSizeRemove(name + " key set", c.get().keySet());
    }

    @Test(dataProvider = "maps", groups = { "serialization-hostile" })
    public void testMapValuesSizeRemove(String name, Supplier<Map<String, String>> c) {
        testCollectionSizeRemove(name + " value set", c.get().values());
    }

    @Test(dataProvider = "maps")
    public void testMapEntriesSizeRemove(String name, Supplier<Map<String, String>> c) {
        testEntrySetSizeRemove(name + " entry set", c.get().entrySet());
    }

    private void testEntrySetSizeRemove(String name, Set<Map.Entry<String, String>> c) {
        Map.Entry<String, String> first = c.iterator().next();
        assertTrue(c.remove(first));
        Stream<Map.Entry<String, String>> s = c.stream();
        Map.Entry<String, String> second = c.iterator().next();
        assertTrue(c.remove(second));
        Object[] result = s.toArray();
        assertEquals(result.length, c.size());
    }
}
