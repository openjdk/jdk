/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8189131
 * @summary Interoperability tests with Buypass Class 2 and Class 3 CA
 * @build ValidatePathWithParams
 * @run main/othervm/timeout=180 -Djava.security.debug=certpath BuypassCA OCSP
 * @run main/othervm/timeout=180 -Djava.security.debug=certpath BuypassCA CRL
 */

 /*
 * Obtain test artifacts for Buypass Class 2 and Class 3 CAs from:
 *  Class 2:
 *      https://valid.domainplus.ca22.ssl.buypass.no/CA2Class2   (valid)
 *      https://revoked.domainplus.ca22.ssl.buypass.no        (revoked)
 *
 *  Class3:
 *      https://valid.business.ca23.ssl.buypass.no    (valid)
 *      https://revoked.business.ca23.ssl.buypass.no    (revoked)
 */
public class BuypassCA {

    public static void main(String[] args) throws Exception {

        ValidatePathWithParams pathValidator = new ValidatePathWithParams(null);

        boolean ocspEnabled = true;

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            pathValidator.enableCRLCheck();
            ocspEnabled = false;
        } else {
            // OCSP check by default
            pathValidator.enableOCSPCheck();
        }

        new BuypassClass2().runTest(pathValidator);
        new BuypassClass3().runTest(pathValidator, ocspEnabled);
    }
}

class BuypassClass2 {

    // Owner: CN=Buypass Class 2 CA 2, O=Buypass AS-983163327, C=NO
    // Issuer: CN=Buypass Class 2 Root CA, O=Buypass AS-983163327, C=NO
    private static final String INT_CLASS_2 = "-----BEGIN CERTIFICATE-----\n"
            + "MIIFCzCCAvOgAwIBAgIBGDANBgkqhkiG9w0BAQsFADBOMQswCQYDVQQGEwJOTzEd\n"
            + "MBsGA1UECgwUQnV5cGFzcyBBUy05ODMxNjMzMjcxIDAeBgNVBAMMF0J1eXBhc3Mg\n"
            + "Q2xhc3MgMiBSb290IENBMB4XDTEwMTAyNjEwMTYxN1oXDTMwMTAyNjEwMTYxN1ow\n"
            + "SzELMAkGA1UEBhMCTk8xHTAbBgNVBAoMFEJ1eXBhc3MgQVMtOTgzMTYzMzI3MR0w\n"
            + "GwYDVQQDDBRCdXlwYXNzIENsYXNzIDIgQ0EgMjCCASIwDQYJKoZIhvcNAQEBBQAD\n"
            + "ggEPADCCAQoCggEBAJyrZ8aWSw0PkdLsyswzK/Ny/A5/uU6EqQ99c6omDMpI+yNo\n"
            + "HjUO42ryrATs4YHla+xj+MieWyvz9HYaCnrGL0CE4oX8M7WzD+g8h6tUCS0AakJx\n"
            + "dC5PBocUkjQGZ5ZAoF92ms6C99qfQXhHx7lBP/AZT8sCWP0chOf9/cNxCplspYVJ\n"
            + "HkQjKN3VGa+JISavCcBqf33ihbPZ+RaLjOTxoaRaWTvlkFxHqsaZ3AsW71qSJwaE\n"
            + "55l9/qH45vn5mPrHQJ8h5LjgQcN5KBmxUMoA2iT/VSLThgcgl+Iklbcv9rs6aaMC\n"
            + "JH+zKbub+RyRijmyzD9YBr+ZTaowHvJs9G59uZMCAwEAAaOB9jCB8zAPBgNVHRMB\n"
            + "Af8EBTADAQH/MB8GA1UdIwQYMBaAFMmAd+BikoL1RpzzuvdMw964o605MB0GA1Ud\n"
            + "DgQWBBSSrWWJsgAPy1ENwSPslE6PwQQ/dzAOBgNVHQ8BAf8EBAMCAQYwEQYDVR0g\n"
            + "BAowCDAGBgRVHSAAMD0GA1UdHwQ2MDQwMqAwoC6GLGh0dHA6Ly9jcmwuYnV5cGFz\n"
            + "cy5uby9jcmwvQlBDbGFzczJSb290Q0EuY3JsMD4GCCsGAQUFBwEBBDIwMDAuBggr\n"
            + "BgEFBQcwAYYiaHR0cDovL29jc3AuYnV5cGFzcy5uby9vY3NwL0JQT2NzcDANBgkq\n"
            + "hkiG9w0BAQsFAAOCAgEAq8IVUouNdeHQljyp8xpa9GC7rpSRXGRRTolSXNa9TUfU\n"
            + "48Z0Vj3x9jT58I+I8P7fKp+p4Wdu0kcwxOXsooP8hdGLqXY4nV9amkNRiTs99xa3\n"
            + "Qu/KdLeAPEeeKztxDCLXGmsC4+1G6DuDrOkwSm9Tm+HxSZRGR4Qo3mU3CCSz37us\n"
            + "q7I0mnY4cCeBPQ3zW5J7k7KmMpUlxOPnLpaASY2JhoeiWIWddH6LUsMkZk1jDv+M\n"
            + "Hyw2JWZUEUMCZoxLZ7F+4xP7v8wcEtICFo6tZIaawq9p/S6+mJLcoQ7wdQBM0+NA\n"
            + "cc1MnSbPz75WP4cFhVf1SFq5gBBMCgzYaw+A9bJxDgqV3IMG6TtWfOWz7KhMV+EL\n"
            + "iVp0fXua2GITRwr+htWnID3ShbHOtCMUm9qrqC6aWNPvJqqKLdhgU9bQ/s5o05a0\n"
            + "D8NFT07l8yY6+ge+PPHOidnZrTNFIF9dtEdtyXGNrcqhZF0QvqeV1yZ/Kf2+W4pa\n"
            + "Wor82CuDZNfcf0lje3guk+oZexxpIO57eGJQh9iGLM5dBeEMF7+f5j/1/rGsf6vA\n"
            + "KkudpjiTl1v/GoO2zMDTTQVcjEsLSYSV0+s2p5QTXuAXrL0/ER3KQRvewIAtmzFg\n"
            + "IaPy7t2TV0olHISRMvaEz4Guh2biuO/N6SP3pkk3dsMxiEVw7Xc+ouCb03Rz3aA=\n"
            + "-----END CERTIFICATE-----";

