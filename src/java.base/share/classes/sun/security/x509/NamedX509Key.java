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

package sun.security.x509;

import sun.security.util.BitArray;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.security.InvalidKeyException;
import java.security.KeyRep;
import java.security.NoSuchAlgorithmException;
import java.security.ProviderException;
import java.security.spec.NamedParameterSpec;

public final class NamedX509Key extends X509Key {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String fname;
    private final transient NamedParameterSpec paramSpec;
    private final byte[] h;

    public NamedX509Key(String fname, String pname, byte[] h) {
        this.fname = fname;
        this.paramSpec = new NamedParameterSpec(pname);
        try {
            this.algid = AlgorithmId.get(pname);
        } catch (NoSuchAlgorithmException e) {
            throw new ProviderException(e);
        }
        this.h = h.clone();

        setKey(new BitArray(h.length * 8, h));
    }

    public NamedX509Key(String fname, byte[] encoded) throws InvalidKeyException {
        this.fname = fname;
        decode(encoded);
        paramSpec = new NamedParameterSpec(algid.getName());
        h = getKey().toByteArray();
    }

    @Override
    public String toString() {
        return paramSpec.getName() + " public key";
    }

    public byte[] getRawBytes() {
        return h.clone();
    }

    @Override
    public NamedParameterSpec getParams() {
        return paramSpec;
    }

    @Override
    public String getAlgorithm() {
        return fname;
    }

    @java.io.Serial
    private Object writeReplace() throws java.io.ObjectStreamException {
        return new KeyRep(KeyRep.Type.PUBLIC, getAlgorithm(), getFormat(),
                getEncoded());
    }

    @java.io.Serial
    private void readObject(ObjectInputStream stream)
            throws IOException, ClassNotFoundException {
        throw new InvalidObjectException(
                "NamedX509Key keys are not directly deserializable");
    }
}

