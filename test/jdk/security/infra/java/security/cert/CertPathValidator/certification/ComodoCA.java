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
 * @bug 8189131 8231887
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
    // OID.2.5.4.17=M5 3EQ, C=GB, OID.2.5.4.15=Private Organization, OID.1.3.6.1.4.1.311.60.2.1.3=GB,
    // SERIALNUMBER=04058690
    // Issuer: CN=COMODO RSA Extended Validation Secure Server CA, O=COMODO CA Limited, L=Salford,
    // ST=Greater Manchester, C=GB
    // Serial number: a0c7cabcc25ed9358ded02cc1d485545
    // Valid from: Sun Sep 29 17:00:00 PDT 2019 until: Tue Dec 28 15:59:59 PST 2021
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIH0TCCBrmgAwIBAgIRAKDHyrzCXtk1je0CzB1IVUUwDQYJKoZIhvcNAQELBQAw\n" +
            "gZIxCzAJBgNVBAYTAkdCMRswGQYDVQQIExJHcmVhdGVyIE1hbmNoZXN0ZXIxEDAO\n" +
            "BgNVBAcTB1NhbGZvcmQxGjAYBgNVBAoTEUNPTU9ETyBDQSBMaW1pdGVkMTgwNgYD\n" +
            "VQQDEy9DT01PRE8gUlNBIEV4dGVuZGVkIFZhbGlkYXRpb24gU2VjdXJlIFNlcnZl\n" +
            "ciBDQTAeFw0xOTA5MzAwMDAwMDBaFw0yMTEyMjgyMzU5NTlaMIIBPjERMA8GA1UE\n" +
            "BRMIMDQwNTg2OTAxEzARBgsrBgEEAYI3PAIBAxMCR0IxHTAbBgNVBA8TFFByaXZh\n" +
            "dGUgT3JnYW5pemF0aW9uMQswCQYDVQQGEwJHQjEPMA0GA1UEERMGTTUgM0VRMRAw\n" +
            "DgYDVQQHEwdTYWxmb3JkMRYwFAYDVQQJEw1UcmFmZm9yZCBSb2FkMRYwFAYDVQQJ\n" +
            "Ew1FeGNoYW5nZSBRdWF5MSUwIwYDVQQJExwzcmQgRmxvb3IsIDI2IE9mZmljZSBW\n" +
            "aWxsYWdlMRgwFgYDVQQKEw9TZWN0aWdvIExpbWl0ZWQxGjAYBgNVBAsTEUNPTU9E\n" +
            "TyBFViBTR0MgU1NMMTgwNgYDVQQDEy9jb21vZG9yc2FjZXJ0aWZpY2F0aW9uYXV0\n" +
            "aG9yaXR5LWV2LmNvbW9kb2NhLmNvbTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCC\n" +
            "AQoCggEBAND/eZQBTjpBDsuteKwl+zpTitF8tJzwHAhcQHC2AaLF/GJl1rnjx4Of\n" +
            "elMhKhN1Od9KU6onHGOd2w4mD4EiYK9TpXwuwTyzfkCmnkqxZjYK3KAJN013o4L+\n" +
            "8y1zsGVUulpN/GfMaxTb4XdmeSekTP91Phw3xezijBq3sa++1rO5RBaT1IHeHhHv\n" +
            "iC9WNrG8CIg/j5MyC9i43LZHiRXLER1LzT/MCIRsiG5AEbiYXV5BNd5SiiHtBJ1q\n" +
            "0ZJH+AxL2ERaT41VCppboZwThmJGGoky9FWjp6z8U6Enx0fAMJIZNEzW6LAJFKPE\n" +
            "ynEU004jFFCEumPUqqCC4ogxulphY80CAwEAAaOCA3EwggNtMB8GA1UdIwQYMBaA\n" +
            "FDna/8ooFIqodBMIueQOqdL6fp1pMB0GA1UdDgQWBBQ+S4ZhIrwOoeGs9BBT4uXq\n" +
            "89Ux/jAOBgNVHQ8BAf8EBAMCBaAwDAYDVR0TAQH/BAIwADAdBgNVHSUEFjAUBggr\n" +
            "BgEFBQcDAQYIKwYBBQUHAwIwTwYDVR0gBEgwRjA7BgwrBgEEAbIxAQIBBQEwKzAp\n" +
            "BggrBgEFBQcCARYdaHR0cHM6Ly9zZWN1cmUuY29tb2RvLmNvbS9DUFMwBwYFZ4EM\n" +
            "AQEwVgYDVR0fBE8wTTBLoEmgR4ZFaHR0cDovL2NybC5jb21vZG9jYS5jb20vQ09N\n" +
            "T0RPUlNBRXh0ZW5kZWRWYWxpZGF0aW9uU2VjdXJlU2VydmVyQ0EuY3JsMIGHBggr\n" +
            "BgEFBQcBAQR7MHkwUQYIKwYBBQUHMAKGRWh0dHA6Ly9jcnQuY29tb2RvY2EuY29t\n" +
            "L0NPTU9ET1JTQUV4dGVuZGVkVmFsaWRhdGlvblNlY3VyZVNlcnZlckNBLmNydDAk\n" +
            "BggrBgEFBQcwAYYYaHR0cDovL29jc3AuY29tb2RvY2EuY29tMDoGA1UdEQQzMDGC\n" +
            "L2NvbW9kb3JzYWNlcnRpZmljYXRpb25hdXRob3JpdHktZXYuY29tb2RvY2EuY29t\n" +
            "MIIBfQYKKwYBBAHWeQIEAgSCAW0EggFpAWcAdQDuS723dc5guuFCaR+r4Z5mow9+\n" +
            "X7By2IMAxHuJeqj9ywAAAW2DAXefAAAEAwBGMEQCIDqP1einOiPHnaG1fOZMDrEc\n" +
            "RAxjq3vEl94fp4pkmke7AiBsJOvPE6irgcOO1/lnP7NRuln7iPJjU7T20PEK5/rm\n" +
            "KwB2AFWB1MIWkDYBSuoLm1c8U/DA5Dh4cCUIFy+jqh0HE9MMAAABbYMBd0kAAAQD\n" +
            "AEcwRQIhALgUI5XxM1NHbJDdr19h2pe3LhzK4tpuB/OQ9BgCyrGXAiBdr6mNCB/G\n" +
            "rbdVx0u7iezwC7mq7iaWugR3rrWlSA8fWQB2ALvZ37wfinG1k5Qjl6qSe0c4V5UK\n" +
            "q1LoGpCWZDaOHtGFAAABbYMBd1oAAAQDAEcwRQIgXbG32dagMeLhuZb+LSpJO1vI\n" +
            "BmxmRnNdiz5FbG9cCbwCIQCr1X9f+ebT5fhlDUNBURUorTtM8QQciBiueBqvHk7+\n" +
            "1DANBgkqhkiG9w0BAQsFAAOCAQEAM/A/1dgoc5NP1n+w3SX9qWcN7QT7ExdrnZSl\n" +
            "Ygn0PF2fx4gz7cvNKucbpQJNA4C9awGydyYK8/o5KDUXt3K7eb1OAZ/NZBjygsJs\n" +
            "ikXvxlBh8oEoqBOfOtr24l0NGUWnP8Qeu/VPcIMER4V8qX+in0pCXkSd67nkp6Bs\n" +
            "EcqhDPgmzdSC1gQHsZuBdotG14OfdH1cG1bRK6GadISLG1h8BFukVem42B149v8F\n" +
            "MCIUQAYprAVv2WlTZKBx9XzuK6IK3+klHZ07Jfvjvt7PPG5HKSMWBMnMaTHKcyQI\n" +
            "G3t91yw7BnNNInZlBSsFtqjbHhDcr7uruZdbi0rerSsi2qDr0w==\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=comodorsacertificationauthority-ev.comodoca.com, OU=COMODO EV SGC SSL, O=Sectigo Limited,
    // STREET="3rd Floor, 26 Office Village", STREET=Exchange Quay, STREET=Trafford Road, L=Salford,
    // ST=Greater Manchester, OID.2.5.4.17=M5 3EQ, C=GB, OID.2.5.4.15=Private Organization,
    // OID.1.3.6.1.4.1.311.60.2.1.3=GB, SERIALNUMBER=04058690
    // Issuer: CN=COMODO RSA Extended Validation Secure Server CA, O=COMODO CA Limited, L=Salford,
    // ST=Greater Manchester, C=GB
    // Serial number: d3df2597cbed1ab6e02ee82021771614
    // Valid from: Wed Nov 28 16:00:00 PST 2018 until: Fri Feb 26 15:59:59 PST 2021
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
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

    public void runTest(ValidatePathWithParams pathValidator) throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Wed Oct 02 06:06:24 PDT 2019", System.out);
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
    // OID.2.5.4.17=M5 3EQ, C=GB, OID.2.5.4.15=Private Organization, OID.1.3.6.1.4.1.311.60.2.1.3=GB,
    // SERIALNUMBER=04058690
    // Issuer: CN=COMODO ECC Extended Validation Secure Server CA, O=COMODO CA Limited, L=Salford,
    // ST=Greater Manchester, C=GB
    // Serial number: 7972d9d8472a2d52ad1ee6edfb16cbe1
    // Valid from: Sun Sep 29 17:00:00 PDT 2019 until: Tue Dec 28 15:59:59 PST 2021
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGPzCCBeWgAwIBAgIQeXLZ2EcqLVKtHubt+xbL4TAKBggqhkjOPQQDAjCBkjEL\n" +
            "MAkGA1UEBhMCR0IxGzAZBgNVBAgTEkdyZWF0ZXIgTWFuY2hlc3RlcjEQMA4GA1UE\n" +
            "BxMHU2FsZm9yZDEaMBgGA1UEChMRQ09NT0RPIENBIExpbWl0ZWQxODA2BgNVBAMT\n" +
            "L0NPTU9ETyBFQ0MgRXh0ZW5kZWQgVmFsaWRhdGlvbiBTZWN1cmUgU2VydmVyIENB\n" +
            "MB4XDTE5MDkzMDAwMDAwMFoXDTIxMTIyODIzNTk1OVowggE6MREwDwYDVQQFEwgw\n" +
            "NDA1ODY5MDETMBEGCysGAQQBgjc8AgEDEwJHQjEdMBsGA1UEDxMUUHJpdmF0ZSBP\n" +
            "cmdhbml6YXRpb24xCzAJBgNVBAYTAkdCMQ8wDQYDVQQREwZNNSAzRVExEDAOBgNV\n" +
            "BAcTB1NhbGZvcmQxFjAUBgNVBAkTDVRyYWZmb3JkIFJvYWQxFjAUBgNVBAkTDUV4\n" +
            "Y2hhbmdlIFF1YXkxJTAjBgNVBAkTHDNyZCBGbG9vciwgMjYgT2ZmaWNlIFZpbGxh\n" +
            "Z2UxGDAWBgNVBAoTD1NlY3RpZ28gTGltaXRlZDEWMBQGA1UECxMNQ09NT0RPIEVW\n" +
            "IFNTTDE4MDYGA1UEAxMvY29tb2RvZWNjY2VydGlmaWNhdGlvbmF1dGhvcml0eS1l\n" +
            "di5jb21vZG9jYS5jb20wWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAS3bqoFLtNG\n" +
            "7/J9H5GKosDNbYL5SykVmU5FzgSEt81gyAWShkqMSfAnO50fpr65E+o86E+BR3o8\n" +
            "V9FAU5wuOaGBo4IDcDCCA2wwHwYDVR0jBBgwFoAU007DGbpYWdEcYLdhU0c7p3eP\n" +
            "+IowHQYDVR0OBBYEFOlnS3MqxwXDpne8IQMXMZHlVKRXMA4GA1UdDwEB/wQEAwIF\n" +
            "gDAMBgNVHRMBAf8EAjAAMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjBP\n" +
            "BgNVHSAESDBGMDsGDCsGAQQBsjEBAgEFATArMCkGCCsGAQUFBwIBFh1odHRwczov\n" +
            "L3NlY3VyZS5jb21vZG8uY29tL0NQUzAHBgVngQwBATBWBgNVHR8ETzBNMEugSaBH\n" +
            "hkVodHRwOi8vY3JsLmNvbW9kb2NhLmNvbS9DT01PRE9FQ0NFeHRlbmRlZFZhbGlk\n" +
            "YXRpb25TZWN1cmVTZXJ2ZXJDQS5jcmwwgYcGCCsGAQUFBwEBBHsweTBRBggrBgEF\n" +
            "BQcwAoZFaHR0cDovL2NydC5jb21vZG9jYS5jb20vQ09NT0RPRUNDRXh0ZW5kZWRW\n" +
            "YWxpZGF0aW9uU2VjdXJlU2VydmVyQ0EuY3J0MCQGCCsGAQUFBzABhhhodHRwOi8v\n" +
            "b2NzcC5jb21vZG9jYS5jb20wOgYDVR0RBDMwMYIvY29tb2RvZWNjY2VydGlmaWNh\n" +
            "dGlvbmF1dGhvcml0eS1ldi5jb21vZG9jYS5jb20wggF8BgorBgEEAdZ5AgQCBIIB\n" +
            "bASCAWgBZgB1AO5Lvbd1zmC64UJpH6vhnmajD35fsHLYgwDEe4l6qP3LAAABbYME\n" +
            "EzgAAAQDAEYwRAIgbdo71lBleuJiq+D0ZLp51oVUyWD9EyrtgBSCNwIW4cMCIAqg\n" +
            "0VFTWHEmAVjaV23fGj3Ybu3mpSiHr6viGlgA2lYaAHUAVYHUwhaQNgFK6gubVzxT\n" +
            "8MDkOHhwJQgXL6OqHQcT0wwAAAFtgwQTKAAABAMARjBEAiBb/gW1RU7kgFBiNpHx\n" +
            "LStujKIocyENUTXsMbsac+LktwIgXbEr8vOOCEdBdXQ2F/FKec8ft6gz57mHNmwl\n" +
            "pp7phbQAdgC72d+8H4pxtZOUI5eqkntHOFeVCqtS6BqQlmQ2jh7RhQAAAW2DBBM6\n" +
            "AAAEAwBHMEUCIQDjKN3h86ofR94+JxLFoYuoA+DRtxEY8XGg+NQXlZfUrgIgEoO2\n" +
            "ZzKbGfohdwj/WtDwJDRX5pjXF4M0nECiwtYXDIwwCgYIKoZIzj0EAwIDSAAwRQIg\n" +
            "AkIRVQBwrElFjrnqk5XPvnlnwkIm1A70ayqOf1FexoQCIQC8tBTn//RCfrhcgTjd\n" +
            "ER4wRjFfFoc6lC68OHGVg9CZZg==\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=comodoecccertificationauthority-ev.comodoca.com, OU=COMODO EV SSL, O=Sectigo Limited,
    // STREET="3rd Floor, 26 Office Village", STREET=Exchange Quay, STREET=Trafford Road, L=Salford,
    // ST=Greater Manchester, OID.2.5.4.17=M5 3EQ, C=GB, OID.2.5.4.15=Private Organization,
    // OID.1.3.6.1.4.1.311.60.2.1.3=GB, SERIALNUMBER=04058690
    // Issuer: CN=COMODO ECC Extended Validation Secure Server CA, O=COMODO CA Limited, L=Salford,
    // ST=Greater Manchester, C=GB
    // Serial number: 603a5c2f85b63e00ba46ce8c3f6000b0
    // Valid from: Wed Nov 28 16:00:00 PST 2018 until: Fri Feb 26 15:59:59 PST 2021
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
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

    public void runTest(ValidatePathWithParams pathValidator) throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Wed Oct 02 06:05:57 PDT 2019", System.out);
    }
}

