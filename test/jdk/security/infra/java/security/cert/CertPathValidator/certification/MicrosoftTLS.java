/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8304760
 * @summary Interoperability tests with Microsoft TLS root CAs
 * @build ValidatePathWithParams
 * @run main/othervm -Djava.security.debug=certpath MicrosoftTLS OCSP
 * @run main/othervm -Djava.security.debug=certpath MicrosoftTLS CRL
 */

/*
 * Microsoft ECC Root Certificate Authority 2017:
 * Valid: http://acteccroot2017.pki.microsoft.com/
 * Revoked: http://rvkeccroot2017.pki.microsoft.com/
 * Expired: http://expeccroot2017.pki.microsoft.com/
 *
 * Microsoft RSA Root Certificate Authority 2017:
 * Valid: http://actrsaroot2017.pki.microsoft.com/
 * Revoked: http://rvkrsaroot2017.pki.microsoft.com/
 * Expired: http://exprsaroot2017.pki.microsoft.com/
 */
public class MicrosoftTLS {

    public static void main(String[] args) throws Exception {

        ValidatePathWithParams pathValidator = new ValidatePathWithParams(null);

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            pathValidator.enableCRLCheck();
        } else {
            // OCSP check by default
            pathValidator.enableOCSPCheck();
        }

        new MicrosoftECCTLS().runTest(pathValidator);
        new MicrosoftRSATLS().runTest(pathValidator);
    }
}

class MicrosoftECCTLS {

    // Owner: CN=Microsoft ECC TLS Issuing AOC CA 01, O=Microsoft Corporation, C=US
    // Issuer: CN=Microsoft ECC Root Certificate Authority 2017, O=Microsoft
    // Corporation, C=US
    // Serial number: 33000000282bfd23e7d1add707000000000028
    // Valid from: Thu Jun 24 12:58:36 PDT 2021 until: Wed Jun 24 12:58:36 PDT 2026
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIESTCCA8+gAwIBAgITMwAAACgr/SPn0a3XBwAAAAAAKDAKBggqhkjOPQQDAzBl\n" +
            "MQswCQYDVQQGEwJVUzEeMBwGA1UEChMVTWljcm9zb2Z0IENvcnBvcmF0aW9uMTYw\n" +
            "NAYDVQQDEy1NaWNyb3NvZnQgRUNDIFJvb3QgQ2VydGlmaWNhdGUgQXV0aG9yaXR5\n" +
            "IDIwMTcwHhcNMjEwNjI0MTk1ODM2WhcNMjYwNjI0MTk1ODM2WjBbMQswCQYDVQQG\n" +
            "EwJVUzEeMBwGA1UEChMVTWljcm9zb2Z0IENvcnBvcmF0aW9uMSwwKgYDVQQDEyNN\n" +
            "aWNyb3NvZnQgRUNDIFRMUyBJc3N1aW5nIEFPQyBDQSAwMTB2MBAGByqGSM49AgEG\n" +
            "BSuBBAAiA2IABMBXcHExvrYrhw7v30oPR4aBaMne5o0FtTtbMV7iqVhTJDQSWDEJ\n" +
            "hr528nyS6jcLLu9pLXQMJYxVd7bz4wWXgVtZnnbQ7trAAIPWVh5B6f5eJf5OQ7w7\n" +
            "AwJgz3snP5Hx16OCAkkwggJFMA4GA1UdDwEB/wQEAwIBhjAQBgkrBgEEAYI3FQEE\n" +
            "AwIBADAdBgNVHQ4EFgQUMVu5zlEbfNGqA8Dr7TZdwp3TieEwHQYDVR0lBBYwFAYI\n" +
            "KwYBBQUHAwEGCCsGAQUFBwMCMBkGCSsGAQQBgjcUAgQMHgoAUwB1AGIAQwBBMBIG\n" +
            "A1UdEwEB/wQIMAYBAf8CAQAwHwYDVR0jBBgwFoAUyMuZcnBSDPjmvrIEVykqz0IQ\n" +
            "7TUwcAYDVR0fBGkwZzBloGOgYYZfaHR0cDovL3d3dy5taWNyb3NvZnQuY29tL3Br\n" +
            "aW9wcy9jcmwvTWljcm9zb2Z0JTIwRUNDJTIwUm9vdCUyMENlcnRpZmljYXRlJTIw\n" +
            "QXV0aG9yaXR5JTIwMjAxNy5jcmwwga4GCCsGAQUFBwEBBIGhMIGeMG0GCCsGAQUF\n" +
            "BzAChmFodHRwOi8vd3d3Lm1pY3Jvc29mdC5jb20vcGtpb3BzL2NlcnRzL01pY3Jv\n" +
            "c29mdCUyMEVDQyUyMFJvb3QlMjBDZXJ0aWZpY2F0ZSUyMEF1dGhvcml0eSUyMDIw\n" +
            "MTcuY3J0MC0GCCsGAQUFBzABhiFodHRwOi8vb25lb2NzcC5taWNyb3NvZnQuY29t\n" +
            "L29jc3AwcAYDVR0gBGkwZzAIBgZngQwBAgEwCAYGZ4EMAQICMFEGDCsGAQQBgjdM\n" +
            "g30BATBBMD8GCCsGAQUFBwIBFjNodHRwOi8vd3d3Lm1pY3Jvc29mdC5jb20vcGtp\n" +
            "b3BzL0RvY3MvUmVwb3NpdG9yeS5odG0wCgYIKoZIzj0EAwMDaAAwZQIxANmPydUj\n" +
            "lgj/2K77UnMeMkSGIgXzOhcTsixzZL+NmTR1Bq2hSPeA6Y3mn3lMlwxZmAIwIio6\n" +
            "KrgItH4YmLWKd8QClIrE9QjbDlR7oFqaU3J34bWbMlAEjRARdZhhQlNwdORe\n" +
            "-----END CERTIFICATE-----";