    // Owner: CN=valid.domainplus.ca22.ssl.buypass.no
    // Issuer: CN=Buypass Class 2 CA 2, O=Buypass AS-983163327, C=NO
    // Serial number: f0673c7183c95b38c93
    // Valid from: Mon Jan 25 00:20:55 PST 2016 until: Fri Jan 25 14:59:00 PST 2019
    private static final String VALID_CLASS_2 = "-----BEGIN CERTIFICATE-----\n"
            + "MIIEgzCCA2ugAwIBAgIKDwZzxxg8lbOMkzANBgkqhkiG9w0BAQsFADBLMQswCQYD\n"
            + "VQQGEwJOTzEdMBsGA1UECgwUQnV5cGFzcyBBUy05ODMxNjMzMjcxHTAbBgNVBAMM\n"
            + "FEJ1eXBhc3MgQ2xhc3MgMiBDQSAyMB4XDTE2MDEyNTA4MjA1NVoXDTE5MDEyNTIy\n"
            + "NTkwMFowLzEtMCsGA1UEAwwkdmFsaWQuZG9tYWlucGx1cy5jYTIyLnNzbC5idXlw\n"
            + "YXNzLm5vMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwhA0eVz8ADqx\n"
            + "dcrIZUzCf1n+kaBFyEF4WteUMtM4ta7szTm19f1/O4LRwr+pI5qQDgWHnHMX9sit\n"
            + "rKOJPfMRgWrViaQ5y9QCZ4h2BIuDe61XVGkEcUiOoNojLRvDrbjpknI69nb1wbjn\n"
            + "fpmCQVjYXoandr7RsexdWG4e+s6rk5Jk/zAUzU3Vbi0lmDJ62Dd+Dk3/IVrSebOp\n"
            + "eIDniRX4vjIeucnDDTQ1VqSIN+gYNR/bMxXKFbScGAG+BpgZMwetJBJhTi7zlOgR\n"
            + "4zAtdvvpJNN1pmNCsmJaM25WQgH6a05cTQtgYN//MKqTDww7z+LfK37mOxh3vBTu\n"
            + "TR5S6VxzQQIDAQABo4IBgzCCAX8wCQYDVR0TBAIwADAfBgNVHSMEGDAWgBSSrWWJ\n"
            + "sgAPy1ENwSPslE6PwQQ/dzAdBgNVHQ4EFgQUIs9OWkfc6S1c8mbYgi6Ns1kzh0Mw\n"
            + "DgYDVR0PAQH/BAQDAgWgMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjAf\n"
            + "BgNVHSAEGDAWMAoGCGCEQgEaAQIEMAgGBmeBDAECATA6BgNVHR8EMzAxMC+gLaAr\n"
            + "hilodHRwOi8vY3JsLmJ1eXBhc3Mubm8vY3JsL0JQQ2xhc3MyQ0EyLmNybDAvBgNV\n"
            + "HREEKDAmgiR2YWxpZC5kb21haW5wbHVzLmNhMjIuc3NsLmJ1eXBhc3Mubm8wdQYI\n"
            + "KwYBBQUHAQEEaTBnMC4GCCsGAQUFBzABhiJodHRwOi8vb2NzcC5idXlwYXNzLm5v\n"
            + "L29jc3AvQlBPY3NwMDUGCCsGAQUFBzAChilodHRwOi8vY3J0LmJ1eXBhc3Mubm8v\n"
            + "Y3J0L0JQQ2xhc3MyQ0EyLmNlcjANBgkqhkiG9w0BAQsFAAOCAQEAjDPxDQnnzH+v\n"
            + "Mnj8dRM6NPBVXl4JNofWlwqzYdu+HauFeF3AOZVVyr/YbOR9/ewDrScOvrGohndV\n"
            + "7Si0l5hz3fo51Ra81TyR8kWR7nJC2joidT1X4a0hF9zu8CNQNVmkOhoACgeuv42R\n"
            + "NDwmj9TfpNRyC4RA7/NzXMeRJYfOrh18S9VHhCzsWScd9td3u7hrhBOPPOql9f2K\n"
            + "t9Hcevo+cceE6bGYwbW6xNr3iPOh31shMxgRUMojVamtH70tYMi+0e0lrzXdxgGO\n"
            + "ISnXBS2HptakUIxF3feTOjBhhh5vb9RJxfdJA///ggkR3L51MfjrusucpNoz3k3P\n"
            + "f5e7ZlSJ6g==\n"
            + "-----END CERTIFICATE-----";

