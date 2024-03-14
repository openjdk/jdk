/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8317431
 * @summary Verify order of PKIXCertComparator sorting algorithm
 * @modules java.base/sun.security.provider.certpath:+open
 *          java.base/sun.security.x509
 *          java.base/sun.security.util
 * @library /test/lib ../../../../../java/security/testlibrary
 * @build CertificateBuilder
 * @run main Order
 */

import java.lang.reflect.Constructor;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Comparator;
import java.util.Set;
import javax.security.auth.x500.X500Principal;
import sun.security.x509.X509CertImpl;

import jdk.test.lib.Asserts;
import sun.security.testlibrary.CertificateBuilder;

public class Order {

    private record CertAndKeyPair(X509Certificate cert, KeyPair keyPair) {}

    private static KeyPairGenerator kpg;

    public static void main(String[] args) throws Exception {
        kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);

        // Create top-level root CA cert with KIDs (Subject and Auth KeyIds)
        // A root CA doesn't usually have an Auth KeyId but for this test,
        // it doesn't matter.
        CertAndKeyPair rootCA =
            createCert(null, "CN=Root CA, O=Java, C=US", true, true);
        System.out.println(rootCA.cert);

        // Create intermediate CA cert with KIDs, issued by root CA
        CertAndKeyPair javaCA =
            createCert(rootCA, "CN=Java CA, O=Java, C=US", true, true);
        System.out.println(javaCA.cert);

        // Create intermediate CA cert without KIDs, issued by root CA.
        // This CA has the same DN/public key as the CA with KIDs.
        CertAndKeyPair javaCAWoKids = createCert(rootCA,
            "CN=Java CA, O=Java, C=US", true, false, javaCA.keyPair);
        System.out.println(javaCAWoKids.cert);

        // Create another intermediate CA cert without KIDs, issued by root CA.
        CertAndKeyPair openJDKCAWoKids = createCert(rootCA,
                "CN=OpenJDK CA, O=OpenJDK, C=US", true, false);
        System.out.println(openJDKCAWoKids.cert);

        // Create another intermediate CA with KIDs, issued by Java CA
        CertAndKeyPair secCA = createCert(javaCAWoKids,
            "CN=Security CA, OU=Security, O=Java, C=US", true, true);
        System.out.println(secCA.cert);

        // Cross certify Java CA with OpenJDK CA
        CertAndKeyPair javaCAIssuedByOpenJDKCA = createCert(openJDKCAWoKids,
            "CN=Java CA, O=Java, C=US", true, false, javaCA.keyPair);
        System.out.println(javaCAIssuedByOpenJDKCA.cert);

        // Cross certify Security CA with OpenJDK CA
        CertAndKeyPair secCAIssuedByOpenJDKCA = createCert(openJDKCAWoKids,
            "CN=Security CA, OU=Security, O=Java, C=US", true, false, secCA.keyPair);
        System.out.println(secCAIssuedByOpenJDKCA.cert);

        // Create end entity cert without KIDs issued by Security CA.
        CertAndKeyPair ee = createCert(secCA,
            "CN=EE, OU=Security, O=Java, C=US", false, false);
        System.out.println(ee.cert);

        // Create another end entity cert without KIDs issued by Java CA.
        // This EE has the same DN/public key as the one above.
        CertAndKeyPair eeIssuedByJavaCA = createCert(javaCA,
            "CN=EE, OU=Security, O=Java, C=US", false, false);
        System.out.println(eeIssuedByJavaCA.cert);

        Constructor ctor = getPKIXCertComparatorCtor();
        Set<X500Principal> trustedSubjects =
            Set.of(new X500Principal("CN=Root CA, O=Java, C=US"));

        System.out.println("Test that equal certs are treated the same");
        Comparator c = (Comparator) ctor.newInstance(trustedSubjects,
            secCA.cert);
        Asserts.assertTrue(c.compare(javaCA.cert, javaCA.cert) == 0);

        System.out.println("Test that cert with matching kids is preferred");
        Asserts.assertTrue(c.compare(javaCA.cert, javaCAWoKids.cert) == -1);
        Asserts.assertTrue(c.compare(javaCAWoKids.cert, javaCA.cert) == 1);

        System.out.println("Test that cert issued by anchor is preferred");
        Asserts.assertTrue(
            c.compare(javaCAWoKids.cert, javaCAIssuedByOpenJDKCA.cert) == -1);
        Asserts.assertTrue(
            c.compare(javaCAIssuedByOpenJDKCA.cert, javaCAWoKids.cert) == 1);

        System.out.println(
            "Test that cert issuer in same namespace as anchor is preferred");
        c = (Comparator) ctor.newInstance(trustedSubjects, ee.cert);
        Asserts.assertTrue(
            c.compare(secCA.cert, secCAIssuedByOpenJDKCA.cert) == -1);
        Asserts.assertTrue(
            c.compare(secCAIssuedByOpenJDKCA.cert, secCA.cert) == 1);

        System.out.println(
            "Test cert issuer in same namespace closest to root is preferred");
        Asserts.assertTrue(c.compare(eeIssuedByJavaCA.cert, ee.cert) == -1);
        Asserts.assertTrue(c.compare(ee.cert, eeIssuedByJavaCA.cert) == 1);
    }

    private static boolean[] CA_KEY_USAGE =
        new boolean[] {true,false,false,false,false,true,true,false,false};
    private static boolean[] EE_KEY_USAGE =
        new boolean[] {true,false,false,false,false,false,false,false,false};

    private static CertAndKeyPair createCert(CertAndKeyPair issuer,
        String subjectDn, boolean ca, boolean kids) throws Exception {

        KeyPair kp = kpg.generateKeyPair();
        return createCert(issuer, subjectDn, ca, kids, kp);
    }

    private static CertAndKeyPair createCert(CertAndKeyPair issuer,
        String subjectDn, boolean ca, boolean kids, KeyPair kp)
        throws Exception {

        if (issuer == null) {
            issuer = new CertAndKeyPair(null, kp);
        }
        CertificateBuilder cb = new CertificateBuilder()
            .setSubjectName(subjectDn)
            .setPublicKey(kp.getPublic());

        if (ca) {
            cb = cb.addBasicConstraintsExt(true, true, -1)
                   .addKeyUsageExt(CA_KEY_USAGE);
        } else {
            cb = cb.addBasicConstraintsExt(true, false, -1)
                   .addKeyUsageExt(EE_KEY_USAGE);
        }
        if (kids) {
            cb = cb.addAuthorityKeyIdExt(issuer.keyPair.getPublic())
                   .addSubjectKeyIdExt(kp.getPublic());
        }
        X509Certificate cert =
            cb.build(issuer.cert, issuer.keyPair.getPrivate(), "SHA256withRSA");
        return new CertAndKeyPair(cert, kp);
    }

    private static Constructor getPKIXCertComparatorCtor() throws Exception {
        var cl = Class.forName(
            "sun.security.provider.certpath.ForwardBuilder$PKIXCertComparator");
        var c = cl.getDeclaredConstructor(Set.class, X509CertImpl.class);
        c.setAccessible(true);
        return c;
    }
}
