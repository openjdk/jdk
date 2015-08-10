/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.io.OutputStream;
import java.util.Enumeration;
import java.security.SecureRandom;

import sun.security.x509.AttributeNameEnumeration;
import sun.security.x509.CertAttrSet;
import sun.security.x509.Extension;
import sun.security.x509.PKIXExtensions;
import sun.security.util.*;

/**
 * Represent the OCSP Nonce Extension.
 * This extension, if present, provides a nonce value in OCSP requests
 * and responses.  This will cryptographically bind requests and responses
 * and help to prevent replay attacks (see RFC 6960, section 4.4.1).
 *
 * @see Extension
 * @see CertAttrSet
 */
public class OCSPNonceExtension extends Extension
implements CertAttrSet<String> {

    /**
     * Attribute name.
     */
    public static final String NAME = "OCSPNonce";
    public static final String NONCE = "nonce";

    private byte[] nonceData = null;
    private String extensionName;

    /**
     * Encode this extension value to DER and assign it to the
     * {@code extensionName} data member.
     *
     * @throws IOException if any errors occur during DER encoding
     */
    private void encodeInternal() throws IOException {
        if (nonceData == null) {
            this.extensionValue = null;
            return;
        }
        DerOutputStream os = new DerOutputStream();
        os.putOctetString(this.nonceData);
        this.extensionValue = os.toByteArray();
    }

    /**
     * Create a {@code OCSPNonceExtension} by providing the nonce length.
     * The criticality is set to false.  The random bytes will be generated
     * using the SUN provider.
     *
     * @param length the number of random bytes composing the nonce
     *
     * @throws IOException if any errors happen during encoding of the
     *      extension.
     */
    public OCSPNonceExtension(int length) throws IOException {
        this(PKIXExtensions.OCSPNonce_Id, false, length, NAME);
    }

    /**
     * Creates the extension (also called by the subclass).
     *
     * @param extensionId the {@code ObjectIdentifier} for the OCSP Nonce
     *      extension
     * @param isCritical a boolean flag indicating if the criticality bit
     *      is to be set for this extension
     * @param length the length of the nonce in bytes
     * @param extensionName the name of the extension
     *
     * @throws IOException if any errors happen during encoding of the
     *      extension.
     */
    protected OCSPNonceExtension(ObjectIdentifier extensionId,
            boolean isCritical, int length, String extensionName)
            throws IOException {
        SecureRandom rng = new SecureRandom();
        this.nonceData = new byte[length];
        rng.nextBytes(nonceData);
        this.extensionId = extensionId;
        this.critical = isCritical;
        this.extensionName = extensionName;
        encodeInternal();
    }

    /**
     * Create the extension using the provided criticality bit setting and
     * DER encoding.
     *
     * @param critical true if the extension is to be treated as critical.
     * @param value an array of DER encoded bytes of the extnValue for the
     *      extension.  It must not include the encapsulating OCTET STRING
     *      tag and length.  For an {@code OCSPNonceExtension} the data value
     *      should be a simple OCTET STRING containing random bytes
     *      (see RFC 6960, section 4.4.1).
     *
     * @throws ClassCastException if value is not an array of bytes
     * @throws IOException if any errors happen during encoding of the
     *      extension
     */
    public OCSPNonceExtension(Boolean critical, Object value)
            throws IOException {
        this(PKIXExtensions.OCSPNonce_Id, critical, value, NAME);
    }

    /**
     * Creates the extension (also called by the subclass).
     *
     * @param extensionId the {@code ObjectIdentifier} for the OCSP Nonce
     *      extension
     * @param critical a boolean flag indicating if the criticality bit
     *      is to be set for this extension
     * @param value an array of DER encoded bytes of the extnValue for the
     *      extension.  It must not include the encapsulating OCTET STRING
     *      tag and length.  For an {@code OCSPNonceExtension} the data value
     *      should be a simple OCTET STRING containing random bytes
     *      (see RFC 6960, section 4.4.1).
     * @param extensionName the name of the extension
     *
     * @throws ClassCastException if value is not an array of bytes
     * @throws IOException if any errors happen during encoding of the
     *      extension
     */
    protected OCSPNonceExtension(ObjectIdentifier extensionId,
            Boolean critical, Object value, String extensionName)
            throws IOException {
        this.extensionId = extensionId;
        this.critical = critical;
        this.extensionValue = (byte[]) value;
        DerValue val = new DerValue(this.extensionValue);
        this.nonceData = val.getOctetString();
        this.extensionName = extensionName;
    }

    /**
     * Set the attribute value.
     *
     * @param name the name of the attribute.
     * @param obj an array of nonce bytes for the extension.  It must not
     *      contain any DER tags or length.
     *
     * @throws IOException if an unsupported name is provided or the supplied
     *      {@code obj} is not a byte array
     */
    @Override
    public void set(String name, Object obj) throws IOException {
        if (name.equalsIgnoreCase(NONCE)) {
            if (!(obj instanceof byte[])) {
                throw new IOException("Attribute must be of type byte[].");
            }
            nonceData = (byte[])obj;
        } else {
            throw new IOException("Attribute name not recognized by"
                    + " CertAttrSet:" + extensionName + ".");
        }
        encodeInternal();
    }

    /**
     * Get the attribute value.
     *
     * @param name the name of the attribute to retrieve.  Only "OCSPNonce"
     *      is currently supported.
     *
     * @return an array of bytes that are the nonce data.  It will not contain
     *      any DER tags or length, only the random nonce bytes.
     *
     * @throws IOException if an unsupported name is provided.
     */
    @Override
    public Object get(String name) throws IOException {
        if (name.equalsIgnoreCase(NONCE)) {
            return nonceData;
        } else {
            throw new IOException("Attribute name not recognized by"
                    + " CertAttrSet:" + extensionName + ".");
        }
    }

    /**
     * Delete the attribute value.
     *
     * @param name the name of the attribute to retrieve.  Only "OCSPNonce"
     *      is currently supported.
     *
     * @throws IOException if an unsupported name is provided or an error
     *      occurs during re-encoding of the extension.
     */
    @Override
    public void delete(String name) throws IOException {
        if (name.equalsIgnoreCase(NONCE)) {
            nonceData = null;
        } else {
            throw new IOException("Attribute name not recognized by"
                  + " CertAttrSet:" + extensionName + ".");
        }
        encodeInternal();
    }

    /**
     * Returns a printable representation of the {@code OCSPNonceExtension}.
     */
    @Override
    public String toString() {
        String s = super.toString() + extensionName + ": " +
                ((nonceData == null) ? "" : Debug.toString(nonceData))
                + "\n";
        return (s);
    }

    /**
     * Write the extension to an {@code OutputStream}
     *
     * @param out the {@code OutputStream} to write the extension to.
     *
     * @throws IOException on encoding errors.
     */
    @Override
    public void encode(OutputStream out) throws IOException {
        encode(out, PKIXExtensions.OCSPNonce_Id, this.critical);
    }

    /**
     * Write the extension to the DerOutputStream.
     *
     * @param out the {@code OutputStream} to write the extension to.
     * @param extensionId the {@code ObjectIdentifier} used for this extension
     * @param isCritical a flag indicating if the criticality bit is set for
     *      this extension.
     *
     * @throws IOException on encoding errors.
     */
    protected void encode(OutputStream out, ObjectIdentifier extensionId,
            boolean isCritical) throws IOException {

        DerOutputStream tmp = new DerOutputStream();

        if (this.extensionValue == null) {
            this.extensionId = extensionId;
            this.critical = isCritical;
            encodeInternal();
        }
        super.encode(tmp);
        out.write(tmp.toByteArray());
    }

    /**
     * Return an enumeration of names of attributes existing within this
     * attribute.
     */
    @Override
    public Enumeration<String> getElements() {
        AttributeNameEnumeration elements = new AttributeNameEnumeration();
        elements.addElement(NONCE);
        return (elements.elements());
    }

    /**
     * Return the name of this attribute.
     */
    @Override
    public String getName() {
        return (extensionName);
    }
}