    // Owner: O=Microsoft Corporation, L=Redmond, ST=Washington, C=US
    // Issuer: CN=Microsoft ECC TLS Issuing AOC CA 01, O=Microsoft Corporation, C=US
    // Serial number: 3300000154e1c6007ee3d5c903000000000154
    // Valid from: Fri Oct 14 13:44:52 PDT 2022 until: Mon Oct 09 13:44:52 PDT 2023
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIF3zCCBWSgAwIBAgITMwAAAVThxgB+49XJAwAAAAABVDAKBggqhkjOPQQDAzBb\n" +
            "MQswCQYDVQQGEwJVUzEeMBwGA1UEChMVTWljcm9zb2Z0IENvcnBvcmF0aW9uMSww\n" +
            "KgYDVQQDEyNNaWNyb3NvZnQgRUNDIFRMUyBJc3N1aW5nIEFPQyBDQSAwMTAeFw0y\n" +
            "MjEwMTQyMDQ0NTJaFw0yMzEwMDkyMDQ0NTJaMFQxCzAJBgNVBAYTAlVTMRMwEQYD\n" +
            "VQQIEwpXYXNoaW5ndG9uMRAwDgYDVQQHEwdSZWRtb25kMR4wHAYDVQQKExVNaWNy\n" +
            "b3NvZnQgQ29ycG9yYXRpb24wdjAQBgcqhkjOPQIBBgUrgQQAIgNiAARk86yqvyiv\n" +
            "jH2Frg2l6bmh1f0CqiKAEHdA2S2vTQhR4CtvFArkrPdqcKrhAAfQSgnC8KJQ08gl\n" +
            "QvjK55202ib55YX3h+96IW6fQOkE18cvPwqkD3DVQuROouLaL1r70NWjggPvMIID\n" +
            "6zCCAX8GCisGAQQB1nkCBAIEggFvBIIBawFpAHYA6D7Q2j71BjUy51covIlryQPT\n" +
            "y9ERa+zraeF3fW0GvW4AAAGD2EdUigAABAMARzBFAiEA6rbt+9QhpuqX36PnuckO\n" +
            "fR0Wu/8z3Yry9fdFKvJDCEUCIGBz901b4ZGEjCaSJdlZVr29v2td4crPa9I6S97i\n" +
            "nShAAHYAs3N3B+GEUPhjhtYFqdwRCUp5LbFnDAuH3PADDnk2pZoAAAGD2EdU/wAA\n" +
            "BAMARzBFAiBIvnSKGeCIWOlZowi7s7ZdwmyGhv2waJWSdewUSS6UOAIhALJhPQ19\n" +
            "nmjjTwWB9sgCIF7RZbd2xwBd1hno06MQMSqTAHcAejKMVNi3LbYg6jjgUh7phBZw\n" +
            "MhOFTTvSK8E6V6NS61IAAAGD2EdUxwAABAMASDBGAiEArrc6Fu74KTj/z4lGCK9A\n" +
            "O6UkhLpKnXdxEHilY7ghcZICIQCUjkvK4wehX1qEonjQoBkBJxLCus6y8WbkoxCe\n" +
            "jHu2HTAbBgkrBgEEAYI3FQoEDjAMMAoGCCsGAQUFBwMBMDwGCSsGAQQBgjcVBwQv\n" +
            "MC0GJSsGAQQBgjcVCIe91xuB5+tGgoGdLo7QDIfw2h1dgbXdUIWf/XUCAWQCAR0w\n" +
            "gaYGCCsGAQUFBwEBBIGZMIGWMGUGCCsGAQUFBzAChllodHRwOi8vd3d3Lm1pY3Jv\n" +
            "c29mdC5jb20vcGtpb3BzL2NlcnRzL01pY3Jvc29mdCUyMEVDQyUyMFRMUyUyMElz\n" +
            "c3VpbmclMjBBT0MlMjBDQSUyMDAxLmNydDAtBggrBgEFBQcwAYYhaHR0cDovL29u\n" +
            "ZW9jc3AubWljcm9zb2Z0LmNvbS9vY3NwMB0GA1UdDgQWBBTVpTA+3jWCa1okX5Ri\n" +
            "HnuY2/b+IzAOBgNVHQ8BAf8EBAMCB4AwKwYDVR0RBCQwIoIgYWN0ZWNjcm9vdDIw\n" +
            "MTcucGtpLm1pY3Jvc29mdC5jb20waAYDVR0fBGEwXzBdoFugWYZXaHR0cDovL3d3\n" +
            "dy5taWNyb3NvZnQuY29tL3BraW9wcy9jcmwvTWljcm9zb2Z0JTIwRUNDJTIwVExT\n" +
            "JTIwSXNzdWluZyUyMEFPQyUyMENBJTIwMDEuY3JsMGYGA1UdIARfMF0wUQYMKwYB\n" +
            "BAGCN0yDfQEBMEEwPwYIKwYBBQUHAgEWM2h0dHA6Ly93d3cubWljcm9zb2Z0LmNv\n" +
            "bS9wa2lvcHMvRG9jcy9SZXBvc2l0b3J5Lmh0bTAIBgZngQwBAgIwHwYDVR0jBBgw\n" +
            "FoAUMVu5zlEbfNGqA8Dr7TZdwp3TieEwEwYDVR0lBAwwCgYIKwYBBQUHAwEwCgYI\n" +
            "KoZIzj0EAwMDaQAwZgIxAOKV8s3SpXVd6zho8zQa4uGXkxPVocYo410FdTwu0lw7\n" +
            "G/MQPhLmj4DNsQJ/nYzDcwIxAMw7iZExsY9Is66/EaAty4rA+yuliwCag88VnDRH\n" +
            "9cjiongZgpddIYS8xf76B2pi/Q==\n" +
            "-----END CERTIFICATE-----";

