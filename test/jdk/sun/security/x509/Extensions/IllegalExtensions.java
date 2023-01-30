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
 * @bug 8296742
 * @summary Illegal X509 Extension should not be created
 * @modules java.base/sun.security.util
 *          java.base/sun.security.x509
 * @library /test/lib
 */

import jdk.test.lib.Utils;
import sun.security.util.ObjectIdentifier;
import sun.security.x509.*;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Vector;

public class IllegalExtensions {

    public static void main(String [] args) throws Exception {

        var oid = ObjectIdentifier.of("1.2.3.4");
        var emptyNames = new GeneralNames();
        var name = new GeneralName(new X500Name("CN=one"));
        var names = new GeneralNames();
        names.add(name);

        var ad = new AccessDescription(AccessDescription.Ad_CAISSUERS_Id, name);
        new AuthorityInfoAccessExtension(List.of(ad));
        Utils.runAndCheckException(() -> new AuthorityInfoAccessExtension(List.of()), IllegalArgumentException.class);
        Utils.runAndCheckException(() -> new AuthorityInfoAccessExtension(null), IllegalArgumentException.class);

        var kid = new KeyIdentifier(new byte[32]);
        var sn = new SerialNumber(0);
        new AuthorityKeyIdentifierExtension(kid, null, null);
        new AuthorityKeyIdentifierExtension(null, names, null);
        new AuthorityKeyIdentifierExtension(null, null, sn);
        Utils.runAndCheckException(() -> new AuthorityKeyIdentifierExtension(null, null, null), IllegalArgumentException.class);

        new CertificateIssuerExtension(names);
        Utils.runAndCheckException(() -> new CertificateIssuerExtension(null), IllegalArgumentException.class);
        Utils.runAndCheckException(() -> new CertificateIssuerExtension(emptyNames), IllegalArgumentException.class);

        var pi = new PolicyInformation(new CertificatePolicyId(oid), Collections.emptySet());
        new CertificatePoliciesExtension(List.of(pi));
        Utils.runAndCheckException(() -> new CertificatePoliciesExtension(null), IllegalArgumentException.class);
        Utils.runAndCheckException(() -> new CertificatePoliciesExtension(List.of()), IllegalArgumentException.class);

        var dp = new DistributionPoint(names, null, null);
        new CRLDistributionPointsExtension(List.of(dp));
        Utils.runAndCheckException(() -> new CRLDistributionPointsExtension(List.of()), IllegalArgumentException.class);
        Utils.runAndCheckException(() -> new CRLDistributionPointsExtension(null), IllegalArgumentException.class);

        new CRLNumberExtension(0);
        new CRLNumberExtension(BigInteger.ONE);
        Utils.runAndCheckException(() -> new CRLNumberExtension(null), IllegalArgumentException.class);

        new CRLReasonCodeExtension(1);
        Utils.runAndCheckException(() -> new CRLReasonCodeExtension(0), IllegalArgumentException.class);
        Utils.runAndCheckException(() -> new CRLReasonCodeExtension(-1), IllegalArgumentException.class);

        new ExtendedKeyUsageExtension(new Vector<>(List.of(oid)));
        Utils.runAndCheckException(() -> new ExtendedKeyUsageExtension(null), IllegalArgumentException.class);
        Utils.runAndCheckException(() -> new ExtendedKeyUsageExtension(new Vector<>()), IllegalArgumentException.class);

        new InhibitAnyPolicyExtension(0);
        new InhibitAnyPolicyExtension(-1);
        Utils.runAndCheckException(() -> new InhibitAnyPolicyExtension(-2), IllegalArgumentException.class);

        new InvalidityDateExtension(new Date());
        Utils.runAndCheckException(() -> new InvalidityDateExtension(null), IllegalArgumentException.class);

        new IssuerAlternativeNameExtension(names);
        Utils.runAndCheckException(() -> new IssuerAlternativeNameExtension(null), IllegalArgumentException.class);
        Utils.runAndCheckException(() -> new IssuerAlternativeNameExtension(emptyNames), IllegalArgumentException.class);

        var dpn = new DistributionPointName(names);
        var rf = new ReasonFlags(new boolean[1]);
        new IssuingDistributionPointExtension(dpn, null, false, false, false, false);
        new IssuingDistributionPointExtension(null, rf, false, false, false, false);
        new IssuingDistributionPointExtension(null, null, true, false, false, false);
        new IssuingDistributionPointExtension(null, null, false, true, false, false);
        new IssuingDistributionPointExtension(null, null, false, false, true, false);
        new IssuingDistributionPointExtension(null, null, false, false, false, true);
        Utils.runAndCheckException(() -> new IssuingDistributionPointExtension(null, null, false, false, false, false), IllegalArgumentException.class);

        var gss = new GeneralSubtrees();
        new NameConstraintsExtension(gss, null);
        new NameConstraintsExtension((GeneralSubtrees) null, gss);
        Utils.runAndCheckException(() -> new NameConstraintsExtension((GeneralSubtrees) null, null), IllegalArgumentException.class);

        new PolicyConstraintsExtension(0, 0);
        new PolicyConstraintsExtension(-1, 0);
        new PolicyConstraintsExtension(0, -1);
        Utils.runAndCheckException(() -> new PolicyConstraintsExtension(-1, -1), IllegalArgumentException.class);

        var cpi = new CertificatePolicyId(oid);
        var cpm = new CertificatePolicyMap(cpi, cpi);
        new PolicyMappingsExtension(List.of(cpm));
        Utils.runAndCheckException(() -> new PolicyMappingsExtension(List.of()), IllegalArgumentException.class);
        Utils.runAndCheckException(() -> new PolicyMappingsExtension(null), IllegalArgumentException.class);

        new PrivateKeyUsageExtension(new Date(), new Date());
        new PrivateKeyUsageExtension(new Date(), null);
        new PrivateKeyUsageExtension((Date) null, new Date());
        Utils.runAndCheckException(() -> new PrivateKeyUsageExtension((Date) null, null), IllegalArgumentException.class);

        new SubjectAlternativeNameExtension(names);
        Utils.runAndCheckException(() -> new SubjectAlternativeNameExtension(null), IllegalArgumentException.class);
        Utils.runAndCheckException(() -> new SubjectAlternativeNameExtension(emptyNames), IllegalArgumentException.class);

        new SubjectInfoAccessExtension(List.of(ad));
        Utils.runAndCheckException(() -> new SubjectInfoAccessExtension(List.of()), IllegalArgumentException.class);
        Utils.runAndCheckException(() -> new SubjectInfoAccessExtension(null), IllegalArgumentException.class);
    }
}
