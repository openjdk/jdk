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
 * @summary Interoperability tests with QuoVadis Root CA1, CA2, and CA3 CAs
 * @build ValidatePathWithParams
 * @run main/othervm -Djava.security.debug=certpath QuoVadisCA OCSP
 * @run main/othervm -Djava.security.debug=certpath QuoVadisCA CRL
 */

 /*
 * Obtain TLS test artifacts for QuoVadis CAs from:
 *
 * Valid TLS Certificates:
 * CA1: https://qvica1g3-v.quovadisglobal.com
 * CA2: https://qvsslicag3-v.quovadisglobal.com
 * CA2 EV: https://evsslicag3-v.quovadisglobal.com
 * CA3: https://qvica3g3-v.quovadisglobal.com
 *
 * Revoked TLS Certificates:
 * CA1: https://qvica1g3-r.quovadisglobal.com
 * CA2: https://qvsslicag3-r.quovadisglobal.com
 * CA2 EV: https://evsslicag3-r.quovadisglobal.com
 * CA3: https://qvica3g3-r.quovadisglobal.com
 */
public class QuoVadisCA {

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

        new RootCA1().runTest(pathValidator, ocspEnabled);
        new RootCA2().runTest(pathValidator, ocspEnabled);
        new RootCA3().runTest(pathValidator, ocspEnabled);
    }
}

class RootCA1 {

    // Owner: CN=QuoVadis Issuing CA 1 G3, O=QuoVadis Limited, C=BM
    private static final String INT = "-----BEGIN CERTIFICATE-----\n"
            + "MIIGFTCCA/2gAwIBAgIUPybG62jqpxKYOV5MlXAGPJYDy9MwDQYJKoZIhvcNAQEL\n"
            + "BQAwSDELMAkGA1UEBhMCQk0xGTAXBgNVBAoTEFF1b1ZhZGlzIExpbWl0ZWQxHjAc\n"
            + "BgNVBAMTFVF1b1ZhZGlzIFJvb3QgQ0EgMSBHMzAeFw0xMjExMDYxNjA5NTJaFw0y\n"
            + "MjExMDYxNjA5NTJaMEsxCzAJBgNVBAYTAkJNMRkwFwYDVQQKExBRdW9WYWRpcyBM\n"
            + "aW1pdGVkMSEwHwYDVQQDExhRdW9WYWRpcyBJc3N1aW5nIENBIDEgRzMwggIiMA0G\n"
            + "CSqGSIb3DQEBAQUAA4ICDwAwggIKAoICAQC2Ud42yCfjYm4WlQ+nhTpZ9aPp0r8a\n"
            + "yz+kKpPxc8ZWvEi7HDPhr7f5nWnEruHE0HbH8WyFGE+sICF788VpZLbFhL4wbIWV\n"
            + "tHIrYan7+yL2yoNbHUBgeWxa48P96WxrW34K/OyQkJoSvY4iNk4BGI0wOYD9wsl9\n"
            + "6wIaQFNu25Wsv0CcDSsyNjw8O8Ib6dmS6iib+KnZlKJnYqSTrzbnzf/2CU+Wb9V0\n"
            + "yExk7shcfDpqxo9yyyEBPP1GUEb5SSr9qXYP2d4UsrRIgzKpD5feqdjk6ZGA4xeM\n"
            + "JHo6GLjddNvVvopaKaLrDzlOXgqgbMIPQu+xkzpKW3IJOylxN55oVuH25MwbS9IL\n"
            + "kDMv//kdiTUl1wXERZiUmcBdpWt9D9liyVxe5+HeI5VlhDuHsxDoPFmoOGTa6brX\n"
            + "PXlNc0xji+grBQjIRNs43T5+GyYzCyjzG3dSb0BTYGLnfUAEQ1+MCC3K33DKL/me\n"
            + "iUrWNclh85BQQigJr5HNLym3+J6Jf0OCnq4VmD1OFrhZrui02Xmz/hOECK2Mciga\n"
            + "DxRgXBKjLebV0RW3j6libuPiKbxSinfqNqf2Q9eCfKrzgWQkuCHZvkt0Cqgzjbm1\n"
            + "n5xu9zXR8YG5/680Nyb3tywUb6FhA8l1L/KLoK79RGjKgPotCog6Ykvy/667jlyo\n"
            + "ZII0YUf6S3uyeQIDAQABo4HzMIHwMBIGA1UdEwEB/wQIMAYBAf8CAQEwEQYDVR0g\n"
            + "BAowCDAGBgRVHSAAMDoGCCsGAQUFBwEBBC4wLDAqBggrBgEFBQcwAYYeaHR0cDov\n"
            + "L29jc3AucXVvdmFkaXNnbG9iYWwuY29tMA4GA1UdDwEB/wQEAwIBBjAfBgNVHSME\n"
            + "GDAWgBSjl9bzXqIQ4atFnzwXZDzuAXCczDA7BgNVHR8ENDAyMDCgLqAshipodHRw\n"
            + "Oi8vY3JsLnF1b3ZhZGlzZ2xvYmFsLmNvbS9xdnJjYTFnMy5jcmwwHQYDVR0OBBYE\n"
            + "FF0EGBL7w+p4fbRXCaH+bf6Cn2TNMA0GCSqGSIb3DQEBCwUAA4ICAQAF6qNCo3LP\n"
            + "Qk8jthU1aiuo5WW9jQC+PqWeyVe4JjHK+5PRM+BtoErOItfZyPqoIBMedC/Ya9L1\n"
            + "Sv0gncvifjtTD3jIBz0FVCbIMJLRp63b4qtmAGuB00XTXgCFcYoiIq5kyNedJnLe\n"
            + "IxMqb0xx8IAqvP9kfEVNdGfvYraSswiGXADftZ3yM24zIc3Ysewi3JeTbzDhEGfb\n"
            + "yv9eBplkfKfcoKyOds4sLcxj1QpUxcXgjX1mKTbfOSD5ac/Cjrz6Kqnl2+PNrc5N\n"
            + "kXBVKhcCAjpqX5OyI86IUg9XY8i7Lz+tXzAQhllh+rPyTyAmieGf2iV9wrl//OZB\n"
            + "l2nXwbgfA7QwQ2VdsmGJfW3a7Zc13GCNx0M2RUGJKLMJOavY72d41wAYPQ46AXls\n"
            + "Ic7RJi6EWmwLi6lvw4kKFfWZ0c6vIJur1hLUUmLOt0UBZ226eIREVpmFbDGOLzfl\n"
            + "gU0xKhqmU0aIOORzBGDfOrnctvaXORNNhCZ78zS96Egzu2w2OC47Zry7k+EOatzA\n"
            + "5zrdJJM3UP7aMSNPvEygbcFUw2I04vpxUuPYTwCtogqNMHqFbCjLM9YxhzsGMdh/\n"
            + "9aD1krboaSXUjrS9cOr5P2A9kFHCMsXBaDoaijQXNeyhu+oCeYsdv4S3djFwDW3+\n"
            + "iPLo51aqZGsTZ1S22vYdkp+QFByLtArVMQ==\n"
            + "-----END CERTIFICATE-----";

