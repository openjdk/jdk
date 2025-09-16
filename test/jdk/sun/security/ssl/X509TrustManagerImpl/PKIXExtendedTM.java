/*
 * Copyright (c) 2010, 2025, Oracle and/or its affiliates. All rights reserved.
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

//
// SunJSSE does not support dynamic system properties, no way to re-use
// system properties in samevm/agentvm mode.
//

/*
 * @test
 * @bug 6916074 8170131
 * @summary Add support for TLS 1.2
 * @run main/othervm PKIXExtendedTM 0
 * @run main/othervm PKIXExtendedTM 1
 * @run main/othervm PKIXExtendedTM 2
 * @run main/othervm PKIXExtendedTM 3
 */

import java.io.*;
import javax.net.ssl.*;
import java.security.Security;
import java.security.KeyStore;
import java.security.KeyFactory;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.CertPathValidatorException;
import java.security.spec.*;
import java.security.interfaces.*;
import java.math.BigInteger;


/*
 * Certificates and key used in the test.
 *
 * TLS server certificate:
 * server private key:
 *
 * Private-Key: (2048 bit, 2 primes)
 * modulus:
 *     00:9a:0c:e0:8f:a8:02:7e:5a:ef:ed:b2:42:ad:08:
 *     4e:91:ba:c2:ad:9b:79:d7:9b:0f:fd:d2:f8:15:2f:
 *     19:89:80:10:00:02:19:6d:27:c2:90:d7:a5:23:53:
 *     74:6e:64:28:7c:24:aa:ed:ea:21:59:dc:a3:5c:b5:
 *     c9:42:31:4f:a2:de:fb:09:7c:73:ed:88:04:34:f1:
 *     15:ad:3d:60:cd:ca:c5:13:99:d3:9f:9b:b2:92:70:
 *     cb:ba:4b:3d:20:96:ad:be:92:53:ed:54:3b:c5:14:
 *     bd:cf:d4:0f:cb:05:4f:fd:2b:9e:e0:50:bb:65:13:
 *     92:c0:d6:bd:4d:02:0c:70:b6:65:d4:7d:b4:4d:c3:
 *     df:2c:08:9e:d2:3e:69:32:46:6f:6f:ca:d1:73:a4:
 *     94:07:ef:14:e3:da:9e:2f:c0:ac:0e:10:33:4c:68:
 *     79:f3:79:40:d6:e9:3c:c2:e6:70:e0:89:ce:a0:7a:
 *     a8:84:28:85:32:37:08:b0:cf:b1:7f:5f:bc:1f:a5:
 *     3d:ef:d6:68:a8:17:21:5f:87:d5:4b:b5:cc:ee:78:
 *     8d:dd:b1:28:6a:c0:fb:64:bd:b7:70:02:33:03:0b:
 *     b8:b8:bb:08:82:f6:8e:05:27:d1:3b:e6:c5:ac:4d:
 *     85:5b:a1:1d:a3:48:5d:03:15:76:63:6c:71:21:3e:
 *     98:cd
 * publicExponent: 65537 (0x10001)
 * privateExponent:
 *     68:87:36:54:a3:c6:d5:5f:f5:0f:4f:76:c8:9c:2b:
 *     5b:dc:e2:be:14:12:2f:c7:0a:a9:cb:5e:04:59:ca:
 *     35:2f:8d:2b:c4:40:e6:7d:25:1b:4d:07:c3:99:9c:
 *     16:4f:a5:dc:de:b0:90:f0:de:22:70:80:f4:a6:70:
 *     e2:96:3d:18:21:bf:2b:27:a4:2d:d7:ae:2b:12:2f:
 *     08:36:ee:99:94:ed:f6:a7:d9:1d:a2:f3:1f:44:a4:
 *     28:4b:67:35:d6:a8:1b:f8:84:34:34:84:bd:ec:9e:
 *     03:08:3c:93:20:8e:af:15:cb:1f:20:08:97:c4:19:
 *     3e:fa:36:c6:ab:0e:2f:e7:b3:c0:a7:bc:e4:e0:a6:
 *     08:1c:69:20:4d:78:bd:7a:e5:25:48:60:9e:2e:50:
 *     8d:36:1e:07:e9:d5:0d:39:67:41:42:24:db:87:e5:
 *     77:76:fd:5e:d5:c6:e5:d3:b0:98:71:48:69:47:4f:
 *     46:05:0c:9e:58:45:2e:e2:27:d0:f6:11:05:78:ad:
 *     83:5a:5b:ec:d7:2e:26:5a:a5:4f:9e:52:84:2c:1f:
 *     59:1a:78:56:0a:44:54:c6:37:64:01:ca:e4:a8:01:
 *     c7:86:c1:b4:d6:6c:7a:15:9a:65:69:46:9e:fd:f6:
 *     08:17:0c:6c:ac:38:bd:c2:cd:da:ef:54:7a:48:92:
 *     4d
 * prime1:
 *     00:e4:43:cc:51:25:aa:1d:90:41:95:2c:e8:9f:aa:
 *     1c:9b:ea:bd:fd:29:e5:68:6b:28:00:ec:31:31:36:
 *     d0:3d:84:db:c5:5d:32:f6:38:b9:04:4f:45:cb:19:
 *     f5:88:cd:a8:fc:70:b8:6d:98:68:a6:b4:9e:c1:da:
 *     fd:db:eb:1a:53:3c:3b:e6:85:d2:6f:03:45:7a:ad:
 *     49:8c:c3:96:a7:46:a4:bb:3b:48:d3:d7:1c:b4:3c:
 *     f7:04:0a:a3:85:9d:94:3e:bd:35:f5:34:21:3d:08:
 *     89:df:c5:54:af:cf:90:f7:d8:5c:57:c5:77:5a:c8:
 *     d1:b3:8f:ee:01:5c:07:13:3f
 * prime2:
 *     00:ac:c4:a0:cc:7c:51:db:65:0a:02:da:bc:d8:77:
 *     21:8c:d3:30:ae:ec:50:60:4b:b9:39:c7:2d:bd:98:
 *     aa:4f:9b:44:74:ab:f8:86:de:e2:44:15:73:7a:cd:
 *     d5:46:f2:03:62:c5:87:9c:6d:91:d5:7a:9a:17:c2:
 *     c6:2f:29:0e:8a:a4:a9:f4:c2:63:a2:77:97:bf:c6:
 *     90:e8:39:70:87:cc:fd:62:4f:d2:3d:e7:47:70:fb:
 *     f3:bd:bd:5c:9c:77:fe:23:33:7d:83:ef:cb:0e:4e:
 *     f1:dd:05:47:40:97:f4:da:b6:1f:b9:8d:e2:92:04:
 *     09:be:fb:6a:97:29:27:ac:f3
 * exponent1:
 *     3f:08:1d:b6:56:b1:38:02:aa:a9:77:c2:30:bc:b7:
 *     b3:b2:49:8e:4b:f0:66:3a:18:cc:d0:6b:f1:0c:12:
 *     ca:ba:12:39:d8:b7:86:d8:38:f6:e0:b1:04:19:81:
 *     fc:a9:d5:bd:07:9f:55:dc:1d:21:d3:84:77:41:72:
 *     92:34:c4:8b:31:79:d4:f9:25:17:b4:8e:8e:06:a5:
 *     e5:b1:e8:ba:fe:3d:e4:d9:c5:0d:82:3c:11:e5:37:
 *     cc:ac:e7:64:b1:13:cb:93:52:00:08:ca:18:e1:6f:
 *     b9:13:f3:83:ac:cc:7a:34:0b:a3:cd:0a:5d:4e:50:
 *     e1:c5:9f:d2:4e:48:41:df
 * exponent2:
 *     02:c7:fb:8a:af:29:a6:2d:7f:36:c2:8c:ad:b3:65:
 *     3f:de:1a:77:86:68:58:d4:7f:3b:d5:df:ff:a0:58:
 *     85:85:8b:59:91:77:23:bc:ac:c9:c9:ca:9d:1c:79:
 *     25:76:39:e5:ba:26:4f:b7:57:d4:a6:ef:9a:18:51:
 *     96:6a:c3:c8:29:94:6e:d3:3e:45:5c:45:7e:19:d5:
 *     35:57:cf:5e:f0:46:d7:f1:4f:02:1e:1a:01:50:9d:
 *     00:dd:ee:82:ba:4f:c6:03:4b:2e:f7:8a:3e:45:b9:
 *     11:04:c7:bb:db:76:5e:9a:f5:f1:c7:bd:f0:f9:cd:
 *     aa:5c:63:bf:e1:32:b9:4f
 * coefficient:
 *     50:4c:e6:1e:23:f3:e2:2b:d6:3f:87:53:fb:19:53:
 *     4b:84:21:0b:77:31:ed:8d:c3:0c:ea:31:b0:a6:38:
 *     a9:e6:44:6e:18:05:53:8f:4a:5f:75:e5:3e:b5:26:
 *     9b:46:3d:73:e7:c1:2a:a6:3e:c3:cd:41:b1:a6:55:
 *     57:84:11:13:ec:44:92:59:7f:dd:0d:67:30:d3:b7:
 *     13:ee:9e:2d:ea:be:b3:ca:4a:f0:6e:4f:22:e8:be:
 *     8b:8d:9b:2c:30:a5:ed:2c:2b:13:4c:f7:61:19:64:
 *     35:9d:b0:c8:10:85:01:e7:2a:70:13:00:39:c5:73:
 *     63:34:fd:28:2d:7f:8d:20
 * -----BEGIN PRIVATE KEY-----
 * MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCaDOCPqAJ+Wu/t
 * skKtCE6RusKtm3nXmw/90vgVLxmJgBAAAhltJ8KQ16UjU3RuZCh8JKrt6iFZ3KNc
 * tclCMU+i3vsJfHPtiAQ08RWtPWDNysUTmdOfm7KScMu6Sz0glq2+klPtVDvFFL3P
 * 1A/LBU/9K57gULtlE5LA1r1NAgxwtmXUfbRNw98sCJ7SPmkyRm9vytFzpJQH7xTj
 * 2p4vwKwOEDNMaHnzeUDW6TzC5nDgic6geqiEKIUyNwiwz7F/X7wfpT3v1mioFyFf
 * h9VLtczueI3dsShqwPtkvbdwAjMDC7i4uwiC9o4FJ9E75sWsTYVboR2jSF0DFXZj
 * bHEhPpjNAgMBAAECggEAaIc2VKPG1V/1D092yJwrW9zivhQSL8cKqcteBFnKNS+N
 * K8RA5n0lG00Hw5mcFk+l3N6wkPDeInCA9KZw4pY9GCG/KyekLdeuKxIvCDbumZTt
 * 9qfZHaLzH0SkKEtnNdaoG/iENDSEveyeAwg8kyCOrxXLHyAIl8QZPvo2xqsOL+ez
 * wKe85OCmCBxpIE14vXrlJUhgni5QjTYeB+nVDTlnQUIk24fld3b9XtXG5dOwmHFI
 * aUdPRgUMnlhFLuIn0PYRBXitg1pb7NcuJlqlT55ShCwfWRp4VgpEVMY3ZAHK5KgB
 * x4bBtNZsehWaZWlGnv32CBcMbKw4vcLN2u9UekiSTQKBgQDkQ8xRJaodkEGVLOif
 * qhyb6r39KeVoaygA7DExNtA9hNvFXTL2OLkET0XLGfWIzaj8cLhtmGimtJ7B2v3b
 * 6xpTPDvmhdJvA0V6rUmMw5anRqS7O0jT1xy0PPcECqOFnZQ+vTX1NCE9CInfxVSv
 * z5D32FxXxXdayNGzj+4BXAcTPwKBgQCsxKDMfFHbZQoC2rzYdyGM0zCu7FBgS7k5
 * xy29mKpPm0R0q/iG3uJEFXN6zdVG8gNixYecbZHVepoXwsYvKQ6KpKn0wmOid5e/
 * xpDoOXCHzP1iT9I950dw+/O9vVycd/4jM32D78sOTvHdBUdAl/Tath+5jeKSBAm+
 * +2qXKSes8wKBgD8IHbZWsTgCqql3wjC8t7OySY5L8GY6GMzQa/EMEsq6EjnYt4bY
 * OPbgsQQZgfyp1b0Hn1XcHSHThHdBcpI0xIsxedT5JRe0jo4GpeWx6Lr+PeTZxQ2C
 * PBHlN8ys52SxE8uTUgAIyhjhb7kT84OszHo0C6PNCl1OUOHFn9JOSEHfAoGAAsf7
 * iq8ppi1/NsKMrbNlP94ad4ZoWNR/O9Xf/6BYhYWLWZF3I7ysycnKnRx5JXY55bom
 * T7dX1KbvmhhRlmrDyCmUbtM+RVxFfhnVNVfPXvBG1/FPAh4aAVCdAN3ugrpPxgNL
 * LveKPkW5EQTHu9t2Xpr18ce98PnNqlxjv+EyuU8CgYBQTOYeI/PiK9Y/h1P7GVNL
 * hCELdzHtjcMM6jGwpjip5kRuGAVTj0pfdeU+tSabRj1z58Eqpj7DzUGxplVXhBET
 * 7ESSWX/dDWcw07cT7p4t6r6zykrwbk8i6L6LjZssMKXtLCsTTPdhGWQ1nbDIEIUB
 * 5ypwEwA5xXNjNP0oLX+NIA==
 * -----END PRIVATE KEY-----
 *
 * server certificate:
 *     Data:
 *         Version: 3 (0x2)
 *         Serial Number: 106315679 (0x6563f9f)
 *         Signature Algorithm: sha1WithRSAEncryption
 *         Issuer: C=Us, ST=Some-State, L=Some-City, O=Some-Org
 *         Validity
 *             Not Before: Jul  1 04:16:55 2024 GMT
 *             Not After : Jul  2 04:16:55 2034 GMT
 *         Subject: C=US, ST=Some-State, L=Some-City, O=Some-Org, CN=localhost ou=SSL-Server
 *         Subject Public Key Info:
 *             Public Key Algorithm: rsaEncryption
 *                 Public-Key: (2048 bit)
 *                 Modulus:
 *                     00:9a:0c:e0:8f:a8:02:7e:5a:ef:ed:b2:42:ad:08:
 *                     4e:91:ba:c2:ad:9b:79:d7:9b:0f:fd:d2:f8:15:2f:
 *                     19:89:80:10:00:02:19:6d:27:c2:90:d7:a5:23:53:
 *                     74:6e:64:28:7c:24:aa:ed:ea:21:59:dc:a3:5c:b5:
 *                     c9:42:31:4f:a2:de:fb:09:7c:73:ed:88:04:34:f1:
 *                     15:ad:3d:60:cd:ca:c5:13:99:d3:9f:9b:b2:92:70:
 *                     cb:ba:4b:3d:20:96:ad:be:92:53:ed:54:3b:c5:14:
 *                     bd:cf:d4:0f:cb:05:4f:fd:2b:9e:e0:50:bb:65:13:
 *                     92:c0:d6:bd:4d:02:0c:70:b6:65:d4:7d:b4:4d:c3:
 *                     df:2c:08:9e:d2:3e:69:32:46:6f:6f:ca:d1:73:a4:
 *                     94:07:ef:14:e3:da:9e:2f:c0:ac:0e:10:33:4c:68:
 *                     79:f3:79:40:d6:e9:3c:c2:e6:70:e0:89:ce:a0:7a:
 *                     a8:84:28:85:32:37:08:b0:cf:b1:7f:5f:bc:1f:a5:
 *                     3d:ef:d6:68:a8:17:21:5f:87:d5:4b:b5:cc:ee:78:
 *                     8d:dd:b1:28:6a:c0:fb:64:bd:b7:70:02:33:03:0b:
 *                     b8:b8:bb:08:82:f6:8e:05:27:d1:3b:e6:c5:ac:4d:
 *                     85:5b:a1:1d:a3:48:5d:03:15:76:63:6c:71:21:3e:
 *                     98:cd
 *                 Exponent: 65537 (0x10001)
 *         X509v3 extensions:
 *             X509v3 Subject Key Identifier:
 *                 5C:AF:44:B1:48:B8:59:9A:64:53:9D:2E:A6:B2:09:D3:0A:92:04:83
 *             X509v3 Key Usage:
 *                 Digital Signature, Non Repudiation, Key Encipherment
 *             X509v3 Subject Alternative Name: critical
 *                 DNS:localhost
 *             X509v3 Basic Constraints:
 *                 CA:FALSE
 *             X509v3 Authority Key Identifier:
 *                 E0:03:90:F6:4F:BB:57:E6:7E:AF:5C:94:25:B3:85:DA:16:0A:51:40
 *     Signature Algorithm: sha1WithRSAEncryption
 *     Signature Value:
 *         9d:22:49:5f:56:23:e6:80:35:cc:ab:44:1c:27:bd:c9:8d:89:
 *         93:49:58:e8:c1:7a:68:dd:cf:bd:e0:12:76:06:54:cd:2f:62:
 *         9b:54:84:f2:bb:90:a0:bb:37:e2:13:1d:f3:df:41:aa:e0:fe:
 *         c0:ef:46:78:8d:aa:f4:1b:70:ad:a9:16:24:fa:15:4a:c6:0a:
 *         8d:e1:99:93:00:a9:d4:b6:08:5d:8e:65:03:dc:d0:95:fc:95:
 *         61:a6:ad:b5:ab:4d:a6:e0:05:48:8c:db:42:42:8a:d6:5e:c0:
 *         2a:a0:11:15:b8:07:69:5c:3f:99:a0:bd:53:65:db:4e:cf:46:
 *         61:93:09:7b:81:40:ff:5c:fe:4c:eb:f4:ac:de:1f:38:ad:b2:
 *         60:28:f6:0e:9f:46:e7:07:8f:20:9a:a4:e1:8f:ab:54:99:76:
 *         82:d8:9e:70:c4:da:98:85:71:af:3b:54:e4:01:b4:9e:83:d0:
 *         7b:c6:8d:1f:ed:25:08:89:05:e9:87:97:76:5a:a3:85:c3:f8:
 *         59:d7:bb:3b:5a:db:cb:ed:5d:ff:ac:21:b9:9a:e2:65:0a:bc:
 *         de:d1:dc:53:94:98:44:97:91:b3:1b:6b:80:0b:9b:57:b3:ae:
 *         5c:7c:35:ca:39:71:f7:4e:8f:4a:d7:eb:0b:25:da:b2:1e:17:
 *         48:b8:eb:09
 * -----BEGIN CERTIFICATE-----
 * MIIDpTCCAo2gAwIBAgIEBlY/nzANBgkqhkiG9w0BAQUFADBJMQswCQYDVQQGEwJV
 * czETMBEGA1UECBMKU29tZS1TdGF0ZTESMBAGA1UEBxMJU29tZS1DaXR5MREwDwYD
 * VQQKEwhTb21lLU9yZzAeFw0yNDA3MDEwNDE2NTVaFw0zNDA3MDIwNDE2NTVaMGsx
 * CzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpTb21lLVN0YXRlMRIwEAYDVQQHEwlTb21l
 * LUNpdHkxETAPBgNVBAoTCFNvbWUtT3JnMSAwHgYDVQQDExdsb2NhbGhvc3Qgb3U9
 * U1NMLVNlcnZlcjCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAJoM4I+o
 * An5a7+2yQq0ITpG6wq2bedebD/3S+BUvGYmAEAACGW0nwpDXpSNTdG5kKHwkqu3q
 * IVnco1y1yUIxT6Le+wl8c+2IBDTxFa09YM3KxROZ05+bspJwy7pLPSCWrb6SU+1U
 * O8UUvc/UD8sFT/0rnuBQu2UTksDWvU0CDHC2ZdR9tE3D3ywIntI+aTJGb2/K0XOk
 * lAfvFOPani/ArA4QM0xoefN5QNbpPMLmcOCJzqB6qIQohTI3CLDPsX9fvB+lPe/W
 * aKgXIV+H1Uu1zO54jd2xKGrA+2S9t3ACMwMLuLi7CIL2jgUn0TvmxaxNhVuhHaNI
 * XQMVdmNscSE+mM0CAwEAAaNzMHEwHQYDVR0OBBYEFFyvRLFIuFmaZFOdLqayCdMK
 * kgSDMAsGA1UdDwQEAwIF4DAXBgNVHREBAf8EDTALgglsb2NhbGhvc3QwCQYDVR0T
 * BAIwADAfBgNVHSMEGDAWgBTgA5D2T7tX5n6vXJQls4XaFgpRQDANBgkqhkiG9w0B
 * AQUFAAOCAQEAnSJJX1Yj5oA1zKtEHCe9yY2Jk0lY6MF6aN3PveASdgZUzS9im1SE
 * 8ruQoLs34hMd899BquD+wO9GeI2q9BtwrakWJPoVSsYKjeGZkwCp1LYIXY5lA9zQ
 * lfyVYaattatNpuAFSIzbQkKK1l7AKqARFbgHaVw/maC9U2XbTs9GYZMJe4FA/1z+
 * TOv0rN4fOK2yYCj2Dp9G5wePIJqk4Y+rVJl2gtiecMTamIVxrztU5AG0noPQe8aN
 * H+0lCIkF6YeXdlqjhcP4Wde7O1rby+1d/6whuZriZQq83tHcU5SYRJeRsxtrgAub
 * V7OuXHw1yjlx906PStfrCyXash4XSLjrCQ==
 * -----END CERTIFICATE-----
 *
 *
 * TLS client certificate:
 * client private key:
 *
 * Private-Key: (2048 bit, 2 primes)
 * modulus:
 *     00:cc:bf:92:3c:a6:57:74:1f:58:ad:c7:69:88:6f:
 *     59:32:47:50:60:22:e4:98:49:0e:3e:1d:b8:ba:e2:
 *     3b:b6:71:5b:fd:64:02:6d:0d:50:77:72:6e:a8:3d:
 *     5d:d4:bd:1f:76:51:dc:9a:d0:d6:3e:d0:31:a5:24:
 *     5a:2c:be:77:fa:88:a1:fa:06:41:c8:0f:47:70:47:
 *     24:99:50:52:44:5b:30:62:5b:65:35:c4:28:b0:5c:
 *     ee:d0:1b:eb:39:2b:0b:a1:ac:96:48:da:56:6c:e0:
 *     e3:e6:e3:dd:45:cb:51:33:8d:40:43:d7:f0:a4:31:
 *     aa:b5:c0:df:4b:df:2b:0a:ed:7e:10:0c:ae:1f:96:
 *     a2:10:1e:6b:d0:f9:37:8b:df:0d:0e:02:35:f8:58:
 *     bc:6e:b5:57:0e:2f:ea:20:e6:73:9a:e5:6b:82:70:
 *     25:bb:51:9a:7c:9d:e2:50:3d:cf:1e:24:3e:92:55:
 *     cf:2a:ad:0d:84:8f:a8:43:24:cd:ad:50:64:74:c2:
 *     73:b6:e1:92:1c:b2:2b:8c:2d:7b:96:a6:41:61:5c:
 *     1b:8f:78:28:51:40:ed:41:90:ce:1d:b8:26:81:47:
 *     6b:e3:57:41:74:4e:20:f0:5a:1b:97:37:91:86:19:
 *     c5:f2:6d:04:c9:78:2b:5a:16:bc:fc:2b:71:5b:d0:
 *     00:4f
 * publicExponent: 65537 (0x10001)
 * privateExponent:
 *     62:b2:d6:63:b6:2b:e2:26:5a:31:2b:37:8c:35:60:
 *     e2:03:ce:93:09:3e:f8:c9:fe:bb:a2:c8:32:0e:6c:
 *     8a:7e:0a:c2:13:3b:b8:25:fa:ec:19:95:8e:34:46:
 *     cf:0e:7b:e4:25:82:1a:7f:21:48:16:44:58:3f:35:
 *     d8:eb:d8:1a:45:53:0f:9b:84:8a:54:13:33:e4:97:
 *     97:f0:48:37:fb:5d:4f:8c:8f:35:63:e1:d9:62:73:
 *     1c:8e:d8:cd:2e:1a:e5:4c:b5:05:59:7a:df:f1:68:
 *     eb:1c:5c:c6:10:44:8c:7d:42:c5:71:8a:e7:1b:aa:
 *     17:03:6a:a0:c0:6b:97:50:17:ad:6e:5e:d9:db:6f:
 *     3e:e9:3f:35:c3:45:bc:e8:3d:5a:b4:b9:3f:53:80:
 *     64:dc:12:24:35:35:bd:98:bb:8d:fa:19:a3:5e:9e:
 *     ac:70:4a:fc:8d:ae:55:8b:71:81:0e:4d:c8:2f:87:
 *     b0:44:f7:4f:dc:a8:c8:50:b5:95:24:63:74:13:54:
 *     58:de:fc:e0:75:eb:f4:06:58:83:12:4c:56:c4:c4:
 *     18:0c:ea:a3:e7:25:a3:de:19:23:a2:5a:2a:b6:56:
 *     04:bc:65:ba:7c:0a:f4:91:10:22:88:3f:9d:be:58:
 *     43:4c:2e:ad:db:d6:32:cf:8e:b5:05:55:39:8b:e1:
 *     01
 * prime1:
 *     00:f1:da:c2:8a:e5:66:45:8a:14:fc:08:6e:fb:aa:
 *     50:d2:8c:b1:c4:f4:88:26:d4:b8:c4:63:30:ca:e3:
 *     0c:6c:50:d4:93:5c:1c:13:37:60:21:11:3b:d1:f1:
 *     9f:4c:0d:7b:0e:53:3d:c9:a4:fb:fa:6b:9e:b4:0a:
 *     5d:d3:50:88:d7:be:c3:88:b2:b1:8a:6e:7b:d6:70:
 *     88:96:a4:fe:90:ef:d1:84:ad:a8:9e:9f:3a:68:3f:
 *     3f:82:07:be:c2:44:1e:d5:a1:a9:1a:db:39:d7:7f:
 *     0c:6e:35:5b:1d:33:1b:a9:cd:38:2a:64:d1:70:2a:
 *     fe:b9:c2:b6:ed:59:19:73:b1
 * prime2:
 *     00:d8:b9:3a:38:6c:79:cd:0b:1f:2b:34:74:bf:7a:
 *     3d:0c:21:5a:a6:ea:f2:9e:de:68:42:05:7f:ea:a5:
 *     00:c9:10:f8:fd:c5:05:8d:03:45:5d:4f:6f:fa:6e:
 *     9d:ef:ad:8a:ec:83:d4:ed:57:f3:86:73:15:2f:d2:
 *     67:70:d1:62:ef:1d:25:08:59:47:20:62:47:16:35:
 *     e1:57:38:bf:39:dd:fc:b9:c8:d8:23:53:e2:02:7d:
 *     22:31:4c:66:72:96:df:d8:7c:01:2c:71:00:89:18:
 *     e9:8c:08:44:8c:64:1f:93:9b:7a:97:26:c9:50:d0:
 *     87:b2:48:a8:19:71:e1:b3:ff
 * exponent1:
 *     23:98:dd:35:70:5a:43:35:f5:ac:ba:d9:0a:f5:a0:
 *     7b:bc:f5:95:55:a0:8c:86:96:c3:61:0e:17:6e:9f:
 *     af:79:9e:30:2a:48:7f:93:90:f4:8d:02:ce:fd:cf:
 *     42:74:61:7e:54:46:2d:dd:b8:b0:bd:12:58:d1:85:
 *     c9:ca:7a:b9:b6:7c:35:2c:87:f1:26:1d:d8:0c:2c:
 *     2e:70:0e:7f:ea:ac:5d:e8:e9:7e:9f:55:0b:6e:f3:
 *     bc:01:c3:d3:f8:0e:c9:c6:c7:8b:0a:65:53:10:82:
 *     15:de:88:90:9d:ab:1e:ac:f3:ed:59:75:72:1b:01:
 *     ee:f9:77:cf:2b:64:11:a1
 * exponent2:
 *     00:9e:29:6f:87:c6:02:8d:d5:54:05:df:de:63:ee:
 *     fd:a6:60:a1:1b:b7:d3:20:86:07:68:47:43:37:26:
 *     fc:0f:c0:c7:35:cc:17:64:f5:c2:25:7a:d7:a9:d8:
 *     18:82:d6:0f:d0:d3:d5:0c:f1:66:d3:f4:20:be:29:
 *     bb:3b:e6:53:61:55:cf:b4:ec:12:b0:5b:88:ad:78:
 *     dc:df:1e:96:cf:d0:65:a3:e0:23:7c:84:b7:28:41:
 *     d2:36:50:1f:63:f9:1f:9b:89:c4:01:7e:e6:79:27:
 *     29:29:fc:ce:a9:f6:57:e5:0d:4e:c6:08:94:5a:da:
 *     14:6d:d4:00:79:b1:56:9a:59
 * coefficient:
 *     6c:73:0d:fe:c7:22:15:5d:8c:a1:91:2b:d1:88:e8:
 *     91:f9:d0:3e:d0:ba:c4:74:88:ce:14:20:4e:1e:4b:
 *     c5:91:8f:c1:56:e9:74:e0:f6:cf:71:91:ed:2c:f5:
 *     90:9d:d6:c8:cd:f5:79:dc:6e:b3:83:3e:fa:d6:b4:
 *     60:d9:3a:52:12:76:9d:92:fb:db:26:ee:43:33:c4:
 *     0b:84:74:1b:91:e0:41:8b:cc:cc:24:da:52:af:2d:
 *     42:e7:11:57:0d:aa:66:af:1a:ba:c2:8e:6a:ee:8f:
 *     2c:e6:5b:76:38:96:bb:7a:2f:59:fe:de:a1:02:fc:
 *     12:3a:aa:9f:3c:0e:a4:78
 * writing RSA key
 * -----BEGIN PRIVATE KEY-----
 * MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDMv5I8pld0H1it
 * x2mIb1kyR1BgIuSYSQ4+Hbi64ju2cVv9ZAJtDVB3cm6oPV3UvR92Udya0NY+0DGl
 * JFosvnf6iKH6BkHID0dwRySZUFJEWzBiW2U1xCiwXO7QG+s5KwuhrJZI2lZs4OPm
 * 491Fy1EzjUBD1/CkMaq1wN9L3ysK7X4QDK4flqIQHmvQ+TeL3w0OAjX4WLxutVcO
 * L+og5nOa5WuCcCW7UZp8neJQPc8eJD6SVc8qrQ2Ej6hDJM2tUGR0wnO24ZIcsiuM
 * LXuWpkFhXBuPeChRQO1BkM4duCaBR2vjV0F0TiDwWhuXN5GGGcXybQTJeCtaFrz8
 * K3Fb0ABPAgMBAAECggEAYrLWY7Yr4iZaMSs3jDVg4gPOkwk++Mn+u6LIMg5sin4K
 * whM7uCX67BmVjjRGzw575CWCGn8hSBZEWD812OvYGkVTD5uEilQTM+SXl/BIN/td
 * T4yPNWPh2WJzHI7YzS4a5Uy1BVl63/Fo6xxcxhBEjH1CxXGK5xuqFwNqoMBrl1AX
 * rW5e2dtvPuk/NcNFvOg9WrS5P1OAZNwSJDU1vZi7jfoZo16erHBK/I2uVYtxgQ5N
 * yC+HsET3T9yoyFC1lSRjdBNUWN784HXr9AZYgxJMVsTEGAzqo+clo94ZI6JaKrZW
 * BLxlunwK9JEQIog/nb5YQ0wurdvWMs+OtQVVOYvhAQKBgQDx2sKK5WZFihT8CG77
 * qlDSjLHE9Igm1LjEYzDK4wxsUNSTXBwTN2AhETvR8Z9MDXsOUz3JpPv6a560Cl3T
 * UIjXvsOIsrGKbnvWcIiWpP6Q79GEraienzpoPz+CB77CRB7Voaka2znXfwxuNVsd
 * MxupzTgqZNFwKv65wrbtWRlzsQKBgQDYuTo4bHnNCx8rNHS/ej0MIVqm6vKe3mhC
 * BX/qpQDJEPj9xQWNA0VdT2/6bp3vrYrsg9TtV/OGcxUv0mdw0WLvHSUIWUcgYkcW
 * NeFXOL853fy5yNgjU+ICfSIxTGZylt/YfAEscQCJGOmMCESMZB+Tm3qXJslQ0Iey
 * SKgZceGz/wKBgCOY3TVwWkM19ay62Qr1oHu89ZVVoIyGlsNhDhdun695njAqSH+T
 * kPSNAs79z0J0YX5URi3duLC9EljRhcnKerm2fDUsh/EmHdgMLC5wDn/qrF3o6X6f
 * VQtu87wBw9P4DsnGx4sKZVMQghXeiJCdqx6s8+1ZdXIbAe75d88rZBGhAoGBAJ4p
 * b4fGAo3VVAXf3mPu/aZgoRu30yCGB2hHQzcm/A/AxzXMF2T1wiV616nYGILWD9DT
 * 1QzxZtP0IL4puzvmU2FVz7TsErBbiK143N8els/QZaPgI3yEtyhB0jZQH2P5H5uJ
 * xAF+5nknKSn8zqn2V+UNTsYIlFraFG3UAHmxVppZAoGAbHMN/sciFV2MoZEr0Yjo
 * kfnQPtC6xHSIzhQgTh5LxZGPwVbpdOD2z3GR7Sz1kJ3WyM31edxus4M++ta0YNk6
 * UhJ2nZL72ybuQzPEC4R0G5HgQYvMzCTaUq8tQucRVw2qZq8ausKOau6PLOZbdjiW
 * u3ovWf7eoQL8EjqqnzwOpHg=
 * -----END PRIVATE KEY-----
 *
 * client certificate:
 *     Data:
 *         Version: 3 (0x2)
 *         Serial Number: 1500699355 (0x5972dadb)
 *         Signature Algorithm: sha1WithRSAEncryption
 *         Issuer: C=Us, ST=Some-State, L=Some-City, O=Some-Org
 *         Validity
 *             Not Before: Jul  1 04:16:52 2024 GMT
 *             Not After : Jul  2 04:16:52 2034 GMT
 *         Subject: C=US, ST=Some-State, L=Some-City, O=Some-Org, CN=localhost ou=SSL-Client
 *         Subject Public Key Info:
 *             Public Key Algorithm: rsaEncryption
 *                 Public-Key: (2048 bit)
 *                 Modulus:
 *                     00:cc:bf:92:3c:a6:57:74:1f:58:ad:c7:69:88:6f:
 *                     59:32:47:50:60:22:e4:98:49:0e:3e:1d:b8:ba:e2:
 *                     3b:b6:71:5b:fd:64:02:6d:0d:50:77:72:6e:a8:3d:
 *                     5d:d4:bd:1f:76:51:dc:9a:d0:d6:3e:d0:31:a5:24:
 *                     5a:2c:be:77:fa:88:a1:fa:06:41:c8:0f:47:70:47:
 *                     24:99:50:52:44:5b:30:62:5b:65:35:c4:28:b0:5c:
 *                     ee:d0:1b:eb:39:2b:0b:a1:ac:96:48:da:56:6c:e0:
 *                     e3:e6:e3:dd:45:cb:51:33:8d:40:43:d7:f0:a4:31:
 *                     aa:b5:c0:df:4b:df:2b:0a:ed:7e:10:0c:ae:1f:96:
 *                     a2:10:1e:6b:d0:f9:37:8b:df:0d:0e:02:35:f8:58:
 *                     bc:6e:b5:57:0e:2f:ea:20:e6:73:9a:e5:6b:82:70:
 *                     25:bb:51:9a:7c:9d:e2:50:3d:cf:1e:24:3e:92:55:
 *                     cf:2a:ad:0d:84:8f:a8:43:24:cd:ad:50:64:74:c2:
 *                     73:b6:e1:92:1c:b2:2b:8c:2d:7b:96:a6:41:61:5c:
 *                     1b:8f:78:28:51:40:ed:41:90:ce:1d:b8:26:81:47:
 *                     6b:e3:57:41:74:4e:20:f0:5a:1b:97:37:91:86:19:
 *                     c5:f2:6d:04:c9:78:2b:5a:16:bc:fc:2b:71:5b:d0:
 *                     00:4f
 *                 Exponent: 65537 (0x10001)
 *         X509v3 extensions:
 *             X509v3 Subject Key Identifier:
 *                 CD:45:E2:05:92:88:A3:C7:49:28:E7:D3:37:B7:13:92:FB:B1:36:C4
 *             X509v3 Key Usage:
 *                 Digital Signature, Non Repudiation, Key Encipherment
 *             X509v3 Subject Alternative Name: critical
 *                 DNS:localhost
 *             X509v3 Basic Constraints:
 *                 CA:FALSE
 *             X509v3 Authority Key Identifier:
 *                 E0:03:90:F6:4F:BB:57:E6:7E:AF:5C:94:25:B3:85:DA:16:0A:51:40
 *     Signature Algorithm: sha1WithRSAEncryption
 *     Signature Value:
 *         23:6e:e9:5d:80:0d:b3:86:c9:cd:17:81:33:bd:5b:aa:c0:65:
 *         4c:6b:9f:fa:ee:32:e9:89:e1:d0:c7:1d:5c:43:7e:94:ac:83:
 *         af:91:90:4c:26:61:8d:fe:6b:1a:aa:6e:61:39:b3:24:4a:dc:
 *         92:c8:ca:f2:80:b0:05:41:0c:b3:dd:ed:b7:81:42:9a:1e:4e:
 *         f2:80:6c:72:62:8b:bd:d4:cd:23:7d:7c:e8:6f:e3:67:89:6a:
 *         79:19:dd:f6:57:62:12:fa:eb:cd:66:c3:d2:d8:40:5a:1c:dd:
 *         7f:9f:b2:34:e9:2a:d6:14:52:ba:6e:a8:9b:0d:a9:a1:03:bf:
 *         c4:0d:92:3d:59:e4:a9:8e:20:41:39:99:81:70:9d:d0:68:98:
 *         fc:5f:49:4a:92:e5:a2:c1:51:61:f6:1e:49:56:0b:b6:8c:57:
 *         db:08:2a:f0:a3:04:dc:a1:04:a2:5c:d0:90:4f:13:8d:1c:e6:
 *         2e:7a:63:9c:32:40:65:59:04:5d:71:90:5a:a8:db:6a:30:42:
 *         57:5b:0b:df:ce:a1:1f:fa:23:71:f3:57:12:c4:1c:66:3b:37:
 *         77:32:28:a7:fb:ad:ee:86:51:4c:80:2f:dd:c8:5b:9f:a7:15:
 *         07:fa:2b:5a:ee:93:00:5f:a6:43:22:1b:40:52:15:66:01:84:
 *         32:9e:71:21
 * -----BEGIN CERTIFICATE-----
 * MIIDpTCCAo2gAwIBAgIEWXLa2zANBgkqhkiG9w0BAQUFADBJMQswCQYDVQQGEwJV
 * czETMBEGA1UECBMKU29tZS1TdGF0ZTESMBAGA1UEBxMJU29tZS1DaXR5MREwDwYD
 * VQQKEwhTb21lLU9yZzAeFw0yNDA3MDEwNDE2NTJaFw0zNDA3MDIwNDE2NTJaMGsx
 * CzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpTb21lLVN0YXRlMRIwEAYDVQQHEwlTb21l
 * LUNpdHkxETAPBgNVBAoTCFNvbWUtT3JnMSAwHgYDVQQDExdsb2NhbGhvc3Qgb3U9
 * U1NMLUNsaWVudDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMy/kjym
 * V3QfWK3HaYhvWTJHUGAi5JhJDj4duLriO7ZxW/1kAm0NUHdybqg9XdS9H3ZR3JrQ
 * 1j7QMaUkWiy+d/qIofoGQcgPR3BHJJlQUkRbMGJbZTXEKLBc7tAb6zkrC6Gslkja
 * Vmzg4+bj3UXLUTONQEPX8KQxqrXA30vfKwrtfhAMrh+WohAea9D5N4vfDQ4CNfhY
 * vG61Vw4v6iDmc5rla4JwJbtRmnyd4lA9zx4kPpJVzyqtDYSPqEMkza1QZHTCc7bh
 * khyyK4wte5amQWFcG494KFFA7UGQzh24JoFHa+NXQXROIPBaG5c3kYYZxfJtBMl4
 * K1oWvPwrcVvQAE8CAwEAAaNzMHEwHQYDVR0OBBYEFM1F4gWSiKPHSSjn0ze3E5L7
 * sTbEMAsGA1UdDwQEAwIF4DAXBgNVHREBAf8EDTALgglsb2NhbGhvc3QwCQYDVR0T
 * BAIwADAfBgNVHSMEGDAWgBTgA5D2T7tX5n6vXJQls4XaFgpRQDANBgkqhkiG9w0B
 * AQUFAAOCAQEAI27pXYANs4bJzReBM71bqsBlTGuf+u4y6Ynh0McdXEN+lKyDr5GQ
 * TCZhjf5rGqpuYTmzJErcksjK8oCwBUEMs93tt4FCmh5O8oBscmKLvdTNI3186G/j
 * Z4lqeRnd9ldiEvrrzWbD0thAWhzdf5+yNOkq1hRSum6omw2poQO/xA2SPVnkqY4g
 * QTmZgXCd0GiY/F9JSpLlosFRYfYeSVYLtoxX2wgq8KME3KEEolzQkE8TjRzmLnpj
 * nDJAZVkEXXGQWqjbajBCV1sL386hH/ojcfNXEsQcZjs3dzIop/ut7oZRTIAv3chb
 * n6cVB/orWu6TAF+mQyIbQFIVZgGEMp5xIQ==
 * -----END CERTIFICATE-----
 *
 *
 * Trusted CA certificate:
 * Certificate:
 *   Data:
 *         Version: 3 (0x2)
 *         Serial Number: 1539881479 (0x5bc8ba07)
 *         Signature Algorithm: sha1WithRSAEncryption
 *         Issuer: C=Us, ST=Some-State, L=Some-City, O=Some-Org
 *         Validity
 *             Not Before: Jul  1 04:16:50 2024 GMT
 *             Not After : Jul  2 04:16:50 2034 GMT
 *         Subject: C=Us, ST=Some-State, L=Some-City, O=Some-Org
 *         Subject Public Key Info:
 *             Public Key Algorithm: rsaEncryption
 *                 Public-Key: (2048 bit)
 *                 Modulus:
 *                     00:bc:a6:55:60:3f:17:74:39:ba:71:8c:ef:11:3f:
 *                     9d:36:47:d5:02:d1:4d:9d:7e:b8:fe:59:b1:2b:f1:
 *                     b7:b0:0c:31:57:eb:9c:9d:13:f5:4c:5f:fc:c4:9e:
 *                     f9:75:09:0f:96:8f:05:77:30:a8:35:48:71:96:e4:
 *                     a5:7d:1a:81:fb:e6:bf:90:80:60:5d:11:20:54:16:
 *                     0b:6d:df:64:de:18:d5:98:51:38:9d:c9:d6:5f:de:
 *                     9d:de:fe:a8:5f:d3:25:3d:ad:f3:2b:45:c8:4a:80:
 *                     97:14:7b:85:9d:cf:59:08:bb:c7:67:ac:8b:29:f3:
 *                     1e:93:bf:fb:82:53:c5:ae:b4:bc:55:30:15:a8:7e:
 *                     3f:82:22:59:43:cc:d2:62:e7:65:67:72:ec:10:8a:
 *                     fc:05:90:91:72:dd:e9:6f:e2:9f:0c:ab:a1:83:55:
 *                     02:23:b7:a3:c3:50:ab:be:0b:bb:51:75:50:d1:a8:
 *                     c9:e5:f5:06:fe:00:09:a6:1b:8a:16:29:0d:ab:00:
 *                     3e:bc:d2:73:d9:37:d7:d9:9a:58:6e:2d:2a:f6:76:
 *                     ae:f4:ea:6d:70:de:7f:e3:04:43:c0:4f:91:3f:78:
 *                     58:d7:c2:ad:74:eb:04:9d:d0:7e:82:b8:7a:97:44:
 *                     61:fa:41:45:a6:ca:7d:a5:2e:fc:f9:a6:cf:61:cd:
 *                     75:bf
 *                 Exponent: 65537 (0x10001)
 *         X509v3 extensions:
 *             X509v3 Subject Key Identifier:
 *                 E0:03:90:F6:4F:BB:57:E6:7E:AF:5C:94:25:B3:85:DA:16:0A:51:40
 *             X509v3 Basic Constraints: critical
 *                 CA:TRUE
 *     Signature Algorithm: sha1WithRSAEncryption
 *     Signature Value:
 *         1f:89:34:e3:ee:05:33:3b:18:ca:96:13:3d:ad:cd:5a:e6:24:
 *         46:94:36:ad:37:a5:36:a9:92:37:f9:ed:07:dd:44:5b:c9:2e:
 *         68:f7:82:f3:58:1c:64:ed:64:d0:ad:eb:30:15:e0:04:3a:d7:
 *         c8:c7:9d:65:76:ae:84:e4:2e:2d:0d:68:09:0d:e5:ae:cc:a7:
 *         54:86:ad:ff:00:95:85:01:49:db:5b:8e:c2:6f:e7:19:10:17:
 *         f7:03:b9:a8:97:21:a2:fc:7f:c0:e0:7a:12:64:b8:70:f5:e8:
 *         b6:e1:25:f7:eb:32:3e:46:ce:43:55:fc:0b:62:59:90:61:63:
 *         f9:94:6c:95:63:31:1b:00:59:1f:72:9d:d0:0b:4f:cd:02:eb:
 *         de:20:4e:60:48:4e:ea:ad:3c:0f:1d:bf:1a:69:3d:a8:3d:8b:
 *         f5:a2:ae:8c:4f:d7:0e:b3:e1:9b:b3:2c:89:19:18:da:db:e1:
 *         6d:d5:ab:c8:b8:48:57:d8:8b:33:01:d4:97:91:d9:da:34:a1:
 *         ef:36:00:e1:38:19:34:8f:0d:47:af:57:cf:59:d6:8b:0d:9e:
 *         89:05:82:3d:3c:f3:45:1d:4a:3f:0e:0f:5a:28:6f:5c:e1:e9:
 *         60:72:87:28:b6:97:44:8b:d7:c6:cd:cb:dc:5a:5d:60:f1:b4:
 *         37:ee:44:db
 * -----BEGIN CERTIFICATE-----
 * MIIDQjCCAiqgAwIBAgIEW8i6BzANBgkqhkiG9w0BAQUFADBJMQswCQYDVQQGEwJV
 * czETMBEGA1UECBMKU29tZS1TdGF0ZTESMBAGA1UEBxMJU29tZS1DaXR5MREwDwYD
 * VQQKEwhTb21lLU9yZzAeFw0yNDA3MDEwNDE2NTBaFw0zNDA3MDIwNDE2NTBaMEkx
 * CzAJBgNVBAYTAlVzMRMwEQYDVQQIEwpTb21lLVN0YXRlMRIwEAYDVQQHEwlTb21l
 * LUNpdHkxETAPBgNVBAoTCFNvbWUtT3JnMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A
 * MIIBCgKCAQEAvKZVYD8XdDm6cYzvET+dNkfVAtFNnX64/lmxK/G3sAwxV+ucnRP1
 * TF/8xJ75dQkPlo8FdzCoNUhxluSlfRqB++a/kIBgXREgVBYLbd9k3hjVmFE4ncnW
 * X96d3v6oX9MlPa3zK0XISoCXFHuFnc9ZCLvHZ6yLKfMek7/7glPFrrS8VTAVqH4/
 * giJZQ8zSYudlZ3LsEIr8BZCRct3pb+KfDKuhg1UCI7ejw1Crvgu7UXVQ0ajJ5fUG
 * /gAJphuKFikNqwA+vNJz2TfX2ZpYbi0q9nau9OptcN5/4wRDwE+RP3hY18KtdOsE
 * ndB+grh6l0Rh+kFFpsp9pS78+abPYc11vwIDAQABozIwMDAdBgNVHQ4EFgQU4AOQ
 * 9k+7V+Z+r1yUJbOF2hYKUUAwDwYDVR0TAQH/BAUwAwEB/zANBgkqhkiG9w0BAQUF
 * AAOCAQEAH4k04+4FMzsYypYTPa3NWuYkRpQ2rTelNqmSN/ntB91EW8kuaPeC81gc
 * ZO1k0K3rMBXgBDrXyMedZXauhOQuLQ1oCQ3lrsynVIat/wCVhQFJ21uOwm/nGRAX
 * 9wO5qJchovx/wOB6EmS4cPXotuEl9+syPkbOQ1X8C2JZkGFj+ZRslWMxGwBZH3Kd
 * 0AtPzQLr3iBOYEhO6q08Dx2/Gmk9qD2L9aKujE/XDrPhm7MsiRkY2tvhbdWryLhI
 * V9iLMwHUl5HZ2jSh7zYA4TgZNI8NR69Xz1nWiw2eiQWCPTzzRR1KPw4PWihvXOHp
 * YHKHKLaXRIvXxs3L3FpdYPG0N+5E2w==
 *
 */


