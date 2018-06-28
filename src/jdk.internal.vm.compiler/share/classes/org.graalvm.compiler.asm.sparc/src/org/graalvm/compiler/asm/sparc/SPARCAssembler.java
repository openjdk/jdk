/*
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.asm.sparc;

import static org.graalvm.compiler.asm.sparc.SPARCAssembler.CC.Icc;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.Always;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Add;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Addc;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Addcc;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.And;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Andcc;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Andn;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Andncc;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Casa;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Casxa;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Flushw;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Fpop1;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Fpop2;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Impdep1;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Jmpl;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Lddf;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Ldf;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Ldsb;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Ldsh;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Ldsw;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Ldub;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Lduh;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Lduw;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Lduwa;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Ldx;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Ldxa;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Membar;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Movcc;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Mulx;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Or;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Popc;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Prefetch;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Rd;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Restore;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Save;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Sdivx;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Sll;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Sllx;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Sra;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Srax;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Srl;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Srlx;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Stb;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Stdf;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Stf;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Sth;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Stw;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Stx;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Stxa;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Sub;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Subcc;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Udivx;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Wr;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Xnor;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Xor;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Op3s.Xorcc;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fabsd;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fabss;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Faddd;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fadds;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fdivd;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fdivs;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fdtoi;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fdtos;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fdtox;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fitod;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fitos;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fmovd;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fmovs;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fmuld;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fmuls;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fnegd;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fnegs;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fpadd32;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fsmuld;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fsqrtd;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fsqrts;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fsrc2d;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fsrc2s;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fstod;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fstoi;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fstox;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fsubd;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fsubs;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fxtod;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fxtos;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fzerod;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Fzeros;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Movdtox;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Movstosw;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Movwtos;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.Movxtod;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Opfs.UMulxhi;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Ops.ArithOp;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Ops.LdstOp;
import static java.lang.String.format;
import static jdk.vm.ci.sparc.SPARC.CPU;
import static jdk.vm.ci.sparc.SPARC.FPUd;
import static jdk.vm.ci.sparc.SPARC.FPUs;
import static jdk.vm.ci.sparc.SPARC.g0;
import static jdk.vm.ci.sparc.SPARC.g2;
import static jdk.vm.ci.sparc.SPARC.g5;
import static jdk.vm.ci.sparc.SPARC.g7;
import static jdk.vm.ci.sparc.SPARC.o7;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.debug.GraalError;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.sparc.SPARC;
import jdk.vm.ci.sparc.SPARC.CPUFeature;
import jdk.vm.ci.sparc.SPARCKind;

/**
 * This class implements an assembler that can encode most SPARC instructions.
 */
public abstract class SPARCAssembler extends Assembler {

    /**
     * Constructs an assembler for the SPARC architecture.
     */
    public SPARCAssembler(TargetDescription target) {
        super(target);
    }

    /**
     * Size of an SPARC assembler instruction in Bytes.
     */
    public static final int INSTRUCTION_SIZE = 4;

    /**
     * Size in bytes which are cleared by stxa %g0, [%rd] ASI_ST_BLKINIT_PRIMARY.
     */
    public static final int BLOCK_ZERO_LENGTH = 64;

    public static final int CCR_ICC_SHIFT = 0;
    public static final int CCR_XCC_SHIFT = 4;
    public static final int CCR_V_SHIFT = 1;

    public static final int MEMBAR_LOAD_LOAD = 1;
    public static final int MEMBAR_STORE_LOAD = 2;
    public static final int MEMBAR_LOAD_STORE = 3;
    public static final int MEMBAR_STORE_STORE = 4;

    private static final Ops[] OPS;
    private static final Op2s[] OP2S;
    private static final Op3s[][] OP3S;

    private ArrayList<Integer> delaySlotOptimizationPoints = new ArrayList<>(5);

    static {
        Ops[] ops = Ops.values();
        OPS = new Ops[ops.length];
        for (Ops op : ops) {
            OPS[op.value] = op;
        }
        Op2s[] op2s = Op2s.values();
        OP2S = new Op2s[op2s.length];
        for (Op2s op2 : op2s) {
            OP2S[op2.value] = op2;
        }
        OP3S = new Op3s[2][64];
        for (Op3s op3 : Op3s.values()) {
            if (op3.value >= 1 << 6) {
                throw new RuntimeException("Error " + op3 + " " + op3.value);
            }
            OP3S[op3.op.value & 1][op3.value] = op3;
        }
    }

    public enum Ops {
        // @formatter:off
        BranchOp(0b00),
        CallOp(0b01),
        ArithOp(0b10),
        LdstOp(0b11);
        // @formatter:on

        private final int value;

        Ops(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public boolean appliesTo(int instructionWord) {
            int opShift = 30;
            return (instructionWord >>> opShift) == value;
        }
    }

    public enum Op2s {
        // Checkstyle: stop
        // @formatter:off
        Illtrap(0b000),
        Bpr    (0b011),
        Fb     (0b110),
        Fbp    (0b101),
        Br     (0b010),
        Bp     (0b001),
        Cb     (0b111),
        Sethi  (0b100);
        // @formatter:on
        // Checkstyle: resume

        private final int value;

        Op2s(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static Op2s byValue(int value) {
            return OP2S[value];
        }
    }

    private static final int COMMUTATIVE = 1;
    private static final int BINARY = 2;
    private static final int UNARY = 4;
    private static final int VOID_IN = 8;

    public enum Op3s {
        // Checkstyle: stop
        // @formatter:off
        Add(0x00, "add", ArithOp, BINARY | COMMUTATIVE),
        And(0x01, "and", ArithOp, BINARY | COMMUTATIVE),
        Or(0x02, "or", ArithOp, BINARY | COMMUTATIVE),
        Xor(0x03, "xor", ArithOp, BINARY | COMMUTATIVE),
        Sub(0x04, "sub", ArithOp, BINARY),
        Andn(0x05, "andn", ArithOp, BINARY | COMMUTATIVE),
        Orn(0x06, "orn", ArithOp, BINARY | COMMUTATIVE),
        Xnor(0x07, "xnor", ArithOp, BINARY | COMMUTATIVE),
        Addc(0x08, "addc", ArithOp, BINARY | COMMUTATIVE),
        Mulx(0x09, "mulx", ArithOp, BINARY | COMMUTATIVE),
        Umul(0x0A, "umul", ArithOp, BINARY | COMMUTATIVE),
        Smul(0x0B, "smul", ArithOp, BINARY | COMMUTATIVE),
        Subc(0x0C, "subc", ArithOp, BINARY),
        Udivx(0x0D, "udivx", ArithOp, BINARY),
        Udiv(0x0E, "udiv", ArithOp, BINARY),
        Sdiv(0x0F, "sdiv", ArithOp, BINARY),

        Addcc(0x10, "addcc", ArithOp, BINARY | COMMUTATIVE),
        Andcc(0x11, "andcc", ArithOp, BINARY | COMMUTATIVE),
        Orcc(0x12, "orcc", ArithOp, BINARY | COMMUTATIVE),
        Xorcc(0x13, "xorcc", ArithOp, BINARY | COMMUTATIVE),
        Subcc(0x14, "subcc", ArithOp, BINARY),
        Andncc(0x15, "andncc", ArithOp, BINARY | COMMUTATIVE),
        Orncc(0x16, "orncc", ArithOp, BINARY | COMMUTATIVE),
        Xnorcc(0x17, "xnorcc", ArithOp, BINARY | COMMUTATIVE),
        Addccc(0x18, "addccc", ArithOp, BINARY | COMMUTATIVE),

        Umulcc(0x1A, "umulcc", ArithOp, BINARY | COMMUTATIVE),
        Smulcc(0x1B, "smulcc", ArithOp, BINARY | COMMUTATIVE),
        Subccc(0x1C, "subccc", ArithOp, BINARY),
        Udivcc(0x1E, "udivcc", ArithOp, BINARY),
        Sdivcc(0x1F, "sdivcc", ArithOp, BINARY),

        Mulscc(0x24, "mulscc", ArithOp, BINARY | COMMUTATIVE),
        Sll(0x25, "sll", ArithOp, BINARY),
        Sllx(0x25, "sllx", ArithOp, BINARY),
        Srl(0x26, "srl", ArithOp, BINARY),
        Srlx(0x26, "srlx", ArithOp, BINARY),
        Sra(0x27, "srax", ArithOp, BINARY),
        Srax(0x27, "srax", ArithOp, BINARY),
        Membar(0x28, "membar", ArithOp),

        Flushw(0x2B, "flushw", ArithOp),
        Movcc(0x2C, "movcc", ArithOp),
        Sdivx(0x2D, "sdivx", ArithOp, BINARY),
        Popc(0x2E, "popc", ArithOp, UNARY),
        Movr(0x2F, "movr", ArithOp, BINARY),

        Fpop1(0b11_0100, "fpop1", ArithOp),
        Fpop2(0b11_0101, "fpop2", ArithOp),
        Impdep1(0b11_0110, "impdep1", ArithOp),
        Impdep2(0b11_0111, "impdep2", ArithOp),
        Jmpl(0x38, "jmpl", ArithOp),
        Rett(0x39, "rett", ArithOp),
        Trap(0x3a, "trap", ArithOp),
        Flush(0x3b, "flush", ArithOp),
        Save(0x3c, "save", ArithOp),
        Restore(0x3d, "restore", ArithOp),
        Retry(0x3e, "retry", ArithOp),


        Casa(0b111100, "casa", LdstOp),
        Casxa(0b111110, "casxa", LdstOp),
        Prefetch(0b101101, "prefetch", LdstOp),
        Prefetcha(0b111101, "prefetcha", LdstOp),

        Lduw  (0b00_0000, "lduw", LdstOp),
        Ldub  (0b00_0001, "ldub", LdstOp),
        Lduh  (0b00_0010, "lduh", LdstOp),
        Stw   (0b00_0100, "stw", LdstOp),
        Stb   (0b00_0101, "stb", LdstOp),
        Sth   (0b00_0110, "sth", LdstOp),
        Ldsw  (0b00_1000, "ldsw", LdstOp),
        Ldsb  (0b00_1001, "ldsb", LdstOp),
        Ldsh  (0b00_1010, "ldsh", LdstOp),
        Ldx   (0b00_1011, "ldx", LdstOp),
        Stx   (0b00_1110, "stx", LdstOp),

        Ldf   (0b10_0000, "ldf", LdstOp),
        Ldfsr (0b10_0001, "ldfsr", LdstOp),
        Ldaf  (0b10_0010, "ldaf", LdstOp),
        Lddf  (0b10_0011, "lddf", LdstOp),
        Stf   (0b10_0100, "stf", LdstOp),
        Stfsr (0b10_0101, "stfsr", LdstOp),
        Staf  (0b10_0110, "staf", LdstOp),
        Stdf  (0b10_0111, "stdf", LdstOp),

        Stba  (0b01_0101, "stba", LdstOp),
        Stha  (0b01_0110, "stha", LdstOp),
        Stwa  (0b01_0100, "stwa", LdstOp),
        Stxa  (0b01_1110, "stxa", LdstOp),