    // Owner: CN=qvica1g3-v.quovadisglobal.com, O=QuoVadis Limited, L=Hamilton,
    // ST=Pembroke, C=BM
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n"
            + "MIIF9DCCA9ygAwIBAgIUGug3BoLw4/auIdDQ0mHS6QnPHB8wDQYJKoZIhvcNAQEL\n"
            + "BQAwSzELMAkGA1UEBhMCQk0xGTAXBgNVBAoTEFF1b1ZhZGlzIExpbWl0ZWQxITAf\n"
            + "BgNVBAMTGFF1b1ZhZGlzIElzc3VpbmcgQ0EgMSBHMzAeFw0xNDExMTQxNDA1MDda\n"
            + "Fw0xNzExMTQxNDA0NTFaMHYxCzAJBgNVBAYTAkJNMREwDwYDVQQIEwhQZW1icm9r\n"
            + "ZTERMA8GA1UEBxMISGFtaWx0b24xGTAXBgNVBAoTEFF1b1ZhZGlzIExpbWl0ZWQx\n"
            + "JjAkBgNVBAMTHXF2aWNhMWczLXYucXVvdmFkaXNnbG9iYWwuY29tMIIBIjANBgkq\n"
            + "hkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwHoNPHE0C/tEwI5jeYvKJdo5SXSccB/c\n"
            + "nCHJVs/4i9F8oRmPqNiFMD99UVylk4nn8iqi8MoxrFAhqtmplPslgRDLwyLMmnGO\n"
            + "1cNoPKGMKxQq9EerBgSk4wqeSsSH+7qnZhCamIlvEm0PUaEH8rcjXokTs0fyjadF\n"
            + "UmVwcmSZdmnNjseOMgm+G6C8tEPHRQl/Oezy6DzS9PQVLUFCBSyOaAgDnr4EvwGE\n"
            + "u2fd3m+ys80XXGq4eLy1MmuC7U+bIQuupuydk/S7kVh7Rl+5nT1eTv0LEOj5gYFc\n"
            + "C5SBnhiLibuRTOr+LsC9HpvN4vnoCaOogWxDQj/f1KRn45PNJncsbwIDAQABo4IB\n"
            + "ozCCAZ8wdAYIKwYBBQUHAQEEaDBmMCoGCCsGAQUFBzABhh5odHRwOi8vb2NzcC5x\n"
            + "dW92YWRpc2dsb2JhbC5jb20wOAYIKwYBBQUHMAKGLGh0dHA6Ly90cnVzdC5xdW92\n"
            + "YWRpc2dsb2JhbC5jb20vcXZpY2ExZzMuY3J0MCgGA1UdEQQhMB+CHXF2aWNhMWcz\n"
            + "LXYucXVvdmFkaXNnbG9iYWwuY29tMFEGA1UdIARKMEgwRgYMKwYBBAG+WAACZAEB\n"
            + "MDYwNAYIKwYBBQUHAgEWKGh0dHA6Ly93d3cucXVvdmFkaXNnbG9iYWwuY29tL3Jl\n"
            + "cG9zaXRvcnkwDgYDVR0PAQH/BAQDAgWgMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggr\n"
            + "BgEFBQcDAjAfBgNVHSMEGDAWgBRdBBgS+8PqeH20Vwmh/m3+gp9kzTA7BgNVHR8E\n"
            + "NDAyMDCgLqAshipodHRwOi8vY3JsLnF1b3ZhZGlzZ2xvYmFsLmNvbS9xdmljYTFn\n"
            + "My5jcmwwHQYDVR0OBBYEFJO98+S7NZMTz2JRogpUwLuxjTa0MA0GCSqGSIb3DQEB\n"
            + "CwUAA4ICAQCq1O/BnzpQjbTbmEob/bWH/p92ZYRV0Lr01CdYkRXl4XKL2ZLusel6\n"
            + "126AIvAK51o65wiGVaLGs49AKXOjcaAnTfwoFembqFRlBiGFSOdglTIsZUGdmhtP\n"
            + "x1meetkOY8bY79viGkVCufAVq0hAF+AYh4nYM+/n7IijIcM5uhzIDb2Vw8+wKPTB\n"
            + "7k2K/e1GGwbqrIAkjrZ6kpRg632RkbR18anaDVOgXuKzmZMRbIAii/N+lo7u3DhC\n"
            + "5mJEIjP4cQXd569AfKQzvBO+syGDAJyX5PbTrd59IXZ+EjiisIq/DNQi6QalWMfS\n"
            + "BnK97nUzH/BjAofMaUufbB8dxg+RT0QC/Yl1lmlA3CYmr6YWn06DiAuWL14ZFFwh\n"
            + "2HQ7juU9oQ1I/HTfhVBoTzuKGCW1ZNXA64IdKlBsYp8NO9xKjBWIxwU/+S/IgoQP\n"
            + "aTNkY4Mc353bdLi9082JwaiQ9B5eH0V9pZ17OSRU44o2TeDDT85sjF+krqCnnolR\n"
            + "3lk7iqYDRHsvgqJqtkhhX/boF3wJAnKqaZ6j97PVqV75kwAak7XaH7C50RsPQGk2\n"
            + "j5OFa6ioobW7tN5PfWAZPMZn98yX2Wh8Z95aGhdsHSJHsrlcUiWa+X2D1kF/dOKd\n"
            + "R8rPqdPIPjUglrXS4yP+cJHx6fCJxW7me1R60lpuL6JNvHp54u7GGA==\n"
            + "-----END CERTIFICATE-----";