public class PKIXExtendedTM {

    /*
     * =============================================================
     * Set the various variables needed for the tests, then
     * specify what tests to run on each side.
     */

    /*
     * Should we run the client or server in a separate thread?
     * Both sides can throw exceptions, but do you have a preference
     * as to which side should be the main thread.
     */
    static boolean separateServerThread = true;

    /*
     * Where do we find the keystores?
     */
    static String trusedCertStr =
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIDQjCCAiqgAwIBAgIEW8i6BzANBgkqhkiG9w0BAQUFADBJMQswCQYDVQQGEwJV\n" +
        "czETMBEGA1UECBMKU29tZS1TdGF0ZTESMBAGA1UEBxMJU29tZS1DaXR5MREwDwYD\n" +
        "VQQKEwhTb21lLU9yZzAeFw0yNDA3MDEwNDE2NTBaFw0zNDA3MDIwNDE2NTBaMEkx\n" +
        "CzAJBgNVBAYTAlVzMRMwEQYDVQQIEwpTb21lLVN0YXRlMRIwEAYDVQQHEwlTb21l\n" +
        "LUNpdHkxETAPBgNVBAoTCFNvbWUtT3JnMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A\n" +
        "MIIBCgKCAQEAvKZVYD8XdDm6cYzvET+dNkfVAtFNnX64/lmxK/G3sAwxV+ucnRP1\n" +
        "TF/8xJ75dQkPlo8FdzCoNUhxluSlfRqB++a/kIBgXREgVBYLbd9k3hjVmFE4ncnW\n" +
        "X96d3v6oX9MlPa3zK0XISoCXFHuFnc9ZCLvHZ6yLKfMek7/7glPFrrS8VTAVqH4/\n" +
        "giJZQ8zSYudlZ3LsEIr8BZCRct3pb+KfDKuhg1UCI7ejw1Crvgu7UXVQ0ajJ5fUG\n" +
        "/gAJphuKFikNqwA+vNJz2TfX2ZpYbi0q9nau9OptcN5/4wRDwE+RP3hY18KtdOsE\n" +
        "ndB+grh6l0Rh+kFFpsp9pS78+abPYc11vwIDAQABozIwMDAdBgNVHQ4EFgQU4AOQ\n" +
        "9k+7V+Z+r1yUJbOF2hYKUUAwDwYDVR0TAQH/BAUwAwEB/zANBgkqhkiG9w0BAQUF\n" +
        "AAOCAQEAH4k04+4FMzsYypYTPa3NWuYkRpQ2rTelNqmSN/ntB91EW8kuaPeC81gc\n" +
        "ZO1k0K3rMBXgBDrXyMedZXauhOQuLQ1oCQ3lrsynVIat/wCVhQFJ21uOwm/nGRAX\n" +
        "9wO5qJchovx/wOB6EmS4cPXotuEl9+syPkbOQ1X8C2JZkGFj+ZRslWMxGwBZH3Kd\n" +
        "0AtPzQLr3iBOYEhO6q08Dx2/Gmk9qD2L9aKujE/XDrPhm7MsiRkY2tvhbdWryLhI\n" +
        "V9iLMwHUl5HZ2jSh7zYA4TgZNI8NR69Xz1nWiw2eiQWCPTzzRR1KPw4PWihvXOHp\n" +
        "YHKHKLaXRIvXxs3L3FpdYPG0N+5E2w==\n" +
        "-----END CERTIFICATE-----";

