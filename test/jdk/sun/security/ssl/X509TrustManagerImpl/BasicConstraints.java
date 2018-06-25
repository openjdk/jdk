/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

//
// SunJSSE does not support dynamic system properties, no way to re-use
// system properties in samevm/agentvm mode.
//

/*
 * @test
 * @bug 7166570
 * @summary JSSE certificate validation has started to fail for
 *     certificate chains
 * @run main/othervm BasicConstraints PKIX
 * @run main/othervm BasicConstraints SunX509
 */

import java.net.*;
import java.util.*;
import java.io.*;
import javax.net.ssl.*;
import java.security.Security;
import java.security.KeyStore;
import java.security.KeyFactory;
import java.security.cert.*;
import java.security.spec.*;
import java.security.interfaces.*;
import java.math.BigInteger;

import java.util.Base64;

public class BasicConstraints {

    /*
     * =============================================================
     * Set the various variables needed for the tests, then
     * specify what tests to run on each side.
     */

    /*
     * Should we run the client or server in a separate thread?
     * Both sides can throw exceptions, but do you have a preference
     * as to which side should be the main thread.
     */
    static boolean separateServerThread = true;

    /*
     * Where do we find the keystores?
     */
    // Certificate information:
    // Issuer: C=US, O=Java, OU=SunJSSE Test Serivce
    // Validity
    //     Not Before: May  5 02:40:50 2012 GMT
    //     Not After : Apr 15 02:40:50 2033 GMT
    // Subject: C=US, O=Java, OU=SunJSSE Test Serivce
    // X509v3 Subject Key Identifier:
    //     DD:4E:8D:2A:11:C0:83:03:F0:AC:EB:A2:BF:F9:F2:7D:C8:69:1F:9B
    // X509v3 Authority Key Identifier:
    //     keyid:DD:4E:8D:2A:11:C0:83:03:F0:AC:EB:A2:BF:F9:F2:7D:C8:69:1F:9B
    //     DirName:/C=US/O=Java/OU=SunJSSE Test Serivce
    //     serial:00
    static String trusedCertStr =
        "-----BEGIN CERTIFICATE-----\n" +
        "MIICkjCCAfugAwIBAgIBADANBgkqhkiG9w0BAQIFADA7MQswCQYDVQQGEwJVUzEN\n" +
        "MAsGA1UEChMESmF2YTEdMBsGA1UECxMUU3VuSlNTRSBUZXN0IFNlcml2Y2UwHhcN\n" +
        "MTIwNTA1MDI0MDUwWhcNMzMwNDE1MDI0MDUwWjA7MQswCQYDVQQGEwJVUzENMAsG\n" +
        "A1UEChMESmF2YTEdMBsGA1UECxMUU3VuSlNTRSBUZXN0IFNlcml2Y2UwgZ8wDQYJ\n" +
        "KoZIhvcNAQEBBQADgY0AMIGJAoGBANtiq0AIJK+iVRwFrqcD7fYXTCbMYC5Qz/k6\n" +
        "AXBy7/1rI8wDhEJLE3m/+NSqiJwZcmdq2dNh/1fJFrwvzuURbc9+paOBWeHbN+Sc\n" +
        "x3huw91oPZme385VpoK3G13rSE114S/rF4DM9mz4EStFhSHXATjtdbskNOAYGLTV\n" +
        "x8uEy9GbAgMBAAGjgaUwgaIwHQYDVR0OBBYEFN1OjSoRwIMD8Kzror/58n3IaR+b\n" +
        "MGMGA1UdIwRcMFqAFN1OjSoRwIMD8Kzror/58n3IaR+boT+kPTA7MQswCQYDVQQG\n" +
        "EwJVUzENMAsGA1UEChMESmF2YTEdMBsGA1UECxMUU3VuSlNTRSBUZXN0IFNlcml2\n" +
        "Y2WCAQAwDwYDVR0TAQH/BAUwAwEB/zALBgNVHQ8EBAMCAQYwDQYJKoZIhvcNAQEC\n" +
        "BQADgYEAjjkJesQrkbr36N40egybaIxw7RcqT6iy5fkAGS1JYlBDk8uSCK1o6bCH\n" +
        "ls5EpYcGeEoabSS73WRdkO1lgeyWDduO4ef8cCCSpmpT6/YdZG0QS1PtcREeVig+\n" +
        "Zr25jNemS4ADHX0aaXP4kiV/G80cR7nX5t5XCUm4bYdbwM07NgI=\n" +
        "-----END CERTIFICATE-----";
    static String trustedPrivateKey = // Private key in the format of PKCS#8
        "MIICeAIBADANBgkqhkiG9w0BAQEFAASCAmIwggJeAgEAAoGBANtiq0AIJK+iVRwF\n" +
        "rqcD7fYXTCbMYC5Qz/k6AXBy7/1rI8wDhEJLE3m/+NSqiJwZcmdq2dNh/1fJFrwv\n" +
        "zuURbc9+paOBWeHbN+Scx3huw91oPZme385VpoK3G13rSE114S/rF4DM9mz4EStF\n" +
        "hSHXATjtdbskNOAYGLTVx8uEy9GbAgMBAAECgYEA2VjHkIiA0ABjkX+PqKeb+VLb\n" +
        "fxS7tSca5C8zfdRhLxAWRui0/3ihst0eCJNrBDuxvAOACovsDWyLuaUjtI2v2ysz\n" +
        "vz6SPyGy82PhQOFzyKQuQ814N6EpothpiZzF0yFchfKIGhUsdY89UrGs9nM7m6NT\n" +
        "rztYvgIu4avg2VPR2AECQQD+pFAqipR2BplQRIuuRSZfHRxvoEyDjT1xnHJsC6WP\n" +
        "I5hCLghL91MhQGWbP4EJMKYQOTRVukWlcp2Kycpf+P5hAkEA3I43gmVUAPEdyZdY\n" +
        "fatW7OaLlbbYJb6qEtpCZ1Rwe/BIvm6H6E3qSi/lpz7Ia7WDulpbF6BawHH3pRFq\n" +
        "CUY5ewJBAP3pUDqrRpBN0jB0uSeDslhjSciQ+dqvSpZv3rSYBHUvlBJhnkpJiy37\n" +
        "7ZUZhIxqYxyIPgRBolLwb+FFh7OdL+ECQCtldDic9WVmC+VheRDpCKZ+SlK/8lGi\n" +
        "7VXeShiIvcU1JysJFoa35fSI7hf1O3wt7+hX5PqGG7Un94EsJwACKEcCQQC1TWt6\n" +
        "ArKH6tRxKjOxFtqfs8fgEVYUaOr3j1jF4KBUuX2mtQtddZe3VfJ2wPsuKMMxmhkB\n" +
        "e7xWWZnJsErt2e+E";

