/*
 * Copyright (c) 2025, Red Hat, Inc.
 *
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

import javax.crypto.*;
import javax.crypto.spec.*;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.HexFormat;
import java.util.List;

/*
 * @test
 * @bug 8328119
 * @summary test HKDF key derivation in SunPKCS11
 * @library /test/lib ..
 * @run main/othervm/timeout=30 TestHKDF
 */

public final class TestHKDF extends PKCS11Test {

    private static final boolean DEBUG = false;
    private static final HexFormat hex = HexFormat.of().withLowerCase();
    private static final byte[] knownBytes = hex.parseHex(
            "000102030405060708090a0b0c0d0e0f10111213141516");
    private static final OutputStream debugOut = new ByteArrayOutputStream();
    private static final PrintWriter debugPrinter = new PrintWriter(debugOut);
    private static boolean testFailed = false;
    private static Provider p11Provider;
    private static SecretKeyFactory p11GenericSkf;

    private record TestContext(String hkdfAlg, String derivedKeyAlg,
            Supplier<SecretKey> baseKey, byte[] salt, byte[] info,
            byte[] expectedPRK, byte[] expectedOKM, byte[] expectedOpOut) {
        // expectedOpOut value:
        //  - If derivedKeyAlg is AES, expectedOpOut is the result of encrypting
        //    knownBytes with derivedKey, using AES/CBC/PKCS5Padding and an IV
        //    of 16 zero bytes.
        //  - If derivedKeyAlg is Generic, expectedOpOut is the result of
        //    calculating the HmacSHA256 of knownBytes with derivedKey.
    }

    private static class HkdfTestAssertionException extends Exception {
        HkdfTestAssertionException(String msg) {
            super(msg);
        }
    }

    private enum KdfParamSpecType {
        EXTRACT,
        EXPAND,
        EXTRACT_EXPAND
    }

    private enum KeyMaterialType {
        KEY,
        DATA
    }

    private static final List<List<KeyMaterialType>> keyMaterialCombinations =
            List.of(
                    List.of(KeyMaterialType.KEY),
                    List.of(KeyMaterialType.DATA),
                    List.of(KeyMaterialType.KEY, KeyMaterialType.KEY),
                    List.of(KeyMaterialType.KEY, KeyMaterialType.DATA),
                    List.of(KeyMaterialType.DATA, KeyMaterialType.KEY),
                    List.of(KeyMaterialType.DATA, KeyMaterialType.DATA)
            );

    private static void addKeyMaterial(
            List<KeyMaterialType> keyMaterialCombination, byte[] keyMaterial,
            Consumer<SecretKey> addKeyCb, Consumer<byte[]> addDataCb)
            throws Exception {
        if (keyMaterial.length < keyMaterialCombination.size()) {
            throw new Exception("Key material is not enough to fulfill the " +
                    "combination requirement.");
        }
        int dataStart = 0, dataEnd;
        for (int i = 0; i < keyMaterialCombination.size(); i++) {
            dataEnd =
                    keyMaterial.length - keyMaterialCombination.size() + i + 1;
            byte[] chunk = Arrays.copyOfRange(keyMaterial, dataStart, dataEnd);
            if (keyMaterialCombination.get(i) == KeyMaterialType.KEY) {
                addKeyCb.accept(p11GenericSkf.generateSecret(
                        new SecretKeySpec(chunk, "Generic")));
            } else {
                addDataCb.accept(chunk);
            }
            dataStart = dataEnd;
        }
    }