        Ldsba  (0b01_1001, "ldsba", LdstOp),
        Ldsha  (0b01_1010, "ldsha", LdstOp),
        Ldswa  (0b01_1000, "ldswa", LdstOp),
        Lduba  (0b01_0001, "lduba", LdstOp),
        Lduha  (0b01_0010, "lduha", LdstOp),
        Lduwa (0b01_0000, "lduwa", LdstOp),

        Ldxa  (0b01_1011, "ldxa", LdstOp),

        Rd    (0b10_1000, "rd", ArithOp),
        Wr    (0b11_0000, "wr", ArithOp),

        Tcc(0b11_1010, "tcc", ArithOp);

        // @formatter:on
        // Checkstyle: resume

        private final int value;
        private final String operator;
        private final Ops op;
        private final int flags;

        Op3s(int value, String name, Ops op) {
            this(value, name, op, 0);
        }

        Op3s(int value, String name, Ops op, int flags) {
            this.value = value;
            this.operator = name;
            this.op = op;
            this.flags = flags;
        }

        public int getValue() {
            return value;
        }

        public String getOperator() {
            return operator;
        }

        public boolean throwsException() {
            if (op == LdstOp) {
                return true;
            }
            switch (this) {
                case Udiv:
                case Udivx:
                case Sdiv:
                case Sdivx:
                case Udivcc:
                case Sdivcc:
                    return true;
                default:
                    return false;
            }
        }

        public boolean isBinary() {
            return (flags & BINARY) != 0;
        }

        public boolean isUnary() {
            return (flags & UNARY) != 0;
        }

        public boolean isCommutative() {
            return (flags & COMMUTATIVE) != 0;
        }
    }

    public enum Opfs {
        // @formatter:off

        Fmovs(0b0_0000_0001, "fmovs", Fpop1, UNARY),
        Fmovd(0b0_0000_0010, "fmovd", Fpop1, UNARY),
        Fmovq(0b0_0000_0011, "fmovq", Fpop1, UNARY),
        Fnegs(0x05, "fnegs", Fpop1, UNARY),
        Fnegd(0x06, "fnegd", Fpop1, UNARY),
        Fnegq(0x07, "fnegq", Fpop1, UNARY),
        Fabss(0x09, "fabss", Fpop1, UNARY),
        Fabsd(0x0A, "fabsd", Fpop1, UNARY),
        Fabsq(0x0B, "fabsq", Fpop1, UNARY),

        // start VIS1
        Fpadd32(0x52, "fpadd32", Impdep1, BINARY | COMMUTATIVE),
        Fzerod(0x60, "fzerod", Impdep1, VOID_IN),
        Fzeros(0x61, "fzeros", Impdep1, VOID_IN),
        Fsrc2d(0x78, "fsrc2d", Impdep1, UNARY),
        Fsrc2s(0x79, "fsrc2s", Impdep1, UNARY),
        // end VIS1

        // start VIS3
        Movdtox(0x110, "movdtox", Impdep1, UNARY),
        Movstouw(0x111, "movstouw", Impdep1, UNARY),
        Movstosw(0x113, "movstosw", Impdep1, UNARY),
        Movxtod(0x118, "movxtod", Impdep1, UNARY),
        Movwtos(0b1_0001_1001, "movwtos", Impdep1, UNARY),
        UMulxhi(0b0_0001_0110, "umulxhi", Impdep1, BINARY | COMMUTATIVE),
        // end VIS3

        Fadds(0x41, "fadds", Fpop1, BINARY | COMMUTATIVE),
        Faddd(0x42, "faddd", Fpop1, BINARY | COMMUTATIVE),
        Fsubs(0x45, "fsubs", Fpop1, BINARY),
        Fsubd(0x46, "fsubd", Fpop1, BINARY),
        Fmuls(0x49, "fmuls", Fpop1, BINARY | COMMUTATIVE),
        Fmuld(0x4A, "fmuld", Fpop1, BINARY | COMMUTATIVE),
        Fdivs(0x4D, "fdivs", Fpop1, BINARY),
        Fdivd(0x4E, "fdivd", Fpop1, BINARY),

        Fsqrts(0x29, "fsqrts", Fpop1, UNARY),
        Fsqrtd(0x2A, "fsqrtd", Fpop1, UNARY),

        Fsmuld(0x69, "fsmuld", Fpop1, BINARY | COMMUTATIVE),

        Fstoi(0xD1, "fstoi", Fpop1, UNARY),
        Fdtoi(0xD2, "fdtoi", Fpop1, UNARY),
        Fstox(0x81, "fstox", Fpop1, UNARY),
        Fdtox(0x82, "fdtox", Fpop1, UNARY),
        Fxtos(0x84, "fxtos", Fpop1, UNARY),
        Fxtod(0x88, "fxtod", Fpop1, UNARY),
        Fitos(0xC4, "fitos", Fpop1, UNARY),
        Fdtos(0xC6, "fdtos", Fpop1, UNARY),
        Fitod(0xC8, "fitod", Fpop1, UNARY),
        Fstod(0xC9, "fstod", Fpop1, UNARY),


        Fcmps(0x51, "fcmps", Fpop2, BINARY),
        Fcmpd(0x52, "fcmpd", Fpop2, BINARY);

        // @formatter:on

        private final int value;
        private final String operator;
        private final Op3s op3;
        private final int flags;

        Opfs(int value, String op, Op3s op3, int flags) {
            this.value = value;
            this.operator = op;
            this.op3 = op3;
            this.flags = flags;
        }

        public int getValue() {
            return value;
        }

        public String getOperator() {
            return operator;
        }

        public boolean isBinary() {
            return (flags & BINARY) != 0;
        }

        public boolean isUnary() {
            return (flags & UNARY) != 0;
        }

        public boolean isCommutative() {
            return (flags & COMMUTATIVE) != 0;
        }
    }

    public enum OpfLow {
        Fmovscc(0b00_0001, "fmovscc", Fpop2),
        Fmovdcc(0b00_0010, "fmovdcc", Fpop2);

        private final int value;
        private final String operator;
        private final Op3s op3;

        OpfLow(int value, String op, Op3s op3) {
            this.value = value;
            this.operator = op;
            this.op3 = op3;
        }

        @Override
        public String toString() {
            return operator;
        }
    }

    public enum Annul {
        ANNUL(1),
        NOT_ANNUL(0);
        public final int flag;

        Annul(int flag) {
            this.flag = flag;
        }
    }

    public enum BranchPredict {
        PREDICT_TAKEN(1),
        PREDICT_NOT_TAKEN(0);
        public final int flag;

        BranchPredict(int flag) {
            this.flag = flag;
        }
    }

    public enum MembarMask {
        // @formatter:off

        StoreStore(1 << 3, "storestore"),
        LoadStore(1 << 2, "loadstore"),
        StoreLoad(1 << 1, "storeload"),
        LoadLoad(1 << 0, "loadload"),
        Sync(1 << 6, "sync"),
        MemIssue(1 << 5, "memissue"),
        LookAside(1 << 4, "lookaside");

        // @formatter:on

        private final int value;
        private final String operator;

        MembarMask(int value, String op) {
            this.value = value;
            this.operator = op;
        }

        public int getValue() {
            return value | 0x2000;
        }

        public String getOperator() {
            return operator;
        }
    }

    /**
     * Condition Codes to use for instruction.
     */
    public enum CC {
        // @formatter:off
        /**
         * Condition is considered as 32bit operation condition.
         */
        Icc(0b00, "icc", false),
        /**
         * Condition is considered as 64bit operation condition.
         */
        Xcc(0b10, "xcc", false),
        Fcc0(0b00, "fcc0", true),
        Fcc1(0b01, "fcc1", true),
        Fcc2(0b10, "fcc2", true),
        Fcc3(0b11, "fcc3", true);

        // @formatter:on

        private final int value;
        private final String operator;
        private boolean isFloat;

        CC(int value, String op, boolean isFloat) {
            this.value = value;
            this.operator = op;
            this.isFloat = isFloat;
        }

        public int getValue() {
            return value;
        }

        public String getOperator() {
            return operator;
        }

        public static CC forKind(PlatformKind kind) {
            if (kind.equals(SPARCKind.XWORD)) {
                return Xcc;
            } else if (kind.equals(SPARCKind.WORD)) {
                return Icc;
            } else if (kind.equals(SPARCKind.SINGLE) || kind.equals(SPARCKind.DOUBLE)) {
                return Fcc0;
            } else {
                throw new IllegalArgumentException("Unknown kind: " + kind);
            }
        }
    }

    public enum ConditionFlag {
        // @formatter:off

        // for FBfcc & FBPfcc instruction
        F_Never(0, "f_never"),
        F_NotEqual(1, "f_notEqual"),
        F_LessOrGreater(2, "f_lessOrGreater"),
        F_UnorderedOrLess(3, "f_unorderedOrLess"),
        F_Less(4, "f_less"),
        F_UnorderedOrGreater(5, "f_unorderedOrGreater"),
        F_Greater(6, "f_greater"),
        F_Unordered(7, "f_unordered"),
        F_Always(8, "f_always"),
        F_Equal(9, "f_equal"),
        F_UnorderedOrEqual(10, "f_unorderedOrEqual"),
        F_GreaterOrEqual(11, "f_greaterOrEqual"),
        F_UnorderedGreaterOrEqual(12, "f_unorderedGreaterOrEqual"),
        F_LessOrEqual(13, "f_lessOrEqual"),
        F_UnorderedOrLessOrEqual(14, "f_unorderedOrLessOrEqual"),
        F_Ordered(15, "f_ordered"),

        // for integers
        Never(0, "never"),
        Equal(1, "equal", true),
        Zero(1, "zero"),
        LessEqual(2, "lessEqual", true),
        Less(3, "less", true),
        LessEqualUnsigned(4, "lessEqualUnsigned", true),
        LessUnsigned(5, "lessUnsigned", true),
        CarrySet(5, "carrySet"),
        Negative(6, "negative", true),
        OverflowSet(7, "overflowSet", true),
        Always(8, "always"),
        NotEqual(9, "notEqual", true),
        NotZero(9, "notZero"),
        Greater(10, "greater", true),
        GreaterEqual(11, "greaterEqual", true),
        GreaterUnsigned(12, "greaterUnsigned", true),
        GreaterEqualUnsigned(13, "greaterEqualUnsigned", true),
        CarryClear(13, "carryClear"),
        Positive(14, "positive", true),
        OverflowClear(15, "overflowClear", true);

        // @formatter:on

        private final int value;
        private final String operator;
        private boolean forCBcond = false;

        ConditionFlag(int value, String op) {
            this(value, op, false);
        }

        ConditionFlag(int value, String op, boolean cbcond) {
            this.value = value;
            this.operator = op;
            this.forCBcond = cbcond;
        }

        public boolean isCBCond() {
            return forCBcond;
        }

        public int getValue() {
            return value;
        }

        public String getOperator() {
            return operator;
        }

