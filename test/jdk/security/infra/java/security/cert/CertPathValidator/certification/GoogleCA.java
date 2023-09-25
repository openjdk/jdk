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
 * @bug 8307134
 * @summary Interoperability tests with Google's GlobalSign R4 and GTS Root certificates
 * @build ValidatePathWithParams
 * @run main/othervm -Djava.security.debug=certpath GoogleCA OCSP
 * @run main/othervm -Djava.security.debug=certpath GoogleCA CRL
 */

/*
 * Obtain TLS test artifacts for Google CAs from:
 *
 * https://pki.goog/repository/
 */
public class GoogleCA {

    public static void main(String[] args) throws Exception {

        ValidatePathWithParams pathValidator = new ValidatePathWithParams(null);
        boolean ocspEnabled = false;

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            pathValidator.enableCRLCheck();
        } else {
            // OCSP check by default
            pathValidator.enableOCSPCheck();
            ocspEnabled = true;
        }

        new GoogleGSR4().runTest(pathValidator);
        new GoogleGTSR1().runTest(pathValidator);
        new GoogleGTSR2().runTest(pathValidator);
        new GoogleGTSR3().runTest(pathValidator);
        new GoogleGTSR4().runTest(pathValidator);
    }
}

class GoogleGSR4 {

    // Owner: CN=GTS CA 2D4, O=Google Trust Services LLC, C=US
    // Issuer: CN=GlobalSign, O=GlobalSign, OU=GlobalSign ECC Root CA - R4
    // Serial number: 21668f1cd0a2a8f847d8aad34
    // Valid from: Tue Oct 04 17:00:42 PDT 2022 until: Wed Sep 29 17:00:42 PDT 2027
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIDBDCCAqugAwIBAgINAhZo8c0KKo+EfYqtNDAKBggqhkjOPQQDAjBQMSQwIgYD\n" +
            "VQQLExtHbG9iYWxTaWduIEVDQyBSb290IENBIC0gUjQxEzARBgNVBAoTCkdsb2Jh\n" +
            "bFNpZ24xEzARBgNVBAMTCkdsb2JhbFNpZ24wHhcNMjIxMDA1MDAwMDQyWhcNMjcw\n" +
            "OTMwMDAwMDQyWjBGMQswCQYDVQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0\n" +
            "IFNlcnZpY2VzIExMQzETMBEGA1UEAxMKR1RTIENBIDJENDBZMBMGByqGSM49AgEG\n" +
            "CCqGSM49AwEHA0IABPQdCdV61990MPueGTVpXAjRmp2JIxt0Yuy59RZYT/XKg1lN\n" +
            "gpRc0eh/bHtpehigtqe+llKTiVEkMhSMURoQQsOjggFyMIIBbjAOBgNVHQ8BAf8E\n" +
            "BAMCAYYwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMCMBIGA1UdEwEB/wQI\n" +
            "MAYBAf8CAQAwHQYDVR0OBBYEFKiI2Yo5rGXVgks3qJVsZUPNRAHgMB8GA1UdIwQY\n" +
            "MBaAFFSwe61FuOJAf/sKbvu+M8k8o4TVMGYGCCsGAQUFBwEBBFowWDAlBggrBgEF\n" +
            "BQcwAYYZaHR0cDovL29jc3AucGtpLmdvb2cvZ3NyNDAvBggrBgEFBQcwAoYjaHR0\n" +
            "cDovL3BraS5nb29nL3JlcG8vY2VydHMvZ3NyNC5kZXIwMgYDVR0fBCswKTAnoCWg\n" +
            "I4YhaHR0cDovL2NybC5wa2kuZ29vZy9nc3I0L2dzcjQuY3JsME0GA1UdIARGMEQw\n" +
            "CAYGZ4EMAQIBMDgGCisGAQQB1nkCBQMwKjAoBggrBgEFBQcCARYcaHR0cHM6Ly9w\n" +
            "a2kuZ29vZy9yZXBvc2l0b3J5LzAKBggqhkjOPQQDAgNHADBEAiBi+ikli1YBHQGs\n" +
            "b5mnyBo5mydw04o386BPgaPpiBzgagIgbcpwQJCalLIekv8XRMoWFr3nV5XJfWRU\n" +
            "5QPpOX0rXbg=\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=good.gsr4.demo.pki.goog
    // Issuer: CN=GTS CA 2D4, O=Google Trust Services LLC, C=US
    // Serial number: 4c435754ee6e013c10efaff908a58cbb
    // Valid from: Mon Mar 27 12:41:45 PDT 2023 until: Sun Jun 25 12:41:44 PDT 2023
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIEsTCCBFegAwIBAgIQTENXVO5uATwQ76/5CKWMuzAKBggqhkjOPQQDAjBGMQsw\n" +
            "CQYDVQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZpY2VzIExMQzET\n" +
            "MBEGA1UEAxMKR1RTIENBIDJENDAeFw0yMzAzMjcxOTQxNDVaFw0yMzA2MjUxOTQx\n" +
            "NDRaMCIxIDAeBgNVBAMTF2dvb2QuZ3NyNC5kZW1vLnBraS5nb29nMIIBIjANBgkq\n" +
            "hkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAymtcnnxJ8pSF4YJUKTWKcHbRw28ShLzo\n" +
            "KVTRPUsRrZZDqyDx296k3e0D04kBhcvEduxtEabCe89m06SH7L+bGVi25j35AXwn\n" +
            "6aziLs/EV4BRy9ACfYipeT5PnQbaMmVe65q/RYKmWqD/z0SEh2uMFxRVl1CBmS/J\n" +
            "owbNUlrEEDiYkE/nGfCmacpW0QZ7kxGjSR34mCSDugIYE/HME3ZVcZOVf2LT0lBA\n" +
            "DhQtZI6cXy2lO8Ro/dUtcZKjo8iu0xW1pQeiJq9+CGp62MJFmpl+EfzP/B8aXQiF\n" +
            "+m44LJJgAjiShAwVo9HbJUYv0dqCS9G22FL43xXqAdDlWZeuZyg7bQIDAQABo4IC\n" +
            "fjCCAnowDgYDVR0PAQH/BAQDAgWgMBMGA1UdJQQMMAoGCCsGAQUFBwMBMAwGA1Ud\n" +
            "EwEB/wQCMAAwHQYDVR0OBBYEFKMuYkTnbWyrTBfBqbNNe91z3GPjMB8GA1UdIwQY\n" +
            "MBaAFKiI2Yo5rGXVgks3qJVsZUPNRAHgMHgGCCsGAQUFBwEBBGwwajA1BggrBgEF\n" +
            "BQcwAYYpaHR0cDovL29jc3AucGtpLmdvb2cvcy9ndHMyZDQvS2tnczU5VFFIelkw\n" +
            "MQYIKwYBBQUHMAKGJWh0dHA6Ly9wa2kuZ29vZy9yZXBvL2NlcnRzL2d0czJkNC5k\n" +
            "ZXIwIgYDVR0RBBswGYIXZ29vZC5nc3I0LmRlbW8ucGtpLmdvb2cwIQYDVR0gBBow\n" +
            "GDAIBgZngQwBAgEwDAYKKwYBBAHWeQIFAzA8BgNVHR8ENTAzMDGgL6AthitodHRw\n" +
            "Oi8vY3Jscy5wa2kuZ29vZy9ndHMyZDQvSUlXMzNMVUVwV3cuY3JsMIIBBAYKKwYB\n" +
            "BAHWeQIEAgSB9QSB8gDwAHcA6D7Q2j71BjUy51covIlryQPTy9ERa+zraeF3fW0G\n" +
            "vW4AAAGHJM62ygAABAMASDBGAiEAkeiqmfYYCVEmGA12/RJUZPdmxRP2ZXF0Xm30\n" +
            "Oz+q2tgCIQCgSYqT/6RH+PCOauOVW4uaoshT+HfqurghVCzwGgBFvwB1ALc++yTf\n" +
            "nE26dfI5xbpY9Gxd/ELPep81xJ4dCYEl7bSZAAABhyTOttoAAAQDAEYwRAIgBXao\n" +
            "3Pry1nCHu3bngW3q3CHSLzmNHmO4cXMSdN2sAOkCIDE5DUyok3TRsOIHu1QTB0R2\n" +
            "UxPeFm9KS73TBT8JEZykMAoGCCqGSM49BAMCA0gAMEUCIG1m91VOq3tghyLPA6YR\n" +
            "/Pkq+gQylyM8wGJgnRMRE0lhAiEAxBgYXImtVqbfymq2MYwhV9KmG9gPIfqN6qWi\n" +
            "lzblUM0=\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=revoked.gsr4.demo.pki.goog
    // Issuer: CN=GTS CA 2D4, O=Google Trust Services LLC, C=US
    // Serial number: 1f9bd55e26716b3710b2614cec6fff02
    // Valid from: Mon Mar 27 12:48:37 PDT 2023 until: Sun Jun 25 12:48:36 PDT 2023
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIEtzCCBFygAwIBAgIQH5vVXiZxazcQsmFM7G//AjAKBggqhkjOPQQDAjBGMQsw\n" +
            "CQYDVQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZpY2VzIExMQzET\n" +
            "MBEGA1UEAxMKR1RTIENBIDJENDAeFw0yMzAzMjcxOTQ4MzdaFw0yMzA2MjUxOTQ4\n" +
            "MzZaMCUxIzAhBgNVBAMTGnJldm9rZWQuZ3NyNC5kZW1vLnBraS5nb29nMIIBIjAN\n" +
            "BgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApVuoZ/bS9c2WSQ8W1FjPEsdGoANj\n" +
            "PqKaPwdyUhnko9ayyGGi5hHLYqir2tiNjfO8i5e3ybe6CIaybY37SQebquV+rioH\n" +
            "O9BS75GgtYXCaMK/8prya9RiaUjy7kecvpKtJNiaXrLJy8Vzq9g39n9hiXJYMGkc\n" +
            "fCWYjWd5jU4pAsYTslmuIYoIZuwRRX34iET6Brs3ijykcmYtG5F90wqFlvRxRh0x\n" +
            "vD0EeTOLGZSDQMYxlhfrqG449I10iTHusSxI2AXB6k7N2UXMJ44D7Z3RWkv1ItsY\n" +
            "eKVXQyLAYd8YYTFNdGa75SoRr+ChFbLCgSUMg188T/SS013bH/XSHpCbQQIDAQAB\n" +
            "o4ICgDCCAnwwDgYDVR0PAQH/BAQDAgWgMBMGA1UdJQQMMAoGCCsGAQUFBwMBMAwG\n" +
            "A1UdEwEB/wQCMAAwHQYDVR0OBBYEFLXeKzKKPx+Vs7YEKdmz9Vur9BZiMB8GA1Ud\n" +
            "IwQYMBaAFKiI2Yo5rGXVgks3qJVsZUPNRAHgMHgGCCsGAQUFBwEBBGwwajA1Bggr\n" +
            "BgEFBQcwAYYpaHR0cDovL29jc3AucGtpLmdvb2cvcy9ndHMyZDQvaG5fZHY1dHlS\n" +
            "SVkwMQYIKwYBBQUHMAKGJWh0dHA6Ly9wa2kuZ29vZy9yZXBvL2NlcnRzL2d0czJk\n" +
            "NC5kZXIwJQYDVR0RBB4wHIIacmV2b2tlZC5nc3I0LmRlbW8ucGtpLmdvb2cwIQYD\n" +
            "VR0gBBowGDAIBgZngQwBAgEwDAYKKwYBBAHWeQIFAzA8BgNVHR8ENTAzMDGgL6At\n" +
            "hitodHRwOi8vY3Jscy5wa2kuZ29vZy9ndHMyZDQvSUlXMzNMVUVwV3cuY3JsMIIB\n" +
            "AwYKKwYBBAHWeQIEAgSB9ASB8QDvAHYAejKMVNi3LbYg6jjgUh7phBZwMhOFTTvS\n" +
            "K8E6V6NS61IAAAGHJNUx1gAABAMARzBFAiEAj/RgXx1ScnsOf9R9N3eyPMJtH33C\n" +
            "mOrRCOodG8QXmE0CIHwNJC5E53BVmfMzZwJH9f2BiUx31SGHWFvG283zVtX/AHUA\n" +
            "6D7Q2j71BjUy51covIlryQPTy9ERa+zraeF3fW0GvW4AAAGHJNUxnAAABAMARjBE\n" +
            "AiAI7pcrKatsz0G4QYPKmS74VQVEgnHqgKSoqv0ghTJXTgIgPyoYubz4MEHYirBu\n" +
            "69BLC2jioXr8+wS7MK1IPqjdH44wCgYIKoZIzj0EAwIDSQAwRgIhAI4NdZ5JwTuW\n" +
            "P+RH2bsAc5xrb804G9mOc3WMRVxTUKesAiEA/jHMJ2YdPv0WXKjKY7nUyFjUPdin\n" +
            "BHRHfBeltynaFzU=\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator) throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Mon Mar 27 13:49:33 PDT 2023", System.out);
    }
}

