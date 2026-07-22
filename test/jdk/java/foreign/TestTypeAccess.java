/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

/*
 * @test
 * @run junit TestTypeAccess
 */

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import java.lang.invoke.VarHandle;
import java.lang.invoke.WrongMethodTypeException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestTypeAccess {

    static final VarHandle INT_HANDLE = ValueLayout.JAVA_INT.varHandle();
    static final VarHandle ADDR_HANDLE = ValueLayout.ADDRESS.varHandle();

    @Test
    public void testMemoryAddressCoordinateAsString() {
        Assertions.assertThrows(ClassCastException.class, () -> {
            int v = (int)INT_HANDLE.get("string", 0L);
        });
    }

    @Test
    public void testMemoryCoordinatePrimitive() {
        Assertions.assertThrows(WrongMethodTypeException.class, () -> {
            int v = (int)INT_HANDLE.get(1);
        });
    }

    @Test
    public void testMemoryAddressValueGetAsString() {
        Assertions.assertThrows(ClassCastException.class, () -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment s = arena.allocate(8, 8);
                String address = (String)ADDR_HANDLE.get(s, 0L);
            }
        });
    }

    @Test
    public void testMemoryAddressValueSetAsString() {
        Assertions.assertThrows(ClassCastException.class, () -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment s = arena.allocate(8, 8);;
                ADDR_HANDLE.set(s, 0L, "string");
            }
        });
    }

    @Test
    public void testMemoryAddressValueGetAsPrimitive() {
        Assertions.assertThrows(WrongMethodTypeException.class, () -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment s = arena.allocate(8, 8);
                int address = (int)ADDR_HANDLE.get(s, 0L);
            }
        });
    }

    @Test
    public void testMemoryAddressValueSetAsPrimitive() {
        Assertions.assertThrows(WrongMethodTypeException.class, () -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment s = arena.allocate(8, 8);;
                ADDR_HANDLE.set(s, 1);
            }
        });
    }

}
