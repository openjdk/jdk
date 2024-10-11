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

import java.io.ByteArrayOutputStream;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.PrivateKey;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.security.SignatureSpi;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Objects;

/// A base class for all `Signature` implementations that can be
/// configured with a named parameter set. See [NamedKeyPairGenerator]
/// for more details.
///
/// This class does not work with preHash signatures.
public abstract class NamedSignature extends SignatureSpi {

    private final String fname; // family name
    private final String[] pnames; // allowed parameter set name (at least one)

    private final ByteArrayOutputStream bout = new ByteArrayOutputStream();

    // init with...
    private String name;
    private byte[] secKey;
    private byte[] pubKey;

    private Object sk2;
    private Object pk2;

    /// Creates a new `NamedSignature` object.
    ///
    /// @param fname the family name
    /// @param pnames the standard parameter set names, at least one is needed.
    protected NamedSignature(String fname, String... pnames) {
        if (fname == null) {
            throw new AssertionError("fname cannot be null");
        }
        if (pnames == null || pnames.length == 0) {
            throw new AssertionError("pnames cannot be null or empty");
        }
        this.fname = fname;
        this.pnames = pnames;
    }

    @Override
    protected void engineInitVerify(PublicKey publicKey) throws InvalidKeyException {
        // translate also check the key
        var nk = (NamedX509Key) new NamedKeyFactory(fname, pnames)
                .engineTranslateKey(publicKey);
        name = nk.getParams().getName();
        pubKey = nk.getRawBytes();
        pk2 = implCheckPublicKey(name, pubKey);
        secKey = null;
        bout.reset();
    }

    @Override
    protected void engineInitSign(PrivateKey privateKey) throws InvalidKeyException {
        // translate also check the key
        var nk = (NamedPKCS8Key) new NamedKeyFactory(fname, pnames)
                .engineTranslateKey(privateKey);
        name = nk.getParams().getName();
        secKey = nk.getRawBytes();
        sk2 = implCheckPrivateKey(name, secKey);
        pubKey = null;
        bout.reset();
    }

    @Override
    protected void engineUpdate(byte b) throws SignatureException {
        bout.write(b);
    }

    @Override
    protected void engineUpdate(byte[] b, int off, int len) throws SignatureException {
        bout.write(b, off, len);
    }

    @Override
    protected byte[] engineSign() throws SignatureException {
        if (secKey != null) {
            var msg = bout.toByteArray();
            bout.reset();
            return implSign(name, secKey, sk2, msg, appRandom);
        } else {
            throw new SignatureException("No private key");
        }
    }

    @Override
    protected boolean engineVerify(byte[] sig) throws SignatureException {
        if (pubKey != null) {
            var msg = bout.toByteArray();
            bout.reset();
            return implVerify(name, pubKey, pk2, msg, sig);
        } else {
            throw new SignatureException("No public key");
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void engineSetParameter(String param, Object value)
            throws InvalidParameterException {
        throw new InvalidParameterException("setParameter() not supported");
    }

    @Override
    @SuppressWarnings("deprecation")
    protected Object engineGetParameter(String param) throws InvalidParameterException {
        throw new InvalidParameterException("getParameter() not supported");
    }

    @Override
    protected void engineSetParameter(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
        if (params != null) {
            throw new InvalidAlgorithmParameterException(
                    "The " + fname + " algorithm does not take any parameters");
        }
    }

    @Override
    protected AlgorithmParameters engineGetParameters() {
        return null;
    }

    /// User-defined sign function.
    ///
    /// @param name parameter name
    /// @param sk private key in raw bytes
    /// @param sk2 parsed private key, `null` if none. See [#implCheckPrivateKey].
    /// @param msg the message
    /// @param sr SecureRandom object, `null` if not initialized
    /// @return the signature
    /// @throws ProviderException if there is an internal error
    /// @throws SignatureException if there is another error
    protected abstract byte[] implSign(String name, byte[] sk, Object sk2,
            byte[] msg, SecureRandom sr) throws SignatureException;

    /// User-defined verify function.
    ///
    /// @param name parameter name
    /// @param pk public key in raw bytes
    /// @param pk2 parsed public key, `null` if none. See [#implCheckPublicKey].
    /// @param msg the message
    /// @param sig the signature
    /// @return true if verified
    /// @throws ProviderException if there is an internal error
    /// @throws SignatureException if there is another error
    protected abstract boolean implVerify(String name, byte[] pk, Object pk2,
            byte[] msg, byte[] sig) throws SignatureException;

    /// User-defined function to validate a public key.
    ///
    /// This method will be called in `initVerify`. This gives the provider a chance to
    /// reject the key so an `InvalidKeyException` can be thrown earlier.
    /// An implementation can optionally return a "parsed key" as an `Object` value.
    /// This object will be passed into the [#implVerify] method along with the raw key.
    ///
    /// The default implementation returns `null`.
    ///
    /// @param name parameter name
    /// @param pk public key in raw bytes
    /// @return a parsed key, `null` if none.
    /// @throws InvalidKeyException if the key is invalid
    protected Object implCheckPublicKey(String name, byte[] pk) throws InvalidKeyException {
        return null;
    }

    /// User-defined function to validate a private key.
    ///
    /// This method will be called in `initSign`. This gives the provider a chance to
    /// reject the key so an `InvalidKeyException` can be thrown earlier.
    /// An implementation can optionally return a "parsed key" as an `Object` value.
    /// This object will be passed into the [#implSign] method along with the raw key.
    ///
    /// The default implementation returns `null`.
    ///
    /// @param name parameter name
    /// @param sk private key in raw bytes
    /// @return a parsed key, `null` if none.
    /// @throws InvalidKeyException if the key is invalid
    protected Object implCheckPrivateKey(String name, byte[] sk) throws InvalidKeyException {
        return null;
    }
}
