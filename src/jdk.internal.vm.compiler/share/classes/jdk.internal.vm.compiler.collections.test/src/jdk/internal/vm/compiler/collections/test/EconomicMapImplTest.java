/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.Iterator;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.EconomicSet;
import jdk.internal.vm.compiler.collections.Equivalence;
import jdk.internal.vm.compiler.collections.UnmodifiableEconomicSet;
import org.junit.Assert;
import org.junit.Test;

public class EconomicMapImplTest {

    @Test(expected = UnsupportedOperationException.class)
    public void testRemoveNull() {
        EconomicMap<Integer, Integer> map = EconomicMap.create(10);
        map.removeKey(null);
    }

    @Test
    public void testInitFromHashSet() {
        UnmodifiableEconomicSet<Integer> set = new UnmodifiableEconomicSet<Integer>() {

            @Override
            public boolean contains(Integer element) {
                return element == 0;
            }

            @Override
            public int size() {
                return 1;
            }

            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public Iterator<Integer> iterator() {
                return new Iterator<Integer>() {

                    private boolean visited = false;

                    @Override
                    public boolean hasNext() {
                        return !visited;
                    }

                    @Override
                    public Integer next() {
                        if (visited) {
                            return null;
                        } else {
                            visited = true;
                            return 1;
                        }
                    }
                };
            }
        };

        EconomicSet<Integer> newSet = EconomicSet.create(Equivalence.DEFAULT, set);
        Assert.assertEquals(newSet.size(), 1);
    }

    @Test
    public void testCopyHash() {
        EconomicSet<Integer> set = EconomicSet.create(Equivalence.IDENTITY);
        set.addAll(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
        EconomicSet<Integer> newSet = EconomicSet.create(Equivalence.IDENTITY, set);
        Assert.assertEquals(newSet.size(), 10);
        newSet.remove(8);
        newSet.remove(9);
        Assert.assertEquals(newSet.size(), 8);
    }

    @Test
    public void testNewEquivalence() {
        EconomicSet<Integer> set = EconomicSet.create(new Equivalence() {
            @Override
            public boolean equals(Object a, Object b) {
                return false;
            }

            @Override
            public int hashCode(Object o) {
                return 0;
            }
        });
        set.addAll(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
        Assert.assertTrue(set.add(new Integer(0)));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testMapPutNull() {
        EconomicMap<Integer, Integer> map = EconomicMap.create();
        map.put(null, null);
    }

}
