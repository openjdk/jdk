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

/*
 * @test
 * @bug 8298420
 * @library /test/lib
 * @summary Testing PEM API is thread safe
 * @enablePreview
 * @modules java.base/sun.security.util
 */

import java.security.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jdk.test.lib.security.SecurityUtils;

public class PEMMultiThreadTest {
    static final int THREAD_COUNT = 5;
    static final int KEYS_COUNT = 50;

    public static void main(String[] args) throws Exception {
        PEMEncoder encoder = PEMEncoder.of();
        try (ExecutorService ex = Executors.newFixedThreadPool(THREAD_COUNT)) {
            Map<Integer, PublicKey> keys = new HashMap<>();
            Map<Integer, String> encoded = Collections.synchronizedMap(new HashMap<>());
            Map<Integer, String> decoded = Collections.synchronizedMap(new HashMap<>());
            final CountDownLatch encodingComplete = new CountDownLatch(KEYS_COUNT);
            final CountDownLatch decodingComplete = new CountDownLatch(KEYS_COUNT);

            // Generate keys and encode them in parallel
            for (int i = 0; i < KEYS_COUNT; i++) {
                final int finalI = i;
                KeyPair kp = getKeyPair();
                keys.put(finalI, kp.getPublic());

                ex.submit(() -> {
                    encoded.put(finalI, encoder.encodeToString(kp.getPublic()));
                    encodingComplete.countDown();
                });
            }
            encodingComplete.await();

            // Decode keys in parallel
            PEMDecoder decoder = PEMDecoder.of();
            for (Map.Entry<Integer, String> entry : encoded.entrySet()) {
                ex.submit(() -> {
                    decoded.put(entry.getKey(), decoder.decode(entry.getValue(), PublicKey.class)
                            .toString());
                    decodingComplete.countDown();
                });
            }
            decodingComplete.await();

            // verify all keys were properly encoded and decoded comparing with the original key map
            for (Map.Entry<Integer, PublicKey> kp : keys.entrySet()) {
                if (!decoded.get(kp.getKey()).equals(kp.getValue().toString())) {
                    throw new RuntimeException("a key was not properly encoded and decoded: " + decoded);
                }
            }
        }

        System.out.println("PASS: testThreadSafety");
    }

    private static KeyPair getKeyPair() throws NoSuchAlgorithmException {
        String alg = "EC";
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(alg);
        kpg.initialize(SecurityUtils.getTestKeySize(alg));
        return kpg.generateKeyPair();
    }
}
