/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
        boolean ocspEnabled = false;

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            pathValidator.enableCRLCheck();
        } else {
            // OCSP check by default
            pathValidator.enableOCSPCheck();
            ocspEnabled = true;
        }

        new AmazonCA_1().runTest(pathValidator, ocspEnabled);
        new AmazonCA_2().runTest(pathValidator, ocspEnabled);
        new AmazonCA_3().runTest(pathValidator, ocspEnabled);
        new AmazonCA_4().runTest(pathValidator, ocspEnabled);
    }
}

class AmazonCA_1 {

    // Owner: CN=Amazon, OU=Server CA 1A, O=Amazon, C=US
    // Issuer: CN=Amazon Root CA 1, O=Amazon, C=US
    // Serial number: 67f9457508c648c09ca652e71791830e72592
    // Valid from: Wed Oct 21 17:00:00 PDT 2015 until: Sat Oct 18 17:00:00 PDT 2025
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIERzCCAy+gAwIBAgITBn+UV1CMZIwJymUucXkYMOclkjANBgkqhkiG9w0BAQsF\n" +
            "ADA5MQswCQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMRkwFwYDVQQDExBBbWF6\n" +
            "b24gUm9vdCBDQSAxMB4XDTE1MTAyMjAwMDAwMFoXDTI1MTAxOTAwMDAwMFowRjEL\n" +
            "MAkGA1UEBhMCVVMxDzANBgNVBAoTBkFtYXpvbjEVMBMGA1UECxMMU2VydmVyIENB\n" +
            "IDFBMQ8wDQYDVQQDEwZBbWF6b24wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEK\n" +
            "AoIBAQCeQM3XCsIZunv8bSJxOqkc/ed87uL76FDB7teBNThDRB+1J7aITuadbNfH\n" +
            "5ZfZykrdZ1qQLKxP6DwHOmJr9u2b4IxjUX9qUMuq4B02ghD2g6yU3YivEosZ7fpo\n" +
            "srD2TBN29JpgPGrOrpOE+ArZuIpBjdKFinemu6fTDD0NCeQlfyHXd1NOYyfYRLTa\n" +
            "xlpDqr/2M41BgSkWQfSPHHyRWNQgWBiGsIQaS8TK0g8OWi1ov78+2K9DWT+AHgXW\n" +
            "AanjZK91GfygPXJYSlAGxSiBAwH/KhAMifhaoFYAbH0Yuohmd85B45G2xVsop4TM\n" +
            "Dsl007U7qnS7sdJ4jYGzEvva/a95AgMBAAGjggE5MIIBNTASBgNVHRMBAf8ECDAG\n" +
            "AQH/AgEAMA4GA1UdDwEB/wQEAwIBhjAdBgNVHQ4EFgQUYtRCXoZwdWqQvMa40k1g\n" +
            "wjS6UTowHwYDVR0jBBgwFoAUhBjMhTTsvAyUlC4IWZzHshBOCggwewYIKwYBBQUH\n" +
            "AQEEbzBtMC8GCCsGAQUFBzABhiNodHRwOi8vb2NzcC5yb290Y2ExLmFtYXpvbnRy\n" +
            "dXN0LmNvbTA6BggrBgEFBQcwAoYuaHR0cDovL2NydC5yb290Y2ExLmFtYXpvbnRy\n" +
            "dXN0LmNvbS9yb290Y2ExLmNlcjA/BgNVHR8EODA2MDSgMqAwhi5odHRwOi8vY3Js\n" +
            "LnJvb3RjYTEuYW1hem9udHJ1c3QuY29tL3Jvb3RjYTEuY3JsMBEGA1UdIAQKMAgw\n" +
            "BgYEVR0gADANBgkqhkiG9w0BAQsFAAOCAQEAMHbSWHRFMzGNIE0qhN6gnRahTrTU\n" +
            "CDPwe7l9/q0IA+QBlrpUHnlAreetYeH1jB8uF3qXXzy22gpBU7NqulTkqSPByT1J\n" +
            "xOhpT2FpO5R3VAdMPdWfSEgtrED0jkmyUQrR1T+/A+nBLdJZeQcl+OqLgeY790JM\n" +
            "JJTsJnnI6FBWeTGhcDI4Y+n3KS3QCVePeWI7jx1dhrHcXH+QDX8Ywe31hV7YENdr\n" +
            "HDpUXrjK6eHN8gazy8G6pndXHFwHp4auiZbJbYAk/q1peOTRagD2JojcLkm+i3cD\n" +
            "843t4By6YT/PVlePU2PCWejkrJQnKQAPOov7IA8kuO2RDWuzE/zF6Hotdg==\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=good.sca1a.amazontrust.com
    // Issuer: CN=Amazon, OU=Server CA 1A, O=Amazon, C=US
    // Serial number: 75a5dd4b767bedc94a4239da65ed9dfef8218
    // Valid from: Fri Dec 17 12:21:50 PST 2021 until: Tue Jan 17 12:21:50 PST 2023
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIEIDCCAwigAwIBAgITB1pd1LdnvtyUpCOdpl7Z3++CGDANBgkqhkiG9w0BAQsF\n" +
            "ADBGMQswCQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMRUwEwYDVQQLEwxTZXJ2\n" +
            "ZXIgQ0EgMUExDzANBgNVBAMTBkFtYXpvbjAeFw0yMTEyMTcyMDIxNTBaFw0yMzAx\n" +
            "MTcyMDIxNTBaMCUxIzAhBgNVBAMTGmdvb2Quc2NhMWEuYW1hem9udHJ1c3QuY29t\n" +
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA5SadXE1HIzUp4ob40roo\n" +
            "qBiJy57vLcZklkWoxRU2JtIauuZUl8fLT/KOjzW71fqMMTxnvEbtKtRtZKDFjrg7\n" +
            "uPf8Q1J9tqxme6iFlrBlou+moQQ7Spi3H9q7v08vX19XIREGIwHbicbxVujdeA0w\n" +
            "G0fGMlw+Gs8GNiBQplr+oXC7i2CoPmwnR/T8iHjCEznKQIMxiZL4gOHLwh4EKdBA\n" +
            "auirpTq0iXUtC2BcM/w1Zx1UTLu0idmclcxVSYE8hXfV8e7JGpNI1gCqkgrskof3\n" +
            "A6CMCIH/D1VETFtGKn+gGWenWwnELmKuvHObQGXmcwOV3aXBdNFTmfzcshwqm/mE\n" +
            "zQIDAQABo4IBJjCCASIwDgYDVR0PAQH/BAQDAgWgMB0GA1UdDgQWBBTURzXdgGMB\n" +
            "tNyiP16WXB1oM2qqmzAfBgNVHSMEGDAWgBRi1EJehnB1apC8xrjSTWDCNLpROjAd\n" +
            "BgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwdQYIKwYBBQUHAQEEaTBnMC0G\n" +
            "CCsGAQUFBzABhiFodHRwOi8vb2NzcC5zY2ExYS5hbWF6b250cnVzdC5jb20wNgYI\n" +
            "KwYBBQUHMAKGKmh0dHA6Ly9jcnQuc2NhMWEuYW1hem9udHJ1c3QuY29tL3NjYTFh\n" +
            "LmNlcjAlBgNVHREEHjAcghpnb29kLnNjYTFhLmFtYXpvbnRydXN0LmNvbTATBgNV\n" +
            "HSAEDDAKMAgGBmeBDAECATANBgkqhkiG9w0BAQsFAAOCAQEAVNyn7lB3IOstAJj+\n" +
            "avkPfojb+QaUpFjnkKyb7c5kUBEWaaEl27W58OLoIHoEJvfOypv2bTq1fuIx9P88\n" +
            "1HP7DrI7vBtfnAgyIjF2mzL6Jyt7buR7u/cXTO0fsl/uk3wfrJBl860/Nab+WYoj\n" +
            "pvJm0b75WVnU30Khy/xrhNfN2nvCJ5VMoHqV6KnKrMjA5KpdeTvVaIgyxtV6B8vY\n" +
            "VsBbtzJ6n8mN7N8YkEkHV6TG7l+FVPHQdJFtD/qhTd5C4uu4XUehxOta894hLy6z\n" +
            "8Mv9BGtmwyUIEd0KQQdkXrWx/iAq6zo0imAeN/s8tjqAzxnw6M5F9cDqjqkYqgXZ\n" +
            "eIkPBA==\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=revoked.sca1a.amazontrust.com
    // Issuer: CN=Amazon, OU=Server CA 1A, O=Amazon, C=US
    // Serial number: 75a5de4434092b2cd6ed81eb5e6248e1e5f2a
    // Valid from: Fri Dec 17 12:25:17 PST 2021 until: Tue Jan 17 12:25:17 PST 2023
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIEJjCCAw6gAwIBAgITB1pd5ENAkrLNbtgeteYkjh5fKjANBgkqhkiG9w0BAQsF\n" +
            "ADBGMQswCQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMRUwEwYDVQQLEwxTZXJ2\n" +
            "ZXIgQ0EgMUExDzANBgNVBAMTBkFtYXpvbjAeFw0yMTEyMTcyMDI1MTdaFw0yMzAx\n" +
            "MTcyMDI1MTdaMCgxJjAkBgNVBAMTHXJldm9rZWQuc2NhMWEuYW1hem9udHJ1c3Qu\n" +
            "Y29tMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqYk4ZkF9yJgRa0fL\n" +
            "96gmxwlJlyvsQmqumxUGw0u1L+nDgGMFD1bHILOw2AO+feNy8kuTnJVb+zN+2f6l\n" +
            "rMGM1sGKh8W/ZRIdvmcdeZ2kEDyxLotMRXDQ6hJXDj30DSAYNkdqairJItdcev8+\n" +
            "t9LRRNRQwL0sXf5FITQPBnlVCrF9Q42p9hhYUhvsS8jSWPIvUbZajOXKs6AfxyPV\n" +
            "2Q7TybgnRlawznXxflPzXRMpCSQZ9WdI/kYbFOjDNtYA05EI4d8IYm+C5U1eJT30\n" +
            "dKFeU0xzFsrPirzifFMPIhXKxS5rUELuFRUq4sFTN28Sj7Ij/rr+O9Im8jJZq0lo\n" +
            "bqLoQwIDAQABo4IBKTCCASUwDgYDVR0PAQH/BAQDAgWgMB0GA1UdDgQWBBRugPQP\n" +
            "CWEwQp0pw2dEMw/gT7F4gzAfBgNVHSMEGDAWgBRi1EJehnB1apC8xrjSTWDCNLpR\n" +
            "OjAdBgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwdQYIKwYBBQUHAQEEaTBn\n" +
            "MC0GCCsGAQUFBzABhiFodHRwOi8vb2NzcC5zY2ExYS5hbWF6b250cnVzdC5jb20w\n" +
            "NgYIKwYBBQUHMAKGKmh0dHA6Ly9jcnQuc2NhMWEuYW1hem9udHJ1c3QuY29tL3Nj\n" +
            "YTFhLmNlcjAoBgNVHREEITAfgh1yZXZva2VkLnNjYTFhLmFtYXpvbnRydXN0LmNv\n" +
            "bTATBgNVHSAEDDAKMAgGBmeBDAECATANBgkqhkiG9w0BAQsFAAOCAQEAQF9QvedW\n" +
            "gqD5LPsZ5cg+DkGFBVqhWgsvp8so4gmKHklSHvisEek/Yfi7tvHCUAP2P0MuV/49\n" +
            "O2A+1tXQL1+hVM1auSfDOQdUy4xsKSWV+PofQe82iz+6dwRf+HNgOtyNcQ6aGD3t\n" +
            "87DXnJPkBTEPHGxDkjnOwurSffaV1m00bxfb6T1Txvyjs9ClnZf68Jv6oj+2rbs1\n" +
            "+TqKXP0Ma3AgXB37Cq2ozYzpAxy9GBIKIahGX2d2qsuZ2aj6XwJwUayIuU0WTOHK\n" +
            "eeXvKS2uvY9UaIvTeepSWXyAbBMKagQhgAtf3X6ILodQi5Gk7lCuY48oArKziTgN\n" +
            "vB7mK7JqaM2P4g==\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator, boolean ocspEnabled) throws Exception {
        // EE certificates don't have CRLDP extension
        if (!ocspEnabled){
            pathValidator.validate(new String[]{INT},
                    ValidatePathWithParams.Status.GOOD, null, System.out);

            return;
        }

        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Fri Dec 17 12:28:05 PST 2021", System.out);
    }
}

