/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.util.Date;
import java.util.Objects;

import sun.security.util.*;

/**
 * This class defines the Private Key Usage Extension.
 *
 * <p>The Private Key Usage Period extension allows the certificate issuer
 * to specify a different validity period for the private key than the
 * certificate. This extension is intended for use with digital
 * signature keys.  This extension consists of two optional components
 * notBefore and notAfter.  The private key associated with the
 * certificate should not be used to sign objects before or after the
 * times specified by the two components, respectively.
 *
 * <pre>
 * PrivateKeyUsagePeriod ::= SEQUENCE {
 *     notBefore  [0]  GeneralizedTime OPTIONAL,
 *     notAfter   [1]  GeneralizedTime OPTIONAL }
 * </pre>
 *
 * @author Amit Kapoor
 * @author Hemma Prafullchandra
 * @see Extension
 */
public class PrivateKeyUsageExtension extends Extension {

    public static final String NAME = "PrivateKeyUsage";

    // Private data members
    private static final byte TAG_BEFORE = 0;
    private static final byte TAG_AFTER = 1;

    private Date        notBefore = null;
    private Date        notAfter = null;

    // Encode this extension value.
    private void encodeThis() {
        if (notBefore == null && notAfter == null) {
            this.extensionValue = null;
            return;
        }
        DerOutputStream seq = new DerOutputStream();

        DerOutputStream tagged = new DerOutputStream();
        if (notBefore != null) {
            DerOutputStream tmp = new DerOutputStream();
            tmp.putGeneralizedTime(notBefore);
            tagged.writeImplicit(DerValue.createTag(DerValue.TAG_CONTEXT,
                                 false, TAG_BEFORE), tmp);
        }
        if (notAfter != null) {
            DerOutputStream tmp = new DerOutputStream();
            tmp.putGeneralizedTime(notAfter);
            tagged.writeImplicit(DerValue.createTag(DerValue.TAG_CONTEXT,
                                 false, TAG_AFTER), tmp);
        }
        seq.write(DerValue.tag_Sequence, tagged);
        this.extensionValue = seq.toByteArray();
    }

    /**
     * The default constructor for PrivateKeyUsageExtension. At least one
     * of the arguments must be non null.
     *
     * @param notBefore the date/time before which the private key
     *         should not be used
     * @param notAfter the date/time after which the private key
     *         should not be used.
     */
    public PrivateKeyUsageExtension(Date notBefore, Date notAfter) {
        if (notBefore == null && notAfter == null) {
            throw new IllegalArgumentException(
                    "notBefore and notAfter cannot both be null");
        }
        this.notBefore = notBefore;
        this.notAfter = notAfter;

        this.extensionId = PKIXExtensions.PrivateKeyUsage_Id;
        this.critical = false;
        encodeThis();
    }

    /**
     * Create the extension from the passed DER encoded value.
     *
     * @param critical true if the extension is to be treated as critical.
     * @param value an array of DER encoded bytes of the actual value.
     * @exception ClassCastException if value is not an array of bytes
     * @exception CertificateException on certificate parsing errors.
     * @exception IOException on error.
     */
    public PrivateKeyUsageExtension(Boolean critical, Object value)
    throws CertificateException, IOException {
        this.extensionId = PKIXExtensions.PrivateKeyUsage_Id;
        this.critical = critical.booleanValue();

        this.extensionValue = (byte[]) value;
        DerInputStream str = new DerInputStream(this.extensionValue);
        DerValue[] seq = str.getSequence(2);

        // NB. this is always encoded with the IMPLICIT tag
        // The checks only make sense if we assume implicit tagging,
        // with explicit tagging the form is always constructed.
        for (int i = 0; i < seq.length; i++) {
            DerValue opt = seq[i];

            if (opt.isContextSpecific(TAG_BEFORE) &&
                !opt.isConstructed()) {
                if (notBefore != null) {
                    throw new CertificateParsingException(
                        "Duplicate notBefore in PrivateKeyUsage.");
                }
                opt.resetTag(DerValue.tag_GeneralizedTime);
                str = new DerInputStream(opt.toByteArray());
                notBefore = str.getGeneralizedTime();

            } else if (opt.isContextSpecific(TAG_AFTER) &&
                       !opt.isConstructed()) {
                if (notAfter != null) {
                    throw new CertificateParsingException(
                        "Duplicate notAfter in PrivateKeyUsage.");
                }
                opt.resetTag(DerValue.tag_GeneralizedTime);
                str = new DerInputStream(opt.toByteArray());
                notAfter = str.getGeneralizedTime();
            } else
                throw new IOException("Invalid encoding of " +
                                      "PrivateKeyUsageExtension");
        }
    }

    /**
     * Return the printable string.
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString())
            .append("PrivateKeyUsage: [\n");
        if (notBefore != null) {
            sb.append("From: ")
                .append(notBefore);
            if (notAfter != null) {
                sb.append(", ");
            }
        }
        if (notAfter != null) {
            sb.append("To: ")
                .append(notAfter);
        }
        sb.append("]\n");
        return sb.toString();
    }

    /**
     * Verify that the current time is within the validity period.
     *
     * @exception CertificateExpiredException if the certificate has expired.
     * @exception CertificateNotYetValidException if the certificate is not
     * yet valid.
     */
    public void valid()
    throws CertificateNotYetValidException, CertificateExpiredException {
        Date now = new Date();
        valid(now);
    }

    /**
     * Verify that the passed time is within the validity period.
     *
     * @exception CertificateExpiredException if the certificate has expired
     * with respect to the <code>Date</code> supplied.
     * @exception CertificateNotYetValidException if the certificate is not
     * yet valid with respect to the <code>Date</code> supplied.
     *
     */
    public void valid(Date now)
    throws CertificateNotYetValidException, CertificateExpiredException {
        Objects.requireNonNull(now);
        /*
         * we use the internal Dates rather than the passed in Date
         * because someone could override the Date methods after()
         * and before() to do something entirely different.
         */
        if (notBefore != null && notBefore.after(now)) {
            throw new CertificateNotYetValidException("NotBefore: " +
                                                      notBefore.toString());
        }
        if (notAfter != null && notAfter.before(now)) {
            throw new CertificateExpiredException("NotAfter: " +
                                                  notAfter.toString());
        }
    }

    /**
     * Write the extension to the OutputStream.
     *
     * @param out the DerOutputStream to write the extension to.
     */
    @Override
    public void encode(DerOutputStream out) {
        if (extensionValue == null) {
            extensionId = PKIXExtensions.PrivateKeyUsage_Id;
            critical = false;
            encodeThis();
        }
        super.encode(out);
    }

    public Date getNotBefore() {
        return new Date(notBefore.getTime());
    }

    public Date getNotAfter() {
        return new Date(notAfter.getTime());
    }

    /**
     * Return the name of this extension.
     */
    @Override
    public String getName() {
      return NAME;
    }
}
