/*
 * Copyright (c) 2007, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Date;

import sun.security.util.*;

/**
 * From RFC 5280:
 * <p>
 * The invalidity date is a non-critical CRL entry extension that
 * provides the date on which it is known or suspected that the private
 * key was compromised or that the certificate otherwise became invalid.
 * This date may be earlier than the revocation date in the CRL entry,
 * which is the date at which the CA processed the revocation.  When a
 * revocation is first posted by a CRL issuer in a CRL, the invalidity
 * date may precede the date of issue of earlier CRLs, but the
 * revocation date SHOULD NOT precede the date of issue of earlier CRLs.
 * Whenever this information is available, CRL issuers are strongly
 * encouraged to share it with CRL users.
 * <p>
 * The GeneralizedTime values included in this field MUST be expressed
 * in Greenwich Mean Time (Zulu), and MUST be specified and interpreted
 * as defined in section 4.1.2.5.2.
 * <pre>
 * id-ce-invalidityDate OBJECT IDENTIFIER ::= { id-ce 24 }
 *
 * invalidityDate ::=  GeneralizedTime
 * </pre>
 *
 * @author Sean Mullan
 */
public class InvalidityDateExtension extends Extension {

    /**
     * Attribute name and Reason codes
     */
    public static final String NAME = "InvalidityDate";

    private Date date;

    private void encodeThis() {
        if (date == null) {
            this.extensionValue = null;
            return;
        }
        DerOutputStream dos = new DerOutputStream();
        dos.putGeneralizedTime(date);
        this.extensionValue = dos.toByteArray();
    }

    /**
     * Create a InvalidityDateExtension with the passed in date.
     * Criticality automatically set to false.
     *
     * @param date the invalidity date
     */
    public InvalidityDateExtension(Date date) {
        this(false, date);
    }

    /**
     * Create a InvalidityDateExtension with the passed in date.
     *
     * @param critical true if the extension is to be treated as critical.
     * @param date the invalidity date, cannot be null.
     */
    public InvalidityDateExtension(boolean critical, Date date) {
        if (date == null) {
            throw new IllegalArgumentException("date cannot be null");
        }
        this.extensionId = PKIXExtensions.InvalidityDate_Id;
        this.critical = critical;
        this.date = date;
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
    public InvalidityDateExtension(Boolean critical, Object value)
    throws IOException {
        this.extensionId = PKIXExtensions.InvalidityDate_Id;
        this.critical = critical.booleanValue();
        this.extensionValue = (byte[]) value;
        DerValue val = new DerValue(this.extensionValue);
        this.date = val.getGeneralizedTime();
    }

    /**
     * Get the Date value.
     */
    public Date getDate() throws IOException {
        if (date == null) {
            return null;
        } else {
            return new Date(date.getTime());    // clone
        }
    }


    /**
     * Returns a printable representation of the Invalidity Date.
     */
    public String toString() {
        return super.toString() + "    Invalidity Date: " + date;
    }

    /**
     * Write the extension to the DerOutputStream.
     *
     * @param out the DerOutputStream to write the extension to
     */
    @Override
    public void encode(DerOutputStream out) {
        if (this.extensionValue == null) {
            this.extensionId = PKIXExtensions.InvalidityDate_Id;
            this.critical = false;
            encodeThis();
        }
        super.encode(out);
    }


    /**
     * Return the name of this extension.
     */
    @Override
    public String getName() {
        return NAME;
    }

    public static InvalidityDateExtension toImpl(java.security.cert.Extension ext)
        throws IOException {
        if (ext instanceof InvalidityDateExtension) {
            return (InvalidityDateExtension) ext;
        } else {
            return new InvalidityDateExtension
                (Boolean.valueOf(ext.isCritical()), ext.getValue());
        }
    }
}
