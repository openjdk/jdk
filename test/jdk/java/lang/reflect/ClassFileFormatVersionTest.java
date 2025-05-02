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

import java.lang.classfile.ClassFile;
import java.lang.reflect.ClassFileFormatVersion;
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static java.lang.reflect.ClassFileFormatVersion.CURRENT_PREVIEW;
import static java.lang.reflect.ClassFileFormatVersion.latest;
import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @bug 8355536
 * @summary Sanity test for ClassFileFormatVersion
 * @run junit ClassFileFormatVersionTest
 */
class ClassFileFormatVersionTest {
    @Test
    void testLatest() {
        var latest = ClassFileFormatVersion.latest();
        assertNotSame(CURRENT_PREVIEW, latest);
        assertEquals(ClassFile.latestMajorVersion(), latest.major());
        assertTrue(latest.isSupported());
        assertEquals(ClassFileFormatVersion.values().length - 2, latest.ordinal());
    }

    @Test
    void testCurrentPreview() {
        assertTrue(ClassFileFormatVersion.latest().compareTo(CURRENT_PREVIEW) < 0);
        assertEquals(ClassFile.latestMajorVersion(), CURRENT_PREVIEW.major());
        assertFalse(CURRENT_PREVIEW.isSupported());
        assertEquals(ClassFileFormatVersion.values().length - 1, CURRENT_PREVIEW.ordinal());
        assertEquals(latest().runtimeVersion(), CURRENT_PREVIEW.runtimeVersion());
    }

    static Stream<ClassFileFormatVersion> actualCffvs() {
        return Arrays.stream(ClassFileFormatVersion.values()).filter(ClassFileFormatVersion::isSupported);
    }

    @ParameterizedTest
    @MethodSource("actualCffvs")
    void testEachVersion(ClassFileFormatVersion cffv) {
        if (cffv.compareTo(ClassFileFormatVersion.RELEASE_6) >= 0) {
            assertEquals(cffv.major() - 44, cffv.runtimeVersion().feature());
            assertEquals(cffv, ClassFileFormatVersion.valueOf(cffv.runtimeVersion()));
        }
        if (cffv != ClassFileFormatVersion.RELEASE_0) {
            assertEquals(cffv, ClassFileFormatVersion.fromMajor(cffv.major()));
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
            assertTrue(cffv.isSupported());
            assertEquals(i, cffv.major());
            assertNotSame(CURRENT_PREVIEW, cffv);
        }
        assertThrows(IllegalArgumentException.class, () -> ClassFileFormatVersion.fromMajor(ClassFile.latestMajorVersion() + 1));
    }
}