        public ConditionFlag negate() {
            //@formatter:off
            switch (this) {
                case F_Never                  : return F_Always;
                case F_Always                 : return F_Never;
                case F_NotEqual               : return F_Equal;
                case F_Equal                  : return F_NotEqual;
                case F_LessOrGreater          : return F_UnorderedOrEqual;
                case F_UnorderedOrEqual       : return F_LessOrGreater;
                case F_Less                   : return F_UnorderedGreaterOrEqual;
                case F_UnorderedGreaterOrEqual: return F_Less;
                case F_LessOrEqual            : return F_UnorderedOrGreater;
                case F_UnorderedOrGreater     : return F_LessOrEqual;
                case F_Greater                : return F_UnorderedOrLessOrEqual;
                case F_UnorderedOrLessOrEqual : return F_Greater;
                case F_GreaterOrEqual         : return F_UnorderedOrLess;
                case F_UnorderedOrLess        : return F_GreaterOrEqual;
                case F_Unordered              : return F_Ordered;
                case F_Ordered                : return F_Unordered;
                case Never                    : return Always;
                case Always                   : return Never;
                case Equal                    : return NotEqual;
                case NotEqual                 : return Equal;
                case Zero                     : return NotZero;
                case NotZero                  : return Zero;
                case LessEqual                : return Greater;
                case Greater                  : return LessEqual;
                case Less                     : return GreaterEqual;
                case GreaterEqual             : return Less;
                case LessEqualUnsigned        : return GreaterUnsigned;
                case GreaterUnsigned          : return LessEqualUnsigned;
                case LessUnsigned             : return GreaterEqualUnsigned;
                case GreaterEqualUnsigned     : return LessUnsigned;
                case CarrySet                 : return CarryClear;
                case CarryClear               : return CarrySet;
                case Negative                 : return Positive;
                case Positive                 : return Negative;
                case OverflowSet              : return OverflowClear;
                case OverflowClear            : return OverflowSet;
                default:
                    throw new InternalError();
            }
            //@formatter:on
        }

        public ConditionFlag mirror() {
            switch (this) {
            //@formatter:off
                case F_Less                   : return F_Greater;
                case F_Greater                : return F_Less;
                case F_LessOrEqual            : return F_GreaterOrEqual;
                case F_UnorderedGreaterOrEqual: return F_UnorderedOrLessOrEqual;
                case F_UnorderedOrGreater     : return F_UnorderedOrLess;
                case F_UnorderedOrLessOrEqual : return F_UnorderedGreaterOrEqual;
                case F_GreaterOrEqual         : return F_LessOrEqual;
                case F_UnorderedOrLess        : return F_UnorderedOrGreater;
                case LessEqual                : return GreaterEqual;
                case Greater                  : return Less;
                case Less                     : return Greater;
                case GreaterEqual             : return LessEqual;
                case LessEqualUnsigned        : return GreaterEqualUnsigned;
                case GreaterUnsigned          : return LessUnsigned;
                case LessUnsigned             : return GreaterUnsigned;
                case GreaterEqualUnsigned     : return LessEqualUnsigned;
                default:
                    return this;
                //@formatter:on
            }
        }

    }

    public enum RCondition {
        // @formatter:off

        Rc_z(0b001, "rc_z"),
        Rc_lez(0b010, "rc_lez"),
        Rc_lz(0b011, "rc_lz"),
        Rc_nz(0b101, "rc_nz"),
        Rc_gz(0b110, "rc_gz"),
        Rc_gez(0b111, "rc_gez"),
        Rc_last(Rc_gez.getValue(), "rc_last");

        // @formatter:on

        private final int value;
        private final String operator;

        RCondition(int value, String op) {
            this.value = value;
            this.operator = op;
        }

        public int getValue() {
            return value;
        }

        public String getOperator() {
            return operator;
        }
    }

    /**
     * Represents the <b>Address Space Identifier</b> defined in the SPARC architecture.
     */
    public enum Asi {
        // @formatter:off

        INVALID(-1),
        ASI_PRIMARY(0x80),
        ASI_PRIMARY_NOFAULT(0x82),
        ASI_PRIMARY_LITTLE(0x88),
        // Block initializing store
        ASI_ST_BLKINIT_PRIMARY(0xE2),
        // Most-Recently-Used (MRU) BIS variant
        ASI_ST_BLKINIT_MRU_PRIMARY(0xF2);

        // @formatter:on

        private final int value;

        Asi(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public boolean isValid() {
            return value != INVALID.getValue();
        }
    }

    public enum Fcn {
        SeveralWritesAndPossiblyReads(2),
        SeveralReadsWeak(0),
        OneRead(1),
        OneWrite(3),
        Page(4),
        NearestUnifiedCache(17),
        SeveralReadsStrong(20),
        OneReadStrong(21),
        SeveralWritesAndPossiblyReadsStrong(22),
        OneWriteStrong(23);

        private final int value;

