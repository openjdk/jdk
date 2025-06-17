/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8331008
 * @summary KDF API tests
 * @library /test/lib
 * @run main/othervm -Djava.security.egd=file:/dev/urandom -Djava.security.debug=provider,engine=kdf HKDFExhaustiveTest
 */

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.KDF;
import javax.crypto.KDFParameters;
import javax.crypto.SecretKey;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

public class HKDFExhaustiveTest {

  private static final String JDK_HKDF_SHA256 = "HKDF-SHA256";
  private static final String JDK_HKDF_SHA384 = "HKDF-SHA384";
  private static final String JDK_HKDF_SHA512 = "HKDF-SHA512";
  private static final String[] KDF_ALGORITHMS = {
    JDK_HKDF_SHA256, JDK_HKDF_SHA384, JDK_HKDF_SHA512
  };
  private static final String SUNJCE = "SunJCE";

  // SECRET_KEY_SPEC_KEYS and RAW_DATA holds valid values for IKM and SALTS
  private static final List<SecretKey> SECRET_KEY_SPEC_KEYS =
      List.of(
          new SecretKeySpec(new byte[] {0}, "HKDF-IKM"),
          new SecretKeySpec("IKM".getBytes(), "HKDF-IKM"));
  private static final List<byte[]> RAW_DATA = List.of(new byte[] {0}, "RAW".getBytes());

  private static final byte[] EMPTY = new byte[0];
  private static final int SHORT_LENGTH = 42;
  private static final int LARGE_LENGTH = 1000;
  private static final int NEGATIVE_LENGTH = -1;

  static class TestKDFParams implements KDFParameters {}

  private static final KdfVerifier<String, String, AlgorithmParameterSpec> KdfGetInstanceVerifier =
      (a, p, s) -> {

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

  private static final KdfExtractVerifier<Object, Object> KdfExtractVerifierImpl =
      (ikm, salt) -> {
        // ofExtract
        HKDFParameterSpec.Builder hkdfParameterSpecBuilder = HKDFParameterSpec.ofExtract();
        addIkmAndSalt(hkdfParameterSpecBuilder, ikm, salt);

        // extractOnly - it is possible to have empty key param so skip when length is 0
        HKDFParameterSpec.Extract parameterSpec = hkdfParameterSpecBuilder.extractOnly();
        checkIKMSaltPresence(ikm, salt, parameterSpec);

        return parameterSpec;
      };

  private static final KdfExpandVerifier<SecretKey, byte[], Integer> KdfExpandVerifierImpl =
      (prk, info, len) -> {
        // Expand
        HKDFParameterSpec.Expand parameterSpec = HKDFParameterSpec.expandOnly(prk, info, len);

        Asserts.assertEqualsByteArray(prk.getEncoded(), parameterSpec.prk().getEncoded());
        Asserts.assertEqualsByteArray(info, parameterSpec.info());
        Asserts.assertEquals(len, parameterSpec.length());

        return parameterSpec;
      };

  private static final KdfExtThenExpVerifier<Object, Object, byte[], Integer>
      KdfExtThenExpVerifierImpl =
          (ikm, salt, info, len) -> {
            // ofExtract
            HKDFParameterSpec.Builder hkdfParameterSpecBuilder = HKDFParameterSpec.ofExtract();
            addIkmAndSalt(hkdfParameterSpecBuilder, ikm, salt);

            // thenExpand
            HKDFParameterSpec.ExtractThenExpand parameterSpec =
                hkdfParameterSpecBuilder.thenExpand(info, len);
            checkIKMSaltPresence(ikm, salt, parameterSpec);

            // Validate info and length
            Asserts.assertEqualsByteArray(info, parameterSpec.info());
            Asserts.assertEquals(len, parameterSpec.length());

            return parameterSpec;
          };
  private static final DeriveComparator<
          KDF, HKDFParameterSpec, HKDFParameterSpec, String, SecretKey, Integer>
      deriveComparatorImpl =
          (hk, lhs, rhs, t, s, len) -> {
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
              Asserts.assertEqualsByteArray(skUsingLhs, s.getEncoded());
            }
          };
  // Passed in HKDFParameterSpec returned from different methods and algorithms a1, a2.
  // Keys and data derived should be equal.
  private static final DeriveVerifier<KDF, HKDFParameterSpec, HKDFParameterSpec, String, String>
      deriveVerifierImpl =
          (hk, lhs, rhs, a1, a2) -> {
            SecretKey sk1 = hk.deriveKey(a1, lhs);
            SecretKey sk2 = hk.deriveKey(a2, rhs);
            Asserts.assertEqualsByteArray(sk1.getEncoded(), sk2.getEncoded());

            byte[] bk1 = hk.deriveData(lhs);
            Asserts.assertEqualsByteArray(bk1, sk1.getEncoded());
          };

