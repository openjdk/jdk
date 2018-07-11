/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8195774
 * @summary Interoperability tests with Entrust EC CA
 * @build ValidatePathWithParams
 * @run main/othervm -Djava.security.debug=certpath EntrustCA OCSP
 * @run main/othervm -Djava.security.debug=certpath EntrustCA CRL
 */

/*
 * Obtain test artifacts for Entrust EC CA from:
 *
 * Valid https://validec.entrust.net
 *
 * Revoked https://revokedec.entrust.net
 */
public class EntrustCA {

    // Owner: CN=Entrust Certification Authority - L1J, OU="(c) 2016 Entrust, Inc. - for authorized use only",
    // OU=See www.entrust.net/legal-terms, O="Entrust, Inc.", C=US
    // Issuer: CN=Entrust Root Certification Authority - EC1, OU="(c) 2012 Entrust, Inc. - for authorized use only",
    // OU=See www.entrust.net/legal-terms, O="Entrust, Inc.", C=US
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIID5zCCA2ygAwIBAgIQCoPUgD5+n1EAAAAAUdTB9zAKBggqhkjOPQQDAzCBvzEL\n" +
            "MAkGA1UEBhMCVVMxFjAUBgNVBAoTDUVudHJ1c3QsIEluYy4xKDAmBgNVBAsTH1Nl\n" +
            "ZSB3d3cuZW50cnVzdC5uZXQvbGVnYWwtdGVybXMxOTA3BgNVBAsTMChjKSAyMDEy\n" +
            "IEVudHJ1c3QsIEluYy4gLSBmb3IgYXV0aG9yaXplZCB1c2Ugb25seTEzMDEGA1UE\n" +
            "AxMqRW50cnVzdCBSb290IENlcnRpZmljYXRpb24gQXV0aG9yaXR5IC0gRUMxMB4X\n" +
            "DTE2MDQwNTIwMTk1NFoXDTM3MTAwNTIwNDk1NFowgboxCzAJBgNVBAYTAlVTMRYw\n" +
            "FAYDVQQKEw1FbnRydXN0LCBJbmMuMSgwJgYDVQQLEx9TZWUgd3d3LmVudHJ1c3Qu\n" +
            "bmV0L2xlZ2FsLXRlcm1zMTkwNwYDVQQLEzAoYykgMjAxNiBFbnRydXN0LCBJbmMu\n" +
            "IC0gZm9yIGF1dGhvcml6ZWQgdXNlIG9ubHkxLjAsBgNVBAMTJUVudHJ1c3QgQ2Vy\n" +
            "dGlmaWNhdGlvbiBBdXRob3JpdHkgLSBMMUowdjAQBgcqhkjOPQIBBgUrgQQAIgNi\n" +
            "AAT14eFXmpQX/dEf7NAxrMH13n0btz1KKvH2S1rROGPAKex2CY8yxznbffK/MbCk\n" +
            "F7ByYXGs1+8kL5xmTysU/c+YmjOZx2mMSAk2DPw30fijJ3tRrwChZ+TBpgtB6+A5\n" +
            "MsCjggEuMIIBKjAOBgNVHQ8BAf8EBAMCAQYwEgYDVR0TAQH/BAgwBgEB/wIBADAz\n" +
            "BggrBgEFBQcBAQQnMCUwIwYIKwYBBQUHMAGGF2h0dHA6Ly9vY3NwLmVudHJ1c3Qu\n" +
            "bmV0MDMGA1UdHwQsMCowKKAmoCSGImh0dHA6Ly9jcmwuZW50cnVzdC5uZXQvZWMx\n" +
            "cm9vdC5jcmwwOwYDVR0gBDQwMjAwBgRVHSAAMCgwJgYIKwYBBQUHAgEWGmh0dHA6\n" +
            "Ly93d3cuZW50cnVzdC5uZXQvcnBhMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEF\n" +
            "BQcDAjAdBgNVHQ4EFgQUw/lFA77I+Qs8RTXz63Ls5+jrlJswHwYDVR0jBBgwFoAU\n" +
            "t2PnGt2N6QimVYOk4GpQQWURQkkwCgYIKoZIzj0EAwMDaQAwZgIxAPnVAOqxKDd7\n" +
            "v37EBmpPqWCCWBFPKW6HpRx3GUWc9caeQIw8rO2HXYgf92pb/TsJYAIxAJhI0MpR\n" +
            "z5L42xF1R9UIPfQxCMwgsnWBqIqcfMrMO+2DxQy6GIP3cFFj9gRyxguKWw==\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=validec.entrust.net, SERIALNUMBER=D15576572, OID.2.5.4.15=Private Organization, O="Entrust, Inc.",
    // OID.1.3.6.1.4.1.311.60.2.1.2=Maryland, OID.1.3.6.1.4.1.311.60.2.1.3=US, L=Kanata, ST=Ontario, C=CA
    // Issuer: CN=Entrust Certification Authority - L1J, OU="(c) 2016 Entrust, Inc. - for authorized use only",
    // OU=See www.entrust.net/legal-terms, O="Entrust, Inc.", C=US
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIFrTCCBTKgAwIBAgIQYtgW4DLwh74AAAAAVqBXkTAKBggqhkjOPQQDAjCBujEL\n" +
            "MAkGA1UEBhMCVVMxFjAUBgNVBAoTDUVudHJ1c3QsIEluYy4xKDAmBgNVBAsTH1Nl\n" +
            "ZSB3d3cuZW50cnVzdC5uZXQvbGVnYWwtdGVybXMxOTA3BgNVBAsTMChjKSAyMDE2\n" +
            "IEVudHJ1c3QsIEluYy4gLSBmb3IgYXV0aG9yaXplZCB1c2Ugb25seTEuMCwGA1UE\n" +
            "AxMlRW50cnVzdCBDZXJ0aWZpY2F0aW9uIEF1dGhvcml0eSAtIEwxSjAeFw0xODA2\n" +
            "MjUxMzE1NTdaFw0xOTA2MjUxMzQ1NTBaMIHJMQswCQYDVQQGEwJDQTEQMA4GA1UE\n" +
            "CBMHT250YXJpbzEPMA0GA1UEBxMGS2FuYXRhMRMwEQYLKwYBBAGCNzwCAQMTAlVT\n" +
            "MRkwFwYLKwYBBAGCNzwCAQITCE1hcnlsYW5kMRYwFAYDVQQKEw1FbnRydXN0LCBJ\n" +
            "bmMuMR0wGwYDVQQPExRQcml2YXRlIE9yZ2FuaXphdGlvbjESMBAGA1UEBRMJRDE1\n" +
            "NTc2NTcyMRwwGgYDVQQDExN2YWxpZGVjLmVudHJ1c3QubmV0MFkwEwYHKoZIzj0C\n" +
            "AQYIKoZIzj0DAQcDQgAEHQe7lUaAUgIwR9EiLJlhkbx+HfSr22M3JvQD6+fnYgqd\n" +
            "55e6E1UE45fk92UpqPi1CEbXrdpmWKu1Z470B9cPGaOCAwcwggMDMB4GA1UdEQQX\n" +
            "MBWCE3ZhbGlkZWMuZW50cnVzdC5uZXQwggF/BgorBgEEAdZ5AgQCBIIBbwSCAWsB\n" +
            "aQB1AFWB1MIWkDYBSuoLm1c8U/DA5Dh4cCUIFy+jqh0HE9MMAAABZDcxpMkAAAQD\n" +
            "AEYwRAIgIb0PwjCcNOchJg8Zywz/0Lwm2vEOJUSao6BqNUIsyaYCIElHHexB06LE\n" +
            "yXWDXO7UqOtWT6uqkdJN8V4TzwT9B4o4AHcA3esdK3oNT6Ygi4GtgWhwfi6OnQHV\n" +
            "XIiNPRHEzbbsvswAAAFkNzGkvgAABAMASDBGAiEAlxy/kxB9waIifYn+EV550pvA\n" +
            "C3jUfS/bjsKbcsBH9cQCIQDSHTJORz6fZu8uLFhpV525pw7iHVh2dSn3gpcteObh\n" +
            "DQB3ALvZ37wfinG1k5Qjl6qSe0c4V5UKq1LoGpCWZDaOHtGFAAABZDcxpTsAAAQD\n" +
            "AEgwRgIhAPCBqVqSvAEIXMPloV0tfBEEdjRrAhiG407cPqYwt9AFAiEAuQf4R5os\n" +
            "MLkD3XhxvrTDvnD+PUOf8PzPevsWkuxNqcQwDgYDVR0PAQH/BAQDAgeAMB0GA1Ud\n" +
            "JQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjBjBggrBgEFBQcBAQRXMFUwIwYIKwYB\n" +
            "BQUHMAGGF2h0dHA6Ly9vY3NwLmVudHJ1c3QubmV0MC4GCCsGAQUFBzAChiJodHRw\n" +
            "Oi8vYWlhLmVudHJ1c3QubmV0L2wxai1lYzEuY2VyMDMGA1UdHwQsMCowKKAmoCSG\n" +
            "Imh0dHA6Ly9jcmwuZW50cnVzdC5uZXQvbGV2ZWwxai5jcmwwSgYDVR0gBEMwQTA2\n" +
            "BgpghkgBhvpsCgECMCgwJgYIKwYBBQUHAgEWGmh0dHA6Ly93d3cuZW50cnVzdC5u\n" +
            "ZXQvcnBhMAcGBWeBDAEBMB8GA1UdIwQYMBaAFMP5RQO+yPkLPEU18+ty7Ofo65Sb\n" +
            "MB0GA1UdDgQWBBT+J7OhS6gskCanmOGnx10DPSF8ATAJBgNVHRMEAjAAMAoGCCqG\n" +
            "SM49BAMCA2kAMGYCMQCQLUQABT74TmdHzAtB97uNF5+Zy15wzkmlKeRSOXCIf2C5\n" +
            "YKjsgdkR1OdzZXcpjNgCMQDfWcdPhodNXZC4l1lLPOPaTzPPw6uVqqoITQlc6r1t\n" +
            "dRkkD6K9ii/X8EtwoFp7s80=\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=revokedec.entrust.net, SERIALNUMBER=115868500, OID.2.5.4.15=Private Organization, O="Entrust, Inc.",
    // OID.1.3.6.1.4.1.311.60.2.1.2=Texas, OID.1.3.6.1.4.1.311.60.2.1.3=US, L=Kanata, ST=Ontario, C=CA
    // Issuer: CN=Entrust Certification Authority - L1J, OU="(c) 2016 Entrust, Inc. - for authorized use only",
    // OU=See www.entrust.net/legal-terms, O="Entrust, Inc.", C=US
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGJzCCBaygAwIBAgIRAM0WDfag1taIAAAAAFagJ5gwCgYIKoZIzj0EAwIwgbox\n" +
            "CzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1FbnRydXN0LCBJbmMuMSgwJgYDVQQLEx9T\n" +
            "ZWUgd3d3LmVudHJ1c3QubmV0L2xlZ2FsLXRlcm1zMTkwNwYDVQQLEzAoYykgMjAx\n" +
            "NiBFbnRydXN0LCBJbmMuIC0gZm9yIGF1dGhvcml6ZWQgdXNlIG9ubHkxLjAsBgNV\n" +
            "BAMTJUVudHJ1c3QgQ2VydGlmaWNhdGlvbiBBdXRob3JpdHkgLSBMMUowHhcNMTcw\n" +
            "NTI0MTcwNzA4WhcNMTkwNTI0MTczNjU1WjCByDELMAkGA1UEBhMCQ0ExEDAOBgNV\n" +
            "BAgTB09udGFyaW8xDzANBgNVBAcTBkthbmF0YTETMBEGCysGAQQBgjc8AgEDEwJV\n" +
            "UzEWMBQGCysGAQQBgjc8AgECEwVUZXhhczEWMBQGA1UEChMNRW50cnVzdCwgSW5j\n" +
            "LjEdMBsGA1UEDxMUUHJpdmF0ZSBPcmdhbml6YXRpb24xEjAQBgNVBAUTCTExNTg2\n" +
            "ODUwMDEeMBwGA1UEAxMVcmV2b2tlZGVjLmVudHJ1c3QubmV0MFkwEwYHKoZIzj0C\n" +
            "AQYIKoZIzj0DAQcDQgAEN5MP/59yrs9uwVM/Mrc8IuHonMChAZgN2twwvh8KTnR2\n" +
            "3stfem/R+NtLccq+4ds1+8ktnXgP7u1x0as6IJOH1qOCA4EwggN9MCAGA1UdEQQZ\n" +
            "MBeCFXJldm9rZWRlYy5lbnRydXN0Lm5ldDCCAfcGCisGAQQB1nkCBAIEggHnBIIB\n" +
            "4wHhAHYA7ku9t3XOYLrhQmkfq+GeZqMPfl+wctiDAMR7iXqo/csAAAFcO4iiogAA\n" +
            "BAMARzBFAiAgHVpryyNVgnsUIihu+5DC2/vuP8Cy5iXq8NhCBXg8UgIhAKi5jImT\n" +
            "f1FJksvHboc0EZh9TWhWljVZ6E5jB2CL+qzeAHcAVhQGmi/XwuzT9eG9RLI+x0Z2\n" +
            "ubyZEVzA75SYVdaJ0N0AAAFcO4ij9QAABAMASDBGAiEA4B2p2726ISSkKC9WVlzj\n" +
            "BVwYZ1Hr7mTjPrFqkoGpEHYCIQC5iuInkJXGBANLTH06BHIQkkr4KnFRl9QBOSw4\n" +
            "b+kNqgB1AN3rHSt6DU+mIIuBrYFocH4ujp0B1VyIjT0RxM227L7MAAABXDuIpkcA\n" +
            "AAQDAEYwRAIgQ9ssw19wIhHWW6IWgwnIyB7e30HacBNX6S1eQ3GUX04CICffGj3A\n" +
            "WWmK9lixmk35YklMnSXNqHQezSYRiCYtXxejAHcApLkJkLQYWBSHuxOizGdwCjw1\n" +
            "mAT5G9+443fNDsgN3BAAAAFcO4inUwAABAMASDBGAiEA+8T9tpPw/mU/STsNv0oz\n" +
            "8Nla21fKlpEOyWqDKWPSUeYCIQCwI5tDyyaJtyFY9/OVqLG+BKPKjscUtTqGJYl4\n" +
            "XbOo1jAOBgNVHQ8BAf8EBAMCB4AwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUF\n" +
            "BwMCMGMGCCsGAQUFBwEBBFcwVTAjBggrBgEFBQcwAYYXaHR0cDovL29jc3AuZW50\n" +
            "cnVzdC5uZXQwLgYIKwYBBQUHMAKGImh0dHA6Ly9haWEuZW50cnVzdC5uZXQvbDFq\n" +
            "LWVjMS5jZXIwMwYDVR0fBCwwKjAooCagJIYiaHR0cDovL2NybC5lbnRydXN0Lm5l\n" +
            "dC9sZXZlbDFqLmNybDBKBgNVHSAEQzBBMDYGCmCGSAGG+mwKAQIwKDAmBggrBgEF\n" +
            "BQcCARYaaHR0cDovL3d3dy5lbnRydXN0Lm5ldC9ycGEwBwYFZ4EMAQEwHwYDVR0j\n" +
            "BBgwFoAUw/lFA77I+Qs8RTXz63Ls5+jrlJswHQYDVR0OBBYEFIj28ytR8ulo1p2t\n" +
            "ZnBQOLK0rlLUMAkGA1UdEwQCMAAwCgYIKoZIzj0EAwIDaQAwZgIxANzqGRI0en5P\n" +
            "gSUDcdwoQSNKrBPBfGz2AQVLHAXsxvIlGhKZAQtM49zxA8AdFy/agwIxAMEjJH6A\n" +
            "4UbcGZc40eYu6wUbAxiUDD3gwSElNQ8Z6IhNLPCCdMM6KZORyaagAcXn4A==\n" +
            "-----END CERTIFICATE-----";

    public static void main(String[] args) throws Exception {

        ValidatePathWithParams pathValidator = new ValidatePathWithParams(null);

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            pathValidator.enableCRLCheck();
        } else {
            // OCSP check by default
            pathValidator.enableOCSPCheck();
        }

        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Wed May 24 10:39:28 PDT 2017", System.out);
    }

}