        Fcn(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * Specifies various bit fields used in SPARC instructions.
     */
    @SuppressWarnings("unused")
    public abstract static class BitSpec {
        private static final BitSpec op = new ContinousBitSpec(31, 30, "op");
        private static final BitSpec op2 = new ContinousBitSpec(24, 22, "op2");
        private static final BitSpec op3 = new ContinousBitSpec(24, 19, "op3");
        private static final BitSpec opf = new ContinousBitSpec(13, 5, "opf");
        private static final BitSpec opfLow = new ContinousBitSpec(10, 5, "opfLow");
        private static final BitSpec opfCC = new ContinousBitSpec(13, 11, "opfCC");
        private static final BitSpec opfCond = new ContinousBitSpec(17, 14, "opfCond");
        private static final BitSpec rd = new ContinousBitSpec(29, 25, "rd");
        private static final BitSpec rs1 = new ContinousBitSpec(18, 14, "rs1");
        private static final BitSpec rs2 = new ContinousBitSpec(4, 0, "rs2");
        private static final BitSpec simm13 = new ContinousBitSpec(12, 0, true, "simm13");
        private static final BitSpec shcnt32 = new ContinousBitSpec(4, 0, "shcnt32");
        private static final BitSpec shcnt64 = new ContinousBitSpec(5, 0, "shcnt64");
        private static final BitSpec imm22 = new ContinousBitSpec(21, 0, "imm22");
        private static final BitSpec immAsi = new ContinousBitSpec(12, 5, "immASI");
        private static final BitSpec i = new ContinousBitSpec(13, 13, "i");
        private static final BitSpec disp19 = new ContinousBitSpec(18, 0, true, "disp19");
        private static final BitSpec disp22 = new ContinousBitSpec(21, 0, true, "disp22");
        private static final BitSpec disp30 = new ContinousBitSpec(29, 0, true, "disp30");
        private static final BitSpec a = new ContinousBitSpec(29, 29, "a");
        private static final BitSpec p = new ContinousBitSpec(19, 19, "p");
        private static final BitSpec x = new ContinousBitSpec(12, 12, "x");
        private static final BitSpec cond = new ContinousBitSpec(28, 25, "cond");
        private static final BitSpec rcond = new ContinousBitSpec(27, 25, "rcond");
        private static final BitSpec cc = new ContinousBitSpec(21, 20, "cc");
        private static final BitSpec fcc = new ContinousBitSpec(26, 25, "cc");
        private static final BitSpec d16lo = new ContinousBitSpec(13, 0, "d16lo");
        private static final BitSpec d16hi = new ContinousBitSpec(21, 20, true, "d16hi");
        private static final BitSpec d16 = new CompositeBitSpec(d16hi, d16lo);
        // Movcc
        private static final BitSpec movccLo = new ContinousBitSpec(12, 11, "cc_lo");
        private static final BitSpec movccHi = new ContinousBitSpec(18, 18, "cc_hi");
        private static final BitSpec movccCond = new ContinousBitSpec(17, 14, "cond");
        private static final BitSpec simm11 = new ContinousBitSpec(10, 0, true, "simm11");

        // CBCond
        private static final BitSpec cLo = new ContinousBitSpec(27, 25, "cLo");
        private static final BitSpec cHi = new ContinousBitSpec(29, 29, "cHi");
        private static final BitSpec c = new CompositeBitSpec(cHi, cLo);
        private static final BitSpec cbcond = new ContinousBitSpec(28, 28, "cbcond");
        private static final BitSpec cc2 = new ContinousBitSpec(21, 21, "cc2");
        private static final BitSpec d10Lo = new ContinousBitSpec(12, 5, "d10Lo");
        private static final BitSpec d10Hi = new ContinousBitSpec(20, 19, true, "d10Hi");
        private static final BitSpec d10 = new CompositeBitSpec(d10Hi, d10Lo);
        private static final BitSpec simm5 = new ContinousBitSpec(4, 0, true, "simm5");

        protected final boolean signExtend;

        public BitSpec(boolean signExtend) {
            super();
            this.signExtend = signExtend;
        }

        public final boolean isSignExtend() {
            return signExtend;
        }

        public abstract int setBits(int word, int value);

        public abstract int getBits(int word);

        public abstract int getWidth();

        public abstract boolean valueFits(int value);
    }

    public static final class ContinousBitSpec extends BitSpec {
        private final int hiBit;
        private final int lowBit;
        private final int width;
        private final int mask;
        private final String name;

        public ContinousBitSpec(int hiBit, int lowBit, String name) {
            this(hiBit, lowBit, false, name);
        }

        public ContinousBitSpec(int hiBit, int lowBit, boolean signExt, String name) {
            super(signExt);
            this.hiBit = hiBit;
            this.lowBit = lowBit;
            this.width = hiBit - lowBit + 1;
            mask = ((1 << width) - 1) << lowBit;
            this.name = name;
        }

        @Override
        public int setBits(int word, int value) {
            assert valueFits(value) : String.format("Value 0x%x for field %s does not fit.", value, this);
            return (word & ~mask) | ((value << lowBit) & mask);
        }

        @Override
        public int getBits(int word) {
            if (signExtend) {
                return ((word & mask) << (31 - hiBit)) >> (32 - width);
            } else {
                return (word & mask) >>> lowBit;
            }
        }

        @Override
        public int getWidth() {
            return width;
        }

        @Override
        public String toString() {
            return String.format("%s [%d:%d]", name, hiBit, lowBit);
        }

        @Override
        public boolean valueFits(int value) {
            if (signExtend) {
                return isSimm(value, getWidth());
            } else {
                return isImm(value, getWidth());
            }
        }
    }

    public static final class CompositeBitSpec extends BitSpec {
        private final BitSpec left;
        private final int leftWidth;
        private final BitSpec right;
        private final int rightWidth;
        private final int width;

        public CompositeBitSpec(BitSpec left, BitSpec right) {
            super(left.isSignExtend());
            assert !right.isSignExtend() : String.format("Right field %s must not be sign extended", right);
            this.left = left;
            this.leftWidth = left.getWidth();
            this.right = right;
            this.rightWidth = right.getWidth();
            this.width = leftWidth + rightWidth;
        }

        @Override
        public int getBits(int word) {
            int l = left.getBits(word);
            int r = right.getBits(word);
            return (l << rightWidth) | r;
        }

        @Override
        public int setBits(int word, int value) {
            int l = leftBits(value);
            int r = rightBits(value);
            return left.setBits(right.setBits(word, r), l);
        }

        private int leftBits(int value) {
            return getBits(value, width - 1, rightWidth, signExtend);
        }

        private int rightBits(int value) {
            return getBits(value, rightWidth - 1, 0, false);
        }

        @Override
        public int getWidth() {
            return width;
        }

        @Override
        public String toString() {
            return String.format("CompositeBitSpec[%s, %s]", left, right);
        }

        @Override
        public boolean valueFits(int value) {
            int l = leftBits(value);
            int r = rightBits(value);
            return left.valueFits(l) && right.valueFits(r);
        }

        private static int getBits(int inst, int hiBit, int lowBit, boolean signExtended) {
            int shifted = inst >> lowBit;
            if (signExtended) {
                return shifted;
            } else {
                return shifted & ((1 << (hiBit - lowBit + 1)) - 1);
            }
        }
    }

    public static class BitKey {
        private final BitSpec spec;
        private final int value;

        public BitKey(BitSpec spec, int value) {
            super();
            this.spec = spec;
            this.value = value;
        }

        @Override
        public String toString() {
            return String.format("BitKey %s=%s", spec, value);
        }
    }

    /**
     * Represents a prefix tree of {@link BitSpec} objects to find the most accurate SPARCOp.
     */
    public static final class BitKeyIndex {
        private final BitSpec spec;
        private final Map<Integer, BitKeyIndex> nodes;
        private SPARCOp op;

        public BitKeyIndex(SPARCOp op) {
            assert op != null;
            this.op = op;
            this.nodes = null;
            this.spec = null;
        }

        public BitKeyIndex(BitSpec spec) {
            assert spec != null;
            this.op = null;
            this.nodes = new HashMap<>(4);
            this.spec = spec;
        }

        /**
         * Adds operation to the index.
         *
         * @param keys Ordered by the importance
         * @param operation Operation represented by this list of keys
         */
        private void addOp(List<BitKey[]> keys, SPARCOp operation) {
            assert keys.size() > 0;
            BitKey[] firstKeys = keys.get(0);
            for (BitKey first : firstKeys) {
                assert first.spec.equals(spec) : first.spec + " " + spec;
                BitKeyIndex node;
                if (keys.size() == 1) {
                    if (nodes.containsKey(first.value)) {
                        node = nodes.get(first.value);
                        assert node.op == null : node + " " + keys;
                        node.op = operation;
                    } else {
                        assert !nodes.containsKey(first.value) : "Index must be unique. Existing key: " + nodes.get(first.value);
                        node = new BitKeyIndex(operation);
                    }
                } else {
                    node = nodes.get(first.value);
                    BitKey[] next = keys.get(1);
                    if (node == null) {
                        for (int i = 1; i < next.length; i++) {
                            assert next[i - 1].spec.equals(next[i].spec) : "All spec on this node must equal";
                        }
                        node = new BitKeyIndex(next[0].spec);
                    }
                    node.addOp(keys.subList(1, keys.size()), operation);
                }
                nodes.put(first.value, node);
            }
        }

        /**
         * Finds the best matching {@link SPARCOp} for this instruction.
         */
        public SPARCOp find(int inst) {
            if (nodes != null) {
                int key = spec.getBits(inst);
                BitKeyIndex sub = nodes.get(key);
                if (sub == null) {
                    if (op != null) {
                        return op;
                    } else {
                        throw new RuntimeException(String.format("%s 0x%x, 0x%x %s", spec, inst, key, nodes));
                    }
                }
                return sub.find(inst);
            } else {
                return this.op;
            }
        }

        @Override
        public String toString() {
            return this.op == null ? this.spec + ": " + this.nodes : this.op.toString();
        }
    }

    public static final Bpcc BPCC = new Bpcc(Op2s.Bp);
    public static final Bpcc FBPCC = new Bpcc(Op2s.Fbp);
    public static final CBCond CBCOND = new CBCond();
    public static final Bpr BPR = new Bpr();
    public static final Br BR = new Br();
    public static final Sethi SETHI = new Sethi();
    public static final FMOVcc FMOVSCC = new FMOVcc(OpfLow.Fmovscc);
    public static final FMOVcc FMOVDCC = new FMOVcc(OpfLow.Fmovdcc);
    public static final MOVicc MOVicc = new MOVicc();
    public static final OpfOp OPF = new OpfOp();
    public static final Op3Op OP3 = new Op3Op();
    public static final SPARCOp LDST = new SPARCOp(Ops.LdstOp);
    public static final SPARCOp BRANCH = new SPARCOp(Ops.BranchOp);
    public static final SPARCOp CALL = new SPARCOp(Ops.CallOp);
    private static final BitKeyIndex INDEX = new BitKeyIndex(BitSpec.op);

    static {
        for (SPARCOp op : SPARCOp.OPS) {
            INDEX.addOp(op.getKeys(), op);
        }
    }

    public static SPARCOp getSPARCOp(int inst) {
        return INDEX.find(inst);
    }

    /**
     * Represents a class of SPARC instruction and gives methods to modify its fields.
     */
    public static class SPARCOp {
        private final Ops op;
        private final BitKey opKey;
        private List<BitKey[]> keyFields;
        private static final List<SPARCOp> OPS = new ArrayList<>();

        public SPARCOp(Ops op) {
            super();
            this.op = op;
            this.opKey = new BitKey(BitSpec.op, op.value);
            OPS.add(this);
        }

        protected int setBits(int word) {
            return BitSpec.op.setBits(word, op.value);
        }

        public boolean match(int inst) {
            for (BitKey[] keys : keyFields) {
                for (BitKey k : keys) {
                    if (k.spec.getBits(inst) != k.value) {
                        return false;
                    }
                }
            }
            return true;
        }

        protected List<BitKey[]> getKeys() {
            if (keyFields == null) {
                keyFields = new ArrayList<>(4);
                keyFields.add(new BitKey[]{opKey});
            }
            return keyFields;
        }

        public Ops getOp(int inst) {
            return SPARCAssembler.OPS[BitSpec.op.getBits(inst)];
        }

        @Override
        public String toString() {
            String name = getClass().getName();
            name = name.substring(name.lastIndexOf(".") + 1);
            return name + "[op: " + op + "]";
        }
    }

    /**
     * Base class for control transfer operations; provides access to the disp field.
     */
    public abstract static class ControlTransferOp extends SPARCOp {
        private final Op2s op2;
        private final boolean delaySlot;
        private final BitSpec disp;
        private final BitKey[] op2Key;

        private ControlTransferOp(Ops op, Op2s op2, boolean delaySlot, BitSpec disp) {
            super(op);
            this.op2 = op2;
            this.delaySlot = delaySlot;
            this.disp = disp;
            this.op2Key = new BitKey[]{new BitKey(BitSpec.op2, op2.value)};
        }

        public boolean hasDelaySlot() {
            return delaySlot;
        }

        @Override
        protected int setBits(int word) {
            return BitSpec.op2.setBits(super.setBits(word), op2.value);
        }

        protected int setDisp(int inst, SPARCMacroAssembler masm, Label lab) {
            if (lab.isBound()) {
                int d = (lab.position() - masm.position()) / 4;
                return setDisp(inst, d);
            } else {
                masm.patchUnbound(lab);
                return inst;
            }
        }

        public int setDisp(int inst, int d) {
            assert this.match(inst);
            if (!isValidDisp(d)) {
                throw new PermanentBailoutException("Too large displacement 0x%x in field %s in instruction %s", d, this.disp, this);
            }
            return this.disp.setBits(inst, d);
        }

        public boolean isValidDisp(int d) {
            return this.disp.valueFits(d);
        }

        public int setAnnul(int inst, boolean a) {
            return BitSpec.a.setBits(inst, a ? 1 : 0);
        }

        @Override
        protected List<BitKey[]> getKeys() {
            List<BitKey[]> keys = super.getKeys();
            keys.add(op2Key);
            return keys;
        }

        public int getDisp(int inst) {
            return this.disp.getBits(inst);
        }

        public abstract boolean isAnnulable(int inst);

        public abstract boolean isConditional(int inst);
    }

    public static final class Bpcc extends ControlTransferOp {
        public Bpcc(Op2s op2) {
            super(Ops.BranchOp, op2, true, BitSpec.disp19);
        }

        public void emit(SPARCMacroAssembler masm, CC cc, ConditionFlag cf, Annul annul, BranchPredict p, Label lab) {
            int inst = setBits(0);
            inst = BitSpec.a.setBits(inst, annul.flag);
            inst = BitSpec.cond.setBits(inst, cf.value);
            inst = BitSpec.cc.setBits(inst, cc.value);
            inst = BitSpec.p.setBits(inst, p.flag);
            masm.insertNopAfterCBCond();
            masm.emitInt(setDisp(inst, masm, lab));
        }

        @Override
        public boolean isAnnulable(int inst) {
            return isConditional(inst);
        }

        @Override
        public boolean isConditional(int inst) {
            int cond = BitSpec.cond.getBits(inst);
            return cond != ConditionFlag.Always.value && cond != ConditionFlag.Never.value;
        }
    }

    public static final class Br extends ControlTransferOp {
        public Br() {
            super(Ops.BranchOp, Op2s.Br, true, BitSpec.disp22);
        }

        @Override
        public boolean isAnnulable(int inst) {
            return isConditional(inst);
        }

        @Override
        public boolean isConditional(int inst) {
            int cond = BitSpec.cond.getBits(inst);
            return cond != ConditionFlag.Always.value && cond != ConditionFlag.Never.value;
        }

        public void emit(SPARCMacroAssembler masm, ConditionFlag cond, Annul a, Label lab) {
            int inst = setBits(0);
            inst = BitSpec.cond.setBits(inst, cond.value);
            inst = BitSpec.a.setBits(inst, a.flag);
            masm.insertNopAfterCBCond();
            masm.emitInt(setDisp(inst, masm, lab));
        }
    }

    public static final class Bpr extends ControlTransferOp {
        private static final BitKey CBCOND_KEY = new BitKey(BitSpec.cbcond, 0);

        public Bpr() {
            super(Ops.BranchOp, Op2s.Bpr, true, BitSpec.d16);
        }

        public void emit(SPARCMacroAssembler masm, RCondition rcond, Annul a, BranchPredict p, Register rs1, Label lab) {
            int inst = setBits(0);
            inst = BitSpec.rcond.setBits(inst, rcond.value);
            inst = BitSpec.a.setBits(inst, a.flag);
            inst = BitSpec.p.setBits(inst, p.flag);
            inst = BitSpec.rs1.setBits(inst, rs1.encoding);
            masm.insertNopAfterCBCond();
            masm.emitInt(setDisp(inst, masm, lab));
        }

        @Override
        protected List<BitKey[]> getKeys() {
            List<BitKey[]> keys = super.getKeys();
            keys.add(new BitKey[]{CBCOND_KEY});
            return keys;
        }

        @Override
        public boolean isAnnulable(int inst) {
            return isConditional(inst);
        }

        @Override
        public boolean isConditional(int inst) {
            int cond = BitSpec.cond.getBits(inst);
            return cond != ConditionFlag.Always.value && cond != ConditionFlag.Never.value;
        }
    }

    public static final class CBCond extends ControlTransferOp {
        private static final BitKey CBCOND_KEY = new BitKey(BitSpec.cbcond, 1);

        private CBCond() {
            super(Ops.BranchOp, Op2s.Bpr, false, BitSpec.d10);
        }

        @Override
        protected List<BitKey[]> getKeys() {
            List<BitKey[]> keys = super.getKeys();
            keys.add(new BitKey[]{CBCOND_KEY});
            return keys;
        }

        public void emit(SPARCMacroAssembler masm, ConditionFlag cf, boolean cc2, Register rs1, Register rs2, Label lab) {
            int inst = setBits(0, cf, cc2, rs1);
            inst = BitSpec.rs2.setBits(inst, rs2.encoding);
            inst = BitSpec.i.setBits(inst, 0);
            masm.insertNopAfterCBCond();
            emit(masm, lab, inst);
        }

        public void emit(SPARCMacroAssembler masm, ConditionFlag cf, boolean cc2, Register rs1, int simm5, Label lab) {
            int inst = setBits(0, cf, cc2, rs1);
            inst = BitSpec.simm5.setBits(inst, simm5);
            inst = BitSpec.i.setBits(inst, 1);
            emit(masm, lab, inst);
        }

        private void emit(SPARCMacroAssembler masm, Label lab, int baseInst) {
            int inst = baseInst;
            masm.insertNopAfterCBCond();
            masm.emitInt(setDisp(inst, masm, lab));
        }

        private int setBits(int base, ConditionFlag cf, boolean cc2, Register rs1) {
            int inst = super.setBits(base);
            inst = BitSpec.rs1.setBits(inst, rs1.encoding);
            inst = BitSpec.cc2.setBits(inst, cc2 ? 1 : 0);
            inst = BitSpec.c.setBits(inst, cf.value);
            return BitSpec.cbcond.setBits(inst, 1);
        }

        @Override
        public boolean isAnnulable(int inst) {
            return false;
        }

        @Override
        public boolean isConditional(int inst) {
            return true;
        }
    }

    public static class Op2Op extends SPARCOp {
        private final Op2s op2;
        private final BitKey op2Key;

        public Op2Op(Ops op, Op2s op2) {
            super(op);
            this.op2 = op2;
            op2Key = new BitKey(BitSpec.op2, op2.value);
        }

        @Override
        protected int setBits(int word) {
            int result = super.setBits(word);
            return BitSpec.op2.setBits(result, op2.value);
        }

        @Override
        protected List<BitKey[]> getKeys() {
            List<BitKey[]> keys = super.getKeys();
            keys.add(new BitKey[]{op2Key});
            return keys;
        }
    }

    public static final class Sethi extends Op2Op {
        public Sethi() {
            super(Ops.BranchOp, Op2s.Sethi);
        }

        public static Register getRS1(int word) {
            int regNum = BitSpec.rs1.getBits(word);
            return SPARC.cpuRegisters.get(regNum);
        }

        public static int getImm22(int word) {
            return BitSpec.imm22.getBits(word);
        }

        public static boolean isNop(int inst) {
            return getRS1(inst).equals(g0) && getImm22(inst) == 0;
        }
    }

    public static final class Op3Op extends SPARCOp {
        public Op3Op() {
            super(ArithOp);
        }

        public Op3s getOp3(int inst) {
            assert match(inst);
            return OP3S[ArithOp.value & 1][BitSpec.op3.getBits(inst)];
        }

        public static void emit(SPARCMacroAssembler masm, Op3s opcode, Register rs1, Register rs2, Register rd) {
            int instruction = setBits(0, opcode, rs1, rd);
            instruction = BitSpec.rs2.setBits(instruction, rs2.encoding);
            instruction = BitSpec.i.setBits(instruction, 0);
            masm.emitInt(instruction);
        }

        public static void emit(SPARCMacroAssembler masm, Op3s opcode, Register rs1, int simm13, Register rd) {
            int instruction = setBits(0, opcode, rs1, rd);
            instruction = BitSpec.i.setBits(instruction, 1);
            BitSpec immediateSpec;
            switch (opcode) {
                case Sllx:
                case Srlx:
                case Srax:
                    immediateSpec = BitSpec.shcnt64;
                    break;
                case Sll:
                case Srl:
                case Sra:
                    immediateSpec = BitSpec.shcnt32;
                    break;
                default:
                    immediateSpec = BitSpec.simm13;
                    break;
            }
            instruction = immediateSpec.setBits(instruction, simm13);
            masm.emitInt(instruction);
        }

        private static int setBits(int instruction, Op3s op3, Register rs1, Register rd) {
            assert op3.op.equals(ArithOp);
            int tmp = BitSpec.op3.setBits(instruction, op3.value);
            switch (op3) {
                case Sllx:
                case Srlx:
                case Srax:
                    tmp = BitSpec.x.setBits(tmp, 1);
                    break;
            }
            tmp = BitSpec.op.setBits(tmp, op3.op.value);
            tmp = BitSpec.rd.setBits(tmp, rd.encoding);
            return BitSpec.rs1.setBits(tmp, rs1.encoding);
        }
    }

    /**
     * Used for interfacing FP and GP conditional move instructions.
     */
    public interface CMOV {
        void emit(SPARCMacroAssembler masm, ConditionFlag condition, CC cc, Register rs2, Register rd);

        void emit(SPARCMacroAssembler masm, ConditionFlag condition, CC cc, int simm11, Register rd);
    }

    public static final class MOVicc extends SPARCOp implements CMOV {
        private static final Op3s op3 = Movcc;

        public MOVicc() {
            super(ArithOp);
        }

        @Override
        public void emit(SPARCMacroAssembler masm, ConditionFlag condition, CC cc, Register rs2, Register rd) {
            int inst = setBits(0, condition, cc, rd);
            inst = BitSpec.rs2.setBits(inst, rs2.encoding());
            masm.emitInt(inst);
        }

        @Override
        public void emit(SPARCMacroAssembler masm, ConditionFlag condition, CC cc, int simm11, Register rd) {
            int inst = setBits(0, condition, cc, rd);
            inst = BitSpec.i.setBits(inst, 1);
            inst = BitSpec.simm11.setBits(inst, simm11);
            masm.emitInt(inst);
        }

        protected int setBits(int word, ConditionFlag condition, CC cc, Register rd) {
            int inst = super.setBits(word);
            inst = BitSpec.rd.setBits(inst, rd.encoding());
            inst = BitSpec.op3.setBits(inst, op3.value);
            inst = BitSpec.movccCond.setBits(inst, condition.value);
            inst = BitSpec.movccLo.setBits(inst, cc.value);
            return BitSpec.movccHi.setBits(inst, cc.isFloat ? 0 : 1);
        }

        @Override
        protected List<BitKey[]> getKeys() {
            List<BitKey[]> keys = super.getKeys();
            keys.add(new BitKey[]{new BitKey(BitSpec.op3, op3.value)});
            return keys;
        }
    }

    public static final class FMOVcc extends SPARCOp implements CMOV {
        private OpfLow opfLow;

        public FMOVcc(OpfLow opfLow) {
            super(ArithOp);
            this.opfLow = opfLow;
        }

        @Override
        public void emit(SPARCMacroAssembler masm, ConditionFlag condition, CC cc, Register rs2, Register rd) {
            int inst = setBits(0);
            inst = BitSpec.rd.setBits(inst, rd.encoding());
            inst = BitSpec.op3.setBits(inst, opfLow.op3.value);
            inst = BitSpec.opfCond.setBits(inst, condition.value);
            inst = BitSpec.opfCC.setBits(inst, cc.value);
            inst = BitSpec.opfLow.setBits(inst, opfLow.value);
            inst = BitSpec.rs2.setBits(inst, rs2.encoding());
            masm.emitInt(inst);
        }

        @Override
        public void emit(SPARCMacroAssembler masm, ConditionFlag condition, CC cc, int simm11, Register rd) {
            throw new IllegalArgumentException("FMOVCC cannot be used with immediate value");
        }

        @Override
        protected List<BitKey[]> getKeys() {
            List<BitKey[]> keys = super.getKeys();
            keys.add(new BitKey[]{new BitKey(BitSpec.op3, opfLow.op3.value)});
            keys.add(new BitKey[]{new BitKey(BitSpec.opfLow, opfLow.value)});
            return keys;
        }
    }

    public static final class OpfOp extends SPARCOp {

        private BitKey[] op3Keys;

        public OpfOp(BitKey... op3Keys) {
            super(ArithOp);
            this.op3Keys = op3Keys;
        }

        public OpfOp() {
            // @formatter:off
            this(new BitKey[]{
                            new BitKey(BitSpec.op3, Op3s.Fpop1.value),
                            new BitKey(BitSpec.op3, Op3s.Fpop2.value),
                            new BitKey(BitSpec.op3, Op3s.Impdep1.value),
                            new BitKey(BitSpec.op3, Op3s.Impdep2.value)});
            // @formatter:on
        }

        public static void emit(SPARCMacroAssembler masm, Opfs opf, Register rs1, Register rs2, Register rd) {
            int instruction = setBits(0, opf, rs1, rs2);
            instruction = BitSpec.rd.setBits(instruction, rd.encoding);
            instruction = BitSpec.i.setBits(instruction, 0);
            masm.emitInt(instruction);
        }

        public static void emitFcmp(SPARCMacroAssembler masm, Opfs opf, CC cc, Register rs1, Register rs2) {
            assert opf.equals(Opfs.Fcmpd) || opf.equals(Opfs.Fcmps) : opf;
            int instruction = setBits(0, opf, rs1, rs2);
            instruction = BitSpec.fcc.setBits(instruction, cc.value);
            masm.emitInt(instruction);
        }

        private static int setBits(int instruction, Opfs opf, Register rs1, Register rs2) {
            int tmp = BitSpec.op.setBits(instruction, opf.op3.op.value);
            tmp = BitSpec.op3.setBits(tmp, opf.op3.value);
            tmp = BitSpec.opf.setBits(tmp, opf.value);
            tmp = BitSpec.rs1.setBits(tmp, rs1.encoding);
            return BitSpec.rs2.setBits(tmp, rs2.encoding);
        }

        @Override
        protected List<BitKey[]> getKeys() {
            List<BitKey[]> keys = super.getKeys();
            keys.add(op3Keys);
            // @formatter:on
            return keys;
        }
    }

    public static boolean isCPURegister(Register... regs) {
        for (Register reg : regs) {
            if (!isCPURegister(reg)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isCPURegister(Register r) {
        return r.getRegisterCategory().equals(CPU);
    }

    public static boolean isGlobalRegister(Register r) {
        return isCPURegister(r) && g0.number <= r.number && r.number <= g7.number;
    }

    public static boolean isSingleFloatRegister(Register r) {
        return r.getRegisterCategory().equals(FPUs);
    }

    public static boolean isDoubleFloatRegister(Register r) {
        return r.getRegisterCategory().equals(FPUd);
    }

    public boolean hasFeature(CPUFeature feature) {
        return ((SPARC) this.target.arch).features.contains(feature);
    }

    public static final int simm(int x, int nbits) {
        // assert_signed_range(x, nbits);
        return x & ((1 << nbits) - 1);
    }

    public static final boolean isImm(int x, int nbits) {
        // assert_signed_range(x, nbits);
        return simm(x, nbits) == x;
    }

    /**
     * Minimum value for signed immediate ranges.
     */
    public static long minSimm(long nbits) {
        return -(1L << (nbits - 1));
    }

    /**
     * Maximum value for signed immediate ranges.
     */
    public static long maxSimm(long nbits) {
        return (1L << (nbits - 1)) - 1;
    }

    /**
     * Test if imm is within signed immediate range for nbits.
     */
    public static boolean isSimm(long imm, int nbits) {
        return minSimm(nbits) <= imm && imm <= maxSimm(nbits);
    }

    public static boolean isSimm10(long imm) {
        return isSimm(imm, 10);
    }

    public static boolean isSimm11(long imm) {
        return isSimm(imm, 11);
    }

    public static boolean isSimm11(JavaConstant constant) {
        return constant.isNull() || isSimm11(constant.asLong());
    }

    public static boolean isSimm5(JavaConstant constant) {
        return constant.isNull() || isSimm(constant.asLong(), 5);
    }

    public static boolean isSimm5(long imm) {
        return isSimm(imm, 5);
    }

    public static boolean isSimm13(int imm) {
        return isSimm(imm, 13);
    }

    public static boolean isSimm13(JavaConstant constant) {
        long bits;
        switch (constant.getJavaKind()) {
            case Double:
                bits = Double.doubleToRawLongBits(constant.asDouble());
                break;
            case Float:
                bits = Float.floatToRawIntBits(constant.asFloat());
                break;
            case Object:
                return constant.isNull();
            default:
                bits = constant.asLong();
                break;
        }
        return constant.isNull() || isSimm13(bits);
    }

    public static boolean isSimm13(long imm) {
        return NumUtil.isInt(imm) && isSimm(imm, 13);
    }

    public static boolean isWordDisp30(long imm) {
        return isSimm(imm, 30 + 2);
    }

    public static final int hi22(int x) {
        return x >>> 10;
    }

    public static final int lo10(int x) {
        return x & ((1 << 10) - 1);
    }

    // @formatter:off
    /**
     * Instruction format for Fmt00 instructions. This abstraction is needed as it
     * makes the patching easier later on.
     * <pre>
     * | 00  |    a   | op2 |               b                         |
     * |31 30|29    25|24 22|21                                      0|
     * </pre>
     */
    // @formatter:on
    protected void fmt00(int a, int op2, int b) {
        assert isImm(a, 5) && isImm(op2, 3) && isImm(b, 22) : String.format("a: 0x%x op2: 0x%x b: 0x%x", a, op2, b);
        int word = 0;
        BitSpec.op.setBits(word, 0);
        BitSpec.rd.setBits(word, a);
        BitSpec.op2.setBits(word, op2);
        BitSpec.imm22.setBits(word, b);
        emitInt(a << 25 | op2 << 22 | b);
    }

    private void op3(Op3s op3, Opfs opf, Register rs1, Register rs2, Register rd) {
        int b = opf.value << 5 | (rs2 == null ? 0 : rs2.encoding);
        fmt(op3.op.value, rd.encoding, op3.value, rs1 == null ? 0 : rs1.encoding, b);
    }

    protected void op3(Op3s op3, Register rs1, Register rs2, Register rd) {
        int b = rs2 == null ? 0 : rs2.encoding;
        int xBit = getXBit(op3);
        fmt(op3.op.value, rd.encoding, op3.value, rs1 == null ? 0 : rs1.encoding, b | xBit);
    }

    protected void op3(Op3s op3, Register rs1, int simm13, Register rd) {
        assert isSimm13(simm13) : simm13;
        int i = 1 << 13;
        int simm13WithX = simm13 | getXBit(op3);
        fmt(op3.op.value, rd.encoding, op3.value, rs1.encoding, i | simm13WithX & ((1 << 13) - 1));
    }

    public void insertNopAfterCBCond() {
        int pos = position() - INSTRUCTION_SIZE;
        if (pos == 0) {
            return;
        }
        int inst = getInt(pos);
        if (CBCOND.match(inst)) {
            nop();
        }
    }

    protected int patchUnbound(Label label) {
        label.addPatchAt(position());
        return 0;
    }

    // @formatter:off
    /**
     * NOP.
     * <pre>
     * | 00  |00000| 100 |                0                    |
     * |31 30|29 25|24 22|21                                  0|
     * </pre>
     */
    // @formatter:on
    public void nop() {
        emitInt(1 << 24);
    }

    public void sethi(int imm22, Register dst) {
        fmt00(dst.encoding, Op2s.Sethi.value, imm22);
    }

    // @formatter:off
    /**
     * Instruction format for calls.
     * <pre>
     * | 01  |                      disp30                             |
     * |31 30|29                                                      0|
     * </pre>
     *
     * @return Position of the call instruction
     */
    // @formatter:on
    public int call(int disp30) {
        assert isImm(disp30, 30);
        insertNopAfterCBCond();
        int before = position();
        int instr = 1 << 30;
        instr |= disp30;
        emitInt(instr);
        return before;
    }

    public void add(Register rs1, Register rs2, Register rd) {
        op3(Add, rs1, rs2, rd);
    }

    public void add(Register rs1, int simm13, Register rd) {
        op3(Add, rs1, simm13, rd);
    }

    public void addc(Register rs1, Register rs2, Register rd) {
        op3(Addc, rs1, rs2, rd);
    }

    public void addc(Register rs1, int simm13, Register rd) {
        op3(Addc, rs1, simm13, rd);
    }

    public void addcc(Register rs1, Register rs2, Register rd) {
        op3(Addcc, rs1, rs2, rd);
    }

    public void addcc(Register rs1, int simm13, Register rd) {
        op3(Addcc, rs1, simm13, rd);
    }

    public void and(Register rs1, Register rs2, Register rd) {
        op3(And, rs1, rs2, rd);
    }

    public void and(Register rs1, int simm13, Register rd) {
        op3(And, rs1, simm13, rd);
    }

    public void andcc(Register rs1, Register rs2, Register rd) {
        op3(Andcc, rs1, rs2, rd);
    }

    public void andcc(Register rs1, int simm13, Register rd) {
        op3(Andcc, rs1, simm13, rd);
    }

    public void andn(Register rs1, Register rs2, Register rd) {
        op3(Andn, rs1, rs2, rd);
    }

    public void andn(Register rs1, int simm13, Register rd) {
        op3(Andn, rs1, simm13, rd);
    }

    public void andncc(Register rs1, Register rs2, Register rd) {
        op3(Andncc, rs1, rs2, rd);
    }

    public void andncc(Register rs1, int simm13, Register rd) {
        op3(Andncc, rs1, simm13, rd);
    }

    public void movwtos(Register rs2, Register rd) {
        assert isSingleFloatRegister(rd) && isCPURegister(rs2) : String.format("%s %s", rs2, rd);
        op3(Impdep1, Movwtos, null, rs2, rd);
    }

    public void umulxhi(Register rs1, Register rs2, Register rd) {
        op3(Impdep1, UMulxhi, rs1, rs2, rd);
    }

    public void fdtos(Register rs2, Register rd) {
        assert isSingleFloatRegister(rd) && isDoubleFloatRegister(rs2) : String.format("%s %s", rs2, rd);
        op3(Fpop1, Fdtos, null, rs2, rd);
    }

    public void movstouw(Register rs2, Register rd) {
        assert isSingleFloatRegister(rs2) && isCPURegister(rd) : String.format("%s %s", rs2, rd);
        op3(Impdep1, Movstosw, null, rs2, rd);
    }

    public void movstosw(Register rs2, Register rd) {
        assert isSingleFloatRegister(rs2) && isCPURegister(rd) : String.format("%s %s", rs2, rd);
        op3(Impdep1, Movstosw, null, rs2, rd);
    }

    public void movdtox(Register rs2, Register rd) {
        assert isDoubleFloatRegister(rs2) && isCPURegister(rd) : String.format("%s %s", rs2, rd);
        op3(Impdep1, Movdtox, null, rs2, rd);
    }

    public void movxtod(Register rs2, Register rd) {
        assert isCPURegister(rs2) && isDoubleFloatRegister(rd) : String.format("%s %s", rs2, rd);
        op3(Impdep1, Movxtod, null, rs2, rd);
    }

    public void fadds(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Fadds, rs1, rs2, rd);
    }

    public void faddd(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Faddd, rs1, rs2, rd);
    }

    public void fdivs(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Fdivs, rs1, rs2, rd);
    }

    public void fdivd(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Fdivd, rs1, rs2, rd);
    }

