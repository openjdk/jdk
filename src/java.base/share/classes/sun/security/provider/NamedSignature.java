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
import java.security.*;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;
import java.util.Objects;

/// An implementation extends this class to create its own `Signature`.
public abstract class NamedSignature extends SignatureSpi {

    private final String fname; // family name
    private final String[] pnames; // allowed parameter set name, need at least one

    private final ByteArrayOutputStream bout = new ByteArrayOutputStream();

    // init with...
    private String name = null;
    private byte[] secKey = null;
    private byte[] pubKey = null;

    public NamedSignature(String fname, String... pnames) {
        this.fname = Objects.requireNonNull(fname);
        if (pnames == null || pnames.length == 0) {
            throw new AssertionError("pnames cannot be null or empty");
        }
        this.pnames = pnames;
    }

    private String checkName(String name) throws InvalidKeyException  {
        for (var pname : pnames) {
            if (pname.equalsIgnoreCase(name)) {
                // return the stored pname, name should be sTrAnGe.
                return pname;
            }
        }
        throw new InvalidKeyException("Unknown parameter set name: " + name);
    }

    @Override
    protected void engineInitVerify(PublicKey publicKey) throws InvalidKeyException {
        // translate and check
        var nk = (NamedX509Key) new NamedKeyFactory(fname, pnames)
                .engineTranslateKey(publicKey);
        name = nk.getParams().getName();
        pubKey = nk.getRawBytes();
        checkPublicKey(name, pubKey);
        secKey = null;
        bout.reset();
    }

    @Override
    protected void engineInitSign(PrivateKey privateKey) throws InvalidKeyException {
        // translate and check
        var nk = (NamedPKCS8Key) new NamedKeyFactory(fname, pnames)
                .engineTranslateKey(privateKey);
        name = nk.getParams().getName();
        secKey = nk.getRawBytes();
        checkPrivateKey(name, secKey);
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
            return sign0(name, secKey, msg, appRandom);
        } else {
            throw new IllegalStateException("No private key");
        }
    }

    @Override
    protected boolean engineVerify(byte[] sig) throws SignatureException {
        if (pubKey != null) {
            var msg = bout.toByteArray();
            bout.reset();
            return verify0(name, pubKey, msg, sig);
        } else {
            throw new IllegalStateException("No public key");
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void engineSetParameter(String param, Object value) throws InvalidParameterException {
        throw new UnsupportedOperationException("setParameter() not supported");
    }

    @Override
    @SuppressWarnings("deprecation")
    protected Object engineGetParameter(String param) throws InvalidParameterException {
        throw new UnsupportedOperationException("getParameter() not supported");
    }

    @Override
    protected void engineSetParameter(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
        if (params != null) {
            throw new InvalidAlgorithmParameterException("No params needed");
        }
    }

    @Override
    protected AlgorithmParameters engineGetParameters() {
        return null;
    }

    /**
     * User-defined sign function.
     *
     * @param name parameter name
     * @param sk private key in raw bytes
     * @param msg the message
     * @param sr SecureRandom object, null if not initialized
     * @return the signature
     * @throws ProviderException if there is an internal error
     * @throws SignatureException if there is another error
     */
    public abstract byte[] sign0(String name, byte[] sk, byte[] msg, SecureRandom sr)
            throws SignatureException;

    /**
     * User-defined verify function.
     *
     * @param name parameter name
     * @param pk public key in raw bytes
     * @param msg the message
     * @param sig the signature
     * @return true if verified
     * @throws ProviderException if there is an internal error
     * @throws SignatureException if there is another error
     */
    public abstract boolean verify0(String name, byte[] pk, byte[] msg, byte[] sig)
            throws SignatureException;

    /**
     * User-defined function to validate a public key.
     *
     * This method will be called in {@code initVerify}. This gives provider a chance to
     * reject the key so an {@code InvalidKeyException} can be thrown earlier.
     *
     * The default implementation returns with an exception.
     *
     * @param name parameter name
     * @param pk public key in raw bytes
     * @throws InvalidKeyException if the key is invalid
     */
    public void checkPublicKey(String name, byte[] pk) throws InvalidKeyException {
        return;
    }

    /**
     * User-defined function to validate a private key.
     *
     * This method will be called in {@code initSign}. This gives provider a chance to
     * reject the key so an {@code InvalidKeyException} can be thrown earlier.
     *
     * The default implementation returns with an exception.
     *
     * @param name parameter name
     * @param sk public key in raw bytes
     * @throws InvalidKeyException if the key is invalid
     */
    public void checkPrivateKey(String name, byte[] sk) throws InvalidKeyException {
        return;
    }
}
