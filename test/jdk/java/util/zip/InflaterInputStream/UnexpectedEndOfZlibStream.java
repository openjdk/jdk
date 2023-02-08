/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8292327
 * @summary Test the case where Inflater.needsInput() is true but the native
 *          inflater still has unwritten output in its internal buffer.
 */

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.Deflater;
import java.util.zip.DeflaterInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class UnexpectedEndOfZlibStream {
    public static void main(String[] args) throws Exception {
        String original = "Readsuncompresseddataintoanarrayofbytes0123456789Ifcodelenis" +
            "notzerothemethodwillblockReadsuncompresseddataintoanarrayofbytes123456789";
        byte[] deflated =
            new DeflaterInputStream(
                new ByteArrayInputStream(original.getBytes("US-ASCII")),
                new Deflater(Deflater.DEFAULT_COMPRESSION, /*nowrap=*/ true)
            ).readAllBytes();

        // using readAllBytes succeeds, the underlying gzip data seems to be well-formed
        byte[] inflated =
            new InflaterInputStream(
                new ByteArrayInputStream(deflated),
                new Inflater(/*nowrap=*/ true)
            ).readAllBytes();

        if (!original.equals(new String(inflated, "US-ASCII"))) {
            throw new Exception("Inflated output differs from original");
        }

        // a 128 byte read followed by a 512 byte read fails after JDK-8281962
        InflaterInputStream is =
            new InflaterInputStream(
                new ByteArrayInputStream(deflated),
                new Inflater(/*nowrap=*/ true));
        byte[] buf = new byte[512];
        int n = is.read(buf, 0, 128);
        int pos = n;
        while (n > 0) {
            if ((n = is.read(buf, pos, 1)) > 0) { // Unexpected end of ZLIB input stream
                pos+= n;
            }
        }
        if (!original.equals(new String(buf, 0, pos, "US-ASCII"))) {
            throw new Exception("Inflated output differs from original");
        }
    }
}
