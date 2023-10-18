/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.crypto.provider;

import java.io.*;
import java.lang.ref.Reference;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.Locale;
import java.security.MessageDigest;
import java.security.KeyRep;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.PBEKeySpec;

import static java.nio.charset.StandardCharsets.UTF_8;

import jdk.internal.ref.CleanerFactory;

/**
 * This class represents a PBE key derived using PBKDF2 defined
 * in PKCS#5 v2.0. meaning that
 * 1) the password must consist of characters which will be converted
 *    to bytes using UTF-8 character encoding.
 * 2) salt, iteration count, and to be derived key length are supplied
 *
 * @author Valerie Peng
 *
 */
final class PBKDF2KeyImpl implements javax.crypto.interfaces.PBEKey {

    @java.io.Serial
    private static final long serialVersionUID = -2234868909660948157L;

    private final char[] passwd;
    private final byte[] salt;
    private final int iterCount;
    private byte[] key;

    // The following fields are not Serializable. See writeReplace method.
    private final transient Mac prf;
    private final transient Cleaner.Cleanable cleaner;

    private static byte[] getPasswordBytes(char[] passwd) {
        CharBuffer cb = CharBuffer.wrap(passwd);
        ByteBuffer bb = UTF_8.encode(cb);

        int len = bb.limit();
        byte[] passwdBytes = new byte[len];
        bb.get(passwdBytes, 0, len);
        bb.clear().put(new byte[len]);

        return passwdBytes;
    }

    /**
     * Creates a PBE key from a given PBE key specification.
     *
     * @param keySpec the given PBE key specification
     * @param prfAlgo the given PBE key algorithm
     */
    PBKDF2KeyImpl(PBEKeySpec keySpec, String prfAlgo)
        throws InvalidKeySpecException {
        this.passwd = keySpec.getPassword();
        // Convert the password from char[] to byte[]
        byte[] passwdBytes = getPasswordBytes(this.passwd);

        try {
            this.salt = keySpec.getSalt();
            if (salt == null) {
                throw new InvalidKeySpecException("Salt not found");
            }
            this.iterCount = keySpec.getIterationCount();
            if (iterCount == 0) {
                throw new InvalidKeySpecException("Iteration count not found");
            } else if (iterCount < 0) {
                throw new InvalidKeySpecException("Iteration count is negative");
            }
            int keyLength = keySpec.getKeyLength();
            if (keyLength == 0) {
                throw new InvalidKeySpecException("Key length not found");
            } else if (keyLength < 0) {
                throw new InvalidKeySpecException("Key length is negative");
            }
            this.prf = Mac.getInstance(prfAlgo, SunJCE.getInstance());
            this.key = deriveKey(prf, passwdBytes, salt, iterCount, keyLength);
        } catch (NoSuchAlgorithmException nsae) {
            // not gonna happen; re-throw just in case
            throw new InvalidKeySpecException(nsae);
        } finally {
            Arrays.fill(passwdBytes, (byte) 0x00);
            if (key == null) {
                Arrays.fill(passwd, '\0');
            }
        }
        // Use the cleaner to zero the key when no longer referenced
        final byte[] k = this.key;
        final char[] p = this.passwd;
        cleaner = CleanerFactory.cleaner().register(this,
                () -> {
                    Arrays.fill(k, (byte) 0x00);
                    Arrays.fill(p, '\0');
                });
    }

