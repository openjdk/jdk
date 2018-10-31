/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8189131 8207059
 * @summary Interoperability tests with QuoVadis Root CA1, CA2, and CA3 CAs
 * @build ValidatePathWithParams
 * @run main/othervm -Djava.security.debug=certpath QuoVadisCA OCSP
 * @run main/othervm -Djava.security.debug=certpath QuoVadisCA CRL
 */

/*
 * Obtain TLS test artifacts for QuoVadis CAs from:
 *
 * https://www.quovadisglobal.com/QVRepository/TestCertificates.aspx
 *
 */
public class QuoVadisCA {

    public static void main(String[] args) throws Exception {

        ValidatePathWithParams pathValidator = new ValidatePathWithParams(null);

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            pathValidator.enableCRLCheck();
        } else {
            // OCSP check by default
            pathValidator.enableOCSPCheck();
        }

        new RootCA1G3().runTest(pathValidator);
        new RootCA2G3().runTest(pathValidator);
        new RootCA3G3().runTest(pathValidator);
    }
}

class RootCA1G3 {

    // Owner: CN=QuoVadis QVRCA1G3 SSL ICA, O=QuoVadis Limited, C=BM
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGszCCBJugAwIBAgIUdJ4w/GwP08WekbUIXvYTsQrO+a8wDQYJKoZIhvcNAQEL\n" +
            "BQAwSDELMAkGA1UEBhMCQk0xGTAXBgNVBAoTEFF1b1ZhZGlzIExpbWl0ZWQxHjAc\n" +
            "BgNVBAMTFVF1b1ZhZGlzIFJvb3QgQ0EgMSBHMzAeFw0xNzA0MTkxNTAzMzZaFw0y\n" +
            "NzA0MTkxNTAzMzZaMEwxCzAJBgNVBAYTAkJNMRkwFwYDVQQKDBBRdW9WYWRpcyBM\n" +
            "aW1pdGVkMSIwIAYDVQQDDBlRdW9WYWRpcyBRVlJDQTFHMyBTU0wgSUNBMIICIjAN\n" +
            "BgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAqVn6XxE+YKKifggi6EPcx7mOOrhA\n" +
            "HVxHFsFV/OR/dtQlx2oOTAGPpa8o3ZPVubtNH5QiiMBBiPDW1KqBaU+rmgUeGCj0\n" +
            "hWKbdNGRQ5h3rV+4Vhs45BYxQcUzGTZ+oobao8gNo1LuhPIhOQComGOjZtUP0+qQ\n" +
            "nXsWJn5004TvCzu7mmt3aTlMeyjSbpoXa3ojwU2BvUzJwcLg0BD49kNXZsM0JLbY\n" +
            "QgfEfluWFkb5QzjnE45sBni4LJNfSodhNB+mL/VmETO+0m/A1H6in1rG1n4Ao2G6\n" +
            "KYgtk9rXWfF3g7JqwuZUULfI0467h14oG1PzqVcLgZ1B+wrdyiBJJSpRmhf00xSB\n" +
            "WM/p93s2xkyQZ2Uh0b0tP90S6spwwpL8PSW3J8x46LaZDEVON/Gm9H891ZgwHLaf\n" +
            "3idGX93XHFafve8CrJFMhK2AZElwYaz2H6iJuPftyhR3oQIgLst8l+/2LoqDRyaI\n" +
            "6c+tVnk8LcvUgDEPuA70aNthQQ6PWA7iuI2Oies6GEPm7gKVNxIrg6rp2T9RghLm\n" +
            "vLnf6Gyn1ONLI7Ib3EyzjE8CJIAtor5KZcs8xm8iPNsDQza+1ugx8D8Zsla64vVw\n" +
            "w2W2qNH4orutsAQKRImtbDkEnMb3nGDe0ZPohVyw3Fy+b9g6MX7wQzFjIx3UkzZG\n" +
            "QQqGdIh940Qq3wUCAwEAAaOCAY8wggGLMBIGA1UdEwEB/wQIMAYBAf8CAQAwSQYD\n" +
            "VR0gBEIwQDA+BgRVHSAAMDYwNAYIKwYBBQUHAgEWKGh0dHA6Ly93d3cucXVvdmFk\n" +
            "aXNnbG9iYWwuY29tL3JlcG9zaXRvcnkwdAYIKwYBBQUHAQEEaDBmMCoGCCsGAQUF\n" +
            "BzABhh5odHRwOi8vb2NzcC5xdW92YWRpc2dsb2JhbC5jb20wOAYIKwYBBQUHMAKG\n" +
            "LGh0dHA6Ly90cnVzdC5xdW92YWRpc2dsb2JhbC5jb20vcXZyY2ExZzMuY3J0MA4G\n" +
            "A1UdDwEB/wQEAwIBBjAnBgNVHSUEIDAeBggrBgEFBQcDAQYIKwYBBQUHAwIGCCsG\n" +
            "AQUFBwMJMB8GA1UdIwQYMBaAFKOX1vNeohDhq0WfPBdkPO4BcJzMMDsGA1UdHwQ0\n" +
            "MDIwMKAuoCyGKmh0dHA6Ly9jcmwucXVvdmFkaXNnbG9iYWwuY29tL3F2cmNhMWcz\n" +
            "LmNybDAdBgNVHQ4EFgQUIAYNQkuk2dMocCdjvExpRiGBHTwwDQYJKoZIhvcNAQEL\n" +
            "BQADggIBAEu/Bea66BZPfGNE4Np+PCRrTag/U7EBK/Yhjmf3mHtFMZzZ94QLH1km\n" +
            "4iJ5dPKTR/+1iYYNHfO7fY2Lj/Tg/E+q2SEfA0n6Y/lYHAlbmnaYGGdtfTOjaQgL\n" +
            "0Bf0TmLPyc/gf9uKHe230vIaN4QcodBnCmCJOAk/lvIl7b7gRNPN/HuJNQlBohNx\n" +
            "ih9VAtLXJ6xO6Xfs5o8ZkZkHb2nG/M1yxySEyU3mqQ5PTgy8kg59szWr2ufT8PvL\n" +
            "JuyGNQmT/PHcLp2zaCC0+5Ra65umjhG8IW2haXu8g8aRAgr9ZRPrcgg2npLBA0Qf\n" +
            "MTEJPPptGx2GQgE+lMdn5Gff82d3Y35pDmxNTA7hy+4CnWKfmoey7ll8kwGxC+W1\n" +
            "OUVgzfdXcpsm+HP2z4E/zw6uB0cAFgMJbxgnm6ZW9+R2yEbD6EOpqR8HqCvhVkkv\n" +
            "CdQBNkk432pKD3+L7o6vkwONFOFWVpbXHIxDf9ys8Jr4B8qYWDUnR6jz/HG9aWPV\n" +
            "k4vBYYWuahANZCfCKH2B9SqCdK6DjwKihYmallClwsUQnSwW8H7xqmLtAHX0ek7z\n" +
            "1Ipj/BNS6c52cPxeAoFbUcVt6+M8xURIJ5qrobTYVaJ8AtfW+3Ml2oqT/EiItXOk\n" +
            "W1319hZuAGD5qaG3dg9aLYUqpD948xJVhYVxwIIwvL4G9ZEVyYmE\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=qvsslrca1g3-ssl-v.quovadisglobal.com, O=QuoVadis Limited, L=Hamilton,
    // ST=Pembroke, C=BM
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGJzCCBA+gAwIBAgIUGCzNOZhcLiPYbOjRFAp5n04dPNowDQYJKoZIhvcNAQEL\n" +
            "BQAwTDELMAkGA1UEBhMCQk0xGTAXBgNVBAoMEFF1b1ZhZGlzIExpbWl0ZWQxIjAg\n" +
            "BgNVBAMMGVF1b1ZhZGlzIFFWUkNBMUczIFNTTCBJQ0EwHhcNMTcwNTAyMTcwMDA4\n" +
            "WhcNMjAwNTAyMTcxMDAwWjB9MQswCQYDVQQGEwJCTTERMA8GA1UECAwIUGVtYnJv\n" +
            "a2UxETAPBgNVBAcMCEhhbWlsdG9uMRkwFwYDVQQKDBBRdW9WYWRpcyBMaW1pdGVk\n" +
            "MS0wKwYDVQQDDCRxdnNzbHJjYTFnMy1zc2wtdi5xdW92YWRpc2dsb2JhbC5jb20w\n" +
            "ggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCLgSX0nduUm87/qmfTdofL\n" +
            "5P/Xtrly8Z9GaiLPLu1syNqT/Sri4ngYQGXXwF8h6gnHgEb6gDI2p3Q3gb75NthO\n" +
            "WfWMD6FqafV47pUeNml6JvNbsYAPc8qGxMPtgQ8HhQuU+Trykx3onq/Se5HRYlve\n" +
            "7MMJixiYQKYwwThHh9G1uGYPMQJT2TQfueIAu0MT6Ljc2YB6noXpzTzU63dvmC1Q\n" +
            "8TMmFoJYL276lQ3p3vRKEW1nVmjeVoqvK/3Vpg440KbQL5D7Gj/pQPL4d7ljyS/I\n" +
            "UN3q7QPS7BojsvF90u0YpvYEuBXsxdFnqivj5owSuSENG4nqcZUO8/nY+4b+NbJd\n" +
            "AgMBAAGjggHOMIIByjB6BggrBgEFBQcBAQRuMGwwPgYIKwYBBQUHMAKGMmh0dHA6\n" +
            "Ly90cnVzdC5xdW92YWRpc2dsb2JhbC5jb20vcXZyY2ExZzNzc2xpY2EuY3J0MCoG\n" +
            "CCsGAQUFBzABhh5odHRwOi8vb2NzcC5xdW92YWRpc2dsb2JhbC5jb20wHQYDVR0O\n" +
            "BBYEFIDk6mMLdh49CFbFiUDnjZhWatYzMB8GA1UdIwQYMBaAFCAGDUJLpNnTKHAn\n" +
            "Y7xMaUYhgR08MGkGA1UdIARiMGAwRgYMKwYBBAG+WAABZAEBMDYwNAYIKwYBBQUH\n" +
            "AgEWKGh0dHA6Ly93d3cucXVvdmFkaXNnbG9iYWwuY29tL3JlcG9zaXRvcnkwCAYG\n" +
            "Z4EMAQICMAwGCisGAQQBvlgBhFgwQQYDVR0fBDowODA2oDSgMoYwaHR0cDovL2Ny\n" +
            "bC5xdW92YWRpc2dsb2JhbC5jb20vcXZyY2ExZzNzc2xpY2EuY3JsMA4GA1UdDwEB\n" +
            "/wQEAwIFoDAdBgNVHSUEFjAUBggrBgEFBQcDAgYIKwYBBQUHAwEwLwYDVR0RBCgw\n" +
            "JoIkcXZzc2xyY2ExZzMtc3NsLXYucXVvdmFkaXNnbG9iYWwuY29tMA0GCSqGSIb3\n" +
            "DQEBCwUAA4ICAQB2XiV2msE7M8Qp0YIcihD86T8U91PJH7Pb3F/3+8fyX08/oKDo\n" +
            "s80sE50tiI5lw+tSFQZuvpOFefejEh1uAwu1slZOlvICHOAJNG1EXPa8pEmDU2i5\n" +
            "nd5r7rM757/+cgsPLvwegVuIL4vIYhnoKzPiXpkl8FkNrhRjqeUIAXf2sLjbbbng\n" +
            "oYRCypkSovpijPf7Cid19wKh/ipp8DxCNnGMit55mnx7eFNAWpb9cFljd+WaABCA\n" +
            "IcvcZhZrLKYrbUErdQzzu0sa3IlEC5QBgz+IvT62RHT+vWRiv0LYhkHVLsDQUHpJ\n" +
            "uTa1xi0qvBVGIP1jxIQv5W3hGPLYt7B/8A8v+xOn4m1VWfGIa4V3RGpbBMw19DH+\n" +
            "JvLjg8coDWKhqZ150V31Ve8wczSjT+KZHFRWTb4TZt8GSXa56kJV5xadPW8A3EKV\n" +
            "kulcspO1njb73ImrwTPIOLnDAsMDrAO41FEob87bdZacpg+kHjiAP9BzpgSSX1x5\n" +
            "b/qy2uRtsf3ZlOb1J6fCqb8lRwSU7uGUStUx4tVMpjR5LQfNVroiDEthN5BE6sye\n" +
            "zVRq8vyGvG40jSMBZF1KyW4GW6JlgM1THr1egNFhNkHBs7pSTHJp1Ea+QJjB1uVe\n" +
            "A8kBL0iUlI5PPOqe5KdEXcFy3L+gRh34gyckC4vrLzfNLjKHQvdRHYnQBA==\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=qvsslrca1g3-ssl-r.quovadisglobal.com, O=QuoVadis Limited, L=Hamilton,
    // ST=Pembroke, C=BM
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGJzCCBA+gAwIBAgIUGeTgdhQ6UoMWie3kBh4IGxDH4AQwDQYJKoZIhvcNAQEL\n" +
            "BQAwTDELMAkGA1UEBhMCQk0xGTAXBgNVBAoMEFF1b1ZhZGlzIExpbWl0ZWQxIjAg\n" +
            "BgNVBAMMGVF1b1ZhZGlzIFFWUkNBMUczIFNTTCBJQ0EwHhcNMTcwNTAyMTY1OTQ4\n" +
            "WhcNMjAwNTAyMTcwOTAwWjB9MQswCQYDVQQGEwJCTTERMA8GA1UECAwIUGVtYnJv\n" +
            "a2UxETAPBgNVBAcMCEhhbWlsdG9uMRkwFwYDVQQKDBBRdW9WYWRpcyBMaW1pdGVk\n" +
            "MS0wKwYDVQQDDCRxdnNzbHJjYTFnMy1zc2wtci5xdW92YWRpc2dsb2JhbC5jb20w\n" +
            "ggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDR/0pcsSc4mmqVkzCO5h1m\n" +
            "BlZ0uxmakNTNnWqeOXmMgl2KBni6MzIdxBkPmII5TI3nc+DXrWrtBCJKRtww3mbF\n" +
            "ZoBhrscODv3OjfVqsVfhUPjqLwUEE9X/8IlxFpcsKRH1mC7weLg56kfnHuK2WHPQ\n" +
            "dbnVgzzjk8mSi8HL3szIiojGC0ZwilrV/LCXBqETC3aMe8PtGnMW96TcvqQEdYFa\n" +
            "4MEXuYnUwXB0WoKAJkHw/MMc0RytrICtlpaMQ7ZnloW8LvoQ1wIM7nWwCr+dieh6\n" +
            "lZCWRN/Au+h6qdyDUDUPQFoGpp7AfE2OJmeCY30gp4GdAKtGpTG++gfJrtkc8FnZ\n" +
            "AgMBAAGjggHOMIIByjB6BggrBgEFBQcBAQRuMGwwPgYIKwYBBQUHMAKGMmh0dHA6\n" +
            "Ly90cnVzdC5xdW92YWRpc2dsb2JhbC5jb20vcXZyY2ExZzNzc2xpY2EuY3J0MCoG\n" +
            "CCsGAQUFBzABhh5odHRwOi8vb2NzcC5xdW92YWRpc2dsb2JhbC5jb20wHQYDVR0O\n" +
            "BBYEFGffDkPGAcip01jKnnvEt1jpKNRnMB8GA1UdIwQYMBaAFCAGDUJLpNnTKHAn\n" +
            "Y7xMaUYhgR08MGkGA1UdIARiMGAwRgYMKwYBBAG+WAABZAEBMDYwNAYIKwYBBQUH\n" +
            "AgEWKGh0dHA6Ly93d3cucXVvdmFkaXNnbG9iYWwuY29tL3JlcG9zaXRvcnkwCAYG\n" +
            "Z4EMAQICMAwGCisGAQQBvlgBhFgwQQYDVR0fBDowODA2oDSgMoYwaHR0cDovL2Ny\n" +
            "bC5xdW92YWRpc2dsb2JhbC5jb20vcXZyY2ExZzNzc2xpY2EuY3JsMA4GA1UdDwEB\n" +
            "/wQEAwIFoDAdBgNVHSUEFjAUBggrBgEFBQcDAgYIKwYBBQUHAwEwLwYDVR0RBCgw\n" +
            "JoIkcXZzc2xyY2ExZzMtc3NsLXIucXVvdmFkaXNnbG9iYWwuY29tMA0GCSqGSIb3\n" +
            "DQEBCwUAA4ICAQBI/zlzisJLwBNaVZkQDMh1gYY8uRUad6Jn7yBFQbJ796VVlD1A\n" +
            "yxJD+y9cpwzXvwKau8jIMi96OXo6xtsTDxKY9PzW8DkrlrxqdzLI7s5M30tGu8Sk\n" +
            "WitIWPC3FU0oZqa9jBPkfujllR5FNuYikMOFIi2+/3haEK/6kviLpe5WyK4yJ3a9\n" +
            "7dLq0If4vhNbKsuW1ROnq5CpPy+iIuZy3CWtq8WJSHDyZzhzrW48QHmTkoAU5lAb\n" +
            "3KLMBo/gtUTjABVauADeVZVN6GgLflSIdz1P/aMJQ88q/88w+6KYJlBtg3mWSRHc\n" +
            "Vh+BkIiKmfTG+N9SJ5jv7VKt8PjcKgqCzOHUslLHgUDFhJ5gdYIixD24ikRHYriH\n" +
            "UCO3ltEppIUm/xgins75F6V9YBxHA1Ks/S5MfMnI6N+fFurIwIsas5w6gTPNwbBC\n" +
            "z6G1fu6schk73uvfK4W6PiuMTURsQ1M746K2BlV+FIclTk8jYHe+EyLFgIsgVigo\n" +
            "JJs0DsIp0RoGvw+bxxyA9CHeFFi+MlAVEj2+qJnwrD3ZqNFFw87/HDIWW+Ue8ERs\n" +
            "HfPDZvEQZ1BHGzD/H04F0+HwwfItxvgiQVC2L/yjmh7St311OLiK8RM3Ur0X15bZ\n" +
            "3g4c1gsHx9Gmlk3l8YIOk0yxvLaF03YsNbrfykXHuJM9Phy8Ya3nTpsqtw==\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator)
            throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Tue May 02 10:15:37 PDT 2017", System.out);
    }
}

