/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
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

