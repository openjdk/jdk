/*
 * Copyright (c) 2009, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.amd64;

import static jdk.vm.ci.code.MemoryBarriers.LOAD_LOAD;
import static jdk.vm.ci.code.MemoryBarriers.LOAD_STORE;
import static jdk.vm.ci.code.MemoryBarriers.STORE_STORE;
import static jdk.vm.ci.code.Register.SPECIAL;

import java.nio.ByteOrder;
import java.util.EnumSet;
import java.util.List;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.CPUFeatureName;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.Register.RegisterCategory;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;

/**
 * Represents the AMD64 architecture.
 *
 * The value returned by {@code Architecture#getName} for an instance of this class is {@code "AMD64"}.
 */
public class AMD64 extends Architecture {

    public static final RegisterCategory CPU = new RegisterCategory("CPU");

    // @formatter:off

    // General purpose CPU registers
    public static final Register rax = new Register(0, 0, "rax", CPU);
    public static final Register rcx = new Register(1, 1, "rcx", CPU);
    public static final Register rdx = new Register(2, 2, "rdx", CPU);
    public static final Register rbx = new Register(3, 3, "rbx", CPU);
    public static final Register rsp = new Register(4, 4, "rsp", CPU);
    public static final Register rbp = new Register(5, 5, "rbp", CPU);
    public static final Register rsi = new Register(6, 6, "rsi", CPU);
    public static final Register rdi = new Register(7, 7, "rdi", CPU);

    public static final Register r8  = new Register(8,  8,  "r8", CPU);
    public static final Register r9  = new Register(9,  9,  "r9", CPU);
    public static final Register r10 = new Register(10, 10, "r10", CPU);
    public static final Register r11 = new Register(11, 11, "r11", CPU);
    public static final Register r12 = new Register(12, 12, "r12", CPU);
    public static final Register r13 = new Register(13, 13, "r13", CPU);
    public static final Register r14 = new Register(14, 14, "r14", CPU);
    public static final Register r15 = new Register(15, 15, "r15", CPU);

    public static final Register r16 = new Register(16, 16, "r16", CPU);
    public static final Register r17 = new Register(17, 17, "r17", CPU);
    public static final Register r18 = new Register(18, 18, "r18", CPU);
    public static final Register r19 = new Register(19, 19, "r19", CPU);
    public static final Register r20 = new Register(20, 20, "r20", CPU);
    public static final Register r21 = new Register(21, 21, "r21", CPU);
    public static final Register r22 = new Register(22, 22, "r22", CPU);
    public static final Register r23 = new Register(23, 23, "r23", CPU);
    public static final Register r24 = new Register(24, 24, "r24", CPU);
    public static final Register r25 = new Register(25, 25, "r25", CPU);
    public static final Register r26 = new Register(26, 26, "r26", CPU);
    public static final Register r27 = new Register(27, 27, "r27", CPU);
    public static final Register r28 = new Register(28, 28, "r28", CPU);
    public static final Register r29 = new Register(29, 29, "r29", CPU);
    public static final Register r30 = new Register(30, 30, "r30", CPU);
    public static final Register r31 = new Register(31, 31, "r31", CPU);

    // The set of common CPU registers available on all x64 platforms.
    public static final List<Register> cpuRegisters = List.of(
        rax, rcx, rdx, rbx, rsp, rbp, rsi, rdi,
        r8, r9, r10, r11, r12, r13, r14, r15
    );

    public static final List<Register> cpuRegistersAPX = List.of(
        rax, rcx, rdx, rbx, rsp, rbp, rsi, rdi,
         r8,  r9, r10, r11, r12, r13, r14, r15,
        r16, r17, r18, r19, r20, r21, r22, r23,
        r24, r25, r26, r27, r28, r29, r30, r31
    );

    public static final RegisterCategory XMM = new RegisterCategory("XMM");