    private static List<AlgorithmParameterSpec> generateKdfParamSpecs(
            TestContext ctx, KdfParamSpecType type) throws Exception {
        List<AlgorithmParameterSpec> kdfParamSpecs = new ArrayList<>();
        if (type == KdfParamSpecType.EXTRACT ||
                type == KdfParamSpecType.EXTRACT_EXPAND) {
            for (List<KeyMaterialType> keyMaterialCombination :
                    keyMaterialCombinations) {
                final HKDFParameterSpec.Builder b =
                        HKDFParameterSpec.ofExtract();
                SecretKey baseKey = ctx.baseKey.get();
                if (baseKey instanceof SecretKeySpec) {
                    addKeyMaterial(keyMaterialCombination, baseKey.getEncoded(),
                            b::addIKM, b::addIKM);
                } else if (baseKey != null) {
                    b.addIKM(baseKey);
                }
                if (ctx.salt != null) {
                    addKeyMaterial(keyMaterialCombination, ctx.salt, b::addSalt,
                            b::addSalt);
                }
                if (type == KdfParamSpecType.EXTRACT) {
                    kdfParamSpecs.add(b.extractOnly());
                } else {
                    kdfParamSpecs.add(b.thenExpand(ctx.info,
                            ctx.expectedOKM.length));
                }
                if (ctx.salt == null && !(baseKey instanceof SecretKeySpec)) {
                    // If the salt is null and the IKM is a non-SecretKeySpec
                    // (i.e. is a P11Key.P11SecretKey), the key material
                    // cannot be split and there will be a single
                    // HKDFParameterSpec to test.
                    break;
                }
            }
        } else {
            assert type == KdfParamSpecType.EXPAND : "Unexpected type.";
            kdfParamSpecs.add(HKDFParameterSpec.expandOnly(
                    new SecretKeySpec(ctx.expectedPRK, "Generic"), ctx.info,
                    ctx.expectedOKM.length));
        }
        return kdfParamSpecs;
    }

    private static void checkOpWithDerivedKey(TestContext ctx,
            SecretKey derivedKey, Provider p) throws Exception {
        byte[] opOut;
        switch (ctx.derivedKeyAlg) {
            case "AES" -> {
                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding", p);
                cipher.init(Cipher.ENCRYPT_MODE, derivedKey,
                        new IvParameterSpec(new byte[16]));
                opOut = cipher.doFinal(knownBytes);
            }
            case "Generic" -> {
                Mac hmac = Mac.getInstance("HmacSHA256", p);
                hmac.init(derivedKey);
                opOut = hmac.doFinal(knownBytes);
            }
            default -> throw new RuntimeException(
                    "Unexpected derived key algorithm.");
        }
        printByteArrayAssertion("Operation output", opOut, ctx.expectedOpOut);
        if (!Arrays.equals(opOut, ctx.expectedOpOut)) {
            throw new HkdfTestAssertionException(
                    "Operation with derived key failure.");
        }
    }

    private static void checkDerivationData(String derivationType,
            byte[] derivedKey, byte[] derivedData, byte[] expectedData)
            throws Exception {
        printByteArrayAssertion(derivationType + " key derivation", derivedKey,
                expectedData);
        printByteArrayAssertion(derivationType + " data derivation",
                derivedData, expectedData);
        if (!Arrays.equals(derivedKey, expectedData) ||
                !Arrays.equals(derivedData, expectedData)) {
            throw new HkdfTestAssertionException(
                    derivationType + " derivation failure.");
        }
    }

    private static void executeDerivationForKdfParamSpec(TestContext ctx,
            KdfParamSpecType type, KDF kdf, AlgorithmParameterSpec kdfParamSpec,
            Provider p) throws Exception {
        printDerivationInfo(ctx, type, kdfParamSpec, p);
        printHeader("HKDF derivation: output", '-', 10);
        String derivedKeyAlg = type == KdfParamSpecType.EXTRACT ?
                "Generic" : ctx.derivedKeyAlg;
        SecretKey derivedKey = kdf.deriveKey(derivedKeyAlg, kdfParamSpec);
        byte[] derivedData = kdf.deriveData(kdfParamSpec);
        if (type == KdfParamSpecType.EXPAND ||
                type == KdfParamSpecType.EXTRACT_EXPAND) {
            checkDerivationData("Extract", derivedKey.getEncoded(),
                    derivedData, ctx.expectedOKM);
            checkOpWithDerivedKey(ctx, derivedKey, p);
        } else {
            assert type == KdfParamSpecType.EXTRACT : "Unexpected type.";
            checkDerivationData("Expand", derivedKey.getEncoded(),
                    derivedData, ctx.expectedPRK);
        }
    }

