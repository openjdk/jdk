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

import sun.security.jca.JCAUtil;
import sun.security.pkcs.NamedPKCS8Key;
import sun.security.x509.NamedX509Key;

import java.io.ByteArrayOutputStream;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.security.SecureRandom;

public class ML_DSA extends SignatureSpi {

    public ML_DSA() {
        this(-1);
    }

    public static class ML_DSA2 extends ML_DSA {
        public ML_DSA2() {
            super(2);
        }
    }

    public static class ML_DSA3 extends ML_DSA {
        public ML_DSA3() {
            super(3);
        }
    }
    public static class ML_DSA5 extends ML_DSA {
        public ML_DSA5() {
            super(5);
        }
    }

    static int name2int(String name) {
        if (name.endsWith("44")) return 2;
        else if (name.endsWith("65")) return 3;
        else if (name.endsWith("87")) return 5;
        else throw new ProviderException();
    }

    public static class KPG extends NamedKeyPairGenerator {
        public KPG() {
            this(null);
        }

        public KPG(String pname) {
            super("ML-DSA", pname);
        }

        @Override
        public byte[][] generateKeyPair0(String name, SecureRandom sr) {
            byte[] seed = new byte[32];
            var r = sr != null ? sr : JCAUtil.getDefSecureRandom();
            r.nextBytes(seed);
            Dilithium dilithium = new Dilithium(name2int(name));
            Dilithium.DilithiumKeyPair kp = dilithium.generateKeyPair(seed);
            return new byte[][] {
                    dilithium.pkEncode(kp.publicKey()),
                    dilithium.skEncode(kp.privateKey()) };
        }
    }

    public static class KPG2 extends KPG {
        public KPG2() {
            super("ML-DSA-44");
        }
    }

    public static class KPG3 extends KPG {
        public KPG3() {
            super("ML-DSA-65");
        }
    }

    public static class KPG5 extends KPG {
        public KPG5() {
            super("ML-DSA-87");
        }
    }

    public static class KF extends NamedKeyFactory {
        public KF() {
            this(null);
        }
        public KF(String name) {
            super("ML-DSA", name);
        }
    }

    public static class KF2 extends KF {
        public KF2() {
            super("ML-DSA-44");
        }
    }

    public static class KF3 extends KF {
        public KF3() {
            super("ML-DSA-65");
        }
    }

    public static class KF5 extends KF {
        public KF5() {
            super("ML-DSA-87");
        }
    }

    //
    //Implement SignatureSPI methods
    //

    private final int lockedSize;
    private final ByteArrayOutputStream buffer;

    private Dilithium dilithium;
    private int size;
    private Dilithium.DilithiumPrivateKey sk;
    private Dilithium.DilithiumPublicKey pk;


    public ML_DSA(int lockedSize) {
        buffer = new ByteArrayOutputStream();
        this.lockedSize = lockedSize;
    }

    protected void engineInitSign(PrivateKey privateKey)
        throws InvalidKeyException {
        if (!(privateKey instanceof NamedPKCS8Key mk)) {
            throw new InvalidKeyException("not an ML_DSA private key: " + privateKey);
        }
        //might need to translate key with keyfactory
        //todo do we need other checks?
        size = name2int(mk.getParams().getName());
        if (lockedSize != -1 && lockedSize != size) throw new InvalidKeyException("Not the same size");
        dilithium = new Dilithium(size);
        sk = dilithium.skDecode(mk.getRawBytes());
        buffer.reset();
    }

    protected void engineInitVerify(PublicKey publicKey)
        throws InvalidKeyException {
        if (!(publicKey instanceof NamedX509Key mk)) {
            throw new InvalidKeyException("Not an ML_DSA public key: " + publicKey);
        }
        //might need to translate key with keyfactory
        //todo do we need other checks?
        size = name2int(mk.getParams().getName());
        if (lockedSize != -1 && lockedSize != size) throw new InvalidKeyException("Not the same size");
        dilithium = new Dilithium(size);

        pk = dilithium.pkDecode(mk.getRawBytes());
        buffer.reset();
    }

    protected void engineUpdate(byte b) { buffer.write(b); }

    protected void engineUpdate(byte[] data, int off, int len) { buffer.write(data, off, len); } //Does signature class fail if init hasn't been called

    protected byte[] engineSign() throws SignatureException {
        byte[] message = buffer.toByteArray();
        buffer.reset();
        Dilithium.DilithiumSignature sig = dilithium.sign(message, sk);
        return dilithium.sigEncode(sig);
    }

    protected boolean engineVerify(byte[] signature) {
        byte[] message = buffer.toByteArray();
        buffer.reset();
        Dilithium.DilithiumSignature sig = dilithium.sigDecode(signature);
        return dilithium.verify(pk, message, sig);
    }

    @Deprecated
    protected void engineSetParameter(String key, Object param) {
        throw new InvalidParameterException("No parameter accepted");
    }

    @Override
    protected void engineSetParameter(AlgorithmParameterSpec params)
        throws InvalidAlgorithmParameterException {
        if (params != null) {
            throw new InvalidAlgorithmParameterException("No parameter accepted");
        }
    }

    @Deprecated
    protected Object engineGetParameter(String key) {
        return null;
    }

    @Override
    protected AlgorithmParameters engineGetParameters() {
        return null;
    }
}
