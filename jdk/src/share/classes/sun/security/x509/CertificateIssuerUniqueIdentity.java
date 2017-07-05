/*
 * Copyright 1997-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;

import sun.security.util.*;

/**
 * This class defines the subject/issuer unique identity attribute
 * for the Certificate.
 *
 * @author Amit Kapoor
 * @author Hemma Prafullchandra
 * @see CertAttrSet
 */
public class CertificateIssuerUniqueIdentity implements CertAttrSet<String> {
    private UniqueIdentity      id;

    /**
     * Identifier for this attribute, to be used with the
     * get, set, delete methods of Certificate, x509 type.
     */
    public static final String IDENT = "x509.info.issuerID";
    /**
     * Sub attributes name for this CertAttrSet.
     */
    public static final String NAME = "issuerID";
    public static final String ID = "id";

    /**
     * Default constructor for the certificate attribute.
     *
     * @param key the UniqueIdentity
     */
    public CertificateIssuerUniqueIdentity(UniqueIdentity id) {
        this.id = id;
    }

    /**
     * Create the object, decoding the values from the passed DER stream.
     *
     * @param in the DerInputStream to read the UniqueIdentity from.
     * @exception IOException on decoding errors.
     */
    public CertificateIssuerUniqueIdentity(DerInputStream in)
    throws IOException {
        id = new UniqueIdentity(in);
    }

    /**
     * Create the object, decoding the values from the passed stream.
     *
     * @param in the InputStream to read the UniqueIdentity from.
     * @exception IOException on decoding errors.
     */
    public CertificateIssuerUniqueIdentity(InputStream in)
    throws IOException {
        DerValue val = new DerValue(in);
        id = new UniqueIdentity(val);
    }

    /**
     * Create the object, decoding the values from the passed DER value.
     *
     * @param in the DerValue to read the UniqueIdentity from.
     * @exception IOException on decoding errors.
     */
    public CertificateIssuerUniqueIdentity(DerValue val)
    throws IOException {
        id = new UniqueIdentity(val);
    }

    /**
     * Return the identity as user readable string.
     */
    public String toString() {
        if (id == null) return "";
        return (id.toString());
    }

    /**
     * Encode the identity in DER form to the stream.
     *
     * @param out the DerOutputStream to marshal the contents to.
     * @exception IOException on errors.
     */
    public void encode(OutputStream out) throws IOException {
        DerOutputStream tmp = new DerOutputStream();
        id.encode(tmp,DerValue.createTag(DerValue.TAG_CONTEXT,false,(byte)1));

        out.write(tmp.toByteArray());
    }

    /**
     * Set the attribute value.
     */
    public void set(String name, Object obj) throws IOException {
        if (!(obj instanceof UniqueIdentity)) {
            throw new IOException("Attribute must be of type UniqueIdentity.");
        }
        if (name.equalsIgnoreCase(ID)) {
            id = (UniqueIdentity)obj;
        } else {
            throw new IOException("Attribute name not recognized by " +
                      "CertAttrSet: CertificateIssuerUniqueIdentity.");
        }
    }

    /**
     * Get the attribute value.
     */
    public Object get(String name) throws IOException {
        if (name.equalsIgnoreCase(ID)) {
            return (id);
        } else {
            throw new IOException("Attribute name not recognized by " +
                      "CertAttrSet: CertificateIssuerUniqueIdentity.");
        }
    }

    /**
     * Delete the attribute value.
     */
    public void delete(String name) throws IOException {
        if (name.equalsIgnoreCase(ID)) {
            id = null;
        } else {
            throw new IOException("Attribute name not recognized by " +
                      "CertAttrSet: CertificateIssuerUniqueIdentity.");
        }
    }

    /**
     * Return an enumeration of names of attributes existing within this
     * attribute.
     */
    public Enumeration<String> getElements() {
        AttributeNameEnumeration elements = new AttributeNameEnumeration();
        elements.addElement(ID);

        return (elements.elements());
    }

    /**
     * Return the name of this attribute.
     */
    public String getName() {
        return (NAME);
    }
}
