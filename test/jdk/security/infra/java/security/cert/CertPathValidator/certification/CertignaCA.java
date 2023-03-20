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
 * @bug 8245654
 * @summary Interoperability tests with Certigna Root CA from Dhimyotis
 * @build ValidatePathWithParams
 * @run main/othervm -Djava.security.debug=certpath CertignaCA OCSP
 * @run main/othervm -Djava.security.debug=certpath CertignaCA CRL
 */

/*
 * Obtain TLS test artifacts for Certigna Root CA from:
 *
 * Valid TLS Certificates:
 * https://valid.servicesca.dhimyotis.com/
 *
 * Revoked TLS Certificates:
 * https://revoked.servicesca.dhimyotis.com/
 */
public class CertignaCA {

    // Owner: CN=Certigna Services CA, OID.2.5.4.97=NTRFR-48146308100036,
    // OU=0002 48146308100036, O=DHIMYOTIS, C=FR
    // Issuer: CN=Certigna, O=Dhimyotis, C=FR
    // Serial number: 6f82fa28acd6f784bb5b120ba87367ad
    // Valid from: Wed Nov 25 03:33:52 PST 2015 until: Sat Nov 22 03:33:52 PST 2025
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
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

    // Owner: SERIALNUMBER=S230100953, CN=valid.servicesca.dhimyotis.com,
    // OU=0002 48146308100036, O=DHIMYOTIS, L=VILLENEUVE D'ASCQ, C=FR
    // Issuer: CN=Certigna Services CA, OID.2.5.4.97=NTRFR-48146308100036,
    // OU=0002 48146308100036, O=DHIMYOTIS, C=FR
    // Serial number: 2959798fe2e0e7b43810169ae938bc5f
    // Valid from: Sun Mar 13 16:00:00 PDT 2022 until: Mon Mar 13 15:59:59 PDT 2023
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIIkzCCBnugAwIBAgIQKVl5j+Lg57Q4EBaa6Ti8XzANBgkqhkiG9w0BAQsFADB9\n" +
            "MQswCQYDVQQGEwJGUjESMBAGA1UECgwJREhJTVlPVElTMRwwGgYDVQQLDBMwMDAy\n" +
            "IDQ4MTQ2MzA4MTAwMDM2MR0wGwYDVQRhDBROVFJGUi00ODE0NjMwODEwMDAzNjEd\n" +
            "MBsGA1UEAwwUQ2VydGlnbmEgU2VydmljZXMgQ0EwHhcNMjIwMzEzMjMwMDAwWhcN\n" +
            "MjMwMzEzMjI1OTU5WjCBmTELMAkGA1UEBhMCRlIxGjAYBgNVBAcMEVZJTExFTkVV\n" +
            "VkUgRCdBU0NRMRIwEAYDVQQKDAlESElNWU9USVMxHDAaBgNVBAsMEzAwMDIgNDgx\n" +
            "NDYzMDgxMDAwMzYxJzAlBgNVBAMMHnZhbGlkLnNlcnZpY2VzY2EuZGhpbXlvdGlz\n" +
            "LmNvbTETMBEGA1UEBRMKUzIzMDEwMDk1MzCCASIwDQYJKoZIhvcNAQEBBQADggEP\n" +
            "ADCCAQoCggEBALpeGHbzRGnv1C0PdJS0nT+Cx98Pw8ctaw51m9Vlk2j8AFGZRu8r\n" +
            "lX3noQYX0AIfcbk6KqPAreIvJQV0UgM5jxt3mIQF7iU+55MG4mWmSJgKDDq4b3ck\n" +
            "WdBy0KpSBqLmB9sHyTNk9NilNu7VwG03HGIltWA2uQFJGC8CkxwAFpMCQ9RVYw2Z\n" +
            "NkL/SsiPgrRLiCJZjesk1oAcLnLp7hbelfUB2Z71VmuDDlom7CsLvdN8eIG+Lj+V\n" +
            "wkGmH6AbVGvbFniFDLCNDSJWCQ9AHeO+i0CM/wd2gBRSgm993p2YMxu5mVZjz/rp\n" +
            "ELaCYjulvNZKvPIFoNe8qsxlXRWeqWaHuPsCAwEAAaOCA/AwggPsMIHkBggrBgEF\n" +
            "BQcBAQSB1zCB1DA4BggrBgEFBQcwAoYsaHR0cDovL2F1dG9yaXRlLmRoaW15b3Rp\n" +
            "cy5jb20vc2VydmljZXNjYS5kZXIwNgYIKwYBBQUHMAKGKmh0dHA6Ly9hdXRvcml0\n" +
            "ZS5jZXJ0aWduYS5mci9zZXJ2aWNlc2NhLmRlcjAwBggrBgEFBQcwAYYkaHR0cDov\n" +
            "L3NlcnZpY2VzY2Eub2NzcC5kaGlteW90aXMuY29tMC4GCCsGAQUFBzABhiJodHRw\n" +
            "Oi8vc2VydmljZXNjYS5vY3NwLmNlcnRpZ25hLmZyMB8GA1UdIwQYMBaAFKzsho9L\n" +
            "Nxy4fxcbGdCu6E7jNFwSMAkGA1UdEwQCMAAwYQYDVR0gBFowWDAIBgZngQwBAgIw\n" +
            "TAYLKoF6AYExAgUBAQEwPTA7BggrBgEFBQcCARYvaHR0cHM6Ly93d3cuY2VydGln\n" +
            "bmEuY29tL2F1dG9yaXRlLWNlcnRpZmljYXRpb24wZQYDVR0fBF4wXDAroCmgJ4Yl\n" +
            "aHR0cDovL2NybC5jZXJ0aWduYS5mci9zZXJ2aWNlc2NhLmNybDAtoCugKYYnaHR0\n" +
            "cDovL2NybC5kaGlteW90aXMuY29tL3NlcnZpY2VzY2EuY3JsMBMGA1UdJQQMMAoG\n" +
            "CCsGAQUFBwMBMA4GA1UdDwEB/wQEAwIFoDBIBgNVHREEQTA/gh12YWxpZC5zZXJ2\n" +
            "aWNlc2NhLmNlcnRpZ25hLmNvbYIedmFsaWQuc2VydmljZXNjYS5kaGlteW90aXMu\n" +
            "Y29tMB0GA1UdDgQWBBSGQwwMIdxiI7P+CFU/Z968XZaSGzCCAX0GCisGAQQB1nkC\n" +
            "BAIEggFtBIIBaQFnAHUArfe++nz/EMiLnT2cHj4YarRnKV3PsQwkyoWGNOvcgooA\n" +
            "AAF/h9eOGgAABAMARjBEAiBaneK2CTn9lH28CUnL2C2/WklUYkvygMiDrtCIUXfw\n" +
            "gQIgJrGxwgGlsYzUdZyZY/oNWSLByO8/Jb5LXbNibdk5SnAAdwDoPtDaPvUGNTLn\n" +
            "Vyi8iWvJA9PL0RFr7Otp4Xd9bQa9bgAAAX+H14/NAAAEAwBIMEYCIQCVtuV9p/Ug\n" +
            "IhwVoMUjPp1KzGte/FmDaKPx432VjOpD+AIhANKWkDEuVnMzPH8sdJCL+eXoB0Q7\n" +
            "0mpe5dHEiFJS8lTBAHUAs3N3B+GEUPhjhtYFqdwRCUp5LbFnDAuH3PADDnk2pZoA\n" +
            "AAF/h9eTcQAABAMARjBEAiAjdYhnzPe9lJksk94ngl7PLDRi71tSRN7SslibEyv+\n" +
            "XAIgLQ5NKQAaJnF8oA7WnHB8gyJ/8kqZi52d1WFgARDLR30wDQYJKoZIhvcNAQEL\n" +
            "BQADggIBAJhLhW5Gh9yOPKsrMhABd7U5juc5ev97c6s7Az70Yr5/EtH6TlgC6a1N\n" +
            "i0yzFOeXzAR8Svsq6HzqP9kMJkEFIrdWH8JZdEv871EjYetEzLLnO0m+dNEROJAh\n" +
            "fcJ2w2LufPNaQ327tGY/DxDH9jdtgquReO01bPlJ0Yc5J3maz4XapeUm/kQ8dRzS\n" +
            "0UBOxfUlEMpDatZzg7wugy7g9vOndW/VbtbN5Iioq2bjuykPJZfZUx4cCAmLUS7w\n" +
            "bqPThQ54PnybiPXaF8cH1Gq0Rs/lGB1erzRXRXHgMy61mFY944r13oATnSdTy8Gm\n" +
            "QoMsVp9w7WBRo8O4PR606Ke8Ufm9Kg2GJ1sHClf70FNFO/OSFlr3BLDG0vEMdgVW\n" +
            "9QLu6UQXa9PhWMoo030k5fmUySzIUljXnstj3rgcD2HE1UrobTqyRHbbQ8JVWaF0\n" +
            "PrPR4WDFI9dY0jixVQucKlX6FCqsyNrJF8GWDlZH+Cd8bk+MA9fKUuX/vmoOc2d+\n" +
            "bvOCliME7YjAJkyclk6yiFIMnqyh+TD0d8WbjE94YC/293Xqb6WGkRhhsCX9RUrk\n" +
            "I6QbS2uicCFGjRsPmjvMkDDxS00MShRl2K/KpsAx68Cv/Gcw3bv31obwNXTB2IBg\n" +
            "gI0MfBHnjIp1nmNvCNmVIP52YrGQyC2JE7+GZUWTuwUVeDgBhiEZ\n" +
            "-----END CERTIFICATE-----";

