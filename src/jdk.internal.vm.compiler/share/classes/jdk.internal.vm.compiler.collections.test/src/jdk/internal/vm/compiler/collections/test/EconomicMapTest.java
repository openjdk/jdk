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

import java.util.LinkedHashMap;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.UnmodifiableEconomicMap;
import org.junit.Assert;
import org.junit.Test;

public class EconomicMapTest {

    @Test
    public void testMapGetDefault() {
        EconomicMap<Integer, Integer> map = EconomicMap.create();
        map.put(0, 1);
        Assert.assertEquals(map.get(0, 2), Integer.valueOf(1));
        Assert.assertEquals(map.get(1, 2), Integer.valueOf(2));
    }

    @Test
    public void testMapPutAll() {
        EconomicMap<Integer, Integer> map = EconomicMap.create();
        EconomicMap<Integer, Integer> newMap = EconomicMap.wrapMap(new LinkedHashMap<>());
        newMap.put(1, 1);
        newMap.put(2, 4);
        map.putAll(newMap);
        Assert.assertEquals(map.size(), 2);

        UnmodifiableEconomicMap<Integer, Integer> unmodifiableEconomicMap = EconomicMap.create(newMap);

        map.removeKey(1);
        map.put(2, 2);
        map.put(3, 9);

        map.putAll(unmodifiableEconomicMap);
        Assert.assertEquals(map.size(), 3);
        Assert.assertEquals(map.get(2), Integer.valueOf(4));
    }

    @Test
    public void testToString() {
        EconomicMap<Integer, Integer> map = EconomicMap.create();
        map.put(0, 0);
        map.put(1, 1);
        Assert.assertEquals(map.toString(), "map(size=2, {(0,0),(1,1)})");
    }

}
