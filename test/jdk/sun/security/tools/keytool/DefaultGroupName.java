/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.Asserts;
import sun.security.tools.keytool.Main;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyPairGeneratorSpi;
import java.security.KeyStore;
import java.security.Provider;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.NamedParameterSpec;

/**
 * @test
 * @bug 8391472
 * @summary Default group name should be hardcoded by keytool instead of
 *          determined by providers
 * @library /test/lib
 * @modules java.base/sun.security.tools.keytool
 * @run main/othervm DefaultGroupName
 */

public class DefaultGroupName {

    private static final String COMMON
            = "-keystore ks -storepass changeit -keypass changeit -debug";
    private static final char[] PASS = "changeit".toCharArray();

    private static String last = "";
    private static int count = 0;

    public static void main(String[] args) throws Exception {

        Security.insertProviderAt(new ProviderImpl(), 1);
        Files.deleteIfExists(Path.of("ks"));

        // default group names
        test("EC", "secp384r1");
        test("EdDSA", "Ed25519");
        test("XDH", "X25519");
        test("ML-DSA", "ML-DSA-65");
        test("ML-KEM", "ML-KEM-768");

        // Specified group names
        test("EC", "secp256r1", "-groupname secp256r1");
        test("EdDSA", "Ed448", "-groupname ed448");
        test("EdDSA", "Ed25519", "-groupname ed25519");
        test("XDH", "X448", "-groupname X448");
        test("ML-KEM", "ML-KEM-512", "-groupname ML-KEM-512");
        test("ML-DSA", "ML-DSA-44", "-groupname ML-DSA-44");

        // concrete algorithm names
        test("Ed448", "Ed448");
        test("X448", "X448");
        test("ML-KEM-1024", "ML-KEM-1024");
        test("ML-DSA-87", "ML-DSA-87");
    }

    private static void test(String alg, String expected, String... extras) throws Exception {

        String alias = "a" + count++;
        String cmd = COMMON + " -genkeypair -alias " + alias
                + " -dname CN=" + alias + " -keyalg " + alg;
        if (alg.startsWith("X") || alg.startsWith("ML-KEM")) {
            cmd += " -signer a0"; // the one for EC
        }
        for (String extra : extras) {
            cmd += " " + extra;
        }

        last = "";
        Main.main(cmd.split(" "));
        Asserts.assertEquals(alg, last); // ensure our provider is used

        KeyStore ks = KeyStore.getInstance(new File("ks"), PASS);
        PublicKey key = ks.getCertificate(alias).getPublicKey();
        AlgorithmParameterSpec params = key.getParams();
        if (params instanceof NamedParameterSpec nps) {
            Asserts.assertEquals(expected, nps.getName());
        } else if (params instanceof ECParameterSpec){
            Asserts.assertTrue(params.toString().contains(expected), params.toString());
        } else {
            throw new RuntimeException("Unknown type: " + params.getClass());
        }
    }

    public static class ProviderImpl extends Provider {
        public ProviderImpl() {
            super("P8391472", "1", "cool");
            put("KeyPairGenerator.ML-DSA", KPG.MLDSA.class.getName());
            put("KeyPairGenerator.ML-KEM", KPG.MLKEM.class.getName());
            put("KeyPairGenerator.EC", KPG.EC.class.getName());
            put("KeyPairGenerator.EdDSA", KPG.EDDSA.class.getName());
            put("KeyPairGenerator.XDH", KPG.XDH.class.getName());
            put("KeyPairGenerator.Ed448", KPG.ED448.class.getName());
            put("KeyPairGenerator.X448", KPG.X448.class.getName());
            put("KeyPairGenerator.ML-KEM-1024", KPG.MLKEM1024.class.getName());
            put("KeyPairGenerator.ML-DSA-87", KPG.MLDSA87.class.getName());
        }
    }

    public static class KPG extends KeyPairGeneratorSpi {

        // None of these KeyPairGeneratorSpis uses the same default
        // as claimed in keytool
        public static class MLDSA extends KPG {
            public MLDSA() {
                super("ML-DSA", "SUN", NamedParameterSpec.ML_DSA_44, true);
            }
        }

        public static class MLKEM extends KPG {
            public MLKEM() {
                super("ML-KEM", "SunJCE", NamedParameterSpec.ML_KEM_512, true);
            }
        }

        public static class EC extends KPG {
            public EC() {
                super("EC", "SunEC", new ECGenParameterSpec("secp256r1"), true);
            }
        }

        public static class EDDSA extends KPG {
            public EDDSA() {
                super("EdDSA", "SunEC", NamedParameterSpec.ED448, true);
            }
        }

        public static class XDH extends KPG {
            public XDH() {
                super("XDH", "SunEC", NamedParameterSpec.X448, true);
            }
        }

        public static class X448 extends KPG {
            public X448() {
                super("X448", "SunEC", NamedParameterSpec.X448, false);
            }
        }

        public static class ED448 extends KPG {
            public ED448() {
                super("Ed448", "SunEC", NamedParameterSpec.ED448, false);
            }
        }

        public static class MLKEM1024 extends KPG {
            public MLKEM1024() {
                super("ML-KEM-1024", "SunJCE", NamedParameterSpec.ML_KEM_1024, false);
            }
        }

        public static class MLDSA87 extends KPG {
            public MLDSA87() {
                super("ML-DSA-87", "SUN", NamedParameterSpec.ML_DSA_87, false);
            }
        }

        private AlgorithmParameterSpec spec;
        private final String algorithm;
        private final String realProvider;
        private final boolean isDynamic;

        public KPG(String algorithm, String realProvider,
                AlgorithmParameterSpec defaultSpec, boolean isDynamic) {
            try {
                this.algorithm = algorithm;
                this.realProvider = realProvider;
                this.spec = defaultSpec;
                this.isDynamic = isDynamic;
            } catch (Exception e) {
                throw new ProviderException(e);
            }
        }

        @Override
        public void initialize(AlgorithmParameterSpec params,
                SecureRandom random) throws InvalidAlgorithmParameterException {
            if (isDynamic) {
                spec = params;
            } else {
                throw new InvalidAlgorithmParameterException();
            }
        }

        @Override
        public void initialize(int keysize, SecureRandom random) {
            throw new InvalidParameterException();
        }

        @Override
        public KeyPair generateKeyPair() {
            try {
                KeyPairGenerator gen = KeyPairGenerator.getInstance(algorithm, realProvider);
                gen.initialize(spec);
                KeyPair kp = gen.generateKeyPair();
                last = algorithm;
                return kp;
            } catch (Exception e) {
                last = e.getMessage();
                throw new ProviderException(e);
            }
        }
    }
}
