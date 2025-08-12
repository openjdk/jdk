/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal;

import static jdk.jpackage.internal.OSVersionCondition.WindowsVersion.getExecutableOSVersion;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.condition.OS.WINDOWS;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import jdk.jpackage.internal.OSVersionCondition.WindowsVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;


public class ExecutableOSVersionTest {

    @Test
    @EnabledOnOs(WINDOWS)
    public void testWindowsVersionGetExecutableOSVersion() {
        final var javaHome = Path.of(System.getProperty("java.home"));

        final var javaExeVer = getExecutableOSVersion(javaHome.resolve("bin/java.exe"));

        assertTrue(javaExeVer.majorOSVersion() > 0);
        assertTrue(javaExeVer.minorOSVersion() >= 0);

        final var javaDllVer = getExecutableOSVersion(javaHome.resolve("bin/java.dll"));

        assertEquals(javaExeVer, javaDllVer);
    }

    @ParameterizedTest
    @EnabledOnOs(WINDOWS)
    @MethodSource
    public void testWindowsVersionDescendingOrder(List<WindowsVersion> unsorted, WindowsVersion expectedFirst) {
        final var actualFirst = unsorted.stream().sorted(WindowsVersion.descendingOrder()).findFirst().orElseThrow();
        assertEquals(expectedFirst, actualFirst);
    }

    public static Stream<Object[]> testWindowsVersionDescendingOrder() {
        return Stream.<Object[]>of(
                new Object[] { List.of(wver(5, 0), wver(5, 1), wver(4, 9)), wver(5, 1) },
                new Object[] { List.of(wver(5, 0)), wver(5, 0) },
                new Object[] { List.of(wver(5, 1), wver(5, 1), wver(5, 0)), wver(5, 1) },
                new Object[] { List.of(wver(3, 11), wver(4, 8), wver(5, 6)), wver(5, 6) },
                new Object[] { List.of(wver(3, 11), wver(3, 9), wver(3, 13)), wver(3, 13) }
        );
    }

    private final static WindowsVersion wver(int major, int minor) {
        return new WindowsVersion(major, minor);
    }
}