    // Certificate information:
    // Issuer: C=US, O=Java, OU=SunJSSE Test Serivce
    // Validity
    //     Not Before: May  5 02:40:53 2012 GMT
    //     Not After : Jan 21 02:40:53 2032 GMT
    // Subject: C=US, O=Java, OU=SunJSSE Test Serivce, CN=casigner
    // X509v3 Subject Key Identifier:
    //     13:07:E0:11:07:DB:EB:33:23:87:31:D0:DB:7E:16:56:BE:11:90:0A
    // X509v3 Authority Key Identifier:
    //     keyid:DD:4E:8D:2A:11:C0:83:03:F0:AC:EB:A2:BF:F9:F2:7D:C8:69:1F:9B
    //     DirName:/C=US/O=Java/OU=SunJSSE Test Serivce
    //     serial:00
    static String caSignerStr =
        "-----BEGIN CERTIFICATE-----\n" +
        "MIICqDCCAhGgAwIBAgIBAjANBgkqhkiG9w0BAQQFADA7MQswCQYDVQQGEwJVUzEN\n" +
        "MAsGA1UEChMESmF2YTEdMBsGA1UECxMUU3VuSlNTRSBUZXN0IFNlcml2Y2UwHhcN\n" +
        "MTIwNTA1MDI0MDUzWhcNMzIwMTIxMDI0MDUzWjBOMQswCQYDVQQGEwJVUzENMAsG\n" +
        "A1UEChMESmF2YTEdMBsGA1UECxMUU3VuSlNTRSBUZXN0IFNlcml2Y2UxETAPBgNV\n" +
        "BAMTCGNhc2lnbmVyMIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC+x8+o7oM0\n" +
        "ct/LZmZLXBL4CQ8jrULD5P7NtEW0hg/zxBFZfBHf+44Oo2eMPYZj+7xaREOH5BmV\n" +
        "KRYlzRtONAaC5Ng4Mrm5UKNPcMIIUjUOvm7vWM4oSTMSfoEcSX+vp99uUAkw3w7Z\n" +
        "+frYDm1M4At/j0b+lLij71GFN2L8drpgPQIDAQABo4GoMIGlMB0GA1UdDgQWBBQT\n" +
        "B+ARB9vrMyOHMdDbfhZWvhGQCjBjBgNVHSMEXDBagBTdTo0qEcCDA/Cs66K/+fJ9\n" +
        "yGkfm6E/pD0wOzELMAkGA1UEBhMCVVMxDTALBgNVBAoTBEphdmExHTAbBgNVBAsT\n" +
        "FFN1bkpTU0UgVGVzdCBTZXJpdmNlggEAMBIGA1UdEwEB/wQIMAYBAf8CAQEwCwYD\n" +
        "VR0PBAQDAgEGMA0GCSqGSIb3DQEBBAUAA4GBAI+LXA/UCPkTANablUkt80JNPWsl\n" +
        "pS4XLNgPxWaN0bkRDs5oI4ooWAz1rwpeJ/nfetOvWlpmrVjSeovBFja5Hl+dUHTf\n" +
        "VfuyzkxXbhuNiJIpo1mVBpNsjwu9YRxuwX6UA2LTUQpgvtVJEE012x3zRvxBCbu2\n" +
        "Y/v1R5fZ4c+hXDfC\n" +
        "-----END CERTIFICATE-----";
    static String caSignerPrivateKey = // Private key in the format of PKCS#8
        "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAL7Hz6jugzRy38tm\n" +
        "ZktcEvgJDyOtQsPk/s20RbSGD/PEEVl8Ed/7jg6jZ4w9hmP7vFpEQ4fkGZUpFiXN\n" +
        "G040BoLk2DgyublQo09wwghSNQ6+bu9YzihJMxJ+gRxJf6+n325QCTDfDtn5+tgO\n" +
        "bUzgC3+PRv6UuKPvUYU3Yvx2umA9AgMBAAECgYBYvu30cW8LONyt62Zua9hPFTe7\n" +
        "qt9B7QYyfkdmoG5PQMepTrOp84SzfoOukvgvDm0huFuJnSvhXQl2cCDhkgXskvFj\n" +
        "Hh7KBCFViVXokGdq5YoS0/KYMyQV0TZfJUvILBl51uc4/siQ2tClC/N4sa+1JhgW\n" +
        "a6dFGfRjiUKSSlmMwQJBAPWpIz3Q/c+DYMvoQr5OD8EaYwYIevlTdXb97RnJJh2b\n" +
        "UnhB9jrqesJiHYVzPmP0ukyPOXOwlp2T5Am4Kw0LFOkCQQDGz150NoHOp28Mvyc4\n" +
        "CTqz/zYzUhy2eCJESl196uyP4N65Y01VYQ3JDww4DlsXiU17tVSbgA9TCcfTYOzy\n" +
        "vyw1AkARUky+1hafZCcWGZljK8PmnMKwsTZikCTvL/Zg5BMA8Wu+OQBwpQnk3OAy\n" +
        "Aa87gw0DyvGFG8Vy9POWT9sRP1/JAkBqP0hrMvYMSs6+MSn0eHo2151PsAJIQcuO\n" +
        "U2/Da1khSzu8N6WMi2GiobgV/RYRbf9KrY2ZzMZjykZQYOxAjopBAkEAghCu38cN\n" +
        "aOsW6ueo24uzsWI1FTdE+qWNVEi3RSP120xXBCyhaBjIq4WVSlJK9K2aBaJpit3j\n" +
        "iQ5tl6zrLlxQhg==";

