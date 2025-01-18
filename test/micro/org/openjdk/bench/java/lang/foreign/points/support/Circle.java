/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.lang.foreign.points.support;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static org.openjdk.bench.java.lang.foreign.CLayouts.C_DOUBLE;

public class Circle {
    public static final MemoryLayout POINT_LAYOUT = MemoryLayout.structLayout(
            C_DOUBLE.withName("x"),
            C_DOUBLE.withName("y")
    );
    private static final MethodHandle MH_UNIT_ROTATED;

    static {
        Linker abi = Linker.nativeLinker();
        System.loadLibrary("Point");
        SymbolLookup loaderLibs = SymbolLookup.loaderLookup();
        MH_UNIT_ROTATED = abi.downcallHandle(
                loaderLibs.findOrThrow("unit_rotated"),
                FunctionDescriptor.of(POINT_LAYOUT, C_DOUBLE)
        );
    }

    private final MemorySegment points;

    public Circle(SegmentAllocator allocator, int numPoints) {
        try {
            points = allocator.allocate(POINT_LAYOUT, numPoints);
            for (int i = 0; i < numPoints; i++) {
                double phi = 2 * Math.PI * i / numPoints;
                // points[i] = unit_rotated(phi);
                MemorySegment dest = points.asSlice(i * POINT_LAYOUT.byteSize(), POINT_LAYOUT.byteSize());
                MemorySegment unused =
                        (MemorySegment) MH_UNIT_ROTATED.invokeExact(
                                (SegmentAllocator) (_, _) -> dest,
                                phi);
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