    private static void crossCheckTestExpectedData(TestContext ctx,
            KdfParamSpecType type, AlgorithmParameterSpec kdfParamSpec)
            throws Exception {
        try {
            Provider sunJCE = Security.getProvider("SunJCE");
            KDF kdf = KDF.getInstance(ctx.hkdfAlg, sunJCE);
            executeDerivationForKdfParamSpec(ctx, type, kdf, kdfParamSpec,
                    sunJCE);
        } catch (HkdfTestAssertionException e) {
            // Fail if derivation was possible and the assertion data in this
            // test is inconsistent with SunJCE. This should never happen.
            throw e;
        } catch (Exception e) {
            // Cross-checking of the expected data in this test is a
            // best-effort. If derivation was not possible (e.g. SunJCE does
            // not support the HKDF algorithm), do not fail.
        }
    }

    private static void reportTestFailure(Exception e) {
        testFailed = true;
        printHeader("TEST FAILED", 'x', 10);
        e.printStackTrace(debugPrinter);
    }

    private static void executeDerivation(TestContext ctx,
            KdfParamSpecType type) {
        try {
            KDF kdf = KDF.getInstance(ctx.hkdfAlg, p11Provider);
            List<AlgorithmParameterSpec> kdfParamSpecs =
                    generateKdfParamSpecs(ctx, type);
            crossCheckTestExpectedData(ctx, type, kdfParamSpecs.get(0));
            for (AlgorithmParameterSpec kdfParamSpec : kdfParamSpecs) {
                executeDerivationForKdfParamSpec(ctx, type, kdf, kdfParamSpec,
                        p11Provider);
            }
        } catch (Exception e) {
            reportTestFailure(e);
        }
    }

    private static byte[] hexStringToByteArray(String hexString) {
        return hexString != null ? hex.parseHex(hexString) : null;
    }

    private static void executeTest(String testHeader, String hkdfAlg,
            String derivedKeyAlg, SecretKey baseKey, String saltHex,
            String infoHex, String expectedPRKHex, String expectedOKMHex,
            String expectedOpOutHex) {
        executeTest(testHeader, hkdfAlg, derivedKeyAlg, ()-> baseKey, saltHex,
                infoHex, expectedPRKHex, expectedOKMHex, expectedOpOutHex);
    }

    private static void executeTest(String testHeader, String hkdfAlg,
            String derivedKeyAlg, String baseKeyHex, String saltHex,
            String infoHex, String expectedPRKHex, String expectedOKMHex,
            String expectedOpOutHex) {
        executeTest(testHeader, hkdfAlg, derivedKeyAlg,
                ()-> new SecretKeySpec(hexStringToByteArray(baseKeyHex),
                        "Generic"), saltHex, infoHex, expectedPRKHex,
                expectedOKMHex, expectedOpOutHex);
    }

    private static void executeTest(String testHeader, String hkdfAlg,
            String derivedKeyAlg, Supplier<SecretKey> baseKey, String saltHex,
            String infoHex, String expectedPRKHex, String expectedOKMHex,
            String expectedOpOutHex) {
        printTestHeader(testHeader);
        TestContext ctx = new TestContext(hkdfAlg, derivedKeyAlg, baseKey,
                hexStringToByteArray(saltHex),
                hexStringToByteArray(infoHex),
                hexStringToByteArray(expectedPRKHex),
                hexStringToByteArray(expectedOKMHex),
                hexStringToByteArray(expectedOpOutHex));
        executeDerivation(ctx, KdfParamSpecType.EXTRACT_EXPAND);
        executeDerivation(ctx, KdfParamSpecType.EXTRACT);
        executeDerivation(ctx, KdfParamSpecType.EXPAND);
    }

    private static void executeInvalidKeyDerivationTest(String testHeader,
            String keyAlg, int keySize, String errorMsg) {
        printTestHeader(testHeader);
        try {
            KDF k = KDF.getInstance("HKDF-SHA256", p11Provider);
            k.deriveKey(keyAlg, HKDFParameterSpec.ofExtract()
                    .thenExpand(null, keySize));
            throw new Exception("No exception thrown.");
        } catch (InvalidAlgorithmParameterException iape) {
            // Expected.
        } catch (Exception e) {
            reportTestFailure(new Exception(errorMsg + " expected to throw " +
                    "InvalidAlgorithmParameterException for key algorithm '" +
                    keyAlg + "'.", e));
        }
    }

