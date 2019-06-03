/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8189131
 * @summary Interoperability tests with Comodo RSA, ECC, userTrust RSA, and
 *          userTrust ECC CAs
 * @build ValidatePathWithParams
 * @run main/othervm -Djava.security.debug=certpath ComodoCA OCSP
 * @run main/othervm -Djava.security.debug=certpath ComodoCA CRL
 */

 /*
 * Obtain TLS test artifacts for Comodo CAs from:
 *
 * Valid TLS Certificates:
 * https://comodorsacertificationauthority-ev.comodoca.com
 * https://comodoecccertificationauthority-ev.comodoca.com
 * https://usertrustrsacertificationauthority-ev.comodoca.com
 * https://usertrustecccertificationauthority-ev.comodoca.com
 *
 * Revoked TLS Certificates:
 * https://comodorsacertificationauthority-ev.comodoca.com:444
 * https://comodoecccertificationauthority-ev.comodoca.com:444
 * https://usertrustrsacertificationauthority-ev.comodoca.com:444
 * https://usertrustecccertificationauthority-ev.comodoca.com:444
 */
public class ComodoCA {

    public static void main(String[] args) throws Exception {

        ValidatePathWithParams pathValidator = new ValidatePathWithParams(null);

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            pathValidator.enableCRLCheck();
        } else {
            // OCSP check by default
            pathValidator.enableOCSPCheck();
        }

        new ComodoRSA().runTest(pathValidator);
        new ComodoECC().runTest(pathValidator);
        new ComodoUserTrustRSA().runTest(pathValidator);
        new ComodoUserTrustECC().runTest(pathValidator);
    }
}

class ComodoRSA {

    // Owner: CN=COMODO RSA Extended Validation Secure Server CA,
    // O=COMODO CA Limited, L=Salford, ST=Greater Manchester, C=GB
    // Issuer: CN=COMODO RSA Certification Authority, O=COMODO CA Limited,
    // L=Salford, ST=Greater Manchester, C=GB
    // Serial number: 6a74380d4ebfed435b5a3f7e16abdd8
    // Valid from: Sat Feb 11 16:00:00 PST 2012 until: Thu Feb 11 15:59:59 PST 2027
    private static final String INT = "-----BEGIN CERTIFICATE-----\n"
            + "MIIGDjCCA/agAwIBAgIQBqdDgNTr/tQ1taP34Wq92DANBgkqhkiG9w0BAQwFADCB\n"
            + "hTELMAkGA1UEBhMCR0IxGzAZBgNVBAgTEkdyZWF0ZXIgTWFuY2hlc3RlcjEQMA4G\n"
            + "A1UEBxMHU2FsZm9yZDEaMBgGA1UEChMRQ09NT0RPIENBIExpbWl0ZWQxKzApBgNV\n"
            + "BAMTIkNPTU9ETyBSU0EgQ2VydGlmaWNhdGlvbiBBdXRob3JpdHkwHhcNMTIwMjEy\n"
            + "MDAwMDAwWhcNMjcwMjExMjM1OTU5WjCBkjELMAkGA1UEBhMCR0IxGzAZBgNVBAgT\n"
            + "EkdyZWF0ZXIgTWFuY2hlc3RlcjEQMA4GA1UEBxMHU2FsZm9yZDEaMBgGA1UEChMR\n"
            + "Q09NT0RPIENBIExpbWl0ZWQxODA2BgNVBAMTL0NPTU9ETyBSU0EgRXh0ZW5kZWQg\n"
            + "VmFsaWRhdGlvbiBTZWN1cmUgU2VydmVyIENBMIIBIjANBgkqhkiG9w0BAQEFAAOC\n"
            + "AQ8AMIIBCgKCAQEAlVbeVLTf1QJJe9FbXKKyHo+cK2JMK40SKPMalaPGEP0p3uGf\n"
            + "CzhAk9HvbpUQ/OGQF3cs7nU+e2PsYZJuTzurgElr3wDqAwB/L3XVKC/sVmePgIOj\n"
            + "vdwDmZOLlJFWW6G4ajo/Br0OksxgnP214J9mMF/b5pTwlWqvyIqvgNnmiDkBfBzA\n"
            + "xSr3e5Wg8narbZtyOTDr0VdVAZ1YEZ18bYSPSeidCfw8/QpKdhQhXBZzQCMZdMO6\n"
            + "WAqmli7eNuWf0MLw4eDBYuPCGEUZUaoXHugjddTI0JYT/8ck0YwLJ66eetw6YWNg\n"
            + "iJctXQUL5Tvrrs46R3N2qPos3cCHF+msMJn4HwIDAQABo4IBaTCCAWUwHwYDVR0j\n"
            + "BBgwFoAUu69+Aj36pvE8hI6t7jiY7NkyMtQwHQYDVR0OBBYEFDna/8ooFIqodBMI\n"
            + "ueQOqdL6fp1pMA4GA1UdDwEB/wQEAwIBBjASBgNVHRMBAf8ECDAGAQH/AgEAMD4G\n"
            + "A1UdIAQ3MDUwMwYEVR0gADArMCkGCCsGAQUFBwIBFh1odHRwczovL3NlY3VyZS5j\n"
            + "b21vZG8uY29tL0NQUzBMBgNVHR8ERTBDMEGgP6A9hjtodHRwOi8vY3JsLmNvbW9k\n"
            + "b2NhLmNvbS9DT01PRE9SU0FDZXJ0aWZpY2F0aW9uQXV0aG9yaXR5LmNybDBxBggr\n"
            + "BgEFBQcBAQRlMGMwOwYIKwYBBQUHMAKGL2h0dHA6Ly9jcnQuY29tb2RvY2EuY29t\n"
            + "L0NPTU9ET1JTQUFkZFRydXN0Q0EuY3J0MCQGCCsGAQUFBzABhhhodHRwOi8vb2Nz\n"
            + "cC5jb21vZG9jYS5jb20wDQYJKoZIhvcNAQEMBQADggIBAERCnUFRK0iIXZebeV4R\n"
            + "AUpSGXtBLMeJPNBy3IX6WK/VJeQT+FhlZ58N/1eLqYVeyqZLsKeyLeCMIs37/3mk\n"
            + "jCuN/gI9JN6pXV/kD0fQ22YlPodHDK4ixVAihNftSlka9pOlk7DgG4HyVsTIEFPk\n"
            + "1Hax0VtpS3ey4E/EhOfUoFDuPPpE/NBXueEoU/1Tzdy5H3pAvTA/2GzS8+cHnx8i\n"
            + "teoiccsq8FZ8/qyo0QYPFBRSTP5kKwxpKrgNUG4+BAe/eiCL+O5lCeHHSQgyPQ0o\n"
            + "fkkdt0rvAucNgBfIXOBhYsvss2B5JdoaZXOcOBCgJjqwyBZ9kzEi7nQLiMBciUEA\n"
            + "KKlHMd99SUWa9eanRRrSjhMQ34Ovmw2tfn6dNVA0BM7pINae253UqNpktNEvWS5e\n"
            + "ojZh1CSggjMziqHRbO9haKPl0latxf1eYusVqHQSTC8xjOnB3xBLAer2VBvNfzu9\n"
            + "XJ/B288ByvK6YBIhMe2pZLiySVgXbVrXzYxtvp5/4gJYp9vDLVj2dAZqmvZh+fYA\n"
            + "tmnYOosxWd2R5nwnI4fdAw+PKowegwFOAWEMUnNt/AiiuSpm5HZNMaBWm9lTjaK2\n"
            + "jwLI5jqmBNFI+8NKAnb9L9K8E7bobTQk+p0pisehKxTxlgBzuRPpwLk6R1YCcYAn\n"
            + "pLwltum95OmYdBbxN4SBB7SC\n"
            + "-----END CERTIFICATE-----";

