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
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import jdk.internal.util.Architecture;
import jdk.internal.misc.Unsafe;

import static jdk.internal.util.Architecture.AARCH64;
import static jdk.internal.util.Architecture.ARM;
import static jdk.internal.util.Architecture.LOONGARCH64;
import static jdk.internal.util.Architecture.MIPSEL;
import static jdk.internal.util.Architecture.MIPS64EL;
import static jdk.internal.util.Architecture.PPC;
import static jdk.internal.util.Architecture.PPC64;
import static jdk.internal.util.Architecture.PPC64LE;
import static jdk.internal.util.Architecture.RISCV64;
import static jdk.internal.util.Architecture.S390;
import static jdk.internal.util.Architecture.SPARCV9;
import static jdk.internal.util.Architecture.X64;
import static jdk.internal.util.Architecture.X86;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @test
 * @bug 8304915 8308452 8310982
 * @summary Verify Architecture enum maps to system property os.arch
 * @modules java.base/jdk.internal.util
 * @modules java.base/jdk.internal.misc
 * @run junit ArchTest
 */
public class ArchTest {
    private static final boolean IS_BIG_ENDIAN = Unsafe.getUnsafe().isBigEndian();

    private static final boolean IS_64BIT_ADDRESS = Unsafe.getUnsafe().addressSize() == 8;

    /**
     * Test data for Architecture name vs Arch enums, address bits, endian-ness and boolean isXXX() methods..
     * Each Argument contains:
     *  - the common os.arch name,
     *  - the Architecture Enum,
     *  - address bits 32/64,
     *  - the byte-order (little or big),
     *  - the result of invoking the architecture specific static method
     * @return a stream of arguments for parameterized tests
     */
    private static Stream<Arguments> archParams() {
        // In alphabetical order
        return Stream.of(
                Arguments.of("aarch64", AARCH64, 64, ByteOrder.LITTLE_ENDIAN, Architecture.isAARCH64()),
                Arguments.of("amd64", X64, 64, ByteOrder.LITTLE_ENDIAN, Architecture.isX64()),
                Arguments.of("arm", ARM, 32, ByteOrder.LITTLE_ENDIAN, Architecture.isARM()),
                Arguments.of("i386", X86, 32, ByteOrder.LITTLE_ENDIAN, Architecture.isX86()),
                Arguments.of("loongarch64", LOONGARCH64, 64, ByteOrder.LITTLE_ENDIAN, Architecture.isLOONGARCH64()),
                Arguments.of("mips64el", MIPS64EL, 64, ByteOrder.LITTLE_ENDIAN, Architecture.isMIPS64EL()),
                Arguments.of("mipsel", MIPSEL, 32, ByteOrder.LITTLE_ENDIAN, Architecture.isMIPSEL()),
                Arguments.of("ppc", PPC, 32, ByteOrder.BIG_ENDIAN, Architecture.isPPC()),
                Arguments.of("ppc64", PPC64, 64, ByteOrder.BIG_ENDIAN, Architecture.isPPC64()),
                Arguments.of("ppc64le", PPC64LE, 64, ByteOrder.LITTLE_ENDIAN, Architecture.isPPC64LE()),
                Arguments.of("riscv64", RISCV64, 64, ByteOrder.LITTLE_ENDIAN, Architecture.isRISCV64()),
                Arguments.of("s390", S390, 64, ByteOrder.BIG_ENDIAN, Architecture.isS390()),
                Arguments.of("s390x", S390, 64, ByteOrder.BIG_ENDIAN, Architecture.isS390()),
                Arguments.of("sparcv9", SPARCV9, 64, ByteOrder.BIG_ENDIAN, Architecture.isSPARCV9()),
                Arguments.of("x64", X64, 64, ByteOrder.LITTLE_ENDIAN, Architecture.isX64()),
                Arguments.of("x86", X86, 32, ByteOrder.LITTLE_ENDIAN, Architecture.isX86()),
                Arguments.of("x86_64", X64, 64, ByteOrder.LITTLE_ENDIAN, Architecture.isX64())
        );
    }


    /**
     * Test consistency of System property "os.arch" with Architecture.current().
     */
    @Test
    public void nameVsCurrent() {
        String osArch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        System.err.printf("System property os.arch: \"%s\", Architecture.current(): \"%s\"%n",
                osArch, Architecture.current());

        // Map os.arch system property to expected Architecture
        List<Architecture> argList = archParams()
                .filter(p -> p.get()[0].equals(osArch))
                .map(a -> (Architecture)a.get()[1])
                .toList();
        assertEquals(1, argList.size(), osArch + " too few or too many matching system property os.arch cases: " + argList);
        assertEquals(Architecture.current(), argList.get(0), "mismatch in Architecture.current vs " + osArch);
    }

    @ParameterizedTest
    @MethodSource("archParams")
    public void checkParams(String archName, Architecture arch, int addrSize, ByteOrder byteOrder, boolean isArch) {
        Architecture actual = Architecture.lookupByName(archName);
        assertEquals(actual, arch, "Wrong Architecture from lookupByName");

        actual = Architecture.lookupByName(archName.toUpperCase(Locale.ROOT));
        assertEquals(actual, arch, "Wrong Architecture from lookupByName (upper-case)");

        actual = Architecture.lookupByName(archName.toLowerCase(Locale.ROOT));
        assertEquals(actual, arch, "Wrong Architecture from lookupByName (lower-case)");

        assertEquals(addrSize, actual.addressSize(), "Wrong address size");
        assertEquals(byteOrder, actual.byteOrder(), "Wrong byteOrder");

        Architecture current = Architecture.current();
        assertEquals(arch == current, isArch,
                "Method is" + arch + "(): returned " + isArch + ", should be (" + arch + " == " + current + ")");
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