class AmazonCA_2 {

    // Owner: CN=Amazon, OU=Server CA 2A, O=Amazon, C=US
    // Issuer: CN=Amazon Root CA 2, O=Amazon, C=US
    // Serial number: 67f945755f187a91f8163f3e624620177ff38
    // Valid from: Wed Oct 21 17:00:00 PDT 2015 until: Sat Oct 18 17:00:00 PDT 2025
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGRzCCBC+gAwIBAgITBn+UV1Xxh6kfgWPz5iRiAXf/ODANBgkqhkiG9w0BAQwF\n" +
            "ADA5MQswCQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMRkwFwYDVQQDExBBbWF6\n" +
            "b24gUm9vdCBDQSAyMB4XDTE1MTAyMjAwMDAwMFoXDTI1MTAxOTAwMDAwMFowRjEL\n" +
            "MAkGA1UEBhMCVVMxDzANBgNVBAoTBkFtYXpvbjEVMBMGA1UECxMMU2VydmVyIENB\n" +
            "IDJBMQ8wDQYDVQQDEwZBbWF6b24wggIiMA0GCSqGSIb3DQEBAQUAA4ICDwAwggIK\n" +
            "AoICAQC0P8hSLewmrZ41CCPBQytZs5NBFMq5ztbnMf+kZUp9S25LPfjNW3zgC/6E\n" +
            "qCTWNVMMHhq7ez9IQJk48qbfBTLlZkuKnUWbA9vowrDfcxUN0mRE4B/TJbveXyTf\n" +
            "vE91iDlqDrERecE9D8sdjzURrtHTp27lZdRkXFvfEVCq4hl3sHkzjodisaQthLp1\n" +
            "gLsiA7vKt+8zcL4Aeq52UyYb8r4/jdZ3KaQp8O/T4VwDCRKm8ey3kttpJWaflci7\n" +
            "eRzNjY7gE3NMANVXCeQwOBfH2GjINFCObmPsqiBuoAnsv2k5aQLNoU1OZk08ClXm\n" +
            "mEZ2rI5qZUTX1HuefBJnpMkPugFCw8afaHnB13SkLE7wxX8SZRdDIe5WiwyDL1tR\n" +
            "2+8lpz4JsMoFopHmD3GaHyjbN+hkOqHgLltwewOsiyM0u3CZphypN2KeD+1FLjnY\n" +
            "TgdIAd1FRgK2ZXDDrEdjnsSEfShKf0l4mFPSBs9E3U6sLmubDRXKLLLpa/dF4eKu\n" +
            "LEKS1bXYT28iM6D5gSCnzho5G4d18jQD/slmc5XmRo5Pig0RyBwDaLuxeIZuiJ0A\n" +
            "J6YFhffbrLYF5dEQl0cU+t3VBK5u/o1WkWXsZawU038lWn/AXerodT/pAcrtWA4E\n" +
            "NQEN09WEKMhZVPhqdwhF/Gusr04mQtKt7T2v6UMQvtVglv5E7wIDAQABo4IBOTCC\n" +
            "ATUwEgYDVR0TAQH/BAgwBgEB/wIBADAOBgNVHQ8BAf8EBAMCAYYwHQYDVR0OBBYE\n" +
            "FNpDStD8AcBLv1gnjHbNCoHzlC70MB8GA1UdIwQYMBaAFLAM8Eww9AVYAkj9M+VS\n" +
            "r0uE42ZSMHsGCCsGAQUFBwEBBG8wbTAvBggrBgEFBQcwAYYjaHR0cDovL29jc3Au\n" +
            "cm9vdGNhMi5hbWF6b250cnVzdC5jb20wOgYIKwYBBQUHMAKGLmh0dHA6Ly9jcnQu\n" +
            "cm9vdGNhMi5hbWF6b250cnVzdC5jb20vcm9vdGNhMi5jZXIwPwYDVR0fBDgwNjA0\n" +
            "oDKgMIYuaHR0cDovL2NybC5yb290Y2EyLmFtYXpvbnRydXN0LmNvbS9yb290Y2Ey\n" +
            "LmNybDARBgNVHSAECjAIMAYGBFUdIAAwDQYJKoZIhvcNAQEMBQADggIBAEO5W+iF\n" +
            "yChjDyyrmiwFupVWQ0Xy2ReFNQiZq7XKVHvsLQe01moSLnxcBxioOPBKt1KkZO7w\n" +
            "Gcbmke0+7AxLaG/F5NPnzRtK1/pRhXQ0XdU8pVh/1/h4GoqRlZ/eN0JDarUhZPkV\n" +
            "kSr96LUYDTxcsAidF7zkzWfmtcJg/Aw8mi14xKVEa6aVyKu54c8kKkdlt0WaigOv\n" +
            "Z/xYhxp24AfoFKaIraDNdsD8q2N7eDYeN4WGLzNSlil+iFjzflI9mq1hTuI/ZNjV\n" +
            "rbvob6FUQ8Cc524gMjbpZCNuZ1gfXzwwhGp0AnQF6CJsWF9uwPpZEVFnnnfiWH3M\n" +
            "oup41EvBhqaAqOlny0sm5pI82nRUCAE3DLkJ1+eAtdQaYblZQkQrRyTuPmJEm+5y\n" +
            "QwdDVw6uHc5OsSj/tyhh8zJ2Xq3zgh3dMONGjJEysxGaCoIb+61PWwMy2dIarVwI\n" +
            "r+c+AY+3PrhgBspNdWZ87JzNHii7ksdjUSVGTTy1vGXgPYrv0lp0IMnKaZP58xiw\n" +
            "rDx7uTlQuPVWNOZvCaT3ZcoxTsNKNscIUe+WJjWx5hdzpv/oksDPY5ltZ0j3hlDS\n" +
            "D+Itk95/cNJVRM/0HpxI1SX9MTZtOSJoEDdUtOpVaOuBAvEK4gvTzdt0r5L+fuI6\n" +
            "o5LAuRo/LO1xVRH49KFRoaznzU3Ch9+kbPb3\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=good.sca2a.amazontrust.com
    // Issuer: CN=Amazon, OU=Server CA 2A, O=Amazon, C=US
    // Serial number: 75a5dd7d82269ed466af69794f34050bdffa2
    // Valid from: Fri Dec 17 12:22:32 PST 2021 until: Tue Jan 17 12:22:32 PST 2023
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGIDCCBAigAwIBAgITB1pd19giae1GavaXlPNAUL3/ojANBgkqhkiG9w0BAQwF\n" +
            "ADBGMQswCQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMRUwEwYDVQQLEwxTZXJ2\n" +
            "ZXIgQ0EgMkExDzANBgNVBAMTBkFtYXpvbjAeFw0yMTEyMTcyMDIyMzJaFw0yMzAx\n" +
            "MTcyMDIyMzJaMCUxIzAhBgNVBAMTGmdvb2Quc2NhMmEuYW1hem9udHJ1c3QuY29t\n" +
            "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAsCQonlc6fSTDJbH2y6wC\n" +
            "mLeTD3noluSM4LPO53RgLTUvNqrxh/iy9jDGgYP2xN8GGngRI8C65jZqGpJb0Hcp\n" +
            "ADYssYKWcTR5OH8rUVsJ6DkJLx0AUOG+iJcCaqPudkw7WBReFEok7E058gCTbXps\n" +
            "kNRT3w92CzzXa+49yxaAP0I6AQ9BqZP6gbAR1hmd9BDMCdak1JIswGVC3wGAKJFi\n" +
            "c3FS3YeY7VyuXofeEwutvMH4Iag9DZU2puqskrGSmtrVju8CY6w1E/cmBWD9kfpu\n" +
            "Qet2LBZuzmws8XhCjU5cHOeA8pg2m7ZnyNBeZajg4hrbPq8ACjjDmEHiDgazoOGN\n" +
            "1mV1BXZ2qonK+zJAMqE/L0czEPjdROaF786pPY5Cpi1Rzk0R3KKjGhSHgzfCa2eX\n" +
            "cQjBtA7AxLkK+1cI18hYg+okaV+EBrkxXGzeyTjvWbliotIQ9utabXGqJvJtIDeX\n" +
            "OQSdSXlBKgwGTE5/Ju8/6NkJgSMEku/Q9SYvfkzPXrj5VAHgPz4KhholeC4A4hRd\n" +
            "Y3Xtr/U5Xr3fTzLdOcLDKYW4/OGCl8byjwx8bqO7q8YmgDg572Go3gUbNmlm2QN+\n" +
            "NaXhBhPrl4KoHzawApTcod3adhSQziIMGjKYoKhV+ZGNoaLe7IUX0jyX3zygRS6k\n" +
            "n6yeyeh1unDfqSvne9+hDEsCAwEAAaOCASYwggEiMA4GA1UdDwEB/wQEAwIFoDAd\n" +
            "BgNVHQ4EFgQU71fB1r7/l2pFd0ydSNEiGaD+9uIwHwYDVR0jBBgwFoAU2kNK0PwB\n" +
            "wEu/WCeMds0KgfOULvQwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMCMHUG\n" +
            "CCsGAQUFBwEBBGkwZzAtBggrBgEFBQcwAYYhaHR0cDovL29jc3Auc2NhMmEuYW1h\n" +
            "em9udHJ1c3QuY29tMDYGCCsGAQUFBzAChipodHRwOi8vY3J0LnNjYTJhLmFtYXpv\n" +
            "bnRydXN0LmNvbS9zY2EyYS5jZXIwJQYDVR0RBB4wHIIaZ29vZC5zY2EyYS5hbWF6\n" +
            "b250cnVzdC5jb20wEwYDVR0gBAwwCjAIBgZngQwBAgEwDQYJKoZIhvcNAQEMBQAD\n" +
            "ggIBAKULtBRmu4CtBTfBG6hXkBdFGneJlomw02h8dj7xkXof+DoLYtkuJ6XRp89f\n" +
            "9UgYJMBjwKaAFjZzcVYvTd8YKdXzCXy4phxuHTfaV6ZH0WyvOlcTXsfdhJA4oD1G\n" +
            "prB4/PaymwSbv8ZQAE3eg1hytLLlR9+YUS0HfpwaH/PIa0TzKG8Vuu5zKGSlJjeh\n" +
            "Thp/uMBC4twM558Jv2sxoUA5HjgPUyZW7r2eLFbOM1H4oR1US5zFYgzrEK1W12DO\n" +
            "t65mI2YHbDepm5FoizwWYe4uaDCqWjCgzQw8pMGoiDABMaoNQ83Zi8r2sLGibAlb\n" +
            "cVLcjsORsF6TNmYTW1KDT/9hXlOaAhFwfAwKg6cZw51WEg51sPdi5USk/oszavc5\n" +
            "Ft/IZaWSfkA1Xm0EyFwOwCOvGJIb9PWv5PfGZz4xnZlWhp6LfN31e3TagTUbzLVX\n" +
            "XwbDI1cofCl18z6pidXXCASBCAajQ8N4GxNP6qqX9ou0yOBEXxwVqIJLcu3tueCI\n" +
            "3Cb3rWfbybAVhuuP2ERKHJMY8XDCt0O/g8Kj6O69NABOWvNkU3ARzszGzgBfv4IR\n" +
            "jJJEskjxX7Q085iXlaRX/mu+TpTkqK1ZbpBB1Z2PeVMujP+qsWSWGTZBXuI8eqyU\n" +
            "dhq+VlyoVtWeMqKYMtakCJxnhwMZnn0sTzZk/Yno+k9Jn0Rk\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=revoked.sca2a.amazontrust.com
    // Issuer: CN=Amazon, OU=Server CA 2A, O=Amazon, C=US
    // Serial number: 75a5df2d3387cfe5fd4cad9ff00f8c882b98d
    // Valid from: Fri Dec 17 12:28:31 PST 2021 until: Tue Jan 17 12:28:31 PST 2023
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGJjCCBA6gAwIBAgITB1pd8tM4fP5f1MrZ/wD4yIK5jTANBgkqhkiG9w0BAQwF\n" +
            "ADBGMQswCQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMRUwEwYDVQQLEwxTZXJ2\n" +
            "ZXIgQ0EgMkExDzANBgNVBAMTBkFtYXpvbjAeFw0yMTEyMTcyMDI4MzFaFw0yMzAx\n" +
            "MTcyMDI4MzFaMCgxJjAkBgNVBAMTHXJldm9rZWQuc2NhMmEuYW1hem9udHJ1c3Qu\n" +
            "Y29tMIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAu/9+ky4Z5U24pBYd\n" +
            "6xyb1BGQHTXS5nW8QjLWx+xaunRitgIBB8ZZ8OzUmH2mp2S/9Vq1nqii9TvuzA9K\n" +
            "JJQZLK8K+OJX/ZwFdSxTgLcyeJ9cCswj/C3SBA1NopZ3DmEWeXlh7aZhl8IXB6kp\n" +
            "zI87Tg72F2JJokWNPYdx7xXhf/WVeDeNRkz1iE5UTwL+qaNuzT7S8BdnFWqa3l4a\n" +
            "Q1J/YVww0XRhsYJulNVGhoKNf71q8KWw8hJ/zgMxrBFywu7d3OBw6NX3bowZ+jMQ\n" +
            "apJEWiwUYOjH3XcOO6TiChwQMypBrcbGgrD/msTlvIBinHwpWaAgX0kT80+wH1Bq\n" +
            "mw72fEjeE/Y6EL6WIUr1HQdLhvBDxtPgxnAaxptmg126cF4jV/e+D+IGf6qpN1gR\n" +
            "JQC/0+AnASAJ0cGKjSODbl5miqtc0kFSReMsOJeT7gdoPCMg4gWyo62GSvdaAA0I\n" +
            "DA8a0HWLAzXU7SwbytTUTYeVI8QeNm2ZGKvMoHDWSDz69V6gGmNl/YSvyJ2zPOZL\n" +
            "8oRKRUCOA2LPdK0s7nebe0EBXF09FzzE4HdegRe7r86t6FE400W4wxwJjvjdHXcF\n" +
            "s9fI+mgofMvVuK2u3wTdHOrEbfm1GXmj3BlFBORUI11A7K0lmIA02M2jkAN13foe\n" +
            "rFLYg+28UjT4aN62zkynKD1iNwkCAwEAAaOCASkwggElMA4GA1UdDwEB/wQEAwIF\n" +
            "oDAdBgNVHQ4EFgQUOzuuTB9A8p71qwA3qxqOABf69nkwHwYDVR0jBBgwFoAU2kNK\n" +
            "0PwBwEu/WCeMds0KgfOULvQwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMC\n" +
            "MHUGCCsGAQUFBwEBBGkwZzAtBggrBgEFBQcwAYYhaHR0cDovL29jc3Auc2NhMmEu\n" +
            "YW1hem9udHJ1c3QuY29tMDYGCCsGAQUFBzAChipodHRwOi8vY3J0LnNjYTJhLmFt\n" +
            "YXpvbnRydXN0LmNvbS9zY2EyYS5jZXIwKAYDVR0RBCEwH4IdcmV2b2tlZC5zY2Ey\n" +
            "YS5hbWF6b250cnVzdC5jb20wEwYDVR0gBAwwCjAIBgZngQwBAgEwDQYJKoZIhvcN\n" +
            "AQEMBQADggIBALAPC6I/k/WqJ8dxt7yhhSKA5RyGjd16kh+zq57Cjy0Wmj3BtSFJ\n" +
            "l0652ULeHZDjhtEAEMFlWdxuuUJ82UhzPzujeVv5e8CLROYWp52Jb9CFPTwF54ow\n" +
            "0a6recetYvOHBTeQ0cmo3nY6Z8eHDRUdk/aGQku1cesntFOIWm+EDj7SDYnUm3Ub\n" +
            "ECdMv8entU5yjo/herVNMT6GGnKfjRxM0FWJWoHKKC/EPIka34VN6LOZO4Ftl9wI\n" +
            "Ms7w3EgweEqLOyaGSAFwzrcQwKkPBm8fW5CefDtB64CtC8NUuo+XOQ2/JlRnWGLk\n" +
            "CHxesJBUNk5c/IBDPLmyrKCLbGUqwsehQGQdSrLIH0857pTJi30D+/KDvgQynaay\n" +
            "zPWLrSJvXUOQ9Vir+RQtbiMOqUDXX15Vty2mxLqjos1zCAxgrorZ7H2OSBZIWYzE\n" +
            "8UgF1/vOlAtMjYyLLgb2UyqAY2HybKjtYYAyV/oIPjVRXygaOGkDZseqqXuslq5I\n" +
            "ZSDU5hF6Hy6D6gsCVdshswwuRg39248M79qsMDw0Xa7xGcwqdfwTHv4Rb3G/kTrA\n" +
            "8iR2YP/RdABKkTkUKRXs0kYPFoJ0wQPDD5slkLjdZNeezoNrw1rWEEUh1iildiRA\n" +
            "i1p+pwXSyZ+m5Gv0/W84DDhLmAdvFov5muga8UccNbHuObtt1vHIhHe1\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator, boolean ocspEnabled) throws Exception {
        // EE certificates don't have CRLDP extension
        if (!ocspEnabled){
            pathValidator.validate(new String[]{INT},
                    ValidatePathWithParams.Status.GOOD, null, System.out);

            return;
        }

        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Fri Dec 17 12:29:36 PST 2021", System.out);
    }
}

