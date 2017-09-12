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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.Test;

public class ArrayListTest extends JSR166TestCase {
    public static void main(String[] args) {
        main(suite(), args);
    }

    public static Test suite() {
        class Implementation implements CollectionImplementation {
            public Class<?> klazz() { return ArrayList.class; }
            public List emptyCollection() { return new ArrayList(); }
            public Object makeElement(int i) { return i; }
            public boolean isConcurrent() { return false; }
            public boolean permitsNulls() { return true; }
        }
        class SubListImplementation extends Implementation {
            public List emptyCollection() {
                return super.emptyCollection().subList(0, 0);
            }
        }
        return newTestSuite(
                // ArrayListTest.class,
                CollectionTest.testSuite(new Implementation()),
                CollectionTest.testSuite(new SubListImplementation()));
    }

    /**
     * A cloned list equals original
     */
    public void testClone() throws Exception {
        ArrayList<Integer> x = new ArrayList<>();
        x.add(1);
        x.add(2);
        x.add(3);
        ArrayList<Integer> y = (ArrayList<Integer>) x.clone();

        assertNotSame(y, x);
        assertEquals(x, y);
        assertEquals(y, x);
        assertEquals(x.size(), y.size());
        assertEquals(x.toString(), y.toString());
        assertTrue(Arrays.equals(x.toArray(), y.toArray()));
        while (!x.isEmpty()) {
            assertFalse(y.isEmpty());
            assertEquals(x.remove(0), y.remove(0));
        }
        assertTrue(y.isEmpty());
    }

}
