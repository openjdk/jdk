/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8318759
 * @summary Interoperability tests with Digicert CS Root G5 certificates
 * @build ValidatePathWithParams
 * @run main/othervm/manual -Djava.security.debug=ocsp,certpath DigicertCSRootG5 OCSP
 * @run main/othervm/manual -Djava.security.debug=certpath DigicertCSRootG5 CRL
 */

public class DigicertCSRootG5 {

    public static void main(String[] args) throws Exception {

        ValidatePathWithParams pathValidator = new ValidatePathWithParams(null);

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            pathValidator.enableCRLCheck();
        } else {
            // OCSP check by default
            pathValidator.enableOCSPCheck();
        }

        new Digicert_CS_ECC().runTest(pathValidator);
        new Digicert_CS_RSA().runTest(pathValidator);
    }
}

class Digicert_CS_ECC {

    // Owner: CN=DigiCert G5 CS ECC SHA384 2021 CA1, O="DigiCert, Inc.", C=US
    // Issuer: CN=DigiCert CS ECC P384 Root G5, O="DigiCert, Inc.", C=US
    // Serial number: d926818addd3c47758f0ace9379b2e7
    // Valid from: Wed Feb 10 16:00:00 PST 2021 until: Sun Feb 10 15:59:59 PST 2036
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIDOTCCAsCgAwIBAgIQDZJoGK3dPEd1jwrOk3my5zAKBggqhkjOPQQDAzBNMQsw\n" +
            "CQYDVQQGEwJVUzEXMBUGA1UEChMORGlnaUNlcnQsIEluYy4xJTAjBgNVBAMTHERp\n" +
            "Z2lDZXJ0IENTIEVDQyBQMzg0IFJvb3QgRzUwHhcNMjEwMjExMDAwMDAwWhcNMzYw\n" +
            "MjEwMjM1OTU5WjBTMQswCQYDVQQGEwJVUzEXMBUGA1UEChMORGlnaUNlcnQsIElu\n" +
            "Yy4xKzApBgNVBAMTIkRpZ2lDZXJ0IEc1IENTIEVDQyBTSEEzODQgMjAyMSBDQTEw\n" +
            "djAQBgcqhkjOPQIBBgUrgQQAIgNiAAS/zvKH4sLLu/zze3/+vHyfRE5OcO77TNw3\n" +
            "MCMAlad2Y/ja50KTooGSmXhfwMXpbBTob7hsoxpvIU92W6DhFn9lg4pcKf5UHLEi\n" +
            "0iDdHQ9w0hpFJiMABwK60nk+OwsGTZSjggFdMIIBWTASBgNVHRMBAf8ECDAGAQH/\n" +
            "AgEAMB0GA1UdDgQWBBTXHcf6xvqCdCBFcTQSL1XVmEGSXjAfBgNVHSMEGDAWgBTw\n" +
            "jJhxOThlwjobphdmHcjtZd6SNjAOBgNVHQ8BAf8EBAMCAYYwEwYDVR0lBAwwCgYI\n" +
            "KwYBBQUHAwMweQYIKwYBBQUHAQEEbTBrMCQGCCsGAQUFBzABhhhodHRwOi8vb2Nz\n" +
            "cC5kaWdpY2VydC5jb20wQwYIKwYBBQUHMAKGN2h0dHA6Ly9jYWNlcnRzLmRpZ2lj\n" +
            "ZXJ0LmNvbS9EaWdpQ2VydENTRUNDUDM4NFJvb3RHNS5jcnQwRQYDVR0fBD4wPDA6\n" +
            "oDigNoY0aHR0cDovL2NybDMuZGlnaWNlcnQuY29tL0RpZ2lDZXJ0Q1NFQ0NQMzg0\n" +
            "Um9vdEc1LmNybDAcBgNVHSAEFTATMAcGBWeBDAEDMAgGBmeBDAEEATAKBggqhkjO\n" +
            "PQQDAwNnADBkAjByCWijRCnJogZf94U5HG/5S4QFMxEOBSAyxECbFxgrXMKXh5qa\n" +
            "7oS2F+hT2DPzxTwCMCIthK0X/14bxZvrNNiNSWzer2TDUyRw6HNIfnkHgqaGFQVA\n" +
            "KyS5I77prv53stK0XQ==\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN="Win The Customer, LLC", O="Win The Customer, LLC", L=Saratoga
    // Springs, ST=Utah, C=US, SERIALNUMBER=9637546-0160, OID.2.5.4.15=Private
    // Organization, OID.1.3.6.1.4.1.311.60.2.1.2=Utah, OID.1.3.6.1.4.1.311.60.2.1.3=US
    // Issuer: CN=DigiCert G5 CS ECC SHA384 2021 CA1, O="DigiCert, Inc.", C=US
    // Serial number: b13737c3caf58eecb4359f441522133
    // Valid from: Wed Jan 25 16:00:00 PST 2023 until: Tue Jan 28 15:59:59 PST 2025
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIEEjCCA5mgAwIBAgIQCxNzfDyvWO7LQ1n0QVIhMzAKBggqhkjOPQQDAzBTMQsw\n" +
            "CQYDVQQGEwJVUzEXMBUGA1UEChMORGlnaUNlcnQsIEluYy4xKzApBgNVBAMTIkRp\n" +
            "Z2lDZXJ0IEc1IENTIEVDQyBTSEEzODQgMjAyMSBDQTEwHhcNMjMwMTI2MDAwMDAw\n" +
            "WhcNMjUwMTI4MjM1OTU5WjCB2TETMBEGCysGAQQBgjc8AgEDEwJVUzEVMBMGCysG\n" +
            "AQQBgjc8AgECEwRVdGFoMR0wGwYDVQQPDBRQcml2YXRlIE9yZ2FuaXphdGlvbjEV\n" +
            "MBMGA1UEBRMMOTYzNzU0Ni0wMTYwMQswCQYDVQQGEwJVUzENMAsGA1UECBMEVXRh\n" +
            "aDEZMBcGA1UEBxMQU2FyYXRvZ2EgU3ByaW5nczEeMBwGA1UEChMVV2luIFRoZSBD\n" +
            "dXN0b21lciwgTExDMR4wHAYDVQQDExVXaW4gVGhlIEN1c3RvbWVyLCBMTEMwWTAT\n" +
            "BgcqhkjOPQIBBggqhkjOPQMBBwNCAASyShgaH44RcHazlEEMpwRKY4YebnygI9hG\n" +
            "wTMQE/VFG40k3tR8lnyjgxTzZbC0aCVavdv1eglDGejQ+6iD8nzgo4IBxjCCAcIw\n" +
            "HwYDVR0jBBgwFoAU1x3H+sb6gnQgRXE0Ei9V1ZhBkl4wHQYDVR0OBBYEFLGgEWb9\n" +
            "GF89JoXyan/FD/auNIVVMA4GA1UdDwEB/wQEAwIHgDATBgNVHSUEDDAKBggrBgEF\n" +
            "BQcDAzCBjQYDVR0fBIGFMIGCMD+gPaA7hjlodHRwOi8vY3JsMy5kaWdpY2VydC5j\n" +
            "b20vRGlnaUNlcnRHNUNTRUNDU0hBMzg0MjAyMUNBMS5jcmwwP6A9oDuGOWh0dHA6\n" +
            "Ly9jcmw0LmRpZ2ljZXJ0LmNvbS9EaWdpQ2VydEc1Q1NFQ0NTSEEzODQyMDIxQ0Ex\n" +
            "LmNybDA9BgNVHSAENjA0MDIGBWeBDAEDMCkwJwYIKwYBBQUHAgEWG2h0dHA6Ly93\n" +
            "d3cuZGlnaWNlcnQuY29tL0NQUzB+BggrBgEFBQcBAQRyMHAwJAYIKwYBBQUHMAGG\n" +
            "GGh0dHA6Ly9vY3NwLmRpZ2ljZXJ0LmNvbTBIBggrBgEFBQcwAoY8aHR0cDovL2Nh\n" +
            "Y2VydHMuZGlnaWNlcnQuY29tL0RpZ2lDZXJ0RzVDU0VDQ1NIQTM4NDIwMjFDQTEu\n" +
            "Y3J0MAwGA1UdEwEB/wQCMAAwCgYIKoZIzj0EAwMDZwAwZAIwLkWJc/eLxftorFCv\n" +
            "ocOA1dfUFx7Al18d5Xsgpkx47kj2DWgQU+/bQEbbyPrKzYgCAjAP5ErLauJRC2to\n" +
            "pPk/yXZYXsusmWVH7ozl9O5WR7+a3gVQ1zwVFWuqdjbq3zWWqJM=\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=Win the Customer LLC, O=Win the Customer LLC, L=Saratoga Springs, ST=Utah, C=US
    // Issuer: CN=DigiCert G5 CS ECC SHA384 2021 CA1, O="DigiCert, Inc.", C=US
    // Serial number: 201e51cb1ec8a56a1e8438c95adf024
    // Valid from: Sun Oct 22 17:00:00 PDT 2023 until: Tue Oct 22 16:59:59 PDT 2024
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIFdjCCBP2gAwIBAgIQAgHlHLHsilah6EOMla3wJDAKBggqhkjOPQQDAzBTMQsw\n" +
            "CQYDVQQGEwJVUzEXMBUGA1UEChMORGlnaUNlcnQsIEluYy4xKzApBgNVBAMTIkRp\n" +
            "Z2lDZXJ0IEc1IENTIEVDQyBTSEEzODQgMjAyMSBDQTEwHhcNMjMxMDIzMDAwMDAw\n" +
            "WhcNMjQxMDIyMjM1OTU5WjB1MQswCQYDVQQGEwJVUzENMAsGA1UECBMEVXRhaDEZ\n" +
            "MBcGA1UEBxMQU2FyYXRvZ2EgU3ByaW5nczEdMBsGA1UEChMUV2luIHRoZSBDdXN0\n" +
            "b21lciBMTEMxHTAbBgNVBAMTFFdpbiB0aGUgQ3VzdG9tZXIgTExDMIICIjANBgkq\n" +
            "hkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA0o+FWNSfYzJmz+XgA7SRAIQd1H1pYnzq\n" +
            "dPyNJJsd1G/nqfeHk/ezEx8Wd7iMJjcPOvKSd14uniAC3ayi3XOKKeFqEw5g5m2/\n" +
            "JTO3n8xy9DK5CN1ctpK5Zy+UppOXrtTdBZB74/qSaREOysIfRLnVR4fxNy39urtl\n" +
            "TJf0lvzRU9V6BQ3zRjMOCQnY6sueAPoQpVgpCVXkr4obJCkI5arkIQHVpfrcScaJ\n" +
            "IzLQ46xL8nxoXPcGhikRystJKdbzg/oCFt68x87uSviZMtkqTHQhzRCzpO5pdx/z\n" +
            "g64XZP8fAzSrM/uJCETXxMmazK6ZVkgPu3X4GvjfTfulvcJdxZNMm877NOSICtbL\n" +
            "dKoBpvIeKtuyxrvmoJUfNw4e+LLbAQOFznVy7UxkTzG1INPgd57zu3Sm3ALq/oJZ\n" +
            "oKfheM4zo8UevYMKmoki+N+qMHcJplPF8C04/u8CNc1Jk8tKmjgof8ZsGbQCC2+l\n" +
            "NKXzTUnPpza4mHBMU3Qdd4iV8oxd/9jQyE71h11ISakWSresbCyC6HSOVUh409A1\n" +
            "Mhv9+aEbqBNhAHJIYrQSY1hb98CKLRS6cABKAzr+HdafiPCAN3cdLGgJ5TWTIiBj\n" +
            "AcjyHseVU4jeLIQl7/4gZATjePzSy/bo62SZXWzCOFp6zzy8VGGavRmMobe193gn\n" +
            "cz/17hmFvqECAwEAAaOCAcQwggHAMB8GA1UdIwQYMBaAFNcdx/rG+oJ0IEVxNBIv\n" +
            "VdWYQZJeMB0GA1UdDgQWBBR5Hkdl3jgG88ixGc1wEwO6N9Rn2TA+BgNVHSAENzA1\n" +
            "MDMGBmeBDAEEATApMCcGCCsGAQUFBwIBFhtodHRwOi8vd3d3LmRpZ2ljZXJ0LmNv\n" +
            "bS9DUFMwDgYDVR0PAQH/BAQDAgeAMBMGA1UdJQQMMAoGCCsGAQUFBwMDMIGNBgNV\n" +
            "HR8EgYUwgYIwP6A9oDuGOWh0dHA6Ly9jcmwzLmRpZ2ljZXJ0LmNvbS9EaWdpQ2Vy\n" +
            "dEc1Q1NFQ0NTSEEzODQyMDIxQ0ExLmNybDA/oD2gO4Y5aHR0cDovL2NybDQuZGln\n" +
            "aWNlcnQuY29tL0RpZ2lDZXJ0RzVDU0VDQ1NIQTM4NDIwMjFDQTEuY3JsMH4GCCsG\n" +
            "AQUFBwEBBHIwcDAkBggrBgEFBQcwAYYYaHR0cDovL29jc3AuZGlnaWNlcnQuY29t\n" +
            "MEgGCCsGAQUFBzAChjxodHRwOi8vY2FjZXJ0cy5kaWdpY2VydC5jb20vRGlnaUNl\n" +
            "cnRHNUNTRUNDU0hBMzg0MjAyMUNBMS5jcnQwCQYDVR0TBAIwADAKBggqhkjOPQQD\n" +
            "AwNnADBkAjA9aX3CSzCOZiHdC6JBF0nQwPLGNipPdHFMSbINmfpuHCC3Go4prf8M\n" +
            "WCsWEQr2gQYCMErfcrU8zfxnQ9SxsmGJ8jkM3MDGvAr0CtzDwmWis32V60jAUFBQ\n" +
            "lGm/Mdb5/EqKpw==\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator) throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Mon Oct 23 14:48:38 PDT 2023", System.out);
    }
}

