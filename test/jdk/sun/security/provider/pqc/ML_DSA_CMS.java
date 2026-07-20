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
 * @bug 8349732
 * @library /test/lib
 * @summary Add support for JARs signed with ML-DSA
 * @modules java.base/sun.security.pkcs
 */
import jdk.test.lib.Asserts;
import jdk.test.lib.security.RepositoryFileReader;
import sun.security.pkcs.PKCS7;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.stream.Collectors;

import static jdk.test.lib.security.RepositoryFileReader.*;

public class ML_DSA_CMS {
    public static void main(String[] args) throws Exception {
        // Example signed-data encodings from RFC 9882, Appendix B
        // (https://datatracker.ietf.org/doc/html/rfc9882#name-examples), which
        // can be verified by example certificates from RFC 9881, Appendix C.3
        // (https://datatracker.ietf.org/doc/html/rfc9881#name-example-certificates)
        //
        // These data can be retrieved from the following GitHub releases:
        //   https://github.com/lamps-wg/cms-ml-dsa/releases/tag/draft-ietf-lamps-cms-ml-dsa-07
        //   https://github.com/lamps-wg/dilithium-certificates/releases/tag/draft-ietf-lamps-dilithium-certificates-13
        //
        // Although the release tags include "draft", these values are the
        // same as those in the final RFCs 9881 and 9882.
        try (var cmsReader = RepositoryFileReader.of(CMS_ML_DSA.class,
                    "cms-ml-dsa-draft-ietf-lamps-cms-ml-dsa-07/");
            var dsaReader = RepositoryFileReader.of(DILITHIUM_CERTIFICATES.class,
                    "dilithium-certificates-draft-ietf-lamps-dilithium-certificates-13/")) {
            test(readCMS(cmsReader, "mldsa44-signed-attrs.pem"),
                    readCert(dsaReader, "ML-DSA-44.crt"));
            test(readCMS(cmsReader, "mldsa65-signed-attrs.pem"),
                    readCert(dsaReader, "ML-DSA-65.crt"));
            test(readCMS(cmsReader, "mldsa87-signed-attrs.pem"),
                    readCert(dsaReader, "ML-DSA-87.crt"));
        }
    }

    /// Verifies a signed file.
    /// @param data the signed data in PKCS #7 format
    /// @param cert the certificate used to verify
    static void test(byte[] data, X509Certificate cert) throws Exception {
        var p7 = new PKCS7(data);
        for (var si : p7.getSignerInfos()) {
            Asserts.assertTrue(p7.verify(si, null, cert).getIssuerName() != null);
        }
    }

    // Read data in https://datatracker.ietf.org/doc/html/rfc9882#name-examples
    static byte[] readCMS(RepositoryFileReader f, String entry) throws IOException  {
        var data = f.read("examples/" + entry);
        var pem = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data)))
                .lines()
                .filter(s -> !s.contains("-----"))
                .collect(Collectors.joining());
        return Base64.getMimeDecoder().decode(pem);
    }

    // Read data in https://datatracker.ietf.org/doc/html/rfc9881#name-example-certificates
    static X509Certificate readCert(RepositoryFileReader f, String entry) throws Exception {
        var data = f.read("examples/" + entry);
        var cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(data));
    }
}