class AmazonCA_3 {

    // Owner: CN=Amazon, OU=Server CA 3A, O=Amazon, C=US
    // Issuer: CN=Amazon Root CA 3, O=Amazon, C=US
    // Serial number: 67f945758fe55b9ee3f75831d47f07d226c8a
    // Valid from: Wed Oct 21 17:00:00 PDT 2015 until: Sat Oct 18 17:00:00 PDT 2025
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIICuzCCAmGgAwIBAgITBn+UV1j+VbnuP3WDHUfwfSJsijAKBggqhkjOPQQDAjA5\n" +
            "MQswCQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMRkwFwYDVQQDExBBbWF6b24g\n" +
            "Um9vdCBDQSAzMB4XDTE1MTAyMjAwMDAwMFoXDTI1MTAxOTAwMDAwMFowRjELMAkG\n" +
            "A1UEBhMCVVMxDzANBgNVBAoTBkFtYXpvbjEVMBMGA1UECxMMU2VydmVyIENBIDNB\n" +
            "MQ8wDQYDVQQDEwZBbWF6b24wWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATYcYsK\n" +
            "mYdR0Gj8Xz45E/lfcTTnXhg2EtAIYBIHyXv/ZQyyyCas1aptX/I5T1coT6XK181g\n" +
            "nB8hADuKfWlNoIYRo4IBOTCCATUwEgYDVR0TAQH/BAgwBgEB/wIBADAOBgNVHQ8B\n" +
            "Af8EBAMCAYYwHQYDVR0OBBYEFATc4JXl6LlrlKHvjFsxHhN+VZfaMB8GA1UdIwQY\n" +
            "MBaAFKu229cGnjesMIYHkXDHnMQZsXjAMHsGCCsGAQUFBwEBBG8wbTAvBggrBgEF\n" +
            "BQcwAYYjaHR0cDovL29jc3Aucm9vdGNhMy5hbWF6b250cnVzdC5jb20wOgYIKwYB\n" +
            "BQUHMAKGLmh0dHA6Ly9jcnQucm9vdGNhMy5hbWF6b250cnVzdC5jb20vcm9vdGNh\n" +
            "My5jZXIwPwYDVR0fBDgwNjA0oDKgMIYuaHR0cDovL2NybC5yb290Y2EzLmFtYXpv\n" +
            "bnRydXN0LmNvbS9yb290Y2EzLmNybDARBgNVHSAECjAIMAYGBFUdIAAwCgYIKoZI\n" +
            "zj0EAwIDSAAwRQIgOl/vux0qfxNm05W3eofa9lKwz6oKvdu6g6Sc0UlwgRcCIQCS\n" +
            "WSQ6F6JHLoeOWLyFFF658eNKEKbkEGMHz34gLX/N3g==\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=good.sca3a.amazontrust.com
    // Issuer: CN=Amazon, OU=Server CA 3A, O=Amazon, C=US
    // Serial number: 75a5dd9ec12f37f4bbed4bada4b75164a642f
    // Valid from: Fri Dec 17 12:23:00 PST 2021 until: Tue Jan 17 12:23:00 PST 2023
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIClDCCAjqgAwIBAgITB1pd2ewS839LvtS62kt1FkpkLzAKBggqhkjOPQQDAjBG\n" +
            "MQswCQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMRUwEwYDVQQLEwxTZXJ2ZXIg\n" +
            "Q0EgM0ExDzANBgNVBAMTBkFtYXpvbjAeFw0yMTEyMTcyMDIzMDBaFw0yMzAxMTcy\n" +
            "MDIzMDBaMCUxIzAhBgNVBAMTGmdvb2Quc2NhM2EuYW1hem9udHJ1c3QuY29tMFkw\n" +
            "EwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE275wkVMovF+U/fRduMcuthD8AYpYTUgc\n" +
            "qoEHEccF6eZYzoGlufHJCwtLHXk9qXeXtjZV8N90ksYahFV+oGFcpaOCASYwggEi\n" +
            "MA4GA1UdDwEB/wQEAwIHgDAdBgNVHQ4EFgQUS8gTB11XA49gH4IGAD6p3UilrIMw\n" +
            "HwYDVR0jBBgwFoAUBNzgleXouWuUoe+MWzEeE35Vl9owHQYDVR0lBBYwFAYIKwYB\n" +
            "BQUHAwEGCCsGAQUFBwMCMHUGCCsGAQUFBwEBBGkwZzAtBggrBgEFBQcwAYYhaHR0\n" +
            "cDovL29jc3Auc2NhM2EuYW1hem9udHJ1c3QuY29tMDYGCCsGAQUFBzAChipodHRw\n" +
            "Oi8vY3J0LnNjYTNhLmFtYXpvbnRydXN0LmNvbS9zY2EzYS5jZXIwJQYDVR0RBB4w\n" +
            "HIIaZ29vZC5zY2EzYS5hbWF6b250cnVzdC5jb20wEwYDVR0gBAwwCjAIBgZngQwB\n" +
            "AgEwCgYIKoZIzj0EAwIDSAAwRQIgRRteTEwQoqw95mKff0ydDMD1+YQbcN6QLw/a\n" +
            "NwDti9ICIQDYMNw6u0d5gaZZo/zizl1JRVAuSxoO5lNOrleaEOkImA==\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=revoked.sca3a.amazontrust.com
    // Issuer: CN=Amazon, OU=Server CA 3A, O=Amazon, C=US
    // Serial number: 75a5df9c88c0613777baba663000de147a26b
    // Valid from: Fri Dec 17 12:30:04 PST 2021 until: Tue Jan 17 12:30:04 PST 2023
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIICmzCCAkCgAwIBAgITB1pd+ciMBhN3e6umYwAN4UeiazAKBggqhkjOPQQDAjBG\n" +
            "MQswCQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMRUwEwYDVQQLEwxTZXJ2ZXIg\n" +
            "Q0EgM0ExDzANBgNVBAMTBkFtYXpvbjAeFw0yMTEyMTcyMDMwMDRaFw0yMzAxMTcy\n" +
            "MDMwMDRaMCgxJjAkBgNVBAMTHXJldm9rZWQuc2NhM2EuYW1hem9udHJ1c3QuY29t\n" +
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEbppBP3Dj0qoRHMB9VMTXhw2Fg8ef\n" +
            "o32r/Mu3IzT8T6kWCk3UqVDL3UIn3qVZLCW1nJfVc1d1EeSDvyjCL3u3f6OCASkw\n" +
            "ggElMA4GA1UdDwEB/wQEAwIHgDAdBgNVHQ4EFgQUv8lJ3W7O74zVj+0zhD4+rrqZ\n" +
            "yvMwHwYDVR0jBBgwFoAUBNzgleXouWuUoe+MWzEeE35Vl9owHQYDVR0lBBYwFAYI\n" +
            "KwYBBQUHAwEGCCsGAQUFBwMCMHUGCCsGAQUFBwEBBGkwZzAtBggrBgEFBQcwAYYh\n" +
            "aHR0cDovL29jc3Auc2NhM2EuYW1hem9udHJ1c3QuY29tMDYGCCsGAQUFBzAChipo\n" +
            "dHRwOi8vY3J0LnNjYTNhLmFtYXpvbnRydXN0LmNvbS9zY2EzYS5jZXIwKAYDVR0R\n" +
            "BCEwH4IdcmV2b2tlZC5zY2EzYS5hbWF6b250cnVzdC5jb20wEwYDVR0gBAwwCjAI\n" +
            "BgZngQwBAgEwCgYIKoZIzj0EAwIDSQAwRgIhAKrA0fOK4NKDKHTY8ESeVW3D/7NH\n" +
            "tbNdfcIXolAoFfmFAiEAylAsKdND8c4w69jlFTId0X8F/mrXzKfLFCQ+b/7jTto=\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator, boolean ocspEnabled) throws Exception {
        // EE certificates don't have CRLDP extension
        if (!ocspEnabled){
            pathValidator.validate(new String[]{INT},
                    ValidatePathWithParams.Status.GOOD, null, System.out);

            return;
        }

        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Fri Dec 17 12:30:37 PST 2021", System.out);
    }
}