    // Owner: CN=qvica1g3-r.quovadisglobal.com, O=QuoVadis Limited, L=Hamilton,
    // ST=Pembroke, C=BM
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n"
            + "MIIF9DCCA9ygAwIBAgIUBAG4l0ZPYhEdLJSMWYCr7LHngvswDQYJKoZIhvcNAQEL\n"
            + "BQAwSzELMAkGA1UEBhMCQk0xGTAXBgNVBAoTEFF1b1ZhZGlzIExpbWl0ZWQxITAf\n"
            + "BgNVBAMTGFF1b1ZhZGlzIElzc3VpbmcgQ0EgMSBHMzAeFw0xMjExMTQxMzQ4MDda\n"
            + "Fw0xNDExMTQxMzQ4MDdaMHYxCzAJBgNVBAYTAkJNMREwDwYDVQQIEwhQZW1icm9r\n"
            + "ZTERMA8GA1UEBxMISGFtaWx0b24xGTAXBgNVBAoTEFF1b1ZhZGlzIExpbWl0ZWQx\n"
            + "JjAkBgNVBAMTHXF2aWNhMWczLXIucXVvdmFkaXNnbG9iYWwuY29tMIIBIjANBgkq\n"
            + "hkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqlof1qJLTiqI7bf0IU7zOxy0HqjIn0pW\n"
            + "lNIEVAjQRR1jnfpsMapicIGZfnnNaYpwdsIjGPwpvWXGA+30ezJNGfWMjhb/tiis\n"
            + "qjrHdwXAob5MyXOXP5ZS8K34GwKeL45oJZZG0cf2FSta9/CSsRC9wnDUp/kA+VkH\n"
            + "n5vlg7VpUExYO0CBXe4C4ehnvCZHjW5nqpVpm993f9i8E0W3vHPxjGuyuqVEEfma\n"
            + "WfOV78+HF4hxALnr+73mp0i6Do2oa/v85mZzyKeBm2YHhwdQ6CC7UZtABlHyWuz9\n"
            + "h/ocTGbX92rbUaW6icu9bKQkQ9jsomnQkU5b8CWseo2O0NXBevvCowIDAQABo4IB\n"
            + "ozCCAZ8wdAYIKwYBBQUHAQEEaDBmMCoGCCsGAQUFBzABhh5odHRwOi8vb2NzcC5x\n"
            + "dW92YWRpc2dsb2JhbC5jb20wOAYIKwYBBQUHMAKGLGh0dHA6Ly90cnVzdC5xdW92\n"
            + "YWRpc2dsb2JhbC5jb20vcXZpY2ExZzMuY3J0MCgGA1UdEQQhMB+CHXF2aWNhMWcz\n"
            + "LXIucXVvdmFkaXNnbG9iYWwuY29tMFEGA1UdIARKMEgwRgYMKwYBBAG+WAACZAEB\n"
            + "MDYwNAYIKwYBBQUHAgEWKGh0dHA6Ly93d3cucXVvdmFkaXNnbG9iYWwuY29tL3Jl\n"
            + "cG9zaXRvcnkwDgYDVR0PAQH/BAQDAgWgMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggr\n"
            + "BgEFBQcDAjAfBgNVHSMEGDAWgBRdBBgS+8PqeH20Vwmh/m3+gp9kzTA7BgNVHR8E\n"
            + "NDAyMDCgLqAshipodHRwOi8vY3JsLnF1b3ZhZGlzZ2xvYmFsLmNvbS9xdmljYTFn\n"
            + "My5jcmwwHQYDVR0OBBYEFNrefqnat67/DMlw0Z/xdQ478leyMA0GCSqGSIb3DQEB\n"
            + "CwUAA4ICAQBG1TxJNbWzG4ShZefK4wEdScBzxSB7StYO3mmIP2D3LTlEk+zWjDVP\n"
            + "ERPL41Si92asMHvMai7GcFT82XyHxsQGZIPcgIm+rC2NiSPDx2Vd6lkMaO8J9mrU\n"
            + "3Z4Ks3G5HmszQ/gXRT3DCoNng+k+JqdZjrvMcsGTH+AzRdoinwOi+QnpphAcZRhS\n"
            + "Io8C7w9osUPYFdDaE3Io+oYr2mWJg4n+FGsjxunQgIhLiiNaVF8zHxER7gW0YsCW\n"
            + "vw1jX0dmfQZSdo2ybVeHuznUxtUWRHJ/nv6v2B2anUsVEbPyrpQ3i9+BzWaYolPU\n"
            + "ZYxfMHBQ7HvncRP6rgrHF4x+iOnIxWsErYdEj5nQJkptYbVl41VzO6xMP7WvXFPa\n"
            + "dqxwihqILRmAZrI9p/6k/HqV9xMPKprUhnWDGQ/bYnPKyXoTx6uwamaonX4DpW83\n"
            + "b3CJTvBHwKh5eJQoBykAkakPdrmbOhe4/XWnDqQVUgJNmEvkg33AexviJo4mW3HG\n"
            + "K2MdM60GRIC3Lcnd+Q8SnSCCxp+YtuE/C3Fu8VI/8vz9MC159GGtDzyC7OeKPCpU\n"
            + "7H1X0X/OhBkiv7anK/oIhtSw+4DrM2eaVjdWkEa+di2jvI/2us8TXxO1LL+eeSxT\n"
            + "E+LbdNO0jSp8azw2Aw4zL+Q41Fzt7OlH7mTkw1mxLF3aWsUNUz/p4w==\n"
            + "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator, boolean ocspEnabled)
            throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        if (ocspEnabled) {
            // Revoked certificates are expired in Nov 2014
            // and backdated revocation check is only possible with OCSP
            pathValidator.setValidationDate("Jan 01, 2014");
        }

        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Thu Jan 03 23:47:34 PST 2013", System.out);

        // reset validation date back to current date
        pathValidator.resetValidationDate();
    }
}

class RootCA2 {

