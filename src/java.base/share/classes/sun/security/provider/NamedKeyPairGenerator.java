/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 */

package sun.security.provider;

import sun.security.pkcs.NamedPKCS8Key;
import sun.security.x509.NamedX509Key;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidParameterException;
import java.security.KeyPair;
import java.security.KeyPairGeneratorSpi;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.NamedParameterSpec;
import java.util.Arrays;
import java.util.Objects;

public abstract class NamedKeyPairGenerator extends KeyPairGeneratorSpi {

    private final String fname; // family name
    private final String pname; // parameter set name, can be null

    public NamedKeyPairGenerator(String fname, String pname) {
        this.fname = Objects.requireNonNull(fname);
        this.pname = pname;
    }

    private String name = null; // init as
    private SecureRandom secureRandom;

    @Override
    public void initialize(AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidAlgorithmParameterException {
        if (params instanceof NamedParameterSpec spec) {
            name = spec.getName();
            if (pname != null && !pname.equalsIgnoreCase(name)) {
                throw new InvalidAlgorithmParameterException("Fixed");
            }
        } else {
            throw new InvalidAlgorithmParameterException(
                    "Unknown AlgorithmParameterSpec: " + params);
        }
        this.secureRandom = random ;
    }

    private String findName() throws IllegalStateException {
        if (name != null) return name;
        if (pname != null) return pname;
        throw new IllegalStateException("No default parameter set");
    }

    @Override
    public void initialize(int keysize, SecureRandom random) {
        if (keysize != -1) throw new InvalidParameterException("keysize not supported");
        this.secureRandom = random;
    }

    @Override
    public KeyPair generateKeyPair() {
        String name = findName();
        var keys = generateKeyPair0(name, secureRandom);
        try {
            return new KeyPair(new NamedX509Key(fname, name, keys[0]),
                    new NamedPKCS8Key(fname, name, keys[1]));
        } finally {
            Arrays.fill(keys[0], (byte)0);
            Arrays.fill(keys[1], (byte)0);
        }
    }

    // Users defined generator, sr could be null
    public abstract byte[][] generateKeyPair0(String name, SecureRandom sr);
}
