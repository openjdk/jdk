/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8359170
 * @summary Interoperability tests with Sectigo Public Code Signing Root CAs
 * @build ValidatePathWithParams
 * @run main/othervm/manual -Djava.security.debug=ocsp,certpath SectigoCSRootCAs OCSP
 * @run main/othervm/manual -Djava.security.debug=certpath SectigoCSRootCAs CRL
 */

public class SectigoCSRootCAs {

    public static void main(String[] args) throws Exception {

        ValidatePathWithParams pathValidator = new ValidatePathWithParams(null);

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            pathValidator.enableCRLCheck();
        } else {
            // OCSP check by default
            pathValidator.enableOCSPCheck();
        }

        new SectigoCSRootCA_R46().runTest(pathValidator);
        new SectigoCSRootCA_E46().runTest(pathValidator);
    }
}

class SectigoCSRootCA_R46 {

    // Owner: CN=Sectigo Public Code Signing CA R36, O=Sectigo Limited, C=GB
    // Issuer: CN=Sectigo Public Code Signing Root R46, O=Sectigo Limited, C=GB
    // Serial number: 621d6d0c52019e3b9079152089211c0a
    // Valid from: Sun Mar 21 17:00:00 PDT 2021 until: Fri Mar 21 16:59:59
    // PDT 2036
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGGjCCBAKgAwIBAgIQYh1tDFIBnjuQeRUgiSEcCjANBgkqhkiG9w0BAQwFADBW\n" +
            "MQswCQYDVQQGEwJHQjEYMBYGA1UEChMPU2VjdGlnbyBMaW1pdGVkMS0wKwYDVQQD\n" +
            "EyRTZWN0aWdvIFB1YmxpYyBDb2RlIFNpZ25pbmcgUm9vdCBSNDYwHhcNMjEwMzIy\n" +
            "MDAwMDAwWhcNMzYwMzIxMjM1OTU5WjBUMQswCQYDVQQGEwJHQjEYMBYGA1UEChMP\n" +
            "U2VjdGlnbyBMaW1pdGVkMSswKQYDVQQDEyJTZWN0aWdvIFB1YmxpYyBDb2RlIFNp\n" +
            "Z25pbmcgQ0EgUjM2MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAmyud\n" +
            "U/o1P45gBkNqwM/1f/bIU1MYyM7TbH78WAeVF3llMwsRHgBGRmxDeEDIArCS2VCo\n" +
            "Vk4Y/8j6stIkmYV5Gej4NgNjVQ4BYoDjGMwdjioXan1hlaGFt4Wk9vT0k2oWJMJj\n" +
            "L9G//N523hAm4jF4UjrW2pvv9+hdPX8tbbAfI3v0VdJiJPFy/7XwiunD7mBxNtec\n" +
            "M6ytIdUlh08T2z7mJEXZD9OWcJkZk5wDuf2q52PN43jc4T9OkoXZ0arWZVeffvMr\n" +
            "/iiIROSCzKoDmWABDRzV/UiQ5vqsaeFaqQdzFf4ed8peNWh1OaZXnYvZQgWx/SXi\n" +
            "JDRSAolRzZEZquE6cbcH747FHncs/Kzcn0Ccv2jrOW+LPmnOyB+tAfiWu01TPhCr\n" +
            "9VrkxsHC5qFNxaThTG5j4/Kc+ODD2dX/fmBECELcvzUHf9shoFvrn35XGf2RPaNT\n" +
            "O2uSZ6n9otv7jElspkfK9qEATHZcodp+R4q2OIypxR//YEb3fkDn3UayWW9bAgMB\n" +
            "AAGjggFkMIIBYDAfBgNVHSMEGDAWgBQy65Ka/zWWSC8oQEJwIDaRXBeF5jAdBgNV\n" +
            "HQ4EFgQUDyrLIIcouOxvSK4rVKYpqhekzQwwDgYDVR0PAQH/BAQDAgGGMBIGA1Ud\n" +
            "EwEB/wQIMAYBAf8CAQAwEwYDVR0lBAwwCgYIKwYBBQUHAwMwGwYDVR0gBBQwEjAG\n" +
            "BgRVHSAAMAgGBmeBDAEEATBLBgNVHR8ERDBCMECgPqA8hjpodHRwOi8vY3JsLnNl\n" +
            "Y3RpZ28uY29tL1NlY3RpZ29QdWJsaWNDb2RlU2lnbmluZ1Jvb3RSNDYuY3JsMHsG\n" +
            "CCsGAQUFBwEBBG8wbTBGBggrBgEFBQcwAoY6aHR0cDovL2NydC5zZWN0aWdvLmNv\n" +
            "bS9TZWN0aWdvUHVibGljQ29kZVNpZ25pbmdSb290UjQ2LnA3YzAjBggrBgEFBQcw\n" +
            "AYYXaHR0cDovL29jc3Auc2VjdGlnby5jb20wDQYJKoZIhvcNAQEMBQADggIBAAb/\n" +
            "guF3YzZue6EVIJsT/wT+mHVEYcNWlXHRkT+FoetAQLHI1uBy/YXKZDk8+Y1LoNqH\n" +
            "rp22AKMGxQtgCivnDHFyAQ9GXTmlk7MjcgQbDCx6mn7yIawsppWkvfPkKaAQsiqa\n" +
            "T9DnMWBHVNIabGqgQSGTrQWo43MOfsPynhbz2Hyxf5XWKZpRvr3dMapandPfYgoZ\n" +
            "8iDL2OR3sYztgJrbG6VZ9DoTXFm1g0Rf97Aaen1l4c+w3DC+IkwFkvjFV3jS49ZS\n" +
            "c4lShKK6BrPTJYs4NG1DGzmpToTnwoqZ8fAmi2XlZnuchC4NPSZaPATHvNIzt+z1\n" +
            "PHo35D/f7j2pO1S8BCysQDHCbM5Mnomnq5aYcKCsdbh0czchOm8bkinLrYrKpii+\n" +
            "Tk7pwL7TjRKLXkomm5D1Umds++pip8wH2cQpf93at3VDcOK4N7EwoIJB0kak6pSz\n" +
            "Eu4I64U6gZs7tS/dGNSljf2OSSnRr7KWzq03zl8l75jy+hOds9TWSenLbjBQUGR9\n" +
            "6cFr6lEUfAIEHVC1L68Y1GGxx4/eRI82ut83axHMViw1+sVpbPxg51Tbnio1lB93\n" +
            "079WPFnYaOvfGAA0e0zcfF/M9gXr+korwQTh2Prqooq2bYNMvUoUKD85gnJ+t0sm\n" +
            "rWrb8dee2CvYZXD5laGtaAxOfy/VKNmwuWuAh9kc\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=Sectigo Limited, O=Sectigo Limited, ST=West Yorkshire, C=GB
    // Issuer: CN=Sectigo Public Code Signing CA R36, O=Sectigo Limited, C=GB
    // Serial number: c1de046377578f1605414f3fa91bf5f6
    // Valid from: Wed Jun 04 17:00:00 PDT 2025 until: Fri Jun 05 16:59:59
    // PDT 2026
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGRDCCBKygAwIBAgIRAMHeBGN3V48WBUFPP6kb9fYwDQYJKoZIhvcNAQEMBQAw\n" +
            "VDELMAkGA1UEBhMCR0IxGDAWBgNVBAoTD1NlY3RpZ28gTGltaXRlZDErMCkGA1UE\n" +
            "AxMiU2VjdGlnbyBQdWJsaWMgQ29kZSBTaWduaW5nIENBIFIzNjAeFw0yNTA2MDUw\n" +
            "MDAwMDBaFw0yNjA2MDUyMzU5NTlaMFoxCzAJBgNVBAYTAkdCMRcwFQYDVQQIDA5X\n" +
            "ZXN0IFlvcmtzaGlyZTEYMBYGA1UECgwPU2VjdGlnbyBMaW1pdGVkMRgwFgYDVQQD\n" +
            "DA9TZWN0aWdvIExpbWl0ZWQwggIiMA0GCSqGSIb3DQEBAQUAA4ICDwAwggIKAoIC\n" +
            "AQDCYjakgsoYkqVpENW0MuN5hBZDdIM60WJgBXU7zTAXORntSu/Grn/SQywwTg4o\n" +
            "ltRcKuCp0Cd5zLAIjtgpVDDACWHgxKUtxerjjBZeGp0+viR+biLL0mVNPXgZZ5bQ\n" +
            "AnDYVKJaGnPsXQD8l+Bn/R2c4cw7mXjBYp2KrTuqOBkPzk4LmdgpKXjxiw1yYb+n\n" +
            "WKZ+3BMLIU6/k+LB9+WB6Odrl4Lff1jB4C6XhQELGjZAbpkFB2+Qr0ajIA3ZFXqU\n" +
            "IMh0j5oD5juuXxryOvCgSBkEwxPHnlXxZBNd3DmrZ9NGClBIGE2f9FOjzo5Rl7lV\n" +
            "KlzFdFmcH8LaLtWjniF+iT+YZw3Ld1O9VMK7RaHePsS4JYfbjeapoCEgudecmIz4\n" +
            "5Q2tTjCdR5s/SxiVbynfEw+cAGeiv4sRXNSg0uhZ2eGMHh6mPley2pUoRMR8Qx1U\n" +
            "0Uzg2NtPsHAo0DIH3jKEWU2zP5UPwCfqKYGaZLNLeGh07NZHBCp3TGp9kPVen5Ib\n" +
            "tnJssu+pab7fixvbpDM4/r9MkKU6C1IsE6lffyON0PA6LaywwecYTJGpieXqoz03\n" +
            "5UmQXvAzkb9omIAcQ6yWMZNrqwwG9XRKQEhvG3v7sbFkck1KZOz4r/KHkLx9sIxm\n" +
            "vngdD/qLFebxPIvPT0GKnvSzuGcdQXVTdkZBBBrunv+XpQIDAQABo4IBiTCCAYUw\n" +
            "HwYDVR0jBBgwFoAUDyrLIIcouOxvSK4rVKYpqhekzQwwHQYDVR0OBBYEFGMpbbJO\n" +
            "xiuD6t+HEyA3hjp4devXMA4GA1UdDwEB/wQEAwIHgDAMBgNVHRMBAf8EAjAAMBMG\n" +
            "A1UdJQQMMAoGCCsGAQUFBwMDMEoGA1UdIARDMEEwNQYMKwYBBAGyMQECAQMCMCUw\n" +
            "IwYIKwYBBQUHAgEWF2h0dHBzOi8vc2VjdGlnby5jb20vQ1BTMAgGBmeBDAEEATBJ\n" +
            "BgNVHR8EQjBAMD6gPKA6hjhodHRwOi8vY3JsLnNlY3RpZ28uY29tL1NlY3RpZ29Q\n" +
            "dWJsaWNDb2RlU2lnbmluZ0NBUjM2LmNybDB5BggrBgEFBQcBAQRtMGswRAYIKwYB\n" +
            "BQUHMAKGOGh0dHA6Ly9jcnQuc2VjdGlnby5jb20vU2VjdGlnb1B1YmxpY0NvZGVT\n" +
            "aWduaW5nQ0FSMzYuY3J0MCMGCCsGAQUFBzABhhdodHRwOi8vb2NzcC5zZWN0aWdv\n" +
            "LmNvbTANBgkqhkiG9w0BAQwFAAOCAYEAP2OCzJ+0hrg4XK3w+Ypoe0G5hKfzZ9RH\n" +
            "nDcgY8BjgWYVOlN9ad2bU70RfgkZzylkaSt02KHOpkXmYpgfjZsovmyUchvlZ4fU\n" +
            "RmivZleuO3G/ZvDFX373S40QFDd+lC1LYYUolRVz7/ZU2Vzql4FxsM1psRaW17xj\n" +
            "jf9qaAvDlOH45eEEkfRUbIDdn1UYqDxdCN+90jtD1vRWkYINvE1T6mq3rHpEVoTS\n" +
            "dIOgmcSL3MAKMB0LxWUPfoVdhnoUuoIxIAcV1SuR6zej4wHjClEaR8ReT/C23Jr3\n" +
            "hQ4VDbfGu3gvlZG8/+lNmT+t4WaNPECxbFP0BgbD70FP594mSVH3fgptYiiRN7ez\n" +
            "iUfOSBeCZpSMm7Z5P0KkxkagyFIR3vzgvqbJS/iaomvd9ZIkd9AwMEhugJpITeZj\n" +
            "lKSXs+2q2UHQdLTPGVoOjmqyPhDGKAeVVF+jLIUWwtAaAoJm6ooXSp8sAeLA+e+K\n" +
            "6RUFETVxhCefCjkbWq64OYLXriDb0l+W\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=Sectigo Limited, O=Sectigo Limited, ST=West Yorkshire, C=GB
    // Issuer: CN=Sectigo Public Code Signing CA R36, O=Sectigo Limited, C=GB
    // Serial number: 5ca6fb60da04db99dedbbc0c37131ec
    // Valid from: Wed Jun 04 17:00:00 PDT 2025 until: Sun Jun 04 16:59:59
    // PDT 2028
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGQzCCBKugAwIBAgIQBcpvtg2gTbmd7bvAw3Ex7DANBgkqhkiG9w0BAQwFADBU\n" +
            "MQswCQYDVQQGEwJHQjEYMBYGA1UEChMPU2VjdGlnbyBMaW1pdGVkMSswKQYDVQQD\n" +
            "EyJTZWN0aWdvIFB1YmxpYyBDb2RlIFNpZ25pbmcgQ0EgUjM2MB4XDTI1MDYwNTAw\n" +
            "MDAwMFoXDTI4MDYwNDIzNTk1OVowWjELMAkGA1UEBhMCR0IxFzAVBgNVBAgMDldl\n" +
            "c3QgWW9ya3NoaXJlMRgwFgYDVQQKDA9TZWN0aWdvIExpbWl0ZWQxGDAWBgNVBAMM\n" +
            "D1NlY3RpZ28gTGltaXRlZDCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIB\n" +
            "ALDjoFCMN16zlrcv/saieSYT7FXEhTVDPLNzGAbNdBEX09FoL5EIkiN3biVIbDki\n" +
            "bIocCFpo/yTrjARG/zz82AjdWHyomdtDjL35CQmgiUX7V8tu64xUfUAgadqm+0PL\n" +
            "by1LRddKE7chpdJu+EDEmeYDPqcRGM+u8suPgosFf6XfVNFy/FZJiD1c7q6JNZ8i\n" +
            "5NrvTs0zA9HckKE3uvPO9rw56EyF3SfUz9+zHKHwSElv8nCYpREudUf4yCzPNisK\n" +
            "MVovzeCo36nzJFEdWTnDOr4mtvfCEGvJOU3mgzpECK7QF+yFifr90SG4lvrwzkig\n" +
            "wYQymukXmB2gxN1tGOvgLig3Q/b4vljBiEeRPEba/L8YQnaXpR/BnPze8yb2t39l\n" +
            "bzmnghkWkGA0PAB2vrzpi7pq12fGOD0+ErtAzAl/TAD/UFWwXDQLWX9LXRRKi5E+\n" +
            "ScTlqLl9U1q9HsWYfM6CvLbc32TByaQ8yBytvsSRB0C0blp7CtP5MAc8j9xJdwAs\n" +
            "Mj2bvSOfA+NJ0Kdg/tqdHHU6hex2HnGzDiEhovm6u/oAfDp/i2bBKLgARglMfGaC\n" +
            "hFWeHLL6GAyBezMv+AQNCDCTYDMlqAihVMRUAfYgoHcVCfvTSETTTGdRUDFzIdCA\n" +
            "wNwSVfykpadsev43I2IF+F3aNgJYuXnpxSCLPngemcgxAgMBAAGjggGJMIIBhTAf\n" +
            "BgNVHSMEGDAWgBQPKssghyi47G9IritUpimqF6TNDDAdBgNVHQ4EFgQUlff/C/GC\n" +
            "faJ+Y7ua3hKsCsrW9y4wDgYDVR0PAQH/BAQDAgeAMAwGA1UdEwEB/wQCMAAwEwYD\n" +
            "VR0lBAwwCgYIKwYBBQUHAwMwSgYDVR0gBEMwQTA1BgwrBgEEAbIxAQIBAwIwJTAj\n" +
            "BggrBgEFBQcCARYXaHR0cHM6Ly9zZWN0aWdvLmNvbS9DUFMwCAYGZ4EMAQQBMEkG\n" +
            "A1UdHwRCMEAwPqA8oDqGOGh0dHA6Ly9jcmwuc2VjdGlnby5jb20vU2VjdGlnb1B1\n" +
            "YmxpY0NvZGVTaWduaW5nQ0FSMzYuY3JsMHkGCCsGAQUFBwEBBG0wazBEBggrBgEF\n" +
            "BQcwAoY4aHR0cDovL2NydC5zZWN0aWdvLmNvbS9TZWN0aWdvUHVibGljQ29kZVNp\n" +
            "Z25pbmdDQVIzNi5jcnQwIwYIKwYBBQUHMAGGF2h0dHA6Ly9vY3NwLnNlY3RpZ28u\n" +
            "Y29tMA0GCSqGSIb3DQEBDAUAA4IBgQAfVq3mY7ggMcTJeWKKrGxs9RUiuAY0p4Xv\n" +
            "CHNQViM/tHAn0t/nkPp+d2Ji3Zr6PefN+1F6zmsxsbZHse52JNHWYUwCb/Dx4Vw6\n" +
            "3Wnc1zhXtZnvUTUfgrivuIsMjUG8yzTdEt/taMKEO0KqlKPsBPgFKveDVVaq9UZa\n" +
            "FfxTWqgrnvkvP/Lag/YeKKj4cJG+a/MJZJm7kvyaBNKXVAamr/bumoxKDzpD67ds\n" +
            "n9qwBi2Mv0rRXvZ2SHQXzsJ/zjNKWUhpPVrpypaER7EUxjNuSgC4L8AmfvHiO67v\n" +
            "9EVIEud+beP3FtCXl/cSHhVeDxiC0KBXXBl9zLBaYvCH+8iABnZLStLgBDtfdkfk\n" +
            "TZEAGbrNOXJDMnKRxr8y377Zq+KHwfiTnyizACHyMMTi+CCwg1ZFGcLOHa5shByc\n" +
            "Ln9lYysM1/5vrEjt3ZUw11+pDqbPCGS++xgAwcftKfJ0TZrW/g6NZ9URg+11H9ad\n" +
            "WalBv2VkhJAFJam9P2Y+pi9luk85sGo=\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator) throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Thu Jun 05 10:27:45 PDT 2025", System.out);
    }
}