    // Owner: O=Microsoft Corporation, L=Redmond, ST=Washington, C=US
    // Issuer: CN=Microsoft ECC TLS Issuing AOC CA 01, O=Microsoft Corporation, C=US
    // Serial number: 3300000155ea28117be8708034000000000155
    // Valid from: Fri Oct 14 13:50:39 PDT 2022 until: Mon Oct 09 13:50:39 PDT 2023
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIF3TCCBWOgAwIBAgITMwAAAVXqKBF76HCANAAAAAABVTAKBggqhkjOPQQDAzBb\n" +
            "MQswCQYDVQQGEwJVUzEeMBwGA1UEChMVTWljcm9zb2Z0IENvcnBvcmF0aW9uMSww\n" +
            "KgYDVQQDEyNNaWNyb3NvZnQgRUNDIFRMUyBJc3N1aW5nIEFPQyBDQSAwMTAeFw0y\n" +
            "MjEwMTQyMDUwMzlaFw0yMzEwMDkyMDUwMzlaMFQxCzAJBgNVBAYTAlVTMRMwEQYD\n" +
            "VQQIEwpXYXNoaW5ndG9uMRAwDgYDVQQHEwdSZWRtb25kMR4wHAYDVQQKExVNaWNy\n" +
            "b3NvZnQgQ29ycG9yYXRpb24wdjAQBgcqhkjOPQIBBgUrgQQAIgNiAARbimHzMojc\n" +
            "ilBoJCu+adc99oS855DwGTmkKofXfEf6Ej6G9v6Zg1Y2a1wqs5Wd3IcqQONeqKK8\n" +
            "EGxUL7DBpf1dBDsRpWSfenYIRtAzs/JznW0dfGPgnY0kGi4g52JegCOjggPuMIID\n" +
            "6jCCAX4GCisGAQQB1nkCBAIEggFuBIIBagFoAHUArfe++nz/EMiLnT2cHj4YarRn\n" +
            "KV3PsQwkyoWGNOvcgooAAAGD2EyY+gAABAMARjBEAiBnysZazdmXKeL4CnYkJxI2\n" +
            "g5juWT5jQfBi5Nxfc3zc9gIgGSGTTGw+E0864BRuAJjhFRF+j5keQ7Rik+PhGnd1\n" +
            "P1gAdgB6MoxU2LcttiDqOOBSHumEFnAyE4VNO9IrwTpXo1LrUgAAAYPYTJjXAAAE\n" +
            "AwBHMEUCIQDmYqZ1fw/8X2lBl51TknJ8t8sRz4fEFkayqFrmNug1WQIgELQm99K3\n" +
            "QH+Rr8rk9x6835NjXBBAyrrI2B8XLiELITUAdwCzc3cH4YRQ+GOG1gWp3BEJSnkt\n" +
            "sWcMC4fc8AMOeTalmgAAAYPYTJkaAAAEAwBIMEYCIQD+jnAFon/1Bobh3R4wzym7\n" +
            "yiDQ35ZUeRcfFes1IvgyvgIhAPILSf2w3HW7YmbthAVT4P13G+8xFIVlYihgVegU\n" +
            "cJy8MBsGCSsGAQQBgjcVCgQOMAwwCgYIKwYBBQUHAwEwPAYJKwYBBAGCNxUHBC8w\n" +
            "LQYlKwYBBAGCNxUIh73XG4Hn60aCgZ0ujtAMh/DaHV2Btd1QhZ/9dQIBZAIBHTCB\n" +
            "pgYIKwYBBQUHAQEEgZkwgZYwZQYIKwYBBQUHMAKGWWh0dHA6Ly93d3cubWljcm9z\n" +
            "b2Z0LmNvbS9wa2lvcHMvY2VydHMvTWljcm9zb2Z0JTIwRUNDJTIwVExTJTIwSXNz\n" +
            "dWluZyUyMEFPQyUyMENBJTIwMDEuY3J0MC0GCCsGAQUFBzABhiFodHRwOi8vb25l\n" +
            "b2NzcC5taWNyb3NvZnQuY29tL29jc3AwHQYDVR0OBBYEFN3cgtHESQ8o7thvaL42\n" +
            "bD7mpfktMA4GA1UdDwEB/wQEAwIHgDArBgNVHREEJDAigiBydmtlY2Nyb290MjAx\n" +
            "Ny5wa2kubWljcm9zb2Z0LmNvbTBoBgNVHR8EYTBfMF2gW6BZhldodHRwOi8vd3d3\n" +
            "Lm1pY3Jvc29mdC5jb20vcGtpb3BzL2NybC9NaWNyb3NvZnQlMjBFQ0MlMjBUTFMl\n" +
            "MjBJc3N1aW5nJTIwQU9DJTIwQ0ElMjAwMS5jcmwwZgYDVR0gBF8wXTBRBgwrBgEE\n" +
            "AYI3TIN9AQEwQTA/BggrBgEFBQcCARYzaHR0cDovL3d3dy5taWNyb3NvZnQuY29t\n" +
            "L3BraW9wcy9Eb2NzL1JlcG9zaXRvcnkuaHRtMAgGBmeBDAECAjAfBgNVHSMEGDAW\n" +
            "gBQxW7nOURt80aoDwOvtNl3CndOJ4TATBgNVHSUEDDAKBggrBgEFBQcDATAKBggq\n" +
            "hkjOPQQDAwNoADBlAjBBhbuh/iukcibeEh/Op3RfNf6jUSyza4lZvsJsRiEVwySa\n" +
            "ofmg8OvBO2l2+9MjoCUCMQCoiyS1tDgtjW9gguKDgPXypURpL27KfnCzwx6ar2LN\n" +
            "gCZ/soGnLsgPIscuNH/BK20=\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator) throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Fri Oct 14 15:46:18 PDT 2022", System.out);
    }
}

