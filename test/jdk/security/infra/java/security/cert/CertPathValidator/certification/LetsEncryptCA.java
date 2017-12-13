/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Interoperability tests with Let's Encrypt CA
 * @build ValidatePathWithParams
 * @run main/othervm -Djava.security.debug=certpath LetsEncryptCA OCSP
 * @run main/othervm -Djava.security.debug=certpath LetsEncryptCA CRL
 */

 /*
 * "Lets Encrypt Authority X1" intermediate CA is retired.
 * Test certs should be chained through "Lets Encrypt Authority X3" CA.
 *
 * Obtain TLS test artifacts for Let's Encrypt CA from:
 *
 * Valid TLS Certificates:
 * https://valid-isrgrootx1.letsencrypt.org/
 *
 * Revoked TLS Certificates:
 * https://revoked-isrgrootx1.letsencrypt.org/
 *
 * Test artifacts don't have CRLs listed.
 */
public class LetsEncryptCA {

    // Owner: CN=Let's Encrypt Authority X3, O=Let's Encrypt, C=US
    // Issuer: CN=ISRG Root X1, O=Internet Security Research Group, C=US
    private static final String INT = "-----BEGIN CERTIFICATE-----\n"
            + "MIIFjTCCA3WgAwIBAgIRANOxciY0IzLc9AUoUSrsnGowDQYJKoZIhvcNAQELBQAw\n"
            + "TzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh\n"
            + "cmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwHhcNMTYxMDA2MTU0MzU1\n"
            + "WhcNMjExMDA2MTU0MzU1WjBKMQswCQYDVQQGEwJVUzEWMBQGA1UEChMNTGV0J3Mg\n"
            + "RW5jcnlwdDEjMCEGA1UEAxMaTGV0J3MgRW5jcnlwdCBBdXRob3JpdHkgWDMwggEi\n"
            + "MA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCc0wzwWuUuR7dyXTeDs2hjMOrX\n"
            + "NSYZJeG9vjXxcJIvt7hLQQWrqZ41CFjssSrEaIcLo+N15Obzp2JxunmBYB/XkZqf\n"
            + "89B4Z3HIaQ6Vkc/+5pnpYDxIzH7KTXcSJJ1HG1rrueweNwAcnKx7pwXqzkrrvUHl\n"
            + "Npi5y/1tPJZo3yMqQpAMhnRnyH+lmrhSYRQTP2XpgofL2/oOVvaGifOFP5eGr7Dc\n"
            + "Gu9rDZUWfcQroGWymQQ2dYBrrErzG5BJeC+ilk8qICUpBMZ0wNAxzY8xOJUWuqgz\n"
            + "uEPxsR/DMH+ieTETPS02+OP88jNquTkxxa/EjQ0dZBYzqvqEKbbUC8DYfcOTAgMB\n"
            + "AAGjggFnMIIBYzAOBgNVHQ8BAf8EBAMCAYYwEgYDVR0TAQH/BAgwBgEB/wIBADBU\n"
            + "BgNVHSAETTBLMAgGBmeBDAECATA/BgsrBgEEAYLfEwEBATAwMC4GCCsGAQUFBwIB\n"
            + "FiJodHRwOi8vY3BzLnJvb3QteDEubGV0c2VuY3J5cHQub3JnMB0GA1UdDgQWBBSo\n"
            + "SmpjBH3duubRObemRWXv86jsoTAzBgNVHR8ELDAqMCigJqAkhiJodHRwOi8vY3Js\n"
            + "LnJvb3QteDEubGV0c2VuY3J5cHQub3JnMHIGCCsGAQUFBwEBBGYwZDAwBggrBgEF\n"
            + "BQcwAYYkaHR0cDovL29jc3Aucm9vdC14MS5sZXRzZW5jcnlwdC5vcmcvMDAGCCsG\n"
            + "AQUFBzAChiRodHRwOi8vY2VydC5yb290LXgxLmxldHNlbmNyeXB0Lm9yZy8wHwYD\n"
            + "VR0jBBgwFoAUebRZ5nu25eQBc4AIiMgaWPbpm24wDQYJKoZIhvcNAQELBQADggIB\n"
            + "ABnPdSA0LTqmRf/Q1eaM2jLonG4bQdEnqOJQ8nCqxOeTRrToEKtwT++36gTSlBGx\n"
            + "A/5dut82jJQ2jxN8RI8L9QFXrWi4xXnA2EqA10yjHiR6H9cj6MFiOnb5In1eWsRM\n"
            + "UM2v3e9tNsCAgBukPHAg1lQh07rvFKm/Bz9BCjaxorALINUfZ9DD64j2igLIxle2\n"
            + "DPxW8dI/F2loHMjXZjqG8RkqZUdoxtID5+90FgsGIfkMpqgRS05f4zPbCEHqCXl1\n"
            + "eO5HyELTgcVlLXXQDgAWnRzut1hFJeczY1tjQQno6f6s+nMydLN26WuU4s3UYvOu\n"
            + "OsUxRlJu7TSRHqDC3lSE5XggVkzdaPkuKGQbGpny+01/47hfXXNB7HntWNZ6N2Vw\n"
            + "p7G6OfY+YQrZwIaQmhrIqJZuigsrbe3W+gdn5ykE9+Ky0VgVUsfxo52mwFYs1JKY\n"
            + "2PGDuWx8M6DlS6qQkvHaRUo0FMd8TsSlbF0/v965qGFKhSDeQoMpYnwcmQilRh/0\n"
            + "ayLThlHLN81gSkJjVrPI0Y8xCVPB4twb1PFUd2fPM3sA1tJ83sZ5v8vgFv2yofKR\n"
            + "PB0t6JzUA81mSqM3kxl5e+IZwhYAyO0OTg3/fs8HqGTNKd9BqoUwSRBzp06JMg5b\n"
            + "rUCGwbCUDI0mxadJ3Bz4WxR6fyNpBK2yAinWEsikxqEt\n"
            + "-----END CERTIFICATE-----";