    // Owner: CN=QuoVadis Global SSL ICA G3, O=QuoVadis Limited, C=BM
    private static final String INT = "-----BEGIN CERTIFICATE-----\n"
            + "MIIGFzCCA/+gAwIBAgIUftbnnMmtgcTIGT75XUQodw40ExcwDQYJKoZIhvcNAQEL\n"
            + "BQAwSDELMAkGA1UEBhMCQk0xGTAXBgNVBAoTEFF1b1ZhZGlzIExpbWl0ZWQxHjAc\n"
            + "BgNVBAMTFVF1b1ZhZGlzIFJvb3QgQ0EgMiBHMzAeFw0xMjExMDYxNDUwMThaFw0y\n"
            + "MjExMDYxNDUwMThaME0xCzAJBgNVBAYTAkJNMRkwFwYDVQQKExBRdW9WYWRpcyBM\n"
            + "aW1pdGVkMSMwIQYDVQQDExpRdW9WYWRpcyBHbG9iYWwgU1NMIElDQSBHMzCCAiIw\n"
            + "DQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIBANf8Od17be6c6lTGJDhEXpmkTs4y\n"
            + "Q39Rr5VJyBeWCg06nSS71s6xF3sZvKcV0MbXlXCYM2ZX7cNTbJ81gs7uDsKFp+vK\n"
            + "EymiKyEiI2SImOtECNnSg+RVR4np/xz/UlC0yFUisH75cZsJ8T1pkGMfiEouR0EM\n"
            + "7O0uFgoboRfUP582TTWy0F7ynSA6YfGKnKj0OFwZJmGHVkLs1VevWjhj3R1fsPan\n"
            + "H05P5moePFnpQdj1FofoSxUHZ0c7VB+sUimboHm/uHNY1LOsk77qiSuVC5/yrdg3\n"
            + "2EEfP/mxJYT4r/5UiD7VahySzeZHzZ2OibQm2AfgfMN3l57lCM3/WPQBhMAPS1jz\n"
            + "kE+7MjajM2f0aZctimW4Hasrj8AQnfAdHqZehbhtXaAlffNEzCdpNK584oCTVR7N\n"
            + "UR9iZFx83ruTqpo+GcLP/iSYqhM4g7fy45sNhU+IS+ca03zbxTl3TTlkofXunI5B\n"
            + "xxE30eGSQpDZ5+iUJcEOAuVKrlYocFbB3KF45hwcbzPWQ1DcO2jFAapOtQzeS+MZ\n"
            + "yZzT2YseJ8hQHKu8YrXZWwKaNfyl8kFkHUBDICowNEoZvBwRCQp8sgqL6YRZy0uD\n"
            + "JGxmnC2e0BVKSjcIvmq/CRWH7yiTk9eWm73xrsg9iIyD/kwJEnLyIk8tR5V8p/hc\n"
            + "1H2AjDrZH12PsZ45AgMBAAGjgfMwgfAwEgYDVR0TAQH/BAgwBgEB/wIBATARBgNV\n"
            + "HSAECjAIMAYGBFUdIAAwOgYIKwYBBQUHAQEELjAsMCoGCCsGAQUFBzABhh5odHRw\n"
            + "Oi8vb2NzcC5xdW92YWRpc2dsb2JhbC5jb20wDgYDVR0PAQH/BAQDAgEGMB8GA1Ud\n"
            + "IwQYMBaAFO3nb3Zav2DsSVvGpXe7chZxm8Q9MDsGA1UdHwQ0MDIwMKAuoCyGKmh0\n"
            + "dHA6Ly9jcmwucXVvdmFkaXNnbG9iYWwuY29tL3F2cmNhMmczLmNybDAdBgNVHQ4E\n"
            + "FgQUsxKJtalLNbwVAPCA6dh4h/ETfHYwDQYJKoZIhvcNAQELBQADggIBAFGm1Fqp\n"
            + "RMiKr7a6h707M+km36PVXZnX1NZocCn36MrfRvphotbOCDm+GmRkar9ZMGhc8c/A\n"
            + "Vn7JSCjwF9jNOFIOUyNLq0w4luk+Pt2YFDbgF8IDdx53xIo8Gv05e9xpTvQYaIto\n"
            + "qeHbQjGXfSGc91olfX6JUwZlxxbhdJH+rxTFAg0jcbqToJoScWTfXSr1QRcNbSTs\n"
            + "Y4CPG6oULsnhVvrzgldGSK+DxFi2OKcDsOKkV7W4IGg8Do2L/M588AfBnV8ERzpl\n"
            + "qgMBBQxC2+0N6RdFHbmZt0HQE/NIg1s0xcjGx1XW3YTOfje31rmAXKHOehm4Bu48\n"
            + "gr8gePq5cdQ2W9tA0Dnytb9wzH2SyPPIXRI7yNxaX9H8wYeDeeiKSSmQtfh1v5cV\n"
            + "7RXvm8F6hLJkkco/HOW3dAUwZFcKsUH+1eUJKLN18eDGwB8yGawjHvOKqcfg5Lf/\n"
            + "TvC7hgcx7pDYaCCaqHaekgUwXbB2Enzqr1fdwoU1c01W5YuQAtAx5wk1bf34Yq/J\n"
            + "ph7wNXGvo88N0/EfP9AdVGmJzy7VuRXeVAOyjKAIeADMlwpjBRhcbs9m3dkqvoMb\n"
            + "SXKJxv/hFmNgEOvOlaFsXX1dbKg1v+C1AzKAFdiuAIa62JzASiEhigqNSdqdTsOh\n"
            + "8W8hdONuKKpe9zKedhBFAvuxhDgKmnySglYc\n"
            + "-----END CERTIFICATE-----";

