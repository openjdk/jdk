/*
 * Copyright (c) 1999, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     4216997
 * @summary Cloning a subclass of LinkedList results in an object that isn't
 *          an instance of the subclass.  The same applies to TreeSet and
 *          TreeMap.
 * @library /test/lib
 */

import jdk.test.lib.valueclass.VClass;

import java.util.LinkedList;
import java.util.TreeMap;
import java.util.TreeSet;

public class Clone {
    public static void main(String[] args) {
        testStrings();
        testVClass();
    }

    private static void testStrings() {
        LinkedList2 l = new LinkedList2();
        checkLinkedListClone(l, "LinkedList.clone() is broken 1.");
        l.add("a");
        checkLinkedListClone(l, "LinkedList.clone() is broken 2.");
        l.add("b");
        checkLinkedListClone(l, "LinkedList.clone() is broken 3.");


        TreeSet2 s = new TreeSet2();
        checkTreeSetClone(s, "TreeSet.clone() is broken.");
        s.add("a");
        checkTreeSetClone(s, "TreeSet.clone() is broken.");
        s.add("b");
        checkTreeSetClone(s, "TreeSet.clone() is broken.");

        TreeMap2 m = new TreeMap2();
        checkTreeMapClone(m, "TreeMap.clone() is broken.");
        m.put("a", "b");
        checkTreeMapClone(m, "TreeMap.clone() is broken.");
        m.put("c", "d");
        checkTreeMapClone(m, "TreeMap.clone() is broken.");
    }

    private static void testVClass() {
        LinkedList2 l = new LinkedList2();
        checkLinkedListClone(l, "LinkedList.clone() is broken for VClass 1.");
        l.add(new VClass(1, new int[] { 0 }));
        checkLinkedListClone(l, "LinkedList.clone() is broken for VClass 2.");
        l.add(new VClass(2, new int[] { 0 }));
        checkLinkedListClone(l, "LinkedList.clone() is broken for VClass 3.");

        TreeSet2 s = new TreeSet2();
        checkTreeSetClone(s, "TreeSet.clone() is broken for VClass 1.");
        s.add(new VClass(1, new int[] { 0 }));
        checkTreeSetClone(s, "TreeSet.clone() is broken for VClass 2.");
        s.add(new VClass(2, new int[] { 0 }));
        checkTreeSetClone(s, "TreeSet.clone() is broken for VClass 3.");

        TreeMap2 m = new TreeMap2();
        checkTreeMapClone(m, "TreeMap.clone() is broken for VClass 1.");
        m.put(new VClass(1, new int[] { 0 }), new VClass(1, new int[] { 1 }));
        checkTreeMapClone(m, "TreeMap.clone() is broken for VClass 2.");
        m.put(new VClass(2, new int[] { 0 }), new VClass(2, new int[] { 1 }));
        checkTreeMapClone(m, "TreeMap.clone() is broken for VClass 3.");
    }

    private static void checkLinkedListClone(LinkedList2 l, String message) {
        LinkedList2 lClone = (LinkedList2) l.clone();
        if (!(l.equals(lClone) && lClone.equals(l)))
            throw new RuntimeException(message);
    }

    private static void checkTreeSetClone(TreeSet2 s, String message) {
        TreeSet2 sClone = (TreeSet2) s.clone();
        if (!(s.equals(sClone) && sClone.equals(s)))
            throw new RuntimeException(message);
    }

    private static void checkTreeMapClone(TreeMap2 m, String message) {
        TreeMap2 mClone = (TreeMap2) m.clone();
        if (!(m.equals(mClone) && mClone.equals(m)))
            throw new RuntimeException(message);
    }

    private static class LinkedList2 extends LinkedList {}
    private static class TreeSet2    extends TreeSet {}
    private static class TreeMap2    extends TreeMap {}
}
