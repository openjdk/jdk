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

package jdk.jpackage.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;


class RtfConverterTest {

    @Test
    void test_createSimple_dir(@TempDir Path workDir) throws IOException {

        assertEquals(Optional.empty(), RtfConverter.createSimple(workDir));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Empty value to exercise the case when the file's content is shorter than the RTF's header
            "",
            // Value to exercise the case when the file's content is shorter than the RTF's header
            "Hello",
            "Hello Duke!",
    })
    void test_createSimple_text_file(String text, @TempDir Path workDir) throws IOException {

        final var licenseFile = workDir.resolve("license");

        Files.writeString(licenseFile, text);

        final var conv = RtfConverter.createSimple(licenseFile);

        assertTrue(conv.isPresent());

        conv.orElseThrow().convert(licenseFile);

        assertEquals(Optional.empty(), RtfConverter.createSimple(licenseFile));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Arial;}}\\f0\\fs24 Hello, Duke!}",
    })
    void test_createSimple_rtf_file(String text, @TempDir Path workDir) throws IOException {

        final var licenseFile = workDir.resolve("license");

        Files.writeString(licenseFile, text);

        assertEquals(Optional.empty(), RtfConverter.createSimple(workDir));
    }
}