    // Owner: CN=comodorsacertificationauthority-ev.comodoca.com, OU=COMODO EV SGC SSL, O=Sectigo Limited,
    // STREET="3rd Floor, 26 Office Village", STREET=Exchange Quay, STREET=Trafford Road, L=Salford,
    // ST=Greater Manchester, OID.2.5.4.17=M5 3EQ, C=GB, OID.2.5.4.15=Private Organization,
    // OID.1.3.6.1.4.1.311.60.2.1.3=GB, SERIALNUMBER=04058690
    // Issuer: CN=COMODO RSA Extended Validation Secure Server CA, O=COMODO CA Limited, L=Salford,
    // ST=Greater Manchester, C=GB
    // Serial number: d3df2597cbed1ab6e02ee82021771614
    // Valid from: Wed Nov 28 16:00:00 PST 2018 until: Fri Feb 26 15:59:59 PST 2021
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIH7jCCBtagAwIBAgIRANPfJZfL7Rq24C7oICF3FhQwDQYJKoZIhvcNAQELBQAw\n" +
            "gZIxCzAJBgNVBAYTAkdCMRswGQYDVQQIExJHcmVhdGVyIE1hbmNoZXN0ZXIxEDAO\n" +
            "BgNVBAcTB1NhbGZvcmQxGjAYBgNVBAoTEUNPTU9ETyBDQSBMaW1pdGVkMTgwNgYD\n" +
            "VQQDEy9DT01PRE8gUlNBIEV4dGVuZGVkIFZhbGlkYXRpb24gU2VjdXJlIFNlcnZl\n" +
            "ciBDQTAeFw0xODExMjkwMDAwMDBaFw0yMTAyMjYyMzU5NTlaMIIBWzERMA8GA1UE\n" +
            "BRMIMDQwNTg2OTAxEzARBgsrBgEEAYI3PAIBAxMCR0IxHTAbBgNVBA8TFFByaXZh\n" +
            "dGUgT3JnYW5pemF0aW9uMQswCQYDVQQGEwJHQjEPMA0GA1UEERMGTTUgM0VRMRsw\n" +
            "GQYDVQQIExJHcmVhdGVyIE1hbmNoZXN0ZXIxEDAOBgNVBAcTB1NhbGZvcmQxFjAU\n" +
            "BgNVBAkTDVRyYWZmb3JkIFJvYWQxFjAUBgNVBAkTDUV4Y2hhbmdlIFF1YXkxJTAj\n" +
            "BgNVBAkTHDNyZCBGbG9vciwgMjYgT2ZmaWNlIFZpbGxhZ2UxGDAWBgNVBAoTD1Nl\n" +
            "Y3RpZ28gTGltaXRlZDEaMBgGA1UECxMRQ09NT0RPIEVWIFNHQyBTU0wxODA2BgNV\n" +
            "BAMTL2NvbW9kb3JzYWNlcnRpZmljYXRpb25hdXRob3JpdHktZXYuY29tb2RvY2Eu\n" +
            "Y29tMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA0P95lAFOOkEOy614\n" +
            "rCX7OlOK0Xy0nPAcCFxAcLYBosX8YmXWuePHg596UyEqE3U530pTqiccY53bDiYP\n" +
            "gSJgr1OlfC7BPLN+QKaeSrFmNgrcoAk3TXejgv7zLXOwZVS6Wk38Z8xrFNvhd2Z5\n" +
            "J6RM/3U+HDfF7OKMGrexr77Ws7lEFpPUgd4eEe+IL1Y2sbwIiD+PkzIL2LjctkeJ\n" +
            "FcsRHUvNP8wIhGyIbkARuJhdXkE13lKKIe0EnWrRkkf4DEvYRFpPjVUKmluhnBOG\n" +
            "YkYaiTL0VaOnrPxToSfHR8Awkhk0TNbosAkUo8TKcRTTTiMUUIS6Y9SqoILiiDG6\n" +
            "WmFjzQIDAQABo4IDcTCCA20wHwYDVR0jBBgwFoAUOdr/yigUiqh0Ewi55A6p0vp+\n" +
            "nWkwHQYDVR0OBBYEFD5LhmEivA6h4az0EFPi5erz1TH+MA4GA1UdDwEB/wQEAwIF\n" +
            "oDAMBgNVHRMBAf8EAjAAMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjBP\n" +
            "BgNVHSAESDBGMDsGDCsGAQQBsjEBAgEFATArMCkGCCsGAQUFBwIBFh1odHRwczov\n" +
            "L3NlY3VyZS5jb21vZG8uY29tL0NQUzAHBgVngQwBATBWBgNVHR8ETzBNMEugSaBH\n" +
            "hkVodHRwOi8vY3JsLmNvbW9kb2NhLmNvbS9DT01PRE9SU0FFeHRlbmRlZFZhbGlk\n" +
            "YXRpb25TZWN1cmVTZXJ2ZXJDQS5jcmwwgYcGCCsGAQUFBwEBBHsweTBRBggrBgEF\n" +
            "BQcwAoZFaHR0cDovL2NydC5jb21vZG9jYS5jb20vQ09NT0RPUlNBRXh0ZW5kZWRW\n" +
            "YWxpZGF0aW9uU2VjdXJlU2VydmVyQ0EuY3J0MCQGCCsGAQUFBzABhhhodHRwOi8v\n" +
            "b2NzcC5jb21vZG9jYS5jb20wOgYDVR0RBDMwMYIvY29tb2RvcnNhY2VydGlmaWNh\n" +
            "dGlvbmF1dGhvcml0eS1ldi5jb21vZG9jYS5jb20wggF9BgorBgEEAdZ5AgQCBIIB\n" +
            "bQSCAWkBZwB1AO5Lvbd1zmC64UJpH6vhnmajD35fsHLYgwDEe4l6qP3LAAABZ2Bx\n" +
            "+EAAAAQDAEYwRAIgXot8xi2N4oV6A8n2aXJ/TI6oI5t30ZgiyR3jF8nY8tYCIGBB\n" +
            "e7sTFniA3vzfxhMbYsyAEy50PNFCaqLjNoyOaGNqAHYAb1N2rDHwMRnYmQCkURX/\n" +
            "dxUcEdkCwQApBo2yCJo32RMAAAFnYHH4LQAABAMARzBFAiBq8utPzn0fL5zPPQNB\n" +
            "gueIXEFDXPw8s5D+pzD6+ySwegIhAJUCgsW++nO2JwYNwJTxPsHOWs7WpXqXCsVC\n" +
            "/FlJ1HHbAHYAu9nfvB+KcbWTlCOXqpJ7RzhXlQqrUugakJZkNo4e0YUAAAFnYHH4\n" +
            "OAAABAMARzBFAiA7rSUo9XVQDb3CBLo85qFMzYzsylF223s0u4WXQTUGqQIhAKJq\n" +
            "j602nEd4imaE9Wr7OWdIbbhLcNm5dhVZerk4MD6GMA0GCSqGSIb3DQEBCwUAA4IB\n" +
            "AQBPeidaCGBGyFDK60+Eh8GyKQSMowcRA74B6C+JlQYTBtl024xAV7d3fnbULtzY\n" +
            "rs5EGxlEPIR/ZLAETTdEi1mAalXAi2l1QDrmTeOGW+FZXlcXQuNeg56D9gkApftR\n" +
            "yFFRLNScchNDsMwR3UOlJnD05DJk1J+SeNvOlefwfDHIlZBiQIrSxdWS8GIIkKLp\n" +
            "4PIy+N4lgNEudi2LuRheEjmrkN9+NcKlU+v7lzlwCfWCDna2hacGRPRo5fAao5O0\n" +
            "mlUzAYm76dn5dGGBVVqA0cfWnUeVfSTrlVb/QN+uYno4vIrpR5VBYPuJYU47vgzL\n" +
            "YrTYerPngjPbZB0bfLOja0vb\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=comodorsacertificationauthority-ev.comodoca.com, OU=COMODO EV SGC SSL, O=COMODO CA Limited,
    // STREET="3rd Floor, 26 Office Village", STREET=Exchange Quay, STREET=Trafford Road, L=Salford,
    // ST=Greater Manchester, OID.2.5.4.17=M5 3EQ, C=GB, OID.2.5.4.15=Private Organization,
    // OID.1.3.6.1.4.1.311.60.2.1.3=GB, SERIALNUMBER=04058690
    // Issuer: CN=COMODO RSA Extended Validation Secure Server CA, O=COMODO CA Limited, L=Salford,
    // ST=Greater Manchester, C=GB
    // Serial number: 720aa2cfa40094521224f901a984b167
    // Valid from: Thu Jun 29 17:00:00 PDT 2017 until: Sun Sep 29 16:59:59 PDT 2019
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIH8jCCBtqgAwIBAgIQcgqiz6QAlFISJPkBqYSxZzANBgkqhkiG9w0BAQsFADCB\n" +
            "kjELMAkGA1UEBhMCR0IxGzAZBgNVBAgTEkdyZWF0ZXIgTWFuY2hlc3RlcjEQMA4G\n" +
            "A1UEBxMHU2FsZm9yZDEaMBgGA1UEChMRQ09NT0RPIENBIExpbWl0ZWQxODA2BgNV\n" +
            "BAMTL0NPTU9ETyBSU0EgRXh0ZW5kZWQgVmFsaWRhdGlvbiBTZWN1cmUgU2VydmVy\n" +
            "IENBMB4XDTE3MDYzMDAwMDAwMFoXDTE5MDkyOTIzNTk1OVowggFdMREwDwYDVQQF\n" +
            "EwgwNDA1ODY5MDETMBEGCysGAQQBgjc8AgEDEwJHQjEdMBsGA1UEDxMUUHJpdmF0\n" +
            "ZSBPcmdhbml6YXRpb24xCzAJBgNVBAYTAkdCMQ8wDQYDVQQREwZNNSAzRVExGzAZ\n" +
            "BgNVBAgTEkdyZWF0ZXIgTWFuY2hlc3RlcjEQMA4GA1UEBxMHU2FsZm9yZDEWMBQG\n" +
            "A1UECRMNVHJhZmZvcmQgUm9hZDEWMBQGA1UECRMNRXhjaGFuZ2UgUXVheTElMCMG\n" +
            "A1UECRMcM3JkIEZsb29yLCAyNiBPZmZpY2UgVmlsbGFnZTEaMBgGA1UEChMRQ09N\n" +
            "T0RPIENBIExpbWl0ZWQxGjAYBgNVBAsTEUNPTU9ETyBFViBTR0MgU1NMMTgwNgYD\n" +
            "VQQDEy9jb21vZG9yc2FjZXJ0aWZpY2F0aW9uYXV0aG9yaXR5LWV2LmNvbW9kb2Nh\n" +
            "LmNvbTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAND/eZQBTjpBDsut\n" +
            "eKwl+zpTitF8tJzwHAhcQHC2AaLF/GJl1rnjx4OfelMhKhN1Od9KU6onHGOd2w4m\n" +
            "D4EiYK9TpXwuwTyzfkCmnkqxZjYK3KAJN013o4L+8y1zsGVUulpN/GfMaxTb4Xdm\n" +
            "eSekTP91Phw3xezijBq3sa++1rO5RBaT1IHeHhHviC9WNrG8CIg/j5MyC9i43LZH\n" +
            "iRXLER1LzT/MCIRsiG5AEbiYXV5BNd5SiiHtBJ1q0ZJH+AxL2ERaT41VCppboZwT\n" +
            "hmJGGoky9FWjp6z8U6Enx0fAMJIZNEzW6LAJFKPEynEU004jFFCEumPUqqCC4ogx\n" +
            "ulphY80CAwEAAaOCA3QwggNwMB8GA1UdIwQYMBaAFDna/8ooFIqodBMIueQOqdL6\n" +
            "fp1pMB0GA1UdDgQWBBQ+S4ZhIrwOoeGs9BBT4uXq89Ux/jAOBgNVHQ8BAf8EBAMC\n" +
            "BaAwDAYDVR0TAQH/BAIwADAdBgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIw\n" +
            "TwYDVR0gBEgwRjA7BgwrBgEEAbIxAQIBBQEwKzApBggrBgEFBQcCARYdaHR0cHM6\n" +
            "Ly9zZWN1cmUuY29tb2RvLmNvbS9DUFMwBwYFZ4EMAQEwVgYDVR0fBE8wTTBLoEmg\n" +
            "R4ZFaHR0cDovL2NybC5jb21vZG9jYS5jb20vQ09NT0RPUlNBRXh0ZW5kZWRWYWxp\n" +
            "ZGF0aW9uU2VjdXJlU2VydmVyQ0EuY3JsMIGHBggrBgEFBQcBAQR7MHkwUQYIKwYB\n" +
            "BQUHMAKGRWh0dHA6Ly9jcnQuY29tb2RvY2EuY29tL0NPTU9ET1JTQUV4dGVuZGVk\n" +
            "VmFsaWRhdGlvblNlY3VyZVNlcnZlckNBLmNydDAkBggrBgEFBQcwAYYYaHR0cDov\n" +
            "L29jc3AuY29tb2RvY2EuY29tMDoGA1UdEQQzMDGCL2NvbW9kb3JzYWNlcnRpZmlj\n" +
            "YXRpb25hdXRob3JpdHktZXYuY29tb2RvY2EuY29tMIIBgAYKKwYBBAHWeQIEAgSC\n" +
            "AXAEggFsAWoAdgCkuQmQtBhYFIe7E6LMZ3AKPDWYBPkb37jjd80OyA3cEAAAAVz5\n" +
            "cV7GAAAEAwBHMEUCIQCpgc0Eqw3g4pr+oX88h5xgL1VEAiDpqAhbRtilgYwBbgIg\n" +
            "UaIm+n8AHi55nB//Sb4Nz18GYVcfELfpIzRh1vW9HbYAdwBWFAaaL9fC7NP14b1E\n" +
            "sj7HRna5vJkRXMDvlJhV1onQ3QAAAVz5cVybAAAEAwBIMEYCIQDdsgC4KZ++OP44\n" +
            "X7LbUcNaxe0kFzbctF2L3bnmhp9nXQIhAM0/g+PrZBIBpYlOtzidePi8bBHrLWn2\n" +
            "uBiP3pYIntl4AHcA7ku9t3XOYLrhQmkfq+GeZqMPfl+wctiDAMR7iXqo/csAAAFc\n" +
            "+XFeoQAABAMASDBGAiEAoySTb/QKw7JwtZtPHnECEMzgENQSFy58Kl+Mvcd3SmcC\n" +
            "IQD8cU66Ih3ejvt0OTX+lfxQPKyggQfm4Uk/lwn5LEJXbDANBgkqhkiG9w0BAQsF\n" +
            "AAOCAQEAKEaSYWn3Hi8rfJS4cMTJoMkVp2vpPH2dGXySBEy67TEGRw9+f75w3q95\n" +
            "r1m3P+xsR6dBoidTq/6wqUYI51lB4Fq9ylh1Stp5Gj54CuyT+S31l7lD7sl0KMsn\n" +
            "HDUDQHId7hKeORYpiIZOcrKOglKdi1uiGwDgoiLKh98lUrZA6durrhH+sl69wqp2\n" +
            "0XAu+3hurXzCoZFJfyngTO1kt9qcFUAxc5LofIa9QvC6VR7dI4aAh7dUpIRlnjG3\n" +
            "jJ1mUMTqWO6TFTtddb+uQjDqNgkYYYNuSax1WMEIZWbIi13EjXK1GPQUXJe6gQin\n" +
            "NUq9JH9NPK6m8A1YKT+wgzfTDeaV2Q==\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator) throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Thu Nov 29 08:41:09 PST 2018", System.out);
    }
}

