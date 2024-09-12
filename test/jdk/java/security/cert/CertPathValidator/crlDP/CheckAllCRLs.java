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

import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertPathValidatorException.BasicReason;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.cert.X509CRL;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import sun.security.x509.AuthorityKeyIdentifierExtension;
import sun.security.x509.CRLDistributionPointsExtension;
import sun.security.x509.CRLExtensions;
import sun.security.x509.CRLNumberExtension;
import sun.security.x509.DistributionPoint;
import sun.security.x509.Extension;
import sun.security.x509.GeneralName;
import sun.security.x509.GeneralNames;
import sun.security.x509.KeyIdentifier;
import sun.security.x509.URIName;
import sun.security.x509.X500Name;
import sun.security.x509.X509CRLEntryImpl;
import sun.security.x509.X509CRLImpl;
import static sun.security.x509.X509CRLImpl.TBSCertList;
import sun.security.testlibrary.CertificateBuilder;

/*
 * @test
 * @bug 8200566
 * @summary Check that CRL validation continues to check other CRLs in
 *          CRLDP extension after CRL fetching errors and exhibits same
 *          behavior (fails because cert is revoked) whether CRL cache is
 *          fresh or stale.
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.util
 * @library ../../../../../java/security/testlibrary
 * @build CertificateBuilder CheckAllCRLs
 * @run main/othervm -Dcom.sun.security.enableCRLDP=true CheckAllCRLs
 */
public class CheckAllCRLs {

    public static void main(String[] args) throws Exception {

        CertificateBuilder cb = new CertificateBuilder();

        // Create CA cert
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        KeyPair rootKeyPair = kpg.genKeyPair();
        X509Certificate rootCert = createCert(cb, "CN=Root CA",
            rootKeyPair, rootKeyPair, null, "SHA384withRSA", true, false);

        // Create EE cert. This EE cert will contain a CRL Distribution
        // Points extension with two DistributionPoints - one will be a HTTP
        // URL to a non-existant HTTP server, and the other will be a File
        // URL to a file containing the CRL.
        KeyPair eeKeyPair = kpg.genKeyPair();
        X509Certificate eeCert1 = createCert(cb, "CN=End Entity",
            rootKeyPair, eeKeyPair, rootCert, "SHA384withRSA", false, true);

        // Create another EE cert. This EE cert is similar in that it contains
        // a CRL Distribution Points extension but with one DistributionPoint
        // containing 2 GeneralName URLs as above.
        X509Certificate eeCert2 = createCert(cb, "CN=End Entity",
            rootKeyPair, eeKeyPair, rootCert, "SHA384withRSA", false, false);

        // Create a CRL with no revoked certificates and store it in a file
        X509CRL crl = createCRL(new X500Name("CN=Root CA"), rootKeyPair,
            "SHA384withRSA");
        Files.write(Path.of("root.crl"), crl.getEncoded());

        // Validate path containing eeCert1
        System.out.println("Validating cert with CRLDP containing one "
            + "DistributionPoint with 2 entries, the first non-existent");
        validatePath(eeCert1, rootCert);

        // Validate path containing eeCert2
        System.out.println("Validating cert with CRLDP containing two "
            + "DistributionPoints with 1 entry each, the first non-existent");
        validatePath(eeCert2, rootCert);
    }

