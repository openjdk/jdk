/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.java.security;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * This class contains a 3-certificate chain for use in TLS tests.
 * The method {@link #getKeyStore()} returns a keystore with a single entry
 * containing one server+one intermediate CA certificate.
 * Server's CN and subjectAltName are both set to "client"
 *
 * The method {@link #getTrustStore()} returns a keystore with a single entry
 * containing the root CA certificate used for signing the intermediate CA.
 */
class TestCertificates {

    // "/C=US/ST=CA/O=Test Root CA, Inc."
    // basicConstraints=critical, CA:true
    // subjectKeyIdentifier    = hash
    // authorityKeyIdentifier  = keyid:always
    // keyUsage                = keyCertSign
    private static final String ROOT_CA_CERT =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIB0jCCAXigAwIBAgIUE+wUdx22foJXSQzD3hpCNCqITLEwCgYIKoZIzj0EAwIw\n" +
            "NzELMAkGA1UEBhMCVVMxCzAJBgNVBAgMAkNBMRswGQYDVQQKDBJUZXN0IFJvb3Qg\n" +
            "Q0EsIEluYy4wIBcNMjIwNDEyMDcxMzMzWhgPMjEyMjAzMTkwNzEzMzNaMDcxCzAJ\n" +
            "BgNVBAYTAlVTMQswCQYDVQQIDAJDQTEbMBkGA1UECgwSVGVzdCBSb290IENBLCBJ\n" +
            "bmMuMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEBKye/mwO0V0WLr71tf8auFEz\n" +
            "EmqhaYWauaP17Fb33fRAeG8aVp9c4B0isv/VgcqSTRMG0SJjbx7ttSYwR/JNhqNg\n" +
            "MF4wDwYDVR0TAQH/BAUwAwEB/zAdBgNVHQ4EFgQUpfGt4bjadmVzWeXAiSMp9pLU\n" +
            "RMkwHwYDVR0jBBgwFoAUpfGt4bjadmVzWeXAiSMp9pLURMkwCwYDVR0PBAQDAgIE\n" +
            "MAoGCCqGSM49BAMCA0gAMEUCIBF8YyD5BBuhkFNV/3rNmvvMuvWUAECJ8rrUg8kr\n" +
            "J8zpAiEAzbZQsC/IZ0wVNd4lqHn6/Ih5v7vhCgkg95KCP1NhBnU=\n" +
            "-----END CERTIFICATE-----";

    // "/C=US/ST=CA/O=Test Intermediate CA, Inc."
    // basicConstraints=critical, CA:true, pathlen:0
    // subjectKeyIdentifier    = hash
    // authorityKeyIdentifier  = keyid:always
    // keyUsage                = keyCertSign
    private static final String CA_CERT =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIB3TCCAYOgAwIBAgIUQ+lTbsDcIQ1UUg0RGdpJB6JMXpcwCgYIKoZIzj0EAwIw\n" +
            "NzELMAkGA1UEBhMCVVMxCzAJBgNVBAgMAkNBMRswGQYDVQQKDBJUZXN0IFJvb3Qg\n" +
            "Q0EsIEluYy4wIBcNMjIwNDEyMDcxMzM0WhgPMjEyMjAzMTkwNzEzMzRaMD8xCzAJ\n" +
            "BgNVBAYTAlVTMQswCQYDVQQIDAJDQTEjMCEGA1UECgwaVGVzdCBJbnRlcm1lZGlh\n" +
            "dGUgQ0EsIEluYy4wWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQ7DsKCSQkP5oT2\n" +
            "Wx0gf40N+H/F75w1YmPm6dp2wiQ6JPMN/4En87Ylx0ISJkeXJLxrbLvu2xZ+aonM\n" +
            "kckNh/ERo2MwYTASBgNVHRMBAf8ECDAGAQH/AgEAMB0GA1UdDgQWBBTqP6hB5Ibr\n" +
            "aivot/zWSMKr8ZkCVzAfBgNVHSMEGDAWgBSl8a3huNp2ZXNZ5cCJIyn2ktREyTAL\n" +
            "BgNVHQ8EBAMCAgQwCgYIKoZIzj0EAwIDSAAwRQIhAM0vCIV938aqGAEmELIA8Kc4\n" +
            "X+kOc4LGE0R7sMiBAbXuAiBlbNVaskKYRHIEGHEtIWet6Ufi3w9NMrycEbBZ+v5o\n" +
            "gA==\n" +
            "-----END CERTIFICATE-----";

    // "/C=US/ST=CA/O=Test Server/CN=client"
    // subjectKeyIdentifier    = hash
    // authorityKeyIdentifier  = keyid:always
    // keyUsage                = digitalSignature
    // subjectAltName          = DNS:client
    private static final String SERVER_CERT =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIB5TCCAYygAwIBAgIUNWe754lZoDc6wNs9Vsev/h9TMicwCgYIKoZIzj0EAwIw\n" +
            "PzELMAkGA1UEBhMCVVMxCzAJBgNVBAgMAkNBMSMwIQYDVQQKDBpUZXN0IEludGVy\n" +
            "bWVkaWF0ZSBDQSwgSW5jLjAgFw0yMjA0MTIwNzEzMzRaGA8yMTIyMDMxOTA3MTMz\n" +
            "NFowQTELMAkGA1UEBhMCVVMxCzAJBgNVBAgMAkNBMRQwEgYDVQQKDAtUZXN0IFNl\n" +
            "cnZlcjEPMA0GA1UEAwwGY2xpZW50MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE\n" +
            "o6zUz5QmzmfHL2xRifvaJenggck/Dlu6KC4v4rGXug69R7tWKWuRUsbSFLy29Rii\n" +
            "F7V1wjFhsyGAzNyKf/KlmaNiMGAwHQYDVR0OBBYEFHz32VSnXBF4WdLDOe7e3hF9\n" +
            "yDxmMB8GA1UdIwQYMBaAFOo/qEHkhutqK+i3/NZIwqvxmQJXMAsGA1UdDwQEAwIH\n" +
            "gDARBgNVHREECjAIggZjbGllbnQwCgYIKoZIzj0EAwIDRwAwRAIgWsCn2LIElgVs\n" +
            "VihcQznvBemWneEcmnp/Bw+lwk86KQ8CIA3loL7P/0/Ft/xXtClxJfyxEoZ/Az1n\n" +
            "HTTjbe6ZnN0Y\n" +
            "-----END CERTIFICATE-----";

    private static final String serverkey =
            //"-----BEGIN PRIVATE KEY-----\n" +
            "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgKb9cKLH++BgA9CL1\n" +
            "cdCLHpD0poPJ/uAkafGXDJBR67ChRANCAASjrNTPlCbOZ8cvbFGJ+9ol6eCByT8O\n" +
            "W7ooLi/isZe6Dr1Hu1Ypa5FSxtIUvLb1GKIXtXXCMWGzIYDM3Ip/8qWZ";
            // + "\n-----END PRIVATE KEY-----";

    private TestCertificates() {}

    public static KeyStore getKeyStore() throws GeneralSecurityException, IOException {
        KeyStore result = KeyStore.getInstance(KeyStore.getDefaultType());
        result.load(null, null);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate serverCert = cf.generateCertificate(
                new ByteArrayInputStream(
                        SERVER_CERT.getBytes(StandardCharsets.ISO_8859_1)));
        Certificate caCert = cf.generateCertificate(
                new ByteArrayInputStream(
                        CA_CERT.getBytes(StandardCharsets.ISO_8859_1)));
        KeyFactory kf = KeyFactory.getInstance("EC");
        PKCS8EncodedKeySpec ks = new PKCS8EncodedKeySpec(
                Base64.getMimeDecoder().decode(serverkey));
        Key key = kf.generatePrivate(ks);
        Certificate[] chain = {serverCert, caCert};

        result.setKeyEntry("server", key, new char[0], chain);
        return result;
    }

    public static KeyStore getTrustStore() throws GeneralSecurityException, IOException {
        KeyStore result = KeyStore.getInstance(KeyStore.getDefaultType());
        result.load(null, null);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate rootcaCert = cf.generateCertificate(
                new ByteArrayInputStream(
                        ROOT_CA_CERT.getBytes(StandardCharsets.ISO_8859_1)));

        result.setCertificateEntry("testca", rootcaCert);
        return result;
    }
}