    private static void printTestHeader(String testHeader) {
        debugPrinter.println();
        debugPrinter.println("=".repeat(testHeader.length()));
        debugPrinter.println(testHeader);
        debugPrinter.println("=".repeat(testHeader.length()));
    }

    private static void printHeader(String header, char sepChar, int sepCount) {
        String sepBlock = String.valueOf(sepChar).repeat(sepCount);
        debugPrinter.println(sepBlock + " " + header + " " + sepBlock);
    }

    private static void printDerivationKeyMaterial(String header,
            List<SecretKey> keyMaterial, KdfParamSpecType type) {
        if (keyMaterial != null && !keyMaterial.isEmpty()) {
            debugPrinter.println(header + ":");
            for (SecretKey km : keyMaterial) {
                debugPrinter.print(" ".repeat(2));
                if (km instanceof SecretKeySpec) {
                    debugPrinter.println(hex.formatHex(km.getEncoded()));
                } else {
                    debugPrinter.println(km);
                }
            }
        } else if (type == KdfParamSpecType.EXTRACT ||
                type == KdfParamSpecType.EXTRACT_EXPAND) {
            debugPrinter.println(header + ": NULL");
        }
    }

    private static void printDerivationInfo(TestContext ctx,
            KdfParamSpecType type, AlgorithmParameterSpec kdfParamSpec,
            Provider p) {
        debugPrinter.println();
        printHeader("HKDF derivation: input", '-', 10);
        debugPrinter.println("Algorithm: " + ctx.hkdfAlg);
        debugPrinter.println("Provider: " + p.getName());
        debugPrinter.println("Derivation type: " + type);
        List<SecretKey> ikms = null;
        List<SecretKey> salts = null;
        byte[] info = null;
        Integer length = null;
        switch (kdfParamSpec) {
            case HKDFParameterSpec.Extract asExtract -> {
                debugPrinter.println("Derived key type: PRK (Generic)");
                salts = asExtract.salts();
                ikms = asExtract.ikms();
            }
            case HKDFParameterSpec.ExtractThenExpand asExtractExpand -> {
                debugPrinter.println("Derived key type: " + ctx.derivedKeyAlg);
                salts = asExtractExpand.salts();
                ikms = asExtractExpand.ikms();
                info = asExtractExpand.info();
                length = asExtractExpand.length();
            }
            case HKDFParameterSpec.Expand asExpand -> {
                debugPrinter.println("Derived key type: " + ctx.derivedKeyAlg);
                info = asExpand.info();
                length = asExpand.length();
            }
            case null, default -> throw new RuntimeException(
                    "Unrecognized AlgorithmParameterSpec class.");
        }
        printDerivationKeyMaterial("Salts", salts, type);
        printDerivationKeyMaterial("IKMs", ikms, type);
        if (info != null) {
            debugPrinter.println("Info: " + hex.formatHex(info));
        } else if (type == KdfParamSpecType.EXPAND ||
                type == KdfParamSpecType.EXTRACT_EXPAND) {
            debugPrinter.println("Info: NULL");
        }
        if (length != null) {
            debugPrinter.println("Length: " + length);
        }
    }

    private static void printByteArrayAssertion(String desc, byte[] actual,
            byte[] expected) {
        debugPrinter.println(desc + " (actual):");
        debugPrinter.println(actual != null ? hex.formatHex(actual) : "null");
        debugPrinter.println(desc + " (expected):");
        debugPrinter.println(expected != null ? hex.formatHex(expected) :
                "null");
    }

    private static SecretKey doKeyAgreement(String algorithm, PrivateKey privK,
            PublicKey pubK) throws Exception {
        KeyAgreement ka = KeyAgreement.getInstance(algorithm, p11Provider);
        ka.init(privK);
        ka.doPhase(pubK, true);
        return ka.generateSecret("TlsPremasterSecret");
    }

    private static SecretKey getTlsPremasterSecretWithDHExchange(String xHex,
            String yHex, String pHex, String gHex) throws Exception {
        KeyFactory kf = KeyFactory.getInstance("DH", p11Provider);
        BigInteger p = new BigInteger(pHex, 16);
        BigInteger g = new BigInteger(gHex, 16);
        PrivateKey privK = kf.generatePrivate(new DHPrivateKeySpec(
                new BigInteger(xHex, 16), p, g));
        PublicKey pubK = kf.generatePublic(new DHPublicKeySpec(
                new BigInteger(yHex, 16), p, g));
        return doKeyAgreement("DH", privK, pubK);
    }