    // Owner: CN=valid-isrgrootx1.letsencrypt.org
    // Issuer: CN=Let's Encrypt Authority X3, O=Let's Encrypt, C=US
    // Serial number: 36916d6db9151ad4428d458a32eae518671
    // Valid from: Wed Nov 08 07:00:24 PST 2017 until: Tue Feb 06 07:00:24 PST 2018
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n"
            + "MIIFIzCCBAugAwIBAgISA2kW1tuRUa1EKNRYoy6uUYZxMA0GCSqGSIb3DQEBCwUA\n"
            + "MEoxCzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1MZXQncyBFbmNyeXB0MSMwIQYDVQQD\n"
            + "ExpMZXQncyBFbmNyeXB0IEF1dGhvcml0eSBYMzAeFw0xNzExMDgxNTAwMjRaFw0x\n"
            + "ODAyMDYxNTAwMjRaMCsxKTAnBgNVBAMTIHZhbGlkLWlzcmdyb290eDEubGV0c2Vu\n"
            + "Y3J5cHQub3JnMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyugIOCxl\n"
            + "4p0Rrs4aggnzKGYezhMyyvqlBgVBkf3DJV5uHbz/B/CxcoFo2rZzIetJEsb7Qnt1\n"
            + "U8L2O5BKnBeOsI5eFv6WUAQs96VayQ09+xCV3jSNjVpbmKKp1TNWboF/V+EDFq6f\n"
            + "fxK9h+b88RhBn4gfe+BorPnVTmZZQHgcZCjMGyzlXt68r45dXmZOuh0855Y7z6Et\n"
            + "wCHTT8k/7VC0DTIs0+veKv+yblUqwGD0htdOh7POkQGfBeJ432FsCCcLCDjg2Jj2\n"
            + "oYQNpLao55ZnVJGXfP8dJpHqJvuEQVuNT1TbHTs4x7IMftqGcPuhXKhA5FCVf0Hb\n"
            + "osbVmZ/b2b/WswIDAQABo4ICIDCCAhwwDgYDVR0PAQH/BAQDAgWgMB0GA1UdJQQW\n"
            + "MBQGCCsGAQUFBwMBBggrBgEFBQcDAjAMBgNVHRMBAf8EAjAAMB0GA1UdDgQWBBQZ\n"
            + "Mod3QzNPUL56tDMtELpCiwkQOTAfBgNVHSMEGDAWgBSoSmpjBH3duubRObemRWXv\n"
            + "86jsoTBvBggrBgEFBQcBAQRjMGEwLgYIKwYBBQUHMAGGImh0dHA6Ly9vY3NwLmlu\n"
            + "dC14My5sZXRzZW5jcnlwdC5vcmcwLwYIKwYBBQUHMAKGI2h0dHA6Ly9jZXJ0Lmlu\n"
            + "dC14My5sZXRzZW5jcnlwdC5vcmcvMCsGA1UdEQQkMCKCIHZhbGlkLWlzcmdyb290\n"
            + "eDEubGV0c2VuY3J5cHQub3JnMIH+BgNVHSAEgfYwgfMwCAYGZ4EMAQIBMIHmBgsr\n"
            + "BgEEAYLfEwEBATCB1jAmBggrBgEFBQcCARYaaHR0cDovL2Nwcy5sZXRzZW5jcnlw\n"
            + "dC5vcmcwgasGCCsGAQUFBwICMIGeDIGbVGhpcyBDZXJ0aWZpY2F0ZSBtYXkgb25s\n"
            + "eSBiZSByZWxpZWQgdXBvbiBieSBSZWx5aW5nIFBhcnRpZXMgYW5kIG9ubHkgaW4g\n"
            + "YWNjb3JkYW5jZSB3aXRoIHRoZSBDZXJ0aWZpY2F0ZSBQb2xpY3kgZm91bmQgYXQg\n"
            + "aHR0cHM6Ly9sZXRzZW5jcnlwdC5vcmcvcmVwb3NpdG9yeS8wDQYJKoZIhvcNAQEL\n"
            + "BQADggEBAFBiwKeCZfIh8a7x0Y5QEqGwejil/BY6MOVuIU9FRIJKmhJGdh6lI6ln\n"
            + "zlBbMZBAjZ+TqDxU0pvM1AsRDyCqt8GbCAC2xQsGyATLdCjedLQ7U7ORm7pBZdbe\n"
            + "cT7h9Sblj53o5MKa1yFeS89WGjI4UueUemGxp7EQjat0NeAvbnpU+YmuevNYKX2M\n"
            + "kK33reMC+rgD+wKet1CXcB/ZYl3fDzVH3SwkT/bKW5bsuwxBuD2noScnKCitRgiv\n"
            + "Ew7YjwqNOm2naki/xr2sfJirR+lJtZ9KC3H8xWeEHrD8Cf7pnmMYqV59uR+hJwMP\n"
            + "YsjjDbDFCmNN9FBqDwvXs7g86ttkdC8=\n"
            + "-----END CERTIFICATE-----";