    static String serverCertStr =
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIDpTCCAo2gAwIBAgIEBlY/nzANBgkqhkiG9w0BAQUFADBJMQswCQYDVQQGEwJV\n" +
        "czETMBEGA1UECBMKU29tZS1TdGF0ZTESMBAGA1UEBxMJU29tZS1DaXR5MREwDwYD\n" +
        "VQQKEwhTb21lLU9yZzAeFw0yNDA3MDEwNDE2NTVaFw0zNDA3MDIwNDE2NTVaMGsx\n" +
        "CzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpTb21lLVN0YXRlMRIwEAYDVQQHEwlTb21l\n" +
        "LUNpdHkxETAPBgNVBAoTCFNvbWUtT3JnMSAwHgYDVQQDExdsb2NhbGhvc3Qgb3U9\n" +
        "U1NMLVNlcnZlcjCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAJoM4I+o\n" +
        "An5a7+2yQq0ITpG6wq2bedebD/3S+BUvGYmAEAACGW0nwpDXpSNTdG5kKHwkqu3q\n" +
        "IVnco1y1yUIxT6Le+wl8c+2IBDTxFa09YM3KxROZ05+bspJwy7pLPSCWrb6SU+1U\n" +
        "O8UUvc/UD8sFT/0rnuBQu2UTksDWvU0CDHC2ZdR9tE3D3ywIntI+aTJGb2/K0XOk\n" +
        "lAfvFOPani/ArA4QM0xoefN5QNbpPMLmcOCJzqB6qIQohTI3CLDPsX9fvB+lPe/W\n" +
        "aKgXIV+H1Uu1zO54jd2xKGrA+2S9t3ACMwMLuLi7CIL2jgUn0TvmxaxNhVuhHaNI\n" +
        "XQMVdmNscSE+mM0CAwEAAaNzMHEwHQYDVR0OBBYEFFyvRLFIuFmaZFOdLqayCdMK\n" +
        "kgSDMAsGA1UdDwQEAwIF4DAXBgNVHREBAf8EDTALgglsb2NhbGhvc3QwCQYDVR0T\n" +
        "BAIwADAfBgNVHSMEGDAWgBTgA5D2T7tX5n6vXJQls4XaFgpRQDANBgkqhkiG9w0B\n" +
        "AQUFAAOCAQEAnSJJX1Yj5oA1zKtEHCe9yY2Jk0lY6MF6aN3PveASdgZUzS9im1SE\n" +
        "8ruQoLs34hMd899BquD+wO9GeI2q9BtwrakWJPoVSsYKjeGZkwCp1LYIXY5lA9zQ\n" +
        "lfyVYaattatNpuAFSIzbQkKK1l7AKqARFbgHaVw/maC9U2XbTs9GYZMJe4FA/1z+\n" +
        "TOv0rN4fOK2yYCj2Dp9G5wePIJqk4Y+rVJl2gtiecMTamIVxrztU5AG0noPQe8aN\n" +
        "H+0lCIkF6YeXdlqjhcP4Wde7O1rby+1d/6whuZriZQq83tHcU5SYRJeRsxtrgAub\n" +
        "V7OuXHw1yjlx906PStfrCyXash4XSLjrCQ==\n" +
        "-----END CERTIFICATE-----";

