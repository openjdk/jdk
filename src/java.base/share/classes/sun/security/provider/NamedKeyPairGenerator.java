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
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.NamedParameterSpec;
import java.util.Arrays;
import java.util.Objects;

public abstract class NamedKeyPairGenerator extends KeyPairGeneratorSpi {

    private final String fname; // family name
    private final String[] pnames; // allowed parameter set name, need at least one

    protected String name = null; // init as
    private SecureRandom secureRandom;

    public NamedKeyPairGenerator(String fname, String... pnames) {
        this.fname = Objects.requireNonNull(fname);
        if (pnames == null || pnames.length == 0) {
            throw new AssertionError("pnames cannot be null or empty");
        }
        this.pnames = pnames;
    }

    private String checkName(String name) throws InvalidAlgorithmParameterException  {
        for (var pname : pnames) {
            if (pname.equalsIgnoreCase(name)) {
                // return the stored pname, name should be sTrAnGe.
                return pname;
            }
        }
        throw new InvalidAlgorithmParameterException("Unknown parameter set name: " + name);
    }

    @Override
    public void initialize(AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidAlgorithmParameterException {
        if (params instanceof NamedParameterSpec spec) {
            name = checkName(spec.getName());
        } else {
            throw new InvalidAlgorithmParameterException(
                    "Unknown AlgorithmParameterSpec: " + params);
        }
        this.secureRandom = random ;
    }

    private String currentParamSet() throws IllegalStateException {
        if (name != null) {
            return name;
        }
        if (pnames.length == 1) {
            return pnames[0];
        }
        throw new IllegalStateException("No default parameter set");
    }

    @Override
    public void initialize(int keysize, SecureRandom random) {
        if (keysize != -1) {
            throw new InvalidParameterException("keysize not supported");
        }
        this.secureRandom = random;
    }

    @Override
    public KeyPair generateKeyPair() {
        String pname = currentParamSet();
        var keys = generateKeyPair0(pname, secureRandom);
        try {
            return new KeyPair(new NamedX509Key(fname, pname, keys[0]),
                    new NamedPKCS8Key(fname, pname, keys[1]));
        } finally {
            Arrays.fill(keys[0], (byte)0);
            Arrays.fill(keys[1], (byte)0);
        }
    }

    /**
     * User-defined key pair generator.
     *
     * @param pname parametert set name
     * @param sr SecureRandom object, null if not initialized
     * @return public key and private key (in this order) in raw bytes
     * @throws ProviderException if there is an internal error
     */
    public abstract byte[][] generateKeyPair0(String pname, SecureRandom sr);
}
