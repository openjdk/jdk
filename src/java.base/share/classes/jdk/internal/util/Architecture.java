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

import jdk.internal.vm.annotation.ForceInline;

/**
 * System architecture enum values.
 * Each architecture has a matching {@code public static boolean isXXX()} method
 * that is true when running on that architecture.
 * The enum values correspond to architectures as named in the build system.
 * See the values of `OPENJDK_TARGET_ARCH_OSARCH`.
 * The order and ordinal values must match the values defined in OperatingSystemProps.java.template.
 */
public enum Architecture {
    X64(),        // Represents AMD64 and X86_64
    X86(),
    IA64(),
    ARM(),
    AARCH64(),
    RISCV64(),
    S390X(),
    PPC64LE(),
    ;

    // Cache a copy of the array for lightweight indexing
    private static final Architecture[] archValues = Architecture.values();

    /**
     * {@return {@code true} if the current architecture is X64, Aka amd64}
     */
    @ForceInline
    public static boolean isX64() {
        return OperatingSystemProps.TARGET_ARCH_IS_X64;
    }

    /**
     * {@return {@code true} if the current architecture is X86}
     */
    @ForceInline
    public static boolean isX86() {
        return OperatingSystemProps.TARGET_ARCH_IS_X86;
    }

    /**
     * {@return {@code true} if the current architecture is IA64}
     */
    @ForceInline
    public static boolean isIA64() {
        return OperatingSystemProps.TARGET_ARCH_IS_IA64;
    }

    /**
     * {@return {@code true} if the current architecture is Arm}
     */
    @ForceInline
    public static boolean isARM() {
        return OperatingSystemProps.TARGET_ARCH_IS_ARM;
    }

    /**
     * {@return {@code true} if the current architecture is RISCV64}
     */
    @ForceInline
    public static boolean isRISCV() {
        return OperatingSystemProps.TARGET_ARCH_IS_RISCV64;
    }

    /**
     * {@return {@code true} if the current architecture is S390X}
     */
    @ForceInline
    public static boolean isS390X() {
        return OperatingSystemProps.TARGET_ARCH_IS_S390X;
    }

    /**
     * {@return {@code true} if the current architecture is PPC64LE}
     */
    @ForceInline
    public static boolean isPPC64LE() {
        return OperatingSystemProps.TARGET_ARCH_IS_PPC64LE;
    }

    /**
     * {@return {@code true} if the current architecture is AARCH64}
     */
    @ForceInline
    public static boolean isAARCH64() {
        return OperatingSystemProps.TARGET_ARCH_IS_AARCH64;
    }

    /**
     * {@return return the current architecture}
     */
    public static Architecture current() {
        return archValues[OperatingSystemProps.CURRENT_ARCH_ORDINAL];
    }

    /**
     * {@return {@code true} if the current architecture is 64-bit}
     */
    @ForceInline
    public static boolean is64bit() {
        return OperatingSystemProps.TARGET_ARCH_BITS == 64;
    }
}