class ComodoECC {

    // Owner: CN=COMODO ECC Extended Validation Secure Server CA,
    // O=COMODO CA Limited, L=Salford, ST=Greater Manchester, C=GB
    // Issuer: CN=COMODO ECC Certification Authority, O=COMODO CA Limited,
    // L=Salford, ST=Greater Manchester, C=GB
    // Serial number: 61d4643b412b5d8d715499d8553aa03
    // Valid from: Sun Apr 14 17:00:00 PDT 2013 until: Fri Apr 14 16:59:59 PDT 2028
    private static final String INT = "-----BEGIN CERTIFICATE-----\n"
            + "MIIDojCCAyigAwIBAgIQBh1GQ7QStdjXFUmdhVOqAzAKBggqhkjOPQQDAzCBhTEL\n"
            + "MAkGA1UEBhMCR0IxGzAZBgNVBAgTEkdyZWF0ZXIgTWFuY2hlc3RlcjEQMA4GA1UE\n"
            + "BxMHU2FsZm9yZDEaMBgGA1UEChMRQ09NT0RPIENBIExpbWl0ZWQxKzApBgNVBAMT\n"
            + "IkNPTU9ETyBFQ0MgQ2VydGlmaWNhdGlvbiBBdXRob3JpdHkwHhcNMTMwNDE1MDAw\n"
            + "MDAwWhcNMjgwNDE0MjM1OTU5WjCBkjELMAkGA1UEBhMCR0IxGzAZBgNVBAgTEkdy\n"
            + "ZWF0ZXIgTWFuY2hlc3RlcjEQMA4GA1UEBxMHU2FsZm9yZDEaMBgGA1UEChMRQ09N\n"
            + "T0RPIENBIExpbWl0ZWQxODA2BgNVBAMTL0NPTU9ETyBFQ0MgRXh0ZW5kZWQgVmFs\n"
            + "aWRhdGlvbiBTZWN1cmUgU2VydmVyIENBMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcD\n"
            + "QgAEV3AaPyeTQy0aWXXkBJMR42DsJ5pnbliJe7ndaHzCDslVlY8ofpxeFiqluZrK\n"
            + "KNcJeBU/Jl1YI9jLMyMZKsfSoaOCAWkwggFlMB8GA1UdIwQYMBaAFHVxpxlIGbyd\n"
            + "nepBR9+UxEh3mdN5MB0GA1UdDgQWBBTTTsMZulhZ0Rxgt2FTRzund4/4ijAOBgNV\n"
            + "HQ8BAf8EBAMCAYYwEgYDVR0TAQH/BAgwBgEB/wIBADA+BgNVHSAENzA1MDMGBFUd\n"
            + "IAAwKzApBggrBgEFBQcCARYdaHR0cHM6Ly9zZWN1cmUuY29tb2RvLmNvbS9DUFMw\n"
            + "TAYDVR0fBEUwQzBBoD+gPYY7aHR0cDovL2NybC5jb21vZG9jYS5jb20vQ09NT0RP\n"
            + "RUNDQ2VydGlmaWNhdGlvbkF1dGhvcml0eS5jcmwwcQYIKwYBBQUHAQEEZTBjMDsG\n"
            + "CCsGAQUFBzAChi9odHRwOi8vY3J0LmNvbW9kb2NhLmNvbS9DT01PRE9FQ0NBZGRU\n"
            + "cnVzdENBLmNydDAkBggrBgEFBQcwAYYYaHR0cDovL29jc3AuY29tb2RvY2EuY29t\n"
            + "MAoGCCqGSM49BAMDA2gAMGUCMQDmPWS98nREWdt4xB83r9MVvgG5INpKHi6V1dUY\n"
            + "lCqvSvXXjK0QvZSrOB7cj9RavGgCMG2xJNG+SvlTWEYpmK7eXSgmRUgoBDeQ0yDK\n"
            + "lnxmeeOBnnCaDIxAcA3aCj2Gtdt3sA==\n"
            + "-----END CERTIFICATE-----";

