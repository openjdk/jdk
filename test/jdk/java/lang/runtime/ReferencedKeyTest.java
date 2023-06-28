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
 * @summary Test features provided by the ReferencedKeyMap class.
 * @modules java.base/java.lang.runtime
 * @enablePreview
 * @compile --patch-module java.base=${test.src} ReferencedKeyTest.java
 * @run main/othervm --patch-module java.base=${test.class.path} java.lang.runtime.ReferencedKeyTest
 */

package java.lang.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class ReferencedKeyTest {
    static long BASE_KEY = 10_000_000L;

    public static void main(String[] args) throws Throwable {
        mapTest(false, HashMap::new);
        mapTest(true, HashMap::new);
        mapTest(false, ConcurrentHashMap::new);
        mapTest(true, ConcurrentHashMap::new);
    }

    static void assertTrue(boolean test, String message) {
        if (!test) {
            throw new RuntimeException(message);
        }
    }

    static void  mapTest(boolean isSoft, Supplier<Map<ReferenceKey<Long>, String>> supplier) {
        Map<Long, String> map = ReferencedKeyMap.create(isSoft, supplier);
        populate(map);
        collect();
        // assertTrue(map.isEmpty() || isSoft, "Weak not collecting");
        populate(map);
        methods(map);
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
        assertTrue(map.keySet().contains(1L), "key missing");
        assertTrue(map.values().contains("A"), "key missing");
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

    static void collect() {
        System.gc();
        sleep();
    }

    static void sleep() {
        try {
            Thread.sleep(100L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    static void populate(Map<Long, String> map) {
        for (int i = 0; i < 26; i++) {
            Long key = BASE_KEY + i;
            String value = String.valueOf((char) ('a' + i));
            map.put(key, value);
        }
    }
}