    // Certificate information:
    // Issuer: C=US, O=Java, OU=SunJSSE Test Serivce, CN=casigner
    // Validity
    //     Not Before: May  5 02:40:57 2012 GMT
    //     Not After : Jan 21 02:40:57 2032 GMT
    // Subject: C=US, O=Java, OU=SunJSSE Test Serivce, CN=certissuer
    // X509v3 Subject Key Identifier:
    //     39:0E:C6:33:B1:50:BC:73:07:31:E5:D8:04:F7:BB:97:55:CF:9B:C8
    // X509v3 Authority Key Identifier:
    //     keyid:13:07:E0:11:07:DB:EB:33:23:87:31:D0:DB:7E:16:56:BE:11:90:0A
    //     DirName:/C=US/O=Java/OU=SunJSSE Test Serivce
    //     serial:02
    static String certIssuerStr =
        "-----BEGIN CERTIFICATE-----\n" +
        "MIICvjCCAiegAwIBAgIBAzANBgkqhkiG9w0BAQQFADBOMQswCQYDVQQGEwJVUzEN\n" +
        "MAsGA1UEChMESmF2YTEdMBsGA1UECxMUU3VuSlNTRSBUZXN0IFNlcml2Y2UxETAP\n" +
        "BgNVBAMTCGNhc2lnbmVyMB4XDTEyMDUwNTAyNDA1N1oXDTMyMDEyMTAyNDA1N1ow\n" +
        "UDELMAkGA1UEBhMCVVMxDTALBgNVBAoTBEphdmExHTAbBgNVBAsTFFN1bkpTU0Ug\n" +
        "VGVzdCBTZXJpdmNlMRMwEQYDVQQDEwpjZXJ0aXNzdWVyMIGfMA0GCSqGSIb3DQEB\n" +
        "AQUAA4GNADCBiQKBgQCyz55zinU6kNL/LeiTNiBI0QWYmDG0YTotuC4D75liBNqs\n" +
        "7Mmladsh2mTtQUAwmuGaGzaZV25a+cUax0DXZoyBwdbTI09u1bUYsZcaUUKbPoCC\n" +
        "HH26e4jLFL4olW13Sv4ZAd57tIYevMw+Fp5f4fLPFGegCJTFlv2Qjpmic/cuvQID\n" +
        "AQABo4GpMIGmMB0GA1UdDgQWBBQ5DsYzsVC8cwcx5dgE97uXVc+byDBjBgNVHSME\n" +
        "XDBagBQTB+ARB9vrMyOHMdDbfhZWvhGQCqE/pD0wOzELMAkGA1UEBhMCVVMxDTAL\n" +
        "BgNVBAoTBEphdmExHTAbBgNVBAsTFFN1bkpTU0UgVGVzdCBTZXJpdmNlggECMBMG\n" +
        "A1UdEwEB/wQJMAcBAf8CAgQAMAsGA1UdDwQEAwIBBjANBgkqhkiG9w0BAQQFAAOB\n" +
        "gQCQTagenCdClT98C+oTJGJrw/dUBD9K3tE6ZJKPMc/2bUia8G5ei1C0eXj4mWG2\n" +
        "lu9umR6C90/A6qB050QB2h50qtqxSrkpu+ym1yypauZpg7U3nUY9wZWJNI1vqrQZ\n" +
        "pqUMRcXY3iQIVKx+Qj+4/Za1wwFQzpEoGmqRW31V1SdMEw==\n" +
        "-----END CERTIFICATE-----";
    static String certIssuerPrivateKey = // Private key in the format of PKCS#8
        "MIICeQIBADANBgkqhkiG9w0BAQEFAASCAmMwggJfAgEAAoGBALLPnnOKdTqQ0v8t\n" +
        "6JM2IEjRBZiYMbRhOi24LgPvmWIE2qzsyaVp2yHaZO1BQDCa4ZobNplXblr5xRrH\n" +
        "QNdmjIHB1tMjT27VtRixlxpRQps+gIIcfbp7iMsUviiVbXdK/hkB3nu0hh68zD4W\n" +
        "nl/h8s8UZ6AIlMWW/ZCOmaJz9y69AgMBAAECgYEAjtew2tgm4gxDojqIauF4VPM1\n" +
        "pzsdqd1p3pAdomNLgrQiBLZ8N7oiph6TNb1EjA+OXc+ThFgF/oM9ZDD8qZZwcvjN\n" +
        "qDZlpTkFs2TaGcyEZfUaMB45NHVs6Nn+pSkagSNwwy3xeyAct7sQEzGNTDlEwVv5\n" +
        "7V9LQutQtBd6xT48KzkCQQDpNRfv2OFNG/6GtzJoO68oJhpnpl2MsYNi4ntRkre/\n" +
        "6uXpiCYaDskcrPMRwOOs0m7mxG+Ev+uKnLnSoEMm1GCbAkEAxEmDtiD0Psb8Z9BL\n" +
        "ZRb83Jqho3xe2MCAh3xUfz9b/Mhae9dZ44o4OCgQZuwvW1mczF0NtpgZl93BmYa2\n" +
        "hTwHhwJBAKHrEj6ep/fA6x0gD2idoATRR94VfbiU+7NpqtO9ecVP0+gsdr/66hn1\n" +
        "3yLBeZLh3MxvMTrLgkAQh1i9m0JXjOcCQQClLXAHHegrw+u3uNMZeKTFR+Lp3sk6\n" +
        "AZSnbvr0Me9I45kxSeG81x3ENALJecvIRbrrRws5MvmmkNhQR8rkh8WVAkEAk6b+\n" +
        "aVtmBgUaTS5+FFlHGHJY9HFrfT1a1C/dwyMuqlmbC3YsBmZaMOlKli5TXNybLff8\n" +
        "5KMeGEpXMzgC7AscGA==";

