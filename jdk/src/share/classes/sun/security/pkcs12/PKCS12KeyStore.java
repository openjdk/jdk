/*
 * Copyright 1999-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.pkcs12;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.KeyStoreSpi;
import java.security.KeyStoreException;
import java.security.UnrecoverableKeyException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import java.math.*;

import java.security.AlgorithmParameters;
import java.security.spec.AlgorithmParameterSpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.SecretKeyFactory;
import javax.crypto.SecretKey;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.security.auth.x500.X500Principal;

import sun.security.util.DerInputStream;
import sun.security.util.DerOutputStream;
import sun.security.util.DerValue;
import sun.security.util.ObjectIdentifier;
import sun.security.pkcs.ContentInfo;
import sun.security.x509.AlgorithmId;
import sun.security.pkcs.EncryptedPrivateKeyInfo;


/**
 * This class provides the keystore implementation referred to as "PKCS12".
 * Implements the PKCS#12 PFX protected using the Password privacy mode.
 * The contents are protected using Password integrity mode.
 *
 * Currently we support following PBE algorithms:
 *  - pbeWithSHAAnd3KeyTripleDESCBC to encrypt private keys
 *  - pbeWithSHAAnd40BitRC2CBC to encrypt certificates
 *
 * Supported encryption of various implementations :
 *
 * Software and mode.     Certificate encryption  Private key encryption
 * ---------------------------------------------------------------------
 * MSIE4 (domestic            40 bit RC2.            40 bit RC2
 * and xport versions)
 * PKCS#12 export.
 *
 * MSIE4, 5 (domestic         40 bit RC2,            40 bit RC2,
 * and export versions)       3 key triple DES       3 key triple DES
 * PKCS#12 import.
 *
 * MSIE5                      40 bit RC2             3 key triple DES,
 * PKCS#12 export.                                   with SHA1 (168 bits)
 *
 * Netscape Communicator      40 bit RC2             3 key triple DES,
 * (domestic and export                              with SHA1 (168 bits)
 * versions) PKCS#12 export
 *
 * Netscape Communicator      40 bit ciphers only    All.
 * (export version)
 * PKCS#12 import.
 *
 * Netscape Communicator      All.                   All.
 * (domestic or fortified
 * version) PKCS#12 import.
 *
 * OpenSSL PKCS#12 code.      All.                   All.
 * ---------------------------------------------------------------------
 *
 * NOTE: Currently PKCS12 KeyStore does not support TrustedCertEntries.
 * PKCS#12 is mainly used to deliver private keys with their associated
 * certificate chain and aliases. In a PKCS12 keystore, entries are
 * identified by the alias, and a localKeyId is required to match the
 * private key with the certificate.
 *
 * @author Seema Malkani
 * @author Jeff Nisewanger
 * @author Jan Luehe
 *
 * @see KeyProtector
 * @see java.security.KeyStoreSpi
 * @see KeyTool
 *
 *
 */
public final class PKCS12KeyStore extends KeyStoreSpi {

    public static final int VERSION_3 = 3;

    private static final int keyBag[]  = {1, 2, 840, 113549, 1, 12, 10, 1, 2};
    private static final int certBag[] = {1, 2, 840, 113549, 1, 12, 10, 1, 3};

    private static final int pkcs9Name[]  = {1, 2, 840, 113549, 1, 9, 20};
    private static final int pkcs9KeyId[] = {1, 2, 840, 113549, 1, 9, 21};

    private static final int pkcs9certType[] = {1, 2, 840, 113549, 1, 9, 22, 1};

    private static final int pbeWithSHAAnd40BitRC2CBC[] =
                                        {1, 2, 840, 113549, 1, 12, 1, 6};
    private static final int pbeWithSHAAnd3KeyTripleDESCBC[] =
                                        {1, 2, 840, 113549, 1, 12, 1, 3};

    private static ObjectIdentifier PKCS8ShroudedKeyBag_OID;
    private static ObjectIdentifier CertBag_OID;
    private static ObjectIdentifier PKCS9FriendlyName_OID;
    private static ObjectIdentifier PKCS9LocalKeyId_OID;
    private static ObjectIdentifier PKCS9CertType_OID;
    private static ObjectIdentifier pbeWithSHAAnd40BitRC2CBC_OID;
    private static ObjectIdentifier pbeWithSHAAnd3KeyTripleDESCBC_OID;

    private int counter = 0;
    private static final int iterationCount = 1024;
    private static final int SALT_LEN = 20;

    // private key count
    // Note: This is a workaround to allow null localKeyID attribute
    // in pkcs12 with one private key entry and associated cert-chain
    private int privateKeyCount = 0;

    // the source of randomness
    private SecureRandom random;

    static {
        try {
            PKCS8ShroudedKeyBag_OID = new ObjectIdentifier(keyBag);
            CertBag_OID = new ObjectIdentifier(certBag);
            PKCS9FriendlyName_OID = new ObjectIdentifier(pkcs9Name);
            PKCS9LocalKeyId_OID = new ObjectIdentifier(pkcs9KeyId);
            PKCS9CertType_OID = new ObjectIdentifier(pkcs9certType);
            pbeWithSHAAnd40BitRC2CBC_OID =
                        new ObjectIdentifier(pbeWithSHAAnd40BitRC2CBC);
            pbeWithSHAAnd3KeyTripleDESCBC_OID =
                        new ObjectIdentifier(pbeWithSHAAnd3KeyTripleDESCBC);
        } catch (IOException ioe) {
            // should not happen
        }
    }

    // Private keys and their supporting certificate chains
    private static class KeyEntry {
        Date date; // the creation date of this entry
        byte[] protectedPrivKey;
        Certificate chain[];
        byte[] keyId;
        String alias;
    };

    private static class KeyId {
        byte[] keyId;

        KeyId(byte[] keyId) {
            this.keyId = keyId;
        }
        public int hashCode() {
            int hash = 0;

            for (int i = 0; i < keyId.length; i++)
                hash += keyId[i];
            return hash;
        }
        public boolean equals(Object obj) {
            if (!(obj instanceof KeyId))
                return false;
            KeyId that = (KeyId)obj;
            return (Arrays.equals(this.keyId, that.keyId));
        }
    }

