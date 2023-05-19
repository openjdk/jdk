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
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.stream.Stream;

import jdk.internal.util.Architecture;
import jdk.internal.misc.Unsafe;

import static jdk.internal.util.Architecture.OTHER;
import static jdk.internal.util.Architecture.AARCH64;
import static jdk.internal.util.Architecture.ARM;
import static jdk.internal.util.Architecture.PPC64;
import static jdk.internal.util.Architecture.PPC64LE;
import static jdk.internal.util.Architecture.RISCV64;
import static jdk.internal.util.Architecture.S390;
import static jdk.internal.util.Architecture.X64;
import static jdk.internal.util.Architecture.X86;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @test
 * @bug 8304915
 * @summary Verify Architecture enum maps to system property os.arch
 * @modules java.base/jdk.internal.util
 * @modules java.base/jdk.internal.misc
 * @run junit ArchTest
 */
public class ArchTest {
    private static final boolean IS_BIG_ENDIAN = Unsafe.getUnsafe().isBigEndian();

    private static final boolean IS_64BIT_ADDRESS = Unsafe.getUnsafe().addressSize() == 8;

    /**
     * Test consistency of System property "os.arch" with Architecture.current().
     */
    @Test
    public void nameVsCurrent() {
        String osArch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        System.out.printf("System property os.arch: \"%s\", Architecture.current(): \"%s\"%n",
                osArch, Architecture.current());
        Architecture arch = switch (osArch) {
            case "x86_64", "amd64" -> X64;
            case "x86", "i386" -> X86;
            case "aarch64" -> AARCH64;
            case "arm" -> ARM;
            case "riscv64" -> RISCV64;
            case "s390x", "s390" -> S390;
            case "ppc64" -> PPC64;
            case "ppc64le" -> PPC64LE;
            default -> OTHER;
        };
        assertEquals(Architecture.current(), arch, "mismatch in Architecture.current vs " + osArch);
    }

    /**
     * Test various Architecture enum values vs boolean isXXX() methods.
     * @return a stream of arguments for parameterized test
     */
    private static Stream<Arguments> archParams() {
        return Stream.of(
                Arguments.of(X64, Architecture.isX64()),
                Arguments.of(X86, Architecture.isX86()),
                Arguments.of(AARCH64, Architecture.isAARCH64()),
                Arguments.of(ARM, Architecture.isARM()),
                Arguments.of(RISCV64, Architecture.isRISCV64()),
                Arguments.of(S390, Architecture.isS390()),
                Arguments.of(PPC64, Architecture.isPPC64()),
                Arguments.of(PPC64LE, Architecture.isPPC64LE()),
                Arguments.of(OTHER, false)
        );
    }

    @ParameterizedTest
    @MethodSource("archParams")
    public void isArch(Architecture arch, boolean isArch) {
        Architecture current = Architecture.current();
        assertEquals(arch == current, isArch,
                "Method is" + arch + "(): returned " + isArch + ", should be (" + arch + " == " + current + ")");
    }

    /**
     * Test various Architecture names vs Arch enums.
     * @return a stream of arguments for parameterized test
     */
    private static Stream<Arguments> archNames() {
        return Stream.of(
                Arguments.of("x64", X64, 64, ByteOrder.LITTLE_ENDIAN),
                Arguments.of("X86_64", X64, 64, ByteOrder.LITTLE_ENDIAN),
                Arguments.of("x86", X86, 32, ByteOrder.LITTLE_ENDIAN),
                Arguments.of("i386", X86, 32, ByteOrder.LITTLE_ENDIAN),
                Arguments.of("aarch64", AARCH64, 64, ByteOrder.LITTLE_ENDIAN),
                Arguments.of("arm", ARM, 32, ByteOrder.LITTLE_ENDIAN),
                Arguments.of("riscv64", RISCV64, 64, ByteOrder.LITTLE_ENDIAN),
                Arguments.of("s390", S390, 64, ByteOrder.BIG_ENDIAN),
                Arguments.of("s390x", S390, 64, ByteOrder.BIG_ENDIAN),
                Arguments.of("ppc64", PPC64, 64, ByteOrder.BIG_ENDIAN),
                Arguments.of("ppc64le", PPC64LE, 64, ByteOrder.LITTLE_ENDIAN)
        );
    }

    @ParameterizedTest
    @MethodSource("archNames")
    public void isArch(String archName, Architecture arch, int addrSize, ByteOrder byteOrder) {
        Architecture actual = Architecture.lookupByName(archName);
        assertEquals(actual, arch, "Wrong Architecture from lookupByName");

        actual = Architecture.lookupByName(archName.toUpperCase(Locale.ROOT));
        assertEquals(actual, arch, "Wrong Architecture from lookupByName (upper-case)");

        actual = Architecture.lookupByName(archName.toLowerCase(Locale.ROOT));
        assertEquals(actual, arch, "Wrong Architecture from lookupByName (lower-case)");

        assertEquals(addrSize, actual.addressSize(), "Wrong address size");
        assertEquals(byteOrder, actual.byteOrder(), "Wrong byteOrder");
    }

    /**
     * Test that Architecture.is64bit() matches Unsafe.addressSize() == 8.
     */
    @Test
    public void is64BitVsCurrent() {
        assertEquals(Architecture.is64bit(), IS_64BIT_ADDRESS,
                "Architecture.is64bit() does not match UNSAFE.addressSize() == 8");
    }

    /**
     * Test that Architecture.isLittleEndian() == !Unsafe.isBigEndian().
     */
    @Test
    public void isLittleEndianVsCurrent() {
        assertEquals(Architecture.isLittleEndian(), !IS_BIG_ENDIAN,
                "isLittleEndian does not match UNSAFE.isBigEndian()");
    }
}
