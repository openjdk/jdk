/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import javax.crypto.spec.HPKEParameterSpec;
import java.io.IOException;
import java.security.AlgorithmParametersSpi;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;

/**
 * This AlgorithmParametersSpi only supports HPKEParameterSpec.
 * There is no ASN.1 format defined.
 */
public class HPKEParameters extends AlgorithmParametersSpi {

    private HPKEParameterSpec spec;

    @Override
    protected void engineInit(AlgorithmParameterSpec paramSpec)
            throws InvalidParameterSpecException {
        if (!(paramSpec instanceof HPKEParameterSpec hspec)) {
            throw new InvalidParameterSpecException("Not an HPKEParameterSpec");
        }
        this.spec = hspec;
    }

    @Override
    protected void engineInit(byte[] params) throws IOException {
        throw new IOException(
                "HPKE does not support parameters as a byte array.");
    }

    @Override
    protected void engineInit(byte[] params, String format) throws IOException {
        throw new IOException(
                "HPKE does not support parameters as a byte array.");
    }

    @Override
    protected <T extends AlgorithmParameterSpec> T engineGetParameterSpec(
            Class<T> paramSpec) throws InvalidParameterSpecException {

        if (paramSpec.isAssignableFrom(HPKEParameterSpec.class)) {
            return paramSpec.cast(spec);
        }
        throw new InvalidParameterSpecException(
                "Only HPKEParameterSpec supported.");
    }

    @Override
    protected byte[] engineGetEncoded() throws IOException {
        throw new IOException(
                "HPKE does not support parameters as a byte array.");
    }

    @Override
    protected byte[] engineGetEncoded(String format) throws IOException {
        throw new IOException(
                "HPKE does not support parameters as a byte array.");
    }

    @Override
    protected String engineToString() {
        return "HPKE";
    }
}
