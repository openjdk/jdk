/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8189131, TODO
 * @summary Interoperability tests with Let's Encrypt CA
 * @build ValidatePathWithParams
 * @run main/othervm -Djava.security.debug=certpath LetsEncryptCA OCSP
 * @run main/othervm -Djava.security.debug=certpath LetsEncryptCA CRL
 */

 /*
 * Obtain TLS test artifacts for Let's Encrypt CA from:
 *
 * Valid TLS Certificates:
 * https://valid-isrgrootx1.letsencrypt.org/
 * https://valid-isrgrootx2.letsencrypt.org/
 *
 * Revoked TLS Certificates:
 * https://revoked-isrgrootx1.letsencrypt.org/
 * https://revoked-isrgrootxr.letsencrypt.org/
 *
 * Test artifacts don't have CRLs listed and intermediate cert doesn't have OCSP.
 */
public class LetsEncryptCA {

    public static void main(String[] args) throws Exception {

        ValidatePathWithParams pathValidator = new ValidatePathWithParams(null);

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            pathValidator.enableCRLCheck();
        } else {
            // OCSP check by default
            pathValidator.enableOCSPCheck();
        }

        new ISRGRootX1().runTest(pathValidator);
        new ISRGRootX2().runTest(pathValidator);
    }
}

class ISRGRootX1 {

