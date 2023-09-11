/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8233223
 * @summary Interoperability tests with Amazon's CA1, CA2, CA3, and CA4
 * @build ValidatePathWithParams
 * @run main/othervm -Djava.security.debug=certpath AmazonCA OCSP
 * @run main/othervm -Djava.security.debug=certpath AmazonCA CRL
 */

/*
 * Obtain TLS test artifacts for Amazon CAs from:
 *
 * Amazon Root CA 1
 *     Valid - https://good.sca1a.amazontrust.com/
 *     Revoked - https://revoked.sca1a.amazontrust.com/
 * Amazon Root CA 2
 *     Valid - https://good.sca2a.amazontrust.com/
 *     Revoked - https://revoked.sca2a.amazontrust.com/
 * Amazon Root CA 3
 *     Valid - https://good.sca3a.amazontrust.com/
 *     Revoked - https://revoked.sca3a.amazontrust.com/
 * Amazon Root CA 4
 *     Valid - https://good.sca4a.amazontrust.com/
 *     Revoked - https://revoked.sca4a.amazontrust.com/
 */
public class AmazonCA {

    public static void main(String[] args) throws Exception {

        ValidatePathWithParams pathValidator = new ValidatePathWithParams(null);

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            pathValidator.enableCRLCheck();
        } else {
            // OCSP check by default
            pathValidator.enableOCSPCheck();
        }

        new AmazonCA_1().runTest(pathValidator);
        new AmazonCA_2().runTest(pathValidator);
        new AmazonCA_3().runTest(pathValidator);
        new AmazonCA_4().runTest(pathValidator);
    }
}

class AmazonCA_1 {