    // Owner: CN=revoked-isrgrootx1.letsencrypt.org
    // Issuer: CN=Let's Encrypt Authority X3, O=Let's Encrypt, C=US
    // Serial number: 3ddd39c0755648d6687a5d8ded37775657e
    // Valid from: Wed Nov 08 07:00:32 PST 2017 until: Tue Feb 06 07:00:32 PST 2018
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n"
            + "MIIFJzCCBA+gAwIBAgISA93TnAdVZI1mh6XY3tN3dWV+MA0GCSqGSIb3DQEBCwUA\n"
            + "MEoxCzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1MZXQncyBFbmNyeXB0MSMwIQYDVQQD\n"
            + "ExpMZXQncyBFbmNyeXB0IEF1dGhvcml0eSBYMzAeFw0xNzExMDgxNTAwMzJaFw0x\n"
            + "ODAyMDYxNTAwMzJaMC0xKzApBgNVBAMTInJldm9rZWQtaXNyZ3Jvb3R4MS5sZXRz\n"
            + "ZW5jcnlwdC5vcmcwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQC5qlZ0\n"
            + "jslNLn/1uICdZPwflcvsoA2S2Nk+O7cPNew+KQmSIf+LK9AbaWHCkABKx1GdMtfN\n"
            + "4Q/nKBtzqZ5jX1V1XbPqPd1eeyJo0rNaDFk/gEUHw/zIYi1AtsxVHztMqOXRcsw+\n"
            + "6QHRKU2XFVsfSctMv+MKnMTEJZARyhr5ur9bQ4/LmxPMhrlHAst97hiSsXKXeyMK\n"
            + "DWPHmUDn1vz/1mwLMaeYYmuhuRP5HNwYq+LdYvjMV580i6LHY72TwQCfVgOHfqI0\n"
            + "larISk2p4q6DmTEEiAzJB3yEYaxDn0kEXbKhL9efDC+eirVFa0ta2OnH87s9L8z9\n"
            + "fm9JIiSFM9ATQ16/AgMBAAGjggIiMIICHjAOBgNVHQ8BAf8EBAMCBaAwHQYDVR0l\n"
            + "BBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMCMAwGA1UdEwEB/wQCMAAwHQYDVR0OBBYE\n"
            + "FP64lxiV8KwkkzoNaM7iuwX8UBG/MB8GA1UdIwQYMBaAFKhKamMEfd265tE5t6ZF\n"
            + "Ze/zqOyhMG8GCCsGAQUFBwEBBGMwYTAuBggrBgEFBQcwAYYiaHR0cDovL29jc3Au\n"
            + "aW50LXgzLmxldHNlbmNyeXB0Lm9yZzAvBggrBgEFBQcwAoYjaHR0cDovL2NlcnQu\n"
            + "aW50LXgzLmxldHNlbmNyeXB0Lm9yZy8wLQYDVR0RBCYwJIIicmV2b2tlZC1pc3Jn\n"
            + "cm9vdHgxLmxldHNlbmNyeXB0Lm9yZzCB/gYDVR0gBIH2MIHzMAgGBmeBDAECATCB\n"
            + "5gYLKwYBBAGC3xMBAQEwgdYwJgYIKwYBBQUHAgEWGmh0dHA6Ly9jcHMubGV0c2Vu\n"
            + "Y3J5cHQub3JnMIGrBggrBgEFBQcCAjCBngyBm1RoaXMgQ2VydGlmaWNhdGUgbWF5\n"
            + "IG9ubHkgYmUgcmVsaWVkIHVwb24gYnkgUmVseWluZyBQYXJ0aWVzIGFuZCBvbmx5\n"
            + "IGluIGFjY29yZGFuY2Ugd2l0aCB0aGUgQ2VydGlmaWNhdGUgUG9saWN5IGZvdW5k\n"
            + "IGF0IGh0dHBzOi8vbGV0c2VuY3J5cHQub3JnL3JlcG9zaXRvcnkvMA0GCSqGSIb3\n"
            + "DQEBCwUAA4IBAQCBiokogdgIZxwuPSr43S4GZ9FwrpZNMHADMEZB8ykuotJBGyr1\n"
            + "QLWDVeoAJ8OIi1AzjcdwKFQks/MKUJwxJ9hYmm9aM14d5lMKGTyoLSI/Z/Vrpx8w\n"
            + "0GpktSK0WfPeLBHuSpMdrIMWyziSu/bdZtiOIIvMasFwyRhDgII++CIdsnboWXF+\n"
            + "DZcwy0Yd6XzirXuwENwaWrkrbZPr/JB0xLFmydqXAnA1VFTudwL87q4CTlEo8EiD\n"
            + "ucKZ/vAhD+ip3/kQFXg90om+9TdHo8D8GxTC1CLZteJt+nqWFRj0e/7eCXIZuUBE\n"
            + "aSsFCd5RNTHs6tioN9vYJqLojObgF75MgIAC\n"
            + "-----END CERTIFICATE-----";

    public static void main(String[] args) throws Exception {

        ValidatePathWithParams pathValidator = new ValidatePathWithParams(null);

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            pathValidator.enableCRLCheck();

            // Validate int, EE certs don't have CRLs
            pathValidator.validate(new String[]{INT},
                    ValidatePathWithParams.Status.GOOD, null, System.out);

            return;
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
                "Wed Nov 08 08:00:35 PST 2017", System.out);

    }
}
