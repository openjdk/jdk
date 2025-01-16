/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8285932 8310913 8336390 8338060
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class ReferencedKeyTest {
    private static String BASE_KEY = "BASEKEY-";

    // Return a String (identity object) that can be a key in WeakHashMap.
    private static String genKey(int i) {
        return BASE_KEY + i;
    }

    // Return a String of the letter 'a' plus the integer (0..0xffff)
    private static String genValue(int i) {
        return String.valueOf((char) ('a' + i));
    }

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

    static void mapTest(boolean isSoft, Supplier<Map<ReferenceKey<String>, String>> supplier) {
        Map<String, String> map = ReferencedKeyMap.create(isSoft, supplier);
        var strongKeys = populate(map);      // Retain references to the keys
        methods(map);
        Reference.reachabilityFence(strongKeys);

        strongKeys = null;      // drop strong key references
        if (!isSoft) {
            if (!collect(() -> map.isEmpty())) {
                throw new RuntimeException("WeakReference map not collecting!");
            }
        }
    }

    static void setTest(boolean isSoft, Supplier<Map<ReferenceKey<String>, ReferenceKey<String>>> supplier) {
        ReferencedKeySet<String> set = ReferencedKeySet.create(isSoft, supplier);
        var strongKeys = populate(set);      // Retain references to the keys
        methods(set);
        Reference.reachabilityFence(strongKeys);

        strongKeys = null;          // drop strong key references
        if (!isSoft) {
            if (!collect(() -> set.isEmpty())) {
                throw new RuntimeException("WeakReference set not collecting!");
            }
        }
    }

    static void methods(Map<String, String> map) {
        assertTrue(map.size() == 26, "missing key");
        assertTrue(map.containsKey(genKey('a' -'a')), "missing key");
        assertTrue(map.get(genKey('b' -'a')).equals("b"), "wrong key");
        assertTrue(map.containsValue("c"), "missing value");
        map.remove(genKey('d' -'a'));
        assertTrue(map.get(genKey('d' -'a')) == null, "not removed");
        map.putAll(Map.of(genKey(1), "A", genKey(2), "B"));
        assertTrue(map.get(genKey(2)).equals("B"), "collection not added");
        assertTrue(map.containsKey(genKey(1)), "key missing");
        assertTrue(map.containsValue("A"), "key missing");
        assertTrue(map.entrySet().contains(Map.entry(genKey(1), "A")), "key missing");
        map.putIfAbsent(genKey(3), "C");
        assertTrue(map.get(genKey(3)).equals("C"), "key missing");
        map.putIfAbsent(genKey(2), "D");
        assertTrue(map.get(genKey(2)).equals("B"), "key replaced");
        map.remove(genKey(3));
        assertTrue(map.get(genKey(3)) == null, "key not removed");
        map.replace(genKey(2), "D");
        assertTrue(map.get(genKey(2)).equals("D"), "key not replaced");
        map.replace(genKey(2), "B", "E");
        assertTrue(map.get(genKey(2)).equals("D"), "key replaced");
    }

    static void methods(ReferencedKeySet<String> set) {
        assertTrue(set.size() == 26, "missing key");
        assertTrue(set.contains(genKey(3)), "missing key");
        set.remove(genKey(3));
        assertTrue(!set.contains(genKey(3)), "not removed");
        String element1 = set.get(genKey(2));
        String element2 = set.get(genKey(3));
        String element3 = set.get(genKey(4));
        String intern1 = set.intern(genKey(2));
        String intern2 = set.intern(genKey(3));
        String intern3 = set.intern(genKey(4), e -> e);
        assertTrue(element1 != null, "missing key");
        assertTrue(element2 == null, "not removed");
        assertTrue(element1 == intern1, "intern failed"); // must be same object
        assertTrue(intern2 != null, "intern failed");
        assertTrue(element3 == intern3, "intern failed");

        String value1 = genKey(999);
        String value2 = genKey(999);
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

    static List<String> populate(Map<String, String> map) {
        var keyRefs = genStrings(0, 26, ReferencedKeyTest::genKey);
        var valueRefs = genStrings(0, 26, ReferencedKeyTest::genValue);
        for (int i = 0; i < keyRefs.size(); i++) {
            map.put(keyRefs.get(i), valueRefs.get(i));
        }
        return keyRefs;
    }

    static List<String> populate(Set<String> set) {
        var keyRefs = genStrings(0, 26, ReferencedKeyTest::genKey);
        set.addAll(keyRefs);
        return keyRefs;
    }

    // Generate a List of consecutive strings using a function int -> String
    static List<String> genStrings(int min, int maxExclusive, Function<Integer, String> genString) {
        return IntStream.range(min, maxExclusive).mapToObj(i -> genString.apply(i)).toList();
    }
}
