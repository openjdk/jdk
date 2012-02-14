/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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
import java.io.OutputStream;
import java.security.cert.CRLReason;
import java.util.Enumeration;

import sun.security.util.*;

/**
 * The reasonCode is a non-critical CRL entry extension that identifies
 * the reason for the certificate revocation. CAs are strongly
 * encouraged to include reason codes in CRL entries; however, the
 * reason code CRL entry extension should be absent instead of using the
 * unspecified (0) reasonCode value.
 * <p>The ASN.1 syntax for this is:
 * <pre>
 *  id-ce-cRLReason OBJECT IDENTIFIER ::= { id-ce 21 }
 *
 *  -- reasonCode ::= { CRLReason }
 *
 * CRLReason ::= ENUMERATED {
 *    unspecified             (0),
 *    keyCompromise           (1),
 *    cACompromise            (2),
 *    affiliationChanged      (3),
 *    superseded              (4),
 *    cessationOfOperation    (5),
 *    certificateHold         (6),
 *    removeFromCRL           (8),
 *    privilegeWithdrawn      (9),
 *    aACompromise           (10) }
 * </pre>
 * @author Hemma Prafullchandra
 * @see Extension
 * @see CertAttrSet
 */
public class CRLReasonCodeExtension extends Extension
        implements CertAttrSet<String> {

    /**
     * Attribute name and Reason codes
     */
    public static final String NAME = "CRLReasonCode";
    public static final String REASON = "reason";

    public static final int UNSPECIFIED = 0;
    public static final int KEY_COMPROMISE = 1;
    public static final int CA_COMPROMISE = 2;
    public static final int AFFLIATION_CHANGED = 3;
    public static final int SUPERSEDED = 4;
    public static final int CESSATION_OF_OPERATION = 5;
    public static final int CERTIFICATE_HOLD = 6;
    // note 7 missing in syntax
    public static final int REMOVE_FROM_CRL = 8;
    public static final int PRIVILEGE_WITHDRAWN = 9;
    public static final int AA_COMPROMISE = 10;

    private static CRLReason[] values = CRLReason.values();

    private int reasonCode = 0;

    private void encodeThis() throws IOException {
        if (reasonCode == 0) {
            this.extensionValue = null;
            return;
        }
        DerOutputStream dos = new DerOutputStream();
        dos.putEnumerated(reasonCode);
        this.extensionValue = dos.toByteArray();
    }

    /**
     * Create a CRLReasonCodeExtension with the passed in reason.
     * Criticality automatically set to false.
     *
     * @param reason the enumerated value for the reason code.
     */
    public CRLReasonCodeExtension(int reason) throws IOException {
        this(false, reason);
    }

    /**
     * Create a CRLReasonCodeExtension with the passed in reason.
     *
     * @param critical true if the extension is to be treated as critical.
     * @param reason the enumerated value for the reason code.
     */
    public CRLReasonCodeExtension(boolean critical, int reason)
    throws IOException {
        this.extensionId = PKIXExtensions.ReasonCode_Id;
        this.critical = critical;
        this.reasonCode = reason;
        encodeThis();
    }

    /**
     * Create the extension from the passed DER encoded value of the same.
     *
     * @param critical true if the extension is to be treated as critical.
     * @param value an array of DER encoded bytes of the actual value.
     * @exception ClassCastException if value is not an array of bytes
     * @exception IOException on error.
     */
    public CRLReasonCodeExtension(Boolean critical, Object value)
    throws IOException {
        this.extensionId = PKIXExtensions.ReasonCode_Id;
        this.critical = critical.booleanValue();
        this.extensionValue = (byte[]) value;
        DerValue val = new DerValue(this.extensionValue);
        this.reasonCode = val.getEnumerated();
    }

    /**
     * Set the attribute value.
     */
    public void set(String name, Object obj) throws IOException {
        if (!(obj instanceof Integer)) {
            throw new IOException("Attribute must be of type Integer.");
        }
        if (name.equalsIgnoreCase(REASON)) {
            reasonCode = ((Integer)obj).intValue();
        } else {
            throw new IOException
                ("Name not supported by CRLReasonCodeExtension");
        }
        encodeThis();
    }

    /**
     * Get the attribute value.
     */
    public Integer get(String name) throws IOException {
        if (name.equalsIgnoreCase(REASON)) {
            return new Integer(reasonCode);
        } else {
            throw new IOException
                ("Name not supported by CRLReasonCodeExtension");
        }
    }

    /**
     * Delete the attribute value.
     */
    public void delete(String name) throws IOException {
        if (name.equalsIgnoreCase(REASON)) {
            reasonCode = 0;
        } else {
            throw new IOException
                ("Name not supported by CRLReasonCodeExtension");
        }
        encodeThis();
    }

    /**
     * Returns a printable representation of the Reason code.
     */
    public String toString() {
        return super.toString() + "    Reason Code: " + values[reasonCode];
    }

    /**
     * Write the extension to the DerOutputStream.
     *
     * @param out the DerOutputStream to write the extension to.
     * @exception IOException on encoding errors.
     */
    public void encode(OutputStream out) throws IOException {
        DerOutputStream  tmp = new DerOutputStream();

        if (this.extensionValue == null) {
            this.extensionId = PKIXExtensions.ReasonCode_Id;
            this.critical = false;
            encodeThis();
        }
        super.encode(tmp);
        out.write(tmp.toByteArray());
    }

    /**
     * Return an enumeration of names of attributes existing within this
     * attribute.
     */
    public Enumeration<String> getElements() {
        AttributeNameEnumeration elements = new AttributeNameEnumeration();
        elements.addElement(REASON);

        return elements.elements();
    }

    /**
     * Return the name of this attribute.
     */
    public String getName() {
        return NAME;
    }

    /**
     * Return the reason as a CRLReason enum.
     */
    public CRLReason getReasonCode() {
        // if out-of-range, return UNSPECIFIED
        if (reasonCode > 0 && reasonCode < values.length) {
            return values[reasonCode];
        } else {
            return CRLReason.UNSPECIFIED;
        }
    }
}
