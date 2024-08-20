/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 */

package sun.security.provider;

import sun.security.pkcs.NamedPKCS8Key;
import sun.security.x509.NamedX509Key;

import java.security.*;
import java.security.spec.*;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

// This factory can read from a RAW key using translateKey() if key.getFormat() is "RAW",
// and write to a RAW EncodedKeySpec using getKeySpec(key, EncodedKeySpec).
public abstract class NamedKeyFactory extends KeyFactorySpi {

    private final String fname; // family name
    private final String pname; // parameter set name, can be null

    public NamedKeyFactory(String fname, String pname) {
        this.fname = Objects.requireNonNull(fname);
        this.pname = pname;
    }

    private void checkName(String name) throws InvalidKeySpecException {
        if (pname != null) {
            if (!name.equalsIgnoreCase(pname)) {
                throw new InvalidKeySpecException("not name");
            }
        }
        if (!name.toUpperCase(Locale.ROOT).startsWith(fname.toUpperCase(Locale.ROOT))) {
            throw new InvalidKeySpecException(name + " not in family " + fname);
        }
    }

    @Override
    protected PublicKey engineGeneratePublic(KeySpec keySpec) throws InvalidKeySpecException {
        if (keySpec instanceof X509EncodedKeySpec xspec) {
            try {
                return fromX509(xspec.getEncoded());
            } catch (InvalidKeyException e) {
                throw new InvalidKeySpecException(e);
            }
        } else {
            throw new InvalidKeySpecException("Unsupported keyspec: " + keySpec);
        }
    }

    @Override
    protected PrivateKey engineGeneratePrivate(KeySpec keySpec) throws InvalidKeySpecException {
        if (keySpec instanceof PKCS8EncodedKeySpec pspec) {
            var bytes = pspec.getEncoded();
            try {
                return fromPKCS8(bytes);
            } catch (InvalidKeyException e) {
                throw new InvalidKeySpecException(e);
            } finally {
                Arrays.fill(bytes, (byte)0);
            }
        } else {
            throw new InvalidKeySpecException("Unsupported keyspec: " + keySpec);
        }
    }

    private PrivateKey fromPKCS8(byte[] bytes) throws InvalidKeyException, InvalidKeySpecException {
        var k = new NamedPKCS8Key(fname, bytes);
        checkName(k.getParams().getName());
        return k;
    }

    private PublicKey fromX509(byte[] bytes) throws InvalidKeyException, InvalidKeySpecException {
        var k = new NamedX509Key(fname, bytes);
        checkName(k.getParams().getName());
        return k;
    }

    private static class RawEncodedKeySpec extends EncodedKeySpec {
        public RawEncodedKeySpec(byte[] encodedKey) {
            super(encodedKey);
        }

        @Override
        public String getFormat() {
            return "RAW";
        }
    }

    @Override
    protected <T extends KeySpec> T engineGetKeySpec(Key key, Class<T> keySpec) throws InvalidKeySpecException {
        if (key instanceof AsymmetricKey ak) {
            if (ak.getParams() instanceof NamedParameterSpec nps) { // what if not?
                checkName(nps.getName());
            }
            if (key instanceof PrivateKey) {
                if (keySpec == PKCS8EncodedKeySpec.class
                        && key.getFormat().equalsIgnoreCase("PKCS#8")) {
                    var bytes = key.getEncoded();
                    try {
                        return keySpec.cast(new PKCS8EncodedKeySpec(bytes));
                    } finally {
                        Arrays.fill(bytes, (byte) 0);
                    }
                } else if (keySpec.isAssignableFrom(EncodedKeySpec.class)
                        && key instanceof NamedPKCS8Key nk) {
                    return keySpec.cast(new RawEncodedKeySpec(nk.getRawBytes()));
                }
            } else if (key instanceof PublicKey) {
                if (keySpec == X509EncodedKeySpec.class
                        && key.getFormat().equalsIgnoreCase("X.509")) {
                    return keySpec.cast(new X509EncodedKeySpec(key.getEncoded()));
                } else if (keySpec.isAssignableFrom(EncodedKeySpec.class)
                        && key instanceof NamedX509Key nk) {
                    return keySpec.cast(new RawEncodedKeySpec(nk.getRawBytes()));
                }
            }
        }

        throw new InvalidKeySpecException("Unsupported keyspec: " + keySpec);
    }

    @Override
    protected Key engineTranslateKey(Key key) throws InvalidKeyException {
        if (key == null) throw new InvalidKeyException("Key must not be null");
        if (key instanceof NamedPKCS8Key || key instanceof NamedX509Key) {
            // Need check algorithm and parameters?
            return key;
        }
        var format = key.getFormat();
        byte[] bytes = null;
        try {
            if (format.equalsIgnoreCase("RAW")) {
                if (pname == null) {
                    throw new InvalidKeyException("Only KeyFactory instantiated with a parameter set name can translate a RAW key");
                }
                var n = key.getAlgorithm();
                if (!n.equalsIgnoreCase(fname) && !n.equalsIgnoreCase(pname)) { // some vendor uses params set name
                    throw new InvalidKeyException("algorithm must be " + fname + " or " + pname + ", it's " + n);
                }
                if (key instanceof PrivateKey) {
                    bytes = key.getEncoded();
                    return new NamedPKCS8Key(fname, pname, bytes);
                } else if (key instanceof PublicKey) {
                    return new NamedX509Key(fname, pname, key.getEncoded());
                } else {
                    throw new InvalidKeyException("Unsupported key type: " + key.getClass());
                }
            } else if (format.equalsIgnoreCase("PKCS#8") && key instanceof PrivateKey) {
                try {
                    bytes = key.getEncoded();
                    return fromPKCS8(bytes);
                } catch (InvalidKeySpecException e) {
                    throw new InvalidKeyException("Invalid PKCS#8 key", e);
                }
            } else if (format.equalsIgnoreCase("X.509") && key instanceof PublicKey) {
                try {
                    return fromX509(key.getEncoded());
                } catch (InvalidKeySpecException e) {
                    throw new InvalidKeyException("Invalid X.509 key", e);
                }
            } else {
                throw new InvalidKeyException("Unknown key format: " + key.getFormat());
            }
        } finally {
            if (bytes != null) Arrays.fill(bytes, (byte)0);
        }
    }
}
