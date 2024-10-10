/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidParameterException;
import java.security.KeyPair;
import java.security.KeyPairGeneratorSpi;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.NamedParameterSpec;
import java.util.Objects;

/// An implementation extends this class to create its own `KeyPairGenerator`.
///
/// An implementation must include a zero-argument public constructor that calls
/// `super(fname, pnames)`, where `fname` is the family name of the algorithm and
/// `pnames` are the supported parameter set names. `pnames` must contain at least
/// one element and the first element is the default parameter set name,
/// i.e. the parameter set to be used in key pair generation unless
/// [#initialize(AlgorithmParameterSpec, java.security.SecureRandom)]
/// is called to choose a specific parameter set. This requirement also applies
/// to implementations of [NamedKeyFactory], [NamedKEM], and [NamedSignature],
/// although there is no default parameter set concept for these classes.
///
/// An implementation must implement all abstract methods. For all these
/// methods, the implementation must relinquish any "ownership" of any input
/// and output array argument. Precisely, the implementation must not retain
/// any reference to a returning array so that it won't be able to modify its
/// content later. Similarly, the implementation must not modify any input
/// array argument and must not retain any reference to an input array argument
/// after the call. Together, this makes sure that the caller does not need to
/// make any defensive copy on the input and output arrays. This requirement
/// also applies to abstract methods defined in [NamedKEM] and [NamedSignature].
///
/// Also, an implementation must not keep any extra copy of a private key.
/// For key generation, the only copy is the one returned in the
/// [#implGenerateKeyPair] call. For all other methods, it must not make
/// a copy of the input private key. A `KEM` implementation also must
/// not keep a copy of the shared secret key, no matter if it's an
/// encapsulator or a decapsulator.
///
/// The `NamedSignature` and `NamedKEM` classes provide `implCheckPublicKey`
/// and `implCheckPrivateKey` methods that allow an implementation to validate
/// a key before using it. An implementation may return a parsed key of
/// a local type, and this parsed key will be passed to an operational method
/// (For example, `implSign`) later. An implementation must not retain
/// a reference of the parsed key.
public abstract class NamedKeyPairGenerator extends KeyPairGeneratorSpi {

    private final String fname; // family name
    private final String[] pnames; // allowed parameter set name (at least one)

    protected String name = null; // init as
    private SecureRandom secureRandom;

    /// Creates a new `NamedKeyPairGenerator` object.
    ///
    /// @param fname the family name
    /// @param pnames supported parameter set names, at least one is needed.
    ///     If multiple, the first one becomes the default parameter set name.
    protected NamedKeyPairGenerator(String fname, String... pnames) {
        this.fname = Objects.requireNonNull(fname);
        if (pnames == null || pnames.length == 0) {
            throw new AssertionError("pnames cannot be null or empty");
        }
        this.pnames = pnames;
    }

    private String checkName(String name) throws InvalidAlgorithmParameterException  {
        for (var pname : pnames) {
            if (pname.equalsIgnoreCase(name)) {
                // return the stored standard name
                return pname;
            }
        }
        throw new InvalidAlgorithmParameterException(
                "Unknown parameter set name: " + name);
    }

    @Override
    public void initialize(AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidAlgorithmParameterException {
        if (params instanceof NamedParameterSpec spec) {
            name = checkName(spec.getName());
        } else {
            throw new InvalidAlgorithmParameterException(
                    "Unknown AlgorithmParameterSpec: " + params);
        }
        this.secureRandom = random ;
    }

    @Override
    public void initialize(int keysize, SecureRandom random) {
        if (keysize != -1) {
            // Bonus: a chance to provide a SecureRandom without
            // specifying a parameter set name
            throw new InvalidParameterException("keysize not supported");
        }
        this.secureRandom = random;
    }

    @Override
    public KeyPair generateKeyPair() {
        String pname = name != null ? name : pnames[0];
        var keys = implGenerateKeyPair(pname, secureRandom);
        return new KeyPair(new NamedX509Key(fname, pname, keys[0]),
                new NamedPKCS8Key(fname, pname, keys[1]));
    }

    /// User-defined key pair generator.
    ///
    /// @param pname parameter set name
    /// @param sr `SecureRandom` object, `null` if not initialized
    /// @return public key and private key (in this order) in raw bytes
    /// @throws ProviderException if there is an internal error
    public abstract byte[][] implGenerateKeyPair(String pname, SecureRandom sr);
}
