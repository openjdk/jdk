/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ec.ed;

import jdk.internal.ref.CleanerFactory;
import sun.security.pkcs.PKCS8Key;
import sun.security.util.DerInputStream;
import sun.security.util.DerValue;
import sun.security.x509.AlgorithmId;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.lang.ref.Cleaner.Cleanable;
import java.lang.ref.Reference;
import java.security.InvalidKeyException;
import java.security.interfaces.EdECPrivateKey;
import java.security.spec.NamedParameterSpec;
import java.util.Arrays;
import java.util.Optional;

public final class EdDSAPrivateKeyImpl
        extends PKCS8Key implements EdECPrivateKey {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("serial") // Type of field is not Serializable
    private final NamedParameterSpec paramSpec;
    private byte[] h;

    private transient Cleanable cleanable;

    EdDSAPrivateKeyImpl(EdDSAParameters params, byte[] h)
            throws InvalidKeyException {

        this.paramSpec = new NamedParameterSpec(params.getName());
        this.algid = new AlgorithmId(params.getOid());
        this.h = h.clone();

        DerValue val = new DerValue(DerValue.tag_OctetString, h);
        try {
            privKeyMaterial = val.toByteArray();
        } finally {
            val.clear();
        }
        checkLength(params);

        final byte[] lh = this.h;
        cleanable = CleanerFactory.cleaner().register(this,
                                                      () -> Arrays.fill(lh,
                                                                        (byte) 0x00));
    }

    EdDSAPrivateKeyImpl(byte[] encoded) throws InvalidKeyException {

        super(encoded);
        EdDSAParameters params = EdDSAParameters.get(
            InvalidKeyException::new, algid);
        paramSpec = new NamedParameterSpec(params.getName());

        try {
            DerInputStream derStream = new DerInputStream(privKeyMaterial);
            h = derStream.getOctetString();
        } catch (IOException ex) {
            throw new InvalidKeyException(ex);
        }
        checkLength(params);

        final byte[] lh = this.h;
        cleanable = CleanerFactory.cleaner().register(this,
                                                      () -> Arrays.fill(lh,
                                                                        (byte) 0x00));
    }

    /**
     * Clears the internal copy of the key.
     *
     */
    @Override
    public void destroy() {
        super.destroy();
        if (cleanable != null) {
            cleanable.clean();
            cleanable = null;
        }
    }

    @Override
    public boolean isDestroyed() {
        return (cleanable == null);
    }

    void checkLength(EdDSAParameters params) throws InvalidKeyException {

        if (params.getKeyLength() != h.length) {
            throw new InvalidKeyException("key length is " + h.length +
                ", key length must be " + params.getKeyLength());
        }
    }

    public byte[] getKey() {
        try {
            if (isDestroyed()) {
                throw new IllegalStateException("Key is destroyed");
            }
            return h.clone();
        } finally {
            // prevent this from being cleaned for the above block
            Reference.reachabilityFence(this);
        }
    }

    @Override
    public String getAlgorithm() {
        return "EdDSA";
    }

    @Override
    public NamedParameterSpec getParams() {
        return paramSpec;
    }

    @Override
    public Optional<byte[]> getBytes() {
        return Optional.of(getKey());
    }

    /**
     * Restores the state of this object from the stream.
     * <p>
     * Deserialization of this object is not supported.
     *
     * @param  stream the {@code ObjectInputStream} from which data is read
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if a serialized class cannot be loaded
     */
    @java.io.Serial
    private void readObject(ObjectInputStream stream)
            throws IOException, ClassNotFoundException {
        throw new InvalidObjectException(
                "EdDSAPrivateKeyImpl keys are not directly deserializable");
    }
}
