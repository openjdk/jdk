/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import java.nio.ByteOrder;
import java.util.Locale;

/**
 * System architecture enum values.
 * Each architecture, except OTHER, has a matching {@code public static boolean isXXX()} method
 * that is true when running on that architecture.
 * The values of `OPENJDK_TARGET_CPU` from the build are mapped to the
 * architecture values.
 */
public enum Architecture {
    /*
     * An unknown architecture not specifically named.
     * The addrSize and ByteOrder values are those of the current architecture.
     */
    AARCH64(64, ByteOrder.LITTLE_ENDIAN),
    ARM(32, ByteOrder.LITTLE_ENDIAN),
    LOONGARCH64(64, ByteOrder.LITTLE_ENDIAN),
    MIPSEL(32, ByteOrder.LITTLE_ENDIAN),
    MIPS64EL(64, ByteOrder.LITTLE_ENDIAN),
    OTHER(is64bit() ? 64 : 32, ByteOrder.nativeOrder()),
    PPC(32, ByteOrder.BIG_ENDIAN),
    PPC64(64, ByteOrder.BIG_ENDIAN),
    PPC64LE(64, ByteOrder.LITTLE_ENDIAN),
    RISCV64(64, ByteOrder.LITTLE_ENDIAN),
    S390(64, ByteOrder.BIG_ENDIAN),
    SPARCV9(64, ByteOrder.BIG_ENDIAN),
    X86(32, ByteOrder.LITTLE_ENDIAN),
    X64(64, ByteOrder.LITTLE_ENDIAN),  // Represents AMD64 and X86_64
    ;

    private final int addrSize;
    private final ByteOrder byteOrder;

    /**
     * Construct an Architecture with number of address bits and byte order.
     * @param addrSize number of address bits, typically 64 or 32
     * @param byteOrder the byte order, big-endian or little-endian
     */
    Architecture(int addrSize, ByteOrder byteOrder) {
        this.addrSize = addrSize;
        this.byteOrder = byteOrder;
    }

    /**
     * {@return the number of address bits, typically 64 or 32}
     */
    public int addressSize() {
        return addrSize;
    }

    /**
     * {@return the byte order, {@link ByteOrder#BIG_ENDIAN} or {@link ByteOrder#LITTLE_ENDIAN}}
     */
    public ByteOrder byteOrder() {
        return byteOrder;
    }

    /**
     * {@return the Architecture by name or an alias for the architecture}
     * The names are mapped to upper case before mapping to an Architecture.
     * @param archName an Architecture name or alias for the architecture.
     * @throws IllegalArgumentException if the name is not an alias or an Architecture name
     */
    public static Architecture lookupByName(String archName) {
        archName = archName.toUpperCase(Locale.ROOT); // normalize to uppercase
        return switch (archName) {
            case "X86_64", "AMD64" -> X64;
            case "I386" -> X86;
            case "S390X" -> S390;
            default -> Architecture.valueOf(archName);
        };
    }

    /**
     * Returns the Architecture of the built architecture.
     * Build time names are mapped to respective uppercase enum values.
     * Names not recognized are mapped to Architecture.OTHER.
     */
    private static Architecture initArch(String archName) {
        try {
            return lookupByName(archName);
        } catch (IllegalArgumentException ile) {
            return Architecture.OTHER;
        }
    }

    // Initialize the architecture by mapping aliases and names to the enum values.
    private static final Architecture CURRENT_ARCH = initArch(PlatformProps.CURRENT_ARCH_STRING);

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
     * {@return {@code true} if the current architecture is PPC, big-endian}
     */
    @ForceInline
    public static boolean isPPC() {
        return PlatformProps.TARGET_ARCH_IS_PPC;
    }

    /**
     * {@return {@code true} if the current architecture is PPC64, big-endian}
     */
    @ForceInline
    public static boolean isPPC64() {
        return PlatformProps.TARGET_ARCH_IS_PPC64;
    }

    /**
     * {@return {@code true} if the current architecture is PPC64, little-endian}
     */
    @ForceInline
    public static boolean isPPC64LE() {
        return PlatformProps.TARGET_ARCH_IS_PPC64LE;
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
     * {@return {@code true} if the current architecture is SPARCV9}
     */
    @ForceInline
    public static boolean isSPARCV9() {
        return PlatformProps.TARGET_ARCH_IS_SPARCV9;
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
}
