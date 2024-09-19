/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8190951
 * @summary KDF API tests
 * @library /test/lib
 * @run main/othervm -Djava.security.egd=file:/dev/urandom HKDFTest
 * @enablePreview
 */

import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;

import javax.crypto.KDF;
import javax.crypto.KDFParameters;
import javax.crypto.spec.HKDFParameterSpec;
import java.util.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

public class HKDFTest {

    private static final String JDK_HKDF_SHA256 = "HKDF-SHA256";
    private static final String JDK_HKDF_SHA384 = "HKDF-SHA384";
    private static final String JDK_HKDF_SHA512 = "HKDF-SHA512";
    private static final String[] KDF_ALG = {
            JDK_HKDF_SHA256,
            JDK_HKDF_SHA384,
            JDK_HKDF_SHA512
    };
    private static final String SUNJCE = "SunJCE";

    // KEYS and RAW_DATA holds valid values for IKM and SALTS
    private static final List<SecretKey> KEYS = List.of(
            new SecretKeySpec(new byte[]{0}, "HKDF-IKM"),
            new SecretKeySpec("IKM".getBytes(), "HKDF-IKM")
    );
    private static final List<byte[]> RAW_DATA = List.of(
            new byte[]{0},
            "RAW".getBytes()
    );
    private static final byte[] EMPTY = new byte[0];
    private static final int SHORT_LENGTH = 42;
    private static final int LARGE_LENGTH = 1000;
    private static final int NEGATIVE_LENGTH = -1;
    // Implementation of Kdf functional Interface
    private final static Kdf<String, String, AlgorithmParameterSpec> kdf = (a, p, s) -> {

        // Test KDF getInstance methods, all should have same algo and provider
        KDF k1 = KDF.getInstance(a);
        KDF k2 = KDF.getInstance(a, p);
        KDF k3 = KDF.getInstance(a, Security.getProvider(p));
        Asserts.assertEquals(k1.getAlgorithm(), k2.getAlgorithm());
        Asserts.assertEquals(k2.getAlgorithm(), k3.getAlgorithm());
        Asserts.assertEquals(k1.getProviderName(), k2.getProviderName());
        Asserts.assertEquals(k2.getProviderName(), k3.getProviderName());
        Asserts.assertEquals(k1.getParameters(), k2.getParameters());
        Asserts.assertEquals(k2.getParameters(), k3.getParameters());

        // Test KDF getInstance methods with parameters
        KDFParameters spec = (KDFParameters) s;
        k1 = KDF.getInstance(a, spec);
        k2 = KDF.getInstance(a, spec, p);
        k3 = KDF.getInstance(a, spec, Security.getProvider(p));
        Asserts.assertEquals(k1.getAlgorithm(), k2.getAlgorithm());
        Asserts.assertEquals(k2.getAlgorithm(), k3.getAlgorithm());
        Asserts.assertEquals(k1.getProviderName(), k2.getProviderName());
        Asserts.assertEquals(k2.getProviderName(), k3.getProviderName());
        Asserts.assertEquals(k1.getParameters(), k2.getParameters());
        Asserts.assertEquals(k2.getParameters(), k3.getParameters());
    };
    // Implementation of Extract functional Interface
    // Parameters can be of type SecretKey or byte[]
    private final static Extract extract = (ikm, salt) -> {
        HKDFParameterSpec.Builder b = HKDFParameterSpec.ofExtract();

        if (ikm instanceof SecretKey) {
            b.addIKM((SecretKey) ikm);
        } else {
            b.addIKM((byte[]) ikm);
        }

        if (salt instanceof SecretKey) {
            b.addSalt((SecretKey) salt);
        } else {
            b.addSalt((byte[]) salt);
        }

        HKDFParameterSpec.Extract param = b.extractOnly();

        if ((ikm instanceof SecretKey) || ((byte[]) ikm).length != 0) {
            Asserts.assertTrue(param.ikms().contains(
                    (ikm instanceof SecretKey)
                            ? ikm
                            : (new SecretKeySpec((byte[]) ikm, "Generic"))));
        }

        if ((salt instanceof SecretKey) || ((byte[]) salt).length != 0) {
            Asserts.assertTrue(param.salts().contains(
                    (salt instanceof SecretKey)
                            ? salt
                            : (new SecretKeySpec((byte[]) salt, "Generic"))));
        }

        return param;
    };
    // Implementation of Expand functional Interface
    private final static Expand expand = (prk, info, len) -> {
        HKDFParameterSpec.Expand param = HKDFParameterSpec.expandOnly(
                ((SecretKey) prk),
                (byte[]) info,
                (int) len);
        Asserts.assertEqualsByteArray(
                ((SecretKey) prk).getEncoded(), param.prk().getEncoded());
        Asserts.assertEqualsByteArray((byte[]) info, param.info());
        Asserts.assertEquals(len, param.length());
        return param;
    };
    // Implementation of ExtractThenExpand functional Interface
    private final static ExtractThenExpand extExp = (ikm, salt, info, len) -> {
        HKDFParameterSpec.Builder b = HKDFParameterSpec.ofExtract();
        // Go with default value for ikm/salt in case of null
        if ((ikm instanceof SecretKey)) {
            b.addIKM((SecretKey) ikm);
        } else {
            b.addIKM((byte[]) ikm);
        }
        if (salt instanceof SecretKey) {
            b.addSalt((SecretKey) salt);
        } else {
            b.addSalt((byte[]) salt);
        }
        HKDFParameterSpec.ExtractThenExpand param = b.thenExpand((byte[]) info, (int) len);
        if ((ikm instanceof SecretKey) || ((byte[]) ikm).length != 0) {
            Asserts.assertTrue(param.ikms().contains(
                    (ikm instanceof SecretKey)
                            ? ikm
                            : (new SecretKeySpec((byte[]) ikm, "Generic"))));
        }
        if ((salt instanceof SecretKey) || ((byte[]) salt).length != 0) {
            Asserts.assertTrue(param.salts().contains(
                    (salt instanceof SecretKeySpec)
                            ? salt
                            : (new SecretKeySpec((byte[]) salt, "Generic"))));
        }
        Asserts.assertEqualsByteArray((byte[]) info, param.info());
        Asserts.assertEquals(len, param.length());
        return param;
    };
    // Implementation of Compare functional Interface
    // Pass 'len < 0' when deriveKey()/deriveData() check is not required
    private final static Compare<KDF, HKDFParameterSpec, HKDFParameterSpec, String, SecretKey, Integer> compare =
            (hk, lhs, rhs, t, s, len) -> {
                SecretKey sk = null;
                Asserts.assertEqualsByteArray(
                        (sk = hk.deriveKey(t, lhs)).getEncoded(),
                        hk.deriveKey(t, rhs).getEncoded()
                );
                Asserts.assertEqualsByteArray(
                        hk.deriveKey(t, lhs).getEncoded(), sk.getEncoded());
                Asserts.assertEqualsByteArray(hk.deriveData(lhs), sk.getEncoded());
                Asserts.assertEqualsByteArray(hk.deriveData(lhs), hk.deriveData(rhs));
                Asserts.assertEqualsByteArray(
                        hk.deriveData(lhs), hk.deriveKey(t, rhs).getEncoded());
                if (s != null) {
                    Asserts.assertEqualsByteArray(
                            sk.getEncoded(), s.getEncoded());
                }
                if (len >= 0) {
                    Asserts.assertEquals(hk.deriveKey(t, lhs).getEncoded().length, len);
                    Asserts.assertEquals(hk.deriveData(rhs).length, len);
                }
            };
    // Implementation of Derive functional Interface
    private final static Derive<KDF, HKDFParameterSpec, HKDFParameterSpec, String, String> derive =
            (hk, lhs, rhs, a1, a2) -> {
                SecretKey sk1 = hk.deriveKey(a1, lhs);
                SecretKey sk2 = hk.deriveKey(a2, rhs);
                Asserts.assertEqualsByteArray(sk1.getEncoded(), sk2.getEncoded());
                byte[] bk1 = hk.deriveData(lhs);
                Asserts.assertEqualsByteArray(bk1, sk1.getEncoded());
            };