    // Owner: CN=comodoecccertificationauthority-ev.comodoca.com, OU=COMODO EV SSL, O=Sectigo Limited,
    // STREET="3rd Floor, 26 Office Village", STREET=Exchange Quay, STREET=Trafford Road, L=Salford,
    // ST=Greater Manchester, OID.2.5.4.17=M5 3EQ, C=GB, OID.2.5.4.15=Private Organization,
    // OID.1.3.6.1.4.1.311.60.2.1.3=GB, SERIALNUMBER=04058690
    // Issuer: CN=COMODO ECC Extended Validation Secure Server CA, O=COMODO CA Limited, L=Salford,
    // ST=Greater Manchester, C=GB
    // Serial number: 603a5c2f85b63e00ba46ce8c3f6000b0
    // Valid from: Wed Nov 28 16:00:00 PST 2018 until: Fri Feb 26 15:59:59 PST 2021
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGXzCCBgWgAwIBAgIQYDpcL4W2PgC6Rs6MP2AAsDAKBggqhkjOPQQDAjCBkjEL\n" +
            "MAkGA1UEBhMCR0IxGzAZBgNVBAgTEkdyZWF0ZXIgTWFuY2hlc3RlcjEQMA4GA1UE\n" +
            "BxMHU2FsZm9yZDEaMBgGA1UEChMRQ09NT0RPIENBIExpbWl0ZWQxODA2BgNVBAMT\n" +
            "L0NPTU9ETyBFQ0MgRXh0ZW5kZWQgVmFsaWRhdGlvbiBTZWN1cmUgU2VydmVyIENB\n" +
            "MB4XDTE4MTEyOTAwMDAwMFoXDTIxMDIyNjIzNTk1OVowggFXMREwDwYDVQQFEwgw\n" +
            "NDA1ODY5MDETMBEGCysGAQQBgjc8AgEDEwJHQjEdMBsGA1UEDxMUUHJpdmF0ZSBP\n" +
            "cmdhbml6YXRpb24xCzAJBgNVBAYTAkdCMQ8wDQYDVQQREwZNNSAzRVExGzAZBgNV\n" +
            "BAgTEkdyZWF0ZXIgTWFuY2hlc3RlcjEQMA4GA1UEBxMHU2FsZm9yZDEWMBQGA1UE\n" +
            "CRMNVHJhZmZvcmQgUm9hZDEWMBQGA1UECRMNRXhjaGFuZ2UgUXVheTElMCMGA1UE\n" +
            "CRMcM3JkIEZsb29yLCAyNiBPZmZpY2UgVmlsbGFnZTEYMBYGA1UEChMPU2VjdGln\n" +
            "byBMaW1pdGVkMRYwFAYDVQQLEw1DT01PRE8gRVYgU1NMMTgwNgYDVQQDEy9jb21v\n" +
            "ZG9lY2NjZXJ0aWZpY2F0aW9uYXV0aG9yaXR5LWV2LmNvbW9kb2NhLmNvbTBZMBMG\n" +
            "ByqGSM49AgEGCCqGSM49AwEHA0IABLduqgUu00bv8n0fkYqiwM1tgvlLKRWZTkXO\n" +
            "BIS3zWDIBZKGSoxJ8Cc7nR+mvrkT6jzoT4FHejxX0UBTnC45oYGjggNzMIIDbzAf\n" +
            "BgNVHSMEGDAWgBTTTsMZulhZ0Rxgt2FTRzund4/4ijAdBgNVHQ4EFgQU6WdLcyrH\n" +
            "BcOmd7whAxcxkeVUpFcwDgYDVR0PAQH/BAQDAgWAMAwGA1UdEwEB/wQCMAAwHQYD\n" +
            "VR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMCME8GA1UdIARIMEYwOwYMKwYBBAGy\n" +
            "MQECAQUBMCswKQYIKwYBBQUHAgEWHWh0dHBzOi8vc2VjdXJlLmNvbW9kby5jb20v\n" +
            "Q1BTMAcGBWeBDAEBMFYGA1UdHwRPME0wS6BJoEeGRWh0dHA6Ly9jcmwuY29tb2Rv\n" +
            "Y2EuY29tL0NPTU9ET0VDQ0V4dGVuZGVkVmFsaWRhdGlvblNlY3VyZVNlcnZlckNB\n" +
            "LmNybDCBhwYIKwYBBQUHAQEEezB5MFEGCCsGAQUFBzAChkVodHRwOi8vY3J0LmNv\n" +
            "bW9kb2NhLmNvbS9DT01PRE9FQ0NFeHRlbmRlZFZhbGlkYXRpb25TZWN1cmVTZXJ2\n" +
            "ZXJDQS5jcnQwJAYIKwYBBQUHMAGGGGh0dHA6Ly9vY3NwLmNvbW9kb2NhLmNvbTA6\n" +
            "BgNVHREEMzAxgi9jb21vZG9lY2NjZXJ0aWZpY2F0aW9uYXV0aG9yaXR5LWV2LmNv\n" +
            "bW9kb2NhLmNvbTCCAX8GCisGAQQB1nkCBAIEggFvBIIBawFpAHYA7ku9t3XOYLrh\n" +
            "Qmkfq+GeZqMPfl+wctiDAMR7iXqo/csAAAFnYHQLpwAABAMARzBFAiB0pm9GZG/W\n" +
            "REFW/umEd07eSzGsPZpRkOXahkiAmXiuxgIhAOiYZKB4Gr4JlAuQsajqbrS715L9\n" +
            "03E7walRViBHMA3XAHcAb1N2rDHwMRnYmQCkURX/dxUcEdkCwQApBo2yCJo32RMA\n" +
            "AAFnYHQLmwAABAMASDBGAiEAqUN6xU8mcNaYExuxUSZQR1WP5SZrgrDnAl+DN4t3\n" +
            "R1MCIQCJseJMXtpzP2jB1d1sz/WS6pDERjutjYc0ko//m+WjlAB2ALvZ37wfinG1\n" +
            "k5Qjl6qSe0c4V5UKq1LoGpCWZDaOHtGFAAABZ2B0C6cAAAQDAEcwRQIgI42QfFzd\n" +
            "f9W0a383BnL3nGgLQinrd7usIbA81LwOvlECIQD+fmSDEs6lwsS5EGtIvygWMs2F\n" +
            "7FWI1I8ucE0ikP+wCjAKBggqhkjOPQQDAgNIADBFAiB3P9kNVyLpA8tovSGeRvdx\n" +
            "VfA0Slz//EIeeHhbHz7D/AIhAN+WFuDimGKE1XcKrmucSBLSrYGWlY8XJcF6en3v\n" +
            "KOC7\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=comodoecccertificationauthority-ev.comodoca.com, OU=COMODO EV SSL, O=COMODO CA Limited,
    // STREET="3rd Floor, 26 Office Village", STREET=Exchange Quay, STREET=Trafford Road, L=Salford,
    // ST=Greater Manchester, OID.2.5.4.17=M5 3EQ, C=GB, OID.2.5.4.15=Private Organization,
    // OID.1.3.6.1.4.1.311.60.2.1.3=GB, SERIALNUMBER=04058690
    // Issuer: CN=COMODO ECC Extended Validation Secure Server CA, O=COMODO CA Limited, L=Salford,
    // ST=Greater Manchester, C=GB
    // Serial number: 414e5d66ec7d15ca504213f2811d57af
    // Valid from: Mon Jul 03 17:00:00 PDT 2017 until: Thu Oct 03 16:59:59 PDT 2019
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGYDCCBgWgAwIBAgIQQU5dZux9FcpQQhPygR1XrzAKBggqhkjOPQQDAjCBkjEL\n" +
            "MAkGA1UEBhMCR0IxGzAZBgNVBAgTEkdyZWF0ZXIgTWFuY2hlc3RlcjEQMA4GA1UE\n" +
            "BxMHU2FsZm9yZDEaMBgGA1UEChMRQ09NT0RPIENBIExpbWl0ZWQxODA2BgNVBAMT\n" +
            "L0NPTU9ETyBFQ0MgRXh0ZW5kZWQgVmFsaWRhdGlvbiBTZWN1cmUgU2VydmVyIENB\n" +
            "MB4XDTE3MDcwNDAwMDAwMFoXDTE5MTAwMzIzNTk1OVowggFZMREwDwYDVQQFEwgw\n" +
            "NDA1ODY5MDETMBEGCysGAQQBgjc8AgEDEwJHQjEdMBsGA1UEDxMUUHJpdmF0ZSBP\n" +
            "cmdhbml6YXRpb24xCzAJBgNVBAYTAkdCMQ8wDQYDVQQREwZNNSAzRVExGzAZBgNV\n" +
            "BAgTEkdyZWF0ZXIgTWFuY2hlc3RlcjEQMA4GA1UEBxMHU2FsZm9yZDEWMBQGA1UE\n" +
            "CRMNVHJhZmZvcmQgUm9hZDEWMBQGA1UECRMNRXhjaGFuZ2UgUXVheTElMCMGA1UE\n" +
            "CRMcM3JkIEZsb29yLCAyNiBPZmZpY2UgVmlsbGFnZTEaMBgGA1UEChMRQ09NT0RP\n" +
            "IENBIExpbWl0ZWQxFjAUBgNVBAsTDUNPTU9ETyBFViBTU0wxODA2BgNVBAMTL2Nv\n" +
            "bW9kb2VjY2NlcnRpZmljYXRpb25hdXRob3JpdHktZXYuY29tb2RvY2EuY29tMFkw\n" +
            "EwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEt26qBS7TRu/yfR+RiqLAzW2C+UspFZlO\n" +
            "Rc4EhLfNYMgFkoZKjEnwJzudH6a+uRPqPOhPgUd6PFfRQFOcLjmhgaOCA3EwggNt\n" +
            "MB8GA1UdIwQYMBaAFNNOwxm6WFnRHGC3YVNHO6d3j/iKMB0GA1UdDgQWBBTpZ0tz\n" +
            "KscFw6Z3vCEDFzGR5VSkVzAOBgNVHQ8BAf8EBAMCBYAwDAYDVR0TAQH/BAIwADAd\n" +
            "BgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwTwYDVR0gBEgwRjA7BgwrBgEE\n" +
            "AbIxAQIBBQEwKzApBggrBgEFBQcCARYdaHR0cHM6Ly9zZWN1cmUuY29tb2RvLmNv\n" +
            "bS9DUFMwBwYFZ4EMAQEwVgYDVR0fBE8wTTBLoEmgR4ZFaHR0cDovL2NybC5jb21v\n" +
            "ZG9jYS5jb20vQ09NT0RPRUNDRXh0ZW5kZWRWYWxpZGF0aW9uU2VjdXJlU2VydmVy\n" +
            "Q0EuY3JsMIGHBggrBgEFBQcBAQR7MHkwUQYIKwYBBQUHMAKGRWh0dHA6Ly9jcnQu\n" +
            "Y29tb2RvY2EuY29tL0NPTU9ET0VDQ0V4dGVuZGVkVmFsaWRhdGlvblNlY3VyZVNl\n" +
            "cnZlckNBLmNydDAkBggrBgEFBQcwAYYYaHR0cDovL29jc3AuY29tb2RvY2EuY29t\n" +
            "MDoGA1UdEQQzMDGCL2NvbW9kb2VjY2NlcnRpZmljYXRpb25hdXRob3JpdHktZXYu\n" +
            "Y29tb2RvY2EuY29tMIIBfQYKKwYBBAHWeQIEAgSCAW0EggFpAWcAdgCkuQmQtBhY\n" +
            "FIe7E6LMZ3AKPDWYBPkb37jjd80OyA3cEAAAAV0NLqsqAAAEAwBHMEUCIAz9Jjq3\n" +
            "qLUd/a2PYZnLGsEG/MrL7vab5rmGBg8RGAJxAiEA7JJnar07NIjCLLO77xJ3UFcu\n" +
            "UMM3M8JgGC8wbuRwxbUAdgBWFAaaL9fC7NP14b1Esj7HRna5vJkRXMDvlJhV1onQ\n" +
            "3QAAAV0NLqjmAAAEAwBHMEUCIHRvPWKr7vPMBWx1gLPkt8inPINWPNSoax178e5A\n" +
            "D0cPAiEAvRL/VP4DLiyHvcU9AOqTzQXGuWCzswWKG59hSm7gS4kAdQDuS723dc5g\n" +
            "uuFCaR+r4Z5mow9+X7By2IMAxHuJeqj9ywAAAV0NLqsDAAAEAwBGMEQCIFALT043\n" +
            "X5IffLsxIAGXTrWgkZHf12QKgrYKXVB629eOAiAIeci2xi3fUW6mU8tT4LwyjowV\n" +
            "DkrSCw1ZMo0JApsfzTAKBggqhkjOPQQDAgNJADBGAiEA7HUxjwx0MBC+4PuPx4Z1\n" +
            "WpKz7jdHOMTh1sdaoVV5hNoCIQDrnjBFUopXHTvm/rj+aMFIeYejggPqv14KJOqT\n" +
            "gym+uA==\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator) throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Thu Nov 29 08:12:02 PST 2018", System.out);
    }
}