class RootCA2G3 {

    // Owner: CN=QuoVadis EV SSL ICA G3, O=QuoVadis Limited, C=BM
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGuDCCBKCgAwIBAgIUUk/B8W400XArhKE/sEK7zHw8kDIwDQYJKoZIhvcNAQEL\n" +
            "BQAwSDELMAkGA1UEBhMCQk0xGTAXBgNVBAoTEFF1b1ZhZGlzIExpbWl0ZWQxHjAc\n" +
            "BgNVBAMTFVF1b1ZhZGlzIFJvb3QgQ0EgMiBHMzAeFw0xNjExMzAxNjIxMDFaFw0y\n" +
            "NjExMzAxNjIxMDFaMEkxCzAJBgNVBAYTAkJNMRkwFwYDVQQKDBBRdW9WYWRpcyBM\n" +
            "aW1pdGVkMR8wHQYDVQQDDBZRdW9WYWRpcyBFViBTU0wgSUNBIEczMIICIjANBgkq\n" +
            "hkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAonyczmwRSnw5BhWIrfcD19EbE7bYu5dF\n" +
            "tD8o/5NtQCW+XdoLX+X9uNTuvnPw9Hv2RdhYrJgeLgF2wZ52XMGknRdB8tQYrknA\n" +
            "l/j0N5f8DD82xP2eBkCpIB0UED4zNVwwWcdWvBUgNEdNobz9vQKb7B5LlbXm9kaO\n" +
            "uxYgcv8WsNMivSP3mkJShEOh4RZ3ZdBM/vtJyuvUyEPjyiSzfN94tZHx/H194S4D\n" +
            "VAPgE7ny3ISzN+Aa3kjyLebP/sPzI1AY0DWx8Yg4STG1j0PJeuTb6Ago2kmx4Kqn\n" +
            "4q4kSPL8CgITYHiKaJx6Dt8Q90ieJ7ywG4Mb/YADOIlmoXZ6kXhzGAxyWXFgolLb\n" +
            "4UToIh6U66v3Iyq+gXyCeMXGT4nUgs3+PduzOei9668jeKQaoU5d7LjJUL+ZH2+Y\n" +
            "1bPmMAypHFLZryziOzC5kRo4TamgAf3LHHr2C7yIUuo+avlv3cic3NUodcfMi7Ax\n" +
            "OQFLb32CtDoDeVb5v3x88R0tIEJTizk6M1B/0pGtZiFfXtrNVfHmEYvY2rOLbgWK\n" +
            "M831ykqZSYHUpiqgnaNJb4Qs8WcxqUw1xki64WwiPclUSn5XgGMIwxSDGjUIJHKR\n" +
            "rzgQ9lneHOHVb8pXHNFkdBDHTb1KNmDOyLsg3q0LJP6P3nzT/aWDAj3glpJvGQ5d\n" +
            "kjAbjx+NFk8CAwEAAaOCAZcwggGTMBIGA1UdEwEB/wQIMAYBAf8CAQAwUQYDVR0g\n" +
            "BEowSDBGBgwrBgEEAb5YAAJkAQIwNjA0BggrBgEFBQcCARYoaHR0cDovL3d3dy5x\n" +
            "dW92YWRpc2dsb2JhbC5jb20vcmVwb3NpdG9yeTB0BggrBgEFBQcBAQRoMGYwKgYI\n" +
            "KwYBBQUHMAGGHmh0dHA6Ly9vY3NwLnF1b3ZhZGlzZ2xvYmFsLmNvbTA4BggrBgEF\n" +
            "BQcwAoYsaHR0cDovL3RydXN0LnF1b3ZhZGlzZ2xvYmFsLmNvbS9xdnJjYTJnMy5j\n" +
            "cnQwDgYDVR0PAQH/BAQDAgEGMCcGA1UdJQQgMB4GCCsGAQUFBwMBBggrBgEFBQcD\n" +
            "AgYIKwYBBQUHAwkwHwYDVR0jBBgwFoAU7edvdlq/YOxJW8ald7tyFnGbxD0wOwYD\n" +
            "VR0fBDQwMjAwoC6gLIYqaHR0cDovL2NybC5xdW92YWRpc2dsb2JhbC5jb20vcXZy\n" +
            "Y2EyZzMuY3JsMB0GA1UdDgQWBBTlhFTQkEmfOLryyeEqCMVOn6BIPzANBgkqhkiG\n" +
            "9w0BAQsFAAOCAgEAY/EHWbpNwCgGVQ1B7cIn530n6Rnht8ryN6E4Sis2GG09801s\n" +
            "eCVMoGUB1uBCWm7uqQqydjTbjLhuub7hTjSJ1J30SOK1CZbk+c1VP9DcjY46hycy\n" +
            "tUKQ2WbgkaY+l/tZNDKu0djc2hA5apljQCmiIzckbcHr6yRnFK7ZPjSPCAUKm20D\n" +
            "vORQ7hsIaomsIlqXm5BPssMcxjI48Ezgv/s8ynASI8S5P2vOnBo08sJBM/a0Kbuw\n" +
            "351SubTzjxG+o1SHe6lAzvIQMuSwxUca8YkiB19w5YZt+Ss2JXNc6F2jZwpr0hto\n" +
            "IXe+N9/x0CohYRRa+IivRGgdDQc3w2P+pffNQP/qdPuUYyMkYWiuHH/YvwXyuDxv\n" +
            "yGQfvKmHr1uq/qiqbK1bDSUoEq4Su8yX8YoF9TuxYraIpp9iErO5rarDO6GTNVHh\n" +
            "1OXAJ/ePhOWzqo3flLTlAdTcs3Mq97kKW8XWCnu/cjJJglf2zVfLAlv95p56B9If\n" +
            "0pXbN74qDkYEC8TdLOwryhcv8yyimh90/AvW9LpB7swkWnUUYNTep/XMX/RLpHLn\n" +
            "JOVtnRpn3coVfSR/0rz0XKVXeZGnKztGdIMQhWMTxvZ1UpmRAH2Ab2QnVo1fkPVy\n" +
            "qNSJces5Y/VKpIvLBk5Jj55fvK8ME/9ASa+LtLrIms8iYHl75cupuYZZlg8=\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=qvsslrca2g3-ev-v.quovadisglobal.com, O=QuoVadis Limited, L=Hamilton,
    // ST=Pembroke, C=BM, SERIALNUMBER=28474, OID.2.5.4.15=Private Organization,
    // OID.1.3.6.1.4.1.311.60.2.1.3=BM
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIH4DCCBcigAwIBAgIUUZsNAKy8C5AlCfpCZWUQY2R6VawwDQYJKoZIhvcNAQEL\n" +
            "BQAwSTELMAkGA1UEBhMCQk0xGTAXBgNVBAoMEFF1b1ZhZGlzIExpbWl0ZWQxHzAd\n" +
            "BgNVBAMMFlF1b1ZhZGlzIEVWIFNTTCBJQ0EgRzMwHhcNMTcwNDE4MTg1NjEyWhcN\n" +
            "MTkwNDE4MTkwNjAwWjCBwDETMBEGCysGAQQBgjc8AgEDEwJCTTEdMBsGA1UEDwwU\n" +
            "UHJpdmF0ZSBPcmdhbml6YXRpb24xDjAMBgNVBAUTBTI4NDc0MQswCQYDVQQGEwJC\n" +
            "TTERMA8GA1UECAwIUGVtYnJva2UxETAPBgNVBAcMCEhhbWlsdG9uMRkwFwYDVQQK\n" +
            "DBBRdW9WYWRpcyBMaW1pdGVkMSwwKgYDVQQDDCNxdnNzbHJjYTJnMy1ldi12LnF1\n" +
            "b3ZhZGlzZ2xvYmFsLmNvbTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEB\n" +
            "ALo9QJVNVNVfG//nZiOPX/j2O8GTVlSAfIMliEj78G0xmPZiQD3n/70KcYlsI7No\n" +
            "ilytC8e/m4Mic9PpYfmhAwiUSmb3ba8qjekUgmBFXuQqj3fH6Na+8f5WC9cYpwlc\n" +
            "Ew3NuL2WBs86mjM/3GIa61IXrGpRxN6UQJwSxhqlb1QqEGtuwBiy9FJQd0xekCTC\n" +
            "GBe2zFT1WhyVSMWlxwkcy1p2LeUmlvnV6FHQYcM9te8UPhgY53O6+u0tnnnsED37\n" +
            "UOU3MLev8T++WL7VPOrjgjXydMC9ndXKRttQFIeJ1r+W7rdMLCWkrTzvJ6GtBZZr\n" +
            "8jjHNabWPkBslML7oGwvUHMCAwEAAaOCA0YwggNCMHgGCCsGAQUFBwEBBGwwajA5\n" +
            "BggrBgEFBQcwAoYtaHR0cDovL3RydXN0LnF1b3ZhZGlzZ2xvYmFsLmNvbS9xdmV2\n" +
            "c3NsZzMuY3J0MC0GCCsGAQUFBzABhiFodHRwOi8vZXYub2NzcC5xdW92YWRpc2ds\n" +
            "b2JhbC5jb20wHQYDVR0OBBYEFLVK7rSs4x+DZrwYaWl2mjhhAtV/MAwGA1UdEwEB\n" +
            "/wQCMAAwHwYDVR0jBBgwFoAU5YRU0JBJnzi68snhKgjFTp+gSD8wWgYDVR0gBFMw\n" +
            "UTBGBgwrBgEEAb5YAAJkAQIwNjA0BggrBgEFBQcCARYoaHR0cDovL3d3dy5xdW92\n" +
            "YWRpc2dsb2JhbC5jb20vcmVwb3NpdG9yeTAHBgVngQwBATA8BgNVHR8ENTAzMDGg\n" +
            "L6AthitodHRwOi8vY3JsLnF1b3ZhZGlzZ2xvYmFsLmNvbS9xdmV2c3NsZzMuY3Js\n" +
            "MA4GA1UdDwEB/wQEAwIFoDAdBgNVHSUEFjAUBggrBgEFBQcDAgYIKwYBBQUHAwEw\n" +
            "LgYDVR0RBCcwJYIjcXZzc2xyY2EyZzMtZXYtdi5xdW92YWRpc2dsb2JhbC5jb20w\n" +
            "ggF9BgorBgEEAdZ5AgQCBIIBbQSCAWkBZwB2ALvZ37wfinG1k5Qjl6qSe0c4V5UK\n" +
            "q1LoGpCWZDaOHtGFAAABW4J1OtsAAAQDAEcwRQIhANABKS1i5uxEm/HMivDJFyNJ\n" +
            "gOKRrApqmx3KV0/pWMzqAiAui21HV9lVJ1OT6dEA9mlZAH4NmzklmY9WI978GMYG\n" +
            "mgB2AFYUBpov18Ls0/XhvUSyPsdGdrm8mRFcwO+UmFXWidDdAAABW4J1Os0AAAQD\n" +
            "AEcwRQIgTWLHrhex17UyIlr0HC9LXNUv0kyOudo7MpxoWFy1xGICIQCHFSoQGwvv\n" +
            "zzpQ3JmHSLHy0AQQfWlbV9rFv37F4A7AaAB1AKS5CZC0GFgUh7sTosxncAo8NZgE\n" +
            "+RvfuON3zQ7IDdwQAAABW4J1OvYAAAQDAEYwRAIgWLm8u/bcMZt5oXAPIqP9/Qqj\n" +
            "Q61VYX+II6RFK+EJCnwCIBrXxQgngqO7X/aaeWnEjfQeSu7WCK9Md3tcqXsn+gMd\n" +
            "MA0GCSqGSIb3DQEBCwUAA4ICAQAu0Y29voXdwt/68hwbdj8L50yecl2Z0OkOA31v\n" +
            "UhAHgRVhQ+WiAgoeGEgjdntQ5pL7Wtr314gHpG6iR849Zr56WOliA6pfBYDk3qkH\n" +
            "UiRgqQBUEV8oRzgp0E+Ebev+p9leM4RPYmUNsP3K4Z/BY24HNOtNKMC3clqKO35K\n" +
            "D7B9ObYUb0+IjreKgUyQB7wMgFi7393gXDraVDhDoLrcktAkvkv3Mbt+A3IO5VrO\n" +
            "4mQwjrLHzj8nFCmsP4RbCIKFO2QZE8sJYwplKUWOk1ngjpOvObPYpMt5M1kiRvau\n" +
            "agkQ+WvnvuMEgAgafHtI4uu0ZNDW1ib0H+xq5X/x2w1RjEElbXCKMbnf3Pdvh9FG\n" +
            "mLpkVITXIKzT0Jm+oIs+Vk4ktNEe8hQIzcqimmtlVl2hgMWkmIfRio1+41EY4Din\n" +
            "QXBVsbRqftamzSpLbW54ryGJB8dSiGe4P53DOcNKiyie7une95ouZY/1DfQIlVG/\n" +
            "9XexhqdGMKp6qUjgd9hOfHrD+mZHeBdIIONOHOhy6ESIUgpSzaAAM7QXZFqlzLzY\n" +
            "okRp6cJKDfUmXrk80MopQMhRHJwdxfeZ/A/xAkrWlVPshG+qltSGIZWrNjhQIwk3\n" +
            "49zFQCuDS+FrkubRueV+MB8Abp+V1nv5PNbhwfPzGSqwn9XX3vUnsp9uLv+3WlrL\n" +
            "Kl1DeA==\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=qvsslrca2g3-ev-r.quovadisglobal.com, O=QuoVadis Limited, L=Hamilton,
    // ST=Pembroke, C=BM, SERIALNUMBER=28474, OID.2.5.4.15=Private Organization,
    // OID.1.3.6.1.4.1.311.60.2.1.3=BM
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIH4TCCBcmgAwIBAgIUZTuy16qm4LnioIRmiaQZuThb38gwDQYJKoZIhvcNAQEL\n" +
            "BQAwSTELMAkGA1UEBhMCQk0xGTAXBgNVBAoMEFF1b1ZhZGlzIExpbWl0ZWQxHzAd\n" +
            "BgNVBAMMFlF1b1ZhZGlzIEVWIFNTTCBJQ0EgRzMwHhcNMTcwNDE4MTg1NjQ0WhcN\n" +
            "MTkwNDE4MTkwNjAwWjCBwDETMBEGCysGAQQBgjc8AgEDEwJCTTEdMBsGA1UEDwwU\n" +
            "UHJpdmF0ZSBPcmdhbml6YXRpb24xDjAMBgNVBAUTBTI4NDc0MQswCQYDVQQGEwJC\n" +
            "TTERMA8GA1UECAwIUGVtYnJva2UxETAPBgNVBAcMCEhhbWlsdG9uMRkwFwYDVQQK\n" +
            "DBBRdW9WYWRpcyBMaW1pdGVkMSwwKgYDVQQDDCNxdnNzbHJjYTJnMy1ldi1yLnF1\n" +
            "b3ZhZGlzZ2xvYmFsLmNvbTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEB\n" +
            "ALXMNTuogjC2wpziEXbKztdgzBflORLxoAo5Y8HNAZVo8MgJJucshZ5S6cmRjreY\n" +
            "fOlwo85Vu9s39EMRR+I0AZLbxw2PZxNSHUxTCzWlmJ4yValRPRZjz2LXJ+mjpkc3\n" +
            "hsVHtCawvPqh2uNwM2qUD6zKfRGdKC27NeICjYe7RtfhLRdrZ8MNtVWMrrFt3Dzd\n" +
            "SRJXFcLkN4rPzRFCxldU2yH6V4cZwnVz4XxV/nbljVtGyMJWGVzU0Bhy1fedAJ9x\n" +
            "KGJbM6wackOlpUm0XMQdFxHF2tW4Sus6Mcf+2N8FgXk4PnwXarIc/Sj5Tx+Bvf5y\n" +
            "TwBOGS02Hzs07ILV3w4dp00CAwEAAaOCA0cwggNDMHgGCCsGAQUFBwEBBGwwajA5\n" +
            "BggrBgEFBQcwAoYtaHR0cDovL3RydXN0LnF1b3ZhZGlzZ2xvYmFsLmNvbS9xdmV2\n" +
            "c3NsZzMuY3J0MC0GCCsGAQUFBzABhiFodHRwOi8vZXYub2NzcC5xdW92YWRpc2ds\n" +
            "b2JhbC5jb20wHQYDVR0OBBYEFALFAuUwkAiTXc+DIW861Mu1o/7RMAwGA1UdEwEB\n" +
            "/wQCMAAwHwYDVR0jBBgwFoAU5YRU0JBJnzi68snhKgjFTp+gSD8wWgYDVR0gBFMw\n" +
            "UTBGBgwrBgEEAb5YAAJkAQIwNjA0BggrBgEFBQcCARYoaHR0cDovL3d3dy5xdW92\n" +
            "YWRpc2dsb2JhbC5jb20vcmVwb3NpdG9yeTAHBgVngQwBATA8BgNVHR8ENTAzMDGg\n" +
            "L6AthitodHRwOi8vY3JsLnF1b3ZhZGlzZ2xvYmFsLmNvbS9xdmV2c3NsZzMuY3Js\n" +
            "MA4GA1UdDwEB/wQEAwIFoDAdBgNVHSUEFjAUBggrBgEFBQcDAgYIKwYBBQUHAwEw\n" +
            "LgYDVR0RBCcwJYIjcXZzc2xyY2EyZzMtZXYtci5xdW92YWRpc2dsb2JhbC5jb20w\n" +
            "ggF+BgorBgEEAdZ5AgQCBIIBbgSCAWoBaAB2AFYUBpov18Ls0/XhvUSyPsdGdrm8\n" +
            "mRFcwO+UmFXWidDdAAABW4J1uUEAAAQDAEcwRQIhAK2LD7cJrN7YYjyBqFDoZva+\n" +
            "fae1CiuYyxpREVes1c8OAiBLVt/dGKnvwY2CW2TN3/WyRM7al2sLnM+XwNUGZDrJ\n" +
            "pgB2ALvZ37wfinG1k5Qjl6qSe0c4V5UKq1LoGpCWZDaOHtGFAAABW4J1uVQAAAQD\n" +
            "AEcwRQIhAIA9IjxIT69JGX+sl1okMiGsXfCOPq5crSX+m04Q7LcgAiBJWUsLDtm9\n" +
            "5TKsGZvlJRKOn1CcA94sApQ4v+1D+uz+JQB2AKS5CZC0GFgUh7sTosxncAo8NZgE\n" +
            "+RvfuON3zQ7IDdwQAAABW4J1uWwAAAQDAEcwRQIhAIWbEqGnZSIwrI5eWCIzfMRY\n" +
            "A+onO3IjQrVAE6ZuGu2bAiAlyoRSfH4s8+lVL225AYD45OkJJfG41T6k+wVLM5Hg\n" +
            "ezANBgkqhkiG9w0BAQsFAAOCAgEAPwvRI5GmzR72cDoh+7VPj7PihQDG4HBYq5Ta\n" +
            "bF7NK2v9DoaU99vv01K3WBNIydjQX4j+IK8MoGp9dXV+LDUTsebnsY+nr3O4R0pK\n" +
            "I2TAazN7zcy3SYc/MtaW7JI+/ckjHaJw+AT+qUz+l20p9shBFlg4QvH2cx2OOCat\n" +
            "/CRnG2Nqc5nN1xVcS3aVDrGl36XIcjV+ab+3zicm3OhWqn/hlfBBWimuhix68i/L\n" +
            "k+qUyN6A8Bz7NwsouzG7keS17VZbLFkOuczq9KxJLHtlI1OYFNzrLEx6aXeM5VoH\n" +
            "mlwETxagSL6fjRvcCaM6As9WVRS08p/RldUrEw+O6r3ob7FaOywwIzSMFV1GbVFG\n" +
            "eIrSMuSVwbQRa5Duakoe5vz1vOddrZPm3kqpvyT7j51nuedrjc8YgisuyMbxkf5s\n" +
            "8tesqxdltXjFNwpwveYlgHAi3sZvO2dm6bEZcioxLEWEpwmYXrkBJWLhcILdfY99\n" +
            "SWFAmwGtmCqh8Sxla77o+gaZkNKf3zBn/34Q91Z96qKgqjXDAGefsZiy4tQeEUJc\n" +
            "2qIqjb2rWi5Vo7hn2eolNXzp6ZdanicpecpqwpmW9/v6YRxKLGTsdVz82TGWPnpt\n" +
            "q3rCll0NIAfcjekFmRzmBWF1jOn4fCcF/WOxKW1T4JcMIcNoa5iI9M1WcVKQvJKA\n" +
            "Zd5LLu4=\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator)
            throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Tue Apr 18 12:23:14 PDT 2017", System.out);
    }
}

