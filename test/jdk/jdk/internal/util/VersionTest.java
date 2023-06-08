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


import java.util.stream.Stream;

import jdk.internal.util.OSVersion;

import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.ParameterizedTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @test
 * @summary test jdk.internal.util.Version
 * @modules java.base/jdk.internal.util
 * @run junit VersionTest
 */

public class VersionTest {

    private static Stream<Arguments> versionParams() {
        return Stream.of(
                Arguments.of("1", new OSVersion(1, 0)),
                Arguments.of("1.2", new OSVersion(1, 2)),
                Arguments.of("1.2", new OSVersion(1, 2, 0)),
                Arguments.of("1.2.3", new OSVersion(1, 2, 3)),
                Arguments.of("1-abc", new OSVersion(1, 0, 0)), // Ignore extra
                Arguments.of("1.2-abc", new OSVersion(1, 2, 0)), // Ignore extra
                Arguments.of("1.2.3.4", new OSVersion(1, 2, 3)), // Ignore extra
                Arguments.of("1.2.3-abc", new OSVersion(1, 2, 3)) // Ignore extra
        );
    }

    @ParameterizedTest
    @MethodSource("versionParams")
    public void checkParse(String verName, OSVersion expected) {
        OSVersion actual = OSVersion.parse(verName);
        assertEquals(actual, expected, "Parsed version mismatch");
    }

    private static Stream<String> illegalVersionParams() {
        return Stream.of(
                "1.", "1.2.", "1.-abc", "1.2.-abc", // dot without digits
                "",                                 // empty
                "xaaa", "abc.xyz"                   // no initial digit
        );
    }

    @ParameterizedTest()
    @MethodSource("illegalVersionParams")
    public void checkIllegalParse(String verName) {
        Throwable th = assertThrows(IllegalArgumentException.class, () -> OSVersion.parse(verName));
        String expectedMsg = "malformed version, missing digits: " + verName;
        assertEquals(th.getMessage(), expectedMsg, "message mismatch");
    }

    private static Stream<Arguments> versionCompare() {
        return Stream.of(
                Arguments.of(new OSVersion(2, 1), new OSVersion(2, 1), 0),
                Arguments.of(new OSVersion(2, 1), new OSVersion(2, 0), +1),
                Arguments.of(new OSVersion(2, 0), new OSVersion(2, 1), -1),
                Arguments.of(new OSVersion(3, 3, 1), new OSVersion(3, 3, 1), 0),
                Arguments.of(new OSVersion(3, 3, 1), new OSVersion(3, 3, 0), +1),
                Arguments.of(new OSVersion(3, 3, 0), new OSVersion(3, 3, 1), -1),
                Arguments.of(new OSVersion(2, 0), new OSVersion(3, 0), -1),
                Arguments.of(new OSVersion(3, 0), new OSVersion(2, 0), +1)
        );
    }

    @ParameterizedTest()
    @MethodSource("versionCompare")
    public void checkVersionCompare(OSVersion v1, OSVersion v2, int expected) {
        int result1 = v1.compareTo(v2);
        assertEquals(result1, expected, "v1 vs v2");
        int result2 = v2.compareTo(v1);
        assertEquals(result1, -result2, "compare not reflexive");
    }
}
