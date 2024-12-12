/*
 * Copyright (C) 2022 THL A29 Limited, a Tencent company. All rights reserved.
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
package org.openjdk.bench.java.security;

import org.openjdk.jmh.annotations.*;

import java.security.*;
import java.security.spec.*;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(jvmArgs = {"-Xms1024m", "-Xmx1024m", "-Xmn768m", "-XX:+UseParallelGC"}, value = 3)
public class Signatures {
    private static Signature signer;

    @Param({"64", "512", "2048", "16384"})
    private static int messageLength;

    @Param({"secp256r1", "secp384r1", "secp521r1"})
    private String algorithm;

    private static byte[] message;

    @Setup
    public void setup() throws Exception {
        message = new byte[messageLength];
        (new Random(System.nanoTime())).nextBytes(message);

        String signName = switch (algorithm) {
            case "secp256r1" -> "SHA256withECDSA";
            case "secp384r1" -> "SHA384withECDSA";
            case "secp521r1" -> "SHA512withECDSA";
            default -> throw new RuntimeException();
        };

        AlgorithmParameters params =
                AlgorithmParameters.getInstance("EC", "SunEC");
        params.init(new ECGenParameterSpec(algorithm));
        ECGenParameterSpec ecParams =
                params.getParameterSpec(ECGenParameterSpec.class);

        KeyPairGenerator kpg =
                KeyPairGenerator.getInstance("EC", "SunEC");
        kpg.initialize(ecParams);
        KeyPair kp = kpg.generateKeyPair();

        signer = Signature.getInstance(signName, "SunEC");
        signer.initSign(kp.getPrivate());
    }

    @Benchmark
    public byte[] sign() throws SignatureException {
        signer.update(message);
        return signer.sign();
    }

    public static class EdDSA extends Signatures {
        @Param({"Ed25519", "Ed448"})
        private String algorithm;

        @Setup
        public void setup() throws Exception {
            message = new byte[messageLength];
            (new Random(System.nanoTime())).nextBytes(message);

            KeyPairGenerator kpg =
                    KeyPairGenerator.getInstance(algorithm, "SunEC");
            NamedParameterSpec spec = new NamedParameterSpec(algorithm);
            kpg.initialize(spec);
            KeyPair kp = kpg.generateKeyPair();

            signer = Signature.getInstance(algorithm, "SunEC");
            signer.initSign(kp.getPrivate());
        }
    }

    public static class DSA extends Signatures {
        @Param({"SHA256withDSA", "SHA384withDSA", "SHA512withDSA"})
        private String algorithm;

        @Setup
        public void setup() throws Exception {
            message = new byte[messageLength];
            (new Random(System.nanoTime())).nextBytes(message);

            int keyLength = switch (algorithm) {
                case "SHA256withDSA" -> 2048;
                case "SHA384withDSA" -> 3072;
                case "SHA512withDSA" -> 3072;
                default -> throw new RuntimeException();
            };

            KeyPairGenerator kpg = KeyPairGenerator.getInstance("DSA");
            kpg.initialize(keyLength);
            KeyPair kp = kpg.generateKeyPair();

            signer = Signature.getInstance(algorithm);
            signer.initSign(kp.getPrivate());
        }
    }

    public static class RSA extends Signatures {
        @Param({"SHA256withRSA", "SHA384withRSA", "SHA512withRSA"})
        private String algorithm;

        @Setup
        public void setup() throws Exception {
            message = new byte[messageLength];
            (new Random(System.nanoTime())).nextBytes(message);

            int keyLength = switch (algorithm) {
                case "SHA256withRSA" -> 2048;
                case "SHA384withRSA" -> 3072;
                case "SHA512withRSA" -> 4096;
                default -> throw new RuntimeException();
            };

            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(keyLength);
            KeyPair kp = kpg.generateKeyPair();

            signer = Signature.getInstance(algorithm);
            signer.initSign(kp.getPrivate());
        }
    }

    public static class RSASSAPSS extends Signatures {
        @Param({"SHA256", "SHA384", "SHA512"})
        private String algorithm;

        @Setup
        public void setup() throws Exception {
            message = new byte[messageLength];
            (new Random(System.nanoTime())).nextBytes(message);

            int keyLength = switch (algorithm) {
               case "SHA256" -> 2048;
               case "SHA384" -> 3072;
               case "SHA512" -> 4096;
               default -> throw new RuntimeException();
            };

            PSSParameterSpec spec = switch (algorithm) {
               case "SHA256" ->
                       new PSSParameterSpec(
                               "SHA-256", "MGF1",
                               MGF1ParameterSpec.SHA256,
                               32, PSSParameterSpec.TRAILER_FIELD_BC);
               case "SHA384" ->
                       new PSSParameterSpec(
                               "SHA-384", "MGF1",
                               MGF1ParameterSpec.SHA384,
                               48, PSSParameterSpec.TRAILER_FIELD_BC);
               case "SHA512" ->
                        new PSSParameterSpec(
                               "SHA-512", "MGF1",
                               MGF1ParameterSpec.SHA512,
                               64, PSSParameterSpec.TRAILER_FIELD_BC);
               default -> throw new RuntimeException();
            };

            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSASSA-PSS");
            kpg.initialize(keyLength);
            KeyPair kp = kpg.generateKeyPair();

            signer = Signature.getInstance("RSASSA-PSS");
            signer.setParameter(spec);
            signer.initSign(kp.getPrivate());
        }
    }
}

