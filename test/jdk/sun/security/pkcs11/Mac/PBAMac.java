/*
 * Copyright (c) 2023, 2025, Red Hat, Inc.
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
 * @bug 8301553 8348732
 * @summary test password based authentication on SunPKCS11's Mac service
 * @library /test/lib ..
 * @run main/othervm/timeout=30 PBAMac
 */

public final class PBAMac extends PKCS11Test {
    private static final char[] password = "123456\uA4F7".toCharArray();
    private static final byte[] salt = "abcdefgh".getBytes(
            StandardCharsets.UTF_8);
    private static final int iterations = 1000;
    private static final String plainText = "This is a known plain text!";
    private static final String sep = "======================================" +
            "===================================";

    private enum Configuration {
        // Pass salt and iterations to a Mac through a PBEParameterSpec.
        PBEParameterSpec,

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
                    "8611414ddb1875d9f576282199ab492a802b7d49"),
            macAssertionData("HmacPBESHA224", "HmacSHA224",
                    "cebb12b48eb90c07336c695f771d1d0ef4ccf5b9524fc0ab6fb9813a"),
            macAssertionData("HmacPBESHA256", "HmacSHA256",
                    "d83a6a4e8b0e1ec939d05790f385dd774bd2b7c17cfa2dd004efc894" +
                    "e5d53f51"),
            macAssertionData("HmacPBESHA384", "HmacSHA384",
                    "ae6b69cf9edfd9cd8c3b51cdf2b0243502f35a3e6007f33b1ab73568" +
                    "2ea81ea562f4383bb9512ff70752367b7259b16f"),
            macAssertionData("HmacPBESHA512", "HmacSHA512",
                    "46f6d09b0e7e50a66fa559ea4c4e9737a9d9e258b94f0075230d0acb" +
                    "40f2c926f96a152c4f6b03b631efc7f99c84f052f1c78d79e07f2a9e" +
                    "4a96164f5b46e70b"),
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

    private static void testWith(Provider p, AssertionData data,
            boolean testPBEService, Configuration conf) throws Exception {
        String svcAlgo = testPBEService ? data.pbeHmacAlgo : data.hmacAlgo;
        System.out.println(sep + System.lineSeparator() + svcAlgo
                + " (with " + conf.name() + ")");

        BigInteger mac = computeMac(p, svcAlgo, data.pbeHmacAlgo, conf);
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
            case AnonymousPBEKey -> {
                SecretKey key = getAnonymousPBEKey(keyAlgo);
                mac.init(key);
            }
            default -> throw new RuntimeException("Unsupported configuration");
        }

        return new BigInteger(1, mac.doFinal(
                plainText.getBytes(StandardCharsets.UTF_8)));
    }

    private static SecretKey getPasswordOnlyPBEKey()
            throws GeneralSecurityException {
        return SecretKeyFactory.getInstance("PBE")
                .generateSecret(new PBEKeySpec(password));
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