    /**
     * Private keys and certificates are stored in a hashtable.
     * Hash entries are keyed by alias names.
     */
    private Hashtable<String, KeyEntry> entries =
                                new Hashtable<String, KeyEntry>();

    private ArrayList<KeyEntry> keyList = new ArrayList<KeyEntry>();
    private LinkedHashMap<Object, X509Certificate> certs = new LinkedHashMap<Object, X509Certificate>();

    /**
     * Returns the key associated with the given alias, using the given
     * password to recover it.
     *
     * @param alias the alias name
     * @param password the password for recovering the key
     *
     * @return the requested key, or null if the given alias does not exist
     * or does not identify a <i>key entry</i>.
     *
     * @exception NoSuchAlgorithmException if the algorithm for recovering the
     * key cannot be found
     * @exception UnrecoverableKeyException if the key cannot be recovered
     * (e.g., the given password is wrong).
     */
    public Key engineGetKey(String alias, char[] password)
        throws NoSuchAlgorithmException, UnrecoverableKeyException
    {
        KeyEntry entry = entries.get(alias.toLowerCase());
        Key key = null;

        if (entry == null) {
            return null;
        }

        // get the encoded private key
        byte[] encrBytes = entry.protectedPrivKey;

        byte[] encryptedKey;
        AlgorithmParameters algParams;
        ObjectIdentifier algOid;
        try {
            // get the encrypted private key
            EncryptedPrivateKeyInfo encrInfo =
                        new EncryptedPrivateKeyInfo(encrBytes);
            encryptedKey = encrInfo.getEncryptedData();

            // parse Algorithm parameters
            DerValue val = new DerValue(encrInfo.getAlgorithm().encode());
            DerInputStream in = val.toDerInputStream();
            algOid = in.getOID();
            algParams = parseAlgParameters(in);

        } catch (IOException ioe) {
            UnrecoverableKeyException uke =
                new UnrecoverableKeyException("Private key not stored as "
                                 + "PKCS#8 EncryptedPrivateKeyInfo: " + ioe);
            uke.initCause(ioe);
            throw uke;
        }

        try {
            // Use JCE
            SecretKey skey = getPBEKey(password);
            Cipher cipher = Cipher.getInstance(algOid.toString());
            cipher.init(Cipher.DECRYPT_MODE, skey, algParams);
            byte[] privateKeyInfo = cipher.doFinal(encryptedKey);

            PKCS8EncodedKeySpec kspec = new PKCS8EncodedKeySpec(privateKeyInfo);

            /*
             * Parse the key algorithm and then use a JCA key factory
             * to create the private key.
             */
            DerValue val = new DerValue(privateKeyInfo);
            DerInputStream in = val.toDerInputStream();
            int i = in.getInteger();
            DerValue[] value = in.getSequence(2);
            AlgorithmId algId = new AlgorithmId(value[0].getOID());
            String algName = algId.getName();

            KeyFactory kfac = KeyFactory.getInstance(algName);
            key =  kfac.generatePrivate(kspec);
        } catch (Exception e) {
            UnrecoverableKeyException uke =
                new UnrecoverableKeyException("Get Key failed: " +
                                        e.getMessage());
            uke.initCause(e);
            throw uke;
        }
        return key;
    }

    /**
     * Returns the certificate chain associated with the given alias.
     *
     * @param alias the alias name
     *
     * @return the certificate chain (ordered with the user's certificate first
     * and the root certificate authority last), or null if the given alias
     * does not exist or does not contain a certificate chain (i.e., the given
     * alias identifies either a <i>trusted certificate entry</i> or a
     * <i>key entry</i> without a certificate chain).
     */
    public Certificate[] engineGetCertificateChain(String alias) {
        KeyEntry entry = entries.get(alias.toLowerCase());
        if (entry != null) {
            if (entry.chain == null) {
                return null;
            } else {
                return entry.chain.clone();
            }
        } else {
            return null;
        }
    }

    /**
     * Returns the certificate associated with the given alias.
     *
     * <p>If the given alias name identifies a
     * <i>trusted certificate entry</i>, the certificate associated with that
     * entry is returned. If the given alias name identifies a
     * <i>key entry</i>, the first element of the certificate chain of that
     * entry is returned, or null if that entry does not have a certificate
     * chain.
     *
     * @param alias the alias name
     *
     * @return the certificate, or null if the given alias does not exist or
     * does not contain a certificate.
     */
    public Certificate engineGetCertificate(String alias) {
        KeyEntry entry = entries.get(alias.toLowerCase());
        if (entry != null) {
            if (entry.chain == null) {
                return null;
            } else {
                return entry.chain[0];
            }
        } else {
            return null;
        }
    }

    /**
     * Returns the creation date of the entry identified by the given alias.
     *
     * @param alias the alias name
     *
     * @return the creation date of this entry, or null if the given alias does
     * not exist
     */
    public Date engineGetCreationDate(String alias) {
        KeyEntry entry = entries.get(alias.toLowerCase());
        if (entry != null) {
            return new Date(entry.date.getTime());
        } else {
            return null;
        }
    }

    /**
     * Assigns the given key to the given alias, protecting it with the given
     * password.
     *
     * <p>If the given key is of type <code>java.security.PrivateKey</code>,
     * it must be accompanied by a certificate chain certifying the
     * corresponding public key.
     *
     * <p>If the given alias already exists, the keystore information
     * associated with it is overridden by the given key (and possibly
     * certificate chain).
     *
     * @param alias the alias name
     * @param key the key to be associated with the alias
     * @param password the password to protect the key
     * @param chain the certificate chain for the corresponding public
     * key (only required if the given key is of type
     * <code>java.security.PrivateKey</code>).
     *
     * @exception KeyStoreException if the given key cannot be protected, or
     * this operation fails for some other reason
     */
    public synchronized void engineSetKeyEntry(String alias, Key key,
                        char[] password, Certificate[] chain)
        throws KeyStoreException
    {
        try {
            KeyEntry entry = new KeyEntry();
            entry.date = new Date();

            if (key instanceof PrivateKey) {
                if ((key.getFormat().equals("PKCS#8")) ||
                    (key.getFormat().equals("PKCS8"))) {
                    // Encrypt the private key
                    entry.protectedPrivKey =
                        encryptPrivateKey(key.getEncoded(), password);
                } else {
                    throw new KeyStoreException("Private key is not encoded" +
                                "as PKCS#8");
                }
            } else {
                throw new KeyStoreException("Key is not a PrivateKey");
            }

            // clone the chain
            if (chain != null) {
                // validate cert-chain
                if ((chain.length > 1) && (!validateChain(chain)))
                   throw new KeyStoreException("Certificate chain is " +
                                                "not validate");
                entry.chain = chain.clone();
            }

            // set the keyId to current date
            entry.keyId = ("Time " + (entry.date).getTime()).getBytes("UTF8");
            // set the alias
            entry.alias = alias.toLowerCase();

            // add the entry
            entries.put(alias.toLowerCase(), entry);
        } catch (Exception nsae) {
            KeyStoreException ke = new KeyStoreException("Key protection " +
                                        " algorithm not found: " + nsae);
            ke.initCause(nsae);
            throw ke;
        }
    }