  private static void checkIKMSaltPresence(
      Object ikm, Object salt, HKDFParameterSpec parameterSpec) {
    final List<SecretKey> ikms;
    final List<SecretKey> salts;
    if (parameterSpec instanceof HKDFParameterSpec.Extract) {
      ikms = ((HKDFParameterSpec.Extract) parameterSpec).ikms();
      salts = ((HKDFParameterSpec.Extract) parameterSpec).salts();
    } else { // must be HKDFParameterSpec.ExtractThenExpand
      ikms = ((HKDFParameterSpec.ExtractThenExpand) parameterSpec).ikms();
      salts = ((HKDFParameterSpec.ExtractThenExpand) parameterSpec).salts();
    }
    if ((ikm instanceof SecretKey) || ((byte[]) ikm).length != 0) {
      Asserts.assertTrue(ikms.contains(getSecretKey(ikm)));
    }

    if ((salt instanceof SecretKey) || ((byte[]) salt).length != 0) {
      Asserts.assertTrue(salts.contains(getSecretKey(salt)));
    }
  }

  private static SecretKey getSecretKey(Object data) {
    return (data instanceof SecretKey)
        ? (SecretKey) data
        : new SecretKeySpec((byte[]) data, "Generic");
  }

  private static void addIkmAndSalt(
      HKDFParameterSpec.Builder hkdfParameterSpecBuilder, Object ikm, Object salt) {
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
  }

  public static void main(String[] args) throws Exception {
    System.out.println("Starting Test '" + HKDFExhaustiveTest.class.getName() + "'");

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

  private static void testGetInstanceMethods()
      throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
    // POSITIVE TestCase: KDF getInstance methods test
    for (String algo : KDF_ALGORITHMS) {
      KdfGetInstanceVerifier.test(algo, SUNJCE, null);
    }
  }

