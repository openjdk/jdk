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
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.infra.Blackhole;

import javax.crypto.DecapsulateException;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import javax.crypto.KEM;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;

public abstract class KEMBench extends CryptoBase {

    public static final int SET_SIZE = 128;

    @Param({})
    private String algorithm;

    @Param({""})        // Used when the KeyPairGenerator Alg != KEM Alg
    private String kpgSpec;

    private KeyPair[] keys;
    private byte[][] messages;

    private KEM kem;

    @Setup
    public void setup() throws NoSuchAlgorithmException, InvalidKeyException,
            InvalidAlgorithmParameterException {
        String kpgAlg;
        String kpgParams;
        kem = (prov == null) ? KEM.getInstance(algorithm) :
                KEM.getInstance(algorithm, prov);

        // By default use the same provider for KEM and KPG
        Provider kpgProv = prov;
        if (kpgSpec.isEmpty()) {
            kpgAlg = algorithm;
            kpgParams = "";
        } else {
            // The key pair generation spec is broken down from a colon-
            // delimited string spec into 3 fields:
            // [0] - the provider name
            // [1] - the algorithm name
            // [2] - the parameters (i.e. the name of the curve)
            String[] kpgTok = kpgSpec.split(":");
            kpgProv = Security.getProvider(kpgTok[0]);
            kpgAlg = kpgTok[1];
            kpgParams = kpgTok[2];
        }
        KeyPairGenerator generator = (kpgProv == null) ?
                    KeyPairGenerator.getInstance(kpgAlg) :
                    KeyPairGenerator.getInstance(kpgAlg, kpgProv);
        if (kpgParams != null && !kpgParams.isEmpty()) {
            generator.initialize(new ECGenParameterSpec(kpgParams));
        }
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

    private static Provider getInternalJce() {
        try {
            Class<?> dhClazz = Class.forName("sun.security.ssl.HybridProvider");
            return (Provider) dhClazz.getField("PROVIDER").get(null);
        } catch (ReflectiveOperationException exc) {
            throw new RuntimeException(exc);
        }
    }

    @Benchmark
    @OperationsPerInvocation(SET_SIZE)
    public void encapsulate(Blackhole bh) throws InvalidKeyException {
        for (KeyPair kp : keys) {
            bh.consume(kem.newEncapsulator(kp.getPublic()).encapsulate().
                    encapsulation());
        }
    }

    @Benchmark
    @OperationsPerInvocation(SET_SIZE)
    public void decapsulate(Blackhole bh) throws InvalidKeyException,
            DecapsulateException {
        for (int i = 0; i < messages.length; i++) {
            bh.consume(kem.newDecapsulator(keys[i].getPrivate()).
                    decapsulate(messages[i]));
        }
    }

    public static class MLKEM extends KEMBench {
        @Param({"ML-KEM-512", "ML-KEM-768", "ML-KEM-1024" })
        private String algorithm;

        @Param({""})            // ML-KEM uses the same alg for KPG and KEM
        private String kpgSpec;
    }

    @Fork(value = 5, jvmArgs = {"-XX:+AlwaysPreTouch", "--add-opens",
            "java.base/sun.security.ssl=ALL-UNNAMED"})
    public static class JSSE_DHasKEM extends KEMBench {
        @Setup
        public void init() {
            try {
                prov = getInternalJce();
                super.setup();
            } catch (GeneralSecurityException gse) {
                throw new RuntimeException(gse);
            }
        }

        @Param({"DH"})
        private String algorithm;

        @Param({"SunEC:XDH:x25519", "SunEC:EC:secp256r1", "SunEC:EC:secp384r1"})
        private String kpgSpec;
    }

    @Fork(value = 5, jvmArgs = {"-XX:+AlwaysPreTouch", "--add-opens",
            "java.base/sun.security.ssl=ALL-UNNAMED"})
    public static class JSSE_Hybrid extends KEMBench {
        @Setup
        public void init() {
            try {
                prov = getInternalJce();
                super.setup();
            } catch (GeneralSecurityException gse) {
                throw new RuntimeException(gse);
            }
        }

        @Param({"X25519MLKEM768", "SecP256r1MLKEM768", "SecP384r1MLKEM1024"})
        private String algorithm;

        @Param({""})            // ML-KEM uses the same alg for KPG and KEM
        private String kpgSpec;
    }
}
