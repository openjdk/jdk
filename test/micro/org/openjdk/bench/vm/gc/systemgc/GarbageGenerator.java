/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.vm.gc.systemgc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

public class GarbageGenerator {
    static final int K = 1024;
    static final int M = K * K;

    /**
     * Generates roughly 1GB of objects stored as an arraylist of
     * 1024 Object[]. Each Objects[] stores 1024 byte[] of size 1024.
     *
     * @return ArrayList of 1024 Objects[].
     */
    static ArrayList<Object[]> generateObjectArrays() {
        ArrayList<Object[]> tmp = new ArrayList<>();
        for (int i = 0; i < GarbageGenerator.K; i++) {
            Object[] x = new Object[GarbageGenerator.K];
            for (int j=0; j < GarbageGenerator.K; j++) {
                x[j] = new byte[GarbageGenerator.K];
            }
            tmp.add(x);
        }
        return tmp;
    }

    /**
     * Allocating an Object[] with elements and filling each slot with
     * byte[]. If sameSize is true all byte[] are 1024 large, otherwise
     * there are 8 different sizes from K/8 to 16K.
     *
     * @param sameSize all objects are 1K large.
     * @return
     */
    public static Object[] generateAndFillLargeObjArray(boolean sameSize) {
        // Aiming for ~ 1gb of heap usage. For different sizes
        // the average size is ~ 4k.
        Object[] tmp = new Object[sameSize ? M : M / 4];
        for (int i = 0; i < tmp.length; i++) {
            if (sameSize) {
                tmp[i] = new byte[K];
            } else {
                int multiplier = 1 << (i % 8); // 1,2,4,8,16,32,64,128
                tmp[i] = new byte[(K / 8) * multiplier ];
            }
        }
        return tmp;
    }

    public static HashMap<Integer, byte[]> generateAndFillHashMap(boolean sameSize) {
        HashMap<Integer, byte[]> tmp = new HashMap<>();
        int numberOfObjects = sameSize ? M : M / 4;
        for (int i = 0; i < numberOfObjects; i++) {
            if (sameSize) {
                tmp.put(i, new byte[K]);
            } else {
                int multiplier = 1 << (i % 8); // 1,2,4,8,16,32,64,128
                tmp.put(i, new byte[(K / 8) * multiplier]);
            }
        }
        return tmp;
    }

    public static TreeMap<Integer, byte[]> generateAndFillTreeMap(boolean sameSize) {
        TreeMap<Integer, byte[]> tmp = new TreeMap<>();
        int numberOfObjects = sameSize ? M : M / 4;
        for (int i = 0; i < numberOfObjects; i++) {
            if (sameSize) {
                tmp.put(i, new byte[K]);
            } else {
                int multiplier = 1 << (i % 8); // 1,2,4,8,16,32,64,128
                tmp.put(i, new byte[(K / 8) * multiplier]);
            }
        }
        return tmp;
    }
}
