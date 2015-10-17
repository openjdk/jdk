/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.code.MemoryBarriers.*;
import static jdk.vm.ci.code.Register.*;

import java.nio.*;
import java.util.*;

import jdk.vm.ci.code.*;
import jdk.vm.ci.code.Register.RegisterCategory;
import jdk.vm.ci.meta.*;

/**
 * Represents the AMD64 architecture.
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

    public static final Register[] cpuRegisters = {
        rax, rcx, rdx, rbx, rsp, rbp, rsi, rdi,
        r8, r9, r10, r11, r12, r13, r14, r15
    };

    private static final int XMM_REFERENCE_MAP_SHIFT = 2;

    public static final RegisterCategory XMM = new RegisterCategory("XMM", cpuRegisters.length, XMM_REFERENCE_MAP_SHIFT);

    // XMM registers
    public static final Register xmm0 = new Register(16, 0, "xmm0", XMM);
    public static final Register xmm1 = new Register(17, 1, "xmm1", XMM);
    public static final Register xmm2 = new Register(18, 2, "xmm2", XMM);
    public static final Register xmm3 = new Register(19, 3, "xmm3", XMM);
    public static final Register xmm4 = new Register(20, 4, "xmm4", XMM);
    public static final Register xmm5 = new Register(21, 5, "xmm5", XMM);
    public static final Register xmm6 = new Register(22, 6, "xmm6", XMM);
    public static final Register xmm7 = new Register(23, 7, "xmm7", XMM);

    public static final Register xmm8 =  new Register(24,  8, "xmm8",  XMM);
    public static final Register xmm9 =  new Register(25,  9, "xmm9",  XMM);
    public static final Register xmm10 = new Register(26, 10, "xmm10", XMM);
    public static final Register xmm11 = new Register(27, 11, "xmm11", XMM);
    public static final Register xmm12 = new Register(28, 12, "xmm12", XMM);
    public static final Register xmm13 = new Register(29, 13, "xmm13", XMM);
    public static final Register xmm14 = new Register(30, 14, "xmm14", XMM);
    public static final Register xmm15 = new Register(31, 15, "xmm15", XMM);

    public static final Register[] xmmRegisters = {
        xmm0, xmm1, xmm2,  xmm3,  xmm4,  xmm5,  xmm6,  xmm7,
        xmm8, xmm9, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15
    };

    public static final Register[] cpuxmmRegisters = {
        rax,  rcx,  rdx,   rbx,   rsp,   rbp,   rsi,   rdi,
        r8,   r9,   r10,   r11,   r12,   r13,   r14,   r15,
        xmm0, xmm1, xmm2,  xmm3,  xmm4,  xmm5,  xmm6,  xmm7,
        xmm8, xmm9, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15
    };

    /**
     * Register used to construct an instruction-relative address.
     */
    public static final Register rip = new Register(32, -1, "rip", SPECIAL);

    public static final Register[] allRegisters = {
        rax,  rcx,  rdx,   rbx,   rsp,   rbp,   rsi,   rdi,
        r8,   r9,   r10,   r11,   r12,   r13,   r14,   r15,
        xmm0, xmm1, xmm2,  xmm3,  xmm4,  xmm5,  xmm6,  xmm7,
        xmm8, xmm9, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15,
        rip
    };

    // @formatter:on

    /**
     * Basic set of CPU features mirroring what is returned from the cpuid instruction. See:
     * {@code VM_Version::cpuFeatureFlags}.
     */
    public static enum CPUFeature {
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
        AVX512BW
    }

    private final EnumSet<CPUFeature> features;

    /**
     * Set of flags to control code emission.
     */
    public static enum Flag {
        UseCountLeadingZerosInstruction,
        UseCountTrailingZerosInstruction
    }

    private final EnumSet<Flag> flags;

    public AMD64(EnumSet<CPUFeature> features, EnumSet<Flag> flags) {
        super("AMD64", JavaKind.Long, ByteOrder.LITTLE_ENDIAN, true, allRegisters, LOAD_STORE | STORE_STORE, 1, cpuRegisters.length + (xmmRegisters.length << XMM_REFERENCE_MAP_SHIFT), 8);
        this.features = features;
        this.flags = flags;
        assert features.contains(CPUFeature.SSE2) : "minimum config for x64";
    }

    public EnumSet<CPUFeature> getFeatures() {
        return features;
    }

    public EnumSet<Flag> getFlags() {
        return flags;
    }

    @Override
    public PlatformKind getPlatformKind(JavaKind javaKind) {
        if (javaKind.isObject()) {
            return getWordKind();
        } else {
            return javaKind;
        }
    }

    @Override
    public boolean canStoreValue(RegisterCategory category, PlatformKind platformKind) {
        if (!(platformKind instanceof JavaKind)) {
            return false;
        }

        JavaKind kind = (JavaKind) platformKind;
        if (category.equals(CPU)) {
            switch (kind) {
                case Boolean:
                case Byte:
                case Char:
                case Short:
                case Int:
                case Long:
                    return true;
            }
        } else if (category.equals(XMM)) {
            switch (kind) {
                case Float:
                case Double:
                    return true;
            }
        }

        return false;
    }

    @Override
    public PlatformKind getLargestStorableKind(RegisterCategory category) {
        if (category.equals(CPU)) {
            return JavaKind.Long;
        } else if (category.equals(XMM)) {
            return JavaKind.Double;
        } else {
            return JavaKind.Illegal;
        }
    }
}
