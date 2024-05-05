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
 * @bug 8319187
 * @summary Interoperability tests with eMudhra emSign Root CA G2 CS root
 * @build ValidatePathWithParams
 * @run main/othervm -Djava.security.debug=certpath EmSignRootG2CA OCSP
 * @run main/othervm -Djava.security.debug=certpath EmSignRootG2CA CRL
 */

public class EmSignRootG2CA {

    // Owner: CN=emSign CS CA - G2, O=eMudhra Technologies Limited, OU=emSign PKI, C=IN
    // Issuer: CN=emSign Root CA - G2, O=eMudhra Technologies Limited, OU=emSign PKI, C=IN
    // Serial number: c084e666596139a1fa9b
    // Valid from: Sun Feb 18 10:30:00 PST 2018 until: Fri Feb 18 10:30:00 PST 2033
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGeDCCBGCgAwIBAgILAMCE5mZZYTmh+pswDQYJKoZIhvcNAQEMBQAwZzELMAkG\n" +
            "A1UEBhMCSU4xEzARBgNVBAsTCmVtU2lnbiBQS0kxJTAjBgNVBAoTHGVNdWRocmEg\n" +
            "VGVjaG5vbG9naWVzIExpbWl0ZWQxHDAaBgNVBAMTE2VtU2lnbiBSb290IENBIC0g\n" +
            "RzIwHhcNMTgwMjE4MTgzMDAwWhcNMzMwMjE4MTgzMDAwWjBlMQswCQYDVQQGEwJJ\n" +
            "TjETMBEGA1UECxMKZW1TaWduIFBLSTElMCMGA1UEChMcZU11ZGhyYSBUZWNobm9s\n" +
            "b2dpZXMgTGltaXRlZDEaMBgGA1UEAxMRZW1TaWduIENTIENBIC0gRzIwggIiMA0G\n" +
            "CSqGSIb3DQEBAQUAA4ICDwAwggIKAoICAQDYYkv6Q9an5RylOJ6rkTAHT0cAwfYg\n" +
            "ZsFKk/Hz/4VwWYsmzf+Z7M8i3CK3mnUcqgw0AIzrVLUwxiKAaL0qca+SbXwOk/7p\n" +
            "Y/zwwLdg0OhHVGeeU3OTvkbsBpiLS08i7ids9FGrte6m1kqk+QSOY2F5AESxA4+F\n" +
            "AKXGtzIImQd15m67C88AzzFsvszAAxSvVTqs4hb8BcRnUCzlAp7gMJSwwrrgTiEv\n" +
            "6Ap6cFVT+n1oj6370sd5KBiRelLoqZtQx4njoNJkJlM30ftPNMGnqPLCloQ6koP/\n" +
            "dAdpmwWB+F0/5d5UVmVPC3R/F8w7aX3fdSC8+M2E/ZXPVIYkEquLT7K2yXhRl3hn\n" +
            "xwG6qqGp6TjvKvhiyac8qieu9YNG1R+PVFqejOFMohV2g0Z5MfwaruhUCNwHHeZs\n" +
            "Dv/MVYMiHcV+5qU+MMzcKngb3RCmq0jzCb+MESomEMiAieCC15W7YC/LpgDHO0jY\n" +
            "vV4AdLquUHfsOnhT2KD7mEg2PnL7JOwoQSFtuJYmM/coh+Y6CIoV3x+aV1bO7FDF\n" +
            "ap33u36lE639oQj0tTqW3n1WcyNxhD0lwGlYIAjG8XnhRjtl6/MVVrGuyPWpB4TH\n" +
            "u8CgNT0roENuq13RnHbBz2rLnndenHiMbxCyElGJBpZfXiF1H25KHUzvyzxt++L+\n" +
            "hSfprX9BSXLpGQIDAQABo4IBJTCCASEwHwYDVR0jBBgwFoAU7exNRWEYKOezIygR\n" +
            "HE2lJw1e7PQwHQYDVR0OBBYEFBWGyrZ0lhdIWDSCLM3S4XWer0S3MA4GA1UdDwEB\n" +
            "/wQEAwIBBjATBgNVHSUEDDAKBggrBgEFBQcDAzA9BgNVHSAENjA0MDIGBFUdIAAw\n" +
            "KjAoBggrBgEFBQcCARYcaHR0cDovL3JlcG9zaXRvcnkuZW1zaWduLmNvbTASBgNV\n" +
            "HRMBAf8ECDAGAQH/AgEAMDIGCCsGAQUFBwEBBCYwJDAiBggrBgEFBQcwAYYWaHR0\n" +
            "cDovL29jc3AuZW1zaWduLmNvbTAzBgNVHR8ELDAqMCigJqAkhiJodHRwOi8vY3Js\n" +
            "LmVtc2lnbi5jb20/Um9vdENBRzIuY3JsMA0GCSqGSIb3DQEBDAUAA4ICAQCDkogs\n" +
            "d5Tv1zwsQdk15btzYK/oI1tEwvN6IpIM9rSqIrje8XnXKjHHmbHX6emHIR31bxuK\n" +
            "7mY77XjrJMWp+71udC/DgDy4tfZTXIzEekI0XQfcui1UPC08Ysl0taQKTANwsAOV\n" +
            "VSi7boSGqLet0qSmeKVyQ5/blbwx1NhjyLTyi66rVYf7fYdPV55X5TKUJdKDgiRI\n" +
            "BomNVRcrrnHZtS8+t9CXxSXR35VAu2ube44Tl+dQHIWz9XwLxtYFwIPSEdqPpoAu\n" +
            "5XEVo7evwMHQoY/MQj6Ywbw6tYh6bHu6C/qrp4oSyYXbz2ZWlHkz1oEXvefi7a9Z\n" +
            "6mKnnaY3UYHq5AI+k6ojazVFbSTenb/TO/Z247gdhG7Wssshd6pgyqcTEa+FZz+F\n" +
            "5ZZdoiIl8UJsTCPPg0xP9Ab0WE3BjCCqTPt+Czbd3cgBxiBS7KTQs/DnQRFuPCjC\n" +
            "khbDtHsCN4aUoLM9OOw94/ZcoU0G5cg9mSvONBxUv9W7SIpJreXXMPXixcBKULoJ\n" +
            "focui3s0yzGqTA9tSzQ4nmA9aXBCAAxrABlY/hk10ImeBa1SPjocRb/vuCaGp74T\n" +
            "n8oADP42XudDnp8wlOKWxFJulhNi960Rev+5vZOPF/LGfS78GI6yzBjR49VJGhOP\n" +
            "EJK8NSNmK3FNblQfOyFM7VE0uOGHOUwpMGVM2A==\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=test, OU=test, O=test, L=test, ST=test, C=IN
    // Issuer: CN=emSign CS CA - G2, O=eMudhra Technologies Limited, OU=emSign PKI, C=IN
    // Serial number: 7c9ade672c0ad1b6
    // Valid from: Wed Aug 30 05:39:25 PDT 2023 until: Sat Aug 30 05:39:25 PDT 2025
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGNjCCBB6gAwIBAgIIfJreZywK0bYwDQYJKoZIhvcNAQEMBQAwZTELMAkGA1UE\n" +
            "BhMCSU4xEzARBgNVBAsTCmVtU2lnbiBQS0kxJTAjBgNVBAoTHGVNdWRocmEgVGVj\n" +
            "aG5vbG9naWVzIExpbWl0ZWQxGjAYBgNVBAMTEWVtU2lnbiBDUyBDQSAtIEcyMB4X\n" +
            "DTIzMDgzMDEyMzkyNVoXDTI1MDgzMDEyMzkyNVowWDELMAkGA1UEBhMCSU4xDTAL\n" +
            "BgNVBAgTBHRlc3QxDTALBgNVBAcTBHRlc3QxDTALBgNVBAoMBHRlc3QxDTALBgNV\n" +
            "BAsTBHRlc3QxDTALBgNVBAMTBHRlc3QwggGiMA0GCSqGSIb3DQEBAQUAA4IBjwAw\n" +
            "ggGKAoIBgQC04pOiSFbl7Bd4wFYXzzyukKh+EmwIq8xRGQDkuYH+C6Zao36VAV+k\n" +
            "xGw7lmM3rf4YUcArgZYHfrxgPJNBbGrCi/YnEPYQTNwSrBAePUx1tt13LVBxHfNu\n" +
            "cQQT+kqE7064WsYfmfr/uzJZemqVH7lG82DN23+8E/235AIh3lz/pn7T9ByLj7TV\n" +
            "zWP40oT0UfQXQvWUpFevPONu/RksRP+NiKV3ji6/wYpvrfodzkrGxw2DPfOh4Iam\n" +
            "j6bBH2rkTMToH853plsQGr2ji8OndePfvDdk+5c33Jz1knCNPZSlYQIIp8scyz4z\n" +
            "jaUGdoC140FjEA1SMA2WzpRJoE7xjAidLv7jiV596/bTwrIM+IZhzBc8SKRmkdZ6\n" +
            "lYjPYJHPqRosRtfxcQne3pY6F4s1aOUtuGJaQS/AJkkykZoOx27plWM5SjtmlrL+\n" +
            "7g2/ihWT9CEagYuo44tqk9Tmp3P37+ADAmiXxP0zUxYIv77DSabdArrZ+AB5XUol\n" +
            "V8sxE1V6h0UCAwEAAaOCAXUwggFxMB8GA1UdIwQYMBaAFBWGyrZ0lhdIWDSCLM3S\n" +
            "4XWer0S3MB0GA1UdDgQWBBQ2k0TE2p46sYwI5M/a1XJ8M5Oc8DAOBgNVHQ8BAf8E\n" +
            "BAMCB4AwEwYDVR0lBAwwCgYIKwYBBQUHAwMwNwYDVR0fBDAwLjAsoCqgKIYmaHR0\n" +
            "cDovL2NybC5lbXNpZ24uY29tP2VtU2lnbkNTQ0FHMi5jcmwwTgYDVR0gBEcwRTA5\n" +
            "BgsrBgEEAYOOIQEAATAqMCgGCCsGAQUFBwIBFhxodHRwOi8vcmVwb3NpdG9yeS5l\n" +
            "bVNpZ24uY29tMAgGBmeBDAEEATBzBggrBgEFBQcBAQRnMGUwIgYIKwYBBQUHMAGG\n" +
            "Fmh0dHA6Ly9vY3NwLmVtU2lnbi5jb20wPwYIKwYBBQUHMAKGM2h0dHA6Ly9yZXBv\n" +
            "c2l0b3J5LmVtc2lnbi5jb20vY2VydHMvZW1TaWduQ1NDQUcyLmNydDAMBgNVHRMB\n" +
            "Af8EAjAAMA0GCSqGSIb3DQEBDAUAA4ICAQBKLa7j8fNpcnWNv7NegrMKTRy7gycI\n" +
            "qrMK848wISX6jl2wg6b275sWQHzQRxA6rbB76bF2HXLFcpITJPaz+vjetYOVQd4v\n" +
            "l8iZN52OpN6Pwrheiz7JhdLiHisN+2NKMmF899bH7w1l2Sr/FQl5vqk41gwwWMen\n" +
            "99Waf4Bp6p3lvBArK2BbabTs8+16xvmkHEK3d3l3Bu6qTEbQRgUI5XsVXmXXn8Pg\n" +
            "IANliTEsbsN9CMWrJ56ciEujU7w2L+IBfvKhl10N1AQNHwpQzwfFyz2BUbACN75o\n" +
            "feIUBarM3ssNzpnt7idgkCTwWVrdEL1NHyW967aEMWyVwaRrtkjFOW/0xuSr2rEI\n" +
            "jBpPj5RPdP6ZEaqnmg5PIgSrJ8FBjx6JmvVgZH/XEl5MZ7PsvJFfIMun6RxXtGn7\n" +
            "QP0+ipkRrI6USNFS84H53Q0WJhQWZUgd3cdm37wpFGvxOVEskIgJNW9SbOgiT9sB\n" +
            "zTIy3ceOK2onmUkDM2Q2+Hbc7A4BmNIlW4fpYXvZlM7IXSl9U3Voks92Hi45azgz\n" +
            "StWZv9+Ronmmp+b7JKCe7MZXIBHfj0JhAVNJiYTZ9BqkY2VRvuQPVUdKxske9fQ6\n" +
            "ciFJ5a6RDOhce6pFloaQu39ci2XCY1N4mIR3vFzpmBNkttlEXviK07XNTv9cnQt6\n" +
            "3CW5aMAsfTbmOw==\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=test, OU=test, O=test, L=test, ST=test, C=IN
    // Issuer: CN=emSign CS CA - G2, O=eMudhra Technologies Limited, OU=emSign PKI, C=IN
    // Serial number: cf02dedd03d2f509
    // Valid from: Thu Oct 05 22:38:51 PDT 2023 until: Sun Oct 05 22:38:51 PDT 2025
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIGNzCCBB+gAwIBAgIJAM8C3t0D0vUJMA0GCSqGSIb3DQEBDAUAMGUxCzAJBgNV\n" +
            "BAYTAklOMRMwEQYDVQQLEwplbVNpZ24gUEtJMSUwIwYDVQQKExxlTXVkaHJhIFRl\n" +
            "Y2hub2xvZ2llcyBMaW1pdGVkMRowGAYDVQQDExFlbVNpZ24gQ1MgQ0EgLSBHMjAe\n" +
            "Fw0yMzEwMDYwNTM4NTFaFw0yNTEwMDYwNTM4NTFaMFgxCzAJBgNVBAYTAklOMQ0w\n" +
            "CwYDVQQIEwR0ZXN0MQ0wCwYDVQQHEwR0ZXN0MQ0wCwYDVQQKDAR0ZXN0MQ0wCwYD\n" +
            "VQQLEwR0ZXN0MQ0wCwYDVQQDEwR0ZXN0MIIBojANBgkqhkiG9w0BAQEFAAOCAY8A\n" +
            "MIIBigKCAYEAmUSghjvjUvVgYguH2PMLwW4TwtYsNDpAuGPqux53lI9v9S5u4oAv\n" +
            "m1Sa3MW7CeEnhHNAIFu/AKvNXSfkvnJpTozWstZMjd93DcNacteBG0fBKTkIq+5k\n" +
            "A8qIBiXWk8NORlbjV5bXnoW2pO7wbrALDK3FGf2JAQjuYWXE1mlVk0+SJewUSN+F\n" +
            "XTl63V3tcaqjxhoViY8/dCWc7pNTPgQ/f+Rmnm1bpE0hxVPpQ29+60lyoNtKiOWj\n" +
            "InKRKBV8jYkR/xI13bKWguaxZnswpf2MrophQTvO9ivPHADWhZlNYYjYYEMl4tbi\n" +
            "rG2EquJ7g8Jdo+aL3BggLv5gFkpfoEcaveNuUWy7ggUl7MNhvgDdWdoi6VY7R8Fi\n" +
            "F52+JqPByGpHkZKi0wPa3BaI7guGGyCn3TMe66kNTMS4ADxHktqQlpNSaYYl/84G\n" +
            "lnr2WxQt/W+sXoorlKc/Kh0ubbm6eDzPE8kkIDV2uIxUEgSL7SJQ95yf5XgRihoH\n" +
            "KoBA45iR5vCtAgMBAAGjggF1MIIBcTAfBgNVHSMEGDAWgBQVhsq2dJYXSFg0gizN\n" +
            "0uF1nq9EtzAdBgNVHQ4EFgQUDs5dk74eElzdEKdxIlkzISoWSFkwDgYDVR0PAQH/\n" +
            "BAQDAgeAMBMGA1UdJQQMMAoGCCsGAQUFBwMDMDcGA1UdHwQwMC4wLKAqoCiGJmh0\n" +
            "dHA6Ly9jcmwuZW1zaWduLmNvbT9lbVNpZ25DU0NBRzIuY3JsME4GA1UdIARHMEUw\n" +
            "OQYLKwYBBAGDjiEBAAEwKjAoBggrBgEFBQcCARYcaHR0cDovL3JlcG9zaXRvcnku\n" +
            "ZW1TaWduLmNvbTAIBgZngQwBBAEwcwYIKwYBBQUHAQEEZzBlMCIGCCsGAQUFBzAB\n" +
            "hhZodHRwOi8vb2NzcC5lbVNpZ24uY29tMD8GCCsGAQUFBzAChjNodHRwOi8vcmVw\n" +
            "b3NpdG9yeS5lbXNpZ24uY29tL2NlcnRzL2VtU2lnbkNTQ0FHMi5jcnQwDAYDVR0T\n" +
            "AQH/BAIwADANBgkqhkiG9w0BAQwFAAOCAgEAGa2XSoRkoIkHHHGXrdzTBCf/+KgK\n" +
            "FlHhqlBOk5rwLDX1sfNlmsaz10I69phE90Ac8Coa/xCrBaFrTYqRvmkY9gU19jkn\n" +
            "FdVcwQEHNku7Ro/Z/mbyi+aTBzHMTy0Vl4HqVnQInjV891n64SerUuAB7wNVOOho\n" +
            "GoBfpf6lzDzzuEmetFokHYv1tWGQqPF/dHLARQraUlQpWjsnOx0QcZ5cM79REONE\n" +
            "y6uzXT2vaatT3ns8Mtx8zooq+t8pnZlXJqlrwNTcnPad9gSsVu6vfsnWhLhz0VLG\n" +
            "sYPXcWIssLbBQW3v5z0l1Isj7vy2UFfbn8AmZ0PanPo3v3C2sk19DK+Zlc9xBAXc\n" +
            "KKwc4m8le6QkP/EB2wUA7ey5Cf29hjNDJpZznquEaWl9aKbBRdJDKsK88IBJjzK0\n" +
            "Gbpw9fYJ3txuGA7Q27gyaZAeGAIrFvOtRY0XFbr20qSh2GBBYN57+lBPh4UKqgy8\n" +
            "Z2Kk/2jK9k+nm41JYCmwVZHg3Va9RRfW8FkeE95gAUFPDWjeV+GvcimCbcB3DwaZ\n" +
            "9fy1qfV4xsduhC3ei6f7Ask8LbAEWaEIXmgK10YbIfhzomCyCzlA+E+gwkq/bmkv\n" +
            "B8hh27KWA6IRt7URI51MZlh0e8fULyXlOZcoJA/IPX9RdePa2RHFuPSypBHjoN7z\n" +
            "6bCML1XZ2xnHIAg=\n" +
            "-----END CERTIFICATE-----";

    public static void main(String[] args) throws Exception {

        ValidatePathWithParams pathValidator = new ValidatePathWithParams(null);

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            pathValidator.enableCRLCheck();
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
                "Thu Oct 05 22:51:36 PDT 2023", System.out);
    }
}
