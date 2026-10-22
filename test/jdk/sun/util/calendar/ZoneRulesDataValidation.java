/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8393882
 * @summary Tests the validation of the ZoneRules data
 * @modules java.base/sun.util.calendar
 * @run junit ZoneRulesDataValidation
 */

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.StreamCorruptedException;
import java.nio.ByteBuffer;
import java.util.List;
import sun.util.calendar.ZoneInfoFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class ZoneRulesDataValidation {
    private static List<Arguments> testOffsets() {
        return List.of(
            Arguments.of(1, true, 1_024), // stdOffsets
            Arguments.of(6, true, 1_024), // savOffsets
            Arguments.of(11, false, 16) // ruleSize
        );
    }

    private static ByteBuffer validData() {
        return ByteBuffer.allocate(12)
                .put((byte)1) // ZoneRules format
                .putInt(0)    // stdSize
                .put((byte)0) // stdOffsets[0]: UTC
                .putInt(0)    // savSize
                .put((byte)0) // savOffsets[0]: UTC
                .put((byte)0); // ruleSize
    }

    @Test
    void invalidTypeTest() throws IOException {
        var data = validData();
        data.put(0, (byte)0);
        final var in = new DataInputStream(new ByteArrayInputStream(data.array()));
        assertThrows(StreamCorruptedException.class, () -> ZoneInfoFile.getZoneInfo(in, "Test"));
        in.close();
    }

    @ParameterizedTest
    @MethodSource("testOffsets")
    void tooLargeDataSizeTest(int offset, boolean isInt, int max) throws IOException {
        var data = validData();
        if (isInt) {
            data.putInt(offset, max + 1);
        } else {
            data.put(offset, (byte)(max + 1));
        }
        final var in = new DataInputStream(new ByteArrayInputStream(data.array()));
        assertThrows(StreamCorruptedException.class, () -> ZoneInfoFile.getZoneInfo(in, "Test"));
        in.close();
    }

    @ParameterizedTest
    @MethodSource("testOffsets")
    void negativeDataSizeTest(int offset, boolean isInt) throws IOException {
        var data = validData();
        if (isInt) {
            data.putInt(offset, -1);
        } else {
            data.put(offset, (byte)(-1));
        }
        data.position(0);
        final var in = new DataInputStream(new ByteArrayInputStream(data.array()));
        assertThrows(StreamCorruptedException.class, () -> ZoneInfoFile.getZoneInfo(in, "Test"));
        in.close();
    }

    @Test
    void validDataSizeTest() throws IOException {
        final var in = new DataInputStream(new ByteArrayInputStream(validData().array()));
        assertDoesNotThrow(() -> ZoneInfoFile.getZoneInfo(in, "Test"));
        in.close();
    }
}