    public static void main(String[] args) throws Exception {
        System.out.println("Starting Test '" + HKDFTest.class.getName() + "'");

        // POSITIVE TestCase[Operations - extractOnly, expandOnly, thenExpand]
        // Run for all supported algorithms with non-empty parameters
        for (boolean useKey : new boolean[]{true, false}) {
            for (String algo : KDF_ALG) {
                System.out.println("Testing with " + algo + " and useKey " + useKey);
                KDF hk = KDF.getInstance(algo);
                for (SecretKey secretKey : KEYS) {
                    for (byte[] bytes : RAW_DATA) {
                        var key = useKey ? secretKey : secretKey.getEncoded();
                        var raw = useKey
                                ? new SecretKeySpec(bytes, "Generic")
                                : bytes;
                        // extract
                        HKDFParameterSpec extract1 = extract.extract(key, raw);
                        HKDFParameterSpec extract2 = extract.extract(key, raw);
                        SecretKey sk = hk.deriveKey("PRK", extract1);
                        compare.compare(hk, extract1, extract2, "PRK", null, NEGATIVE_LENGTH);

                        // expand
                        HKDFParameterSpec expand1 = expand.expand(sk,
                                ((raw instanceof SecretKey)
                                        ? ((SecretKey) raw).getEncoded()
                                        : raw),
                                SHORT_LENGTH);
                        HKDFParameterSpec expand2 = expand.expand(sk,
                                ((raw instanceof SecretKey)
                                        ? ((SecretKey) raw).getEncoded()
                                        : raw), SHORT_LENGTH);
                        sk = hk.deriveKey("OKM", expand1);
                        compare.compare(hk, expand1, expand2, "OKM", sk, SHORT_LENGTH);

                        // extractExpand
                        HKDFParameterSpec extractExpand1 = extExp.extExp(key, raw,
                                ((raw instanceof SecretKey)
                                        ? ((SecretKey) raw).getEncoded()
                                        : raw),
                                SHORT_LENGTH);
                        HKDFParameterSpec extractExpand2 = extExp.extExp(key, raw,
                                ((raw instanceof SecretKey)
                                        ? ((SecretKey) raw).getEncoded()
                                        : raw),
                                SHORT_LENGTH);
                        compare.compare(hk, extractExpand1, extractExpand2, "OKM", sk, SHORT_LENGTH);
                    }
                }
            }
        }

        // Test KDF.getInstance methods
        System.out.println("Testing getInstance methods");
        testGetInstanceMethods();
        testGetInstanceNegative();

        /* Executing following test cases with one supported algorithm is sufficient */
        KDF hk = KDF.getInstance(KDF_ALG[0]);

        // Test extract
        System.out.println("Testing extract method");
        testExtractMethod(hk);

        // Test expand
        System.out.println("Testing expand method");
        testExpandMethod(hk);

        // Test ExtractThenExpand
        System.out.println("Testing extractThenExpand method");
        testExtractExpandMethod(hk);

        System.out.println("Test executed successfully.");
    }