    // XMM registers
    public static final Register xmm0 = new Register(32, 0, "xmm0", XMM);
    public static final Register xmm1 = new Register(33, 1, "xmm1", XMM);
    public static final Register xmm2 = new Register(34, 2, "xmm2", XMM);
    public static final Register xmm3 = new Register(35, 3, "xmm3", XMM);
    public static final Register xmm4 = new Register(36, 4, "xmm4", XMM);
    public static final Register xmm5 = new Register(37, 5, "xmm5", XMM);
    public static final Register xmm6 = new Register(38, 6, "xmm6", XMM);
    public static final Register xmm7 = new Register(39, 7, "xmm7", XMM);

    public static final Register xmm8  = new Register(40,  8, "xmm8",  XMM);
    public static final Register xmm9  = new Register(41,  9, "xmm9",  XMM);
    public static final Register xmm10 = new Register(42, 10, "xmm10", XMM);
    public static final Register xmm11 = new Register(43, 11, "xmm11", XMM);
    public static final Register xmm12 = new Register(44, 12, "xmm12", XMM);
    public static final Register xmm13 = new Register(45, 13, "xmm13", XMM);
    public static final Register xmm14 = new Register(46, 14, "xmm14", XMM);
    public static final Register xmm15 = new Register(47, 15, "xmm15", XMM);

    public static final Register xmm16 = new Register(48, 16, "xmm16", XMM);
    public static final Register xmm17 = new Register(49, 17, "xmm17", XMM);
    public static final Register xmm18 = new Register(50, 18, "xmm18", XMM);
    public static final Register xmm19 = new Register(51, 19, "xmm19", XMM);
    public static final Register xmm20 = new Register(52, 20, "xmm20", XMM);
    public static final Register xmm21 = new Register(53, 21, "xmm21", XMM);
    public static final Register xmm22 = new Register(54, 22, "xmm22", XMM);
    public static final Register xmm23 = new Register(55, 23, "xmm23", XMM);

    public static final Register xmm24 = new Register(56, 24, "xmm24", XMM);
    public static final Register xmm25 = new Register(57, 25, "xmm25", XMM);
    public static final Register xmm26 = new Register(58, 26, "xmm26", XMM);
    public static final Register xmm27 = new Register(59, 27, "xmm27", XMM);
    public static final Register xmm28 = new Register(60, 28, "xmm28", XMM);
    public static final Register xmm29 = new Register(61, 29, "xmm29", XMM);
    public static final Register xmm30 = new Register(62, 30, "xmm30", XMM);
    public static final Register xmm31 = new Register(63, 31, "xmm31", XMM);

    public static final List<Register> xmmRegistersSSE = List.of(
        xmm0, xmm1, xmm2,  xmm3,  xmm4,  xmm5,  xmm6,  xmm7,
        xmm8, xmm9, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15
    );

    public static final List<Register> xmmRegistersAVX512 = List.of(
        xmm0, xmm1, xmm2,  xmm3,  xmm4,  xmm5,  xmm6,  xmm7,
        xmm8, xmm9, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15,
        xmm16, xmm17, xmm18, xmm19, xmm20, xmm21, xmm22, xmm23,
        xmm24, xmm25, xmm26, xmm27, xmm28, xmm29, xmm30, xmm31
    );

    public static final RegisterCategory MASK = new RegisterCategory("MASK", false);

    public static final Register k0 = new Register(64, 0, "k0", MASK);
    public static final Register k1 = new Register(65, 1, "k1", MASK);
    public static final Register k2 = new Register(66, 2, "k2", MASK);
    public static final Register k3 = new Register(67, 3, "k3", MASK);
    public static final Register k4 = new Register(68, 4, "k4", MASK);
    public static final Register k5 = new Register(69, 5, "k5", MASK);
    public static final Register k6 = new Register(70, 6, "k6", MASK);
    public static final Register k7 = new Register(71, 7, "k7", MASK);

