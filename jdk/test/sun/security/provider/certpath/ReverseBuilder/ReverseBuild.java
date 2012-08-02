/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7167988
 * @summary PKIX CertPathBuilder in reverse mode doesn't work if more than
 *          one trust anchor is specified
 */
import java.io.*;
import java.util.*;
import java.security.cert.*;

import sun.security.provider.certpath.SunCertPathBuilderParameters;

public class ReverseBuild {
    // Certificate information:
    // Issuer: C=US, ST=Some-State, L=Some-City, O=Some-Org
    // Validity
    //     Not Before: Dec  8 02:43:36 2008 GMT
    //     Not After : Aug 25 02:43:36 2028 GMT
    // Subject: C=US, ST=Some-State, L=Some-City, O=Some-Org
    // X509v3 Subject Key Identifier:
    //     FA:B9:51:BF:4C:E7:D9:86:98:33:F9:E7:CB:1E:F1:33:49:F7:A8:14
    // X509v3 Authority Key Identifier:
    //     keyid:FA:B9:51:BF:4C:E7:D9:86:98:33:F9:E7:CB:1E:F1:33:49:F7:A8:14
    //     DirName:/C=US/ST=Some-State/L=Some-City/O=Some-Org
    //     serial:00
    static String NoiceTrusedCertStr =
        "-----BEGIN CERTIFICATE-----\n" +
        "MIICrDCCAhWgAwIBAgIBADANBgkqhkiG9w0BAQQFADBJMQswCQYDVQQGEwJVUzET\n" +
        "MBEGA1UECBMKU29tZS1TdGF0ZTESMBAGA1UEBxMJU29tZS1DaXR5MREwDwYDVQQK\n" +
        "EwhTb21lLU9yZzAeFw0wODEyMDgwMjQzMzZaFw0yODA4MjUwMjQzMzZaMEkxCzAJ\n" +
        "BgNVBAYTAlVTMRMwEQYDVQQIEwpTb21lLVN0YXRlMRIwEAYDVQQHEwlTb21lLUNp\n" +
        "dHkxETAPBgNVBAoTCFNvbWUtT3JnMIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKB\n" +
        "gQDLxDggB76Ip5OwoUNRLdeOha9U3a2ieyNbz5kTU5lFfe5tui2/461uPZ8a+QOX\n" +
        "4BdVrhEmV94BKY4FPyH35zboLjfXSKxT1mAOx1Bt9sWF94umxZE1cjyU7vEX8HHj\n" +
        "7BvOyk5AQrBt7moO1uWtPA/JuoJPePiJl4kqlRJM2Akq6QIDAQABo4GjMIGgMB0G\n" +
        "A1UdDgQWBBT6uVG/TOfZhpgz+efLHvEzSfeoFDBxBgNVHSMEajBogBT6uVG/TOfZ\n" +
        "hpgz+efLHvEzSfeoFKFNpEswSTELMAkGA1UEBhMCVVMxEzARBgNVBAgTClNvbWUt\n" +
        "U3RhdGUxEjAQBgNVBAcTCVNvbWUtQ2l0eTERMA8GA1UEChMIU29tZS1PcmeCAQAw\n" +
        "DAYDVR0TBAUwAwEB/zANBgkqhkiG9w0BAQQFAAOBgQBcIm534U123Hz+rtyYO5uA\n" +
        "ofd81G6FnTfEAV8Kw9fGyyEbQZclBv34A9JsFKeMvU4OFIaixD7nLZ/NZ+IWbhmZ\n" +
        "LovmJXyCkOufea73pNiZ+f/4/ScZaIlM/PRycQSqbFNd4j9Wott+08qxHPLpsf3P\n" +
        "6Mvf0r1PNTY2hwTJLJmKtg==\n" +
        "-----END CERTIFICATE-----";

    // Certificate information:
    // Issuer: C=US, O=Java, OU=SunJSSE Test Serivce
    // Validity
    //     Not Before: Aug 19 01:52:19 2011 GMT
    //     Not After : Jul 29 01:52:19 2032 GMT
    // Subject: C=US, O=Java, OU=SunJSSE Test Serivce

