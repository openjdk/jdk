/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.util.OperatingSystem;

import static jdk.internal.util.OperatingSystem.AIX;
import static jdk.internal.util.OperatingSystem.Linux;
import static jdk.internal.util.OperatingSystem.MacOS;
import static jdk.internal.util.OperatingSystem.Windows;

import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @test
 * @summary test OperatingSystem enum
 * @modules java.base/jdk.internal.util
 * @run junit OSTest
 */

public class OSTest {
    /**
     * Test consistency of System property "os.name" with OperatingSystem.current().
     */
    @Test
    public void os_nameVsCurrent() {
        String osName = System.getProperty("os.name").substring(0, 3).toLowerCase(Locale.ROOT);
        OperatingSystem os = switch (osName) {
            case "win" -> Windows;
            case "lin" -> Linux;
            case "mac" -> MacOS;
            case "aix" -> AIX;
            default    -> fail("Unknown os.name: " + osName);
        };
        assertEquals(OperatingSystem.current(), os, "mismatch in OperatingSystem.current vs " + osName);
    }

    /**
     * Test various OperatingSystem enum values vs boolean isXXX() methods.
     * @return a stream of arguments for parameterized test
     */
    private static Stream<Arguments> osParams() {
        return Stream.of(
                Arguments.of(Linux, OperatingSystem.isLinux()),
                Arguments.of(Windows, OperatingSystem.isWindows()),
                Arguments.of(MacOS, OperatingSystem.isMacOS()),
                Arguments.of(AIX, OperatingSystem.isAix())
        );
    }

    @ParameterizedTest
    @MethodSource("osParams")
    public void isXXX(OperatingSystem os, boolean isXXX) {
        OperatingSystem current = OperatingSystem.current();
        assertEquals(os == current, isXXX,
                "Mismatch " + os + " == " + current + " vs is" + os);
    }
 }
