/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8285932 8310913
 * @summary Test features provided by the ReferencedKeyMap/ReferencedKeySet classes.
 * @modules java.base/jdk.internal.util
 * @compile --patch-module java.base=${test.src} ReferencedKeyTest.java
 * @run main/othervm --patch-module java.base=${test.class.path} jdk.internal.util.ReferencedKeyTest
 */

package jdk.internal.util;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class ReferencedKeyTest {
    static long BASE_KEY = 10_000_000L;

    public static void main(String[] args) {
        mapTest(false, HashMap::new);
        mapTest(true, HashMap::new);
        mapTest(false, ConcurrentHashMap::new);
        mapTest(true, ConcurrentHashMap::new);

        setTest(false, HashMap::new);
        setTest(true, HashMap::new);
        setTest(false, ConcurrentHashMap::new);
        setTest(true, ConcurrentHashMap::new);
    }

    static void assertTrue(boolean test, String message) {
        if (!test) {
            throw new RuntimeException(message);
        }
    }

    static void mapTest(boolean isSoft, Supplier<Map<ReferenceKey<Long>, String>> supplier) {
        Map<Long, String> map = ReferencedKeyMap.create(isSoft, supplier);
        populate(map);
        if (!isSoft) {
            if (!collect(() -> map.isEmpty())) {
                throw new RuntimeException("WeakReference map not collecting!");
            }
        }
        populate(map);
        methods(map);
    }

    static void setTest(boolean isSoft, Supplier<Map<ReferenceKey<Long>, ReferenceKey<Long>>> supplier) {
        ReferencedKeySet<Long> set = ReferencedKeySet.create(isSoft, supplier);
        populate(set);
        if (!isSoft) {
            if (!collect(() -> set.isEmpty())) {
                throw new RuntimeException("WeakReference set not collecting!");
            }
        }
        populate(set);
        methods(set);
    }

    static void methods(Map<Long, String> map) {
        assertTrue(map.size() == 26, "missing key");
        assertTrue(map.containsKey(BASE_KEY + 'a' -'a'), "missing key");
        assertTrue(map.get(BASE_KEY + 'b' -'a').equals("b"), "wrong key");
        assertTrue(map.containsValue("c"), "missing value");
        map.remove(BASE_KEY + 'd' -'a');
        assertTrue(map.get(BASE_KEY + 'd' -'a') == null, "not removed");
        map.putAll(Map.of(1L, "A", 2L, "B"));
        assertTrue(map.get(2L).equals("B"), "collection not added");
        assertTrue(map.containsKey(1L), "key missing");
        assertTrue(map.containsValue("A"), "key missing");
        assertTrue(map.entrySet().contains(Map.entry(1L, "A")), "key missing");
        map.putIfAbsent(3L, "C");
        assertTrue(map.get(3L).equals("C"), "key missing");
        map.putIfAbsent(2L, "D");
        assertTrue(map.get(2L).equals("B"), "key replaced");
        map.remove(3L);
        assertTrue(map.get(3L) == null, "key not removed");
        map.replace(2L, "D");
        assertTrue(map.get(2L).equals("D"), "key not replaced");
        map.replace(2L, "B", "E");
        assertTrue(map.get(2L).equals("D"), "key replaced");
    }

    static void methods(ReferencedKeySet<Long> set) {
        assertTrue(set.size() == 26, "missing key");
        assertTrue(set.contains(BASE_KEY + 3), "missing key");
        set.remove(BASE_KEY + 3);
        assertTrue(!set.contains(BASE_KEY + 3), "not removed");
        Long element1 = set.get(BASE_KEY + 2);
        Long element2 = set.get(BASE_KEY + 3);
        Long element3 = set.get(BASE_KEY + 4);
        Long intern1 = set.intern(BASE_KEY + 2);
        Long intern2 = set.intern(BASE_KEY + 3);
        Long intern3 = set.intern(BASE_KEY + 4, e -> e);
        assertTrue(element1 != null, "missing key");
        assertTrue(element2 == null, "not removed");
        assertTrue(element1 == intern1, "intern failed"); // must be same object
        assertTrue(intern2 != null, "intern failed");
        assertTrue(element3 == intern3, "intern failed");

        Long value1 = Long.valueOf(BASE_KEY + 999);
        Long value2 = Long.valueOf(BASE_KEY + 999);
        assertTrue(set.add(value1), "key not added");
        assertTrue(!set.add(value1), "key added after second attempt");
        assertTrue(!set.add(value2), "key should not have been added");
    }

    // Borrowed from jdk.test.lib.util.ForceGC but couldn't use from java.base/jdk.internal.util
    static boolean collect(BooleanSupplier booleanSupplier) {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        Object obj = new Object();
        PhantomReference<Object> ref = new PhantomReference<>(obj, queue);
        obj = null;
        Reference.reachabilityFence(obj);
        Reference.reachabilityFence(ref);
        long timeout = 1000L;
        long quanta = 200L;
        long retries = timeout / quanta;

        for (; retries >= 0; retries--) {
            if (booleanSupplier.getAsBoolean()) {
                return true;
            }

            System.gc();

            try {
                queue.remove(quanta);
            } catch (InterruptedException ie) {
                // ignore, the loop will try again
            }
        }

        return booleanSupplier.getAsBoolean();
    }

    static void populate(Map<Long, String> map) {
        for (int i = 0; i < 26; i++) {
            Long key = BASE_KEY + i;
            String value = String.valueOf((char) ('a' + i));
            map.put(key, value);
        }
    }

    static void populate(Set<Long> set) {
        for (int i = 0; i < 26; i++) {
            Long value = BASE_KEY + i;
            set.add(value);
        }
    }
}
