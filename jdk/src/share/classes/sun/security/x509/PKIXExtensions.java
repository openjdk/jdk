/*
 * Copyright 1997-2005 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.security.x509;

import java.io.*;

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
    // The object identifiers
    private static final int AuthorityKey_data [] = { 2, 5, 29, 35 };
    private static final int SubjectKey_data [] = { 2, 5, 29, 14 };
    private static final int KeyUsage_data [] = { 2, 5, 29, 15 };
    private static final int PrivateKeyUsage_data [] = { 2, 5, 29, 16 };
    private static final int CertificatePolicies_data [] = { 2, 5, 29, 32 };
    private static final int PolicyMappings_data [] = { 2, 5, 29, 33 };
    private static final int SubjectAlternativeName_data [] = { 2, 5, 29, 17 };
    private static final int IssuerAlternativeName_data [] = { 2, 5, 29, 18 };
    private static final int SubjectDirectoryAttributes_data [] = { 2, 5, 29, 9 };
    private static final int BasicConstraints_data [] = { 2, 5, 29, 19 };
    private static final int NameConstraints_data [] = { 2, 5, 29, 30 };
    private static final int PolicyConstraints_data [] = { 2, 5, 29, 36 };
    private static final int CRLDistributionPoints_data [] = { 2, 5, 29, 31 };
    private static final int CRLNumber_data [] = { 2, 5, 29, 20 };
    private static final int IssuingDistributionPoint_data [] = { 2, 5, 29, 28 };
    private static final int DeltaCRLIndicator_data [] = { 2, 5, 29, 27 };
    private static final int ReasonCode_data [] = { 2, 5, 29, 21 };
    private static final int HoldInstructionCode_data [] = { 2, 5, 29, 23 };
    private static final int InvalidityDate_data [] = { 2, 5, 29, 24 };
    private static final int ExtendedKeyUsage_data [] = { 2, 5, 29, 37 };
    private static final int InhibitAnyPolicy_data [] = { 2, 5, 29, 54 };
    private static final int CertificateIssuer_data [] = { 2, 5, 29, 29 };
    private static final int AuthInfoAccess_data [] = { 1, 3, 6, 1, 5, 5, 7, 1, 1};
    private static final int SubjectInfoAccess_data [] = { 1, 3, 6, 1, 5, 5, 7, 1, 11};
    private static final int FreshestCRL_data [] = { 2, 5, 29, 46 };

    /**
     * Identifies the particular public key used to sign the certificate.
     */
    public static final ObjectIdentifier AuthorityKey_Id;

    /**
     * Identifies the particular public key used in an application.
     */
    public static final ObjectIdentifier SubjectKey_Id;

    /**
     * Defines the purpose of the key contained in the certificate.
     */
    public static final ObjectIdentifier KeyUsage_Id;

    /**
     * Allows the certificate issuer to specify a different validity period
     * for the private key than the certificate.
     */
    public static final ObjectIdentifier PrivateKeyUsage_Id;

    /**
     * Contains the sequence of policy information terms.
     */
    public static final ObjectIdentifier CertificatePolicies_Id;

    /**
     * Lists pairs of objectidentifiers of policies considered equivalent by the
     * issuing CA to the subject CA.
     */
    public static final ObjectIdentifier PolicyMappings_Id;

    /**
     * Allows additional identities to be bound to the subject of the certificate.
     */
    public static final ObjectIdentifier SubjectAlternativeName_Id;

    /**
     * Allows additional identities to be associated with the certificate issuer.
     */
    public static final ObjectIdentifier IssuerAlternativeName_Id;

    /**
     * Identifies additional directory attributes.
     * This extension is always non-critical.
     */
    public static final ObjectIdentifier SubjectDirectoryAttributes_Id;

    /**
     * Identifies whether the subject of the certificate is a CA and how deep
     * a certification path may exist through that CA.
     */
    public static final ObjectIdentifier BasicConstraints_Id;

    /**
     * Provides for permitted and excluded subtrees that place restrictions
     * on names that may be included within a certificate issued by a given CA.
     */
    public static final ObjectIdentifier NameConstraints_Id;

    /**
     * Used to either prohibit policy mapping or limit the set of policies
     * that can be in subsequent certificates.
     */
    public static final ObjectIdentifier PolicyConstraints_Id;

    /**
     * Identifies how CRL information is obtained.
     */
    public static final ObjectIdentifier CRLDistributionPoints_Id;

    /**
     * Conveys a monotonically increasing sequence number for each CRL
     * issued by a given CA.
     */
    public static final ObjectIdentifier CRLNumber_Id;

    /**
     * Identifies the CRL distribution point for a particular CRL.
     */
    public static final ObjectIdentifier IssuingDistributionPoint_Id;

    /**
     * Identifies the delta CRL.
     */
    public static final ObjectIdentifier DeltaCRLIndicator_Id;

    /**
     * Identifies the reason for the certificate revocation.
     */
    public static final ObjectIdentifier ReasonCode_Id;

    /**
     * This extension provides a registered instruction identifier indicating
     * the action to be taken, after encountering a certificate that has been
     * placed on hold.
     */
    public static final ObjectIdentifier HoldInstructionCode_Id;

    /**
     * Identifies the date on which it is known or suspected that the private
     * key was compromised or that the certificate otherwise became invalid.
     */
    public static final ObjectIdentifier InvalidityDate_Id;
    /**
     * Identifies one or more purposes for which the certified public key
     * may be used, in addition to or in place of the basic purposes
     * indicated in the key usage extension field.
     */
    public static final ObjectIdentifier ExtendedKeyUsage_Id;

    /**
     * Specifies whether any-policy policy OID is permitted
     */
    public static final ObjectIdentifier InhibitAnyPolicy_Id;

    /**
     * Identifies the certificate issuer associated with an entry in an
     * indirect CRL.
     */
    public static final ObjectIdentifier CertificateIssuer_Id;

    /**
     * This extension indicates how to access CA information and services for
     * the issuer of the certificate in which the extension appears.
     * This information may be used for on-line certification validation
     * services.
     */
    public static final ObjectIdentifier AuthInfoAccess_Id;

    /**
     * This extension indicates how to access CA information and services for
     * the subject of the certificate in which the extension appears.
     */
    public static final ObjectIdentifier SubjectInfoAccess_Id;

    /**
     * Identifies how delta CRL information is obtained.
     */
    public static final ObjectIdentifier FreshestCRL_Id;

    static {
        AuthorityKey_Id = ObjectIdentifier.newInternal(AuthorityKey_data);
        SubjectKey_Id   = ObjectIdentifier.newInternal(SubjectKey_data);
        KeyUsage_Id     = ObjectIdentifier.newInternal(KeyUsage_data);
        PrivateKeyUsage_Id = ObjectIdentifier.newInternal(PrivateKeyUsage_data);
        CertificatePolicies_Id =
            ObjectIdentifier.newInternal(CertificatePolicies_data);
        PolicyMappings_Id = ObjectIdentifier.newInternal(PolicyMappings_data);
        SubjectAlternativeName_Id =
            ObjectIdentifier.newInternal(SubjectAlternativeName_data);
        IssuerAlternativeName_Id =
            ObjectIdentifier.newInternal(IssuerAlternativeName_data);
        ExtendedKeyUsage_Id = ObjectIdentifier.newInternal(ExtendedKeyUsage_data);
        InhibitAnyPolicy_Id = ObjectIdentifier.newInternal(InhibitAnyPolicy_data);
        SubjectDirectoryAttributes_Id =
            ObjectIdentifier.newInternal(SubjectDirectoryAttributes_data);
        BasicConstraints_Id =
            ObjectIdentifier.newInternal(BasicConstraints_data);
        ReasonCode_Id = ObjectIdentifier.newInternal(ReasonCode_data);
        HoldInstructionCode_Id  =
            ObjectIdentifier.newInternal(HoldInstructionCode_data);
        InvalidityDate_Id = ObjectIdentifier.newInternal(InvalidityDate_data);

        NameConstraints_Id = ObjectIdentifier.newInternal(NameConstraints_data);
        PolicyConstraints_Id =
            ObjectIdentifier.newInternal(PolicyConstraints_data);
        CRLDistributionPoints_Id =
            ObjectIdentifier.newInternal(CRLDistributionPoints_data);
        CRLNumber_Id =
            ObjectIdentifier.newInternal(CRLNumber_data);
        IssuingDistributionPoint_Id =
            ObjectIdentifier.newInternal(IssuingDistributionPoint_data);
        DeltaCRLIndicator_Id =
            ObjectIdentifier.newInternal(DeltaCRLIndicator_data);
        CertificateIssuer_Id =
            ObjectIdentifier.newInternal(CertificateIssuer_data);
        AuthInfoAccess_Id =
            ObjectIdentifier.newInternal(AuthInfoAccess_data);
        SubjectInfoAccess_Id =
            ObjectIdentifier.newInternal(SubjectInfoAccess_data);
        FreshestCRL_Id = ObjectIdentifier.newInternal(FreshestCRL_data);
    }
}