  private static void testGetInstanceNegative() {
    final String INVALID_STRING = "INVALID";
    final Provider SUNJCE_PROVIDER = Security.getProvider(SUNJCE);

    // getInstance(String algorithm)
    Utils.runAndCheckException(() -> KDF.getInstance(null), NullPointerException.class);
    Utils.runAndCheckException(
        () -> KDF.getInstance(INVALID_STRING), NoSuchAlgorithmException.class);

    // getInstance(String algorithm, String provider)
    Utils.runAndCheckException(() -> KDF.getInstance(null, SUNJCE), NullPointerException.class);
    Utils.runAndCheckException(
        () -> KDF.getInstance(INVALID_STRING, SUNJCE), NoSuchAlgorithmException.class);
    Utils.runAndCheckException(
        () -> KDF.getInstance(KDF_ALGORITHMS[0], (String) null), NullPointerException.class);
    Utils.runAndCheckException(
        () -> KDF.getInstance(KDF_ALGORITHMS[0], INVALID_STRING), NoSuchProviderException.class);

    // getInstance(String algorithm, Provider provider)
    Utils.runAndCheckException(
        () -> KDF.getInstance(null, SUNJCE_PROVIDER), NullPointerException.class);
    Utils.runAndCheckException(
        () -> KDF.getInstance(INVALID_STRING, SUNJCE_PROVIDER), NoSuchAlgorithmException.class);
    Utils.runAndCheckException(
        () -> KDF.getInstance(KDF_ALGORITHMS[0], (Provider) null), NullPointerException.class);

    // getInstance(String algorithm, KDFParameters kdfParameters)
    // null spec is a valid case but different class is not
    Utils.runAndCheckException(
        () -> KDF.getInstance(null, (KDFParameters) null), NullPointerException.class);
    Utils.runAndCheckException(
        () -> KDF.getInstance(INVALID_STRING, (KDFParameters) null),
        NoSuchAlgorithmException.class);

    // getInstance(String algorithm, KDFParameters kdfParameters, String provider)
    Utils.runAndCheckException(
        () -> KDF.getInstance(null, null, SUNJCE), NullPointerException.class);
    Utils.runAndCheckException(
        () -> KDF.getInstance(INVALID_STRING, null, SUNJCE), NoSuchAlgorithmException.class);
    Utils.runAndCheckException(
        () -> KDF.getInstance(KDF_ALGORITHMS[0], null, (String) null), NullPointerException.class);
    Utils.runAndCheckException(
        () -> KDF.getInstance(KDF_ALGORITHMS[0], null, INVALID_STRING),
        NoSuchProviderException.class);
    Utils.runAndCheckException(
        () -> KDF.getInstance(KDF_ALGORITHMS[0], new TestKDFParams(), SUNJCE),
        InvalidAlgorithmParameterException.class);
    Utils.runAndCheckException(
        () -> KDF.getInstance(KDF_ALGORITHMS[0], new TestKDFParams(), SUNJCE_PROVIDER),
        InvalidAlgorithmParameterException.class);

    // getInstance(String algorithm, KDFParameters kdfParameters, Provider provider)
    Utils.runAndCheckException(
        () -> KDF.getInstance(null, null, SUNJCE_PROVIDER), NullPointerException.class);
    Utils.runAndCheckException(
        () -> KDF.getInstance(INVALID_STRING, null, SUNJCE_PROVIDER),
        NoSuchAlgorithmException.class);
    Utils.runAndCheckException(
        () -> KDF.getInstance(KDF_ALGORITHMS[0], null, (Provider) null),
        NullPointerException.class);
  }

  private static void testExtractMethod(KDF hk)
      throws InvalidAlgorithmParameterException,
          InvalidParameterSpecException,
          NoSuchAlgorithmException {
    List<Object> ikmSaltTestData = new ArrayList<>();
    ikmSaltTestData.add(null);
    ikmSaltTestData.add(EMPTY);
    ikmSaltTestData.add(RAW_DATA.getFirst());
    ikmSaltTestData.add(SECRET_KEY_SPEC_KEYS.getFirst());

    for (Object ikm : ikmSaltTestData) {
      for (Object salt : ikmSaltTestData) {
        // NEGATIVE Testcase: expects NullPointerException
        if (ikm == null || salt == null) {
          Utils.runAndCheckException(
              () -> KdfExtractVerifierImpl.extract(ikm, salt), NullPointerException.class);
        } else {
          // POSITIVE Testcase: Extract - Empty bytes for IKM/SALT
          HKDFParameterSpec ext1 = KdfExtractVerifierImpl.extract(ikm, salt);
          HKDFParameterSpec ext2 = KdfExtractVerifierImpl.extract(ikm, salt);
          deriveComparatorImpl.deriveAndCompare(hk, ext1, ext2, "PRK", null, NEGATIVE_LENGTH);
        }
      }
    }
  }