    private static void testGetInstanceMethods() throws InvalidAlgorithmParameterException
            , NoSuchAlgorithmException, NoSuchProviderException {
        // POSITIVE TestCase: KDF getInstance methods test
        for (String algo : KDF_ALG) {
            kdf.test(algo, SUNJCE, null);
        }
    }

    private static void testGetInstanceNegative() {

        final String NULL_STRING = null;
        final String INVALID_STRING = "INVALID";
        final Provider NULL_PROVIDER = null;
        final Provider SUNJCE_PROVIDER = Security.getProvider(SUNJCE);
        final KDFParameters NULL_KDF_PARAMS = null;

        // getInstance(String algorithm)
        Utils.runAndCheckException(() -> KDF.getInstance(NULL_STRING),
                NullPointerException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(INVALID_STRING),
                NoSuchAlgorithmException.class);

        // getInstance(String algorithm, String provider)
        Utils.runAndCheckException(() -> KDF.getInstance(NULL_STRING, SUNJCE),
                NullPointerException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(INVALID_STRING, SUNJCE),
                NoSuchAlgorithmException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(KDF_ALG[0], NULL_STRING),
                NullPointerException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(KDF_ALG[0], INVALID_STRING),
                NoSuchProviderException.class);

        // getInstance(String algorithm, Provider provider)
        Utils.runAndCheckException(() -> KDF.getInstance(NULL_STRING, SUNJCE_PROVIDER),
                NullPointerException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(INVALID_STRING, SUNJCE_PROVIDER),
                NoSuchAlgorithmException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(KDF_ALG[0], NULL_PROVIDER),
                NullPointerException.class);

        // getInstance(String algorithm, KDFParameters kdfParameters)
        // null spec is a valid case but different class is not
        Utils.runAndCheckException(() -> KDF.getInstance(NULL_STRING, NULL_KDF_PARAMS),
                NullPointerException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(INVALID_STRING, NULL_KDF_PARAMS),
                NoSuchAlgorithmException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(KDF_ALG[0], (KDFParameters) new KDFAlgorithmParameterSpec()),
                ClassCastException.class);

        // getInstance(String algorithm, KDFParameters kdfParameters, String provider)
        Utils.runAndCheckException(() -> KDF.getInstance(NULL_STRING, NULL_KDF_PARAMS, SUNJCE),
                NullPointerException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(INVALID_STRING, NULL_KDF_PARAMS, SUNJCE),
                NoSuchAlgorithmException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(KDF_ALG[0], (KDFParameters) new KDFAlgorithmParameterSpec(), SUNJCE),
                ClassCastException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(KDF_ALG[0], NULL_KDF_PARAMS, NULL_STRING),
                NullPointerException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(KDF_ALG[0], NULL_KDF_PARAMS, INVALID_STRING),
                NoSuchProviderException.class);

        // getInstance(String algorithm, KDFParameters kdfParameters, Provider provider)
        Utils.runAndCheckException(() -> KDF.getInstance(NULL_STRING, NULL_KDF_PARAMS, SUNJCE_PROVIDER),
                NullPointerException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(INVALID_STRING, NULL_KDF_PARAMS, SUNJCE_PROVIDER),
                NoSuchAlgorithmException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(KDF_ALG[0], (KDFParameters) new KDFAlgorithmParameterSpec(), SUNJCE_PROVIDER),
                ClassCastException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(KDF_ALG[0], NULL_KDF_PARAMS, NULL_PROVIDER),
                NullPointerException.class);
    }

