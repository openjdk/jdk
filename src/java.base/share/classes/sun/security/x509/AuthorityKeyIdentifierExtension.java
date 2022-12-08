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

import sun.security.util.*;

/**
 * This class represents the Authority Key Identifier Extension.
 *
 * <p>The authority key identifier extension provides a means of
 * identifying the particular public key used to sign a certificate.
 * This extension would be used where an issuer has multiple signing
 * keys (either due to multiple concurrent key pairs or due to
 * changeover).
 * <p>
 * The ASN.1 syntax for this is:
 * <pre>
 * AuthorityKeyIdentifier ::= SEQUENCE {
 *    keyIdentifier             [0] KeyIdentifier           OPTIONAL,
 *    authorityCertIssuer       [1] GeneralNames            OPTIONAL,
 *    authorityCertSerialNumber [2] CertificateSerialNumber OPTIONAL
 * }
 * KeyIdentifier ::= OCTET STRING
 * </pre>
 * @author Amit Kapoor
 * @author Hemma Prafullchandra
 * @see Extension
 */
public class AuthorityKeyIdentifierExtension extends Extension {

    public static final String NAME = "AuthorityKeyIdentifier";

    // Private data members
    private static final byte TAG_ID = 0;
    private static final byte TAG_NAMES = 1;
    private static final byte TAG_SERIAL_NUM = 2;

    private KeyIdentifier       id = null;
    private GeneralNames        names = null;
    private SerialNumber        serialNum = null;

    // Encode only the extension value
    private void encodeThis() {
        if (id == null && names == null && serialNum == null) {
            this.extensionValue = null;
            return;
        }
        DerOutputStream seq = new DerOutputStream();
        DerOutputStream tmp = new DerOutputStream();
        if (id != null) {
            DerOutputStream tmp1 = new DerOutputStream();
            id.encode(tmp1);
            tmp.writeImplicit(DerValue.createTag(DerValue.TAG_CONTEXT,
                              false, TAG_ID), tmp1);
        }
        if (names != null) {
            DerOutputStream tmp1 = new DerOutputStream();
            names.encode(tmp1);
            tmp.writeImplicit(DerValue.createTag(DerValue.TAG_CONTEXT,
                              true, TAG_NAMES), tmp1);
        }
        if (serialNum != null) {
            DerOutputStream tmp1 = new DerOutputStream();
            serialNum.encode(tmp1);
            tmp.writeImplicit(DerValue.createTag(DerValue.TAG_CONTEXT,
                              false, TAG_SERIAL_NUM), tmp1);
        }
        seq.write(DerValue.tag_Sequence, tmp);
        this.extensionValue = seq.toByteArray();
    }

    /**
     * The default constructor for this extension. At least one parameter
     * must be non null. Null parameters make the element optional (not present).
     *
     * @param kid the KeyIdentifier associated with this extension.
     * @param names the GeneralNames associated with this extension
     * @param sn the CertificateSerialNumber associated with
     *        this extension.
     */
    public AuthorityKeyIdentifierExtension(KeyIdentifier kid, GeneralNames names,
                                           SerialNumber sn) {
        if (kid == null && names == null && sn == null) {
            throw new IllegalArgumentException(
                    "AuthorityKeyIdentifierExtension cannot be empty");
        }
        this.id = kid;
        this.names = names;
        this.serialNum = sn;

        this.extensionId = PKIXExtensions.AuthorityKey_Id;
        this.critical = false;
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
    public AuthorityKeyIdentifierExtension(Boolean critical, Object value)
    throws IOException {
        this.extensionId = PKIXExtensions.AuthorityKey_Id;
        this.critical = critical.booleanValue();

        this.extensionValue = (byte[]) value;
        DerValue val = new DerValue(this.extensionValue);
        if (val.tag != DerValue.tag_Sequence) {
            throw new IOException("Invalid encoding for " +
                                  "AuthorityKeyIdentifierExtension.");
        }

        // Note that all the fields in AuthorityKeyIdentifier are defined as
        // being OPTIONAL, i.e., there could be an empty SEQUENCE, resulting
        // in val.data being null.
        while ((val.data != null) && (val.data.available() != 0)) {
            DerValue opt = val.data.getDerValue();

            // NB. this is always encoded with the IMPLICIT tag
            // The checks only make sense if we assume implicit tagging,
            // with explicit tagging the form is always constructed.
            if (opt.isContextSpecific(TAG_ID) && !opt.isConstructed()) {
                if (id != null)
                    throw new IOException("Duplicate KeyIdentifier in " +
                                          "AuthorityKeyIdentifier.");
                opt.resetTag(DerValue.tag_OctetString);
                id = new KeyIdentifier(opt);

            } else if (opt.isContextSpecific(TAG_NAMES) &&
                       opt.isConstructed()) {
                if (names != null)
                    throw new IOException("Duplicate GeneralNames in " +
                                          "AuthorityKeyIdentifier.");
                opt.resetTag(DerValue.tag_Sequence);
                names = new GeneralNames(opt);

            } else if (opt.isContextSpecific(TAG_SERIAL_NUM) &&
                       !opt.isConstructed()) {
                if (serialNum != null)
                    throw new IOException("Duplicate SerialNumber in " +
                                          "AuthorityKeyIdentifier.");
                opt.resetTag(DerValue.tag_Integer);
                serialNum = new SerialNumber(opt);
            } else
                throw new IOException("Invalid encoding of " +
                                      "AuthorityKeyIdentifierExtension.");
        }
    }

    /**
     * Return the object as a string.
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString())
            .append("AuthorityKeyIdentifier [\n");
        if (id != null) {
            sb.append(id);       // id already has a newline
        }
        if (names != null) {
            sb.append(names).append('\n');
        }
        if (serialNum != null) {
            sb.append(serialNum).append('\n');
        }
        sb.append("]\n");
        return sb.toString();
    }

    /**
     * Write the extension to the OutputStream.
     *
     * @param out the DerOutputStream to write the extension to.
     */
    @Override
    public void encode(DerOutputStream out) {
        if (this.extensionValue == null) {
            extensionId = PKIXExtensions.AuthorityKey_Id;
            critical = false;
            encodeThis();
        }
        super.encode(out);
    }

    public KeyIdentifier getKeyIdentifier() {
        return id;
    }

    public GeneralNames getAuthName() {
        return names;
    }

    public SerialNumber getSerialNumber() {
        return serialNum;
    }


    /**
     * Return the name of this extension.
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Return the encoded key identifier, or null if not specified.
     */
    public byte[] getEncodedKeyIdentifier() throws IOException {
        if (id != null) {
            DerOutputStream derOut = new DerOutputStream();
            id.encode(derOut);
            return derOut.toByteArray();
        }
        return null;
    }
}
