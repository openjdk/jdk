/*
 * Portions Copyright 2000-2010 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 *
 *  (C) Copyright IBM Corp. 1999 All Rights Reserved.
 *  Copyright 1997 The Open Group Research Institute.  All rights reserved.
 */

package sun.security.krb5;

import sun.security.util.*;
import sun.security.krb5.internal.*;
import sun.security.krb5.internal.crypto.*;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import sun.security.krb5.internal.ktab.KeyTab;
import sun.security.krb5.internal.ccache.CCacheOutputStream;
import javax.crypto.spec.DESKeySpec;
import javax.crypto.spec.DESedeKeySpec;

/**
 * This class encapsulates the concept of an EncryptionKey. An encryption
 * key is defined in RFC 4120 as:
 *
 * EncryptionKey   ::= SEQUENCE {
 *         keytype         [0] Int32 -- actually encryption type --,
 *         keyvalue        [1] OCTET STRING
 * }
 *
 * keytype
 *     This field specifies the encryption type of the encryption key
 *     that follows in the keyvalue field.  Although its name is
 *     "keytype", it actually specifies an encryption type.  Previously,
 *     multiple cryptosystems that performed encryption differently but
 *     were capable of using keys with the same characteristics were
 *     permitted to share an assigned number to designate the type of
 *     key; this usage is now deprecated.
 *
 * keyvalue
 *     This field contains the key itself, encoded as an octet string.
 */

