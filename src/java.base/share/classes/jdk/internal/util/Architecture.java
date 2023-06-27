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
package jdk.internal.util;

import jdk.internal.vm.annotation.ForceInline;
import java.util.Locale;

/**
 * System architecture enum values.
 * Each architecture, except OTHER, has a matching {@code public static boolean isXXX()} method
 * that is true when running on that architecture.
 * The values of `OPENJDK_TARGET_CPU` from the build are mapped to the
 * architecture values.
 */
public enum Architecture {
    OTHER,      // An unknown architecture not specifically named
    X64,        // Represents AMD64 and X86_64
    X86,
    AARCH64,
    ARM,
    RISCV64,
    LOONGARCH64,
    S390,
    PPC64,
    MIPSEL,
    MIPS64EL
    ;

    private static Architecture CURRENT_ARCH = initArch(PlatformProps.CURRENT_ARCH_STRING);

    /**
     * {@return {@code true} if the current architecture is X64, Aka amd64}
     */
    @ForceInline
    public static boolean isX64() {
        return PlatformProps.TARGET_ARCH_IS_X64;
    }

    /**
     * {@return {@code true} if the current architecture is X86}
     */
    @ForceInline
    public static boolean isX86() {
        return PlatformProps.TARGET_ARCH_IS_X86;
    }

    /**
     * {@return {@code true} if the current architecture is RISCV64}
     */
    @ForceInline
    public static boolean isRISCV64() {
        return PlatformProps.TARGET_ARCH_IS_RISCV64;
    }

    /**
     * {@return {@code true} if the current architecture is LOONGARCH64}
     */
    @ForceInline
    public static boolean isLOONGARCH64() {
        return PlatformProps.TARGET_ARCH_IS_LOONGARCH64;
    }

    /**
     * {@return {@code true} if the current architecture is S390}
     */
    @ForceInline
    public static boolean isS390() {
        return PlatformProps.TARGET_ARCH_IS_S390;
    }

    /**
     * {@return {@code true} if the current architecture is PPC64}
     * Use {@link #isLittleEndian()} to determine big or little endian.
     */
    @ForceInline
    public static boolean isPPC64() {
        return PlatformProps.TARGET_ARCH_IS_PPC64;
    }

    /**
     * {@return {@code true} if the current architecture is ARM}
     */
    @ForceInline
    public static boolean isARM() {
        return PlatformProps.TARGET_ARCH_IS_ARM;
    }

    /**
     * {@return {@code true} if the current architecture is AARCH64}
     */
    @ForceInline
    public static boolean isAARCH64() {
        return PlatformProps.TARGET_ARCH_IS_AARCH64;
    }

    /**
     * {@return {@code true} if the current architecture is MIPSEL}
     */
    @ForceInline
    public static boolean isMIPSEL() {
        return PlatformProps.TARGET_ARCH_IS_MIPSEL;
    }

    /**
     * {@return {@code true} if the current architecture is MIPS64EL}
     */
    @ForceInline
    public static boolean isMIPS64EL() {
        return PlatformProps.TARGET_ARCH_IS_MIPS64EL;
    }

    /**
     * {@return the current architecture}
     */
    public static Architecture current() {
        return CURRENT_ARCH;
    }

    /**
     * {@return {@code true} if the current architecture is 64-bit}
     */
    @ForceInline
    public static boolean is64bit() {
        return PlatformProps.TARGET_ARCH_BITS == 64;
    }

    /**
     * {@return {@code true} if the current architecture is little-endian}
     */
    @ForceInline
    public static boolean isLittleEndian() {
        return PlatformProps.TARGET_ARCH_LITTLE_ENDIAN;
    }


    /**
     * Returns the Architecture of the built architecture.
     * Build time names are mapped to respective uppercase enum values.
     * Names not recognized are mapped to Architecture.OTHER.
     */
    private static Architecture initArch(String archName) {
        try {
            return Architecture.valueOf(archName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ile) {
            return Architecture.OTHER;
        }
    }
}