    // Owner: SERIALNUMBER=S230120951, CN=revoked.servicesca.dhimyotis.com,
    // OU=0002 48146308100036, O=DHIMYOTIS, L=VILLENEUVE D'ASCQ, C=FR
    // Issuer: CN=Certigna Services CA, OID.2.5.4.97=NTRFR-48146308100036,
    // OU=0002 48146308100036, O=DHIMYOTIS, C=FR
    // Serial number: f88f2566b3dbf73763622db9b2bf9cc
    // Valid from: Sun Mar 13 16:00:00 PDT 2022 until: Mon Mar 13 15:59:59 PDT 2023
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIImTCCBoGgAwIBAgIQD4jyVms9v3N2NiLbmyv5zDANBgkqhkiG9w0BAQsFADB9\n" +
            "MQswCQYDVQQGEwJGUjESMBAGA1UECgwJREhJTVlPVElTMRwwGgYDVQQLDBMwMDAy\n" +
            "IDQ4MTQ2MzA4MTAwMDM2MR0wGwYDVQRhDBROVFJGUi00ODE0NjMwODEwMDAzNjEd\n" +
            "MBsGA1UEAwwUQ2VydGlnbmEgU2VydmljZXMgQ0EwHhcNMjIwMzEzMjMwMDAwWhcN\n" +
            "MjMwMzEzMjI1OTU5WjCBmzELMAkGA1UEBhMCRlIxGjAYBgNVBAcMEVZJTExFTkVV\n" +
            "VkUgRCdBU0NRMRIwEAYDVQQKDAlESElNWU9USVMxHDAaBgNVBAsMEzAwMDIgNDgx\n" +
            "NDYzMDgxMDAwMzYxKTAnBgNVBAMMIHJldm9rZWQuc2VydmljZXNjYS5kaGlteW90\n" +
            "aXMuY29tMRMwEQYDVQQFEwpTMjMwMTIwOTUxMIIBIjANBgkqhkiG9w0BAQEFAAOC\n" +
            "AQ8AMIIBCgKCAQEAouvIzemKChCjYICW+TzRigLkqaTdMLnaPlGaXyCCoEUS6nkK\n" +
            "QnrwTgebf1X9/mwSAuvTo3Ck7CVgE8AMqsPTluSjezCJuED/F3HYy2YsbIhnVK/i\n" +
            "uSzKsDGVY3RlVNm2MA2viVTNBbOFhk4kefYqpDCmp3EGvIDOCb7Y5PTuKKQ79s97\n" +
            "uDm+0WoBnOdwSuZMUg+hvINBgu2JQFwiWP0g/SxoK6Ci9SVokM3zR4KgECkMVArf\n" +
            "cH0dN+5SYvByaGegQJy7TdKqDsf1lIHM19tUXcxOBNRgV3Rf7WMNIlERtLXjRfke\n" +
            "IWXf8QtXRVIH/i/PoVTDo2qvQOMnZFY/Eb5dFQIDAQABo4ID9DCCA/AwgeQGCCsG\n" +
            "AQUFBwEBBIHXMIHUMDgGCCsGAQUFBzAChixodHRwOi8vYXV0b3JpdGUuZGhpbXlv\n" +
            "dGlzLmNvbS9zZXJ2aWNlc2NhLmRlcjA2BggrBgEFBQcwAoYqaHR0cDovL2F1dG9y\n" +
            "aXRlLmNlcnRpZ25hLmZyL3NlcnZpY2VzY2EuZGVyMDAGCCsGAQUFBzABhiRodHRw\n" +
            "Oi8vc2VydmljZXNjYS5vY3NwLmRoaW15b3Rpcy5jb20wLgYIKwYBBQUHMAGGImh0\n" +
            "dHA6Ly9zZXJ2aWNlc2NhLm9jc3AuY2VydGlnbmEuZnIwHwYDVR0jBBgwFoAUrOyG\n" +
            "j0s3HLh/FxsZ0K7oTuM0XBIwCQYDVR0TBAIwADBhBgNVHSAEWjBYMAgGBmeBDAEC\n" +
            "AjBMBgsqgXoBgTECBQEBATA9MDsGCCsGAQUFBwIBFi9odHRwczovL3d3dy5jZXJ0\n" +
            "aWduYS5jb20vYXV0b3JpdGUtY2VydGlmaWNhdGlvbjBlBgNVHR8EXjBcMCugKaAn\n" +
            "hiVodHRwOi8vY3JsLmNlcnRpZ25hLmZyL3NlcnZpY2VzY2EuY3JsMC2gK6Aphido\n" +
            "dHRwOi8vY3JsLmRoaW15b3Rpcy5jb20vc2VydmljZXNjYS5jcmwwEwYDVR0lBAww\n" +
            "CgYIKwYBBQUHAwEwDgYDVR0PAQH/BAQDAgWgMEwGA1UdEQRFMEOCH3Jldm9rZWQu\n" +
            "c2VydmljZXNjYS5jZXJ0aWduYS5jb22CIHJldm9rZWQuc2VydmljZXNjYS5kaGlt\n" +
            "eW90aXMuY29tMB0GA1UdDgQWBBTGIed1eHBS8Z1H3PdMkItpjyjq2TCCAX0GCisG\n" +
            "AQQB1nkCBAIEggFtBIIBaQFnAHcArfe++nz/EMiLnT2cHj4YarRnKV3PsQwkyoWG\n" +
            "NOvcgooAAAF/h9g4MAAABAMASDBGAiEAp/1fQB730JrX9YGD3d1Uq7rTAL95tMKe\n" +
            "G6kgUP1GEWoCIQCzi6feA3cImTH6tVZALNEmve/n8SVFAvD2AvX8ioCD9QB1AOg+\n" +
            "0No+9QY1MudXKLyJa8kD08vREWvs62nhd31tBr1uAAABf4fYNHcAAAQDAEYwRAIg\n" +
            "Dnd8oOV7/MuaiyR23qbdRVf1kBSsDxnLp1/vRdD0JTYCIAw7LuZalEVa/0KpuNHs\n" +
            "NIdUJgV4Vioa2xkb9fdPIhtkAHUAs3N3B+GEUPhjhtYFqdwRCUp5LbFnDAuH3PAD\n" +
            "Dnk2pZoAAAF/h9g7nwAABAMARjBEAiA80M1W3V3iKjm6Dwn+hKkmvGiuXZoM6o3f\n" +
            "QJsZ2ZOx0QIgUiS3I83WzoCdD4qO9rlmDQhRD69CeVzCgLtkaTPz3JYwDQYJKoZI\n" +
            "hvcNAQELBQADggIBADKub0gNyasTvURoYukQCllqDC+SvWA4TURBcmQMNjdVkreJ\n" +
            "B3O91HZhTyhrCBJxybeIG89zuRI6rjTpHCQGFqtP7968NA3eUlxGGnAPpw6VbN47\n" +
            "Ake+CRI9XnhxcKmTGm987DjtIBH42BedS59P1T56grZP5ysOog9Hz4eYo2ytbZqt\n" +
            "P/DHggivymaaiIaBsqup8C7/XN3vVAa/yo1FeLJ48i1d0M9hjGBUFMajd8Y5+pE7\n" +
            "p6Nb5mT1LXbetORYXMyG3MiJQPBAr1dLnRGnOZxc1Kxa1QwoAFQAFIXFpqfBwfHi\n" +
            "NaSDdFS/wLbpe7UvtC8FWLq9sgITDEkPqDPCsbu8Vc7OxaMhBJ7HQGaAYMReGADG\n" +
            "Elx9ffAc+dFR62zFnqMLouaEznZ7FVNmU3cYbrFVBvnGmoDRe0AKUoYv5DCiawUg\n" +
            "qeQS69DgG7DOE5VIDaWX2Cevy81mz7O8EVQsyS15J/MUxzWfQpRaHUqkge6G9FSH\n" +
            "hF/Nm48oWgpWop5aIF2O6bA/Bt1VvAWdypUPUr4gtpYIQoOQBzTFgBVWUeOTOImE\n" +
            "avvpzSwGQfZkB7t5PcAQ+zYGxWq7fr30/qY3geePcXJCGWS6PXyj8lNn4CaJ2sMF\n" +
            "GKxNJGD49/5uoxi3b3TzGUn/3eG2qP2RZoXZ6ZPLAo+moIy3XLwMoZm3Im8r\n" +
            "-----END CERTIFICATE-----";

    public static void main(String[] args) throws Exception {

        ValidatePathWithParams pathValidator;
        String[] validChainToValidate;
        String[] revChainToValidate;

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            pathValidator = new ValidatePathWithParams(null);
            pathValidator.enableCRLCheck();

            validChainToValidate = new String[]{VALID, INT};
            revChainToValidate = new String[]{REVOKED, INT};
        } else {
            // OCSP check by default
            // int certificate doesn't specify OCSP responder
            pathValidator = new ValidatePathWithParams(new String[]{INT});
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
                "Mon Mar 14 03:00:16 PDT 2022", System.out);
    }
}

