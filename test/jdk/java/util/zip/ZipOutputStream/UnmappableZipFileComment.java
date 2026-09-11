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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipOutputStream;

import static java.io.OutputStream.nullOutputStream;
import static org.junit.jupiter.api.Assertions.assertThrows;

/* @test
 * @bug 8380542
 * @summary ZipOutputStream.setComment should throw IllegalArgumentException for unmappable characters in comment
 * @run junit ${test.main.class}
 */
public class UnmappableZipFileComment {
    /**
     * Verify that calling ZipOutputStream.setComment with an unmappable
     * comment is rejected with a IllegalArgumentException.
     *
     * @throws IOException if an unexpected IO error occurs
     */
    @Test
    void rejectUnmappableZipFileComment() throws IOException {
        // Charset used when creating the ZIP file
         Charset charset = StandardCharsets.US_ASCII;
        // 'ø' is an unmappable character in US_ASCII
        String comment = "\u00f8";
        try (var out = new ZipOutputStream(nullOutputStream(), charset)) {
            assertThrows(IllegalArgumentException.class, () -> out.setComment(comment));
        }
    }
}