    // Owner: CN=qvsslicag3-v.quovadisglobal.com, O=QuoVadis Limited, L=Hamilton,
    // ST=Pembroke, C=BM
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n"
            + "MIIF+DCCA+CgAwIBAgIUE3XHqPhbZc2CY3aRtVHRlQwm3tcwDQYJKoZIhvcNAQEL\n"
            + "BQAwTTELMAkGA1UEBhMCQk0xGTAXBgNVBAoTEFF1b1ZhZGlzIExpbWl0ZWQxIzAh\n"
            + "BgNVBAMTGlF1b1ZhZGlzIEdsb2JhbCBTU0wgSUNBIEczMB4XDTE0MTExNDE0MDUz\n"
            + "MVoXDTE3MTExNDE0MDUxM1oweDELMAkGA1UEBhMCQk0xETAPBgNVBAgTCFBlbWJy\n"
            + "b2tlMREwDwYDVQQHEwhIYW1pbHRvbjEZMBcGA1UEChMQUXVvVmFkaXMgTGltaXRl\n"
            + "ZDEoMCYGA1UEAxMfcXZzc2xpY2FnMy12LnF1b3ZhZGlzZ2xvYmFsLmNvbTCCASIw\n"
            + "DQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAK621GAU2/hywjuxi2Q9rCMncWIY\n"
            + "FbDngS69N6+qe9NUktfs/Rlh+jKUDHyf27G79xYGmDGZ0NTYI7tUyOvRanaq8ngd\n"
            + "NkZI4DS/Au2vpwuXucrtm3V/XRcsWHAsyevVzfiqfZzu+vU7/2KT/k7sByRzED4x\n"
            + "B4tMGaodvzIAzhFAmnmVXSUw7zvU07G/6/mfwYy9gwegJwVby/ZPWXefzHbLGDUz\n"
            + "xtO/6Ow9e5T2hedpo3IgfKptkzy0kA501DNaTMulW1gJwB+1duJ9OxZOAVgGCANX\n"
            + "IzWvgbONLEdkYGK+K8EAuMaa57WlG0wBZ9Y62iuvgw4XRd90/PS2RAFf/DsCAwEA\n"
            + "AaOCAaMwggGfMHMGCCsGAQUFBwEBBGcwZTAqBggrBgEFBQcwAYYeaHR0cDovL29j\n"
            + "c3AucXVvdmFkaXNnbG9iYWwuY29tMDcGCCsGAQUFBzAChitodHRwOi8vdHJ1c3Qu\n"
            + "cXVvdmFkaXNnbG9iYWwuY29tL3F2c3NsZzMuY3J0MCoGA1UdEQQjMCGCH3F2c3Ns\n"
            + "aWNhZzMtdi5xdW92YWRpc2dsb2JhbC5jb20wUQYDVR0gBEowSDBGBgwrBgEEAb5Y\n"
            + "AAJkAQEwNjA0BggrBgEFBQcCARYoaHR0cDovL3d3dy5xdW92YWRpc2dsb2JhbC5j\n"
            + "b20vcmVwb3NpdG9yeTAOBgNVHQ8BAf8EBAMCBaAwHQYDVR0lBBYwFAYIKwYBBQUH\n"
            + "AwEGCCsGAQUFBwMCMB8GA1UdIwQYMBaAFLMSibWpSzW8FQDwgOnYeIfxE3x2MDoG\n"
            + "A1UdHwQzMDEwL6AtoCuGKWh0dHA6Ly9jcmwucXVvdmFkaXNnbG9iYWwuY29tL3F2\n"
            + "c3NsZzMuY3JsMB0GA1UdDgQWBBSSYP84MQGz6cU/fyXfebv/8zn93TANBgkqhkiG\n"
            + "9w0BAQsFAAOCAgEAbqX71QIeoOJ36Aoiwg+oEwdDSRzXkR05kZU2y9qOCArrkgSj\n"
            + "ycdIRQFjHYNAWJrPP17PErk6+6NDWiwxLXbeHaY7pFIDCsgcCTWpixVlpVPKKxAE\n"
            + "uaomHo5K2AWWkJYNNPSLF411CmyN4eJjYQVrkCfJwFSUrQml8pFDedNDNuTaDsZo\n"
            + "klvUDYWM18gFrAbNF4Wi+dvj3qPOpTVyrTk2oBXtVUesNu4JF/O6li10YJ+kdox+\n"
            + "DUeq4Gk4B8zoWRTKa9Pp/RALI8TeNcfjBKbPtuXyfly1Cm8AXoQA5sus2SMMPQXE\n"
            + "S1+IsdnnKb60pT1EOX571SIBKV16xpRpbC3mDU6IG/+Sjm0TJxwSGbBO5bX69+bq\n"
            + "F8Im1QAKqVSYCtoypvieG3iGqHmj4SAaSqdmDDzmOPEtgX63ZmYVs+ey6tN+VThi\n"
            + "eaLRs+pHeBLMhh7Npt85c+xqRlIFBp0e84EzST0oE7pjuZcFFWstFXD2Pt1JQfXo\n"
            + "9szkw6EMhYbrgNqsh8lxkg8cZKHnP8KGLefyHajp3EIfC2MX7nUOeNmNoCxdsfBW\n"
            + "lmzRbv7H7eeAmQYHmxUaRnDMGR6QVX8/NrF1w0hqLkIpDj+29Mvv/Gp2azJrvqrL\n"
            + "w2bJ2mPD8rzBDmEFY317RWc8VBd8ZUxO/dYPWqsXNwBTdPMRQpYcN55po3g=\n"
            + "-----END CERTIFICATE-----";

    // Owner: CN=qvsslicag3-r.quovadisglobal.com, O=QuoVadis Limited, L=Hamilton,
    // ST=Pembroke, C=BM
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n"
            + "MIIF+DCCA+CgAwIBAgIUMJWFWsVjz9o3zQoG9bZ/IsdRWDkwDQYJKoZIhvcNAQEL\n"
            + "BQAwTTELMAkGA1UEBhMCQk0xGTAXBgNVBAoTEFF1b1ZhZGlzIExpbWl0ZWQxIzAh\n"
            + "BgNVBAMTGlF1b1ZhZGlzIEdsb2JhbCBTU0wgSUNBIEczMB4XDTEyMTExNDEzMTI1\n"
            + "NFoXDTE0MTExNDEzMTI1NFoweDELMAkGA1UEBhMCQk0xETAPBgNVBAgTCFBlbWJy\n"
            + "b2tlMREwDwYDVQQHEwhIYW1pbHRvbjEZMBcGA1UEChMQUXVvVmFkaXMgTGltaXRl\n"
            + "ZDEoMCYGA1UEAxMfcXZzc2xpY2FnMy1yLnF1b3ZhZGlzZ2xvYmFsLmNvbTCCASIw\n"
            + "DQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALlwpCyabhrQYeRzEn0O7S505Fv4\n"
            + "ScJlJRHskcyZHBt0vt2tsDJNh2xJKJpnXzW14oGh+xrccEGeUw77qeFgfy+LTIHD\n"
            + "YDkYVHVhfs4NJD5wdyWL9Fn3A7pMFpapPBPJdsAAwByfzYFjRJsPHMSlGcroyGNm\n"
            + "+LquU5r965afaRkWQzZy+lY+OHO19Jis8EfUusYj2fQ3SXB8tBwFylDTnbCoM1HZ\n"
            + "BlbksbtLjFYKtyaNeQuoA7NnB3Q9XEONNK9KZ0S87KIqEKjIWK7ThO6lvhMQy2Zk\n"
            + "k+UVXVLpop7+Nkz3Fn08pE4OMfLjn1KVnk5l40WGabinfE6hz4vk0XREaKcCAwEA\n"
            + "AaOCAaMwggGfMHMGCCsGAQUFBwEBBGcwZTAqBggrBgEFBQcwAYYeaHR0cDovL29j\n"
            + "c3AucXVvdmFkaXNnbG9iYWwuY29tMDcGCCsGAQUFBzAChitodHRwOi8vdHJ1c3Qu\n"
            + "cXVvdmFkaXNnbG9iYWwuY29tL3F2c3NsZzMuY3J0MCoGA1UdEQQjMCGCH3F2c3Ns\n"
            + "aWNhZzMtci5xdW92YWRpc2dsb2JhbC5jb20wUQYDVR0gBEowSDBGBgwrBgEEAb5Y\n"
            + "AAJkAQEwNjA0BggrBgEFBQcCARYoaHR0cDovL3d3dy5xdW92YWRpc2dsb2JhbC5j\n"
            + "b20vcmVwb3NpdG9yeTAOBgNVHQ8BAf8EBAMCBaAwHQYDVR0lBBYwFAYIKwYBBQUH\n"
            + "AwEGCCsGAQUFBwMCMB8GA1UdIwQYMBaAFLMSibWpSzW8FQDwgOnYeIfxE3x2MDoG\n"
            + "A1UdHwQzMDEwL6AtoCuGKWh0dHA6Ly9jcmwucXVvdmFkaXNnbG9iYWwuY29tL3F2\n"
            + "c3NsZzMuY3JsMB0GA1UdDgQWBBSS2t3Itp/XsAppEeGyH+Ew8vEQ0zANBgkqhkiG\n"
            + "9w0BAQsFAAOCAgEAo8MJ2ek95Cs3chn1ecEMdGkUANnCBmgdvQjFt6XVLzYWs37n\n"
            + "j6Ac/nGj+Tzb30nTVdE2laRuTeLuGfYmd1AdBLHuRhWYG6A6jnlzqhmDRL3fvRYk\n"
            + "wjeWQn6Kx/lOoWC1xOa2EJYOWDr/rUY2PKo9rSVdGKmU6NFI/+FOFLaUD8tU77Qq\n"
            + "p9rfOYJwekckA2I2891lTRbnJfQhPD8mQjttd+nS46RwZxxAI5Pr6Jcr+BG3ARP5\n"
            + "oM/ifTCLXCc4L/0nozUDSweU17TCuFXWGEpIXbOVmE3kpmHaVe1FRQ0PUr2XHCbJ\n"
            + "H1vumQcJmOaUxiB4EzP+M+HnKg6rwhWlfgQEAnCcKkMF5ei1NAaHCwhRMC7ijJFA\n"
            + "Wt7s0/PpP2tChU7uXctMk2d36Dpibyn6Rc5a/QJX444ZRFEGrSe4nO/MXt3iEcat\n"
            + "fgYHOWoBunLIm7x/fd611xvyhifjqKVCPpqodpwFrXOlCQhXehhRvJSPDXWgDJeR\n"
            + "cDLLIcit4Sn1uyebQcZafaxgYPWpaYHFd7dzkO+kTVqOVAm7LukC5QQ9qFY1z7a5\n"
            + "IDUAFtEYg/c4XxX+pReOxydnnHeYZBrfRTTxOfMrg6dxsb1QcOeElXHgXRpHyiMh\n"
            + "XYsZWE2WHT7of4wMfNzCUrVSN0tCGDRW0MI48RM4BYbRnz3YNKafjnszeXI=\n"
            + "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator, boolean ocspEnabled)
            throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        if (ocspEnabled) {
            // Revoked certificates are expired in Nov 2014
            // and backdated revocation check is only possible with OCSP
            pathValidator.setValidationDate("Jan 01, 2014");
        }

        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Fri Jan 04 03:49:46 PST 2013", System.out);

