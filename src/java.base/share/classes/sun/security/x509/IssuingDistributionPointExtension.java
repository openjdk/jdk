/*
 * Copyright (c) 2005, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;

import sun.security.util.DerInputStream;
import sun.security.util.DerOutputStream;
import sun.security.util.DerValue;

/**
 * Represents the CRL Issuing Distribution Point Extension (OID = 2.5.29.28).
 *
 * <p>
 * The issuing distribution point is a critical CRL extension that
 * identifies the CRL distribution point and scope for a particular CRL,
 * and it indicates whether the CRL covers revocation for end entity
 * certificates only, CA certificates only, attribute certificates only,
 * or a limited set of reason codes.
 *
 * <p>
 * The extension is defined in Section 5.2.5 of
 * <a href="https://tools.ietf.org/html/rfc5280">Internet X.509 PKI
 * Certificate and Certificate Revocation List (CRL) Profile</a>.
 *
 * <p>
 * Its ASN.1 definition is as follows:
 * <pre>
 *     id-ce-issuingDistributionPoint OBJECT IDENTIFIER ::= { id-ce 28 }
 *
 *     issuingDistributionPoint ::= SEQUENCE {
 *          distributionPoint          [0] DistributionPointName OPTIONAL,
 *          onlyContainsUserCerts      [1] BOOLEAN DEFAULT FALSE,
 *          onlyContainsCACerts        [2] BOOLEAN DEFAULT FALSE,
 *          onlySomeReasons            [3] ReasonFlags OPTIONAL,
 *          indirectCRL                [4] BOOLEAN DEFAULT FALSE,
 *          onlyContainsAttributeCerts [5] BOOLEAN DEFAULT FALSE }
 * </pre>
 *
 * @see DistributionPoint
 * @since 1.6
 */
public class IssuingDistributionPointExtension extends Extension {

    public static final String NAME = "IssuingDistributionPoint";

    /*
     * The distribution point name for the CRL.
     */
    private DistributionPointName distributionPoint = null;

    /*
     * The scope settings for the CRL.
     */
    private ReasonFlags revocationReasons = null;
    private boolean hasOnlyUserCerts = false;
    private boolean hasOnlyCACerts = false;
    private boolean hasOnlyAttributeCerts = false;
    private boolean isIndirectCRL = false;

    /*
     * ASN.1 context specific tag values
     */
    private static final byte TAG_DISTRIBUTION_POINT = 0;
    private static final byte TAG_ONLY_USER_CERTS = 1;
    private static final byte TAG_ONLY_CA_CERTS = 2;
    private static final byte TAG_ONLY_SOME_REASONS = 3;
    private static final byte TAG_INDIRECT_CRL = 4;
    private static final byte TAG_ONLY_ATTRIBUTE_CERTS = 5;

    /**
     * Creates a critical IssuingDistributionPointExtension.
     *
     * @param distributionPoint the name of the distribution point, or null for
     *        none.
     * @param revocationReasons the revocation reasons associated with the
     *        distribution point, or null for none.
     * @param hasOnlyUserCerts if <code>true</code> then scope of the CRL
     *        includes only user certificates.
     * @param hasOnlyCACerts if <code>true</code> then scope of the CRL
     *        includes only CA certificates.
     * @param hasOnlyAttributeCerts if <code>true</code> then scope of the CRL
     *        includes only attribute certificates.
     * @param isIndirectCRL if <code>true</code> then the scope of the CRL
     *        includes certificates issued by authorities other than the CRL
     *        issuer. The responsible authority is indicated by a certificate
     *        issuer CRL entry extension.
     * @throws IllegalArgumentException if more than one of
     *        <code>hasOnlyUserCerts</code>, <code>hasOnlyCACerts</code>,
     *        <code>hasOnlyAttributeCerts</code> is set to <code>true</code>,
     *        or all arguments are either <code>null</code> or <code>false</code>.
     */
    public IssuingDistributionPointExtension(
        DistributionPointName distributionPoint, ReasonFlags revocationReasons,
        boolean hasOnlyUserCerts, boolean hasOnlyCACerts,
        boolean hasOnlyAttributeCerts, boolean isIndirectCRL) {

        if (distributionPoint == null &&
                revocationReasons == null &&
                !hasOnlyUserCerts &&
                !hasOnlyCACerts &&
                !hasOnlyAttributeCerts &&
                !isIndirectCRL) {
            throw new IllegalArgumentException("elements cannot be empty");
        }
        if ((hasOnlyUserCerts && (hasOnlyCACerts || hasOnlyAttributeCerts)) ||
            (hasOnlyCACerts && (hasOnlyUserCerts || hasOnlyAttributeCerts)) ||
            (hasOnlyAttributeCerts && (hasOnlyUserCerts || hasOnlyCACerts))) {
            throw new IllegalArgumentException(
                "Only one of hasOnlyUserCerts, hasOnlyCACerts, " +
                "hasOnlyAttributeCerts may be set to true");
        }
        this.extensionId = PKIXExtensions.IssuingDistributionPoint_Id;
        this.critical = true;
        this.distributionPoint = distributionPoint;
        this.revocationReasons = revocationReasons;
        this.hasOnlyUserCerts = hasOnlyUserCerts;
        this.hasOnlyCACerts = hasOnlyCACerts;
        this.hasOnlyAttributeCerts = hasOnlyAttributeCerts;
        this.isIndirectCRL = isIndirectCRL;
        encodeThis();
    }