    // X509v3 Subject Key Identifier:
    //     B9:7C:D5:D9:DF:A7:4C:03:AE:FD:0E:27:5B:31:95:6C:C7:F3:75:E1
    // X509v3 Authority Key Identifier:
    //     keyid:B9:7C:D5:D9:DF:A7:4C:03:AE:FD:0E:27:5B:31:95:6C:C7:F3:75:E1
    //     DirName:/C=US/O=Java/OU=SunJSSE Test Serivce
    //     serial:00
    static String NoiceTrusedCertStr_2nd =
        "-----BEGIN CERTIFICATE-----\n" +
        "MIICkjCCAfugAwIBAgIBADANBgkqhkiG9w0BAQQFADA7MQswCQYDVQQGEwJVUzEN\n" +
        "MAsGA1UEChMESmF2YTEdMBsGA1UECxMUU3VuSlNTRSBUZXN0IFNlcml2Y2UwHhcN\n" +
        "MTEwODE5MDE1MjE5WhcNMzIwNzI5MDE1MjE5WjA7MQswCQYDVQQGEwJVUzENMAsG\n" +
        "A1UEChMESmF2YTEdMBsGA1UECxMUU3VuSlNTRSBUZXN0IFNlcml2Y2UwgZ8wDQYJ\n" +
        "KoZIhvcNAQEBBQADgY0AMIGJAoGBAM8orG08DtF98TMSscjGsidd1ZoN4jiDpi8U\n" +
        "ICz+9dMm1qM1d7O2T+KH3/mxyox7Rc2ZVSCaUD0a3CkhPMnlAx8V4u0H+E9sqso6\n" +
        "iDW3JpOyzMExvZiRgRG/3nvp55RMIUV4vEHOZ1QbhuqG4ebN0Vz2DkRft7+flthf\n" +
        "vDld6f5JAgMBAAGjgaUwgaIwHQYDVR0OBBYEFLl81dnfp0wDrv0OJ1sxlWzH83Xh\n" +
        "MGMGA1UdIwRcMFqAFLl81dnfp0wDrv0OJ1sxlWzH83XhoT+kPTA7MQswCQYDVQQG\n" +
        "EwJVUzENMAsGA1UEChMESmF2YTEdMBsGA1UECxMUU3VuSlNTRSBUZXN0IFNlcml2\n" +
        "Y2WCAQAwDwYDVR0TAQH/BAUwAwEB/zALBgNVHQ8EBAMCAQYwDQYJKoZIhvcNAQEE\n" +
        "BQADgYEALlgaH1gWtoBZ84EW8Hu6YtGLQ/L9zIFmHonUPZwn3Pr//icR9Sqhc3/l\n" +
        "pVTxOINuFHLRz4BBtEylzRIOPzK3tg8XwuLb1zd0db90x3KBCiAL6E6cklGEPwLe\n" +
        "XYMHDn9eDsaq861Tzn6ZwzMgw04zotPMoZN0mVd/3Qca8UJFucE=\n" +
        "-----END CERTIFICATE-----";


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
    static String trustedCertStr =
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
    static String targetCertStr =
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
    static String targetPrivateKey = // Private key in the format of PKCS#8
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


    public static void main(String args[]) throws Exception {

        // generate certificate from cert string
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        // create a set of trust anchors
        LinkedHashSet<TrustAnchor> trustAnchors = new LinkedHashSet<>();

        ByteArrayInputStream is =
            new ByteArrayInputStream(NoiceTrusedCertStr.getBytes());
        Certificate trustedCert = cf.generateCertificate(is);
        is.close();
        TrustAnchor anchor =
            new TrustAnchor((X509Certificate)trustedCert, null);
        trustAnchors.add(anchor);

        is = new ByteArrayInputStream(trustedCertStr.getBytes());
        trustedCert = cf.generateCertificate(is);
        is.close();
        anchor = new TrustAnchor((X509Certificate)trustedCert, null);
        trustAnchors.add(anchor);

        is = new ByteArrayInputStream(NoiceTrusedCertStr_2nd.getBytes());
        trustedCert = cf.generateCertificate(is);
        is.close();
        anchor = new TrustAnchor((X509Certificate)trustedCert, null);
        trustAnchors.add(anchor);

        // create a list of certificates
        List<Certificate> chainList = new ArrayList<>();

        is = new ByteArrayInputStream(targetCertStr.getBytes());
        Certificate cert = cf.generateCertificate(is);
        is.close();
        chainList.add(cert);

        is = new ByteArrayInputStream(certIssuerStr.getBytes());
        cert = cf.generateCertificate(is);
        is.close();
        chainList.add(cert);

        is = new ByteArrayInputStream(caSignerStr.getBytes());
        cert = cf.generateCertificate(is);
        is.close();
        chainList.add(cert);

        // create a certificate selector
        X509CertSelector xcs = new X509CertSelector();
        X509Certificate eeCert = (X509Certificate)chainList.get(0);
        xcs.setSubject(eeCert.getSubjectX500Principal());

        // reverse build
        SunCertPathBuilderParameters params =
            new SunCertPathBuilderParameters(trustAnchors, xcs);
        params.setBuildForward(false);
        params.setRevocationEnabled(false);

        CollectionCertStoreParameters ccsp =
            new CollectionCertStoreParameters(chainList);
        params.addCertStore(CertStore.getInstance("Collection", ccsp));

        CertPathBuilder cpb = CertPathBuilder.getInstance("PKIX");
        CertPathBuilderResult res = cpb.build(params);
    }
}