    private static byte[] deriveKey(final Mac prf, final byte[] password,
            byte[] salt, int iterCount, int keyLengthInBit) {
        int keyLength = keyLengthInBit/8;
        byte[] key = new byte[keyLength];
        try {
            int hlen = prf.getMacLength();
            int intL = (keyLength + hlen - 1)/hlen; // ceiling
            int intR = keyLength - (intL - 1)*hlen; // residue
            byte[] ui = new byte[hlen];
            byte[] ti = new byte[hlen];
            String algName = prf.getAlgorithm();
            // SecretKeySpec cannot be used, since password can be empty here.
            SecretKey macKey = new SecretKey() {
                @java.io.Serial
                private static final long serialVersionUID = 7874493593505141603L;
                @Override
                public String getAlgorithm() {
                    return algName;
                }
                @Override
                public String getFormat() {
                    return "RAW";
                }
                @Override
                public byte[] getEncoded() {
                    return password.clone();
                }
                @Override
                public int hashCode() {
                    return Arrays.hashCode(password) * 41 +
                      algName.toLowerCase(Locale.ENGLISH).hashCode();
                }
                @Override
                public boolean equals(Object obj) {
                    if (this == obj) return true;
                    if (obj == null || this.getClass() != obj.getClass()) return false;
                    SecretKey sk = (SecretKey)obj;
                    return algName.equalsIgnoreCase(
                        sk.getAlgorithm()) &&
                        MessageDigest.isEqual(password, sk.getEncoded());
                }
                // This derived key can't be deserialized.
                @java.io.Serial
                private void readObject(ObjectInputStream stream)
                        throws IOException, ClassNotFoundException {
                    throw new InvalidObjectException(
                            "PBKDF2KeyImpl SecretKeys are not " +
                            "directly deserializable");
                }
            };

            prf.init(macKey);

            byte[] ibytes = new byte[4];
            for (int i = 1; i <= intL; i++) {
                prf.update(salt);
                ibytes[3] = (byte) i;
                ibytes[2] = (byte) ((i >> 8) & 0xff);
                ibytes[1] = (byte) ((i >> 16) & 0xff);
                ibytes[0] = (byte) ((i >> 24) & 0xff);
                prf.update(ibytes);
                prf.doFinal(ui, 0);
                System.arraycopy(ui, 0, ti, 0, ui.length);

                for (int j = 2; j <= iterCount; j++) {
                    prf.update(ui);
                    prf.doFinal(ui, 0);
                    // XOR the intermediate Ui's together.
                    for (int k = 0; k < ui.length; k++) {
                        ti[k] ^= ui[k];
                    }
                }
                if (i == intL) {
                    System.arraycopy(ti, 0, key, (i-1)*hlen, intR);
                } else {
                    System.arraycopy(ti, 0, key, (i-1)*hlen, hlen);
                }
            }
        } catch (GeneralSecurityException gse) {
            throw new RuntimeException("Error deriving PBKDF2 keys", gse);
        }
        return key;
    }

    public byte[] getEncoded() {
        try {
            return key.clone();
        } finally {
            // prevent this from being cleaned for the above block
            Reference.reachabilityFence(this);
        }
    }

    public String getAlgorithm() {
        return "PBKDF2With" + prf.getAlgorithm();
    }

    public int getIterationCount() {
        return iterCount;
    }

    public void clear() {
        cleaner.clean();
    }

    public char[] getPassword() {
        try {
            return passwd.clone();
        } finally {
            // prevent this from being cleaned for the above block
            Reference.reachabilityFence(this);
        }
    }

    public byte[] getSalt() {
        return salt.clone();
    }

    public String getFormat() {
        return "RAW";
    }

    /**
     * Calculates a hash code value for the object.
     * Objects that are equal will also have the same hashcode.
     */
    @Override
    public int hashCode() {
        try {
            return Arrays.hashCode(this.key)
                    ^ getAlgorithm().toLowerCase(Locale.ENGLISH).hashCode();
        } finally {
            // prevent this from being cleaned for the above block
            Reference.reachabilityFence(this);
        }
    }

    @Override
    public boolean equals(Object obj) {
        try {
            if (obj == this) {
                return true;
            }

            if (!(obj instanceof SecretKey that)) {
                return false;
            }

            if (!(that.getAlgorithm().equalsIgnoreCase(getAlgorithm()))) {
                return false;
            }
            if (!(that.getFormat().equalsIgnoreCase("RAW"))) {
                return false;
            }
            byte[] thatEncoded = that.getEncoded();
            boolean ret = MessageDigest.isEqual(key, thatEncoded);
            Arrays.fill(thatEncoded, (byte)0x00);
            return ret;
        } finally {
            // prevent this from being cleaned for the above block
            Reference.reachabilityFence(this);
        }
    }

    /**
     * Replace the PBE key to be serialized.
     *
     * @return the standard KeyRep object to be serialized
     *
     * @throws ObjectStreamException if a new object representing
     * this PBE key could not be created
     */
    @java.io.Serial
    private Object writeReplace() throws ObjectStreamException {
        try {
            return new KeyRep(KeyRep.Type.SECRET, getAlgorithm(),
                    getFormat(), key);
        } finally {
            // prevent this from being cleaned for the above block
            Reference.reachabilityFence(this);
        }
    }

    /**
     * Restores the state of this object from the stream.
     * <p>
     * Deserialization of this class is not supported.
     *
     * @param  stream the {@code ObjectInputStream} from which data is read
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if a serialized class cannot be loaded
     */
    @java.io.Serial
    private void readObject(ObjectInputStream stream)
            throws IOException, ClassNotFoundException {
        throw new InvalidObjectException(
                "PBKDF2KeyImpl keys are not directly deserializable");
    }
}
