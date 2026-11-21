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
 *
 * The ECDSA-P256 and ML-DSA-65 profiles: each has a leaf and the same RSA CA
 * (i.e. RSA_CA_CERT), and the client trusts that same CA.
 * Their leaf certificates (i.e. ECDSA_P256_SERVER_CERT and MLDSA65_SERVER_CERT)
 * have matching validity and extensions, and are both signed with SHA384withRSA.
 * The leaf public key and TLS CertificateVerify algorithms are the intended variables.
 */
class TestCertificates {

    static final String AUTH_PROFILE_ECDSA_P256 = "ecdsa_p256";
    static final String AUTH_PROFILE_MLDSA65 = "mldsa65";


    /*
     * "/C=US/ST=CA/O=Test Root CA, Inc."
     * basicConstraints       = critical, CA:true
     * subjectKeyIdentifier   = hash
     * keyUsage               = keyCertSign
     * certificate signature  = SHA384withRSA
     */
    private static final String RSA_CA_CERT =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIDMzCCAhugAwIBAgIJAPkLm0LFcOPzMA0GCSqGSIb3DQEBDAUAMDcxGzAZBgNV\n" +
            "BAoTElRlc3QgUm9vdCBDQSwgSW5jLjELMAkGA1UECBMCQ0ExCzAJBgNVBAYTAlVT\n" +
            "MB4XDTI2MDEwMTA4MDAwMFoXDTM1MTIzMDA4MDAwMFowNzEbMBkGA1UEChMSVGVz\n" +
            "dCBSb290IENBLCBJbmMuMQswCQYDVQQIEwJDQTELMAkGA1UEBhMCVVMwggEiMA0G\n" +
            "CSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCu+msbxHb+9OXa+aK5nA5xgylEk1d1\n" +
            "+8d/OswxyIERszYk/pgtVcWUg8w1dvIEo7YEG82nCO0pJ6f04D2cDXOJuQKoyn3t\n" +
            "DS5KlbxVmhQ4Sz6GzoQKrNU7hBeHDFeXFHCkhjHRB1tAaDITJelBFkB8rpuAiY3k\n" +
            "9HF3tp3B9ca0tlr7PPibcxjD5EvP4q4IbXZMcHec03C4KzOV+LDfKdwrShyOP03N\n" +
            "rB6KIpU04noK90NmZNjL/ucrqjwlew55xWmBvTjmscshco/BSEWarYW3bfulywiJ\n" +
            "1fQLbt+HMcb3bmDiOwBFGfxKpaGGPUDHl2lT1A6fQ3H9M4uc8XzU7H5DAgMBAAGj\n" +
            "QjBAMB0GA1UdDgQWBBRXHrhCxRu0/IzKgXlDe4H0E8Hu9DAOBgNVHQ8BAf8EBAMC\n" +
            "AgQwDwYDVR0TAQH/BAUwAwEB/zANBgkqhkiG9w0BAQwFAAOCAQEAO/9W3jBj0wue\n" +
            "xC0AWW6MEq3KJI6C/Q4YdbytAmsx+9uR2I3aMuJbfemh7akW3+KsA0Tn5XJvXDar\n" +
            "vXUy+bjc8hnXB+JQPchux7WLO42/cV0oO7isgtCJQeyD97YaZi8uQrxrO3rljqfL\n" +
            "QtNqraVvf2yLTj9dZyu8Zwt7CdzV2592nfMYgk7WW6xNSa/BxUFNeK2L+UA7rHFy\n" +
            "DkHzKtzbL3ZJT+9bP/JeDnYL4vSq31q4NGGoX7Bp4E2p1Mik8nk1I1mAu6sk8AsB\n" +
            "U2HTG89p6kP6HZAulfyJjSC1elPybaPZRewiIYVdDU1xxZGDbI4BBsdJ0ib+a0fN\n" +
            "ZO7l+wXEDg==\n" +
            "-----END CERTIFICATE-----";