    private static SecretKey getTlsPremasterSecretWithECDHExchange(String s,
            String wx, String wy) throws Exception {
        AlgorithmParameters p =
                AlgorithmParameters.getInstance("EC", p11Provider);
        p.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec params = p.getParameterSpec(ECParameterSpec.class);
        KeyFactory kf = KeyFactory.getInstance("EC", p11Provider);
        PrivateKey privK = kf.generatePrivate(new ECPrivateKeySpec(
                new BigInteger(s), params));
        ECPoint publicPoint = new ECPoint(new BigInteger(wx),
                new BigInteger(wy));
        PublicKey pubK = kf.generatePublic(new ECPublicKeySpec(
                publicPoint, params));
        return doKeyAgreement("ECDH", privK, pubK);
    }

    private static void test_RFC_5869_case_1() {
        executeTest("RFC 5869 - Test Case 1",
                "HKDF-SHA256",
                "Generic",
                "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b",
                "000102030405060708090a0b0c",
                "f0f1f2f3f4f5f6f7f8f9",
                "077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2" +
                "b3e5",
                "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4" +
                "c5bf34007208d5b887185865",
                "ad9e90d0c59d47539899647a3baf0fd364c54eeb5f4d0b80e1f39579e434" +
                "e801");
    }

    private static void test_RFC_5869_case_2() {
        executeTest("RFC 5869 - Test Case 2",
                "HKDF-SHA256",
                "Generic",
                "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d" +
                "1e1f202122232425262728292a2b2c2d2e2f303132333435363738393a3b" +
                "3c3d3e3f404142434445464748494a4b4c4d4e4f",
                "606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d" +
                "7e7f808182838485868788898a8b8c8d8e8f909192939495969798999a9b" +
                "9c9d9e9fa0a1a2a3a4a5a6a7a8a9aaabacadaeaf",
                "b0b1b2b3b4b5b6b7b8b9babbbcbdbebfc0c1c2c3c4c5c6c7c8c9cacbcccd" +
                "cecfd0d1d2d3d4d5d6d7d8d9dadbdcdddedfe0e1e2e3e4e5e6e7e8e9eaeb" +
                "ecedeeeff0f1f2f3f4f5f6f7f8f9fafbfcfdfeff",
                "06a6b88c5853361a06104c9ceb35b45cef760014904671014a193f40c15f" +
                "c244",
                "b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19af" +
                "a97c59045a99cac7827271cb41c65e590e09da3275600c2f09b8367793a9" +
                "aca3db71cc30c58179ec3e87c14c01d5c1f3434f1d87",
                "eabe8bc548bf430aedc423e9d7df94125eacff3dbb3b95b50379246c2546" +
                "01da");
    }

    private static void test_RFC_5869_case_3() {
        executeTest("RFC 5869 - Test Case 3",
                "HKDF-SHA256",
                "Generic",
                "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b",
                null,
                null,
                "19ef24a32c717b167f33a91d6f648bdf96596776afdb6377ac434c1c293c" +
                "cb04",
                "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c73" +
                "8d2d9d201395faa4b61a96c8",
                "06828b5679679681be59aa2822869cb1a174319e53a545e3301bd832ae3e" +
                "513f");
    }

    private static void test_AES_HKDFWithHmacSHA256() {
        executeTest("AES - HKDF-SHA256",
                "HKDF-SHA256",
                "AES",
                "000102030405060708090a0b0c0d0e0f",
                "101112131415161718191a1b1c1d1e1f",
                "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf",
                "0ecd9f09ddfc6b7bcad2646fa6bc10f922e5489a4ea755ec87ec1b7df379" +
                "85ca",
                "b97b1b4ce098f8e22f2f38b60d9f7a0e5902a1193602a876c010d73009dd" +
                "0701",
                "646e0175bcef43b9ebd2a3884699ad40b34d4b011e91679c5f25f0721d36" +
                "7f6a");
    }