    public static final List<Register> valueRegistersSSE = List.of(
        rax,  rcx,  rdx,   rbx,   rsp,   rbp,   rsi,   rdi,
        r8,   r9,   r10,   r11,   r12,   r13,   r14,   r15,
        xmm0, xmm1, xmm2,  xmm3,  xmm4,  xmm5,  xmm6,  xmm7,
        xmm8, xmm9, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15
    );

    public static final List<Register> valueRegistersAVX512 = List.of(
        rax,  rcx,  rdx,   rbx,   rsp,   rbp,   rsi,   rdi,
        r8,   r9,   r10,   r11,   r12,   r13,   r14,   r15,
        xmm0, xmm1, xmm2,  xmm3,  xmm4,  xmm5,  xmm6,  xmm7,
        xmm8, xmm9, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15,
        xmm16, xmm17, xmm18, xmm19, xmm20, xmm21, xmm22, xmm23,
        xmm24, xmm25, xmm26, xmm27, xmm28, xmm29, xmm30, xmm31,
        k0, k1, k2, k3, k4, k5, k6, k7
    );

    public static final List<Register> valueRegistersSSEAndAPX = List.of(
        rax,  rcx,  rdx,   rbx,   rsp,   rbp,   rsi,   rdi,
        r8,   r9,   r10,   r11,   r12,   r13,   r14,   r15,
        r16,  r17,  r18,   r19,   r20,   r21,   r22,   r23,
        r24,  r25,  r26,   r27,   r28,   r29,   r30,   r31,
        xmm0, xmm1, xmm2,  xmm3,  xmm4,  xmm5,  xmm6,  xmm7,
        xmm8, xmm9, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15
    );

    public static final List<Register> valueRegistersAVX512AndAPX = List.of(
        rax,  rcx,  rdx,   rbx,   rsp,   rbp,   rsi,   rdi,
        r8,   r9,   r10,   r11,   r12,   r13,   r14,   r15,
        r16,  r17,  r18,   r19,   r20,   r21,   r22,   r23,
        r24,  r25,  r26,   r27,   r28,   r29,   r30,   r31,
        xmm0, xmm1, xmm2,  xmm3,  xmm4,  xmm5,  xmm6,  xmm7,
        xmm8, xmm9, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15,
        xmm16, xmm17, xmm18, xmm19, xmm20, xmm21, xmm22, xmm23,
        xmm24, xmm25, xmm26, xmm27, xmm28, xmm29, xmm30, xmm31,
        k0, k1, k2, k3, k4, k5, k6, k7
    );

    /**
     * Register used to construct an instruction-relative address.
     */
    public static final Register rip = new Register(72, -1, "rip", SPECIAL);

    public static final List<Register> allRegisters = List.of(
        rax,  rcx,  rdx,   rbx,   rsp,   rbp,   rsi,   rdi,
        r8,   r9,   r10,   r11,   r12,   r13,   r14,   r15,
        r16,  r17,  r18,   r19,   r20,   r21,   r22,   r23,
        r24,  r25,  r26,   r27,   r28,   r29,   r30,   r31,
        xmm0, xmm1, xmm2,  xmm3,  xmm4,  xmm5,  xmm6,  xmm7,
        xmm8, xmm9, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15,
        xmm16, xmm17, xmm18, xmm19, xmm20, xmm21, xmm22, xmm23,
        xmm24, xmm25, xmm26, xmm27, xmm28, xmm29, xmm30, xmm31,
        k0, k1, k2, k3, k4, k5, k6, k7,
        rip
    );

    // @formatter:on