    /*
     * "/C=US/ST=CA/O=Test Server/CN=client"
     * issuer                 = RSA_CA_CERT
     * subjectKeyIdentifier   = hash
     * authorityKeyIdentifier = keyid:always
     * keyUsage               = digitalSignature
     * subjectAltName         = DNS:client
     * certificate signature  = SHA384withRSA
     */
    private static final String ECDSA_P256_SERVER_CERT =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIICkTCCAXmgAwIBAgIILnqnwixRjNswDQYJKoZIhvcNAQEMBQAwNzEbMBkGA1UE\n" +
            "ChMSVGVzdCBSb290IENBLCBJbmMuMQswCQYDVQQIEwJDQTELMAkGA1UEBhMCVVMw\n" +
            "HhcNMjYwMTAxMDgwMDAwWhcNMzUxMjMwMDgwMDAwWjBBMQ8wDQYDVQQDEwZjbGll\n" +
            "bnQxFDASBgNVBAoTC1Rlc3QgU2VydmVyMQswCQYDVQQIEwJDQTELMAkGA1UEBhMC\n" +
            "VVMwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASa320SU05tUU/0CtKHNJZ6KPRn\n" +
            "LcEKeLvzyV+wv5eSLiuQKJziO2TVQoyJ8ubxU7dMNGz6wR8nIGOstNj/nXj6o2Iw\n" +
            "YDAdBgNVHQ4EFgQU/OKxH+0P0T4Zmo3uPZL390IvzL0wCwYDVR0PBAQDAgeAMBEG\n" +
            "A1UdEQQKMAiCBmNsaWVudDAfBgNVHSMEGDAWgBRXHrhCxRu0/IzKgXlDe4H0E8Hu\n" +
            "9DANBgkqhkiG9w0BAQwFAAOCAQEAgimF3mhoeYo2iZA75tR2unVaelzGn+do38/t\n" +
            "T9qc6z9LO1YrQWqX/qWDOVpb5hRmGZVZZ2l55dPw8MZpBHJRBVlaHevHcj85e0hi\n" +
            "H+oeq+PE6+yx21SVGDI/PkDF3zTee6jxlxZoWL+hA/y6CjG2yOD8gkH6LpCfPu9I\n" +
            "hQIBPjtIby0cD1meIbJknZL3geKzNx15jHIv0OMsiK5I/Jb2acBk94sCNIlqu7w9\n" +
            "gM5+I1fD2jOD5I3cqo1v6/ubsUfmLs9oziHERnL+HG1YAuJIqoTph7PhOo4YxoEC\n" +
            "haClAhZ4xbU3CVEp8yA+QcVyGbSfenuNr3xP1Pz6lQzLp8XNsw==\n" +
            "-----END CERTIFICATE-----";

    private static final String controlledEcdsaP256ServerKey =
            "MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCCt6kp2YgwlAV5pBRIO" +
            "ijBGoskC6JyB1DKzWc9yPst4PA==";

