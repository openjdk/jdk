/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.provider;

import sun.security.pkcs.NamedPKCS8Key;
import sun.security.x509.NamedX509Key;

import javax.crypto.DecapsulateException;
import javax.crypto.KEM;
import javax.crypto.KEMSpi;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.NamedParameterSpec;
import java.util.Arrays;

/// A base class for all `KEM` implementations that can be
/// configured with a named parameter set. See [NamedKeyPairGenerator]
/// for more details.
public abstract class NamedKEM implements KEMSpi {

    private final String fname; // family name
    private final NamedKeyFactory fac;

    /// Creates a new `NamedKEM` object.
    ///
    /// @param fname the family name
    /// @param fac the `KeyFactory` used to translate foreign keys and
    ///         perform key validation
    protected NamedKEM(String fname, NamedKeyFactory fac) {
        if (fname == null) {
            throw new AssertionError("fname cannot be null");
        }
        this.fname = fname;
        this.fac = fac;
    }

    @Override
    public EncapsulatorSpi engineNewEncapsulator(PublicKey publicKey,
            AlgorithmParameterSpec spec, SecureRandom secureRandom)
            throws InvalidAlgorithmParameterException, InvalidKeyException {
        if (spec != null) {
            throw new InvalidAlgorithmParameterException(
                    "The " + fname + " algorithm does not take any parameters");
        }
        // translate also check the key
        var nk = (NamedX509Key) fac.toNamedKey(publicKey);
        var pk = nk.getRawBytes();
        return getKeyConsumerImpl(this, nk.getParams(), pk,
                implCheckPublicKey(nk.getParams().getName(), pk), secureRandom);
    }

    @Override
    public DecapsulatorSpi engineNewDecapsulator(
            PrivateKey privateKey, AlgorithmParameterSpec spec)
            throws InvalidAlgorithmParameterException, InvalidKeyException {
        if (spec != null) {
            throw new InvalidAlgorithmParameterException(
                    "The " + fname + " algorithm does not take any parameters");
        }
        // translate also check the key
        var nk = (NamedPKCS8Key) fac.toNamedKey(privateKey);
        var sk = nk.getExpanded();
        return getKeyConsumerImpl(this, nk.getParams(), sk,
                implCheckPrivateKey(nk.getParams().getName(), sk), null);
    }

    // We don't have a flag on whether key is public key or private key.
    // The correct method should always be called.
    private record KeyConsumerImpl(NamedKEM kem, String pname, int sslen,
            int clen, byte[] key, Object k2, SecureRandom sr)
            implements KEMSpi.EncapsulatorSpi, KEMSpi.DecapsulatorSpi {
        @Override
        public SecretKey engineDecapsulate(byte[] encapsulation, int from, int to,
                String algorithm) throws DecapsulateException {
            if (encapsulation.length != clen) {
                throw new DecapsulateException("Invalid key encapsulation message length");
            }
            var ss = kem.implDecapsulate(pname, key, k2, encapsulation);
            try {
                return new SecretKeySpec(ss,
                        from, to - from, algorithm);
            } finally {
                Arrays.fill(ss, (byte)0);
            }
        }

        @Override
        public KEM.Encapsulated engineEncapsulate(int from, int to, String algorithm) {
            var enc = kem.implEncapsulate(pname, key, k2, sr);
            try {
                return new KEM.Encapsulated(
                        new SecretKeySpec(enc[1],
                                from, to - from, algorithm),
                        enc[0],
                        null);
            } finally {
                Arrays.fill(enc[1], (byte)0);
            }
        }

        @Override
        public int engineSecretSize() {
            return sslen;
        }

        @Override
        public int engineEncapsulationSize() {
            return clen;
        }
    }

    private static KeyConsumerImpl getKeyConsumerImpl(NamedKEM kem,
            NamedParameterSpec nps, byte[] key, Object k2, SecureRandom sr) {
        String pname = nps.getName();
        return new KeyConsumerImpl(kem, pname, kem.implSecretSize(pname), kem.implEncapsulationSize(pname),
                key, k2, sr);
    }

    /// User-defined encap function.
    ///
    /// @param pname parameter name
    /// @param pk public key in raw bytes
    /// @param pk2 parsed public key, `null` if none. See [#implCheckPublicKey].
    /// @param sr SecureRandom object, `null` if not initialized
    /// @return the key encapsulation message and the shared key (in this order)
    /// @throws ProviderException if there is an internal error
    protected abstract byte[][] implEncapsulate(String pname, byte[] pk, Object pk2, SecureRandom sr);

    /// User-defined decap function.
    ///
    /// @param pname parameter name
    /// @param sk private key in raw bytes
    /// @param sk2 parsed private key, `null` if none. See [#implCheckPrivateKey].
    /// @param encap the key encapsulation message
    /// @return the shared key
    /// @throws ProviderException if there is an internal error
    /// @throws DecapsulateException if there is another error
    protected abstract byte[] implDecapsulate(String pname, byte[] sk, Object sk2, byte[] encap)
            throws DecapsulateException;

    /// User-defined function returning shared secret key length.
    ///
    /// @param pname parameter name
    /// @return shared secret key length
    /// @throws ProviderException if there is an internal error
    protected abstract int implSecretSize(String pname);

    /// User-defined function returning key encapsulation message length.
    ///
    /// @param pname parameter name
    /// @return key encapsulation message length
    /// @throws ProviderException if there is an internal error
    protected abstract int implEncapsulationSize(String pname);

    /// User-defined function to validate a public key.
    ///
    /// This method will be called in `newEncapsulator`. This gives the provider a chance to
    /// reject the key so an `InvalidKeyException` can be thrown earlier.
    /// An implementation can optionally return a "parsed key" as an `Object` value.
    /// This object will be passed into the [#implEncapsulate] method along with the raw key.
    ///
    /// The default implementation returns `null`.
    ///
    /// @param pname parameter name
    /// @param pk public key in raw bytes
    /// @return a parsed key, `null` if none.
    /// @throws InvalidKeyException if the key is invalid
    protected Object implCheckPublicKey(String pname, byte[] pk) throws InvalidKeyException {
        return null;
    }

    /// User-defined function to validate a private key.
    ///
    /// This method will be called in `newDecapsulator`. This gives the provider a chance to
    /// reject the key so an `InvalidKeyException` can be thrown earlier.
    /// An implementation can optionally return a "parsed key" as an `Object` value.
    /// This object will be passed into the [#implDecapsulate] method along with the raw key.
    ///
    /// The default implementation returns `null`.
    ///
    /// @param pname parameter name
    /// @param sk private key in raw bytes
    /// @return a parsed key, `null` if none.
    /// @throws InvalidKeyException if the key is invalid
    protected Object implCheckPrivateKey(String pname, byte[] sk) throws InvalidKeyException {
        return null;
    }
}