    // Certificate information:
    // Issuer: C=US, O=Java, OU=SunJSSE Test Serivce, CN=certissuer
    // Validity
    //     Not Before: May  5 02:41:01 2012 GMT
    //     Not After : Jan 21 02:41:01 2032 GMT
    // Subject: C=US, O=Java, OU=SunJSSE Test Serivce, CN=localhost
    // X509v3 Subject Key Identifier:
    //     AD:C0:2C:4C:E4:C2:2E:A1:BB:5D:92:BE:66:E0:4E:E0:0D:2F:11:EF
    // X509v3 Authority Key Identifier:
    //     keyid:39:0E:C6:33:B1:50:BC:73:07:31:E5:D8:04:F7:BB:97:55:CF:9B:C8
    static String serverCertStr =
        "-----BEGIN CERTIFICATE-----\n" +
        "MIICjTCCAfagAwIBAgIBBDANBgkqhkiG9w0BAQQFADBQMQswCQYDVQQGEwJVUzEN\n" +
        "MAsGA1UEChMESmF2YTEdMBsGA1UECxMUU3VuSlNTRSBUZXN0IFNlcml2Y2UxEzAR\n" +
        "BgNVBAMTCmNlcnRpc3N1ZXIwHhcNMTIwNTA1MDI0MTAxWhcNMzIwMTIxMDI0MTAx\n" +
        "WjBPMQswCQYDVQQGEwJVUzENMAsGA1UEChMESmF2YTEdMBsGA1UECxMUU3VuSlNT\n" +
        "RSBUZXN0IFNlcml2Y2UxEjAQBgNVBAMTCWxvY2FsaG9zdDCBnzANBgkqhkiG9w0B\n" +
        "AQEFAAOBjQAwgYkCgYEAvwaUd7wmBSKqycEstYLWD26vkU08DM39EtaT8wL9HnQ0\n" +
        "fgPblwBFI4zdLa2cuYXRZcFUb04N8nrkcpR0D6kkE+AlFAoRWrrZF80B7JTbtEK4\n" +
        "1PIeurihXvUT+4MpzGLOojIihMfvM4ufelblD56SInso4WFHm7t4qCln88J1gjkC\n" +
        "AwEAAaN4MHYwCwYDVR0PBAQDAgPoMB0GA1UdDgQWBBStwCxM5MIuobtdkr5m4E7g\n" +
        "DS8R7zAfBgNVHSMEGDAWgBQ5DsYzsVC8cwcx5dgE97uXVc+byDAnBgNVHSUEIDAe\n" +
        "BggrBgEFBQcDAQYIKwYBBQUHAwIGCCsGAQUFBwMDMA0GCSqGSIb3DQEBBAUAA4GB\n" +
        "AGfwcfdvEG/nSCiAn2MGbYHp34mgF3OA1SJLWUW0LvWJhwm2cn4AXlSoyvbwrkaB\n" +
        "IDDCwhJvvc0vUyL2kTx7sqVaFTq3mDs+ktlB/FfH0Pb+i8FE+g+7T42Iw/j0qxHL\n" +
        "YmgbrjBQf5WYN1AvBE/rrPt9aOtS3UsqtVGW574b0shW\n" +
        "-----END CERTIFICATE-----";
    static String serverPrivateKey = // Private key in the format of PKCS#8
        "MIICdAIBADANBgkqhkiG9w0BAQEFAASCAl4wggJaAgEAAoGBAL8GlHe8JgUiqsnB\n" +
        "LLWC1g9ur5FNPAzN/RLWk/MC/R50NH4D25cARSOM3S2tnLmF0WXBVG9ODfJ65HKU\n" +
        "dA+pJBPgJRQKEVq62RfNAeyU27RCuNTyHrq4oV71E/uDKcxizqIyIoTH7zOLn3pW\n" +
        "5Q+ekiJ7KOFhR5u7eKgpZ/PCdYI5AgMBAAECf3CscOYvFD3zNMnMJ5LomVqA7w3F\n" +
        "gKYM2jlCWAH+wU41PMEXhW6Lujw92jgXL1o+lERwxFzirVdZJWZwKgUSvzP1G0h3\n" +
        "fkucq1/UWnToK+8NSXNM/yS8hXbBgSEoJo5f7LKcIi1Ev6doBVofMxs+njzyWKbM\n" +
        "Nb7rOLHadghoon0CQQDgQzbzzSN8Dc1YmmylhI5v+0sQRHH0DL7D24k4Weh4vInG\n" +
        "EAbt4x8M7ZKEo8/dv0s4hbmNmAnJl93/RRxIyEqLAkEA2g87DiswSQam2pZ8GlrO\n" +
        "+w4Qg9mH8uxx8ou2rl0XlHzH1XiTNbkjfY0EZoL7L31BHFk9n11Fb2P85g6ws+Hy\n" +
        "ywJAM/xgyLNM/nzUlS128geAXUULaYH0SHaL4isJ7B4rXZGW/mrIsGxtzjlkNYsj\n" +
        "rGujrD6TfNc5rZmexIXowJZtcQJBAIww+pCzZ4mrgx5JXWQ8OZHiiu+ZrPOa2+9J\n" +
        "r5sOMpi+WGN/73S8oHqZbNjTINZ5OqEVJq8MchWZPQBTNXuQql0CQHEjUzzkCQa3\n" +
        "j6JTa2KAdqyvLOx0XF9zcc1gA069uNQI2gPUHS8V215z57f/gMGnDNhVfLs/vMKz\n" +
        "sFkVZ3zg7As=";

