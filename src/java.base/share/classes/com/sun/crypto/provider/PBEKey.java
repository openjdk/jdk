/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.ref.Cleaner.Cleanable;
import java.security.MessageDigest;
import java.security.KeyRep;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Locale;
import javax.crypto.SecretKey;
import javax.crypto.spec.PBEKeySpec;
import sun.security.util.PBEUtil;

import jdk.internal.ref.CleanerFactory;

/**
 * This class represents a PBE key.
 *
 * @author Jan Luehe
 *
 */
final class PBEKey implements SecretKey {

    @java.io.Serial
    private static final long serialVersionUID = -2234768909660948176L;

    private byte[] key;

    private final String type;

    private transient Cleanable cleanable;

    /**
     * Creates a PBE key from a given PBE key specification.
     *
     * @param keytype the given PBE key specification
     */
    PBEKey(PBEKeySpec keySpec, String keytype) throws InvalidKeySpecException {
        char[] passwd = keySpec.getPassword();
        if (passwd == null) {
            // Should allow an empty password.
            passwd = new char[0];
        }
        this.key = PBEUtil.encodePassword(passwd);
        Arrays.fill(passwd, '\0');
        type = keytype;

        // Use the cleaner to zero the key when no longer referenced
        final byte[] k = this.key;
        cleanable = CleanerFactory.cleaner().register(this,
                () -> Arrays.fill(k, (byte)0x00));
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
        return type;
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
            if (obj == this)
                return true;

            if (!(obj instanceof SecretKey that))
                return false;

            // destroyed keys are considered different
            if (isDestroyed() || that.isDestroyed()) {
                return false;
            }

            if (!(that.getAlgorithm().equalsIgnoreCase(type)))
                return false;

            byte[] thatEncoded = that.getEncoded();
            boolean ret = MessageDigest.isEqual(this.key, thatEncoded);
            Arrays.fill(thatEncoded, (byte)0x00);
            return ret;
        } finally {
            // prevent this from being cleaned for the above block
            Reference.reachabilityFence(this);
        }
    }

    /**
     * Clears the internal copy of the key.
     *
     */
    @Override
    public void destroy() {
        if (cleanable != null) {
            cleanable.clean();
            cleanable = null;
        }
    }

    @Override
    public boolean isDestroyed() {
        return (cleanable == null);
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
        if (key == null) {
            throw new InvalidObjectException(
                    "PBEKey couldn't be deserialized");
        }
        byte[] temp = key;
        key = temp.clone();
        Arrays.fill(temp, (byte)0x00);

        // Accept "\0" to signify "zero-length password with no terminator".
        if (!(key.length == 1 && key[0] == 0)) {
            for (int i = 0; i < key.length; i++) {
                if ((key[i] < '\u0020') || (key[i] > '\u007E')) {
                    throw new InvalidObjectException(
                            "PBEKey had non-ASCII chars");
                }
            }
        }

        // Use cleaner to zero the key when no longer referenced
        final byte[] k = this.key;
        cleanable = CleanerFactory.cleaner().register(this,
                () -> Arrays.fill(k, (byte)0x00));
    }


    /**
     * Replace the PBE key to be serialized.
     *
     * @return the standard KeyRep object to be serialized
     *
     * @throws java.io.ObjectStreamException if a new object representing
     * this PBE key could not be created
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
