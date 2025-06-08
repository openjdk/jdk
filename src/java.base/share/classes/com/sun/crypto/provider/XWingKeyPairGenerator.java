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

        // https://www.ietf.org/archive/id/draft-connolly-cfrg-xwing-kem-07.html#section-5.2
        byte[] sk = new byte[32];
        sr.nextBytes(sk);
        byte[] pk = derivePublicKey(sk);
        return new byte[][]{pk, sk};
    }

    // visible for testing
    // similar to `GenerateKeyPairDerand` defined in https://www.ietf.org/archive/id/draft-connolly-cfrg-xwing-kem-07.html#section-5.2.1
    byte[] derivePublicKey(byte[] sk) {
        var expanded = XWing.expandDecapsulationKey(sk);

        var pkM = expanded.pkM();
        var pkX = expanded.pkX();

        byte[] pk = new byte[pkM.length + pkX.length];
        System.arraycopy(pkM, 0, pk, 0, pkM.length);
        System.arraycopy(pkX, 0, pk, pkM.length, pkX.length);

        return pk;
    }

}
