/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.*;
import java.util.*;

import sun.security.ec.*;
import sun.security.ec.point.*;
import sun.security.util.ArrayUtil;
import sun.security.util.math.*;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8189189 8147502 8295010
 * @summary Test ECDSA primitive operations
 * @library /test/lib
 * @modules java.base/sun.security.ec java.base/sun.security.ec.point
 *          java.base/sun.security.util java.base/sun.security.util.math
 * @run main ECDSAPrimitive
 */
public class ECDSAPrimitive {

    private static final Map<String, String> CURVE_NAME_MAP = Map.ofEntries(
            Map.entry("P-256", "secp256r1"),
            Map.entry("P-384", "secp384r1"),
            Map.entry("P-521", "secp521r1")
    );
    private static final Set<String> DIGEST_NAME_SET = Set.of(
            "SHA-224",
            "SHA-256",
            "SHA-384",
            "SHA-512"
    );

    public static void main(String[] args) throws Exception {
        Path siggenFile = Path.of(System.getProperty("test.src"), "SigGen-1.txt");

        ECParameterSpec ecParams = null;
        String digestAlg = null;

        try (BufferedReader in = new BufferedReader(new FileReader(
                siggenFile.toFile()))) {
            Map<String, byte[]> values = new HashMap<>();
            String line = in.readLine();
            while (line != null) {
                line = line.trim();
                if (line.startsWith("#") || line.length() == 0) {
                    // ignore
                } else if (line.startsWith("[")) {
                    // change curve and hash
                    StringTokenizer tok = new StringTokenizer(line, "[,]");
                    String name = tok.nextToken();
                    String curveName = lookUpCurveName(name);

                    String digestName = tok.nextToken();
                    digestAlg = lookUpDigestName(digestName);

                    if (curveName == null) {
                        System.out.println("Unknown curve: " + name
                                + ". Skipping test");
                        ecParams = null;
                        digestAlg = null;
                    }
                    if (digestAlg == null) {
                        System.out.println("Unknown digest: " + digestName
                                + ". Skipping test");
                        ecParams = null;
                        digestAlg = null;
                    } else {
                        AlgorithmParameters params =
                                AlgorithmParameters.getInstance("EC", "SunEC");
                        params.init(new ECGenParameterSpec(curveName));
                        ecParams = params.getParameterSpec(
                                ECParameterSpec.class);
                        System.out.println("Testing curve/digest: "
                                + curveName + "/" + digestAlg);
                    }

                } else if (line.startsWith("S")) {
                    addKeyValue(line, values);
                    if (ecParams != null) {
                        runTest(ecParams, digestAlg, values);
                    }
                } else {
                    addKeyValue(line, values);
                }

                line = in.readLine();
            }
        }
    }

    private static void runTest(ECParameterSpec ecParams, String digestAlg,
                                Map<String, byte[]> values) throws Exception {

        Optional<ECDSAOperations> opsOpt =
                ECDSAOperations.forParameters(ecParams);
        Optional<Signer> signerOpt = opsOpt.map(OpsSigner::new);
        Signer signer = signerOpt.orElseGet(() -> new JCASigner(ecParams));

        byte[] msg = values.get("Msg");
        MessageDigest md = MessageDigest.getInstance(digestAlg);
        byte[] digest = md.digest(msg);

        // all operations accept little endian private key and nonce
        byte[] privateKey = values.get("d");
        byte[] k = values.get("k");

        byte[] computedSig = signer.sign(privateKey, digest, k);

        int valueLength = computedSig.length / 2;
        byte[] computedR = Arrays.copyOf(computedSig, valueLength);
        byte[] expectedR = values.get("R");
        Asserts.assertEquals(new BigInteger(1, expectedR), new BigInteger(1, computedR), "R");

        byte[] computedS = Arrays.copyOfRange(computedSig, valueLength,
                2 * valueLength);
        byte[] expectedS = values.get("S");
        Asserts.assertEquals(new BigInteger(1, expectedS), new BigInteger(1, computedS), "S");

        // ensure public key is correct
        byte[] expectedQx = values.get("Qx");
        byte[] expectedQy = values.get("Qy");
        ECPoint ecPublicKey =
                signer.checkPublicKey(privateKey, expectedQx, expectedQy);

        // ensure the verification works
        if (!signer.verify(ecPublicKey, digest, computedSig)) {
            throw new RuntimeException("Signature did not verify");
        }

        // ensure incorrect signature does not verify
        int length = k.length;
        computedSig[length / 2] ^= (byte) 1;
        if (signer.verify(ecPublicKey, digest, computedSig)) {
            throw new RuntimeException("Incorrect signature verified");
        }
        computedSig[length / 2] ^= (byte) 1;
        computedSig[length + length / 2] ^= (byte) 1;
        if (signer.verify(ecPublicKey, digest, computedSig)) {
            throw new RuntimeException("Incorrect signature verified");
        }

        System.out.println("Test case passed");
    }

