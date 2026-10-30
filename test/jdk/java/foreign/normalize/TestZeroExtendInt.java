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
 * @requires jdk.foreign.linker != "FALLBACK"
 * @library ../
 * @run junit/othervm/native
 *   --enable-native-access=ALL-UNNAMED
 *   TestZeroExtendInt
 */

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestZeroExtendInt extends NativeTestHelper {

    static final MethodHandle MH_CAPTURE;

    static {
        try {
            MH_CAPTURE = MethodHandles.lookup().findStatic(TestZeroExtendInt.class, "capture",
                            MethodType.methodType(int.class, AtomicInteger.class, int.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @ParameterizedTest
    @MethodSource("cases")
    public void testZeroExtend(MemoryLayout layout, Object input, Object expectedOutput) throws Throwable {
        AtomicInteger captured = new AtomicInteger();
        // We pass a type smaller than int, but receive back an int
        // make sure it gets widened correctly according to its sign by the binding recipe.
        MemorySegment stub = LINKER.upcallStub(MH_CAPTURE.bindTo(captured),
                FunctionDescriptor.of(C_INT, C_INT), Arena.ofAuto());
        MethodHandle mh = LINKER.downcallHandle(FunctionDescriptor.of(C_INT, layout));
        int output = (int) mh.invoke(stub, input);
        assertEquals(expectedOutput, captured.get());
        assertEquals(expectedOutput, output);
    }

    private static int capture(AtomicInteger captured, int arg) {
        captured.set(arg);
        return arg;
    }

    public static Object[][] cases() {
        return new Object[][] {
                // when MIN_VALUE gets zero-extended the sign should be dropped
                { UCHAR,  Byte.MIN_VALUE,  Byte.toUnsignedInt(Byte.MIN_VALUE)   },
                { USHORT, Short.MIN_VALUE, Short.toUnsignedInt(Short.MIN_VALUE) },
                { UINT8,  Byte.MIN_VALUE,  Byte.toUnsignedInt(Byte.MIN_VALUE)   },
                { UINT16, Short.MIN_VALUE, Short.toUnsignedInt(Short.MIN_VALUE) },
        };
    }
}