class GoogleGTSR1 {

    // Owner: CN=GTS CA 1D4, O=Google Trust Services LLC, C=US
    // Issuer: CN=GTS Root R1, O=Google Trust Services LLC, C=US
    // Serial number: 2008eb2023336658b64cddb9b
    // Valid from: Wed Aug 12 17:00:42 PDT 2020 until: Wed Sep 29 17:00:42 PDT 2027
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIFjDCCA3SgAwIBAgINAgCOsgIzNmWLZM3bmzANBgkqhkiG9w0BAQsFADBHMQsw\n" +
            "CQYDVQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZpY2VzIExMQzEU\n" +
            "MBIGA1UEAxMLR1RTIFJvb3QgUjEwHhcNMjAwODEzMDAwMDQyWhcNMjcwOTMwMDAw\n" +
            "MDQyWjBGMQswCQYDVQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZp\n" +
            "Y2VzIExMQzETMBEGA1UEAxMKR1RTIENBIDFENDCCASIwDQYJKoZIhvcNAQEBBQAD\n" +
            "ggEPADCCAQoCggEBAKvAqqPCE27l0w9zC8dTPIE89bA+xTmDaG7y7VfQ4c+mOWhl\n" +
            "UebUQpK0yv2r678RJExK0HWDjeq+nLIHN1Em5j6rARZixmyRSjhIR0KOQPGBMUld\n" +
            "saztIIJ7O0g/82qj/vGDl//3t4tTqxiRhLQnTLXJdeB+2DhkdU6IIgx6wN7E5NcU\n" +
            "H3Rcsejcqj8p5Sj19vBm6i1FhqLGymhMFroWVUGO3xtIH91dsgy4eFKcfKVLWK3o\n" +
            "2190Q0Lm/SiKmLbRJ5Au4y1euFJm2JM9eB84Fkqa3ivrXWUeVtye0CQdKvsY2Fka\n" +
            "zvxtxvusLJzLWYHk55zcRAacDA2SeEtBbQfD1qsCAwEAAaOCAXYwggFyMA4GA1Ud\n" +
            "DwEB/wQEAwIBhjAdBgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwEgYDVR0T\n" +
            "AQH/BAgwBgEB/wIBADAdBgNVHQ4EFgQUJeIYDrJXkZQq5dRdhpCD3lOzuJIwHwYD\n" +
            "VR0jBBgwFoAU5K8rJnEaK0gnhS9SZizv8IkTcT4waAYIKwYBBQUHAQEEXDBaMCYG\n" +
            "CCsGAQUFBzABhhpodHRwOi8vb2NzcC5wa2kuZ29vZy9ndHNyMTAwBggrBgEFBQcw\n" +
            "AoYkaHR0cDovL3BraS5nb29nL3JlcG8vY2VydHMvZ3RzcjEuZGVyMDQGA1UdHwQt\n" +
            "MCswKaAnoCWGI2h0dHA6Ly9jcmwucGtpLmdvb2cvZ3RzcjEvZ3RzcjEuY3JsME0G\n" +
            "A1UdIARGMEQwCAYGZ4EMAQIBMDgGCisGAQQB1nkCBQMwKjAoBggrBgEFBQcCARYc\n" +
            "aHR0cHM6Ly9wa2kuZ29vZy9yZXBvc2l0b3J5LzANBgkqhkiG9w0BAQsFAAOCAgEA\n" +
            "IVToy24jwXUr0rAPc924vuSVbKQuYw3nLflLfLh5AYWEeVl/Du18QAWUMdcJ6o/q\n" +
            "FZbhXkBH0PNcw97thaf2BeoDYY9Ck/b+UGluhx06zd4EBf7H9P84nnrwpR+4GBDZ\n" +
            "K+Xh3I0tqJy2rgOqNDflr5IMQ8ZTWA3yltakzSBKZ6XpF0PpqyCRvp/NCGv2KX2T\n" +
            "uPCJvscp1/m2pVTtyBjYPRQ+QuCQGAJKjtN7R5DFrfTqMWvYgVlpCJBkwlu7+7KY\n" +
            "3cTIfzE7cmALskMKNLuDz+RzCcsYTsVaU7Vp3xL60OYhqFkuAOOxDZ6pHOj9+OJm\n" +
            "YgPmOT4X3+7L51fXJyRH9KfLRP6nT31D5nmsGAOgZ26/8T9hsBW1uo9ju5fZLZXV\n" +
            "VS5H0HyIBMEKyGMIPhFWrlt/hFS28N1zaKI0ZBGD3gYgDLbiDT9fGXstpk+Fmc4o\n" +
            "lVlWPzXe81vdoEnFbr5M272HdgJWo+WhT9BYM0Ji+wdVmnRffXgloEoluTNcWzc4\n" +
            "1dFpgJu8fF3LG0gl2ibSYiCi9a6hvU0TppjJyIWXhkJTcMJlPrWx1VytEUGrX2l0\n" +
            "JDwRjW/656r0KVB02xHRKvm2ZKI03TglLIpmVCK3kBKkKNpBNkFt8rhafcCKOb9J\n" +
            "x/9tpNFlQTl7B39rJlJWkR17QnZqVptFePFORoZmFzM=\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=good.gtsr1.demo.pki.goog
    // Issuer: CN=GTS CA 1D4, O=Google Trust Services LLC, C=US
    // Serial number: 19c08d5cde41fc84108f54c8d2a1aeca
    // Valid from: Mon Mar 27 12:33:43 PDT 2023 until: Sun Jun 25 12:33:42 PDT 2023
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIFcjCCBFqgAwIBAgIQGcCNXN5B/IQQj1TI0qGuyjANBgkqhkiG9w0BAQsFADBG\n" +
            "MQswCQYDVQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZpY2VzIExM\n" +
            "QzETMBEGA1UEAxMKR1RTIENBIDFENDAeFw0yMzAzMjcxOTMzNDNaFw0yMzA2MjUx\n" +
            "OTMzNDJaMCMxITAfBgNVBAMTGGdvb2QuZ3RzcjEuZGVtby5wa2kuZ29vZzCCASIw\n" +
            "DQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMkOYhMM6kQMlep+l2/l5KTC1ow8\n" +
            "nXHwXQzugR2Js302pM3p2UCfnfhlK0a9UUSVtAZa8ydVUyVRF9LzW1rOIK8UdlEj\n" +
            "O6qAvPnPw8laY7rCPWRPibxu0OqL/5sYD+a4hQ7GhVsYDXXxnWQvLV5mppRlYF/8\n" +
            "80ugGggRb+U3y6V84f1JnwSMvZFULe19BOeV5qWAHHFfgy0zePzcDMy8AqxaVBOb\n" +
            "FVSsbdql2gnRyC4WZ9D5lc8vwS84KrJbce2+VtrpcKVALtyVA0Zzor2lr2wOVc4i\n" +
            "OOwMNk9948eStAjOV8N4B1h9D/pd+cFSWfgXufr5ZClwijLr3zLvZxDGI6ECAwEA\n" +
            "AaOCAn0wggJ5MA4GA1UdDwEB/wQEAwIFoDATBgNVHSUEDDAKBggrBgEFBQcDATAM\n" +
            "BgNVHRMBAf8EAjAAMB0GA1UdDgQWBBSTKR+0ebWnH3uGz5qju5/LpkCjYzAfBgNV\n" +
            "HSMEGDAWgBQl4hgOsleRlCrl1F2GkIPeU7O4kjB4BggrBgEFBQcBAQRsMGowNQYI\n" +
            "KwYBBQUHMAGGKWh0dHA6Ly9vY3NwLnBraS5nb29nL3MvZ3RzMWQ0L3B6OThKdFZT\n" +
            "RnRjMDEGCCsGAQUFBzAChiVodHRwOi8vcGtpLmdvb2cvcmVwby9jZXJ0cy9ndHMx\n" +
            "ZDQuZGVyMCMGA1UdEQQcMBqCGGdvb2QuZ3RzcjEuZGVtby5wa2kuZ29vZzAhBgNV\n" +
            "HSAEGjAYMAgGBmeBDAECATAMBgorBgEEAdZ5AgUDMDwGA1UdHwQ1MDMwMaAvoC2G\n" +
            "K2h0dHA6Ly9jcmxzLnBraS5nb29nL2d0czFkNC92My1EUW1sYi1ZWS5jcmwwggEC\n" +
            "BgorBgEEAdZ5AgQCBIHzBIHwAO4AdQC3Pvsk35xNunXyOcW6WPRsXfxCz3qfNcSe\n" +
            "HQmBJe20mQAAAYckx1OMAAAEAwBGMEQCICQ4Do1cKFsqmm/swKZkdM/qGluDbctL\n" +
            "tIgp0YnoZTlEAiByAeAEaVQiU27AnpUerimnjPnThQq26vqvnWdstb0mwgB1AK33\n" +
            "vvp8/xDIi509nB4+GGq0Zyldz7EMJMqFhjTr3IKKAAABhyTHU7UAAAQDAEYwRAIg\n" +
            "WAIAOov42kcgOj0rYO3qb4/HTsW3o69x4IKd8ycsaVkCICIQUaeKwNp4aW/civO9\n" +
            "No/v5Ner5bmlwheqFAJcR/HCMA0GCSqGSIb3DQEBCwUAA4IBAQBEKKdwuzuAhdir\n" +
            "3hbPQIosD6H9vatr8tExWCDmw+PHOoiWIUTBu5fVZPQ27EgehTIA6kNhQj2g7fkF\n" +
            "Bd5zAl4k7WdsDZCeOHml6XXQZHvc+p4DYBKTTt3h81lsMLw8aWCOaiSmrQ0hZS/E\n" +
            "iuaqvlOFpOTd0x+MN2qcU14hi8SKxBgpraqR/s7OCwUFltxcPq0GAybzDGc9lgB+\n" +
            "Jt56QviN641s7hxThyGhFIHSePgWuwbT1grJKQiSW35yI4PJO90HoCpd2MLrC5Ic\n" +
            "B89ykY8mQcx+naGPZQdwdpx9GvKwSZdn+cq3kZwD66iXnwhqmiEdq4eBZr8ygSya\n" +
            "lnGV2OW+\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=revoked.gtsr1.demo.pki.goog
    // Issuer: CN=GTS CA 1D4, O=Google Trust Services LLC, C=US
    // Serial number: c414c34e6c2cc66c102b8d3502be3bb4
    // Valid from: Mon Mar 27 12:42:39 PDT 2023 until: Sun Jun 25 12:42:38 PDT 2023
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIFfDCCBGSgAwIBAgIRAMQUw05sLMZsECuNNQK+O7QwDQYJKoZIhvcNAQELBQAw\n" +
            "RjELMAkGA1UEBhMCVVMxIjAgBgNVBAoTGUdvb2dsZSBUcnVzdCBTZXJ2aWNlcyBM\n" +
            "TEMxEzARBgNVBAMTCkdUUyBDQSAxRDQwHhcNMjMwMzI3MTk0MjM5WhcNMjMwNjI1\n" +
            "MTk0MjM4WjAmMSQwIgYDVQQDExtyZXZva2VkLmd0c3IxLmRlbW8ucGtpLmdvb2cw\n" +
            "ggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCOeL80aphh8K8Cz41Sl2Cv\n" +
            "cI3Elrrm/2sQH5Q0nxNuoZcxTGk3hD75Ntf6eqgclUQXJDEGbfoo3q7kYIQPXEIy\n" +
            "+AuiMTd80ZRHuPBp8ci/wkh6N7B9mE/rjzJz77QgJluykoXRx9SiDyE4Yn9sRbBH\n" +
            "jNm/KBv8wMV6hzJZYaALyDpGVNuAx9cHE91LaSvamPiccJn4wb9zDtyFduS3yYbz\n" +
            "FREt960j420TeHjeWFkuXXVQMnPeRAWugclhJKzLz1U1gm5PWGxThMgVIy0v8v63\n" +
            "3qFT09I4avi0AzBaRtINCaS39Mo2AoX1jZNjFDNLzRO1fSSJpzJmWyXJ2jRI7MwF\n" +
            "AgMBAAGjggKDMIICfzAOBgNVHQ8BAf8EBAMCBaAwEwYDVR0lBAwwCgYIKwYBBQUH\n" +
            "AwEwDAYDVR0TAQH/BAIwADAdBgNVHQ4EFgQUCuJDEKGIdbWqxyVFZmIZoyQZ4T4w\n" +
            "HwYDVR0jBBgwFoAUJeIYDrJXkZQq5dRdhpCD3lOzuJIweAYIKwYBBQUHAQEEbDBq\n" +
            "MDUGCCsGAQUFBzABhilodHRwOi8vb2NzcC5wa2kuZ29vZy9zL2d0czFkNC9rb2Zm\n" +
            "cmFBODZBdzAxBggrBgEFBQcwAoYlaHR0cDovL3BraS5nb29nL3JlcG8vY2VydHMv\n" +
            "Z3RzMWQ0LmRlcjAmBgNVHREEHzAdghtyZXZva2VkLmd0c3IxLmRlbW8ucGtpLmdv\n" +
            "b2cwIQYDVR0gBBowGDAIBgZngQwBAgEwDAYKKwYBBAHWeQIFAzA8BgNVHR8ENTAz\n" +
            "MDGgL6AthitodHRwOi8vY3Jscy5wa2kuZ29vZy9ndHMxZDQvODJFckFFQVVsR1ku\n" +
            "Y3JsMIIBBQYKKwYBBAHWeQIEAgSB9gSB8wDxAHYArfe++nz/EMiLnT2cHj4YarRn\n" +
            "KV3PsQwkyoWGNOvcgooAAAGHJM+cawAABAMARzBFAiB568monxGD3NiHsqNmsy+t\n" +
            "IL4kCc71UNCCJthgnlL7HgIhAKSYf7P7CFO2wWdAt8LBMrsLoip9lytrinj0JR8R\n" +
            "CYK9AHcAtz77JN+cTbp18jnFulj0bF38Qs96nzXEnh0JgSXttJkAAAGHJM+cZAAA\n" +
            "BAMASDBGAiEAj8nBf1ihput8Gb8qCqVgvqAxPv9t4xLVhWg3tqv8gGMCIQDPiNbu\n" +
            "vsyOi9nE6pDm86nggExXRa13wwCtr2wjAn5IpDANBgkqhkiG9w0BAQsFAAOCAQEA\n" +
            "ezldM/NCUH58eXPZnbPaMMKrT5oNBxv+hypDy96+PyAqKtbC2bK+7sobGMZkfpG5\n" +
            "8dW0mFmfazzjgbZUj54ZVHG4KaHeit8Nq1s07wh2Jo1c2JQdKxEXAOItax/IOfEd\n" +
            "tqSg8AwSmhogQeiA7EXRspw4dYXL5uP/8jPPqByMI3PRmm3y7wyQLKNlNAfSgn7m\n" +
            "wkrZxMRAENML4JND5UKxg7zo9e/Wvf4UPtEVVZaEj6ZxOe4JljvErCtayaw03t5p\n" +
            "I18IAhXRpqm8JG1UGWjn49O8vkjB0bf/7iVXXI4rg6gGVia+HFuxKVGk5OQzo4Qd\n" +
            "wBl6yOc8tpUH3phFPYbiMg==\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator) throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Mon Mar 27 13:43:25 PDT 2023", System.out);
    }
}

