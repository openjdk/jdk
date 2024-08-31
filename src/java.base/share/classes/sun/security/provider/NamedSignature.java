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

import sun.security.jca.JCAUtil;
import sun.security.pkcs.NamedPKCS8Key;
import sun.security.x509.NamedX509Key;

import java.io.ByteArrayOutputStream;
import java.security.*;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Locale;
import java.util.Objects;

public abstract class NamedSignature extends SignatureSpi {

    // TODO: SecureRandom
    private String name = null;
    private byte[] secKey = null;
    private byte[] pubKey = null;

    private final ByteArrayOutputStream bout = new ByteArrayOutputStream();
    private final String fname; // family name
    private final String pname; // parameter set name, can be null

    public NamedSignature(String fname, String pname) {
        this.fname = Objects.requireNonNull(fname);
        this.pname = pname;
    }

    public abstract byte[] sign0(String name, byte[] sk, byte[] msg, SecureRandom sr);
    public abstract boolean verify0(String name, byte[] pk, byte[] msg, byte[] sig);

    private void checkName(String name) throws InvalidKeyException {
        if (pname != null) {
            if (!name.equalsIgnoreCase(pname)) {
                throw new InvalidKeyException("not name");
            }
        }
        if (!name.toUpperCase(Locale.ROOT).startsWith(fname.toUpperCase(Locale.ROOT))) {
            throw new InvalidKeyException(name + " not in family " + fname);
        }
    }

    @Override
    protected void engineInitVerify(PublicKey publicKey) throws InvalidKeyException {
        if (publicKey instanceof NamedX509Key nk) {
            checkName(name = nk.getParams().getName());
            pubKey = nk.getRawBytes();
        } else {
            throw new InvalidKeyException("Unknown key: " + publicKey);
        }
    }

    @Override
    protected void engineInitSign(PrivateKey privateKey) throws InvalidKeyException {
        if (privateKey instanceof NamedPKCS8Key nk) {
            checkName(name = nk.getParams().getName());
            secKey = nk.getRawBytes();
        } else {
            throw new InvalidKeyException("Unknown key: " + privateKey);
        }
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
            return sign0(name, secKey, msg, appRandom != null ? appRandom : JCAUtil.getDefSecureRandom());
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
        throw new InvalidParameterException("No params needed");
    }

    @Override
    @SuppressWarnings("deprecation")
    protected Object engineGetParameter(String param) throws InvalidParameterException {
        throw new InvalidParameterException("No params needed");
    }

    @Override
    protected void engineSetParameter(AlgorithmParameterSpec params)
            throws InvalidAlgorithmParameterException {
        throw new InvalidAlgorithmParameterException("No params needed");
    }

    @Override
    protected AlgorithmParameters engineGetParameters() {
        return null;
    }
}
