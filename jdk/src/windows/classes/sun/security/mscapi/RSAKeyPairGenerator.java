/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.mscapi;

import java.util.UUID;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.RSAKeyGenParameterSpec;

import sun.security.jca.JCAUtil;

/**
 * RSA keypair generator.
 *
 * Standard algorithm, minimum key length is 512 bit, maximum is 16,384.
 * Generates a private key that is exportable.
 *
 * @since 1.6
 */
public final class RSAKeyPairGenerator extends KeyPairGeneratorSpi {

    // Supported by Microsoft Base, Strong and Enhanced Cryptographic Providers
    private static final int KEY_SIZE_MIN = 512; // disallow MSCAPI min. of 384
    private static final int KEY_SIZE_MAX = 16384;
    private static final int KEY_SIZE_DEFAULT = 1024;

    // size of the key to generate, KEY_SIZE_MIN <= keySize <= KEY_SIZE_MAX
    private int keySize;

    public RSAKeyPairGenerator() {
        // initialize to default in case the app does not call initialize()
        initialize(KEY_SIZE_DEFAULT, null);
    }

    // initialize the generator. See JCA doc
    // random is always ignored
    public void initialize(int keySize, SecureRandom random) {

        checkKeySize(keySize);
    }

    // second initialize method. See JCA doc
    // random and exponent are always ignored
    public void initialize(AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidAlgorithmParameterException {

        if (params == null) {
            checkKeySize(KEY_SIZE_DEFAULT);

        } else if (params instanceof RSAKeyGenParameterSpec) {

            if (((RSAKeyGenParameterSpec) params).getPublicExponent() != null) {
                throw new InvalidAlgorithmParameterException
                    ("Exponent parameter is not supported");
            }
            checkKeySize(((RSAKeyGenParameterSpec) params).getKeysize());

        } else {
            throw new InvalidAlgorithmParameterException
                ("Params must be an instance of RSAKeyGenParameterSpec");
        }
    }

    // generate the keypair. See JCA doc
    public KeyPair generateKeyPair() {

        // Generate each keypair in a unique key container
        RSAKeyPair keys =
            generateRSAKeyPair(keySize,
                "{" + UUID.randomUUID().toString() + "}");

        return new KeyPair(keys.getPublic(), keys.getPrivate());
    }

    private void checkKeySize(int keySize) throws InvalidParameterException {
        if (keySize < KEY_SIZE_MIN) {
            throw new InvalidParameterException
                ("Key size must be at least " + KEY_SIZE_MIN + " bits");
        }
        if (keySize > KEY_SIZE_MAX) {
            throw new InvalidParameterException
                ("Key size must be " + KEY_SIZE_MAX + " bits or less");
        }
        this.keySize = keySize;
    }

    private static native RSAKeyPair generateRSAKeyPair(int keySize,
        String keyContainerName);
}