    /**
     * Assigns the given key (that has already been protected) to the given
     * alias.
     *
     * <p>If the protected key is of type
     * <code>java.security.PrivateKey</code>, it must be accompanied by a
     * certificate chain certifying the corresponding public key. If the
     * underlying keystore implementation is of type <code>jks</code>,
     * <code>key</code> must be encoded as an
     * <code>EncryptedPrivateKeyInfo</code> as defined in the PKCS #8 standard.
     *
     * <p>If the given alias already exists, the keystore information
     * associated with it is overridden by the given key (and possibly
     * certificate chain).
     *
     * @param alias the alias name
     * @param key the key (in protected format) to be associated with the alias
     * @param chain the certificate chain for the corresponding public
     * key (only useful if the protected key is of type
     * <code>java.security.PrivateKey</code>).
     *
     * @exception KeyStoreException if this operation fails.
     */
    public synchronized void engineSetKeyEntry(String alias, byte[] key,
                                  Certificate[] chain)
        throws KeyStoreException
    {
        // key must be encoded as EncryptedPrivateKeyInfo
        // as defined in PKCS#8
        try {
            new EncryptedPrivateKeyInfo(key);
        } catch (IOException ioe) {
            KeyStoreException ke = new KeyStoreException("Private key is not"
                        + " stored as PKCS#8 EncryptedPrivateKeyInfo: " + ioe);
            ke.initCause(ioe);
            throw ke;
        }

        KeyEntry entry = new KeyEntry();
        entry.date = new Date();

        entry.protectedPrivKey = key.clone();
        if (chain != null) {
           entry.chain = chain.clone();
        }

        // add the entry
        entries.put(alias.toLowerCase(), entry);
    }


    /*
     * Generate random salt
     */
    private byte[] getSalt()
    {
        // Generate a random salt.
        byte[] salt = new byte[SALT_LEN];
        if (random == null) {
           random = new SecureRandom();
        }
        random.nextBytes(salt);
        return salt;
    }

    /*
     * Generate PBE Algorithm Parameters
     */
    private AlgorithmParameters getAlgorithmParameters(String algorithm)
        throws IOException
    {
        AlgorithmParameters algParams = null;

        // create PBE parameters from salt and iteration count
        PBEParameterSpec paramSpec =
                new PBEParameterSpec(getSalt(), iterationCount);
        try {
           algParams = AlgorithmParameters.getInstance(algorithm);
           algParams.init(paramSpec);
        } catch (Exception e) {
           IOException ioe =
                new IOException("getAlgorithmParameters failed: " +
                                e.getMessage());
           ioe.initCause(e);
           throw ioe;
        }
        return algParams;
    }

    /*
     * parse Algorithm Parameters
     */
    private AlgorithmParameters parseAlgParameters(DerInputStream in)
        throws IOException
    {
        AlgorithmParameters algParams = null;
        try {
            DerValue params;
            if (in.available() == 0) {
                params = null;
            } else {
                params = in.getDerValue();
                if (params.tag == DerValue.tag_Null) {
                   params = null;
                }
            }
            if (params != null) {
                algParams = AlgorithmParameters.getInstance("PBE");
                algParams.init(params.toByteArray());
            }
        } catch (Exception e) {
           IOException ioe =
                new IOException("parseAlgParameters failed: " +
                                e.getMessage());
           ioe.initCause(e);
           throw ioe;
        }
        return algParams;
    }

    /*
     * Generate PBE key
     */
    private SecretKey getPBEKey(char[] password) throws IOException
    {
        SecretKey skey = null;

        try {
            PBEKeySpec keySpec = new PBEKeySpec(password);
            SecretKeyFactory skFac = SecretKeyFactory.getInstance("PBE");
            skey = skFac.generateSecret(keySpec);
        } catch (Exception e) {
           IOException ioe = new IOException("getSecretKey failed: " +
                                        e.getMessage());
           ioe.initCause(e);
           throw ioe;
        }
        return skey;
    }

    /*
     * Encrypt private key using Password-based encryption (PBE)
     * as defined in PKCS#5.
     *
     * NOTE: Currently pbeWithSHAAnd3-KeyTripleDES-CBC algorithmID is
     *       used to derive the key and IV.
     *
     * @return encrypted private key encoded as EncryptedPrivateKeyInfo
     */
    private byte[] encryptPrivateKey(byte[] data, char[] password)
        throws IOException, NoSuchAlgorithmException, UnrecoverableKeyException
    {
        byte[] key = null;

        try {
            // create AlgorithmParameters
            AlgorithmParameters algParams =
                getAlgorithmParameters("PBEWithSHA1AndDESede");

            // Use JCE
            SecretKey skey = getPBEKey(password);
            Cipher cipher = Cipher.getInstance("PBEWithSHA1AndDESede");
            cipher.init(Cipher.ENCRYPT_MODE, skey, algParams);
            byte[] encryptedKey = cipher.doFinal(data);

            // wrap encrypted private key in EncryptedPrivateKeyInfo
            // as defined in PKCS#8
            AlgorithmId algid =
                new AlgorithmId(pbeWithSHAAnd3KeyTripleDESCBC_OID, algParams);
            EncryptedPrivateKeyInfo encrInfo =
                new EncryptedPrivateKeyInfo(algid, encryptedKey);
            key = encrInfo.getEncoded();
        } catch (Exception e) {
            UnrecoverableKeyException uke =
                new UnrecoverableKeyException("Encrypt Private Key failed: "
                                                + e.getMessage());
            uke.initCause(e);
            throw uke;
        }

        return key;
    }

