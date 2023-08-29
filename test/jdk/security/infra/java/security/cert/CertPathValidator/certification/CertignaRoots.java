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
 * @bug 8245654 8314960
 * @summary Interoperability tests with Certigna Root CAs from Dhimyotis
 * @build ValidatePathWithParams
 * @run main/othervm -Djava.security.debug=certpath CertignaRoots OCSP
 * @run main/othervm -Djava.security.debug=certpath CertignaRoots CRL
 */

/*
 * Obtain TLS test artifacts for Certigna Root CAs from:
 *
 * Valid TLS Certificates:
 * https://valid.servicesca.dhimyotis.com/
 *
 * Revoked TLS Certificates:
 * https://revoked.servicesca.dhimyotis.com/
 */
public class CertignaRoots {

    // Owner: CN=Certigna Services CA, OID.2.5.4.97=NTRFR-48146308100036,
    // OU=0002 48146308100036, O=DHIMYOTIS, C=FR
    // Issuer: CN=Certigna Root CA, OU=0002 48146308100036, O=Dhimyotis, C=FR
    // Serial number: fd30cf04344fc38dd90c4e70753d0623
    // Valid from: Wed Nov 25 03:37:21 PST 2015 until: Fri Jun 03 04:37:21 PDT 2033
    private static final String INT_CERTIGNA_ROOT_CA = "-----BEGIN CERTIFICATE-----\n" +
            "MIIHETCCBPmgAwIBAgIRAP0wzwQ0T8ON2QxOcHU9BiMwDQYJKoZIhvcNAQELBQAw\n" +
            "WjELMAkGA1UEBhMCRlIxEjAQBgNVBAoMCURoaW15b3RpczEcMBoGA1UECwwTMDAw\n" +
            "MiA0ODE0NjMwODEwMDAzNjEZMBcGA1UEAwwQQ2VydGlnbmEgUm9vdCBDQTAeFw0x\n" +
            "NTExMjUxMTM3MjFaFw0zMzA2MDMxMTM3MjFaMH0xCzAJBgNVBAYTAkZSMRIwEAYD\n" +
            "VQQKDAlESElNWU9USVMxHDAaBgNVBAsMEzAwMDIgNDgxNDYzMDgxMDAwMzYxHTAb\n" +
            "BgNVBGEMFE5UUkZSLTQ4MTQ2MzA4MTAwMDM2MR0wGwYDVQQDDBRDZXJ0aWduYSBT\n" +
            "ZXJ2aWNlcyBDQTCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIBALPM+7Lp\n" +
            "WBz9wFcPaTc3xnB+5g0XrnptB0EPPfrR04vO52Ykm4ky1d4ZLd10tbM1fa1RqNSO\n" +
            "VWWg93O4pL7zCFKlz6JV74ZZVhHpEAwzBwv2oPnxvVbxtSN67xsSY66ahUYxjzs8\n" +
            "+3FhmsiRxqwnTYvK2u70uglUvRisOKyTL/M6JnrC4y8tlmoz7OSa5BmBMVplJFQt\n" +
            "vmON6N9aHLvYMz+EyJPCbXL6pELxeHjFT5QmIaRamsr2DOTaCjtBZKI1Wnh3X7ln\n" +
            "bjM8MESJiV2t7E9tIQNG0Z/HI3tO4aaUMum3KysY5sC8v3vi7rryGidgzHQhrtP0\n" +
            "ZXWW5UH/k7umLS/P/XXWnCFpc2Lxa1uDGfc2im7xibRoPP+JNZszN76euFlls6jy\n" +
            "EXAiwnVr14tVVTewLK0OWs5SJHpEKp8PGMZRDj59EmMvokWwzL6QzNZ6vVAp00oO\n" +
            "m05sbspNY9+MFqGKKUsKvhFGEa4XmRNxDe6KswLcjPZB+NKHZ0QWFd4ip5C5XmEK\n" +
            "/8qIPjwVr9dah9+oiHGGO8Wx7gJAMF5DTmkvW7GhqCKj1LmHnabjzc8av6kxWVQZ\n" +
            "i/C7HCm9i/W4wio+JA2EAFLqNL3GPNbK9kau4yPhQt/c7zxzo0OHnlsV4THCG7oO\n" +
            "Cd3cfCiyfQcb3FBt6OSpaKRZxjCLBwP00r0fAgMBAAGjggGtMIIBqTASBgNVHRMB\n" +
            "Af8ECDAGAQH/AgEAMA4GA1UdDwEB/wQEAwIBBjAdBgNVHQ4EFgQUrOyGj0s3HLh/\n" +
            "FxsZ0K7oTuM0XBIwHwYDVR0jBBgwFoAUGIdW4G537iQ1PE5zmh/W4eJ5fiswSQYD\n" +
            "VR0gBEIwQDA+BgoqgXoBgTECAAEBMDAwLgYIKwYBBQUHAgEWImh0dHBzOi8vd3d3\n" +
            "LmNlcnRpZ25hLmZyL2F1dG9yaXRlcy8wgYgGCCsGAQUFBwEBBHwwejA6BggrBgEF\n" +
            "BQcwAoYuaHR0cDovL2F1dG9yaXRlLmNlcnRpZ25hLmZyL2NlcnRpZ25hcm9vdGNh\n" +
            "LmRlcjA8BggrBgEFBQcwAoYwaHR0cDovL2F1dG9yaXRlLmRoaW15b3Rpcy5jb20v\n" +
            "Y2VydGlnbmFyb290Y2EuZGVyMG0GA1UdHwRmMGQwL6AtoCuGKWh0dHA6Ly9jcmwu\n" +
            "Y2VydGlnbmEuZnIvY2VydGlnbmFyb290Y2EuY3JsMDGgL6AthitodHRwOi8vY3Js\n" +
            "LmRoaW15b3Rpcy5jb20vY2VydGlnbmFyb290Y2EuY3JsMA0GCSqGSIb3DQEBCwUA\n" +
            "A4ICAQCI5QbprXJ93L+JWHYpUTinXAMSvXMx2dmNm4mIiJRAbGnBOoEYx7M61fbL\n" +
            "L5EJIYZhw8jLmeYVFuMao5OJLwda+RMmVzE7lyTGsY64IDKdwogByNCqbKzrlhnU\n" +
            "8myyMNB0BDs2jgwQe2Dj9v+MddeHr7sDqvs7R1tSS5hoASLtdQhO7oxUzr3m7M8q\n" +
            "+lh4jszli+cjfiPUVS2ADFu4ccQIh4OsIX6SWdU+8R+c/fn0FV6ip4SAVbNyCToz\n" +
            "0ZbZKO8YTJgORxRmvrop9dPyuLWjaRrZ0LMx4a3EM3sQDPDqmsG0lHtfFj2PiJvq\n" +
            "4lEYA+gDiLKODI+3DJMqo559m3QSS52DsShomHX/Txd0lJoZwepCE6X4KkG9FHjV\n" +
            "WXyLgYFwCOcn+hkLhdpblms0wtjeSPITGOioSkefzhleJnDgJ9X4M3svd0HLTpJi\n" +
            "lC1DmDZgdrXWITVdOoCogr2LFKNiGd0tbpKG533eKpfBALlm+afc6j73p1KhJEAn\n" +
            "AfydDZqBRqv6+HHYplNDn/K2I1CZdkwaGrx3HOR/voGUi1sUI+hYbsPAFu8ZxrhD\n" +
            "9UiysmLCfEUhqkbojony+L2mKsoLqyd24emQzn7GgMa7emlWX2jQUTwrD4SliZ2u\n" +
            "OetVaZX5RLyqJWs4Igo/xye0xtMQN8INJ4hSZvnMQ1qFtuSRcQ==\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=Certigna Services CA, OID.2.5.4.97=NTRFR-48146308100036,
    // OU=0002 48146308100036, O=DHIMYOTIS, C=FR
    // Issuer: CN=Certigna, O=Dhimyotis, C=FR
    // Serial number: 6f82fa28acd6f784bb5b120ba87367ad
    // Valid from: Wed Nov 25 03:33:52 PST 2015 until: Sat Nov 22 03:33:52 PST 2025
    private static final String INT_CERTIGNA = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGFjCCBP6gAwIBAgIQb4L6KKzW94S7WxILqHNnrTANBgkqhkiG9w0BAQsFADA0\n" +
            "MQswCQYDVQQGEwJGUjESMBAGA1UECgwJRGhpbXlvdGlzMREwDwYDVQQDDAhDZXJ0\n" +
            "aWduYTAeFw0xNTExMjUxMTMzNTJaFw0yNTExMjIxMTMzNTJaMH0xCzAJBgNVBAYT\n" +
            "AkZSMRIwEAYDVQQKDAlESElNWU9USVMxHDAaBgNVBAsMEzAwMDIgNDgxNDYzMDgx\n" +
            "MDAwMzYxHTAbBgNVBGEMFE5UUkZSLTQ4MTQ2MzA4MTAwMDM2MR0wGwYDVQQDDBRD\n" +
            "ZXJ0aWduYSBTZXJ2aWNlcyBDQTCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCCAgoC\n" +
            "ggIBALPM+7LpWBz9wFcPaTc3xnB+5g0XrnptB0EPPfrR04vO52Ykm4ky1d4ZLd10\n" +
            "tbM1fa1RqNSOVWWg93O4pL7zCFKlz6JV74ZZVhHpEAwzBwv2oPnxvVbxtSN67xsS\n" +
            "Y66ahUYxjzs8+3FhmsiRxqwnTYvK2u70uglUvRisOKyTL/M6JnrC4y8tlmoz7OSa\n" +
            "5BmBMVplJFQtvmON6N9aHLvYMz+EyJPCbXL6pELxeHjFT5QmIaRamsr2DOTaCjtB\n" +
            "ZKI1Wnh3X7lnbjM8MESJiV2t7E9tIQNG0Z/HI3tO4aaUMum3KysY5sC8v3vi7rry\n" +
            "GidgzHQhrtP0ZXWW5UH/k7umLS/P/XXWnCFpc2Lxa1uDGfc2im7xibRoPP+JNZsz\n" +
            "N76euFlls6jyEXAiwnVr14tVVTewLK0OWs5SJHpEKp8PGMZRDj59EmMvokWwzL6Q\n" +
            "zNZ6vVAp00oOm05sbspNY9+MFqGKKUsKvhFGEa4XmRNxDe6KswLcjPZB+NKHZ0QW\n" +
            "Fd4ip5C5XmEK/8qIPjwVr9dah9+oiHGGO8Wx7gJAMF5DTmkvW7GhqCKj1LmHnabj\n" +
            "zc8av6kxWVQZi/C7HCm9i/W4wio+JA2EAFLqNL3GPNbK9kau4yPhQt/c7zxzo0OH\n" +
            "nlsV4THCG7oOCd3cfCiyfQcb3FBt6OSpaKRZxjCLBwP00r0fAgMBAAGjggHZMIIB\n" +
            "1TASBgNVHRMBAf8ECDAGAQH/AgEAMA4GA1UdDwEB/wQEAwIBBjAdBgNVHQ4EFgQU\n" +
            "rOyGj0s3HLh/FxsZ0K7oTuM0XBIwZAYDVR0jBF0wW4AUGu3+QTmQtCRZvgHyUtVF\n" +
            "9lo53BGhOKQ2MDQxCzAJBgNVBAYTAkZSMRIwEAYDVQQKDAlEaGlteW90aXMxETAP\n" +
            "BgNVBAMMCENlcnRpZ25hggkA/tzjAQ/JSP8wSQYDVR0gBEIwQDA+BgoqgXoBgTEB\n" +
            "AAECMDAwLgYIKwYBBQUHAgEWImh0dHBzOi8vd3d3LmNlcnRpZ25hLmZyL2F1dG9y\n" +
            "aXRlcy8wfAYIKwYBBQUHAQEEcDBuMDQGCCsGAQUFBzAChihodHRwOi8vYXV0b3Jp\n" +
            "dGUuY2VydGlnbmEuZnIvY2VydGlnbmEuZGVyMDYGCCsGAQUFBzAChipodHRwOi8v\n" +
            "YXV0b3JpdGUuZGhpbXlvdGlzLmNvbS9jZXJ0aWduYS5kZXIwYQYDVR0fBFowWDAp\n" +
            "oCegJYYjaHR0cDovL2NybC5jZXJ0aWduYS5mci9jZXJ0aWduYS5jcmwwK6ApoCeG\n" +
            "JWh0dHA6Ly9jcmwuZGhpbXlvdGlzLmNvbS9jZXJ0aWduYS5jcmwwDQYJKoZIhvcN\n" +
            "AQELBQADggEBAGLft7gIuGPZVfg0cTM+HT2xAZFPDb/2+siH06x+dH044zMKbBIN\n" +
            "bRzhKipwB1A3MW8FQjveE9tyrfyuqZE/X+o2SlGcdNV44ybYkxo4f6kcLEavV/IW\n" +
            "+oFEnojZlhpksYcxrvQoEyqkAwshe8IS2KtZHKVACrt+XSs0lwvy7ALGmHaF7A4b\n" +
            "y6cZWItA7Lhj8XWp+8tBJDj7HocRbWtxzEODdBuyMgJzFrNjc+97J0vH/K0+3yjm\n" +
            "kczpKshMA0tM+MF9XDMN/MuwrPmUWGO/fHiqHgUp8yqeWtl1n44ZxkkK1t9GRwhn\n" +
            "DWLv73/xhTmdhWYQ/reo0GbgBoLiltKmIJQ=\n" +
            "-----END CERTIFICATE-----";

