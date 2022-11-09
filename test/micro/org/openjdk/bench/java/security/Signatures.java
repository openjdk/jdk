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
import java.security.spec.ECGenParameterSpec;
import java.security.spec.NamedParameterSpec;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(jvmArgsAppend = {"-Xms1024m", "-Xmx1024m", "-Xmn768m", "-XX:+UseParallelGC"}, value = 3)
public class Signatures {
    private Signature signer;

    @Param({"64", "512", "2048", "16384"})
    private int messageLength;

    @Param({"secp256r1", "secp384r1", "secp521r1", "Ed25519", "Ed448"})
    private String curveName;

    private byte[] message;

    record CurveSpec(String curveName, String signName) {
        // blank
    }

    @Setup
    public void setup() throws Exception {
        message = new byte[messageLength];
        (new Random(System.nanoTime())).nextBytes(message);

        String signName = switch (curveName) {
            case "secp256r1" -> "SHA256withECDSA";
            case "secp384r1" -> "SHA384withECDSA";
            case "secp521r1" -> "SHA512withECDSA";
            case "Ed25519" -> "Ed25519";
            case "Ed448" -> "Ed448";
            default -> throw new RuntimeException();
        };

        KeyPair kp;
        if (curveName.startsWith("secp")) {
            AlgorithmParameters params =
                    AlgorithmParameters.getInstance("EC", "SunEC");
            params.init(new ECGenParameterSpec(curveName));
            ECGenParameterSpec ecParams =
                    params.getParameterSpec(ECGenParameterSpec.class);

            KeyPairGenerator kpg =
                    KeyPairGenerator.getInstance("EC", "SunEC");
            kpg.initialize(ecParams);
            kp = kpg.generateKeyPair();
        } else {
            KeyPairGenerator kpg =
                    KeyPairGenerator.getInstance(curveName, "SunEC");
            NamedParameterSpec spec = new NamedParameterSpec(curveName);
            kpg.initialize(spec);
            kp = kpg.generateKeyPair();
        }

        signer = Signature.getInstance(signName, "SunEC");
        signer.initSign(kp.getPrivate());
    }

    @Benchmark
    public byte[] sign() throws SignatureException {
        signer.update(message);
        return signer.sign();
    }
}

