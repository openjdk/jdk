/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8319187 8385585
 * @summary Interoperability tests with eMudhra emSign Root CA G2 CS root
 * @build ValidatePathWithParams
 * @run main/othervm/manual -Djava.security.debug=certpath EmSignRootG2CA OCSP
 * @run main/othervm/manual -Djava.security.debug=certpath EmSignRootG2CA CRL
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

    // Owner: CN=eMudhra Technologies Limited, O=eMudhra Technologies Limited,
    // L=Bangalore, ST=Karnataka, C=IN
    // Issuer: CN=emSign CS CA - G2, O=eMudhra Technologies Limited,
    // OU=emSign PKI, C=IN
    // Serial number: e66c2d81f1cf97f024897e285626ad
    // Valid from: Fri Sep 04 00:59:47 PDT 2026 until: Sat Sep 04 00:59:47
    // PDT 2027
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIG6zCCBNOgAwIBAgIQAOZsLYHxz5fwJIl+KFYmrTANBgkqhkiG9w0BAQwFADBl\n" +
            "MQswCQYDVQQGEwJJTjETMBEGA1UECxMKZW1TaWduIFBLSTElMCMGA1UEChMcZU11\n" +
            "ZGhyYSBUZWNobm9sb2dpZXMgTGltaXRlZDEaMBgGA1UEAxMRZW1TaWduIENTIENB\n" +
            "IC0gRzIwHhcNMjYwOTA0MDc1OTQ3WhcNMjcwOTA0MDc1OTQ3WjCBgzELMAkGA1UE\n" +
            "BhMCSU4xEjAQBgNVBAgTCUthcm5hdGFrYTESMBAGA1UEBxMJQmFuZ2Fsb3JlMSUw\n" +
            "IwYDVQQKDBxlTXVkaHJhIFRlY2hub2xvZ2llcyBMaW1pdGVkMSUwIwYDVQQDExxl\n" +
            "TXVkaHJhIFRlY2hub2xvZ2llcyBMaW1pdGVkMIICIjANBgkqhkiG9w0BAQEFAAOC\n" +
            "Ag8AMIICCgKCAgEA5Mh6duhdJ4xfZw3Iwnvy7KIauGVXCpX/EOS3ueDOKPCQ8XG+\n" +
            "pWHP+bs1OioHxWwRAo0AVV/hYr9c+nAyAq0Y0RsZGeBDqZUVK0oECYdWvssPP/0o\n" +
            "ZUcmiDvDI3NDYKXE8XqPrxd6uHZ0mFkJn+GQHUrTx7gOtNCscijmkeMip9jyLq8B\n" +
            "m8c9B6TzkSXD6Q2tB76cRuHGLL7FHDzBut6GZElJicsoeI/AQtLfakz02Taea8Ue\n" +
            "ILyLCnpq5jgij2zqk6ozMDd8lpHYvJCwZ7tFHfuxQBcWy9R2qUAeXBUX+Iymq+Me\n" +
            "gpv8m3Ov1382p+iP6wpxp/LsDlr+Cg3uil19aFG2GO7WIxiIyDbSY3t6VjhZWFWP\n" +
            "YbemMXhiKCMWZmX8bKVb8wJYjxUaBDpCoIq8+gj/XglUjq7vpXN8oARLW69z1fJi\n" +
            "Yt0QAlEDLMcM0Rh3wmEr/sqtu7mkBU7Lp1nnqNx4bPXDf69SalbTnM3ukN7QYpgz\n" +
            "d/cxzACOQioTwoCDFSHuOBhLuyo81xho9UKzpMR50r7FcePmiLiAtkgl7fZ02U1C\n" +
            "DincqL+bLtpUxGcSmxNtI3NF+OqpcG+DUjUFDge7k+WgbFxvzahcxH5i5YdzIRf0\n" +
            "y3NbodvHIYmVPcH0vMv1ntywwP1jy9VuAgT24cj+Es5rmPYy8xogrZa95kkCAwEA\n" +
            "AaOCAXYwggFyMB8GA1UdIwQYMBaAFBWGyrZ0lhdIWDSCLM3S4XWer0S3MB0GA1Ud\n" +
            "DgQWBBQy1M8ziJmPioriHY7mJB7+beUk5zAOBgNVHQ8BAf8EBAMCB4AwEwYDVR0l\n" +
            "BAwwCgYIKwYBBQUHAwMwNwYDVR0fBDAwLjAsoCqgKIYmaHR0cDovL2NybC5lbXNp\n" +
            "Z24uY29tP2VtU2lnbkNTQ0FHMi5jcmwwTwYDVR0gBEgwRjA6BgwrBgEEAYOOIQEA\n" +
            "AQIwKjAoBggrBgEFBQcCARYcaHR0cDovL3JlcG9zaXRvcnkuZW1zaWduLmNvbTAI\n" +
            "BgZngQwBBAEwcwYIKwYBBQUHAQEEZzBlMCIGCCsGAQUFBzABhhZodHRwOi8vb2Nz\n" +
            "cC5lbVNpZ24uY29tMD8GCCsGAQUFBzAChjNodHRwOi8vcmVwb3NpdG9yeS5lbXNp\n" +
            "Z24uY29tL2NlcnRzL2VtU2lnbkNTQ0FHMi5jcnQwDAYDVR0TAQH/BAIwADANBgkq\n" +
            "hkiG9w0BAQwFAAOCAgEAL9uz2lWq/Dx1P9jot/DUvDS3u7wKf3qw5XH191kQ1B+S\n" +
            "m9/uhkEptH4lanpXoB/QorvTSkSvu2D2LxQOjHbXdYzLJqDO4NNbFw3S7H5jRVWT\n" +
            "dRBrb/2VREX5LJ2NBVgxETCpOMyXKgpmefcvVYxZ1jKa80H6Jqd7YzPpFhwsHglX\n" +
            "/YQX+jYUWjdxkWSo48jlAbi81vUj5snn+AbrFnq8RRy1r22Z5k3zSFNGz2W9iWzS\n" +
            "UjpSQoaz7e4/L+2flScEydmmv1F2MH9boP5XIZmNXKp6sTr55jZQmQiWysiERPJ2\n" +
            "0bHhM9Q8qr+Sp8NWVZs5tIYnWJSyBasOctkVvl2ADdrIofR9cmWaP1/EdB5Hl1cx\n" +
            "Aqo3FKfXK5ZNRHt7B7v11PoBiwzYqk45L03AABNmuZcNC3HkG5emfBrlY0Ud6QuK\n" +
            "/8Oi4+G01H/6DFCqMqEFR/AFQtzPDnlaEL3XvoZyJcIJARP14XTyXXlUJt5fI0yq\n" +
            "E6F0KsJCeCRcGKZcjLMWyN+5mPsNdT9EdQOpYJ8dXLtB5r2uN0xsjcOPpFTAIwRt\n" +
            "fUW5MUFiZZvWqqadhI1EhKhWDTyTn8vJGWNiqXKbbfedck/k/B5Kc75A5nfuBB/u\n" +
            "awVsnhMuAU7yVUOH37dGv4b4tKRnkW/L+owhEOfbWbiRrtmQ4z/u5+84yxddNiY=\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=eMudhra Technologies Limited, O=eMudhra Technologies Limited,
    // L=Bangalore, ST=Karnataka, C=IN
    // Issuer: CN=emSign CS CA - G2, O=eMudhra Technologies Limited,
    // OU=emSign PKI, C=IN
    // Serial number: 399515f0e9db1ec53b695df8a4216
    // Valid from: Sat Sep 05 10:12:31 PDT 2026 until: Sun Sep 05 10:12:31
    // PDT 2027
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIG6jCCBNKgAwIBAgIPA5lRXw6dsexTtpXfikIWMA0GCSqGSIb3DQEBDAUAMGUx\n" +
            "CzAJBgNVBAYTAklOMRMwEQYDVQQLEwplbVNpZ24gUEtJMSUwIwYDVQQKExxlTXVk\n" +
            "aHJhIFRlY2hub2xvZ2llcyBMaW1pdGVkMRowGAYDVQQDExFlbVNpZ24gQ1MgQ0Eg\n" +
            "LSBHMjAeFw0yNjA5MDUxNzEyMzFaFw0yNzA5MDUxNzEyMzFaMIGDMQswCQYDVQQG\n" +
            "EwJJTjESMBAGA1UECBMJS2FybmF0YWthMRIwEAYDVQQHEwlCYW5nYWxvcmUxJTAj\n" +
            "BgNVBAoMHGVNdWRocmEgVGVjaG5vbG9naWVzIExpbWl0ZWQxJTAjBgNVBAMTHGVN\n" +
            "dWRocmEgVGVjaG5vbG9naWVzIExpbWl0ZWQwggIiMA0GCSqGSIb3DQEBAQUAA4IC\n" +
            "DwAwggIKAoICAQDamKtV3CD/F8V6OpeyMvQZakL/CLsYZVSlSHN34u2nxtpG5RIt\n" +
            "buEMQfZl1lsLQlV39TgoCfrf7kY2S9xLZcMapUGMwT0HoP+rRnyVJpfhgq/ULlX/\n" +
            "L2KgGxJyUP6XpVcdrlyrHOAvErFhdhdg9L4/tTwOoarHUZCNBB1Wx3Rl1sgCMVbl\n" +
            "UcbzgJZulYhMJ+deEnHwJTPLO/GTFHek7hGy7bYoD2jhwUgG6Yjae5rZWHnLaKRg\n" +
            "zACpgQl2sUAtJ7l8Zh0Ig14BTzM0OTfQutPaB3JYr81wZj4awhk39MDf4ob6kcEm\n" +
            "8KqqYqlARhhFe8XOCZ5IrmQEzie2am0EZICk6g2nBvIcMUgPN9yFOGQdUaSSQe5+\n" +
            "lA6skGA/ZADVCBf9E1j/tqABdcFdYiPvpInlRxXCK/xxbspupQhffyamqUtj7PT+\n" +
            "tIdAaruiFV/PIx6MvmNpBhzyPt01QJlAy5GU//5/EnQ7HW/np9jjHr0QdZFbnis+\n" +
            "O+VloqNSP7UW9eWo1mMwQWLGr/PQHNERGGsPb6gofxUJdKgp4kbg9rqfBkLdYLBV\n" +
            "jVYAKIpOlP7b93eta5qr0DOImBLor5fvZOs7bljltp68S1mmVxMsvezmSJIcvLvG\n" +
            "R0G4YWDvobhcCVmEk1nV580+/3OZEMbBk3R4oOB2MUv4eiCYrlpWI0xmjQIDAQAB\n" +
            "o4IBdjCCAXIwHwYDVR0jBBgwFoAUFYbKtnSWF0hYNIIszdLhdZ6vRLcwHQYDVR0O\n" +
            "BBYEFMQn0uMfp84zx4vz2EYeBnzyyh9fMA4GA1UdDwEB/wQEAwIHgDATBgNVHSUE\n" +
            "DDAKBggrBgEFBQcDAzA3BgNVHR8EMDAuMCygKqAohiZodHRwOi8vY3JsLmVtc2ln\n" +
            "bi5jb20/ZW1TaWduQ1NDQUcyLmNybDBPBgNVHSAESDBGMDoGDCsGAQQBg44hAQAB\n" +
            "AjAqMCgGCCsGAQUFBwIBFhxodHRwOi8vcmVwb3NpdG9yeS5lbXNpZ24uY29tMAgG\n" +
            "BmeBDAEEATBzBggrBgEFBQcBAQRnMGUwIgYIKwYBBQUHMAGGFmh0dHA6Ly9vY3Nw\n" +
            "LmVtU2lnbi5jb20wPwYIKwYBBQUHMAKGM2h0dHA6Ly9yZXBvc2l0b3J5LmVtc2ln\n" +
            "bi5jb20vY2VydHMvZW1TaWduQ1NDQUcyLmNydDAMBgNVHRMBAf8EAjAAMA0GCSqG\n" +
            "SIb3DQEBDAUAA4ICAQBy0UYUXy/AwPgRmUWu1B0xeq9hU2vFwoFmGbOkd2sJqOOi\n" +
            "KdIbpjvIdLtPchIasENu+oc55ENgmbD4u5WDxGspBjL18sbE6jmzmGY1dsbCgF17\n" +
            "Pi9KvlX3LRtD8qTD2oJ5y/YJ7fMKW4j+h4ssNrmA2NboXhgX3e+idQeZ2ItAgV0k\n" +
            "s3STGRkoOKdsuwpeD/+69nXVIIYusVSgOuYXZb/ViWcL3cMq75cZdR/WYixxU9ZV\n" +
            "Z55NQKikOGVTuKBpDv9BEa5yqAK32RcajUl7R7bT7AuY+LnTRHtX0P4sCkohj1kP\n" +
            "9JGoFWN1NIJeCou5GSRmuWLYpnMfi9oDvMPt2e7GUqKkNF+OXTxWfq5A0xtkLWGN\n" +
            "3qEr9xD34ZnFP5h0c6sE8hzee65erhqSo6LqlQMGINP2gL+fUG+smdX0JQYbX9hP\n" +
            "raKmp53+aHwolzn0R3t9otgZ6yVdAZLScNLXqDd55/gr/TDrfu4XzRTHK9RCrMzT\n" +
            "trvbKKtqTuwEp1vQBoced60CI1ZjG41tipF1irhU+pyHk/Kt9YiExp6QZGoBznTY\n" +
            "9Rvw5No0XoVQHLC56JbsGUot8bVVpGNXzF8PtWBmskqttsw1XTgSUOsnoopfb+Eb\n" +
            "fpPUdqTDs47FRowITPbj4cgYzYRKWEc7xh8fozZ18lN8yAP6hA2OfDN3j4HrCA==\n" +
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
                "Sat Sep 05 10:32:30 PDT 2026", System.out);
    }
}
