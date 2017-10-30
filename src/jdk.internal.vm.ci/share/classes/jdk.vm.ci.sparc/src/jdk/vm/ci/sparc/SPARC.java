/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.sparc;

import static java.nio.ByteOrder.BIG_ENDIAN;
import static jdk.vm.ci.code.MemoryBarriers.LOAD_LOAD;
import static jdk.vm.ci.code.MemoryBarriers.LOAD_STORE;
import static jdk.vm.ci.code.MemoryBarriers.STORE_STORE;

import java.util.Set;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.Register.RegisterCategory;
import jdk.vm.ci.code.RegisterArray;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;

/**
 * Represents the SPARC architecture.
 */
public class SPARC extends Architecture {

    public static final RegisterCategory CPU = new RegisterCategory("CPU");
    public static final RegisterCategory FPUs = new RegisterCategory("FPUs");
    public static final RegisterCategory FPUd = new RegisterCategory("FPUd");
    public static final RegisterCategory FPUq = new RegisterCategory("FPUq");

    // General purpose registers
    public static final Register g0 = new Register(0, 0, "g0", CPU);
    public static final Register g1 = new Register(1, 1, "g1", CPU);
    public static final Register g2 = new Register(2, 2, "g2", CPU);
    public static final Register g3 = new Register(3, 3, "g3", CPU);
    public static final Register g4 = new Register(4, 4, "g4", CPU);
    public static final Register g5 = new Register(5, 5, "g5", CPU);
    public static final Register g6 = new Register(6, 6, "g6", CPU);
    public static final Register g7 = new Register(7, 7, "g7", CPU);

    public static final Register o0 = new Register(8, 8, "o0", CPU);
    public static final Register o1 = new Register(9, 9, "o1", CPU);
    public static final Register o2 = new Register(10, 10, "o2", CPU);
    public static final Register o3 = new Register(11, 11, "o3", CPU);
    public static final Register o4 = new Register(12, 12, "o4", CPU);
    public static final Register o5 = new Register(13, 13, "o5", CPU);
    public static final Register o6 = new Register(14, 14, "o6", CPU);
    public static final Register o7 = new Register(15, 15, "o7", CPU);

    public static final Register l0 = new Register(16, 16, "l0", CPU);
    public static final Register l1 = new Register(17, 17, "l1", CPU);
    public static final Register l2 = new Register(18, 18, "l2", CPU);
    public static final Register l3 = new Register(19, 19, "l3", CPU);
    public static final Register l4 = new Register(20, 20, "l4", CPU);
    public static final Register l5 = new Register(21, 21, "l5", CPU);
    public static final Register l6 = new Register(22, 22, "l6", CPU);
    public static final Register l7 = new Register(23, 23, "l7", CPU);

    public static final Register i0 = new Register(24, 24, "i0", CPU);
    public static final Register i1 = new Register(25, 25, "i1", CPU);
    public static final Register i2 = new Register(26, 26, "i2", CPU);
    public static final Register i3 = new Register(27, 27, "i3", CPU);
    public static final Register i4 = new Register(28, 28, "i4", CPU);
    public static final Register i5 = new Register(29, 29, "i5", CPU);
    public static final Register i6 = new Register(30, 30, "i6", CPU);
    public static final Register i7 = new Register(31, 31, "i7", CPU);

    public static final Register sp = o6;
    public static final Register fp = i6;

    // Floating point registers
    public static final Register f0 = new Register(32, 0, "f0", FPUs);
    public static final Register f1 = new Register(33, 1, "f1", FPUs);
    public static final Register f2 = new Register(34, 2, "f2", FPUs);
    public static final Register f3 = new Register(35, 3, "f3", FPUs);
    public static final Register f4 = new Register(36, 4, "f4", FPUs);
    public static final Register f5 = new Register(37, 5, "f5", FPUs);
    public static final Register f6 = new Register(38, 6, "f6", FPUs);
    public static final Register f7 = new Register(39, 7, "f7", FPUs);