class RootCA3G3 {

    // Owner: CN=QuoVadis QVRCA3G3 SSL ICA, O=QuoVadis Limited, C=BM
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGszCCBJugAwIBAgIURUME8OY/YBHyokbgxoTKpPcoiHYwDQYJKoZIhvcNAQEL\n" +
            "BQAwSDELMAkGA1UEBhMCQk0xGTAXBgNVBAoTEFF1b1ZhZGlzIExpbWl0ZWQxHjAc\n" +
            "BgNVBAMTFVF1b1ZhZGlzIFJvb3QgQ0EgMyBHMzAeFw0xNzA0MTkxNDQ4NDBaFw0y\n" +
            "NzA0MTkxNDQ4NDBaMEwxCzAJBgNVBAYTAkJNMRkwFwYDVQQKDBBRdW9WYWRpcyBM\n" +
            "aW1pdGVkMSIwIAYDVQQDDBlRdW9WYWRpcyBRVlJDQTNHMyBTU0wgSUNBMIICIjAN\n" +
            "BgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAt8UIgFvcneWgv29aR2/UV810uW9N\n" +
            "VpvdEgQDPHao5+i3IwCH1GrV8KeC25vfJAuW2TJ5gHeN5fmWAtWU8NDaNwGxJq/w\n" +
            "jlOe/UW0KSosuuOBltLY9fl+7lDYqBjEwmCGvZMQOzpsbm8QUYTuZmtw96sT5beZ\n" +
            "Kwqub/NBDE59IZ+b82obreNFFOgwcHv9E00bfRW7kizNfaC8AiwgV9WfIFgvtb4+\n" +
            "YflcgGbdWnmNvwL8aZGWpGYjw/H/0kpwfMgrVF3Q7h8Y0yTg/jj8ZdXLdaE/PQzx\n" +
            "8RUU4xJGxply/RrNUEvm9xeXZG3ssLW56WDEhRLkORX/zF4/mVyO2DpGJs06IUSP\n" +
            "VWe+JuJGT1UxWqIsDIIHqJNa2BYl6O/XOjE2oGxiCb9w0/kQ8tKWWynFx4XOtrjA\n" +
            "pGktsjw66tqE08XWOuoKwAXH2Llwz+VGSMzrCDH98VHtAu/XpEjuW3iP+I7EHksm\n" +
            "W2eLdQdvTJ5DBdLsspIG4HC9Ke+c/gCEJHvOURPXoY7j9JPcQLc+5O7kBqiIjEBU\n" +
            "NpPX37x7z3msac/IiG/SOYl+kiBESV44QFIOl8sHYmj9HGNlkQz4B/inuGwifIux\n" +
            "rfdvm6nrpC7jhd/5Ptk4PO1kcAtgwcB99BnRCw47Xl7hrERTpoRISReNG0JMK7Op\n" +
            "wVFqyi7bV1U/l4MCAwEAAaOCAY8wggGLMBIGA1UdEwEB/wQIMAYBAf8CAQAwSQYD\n" +
            "VR0gBEIwQDA+BgRVHSAAMDYwNAYIKwYBBQUHAgEWKGh0dHA6Ly93d3cucXVvdmFk\n" +
            "aXNnbG9iYWwuY29tL3JlcG9zaXRvcnkwdAYIKwYBBQUHAQEEaDBmMCoGCCsGAQUF\n" +
            "BzABhh5odHRwOi8vb2NzcC5xdW92YWRpc2dsb2JhbC5jb20wOAYIKwYBBQUHMAKG\n" +
            "LGh0dHA6Ly90cnVzdC5xdW92YWRpc2dsb2JhbC5jb20vcXZyY2EzZzMuY3J0MA4G\n" +
            "A1UdDwEB/wQEAwIBBjAnBgNVHSUEIDAeBggrBgEFBQcDAQYIKwYBBQUHAwIGCCsG\n" +
            "AQUFBwMJMB8GA1UdIwQYMBaAFMYX0Lyo6gJD8hsGmV0rkCC515zkMDsGA1UdHwQ0\n" +
            "MDIwMKAuoCyGKmh0dHA6Ly9jcmwucXVvdmFkaXNnbG9iYWwuY29tL3F2cmNhM2cz\n" +
            "LmNybDAdBgNVHQ4EFgQUTknx5HQLmDQSOuWxVX3EknK1r6QwDQYJKoZIhvcNAQEL\n" +
            "BQADggIBAHfmIJkd+URmnVm0X1/43QXu08RTzUr1zjf4ZBVtzUFoEkfZm+zKlhb7\n" +
            "QeYJ5lprX1tdRfHLI+JC7oyI5+7/0q1j2FN2g0oKYN63dIgtppoCNpBu58f69YxL\n" +
            "Y3GPSCfgs+ld+HegNSTjQVzelr16aFo9sj1fzUwY4Xj+xEYDjYxFmNGSXY37+DdN\n" +
            "3WPm1iahBNNCZGfXq5T4qr6+R6RWwxsaBdQfZh3efGB1WG4DVSQBoiCKjS7Eg+Mf\n" +
            "LT+KEZgawLUVrt/sT5lNfw23XA1gxIOcNRBHjsTWbtTBHJeb8hYvXB38UGK4GfIo\n" +
            "NxtvRyXgG/U8+OuCQPS2SpJ1VH+yZ4Tn3G4k2+WillxfpqCVgHDVuT8wigw1xUNb\n" +
            "0Ft9F3OWftWCVILaYEcyuJrnL3jjcZXc/zG01wIGDFvlPshRifVs/69Xq9UQmMfB\n" +
            "GUh6MteDIsN9NdiArcumSC1dNoA/9eESp1pb186lDx9KxRQ/3NJRDMOIsMYN8Lyu\n" +
            "cDNzsnymtQyIm3YG7VmZi/6k99n9vT8Ff9PvQ49cdfPl8GIONMdYmhTtLtuC00AU\n" +
            "550HVLnpFW8d1NX3+XKxQ5FG04nsTxUD2FtT+trEQouktPq9iFqZN+PLPi8UdrBW\n" +
            "AGUDCnO/TNo7IPW6arQrFpYbRLStiOJw7204Mjuqco/1KcqnEiIC\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=qvsslrca3g3-ssl-v.quovadisglobal.com, O=QuoVadis Limited, L=Hamilton,
    // ST=Pembroke, C=BM
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGJzCCBA+gAwIBAgIUatc95M2rfpt/opXnck37WXW2RpAwDQYJKoZIhvcNAQEL\n" +
            "BQAwTDELMAkGA1UEBhMCQk0xGTAXBgNVBAoMEFF1b1ZhZGlzIExpbWl0ZWQxIjAg\n" +
            "BgNVBAMMGVF1b1ZhZGlzIFFWUkNBM0czIFNTTCBJQ0EwHhcNMTcwNTAyMTY1OTAy\n" +
            "WhcNMjAwNTAyMTcwOTAwWjB9MQswCQYDVQQGEwJCTTERMA8GA1UECAwIUGVtYnJv\n" +
            "a2UxETAPBgNVBAcMCEhhbWlsdG9uMRkwFwYDVQQKDBBRdW9WYWRpcyBMaW1pdGVk\n" +
            "MS0wKwYDVQQDDCRxdnNzbHJjYTNnMy1zc2wtdi5xdW92YWRpc2dsb2JhbC5jb20w\n" +
            "ggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQC+S725uLLelMIYHWuh6fbT\n" +
            "lGdi7wf1BlsfQY/ZnLvsFbT1KHodE407RXP0NB6AeEBOlO8xQxaZ5b38aF+HROJw\n" +
            "TcvUAgQHmNE+ER0JCMi42jSFC2dc93PhdcUEeesxIfu1iIKXxFmlbJtJxG3l27yJ\n" +
            "L4ufum9iQYeZeoGXAr54x6JMY29kl5t9QM018d9sA9bHY+0iVJevM3xgxVe7xApw\n" +
            "MSKoZH/OmkX8FaEW9b7TqrWfWcAdD8fkXK8lHCDqmUzSiDGJP16YeQA/4dmFO2vr\n" +
            "ItXY8rTPjXoaolebHxf5WG5Qosxv0mPyklUb+SVSJagv66zl/H2Uk0bLyFFmuNAd\n" +
            "AgMBAAGjggHOMIIByjB6BggrBgEFBQcBAQRuMGwwPgYIKwYBBQUHMAKGMmh0dHA6\n" +
            "Ly90cnVzdC5xdW92YWRpc2dsb2JhbC5jb20vcXZyY2EzZzNzc2xpY2EuY3J0MCoG\n" +
            "CCsGAQUFBzABhh5odHRwOi8vb2NzcC5xdW92YWRpc2dsb2JhbC5jb20wHQYDVR0O\n" +
            "BBYEFFhZXE0P1SMuntLc7JYoHTcD8JKfMB8GA1UdIwQYMBaAFE5J8eR0C5g0Ejrl\n" +
            "sVV9xJJyta+kMGkGA1UdIARiMGAwRgYMKwYBBAG+WAADZAEBMDYwNAYIKwYBBQUH\n" +
            "AgEWKGh0dHA6Ly93d3cucXVvdmFkaXNnbG9iYWwuY29tL3JlcG9zaXRvcnkwCAYG\n" +
            "Z4EMAQICMAwGCisGAQQBvlgBhFgwQQYDVR0fBDowODA2oDSgMoYwaHR0cDovL2Ny\n" +
            "bC5xdW92YWRpc2dsb2JhbC5jb20vcXZyY2EzZzNzc2xpY2EuY3JsMA4GA1UdDwEB\n" +
            "/wQEAwIFoDAdBgNVHSUEFjAUBggrBgEFBQcDAgYIKwYBBQUHAwEwLwYDVR0RBCgw\n" +
            "JoIkcXZzc2xyY2EzZzMtc3NsLXYucXVvdmFkaXNnbG9iYWwuY29tMA0GCSqGSIb3\n" +
            "DQEBCwUAA4ICAQB6QV56jPZzFbFNnKq4xRglTkZSLDMnyrmquWJr4xUWkWIqhQqC\n" +
            "s+wAchy39Uuu+Nv99N1AxJhorpdbyIOd7B2NAnUXPeOa1Rm34mh2a/df0gTVrrWJ\n" +
            "YSUd3Tv7tcGrMXa7kNaP0N3lTITC0F0fu0rLyCH5I28t4zkCXadcWTqHUKIDNS1h\n" +
            "fwx1Y6Dq4fBhKQGpqBq4ThEpBgJdj5aGCNiYfKO/MTDrLxD1BpIjV88O+54cdtYp\n" +
            "3K+UDN2lP03PNH4Z/0jF4K43DHpwDM0r6qP4yLqFf3K1NlzGkYgNlMrKUPSlu+M8\n" +
            "F6R45TWkcHndk3SUxbtGsxhiLG4xJKY7Zm/0vSxNqia+UJ5wL5s+IoiXhj22RrPe\n" +
            "kcx7u3MxB+KWSrtQd8y624J6tqbE7R+aaAX95KTQZoawjypX99P8Kir/NynFHYri\n" +
            "RAX9qFU8nYQEAe47oxl0bIr7URiQrlz+FJ/bzJZQwROWY723JPXgv7wUMifCYvJz\n" +
            "4pLkuc4KE+LIEqk5LUuoYGEhKhKVu8YnmDifPPrBBADNvAnnGfDZF9FRvIcD6h8H\n" +
            "icZBXJHOgu70Rh8Zc77x+v29tKlAJVtswLlV0mVClDUk7U36XL+mAvYntnG9kH5x\n" +
            "qQ2Fl7AkUewOd4tLeiN4fl+S+ceW9sOZPSWx5aLui9p2mmxuyxhC5egCzg==\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=qvsslrca3g3-ssl-r.quovadisglobal.com, O=QuoVadis Limited, L=Hamilton,
    // ST=Pembroke, C=BM
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGJzCCBA+gAwIBAgIUTgJvLquqZ+Padg/W5Y0bTu9jimswDQYJKoZIhvcNAQEL\n" +
            "BQAwTDELMAkGA1UEBhMCQk0xGTAXBgNVBAoMEFF1b1ZhZGlzIExpbWl0ZWQxIjAg\n" +
            "BgNVBAMMGVF1b1ZhZGlzIFFWUkNBM0czIFNTTCBJQ0EwHhcNMTcwNTAyMTY1ODQy\n" +
            "WhcNMjAwNTAyMTcwODAwWjB9MQswCQYDVQQGEwJCTTERMA8GA1UECAwIUGVtYnJv\n" +
            "a2UxETAPBgNVBAcMCEhhbWlsdG9uMRkwFwYDVQQKDBBRdW9WYWRpcyBMaW1pdGVk\n" +
            "MS0wKwYDVQQDDCRxdnNzbHJjYTNnMy1zc2wtci5xdW92YWRpc2dsb2JhbC5jb20w\n" +
            "ggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCOdbnnY8GsO002xJ6Snu2W\n" +
            "snpPmW9ZJ4cEKzdBA4fYKP2V/8ibbOZVH5gI4tSSW+mcMrepS9Jw49sZaKOOGf/7\n" +
            "YsjFOp4DQ0+w/7FOj4WrKWBhymDGKI8SsDqoCkxjCYkAc7cutm5Ge67Yto2mvkzW\n" +
            "vThV7o9pJ4z2kMg+Q527908zvP1eqT2g+72X1L3o3RSdGM5V35R+lGiBDum8ojZm\n" +
            "+QGCGuc6zROgumfYrh11iTNhXJw6KVAS9KJ5GSHzmua/Cu1dwC2SPxp/hRRHlvPp\n" +
            "07EjY2oGhfe6Hvsu9YuoQCm95H4HPTmTDUCKURRIGcC8jdrjXBowEuH15vUocSIJ\n" +
            "AgMBAAGjggHOMIIByjB6BggrBgEFBQcBAQRuMGwwPgYIKwYBBQUHMAKGMmh0dHA6\n" +
            "Ly90cnVzdC5xdW92YWRpc2dsb2JhbC5jb20vcXZyY2EzZzNzc2xpY2EuY3J0MCoG\n" +
            "CCsGAQUFBzABhh5odHRwOi8vb2NzcC5xdW92YWRpc2dsb2JhbC5jb20wHQYDVR0O\n" +
            "BBYEFLzYzgqJRXrnLc5OYHF/koTdbIzeMB8GA1UdIwQYMBaAFE5J8eR0C5g0Ejrl\n" +
            "sVV9xJJyta+kMGkGA1UdIARiMGAwRgYMKwYBBAG+WAADZAEBMDYwNAYIKwYBBQUH\n" +
            "AgEWKGh0dHA6Ly93d3cucXVvdmFkaXNnbG9iYWwuY29tL3JlcG9zaXRvcnkwCAYG\n" +
            "Z4EMAQICMAwGCisGAQQBvlgBhFgwQQYDVR0fBDowODA2oDSgMoYwaHR0cDovL2Ny\n" +
            "bC5xdW92YWRpc2dsb2JhbC5jb20vcXZyY2EzZzNzc2xpY2EuY3JsMA4GA1UdDwEB\n" +
            "/wQEAwIFoDAdBgNVHSUEFjAUBggrBgEFBQcDAgYIKwYBBQUHAwEwLwYDVR0RBCgw\n" +
            "JoIkcXZzc2xyY2EzZzMtc3NsLXIucXVvdmFkaXNnbG9iYWwuY29tMA0GCSqGSIb3\n" +
            "DQEBCwUAA4ICAQAge+6VZgaEFxN38q0MYKs/QbdGowLd5n2CfQfpdOTRnpOtKQw6\n" +
            "Bc/o1O8O/y0XUl1Be7TCgfXKWgw+rKX+ZrI6wCm7MxYlWXV2guWU/AeEl2uv14s/\n" +
            "KnKhzZHfb0eQyItfk23flubc7pbh99LaVqozsLCTL78lOB7N7ZQwsNCrEghHWMxl\n" +
            "w1/IX/h9XOJoBzu4ulebJoQ3hdIYJY7+lkw64uH1FNrCu7P/jjU9ZlPaobZOUy64\n" +
            "sYIt4GsZbMFaUiamNzBUvULw+ZkZq0hTK0cuyA85MXd+3rm5z2AMemC/29XTUYRU\n" +
            "L9LkxMF71w8BJzgpVx3s0a6dfi6XtgacP407IhMc3EW1McsSWdT6jL0zidbjXisU\n" +
            "vfvuzA50b3HwYz8PsRN0Zfi2R1BubaZQ9fQW2fe1EWgq80CqOdO7eNZeaBxbW/qB\n" +
            "smGA1wiHIVEtyHbwZslcKNy8VPKurfKClwZQxf17/oK6QrhOgxiKJGYBUDTa7Ln7\n" +
            "Qslp/y3G721NOXzborchs8XB+BYEETtWWkKoWFDiV7vkfyn3x2cYNiv5JCWDszhZ\n" +
            "RyVrW26YOQ3MqBAiYqgbU2jMdqeRRfMIFqUvvXwoTvYXuN4Yc2ZAOmCBPpUxo66V\n" +
            "zHDu+QK/2/pI1SMLvU3KG526gUtDd67t8JUHqxyo3NsXUCD8tUYpaJy/vg==\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator)
            throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Tue May 02 10:15:53 PDT 2017", System.out);
    }
}
