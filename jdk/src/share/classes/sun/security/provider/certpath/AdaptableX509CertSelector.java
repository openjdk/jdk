/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.provider.certpath;

import java.io.IOException;
import java.util.Date;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.cert.X509CertSelector;
import java.security.cert.CertificateException;

import sun.security.util.DerOutputStream;
import sun.security.x509.SerialNumber;
import sun.security.x509.KeyIdentifier;
import sun.security.x509.AuthorityKeyIdentifierExtension;

/**
 * An adaptable X509 certificate selector for forward certification path
 * building.
 *
 * @since 1.7
 */
class AdaptableX509CertSelector extends X509CertSelector {
    // The start date of a validity period.
    private Date startDate;

    // The end date of a validity period.
    private Date endDate;

    // Is subject key identifier sensitive?
    private boolean isSKIDSensitive = false;

    // Is serial number sensitive?
    private boolean isSNSensitive = false;

    AdaptableX509CertSelector() {
        super();
    }

    /**
     * Sets the criterion of the X509Certificate validity period.
     *
     * Normally, we may not have to check that a certificate validity period
     * must fall within its issuer's certificate validity period. However,
     * when we face root CA key updates for version 1 certificates, according
     * to scheme of RFC 4210 or 2510, the validity periods should be checked
     * to determine the right issuer's certificate.
     *
     * Conservatively, we will only check the validity periods for version
     * 1 and version 2 certificates. For version 3 certificates, we can
     * determine the right issuer by authority and subject key identifier
     * extensions.
     *
     * @param startDate the start date of a validity period that must fall
     *        within the certificate validity period for the X509Certificate
     * @param endDate the end date of a validity period that must fall
     *        within the certificate validity period for the X509Certificate
     */
    void setValidityPeriod(Date startDate, Date endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * Parse the authority key identifier extension.
     *
     * If the keyIdentifier field of the extension is non-null, set the
     * subjectKeyIdentifier criterion. If the authorityCertSerialNumber
     * field is non-null, set the serialNumber criterion.
     *
     * Note that we will not set the subject criterion according to the
     * authorityCertIssuer field of the extension. The caller MUST set
     * the subject criterion before call match().
     *
     * @param akidext the authorityKeyIdentifier extension
     */
    void parseAuthorityKeyIdentifierExtension(
            AuthorityKeyIdentifierExtension akidext) throws IOException {
        if (akidext != null) {
            KeyIdentifier akid = (KeyIdentifier)akidext.get(
                    AuthorityKeyIdentifierExtension.KEY_ID);
            if (akid != null) {
                // Do not override the previous setting for initial selection.
                if (isSKIDSensitive || getSubjectKeyIdentifier() == null) {
                    DerOutputStream derout = new DerOutputStream();
                    derout.putOctetString(akid.getIdentifier());
                    super.setSubjectKeyIdentifier(derout.toByteArray());

                    isSKIDSensitive = true;
                }
            }

            SerialNumber asn = (SerialNumber)akidext.get(
                    AuthorityKeyIdentifierExtension.SERIAL_NUMBER);
            if (asn != null) {
                // Do not override the previous setting for initial selection.
                if (isSNSensitive || getSerialNumber() == null) {
                    super.setSerialNumber(asn.getNumber());
                    isSNSensitive = true;
                }
            }

            // the subject criterion should be set by the caller.
        }
    }

    /**
     * Decides whether a <code>Certificate</code> should be selected.
     *
     * For the purpose of compatibility, when a certificate is of
     * version 1 and version 2, or the certificate does not include
     * a subject key identifier extension, the selection criterion
     * of subjectKeyIdentifier will be disabled.
     */
    @Override
    public boolean match(Certificate cert) {
        if (!(cert instanceof X509Certificate)) {
            return false;
        }

        X509Certificate xcert = (X509Certificate)cert;
        int version = xcert.getVersion();

        // Check the validity period for version 1 and 2 certificate.
        if (version < 3) {
            if (startDate != null) {
                try {
                    xcert.checkValidity(startDate);
                } catch (CertificateException ce) {
                    return false;
                }
            }

            if (endDate != null) {
                try {
                    xcert.checkValidity(endDate);
                } catch (CertificateException ce) {
                    return false;
                }
            }
        }

        // If no SubjectKeyIdentifier extension, don't bother to check it.
        if (isSKIDSensitive &&
            (version < 3 || xcert.getExtensionValue("2.5.29.14") == null)) {
            setSubjectKeyIdentifier(null);
        }

        // In practice, a CA may replace its root certificate and require that
        // the existing certificate is still valid, even if the AKID extension
        // does not match the replacement root certificate fields.
        //
        // Conservatively, we only support the replacement for version 1 and
        // version 2 certificate. As for version 2, the certificate extension
        // may contain sensitive information (for example, policies), the
        // AKID need to be respected to seek the exact certificate in case
        // of key or certificate abuse.
        if (isSNSensitive && version < 3) {
            setSerialNumber(null);
        }

        return super.match(cert);
    }

    @Override
    public Object clone() {
        AdaptableX509CertSelector copy =
                        (AdaptableX509CertSelector)super.clone();
        if (startDate != null) {
            copy.startDate = (Date)startDate.clone();
        }

        if (endDate != null) {
            copy.endDate = (Date)endDate.clone();
        }

        return copy;
    }
}