    static String clientCertStr =
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIDpTCCAo2gAwIBAgIEWXLa2zANBgkqhkiG9w0BAQUFADBJMQswCQYDVQQGEwJV\n" +
        "czETMBEGA1UECBMKU29tZS1TdGF0ZTESMBAGA1UEBxMJU29tZS1DaXR5MREwDwYD\n" +
        "VQQKEwhTb21lLU9yZzAeFw0yNDA3MDEwNDE2NTJaFw0zNDA3MDIwNDE2NTJaMGsx\n" +
        "CzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpTb21lLVN0YXRlMRIwEAYDVQQHEwlTb21l\n" +
        "LUNpdHkxETAPBgNVBAoTCFNvbWUtT3JnMSAwHgYDVQQDExdsb2NhbGhvc3Qgb3U9\n" +
        "U1NMLUNsaWVudDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMy/kjym\n" +
        "V3QfWK3HaYhvWTJHUGAi5JhJDj4duLriO7ZxW/1kAm0NUHdybqg9XdS9H3ZR3JrQ\n" +
        "1j7QMaUkWiy+d/qIofoGQcgPR3BHJJlQUkRbMGJbZTXEKLBc7tAb6zkrC6Gslkja\n" +
        "Vmzg4+bj3UXLUTONQEPX8KQxqrXA30vfKwrtfhAMrh+WohAea9D5N4vfDQ4CNfhY\n" +
        "vG61Vw4v6iDmc5rla4JwJbtRmnyd4lA9zx4kPpJVzyqtDYSPqEMkza1QZHTCc7bh\n" +
        "khyyK4wte5amQWFcG494KFFA7UGQzh24JoFHa+NXQXROIPBaG5c3kYYZxfJtBMl4\n" +
        "K1oWvPwrcVvQAE8CAwEAAaNzMHEwHQYDVR0OBBYEFM1F4gWSiKPHSSjn0ze3E5L7\n" +
        "sTbEMAsGA1UdDwQEAwIF4DAXBgNVHREBAf8EDTALgglsb2NhbGhvc3QwCQYDVR0T\n" +
        "BAIwADAfBgNVHSMEGDAWgBTgA5D2T7tX5n6vXJQls4XaFgpRQDANBgkqhkiG9w0B\n" +
        "AQUFAAOCAQEAI27pXYANs4bJzReBM71bqsBlTGuf+u4y6Ynh0McdXEN+lKyDr5GQ\n" +
        "TCZhjf5rGqpuYTmzJErcksjK8oCwBUEMs93tt4FCmh5O8oBscmKLvdTNI3186G/j\n" +
        "Z4lqeRnd9ldiEvrrzWbD0thAWhzdf5+yNOkq1hRSum6omw2poQO/xA2SPVnkqY4g\n" +
        "QTmZgXCd0GiY/F9JSpLlosFRYfYeSVYLtoxX2wgq8KME3KEEolzQkE8TjRzmLnpj\n" +
        "nDJAZVkEXXGQWqjbajBCV1sL386hH/ojcfNXEsQcZjs3dzIop/ut7oZRTIAv3chb\n" +
        "n6cVB/orWu6TAF+mQyIbQFIVZgGEMp5xIQ==\n" +
        "-----END CERTIFICATE-----";

