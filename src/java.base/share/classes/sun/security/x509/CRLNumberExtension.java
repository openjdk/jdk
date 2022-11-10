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
import java.math.BigInteger;

import sun.security.util.*;

/**
 * Represent the CRL Number Extension.
 *
 * <p>This extension, if present, conveys a monotonically increasing
 * sequence number for each CRL issued by a given CA through a specific
 * CA X.500 Directory entry or CRL distribution point. This extension
 * allows users to easily determine when a particular CRL supersedes
 * another CRL.
 *
 * @author Hemma Prafullchandra
 * @see Extension
 */
public class CRLNumberExtension extends Extension {

    public static final String NAME = "CRLNumber";

    private static final String LABEL = "CRL Number";

    private BigInteger crlNumber;
    private final String extensionName;
    private final String extensionLabel;

    // Encode this extension value
    private void encodeThis() throws IOException {
        if (crlNumber == null) {
            this.extensionValue = null;
            return;
        }
        DerOutputStream os = new DerOutputStream();
        os.putInteger(this.crlNumber);
        this.extensionValue = os.toByteArray();
    }

    /**
     * Create a CRLNumberExtension with the integer value .
     * The criticality is set to false.
     *
     * @param crlNum the value to be set for the extension.
     */
    public CRLNumberExtension(int crlNum) throws IOException {
        this(PKIXExtensions.CRLNumber_Id, false, BigInteger.valueOf(crlNum),
        NAME, LABEL);
    }

    /**
     * Create a CRLNumberExtension with the BigInteger value .
     * The criticality is set to false.
     *
     * @param crlNum the value to be set for the extension.
     */
    public CRLNumberExtension(BigInteger crlNum) throws IOException {
        this(PKIXExtensions.CRLNumber_Id, false, crlNum, NAME, LABEL);
    }

    /**
     * Creates the extension (also called by the subclass).
     */
    protected CRLNumberExtension(ObjectIdentifier extensionId,
        boolean isCritical, BigInteger crlNum, String extensionName,
        String extensionLabel) throws IOException {

        this.extensionId = extensionId;
        this.critical = isCritical;
        this.crlNumber = crlNum;
        this.extensionName = extensionName;
        this.extensionLabel = extensionLabel;
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
    public CRLNumberExtension(Boolean critical, Object value)
    throws IOException {
        this(PKIXExtensions.CRLNumber_Id, critical, value, NAME, LABEL);
    }

    /**
     * Creates the extension (also called by the subclass).
     */
    protected CRLNumberExtension(ObjectIdentifier extensionId,
        Boolean critical, Object value, String extensionName,
        String extensionLabel) throws IOException {

        this.extensionId = extensionId;
        this.critical = critical.booleanValue();
        this.extensionValue = (byte[]) value;
        DerValue val = new DerValue(this.extensionValue);
        this.crlNumber = val.getBigInteger();
        this.extensionName = extensionName;
        this.extensionLabel = extensionLabel;
    }

    /**
     * Get the crlNumber value.
     */
    public BigInteger getCrlNumber() {
        return crlNumber;
    }


    /**
     * Returns a printable representation of the CRLNumberExtension.
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString())
            .append(extensionLabel)
            .append(": ");
        if (crlNumber != null) {
            sb.append(Debug.toHexString(crlNumber));
        }
        sb.append('\n');
        return sb.toString();
    }

    /**
     * Write the extension to the DerOutputStream.
     *
     * @param out the DerOutputStream to write the extension to.
     * @exception IOException on encoding errors.
     */
    @Override
    public void encode(DerOutputStream out) throws IOException {
        encode(out, PKIXExtensions.CRLNumber_Id, true);
    }

    /**
     * Write the extension to the DerOutputStream.
     * (Also called by the subclass)
     */
    protected void encode(DerOutputStream out, ObjectIdentifier extensionId,
            boolean isCritical) throws IOException {

       if (this.extensionValue == null) {
           this.extensionId = extensionId;
           this.critical = isCritical;
           encodeThis();
       }
       super.encode(out);
    }


    /**
     * Return the name of this extension.
     */
    @Override
    public String getName() {
        return extensionName;
    }
}
