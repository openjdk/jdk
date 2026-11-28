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
 * Certificate material for TLS handshake benchmarks.
 *
 * The server key-manager keystore contains classical and post-quantum server
 * certificate chains. Each transmitted chain contains a leaf certificate and
 * its intermediate CA. The corresponding root CA is present only in the
 * client trust-manager trust store.
 *
 * The classical profile has the ECDSA P-256 certificate path:
 * <ul>
 *   <li>Server leaf signed with SHA256withECDSA
 *   <li>Intermediate CA signed with SHA256withECDSA
 *   <li>Root CA self-signed with SHA256withECDSA
 * </ul>
 *
 * The fully post-quantum profile has the ML-DSA-65 certificate path:
 * <ul>
 *   <li>Server leaf signed with ML-DSA-65
 *   <li>Intermediate CA signed with ML-DSA-65
 *   <li>Root CA self-signed with ML-DSA-65
 * </ul>
 *
 * Both server leaves have their CN and subjectAltName set to "client".
 *
 * The method {@link #getKeyStore()} returns a keystore with a single entry
 * containing one server+one intermediate CA certificate.
 *
 * The method {@link #getTrustStore()} returns a keystore with a single entry
 * containing the root CA certificate used for signing the intermediate CA.
 */
class TestCertificates {

    static final String AUTH_PROFILE_ECDSA_P256 = "ecdsa_p256";
    static final String AUTH_PROFILE_MLDSA65 = "mldsa65";

    // basicConstraints=critical, CA:true
    // subjectKeyIdentifier    = hash
    // authorityKeyIdentifier  = keyid:always
    // keyUsage                = keyCertSign
    private static final String ECDSA_P256_ROOT_CA_CERT =
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
    private static final String ECDSA_P256_INTERMEDIATE_CA_CERT =
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
    private static final String ECDSA_P256_SERVER_CERT =
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

    private static final String ecdsaP256ServerKey =
            //"-----BEGIN PRIVATE KEY-----\n" +
            "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgKb9cKLH++BgA9CL1\n" +
            "cdCLHpD0poPJ/uAkafGXDJBR67ChRANCAASjrNTPlCbOZ8cvbFGJ+9ol6eCByT8O\n" +
            "W7ooLi/isZe6Dr1Hu1Ypa5FSxtIUvLb1GKIXtXXCMWGzIYDM3Ip/8qWZ";
            // + "\n-----END PRIVATE KEY-----";

    // Fully post-quantum certificate path: ML-DSA-65 leaf, intermediate, and root.
    // "/C=US/ST=CA/O=Test PQ Root CA, Inc."
    // basicConstraints=critical, CA:true
    // subjectKeyIdentifier    = hash
    // authorityKeyIdentifier  = keyid:always
    // keyUsage                = keyCertSign
    private static final String MLDSA65_ROOT_CA_CERT =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIVtDCCCLGgAwIBAgIJANs5AmqBRlGSMAsGCWCGSAFlAwQDEjA6MR4wHAYDVQQK\n" +
            "ExVUZXN0IFBRIFJvb3QgQ0EsIEluYy4xCzAJBgNVBAgTAkNBMQswCQYDVQQGEwJV\n" +
            "UzAgFw0yNjAxMDEwODAwMDBaGA8yMTIyMDMxOTA4MDAwMFowOjEeMBwGA1UEChMV\n" +
            "VGVzdCBQUSBSb290IENBLCBJbmMuMQswCQYDVQQIEwJDQTELMAkGA1UEBhMCVVMw\n" +
            "ggeyMAsGCWCGSAFlAwQDEgOCB6EA2RVgUCp29qc3VwCu99j2mDqBoClJCGsXycr8\n" +
            "DIzNvYajs1iT8chsnEc+so+OUZkq+VazrOgdnH8tg7rMJT6bX2S8kA2FM0qe9ev5\n" +
            "DT2qk35ZZu0pDpnUSI7fFIAXWdDycrS0KVPH4Isdkaf2e1cSp/6GGnbv6O2raupK\n" +
            "jFlfxEw90eahy2fRFah1/Z2uEEKp4i6cFMgObDjGXnrT3lK2GjhxDmJzNTakXkCc\n" +
            "gAGE7oaf1K9w2ia+WQ/9dSzc0IGDcMQ2eArYnhwch2QSnQ1HewheWSihkPo1gM6f\n" +
            "Fqf5H4Vr95kOxG72+jFzGK9Luy5Bno+3XbeIhFUfwUUga+LbmHTn7Ui2OFjzwA+D\n" +
            "U8KKTOrb347pnp7pRlbtdY1nf+XB+3NsyLZKx/ol525pIJoUrjYBhbjgowsON7k9\n" +
            "xkQON9WrIoR6ckbfhbuHCt4T4cTdXgEcDQkAwkEYI737aipsb2Qhh6P1OQkGbTvJ\n" +
            "W8alN33TBXMeuyuHRqchgZLIAH4uM2CION8CKuRRGc6g7IVxD47iiyvYf+1+R+xz\n" +
            "kzjOdUQLkSjZzesvYOvXZYgg3xesd3BaFNM9/VP5A4wrQjs8BDdNo+QIBhdEO247\n" +
            "KeEIANO/KqFQave9WR1cfnm2RCroVXZSTcFi0MkZohxarc5eQyIvgVn90BoeRPJ7\n" +
            "J2vplQSgr5vy3UWfCD37d9u/NtUn3iyW0Y6aoVsyLM/Mzu1xcoGNzWcjpLdEd1Wn\n" +
            "ruT7sKapWQDnb/MsZdmR6+husiokOfPLxHVE4EPQKC7qZQt/oug7dhAevk+3RC66\n" +
            "cVsbaz1lCd8u7sx3lFxPEr3UkY3IsL85wDSHrgXrgUJLXogtn4KKa5YIN96Wv6W+\n" +
            "0vy05s9XM57jS9CSeUGwUc2cQFGdJW/dByhfx+FDIA2h+IyXijR39KVUjORsnhA7\n" +
            "0ladjjD2nDSk5JzgLOYrlC//avxJpZkP95X/G0adIzs5scxcspT3k4VcZbmBMz2c\n" +
            "SOyegD59ws/pS8lUMGUFGPEDnB5wkOnMKlJtZdLXVdl7oFqWCh4KjiSSHLruxyF0\n" +
            "BnZtSkbpQXdcFwf90CgaAEH3hFfqSq0CaVwaDL2fWZ6Oc5TQaKnboYbe+Wyyzdp2\n" +
            "7kGO8PPd/x5ouYhB8nIyF8Fbveoe25nF2xCLVkne2fU4rJqsRZSEdWaI0dy79cAj\n" +
            "lYQ3dGjwY7jlOZ06L/T9vK0dJwAIFtoB2Jkcd4n8MLsAuNq5ZUmdwfBdNzdnZVUy\n" +
            "iDAoKlITtlvYUUUb9BUT76kPdUejqOk+vnfyB/Rckfwot8zwqCe5F9Jrrdi5PHJ0\n" +
            "8xLxup3jtRGhV8DjQM/xTVqW/imPL4wBb6Rz5eb4k1L5Rdf/XlBPCcTiH8t4YhWO\n" +
            "/eot/IJ0shfZ1Em3cE9534zMH5RIXdPVvIf40AH26eNkcEfiiwlu3/Z0x1I6FnDg\n" +
            "55DAn5Nl/Td4G4uFJOgjhvnGvn5K8STYUV4K1BqJKf9ymNU1w0nxrPRoSywou/5x\n" +
            "8vZScKMRYPilwezG4rGRJwPS65CqAArvLQfQMcFdW3zbC/1bB7T/cyp5DF+aQfvm\n" +
            "1yooMfuoO2Og7COSNyCOb9Ai4xDBAVpxOCYFFr9jp39LTc3w0h09zLr/LUy7WMVR\n" +
            "Tc0+OLO+H2gagAuAinEW6Kh6Ji6Jie6EQO07Hy1n3XQflslGLcLQEXE8wIRcrO4I\n" +
            "WKokSWhkKMcduxU+VbICUanQ7yiWMcEujSN1Ehs1cIjY8s2cyjEzUAEJ+SKObo24\n" +
            "ibT6bKZcQpioesGnQjNEW6VQLxN4DPQvWFKfGx8RPaEqtTZny5Fgenf7iDLFjbbt\n" +
            "i2mpKwgPt1eWIojQvRSk8M2N8oIllVRZDpx/lr6yJxG50B8v1bQlmlIQ3/J7eOot\n" +
            "HapZaq9Yl4Gf+EmFpyAsYJMaSRqjv/k8bRUcxX6dORfbv/Q2ca3zgLXfuO4TIE99\n" +
            "6JDtX8FmrmU7BQ7czdR2SLrXIHOD5hr4QNJfKVeERG4VfPKBrbAUn/qfGe12RxKV\n" +
            "VR2AS3ZFFLZp4mmm103JcKltGhX3xcUo2CqRzYial7ckK2Hyffw7z1iHkFlPqKLH\n" +
            "aosdC0yetquHMx9rFr3MjsciufXQ/nIiWYalI+cv8dGvc6hHxz4avapQvPVAi3vz\n" +
            "jzWVEcy7CdvHPIXSKA78QOlrayuCBzDHp6nVsAAqjRV5avvxXR1LGh+6kYcoPtKL\n" +
            "Kh5+tBPNscY/1pwvB7vagasr6zBc3KaDuWAtOuFW8UNhtudx4jyN65t68O9nfYnO\n" +
            "x9hashhPe+3kL5od5nFbqhwOHusmGd6AUpcDpCbGoceI3YFzxzcI6jd8IwJNkymq\n" +
            "K4U6+An91Dn+UrxY6rriG2TWkHundDjiRclQC+0ekcdDr/mJfibA5hhNBV6COke8\n" +
            "+PCeIp7tE6jjU/T0NGQxlzvsaBOUea37kryyetnhFUKN2zsiGygPWzaiJ2lPrLXD\n" +
            "QNk455Dvc1SlwJs7ZS+f6PzYmgd8xhMgXAS6BA8zUhZ6EhzzBBgx9oQV5l73D/dJ\n" +
            "WHZS+OCoHCW0DoSwqTSdqQt1P8MFGeuB7szmNHvom798FV1/qCnyu3dQk8UVEAft\n" +
            "3cDpPvijQjBAMB0GA1UdDgQWBBR1l7sfL1iXt/WVjSVnciriB7KwpTAOBgNVHQ8B\n" +
            "Af8EBAMCAgQwDwYDVR0TAQH/BAUwAwEB/zALBglghkgBZQMEAxIDggzuAO0e6aEc\n" +
            "cyb++7q5j4RMPLzVNtQynjaSGipILqthBjN4zKn0Qe5GilVdFD1vXHVi07wL+6ux\n" +
            "hTYYKhtmmPWX4l4SnpWcSTd855aoHOFJTwfkFyz3Al+Uz3nq25/X/Xj4C60NOCtD\n" +
            "e2Xm4Xqo0DhGB8mBgoXFkeyZyN6TuhtA8WVlxSJ1CTbES5ltDoArD+dLhB54sbLM\n" +
            "Ondfcr7VZaL8QWADbNV2D/wlFodvtiUhhMAaJCo8oCOhkhUktAUFYlAdJnUpDFPd\n" +
            "rXSau9Bi3clBxOEwdWcydUqyYnJ3Y5M0rNaVxzwONeXTbwE5pONuXkjTdPE0HBm2\n" +
            "/A2T6LG24iPu13V+qFLM52q2bpI397wIAeoo3aE0ZCXNHBtiZoaAH9Ckbq0eTplL\n" +
            "J01DSlNvrdlFYzPZtj7CJ5J4Q6Q2nhGPnXFjPgRT61kkFsk8bC7eNQHZ+A6gXQFg\n" +
            "5clMHCoJZTzZHB3Ol13KRS03NnD3Qt3J7o+b3M9znuHug32iGM2OCKTNGAmzsP8D\n" +
            "O+kXnbK8ckk99bLGyQe9iMbtpIayIZkuE6YYZ9jXsdrYm/gS/UBb5fCWG2401Acv\n" +
            "d+f1NLiCPJoqOkpsLYBVmGs+Jcmwvcbt/+NPi0fUUhJNdzLA0LBNrR4do0/MrYlT\n" +
            "CHcoAQpXYChBgvxBDzzj+31WeAEkh20ubahZSzF05wfjv3SCfOkyLjVyl6wWJi6z\n" +
            "0uKvzEHi9oNjQwW+pe2/IgzF6xKPb5OmE3xL1TzRDDmuccU55JR+UmB6Ypz2mWaf\n" +
            "+vqYQV8kJFqF0IwyUMWhGVa7yv66eZr54Rg1kNgzM4V8T0vJnnKfj/F6YVAURnGJ\n" +
            "EXHTLyqN4KJys9voT3Tde6U4ZTODQL/XlGrri/XdiSgphi1AD0AAivcekx5/l9DX\n" +
            "hQotLrRPqKFIACzNE8G/ML6dHtxIkl5qc6PaWPmMtOIiJgohOXSUhPrRQDNUkzwN\n" +
            "c1TmSpfhE5HrryqltU/BRKXpGu4+aLkKSB/1eQg7+MSgdWvBjQasorAYVYfc10ey\n" +
            "7Sdu/ryWDV1rf7tXcM2HlLdMZ08vbU2eUPkoMAZNYuD56y0xiJbcvYaSFoX9RF2G\n" +
            "3SQnYvRs9okgYWZhV1zAEe6MI+t8Y7PK8Ib1A6U1A5j91CaDVdBs87lX5jfmsy8j\n" +
            "N1aBKvFhOXv1I0Qeplk53dM7m5OU/cv7EehtpJaeTG+XZ5JC68DmKSMCSvC8ykcA\n" +
            "UUFrIOpGPBHBPT+jFd2yTjKtWJUS70fJ0eyCOwlP79n9bzGWNx40DLSNKqO1sICb\n" +
            "UNbq2r0lqVci6gNDXdQqD/Ed4QeTgM+o418cWsbR8K77N9Cskmy0FIWeDqcoXeOW\n" +
            "Ynz/UztCzMs2AetvkKpfqWveD2OTHtaj5G+qdgxg/2D9JiBE6EClWW+cJGtRSBb3\n" +
            "V+ggmiH6L3X8dV7SfyIUjZTNm8AoJPgUIuPaXZzZaX5siDbr7as2HolfZNs8hUX6\n" +
            "bwyhWEKFsuajZ8pIOZPMYsjr2WnbfBkZg/5sJCJtleh8jsxeXiJTRhXY/B4QLjCk\n" +
            "CpGFs8cJXpnMuUEki3m482Gu3Qh9Ez/xLy/0SRjjRfV/mLRBpCaFc+JglfNiAOqi\n" +
            "lLBRF0Dr/c0vuDkTIebMCmhKXEdKKT315X3gcjlEar4BGnLcc62qgOgEUxj9g8UA\n" +
            "nU5rztrNupVSGlwQ454M7GoDl1NGdz7p01sx9wKg9Igfiv2bdfDZovPfd1ETiTUP\n" +
            "xckbx0eUgx+PVFAJyajH0AJKPEJ1Gw42d1gv47Zuv3KnVYqLFHMmzZezcUkVlP4d\n" +
            "nBPrhMb34WESm9X2RthgDqN6GorGskUzv+Chal1pVBne4YLhr7p6moNShlAFz4A9\n" +
            "cQvGVr4k1Aiqq8y4fUYgAMYORoQHoOODnVLih2L7RZaQLOmflVAYXOATze6xXzLm\n" +
            "tUVruEhcd30uM+EbFN71v+cikZe4fOHRQU4BbPwCncs+5D589AVeW/Z+dPPhfmzg\n" +
            "8KoT4F02B3w/I4rJKsH/JmFT8Ad6LUnUxDAiscSUeQmSV/iR3/URezqAG/Z7yrTD\n" +
            "Q/sFZ2O5WT9aY8lC8YrnGTAJnjB7Tq/RQOSY8SZmDB25WCboC6CbW2hcmosXrr8e\n" +
            "3QUrjHeo3HXdsEm/8L5HZ9w8cGxJUIZJDoTuZgFLSj+i5L6NoNbgDKKXaOGYSs8S\n" +
            "F4aYSpjp8cFY51NZvImTiOmZ6MF2YR9kTQ0LjL7NbIEh6ooyCzSDtYgvaOaYE7te\n" +
            "aEdwAFje4ghZHheN0bjCIAG2blKIaBH+O7ug1rKZxi4Ar6fxnVKQR+2zLSGfaPvc\n" +
            "VT0X5XJ20DhFjBN9qEmEVrpFGH8q/wnNVo8hoE3ALaekFV1V4IETCcIdkxmPfRaj\n" +
            "jHKZ1kHWhy1X6YCDNln2aXS1/R1XY6O/Fdc0n7ZgcI60ayT4dcjOCNjnobVXALUb\n" +
            "jL+oaOmG3GBLq+R8GkIHkd15zn40RhWEEBNucKdUi0cS2oB2ApnuRjO5Xljocb7J\n" +
            "0kS712QU9G2Ex2PoRaNmyDotltPKtF1uOYoSJSvDcWKVxUIouITzjlkxQjrKScL6\n" +
            "1xHf/bOhdrE/2y52tCLA8NKD7elvZHFV1IHOElh/zBV3f8S3lrRR3K1AZwMX/UFx\n" +
            "O8gTXid+vOuISMzjCwRihihoaI/SbMQSi6jwyr2Wdux3164KAZgYRK9lSHA24l0d\n" +
            "inFaYZWCF4vOpz1bCX3BzEnmsmTo/h+pHlf/XVS7EDZCIU15LrRLmVq4Q/QOZYcM\n" +
            "ocgZMX+WNLuzWOTT/jMrmRN+yVn3zrCVgqRZYYUJOR/h6GIfvcZK/2xGUO3N0xvf\n" +
            "kO9K7Te6W3DiZJ8xzsiAz8SeU3clnKPu+GmF8/UtMFX2UX9sb4vgfMH+k7iF6AT3\n" +
            "fuMdDTO6f2f+EV695ngn8mNhZ0r5JdtqVPtp3l7krK8hh7e9QtbDZsSD9g03FJ87\n" +
            "qvGNswUsCfQBWzum9aji04Em72P6jCx1LlgU/Z6fdMRHTtkzjmoJ0TrkB0EaF9VN\n" +
            "SGgpQrr/+hguB//jWAOYgaPnxEa/uaXmCN61keU3B+QosveWS2avQX8fxO6drbVA\n" +
            "2QYKUPU06RRZOWASy0HZ61aQuFUgK0sMoH/LGdyz58RNTxFmuvni331EcZ4rwb0t\n" +
            "0JlQZTrGrN5hMHCHej+ZmWz8q3ElLEENNchc7vbKvgFK9SAtGmb0/BVI65RNKjSs\n" +
            "gbMHq4zgL6QRyvdz8+fWpCa+e4E30+EoZZJ0OA6PkAXkLVyNPCtiKAhtjfSIY+8r\n" +
            "Vg+W8ZD02xadt8JvE65tYjBQLkKQc56vA61pWnCRM6+TycqPP4Om7aA7adAOcI9N\n" +
            "eeRJ1lw42655C8Upxfc+Y6lSQncPoxyTxoQkCv8cnPMqxuIvMy8/FDGtqVsWTpVZ\n" +
            "b/bB0U9MEb8/2neNZ93xva3dA4LpCcw9BkbN/ZNvnDVjrGafyO0h4Y1C0HP9Bx5P\n" +
            "PCi+gMTgT0bIvk3HAlZQck60ZwwfjNt/LJFRxSOrZ1e3jjXzGmath3473zhSdOkC\n" +
            "GkFg6BLJAAMDWNkaH66dk2snjjADtZRU4j39kIGATw232dNwwy77HyVFi2kv3r+a\n" +
            "kTk2aEmmCJBUf2WkdkDSn34WY3q7qNGFrlw3MV3dG9mK6LmH19H40q09fNCzCNAb\n" +
            "RtFLg6a52VdfFIRGi+W+4j3qph+IS1uZ7RUhqFILY4y3LL0h3dkH33TAfpftg5sm\n" +
            "s21++5euT53XxrhZPSW6LGGpix2fNGZCgkx10SMeTdjJiOu2CgzyzqYG4lu7/RgD\n" +
            "qunJ4mnRe+79XFJGSH+9j6uvQniqU/KH6LXh9vP9awnJaMvHkwuUXvXytwOLSSQt\n" +
            "TiPmNPsBDDLLnSCTfPXEAWa7KSwmJFqNLCgLrMaTlXY7Z+fiiyAbDq5hbtzFyGk8\n" +
            "jmd7PqX/iAEFrNlxiwDqv58FUoNh1dw4DFYJWjYNgtMxbBkV+OILJZIJsCzRKsxM\n" +
            "mzfEcerTl45polG9DcO3r0MRJiHI/vW2oZfTtjzecARnsGqv/3Ig52fnXMbsWp4q\n" +
            "S3MgKZX8hGSJjfHptg4cb3+7FhKaQrQNEIDwa/qQNa7rdtO7ciLFRAt/AD2N5EKx\n" +
            "uZASnCKzmSmRnOnxxxOa8FfyeIw9+O5+N5mVMOETpQMWoHYo2sCoovOeNs6n9ee5\n" +
            "ONKRgfwW3iMr98++SyoDFwGLvb4wJPjfRzbrSt6JAehtok7aGB7r0UgJz0/22YS2\n" +
            "0q6hCFBg1vCsz9gyUhPCMdry1QnjepziCgpu9Im+9cCa1PCuDHAss9Oc3HEb1pQY\n" +
            "mnS3b0Q2wHl1J2T8COr6yMCREPvOo5HcE7g3AwgkRZbK6TxukqmutrvJzBFBQklL\n" +
            "V2p7rmiwus0IJlaBi6SpI5Cr+gAAAAAAAAAAAAAAAAAAAAcQGR0kKA==\n" +
            "-----END CERTIFICATE-----";

    // "/C=US/ST=CA/O=Test PQ Intermediate CA, Inc."
    // basicConstraints=critical, CA:true, pathlen:0
    // subjectKeyIdentifier    = hash
    // authorityKeyIdentifier  = keyid:always
    // keyUsage                = keyCertSign
    private static final String MLDSA65_INTERMEDIATE_CA_CERT =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIV4DCCCN2gAwIBAgIJAPi1Y3H86JH+MAsGCWCGSAFlAwQDEjA6MR4wHAYDVQQK\n" +
            "ExVUZXN0IFBRIFJvb3QgQ0EsIEluYy4xCzAJBgNVBAgTAkNBMQswCQYDVQQGEwJV\n" +
            "UzAgFw0yNjAxMDEwODAwMDBaGA8yMTIyMDMxOTA4MDAwMFowQjEmMCQGA1UEChMd\n" +
            "VGVzdCBQUSBJbnRlcm1lZGlhdGUgQ0EsIEluYy4xCzAJBgNVBAgTAkNBMQswCQYD\n" +
            "VQQGEwJVUzCCB7IwCwYJYIZIAWUDBAMSA4IHoQCG/NFMeQdrYEJS9iGQOXtlX5Lj\n" +
            "9uAx1J9qSFaLxZEZfKKrIkTrrA9I7yZxg3Y44gKovbQ4Jt+vl+QfO+b0s8YT3yNn\n" +
            "wMoAqFQV4Ikt3nGRxWuDgXDD5PPSIuT5qgXTD0pLHwBkETW0eulNZ2xqyVWpQCRd\n" +
            "lvG4XmIH5WpOhkxM65fmzF5PX/nUQW9P1TqbZJPXX1Yp3hPS4NfReGSFRA+U6zot\n" +
            "obPJEzLsJ17TganmMZDZKdjQGw4oBuU1Fx55UnaTJNjz52E06MQDwiyO+ViIHlnx\n" +
            "Fc2TUZoIQT7FIIs1LNXoNUWuxu28gTbuqmSuYS7EjQ1mGRiiUPWc4A/Qm5hDAhLP\n" +
            "GGc4ssdnq0vcUK6k5YbJcyDQKsFPNBqSpn3YMe9RfwrQgrd9AQM94S5J2ZHv4Fe3\n" +
            "cemWEIhoeexbhenIaTlRtaqip9Oj/vcXBg2Hfb20E/CPJYm6n4Z1eCNz6yHKsyIh\n" +
            "ou+UZhZ+PTtOGD/uZ/aSfEiOrSPhlawBontCkdhMEwSSvx+3fu5ePM60WXTjm1zE\n" +
            "UMp2oGhz/cL7FP2w/mZ8i1Qoc9WKLDOMSk+wpVMhgk5yDp/z6zsZMA9AHnFR32aX\n" +
            "P06eld4Plol2QmHXXcfkzqjd9WByAhWoaPfMiZE9fD05MATIARq+2iTk+mY1n3G6\n" +
            "EoZvowlfCO1iAwqejjq1JuV/MgqvrYii+fMRgGjAtCfL/jy0Q6RxBY3KhxmcPVme\n" +
            "AsJrXYnGU0KV6tyYVwe7scPZZTJMHWQMhz3oApW3Ly+VaqPstnoPqvYTfEhElZhF\n" +
            "K910DOfXxM6g9c2Yf3teKb5o93nxMpG1Kv19wDtgQLUKYwISSoDnSqEhkZmZQ5VI\n" +
            "rpLz91EKw7BhyNGfOIEBw6xCsKNEpyngQYwK1x7mdqeu9CQdnkGbZgOK/uPSmCfm\n" +
            "pTaCt3/MnzqwLVd9oOYN4C6FUPblSO0hteApubXlhYtSiMv7kIOO16LVJ4HL1oSO\n" +
            "EtDOSXZv+uV+JjFl85R6Dqb6iI4byPbJEhvg+0eG5j2dIxrQByf+1E4zdjJ2QSzo\n" +
            "dp0oInMarsGQ8i6WsVMJgc45zhJlY+rVJJ/pK0cXY2OBlmfOlBiUZ93eJ6oStjRk\n" +
            "ZkUc2Zc01k+5qKLQItgcWlXM4GKydGck1+OkN9y+KudsOR5MSDhpYzWuZ1w5xwZ/\n" +
            "5u++5fogH2K4Jr/dSt2gfzEPhAqx72OHuXD5aBu9Rr7t6NsJ49MEneowidUafkiN\n" +
            "bShj06HUUrdhMZChrVilSHB6nENIxhV1LJPB9rmqk3Y0FPOiWDPJHy5OFtWsL99H\n" +
            "0kGdPevSjm5xxLty6+M9NaST8iDrQN69jVsbQRqXee7T0t5XVg7U7mv8SfJWPdxW\n" +
            "TfbEeYTiOIxkqRQWHUAHKEDeTXnuLj9BajiJEGjK6SDJdx0jt4IUK4MNq2hCkAEp\n" +
            "5QVLsK5C0KZinUEpmknrNuMEJFMakxlFDaELedZT73hEt35w82JFo3Td0QNburSX\n" +
            "GTqkGkMLJOIuGVouRfMcSVBoo2W9B4J80ZMjUcNF/g5KOdynxoIQptnk0WFtDpDl\n" +
            "KUPako35b9CiYsVOLGLYd05dXQ98FMTmAxWdKEggjmYeTPGT3b3L/fAwmbs5C9oY\n" +
            "bEoCRrsaBA3hzsue3A11j7Ov+tsW08lHyjNnMowniG6BA0GU14A2pYbokXsuIlxv\n" +
            "2D7lr2/ZLn+T0ZFWM+YyFCXkTrVsnRjp2Xb+2ykMC0u/AQYVITCWvcnfXjbIIHjO\n" +
            "Y6pEZs2zCNncoqhwsGgYVQXbo+7R1DlxKUY7x/KRtSWpnjCx5J2Il3e8jFw7nilV\n" +
            "aWqmUorIMGzbkqGkNYWKey5M0s/hgNU7iRb7m6iSW9L5hZx7uN/oGb42uspApeuc\n" +
            "YJT6tfVUHpsQyZfn6MZD5zK9njwNQ19XgMjcGBdtGARo9zDsuZVpI59zboJLrpXj\n" +
            "e+qGRZ115V2+7mSgmV+jl9uRNdYjtPHjCWliZaEXNVmn32losH5A58ip1QqSqqS2\n" +
            "pEoZEpj0j3Vu4JtmVHCMQy4QxY023PnjPu7QNJoqcw3xGURcP9fyLdbCm4p0mIr8\n" +
            "xHwhiembpuoz5LL78ESKim4sA0z/13EzGXoNYaqPPUwDv5EoFSBzB/S8/VqHHJVC\n" +
            "N5Jzv7aFmQpX4MYQvgEPcfbhDxcVfGNQxpeE2O3kNUDazL2EXDG6P9gUVEJQ5Ah4\n" +
            "UGcKJm5KjZUb/iz2eOcanDsCY4+zF2lDaIoF4UZv77yKpVyIDI/p58EB89goieYT\n" +
            "ott4HtiPktEdxbtgF68ztXXue8kgkSyq/rqmHyWhIFUlo9YUFosgWjvJ+P4XivKe\n" +
            "atjPTDcxjzLDu+DwSclVAtdBbh0oz+laf6cTr/8WuskE8jvSpMThFIsb6qmx6RNY\n" +
            "YdVQUU7r5zfAS1D50IkEWv4pcjSCqgJ8kCO6NB3YUd7SDm9li+ixZVSpkYlE93Rt\n" +
            "2MCYvmee96ypivgswN4yspyKVt0pXPnXKdLCLJbmX3CLvous59jzi/+UFzpbgjEr\n" +
            "oUUOFKB8DVBbaaN+pVTh5b8mvY9RB5B/Ceql61FlgOigQVwb6AsWDyVNeMiYRimw\n" +
            "AsJwOUAxpOJcGGvh96NmMGQwHQYDVR0OBBYEFDPAGGMC733d7UjJwEIoM6DuBtHS\n" +
            "MA4GA1UdDwEB/wQEAwICBDASBgNVHRMBAf8ECDAGAQH/AgEAMB8GA1UdIwQYMBaA\n" +
            "FHWXux8vWJe39ZWNJWdyKuIHsrClMAsGCWCGSAFlAwQDEgOCDO4A1w3+soAmpZC9\n" +
            "ybwI27vcrSKMtS1/0KDmkjogr7Xu+Kja220X+1hjQhffAE3GSMVDMQB0urpL2sar\n" +
            "dNFpsaM1dVNq+DExGv+d4a++fXOBSqZCFXhXDPKQ/r+qYcRyDXEjutp7lr3xVdJe\n" +
            "zlEXo68synXubtwvhengyNhhiR6ANe6wNxldf4NS5HrODhYWl6gkbvZC66EXVa/d\n" +
            "W0qdEzuM9YhSJ0u+OlcivXg1hsp+uf37mvZm+xc3iikSk5AKrEuq4MMkWjmYI58J\n" +
            "Dp8eAskW1+KFLjifqpZqGoq84hWU4FvzduLYKu9rg8icgpFigJu1pmmrXPqWI0ZO\n" +
            "ctC9i+CW8W8ZiS5QvwAhGeinwX/4VJ8tdHNydebkntEoeufqgdIVfiSKa38ymIBn\n" +
            "xnlFJrhvGuJNiqywd+K6RBY99G72BmplaPTIDVYqphHrE5CdFps4rSL494YqZ8ir\n" +
            "Nmfik4OjpGDFLPZbd7Qr7uTowBYDgfvdNypKduKzTSa/8aJDI0tvOc6zhKeLG71b\n" +
            "1F12MBs8aOIqZ1Wx+fYfT2f4b4Wb0S25qzg5jUVvrSYXx0ho3WVXQhD8KdtSyoGi\n" +
            "3i4SgR+x+B61pUVXtpfY0YkBDKDE8PQzBvf26bg5Wx6RYuNY/jlMVzqViuJKeDWR\n" +
            "MNKngbGsi8TwVE4ZpO9ilnLP/XT2n6ubHXA1+nfgodvHLdiykHZ4wKoSvBx0Pj9Q\n" +
            "sFTEZ62k7AmB+RieAjChvDUQJ66cuoAIEtOQCS74qsOOzWIf2pFjXh2//5H6g3y5\n" +
            "h2UY0RkuY2IcmD9XKq3Ls5qX/9YgnQFHRQGZCu+TYCvvdNGpDG3YcU6EcscNfAPU\n" +
            "uGk7yrZsQtaOtOeML/I7AfaqaoMVJt4UXzaKGl9CDCHfBQoW/NEmd61DTswbR+kU\n" +
            "lBurJ36ep1dzMZCz1GgfwTLJO5m4ddL5i5+9CD+cdUbVZRkJZgTdH1eEF2hTgst4\n" +
            "K3OC3z/kfllsaP/sRn1s1C4oEDUsZA+D0KQbvcwNtaMpPr7CDz3wwiLg3xeGCi0G\n" +
            "DLqqbLjanUv0PZyVPzA55JlKGZMONv5J8DJMs0ktCa7mmXhDqmrwHYXnIOV6SJOL\n" +
            "HKrddX+c6GgYwXd1oGAOrtyBFgmlSWl3zM3bcZQvaA5/h9jajhQOmX3H836n7tQo\n" +
            "lGGu01ZYjdnEyhiCypxnES4Oi8F+76COfri80gTc5vzF7l/SsXE5a4Bsk1Wh+adO\n" +
            "O1xrAkoXDXnjBFBtBAMin7iUVkvaO+p6p1+IEOwWEZNdH8i7yWLUVjK+T4QvqxYF\n" +
            "uCch+lNSnqRfakm9v+IW9MRNuTIT6XJSQHDQnheJvq073LKaOrKhWHRu2v12olvU\n" +
            "HDBgJF37/R6HVXG4uG719QT726QFstdJ54kRKrHqrPWJq6VMS5i3NSMlYvoXjni+\n" +
            "4K/IMBJkfOGeH/UX4jqdFdhYXB6YhitAwhn9QEJm7iEQmSRwSJNFEYlKXnPKvwNL\n" +
            "z+i0cIXqm25zGUcDPbllhd0lHaibQA3cs+FUXvc1B6Xpxk/G294YOLeokT9dMWwg\n" +
            "pIl72qTkIJWa4n2u8EzwxTVT9tzdovTOIsucPtFt09xwr5X/6Iq8xPPI0Iqs7zHX\n" +
            "wjM/Vq17cCwA2fAbQRMs7+bDCxrZ/2Xevu9/gCyaMvm57teI4DcVLY/sqQ6oguw2\n" +
            "kOA4LMWswBGa13MhnhdymHj6724q9dIXavqjkVIaApIFOZHS4wDzeTK1MquiQEHk\n" +
            "HLlJrb9aVwVeekPaIWMJxE/uENPVGeZuiVW27ypATBBQU5vBNKgR5lrtazDCs5VA\n" +
            "/0jGMJD15tF10k+UZcd6FFaWWBNwy45dj1v6F9QTRH9umTeZOofbH4ozwdUoXACm\n" +
            "W+QU7KhwHNQLX2VzFULrpJWLtGLqA7YtmUftMWO2UIG3V2uSPNeZvvCt+iX2XNVu\n" +
            "GQdvzbH8OaZUU/2VP0d8TvFGrUf/i8ATEb11ESu8lq3WnWxJ3RLcR/4qe15xo5LC\n" +
            "NiNBvvLDAMdwC/48WdC8dauRzRG3+RPSELpjEteGM/xHjWggwY2n8SMd1Fci3ey1\n" +
            "chL5TMqAJfBa627CDfepHkwthG5dPvC3/uXhhGgmnl6oJWFrEplGOsPYlf41aRGZ\n" +
            "G4CklXZ98JmNvk/ALN6kz/goXz27U+t9fiKcAHgxRJ6tsOQGOe+IyR+Xj4P+lQOM\n" +
            "4jleDJ5L//C1bgMcAaEKncR4DYMOpOomN5cif+6dIDVcfcYXy+p8Et9ohgoocQe1\n" +
            "lKridbOe37eErzB2F3tzbCM+SxiDvC+FIZ+9bYJOg/XKucTapBhuhKemWD+4KnC2\n" +
            "DWmoyZ2vIUHQVfXHQnlNHAyMCVIfRtD3c6Wddj3D8e13TRsP3Q5acGq+EP/iIa+H\n" +
            "RwjI3fPf623aJ+eW0XT7Ti0XIjUkt/g6Ls0utDN2x7MwAc4lzxAVn3I5Gw4gJCti\n" +
            "EvKNDtNV2AfaAflGI/HGI4VNa40mxSxgrQsu4qjcokj8I+nuwL7zk0jz5CoPIMBm\n" +
            "rgV2CLwtNHnEt57MGx/4Me0CxzPoHPHcWHbuPzt8rI1OCQYZu2c9FxqcFR4L3HSS\n" +
            "7j8zeIpmCLemmgKxga+6EwsKFEqoOg0gErxYXTgvIb0heZOFl23uE3h29BjQsmcb\n" +
            "x0ieqL+kIDm6byERB/XZyOZKwa+pfNqrqgxRd+UznREKzGFCY13wEP+JeMb6boML\n" +
            "MvC//gT/jFhbWEybVWYM9gcrPjCXvCEmXWrjxgcxME9IOuwkKGDcBsmosr/PBsBI\n" +
            "4mgHHisAw3fwecFLqhRn/AtGn8AzZC/ETRfhUI9osi/CNZT1o3JRI+rb61uacd7w\n" +
            "s1UVCBlYDxZEFl3q7fsBCMKVCGI81qU3mc3dKToMeDLbDNGh2Ermn3vhZ9vM/JTc\n" +
            "hMHa/p9vod9tjKzrl+oY8Kb+LxlxCaa5IWppq5GCzwWHpbqi0iw2y4ONUZbTzHfZ\n" +
            "4Wf5POuqJCGQl81q/L5srzobiWjlwg8XZndkKkkimm+jkNo0PHw7xKOf+4iRYjDw\n" +
            "US94riu114subdTuhOXFqvIk5SoZwBv4Kbt7vkIabMyZQmotovkjkAagbH1QXSBu\n" +
            "DQb7WivG7vXTnbJMYHDi3w8byAT9gFCZeb6QNnUFx4IiYE+8GGwcS/hDdCqPW1xz\n" +
            "gIcXFtFpaVvlc7lnFeMzlPmkntfz5GZ8IFl142YUNjGI/XzHSpWGxKYgqzu1A09U\n" +
            "96oDnlfbTgI/9enPmSJ7TH31EpcYCel8YTcyMp9JtlovTKcuIRPKiuWhCH6Q0WGl\n" +
            "zyPDXD46KDCnFpnOPyHYTm8etlmd4b5+cp8bt1cFWYE2YGfpkrvdXgLqqalk49QY\n" +
            "v8dPB43HYWrvl/56Dty6amgnr4TBq6/6l5z+lI98IODyMAhFPizEeq+B4kqpeDp8\n" +
            "4FIq4wywSsF9kXSCR7wc8f3i16FRhRm7Da3JnzUsjoqaJgqzRmeQsnLYR12btGh6\n" +
            "8IOvmHQRoB+JQWUjAKkvAzMQpMP2DJ6TxOAXhW1IwrBdin/Jwo0WEnkiHMC4zF8U\n" +
            "kKU0LtenH6CPMuTJnDE2Kf2c4FZYvPMAozy7ZVESkafE9Dxcd01MtTJ6UkZm2ulz\n" +
            "6H/ZZSc2zhym+fek2Th5hTzLMDhZqWtPdGWJaGIUfQ+zKof81FYEkkN8ErU5q4WX\n" +
            "wtqvMGuFEVwSJQ4qBlHN3mYAbF3l1j3etLQ1C6A/LfuMrcET8+TXy91Goj7dyvp9\n" +
            "H2H9MqsD7KyIkCmCSsaxqyVCKc/sA/syrrjlyBsOQPr24Ouakcli/UoSYPxzgT5d\n" +
            "YLikdIIzlvlucCsfrrpBkT2iVMZiTwNXadSw2ZbTGRTb5PmOB22NOvKQWBryouBC\n" +
            "mOlhQqGUwr+uly7ihbasOzGOOoa3g0JAU/Dm7Dji6r/LyKAieEoOmuRipR6MkhrY\n" +
            "njmncrpzn/jGRvYWUm538OpkhtApf69gY9IO+LCIiYsM7tS3xtiHvNEVz5fKMTag\n" +
            "Gf98lDikGIjjjYnaaw9ppQjXDXlCcSNOMKFu9yYdyoWEm2P/bVVMK+cYPUfSkHR1\n" +
            "8h4b1kDb+5D5rLCHg4c9Z949qxu2D76bZQqaYQQudOeAXSWKI6se/p+sByMDNmrI\n" +
            "wpq0e7PAFF/b4dDh8xc1uEW57eS4brFfNM5aLPGN3RbyXUfl4yg0YFZ+k7XOPKyK\n" +
            "3foNJEhh+qoFZHlh+/ixyg2MU5h5WeglFnSc0wS4pPFWq2+CY5Vnte7r6dn5yLYX\n" +
            "/CumVxLReLhImyZNEq7X7f3s6G782wtDZZpL/nARiHNCqjKrHwevxzJX6P5lr+fO\n" +
            "oI/7X9PYcCwwrsC3s/7+WOIfLgbmSWQyV1xdYYaco8jfCg5BlsDR1ygxhqsMIUSC\n" +
            "n/8sbIWf3OomVmmcsMLQAAAAAAAAAAAAAAAAAAAAChEVGyEo\n" +
            "-----END CERTIFICATE-----";

    // "/C=US/ST=CA/O=Test Server/CN=client"
    // subjectKeyIdentifier    = hash
    // authorityKeyIdentifier  = keyid:always
    // keyUsage                = digitalSignature
    // subjectAltName          = DNS:client
    private static final String MLDSA65_SERVER_CERT =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIV4zCCCOCgAwIBAgIJALZZBGwGxYq4MAsGCWCGSAFlAwQDEjBCMSYwJAYDVQQK\n" +
            "Ex1UZXN0IFBRIEludGVybWVkaWF0ZSBDQSwgSW5jLjELMAkGA1UECBMCQ0ExCzAJ\n" +
            "BgNVBAYTAlVTMCAXDTI2MDEwMTA4MDAwMFoYDzIxMjIwMzE5MDgwMDAwWjBBMQ8w\n" +
            "DQYDVQQDEwZjbGllbnQxFDASBgNVBAoTC1Rlc3QgU2VydmVyMQswCQYDVQQIEwJD\n" +
            "QTELMAkGA1UEBhMCVVMwggeyMAsGCWCGSAFlAwQDEgOCB6EATRbyWHPJ9kiVq8Jw\n" +
            "W0RBPgSXid5n4k1dfIMSrl3ksFyQYXNeiG8HWTrn3mqX1TtzVduedSq1WVJZwKKd\n" +
            "8/LP6VNUFe1SK94iiYf7ZsPlieGghcvIcKddBz5+ftfzAz6FcELv+htek2HVc0xC\n" +
            "X9fMTPgPwpBGInTPV2+Ca/SPLx80Uts7TP61Fd5crdROGW8N6EM9kMFx1AsEgztZ\n" +
            "3nLya+qOypKuwypbwAFeyCMEMvWuhSr/4YEoKLX1+IXq7Qt4rs4l4/jwa6/Dckwj\n" +
            "g9quQbWKocs15Dqn5uT0Myh0VWAwu0snXz2FUDsLXMfDVf2E4vzSZ0xl7drsnuih\n" +
            "tG3FhjsDj5/LOe3wN2P0tU6OlwsKe9l6q0tK4nkMqSSfouc+GQtMPvk5wMVqWOlF\n" +
            "qpcN6P0+ks4VNwz8Biw7MY1Kl8k1cTw6msLt1JLFlexSZy7dZWtqe5kXqzp7mKnD\n" +
            "Ed/OJdqXCHCpIez3HzCSxp0YHA9uOu2KI7sQg1BW5tlxjB5YYWK/Urkf3sZgSdFQ\n" +
            "ZsW2siqa4FzTmEqTQG4GsTX3FAfhClMHqWfXm2NA3B9OH8JVrPt46NQigysuqo7w\n" +
            "r/BGqDjVSlU6LjoGEj1weQbYnKwnVzAynyrjxMEvZvRryVhF0sNjXkpqAA8ggf6x\n" +
            "Sj0OGEdoqDb/rAWEqQ9D3yZ6MQVJPQJd0FDwoQwrepDudTlT2kZYQS5EE/fre1BL\n" +
            "iL+Hd7Msg57tsXMq5lsNJfjj4yIkFgGWofi1Kht6mMiYJ9tWvjI7zeMeTsXQndKh\n" +
            "zJkql1vTImVtqxwrTr8yxJIC6UQ9qH7MDpe8I3z08nz/Rupj7Z6Wgf+1iCdm8p7Q\n" +
            "J72hBwH1iZEQpmPMkrTc2ppqjqijV1PcW5DGdL0lNwOxLPpjMq36UutDFqfRKgEu\n" +
            "NY2dBlyoVV1seSpHaH/KM2x7ffSoBpoTKqxN0tV7fwikGm1/+OEyM7G8jbag1dW1\n" +
            "malXUzl+OsmoR8u8GmlsF1ALo8/BhpoyAzXOmpPlMuPZZYVqCJlK1MggZvpJ/b3A\n" +
            "k2/y0sRF1o+sckdiOrl79/EdSGYwUcRJAP2vePxTbRFTK/LMKbjVlLFCQAwc8Cwu\n" +
            "wyuf2P8RVbSCU+K4/1v8qkX3RGl5IZA7AQy+u4qdyCjKZkRcJgMfFczL5xp+7fcB\n" +
            "1Bo4I5fMvEJJZJubfZdQ1MCtMS8HSYjavOsu7Fx++I027QV7LRPXxyBa7vRWQVim\n" +
            "/d+vMQvNTCdC9SRmDhZ8X8xRPKunyG+2JjlnqrwzXAlHVWKVncSm1eOboDZZ8jN+\n" +
            "ugmuE4MMteOC0Prr9ZeegS6Ow0Wh6l0H9agSyQtfqZI7sGZVldEX0NS7EUf2PVD2\n" +
            "1QR++0z90yO1/sXnEQ9LlVEUMt+HGI2wW5ZWs7HFoeYND4t7MZRgllxospOPMO05\n" +
            "Z8lErfKBLKJW9s+bd/KaHQXyQfDgJqeRrfeh3R14pYuCB587HKnFvkuyB/Ir7++y\n" +
            "xGQ3s45dKEXEnSBF3kFdIj/r1m9OKHx4sRYjsMGFjWX5xObE8+aZRgINimsW6SQR\n" +
            "PFsZAmC7WQHTXuFq3WrTZJJtRZ3K7WtIxBQtRlmHdX92xFDKjx6fmrz/COjnAGqm\n" +
            "MYeUpjONWFEOQMnqM88JOrS+CvNs/EeUAIPUyTmtK164H7pxBjA0uvA0avKipv54\n" +
            "lYFJZvlKVydItEfgnLN2X+x0lFI2m8FxMxPtHLl2wS7uyOjl7ZQTVqVj68lnXBrc\n" +
            "IiNkrK0RYor9cxs2ROoTieEuOuEAi10NG57WWbi3Ex/HDtvrCPzy4HKcK4hLiUTg\n" +
            "Ckj6mk66Wgqk8mtuitoiWi8TsefWX4Ydb9M+LPFTPVXyaDNucnoMR1t1CJY3hi99\n" +
            "9UDLmYcZ73KAODLQ1+vQtAa9XsAB01Iw6hgjAsPQ7w9wDp3755j4vDOk1rDY1/bC\n" +
            "hEND0mlQV3rmZGawyHrXwA14Nm5aIxxr3mGXsJImu6dWucAAMBPMPGP7i5u2PPre\n" +
            "fK0q0GHO0Ij+CRNAexvmYbdORELBXUhR2M+hjABJZpXLJJysShgshOIipaUhmiha\n" +
            "3EWjf3W7tSZJkqvgRMWBo6rsznwLhKH36O1sZaOu4DzmCM5VIOAt4NSK5AIHVgqv\n" +
            "4tCHeYutARYE+julltF2YpgSo+YYx9rEcbSmXhVz5thCrVRObH1vA8IZNVC34YSb\n" +
            "o3dh861hktKDk6tEH3542lGr9bZrcnJIYMcG/04BaF59mYaZxJ2UI4bc2Vkv4hTf\n" +
            "Vadp4C08irY3EmnbfnWKNDB6UEv8GSjSqkazp0XMf/neFgNlJFjs19mv3CzZCHY2\n" +
            "mje4c8+uFxDp1IzsmThnculT/+sTtFoTwpbi+OC3/WcB/oXROhp7KeAIOGfAeJ4L\n" +
            "ZHFt7fqzRnkeLeLaEvQV8PXw7ZkfPiptVM6S+gebMF48GNJUuSvjTDquHCyKmZ5a\n" +
            "RrrELUUqKAI2Zg57+2b4VP7c8W2y/HUzP/r/e7Lghf3B78F0Iid4HseLLoSDSawh\n" +
            "2/J4JFx5UJK7HSfCwOnX9r57YvZzKnMkFSaRkavcJsWHi7fc2IiRudvAYvQ9pT8+\n" +
            "Ty37blM/6a9J65vTFu+3lnB/36ejYjBgMB0GA1UdDgQWBBROMZfSWIng31Zeht7p\n" +
            "swRsZCaT3TALBgNVHQ8EBAMCB4AwEQYDVR0RBAowCIIGY2xpZW50MB8GA1UdIwQY\n" +
            "MBaAFDPAGGMC733d7UjJwEIoM6DuBtHSMAsGCWCGSAFlAwQDEgOCDO4AlS52BD/r\n" +
            "rKC9LE9LR1pdLhmYIN7N1N+jNbAq1zJsq+ePlgMxcnTIjvfrcK+A7y6Pm1LMB8fS\n" +
            "/LJVf/ORXDMyGAwpD0rH480NfMDw0j4t/EwAPVf/Kg+UI78W3a7DNyBxToV11EzI\n" +
            "ucbHh2NR4B9T5EtJFrASJoEfM7B27zWqjdForxTBN8Bup11c1Wb/m7SyQ+kbRbH1\n" +
            "dLYMOknXgb2S4k1S/xi5WoVLQ/gs8RHWQZPN7fcd+DARrMNB9GPfGNoRhwopZmco\n" +
            "GuRitlFKpJmuA6XPRRzGQY2XFr0mZMeLXlGpMkqxwgFd0lzdeLRlYxhJS+BnscdR\n" +
            "pxFtNE54GFG3sU9e58oQiTtUWbyjJaqalofGW4l9cCGp+LcS6cucp4Y7/9L/k/5h\n" +
            "YYNPpYKWorrlbnwRCH/AZFiaA/rRA2QzPiExSLKxCU5iF7yQbysLB9JR1sK0Xm7x\n" +
            "v1mbb+s70prJQLXlRBkLadjI1DWzjp3AG0yp4gp59dWSA6PpphgQtC+rqp7UD8/s\n" +
            "hsqKHi5v3tOHIeqEWza6QETMf/pPoiViWOupyJycZPlb0GQR61qwf63aIXPHVecL\n" +
            "0L9C4L+BCcHNcR95Wh49lPtR47Q2Iu/znHGSQvdsrL6eCiIb/1MxjlpsuCQ3PZMX\n" +
            "k55/UCSdpvP+Czb/eIipVi5wHjbHdjStAjAWPj5dtb/8+5i0iH+4cl657qtO+6Xk\n" +
            "9D/ZXJdranpPIjvHrZQlwXOj8XDr95hwllGK1NKsw+5W9muNv9yRVtLOEnuEiusl\n" +
            "eiuFp9u9Mwo3V0TklPUjk2ig41lBwwWd2EV5afXQPaCYt1sqJWxcHK0VrZ6a6Ha4\n" +
            "DqFlmBL5QfPaLiSUpdxkfqGM2Q0z9Z2BX62NlRWHRQ5ycm4uMBHWQ6blrCfZpd9r\n" +
            "GvRaeuYGnGCmUIzGFBbNNaFPJckNpaguXynvqmJJp47gPrYI18mu27zSQ8sok0H3\n" +
            "FUurNRQEjwPlSJSgvihRgAIVtD0NcE3OiLB2ElVznGM0M2VA0w4Muf5rVH169h5Q\n" +
            "CYVO4wugcvbbn7LpJuf44otBDQltovofRDu2AMt4XiLuGdBjK/J0PRw8Wr2v11bI\n" +
            "xoH2x+WtLGFpgCUkUTqvAn+1YRHUKoUjLIpHZdbNjkb21nwmo5TruLHfrx24PeS6\n" +
            "7tPD1pejTfWG+2HLDIHq21+XZUavYphjqss8717g68DyM0ExidwdRr/s5G0EwOm8\n" +
            "hwNjXq03y6L9wKPcILrtSDytsDA2eT8Uh0otZN3kwcvEa7mjSB1+JpYPsxhsflmi\n" +
            "3yKFKl4DGoKU/5OE4kzopI5zVeadN/yPawi9BDGW/mQY37zlDPDmto2BIVYXi27c\n" +
            "o9252lU/338pm0aVOhIZbN0S/6pvYuYqDjpzAQ8HVz8v/6aICIHAvwuV/FaRcYgj\n" +
            "IxdFgBQe1j0AwjO6jpVCTjA/NiBzMPEntjMBpWNpDqiOrGMuwPt62Z58an8zevHl\n" +
            "d34fkPC5JDsr2Z5G3keZfo76B4Pa1y9weXAZewHqWrvwn5T6WdmzzpOROZHINKea\n" +
            "aSlgnzRVVVYrsDBs48xT9DtnxmWbPzjyZquHftG0IdSWV74JUAkSRpI1vaUix9bL\n" +
            "+7P7ifgEt5UEDi2YiKelllf7EZiA3t0wxTaBFe9h7tmTLSndBpFOKwnyaQZ+pk2k\n" +
            "aC5yRm/w5VW9tyMSu2D9JCdOKGSvMTak8VwdWYMOaxZJJfF35IhExTJ9uOGg/d57\n" +
            "9kWXe43bNwyDxpDadfwv/NiFoEBNW83dVDbb9+80WZqBykGJ62S6FxQb9W1mf83T\n" +
            "PdE2MyiolHzPKeCqAA+zHMr/ldG06R8FhP4Y0XBk535FexVQB+7HSpmM8nxJnAJN\n" +
            "Vl0+nGvrExKElhDzGmlDo6QJzCl7jJGY5wxuvjZyorOhDu/iUKS3TAmgCHbNbPaS\n" +
            "jjrE/xHgJmEggyj2JIJ/c0upuQkSnmY6lNecvdY/mj3Q9hCBfmZc8E1CfMs+y/Br\n" +
            "mnG9R37eq+YjgwOPe7h0Xc4pE7NFDNVo8WcK6etMOwQJQGNC8cKH9839TV1zejOM\n" +
            "HGzVrT0f7tMNJ21SNF8TS088kQTJG8zWV4VtArjA6vSu48LMEk2hdcnIJfs+6RHq\n" +
            "Iev9MzEDUC5eHmJQHuoku5nKfTcamgGcGyJr/1l6y8CJrniHmKMxK7IUYbq+sVdG\n" +
            "IxUU7UrF4MOsrMOFnIW8akQuOGy/S5xviYt1GB8bJzBaZ7oo/EgfLxZS/ZEQrZiw\n" +
            "KVHRMEdXnpn5QfHhhHGxRgzjAyCSPfB8frSTMRgzretkncHoo98tBXTl7JIf5Wlx\n" +
            "y63tv0hkL+HABwHEP1izVsHqUh6Lxbonzyrzqzjqo1wW4IMQFrFXQESSOWgH1HEp\n" +
            "0F98tnwd+1v3KnJ6nKQGOOQs5ANPp7akE44I0dsEqw8OHo21RVDTo9WbM4uaTFHU\n" +
            "OAqPE/V9zj4qkESNkrcGp6dNDeHZZVcC3/zrBgy0aeRZ6pGL33uxDd6YxQtEu0Hy\n" +
            "OYQ4wNPhZLjVa8ygC3VOhM3Yy1PMRhTZ3V38AEQ7RHZrYkKk0G4C9FToWrabvcM+\n" +
            "5M45ov9DZb98yUYVTwixuoyJtEh4ZZlEAhAhuItpn03GCaJZvidPty8to8esLTPs\n" +
            "462Mf95k86eW9jAuaFwN6Hfh+41GBEV+Yf54seml9CBx9XkTlPLlM82dS9AYbTcH\n" +
            "4+xgkCI/01h4mFkGd62mpj0qibuHhnbL6v4/05eE+DjAACiuuX+4LbZiK7QVRkua\n" +
            "CbdSumMDfJ/QgKEsd0aiDNofPHoQODSwm4YRUF9BHiSUoPz/l1CJNdGeLXqOHecR\n" +
            "qsWkr0lgeCT5u4MnliA3Z8yq8aw/j2mletQGOfXpBI58WaDt4VnH7L2HJsRBUdDW\n" +
            "977vnv8WkrUIeIXaLhzJR+MGmYO/Bbo2bv0/GII0y3aUZeaEQ2DtYFLgIxyKtRwU\n" +
            "NFOgFFj491/qyHnEV2jYFZs6XXlu7qgXjpN6v48AdWDhEAZEhHnLCUwJ75ytbxT6\n" +
            "a9IBbAfu/ZedflqXyq4n8uPl1lm2b5awtzqzteuKyZgJcjad8rqK7VupK1Ovd9lQ\n" +
            "gpZvy7M3fS1Iug8P7McNSSEkqKf7nCG/7u/cY1E3Kdvu/N6TJCYKn6ml5GK0uDcY\n" +
            "20KUdD2xgaj21N7O9h1+Yh+FaDL6yTtAXkmhh8U9wQKYGNsIRixwtVuiVlP3f4Fi\n" +
            "E94uEh8ykjfQrEbvqDq6KRQswuEt6H9mTygyYG74lJ0IC/d02j52Xyv0P7cjAyB6\n" +
            "jqDCS6qk9KIPPvTMzhPn3StnSCnxvYdtNZHWCm085cdr6Pjjdxt4/W+Y+ome9BER\n" +
            "kGJqUp8MBjBt/G8nFqc1ilIfOb6zsAOjZf7Amr+ZmWO12RhikP1Y1zoVUQ/o1XqA\n" +
            "+Kq0ytpHPdwjVxHbbsOy7vBOb2mgneSqGZx7r1w3ZUEIU/r9ooV37KeEnYlpp4PK\n" +
            "l+yM8oB6N962ZIiGum/yVETmkqWQ7/Q+CAHmPnmzxQ21GR77ZbIszOy8pYRyr5uh\n" +
            "NgudP4Gjp6JPbeqlFyx6TQ8it8aG9Mq/Rd06oe5AAbrMikfiJGK/yc8VeQwYBSbY\n" +
            "86tTcAlUsQkci1tpMaAHAl2tfVA/MGtU88p4QGDtFmOIQfw/aA+pSUYUfHvkA2ZP\n" +
            "PQk9G8vgwMDX52bkkmqb8r7odKqRa67Zx0ytL2U/uXvhUWATrHJp5Vzi3/rCCM5s\n" +
            "F1VUHdsaGe7RdXrOB7vtrmLof4QgY68jn2kcGtnSN8jYUEYlZrrzgy4ba5y/8/Me\n" +
            "ziFzOPD05dGWcXa50c1SR0QNn3fLdwIZbm/BnJXGZ7KEkijKbxsUGG2uYUw0apQ+\n" +
            "uQ3Q4zIcdP7TcwyD84w3XQ7xxwMQvjkB03ogFu7UbMGREtJikCTkpB6M1zlPjvNn\n" +
            "RQ82eCOJAXihH4ajmLi2g9IpGiHtP8tpQA13YGzQFHP+5Qk1LJxZosBoB/tXNKAr\n" +
            "mpk+rzidU1Z/FLVLwl/vGUIMcJnf2y4JN+LyLWt3WyUetdee6vsOV3DKkqfsEhum\n" +
            "oEwefE8V2KWDZhZsEnc6YkHD4e3IR3KRt9b+BqXYvGViAXvKmGbUBzQhJe30E3YM\n" +
            "l2xbi6VHRm+WtBd4E5sXVC0u+OKqOlzbAXIBUspM4zZRJguqPW3BLA42XzCIbkkW\n" +
            "o7AE4nhURxmiJzGMyhLjd4ADTZVaE85W/uWXy6iW5ljXAUC2z3k4R0gJCN8+9xOq\n" +
            "J0dUwVOlhT0VOcS3gOgSgjZGzKxuNz34tJBl2nyu5W/OCGW/kjnUxpmi6QXdXjWX\n" +
            "6mPEU3g8txQkjDQAWiWucotEtlONoiOjw+RYcniLoaiy8RQ0YG61/ztue6HQ1AUh\n" +
            "WYWYpcTQOEBJ6wgzTZHAyczUAAAAAAAAAAAAAAAAAAAACA4UHCAo\n" +
            "-----END CERTIFICATE-----";

    private static final String mldsa65ServerKey =
            "MDQCAQAwCwYJYIZIAWUDBAMSBCKAILKjs8r2R35j+4BYyJdiylSIFBtOZKW9kCqff9PCdsfe";

    private TestCertificates() {}

    public static KeyStore getKeyStore()
            throws GeneralSecurityException, IOException {
        KeyStore result = KeyStore.getInstance(KeyStore.getDefaultType());
        result.load(null, null);

        result.setKeyEntry("ecdsa", getPrivKey("EC", ecdsaP256ServerKey),
                new char[0], new Certificate[] {
                        getCert(ECDSA_P256_SERVER_CERT),
                        getCert(ECDSA_P256_INTERMEDIATE_CA_CERT)});
        result.setKeyEntry("mldsa65", getPrivKey("ML-DSA", mldsa65ServerKey),
                new char[0], new Certificate[] {getCert(MLDSA65_SERVER_CERT),
                        getCert(MLDSA65_INTERMEDIATE_CA_CERT)});
        return result;
    }

    public static KeyStore getTrustStore()
            throws GeneralSecurityException, IOException {
        KeyStore result = KeyStore.getInstance(KeyStore.getDefaultType());
        result.load(null, null);
        result.setCertificateEntry("classical-root",
                getCert(ECDSA_P256_ROOT_CA_CERT));
        result.setCertificateEntry("pq-root", getCert(MLDSA65_ROOT_CA_CERT));
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