    public void fmovs(Register rs2, Register rd) {
        op3(Fpop1, Fmovs, null, rs2, rd);
    }

    public void fmovd(Register rs2, Register rd) {
        op3(Fpop1, Fmovd, null, rs2, rd);
    }

    public void fsrc2s(Register rs2, Register rd) {
        op3(Impdep1, Fsrc2s, null, rs2, rd);
    }

    public void fsrc2d(Register rs2, Register rd) {
        op3(Impdep1, Fsrc2d, null, rs2, rd);
    }

    public void fmuls(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Fmuls, rs1, rs2, rd);
    }

    public void fsmuld(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Fsmuld, rs1, rs2, rd);
    }

    public void fmuld(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Fmuld, rs1, rs2, rd);
    }

    public void fnegs(Register rs2, Register rd) {
        op3(Fpop1, Fnegs, null, rs2, rd);
    }

    public void fnegd(Register rs2, Register rd) {
        op3(Fpop1, Fnegd, null, rs2, rd);
    }

    /**
     * Helper method to determine if the instruction needs the X bit set.
     */
    private static int getXBit(Op3s op3) {
        switch (op3) {
            case Sllx:
            case Srax:
            case Srlx:
                return 1 << 12;
            default:
                return 0;
        }
    }

    public void fstoi(Register rs2, Register rd) {
        op3(Fpop1, Fstoi, null, rs2, rd);
    }