    private static void addKeyValue(String line, Map<String, byte[]> values) {
        StringTokenizer tok = new StringTokenizer(line, " =");
        String key = tok.nextToken();
        String value = tok.nextToken();
        byte[] valueArr;
        if (value.length() <= 2) {
            valueArr = new byte[1];
            valueArr[0] = Byte.parseByte(value, 10);
        } else {
            // some values are odd-length big-endian integers
            if (value.length() % 2 == 1) {
                if (key.equals("Msg")) {
                    throw new RuntimeException("message length may not be odd");
                }
                value = "0" + value;
            }
            valueArr = HexFormat.of().parseHex(value);
        }

        values.put(key, valueArr);
    }

    private static String lookUpCurveName(String name) {
        return CURVE_NAME_MAP.get(name);
    }

    private static String lookUpDigestName(String name) {
        return DIGEST_NAME_SET.contains(name) ? name : null;
    }

    public static boolean verifySignedDigest(ECDSAOperations ops, ECPoint publicKey,
                                             byte[] digest, byte[] signature) {

        try {
            return verifySignedDigestImpl(ops, publicKey, digest, signature);
        } catch (ImproperSignatureException ex) {
            return false;
        }
    }

    private static boolean verifySignedDigestImpl(ECDSAOperations ops, ECPoint publicKey,
                                                  byte[] digest, byte[] signature)
            throws ImproperSignatureException {

        ECOperations ecOps = ops.getEcOperations();
        IntegerFieldModuloP orderField = ecOps.getOrderField();
        int orderBits = orderField.getSize().bitLength();
        if (orderBits % 8 != 0 && orderBits < digest.length * 8) {
            // This implementation does not support truncating digests to
            // a length that is not a multiple of 8.
            throw new ProviderException("Invalid digest length");
        }
        // decode signature as (r, s)
        byte[] rBytes = Arrays.copyOf(signature, signature.length / 2);
        ArrayUtil.reverse(rBytes);
        byte[] sBytes = Arrays.copyOfRange(signature, signature.length / 2,
                signature.length);
        ArrayUtil.reverse(sBytes);

        // convert r and s to field elements
        // TODO: reject non-canonical values
        IntegerModuloP s = orderField.getElement(sBytes);
        IntegerModuloP r = orderField.getElement(rBytes);

        // truncate the digest and interpret as a field element
        int length = (orderBits + 7) / 8;
        int lengthE = Math.min(length, digest.length);
        byte[] E = new byte[lengthE];
        System.arraycopy(digest, 0, E, 0, lengthE);
        ArrayUtil.reverse(E);
        IntegerModuloP e = orderField.getElement(E);

        // perform the calculation
        IntegerModuloP sInverse = s.multiplicativeInverse();
        IntegerModuloP u1 = e.multiply(sInverse);
        IntegerModuloP u2 = r.multiply(sInverse);

        byte[] u1Bytes = u1.asByteArray(length);
        byte[] u2Bytes = u2.asByteArray(length);
        AffinePoint publicKeyPoint = ECDSAOperations.toAffinePoint(publicKey,
                ecOps.getField());
        MutablePoint R = ecOps.multiply(publicKeyPoint, u2Bytes);
        AffinePoint a1 = ops.basePointMultiply(u1Bytes);
        MutablePoint p2 = new ProjectivePoint.Mutable(
                a1.getX(false).mutable(),
                a1.getY(false).mutable(),
                ecOps.getField().get1().mutable());
        ecOps.setSum(R, p2);

        // can't continue if R is neutral
        if (ecOps.isNeutral(R)) {
            throw new ImproperSignatureException();
        }

        IntegerModuloP xr = R.asAffine().getX();
        byte[] temp = new byte[length];
        xr.asByteArray(temp);
        IntegerModuloP v = orderField.getElement(temp);

        // Check that v==r by subtracting and comparing result to 0
        v.subtract(r).mutable().asByteArray(temp);
        return ECOperations.allZero(temp);
    }

    private interface Signer {
        byte[] sign(byte[] privateKey, byte[] digest, byte[] k);

        ECPoint checkPublicKey(byte[] privateKey, byte[] expectedQx,
                               byte[] expectedQy);

        boolean verify(ECPoint ecPublicKey, byte[] digest, byte[] sig);
    }