class GoogleGTSR2 {

    // Owner: CN=GTS CA 1D8, O=Google Trust Services LLC, C=US
    // Issuer: CN=GTS Root R2, O=Google Trust Services LLC, C=US
    // Serial number: 219c15ac025a1b0a5c1d9d501
    // Valid from: Tue Oct 04 17:00:42 PDT 2022 until: Wed Sep 29 17:00:42 PDT 2027
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIFjDCCA3SgAwIBAgINAhnBWsAlobClwdnVATANBgkqhkiG9w0BAQsFADBHMQsw\n" +
            "CQYDVQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZpY2VzIExMQzEU\n" +
            "MBIGA1UEAxMLR1RTIFJvb3QgUjIwHhcNMjIxMDA1MDAwMDQyWhcNMjcwOTMwMDAw\n" +
            "MDQyWjBGMQswCQYDVQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZp\n" +
            "Y2VzIExMQzETMBEGA1UEAxMKR1RTIENBIDFEODCCASIwDQYJKoZIhvcNAQEBBQAD\n" +
            "ggEPADCCAQoCggEBAKgpBAgi9bhOp5nCIELI/W+1rMoxNH3isu2LuDI3pPEPYJ0o\n" +
            "YDxXB1zKHvUqn1VWDlF+K4vLPzjTRv2MUw8fHH9IAd/Rx+mrUHUxffTPU5O41tPj\n" +
            "OdzFRO+FOr5RqZfbtXWbEUNyv7wyyCYr9gaDvDeQgDnHTfHAafdoDracNLm2LS3r\n" +
            "8iznvJltsboRm+fBwTH99nHciN/h/hHEWlRriUGZ+Cz+5YVB9Tm4gAOByyYYbAa4\n" +
            "ES0PhzkIUHaq+56cTDVhK0DM5ZtnZJqV8amhBFssswPttAXT9pNCzoDLCtxeZ2Lw\n" +
            "r7bcaGaDcuDmv4j8zAw3BOR73O0Xk1VcBYPBBUcCAwEAAaOCAXYwggFyMA4GA1Ud\n" +
            "DwEB/wQEAwIBhjAdBgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwEgYDVR0T\n" +
            "AQH/BAgwBgEB/wIBADAdBgNVHQ4EFgQUkPhQ+ueQJcnkJ30S3UdY53QPtmowHwYD\n" +
            "VR0jBBgwFoAUu//KjiOfT5nK2+JopqUVJxce2Q4waAYIKwYBBQUHAQEEXDBaMCYG\n" +
            "CCsGAQUFBzABhhpodHRwOi8vb2NzcC5wa2kuZ29vZy9ndHNyMjAwBggrBgEFBQcw\n" +
            "AoYkaHR0cDovL3BraS5nb29nL3JlcG8vY2VydHMvZ3RzcjIuZGVyMDQGA1UdHwQt\n" +
            "MCswKaAnoCWGI2h0dHA6Ly9jcmwucGtpLmdvb2cvZ3RzcjIvZ3RzcjIuY3JsME0G\n" +
            "A1UdIARGMEQwCAYGZ4EMAQIBMDgGCisGAQQB1nkCBQMwKjAoBggrBgEFBQcCARYc\n" +
            "aHR0cHM6Ly9wa2kuZ29vZy9yZXBvc2l0b3J5LzANBgkqhkiG9w0BAQsFAAOCAgEA\n" +
            "q3rJUW9syti3qkV5WNXCpJj2WoCptfxOdIrojT4Q1CIGzruFu4GXB8pfkJu7iU8k\n" +
            "Aklel6RCn7MG/aI12ndmvUsW86e6UJDWEMz1CsQPA92AOsktAVXiGVDx3RAiPfP2\n" +
            "9W9yNwlImSVZhGNQISC0SueK7QOv+mHHWE/7G0G0/YqAxMbVZzyrPYHfPUh0SD1g\n" +
            "k7qYjq9hGJB7w7cfepZ2iPdKzlj/4aFOe04gho1zHMLJYIs03nb6uWg0AwX55SSu\n" +
            "KvehoYs1ItHdEV1J2XfATZpCn6jMTEB/JYERbXW0VWLUhdaZORtaayQoU5YXbgvg\n" +
            "bsPgqdIsPaxs/Chrp6zIKvs503YYcvs0GQSUQ1MFAWc+Loc39669T7WnL8Uu2yCO\n" +
            "RxjFp3+fhTVA5UYwL1vy4wPnNUoa4+CA6JypT6ODUWcXZa8pWOdyHpbg0IeL389D\n" +
            "s67kirG8/eKQxFzckbhL5AD8BJS3wkF7O7A8Gd+2VvSWhmEQzzOBHcvT/lqrCSe0\n" +
            "7R7CV/Pw4E9C2GBLGfw8opxGXrdfJRjU6nHf5c+tC4xIjH/i3PQjaIFLG3D60mav\n" +
            "0nkS92iorZl2dCiHTKxaD/J4B6VV03lpEcUdVg4WeGAmTClsXUnMOjCnlVYMLg9v\n" +
            "URq0LbylxbGBelBrCNyqBS5UO6+9F4/Yi4vzoIvvbJ0=\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=good.gtsr2.demo.pki.goog
    // Issuer: CN=GTS CA 1D8, O=Google Trust Services LLC, C=US
    // Serial number: 428fe99edb0df46e1008e4452f6cbfd2
    // Valid from: Mon Mar 27 12:52:12 PDT 2023 until: Sun Jun 25 12:52:11 PDT 2023
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIFdDCCBFygAwIBAgIQQo/pntsN9G4QCORFL2y/0jANBgkqhkiG9w0BAQsFADBG\n" +
            "MQswCQYDVQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZpY2VzIExM\n" +
            "QzETMBEGA1UEAxMKR1RTIENBIDFEODAeFw0yMzAzMjcxOTUyMTJaFw0yMzA2MjUx\n" +
            "OTUyMTFaMCMxITAfBgNVBAMTGGdvb2QuZ3RzcjIuZGVtby5wa2kuZ29vZzCCASIw\n" +
            "DQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMaC0h20vohsggOQ0XGL5ca3Gqyf\n" +
            "2n44PhYBrhzPpbq9/Mk9BKYYFy9osH0HwTFkYRYnI5fDeK6s/7svufiEwH8LtXK7\n" +
            "A3juxf3k65cJ8M5bbBwDDW7Prgp86ueUd6pzqv23rLPc9Kv6vvtNYzgaTd4COU38\n" +
            "3zFnuudAh8gvEbIQD+Nqis+kc4kEO3JfZBlAF883YRQZRpm6c4bWxKm1Atco53/6\n" +
            "fYOota/XUgdJ8zQWOH1f9iaKX3kiDn76djxT9v/8MrcK2gRkHJJDo72HtCPuhdt8\n" +
            "UkVLX4C3KF6eSUrgZ1gxA92ikAWxI4tn5D70yEffH0A7by0/b/C6uPMvXCECAwEA\n" +
            "AaOCAn8wggJ7MA4GA1UdDwEB/wQEAwIFoDATBgNVHSUEDDAKBggrBgEFBQcDATAM\n" +
            "BgNVHRMBAf8EAjAAMB0GA1UdDgQWBBTegr5Cc+1LmL/c1H3sXVKufKZE8DAfBgNV\n" +
            "HSMEGDAWgBSQ+FD655AlyeQnfRLdR1jndA+2ajB4BggrBgEFBQcBAQRsMGowNQYI\n" +
            "KwYBBQUHMAGGKWh0dHA6Ly9vY3NwLnBraS5nb29nL3MvZ3RzMWQ4L0FoZFdDWF9D\n" +
            "QUJFMDEGCCsGAQUFBzAChiVodHRwOi8vcGtpLmdvb2cvcmVwby9jZXJ0cy9ndHMx\n" +
            "ZDguZGVyMCMGA1UdEQQcMBqCGGdvb2QuZ3RzcjIuZGVtby5wa2kuZ29vZzAhBgNV\n" +
            "HSAEGjAYMAgGBmeBDAECATAMBgorBgEEAdZ5AgUDMDwGA1UdHwQ1MDMwMaAvoC2G\n" +
            "K2h0dHA6Ly9jcmxzLnBraS5nb29nL2d0czFkOC8tME5ITHA5Y0w5US5jcmwwggEE\n" +
            "BgorBgEEAdZ5AgQCBIH1BIHyAPAAdgB6MoxU2LcttiDqOOBSHumEFnAyE4VNO9Ir\n" +
            "wTpXo1LrUgAAAYck2PpFAAAEAwBHMEUCIAznUI2WdAkwXBvnx0a8Io6hnZReoXsd\n" +
            "Y+o+xpXqZsbbAiEAw/i7jWA43QWEMZz265nflCNxAS1W+s7nsZaKL512/S8AdgDo\n" +
            "PtDaPvUGNTLnVyi8iWvJA9PL0RFr7Otp4Xd9bQa9bgAAAYck2PoBAAAEAwBHMEUC\n" +
            "IHWqRE57W1pJJJAXrxFNMrjEO3f0YejAfi47mdyS1zJYAiEA4ye+achvGTYIMRnl\n" +
            "jwBlTsYQQYt7KAVt2VAGMRB4H8kwDQYJKoZIhvcNAQELBQADggEBAGf9hz7NJRow\n" +
            "veCSrfeVav2tDkx8s9VU7VD+lApip1mdqOGsqkCkeaA5hsGfhqleQFwsOAjduBFA\n" +
            "nSV6KgiqFsgHSuS9zuSp2aVe8xhxq6mpr4LngkeUDc32mB9tW9AMaiYp8UeYyFGq\n" +
            "hvjUb7/H2wFlT6qO+Qp/+hmfulKqNnrSzpZLIl+x2EBn3L6CFe5xaKzNaANgbShI\n" +
            "cQsyKdaUrSAzNJZWnHwaAyQ1msqqXXoVzKmjAGMgZrXZNxv8Lh9V1v+F9WHDIjeQ\n" +
            "TtahntIgq38eGtZAnyjdrUtfQwBlQI3zaE0n7n6Fq8ocglJE5woRlL/eTmSKiZr9\n" +
            "rrEY0sJ0fCw=\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=revoked.gtsr2.demo.pki.goog
    // Issuer: CN=GTS CA 1D8, O=Google Trust Services LLC, C=US
    // Serial number: df9af5c19e9dbdf6107cb03548ffbd06
    // Valid from: Mon Mar 27 12:45:09 PDT 2023 until: Sun Jun 25 12:45:08 PDT 2023
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIFejCCBGKgAwIBAgIRAN+a9cGenb32EHywNUj/vQYwDQYJKoZIhvcNAQELBQAw\n" +
            "RjELMAkGA1UEBhMCVVMxIjAgBgNVBAoTGUdvb2dsZSBUcnVzdCBTZXJ2aWNlcyBM\n" +
            "TEMxEzARBgNVBAMTCkdUUyBDQSAxRDgwHhcNMjMwMzI3MTk0NTA5WhcNMjMwNjI1\n" +
            "MTk0NTA4WjAmMSQwIgYDVQQDExtyZXZva2VkLmd0c3IyLmRlbW8ucGtpLmdvb2cw\n" +
            "ggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDFJUSh0aOOjj6BXJqBFDOD\n" +
            "GFjnr1VKDfWYdGWfB3QNhcbjz7qJRLeZDSYQZ3H2D5pkOQhl6xYLOZ1L0v+0TWW9\n" +
            "5lCXQ476jdZXzPlOC29gYFX4VzS9w92ochg0dUhHdzKcWsqBjqChZdudGydYfwNS\n" +
            "edZIhd4AcamVsXbCqAhS01Evo2hiBRlmMgryR9Ok2xRqbJiyvd8awhBIB4L0vMN+\n" +
            "CgMpWMgaV1nn+LjEa3bHisyNVsRLdDZXY6Bgq3hUQ9jQWJdK/vGxHqunqC5ByrqG\n" +
            "iN+4/+kK/PS8okkpAEAOXFoohogb6BQASMRgO/l50Mz8B24NGgWVLlWdaNysgU8f\n" +
            "AgMBAAGjggKBMIICfTAOBgNVHQ8BAf8EBAMCBaAwEwYDVR0lBAwwCgYIKwYBBQUH\n" +
            "AwEwDAYDVR0TAQH/BAIwADAdBgNVHQ4EFgQUh/wMqf9pabUzGDoQvsyHVaT1rjAw\n" +
            "HwYDVR0jBBgwFoAUkPhQ+ueQJcnkJ30S3UdY53QPtmoweAYIKwYBBQUHAQEEbDBq\n" +
            "MDUGCCsGAQUFBzABhilodHRwOi8vb2NzcC5wa2kuZ29vZy9zL2d0czFkOC9CdWF6\n" +
            "OFdQMnoybzAxBggrBgEFBQcwAoYlaHR0cDovL3BraS5nb29nL3JlcG8vY2VydHMv\n" +
            "Z3RzMWQ4LmRlcjAmBgNVHREEHzAdghtyZXZva2VkLmd0c3IyLmRlbW8ucGtpLmdv\n" +
            "b2cwIQYDVR0gBBowGDAIBgZngQwBAgEwDAYKKwYBBAHWeQIFAzA8BgNVHR8ENTAz\n" +
            "MDGgL6AthitodHRwOi8vY3Jscy5wa2kuZ29vZy9ndHMxZDgvLTBOSExwOWNMOVEu\n" +
            "Y3JsMIIBAwYKKwYBBAHWeQIEAgSB9ASB8QDvAHYAejKMVNi3LbYg6jjgUh7phBZw\n" +
            "MhOFTTvSK8E6V6NS61IAAAGHJNGpywAABAMARzBFAiEApXndD34BJ3oOCLvGoa5f\n" +
            "Xu0P6t4yf1pdCQONuLTSrX4CIDMp1N5/VKjClXqE/t2xux3mvJH2ceVECID4B69v\n" +
            "WfOhAHUA6D7Q2j71BjUy51covIlryQPTy9ERa+zraeF3fW0GvW4AAAGHJNGphwAA\n" +
            "BAMARjBEAiBa5aSnTCc2ceQj/asKFYRRGbwzXTnaDbvNMMeB4ogEXAIgZykyJVPh\n" +
            "4Sfkroi8tvV6dwxexp0dT2EXHAmr+/GzZU0wDQYJKoZIhvcNAQELBQADggEBAHVn\n" +
            "uWbk/OaljXKeyhlDCgdvnzJGCFQXwGyIJzNDkCs8k3iA1iwJKArvpkczxnCBxCPE\n" +
            "imW2MHWCayT9JXKuO4ppU0oTh6GYvRV6DV1OkuWXsna7+dGf3+tkm9k0wauI6J8X\n" +
            "H1T8Dq3W0+S+8UNSftduYSR1wTcN15OxIzlZ/FrV3LLRDxH2RKSsXfXBLgP1befh\n" +
            "m+8SPQTpZ5NdMl7my0gmVgNF5ZIbFiHYzJkF2vS4iXJCI6fTWyoA1u/7jQyHdLOy\n" +
            "pY0s6gKWEwwtpYC1lWI6ek/wLfuNrJbiRRiRs8e3HHQymn8K3T1PM+7n8huDy95b\n" +
            "f1EgLMjvEtx6xpIqrqg=\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator) throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Mon Mar 27 13:45:40 PDT 2023", System.out);
    }
}

