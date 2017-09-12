/*
 * Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.
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
 *  (C) Copyright IBM Corp. 1999 All Rights Reserved.
 *  Copyright 1997 The Open Group Research Institute.  All rights reserved.
 */

package sun.security.krb5;

import java.util.Arrays;
import sun.security.util.*;
import sun.security.krb5.internal.*;
import sun.security.krb5.internal.crypto.*;
import java.io.IOException;
import java.math.BigInteger;

/**
 * This class encapsulates the concept of a Kerberos checksum.
 */
public class Checksum {

    private int cksumType;
    private byte[] checksum;

    // ----------------------------------------------+-------------+-----------
    //                      Checksum type            |sumtype      |checksum
    //                                               |value        | size
    // ----------------------------------------------+-------------+-----------
    public static final int CKSUMTYPE_NULL          = 0;               // 0
    public static final int CKSUMTYPE_CRC32         = 1;               // 4
    public static final int CKSUMTYPE_RSA_MD4       = 2;               // 16
    public static final int CKSUMTYPE_RSA_MD4_DES   = 3;               // 24
    public static final int CKSUMTYPE_DES_MAC       = 4;               // 16
    public static final int CKSUMTYPE_DES_MAC_K     = 5;               // 8
    public static final int CKSUMTYPE_RSA_MD4_DES_K = 6;               // 16
    public static final int CKSUMTYPE_RSA_MD5       = 7;               // 16
    public static final int CKSUMTYPE_RSA_MD5_DES   = 8;               // 24

     // draft-ietf-krb-wg-crypto-07.txt
    public static final int CKSUMTYPE_HMAC_SHA1_DES3_KD = 12;          // 20

    // draft-raeburn-krb-rijndael-krb-07.txt
    public static final int CKSUMTYPE_HMAC_SHA1_96_AES128 = 15;        // 96
    public static final int CKSUMTYPE_HMAC_SHA1_96_AES256 = 16;        // 96

    // draft-brezak-win2k-krb-rc4-hmac-04.txt
    public static final int CKSUMTYPE_HMAC_MD5_ARCFOUR = -138;

    static int CKSUMTYPE_DEFAULT;
    static int SAFECKSUMTYPE_DEFAULT;

    private static boolean DEBUG = Krb5.DEBUG;
    static {
        initStatic();
    }

    public static void initStatic() {
        String temp = null;
        Config cfg = null;
        try {
            cfg = Config.getInstance();
            temp = cfg.get("libdefaults", "default_checksum");
            if (temp != null)
                {
                    CKSUMTYPE_DEFAULT = Config.getType(temp);
                } else {
                    /*
                     * If the default checksum is not
                     * specified in the configuration we
                     * set it to RSA_MD5. We follow the MIT and
                     * SEAM implementation.
                     */
                    CKSUMTYPE_DEFAULT = CKSUMTYPE_RSA_MD5;
                }
        } catch (Exception exc) {
            if (DEBUG) {
                System.out.println("Exception in getting default checksum "+
                                   "value from the configuration " +
                                   "Setting default checksum to be RSA-MD5");
                exc.printStackTrace();
            }
            CKSUMTYPE_DEFAULT = CKSUMTYPE_RSA_MD5;
        }


        try {
            temp = cfg.get("libdefaults", "safe_checksum_type");
            if (temp != null)
                {
                    SAFECKSUMTYPE_DEFAULT = Config.getType(temp);
                } else {
                    SAFECKSUMTYPE_DEFAULT = CKSUMTYPE_RSA_MD5_DES;
                }
        } catch (Exception exc) {
            if (DEBUG) {
                System.out.println("Exception in getting safe default " +
                                   "checksum value " +
                                   "from the configuration Setting  " +
                                   "safe default checksum to be RSA-MD5");
                exc.printStackTrace();
            }
            SAFECKSUMTYPE_DEFAULT = CKSUMTYPE_RSA_MD5_DES;
        }
    }

    /**
     * Constructs a new Checksum using the raw data and type.
     * @param data the byte array of checksum.
     * @param new_cksumType the type of checksum.
     *
     */
         // used in InitialToken
    public Checksum(byte[] data, int new_cksumType) {
        cksumType = new_cksumType;
        checksum = data;
    }

    /**
     * Constructs a new Checksum by calculating the checksum over the data
     * using specified checksum type.
     * @param new_cksumType the type of checksum.
     * @param data the data that needs to be performed a checksum calculation on.
     */
    public Checksum(int new_cksumType, byte[] data)
        throws KdcErrException, KrbCryptoException {

        cksumType = new_cksumType;
        CksumType cksumEngine = CksumType.getInstance(cksumType);
        if (!cksumEngine.isSafe()) {
            checksum = cksumEngine.calculateChecksum(data, data.length);
        } else {
            throw new KdcErrException(Krb5.KRB_AP_ERR_INAPP_CKSUM);
        }
    }

    /**
     * Constructs a new Checksum by calculating the keyed checksum
     * over the data using specified checksum type.
     * @param new_cksumType the type of checksum.
     * @param data the data that needs to be performed a checksum calculation on.
     */
         // KrbSafe, KrbTgsReq
    public Checksum(int new_cksumType, byte[] data,
                        EncryptionKey key, int usage)
        throws KdcErrException, KrbApErrException, KrbCryptoException {
        cksumType = new_cksumType;
        CksumType cksumEngine = CksumType.getInstance(cksumType);
        if (!cksumEngine.isSafe())
            throw new KrbApErrException(Krb5.KRB_AP_ERR_INAPP_CKSUM);
        checksum =
            cksumEngine.calculateKeyedChecksum(data,
                data.length,
                key.getBytes(),
                usage);
    }

