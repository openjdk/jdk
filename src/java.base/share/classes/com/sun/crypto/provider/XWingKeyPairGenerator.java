package com.sun.crypto.provider;

import sun.security.jca.JCAUtil;
import sun.security.provider.NamedKeyPairGenerator;

import java.security.SecureRandom;

public class XWingKeyPairGenerator extends NamedKeyPairGenerator {

    public XWingKeyPairGenerator() {
        super("X-Wing", "X-Wing");
    }

    @Override
    protected byte[][] implGenerateKeyPair(String pname, SecureRandom sr) {
        if (sr == null) {
            sr = JCAUtil.getDefSecureRandom();
        }

        // https://www.ietf.org/archive/id/draft-connolly-cfrg-xwing-kem-08.html#section-5.2
        byte[] sk = new byte[32];
        sr.nextBytes(sk);
        return generateKeyPairDerand(sk);
    }

    // visible for testing
    // similar to `GenerateKeyPairDerand` defined in https://www.ietf.org/archive/id/draft-connolly-cfrg-xwing-kem-08.html#section-5.2.1
    /// @return the public key, the private key in its encoding format, and the private key in its expanded format (in this order) in raw bytes.
    public static byte[][] generateKeyPairDerand(byte[] sk) {
        var privateKey = XWingKeyFactory.XWingPrivateKey.of(sk);
        try {
            var publicKey = privateKey.derivePublicKey();
            var pkM = publicKey.m();
            var pkX = publicKey.x();

            byte[] pk = new byte[pkM.length + pkX.length];
            System.arraycopy(pkM, 0, pk, 0, pkM.length);
            System.arraycopy(pkX, 0, pk, pkM.length, pkX.length);

            byte[] expandedSk = privateKey.expanded();

            return new byte[][]{
                    pk,
                    sk,
                    expandedSk
            };
        } finally {
            privateKey.destroy();
        }
    }

}