    /**
     * Basic set of CPU features mirroring what is returned from the cpuid instruction. See:
     * {@code VM_Version::cpuFeatureFlags}.
     */
    public enum CPUFeature implements CPUFeatureName {
        CX8,
        CMOV,
        FXSR,
        HT,
        MMX,
        AMD_3DNOW_PREFETCH,
        SSE,
        SSE2,
        SSE3,
        SSSE3,
        SSE4A,
        SSE4_1,
        SSE4_2,
        POPCNT,
        LZCNT,
        TSC,
        TSCINV,
        TSCINV_BIT,
        AVX,
        AVX2,
        AES,
        ERMS,
        CLMUL,
        BMI1,
        BMI2,
        RTM,
        ADX,
        AVX512F,
        AVX512DQ,
        AVX512PF,
        AVX512ER,
        AVX512CD,
        AVX512BW,
        AVX512VL,
        SHA,
        FMA,
        VZEROUPPER,
        AVX512_VPOPCNTDQ,
        AVX512_VPCLMULQDQ,
        AVX512_VAES,
        AVX512_VNNI,
        FLUSH,
        FLUSHOPT,
        CLWB,
        AVX512_VBMI2,
        AVX512_VBMI,
        HV,
        SERIALIZE,
        RDTSCP,
        RDPID,
        FSRM,
        GFNI,
        AVX512_BITALG,
        F16C,
        PKU,
        OSPKE,
        CET_IBT,
        CET_SS,
        AVX512_IFMA,
        AVX_IFMA,
        APX_F,
        SHA512,
        AVX512_FP16,
        AVX10_1,
        AVX10_2
    }

    private final EnumSet<CPUFeature> features;

    private final AMD64Kind largestKind;

    private final AMD64Kind largestMaskKind;

    public AMD64(EnumSet<CPUFeature> features) {
        super("AMD64", AMD64Kind.QWORD, ByteOrder.LITTLE_ENDIAN, true, allRegisters, LOAD_LOAD | LOAD_STORE | STORE_STORE, 1, 8);
        this.features = features;
        assert features.contains(CPUFeature.SSE2) : "minimum config for x64";

        if (features.contains(CPUFeature.AVX512F)) {
            largestKind = AMD64Kind.V512_QWORD;
            if (features.contains(CPUFeature.AVX512BW)) {
                largestMaskKind = AMD64Kind.MASK64;
            } else {
                largestMaskKind = AMD64Kind.MASK16;
            }
        } else if (features.contains(CPUFeature.AVX)) {
            largestKind = AMD64Kind.V256_QWORD;
            largestMaskKind = null;
        } else {
            largestKind = AMD64Kind.V128_QWORD;
            largestMaskKind = null;
        }
    }

    @Override
    public EnumSet<CPUFeature> getFeatures() {
        return features;
    }

    @Override
    public List<Register> getAvailableValueRegisters() {
        if (features.contains(CPUFeature.APX_F)) {
            if (features.contains(CPUFeature.AVX512F)) {
                return valueRegistersAVX512AndAPX;
            } else {
                return valueRegistersSSEAndAPX;
            }
        } else if (features.contains(CPUFeature.AVX512F)) {
            return valueRegistersAVX512;
        } else {
            return valueRegistersSSE;
        }
    }

    @Override
    public PlatformKind getPlatformKind(JavaKind javaKind) {
        switch (javaKind) {
            case Boolean:
            case Byte:
                return AMD64Kind.BYTE;
            case Short:
            case Char:
                return AMD64Kind.WORD;
            case Int:
                return AMD64Kind.DWORD;
            case Long:
            case Object:
                return AMD64Kind.QWORD;
            case Float:
                return AMD64Kind.SINGLE;
            case Double:
                return AMD64Kind.DOUBLE;
            default:
                return null;
        }
    }

    @Override
    public boolean canStoreValue(RegisterCategory category, PlatformKind platformKind) {
        AMD64Kind kind = (AMD64Kind) platformKind;
        if (kind.isInteger()) {
            return category.equals(CPU);
        } else if (kind.isXMM()) {
            return category.equals(XMM);
        } else {
            assert kind.isMask();
            return category.equals(MASK);
        }
    }

    @Override
    public AMD64Kind getLargestStorableKind(RegisterCategory category) {
        if (category.equals(CPU)) {
            return AMD64Kind.QWORD;
        } else if (category.equals(XMM)) {
            return largestKind;
        } else if (category.equals(MASK)) {
            return largestMaskKind;
        } else {
            return null;
        }
    }
}