    // Owner: CN=Amazon RSA 2048 M02, O=Amazon, C=US
    // Issuer: CN=Amazon Root CA 1, O=Amazon, C=US
    // Serial number: 773124a4bcbd44ec7b53beaf194842d3a0fa1
    // Valid from: Tue Aug 23 15:25:30 PDT 2022 until: Fri Aug 23 15:25:30 PDT 2030
    private static final String INT_VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIEXjCCA0agAwIBAgITB3MSSkvL1E7HtTvq8ZSELToPoTANBgkqhkiG9w0BAQsF\n" +
            "ADA5MQswCQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMRkwFwYDVQQDExBBbWF6\n" +
            "b24gUm9vdCBDQSAxMB4XDTIyMDgyMzIyMjUzMFoXDTMwMDgyMzIyMjUzMFowPDEL\n" +
            "MAkGA1UEBhMCVVMxDzANBgNVBAoTBkFtYXpvbjEcMBoGA1UEAxMTQW1hem9uIFJT\n" +
            "QSAyMDQ4IE0wMjCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALtDGMZa\n" +
            "qHneKei1by6+pUPPLljTB143Si6VpEWPc6mSkFhZb/6qrkZyoHlQLbDYnI2D7hD0\n" +
            "sdzEqfnuAjIsuXQLG3A8TvX6V3oFNBFVe8NlLJHvBseKY88saLwufxkZVwk74g4n\n" +
            "WlNMXzla9Y5F3wwRHwMVH443xGz6UtGSZSqQ94eFx5X7Tlqt8whi8qCaKdZ5rNak\n" +
            "+r9nUThOeClqFd4oXych//Rc7Y0eX1KNWHYSI1Nk31mYgiK3JvH063g+K9tHA63Z\n" +
            "eTgKgndlh+WI+zv7i44HepRZjA1FYwYZ9Vv/9UkC5Yz8/yU65fgjaE+wVHM4e/Yy\n" +
            "C2osrPWE7gJ+dXMCAwEAAaOCAVowggFWMBIGA1UdEwEB/wQIMAYBAf8CAQAwDgYD\n" +
            "VR0PAQH/BAQDAgGGMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjAdBgNV\n" +
            "HQ4EFgQUwDFSzVpQw4J8dHHOy+mc+XrrguIwHwYDVR0jBBgwFoAUhBjMhTTsvAyU\n" +
            "lC4IWZzHshBOCggwewYIKwYBBQUHAQEEbzBtMC8GCCsGAQUFBzABhiNodHRwOi8v\n" +
            "b2NzcC5yb290Y2ExLmFtYXpvbnRydXN0LmNvbTA6BggrBgEFBQcwAoYuaHR0cDov\n" +
            "L2NydC5yb290Y2ExLmFtYXpvbnRydXN0LmNvbS9yb290Y2ExLmNlcjA/BgNVHR8E\n" +
            "ODA2MDSgMqAwhi5odHRwOi8vY3JsLnJvb3RjYTEuYW1hem9udHJ1c3QuY29tL3Jv\n" +
            "b3RjYTEuY3JsMBMGA1UdIAQMMAowCAYGZ4EMAQIBMA0GCSqGSIb3DQEBCwUAA4IB\n" +
            "AQAtTi6Fs0Azfi+iwm7jrz+CSxHH+uHl7Law3MQSXVtR8RV53PtR6r/6gNpqlzdo\n" +
            "Zq4FKbADi1v9Bun8RY8D51uedRfjsbeodizeBB8nXmeyD33Ep7VATj4ozcd31YFV\n" +
            "fgRhvTSxNrrTlNpWkUk0m3BMPv8sg381HhA6uEYokE5q9uws/3YkKqRiEz3TsaWm\n" +
            "JqIRZhMbgAfp7O7FUwFIb7UIspogZSKxPIWJpxiPo3TcBambbVtQOcNRWz5qCQdD\n" +
            "slI2yayq0n2TXoHyNCLEH8rpsJRVILFsg0jc7BaFrMnF462+ajSehgj12IidNeRN\n" +
            "4zl+EoNaWdpnWndvSpAEkq2P\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=Amazon RSA 2048 M01, O=Amazon, C=US
    // Issuer: CN=Amazon Root CA 1, O=Amazon, C=US
    // Serial number: 77312380b9d6688a33b1ed9bf9ccda68e0e0f
    // Valid from: Tue Aug 23 15:21:28 PDT 2022 until: Fri Aug 23 15:21:28 PDT 2030
    private static final String INT_REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIEXjCCA0agAwIBAgITB3MSOAudZoijOx7Zv5zNpo4ODzANBgkqhkiG9w0BAQsF\n" +
            "ADA5MQswCQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMRkwFwYDVQQDExBBbWF6\n" +
            "b24gUm9vdCBDQSAxMB4XDTIyMDgyMzIyMjEyOFoXDTMwMDgyMzIyMjEyOFowPDEL\n" +
            "MAkGA1UEBhMCVVMxDzANBgNVBAoTBkFtYXpvbjEcMBoGA1UEAxMTQW1hem9uIFJT\n" +
            "QSAyMDQ4IE0wMTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAOtxLKnL\n" +
            "H4gokjIwr4pXD3i3NyWVVYesZ1yX0yLI2qIUZ2t88Gfa4gMqs1YSXca1R/lnCKeT\n" +
            "epWSGA+0+fkQNpp/L4C2T7oTTsddUx7g3ZYzByDTlrwS5HRQQqEFE3O1T5tEJP4t\n" +
            "f+28IoXsNiEzl3UGzicYgtzj2cWCB41eJgEmJmcf2T8TzzK6a614ZPyq/w4CPAff\n" +
            "nAV4coz96nW3AyiE2uhuB4zQUIXvgVSycW7sbWLvj5TDXunEpNCRwC4kkZjK7rol\n" +
            "jtT2cbb7W2s4Bkg3R42G3PLqBvt2N32e/0JOTViCk8/iccJ4sXqrS1uUN4iB5Nmv\n" +
            "JK74csVl+0u0UecCAwEAAaOCAVowggFWMBIGA1UdEwEB/wQIMAYBAf8CAQAwDgYD\n" +
            "VR0PAQH/BAQDAgGGMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjAdBgNV\n" +
            "HQ4EFgQUgbgOY4qJEhjl+js7UJWf5uWQE4UwHwYDVR0jBBgwFoAUhBjMhTTsvAyU\n" +
            "lC4IWZzHshBOCggwewYIKwYBBQUHAQEEbzBtMC8GCCsGAQUFBzABhiNodHRwOi8v\n" +
            "b2NzcC5yb290Y2ExLmFtYXpvbnRydXN0LmNvbTA6BggrBgEFBQcwAoYuaHR0cDov\n" +
            "L2NydC5yb290Y2ExLmFtYXpvbnRydXN0LmNvbS9yb290Y2ExLmNlcjA/BgNVHR8E\n" +
            "ODA2MDSgMqAwhi5odHRwOi8vY3JsLnJvb3RjYTEuYW1hem9udHJ1c3QuY29tL3Jv\n" +
            "b3RjYTEuY3JsMBMGA1UdIAQMMAowCAYGZ4EMAQIBMA0GCSqGSIb3DQEBCwUAA4IB\n" +
            "AQCtAN4CBSMuBjJitGuxlBbkEUDeK/pZwTXv4KqPK0G50fOHOQAd8j21p0cMBgbG\n" +
            "kfMHVwLU7b0XwZCav0h1ogdPMN1KakK1DT0VwA/+hFvGPJnMV1Kx2G4S1ZaSk0uU\n" +
            "5QfoiYIIano01J5k4T2HapKQmmOhS/iPtuo00wW+IMLeBuKMn3OLn005hcrOGTad\n" +
            "hcmeyfhQP7Z+iKHvyoQGi1C0ClymHETx/chhQGDyYSWqB/THwnN15AwLQo0E5V9E\n" +
            "SJlbe4mBlqeInUsNYugExNf+tOiybcrswBy8OFsd34XOW3rjSUtsuafd9AWySa3h\n" +
            "xRRrwszrzX/WWGm6wyB+f7C4\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=valid.rootca1.demo.amazontrust.com
    // Issuer: CN=Amazon RSA 2048 M02, O=Amazon, C=US
    // Serial number: 60c6e837b2e7586d8464eb34f4a85fe
    // Valid from: Tue May 09 17:00:00 PDT 2023 until: Fri Jun 07 16:59:59 PDT 2024
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGKDCCBRCgAwIBAgIQBgxug3sudYbYRk6zT0qF/jANBgkqhkiG9w0BAQsFADA8\n" +
            "MQswCQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMRwwGgYDVQQDExNBbWF6b24g\n" +
            "UlNBIDIwNDggTTAyMB4XDTIzMDUxMDAwMDAwMFoXDTI0MDYwNzIzNTk1OVowLTEr\n" +
            "MCkGA1UEAxMidmFsaWQucm9vdGNhMS5kZW1vLmFtYXpvbnRydXN0LmNvbTCCASIw\n" +
            "DQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAL3hA+omhUcO8nYO8/+dkpbYz8WI\n" +
            "1ms7Y7JA2pPFfp2N/aWcf6m5ORm1BkyGLOttjTu318Qpa9eahQ1Pi3RNe3BtqjD9\n" +
            "jcHncpwAFMsXy1beZA7sZ7AA4vKltA3t6yrU5ruTLUGQwUndeIBBSTW5QpdT9I/p\n" +
            "EM7d+Miwre63kofbJ1lVPAJvN/udMVqGWNF8V5qscklUUHoSKA3FWWsiCyIgnthg\n" +
            "G3u6R1KH66Qionp0ho/ttvrBCI0C/bdrdH+wybFv8oFFvAW2U9xn2Azt47/2kHHm\n" +
            "tTRjrgufhDbcz/MLR6hwBXAJuwVvJZmSqe7B4IILFexu6wjxZfyqVm2FMr8CAwEA\n" +
            "AaOCAzMwggMvMB8GA1UdIwQYMBaAFMAxUs1aUMOCfHRxzsvpnPl664LiMB0GA1Ud\n" +
            "DgQWBBSkrnsTnjwYhDRAeLy/9FXm/7hApDBlBgNVHREEXjBcgiJ2YWxpZC5yb290\n" +
            "Y2ExLmRlbW8uYW1hem9udHJ1c3QuY29tghpnb29kLnNjYTBhLmFtYXpvbnRydXN0\n" +
            "LmNvbYIaZ29vZC5zY2ExYS5hbWF6b250cnVzdC5jb20wDgYDVR0PAQH/BAQDAgWg\n" +
            "MB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjA7BgNVHR8ENDAyMDCgLqAs\n" +
            "hipodHRwOi8vY3JsLnIybTAyLmFtYXpvbnRydXN0LmNvbS9yMm0wMi5jcmwwEwYD\n" +
            "VR0gBAwwCjAIBgZngQwBAgEwdQYIKwYBBQUHAQEEaTBnMC0GCCsGAQUFBzABhiFo\n" +
            "dHRwOi8vb2NzcC5yMm0wMi5hbWF6b250cnVzdC5jb20wNgYIKwYBBQUHMAKGKmh0\n" +
            "dHA6Ly9jcnQucjJtMDIuYW1hem9udHJ1c3QuY29tL3IybTAyLmNlcjAMBgNVHRMB\n" +
            "Af8EAjAAMIIBfgYKKwYBBAHWeQIEAgSCAW4EggFqAWgAdgDuzdBk1dsazsVct520\n" +
            "zROiModGfLzs3sNRSFlGcR+1mwAAAYgHvXWVAAAEAwBHMEUCICAs74qT1f9ufSr5\n" +
            "PgQqtQFiXBbmbb3i4xwVV78USU5NAiEA/iJEfnTG+hZZaHYv2wVbg6tUY8fQgIhI\n" +
            "2rbl6PrD9FIAdgBIsONr2qZHNA/lagL6nTDrHFIBy1bdLIHZu7+rOdiEcwAAAYgH\n" +
            "vXWWAAAEAwBHMEUCIQDf2nWyee/5+vSgk/O8P0BFvXYu89cyAugZHyd919BdAgIg\n" +
            "UnGGpQtZmWnPMmdgpzI7jrCLuC370Tn0i7Aktdzj2X8AdgDatr9rP7W2Ip+bwrtc\n" +
            "a+hwkXFsu1GEhTS9pD0wSNf7qwAAAYgHvXVpAAAEAwBHMEUCIGN6cT+6uwDospXe\n" +
            "gMa8b38oXouXUT66X2gOiJ0SoRyQAiEAjDMu2vEll5tRpUvU8cD4gR2xV4hqoDxx\n" +
            "Q+QGW+PvJxcwDQYJKoZIhvcNAQELBQADggEBACtxC3LlQvULeI3lt7ZYFSWndEhm\n" +
            "tNUotoeKSXJXdoIpqSr10bzMPX9SHvemgOUtzP3JNqWPHw1uW9YFyeDE6yWj/B13\n" +
            "Xj1hv1cqYIwyaOZBerU/9PT5PaCn20AC9DHbc7iBv+zs+DYiqlAFJ1GVaprwLul4\n" +
            "8wp3gnC3Hjb8NykydCo6vw0AJ2UzjpjiTyVZ93jITzLOiboOUa1gQGnojzWlYaet\n" +
            "sXe+RDylBp/Wuj1ZS7v/etltzYm5GanPi4y/p7Ta3Uky6std/GM6XbPRdBEFboFR\n" +
            "B2IP0divd9c74Q+tLgpsAz5yXm9LtYPMcEPC2YRN2PgBg67c5+A7eIOluuw=\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=revoked.rootca1.demo.amazontrust.com
    // Issuer: CN=Amazon RSA 2048 M01, O=Amazon, C=US
    // Serial number: e1023665b1268d788cc25bf69a9d05e
    // Valid from: Tue May 09 17:00:00 PDT 2023 until: Fri Jun 07 16:59:59 PDT 2024
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGMjCCBRqgAwIBAgIQDhAjZlsSaNeIzCW/aanQXjANBgkqhkiG9w0BAQsFADA8\n" +
            "MQswCQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMRwwGgYDVQQDExNBbWF6b24g\n" +
            "UlNBIDIwNDggTTAxMB4XDTIzMDUxMDAwMDAwMFoXDTI0MDYwNzIzNTk1OVowLzEt\n" +
            "MCsGA1UEAxMkcmV2b2tlZC5yb290Y2ExLmRlbW8uYW1hem9udHJ1c3QuY29tMIIB\n" +
            "IjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxSPd1PWACxZohFCAJT1JWuXK\n" +
            "GY29wZZ9yY0zoiq6+qYiUIU0crktytUNNI1ZpW/3qXpEw2ZQkM6WF1LshXtwGwrA\n" +
            "zJwSeX1L9T5rOKhoBvoFeqfX7xu4VBM1/fDGt5X+NRFfD9Op9UfK5OsnL05TYach\n" +
            "rdnfOA5wKGvMgFiN5CeOD0AtumXSuAnTZC85ojJTHjPF+hqV893WvrrUxLyyxtvh\n" +
            "lq/WttFOjhfQu2IkfyDAFiH939uzUi0WSTAdsbsHuko5mDTDnOfMRbaaWZu0At01\n" +
            "EgaIPeK+kGdi7EYwVndIwTKLeQ4mjIM8aj8Heg/y2hZ0kOmfCUZdUmJFlNoCIQID\n" +
            "AQABo4IDOzCCAzcwHwYDVR0jBBgwFoAUgbgOY4qJEhjl+js7UJWf5uWQE4UwHQYD\n" +
            "VR0OBBYEFMeBhIOkuWUY4DYqFrfgbD2eUeFtMG0GA1UdEQRmMGSCJHJldm9rZWQu\n" +
            "cm9vdGNhMS5kZW1vLmFtYXpvbnRydXN0LmNvbYIdcmV2b2tlZC5zY2EwYS5hbWF6\n" +
            "b250cnVzdC5jb22CHXJldm9rZWQuc2NhMWEuYW1hem9udHJ1c3QuY29tMA4GA1Ud\n" +
            "DwEB/wQEAwIFoDAdBgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwOwYDVR0f\n" +
            "BDQwMjAwoC6gLIYqaHR0cDovL2NybC5yMm0wMS5hbWF6b250cnVzdC5jb20vcjJt\n" +
            "MDEuY3JsMBMGA1UdIAQMMAowCAYGZ4EMAQIBMHUGCCsGAQUFBwEBBGkwZzAtBggr\n" +
            "BgEFBQcwAYYhaHR0cDovL29jc3AucjJtMDEuYW1hem9udHJ1c3QuY29tMDYGCCsG\n" +
            "AQUFBzAChipodHRwOi8vY3J0LnIybTAxLmFtYXpvbnRydXN0LmNvbS9yMm0wMS5j\n" +
            "ZXIwDAYDVR0TAQH/BAIwADCCAX4GCisGAQQB1nkCBAIEggFuBIIBagFoAHYA7s3Q\n" +
            "ZNXbGs7FXLedtM0TojKHRny87N7DUUhZRnEftZsAAAGIB72TggAABAMARzBFAiAZ\n" +
            "naLbRHRuaRrE304GSuWX/79MU/e+SSlr0cNJ0kNNaAIhAPnz9HayL4txhkTEZiMs\n" +
            "nttNnNqD17I0J17JLVOF4i/4AHYASLDja9qmRzQP5WoC+p0w6xxSActW3SyB2bu/\n" +
            "qznYhHMAAAGIB72TmwAABAMARzBFAiEAgEqT7CYGQ/u36/3YcxBH78QfknI9kgcY\n" +
            "sgJLkurUF6cCIFZZ/b803+ek6o+bmdV/uVx2UlskAyyolZ2okBAb6IscAHYA2ra/\n" +
            "az+1tiKfm8K7XGvocJFxbLtRhIU0vaQ9MEjX+6sAAAGIB72TbQAABAMARzBFAiEA\n" +
            "6z2RSoK263hvYF71rj1d0TpC70/6zagSRR4glHOT6IACICYvaMAnrCNSTSiZ20Wz\n" +
            "Ju5roTippO3BWKhQYrTKZuu4MA0GCSqGSIb3DQEBCwUAA4IBAQB4S1JGulFpMIaP\n" +
            "NtLUJmjWz8eexQdWLDVF+H8dd6xpZgpiYtig/Ynphzuk1IIF8DkT3CeK/9vrezgI\n" +
            "igNjneN9B4eIuzi/rJzIKeUwpZ2k5D+36Ab4esseoc+TopmNerw8hidt2g818jER\n" +
            "D71ppSMakeQFPGe/Hs2/cVa/G1DNVcU2XAut45yRZ/+xsZ0/mcBDVsG9P5uGCN5O\n" +
            "7SAp4J959WnKDqgVuU9WowPE5IjmS9BAv2gjniFYdDV2yksyf7+8edHd1KfSVX06\n" +
            "pLx6CuCVZGJFG4Q2Aa1YAh1Wvt9hqWeXXpNRO2/wChL5rhT4GajsrGepsk4bjxYX\n" +
            "Wf2iZ8mX\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator) throws Exception {