class AmazonCA_4 {

    // Owner: CN=Amazon, OU=Server CA 4A, O=Amazon, C=US
    // Issuer: CN=Amazon Root CA 4, O=Amazon, C=US
    // Serial number: 67f94575a8862a9072e3239c37ceba1274e18
    // Valid from: Wed Oct 21 17:00:00 PDT 2015 until: Sat Oct 18 17:00:00 PDT 2025
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIC+TCCAn6gAwIBAgITBn+UV1qIYqkHLjI5w3zroSdOGDAKBggqhkjOPQQDAzA5\n" +
            "MQswCQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMRkwFwYDVQQDExBBbWF6b24g\n" +
            "Um9vdCBDQSA0MB4XDTE1MTAyMjAwMDAwMFoXDTI1MTAxOTAwMDAwMFowRjELMAkG\n" +
            "A1UEBhMCVVMxDzANBgNVBAoTBkFtYXpvbjEVMBMGA1UECxMMU2VydmVyIENBIDRB\n" +
            "MQ8wDQYDVQQDEwZBbWF6b24wdjAQBgcqhkjOPQIBBgUrgQQAIgNiAASRP0kIW0Ha\n" +
            "7+ORvEVhIS5gIgkH66X5W9vBRTX14oG/1elIyI6LbFZ+E5KAufL0XoWJGI1WbPRm\n" +
            "HW246FKSzF0wOEZZyxEROz6tuaVsnXRHRE76roS/Wr064uJpKH+Lv+SjggE5MIIB\n" +
            "NTASBgNVHRMBAf8ECDAGAQH/AgEAMA4GA1UdDwEB/wQEAwIBhjAdBgNVHQ4EFgQU\n" +
            "pSHN2+tTIZmqytlnQpQlsnv0wuMwHwYDVR0jBBgwFoAU0+zHOmVuzOHadppW+5zz\n" +
            "hm1X5YEwewYIKwYBBQUHAQEEbzBtMC8GCCsGAQUFBzABhiNodHRwOi8vb2NzcC5y\n" +
            "b290Y2E0LmFtYXpvbnRydXN0LmNvbTA6BggrBgEFBQcwAoYuaHR0cDovL2NydC5y\n" +
            "b290Y2E0LmFtYXpvbnRydXN0LmNvbS9yb290Y2E0LmNlcjA/BgNVHR8EODA2MDSg\n" +
            "MqAwhi5odHRwOi8vY3JsLnJvb3RjYTQuYW1hem9udHJ1c3QuY29tL3Jvb3RjYTQu\n" +
            "Y3JsMBEGA1UdIAQKMAgwBgYEVR0gADAKBggqhkjOPQQDAwNpADBmAjEA59RAOBaj\n" +
            "uh0rT/OOTWPEv6TBnb9XEadburBaXb8SSrR8il+NdkfS9WXRAzbwrG7LAjEA3ukD\n" +
            "1HrQq+WXHBM5sIuViJI/Zh7MOjsc159Q+dn36PBqLRq03AXqE/lRjnv8C5nj\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=good.sca4a.amazontrust.com
    // Issuer: CN=Amazon, OU=Server CA 4A, O=Amazon, C=US
    // Serial number: 75a5ddc1a4ea5a18110454883269df9409bf5
    // Valid from: Fri Dec 17 12:23:29 PST 2021 until: Tue Jan 17 12:23:29 PST 2023
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIC0TCCAlegAwIBAgITB1pd3BpOpaGBEEVIgyad+UCb9TAKBggqhkjOPQQDAzBG\n" +
            "MQswCQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMRUwEwYDVQQLEwxTZXJ2ZXIg\n" +
            "Q0EgNEExDzANBgNVBAMTBkFtYXpvbjAeFw0yMTEyMTcyMDIzMjlaFw0yMzAxMTcy\n" +
            "MDIzMjlaMCUxIzAhBgNVBAMTGmdvb2Quc2NhNGEuYW1hem9udHJ1c3QuY29tMHYw\n" +
            "EAYHKoZIzj0CAQYFK4EEACIDYgAE7VpccYyJsD19xB1owlNs9PGkXkjptJZK6eFY\n" +
            "q9Tc+CZLaOAhi47uuWsPwyIYB3vEIUXoWWKTvgycHhsmVQDQ2hLYMS+h9tQgnqVN\n" +
            "TDYpEnnBa6AUbTKXtJDLG+z+7Kd7o4IBJjCCASIwDgYDVR0PAQH/BAQDAgeAMB0G\n" +
            "A1UdDgQWBBRHzxN3jV4vU1PEmHmTqB8YXXoMYDAfBgNVHSMEGDAWgBSlIc3b61Mh\n" +
            "marK2WdClCWye/TC4zAdBgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwdQYI\n" +
            "KwYBBQUHAQEEaTBnMC0GCCsGAQUFBzABhiFodHRwOi8vb2NzcC5zY2E0YS5hbWF6\n" +
            "b250cnVzdC5jb20wNgYIKwYBBQUHMAKGKmh0dHA6Ly9jcnQuc2NhNGEuYW1hem9u\n" +
            "dHJ1c3QuY29tL3NjYTRhLmNlcjAlBgNVHREEHjAcghpnb29kLnNjYTRhLmFtYXpv\n" +
            "bnRydXN0LmNvbTATBgNVHSAEDDAKMAgGBmeBDAECATAKBggqhkjOPQQDAwNoADBl\n" +
            "AjEAyHMRGLsUVEufoih22dPfO5LrLpO4/2VzeNBbUvP/mOcwvMrrq1yQjot3CTdm\n" +
            "ZwnRAjAj2zmAM5asBZwuEN1pbEFgHdojio0O4oYvUsdMooLOKJsBD7hmgAdhpObO\n" +
            "Xv0oNIE=\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=revoked.sca4a.amazontrust.com
    // Issuer: CN=Amazon, OU=Server CA 4A, O=Amazon, C=US
    // Serial number: 75a5e0442d0fed2b11850ed6746a2200bb4af
    // Valid from: Fri Dec 17 12:32:23 PST 2021 until: Tue Jan 17 12:32:23 PST 2023
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIC1zCCAl2gAwIBAgITB1peBELQ/tKxGFDtZ0aiIAu0rzAKBggqhkjOPQQDAzBG\n" +
            "MQswCQYDVQQGEwJVUzEPMA0GA1UEChMGQW1hem9uMRUwEwYDVQQLEwxTZXJ2ZXIg\n" +
            "Q0EgNEExDzANBgNVBAMTBkFtYXpvbjAeFw0yMTEyMTcyMDMyMjNaFw0yMzAxMTcy\n" +
            "MDMyMjNaMCgxJjAkBgNVBAMTHXJldm9rZWQuc2NhNGEuYW1hem9udHJ1c3QuY29t\n" +
            "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEqxQKCDKJYXzA0uR3jyfk/ZRyPAJolRNI\n" +
            "xI3+vlQW7SqVs+MziCLFPuU68kf74a5/YFWK/bRdXrVue4IMbM8TKO2FwXIHn/Iw\n" +
            "udkJIG+CdqnL4IlH+tFf+l47vRzMS0TQo4IBKTCCASUwDgYDVR0PAQH/BAQDAgeA\n" +
            "MB0GA1UdDgQWBBR04rEvUxTzLh0OGHyMgrYanP7lqzAfBgNVHSMEGDAWgBSlIc3b\n" +
            "61MhmarK2WdClCWye/TC4zAdBgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIw\n" +
            "dQYIKwYBBQUHAQEEaTBnMC0GCCsGAQUFBzABhiFodHRwOi8vb2NzcC5zY2E0YS5h\n" +
            "bWF6b250cnVzdC5jb20wNgYIKwYBBQUHMAKGKmh0dHA6Ly9jcnQuc2NhNGEuYW1h\n" +
            "em9udHJ1c3QuY29tL3NjYTRhLmNlcjAoBgNVHREEITAfgh1yZXZva2VkLnNjYTRh\n" +
            "LmFtYXpvbnRydXN0LmNvbTATBgNVHSAEDDAKMAgGBmeBDAECATAKBggqhkjOPQQD\n" +
            "AwNoADBlAjEAgOyeHMBYmO9rfMgCnV4oOQ5PcjSAgotYwEGqFHA5+TuIPBTcdFar\n" +
            "J1j1JY+EirQ3AjAuGMJdyiQfAVi1n5wT1/2nIOIEGtV2/9CrNmhmjIzKrfu+HUUk\n" +
            "bduxD7hNhott7NE=\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator, boolean ocspEnabled) throws Exception {
        // EE certificates don't have CRLDP extension
        if (!ocspEnabled){
            pathValidator.validate(new String[]{INT},
                    ValidatePathWithParams.Status.GOOD, null, System.out);

            return;
        }

        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Fri Dec 17 12:32:59 PST 2021", System.out);
    }
}
