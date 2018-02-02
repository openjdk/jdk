/*
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
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea and Martin Buchholz with assistance from
 * members of JCP JSR-166 Expert Group and released to the public
 * domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import junit.framework.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Contains tests applicable to all Map implementations.
 */
public class MapTest extends JSR166TestCase {
    final MapImplementation impl;

    /** Tests are parameterized by a Map implementation. */
    MapTest(MapImplementation impl, String methodName) {
        super(methodName);
        this.impl = impl;
    }

    public static Test testSuite(MapImplementation impl) {
        return newTestSuite(
            parameterizedTestSuite(MapTest.class,
                                   MapImplementation.class,
                                   impl));
    }

    public void testImplSanity() {
        final ThreadLocalRandom rnd = ThreadLocalRandom.current();
        {
            Map m = impl.emptyMap();
            assertTrue(m.isEmpty());
            assertEquals(0, m.size());
            Object k = impl.makeKey(rnd.nextInt());
            Object v = impl.makeValue(rnd.nextInt());
            m.put(k, v);
            assertFalse(m.isEmpty());
            assertEquals(1, m.size());
            assertTrue(m.containsKey(k));
            assertTrue(m.containsValue(v));
        }
        {
            Map m = impl.emptyMap();
            Object v = impl.makeValue(rnd.nextInt());
            if (impl.permitsNullKeys()) {
                m.put(null, v);
                assertTrue(m.containsKey(null));
                assertTrue(m.containsValue(v));
            } else {
                assertThrows(NullPointerException.class, () -> m.put(null, v));
            }
        }
        {
            Map m = impl.emptyMap();
            Object k = impl.makeKey(rnd.nextInt());
            if (impl.permitsNullValues()) {
                m.put(k, null);
                assertTrue(m.containsKey(k));
                assertTrue(m.containsValue(null));
            } else {
                assertThrows(NullPointerException.class, () -> m.put(k, null));
            }
        }
        {
            Map m = impl.emptyMap();
            Object k = impl.makeKey(rnd.nextInt());
            Object v1 = impl.makeValue(rnd.nextInt());
            Object v2 = impl.makeValue(rnd.nextInt());
            m.put(k, v1);
            if (impl.supportsSetValue()) {
                ((Map.Entry)(m.entrySet().iterator().next())).setValue(v2);
                assertSame(v2, m.get(k));
                assertTrue(m.containsKey(k));
                assertTrue(m.containsValue(v2));
                assertFalse(m.containsValue(v1));
            } else {
                assertThrows(UnsupportedOperationException.class,
                             () -> ((Map.Entry)(m.entrySet().iterator().next())).setValue(v2));
            }
        }
    }

    /**
     * Tests and extends the scenario reported in
     * https://bugs.openjdk.java.net/browse/JDK-8186171
     * HashMap: Entry.setValue may not work after Iterator.remove() called for previous entries
     * ant -Djsr166.tckTestClass=HashMapTest -Djsr166.methodFilter=testBug8186171 -Djsr166.runsPerTest=1000 tck
     */
    public void testBug8186171() {
        if (!impl.supportsSetValue()) return;
        final ThreadLocalRandom rnd = ThreadLocalRandom.current();
        final boolean permitsNullValues = impl.permitsNullValues();
        final Object v1 = (permitsNullValues && rnd.nextBoolean())
            ? null : impl.makeValue(1);
        final Object v2 = (permitsNullValues && rnd.nextBoolean() && v1 != null)
            ? null : impl.makeValue(2);

        // If true, always lands in first bucket in hash tables.
        final boolean poorHash = rnd.nextBoolean();
        class Key implements Comparable<Key> {
            final int i;
            Key(int i) { this.i = i; }
            public int hashCode() { return poorHash ? 0 : super.hashCode(); }
            public int compareTo(Key x) {
                return Integer.compare(this.i, x.i);
            }
        }

        // Both HashMap and ConcurrentHashMap have:
        // TREEIFY_THRESHOLD = 8; UNTREEIFY_THRESHOLD = 6;
        final int size = rnd.nextInt(1, 25);

        List<Key> keys = new ArrayList<>();
        for (int i = size; i-->0; ) keys.add(new Key(i));
        Key keyToFrob = keys.get(rnd.nextInt(keys.size()));

        Map<Key, Object> m = impl.emptyMap();
        for (Key key : keys) m.put(key, v1);

        for (Iterator<Map.Entry<Key, Object>> it = m.entrySet().iterator();
             it.hasNext(); ) {
            Map.Entry<Key, Object> entry = it.next();
            if (entry.getKey() == keyToFrob)
                entry.setValue(v2); // does this have the expected effect?
            else
                it.remove();
        }

        assertFalse(m.containsValue(v1));
        assertTrue(m.containsValue(v2));
        assertTrue(m.containsKey(keyToFrob));
        assertEquals(1, m.size());
    }

//     public void testFailsIntentionallyForDebugging() {
//         fail(impl.klazz().getSimpleName());
//     }
}
