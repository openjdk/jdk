/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.jtt.optimize;

import java.util.Random;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */
@SuppressWarnings("unused")
public class Conditional01 extends JTTTest {

    private static class TestClass {
        private int nextPC;
        private int pc;
        private boolean aC;
        private boolean aH;
        private boolean aN;
        private boolean aZ;
        private boolean aV;
        private boolean aS;
        private int cyclesConsumed;
        private int[] sram = new int[RAM_SIZE];

        public void visit(CPC i) {
            nextPC = pc + 2;
            int tmp0 = getRegisterByte(i.r1);
            int tmp1 = getRegisterByte(i.r2);
            int tmp2 = bit(aC);
            int tmp3 = tmp0 - tmp1 - tmp2;
            boolean tmp4 = ((tmp0 & 128) != 0);
            boolean tmp5 = ((tmp1 & 128) != 0);
            boolean tmp6 = ((tmp3 & 128) != 0);
            boolean tmp7 = ((tmp0 & 8) != 0);
            boolean tmp8 = ((tmp1 & 8) != 0);
            boolean tmp9 = ((tmp3 & 8) != 0);
            aH = !tmp7 && tmp8 || tmp8 && tmp9 || tmp9 && !tmp7;
            aC = !tmp4 && tmp5 || tmp5 && tmp6 || tmp6 && !tmp4;
            aN = tmp6;
            aZ = low(tmp3) == 0 && aZ;
            aV = tmp4 && !tmp5 && !tmp6 || !tmp4 && tmp5 && tmp6;
            aS = (aN != aV);
            cyclesConsumed++;
        }

        public int getRegisterByte(Register r1) {
            if ((r1.val % 10) == 0) {
                return sram[r1.num];
            }
            return r1.val;
        }

        public int low(int tmp3) {
            return tmp3 & 0x01;
        }

        public int bit(boolean c2) {
            return c2 ? 1 : 0;
        }
    }

    private static final int RAM_SIZE = 0x100;
    private static final int init = new Random().nextInt();
    private static final int init1 = new Register().val;
    private static final Register init2 = new CPC().r1;

    public static int test(int arg) {
        TestClass c = new TestClass();
        Random rnd = new Random();
        for (int i = 0; i < arg; i++) {
            CPC i2 = new CPC();
            i2.r1 = new Register();
            i2.r1.val = i;
            i2.r1.num = i + RAM_SIZE - 20;
            i2.r2 = new Register();
            i2.r2.val = rnd.nextInt();
            i2.r2.num = rnd.nextInt(RAM_SIZE);
            try {
                c.visit(i2);
            } catch (RuntimeException re) {

            }
        }
        return c.cyclesConsumed;
    }

    private static class Register {

        int val;
        int num;
    }

    private static class CPC {

        public Register r1;
        public Register r2;

    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 0);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", 10);
    }

    @Test
    public void run2() throws Throwable {
        runTest("test", 20);
    }

    @Test
    public void run3() throws Throwable {
        runTest("test", 40);
    }

}
