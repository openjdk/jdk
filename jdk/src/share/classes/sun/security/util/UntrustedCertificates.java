/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package sun.security.util;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateException;
import java.util.Set;
import java.util.HashSet;

/**
 * A utility class to check if a certificate is untrusted. This is an internal
 * mechanism that explicitly marks a certificate as untrusted, normally in the
 * case that a certificate is known to be used for malicious reasons.
 *
 * <b>Attention</b>: This check is NOT meant to replace the standard PKI-defined
 * validation check, neither is it used as an alternative to CRL.
 */
public final class UntrustedCertificates {

    private final static Set<X509Certificate> untrustedCerts = new HashSet<>();

    /**
     * Checks if a certificate is untrusted.
     *
     * @param cert the certificate to check
     * @return true if the certificate is untrusted.
     */
    public static boolean isUntrusted(X509Certificate cert) {
        return untrustedCerts.contains(cert);
    }

    private static void add(String alias, String pemCert) {
        // generate certificate from PEM certificate
        try (ByteArrayInputStream is =
                new ByteArrayInputStream(pemCert.getBytes())) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate)cf.generateCertificate(is);

            if (!untrustedCerts.add(cert)) {
                throw new RuntimeException("Duplicate untrusted certificate: " +
                    cert.getSubjectX500Principal());
            }
        } catch (CertificateException | IOException e) {
            throw new RuntimeException(
                        "Incorrect untrusted certificate: " + alias, e);
        }
    }

    static {
        // -----------------------------------------------------------------
        // Compromised CAs of Digicert Malaysia
        //
        // Reported by Digicert in its announcement on November 05, 2011.
        //

        // Digicert Malaysia intermediate, cross-signed by CyberTrust
        //
        // Subject: CN=Digisign Server ID (Enrich),
        //          OU=457608-K,
        //          O=Digicert Sdn. Bhd.,
        //          C=MY
        // Issuer:  CN=GTE CyberTrust Global Root,
        //          OU=GTE CyberTrust Solutions, Inc.,
        //          O=GTE Corporation,
        //          C=US
        // Serial:  120001705 (07:27:14:a9)
        add("digicert-server-cross-to-cybertrust-4C0E636A",
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIDyzCCAzSgAwIBAgIEBycUqTANBgkqhkiG9w0BAQUFADB1MQswCQYDVQQGEwJV\n" +
        "UzEYMBYGA1UEChMPR1RFIENvcnBvcmF0aW9uMScwJQYDVQQLEx5HVEUgQ3liZXJU\n" +
        "cnVzdCBTb2x1dGlvbnMsIEluYy4xIzAhBgNVBAMTGkdURSBDeWJlclRydXN0IEds\n" +
        "b2JhbCBSb290MB4XDTA3MDcxNzE1MTc0OFoXDTEyMDcxNzE1MTY1NFowYzELMAkG\n" +
        "A1UEBhMCTVkxGzAZBgNVBAoTEkRpZ2ljZXJ0IFNkbi4gQmhkLjERMA8GA1UECxMI\n" +
        "NDU3NjA4LUsxJDAiBgNVBAMTG0RpZ2lzaWduIFNlcnZlciBJRCAoRW5yaWNoKTCB\n" +
        "nzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEArahkS02Hx4RZufuQRqCmicDx/tXa\n" +
        "VII3DZkrRSYK6Fawf8qo9I5HhAGCKeOzarWR8/uVhbxyqGToCkCcxfRxrnt7agfq\n" +
        "kBRPjYmvlKuyBtQCanuYH1m5Os1U+iDfsioK6bjdaZDAKdNO0JftZszFGUkGf/pe\n" +
        "LHx7hRsyQt97lSUCAwEAAaOCAXgwggF0MBIGA1UdEwEB/wQIMAYBAf8CAQAwXAYD\n" +
        "VR0gBFUwUzBIBgkrBgEEAbE+AQAwOzA5BggrBgEFBQcCARYtaHR0cDovL2N5YmVy\n" +
        "dHJ1c3Qub21uaXJvb3QuY29tL3JlcG9zaXRvcnkuY2ZtMAcGBWCDSgEBMA4GA1Ud\n" +
        "DwEB/wQEAwIB5jCBiQYDVR0jBIGBMH+heaR3MHUxCzAJBgNVBAYTAlVTMRgwFgYD\n" +
        "VQQKEw9HVEUgQ29ycG9yYXRpb24xJzAlBgNVBAsTHkdURSBDeWJlclRydXN0IFNv\n" +
        "bHV0aW9ucywgSW5jLjEjMCEGA1UEAxMaR1RFIEN5YmVyVHJ1c3QgR2xvYmFsIFJv\n" +
        "b3SCAgGlMEUGA1UdHwQ+MDwwOqA4oDaGNGh0dHA6Ly93d3cucHVibGljLXRydXN0\n" +
        "LmNvbS9jZ2ktYmluL0NSTC8yMDE4L2NkcC5jcmwwHQYDVR0OBBYEFMYWk04WF+wW\n" +
        "royUdvOGbcV0boR3MA0GCSqGSIb3DQEBBQUAA4GBAHYAe6Z4K2Ydjl42xqSOBfIj\n" +
        "knyTZ9P0wAp9iy3Z6tVvGvPhSilaIoRNUC9LDPL/hcJ7VdREgr5trGeOvLQfkpxR\n" +
        "gBoU9m6rYYgLrRx/90tQUdZlG6ZHcRVesHHzNRTyN71jyNXwk1o0X9g96F33xR7A\n" +
        "5c8fhiSpPAdmzcHSNmNZ\n" +
        "-----END CERTIFICATE-----");

        // Digicert Malaysia intermediate, cross-signed by Entrust
        //
        // Subject: CN=Digisign Server ID - (Enrich),
        //          OU=457608-K,
        //          O=Digicert Sdn. Bhd.,
        //          C=MY
        // Issuer:  CN=Entrust.net Certification Authority (2048)
        //          OU=(c) 1999 Entrust.net Limited,
        //          OU=www.entrust.net/CPS_2048 incorp. by ref. (limits liab.),
        //          O=Entrust.net
        // Serial:  1184644297 (4c:0e:63:6a)
        add("digicert-server-cross-to-entrust-ca-4C0E636A",
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIEzjCCA7agAwIBAgIETA5jajANBgkqhkiG9w0BAQUFADCBtDEUMBIGA1UEChML\n" +
        "RW50cnVzdC5uZXQxQDA+BgNVBAsUN3d3dy5lbnRydXN0Lm5ldC9DUFNfMjA0OCBp\n" +
        "bmNvcnAuIGJ5IHJlZi4gKGxpbWl0cyBsaWFiLikxJTAjBgNVBAsTHChjKSAxOTk5\n" +
        "IEVudHJ1c3QubmV0IExpbWl0ZWQxMzAxBgNVBAMTKkVudHJ1c3QubmV0IENlcnRp\n" +
        "ZmljYXRpb24gQXV0aG9yaXR5ICgyMDQ4KTAeFw0xMDA3MTYxNzIzMzdaFw0xNTA3\n" +
        "MTYxNzUzMzdaMGUxCzAJBgNVBAYTAk1ZMRswGQYDVQQKExJEaWdpY2VydCBTZG4u\n" +
        "IEJoZC4xETAPBgNVBAsTCDQ1NzYwOC1LMSYwJAYDVQQDEx1EaWdpc2lnbiBTZXJ2\n" +
        "ZXIgSUQgLSAoRW5yaWNoKTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEB\n" +
        "AMWJ5PQNBkCSWccaszXRDkwqM/n4r8qef+65p21g9FTob9Wb8xtjMQRoctE0Foy0\n" +
        "FyyX3nPF2JAVoBor9cuzSIZE8B2ITM5BQhrv9Qze/kDaOSD3BlU6ap1GwdJvpbLI\n" +
        "Vz4po5zg6YV3ZuiYpyR+vsBZIOVEb7ZX2L7OwmV3WMZhQdF0BMh/SULFcqlyFu6M\n" +
        "3RJdtErU0a9Qt9iqdXZorT5dqjBtYairEFs+E78z4K9EnTgiW+9ML6ZxJhUmyiiM\n" +
        "2fqOjqmiFDXimySItPR/hZ2DTwehthSQNsQ0HI0mYW0Tb3i+6I8nx0uElqOGaAwj\n" +
        "vgvsjJQAqQSKE5D334VsDLECAwEAAaOCATQwggEwMA4GA1UdDwEB/wQEAwIBBjAS\n" +
        "BgNVHRMBAf8ECDAGAQH/AgEAMCcGA1UdJQQgMB4GCCsGAQUFBwMBBggrBgEFBQcD\n" +
        "AgYIKwYBBQUHAwQwMwYIKwYBBQUHAQEEJzAlMCMGCCsGAQUFBzABhhdodHRwOi8v\n" +
        "b2NzcC5lbnRydXN0Lm5ldDBEBgNVHSAEPTA7MDkGBWCDSgEBMDAwLgYIKwYBBQUH\n" +
        "AgEWImh0dHA6Ly93d3cuZGlnaWNlcnQuY29tLm15L2Nwcy5odG0wMgYDVR0fBCsw\n" +
        "KTAnoCWgI4YhaHR0cDovL2NybC5lbnRydXN0Lm5ldC8yMDQ4Y2EuY3JsMBEGA1Ud\n" +
        "DgQKBAhMTswlKAMpgTAfBgNVHSMEGDAWgBRV5IHREYC+2Im5CKMx+aEkCRa5cDAN\n" +
        "BgkqhkiG9w0BAQUFAAOCAQEAl0zvSjpJrHL8MCBrtClbp8WVBJD5MtXChWreA6E3\n" +
        "+YkAsFqsVX7bQzX/yQH4Ub7MJsrIaqTEVD4mHucMo82XZ5TdpkLrXM2POXlrM3kh\n" +
        "Bnn6gkQVmczBtznTRmJ8snDrb84gqj4Zt+l0gpy0pUtNYQA35IfS8hQ6ZHy4qXth\n" +
        "4JMi59WfPkfmNnagU9gAAzoPtTP+lsrT0oI6Lt3XSOHkp2nMHOmZSufKcEXXCwcO\n" +
        "mnUb0C+Sb/akB8O9HEumhLZ9qJqp0qcp8QtXaR6XVybsK0Os1EWDBQDp4/BGQAf6\n" +
        "6rFRc5Mcpd1TETfIKqcVJx20qsx/qjEw/LhFn0gJ7RDixQ==\n" +
        "-----END CERTIFICATE-----");


        // -----------------------------------------------------------------
        //
        // No longer used certificates
        //

        // Subject: CN=Java Media APIs,
        //          OU=Java Signed Extensions,
        //          OU=Corporate Object Signing,
        //          O=Sun Microsystems Inc
        // Issuer:  CN=Object Signing CA,
        //          OU=Class 2 OnSite Subscriber CA,
        //          OU=VeriSign Trust Network,
        //          O=Sun Microsystems Inc
        // Serial:  6a:8b:99:91:37:59:4f:89:53:e2:97:18:9f:19:1e:4e
        add("java-media-pretrusted-9F191E4E",
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIFdzCCBF+gAwIBAgIQaouZkTdZT4lT4pcYnxkeTjANBgkqhkiG9w0BAQUFADCB\n" +
        "gzEdMBsGA1UEChMUU3VuIE1pY3Jvc3lzdGVtcyBJbmMxHzAdBgNVBAsTFlZlcmlT\n" +
        "aWduIFRydXN0IE5ldHdvcmsxJTAjBgNVBAsTHENsYXNzIDIgT25TaXRlIFN1YnNj\n" +
        "cmliZXIgQ0ExGjAYBgNVBAMTEU9iamVjdCBTaWduaW5nIENBMB4XDTA5MDUxMjAw\n" +
        "MDAwMFoXDTEyMDUxMTIzNTk1OVowfTEdMBsGA1UEChQUU3VuIE1pY3Jvc3lzdGVt\n" +
        "cyBJbmMxITAfBgNVBAsUGENvcnBvcmF0ZSBPYmplY3QgU2lnbmluZzEfMB0GA1UE\n" +
        "CxQWSmF2YSBTaWduZWQgRXh0ZW5zaW9uczEYMBYGA1UEAxQPSmF2YSBNZWRpYSBB\n" +
        "UElzMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAl5blzoKTVE8y4Hpz\n" +
        "q6E15RZz1bF5HnYEyYqgHkZXnAKedmYCoMzm1XK8s+gQWShLEvGEAvs5yqarx9gE\n" +
        "nnC21N28aEZgIJMa2/arKxCUkS4pxdGPYGexL9UzSRkUpoBShCZKEGdmX7gfJE2K\n" +
        "/sd9MFvGV5/yZtWXrADzvm0Kd/9mg1KRv1gfrZIq0TJbupoXPYYqb73AkI9eT2ZD\n" +
        "q9MdwD4E5+oojsDFXt8GU/D00fUhtXpYwuplU7D667WHYdJhIah0ST6JywyqcLXG\n" +
        "XSuFTXOgITT2idSHluZVmx3dqJ72u9kPkO4JdJTMDfaK8zgNLaRkiU8Qcj+qhLYH\n" +
        "ytaqcwIDAQABo4IB6jCCAeYwCQYDVR0TBAIwADAOBgNVHQ8BAf8EBAMCB4AwfwYD\n" +
        "VR0fBHgwdjB0oHKgcIZuaHR0cDovL29uc2l0ZWNybC52ZXJpc2lnbi5jb20vU3Vu\n" +
        "TWljcm9zeXN0ZW1zSW5jQ29ycG9yYXRlT2JqZWN0U2lnbmluZ0phdmFTaWduZWRF\n" +
        "eHRlbnNpb25zQ2xhc3NCL0xhdGVzdENSTC5jcmwwHwYDVR0jBBgwFoAUs0crgn5T\n" +
        "tHPKuLsZt76BTQeVx+0wHQYDVR0OBBYEFKS32mVx0gNWTeS4ProHEaeSpvvIMDsG\n" +
        "CCsGAQUFBwEBBC8wLTArBggrBgEFBQcwAYYfaHR0cDovL29uc2l0ZS1vY3NwLnZl\n" +
        "cmlzaWduLmNvbTCBtQYDVR0gBIGtMIGqMDkGC2CGSAGG+EUBBxcCMCowKAYIKwYB\n" +
        "BQUHAgEWHGh0dHBzOi8vd3d3LnZlcmlzaWduLmNvbS9ycGEwbQYLYIZIAYb3AIN9\n" +
        "nD8wXjAnBggrBgEFBQcCARYbaHR0cHM6Ly93d3cuc3VuLmNvbS9wa2kvY3BzMDMG\n" +
        "CCsGAQUFBwICMCcaJVZhbGlkYXRlZCBGb3IgU3VuIEJ1c2luZXNzIE9wZXJhdGlv\n" +
        "bnMwEwYDVR0lBAwwCgYIKwYBBQUHAwMwDQYJKoZIhvcNAQEFBQADggEBAAe6BO4W\n" +
        "3TSNWfezyelJs6kE3HfulT6Bdyz4UUoh9ykXcV8nRwT+kh25I5MdyG2GfkJoADPR\n" +
        "VhC5DYo13UFpIsTNVjq+hGYe2hML93bN7ad9SxCCyjHUo3yMz2qgBbHZI3VA9ZHA\n" +
        "aWM4Tx0saMwbcnVvlbuGh+PXvStfypJqYT6lzcdFfjNVX4FI/QQNGhBswMY51tC8\n" +
        "GTBCL2qhJon0gSCU4zaawDOf7+XxJWirLamYL1Aal1/h2z2sFrvA/1ftxtU3kZ6I\n" +
        "7De8DyoHeZg7pYGdrj7g+lPhCga/WvEhN152I+aP08YbFcJHYmK05ngl/Ye4c6Bd\n" +
        "cdrdfbw6QzEUIYY=\n" +
        "-----END CERTIFICATE-----");

        // Subject: CN=JavaFX 1.0 Runtime,
        //          OU=Java Signed Extensions,
        //          OU=Corporate Object Signing,
        //          O=Sun Microsystems Inc
        // Issuer:  CN=Object Signing CA,
        //          OU=Class 2 OnSite Subscriber CA,
        //          OU=VeriSign Trust Network,
        //          O=Sun Microsystems Inc
        // Serial:  55:c0:e6:44:59:59:79:9e:d9:26:f1:b0:4a:1e:f0:27
        add("java-fx10-pretrusted-4A1EF027",
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIFezCCBGOgAwIBAgIQVcDmRFlZeZ7ZJvGwSh7wJzANBgkqhkiG9w0BAQUFADCB\n" +
        "gzEdMBsGA1UEChMUU3VuIE1pY3Jvc3lzdGVtcyBJbmMxHzAdBgNVBAsTFlZlcmlT\n" +
        "aWduIFRydXN0IE5ldHdvcmsxJTAjBgNVBAsTHENsYXNzIDIgT25TaXRlIFN1YnNj\n" +
        "cmliZXIgQ0ExGjAYBgNVBAMTEU9iamVjdCBTaWduaW5nIENBMB4XDTA4MTAwOTAw\n" +
        "MDAwMFoXDTExMTAwOTIzNTk1OVowgYAxHTAbBgNVBAoUFFN1biBNaWNyb3N5c3Rl\n" +
        "bXMgSW5jMSEwHwYDVQQLFBhDb3Jwb3JhdGUgT2JqZWN0IFNpZ25pbmcxHzAdBgNV\n" +
        "BAsUFkphdmEgU2lnbmVkIEV4dGVuc2lvbnMxGzAZBgNVBAMUEkphdmFGWCAxLjAg\n" +
        "UnVudGltZTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAM+WDc6+bu+4\n" +
        "tmAcS/lBtUc02WOt9QZpVsXg9cG2pu/8bUtmDELa8iiYBVFpIs8DU58HLrGQtCUY\n" +
        "SIAGOVPsOJoN29UKCDWfY9j5JeVhfhMGqk9DwrWhzgsjy4cpZ1pIp+k/fJ8zT8Ul\n" +
        "aYLpow1vg3UNddsmwz02tN7cOrMw9WYIG4CRYnY1OrtJSfe2pYzheC4zyvR+aiVl\n" +
        "nang2OtqikSQsNFOFHsLOJFxngy9LrO8evDSu25VTKI6zlWU6/bMeqtztJPN0VOn\n" +
        "NyUrJZvkxZ207Jg0T693BGSxNC1n+ihztXogql8950M/pEuUbDjylv5FFvlp6DSB\n" +
        "dDT2MkutmyMCAwEAAaOCAeowggHmMAkGA1UdEwQCMAAwDgYDVR0PAQH/BAQDAgeA\n" +
        "MH8GA1UdHwR4MHYwdKByoHCGbmh0dHA6Ly9vbnNpdGVjcmwudmVyaXNpZ24uY29t\n" +
        "L1N1bk1pY3Jvc3lzdGVtc0luY0NvcnBvcmF0ZU9iamVjdFNpZ25pbmdKYXZhU2ln\n" +
        "bmVkRXh0ZW5zaW9uc0NsYXNzQi9MYXRlc3RDUkwuY3JsMB8GA1UdIwQYMBaAFLNH\n" +
        "K4J+U7Rzyri7Gbe+gU0HlcftMB0GA1UdDgQWBBTjgufVi3XJ3gx1ewsA6Rr7BR4Z\n" +
        "zjA7BggrBgEFBQcBAQQvMC0wKwYIKwYBBQUHMAGGH2h0dHA6Ly9vbnNpdGUtb2Nz\n" +
        "cC52ZXJpc2lnbi5jb20wgbUGA1UdIASBrTCBqjA5BgtghkgBhvhFAQcXAjAqMCgG\n" +
        "CCsGAQUFBwIBFhxodHRwczovL3d3dy52ZXJpc2lnbi5jb20vcnBhMG0GC2CGSAGG\n" +
        "9wCDfZw/MF4wJwYIKwYBBQUHAgEWG2h0dHBzOi8vd3d3LnN1bi5jb20vcGtpL2Nw\n" +
        "czAzBggrBgEFBQcCAjAnGiVWYWxpZGF0ZWQgRm9yIFN1biBCdXNpbmVzcyBPcGVy\n" +
        "YXRpb25zMBMGA1UdJQQMMAoGCCsGAQUFBwMDMA0GCSqGSIb3DQEBBQUAA4IBAQAB\n" +
        "YVJTTVe7rzyTO4jc3zajErOT/COkdQTfNo0eIX1QbNynFieJvwY/jRzUZwjktIFR\n" +
        "2p4JtbpHGAtKtjOAOTieQ8xdDOoC1djzpE7/AbMvuvlTavtUKT+F7tPdhfXgWXJV\n" +
        "6Wbt8jryKyk3zZGiEhauIwZUkfjRkEtffEmZWLUd8c8rURJjfC/XHH2oyurscoxc\n" +
        "CjX29c9ynxSiS/VvQp1an0HvErGh69N48wj7cj8mtZ1yHzd2XCzSSR1OfTPfk0Pt\n" +
        "yg51p7yJaFiH21PTZegEL6zyVNOYBTKwwIi2OzpwYalD3uvK6e3OKDrfFCOxu17u\n" +
        "4PveESbrdyrmvLe7IVez\n" +
        "-----END CERTIFICATE-----");

        // Subject: CN=JavaFX Runtime,
        //          OU=Java Signed Extensions,
        //          OU=Corporate Object Signing,
        //          O=Sun Microsystems Inc
        // Issuer:  CN=Object Signing CA,
        //          OU=Class 2 OnSite Subscriber CA,
        //          OU=VeriSign Trust Network,
        //          O=Sun Microsystems Inc
        // Serial:  47:f4:55:f1:da:4a:5e:f9:e3:f7:a8:03:62:17:c0:ff
        add("javafx-runtime-pretrusted-6217C0FF",
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIFdjCCBF6gAwIBAgIQR/RV8dpKXvnj96gDYhfA/zANBgkqhkiG9w0BAQUFADCB\n" +
        "gzEdMBsGA1UEChMUU3VuIE1pY3Jvc3lzdGVtcyBJbmMxHzAdBgNVBAsTFlZlcmlT\n" +
        "aWduIFRydXN0IE5ldHdvcmsxJTAjBgNVBAsTHENsYXNzIDIgT25TaXRlIFN1YnNj\n" +
        "cmliZXIgQ0ExGjAYBgNVBAMTEU9iamVjdCBTaWduaW5nIENBMB4XDTA5MDEyOTAw\n" +
        "MDAwMFoXDTEyMDEyOTIzNTk1OVowfDEdMBsGA1UEChQUU3VuIE1pY3Jvc3lzdGVt\n" +
        "cyBJbmMxITAfBgNVBAsUGENvcnBvcmF0ZSBPYmplY3QgU2lnbmluZzEfMB0GA1UE\n" +
        "CxQWSmF2YSBTaWduZWQgRXh0ZW5zaW9uczEXMBUGA1UEAxQOSmF2YUZYIFJ1bnRp\n" +
        "bWUwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCIzd0fAk8mI9ONc6RJ\n" +
        "aGieioK2FLdXEwj8zL3vdGDVmBwyR1zwYkaOIFFgF9IW/8qc4iAYA5sGUY+0g8q3\n" +
        "5DuYAxfTzBB5KdaYvbuq6GGnoHIWmTirXY+1friFp8lyXSvtuEaGB1VHaBoZchEg\n" +
        "k+UgeVDA43dHwcT1Ov3DePczJRUes8T/QHzLX+BxUDG43vjyncCEO/AjqLZxXEz2\n" +
        "xrNbKLcH3lGMJK7hdbfssUfF5BjC38Hn71HauYlA43b2no+2y0Sjulwzez2YPbDC\n" +
        "0GLR3TnKtA8dqOrnl5t3DniDbfOBNtBE3VOydJO0XW57Ng1HRXD023nm9ECPY2xp\n" +
        "0N/pAgMBAAGjggHqMIIB5jAJBgNVHRMEAjAAMA4GA1UdDwEB/wQEAwIHgDB/BgNV\n" +
        "HR8EeDB2MHSgcqBwhm5odHRwOi8vb25zaXRlY3JsLnZlcmlzaWduLmNvbS9TdW5N\n" +
        "aWNyb3N5c3RlbXNJbmNDb3Jwb3JhdGVPYmplY3RTaWduaW5nSmF2YVNpZ25lZEV4\n" +
        "dGVuc2lvbnNDbGFzc0IvTGF0ZXN0Q1JMLmNybDAfBgNVHSMEGDAWgBSzRyuCflO0\n" +
        "c8q4uxm3voFNB5XH7TAdBgNVHQ4EFgQUvOdd0cKPj+Yik/iOBwTdphh5A+gwOwYI\n" +
        "KwYBBQUHAQEELzAtMCsGCCsGAQUFBzABhh9odHRwOi8vb25zaXRlLW9jc3AudmVy\n" +
        "aXNpZ24uY29tMIG1BgNVHSAEga0wgaowOQYLYIZIAYb4RQEHFwIwKjAoBggrBgEF\n" +
        "BQcCARYcaHR0cHM6Ly93d3cudmVyaXNpZ24uY29tL3JwYTBtBgtghkgBhvcAg32c\n" +
        "PzBeMCcGCCsGAQUFBwIBFhtodHRwczovL3d3dy5zdW4uY29tL3BraS9jcHMwMwYI\n" +
        "KwYBBQUHAgIwJxolVmFsaWRhdGVkIEZvciBTdW4gQnVzaW5lc3MgT3BlcmF0aW9u\n" +
        "czATBgNVHSUEDDAKBggrBgEFBQcDAzANBgkqhkiG9w0BAQUFAAOCAQEAbGcf2NjL\n" +
        "AI93HG6ny2BbepaZA1a8xa/R6uUc7xV+Qw6MgLwFD4Q4i6LWUztQDvg9l68MM2/i\n" +
        "Y9LEi1KM4lcNbK5+D+t9x98wXBiuojXhVdp5ZmC03EyEBbriopdBsmXVLDSu/Y3+\n" +
        "zowOO5xwpMK3dbgsSDs2Vt0UosD3FTcRaD3GNfOhXMp+o1grHNiXF9YgkmdQbPPZ\n" +
        "DQ2KBhFPCRJXBGvyKOqno/DTg0sQ3crGH/C4/4t7mnQXWldZotmJUZ0ONc9oD+Q1\n" +
        "JAaguUKqIwn9yZ093ie+JWHbYNid9IIIPXYgtRxmf9a376WBhqhu56uJftBJ7x9g\n" +
        "eQ7Lot6CSWCiFw==\n" +
        "-----END CERTIFICATE-----");

        //
        // Compromised Solaris INTERNAL DEVELOPMENT USE ONLY certificate
        //

        // Subject: CN=Solaris INTERNAL DEVELOPMENT USE ONLY,
        //          OU=Solaris Cryptographic Framework,
        //          OU=Corporate Object Signing,
        //          O=Sun Microsystems Inc
        // Issuer:  CN=Object Signing CA,
        //          OU=Class 2 OnSite Subscriber CA,
        //          OU=VeriSign Trust Network,
        //          O=Sun Microsystems Inc
        // Serial:  77:29:77:52:6a:19:7b:9a:a6:a2:c7:99:a0:e1:cd:8c
        add("solaris-internal-dev-A0E1CD8C",
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIFHjCCBAagAwIBAgIQdyl3UmoZe5qmoseZoOHNjDANBgkqhkiG9w0BAQUFADCB\n" +
        "gzEdMBsGA1UEChMUU3VuIE1pY3Jvc3lzdGVtcyBJbmMxHzAdBgNVBAsTFlZlcmlT\n" +
        "aWduIFRydXN0IE5ldHdvcmsxJTAjBgNVBAsTHENsYXNzIDIgT25TaXRlIFN1YnNj\n" +
        "cmliZXIgQ0ExGjAYBgNVBAMTEU9iamVjdCBTaWduaW5nIENBMB4XDTA3MDEwNDAw\n" +
        "MDAwMFoXDTEwMDEwMzIzNTk1OVowgZwxHTAbBgNVBAoUFFN1biBNaWNyb3N5c3Rl\n" +
        "bXMgSW5jMSEwHwYDVQQLFBhDb3Jwb3JhdGUgT2JqZWN0IFNpZ25pbmcxKDAmBgNV\n" +
        "BAsUH1NvbGFyaXMgQ3J5cHRvZ3JhcGhpYyBGcmFtZXdvcmsxLjAsBgNVBAMUJVNv\n" +
        "bGFyaXMgSU5URVJOQUwgREVWRUxPUE1FTlQgVVNFIE9OTFkwgZ8wDQYJKoZIhvcN\n" +
        "AQEBBQADgY0AMIGJAoGBALbNU4hf3mD5ArDI9pjgioAyvV3bjMPRQdCZniIeGJBp\n" +
        "odFlSEH+Mh64W1DsY8coeZ7FvvGJkx9IpTMJW9k8w1oJK9UNqHyAQfaYjQyXi3xQ\n" +
        "LJp62EvYdGfDlwOZejEcR/MbzZG+GOPMMvQj5+xyFDvLXNGfQNTnxw2qnBgCJXjj\n" +
        "AgMBAAGjggH1MIIB8TAJBgNVHRMEAjAAMA4GA1UdDwEB/wQEAwIHgDCBiQYDVR0f\n" +
        "BIGBMH8wfaB7oHmGd2h0dHA6Ly9vbnNpdGVjcmwudmVyaXNpZ24uY29tL1N1bk1p\n" +
        "Y3Jvc3lzdGVtc0luY0NvcnBvcmF0ZU9iamVjdFNpZ25pbmdTb2xhcmlzQ3J5cHRv\n" +
        "Z3JhcGhpY0ZyYW1ld29ya0NsYXNzQi9MYXRlc3RDUkwuY3JsMB8GA1UdIwQYMBaA\n" +
        "FLNHK4J+U7Rzyri7Gbe+gU0HlcftMB0GA1UdDgQWBBRpfiGYkehTnsIzuN2H6AFb\n" +
        "VCZG8jA7BggrBgEFBQcBAQQvMC0wKwYIKwYBBQUHMAGGH2h0dHA6Ly9vbnNpdGUt\n" +
        "b2NzcC52ZXJpc2lnbi5jb20wgbUGA1UdIASBrTCBqjA5BgtghkgBhvhFAQcXAjAq\n" +
        "MCgGCCsGAQUFBwIBFhxodHRwczovL3d3dy52ZXJpc2lnbi5jb20vcnBhMG0GC2CG\n" +
        "SAGG9wCDfZw/MF4wJwYIKwYBBQUHAgEWG2h0dHBzOi8vd3d3LnN1bi5jb20vcGtp\n" +
        "L2NwczAzBggrBgEFBQcCAjAnFiVWYWxpZGF0ZWQgRm9yIFN1biBCdXNpbmVzcyBP\n" +
        "cGVyYXRpb25zMBMGA1UdJQQMMAoGCCsGAQUFBwMDMA0GCSqGSIb3DQEBBQUAA4IB\n" +
        "AQCG5soy3LFHTFbA8/5SzDRhQoJkHUnOP0t3b6nvX6vZYRp649fje7TQOPRm1pFd\n" +
        "CZ17J+tggdZwgzTqY4aYpJ00jZaK6pV37q/vgFC/ia6jDs8Q+ly9cEcadBZ5loYg\n" +
        "cmxp9p57W2MNWx8VA8oFdNtKfF0jUNXbLNtvwGHmgR6YcwLrGN1b6/9Lt9bO3ODl\n" +
        "FO+ZDwkfQz5ClUVrTx2dGBvKRYFqSG5S8JAfsgYhPvcacUQkA7ExyKvfRXLWVrce\n" +
        "ZiPpcElbx+819H2sAPvVvparVeAruZGMAtejHZp9NFoowKen5drJp9VxePS4eM49\n" +
        "3DepB6lKRrNRw66LNQol4ZBz\n" +
        "-----END CERTIFICATE-----");


        // -----------------------------------------------------------------
        // Compromised CAs of DigiNotar
        //
        // Reported by Fox-IT in its interim report on September 5, 2011,
        // "DigiNotar Certificate Authority breach 'Operation Black Tulip'".
        //

        //
        // Compromised DigiNotar Cyber CA
        //

        // DigiNotar intermediate, cross-signed by CyberTrust
        //
        // Subject: EMAILADDRESS=info@diginotar.nl, CN=DigiNotar Cyber CA,
        //          O=DigiNotar, C=NL
        // Issuer:  CN=GTE CyberTrust Global Root,
        //          OU=GTE CyberTrust Solutions, Inc.,
        //          O=GTE Corporation,
        //          C=US
        // Serial:  120000525 (07:27:10:0D)
        add("info-at-diginotar-cyber-ca-cross-to-gte-cybertrust-0727100D",
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIFWjCCBMOgAwIBAgIEBycQDTANBgkqhkiG9w0BAQUFADB1MQswCQYDVQQGEwJV\n" +
        "UzEYMBYGA1UEChMPR1RFIENvcnBvcmF0aW9uMScwJQYDVQQLEx5HVEUgQ3liZXJU\n" +
        "cnVzdCBTb2x1dGlvbnMsIEluYy4xIzAhBgNVBAMTGkdURSBDeWJlclRydXN0IEds\n" +
        "b2JhbCBSb290MB4XDTA2MTAwNDEwNTQxMVoXDTExMTAwNDEwNTMxMVowYDELMAkG\n" +
        "A1UEBhMCTkwxEjAQBgNVBAoTCURpZ2lOb3RhcjEbMBkGA1UEAxMSRGlnaU5vdGFy\n" +
        "IEN5YmVyIENBMSAwHgYJKoZIhvcNAQkBFhFpbmZvQGRpZ2lub3Rhci5ubDCCAiIw\n" +
        "DQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIBANLOFQotqF6EZ639vu9Gx8i5z3P8\n" +
        "9DS5+SxD52ATPXrjss87Z2yQrcC5P4RS8DVC3HTcKDu9UrSnrHJFF8bwieu0qiXy\n" +
        "XUte0dmHutZ9fPXOMp8QM8WxSrtekTHC0OlBwpFkfglBO9uLCDdqqspS3rU5HsCI\n" +
        "A6U/i5kTYUO1m4Kz7iBvz6FEouova0CfjytXraFTwoUiaZ2gP1HfC0GRDaXhqKpc\n" +
        "SQhdvd5wQbEPyWNr0380dAIvNFp4dRxoeoFnivPaQPBgY/SSINcDpj2jHmfEhBtB\n" +
        "pcmM5r3qSLYFFgizNxJa92E89zhvLpfgb1Y4VNMota0Ubi5LZLUnZbd1JQm2Bz2V\n" +
        "VgIKgmCyc0XgMyZRdJq51FAc9k1bW1JSE1qmf6cO4ehBVGeYjIfVydNsy9NUkgYJ\n" +
        "NEH3gW8/nsl8dVWw58Gzd+jDxAA1lUBwEEoF3iW7n1mlZLxHYL9g43aLE1Xd4XR6\n" +
        "uc8kpmp/3mQiRFhogmoQ+T3lPhu5vfwi9GAEibtVbShV+t6OjRshFNc3izR7Tfay\n" +
        "shDPM7F9HGKZSMsrbHaWVb8ZDR0fu2WqG46ZtcYokOWCLXhQIJr9eS8kf/CJKWn0\n" +
        "fc1zvrPtTsHR7VJej/e4142HrbLZG1ES/1az4a80fVykeIgQnp0DxqWqoiRR90kU\n" +
        "xbHuWUOV36toKDA/AgMBAAGjggGGMIIBgjASBgNVHRMBAf8ECDAGAQH/AgEBMFMG\n" +
        "A1UdIARMMEowSAYJKwYBBAGxPgEAMDswOQYIKwYBBQUHAgEWLWh0dHA6Ly93d3cu\n" +
        "cHVibGljLXRydXN0LmNvbS9DUFMvT21uaVJvb3QuaHRtbDAOBgNVHQ8BAf8EBAMC\n" +
        "AQYwgaAGA1UdIwSBmDCBlYAUpgwdn2H/Bxe1vzhG20Mw1Y6wUgaheaR3MHUxCzAJ\n" +
        "BgNVBAYTAlVTMRgwFgYDVQQKEw9HVEUgQ29ycG9yYXRpb24xJzAlBgNVBAsTHkdU\n" +
        "RSBDeWJlclRydXN0IFNvbHV0aW9ucywgSW5jLjEjMCEGA1UEAxMaR1RFIEN5YmVy\n" +
        "VHJ1c3QgR2xvYmFsIFJvb3SCAgGlMEUGA1UdHwQ+MDwwOqA4oDaGNGh0dHA6Ly93\n" +
        "d3cucHVibGljLXRydXN0LmNvbS9jZ2ktYmluL0NSTC8yMDE4L2NkcC5jcmwwHQYD\n" +
        "VR0OBBYEFKv5aN/PSjfXe0WMX3LeQETDZbvCMA0GCSqGSIb3DQEBBQUAA4GBAI9o\n" +
        "a6VbB7pEZg4cqFwwezPkCiYE/O+eGjjWLqEf0JlHwnVkJP2eOyh2uSYoYZEMbSz4\n" +
        "BJ98UAHV42mv7xXSRZskCSpmBU8lgcpdvqrBWSeuM46C9990sFWzjvjnN8huqlZE\n" +
        "9r1TgSOWPbT6MopTZkQloiXGpjwljPDgKAYityZB\n" +
        "-----END CERTIFICATE-----");

        // DigiNotar intermediate, cross-signed by CyberTrust
        //
        // Subject: CN=DigiNotar Cyber CA, O=DigiNotar, C=NL
        // Issuer:  CN=GTE CyberTrust Global Root,
        //          OU=GTE CyberTrust Solutions, Inc.,
        //          O=GTE Corporation,
        //          C=US
        // Serial:  120000505 (07:27:0F:F9)
        add("diginotar-cyber-ca-cross-to-gte-cybertrust-07270FF9",
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIFODCCBKGgAwIBAgIEBycP+TANBgkqhkiG9w0BAQUFADB1MQswCQYDVQQGEwJV\n" +
        "UzEYMBYGA1UEChMPR1RFIENvcnBvcmF0aW9uMScwJQYDVQQLEx5HVEUgQ3liZXJU\n" +
        "cnVzdCBTb2x1dGlvbnMsIEluYy4xIzAhBgNVBAMTGkdURSBDeWJlclRydXN0IEds\n" +
        "b2JhbCBSb290MB4XDTA2MDkyMDA5NDUzMloXDTEzMDkyMDA5NDQwNlowPjELMAkG\n" +
        "A1UEBhMCTkwxEjAQBgNVBAoTCURpZ2lOb3RhcjEbMBkGA1UEAxMSRGlnaU5vdGFy\n" +
        "IEN5YmVyIENBMIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA0s4VCi2o\n" +
        "XoRnrf2+70bHyLnPc/z0NLn5LEPnYBM9euOyzztnbJCtwLk/hFLwNULcdNwoO71S\n" +
        "tKesckUXxvCJ67SqJfJdS17R2Ye61n189c4ynxAzxbFKu16RMcLQ6UHCkWR+CUE7\n" +
        "24sIN2qqylLetTkewIgDpT+LmRNhQ7WbgrPuIG/PoUSi6i9rQJ+PK1etoVPChSJp\n" +
        "naA/Ud8LQZENpeGoqlxJCF293nBBsQ/JY2vTfzR0Ai80Wnh1HGh6gWeK89pA8GBj\n" +
        "9JIg1wOmPaMeZ8SEG0GlyYzmvepItgUWCLM3Elr3YTz3OG8ul+BvVjhU0yi1rRRu\n" +
        "LktktSdlt3UlCbYHPZVWAgqCYLJzReAzJlF0mrnUUBz2TVtbUlITWqZ/pw7h6EFU\n" +
        "Z5iMh9XJ02zL01SSBgk0QfeBbz+eyXx1VbDnwbN36MPEADWVQHAQSgXeJbufWaVk\n" +
        "vEdgv2DjdosTVd3hdHq5zySman/eZCJEWGiCahD5PeU+G7m9/CL0YASJu1VtKFX6\n" +
        "3o6NGyEU1zeLNHtN9rKyEM8zsX0cYplIyytsdpZVvxkNHR+7Zaobjpm1xiiQ5YIt\n" +
        "eFAgmv15LyR/8IkpafR9zXO+s+1OwdHtUl6P97jXjYetstkbURL/VrPhrzR9XKR4\n" +
        "iBCenQPGpaqiJFH3SRTFse5ZQ5Xfq2goMD8CAwEAAaOCAYYwggGCMBIGA1UdEwEB\n" +
        "/wQIMAYBAf8CAQEwUwYDVR0gBEwwSjBIBgkrBgEEAbE+AQAwOzA5BggrBgEFBQcC\n" +
        "ARYtaHR0cDovL3d3dy5wdWJsaWMtdHJ1c3QuY29tL0NQUy9PbW5pUm9vdC5odG1s\n" +
        "MA4GA1UdDwEB/wQEAwIBBjCBoAYDVR0jBIGYMIGVgBSmDB2fYf8HF7W/OEbbQzDV\n" +
        "jrBSBqF5pHcwdTELMAkGA1UEBhMCVVMxGDAWBgNVBAoTD0dURSBDb3Jwb3JhdGlv\n" +
        "bjEnMCUGA1UECxMeR1RFIEN5YmVyVHJ1c3QgU29sdXRpb25zLCBJbmMuMSMwIQYD\n" +
        "VQQDExpHVEUgQ3liZXJUcnVzdCBHbG9iYWwgUm9vdIICAaUwRQYDVR0fBD4wPDA6\n" +
        "oDigNoY0aHR0cDovL3d3dy5wdWJsaWMtdHJ1c3QuY29tL2NnaS1iaW4vQ1JMLzIw\n" +
        "MTgvY2RwLmNybDAdBgNVHQ4EFgQUq/lo389KN9d7RYxfct5ARMNlu8IwDQYJKoZI\n" +
        "hvcNAQEFBQADgYEACcpiD427SuDUejUrBi3RKGG2rAH7g0m8rtQvLYauGYOl1h0T\n" +
        "4he+/jJ06XoUOMqUXvcpAWlxG5Ea/aO7qh3Ke+IW/aGjDvMMX7LhIDGUK16Sdu36\n" +
        "6bUjpr8KOwOpb1JgVM1f6bcvfKIn/UGDdbYN+3gm87FF6TKVKho1IZXFonU=\n" +
        "-----END CERTIFICATE-----");

        // DigiNotar intermediate, cross-signed by CyberTrust
        //
        // Subject: CN=DigiNotar Cyber CA, O=DigiNotar, C=NL
        // Issuer:  CN=GTE CyberTrust Global Root,
        //          OU=GTE CyberTrust Solutions, Inc.,
        //          O=GTE Corporation,
        //          C=US
        // Serial:  120000515 (07:27:10:03)
        add("diginotar-cyber-ca-cross-to-gte-cybertrust-07271003",
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIFODCCBKGgAwIBAgIEBycQAzANBgkqhkiG9w0BAQUFADB1MQswCQYDVQQGEwJV\n" +
        "UzEYMBYGA1UEChMPR1RFIENvcnBvcmF0aW9uMScwJQYDVQQLEx5HVEUgQ3liZXJU\n" +
        "cnVzdCBTb2x1dGlvbnMsIEluYy4xIzAhBgNVBAMTGkdURSBDeWJlclRydXN0IEds\n" +
        "b2JhbCBSb290MB4XDTA2MDkyNzEwNTMzMloXDTExMDkyNzEwNTIzMFowPjELMAkG\n" +
        "A1UEBhMCTkwxEjAQBgNVBAoTCURpZ2lOb3RhcjEbMBkGA1UEAxMSRGlnaU5vdGFy\n" +
        "IEN5YmVyIENBMIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA0s4VCi2o\n" +
        "XoRnrf2+70bHyLnPc/z0NLn5LEPnYBM9euOyzztnbJCtwLk/hFLwNULcdNwoO71S\n" +
        "tKesckUXxvCJ67SqJfJdS17R2Ye61n189c4ynxAzxbFKu16RMcLQ6UHCkWR+CUE7\n" +
        "24sIN2qqylLetTkewIgDpT+LmRNhQ7WbgrPuIG/PoUSi6i9rQJ+PK1etoVPChSJp\n" +
        "naA/Ud8LQZENpeGoqlxJCF293nBBsQ/JY2vTfzR0Ai80Wnh1HGh6gWeK89pA8GBj\n" +
        "9JIg1wOmPaMeZ8SEG0GlyYzmvepItgUWCLM3Elr3YTz3OG8ul+BvVjhU0yi1rRRu\n" +
        "LktktSdlt3UlCbYHPZVWAgqCYLJzReAzJlF0mrnUUBz2TVtbUlITWqZ/pw7h6EFU\n" +
        "Z5iMh9XJ02zL01SSBgk0QfeBbz+eyXx1VbDnwbN36MPEADWVQHAQSgXeJbufWaVk\n" +
        "vEdgv2DjdosTVd3hdHq5zySman/eZCJEWGiCahD5PeU+G7m9/CL0YASJu1VtKFX6\n" +
        "3o6NGyEU1zeLNHtN9rKyEM8zsX0cYplIyytsdpZVvxkNHR+7Zaobjpm1xiiQ5YIt\n" +
        "eFAgmv15LyR/8IkpafR9zXO+s+1OwdHtUl6P97jXjYetstkbURL/VrPhrzR9XKR4\n" +
        "iBCenQPGpaqiJFH3SRTFse5ZQ5Xfq2goMD8CAwEAAaOCAYYwggGCMBIGA1UdEwEB\n" +
        "/wQIMAYBAf8CAQEwUwYDVR0gBEwwSjBIBgkrBgEEAbE+AQAwOzA5BggrBgEFBQcC\n" +
        "ARYtaHR0cDovL3d3dy5wdWJsaWMtdHJ1c3QuY29tL0NQUy9PbW5pUm9vdC5odG1s\n" +
        "MA4GA1UdDwEB/wQEAwIBBjCBoAYDVR0jBIGYMIGVgBSmDB2fYf8HF7W/OEbbQzDV\n" +
        "jrBSBqF5pHcwdTELMAkGA1UEBhMCVVMxGDAWBgNVBAoTD0dURSBDb3Jwb3JhdGlv\n" +
        "bjEnMCUGA1UECxMeR1RFIEN5YmVyVHJ1c3QgU29sdXRpb25zLCBJbmMuMSMwIQYD\n" +
        "VQQDExpHVEUgQ3liZXJUcnVzdCBHbG9iYWwgUm9vdIICAaUwRQYDVR0fBD4wPDA6\n" +
        "oDigNoY0aHR0cDovL3d3dy5wdWJsaWMtdHJ1c3QuY29tL2NnaS1iaW4vQ1JMLzIw\n" +
        "MTgvY2RwLmNybDAdBgNVHQ4EFgQUq/lo389KN9d7RYxfct5ARMNlu8IwDQYJKoZI\n" +
        "hvcNAQEFBQADgYEAWcyGZhizJlRP1jjNupZey+yZG6oMDW4Z11boriMHbYPCndBE\n" +
        "bVh07zmPbZsihOw9w/vm5KbVX5CgxUv4Rhzh/20Faixf3P3bpWg0qgzHVVusNVR/\n" +
        "P50aKkpdK3hp+QLl56e+lWOddSAINIpmcuyDI1hyuzB+GJEASm9tNU/6rs8=\n" +
        "-----END CERTIFICATE-----");

        //
        // Compromised DigiNotar Root CA
        //

        // DigiNotar intermediate, cross-signed by Entrust
        //
        // Subject: EMAILADDRESS=info@diginotar.nl,
        //          CN=DigiNotar Root CA,
        //          O=DigiNotar, C=NL
        // Issuer:  CN=Entrust.net Secure Server Certification Authority
        //          OU=(c) 1999 Entrust.net Limited,
        //          OU=www.entrust.net/CPS incorp. by ref. (limits liab.),
        //          O=Entrust.net,
        //          C=US,
        // Serial:  1184644297 (46:9C:3C:C9)
        add("info-at-diginotar-root-ca-cross-to-entrust-secure-server-469C3CC9",
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIFSDCCBLGgAwIBAgIERpw8yTANBgkqhkiG9w0BAQUFADCBwzELMAkGA1UEBhMC\n" +
        "VVMxFDASBgNVBAoTC0VudHJ1c3QubmV0MTswOQYDVQQLEzJ3d3cuZW50cnVzdC5u\n" +
        "ZXQvQ1BTIGluY29ycC4gYnkgcmVmLiAobGltaXRzIGxpYWIuKTElMCMGA1UECxMc\n" +
        "KGMpIDE5OTkgRW50cnVzdC5uZXQgTGltaXRlZDE6MDgGA1UEAxMxRW50cnVzdC5u\n" +
        "ZXQgU2VjdXJlIFNlcnZlciBDZXJ0aWZpY2F0aW9uIEF1dGhvcml0eTAeFw0wNzA0\n" +
        "MjYwNTAwMDBaFw0xMzA4MTQyMDEyMzZaMF8xCzAJBgNVBAYTAk5MMRIwEAYDVQQK\n" +
        "EwlEaWdpTm90YXIxGjAYBgNVBAMTEURpZ2lOb3RhciBSb290IENBMSAwHgYJKoZI\n" +
        "hvcNAQkBFhFpbmZvQGRpZ2lub3Rhci5ubDCCAiIwDQYJKoZIhvcNAQEBBQADggIP\n" +
        "ADCCAgoCggIBAKywWMEAvdghCAsrmv5uVjAFnxt3kBBBXMMNhxF3joHxynzpjGrt\n" +
        "OHQ1u9rf+bvACTe0lnOBfTMamDn3k2+Vfz25sXWHulFI6ItwPpUExdi2wxbZiLCx\n" +
        "hx1w2oa0DxSLes8Q0XQ2ohJ7d4ZKeeZ73wIRaKVOhq40WJskE3hWIiUeAYtLUXH7\n" +
        "gsxZlmmIWmhTxbkNAjfLS7xmSpB+KgsFB+0WX1WQddhGyRuD4gi+8SPMmR3WKg+D\n" +
        "IBVYJ4Iu+uIiwkmxuQGBap1tnUB3aHZOISpthECFTnaZfILz87cCWdQmARuO361T\n" +
        "BtGuGN3isjrL14g4jqxbKbkZ05j5GAPPSIKGZgsbaQ/J6ziIeiYaBUyS1yTUlvKs\n" +
        "Ui2jR9VS9j/+zoQGcKaqPqLytlY0GFei5IFt58rwatPHkWsCg0F8Fe9rmmRe49A8\n" +
        "5bHre12G+8vmd0nNo2Xc97mcuOQLX5PPzDAaMhzOHGOVpfnq4XSLnukrqTB7oBgf\n" +
        "DhgL5Vup09FsHgdnj5FLqYq80maqkwGIspH6MVzVpsFSCAnNCmOi0yKm6KHZOQaX\n" +
        "9W6NApCMFHs/gM0bnLrEWHIjr7ZWn8Z6QjMpBz+CyeYfBQ3NTCg2i9PIPhzGiO9e\n" +
        "7olk6R3r2ol+MqZp0d3MiJ/R0MlmIdwGZ8WUepptYkx9zOBkgLKeR46jAgMBAAGj\n" +
        "ggEmMIIBIjASBgNVHRMBAf8ECDAGAQH/AgEBMCcGA1UdJQQgMB4GCCsGAQUFBwMB\n" +
        "BggrBgEFBQcDAgYIKwYBBQUHAwQwEQYDVR0gBAowCDAGBgRVHSAAMDMGCCsGAQUF\n" +
        "BwEBBCcwJTAjBggrBgEFBQcwAYYXaHR0cDovL29jc3AuZW50cnVzdC5uZXQwMwYD\n" +
        "VR0fBCwwKjAooCagJIYiaHR0cDovL2NybC5lbnRydXN0Lm5ldC9zZXJ2ZXIxLmNy\n" +
        "bDAdBgNVHQ4EFgQUiGi/4I41xDs4a2L3KDuEgcgM100wCwYDVR0PBAQDAgEGMB8G\n" +
        "A1UdIwQYMBaAFPAXYhNVPbP/CgBr+1CEl/PtYtAaMBkGCSqGSIb2fQdBAAQMMAob\n" +
        "BFY3LjEDAgCBMA0GCSqGSIb3DQEBBQUAA4GBAI979rBep8tu3TeLunapgsZ0jtXp\n" +
        "GDFjKWSk87dj1jCyYi+q/GyDyZ6ZQZNRP0sF+6twscq05lClWNy3TROMp7QeuoLO\n" +
        "G7Utw3OJaswUtp4YglANMRTHEe3g9ltifUXRH5tSuy7u6yi4LD4WTm5ULP6r/g6l\n" +
        "0CnjXYb0+b1Fmz6U\n" +
        "-----END CERTIFICATE-----");

        // DigiNotar intermediate, cross-signed by Entrust
        //
        // Subject: EMAILADDRESS=info@diginotar.nl,
        //          CN=DigiNotar Root CA,
        //          O=DigiNotar, C=NL
        // Issuer:  CN=Entrust.net Secure Server Certification Authority
        //          OU=(c) 1999 Entrust.net Limited,
        //          OU=www.entrust.net/CPS incorp. by ref. (limits liab.),
        //          O=Entrust.net,
        //          C=US,
        // Serial:  1184640175 (46:9C:2C:AF)
        add("info-at-diginotar-root-ca-cross-to-entrust-secure-server-469C2CAF",
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIFSDCCBLGgAwIBAgIERpwsrzANBgkqhkiG9w0BAQUFADCBwzELMAkGA1UEBhMC\n" +
        "VVMxFDASBgNVBAoTC0VudHJ1c3QubmV0MTswOQYDVQQLEzJ3d3cuZW50cnVzdC5u\n" +
        "ZXQvQ1BTIGluY29ycC4gYnkgcmVmLiAobGltaXRzIGxpYWIuKTElMCMGA1UECxMc\n" +
        "KGMpIDE5OTkgRW50cnVzdC5uZXQgTGltaXRlZDE6MDgGA1UEAxMxRW50cnVzdC5u\n" +
        "ZXQgU2VjdXJlIFNlcnZlciBDZXJ0aWZpY2F0aW9uIEF1dGhvcml0eTAeFw0wNzA3\n" +
        "MjYxNTU3MzlaFw0xMzA4MjYxNjI3MzlaMF8xCzAJBgNVBAYTAk5MMRIwEAYDVQQK\n" +
        "EwlEaWdpTm90YXIxGjAYBgNVBAMTEURpZ2lOb3RhciBSb290IENBMSAwHgYJKoZI\n" +
        "hvcNAQkBFhFpbmZvQGRpZ2lub3Rhci5ubDCCAiIwDQYJKoZIhvcNAQEBBQADggIP\n" +
        "ADCCAgoCggIBAKywWMEAvdghCAsrmv5uVjAFnxt3kBBBXMMNhxF3joHxynzpjGrt\n" +
        "OHQ1u9rf+bvACTe0lnOBfTMamDn3k2+Vfz25sXWHulFI6ItwPpUExdi2wxbZiLCx\n" +
        "hx1w2oa0DxSLes8Q0XQ2ohJ7d4ZKeeZ73wIRaKVOhq40WJskE3hWIiUeAYtLUXH7\n" +
        "gsxZlmmIWmhTxbkNAjfLS7xmSpB+KgsFB+0WX1WQddhGyRuD4gi+8SPMmR3WKg+D\n" +
        "IBVYJ4Iu+uIiwkmxuQGBap1tnUB3aHZOISpthECFTnaZfILz87cCWdQmARuO361T\n" +
        "BtGuGN3isjrL14g4jqxbKbkZ05j5GAPPSIKGZgsbaQ/J6ziIeiYaBUyS1yTUlvKs\n" +
        "Ui2jR9VS9j/+zoQGcKaqPqLytlY0GFei5IFt58rwatPHkWsCg0F8Fe9rmmRe49A8\n" +
        "5bHre12G+8vmd0nNo2Xc97mcuOQLX5PPzDAaMhzOHGOVpfnq4XSLnukrqTB7oBgf\n" +
        "DhgL5Vup09FsHgdnj5FLqYq80maqkwGIspH6MVzVpsFSCAnNCmOi0yKm6KHZOQaX\n" +
        "9W6NApCMFHs/gM0bnLrEWHIjr7ZWn8Z6QjMpBz+CyeYfBQ3NTCg2i9PIPhzGiO9e\n" +
        "7olk6R3r2ol+MqZp0d3MiJ/R0MlmIdwGZ8WUepptYkx9zOBkgLKeR46jAgMBAAGj\n" +
        "ggEmMIIBIjASBgNVHRMBAf8ECDAGAQH/AgEBMCcGA1UdJQQgMB4GCCsGAQUFBwMB\n" +
        "BggrBgEFBQcDAgYIKwYBBQUHAwQwEQYDVR0gBAowCDAGBgRVHSAAMDMGCCsGAQUF\n" +
        "BwEBBCcwJTAjBggrBgEFBQcwAYYXaHR0cDovL29jc3AuZW50cnVzdC5uZXQwMwYD\n" +
        "VR0fBCwwKjAooCagJIYiaHR0cDovL2NybC5lbnRydXN0Lm5ldC9zZXJ2ZXIxLmNy\n" +
        "bDAdBgNVHQ4EFgQUiGi/4I41xDs4a2L3KDuEgcgM100wCwYDVR0PBAQDAgEGMB8G\n" +
        "A1UdIwQYMBaAFPAXYhNVPbP/CgBr+1CEl/PtYtAaMBkGCSqGSIb2fQdBAAQMMAob\n" +
        "BFY3LjEDAgCBMA0GCSqGSIb3DQEBBQUAA4GBAEa6RcDNcEIGUlkDJUY/pWTds4zh\n" +
        "xbVkp3wSmpwPFhx5fxTyF4HD2L60jl3aqjTB7gPpsL2Pk5QZlNsi3t4UkCV70UOd\n" +
        "ueJRN3o/LOtk4+bjXY2lC0qTHbN80VMLqPjmaf9ghSA9hwhskdtMgRsgfd90q5QP\n" +
        "ZFdYf+hthc3m6IcJ\n" +
        "-----END CERTIFICATE-----");

        //
        // Compromised DigiNotar PKIoverheid CA Organisatie - G2
        //

        // DigiNotar intermediate, cross-signed by the Dutch government
        //
        // Subject: CN=DigiNotar PKIoverheid CA Organisatie - G2,
        //          O=DigiNotar B.V.,
        //          C=NL
        // Issuer:  CN=Staat der Nederlanden Organisatie CA - G2,
        //          O=Staat der Nederlanden,
        //          C=NL
        // Serial:  20001983 (01:31:34:bf)
        add("diginotar-pkioverheid-organisatie-cross-to-nederlanden-013134BF",
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIGnDCCBISgAwIBAgIEATE0vzANBgkqhkiG9w0BAQsFADBhMQswCQYDVQQGEwJO\n" +
        "TDEeMBwGA1UECgwVU3RhYXQgZGVyIE5lZGVybGFuZGVuMTIwMAYDVQQDDClTdGFh\n" +
        "dCBkZXIgTmVkZXJsYW5kZW4gT3JnYW5pc2F0aWUgQ0EgLSBHMjAeFw0xMDA1MTIw\n" +
        "ODUxMzhaFw0yMDAzMjMwOTUwMDRaMFoxCzAJBgNVBAYTAk5MMRcwFQYDVQQKDA5E\n" +
        "aWdpTm90YXIgQi5WLjEyMDAGA1UEAwwpRGlnaU5vdGFyIFBLSW92ZXJoZWlkIENB\n" +
        "IE9yZ2FuaXNhdGllIC0gRzIwggIiMA0GCSqGSIb3DQEBAQUAA4ICDwAwggIKAoIC\n" +
        "AQCxExkPJ+Zs1FWGS9DsiYpFkXisR71HK+T8RetPtCZzWzfTw3/2497Xo/gtaMUI\n" +
        "PkuU1uSHJTZrhLUYdPMoWHMvm2rPvAQe9t7dr/xLqvXbZmIlASWC3vKXWhBu3V2p\n" +
        "IrEEqSNzOvhxrR3PhETrR9Gvbch8KKvH8jd6dF9fxQIUiqNa4xtsAeNdjtlo1vQJ\n" +
        "GzLckbUs9SDrjANtJkm4k8SFXdjSm69WaswFM8ygQp40VUSca6DUEtArVM23iQ3l\n" +
        "9uvo+4UBM096a/GdcjOWDveyhKWlJ8Qn8VFzKXe6Z27+TNy04qGhgS85SY1DOBPO\n" +
        "0KVcwoc6AGdlQiPxNlkKHaNRyLyjlCox3+M88p0aPASw77EKMBNzttfzo0wBdRSF\n" +
        "eMDXijlYhVD6LubFvs+LP6+PNtQlCS3SD6xyk/K/i9RQs/kVUJuZ9RTZ+4uRozIm\n" +
        "JqD43ztggYaDeVsr6xM9KTrBbd29no6H1kquNJcF7hSm9tw4fkrpJFQHPZdoN0Zr\n" +
        "DceoIa8TVOQJavFNRgrJXfubT73e+7dUy7g4nKc5+2otwHuNq6WnV+xKkoozxeEg\n" +
        "XHPYkJIrgNUPhhhpfDlPhIa890xb89W0yqDC8DciynlSH1PmqvOQsDvd8ij9rOvF\n" +
        "BiSgydQvD1j9tZ7sD8+yWdCiBHo4aq5y+73wJWKUCacFCwIDAQABo4IBYTCCAV0w\n" +
        "SAYDVR0gBEEwPzA9BgRVHSAAMDUwMwYIKwYBBQUHAgEWJ2h0dHA6Ly93d3cuZGln\n" +
        "aW5vdGFyLm5sL2Nwcy9wa2lvdmVyaGVpZDAPBgNVHRMBAf8EBTADAQH/MA4GA1Ud\n" +
        "DwEB/wQEAwIBBjCBhQYDVR0jBH4wfIAUORCLSZJc22ESIM1JnRqO2pxnQLmhXqRc\n" +
        "MFoxCzAJBgNVBAYTAk5MMR4wHAYDVQQKDBVTdGFhdCBkZXIgTmVkZXJsYW5kZW4x\n" +
        "KzApBgNVBAMMIlN0YWF0IGRlciBOZWRlcmxhbmRlbiBSb290IENBIC0gRzKCBACY\n" +
        "lvQwSQYDVR0fBEIwQDA+oDygOoY4aHR0cDovL2NybC5wa2lvdmVyaGVpZC5ubC9E\n" +
        "b21PcmdhbmlzYXRpZUxhdGVzdENSTC1HMi5jcmwwHQYDVR0OBBYEFLxdlDvZq3sD\n" +
        "JXNhwtst7vyrj2WhMA0GCSqGSIb3DQEBCwUAA4ICAQCP/C1Mt9kt1R+978v0t2gX\n" +
        "dZ1O1ffdnPEqJu2forYcA9VTs+wIzzTi48P0tRYvyMO+19NzqwA2+RpKftZj6V5G\n" +
        "uqW2jhW3oyrYQx3vXcgfgYWzi/f/PPTZ9EYIP5y8HaDZqEzNJVJOCrEg9x/pQ9lU\n" +
        "RoETmsBedGwqmDLq/He7DaWiMZgifnx859qkrey3LhoZcfhIUNpDjyyE3cFAJ+O1\n" +
        "8BVOltT4XOOGKUYr1zsH6zh/yIZXl9PvKjPEF1DVZGlrK2tFXl0vF8paTs/D1zk8\n" +
        "9TufRrmb5w5Jl53W1eMbD+qPAU6aE5RZCgIHSEsaYKt/T+0L2FUNaG9VnGllFULs\n" +
        "wNzdbKzDFs4LHVabpMTE0i7gD+JEJytQaaTcYuiKISlCbMwAOpZ2m+9AwKRed4Qy\n" +
        "bCYqOWauXeO5ubIsaB8empADOfCqs6TMSYsYNOk3yXspx4R8b0QVL+xhWQTJRcui\n" +
        "1lKifH8pktZKxYtCqNT+6tjHhyMY5J16fXNAUpigrm7jBT8FD+Clxm1N7YM3iJzH\n" +
        "89xCmmq21yFJNnfy7xhPxXDZnunetyuL9Lx+KN8NQMmFXK6dxTH/0FwOtah+8Okv\n" +
        "uq+IruW10Vilr5xxpykBkINpN4IFuvwJwQhujHg7wzMCgD9EhQgd31VWCK0shS1d\n" +
        "sQPhrqp0xaTzTro3mHuCuQ==\n" +
        "-----END CERTIFICATE-----");

        //
        // Compromised DigiNotar PKIoverheid CA Overheid en Bedrijven
        //

        // DigiNotar intermediate, cross-signed by the Dutch government
        //
        // Subject: CN=DigiNotar PKIoverheid CA Overheid en Bedrijven,
        //          O=DigiNotar B.V.,
        //          C=NL
        // Issuer:  CN=Staat der Nederlanden Overheid CA
        //          O=Staat der Nederlanden,
        //          C=NL
        // Serial:  20015536 (01:31:69:b0)
        add("diginotar-pkioverheid-overheid-enb-cross-to-nederlanden-013169B0",
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIEiDCCA3CgAwIBAgIEATFpsDANBgkqhkiG9w0BAQUFADBZMQswCQYDVQQGEwJO\n" +
        "TDEeMBwGA1UEChMVU3RhYXQgZGVyIE5lZGVybGFuZGVuMSowKAYDVQQDEyFTdGFh\n" +
        "dCBkZXIgTmVkZXJsYW5kZW4gT3ZlcmhlaWQgQ0EwHhcNMDcwNzA1MDg0MjA3WhcN\n" +
        "MTUwNzI3MDgzOTQ2WjBfMQswCQYDVQQGEwJOTDEXMBUGA1UEChMORGlnaU5vdGFy\n" +
        "IEIuVi4xNzA1BgNVBAMTLkRpZ2lOb3RhciBQS0lvdmVyaGVpZCBDQSBPdmVyaGVp\n" +
        "ZCBlbiBCZWRyaWp2ZW4wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDc\n" +
        "vdKnTmoKuzuiheF/AK2+tDBomAfNoHrElM9x+Yo35FPrV3bMi+Zs/u6HVcg+uwQ5\n" +
        "AKeAeKxbT370vbhUuHE7BzFJOZNUfCA7eSuPu2GQfbGs5h+QLp1FAalkLU3DL7nn\n" +
        "UNVOKlyrdnY3Rtd57EKZ96LspIlw3Dgrh6aqJOadkiQbvvb91C8ZF3rmMgeUVAVT\n" +
        "Q+lsvK9Hy7zL/b07RBKB8WtLu+20z6slTxjSzAL8o0+1QjPLWc0J3NNQ/aB2jKx+\n" +
        "ZopC9q0ckvO2+xRG603XLzDgbe5bNr5EdLcgBVeFTegAGaL2DOauocBC36esgl3H\n" +
        "aLcY5olLmmv6znn58yynAgMBAAGjggFQMIIBTDBIBgNVHSAEQTA/MD0GBFUdIAAw\n" +
        "NTAzBggrBgEFBQcCARYnaHR0cDovL3d3dy5kaWdpbm90YXIubmwvY3BzL3BraW92\n" +
        "ZXJoZWlkMA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgEGMIGABgNVHSME\n" +
        "eTB3gBQLhtYPd6NosftkCcOIblwEHFfpPaFZpFcwVTELMAkGA1UEBhMCTkwxHjAc\n" +
        "BgNVBAoTFVN0YWF0IGRlciBOZWRlcmxhbmRlbjEmMCQGA1UEAxMdU3RhYXQgZGVy\n" +
        "IE5lZGVybGFuZGVuIFJvb3QgQ0GCBACYmnkwPQYDVR0fBDYwNDAyoDCgLoYsaHR0\n" +
        "cDovL2NybC5wa2lvdmVyaGVpZC5ubC9Eb21PdkxhdGVzdENSTC5jcmwwHQYDVR0O\n" +
        "BBYEFEwIyY128ZjHPt881y91DbF2eZfMMA0GCSqGSIb3DQEBBQUAA4IBAQAMlIca\n" +
        "v03jheLu19hjeQ5Q38aEW9K72fUxCho1l3TfFPoqDz7toOMI9tVOW6+mriXiRWsi\n" +
        "D7dUKH6S3o0UbNEc5W50BJy37zRERd/Jgx0ZH8Apad+J1T/CsFNt5U4X5HNhIxMm\n" +
        "cUP9TFnLw98iqiEr2b+VERqKpOKrp11Lbyn1UtHk0hWxi/7wA8+nfemZhzizDXMU\n" +
        "5HIs4c71rQZIZPrTKbmi2Lv01QulQERDjqC/zlqlUkxk0xcxYczopIro5Ij76eUv\n" +
        "BjMzm5RmZrGrUDqhCYF0U1onuabSJc/Tw6f/ltAv6uAejVLpGBwgCkegllYOQJBR\n" +
        "RKwa/fHuhR/3Qlpl\n" +
        "-----END CERTIFICATE-----");

        //
        // Compromised DigiNotar PKIoverheid CA Overheid
        //

        // DigiNotar intermediate, cross-signed by the Dutch government
        //
        // Subject: CN=DigiNotar PKIoverheid CA Overheid
        //          O=DigiNotar B.V.,
        //          C=NL
        // Issuer:  CN=Staat der Nederlanden Overheid CA
        //          O=Staat der Nederlanden,
        //          C=NL
        // Serial:  20006006 (01:31:44:76)
        add("diginotar-pkioverheid-overheid-cross-to-nederlanden-01314476",
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIEezCCA2OgAwIBAgIEATFEdjANBgkqhkiG9w0BAQUFADBZMQswCQYDVQQGEwJO\n" +
        "TDEeMBwGA1UEChMVU3RhYXQgZGVyIE5lZGVybGFuZGVuMSowKAYDVQQDEyFTdGFh\n" +
        "dCBkZXIgTmVkZXJsYW5kZW4gT3ZlcmhlaWQgQ0EwHhcNMDQwNjI0MDgxOTMyWhcN\n" +
        "MTAwNjIzMDgxNzM2WjBSMQswCQYDVQQGEwJOTDEXMBUGA1UEChMORGlnaU5vdGFy\n" +
        "IEIuVi4xKjAoBgNVBAMTIURpZ2lOb3RhciBQS0lvdmVyaGVpZCBDQSBPdmVyaGVp\n" +
        "ZDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBANSlrubta5tlOjVCi/gb\n" +
        "yLCvRqfBjxG8H594VcKHu0WAYc99SPZF9cycj5mw2GyfQvy/WIrGrL4iyNq1gSqR\n" +
        "0QA/mTXKZIaPqzpDhdm+VvrKkmjrbZfaQxgMSs3ChtBsjcP9Lc0X1zXZ4Q8nBe3k\n" +
        "BTp+zehINfmbjoEgXLxsMR5RQ6GxzKjuC04PQpbJQgTIakglKaqYcDDZbEscWgPV\n" +
        "Hgj/2aoHlj6leW/ThHZ+O41jUguEmBLZA3mu3HrCfrHntb5dPt0ihzSx7GtD/SaX\n" +
        "5HBLxnP189YuqMk5iRA95CtiSdKauvon/xRKRLNgG6XAz0ctSoY7xLDdiBVU5kJd\n" +
        "FScCAwEAAaOCAVAwggFMMEgGA1UdIARBMD8wPQYEVR0gADA1MDMGCCsGAQUFBwIB\n" +
        "FidodHRwOi8vd3d3LmRpZ2lub3Rhci5ubC9jcHMvcGtpb3ZlcmhlaWQwDwYDVR0T\n" +
        "AQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMCAQYwgYAGA1UdIwR5MHeAFAuG1g93o2ix\n" +
        "+2QJw4huXAQcV+k9oVmkVzBVMQswCQYDVQQGEwJOTDEeMBwGA1UEChMVU3RhYXQg\n" +
        "ZGVyIE5lZGVybGFuZGVuMSYwJAYDVQQDEx1TdGFhdCBkZXIgTmVkZXJsYW5kZW4g\n" +
        "Um9vdCBDQYIEAJiaeTA9BgNVHR8ENjA0MDKgMKAuhixodHRwOi8vY3JsLnBraW92\n" +
        "ZXJoZWlkLm5sL0RvbU92TGF0ZXN0Q1JMLmNybDAdBgNVHQ4EFgQUvRaYQh2+kdE9\n" +
        "wpcl4CjXWOC1f+IwDQYJKoZIhvcNAQEFBQADggEBAGhQsCWLiaN2EOhPAW+JQP6o\n" +
        "XBOrLv5w6joahzBFVn1BiefzmlMKjibqKYxURRvMAsMkh82/MfL8V0w6ugxl81lu\n" +
        "i42dcxl9cKSVXKMw4bbBzJ2VQI5HTIABwefeNuy/eX6idVwYdt3ajAH7fUA8Q9Cq\n" +
        "vr6H8B+8mwoEqTVTEVlCSsC/EXsokYEUr06PPzRudKjDmijgj7zFaIioZNc8hk7g\n" +
        "ufEgrs/tmcNGylrwRHgCXjCRBt2NHlZ08l7A1AGU8HcHlSbG9Un/2q9kVHUkps0D\n" +
        "gtUaEK+x6jpAu/R8Ojezu/+ZEcwwjI/KOhG+84+ejFmtyEkrUdsAdEdLf/2dKsw=\n" +
        "-----END CERTIFICATE-----");

        //
        // Compromised DigiNotar Services 1024 CA
        //

        // DigiNotar intermediate, cross-signed by the Entrust
        //
        // Subject: EMAILADDRESS=info@diginotar.nl,
        //          CN=DigiNotar Services 1024 CA
        //          O=DigiNotar, C=NL
        // Issuer:  CN=Entrust.net Secure Server Certification Authority,
        //          OU=(c) 1999 Entrust.net Limited,
        //          OU=www.entrust.net/CPS incorp. by ref. (limits liab.),
        //          O=Entrust.net,
        //          C=US
        // Serial:  1184640176 (46:9c:2c:b0)
        add("diginotar-services-1024-ca-cross-to-entrust-469C2CB0",
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIDzTCCAzagAwIBAgIERpwssDANBgkqhkiG9w0BAQUFADCBwzELMAkGA1UEBhMC\n" +
        "VVMxFDASBgNVBAoTC0VudHJ1c3QubmV0MTswOQYDVQQLEzJ3d3cuZW50cnVzdC5u\n" +
        "ZXQvQ1BTIGluY29ycC4gYnkgcmVmLiAobGltaXRzIGxpYWIuKTElMCMGA1UECxMc\n" +
        "KGMpIDE5OTkgRW50cnVzdC5uZXQgTGltaXRlZDE6MDgGA1UEAxMxRW50cnVzdC5u\n" +
        "ZXQgU2VjdXJlIFNlcnZlciBDZXJ0aWZpY2F0aW9uIEF1dGhvcml0eTAeFw0wNzA3\n" +
        "MjYxNTU5MDBaFw0xMzA4MjYxNjI5MDBaMGgxCzAJBgNVBAYTAk5MMRIwEAYDVQQK\n" +
        "EwlEaWdpTm90YXIxIzAhBgNVBAMTGkRpZ2lOb3RhciBTZXJ2aWNlcyAxMDI0IENB\n" +
        "MSAwHgYJKoZIhvcNAQkBFhFpbmZvQGRpZ2lub3Rhci5ubDCBnzANBgkqhkiG9w0B\n" +
        "AQEFAAOBjQAwgYkCgYEA2ptNXTz50eKLxsYIIMXZHkjsZlhneWIrQWP0iY1o2q+4\n" +
        "lDaLGSSkoJPSmQ+yrS01Tc0vauH5mxkrvAQafi09UmTN8T5nD4ku6PJPrqYIoYX+\n" +
        "oakJ5sarPkP8r3oDkdqmOaZh7phPGKjTs69mgumfvN1y+QYEvRLZGCTnq5NTi1kC\n" +
        "AwEAAaOCASYwggEiMBIGA1UdEwEB/wQIMAYBAf8CAQAwJwYDVR0lBCAwHgYIKwYB\n" +
        "BQUHAwEGCCsGAQUFBwMCBggrBgEFBQcDBDARBgNVHSAECjAIMAYGBFUdIAAwMwYI\n" +
        "KwYBBQUHAQEEJzAlMCMGCCsGAQUFBzABhhdodHRwOi8vb2NzcC5lbnRydXN0Lm5l\n" +
        "dDAzBgNVHR8ELDAqMCigJqAkhiJodHRwOi8vY3JsLmVudHJ1c3QubmV0L3NlcnZl\n" +
        "cjEuY3JsMB0GA1UdDgQWBBT+3JRJDG/vXH/G8RKZTxZJrfuCZTALBgNVHQ8EBAMC\n" +
        "AQYwHwYDVR0jBBgwFoAU8BdiE1U9s/8KAGv7UISX8+1i0BowGQYJKoZIhvZ9B0EA\n" +
        "BAwwChsEVjcuMQMCAIEwDQYJKoZIhvcNAQEFBQADgYEAY3RqN6k/lpxmyFisCcnv\n" +
        "9WWUf6MCxDgxvV0jh+zUVrLJsm7kBQb87PX6iHBZ1O7m3bV6oKNgLwIMq94SXa/w\n" +
        "NUuqikeRGvWFLELHHe+VQ7NeuJWTpdrFKKqtci0xrZlrbP+MISevrZqRK8fdWMNu\n" +
        "B8WfedLHjFW/TMcnXlEWKz4=\n" +
        "-----END CERTIFICATE-----");

    }
}