        // Validate valid
        pathValidator.validate(new String[]{VALID, INT_VALID},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT_REVOKED},
                ValidatePathWithParams.Status.REVOKED,
                "Mon May 15 13:36:57 PDT 2023", System.out);
    }
}

class AmazonCA_2 {

    // Owner: CN=Amazon RSA 4096 M02, O=Amazon, C=US
    // Issuer: CN=Amazon Root CA 2, O=Amazon, C=US
    // Serial number: 773125b0c34c3c940299a9f04a39e5a52ccd9
    // Valid from: Tue Aug 23 15:29:13 PDT 2022 until: Fri Aug 23 15:29:13 PDT 2030
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGXjCCBEagAwIBAgITB3MSWww0w8lAKZqfBKOeWlLM2TANBgkqhkiG9w0BAQwF\n" +
            "ADA5MQswCQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMRkwFwYDVQQDExBBbWF6\n" +
            "b24gUm9vdCBDQSAyMB4XDTIyMDgyMzIyMjkxM1oXDTMwMDgyMzIyMjkxM1owPDEL\n" +
            "MAkGA1UEBhMCVVMxDzANBgNVBAoTBkFtYXpvbjEcMBoGA1UEAxMTQW1hem9uIFJT\n" +
            "QSA0MDk2IE0wMjCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIBAMGMl/pZ\n" +
            "1OsxHY9gw/YfdON4mmrANkPwi7z2djHA5ELt/vRI3Su0le6OoipLf03iyoCnYy4Y\n" +
            "rpfTbhyDriE8NJpps2ODJ5W1h0rz6FM1Q5Jt35wfk+4CEfATBTegHVlUJ0rJgzK5\n" +
            "Yl/jrk12ZsC4ZeRn54shszcK6bHj4LZIHXhrYIIfetBMMD8V7hlhd54AclEWutUV\n" +
            "eBEjkSCzDSk+pQKIjCL0crqvRSPvUNry/BV65zfGmceSYxpcLmV7k7Spwpo+1z8w\n" +
            "+Odfnx2vsm7olPldfaThqk6fXBtInORl4Ef32xF3VDT13UeXtQPolFhnp8UOci64\n" +
            "bW+R8tbtGpUXIA8Dhr8SgYPH6NW4jhUD4+AG8yer8ctA1Hl9tq+6tYr26q3yuCLu\n" +
            "5rwJdfMG634fWIRXSj+GJi8SfAdGtPyXwu5799NWesV4vUkrkSXdIBK4TQCuK+jx\n" +
            "aJ5Y+Zo2l3GFsWyMPNORLjoQXbjF6KAyjTyICLq9VzoQKhyx4Ll2CNrQv8CxqtDC\n" +
            "GvXi9kREJYAF6lscOB0xglAAF5lndcaNkVHEVOMdg9ZZtdJywHWm8Qed1Wty2qr+\n" +
            "hmA7booWQNRE12nW1niC5D4cP2ykPK9HSgb7xWdUF32VidUc9tNKM6xKjSd/R/tP\n" +
            "p+XAybNSwEooPt3/OvyhpVRjLuWoqqbClTKdAgMBAAGjggFaMIIBVjASBgNVHRMB\n" +
            "Af8ECDAGAQH/AgEAMA4GA1UdDwEB/wQEAwIBhjAdBgNVHSUEFjAUBggrBgEFBQcD\n" +
            "AQYIKwYBBQUHAwIwHQYDVR0OBBYEFJ5xHxodk6nZLY7MSFM/A1TznuZmMB8GA1Ud\n" +
            "IwQYMBaAFLAM8Eww9AVYAkj9M+VSr0uE42ZSMHsGCCsGAQUFBwEBBG8wbTAvBggr\n" +
            "BgEFBQcwAYYjaHR0cDovL29jc3Aucm9vdGNhMi5hbWF6b250cnVzdC5jb20wOgYI\n" +
            "KwYBBQUHMAKGLmh0dHA6Ly9jcnQucm9vdGNhMi5hbWF6b250cnVzdC5jb20vcm9v\n" +
            "dGNhMi5jZXIwPwYDVR0fBDgwNjA0oDKgMIYuaHR0cDovL2NybC5yb290Y2EyLmFt\n" +
            "YXpvbnRydXN0LmNvbS9yb290Y2EyLmNybDATBgNVHSAEDDAKMAgGBmeBDAECATAN\n" +
            "BgkqhkiG9w0BAQwFAAOCAgEAl1GgKXOn0j1MWT1KJVSewQ28SGbie3UwZj1dMsjJ\n" +
            "amCrQPn2ngSNbLm9+ulFiBDU8xKR9Zx3tZps55IUKWLUPkfMC+vkV7asDBqqzzE0\n" +
            "F/MkekgPfOjx1V9S6Wfg3sSg+9KcluurXFElruqKfOm4cqmkV776X1G+AaaQ7mlU\n" +
            "giCYi6NqRQSyhn8zrKkNnbO6QL5a9ICC47kiZYRAR/hRvZOt11QUK5tCMXJXo0iO\n" +
            "4XKkMu+jdnehP1kh4xuZhYznIgKK6MJIITFI/Jj89U4SOPncyuS94sUuE2EqvvO/\n" +
            "t81qeoey6wThz5iRbU/0CvDFnTMgebWGUZ2UZJ+az/rb3KYXGfVWasLIonkvYT7z\n" +
            "vHOGNAA9oQ8TTgPOmPfSVyfpplKtO/aybWp5QSH2csIwuvw5dkmpkc42iD57XHob\n" +
            "5LbMJg99z3vQBmod/ipmOpND95/BeA2mllBZgZ53S0nvDXDzbzR9Fd81PAz9Qruo\n" +
            "dOJKcD6plKQjZjkLzNh1v/RoCFO8kiJGE4UBMTM8FUk0DXH4bALII4wwmDelrSUu\n" +
            "lKvDTDxZvPF4dbEXICNPd51EMGPgETxwboOV+bzWFVI0IWQ8PhZ2VuMPDk2taOMp\n" +
            "NsuLtlYc2twPb9r/Hvgv7G6+ItpBHZwOVt1oI3pHbjMp7P3pOZSPr6G1WkNy9mX8\n" +
            "rVc=\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=valid.rootca2.demo.amazontrust.com
    // Issuer: CN=Amazon RSA 4096 M02, O=Amazon, C=US
    // Serial number: 662f7646d76193cbb76946d111e49fa
    // Valid from: Tue May 09 17:00:00 PDT 2023 until: Fri Jun 07 16:59:59 PDT 2024
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIICzCCBfOgAwIBAgIQBmL3ZG12GTy7dpRtER5J+jANBgkqhkiG9w0BAQwFADA8\n" +
            "MQswCQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMRwwGgYDVQQDExNBbWF6b24g\n" +
            "UlNBIDQwOTYgTTAyMB4XDTIzMDUxMDAwMDAwMFoXDTI0MDYwNzIzNTk1OVowLTEr\n" +
            "MCkGA1UEAxMidmFsaWQucm9vdGNhMi5kZW1vLmFtYXpvbnRydXN0LmNvbTCCAiIw\n" +
            "DQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIBAON5EbEKoBiujI7Ja8mLZLJbaY7f\n" +
            "RtoWIjU/F0l9ueWFogXmEaA1jWsl97F3WTHTyGKz6ChCjPMSyoXXpY+yoE90QUyX\n" +
            "w35uWEhNrc40drMJkyN+QXitSrH346GCOKvpYVvu18UD4W8hDhg8vvbOQYhtmSf7\n" +
            "Rfrs7/qUdXpzpvR9VjWktbQAzJT8fB/jFNjNQJTknynjGiYO5GF51+peOCLK6qw8\n" +
            "9kKYEigR4K8/aWL283rC4xRxZqVioy433VG02l/Fwdv8o/vL9YYIqkyspCB9fpFw\n" +
            "Q50yYrwEomxuOz7rXhmdfeNaFYuyTtOUSKff6p2oqO0S7pcLujUVMlO4dYBDELQF\n" +
            "cabByNjwblviCtGKJMIzD6Thkgamp3iXQgcU498+P5r7N5CYbMmkJEdcuILg+bgJ\n" +
            "/LUUTT+IMt2txYlO/ld3N0EHlgVt7rztW5mtm6Ba8jN7cLSh7ZWu6Fr1+oK7bl5T\n" +
            "wPxSfqT5W3BwQKS3YptIoKEWUb+VNnS/dYx/7IspF9+z6kw4g+V2EY9M4ZYNakzM\n" +
            "AI7KIj4thMFoWeYrJq0dUMZ297QCBPRdAwh9hhkq2LYi2x8tMUtcBnhb/q75sO+E\n" +
            "icPqFVv7iMDZ/8Xep+0UoClF3JGmZW3UNtwcbi7Pn/OqtaMi7E8xnHUgc4ZchtXO\n" +
            "v8VtVvDeZAlY5TjVAgMBAAGjggMWMIIDEjAfBgNVHSMEGDAWgBSecR8aHZOp2S2O\n" +
            "zEhTPwNU857mZjAdBgNVHQ4EFgQUnGekBRKIZBYgCEajbpCMC24bp2owSQYDVR0R\n" +
            "BEIwQIIidmFsaWQucm9vdGNhMi5kZW1vLmFtYXpvbnRydXN0LmNvbYIaZ29vZC5z\n" +
            "Y2EyYS5hbWF6b250cnVzdC5jb20wDgYDVR0PAQH/BAQDAgWgMB0GA1UdJQQWMBQG\n" +
            "CCsGAQUFBwMBBggrBgEFBQcDAjA7BgNVHR8ENDAyMDCgLqAshipodHRwOi8vY3Js\n" +
            "LnI0bTAyLmFtYXpvbnRydXN0LmNvbS9yNG0wMi5jcmwwEwYDVR0gBAwwCjAIBgZn\n" +
            "gQwBAgEwdQYIKwYBBQUHAQEEaTBnMC0GCCsGAQUFBzABhiFodHRwOi8vb2NzcC5y\n" +
            "NG0wMi5hbWF6b250cnVzdC5jb20wNgYIKwYBBQUHMAKGKmh0dHA6Ly9jcnQucjRt\n" +
            "MDIuYW1hem9udHJ1c3QuY29tL3I0bTAyLmNlcjAMBgNVHRMBAf8EAjAAMIIBfQYK\n" +
            "KwYBBAHWeQIEAgSCAW0EggFpAWcAdgDuzdBk1dsazsVct520zROiModGfLzs3sNR\n" +
            "SFlGcR+1mwAAAYgHvX9QAAAEAwBHMEUCIQD8qPPCLL2Grd+/YNALWqAq7LC7YBaa\n" +
            "dNg5+6Q4kRDEqgIgEkf/UMsMNfTRaOZvoOgAK9/F0xX/CfdcUTjULhmoA+cAdQBI\n" +
            "sONr2qZHNA/lagL6nTDrHFIBy1bdLIHZu7+rOdiEcwAAAYgHvX8UAAAEAwBGMEQC\n" +
            "IBVFDtapMMWJOqyu8Cv6XEhFmbU8N33c2owed//pa80xAiAT9T6Wba3B9DFUmrL5\n" +
            "cCGKLqciIEUPhPbvjCuUepelrAB2ANq2v2s/tbYin5vCu1xr6HCRcWy7UYSFNL2k\n" +
            "PTBI1/urAAABiAe9ft8AAAQDAEcwRQIhAP2XDC/RlmVtH4WrfSwVosR/f/WXRhG5\n" +
            "mk9Nwq+ZOIriAiAopPXSH7VwXa3bEAIiTwcV1l10QIDZaIPCU5olknU5CjANBgkq\n" +
            "hkiG9w0BAQwFAAOCAgEAFuwMIJdP5rgz6cqOIj2EgF2OU8CUGi/wJ45BomXWv4Rv\n" +
            "U5mOKB+jHOGZZC9dncjAMa44RwoF2I7/8Y3qLVaoNm46ObvvS+6UvzTcyQqXM7JU\n" +
            "cSmdlf9DkspjKPDvMBokVrM4ak5AoxUjuru5qaia3nvbxq7XKO9/FGUaUaU8Xlsd\n" +
            "V6Fo8VmNwFc88VCqOp8eI/IicHxMDLl8TKXMvr3CYh8A9nCeFGcV+4CL+7JF2t5K\n" +
            "YvV5r074Wyk0QMlRVYMNDl0t+VAEoDJ7RRE+kEvplWcsX9S2wvr4HhkA4iChpwFm\n" +
            "2UDTppHskSWyLsuNQvipn0zTzZ8RIxXd/ei0qCdhKmkV7x9cgbTiyXgaI7iJEtdo\n" +
            "RvYNcXc2RmitWjY5Av8yJGOk0eYpCwRrBv6ughbtJe3NMrqUeTyrKidIEo9KnRSA\n" +
            "rMokRbHunkroS97VkoK/9j9pNJki+qAH9XTLYWcm/5+cTSGRsN+escRgZwV6KWg/\n" +
            "JQQe5LbwU2HHzNqWuk63GC/ngVlWXjaVFfbNVmYEKZFFazcZchesN1YyDu+WndOx\n" +
            "+rTcuke2feOvQ4EnVviM0k85JZNiqPDH2iafAWyqZFUYTnb7XK3HhJflAniv/SLq\n" +
            "DQfbJmtQtNHdJYgVmC1u2RT9gbJDIAj0ZI4vU2WVB5Hmd9F31un6jundEuG4+S4=\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=revoked.rootca2.demo.amazontrust.com
    // Issuer: CN=Amazon RSA 4096 M02, O=Amazon, C=US
    // Serial number: 788baa8f47bc5b1c624424216240fd3
    // Valid from: Tue May 09 17:00:00 PDT 2023 until: Fri Jun 07 16:59:59 PDT 2024
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIIEjCCBfqgAwIBAgIQB4i6qPR7xbHGJEJCFiQP0zANBgkqhkiG9w0BAQwFADA8\n" +
            "MQswCQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMRwwGgYDVQQDExNBbWF6b24g\n" +
            "UlNBIDQwOTYgTTAyMB4XDTIzMDUxMDAwMDAwMFoXDTI0MDYwNzIzNTk1OVowLzEt\n" +
            "MCsGA1UEAxMkcmV2b2tlZC5yb290Y2EyLmRlbW8uYW1hem9udHJ1c3QuY29tMIIC\n" +
            "IjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAzJfddWdrWhA9dSJdmy23veN9\n" +
            "oLvSqpM4YaXGZmPtKUmbFMLs2I3vCKrzflRKeOpl3MCc2hh6TH/3z+Q/fGugXLsY\n" +
            "H8QcjSbiIOd15n+3dUFTLKaoWMyseMcWiOIVaN5rCDVXiAHdt1pc147wyFQIzqNK\n" +
            "J/xiV1u9eT2MFue+4bd7kUNAcmI8M+SXruhto4jtAV8ugpTEChTDlyO/l8xmaM1Q\n" +
            "HkijsHX7Aq72Q/3PH/U+wbJ9pmpTp4x2AEJoo45IGfB/NKDTrv5otLBuiP8Y0M7b\n" +
            "K7irRPDFBqMNZw7S7p39SnC+V/WibJQk5Bo/8vcwDJX+WnDkw1QD/uXu3ugDzSDD\n" +
            "iBDViMOdN+3K47s4x2kdssoh4WWScMlAVb4vyN7IA3J4TnwA/1uCWhw4LE1WvY7N\n" +
            "etekhVP1eWF8IzNY0oo2u2ie79777xvBtmtp7RnvYLGv7I+xVhjH5qGNzn9fRCUm\n" +
            "QDego5HAfJ0PLlMEagdW8asCak1WaC117adnibL6WPtFA2FD2i6gNalTvhXhK2Ex\n" +
            "alGxrVd/BCseT3bMp783jqScJO1g6xRHu0Qx+RyrOGVvcKZa6Y0DcAc8psRpkHaO\n" +
            "HZY+lE8O2CIxpAJlwSnD6BoDNo8sg1IqFNkECw3wqfeMPBcg38k6zjAxwRDcIx6U\n" +
            "SwDl4d3sjrmy3gOFFXMCAwEAAaOCAxswggMXMB8GA1UdIwQYMBaAFJ5xHxodk6nZ\n" +
            "LY7MSFM/A1TznuZmMB0GA1UdDgQWBBQXpWT7gMHO+HKoHM1gU1VQVnylRzBOBgNV\n" +
            "HREERzBFgiRyZXZva2VkLnJvb3RjYTIuZGVtby5hbWF6b250cnVzdC5jb22CHXJl\n" +
            "dm9rZWQuc2NhMmEuYW1hem9udHJ1c3QuY29tMA4GA1UdDwEB/wQEAwIFoDAdBgNV\n" +
            "HSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwOwYDVR0fBDQwMjAwoC6gLIYqaHR0\n" +
            "cDovL2NybC5yNG0wMi5hbWF6b250cnVzdC5jb20vcjRtMDIuY3JsMBMGA1UdIAQM\n" +
            "MAowCAYGZ4EMAQIBMHUGCCsGAQUFBwEBBGkwZzAtBggrBgEFBQcwAYYhaHR0cDov\n" +
            "L29jc3AucjRtMDIuYW1hem9udHJ1c3QuY29tMDYGCCsGAQUFBzAChipodHRwOi8v\n" +
            "Y3J0LnI0bTAyLmFtYXpvbnRydXN0LmNvbS9yNG0wMi5jZXIwDAYDVR0TAQH/BAIw\n" +
            "ADCCAX0GCisGAQQB1nkCBAIEggFtBIIBaQFnAHYA7s3QZNXbGs7FXLedtM0TojKH\n" +
            "Rny87N7DUUhZRnEftZsAAAGIB72CzgAABAMARzBFAiEA2vPYIPfGJeynPaZHq/c0\n" +
            "GGvyT6MpvFGMW0s0woLRT28CIEFbZbFSCnKugaqw9QDNi7vYmIF3Gyi3s6G2cCxY\n" +
            "4RJXAHYASLDja9qmRzQP5WoC+p0w6xxSActW3SyB2bu/qznYhHMAAAGIB72DDgAA\n" +
            "BAMARzBFAiAvfNcgtFEwk5C9dvMUYANbIAv0IOdF1new8Umn3cM+JwIhALbs/3L9\n" +
            "0ndF7sRKDZmfronNruptFlrI528P5Qi2P528AHUA2ra/az+1tiKfm8K7XGvocJFx\n" +
            "bLtRhIU0vaQ9MEjX+6sAAAGIB72CxQAABAMARjBEAiBKUns2FPbs0cThb6e7SnyL\n" +
            "y4/qP3V1Q/ASt/ZDRTeEQQIgWSQO4Gsz32srtqYuTM9AsFd92WA44kJHincdcGVX\n" +
            "XbIwDQYJKoZIhvcNAQEMBQADggIBAAnaNbn2wXylTCS7dtgB3rWdUf6hja1UDuvB\n" +
            "uZEL2dUOvyXfVFLNxKdeWBPzqpwEBNNwPQXhoI97TXlyu2x60jLzQamoGoRQ3s0P\n" +
            "NLhasLGEIQH/oYdMV/yp8EI8fUuRVE3xyw39FRqOrmsUFAnxNQmBO/09JM7sLcvS\n" +
            "wwh14p9dFTTolJHgnL4ZEtmZxSddFG+GBSTJ/A7dVSmwIudwzd+goA6173BI6yeT\n" +
            "hhQumLctQiOM7y1MzFeV8rL+oIpd2xuzyhKKT1EgvU6/wyt0Ib8QqsFsrXPnUOKk\n" +
            "HAq3SeZyq35QUaTKoaH9L1iZMbSCG9Jm6FMb12SdAz53653tYvAiUS76oD8Jot13\n" +
            "RZu5NUlWAVLLq0OaEtuGp0bh+cVtzVnCC9m1qa46YpY0SojpvSbakgQMMGIgDlT3\n" +
            "wFE7tST4WlsDC1f/m+H9V5qz/j0U8D3eNNdowxPqx/JZq/sk9ZK5KyMFARrvM+fh\n" +
            "YrVYjKt91mu7JaS4pPOyZmJ8OQ14EvrN7BXc7IkNrI1reeaRFe49k5DAETB8VmP5\n" +
            "2F0SWou2KkgtJvU4Z7YjlZ2HNHnpjTK5KdPNpRSt7EUy2zn9NCNoyQhnws70FyXv\n" +
            "oPFyG92lnUQOKaAUhVRwTr9fvnkdMOzSKg/spxi2Ogdzym5Jw68eguwi0dVqX2+9\n" +
            "3zViP2aH\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator) throws Exception {

        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Mon May 15 13:38:54 PDT 2023", System.out);
    }
}