        // reset validation date back to current date
        pathValidator.resetValidationDate();
    }
}

class RootCA3 {

    // wner: CN=QuoVadis Issuing CA 3 G3, O=QuoVadis Limited, C=BM
    private static final String INT = "-----BEGIN CERTIFICATE-----\n"
            + "MIIGFTCCA/2gAwIBAgIUHZxbikClihb+1PM2ck0SDEZ8/dIwDQYJKoZIhvcNAQEL\n"
            + "BQAwSDELMAkGA1UEBhMCQk0xGTAXBgNVBAoTEFF1b1ZhZGlzIExpbWl0ZWQxHjAc\n"
            + "BgNVBAMTFVF1b1ZhZGlzIFJvb3QgQ0EgMyBHMzAeFw0xMjExMDYxODM5MDVaFw0y\n"
            + "MjExMDYxODM5MDVaMEsxCzAJBgNVBAYTAkJNMRkwFwYDVQQKExBRdW9WYWRpcyBM\n"
            + "aW1pdGVkMSEwHwYDVQQDExhRdW9WYWRpcyBJc3N1aW5nIENBIDMgRzMwggIiMA0G\n"
            + "CSqGSIb3DQEBAQUAA4ICDwAwggIKAoICAQCs6x3rpBdA1tTXgPYNjL1MKHuoDyb9\n"
            + "d4mxxk41t5Cvo3BnS0/cBlRIl91oqu3Iv9goVCMStla+GW9iRdX/53jYM1rXePDa\n"
            + "OnE/MJNLcVjmABZmEUtpzxUYLftwGEg1w/Wgi6z68vqZn7vbHJtFV8inlMIsdBVY\n"
            + "o3VmU9h+pGZU8JrF8x8U3voX4vm56OBCAM/1osUGXsVL2AY3z2Gjyb1Hv6fqHma7\n"
            + "PWrWV1hYS/EAnRUPO8iQqJwrbT/j7Mlo3khULV+T02M+oqs1ckIihl38n1eGvYcp\n"
            + "z40cceA2Ej5aglyF9i+ypA4XnxKF3f+6vvEYRPCMQB8Hiwuyy6naj6lPoLZ+nolT\n"
            + "t++xSkZ5imAoTXewA9JxyGCdiO9G4sZFIy4jjW7HBmKx6pZy3wWf48eawXPIpjop\n"
            + "EC+Kayf3foeyq40CAOjysVkblhUBawvVjAqKJ5aoKD4Ghnv02jdVvI4W7ME/fYYb\n"
            + "gm+XD7KJv4gHks+SIV93eXiUhYHvofJ3AG/1kp1p4tvIKCUtm2LCihmp53n9uLGA\n"
            + "NvizEnkuQmwlXtqOquKDluwSpHVFPxePMdRICUOnoZBdHv6f3LQCOU7AczRJYh8+\n"
            + "WYSKQy675/Itucgd+ABfY1H07F4FisCP75j8YknBdv4nfQsb0RcTg2P89dJNwAhL\n"
            + "rpk452WD4LuvsQIDAQABo4HzMIHwMBIGA1UdEwEB/wQIMAYBAf8CAQEwEQYDVR0g\n"
            + "BAowCDAGBgRVHSAAMDoGCCsGAQUFBwEBBC4wLDAqBggrBgEFBQcwAYYeaHR0cDov\n"
            + "L29jc3AucXVvdmFkaXNnbG9iYWwuY29tMA4GA1UdDwEB/wQEAwIBBjAfBgNVHSME\n"
            + "GDAWgBTGF9C8qOoCQ/IbBpldK5Agudec5DA7BgNVHR8ENDAyMDCgLqAshipodHRw\n"
            + "Oi8vY3JsLnF1b3ZhZGlzZ2xvYmFsLmNvbS9xdnJjYTNnMy5jcmwwHQYDVR0OBBYE\n"
            + "FBPyQfSNOURHBZ37q8ZaNQwelu9eMA0GCSqGSIb3DQEBCwUAA4ICAQAsbJU89ZB9\n"
            + "1XVlOLmw8MaoWwOgI3DwM/g30YyIV1SERtDMKDOUnLVGTORTGv7Y8X789nGkMbKq\n"
            + "OEEa9Hty4jwyTnt2OISpCAb4GwBtH+FxNcLkJwZU2qtpTX8zDndofE/JLGo0rte5\n"
            + "bKchF2JTg+oby/Wpu2IO0CMd1phou3LLi8sWQGcY/f5vk+MUDnskH6NRXte4m8HW\n"
            + "FtYb7nOgLzY5FOJDtQuUFFioNoQzUHuj3SpUjIBxXf4VRFXz+FKIQ4jqzD/SnHG6\n"
            + "/7g/28x66LNpYjvaQ0T45EqxqPDCztfJO67GsNLXeSKq+BteqXcnKI77ZkqmwnWl\n"
            + "cYt5qek0GBYRYVOM8dUIvDryWHZIEqbeI0DAu06dyPuvIJNQ6WweqxJ+hH++BqGh\n"
            + "P4bViNNuP/Lqarb1RP7JiJW3wlyIUDD34JLzkusBgU++ptdYg1o0VnEB8KWDG8Of\n"
            + "cABL+TMoUldUp9DFFgFJIfnPX5XjXyG9mw2wwiUvClo93qFvC8+rhEGeZFd29rKi\n"
            + "dmmCc8FaCfBV9XdHHx/0ORTQp3HxnRDDiz+MN7p1Y4SXbHE3XXyQAUVTISGpPe3X\n"
            + "TUhmoARNmBBPALDm3EAvEBikTUMBFGR63wtu0pjA2cF5nvOyY8mBSsNk0R6+ZJSl\n"
            + "Cok3lH5oBM2H+KBk+sNZIBQ8BHcgbwlghg==\n"
            + "-----END CERTIFICATE-----";

