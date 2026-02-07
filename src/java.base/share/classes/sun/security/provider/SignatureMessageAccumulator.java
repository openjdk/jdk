/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package sun.security.provider;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/// Accumulate messages in a [NamedSignature]. May be pure or pre-hashed.
public class SignatureMessageAccumulator {

    private final ByteArrayOutputStream ba; // for "pure"
    private final MessageDigest md; // for "preHash"

    /// Creates a new accumulator without a hash.
    public SignatureMessageAccumulator() {
        ba = new ByteArrayOutputStream();
        md = null;
    }

    /// Creates a new accumulator with a hash.
    ///
    /// @param s pre-hash algorithm
    /// @throws NoSuchAlgorithmException if `s` is not supported
    public SignatureMessageAccumulator(String s) throws NoSuchAlgorithmException {
        ba = null;
        md = MessageDigest.getInstance(Objects.requireNonNull(s));
    }

    /// Reset the accumulator
    public void reset() {
        if (ba == null) md.reset();
        else ba.reset();
    }

    /// Write a byte into the accumulator.
    ///
    /// @param b the byte
    public void write(byte b) {
        if (ba == null) md.update(b);
        else ba.write(b);
    }

    /// Write a byte array into the accumulator.
    ///
    /// @param b the data.
    /// @param off the start offset in the data.
    /// @param len the number of bytes to write.
    public void write(byte[] b, int off, int len) {
        if (ba == null) md.update(b, off, len);
        else ba.write(b, off, len);
    }

    /// {@return the data accumulated and reset the accumulator}
    public byte[] toByteArray() {
        if (ba == null) {
            return md.digest();
        } else {
            var result = ba.toByteArray();
            ba.reset();
            return result;
        }
    }
}