    public void fstox(Register rs2, Register rd) {
        op3(Fpop1, Fstox, null, rs2, rd);
    }

    public void fdtox(Register rs2, Register rd) {
        op3(Fpop1, Fdtox, null, rs2, rd);
    }

    public void fstod(Register rs2, Register rd) {
        op3(Fpop1, Fstod, null, rs2, rd);
    }

    public void fdtoi(Register rs2, Register rd) {
        op3(Fpop1, Fdtoi, null, rs2, rd);
    }

    public void fitos(Register rs2, Register rd) {
        op3(Fpop1, Fitos, null, rs2, rd);
    }

    public void fitod(Register rs2, Register rd) {
        op3(Fpop1, Fitod, null, rs2, rd);
    }

    public void fxtos(Register rs2, Register rd) {
        op3(Fpop1, Fxtos, null, rs2, rd);
    }

    public void fxtod(Register rs2, Register rd) {
        op3(Fpop1, Fxtod, null, rs2, rd);
    }

    public void fzeros(Register rd) {
        op3(Impdep1, Fzeros, null, null, rd);
    }

    public void fzerod(Register rd) {
        op3(Impdep1, Fzerod, null, null, rd);
    }

    public void flushw() {
        op3(Flushw, g0, g0, g0);
    }

    public void fsqrtd(Register rs2, Register rd) {
        op3(Fpop1, Fsqrtd, null, rs2, rd);
    }

