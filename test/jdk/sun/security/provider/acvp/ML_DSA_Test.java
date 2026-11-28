/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.json.JSONValue;
import jdk.test.lib.security.FixedSecureRandom;
import sun.security.provider.ML_DSA_Impls;
import sun.security.util.DerOutputStream;
import sun.security.util.InternalSignatureParameterSpec;
import sun.security.util.KnownOIDs;
import sun.security.util.ObjectIdentifier;

import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.MLDSAParameterSpec;
import java.security.spec.NamedParameterSpec;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static jdk.test.lib.Utils.toByteArray;

// JSON spec at https://pages.nist.gov/ACVP/draft-celi-acvp-ml-dsa.html
public class ML_DSA_Test {

    public static void run(JSONValue kat, Provider provider) throws Exception {
        String mode = kat.get("mode").asString();
        switch (mode) {
            case "keyGen" -> keyGenTest(kat, provider);
            case "sigGen" -> sigGenTest(kat, provider);
            case "sigVer" -> sigVerTest(kat, provider);
            default -> throw new UnsupportedOperationException("Unknown mode: " + mode);
        }
    }

    static NamedParameterSpec genParams(String pname) {
        return switch (pname) {
            case "ML-DSA-44" -> NamedParameterSpec.ML_DSA_44;
            case "ML-DSA-65" -> NamedParameterSpec.ML_DSA_65;
            case "ML-DSA-87" -> NamedParameterSpec.ML_DSA_87;
            default -> throw new RuntimeException("Unknown params: " + pname);
        };
    }

    static void keyGenTest(JSONValue kat, Provider p) throws Exception {
        KeyPairGenerator g = p == null
                ? KeyPairGenerator.getInstance("ML-DSA")
                : KeyPairGenerator.getInstance("ML-DSA", p);
        KeyFactory f = p == null
                ? KeyFactory.getInstance("ML-DSA")
                : KeyFactory.getInstance("ML-DSA", p);
        for (JSONValue t : kat.get("testGroups").elements()) {
            String pname = t.get("parameterSet").asString();
            NamedParameterSpec np = genParams(pname);
            System.out.println(">> " + pname);
            for (JSONValue c : t.get("tests").elements()) {
                System.out.print(c.get("tcId").asInt() + " ");
                byte[] seed = toByteArray(c.get("seed").asString());
                g.initialize(np, new FixedSecureRandom(seed));
                KeyPair kp = g.generateKeyPair();
                byte[] pk = f.getKeySpec(kp.getPublic(), EncodedKeySpec.class).getEncoded();
                Asserts.assertEqualsByteArray(toByteArray(c.get("pk").asString()), pk);
                Asserts.assertEqualsByteArray(toByteArray(c.get("sk").asString()),
                        ML_DSA_Impls.seedToExpanded(pname, seed));
            }
            System.out.println();
        }
    }

    static String h2h(String in) {
        return switch (in) {
            case "SHA2-512/224" -> "SHA-512/224";
            case "SHA2-512/256" -> "SHA-512/256";
            case "SHA2-224" -> "SHA-224";
            case "SHA2-256" -> "SHA-256";
            case "SHA2-384" -> "SHA-384";
            case "SHA2-512" -> "SHA-512";
            case "SHAKE-128" -> "SHAKE128-256";
            case "SHAKE-256" -> "SHAKE256-512";
            default -> in;
        };
    }

    static void sigGenTest(JSONValue kat, Provider p) throws Exception {
        Signature s = p == null
                ? Signature.getInstance("ML-DSA")
                : Signature.getInstance("ML-DSA", p);
        for (JSONValue t : kat.get("testGroups").elements()) {
            String pname = t.get("parameterSet").asString();
            System.out.println(">> " + pname + " sign");
            boolean deterministic = t.get("deterministic").asBoolean();
            boolean internal = t.get("signatureInterface").asString().equals("internal");
            boolean externalMu = t.get("externalMu").asBoolean();
            for (JSONValue c : t.get("tests").elements()) {
                System.out.print(c.get("tcId").asInt() + " ");
                byte[] ctxt =  c.getOrAbsent("context")
                        .map(v -> toByteArray(v.asString()))
                        .orElse(new byte[0]);
                String hashAlg = c.get("hashAlg").asString();
                String preHash = hashAlg.equals("none") ? null : h2h(hashAlg);
                byte[] rawSk = toByteArray(c.get("sk").asString());
                PrivateKey sk = new PrivateKey() {
                    public String getAlgorithm() { return pname; }
                    public String getFormat() { return "RAW"; }
                    public byte[] getEncoded() { return oct(rawSk); }
                };
                byte[] input = toByteArray(externalMu
                        ? c.get("mu").asString()
                        : c.get("message").asString());
                byte[] sigExpected = toByteArray(c.get("signature").asString());

                // Use InternalSignatureParameterSpec
                Set<String> features = new HashSet<>();
                if (deterministic) {
                    features.add("deterministic");
                }
                if (internal) {
                    features.add("internal");
                }
                if (externalMu) {
                    features.add("externalMu");
                }

                s.setParameter(new InternalSignatureParameterSpec(
                        preHash, ctxt, features.toArray(new String[0])));
                s.initSign(sk, deterministic
                        ? null
                        : new FixedSecureRandom(toByteArray(c.get("rnd").asString())));
                s.update(input);
                byte[] sigUsingInternal = s.sign();

                // Use MLDSAParameterSpec
                SecureRandom sr = deterministic
                        ? new FixedSecureRandom(new byte[32])
                        : new FixedSecureRandom(toByteArray(c.get("rnd").asString()));
                if (!internal && preHash == null) {
                    s.setParameter(MLDSAParameterSpec.ofContext(ctxt));
                    s.initSign(sk, sr);
                    s.update(input);
                } else {
                    byte[] mu = externalMu
                            ? input
                            : (internal
                               ? mu(input, sk2tr(rawSk))
                               : mu(mprime(preHash, ctxt, input), sk2tr(rawSk)));
                    s.setParameter(MLDSAParameterSpec.ofExternalMu());
                    s.initSign(sk, sr);
                    s.update(mu);
                }
                byte[] sigUsingPublic = s.sign();

                Asserts.assertEqualsByteArray(sigExpected, sigUsingInternal);
                Asserts.assertEqualsByteArray(sigExpected, sigUsingPublic);
            }
            System.out.println();
        }
    }