    private static X509Certificate createCert(CertificateBuilder cb,
            String subjectDN, KeyPair issuerKeyPair, KeyPair subjectKeyPair,
            X509Certificate issuerCert, String sigAlg, boolean isCA,
            boolean twoDPs) throws Exception {
        cb.setSubjectName(subjectDN);
        cb.setPublicKey(subjectKeyPair.getPublic());
        cb.setSerialNumber(new BigInteger("1"));

        if (isCA) {
            // Make a 3 year validity starting from 60 days ago
            long start = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(60);
            long end = start + TimeUnit.DAYS.toMillis(1085);
            cb.setValidity(new Date(start), new Date(end));
            cb.addBasicConstraintsExt(true, true, -1);
            cb.addKeyUsageExt(new boolean[]
                {false, false, false, false, false, true, true, false, false});
        } else {
            // Make a 1 year validity starting from 7 days ago
            long start = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7);
            long end = start + TimeUnit.DAYS.toMillis(365);
            cb.setValidity(new Date(start), new Date(end));
            cb.addAuthorityKeyIdExt(issuerKeyPair.getPublic());
            cb.addKeyUsageExt(new boolean[]
                {true, false, false, false, false, false, false, false, false});
            cb.addExtendedKeyUsageExt(List.of("1.3.6.1.5.5.7.3.1"));
            GeneralName first = new GeneralName(new URIName(
                    "http://127.0.0.1:48180/crl/will/always/fail/root.crl"));
            GeneralName second = new GeneralName(new URIName("file:./root.crl"));
            if (twoDPs) {
                GeneralNames gn1 = new GeneralNames().add(first);
                DistributionPoint dp1 = new DistributionPoint(gn1, null, null);
                GeneralNames gn2 = new GeneralNames().add(second);
                DistributionPoint dp2 = new DistributionPoint(gn2, null, null);
                cb.addExtension(new CRLDistributionPointsExtension(List.of(dp1, dp2)));
            } else {
                GeneralNames gn = new GeneralNames().add(first).add(second);
                DistributionPoint dp = new DistributionPoint(gn, null, null);
                cb.addExtension(new CRLDistributionPointsExtension(List.of(dp)));
            }
        }
        cb.addSubjectKeyIdExt(subjectKeyPair.getPublic());

        // return signed cert
        return cb.build(issuerCert, issuerKeyPair.getPrivate(), sigAlg);
    }

    private static X509CRL createCRL(X500Name caIssuer, KeyPair caKeyPair,
            String sigAlg) throws Exception {

        CRLExtensions crlExts = new CRLExtensions();

        // add AuthorityKeyIdentifier extension
        KeyIdentifier kid = new KeyIdentifier(caKeyPair.getPublic());
        Extension ext = new AuthorityKeyIdentifierExtension(kid, null, null);
        crlExts.setExtension(ext.getId(),
            new AuthorityKeyIdentifierExtension(kid, null, null));

        // add CRLNumber extension
        ext = new CRLNumberExtension(1);
        crlExts.setExtension(ext.getId(), ext);

        // revoke cert
        X509CRLEntryImpl crlEntry =
            new X509CRLEntryImpl(new BigInteger("1"), new Date());

        // Create a 1 year validity CRL starting from 7 days ago
        long start = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7);
        long end = start + TimeUnit.DAYS.toMillis(365);
        TBSCertList tcl = new TBSCertList(caIssuer, new Date(start),
            new Date(end), new X509CRLEntryImpl[]{ crlEntry }, crlExts);

        // return signed CRL
        return X509CRLImpl.newSigned(tcl, caKeyPair.getPrivate(), sigAlg);
    }

    private static void validatePath(X509Certificate eeCert,
            X509Certificate rootCert) throws Exception {

        // Create certification path and set up PKIXParameters.
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        CertPath cp = cf.generateCertPath(List.of(eeCert));
        PKIXParameters pp =
            new PKIXParameters(Set.of(new TrustAnchor(rootCert, null)));
        pp.setRevocationEnabled(true);

        CertPathValidator cpv = CertPathValidator.getInstance("PKIX");

        // Validate path twice in succession, making sure we get consistent
        // results the second time when the CRL cache is fresh.
        System.out.println("First time validating path");
        validate(cpv, cp, pp);
        System.out.println("Second time validating path");
        validate(cpv, cp, pp);

        // CRL lookup cache time is 30s. Sleep for 35 seconds to ensure
        // cache is stale, and validate one more time to ensure we get
        // consistent results.
        System.out.println("Waiting for CRL cache to be cleared");
        Thread.sleep(30500);

        System.out.println("Third time validating path");
        validate(cpv, cp, pp);
    }

    private static void validate(CertPathValidator cpv, CertPath cp,
            PKIXParameters pp) throws Exception {

        try {
            cpv.validate(cp, pp);
            throw new Exception("Validation passed unexpectedly");
        } catch (CertPathValidatorException cpve) {
            if (cpve.getReason() != BasicReason.REVOKED) {
                throw new Exception("Validation failed with unexpected reason", cpve);
            }
            System.out.println("Validation failed as expected: " + cpve);
        }
    }
}