class AmazonCA_3 {

    // Owner: CN=Amazon ECDSA 256 M02, O=Amazon, C=US
    // Issuer: CN=Amazon Root CA 3, O=Amazon, C=US
    // Serial number: 773126de2c2fafd2c47ad88b1566e0182046d
    // Valid from: Tue Aug 23 15:33:24 PDT 2022 until: Fri Aug 23 15:33:24 PDT 2030
    private static final String INT_VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIC1DCCAnmgAwIBAgITB3MSbeLC+v0sR62IsVZuAYIEbTAKBggqhkjOPQQDAjA5\n" +
            "MQswCQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMRkwFwYDVQQDExBBbWF6b24g\n" +
            "Um9vdCBDQSAzMB4XDTIyMDgyMzIyMzMyNFoXDTMwMDgyMzIyMzMyNFowPTELMAkG\n" +
            "A1UEBhMCVVMxDzANBgNVBAoTBkFtYXpvbjEdMBsGA1UEAxMUQW1hem9uIEVDRFNB\n" +
            "IDI1NiBNMDIwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAS9vQLD4W/Kg4AnFRl8\n" +
            "x/FUbLqtd5ICYjUijGsytF9hmgb/Dyk+Ebt4cw6rAlGbaiOLapSJKZiZr+UQdh3I\n" +
            "QOr+o4IBWjCCAVYwEgYDVR0TAQH/BAgwBgEB/wIBADAOBgNVHQ8BAf8EBAMCAYYw\n" +
            "HQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMCMB0GA1UdDgQWBBS7eJrXaDMy\n" +
            "nRq7bP2xNEwB3svQdTAfBgNVHSMEGDAWgBSrttvXBp43rDCGB5Fwx5zEGbF4wDB7\n" +
            "BggrBgEFBQcBAQRvMG0wLwYIKwYBBQUHMAGGI2h0dHA6Ly9vY3NwLnJvb3RjYTMu\n" +
            "YW1hem9udHJ1c3QuY29tMDoGCCsGAQUFBzAChi5odHRwOi8vY3J0LnJvb3RjYTMu\n" +
            "YW1hem9udHJ1c3QuY29tL3Jvb3RjYTMuY2VyMD8GA1UdHwQ4MDYwNKAyoDCGLmh0\n" +
            "dHA6Ly9jcmwucm9vdGNhMy5hbWF6b250cnVzdC5jb20vcm9vdGNhMy5jcmwwEwYD\n" +
            "VR0gBAwwCjAIBgZngQwBAgEwCgYIKoZIzj0EAwIDSQAwRgIhAKSYEcDcp3kcPMzh\n" +
            "OIYDWZOLu4InPod4fQhRTmc2zBAgAiEAmwdGE4AuNWhw9N8REhf82rJLNm7h9Myg\n" +
            "TsR9Wu0bQYU=\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=Amazon ECDSA 256 M01, O=Amazon, C=US
    // Issuer: CN=Amazon Root CA 3, O=Amazon, C=US
    // Serial number: 773126684d577c0fcf8d3a342bea86f94fc8f
    // Valid from: Tue Aug 23 15:31:46 PDT 2022 until: Fri Aug 23 15:31:46 PDT 2030
    private static final String INT_REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIC0zCCAnmgAwIBAgITB3MSZoTVd8D8+NOjQr6ob5T8jzAKBggqhkjOPQQDAjA5\n" +
            "MQswCQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMRkwFwYDVQQDExBBbWF6b24g\n" +
            "Um9vdCBDQSAzMB4XDTIyMDgyMzIyMzE0NloXDTMwMDgyMzIyMzE0NlowPTELMAkG\n" +
            "A1UEBhMCVVMxDzANBgNVBAoTBkFtYXpvbjEdMBsGA1UEAxMUQW1hem9uIEVDRFNB\n" +
            "IDI1NiBNMDEwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAT80w+2RwNHzyXmVUM/\n" +
            "OUKBZpJkTzHyCKDl4sBrUfjzVjot/lNba9kYzMKSHYv95CUDoMaF2h2KAqx65uLQ\n" +
            "Y8ago4IBWjCCAVYwEgYDVR0TAQH/BAgwBgEB/wIBADAOBgNVHQ8BAf8EBAMCAYYw\n" +
            "HQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMCMB0GA1UdDgQWBBRPWfy8BhYo\n" +
            "v6LI2wj7zxMkumlCXDAfBgNVHSMEGDAWgBSrttvXBp43rDCGB5Fwx5zEGbF4wDB7\n" +
            "BggrBgEFBQcBAQRvMG0wLwYIKwYBBQUHMAGGI2h0dHA6Ly9vY3NwLnJvb3RjYTMu\n" +
            "YW1hem9udHJ1c3QuY29tMDoGCCsGAQUFBzAChi5odHRwOi8vY3J0LnJvb3RjYTMu\n" +
            "YW1hem9udHJ1c3QuY29tL3Jvb3RjYTMuY2VyMD8GA1UdHwQ4MDYwNKAyoDCGLmh0\n" +
            "dHA6Ly9jcmwucm9vdGNhMy5hbWF6b250cnVzdC5jb20vcm9vdGNhMy5jcmwwEwYD\n" +
            "VR0gBAwwCjAIBgZngQwBAgEwCgYIKoZIzj0EAwIDSAAwRQIhALRfxq3SQIhj5xA4\n" +
            "S5UAY/KlKqayZDpnbBdCDH8Kqmf/AiAUVZddALefnqRe+ifxN2FUp461LL6/cgVM\n" +
            "EH3Ty27f1Q==\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=valid.rootca3.demo.amazontrust.com
    // Issuer: CN=Amazon ECDSA 256 M02, O=Amazon, C=US
    // Serial number: 8e2f14864fb28e4a1da0f15a5118cc8
    // Valid from: Tue May 09 17:00:00 PDT 2023 until: Fri Jun 07 16:59:59 PDT 2024
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIEfjCCBCWgAwIBAgIQCOLxSGT7KOSh2g8VpRGMyDAKBggqhkjOPQQDAjA9MQsw\n" +
            "CQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMR0wGwYDVQQDExRBbWF6b24gRUNE\n" +
            "U0EgMjU2IE0wMjAeFw0yMzA1MTAwMDAwMDBaFw0yNDA2MDcyMzU5NTlaMC0xKzAp\n" +
            "BgNVBAMTInZhbGlkLnJvb3RjYTMuZGVtby5hbWF6b250cnVzdC5jb20wWTATBgcq\n" +
            "hkjOPQIBBggqhkjOPQMBBwNCAAQfWc7gBGBBBmseCb2XWWRQVhCUQDVml3mVgvj5\n" +
            "RmnP1y5wpifUTFqu8ELdI7YGZ4JMSnetiKNmLtg5yhTEjzCQo4IDFTCCAxEwHwYD\n" +
            "VR0jBBgwFoAUu3ia12gzMp0au2z9sTRMAd7L0HUwHQYDVR0OBBYEFHCE8orvZDUK\n" +
            "5TI9MYadzxWR9CZGMEkGA1UdEQRCMECCInZhbGlkLnJvb3RjYTMuZGVtby5hbWF6\n" +
            "b250cnVzdC5jb22CGmdvb2Quc2NhM2EuYW1hem9udHJ1c3QuY29tMA4GA1UdDwEB\n" +
            "/wQEAwIHgDAdBgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwOwYDVR0fBDQw\n" +
            "MjAwoC6gLIYqaHR0cDovL2NybC5lMm0wMi5hbWF6b250cnVzdC5jb20vZTJtMDIu\n" +
            "Y3JsMBMGA1UdIAQMMAowCAYGZ4EMAQIBMHUGCCsGAQUFBwEBBGkwZzAtBggrBgEF\n" +
            "BQcwAYYhaHR0cDovL29jc3AuZTJtMDIuYW1hem9udHJ1c3QuY29tMDYGCCsGAQUF\n" +
            "BzAChipodHRwOi8vY3J0LmUybTAyLmFtYXpvbnRydXN0LmNvbS9lMm0wMi5jZXIw\n" +
            "DAYDVR0TAQH/BAIwADCCAXwGCisGAQQB1nkCBAIEggFsBIIBaAFmAHUA7s3QZNXb\n" +
            "Gs7FXLedtM0TojKHRny87N7DUUhZRnEftZsAAAGIB71y/gAABAMARjBEAiAEAXIb\n" +
            "aOVR26HgFaI+qoIasCb8w2sOqVxGAxf5iPgX6QIgdAlMjqeoihi1arnJpzN8Bqxy\n" +
            "5ULMUO7GK3JEgcogJHMAdgBIsONr2qZHNA/lagL6nTDrHFIBy1bdLIHZu7+rOdiE\n" +
            "cwAAAYgHvXLkAAAEAwBHMEUCIF7wDDmWxTHwBZM7Me8eOCM1aQ/g1c1rJg/I+NJa\n" +
            "HkZYAiEA8p+IviuY5piHBELjUtVlZLiS9XSSMxpQNhUerqC/YFoAdQDatr9rP7W2\n" +
            "Ip+bwrtca+hwkXFsu1GEhTS9pD0wSNf7qwAAAYgHvXKvAAAEAwBGMEQCIFLskZDs\n" +
            "UG4+/88D/5/QbD9zT6ZmZlwXiPZ6H2YR/KiJAiBvi4vvNsb9KNAhJMgI2T2iCg9U\n" +
            "CIru+US6y3ua7dKKDTAKBggqhkjOPQQDAgNHADBEAiAzvgzKV/kvBbKWCT1NNUBD\n" +
            "AF9okIEcJx/ukFgzmYMwUQIgXeJeVf3izkxsgiEUSknwHsErLFs/cEme2PSRj2AW\n" +
            "dYA=\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=revoked.rootca3.demo.amazontrust.com
    // Issuer: CN=Amazon ECDSA 256 M01, O=Amazon, C=US
    // Serial number: c458bfaeedae16a5e61fe64773fc898
    // Valid from: Tue May 09 17:00:00 PDT 2023 until: Fri Jun 07 16:59:59 PDT 2024
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIEhzCCBC2gAwIBAgIQDEWL+u7a4WpeYf5kdz/ImDAKBggqhkjOPQQDAjA9MQsw\n" +
            "CQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMR0wGwYDVQQDExRBbWF6b24gRUNE\n" +
            "U0EgMjU2IE0wMTAeFw0yMzA1MTAwMDAwMDBaFw0yNDA2MDcyMzU5NTlaMC8xLTAr\n" +
            "BgNVBAMTJHJldm9rZWQucm9vdGNhMy5kZW1vLmFtYXpvbnRydXN0LmNvbTBZMBMG\n" +
            "ByqGSM49AgEGCCqGSM49AwEHA0IABAsSs5kW5TZlS0SDrMb9iUQAqEaKa12Fc6SN\n" +
            "9UR6qtOFdW/1UuziDq3Hl5dqsAYZJkbJSPCIsD2HTP/EGTMKITCjggMbMIIDFzAf\n" +
            "BgNVHSMEGDAWgBRPWfy8BhYov6LI2wj7zxMkumlCXDAdBgNVHQ4EFgQUeE55ET2e\n" +
            "i8KbY7KHTxOuvCkRpTowTgYDVR0RBEcwRYIkcmV2b2tlZC5yb290Y2EzLmRlbW8u\n" +
            "YW1hem9udHJ1c3QuY29tgh1yZXZva2VkLnNjYTNhLmFtYXpvbnRydXN0LmNvbTAO\n" +
            "BgNVHQ8BAf8EBAMCB4AwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMCMDsG\n" +
            "A1UdHwQ0MDIwMKAuoCyGKmh0dHA6Ly9jcmwuZTJtMDEuYW1hem9udHJ1c3QuY29t\n" +
            "L2UybTAxLmNybDATBgNVHSAEDDAKMAgGBmeBDAECATB1BggrBgEFBQcBAQRpMGcw\n" +
            "LQYIKwYBBQUHMAGGIWh0dHA6Ly9vY3NwLmUybTAxLmFtYXpvbnRydXN0LmNvbTA2\n" +
            "BggrBgEFBQcwAoYqaHR0cDovL2NydC5lMm0wMS5hbWF6b250cnVzdC5jb20vZTJt\n" +
            "MDEuY2VyMAwGA1UdEwEB/wQCMAAwggF9BgorBgEEAdZ5AgQCBIIBbQSCAWkBZwB2\n" +
            "AHb/iD8KtvuVUcJhzPWHujS0pM27KdxoQgqf5mdMWjp0AAABiAe9lQ8AAAQDAEcw\n" +
            "RQIgZVFAX5WPZRBpEOqk620v4Rbzxh/3wrJ5QBMBJ0Mb8B0CIQC0oxFVLfs+PAv7\n" +
            "25wawOu2VgDXG9lJAJtCwk3gN8BshQB2AEiw42vapkc0D+VqAvqdMOscUgHLVt0s\n" +
            "gdm7v6s52IRzAAABiAe9lQ4AAAQDAEcwRQIhAIPVMj6IfjAUKeGYbpG9s0DRdWbc\n" +
            "b8OzsOf+kRqk03NMAiB777hfoFCUMPrN0g8o5v6zp3T3qOhRnYY0TZN4q4NnMgB1\n" +
            "ANq2v2s/tbYin5vCu1xr6HCRcWy7UYSFNL2kPTBI1/urAAABiAe9lN4AAAQDAEYw\n" +
            "RAIgL0qoVbKLFD+Y3f/V6Rw+euZrPO6d1HEVPQGo7wLzkl8CIGHp3PQmmrEofl76\n" +
            "4da7bY0L+csFW0sB8clN0KziMfe6MAoGCCqGSM49BAMCA0gAMEUCIQC+6VdX9X5g\n" +
            "x3NSUmJ7py01Zxf26TNBv1ildxqesvZ/7wIgIrefriRzPiIFDHCUbdjk0VlmMwZR\n" +
            "VzXXHINsGCiCKOs=\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator) throws Exception {