class SectigoCSRootCA_E46 {

    // Owner: CN=Sectigo Public Code Signing CA EV E36, O=Sectigo Limited, C=GB
    // Issuer: CN=Sectigo Public Code Signing Root E46, O=Sectigo Limited, C=GB
    // Serial number: 3774434f9eb40e221f9236ca1f2f2717
    // Valid from: Sun Mar 21 17:00:00 PDT 2021 until: Fri Mar 21 16:59:59
    // PDT 2036
    private static final String INT_VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIDMDCCAragAwIBAgIQN3RDT560DiIfkjbKHy8nFzAKBggqhkjOPQQDAzBWMQsw\n" +
            "CQYDVQQGEwJHQjEYMBYGA1UEChMPU2VjdGlnbyBMaW1pdGVkMS0wKwYDVQQDEyRT\n" +
            "ZWN0aWdvIFB1YmxpYyBDb2RlIFNpZ25pbmcgUm9vdCBFNDYwHhcNMjEwMzIyMDAw\n" +
            "MDAwWhcNMzYwMzIxMjM1OTU5WjBXMQswCQYDVQQGEwJHQjEYMBYGA1UEChMPU2Vj\n" +
            "dGlnbyBMaW1pdGVkMS4wLAYDVQQDEyVTZWN0aWdvIFB1YmxpYyBDb2RlIFNpZ25p\n" +
            "bmcgQ0EgRVYgRTM2MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE3mMV9nNViYoH\n" +
            "4aSrPwFjpbbeXHw2pMbqezwDGb45uEZQr3qI9Hgt0k4R26o5upfXzJt03F8efu0r\n" +
            "RNEs4yDDz6OCAWMwggFfMB8GA1UdIwQYMBaAFM99LKCQepgd3bZehcLg2hVx0uVe\n" +
            "MB0GA1UdDgQWBBQadKQ417m2DrNb+txerj+28HM9iDAOBgNVHQ8BAf8EBAMCAYYw\n" +
            "EgYDVR0TAQH/BAgwBgEB/wIBADATBgNVHSUEDDAKBggrBgEFBQcDAzAaBgNVHSAE\n" +
            "EzARMAYGBFUdIAAwBwYFZ4EMAQMwSwYDVR0fBEQwQjBAoD6gPIY6aHR0cDovL2Ny\n" +
            "bC5zZWN0aWdvLmNvbS9TZWN0aWdvUHVibGljQ29kZVNpZ25pbmdSb290RTQ2LmNy\n" +
            "bDB7BggrBgEFBQcBAQRvMG0wRgYIKwYBBQUHMAKGOmh0dHA6Ly9jcnQuc2VjdGln\n" +
            "by5jb20vU2VjdGlnb1B1YmxpY0NvZGVTaWduaW5nUm9vdEU0Ni5wN2MwIwYIKwYB\n" +
            "BQUHMAGGF2h0dHA6Ly9vY3NwLnNlY3RpZ28uY29tMAoGCCqGSM49BAMDA2gAMGUC\n" +
            "MQCger3L4CYx2W7HyHzvLaAnNee9QVqOwOrBYZyyqXERLtZg1DscsdoYZ2gszEW3\n" +
            "zaUCMAaLtcwdoV35ADpru29wChS7kFgXt599Ex27wmL++uJCJth6xYr3nyF2b2YJ\n" +
            "DAatOw==\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=Sectigo Public Code Signing CA E36, O=Sectigo Limited, C=GB
    // Issuer: CN=Sectigo Public Code Signing Root E46, O=Sectigo Limited, C=GB
    // Serial number: 3602617636e7034b9cc1fc5ffeac2d54
    // Valid from: Sun Mar 21 17:00:00 PDT 2021 until: Fri Mar 21 16:59:59
    // PDT 2036
    private static final String INT_REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIDLjCCArSgAwIBAgIQNgJhdjbnA0ucwfxf/qwtVDAKBggqhkjOPQQDAzBWMQsw\n" +
            "CQYDVQQGEwJHQjEYMBYGA1UEChMPU2VjdGlnbyBMaW1pdGVkMS0wKwYDVQQDEyRT\n" +
            "ZWN0aWdvIFB1YmxpYyBDb2RlIFNpZ25pbmcgUm9vdCBFNDYwHhcNMjEwMzIyMDAw\n" +
            "MDAwWhcNMzYwMzIxMjM1OTU5WjBUMQswCQYDVQQGEwJHQjEYMBYGA1UEChMPU2Vj\n" +
            "dGlnbyBMaW1pdGVkMSswKQYDVQQDEyJTZWN0aWdvIFB1YmxpYyBDb2RlIFNpZ25p\n" +
            "bmcgQ0EgRTM2MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAElDe1m6jawDhrwMxJ\n" +
            "yFPhKYf8EGu+lBw3bF5CzmfaH1I7Zi+WAmkeEwS3tiNxzPh8GbBBLtdaRuqGuyWc\n" +
            "W6ERmaOCAWQwggFgMB8GA1UdIwQYMBaAFM99LKCQepgd3bZehcLg2hVx0uVeMB0G\n" +
            "A1UdDgQWBBQlDZtt2Bh3t4rDOFFW5cfytf+DajAOBgNVHQ8BAf8EBAMCAYYwEgYD\n" +
            "VR0TAQH/BAgwBgEB/wIBADATBgNVHSUEDDAKBggrBgEFBQcDAzAbBgNVHSAEFDAS\n" +
            "MAYGBFUdIAAwCAYGZ4EMAQQBMEsGA1UdHwREMEIwQKA+oDyGOmh0dHA6Ly9jcmwu\n" +
            "c2VjdGlnby5jb20vU2VjdGlnb1B1YmxpY0NvZGVTaWduaW5nUm9vdEU0Ni5jcmww\n" +
            "ewYIKwYBBQUHAQEEbzBtMEYGCCsGAQUFBzAChjpodHRwOi8vY3J0LnNlY3RpZ28u\n" +
            "Y29tL1NlY3RpZ29QdWJsaWNDb2RlU2lnbmluZ1Jvb3RFNDYucDdjMCMGCCsGAQUF\n" +
            "BzABhhdodHRwOi8vb2NzcC5zZWN0aWdvLmNvbTAKBggqhkjOPQQDAwNoADBlAjBM\n" +
            "ykNTSVvegC1m17yIi87qgx6QIGbw1Mw2bQ4gtOWBVb/C8ALByC1YK7yQJNLJFTkC\n" +
            "MQCNBv3fe1eLrGELS5KQD0cEFbXGlzQ5r1mnOHePMqlK5d+rmMxff58/t6bo3QEb\n" +
            "8SQ=\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=Sectigo Limited, O=Sectigo Limited, ST=West Yorkshire, C=GB,
    // OID.2.5.4.15=Private Organization, OID.1.3.6.1.4.1.311.60.2.1.3=GB,
    // SERIALNUMBER=04058690
    // Issuer: CN=Sectigo Public Code Signing CA EV E36, O=Sectigo Limited, C=GB
    // Serial number: fa2aa131f36b337717ac73f606a6ec49
    // Valid from: Tue Feb 13 16:00:00 PST 2024 until: Sat Feb 13 15:59:59
    // PST 2027
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIDrjCCA1SgAwIBAgIRAPoqoTHzazN3F6xz9gam7EkwCgYIKoZIzj0EAwIwVzEL\n" +
            "MAkGA1UEBhMCR0IxGDAWBgNVBAoTD1NlY3RpZ28gTGltaXRlZDEuMCwGA1UEAxMl\n" +
            "U2VjdGlnbyBQdWJsaWMgQ29kZSBTaWduaW5nIENBIEVWIEUzNjAeFw0yNDAyMTQw\n" +
            "MDAwMDBaFw0yNzAyMTMyMzU5NTlaMIGhMREwDwYDVQQFEwgwNDA1ODY5MDETMBEG\n" +
            "CysGAQQBgjc8AgEDEwJHQjEdMBsGA1UEDxMUUHJpdmF0ZSBPcmdhbml6YXRpb24x\n" +
            "CzAJBgNVBAYTAkdCMRcwFQYDVQQIDA5XZXN0IFlvcmtzaGlyZTEYMBYGA1UECgwP\n" +
            "U2VjdGlnbyBMaW1pdGVkMRgwFgYDVQQDDA9TZWN0aWdvIExpbWl0ZWQwWTATBgcq\n" +
            "hkjOPQIBBggqhkjOPQMBBwNCAASwXGEU01WkW/hWNYza08ZT7i0ZeZ9M1s93JYEB\n" +
            "rZ/f1Ho1YzxtToqgIK2o+32afablPFYWlE6wGyuL/TYggBpKo4IBtDCCAbAwHwYD\n" +
            "VR0jBBgwFoAUGnSkONe5tg6zW/rcXq4/tvBzPYgwHQYDVR0OBBYEFHEcsJgcYuDO\n" +
            "dv1raL6h83a6j9C/MA4GA1UdDwEB/wQEAwIHgDAMBgNVHRMBAf8EAjAAMBMGA1Ud\n" +
            "JQQMMAoGCCsGAQUFBwMDMEkGA1UdIARCMEAwNQYMKwYBBAGyMQECAQYBMCUwIwYI\n" +
            "KwYBBQUHAgEWF2h0dHBzOi8vc2VjdGlnby5jb20vQ1BTMAcGBWeBDAEDMEsGA1Ud\n" +
            "HwREMEIwQKA+oDyGOmh0dHA6Ly9jcmwuc2VjdGlnby5jb20vU2VjdGlnb1B1Ymxp\n" +
            "Y0NvZGVTaWduaW5nQ0FFVkUzNi5jcmwwewYIKwYBBQUHAQEEbzBtMEYGCCsGAQUF\n" +
            "BzAChjpodHRwOi8vY3J0LnNlY3RpZ28uY29tL1NlY3RpZ29QdWJsaWNDb2RlU2ln\n" +
            "bmluZ0NBRVZFMzYuY3J0MCMGCCsGAQUFBzABhhdodHRwOi8vb2NzcC5zZWN0aWdv\n" +
            "LmNvbTAmBgNVHREEHzAdoBsGCCsGAQUFBwgDoA8wDQwLR0ItMDQwNTg2OTAwCgYI\n" +
            "KoZIzj0EAwIDSAAwRQIgQVp7IIkEZNmC7GfmT1MSEhDebIjjzd75ZVEEbPP/4ocC\n" +
            "IQDSyfPDKNMbKNOKrweDLwSE2GZV6nDWbiLz6ZmSZHob8w==\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=Sectigo Limited, O=Sectigo Limited, ST=West Yorkshire, C=GB
    // Issuer: CN=Sectigo Public Code Signing CA E36, O=Sectigo Limited, C=GB
    // Serial number: a1f601514271f24ca0a31c0d7b856705
    // Valid from: Wed Jun 04 17:00:00 PDT 2025 until: Sun Jun 04 16:59:59
    // PDT 2028
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIDOTCCAt6gAwIBAgIRAKH2AVFCcfJMoKMcDXuFZwUwCgYIKoZIzj0EAwIwVDEL\n" +
            "MAkGA1UEBhMCR0IxGDAWBgNVBAoTD1NlY3RpZ28gTGltaXRlZDErMCkGA1UEAxMi\n" +
            "U2VjdGlnbyBQdWJsaWMgQ29kZSBTaWduaW5nIENBIEUzNjAeFw0yNTA2MDUwMDAw\n" +
            "MDBaFw0yODA2MDQyMzU5NTlaMFoxCzAJBgNVBAYTAkdCMRcwFQYDVQQIDA5XZXN0\n" +
            "IFlvcmtzaGlyZTEYMBYGA1UECgwPU2VjdGlnbyBMaW1pdGVkMRgwFgYDVQQDDA9T\n" +
            "ZWN0aWdvIExpbWl0ZWQwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASwXGEU01Wk\n" +
            "W/hWNYza08ZT7i0ZeZ9M1s93JYEBrZ/f1Ho1YzxtToqgIK2o+32afablPFYWlE6w\n" +
            "GyuL/TYggBpKo4IBiTCCAYUwHwYDVR0jBBgwFoAUJQ2bbdgYd7eKwzhRVuXH8rX/\n" +
            "g2owHQYDVR0OBBYEFHEcsJgcYuDOdv1raL6h83a6j9C/MA4GA1UdDwEB/wQEAwIH\n" +
            "gDAMBgNVHRMBAf8EAjAAMBMGA1UdJQQMMAoGCCsGAQUFBwMDMEoGA1UdIARDMEEw\n" +
            "NQYMKwYBBAGyMQECAQMCMCUwIwYIKwYBBQUHAgEWF2h0dHBzOi8vc2VjdGlnby5j\n" +
            "b20vQ1BTMAgGBmeBDAEEATBJBgNVHR8EQjBAMD6gPKA6hjhodHRwOi8vY3JsLnNl\n" +
            "Y3RpZ28uY29tL1NlY3RpZ29QdWJsaWNDb2RlU2lnbmluZ0NBRTM2LmNybDB5Bggr\n" +
            "BgEFBQcBAQRtMGswRAYIKwYBBQUHMAKGOGh0dHA6Ly9jcnQuc2VjdGlnby5jb20v\n" +
            "U2VjdGlnb1B1YmxpY0NvZGVTaWduaW5nQ0FFMzYuY3J0MCMGCCsGAQUFBzABhhdo\n" +
            "dHRwOi8vb2NzcC5zZWN0aWdvLmNvbTAKBggqhkjOPQQDAgNJADBGAiEAlEkiISLz\n" +
            "PdJsFmVzJ2VZ8hnnVsOBXKbqISFQvIdguJoCIQCH4T0vwxn6uVkJpMvtxiMG/rYg\n" +
            "jRFhfbxDcVee6likOw==\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator) throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID, INT_VALID},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT_REVOKED},
                ValidatePathWithParams.Status.REVOKED,
                "Thu Jun 05 10:27:19 PDT 2025", System.out);
    }
}