class Digicert_CS_RSA {

    // Owner: CN=DigiCert G5 CS RSA4096 SHA384 2021 CA1, O="DigiCert, Inc.", C=US
    // Issuer: CN=DigiCert CS RSA4096 Root G5, O="DigiCert, Inc.", C=US
    // Serial number: 10262e16224ca6dfef584f8c63048db
    // Valid from: Wed Feb 10 16:00:00 PST 2021 until: Sun Feb 10 15:59:59 PST 2036
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGjDCCBHSgAwIBAgIQAQJi4WIkym3+9YT4xjBI2zANBgkqhkiG9w0BAQwFADBM\n" +
            "MQswCQYDVQQGEwJVUzEXMBUGA1UEChMORGlnaUNlcnQsIEluYy4xJDAiBgNVBAMT\n" +
            "G0RpZ2lDZXJ0IENTIFJTQTQwOTYgUm9vdCBHNTAeFw0yMTAyMTEwMDAwMDBaFw0z\n" +
            "NjAyMTAyMzU5NTlaMFcxCzAJBgNVBAYTAlVTMRcwFQYDVQQKEw5EaWdpQ2VydCwg\n" +
            "SW5jLjEvMC0GA1UEAxMmRGlnaUNlcnQgRzUgQ1MgUlNBNDA5NiBTSEEzODQgMjAy\n" +
            "MSBDQTEwggIiMA0GCSqGSIb3DQEBAQUAA4ICDwAwggIKAoICAQC1GOMV0tdTLLBk\n" +
            "Ylmccgb6bFa9By5zkuLg9NfFMl4y9P9f21C7N+mMA4fWgfjEs+7/3ByGLaB+7/Pi\n" +
            "TT3qXpvBz4uVWob9xv3lkAsIpwh/TMJulijy3GdpAQBMdvW/+HFrbRJGaJ3MM9d1\n" +
            "pC3CRPmFWyXUpxqhb0FbMPA8OlsZNjg9fd/zCLevSJlL6ZdjfZ/4FiF26OfO60V6\n" +
            "bOuTnd8JbDuwPfMWLP6qEinlFr7V9mjcZc4dfUWH70y7M6av7R1Tc68YQjrtPwIA\n" +
            "5pdEcG/VeBVplpne1uxuc61ucVgTpjwOTV6E2KrCe+OCG8/m4voN7T4GC1RfPH3n\n" +
            "PlCNV6MeiCVwExPhJFxZ+eTvhVJe0W7mriYpEo2kNR4pnSUhiS92vF4lI3ToAdnH\n" +
            "LV+yx0VdsPVwEO344rsVNQvP/hrCHefKm3HsirlazTKpiI9OgZlkXohHanp8IgMx\n" +
            "2HvBE/6HcCq/5PiRaeSzvFfRuotLS/LMCXaQEGV9JNSd1omKeNyaDqs89cNbf0g7\n" +
            "Tn1AhAxb/TDIkIAV/1bU1UFeq48ufRCRpPO145JQXL7hfdUIth3AkvFRqLPbTsCH\n" +
            "v/PcnKScv/QCtoYRnYv5LwdIvYblC+yqe7a9CVARsaVsGBw45wBevcMR5fcdriET\n" +
            "ZjRNmQ5gMBjm/ZlHlzyBgShH6U22TQIDAQABo4IBXTCCAVkwEgYDVR0TAQH/BAgw\n" +
            "BgEB/wIBADAdBgNVHQ4EFgQUiRgH/z5tMBfJNa27i3GG5Z9mksMwHwYDVR0jBBgw\n" +
            "FoAUaAGTsdJKQEJplEYsHFqIqSW0R08wDgYDVR0PAQH/BAQDAgGGMBMGA1UdJQQM\n" +
            "MAoGCCsGAQUFBwMDMHkGCCsGAQUFBwEBBG0wazAkBggrBgEFBQcwAYYYaHR0cDov\n" +
            "L29jc3AuZGlnaWNlcnQuY29tMEMGCCsGAQUFBzAChjdodHRwOi8vY2FjZXJ0cy5k\n" +
            "aWdpY2VydC5jb20vRGlnaUNlcnRDU1JTQTQwOTZSb290RzUuY3J0MEUGA1UdHwQ+\n" +
            "MDwwOqA4oDaGNGh0dHA6Ly9jcmwzLmRpZ2ljZXJ0LmNvbS9EaWdpQ2VydENTUlNB\n" +
            "NDA5NlJvb3RHNS5jcmwwHAYDVR0gBBUwEzAHBgVngQwBAzAIBgZngQwBBAEwDQYJ\n" +
            "KoZIhvcNAQEMBQADggIBALBxItkM8LmHhbsnkykSN6+HnLj9/hUx9UUcym1Hwoii\n" +
            "Bl9VCCpibLDJurx1w19KL5S6j2ggOMn/1zBugWMVhn6j12RzD4HUkfLqNBXzQmRc\n" +
            "xZoXxspSgqpk+jd5iMtVSDBzlaF7s1feDh9qKa7O/7OB5KAiIO2VYFx1ia9ne3tV\n" +
            "lY98G+3TnEdjo7r9lBi4KDGmDJv56h7Sb4WeVFlJ/8b4u9IHblq3ykQ/LyKuCYDf\n" +
            "v2bnqlT+HY4mgU9ZA0WoO/L7V7m0sBrBYhpdM0pmxlqn6mpvWIHA2tC4rsTY2TXn\n" +
            "ZlXbyJaMd5mvjRjvK0DF/2yoKC+us/1li2blLZKS9k0Z36/m4D7z5nVXkmUvRvE2\n" +
            "70BhJ0NnM5lHtytTR+OgiaPapeiDy6AA+VbdnV7hhINGEhP7tF3IZPPfmKZN7/bN\n" +
            "Qr7wuKZx/jO5sTBtblBaOU2+xric+MlTt2k3ilDnO3EzkZOp1JMWnNjAZciRa8Gy\n" +
            "bYAXrsxY4vQnxgA7dj1/3KDB+pCRT7CTMOJJQu27OOv0MuNkb1E+8chPx/eFwfrN\n" +
            "rft1Eiqp3Te0w4njDkzukP6EMhebcTp3POm0YhMZl8s1fTI6DCcHFwcMVywXiWwv\n" +
            "QG+Td+dHlFT0P8jq/ecaMj6s8j69q36MER+QMyrxGAl3MHyEA7BBut1WCh9dsOnY\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN="Win The Customer, LLC", O="Win The Customer, LLC", L=Saratoga
    // Springs, ST=Utah, C=US
    // Issuer: CN=DigiCert G5 CS RSA4096 SHA384 2021 CA1, O="DigiCert, Inc.", C=US
    // Serial number: bfec2fd49eeacb347ddbea5c1576083
    // Valid from: Fri Jun 23 17:00:00 PDT 2023 until: Wed Jun 26 16:59:59 PDT 2024
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGqzCCBJOgAwIBAgIQC/7C/UnurLNH3b6lwVdggzANBgkqhkiG9w0BAQsFADBX" +
            "MQswCQYDVQQGEwJVUzEXMBUGA1UEChMORGlnaUNlcnQsIEluYy4xLzAtBgNVBAMT" +
            "JkRpZ2lDZXJ0IEc1IENTIFJTQTQwOTYgU0hBMzg0IDIwMjEgQ0ExMB4XDTIzMDYy" +
            "NDAwMDAwMFoXDTI0MDYyNjIzNTk1OVowdzELMAkGA1UEBhMCVVMxDTALBgNVBAgT" +
            "BFV0YWgxGTAXBgNVBAcTEFNhcmF0b2dhIFNwcmluZ3MxHjAcBgNVBAoTFVdpbiBU" +
            "aGUgQ3VzdG9tZXIsIExMQzEeMBwGA1UEAxMVV2luIFRoZSBDdXN0b21lciwgTExD" +
            "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAsElsbtoNNIL5fCadUzW+" +
            "aDl2LF0c6BRckZSuH1f88tFD5LDjuT+rdIxsjDS/dqedRiilJe40z/3973OZNaxs" +
            "wxYCSHhUV9XimSHH0zQ2MpbupdA7aLDYM4tcypam1Zm9q6njLArBUgGVaKYBUZqW" +
            "obVh+6aFBzj36u7EmPgLCJsre5oheo8+gOwfu+xVExceoHG+V7XTKhD6vhclS49B" +
            "UIHgvpn+/BlB8kjf5M2XzmpfWg9aGq75gnd1ix4fU1BnA0A33cZPrFsi5cMh6NZd" +
            "tI4WIpb5P8X17G3yRqNMM/noBvBrtpQHVLpN2C2NLg0YX1FjIK7bcBKFOnIG36ou" +
            "vs+QesMyVOXeKKnt1ERBSqwrMjUuqN7W6YnXjoIp7xWxEdIdae+1fDK702zhGaYv" +
            "b6pYGoJ7HQI/x7S6kF462qvXsf++yA5kxr2qNTSNY4ZggzEwubvu0PYRYjMHwIUn" +
            "SV3ZlRAKXK2AO7GydecWr2QVRra4+myCznsil/rKasWTAgMBAAGjggHRMIIBzTAf" +
            "BgNVHSMEGDAWgBSJGAf/Pm0wF8k1rbuLcYbln2aSwzAdBgNVHQ4EFgQUfr+syABm" +
            "R7FB/f155oky+e5fLR8wDgYDVR0PAQH/BAQDAgeAMBMGA1UdJQQMMAoGCCsGAQUF" +
            "BwMDMIGVBgNVHR8EgY0wgYowQ6BBoD+GPWh0dHA6Ly9jcmwzLmRpZ2ljZXJ0LmNv" +
            "bS9EaWdpQ2VydEc1Q1NSU0E0MDk2U0hBMzg0MjAyMUNBMS5jcmwwQ6BBoD+GPWh0" +
            "dHA6Ly9jcmw0LmRpZ2ljZXJ0LmNvbS9EaWdpQ2VydEc1Q1NSU0E0MDk2U0hBMzg0" +
            "MjAyMUNBMS5jcmwwPgYDVR0gBDcwNTAzBgZngQwBBAEwKTAnBggrBgEFBQcCARYb" +
            "aHR0cDovL3d3dy5kaWdpY2VydC5jb20vQ1BTMIGCBggrBgEFBQcBAQR2MHQwJAYI" +
            "KwYBBQUHMAGGGGh0dHA6Ly9vY3NwLmRpZ2ljZXJ0LmNvbTBMBggrBgEFBQcwAoZA" +
            "aHR0cDovL2NhY2VydHMuZGlnaWNlcnQuY29tL0RpZ2lDZXJ0RzVDU1JTQTQwOTZT" +
            "SEEzODQyMDIxQ0ExLmNydDAJBgNVHRMEAjAAMA0GCSqGSIb3DQEBCwUAA4ICAQCj" +
            "HCYM2aGyHFpdWRkbxa+so37uyPDJ27wpn4oNhaSKKletB8Xr6rMa5JBJ1NUa2S9Q" +
            "3CYvdH9pGjjThUJPR0Lg8DrZNkPtqyjQLQ86tYfjteoKe5SXTxZ0epXikRTXySFa" +
            "NM1KOEf5CJq7OywLLXVxm+F2VEX2+PzLAtHxViGeN7AsZMbWGlp3VkymVITcKkP3" +
            "vnsoF6Teacb019xxBDCLuhNG91rlzhG0YrJ3qMlPyStmzxqy+2UIlPwFeLRkBkRG" +
            "K7Kxi2xvYbgdFP93kRbwJbp8d3x/JG3LpwAZv+NV0TY3jBj7ymGoGuiSV0nU9XPt" +
            "yDm1FYYZAH2ykwo8YPZqAcu+EHvyxi1dgOM3ABfoLJfOIYJv2gxPx+KIKzn1wzBS" +
            "kk1HMf8xbYXs40vF2Lrb7AQIyLa2ZskJTyfb0dyEyOq+vvVgLA9ZdwidzD1RnVf6" +
            "vOb7KbMSBCLK+HGqHrW+hhSDi2vHvSit7Cn+q80ZmzRqvJ/+mVl+ppnjDC7nSLIa" +
            "qeG0fvUz6SabPX7yV92D5ARrJJ3xgAvgmgWfuKBV7WlEGCmj0QTWZ0/AFBLzNcq7" +
            "+0rgP0GM98MZpKa8pHZaS1A3uP1TFzamfVGdv0FVHXSkN5Kvg0iPh4Qz9TRiCkyE" +
            "boJeU1LYdyTrP/+q3zQqsGa9xdQ50EovjWymbvWzCQ==\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=Win the Customer LLC, O=Win the Customer LLC, L=Saratoga Springs,
    // ST=Utah, C=US
    // Issuer: CN=DigiCert G5 CS RSA4096 SHA384 2021 CA1, O="DigiCert, Inc.", C=US
    // Serial number: f409d101094769abaf06f085f11ca4f
    // Valid from: Sun Oct 22 17:00:00 PDT 2023 until: Tue Oct 22 16:59:59 PDT 2024
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIHKTCCBRGgAwIBAgIQD0CdEBCUdpq68G8IXxHKTzANBgkqhkiG9w0BAQsFADBX\n" +
            "MQswCQYDVQQGEwJVUzEXMBUGA1UEChMORGlnaUNlcnQsIEluYy4xLzAtBgNVBAMT\n" +
            "JkRpZ2lDZXJ0IEc1IENTIFJTQTQwOTYgU0hBMzg0IDIwMjEgQ0ExMB4XDTIzMTAy\n" +
            "MzAwMDAwMFoXDTI0MTAyMjIzNTk1OVowdTELMAkGA1UEBhMCVVMxDTALBgNVBAgT\n" +
            "BFV0YWgxGTAXBgNVBAcTEFNhcmF0b2dhIFNwcmluZ3MxHTAbBgNVBAoTFFdpbiB0\n" +
            "aGUgQ3VzdG9tZXIgTExDMR0wGwYDVQQDExRXaW4gdGhlIEN1c3RvbWVyIExMQzCC\n" +
            "AiIwDQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIBANKPhVjUn2MyZs/l4AO0kQCE\n" +
            "HdR9aWJ86nT8jSSbHdRv56n3h5P3sxMfFne4jCY3DzrykndeLp4gAt2sot1ziinh\n" +
            "ahMOYOZtvyUzt5/McvQyuQjdXLaSuWcvlKaTl67U3QWQe+P6kmkRDsrCH0S51UeH\n" +
            "8Tct/bq7ZUyX9Jb80VPVegUN80YzDgkJ2OrLngD6EKVYKQlV5K+KGyQpCOWq5CEB\n" +
            "1aX63EnGiSMy0OOsS/J8aFz3BoYpEcrLSSnW84P6AhbevMfO7kr4mTLZKkx0Ic0Q\n" +
            "s6TuaXcf84OuF2T/HwM0qzP7iQhE18TJmsyumVZID7t1+Br43037pb3CXcWTTJvO\n" +
            "+zTkiArWy3SqAabyHirbssa75qCVHzcOHviy2wEDhc51cu1MZE8xtSDT4Hee87t0\n" +
            "ptwC6v6CWaCn4XjOM6PFHr2DCpqJIvjfqjB3CaZTxfAtOP7vAjXNSZPLSpo4KH/G\n" +
            "bBm0AgtvpTSl801Jz6c2uJhwTFN0HXeIlfKMXf/Y0MhO9YddSEmpFkq3rGwsguh0\n" +
            "jlVIeNPQNTIb/fmhG6gTYQBySGK0EmNYW/fAii0UunAASgM6/h3Wn4jwgDd3HSxo\n" +
            "CeU1kyIgYwHI8h7HlVOI3iyEJe/+IGQE43j80sv26OtkmV1swjhaes88vFRhmr0Z\n" +
            "jKG3tfd4J3M/9e4Zhb6hAgMBAAGjggHRMIIBzTAfBgNVHSMEGDAWgBSJGAf/Pm0w\n" +
            "F8k1rbuLcYbln2aSwzAdBgNVHQ4EFgQUeR5HZd44BvPIsRnNcBMDujfUZ9kwPgYD\n" +
            "VR0gBDcwNTAzBgZngQwBBAEwKTAnBggrBgEFBQcCARYbaHR0cDovL3d3dy5kaWdp\n" +
            "Y2VydC5jb20vQ1BTMA4GA1UdDwEB/wQEAwIHgDATBgNVHSUEDDAKBggrBgEFBQcD\n" +
            "AzCBlQYDVR0fBIGNMIGKMEOgQaA/hj1odHRwOi8vY3JsMy5kaWdpY2VydC5jb20v\n" +
            "RGlnaUNlcnRHNUNTUlNBNDA5NlNIQTM4NDIwMjFDQTEuY3JsMEOgQaA/hj1odHRw\n" +
            "Oi8vY3JsNC5kaWdpY2VydC5jb20vRGlnaUNlcnRHNUNTUlNBNDA5NlNIQTM4NDIw\n" +
            "MjFDQTEuY3JsMIGCBggrBgEFBQcBAQR2MHQwJAYIKwYBBQUHMAGGGGh0dHA6Ly9v\n" +
            "Y3NwLmRpZ2ljZXJ0LmNvbTBMBggrBgEFBQcwAoZAaHR0cDovL2NhY2VydHMuZGln\n" +
            "aWNlcnQuY29tL0RpZ2lDZXJ0RzVDU1JTQTQwOTZTSEEzODQyMDIxQ0ExLmNydDAJ\n" +
            "BgNVHRMEAjAAMA0GCSqGSIb3DQEBCwUAA4ICAQAKCH6ri6f507/j2ifF7VQbavWE\n" +
            "Wn4T63PzJveL6/kedV7avhrQ/B6uHrez1xy/RH/MlL/B+TF6YTg+ILqtKR/PyJrg\n" +
            "N+1RON0Eg3AEWWDtGl3KBYFlklz8Szo+xmXf5GYiqueejbxscH1BA0PU/5CgGkr6\n" +
            "1Kk4OXqKqmpuPeQCxca1ARDD749E/2IFsDGC8kBCWepV62l0/xcDKWD5Zn+y4Tkh\n" +
            "5+YJJ21D746sNDOsDNJ4DuqEYrXWUH6BlT5EDYelGqRCOdyTYUdDg+QcSFWnH7wR\n" +
            "O+eIA3BLSw0x1Vh6DJRKm5H644sPVppaI1jVZDe+zBwp2e/j8XH7KDlp/WaRUhcU\n" +
            "bjGg2Ss5TMbBjR6B4nMwjvqaCIFoAD6aFRYc80px/KY6KTSyOFF0FBQNuhSsUZQy\n" +
            "p74aRjUraSu/RiJMA8A6OYGo1b7W9o/UOg0MB4WQkfwl+Mxh+58QKjLjZr9VVapW\n" +
            "4yv0G/G6rT/pHrRiyBcT7Kt4xNFsmMFAN4BXL9WI9mkGDa4iwDmWVjIjAaiilaaC\n" +
            "MIXwwm3eg/QBgWBUrwXf3YC+1HXkaFDZc5apQ5uaNJPjQo9nQ6xqfpnACXTJ/Lwm\n" +
            "JBu4YlXPby5Vh6mWWSyVdbICrCD7BtGP8aSBPFGPEuPEjK32uyeoGWVwwSubVFPX\n" +
            "ARhLX5oSFZUySvHgYg==\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator) throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Mon Oct 23 14:44:23 PDT 2023", System.out);
    }
}