  private static void testDeriveKeyDataWithExtract(KDF hk)
      throws InvalidAlgorithmParameterException,
          InvalidParameterSpecException,
          NoSuchAlgorithmException {
    // POSITIVE TestCase: Extract - Derive keys/data with unknown algorithm name
    deriveVerifierImpl.derive(
        hk,
        KdfExtractVerifierImpl.extract(SECRET_KEY_SPEC_KEYS.getFirst(), RAW_DATA.getFirst()),
        KdfExtractVerifierImpl.extract(SECRET_KEY_SPEC_KEYS.getFirst(), RAW_DATA.getFirst()),
        "XYZ",
        "ABC");

    // NEGATIVE TestCase: Extract - {null, ""} algo to derive key
    Utils.runAndCheckException(
        () ->
            hk.deriveKey(
                null,
                KdfExtractVerifierImpl.extract(
                    SECRET_KEY_SPEC_KEYS.getFirst(), RAW_DATA.getFirst())),
        NullPointerException.class);
    Utils.runAndCheckException(
        () ->
            hk.deriveKey(
                "",
                KdfExtractVerifierImpl.extract(
                    SECRET_KEY_SPEC_KEYS.getFirst(), RAW_DATA.getFirst())),
        NoSuchAlgorithmException.class);
  }

  private static void testExpandMethod(KDF hk)
      throws InvalidAlgorithmParameterException,
          InvalidParameterSpecException,
          NoSuchAlgorithmException {
    SecretKey prk =
        hk.deriveKey(
            "PRK",
            KdfExtractVerifierImpl.extract(SECRET_KEY_SPEC_KEYS.get(1), RAW_DATA.getFirst()));

    // Test extExp with {null, EMPTY} info and {SHORT_LENGTH, LARGE_LENGTH} length
    for (byte[] info : new byte[][] {null, EMPTY}) {
      for (int length : new Integer[] {SHORT_LENGTH, LARGE_LENGTH}) {
        HKDFParameterSpec exp1 = KdfExpandVerifierImpl.expand(prk, info, length);
        HKDFParameterSpec exp2 = KdfExpandVerifierImpl.expand(prk, info, length);
        deriveComparatorImpl.deriveAndCompare(hk, exp1, exp2, "OKM", null, length);
      }
    }

    // NEGATIVE TestCase: Expand - PRK=null
    Utils.runAndCheckException(
        () -> KdfExpandVerifierImpl.expand(null, RAW_DATA.getFirst(), SHORT_LENGTH),
        NullPointerException.class);

    // NEGATIVE TestCase: Expand - Derive keys/data of negative length
    Utils.runAndCheckException(
        () ->
            KdfExpandVerifierImpl.expand(
                SECRET_KEY_SPEC_KEYS.getFirst(), RAW_DATA.getFirst(), NEGATIVE_LENGTH),
        IllegalArgumentException.class);

    // NEGATIVE TestCase: Expand - PRK value too short
    Utils.runAndCheckException(
        () ->
            hk.deriveKey(
                "OKM",
                KdfExpandVerifierImpl.expand(
                    new SecretKeySpec(new byte[] {0x00}, "PRK"), null, 32)),
        InvalidAlgorithmParameterException.class);

    // NEGATIVE TestCase: Expand - length greater than 255 > hmacLen
    Utils.runAndCheckException(
        () -> hk.deriveKey("OKM", KdfExpandVerifierImpl.expand(prk, null, 8162)),
        InvalidAlgorithmParameterException.class);
  }

  private static void testDeriveKeyDataWithExpand(KDF hk)
      throws InvalidAlgorithmParameterException,
          NoSuchAlgorithmException,
          InvalidParameterSpecException {
    SecretKey prk =
        hk.deriveKey(
            "PRK",
            KdfExtractVerifierImpl.extract(SECRET_KEY_SPEC_KEYS.get(1), RAW_DATA.getFirst()));

    // POSITIVE TestCase: Expand - Derive keys/data with unknown algorithm name
    deriveVerifierImpl.derive(
        hk,
        KdfExpandVerifierImpl.expand(prk, RAW_DATA.getFirst(), SHORT_LENGTH),
        KdfExpandVerifierImpl.expand(prk, RAW_DATA.getFirst(), SHORT_LENGTH),
        "XYZ",
        "ABC");

    // NEGATIVE TestCase: Expand - PRK is not derived
    Utils.runAndCheckException(
        () ->
            hk.deriveKey(
                "PRK",
                KdfExpandVerifierImpl.expand(
                    SECRET_KEY_SPEC_KEYS.get(1), RAW_DATA.getFirst(), SHORT_LENGTH)),
        InvalidAlgorithmParameterException.class);
  }

