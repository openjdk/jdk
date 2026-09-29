/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * This class contains certificate material for TLS benchmarks.
 * The method {@link #getKeyStore(String)} returns a keystore with a single
 * server entry for the requested authentication profile.
 *
 * The method {@link #getTrustStore(String)} returns a truststore containing
 * the trust anchor used by the requested authentication profile.
 */
class TestCertificates {

    static final String AUTH_PROFILE_ECDSA_P256 = "ecdsa_p256";
    static final String AUTH_PROFILE_MLDSA65 = "mldsa65";

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

    private static final String MLDSA_CA_CERT =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIC3DCCAcSgAwIBAgIJANdNhiGffUIJMA0GCSqGSIb3DQEBDAUAMA0xCzAJBgNV\n" +
            "BAMTAkNBMB4XDTI2MDgxMTA2MDk0MVoXDTM2MDgwODA2MDk0MVowDTELMAkGA1UE\n" +
            "AxMCQ0EwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDFsBUq+9ua3pm3\n" +
            "l+91IcYuJ9mc1s43bfHKga1gugFxEkUIZ5i5/DNalixeVTOBiom+m+5gKtTqJ5m7\n" +
            "D/uBY8iD90UuGjYoAJRdW0N6dWuOcs5j9gHeVc+oK4sfZS/5GSFrMDrFswcORsLS\n" +
            "HvT2zdKuwVO3MaQHf4J4iEBkWlieiUgQT62dwbqkVmHPbG/pooAQL9Z2L9FRFErF\n" +
            "MtFjWX/VfSug4cPDrkVQRVWrBls84IYutAxxXCTCC1H77aud4vmZUbltLuG7/BUV\n" +
            "F3MTVVRbxmjeINkeZ0zBa36gLbl+Ou3xTORt8/sVJ8hv7iMupSg/mD3kXuG4i07D\n" +
            "27XuLeKtAgMBAAGjPzA9MB0GA1UdDgQWBBTvUJ4k303igP+h1UDquT0YE7U2QDAL\n" +
            "BgNVHQ8EBAMCAQYwDwYDVR0TAQH/BAUwAwEB/zANBgkqhkiG9w0BAQwFAAOCAQEA\n" +
            "MRehPcrW3DIq8MMTNBUTtJQ5hU1NivX4TrBBm/gjhtg8C5eYZ7f64cr8rfopiVt0\n" +
            "z7+fVNOI3MyZNm7VqHZl0Il5fmxdaGHk0EZ93K/sPZjr/BodvVHx6XGkh28zhasN\n" +
            "cwXZEKMXcXPsMATyauNK+IRAtoQvShsVlbecFpgIbI/u60FFZdUEIGzy5vWumfmR\n" +
            "lVlvr+fF0QfIDhHRgPPMwXT4SLjcELG6mLg6p1pDjj2NCjTTuRj/kep7/B6g1v3N\n" +
            "wo0KNXdl7X2kKrK6u4owCdoDqLOpF8lOO6/E99ssMAFIk9cNV6tkc7RdsAp818pX\n" +
            "rRDxX2rb0c6937b+UdH3cQ==\n" +
            "-----END CERTIFICATE-----";

    private static final String MLDSA_SERVER_CERT =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIJdTCCCF2gAwIBAgIILpNJyz+5le0wDQYJKoZIhvcNAQEMBQAwDTELMAkGA1UE\n" +
            "AxMCQ0EwHhcNMjYwODExMDYyNzQ3WhcNMzYwODA4MDYyNzQ3WjAUMRIwEAYDVQQD\n" +
            "Ewlsb2NhbGhvc3QwggeyMAsGCWCGSAFlAwQDEgOCB6EAx1NliX2iHKzfas+AJqIa\n" +
            "ENXiCGm0mesKBsdCktd7X6HXoAniNGGytBsqFXcPJk52zjBTA4bDTNldugrTJyGS\n" +
            "QlL0wUvmSL1ZbpENW4Sr2J22D1AWmPtcHOME9PBPueqHCwt+k1xTqnwIKUttkka6\n" +
            "OCRWRTP3yEFNA65sLf5rOEArj0PIP4YFxfBQ80zJqtMepBGnD1PDU1qdPi3qghFR\n" +
            "g6Ue3mtj69Z/LnkDXD88Xj3FoqEJSE427JdCN62Z5P3sSFwCHjEhRwOxSQdZmZmF\n" +
            "DlOO50O56gV6zu70S0WszDxGpgzA8pwF3FesoDLmaeOT7vEwCYZQ38xgWk8QzMIM\n" +
            "jVxkSjLEvO8zw87NjixXIorsDjRbT8ArLuVGxmFiggqLYopjJBGH46KUAWV0fNDB\n" +
            "WLZ/3/Gnf2bwoU84rEOZDrc3g2aGLEWRp+lIydLOLyQvtIynw3wb5EW2sa7EEcW3\n" +
            "qEcdS+naV3PXEyq5UW5A9eypTDIr1CRqCkIo/dShxszkMamHS2UZ9kEzWDTRN35o\n" +
            "QLWBanf2QZCiMyMcTp77eioY5hIHsVM9JY9jQFva3wTTtI9b1Vhv5PP5F0wC/d5F\n" +
            "rfo3fhc2gidO497Wkk5814dRdx4kJZmaZxGYYTHcTuQxbEP10pJ+Z/pakhE+eRk8\n" +
            "xicqMVJstNw2nuTxfIDinp/N82dUcVKxB5Sm6dcy7YqtnUT/ltFFrpIB9PmIUffR\n" +
            "NcPUeySzdH03wyBmhrL5RdjGnA7o+MEiO2wRGgla5g6SsKg+NMmNy67Gat6XP0Qp\n" +
            "EOtHah4kaREO3V3Qs5MgtBBFjOxA2BQy6ybNheBDdWHoVo17cSLo3s46RJVGSzIM\n" +
            "MFxZi4RlHmKqqHitREInTe+CypNvz2uLvUfUZ+pg3niT9E302k6IOzwYbWY3ybcH\n" +
            "M8bY+AhmNsomOdW+HBamSemXNPZdE71cvq6zwEq09KQTIGOXY3S/IAl3rDf9XCR6\n" +
            "9+J9LJf4hms09KurFjsetRAORWnkRgJp7y3R7AnKhqvez/hZzac/bMhY2EBEZsGY\n" +
            "700D9yX+ZQ/mK2253B6VQwu7CELaL6TY3vLH0hZz+ER1SZG2RLmCnlYT9kyqxkzm\n" +
            "tmWfVo2Od39jj7kmiXkOR13ZjzgfYl+gnuKHHY8z5olmT/HzVs58lP1OLokz+Tbn\n" +
            "N1Ga0xWO0SrHjZEGSO4aJ2Dddm+hbgxLFPUO/6sJrOQGeUHJzm1h/8gbpIaflIO5\n" +
            "E5ZUltXb8gVlrwqNloMVSdrKmC8byfR5R96mIWX81cp5EH/WTFkL81zQXmCLLZSB\n" +
            "xosip08DNQ9NnJ+yt54S4+dx02FPvo+gSut+rG4qe6O/UFlJ6KnLssWmGwPfznyb\n" +
            "V5ujKtzecrXNKjm9rKOGTCSO5VH0y6RAr/R4RmlPFtOGaRQamQJ5+D1Metk84DmL\n" +
            "o3XXZ+semLk9+pTfiHORE+lH/Uj1K8i9YJaykqFlR0FP6/1wduoLMK5dD861bR+A\n" +
            "UkIFywyO6OxiOmOEaWePM59TY1dqP+LpLCpfL/8Fk+y6nwBYokO60zEj2iGCxSan\n" +
            "ky2ULwjptxw6qMAzAuTqm8cV/fVcqynNlAu69jobR8VtLAb2CwsYIQUSISs5UyB4\n" +
            "KI7ungFa0x7Z+w2appeAzcHMbMjzDxCF8FnPRN4NGWIMfOBqklrSg5sbeTvh2LBE\n" +
            "Wryh4uy7yq5LBCnl1DChu8U/QBo6iPtMp+w8WhuH4mIySM+t+V/QeDGHltCeVmup\n" +
            "U4+abJs6LDcDBIC/EbE1D4DfXyLe8ukB9o6M7UNI2lJLGHMYrUlG1xxmq1WGlcCp\n" +
            "gpDQST5Ouc0OKgLuDYT2+KvAWwJ48Q9lAuUPDVyKLN376mdbWG3VaXk7gi6oCsdP\n" +
            "NVkQ9qWj9Fx29Nz/J4d0re/Ibz+yplRZbTsKVR0rxJ8VNx/hdptTRwJ7zs+8WvsH\n" +
            "JwE7Dz1bHm6Hnv2Z+1thmnPpkwMLZhFfP0Jhn30nFtEpEq+Bqxr/V/Az04LrmChq\n" +
            "mfhPY1ymUeNWHv20DNOmB3ih1lp2VvoIfN4btTQyD1ji23tuyRyb7gVwM6m1EK3F\n" +
            "uKxpPJieWO25x1QGGqH0Ww5bkq9YYLoLZz0SZh8Vl8LkxRQ5FLQvDGwuuhjge4Ts\n" +
            "w9YvLIX/WVtwfARTQK3K1vPy6M7oWnkW7ymEqB0CQR10Y1/eAsnM8ZjG/gZm8mU4\n" +
            "B4ojJBHJ1bPK1NXiXKGMc1n63U0gg1ovrVWsOQMv31Q+hJOA+Hh/ydxhJHEN5HBJ\n" +
            "QK9+r1TgbdS7T7yeSh2ga1SdvMyr7GbBO/kuDZzPBVaZYgEBE8NDB6uBt78HuM4o\n" +
            "9pI3JvFRaCRKLVMXonNFzWGTS5w2Yt7uhrJnL9v05qK1VYnk+JfU7GTnqMlqDkf8\n" +
            "K1njPQC7RN0vHuWQFLxOKs3VtxCKnm8awoD6Bg+IIXTTkCIzLytGQZCXjs0o8bPO\n" +
            "JNbWmUlp2w4sxq+/FuZUmS8dgj8GpETJh1zLOWkQoQs1VuTHlrzQJnE7bjUz2Wzt\n" +
            "h0d2oON3ztIVUMCnbdzGIbUrWdZ2Yyqpc3i3/jN88DYzH+cbfZ8Sc9l0XAxRymxt\n" +
            "bJtJQFcm8enEQKntcUUgXH+jQjBAMB0GA1UdDgQWBBQT8YllH+fHGlFX8Al8tV8l\n" +
            "aV0GVTAfBgNVHSMEGDAWgBTvUJ4k303igP+h1UDquT0YE7U2QDANBgkqhkiG9w0B\n" +
            "AQwFAAOCAQEAknJdGMDpfNEE2Bn343IdKgFy9ntpUBl299Li3xav+DNLi4VlXWQO\n" +
            "d/B8qu3dpJ23USibqc87CEvEZglNHWg/S394bruAhkWHlgzwD/J6SeNjk58OYQ79\n" +
            "uT+5okx2ALoFmXa9YqXa6H9d7DKLxVdjBB5pWJbY0fuMFbFl10zZ+BgHkB34+aeQ\n" +
            "BLxDqT3rI3JJb1WRNNgq9OlCYZTr8iDqFuQR5bCW1yX9MaaxcCeY1GrHuOSMYp9X\n" +
            "trJn12uNqV8TdT0yPnqJOOm8sAJeCqjgil5jAienQhZnW8duxD6Xepj0oPottbk4\n" +
            "IwWgOeLjoKhqhLxYkTlCpO31JWbx6B7bGg==\n" +
            "-----END CERTIFICATE-----";

    private static final String mldsaServerKey =
            "MDQCAQAwCwYJYIZIAWUDBAMSBCKAIDX3Fb2fp4CvjPbCRd2f8QN5fb1ljKOdopRe" +
            "ATnEmhkP";

    private TestCertificates() {}

    public static KeyStore getKeyStore(String authProfile)
            throws GeneralSecurityException, IOException {
        KeyStore result = KeyStore.getInstance(KeyStore.getDefaultType());
        result.load(null, null);
        switch (authProfile) {
            case AUTH_PROFILE_ECDSA_P256 -> {
                Certificate serverCert = getCert(SERVER_CERT);
                Certificate caCert = getCert(CA_CERT);
                Key key = getPrivKey("EC", serverkey);
                result.setKeyEntry("server", key, new char[0],
                        new Certificate[] {serverCert, caCert});
            }
            case AUTH_PROFILE_MLDSA65 -> {
                Certificate serverCert = getCert(MLDSA_SERVER_CERT);
                Certificate caCert = getCert(MLDSA_CA_CERT);
                Key key = getPrivKey("ML-DSA", mldsaServerKey);
                result.setKeyEntry("server", key, new char[0],
                        new Certificate[] {serverCert, caCert});
            }
            default -> throw new IllegalArgumentException(
                    "Unknown auth profile: " + authProfile);
        }
        return result;
    }

    public static KeyStore getTrustStore(String authProfile)
            throws GeneralSecurityException, IOException {
        KeyStore result = KeyStore.getInstance(KeyStore.getDefaultType());
        result.load(null, null);
        switch (authProfile) {
            case AUTH_PROFILE_ECDSA_P256 ->
                    result.setCertificateEntry("testca",
                            getCert(ROOT_CA_CERT));
            case AUTH_PROFILE_MLDSA65 ->
                    result.setCertificateEntry("testca",
                            getCert(MLDSA_CA_CERT));
            default -> throw new IllegalArgumentException(
                    "Unknown auth profile: " + authProfile);
        }
        return result;
    }

    private static Certificate getCert(String pem)
            throws GeneralSecurityException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return cf.generateCertificate(new ByteArrayInputStream(
                pem.getBytes(StandardCharsets.ISO_8859_1)));
    }

    private static Key getPrivKey(String algorithm, String keyBase64)
            throws GeneralSecurityException {
        KeyFactory kf = KeyFactory.getInstance(algorithm);
        PKCS8EncodedKeySpec ks = new PKCS8EncodedKeySpec(
                Base64.getMimeDecoder().decode(keyBase64));
        return kf.generatePrivate(ks);
    }
}