    /**
     * Verifies the keyed checksum over the data passed in.
     */
    public boolean verifyKeyedChecksum(byte[] data, EncryptionKey key,
                                        int usage)
        throws KdcErrException, KrbApErrException, KrbCryptoException {
        CksumType cksumEngine = CksumType.getInstance(cksumType);
        if (!cksumEngine.isSafe())
            throw new KrbApErrException(Krb5.KRB_AP_ERR_INAPP_CKSUM);
        return cksumEngine.verifyKeyedChecksum(data,
                                               data.length,
                                               key.getBytes(),
                                               checksum,
            usage);
    }

    /*
    public Checksum(byte[] data) throws KdcErrException, KrbCryptoException {
        this(Checksum.CKSUMTYPE_DEFAULT, data);
    }
    */

    boolean isEqual(Checksum cksum) throws KdcErrException {
        if (cksumType != cksum.cksumType)
            return false;
        CksumType cksumEngine = CksumType.getInstance(cksumType);
        return CksumType.isChecksumEqual(checksum, cksum.checksum);
    }

    /**
     * Constructs an instance of Checksum from an ASN.1 encoded representation.
     * @param encoding a single DER-encoded value.
     * @exception Asn1Exception if an error occurs while decoding an ASN1
     * encoded data.
     * @exception IOException if an I/O error occurs while reading encoded data.
     *
     */
    private Checksum(DerValue encoding) throws Asn1Exception, IOException {
        DerValue der;
        if (encoding.getTag() != DerValue.tag_Sequence) {
            throw new Asn1Exception(Krb5.ASN1_BAD_ID);
        }
        der = encoding.getData().getDerValue();
        if ((der.getTag() & (byte)0x1F) == (byte)0x00) {
            cksumType = der.getData().getBigInteger().intValue();
        }
        else
            throw new Asn1Exception(Krb5.ASN1_BAD_ID);
        der = encoding.getData().getDerValue();
        if ((der.getTag() & (byte)0x1F) == (byte)0x01) {
            checksum = der.getData().getOctetString();
        }
        else
            throw new Asn1Exception(Krb5.ASN1_BAD_ID);
        if (encoding.getData().available() > 0) {
            throw new Asn1Exception(Krb5.ASN1_BAD_ID);
        }
    }

    /**
     * Encodes a Checksum object.
     * <pre>{@code
     * Checksum    ::= SEQUENCE {
     *         cksumtype   [0] Int32,
     *         checksum    [1] OCTET STRING
     * }
     * }</pre>
     *
     * <p>
     * This definition reflects the Network Working Group RFC 4120
     * specification available at
     * <a href="http://www.ietf.org/rfc/rfc4120.txt">
     * http://www.ietf.org/rfc/rfc4120.txt</a>.
     * @return byte array of enocded Checksum.
     * @exception Asn1Exception if an error occurs while decoding an
     * ASN1 encoded data.
     * @exception IOException if an I/O error occurs while reading
     * encoded data.
     *
     */
    public byte[] asn1Encode() throws Asn1Exception, IOException {
        DerOutputStream bytes = new DerOutputStream();
        DerOutputStream temp = new DerOutputStream();
        temp.putInteger(BigInteger.valueOf(cksumType));
        bytes.write(DerValue.createTag(DerValue.TAG_CONTEXT,
                                       true, (byte)0x00), temp);
        temp = new DerOutputStream();
        temp.putOctetString(checksum);
        bytes.write(DerValue.createTag(DerValue.TAG_CONTEXT,
                                       true, (byte)0x01), temp);
        temp = new DerOutputStream();
        temp.write(DerValue.tag_Sequence, bytes);
        return temp.toByteArray();
    }


    /**
     * Parse (unmarshal) a checksum object from a DER input stream.  This form
     * parsing might be used when expanding a value which is part of
     * a constructed sequence and uses explicitly tagged type.
     *
     * @exception Asn1Exception if an error occurs while decoding an
     * ASN1 encoded data.
     * @exception IOException if an I/O error occurs while reading
     * encoded data.
     * @param data the Der input stream value, which contains one or more
     * marshaled value.
     * @param explicitTag tag number.
     * @param optional indicates if this data field is optional
     * @return an instance of Checksum.
     *
     */
    public static Checksum parse(DerInputStream data,
                                 byte explicitTag, boolean optional)
        throws Asn1Exception, IOException {

        if ((optional) &&
            (((byte)data.peekByte() & (byte)0x1F) != explicitTag)) {
            return null;
        }
        DerValue der = data.getDerValue();
        if (explicitTag != (der.getTag() & (byte)0x1F))  {
            throw new Asn1Exception(Krb5.ASN1_BAD_ID);
        } else {
            DerValue subDer = der.getData().getDerValue();
            return new Checksum(subDer);
        }
    }

    /**
     * Returns the raw bytes of the checksum, not in ASN.1 encoded form.
     */
    public final byte[] getBytes() {
        return checksum;
    }

    public final int getType() {
        return cksumType;
    }

    @Override public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Checksum)) {
            return false;
        }

        try {
            return isEqual((Checksum)obj);
        } catch (KdcErrException kee) {
            return false;
        }
    }

    @Override public int hashCode() {
        int result = 17;
        result = 37 * result + cksumType;
        if (checksum != null) {
            result = 37 * result + Arrays.hashCode(checksum);
        }
        return result;
    }
}
