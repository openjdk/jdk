package com.sun.crypto.provider;

import java.io.Serial;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.security.SecureRandom;
import java.security.interfaces.*;
import java.security.spec.*;
import sun.security.provider.Dilithium;

abstract class ML_DSA extends SignatureSpi {

    public static class ML_DSA_PublicKey implements PublicKey {
        @Serial
        private static final long serialVersionUID = 24L;
        int size;
        byte[] keyBytes;

        public ML_DSA_PublicKey(int size, byte[] keyBytes) {
            //todo check size constraints

            this.size = size;
            this.keyBytes = keyBytes.clone();
        }

        @Override
        public String getAlgorithm() {
            return "ML-DSA";
        }

        @Override
        public String getFormat() {
            return "X.509";
        }

        @Override
        public byte[] getEncoded() {
            return keyBytes.clone();
        } //X.509 key class
    }

    public static class ML_DSA_PrivateKey implements PrivateKey { //these need to override getParameters like EdDSA to return NamedParameterSpc
        @Serial
        private static final long serialVersionUID = 24L;
        int size;
        byte[] keyBytes;

        public ML_DSA_PrivateKey(int size, byte[] keyBytes) {
            //todo: check size constraints

            this.size = size;
            this.keyBytes = keyBytes.clone();
        }

        @Override
        public String getAlgorithm() {
            return "ML-DSA";
        }

        @Override
        public String getFormat() {
            return "PKCS#8";
        }

        @Override
        public byte[] getEncoded() {
            return keyBytes.clone();
        } //Der encoding of SK use PKCS#8 class
    }

    public static record MlDsaAlgorithmParameterSpec
        (int size, boolean useIntrinsics, long flags)
        implements AlgorithmParameterSpec {
    }

    public static class KeyPairGenerator extends KeyPairGeneratorSpi {

        String name = "ML-DSA KeyPairGenerator";
        private int size = 0;
        private SecureRandom random = null;

        public KeyPairGenerator() {}

        public KeyPairGenerator(int size, SecureRandom random) { initialize(size, random); }

        @Override
        public void initialize(int keysize, SecureRandom random) {
            if (size != 0) { //Can pass in -1 for keysize to indicate using user-supplied random
                throw new InvalidParameterException(
                    "This generator has already been initialized.");
            }
            if (random == null) {
                random = new SecureRandom();
            }
            this.size = keysize;
            this.random = random;
        }

        public void initialize(AlgorithmParameterSpec params, SecureRandom random) { //AlgorithmParameterSpec uses NamedParameterSpec
            if (size != 0) {
                throw new InvalidParameterException(
                    "This generator has already been initialized.");
            }

            //Change to use NamedParameterSpec
            if (!(params instanceof ML_DSA.MlDsaAlgorithmParameterSpec mlDsaParams)) {
                throw new InvalidParameterException(
                    "Bad AlgorithmParameterSpec.");
            }

            if (random == null) {
                random = new SecureRandom();
            }
            this.size = mlDsaParams.size;
            this.random = random;
        }

        //getInstance for each parameter set name

        @Override
        public KeyPair generateKeyPair() {
            byte[] seed = new byte[32];
            random.nextBytes(seed);
            Dilithium dilithium = new Dilithium(size);
            Dilithium.DilithiumKeyPair kp = dilithium.generateKeyPair(seed);
            ML_DSA_PublicKey pk = new ML_DSA_PublicKey(size, dilithium.pkEncode(kp.publicKey()));
            ML_DSA_PrivateKey sk = new ML_DSA_PrivateKey(size, dilithium.skEncode(kp.privateKey()));
            return new KeyPair(pk,sk);
        }
    }

    //
    //Implement SignatureSPI methods
    //

    private Dilithium dilithium;

    private Dilithium.DilithiumPrivateKey sk;

    private Dilithium.DilithiumPublicKey pk;

    private MessageDigest messageDigest;

    public ML_DSA(MessageDigest md, int size) {
        messageDigest = md;
        dilithium = new Dilithium(size);
    }

    protected void engineInitSign(PrivateKey privateKey)
        throws InvalidKeyException {
        if (!(privateKey instanceof ML_DSA_PrivateKey)) {
            throw new InvalidKeyException("not an ML_DSA private key: " + privateKey);
        }
        //might need to translate key with keyfactory
        //todo do we need other checks?

        sk = dilithium.skDecode(privateKey.getEncoded());
        messageDigest.reset();
    }

    protected void engineInitVerify(PublicKey publicKey)
        throws InvalidKeyException {
        if (!(publicKey instanceof ML_DSA_PublicKey)) {
            throw new InvalidKeyException("Not an ML_DSA public key: " + publicKey);
        }
        //might need to translate key with keyfactory
        //todo do we need other checks?

        pk = dilithium.pkDecode(publicKey.getEncoded());
        messageDigest.reset();
    }

    protected void engineUpdate(byte b) { messageDigest.update(b); }

    protected void engineUpdate(byte[] data, int off, int len) { messageDigest.update(data, off, len); } //Does signature class fail if init hasn't been called

    protected void engineUpdate(ByteBuffer b) { messageDigest.update(b); }

    protected byte[] engineSign() throws SignatureException {
        byte[] message = messageDigest.digest();
        Dilithium.DilithiumSignature sig = dilithium.sign(message, sk);
        return dilithium.sigEncode(sig);
    }

    protected boolean engineVerify(byte[] signature) {
        byte[] message = messageDigest.digest();
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
