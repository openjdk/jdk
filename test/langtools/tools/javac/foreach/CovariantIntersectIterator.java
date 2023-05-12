/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8295020
 * @summary javac emits incorrect code for for-each on an intersection type.
 * @run main CovariantIntersectIterator
 */

import java.io.Serializable;
import java.util.Iterator;

public class CovariantIntersectIterator {

    public static void main(String... args) {
        int npeCount = 0;
        try {
            // JCEnhancedForLoop.expr's erased type is ALREADY an Iterable
            // iterator() comes from expr's erased type (MyIterable) and
            // is called using invokevirtual & returns a covariant type (MyIterable.MyIterator)
            for (Object s : (MyIterable & Serializable) null) {}
        } catch (NullPointerException e) {
            npeCount++;
        }
        try {
            // JCEnhancedForLoop.expr's erased type is NOT an Iterable
            // iterator() comes from Iterable (expr's erased type casted),
            // will be called by invokeinterface and return Iterator
            for (Object s : (MyIterableBase & Iterable<Object>) null) {}
        } catch (NullPointerException e) {
            npeCount++;
        }
        if (npeCount != 2) {
            throw new AssertionError("Expected NPE missing");
        }
    }

    abstract static class MyIterableBase {
        public abstract MyIterable.MyIterator iterator();
    }

    static class MyIterable extends MyIterableBase implements Iterable<Object> {

        class MyIterator implements Iterator<Object> {

            public boolean hasNext() {
                return false;
            }

            public Object next() {
                return null;
            }

            public void remove() {}
        }

        public MyIterator iterator() {
            return new MyIterator();
        }
    }
}