    /**
     * Assigns the given certificate to the given alias.
     *
     * <p>If the given alias already exists in this keystore and identifies a
     * <i>trusted certificate entry</i>, the certificate associated with it is
     * overridden by the given certificate.
     *
     * @param alias the alias name
     * @param cert the certificate
     *
     * @exception KeyStoreException if the given alias already exists and does
     * identify a <i>key entry</i>, or on an attempt to create a
     * <i>trusted cert entry</i> which is currently not supported.
     */
    public synchronized void engineSetCertificateEntry(String alias,
        Certificate cert) throws KeyStoreException
    {
        KeyEntry entry = entries.get(alias.toLowerCase());
        if (entry != null) {
            throw new KeyStoreException("Cannot overwrite own certificate");
        } else
            throw new KeyStoreException("TrustedCertEntry not supported");
    }

    /**
     * Deletes the entry identified by the given alias from this keystore.
     *
     * @param alias the alias name
     *
     * @exception KeyStoreException if the entry cannot be removed.
     */
    public synchronized void engineDeleteEntry(String alias)
        throws KeyStoreException
    {
        entries.remove(alias.toLowerCase());
    }

    /**
     * Lists all the alias names of this keystore.
     *
     * @return enumeration of the alias names
     */
    public Enumeration<String> engineAliases() {
        return entries.keys();
    }

    /**
     * Checks if the given alias exists in this keystore.
     *
     * @param alias the alias name
     *
     * @return true if the alias exists, false otherwise
     */
    public boolean engineContainsAlias(String alias) {
        return entries.containsKey(alias.toLowerCase());
    }

    /**
     * Retrieves the number of entries in this keystore.
     *
     * @return the number of entries in this keystore
     */
    public int engineSize() {
        return entries.size();
    }