class GoogleGTSR3 {

    // Owner: CN=GTS CA 2D3, O=Google Trust Services LLC, C=US
    // Issuer: CN=GTS Root R3, O=Google Trust Services LLC, C=US
    // Serial number: 21668d8d65bc4320e5b8e5e76
    // Valid from: Tue Oct 04 17:00:42 PDT 2022 until: Wed Sep 29 17:00:42 PDT 2027
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIDIDCCAqagAwIBAgINAhZo2NZbxDIOW45edjAKBggqhkjOPQQDAzBHMQswCQYD\n" +
            "VQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZpY2VzIExMQzEUMBIG\n" +
            "A1UEAxMLR1RTIFJvb3QgUjMwHhcNMjIxMDA1MDAwMDQyWhcNMjcwOTMwMDAwMDQy\n" +
            "WjBGMQswCQYDVQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZpY2Vz\n" +
            "IExMQzETMBEGA1UEAxMKR1RTIENBIDJEMzBZMBMGByqGSM49AgEGCCqGSM49AwEH\n" +
            "A0IABGQQXn8LoR0OtyBn+KkEav3utA7WFBgWEb/8bXVlW6xJLTZJIC04lsNmNKWJ\n" +
            "P/fwHYfrZcx1o4vvOUTO9OD/7pijggF2MIIBcjAOBgNVHQ8BAf8EBAMCAYYwHQYD\n" +
            "VR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMCMBIGA1UdEwEB/wQIMAYBAf8CAQAw\n" +
            "HQYDVR0OBBYEFL+pU78badiFKTSaaUPL1nrUmf9tMB8GA1UdIwQYMBaAFMHxJrqg\n" +
            "La6Fgc/T8SoSvbgKZ/28MGgGCCsGAQUFBwEBBFwwWjAmBggrBgEFBQcwAYYaaHR0\n" +
            "cDovL29jc3AucGtpLmdvb2cvZ3RzcjMwMAYIKwYBBQUHMAKGJGh0dHA6Ly9wa2ku\n" +
            "Z29vZy9yZXBvL2NlcnRzL2d0c3IzLmRlcjA0BgNVHR8ELTArMCmgJ6AlhiNodHRw\n" +
            "Oi8vY3JsLnBraS5nb29nL2d0c3IzL2d0c3IzLmNybDBNBgNVHSAERjBEMAgGBmeB\n" +
            "DAECATA4BgorBgEEAdZ5AgUDMCowKAYIKwYBBQUHAgEWHGh0dHBzOi8vcGtpLmdv\n" +
            "b2cvcmVwb3NpdG9yeS8wCgYIKoZIzj0EAwMDaAAwZQIxAO3wG4U11INX3hl2UyCn\n" +
            "0A/upBaO+BBzX1OiQx7UfmMXc65kqkdIcNzZc6G6EWnNVAIwBG0LuIKWXfYc+Wbk\n" +
            "STfMvwatUvd6QjdIKsYF0e8Hiaav+hLI0DzOuJcDPFtfYIyY\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=good.gtsr3.demo.pki.goog
    // Issuer: CN=GTS CA 2D3, O=Google Trust Services LLC, C=US
    // Serial number: 7d08ad6716e51d1210bfc149e3d0af19
    // Valid from: Mon Mar 27 12:37:41 PDT 2023 until: Sun Jun 25 12:37:40 PDT 2023
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIEszCCBFmgAwIBAgIQfQitZxblHRIQv8FJ49CvGTAKBggqhkjOPQQDAjBGMQsw\n" +
            "CQYDVQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZpY2VzIExMQzET\n" +
            "MBEGA1UEAxMKR1RTIENBIDJEMzAeFw0yMzAzMjcxOTM3NDFaFw0yMzA2MjUxOTM3\n" +
            "NDBaMCMxITAfBgNVBAMTGGdvb2QuZ3RzcjMuZGVtby5wa2kuZ29vZzCCASIwDQYJ\n" +
            "KoZIhvcNAQEBBQADggEPADCCAQoCggEBAL7R40feuILVPC65FhoVh3kZ8mJuEKpJ\n" +
            "SiSB9gbKRkaKBr4kHOm7+sa0RkAm3Zgbomd2JGiJbYYcQ4lY8MMlXruFLLY+0AMf\n" +
            "Pf5mQbn6i+oSyfaNwV0Hk1q1MhZL5WSKLywXS0NVw50JGQw/SiIRhmR22DdOtxuh\n" +
            "VC7ZOebYTbHzTBSYTxvoyJZ0bGUQMWQ0rI2lzOp+2kqSTDMmRejXUNm14ZrsdXUb\n" +
            "F8nOunZpT5ppESFvsK7TFrWJlAFHNVxJjPkNaRyfIaR7G+hORoV5tHGaNeTzmFkO\n" +
            "3ySGcRlvL41IWqBN4LwLiS6QN+Je7nIBDojEPTBVhPCzP++1uLKEKusCAwEAAaOC\n" +
            "An8wggJ7MA4GA1UdDwEB/wQEAwIFoDATBgNVHSUEDDAKBggrBgEFBQcDATAMBgNV\n" +
            "HRMBAf8EAjAAMB0GA1UdDgQWBBRRhq17jer1cVfi0eFV+LIwk+Lk8jAfBgNVHSME\n" +
            "GDAWgBS/qVO/G2nYhSk0mmlDy9Z61Jn/bTB4BggrBgEFBQcBAQRsMGowNQYIKwYB\n" +
            "BQUHMAGGKWh0dHA6Ly9vY3NwLnBraS5nb29nL3MvZ3RzMmQzL09KOENlY2cwdWNV\n" +
            "MDEGCCsGAQUFBzAChiVodHRwOi8vcGtpLmdvb2cvcmVwby9jZXJ0cy9ndHMyZDMu\n" +
            "ZGVyMCMGA1UdEQQcMBqCGGdvb2QuZ3RzcjMuZGVtby5wa2kuZ29vZzAhBgNVHSAE\n" +
            "GjAYMAgGBmeBDAECATAMBgorBgEEAdZ5AgUDMDwGA1UdHwQ1MDMwMaAvoC2GK2h0\n" +
            "dHA6Ly9jcmxzLnBraS5nb29nL2d0czJkMy9WREItNVdJSTVRSS5jcmwwggEEBgor\n" +
            "BgEEAdZ5AgQCBIH1BIHyAPAAdgDoPtDaPvUGNTLnVyi8iWvJA9PL0RFr7Otp4Xd9\n" +
            "bQa9bgAAAYckzOfmAAAEAwBHMEUCIF0wxIlFnHLMan20Gtbnia+mzuA1Re0dhoIS\n" +
            "wOAO7aC4AiEA7cYfSflOAA0DLxHsHAXpVs2LuLYlq34bSxbyUa85UyYAdgCzc3cH\n" +
            "4YRQ+GOG1gWp3BEJSnktsWcMC4fc8AMOeTalmgAAAYckzOf5AAAEAwBHMEUCICza\n" +
            "2nef9GWr9tF/ZXxhMYP15JQsdWPWmpQkdS/xUBWyAiEAs9AaeMarT7EaBVoSatAT\n" +
            "Poj6cOhdvF/uDOHigyQdVd8wCgYIKoZIzj0EAwIDSAAwRQIhALv6jaEFgAIe3NbX\n" +
            "87YEjhMMymK7wl435DQD9syoOEx2AiBbcYXr6nLNWA1pPoRiA1WvHgTVJFWftpYt\n" +
            "e8CkUXnIxA==\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=revoked.gtsr3.demo.pki.goog
    // Issuer: CN=GTS CA 2D3, O=Google Trust Services LLC, C=US
    // Serial number: 7ffa6a827df64c6010ebc47b5ca3eda7
    // Valid from: Mon Mar 27 12:45:58 PDT 2023 until: Sun Jun 25 12:45:57 PDT 2023
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIEuTCCBF+gAwIBAgIQf/pqgn32TGAQ68R7XKPtpzAKBggqhkjOPQQDAjBGMQsw\n" +
            "CQYDVQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZpY2VzIExMQzET\n" +
            "MBEGA1UEAxMKR1RTIENBIDJEMzAeFw0yMzAzMjcxOTQ1NThaFw0yMzA2MjUxOTQ1\n" +
            "NTdaMCYxJDAiBgNVBAMTG3Jldm9rZWQuZ3RzcjMuZGVtby5wa2kuZ29vZzCCASIw\n" +
            "DQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAKpn78KglifqiS3f5hPLH64og4aH\n" +
            "7a1tDBza2ebTLYB74i1u65EIENCyzvz6OYvh8kKzhqZMPFbORd8OCESzebjv/Dc2\n" +
            "BJJV498N3BfSZYWN+baVxKuOZ4HWXV5NyP85rEvbcaAWcmqvh++G88FOCTQvYd4D\n" +
            "/RKgAMptDjM+4X6V2NIRXcmOZJWZ2iItao76FARvbKH0D2UJLG4ENdOznRonnItP\n" +
            "74UEVfNCb/i7I+NMJYTuDA4/rr+AS6pttvsVM9pqWkIJqOloEVNcCyyr1buflfJO\n" +
            "j4A8Nz9fTUffpfApQnPi394iUcdCVyCrcjB2ta2eMR/3AyhiSXOmxcGjUcECAwEA\n" +
            "AaOCAoIwggJ+MA4GA1UdDwEB/wQEAwIFoDATBgNVHSUEDDAKBggrBgEFBQcDATAM\n" +
            "BgNVHRMBAf8EAjAAMB0GA1UdDgQWBBSg57WFIkW4b1eTcWX+qZsN+JEewTAfBgNV\n" +
            "HSMEGDAWgBS/qVO/G2nYhSk0mmlDy9Z61Jn/bTB4BggrBgEFBQcBAQRsMGowNQYI\n" +
            "KwYBBQUHMAGGKWh0dHA6Ly9vY3NwLnBraS5nb29nL3MvZ3RzMmQzL1pEZWExWTdT\n" +
            "SlBZMDEGCCsGAQUFBzAChiVodHRwOi8vcGtpLmdvb2cvcmVwby9jZXJ0cy9ndHMy\n" +
            "ZDMuZGVyMCYGA1UdEQQfMB2CG3Jldm9rZWQuZ3RzcjMuZGVtby5wa2kuZ29vZzAh\n" +
            "BgNVHSAEGjAYMAgGBmeBDAECATAMBgorBgEEAdZ5AgUDMDwGA1UdHwQ1MDMwMaAv\n" +
            "oC2GK2h0dHA6Ly9jcmxzLnBraS5nb29nL2d0czJkMy9WREItNVdJSTVRSS5jcmww\n" +
            "ggEEBgorBgEEAdZ5AgQCBIH1BIHyAPAAdQDoPtDaPvUGNTLnVyi8iWvJA9PL0RFr\n" +
            "7Otp4Xd9bQa9bgAAAYck00MJAAAEAwBGMEQCIALwbMReWy/zrvUwV1G5XOxN8koN\n" +
            "VJ1pp7s1d7ClE9ebAiBYWwJeccnfHLIh9AJTdeuN+R/pDzEudVBSC2rIdo3HhgB3\n" +
            "ALc++yTfnE26dfI5xbpY9Gxd/ELPep81xJ4dCYEl7bSZAAABhyTTQzMAAAQDAEgw\n" +
            "RgIhAOEO0oyiRgMNDdWvRTobr7sex2SUFsjpKmwenYAULrRiAiEA6uKFK1sbnJ1J\n" +
            "lW8Tw2G4jGpEFIc4C9duRbU6DIbGnckwCgYIKoZIzj0EAwIDSAAwRQIgN3byD4lu\n" +
            "a8A0hzUR1OnPoXSyfus6HOhmBozH6coY9MICIQDsT5jj5GKVtxtlcki5iE08K70Z\n" +
            "gt/tkcE1Fkk4RsZORA==\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator) throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Mon Mar 27 13:47:24 PDT 2023", System.out);
    }
}

