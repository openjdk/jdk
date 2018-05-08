/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ec;

import java.security.interfaces.XECPrivateKey;
import java.util.Optional;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.NamedParameterSpec;

import sun.security.pkcs.PKCS8Key;
import sun.security.x509.AlgorithmId;

public final class XDHPrivateKeyImpl extends PKCS8Key implements XECPrivateKey {

    private static final long serialVersionUID = 1L;

    private AlgorithmParameterSpec paramSpec;

    XDHPrivateKeyImpl(XECParameters params, byte[] k)
        throws InvalidKeyException {

        this.paramSpec = new NamedParameterSpec(params.getName());
        this.algid = new AlgorithmId(params.getOid());
        this.key = k.clone();

        checkLength(params);
    }

    XDHPrivateKeyImpl(byte[] encoded) throws InvalidKeyException {

        decode(encoded);
        XECParameters params = XECParameters.get(
            InvalidKeyException::new, algid);
        paramSpec = new NamedParameterSpec(params.getName());

        checkLength(params);
    }

    void checkLength(XECParameters params) throws InvalidKeyException {

        if (params.getBytes() != this.key.length) {
            throw new InvalidKeyException(
                "key length must be " + params.getBytes());
        }
    }

    public byte[] getK() {
        return key.clone();
    }

    @Override
    public String getAlgorithm() {
        return "XDH";
    }

    @Override
    public AlgorithmParameterSpec getParams() {
        return paramSpec;
    }

    @Override
    public Optional<byte[]> getScalar() {
        return Optional.of(getK());
    }
}

