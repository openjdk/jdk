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
import sun.security.util.RawKeySpec;
import sun.security.x509.NamedX509Key;

import java.security.AsymmetricKey;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactorySpi;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

/// A base class for all `KeyFactory` implementations that can be
/// configured with a named parameter set. See [NamedKeyPairGenerator]
/// for more details.
///
/// This factory supports reading and writing to RAW formats:
///
/// 1. It reads from a RAW key using `translateKey` if `key.getFormat` is "RAW".
/// 2. It writes to a RAW [EncodedKeySpec] if `getKeySpec(key, EncodedKeySpec.class)`
///    is called. The format of the output is "RAW" and the algorithm is
///    intentionally left unspecified.
/// 3. It reads from and writes to the internal type [RawKeySpec].
///
/// When reading from a RAW format, it needs enough info to derive the
/// parameter set name.
public abstract class NamedKeyFactory extends KeyFactorySpi {

    private final String fname; // family name
    private final String[] pnames; // allowed parameter set name (at least one)

    /// Creates a new `NamedKeyFactory` object.
    ///
    /// @param fname the family name
    /// @param pnames the standard parameter set names, at least one is needed.
    protected NamedKeyFactory(String fname, String... pnames) {
        if (fname == null) {
            throw new AssertionError("fname cannot be null");
        }
        if (pnames == null || pnames.length == 0) {
            throw new AssertionError("pnames cannot be null or empty");
        }
        this.fname = fname;
        this.pnames = pnames;
    }

    private String checkName(String pname) throws InvalidKeyException {
        for (var n : pnames) {
            if (n.equalsIgnoreCase(pname)) {
                // return the stored standard name
                return n;
            }
        }
        throw new InvalidKeyException("Unsupported parameter set name: " + pname);
    }

    @Override
    protected PublicKey engineGeneratePublic(KeySpec keySpec)
            throws InvalidKeySpecException {
        return switch (keySpec) {
            case X509EncodedKeySpec xspec -> {
                try {
                    yield fromX509(xspec.getEncoded());
                } catch (InvalidKeyException e) {
                    throw new InvalidKeySpecException(e);
                }
            }
            case RawKeySpec rks -> {
                if (pnames.length == 1) {
                    yield new NamedX509Key(fname, pnames[0], rks.getKeyArr());
                } else {
                    throw new InvalidKeySpecException("Parameter set name unavailable");
                }
            }
            case EncodedKeySpec espec when espec.getFormat().equalsIgnoreCase("RAW") -> {
                if (pnames.length == 1) {
                    yield new NamedX509Key(fname, pnames[0], espec.getEncoded());
                } else {
                    throw new InvalidKeySpecException("Parameter set name unavailable");
                }
            }
            case null -> throw new InvalidKeySpecException(
                    "keySpec must not be null");
            default ->
                    throw new InvalidKeySpecException(keySpec.getClass().getName() +
                            " not supported.");
        };
    }

    @Override
    protected PrivateKey engineGeneratePrivate(KeySpec keySpec)
            throws InvalidKeySpecException {
        return switch (keySpec) {
            case PKCS8EncodedKeySpec pspec -> {
                var bytes = pspec.getEncoded();
                try {
                    yield fromPKCS8(bytes);
                } catch (InvalidKeyException e) {
                    throw new InvalidKeySpecException(e);
                } finally {
                    Arrays.fill(bytes, (byte) 0);
                }
            }
            case RawKeySpec rks -> {
                if (pnames.length == 1) {
                    var raw = rks.getKeyArr();
                    try {
                        yield fromRaw(pnames[0], raw);
                    } catch (InvalidKeyException e) {
                        throw new InvalidKeySpecException("Invalid key input", e);
                    }
                } else {
                    throw new InvalidKeySpecException("Parameter set name unavailable");
                }
            }
            case EncodedKeySpec espec when espec.getFormat().equalsIgnoreCase("RAW") -> {
                if (pnames.length == 1) {
                    var raw = espec.getEncoded();
                    try {
                        yield fromRaw(pnames[0], raw);
                    } catch (InvalidKeyException e) {
                        throw new InvalidKeySpecException("Invalid key input", e);
                    }
                } else {
                    throw new InvalidKeySpecException("Parameter set name unavailable");
                }
            }
            case null -> throw new InvalidKeySpecException(
                    "keySpec must not be null");
            default ->
                    throw new InvalidKeySpecException(keySpec.getClass().getName() +
                            " not supported.");
        };
    }

    private PrivateKey fromRaw(String pname, byte[] raw)
            throws InvalidKeyException {
        return NamedPKCS8Key.internalCreate(
                fname, pname, raw, implExpand(pname, raw));
    }

    private PrivateKey fromPKCS8(byte[] bytes)
            throws InvalidKeyException {
        var k = new NamedPKCS8Key(fname, bytes, this::implExpand);
        checkName(k.getParams().getName());
        return k;
    }

    private PublicKey fromX509(byte[] bytes)
            throws InvalidKeyException {
        var k = new NamedX509Key(fname, bytes);
        checkName(k.getParams().getName());
        return k;
    }

