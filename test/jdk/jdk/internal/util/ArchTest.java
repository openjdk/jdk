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
import java.util.Locale;
import java.util.stream.Stream;

import jdk.internal.util.Architecture;

import static jdk.internal.util.Architecture.AARCH64;
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
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @test
 * @bug 8304915
 * @summary Verify Architecture enum maps to system property os.arch
 * @modules java.base/jdk.internal.util
 * @run junit ArchTest
 */
public class ArchTest {
    /**
     * Test consistency of System property "os.arch" with Architecture.current().
     */
    @Test
    public void nameVsCurrent() {
        String osArch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        System.out.printf("System property os.arch: \"%s\", Architecture.current(): \"%s\"%n",
                osArch, Architecture.current());
        Architecture arch = switch (osArch) {
            case "x86_64" -> X64;
            case "x86" -> X86;
            case "i386" -> X86;
            case "amd64" -> X64;  // Is alias for X86_64
            case "aarch64" -> AARCH64;
            case "riscv64" -> RISCV64;  // unverified
            case "s390x", "s390" -> S390;  // unverified
            case "ppc64le" -> PPC64LE;  // unverified
            default    -> fail("Unknown os.arch: " + osArch);
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
                Arguments.of(RISCV64, Architecture.isRISCV64()),
                Arguments.of(S390, Architecture.isS390()),
                Arguments.of(PPC64LE, Architecture.isPPC64LE())
        );
    }

    @ParameterizedTest
    @MethodSource("archParams")
    public void isArch(Architecture arch, boolean isArch) {
        Architecture current = Architecture.current();
        assertEquals(arch == current, isArch,
                "Mismatch " + arch + " == " + current + " vs is" + arch);
    }

    /**
     * Test that Architecture.is64bit() matches Architecture.current().
     */
    @Test
    public void is64BitVsCurrent() {
        Architecture current = Architecture.current();
        boolean expected64Bit = switch (current) {
            case X64 -> true;
            case X86 -> false;
            case AARCH64 -> true;
            case RISCV64 -> true;
            case S390 -> true;
            case PPC64LE -> true;
        };
        assertEquals(Architecture.is64bit(), expected64Bit, "mismatch in is64bit");
    }
}
