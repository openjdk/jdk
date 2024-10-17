/*
 * Copyright (c) 2023, Red Hat, Inc.
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

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.Security;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.interfaces.PBEKey;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

/*
 * @test
 * @bug 8301553
 * @summary test password based authentication on SunPKCS11's Mac service
 * @library /test/lib ..
 * @run main/othervm/timeout=30 PBAMac
 */

public final class PBAMac extends PKCS11Test {
    private static final char[] password = "123456".toCharArray();
    private static final byte[] salt = "abcdefgh".getBytes(
            StandardCharsets.UTF_8);
    private static final int iterations = 1000;
    private static final String plainText = "This is a known plain text!";
    private static final String sep = "======================================" +
            "===================================";

    private enum Configuration {
        // Pass salt and iterations to a Mac through a PBEParameterSpec.
        PBEParameterSpec,

        // Derive a key using SunPKCS11's SecretKeyFactory (wrapping password,
        // salt and iterations in a PBEKeySpec), and pass it to a Mac.
        SecretKeyFactoryDerivedKey,

        // Pass password, salt and iterations and iterations to
        // a Mac through an anonymous class implementing the
        // javax.crypto.interfaces.PBEKey interface.
        AnonymousPBEKey,
    }

    private static Provider sunJCE = Security.getProvider(
            System.getProperty("test.provider.name", "SunJCE"));

    private record AssertionData(String pbeHmacAlgo, String hmacAlgo,
            BigInteger expectedMac) {}

    private static AssertionData macAssertionData(String pbeHmacAlgo,
            String hmacAlgo, String staticExpectedMacString) {
        BigInteger staticExpectedMac = new BigInteger(staticExpectedMacString,
                16);
        BigInteger expectedMac = null;
        if (sunJCE != null) {
            try {
                expectedMac = computeMac(sunJCE, pbeHmacAlgo,
                        pbeHmacAlgo, Configuration.PBEParameterSpec);
                checkAssertionValues(expectedMac, staticExpectedMac);
            } catch (GeneralSecurityException e) {
                // Move to staticExpectedMac as it's unlikely
                // that any of the algorithms are available.
                sunJCE = null;
            }
        }
        if (expectedMac == null) {
            expectedMac = staticExpectedMac;
        }
        return new AssertionData(pbeHmacAlgo, hmacAlgo, expectedMac);
    }

    private static void checkAssertionValues(BigInteger expectedValue,
            BigInteger staticExpectedValue) {
        if (!expectedValue.equals(staticExpectedValue)) {
            printHex("SunJCE value", expectedValue);
            printHex("Static value", staticExpectedValue);
            throw new Error("Static and SunJCE values do not match.");
        }
    }

    // Generated with SunJCE.
    private static final AssertionData[] assertionData = new AssertionData[]{
            macAssertionData("HmacPBESHA1", "HmacSHA1",
                    "707606929395e4297adc63d520ac7d22f3f5fa66"),
            macAssertionData("HmacPBESHA224", "HmacSHA224",
                    "4ffb5ad4974a7a9fca5a36ebe3e34dd443c07fb68c392f8b611657e6"),
            macAssertionData("HmacPBESHA256", "HmacSHA256",
                    "9e8c102c212d2fd1334dc497acb4e002b04e84713b7eda5a63807af2" +
                    "989d3e50"),
            macAssertionData("HmacPBESHA384", "HmacSHA384",
                    "77f31a785d4f2220251143a4ba80f5610d9d0aeaebb4a278b8a7535c" +
                    "8cea8e8211809ba450458e351c5b66d691839c23"),
            macAssertionData("HmacPBESHA512", "HmacSHA512",
                    "a53f942a844b234a69c1f92cba20ef272c4394a3cf4024dc16d9dbac" +
                    "1969870b1c2b28b897149a1a3b9ad80a7ca8c547dfabf3ed5f144c6b" +
                    "593900b62e120c45"),
    };

    public void main(Provider sunPKCS11) throws Exception {
        System.out.println("SunPKCS11: " + sunPKCS11.getName());
        for (Configuration conf : Configuration.values()) {
            for (AssertionData data : assertionData) {
                testWith(sunPKCS11, data, true, conf);
                if (conf != Configuration.PBEParameterSpec) {
                    testWith(sunPKCS11, data, false, conf);
                }
            }
        }
        System.out.println("TEST PASS - OK");
    }

    private static void testWith(Provider sunPKCS11, AssertionData data,
            boolean testPBEService, Configuration conf) throws Exception {
        String svcAlgo = testPBEService ? data.pbeHmacAlgo : data.hmacAlgo;
        System.out.println(sep + System.lineSeparator() + svcAlgo
                + " (with " + conf.name() + ")");

        BigInteger mac = computeMac(sunPKCS11, svcAlgo, data.pbeHmacAlgo, conf);
        printHex("HMAC", mac);

        if (!mac.equals(data.expectedMac)) {
            printHex("Expected HMAC", data.expectedMac);
            throw new Exception("Expected HMAC did not match");
        }
    }

    private static BigInteger computeMac(Provider p, String svcAlgo,
            String keyAlgo, Configuration conf)
            throws GeneralSecurityException {
        Mac mac = Mac.getInstance(svcAlgo, p);
        switch (conf) {
            case PBEParameterSpec -> {
                SecretKey key = getPasswordOnlyPBEKey();
                mac.init(key, new PBEParameterSpec(salt, iterations));
            }
            case SecretKeyFactoryDerivedKey -> {
                SecretKey key = getDerivedSecretKey(p, keyAlgo);
                mac.init(key);
            }
            case AnonymousPBEKey -> {
                SecretKey key = getAnonymousPBEKey(keyAlgo);
                mac.init(key);
            }
        }
        return new BigInteger(1, mac.doFinal(
                plainText.getBytes(StandardCharsets.UTF_8)));
    }

    private static SecretKey getPasswordOnlyPBEKey()
            throws GeneralSecurityException {
        return SecretKeyFactory.getInstance("PBE")
                .generateSecret(new PBEKeySpec(password));
    }

    private static SecretKey getDerivedSecretKey(Provider sunPKCS11,
            String algorithm) throws GeneralSecurityException {
        return SecretKeyFactory.getInstance(algorithm, sunPKCS11)
                .generateSecret(new PBEKeySpec(password, salt, iterations));
    }

    private static SecretKey getAnonymousPBEKey(String algorithm) {
        return new PBEKey() {
            public byte[] getSalt() { return salt.clone(); }
            public int getIterationCount() { return iterations; }
            public String getAlgorithm() { return algorithm; }
            public String getFormat() { return "RAW"; }
            public char[] getPassword() { return password.clone(); }
            public byte[] getEncoded() { return null; }
        };
    }

    private static void printHex(String title, BigInteger b) {
        String repr = (b == null) ? "buffer is null" : b.toString(16);
        System.out.println(title + ": " + repr + System.lineSeparator());
    }

    public static void main(String[] args) throws Exception {
        main(new PBAMac());
    }
}
