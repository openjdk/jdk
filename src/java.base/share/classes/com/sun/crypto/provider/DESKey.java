/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InvalidObjectException;
import java.lang.ref.Reference;
import java.security.MessageDigest;
import java.security.KeyRep;
import java.security.InvalidKeyException;
import java.util.Arrays;
import javax.crypto.SecretKey;
import javax.crypto.spec.DESKeySpec;

import jdk.internal.ref.CleanerFactory;

/**
 * This class represents a DES key.
 *
 * @author Jan Luehe
 *
 */

final class DESKey implements SecretKey {

    @java.io.Serial
    private static final long serialVersionUID = 7724971015953279128L;

    private byte[] key;

    /**
     * Uses the first 8 bytes of the given key as the DES key.
     *
     * @param key the buffer with the DES key bytes.
     *
     * @exception InvalidKeyException if less than 8 bytes are available for
     * the key.
     */
    DESKey(byte[] key) throws InvalidKeyException {
        this(key, 0);
    }

    /**
     * Uses the first 8 bytes in <code>key</code>, beginning at
     * <code>offset</code>, as the DES key
     *
     * @param key the buffer with the DES key bytes.
     * @param offset the offset in <code>key</code>, where the DES key bytes
     * start.
     *
     * @exception InvalidKeyException if less than 8 bytes are available for
     * the key.
     */
    DESKey(byte[] key, int offset) throws InvalidKeyException {
        if (key == null || key.length - offset < DESKeySpec.DES_KEY_LEN) {
            throw new InvalidKeyException("Wrong key size");
        }
        this.key = new byte[DESKeySpec.DES_KEY_LEN];
        System.arraycopy(key, offset, this.key, 0, DESKeySpec.DES_KEY_LEN);
        DESKeyGenerator.setParityBit(this.key, 0);

        // Use the cleaner to zero the key when no longer referenced
        final byte[] k = this.key;
        CleanerFactory.cleaner().register(this,
                () -> Arrays.fill(k, (byte)0x00));
    }

    public byte[] getEncoded() {
        // Return a copy of the key, rather than a reference,
        // so that the key data cannot be modified from outside
        try {
            return key.clone();
        } finally {
            // prevent this from being cleaned for the above block
            Reference.reachabilityFence(this);
        }
    }

    public String getAlgorithm() {
        return "DES";
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
            return Arrays.hashCode(this.key) ^ "des".hashCode();
        } finally {
            // prevent this from being cleaned for the above block
            Reference.reachabilityFence(this);
        }
    }

    @Override
    public boolean equals(Object obj) {
        try {
            if (this == obj)
                return true;

            if (!(obj instanceof SecretKey that))
                return false;

            String thatAlg = that.getAlgorithm();
            if (!(thatAlg.equalsIgnoreCase("DES")))
                return false;

            byte[] thatKey = that.getEncoded();
            boolean ret = MessageDigest.isEqual(this.key, thatKey);
            Arrays.fill(thatKey, (byte)0x00);
            return ret;
        } finally {
            // prevent this from being cleaned for the above block
            Reference.reachabilityFence(this);
        }
    }

    /**
     * Restores the state of this object from the stream.
     *
     * @param  s the {@code ObjectInputStream} from which data is read
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if a serialized class cannot be loaded
     */
    @java.io.Serial
    private void readObject(java.io.ObjectInputStream s)
         throws IOException, ClassNotFoundException
    {
        s.defaultReadObject();
        if ((key == null) || (key.length != DESKeySpec.DES_KEY_LEN)) {
            throw new InvalidObjectException("Wrong key size");
        }
        byte[] temp = key;
        key = temp.clone();
        Arrays.fill(temp, (byte)0x00);

        DESKeyGenerator.setParityBit(key, 0);

        // Use the cleaner to zero the key when no longer referenced
        final byte[] k = this.key;
        CleanerFactory.cleaner().register(this,
                () -> Arrays.fill(k, (byte)0x00));
    }

    /**
     * Replace the DES key to be serialized.
     *
     * @return the standard KeyRep object to be serialized
     *
     * @throws java.io.ObjectStreamException if a new object representing
     * this DES key could not be created
     */
    @java.io.Serial
    private Object writeReplace() throws java.io.ObjectStreamException {
        try {
            return new KeyRep(KeyRep.Type.SECRET,
                        getAlgorithm(),
                        getFormat(),
                        key);
        } finally {
            // prevent this from being cleaned for the above block
            Reference.reachabilityFence(this);
        }
    }
}