    static byte serverPrivateExponent[] = {
        (byte)0x68, (byte)0x87, (byte)0x36, (byte)0x54,
        (byte)0xa3, (byte)0xc6, (byte)0xd5, (byte)0x5f,
        (byte)0xf5, (byte)0x0f, (byte)0x4f, (byte)0x76,
        (byte)0xc8, (byte)0x9c, (byte)0x2b, (byte)0x5b,
        (byte)0xdc, (byte)0xe2, (byte)0xbe, (byte)0x14,
        (byte)0x12, (byte)0x2f, (byte)0xc7, (byte)0x0a,
        (byte)0xa9, (byte)0xcb, (byte)0x5e, (byte)0x04,
        (byte)0x59, (byte)0xca, (byte)0x35, (byte)0x2f,
        (byte)0x8d, (byte)0x2b, (byte)0xc4, (byte)0x40,
        (byte)0xe6, (byte)0x7d, (byte)0x25, (byte)0x1b,
        (byte)0x4d, (byte)0x07, (byte)0xc3, (byte)0x99,
        (byte)0x9c, (byte)0x16, (byte)0x4f, (byte)0xa5,
        (byte)0xdc, (byte)0xde, (byte)0xb0, (byte)0x90,
        (byte)0xf0, (byte)0xde, (byte)0x22, (byte)0x70,
        (byte)0x80, (byte)0xf4, (byte)0xa6, (byte)0x70,
        (byte)0xe2, (byte)0x96, (byte)0x3d, (byte)0x18,
        (byte)0x21, (byte)0xbf, (byte)0x2b, (byte)0x27,
        (byte)0xa4, (byte)0x2d, (byte)0xd7, (byte)0xae,
        (byte)0x2b, (byte)0x12, (byte)0x2f, (byte)0x08,
        (byte)0x36, (byte)0xee, (byte)0x99, (byte)0x94,
        (byte)0xed, (byte)0xf6, (byte)0xa7, (byte)0xd9,
        (byte)0x1d, (byte)0xa2, (byte)0xf3, (byte)0x1f,
        (byte)0x44, (byte)0xa4, (byte)0x28, (byte)0x4b,
        (byte)0x67, (byte)0x35, (byte)0xd6, (byte)0xa8,
        (byte)0x1b, (byte)0xf8, (byte)0x84, (byte)0x34,
        (byte)0x34, (byte)0x84, (byte)0xbd, (byte)0xec,
        (byte)0x9e, (byte)0x03, (byte)0x08, (byte)0x3c,
        (byte)0x93, (byte)0x20, (byte)0x8e, (byte)0xaf,
        (byte)0x15, (byte)0xcb, (byte)0x1f, (byte)0x20,
        (byte)0x08, (byte)0x97, (byte)0xc4, (byte)0x19,
        (byte)0x3e, (byte)0xfa, (byte)0x36, (byte)0xc6,
        (byte)0xab, (byte)0x0e, (byte)0x2f, (byte)0xe7,
        (byte)0xb3, (byte)0xc0, (byte)0xa7, (byte)0xbc,
        (byte)0xe4, (byte)0xe0, (byte)0xa6, (byte)0x08,
        (byte)0x1c, (byte)0x69, (byte)0x20, (byte)0x4d,
        (byte)0x78, (byte)0xbd, (byte)0x7a, (byte)0xe5,
        (byte)0x25, (byte)0x48, (byte)0x60, (byte)0x9e,
        (byte)0x2e, (byte)0x50, (byte)0x8d, (byte)0x36,
        (byte)0x1e, (byte)0x07, (byte)0xe9, (byte)0xd5,
        (byte)0x0d, (byte)0x39, (byte)0x67, (byte)0x41,
        (byte)0x42, (byte)0x24, (byte)0xdb, (byte)0x87,
        (byte)0xe5, (byte)0x77, (byte)0x76, (byte)0xfd,
        (byte)0x5e, (byte)0xd5, (byte)0xc6, (byte)0xe5,
        (byte)0xd3, (byte)0xb0, (byte)0x98, (byte)0x71,
        (byte)0x48, (byte)0x69, (byte)0x47, (byte)0x4f,
        (byte)0x46, (byte)0x05, (byte)0x0c, (byte)0x9e,
        (byte)0x58, (byte)0x45, (byte)0x2e, (byte)0xe2,
        (byte)0x27, (byte)0xd0, (byte)0xf6, (byte)0x11,
        (byte)0x05, (byte)0x78, (byte)0xad, (byte)0x83,
        (byte)0x5a, (byte)0x5b, (byte)0xec, (byte)0xd7,
        (byte)0x2e, (byte)0x26, (byte)0x5a, (byte)0xa5,
        (byte)0x4f, (byte)0x9e, (byte)0x52, (byte)0x84,
        (byte)0x2c, (byte)0x1f, (byte)0x59, (byte)0x1a,
        (byte)0x78, (byte)0x56, (byte)0x0a, (byte)0x44,
        (byte)0x54, (byte)0xc6, (byte)0x37, (byte)0x64,
        (byte)0x01, (byte)0xca, (byte)0xe4, (byte)0xa8,
        (byte)0x01, (byte)0xc7, (byte)0x86, (byte)0xc1,
        (byte)0xb4, (byte)0xd6, (byte)0x6c, (byte)0x7a,
        (byte)0x15, (byte)0x9a, (byte)0x65, (byte)0x69,
        (byte)0x46, (byte)0x9e, (byte)0xfd, (byte)0xf6,
        (byte)0x08, (byte)0x17, (byte)0x0c, (byte)0x6c,
        (byte)0xac, (byte)0x38, (byte)0xbd, (byte)0xc2,
        (byte)0xcd, (byte)0xda, (byte)0xef, (byte)0x54,
        (byte)0x7a, (byte)0x48, (byte)0x92, (byte)0x4d
    };