    // Owner: CN=qvica3g3-v.quovadisglobal.com, O=QuoVadis Limited, L=Hamilton,
    // ST=Pembroke, C=BM
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n"
            + "MIIF9DCCA9ygAwIBAgIUTkK7g7zoijtiLY/YV9ASX+pEsx0wDQYJKoZIhvcNAQEL\n"
            + "BQAwSzELMAkGA1UEBhMCQk0xGTAXBgNVBAoTEFF1b1ZhZGlzIExpbWl0ZWQxITAf\n"
            + "BgNVBAMTGFF1b1ZhZGlzIElzc3VpbmcgQ0EgMyBHMzAeFw0xNDExMTQxNDA1NTJa\n"
            + "Fw0xNzExMTQxNDA1MzhaMHYxCzAJBgNVBAYTAkJNMREwDwYDVQQIEwhQZW1icm9r\n"
            + "ZTERMA8GA1UEBxMISGFtaWx0b24xGTAXBgNVBAoTEFF1b1ZhZGlzIExpbWl0ZWQx\n"
            + "JjAkBgNVBAMTHXF2aWNhM2czLXYucXVvdmFkaXNnbG9iYWwuY29tMIIBIjANBgkq\n"
            + "hkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAosZjGbZtvAM45zdlTtT+uL12F5nebQrE\n"
            + "F9Fb8z1uhRJKgXAfjlfsMIjv7Xc7F80Li39yO0CmWHTMJS41auktW8IGVEkVV2og\n"
            + "EL7SKLjtgDJ1I3HAX02hfuOW0b/jkfPEcqTeZVE5Xew/HTAuTJMTqCEHM5hFieWL\n"
            + "tADPm7kANu5q6HaFXndKN/k1ozZXQn9YNTpDvvH6oD0Kqn/Peezi+C+6asTMSCk0\n"
            + "Xoi2TBHNi9dl2tfb6hu+T5VFwFsC9dGqYt07V8TbvKRAVV0MC8DnXnS89quFVmPS\n"
            + "I3ZSKeU4dlp8FzmTrd5nk3y9GL8GTkCsSN3RZbeAbLCpzcG5weS3GQIDAQABo4IB\n"
            + "ozCCAZ8wdAYIKwYBBQUHAQEEaDBmMCoGCCsGAQUFBzABhh5odHRwOi8vb2NzcC5x\n"
            + "dW92YWRpc2dsb2JhbC5jb20wOAYIKwYBBQUHMAKGLGh0dHA6Ly90cnVzdC5xdW92\n"
            + "YWRpc2dsb2JhbC5jb20vcXZpY2EzZzMuY3J0MCgGA1UdEQQhMB+CHXF2aWNhM2cz\n"
            + "LXYucXVvdmFkaXNnbG9iYWwuY29tMFEGA1UdIARKMEgwRgYMKwYBBAG+WAACZAEB\n"
            + "MDYwNAYIKwYBBQUHAgEWKGh0dHA6Ly93d3cucXVvdmFkaXNnbG9iYWwuY29tL3Jl\n"
            + "cG9zaXRvcnkwDgYDVR0PAQH/BAQDAgWgMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggr\n"
            + "BgEFBQcDAjAfBgNVHSMEGDAWgBQT8kH0jTlERwWd+6vGWjUMHpbvXjA7BgNVHR8E\n"
            + "NDAyMDCgLqAshipodHRwOi8vY3JsLnF1b3ZhZGlzZ2xvYmFsLmNvbS9xdmljYTNn\n"
            + "My5jcmwwHQYDVR0OBBYEFINCE86z3wESNeL4rz3eiaYA5LIWMA0GCSqGSIb3DQEB\n"
            + "CwUAA4ICAQBPe+Y5xDGZLYaVNOxxiyqFZrntGJGGQW1w4GtEfkH9oD8WGs5kBhMM\n"
            + "/XPGqw2FzzrvA5GfSdh+EMuXUfJY933AxwPcNfwGHzYGAHIDFsW17y5ZdKfBMN4Y\n"
            + "82e13iSfHQrbI0P6l8IIExfCw4HC8PxuEalg6H9fj9/1Q7mzdpwT3uG/HP6Dr2z+\n"
            + "PGYFMaH77MsOjfANT8UIdo5SAyXiJI5Y0cyKjuXhR6eJEKwNfri27UaV5cJJuV7I\n"
            + "fRcjb0h0Grr6gpKFb7JnhDZVGR3fDHTzuybuCqZk9TYKQ2sn1YfBFDqDpWODpykt\n"
            + "vyFO7eugpvSUgdTKRMPCtyppYgo2RwIsMmLrU4wPzdnPi8oo+cM0f5zXrmrkOLY0\n"
            + "PZo+K8QT/SrNT+9yZnHupLy01aYGJ4RJ047Wthr7a9S6i6DxbQ+ps4Ajh0X1bvOK\n"
            + "KCEKq5aoivYQMLn8pjudiMjbnKU4mgpmZK15D6lLmAprW3L6F8AEBJsK1BunPWhJ\n"
            + "nkQUyBnFgq2epmDfZ4f6SztNoLfDbatYNRb2KJfW1lks7UHDjuZ4PM20KkmmFJEE\n"
            + "LKR76WJzKi/+aks/csdFD7+/TMXrkY+JWlT4mCoHR1ol0m3DiqApKvRFZkMARfJq\n"
            + "npjt2cXyzDnguyQuLrHhdkKW+/LYeNckmVX+cPIxShLbuVhqMgdnWg==\n"
            + "-----END CERTIFICATE-----";