    // Owner: SERIALNUMBER=S266241169, CN=valid.servicesca.dhimyotis.com, O=DHIMYOTIS,
    // L=VILLENEUVE D'ASCQ, C=FR
    // Issuer: CN=Certigna Services CA, OID.2.5.4.97=NTRFR-48146308100036, OU=0002
    // 48146308100036, O=DHIMYOTIS, C=FR
    // Serial number: c641ef7b0340c21515d8c462e729dc0e
    // Valid from: Thu Mar 09 15:00:00 PST 2023 until: Mon Mar 11 15:59:59 PDT 2024
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIIdzCCBl+gAwIBAgIRAMZB73sDQMIVFdjEYucp3A4wDQYJKoZIhvcNAQELBQAw\n" +
            "fTELMAkGA1UEBhMCRlIxEjAQBgNVBAoMCURISU1ZT1RJUzEcMBoGA1UECwwTMDAw\n" +
            "MiA0ODE0NjMwODEwMDAzNjEdMBsGA1UEYQwUTlRSRlItNDgxNDYzMDgxMDAwMzYx\n" +
            "HTAbBgNVBAMMFENlcnRpZ25hIFNlcnZpY2VzIENBMB4XDTIzMDMwOTIzMDAwMFoX\n" +
            "DTI0MDMxMTIyNTk1OVowezELMAkGA1UEBhMCRlIxGjAYBgNVBAcMEVZJTExFTkVV\n" +
            "VkUgRCdBU0NRMRIwEAYDVQQKDAlESElNWU9USVMxJzAlBgNVBAMMHnZhbGlkLnNl\n" +
            "cnZpY2VzY2EuZGhpbXlvdGlzLmNvbTETMBEGA1UEBRMKUzI2NjI0MTE2OTCCASIw\n" +
            "DQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAJDrFpZWEeBJoMUuG37wEmJ7XVeX\n" +
            "Jde1bgURpFbLwifRj2TVmMdtfg9hXHL7B7Mh/+I8/e7kJz8mlU9qUYKyH24oAitE\n" +
            "myXYHAKTydqTseiM3mp92n4PM+DrgsdbT7bpmiirNM0/sqWFNyGUz7kP6Z5E3uuU\n" +
            "HSlzX1LBBj8S0ORNZWvomQho11gjuZJRS72X4XTnSc0DESwnLp2irUfx7pflBNt0\n" +
            "sLE8BhpNSSQd91naJVKtCtn0H7df+o4gGBt2ZceCLBwU0NwN8+KXz06KjP8298V4\n" +
            "P3+eR2QxAw4QBIanRaG6Gd4AmpdIaT7TpiYHotjrJ/Pbx5C8/cmgxxlmtI0CAwEA\n" +
            "AaOCA/IwggPuMIHkBggrBgEFBQcBAQSB1zCB1DA2BggrBgEFBQcwAoYqaHR0cDov\n" +
            "L2F1dG9yaXRlLmNlcnRpZ25hLmZyL3NlcnZpY2VzY2EuZGVyMDgGCCsGAQUFBzAC\n" +
            "hixodHRwOi8vYXV0b3JpdGUuZGhpbXlvdGlzLmNvbS9zZXJ2aWNlc2NhLmRlcjAu\n" +
            "BggrBgEFBQcwAYYiaHR0cDovL3NlcnZpY2VzY2Eub2NzcC5jZXJ0aWduYS5mcjAw\n" +
            "BggrBgEFBQcwAYYkaHR0cDovL3NlcnZpY2VzY2Eub2NzcC5kaGlteW90aXMuY29t\n" +
            "MB8GA1UdIwQYMBaAFKzsho9LNxy4fxcbGdCu6E7jNFwSMAkGA1UdEwQCMAAwYQYD\n" +
            "VR0gBFowWDAIBgZngQwBAgIwTAYLKoF6AYExAgUBAQEwPTA7BggrBgEFBQcCARYv\n" +
            "aHR0cHM6Ly93d3cuY2VydGlnbmEuY29tL2F1dG9yaXRlLWNlcnRpZmljYXRpb24w\n" +
            "ZQYDVR0fBF4wXDAtoCugKYYnaHR0cDovL2NybC5kaGlteW90aXMuY29tL3NlcnZp\n" +
            "Y2VzY2EuY3JsMCugKaAnhiVodHRwOi8vY3JsLmNlcnRpZ25hLmZyL3NlcnZpY2Vz\n" +
            "Y2EuY3JsMBMGA1UdJQQMMAoGCCsGAQUFBwMBMA4GA1UdDwEB/wQEAwIFoDBIBgNV\n" +
            "HREEQTA/gh12YWxpZC5zZXJ2aWNlc2NhLmNlcnRpZ25hLmNvbYIedmFsaWQuc2Vy\n" +
            "dmljZXNjYS5kaGlteW90aXMuY29tMB0GA1UdDgQWBBSzyYZfPBt65RUDq98+e0AK\n" +
            "U8pd/jCCAX8GCisGAQQB1nkCBAIEggFvBIIBawFpAHcA7s3QZNXbGs7FXLedtM0T\n" +
            "ojKHRny87N7DUUhZRnEftZsAAAGGy1ZNXwAABAMASDBGAiEAyG838/RfBOpojEI/\n" +
            "cx++f0tvuDbc/rVa0WNcd2f9HekCIQDVKV2wI3VkD3wNmO93m022H7kvKD1OBEhw\n" +
            "Tn6+0ZLA6QB2AHb/iD8KtvuVUcJhzPWHujS0pM27KdxoQgqf5mdMWjp0AAABhstW\n" +
            "TcYAAAQDAEcwRQIhAOuj/r5G1wHNgFOMg3jsr3uWmWzIIkTmwmp4hJqvsJzzAiBf\n" +
            "nm/jZCUW8DFY+iC+O/+Hzsk/kVDkKIlBDd6rA3MzJgB2AFWB1MIWkDYBSuoLm1c8\n" +
            "U/DA5Dh4cCUIFy+jqh0HE9MMAAABhstWTw4AAAQDAEcwRQIgRbCAqI1/nxc6P4de\n" +
            "Fqg/zc1+ldMDWjeamWjhctciGsgCIQDHQ4OKj0AA7hQKFIe1SVp+00BxRefFGmq7\n" +
            "ZJ+8q+pRqzANBgkqhkiG9w0BAQsFAAOCAgEAVkzCC9LIHU+iOi+GFeCtWxxa5Fsk\n" +
            "5gXnDJmtbdoVe2TJvOhrb+VnNI7/Ak+csBv3vxNl3P3DXIbPryB98aelleX7pkfP\n" +
            "PcKhFAlbwzbII2D3L0mjFLERtVwdnoEJXXKcHsb9hJResKipZ//daMPD8FthHvEE\n" +
            "HmtOrR0lHLjhbi4ODq0e4xyygbxFXXl5CCjtBw0jBtZaMDQaC3eemK9LkOggLz3h\n" +
            "qs/+VQ7RyKfcKCuGC5Wb4GJR+IDKH812hFsUWmXe26MPoyTrzLNq6tfQZHSuY5Hj\n" +
            "K0ZwldEkUZ2Hd7PrRlhCiGdVCp/2kS2yefhUkvX7Z5K5wX6n+LylfzOTvWf6ZPwQ\n" +
            "1jTI0Js8ig4eHF25GlqgOWrqbyF9j67kLs3f7/c5Kx3FlclJ7/vlL8zEcTmGU7rm\n" +
            "ZFOhEMDT/UYkitqAOvrgT60oIm9YJ1XTAVTeDbW0FFAb2nFmeBOrw8N3jaCb+jpO\n" +
            "ysBA/lDaGTiQhMlJK44vwgS+TjbeWHxvmAE5srKa7MWU8Mmku2vuX95lupJo4LmD\n" +
            "zOsihH00hyhtHFUB1TGXuaf77kFsipE6iycyxpcrpJ1UAWiZrba6PAZ85TbYhEdY\n" +
            "FDNm7F7CVPU67HV5gE2kDa3Jprd1SjwO095LsRptWhzxUByhee3JI0jljBTaKowy\n" +
            "jPv8oekm7zqCLzY=\n" +
            "-----END CERTIFICATE-----";

