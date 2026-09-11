/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng TestNormalizeBooleanVarHandle
 */

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.lang.foreign.ValueLayout.*;
import static org.testng.Assert.*;

// test normalization of smaller than int primitive types
public class TestNormalizeBooleanVarHandle {

    static final VarHandle VH = JAVA_BOOLEAN.varHandle();

    @Test(dataProvider = "bools")
    public void testBool(Function<Arena, MemorySegment> segmentFactory, Predicate<MemorySegment> accessor,
                         byte testValue, boolean expected) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ms = segmentFactory.apply(arena);
            ms.set(JAVA_BYTE, 0L, testValue);

            boolean b = accessor.test(ms);
            assertEquals(b, expected);
        }
    }

    @DataProvider
    public static Object[][] bools() {
        List<Object[]> cases = new ArrayList<>();
        for (Function<Arena, MemorySegment> segmentFactory : factories()) {
            for (Predicate<MemorySegment> accessor : accessors()) {
                cases.add(new Object[]{ segmentFactory, accessor,
                        (byte) 0b0 , false }); // canonical false
                cases.add(new Object[]{ segmentFactory, accessor,
                        (byte) 0b01, true  }); // canonical true
                cases.add(new Object[]{ segmentFactory, accessor,
                        (byte) 0b10, true  }); // zero least significant bit, but non-zero first byte
            }
        }

        return cases.toArray(Object[][]::new);
    }

    private static List<Function<Arena, MemorySegment>> factories() {
        return List.of(
            a -> a.allocate(JAVA_BYTE),
            _ -> MemorySegment.ofArray(new byte[1])
        );
    }

    private static List<Predicate<MemorySegment>> accessors() {
        return List.of(
            ms -> ms.get(JAVA_BOOLEAN, 0L),
            ms -> (boolean) VH.get(ms, 0L),
            ms -> (boolean) VH.getVolatile(ms, 0L),
            ms -> (boolean) VH.getAcquire(ms, 0L),
            ms -> (boolean) VH.getOpaque(ms, 0L)
        );
    }
}
