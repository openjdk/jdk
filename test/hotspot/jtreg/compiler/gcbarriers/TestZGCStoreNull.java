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
 * @test id=default
 * @summary RISC-V ZGC constant null stores do not need a register holding null
 * @requires vm.gc.Z & vm.compiler2.enabled & (os.arch == "riscv64")
 * @library /test/lib /
 * @run driver compiler.gcbarriers.TestZGCStoreNull -XX:-UseZalasr
 */

/*
 * @test id=zalasr
 * @summary RISC-V ZGC constant null stores using Zalasr
 * @requires vm.gc.Z & vm.compiler2.enabled & (os.arch == "riscv64")
 * @requires vm.cpu.features ~= ".*zalasr.*"
 * @library /test/lib /
 * @run driver compiler.gcbarriers.TestZGCStoreNull -XX:+UseZalasr
 */

package compiler.gcbarriers;

import compiler.lib.ir_framework.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import jdk.test.lib.Asserts;

public class TestZGCStoreNull {
    private static final String NULL_STORE = "\\bzStoreP(?:Volatile)?Null\\b";
    private static final String NULL_REGISTER = "\\bloadConP0\\b";

    static class Holder {
        Object field;
        volatile Object volatileField;
    }

    private static final VarHandle FIELD;
    private static final VarHandle ARRAY = MethodHandles.arrayElementVarHandle(Object[].class);
    private static final Object VALUE = new Object();
    private static final Holder HOLDER = new Holder();
    private static final Object[] ELEMENTS = new Object[4];

    static {
        try {
            FIELD = MethodHandles.lookup().findVarHandle(Holder.class, "field", Object.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:+UseZGC", "-Xms128m", "-Xmx128m",
                "-XX:+UnlockExperimentalVMOptions",
                args[0],
                "-XX:CompileCommand=blackhole," + TestZGCStoreNull.class.getName() + "::blackhole");
    }

    static void blackhole(Object o) {}

    @Test
    @IR(counts = {NULL_STORE, "1"}, failOn = {NULL_REGISTER}, phase = CompilePhase.FINAL_CODE)
    static void testField(Holder h) {
        h.field = null;
    }

    @Test
    @IR(counts = {NULL_STORE, "1"}, failOn = {NULL_REGISTER}, phase = CompilePhase.FINAL_CODE)
    static void testVolatile(Holder h) {
        h.volatileField = null;
    }

    @Test
    @IR(counts = {NULL_STORE, "1"}, failOn = {NULL_REGISTER}, phase = CompilePhase.FINAL_CODE)
    static void testRelease(Holder h) {
        FIELD.setRelease(h, null);
    }

    @Test
    @IR(counts = {NULL_STORE, "1"}, failOn = {NULL_REGISTER}, phase = CompilePhase.FINAL_CODE)
    static void testArray(Object[] a, int index) {
        a[index] = null;
    }

    @Test
    @IR(counts = {NULL_STORE, "1"}, failOn = {NULL_REGISTER}, phase = CompilePhase.FINAL_CODE)
    static void testArrayVolatile(Object[] a, int index) {
        ARRAY.setVolatile(a, index, null);
    }

    @Test
    @IR(counts = {NULL_STORE, "1"}, failOn = {NULL_REGISTER}, phase = CompilePhase.FINAL_CODE)
    @IR(counts = {IRNode.Z_STORE_P_WITH_BARRIER_FLAG, "elided", "1"}, phase = CompilePhase.FINAL_CODE)
    static void testStoreThenNull(Holder h, Object value) {
        h.volatileField = value;
        h.volatileField = null;
    }

    @Test
    @IR(counts = {NULL_STORE, "1"}, failOn = {NULL_REGISTER}, phase = CompilePhase.FINAL_CODE)
    @IR(counts = {IRNode.Z_STORE_P_WITH_BARRIER_FLAG, "elided", "1"}, phase = CompilePhase.FINAL_CODE)
    static Holder testAllocateThenNull() {
        Holder h = new Holder();
        blackhole(h);
        h.volatileField = null;
        return h;
    }

    @Run(test = {"testField", "testVolatile", "testRelease", "testArray",
                 "testArrayVolatile", "testStoreThenNull", "testAllocateThenNull"})
    static void run(RunInfo info) {
        // Also exercise compiled stores after a collection has changed the colors.
        if (!info.isWarmUp()) {
            System.gc();
        }
        HOLDER.field = VALUE;
        testField(HOLDER);
        Asserts.assertNull(HOLDER.field);

        HOLDER.volatileField = VALUE;
        testVolatile(HOLDER);
        Asserts.assertNull(HOLDER.volatileField);

        HOLDER.field = VALUE;
        testRelease(HOLDER);
        Asserts.assertNull(FIELD.getAcquire(HOLDER));

        ELEMENTS[1] = VALUE;
        testArray(ELEMENTS, 1);
        Asserts.assertNull(ELEMENTS[1]);

        ELEMENTS[2] = VALUE;
        testArrayVolatile(ELEMENTS, 2);
        Asserts.assertNull(ARRAY.getVolatile(ELEMENTS, 2));

        testStoreThenNull(HOLDER, VALUE);
        Asserts.assertNull(HOLDER.volatileField);
        Asserts.assertNull(testAllocateThenNull().volatileField);
    }
}