class ComodoUserTrustRSA {

    // Owner: CN=Sectigo RSA Extended Validation Secure Server CA, O=Sectigo Limited, L=Salford,
    // ST=Greater Manchester, C=GB
    // Issuer: CN=USERTrust RSA Certification Authority, O=The USERTRUST Network, L=Jersey City, ST=New Jersey, C=US
    // Serial number: 284e39c14b386d889c7299e58cd05a57
    // Valid from: Thu Nov 01 17:00:00 PDT 2018 until: Tue Dec 31 15:59:59 PST 2030
    private static final String INT_VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGNDCCBBygAwIBAgIQKE45wUs4bYiccpnljNBaVzANBgkqhkiG9w0BAQwFADCB\n" +
            "iDELMAkGA1UEBhMCVVMxEzARBgNVBAgTCk5ldyBKZXJzZXkxFDASBgNVBAcTC0pl\n" +
            "cnNleSBDaXR5MR4wHAYDVQQKExVUaGUgVVNFUlRSVVNUIE5ldHdvcmsxLjAsBgNV\n" +
            "BAMTJVVTRVJUcnVzdCBSU0EgQ2VydGlmaWNhdGlvbiBBdXRob3JpdHkwHhcNMTgx\n" +
            "MTAyMDAwMDAwWhcNMzAxMjMxMjM1OTU5WjCBkTELMAkGA1UEBhMCR0IxGzAZBgNV\n" +
            "BAgTEkdyZWF0ZXIgTWFuY2hlc3RlcjEQMA4GA1UEBxMHU2FsZm9yZDEYMBYGA1UE\n" +
            "ChMPU2VjdGlnbyBMaW1pdGVkMTkwNwYDVQQDEzBTZWN0aWdvIFJTQSBFeHRlbmRl\n" +
            "ZCBWYWxpZGF0aW9uIFNlY3VyZSBTZXJ2ZXIgQ0EwggEiMA0GCSqGSIb3DQEBAQUA\n" +
            "A4IBDwAwggEKAoIBAQCaoslYBiqFev0Yc4TXPa0s9oliMcn9VaENfTUK4GVT7niB\n" +
            "QXxC6Mt8kTtvyr5lU92hDQDh2WDPQsZ7oibh75t2kowT3z1S+Sy1GsUDM4NbdOde\n" +
            "orcmzFm/b4bwD4G/G+pB4EX1HSfjN9eT0Hje+AGvCrd2MmnxJ+Yymv9BH9OB65jK\n" +
            "rUO9Na4iHr48XWBDFvzsPCJ11Uioof6dRBVp+Lauj88Z7k2X8d606HeXn43h6acp\n" +
            "LLURWyqXM0CrzedVWBzuXKuBEaqD6w/1VpLJvSU+wl3ScvXSLFp82DSRJVJONXWl\n" +
            "dp9gjJioPGRByeZw11k3galbbF5gFK9xSnbDx29LAgMBAAGjggGNMIIBiTAfBgNV\n" +
            "HSMEGDAWgBRTeb9aqitKz1SA4dibwJ3ysgNmyzAdBgNVHQ4EFgQULGn/gMmHkK40\n" +
            "4bTnTJOFmUDpp7IwDgYDVR0PAQH/BAQDAgGGMBIGA1UdEwEB/wQIMAYBAf8CAQAw\n" +
            "HQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMCMDoGA1UdIAQzMDEwLwYEVR0g\n" +
            "ADAnMCUGCCsGAQUFBwIBFhlodHRwczovL2Nwcy51c2VydHJ1c3QuY29tMFAGA1Ud\n" +
            "HwRJMEcwRaBDoEGGP2h0dHA6Ly9jcmwudXNlcnRydXN0LmNvbS9VU0VSVHJ1c3RS\n" +
            "U0FDZXJ0aWZpY2F0aW9uQXV0aG9yaXR5LmNybDB2BggrBgEFBQcBAQRqMGgwPwYI\n" +
            "KwYBBQUHMAKGM2h0dHA6Ly9jcnQudXNlcnRydXN0LmNvbS9VU0VSVHJ1c3RSU0FB\n" +
            "ZGRUcnVzdENBLmNydDAlBggrBgEFBQcwAYYZaHR0cDovL29jc3AudXNlcnRydXN0\n" +
            "LmNvbTANBgkqhkiG9w0BAQwFAAOCAgEAQ4AzPxVypLyy3IjUUmVl7FaxrHsXQq2z\n" +
            "Zt2gKnHQShuA+5xpRPNndjvhHk4D08PZXUe6Im7E5knqxtyl5aYdldb+HI/7f+zd\n" +
            "W/1ub2N4Vq4ZYUjcZ1ECOFK7Z2zoNicDmU+Fe/TreXPuPsDicTG/tMcWEVM558OQ\n" +
            "TJkB2LK3ZhGukWM/RTMRcRdXaXOX8Lh0ylzRO1O0ObXytvOFpkkkD92HGsfS06i7\n" +
            "NLDPJEeZXqzHE5Tqj7VSAj+2luwfaXaPLD8lQEVci8xmsPGOn0mXE1ZzsChEPhVq\n" +
            "FYQUsbiRJRhidKauhd+G2CkRTcR5fpsuz+iStB9s5Fks9lKoXnn0hv78VYjvR78C\n" +
            "Cvj5FW/ounHjWTWMb3il9S5ngbFGcelB1l/MQkR63+1ybdi2OpjNWJCftxOWUpkC\n" +
            "xaRdnOnSj7GQY0NLn8Gtq9FcSZydtkVgXpouSFZkXNS/MYwbcCCcRKBbrk8ss0SI\n" +
            "Xg1gTURjh9VP1OHm0OktYcUw9e90wHIDn7h0qA+bWOsZquSRzT4s2crF3ZSA3tuV\n" +
            "/UJ33mjdVO8wBD8aI5y10QreSPJvZHHNDyCmoyjXvNhR+u3arXUoHWxO+MZBeXbi\n" +
            "iF7Nwn/IEmQvWBW8l6D26CXIavcY1kAJcfyzHkrPbLo+fAOa/KFl3lIU+0biEVNk\n" +
            "Q9zXE6hC6X4=\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=USERTrust RSA Extended Validation Secure Server CA,
    // O=The USERTRUST Network, L=Jersey City, ST=New Jersey, C=US
    // Issuer: CN=USERTrust RSA Certification Authority, O=The USERTRUST Network,
    // L=Jersey City, ST=New Jersey, C=US
    // Serial number: f6bb751efa7d2e8368e606407334f83
    // Valid from: Sat Feb 11 16:00:00 PST 2012 until: Thu Feb 11 15:59:59 PST 2027
    private static final String INT_REVOKED = "-----BEGIN CERTIFICATE-----\n"
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

