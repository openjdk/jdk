/*
 * Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2015, Red Hat Inc.
 * Copyright (c) 2021, Huawei Technologies Co., Ltd. All rights reserved.
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
 *
 */

package sun.jvm.hotspot.debugger.riscv64;

import java.lang.annotation.Native;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.cdbg.*;

/** Specifies the thread context on riscv64 platforms; only a sub-portion
 * of the context is guaranteed to be present on all operating
 * systems. */

public abstract class RISCV64ThreadContext implements ThreadContext {
    // Taken from /usr/include/asm/sigcontext.h on Linux/RISCV64.

    //  /*
    //   * Signal context structure - contains all info to do with the state
    //   * before the signal handler was invoked.
    //   */
    // struct sigcontext {
    //   struct user_regs_struct sc_regs;
    //   union __riscv_fp_state sc_fpregs;
    // };
    //
    // struct user_regs_struct {
    //    unsigned long pc;
    //    unsigned long ra;
    //    unsigned long sp;
    //    unsigned long gp;
    //    unsigned long tp;
    //    unsigned long t0;
    //    unsigned long t1;
    //    unsigned long t2;
    //    unsigned long s0;
    //    unsigned long s1;
    //    unsigned long a0;
    //    unsigned long a1;
    //    unsigned long a2;
    //    unsigned long a3;
    //    unsigned long a4;
    //    unsigned long a5;
    //    unsigned long a6;
    //    unsigned long a7;
    //    unsigned long s2;
    //    unsigned long s3;
    //    unsigned long s4;
    //    unsigned long s5;
    //    unsigned long s6;
    //    unsigned long s7;
    //    unsigned long s8;
    //    unsigned long s9;
    //    unsigned long s10;
    //    unsigned long s11;
    //    unsigned long t3;
    //    unsigned long t4;
    //    unsigned long t5;
    //    unsigned long t6;
    // };

    // NOTE: the indices for the various registers must be maintained as
    // listed across various operating systems. However, only a small
    // subset of the registers' values are guaranteed to be present (and
    // must be present for the SA's stack walking to work)

    // One instance of the Native annotation is enough to trigger header generation
    // for this file.
    @Native
    public static final int R0 = 0;
    public static final int R1 = 1;
    public static final int R2 = 2;
    public static final int R3 = 3;
    public static final int R4 = 4;
    public static final int R5 = 5;
    public static final int R6 = 6;
    public static final int R7 = 7;
    public static final int R8 = 8;
    public static final int R9 = 9;
    public static final int R10 = 10;
    public static final int R11 = 11;
    public static final int R12 = 12;
    public static final int R13 = 13;
    public static final int R14 = 14;
    public static final int R15 = 15;
    public static final int R16 = 16;
    public static final int R17 = 17;
    public static final int R18 = 18;
    public static final int R19 = 19;
    public static final int R20 = 20;
    public static final int R21 = 21;
    public static final int R22 = 22;
    public static final int R23 = 23;
    public static final int R24 = 24;
    public static final int R25 = 25;
    public static final int R26 = 26;
    public static final int R27 = 27;
    public static final int R28 = 28;
    public static final int R29 = 29;
    public static final int R30 = 30;
    public static final int R31 = 31;

    public static final int NPRGREG = 32;

    public static final int PC = R0;
    public static final int LR = R1;
    public static final int SP = R2;
    public static final int FP = R8;

    private long[] data;

    public RISCV64ThreadContext() {
        data = new long[NPRGREG];
    }

    public int getNumRegisters() {
        return NPRGREG;
    }

    public String getRegisterName(int index) {
        switch (index) {
        case LR: return "lr";
        case SP: return "sp";
        case PC: return "pc";
        default:
            return "r" + index;
        }
    }

    public void setRegister(int index, long value) {
        data[index] = value;
    }

    public long getRegister(int index) {
        return data[index];
    }

    public CFrame getTopFrame(Debugger dbg) {
        return null;
    }

    /** This can't be implemented in this class since we would have to
     * tie the implementation to, for example, the debugging system */
    public abstract void setRegisterAsAddress(int index, Address value);

    /** This can't be implemented in this class since we would have to
     * tie the implementation to, for example, the debugging system */
    public abstract Address getRegisterAsAddress(int index);
}
