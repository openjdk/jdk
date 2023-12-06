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
package org.openjdk.bench.javax.crypto.full;

import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;

import java.security.spec.AlgorithmParameterSpec;
import javax.crypto.spec.IvParameterSpec;

/**
 * This performance tests runs ChaCha20-Poly1305 encryption and decryption
 * using heap and direct ByteBuffers for input and output buffers with single
 * and multi-part operations.
 */

public class CC20P1305ByteBuffer extends ByteBufferBase {

    public static final int IV_MODULO = 12;

    public AlgorithmParameterSpec getNewSpec() {
        iv_index = (iv_index + 1) % IV_MODULO;
        return new IvParameterSpec(iv, iv_index, IV_MODULO);
    }

    @Setup
    public void setup() throws Exception {
        init("ChaCha20-Poly1305/None/NoPadding", keyLength);
    }
}