    // Owner: CN=usertrustrsacertificationauthority-ev.comodoca.com, OU=COMODO EV SGC SSL, O=Sectigo Limited,
    // STREET="3rd Floor, 26 Office Village", STREET=Exchange Quay, STREET=Trafford Road, L=Salford, ST=Manchester,
    // OID.2.5.4.17=M5 3EQ, C=GB, OID.2.5.4.15=Private Organization, OID.1.3.6.1.4.1.311.60.2.1.3=GB,
    // SERIALNUMBER=04058690
    // Issuer: CN=Sectigo RSA Extended Validation Secure Server CA, O=Sectigo Limited, L=Salford,
    // ST=Greater Manchester, C=GB
    // Serial number: b07fd164b5790c9d5d1fddff5819cdb2
    // Valid from: Sun Sep 29 17:00:00 PDT 2019 until: Tue Dec 28 15:59:59 PST 2021
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIH5TCCBs2gAwIBAgIRALB/0WS1eQydXR/d/1gZzbIwDQYJKoZIhvcNAQELBQAw\n" +
            "gZExCzAJBgNVBAYTAkdCMRswGQYDVQQIExJHcmVhdGVyIE1hbmNoZXN0ZXIxEDAO\n" +
            "BgNVBAcTB1NhbGZvcmQxGDAWBgNVBAoTD1NlY3RpZ28gTGltaXRlZDE5MDcGA1UE\n" +
            "AxMwU2VjdGlnbyBSU0EgRXh0ZW5kZWQgVmFsaWRhdGlvbiBTZWN1cmUgU2VydmVy\n" +
            "IENBMB4XDTE5MDkzMDAwMDAwMFoXDTIxMTIyODIzNTk1OVowggFWMREwDwYDVQQF\n" +
            "EwgwNDA1ODY5MDETMBEGCysGAQQBgjc8AgEDEwJHQjEdMBsGA1UEDxMUUHJpdmF0\n" +
            "ZSBPcmdhbml6YXRpb24xCzAJBgNVBAYTAkdCMQ8wDQYDVQQREwZNNSAzRVExEzAR\n" +
            "BgNVBAgTCk1hbmNoZXN0ZXIxEDAOBgNVBAcTB1NhbGZvcmQxFjAUBgNVBAkTDVRy\n" +
            "YWZmb3JkIFJvYWQxFjAUBgNVBAkTDUV4Y2hhbmdlIFF1YXkxJTAjBgNVBAkTHDNy\n" +
            "ZCBGbG9vciwgMjYgT2ZmaWNlIFZpbGxhZ2UxGDAWBgNVBAoTD1NlY3RpZ28gTGlt\n" +
            "aXRlZDEaMBgGA1UECxMRQ09NT0RPIEVWIFNHQyBTU0wxOzA5BgNVBAMTMnVzZXJ0\n" +
            "cnVzdHJzYWNlcnRpZmljYXRpb25hdXRob3JpdHktZXYuY29tb2RvY2EuY29tMIIB\n" +
            "IjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnh/rxeiYwpLa651eLvGnR+RE\n" +
            "rhDWkTZtqZcHw9Oy7JL2uELyEPbM+v0az40cBHS0bQZJZbWmXNukMUMSwIb4z7t8\n" +
            "OXlxz9uvxEufvlqBl4qeC/z3LpFBRRHEero3yGKVwkoe1aP2Pq7Udi+7i7eVZZdA\n" +
            "1ticxZWo/UBU9mwbIOYqf/4xzZ6G891hKb+NAuuEfxG52vXZl8odMThfHuDlkfS7\n" +
            "nZMQBaO40KJeSEBhr+5TIS7d7tWWye/F6oEQ0+dHBiF9PyZ1dXoO8aue/80mP+0F\n" +
            "MYTmRFsKHge6ZjojfH9cLlR5kTqtP5Tqh5GBQ4zp3uyIBBU6ylKp9PNHkewGUQID\n" +
            "AQABo4IDbjCCA2owHwYDVR0jBBgwFoAULGn/gMmHkK404bTnTJOFmUDpp7IwHQYD\n" +
            "VR0OBBYEFHz7cvDn1LYe2M+z4plwQn7rt938MA4GA1UdDwEB/wQEAwIFoDAMBgNV\n" +
            "HRMBAf8EAjAAMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjBJBgNVHSAE\n" +
            "QjBAMDUGDCsGAQQBsjEBAgEFATAlMCMGCCsGAQUFBwIBFhdodHRwczovL3NlY3Rp\n" +
            "Z28uY29tL0NQUzAHBgVngQwBATBWBgNVHR8ETzBNMEugSaBHhkVodHRwOi8vY3Js\n" +
            "LnNlY3RpZ28uY29tL1NlY3RpZ29SU0FFeHRlbmRlZFZhbGlkYXRpb25TZWN1cmVT\n" +
            "ZXJ2ZXJDQS5jcmwwgYYGCCsGAQUFBwEBBHoweDBRBggrBgEFBQcwAoZFaHR0cDov\n" +
            "L2NydC5zZWN0aWdvLmNvbS9TZWN0aWdvUlNBRXh0ZW5kZWRWYWxpZGF0aW9uU2Vj\n" +
            "dXJlU2VydmVyQ0EuY3J0MCMGCCsGAQUFBzABhhdodHRwOi8vb2NzcC5zZWN0aWdv\n" +
            "LmNvbTA9BgNVHREENjA0gjJ1c2VydHJ1c3Ryc2FjZXJ0aWZpY2F0aW9uYXV0aG9y\n" +
            "aXR5LWV2LmNvbW9kb2NhLmNvbTCCAX4GCisGAQQB1nkCBAIEggFuBIIBagFoAHYA\n" +
            "7ku9t3XOYLrhQmkfq+GeZqMPfl+wctiDAMR7iXqo/csAAAFtgzv54wAABAMARzBF\n" +
            "AiB5PmhsK3zU3XdKvyxw/wWHMmLI7apHLa1yKdjkA8H+ggIhALdUx7Tl8aeWhK6z\n" +
            "lh+PHvMAdCcAJK6w9qBJGQtSrYO5AHUAVYHUwhaQNgFK6gubVzxT8MDkOHhwJQgX\n" +
            "L6OqHQcT0wwAAAFtgzv5zgAABAMARjBEAiBumSwAUamibqJXTN2cf/H3mjd0T35/\n" +
            "UK9w2hu9gFobxgIgSXTLndHyqFUmcmquu3It0WC1yl6YMceGixbQL1e8BQcAdwC7\n" +
            "2d+8H4pxtZOUI5eqkntHOFeVCqtS6BqQlmQ2jh7RhQAAAW2DO/nXAAAEAwBIMEYC\n" +
            "IQDHRs10oYoXE5yq6WsiksjdQsUWZNpbSsrmz0u+KlxTVQIhAJ4rvHItKSeJLkaN\n" +
            "S3YpVZnkN8tOwuxPsYeyVx/BtaNpMA0GCSqGSIb3DQEBCwUAA4IBAQAPFIsUFymo\n" +
            "VTp0vntHrZpBApBQzDeriQv7Bi7tmou/Ng47RtXW3DjGdrePGSfOdl7h62k8qprU\n" +
            "JeLyloDqhvmT/CG/hdwrfZ3Sv3N2xpetGcnW5S3oEi3m+/M1ls9eD+x1vybqV9Kd\n" +
            "lcjuV7SYDlbvAS9w7TcygudhdW0cI8XTCvesGKohBkAlqaQ/MWYpt4WvsxHjbWgn\n" +
            "5ZlIYR6A1ZFEjADifViH/5AA79lgGhAskkIWPjvRFalEVKTKtjhRK76eCfZs4Frr\n" +
            "CEOpon+BeNKk+x/K/r10dSoWe0SV2uGVxTD83zkP++eREwo1hTgn8bXn7ftlnA3j\n" +
            "7ml+Usz6udaD\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=usertrustrsacertificationauthority-ev.comodoca.com, OU=COMODO EV SGC SSL, O=Sectigo Limited,
    // STREET="3rd Floor, 26 Office Village", STREET=Exchange Quay, STREET=Trafford Road, L=Salford,
    // ST=Greater Manchester, OID.2.5.4.17=M5 3EQ, C=GB, OID.2.5.4.15=Private Organization,
    // OID.1.3.6.1.4.1.311.60.2.1.3=GB, SERIALNUMBER=04058690
    // Issuer: CN=USERTrust RSA Extended Validation Secure Server CA, O=The USERTRUST Network, L=Jersey City,
    // ST=New Jersey, C=US
    // Serial number: d3c204e8df6a1539568cf15e97e57b1d
    // Valid from: Wed Nov 28 16:00:00 PST 2018 until: Fri Feb 26 15:59:59 PST 2021
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
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

