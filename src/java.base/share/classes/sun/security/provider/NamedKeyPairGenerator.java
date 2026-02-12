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

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidParameterException;
import java.security.KeyPair;
import java.security.KeyPairGeneratorSpi;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.NamedParameterSpec;

/// A base class for all `KeyPairGenerator` implementations that can be
/// configured with a named parameter set.
///
/// Together with [NamedKeyFactory], [NamedKEM], and [NamedSignature], these
/// classes form a compact framework designed to support any public key
/// algorithm standardized with named parameter sets. In this scenario,
/// the algorithm name is the "family name" and each standardized parameter
/// set has a "parameter set name". Implementations of these classes are able
/// to instantiate a `KeyPairGenerator`, `KeyFactory`, or `KEM` or `Signature`
/// object using either the family name or a parameter set name. All keys used
/// in this context will be of the type [NamedPKCS8Key] or [NamedX509Key],
/// with `getAlgorithm` returning the family name, and `getParams` returning
/// the parameter set name as a [NamedParameterSpec] object.
///
/// A `NamedKeyPairGenerator` or `NamedKeyFactory` implementation must include
/// a zero-argument public constructor that calls `super(fname, pnames)`, where
/// `fname` is the family name of the algorithm and `pnames` are its supported
/// parameter set names. `pnames` must contain at least one element. For an
/// implementation of `NamedKeyPairGenerator`, the first element becomes its
/// default parameter set, i.e. the parameter set used by generated keys unless
/// [#initialize(AlgorithmParameterSpec, java.security.SecureRandom)]
/// is called on a different parameter set.
///
/// A `NamedKEM` or `NamedSignature` implementation must include a zero-argument
/// public constructor that calls `super(fname, factory)`, where `fname` is the
/// family name of the algorithm and `factory` is the `NamedKeyFactory` object
/// that is used to translate foreign keys. `factory` only recognizes
/// parameter sets supported by this implementation.
///
/// An implementation must implement all abstract methods. For all these
/// methods, the implementation must relinquish any "ownership" of any input
/// and output array argument. Precisely, the implementation must not retain
/// any reference to a returning array so that it won't be able to modify its
/// content later. Similarly, the implementation must not modify any input
/// array argument and must not retain any reference to an input array argument
/// after the call.
///
/// Also, an implementation must not keep any extra copy of a private key in
/// any format. For key generation, the only copy is the one returned in the
/// [#implGenerateKeyPair] call. For all other methods, it must not make
/// a copy of the input private key. A `KEM` implementation also must not
/// keep a copy of the shared secret key, no matter if it's an encapsulator
/// or a decapsulator. Only the code that owns these sensitive data can
/// choose to perform cleanup when it determines they are no longer needed.
///
/// The `NamedSignature` and `NamedKEM` classes provide `implCheckPublicKey`
/// and `implCheckPrivateKey` methods that allow an implementation to validate
/// a key before using it. An implementation may return a parsed key in
/// a local type, and this parsed key will be passed to an operational method
/// (For example, `implSign`) later. An implementation must not retain
/// a reference of the parsed key.
///
/// The private key, represented as a byte array when used in `NamedKEM` or
/// `NamedSignature`, is referred to as its expanded format. For some
/// algorithms, this format may differ from the
/// [key material][NamedPKCS8Key#getRawBytes()] inside a PKCS #8 file. For example,
/// [FIPS 204](https://nvlpubs.nist.gov/nistpubs/FIPS/NIST.FIPS.204.pdf)
/// Table 2 defines the ML-DSA-65 private key as a 4032-byte array, which is
/// used in the ML-DSA.Sign function in Algorithm 2, representing the
/// expanded format. However, in
/// [RFC 9881](https://datatracker.ietf.org/doc/html/rfc9881#name-private-key-format),
/// a private key can be encoded into a CHOICE of three formats, none in the
/// same as the FIPS 204 format. The choices are defined in
/// [sun.security.util.KeyChoices]. A `NamedKeyPairGenerator` implementation
/// should return both the expanded key and a preferred encoding in its
///  [#implGenerateKeyPair] method.
///
/// A `NamedKeyFactory` must override the `implExpand` method to derive
/// the expanded format from an encoding format, or return `null` if there
/// is no difference.
///
/// Implementations may support multiple encoding formats.
///
/// A `NamedKeyFactory` must not modify the encoding when generating a key
/// from a `KeySpec` object, ensuring that when re-encoded, the key retains
/// its original encoding format.
///
/// A `NamedKeyFactory` can choose a different encoding format when
/// `translateKey` is called.
///
/// When constructing a [NamedX509Key] or [NamedPKCS8Key] object from raw key
/// bytes, the key bytes are directly referenced within the object, so the
/// caller must not modify them afterward. Similarly, the key's `getRawBytes`
/// method returns direct references to the underlying raw key bytes, meaning
/// the caller must not alter the contents of the returned value.
///
/// Together, these measures ensure the classes are as efficient as possible,
/// preventing unnecessary array cloning and potential data leaks. While these
/// classes should not be considered immutable, strictly adhering to the rules
/// above will ensure data integrity is maintained.
///
/// Note: A limitation of `NamedKeyPairGenerator` and `NamedKeyFactory` is
/// that the keys generated by their implementations will always be of type
/// `NamedX509Key` or `NamedPKCS8Key`. Existing implementations of algorithms
/// like EdDSA and XDH have been generating keys implementing `EdECKey` or
/// `XECKey` interfaces, and they are not rewritten with this framework.
/// `NamedParameterSpec` fields not implemented with this framework include
/// Ed25519, Ed448, X25519, and X448.
public abstract class NamedKeyPairGenerator extends KeyPairGeneratorSpi {