    // Owner: CN=revoked.domainplus.ca22.ssl.buypass.no
    // Issuer: CN=Buypass Class 2 CA 2, O=Buypass AS-983163327, C=NO
    // Serial number: f07a517dfc19ea8bf8f
    // Valid from: Mon Jan 25 00:22:09 PST 2016 until: Fri Jan 25 14:59:00 PST 2019
    private static final String REVOKED_CLASS_2 = "-----BEGIN CERTIFICATE-----\n"
            + "MIIEhzCCA2+gAwIBAgIKDwelF9/Bnqi/jzANBgkqhkiG9w0BAQsFADBLMQswCQYD\n"
            + "VQQGEwJOTzEdMBsGA1UECgwUQnV5cGFzcyBBUy05ODMxNjMzMjcxHTAbBgNVBAMM\n"
            + "FEJ1eXBhc3MgQ2xhc3MgMiBDQSAyMB4XDTE2MDEyNTA4MjIwOVoXDTE5MDEyNTIy\n"
            + "NTkwMFowMTEvMC0GA1UEAwwmcmV2b2tlZC5kb21haW5wbHVzLmNhMjIuc3NsLmJ1\n"
            + "eXBhc3Mubm8wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDjp/5BLRjH\n"
            + "03XNNT2YXqg+txclRaUu88Rjbj4oEudFbkGTl+oBhmXX4QjM4WGvgw1AHW7nePWF\n"
            + "/j3aR1kWJCl/ZOe097mb0V0dIwK6u6RVx9ERd4ITa/cmUJjy1+D+vCsT0elJY1vf\n"
            + "vbwCdaloS7MZDG3wmJGxrUz7fo7t/JdsW481Ymau3xVTQ+45MusPmOE8RZ6nggIQ\n"
            + "dZIA00XPhlQwg5ivuPwtcNNZIkk1fkU+5J+RUOI5qHA9zH2s1Hly6PzTATCxSDSi\n"
            + "zqAmBH0ehrWqCWiKH5P3J8dCRA6qa2n5pD71CweLrUsbmztkBHUlYKlZ0fP6bGiI\n"
            + "ZDMBLL/aFQybAgMBAAGjggGFMIIBgTAJBgNVHRMEAjAAMB8GA1UdIwQYMBaAFJKt\n"
            + "ZYmyAA/LUQ3BI+yUTo/BBD93MB0GA1UdDgQWBBQZICByGObE/pJISOcMavbKRl2L\n"
            + "+zAOBgNVHQ8BAf8EBAMCBaAwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMC\n"
            + "MB8GA1UdIAQYMBYwCgYIYIRCARoBAgQwCAYGZ4EMAQIBMDoGA1UdHwQzMDEwL6At\n"
            + "oCuGKWh0dHA6Ly9jcmwuYnV5cGFzcy5uby9jcmwvQlBDbGFzczJDQTIuY3JsMDEG\n"
            + "A1UdEQQqMCiCJnJldm9rZWQuZG9tYWlucGx1cy5jYTIyLnNzbC5idXlwYXNzLm5v\n"
            + "MHUGCCsGAQUFBwEBBGkwZzAuBggrBgEFBQcwAYYiaHR0cDovL29jc3AuYnV5cGFz\n"
            + "cy5uby9vY3NwL0JQT2NzcDA1BggrBgEFBQcwAoYpaHR0cDovL2NydC5idXlwYXNz\n"
            + "Lm5vL2NydC9CUENsYXNzMkNBMi5jZXIwDQYJKoZIhvcNAQELBQADggEBAAdjMdlP\n"
            + "qYNK+YkrqTgQV0dblIazL/cIhMPByjnEkfxew9tDxpcMWafIFKcgM/QxYJG/mzoL\n"
            + "sSQ9pzzuGLQX7eAPA3rlWoQBusOeOaC3HQqy73kGStd7H8HPa3m+q47Z6JG0w+Fb\n"
            + "rk8odrml+8rAEPLBlldB39xJuNVHjmlyTEDSC4azEXjfV4+kj8uE86sm+AoTt4Ba\n"
            + "tEZSbKp70oH63QKBAEHORMM4gXeP+WG276p3kTcL1VUfgQw7vVmGN0C8DjhK4BAC\n"
            + "0PUChr8agu0F5YcqpGxjLemMnDrqW+Bi/JYmGhEjWTiLSyYSlvJb1dAFUyPlc958\n"
            + "pmOu5xTMEatiPFI=\n"
            + "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator) throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID_CLASS_2, INT_CLASS_2},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED_CLASS_2, INT_CLASS_2},
                ValidatePathWithParams.Status.REVOKED,
                "Mon Jan 25 00:24:47 PST 2016", System.out);
    }
}