public class EncryptionKey
    implements Cloneable {

    public static final EncryptionKey NULL_KEY =
        new EncryptionKey(new byte[] {}, EncryptedData.ETYPE_NULL, null);

    private int keyType;
    private byte[] keyValue;
    private Integer kvno; // not part of ASN1 encoding;

    private static final boolean DEBUG = Krb5.DEBUG;

    public synchronized int getEType() {
        return keyType;
    }

    public final Integer getKeyVersionNumber() {
        return kvno;
    }

    /**
     * Returns the raw key bytes, not in any ASN.1 encoding.
     */
    public final byte[] getBytes() {
        // This method cannot be called outside sun.security, hence no
        // cloning. getEncoded() calls this method.
        return keyValue;
    }

    public synchronized Object clone() {
        return new EncryptionKey(keyValue, keyType, kvno);
    }

    /**
     * Obtains the latest version of the secret key of
     * the principal from a keytab.
     *
     * @param princ the principal whose secret key is desired
     * @param keytab the path to the keytab file. A value of null
     * will be accepted to indicate that the default path should be
     * searched.
     * @returns the secret key or null if none was found.
     */
    /*
    // Replaced by acquireSecretKeys
    public static EncryptionKey acquireSecretKey(PrincipalName princ,
                                                 String keytab)
        throws KrbException, IOException {

        if (princ == null) {
            throw new IllegalArgumentException(
                "Cannot have null pricipal name to look in keytab.");
        }

        KeyTab ktab = KeyTab.getInstance(keytab);

        if (ktab == null)
            return null;

        return ktab.readServiceKey(princ);
    }
    */

    /**
     * Obtains all versions of the secret key of the principal from a
     * keytab.
     *
     * @Param princ the principal whose secret key is desired
     * @param keytab the path to the keytab file. A value of null
     * will be accepted to indicate that the default path should be
     * searched.
     * @returns an array of secret keys or null if none were found.
     */
    public static EncryptionKey[] acquireSecretKeys(PrincipalName princ,
                                                    String keytab)
        throws KrbException, IOException {

        if (princ == null)
            throw new IllegalArgumentException(
                "Cannot have null pricipal name to look in keytab.");

        // KeyTab getInstance(keytab) will call KeyTab.getInstance()
        // if keytab is null
        KeyTab ktab = KeyTab.getInstance(keytab);

        if (ktab == null) {
            return null;
        }

        return ktab.readServiceKeys(princ);
    }

    /**
     * Generate a list of keys using the given principal and password.
     * Construct a key for each configured etype.
     * Caller is responsible for clearing password.
     */
    /*
     * Usually, when keyType is decoded from ASN.1 it will contain a
     * value indicating what the algorithm to be used is. However, when
     * converting from a password to a key for the AS-EXCHANGE, this
     * keyType will not be available. Use builtin list of default etypes
     * as the default in that case. If default_tkt_enctypes was set in
     * the libdefaults of krb5.conf, then use that sequence.
     */
         // Used in Krb5LoginModule
    public static EncryptionKey[] acquireSecretKeys(char[] password,
        String salt) throws KrbException {
        return (acquireSecretKeys(password, salt, false, 0, null));
    }

    /**
     * Generates a list of keys using the given principal, password,
     * and the pre-authentication values.
     */
    public static EncryptionKey[] acquireSecretKeys(char[] password,
        String salt, boolean pa_exists, int pa_etype, byte[] pa_s2kparams)
        throws KrbException {

        int[] etypes = EType.getDefaults("default_tkt_enctypes");
        if (etypes == null) {
            etypes = EType.getBuiltInDefaults();
        }

        // set the preferred etype for preauth
        if ((pa_exists) && (pa_etype != EncryptedData.ETYPE_NULL)) {
            if (DEBUG) {
                System.out.println("Pre-Authentication: " +
                        "Set preferred etype = " + pa_etype);
            }
            if (EType.isSupported(pa_etype)) {
                // reset etypes to preferred value
                etypes = new int[1];
                etypes[0] = pa_etype;
            }
        }

        EncryptionKey[] encKeys = new EncryptionKey[etypes.length];
        for (int i = 0; i < etypes.length; i++) {
            if (EType.isSupported(etypes[i])) {
                encKeys[i] = new EncryptionKey(
                        stringToKey(password, salt, pa_s2kparams, etypes[i]),
                        etypes[i], null);
            } else {
                if (DEBUG) {
                    System.out.println("Encryption Type " +
                        EType.toString(etypes[i]) +
                        " is not supported/enabled");
                }
            }
        }
        return encKeys;
    }

    // Used in Krb5AcceptCredential, self
    public EncryptionKey(byte[] keyValue,
                         int keyType,
                         Integer kvno) {

        if (keyValue != null) {
            this.keyValue = new byte[keyValue.length];
            System.arraycopy(keyValue, 0, this.keyValue, 0, keyValue.length);
        } else {
            throw new IllegalArgumentException("EncryptionKey: " +
                                               "Key bytes cannot be null!");
        }
        this.keyType = keyType;
        this.kvno = kvno;
    }

    /**
     * Constructs an EncryptionKey by using the specified key type and key
     * value.  It is used to recover the key when retrieving data from
     * credential cache file.
     *
     */
     // Used in JSSE (KerberosWrapper), Credentials,
     // javax.security.auth.kerberos.KeyImpl
    public EncryptionKey(int keyType,
                         byte[] keyValue) {
        this(keyValue, keyType, null);
    }

    private static byte[] stringToKey(char[] password, String salt,
        byte[] s2kparams, int keyType) throws KrbCryptoException {

        char[] slt = salt.toCharArray();
        char[] pwsalt = new char[password.length + slt.length];
        System.arraycopy(password, 0, pwsalt, 0, password.length);
        System.arraycopy(slt, 0, pwsalt, password.length, slt.length);
        Arrays.fill(slt, '0');

        try {
            switch (keyType) {
                case EncryptedData.ETYPE_DES_CBC_CRC:
                case EncryptedData.ETYPE_DES_CBC_MD5:
                        return Des.string_to_key_bytes(pwsalt);

                case EncryptedData.ETYPE_DES3_CBC_HMAC_SHA1_KD:
                        return Des3.stringToKey(pwsalt);

                case EncryptedData.ETYPE_ARCFOUR_HMAC:
                        return ArcFourHmac.stringToKey(password);

                case EncryptedData.ETYPE_AES128_CTS_HMAC_SHA1_96:
                        return Aes128.stringToKey(password, salt, s2kparams);

                case EncryptedData.ETYPE_AES256_CTS_HMAC_SHA1_96:
                        return Aes256.stringToKey(password, salt, s2kparams);

                default:
                        throw new IllegalArgumentException("encryption type " +
                        EType.toString(keyType) + " not supported");
            }

        } catch (GeneralSecurityException e) {
            KrbCryptoException ke = new KrbCryptoException(e.getMessage());
            ke.initCause(e);
            throw ke;
        } finally {
            Arrays.fill(pwsalt, '0');
        }
    }

    // Used in javax.security.auth.kerberos.KeyImpl
    public EncryptionKey(char[] password,
                         String salt,
                         String algorithm) throws KrbCryptoException {

        if (algorithm == null || algorithm.equalsIgnoreCase("DES")) {
            keyType = EncryptedData.ETYPE_DES_CBC_MD5;
        } else if (algorithm.equalsIgnoreCase("DESede")) {
            keyType = EncryptedData.ETYPE_DES3_CBC_HMAC_SHA1_KD;
        } else if (algorithm.equalsIgnoreCase("AES128")) {
            keyType = EncryptedData.ETYPE_AES128_CTS_HMAC_SHA1_96;
        } else if (algorithm.equalsIgnoreCase("ArcFourHmac")) {
            keyType = EncryptedData.ETYPE_ARCFOUR_HMAC;
        } else if (algorithm.equalsIgnoreCase("AES256")) {
            keyType = EncryptedData.ETYPE_AES256_CTS_HMAC_SHA1_96;
            // validate if AES256 is enabled
            if (!EType.isSupported(keyType)) {
                throw new IllegalArgumentException("Algorithm " + algorithm +
                        " not enabled");
            }
        } else {
            throw new IllegalArgumentException("Algorithm " + algorithm +
                " not supported");
        }

        keyValue = stringToKey(password, salt, null, keyType);
        kvno = null;
    }

    /**
     * Generates a sub-sessionkey from a given session key.
     */
     // Used in KrbApRep, KrbApReq
    EncryptionKey(EncryptionKey key) throws KrbCryptoException {
        // generate random sub-session key
        keyValue = Confounder.bytes(key.keyValue.length);
        for (int i = 0; i < keyValue.length; i++) {
          keyValue[i] ^= key.keyValue[i];
        }
        keyType = key.keyType;

        // check for key parity and weak keys
        try {
            // check for DES key
            if ((keyType == EncryptedData.ETYPE_DES_CBC_MD5) ||
                (keyType == EncryptedData.ETYPE_DES_CBC_CRC)) {
                // fix DES key parity
                if (!DESKeySpec.isParityAdjusted(keyValue, 0)) {
                    keyValue = Des.set_parity(keyValue);
                }
                // check for weak key
                if (DESKeySpec.isWeak(keyValue, 0)) {
                    keyValue[7] = (byte)(keyValue[7] ^ 0xF0);
                }
            }
            // check for 3DES key
            if (keyType == EncryptedData.ETYPE_DES3_CBC_HMAC_SHA1_KD) {
                // fix 3DES key parity
                if (!DESedeKeySpec.isParityAdjusted(keyValue, 0)) {
                    keyValue = Des3.parityFix(keyValue);
                }
                // check for weak keys
                byte[] oneKey = new byte[8];
                for (int i=0; i<keyValue.length; i+=8) {
                    System.arraycopy(keyValue, i, oneKey, 0, 8);
                    if (DESKeySpec.isWeak(oneKey, 0)) {
                        keyValue[i+7] = (byte)(keyValue[i+7] ^ 0xF0);
                    }
                }
            }
        } catch (GeneralSecurityException e) {
            KrbCryptoException ke = new KrbCryptoException(e.getMessage());
            ke.initCause(e);
            throw ke;
        }
    }

    /**
     * Constructs an instance of EncryptionKey type.
     * @param encoding a single DER-encoded value.
     * @exception Asn1Exception if an error occurs while decoding an ASN1
     * encoded data.
     * @exception IOException if an I/O error occurs while reading encoded
     * data.
     *
     *
     */
         // Used in javax.security.auth.kerberos.KeyImpl
    public EncryptionKey(DerValue encoding) throws Asn1Exception, IOException {
        DerValue der;
        if (encoding.getTag() != DerValue.tag_Sequence) {
            throw new Asn1Exception(Krb5.ASN1_BAD_ID);
        }
        der = encoding.getData().getDerValue();
        if ((der.getTag() & (byte)0x1F) == (byte)0x00) {
            keyType = der.getData().getBigInteger().intValue();
        }
        else
            throw new Asn1Exception(Krb5.ASN1_BAD_ID);
        der = encoding.getData().getDerValue();
        if ((der.getTag() & (byte)0x1F) == (byte)0x01) {
            keyValue = der.getData().getOctetString();
        }
        else
            throw new Asn1Exception(Krb5.ASN1_BAD_ID);
        if (der.getData().available() > 0) {
            throw new Asn1Exception(Krb5.ASN1_BAD_ID);
        }
    }

    /**
     * Returns the ASN.1 encoding of this EncryptionKey.
     *
     * <xmp>
     * EncryptionKey ::=   SEQUENCE {
     *                             keytype[0]    INTEGER,
     *                             keyvalue[1]   OCTET STRING }
     * </xmp>
     *
     * <p>
     * This definition reflects the Network Working Group RFC 4120
     * specification available at
     * <a href="http://www.ietf.org/rfc/rfc4120.txt">
     * http://www.ietf.org/rfc/rfc4120.txt</a>.
     *
     * @return byte array of encoded EncryptionKey object.
     * @exception Asn1Exception if an error occurs while decoding an ASN1
     * encoded data.
     * @exception IOException if an I/O error occurs while reading encoded
     * data.
     *
     */
    public synchronized byte[] asn1Encode() throws Asn1Exception, IOException {
        DerOutputStream bytes = new DerOutputStream();
        DerOutputStream temp = new DerOutputStream();
        temp.putInteger(keyType);
        bytes.write(DerValue.createTag(DerValue.TAG_CONTEXT, true,
                                       (byte)0x00), temp);
        temp = new DerOutputStream();
        temp.putOctetString(keyValue);
        bytes.write(DerValue.createTag(DerValue.TAG_CONTEXT, true,
                                       (byte)0x01), temp);
        temp = new DerOutputStream();
        temp.write(DerValue.tag_Sequence, bytes);
        return temp.toByteArray();
    }

    public synchronized void destroy() {
        if (keyValue != null)
            for (int i = 0; i < keyValue.length; i++)
                keyValue[i] = 0;
    }


    /**
     * Parse (unmarshal) an Encryption key from a DER input stream.  This form
     * parsing might be used when expanding a value which is part of
     * a constructed sequence and uses explicitly tagged type.
     *
     * @param data the Der input stream value, which contains one or more
     * marshaled value.
     * @param explicitTag tag number.
     * @param optional indicate if this data field is optional
     * @exception Asn1Exception if an error occurs while decoding an ASN1
     * encoded data.
     * @exception IOException if an I/O error occurs while reading encoded
     * data.
     * @return an instance of EncryptionKey.
     *
     */
    public static EncryptionKey parse(DerInputStream data, byte
                                      explicitTag, boolean optional) throws
                                      Asn1Exception, IOException {
        if ((optional) && (((byte)data.peekByte() & (byte)0x1F) !=
                           explicitTag)) {
            return null;
        }
        DerValue der = data.getDerValue();
        if (explicitTag != (der.getTag() & (byte)0x1F))  {
            throw new Asn1Exception(Krb5.ASN1_BAD_ID);
        } else {
            DerValue subDer = der.getData().getDerValue();
            return new EncryptionKey(subDer);
        }
    }

    /**
     * Writes key value in FCC format to a <code>CCacheOutputStream</code>.
     *
     * @param cos a <code>CCacheOutputStream</code> to be written to.
     * @exception IOException if an I/O exception occurs.
     * @see sun.security.krb5.internal.ccache.CCacheOutputStream
     *
     */
    public synchronized void writeKey(CCacheOutputStream cos)
        throws IOException {

        cos.write16(keyType);
        // we use KRB5_FCC_FVNO_3
        cos.write16(keyType); // key type is recorded twice.
        cos.write32(keyValue.length);
        for (int i = 0; i < keyValue.length; i++) {
            cos.write8(keyValue[i]);
        }
    }

    public String toString() {
        return new String("EncryptionKey: keyType=" + keyType
                          + " kvno=" + kvno
                          + " keyValue (hex dump)="
                          + (keyValue == null || keyValue.length == 0 ?
                        " Empty Key" : '\n'
                        + Krb5.hexDumper.encodeBuffer(keyValue)
                        + '\n'));
    }

    /**
     * Find a key with given etype
     */
    public static EncryptionKey findKey(int etype, EncryptionKey[] keys)
            throws KrbException {
        return findKey(etype, null, keys);
    }

    /**
     * Determines if a kvno matches another kvno. Used in the method
     * findKey(type, kvno, keys). Always returns true if either input
     * is null or zero, in case any side does not have kvno info available.
     *
     * Note: zero is included because N/A is not a legal value for kvno
     * in javax.security.auth.kerberos.KerberosKey. Therefore, the info
     * that the kvno is N/A might be lost when converting between this
     * class and KerberosKey.
     */
    private static boolean versionMatches(Integer v1, Integer v2) {
        if (v1 == null || v1 == 0 || v2 == null || v2 == 0) {
            return true;
        }
        return v1.equals(v2);
    }

    /**
     * Find a key with given etype and kvno
     * @param kvno if null, return any (first?) key
     */
    public static EncryptionKey findKey(int etype, Integer kvno, EncryptionKey[] keys)
        throws KrbException {

        // check if encryption type is supported
        if (!EType.isSupported(etype)) {
            throw new KrbException("Encryption type " +
                EType.toString(etype) + " is not supported/enabled");
        }

        int ktype;
        boolean etypeFound = false;
        for (int i = 0; i < keys.length; i++) {
            ktype = keys[i].getEType();
            if (EType.isSupported(ktype)) {
                Integer kv = keys[i].getKeyVersionNumber();
                if (etype == ktype) {
                    etypeFound = true;
                    if (versionMatches(kvno, kv)) {
                        return keys[i];
                    }
                }
            }
        }

        // Key not found.
        // allow DES key to be used for the DES etypes
        if ((etype == EncryptedData.ETYPE_DES_CBC_CRC ||
            etype == EncryptedData.ETYPE_DES_CBC_MD5)) {
            for (int i = 0; i < keys.length; i++) {
                ktype = keys[i].getEType();
                if (ktype == EncryptedData.ETYPE_DES_CBC_CRC ||
                        ktype == EncryptedData.ETYPE_DES_CBC_MD5) {
                    Integer kv = keys[i].getKeyVersionNumber();
                    etypeFound = true;
                    if (versionMatches(kvno, kv)) {
                        return new EncryptionKey(etype, keys[i].getBytes());
                    }
                }
            }
        }
        if (etypeFound) {
            throw new KrbException(Krb5.KRB_AP_ERR_BADKEYVER);
        }
        return null;
    }
}