    static byte serverModulus[] = {
        (byte)0x00, (byte)0x9a, (byte)0x0c, (byte)0xe0,
        (byte)0x8f, (byte)0xa8, (byte)0x02, (byte)0x7e,
        (byte)0x5a, (byte)0xef, (byte)0xed, (byte)0xb2,
        (byte)0x42, (byte)0xad, (byte)0x08, (byte)0x4e,
        (byte)0x91, (byte)0xba, (byte)0xc2, (byte)0xad,
        (byte)0x9b, (byte)0x79, (byte)0xd7, (byte)0x9b,
        (byte)0x0f, (byte)0xfd, (byte)0xd2, (byte)0xf8,
        (byte)0x15, (byte)0x2f, (byte)0x19, (byte)0x89,
        (byte)0x80, (byte)0x10, (byte)0x00, (byte)0x02,
        (byte)0x19, (byte)0x6d, (byte)0x27, (byte)0xc2,
        (byte)0x90, (byte)0xd7, (byte)0xa5, (byte)0x23,
        (byte)0x53, (byte)0x74, (byte)0x6e, (byte)0x64,
        (byte)0x28, (byte)0x7c, (byte)0x24, (byte)0xaa,
        (byte)0xed, (byte)0xea, (byte)0x21, (byte)0x59,
        (byte)0xdc, (byte)0xa3, (byte)0x5c, (byte)0xb5,
        (byte)0xc9, (byte)0x42, (byte)0x31, (byte)0x4f,
        (byte)0xa2, (byte)0xde, (byte)0xfb, (byte)0x09,
        (byte)0x7c, (byte)0x73, (byte)0xed, (byte)0x88,
        (byte)0x04, (byte)0x34, (byte)0xf1, (byte)0x15,
        (byte)0xad, (byte)0x3d, (byte)0x60, (byte)0xcd,
        (byte)0xca, (byte)0xc5, (byte)0x13, (byte)0x99,
        (byte)0xd3, (byte)0x9f, (byte)0x9b, (byte)0xb2,
        (byte)0x92, (byte)0x70, (byte)0xcb, (byte)0xba,
        (byte)0x4b, (byte)0x3d, (byte)0x20, (byte)0x96,
        (byte)0xad, (byte)0xbe, (byte)0x92, (byte)0x53,
        (byte)0xed, (byte)0x54, (byte)0x3b, (byte)0xc5,
        (byte)0x14, (byte)0xbd, (byte)0xcf, (byte)0xd4,
        (byte)0x0f, (byte)0xcb, (byte)0x05, (byte)0x4f,
        (byte)0xfd, (byte)0x2b, (byte)0x9e, (byte)0xe0,
        (byte)0x50, (byte)0xbb, (byte)0x65, (byte)0x13,
        (byte)0x92, (byte)0xc0, (byte)0xd6, (byte)0xbd,
        (byte)0x4d, (byte)0x02, (byte)0x0c, (byte)0x70,
        (byte)0xb6, (byte)0x65, (byte)0xd4, (byte)0x7d,
        (byte)0xb4, (byte)0x4d, (byte)0xc3, (byte)0xdf,
        (byte)0x2c, (byte)0x08, (byte)0x9e, (byte)0xd2,
        (byte)0x3e, (byte)0x69, (byte)0x32, (byte)0x46,
        (byte)0x6f, (byte)0x6f, (byte)0xca, (byte)0xd1,
        (byte)0x73, (byte)0xa4, (byte)0x94, (byte)0x07,
        (byte)0xef, (byte)0x14, (byte)0xe3, (byte)0xda,
        (byte)0x9e, (byte)0x2f, (byte)0xc0, (byte)0xac,
        (byte)0x0e, (byte)0x10, (byte)0x33, (byte)0x4c,
        (byte)0x68, (byte)0x79, (byte)0xf3, (byte)0x79,
        (byte)0x40, (byte)0xd6, (byte)0xe9, (byte)0x3c,
        (byte)0xc2, (byte)0xe6, (byte)0x70, (byte)0xe0,
        (byte)0x89, (byte)0xce, (byte)0xa0, (byte)0x7a,
        (byte)0xa8, (byte)0x84, (byte)0x28, (byte)0x85,
        (byte)0x32, (byte)0x37, (byte)0x08, (byte)0xb0,
        (byte)0xcf, (byte)0xb1, (byte)0x7f, (byte)0x5f,
        (byte)0xbc, (byte)0x1f, (byte)0xa5, (byte)0x3d,
        (byte)0xef, (byte)0xd6, (byte)0x68, (byte)0xa8,
        (byte)0x17, (byte)0x21, (byte)0x5f, (byte)0x87,
        (byte)0xd5, (byte)0x4b, (byte)0xb5, (byte)0xcc,
        (byte)0xee, (byte)0x78, (byte)0x8d, (byte)0xdd,
        (byte)0xb1, (byte)0x28, (byte)0x6a, (byte)0xc0,
        (byte)0xfb, (byte)0x64, (byte)0xbd, (byte)0xb7,
        (byte)0x70, (byte)0x02, (byte)0x33, (byte)0x03,
        (byte)0x0b, (byte)0xb8, (byte)0xb8, (byte)0xbb,
        (byte)0x08, (byte)0x82, (byte)0xf6, (byte)0x8e,
        (byte)0x05, (byte)0x27, (byte)0xd1, (byte)0x3b,
        (byte)0xe6, (byte)0xc5, (byte)0xac, (byte)0x4d,
        (byte)0x85, (byte)0x5b, (byte)0xa1, (byte)0x1d,
        (byte)0xa3, (byte)0x48, (byte)0x5d, (byte)0x03,
        (byte)0x15, (byte)0x76, (byte)0x63, (byte)0x6c,
        (byte)0x71, (byte)0x21, (byte)0x3e, (byte)0x98,
        (byte)0xcd
    };