class BuypassClass3 {

    // Owner: CN=Buypass Class 3 CA 2, O=Buypass AS-983163327, C=NO
    // Issuer: CN=Buypass Class 3 Root CA, O=Buypass AS-983163327, C=NO
    private static final String INT_CLASS_3 = "-----BEGIN CERTIFICATE-----\n"
            + "MIIFCzCCAvOgAwIBAgIBGDANBgkqhkiG9w0BAQsFADBOMQswCQYDVQQGEwJOTzEd\n"
            + "MBsGA1UECgwUQnV5cGFzcyBBUy05ODMxNjMzMjcxIDAeBgNVBAMMF0J1eXBhc3Mg\n"
            + "Q2xhc3MgMyBSb290IENBMB4XDTEwMTAyNjA5MTYxN1oXDTMwMTAyNjA5MTYxN1ow\n"
            + "SzELMAkGA1UEBhMCTk8xHTAbBgNVBAoMFEJ1eXBhc3MgQVMtOTgzMTYzMzI3MR0w\n"
            + "GwYDVQQDDBRCdXlwYXNzIENsYXNzIDMgQ0EgMjCCASIwDQYJKoZIhvcNAQEBBQAD\n"
            + "ggEPADCCAQoCggEBAL1OFdoURRXuCuwTBJpuCKDE8Euzcg0AeCRGq3VdagbChyCE\n"
            + "CQ5vYWwmpHCyFl1b+r2KyWdQBBdG+msAcIYZal5cjZzrTWvbkfiAD/OneMjhqYB0\n"
            + "pTQIXbTjpPUMOjFM8waNZcqGJqC9H+Z9NkjK5THAK0oOOfKNPHg1MeImbOHVw0fR\n"
            + "48WnNrPpnQDt+SbPFSvw+dACDAybx1XgjMPq7pmZDWbkajOz4yCvrgZm6jvAPeT3\n"
            + "qkBFh7zOZ3IZVdfmRjVahx0iXp5TJ1SsrRr/uCiae1O+NR//XDG3dl9j17HsFlhY\n"
            + "Rl6EvEfVV0OcW94Ret9uBUF73ANZl0b+gwCXnV0CAwEAAaOB9jCB8zAPBgNVHRMB\n"
            + "Af8EBTADAQH/MB8GA1UdIwQYMBaAFEe4zf/lb+74suwvTg75JbCOPGvDMB0GA1Ud\n"
            + "DgQWBBQiMC7S+/ZLysC4O9IExOly5pebDDAOBgNVHQ8BAf8EBAMCAQYwEQYDVR0g\n"
            + "BAowCDAGBgRVHSAAMD0GA1UdHwQ2MDQwMqAwoC6GLGh0dHA6Ly9jcmwuYnV5cGFz\n"
            + "cy5uby9jcmwvQlBDbGFzczNSb290Q0EuY3JsMD4GCCsGAQUFBwEBBDIwMDAuBggr\n"
            + "BgEFBQcwAYYiaHR0cDovL29jc3AuYnV5cGFzcy5uby9vY3NwL0JQT2NzcDANBgkq\n"
            + "hkiG9w0BAQsFAAOCAgEAaOLyxpj2t9k9Rzkxkcj/teTNOWxBLPZDi+eFx3u7laf2\n"
            + "mX/ZUSSE4g7OiKnD7ozWk9Qgocn3rBWGDKsp676RwWV97Elofz73Oebei6P3Gg/9\n"
            + "CD8y6rf8xHRxru5d1ZQ1NkWdPwYI38jlt3LaDjJKZjJW7pOPIMRvw1Y1AY3mYgCJ\n"
            + "Qqpw8jgukHIP0454DPzkUXzg/ZVJG0swmFmjYfARleSPidcs5BJx5ngpcUS4745g\n"
            + "mN9PQ578+ROIbML4Jx83myivlyTQSPdYSwzSswb1RVBJmiF9qC0B1hivCrs4BATu\n"
            + "YeaPV6CiNDr0jGnbxAskz7QDNR6uJSUKX3L9iY2TB/4/5hJ9TZ/YDI6OEG/wVtBz\n"
            + "5FkU0ucztyQa4UG1mXR8Zbs/zt9Fj0Xn8f5IM3dB/s/r8c1AFDIcLRUqP/LkI9Wj\n"
            + "XovWr79PEJcIfIln0AfzYfBBxCRE+4QHcVhci6p/mbyl2a+Rf8ZGNTiDLaWSZp5x\n"
            + "jqdaq5UQaoZK8XQ+JVR0etep/KPgVMXq5Zv16YEb2vjs//RfxT8psDZLe/37+Bs4\n"
            + "AG9sdT/bsH7HDQwodTon/HvMmxt4EiU/1Sjco4Fok9VmSE2UVjIghajbbTSKR3LV\n"
            + "UuU19x12fKp+htO8L+wVlGgxXb9WvDBNHCe6RmR4jqavmvrAyCPtrx3cXwqGmXA=\n"
            + "-----END CERTIFICATE-----";

