/*
 * Copyright 2005-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.internal.spec;

import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.SecretKey;

/**
 * Parameters for the TLS PRF (pseudo-random function). The PRF function
 * is defined in RFC 2246.
 * This class is used to initialize KeyGenerators of the type "TlsPrf".
 *
 * <p>Instances of this class are immutable.
 *
 * @since   1.6
 * @author  Andreas Sterbenz
 * @deprecated Sun JDK internal use only --- WILL BE REMOVED in Dolphin (JDK 7)
 */
@Deprecated
public class TlsPrfParameterSpec implements AlgorithmParameterSpec {

    private final SecretKey secret;
    private final String label;
    private final byte[] seed;
    private final int outputLength;

    /**
     * Constructs a new TlsPrfParameterSpec.
     *
     * @param secret the secret to use in the calculation (or null)
     * @param label the label to use in the calculation
     * @param seed the random seed to use in the calculation
     * @param outputLength the length in bytes of the output key to be produced
     *
     * @throws NullPointerException if label or seed is null
     * @throws IllegalArgumentException if outputLength is negative
     */
    public TlsPrfParameterSpec(SecretKey secret, String label, byte[] seed, int outputLength) {
        if ((label == null) || (seed == null)) {
            throw new NullPointerException("label and seed must not be null");
        }
        if (outputLength <= 0) {
            throw new IllegalArgumentException("outputLength must be positive");
        }
        this.secret = secret;
        this.label = label;
        this.seed = seed.clone();
        this.outputLength = outputLength;
    }

    /**
     * Returns the secret to use in the PRF calculation, or null if there is no
     * secret.
     *
     * @return the secret to use in the PRF calculation, or null if there is no
     * secret.
     */
    public SecretKey getSecret() {
        return secret;
    }

    /**
     * Returns the label to use in the PRF calcuation.
     *
     * @return the label to use in the PRF calcuation.
     */
    public String getLabel() {
        return label;
    }

    /**
     * Returns a copy of the seed to use in the PRF calcuation.
     *
     * @return a copy of the seed to use in the PRF calcuation.
     */
    public byte[] getSeed() {
        return seed.clone();
    }

    /**
     * Returns the length in bytes of the output key to be produced.
     *
     * @return the length in bytes of the output key to be produced.
     */
    public int getOutputLength() {
        return outputLength;
    }

}
