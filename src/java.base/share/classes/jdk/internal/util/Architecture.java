/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.util;

/**
 * System architecture enum values.
 * Each architecture has a matching {@code public static boolean isXXX()} method
 * that is true when running on that architecture.
 * The enum values correspond to architectures as named in the build system.
 * See the values of `OPENJDK_TARGET_ARCH_OSARCH`.
 * The order and ordinal values must match the values defined in OperatingSystemProps.java.template.
 */
public enum Architecture {
    X64(true),        // Represents AMD64 and X86_64
    X86(false),
    IA64(true),
    ARM(false),
    AARCH64(true),
    RISCV64(true),
    S390X(true),
    PPC64LE(true),
    ;

    // Cache a copy of the array for lightweight indexing
    private static final Architecture[] archValues = Architecture.values();

    /**
     * 64-bit Architecture = true; false = 32-bit/other.
     */
    private final boolean is64Bit;

    /**
     * Construct an Architecture enum.
     * @param is64Bit true if the architecture uses 64-bit addressing.
     */
    Architecture(boolean is64Bit) {
        this.is64Bit = is64Bit;
    }

    /**
     * {@return {@code true} if the current architecture is X64, Aka amd64}
     */
    public static boolean isX64() {
        return OperatingSystemProps.TARGET_ARCH_IS_X64;
    }

    /**
     * {@return {@code true} if the current architecture is X86}
     */
    public static boolean isX86() {
        return OperatingSystemProps.TARGET_ARCH_IS_X86;
    }

    /**
     * {@return {@code true} if the current architecture is IA64}
     */
    public static boolean isIA64() {
        return OperatingSystemProps.TARGET_ARCH_IS_IA64;
    }

    /**
     * {@return {@code true} if the current architecture is Arm}
     */
    public static boolean isArm() {
        return OperatingSystemProps.TARGET_ARCH_IS_ARM;
    }

    /**
     * {@return {@code true} if the current architecture is RISCV64}
     */
    public static boolean isRiscv64() {
        return OperatingSystemProps.TARGET_ARCH_IS_RISCV64;
    }

    /**
     * {@return {@code true} if the current architecture is S390X}
     */
    public static boolean isS390X() {
        return OperatingSystemProps.TARGET_ARCH_IS_S390X;
    }

    /**
     * {@return {@code true} if the current architecture is PPC64LE}
     */
    public static boolean isPpc64le() {
        return OperatingSystemProps.TARGET_ARCH_IS_PPC64LE;
    }

    /**
     * {@return {@code true} if the current architecture is AARCH64}
     */
    public static boolean isAarch64() {
        return OperatingSystemProps.TARGET_ARCH_IS_AARCH64;
    }

    /**
     * {@return return the current architecture}
     */
    public static Architecture current() {
        return archValues[OperatingSystemProps.CURRENT_ARCH_ORDINAL];
    }

    /**
     * {@return true if the architecture uses 64-bit addressing}
     */
    public boolean is64Bit() {
        return is64Bit;
    }
}