    public static final Register f8 = new Register(40, 8, "f8", FPUs);
    public static final Register f9 = new Register(41, 9, "f9", FPUs);
    public static final Register f10 = new Register(42, 10, "f10", FPUs);
    public static final Register f11 = new Register(43, 11, "f11", FPUs);
    public static final Register f12 = new Register(44, 12, "f12", FPUs);
    public static final Register f13 = new Register(45, 13, "f13", FPUs);
    public static final Register f14 = new Register(46, 14, "f14", FPUs);
    public static final Register f15 = new Register(47, 15, "f15", FPUs);

    public static final Register f16 = new Register(48, 16, "f16", FPUs);
    public static final Register f17 = new Register(49, 17, "f17", FPUs);
    public static final Register f18 = new Register(50, 18, "f18", FPUs);
    public static final Register f19 = new Register(51, 19, "f19", FPUs);
    public static final Register f20 = new Register(52, 20, "f20", FPUs);
    public static final Register f21 = new Register(53, 21, "f21", FPUs);
    public static final Register f22 = new Register(54, 22, "f22", FPUs);
    public static final Register f23 = new Register(55, 23, "f23", FPUs);

    public static final Register f24 = new Register(56, 24, "f24", FPUs);
    public static final Register f25 = new Register(57, 25, "f25", FPUs);
    public static final Register f26 = new Register(58, 26, "f26", FPUs);
    public static final Register f27 = new Register(59, 27, "f27", FPUs);
    public static final Register f28 = new Register(60, 28, "f28", FPUs);
    public static final Register f29 = new Register(61, 29, "f29", FPUs);
    public static final Register f30 = new Register(62, 30, "f30", FPUs);
    public static final Register f31 = new Register(63, 31, "f31", FPUs);

    // Double precision registers
    public static final Register d0 = new Register(64, getDoubleEncoding(0), "d0", FPUd);
    public static final Register d2 = new Register(65, getDoubleEncoding(2), "d2", FPUd);
    public static final Register d4 = new Register(66, getDoubleEncoding(4), "d4", FPUd);
    public static final Register d6 = new Register(67, getDoubleEncoding(6), "d6", FPUd);
    public static final Register d8 = new Register(68, getDoubleEncoding(8), "d8", FPUd);
    public static final Register d10 = new Register(69, getDoubleEncoding(10), "d10", FPUd);
    public static final Register d12 = new Register(70, getDoubleEncoding(12), "d12", FPUd);
    public static final Register d14 = new Register(71, getDoubleEncoding(14), "d14", FPUd);

    public static final Register d16 = new Register(72, getDoubleEncoding(16), "d16", FPUd);
    public static final Register d18 = new Register(73, getDoubleEncoding(18), "d18", FPUd);
    public static final Register d20 = new Register(74, getDoubleEncoding(20), "d20", FPUd);
    public static final Register d22 = new Register(75, getDoubleEncoding(22), "d22", FPUd);
    public static final Register d24 = new Register(76, getDoubleEncoding(24), "d24", FPUd);
    public static final Register d26 = new Register(77, getDoubleEncoding(26), "d26", FPUd);
    public static final Register d28 = new Register(78, getDoubleEncoding(28), "d28", FPUd);
    public static final Register d30 = new Register(79, getDoubleEncoding(28), "d28", FPUd);

    public static final Register d32 = new Register(80, getDoubleEncoding(32), "d32", FPUd);
    public static final Register d34 = new Register(81, getDoubleEncoding(34), "d34", FPUd);
    public static final Register d36 = new Register(82, getDoubleEncoding(36), "d36", FPUd);
    public static final Register d38 = new Register(83, getDoubleEncoding(38), "d38", FPUd);
    public static final Register d40 = new Register(84, getDoubleEncoding(40), "d40", FPUd);
    public static final Register d42 = new Register(85, getDoubleEncoding(42), "d42", FPUd);
    public static final Register d44 = new Register(86, getDoubleEncoding(44), "d44", FPUd);
    public static final Register d46 = new Register(87, getDoubleEncoding(46), "d46", FPUd);