        // Validate valid
        pathValidator.validate(new String[]{VALID, INT_VALID},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT_REVOKED},
                ValidatePathWithParams.Status.REVOKED,
                "Mon May 15 13:41:22 PDT 2023", System.out);
    }
}

class AmazonCA_4 {

    // Owner: CN=Amazon ECDSA 384 M02, O=Amazon, C=US
    // Issuer: CN=Amazon Root CA 4, O=Amazon, C=US
    // Serial number: 773127dfaa6b9e2b95538aa76dde4307f17c4
    // Valid from: Tue Aug 23 15:36:58 PDT 2022 until: Fri Aug 23 15:36:58 PDT 2030
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIDETCCApagAwIBAgITB3MSffqmueK5VTiqdt3kMH8XxDAKBggqhkjOPQQDAzA5\n" +
            "MQswCQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMRkwFwYDVQQDExBBbWF6b24g\n" +
            "Um9vdCBDQSA0MB4XDTIyMDgyMzIyMzY1OFoXDTMwMDgyMzIyMzY1OFowPTELMAkG\n" +
            "A1UEBhMCVVMxDzANBgNVBAoTBkFtYXpvbjEdMBsGA1UEAxMUQW1hem9uIEVDRFNB\n" +
            "IDM4NCBNMDIwdjAQBgcqhkjOPQIBBgUrgQQAIgNiAATNYzWQDXV0NoNmR0hJPwJq\n" +
            "hjYOOS9z0B2Z7MQudxg5x3Vsib6N+tJkq8dljRq5o6K0bbh/kRVfoi9wfKhB03Yz\n" +
            "gkerrwRCH7Z9gU5nbBY+Y5+EtImq4yOB0n7JQgQxWemjggFaMIIBVjASBgNVHRMB\n" +
            "Af8ECDAGAQH/AgEAMA4GA1UdDwEB/wQEAwIBhjAdBgNVHSUEFjAUBggrBgEFBQcD\n" +
            "AQYIKwYBBQUHAwIwHQYDVR0OBBYEFKbZqzuHmTP/6Gj4i2GDbNCyuq+9MB8GA1Ud\n" +
            "IwQYMBaAFNPsxzplbszh2naaVvuc84ZtV+WBMHsGCCsGAQUFBwEBBG8wbTAvBggr\n" +
            "BgEFBQcwAYYjaHR0cDovL29jc3Aucm9vdGNhNC5hbWF6b250cnVzdC5jb20wOgYI\n" +
            "KwYBBQUHMAKGLmh0dHA6Ly9jcnQucm9vdGNhNC5hbWF6b250cnVzdC5jb20vcm9v\n" +
            "dGNhNC5jZXIwPwYDVR0fBDgwNjA0oDKgMIYuaHR0cDovL2NybC5yb290Y2E0LmFt\n" +
            "YXpvbnRydXN0LmNvbS9yb290Y2E0LmNybDATBgNVHSAEDDAKMAgGBmeBDAECATAK\n" +
            "BggqhkjOPQQDAwNpADBmAjEA2zCG6x0xMlgSXWEGLN8+1XN+OCYF5vj0Z1jtVy+A\n" +
            "pdLlzuxNt9HBWn3hvqvO2W8KAjEApNdsZOCmk5uZBYiuCSBnDH3jyKhN6dWyuuHW\n" +
            "9Wj7SxKnOU5+wYWZA0BQAv1KT62i\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=valid.rootca4.demo.amazontrust.com
    // Issuer: CN=Amazon ECDSA 384 M02, O=Amazon, C=US
    // Serial number: f579bed3369f1a147ea5d0e8e6532d3
    // Valid from: Tue May 09 17:00:00 PDT 2023 until: Fri Jun 07 16:59:59 PDT 2024
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIEvjCCBESgAwIBAgIQD1eb7TNp8aFH6l0OjmUy0zAKBggqhkjOPQQDAzA9MQsw\n" +
            "CQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMR0wGwYDVQQDExRBbWF6b24gRUNE\n" +
            "U0EgMzg0IE0wMjAeFw0yMzA1MTAwMDAwMDBaFw0yNDA2MDcyMzU5NTlaMC0xKzAp\n" +
            "BgNVBAMTInZhbGlkLnJvb3RjYTQuZGVtby5hbWF6b250cnVzdC5jb20wdjAQBgcq\n" +
            "hkjOPQIBBgUrgQQAIgNiAAT6/95JFuvx5t9MVeRZmBtXq63Q2fXZnSwEy2U2F4Qc\n" +
            "ejhDwcYfD2HmT6S6GrKqLNJMa5n2YOvet4LZpKJLFF+BQo6FJt5cXkzHHxZ1I4z3\n" +
            "8pGU79CpCgFOFy6QUlF68NajggMXMIIDEzAfBgNVHSMEGDAWgBSm2as7h5kz/+ho\n" +
            "+Ithg2zQsrqvvTAdBgNVHQ4EFgQUR/GnpQkrUsCj8jF6/JIE1Rs07zswSQYDVR0R\n" +
            "BEIwQIIidmFsaWQucm9vdGNhNC5kZW1vLmFtYXpvbnRydXN0LmNvbYIaZ29vZC5z\n" +
            "Y2E0YS5hbWF6b250cnVzdC5jb20wDgYDVR0PAQH/BAQDAgeAMB0GA1UdJQQWMBQG\n" +
            "CCsGAQUFBwMBBggrBgEFBQcDAjA7BgNVHR8ENDAyMDCgLqAshipodHRwOi8vY3Js\n" +
            "LmUzbTAyLmFtYXpvbnRydXN0LmNvbS9lM20wMi5jcmwwEwYDVR0gBAwwCjAIBgZn\n" +
            "gQwBAgEwdQYIKwYBBQUHAQEEaTBnMC0GCCsGAQUFBzABhiFodHRwOi8vb2NzcC5l\n" +
            "M20wMi5hbWF6b250cnVzdC5jb20wNgYIKwYBBQUHMAKGKmh0dHA6Ly9jcnQuZTNt\n" +
            "MDIuYW1hem9udHJ1c3QuY29tL2UzbTAyLmNlcjAMBgNVHRMBAf8EAjAAMIIBfgYK\n" +
            "KwYBBAHWeQIEAgSCAW4EggFqAWgAdgDuzdBk1dsazsVct520zROiModGfLzs3sNR\n" +
            "SFlGcR+1mwAAAYgHvZA9AAAEAwBHMEUCIQCmzmQOzunsuAg1GpIcNx0isG6ylbhP\n" +
            "y9JP4UFclL2hdwIgBtTM89mE7QJDj7h7xr2eRPio1ehgmeYH1PHXxCqHIGYAdgBI\n" +
            "sONr2qZHNA/lagL6nTDrHFIBy1bdLIHZu7+rOdiEcwAAAYgHvZB1AAAEAwBHMEUC\n" +
            "IF9hbi82CLU5umfRze4NpX6u4jlT+N8KSaBe6UbhqjBZAiEAi2Y6PTt2+107LxtM\n" +
            "oBpHprph7hQvGfjPE+p+rfM/X+EAdgDatr9rP7W2Ip+bwrtca+hwkXFsu1GEhTS9\n" +
            "pD0wSNf7qwAAAYgHvZBeAAAEAwBHMEUCIAI+m4mVE3HtZOEMC5VI7m0nEPdPPJUq\n" +
            "fxUKPpeIVmk5AiEA0scVJy7g3Fv+2nTVhbcwWCwn/Gvc+0txQrc529juflcwCgYI\n" +
            "KoZIzj0EAwMDaAAwZQIxAKV837BpqlNHg35EsCCtrJPoQ6RuY9UoHm1O2CdsCXGR\n" +
            "Z3kAnlgIV8A/waI6wQqfsQIwdCqaC+qN60JCnX09YKRD15eQjq1rN3w+llI+lEbS\n" +
            "FSMsnoHJcqMZLo9s+4Rf0zS3\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=revoked.rootca4.demo.amazontrust.com
    // Issuer: CN=Amazon ECDSA 384 M02, O=Amazon, C=US
    // Serial number: 4a5d392936b4decb818b7fb106ebbd8
    // Valid from: Tue May 09 17:00:00 PDT 2023 until: Fri Jun 07 16:59:59 PDT 2024
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIExjCCBEygAwIBAgIQBKXTkpNrTey4GLf7EG672DAKBggqhkjOPQQDAzA9MQsw\n" +
            "CQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMR0wGwYDVQQDExRBbWF6b24gRUNE\n" +
            "U0EgMzg0IE0wMjAeFw0yMzA1MTAwMDAwMDBaFw0yNDA2MDcyMzU5NTlaMC8xLTAr\n" +
            "BgNVBAMTJHJldm9rZWQucm9vdGNhNC5kZW1vLmFtYXpvbnRydXN0LmNvbTB2MBAG\n" +
            "ByqGSM49AgEGBSuBBAAiA2IABFYfMbv5/vgqDunZj4ffJiuELtdwfEPXx9QlZnCm\n" +
            "rBP3Z4/GvUVRVmyh5sYdnbCGCEClH/RxU6BC5SKv+TzhsFLEumhezanljnQXRAIL\n" +
            "a1OGbP8zLLP6FuAD0cjY3P3adKOCAx0wggMZMB8GA1UdIwQYMBaAFKbZqzuHmTP/\n" +
            "6Gj4i2GDbNCyuq+9MB0GA1UdDgQWBBSqnGV5pN/agPCtVdV37CP1z/DUqjBOBgNV\n" +
            "HREERzBFgiRyZXZva2VkLnJvb3RjYTQuZGVtby5hbWF6b250cnVzdC5jb22CHXJl\n" +
            "dm9rZWQuc2NhNGEuYW1hem9udHJ1c3QuY29tMA4GA1UdDwEB/wQEAwIHgDAdBgNV\n" +
            "HSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwOwYDVR0fBDQwMjAwoC6gLIYqaHR0\n" +
            "cDovL2NybC5lM20wMi5hbWF6b250cnVzdC5jb20vZTNtMDIuY3JsMBMGA1UdIAQM\n" +
            "MAowCAYGZ4EMAQIBMHUGCCsGAQUFBwEBBGkwZzAtBggrBgEFBQcwAYYhaHR0cDov\n" +
            "L29jc3AuZTNtMDIuYW1hem9udHJ1c3QuY29tMDYGCCsGAQUFBzAChipodHRwOi8v\n" +
            "Y3J0LmUzbTAyLmFtYXpvbnRydXN0LmNvbS9lM20wMi5jZXIwDAYDVR0TAQH/BAIw\n" +
            "ADCCAX8GCisGAQQB1nkCBAIEggFvBIIBawFpAHYAdv+IPwq2+5VRwmHM9Ye6NLSk\n" +
            "zbsp3GhCCp/mZ0xaOnQAAAGIB72QJQAABAMARzBFAiA74zKrlL+y5rYwSLxBL8fs\n" +
            "QYRYXF0s0sGoaSEeAg1DkgIhAPu8Z0TLIFoppmyiv+A5z6S+SG+v/kOsAYmQmiUO\n" +
            "5scIAHcASLDja9qmRzQP5WoC+p0w6xxSActW3SyB2bu/qznYhHMAAAGIB72QJgAA\n" +
            "BAMASDBGAiEAg+x7JBT3oIaZdnfgGN1G6SAiNUL7zR/tBhbWIG9tz94CIQDGwBiV\n" +
            "Tslt11+W3ZaNsS7UtUIiB45YHUc4qKm5ry2fTAB2ANq2v2s/tbYin5vCu1xr6HCR\n" +
            "cWy7UYSFNL2kPTBI1/urAAABiAe9kAgAAAQDAEcwRQIgPvKfSpMJKRocGk9+GNr3\n" +
            "hUj8x8WySB//0X116TNgA0gCIQDhGRqxnEZmEFGEfj5GY9vjEfm0kKwcL0lCuwBu\n" +
            "NZG4dzAKBggqhkjOPQQDAwNoADBlAjEA1PLdsrko3tDs50aAeEU9Gn+0CG8QKy7R\n" +
            "fQaXBTjGETDgGJk/7zGNpGelKPr/UYV9AjASwdA32S8jIADxA8HrqiMsVYDFMnbU\n" +
            "jLLwR6CTLtAcWtwVmoQ2x0usvTvN8YJBPoA=\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator) throws Exception {

        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Mon May 15 13:42:48 PDT 2023", System.out);
    }
}