    static byte clientPrivateExponent[] = {
        (byte)0x62, (byte)0xb2, (byte)0xd6, (byte)0x63,
        (byte)0xb6, (byte)0x2b, (byte)0xe2, (byte)0x26,
        (byte)0x5a, (byte)0x31, (byte)0x2b, (byte)0x37,
        (byte)0x8c, (byte)0x35, (byte)0x60, (byte)0xe2,
        (byte)0x03, (byte)0xce, (byte)0x93, (byte)0x09,
        (byte)0x3e, (byte)0xf8, (byte)0xc9, (byte)0xfe,
        (byte)0xbb, (byte)0xa2, (byte)0xc8, (byte)0x32,
        (byte)0x0e, (byte)0x6c, (byte)0x8a, (byte)0x7e,
        (byte)0x0a, (byte)0xc2, (byte)0x13, (byte)0x3b,
        (byte)0xb8, (byte)0x25, (byte)0xfa, (byte)0xec,
        (byte)0x19, (byte)0x95, (byte)0x8e, (byte)0x34,
        (byte)0x46, (byte)0xcf, (byte)0x0e, (byte)0x7b,
        (byte)0xe4, (byte)0x25, (byte)0x82, (byte)0x1a,
        (byte)0x7f, (byte)0x21, (byte)0x48, (byte)0x16,
        (byte)0x44, (byte)0x58, (byte)0x3f, (byte)0x35,
        (byte)0xd8, (byte)0xeb, (byte)0xd8, (byte)0x1a,
        (byte)0x45, (byte)0x53, (byte)0x0f, (byte)0x9b,
        (byte)0x84, (byte)0x8a, (byte)0x54, (byte)0x13,
        (byte)0x33, (byte)0xe4, (byte)0x97, (byte)0x97,
        (byte)0xf0, (byte)0x48, (byte)0x37, (byte)0xfb,
        (byte)0x5d, (byte)0x4f, (byte)0x8c, (byte)0x8f,
        (byte)0x35, (byte)0x63, (byte)0xe1, (byte)0xd9,
        (byte)0x62, (byte)0x73, (byte)0x1c, (byte)0x8e,
        (byte)0xd8, (byte)0xcd, (byte)0x2e, (byte)0x1a,
        (byte)0xe5, (byte)0x4c, (byte)0xb5, (byte)0x05,
        (byte)0x59, (byte)0x7a, (byte)0xdf, (byte)0xf1,
        (byte)0x68, (byte)0xeb, (byte)0x1c, (byte)0x5c,
        (byte)0xc6, (byte)0x10, (byte)0x44, (byte)0x8c,
        (byte)0x7d, (byte)0x42, (byte)0xc5, (byte)0x71,
        (byte)0x8a, (byte)0xe7, (byte)0x1b, (byte)0xaa,
        (byte)0x17, (byte)0x03, (byte)0x6a, (byte)0xa0,
        (byte)0xc0, (byte)0x6b, (byte)0x97, (byte)0x50,
        (byte)0x17, (byte)0xad, (byte)0x6e, (byte)0x5e,
        (byte)0xd9, (byte)0xdb, (byte)0x6f, (byte)0x3e,
        (byte)0xe9, (byte)0x3f, (byte)0x35, (byte)0xc3,
        (byte)0x45, (byte)0xbc, (byte)0xe8, (byte)0x3d,
        (byte)0x5a, (byte)0xb4, (byte)0xb9, (byte)0x3f,
        (byte)0x53, (byte)0x80, (byte)0x64, (byte)0xdc,
        (byte)0x12, (byte)0x24, (byte)0x35, (byte)0x35,
        (byte)0xbd, (byte)0x98, (byte)0xbb, (byte)0x8d,
        (byte)0xfa, (byte)0x19, (byte)0xa3, (byte)0x5e,
        (byte)0x9e, (byte)0xac, (byte)0x70, (byte)0x4a,
        (byte)0xfc, (byte)0x8d, (byte)0xae, (byte)0x55,
        (byte)0x8b, (byte)0x71, (byte)0x81, (byte)0x0e,
        (byte)0x4d, (byte)0xc8, (byte)0x2f, (byte)0x87,
        (byte)0xb0, (byte)0x44, (byte)0xf7, (byte)0x4f,
        (byte)0xdc, (byte)0xa8, (byte)0xc8, (byte)0x50,
        (byte)0xb5, (byte)0x95, (byte)0x24, (byte)0x63,
        (byte)0x74, (byte)0x13, (byte)0x54, (byte)0x58,
        (byte)0xde, (byte)0xfc, (byte)0xe0, (byte)0x75,
        (byte)0xeb, (byte)0xf4, (byte)0x06, (byte)0x58,
        (byte)0x83, (byte)0x12, (byte)0x4c, (byte)0x56,
        (byte)0xc4, (byte)0xc4, (byte)0x18, (byte)0x0c,
        (byte)0xea, (byte)0xa3, (byte)0xe7, (byte)0x25,
        (byte)0xa3, (byte)0xde, (byte)0x19, (byte)0x23,
        (byte)0xa2, (byte)0x5a, (byte)0x2a, (byte)0xb6,
        (byte)0x56, (byte)0x04, (byte)0xbc, (byte)0x65,
        (byte)0xba, (byte)0x7c, (byte)0x0a, (byte)0xf4,
        (byte)0x91, (byte)0x10, (byte)0x22, (byte)0x88,
        (byte)0x3f, (byte)0x9d, (byte)0xbe, (byte)0x58,
        (byte)0x43, (byte)0x4c, (byte)0x2e, (byte)0xad,
        (byte)0xdb, (byte)0xd6, (byte)0x32, (byte)0xcf,
        (byte)0x8e, (byte)0xb5, (byte)0x05, (byte)0x55,
        (byte)0x39, (byte)0x8b, (byte)0xe1, (byte)0x01
    };