    /**
     * Returns true if the entry identified by the given alias is a
     * <i>key entry</i>, and false otherwise.
     *
     * @return true if the entry identified by the given alias is a
     * <i>key entry</i>, false otherwise.
     */
    public boolean engineIsKeyEntry(String alias) {
        KeyEntry entry = entries.get(alias.toLowerCase());
        if (entry != null) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns true if the entry identified by the given alias is a
     * <i>trusted certificate entry</i>, and false otherwise.
     *
     * @return true if the entry identified by the given alias is a
     * <i>trusted certificate entry</i>, false otherwise.
     */
    public boolean engineIsCertificateEntry(String alias) {
        // TrustedCertEntry is not supported
        return false;
    }

    /**
     * Returns the (alias) name of the first keystore entry whose certificate
     * matches the given certificate.
     *
     * <p>This method attempts to match the given certificate with each
     * keystore entry. If the entry being considered
     * is a <i>trusted certificate entry</i>, the given certificate is
     * compared to that entry's certificate. If the entry being considered is
     * a <i>key entry</i>, the given certificate is compared to the first
     * element of that entry's certificate chain (if a chain exists).
     *
     * @param cert the certificate to match with.
     *
     * @return the (alias) name of the first entry with matching certificate,
     * or null if no such entry exists in this keystore.
     */
    public String engineGetCertificateAlias(Certificate cert) {
        Certificate certElem = null;

        for (Enumeration<String> e = entries.keys(); e.hasMoreElements(); ) {
            String alias = e.nextElement();
            KeyEntry entry = entries.get(alias);
            if (entry.chain != null) {
                certElem = entry.chain[0];
            }
            if (certElem.equals(cert)) {
                return alias;
            }
        }
        return null;
    }

    /**
     * Stores this keystore to the given output stream, and protects its
     * integrity with the given password.
     *
     * @param stream the output stream to which this keystore is written.
     * @param password the password to generate the keystore integrity check
     *
     * @exception IOException if there was an I/O problem with data
     * @exception NoSuchAlgorithmException if the appropriate data integrity
     * algorithm could not be found
     * @exception CertificateException if any of the certificates included in
     * the keystore data could not be stored
     */
    public synchronized void engineStore(OutputStream stream, char[] password)
        throws IOException, NoSuchAlgorithmException, CertificateException
    {
        // password is mandatory when storing
        if (password == null) {
           throw new IllegalArgumentException("password can't be null");
        }

        // -- Create PFX
        DerOutputStream pfx = new DerOutputStream();

        // PFX version (always write the latest version)
        DerOutputStream version = new DerOutputStream();
        version.putInteger(VERSION_3);
        byte[] pfxVersion = version.toByteArray();
        pfx.write(pfxVersion);

        // -- Create AuthSafe
        DerOutputStream authSafe = new DerOutputStream();

        // -- Create ContentInfos
        DerOutputStream authSafeContentInfo = new DerOutputStream();

        // -- create safeContent Data ContentInfo
        byte[] safeContentData = createSafeContent();
        ContentInfo dataContentInfo = new ContentInfo(safeContentData);
        dataContentInfo.encode(authSafeContentInfo);

        // -- create EncryptedContentInfo
        byte[] encrData = createEncryptedData(password);
        ContentInfo encrContentInfo =
                new ContentInfo(ContentInfo.ENCRYPTED_DATA_OID,
                                new DerValue(encrData));
        encrContentInfo.encode(authSafeContentInfo);

        // wrap as SequenceOf ContentInfos
        DerOutputStream cInfo = new DerOutputStream();
        cInfo.write(DerValue.tag_SequenceOf, authSafeContentInfo);
        byte[] authenticatedSafe = cInfo.toByteArray();

        // Create Encapsulated ContentInfo
        ContentInfo contentInfo = new ContentInfo(authenticatedSafe);
        contentInfo.encode(authSafe);
        byte[] authSafeData = authSafe.toByteArray();
        pfx.write(authSafeData);

        // -- MAC
        byte[] macData = calculateMac(password, authenticatedSafe);
        pfx.write(macData);

        // write PFX to output stream
        DerOutputStream pfxout = new DerOutputStream();
        pfxout.write(DerValue.tag_Sequence, pfx);
        byte[] pfxData = pfxout.toByteArray();
        stream.write(pfxData);
        stream.flush();
    }


    /*
     * Generate Hash.
     */
    private byte[] generateHash(byte[] data) throws IOException
    {
        byte[] digest = null;

        try {
            MessageDigest md = MessageDigest.getInstance("SHA1");
            md.update(data);
            digest = md.digest();
        } catch (Exception e) {
            IOException ioe = new IOException("generateHash failed: " + e);
            ioe.initCause(e);
            throw ioe;
        }
        return digest;
    }


    /*
     * Calculate MAC using HMAC algorithm (required for password integrity)
     *
     * Hash-based MAC algorithm combines secret key with message digest to
     * create a message authentication code (MAC)
     */
    private byte[] calculateMac(char[] passwd, byte[] data)
        throws IOException
    {
        byte[] mData = null;
        String algName = "SHA1";

        try {
            // Generate a random salt.
            byte[] salt = getSalt();

            // generate MAC (MAC key is generated within JCE)
            Mac m = Mac.getInstance("HmacPBESHA1");
            PBEParameterSpec params =
                        new PBEParameterSpec(salt, iterationCount);
            SecretKey key = getPBEKey(passwd);
            m.init(key, params);
            m.update(data);
            byte[] macResult = m.doFinal();

            // encode as MacData
            MacData macData = new MacData(algName, macResult, salt,
                                                iterationCount);
            DerOutputStream bytes = new DerOutputStream();
            bytes.write(macData.getEncoded());
            mData = bytes.toByteArray();
        } catch (Exception e) {
            IOException ioe = new IOException("calculateMac failed: " + e);
            ioe.initCause(e);
            throw ioe;
        }
        return mData;
    }


    /*
     * Validate Certificate Chain
     */
    private boolean validateChain(Certificate[] certChain)
    {
        for (int i = 0; i < certChain.length-1; i++) {
            X500Principal issuerDN =
                ((X509Certificate)certChain[i]).getIssuerX500Principal();
            X500Principal subjectDN =
                ((X509Certificate)certChain[i+1]).getSubjectX500Principal();
            if (!(issuerDN.equals(subjectDN)))
                return false;
        }
        return true;
    }


    /*
     * Create PKCS#12 Attributes, friendlyName and localKeyId.
     *
     * Although attributes are optional, they could be required.
     * For e.g. localKeyId attribute is required to match the
     * private key with the associated end-entity certificate.
     *
     * PKCS8ShroudedKeyBags include unique localKeyID and friendlyName.
     * CertBags may or may not include attributes depending on the type
     * of Certificate. In end-entity certificates, localKeyID should be
     * unique, and the corresponding private key should have the same
     * localKeyID. For trusted CA certs in the cert-chain, localKeyID
     * attribute is not required, hence most vendors don't include it.
     * NSS/Netscape require it to be unique or null, where as IE/OpenSSL
     * ignore it.
     *
     * Here is a list of pkcs12 attribute values in CertBags.
     *
     * PKCS12 Attribute       NSS/Netscape    IE     OpenSSL    J2SE
     * --------------------------------------------------------------
     * LocalKeyId
     * (In EE cert only,
     *  NULL in CA certs)      true          true     true      true
     *
     * friendlyName            unique        same/    same/     unique
     *                                       unique   unique/
     *                                                null
     *
     * Note: OpenSSL adds friendlyName for end-entity cert only, and
     * removes the localKeyID and friendlyName for CA certs.
     * If the CertBag did not have a friendlyName, most vendors will
     * add it, and assign it to the DN of the cert.
     */
    private byte[] getBagAttributes(String alias, byte[] keyId)
        throws IOException {

        byte[] localKeyID = null;
        byte[] friendlyName = null;

        // return null if both attributes are null
        if ((alias == null) && (keyId == null)) {
            return null;
        }

        // SafeBag Attributes
        DerOutputStream bagAttrs = new DerOutputStream();

        // Encode the friendlyname oid.
        if (alias != null) {
            DerOutputStream bagAttr1 = new DerOutputStream();
            bagAttr1.putOID(PKCS9FriendlyName_OID);
            DerOutputStream bagAttrContent1 = new DerOutputStream();
            DerOutputStream bagAttrValue1 = new DerOutputStream();
            bagAttrContent1.putBMPString(alias);
            bagAttr1.write(DerValue.tag_Set, bagAttrContent1);
            bagAttrValue1.write(DerValue.tag_Sequence, bagAttr1);
            friendlyName = bagAttrValue1.toByteArray();
        }

        // Encode the localkeyId oid.
        if (keyId != null) {
            DerOutputStream bagAttr2 = new DerOutputStream();
            bagAttr2.putOID(PKCS9LocalKeyId_OID);
            DerOutputStream bagAttrContent2 = new DerOutputStream();
            DerOutputStream bagAttrValue2 = new DerOutputStream();
            bagAttrContent2.putOctetString(keyId);
            bagAttr2.write(DerValue.tag_Set, bagAttrContent2);
            bagAttrValue2.write(DerValue.tag_Sequence, bagAttr2);
            localKeyID = bagAttrValue2.toByteArray();
        }

        DerOutputStream attrs = new DerOutputStream();
        if (friendlyName != null) {
            attrs.write(friendlyName);
        }
        if (localKeyID != null) {
            attrs.write(localKeyID);
        }
        bagAttrs.write(DerValue.tag_Set, attrs);
        return bagAttrs.toByteArray();
    }


    /*
     * Create EncryptedData content type, that contains EncryptedContentInfo.
     * Includes certificates in individual SafeBags of type CertBag.
     * Each CertBag may include pkcs12 attributes
     * (see comments in getBagAttributes)
     */
    private byte[] createEncryptedData(char[] password)
        throws CertificateException, IOException
    {
        DerOutputStream out = new DerOutputStream();
        for (Enumeration<String> e = entries.keys(); e.hasMoreElements(); ) {

            String alias = e.nextElement();
            KeyEntry entry = entries.get(alias);

            // certificate chain
            int chainLen;
            if (entry.chain == null) {
                chainLen = 0;
            } else {
                chainLen = entry.chain.length;
            }

            for (int i = 0; i < chainLen; i++) {
                // create SafeBag of Type CertBag
                DerOutputStream safeBag = new DerOutputStream();
                safeBag.putOID(CertBag_OID);

                // create a CertBag
                DerOutputStream certBag = new DerOutputStream();
                certBag.putOID(PKCS9CertType_OID);

                // write encoded certs in a context-specific tag
                DerOutputStream certValue = new DerOutputStream();
                X509Certificate cert = (X509Certificate)entry.chain[i];
                certValue.putOctetString(cert.getEncoded());
                certBag.write(DerValue.createTag(DerValue.TAG_CONTEXT,
                                        true, (byte) 0), certValue);

                // wrap CertBag in a Sequence
                DerOutputStream certout = new DerOutputStream();
                certout.write(DerValue.tag_Sequence, certBag);
                byte[] certBagValue = certout.toByteArray();

                // Wrap the CertBag encoding in a context-specific tag.
                DerOutputStream bagValue = new DerOutputStream();
                bagValue.write(certBagValue);
                // write SafeBag Value
                safeBag.write(DerValue.createTag(DerValue.TAG_CONTEXT,
                                true, (byte) 0), bagValue);

                // write SafeBag Attributes
                // All Certs should have a unique friendlyName.
                // This change is made to meet NSS requirements.
                byte[] bagAttrs = null;
                String friendlyName = cert.getSubjectX500Principal().getName();
                if (i == 0) {
                    // Only End-Entity Cert should have a localKeyId.
                    bagAttrs = getBagAttributes(friendlyName, entry.keyId);
                } else {
                    // Trusted root CA certs and Intermediate CA certs do not
                    // need to have a localKeyId, and hence localKeyId is null
                    // This change is made to meet NSS/Netscape requirements.
                    // NSS pkcs12 library requires trusted CA certs in the
                    // certificate chain to have unique or null localKeyID.
                    // However, IE/OpenSSL do not impose this restriction.
                    bagAttrs = getBagAttributes(friendlyName, null);
                }
                if (bagAttrs != null) {
                    safeBag.write(bagAttrs);
                }

                // wrap as Sequence
                out.write(DerValue.tag_Sequence, safeBag);
            } // for cert-chain
        }

        // wrap as SequenceOf SafeBag
        DerOutputStream safeBagValue = new DerOutputStream();
        safeBagValue.write(DerValue.tag_SequenceOf, out);
        byte[] safeBagData = safeBagValue.toByteArray();

        // encrypt the content (EncryptedContentInfo)
        byte[] encrContentInfo = encryptContent(safeBagData, password);

        // -- SEQUENCE of EncryptedData
        DerOutputStream encrData = new DerOutputStream();
        DerOutputStream encrDataContent = new DerOutputStream();
        encrData.putInteger(0);
        encrData.write(encrContentInfo);
        encrDataContent.write(DerValue.tag_Sequence, encrData);
        return encrDataContent.toByteArray();
    }

    /*
     * Create SafeContent Data content type.
     * Includes encrypted private key in a SafeBag of type PKCS8ShroudedKeyBag.
     * Each PKCS8ShroudedKeyBag includes pkcs12 attributes
     * (see comments in getBagAttributes)
     */
    private byte[] createSafeContent()
        throws CertificateException, IOException {

        DerOutputStream out = new DerOutputStream();
        for (Enumeration<String> e = entries.keys(); e.hasMoreElements(); ) {

            String alias = e.nextElement();
            KeyEntry entry = entries.get(alias);

            // Create SafeBag of type pkcs8ShroudedKeyBag
            DerOutputStream safeBag = new DerOutputStream();
            safeBag.putOID(PKCS8ShroudedKeyBag_OID);

            // get the encrypted private key
            byte[] encrBytes = entry.protectedPrivKey;
            EncryptedPrivateKeyInfo encrInfo = null;
            try {
                encrInfo = new EncryptedPrivateKeyInfo(encrBytes);
            } catch (IOException ioe) {
                throw new IOException("Private key not stored as "
                        + "PKCS#8 EncryptedPrivateKeyInfo" + ioe.getMessage());
            }

            // Wrap the EncryptedPrivateKeyInfo in a context-specific tag.
            DerOutputStream bagValue = new DerOutputStream();
            bagValue.write(encrInfo.getEncoded());
            safeBag.write(DerValue.createTag(DerValue.TAG_CONTEXT,
                                true, (byte) 0), bagValue);

            // write SafeBag Attributes
            byte[] bagAttrs = getBagAttributes(alias, entry.keyId);
            safeBag.write(bagAttrs);

            // wrap as Sequence
            out.write(DerValue.tag_Sequence, safeBag);
        }

        // wrap as Sequence
        DerOutputStream safeBagValue = new DerOutputStream();
        safeBagValue.write(DerValue.tag_Sequence, out);
        return safeBagValue.toByteArray();
    }


    /*
     * Encrypt the contents using Password-based (PBE) encryption
     * as defined in PKCS #5.
     *
     * NOTE: Currently pbeWithSHAAnd40BiteRC2-CBC algorithmID is used
     *       to derive the key and IV.
     *
     * @return encrypted contents encoded as EncryptedContentInfo
     */
    private byte[] encryptContent(byte[] data, char[] password)
        throws IOException {

        byte[] encryptedData = null;

        // create AlgorithmParameters
        AlgorithmParameters algParams =
                getAlgorithmParameters("PBEWithSHA1AndRC2_40");
        DerOutputStream bytes = new DerOutputStream();
        AlgorithmId algId =
                new AlgorithmId(pbeWithSHAAnd40BitRC2CBC_OID, algParams);
        algId.encode(bytes);
        byte[] encodedAlgId = bytes.toByteArray();

        try {
            // Use JCE
            SecretKey skey = getPBEKey(password);
            Cipher cipher = Cipher.getInstance("PBEWithSHA1AndRC2_40");
            cipher.init(Cipher.ENCRYPT_MODE, skey, algParams);
            encryptedData = cipher.doFinal(data);

        } catch (Exception e) {
            IOException ioe = new IOException("Failed to encrypt" +
                                " safe contents entry: " + e);
            ioe.initCause(e);
            throw ioe;
        }

        // create EncryptedContentInfo
        DerOutputStream bytes2 = new DerOutputStream();
        bytes2.putOID(ContentInfo.DATA_OID);
        bytes2.write(encodedAlgId);

        // Wrap encrypted data in a context-specific tag.
        DerOutputStream tmpout2 = new DerOutputStream();
        tmpout2.putOctetString(encryptedData);
        bytes2.writeImplicit(DerValue.createTag(DerValue.TAG_CONTEXT,
                                        false, (byte)0), tmpout2);

        // wrap EncryptedContentInfo in a Sequence
        DerOutputStream out = new DerOutputStream();
        out.write(DerValue.tag_Sequence, bytes2);
        return out.toByteArray();
    }

    /**
     * Loads the keystore from the given input stream.
     *
     * <p>If a password is given, it is used to check the integrity of the
     * keystore data. Otherwise, the integrity of the keystore is not checked.
     *
     * @param stream the input stream from which the keystore is loaded
     * @param password the (optional) password used to check the integrity of
     * the keystore.
     *
     * @exception IOException if there is an I/O or format problem with the
     * keystore data
     * @exception NoSuchAlgorithmException if the algorithm used to check
     * the integrity of the keystore cannot be found
     * @exception CertificateException if any of the certificates in the
     * keystore could not be loaded
     */
    public synchronized void engineLoad(InputStream stream, char[] password)
        throws IOException, NoSuchAlgorithmException, CertificateException
    {
        DataInputStream dis;
        CertificateFactory cf = null;
        ByteArrayInputStream bais = null;
        byte[] encoded = null;

        if (stream == null)
           return;

        // reset the counter
        counter = 0;

        DerValue val = new DerValue(stream);
        DerInputStream s = val.toDerInputStream();
        int version = s.getInteger();

        if (version != VERSION_3) {
           throw new IOException("PKCS12 keystore not in version 3 format");
        }

        entries.clear();

        /*
         * Read the authSafe.
         */
        byte[] authSafeData;
        ContentInfo authSafe = new ContentInfo(s);
        ObjectIdentifier contentType = authSafe.getContentType();

        if (contentType.equals(ContentInfo.DATA_OID)) {
           authSafeData = authSafe.getData();
        } else /* signed data */ {
           throw new IOException("public key protected PKCS12 not supported");
        }

        DerInputStream as = new DerInputStream(authSafeData);
        DerValue[] safeContentsArray = as.getSequence(2);
        int count = safeContentsArray.length;

        // reset the count at the start
        privateKeyCount = 0;

        /*
         * Spin over the ContentInfos.
         */
        for (int i = 0; i < count; i++) {
            byte[] safeContentsData;
            ContentInfo safeContents;
            DerInputStream sci;
            byte[] eAlgId = null;

            sci = new DerInputStream(safeContentsArray[i].toByteArray());
            safeContents = new ContentInfo(sci);
            contentType = safeContents.getContentType();
            safeContentsData = null;
            if (contentType.equals(ContentInfo.DATA_OID)) {
                safeContentsData = safeContents.getData();
            } else if (contentType.equals(ContentInfo.ENCRYPTED_DATA_OID)) {
                if (password == null) {
                   continue;
                }
                DerInputStream edi =
                                safeContents.getContent().toDerInputStream();
                int edVersion = edi.getInteger();
                DerValue[] seq = edi.getSequence(2);
                ObjectIdentifier edContentType = seq[0].getOID();
                eAlgId = seq[1].toByteArray();
                if (!seq[2].isContextSpecific((byte)0)) {
                   throw new IOException("encrypted content not present!");
                }
                byte newTag = DerValue.tag_OctetString;
                if (seq[2].isConstructed())
                   newTag |= 0x20;
                seq[2].resetTag(newTag);
                safeContentsData = seq[2].getOctetString();

                // parse Algorithm parameters
                DerInputStream in = seq[1].toDerInputStream();
                ObjectIdentifier algOid = in.getOID();
                AlgorithmParameters algParams = parseAlgParameters(in);

                try {
                    // Use JCE
                    SecretKey skey = getPBEKey(password);
                    Cipher cipher = Cipher.getInstance(algOid.toString());
                    cipher.init(Cipher.DECRYPT_MODE, skey, algParams);
                    safeContentsData = cipher.doFinal(safeContentsData);

                } catch (Exception e) {
                    IOException ioe = new IOException("failed to decrypt safe"
                                        + " contents entry: " + e);
                    ioe.initCause(e);
                    throw ioe;
                }
            } else {
                throw new IOException("public key protected PKCS12" +
                                        " not supported");
            }
            DerInputStream sc = new DerInputStream(safeContentsData);
            loadSafeContents(sc, password);
        }

        // The MacData is optional.
        if (password != null && s.available() > 0) {
           MacData macData = new MacData(s);
           try {
                String algName = macData.getDigestAlgName().toUpperCase();
                if (algName.equals("SHA")  ||
                    algName.equals("SHA1") ||
                    algName.equals("SHA-1")) {
                    algName = "SHA1";
                }

                // generate MAC (MAC key is created within JCE)
                Mac m = Mac.getInstance("HmacPBE" + algName);
                PBEParameterSpec params =
                        new PBEParameterSpec(macData.getSalt(),
                                        macData.getIterations());
                SecretKey key = getPBEKey(password);
                m.init(key, params);
                m.update(authSafeData);
                byte[] macResult = m.doFinal();

                if (!Arrays.equals(macData.getDigest(), macResult)) {
                   throw new SecurityException("Failed PKCS12" +
                                        " integrity checking");
                }
           } catch (Exception e) {
                IOException ioe =
                        new IOException("Integrity check failed: " + e);
                ioe.initCause(e);
                throw ioe;
           }
        }

        /*
         * Match up private keys with certificate chains.
         */
        KeyEntry[] list = keyList.toArray(new KeyEntry[keyList.size()]);
        for (int m = 0; m < list.length; m++) {
            KeyEntry entry = list[m];
            if (entry.keyId != null) {
                ArrayList<X509Certificate> chain =
                                new ArrayList<X509Certificate>();
                X509Certificate cert = certs.get(new KeyId(entry.keyId));
                while (cert != null) {
                    chain.add(cert);
                    X500Principal issuerDN = cert.getIssuerX500Principal();
                    if (issuerDN.equals(cert.getSubjectX500Principal())) {
                        break;
                    }
                    cert = certs.get(issuerDN);
                }
                /* Update existing KeyEntry in entries table */
                if (chain.size() > 0)
                    entry.chain = chain.toArray(new Certificate[chain.size()]);
            }
        }
        certs.clear();
        keyList.clear();
    }


    private void loadSafeContents(DerInputStream stream, char[] password)
        throws IOException, NoSuchAlgorithmException, CertificateException
    {
        DerValue[] safeBags = stream.getSequence(2);
        int count = safeBags.length;

        /*
         * Spin over the SafeBags.
         */
        for (int i = 0; i < count; i++) {
            ObjectIdentifier bagId;
            DerInputStream sbi;
            DerValue bagValue;
            Object bagItem = null;

            sbi = safeBags[i].toDerInputStream();
            bagId = sbi.getOID();
            bagValue = sbi.getDerValue();
            if (!bagValue.isContextSpecific((byte)0)) {
                throw new IOException("unsupported PKCS12 bag value type "
                                        + bagValue.tag);
            }
            bagValue = bagValue.data.getDerValue();
            if (bagId.equals(PKCS8ShroudedKeyBag_OID)) {
                KeyEntry kEntry = new KeyEntry();
                kEntry.protectedPrivKey = bagValue.toByteArray();
                bagItem = kEntry;
                privateKeyCount++;
            } else if (bagId.equals(CertBag_OID)) {
                DerInputStream cs = new DerInputStream(bagValue.toByteArray());
                DerValue[] certValues = cs.getSequence(2);
                ObjectIdentifier certId = certValues[0].getOID();
                if (!certValues[1].isContextSpecific((byte)0)) {
                    throw new IOException("unsupported PKCS12 cert value type "
                                        + certValues[1].tag);
                }
                DerValue certValue = certValues[1].data.getDerValue();
                CertificateFactory cf = CertificateFactory.getInstance("X509");
                X509Certificate cert;
                cert = (X509Certificate)cf.generateCertificate
                        (new ByteArrayInputStream(certValue.getOctetString()));
                bagItem = cert;
            } else {
                // log error message for "unsupported PKCS12 bag type"
            }

            DerValue[] attrSet;
            try {
                attrSet = sbi.getSet(2);
            } catch (IOException e) {
                // entry does not have attributes
                // Note: CA certs can have no attributes
                // OpenSSL generates pkcs12 with no attr for CA certs.
                attrSet = null;
            }

            String alias = null;
            byte[] keyId = null;

            if (attrSet != null) {
                for (int j = 0; j < attrSet.length; j++) {
                    DerInputStream as =
                        new DerInputStream(attrSet[j].toByteArray());
                    DerValue[] attrSeq = as.getSequence(2);
                    ObjectIdentifier attrId = attrSeq[0].getOID();
                    DerInputStream vs =
                        new DerInputStream(attrSeq[1].toByteArray());
                    DerValue[] valSet;
                    try {
                        valSet = vs.getSet(1);
                    } catch (IOException e) {
                        throw new IOException("Attribute " + attrId +
                                " should have a value " + e.getMessage());
                    }
                    if (attrId.equals(PKCS9FriendlyName_OID)) {
                        alias = valSet[0].getBMPString();
                    } else if (attrId.equals(PKCS9LocalKeyId_OID)) {
                        keyId = valSet[0].getOctetString();
                    } else {
                        // log error message for "unknown attr"
                    }
                }
            }

            /*
             * As per PKCS12 v1.0 friendlyname (alias) and localKeyId (keyId)
             * are optional PKCS12 bagAttributes. But entries in the keyStore
             * are identified by their alias. Hence we need to have an
             * Unfriendlyname in the alias, if alias is null. The keyId
             * attribute is required to match the private key with the
             * certificate. If we get a bagItem of type KeyEntry with a
             * null keyId, we should skip it entirely.
             */
            if (bagItem instanceof KeyEntry) {
                KeyEntry entry = (KeyEntry)bagItem;
                if (keyId == null) {
                   // Insert a localKeyID for the privateKey
                   // Note: This is a workaround to allow null localKeyID
                   // attribute in pkcs12 with one private key entry and
                   // associated cert-chain
                   if (privateKeyCount == 1) {
                        keyId = "01".getBytes("UTF8");
                   } else {
                        continue;
                   }
                }
                entry.keyId = keyId;
                // restore date if it exists
                String keyIdStr = new String(keyId, "UTF8");
                Date date = null;
                if (keyIdStr.startsWith("Time ")) {
                    try {
                        date = new Date(
                                Long.parseLong(keyIdStr.substring(5)));
                    } catch (Exception e) {
                        date = null;
                    }
                }
                if (date == null) {
                    date = new Date();
                }
                entry.date = date;
                keyList.add(entry);
                if (alias == null)
                   alias = getUnfriendlyName();
                entry.alias = alias;
                entries.put(alias.toLowerCase(), entry);
            } else if (bagItem instanceof X509Certificate) {
                X509Certificate cert = (X509Certificate)bagItem;
                // Insert a localKeyID for the corresponding cert
                // Note: This is a workaround to allow null localKeyID
                // attribute in pkcs12 with one private key entry and
                // associated cert-chain
                if ((keyId == null) && (privateKeyCount == 1)) {
                    // insert localKeyID only for EE cert or self-signed cert
                    if (i == 0) {
                        keyId = "01".getBytes("UTF8");
                    }
                }
                if (keyId != null) {
                    KeyId keyid = new KeyId(keyId);
                    if (!certs.containsKey(keyid))
                        certs.put(keyid, cert);
                }
                if (alias != null) {
                    if (!certs.containsKey(alias))
                        certs.put(alias, cert);
                }
                X500Principal subjectDN = cert.getSubjectX500Principal();
                if (subjectDN != null) {
                    if (!certs.containsKey(subjectDN))
                        certs.put(subjectDN, cert);
                }
            }
        }
    }

    private String getUnfriendlyName() {
        counter++;
        return (String.valueOf(counter));
    }
}