    // Owner: CN=qvica3g3-r.quovadisglobal.com, O=QuoVadis Limited, L=Hamilton,
    // ST=Pembroke, C=BM
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n"
            + "MIIF9DCCA9ygAwIBAgIUSTXTLsMPxg4n9YY6GASBcJsgcaEwDQYJKoZIhvcNAQEL\n"
            + "BQAwSzELMAkGA1UEBhMCQk0xGTAXBgNVBAoTEFF1b1ZhZGlzIExpbWl0ZWQxITAf\n"
            + "BgNVBAMTGFF1b1ZhZGlzIElzc3VpbmcgQ0EgMyBHMzAeFw0xMjExMTQxMzQ4MTda\n"
            + "Fw0xNDExMTQxMzQ4MTdaMHYxCzAJBgNVBAYTAkJNMREwDwYDVQQIEwhQZW1icm9r\n"
            + "ZTERMA8GA1UEBxMISGFtaWx0b24xGTAXBgNVBAoTEFF1b1ZhZGlzIExpbWl0ZWQx\n"
            + "JjAkBgNVBAMTHXF2aWNhM2czLXIucXVvdmFkaXNnbG9iYWwuY29tMIIBIjANBgkq\n"
            + "hkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtaZUVAvasDtoFhZqL2fH+rI/IKeY0zj7\n"
            + "hGuYpLlT32JZX8cmkWUywZt6VxA8A5o82Ay0xT9vHy4MPnmmZExEvmkaECBmOh6+\n"
            + "WzWydYGKeeheUERJ1hLj2T7MKz/CCFY6NxD9XzvYOyhDpCUQKCOx4LMn0nMFrXrS\n"
            + "6IVirDUmH26dpl3IfsdVXyn6N3wLSNf+UX7in/PXsfD/A6RVtqYsfx4fxFJIPIhv\n"
            + "XG/cDOVIyfq6Oo1hthzGm8cnOSjvK/UfQV5iVBK68rqoGG+r9uBG9BfZtd7o0wrf\n"
            + "SSJkJAPJVpWTLvnD8RYpJIBz01vNgEOCEgF54bvjhBOjx15mrH7roQIDAQABo4IB\n"
            + "ozCCAZ8wdAYIKwYBBQUHAQEEaDBmMCoGCCsGAQUFBzABhh5odHRwOi8vb2NzcC5x\n"
            + "dW92YWRpc2dsb2JhbC5jb20wOAYIKwYBBQUHMAKGLGh0dHA6Ly90cnVzdC5xdW92\n"
            + "YWRpc2dsb2JhbC5jb20vcXZpY2EzZzMuY3J0MCgGA1UdEQQhMB+CHXF2aWNhM2cz\n"
            + "LXIucXVvdmFkaXNnbG9iYWwuY29tMFEGA1UdIARKMEgwRgYMKwYBBAG+WAACZAEB\n"
            + "MDYwNAYIKwYBBQUHAgEWKGh0dHA6Ly93d3cucXVvdmFkaXNnbG9iYWwuY29tL3Jl\n"
            + "cG9zaXRvcnkwDgYDVR0PAQH/BAQDAgWgMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggr\n"
            + "BgEFBQcDAjAfBgNVHSMEGDAWgBQT8kH0jTlERwWd+6vGWjUMHpbvXjA7BgNVHR8E\n"
            + "NDAyMDCgLqAshipodHRwOi8vY3JsLnF1b3ZhZGlzZ2xvYmFsLmNvbS9xdmljYTNn\n"
            + "My5jcmwwHQYDVR0OBBYEFLnaKDrPemoRtOZaUReSV5rWp3OoMA0GCSqGSIb3DQEB\n"
            + "CwUAA4ICAQA+B+R1TDmE4jC6itHBMPgqRoETJxtTdKyp6/egk5My4MATXRCSrStA\n"
            + "gp1c86hljmlN2gq05HKlAz9cC4W80pypJGfEbhYIi9B4Jxdo6zJNJqcFz3zj/otx\n"
            + "hvZ2nOO5qqEupAP8aHju0LhUlkcFQlbqaA+IiuQUh0VFQxk8LwkKEA8oIib7wLie\n"
            + "P1zBMXeRyDM5CnFWQmIFKXR4+9f51Dfv40Gy2RKQT7I8oXuADhrG9iXFJPXz4yYK\n"
            + "LazlDjnn0wv4vB9BmlcVdM2HPYqIPdvWBtPxT9vpNYHnB9Dq/zGqKJNUh8I4jB9k\n"
            + "8iQYJgoj62mQW2o1fObkVwrGgglAyzUzUzJfJyy9OEECjLY5o/9TJAKBAnewJ5B9\n"
            + "PagYo+klH937s2MOLqzl/uvbjXUBBvql1UU/lb8tSK9xCaXMEDhgiVricr13k32y\n"
            + "XmUcA/im96CI5cF5i4xHMnqprzPehFB/Mmi6g2tpiE0bmLkYj7MMJcmtUowa3FqA\n"
            + "QHtqKrK8wOfHep6qPx6VMD6Ypaf6yq66/kkSg05i6VO7V371UTibHeVLTr7LPRQJ\n"
            + "Emp8k/6qCXOtf5OdXwHBIDqvszf8ry85Rl3q813TntF0pPRvqLEYadC4Bwq7Snf+\n"
            + "PR0MPNhuwZBCmxZcyZqhVG2PyvvEmhPxhEdbO5DWUFwUP17WHNlgeQ==\n"
            + "-----END CERTIFICATE-----";

    public void runTest(ValidatePathWithParams pathValidator, boolean ocspEnabled)
            throws Exception {
        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        if (ocspEnabled) {
            // Revoked certificates are expired in Nov 2014
            // and backdated revocation check is only possible with OCSP
            pathValidator.setValidationDate("Jan 01, 2014");
        }

        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Thu Jan 03 23:47:02 PST 2013", System.out);

        // reset validation date back to current date
        pathValidator.resetValidationDate();
    }
}
