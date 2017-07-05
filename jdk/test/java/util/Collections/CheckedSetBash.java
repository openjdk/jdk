/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     4904067
 * @summary Unit test for Collections.checkedSet
 * @author  Josh Bloch
 */

import java.util.*;

public class CheckedSetBash {
    static Random rnd = new Random();

    public static void main(String[] args) {
        int numItr = 100;
        int setSize = 100;

        for (int i=0; i<numItr; i++) {
            Set s1 = newSet();
            AddRandoms(s1, setSize);

            Set s2 = newSet();
            AddRandoms(s2, setSize);

            Set intersection = clone(s1);
            intersection.retainAll(s2);
            Set diff1 = clone(s1); diff1.removeAll(s2);
            Set diff2 = clone(s2); diff2.removeAll(s1);
            Set union = clone(s1); union.addAll(s2);

            if (diff1.removeAll(diff2))
                fail("Set algebra identity 2 failed");
            if (diff1.removeAll(intersection))
                fail("Set algebra identity 3 failed");
            if (diff2.removeAll(diff1))
                fail("Set algebra identity 4 failed");
            if (diff2.removeAll(intersection))
                fail("Set algebra identity 5 failed");
            if (intersection.removeAll(diff1))
                fail("Set algebra identity 6 failed");
            if (intersection.removeAll(diff1))
                fail("Set algebra identity 7 failed");

            intersection.addAll(diff1); intersection.addAll(diff2);
            if (!intersection.equals(union))
                fail("Set algebra identity 1 failed");

            if (new HashSet(union).hashCode() != union.hashCode())
                fail("Incorrect hashCode computation.");

            Iterator e = union.iterator();
            while (e.hasNext())
                if (!intersection.remove(e.next()))
                    fail("Couldn't remove element from copy.");
            if (!intersection.isEmpty())
                fail("Copy nonempty after deleting all elements.");

            e = union.iterator();
            while (e.hasNext()) {
                Object o = e.next();
                if (!union.contains(o))
                    fail("Set doesn't contain one of its elements.");
                e.remove();
                if (union.contains(o))
                    fail("Set contains element after deletion.");
            }
            if (!union.isEmpty())
                fail("Set nonempty after deleting all elements.");

            s1.clear();
            if (!s1.isEmpty())
                fail("Set nonempty after clear.");
        }
    }

    // Done inefficiently so as to exercise toArray
    static Set clone(Set s) {
        Set clone = newSet();
        List arrayList = Arrays.asList(s.toArray());
        clone.addAll(arrayList);
        if (!s.equals(clone))
            fail("Set not equal to copy.");
        if (!s.containsAll(clone))
            fail("Set does not contain copy.");
        if (!clone.containsAll(s))
            fail("Copy does not contain set.");
        return clone;
    }

    static Set newSet() {
        Set s = Collections.checkedSet(new HashSet(), Integer.class);
        if (!s.isEmpty())
            fail("New instance non empty.");
        return s;
    }

    static void AddRandoms(Set s, int n) {
        for (int i=0; i<n; i++) {
            int r = rnd.nextInt() % n;
            Integer e = new Integer(r < 0 ? -r : r);

            int preSize = s.size();
            boolean prePresent = s.contains(e);
            boolean added = s.add(e);
            if (!s.contains(e))
                fail ("Element not present after addition.");
            if (added == prePresent)
                fail ("added == alreadyPresent");
            int postSize = s.size();
            if (added && preSize == postSize)
                fail ("Add returned true, but size didn't change.");
            if (!added && preSize != postSize)
                fail ("Add returned false, but size changed.");
        }
    }

    static void fail(String s) {
        throw new RuntimeException(s);

    }
}
