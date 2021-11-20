/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * @test
 * @bug 8003417
 * @summary Verify that the Iterator returned by WeakHashMap removes the correct entry
 * when remove() is called on it
 * @run testng IteratorRemovalTest
 */
public class IteratorRemovalTest {

    /**
     * Creates a WeakHashMap and adds some entries to it. One of those entry has a null key.
     * Then uses the iterator returned by WeakHashMap.entrySet().iterator() to remove the
     * entry corresponding to the null key. The test then verifies that the correct entry
     * was removed and the rest of the entries continue to exist.
     */
    @Test
    public void testNullKeyRemoval() {
        Map<Integer, Integer> map = new WeakHashMap<>();
        map.put(null, 0);
        map.put(1, 1);
        map.put(2, 2);
        var iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey() == null) {
                // remove the entry
                iterator.remove();
                // verify null key no longer exists in the map
                Assert.assertFalse(map.containsKey(null), "null key unexpectedly exists in the map " + map);
                Assert.assertNull(map.get(null), "null key unexpectedly returned a value from map " + map);
                // removal of non-existent entry must return null
                Assert.assertNull(map.remove(null), "unexpected value present for null key from map " + map);
                // verify the rest of the keys are present as expected
                Assert.assertEquals((Object) map.get(1), 1);
                Assert.assertEquals((Object) map.get(2), 2);
                return;
            }
        }
        Assert.fail("null key wasn't found while iterating over the WeakHashMap");
    }

    /**
     * Creates a WeakHashMap and adds some entries to it. One of those entry has a null key.
     * Then uses the iterator returned by WeakHashMap.entrySet().iterator() to remove an
     * entry whose key isn't null. The test then verifies that the correct entry
     * was removed and the rest of the entries continue to exist.
     */
    @Test
    public void testNonNullKeyRemoval() {
        Map<Integer, Integer> map = new WeakHashMap<>();
        map.put(null, 0);
        map.put(1, 1);
        map.put(2, 2);
        var iterator = map.entrySet().iterator();
        Integer keyToRemove = 1;
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (keyToRemove.equals(entry.getKey())) {
                // remove the entry
                iterator.remove();
                // verify null key no longer exists in the map
                Assert.assertFalse(map.containsKey(keyToRemove), "key " + keyToRemove
                        + " unexpectedly exists in the map " + map);
                Assert.assertNull(map.get(keyToRemove), "key " + keyToRemove
                        + " unexpectedly returned a value from the map " + map);
                // removal of non-existent entry must return null
                Assert.assertNull(map.remove(keyToRemove),
                        "unexpected value present for key " + keyToRemove + " in the map " + map);
                // verify the rest of the keys are present as expected
                Assert.assertEquals((Object) map.get(null), 0);
                Assert.assertEquals((Object) map.get(2), 2);
                return;
            }
        }
        Assert.fail("key " + keyToRemove + " wasn't found while iterating over the WeakHashMap");
    }

    /**
     * Creates multiple WeakHashMaps and adds some entries to it. Some of these entries have
     * a null key. The test then uses the iterator returned by WeakHashMap.entrySet().iterator()
     * to traverse till the end of the iterator and invokes the remove() method on the iterator
     * to remove the last returned entry. The test then verifies that the correct entry
     * was removed and the rest of the entries continue to exist.
     */
    @Test
    public void testLastEntryRemoval() {
        Map<Integer, Integer> mapWithoutNullKey = new WeakHashMap<>();
        mapWithoutNullKey.put(4, 4);
        mapWithoutNullKey.put(5, 5);
        mapWithoutNullKey.put(6, 6);

        Map<Integer, Integer> mapWithNullKey = new WeakHashMap<>();
        mapWithNullKey.put(null, 0);
        mapWithNullKey.put(1, 1);
        mapWithNullKey.put(2, 2);

        for (var map : Set.of(mapWithoutNullKey, mapWithNullKey)) {
            Set<Integer> keysBeforeRemoval = new HashSet<>(map.keySet());
            var iterator = map.entrySet().iterator();
            var lastEntry = iterateTillEnd(iterator);
            var expectedKeyToBeRemoved = lastEntry.getKey();
            // remove the last entry
            iterator.remove();
            // verify the correct entry was removed
            Assert.assertFalse(map.containsKey(expectedKeyToBeRemoved), "key " + expectedKeyToBeRemoved
                    + " unexpectedly exists in the map " + map);
            Assert.assertNull(map.get(expectedKeyToBeRemoved), "key " + expectedKeyToBeRemoved
                    + " unexpectedly returned a value from the map " + map);
            // removal of non-existent entry must return null
            Assert.assertNull(map.remove(expectedKeyToBeRemoved),
                    "unexpected value present for key " + expectedKeyToBeRemoved + " from the map " + map);
            // verify rest of the key/values continue to exist
            keysBeforeRemoval.remove(expectedKeyToBeRemoved);
            for (var expectedKey : keysBeforeRemoval) {
                var expectedValue = expectedKey == null ? 0 : expectedKey;
                Assert.assertEquals((Object) map.get(expectedKey), expectedValue,
                        "unexpected value for key " + expectedKey + " in the map " + map);
            }
        }
    }

    // iterates till the last entry and returns back the last entry
    private static <K, V> Map.Entry<K, V> iterateTillEnd(Iterator<Map.Entry<K, V>> it) {
        Map.Entry<K, V> entry = null;
        while (it.hasNext()) {
            entry = it.next();
        }
        return entry;
    }
}