    /*
     * "/C=US/ST=CA/O=Test Server/CN=client"
     * issuer                 = RSA_CA_CERT
     * subjectKeyIdentifier   = hash
     * authorityKeyIdentifier = keyid:always
     * keyUsage               = digitalSignature
     * subjectAltName         = DNS:client
     * certificate signature  = SHA384withRSA
     */
    private static final String MLDSA65_SERVER_CERT =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIJ7TCCCNWgAwIBAgIJALQY2xu2Akg1MA0GCSqGSIb3DQEBDAUAMDcxGzAZBgNV\n" +
            "BAoTElRlc3QgUm9vdCBDQSwgSW5jLjELMAkGA1UECBMCQ0ExCzAJBgNVBAYTAlVT\n" +
            "MB4XDTI2MDEwMTA4MDAwMFoXDTM1MTIzMDA4MDAwMFowQTEPMA0GA1UEAxMGY2xp\n" +
            "ZW50MRQwEgYDVQQKEwtUZXN0IFNlcnZlcjELMAkGA1UECBMCQ0ExCzAJBgNVBAYT\n" +
            "AlVTMIIHsjALBglghkgBZQMEAxIDggehAHf+H8fqTO74fK0NGjbG3Cwwc+WuPFUY\n" +
            "mRrsi9BOx6tWr1/xm2WPpAlMn4GtF1w3hmBM9w1lPVcf/qeMyTe0Vs+fgzzOCEPl\n" +
            "g3nw0Ux0u22Ms/2aVUyN3ZGmIQcs+27CQtLPUlwuiFWHxtrNrfQxjLQNnePR9Dcq\n" +
            "H65ytOrfK/fDx4JametWJtgQOPqARbprKsIHB5nA12abhB+JCpjswZikbAYslq5v\n" +
            "QpH7sBirB89VWWHdmqVrH5Dw4tfnxq2gxT634CryUUD793EB5a5e52/N9W5oa0Iz\n" +
            "oFpmWlZ0IF+qp91kphTg/qhhteZLsGb+cxgjepuHi9TwfoVcGaWVpS51R0twtKae\n" +
            "4z8HDXGGQNTfMv/OIhOfgdlGh6O+8tVehzxTYr5qDzu/i2wcxEsMvfTmkpAwqX3W\n" +
            "kNQmXAX0rqUqdu3q+Y6oc09rsIYLXbyLg+XM8Ar9hpQ2+hdS8Sf3DpwOy7pyHzJB\n" +
            "VU3Mv7VfxwsvWkz28d6d/ZreLVtpPKOyoZg5jC7JelR1yAOKYGZ+ARTYmQYYukTu\n" +
            "OSyL11gROa0W+PdTRzvgNu3I+OlwZc9axGAmaUdzuVuMb7kAm8oFMD5C93JqNl5F\n" +
            "OFZ49XFbrIOQD9DQRbkI2P6QCbZKoU697My1Fe+frF64tAuHEEl/CL5Yl8NSDrmO\n" +
            "Ny0Np0KzpNovmIt2NJrasneg/LtfsRUY0FnuBPeuFLAB/ZXiuDVqXJfKt4GGk34Z\n" +
            "QDF5Vc+z3qhgthLBsYBVpDb1ZX5bbJ306oHHHK3U0Srn02FUlc6kZQyHvO9LXbhp\n" +
            "7DWawIQbjt/M23ZKXL4pEHDkoiNeVF+1Y95OHjReYR277tAS6gf3qudm0OQ9lWVs\n" +
            "U2K3mXK9TBGo5y1KFgeQYZqwpcZsF2koU1VzZjgq2xpGNanpyhtFgTEQ7ombNhC4\n" +
            "RzWspTpKgIpDChSBzcaRhs5ToYaQrrIwo0lb0aHBPRFkAnkOOXnNUb2oizDjGECp\n" +
            "sdIy6wwIoy5WRnwp8V3Tle7tIYw3LjBDRyaf0URTVuSA6hRa2VqiaG9DiQNMPRJw\n" +
            "p5t2/BqMq/3ex5AvyGD3S3Tp9RCWYwdoRFxSnnAXMyIaAiBoKehy3VijU3C4l3ou\n" +
            "AmQZg4PIRpUeuuiniHgvP+/pxd8D7VlC7l+ySz0KAT3RAAp0gPgGipzi7hsHEltN\n" +
            "df2Mj7i4Fm1BGdT3NDePALaIwZJdojB1f8PgH302mANrI4akIPzXR59A30S+CSze\n" +
            "ZYPi52QKzCqk/09NA7eTsRoLNzS/u6edE+yhEnWl1xGC3oMmnIrEYYujQ6jVrxB3\n" +
            "qvtyJEuB0yh5katxY46quMk8fosQmosEZsrKCx40tucm5RH+FRe0ThphBFigv7V4\n" +
            "wURQO6KsyIKwdsfMqI8IWGFF6dpHQY1J4YrCqse+uBj5JQ26xWOp0vVWT5fZ1SK0\n" +
            "cxGAHiXREfOXySExkGWGOprgY7WuyXSSTP3sxF5/+q13VML8jpzAYK3VFZ+x9arN\n" +
            "1+zcZSib61qS5v6ph2KtNEI8GC5XgNWv76w6rpOHZVdfdoS0qwbBshNtDzRcnBGp\n" +
            "nDMfppkd5NCPjYFmycobD7shx47zKlvto+Mvlyk+4YJQZC5C51jJ0wOFjk3Rs+NM\n" +
            "Kt4edMmyVCnzOpv3AQo9llebdYFrMNBFpdHz9zmKZDmn5s2SYt2ZrQeaDAN95PWa\n" +
            "lBl+nhYlitPso06chqcHrxOUwF589RizEdSg5acwc800RYXF5FHwwGwbrq4+aWsm\n" +
            "cQvPoMjecbZyH95Q+x8ZCpdTt9ah9+JEeJxLFst7kGzDIwN1hGEHJs+niNrDBl4h\n" +
            "l8NSAT8Ea4hWgk5mAw2bBNsE0RDnwyMiorsIK01PE6hf0Tyu7E/HlGxeBIp1pkIN\n" +
            "uss9iSGoWmwyqnWVp3cGNiKcAKngK/YjryHfKpqHH3pct5NPnAzqqmACaLheJW66\n" +
            "jUXjHMvX9oejaL/FgX9B3TX807DLuatpasnnBiog65SxXGeUAEJSLCFnx2f3jsgd\n" +
            "yY1fKOZOadDpTI6W7fifTWAIjs3whoEKzeXUSQLdBkx+2ClZ7SyqUVA9vWY732v1\n" +
            "0kkFTz4ApRUSVd8ej6UoFiMfMJUlOYsmY32j3u8bBU3tXgOcc28wiAt/FW3EPHDm\n" +
            "o38DOboE8QGp0+OX8hHEbTnAXU86ha+jceIMVth2HK4H14a0ZlpcbsoJM2rO9dNu\n" +
            "PFoIENyUhbsjINLXumtbFvOwdxMISGp6lf7V2sVgmHy1xpeMJlHek43I0j9/XIZ4\n" +
            "k4R8Lb877mThi/k4FxZi8IZNF33CMZUJGOXz+PQzhwA9cS1G4vbAFwlpheK7Zkrf\n" +
            "2SNWVcArN6PnZJWexIyyKTAl3A/DAnMco9viJ9w1d9IRy6XHZVjBu7Jg3ghJjJcv\n" +
            "d4kprH9yHHHyBUSTa2fknDHGNDCFXi6E/EDQF3dw+4q1lyAXqOCQStx3ernvtd4q\n" +
            "0U8+Dsunrsn9J5Z7XdeEi+RYHdE4XXavtzdI82zP2e4Nimub4s5X6HCu8CKzFjio\n" +
            "rUAmGEOJumU+2qmVB34P4Rqa16Xfl8GKsfFq9LdvW6wfZhnA4FjzdP96V/gRdQdj\n" +
            "vLfOhifEZzlHo2IwYDAdBgNVHQ4EFgQUNmd0JURy4UgjXmzQQfuHPQkEeS8wCwYD\n" +
            "VR0PBAQDAgeAMBEGA1UdEQQKMAiCBmNsaWVudDAfBgNVHSMEGDAWgBRXHrhCxRu0\n" +
            "/IzKgXlDe4H0E8Hu9DANBgkqhkiG9w0BAQwFAAOCAQEAdaZPyqbOAmSwqdTfN5zh\n" +
            "8Z97DVfjJfZhs9b2k9iH+frxK1VtBW2dvwI3C2Ld262kz4EvPdKK45cw5vad5q6g\n" +
            "e+RbCfigJyQSCrAfCTRl1FJHS1f2JTMAwWgOcyW/Kj2ki1p8YQdzi8k2Kei4vadT\n" +
            "wNUR0H2N6ZSumE5GW27NqOKTwvsDpu9HC9MJd5mky+qAEKVALCDDMr9Hk/LupjkT\n" +
            "DHEaCLPS031oBS//5rnuxyzNZrIhTEPmT9w1gqMtoukHgR0987VUAJJYv9rwPe+L\n" +
            "rRwuuuo/PaRLc/P8n7mM47VIbFcQMqWX/xk9mde+M2PRMC0YUA9viAa16aoljs1a\n" +
            "HA==\n" +
            "-----END CERTIFICATE-----";

