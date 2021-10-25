/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import jdk.test.lib.security.CertUtils;

/*
 * @test
 * @bug 8251468
 * @library /test/lib
 * @summary Check that X509Certificate.getSubjectAlternativeNames throws
 *          CertificateParsingException if extension is unparseable/invalid.
 */
public class NullRFC822Name {

    private final static String CERT_WITH_NULL_RFC822NAME = """
        -----BEGIN CERTIFICATE-----
        MIIC7DCCAdSgAwIBAgIJAOERuseHYHV2MA0GCSqGSIb3DQEBCwUAMA0xCzAJBgNV
        BAMTAkNBMB4XDTIxMTAyMTIwNTcyMVoXDTIyMDExOTIwNTcyMVowDTELMAkGA1UE
        AxMCRTEwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCiCCLD+oIWfeIS
        JVNxvg9dbwEkj+yTJl+huqKRinpetHWeUqN03Y9BOlR3ocHMRHYfeCxCMbzpEtwn
        YYXdYogeP6tiJ7y7hs0lb1gMGDZAexm0d9aSLLdBgBXR/s/7VY4x3GnARsJsrJPA
        zUZsZuugMicRwNVP7+uL8eZEhjaFABHFpvjrJcX3Se58oJ1Pkumv9OrEb4n1Z+6u
        3CoOACBRNfr1q3IGGwKZDzYy4kRgK990wG9cvpzdc1v5bhDBECCQXfmCVvTqzxjF
        K0g0l3wdOh7HB9W01Hzdp4LMU9ocvXogog7WoDyMGANLFTY8lLXz7IwB9aMQz18w
        uariQef7AgMBAAGjTzBNMB0GA1UdDgQWBBQqljEAZIttXEqiiB6eKxYrX+qUNjAL
        BgNVHREEBDACgQAwHwYDVR0jBBgwFoAUJbvRN17hEohYsg1Micq1mMpIwoAwDQYJ
        KoZIhvcNAQELBQADggEBADYOkYHRDeWEdem8bv/xUISwavKi53QIjxoaaJ0bmVDz
        xuGQ6JK+M++fp6PlnsF3Fg/SE/q1keyuCj5qqawJpm18JhZUpmzbXnYh9ZZum31i
        Z5aMWgphn/pAPkHnVppJWtSTuDNSvQR2WifvQ493p/+WtLIy9ZLufhXqRXFTsWeD
        3myNbuQIO1j7uJFk1VtG8eGRqPjYotzHfwYFK7XbOMrgTbWAvGAbOaPdszkLOme+
        C/c7DDA/6aZNGh9NkKQMiUS3d9FwWJEexsBB4TsZ3GvHMyz8IjMVdaBsBhsl7a8t
        5eZPNXL5hDogw/E6P8I1Ay1ellzuYVzh7IpiAcHpPLg=
        -----END CERTIFICATE-----
        """;

    public static void main(String[] args) throws Exception {

        X509Certificate cert =
                CertUtils.getCertFromString(CERT_WITH_NULL_RFC822NAME);
        try {
            cert.getSubjectAlternativeNames();
            throw new Exception("Test FAILED");
        } catch (CertificateParsingException cpe) {
            System.out.println(cpe.getMessage());
            System.out.println("Test PASSED");
        }
    }
}