    // Owner: SERIALNUMBER=S266251168, CN=revoked.servicesca.certigna.com, O=DHIMYOTIS,
    // L=VILLENEUVE D'ASCQ, C=FR
    // Issuer: CN=Certigna Services CA, OID.2.5.4.97=NTRFR-48146308100036, OU=0002
    // 48146308100036, O=DHIMYOTIS, C=FR
    // Serial number: e863f752a23a735e3ccf958abf18565b
    // Valid from: Thu Mar 09 15:00:00 PST 2023 until: Fri Mar 08 14:59:59 PST 2024
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIIezCCBmOgAwIBAgIRAOhj91KiOnNePM+Vir8YVlswDQYJKoZIhvcNAQELBQAw\n" +
            "fTELMAkGA1UEBhMCRlIxEjAQBgNVBAoMCURISU1ZT1RJUzEcMBoGA1UECwwTMDAw\n" +
            "MiA0ODE0NjMwODEwMDAzNjEdMBsGA1UEYQwUTlRSRlItNDgxNDYzMDgxMDAwMzYx\n" +
            "HTAbBgNVBAMMFENlcnRpZ25hIFNlcnZpY2VzIENBMB4XDTIzMDMwOTIzMDAwMFoX\n" +
            "DTI0MDMwODIyNTk1OVowfDELMAkGA1UEBhMCRlIxGjAYBgNVBAcMEVZJTExFTkVV\n" +
            "VkUgRCdBU0NRMRIwEAYDVQQKDAlESElNWU9USVMxKDAmBgNVBAMMH3Jldm9rZWQu\n" +
            "c2VydmljZXNjYS5jZXJ0aWduYS5jb20xEzARBgNVBAUTClMyNjYyNTExNjgwggEi\n" +
            "MA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCBqKNjMkHqJ9EQa3CjuZ6EYMz6\n" +
            "mWODrEucRcJDihYMigaV1oRyquGlFQ82ootXaK5bU+EYSMmUwbRpdZ9G/oZUn2+K\n" +
            "MKAFDI+MoZoFhQC+2w0AzJycCf/hShUVxcRREKRKdfzv+k5YHj3e8ic16tGlTFXT\n" +
            "IF1x3y2Uru7mzZARsZJqnRqaqPPghT/QlBpcA04yLi3iSpgO++mRrJxTUoUHlDw/\n" +
            "a1nhqnDgH2yKN7tSfwFTetnXat6/UVt0CJ/6dJF6oY8bGWO1YB03Xdq735eLdJE4\n" +
            "t38pV/X8rf5Mc9ZQh8IGrjVW83M8mQmqaX5rbsOl0ZCA/q6RWxRFEF2SwK+dAgMB\n" +
            "AAGjggP1MIID8TCB5AYIKwYBBQUHAQEEgdcwgdQwNgYIKwYBBQUHMAKGKmh0dHA6\n" +
            "Ly9hdXRvcml0ZS5jZXJ0aWduYS5mci9zZXJ2aWNlc2NhLmRlcjA4BggrBgEFBQcw\n" +
            "AoYsaHR0cDovL2F1dG9yaXRlLmRoaW15b3Rpcy5jb20vc2VydmljZXNjYS5kZXIw\n" +
            "LgYIKwYBBQUHMAGGImh0dHA6Ly9zZXJ2aWNlc2NhLm9jc3AuY2VydGlnbmEuZnIw\n" +
            "MAYIKwYBBQUHMAGGJGh0dHA6Ly9zZXJ2aWNlc2NhLm9jc3AuZGhpbXlvdGlzLmNv\n" +
            "bTAfBgNVHSMEGDAWgBSs7IaPSzccuH8XGxnQruhO4zRcEjAJBgNVHRMEAjAAMGEG\n" +
            "A1UdIARaMFgwCAYGZ4EMAQICMEwGCyqBegGBMQIFAQEBMD0wOwYIKwYBBQUHAgEW\n" +
            "L2h0dHBzOi8vd3d3LmNlcnRpZ25hLmNvbS9hdXRvcml0ZS1jZXJ0aWZpY2F0aW9u\n" +
            "MGUGA1UdHwReMFwwLaAroCmGJ2h0dHA6Ly9jcmwuZGhpbXlvdGlzLmNvbS9zZXJ2\n" +
            "aWNlc2NhLmNybDAroCmgJ4YlaHR0cDovL2NybC5jZXJ0aWduYS5mci9zZXJ2aWNl\n" +
            "c2NhLmNybDATBgNVHSUEDDAKBggrBgEFBQcDATAOBgNVHQ8BAf8EBAMCBaAwTAYD\n" +
            "VR0RBEUwQ4IgcmV2b2tlZC5zZXJ2aWNlc2NhLmRoaW15b3Rpcy5jb22CH3Jldm9r\n" +
            "ZWQuc2VydmljZXNjYS5jZXJ0aWduYS5jb20wHQYDVR0OBBYEFEQsKyX8x8zVxVC2\n" +
            "HEK7+bOBLoMkMIIBfgYKKwYBBAHWeQIEAgSCAW4EggFqAWgAdgDuzdBk1dsazsVc\n" +
            "t520zROiModGfLzs3sNRSFlGcR+1mwAAAYbLTxPnAAAEAwBHMEUCIQD16IHX+8+4\n" +
            "zWnxIME4rzCgQIA4m5OsEqP6ssgRG5iurwIgdBOGFGlF6+DGPSm5FKuk5ShAA8ZC\n" +
            "AE+E27CKLkBTnfgAdgB2/4g/Crb7lVHCYcz1h7o0tKTNuyncaEIKn+ZnTFo6dAAA\n" +
            "AYbLTxRMAAAEAwBHMEUCIDmW9elysDm3zAeIXsgJwmL33EoMTyVhA3ah2jkvMjzv\n" +
            "AiEA6aIZXtwk2DnFt+GA6gLr4UgswUCuK4wxheDVwbpSw/4AdgA7U3d1Pi25gE6L\n" +
            "MFsG/kA7Z9hPw/THvQANLXJv4frUFwAAAYbLTxXAAAAEAwBHMEUCIQDGuOg7koEE\n" +
            "H9K4VkSHaDD9rAndys2BtswdspfRKUFR3QIgVZ7QUX3H56ECuI8wsAkSjBze4lBO\n" +
            "RgfN2xh3l9xQOK0wDQYJKoZIhvcNAQELBQADggIBAFQTTtyQSoV4Zq3QYMnb0yEp\n" +
            "u6Hwic/wpYN5L0km+zZoHWuf58vfj8Yg/sfKmftGSZHDdc3NfYSVBlT/0Hl4SDhi\n" +
            "zHLLyapoX2GNhbg3esu0Y1fch8E16z2A/wAwrFvxI0XrjHpOyDp4CBDYqDADNPiL\n" +
            "vlEkiwP6r7WHjUdWRb7W0t75uAkcajn46XKpFmaHHie5KBch+KDGsUionuH5ZW8Y\n" +
            "klh2B34uLWcGZuIR7PeCO9+91mbn/bBNeabGC70qMStaB139lp9P2M+l2WpyREUK\n" +
            "l7qHwTsrlMmNb8n44zGtY4wL9NSYWTdTfhcU0FAPdPcLlnjoQubJ1O0vPkzfVYns\n" +
            "WQrslxoCBor6CL6AYMQz3jbzQ0soD3Reb11+uTngWGJZtx4DT09RFB3a+1rcYjiS\n" +
            "ijCBB+Lqx0xfLQnfBv1A0wjNqUY+gyEe0SpXqB4edqy5uaqawRRKMuNSnb2BVz0/\n" +
            "keo1Kif/GSak+JUBpJ8hkJWygtrWCETUNfoseQhqo3gism0EGxJ04tBp+DRvfbrz\n" +
            "X4aBgALRro3jSIR1Ibp+e0fxePwShy715SF2H4SfjvplTAKq5bwztZtQUkPR6fJ7\n" +
            "5xT0f762c1yytKP1rHFMvzl6k7QWvC6zb2FeG5UqXJw3wFxxWsCuAUu5SPFfXdno\n" +
            "5lIHTTV5rpZBN+PzTZsz\n" +
            "-----END CERTIFICATE-----";

