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
 * @summary Test a condy bootstrap with void return can produce null constant
 * @library /java/lang/invoke/common
 * @build test.java.lang.invoke.lib.InstructionHelper
 * @run junit CondyVoidForNullTest
 */

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.junit.jupiter.api.Test;
import test.java.lang.invoke.lib.InstructionHelper;

import static org.junit.jupiter.api.Assertions.*;

public class CondyVoidForNullTest {
    static final MethodHandles.Lookup L = MethodHandles.lookup();

    // A condy BSM that can only return null - statically represented with void type
    public static void voidNull(MethodHandles.Lookup lookup, String name, Class<?> type) {
    }

    static MethodType lookupMT(Class<?> ret, Class<?>... params) {
        return MethodType.methodType(ret, MethodHandles.Lookup.class, String.class, Class.class).
                appendParameterTypes(params);
    }

    @Test
    public void testNullConstant() throws Throwable {
        var handle = InstructionHelper.ldcDynamicConstant(MethodHandles.lookup(), "_", Object.class,
                CondyVoidForNullTest.class, "voidNull", lookupMT(void.class));
        assertNull(handle.invoke());

        handle = InstructionHelper.ldcDynamicConstant(MethodHandles.lookup(), "_", MethodType.class,
                CondyVoidForNullTest.class, "voidNull", lookupMT(void.class));
        assertNull(handle.invoke());

        var badLookupTypeHandle = InstructionHelper.ldcDynamicConstant(MethodHandles.lookup(), "_", int.class,
                CondyVoidForNullTest.class, "voidNull", lookupMT(void.class));
        var bme = assertThrows(BootstrapMethodError.class, () -> {
            var _ = (int) badLookupTypeHandle.invokeExact();
        });
        assertInstanceOf(NullPointerException.class, bme.getCause());
    }
}
