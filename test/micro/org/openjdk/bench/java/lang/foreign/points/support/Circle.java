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