    // Owner: SERIALNUMBER=983163327, CN=valid.business.ca23.ssl.buypass.no,
    // O=BUYPASS AS, L=OSLO, OID.2.5.4.17=0484, C=NO
    // Issuer: CN=Buypass Class 3 CA 2, O=Buypass AS-983163327, C=NO
    // Serial number: 97631b91e98293b35c8
    // Valid from: Fri Feb 06 00:57:04 PST 2015 until: Fri Feb 09 14:59:00 PST 2018
    private static final String VALID_CLASS_3 = "-----BEGIN CERTIFICATE-----\n"
            + "MIIE1DCCA7ygAwIBAgIKCXYxuR6YKTs1yDANBgkqhkiG9w0BAQsFADBLMQswCQYD\n"
            + "VQQGEwJOTzEdMBsGA1UECgwUQnV5cGFzcyBBUy05ODMxNjMzMjcxHTAbBgNVBAMM\n"
            + "FEJ1eXBhc3MgQ2xhc3MgMyBDQSAyMB4XDTE1MDIwNjA4NTcwNFoXDTE4MDIwOTIy\n"
            + "NTkwMFowgYExCzAJBgNVBAYTAk5PMQ0wCwYDVQQRDAQwNDg0MQ0wCwYDVQQHDARP\n"
            + "U0xPMRMwEQYDVQQKDApCVVlQQVNTIEFTMSswKQYDVQQDDCJ2YWxpZC5idXNpbmVz\n"
            + "cy5jYTIzLnNzbC5idXlwYXNzLm5vMRIwEAYDVQQFEwk5ODMxNjMzMjcwggEiMA0G\n"
            + "CSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCbahUoF2A7upqIxDQKraZ+aEOzNkHF\n"
            + "1fIQEtUMQS1OTB8la7pWsBnv1gk9Ja2ifIrwdSxAjefL3SXR47h4vxUMnufMnkTk\n"
            + "PERXft/XR8/jZQZRpznnN/V89ctb8qcVhHCooTIELOBzF9QAmDnawZQogwhDNLNy\n"
            + "kLtWsl75X547DS/Z5hsqCqXPyOiFzkHY59uamYu48TF9d7HwQ741H0YhehoxTl/O\n"
            + "YqzW2wqYxqhQuCX5IuYER7G/P3G6UAm+VB9aujtWW+TBT9+iWh0aT+C7ezDtREse\n"
            + "lwb44svf8S3iW18KlSF8EMT0qwqNpA8njOCQiSgluYD+Uk9E5f8505UzAgMBAAGj\n"
            + "ggGBMIIBfTAJBgNVHRMEAjAAMB8GA1UdIwQYMBaAFCIwLtL79kvKwLg70gTE6XLm\n"
            + "l5sMMB0GA1UdDgQWBBQncKIaP6HdQV8RIBO+dddWDSKvJjAOBgNVHQ8BAf8EBAMC\n"
            + "BaAwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMCMB8GA1UdIAQYMBYwCgYI\n"
            + "YIRCARoBAwQwCAYGZ4EMAQICMDoGA1UdHwQzMDEwL6AtoCuGKWh0dHA6Ly9jcmwu\n"
            + "YnV5cGFzcy5uby9jcmwvQlBDbGFzczNDQTIuY3JsMC0GA1UdEQQmMCSCInZhbGlk\n"
            + "LmJ1c2luZXNzLmNhMjMuc3NsLmJ1eXBhc3Mubm8wdQYIKwYBBQUHAQEEaTBnMC4G\n"
            + "CCsGAQUFBzABhiJodHRwOi8vb2NzcC5idXlwYXNzLm5vL29jc3AvQlBPY3NwMDUG\n"
            + "CCsGAQUFBzAChilodHRwOi8vY3J0LmJ1eXBhc3Mubm8vY3J0L0JQQ2xhc3MzQ0Ey\n"
            + "LmNlcjANBgkqhkiG9w0BAQsFAAOCAQEAqeA3IqMPn/az52twbNnimXIhIb7tWj7U\n"
            + "NSBqr+httoQvNo7NbtVCgO/fM3/t0YN7rgZfP07QTn7L7CwoddrgHbnuCuFr9UhD\n"
            + "df7cfY3cwDhWx+YKgXTkRZpXXrOPqeY2+9gaJlcQCnw66t5EBa4lSBnN0ZtkB4lT\n"
            + "ujFP6BAyzZAjRdXWUidtErDWZri1uLmWAP0kQNez2toOcQ0XpbrbL8+nQtvOVOJv\n"
            + "b/c8WoaoC14C32mAeC5bx4dQ3mpf3hQv9man1SPjY/rsDsWWjsaJAijl3YPtP2bU\n"
            + "JRCCM7qfZWrY8/uBLG2llfjviKV9I6sT76w7TnawPsz+SkDXFm/nwg==\n"
            + "-----END CERTIFICATE-----";

