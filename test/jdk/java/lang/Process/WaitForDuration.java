/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8336479
 * @summary Tests for Process.waitFor(Duration)
 * @run junit WaitForDuration
 */

import java.io.IOException;
import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

public class WaitForDuration {
    static Stream<Arguments> durations() {
        return Stream.of(
            Arguments.of(Duration.ZERO, false),
            Arguments.of(Duration.ofSeconds(-100), false),
            Arguments.of(Duration.ofSeconds(100), true),
            Arguments.of(Duration.ofSeconds(Long.MAX_VALUE), true), // nano overflow
            Arguments.of(Duration.ofSeconds(Long.MIN_VALUE), false) // nano underflow
        );
    }

    @ParameterizedTest
    @MethodSource("durations")
    void testEdgeDurations(Duration d, boolean expected)
            throws IOException, InterruptedException {
        assertEquals(expected,
            new ProcessBuilder("sleep", "3").start().waitFor(d));
    }

    @Test
    void testNullDuration() throws IOException, InterruptedException {
        assertThrows(NullPointerException.class, () ->
            new ProcessBuilder("sleep", "3").start().waitFor(null));
    }
}