    private static class RawEncodedKeySpec extends EncodedKeySpec {
        public RawEncodedKeySpec(byte[] encodedKey) {
            super(encodedKey);
        }

        @Override
        public String getFormat() {
            return "RAW";
        }
    }

    @Override
    protected <T extends KeySpec> T engineGetKeySpec(Key key, Class<T> keySpec)
            throws InvalidKeySpecException {
        try {
            key = toNamedKey(key);
        } catch (InvalidKeyException e) {
            throw new InvalidKeySpecException(e);
        }
        // key is now either NamedPKCS8Key or NamedX509Key of permitted param set
        if (key instanceof NamedPKCS8Key nk) {
            byte[] bytes = null;
            try {
                if (keySpec == PKCS8EncodedKeySpec.class) {
                    return keySpec.cast(
                            new PKCS8EncodedKeySpec(bytes = key.getEncoded()));
                } else if (keySpec == RawKeySpec.class) {
                    return keySpec.cast(new RawKeySpec(nk.getRawBytes()));
                } else if (keySpec.isAssignableFrom(EncodedKeySpec.class)) {
                    return keySpec.cast(
                            new RawEncodedKeySpec(nk.getRawBytes()));
                } else {
                    throw new InvalidKeySpecException("Unsupported type: " + keySpec);
                }
            } finally {
                if (bytes != null) {
                    Arrays.fill(bytes, (byte)0);
                }
            }
        } else if (key instanceof NamedX509Key nk) {
            if (keySpec == X509EncodedKeySpec.class
                    && key.getFormat().equalsIgnoreCase("X.509")) {
                return keySpec.cast(new X509EncodedKeySpec(key.getEncoded()));
            } else if (keySpec == RawKeySpec.class) {
                return keySpec.cast(new RawKeySpec(nk.getRawBytes()));
            } else if (keySpec.isAssignableFrom(EncodedKeySpec.class)) {
                return keySpec.cast(new RawEncodedKeySpec(nk.getRawBytes()));
            } else {
                throw new InvalidKeySpecException("Unsupported type: " + keySpec);
            }
        }
        throw new AssertionError("No " + keySpec.getName() + " for " + key.getClass());
    }

    @Override
    protected Key engineTranslateKey(Key key) throws InvalidKeyException {
        // The base toNamedKey only makes sure key is translated into a NamedKey.
        // the key material is still the same as the input.
        return toNamedKey(key);
    }

    protected Key toNamedKey(Key key) throws InvalidKeyException {
        if (key == null) {
            throw new InvalidKeyException("Key must not be null");
        }
        if (key instanceof NamedX509Key nk) {
            checkName(nk.getParams().getName());
            return key;
        }
        if (key instanceof NamedPKCS8Key nk) {
            checkName(nk.getParams().getName());
            return key;
        }
        var format = key.getFormat();
        if (format == null) {
            throw new InvalidKeyException("Unextractable key");
        } else if (format.equalsIgnoreCase("RAW")) {
            var kAlg = key.getAlgorithm();
            if (key instanceof AsymmetricKey pk) {
                String pname;
                // Three cases that we can find the parameter set name from a RAW key:
                // 1. getParams() returns one
                // 2. getAlgorithm() returns param set name (some provider does this)
                // 3. getAlgorithm() returns family name but this KF is for param set name
                if (pk.getParams() instanceof NamedParameterSpec nps) {
                    pname = checkName(nps.getName());
                } else {
                    if (kAlg.equalsIgnoreCase(fname)) {
                        if (pnames.length == 1) {
                            pname = pnames[0];
                        } else {
                            throw new InvalidKeyException("No parameter set info");
                        }
                    } else {
                        pname = checkName(kAlg);
                    }
                }
                var raw = key.getEncoded();
                return key instanceof PrivateKey
                        ? fromRaw(pname, raw)
                        : new NamedX509Key(fname, pname, raw);
            } else {
                throw new InvalidKeyException("Unsupported key type: " + key.getClass());
            }
        } else if (format.equalsIgnoreCase("PKCS#8") && key instanceof PrivateKey) {
            var bytes = key.getEncoded();
            try {
                return fromPKCS8(bytes);
            } finally {
                Arrays.fill(bytes, (byte) 0);
            }
        } else if (format.equalsIgnoreCase("X.509") && key instanceof PublicKey) {
            return fromX509(key.getEncoded());
        } else {
            throw new InvalidKeyException("Unsupported key format: " + key.getFormat());
        }
    }

    /// User-defined function to generate the expanded format of
    /// a [NamedPKCS8Key] from its encoding format.
    ///
    /// This method is called when the key factory is constructing a private
    /// key. The ownership of the result is fully granted to the caller.
    ///
    /// @param pname the parameter set name
    /// @param input the encoding, could be any format
    /// @return the expanded key, not null
    /// @throws InvalidKeyException if `input` is invalid
    protected abstract byte[] implExpand(String pname, byte[] input)
            throws InvalidKeyException;
}