    // Owner: SERIALNUMBER=983163327, CN=revoked.business.ca23.ssl.buypass.no,
    // O=BUYPASS AS, L=OSLO, OID.2.5.4.17=0402, C=NO
    // Issuer: CN=Buypass Class 3 CA 2, O=Buypass AS-983163327, C=NO
    private static final String REVOKED_CLASS_3 = "-----BEGIN CERTIFICATE-----\n"
            + "MIIE2DCCA8CgAwIBAgIKARno/wYhPtNtmjANBgkqhkiG9w0BAQsFADBLMQswCQYD\n"
            + "VQQGEwJOTzEdMBsGA1UECgwUQnV5cGFzcyBBUy05ODMxNjMzMjcxHTAbBgNVBAMM\n"
            + "FEJ1eXBhc3MgQ2xhc3MgMyBDQSAyMB4XDTEzMDIwMTA5MTE0NFoXDTE2MDIwMTA5\n"
            + "MTE0NFowgYMxCzAJBgNVBAYTAk5PMQ0wCwYDVQQRDAQwNDAyMQ0wCwYDVQQHDARP\n"
            + "U0xPMRMwEQYDVQQKDApCVVlQQVNTIEFTMS0wKwYDVQQDDCRyZXZva2VkLmJ1c2lu\n"
            + "ZXNzLmNhMjMuc3NsLmJ1eXBhc3Mubm8xEjAQBgNVBAUTCTk4MzE2MzMyNzCCASIw\n"
            + "DQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMmBUI0wNCz4kLikR5wog4QTUEmO\n"
            + "XoGgjnQv0cKfDogbewK+0ngdyyR8dZOqSauQTGLlPTpo6DEWpD3Jqrr444MV6Vc1\n"
            + "AGWnjk3T+KT5tKl6qJOQq17Y+HEnsTEzCo1kieVygpSu7FBa2OnhHNmLWThhGUEi\n"
            + "mLqrEyfjMSb9zacvo06Zr7S8BauLRB3aM5BeMVF7Bj/9f/FvnB/y1cRDLG32WRCx\n"
            + "K9IAFwCaJkfWsXx+bnaO4uEQwLFZ96p7L5mr+QNvI6QuweIY1hDM3RDM6HQkGTK9\n"
            + "8iHSzGBSCGwOM24Ym3XM5vTbiV5uLno+QEYlJL/+qbYvarbO2gPF+6A6M10CAwEA\n"
            + "AaOCAYMwggF/MAkGA1UdEwQCMAAwHwYDVR0jBBgwFoAUIjAu0vv2S8rAuDvSBMTp\n"
            + "cuaXmwwwHQYDVR0OBBYEFNI2C2XKZkNRHZrHLkBhCMeDRN0KMA4GA1UdDwEB/wQE\n"
            + "AwIFoDAdBgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwHwYDVR0gBBgwFjAK\n"
            + "BghghEIBGgEDBDAIBgZngQwBAgIwOgYDVR0fBDMwMTAvoC2gK4YpaHR0cDovL2Ny\n"
            + "bC5idXlwYXNzLm5vL2NybC9CUENsYXNzM0NBMi5jcmwwLwYDVR0RBCgwJoIkcmV2\n"
            + "b2tlZC5idXNpbmVzcy5jYTIzLnNzbC5idXlwYXNzLm5vMHUGCCsGAQUFBwEBBGkw\n"
            + "ZzAuBggrBgEFBQcwAYYiaHR0cDovL29jc3AuYnV5cGFzcy5uby9vY3NwL0JQT2Nz\n"
            + "cDA1BggrBgEFBQcwAoYpaHR0cDovL2NydC5idXlwYXNzLm5vL2NydC9CUENsYXNz\n"
            + "M0NBMi5jZXIwDQYJKoZIhvcNAQELBQADggEBAGNQe9cgrw/mN7bChof205NRS+TH\n"
            + "A8f0JcKk1KrPYYW+ilyp6j3My26Sm9a4ZyKRhAS8fCxYUXWzfNvJNFYv2ttLuegl\n"
            + "SFfeXjSJJZW9+wC5oRLta++62UTTxXp0Zf5UkMsHZCIjvnk0yGWZa0phyRCH89ca\n"
            + "4vfRTOGNTNfX3d0jm/+fm70UNYHKZ/VcxVj0vH2Ij/kDUy7r2cw1gQ65RDUotnTu\n"
            + "Yt59y3COyMZeYNMcuoss2XWnedFoD7fwCSkNqVbwjCxGVkL1+ivbWhqlCefaniZX\n"
            + "Wy35oP1635RSxHbCMU9msmUO7FS8n1VH2edEC797gduK5pn2aBhy/MW0unU=\n"
            + "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator, boolean ocspEnabled)
            throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID_CLASS_3, INT_CLASS_3},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        if (ocspEnabled) {
            // Revoked test certificate is expired
            // and backdated revocation check is only possible with OCSP
            pathValidator.setValidationDate("July 01, 2013");
        }

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED_CLASS_3, INT_CLASS_3},
                ValidatePathWithParams.Status.REVOKED,
                "Wed Feb 06 02:56:32 PST 2013", System.out);
    }
}
