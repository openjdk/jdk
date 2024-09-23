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
import java.util.List;

import javax.crypto.KDF;
import javax.crypto.KDFParameters;
import javax.crypto.spec.HKDFParameterSpec;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

public class HKDFTest {

    private static final String JDK_HKDF_SHA256 = "HKDF-SHA256";
    private static final String JDK_HKDF_SHA384 = "HKDF-SHA384";
    private static final String JDK_HKDF_SHA512 = "HKDF-SHA512";
    private static final String[] KDF_ALGORITHMS = {
            JDK_HKDF_SHA256,
            JDK_HKDF_SHA384,
            JDK_HKDF_SHA512
    };
    private static final String SUNJCE = "SunJCE";

    // SECRET_KEY_SPEC_KEYS and RAW_DATA holds valid values for IKM and SALTS
    private static final List<SecretKey> SECRET_KEY_SPEC_KEYS = List.of(
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

    private final static KdfVerifier<String, String, AlgorithmParameterSpec> KdfGetInstanceVerifier = (a, p, s) -> {

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

    private final static KdfExtractVerifier<Object, Object> KdfExtractVerifierImpl = (ikm, salt) -> {
        // Extract
        HKDFParameterSpec.Builder hkdfParameterSpecBuilder = HKDFParameterSpec.ofExtract();

        if (ikm instanceof SecretKey) {
            hkdfParameterSpecBuilder.addIKM((SecretKey) ikm);
        } else {
            hkdfParameterSpecBuilder.addIKM((byte[]) ikm);
        }

        if (salt instanceof SecretKey) {
            hkdfParameterSpecBuilder.addSalt((SecretKey) salt);
        } else {
            hkdfParameterSpecBuilder.addSalt((byte[]) salt);
        }

        // Extract
        HKDFParameterSpec.Extract param = hkdfParameterSpecBuilder.extractOnly();

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

    private final static KdfExpandVerifier<SecretKey, byte[], Integer>
            KdfExpandVerifierImpl = (prk, info, len) -> {
        // Expand
        HKDFParameterSpec.Expand parameterSpec = HKDFParameterSpec.expandOnly(prk, info, len);

        Asserts.assertEqualsByteArray(prk.getEncoded(), parameterSpec.prk().getEncoded());
        Asserts.assertEqualsByteArray(info, parameterSpec.info());
        Asserts.assertEquals(len, parameterSpec.length());

        return parameterSpec;
    };

    private final static KdfExtThenExpVerifier<Object, Object, byte[], Integer> KdfExtThenExpVerifierImpl = (ikm, salt, info, len) -> {
        // Extract
        HKDFParameterSpec.Builder hkdfParameterSpecBuilder = HKDFParameterSpec.ofExtract();

        if ((ikm instanceof SecretKey)) {
            hkdfParameterSpecBuilder.addIKM((SecretKey) ikm);
        } else {
            hkdfParameterSpecBuilder.addIKM((byte[]) ikm);
        }

        if (salt instanceof SecretKey) {
            hkdfParameterSpecBuilder.addSalt((SecretKey) salt);
        } else {
            hkdfParameterSpecBuilder.addSalt((byte[]) salt);
        }

        // Then Expand
        HKDFParameterSpec.ExtractThenExpand parameterSpec =
                hkdfParameterSpecBuilder.thenExpand(info, len);
        if ((ikm instanceof SecretKey) || ((byte[]) ikm).length != 0) {
            Asserts.assertTrue(parameterSpec.ikms().contains(
                    (ikm instanceof SecretKey)
                            ? ikm
                            : (new SecretKeySpec((byte[]) ikm, "Generic"))));
        }

        if ((salt instanceof SecretKey) || ((byte[]) salt).length != 0) {
            Asserts.assertTrue(parameterSpec.salts().contains(
                    (salt instanceof SecretKeySpec)
                            ? salt
                            : (new SecretKeySpec((byte[]) salt, "Generic"))));
        }

        // Validate info and length
        Asserts.assertEqualsByteArray(info, parameterSpec.info());
        Asserts.assertEquals(len, parameterSpec.length());

        return parameterSpec;
    };

    private final static DeriveComparator<KDF, HKDFParameterSpec, HKDFParameterSpec, String, SecretKey, Integer>
            deriveComparatorImpl = (hk, lhs, rhs, t, s, len) -> {
        // deriveKey using two passed in HKDFParameterSpec and compare
        byte[] skUsingLhs = hk.deriveKey(t, lhs).getEncoded();
        byte[] skUsingRhs = hk.deriveKey(t, rhs).getEncoded();

        // compare deriveData and keys using same HKDFParameterSpec are equal
        Asserts.assertEqualsByteArray(skUsingLhs, skUsingRhs);
        Asserts.assertEqualsByteArray(hk.deriveData(lhs), skUsingLhs);
        Asserts.assertEqualsByteArray(hk.deriveData(lhs), skUsingRhs);
        Asserts.assertEqualsByteArray(hk.deriveData(lhs), hk.deriveData(rhs));

        // if 'len < 0' then deriveKey()/deriveData() length check is not required
        if (len >= 0) {
            Asserts.assertEquals(skUsingLhs.length, len);
        }

        // Compare with if SecretKey is passed in parameter
        if (s != null) {
            Asserts.assertEqualsByteArray(
                    skUsingLhs, s.getEncoded());
        }
    };

    // Passed in HKDFParameterSpec returned from different methods and algorithms a1, a2.
    // Keys and data derived should be equal.
    private final static DeriveVerifier<KDF, HKDFParameterSpec, HKDFParameterSpec, String, String>
            deriveVerifierImpl = (hk, lhs, rhs, a1, a2) -> {
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
        // Uses SecretKey or byte[] for IKM and salt to cover addIKM and addSalt
        for (boolean useKey : new boolean[]{true, false}) {
            for (String algo : KDF_ALGORITHMS) {
                System.out.println("Testing with " + algo + " and useKey " + useKey);
                KDF hk = KDF.getInstance(algo);
                for (SecretKey secretKey : SECRET_KEY_SPEC_KEYS) {
                    for (byte[] bytes : RAW_DATA) {
                        var key = useKey ? secretKey : secretKey.getEncoded();
                        var raw = useKey
                                ? new SecretKeySpec(bytes, "Generic")
                                : bytes;
                        // extract
                        HKDFParameterSpec extract1 = KdfExtractVerifierImpl.extract(key, raw);
                        HKDFParameterSpec extract2 = KdfExtractVerifierImpl.extract(key, raw);
                        SecretKey sk = hk.deriveKey("PRK", extract1);
                        deriveComparatorImpl.deriveAndCompare(hk, extract1, extract2, "PRK", null, NEGATIVE_LENGTH);

                        // expand
                        HKDFParameterSpec expand1 = KdfExpandVerifierImpl.expand(sk,
                                ((raw instanceof SecretKey)
                                        ? ((SecretKey) raw).getEncoded()
                                        : (byte[]) raw),
                                SHORT_LENGTH);
                        HKDFParameterSpec expand2 = KdfExpandVerifierImpl.expand(sk,
                                ((raw instanceof SecretKey)
                                        ? ((SecretKey) raw).getEncoded()
                                        : (byte[]) raw), SHORT_LENGTH);
                        sk = hk.deriveKey("OKM", expand1);
                        deriveComparatorImpl.deriveAndCompare(hk, expand1, expand2, "OKM", sk, SHORT_LENGTH);

                        // extractExpand
                        HKDFParameterSpec extractExpand1 = KdfExtThenExpVerifierImpl.extExp(key, raw,
                                ((raw instanceof SecretKey)
                                        ? ((SecretKey) raw).getEncoded()
                                        : (byte[]) raw),
                                SHORT_LENGTH);
                        HKDFParameterSpec extractExpand2 = KdfExtThenExpVerifierImpl.extExp(key, raw,
                                ((raw instanceof SecretKey)
                                        ? ((SecretKey) raw).getEncoded()
                                        : (byte[]) raw),
                                SHORT_LENGTH);
                        deriveComparatorImpl.deriveAndCompare(hk, extractExpand1, extractExpand2, "OKM", sk, SHORT_LENGTH);
                    }
                }
            }
        }

        // Test KDF.getInstance methods
        System.out.println("Testing getInstance methods");
        testGetInstanceMethods();
        testGetInstanceNegative();

        /* Executing following test cases with one supported algorithm is sufficient */
        KDF hk = KDF.getInstance(KDF_ALGORITHMS[0]);

        // Test extract
        System.out.println("Testing extract method");
        testExtractMethod(hk);

        System.out.println("Testing deriveKey and deriveData with extract method");
        testDeriveKeyDataWithExtract(hk);

        // Test expand
        System.out.println("Testing expand method");
        testExpandMethod(hk);

        System.out.println("Testing deriveKey and deriveData with expand method");
        testDeriveKeyDataWithExpand(hk);

        // Test ExtractThenExpand
        System.out.println("Testing extractThenExpand method");
        testExtractExpandMethod(hk);

        System.out.println("Testing deriveKey and deriveData with extExpand method");
        testDeriveKeyDataWithExtExpand(hk);

        System.out.println("Test executed successfully.");
    }

    private static void testGetInstanceMethods() throws InvalidAlgorithmParameterException
            , NoSuchAlgorithmException, NoSuchProviderException {
        // POSITIVE TestCase: KDF getInstance methods test
        for (String algo : KDF_ALGORITHMS) {
            KdfGetInstanceVerifier.test(algo, SUNJCE, null);
        }
    }

    private static void testGetInstanceNegative() {
        final String INVALID_STRING = "INVALID";
        final Provider SUNJCE_PROVIDER = Security.getProvider(SUNJCE);

        // getInstance(String algorithm)
        Utils.runAndCheckException(() -> KDF.getInstance(null),
                NullPointerException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(INVALID_STRING),
                NoSuchAlgorithmException.class);

        // getInstance(String algorithm, String provider)
        Utils.runAndCheckException(() -> KDF.getInstance(null, SUNJCE),
                NullPointerException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(INVALID_STRING, SUNJCE),
                NoSuchAlgorithmException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(KDF_ALGORITHMS[0], (String) null),
                NullPointerException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(KDF_ALGORITHMS[0], INVALID_STRING),
                NoSuchProviderException.class);

        // getInstance(String algorithm, Provider provider)
        Utils.runAndCheckException(() -> KDF.getInstance(null, SUNJCE_PROVIDER),
                NullPointerException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(INVALID_STRING, SUNJCE_PROVIDER),
                NoSuchAlgorithmException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(KDF_ALGORITHMS[0], (Provider) null),
                NullPointerException.class);

        // getInstance(String algorithm, KDFParameters kdfParameters)
        // null spec is a valid case but different class is not
        Utils.runAndCheckException(() -> KDF.getInstance(null, (KDFParameters) null),
                NullPointerException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(INVALID_STRING, (KDFParameters) null),
                NoSuchAlgorithmException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(KDF_ALGORITHMS[0],
                        (KDFParameters) new KDFAlgorithmParameterSpec()),
                ClassCastException.class);

        // getInstance(String algorithm, KDFParameters kdfParameters, String provider)
        Utils.runAndCheckException(() -> KDF.getInstance(null, null, SUNJCE),
                NullPointerException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(INVALID_STRING, null, SUNJCE),
                NoSuchAlgorithmException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(KDF_ALGORITHMS[0],
                        (KDFParameters) new KDFAlgorithmParameterSpec(), SUNJCE),
                ClassCastException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(KDF_ALGORITHMS[0],
                        null, (String) null),
                NullPointerException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(KDF_ALGORITHMS[0],
                        null, INVALID_STRING),
                NoSuchProviderException.class);

        // getInstance(String algorithm, KDFParameters kdfParameters, Provider provider)
        Utils.runAndCheckException(() -> KDF.getInstance(null, null, SUNJCE_PROVIDER),
                NullPointerException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(INVALID_STRING, null, SUNJCE_PROVIDER),
                NoSuchAlgorithmException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(KDF_ALGORITHMS[0],
                        (KDFParameters) new KDFAlgorithmParameterSpec(), SUNJCE_PROVIDER),
                ClassCastException.class);
        Utils.runAndCheckException(() -> KDF.getInstance(KDF_ALGORITHMS[0], null, (Provider) null),
                NullPointerException.class);
    }

    private static void testExtractMethod(KDF hk) throws InvalidAlgorithmParameterException
            , InvalidParameterSpecException, NoSuchAlgorithmException {
        // POSITIVE TestCase: Extract - Empty bytes for IKM/SALT
        HKDFParameterSpec ext1 = KdfExtractVerifierImpl.extract(EMPTY, RAW_DATA.getFirst());
        HKDFParameterSpec ext2 = KdfExtractVerifierImpl.extract(EMPTY, RAW_DATA.getFirst());
        deriveComparatorImpl.deriveAndCompare(hk, ext1, ext2, "PRK", null, NEGATIVE_LENGTH);

        ext1 = KdfExtractVerifierImpl.extract(RAW_DATA.getFirst(), EMPTY);
        ext2 = KdfExtractVerifierImpl.extract(RAW_DATA.getFirst(), EMPTY);
        deriveComparatorImpl.deriveAndCompare(hk, ext1, ext2, "PRK", null, NEGATIVE_LENGTH);

        ext1 = KdfExtractVerifierImpl.extract(EMPTY, SECRET_KEY_SPEC_KEYS.getFirst());
        ext2 = KdfExtractVerifierImpl.extract(EMPTY, SECRET_KEY_SPEC_KEYS.getFirst());
        deriveComparatorImpl.deriveAndCompare(hk, ext1, ext2, "PRK", null, NEGATIVE_LENGTH);

        ext1 = KdfExtractVerifierImpl.extract(SECRET_KEY_SPEC_KEYS.getFirst(), EMPTY);
        ext2 = KdfExtractVerifierImpl.extract(SECRET_KEY_SPEC_KEYS.getFirst(), EMPTY);
        deriveComparatorImpl.deriveAndCompare(hk, ext1, ext2, "PRK", null, NEGATIVE_LENGTH);

        ext1 = KdfExtractVerifierImpl.extract(EMPTY, EMPTY);
        ext2 = KdfExtractVerifierImpl.extract(EMPTY, EMPTY);
        deriveComparatorImpl.deriveAndCompare(hk, ext1, ext2, "PRK", null, NEGATIVE_LENGTH);

        // NEGATIVE TestCase: Extract - NULL IKM/SALT
        Utils.runAndCheckException(() -> KdfExtractVerifierImpl.extract(null, RAW_DATA.getFirst()),
                NullPointerException.class);
        Utils.runAndCheckException(() -> KdfExtractVerifierImpl.extract(RAW_DATA.getFirst(), null),
                NullPointerException.class);
        Utils.runAndCheckException(() -> KdfExtractVerifierImpl.extract(null, SECRET_KEY_SPEC_KEYS.getFirst()),
                NullPointerException.class);
        Utils.runAndCheckException(() -> KdfExtractVerifierImpl.extract(SECRET_KEY_SPEC_KEYS.getFirst(), null),
                NullPointerException.class);
        Utils.runAndCheckException(() -> KdfExtractVerifierImpl.extract(null, null),
                NullPointerException.class);
    }

    private static void testDeriveKeyDataWithExtract(KDF hk) throws InvalidAlgorithmParameterException,
            InvalidParameterSpecException, NoSuchAlgorithmException {
        // POSITIVE TestCase: Extract - Derive keys/data with unknown algorithm name
        deriveVerifierImpl.derive(hk,
                KdfExtractVerifierImpl.extract(SECRET_KEY_SPEC_KEYS.getFirst(), RAW_DATA.getFirst()),
                KdfExtractVerifierImpl.extract(SECRET_KEY_SPEC_KEYS.getFirst(), RAW_DATA.getFirst()),
                "XYZ", "ABC");

        // NEGATIVE TestCase: Extract - NULL algo to derive key
        Utils.runAndCheckException(() -> hk.deriveKey(null,
                        KdfExtractVerifierImpl.extract(SECRET_KEY_SPEC_KEYS.getFirst(), RAW_DATA.getFirst())),
                NullPointerException.class);
        Utils.runAndCheckException(() -> hk.deriveKey("",
                        KdfExtractVerifierImpl.extract(SECRET_KEY_SPEC_KEYS.getFirst(), RAW_DATA.getFirst())),
                NoSuchAlgorithmException.class);
        Utils.runAndCheckException(() -> hk.deriveKey(null,
                        KdfExtractVerifierImpl.extract(RAW_DATA.getFirst(), SECRET_KEY_SPEC_KEYS.getFirst())),
                NullPointerException.class);
    }

    private static void testExpandMethod(KDF hk) throws InvalidAlgorithmParameterException
            , InvalidParameterSpecException, NoSuchAlgorithmException {
        SecretKey prk = hk.deriveKey("PRK",
                KdfExtractVerifierImpl.extract(SECRET_KEY_SPEC_KEYS.get(1), RAW_DATA.getFirst()));
        // POSITIVE TestCase: Expand - 'info' is null
        HKDFParameterSpec exp1 = KdfExpandVerifierImpl.expand(prk, null, SHORT_LENGTH);
        HKDFParameterSpec exp2 = KdfExpandVerifierImpl.expand(prk, null, SHORT_LENGTH);
        deriveComparatorImpl.deriveAndCompare(hk, exp1, exp2, "OKM", null, SHORT_LENGTH);
        exp1 = KdfExpandVerifierImpl.expand(prk, null, LARGE_LENGTH);
        exp2 = KdfExpandVerifierImpl.expand(prk, null, LARGE_LENGTH);
        deriveComparatorImpl.deriveAndCompare(hk, exp1, exp2, "OKM", null, LARGE_LENGTH);

        // POSITIVE TestCase: Expand parameter 'info' is empty byte
        exp1 = KdfExpandVerifierImpl.expand(prk, EMPTY, SHORT_LENGTH);
        exp2 = KdfExpandVerifierImpl.expand(prk, EMPTY, SHORT_LENGTH);
        deriveComparatorImpl.deriveAndCompare(hk, exp1, exp2, "OKM", null, SHORT_LENGTH);
        exp1 = KdfExpandVerifierImpl.expand(prk, EMPTY, LARGE_LENGTH);
        exp2 = KdfExpandVerifierImpl.expand(prk, EMPTY, LARGE_LENGTH);
        deriveComparatorImpl.deriveAndCompare(hk, exp1, exp2, "OKM", null, LARGE_LENGTH);

        // NEGATIVE TestCase: Expand - PRK=null
        Utils.runAndCheckException(() -> KdfExpandVerifierImpl.expand(null,
                RAW_DATA.getFirst(), SHORT_LENGTH), NullPointerException.class);

        // NEGATIVE TestCase: Expand - Derive keys/data of negative length
        Utils.runAndCheckException(() -> KdfExpandVerifierImpl.expand(SECRET_KEY_SPEC_KEYS.getFirst(),
                RAW_DATA.getFirst(), -1), IllegalArgumentException.class);
    }

    private static void testDeriveKeyDataWithExpand(KDF hk) throws InvalidAlgorithmParameterException,
            NoSuchAlgorithmException, InvalidParameterSpecException {
        SecretKey prk = hk.deriveKey("PRK",
                KdfExtractVerifierImpl.extract(SECRET_KEY_SPEC_KEYS.get(1), RAW_DATA.getFirst()));

        // POSITIVE TestCase: Expand - Derive keys/data with unknown algorithm name
        deriveVerifierImpl.derive(hk,
                KdfExpandVerifierImpl.expand(prk, RAW_DATA.getFirst(), SHORT_LENGTH),
                KdfExpandVerifierImpl.expand(prk, RAW_DATA.getFirst(), SHORT_LENGTH),
                "XYZ", "ABC");

        // NEGATIVE TestCase: Expand - PRK is not derived
        Utils.runAndCheckException(() -> hk.deriveKey("PRK",
                KdfExpandVerifierImpl.expand(SECRET_KEY_SPEC_KEYS.get(1),
                        RAW_DATA.getFirst(), SHORT_LENGTH)), InvalidAlgorithmParameterException.class);

    }

    private static void testExtractExpandMethod(KDF hk) throws InvalidAlgorithmParameterException
            , InvalidParameterSpecException, NoSuchAlgorithmException {
        // POSITIVE TestCase: ExtractExpand - 'info' is null
        HKDFParameterSpec exep1 = KdfExtThenExpVerifierImpl.extExp(RAW_DATA.getFirst(), RAW_DATA.getFirst(), null, SHORT_LENGTH);
        HKDFParameterSpec exep2 = KdfExtThenExpVerifierImpl.extExp(RAW_DATA.getFirst(), RAW_DATA.getFirst(), null, SHORT_LENGTH);
        deriveComparatorImpl.deriveAndCompare(hk, exep1, exep2, "OKM", null, SHORT_LENGTH);
        exep1 = KdfExtThenExpVerifierImpl.extExp(RAW_DATA.getFirst(), RAW_DATA.getFirst(), null, LARGE_LENGTH);
        exep2 = KdfExtThenExpVerifierImpl.extExp(RAW_DATA.getFirst(), RAW_DATA.getFirst(), null, LARGE_LENGTH);
        deriveComparatorImpl.deriveAndCompare(hk, exep1, exep2, "OKM", null, LARGE_LENGTH);

        // POSITIVE TestCase: ExtractExpand - 'info' is empty byte
        exep1 = KdfExtThenExpVerifierImpl.extExp(RAW_DATA.getFirst(), RAW_DATA.getFirst(), EMPTY, SHORT_LENGTH);
        exep2 = KdfExtThenExpVerifierImpl.extExp(RAW_DATA.getFirst(), RAW_DATA.getFirst(), EMPTY, SHORT_LENGTH);
        deriveComparatorImpl.deriveAndCompare(hk, exep1, exep2, "OKM", null, SHORT_LENGTH);
        exep1 = KdfExtThenExpVerifierImpl.extExp(RAW_DATA.getFirst(), RAW_DATA.getFirst(), EMPTY, LARGE_LENGTH);
        exep2 = KdfExtThenExpVerifierImpl.extExp(RAW_DATA.getFirst(), RAW_DATA.getFirst(), EMPTY, LARGE_LENGTH);
        deriveComparatorImpl.deriveAndCompare(hk, exep1, exep2, "OKM", null, LARGE_LENGTH);

        // NEGATIVE TestCase: ExtractExpand - NULL IKM/SALT
        Utils.runAndCheckException(() ->
                        KdfExtThenExpVerifierImpl.extExp(null, RAW_DATA.getFirst(), RAW_DATA.getFirst(), SHORT_LENGTH),
                NullPointerException.class);
        Utils.runAndCheckException(() ->
                        KdfExtThenExpVerifierImpl.extExp(null, SECRET_KEY_SPEC_KEYS.getFirst(), RAW_DATA.getFirst(), SHORT_LENGTH),
                NullPointerException.class);
        Utils.runAndCheckException(() ->
                        KdfExtThenExpVerifierImpl.extExp(RAW_DATA.getFirst(), null, RAW_DATA.getFirst(), SHORT_LENGTH),
                NullPointerException.class);
        Utils.runAndCheckException(() ->
                        KdfExtThenExpVerifierImpl.extExp(SECRET_KEY_SPEC_KEYS.getFirst(), null, RAW_DATA.getFirst(), SHORT_LENGTH),
                NullPointerException.class);

        // NEGATIVE: ExtractExpand Parameters - negative length
        Utils.runAndCheckException(() ->
                        KdfExtThenExpVerifierImpl.extExp(RAW_DATA.getFirst(), RAW_DATA.getFirst(), RAW_DATA.getFirst(), -1),
                IllegalArgumentException.class);
        Utils.runAndCheckException(() ->
                        KdfExtThenExpVerifierImpl.extExp(SECRET_KEY_SPEC_KEYS.getFirst(), SECRET_KEY_SPEC_KEYS.getFirst(), RAW_DATA.getFirst(), -1),
                IllegalArgumentException.class);
        Utils.runAndCheckException(() ->
                        KdfExtThenExpVerifierImpl.extExp(RAW_DATA.getFirst(), SECRET_KEY_SPEC_KEYS.getFirst(), RAW_DATA.getFirst(), -1),
                IllegalArgumentException.class);
        Utils.runAndCheckException(() ->
                        KdfExtThenExpVerifierImpl.extExp(SECRET_KEY_SPEC_KEYS.getFirst(), RAW_DATA.getFirst(), RAW_DATA.getFirst(), -1),
                IllegalArgumentException.class);
    }

    private static void testDeriveKeyDataWithExtExpand(KDF hk) throws InvalidAlgorithmParameterException,
            InvalidParameterSpecException, NoSuchAlgorithmException {
        // POSITIVE TestCase: ExtractExpand - Derive keys/data with unknown algorithm names
        deriveVerifierImpl.derive(hk,
                KdfExtThenExpVerifierImpl.extExp(SECRET_KEY_SPEC_KEYS.getFirst(),
                        RAW_DATA.getFirst(), RAW_DATA.getFirst(), SHORT_LENGTH),
                KdfExtThenExpVerifierImpl.extExp(SECRET_KEY_SPEC_KEYS.getFirst(),
                        RAW_DATA.getFirst(), RAW_DATA.getFirst(), SHORT_LENGTH),
                "XYZ", "ABC");
    }

    @FunctionalInterface
    private interface KdfVerifier<A, P, S> {
        void test(A a, P p, S s)
                throws NoSuchAlgorithmException,
                NoSuchProviderException,
                InvalidAlgorithmParameterException;
    }

    @FunctionalInterface
    private interface KdfExtractVerifier<K, S> {
        HKDFParameterSpec extract(K k, S s);
    }

    @FunctionalInterface
    private interface KdfExpandVerifier<P, I, L> {
        HKDFParameterSpec expand(P p, I i, L l);
    }

    @FunctionalInterface
    private interface KdfExtThenExpVerifier<K, S, I, L> {
        HKDFParameterSpec extExp(K k, S s, I i, L l);
    }

    @FunctionalInterface
    private interface DeriveComparator<HK, L, R, T, S, LN> {
        void deriveAndCompare(HK hk, L lh, R rh, T t, S s, LN l)
                throws InvalidParameterSpecException, InvalidAlgorithmParameterException, NoSuchAlgorithmException;
    }

    @FunctionalInterface
    private interface DeriveVerifier<HK, L, R, A1, A2> {
        void derive(HK hk, L lh, R rh, A1 a1, A2 a2)
                throws InvalidParameterSpecException, InvalidAlgorithmParameterException, NoSuchAlgorithmException;
    }

    private static class KDFAlgorithmParameterSpec implements AlgorithmParameterSpec {
        public KDFAlgorithmParameterSpec() {
        }
    }
}