    private static void testExtractMethod(KDF hk) throws InvalidAlgorithmParameterException
            , InvalidParameterSpecException, NoSuchAlgorithmException {
        // POSITIVE TestCase: Extract - Empty bytes for IKM/SALT
        HKDFParameterSpec ext1 = null;
        HKDFParameterSpec ext2 = null;
        ext1 = extract.extract(EMPTY, RAW_DATA.getFirst());
        ext2 = extract.extract(EMPTY, RAW_DATA.getFirst());
        compare.compare(hk, ext1, ext2, "PRK", null, NEGATIVE_LENGTH);

        ext1 = extract.extract(RAW_DATA.getFirst(), EMPTY);
        ext2 = extract.extract(RAW_DATA.getFirst(), EMPTY);
        compare.compare(hk, ext1, ext2, "PRK", null, NEGATIVE_LENGTH);

        ext1 = extract.extract(EMPTY, KEYS.getFirst());
        ext2 = extract.extract(EMPTY, KEYS.getFirst());
        compare.compare(hk, ext1, ext2, "PRK", null, NEGATIVE_LENGTH);

        ext1 = extract.extract(KEYS.getFirst(), EMPTY);
        ext2 = extract.extract(KEYS.getFirst(), EMPTY);
        compare.compare(hk, ext1, ext2, "PRK", null, NEGATIVE_LENGTH);

        ext1 = extract.extract(EMPTY, EMPTY);
        ext2 = extract.extract(EMPTY, EMPTY);
        compare.compare(hk, ext1, ext2, "PRK", null, NEGATIVE_LENGTH);

        // POSITIVE TestCase: Extract - Derive keys/data with unknown algorithm name
        derive.derive(hk,
                extract.extract(KEYS.getFirst(), RAW_DATA.getFirst()),
                extract.extract(KEYS.getFirst(), RAW_DATA.getFirst()),
                "XYZ", "ABC");

        // NEGATIVE TestCase: Extract - NULL algo to derive key
        Utils.runAndCheckException(() -> hk.deriveKey(null,
                        extract.extract(KEYS.getFirst(), RAW_DATA.getFirst())),
                NullPointerException.class);
        Utils.runAndCheckException(() -> hk.deriveKey("",
                        extract.extract(KEYS.getFirst(), RAW_DATA.getFirst())),
                NoSuchAlgorithmException.class);
        Utils.runAndCheckException(() -> hk.deriveKey(null,
                        extract.extract(RAW_DATA.getFirst(), KEYS.getFirst())),
                NullPointerException.class);

        // NEGATIVE TestCase: Extract - NULL IKM/SALT
        Utils.runAndCheckException(() -> extract.extract(null, RAW_DATA.getFirst()),
                NullPointerException.class);
        Utils.runAndCheckException(() -> extract.extract(RAW_DATA.getFirst(), null),
                NullPointerException.class);
        Utils.runAndCheckException(() -> extract.extract(null, KEYS.getFirst()),
                NullPointerException.class);
        Utils.runAndCheckException(() -> extract.extract(KEYS.getFirst(), null),
                NullPointerException.class);
        Utils.runAndCheckException(() -> extract.extract(null, null),
                NullPointerException.class);
    }

