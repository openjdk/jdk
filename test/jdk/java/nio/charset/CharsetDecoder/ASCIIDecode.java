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

/* @test
 * @bug 8283325
 * @summary Ensure that decoding to ASCII from a stream with a non-ASCII
 *          character correctly decodes up until the byte in error.
 */

import java.nio.*;
import java.nio.charset.*;
import java.util.Arrays;

public class ASCIIDecode {

    public static void main(String[] args) throws Exception {
        final Charset ascii = Charset.forName("US-ASCII");
        final CharsetDecoder decoder = ascii.newDecoder();

        byte[] ba = new byte[] { 0x60, 0x60, 0x60, (byte)0xFF };

        // Repeat enough times to test that interpreter and JIT:ed versions
        // behave the same (without the patch for 8283325 this fails within
        // 50 000 iterations on the system used for verification)
        for (int i = 0; i < 100_000; i++) {
            ByteBuffer bb = ByteBuffer.wrap(ba);
            char[] ca = new char[4];
            CharBuffer cb = CharBuffer.wrap(ca);
            CoderResult buf = decoder.decode(bb, cb, true);
            if (ca[0] != 0x60 || ca[1] != 0x60 || ca[2] != 0x60) {
                throw new RuntimeException("Unexpected output on iteration " + i);
            }
        }
    }
}
