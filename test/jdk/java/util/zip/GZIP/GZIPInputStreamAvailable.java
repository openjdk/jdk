/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 7036144
 * @summary Test concatenated gz streams when available() returns zero
 * @run junit GZIPInputStreamAvailable
 */

import org.junit.Test;
import org.junit.Assert;

import java.io.*;
import java.util.*;
import java.util.zip.*;

public class GZIPInputStreamAvailable {

    @Test
    public void testZeroAvailable() throws IOException {

        // Create some compressed data
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(buf)) {
            out.write("boo".getBytes("ASCII"));
        }
        byte[] gz = buf.toByteArray();

        // Repeat to build a sequence of concatenated compressed streams
        buf.reset();
        for(int i = 0; i < 100; i++)
            buf.write(gz);
        final byte[] gz32 = buf.toByteArray();

        // (a) Read it back from a stream where available() is accurate
        long count1 = countBytes(new GZIPInputStream(new ByteArrayInputStream(gz32)));

        // (b) Read it back from a stream where available() always returns zero
        long count2 = countBytes(new GZIPInputStream(new ZeroAvailableInputStream(new ByteArrayInputStream(gz32))));

        // They should be the same
        Assert.assertEquals(count2, count1);
    }

    public long countBytes(InputStream in) throws IOException {
        long count = 0;
        while (in.read() != -1)
            count++;
        in.close();
        return count;
    }

    public static class ZeroAvailableInputStream extends FilterInputStream {
        public ZeroAvailableInputStream(InputStream in) {
            super(in);
        }
        @Override
        public int available() throws IOException {
            return 0;
        }
    }
}
