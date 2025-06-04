/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Palantir Technologies, Inc. and/or its affiliates. All rights reserved.
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

/**
 * @test TestChunkInputStreamBulkRead
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.api.consumer.TestChunkInputStreamBulkRead
 */
package jdk.jfr.api.consumer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import jdk.jfr.Recording;
import jdk.test.lib.Asserts;

public class TestChunkInputStreamBulkRead {

    public static void main(String[] args) throws Exception {
        try (Recording r = new Recording()) {
            r.start();
            try (Recording s = new Recording()) {
                s.start();
                s.stop();
            }
            r.stop();
            try (InputStream stream = r.getStream(null, null);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                long read = stream.transferTo(output);
                System.out.printf("Read %d bytes from JFR stream%n", read);
                Asserts.assertEquals(r.getSize(), read);

                byte[] actual = output.toByteArray();
                Asserts.assertEqualsByteArray(r.getStream(null, null).readAllBytes(), actual);

                Path dumpFile = Paths.get("recording.jfr").toAbsolutePath().normalize();
                r.dump(dumpFile);
                System.out.printf("Dumped recording to %s (%d bytes)%n", dumpFile, Files.size(dumpFile));
                Asserts.assertEqualsByteArray(Files.readAllBytes(dumpFile), actual);
            }
        }
    }
}