    public void fsqrts(Register rs2, Register rd) {
        op3(Fpop1, Fsqrts, null, rs2, rd);
    }

    public void fabss(Register rs2, Register rd) {
        op3(Fpop1, Fabss, null, rs2, rd);
    }

    public void fabsd(Register rs2, Register rd) {
        op3(Fpop1, Fabsd, null, rs2, rd);
    }

    public void fsubs(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Fsubs, rs1, rs2, rd);
    }

    public void fsubd(Register rs1, Register rs2, Register rd) {
        op3(Fpop1, Fsubd, rs1, rs2, rd);
    }

    // @formatter:off
    /**
     * Instruction format for fcmp.
     * <pre>
     * | 10  | --- |cc1|cc0|desc |   rs1   |   opf  | rs2 |
     * |31 30|29 27|26 |25 |24 19|18     14|13     5|4   0|
     * </pre>
     */
    // @formatter:on
    public void fcmp(CC cc, Opfs opf, Register rs1, Register rs2) {
        int a = cc.value;
        int b = opf.value << 5 | rs2.encoding;
        delaySlotOptimizationPoints.add(position());
        fmt10(a, Fpop2.value, rs1.encoding, b);
    }

    // @formatter:off
    /**
     * Instruction format for most arithmetic stuff.
     * <pre>
     * |  10 | rd  | op3 | rs1 |   b   |
     * |31 30|29 25|24 19|18 14|13    0|
     * </pre>
     */
    // @formatter:on
    protected void fmt10(int rd, int op3, int rs1, int b) {
        fmt(0b10, rd, op3, rs1, b);
    }

    // @formatter:off
    /**
     * Instruction format for most arithmetic stuff.
     * <pre>
     * |  op | rd  | op3 | rs1 |   b   |
     * |31 30|29 25|24 19|18 14|13    0|
     * </pre>
     */
    // @formatter:on
    protected void fmt(int op, int rd, int op3, int rs1, int b) {
        assert isImm(rd, 5) && isImm(op3, 6) && isImm(b, 14) : String.format("rd: 0x%x op3: 0x%x b: 0x%x", rd, op3, b);
        int instr = op << 30 | rd << 25 | op3 << 19 | rs1 << 14 | b;
        emitInt(instr);
    }

    public void illtrap(int const22) {
        fmt00(0, Op2s.Illtrap.value, const22);
    }

    public void jmpl(Register rs1, Register rs2, Register rd) {
        insertNopAfterCBCond();
        op3(Jmpl, rs1, rs2, rd);
    }

    /**
     * @return Position of the jmpl instruction
     */
    public int jmpl(Register rs1, int simm13, Register rd) {
        insertNopAfterCBCond();
        int before = position();
        op3(Jmpl, rs1, simm13, rd);
        return before;
    }

    public void fmovdcc(ConditionFlag cond, CC cc, Register rs2, Register rd) {
        fmovcc(cond, cc, rs2, rd, OpfLow.Fmovdcc.value);
    }

    public void fmovscc(ConditionFlag cond, CC cc, Register rs2, Register rd) {
        fmovcc(cond, cc, rs2, rd, OpfLow.Fmovscc.value);
    }

    private void fmovcc(ConditionFlag cond, CC cc, Register rs2, Register rd, int opfLow) {
        int opfCC = cc.value;
        int a = opfCC << 11 | opfLow << 5 | rs2.encoding;
        fmt10(rd.encoding, Fpop2.value, cond.value, a);
    }

    public void movcc(ConditionFlag conditionFlag, CC cc, Register rs2, Register rd) {
        movcc(conditionFlag, cc, 0, rs2.encoding, rd);
    }

    public void movcc(ConditionFlag conditionFlag, CC cc, int simm11, Register rd) {
        assert isSimm11(simm11);
        movcc(conditionFlag, cc, 1, simm11 & ((1 << 11) - 1), rd);
    }

    private void movcc(ConditionFlag conditionFlag, CC cc, int i, int imm, Register rd) {
        int cc01 = 0b11 & cc.value;
        int cc2 = cc.isFloat ? 0 : 1;
        int a = cc2 << 4 | conditionFlag.value;
        int b = cc01 << 11 | i << 13 | imm;
        fmt10(rd.encoding, Movcc.value, a, b);
    }

    public void mulx(Register rs1, Register rs2, Register rd) {
        op3(Mulx, rs1, rs2, rd);
    }

    public void mulx(Register rs1, int simm13, Register rd) {
        op3(Mulx, rs1, simm13, rd);
    }

    public void or(Register rs1, Register rs2, Register rd) {
        assert isCPURegister(rs1, rs2, rd) : String.format("%s %s %s", rs1, rs2, rd);
        op3(Or, rs1, rs2, rd);
    }

    public void or(Register rs1, int simm13, Register rd) {
        assert isCPURegister(rs1, rd) : String.format("%s %s", rs1, rd);
        op3(Or, rs1, simm13, rd);
    }

    public void popc(Register rs2, Register rd) {
        op3(Popc, g0, rs2, rd);
    }

    public void popc(int simm13, Register rd) {
        op3(Popc, g0, simm13, rd);
    }

    public void prefetch(SPARCAddress addr, Fcn fcn) {
        Register rs1 = addr.getBase();
        if (addr.getIndex().equals(Register.None)) {
            int dis = addr.getDisplacement();
            assert isSimm13(dis);
            fmt(Prefetch.op.value, fcn.value, Prefetch.value, rs1.encoding, 1 << 13 | dis & ((1 << 13) - 1));
        } else {
            Register rs2 = addr.getIndex();
            fmt(Prefetch.op.value, fcn.value, Prefetch.value, rs1.encoding, rs2.encoding);
        }
    }

    // A.44 Read State Register

    public void rdpc(Register rd) {
        op3(Rd, g5, g0, rd);
    }

    public void restore(Register rs1, Register rs2, Register rd) {
        op3(Restore, rs1, rs2, rd);
    }

    public static final int PC_RETURN_OFFSET = 8;

    public void save(Register rs1, Register rs2, Register rd) {
        op3(Save, rs1, rs2, rd);
    }

    public void save(Register rs1, int simm13, Register rd) {
        op3(Save, rs1, simm13, rd);
    }

    public void sdivx(Register rs1, Register rs2, Register rd) {
        op3(Sdivx, rs1, rs2, rd);
    }

    public void sdivx(Register rs1, int simm13, Register rd) {
        op3(Sdivx, rs1, simm13, rd);
    }

    public void udivx(Register rs1, Register rs2, Register rd) {
        op3(Udivx, rs1, rs2, rd);
    }

    public void udivx(Register rs1, int simm13, Register rd) {
        op3(Udivx, rs1, simm13, rd);
    }

    public void sll(Register rs1, Register rs2, Register rd) {
        op3(Sll, rs1, rs2, rd);
    }

    public void sll(Register rs1, int shcnt32, Register rd) {
        assert isImm(shcnt32, 5);
        op3(Sll, rs1, shcnt32, rd);
    }

    public void sllx(Register rs1, Register rs2, Register rd) {
        op3(Sllx, rs1, rs2, rd);
    }

    public void sllx(Register rs1, int shcnt64, Register rd) {
        assert isImm(shcnt64, 6);
        op3(Sllx, rs1, shcnt64, rd);
    }

    public void sra(Register rs1, Register rs2, Register rd) {
        op3(Sra, rs1, rs2, rd);
    }

    public void sra(Register rs1, int simm13, Register rd) {
        op3(Sra, rs1, simm13, rd);
    }

    public void srax(Register rs1, Register rs2, Register rd) {
        op3(Srax, rs1, rs2, rd);
    }

    public void srax(Register rs1, int shcnt64, Register rd) {
        assert isImm(shcnt64, 6);
        op3(Srax, rs1, shcnt64, rd);
    }

    public void srl(Register rs1, Register rs2, Register rd) {
        op3(Srl, rs1, rs2, rd);
    }

    public void srl(Register rs1, int simm13, Register rd) {
        op3(Srl, rs1, simm13, rd);
    }

    public void srlx(Register rs1, Register rs2, Register rd) {
        op3(Srlx, rs1, rs2, rd);
    }

    public void srlx(Register rs1, int shcnt64, Register rd) {
        assert isImm(shcnt64, 6);
        op3(Srlx, rs1, shcnt64, rd);
    }

    public void sub(Register rs1, Register rs2, Register rd) {
        op3(Sub, rs1, rs2, rd);
    }

    public void sub(Register rs1, int simm13, Register rd) {
        op3(Sub, rs1, simm13, rd);
    }

    public void subcc(Register rs1, Register rs2, Register rd) {
        op3(Subcc, rs1, rs2, rd);
    }

    public void subcc(Register rs1, int simm13, Register rd) {
        op3(Subcc, rs1, simm13, rd);
    }

    public void ta(int trap) {
        tcc(Icc, Always, trap);
    }

    public void pause() {
        // Maybe fmt10(rd=0b1_1011, op3=0b11_0000, rs1=0, i=1, simm13=1), or
        // maybe op3(Wr, g0, 1, %pause).
        // What should the count be?
        GraalError.unimplemented("The SPARC pause instruction is not yet implemented.");
    }

    public void tcc(CC cc, ConditionFlag flag, int trap) {
        assert isImm(trap, 8);
        int b = cc.value << 11;
        b |= 1 << 13;
        b |= trap;
        fmt10(flag.value, Op3s.Tcc.getValue(), 0, b);
    }

    public void wrccr(Register rs1, Register rs2) {
        op3(Wr, rs1, rs2, g2);
    }

    public void wrccr(Register rs1, int simm13) {
        op3(Wr, rs1, simm13, g2);
    }

    public void xor(Register rs1, Register rs2, Register rd) {
        op3(Xor, rs1, rs2, rd);
    }

    public void xor(Register rs1, int simm13, Register rd) {
        op3(Xor, rs1, simm13, rd);
    }

    public void xorcc(Register rs1, Register rs2, Register rd) {
        op3(Xorcc, rs1, rs2, rd);
    }

    public void xorcc(Register rs1, int simm13, Register rd) {
        op3(Xorcc, rs1, simm13, rd);
    }