class MicrosoftRSATLS {

    // Owner: CN=Microsoft RSA TLS Issuing AOC CA 01, O=Microsoft Corporation, C=US
    // Issuer: CN=Microsoft RSA Root Certificate Authority 2017, O=Microsoft
    // Corporation, C=US
    // Serial number: 330000002ffaf06f6697e2469c00000000002f
    // Valid from: Thu Jun 24 13:57:35 PDT 2021 until: Wed Jun 24 13:57:35 PDT 2026
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIHmDCCBYCgAwIBAgITMwAAAC/68G9ml+JGnAAAAAAALzANBgkqhkiG9w0BAQwF\n" +
            "ADBlMQswCQYDVQQGEwJVUzEeMBwGA1UEChMVTWljcm9zb2Z0IENvcnBvcmF0aW9u\n" +
            "MTYwNAYDVQQDEy1NaWNyb3NvZnQgUlNBIFJvb3QgQ2VydGlmaWNhdGUgQXV0aG9y\n" +
            "aXR5IDIwMTcwHhcNMjEwNjI0MjA1NzM1WhcNMjYwNjI0MjA1NzM1WjBbMQswCQYD\n" +
            "VQQGEwJVUzEeMBwGA1UEChMVTWljcm9zb2Z0IENvcnBvcmF0aW9uMSwwKgYDVQQD\n" +
            "EyNNaWNyb3NvZnQgUlNBIFRMUyBJc3N1aW5nIEFPQyBDQSAwMTCCAiIwDQYJKoZI\n" +
            "hvcNAQEBBQADggIPADCCAgoCggIBAKAYz8zB6I+LeiWYURf1QUaISydvRgxWfcc6\n" +
            "UvEiwvryj2UsRfFuREo2ErLTvP9qQ9E0YBTyWEqI2TXn4jo2uZ2cpGODiQQWlixe\n" +
            "aAFcYgSqLzidFXj401vzQsz4E0zylD/ZeY+xkQ6xrdg5312x2u2Ap7AWLzqolZHZ\n" +
            "gR0aicn9gcO6M4qn6Uuge8mOve1N7U6j8ebhSiw0KlkzY9ha1Kvrez+NXQdeLC+V\n" +
            "PDWPPPlBWeysTnIM6dusbV1v2/C7Ooz9TuGb8wiXRriPpI7+igSIPqBebF00rHGJ\n" +
            "Dmx9eN3g78VF9JpTrrRkV8alpMYVZKAh9IzMp9NWVZsw5wgZaX2W05SaXkSHP3zR\n" +
            "OBANhKzwkBkCcDMbmF1LFOk+wgkcEtFlKEnfgvOQVHTp02gTzyhSxstw0buon4Cy\n" +
            "ZAm1L+6bJJ+puNL8HuLTJxq1mqiaY0T50olJeySSX5uJBo/l29Pz+0WjANnhRLVq\n" +
            "e5xdxPV11QGHDxnvsXaMgC4y/5sLo5v4UEZT+4VDcKiRHReusJD+kUt92FSYqWTK\n" +
            "xs6zwuxf25as/rJbZT99o9QVFLfHEs6DgHKNIqQuVxZxH0T3M6XqfmnRTo1FrD8i\n" +
            "p/93Q4zQta5S9whe/sAxpizwyMw/9fhBDHGVHfgFV1C0EP9zxkyHEya0CGAMhbzp\n" +
            "+0Y/ZYxrAgMBAAGjggJJMIICRTAOBgNVHQ8BAf8EBAMCAYYwEAYJKwYBBAGCNxUB\n" +
            "BAMCAQAwHQYDVR0OBBYEFOtMMXw9PzK4g9fF23va5HjanBRXMB0GA1UdJQQWMBQG\n" +
            "CCsGAQUFBwMBBggrBgEFBQcDAjAZBgkrBgEEAYI3FAIEDB4KAFMAdQBiAEMAQTAS\n" +
            "BgNVHRMBAf8ECDAGAQH/AgEAMB8GA1UdIwQYMBaAFAnLWX+GsnCPGsM548DZ6b+7\n" +
            "TbIjMHAGA1UdHwRpMGcwZaBjoGGGX2h0dHA6Ly93d3cubWljcm9zb2Z0LmNvbS9w\n" +
            "a2lvcHMvY3JsL01pY3Jvc29mdCUyMFJTQSUyMFJvb3QlMjBDZXJ0aWZpY2F0ZSUy\n" +
            "MEF1dGhvcml0eSUyMDIwMTcuY3JsMIGuBggrBgEFBQcBAQSBoTCBnjBtBggrBgEF\n" +
            "BQcwAoZhaHR0cDovL3d3dy5taWNyb3NvZnQuY29tL3BraW9wcy9jZXJ0cy9NaWNy\n" +
            "b3NvZnQlMjBSU0ElMjBSb290JTIwQ2VydGlmaWNhdGUlMjBBdXRob3JpdHklMjAy\n" +
            "MDE3LmNydDAtBggrBgEFBQcwAYYhaHR0cDovL29uZW9jc3AubWljcm9zb2Z0LmNv\n" +
            "bS9vY3NwMHAGA1UdIARpMGcwCAYGZ4EMAQIBMAgGBmeBDAECAjBRBgwrBgEEAYI3\n" +
            "TIN9AQEwQTA/BggrBgEFBQcCARYzaHR0cDovL3d3dy5taWNyb3NvZnQuY29tL3Br\n" +
            "aW9wcy9Eb2NzL1JlcG9zaXRvcnkuaHRtMA0GCSqGSIb3DQEBDAUAA4ICAQAkucWk\n" +
            "Mrgs2ahYrG7y4sY2yZno4f9TGyk7p+Srg4Yz/g7LmVeyOob9o579Omw9AiyeDK8Y\n" +
            "/dXnTTof+sKJrlNTpIzyEBkzCiGGkWtp7x2yxLCm12L65wtmD/6OAV9Bm1kOhf3p\n" +
            "7v+d3gtFt7cw46W35lr+fguy62s7uuytTV9hfhQ0pp2E2E9F6B7U71jR4bC+6zGq\n" +
            "+34AmqTirjKHwXOhWDRDpEJIkaFAh+qdz/nqJktZj3n5GdC94jfWrMUJjClGjlc4\n" +
            "+Ws3AxN46oFpx8oIXDG9wIPfFhUf0SdnCYJL8TD5+qBNp0H5q/V2R31Wi8rijHGQ\n" +
            "4CxHqzP5VJbjgvRQgxAp39BrmLQ+JSvf9e5VqQqaH4NYgpB1WObq12B73BJHjBOv\n" +
            "pRrULFjPqDW8sPRBzBTRXkXOPEdZbzQj6O/CWEFsg6ilO4thk3n3drb9FEJjVh9u\n" +
            "GtRXV6Ea5bNaPvJppZNXb7M9mORk3mddx/K1FgOETQE3quh+mU4ojbSRUWMVmjcb\n" +
            "6bKF5oQd+Q0do4yaEIfH1oVnIas/FIE/xu3Z4fvBs0qdiNLCeNT6uS26vqD2PEvV\n" +
            "lFWb683Do3Ls59MMCxhy6Erb7kFQgu1oUWXGFhbMQkeLN4TXGi6X3loXYfING9om\n" +
            "nWa/udxvPRwAZmcHU2l2W8cwVXiy6uucsh3kPQ==\n" +
            "-----END CERTIFICATE-----";

