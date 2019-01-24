/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;
import java.util.Base64;

/**
 * @test
 * @bug 8210583
 * @summary Tests Base64.Encoder.encode/Decoder.decode for the large size
 *          of resulting bytes which can not be allocated
 * @requires os.maxMemory >= 6g
 * @run main/othervm -Xms4g -Xmx6g TestEncodingDecodingLength
 *
 */

public class TestEncodingDecodingLength {

    public static void main(String[] args) {
        int size = Integer.MAX_VALUE - 2;
        byte[] inputBytes = new byte[size];
        byte[] outputBytes = new byte[size];

        // Check encoder with large array length
        Base64.Encoder encoder = Base64.getEncoder();
        checkOOM("encode(byte[])", () -> encoder.encode(inputBytes));
        checkIAE("encode(byte[] byte[])", () -> encoder.encode(inputBytes, outputBytes));
        checkOOM("encodeToString(byte[])", () -> encoder.encodeToString(inputBytes));
        checkOOM("encode(ByteBuffer)", () -> encoder.encode(ByteBuffer.allocate(size)));

        // Check decoder with large array length
        Base64.Decoder decoder = Base64.getDecoder();
        checkOOM("decode(byte[])", () -> decoder.decode(inputBytes));
        checkIAE("decode(byte[], byte[])", () -> decoder.decode(inputBytes, outputBytes));
        checkOOM("decode(ByteBuffer)", () -> decoder.decode(ByteBuffer.allocate(size)));
    }

    private static final void checkOOM(String methodName, Runnable r) {
        try {
            r.run();
            throw new RuntimeException("OutOfMemoryError should have been thrown by: " + methodName);
        } catch (OutOfMemoryError er) {}
    }

    private static final void checkIAE(String methodName, Runnable r) {
        try {
            r.run();
            throw new RuntimeException("IllegalArgumentException should have been thrown by: " + methodName);
        } catch (IllegalArgumentException iae) {}
    }
}

