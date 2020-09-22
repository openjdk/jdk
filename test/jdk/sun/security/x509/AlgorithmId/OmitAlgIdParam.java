/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8252377
 * @summary The AlgorithmIdentifier for ECDSA should omit the parameters field
 */

import java.util.Base64;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class OmitAlgIdParam {

    // ECDSA certificate (signature algorithm SHA224withECDSA) contains two
    // extra tag_Nulls as the encoding does not omit the parameters field in
    // AlgorithmIdentifier.
    static String SHA224ECDSA_NULL_TAG =
        "-----BEGIN CERTIFICATE-----" +
        "MIIBMzCB2aADAgECAghC1WSCwCzuAzAMBggqhkjOPQQDAQUAMA0xCzAJBgNVBAMT" +
        "Am1lMB4XDTIwMDkwOTIxMTMzMloXDTIwMTIwODIxMTMzMlowDTELMAkGA1UEAxMC" +
        "bWUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARal51XdJhVitpkxpISQXe3t31G" +
        "YquhksesPzhiX2GYf9L/uonaltnnyl0fj25yXFXunHav3yCvg3SDnQbGVsihoyEw" +
        "HzAdBgNVHQ4EFgQUouV8yKNIcusBMb/QJMX8QTR4vdwwDAYIKoZIzj0EAwEFAANH" +
        "ADBEAiAvYSRCF8/RKIbF/Wpe5zQZxV7w7YvtM5wPGUAXlKV2wgIgBwL3CYQgcGjC" +
        "KT7IFMn6RFR+s6+Qw+fkUxotLsOfL0w=" +
        "-----END CERTIFICATE-----";

    // ECDSA certificate (signature algorithm SHA256withECDSA) contains two
    // extra tag_Nulls as the encoding does not omit the parameters field in
    // AlgorithmIdentifier.
    static String SHA256ECDSA_NULL_TAG =
        "-----BEGIN CERTIFICATE-----" +
        "MIIBNDCB2aADAgECAggjyfYsAqKObjAMBggqhkjOPQQDAgUAMA0xCzAJBgNVBAMT" +
        "Am1lMB4XDTIwMDkwOTIxMTI1NFoXDTIwMTIwODIxMTI1NFowDTELMAkGA1UEAxMC" +
        "bWUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAS11YiZCLVStBIi+nmYcwHdBwfw" +
        "qlcfEcDlHqFDXlf4iiaPiehKRrbU8jvzZmUzcP1zvon1xScJt/dFZrhVxKaToyEw" +
        "HzAdBgNVHQ4EFgQUOj/egZaejfmIRLK5Yo3+LFeKHf0wDAYIKoZIzj0EAwIFAANI" +
        "ADBFAiEAs4Zmuis15OEtw8fRIJCPT2uLLyJC9+y6/Odms/HZCkACIC0Awlg5KWiw" +
        "rdEi8/gfz+HivCJzie4xVxCrvY7r59AE" +
        "-----END CERTIFICATE-----";

    // ECDSA certificate (signature algorithm SHA384withECDSA) contains two
    // extra tag_Nulls as the encoding does not omit the parameters field in
    // AlgorithmIdentifier.
    static String SHA384ECDSA_NULL_TAG =
        "-----BEGIN CERTIFICATE-----" +
        "MIIBNDCB2qADAgECAgkA7mCl6MfxgZwwDAYIKoZIzj0EAwMFADANMQswCQYDVQQD" +
        "EwJtZTAeFw0yMDA5MDkyMTEzNDBaFw0yMDEyMDgyMTEzNDBaMA0xCzAJBgNVBAMT" +
        "Am1lMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEsWvOnongREulQaDLamkMkFjn" +
        "Bseo05m+7ikc6G5gFXkAlbM2JtEU2+36xvw9c5YFofNxkBm0SrzN94TQSYrfb6Mh" +
        "MB8wHQYDVR0OBBYEFP7eD5LpWGneADhh5AhUT1kpXxF6MAwGCCqGSM49BAMDBQAD" +
        "RwAwRAIgTStKcy1Xqx5uXMinPqbr9h4f8RZ5zhNX2YsfdN9ehJkCIE9dxVLRMTjO" +
        "4Wszk0B6yq1TMl26bcKVlMlHHrlWrmE9" +
        "-----END CERTIFICATE-----";

    // ECDSA certificate (signature algorithm SHA512withECDSA) contains two
    // extra tag_Nulls as the encoding does not omit the parameters field in
    // AlgorithmIdentifier.
    static String SHA512ECDSA_NULL_TAG =
        "-----BEGIN CERTIFICATE-----" +
        "MIIBNDCB2qADAgECAgkAkwD7K4YgR+AwDAYIKoZIzj0EAwQFADANMQswCQYDVQQD" +
        "EwJtZTAeFw0yMDA5MDkyMTEzNDdaFw0yMDEyMDgyMTEzNDdaMA0xCzAJBgNVBAMT" +
        "Am1lMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE0Sx7489MAJOerSROT9fvxSHK" +
        "OjSUBubwBHK0q5zjaftI7YWp4YWZ5KQ04HjIldtUw4/VV8WNdydui3x923eQ86Mh" +
        "MB8wHQYDVR0OBBYEFHQoSpKVo3lc4suEzDFT68I6JSfLMAwGCCqGSM49BAMEBQAD" +
        "RwAwRAIgXRNfdhLR2wKcNd7JdUC2OwocHiLcQiH0j1/tIFuARSYCIGh6wbAp0YQh" +
        "bVZXB1IvxTu1+Tl/21X/SeRsho5xZA9t" +
        "-----END CERTIFICATE-----";

    // ECDSA certificate (signature algorithm SHA224withECDSA) does not have
    // tag_Null as the encoding omits the parameters field in AlgorithmIdentifier.
    static String SHA224ECDSA_NO_NULL_TAG =
        "-----BEGIN CERTIFICATE-----" +
        "MIIBMTCB16ADAgECAghUcKrh5qb/dDAKBggqhkjOPQQDATANMQswCQYDVQQDEwJt" +
        "ZTAeFw0yMDA5MDkyMDMzNDlaFw0yMDEyMDgyMDMzNDlaMA0xCzAJBgNVBAMTAm1l" +
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAETXEiW/fhG3EFcuRD1QVg58oWKPpq" +
        "OEBveyYy9yZHdqx0RkjYGyUwhrxzwFMh5qYsqy/CPWmOzd79lyaGXYxBO6MhMB8w" +
        "HQYDVR0OBBYEFE9VQptSlisl2V18fO76CnvisYIKMAoGCCqGSM49BAMBA0kAMEYC" +
        "IQC+BxCOw/R83Kw0fA7cc9Yy7v/LIb25NcjFg03lOcB8ygIhANSDNoi96zOsIYpu" +
        "k3fe2+fJiu13sVQxy6AM587OPX/0" +
        "-----END CERTIFICATE-----";

    // ECDSA certificate (signature algorithm SHA256withECDSA) does not have
    // tag_Null as the encoding omits the parameters field in AlgorithmIdentifier.
    static String SHA256ECDSA_NO_NULL_TAG =
        "-----BEGIN CERTIFICATE-----" +
        "MIIBMDCB16ADAgECAgg12v8SD5aE0DAKBggqhkjOPQQDAjANMQswCQYDVQQDEwJt" +
        "ZTAeFw0yMDA5MDgyMjI1MzdaFw0yMDEyMDcyMjI1MzdaMA0xCzAJBgNVBAMTAm1l" +
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEbLcM12vf/au1OTmnTYzPfvaycTn+" +
        "YxWpuI/MmsAQnchu/hudUAzQUT73VkWnIxvotjhk/Y/kmKIbiV69lxrr86MhMB8w" +
        "HQYDVR0OBBYEFNBKdpJYY1gJTMOL/5FFCx+W1zCJMAoGCCqGSM49BAMCA0gAMEUC" +
        "IG+52/OLQWmKpv59Pxq5B5ps+cDoC7MMlKH3xhTH336qAiEAlBLrOQf4cfH+dSK8" +
        "lOfI8dZGzls96a8AWG65si84IKo=" +
        "-----END CERTIFICATE-----";

    // ECDSA certificate (signature algorithm SHA384withECDSA) does not have
    // tag_Null as the encoding omits the parameters field in AlgorithmIdentifier.
    static String SHA384ECDSA_NO_NULL_TAG =
        "-----BEGIN CERTIFICATE-----" +
        "MIIBMTCB2KADAgECAgkAr59mIXOL19IwCgYIKoZIzj0EAwMwDTELMAkGA1UEAxMC" +
        "bWUwHhcNMjAwOTIyMDExMDQxWhcNMjAxMjIxMDExMDQxWjANMQswCQYDVQQDEwJt" +
        "ZTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABD82vloJlb+4N8WDbAdXdnTYSRQE" +
        "DEW3dYwdwENKzx9b2C51xJ/WaOJusgxyQrXa66f1f2fKeTmuuMNcN04b8nujITAf" +
        "MB0GA1UdDgQWBBRAAPDZ9AQmiTxE3xRQASUfP+SgcDAKBggqhkjOPQQDAwNIADBF" +
        "AiBbD32S3MFVyFDCaq3b1xjN62Q63Sa5o0hstCh5bhlqXQIhAONMxdiD4JK6XUQP" +
        "H+0ZIiu/ka5VMNLApkluM9bE8ly3" +
        "-----END CERTIFICATE-----";

    // ECDSA certificate (signature algorithm SHA512withECDSA) does not have
    // tag_Null as the encoding omits the parameters field in AlgorithmIdentifier.
    static String SHA512ECDSA_NO_NULL_TAG =
        "-----BEGIN CERTIFICATE-----" +
        "MIIBMDCB2KADAgECAgkAg+sRIEuL3NMwCgYIKoZIzj0EAwQwDTELMAkGA1UEAxMC" +
        "bWUwHhcNMjAwOTA5MjAzNTAyWhcNMjAxMjA4MjAzNTAyWjANMQswCQYDVQQDEwJt" +
        "ZTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABG+xByZLtCzjNqyPXBIEmvOVDrOk" +
        "3Kql8J5q9XPxjRIvk3fQRc9syGBkWfcdrS+yu66GIHPOApKadj0ed3BgejejITAf" +
        "MB0GA1UdDgQWBBQH29Q+6WslDk+b7WSU7Q7J999suzAKBggqhkjOPQQDBANHADBE" +
        "AiAjb5vgAofMtYF1H4Avv7lUDXebQ0q63d9nrI5167zWQwIgIm+x9X7/nMVCjZMe" +
        "Yx/HcDGBvaMDkktrug0rj+hqlDI=" +
        "-----END CERTIFICATE-----";

    public static void main(String[] args) throws Exception {
        omitParamTest(SHA224ECDSA_NULL_TAG, SHA224ECDSA_NO_NULL_TAG, "SHA224ECDSA");
        omitParamTest(SHA256ECDSA_NULL_TAG, SHA256ECDSA_NO_NULL_TAG, "SHA256ECDSA");
        omitParamTest(SHA384ECDSA_NULL_TAG, SHA384ECDSA_NO_NULL_TAG, "SHA384ECDSA");
        omitParamTest(SHA512ECDSA_NULL_TAG, SHA512ECDSA_NO_NULL_TAG, "SHA512ECDSA");
    }

    static void omitParamTest(String nullTagCert, String noNullTagCert, String Alg)
            throws Exception {
        int certLenWithNull = getCertLen(nullTagCert);
        int certLenWithoutNull = getCertLen(noNullTagCert);

        System.out.println("certLenWithNull = " + certLenWithNull + ", certLenWithoutNull = " + certLenWithoutNull);
        int deltaLen = certLenWithNull - certLenWithoutNull;

        /*
         * Each tag_Null occupies two bytes (tag: 0x05, length: 0).
         * There are two signature algorithm identifiers in the certificate, taking up 4 bytes of space
         * for the algId.parameters field - this should be omitted.
         * The case of 5 bytes is checked, since one extra byte is added for an even byte alignment.
         */
        if ((deltaLen == 4) || (deltaLen == 5))
        {
                System.out.println(Alg + " test: Passed\n");
        } else {
                System.out.println(Alg + " test: Failed\n");
        }
    }

    static int getCertLen(String cert) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate x509Cert = (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(getPemBlob(cert, "CERTIFICATE")));

        /*
         * We cannot simply compare the certificate length between the certificates
         * with and without the null tag because the signature length varies,
         * and its sigLen is included in certLen.
         */
        int certLen = x509Cert.getEncoded().length;
        int sigLen = x509Cert.getSignature().length;

        return certLen - sigLen;
    }

    static byte[] getPemBlob(String pemData, String qualifier) {
        String beginTag = "-----BEGIN " + qualifier + "-----";
        return Base64.getDecoder()
                .decode(pemData.substring(pemData.indexOf(beginTag) + beginTag.length(),
                        pemData.indexOf("-----END " + qualifier + "-----")));
    }
}