class ComodoUserTrustRSA {

    // Owner: CN=USERTrust RSA Extended Validation Secure Server CA,
    // O=The USERTRUST Network, L=Jersey City, ST=New Jersey, C=US
    // Issuer: CN=USERTrust RSA Certification Authority, O=The USERTRUST Network,
    // L=Jersey City, ST=New Jersey, C=US
    // Serial number: f6bb751efa7d2e8368e606407334f83
    // Valid from: Sat Feb 11 16:00:00 PST 2012 until: Thu Feb 11 15:59:59 PST 2027
    private static final String INT = "-----BEGIN CERTIFICATE-----\n"
            + "MIIGGTCCBAGgAwIBAgIQD2u3Ue+n0ug2jmBkBzNPgzANBgkqhkiG9w0BAQwFADCB\n"
            + "iDELMAkGA1UEBhMCVVMxEzARBgNVBAgTCk5ldyBKZXJzZXkxFDASBgNVBAcTC0pl\n"
            + "cnNleSBDaXR5MR4wHAYDVQQKExVUaGUgVVNFUlRSVVNUIE5ldHdvcmsxLjAsBgNV\n"
            + "BAMTJVVTRVJUcnVzdCBSU0EgQ2VydGlmaWNhdGlvbiBBdXRob3JpdHkwHhcNMTIw\n"
            + "MjEyMDAwMDAwWhcNMjcwMjExMjM1OTU5WjCBlTELMAkGA1UEBhMCVVMxEzARBgNV\n"
            + "BAgTCk5ldyBKZXJzZXkxFDASBgNVBAcTC0plcnNleSBDaXR5MR4wHAYDVQQKExVU\n"
            + "aGUgVVNFUlRSVVNUIE5ldHdvcmsxOzA5BgNVBAMTMlVTRVJUcnVzdCBSU0EgRXh0\n"
            + "ZW5kZWQgVmFsaWRhdGlvbiBTZWN1cmUgU2VydmVyIENBMIIBIjANBgkqhkiG9w0B\n"
            + "AQEFAAOCAQ8AMIIBCgKCAQEAlJwjjGNzAgMFwLu05RnhYFJS1PpbcyPH6VZOij+z\n"
            + "PyvCILGvwXC8A+EgBthY080+kIlSxrNyOdnrUfNj8IsBtBlmtOF9nMWgD0Cb4HB1\n"
            + "Y/tCNas8IHMtKr6eI4nJa4NjPhTcST+GtC8r+bVGHk0QpX4LbT+Z8WeE7pXIOUGs\n"
            + "9j66/hsMwgnBxkQ9xXN0jhTFITUZfnCuM0vOo5hRYlCNtwD8iaHJPaKxYe6qHSKH\n"
            + "WCBK7GUQiQRngry+YKLx3YtC3k/NQIyhaTLY/gUFi57kPcpZoa0h3RGfS9MpPFoe\n"
            + "mk3rGH3jwjVFxR1ep1FtP/kprzLaR1UL81gxENhWvZEWXQIDAQABo4IBbjCCAWow\n"
            + "HwYDVR0jBBgwFoAUU3m/WqorSs9UgOHYm8Cd8rIDZsswHQYDVR0OBBYEFC+BT+Jm\n"
            + "+rxov5lDhFKJIDqC86SlMA4GA1UdDwEB/wQEAwIBBjASBgNVHRMBAf8ECDAGAQH/\n"
            + "AgEAMDoGA1UdIAQzMDEwLwYEVR0gADAnMCUGCCsGAQUFBwIBFhlodHRwczovL2Nw\n"
            + "cy51c2VydHJ1c3QuY29tMFAGA1UdHwRJMEcwRaBDoEGGP2h0dHA6Ly9jcmwudXNl\n"
            + "cnRydXN0LmNvbS9VU0VSVHJ1c3RSU0FDZXJ0aWZpY2F0aW9uQXV0aG9yaXR5LmNy\n"
            + "bDB2BggrBgEFBQcBAQRqMGgwPwYIKwYBBQUHMAKGM2h0dHA6Ly9jcnQudXNlcnRy\n"
            + "dXN0LmNvbS9VU0VSVHJ1c3RSU0FBZGRUcnVzdENBLmNydDAlBggrBgEFBQcwAYYZ\n"
            + "aHR0cDovL29jc3AudXNlcnRydXN0LmNvbTANBgkqhkiG9w0BAQwFAAOCAgEAa2bX\n"
            + "Xf22zjY/QLzzdZwJ9JO86qH/czwCFPK4o9Cb7rixQL9S7zHw1dm3n/+Lx5kT9lqx\n"
            + "wB0dqoZ8o0XwFgVcksGz7QRhEBjrB0nSUNYG8kuFaMxRWa9ze6Ovov44WDrq1uyF\n"
            + "npi3eeQiwMr3xHmY76b1NX0WqvlTTFw4L5DrcIohBz1zKVkRp7LH/s5vxjDECM+/\n"
            + "erdy1WTILNFv09gwz4iFyfu/WmYYNUKlQJaSoUqja/KHcqY8zYKKjq5o982Ji3Ti\n"
            + "/Odkx1NJA1Yf5ivDxxRFQmij6knL1pi1wgQxGjd67V3/+HfHF7MCRWk8mXnT32B9\n"
            + "1Hk3jm10GL0R6y/XFsLhv0mGkmKD1vTP7vz1hdMLlVgxEs1k5dLMybtjUJ3LuENz\n"
            + "avmZ/G/vOi284ZRo/gA/YjT5CeeWgI11IHbpRDAqKy4BWhmtIi11u12i9ftPxxrD\n"
            + "/VwHtC0hTTOBnYgbJAK9ZLvaJUBU22EimU4Jv3ELkeV7SWedbAdfjXolI1mCcAbq\n"
            + "RgzRC+RaTloSmO2dWicDBW7KlRHmKZXrkDUAExSBY/1j9HmNcYzWv4NCTtK7t0en\n"
            + "gsE/OP2b7zHrHWtC/F1JwOCrH1JkbPA7c/6nNJVY2AscGM16pIU89OL0Ez1PyZYG\n"
            + "4fokbdNREXoShKClNIPbB5iY+WdSzb9CKLyb96g=\n"
            + "-----END CERTIFICATE-----";