class GoogleGTSR4 {

    // Owner: CN=GTS CA 2P2, O=Google Trust Services LLC, C=US
    // Issuer: CN=GTS Root R4, O=Google Trust Services LLC, C=US
    // Serial number: 2166825e1700440612491f540
    // Valid from: Tue Oct 04 17:00:42 PDT 2022 until: Wed Sep 29 17:00:42 PDT 2027
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIDITCCAqagAwIBAgINAhZoJeFwBEBhJJH1QDAKBggqhkjOPQQDAzBHMQswCQYD\n" +
            "VQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZpY2VzIExMQzEUMBIG\n" +
            "A1UEAxMLR1RTIFJvb3QgUjQwHhcNMjIxMDA1MDAwMDQyWhcNMjcwOTMwMDAwMDQy\n" +
            "WjBGMQswCQYDVQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZpY2Vz\n" +
            "IExMQzETMBEGA1UEAxMKR1RTIENBIDJQMjBZMBMGByqGSM49AgEGCCqGSM49AwEH\n" +
            "A0IABKdQkzjAHqOUsb/TkH7cz5lRtD374tNZ8rYrCUb1mxypE+VmCb1Jgzq+93tR\n" +
            "dE78GRzPI4+q6raha1TEyWgoniOjggF2MIIBcjAOBgNVHQ8BAf8EBAMCAYYwHQYD\n" +
            "VR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMCMBIGA1UdEwEB/wQIMAYBAf8CAQAw\n" +
            "HQYDVR0OBBYEFIcjqVBIDgeJVApxMPYz0gpH9p2sMB8GA1UdIwQYMBaAFIBM1ut0\n" +
            "/0k2o9XY/LU+xWrwlB2MMGgGCCsGAQUFBwEBBFwwWjAmBggrBgEFBQcwAYYaaHR0\n" +
            "cDovL29jc3AucGtpLmdvb2cvZ3RzcjQwMAYIKwYBBQUHMAKGJGh0dHA6Ly9wa2ku\n" +
            "Z29vZy9yZXBvL2NlcnRzL2d0c3I0LmRlcjA0BgNVHR8ELTArMCmgJ6AlhiNodHRw\n" +
            "Oi8vY3JsLnBraS5nb29nL2d0c3I0L2d0c3I0LmNybDBNBgNVHSAERjBEMAgGBmeB\n" +
            "DAECATA4BgorBgEEAdZ5AgUDMCowKAYIKwYBBQUHAgEWHGh0dHBzOi8vcGtpLmdv\n" +
            "b2cvcmVwb3NpdG9yeS8wCgYIKoZIzj0EAwMDaQAwZgIxAMnbIiQb5fsdexUuVGoB\n" +
            "MVwsDPGd7VC13Y0OBezt7FqFHDwqm8nnVdV/FkNyXNv9/AIxAN51NGqMcbexMOYK\n" +
            "pLC0zXfjNwvqBsZhmzCCQIM6MVyBID0rjjxPu7laIaHqAu6T5Q==\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=good.gtsr4.demo.pki.goog
    // Issuer: CN=GTS CA 2P2, O=Google Trust Services LLC, C=US
    // Serial number: 743c4f78750e30f0d407a19254ba96a
    // Valid from: Mon Mar 27 12:40:42 PDT 2023 until: Sun Jun 25 12:40:41 PDT 2023
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIEsTCCBFegAwIBAgIQB0PE94dQ4w8NQHoZJUupajAKBggqhkjOPQQDAjBGMQsw\n" +
            "CQYDVQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZpY2VzIExMQzET\n" +
            "MBEGA1UEAxMKR1RTIENBIDJQMjAeFw0yMzAzMjcxOTQwNDJaFw0yMzA2MjUxOTQw\n" +
            "NDFaMCMxITAfBgNVBAMTGGdvb2QuZ3RzcjQuZGVtby5wa2kuZ29vZzCCASIwDQYJ\n" +
            "KoZIhvcNAQEBBQADggEPADCCAQoCggEBAOdkWBg3i5CxzH1dvlBoWHtIUyk78OAA\n" +
            "bZdq7pKWB8i8C9Rf089uQ+7jQWOmqCNxU+OXdjumPfk/4MQvvtkmaqKi7HCN1bvQ\n" +
            "0CrW7Zhi5jx11QuzEEZVdvXcchzmodp9GSl9t6zK/ItNiIYVisH9dqRWrZ/KZnO+\n" +
            "y13dlr5UXAXVvNKx1L4TjhGlam7IEJdrAjkLJk4wXAFhv9HaPNJnjj0306xNm2h+\n" +
            "VzldpMPlaXGN9JcGQdMVFpa9f0AI/r7SF7I2EDXaIKFToJ4jQurEGc3oxayiv9wB\n" +
            "QapXqSTbPztb5SPGdX1yawDeigNHf10tDqFzCpfI/AwLxagpA2YyyXMCAwEAAaOC\n" +
            "An0wggJ5MA4GA1UdDwEB/wQEAwIFoDATBgNVHSUEDDAKBggrBgEFBQcDATAMBgNV\n" +
            "HRMBAf8EAjAAMB0GA1UdDgQWBBTZs4UFHCFLlXnJswubCMxEhtgPmjAfBgNVHSME\n" +
            "GDAWgBSHI6lQSA4HiVQKcTD2M9IKR/adrDB4BggrBgEFBQcBAQRsMGowNQYIKwYB\n" +
            "BQUHMAGGKWh0dHA6Ly9vY3NwLnBraS5nb29nL3MvZ3RzMnAyL3dKWTY1eFNLQUNB\n" +
            "MDEGCCsGAQUFBzAChiVodHRwOi8vcGtpLmdvb2cvcmVwby9jZXJ0cy9ndHMycDIu\n" +
            "ZGVyMCMGA1UdEQQcMBqCGGdvb2QuZ3RzcjQuZGVtby5wa2kuZ29vZzAhBgNVHSAE\n" +
            "GjAYMAgGBmeBDAECATAMBgorBgEEAdZ5AgUDMDwGA1UdHwQ1MDMwMaAvoC2GK2h0\n" +
            "dHA6Ly9jcmxzLnBraS5nb29nL2d0czJwMi94NWswT2ZlZ0o4OC5jcmwwggECBgor\n" +
            "BgEEAdZ5AgQCBIHzBIHwAO4AdQDoPtDaPvUGNTLnVyi8iWvJA9PL0RFr7Otp4Xd9\n" +
            "bQa9bgAAAYckzdBSAAAEAwBGMEQCICpm7XEQds5Pzk59Qhhlx3PjipAEVzxVJB3H\n" +
            "UmmGlHYKAiBG39UauHNNQDMYK2PEnILbFI0AvVWpCBUck4CHbs+9xAB1AHoyjFTY\n" +
            "ty22IOo44FIe6YQWcDIThU070ivBOlejUutSAAABhyTN0JoAAAQDAEYwRAIgekoP\n" +
            "yJFspEfqvzW/pzVtRn8oz1L/PBzw2NYRPFdDkRUCIG1uIaGUA7uqiILD6vvp/1VD\n" +
            "XriEIH8/qz/3qWqxsZanMAoGCCqGSM49BAMCA0gAMEUCIQCnpyh5H9Hn+f8nOFZp\n" +
            "wz7p+x5pmMVvPzah1g+EmoFO/wIgStidgVhudT/vpM2OH/oN30Na+EJJDqWxousN\n" +
            "6t9L8FQ=\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=revoked.gtsr4.demo.pki.goog
    // Issuer: CN=GTS CA 2P2, O=Google Trust Services LLC, C=US
    // Serial number: 6b2d650d4bc3bd3f11a595bf05187915
    // Valid from: Mon Mar 27 12:47:43 PDT 2023 until: Sun Jun 25 12:47:42 PDT 2023
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIEuDCCBF6gAwIBAgIQay1lDUvDvT8RpZW/BRh5FTAKBggqhkjOPQQDAjBGMQsw\n" +
            "CQYDVQQGEwJVUzEiMCAGA1UEChMZR29vZ2xlIFRydXN0IFNlcnZpY2VzIExMQzET\n" +
            "MBEGA1UEAxMKR1RTIENBIDJQMjAeFw0yMzAzMjcxOTQ3NDNaFw0yMzA2MjUxOTQ3\n" +
            "NDJaMCYxJDAiBgNVBAMTG3Jldm9rZWQuZ3RzcjQuZGVtby5wa2kuZ29vZzCCASIw\n" +
            "DQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAOEKoC1Zv/m2G8DrGkOgLq5TPSeC\n" +
            "X3cClcI6s4JS5Cld2DKX7m4P8rXAxJyVHvlmkxZQoD6Y7JxsavlJ/Yw0qdqkNLTv\n" +
            "kviEiLNYEn8Qu0SoRLNanzoFUINZkAZ4/0Lfvsrl9tTigLsCJ4jQauemGmGcmKUy\n" +
            "qsKisfrMC0ZG9EP9WRjc9WF13Jqe55+gZ7LqaAAoPVR/7J6T1VAKteaYaXrORtVF\n" +
            "uMeinE4c9YuxRCLa+3X1qqc3HAsvZEBOdb35fC0cN/ILktCQpq1Fj+QD4jfR6bVQ\n" +
            "E8eA6Jy+5qHSg2VjAm6wNLd5QkfE7D8uC9sYs638r48ahcXhy3zwpzGhuH0CAwEA\n" +
            "AaOCAoEwggJ9MA4GA1UdDwEB/wQEAwIFoDATBgNVHSUEDDAKBggrBgEFBQcDATAM\n" +
            "BgNVHRMBAf8EAjAAMB0GA1UdDgQWBBQl5Uh4jTR3l8PkcLdBwtwQXkUzBjAfBgNV\n" +
            "HSMEGDAWgBSHI6lQSA4HiVQKcTD2M9IKR/adrDB4BggrBgEFBQcBAQRsMGowNQYI\n" +
            "KwYBBQUHMAGGKWh0dHA6Ly9vY3NwLnBraS5nb29nL3MvZ3RzMnAyL3h5WmtBTEE3\n" +
            "aGY0MDEGCCsGAQUFBzAChiVodHRwOi8vcGtpLmdvb2cvcmVwby9jZXJ0cy9ndHMy\n" +
            "cDIuZGVyMCYGA1UdEQQfMB2CG3Jldm9rZWQuZ3RzcjQuZGVtby5wa2kuZ29vZzAh\n" +
            "BgNVHSAEGjAYMAgGBmeBDAECATAMBgorBgEEAdZ5AgUDMDwGA1UdHwQ1MDMwMaAv\n" +
            "oC2GK2h0dHA6Ly9jcmxzLnBraS5nb29nL2d0czJwMi9sU1htaTNxZWRoYy5jcmww\n" +
            "ggEDBgorBgEEAdZ5AgQCBIH0BIHxAO8AdgCt9776fP8QyIudPZwePhhqtGcpXc+x\n" +
            "DCTKhYY069yCigAAAYck1BYGAAAEAwBHMEUCIGM5ykDTU3mqgLIk+fPmVn6JGUXB\n" +
            "W4xouGUA1iiNs7G0AiEAtuWnV/J5llcxB7ZTwkCb6cviyv4Z6O396ZGW8GsrqAQA\n" +
            "dQC3Pvsk35xNunXyOcW6WPRsXfxCz3qfNcSeHQmBJe20mQAAAYck1BYIAAAEAwBG\n" +
            "MEQCIHcK1H025GIv8klzQGSZAL9NnuH5EzeGra0jRRg5RM4UAiAQaJyJDBkJRL/C\n" +
            "F9WCg9Lmp8bdsXkG5WPreI24ansAPTAKBggqhkjOPQQDAgNIADBFAiBehPLU7raP\n" +
            "509khaP9yiKiL3mbygtfQo4MDpBnd2RI6wIhAOdlQythGgU+nOENodsB+wUOQXOb\n" +
            "akcBOxrDWfyhxmpk\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator) throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Mon Mar 27 13:48:18 PDT 2023", System.out);
    }
}
