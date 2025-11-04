/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     4715206 8371164
 * @summary Ensure that addAll method can cope with underestimate by size().
 *          Test ArrayList-to-ArrayList fast path optimization.
 * @author  Josh Bloch
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.WeakHashMap;

public class AddAll {
    public static void main(String[] args) {
        testWeakHashMapAddAll();
        testArrayListFastPath();
        testArrayListSubclassUsesSlowPath();
        testSingletonSetToArray();
        testModCountIncrement();
    }

    private static void testWeakHashMapAddAll() {
        for (int j = 0; j < 1; j++) {
            Map m = new WeakHashMap(100000);
            for (int i = 0; i < 100000; i++)
                m.put(new Object(), Boolean.TRUE);
            new ArrayList().addAll(m.keySet());
        }

        for (int j = 0; j < 1; j++) {
            Map m = new WeakHashMap(100000);
            for (int i = 0; i < 100000; i++)
                m.put(new Object(), Boolean.TRUE);
            new LinkedList().addAll(m.keySet());
        }

        for (int j = 0; j < 1; j++) {
            Map m = new WeakHashMap(100000);
            for (int i = 0; i < 100000; i++)
                m.put(new Object(), Boolean.TRUE);
            new Vector().addAll(m.keySet());
        }

        for (int j = 0; j < 1; j++) {
            Map m = new WeakHashMap(100000);
            for (int i = 0; i < 100000; i++)
                m.put(new Object(), Boolean.TRUE);
            List list = new ArrayList();
            list.add("inka"); list.add("dinka"); list.add("doo");
            list.addAll(1, m.keySet());
        }

        for (int j = 0; j < 1; j++) {
            Map m = new WeakHashMap(100000);
            for (int i = 0; i < 100000; i++)
                m.put(new Object(), Boolean.TRUE);
            List list = new LinkedList();
            list.add("inka"); list.add("dinka"); list.add("doo");
            list.addAll(1, m.keySet());
        }

        for (int j = 0; j < 1; j++) {
            Map m = new WeakHashMap(100000);
            for (int i = 0; i < 100000; i++)
                m.put(new Object(), Boolean.TRUE);
            List list = new ArrayList();
            list.add("inka"); list.add("dinka"); list.add("doo");
            list.addAll(1, m.keySet());
        }
    }

    private static void testArrayListFastPath() {
        // Test ArrayList-to-ArrayList fast path
        ArrayList<String> source = new ArrayList<>();
        source.add("a");
        source.add("b");
        source.add("c");

        ArrayList<String> dest = new ArrayList<>();
        dest.add("x");
        boolean result = dest.addAll(source);

        if (!result) {
            throw new RuntimeException("addAll should return true when adding non-empty collection");
        }
        if (dest.size() != 4) {
            throw new RuntimeException("Expected size 4, got " + dest.size());
        }
        if (!dest.get(0).equals("x") || !dest.get(1).equals("a") ||
            !dest.get(2).equals("b") || !dest.get(3).equals("c")) {
            throw new RuntimeException("Elements not added correctly, got: " + dest);
        }

        // Test empty ArrayList-to-ArrayList
        ArrayList<String> emptySource = new ArrayList<>();
        ArrayList<String> dest2 = new ArrayList<>();
        dest2.add("y");
        boolean result2 = dest2.addAll(emptySource);

        if (result2) {
            throw new RuntimeException("addAll should return false when adding empty collection");
        }
        if (dest2.size() != 1 || !dest2.get(0).equals("y")) {
            throw new RuntimeException("Destination should be unchanged when adding empty collection, got: " + dest2);
        }

        // Test ArrayList fast path with null elements
        ArrayList<String> sourceWithNull = new ArrayList<>();
        sourceWithNull.add(null);
        sourceWithNull.add("test");

        ArrayList<String> dest3 = new ArrayList<>();
        dest3.addAll(sourceWithNull);

        if (dest3.size() != 2 || dest3.get(0) != null || !dest3.get(1).equals("test")) {
            throw new RuntimeException("Fast path should preserve null elements, got: " + dest3);
        }
    }

    private static void testSingletonSetToArray() {
        // Test SingletonSet toArray() optimization
        var set = Collections.singleton("test");
        Object[] array = set.toArray();

        if (array.length != 1 || !array[0].equals("test")) {
            throw new RuntimeException("SingletonSet toArray() failed, expected [test], got: " + java.util.Arrays.toString(array));
        }

        // Test SingletonSet toArray(T[]) with exact size
        String[] stringArray = new String[1];
        String[] result = set.toArray(stringArray);

        if (result != stringArray || result.length != 1 || !result[0].equals("test")) {
            throw new RuntimeException("SingletonSet toArray(T[]) with exact size failed, expected same array with [test], got: " + java.util.Arrays.toString(result));
        }

        // Test SingletonSet toArray(T[]) with larger array
        String[] largerArray = new String[3];
        String[] result2 = set.toArray(largerArray);

        if (result2 != largerArray || !result2[0].equals("test") || result2[1] != null) {
            throw new RuntimeException("SingletonSet toArray(T[]) with larger array failed, expected [test, null, ...], got: " + java.util.Arrays.toString(result2));
        }

        // Test SingletonSet toArray(T[]) with smaller array
        String[] smallerArray = new String[0];
        String[] result3 = set.toArray(smallerArray);

        if (result3 == smallerArray || result3.length != 1 || !result3[0].equals("test")) {
            throw new RuntimeException("SingletonSet toArray(T[]) with smaller array failed, expected new array [test], got: " + java.util.Arrays.toString(result3));
        }
    }

    private static void testArrayListSubclassUsesSlowPath() {
        // ArrayList subclasses should NOT use the fast path
        class ArrayListSubclass<E> extends ArrayList<E> {}

        ArrayListSubclass<String> source = new ArrayListSubclass<>();
        source.add("test");

        ArrayList<String> dest = new ArrayList<>();
        boolean result = dest.addAll(source);

        if (!result || dest.size() != 1 || !dest.get(0).equals("test")) {
            throw new RuntimeException("ArrayList subclass addAll failed");
        }
    }

    private static void testModCountIncrement() {
        // Test that modCount is incremented by checking iterator behavior
        ArrayList<String> source = new ArrayList<>();
        source.add("test");

        ArrayList<String> dest = new ArrayList<>();
        Iterator<String> it = dest.iterator();

        dest.addAll(source); // Should increment modCount

        try {
            it.next(); // Should throw ConcurrentModificationException
            throw new RuntimeException("Expected ConcurrentModificationException");
        } catch (ConcurrentModificationException e) {
            // Expected - modCount was incremented
        }

        // Test that modCount is incremented even when adding empty ArrayList
        ArrayList<String> emptySource = new ArrayList<>();
        ArrayList<String> dest2 = new ArrayList<>();
        Iterator<String> it2 = dest2.iterator();

        dest2.addAll(emptySource); // Should increment modCount (even though returns false)

        try {
            it2.next(); // Should throw ConcurrentModificationException
            throw new RuntimeException("Expected ConcurrentModificationException for empty addAll");
        } catch (ConcurrentModificationException e) {
            // Expected - modCount was incremented even for empty collection
        }
    }
}