    public static void main(String[] args) throws Exception {
        // OCSP check by default
        boolean ocspEnabled = args.length < 1 || !"CRL".equalsIgnoreCase(args[0]);

        // CN=Certigna
        new CertignaCAs().runTest(ocspEnabled,
                VALID,
                REVOKED,
                INT_CERTIGNA);

        // CN=Certigna Root CA
        new CertignaCAs().runTest(ocspEnabled,
                VALID,
                REVOKED,
                INT_CERTIGNA_ROOT_CA);
    }
}

class CertignaCAs {
    public void runTest(boolean ocspEnabled,
                        final String VALID,
                        final String REVOKED,
                        final String INT_CERT) throws Exception {

        ValidatePathWithParams pathValidator;
        String[] validChainToValidate;
        String[] revChainToValidate;

        if (!ocspEnabled) {
            pathValidator = new ValidatePathWithParams(null);
            pathValidator.enableCRLCheck();

            validChainToValidate = new String[]{VALID, INT_CERT};
            revChainToValidate = new String[]{REVOKED, INT_CERT};
        } else {
            // int certificate doesn't specify OCSP responder
            pathValidator = new ValidatePathWithParams(new String[]{INT_CERT});
            pathValidator.enableOCSPCheck();

            validChainToValidate = new String[]{VALID};
            revChainToValidate = new String[]{REVOKED};
        }

        // Validate valid
        pathValidator.validate(validChainToValidate,
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(revChainToValidate,
                ValidatePathWithParams.Status.REVOKED,
                "Fri Mar 10 03:39:51 PST 2023", System.out);
    }
}
