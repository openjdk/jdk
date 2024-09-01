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

import sun.security.pkcs.NamedPKCS8Key;
import sun.security.x509.NamedX509Key;

import java.security.*;
import java.security.spec.*;
import java.util.Arrays;
import java.util.Objects;

// This factory can read from a RAW key using translateKey() if key.getFormat() is "RAW",
// and write to a RAW EncodedKeySpec using getKeySpec(key, EncodedKeySpec).
public class NamedKeyFactory extends KeyFactorySpi {

    private final String fname; // family name
    private final String[] pnames; // parameter set name, never null or empty

    public NamedKeyFactory(String fname, String... pnames) {
        this.fname = Objects.requireNonNull(fname);
        if (pnames == null || pnames.length == 0) {
            throw new AssertionError("pnames cannot be null or empty");
        }
        this.pnames = pnames;
    }

    String checkName(String name) throws InvalidKeySpecException  {
        for (var pname : pnames) {
            if (pname.equalsIgnoreCase(name)) {
                return pname;
            }
        }
        throw new InvalidKeySpecException("Unknown name: " + name);
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
                String kAlg = key.getAlgorithm();
                if (key instanceof AsymmetricKey pk) {
                    String name;
                    if (pk.getParams() instanceof NamedParameterSpec nps) {
                        name = nps.getName();
                        try {
                            name = checkName(name);
                        } catch (InvalidKeySpecException e) {
                            throw new InvalidKeyException("This factory does not accept " + name);
                        }
                    } else {
                        if (kAlg.equalsIgnoreCase(fname)) {
                            if (pnames.length == 1) {
                                name = pnames[0];
                            } else {
                                throw new InvalidKeyException("No parameter set info");
                            }
                        } else {
                            try {
                                name = checkName(kAlg);
                            } catch (InvalidKeySpecException e) {
                                throw new InvalidKeyException("This factory does not accept " + kAlg);
                            }
                        }
                    }
                    bytes = key.getEncoded();
                    return key instanceof PrivateKey
                            ? new NamedPKCS8Key(fname, name, bytes)
                            : new NamedX509Key(fname, name, key.getEncoded());
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