    // Owner: CN=usertrustrsacertificationauthority-ev.comodoca.com, OU=COMODO EV SGC SSL,
    // O=Sectigo Limited, STREET="3rd Floor, 26 Office Village", STREET=Exchange Quay, STREET=Trafford Road,
    // L=Salford, ST=Greater Manchester, OID.2.5.4.17=M5 3EQ, C=GB, OID.2.5.4.15=Private Organization,
    // OID.1.3.6.1.4.1.311.60.2.1.3=GB, SERIALNUMBER=04058690
    // Issuer: CN=USERTrust RSA Extended Validation Secure Server CA, O=The USERTRUST Network, L=Jersey City,
    // ST=New Jersey, C=US
    // Serial number: d3c204e8df6a1539568cf15e97e57b1d
    // Valid from: Wed Nov 28 16:00:00 PST 2018 until: Fri Feb 26 15:59:59 PST 2021
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIIADCCBuigAwIBAgIRANPCBOjfahU5VozxXpflex0wDQYJKoZIhvcNAQELBQAw\n" +
            "gZUxCzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpOZXcgSmVyc2V5MRQwEgYDVQQHEwtK\n" +
            "ZXJzZXkgQ2l0eTEeMBwGA1UEChMVVGhlIFVTRVJUUlVTVCBOZXR3b3JrMTswOQYD\n" +
            "VQQDEzJVU0VSVHJ1c3QgUlNBIEV4dGVuZGVkIFZhbGlkYXRpb24gU2VjdXJlIFNl\n" +
            "cnZlciBDQTAeFw0xODExMjkwMDAwMDBaFw0yMTAyMjYyMzU5NTlaMIIBXjERMA8G\n" +
            "A1UEBRMIMDQwNTg2OTAxEzARBgsrBgEEAYI3PAIBAxMCR0IxHTAbBgNVBA8TFFBy\n" +
            "aXZhdGUgT3JnYW5pemF0aW9uMQswCQYDVQQGEwJHQjEPMA0GA1UEERMGTTUgM0VR\n" +
            "MRswGQYDVQQIExJHcmVhdGVyIE1hbmNoZXN0ZXIxEDAOBgNVBAcTB1NhbGZvcmQx\n" +
            "FjAUBgNVBAkTDVRyYWZmb3JkIFJvYWQxFjAUBgNVBAkTDUV4Y2hhbmdlIFF1YXkx\n" +
            "JTAjBgNVBAkTHDNyZCBGbG9vciwgMjYgT2ZmaWNlIFZpbGxhZ2UxGDAWBgNVBAoT\n" +
            "D1NlY3RpZ28gTGltaXRlZDEaMBgGA1UECxMRQ09NT0RPIEVWIFNHQyBTU0wxOzA5\n" +
            "BgNVBAMTMnVzZXJ0cnVzdHJzYWNlcnRpZmljYXRpb25hdXRob3JpdHktZXYuY29t\n" +
            "b2RvY2EuY29tMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnh/rxeiY\n" +
            "wpLa651eLvGnR+RErhDWkTZtqZcHw9Oy7JL2uELyEPbM+v0az40cBHS0bQZJZbWm\n" +
            "XNukMUMSwIb4z7t8OXlxz9uvxEufvlqBl4qeC/z3LpFBRRHEero3yGKVwkoe1aP2\n" +
            "Pq7Udi+7i7eVZZdA1ticxZWo/UBU9mwbIOYqf/4xzZ6G891hKb+NAuuEfxG52vXZ\n" +
            "l8odMThfHuDlkfS7nZMQBaO40KJeSEBhr+5TIS7d7tWWye/F6oEQ0+dHBiF9PyZ1\n" +
            "dXoO8aue/80mP+0FMYTmRFsKHge6ZjojfH9cLlR5kTqtP5Tqh5GBQ4zp3uyIBBU6\n" +
            "ylKp9PNHkewGUQIDAQABo4IDfTCCA3kwHwYDVR0jBBgwFoAUL4FP4mb6vGi/mUOE\n" +
            "UokgOoLzpKUwHQYDVR0OBBYEFHz7cvDn1LYe2M+z4plwQn7rt938MA4GA1UdDwEB\n" +
            "/wQEAwIFoDAMBgNVHRMBAf8EAjAAMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEF\n" +
            "BQcDAjBLBgNVHSAERDBCMDcGDCsGAQQBsjEBAgEFATAnMCUGCCsGAQUFBwIBFhlo\n" +
            "dHRwczovL2Nwcy51c2VydHJ1c3QuY29tMAcGBWeBDAEBMFoGA1UdHwRTMFEwT6BN\n" +
            "oEuGSWh0dHA6Ly9jcmwudXNlcnRydXN0LmNvbS9VU0VSVHJ1c3RSU0FFeHRlbmRl\n" +
            "ZFZhbGlkYXRpb25TZWN1cmVTZXJ2ZXJDQS5jcmwwgY0GCCsGAQUFBwEBBIGAMH4w\n" +
            "VQYIKwYBBQUHMAKGSWh0dHA6Ly9jcnQudXNlcnRydXN0LmNvbS9VU0VSVHJ1c3RS\n" +
            "U0FFeHRlbmRlZFZhbGlkYXRpb25TZWN1cmVTZXJ2ZXJDQS5jcnQwJQYIKwYBBQUH\n" +
            "MAGGGWh0dHA6Ly9vY3NwLnVzZXJ0cnVzdC5jb20wPQYDVR0RBDYwNIIydXNlcnRy\n" +
            "dXN0cnNhY2VydGlmaWNhdGlvbmF1dGhvcml0eS1ldi5jb21vZG9jYS5jb20wggGA\n" +
            "BgorBgEEAdZ5AgQCBIIBcASCAWwBagB3AO5Lvbd1zmC64UJpH6vhnmajD35fsHLY\n" +
            "gwDEe4l6qP3LAAABZ2ElLBIAAAQDAEgwRgIhAM33ah4mcEfxzv4a8+BLysZKJygV\n" +
            "jeARvesL/mH6y51bAiEA4hzW9aDRmEnuCcagONmIexMl0s3gMWfaU8/0s4ESJecA\n" +
            "dgBvU3asMfAxGdiZAKRRFf93FRwR2QLBACkGjbIImjfZEwAAAWdhJSwQAAAEAwBH\n" +
            "MEUCIQDCZ7GZayfWD3blSOWgk8PrAs/GVVpc5gJG3V+wq8LHMwIgH6n8DkoA46v1\n" +
            "0UrmkVo7ND18ykuWyNH83QafsolTYSIAdwC72d+8H4pxtZOUI5eqkntHOFeVCqtS\n" +
            "6BqQlmQ2jh7RhQAAAWdhJSwSAAAEAwBIMEYCIQC/XcbvfZzEqDpUthm3gYzndaiB\n" +
            "8djqlQ89Mo29WDMS8AIhAKcWwmE5F2SuoTojiO0pPC5w5T38uW0vTXzb+fkj+6d5\n" +
            "MA0GCSqGSIb3DQEBCwUAA4IBAQAmq2kRR74802+YMB2AaqklGcFRCuBVxU/ExbvE\n" +
            "loNpNk64KH2eHwMK13ad0Lbc8/LaVgIJPtPz87LNqu00kQh0DL/kCnoO94+Z+usU\n" +
            "ulQNYr8y3sg8+NZd9ui/VQTOcXrlovL+mnJnTgsCUzNYcoBIPq7lHxtZv5MG99AR\n" +
            "6NyGTG28Aw8ZByCzhaasDOyT4YwlfveVUtsx1jKUC+0e6IhsGhxADnYOnrDD0cNd\n" +
            "4bgX6SkpM0MCg6Nc3X/C4fo3WaSRM6FO4S98NY4g2DhNskT/7TmX2DsPwS005t0r\n" +
            "3Ld31zbQaywKdpCsT74/hEBMfcDiP02mmtyrlqHD4R3tdYne\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=usertrustrsacertificationauthority-ev.comodoca.com, OU=COMODO EV SGC SSL, O=COMODO CA Limited,
    // STREET="3rd Floor, 26 Office Village", STREET=Exchange Quay, STREET=Trafford Road, L=Salford,
    // ST=Greater Manchester, OID.2.5.4.17=M5 3EQ, C=GB, OID.2.5.4.15=Private Organization,
    // OID.1.3.6.1.4.1.311.60.2.1.3=GB, SERIALNUMBER=04058690
    // Issuer: CN=USERTrust RSA Extended Validation Secure Server CA, O=The USERTRUST Network, L=Jersey City,
    // ST=New Jersey, C=US
    // Serial number: ffcada019c9fb1155a32300083cb99c9
    // Valid from: Mon Jul 03 17:00:00 PDT 2017 until: Thu Oct 03 16:59:59 PDT 2019
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIIATCCBumgAwIBAgIRAP/K2gGcn7EVWjIwAIPLmckwDQYJKoZIhvcNAQELBQAw\n" +
            "gZUxCzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpOZXcgSmVyc2V5MRQwEgYDVQQHEwtK\n" +
            "ZXJzZXkgQ2l0eTEeMBwGA1UEChMVVGhlIFVTRVJUUlVTVCBOZXR3b3JrMTswOQYD\n" +
            "VQQDEzJVU0VSVHJ1c3QgUlNBIEV4dGVuZGVkIFZhbGlkYXRpb24gU2VjdXJlIFNl\n" +
            "cnZlciBDQTAeFw0xNzA3MDQwMDAwMDBaFw0xOTEwMDMyMzU5NTlaMIIBYDERMA8G\n" +
            "A1UEBRMIMDQwNTg2OTAxEzARBgsrBgEEAYI3PAIBAxMCR0IxHTAbBgNVBA8TFFBy\n" +
            "aXZhdGUgT3JnYW5pemF0aW9uMQswCQYDVQQGEwJHQjEPMA0GA1UEERMGTTUgM0VR\n" +
            "MRswGQYDVQQIExJHcmVhdGVyIE1hbmNoZXN0ZXIxEDAOBgNVBAcTB1NhbGZvcmQx\n" +
            "FjAUBgNVBAkTDVRyYWZmb3JkIFJvYWQxFjAUBgNVBAkTDUV4Y2hhbmdlIFF1YXkx\n" +
            "JTAjBgNVBAkTHDNyZCBGbG9vciwgMjYgT2ZmaWNlIFZpbGxhZ2UxGjAYBgNVBAoT\n" +
            "EUNPTU9ETyBDQSBMaW1pdGVkMRowGAYDVQQLExFDT01PRE8gRVYgU0dDIFNTTDE7\n" +
            "MDkGA1UEAxMydXNlcnRydXN0cnNhY2VydGlmaWNhdGlvbmF1dGhvcml0eS1ldi5j\n" +
            "b21vZG9jYS5jb20wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCeH+vF\n" +
            "6JjCktrrnV4u8adH5ESuENaRNm2plwfD07Lskva4QvIQ9sz6/RrPjRwEdLRtBkll\n" +
            "taZc26QxQxLAhvjPu3w5eXHP26/ES5++WoGXip4L/PcukUFFEcR6ujfIYpXCSh7V\n" +
            "o/Y+rtR2L7uLt5Vll0DW2JzFlaj9QFT2bBsg5ip//jHNnobz3WEpv40C64R/Ebna\n" +
            "9dmXyh0xOF8e4OWR9LudkxAFo7jQol5IQGGv7lMhLt3u1ZbJ78XqgRDT50cGIX0/\n" +
            "JnV1eg7xq57/zSY/7QUxhOZEWwoeB7pmOiN8f1wuVHmROq0/lOqHkYFDjOne7IgE\n" +
            "FTrKUqn080eR7AZRAgMBAAGjggN8MIIDeDAfBgNVHSMEGDAWgBQvgU/iZvq8aL+Z\n" +
            "Q4RSiSA6gvOkpTAdBgNVHQ4EFgQUfPty8OfUth7Yz7PimXBCfuu33fwwDgYDVR0P\n" +
            "AQH/BAQDAgWgMAwGA1UdEwEB/wQCMAAwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsG\n" +
            "AQUFBwMCMEsGA1UdIAREMEIwNwYMKwYBBAGyMQECAQUBMCcwJQYIKwYBBQUHAgEW\n" +
            "GWh0dHBzOi8vY3BzLnVzZXJ0cnVzdC5jb20wBwYFZ4EMAQEwWgYDVR0fBFMwUTBP\n" +
            "oE2gS4ZJaHR0cDovL2NybC51c2VydHJ1c3QuY29tL1VTRVJUcnVzdFJTQUV4dGVu\n" +
            "ZGVkVmFsaWRhdGlvblNlY3VyZVNlcnZlckNBLmNybDCBjQYIKwYBBQUHAQEEgYAw\n" +
            "fjBVBggrBgEFBQcwAoZJaHR0cDovL2NydC51c2VydHJ1c3QuY29tL1VTRVJUcnVz\n" +
            "dFJTQUV4dGVuZGVkVmFsaWRhdGlvblNlY3VyZVNlcnZlckNBLmNydDAlBggrBgEF\n" +
            "BQcwAYYZaHR0cDovL29jc3AudXNlcnRydXN0LmNvbTA9BgNVHREENjA0gjJ1c2Vy\n" +
            "dHJ1c3Ryc2FjZXJ0aWZpY2F0aW9uYXV0aG9yaXR5LWV2LmNvbW9kb2NhLmNvbTCC\n" +
            "AX8GCisGAQQB1nkCBAIEggFvBIIBawFpAHYApLkJkLQYWBSHuxOizGdwCjw1mAT5\n" +
            "G9+443fNDsgN3BAAAAFdDU2iYQAABAMARzBFAiB0o4GnVHD8MeVQ32D0XYu+EQQW\n" +
            "jvN78rmCfk0OEBxyFAIhAKgyctIn0IaDJiZzsrtAiqEnkcMtuh8o+R0Rqw1ygAjk\n" +
            "AHcAVhQGmi/XwuzT9eG9RLI+x0Z2ubyZEVzA75SYVdaJ0N0AAAFdDU2gFgAABAMA\n" +
            "SDBGAiEA7mcmZ8H5uHuNCdI0CVxsqDZQcZX/gVk94KckePkzQoACIQCHwm5hcvNC\n" +
            "M8vNmFkboQN79DglRctHrlh143A6mUTk8QB2AO5Lvbd1zmC64UJpH6vhnmajD35f\n" +
            "sHLYgwDEe4l6qP3LAAABXQ1NojoAAAQDAEcwRQIhAPqwijgE0Fr6uJ+yF+TvyXco\n" +
            "Hduv9h7R5WWwJfghXiMyAiBB4+fJm4rIcOnJBZmOqFnRpIjPN0jwDqJT0nDHxaXA\n" +
            "nDANBgkqhkiG9w0BAQsFAAOCAQEACXitF1bTEvV1HX11WrT/XuoMhsoPK4TS16rs\n" +
            "FqztV4iXKlA1/h5qbsjYY1gVrM+/6kQkmEs5qrxsek2WNxY80NO3WAzroRJ3H9Sd\n" +
            "mPn0No2P8LZ5Fs5hvaD/PfWO5xxey80c3kGyvWOej90P3IrL/1RiULyh95TrXBjI\n" +
            "ddCBsZ28904wsQUrPBPMpiu0DKl1HR/em9WkcipMi+onJxxFWjucssz5PW/BzGYF\n" +
            "jfWLDEI0tN5L4CWV3iVXFXOURY1Mwhtsey9jvlEyxSsys55QdKF40yGgtV9VC+os\n" +
            "7hJP33+qA0cvCTaRytiPP6z/l2G/KSIXTyv6SxzGhsTFfzLAOg==\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator) throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Thu Nov 29 10:58:13 PST 2018", System.out);
    }
}

