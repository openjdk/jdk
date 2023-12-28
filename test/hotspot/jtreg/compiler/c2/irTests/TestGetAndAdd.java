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
package compiler.c2.x86;

import compiler.lib.ir_framework.*;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/*
 * @test
 * bug 8308444
 * @summary verify that the correct node is matched for atomic getAndAdd
 * @requires os.simpleArch == "x64"
 * @requires vm.compiler2.enabled
 * @library /test/lib /
 * @run driver compiler.c2.x86.TestGetAndAdd
 */
public class TestGetAndAdd {
    static final VarHandle B;
    static final VarHandle S;
    static final VarHandle I;
    static final VarHandle L;

    static {
        try {
            var lookup = MethodHandles.lookup();
            B = lookup.findStaticVarHandle(TestGetAndAdd.class, "b", byte.class);
            S = lookup.findStaticVarHandle(TestGetAndAdd.class, "s", short.class);
            I = lookup.findStaticVarHandle(TestGetAndAdd.class, "i", int.class);
            L = lookup.findStaticVarHandle(TestGetAndAdd.class, "l", long.class);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    static byte b;
    static short s;
    static int i;
    static long l;

    static byte b2;
    static short s2;
    static int i2;
    static long l2;

    public static void main(String[] args) {
        new TestFramework()
                .addFlags("-XX:+UseStoreImmI16")
                .start();
    }

    @Test
    @IR(counts = {IRNode.X86_LOCK_ADDB_REG, "1"}, phase = CompilePhase.FINAL_CODE)
    @IR(counts = {IRNode.X86_LOCK_ADDB_IMM, "1"}, phase = CompilePhase.FINAL_CODE)
    @IR(counts = {IRNode.X86_LOCK_XADDB, "3"}, phase = CompilePhase.FINAL_CODE)
    public static void addB() {
        B.getAndAdd(b2);
        B.getAndAdd((byte)1);
        b2 = (byte)B.getAndAdd(b2);
    }

    @Test
    @IR(counts = {IRNode.X86_LOCK_ADDS_REG, "1"}, phase = CompilePhase.FINAL_CODE)
    @IR(counts = {IRNode.X86_LOCK_ADDS_IMM, "1"}, phase = CompilePhase.FINAL_CODE)
    @IR(counts = {IRNode.X86_LOCK_XADDS, "3"}, phase = CompilePhase.FINAL_CODE)
    public static void addS() {
        S.getAndAdd(s2);
        S.getAndAdd((short)1);
        s2 = (short)S.getAndAdd(s2);
    }

    @Test
    @IR(counts = {IRNode.X86_LOCK_ADDI_REG, "1"}, phase = CompilePhase.FINAL_CODE)
    @IR(counts = {IRNode.X86_LOCK_ADDI_IMM, "1"}, phase = CompilePhase.FINAL_CODE)
    @IR(counts = {IRNode.X86_LOCK_XADDI, "3"}, phase = CompilePhase.FINAL_CODE)
    public static void addI() {
        I.getAndAdd(i2);
        I.getAndAdd(1);
        i2 = (int)I.getAndAdd(i2);
    }

    @Test
    @IR(counts = {IRNode.X86_LOCK_ADDL_REG, "1"}, phase = CompilePhase.FINAL_CODE)
    @IR(counts = {IRNode.X86_LOCK_ADDL_IMM, "1"}, phase = CompilePhase.FINAL_CODE)
    @IR(counts = {IRNode.X86_LOCK_XADDL, "3"}, phase = CompilePhase.FINAL_CODE)
    public static void addL() {
        L.getAndAdd(l2);
        L.getAndAdd(1L);
        l2 = (long)L.getAndAdd(l2);
    }
}