    private static class FixedRandom extends SecureRandom {

        private final byte[] val;

        public FixedRandom(byte[] val) {
            BigInteger biVal = new BigInteger(1, val);
            biVal = biVal.subtract(BigInteger.ONE);
            byte[] temp = biVal.toByteArray();
            this.val = new byte[val.length];
            int inStartPos = Math.max(0, temp.length - val.length);
            int outStartPos = Math.max(0, val.length - temp.length);
            System.arraycopy(temp, inStartPos, this.val, outStartPos,
                    temp.length - inStartPos);
        }

        @Override
        public void nextBytes(byte[] bytes) {
            Arrays.fill(bytes, (byte) 0);
            int copyLength = Math.min(val.length, bytes.length - 2);
            System.arraycopy(val, 0, bytes, bytes.length - copyLength - 2,
                    copyLength);
        }
    }

    // The signature verification function lives here. It is not used in the
    // JDK, but it is working, and the performance is roughly as good as the
    // native implementation in the JDK.

    private static class JCASigner implements Signer {

        private static final String SIG_ALG = "NONEwithECDSAinP1363Format";
        private final ECParameterSpec ecParams;

        private JCASigner(ECParameterSpec ecParams) {
            this.ecParams = ecParams;
        }

        @Override
        public byte[] sign(byte[] privateKey, byte[] digest, byte[] k) {

            try {

                KeyFactory kf = KeyFactory.getInstance("EC", "SunEC");
                BigInteger s = new BigInteger(1, privateKey);
                ECPrivateKeySpec privKeySpec =
                        new ECPrivateKeySpec(s, ecParams);
                PrivateKey privKey = kf.generatePrivate(privKeySpec);

                Signature sig = Signature.getInstance(SIG_ALG, "SunEC");
                sig.initSign(privKey, new FixedRandom(k));
                sig.update(digest);
                return sig.sign();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public ECPoint checkPublicKey(byte[] privateKey, byte[] expectedQx,
                                      byte[] expectedQy) {
            // no way to compute the public key using the API
            BigInteger x = new BigInteger(1, expectedQx);
            BigInteger y = new BigInteger(1, expectedQy);
            return new ECPoint(x, y);
        }

        @Override
        public boolean verify(ECPoint ecPublicKey, byte[] digest,
                              byte[] providedSig) {

            try {
                KeyFactory kf = KeyFactory.getInstance("EC", "SunEC");
                ECPublicKeySpec pubKeySpec =
                        new ECPublicKeySpec(ecPublicKey, ecParams);
                PublicKey pubKey = kf.generatePublic(pubKeySpec);

                Signature sig = Signature.getInstance(SIG_ALG, "SunEC");
                sig.initVerify(pubKey);
                sig.update(digest);
                return sig.verify(providedSig);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private static class OpsSigner implements Signer {

        private final ECDSAOperations ops;

        public OpsSigner(ECDSAOperations ops) {
            this.ops = ops;
        }

        @Override
        public byte[] sign(byte[] privateKey, byte[] digest, byte[] k) {

            privateKey = privateKey.clone();
            ArrayUtil.reverse(privateKey);
            k = k.clone();
            ArrayUtil.reverse(k);
            ECDSAOperations.Nonce nonce = new ECDSAOperations.Nonce(k);
            try {
                return ops.signDigest(privateKey, digest, nonce);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public ECPoint checkPublicKey(byte[] privateKey, byte[] expectedQx,
                                      byte[] expectedQy) {

            privateKey = privateKey.clone();
            ArrayUtil.reverse(privateKey);
            AffinePoint publicKey = ops.basePointMultiply(privateKey);
            int length = privateKey.length;
            byte[] computedQx = new byte[length];
            byte[] computedQy = new byte[length];
            publicKey.getX().asByteArray(computedQx);
            ArrayUtil.reverse(computedQx);
            Asserts.assertEqualsByteArray(expectedQx, computedQx, "Qx");
            publicKey.getY().asByteArray(computedQy);
            ArrayUtil.reverse(computedQy);
            Asserts.assertEqualsByteArray(expectedQy, computedQy, "Qy");
            BigInteger bigX = publicKey.getX().asBigInteger();
            BigInteger bigY = publicKey.getY().asBigInteger();
            return new ECPoint(bigX, bigY);
        }

        @Override
        public boolean verify(ECPoint publicKey, byte[] digest, byte[] sig) {
            return verifySignedDigest(ops, publicKey, digest, sig);
        }
    }

    /*
     * An exception indicating that a signature is not formed correctly.
     */
    private static class ImproperSignatureException extends Exception {

        private static final long serialVersionUID = 1;
    }

}