  private static void testExtractExpandMethod(KDF hk)
      throws InvalidAlgorithmParameterException,
          InvalidParameterSpecException,
          NoSuchAlgorithmException {
    // Test extExp with {null, EMPTY} info and {SHORT_LENGTH, LARGE_LENGTH} length
    for (byte[] info : new byte[][] {null, EMPTY}) {
      for (int length : new Integer[] {SHORT_LENGTH, LARGE_LENGTH}) {
        HKDFParameterSpec extractExpand1 =
            KdfExtThenExpVerifierImpl.extExp(
                RAW_DATA.getFirst(), RAW_DATA.getFirst(), info, length);
        HKDFParameterSpec extractExpand2 =
            KdfExtThenExpVerifierImpl.extExp(
                RAW_DATA.getFirst(), RAW_DATA.getFirst(), info, length);
        deriveComparatorImpl.deriveAndCompare(
            hk, extractExpand1, extractExpand2, "OKM", null, length);
      }
    }

    // NEGATIVE TestCases: ExtractExpand
    List<Object> ikmSaltTestData = new ArrayList<>();
    ikmSaltTestData.add(null);
    ikmSaltTestData.add(RAW_DATA.getFirst());
    ikmSaltTestData.add(SECRET_KEY_SPEC_KEYS.getFirst());

    for (Object ikm : ikmSaltTestData) {
      for (Object salt : ikmSaltTestData) {
        if (ikm == null || salt == null) {
          // ikm and/or salt are null, expect NullPointerException
          Utils.runAndCheckException(
              () -> KdfExtThenExpVerifierImpl.extExp(ikm, salt, RAW_DATA.getFirst(), SHORT_LENGTH),
              NullPointerException.class);
        } else {
          // ikm and salt are not null, test with negative length
          Utils.runAndCheckException(
              () ->
                  KdfExtThenExpVerifierImpl.extExp(ikm, salt, RAW_DATA.getFirst(), NEGATIVE_LENGTH),
              IllegalArgumentException.class);
        }
      }
    }

    // NEGATIVE TestCase: ExtractThenExpand - length greater than 255 > hmacLen
    Utils.runAndCheckException(
        () -> hk.deriveKey("OKM", HKDFParameterSpec.ofExtract().thenExpand(null, 8162)),
        InvalidAlgorithmParameterException.class);
  }

  private static void testDeriveKeyDataWithExtExpand(KDF hk)
      throws InvalidAlgorithmParameterException,
          InvalidParameterSpecException,
          NoSuchAlgorithmException {
    // POSITIVE TestCase: ExtractExpand - Derive keys/data with unknown algorithm names
    deriveVerifierImpl.derive(
        hk,
        KdfExtThenExpVerifierImpl.extExp(
            SECRET_KEY_SPEC_KEYS.getFirst(),
            RAW_DATA.getFirst(),
            RAW_DATA.getFirst(),
            SHORT_LENGTH),
        KdfExtThenExpVerifierImpl.extExp(
            SECRET_KEY_SPEC_KEYS.getFirst(),
            RAW_DATA.getFirst(),
            RAW_DATA.getFirst(),
            SHORT_LENGTH),
        "XYZ",
        "ABC");
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
        throws InvalidParameterSpecException,
            InvalidAlgorithmParameterException,
            NoSuchAlgorithmException;
  }

  @FunctionalInterface
  private interface DeriveVerifier<HK, L, R, A1, A2> {
    void derive(HK hk, L lh, R rh, A1 a1, A2 a2)
        throws InvalidParameterSpecException,
            InvalidAlgorithmParameterException,
            NoSuchAlgorithmException;
  }

  private static class KDFAlgorithmParameterSpec implements AlgorithmParameterSpec {
    public KDFAlgorithmParameterSpec() {}
  }
}
