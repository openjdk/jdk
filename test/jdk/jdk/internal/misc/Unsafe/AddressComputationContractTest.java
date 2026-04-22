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

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import static jdk.internal.misc.Unsafe.getUnsafe;
import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @bug 8361300
 * @summary Verify Unsafe memory address computation method contracts,
 *          exposed via sun.misc.Unsafe
 * @modules java.base/jdk.internal.misc
 * @run junit AddressComputationContractTest
 */
public class AddressComputationContractTest {

    int instanceField;
    static int staticField;

    private static final Field INSTANCE_FIELD;
    private static final Field STATIC_FIELD;

    static {
        try {
            INSTANCE_FIELD = AddressComputationContractTest.class.getDeclaredField("instanceField");
            STATIC_FIELD = AddressComputationContractTest.class.getDeclaredField("staticField");
        } catch (ReflectiveOperationException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @Test
    void objectFieldOffset() {
        assertDoesNotThrow(() -> getUnsafe().objectFieldOffset(INSTANCE_FIELD));
        assertThrows(NullPointerException.class, () -> getUnsafe().objectFieldOffset(null));
        assertThrows(IllegalArgumentException.class, () -> getUnsafe().objectFieldOffset(STATIC_FIELD));
    }

    @Test
    void knownObjectFieldOffset() {
        assertDoesNotThrow(() -> getUnsafe().objectFieldOffset(AddressComputationContractTest.class, "instanceField"));
        assertThrows(NullPointerException.class, () -> getUnsafe().objectFieldOffset(null, "instanceField"));
        assertThrows(NullPointerException.class, () -> getUnsafe().objectFieldOffset(AddressComputationContractTest.class, null));
        // Two conventional failure cases, not necessarily complete
        var dneMsg = assertThrows(InternalError.class, () -> getUnsafe().objectFieldOffset(AddressComputationContractTest.class, "doesNotExist")).getMessage();
        assertTrue(dneMsg.contains("AddressComputationContractTest.doesNotExist") && dneMsg.contains("not found"), dneMsg);
        var staticMsg = assertThrows(InternalError.class, () -> getUnsafe().objectFieldOffset(AddressComputationContractTest.class, "staticField")).getMessage();
        assertTrue(staticMsg.contains("AddressComputationContractTest.staticField") && staticMsg.contains("static field"), staticMsg);
    }

    @Test
    void staticFieldOffset() {
        assertDoesNotThrow(() -> getUnsafe().staticFieldOffset(STATIC_FIELD));
        assertThrows(NullPointerException.class, () -> getUnsafe().staticFieldOffset(null));
        assertThrows(IllegalArgumentException.class, () -> getUnsafe().staticFieldOffset(INSTANCE_FIELD));
    }

    @Test
    void staticFieldBase() {
        assertDoesNotThrow(() -> getUnsafe().staticFieldBase(STATIC_FIELD));
        assertThrows(NullPointerException.class, () -> getUnsafe().staticFieldBase(null));
        assertThrows(IllegalArgumentException.class, () -> getUnsafe().staticFieldBase(INSTANCE_FIELD));
    }

    @Test
    void arrayBaseOffset() {
        assertDoesNotThrow(() -> getUnsafe().arrayBaseOffset(int[].class));
        assertThrows(NullPointerException.class, () -> getUnsafe().arrayBaseOffset(null));
        // Caused by VM trying to throw java.lang.InvalidClassException (there's one in java.io instead)
        assertThrows(NoClassDefFoundError.class, () -> getUnsafe().arrayBaseOffset(AddressComputationContractTest.class));
    }

    @Test
    void arrayIndexScale() {
        assertDoesNotThrow(() -> getUnsafe().arrayIndexScale(int[].class));
        assertThrows(NullPointerException.class, () -> getUnsafe().arrayIndexScale(null));
        // Caused by VM trying to throw java.lang.InvalidClassException (there's one in java.io instead)
        assertThrows(NoClassDefFoundError.class, () -> getUnsafe().arrayIndexScale(AddressComputationContractTest.class));
    }
}
