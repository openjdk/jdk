/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.infra.Blackhole;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidParameterSpecException;

public class AESExtraBench extends AESBench {

    public static final int SET_SIZE = 8;

    @Param({"AES/CBC/NoPadding"})
    private String algorithm;

    @Param({"128", "192", "256"})
    private int keyLength;

    @Param({"" + 16 * 1024})
    private int dataSize;

    private byte[] outBuffer;
    int index = 0;

    @Setup
    public void setup() throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException, InvalidParameterSpecException {
        super.setup();
        outBuffer = new byte[dataSize + 128]; // extra space for tag, etc
    }

    @Benchmark
    public int encrypt2() throws GeneralSecurityException {
        byte[] d = data[index];
        index = (index +1) % SET_SIZE;
        return encryptCipher.doFinal(d, 0, d.length, outBuffer);
    }

    @Benchmark
    public int decrypt2() throws GeneralSecurityException {
        byte[] e = encryptedData[index];
        index = (index +1) % SET_SIZE;
        return decryptCipher.doFinal(e, 0, e.length, outBuffer);
    }

}
