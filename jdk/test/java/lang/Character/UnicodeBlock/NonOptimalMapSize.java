/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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


/**
 * @test
 * @bug 8080535
 * @summary Expected size of Character.UnicodeBlock.map is not optimal
 */

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class NonOptimalMapSize {
    public static void main(String[] args) throws Throwable {
        Class<?> ubCls = Character.UnicodeBlock.class;
        Field mapField = ubCls.getDeclaredField("map");
        mapField.setAccessible(true);
        Map<?,?> map = (Map<?,?>)mapField.get(null);
        if (!map.getClass().equals(HashMap.class)) {
            throw new RuntimeException(
                    "Character.UnicodeBlock.map is expected to be HashMap");
        }
        int mapSize = map.size();

        Field sizeField = ubCls.getDeclaredField("INITIAL_CAPACITY");
        sizeField.setAccessible(true);
        int INITIAL_CAPACITY = sizeField.getInt(null);

        // Construct a HashMap with specified initial capacity
        HashMap<Object,Object> map1 = new HashMap<>(INITIAL_CAPACITY);
        Class<?> hmCls = HashMap.class;
        Field tableField = hmCls.getDeclaredField("table");
        tableField.setAccessible(true);
        // ... and fill it up
        map1.put(new Object(), new Object());
        final Object initialTable = tableField.get(map1);
        while (map1.size() < map.size() &&
                initialTable == tableField.get(map1)) {
            map1.put(new Object(), new Object());
        }

        // Now check that internal storage didn't change
        if (initialTable != tableField.get(map1)) {
            throw new RuntimeException(
                    "Initial capacity " + INITIAL_CAPACITY +
                    " was only enough to hold " + (map1.size()-1) +
                    " entries, but needed " + map.size());
        }
    }
}
