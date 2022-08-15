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
 * @summary Test case where Inflater.needsInput() is true but the native inflater
 *          still has unwritten output in its internal buffer.
 */

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class UnexpectedEndOfZlibStream {
    public static void main(String[] args) throws Exception {
        byte[] bytes = {
            85, -115, 49, 14, -125, 48, 12, 0, -9, -68, 2, 49, -63, 98, -87, 93, 89, 90, -47, -103, 14,
            -99, 58, -102, 96, 5, -89, 36, -74, -120, -123, -44, -33, -61, 8, -21, -23, 78, -9, 120, -113,
            -111, -68, -15, 70, -3, -128, -119, -102, -6, -11, 29, 62, 79, -27, -70, 117, -118, -2, -121,
            -127, 42, 47, 9, -126, 72, 88, 8, 80, -75, -64, -12, -49, -104, -40, 8, -74, 27, -108, 25, 87,
            -102, 14, -50, -99, 115, -100, 84, 86, 59, -5, -15, 46, 99, -12, -128, 57, -117, -95, -79,
            -28, 2, -41, -33, 81, -19,
        };
        String deflated = "@ObjectiveCName(\"DYNSApi\")\npackage com.google.apps.dynamite.v1.shared.api;\n\n"+
            "import com.google.j2objc.annotations.ObjectiveCName;\n\n";

        // using readAllBytes succeeds, the underlying gzip data seems to be well-formed
        byte[] inflated =
            new InflaterInputStream(new ByteArrayInputStream(bytes), new Inflater(/*nowrap=*/ true)).readAllBytes();

        if (!deflated.equals(new String(inflated, "US-ASCII"))) {
            throw new Exception("Inflated output differs from original");
        }

        // a 128 byte read followed by a 512 byte read fails after JDK-8281962
        InflaterInputStream is =
            new InflaterInputStream(new ByteArrayInputStream(bytes), new Inflater(/*nowrap=*/ true));
        byte[] buf = new byte[512];
        int n = is.read(buf, 0, 128);
        int pos = n;
        while (n > 0) {
            if ((n = is.read(buf, pos, 1)) > 0) { // Unexpected end of ZLIB input stream
                pos+= n;
            }
        }
        if (!deflated.equals(new String(buf, 0, pos, "US-ASCII"))) {
            throw new Exception("Inflated output differs from original");
        }
    }
}
