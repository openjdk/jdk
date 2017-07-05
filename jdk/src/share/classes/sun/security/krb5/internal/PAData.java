/*
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

/*
 *
 *  (C) Copyright IBM Corp. 1999 All Rights Reserved.
 *  Copyright 1997 The Open Group Research Institute.  All rights reserved.
 */

package sun.security.krb5.internal;

import sun.security.util.*;
import sun.security.krb5.Asn1Exception;
import java.io.IOException;

/**
 * Implements the ASN.1 PA-DATA type.
 *
 * <xmp>
 * PA-DATA         ::= SEQUENCE {
 *         -- NOTE: first tag is [1], not [0]
 *         padata-type     [1] Int32,
 *         padata-value    [2] OCTET STRING -- might be encoded AP-REQ
 * }
 * </xmp>
 *
 * <p>
 * This definition reflects the Network Working Group RFC 4120
 * specification available at
 * <a href="http://www.ietf.org/rfc/rfc4120.txt">
 * http://www.ietf.org/rfc/rfc4120.txt</a>.
 */

public class PAData {
    private int pADataType;
    private byte[] pADataValue = null;
    private static final byte TAG_PATYPE = 1;
    private static final byte TAG_PAVALUE = 2;

    private PAData() {
    }

    public PAData(int new_pADataType, byte[] new_pADataValue) {
        pADataType = new_pADataType;
        if (new_pADataValue != null) {
            pADataValue = new_pADataValue.clone();
        }
    }

    public Object clone() {
        PAData new_pAData = new PAData();
        new_pAData.pADataType = pADataType;
        if (pADataValue != null) {
            new_pAData.pADataValue = new byte[pADataValue.length];
            System.arraycopy(pADataValue, 0, new_pAData.pADataValue,
                             0, pADataValue.length);
        }
        return new_pAData;
    }

    /**
     * Constructs a PAData object.
     * @param encoding a Der-encoded data.
     * @exception Asn1Exception if an error occurs while decoding an ASN1 encoded data.
     * @exception IOException if an I/O error occurs while reading encoded data.
     */
    public PAData(DerValue encoding) throws Asn1Exception, IOException {
        DerValue der = null;
        if (encoding.getTag() != DerValue.tag_Sequence) {
            throw new Asn1Exception(Krb5.ASN1_BAD_ID);
        }
        der = encoding.getData().getDerValue();
        if ((der.getTag() & 0x1F) == 0x01) {
            this.pADataType = der.getData().getBigInteger().intValue();
        }
        else
            throw new Asn1Exception(Krb5.ASN1_BAD_ID);
        der = encoding.getData().getDerValue();
        if ((der.getTag() & 0x1F) == 0x02) {
            this.pADataValue = der.getData().getOctetString();
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

        temp.putInteger(pADataType);
        bytes.write(DerValue.createTag(DerValue.TAG_CONTEXT, true, TAG_PATYPE), temp);
        temp = new DerOutputStream();
        temp.putOctetString(pADataValue);
        bytes.write(DerValue.createTag(DerValue.TAG_CONTEXT, true, TAG_PAVALUE), temp);

        temp = new DerOutputStream();
        temp.write(DerValue.tag_Sequence, bytes);
        return temp.toByteArray();
    }

    // accessor methods
    public int getType() {
        return pADataType;
    }

    public byte[] getValue() {
        return ((pADataValue == null) ? null : pADataValue.clone());
    }
}