    /**
     * Creates a critical IssuingDistributionPointExtension from its
     * DER-encoding.
     *
     * @param critical true if the extension is to be treated as critical.
     * @param value the DER-encoded value. It must be a <code>byte[]</code>.
     * @exception IOException on decoding error.
     */
    public IssuingDistributionPointExtension(Boolean critical, Object value)
            throws IOException {
        this.extensionId = PKIXExtensions.IssuingDistributionPoint_Id;
        this.critical = critical.booleanValue();

        if (!(value instanceof byte[])) {
            throw new IOException("Illegal argument type");
        }

        extensionValue = (byte[])value;
        DerValue val = new DerValue(extensionValue);
        if (val.tag != DerValue.tag_Sequence) {
            throw new IOException("Invalid encoding for " +
                                  "IssuingDistributionPointExtension.");
        }

        // All the elements in issuingDistributionPoint are optional
        if ((val.data == null) || (val.data.available() == 0)) {
            return;
        }

        DerInputStream in = val.data;
        while (in.available() != 0) {
            DerValue opt = in.getDerValue();

            if (opt.isContextSpecific(TAG_DISTRIBUTION_POINT) &&
                opt.isConstructed()) {
                distributionPoint =
                    new DistributionPointName(opt.data.getDerValue());
            } else if (opt.isContextSpecific(TAG_ONLY_USER_CERTS) &&
                       !opt.isConstructed()) {
                opt.resetTag(DerValue.tag_Boolean);
                hasOnlyUserCerts = opt.getBoolean();
            } else if (opt.isContextSpecific(TAG_ONLY_CA_CERTS) &&
                  !opt.isConstructed()) {
                opt.resetTag(DerValue.tag_Boolean);
                hasOnlyCACerts = opt.getBoolean();
            } else if (opt.isContextSpecific(TAG_ONLY_SOME_REASONS) &&
                       !opt.isConstructed()) {
                revocationReasons = new ReasonFlags(opt); // expects tag implicit
            } else if (opt.isContextSpecific(TAG_INDIRECT_CRL) &&
                       !opt.isConstructed()) {
                opt.resetTag(DerValue.tag_Boolean);
                isIndirectCRL = opt.getBoolean();
            } else if (opt.isContextSpecific(TAG_ONLY_ATTRIBUTE_CERTS) &&
                       !opt.isConstructed()) {
                opt.resetTag(DerValue.tag_Boolean);
                hasOnlyAttributeCerts = opt.getBoolean();
            } else {
                throw new IOException
                    ("Invalid encoding of IssuingDistributionPoint");
            }
        }
    }