    private static void testExpandMethod(KDF hk) throws InvalidAlgorithmParameterException
            , InvalidParameterSpecException, NoSuchAlgorithmException {
        SecretKey prk = hk.deriveKey("PRK", extract.extract(KEYS.get(1), RAW_DATA.getFirst()));
        // POSITIVE TestCase: Expand - 'info' is null
        HKDFParameterSpec exp1 = expand.expand(prk, null, SHORT_LENGTH);
        HKDFParameterSpec exp2 = expand.expand(prk, null, SHORT_LENGTH);
        compare.compare(hk, exp1, exp2, "OKM", null, SHORT_LENGTH);
        exp1 = expand.expand(prk, null, LARGE_LENGTH);
        exp2 = expand.expand(prk, null, LARGE_LENGTH);
        compare.compare(hk, exp1, exp2, "OKM", null, LARGE_LENGTH);

        // POSITIVE TestCase: Expand parameter 'info' is empty byte
        exp1 = expand.expand(prk, EMPTY, SHORT_LENGTH);
        exp2 = expand.expand(prk, EMPTY, SHORT_LENGTH);
        compare.compare(hk, exp1, exp2, "OKM", null, SHORT_LENGTH);
        exp1 = expand.expand(prk, EMPTY, LARGE_LENGTH);
        exp2 = expand.expand(prk, EMPTY, LARGE_LENGTH);
        compare.compare(hk, exp1, exp2, "OKM", null, LARGE_LENGTH);

        // POSITIVE TestCase: Expand - Derive keys/data with unknown algorithm name
        derive.derive(hk,
                expand.expand(prk, RAW_DATA.getFirst(), SHORT_LENGTH),
                expand.expand(prk, RAW_DATA.getFirst(), SHORT_LENGTH),
                "XYZ", "ABC");

        // NEGATIVE TestCase: Expand - PRK=null
        Utils.runAndCheckException(() -> expand.expand(null, RAW_DATA.getFirst(), SHORT_LENGTH),
                NullPointerException.class);

        // NEGATIVE TestCase: Expand - Derive keys/data of negative length
        Utils.runAndCheckException(() -> expand.expand(KEYS.getFirst(), RAW_DATA.getFirst(), -1),
                IllegalArgumentException.class);

        // NEGATIVE TestCase: Expand - PRK is not derived
        Utils.runAndCheckException(() -> hk.deriveKey("PRK",
                        expand.expand(KEYS.get(1), RAW_DATA.getFirst(), SHORT_LENGTH)),
                InvalidAlgorithmParameterException.class);
    }