class ComodoUserTrustECC {

    // Owner: CN=USERTrust ECC Extended Validation Secure Server CA, O=The USERTRUST Network,
    // L=Jersey City, ST=New Jersey, C=US
    // Issuer: CN=USERTrust ECC Certification Authority, O=The USERTRUST Network,
    // L=Jersey City, ST=New Jersey, C=US
    // Serial number: 3d09b24f5c08a7ce8eb85a51d3c1aa52
    // Valid from: Sun Apr 14 17:00:00 PDT 2013 until: Fri Apr 14 16:59:59 PDT 2028
    private static final String INT = "-----BEGIN CERTIFICATE-----\n"
            + "MIIDwTCCA0igAwIBAgIQPQmyT1wIp86OuFpR08GqUjAKBggqhkjOPQQDAzCBiDEL\n"
            + "MAkGA1UEBhMCVVMxEzARBgNVBAgTCk5ldyBKZXJzZXkxFDASBgNVBAcTC0plcnNl\n"
            + "eSBDaXR5MR4wHAYDVQQKExVUaGUgVVNFUlRSVVNUIE5ldHdvcmsxLjAsBgNVBAMT\n"
            + "JVVTRVJUcnVzdCBFQ0MgQ2VydGlmaWNhdGlvbiBBdXRob3JpdHkwHhcNMTMwNDE1\n"
            + "MDAwMDAwWhcNMjgwNDE0MjM1OTU5WjCBlTELMAkGA1UEBhMCVVMxEzARBgNVBAgT\n"
            + "Ck5ldyBKZXJzZXkxFDASBgNVBAcTC0plcnNleSBDaXR5MR4wHAYDVQQKExVUaGUg\n"
            + "VVNFUlRSVVNUIE5ldHdvcmsxOzA5BgNVBAMTMlVTRVJUcnVzdCBFQ0MgRXh0ZW5k\n"
            + "ZWQgVmFsaWRhdGlvbiBTZWN1cmUgU2VydmVyIENBMFkwEwYHKoZIzj0CAQYIKoZI\n"
            + "zj0DAQcDQgAEkSRGk0F0N82ZCZ+kVZ/StqVUiWRirw1ebViS06+j+HgS9xZKRGh7\n"
            + "bqSas/gNMyg1LZusGu5IvEmXmNC5hzOT06OCAYMwggF/MB8GA1UdIwQYMBaAFDrh\n"
            + "CYbUzxnClnZ0SXbc4DXGY2OaMB0GA1UdDgQWBBQqnFr5TqEw2kBLK+lL8fWc3AL5\n"
            + "LjAOBgNVHQ8BAf8EBAMCAYYwEgYDVR0TAQH/BAgwBgEB/wIBADA/BgNVHSAEODA2\n"
            + "MDQGBFUdIAAwLDAqBggrBgEFBQcCARYeaHR0cHM6Ly9jcHMudHJ1c3QtcHJvdmlk\n"
            + "ZXIuY29tMFUGA1UdHwROMEwwSqBIoEaGRGh0dHA6Ly9jcmwudHJ1c3QtcHJvdmlk\n"
            + "ZXIuY29tL1VTRVJUcnVzdEVDQ0NlcnRpZmljYXRpb25BdXRob3JpdHkuY3JsMIGA\n"
            + "BggrBgEFBQcBAQR0MHIwRAYIKwYBBQUHMAKGOGh0dHA6Ly9jcnQudHJ1c3QtcHJv\n"
            + "dmlkZXIuY29tL1VTRVJUcnVzdEVDQ0FkZFRydXN0Q0EuY3J0MCoGCCsGAQUFBzAB\n"
            + "hh5odHRwOi8vb2NzcC50cnVzdC1wcm92aWRlci5jb20wCgYIKoZIzj0EAwMDZwAw\n"
            + "ZAIwSzIqrW8TN9/aCfkhUtz0t8IIK+Z46z3wm+crwjThpQ/VoPgTNbvP/lGTi1xR\n"
            + "qJvLAjBFa27l4uqeAQZHNJnIx1Mu9OXzoJelx1cYP7ToQUms/g+PK77yImJcXUU3\n"
            + "s1rWGRU=\n"
            + "-----END CERTIFICATE-----";

