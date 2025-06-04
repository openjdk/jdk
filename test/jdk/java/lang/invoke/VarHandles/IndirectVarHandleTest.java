/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8307508
 * @run junit IndirectVarHandleTest
 * @summary Test VarHandle::isAccessModeSupported on indirect VarHandle
 *          produced by MethodHandles.filterCoordinates
 */
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IndirectVarHandleTest {
    @Test
    public void testIsAccessModeTypeSupported() throws Throwable {
        var lookup = MethodHandles.lookup();
        var intArrayVh = MethodHandles.arrayElementVarHandle(int[].class);
        var addOne = lookup.bind((IntUnaryOperator) a -> a + 1, "applyAsInt",
                MethodType.methodType(int.class, int.class));
        var offsetIntArrayVh = MethodHandles.filterCoordinates(intArrayVh, 1, addOne);

        for (var mode : VarHandle.AccessMode.values()) {
            assertEquals(intArrayVh.isAccessModeSupported(mode),
                    offsetIntArrayVh.isAccessModeSupported(mode), mode.toString());
        }

        var stringArrayVh = MethodHandles.arrayElementVarHandle(String[].class);
        var offsetStringArrayVh = MethodHandles.filterCoordinates(stringArrayVh, 1, addOne);

        for (var mode : VarHandle.AccessMode.values()) {
            assertEquals(stringArrayVh.isAccessModeSupported(mode),
                    offsetStringArrayVh.isAccessModeSupported(mode), mode.toString());
        }
    }
}