    private static void testExtractExpandMethod(KDF hk) throws InvalidAlgorithmParameterException
            , InvalidParameterSpecException, NoSuchAlgorithmException {
        // POSITIVE TestCase: ExtractExpand - 'info' is null
        HKDFParameterSpec exep1 = extExp.extExp(RAW_DATA.getFirst(), RAW_DATA.getFirst(), null, SHORT_LENGTH);
        HKDFParameterSpec exep2 = extExp.extExp(RAW_DATA.getFirst(), RAW_DATA.getFirst(), null, SHORT_LENGTH);
        compare.compare(hk, exep1, exep2, "OKM", null, SHORT_LENGTH);
        exep1 = extExp.extExp(RAW_DATA.getFirst(), RAW_DATA.getFirst(), null, LARGE_LENGTH);
        exep2 = extExp.extExp(RAW_DATA.getFirst(), RAW_DATA.getFirst(), null, LARGE_LENGTH);
        compare.compare(hk, exep1, exep2, "OKM", null, LARGE_LENGTH);

        // POSITIVE TestCase: ExtractExpand - 'info' is empty byte
        exep1 = extExp.extExp(RAW_DATA.getFirst(), RAW_DATA.getFirst(), EMPTY, SHORT_LENGTH);
        exep2 = extExp.extExp(RAW_DATA.getFirst(), RAW_DATA.getFirst(), EMPTY, SHORT_LENGTH);
        compare.compare(hk, exep1, exep2, "OKM", null, SHORT_LENGTH);
        exep1 = extExp.extExp(RAW_DATA.getFirst(), RAW_DATA.getFirst(), EMPTY, LARGE_LENGTH);
        exep2 = extExp.extExp(RAW_DATA.getFirst(), RAW_DATA.getFirst(), EMPTY, LARGE_LENGTH);
        compare.compare(hk, exep1, exep2, "OKM", null, LARGE_LENGTH);

        // POSITIVE TestCase: ExtractExpand - Derive keys/data with unknown algorithm names
        derive.derive(hk,
                extExp.extExp(KEYS.getFirst(), RAW_DATA.getFirst(), RAW_DATA.getFirst(), SHORT_LENGTH),
                extExp.extExp(KEYS.getFirst(), RAW_DATA.getFirst(), RAW_DATA.getFirst(), SHORT_LENGTH),
                "XYZ", "ABC");

        // NEGATIVE TestCase: ExtractExpand - NULL IKM/SALT
        Utils.runAndCheckException(() ->
                        extExp.extExp(null, RAW_DATA.getFirst(), RAW_DATA.getFirst(), SHORT_LENGTH),
                NullPointerException.class);
        Utils.runAndCheckException(() ->
                        extExp.extExp(null, KEYS.getFirst(), RAW_DATA.getFirst(), SHORT_LENGTH),
                NullPointerException.class);
        Utils.runAndCheckException(() ->
                        extExp.extExp(RAW_DATA.getFirst(), null, RAW_DATA.getFirst(), SHORT_LENGTH),
                NullPointerException.class);
        Utils.runAndCheckException(() ->
                        extExp.extExp(KEYS.getFirst(), null, RAW_DATA.getFirst(), SHORT_LENGTH),
                NullPointerException.class);

        // NEGATIVE: ExtractExpand Parameters - negative length
        Utils.runAndCheckException(() ->
                        extExp.extExp(RAW_DATA.getFirst(), RAW_DATA.getFirst(), RAW_DATA.getFirst(), -1),
                IllegalArgumentException.class);
        Utils.runAndCheckException(() ->
                        extExp.extExp(KEYS.getFirst(), KEYS.getFirst(), RAW_DATA.getFirst(), -1),
                IllegalArgumentException.class);
        Utils.runAndCheckException(() ->
                        extExp.extExp(RAW_DATA.getFirst(), KEYS.getFirst(), RAW_DATA.getFirst(), -1),
                IllegalArgumentException.class);
        Utils.runAndCheckException(() ->
                        extExp.extExp(KEYS.getFirst(), RAW_DATA.getFirst(), RAW_DATA.getFirst(), -1),
                IllegalArgumentException.class);
    }

    @FunctionalInterface
    private interface Kdf<A, P, S> {
        void test(A a, P p, S s)
                throws NoSuchAlgorithmException,
                NoSuchProviderException,
                InvalidAlgorithmParameterException;
    }

    @FunctionalInterface
    private interface Extract<K, S> {
        HKDFParameterSpec extract(K k, S s);
    }

    @FunctionalInterface
    private interface Expand<P, I, L> {
        HKDFParameterSpec expand(P p, I i, L l);
    }

    @FunctionalInterface
    private interface ExtractThenExpand<K, S, I, L> {
        HKDFParameterSpec extExp(K k, S s, I i, L l);
    }

    @FunctionalInterface
    private interface Compare<HK, L, R, T, S, LN> {
        void compare(HK hk, L lh, R rh, T t, S s, LN l)
                throws InvalidParameterSpecException, InvalidAlgorithmParameterException, NoSuchAlgorithmException;
    }

    @FunctionalInterface
    private interface Derive<HK, L, R, A1, A2> {
        void derive(HK hk, L lh, R rh, A1 a1, A2 a2)
                throws InvalidParameterSpecException, InvalidAlgorithmParameterException, NoSuchAlgorithmException;
    }

    private static class KDFAlgorithmParameterSpec implements AlgorithmParameterSpec {
        public KDFAlgorithmParameterSpec() {
        }
    }
}