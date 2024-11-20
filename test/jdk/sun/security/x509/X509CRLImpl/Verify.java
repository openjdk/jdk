/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7026347
 * @summary X509CRL should have verify(PublicKey key, Provider sigProvider)
 */

import java.io.ByteArrayInputStream;
import java.security.*;
import java.security.cert.*;

public class Verify {

    static String selfSignedCertStr =
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIDVzCCAj+gAwIBAgIUUM/RKxE2Rcc6zYLWLxNolpLnuiwwDQYJKoZIhvcNAQEL\n" +
        "BQAwOzENMAsGA1UEAwwEUk9PVDEQMA4GA1UECgwHRXhhbXBsZTELMAkGA1UECAwC\n" +
        "Q0ExCzAJBgNVBAYTAlVTMB4XDTI0MDYxOTA0NDc1N1oXDTM0MDYxOTA0NDc1N1ow\n" +
        "OzENMAsGA1UEAwwEUk9PVDEQMA4GA1UECgwHRXhhbXBsZTELMAkGA1UECAwCQ0Ex\n" +
        "CzAJBgNVBAYTAlVTMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAohRG\n" +
        "eq8/CniUqWEtpm1gBp+PWENpYgeaALAUgFdBBa6ao7mESjxRG8teaNRcszmoL3Rl\n" +
        "TH5hLycHA00G5qsALXo4Cj9wAGfR3LbA0HlTurdw3NNk76twQXZpuE19YNYQonbR\n" +
        "Mm2sgTd2YcrNWmGpthgNiUaT837Yt7RCuurPo4zi1y6g/NJwyLtn775S86NrV5PT\n" +
        "4vaBCsB5+eCm01CBgzBq3I0OY5oosopNUjmFL4LYccZZ2YAOUY0fvxfsMZD5EDcj\n" +
        "KrgKBspjmolfn5g5lA5vdVthG2/TxTIdLss69+NsGS1RBkSKGiQNKnRnAB9/gHwc\n" +
        "2ryHKJRMQrV+JGMjrQIDAQABo1MwUTAdBgNVHQ4EFgQUW6jZ+mcCEMAQTUzJH2F0\n" +
        "TwMTOMswHwYDVR0jBBgwFoAUW6jZ+mcCEMAQTUzJH2F0TwMTOMswDwYDVR0TAQH/\n" +
        "BAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAB8T/EfVh602S1GJD2tJ1ck9TwutF\n" +
        "2VSoSRKajMOabbwjzKEAeJ9rNcWiy60rSvDuL8i4IL52R7fHhlJaDg9FVjmkiWSO\n" +
        "VPiIZuOyvUtsc9++AM741RK9OrEMETvAtbtEMU6du7LiFk2KcnDTHfcNihtM/TNZ\n" +
        "1bzEKuSfQydBNPkO3Ftmveygj7QGX+Kgppp7RXXUFzySYxrlA1usgNhVXY/qhFiJ\n" +
        "jhTU33iZgwiKxpY+zj/Gmk5sdOCEk7e1P06IB3eIopdRTMGJCeCBKyFyXND38kNC\n" +
        "bTIPnuOdE73M2AW0LWuPv6UQZVBv5A82WMT9f8Hq9H2cHbuhgL/ozyFSWw==\n" +
        "-----END CERTIFICATE-----";

    static String crlIssuerCertStr =
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIDeTCCAmGgAwIBAgIBATANBgkqhkiG9w0BAQUFADA7MQ0wCwYDVQQDDARST09U\n" +
        "MRAwDgYDVQQKDAdFeGFtcGxlMQswCQYDVQQIDAJDQTELMAkGA1UEBhMCVVMwHhcN\n" +
        "MjQwNjE5MDQ0NzU3WhcNMjYwNjE4MDQ0NzU3WjA5MQswCQYDVQQDDAJDQTELMAkG\n" +
        "A1UECAwCQ0ExCzAJBgNVBAYTAlVTMRAwDgYDVQQKDAdFeGFtcGxlMIIBIjANBgkq\n" +
        "hkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAn3wVMxoa3mgqk7fbg+UEj3vDfdR+o0dL\n" +
        "UeDqtkM/KHQg2h16LTRsRM+bGcDAg8pz/8RNK+jiCq5lXylUtOYEIKzD2NTrycOH\n" +
        "gAt92vt01cusZrnvdf+wKFNzDQea1q1fgNFbFdWZZ7Ia+BvR9dYdwbyX7LPKPth5\n" +
        "aSmvwhKivETV6mTU17dMls/8OjQ+oUydBggVjhpjS+xYCBa09ie2dR+eGrluCaF5\n" +
        "gspoTeQxAOOytBoL4+DECEPsAyr7/guMOdmWUbPDvfYL+97N6imXUh4XtQ7+xHTd\n" +
        "OWWwAhS7JjqcalADSNUClU54VVGbZ9NmIjDiSPc1bvam4FxicuqrBQIDAQABo4GJ\n" +
        "MIGGMAwGA1UdEwQFMAMBAf8wHQYDVR0OBBYEFMPkRHT0w2v7Nx2SN/i+2hJIj/5x\n" +
        "MB8GA1UdIwQYMBaAFFuo2fpnAhDAEE1MyR9hdE8DEzjLMAsGA1UdDwQEAwIBAjAp\n" +
        "BgNVHR8EIjAgMB6gHKAahhhodHRwOi8vdGVzdC5jb20vcm9vdC5jcmwwDQYJKoZI\n" +
        "hvcNAQEFBQADggEBAIsREfhopvEGrbVjbaRsBmGlMAblqiTWF3DklU4BfXGQ7u+2\n" +
        "z/Dvl5rehGkWIU5GmBY/DFWN/Tgt6yJU+d1ismKj+zhWI8IT7dLKJnSP0Sei0zqr\n" +
        "qsIj/y5Xzmd2XpQ52V3KtDy4t7YQJ+nRKUrqLzSKHvOXOQgScK2RL4FZx0gah/bJ\n" +
        "YCKq6zonC59lZ6ftJ2j9Ny9wNulHBlgS0p8q+Z42IfdfVgrLmbXoHNmKjVKdrs1Z\n" +
        "HCva3WKMOkVFdejOuvPSnSw4Iob479nC3V12YtFAgeYMoBMPgZHcuWce4IC9Ts7z\n" +
        "w8Xo1Fv3aNOygWdXdVDL79jkOJo2wO8yIe+J6Ig=\n" +
        "-----END CERTIFICATE-----";