    static byte[] oct(byte[] in) {
        return new DerOutputStream().putOctetString(in).toByteArray();
    }

    static void sigVerTest(JSONValue kat, Provider p) throws Exception {
        Signature s = p == null
                ? Signature.getInstance("ML-DSA")
                : Signature.getInstance("ML-DSA", p);
        for (JSONValue t : kat.get("testGroups").elements()) {
            String pname = t.get("parameterSet").asString();
            System.out.println(">> " + pname + " verify");
            boolean internal = t.get("signatureInterface").asString().equals("internal");
            boolean externalMu = t.get("externalMu").asBoolean();
            for (JSONValue c : t.get("tests").elements()) {
                System.out.print(c.get("tcId").asInt() + " ");
                byte[] ctxt =  c.getOrAbsent("context")
                        .map(v -> toByteArray(v.asString()))
                        .orElse(new byte[0]);
                String hashAlg = c.get("hashAlg").asString();
                String preHash = hashAlg.equals("none") ? null : h2h(hashAlg);
                byte[] rawpk = toByteArray(c.get("pk").asString());
                PublicKey pk = new PublicKey() {
                    public String getAlgorithm() { return pname; }
                    public String getFormat() { return "RAW"; }
                    public byte[] getEncoded() { return rawpk; }
                };
                byte[] input = toByteArray(externalMu
                        ? c.get("mu").asString()
                        : c.get("message").asString());
                byte[] signature = toByteArray(c.get("signature").asString());
                boolean expected = c.get("testPassed").asBoolean();

                // Use InternalSignatureParameterSpec
                boolean actualUsingInternal = true;
                Set<String> features = new HashSet<>();
                if (t.get("signatureInterface").asString().equals("internal")) {
                    features.add("internal");
                }
                if (t.get("externalMu").asBoolean()) {
                    features.add("externalMu");
                }

                s.setParameter(new InternalSignatureParameterSpec(
                        preHash, ctxt, features.toArray(new String[0])));
                try {
                    s.initVerify(pk);
                    s.update(input);
                    actualUsingInternal = s.verify(signature);
                } catch (InvalidKeyException | SignatureException e) {
                    actualUsingInternal = false;
                }

                // Use MLDSAParameterSpec
                boolean actualUsingPublic = true;
                try {
                    if (!internal && preHash == null) {
                        s.setParameter(MLDSAParameterSpec.ofContext(ctxt));
                        s.initVerify(pk);
                        s.update(input);
                    } else {
                        byte[] mu = externalMu
                                ? input
                                : internal
                                  ? mu(input, pk2tr(rawpk))
                                  : mu(mprime(preHash, ctxt, input), pk2tr(rawpk));
                        s.setParameter(MLDSAParameterSpec.ofExternalMu());
                        s.initVerify(pk);
                        s.update(mu);
                    }
                    actualUsingPublic = s.verify(signature);
                } catch (InvalidKeyException | SignatureException e) {
                    actualUsingPublic = false;
                }

                Asserts.assertEQ(expected, actualUsingInternal);
                Asserts.assertEQ(expected, actualUsingPublic);
            }
            System.out.println();
        }
    }

    // Helper methods for the MLDSAParameterSpec approach

    static byte[] sk2tr(byte[] sk) {
        return Arrays.copyOfRange(sk, 64, 128);
    }

    static byte[] pk2tr(byte[] pk) throws Exception {
        return MessageDigest.getInstance("SHAKE256-512").digest(pk);
    }

    static byte[] mprime(String preHash, byte[] context, byte[] msg)
            throws Exception {
        int ctxLen = context != null ? context.length : 0;
        byte[] oid = new byte[0];
        if (preHash != null) {
            oid = new DerOutputStream().putOID(ObjectIdentifier.of(
                    KnownOIDs.findMatch(preHash))).toByteArray();
            msg = MessageDigest.getInstance(preHash).digest(msg);
        }
        byte[] mprime = new byte[msg.length + 2 + ctxLen + oid.length];
        mprime[0] = preHash != null ? (byte)1 : (byte)0;
        mprime[1] = (byte) ctxLen;
        if (ctxLen > 0) {
            System.arraycopy(context, 0, mprime, 2, ctxLen);
        }
        if (oid.length > 0) {
            System.arraycopy(oid, 0, mprime, ctxLen + 2, oid.length);
        }
        System.arraycopy(msg, 0, mprime, ctxLen + 2 + oid.length, msg.length);
        return mprime;
    }

    static byte[] mu(byte[] mprime, byte[] tr) throws Exception {
        MessageDigest shake = MessageDigest.getInstance("SHAKE256-512");
        shake.update(tr);
        shake.update(mprime);
        return shake.digest();
    }
}
