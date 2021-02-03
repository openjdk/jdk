/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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


















package jdk.internal.vm.compiler.collections.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import jdk.internal.vm.compiler.collections.EconomicSet;
import jdk.internal.vm.compiler.collections.Equivalence;
import org.junit.Assert;
import org.junit.Test;

public class EconomicSetTest {

    @Test
    public void testUtilities() {
        EconomicSet<Integer> set = EconomicSet.create(0);
        set.add(0);
        Assert.assertTrue(set.add(1));
        Assert.assertEquals(set.size(), 2);
        Assert.assertFalse(set.add(1));
        Assert.assertEquals(set.size(), 2);
        set.remove(1);
        Assert.assertEquals(set.size(), 1);
        set.remove(2);
        Assert.assertEquals(set.size(), 1);
        Assert.assertTrue(set.add(1));
        set.clear();
        Assert.assertEquals(set.size(), 0);
    }

    @Test
    public void testAddAll() {
        EconomicSet<Integer> set = EconomicSet.create();
        set.addAll(Arrays.asList(0, 1, 0));
        Assert.assertEquals(set.size(), 2);

        EconomicSet<Integer> newSet = EconomicSet.create();
        newSet.addAll(Arrays.asList(1, 2));
        Assert.assertEquals(newSet.size(), 2);
        newSet.addAll(set);
        Assert.assertEquals(newSet.size(), 3);
    }

    @Test
    public void testRemoveAll() {
        EconomicSet<Integer> set = EconomicSet.create();
        set.addAll(Arrays.asList(0, 1));

        set.removeAll(Arrays.asList(1, 2));
        Assert.assertEquals(set.size(), 1);

        set.removeAll(EconomicSet.create(set));
        Assert.assertEquals(set.size(), 0);
    }

    @Test
    public void testRetainAll() {
        EconomicSet<Integer> set = EconomicSet.create();
        set.addAll(Arrays.asList(0, 1, 2));

        EconomicSet<Integer> newSet = EconomicSet.create();
        newSet.addAll(Arrays.asList(2, 3));

        set.retainAll(newSet);
        Assert.assertEquals(set.size(), 1);
    }

    @Test
    public void testToArray() {
        EconomicSet<Integer> set = EconomicSet.create();
        set.addAll(Arrays.asList(0, 1));
        Assert.assertArrayEquals(set.toArray(new Integer[2]), new Integer[]{0, 1});
    }

    @Test
    public void testToString() {
        EconomicSet<Integer> set = EconomicSet.create();
        set.addAll(Arrays.asList(0, 1));
        Assert.assertEquals(set.toString(), "set(size=2, {0,1})");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testToUnalignedArray() {
        Assert.assertArrayEquals(EconomicSet.create().toArray(new Integer[2]), new Integer[0]);
    }

    @Test
    public void testSetRemoval() {
        ArrayList<Integer> initialList = new ArrayList<>();
        ArrayList<Integer> removalList = new ArrayList<>();
        ArrayList<Integer> finalList = new ArrayList<>();
        EconomicSet<Integer> set = EconomicSet.create(Equivalence.IDENTITY);
        set.add(1);
        set.add(2);
        set.add(3);
        set.add(4);
        set.add(5);
        set.add(6);
        set.add(7);
        set.add(8);
        set.add(9);
        Iterator<Integer> i1 = set.iterator();
        while (i1.hasNext()) {
            initialList.add(i1.next());
        }
        int size = 0;
        Iterator<Integer> i2 = set.iterator();
        while (i2.hasNext()) {
            Integer elem = i2.next();
            if (size++ < 8) {
                i2.remove();
            }
            removalList.add(elem);
        }
        Iterator<Integer> i3 = set.iterator();
        while (i3.hasNext()) {
            finalList.add(i3.next());
        }
        Assert.assertEquals(initialList, removalList);
        Assert.assertEquals(1, finalList.size());
        Assert.assertEquals(newInteger(9), finalList.get(0));
    }

    @SuppressWarnings({"deprecation", "removal", "unused"})
    private static Integer newInteger(int value) {
        return new Integer(value);
    }

}