    private static void test_AES_HKDFWithHmacSHA384() {
        executeTest("AES - HKDF-SHA384",
                "HKDF-SHA384",
                "AES",
                "000102030405060708090a0b0c0d0e0f",
                "101112131415161718191a1b1c1d1e1f",
                "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf",
                "31ca88a527220f8271d78df4ce6c4d973f135ad37973b96644b4d52d499d" +
                "0a2b03d53c875b1176b089e1e6161ab6d92b",
                "ba91a67e4d7640495194916ef1252418a651103fbddb0f2ec8b9d1f44f7a" +
                "7a0d",
                "f3cfcb44d7b36dce96f584c74118b434e714a13448321063241fd24ace11" +
                "f2a0");
    }

    private static void test_AES_HKDFWithHmacSHA512() {
        executeTest("AES - HKDF-SHA512",
                "HKDF-SHA512",
                "AES",
                "000102030405060708090a0b0c0d0e0f",
                "101112131415161718191a1b1c1d1e1f",
                "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf",
                "f6e6b1ddb24ea0f0ede0f533d1f350c86bf78966b0e5fd2af34dd00dae39" +
                "01d6279fe8111d6572e3cd05f2f0eeabb9144dc0da9437cdf37b0c6d7f3b" +
                "1064ab2b",
                "302212eb57ae758874e0e52fbdfa4eee29d7c694f181b21d8a8b571a43ce" +
                "aad5",
                "94459a6593f9c2cfea2ad32970efb8506f3a927927ba283fb6bfd7111aa8" +
                "63fc");
    }

    private static void test_AES_HKDFWithHmacSHA256_EmptyBaseKey() {
        executeTest("AES - HKDF-SHA256 (empty base key)",
                "HKDF-SHA256",
                "AES",
                (SecretKey) null,
                "101112131415161718191a1b1c1d1e1f",
                "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf",
                "cc267bd9515c1eba2cf6aaa1fc8380677f4351fcbea6d70873df5a334efc" +
                "ee0d",
                "cf353a33460b146c0eae3f0788ee281e5a0be15280fbeba107472aa1cd58" +
                "d111",
                "326e9028f51c05c1919215bad6e35668c94c88040c3777e8e6f8b6acdece" +
                "85fa");
    }

    private static void test_AES_HKDFWithHmacSHA256_EmptyBaseKeySaltInfo() {
        executeTest("AES - HKDF-SHA256 (empty base key, salt, and info)",
                "HKDF-SHA256",
                "AES",
                (SecretKey) null,
                null,
                null,
                "b613679a0814d9ec772f95d778c35fc5ff1697c493715653c6c712144292" +
                "c5ad",
                "eb70f01dede9afafa449eee1b1286504e1f62388b3f7dd4f956697b0e828" +
                "fe18",
                "3fdcf83994f6e0a6f6f482d097e242355e255a8ed17e661a71ca2d592c7a" +
                "884e");
    }

