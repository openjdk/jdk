/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.riscv64;

import java.nio.ByteOrder;
import java.util.EnumSet;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.CPUFeatureName;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.Register.RegisterCategory;
import jdk.vm.ci.code.RegisterArray;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;

/**
 * Represents the RISCV64 architecture.
 *
 * The value returned by {@code Architecture#getName} for an instance of this class is {@code "riscv64"}.
 */
public class RISCV64 extends Architecture {

    public static final RegisterCategory CPU = new RegisterCategory("CPU");

    // General purpose CPU registers
    public static final Register x0 = new Register(0, 0, "x0", CPU);
    public static final Register x1 = new Register(1, 1, "x1", CPU);
    public static final Register x2 = new Register(2, 2, "x2", CPU);
    public static final Register x3 = new Register(3, 3, "x3", CPU);
    public static final Register x4 = new Register(4, 4, "x4", CPU);
    public static final Register x5 = new Register(5, 5, "x5", CPU);
    public static final Register x6 = new Register(6, 6, "x6", CPU);
    public static final Register x7 = new Register(7, 7, "x7", CPU);
    public static final Register x8 = new Register(8, 8, "x8", CPU);
    public static final Register x9 = new Register(9, 9, "x9", CPU);
    public static final Register x10 = new Register(10, 10, "x10", CPU);
    public static final Register x11 = new Register(11, 11, "x11", CPU);
    public static final Register x12 = new Register(12, 12, "x12", CPU);
    public static final Register x13 = new Register(13, 13, "x13", CPU);
    public static final Register x14 = new Register(14, 14, "x14", CPU);
    public static final Register x15 = new Register(15, 15, "x15", CPU);
    public static final Register x16 = new Register(16, 16, "x16", CPU);
    public static final Register x17 = new Register(17, 17, "x17", CPU);
    public static final Register x18 = new Register(18, 18, "x18", CPU);
    public static final Register x19 = new Register(19, 19, "x19", CPU);
    public static final Register x20 = new Register(20, 20, "x20", CPU);
    public static final Register x21 = new Register(21, 21, "x21", CPU);
    public static final Register x22 = new Register(22, 22, "x22", CPU);
    public static final Register x23 = new Register(23, 23, "x23", CPU);
    public static final Register x24 = new Register(24, 24, "x24", CPU);
    public static final Register x25 = new Register(25, 25, "x25", CPU);
    public static final Register x26 = new Register(26, 26, "x26", CPU);
    public static final Register x27 = new Register(27, 27, "x27", CPU);
    public static final Register x28 = new Register(28, 28, "x28", CPU);
    public static final Register x29 = new Register(29, 29, "x29", CPU);
    public static final Register x30 = new Register(30, 30, "x30", CPU);
    public static final Register x31 = new Register(31, 31, "x31", CPU);

    // @formatter:off
    public static final RegisterArray cpuRegisters = new RegisterArray(
        x0,  x1,  x2,  x3,  x4,  x5,  x6,  x7,
        x8,  x9,  x10, x11, x12, x13, x14, x15,
        x16, x17, x18, x19, x20, x21, x22, x23,
        x24, x25, x26, x27, x28, x29, x30, x31
    );
    // @formatter:on

    public static final RegisterCategory FP = new RegisterCategory("FP");

    // Simd registers
    public static final Register f0 = new Register(32, 0, "f0", FP);
    public static final Register f1 = new Register(33, 1, "f1", FP);
    public static final Register f2 = new Register(34, 2, "f2", FP);
    public static final Register f3 = new Register(35, 3, "f3", FP);
    public static final Register f4 = new Register(36, 4, "f4", FP);
    public static final Register f5 = new Register(37, 5, "f5", FP);
    public static final Register f6 = new Register(38, 6, "f6", FP);
    public static final Register f7 = new Register(39, 7, "f7", FP);
    public static final Register f8 = new Register(40, 8, "f8", FP);
    public static final Register f9 = new Register(41, 9, "f9", FP);
    public static final Register f10 = new Register(42, 10, "f10", FP);
    public static final Register f11 = new Register(43, 11, "f11", FP);
    public static final Register f12 = new Register(44, 12, "f12", FP);
    public static final Register f13 = new Register(45, 13, "f13", FP);
    public static final Register f14 = new Register(46, 14, "f14", FP);
    public static final Register f15 = new Register(47, 15, "f15", FP);
    public static final Register f16 = new Register(48, 16, "f16", FP);
    public static final Register f17 = new Register(49, 17, "f17", FP);
    public static final Register f18 = new Register(50, 18, "f18", FP);
    public static final Register f19 = new Register(51, 19, "f19", FP);
    public static final Register f20 = new Register(52, 20, "f20", FP);
    public static final Register f21 = new Register(53, 21, "f21", FP);
    public static final Register f22 = new Register(54, 22, "f22", FP);
    public static final Register f23 = new Register(55, 23, "f23", FP);
    public static final Register f24 = new Register(56, 24, "f24", FP);
    public static final Register f25 = new Register(57, 25, "f25", FP);
    public static final Register f26 = new Register(58, 26, "f26", FP);
    public static final Register f27 = new Register(59, 27, "f27", FP);
    public static final Register f28 = new Register(60, 28, "f28", FP);
    public static final Register f29 = new Register(61, 29, "f29", FP);
    public static final Register f30 = new Register(62, 30, "f30", FP);
    public static final Register f31 = new Register(63, 31, "f31", FP);

