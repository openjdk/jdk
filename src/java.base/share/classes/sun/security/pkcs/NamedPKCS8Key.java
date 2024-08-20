/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 */

package sun.security.pkcs;

import sun.security.util.DerInputStream;
import sun.security.util.DerValue;
import sun.security.x509.AlgorithmId;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.ProviderException;
import java.security.spec.NamedParameterSpec;

public final class NamedPKCS8Key extends PKCS8Key {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String fname;
    private final transient NamedParameterSpec paramSpec;
    private final byte[] h;

    public NamedPKCS8Key(String fname, String pname, byte[] h) {
        this.fname = fname;
        this.paramSpec = new NamedParameterSpec(pname);
        try {
            this.algid = AlgorithmId.get(pname);
        } catch (NoSuchAlgorithmException e) {
            throw new ProviderException(e);
        }
        this.h = h.clone();

        DerValue val = new DerValue(DerValue.tag_OctetString, h);
        try {
            this.key = val.toByteArray();
        } finally {
            val.clear();
        }
    }

    public NamedPKCS8Key(String fname, byte[] encoded) throws InvalidKeyException {
        super(encoded);
        this.fname = fname;
        try {
            paramSpec = new NamedParameterSpec(algid.getName());
            h = new DerInputStream(key).getOctetString();
        } catch (IOException e) {
            throw new InvalidKeyException("Cannot parse", e);
        }
    }

    @Override
    public String toString() {
        return paramSpec.getName() + " private key";
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
    private void readObject(ObjectInputStream stream)
            throws IOException, ClassNotFoundException {
        throw new InvalidObjectException(
                "NamedPKCS8Key keys are not directly deserializable");
    }
}
