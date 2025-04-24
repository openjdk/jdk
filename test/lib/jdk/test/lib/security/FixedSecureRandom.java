/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.test.lib.security;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;

/// A custom implementation of `SecureRandom` that outputs a
/// predefined sequence of bytes.
///
/// The `FixedSecureRandom` class is designed for testing and
/// controlled environments where predictable output is required.
/// Upon creation, the class is initialized with a fixed byte array.
/// Each call to `nextBytes()` will return these bytes in sequence,
/// ensuring that the output matches the provided input exactly.
/// An `IllegalStateException` will be thrown when the predefined
/// bytes are exhausted.
public class FixedSecureRandom extends SecureRandom {

    private static final long serialVersionUID = -8753752741562231543L;

    private byte[] buffer;
    private int offset;

    // Multiple segments of ordered predefined bytes can be
    // provided for convenience. For example, ML-KEM.KeyGen
    // requires 2 blocks of 32-byte random data.
    public FixedSecureRandom(byte[]... data) {
        var os = new ByteArrayOutputStream();
        for (byte[] b : data) {
            os.writeBytes(b);
        }
        buffer = os.toByteArray();
        offset = 0;
    }

    @Override
    public void nextBytes(byte[] bytes) {
        if (bytes.length > buffer.length - offset) {
            throw new IllegalStateException("Not enough bytes");
        }
        System.arraycopy(buffer, offset, bytes, 0, bytes.length);
        offset += bytes.length;
    }

    /// {@return whether there are remaining used bytes}
    ///
    /// This method is useful to detect whether an algorithm
    /// implementation has indeed consumed the required number
    /// of bytes correctly.
    public boolean hasRemaining() {
        return offset != buffer.length;
    }
}
