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
import static jdk.internal.util.Architecture.ARM;
import static jdk.internal.util.Architecture.IA64;
import static jdk.internal.util.Architecture.PPC64LE;
import static jdk.internal.util.Architecture.RISCV64;
import static jdk.internal.util.Architecture.S390X;
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
 * @summary Verify Architecture enum matches system property os.arch
 * @modules java.base/jdk.internal.util
 * @run junit ArchTest
 */
public class ArchTest {
    /**
     * Test consistency of System property "os.arch" with Architecture.current().
     */
    @Test
    public void arch_nameVsCurrent() {
        String osArch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        System.out.printf("System property os.arch: \"%s\", Architecture.current(): \"%s\"%n",
                osArch, Architecture.current());
        Architecture arch = switch (osArch) {
            case "x86_64" -> X64;
            case "x86" -> X86;
            case "amd64" -> X64;  // Is alias for X86_64
            case "ia64" -> IA64;  // unverified
            case "arm" -> ARM;  // unverified
            case "aarch64" -> AARCH64;
            case "riscv64" -> RISCV64;  // unverified
            case "s390x" -> S390X;  // unverified
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
                Arguments.of(IA64, Architecture.isIA64()),
                Arguments.of(ARM, Architecture.isArm()),
                Arguments.of(AARCH64, Architecture.isAarch64()),
                Arguments.of(RISCV64, Architecture.isRiscv64()),
                Arguments.of(S390X, Architecture.isS390X()),
                Arguments.of(PPC64LE, Architecture.isPpc64le())
        );
    }

    @ParameterizedTest
    @MethodSource("archParams")
    public void isArch(Architecture arch, boolean isArch) {
        Architecture current = Architecture.current();
        assertEquals(arch == current, isArch,
                "Mismatch " + arch + " == " + current + " vs is" + arch);
    }
}
