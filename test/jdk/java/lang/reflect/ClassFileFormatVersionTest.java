/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8355536 8371953
 * @summary General tests for ClassFileFormatVersion.
 * @run junit ClassFileFormatVersionTest
 */

import java.lang.classfile.ClassFile;
import java.lang.reflect.ClassFileFormatVersion;
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static java.lang.reflect.ClassFileFormatVersion.*;
import static org.junit.jupiter.api.Assertions.*;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ClassFileFormatVersionTest {
    @Test
    void argumentChecks() {
        assertThrows(NullPointerException.class, () -> ClassFileFormatVersion.valueOf((String) null));
        assertThrows(NullPointerException.class, () -> ClassFileFormatVersion.valueOf((Runtime.Version) null));
        assertThrows(IllegalArgumentException.class, () -> ClassFileFormatVersion.valueOf("Absent"));
        var runtimeVersion = Runtime.Version.parse("99999999");
        assertThrows(IllegalArgumentException.class, () -> ClassFileFormatVersion.valueOf(runtimeVersion));
    }

    @Test
    void testLatest() {
        var latest = ClassFileFormatVersion.latest();
        assertNotSame(PREVIEW_ENABLED, latest);
        assertEquals(ClassFile.latestMajorVersion(), latest.major());
        assertEquals(Runtime.version().feature(), latest.runtimeVersion().feature());
        assertEquals(ClassFileFormatVersion.values().length - 2, latest.ordinal());
    }

    @Test
    void testPreviewEnabled() {
        var latest = ClassFileFormatVersion.latest();
        assertTrue(latest.compareTo(PREVIEW_ENABLED) < 0);
        assertEquals(latest.major(), PREVIEW_ENABLED.major());
        assertEquals(latest.runtimeVersion(), PREVIEW_ENABLED.runtimeVersion());
        assertEquals(ClassFileFormatVersion.values().length - 1, PREVIEW_ENABLED.ordinal());
    }

    @Test
    void testSequentialVersions() {
        ClassFileFormatVersion[] values = ClassFileFormatVersion.values();
        assertSame(RELEASE_0, values[0]);
        assertSame(PREVIEW_ENABLED, values[values.length - 1]);
        for (int i = 1; i < values.length - 1; i++) {
            var cffv = values[i];
            int expectedMajor = i + 44;
            assertEquals(expectedMajor, cffv.major());
            assertEquals(cffv, ClassFileFormatVersion.fromMajor(expectedMajor));
            if (cffv.compareTo(ClassFileFormatVersion.RELEASE_6) >= 0) {
                assertEquals(i, cffv.runtimeVersion().feature());
                assertEquals(cffv, ClassFileFormatVersion.valueOf(cffv.runtimeVersion()));
            } else {
                assertNull(cffv.runtimeVersion());
            }
        }
    }

    @Test
    void testFromMajor() {
        for (int i = -1; i < ClassFile.JAVA_1_VERSION; i++) {
            final int major = i;
            assertThrows(IllegalArgumentException.class, () -> ClassFileFormatVersion.fromMajor(major));
        }
        for (int i = ClassFile.JAVA_1_VERSION; i <= ClassFile.latestMajorVersion(); i++) {
            var cffv = ClassFileFormatVersion.fromMajor(i);
            assertEquals(i, cffv.major());
            assertNotSame(PREVIEW_ENABLED, cffv);
        }
        assertThrows(IllegalArgumentException.class, () -> ClassFileFormatVersion.fromMajor(ClassFile.latestMajorVersion() + 1));
    }
}