    public void xnor(Register rs1, Register rs2, Register rd) {
        op3(Xnor, rs1, rs2, rd);
    }

    public void xnor(Register rs1, int simm13, Register rd) {
        op3(Xnor, rs1, simm13, rd);
    }

    /*
     * Load/Store
     */
    protected void ld(Op3s op3, SPARCAddress addr, Register rd, Asi asi) {
        Register rs1 = addr.getBase();
        if (!addr.getIndex().equals(Register.None)) {
            Register rs2 = addr.getIndex();
            if (asi != null) {
                int b = rs2.encoding;
                b |= asi.value << 5;
                fmt(op3.op.value, rd.encoding, op3.value, rs1.encoding, b);
            } else {
                op3(op3, rs1, rs2, rd);
            }
        } else {
            int imm = addr.getDisplacement();
            op3(op3, rs1, imm, rd);
        }
    }

    protected void ld(Op3s op3, SPARCAddress addr, Register rd) {
        ld(op3, addr, rd, null);
    }

    public void lddf(SPARCAddress src, Register dst) {
        assert isDoubleFloatRegister(dst) : dst;
        ld(Lddf, src, dst);
    }

    public void ldf(SPARCAddress src, Register dst) {
        assert isSingleFloatRegister(dst) : dst;
        ld(Ldf, src, dst);
    }

    public void lduh(SPARCAddress src, Register dst) {
        assert isCPURegister(dst) : dst;
        ld(Lduh, src, dst);
    }

    public void ldsh(SPARCAddress src, Register dst) {
        assert isCPURegister(dst) : dst;
        ld(Ldsh, src, dst);
    }

    public void ld(SPARCAddress src, Register dst, int bytes, boolean signExtend) {
        if (isCPURegister(dst)) {
            if (signExtend) {
                switch (bytes) {
                    case 1:
                        ld(Ldsb, src, dst);
                        break;
                    case 2:
                        ld(Ldsh, src, dst);
                        break;
                    case 4:
                        ld(Ldsw, src, dst);
                        break;
                    case 8:
                        ld(Ldx, src, dst);
                        break;
                    default:
                        throw new InternalError();
                }
            } else {
                switch (bytes) {
                    case 1:
                        ld(Ldub, src, dst);
                        break;
                    case 2:
                        ld(Lduh, src, dst);
                        break;
                    case 4:
                        ld(Lduw, src, dst);
                        break;
                    case 8:
                        ld(Ldx, src, dst);
                        break;
                    default:
                        throw new InternalError();
                }
            }
        } else if (isDoubleFloatRegister(dst) && bytes == 8) {
            assert !signExtend;
            ld(Lddf, src, dst);
        } else if (isSingleFloatRegister(dst) && bytes == 4) {
            assert !signExtend;
            ld(Ldf, src, dst);
        } else {
            throw new InternalError(String.format("src: %s dst: %s bytes: %d signExtend: %b", src, dst, bytes, signExtend));
        }
    }

    public void st(Register src, SPARCAddress dst, int bytes) {
        if (isCPURegister(src)) {
            switch (bytes) {
                case 1:
                    st(Stb, src, dst);
                    break;
                case 2:
                    st(Sth, src, dst);
                    break;
                case 4:
                    st(Stw, src, dst);
                    break;
                case 8:
                    st(Stx, src, dst);
                    break;
                default:
                    throw new InternalError(Integer.toString(bytes));
            }
        } else if (isDoubleFloatRegister(src) && bytes == 8) {
            st(Stdf, src, dst);
        } else if (isSingleFloatRegister(src) && bytes == 4) {
            st(Stf, src, dst);
        } else {
            throw new InternalError(String.format("src: %s dst: %s bytes: %d", src, dst, bytes));
        }
    }

    public void ldub(SPARCAddress src, Register dst) {
        assert isCPURegister(dst) : dst;
        ld(Ldub, src, dst);
    }

    public void ldsb(SPARCAddress src, Register dst) {
        assert isCPURegister(dst) : dst;
        ld(Ldsb, src, dst);
    }

    public void lduw(SPARCAddress src, Register dst) {
        assert isCPURegister(dst) : dst;
        ld(Lduw, src, dst);
    }

    public void ldsw(SPARCAddress src, Register dst) {
        assert isCPURegister(dst) : dst;
        ld(Ldsw, src, dst);
    }

    public void ldx(SPARCAddress src, Register dst) {
        assert isCPURegister(dst) : dst;
        ld(Ldx, src, dst);
    }

    public void ldxa(Register rs1, Register rs2, Register rd, Asi asi) {
        assert isCPURegister(rs1, rs2, rd) : format("%s %s %s", rs1, rs2, rd);
        ld(Ldxa, new SPARCAddress(rs1, rs2), rd, asi);
    }

    public void lduwa(Register rs1, Register rs2, Register rd, Asi asi) {
        assert isCPURegister(rs1, rs2, rd) : format("%s %s %s", rs1, rs2, rd);
        ld(Lduwa, new SPARCAddress(rs1, rs2), rd, asi);
    }

    public void stxa(Register rd, Register rs1, Register rs2, Asi asi) {
        assert isCPURegister(rs1, rs2, rd) : format("%s %s %s", rs1, rs2, rd);
        ld(Stxa, new SPARCAddress(rs1, rs2), rd, asi);
    }

    protected void st(Op3s op3, Register rs1, SPARCAddress dest) {
        ld(op3, dest, rs1);
    }

    public void stdf(Register rd, SPARCAddress addr) {
        assert isDoubleFloatRegister(rd) : rd;
        st(Stdf, rd, addr);
    }

    public void stf(Register rd, SPARCAddress addr) {
        assert isSingleFloatRegister(rd) : rd;
        st(Stf, rd, addr);
    }

    public void stb(Register rd, SPARCAddress addr) {
        assert isCPURegister(rd) : rd;
        st(Stb, rd, addr);
    }

    public void sth(Register rd, SPARCAddress addr) {
        assert isCPURegister(rd) : rd;
        st(Sth, rd, addr);
    }

    public void stw(Register rd, SPARCAddress addr) {
        assert isCPURegister(rd) : rd;
        st(Stw, rd, addr);
    }

    public void stx(Register rd, SPARCAddress addr) {
        assert isCPURegister(rd) : rd;
        st(Stx, rd, addr);
    }

    public void membar(int barriers) {
        op3(Membar, o7, barriers, g0);
    }

    public void casa(Register rs1, Register rs2, Register rd, Asi asi) {
        ld(Casa, new SPARCAddress(rs1, rs2), rd, asi);
    }

    public void casxa(Register rs1, Register rs2, Register rd, Asi asi) {
        ld(Casxa, new SPARCAddress(rs1, rs2), rd, asi);
    }

    @Override
    public InstructionCounter getInstructionCounter() {
        return new SPARCInstructionCounter(this);
    }

    public void patchAddImmediate(int position, int simm13) {
        int inst = getInt(position);
        assert SPARCAssembler.isSimm13(simm13) : simm13;
        assert (inst >>> 30) == 0b10 : String.format("0x%x", inst);
        assert ((inst >>> 18) & 0b11_1111) == 0 : String.format("0x%x", inst);
        assert (inst & (1 << 13)) != 0 : String.format("0x%x", inst);
        inst = inst & (~((1 << 13) - 1));
        inst |= simm13 & ((1 << 12) - 1);
        emitInt(inst, position);
    }

    public void fpadd32(Register rs1, Register rs2, Register rd) {
        op3(Impdep1, Fpadd32, rs1, rs2, rd);
    }

    /**
     * Does peephole optimization on code generated by this assembler. This method should be called
     * at the end of code generation.
     * <p>
     * It searches for conditional branch instructions which has nop in the delay slot then looks at
     * the instruction at branch target; if it is an arithmetic instruction, which does not throw an
     * exception (e.g. division), it pulls this instruction into the delay slot and increments the
     * displacement by 1.
     */
    public void peephole() {
        for (int i : delaySlotOptimizationPoints) {
            optimizeDelaySlot(i);
        }
    }

    /**
     * Optimizes branch instruction <i>b</t> which has a nop in the delay slot. It tries to stuff
     * the instruction at <i>b</i>s branch target into the delay slot of <i>b</i>, set the annul
     * flag and increments <i>b</i>s disp field by 1;
     * <p>
     * If <i>b</i>s branch target instruction is an unconditional branch <i>t</i>, then it tries to
     * put <i>t</i>s delayed instruction into the delay slot of <i>b</i> and add the <i>t</i>s disp
     * field to <i>b</i>s disp field.
     */
    private void optimizeDelaySlot(int i) {
        int delaySlotAbsolute = i + INSTRUCTION_SIZE;
        int nextInst = getInt(delaySlotAbsolute);
        SPARCOp nextOp = getSPARCOp(nextInst);
        if (nextOp instanceof Sethi && Sethi.isNop(nextInst)) {
            int inst = getInt(i);
            SPARCOp op = getSPARCOp(inst);
            if (op instanceof ControlTransferOp && ((ControlTransferOp) op).hasDelaySlot() && ((ControlTransferOp) op).isAnnulable(inst)) {
                ControlTransferOp ctOp = (ControlTransferOp) op;
                int disp = ctOp.getDisp(inst);
                int branchTargetAbsolute = i + disp * INSTRUCTION_SIZE;
                int branchTargetInst = getInt(branchTargetAbsolute);
                SPARCOp branchTargetOp = getSPARCOp(branchTargetInst);
                if (branchTargetOp instanceof Op3Op) {
                    Op3s op3 = ((Op3Op) branchTargetOp).getOp3(branchTargetInst);
                    if (!op3.throwsException()) {
                        inst = ctOp.setDisp(inst, disp + 1); // Increment the offset
                        inst = ctOp.setAnnul(inst, true);
                        emitInt(inst, i);
                        emitInt(branchTargetInst, delaySlotAbsolute);
                    }
                } else if (branchTargetOp instanceof ControlTransferOp && !((ControlTransferOp) branchTargetOp).isConditional(branchTargetInst)) {
                    // If branchtarget is a unconditional branch
                    ControlTransferOp branchTargetOpBranch = (ControlTransferOp) branchTargetOp;
                    int btDisp = branchTargetOpBranch.getDisp(branchTargetInst);
                    int newDisp = disp + btDisp;
                    if (ctOp.isValidDisp(newDisp)) { // Test if we don't exceed field size
                        int instAfter = ctOp.setDisp(inst, newDisp);
                        instAfter = ctOp.setAnnul(instAfter, true);
                        branchTargetInst = getInt(branchTargetAbsolute + INSTRUCTION_SIZE);
                        branchTargetOp = getSPARCOp(branchTargetInst);
                        if (branchTargetOp instanceof Op3Op && !((Op3Op) branchTargetOp).getOp3(branchTargetInst).throwsException()) {
                            emitInt(instAfter, i);
                            emitInt(branchTargetInst, delaySlotAbsolute);
                        }
                    }
                }
            }
        }
    }
}
