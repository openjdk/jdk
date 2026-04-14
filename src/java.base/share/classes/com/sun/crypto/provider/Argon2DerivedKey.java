/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Locale;
import javax.crypto.SecretKey;
import javax.crypto.spec.Argon2ParameterSpec;
import javax.security.auth.Destroyable;

import sun.security.util.Argon2Util;

/**
 * This class represents a secret key derived using Argon2 whose toString()
 * method outputs Argon2 encoded hash.
 *
 * @since 27
 */
public final class Argon2DerivedKey implements SecretKey, Destroyable {

    @java.io.Serial
    private static final long serialVersionUID = 724953279128L;

    // cannot be final; set to null after destroy() is called
    private byte[] key;
    private final String algo;

    // for including Argon2 parameters in toString() method
    private final transient String info;

    /**
     * Create a Argon2 derived secret key using the supplied arguments.
     *
     * @param type the Argon2 variant.
     * @param spec the Argon2 parameters used.
     * @param key the derived key bytes.
     * @param algo the algorithm for the derived key.
     *
     * @exception InvalidKeyException if less than 8 bytes are available for
     * the key.
     */
    public Argon2DerivedKey(String type, Argon2ParameterSpec spec,
            byte[] key, String algo) {
        this.key = key; // internally derived, no need to clone
        this.algo = algo;

        this.info = String.format("%s key derived using %s with params = %s",
                algo, type, spec.toString());
    }

    public byte[] getEncoded() {
        if (isDestroyed()) {
            throw new IllegalStateException("key destroyed");
        }
        // Return a copy of the key, rather than a reference,
        // so that the key data cannot be modified from outside
        return key.clone();
    }

    public String getAlgorithm() {
        return algo;
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
        return Arrays.hashCode(key) ^
                algo.toLowerCase(Locale.ENGLISH).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (isDestroyed()) {
            throw new IllegalStateException("key destroyed");
        }

        if (!(obj instanceof SecretKey that)) {
            return false;
        }
        if (!(algo.equalsIgnoreCase(that.getAlgorithm()))) {
            return false;
        }
        try {
            byte[] thatKey = that.getEncoded();
            boolean ret = MessageDigest.isEqual(this.key, thatKey);
            if (thatKey != null) {
                Arrays.fill(thatKey, (byte)0);
            }
            return ret;
        } catch (RuntimeException re) {
            // if cannot compare for any reason
            return false;
        }
    }

    @Override
    public String toString() {
        return info;
    }

    @Override
    public void destroy() {
        if (key != null) {
            Arrays.fill(key, (byte)0);
            key = null;
        }
    }

    @Override
    public boolean isDestroyed() {
        return (key == null);
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
            throws IOException, ClassNotFoundException {
        // not directly serialized
        throw new InvalidObjectException("Invalid object");
    }

    /**
     * Replace this key object to a KeyRep object for serialization
     *
     * @return the standard KeyRep object to be serialized
     *
     * @throws java.io.ObjectStreamException if a new object representing
     * this DES key could not be created
     */
    @java.io.Serial
    private Object writeReplace() throws java.io.ObjectStreamException {
        return new KeyRep(KeyRep.Type.SECRET,
                    getAlgorithm(),
                    getFormat(),
                    key);
    }
}
