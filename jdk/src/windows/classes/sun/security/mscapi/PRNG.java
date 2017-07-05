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

import java.security.ProviderException;
import java.security.SecureRandomSpi;

/**
 * Native PRNG implementation for Windows using the Microsoft Crypto API.
 *
 * @since 1.6
 */

public final class PRNG extends SecureRandomSpi
    implements java.io.Serializable {

    // TODO - generate the serialVersionUID
    //private static final long serialVersionUID = XXX;

    /*
     * The CryptGenRandom function fills a buffer with cryptographically random
     * bytes.
     */
    private static native byte[] generateSeed(int length, byte[] seed);

    /**
     * Creates a random number generator.
     */
    public PRNG() {
    }

    /**
     * Reseeds this random object. The given seed supplements, rather than
     * replaces, the existing seed. Thus, repeated calls are guaranteed
     * never to reduce randomness.
     *
     * @param seed the seed.
     */
    protected void engineSetSeed(byte[] seed) {
        if (seed != null) {
            generateSeed(-1, seed);
        }
    }

    /**
     * Generates a user-specified number of random bytes.
     *
     * @param bytes the array to be filled in with random bytes.
     */
    protected void engineNextBytes(byte[] bytes) {
        if (bytes != null) {
            if (generateSeed(0, bytes) == null) {
                throw new ProviderException("Error generating random bytes");
            }
        }
    }

    /**
     * Returns the given number of seed bytes.  This call may be used to
     * seed other random number generators.
     *
     * @param numBytes the number of seed bytes to generate.
     *
     * @return the seed bytes.
     */
    protected byte[] engineGenerateSeed(int numBytes) {
        byte[] seed = generateSeed(numBytes, null);

        if (seed == null) {
            throw new ProviderException("Error generating seed bytes");
        }
        return seed;
    }
}
