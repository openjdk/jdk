/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.valhalla.sandbox.corelibs.corelibs.mapprotos;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;

public class WalkX extends MapBase {

    IntFunction<Map<Integer, Integer>> mapSupplier;
    Map<Integer, Integer> map;

    @Setup
    public void setup() {
        super.init(size);
        try {
            Class<?> mapClass = Class.forName(mapType);
            mapSupplier =  (size) -> newInstance(mapClass, size);
        } catch (Exception ex) {
            System.out.printf("%s: %s%n", mapType, ex.getMessage());
            return;
        }

        map = mapSupplier.apply(0);
        for (Integer k : keys) {
            map.put(k, k);
        }
    }

    Map<Integer, Integer> newInstance(Class<?> mapClass, int size) {
        try {
            return (Map<Integer, Integer>)mapClass.getConstructor(int.class).newInstance(size);
        } catch (Exception ex) {
            throw new RuntimeException("failed", ex);
        }
    }

    @Benchmark
    public int sumIterator() {
        int s = 0;
        for (Iterator<Integer> iterator = map.keySet().iterator(); iterator.hasNext(); ) {
            s += iterator.next();
        }
        return s;
    }

    @Benchmark
    public int sumIteratorHidden() {
        int s = 0;
        for (Iterator<Integer> iterator = hide(map.keySet().iterator()); iterator.hasNext(); ) {
            s += iterator.next();
        }
        return s;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static Iterator<Integer> hide(Iterator<Integer> it) {
        return it;
    }

}