    // Certificate information:
    // Issuer: C=US, O=Java, OU=SunJSSE Test Serivce, CN=certissuer
    // Validity
    //     Not Before: May  5 02:41:02 2012 GMT
    //     Not After : Jan 21 02:41:02 2032 GMT
    // Subject: C=US, O=Java, OU=SunJSSE Test Serivce, CN=InterOp Tester
    // X509v3 Subject Key Identifier:
    //     57:7D:E2:33:33:60:DF:DD:5E:ED:81:3F:EB:F2:1B:59:7F:50:9C:99
    // X509v3 Authority Key Identifier:
    //     keyid:39:0E:C6:33:B1:50:BC:73:07:31:E5:D8:04:F7:BB:97:55:CF:9B:C8
    static String clientCertStr =
        "-----BEGIN CERTIFICATE-----\n" +
        "MIICaTCCAdKgAwIBAgIBBTANBgkqhkiG9w0BAQQFADBQMQswCQYDVQQGEwJVUzEN\n" +
        "MAsGA1UEChMESmF2YTEdMBsGA1UECxMUU3VuSlNTRSBUZXN0IFNlcml2Y2UxEzAR\n" +
        "BgNVBAMTCmNlcnRpc3N1ZXIwHhcNMTIwNTA1MDI0MTAyWhcNMzIwMTIxMDI0MTAy\n" +
        "WjBUMQswCQYDVQQGEwJVUzENMAsGA1UEChMESmF2YTEdMBsGA1UECxMUU3VuSlNT\n" +
        "RSBUZXN0IFNlcml2Y2UxFzAVBgNVBAMTDkludGVyT3AgVGVzdGVyMIGfMA0GCSqG\n" +
        "SIb3DQEBAQUAA4GNADCBiQKBgQC1pA71nDg1KhhnHjRdi/eVDUa7uFZAtN8R9huu\n" +
        "pTwFoyqSX8lDMz8jDawOMmaI9dVZLjTh3hnf4KBEqQOearFVz45yBOjlgPLBuI4F\n" +
        "D/ORhgmDaIu2NK+c1yj6YQlyiO0DPwh55GtPLVG3iuEpejU7gQyaMuTaddoXrO7s\n" +
        "xwzanQIDAQABo08wTTALBgNVHQ8EBAMCA+gwHQYDVR0OBBYEFFd94jMzYN/dXu2B\n" +
        "P+vyG1l/UJyZMB8GA1UdIwQYMBaAFDkOxjOxULxzBzHl2AT3u5dVz5vIMA0GCSqG\n" +
        "SIb3DQEBBAUAA4GBAHTgB5W7wnl7Jnb4wNQcb6JdR8FRHIdslcRfnReFfZBHZZux\n" +
        "ChpA1lf62KIzYohKoxQXXMul86vnVSHnXq5xctHEmxCBnALEnoAcCOv6wfWqEA7g\n" +
        "2rX+ydmu+0ArbqKhSOypZ7K3ame0UOJJ6HDxdsgBYJuotmSou4KKq9e8GF+d\n" +
        "-----END CERTIFICATE-----";
    static String clientPrivateKey = // Private key in the format of PKCS#8
        "MIICeAIBADANBgkqhkiG9w0BAQEFAASCAmIwggJeAgEAAoGBALWkDvWcODUqGGce\n" +
        "NF2L95UNRru4VkC03xH2G66lPAWjKpJfyUMzPyMNrA4yZoj11VkuNOHeGd/goESp\n" +
        "A55qsVXPjnIE6OWA8sG4jgUP85GGCYNoi7Y0r5zXKPphCXKI7QM/CHnka08tUbeK\n" +
        "4Sl6NTuBDJoy5Np12hes7uzHDNqdAgMBAAECgYEAjLwygwapXjfhdHQoqpp6F9iT\n" +
        "h3sKCVSaybXgOO75lHyZzZO9wv1/288KEm3mmBOxXEm6245UievnAYvaq/GKt93O\n" +
        "pj2zRefBzZjGbz0v84fmna/MN6zUUYX1PcVRMKWLx9HKKmQihzwoXdBX0o9PPXdi\n" +
        "LfzujNa/q8/mpI5PmEECQQDZwLSaL7OReWZTY4NoQuNzwhx5IKJUOtCFQfmHKZSW\n" +
        "wtXntZf+E5W9tGaDY5wjpq5cilKDAHdEAlFWxDe1PoE1AkEA1YuTBpctOLBfquFn\n" +
        "Y/S3lzGVlnIHDk3dj4bFglkoJ2bCdlwRNUyBSjAjBDcbYhper8S7GlEN5SiEdz9I\n" +
        "3OjIyQJBAKEPMgYhZjYhjxf6sQV7A/VpC9pj0u1uGzGVXNUmYisorUKXRHa/UbBh\n" +
        "MLnaAXE1Jh54iRMwUwbQmA0PUQ0T0EkCQQCcr6/umwhkWw2nHYK2Vf5LoudGn15M\n" +
        "AZg7UsEjVnXfC0hOfllmCT+ohs96rVCbWAv33lsHAUg3x9YChV3aMbf5AkAj1kuV\n" +
        "jUTgFKjediyQC6uof7YdLn+gQGiXK1XE0GBN4WMkzcLiS0jC+MFTgKfFnFdh9K0y\n" +
        "fswYKdTA/o8RKaa5";

