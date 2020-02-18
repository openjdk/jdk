/*
 * Copyright (c) 1997, 2020, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.x509;


import sun.security.util.*;

/**
 * Lists all the object identifiers of the X509 extensions of the PKIX profile.
 *
 * <p>Extensions are addiitonal attributes which can be inserted in a X509
 * v3 certificate. For example a "Driving License Certificate" could have
 * the driving license number as a extension.
 *
 * <p>Extensions are represented as a sequence of the extension identifier
 * (Object Identifier), a boolean flag stating whether the extension is to
 * be treated as being critical and the extension value itself (this is again
 * a DER encoding of the extension value).
 *
 * @see Extension
 *
 *
 * @author Amit Kapoor
 * @author Hemma Prafullchandra
 */
public class PKIXExtensions {
    /**
     * Identifies the particular public key used to sign the certificate.
     */
    public static final ObjectIdentifier AuthorityKey_Id =
            ObjectIdentifier.of("2.5.29.35");

    /**
     * Identifies the particular public key used in an application.
     */
    public static final ObjectIdentifier SubjectKey_Id =
            ObjectIdentifier.of("2.5.29.14");

    /**
     * Defines the purpose of the key contained in the certificate.
     */
    public static final ObjectIdentifier KeyUsage_Id =
            ObjectIdentifier.of("2.5.29.15");

    /**
     * Allows the certificate issuer to specify a different validity period
     * for the private key than the certificate.
     */
    public static final ObjectIdentifier PrivateKeyUsage_Id =
            ObjectIdentifier.of("2.5.29.16");

    /**
     * Contains the sequence of policy information terms.
     */
    public static final ObjectIdentifier CertificatePolicies_Id =
            ObjectIdentifier.of("2.5.29.32");

    /**
     * Lists pairs of object identifiers of policies considered equivalent by
     * the issuing CA to the subject CA.
     */
    public static final ObjectIdentifier PolicyMappings_Id =
            ObjectIdentifier.of("2.5.29.33");

    /**
     * Allows additional identities to be bound to the subject of the
     * certificate.
     */
    public static final ObjectIdentifier SubjectAlternativeName_Id =
            ObjectIdentifier.of("2.5.29.17");

    /**
     * Allows additional identities to be associated with the certificate
     * issuer.
     */
    public static final ObjectIdentifier IssuerAlternativeName_Id =
            ObjectIdentifier.of("2.5.29.18");

    /**
     * Identifies additional directory attributes.
     * This extension is always non-critical.
     */
    public static final ObjectIdentifier SubjectDirectoryAttributes_Id =
            ObjectIdentifier.of("2.5.29.9");

    /**
     * Identifies whether the subject of the certificate is a CA and how deep
     * a certification path may exist through that CA.
     */
    public static final ObjectIdentifier BasicConstraints_Id =
            ObjectIdentifier.of("2.5.29.19");

    /**
     * Provides for permitted and excluded subtrees that place restrictions
     * on names that may be included within a certificate issued by a given CA.
     */
    public static final ObjectIdentifier NameConstraints_Id =
            ObjectIdentifier.of("2.5.29.30");

    /**
     * Used to either prohibit policy mapping or limit the set of policies
     * that can be in subsequent certificates.
     */
    public static final ObjectIdentifier PolicyConstraints_Id =
            ObjectIdentifier.of("2.5.29.36");

    /**
     * Identifies how CRL information is obtained.
     */
    public static final ObjectIdentifier CRLDistributionPoints_Id =
            ObjectIdentifier.of("2.5.29.31");

    /**
     * Conveys a monotonically increasing sequence number for each CRL
     * issued by a given CA.
     */
    public static final ObjectIdentifier CRLNumber_Id =
            ObjectIdentifier.of("2.5.29.20");

    /**
     * Identifies the CRL distribution point for a particular CRL.
     */
    public static final ObjectIdentifier IssuingDistributionPoint_Id =
            ObjectIdentifier.of("2.5.29.28");

    /**
     * Identifies the delta CRL.
     */
    public static final ObjectIdentifier DeltaCRLIndicator_Id =
            ObjectIdentifier.of("2.5.29.27");

    /**
     * Identifies the reason for the certificate revocation.
     */
    public static final ObjectIdentifier ReasonCode_Id =
            ObjectIdentifier.of("2.5.29.21");

    /**
     * This extension provides a registered instruction identifier indicating
     * the action to be taken, after encountering a certificate that has been
     * placed on hold.
     */
    public static final ObjectIdentifier HoldInstructionCode_Id =
            ObjectIdentifier.of("2.5.29.23");

    /**
     * Identifies the date on which it is known or suspected that the private
     * key was compromised or that the certificate otherwise became invalid.
     */
    public static final ObjectIdentifier InvalidityDate_Id =
            ObjectIdentifier.of("2.5.29.24");
    /**
     * Identifies one or more purposes for which the certified public key
     * may be used, in addition to or in place of the basic purposes
     * indicated in the key usage extension field.
     */
    public static final ObjectIdentifier ExtendedKeyUsage_Id =
            ObjectIdentifier.of("2.5.29.37");

    /**
     * Specifies whether any-policy policy OID is permitted
     */
    public static final ObjectIdentifier InhibitAnyPolicy_Id =
            ObjectIdentifier.of("2.5.29.54");

    /**
     * Identifies the certificate issuer associated with an entry in an
     * indirect CRL.
     */
    public static final ObjectIdentifier CertificateIssuer_Id =
            ObjectIdentifier.of("2.5.29.29");

    /**
     * This extension indicates how to access CA information and services for
     * the issuer of the certificate in which the extension appears.
     * This information may be used for on-line certification validation
     * services.
     */
    public static final ObjectIdentifier AuthInfoAccess_Id =
            ObjectIdentifier.of("1.3.6.1.5.5.7.1.1");

    /**
     * This extension indicates how to access CA information and services for
     * the subject of the certificate in which the extension appears.
     */
    public static final ObjectIdentifier SubjectInfoAccess_Id =
            ObjectIdentifier.of("1.3.6.1.5.5.7.1.11");

    /**
     * Identifies how delta CRL information is obtained.
     */
    public static final ObjectIdentifier FreshestCRL_Id =
            ObjectIdentifier.of("2.5.29.46");

    /**
     * Identifies the OCSP client can trust the responder for the
     * lifetime of the responder's certificate.
     */
    public static final ObjectIdentifier OCSPNoCheck_Id =
            ObjectIdentifier.of("1.3.6.1.5.5.7.48.1.5");

    /**
     * This extension is used to provide nonce data for OCSP requests
     * or responses.
     */
    public static final ObjectIdentifier OCSPNonce_Id =
            ObjectIdentifier.of("1.3.6.1.5.5.7.48.1.2");
}