    private final String fname; // family name
    private final String[] pnames; // allowed parameter set names (at least one)

    protected String pname; // parameter set name, if can be determined
    private SecureRandom secureRandom;

    /// Creates a new `NamedKeyPairGenerator` object.
    ///
    /// @param fname the family name
    /// @param pnames supported parameter set names, at least one is needed.
    ///     If multiple, the first one becomes the default parameter set name.
    protected NamedKeyPairGenerator(String fname, String... pnames) {
        if (fname == null) {
            throw new AssertionError("fname cannot be null");
        }
        if (pnames == null || pnames.length == 0) {
            throw new AssertionError("pnames cannot be null or empty");
        }
        this.fname = fname;
        this.pnames = pnames;
    }

    private String checkName(String pname) throws InvalidAlgorithmParameterException {
        for (var n : pnames) {
            if (n.equalsIgnoreCase(pname)) {
                // return the stored standard pname
                return n;
            }
        }
        throw new InvalidAlgorithmParameterException(
                "Unsupported parameter set name: " + pname);
    }

    @Override
    public void initialize(AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidAlgorithmParameterException {
        if (params instanceof NamedParameterSpec spec) {
            pname = checkName(spec.getName());
        } else {
            throw new InvalidAlgorithmParameterException(
                    "Unsupported AlgorithmParameterSpec: " + params);
        }
        this.secureRandom = random;
    }

    @Override
    public void initialize(int keysize, SecureRandom random) {
        if (keysize != -1) {
            // User can call initialize(-1, sr) to provide a SecureRandom
            // without touching the parameter set currently used
            throw new InvalidParameterException("keysize not supported");
        }
        this.secureRandom = random;
    }

    @Override
    public KeyPair generateKeyPair() {
        String tmpName = pname != null ? pname : pnames[0];
        var keys = implGenerateKeyPair(tmpName, secureRandom);
        return new KeyPair(new NamedX509Key(fname, tmpName, keys[0]),
                NamedPKCS8Key.internalCreate(fname, tmpName, keys[1],
                        keys.length == 2 ? null : keys[2]));
    }

    /// User-defined key pair generator.
    ///
    /// @param pname parameter set name
    /// @param sr `SecureRandom` object, `null` if not initialized
    /// @return the public key, the private key in its encoding format, and
    ///         the private key in its expanded format (in this order) in
    ///         raw bytes. If the expanded format of the private key is the
    ///         same as its encoding format, the 3rd element must be omitted.
    /// @throws ProviderException if there is an internal error
    protected abstract byte[][] implGenerateKeyPair(String pname, SecureRandom sr);
}