    /**
     * Returns the name of this extension.
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Encodes the issuing distribution point extension and writes it to the
     * DerOutputStream.
     *
     * @param out the output stream.
     */
    @Override
    public void encode(DerOutputStream out) {
        if (this.extensionValue == null) {
            this.extensionId = PKIXExtensions.IssuingDistributionPoint_Id;
            this.critical = false;
            encodeThis();
        }
        super.encode(out);
    }

    public void setRevocationReasons(ReasonFlags val) throws IOException {
        revocationReasons = val;
        encodeThis();
    }

    public DistributionPointName getDistributionPoint() {
        return distributionPoint;
    }

    public ReasonFlags getRevocationReasons() {
        return revocationReasons;
    }

    public boolean hasOnlyUserCerts() {
        return hasOnlyUserCerts;
    }

    public boolean hasOnlyCACerts() {
        return hasOnlyCACerts;
    }

    public boolean hasOnlyAttributeCerts() {
        return hasOnlyAttributeCerts;
    }

    public boolean isIndirectCRL() {
        return isIndirectCRL;
    }

     // Encodes this extension value
    private void encodeThis() {

        if (distributionPoint == null &&
            revocationReasons == null &&
            !hasOnlyUserCerts &&
            !hasOnlyCACerts &&
            !hasOnlyAttributeCerts &&
            !isIndirectCRL) {

            this.extensionValue = null;
            return;

        }

        DerOutputStream tagged = new DerOutputStream();

        if (distributionPoint != null) {
            DerOutputStream tmp = new DerOutputStream();
            distributionPoint.encode(tmp);
            // DistributionPointName is CHOICE. Do not writeImplicit.
            tagged.write(DerValue.createTag(DerValue.TAG_CONTEXT, true,
                TAG_DISTRIBUTION_POINT), tmp);
        }

        if (hasOnlyUserCerts) {
            DerOutputStream tmp = new DerOutputStream();
            tmp.putBoolean(hasOnlyUserCerts);
            tagged.writeImplicit(DerValue.createTag(DerValue.TAG_CONTEXT, false,
                TAG_ONLY_USER_CERTS), tmp);
        }

        if (hasOnlyCACerts) {
            DerOutputStream tmp = new DerOutputStream();
            tmp.putBoolean(hasOnlyCACerts);
            tagged.writeImplicit(DerValue.createTag(DerValue.TAG_CONTEXT, false,
                TAG_ONLY_CA_CERTS), tmp);
        }

        if (revocationReasons != null) {
            DerOutputStream tmp = new DerOutputStream();
            revocationReasons.encode(tmp);
            tagged.writeImplicit(DerValue.createTag(DerValue.TAG_CONTEXT, false,
                TAG_ONLY_SOME_REASONS), tmp);
        }

        if (isIndirectCRL) {
            DerOutputStream tmp = new DerOutputStream();
            tmp.putBoolean(isIndirectCRL);
            tagged.writeImplicit(DerValue.createTag(DerValue.TAG_CONTEXT, false,
                TAG_INDIRECT_CRL), tmp);
        }

        if (hasOnlyAttributeCerts) {
            DerOutputStream tmp = new DerOutputStream();
            tmp.putBoolean(hasOnlyAttributeCerts);
            tagged.writeImplicit(DerValue.createTag(DerValue.TAG_CONTEXT, false,
                TAG_ONLY_ATTRIBUTE_CERTS), tmp);
        }

        DerOutputStream seq = new DerOutputStream();
        seq.write(DerValue.tag_Sequence, tagged);
        this.extensionValue = seq.toByteArray();
    }

    /**
     * Returns the extension as user readable string.
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString())
            .append("IssuingDistributionPoint [\n  ");

        if (distributionPoint != null) {
            sb.append(distributionPoint);
        }

        if (revocationReasons != null) {
            sb.append(revocationReasons);
        }

        sb.append("  Only contains user certs: ")
            .append(hasOnlyUserCerts)
            .append('\n')
            .append("  Only contains CA certs: ")
            .append(hasOnlyCACerts)
            .append('\n')
            .append("  Only contains attribute certs: ")
            .append(hasOnlyAttributeCerts)
            .append('\n')
            .append("  Indirect CRL: ")
            .append(isIndirectCRL)
            .append("\n]\n");

        return sb.toString();
    }

}