    // @formatter:off
    public static final RegisterArray fpRegisters = new RegisterArray(
        f0,  f1,  f2,  f3,  f4,  f5,  f6,  f7,
        f8,  f9,  f10, f11, f12, f13, f14, f15,
        f16, f17, f18, f19, f20, f21, f22, f23,
        f24, f25, f26, f27, f28, f29, f30, f31
    );
    // @formatter:on

    // @formatter:off
    public static final RegisterArray allRegisters = new RegisterArray(
        x0,  x1,  x2,  x3,  x4,  x5,  x6,  x7,
        x8,  x9,  x10, x11, x12, x13, x14, x15,
        x16, x17, x18, x19, x20, x21, x22, x23,
        x24, x25, x26, x27, x28, x29, x30, x31,

        f0,  f1,  f2,  f3,  f4,  f5,  f6,  f7,
        f8,  f9,  f10, f11, f12, f13, f14, f15,
        f16, f17, f18, f19, f20, f21, f22, f23,
        f24, f25, f26, f27, f28, f29, f30, f31
    );
    // @formatter:on

    /**
     * Basic set of CPU features mirroring what is returned from the mcpuid register. See:
     * {@code VM_Version::cpuFeatureFlags}.
     */
    public enum CPUFeature implements CPUFeatureName {
        I,
        M,
        A,
        F,
        D,
        C,
        V
    }

    private final EnumSet<CPUFeature> features;

    /**
     * Set of flags to control code emission.
     */
    public enum Flag {
        UseConservativeFence,
        AvoidUnalignedAccesses,
        NearCpool,
        TraceTraps,
        UseRVV,
        UseRVC,
        UseZba,
        UseZbb,
        UseRVVForBigIntegerShiftIntrinsics
    }

    private final EnumSet<Flag> flags;

    public RISCV64(EnumSet<CPUFeature> features, EnumSet<Flag> flags) {
        super("riscv64", RISCV64Kind.QWORD, ByteOrder.LITTLE_ENDIAN, true, allRegisters, 0, 0, 8);
        this.features = features;
        this.flags = flags;
    }

    @Override
    public EnumSet<CPUFeature> getFeatures() {
        return features;
    }

    public EnumSet<Flag> getFlags() {
        return flags;
    }

    @Override
    public PlatformKind getPlatformKind(JavaKind javaKind) {
        switch (javaKind) {
            case Boolean:
            case Byte:
                return RISCV64Kind.BYTE;
            case Short:
            case Char:
                return RISCV64Kind.WORD;
            case Int:
                return RISCV64Kind.DWORD;
            case Long:
            case Object:
                return RISCV64Kind.QWORD;
            case Float:
                return RISCV64Kind.SINGLE;
            case Double:
                return RISCV64Kind.DOUBLE;
            default:
                return null;
        }
    }

    @Override
    public boolean canStoreValue(RegisterCategory category, PlatformKind platformKind) {
        RISCV64Kind kind = (RISCV64Kind) platformKind;
        if (kind.isInteger()) {
            return category.equals(CPU);
        } else if (kind.isFP()) {
            return category.equals(FP);
        }
        return false;
    }

    @Override
    public RISCV64Kind getLargestStorableKind(RegisterCategory category) {
        if (category.equals(CPU)) {
            return RISCV64Kind.QWORD;
        } else if (category.equals(FP)) {
            return RISCV64Kind.DOUBLE;
        } else {
            return null;
        }
    }
}
