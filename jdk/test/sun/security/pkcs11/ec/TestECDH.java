/*
 * Copyright (c) 2006, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/**
 * @test
 * @bug 6405536
 * @summary Basic known answer test for ECDH
 * @author Andreas Sterbenz
 * @library ..
 * @library ../../../../java/security/testlibrary
 * @run main/othervm TestECDH
 * @run main/othervm TestECDH sm policy
 */

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import javax.crypto.KeyAgreement;

public class TestECDH extends PKCS11Test {

    private final static String pub192a  = "30:49:30:13:06:07:2a:86:48:ce:3d:02:01:06:08:2a:86:48:ce:3d:03:01:01:03:32:00:04:bc:49:85:81:4d:d0:a4:ef:67:09:f1:9f:f5:ee:ff:4c:2f:0e:74:2c:a0:98:a8:69:79:9c:0c:3c:e8:99:f2:f2:3c:6f:48:bf:2a:ea:45:e9:76:be:1b:4a:45:0c:a2:99";
    private final static String priv192a = "30:39:02:01:00:30:13:06:07:2a:86:48:ce:3d:02:01:06:08:2a:86:48:ce:3d:03:01:01:04:1f:30:1d:02:01:01:04:18:50:9a:f1:fb:14:91:08:91:18:b9:46:7f:c3:ff:84:db:be:4c:70:89:41:5e:5a:f5";
    private final static String pub192b  = "30:49:30:13:06:07:2a:86:48:ce:3d:02:01:06:08:2a:86:48:ce:3d:03:01:01:03:32:00:04:41:f3:1d:09:19:6e:dc:bf:6e:14:3a:b8:1a:40:44:ef:7b:51:fc:e1:9a:64:ac:46:47:ab:31:e2:1b:d3:76:d9:85:7a:b8:e6:95:f5:75:3f:13:7a:3a:88:02:57:de:8f";
    private final static String priv192b = "30:39:02:01:00:30:13:06:07:2a:86:48:ce:3d:02:01:06:08:2a:86:48:ce:3d:03:01:01:04:1f:30:1d:02:01:01:04:18:1d:8c:7d:64:1a:c1:ca:7d:59:d6:e7:11:61:e3:4d:d4:64:31:d9:76:17:a4:dd:6b";

    private final static String secret192 = "1f:48:aa:23:8e:6f:8a:70:87:af:3f:cd:53:f9:ae:85:41:1f:25:7e:b9:88:1f:6b";

    private final static String pub163a  = "30:40:30:10:06:07:2a:86:48:ce:3d:02:01:06:05:2b:81:04:00:0f:03:2c:00:04:04:81:99:2a:6d:53:e1:9a:31:4b:42:5b:01:41:bd:69:3f:73:63:f2:c5:02:70:25:7c:81:ce:6a:00:a0:fa:43:33:25:5b:ac:1f:66:82:1f:fa:63";
    private final static String priv163a = "30:33:02:01:00:30:10:06:07:2a:86:48:ce:3d:02:01:06:05:2b:81:04:00:0f:04:1c:30:1a:02:01:01:04:15:01:a0:2c:f6:24:bb:c8:2f:6e:f3:86:e2:24:bc:f1:01:ce:49:15:09:b9";
    private final static String pub163b  = "30:40:30:10:06:07:2a:86:48:ce:3d:02:01:06:05:2b:81:04:00:0f:03:2c:00:04:03:59:e7:69:a5:89:2f:28:ba:75:ac:bf:01:d5:ad:14:d8:f8:19:25:81:01:31:b3:e2:2d:f3:db:f1:d2:cd:fc:94:af:d2:1d:16:58:94:fe:d5:65";
    private final static String priv163b = "30:33:02:01:00:30:10:06:07:2a:86:48:ce:3d:02:01:06:05:2b:81:04:00:0f:04:1c:30:1a:02:01:01:04:15:02:4e:49:b1:8b:36:d8:71:22:81:06:8d:14:a9:4c:5c:7c:61:8b:e2:95";

    private final static String secret163 = "04:ae:71:c1:c6:4d:f4:34:4d:72:70:a4:64:65:7f:2d:88:2d:3f:50:be";

    @Override
    public void main(Provider p) throws Exception {
        if (p.getService("KeyAgreement", "ECDH") == null) {
            System.out.println("Provider does not support ECDH, skipping");
            return;
        }

        if (isNSS(p) && getNSSECC() == ECCState.Basic) {
            System.out.println("NSS only supports Basic ECC.  Skipping..");
            return;
        }

        /*
         * PKCS11Test.main will remove this provider if needed
         */
        Providers.setAt(p, 1);

        if (false) {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", p);
            kpg.initialize(163);
            KeyPair kp = kpg.generateKeyPair();
            System.out.println(toString(kp.getPublic().getEncoded()));
            System.out.println(toString(kp.getPrivate().getEncoded()));
            kp = kpg.generateKeyPair();
            System.out.println(toString(kp.getPublic().getEncoded()));
            System.out.println(toString(kp.getPrivate().getEncoded()));
            return;
        }

        test(p, pub192a, priv192a, pub192b, priv192b, secret192);
        test(p, pub163a, priv163a, pub163b, priv163b, secret163);

        System.out.println("OK");
    }

    private final static void test(Provider p, String pub1s, String priv1s,
            String pub2s, String priv2s, String secrets) throws Exception {
        KeyFactory kf = KeyFactory.getInstance("EC", p);
        PublicKey pub1 = kf.generatePublic(new X509EncodedKeySpec(parse(pub1s)));
        System.out.println("Testing using parameters "
                + ((ECPublicKey)pub1).getParams() + "...");

        PrivateKey priv1 = kf.generatePrivate(new PKCS8EncodedKeySpec(parse(priv1s)));
        PublicKey pub2 = kf.generatePublic(new X509EncodedKeySpec(parse(pub2s)));
        PrivateKey priv2 = kf.generatePrivate(new PKCS8EncodedKeySpec(parse(priv2s)));
        byte[] secret = parse(secrets);

        KeyAgreement ka1 = KeyAgreement.getInstance("ECDH", p);
        ka1.init(priv1);
        ka1.doPhase(pub2, true);
        byte[] s1 = ka1.generateSecret();
        if (Arrays.equals(secret, s1) == false) {
            System.out.println("expected: " + toString(secret));
            System.out.println("actual:   " + toString(s1));
            throw new Exception("Secret 1 does not match");
        }

        KeyAgreement ka2 = KeyAgreement.getInstance("ECDH", p);
        ka2.init(priv2);
        ka2.doPhase(pub1, true);
        byte[] s2 = ka2.generateSecret();
        if (Arrays.equals(secret, s2) == false) {
            System.out.println("expected: " + toString(secret));
            System.out.println("actual:   " + toString(s2));
            throw new Exception("Secret 2 does not match");
        }
    }

    public static void main(String[] args) throws Exception {
        main(new TestECDH(), args);
    }

}