    static byte clientModulus[] = {
        (byte)0x00, (byte)0xcc, (byte)0xbf, (byte)0x92,
        (byte)0x3c, (byte)0xa6, (byte)0x57, (byte)0x74,
        (byte)0x1f, (byte)0x58, (byte)0xad, (byte)0xc7,
        (byte)0x69, (byte)0x88, (byte)0x6f, (byte)0x59,
        (byte)0x32, (byte)0x47, (byte)0x50, (byte)0x60,
        (byte)0x22, (byte)0xe4, (byte)0x98, (byte)0x49,
        (byte)0x0e, (byte)0x3e, (byte)0x1d, (byte)0xb8,
        (byte)0xba, (byte)0xe2, (byte)0x3b, (byte)0xb6,
        (byte)0x71, (byte)0x5b, (byte)0xfd, (byte)0x64,
        (byte)0x02, (byte)0x6d, (byte)0x0d, (byte)0x50,
        (byte)0x77, (byte)0x72, (byte)0x6e, (byte)0xa8,
        (byte)0x3d, (byte)0x5d, (byte)0xd4, (byte)0xbd,
        (byte)0x1f, (byte)0x76, (byte)0x51, (byte)0xdc,
        (byte)0x9a, (byte)0xd0, (byte)0xd6, (byte)0x3e,
        (byte)0xd0, (byte)0x31, (byte)0xa5, (byte)0x24,
        (byte)0x5a, (byte)0x2c, (byte)0xbe, (byte)0x77,
        (byte)0xfa, (byte)0x88, (byte)0xa1, (byte)0xfa,
        (byte)0x06, (byte)0x41, (byte)0xc8, (byte)0x0f,
        (byte)0x47, (byte)0x70, (byte)0x47, (byte)0x24,
        (byte)0x99, (byte)0x50, (byte)0x52, (byte)0x44,
        (byte)0x5b, (byte)0x30, (byte)0x62, (byte)0x5b,
        (byte)0x65, (byte)0x35, (byte)0xc4, (byte)0x28,
        (byte)0xb0, (byte)0x5c, (byte)0xee, (byte)0xd0,
        (byte)0x1b, (byte)0xeb, (byte)0x39, (byte)0x2b,
        (byte)0x0b, (byte)0xa1, (byte)0xac, (byte)0x96,
        (byte)0x48, (byte)0xda, (byte)0x56, (byte)0x6c,
        (byte)0xe0, (byte)0xe3, (byte)0xe6, (byte)0xe3,
        (byte)0xdd, (byte)0x45, (byte)0xcb, (byte)0x51,
        (byte)0x33, (byte)0x8d, (byte)0x40, (byte)0x43,
        (byte)0xd7, (byte)0xf0, (byte)0xa4, (byte)0x31,
        (byte)0xaa, (byte)0xb5, (byte)0xc0, (byte)0xdf,
        (byte)0x4b, (byte)0xdf, (byte)0x2b, (byte)0x0a,
        (byte)0xed, (byte)0x7e, (byte)0x10, (byte)0x0c,
        (byte)0xae, (byte)0x1f, (byte)0x96, (byte)0xa2,
        (byte)0x10, (byte)0x1e, (byte)0x6b, (byte)0xd0,
        (byte)0xf9, (byte)0x37, (byte)0x8b, (byte)0xdf,
        (byte)0x0d, (byte)0x0e, (byte)0x02, (byte)0x35,
        (byte)0xf8, (byte)0x58, (byte)0xbc, (byte)0x6e,
        (byte)0xb5, (byte)0x57, (byte)0x0e, (byte)0x2f,
        (byte)0xea, (byte)0x20, (byte)0xe6, (byte)0x73,
        (byte)0x9a, (byte)0xe5, (byte)0x6b, (byte)0x82,
        (byte)0x70, (byte)0x25, (byte)0xbb, (byte)0x51,
        (byte)0x9a, (byte)0x7c, (byte)0x9d, (byte)0xe2,
        (byte)0x50, (byte)0x3d, (byte)0xcf, (byte)0x1e,
        (byte)0x24, (byte)0x3e, (byte)0x92, (byte)0x55,
        (byte)0xcf, (byte)0x2a, (byte)0xad, (byte)0x0d,
        (byte)0x84, (byte)0x8f, (byte)0xa8, (byte)0x43,
        (byte)0x24, (byte)0xcd, (byte)0xad, (byte)0x50,
        (byte)0x64, (byte)0x74, (byte)0xc2, (byte)0x73,
        (byte)0xb6, (byte)0xe1, (byte)0x92, (byte)0x1c,
        (byte)0xb2, (byte)0x2b, (byte)0x8c, (byte)0x2d,
        (byte)0x7b, (byte)0x96, (byte)0xa6, (byte)0x41,
        (byte)0x61, (byte)0x5c, (byte)0x1b, (byte)0x8f,
        (byte)0x78, (byte)0x28, (byte)0x51, (byte)0x40,
        (byte)0xed, (byte)0x41, (byte)0x90, (byte)0xce,
        (byte)0x1d, (byte)0xb8, (byte)0x26, (byte)0x81,
        (byte)0x47, (byte)0x6b, (byte)0xe3, (byte)0x57,
        (byte)0x41, (byte)0x74, (byte)0x4e, (byte)0x20,
        (byte)0xf0, (byte)0x5a, (byte)0x1b, (byte)0x97,
        (byte)0x37, (byte)0x91, (byte)0x86, (byte)0x19,
        (byte)0xc5, (byte)0xf2, (byte)0x6d, (byte)0x04,
        (byte)0xc9, (byte)0x78, (byte)0x2b, (byte)0x5a,
        (byte)0x16, (byte)0xbc, (byte)0xfc, (byte)0x2b,
        (byte)0x71, (byte)0x5b, (byte)0xd0, (byte)0x00,
        (byte)0x4f
    };

    static char passphrase[] = "passphrase".toCharArray();

    /*
     * Is the server ready to serve?
     */
    volatile static boolean serverReady = false;

    /*
     * Turn on SSL debugging?
     */
    static boolean debug = false;

    /*
     * Define the server side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    void doServerSide() throws Exception {
        SSLContext context = getSSLContext(trusedCertStr, serverCertStr,
            serverModulus, serverPrivateExponent, passphrase);
        SSLServerSocketFactory sslssf = context.getServerSocketFactory();

        SSLServerSocket sslServerSocket =
            (SSLServerSocket) sslssf.createServerSocket(serverPort);
        serverPort = sslServerSocket.getLocalPort();

        // enable endpoint identification
        // ignore, we may test the feature when known how to parse client
        // hostname
        //SSLParameters params = sslServerSocket.getSSLParameters();
        //params.setEndpointIdentificationAlgorithm("HTTPS");
        //sslServerSocket.setSSLParameters(params);

        /*
         * Signal Client, we're ready for his connect.
         */
        serverReady = true;

        SSLSocket sslSocket = (SSLSocket) sslServerSocket.accept();
        sslSocket.setNeedClientAuth(true);

        InputStream sslIS = sslSocket.getInputStream();
        OutputStream sslOS = sslSocket.getOutputStream();

        sslIS.read();
        sslOS.write(85);
        sslOS.flush();

        sslSocket.close();

    }

    /*
     * Define the client side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    void doClientSide() throws Exception {
        /*
         * Wait for server to get started.
         */
        while (!serverReady) {
            Thread.sleep(50);
        }

        SSLContext context = getSSLContext(trusedCertStr, clientCertStr,
            clientModulus, clientPrivateExponent, passphrase);

        SSLSocketFactory sslsf = context.getSocketFactory();
        SSLSocket sslSocket = (SSLSocket)
            sslsf.createSocket("localhost", serverPort);

        // enable endpoint identification
        SSLParameters params = sslSocket.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS");
        sslSocket.setSSLParameters(params);

        InputStream sslIS = sslSocket.getInputStream();
        OutputStream sslOS = sslSocket.getOutputStream();

        sslOS.write(280);
        sslOS.flush();
        sslIS.read();

        sslSocket.close();

    }

    // get the ssl context
    private static SSLContext getSSLContext(String trusedCertStr,
            String keyCertStr, byte[] modulus,
            byte[] privateExponent, char[] passphrase) throws Exception {

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

        if (keyCertStr != null) {
            // generate the private key.
            RSAPrivateKeySpec priKeySpec = new RSAPrivateKeySpec(
                                            new BigInteger(modulus),
                                            new BigInteger(privateExponent));
            KeyFactory kf = KeyFactory.getInstance("RSA");
            RSAPrivateKey priKey =
                    (RSAPrivateKey)kf.generatePrivate(priKeySpec);

            // generate certificate chain
            is = new ByteArrayInputStream(keyCertStr.getBytes());
            Certificate keyCert = cf.generateCertificate(is);
            is.close();

            Certificate[] chain = new Certificate[2];
            chain[0] = keyCert;
            chain[1] = trusedCert;

            // import the key entry.
            ks.setKeyEntry("Whatever", priKey, passphrase, chain);
        }

        // create SSL context
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(ks);

        TrustManager tms[] = tmf.getTrustManagers();
        if (tms == null || tms.length == 0) {
            throw new Exception("unexpected trust manager implementation");
        } else {
           if (!(tms[0] instanceof X509ExtendedTrustManager)) {
               throw new Exception("unexpected trust manager implementation: "
                                + tms[0].getClass().getCanonicalName());
           }
        }


        SSLContext ctx = SSLContext.getInstance("TLS");

        if (keyCertStr != null) {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, passphrase);

            ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        } else {
            ctx.init(null, tmf.getTrustManagers(), null);
        }

        return ctx;
    }

    /*
     * =============================================================
     * The remainder is just support stuff
     */

    // use any free port by default
    volatile int serverPort = 0;

    volatile Exception serverException = null;
    volatile Exception clientException = null;

    static class Test {
        String tlsDisAlgs;
        String certPathDisAlgs;
        boolean fail;
        Test(String tlsDisAlgs, String certPathDisAlgs, boolean fail) {
            this.tlsDisAlgs = tlsDisAlgs;
            this.certPathDisAlgs = certPathDisAlgs;
            this.fail = fail;
        }
    }

    static Test[] tests = {
        // SHA1 is used in this test case, don't disable SHA1 algorithm.
        new Test(
            "SSLv3, RC4, DH keySize < 768",
            "MD2, RSA keySize < 1024",
            false),
        // Disable SHA1 but only if cert chains back to public root CA, should
        // pass because the SHA1 cert in this test case is issued by test CA
        new Test(
            "SSLv3, RC4, DH keySize < 768",
            "MD2, SHA1 jdkCA, RSA keySize < 1024",
            false),
        // Disable SHA1 alg via TLS property and expect failure
        new Test(
            "SSLv3, SHA1, RC4, DH keySize < 768",
            "MD2, RSA keySize < 1024",
            true),
        // Disable SHA1 alg via certpath property and expect failure
        new Test(
            "SSLv3, RC4, DH keySize < 768",
            "MD2, SHA1, RSA keySize < 1024",
            true),
    };

    public static void main(String args[]) throws Exception {
        // Disable KeyManager's algorithm constraints checking as this test
        // is about TrustManager's constraints check.
        System.setProperty("jdk.tls.SunX509KeyManager.certChecking", "false");

        if (args.length != 1) {
            throw new Exception("Incorrect number of arguments");
        }
        Test test = tests[Integer.parseInt(args[0])];
        Security.setProperty("jdk.tls.disabledAlgorithms", test.tlsDisAlgs);
        Security.setProperty("jdk.certpath.disabledAlgorithms",
                             test.certPathDisAlgs);

        if (debug) {
            System.setProperty("javax.net.debug", "all");
        }

        /*
         * Start the tests.
         */
        try {
            new PKIXExtendedTM();
            if (test.fail) {
                throw new Exception("Expected SHA1 certificate to be blocked");
            }
        } catch (Exception e) {
            if (test.fail) {
                // find expected cause
                boolean correctReason = false;
                Throwable cause = e.getCause();
                while (cause != null) {
                    if (cause instanceof CertPathValidatorException) {
                        CertPathValidatorException cpve =
                            (CertPathValidatorException)cause;
                        if (cpve.getReason() == CertPathValidatorException.BasicReason.ALGORITHM_CONSTRAINED) {
                            correctReason = true;
                            break;
                        }
                    }
                    cause = cause.getCause();
                }
                if (!correctReason) {
                    throw new Exception("Unexpected exception", e);
                }
            } else {
                throw e;
            }
        }
    }

    Thread clientThread = null;
    Thread serverThread = null;
    /*
     * Primary constructor, used to drive remainder of the test.
     *
     * Fork off the other side, then do your work.
     */
    PKIXExtendedTM() throws Exception {
        if (separateServerThread) {
            startServer(true);
            startClient(false);
        } else {
            startClient(true);
            startServer(false);
        }

        /*
         * Wait for other side to close down.
         */
        if (separateServerThread) {
            serverThread.join();
        } else {
            clientThread.join();
        }

        /*
         * When we get here, the test is pretty much over.
         *
         * If the main thread excepted, that propagates back
         * immediately.  If the other thread threw an exception, we
         * should report back.
         */
        if (serverException != null)
            throw serverException;
        if (clientException != null)
            throw clientException;
    }

    void startServer(boolean newThread) throws Exception {
        if (newThread) {
            serverThread = new Thread() {
                public void run() {
                    try {
                        doServerSide();
                    } catch (Exception e) {
                        /*
                         * Our server thread just died.
                         *
                         * Release the client, if not active already...
                         */
                        System.err.println("Server died...");
                        serverReady = true;
                        serverException = e;
                    }
                }
            };
            serverThread.start();
        } else {
            doServerSide();
        }
    }

    void startClient(boolean newThread) throws Exception {
        if (newThread) {
            clientThread = new Thread() {
                public void run() {
                    try {
                        doClientSide();
                    } catch (Exception e) {
                        /*
                         * Our client thread just died.
                         */
                        System.err.println("Client died...");
                        clientException = e;
                    }
                }
            };
            clientThread.start();
        } else {
            doClientSide();
        }
    }

}
