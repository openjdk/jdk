/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.krb5.internal;

import sun.security.util.*;
import sun.security.krb5.Asn1Exception;
import java.io.IOException;

/**
 * Implements the ASN.1 ETYPE-INFO-ENTRY type.
 *
 * ETYPE-INFO-ENTRY     ::= SEQUENCE {
 *         etype        [0] Int32,
 *         salt         [1] OCTET STRING OPTIONAL
 *   }
 *
 * @author Seema Malkani
 */

public class ETypeInfo {

    private int etype;
    private byte[] salt = null;

    private static final byte TAG_TYPE = 0;
    private static final byte TAG_VALUE = 1;

    private ETypeInfo() {
    }

    public ETypeInfo(int etype, byte[] salt) {
        this.etype = etype;
        if (salt != null) {
            this.salt = salt.clone();
        }
    }

    public Object clone() {
        ETypeInfo etypeInfo = new ETypeInfo();
        etypeInfo.etype = etype;
        if (salt != null) {
            etypeInfo.salt = new byte[salt.length];
            System.arraycopy(salt, 0, etypeInfo.salt, 0, salt.length);
        }
        return etypeInfo;
    }

    /**
     * Constructs a ETypeInfo object.
     * @param encoding a DER-encoded data.
     * @exception Asn1Exception if an error occurs while decoding an
     *            ASN1 encoded data.
     * @exception IOException if an I/O error occurs while reading encoded data.
     */
    public ETypeInfo(DerValue encoding) throws Asn1Exception, IOException {
        DerValue der = null;

        if (encoding.getTag() != DerValue.tag_Sequence) {
            throw new Asn1Exception(Krb5.ASN1_BAD_ID);
        }

        // etype
        der = encoding.getData().getDerValue();
        if ((der.getTag() & 0x1F) == 0x00) {
            this.etype = der.getData().getBigInteger().intValue();
        }
        else
            throw new Asn1Exception(Krb5.ASN1_BAD_ID);

        // salt
        if (encoding.getData().available() > 0) {
            der = encoding.getData().getDerValue();
            if ((der.getTag() & 0x1F) == 0x01) {
                this.salt = der.getData().getOctetString();
            }
        }

        if (encoding.getData().available() > 0)
            throw new Asn1Exception(Krb5.ASN1_BAD_ID);
    }

    /**
     * Encodes this object to an OutputStream.
     *
     * @return byte array of the encoded data.
     * @exception IOException if an I/O error occurs while reading encoded data.
     * @exception Asn1Exception on encoding errors.
     */
    public byte[] asn1Encode() throws Asn1Exception, IOException {

        DerOutputStream bytes = new DerOutputStream();
        DerOutputStream temp = new DerOutputStream();

        temp.putInteger(etype);
        bytes.write(DerValue.createTag(DerValue.TAG_CONTEXT, true,
                                        TAG_TYPE), temp);

        if (salt != null) {
            temp = new DerOutputStream();
            temp.putOctetString(salt);
            bytes.write(DerValue.createTag(DerValue.TAG_CONTEXT, true,
                                        TAG_VALUE), temp);
        }

        temp = new DerOutputStream();
        temp.write(DerValue.tag_Sequence, bytes);
        return temp.toByteArray();
    }

    // accessor methods
    public int getEType() {
        return etype;
    }

    public byte[] getSalt() {
        return ((salt == null) ? null : salt.clone());
    }

}