    static String crlStr =
        "-----BEGIN X509 CRL-----\n" +
        "MIIBtjCBnwIBATANBgkqhkiG9w0BAQUFADA5MQswCQYDVQQDDAJDQTELMAkGA1UE\n" +
        "CAwCQ0ExCzAJBgNVBAYTAlVTMRAwDgYDVQQKDAdFeGFtcGxlFw0yNDA2MTkwNDQ3\n" +
        "NThaFw0yNjA2MTgwNDQ3NThaMCIwIAIBAhcNMjQwNjE5MDQ0NzU4WjAMMAoGA1Ud\n" +
        "FQQDCgEEoA4wDDAKBgNVHRQEAwIBATANBgkqhkiG9w0BAQUFAAOCAQEAkN0owWtq\n" +
        "We0SznF9rAAADLMfB/2GKBQpqsJXXwE9FnCm8emSDtHpud+NZL+PAy9g050et8nl\n" +
        "CNey/FBMJJMN3b3SZKkHA2MR4qJmHfeFnlE5mHnUHg7gH0a1u7H7wf0Z/L6eZNWy\n" +
        "dB905II7Ej0GBuPnLsKNMDBtGtDuSPXCvmaBsKDe8awaEA1VchZKVLzg+8hEC0vt\n" +
        "60jz9HrDpFun99IKTTCxBT+9GrW38GbPMxj0rLAL4n75SrfPdeFPj0t5fksOC7a7\n" +
        "SLO9t+UC89SMTsoIwVjHIFIUxw5FHpuUfgOQ7PtjhpLd2Pm5u5Pe2gv4Q41xVgVW\n" +
        "hVMagRPmAQAniQ==\n" +
        "-----END X509 CRL-----";

    private static X509CRL crl;
    private static PublicKey selfSignedCertPubKey;
    private static PublicKey crlIssuerCertPubKey;

    public static void main(String[] args) throws Exception {
        setup();

        /*
         * Verify CRL with its own public key.
         * Should pass.
         */
        verifyCRL(crlIssuerCertPubKey,
                System.getProperty("test.provider.name", "SunRsaSign"));

        /*
         * Try to verify CRL with a provider that does not have a Signature
         * implementation.
         * Should fail with NoSuchAlgorithmException.
         */
        try {
            verifyCRL(crlIssuerCertPubKey, "SunJCE");
            throw new RuntimeException("Didn't catch the exception properly");
        } catch (NoSuchAlgorithmException e) {
            System.out.println("Caught the correct exception.");
        }

        /*
         * Try to verify CRL with a provider that has a Signature implementation
         * but not of the right algorithm (SHA1withRSA).
         * Should fail with NoSuchAlgorithmException.
         */
        try {
            verifyCRL(crlIssuerCertPubKey,
                System.getProperty("test.provider.name", "SUN"));
            throw new RuntimeException("Didn't catch the exception properly");
        } catch (NoSuchAlgorithmException e) {
            System.out.println("Caught the correct exception.");
        }

        /*
         * Try to verify CRL with the wrong public key.
         * Should fail with SignatureException.
         */
        try {
            verifyCRL(selfSignedCertPubKey,
                    System.getProperty("test.provider.name","SunRsaSign"));
            throw new RuntimeException("Didn't catch the exception properly");
        } catch (SignatureException e) {
            System.out.println("Caught the correct exception.");
        }
    }

    private static void setup() throws CertificateException, CRLException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        /* Create CRL */
        ByteArrayInputStream inputStream =
                new ByteArrayInputStream(crlStr.getBytes());
        crl = (X509CRL)cf.generateCRL(inputStream);

        /* Get public key of the CRL issuer cert */
        inputStream = new ByteArrayInputStream(crlIssuerCertStr.getBytes());
        X509Certificate cert
                = (X509Certificate)cf.generateCertificate(inputStream);
        crlIssuerCertPubKey = cert.getPublicKey();

        /* Get public key of the self-signed Cert */
        inputStream = new ByteArrayInputStream(selfSignedCertStr.getBytes());
        selfSignedCertPubKey = cf.generateCertificate(inputStream).getPublicKey();
    }

    private static void verifyCRL(PublicKey key, String providerName)
            throws CRLException, NoSuchAlgorithmException, InvalidKeyException,
            SignatureException {
        Provider provider = Security.getProvider(providerName);
        System.out.println("Provider = " + provider.getName());
        if (provider == null) {
            throw new RuntimeException("Provider " + providerName
                                                   + " not found.");
        }
        crl.verify(key, provider);
    }
}