    // Owner: O=Microsoft Corporation, L=Redmond, ST=Washington, C=US
    // Issuer: CN=Microsoft RSA TLS Issuing AOC CA 01, O=Microsoft Corporation, C=US
    // Serial number: 330000014a3b44c12636e54b9f00000000014a
    // Valid from: Fri Oct 14 13:55:34 PDT 2022 until: Mon Oct 09 13:55:34 PDT 2023
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIILDCCBhSgAwIBAgITMwAAAUo7RMEmNuVLnwAAAAABSjANBgkqhkiG9w0BAQwF\n" +
            "ADBbMQswCQYDVQQGEwJVUzEeMBwGA1UEChMVTWljcm9zb2Z0IENvcnBvcmF0aW9u\n" +
            "MSwwKgYDVQQDEyNNaWNyb3NvZnQgUlNBIFRMUyBJc3N1aW5nIEFPQyBDQSAwMTAe\n" +
            "Fw0yMjEwMTQyMDU1MzRaFw0yMzEwMDkyMDU1MzRaMFQxCzAJBgNVBAYTAlVTMRMw\n" +
            "EQYDVQQIEwpXYXNoaW5ndG9uMRAwDgYDVQQHEwdSZWRtb25kMR4wHAYDVQQKExVN\n" +
            "aWNyb3NvZnQgQ29ycG9yYXRpb24wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEK\n" +
            "AoIBAQDTo/3ysrrKP2eOLQ8JUFhQT09HJM1lUr0nH7RiP4VAKFrGFMIQSCsq17y7\n" +
            "PuTHxW53Fvxb5s/EKZobzhlgv4rHQxvoMuGWRBgJN6KfspQAuFnUVG+3y70fHy/O\n" +
            "PiVUJdfTupsys/fjzERqzx6FZoU1RzQ08na36SicSOQmj5svtHHxL8ZibDD48Xzp\n" +
            "oIEBh2uUDhevkZedBmqlIdAhNgKXqf2lieLjWXZQLzUyXHikQJxNFOHFVjBqH3pu\n" +
            "pYt2XD78bS/xeKRbGLw52+o3/u4eaPyiJoG0GaVSG2HRGcplu7Auk6ycD3htispr\n" +
            "dviXfHa3tW1hO52PrQBOWvpsP3jdAgMBAAGjggPuMIID6jCCAX4GCisGAQQB1nkC\n" +
            "BAIEggFuBIIBagFoAHUA6D7Q2j71BjUy51covIlryQPTy9ERa+zraeF3fW0GvW4A\n" +
            "AAGD2FEl4wAABAMARjBEAiBStVFeTYxl3DxgsM2z7VsvWZ5n7V0SXjnNdgFfmjfL\n" +
            "twIgQ6Xfm7oJQDMyBIuPVF0qxLr+EqZ71HDHz5n6g60orlcAdgB6MoxU2LcttiDq\n" +
            "OOBSHumEFnAyE4VNO9IrwTpXo1LrUgAAAYPYUSX8AAAEAwBHMEUCIFbeyJxWClLT\n" +
            "C1YjUizDHmL5TeKFluRsL0of3NXn7LXuAiEAoZLtiZOie9QLWA66IN3NO8F4VE72\n" +
            "m4hZyo0tcJ2FrDkAdwCzc3cH4YRQ+GOG1gWp3BEJSnktsWcMC4fc8AMOeTalmgAA\n" +
            "AYPYUSZUAAAEAwBIMEYCIQD7nnuRlDX0iUH+vfbl3aKgn6siy8fL5Dl6HczdPXgD\n" +
            "2AIhAJE6xuIKnLOC/BqVG8DydYmhM17TTSK3T98pBtvU9SDcMBsGCSsGAQQBgjcV\n" +
            "CgQOMAwwCgYIKwYBBQUHAwEwPAYJKwYBBAGCNxUHBC8wLQYlKwYBBAGCNxUIh73X\n" +
            "G4Hn60aCgZ0ujtAMh/DaHV2Btd1QhZ/9dQIBZAIBHTCBpgYIKwYBBQUHAQEEgZkw\n" +
            "gZYwZQYIKwYBBQUHMAKGWWh0dHA6Ly93d3cubWljcm9zb2Z0LmNvbS9wa2lvcHMv\n" +
            "Y2VydHMvTWljcm9zb2Z0JTIwUlNBJTIwVExTJTIwSXNzdWluZyUyMEFPQyUyMENB\n" +
            "JTIwMDEuY3J0MC0GCCsGAQUFBzABhiFodHRwOi8vb25lb2NzcC5taWNyb3NvZnQu\n" +
            "Y29tL29jc3AwHQYDVR0OBBYEFJ+DafMSR5RMWJrM6iGS024FVuBYMA4GA1UdDwEB\n" +
            "/wQEAwIEsDArBgNVHREEJDAigiBhY3Ryc2Fyb290MjAxNy5wa2kubWljcm9zb2Z0\n" +
            "LmNvbTBoBgNVHR8EYTBfMF2gW6BZhldodHRwOi8vd3d3Lm1pY3Jvc29mdC5jb20v\n" +
            "cGtpb3BzL2NybC9NaWNyb3NvZnQlMjBSU0ElMjBUTFMlMjBJc3N1aW5nJTIwQU9D\n" +
            "JTIwQ0ElMjAwMS5jcmwwZgYDVR0gBF8wXTBRBgwrBgEEAYI3TIN9AQEwQTA/Bggr\n" +
            "BgEFBQcCARYzaHR0cDovL3d3dy5taWNyb3NvZnQuY29tL3BraW9wcy9Eb2NzL1Jl\n" +
            "cG9zaXRvcnkuaHRtMAgGBmeBDAECAjAfBgNVHSMEGDAWgBTrTDF8PT8yuIPXxdt7\n" +
            "2uR42pwUVzATBgNVHSUEDDAKBggrBgEFBQcDATANBgkqhkiG9w0BAQwFAAOCAgEA\n" +
            "j80IEKdsV/mWM5LwiS12qjOFzukGhpaFgM4XVQV9QJ/oEwworf7KEFfp4YlrSbtw\n" +
            "Wwrh06LESleEfCqY+pbYHUx6ox4LvI5EYu23+YINSdhkTaITFZ1DDrYEHX08r26I\n" +
            "rdaTkUOLzP9CRuSw1tbcf0gsj/Dqr8ec3usktccOE6QFbCA9yCsKOr6WdPc4h3PV\n" +
            "WKHnpf4n46fZ+N+d7+eAOUZSjqsw/5i6/yiQ0Vx6rBMSKmEzkZx72Xkh9IowCeZJ\n" +
            "w/gstrzKepSljWUuNi2iXJB2OuIqydFodLXFc9eeH8MXShDqwFF77nf3R3jMAhvI\n" +
            "6fHnEz7+UqhMuyiAU5TfSjC1WyeqHhDZawWPumFyXEh0XX1eUphfoN3bApbZJhEE\n" +
            "tyhcz44mGawrjSpxlJGgE5TmKJ+CC73TcBC5Ehelo+Is1gzbbVQCu6gMZQyYS8qf\n" +
            "kg+JqJAOfx+YFn4bPAio8uF6XpcvMkcd9dyEYi2Q9zMhnQoOjLWj0pPSQaCBmmbI\n" +
            "ougVo16GCOdcOG9+c6dBjbHseaQY0a95ZirtNLbutIvmvMIysvAHMC3NkunnD0cQ\n" +
            "BxF47+meDc80QJGCaNlJ8E1SlUbEtRfVNsbcw1skO3hAsYAIA8M//BW7XcKRDvLn\n" +
            "nPrC+5fWtDzmXgUE/Sve3rCr/AfBiBrLERcJHxYy41U=\n" +
            "-----END CERTIFICATE-----";