    public void runTest(ValidatePathWithParams pathValidator) throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID, INT_VALID},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT_REVOKED},
                ValidatePathWithParams.Status.REVOKED,
                "Wed Oct 02 06:07:12 PDT 2019", System.out);
    }
}

class ComodoUserTrustECC {

    // Owner: CN=Sectigo ECC Extended Validation Secure Server CA, O=Sectigo Limited, L=Salford,
    // ST=Greater Manchester, C=GB
    // Issuer: CN=USERTrust ECC Certification Authority, O=The USERTRUST Network, L=Jersey City, ST=New Jersey, C=US
    // Serial number: 80f5606d3a162b143adc12fbe8c2066f
    // Valid from: Thu Nov 01 17:00:00 PDT 2018 until: Tue Dec 31 15:59:59 PST 2030
    private static final String INT_VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIDyTCCA0+gAwIBAgIRAID1YG06FisUOtwS++jCBm8wCgYIKoZIzj0EAwMwgYgx\n" +
            "CzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpOZXcgSmVyc2V5MRQwEgYDVQQHEwtKZXJz\n" +
            "ZXkgQ2l0eTEeMBwGA1UEChMVVGhlIFVTRVJUUlVTVCBOZXR3b3JrMS4wLAYDVQQD\n" +
            "EyVVU0VSVHJ1c3QgRUNDIENlcnRpZmljYXRpb24gQXV0aG9yaXR5MB4XDTE4MTEw\n" +
            "MjAwMDAwMFoXDTMwMTIzMTIzNTk1OVowgZExCzAJBgNVBAYTAkdCMRswGQYDVQQI\n" +
            "ExJHcmVhdGVyIE1hbmNoZXN0ZXIxEDAOBgNVBAcTB1NhbGZvcmQxGDAWBgNVBAoT\n" +
            "D1NlY3RpZ28gTGltaXRlZDE5MDcGA1UEAxMwU2VjdGlnbyBFQ0MgRXh0ZW5kZWQg\n" +
            "VmFsaWRhdGlvbiBTZWN1cmUgU2VydmVyIENBMFkwEwYHKoZIzj0CAQYIKoZIzj0D\n" +
            "AQcDQgAEAyJ5Ca9JyXq8bO+krLVWysbtm7fdMSJ54uFD23t0x6JAC4IjxevfQJzW\n" +
            "z4T6yY+FybTBqtOa++ijJFnkB5wKy6OCAY0wggGJMB8GA1UdIwQYMBaAFDrhCYbU\n" +
            "zxnClnZ0SXbc4DXGY2OaMB0GA1UdDgQWBBTvwSqVDDLa+3Mw3IoT2BVL9xPo+DAO\n" +
            "BgNVHQ8BAf8EBAMCAYYwEgYDVR0TAQH/BAgwBgEB/wIBADAdBgNVHSUEFjAUBggr\n" +
            "BgEFBQcDAQYIKwYBBQUHAwIwOgYDVR0gBDMwMTAvBgRVHSAAMCcwJQYIKwYBBQUH\n" +
            "AgEWGWh0dHBzOi8vY3BzLnVzZXJ0cnVzdC5jb20wUAYDVR0fBEkwRzBFoEOgQYY/\n" +
            "aHR0cDovL2NybC51c2VydHJ1c3QuY29tL1VTRVJUcnVzdEVDQ0NlcnRpZmljYXRp\n" +
            "b25BdXRob3JpdHkuY3JsMHYGCCsGAQUFBwEBBGowaDA/BggrBgEFBQcwAoYzaHR0\n" +
            "cDovL2NydC51c2VydHJ1c3QuY29tL1VTRVJUcnVzdEVDQ0FkZFRydXN0Q0EuY3J0\n" +
            "MCUGCCsGAQUFBzABhhlodHRwOi8vb2NzcC51c2VydHJ1c3QuY29tMAoGCCqGSM49\n" +
            "BAMDA2gAMGUCMQCjHztBDL90GCRXHlGqm0H7kzP04hd0MxwakKjWzOmstXNFLONj\n" +
            "RFa0JqI/iKUJMFcCMCbLgyzcFW7DihtY5XE0XCLCw+git0NjxiFB6FaOFIlyDdqT\n" +
            "j+Th+DJ92JLvICVD/g==\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=USERTrust ECC Extended Validation Secure Server CA, O=The USERTRUST Network,
    // L=Jersey City, ST=New Jersey, C=US
    // Issuer: CN=USERTrust ECC Certification Authority, O=The USERTRUST Network,
    // L=Jersey City, ST=New Jersey, C=US
    // Serial number: 3d09b24f5c08a7ce8eb85a51d3c1aa52
    // Valid from: Sun Apr 14 17:00:00 PDT 2013 until: Fri Apr 14 16:59:59 PDT 2028
    private static final String INT_REVOKED = "-----BEGIN CERTIFICATE-----\n"
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
    // OID.2.5.4.17=M5 3EQ, C=GB, OID.2.5.4.15=Private Organization, OID.1.3.6.1.4.1.311.60.2.1.3=GB,
    // SERIALNUMBER=04058690
    // Issuer: CN=Sectigo ECC Extended Validation Secure Server CA, O=Sectigo Limited, L=Salford,
    // ST=Greater Manchester, C=GB
    // Serial number: 8b72489b7f505a55e2a22659c90ed2ab
    // Valid from: Sun Sep 29 17:00:00 PDT 2019 until: Tue Dec 28 15:59:59 PST 2021
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGRTCCBeugAwIBAgIRAItySJt/UFpV4qImWckO0qswCgYIKoZIzj0EAwIwgZEx\n" +
            "CzAJBgNVBAYTAkdCMRswGQYDVQQIExJHcmVhdGVyIE1hbmNoZXN0ZXIxEDAOBgNV\n" +
            "BAcTB1NhbGZvcmQxGDAWBgNVBAoTD1NlY3RpZ28gTGltaXRlZDE5MDcGA1UEAxMw\n" +
            "U2VjdGlnbyBFQ0MgRXh0ZW5kZWQgVmFsaWRhdGlvbiBTZWN1cmUgU2VydmVyIENB\n" +
            "MB4XDTE5MDkzMDAwMDAwMFoXDTIxMTIyODIzNTk1OVowggFBMREwDwYDVQQFEwgw\n" +
            "NDA1ODY5MDETMBEGCysGAQQBgjc8AgEDEwJHQjEdMBsGA1UEDxMUUHJpdmF0ZSBP\n" +
            "cmdhbml6YXRpb24xCzAJBgNVBAYTAkdCMQ8wDQYDVQQREwZNNSAzRVExEDAOBgNV\n" +
            "BAcTB1NhbGZvcmQxFjAUBgNVBAkTDVRyYWZmb3JkIFJvYWQxFjAUBgNVBAkTDUV4\n" +
            "Y2hhbmdlIFF1YXkxJTAjBgNVBAkTHDNyZCBGbG9vciwgMjYgT2ZmaWNlIFZpbGxh\n" +
            "Z2UxGDAWBgNVBAoTD1NlY3RpZ28gTGltaXRlZDEaMBgGA1UECxMRQ09NT0RPIEVW\n" +
            "IFNHQyBTU0wxOzA5BgNVBAMTMnVzZXJ0cnVzdGVjY2NlcnRpZmljYXRpb25hdXRo\n" +
            "b3JpdHktZXYuY29tb2RvY2EuY29tMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE\n" +
            "LTJfEd92Wlg+h/AVtPsMmwX9Puvi+WGCv3sgFRpur8Iy2kGVpXHRQTCn2j9aky4t\n" +
            "FQGm7OG2klJA/MEeevKVaaOCA28wggNrMB8GA1UdIwQYMBaAFO/BKpUMMtr7czDc\n" +
            "ihPYFUv3E+j4MB0GA1UdDgQWBBSzrWHzmiHwx2Rrm7SjRC0UegNrKzAOBgNVHQ8B\n" +
            "Af8EBAMCB4AwDAYDVR0TAQH/BAIwADAdBgNVHSUEFjAUBggrBgEFBQcDAQYIKwYB\n" +
            "BQUHAwIwSQYDVR0gBEIwQDA1BgwrBgEEAbIxAQIBBQEwJTAjBggrBgEFBQcCARYX\n" +
            "aHR0cHM6Ly9zZWN0aWdvLmNvbS9DUFMwBwYFZ4EMAQEwVgYDVR0fBE8wTTBLoEmg\n" +
            "R4ZFaHR0cDovL2NybC5zZWN0aWdvLmNvbS9TZWN0aWdvRUNDRXh0ZW5kZWRWYWxp\n" +
            "ZGF0aW9uU2VjdXJlU2VydmVyQ0EuY3JsMIGGBggrBgEFBQcBAQR6MHgwUQYIKwYB\n" +
            "BQUHMAKGRWh0dHA6Ly9jcnQuc2VjdGlnby5jb20vU2VjdGlnb0VDQ0V4dGVuZGVk\n" +
            "VmFsaWRhdGlvblNlY3VyZVNlcnZlckNBLmNydDAjBggrBgEFBQcwAYYXaHR0cDov\n" +
            "L29jc3Auc2VjdGlnby5jb20wPQYDVR0RBDYwNIIydXNlcnRydXN0ZWNjY2VydGlm\n" +
            "aWNhdGlvbmF1dGhvcml0eS1ldi5jb21vZG9jYS5jb20wggF/BgorBgEEAdZ5AgQC\n" +
            "BIIBbwSCAWsBaQB2AO5Lvbd1zmC64UJpH6vhnmajD35fsHLYgwDEe4l6qP3LAAAB\n" +
            "bYL/SJoAAAQDAEcwRQIhAL7EJt/Rgz6NBnx2v8Hevux3Gpcxy64kaeyLVgFeNqFk\n" +
            "AiBRf+OWLOtZzEav/oERljrk8hgZB4CR1nj/Tn98cmRrwwB2AFWB1MIWkDYBSuoL\n" +
            "m1c8U/DA5Dh4cCUIFy+jqh0HE9MMAAABbYL/SIgAAAQDAEcwRQIgVtZZaiBMC2lu\n" +
            "atBzUHQmOq4qrUQP7nS83cd3VzPhToECIQDnlpOCdaxJwr8C0MtkvYpKSabwBPFL\n" +
            "ASEkwmOpjuQErAB3ALvZ37wfinG1k5Qjl6qSe0c4V5UKq1LoGpCWZDaOHtGFAAAB\n" +
            "bYL/SJoAAAQDAEgwRgIhAI8OgzP/kzF1bOJRHU2S/ewij/6HpGPy7Mbm7Hyuv3IU\n" +
            "AiEAxDmX2FmORlgeerQmQ+ar3D9/TwA9RQckVDu5IrgweREwCgYIKoZIzj0EAwID\n" +
            "SAAwRQIhAPwQWGWd3oR7YJ7ngCDQ9TAbdPgND51SiR34WfEgaTQtAiAxD4umKm02\n" +
            "59GEMj5NpyF2ZQEq5mEGcjJNojrn+PC4zg==\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=usertrustecccertificationauthority-ev.comodoca.com, OU=COMODO EV SGC SSL, O=Sectigo Limited,
    // STREET="3rd Floor, 26 Office Village", STREET=Exchange Quay, STREET=Trafford Road, L=Salford,
    // ST=Greater Manchester, OID.2.5.4.17=M5 3EQ, C=GB, OID.2.5.4.15=Private Organization,
    // OID.1.3.6.1.4.1.311.60.2.1.3=GB, SERIALNUMBER=04058690
    // Issuer: CN=USERTrust ECC Extended Validation Secure Server CA, O=The USERTRUST Network, L=Jersey City,
    // ST=New Jersey, C=US
    // Serial number: ab1455f9833ae7783f95de8744181f6a
    // Valid from: Wed Nov 28 16:00:00 PST 2018 until: Fri Feb 26 15:59:59 PST 2021
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
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

    public void runTest(ValidatePathWithParams pathValidator) throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID, INT_VALID},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT_REVOKED},
                ValidatePathWithParams.Status.REVOKED,
                "Wed Oct 02 06:06:50 PDT 2019", System.out);
    }
}