    public static final Register d48 = new Register(88, getDoubleEncoding(48), "d48", FPUd);
    public static final Register d50 = new Register(89, getDoubleEncoding(50), "d50", FPUd);
    public static final Register d52 = new Register(90, getDoubleEncoding(52), "d52", FPUd);
    public static final Register d54 = new Register(91, getDoubleEncoding(54), "d54", FPUd);
    public static final Register d56 = new Register(92, getDoubleEncoding(56), "d56", FPUd);
    public static final Register d58 = new Register(93, getDoubleEncoding(58), "d58", FPUd);
    public static final Register d60 = new Register(94, getDoubleEncoding(60), "d60", FPUd);
    public static final Register d62 = new Register(95, getDoubleEncoding(62), "d62", FPUd);

    // Quad precision registers
    public static final Register q0 = new Register(96, getQuadncoding(0), "q0", FPUq);
    public static final Register q4 = new Register(97, getQuadncoding(4), "q4", FPUq);
    public static final Register q8 = new Register(98, getQuadncoding(8), "q8", FPUq);
    public static final Register q12 = new Register(99, getQuadncoding(12), "q12", FPUq);
    public static final Register q16 = new Register(100, getQuadncoding(16), "q16", FPUq);
    public static final Register q20 = new Register(101, getQuadncoding(20), "q20", FPUq);
    public static final Register q24 = new Register(102, getQuadncoding(24), "q24", FPUq);
    public static final Register q28 = new Register(103, getQuadncoding(28), "q28", FPUq);

    public static final Register q32 = new Register(104, getQuadncoding(32), "q32", FPUq);
    public static final Register q36 = new Register(105, getQuadncoding(36), "q36", FPUq);
    public static final Register q40 = new Register(106, getQuadncoding(40), "q40", FPUq);
    public static final Register q44 = new Register(107, getQuadncoding(44), "q44", FPUq);
    public static final Register q48 = new Register(108, getQuadncoding(48), "q48", FPUq);
    public static final Register q52 = new Register(109, getQuadncoding(52), "q52", FPUq);
    public static final Register q56 = new Register(110, getQuadncoding(56), "q56", FPUq);
    public static final Register q60 = new Register(111, getQuadncoding(60), "q60", FPUq);

    // @formatter:off
    public static final RegisterArray cpuRegisters = new RegisterArray(
        g0,  g1,  g2,  g3,  g4,  g5,  g6,  g7,
        o0,  o1,  o2,  o3,  o4,  o5,  o6,  o7,
        l0,  l1,  l2,  l3,  l4,  l5,  l6,  l7,
        i0,  i1,  i2,  i3,  i4,  i5,  i6,  i7
    );

    public static final RegisterArray fpusRegisters = new RegisterArray(
        f0,  f1,  f2,  f3,  f4,  f5,  f6,  f7,
        f8,  f9,  f10, f11, f12, f13, f14, f15,
        f16, f17, f18, f19, f20, f21, f22, f23,
        f24, f25, f26, f27, f28, f29, f30, f31
    );

    public static final RegisterArray fpudRegisters = new RegisterArray(
        d0, d2, d4, d6, d8,  d10, d12, d14,
        d16, d18, d20, d22, d24, d26, d28, d30,
        d32, d34, d36, d38, d40, d42, d44, d46,
        d48, d50, d52, d54, d56, d58, d60, d62
    );

    public static final RegisterArray fpuqRegisters = new RegisterArray(
        q0, q4, q8, q12,
        q16, q20, q24, q28,
        q32, q36, q40, q44,
        q48, q52, q56, q60
    );

    public static final RegisterArray allRegisters = new RegisterArray(
        g0,  g1,  g2,  g3,  g4,  g5,  g6,  g7,
        o0,  o1,  o2,  o3,  o4,  o5,  o6,  o7,
        l0,  l1,  l2,  l3,  l4,  l5,  l6,  l7,
        i0,  i1,  i2,  i3,  i4,  i5,  i6,  i7,

        f0,  f1,  f2,  f3,  f4,  f5,  f6,  f7,
        f8,  f9,  f10, f11, f12, f13, f14, f15,
        f16, f17, f18, f19, f20, f21, f22, f23,
        f24, f25, f26, f27, f28, f29, f30, f31,

        d0, d2, d4, d6, d8,  d10, d12, d14,
        d16, d18, d20, d22, d24, d26, d28, d30,
        d32, d34, d36, d38, d40, d42, d44, d46,
        d48, d50, d52, d54, d56, d58, d60, d62,

        q0, q4, q8, q12,
        q16, q20, q24, q28,
        q32, q36, q40, q44,
        q48, q52, q56, q60
    );
    // @formatter:on