    private static final String controlledMldsa65ServerKey =
            "MDQCAQAwCwYJYIZIAWUDBAMSBCKAIF23Vv+WcEpDkCC0JtcKxq4rRGZ1dZ6hRcl7" +
            "fJmcarEv";

    private TestCertificates() {}

    public static KeyStore getKeyStore(String authProfile)
            throws GeneralSecurityException, IOException {
        KeyStore result = KeyStore.getInstance(KeyStore.getDefaultType());
        result.load(null, null);
        switch (authProfile) {
            case AUTH_PROFILE_ECDSA_P256 -> {
                Certificate serverCert =
                        getCert(ECDSA_P256_SERVER_CERT);
                Certificate caCert = getCert(RSA_CA_CERT);
                Key key = getPrivKey("EC", controlledEcdsaP256ServerKey);
                result.setKeyEntry("server", key, new char[0],
                        new Certificate[] {serverCert, caCert});
            }
            case AUTH_PROFILE_MLDSA65 -> {
                Certificate serverCert =
                        getCert(MLDSA65_SERVER_CERT);
                Certificate caCert = getCert(RSA_CA_CERT);
                Key key = getPrivKey("ML-DSA", controlledMldsa65ServerKey);
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
            case AUTH_PROFILE_ECDSA_P256, AUTH_PROFILE_MLDSA65 ->
                    result.setCertificateEntry("testca",
                            getCert(RSA_CA_CERT));
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
