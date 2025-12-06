package com.sun.crypto.provider;

import sun.security.ec.XECOperations;
import sun.security.ec.XECParameters;
import sun.security.provider.NamedKeyFactory;
import sun.security.provider.SHA3;

import javax.security.auth.Destroyable;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.util.Arrays;

public class XWingKeyFactory extends NamedKeyFactory {

    public XWingKeyFactory() {
        super("X-Wing", "X-Wing");
    }

    @Override
    protected byte[] implExpand(String pname, byte[] input) throws InvalidKeyException {
        if (input == null || input.length != 32) {
            throw new InvalidKeyException("X-Wing private key seed must be 32 bytes long");
        }
        var privateKey = XWingPrivateKey.of(input);
        try {
            return privateKey.expanded();
        } finally {
            privateKey.destroy();
        }
    }

    /// The public key parts `pk_X` and `pk_M`
    interface XWingPublicKey {

        static XWingPublicKey of(byte[] m, byte[] x) {
            record XWingPublicKeyImpl(byte[] m, byte[] x) implements XWingPublicKey {}
            return new XWingPublicKeyImpl(m, x);
        }

        /// returns the ML-KEM public key part (`pk_M = pk[0:1184]`)
        /// @return subkey bytes of length 1184
        byte[] m();

        /// returns the X25519 public key part (`pk_X = pk[1184:1216]`)
        /// @return subkey bytes of length 32
        byte[] x();
    }

    /// The public key parts `sk_X` and `sk_M`
    interface XWingPrivateKey extends Destroyable {

        /// derive X-Wing private key from a 32-byte seed
        static XWingPrivateKey of(byte[] seed) {
            // see https://www.ietf.org/archive/id/draft-connolly-cfrg-xwing-kem-08.html#section-5.2
            assert seed.length == 32; // guaranteed by the caller

            var expanded = shake256(seed, 96);
            byte[] dz = Arrays.copyOfRange(expanded, 0, 64); // 32 byte d + 32 byte z
            byte[] skX = Arrays.copyOfRange(expanded, 64, 96);
            Arrays.fill(expanded, (byte) 0);

            var skM = ML_KEM_Impls.seedToExpanded("ML-KEM-768", dz);
            return of(skM, skX);
        }

        static XWingPrivateKey of(byte[] m, byte[] x) {
            record XWingPrivateKeyImpl(byte[] m, byte[] x) implements XWingPrivateKey {}
            return new XWingPrivateKeyImpl(m, x);
        }

        /// returns the ML-KEM private key part (`sk_M = sk[0:1184]`)
        /// @return subkey bytes of length 1184
        byte[] m();

        /// returns the X25519 private key part (`sk_X = sk[1184:1216]`)
        /// @return subkey bytes of length 32
        byte[] x();

        /// @return a new byte array containing the concatenation of `sk_M` and `sk_X`
        default byte[] expanded() {
            byte[] skM = m();
            byte[] skX = x();
            byte[] output = new byte[skM.length + skX.length];
            System.arraycopy(skM, 0, output, 0, skM.length);
            System.arraycopy(skX, 0, output, skM.length, skX.length);
            return output;
        }

        @Override
        default void destroy() {
            Arrays.fill(m(), (byte) 0);
            Arrays.fill(x(), (byte) 0);
        }

        default XWingPublicKey derivePublicKey() {
            byte[] pkM = new ML_KEM("ML-KEM-768").privKeyToPubKey(m());

            // applying x25519 on secret key and base point yields the public key:
            var x25519 = new XECOperations(XECParameters.X25519);
            byte[] pkX = x25519.encodedPointMultiply(x(), BigInteger.valueOf(XECParameters.X25519.getBasePoint()));
            return XWingPublicKey.of(pkM, pkX);
        }
    }

    private static byte[] shake256(byte[] input, int byteLength) {
        var digest = new SHA3.SHAKE256(byteLength);
        digest.update(input);
        return digest.digest();
    }
}
