/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.infra.Blackhole;

import javax.crypto.DecapsulateException;
import javax.crypto.KEM;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

public class KEMBench extends CryptoBase {

    public static final int SET_SIZE = 128;

    @Param({"ML-KEM-512", "ML-KEM-768", "ML-KEM-1024" })
    private String algorithm;

    private KeyPair[] keys;
    private byte[][] messages;

    private KEM kem;

    @Setup
    public void setup() throws NoSuchAlgorithmException, InvalidKeyException {
        kem = (prov == null) ? KEM.getInstance(algorithm) : KEM.getInstance(algorithm, prov);
        KeyPairGenerator generator = (prov == null) ? KeyPairGenerator.getInstance(algorithm) : KeyPairGenerator.getInstance(algorithm, prov);
        keys = new KeyPair[SET_SIZE];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = generator.generateKeyPair();
        }
        messages = new byte[SET_SIZE][];
        for (int i = 0; i < messages.length; i++) {
            KEM.Encapsulator enc = kem.newEncapsulator(keys[i].getPublic());
            KEM.Encapsulated encap = enc.encapsulate();
            messages[i] = encap.encapsulation();
        }
    }

    @Benchmark
    @OperationsPerInvocation(SET_SIZE)
    public void encapsulate(Blackhole bh) throws InvalidKeyException {
        for (KeyPair kp : keys) {
            bh.consume(kem.newEncapsulator(kp.getPublic()).encapsulate().encapsulation());
        }
    }

    @Benchmark
    @OperationsPerInvocation(SET_SIZE)
    public void decapsulate(Blackhole bh) throws InvalidKeyException, DecapsulateException {
        for (int i = 0; i < messages.length; i++) {
            bh.consume(kem.newDecapsulator(keys[i].getPrivate()).decapsulate(messages[i]));
        }
    }

}