     // Owner: CN=R3, O=Let's Encrypt, C=US
     // Issuer: CN=ISRG Root X1, O=Internet Security Research Group, C=US
     // Serial number: 912b084acf0c18a753f6d62e25a75f5a
     // Valid from: Thu Sep 03 17:00:00 PDT 2020 until: Mon Sep 15 09:00:00 PDT 2025
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIFFjCCAv6gAwIBAgIRAJErCErPDBinU/bWLiWnX1owDQYJKoZIhvcNAQELBQAw\n" +
            "TzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh\n" +
            "cmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwHhcNMjAwOTA0MDAwMDAw\n" +
            "WhcNMjUwOTE1MTYwMDAwWjAyMQswCQYDVQQGEwJVUzEWMBQGA1UEChMNTGV0J3Mg\n" +
            "RW5jcnlwdDELMAkGA1UEAxMCUjMwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEK\n" +
            "AoIBAQC7AhUozPaglNMPEuyNVZLD+ILxmaZ6QoinXSaqtSu5xUyxr45r+XXIo9cP\n" +
            "R5QUVTVXjJ6oojkZ9YI8QqlObvU7wy7bjcCwXPNZOOftz2nwWgsbvsCUJCWH+jdx\n" +
            "sxPnHKzhm+/b5DtFUkWWqcFTzjTIUu61ru2P3mBw4qVUq7ZtDpelQDRrK9O8Zutm\n" +
            "NHz6a4uPVymZ+DAXXbpyb/uBxa3Shlg9F8fnCbvxK/eG3MHacV3URuPMrSXBiLxg\n" +
            "Z3Vms/EY96Jc5lP/Ooi2R6X/ExjqmAl3P51T+c8B5fWmcBcUr2Ok/5mzk53cU6cG\n" +
            "/kiFHaFpriV1uxPMUgP17VGhi9sVAgMBAAGjggEIMIIBBDAOBgNVHQ8BAf8EBAMC\n" +
            "AYYwHQYDVR0lBBYwFAYIKwYBBQUHAwIGCCsGAQUFBwMBMBIGA1UdEwEB/wQIMAYB\n" +
            "Af8CAQAwHQYDVR0OBBYEFBQusxe3WFbLrlAJQOYfr52LFMLGMB8GA1UdIwQYMBaA\n" +
            "FHm0WeZ7tuXkAXOACIjIGlj26ZtuMDIGCCsGAQUFBwEBBCYwJDAiBggrBgEFBQcw\n" +
            "AoYWaHR0cDovL3gxLmkubGVuY3Iub3JnLzAnBgNVHR8EIDAeMBygGqAYhhZodHRw\n" +
            "Oi8veDEuYy5sZW5jci5vcmcvMCIGA1UdIAQbMBkwCAYGZ4EMAQIBMA0GCysGAQQB\n" +
            "gt8TAQEBMA0GCSqGSIb3DQEBCwUAA4ICAQCFyk5HPqP3hUSFvNVneLKYY611TR6W\n" +
            "PTNlclQtgaDqw+34IL9fzLdwALduO/ZelN7kIJ+m74uyA+eitRY8kc607TkC53wl\n" +
            "ikfmZW4/RvTZ8M6UK+5UzhK8jCdLuMGYL6KvzXGRSgi3yLgjewQtCPkIVz6D2QQz\n" +
            "CkcheAmCJ8MqyJu5zlzyZMjAvnnAT45tRAxekrsu94sQ4egdRCnbWSDtY7kh+BIm\n" +
            "lJNXoB1lBMEKIq4QDUOXoRgffuDghje1WrG9ML+Hbisq/yFOGwXD9RiX8F6sw6W4\n" +
            "avAuvDszue5L3sz85K+EC4Y/wFVDNvZo4TYXao6Z0f+lQKc0t8DQYzk1OXVu8rp2\n" +
            "yJMC6alLbBfODALZvYH7n7do1AZls4I9d1P4jnkDrQoxB3UqQ9hVl3LEKQ73xF1O\n" +
            "yK5GhDDX8oVfGKF5u+decIsH4YaTw7mP3GFxJSqv3+0lUFJoi5Lc5da149p90Ids\n" +
            "hCExroL1+7mryIkXPeFM5TgO9r0rvZaBFOvV2z0gp35Z0+L4WPlbuEjN/lxPFin+\n" +
            "HlUjr8gRsI3qfJOQFy/9rKIJR0Y/8Omwt/8oTWgy1mdeHmmjk7j1nYsvC9JSQ6Zv\n" +
            "MldlTTKB3zhThV1+XWYp6rjd5JW1zbVWEkLNxE7GJThEUG3szgBVGP7pSWTUTsqX\n" +
            "nLRbwHOoq7hHwg==\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=valid-isrgrootx1.letsencrypt.org
    // Issuer: CN=R3, O=Let's Encrypt, C=US
    // Serial number: 46326744d1c2f3feeca7148ed59353144a6
    // Valid from: Wed Jun 02 08:00:18 PDT 2021 until: Tue Aug 31 08:00:18 PDT 2021
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIFSDCCBDCgAwIBAgISBGMmdE0cLz/uynFI7Vk1MUSmMA0GCSqGSIb3DQEBCwUA\n" +
            "MDIxCzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1MZXQncyBFbmNyeXB0MQswCQYDVQQD\n" +
            "EwJSMzAeFw0yMTA2MDIxNTAwMThaFw0yMTA4MzExNTAwMThaMCsxKTAnBgNVBAMT\n" +
            "IHZhbGlkLWlzcmdyb290eDEubGV0c2VuY3J5cHQub3JnMIIBIjANBgkqhkiG9w0B\n" +
            "AQEFAAOCAQ8AMIIBCgKCAQEAmdx7jlaUZ0MgEvqzYWXItAFxVAOmR3KF+79vU195\n" +
            "O5X54Go1+GU+eyFAeTqr6W1gC/MIrSA9LO4neJUx5AWCYaLq7IE7/YnmXTT6BB0x\n" +
            "WFN3V1OJg9bAqpcEclQp6fbQS6DjdQvUUaEvVIwPzaen6Hmtw6LuHOYOdLk4fUSm\n" +
            "zadWiyNlMm0/ts+MLHY5iQd9ypGhJED7KBDQ4d4wvyMYo/MYKOUQ+dTXcIegh7p4\n" +
            "0OVtbrkdCuGJL+cEw1IUtSNQD+MnvUIu1je7Yb6iZ6Qd3iopNLykHYZb8YemakGX\n" +
            "SDdC54yi35NU+Y+l23vycbVmRd8vK1sizhjRSE+ufmEqXQIDAQABo4ICXTCCAlkw\n" +
            "DgYDVR0PAQH/BAQDAgWgMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjAM\n" +
            "BgNVHRMBAf8EAjAAMB0GA1UdDgQWBBR300bKVFG2auzS0mO4+E57SN6QLzAfBgNV\n" +
            "HSMEGDAWgBQULrMXt1hWy65QCUDmH6+dixTCxjBVBggrBgEFBQcBAQRJMEcwIQYI\n" +
            "KwYBBQUHMAGGFWh0dHA6Ly9yMy5vLmxlbmNyLm9yZzAiBggrBgEFBQcwAoYWaHR0\n" +
            "cDovL3IzLmkubGVuY3Iub3JnLzArBgNVHREEJDAigiB2YWxpZC1pc3Jncm9vdHgx\n" +
            "LmxldHNlbmNyeXB0Lm9yZzBMBgNVHSAERTBDMAgGBmeBDAECATA3BgsrBgEEAYLf\n" +
            "EwEBATAoMCYGCCsGAQUFBwIBFhpodHRwOi8vY3BzLmxldHNlbmNyeXB0Lm9yZzCC\n" +
            "AQYGCisGAQQB1nkCBAIEgfcEgfQA8gB3APZclC/RdzAiFFQYCDCUVo7jTRMZM7/f\n" +
            "DC8gC8xO8WTjAAABec10PpUAAAQDAEgwRgIhAPDWvnP5mA0RhPa9oiTlE21Ppcez\n" +
            "eF1+wU0MeoQcjq/7AiEAsox8kMGpWXq0ZVPweTpw1So/sNOZTsSPyBUdbLwjf+MA\n" +
            "dwBvU3asMfAxGdiZAKRRFf93FRwR2QLBACkGjbIImjfZEwAAAXnNdD7rAAAEAwBI\n" +
            "MEYCIQCYBSmmb5P+DZGANyYTPHlEbmqOBkEOblkEHq5Lf+wtkQIhAO2HhwOm3wns\n" +
            "ZTsXjUCcfQA0lKBI2TKkg9tJKFs3uuKDMA0GCSqGSIb3DQEBCwUAA4IBAQBJJ47x\n" +
            "ZhKN3QRBYVROpoYDSh0a/JW7zPGRCxK5fnDY9UT8m4gEh3yhDTkycX+vo8TReK6W\n" +
            "fEYareTSTq71MYgtKDYEARm10DuL7Vdig9Tf5DpjXLHaba+wqPz24lwhiJgoKRRr\n" +
            "8by3wXPFCGSuQyDo1ZUNrAJVYKO4hPMob1ZE8z9IYW63GvzBjEla/HxoVa9iTkv+\n" +
            "31rsKzpSbMJpnQ7WcgkUPdpoDo4JElGCyf7VZHNicumipAiCmKu0Q6TRCPOXxlKE\n" +
            "/BIyDey3rXVw3wzOlxmVF6t/V3vGtbgVvN/feUe/ytyv4vLfRR4udi2XxWt3x1la\n" +
            "7R3zuWdRQhh21p1H\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=revoked-isrgrootx1.letsencrypt.org
    // Issuer: CN=R3, O=Let's Encrypt, C=US
    // Serial number: 4f1333011635d76d6356c5f1fb8a7273617
    // Valid from: Fri Jun 25 08:18:10 PDT 2021 until: Thu Sep 23 08:18:09 PDT 2021
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIFSTCCBDGgAwIBAgISBPEzMBFjXXbWNWxfH7inJzYXMA0GCSqGSIb3DQEBCwUA\n" +
            "MDIxCzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1MZXQncyBFbmNyeXB0MQswCQYDVQQD\n" +
            "EwJSMzAeFw0yMTA2MjUxNTE4MTBaFw0yMTA5MjMxNTE4MDlaMC0xKzApBgNVBAMT\n" +
            "InJldm9rZWQtaXNyZ3Jvb3R4MS5sZXRzZW5jcnlwdC5vcmcwggEiMA0GCSqGSIb3\n" +
            "DQEBAQUAA4IBDwAwggEKAoIBAQCkCp4fq7FnN5lfAWX0vhCcyC5WO9TuU6ckuYYj\n" +
            "8/wQ8GQ/FIl+vXCAmHIfIX14irQN8TISeVdMOP0C7sa73d3GSawX7qMaRhddXn7V\n" +
            "EL+4CbHQ6qit5YkakwhHz9tKbYX16wPj+inn22kJVwi8iLbhYB9WWSvv7OyiNSHv\n" +
            "nmlYUkMv8+9UhgPT4yCKF1OEI5ajUOuecjOKc+EzsT/JqPRErvBOIKn3PRn4h8UM\n" +
            "0BJDrDtZMpkvD4/lyRs3g/BLsf3DQjlEgKit0hvc72yyhiDbKd41EmBoQC5rNF7o\n" +
            "B0CnBXhDLHbC/YRunVrYGsF0h2J9hw4055BdaXbS2BJnPEFnAgMBAAGjggJcMIIC\n" +
            "WDAOBgNVHQ8BAf8EBAMCBaAwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMC\n" +
            "MAwGA1UdEwEB/wQCMAAwHQYDVR0OBBYEFJBkf3Z/ICoCTUx3JCgrBeoMyedQMB8G\n" +
            "A1UdIwQYMBaAFBQusxe3WFbLrlAJQOYfr52LFMLGMFUGCCsGAQUFBwEBBEkwRzAh\n" +
            "BggrBgEFBQcwAYYVaHR0cDovL3IzLm8ubGVuY3Iub3JnMCIGCCsGAQUFBzAChhZo\n" +
            "dHRwOi8vcjMuaS5sZW5jci5vcmcvMC0GA1UdEQQmMCSCInJldm9rZWQtaXNyZ3Jv\n" +
            "b3R4MS5sZXRzZW5jcnlwdC5vcmcwTAYDVR0gBEUwQzAIBgZngQwBAgEwNwYLKwYB\n" +
            "BAGC3xMBAQEwKDAmBggrBgEFBQcCARYaaHR0cDovL2Nwcy5sZXRzZW5jcnlwdC5v\n" +
            "cmcwggEDBgorBgEEAdZ5AgQCBIH0BIHxAO8AdQCUILwejtWNbIhzH4KLIiwN0dpN\n" +
            "XmxPlD1h204vWE2iwgAAAXpD9t6nAAAEAwBGMEQCIHwF9NcPqsovYp56lhqFkWYj\n" +
            "QCATATrLzzxgUoLDYRwgAiBBecqe5Ub32I+q9oqH1nbK/s8QadcafIL3bkrRVbFB\n" +
            "TAB2AH0+8viP/4hVaCTCwMqeUol5K8UOeAl/LmqXaJl+IvDXAAABekP23sYAAAQD\n" +
            "AEcwRQIgGli/1mmKKnZ0uxDIX7ySqAyD2C7FTf+y3py2S0Xcv4YCIQCZve3cqKZ2\n" +
            "lrEyyaMeLZA+PIxUMniHx3gDkro0sKLzOzANBgkqhkiG9w0BAQsFAAOCAQEAle42\n" +
            "p58OTusm7DAOcdK4ld+pJu2bz9F940Wrnql08rciRjGIVpp5PhMNFm9AOaptKPNY\n" +
            "h62V2GEOVaLxmvr9/8EDFcCCPAGV1DNYrG9aTKaiXk7IzO4UxKbzox4iUcuop/zB\n" +
            "uofxT8uBLmT4XYZrQXXKj1KdfJGzgeoXqBv5PPCiP3hmBQixoJnSKImnUIXWh4O8\n" +
            "kBtmgII5ug0q+jI3LvpJuv7xQsaNYFBcmFiQQ7YRt4W99GMdbYGjhzT8iBDEH7nG\n" +
            "MsqWuwB5TN5vIuw2aWxcfaqKayq7UPA4rJePWdD/5RzKlQKLQx0BA3AL+3Nnj1fT\n" +
            "NEKwCWWylIND6z/9Xw==\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator) throws Exception {
        // OCSP check by default
        // intermediate cert R3 doesn't specify OCSP responder
        ValidatePathWithParams pathValidator = new ValidatePathWithParams(new String[]{INT});
        pathValidator.enableOCSPCheck();

