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

/*
 * @test
 * @bug 8371953
 * @summary General argument checks for Array APIs.
 * @run junit ArrayArgumentCheckTest
 */

import java.lang.constant.ConstantDescs;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArrayArgumentCheckTest {
    @Test
    void newArrayChecks() throws ReflectiveOperationException {
        // Single newInstance
        assertDoesNotThrow(() -> Array.newInstance(Object.class, 0));
        assertThrows(NullPointerException.class, () -> Array.newInstance(null, 0));
        assertThrows(NegativeArraySizeException.class, () -> Array.newInstance(Object.class, -1));
        assertThrows(IllegalArgumentException.class, () -> Array.newInstance(void.class, 0));
        var object255ArrayType = ConstantDescs.CD_Object.arrayType(255).resolveConstantDesc(MethodHandles.publicLookup());
        assertThrows(IllegalArgumentException.class, () -> Array.newInstance(object255ArrayType, 0));
        // Multi-level newInstance
        assertDoesNotThrow(() -> Array.newInstance(Object.class, 0));
        assertThrows(NullPointerException.class, () -> Array.newInstance(null, new int[1]));
        assertThrows(NullPointerException.class, () -> Array.newInstance(Object.class, (int[]) null));
        assertThrows(NegativeArraySizeException.class, () -> Array.newInstance(Object.class, new int[]{3, -1, 5}));
        assertThrows(IllegalArgumentException.class, () -> Array.newInstance(Object.class));
        var object254ArrayType = ConstantDescs.CD_Object.arrayType(254).resolveConstantDesc(MethodHandles.publicLookup());
        assertThrows(IllegalArgumentException.class, () -> Array.newInstance(object254ArrayType, new int[] {2, 3}));
    }

    @Test
    void getLengthChecks() {
        assertThrows(NullPointerException.class, () -> Array.getLength(null));
        assertThrows(IllegalArgumentException.class, () -> Array.getLength(5));
    }
}
