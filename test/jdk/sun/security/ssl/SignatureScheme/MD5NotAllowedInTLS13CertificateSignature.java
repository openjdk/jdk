/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8350807
 * @summary Certificates using MD5 algorithm that are disabled by default are
 *          incorrectly allowed in TLSv1.3 when re-enabled.
 * @library /javax/net/ssl/templates
 *          /test/lib
 * @run main/othervm MD5NotAllowedInTLS13CertificateSignature
 */

import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertTrue;
import static jdk.test.lib.Utils.runAndCheckException;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.RSAPrivateKeySpec;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;

public class MD5NotAllowedInTLS13CertificateSignature extends
        SSLSocketTemplate {

    // Certificates and keys used in the test.
    // Certificates are signed with signature using MD5WithRSA algorithm.
    static String trusedCertStr =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIICrDCCAhWgAwIBAgIBADANBgkqhkiG9w0BAQQFADBJMQswCQYDVQQGEwJVUzET\n" +
            "MBEGA1UECBMKU29tZS1TdGF0ZTESMBAGA1UEBxMJU29tZS1DaXR5MREwDwYDVQQK\n" +
            "EwhTb21lLU9yZzAeFw0wODEyMDgwMjQzMzZaFw0yODA4MjUwMjQzMzZaMEkxCzAJ\n" +
            "BgNVBAYTAlVTMRMwEQYDVQQIEwpTb21lLVN0YXRlMRIwEAYDVQQHEwlTb21lLUNp\n" +
            "dHkxETAPBgNVBAoTCFNvbWUtT3JnMIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKB\n" +
            "gQDLxDggB76Ip5OwoUNRLdeOha9U3a2ieyNbz5kTU5lFfe5tui2/461uPZ8a+QOX\n" +
            "4BdVrhEmV94BKY4FPyH35zboLjfXSKxT1mAOx1Bt9sWF94umxZE1cjyU7vEX8HHj\n" +
            "7BvOyk5AQrBt7moO1uWtPA/JuoJPePiJl4kqlRJM2Akq6QIDAQABo4GjMIGgMB0G\n" +
            "A1UdDgQWBBT6uVG/TOfZhpgz+efLHvEzSfeoFDBxBgNVHSMEajBogBT6uVG/TOfZ\n" +
            "hpgz+efLHvEzSfeoFKFNpEswSTELMAkGA1UEBhMCVVMxEzARBgNVBAgTClNvbWUt\n" +
            "U3RhdGUxEjAQBgNVBAcTCVNvbWUtQ2l0eTERMA8GA1UEChMIU29tZS1PcmeCAQAw\n" +
            "DAYDVR0TBAUwAwEB/zANBgkqhkiG9w0BAQQFAAOBgQBcIm534U123Hz+rtyYO5uA\n" +
            "ofd81G6FnTfEAV8Kw9fGyyEbQZclBv34A9JsFKeMvU4OFIaixD7nLZ/NZ+IWbhmZ\n" +
            "LovmJXyCkOufea73pNiZ+f/4/ScZaIlM/PRycQSqbFNd4j9Wott+08qxHPLpsf3P\n" +
            "6Mvf0r1PNTY2hwTJLJmKtg==\n" +
            "-----END CERTIFICATE-----";

    static String serverCertStr =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIICqjCCAhOgAwIBAgIBBDANBgkqhkiG9w0BAQQFADBJMQswCQYDVQQGEwJVUzET\n" +
            "MBEGA1UECBMKU29tZS1TdGF0ZTESMBAGA1UEBxMJU29tZS1DaXR5MREwDwYDVQQK\n" +
            "EwhTb21lLU9yZzAeFw0wODEyMDgwMzIxMTZaFw0yODA4MjUwMzIxMTZaMHIxCzAJ\n" +
            "BgNVBAYTAlVTMRMwEQYDVQQIEwpTb21lLVN0YXRlMRIwEAYDVQQHEwlTb21lLUNp\n" +
            "dHkxETAPBgNVBAoTCFNvbWUtT3JnMRMwEQYDVQQLEwpTU0wtU2VydmVyMRIwEAYD\n" +
            "VQQDEwlsb2NhbGhvc3QwgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBAKWsWxw3\n" +
            "ot2ZiS2yebiP1Uil5xyEF41pnMasbfnyHR85GdrTch5u7ETMcKTcugAw9qBPPVR6\n" +
            "YWrMV9AKf5UoGD+a2ZTyG8gkiH7+nQ89+1dTCLMgM9Q/F0cU0c3qCNgOdU6vvszS\n" +
            "7K+peknfwtmsuCRAkKYDVirQMAVALE+r2XSJAgMBAAGjeTB3MAkGA1UdEwQCMAAw\n" +
            "CwYDVR0PBAQDAgXgMB0GA1UdDgQWBBTtbtv0tVbI+xoGYT8PCLumBNgWVDAfBgNV\n" +
            "HSMEGDAWgBT6uVG/TOfZhpgz+efLHvEzSfeoFDAdBgNVHREBAf8EEzARhwR/AAAB\n" +
            "gglsb2NhbGhvc3QwDQYJKoZIhvcNAQEEBQADgYEAWTrftGaL73lKLgRTrChGR+F6\n" +
            "//qvs0OM94IOKVeHz36NO49cMJmhJSbKdiGIkppBgpLIBoWxZlN9NOO9oSXFYZsZ\n" +
            "rHaAe9/lWMtQM7XpjqjhWVhB5VPvWFbkorQFMtRYLf7pkonGPFq8GOO1s0TKhogC\n" +
            "jtYCdzlrU4v+om/J3H8=\n" +
            "-----END CERTIFICATE-----";

    static String clientCertStr =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIICqjCCAhOgAwIBAgIBBTANBgkqhkiG9w0BAQQFADBJMQswCQYDVQQGEwJVUzET\n" +
            "MBEGA1UECBMKU29tZS1TdGF0ZTESMBAGA1UEBxMJU29tZS1DaXR5MREwDwYDVQQK\n" +
            "EwhTb21lLU9yZzAeFw0wODEyMDgwMzIyMTBaFw0yODA4MjUwMzIyMTBaMHIxCzAJ\n" +
            "BgNVBAYTAlVTMRMwEQYDVQQIEwpTb21lLVN0YXRlMRIwEAYDVQQHEwlTb21lLUNp\n" +
            "dHkxETAPBgNVBAoTCFNvbWUtT3JnMRMwEQYDVQQLEwpTU0wtQ2xpZW50MRIwEAYD\n" +
            "VQQDEwlsb2NhbGhvc3QwgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBALvwQDas\n" +
            "JlRO9KNaAC9pIW+5ejqT7KL24Y7HY9gvEjCZLrDyj/gnLSR4KIT3Ab+NRHndO9JV\n" +
            "8848slshfe/9M0qxo//GyJu5D3xBNZf52zoFYAUVr1kXkqMQrRYc5AdTr6h2olYq\n" +
            "ktP5KOB4z14fSKtcGd3hZ0O6dY31gqxDkkQbAgMBAAGjeTB3MAkGA1UdEwQCMAAw\n" +
            "CwYDVR0PBAQDAgXgMB0GA1UdDgQWBBTNu8iFqpG9/R2+zWd8/7PpTKgi5jAfBgNV\n" +
            "HSMEGDAWgBT6uVG/TOfZhpgz+efLHvEzSfeoFDAdBgNVHREBAf8EEzARhwR/AAAB\n" +
            "gglsb2NhbGhvc3QwDQYJKoZIhvcNAQEEBQADgYEAwDc4f13abs9ZeEkrl5WV2Z74\n" +
            "BlmBhXu8ExtAvoF9q6Ug6xV1MDpxbD124KfUHHL0kNMhMB1WIpC0kOnQBxziNpfS\n" +
            "7u6GOc3tWLSxw/sHoJGCefnRBllLZOoQuSBrWB8qgilL6HRmZ4UqDcXu4UCaLBZ0\n" +
            "KGDT5ASEN6Lq2GtiP4Y=\n" +
            "-----END CERTIFICATE-----";

    static byte serverPrivateExponent[] = {
            (byte)0x6e, (byte)0xa7, (byte)0x1b, (byte)0x83,
            (byte)0x51, (byte)0x35, (byte)0x9a, (byte)0x44,
            (byte)0x7d, (byte)0xf6, (byte)0xe3, (byte)0x89,
            (byte)0xa0, (byte)0xd7, (byte)0x90, (byte)0x60,
            (byte)0xa1, (byte)0x4e, (byte)0x27, (byte)0x21,
            (byte)0xa2, (byte)0x89, (byte)0x74, (byte)0xcc,
            (byte)0x9d, (byte)0x75, (byte)0x75, (byte)0x4e,
            (byte)0xc7, (byte)0x82, (byte)0xe3, (byte)0xe3,
            (byte)0xc3, (byte)0x7d, (byte)0x00, (byte)0x54,
            (byte)0xec, (byte)0x36, (byte)0xb1, (byte)0xdf,
            (byte)0x91, (byte)0x9c, (byte)0x7a, (byte)0xc0,
            (byte)0x62, (byte)0x0a, (byte)0xd6, (byte)0xa9,
            (byte)0x22, (byte)0x91, (byte)0x4a, (byte)0x29,
            (byte)0x2e, (byte)0x43, (byte)0xfa, (byte)0x8c,
            (byte)0xd8, (byte)0xe9, (byte)0xbe, (byte)0xd9,
            (byte)0x4f, (byte)0xca, (byte)0x23, (byte)0xc6,
            (byte)0xe4, (byte)0x3f, (byte)0xb8, (byte)0x72,
            (byte)0xcf, (byte)0x02, (byte)0xfc, (byte)0xf4,
            (byte)0x58, (byte)0x34, (byte)0x77, (byte)0x76,
            (byte)0xce, (byte)0x22, (byte)0x44, (byte)0x5f,
            (byte)0x2d, (byte)0xca, (byte)0xee, (byte)0xf5,
            (byte)0x43, (byte)0x56, (byte)0x47, (byte)0x71,
            (byte)0x0b, (byte)0x09, (byte)0x6b, (byte)0x5e,
            (byte)0xf2, (byte)0xc8, (byte)0xee, (byte)0xd4,
            (byte)0x6e, (byte)0x44, (byte)0x92, (byte)0x2a,
            (byte)0x7f, (byte)0xcc, (byte)0xa7, (byte)0xd4,
            (byte)0x5b, (byte)0xfb, (byte)0xf7, (byte)0x4a,
            (byte)0xa9, (byte)0xfb, (byte)0x54, (byte)0x18,
            (byte)0xd5, (byte)0xd5, (byte)0x14, (byte)0xba,
            (byte)0xa0, (byte)0x1c, (byte)0x13, (byte)0xb3,
            (byte)0x37, (byte)0x6b, (byte)0x37, (byte)0x59,
            (byte)0xed, (byte)0xdb, (byte)0x6d, (byte)0xb1
    };

    static byte serverModulus[] = {
            (byte)0x00,
            (byte)0xa5, (byte)0xac, (byte)0x5b, (byte)0x1c,
            (byte)0x37, (byte)0xa2, (byte)0xdd, (byte)0x99,
            (byte)0x89, (byte)0x2d, (byte)0xb2, (byte)0x79,
            (byte)0xb8, (byte)0x8f, (byte)0xd5, (byte)0x48,
            (byte)0xa5, (byte)0xe7, (byte)0x1c, (byte)0x84,
            (byte)0x17, (byte)0x8d, (byte)0x69, (byte)0x9c,
            (byte)0xc6, (byte)0xac, (byte)0x6d, (byte)0xf9,
            (byte)0xf2, (byte)0x1d, (byte)0x1f, (byte)0x39,
            (byte)0x19, (byte)0xda, (byte)0xd3, (byte)0x72,
            (byte)0x1e, (byte)0x6e, (byte)0xec, (byte)0x44,
            (byte)0xcc, (byte)0x70, (byte)0xa4, (byte)0xdc,
            (byte)0xba, (byte)0x00, (byte)0x30, (byte)0xf6,
            (byte)0xa0, (byte)0x4f, (byte)0x3d, (byte)0x54,
            (byte)0x7a, (byte)0x61, (byte)0x6a, (byte)0xcc,
            (byte)0x57, (byte)0xd0, (byte)0x0a, (byte)0x7f,
            (byte)0x95, (byte)0x28, (byte)0x18, (byte)0x3f,
            (byte)0x9a, (byte)0xd9, (byte)0x94, (byte)0xf2,
            (byte)0x1b, (byte)0xc8, (byte)0x24, (byte)0x88,
            (byte)0x7e, (byte)0xfe, (byte)0x9d, (byte)0x0f,
            (byte)0x3d, (byte)0xfb, (byte)0x57, (byte)0x53,
            (byte)0x08, (byte)0xb3, (byte)0x20, (byte)0x33,
            (byte)0xd4, (byte)0x3f, (byte)0x17, (byte)0x47,
            (byte)0x14, (byte)0xd1, (byte)0xcd, (byte)0xea,
            (byte)0x08, (byte)0xd8, (byte)0x0e, (byte)0x75,
            (byte)0x4e, (byte)0xaf, (byte)0xbe, (byte)0xcc,
            (byte)0xd2, (byte)0xec, (byte)0xaf, (byte)0xa9,
            (byte)0x7a, (byte)0x49, (byte)0xdf, (byte)0xc2,
            (byte)0xd9, (byte)0xac, (byte)0xb8, (byte)0x24,
            (byte)0x40, (byte)0x90, (byte)0xa6, (byte)0x03,
            (byte)0x56, (byte)0x2a, (byte)0xd0, (byte)0x30,
            (byte)0x05, (byte)0x40, (byte)0x2c, (byte)0x4f,
            (byte)0xab, (byte)0xd9, (byte)0x74, (byte)0x89
    };

    static byte clientPrivateExponent[] = {
            (byte)0x11, (byte)0xb7, (byte)0x6a, (byte)0x36,
            (byte)0x3d, (byte)0x30, (byte)0x37, (byte)0xce,
            (byte)0x61, (byte)0x9d, (byte)0x6c, (byte)0x84,
            (byte)0x8b, (byte)0xf3, (byte)0x9b, (byte)0x25,
            (byte)0x4f, (byte)0x14, (byte)0xc8, (byte)0xa4,
            (byte)0xdd, (byte)0x2f, (byte)0xd7, (byte)0x9a,
            (byte)0x17, (byte)0xbd, (byte)0x90, (byte)0x19,
            (byte)0xf7, (byte)0x05, (byte)0xfd, (byte)0xf2,
            (byte)0xd2, (byte)0xc5, (byte)0xf7, (byte)0x77,
            (byte)0xbe, (byte)0xea, (byte)0xe2, (byte)0x84,
            (byte)0x87, (byte)0x97, (byte)0x3a, (byte)0x41,
            (byte)0x96, (byte)0xb6, (byte)0x99, (byte)0xf8,
            (byte)0x94, (byte)0x8c, (byte)0x58, (byte)0x71,
            (byte)0x51, (byte)0x8c, (byte)0xf4, (byte)0x2a,
            (byte)0x20, (byte)0x9e, (byte)0x1a, (byte)0xa0,
            (byte)0x26, (byte)0x99, (byte)0x75, (byte)0xd6,
            (byte)0x31, (byte)0x53, (byte)0x43, (byte)0x39,
            (byte)0xf5, (byte)0x2a, (byte)0xa6, (byte)0x7e,
            (byte)0x34, (byte)0x42, (byte)0x51, (byte)0x2a,
            (byte)0x40, (byte)0x87, (byte)0x03, (byte)0x88,
            (byte)0x43, (byte)0x69, (byte)0xb2, (byte)0x89,
            (byte)0x6d, (byte)0x20, (byte)0xbd, (byte)0x7d,
            (byte)0x71, (byte)0xef, (byte)0x47, (byte)0x0a,
            (byte)0xdf, (byte)0x06, (byte)0xc1, (byte)0x69,
            (byte)0x66, (byte)0xa8, (byte)0x22, (byte)0x37,
            (byte)0x1a, (byte)0x77, (byte)0x1e, (byte)0xc7,
            (byte)0x94, (byte)0x4e, (byte)0x2c, (byte)0x27,
            (byte)0x69, (byte)0x45, (byte)0x5e, (byte)0xc8,
            (byte)0xf8, (byte)0x0c, (byte)0xb7, (byte)0xf8,
            (byte)0xc0, (byte)0x8f, (byte)0x99, (byte)0xc1,
            (byte)0xe5, (byte)0x28, (byte)0x9b, (byte)0xf9,
            (byte)0x4c, (byte)0x94, (byte)0xc6, (byte)0xb1
    };

    static byte clientModulus[] = {
            (byte)0x00,
            (byte)0xbb, (byte)0xf0, (byte)0x40, (byte)0x36,
            (byte)0xac, (byte)0x26, (byte)0x54, (byte)0x4e,
            (byte)0xf4, (byte)0xa3, (byte)0x5a, (byte)0x00,
            (byte)0x2f, (byte)0x69, (byte)0x21, (byte)0x6f,
            (byte)0xb9, (byte)0x7a, (byte)0x3a, (byte)0x93,
            (byte)0xec, (byte)0xa2, (byte)0xf6, (byte)0xe1,
            (byte)0x8e, (byte)0xc7, (byte)0x63, (byte)0xd8,
            (byte)0x2f, (byte)0x12, (byte)0x30, (byte)0x99,
            (byte)0x2e, (byte)0xb0, (byte)0xf2, (byte)0x8f,
            (byte)0xf8, (byte)0x27, (byte)0x2d, (byte)0x24,
            (byte)0x78, (byte)0x28, (byte)0x84, (byte)0xf7,
            (byte)0x01, (byte)0xbf, (byte)0x8d, (byte)0x44,
            (byte)0x79, (byte)0xdd, (byte)0x3b, (byte)0xd2,
            (byte)0x55, (byte)0xf3, (byte)0xce, (byte)0x3c,
            (byte)0xb2, (byte)0x5b, (byte)0x21, (byte)0x7d,
            (byte)0xef, (byte)0xfd, (byte)0x33, (byte)0x4a,
            (byte)0xb1, (byte)0xa3, (byte)0xff, (byte)0xc6,
            (byte)0xc8, (byte)0x9b, (byte)0xb9, (byte)0x0f,
            (byte)0x7c, (byte)0x41, (byte)0x35, (byte)0x97,
            (byte)0xf9, (byte)0xdb, (byte)0x3a, (byte)0x05,
            (byte)0x60, (byte)0x05, (byte)0x15, (byte)0xaf,
            (byte)0x59, (byte)0x17, (byte)0x92, (byte)0xa3,
            (byte)0x10, (byte)0xad, (byte)0x16, (byte)0x1c,
            (byte)0xe4, (byte)0x07, (byte)0x53, (byte)0xaf,
            (byte)0xa8, (byte)0x76, (byte)0xa2, (byte)0x56,
            (byte)0x2a, (byte)0x92, (byte)0xd3, (byte)0xf9,
            (byte)0x28, (byte)0xe0, (byte)0x78, (byte)0xcf,
            (byte)0x5e, (byte)0x1f, (byte)0x48, (byte)0xab,
            (byte)0x5c, (byte)0x19, (byte)0xdd, (byte)0xe1,
            (byte)0x67, (byte)0x43, (byte)0xba, (byte)0x75,
            (byte)0x8d, (byte)0xf5, (byte)0x82, (byte)0xac,
            (byte)0x43, (byte)0x92, (byte)0x44, (byte)0x1b
    };

    static char[] passphrase = "passphrase".toCharArray();

    private final String protocol;

    protected MD5NotAllowedInTLS13CertificateSignature(String protocol) {
        super();
        this.protocol = protocol;
    }

    public static void main(String[] args) throws Exception {
        // MD5 is disabled by default in java.security config file.
        Security.setProperty("jdk.certpath.disabledAlgorithms", "");
        Security.setProperty("jdk.tls.disabledAlgorithms", "");

        // Should fail on TLSv1.3 and up.
        runAndCheckException(
                // The conditions to reproduce the bug being fixed only met when
                // 'TLS' is specified, i.e. when older versions of protocol are
                // supported besides TLSv1.3.
                () -> new MD5NotAllowedInTLS13CertificateSignature("TLS").run(),
                serverEx -> {
                    Throwable clientEx = serverEx.getSuppressed()[0];
                    assertTrue(clientEx instanceof SSLHandshakeException);
                    assertEquals(clientEx.getMessage(), "(bad_certificate) "
                            + "PKIX path validation failed: "
                            + "java.security.cert.CertPathValidatorException: "
                            + "Algorithm constraints check failed on signature"
                            + " algorithm: MD5withRSA");
                });

        // Should run fine on TLSv1.2.
        new MD5NotAllowedInTLS13CertificateSignature("TLSv1.2").run();
    }

    @Override
    public SSLContext createServerSSLContext() throws Exception {
        return getSSLContext(trusedCertStr, serverCertStr,
                serverModulus, serverPrivateExponent, passphrase, protocol);
    }

    @Override
    public SSLContext createClientSSLContext() throws Exception {
        return getSSLContext(trusedCertStr, clientCertStr,
                clientModulus, clientPrivateExponent, passphrase, protocol);
    }

    private static SSLContext getSSLContext(String trusedCertStr,
            String keyCertStr, byte[] modulus,
            byte[] privateExponent, char[] passphrase, String protocol)
            throws Exception {

        // generate certificate from cert string
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        ByteArrayInputStream is =
                new ByteArrayInputStream(trusedCertStr.getBytes());
        Certificate trusedCert = cf.generateCertificate(is);
        is.close();

        // create a key store
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);

        // import the trused cert
        ks.setCertificateEntry("RSA Export Signer", trusedCert);

        // generate the private key.
        RSAPrivateKeySpec priKeySpec = new RSAPrivateKeySpec(
                new BigInteger(modulus),
                new BigInteger(privateExponent));
        KeyFactory kf = KeyFactory.getInstance("RSA");
        RSAPrivateKey priKey =
                (RSAPrivateKey) kf.generatePrivate(priKeySpec);

        // generate certificate chain
        is = new ByteArrayInputStream(keyCertStr.getBytes());
        Certificate keyCert = cf.generateCertificate(is);
        is.close();

        Certificate[] chain = new Certificate[2];
        chain[0] = keyCert;
        chain[1] = trusedCert;

        // import the key entry.
        ks.setKeyEntry("Whatever", priKey, passphrase, chain);

        // Using PKIX TrustManager - this is where MD5 signature check is done.
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(ks);

        // create SSL context
        SSLContext ctx = SSLContext.getInstance(protocol);

        // Using "SunX509" which doesn't check peer supported signature
        // algorithms, so we check against local supported signature
        // algorithms which constitutes the fix being tested.
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);

        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return ctx;
    }
}
