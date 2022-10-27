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

import java.security.AlgorithmParameters;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.NamedParameterSpec;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(jvmArgsAppend = {"-Xms1024m", "-Xmx1024m", "-Xmn768m", "-XX:+UseParallelGC"}, value = 3)
public class KeyPairGenerators {
    @Param({"secp256r1", "secp384r1", "secp521r1", "Ed25519", "Ed448"})
    private String curveName;

    private KeyPairGenerator kpg;

    @Setup
    public void setup() throws Exception {
        if (curveName.startsWith("secp")) {
            AlgorithmParameters params =
                    AlgorithmParameters.getInstance("EC", "SunEC");
            params.init(new ECGenParameterSpec(curveName));
            ECGenParameterSpec ecParams =
                    params.getParameterSpec(ECGenParameterSpec.class);

            kpg = KeyPairGenerator.getInstance("EC", "SunEC");
            kpg.initialize(ecParams);
        } else {
            kpg = KeyPairGenerator.getInstance(curveName, "SunEC");
            NamedParameterSpec spec = new NamedParameterSpec(curveName);
            kpg.initialize(spec);
        }
    }

    @Benchmark
    public KeyPair keyPairGen() {
        return kpg.generateKeyPair();
    }
}

