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

import javax.crypto.DecapsulateException;
import javax.crypto.KEM;
import javax.crypto.KEMSpi;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.NamedParameterSpec;
import java.util.Objects;

public abstract class NamedKEM implements KEMSpi {

    private final String fname; // family name
    private final String[] pnames; // parameter set name, never null or empty

    public NamedKEM(String fname, String... pnames) {
        this.fname = Objects.requireNonNull(fname);
        if (pnames == null || pnames.length == 0) {
            throw new AssertionError("pnames cannot be null or empty");
        }
        this.pnames = pnames;
    }

    String checkName(String name) throws InvalidKeyException  {
        for (var pname : pnames) {
            if (pname.equalsIgnoreCase(name)) {
                return pname;
            }
        }
        throw new InvalidKeyException("Unknown name: " + name);
    }

    // sr might be null
    public abstract byte[][] encap(String name, byte[] pk, SecureRandom sr);
    public abstract byte[] decap(String name, byte[] sk, byte[] encap);
    public abstract int sslen(String name);
    public abstract int clen(String name);

    @Override
    public EncapsulatorSpi engineNewEncapsulator(
            PublicKey publicKey, AlgorithmParameterSpec spec, SecureRandom secureRandom)
            throws InvalidAlgorithmParameterException, InvalidKeyException {
        if (spec != null) {
            throw new InvalidAlgorithmParameterException("No params needed");
        }
        if (!(publicKey instanceof NamedX509Key)) {
            publicKey = (PublicKey) new NamedKeyFactory(fname, pnames).engineTranslateKey(publicKey);
        }
        if (publicKey instanceof NamedX509Key qk) {
            checkName(qk.getParams().getName());
            return getKeyConsumerImpl(this, qk.getParams(), qk.getRawBytes(), secureRandom);
        } else {
            throw new InvalidKeyException("Unknown key: " + publicKey);
        }
    }

    @Override
    public DecapsulatorSpi engineNewDecapsulator(
            PrivateKey privateKey, AlgorithmParameterSpec spec)
            throws InvalidAlgorithmParameterException, InvalidKeyException {
        if (spec != null) {
            throw new InvalidAlgorithmParameterException("No params needed");
        }
        if (!(privateKey instanceof NamedPKCS8Key)) {
            privateKey = (PrivateKey) new NamedKeyFactory(fname, pnames).engineTranslateKey(privateKey);
        }
        if (privateKey instanceof NamedPKCS8Key qk) {
            checkName(qk.getParams().getName());
            return getKeyConsumerImpl(this, qk.getParams(), qk.getRawBytes(), null);
        } else {
            throw new InvalidKeyException("Unknown key: " + privateKey);
        }
    }

    record KeyConsumerImpl(NamedKEM kem, String name, int sslen, int clen, byte[] key, SecureRandom sr)
            implements KEMSpi.EncapsulatorSpi, KEMSpi.DecapsulatorSpi {
        @Override
        public SecretKey engineDecapsulate(byte[] encapsulation, int from, int to, String algorithm)
                throws DecapsulateException {
            if (encapsulation.length != clen) {
                throw new DecapsulateException("Invalid key encapsulation message length");
            }
            var ss = kem.decap(name, key, encapsulation);
            return new SecretKeySpec(ss,
                    from, to - from, algorithm);
        }

        @Override
        public KEM.Encapsulated engineEncapsulate(int from, int to, String algorithm) {
            var enc = kem.encap(name, key, sr);
            return new KEM.Encapsulated(
                    new SecretKeySpec(enc[1],
                            from, to - from, "Generic"),
                    enc[0],
                    null);
        }

        @Override
        public int engineSecretSize() {
            return sslen;
        }

        @Override
        public int engineEncapsulationSize() {
            return clen;
        }
    }

    private static KeyConsumerImpl getKeyConsumerImpl(
            NamedKEM kem, NamedParameterSpec nps, byte[] key, SecureRandom sr) {
        String name = nps.getName();
        return new KeyConsumerImpl(kem, name, kem.sslen(name), kem.clen(name), key, sr);
    }
}
