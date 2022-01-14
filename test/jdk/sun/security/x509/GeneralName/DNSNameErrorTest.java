/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8255739
 * @library /test/lib
 * @summary CertificateFactory.generateCertificate should not read invalid
 *          subjectAlternativeNames values.
 * @run main/othervm  DNSNameErrorTest
 * @run main/othervm -Djava.security.debug=x509 DNSNameErrorTest debug.x509
 */

import java.util.Collection;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class DNSNameErrorTest {

    /* Invalid SubjectAlternativeName without criticality
     *   #1: ObjectId: 2.5.29.17 Criticality=false
     *   SubjectAlternativeName [
     *     DNSName: ???.com      ??? is incorrect IA5String 'e2 84 a1'
     *     DNSName: ???.com
     *   ]
     */
    private static String invalidSANCertStr =
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIBSDCB86ADAgECAhRLR4TGgXBegg0np90FZ1KPeWpDtjANBgkqhkiG9w0BAQsF\n" +
        "ADASMRAwDgYDVQQDDAdmb28uY29tMCAXDTIwMTAyOTA2NTkwNVoYDzIxMjAxMDA1\n" +
        "MDY1OTA1WjASMRAwDgYDVQQDDAdmb28uY29tMFwwDQYJKoZIhvcNAQEBBQADSwAw\n" +
        "SAJBALQcTVW9aW++ClIV9/9iSzijsPvQGEu/FQOjIycSrSIheZyZmR8bluSNBq0C\n" +
        "9fpalRKZb0S2tlCTi5WoX8d3K30CAwEAAaMfMB0wGwYDVR0RBBQwEoIH4oShLmNv\n" +
        "bYIH4oSqLmNvbTANBgkqhkiG9w0BAQsFAANBAA1+/eDvSUGv78iEjNW+1w3OPAwt\n" +
        "Ij1qLQ/YI8OogZPMk7YY46/ydWWp7UpD47zy/vKmm4pOc8Glc8MoDD6UADs=\n" +
        "-----END CERTIFICATE-----";

    /* Invalid SubjectAlternativeName with criticality
     *   #1: ObjectId: 2.5.29.17 Criticality=true
     *   SubjectAlternativeName [
     *     DNSName: ???.com      ??? is incorrect IA5String 'e2 84 a1'
     *     DNSName: ???.com
     *   ]
     */
    private static String invalidCriticalSANCertStr =
        "-----BEGIN CERTIFICATE-----\n" +
        "MIIBWTCCAQOgAwIBAgIEN5HYCDANBgkqhkiG9w0BAQsFADASMRAwDgYDVQQDEwdm\n" +
        "b28uY29tMB4XDTIxMTIxNzA5MTEzMVoXDTIyMDMxNzA5MTEzMVowEjEQMA4GA1UE\n" +
        "AxMHZm9vLmNvbTBcMA0GCSqGSIb3DQEBAQUAA0sAMEgCQQCQ/I+QhhOmUywaRIv7\n" +
        "zijFGL0grGZ/iGkob+L0dlO8iZAvV9If8tyCSQY/YDbmxP4X0JByOChJFZLERNrg\n" +
        "8vk1AgMBAAGjQTA/MB4GA1UdEQEB/wQUMBKCB+KEoS5jb22CB+KEqi5jb20wHQYD\n" +
        "VR0OBBYEFNSLW2LFszpr+rIc+ubAfGGLn64oMA0GCSqGSIb3DQEBCwUAA0EAgqU7\n" +
        "lOMtR8l9ZyXYIro+c6PNdTF1m6xWHt4Eofyi70PFX4oGNT5dXc2sqO4tXnDFi804\n" +
        "w3l4T5it0fa5qw50gQ==\n" +
        "-----END CERTIFICATE-----";

    private final static String debugMsg1 = "x509: Debug info only. Error parsing extension: ObjectId: 2.5.29.17 Criticality=false";
    private final static String debugMsg2 = "java.io.IOException: Incorrect DNSName";

    public static void main(String[] args) throws Exception {

        if (args.length > 0 && args[0].equals("debug.x509")) {
               testNonCriticalNameWithDBG();
        }
        else {
            testNonCriticalName();
            testCriticalName();
        }
    }

    private static void testNonCriticalName() throws Exception {
        // getSubjectAlternativeNames() doesn't throw an Exception and retrun null
        // when critical is not specified, even if it has incorrect strings.
        X509Certificate certificate = certificate(invalidSANCertStr);
        Collection<?> subjectAltNames = certificate.getSubjectAlternativeNames();
        if (subjectAltNames != null) {
            throw new RuntimeException("Read invalid subjectAlternativeNames.");
        }
        System.out.println("Passed.");
    }

    private static void testNonCriticalNameWithDBG() throws Exception {
        // getSubjectAlternativeNames() doesn't throw an Exception and retrun null
        // when critical is not specified, even if it has incorrect strings.
        // IOExceition is printed in x509 debug info.
        PrintStream err = System.err;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setErr(new PrintStream(baos));
        X509Certificate certificate = certificate(invalidSANCertStr);
        Collection<?> subjectAltNames = certificate.getSubjectAlternativeNames();
        if (subjectAltNames != null) {
            throw new RuntimeException("Read invalid subjectAlternativeNames.");
        }
        baos.close();
        System.setErr(err);
        String debugMsg = baos.toString();
        if (debugMsg.contains(debugMsg1) && debugMsg.contains(debugMsg2)) {
            System.out.println("Passed.");
            return;
        }
        throw new RuntimeException("Invalid java.security.debug=x509 messaege.");
    }

    private static void testCriticalName() throws Exception {
        // getSubjectAlternativeNames() should throw an Exception
        // when critical is specified and it has incorrect strings.
        try {
            X509Certificate certificate = certificate(invalidCriticalSANCertStr);
            Collection<?> subjectAltNames = certificate.getSubjectAlternativeNames();
        }
        catch (CertificateParsingException e) {
            if (e.getMessage().equals("java.io.IOException: Incorrect DNSName")) {
                System.out.println("Passed.");
                return;
            }
        }
        throw new RuntimeException("Read invalid subjectAlternativeNames.");
    }

    private static X509Certificate certificate(String certificate) throws CertificateException {
        return (X509Certificate) CertificateFactory.getInstance("X.509")
            .generateCertificate(new ByteArrayInputStream(certificate.getBytes()));
    }
}