    /**
     * Stack bias for stack and frame pointer loads.
     */
    public static final int STACK_BIAS = 0x7ff;

    /**
     * Size to keep free for flushing the register-window to stack.
     */
    public static final int REGISTER_SAFE_AREA_SIZE = 128;

    public final Set<CPUFeature> features;

    public SPARC(Set<CPUFeature> features) {
        super("SPARC", SPARCKind.XWORD, BIG_ENDIAN, false, allRegisters, LOAD_LOAD | LOAD_STORE | STORE_STORE, 1, 8);
        this.features = features;
    }

    @Override
    public RegisterArray getAvailableValueRegisters() {
        return allRegisters;
    }

    @Override
    public boolean canStoreValue(RegisterCategory category, PlatformKind kind) {
        SPARCKind sparcKind = (SPARCKind) kind;
        switch (sparcKind) {
            case BYTE:
            case HWORD:
            case WORD:
            case XWORD:
                return CPU.equals(category);
            case SINGLE:
            case V32_BYTE:
            case V32_HWORD:
                return FPUs.equals(category);
            case DOUBLE:
            case V64_BYTE:
            case V64_HWORD:
            case V64_WORD:
            case V64_SINGLE:
                return FPUd.equals(category);
            case QUAD:
                return FPUq.equals(category);
            default:
                return false;
        }
    }

    @Override
    public PlatformKind getLargestStorableKind(RegisterCategory category) {
        if (category.equals(CPU)) {
            return SPARCKind.XWORD;
        } else if (category.equals(FPUd)) {
            return SPARCKind.DOUBLE;
        } else if (category.equals(FPUs)) {
            return SPARCKind.SINGLE;
        } else if (category.equals(FPUq)) {
            return SPARCKind.QUAD;
        } else {
            throw new IllegalArgumentException("Unknown register category: " + category);
        }
    }

    @Override
    public PlatformKind getPlatformKind(JavaKind javaKind) {
        switch (javaKind) {
            case Boolean:
            case Byte:
                return SPARCKind.BYTE;
            case Short:
            case Char:
                return SPARCKind.HWORD;
            case Int:
                return SPARCKind.WORD;
            case Long:
            case Object:
                return SPARCKind.XWORD;
            case Float:
                return SPARCKind.SINGLE;
            case Double:
                return SPARCKind.DOUBLE;
            default:
                throw new IllegalArgumentException("Unknown JavaKind: " + javaKind);
        }
    }

    private static int getDoubleEncoding(int reg) {
        assert reg < 64 && ((reg & 1) == 0);
        return (reg & 0x1e) | ((reg & 0x20) >> 5);
    }

    private static int getQuadncoding(int reg) {
        assert reg < 64 && ((reg & 1) == 0);
        return (reg & 0x1c) | ((reg & 0x20) >> 5);
    }

    public Set<CPUFeature> getFeatures() {
        return features;
    }

    public boolean hasFeature(CPUFeature feature) {
        return features.contains(feature);
    }

    public enum CPUFeature {
        // ISA determined properties:
        ADI,
        AES,
        BLK_INIT,
        CAMELLIA,
        CBCOND,
        CRC32C,
        DES,
        DICTUNP,
        FMAF,
        FPCMPSHL,
        HPC,
        IMA,
        KASUMI,
        MD5,
        MME,
        MONT,
        MPMUL,
        MWAIT,
        PAUSE,
        PAUSE_NSEC,
        POPC,
        RLE,
        SHA1,
        SHA256,
        SHA3,
        SHA512,
        SPARC5,
        SPARC5B,
        SPARC6,
        V9,
        VAMASK,
        VIS1,
        VIS2,
        VIS3,
        VIS3B,
        VIS3C,
        XMONT,
        XMPMUL,
        // Synthesised CPU properties:
        BLK_ZEROING,
        FAST_BIS,
        FAST_CMOVE,
        FAST_IDIV,
        FAST_IND_BR,
        FAST_LD,
        FAST_RDPC
    }
}
