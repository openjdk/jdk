/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.crypto.provider;

import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import javax.crypto.spec.Argon2ParameterSpec;
import static javax.crypto.spec.Argon2ParameterSpec.Version;
import javax.crypto.KDFParameters;
import javax.crypto.KDFSpi;
import javax.crypto.SecretKey;

import static sun.security.provider.ByteArrayAccess.*;

/**
 * This class implements the Password Hashing Algorithm Argon2ID as specified
 * in <a href="https://datatracker.ietf.org/doc/html/rfc9106">RFC 9106</a>.
 *
 * @since 27
 */
public final class Argon2ID extends KDFSpi {

    private static final String TYPE = "ARGON2ID";

    private final Argon2Impl impl;

    private static Argon2ParameterSpec check(AlgorithmParameterSpec s)
            throws InvalidAlgorithmParameterException {
        if (s instanceof Argon2ParameterSpec argon2Params) {
            if (argon2Params.version() != Version.V13) {
                throw new InvalidAlgorithmParameterException
                        ("Unsupported Argon2 version; only V13 accepted");
            }
            return argon2Params;
        } else {
            throw new InvalidAlgorithmParameterException
                    ("Argon2ParameterSpec required");
        }
    }

    public Argon2ID(KDFParameters p)
            throws InvalidAlgorithmParameterException {
        super(null);
        if (p != null) {
            throw new InvalidAlgorithmParameterException
                    ("only null params accepted");
        }
        impl = new Argon2Impl(TYPE);
    }

    @Override
    protected SecretKey engineDeriveKey(String alg,
            AlgorithmParameterSpec derivationSpec)
            throws InvalidAlgorithmParameterException,
            NoSuchAlgorithmException {
        // javax.crypto.KDF checks and rejects null or empty alg, and null
        // derivationSpec
        Argon2ParameterSpec spec = check(derivationSpec);
        byte[] keyBytes = impl.derive(spec);
        return new Argon2DerivedKey(TYPE, spec, keyBytes, alg);
    }

    @Override
    protected byte[] engineDeriveData(AlgorithmParameterSpec derivationSpec)
            throws InvalidAlgorithmParameterException {
        // javax.crypto.KDF checks and rejects null derivationSpec
        return impl.derive(check(derivationSpec));
    }

    @Override
    protected KDFParameters engineGetParameters() {
        return null;
    }
}
