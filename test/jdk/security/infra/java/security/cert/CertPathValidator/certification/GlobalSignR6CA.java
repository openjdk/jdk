/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8216577
 * @summary Interoperability tests with GlobalSign R6 CA
 * @build ValidatePathWithParams
 * @run main/othervm -Djava.security.debug=certpath GlobalSignR6CA OCSP
 * @run main/othervm -Djava.security.debug=certpath GlobalSignR6CA CRL
 */

 /*
 *
 * Obtain TLS test artifacts for GlobalSign R6 CA from:
 *
 * Valid TLS Certificates:
 * https://valid.r6.roots.globalsign.com/
 *
 * Revoked TLS Certificates:
 * https://revoked.r6.roots.globalsign.com/
 */
public class GlobalSignR6CA {

    // Owner: CN=GlobalSign R6 Admin CA - SHA256 - G3, O=GlobalSign nv-sa, C=BE
    // Issuer: CN=GlobalSign, O=GlobalSign, OU=GlobalSign Root CA - R6
    // Serial number: 48a402ddb5defd50accfc0fcf13f
    // Valid from: Tue Sep 20 17:00:00 PDT 2016 until: Mon Sep 20 17:00:00 PDT 2021
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIFmjCCA4KgAwIBAgIOSKQC3bXe/VCsz8D88T8wDQYJKoZIhvcNAQELBQAwTDEg\n" +
            "MB4GA1UECxMXR2xvYmFsU2lnbiBSb290IENBIC0gUjYxEzARBgNVBAoTCkdsb2Jh\n" +
            "bFNpZ24xEzARBgNVBAMTCkdsb2JhbFNpZ24wHhcNMTYwOTIxMDAwMDAwWhcNMjEw\n" +
            "OTIxMDAwMDAwWjBXMQswCQYDVQQGEwJCRTEZMBcGA1UEChMQR2xvYmFsU2lnbiBu\n" +
            "di1zYTEtMCsGA1UEAxMkR2xvYmFsU2lnbiBSNiBBZG1pbiBDQSAtIFNIQTI1NiAt\n" +
            "IEczMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAmyyfJA4reymawDG1\n" +
            "FNDCSFBqst/+Jih2Zg1ThovSfkxVWcviBhIZfu0t/Hv9hmolN2dxPibKCHhjyfMp\n" +
            "WaGj+S8VPPaR3xoeOvHtuf/2uOyBZa/3mgiWWRF50fLy0fzyWNJL9lbTH459oUci\n" +
            "QN2H0nFEuD1tGGzFdjtXCRVjWy9dZW8Vv2831buzuPLTtOPSKhqOiigpXFTo6SL9\n" +
            "n/NHQ4HI7WV+DMB7yOPEERqQzfi28v1B2j4GOT4wqXncbw5uFZdYobBfRNv3VNdk\n" +
            "p/2Frtm15ePBIAAb4o28du+orJUuVVpxreeEyVBGJuaP0RWksjSnqkSbPm9MEY0k\n" +
            "dS7tgwIDAQABo4IBbTCCAWkwDgYDVR0PAQH/BAQDAgEGMCcGA1UdJQQgMB4GCCsG\n" +
            "AQUFBwMBBggrBgEFBQcDAgYIKwYBBQUHAwkwEgYDVR0TAQH/BAgwBgEB/wIBADAd\n" +
            "BgNVHQ4EFgQUgUlc6QW/DIigOJayXUEDWun/14cwHwYDVR0jBBgwFoAUrmwFo5MT\n" +
            "4qLn4tcc1sfwf8hnU6AwPgYIKwYBBQUHAQEEMjAwMC4GCCsGAQUFBzABhiJodHRw\n" +
            "Oi8vb2NzcDIuZ2xvYmFsc2lnbi5jb20vcm9vdHI2MDYGA1UdHwQvMC0wK6ApoCeG\n" +
            "JWh0dHA6Ly9jcmwuZ2xvYmFsc2lnbi5jb20vcm9vdC1yNi5jcmwwYgYDVR0gBFsw\n" +
            "WTAHBgVngQwBATALBgkrBgEEAaAyAQEwQQYJKwYBBAGgMgFfMDQwMgYIKwYBBQUH\n" +
            "AgEWJmh0dHBzOi8vd3d3Lmdsb2JhbHNpZ24uY29tL3JlcG9zaXRvcnkvMA0GCSqG\n" +
            "SIb3DQEBCwUAA4ICAQBovPHk0rWZ5tGQ3NiYORqZNfSh2KH0RxweRE+ZTpnGOZjE\n" +
            "vRQYLYm/vf2q+v2IcESmpVCjq1eN0k75wc/4475Y9RH6xK7ai1+O8HHDgj8GK4iZ\n" +
            "0ILbKtJQ2/ih19TMO7M3Y/tZByLPcdy8cuDMoCWoQJqUFtM8l784S5lEjefrcwkZ\n" +
            "uNOdTrZbsqXY71Xfa61DNuW3lIt/w34myrKG0xRyGicI9P9VpcWYdWCKpwVe10MP\n" +
            "d4WQ/lclJZLrLljmn76bc+q/L2Sw+tpadsD2qP3l05FhRqcF5iI9lIw77KIU15Jt\n" +
            "QysmI7xTjByjny/OiIYP/7PKQjh+KEe/17GOg0AamdI9dbaOHRcyHFht01ymaphf\n" +
            "kU3hjWb2bdtVLuDsIKfGN/QDXSmv0ThKsgkj3OOiLUpllApr5SU2tY40rpZ210iD\n" +
            "/jA18LYwBmR64t3e7ud/tDz4c/YLY8p6vPLdASbbwyptj93n0c0HXpjdcrx/XOQa\n" +
            "ogw6JzJ2v3Kok94frBKKdoxg4SnMvZoakM1SbY6Q3XlC24qVnVuWJ142rVkCFixZ\n" +
            "Sb5ZEB7fxk/2YfaWkSW3uejwh2qN7qXji0S1ALNbASJATYqMgdJVz+25yOBfxFN6\n" +
            "KzNbvmVmEM/hnKaQxePhwForQjDFaep1RO5Yg4wnIcLRC3atKgkIIA6YDNUcog==\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=valid.r6.roots.globalsign.com, O=GMO GlobalSign Inc., STREET="Two International Drive, Suite 150",
    // L=Portsmouth, ST=New Hampshire, C=US, OID.1.3.6.1.4.1.311.60.2.1.2=New Hampshire, OID.1.3.6.1.4.1.311.60.2.1.3=US,
    // SERIALNUMBER=578611, OID.2.5.4.15=Private Organization
    // Issuer: CN=GlobalSign R6 Admin CA - SHA256 - G3, O=GlobalSign nv-sa, C=BE
    // Serial number: 1355071ec648a599cea67b3b
    // Valid from: Wed Jun 13 21:31:05 PDT 2018 until: Sat Jun 13 21:31:05 PDT 2020
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIHUjCCBjqgAwIBAgIME1UHHsZIpZnOpns7MA0GCSqGSIb3DQEBCwUAMFcxCzAJ\n" +
            "BgNVBAYTAkJFMRkwFwYDVQQKExBHbG9iYWxTaWduIG52LXNhMS0wKwYDVQQDEyRH\n" +
            "bG9iYWxTaWduIFI2IEFkbWluIENBIC0gU0hBMjU2IC0gRzMwHhcNMTgwNjE0MDQz\n" +
            "MTA1WhcNMjAwNjE0MDQzMTA1WjCCARIxHTAbBgNVBA8MFFByaXZhdGUgT3JnYW5p\n" +
            "emF0aW9uMQ8wDQYDVQQFEwY1Nzg2MTExEzARBgsrBgEEAYI3PAIBAxMCVVMxHjAc\n" +
            "BgsrBgEEAYI3PAIBAhMNTmV3IEhhbXBzaGlyZTELMAkGA1UEBhMCVVMxFjAUBgNV\n" +
            "BAgTDU5ldyBIYW1wc2hpcmUxEzARBgNVBAcTClBvcnRzbW91dGgxKzApBgNVBAkT\n" +
            "IlR3byBJbnRlcm5hdGlvbmFsIERyaXZlLCBTdWl0ZSAxNTAxHDAaBgNVBAoTE0dN\n" +
            "TyBHbG9iYWxTaWduIEluYy4xJjAkBgNVBAMTHXZhbGlkLnI2LnJvb3RzLmdsb2Jh\n" +
            "bHNpZ24uY29tMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArh1lHcNS\n" +
            "cfvFI/vGrfu3sC561NL6VTm9WQpq0UcdQpVlOjnmlScZaUhTlcJ2aWz4tqNnT/SF\n" +
            "EO48kgIy0c07n0z1igBGOvM6shPtdIT3Yik2KwKdnt2Oaw/RqyXQxZhMvvGGyXLP\n" +
            "hEyRdUrcNEXzOh+/AFzV2Ayo2OfZB/SEJW2BMhYEvZ89ziniab7vaNfVVUwsR6yD\n" +
            "JX/3bdgRpG3gvKpdawAXMkhX5yAJaLInp5gHfCKNsW7l5gSrW/IYmPZvmEovLLmF\n" +
            "lJfEDltnaNrO3jFzCjzEVRsurBrn1lMgKuCCkCZhzUgy5w8fR7OiGDpI/DmprRxn\n" +
            "WQomtZBRd9VG1wIDAQABo4IDXzCCA1swDgYDVR0PAQH/BAQDAgWgMIGWBggrBgEF\n" +
            "BQcBAQSBiTCBhjBHBggrBgEFBQcwAoY7aHR0cDovL3NlY3VyZS5nbG9iYWxzaWdu\n" +
            "LmNvbS9jYWNlcnQvZ3NyNmFkbWluY2FzaGEyNTZnMy5jcnQwOwYIKwYBBQUHMAGG\n" +
            "L2h0dHA6Ly9vY3NwMi5nbG9iYWxzaWduLmNvbS9nc3I2YWRtaW5jYXNoYTI1Nmcz\n" +
            "MFUGA1UdIAROMEwwQQYJKwYBBAGgMgEBMDQwMgYIKwYBBQUHAgEWJmh0dHBzOi8v\n" +
            "d3d3Lmdsb2JhbHNpZ24uY29tL3JlcG9zaXRvcnkvMAcGBWeBDAEBMAkGA1UdEwQC\n" +
            "MAAwQgYDVR0fBDswOTA3oDWgM4YxaHR0cDovL2NybC5nbG9iYWxzaWduLmNvbS9n\n" +
            "c3I2YWRtaW5jYXNoYTI1NmczLmNybDAoBgNVHREEITAfgh12YWxpZC5yNi5yb290\n" +
            "cy5nbG9iYWxzaWduLmNvbTAdBgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIw\n" +
            "HQYDVR0OBBYEFPTkCvZs787YEtziawL5ju/rC8XwMB8GA1UdIwQYMBaAFIFJXOkF\n" +
            "vwyIoDiWsl1BA1rp/9eHMIIBfwYKKwYBBAHWeQIEAgSCAW8EggFrAWkAdwBVgdTC\n" +
            "FpA2AUrqC5tXPFPwwOQ4eHAlCBcvo6odBxPTDAAAAWP8j7bvAAAEAwBIMEYCIQDH\n" +
            "FRH+VkQ4RgVRYaO47rC83fQrzEO9Pb45BD5ZEHfrRwIhALY75BbrPhtAZSXWfpVN\n" +
            "MoDQzA6X0DQFSf29dlnCMYCmAHcApLkJkLQYWBSHuxOizGdwCjw1mAT5G9+443fN\n" +
            "DsgN3BAAAAFj/I+4QgAABAMASDBGAiEA3kcOlf4Az7R+/MkV5GurWnpUmIhCUB3v\n" +
            "a/tNz+Dd8HgCIQC22RG+EW4OYdaoWN/B3MeI95OlNofD/OqJB/med+quWwB1AG9T\n" +
            "dqwx8DEZ2JkApFEV/3cVHBHZAsEAKQaNsgiaN9kTAAABY/yPt6kAAAQDAEYwRAIg\n" +
            "THH7eeWpo5vDtjDNKzpkkrR/McYDgmQIRRnLKXkKMsoCIC9cY4xj9LlXPVRF9bLH\n" +
            "1DvP9qmONga9pO7kxuyYtd8YMA0GCSqGSIb3DQEBCwUAA4IBAQA0Ufq4QDCiWxm4\n" +
            "5D3MrfbQnC9apSMpzRT2udD/gFDbtqTJ7Rx4CJjNWa9ANkKWNlJ6zVASpVzV7KB7\n" +
            "otvqO4iR5V0EE4+9fitJ3zRe9nl76uDf2upCHLcWsYurq/eIxIuXnIByLJvTS3jS\n" +
            "42i07D6JsgNg9SR8rIKyYiz4KX2975GlMSue/SOMFcf/AC7amYzs6U+FA68y8GBV\n" +
            "yDGpYvQW9zfnQ2Z/XVcLE1tVERrEs3Ba08g+uk1dICyibSz83yrX3Eas/bq6kZEy\n" +
            "kRvhD1fnk3wAlgiuUED65Rn3ezm2AjsFJBIitdDyHFzgZiu/DKccakuuk8NwDZjJ\n" +
            "NrTZIL32\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=revoked.r6.roots.globalsign.com, O=GMO GlobalSign Inc., STREET="Two International Drive, Suite 150",
    // L=Portsmouth, ST=New Hampshire, C=US, OID.1.3.6.1.4.1.311.60.2.1.2=New Hampshire, OID.1.3.6.1.4.1.311.60.2.1.3=US,
    // SERIALNUMBER=578611, OID.2.5.4.15=Private Organization
    // Issuer: CN=GlobalSign R6 Admin CA - SHA256 - G3, O=GlobalSign nv-sa, C=BE
    // Serial number: 535589c9d767cf1cd892f1dc
    // Valid from: Wed Jun 13 21:36:04 PDT 2018 until: Sat Jun 13 21:36:04 PDT 2020
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIHVTCCBj2gAwIBAgIMU1WJyddnzxzYkvHcMA0GCSqGSIb3DQEBCwUAMFcxCzAJ\n" +
            "BgNVBAYTAkJFMRkwFwYDVQQKExBHbG9iYWxTaWduIG52LXNhMS0wKwYDVQQDEyRH\n" +
            "bG9iYWxTaWduIFI2IEFkbWluIENBIC0gU0hBMjU2IC0gRzMwHhcNMTgwNjE0MDQz\n" +
            "NjA0WhcNMjAwNjE0MDQzNjA0WjCCARQxHTAbBgNVBA8MFFByaXZhdGUgT3JnYW5p\n" +
            "emF0aW9uMQ8wDQYDVQQFEwY1Nzg2MTExEzARBgsrBgEEAYI3PAIBAxMCVVMxHjAc\n" +
            "BgsrBgEEAYI3PAIBAhMNTmV3IEhhbXBzaGlyZTELMAkGA1UEBhMCVVMxFjAUBgNV\n" +
            "BAgTDU5ldyBIYW1wc2hpcmUxEzARBgNVBAcTClBvcnRzbW91dGgxKzApBgNVBAkT\n" +
            "IlR3byBJbnRlcm5hdGlvbmFsIERyaXZlLCBTdWl0ZSAxNTAxHDAaBgNVBAoTE0dN\n" +
            "TyBHbG9iYWxTaWduIEluYy4xKDAmBgNVBAMTH3Jldm9rZWQucjYucm9vdHMuZ2xv\n" +
            "YmFsc2lnbi5jb20wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQC6SJ+O\n" +
            "PX5/ECfblZpVByiogO5sUCS23Sry3Ucn1fxFO3b6tOKppUtgZjJUxUOHj9jRIsmS\n" +
            "8Tvbn+Iu35Cjj2vTsJNoFzxiMj/FBl3IqfF7w4ghLNZ+wE91cMwG0LUtDeAKTlJa\n" +
            "j4Q2Gj1ZOGLPyr4flSig2bOvcIBWYjbXqwBMZek9EC58D34HF+h2fdzXPrqHHWqg\n" +
            "NQpj7lxkr4XA1jXSgZJZnRfoVW+BCVidbNw9LEteF+WGcg3P9sd8XUWJtG/pb4w1\n" +
            "GsCMf/ig8gkrsQvrMYPsYgQJMdypXm9eAqZmVcE94E0Uz1dbJL9zCa8y4ue9yDnp\n" +
            "+gzXxToJvNzrlmUPAgMBAAGjggNgMIIDXDAOBgNVHQ8BAf8EBAMCBaAwgZYGCCsG\n" +
            "AQUFBwEBBIGJMIGGMEcGCCsGAQUFBzAChjtodHRwOi8vc2VjdXJlLmdsb2JhbHNp\n" +
            "Z24uY29tL2NhY2VydC9nc3I2YWRtaW5jYXNoYTI1NmczLmNydDA7BggrBgEFBQcw\n" +
            "AYYvaHR0cDovL29jc3AyLmdsb2JhbHNpZ24uY29tL2dzcjZhZG1pbmNhc2hhMjU2\n" +
            "ZzMwVQYDVR0gBE4wTDBBBgkrBgEEAaAyAQEwNDAyBggrBgEFBQcCARYmaHR0cHM6\n" +
            "Ly93d3cuZ2xvYmFsc2lnbi5jb20vcmVwb3NpdG9yeS8wBwYFZ4EMAQEwCQYDVR0T\n" +
            "BAIwADBCBgNVHR8EOzA5MDegNaAzhjFodHRwOi8vY3JsLmdsb2JhbHNpZ24uY29t\n" +
            "L2dzcjZhZG1pbmNhc2hhMjU2ZzMuY3JsMCoGA1UdEQQjMCGCH3Jldm9rZWQucjYu\n" +
            "cm9vdHMuZ2xvYmFsc2lnbi5jb20wHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUF\n" +
            "BwMCMB0GA1UdDgQWBBR66TcwHJ5KRJZqtNB3Cqj8rWUAYzAfBgNVHSMEGDAWgBSB\n" +
            "SVzpBb8MiKA4lrJdQQNa6f/XhzCCAX4GCisGAQQB1nkCBAIEggFuBIIBagFoAHYA\n" +
            "VYHUwhaQNgFK6gubVzxT8MDkOHhwJQgXL6OqHQcT0wwAAAFj/JRH/gAABAMARzBF\n" +
            "AiBtxn2bgwXrjx2zX3RPP3L4iFEZ1bK71oZ67RvNpI/pWQIhAK1Wg3wEdSqUUa9I\n" +
            "VKSNaDaMqtI7s5yQvIV3YdDDxl+hAHcAu9nfvB+KcbWTlCOXqpJ7RzhXlQqrUuga\n" +
            "kJZkNo4e0YUAAAFj/JRJMQAABAMASDBGAiEAkwpftFhujb0p9wNDywVgZPPxGdLy\n" +
            "7c7WnpBLkViuvVgCIQCtWUK5pfYn+FWPKX82XmG0Hw1VgeQRPZZNAy0HQu/V0QB1\n" +
            "AG9Tdqwx8DEZ2JkApFEV/3cVHBHZAsEAKQaNsgiaN9kTAAABY/yUSPUAAAQDAEYw\n" +
            "RAIgEN2Y70rpA+zoK1C5bKEOYUDy6Km5pgymDEPcMBgmh5ECIEAWEPdNA9FeCwqW\n" +
            "S1Mi3uOhB4dmJKNbToFWtL2lBeDrMA0GCSqGSIb3DQEBCwUAA4IBAQCDoIyqZlvt\n" +
            "YeqjVCR2rvb1ZHyB5UI5rfYuoNstjaxLKP2tIDByeGwllT0vSb2otM6XjXGVuTTO\n" +
            "sbVUf4aQQb82pkKXYtB6L7cfPkqrnZXJrmPYb+3xzAsr+HXyyPOu0FIVrtB/WTvd\n" +
            "Qo/JyVMm7Duke/e5gudw9Lv6sb2P5B3BVcNzbv1f7589wydNvrTgdVeldyPNfuZ4\n" +
            "gMT/ICoNaX+U6O3EiqYB+gLDBKVAIDsQV1k/fYq5uZr1FsTzOMesaCT4me/4I4tR\n" +
            "2H7WrVajYEJ73gWUclDLxy7hoDNwR/ZuLcilAaqdwIdmVD0aFiw8RFsyZkXO5J0R\n" +
            "BuecWspICLIw\n" +
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
                "Wed Jun 13 23:36:02 PDT 2018", System.out);

    }
}