        // Validate valid
        pathValidator.validate(new String[]{VALID},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED},
                ValidatePathWithParams.Status.REVOKED,
                "Fri Jun 25 09:18:12 PDT 2021", System.out);
    }
}

class ISRGRootX2 {

     // Owner: CN=E1, O=Let's Encrypt, C=US
     // Issuer: CN=ISRG Root X2, O=Internet Security Research Group, C=US
     // Serial number: b3bddff8a7845bbce903a04135b34a45
     // Valid from: Fri Sep 04 00:00:00 GMT 2020 until: Sat Sep 15 16:00:00 GMT 2025

    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIICxjCCAk2gAwIBAgIRALO93/inhFu86QOgQTWzSkUwCgYIKoZIzj0EAwMwTzEL\n" +
            "MAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2VhcmNo\n" +
            "IEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDIwHhcNMjAwOTA0MDAwMDAwWhcN\n" +
            "MjUwOTE1MTYwMDAwWjAyMQswCQYDVQQGEwJVUzEWMBQGA1UEChMNTGV0J3MgRW5j\n" +
            "cnlwdDELMAkGA1UEAxMCRTEwdjAQBgcqhkjOPQIBBgUrgQQAIgNiAAQkXC2iKv0c\n" +
            "S6Zdl3MnMayyoGli72XoprDwrEuf/xwLcA/TmC9N/A8AmzfwdAVXMpcuBe8qQyWj\n" +
            "+240JxP2T35p0wKZXuskR5LBJJvmsSGPwSSB/GjMH2m6WPUZIvd0xhajggEIMIIB\n" +
            "BDAOBgNVHQ8BAf8EBAMCAYYwHQYDVR0lBBYwFAYIKwYBBQUHAwIGCCsGAQUFBwMB\n" +
            "MBIGA1UdEwEB/wQIMAYBAf8CAQAwHQYDVR0OBBYEFFrz7Sv8NsI3eblSMOpUb89V\n" +
            "yy6sMB8GA1UdIwQYMBaAFHxClq7eS0g7+pL4nozPbYupcjeVMDIGCCsGAQUFBwEB\n" +
            "BCYwJDAiBggrBgEFBQcwAoYWaHR0cDovL3gyLmkubGVuY3Iub3JnLzAnBgNVHR8E\n" +
            "IDAeMBygGqAYhhZodHRwOi8veDIuYy5sZW5jci5vcmcvMCIGA1UdIAQbMBkwCAYG\n" +
            "Z4EMAQIBMA0GCysGAQQBgt8TAQEBMAoGCCqGSM49BAMDA2cAMGQCMHt01VITjWH+\n" +
            "Dbo/AwCd89eYhNlXLr3pD5xcSAQh8suzYHKOl9YST8pE9kLJ03uGqQIwWrGxtO3q\n" +
            "YJkgsTgDyj2gJrjubi1K9sZmHzOa25JK1fUpE8ZwYii6I4zPPS/Lgul/\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=valid-isrgrootx2.letsencrypt.org
    // Issuer: CN=E1, O=Let's Encrypt, C=US
    // Serial number: 043209568cac73a998e59451c38532062592
    // Valid from: Wed Dec 14 15:00:04 GMT 2022 until: Tue Mar 14 15:00:03 2023 GMT 2023
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIID2TCCA2CgAwIBAgISBDIJVoysc6mY5ZRRw4UyBiWSMAoGCCqGSM49BAMDMDIx\n" +
            "CzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1MZXQncyBFbmNyeXB0MQswCQYDVQQDEwJF\n" +
            "MTAeFw0yMjEyMTQxNTAwMDRaFw0yMzAzMTQxNTAwMDNaMCsxKTAnBgNVBAMTIHZh\n" +
            "bGlkLWlzcmdyb290eDIubGV0c2VuY3J5cHQub3JnMFkwEwYHKoZIzj0CAQYIKoZI\n" +
            "zj0DAQcDQgAEpQ/jWiT4wFE2HfhvU80bDq8jb+6oJ265/i/PwpBE8gcYWSLeDNYj\n" +
            "5pvgZ7bn2+5paWWwv0zeTjAZJYgXZkbgjKOCAlswggJXMA4GA1UdDwEB/wQEAwIH\n" +
            "gDAdBgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwDAYDVR0TAQH/BAIwADAd\n" +
            "BgNVHQ4EFgQUNSUfoputqDlTzUMAxsCj20JTV1owHwYDVR0jBBgwFoAUWvPtK/w2\n" +
            "wjd5uVIw6lRvz1XLLqwwVQYIKwYBBQUHAQEESTBHMCEGCCsGAQUFBzABhhVodHRw\n" +
            "Oi8vZTEuby5sZW5jci5vcmcwIgYIKwYBBQUHMAKGFmh0dHA6Ly9lMS5pLmxlbmNy\n" +
            "Lm9yZy8wKwYDVR0RBCQwIoIgdmFsaWQtaXNyZ3Jvb3R4Mi5sZXRzZW5jcnlwdC5v\n" +
            "cmcwTAYDVR0gBEUwQzAIBgZngQwBAgEwNwYLKwYBBAGC3xMBAQEwKDAmBggrBgEF\n" +
            "BQcCARYaaHR0cDovL2Nwcy5sZXRzZW5jcnlwdC5vcmcwggEEBgorBgEEAdZ5AgQC\n" +
            "BIH1BIHyAPAAdgC3Pvsk35xNunXyOcW6WPRsXfxCz3qfNcSeHQmBJe20mQAAAYUR\n" +
            "XUijAAAEAwBHMEUCIAyojH4ZvjaCw9P0APu/CfLa/HdX6PtXyCUGyR2RfnCxAiEA\n" +
            "nqLcdimOIJBveu0XbGgijfEvYdQCUt1O7usL6t0WEz4AdgB6MoxU2LcttiDqOOBS\n" +
            "HumEFnAyE4VNO9IrwTpXo1LrUgAAAYURXUqgAAAEAwBHMEUCIQDiafDARb0UCSwd\n" +
            "S1ke4vLauKePZBvr4MlqIm2NXMrgYgIgF9/mEtQJsOGJ6YTXhpB1vt+EWvouy11d\n" +
            "Haair5fLerEwCgYIKoZIzj0EAwMDZwAwZAIwI/hh4sH57CUIZ8lTTPG14+9G3rYB\n" +
            "5c0okACKIWKK5CIwgzvvSxL5v5P/6fdkPOgqAjBGADP/ZDYytoRrrLcqtYQeWoWy\n" +
            "Sxt5QKP4bDWeAhOCrPY77rRy3optar0B58jaSMw=\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=revoked-isrgrootx2.letsencrypt.org
    // Issuer: CN=E1, O=Let's Encrypt, C=US
    // Serial number: 03286d84fa714eaac4222705ebc970ac82e0
    // Valid from: Wed Dec  7 15:00:14 GMT 2022 until: Tue Mar  7 15:00:13 GMT 2023
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIFSTCCBDGgAwIBAgISBPEzMBFjXXbWNWxfH7inJzYXMA0GCSqGSIb3DQEBCwUA\n" +
            "MIID3jCCA2WgAwIBAgISAyhthPpxTqrEIicF68lwrILgMAoGCCqGSM49BAMDMDIx\n" +
            "CzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1MZXQncyBFbmNyeXB0MQswCQYDVQQDEwJF\n" +
            "MTAeFw0yMjEyMDcxNTAwMTRaFw0yMzAzMDcxNTAwMTNaMC0xKzApBgNVBAMTInJl\n" +
            "dm9rZWQtaXNyZ3Jvb3R4Mi5sZXRzZW5jcnlwdC5vcmcwWTATBgcqhkjOPQIBBggq\n" +
            "hkjOPQMBBwNCAARCT9NAZIsyB2/xJCbEyRaMMEXOIqKzzA1dA7T31tT00JyDQlBX\n" +
            "RwN+LhYBthZhsvK+IBHDmER3A3kKsEUwBGTho4ICXjCCAlowDgYDVR0PAQH/BAQD\n" +
            "AgeAMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjAMBgNVHRMBAf8EAjAA\n" +
            "MB0GA1UdDgQWBBQWKgCNNMTKbgEwFkq66irh3rD/0DAfBgNVHSMEGDAWgBRa8+0r\n" +
            "/DbCN3m5UjDqVG/PVcsurDBVBggrBgEFBQcBAQRJMEcwIQYIKwYBBQUHMAGGFWh0\n" +
            "dHA6Ly9lMS5vLmxlbmNyLm9yZzAiBggrBgEFBQcwAoYWaHR0cDovL2UxLmkubGVu\n" +
            "Y3Iub3JnLzAtBgNVHREEJjAkgiJyZXZva2VkLWlzcmdyb290eDIubGV0c2VuY3J5\n" +
            "cHQub3JnMEwGA1UdIARFMEMwCAYGZ4EMAQIBMDcGCysGAQQBgt8TAQEBMCgwJgYI\n" +
            "KwYBBQUHAgEWGmh0dHA6Ly9jcHMubGV0c2VuY3J5cHQub3JnMIIBBQYKKwYBBAHW\n" +
            "eQIEAgSB9gSB8wDxAHYAtz77JN+cTbp18jnFulj0bF38Qs96nzXEnh0JgSXttJkA\n" +
            "AAGE7VDuewAABAMARzBFAiEAzwS1E983BFGtYmZDUT8YB0B8S/Av+8HgHLWr4EXz\n" +
            "5BICIE8bhEluUjpsXaRKy/Jg17Ecn5IjnJ5efue8UWPEirU7AHcAejKMVNi3LbYg\n" +
            "6jjgUh7phBZwMhOFTTvSK8E6V6NS61IAAAGE7VDupAAABAMASDBGAiEAhSYqVOMl\n" +
            "KH28Xg2jjByZTL6qkN+P4Vw9qGJzgcylTYECIQDTzg5Hva/V/E2GJGc6UvysmS4k\n" +
            "BbOQ0vYEFaCj8JN7iDAKBggqhkjOPQQDAwNnADBkAjBF/g4C6YLPA1Ubupz1R5/p\n" +
            "IKqF05Fy+7Yhk8x3FrtFnKC3Q/E+K8Ea/Ry1mzqL8kACMCSlQJPw46mP9H24qSKS\n" +
            "O+hISc4qbvppKUOJO2Jrev2JqqMFkJ93IIT/Ysvnqy6Sqw==\n" +
            "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator) throws Exception {
        // OCSP check by default
        // intermediate cert R3 doesn't specify OCSP responder
        ValidatePathWithParams pathValidator = new ValidatePathWithParams(new String[]{INT});
        pathValidator.enableOCSPCheck();

        // Validate valid
        pathValidator.validate(new String[]{VALID},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED},
                ValidatePathWithParams.Status.REVOKED,
                "Fri Jun 25 09:18:12 PDT 2021", System.out);
    }
}