    // Owner: CN=usertrustecccertificationauthority-ev.comodoca.com, OU=COMODO EV SGC SSL, O=Sectigo Limited,
    // STREET="3rd Floor, 26 Office Village", STREET=Exchange Quay, STREET=Trafford Road, L=Salford,
    // ST=Greater Manchester, OID.2.5.4.17=M5 3EQ, C=GB, OID.2.5.4.15=Private Organization,
    // OID.1.3.6.1.4.1.311.60.2.1.3=GB, SERIALNUMBER=04058690
    // Issuer: CN=USERTrust ECC Extended Validation Secure Server CA, O=The USERTRUST Network, L=Jersey City,
    // ST=New Jersey, C=US
    // Serial number: ab1455f9833ae7783f95de8744181f6a
    // Valid from: Wed Nov 28 16:00:00 PST 2018 until: Fri Feb 26 15:59:59 PST 2021
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGhjCCBiygAwIBAgIRAKsUVfmDOud4P5Xeh0QYH2owCgYIKoZIzj0EAwIwgZUx\n" +
            "CzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpOZXcgSmVyc2V5MRQwEgYDVQQHEwtKZXJz\n" +
            "ZXkgQ2l0eTEeMBwGA1UEChMVVGhlIFVTRVJUUlVTVCBOZXR3b3JrMTswOQYDVQQD\n" +
            "EzJVU0VSVHJ1c3QgRUNDIEV4dGVuZGVkIFZhbGlkYXRpb24gU2VjdXJlIFNlcnZl\n" +
            "ciBDQTAeFw0xODExMjkwMDAwMDBaFw0yMTAyMjYyMzU5NTlaMIIBXjERMA8GA1UE\n" +
            "BRMIMDQwNTg2OTAxEzARBgsrBgEEAYI3PAIBAxMCR0IxHTAbBgNVBA8TFFByaXZh\n" +
            "dGUgT3JnYW5pemF0aW9uMQswCQYDVQQGEwJHQjEPMA0GA1UEERMGTTUgM0VRMRsw\n" +
            "GQYDVQQIExJHcmVhdGVyIE1hbmNoZXN0ZXIxEDAOBgNVBAcTB1NhbGZvcmQxFjAU\n" +
            "BgNVBAkTDVRyYWZmb3JkIFJvYWQxFjAUBgNVBAkTDUV4Y2hhbmdlIFF1YXkxJTAj\n" +
            "BgNVBAkTHDNyZCBGbG9vciwgMjYgT2ZmaWNlIFZpbGxhZ2UxGDAWBgNVBAoTD1Nl\n" +
            "Y3RpZ28gTGltaXRlZDEaMBgGA1UECxMRQ09NT0RPIEVWIFNHQyBTU0wxOzA5BgNV\n" +
            "BAMTMnVzZXJ0cnVzdGVjY2NlcnRpZmljYXRpb25hdXRob3JpdHktZXYuY29tb2Rv\n" +
            "Y2EuY29tMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAELTJfEd92Wlg+h/AVtPsM\n" +
            "mwX9Puvi+WGCv3sgFRpur8Iy2kGVpXHRQTCn2j9aky4tFQGm7OG2klJA/MEeevKV\n" +
            "aaOCA48wggOLMB8GA1UdIwQYMBaAFCqcWvlOoTDaQEsr6Uvx9ZzcAvkuMB0GA1Ud\n" +
            "DgQWBBSzrWHzmiHwx2Rrm7SjRC0UegNrKzAOBgNVHQ8BAf8EBAMCBYAwDAYDVR0T\n" +
            "AQH/BAIwADAdBgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwUAYDVR0gBEkw\n" +
            "RzA8BgwrBgEEAbIxAQIBBQEwLDAqBggrBgEFBQcCARYeaHR0cHM6Ly9jcHMudHJ1\n" +
            "c3QtcHJvdmlkZXIuY29tMAcGBWeBDAEBMF8GA1UdHwRYMFYwVKBSoFCGTmh0dHA6\n" +
            "Ly9jcmwudHJ1c3QtcHJvdmlkZXIuY29tL1VTRVJUcnVzdEVDQ0V4dGVuZGVkVmFs\n" +
            "aWRhdGlvblNlY3VyZVNlcnZlckNBLmNybDCBmAYIKwYBBQUHAQEEgYswgYgwWgYI\n" +
            "KwYBBQUHMAKGTmh0dHA6Ly9jcnQudHJ1c3QtcHJvdmlkZXIuY29tL1VTRVJUcnVz\n" +
            "dEVDQ0V4dGVuZGVkVmFsaWRhdGlvblNlY3VyZVNlcnZlckNBLmNydDAqBggrBgEF\n" +
            "BQcwAYYeaHR0cDovL29jc3AudHJ1c3QtcHJvdmlkZXIuY29tMD0GA1UdEQQ2MDSC\n" +
            "MnVzZXJ0cnVzdGVjY2NlcnRpZmljYXRpb25hdXRob3JpdHktZXYuY29tb2RvY2Eu\n" +
            "Y29tMIIBfQYKKwYBBAHWeQIEAgSCAW0EggFpAWcAdgDuS723dc5guuFCaR+r4Z5m\n" +
            "ow9+X7By2IMAxHuJeqj9ywAAAWdhJc3KAAAEAwBHMEUCIQCPBqO6bw8GSm3HxUf5\n" +
            "vRcv74BOEAwZxEr0+PyszJHDbQIgCDXHPzlznU2jbaPdbZ65OQ78mRL5aDDMt1vf\n" +
            "/foQr/wAdQBvU3asMfAxGdiZAKRRFf93FRwR2QLBACkGjbIImjfZEwAAAWdhJc3B\n" +
            "AAAEAwBGMEQCICIvBLk08NTaEWtKdH8xl2VjjYUo+yQmtdqwMiVMjEB8AiAc50at\n" +
            "gCogvZhdlIlGAqg5oND0K74+iglilBVvuTcIoQB2ALvZ37wfinG1k5Qjl6qSe0c4\n" +
            "V5UKq1LoGpCWZDaOHtGFAAABZ2ElzcsAAAQDAEcwRQIhALDnAxdhUQvt2HkslYbG\n" +
            "J8mYzWbDSSoidZzF4EF4bredAiB3xrSKOKFdTF3KUFHJANqT0c6Xxmo5dVUtovwM\n" +
            "QMb1vTAKBggqhkjOPQQDAgNIADBFAiEA2ZwNj/BD8n2yR5BMBQvw7utv9XWrJvKQ\n" +
            "11EPtBSCEhUCIBcyI0yl5dRff6+4x8IeCrLiAOYsfzM7Y/a5uRKFnbYz\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=usertrustecccertificationauthority-ev.comodoca.com, OU=COMODO EV SGC SSL, O=COMODO CA Limited,
    // STREET="3rd Floor, 26 Office Village", STREET=Exchange Quay, STREET=Trafford Road, L=Salford,
    // ST=Greater Manchester, OID.2.5.4.17=M5 3EQ, C=GB, OID.2.5.4.15=Private Organization,
    // OID.1.3.6.1.4.1.311.60.2.1.3=GB, SERIALNUMBER=04058690
    // Issuer: CN=USERTrust ECC Extended Validation Secure Server CA, O=The USERTRUST Network, L=Jersey City,
    // ST=New Jersey, C=US
    // Serial number: 9bd0c93cac9ca2edc1a7dd923316b3c6
    // Valid from: Mon Jul 03 17:00:00 PDT 2017 until: Thu Oct 03 16:59:59 PDT 2019
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGhzCCBi2gAwIBAgIRAJvQyTysnKLtwafdkjMWs8YwCgYIKoZIzj0EAwIwgZUx\n" +
            "CzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpOZXcgSmVyc2V5MRQwEgYDVQQHEwtKZXJz\n" +
            "ZXkgQ2l0eTEeMBwGA1UEChMVVGhlIFVTRVJUUlVTVCBOZXR3b3JrMTswOQYDVQQD\n" +
            "EzJVU0VSVHJ1c3QgRUNDIEV4dGVuZGVkIFZhbGlkYXRpb24gU2VjdXJlIFNlcnZl\n" +
            "ciBDQTAeFw0xNzA3MDQwMDAwMDBaFw0xOTEwMDMyMzU5NTlaMIIBYDERMA8GA1UE\n" +
            "BRMIMDQwNTg2OTAxEzARBgsrBgEEAYI3PAIBAxMCR0IxHTAbBgNVBA8TFFByaXZh\n" +
            "dGUgT3JnYW5pemF0aW9uMQswCQYDVQQGEwJHQjEPMA0GA1UEERMGTTUgM0VRMRsw\n" +
            "GQYDVQQIExJHcmVhdGVyIE1hbmNoZXN0ZXIxEDAOBgNVBAcTB1NhbGZvcmQxFjAU\n" +
            "BgNVBAkTDVRyYWZmb3JkIFJvYWQxFjAUBgNVBAkTDUV4Y2hhbmdlIFF1YXkxJTAj\n" +
            "BgNVBAkTHDNyZCBGbG9vciwgMjYgT2ZmaWNlIFZpbGxhZ2UxGjAYBgNVBAoTEUNP\n" +
            "TU9ETyBDQSBMaW1pdGVkMRowGAYDVQQLExFDT01PRE8gRVYgU0dDIFNTTDE7MDkG\n" +
            "A1UEAxMydXNlcnRydXN0ZWNjY2VydGlmaWNhdGlvbmF1dGhvcml0eS1ldi5jb21v\n" +
            "ZG9jYS5jb20wWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQtMl8R33ZaWD6H8BW0\n" +
            "+wybBf0+6+L5YYK/eyAVGm6vwjLaQZWlcdFBMKfaP1qTLi0VAabs4baSUkD8wR56\n" +
            "8pVpo4IDjjCCA4owHwYDVR0jBBgwFoAUKpxa+U6hMNpASyvpS/H1nNwC+S4wHQYD\n" +
            "VR0OBBYEFLOtYfOaIfDHZGubtKNELRR6A2srMA4GA1UdDwEB/wQEAwIFgDAMBgNV\n" +
            "HRMBAf8EAjAAMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjBQBgNVHSAE\n" +
            "STBHMDwGDCsGAQQBsjEBAgEFATAsMCoGCCsGAQUFBwIBFh5odHRwczovL2Nwcy50\n" +
            "cnVzdC1wcm92aWRlci5jb20wBwYFZ4EMAQEwXwYDVR0fBFgwVjBUoFKgUIZOaHR0\n" +
            "cDovL2NybC50cnVzdC1wcm92aWRlci5jb20vVVNFUlRydXN0RUNDRXh0ZW5kZWRW\n" +
            "YWxpZGF0aW9uU2VjdXJlU2VydmVyQ0EuY3JsMIGYBggrBgEFBQcBAQSBizCBiDBa\n" +
            "BggrBgEFBQcwAoZOaHR0cDovL2NydC50cnVzdC1wcm92aWRlci5jb20vVVNFUlRy\n" +
            "dXN0RUNDRXh0ZW5kZWRWYWxpZGF0aW9uU2VjdXJlU2VydmVyQ0EuY3J0MCoGCCsG\n" +
            "AQUFBzABhh5odHRwOi8vb2NzcC50cnVzdC1wcm92aWRlci5jb20wPQYDVR0RBDYw\n" +
            "NIIydXNlcnRydXN0ZWNjY2VydGlmaWNhdGlvbmF1dGhvcml0eS1ldi5jb21vZG9j\n" +
            "YS5jb20wggF8BgorBgEEAdZ5AgQCBIIBbASCAWgBZgB1AKS5CZC0GFgUh7sTosxn\n" +
            "cAo8NZgE+RvfuON3zQ7IDdwQAAABXQ0/jQ0AAAQDAEYwRAIgPbaNWgoi6OfyNwL2\n" +
            "+jiySsoLrkx+0d4NJE1WnZQcfzwCICW4yvsXaMxoOXpQp3EPgrYk5Ajfvy/dY3Ui\n" +
            "0/dbQtHxAHYAVhQGmi/XwuzT9eG9RLI+x0Z2ubyZEVzA75SYVdaJ0N0AAAFdDT+K\n" +
            "xwAABAMARzBFAiB3GQasrX+akoHX02ZvXCcvhWCqv6qQOhLCUqflPoRbuAIhALwe\n" +
            "hrQo8S1Tm5vbMcxGiViq5ZcawxENWhxZ9hS0BZweAHUA7ku9t3XOYLrhQmkfq+Ge\n" +
            "ZqMPfl+wctiDAMR7iXqo/csAAAFdDT+M4AAABAMARjBEAiAjvp8w/fdTVW1VGE0T\n" +
            "I0YcCIXTYFDgzUMsEUiKHANAgwIgETQUcac7Hiis2fgQ+GdGF9yuh+xMo2Z8QXNu\n" +
            "1Cknf+8wCgYIKoZIzj0EAwIDSAAwRQIgQ5UiUI7xodmmMYNs3CmqlZHw/04BQRAR\n" +
            "4gRm7blZSIMCIQDHvIWTaPzSO6vwVzs6wSD6FqebLiFxoddC6aZG8Nm0wQ==\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator) throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Thu Nov 29 10:06:00 PST 2018", System.out);
    }
}
