/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

/**
 * @test
 * @library ..
 * @run testng LastModifiedTest
 * @summary Test to validate that java.nio.Files returns the same value
 * as java.io.File
 */
public class LastModifiedTest {

    private static final Instant MILLISECOND_PRECISION = Instant.ofEpochMilli(1999L);

    @Test
    public void verifyLastModifiedTime() throws IOException {
        File tempFile = Files.createTempFile("MillisecondPrecisionTest", "txt").toFile();
        try {
            tempFile.setLastModified(MILLISECOND_PRECISION.toEpochMilli());

            long ioTimestamp = tempFile.lastModified();
            long nioTimestamp = Files.getLastModifiedTime(tempFile.toPath()).toMillis();

            assertEquals(ioTimestamp, nioTimestamp);
        } finally {
            tempFile.delete();
        }

    }
}