    private static void test_HKDF_after_DH_HkdfSHA256() throws Exception {
        SecretKey tlsPremasterSecret = getTlsPremasterSecretWithDHExchange(
                "00bcb8fa0a6b569961782a394599a1a02a05532a836819908a9a9000ed",
                "58ceab52f470026eaea24eb250e08d7cc23f21dda57ad628d14eab788633" +
                "cebc78c565f9292e6cfe9910d51c4878f590c46cbf380e19acf55cd468ab" +
                "672afb29c09b7edfd522d034019eadae75ea99bacf1e166548f092a5d371" +
                "930a275cbcb4bb02cb1d1b7a8bf3751dc85e61fb674059deef54e8ebbd36" +
                "3bdac4f85c5e49cb7dc8720a8088f047f319a63c2722a720e187f827578b" +
                "2545041bb5e640454e791f683622bb5aba4ab9bc51001c59bba5cd6cc0e2" +
                "aec00b0a5313a27454a93d3bd3f2ae5ab1c13165d1564e3b2d60629302b3" +
                "6bf44c1991bad279d3bd51b142294007f0c8828c9060d8b9b4cc6d335bcc" +
                "ce31d4e6aa18fd3ce99cb92aec09de2d",
                "00ffffffffffffffffc90fdaa22168c234c4c6628b80dc1cd129024e088a" +
                "67cc74020bbea63b139b22514a08798e3404ddef9519b3cd3a431b302b0a" +
                "6df25f14374fe1356d6d51c245e485b576625e7ec6f44c42e9a637ed6b0b" +
                "ff5cb6f406b7edee386bfb5a899fa5ae9f24117c4b1fe649286651ece45b" +
                "3dc2007cb8a163bf0598da48361c55d39a69163fa8fd24cf5f83655d23dc" +
                "a3ad961c62f356208552bb9ed529077096966d670c354e4abc9804f1746c" +
                "08ca18217c32905e462e36ce3be39e772c180e86039b2783a2ec07a28fb5" +
                "c55df06f4c52c9de2bcbf6955817183995497cea956ae515d2261898fa05" +
                "1015728e5a8aacaa68ffffffffffffffff",
                "02"
        );
        executeTest("Test HKDF-SHA256 after DH exchange (TLS)",
                "HKDF-SHA256",
                "Generic",
                tlsPremasterSecret,
                null,
                null,
                "e3cf8e5e0892ad251a5863c7f6ddc4fb988b1a723a30d3fe1ac235799caf" +
                "86e1",
                "86e508974080cdad9fa4407e253d35ae48f40e0e266c91dd04c775538c17" +
                "0eacd71bb4d54ba0c5065091",
                "2e94c8c852d318887fa94dac544c369bc25879efd39683a9dc5eda55f565" +
                "88c0");
    }

    private static void test_HKDF_after_ECDH_HkdfSHA256() throws Exception {
        SecretKey tlsPremasterSecret = getTlsPremasterSecretWithECDHExchange(
                "312092587041182431404764856027482553256569653405119712868911" +
                "21589605635583946",
                "851398477998049170325388348439523125186814652510003800309225" +
                "34333070929362623",
                "531080873930420952237875954830357399317339863932672261700603" +
                "26242234504331049");
        executeTest("Test HKDF-SHA256 after ECDH exchange (TLS)",
                "HKDF-SHA256",
                "Generic",
                tlsPremasterSecret,
                null,
                null,
                "638d8874237f12e42b366090ee8a0207d28a1ac8fd12b6a753ecb58c31cd" +
                "6a5e",
                "348a1afabe9560d3a0a6577e8bd66f0e8dc43b4ad52037f692ea5d28fbb2" +
                "bc963ef59eba65a83befc465",
                "bab55b2106b4fee07b7afc905ed7c1e84889e941fbc12f132c706addcfc0" +
                "6e09");
    }

    private static void test_unknown_key_algorithm_derivation() {
        executeInvalidKeyDerivationTest(
                "Test derivation of an unknown key algorithm",
                "UnknownAlgorithm",
                32,
                "Derivation of an unknown key algorithm");
    }

    private static void test_invalid_key_algorithm_derivation() {
        executeInvalidKeyDerivationTest(
                "Test derivation of an invalid key algorithm",
                "PBKDF2WithHmacSHA1",
                32,
                "Derivation of an invalid key algorithm");
    }

    private static void test_invalid_aes_key_algorithm_derivation() {
        executeInvalidKeyDerivationTest(
                "Test derivation of an invalid AES key",
                "PBEWithHmacSHA224AndAES_256",
                32,
                "Derivation of an invalid AES key");
    }

    private static void test_invalid_AES_key_size() {
        executeInvalidKeyDerivationTest(
                "Test derivation of an invalid AES key size",
                "AES",
                31,
                "Derivation of an AES key of invalid size (31 bytes)");
    }

    public void main(Provider p) throws Exception {
        p11Provider = p;
        p11GenericSkf = SecretKeyFactory.getInstance("Generic", p11Provider);
        for (Method m : TestHKDF.class.getDeclaredMethods()) {
            if (m.getName().startsWith("test")) {
                m.invoke(null);
            }
        }
        if (DEBUG || testFailed) {
            debugPrinter.flush();
            System.out.println(debugOut);
        }
        if (testFailed) {
            throw new Exception("TEST FAILED");
        }
        System.out.println("TEST PASS - OK");
    }

    public static void main(String[] args) throws Exception {
        main(new TestHKDF(), args);
    }
}
