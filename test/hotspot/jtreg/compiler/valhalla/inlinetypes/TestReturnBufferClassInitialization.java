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

/**
 * @test
 * @bug 8389391
 * @key stress randomness
 * @summary Return buffer allocation for late-inlined MH calls should not initialize the class.
 * @requires vm.compiler2.enabled
 * @library /test/lib
 * @enablePreview
 * @run main ${test.main.class}
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+StressIncrementalInlining -XX:StressSeed=1
 *                   -XX:CompileCommand=exclude,${test.main.class}::target
 *                   ${test.main.class}
 */

package compiler.valhalla.inlinetypes;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import jdk.test.lib.Asserts;

public class TestReturnBufferClassInitialization {
    static boolean initialized;
    static boolean returnNonNull;
    static int invocations;

    static value class UninitializedValue {
        final int value;

        UninitializedValue(int value) {
            this.value = value;
        }

        static {
            initialized = true;
        }
    }

    static UninitializedValue target() {
        invocations++;
        return returnNonNull ? new UninitializedValue(42) : null;
    }

    static final MethodHandle HANDLE;
    static {
        try {
            MethodType exact = MethodType.methodType(UninitializedValue.class);
            MethodHandle target = MethodHandles.lookup().findStatic(TestReturnBufferClassInitialization.class, "target", exact);
            HANDLE = target.asType(exact.changeReturnType(Object.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static Object test() throws Throwable {
        // Late inlining 'target' through the MH requires buffering because the caller expects an oop return.
        // C2 must not initialize UninitializedValue by allocating an unused buffer when the result is null.
        return (Object) HANDLE.invokeExact();
    }

    public static void main(String[] args) throws Throwable {
        Asserts.assertFalse(initialized, "Should not be initialized");
        Object result = null;
        for (int i = 0; i < 10_000; i++) {
            result = test();
        }
        Asserts.assertNull(result, "Unexpected result");
        Asserts.assertFalse(initialized, "Should not be initialized");
        Asserts.assertEQ(invocations, 10_000, "Unexpected invocation count");

        // Test that a non-null scalarized return deoptimizes at the next BCI without re-executing target
        returnNonNull = true;
        result = test();
        Asserts.assertEQ(invocations, 10_001, "Unexpected invocation count - target re-executed?");
        Asserts.assertEQ(result, new UninitializedValue(42), "Unexpected result");
        Asserts.assertTrue(initialized, "Should now be initialized");
    }
}