    // Owner: O=Microsoft Corporation, L=Redmond, ST=Washington, C=US
    // Issuer: CN=Microsoft RSA TLS Issuing AOC CA 01, O=Microsoft Corporation, C=US
    // Serial number: 330000014b4c2b0b9955688feb00000000014b
    // Valid from: Fri Oct 14 13:56:58 PDT 2022 until: Mon Oct 09 13:56:58 PDT 2023
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIIKjCCBhKgAwIBAgITMwAAAUtMKwuZVWiP6wAAAAABSzANBgkqhkiG9w0BAQwF\n" +
            "ADBbMQswCQYDVQQGEwJVUzEeMBwGA1UEChMVTWljcm9zb2Z0IENvcnBvcmF0aW9u\n" +
            "MSwwKgYDVQQDEyNNaWNyb3NvZnQgUlNBIFRMUyBJc3N1aW5nIEFPQyBDQSAwMTAe\n" +
            "Fw0yMjEwMTQyMDU2NThaFw0yMzEwMDkyMDU2NThaMFQxCzAJBgNVBAYTAlVTMRMw\n" +
            "EQYDVQQIEwpXYXNoaW5ndG9uMRAwDgYDVQQHEwdSZWRtb25kMR4wHAYDVQQKExVN\n" +
            "aWNyb3NvZnQgQ29ycG9yYXRpb24wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEK\n" +
            "AoIBAQD2UxPzrv61IqG8jCFPWM3KeQpBeBlxh3mzvWVFmo340r0J1C3uLaUTPYLo\n" +
            "P+Xndq2GqYLlm/5FEY7ynU1as57SH0tHbKCIYYJezn/ZJHUYcOY80uGKpP3bdbRq\n" +
            "W51Xo7/gzTrXFJ2Nrn05d9mKBq+Oxs71+Nj7QuzjHYAF0n8OWNwZCBOBdAX3EDVQ\n" +
            "4HBMSkIzriodM0FD2zkT8RIvZ7WbpLxvZXqWbynAeLirTRYE2lY9UalxrP+wCef9\n" +
            "DARxcpEgF30nwRnALfOhnuOhdrtdLYhArfQMyDcvJnDyzCWEZCaPNtBhdsziJjf9\n" +
            "A8R4/qdnlQE4/24O9MXQja5dwyyRAgMBAAGjggPsMIID6DCCAXwGCisGAQQB1nkC\n" +
            "BAIEggFsBIIBaAFmAHYA6D7Q2j71BjUy51covIlryQPTy9ERa+zraeF3fW0GvW4A\n" +
            "AAGD2FJirgAABAMARzBFAiBct8qI4aiBtisWWMKAtwCueQWAnFtxcrGBiZjwctiB\n" +
            "pwIhAPasvYgCS4Rbhb6p2//TCeq0P2H3jUftmi0afwhJYXLaAHUAs3N3B+GEUPhj\n" +
            "htYFqdwRCUp5LbFnDAuH3PADDnk2pZoAAAGD2FJjIwAABAMARjBEAiBjbry24wGs\n" +
            "tpzJFzxWAk7h3IHMKiY1KxIieJMBe7k1dQIgPvDrVgOiUeWlYJmDSdRafTVZHfQg\n" +
            "bODj86WqyB5ndt4AdQB6MoxU2LcttiDqOOBSHumEFnAyE4VNO9IrwTpXo1LrUgAA\n" +
            "AYPYUmLUAAAEAwBGMEQCIHlmAPOJT2CSJPnupJqbiUOE8nukIuNxaayaEROQQC16\n" +
            "AiBufiWDUp9FNjGdZVhjX3t/Bh3iSNrMJD22k5BcNzUbIjAbBgkrBgEEAYI3FQoE\n" +
            "DjAMMAoGCCsGAQUFBwMBMDwGCSsGAQQBgjcVBwQvMC0GJSsGAQQBgjcVCIe91xuB\n" +
            "5+tGgoGdLo7QDIfw2h1dgbXdUIWf/XUCAWQCAR0wgaYGCCsGAQUFBwEBBIGZMIGW\n" +
            "MGUGCCsGAQUFBzAChllodHRwOi8vd3d3Lm1pY3Jvc29mdC5jb20vcGtpb3BzL2Nl\n" +
            "cnRzL01pY3Jvc29mdCUyMFJTQSUyMFRMUyUyMElzc3VpbmclMjBBT0MlMjBDQSUy\n" +
            "MDAxLmNydDAtBggrBgEFBQcwAYYhaHR0cDovL29uZW9jc3AubWljcm9zb2Z0LmNv\n" +
            "bS9vY3NwMB0GA1UdDgQWBBQVaBKJl3UpdKhMrW9owCC3eUdMWzAOBgNVHQ8BAf8E\n" +
            "BAMCBLAwKwYDVR0RBCQwIoIgcnZrcnNhcm9vdDIwMTcucGtpLm1pY3Jvc29mdC5j\n" +
            "b20waAYDVR0fBGEwXzBdoFugWYZXaHR0cDovL3d3dy5taWNyb3NvZnQuY29tL3Br\n" +
            "aW9wcy9jcmwvTWljcm9zb2Z0JTIwUlNBJTIwVExTJTIwSXNzdWluZyUyMEFPQyUy\n" +
            "MENBJTIwMDEuY3JsMGYGA1UdIARfMF0wUQYMKwYBBAGCN0yDfQEBMEEwPwYIKwYB\n" +
            "BQUHAgEWM2h0dHA6Ly93d3cubWljcm9zb2Z0LmNvbS9wa2lvcHMvRG9jcy9SZXBv\n" +
            "c2l0b3J5Lmh0bTAIBgZngQwBAgIwHwYDVR0jBBgwFoAU60wxfD0/MriD18Xbe9rk\n" +
            "eNqcFFcwEwYDVR0lBAwwCgYIKwYBBQUHAwEwDQYJKoZIhvcNAQEMBQADggIBAFHb\n" +
            "lQDG/Jk+kOLRWlZiya00OkZiXrueh4NgfwybZAx3O344+FzP4TsneUys16GO7Pti\n" +
            "UTKkxF42INw/3TAC4iOMg4RS4dm+Fn1G7xM59lwqnZLn48a6jORKwZIG0H/2Fevr\n" +
            "bGn3ZcTw+NP02OA7X1/ewRfljDZfHNmzdVTSVlqzhliv2cRuZyk7lf1LoIXBTz3Y\n" +
            "6ofOjgsP05XEZmMxMwM40FVeslTfuu301plj5KuHpQfbSny0VES3DQnZi+gHX+Zn\n" +
            "XuIYQL9stePqQr1GJBqAHM4sRgUCnW5t8efIYDMpYhQynXbniowLGbXOa0OP1IFG\n" +
            "oGmhPRonR1aJ2eFBfe0pnc4WO5qdiXQp/XWWYmUJaD7SdGDQF7wH9BUJdldIk6uI\n" +
            "SGTh4YD2VAXAGH4e9wHI5t9Lyah/VeBoLU1j3SsJfL6XfcWCwFG2sdqFFQHcONBl\n" +
            "ApIjebH4RlOGiRRRJ5/Wz9Wk850mEvF16UlB1MUpLiKU63/nJvuR1TvOisAUl+5L\n" +
            "oAfBFVkX4IGJU+9tc4VXYvTpd24xLHk/o6Fnl23D6zWlsZKldNxYPhiriXN9Duvb\n" +
            "6xmaQX4gua6jmTFUhKDyyVJpW1A4GjuenPYsCmabzydiAeMIQirCCLSTqXrSw1YL\n" +
            "2+608l1nqYy1JOrSq/zFp3c5buSFbjj7jVJB5LEh\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator) throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Fri Oct 14 15:46:18 PDT 2022", System.out);
    }
}