    static char passphrase[] = "passphrase".toCharArray();

    /*
     * Is the server ready to serve?
     */
    volatile static boolean serverReady = false;

    /*
     * Turn on SSL debugging?
     */
    static boolean debug = false;

    /*
     * Define the server side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    void doServerSide() throws Exception {
        SSLContext context = getSSLContext(true);
        SSLServerSocketFactory sslssf = context.getServerSocketFactory();

        SSLServerSocket sslServerSocket =
            (SSLServerSocket)sslssf.createServerSocket(serverPort);
        serverPort = sslServerSocket.getLocalPort();
        SSLSocket sslSocket = null;
        try {
            /*
             * Signal Client, we're ready for his connect.
             */
            serverReady = true;

            sslSocket = (SSLSocket) sslServerSocket.accept();
            sslSocket.setNeedClientAuth(true);

            InputStream sslIS = sslSocket.getInputStream();
            OutputStream sslOS = sslSocket.getOutputStream();

            sslIS.read();
            sslOS.write(85);
            sslOS.flush();
        } finally {
            if (sslSocket != null) {
                sslSocket.close();
            }
            sslServerSocket.close();
        }
    }

    /*
     * Define the client side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    void doClientSide() throws Exception {
        /*
         * Wait for server to get started.
         */
        while (!serverReady) {
            Thread.sleep(50);
        }

        SSLContext context = getSSLContext(false);
        SSLSocketFactory sslsf = context.getSocketFactory();

        SSLSocket sslSocket =
            (SSLSocket)sslsf.createSocket("localhost", serverPort);
        sslSocket.setEnabledProtocols(new String[] { "TLSv1", "TLSv1.1", "TLSv1.2" });
        try {
            InputStream sslIS = sslSocket.getInputStream();
            OutputStream sslOS = sslSocket.getOutputStream();

            sslOS.write(280);
            sslOS.flush();
            sslIS.read();
        } finally {
            sslSocket.close();
        }
    }

    // get the ssl context
    private static SSLContext getSSLContext(boolean isServer) throws Exception {

        // generate certificate from cert string
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        // create a key store
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);

        // import the trused cert
        ByteArrayInputStream is =
            new ByteArrayInputStream(trusedCertStr.getBytes());
        Certificate trusedCert = cf.generateCertificate(is);
        is.close();

        ks.setCertificateEntry("SunJSSE Test Serivce", trusedCert);

        // import the certificate chain and key
        Certificate[] chain = new Certificate[3];

        is = new ByteArrayInputStream(caSignerStr.getBytes());
        Certificate caSignerCert = cf.generateCertificate(is);
        is.close();
        chain[2] = caSignerCert;

        is = new ByteArrayInputStream(certIssuerStr.getBytes());
        Certificate certIssuerCert = cf.generateCertificate(is);
        is.close();
        chain[1] = certIssuerCert;

        PKCS8EncodedKeySpec priKeySpec = null;
        if (isServer) {
            priKeySpec = new PKCS8EncodedKeySpec(
                            Base64.getMimeDecoder().decode(serverPrivateKey));
            is = new ByteArrayInputStream(serverCertStr.getBytes());
        } else {
            priKeySpec = new PKCS8EncodedKeySpec(
                            Base64.getMimeDecoder().decode(clientPrivateKey));
            is = new ByteArrayInputStream(clientCertStr.getBytes());
        }
        KeyFactory kf = KeyFactory.getInstance("RSA");
        RSAPrivateKey priKey = (RSAPrivateKey)kf.generatePrivate(priKeySpec);
        Certificate keyCert = cf.generateCertificate(is);
        is.close();
        chain[0] = keyCert;

        ks.setKeyEntry("End Entity", priKey, passphrase, chain);

        // check the certification path
        PKIXParameters paras = new PKIXParameters(ks);
        paras.setRevocationEnabled(false);
        CertPath path = cf.generateCertPath(Arrays.asList(chain));
        CertPathValidator cv = CertPathValidator.getInstance("PKIX");
        cv.validate(path, paras);

        // create SSL context
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmAlgorithm);
        tmf.init(ks);

        SSLContext ctx = SSLContext.getInstance("TLS");
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("NewSunX509");
        kmf.init(ks, passphrase);

        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        ks = null;

        return ctx;
    }

    private static String tmAlgorithm;        // trust manager

    private static void parseArguments(String[] args) {
        tmAlgorithm = args[0];
    }

    /*
     * =============================================================
     * The remainder is just support stuff
     */

    // use any free port by default
    volatile int serverPort = 0;

    volatile Exception serverException = null;
    volatile Exception clientException = null;

    public static void main(String args[]) throws Exception {
        // MD5 is used in this test case, don't disable MD5 algorithm.
        Security.setProperty("jdk.certpath.disabledAlgorithms",
                "MD2, RSA keySize < 1024");
        Security.setProperty("jdk.tls.disabledAlgorithms",
                "SSLv3, RC4, DH keySize < 768");

        if (debug)
            System.setProperty("javax.net.debug", "all");


        /*
         * Get the customized arguments.
         */
        parseArguments(args);

        /*
         * Start the tests.
         */
        new BasicConstraints();
    }

    Thread clientThread = null;
    Thread serverThread = null;
    /*
     * Primary constructor, used to drive remainder of the test.
     *
     * Fork off the other side, then do your work.
     */
    BasicConstraints() throws Exception {
        if (separateServerThread) {
            startServer(true);
            startClient(false);
        } else {
            startClient(true);
            startServer(false);
        }

        /*
         * Wait for other side to close down.
         */
        if (separateServerThread) {
            serverThread.join();
        } else {
            clientThread.join();
        }

        /*
         * When we get here, the test is pretty much over.
         *
         * If the main thread excepted, that propagates back
         * immediately.  If the other thread threw an exception, we
         * should report back.
         */
        if (serverException != null)
            throw serverException;
        if (clientException != null)
            throw clientException;
    }

    void startServer(boolean newThread) throws Exception {
        if (newThread) {
            serverThread = new Thread() {
                public void run() {
                    try {
                        doServerSide();
                    } catch (Exception e) {
                        /*
                         * Our server thread just died.
                         *
                         * Release the client, if not active already...
                         */
                        System.err.println("Server died...");
                        serverReady = true;
                        serverException = e;
                    }
                }
            };
            serverThread.start();
        } else {
            doServerSide();
        }
    }

    void startClient(boolean newThread) throws Exception {
        if (newThread) {
            clientThread = new Thread() {
                public void run() {
                    try {
                        doClientSide();
                    } catch (Exception e) {
                        /*
                         * Our client thread just died.
                         */
                        System.err.println("Client died...");
                        clientException = e;
                    }
                }
            };
            clientThread.start();
        } else {
            doClientSide();
        }
    }

}
