/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4853306
 * @summary Test RSA Cipher implementation
 * @author Andreas Sterbenz
 * @key randomness
 */

import java.io.*;
import java.util.*;
import java.math.BigInteger;

import java.security.*;
import java.security.spec.*;

import javax.crypto.*;

public class TestRSA {

    private final static char[] hexDigits = "0123456789abcdef".toCharArray();

    public static String toString(byte[] b) {
        if (b == null) {
            return "(null)";
        }
        StringBuffer sb = new StringBuffer(b.length * 3);
        for (int i = 0; i < b.length; i++) {
            int k = b[i] & 0xff;
            if (i != 0) {
                sb.append(':');
            }
            sb.append(hexDigits[k >>> 4]);
            sb.append(hexDigits[k & 0xf]);
        }
        return sb.toString();
    }

    public static byte[] parse(String s) {
        try {
            int n = s.length();
            ByteArrayOutputStream out = new ByteArrayOutputStream(n / 3);
            StringReader r = new StringReader(s);
            while (true) {
                int b1 = nextNibble(r);
                if (b1 < 0) {
                    break;
                }
                int b2 = nextNibble(r);
                if (b2 < 0) {
                    throw new RuntimeException("Invalid string " + s);
                }
                int b = (b1 << 4) | b2;
                out.write(b);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] b(String s) {
        return parse(s);
    }

    private static int nextNibble(StringReader r) throws IOException {
        while (true) {
            int ch = r.read();
            if (ch == -1) {
                return -1;
            } else if ((ch >= '0') && (ch <= '9')) {
                return ch - '0';
            } else if ((ch >= 'a') && (ch <= 'f')) {
                return ch - 'a' + 10;
            } else if ((ch >= 'A') && (ch <= 'F')) {
                return ch - 'A' + 10;
            }
        }
    }

    private final static BigInteger N = new BigInteger
    ("188266606413163647033284152746165049309898453322378171182320013745371408"
    +"184225151227340555539225381200565037956400694325061098310480360339435446"
    +"755336872372614880713694669514510970895097323213784523223711244354375506"
    +"545371740274561954822416119304686041493350049135717091225288845575963270"
    +"990119098295690603875646206002898855577388327774594330896529948536446408"
    +"529165060686851725546480612209956477350581924733034990053737541249952501"
    +"521769091148873248215142518797910690254486909784694829645856181407041627"
    +"170373444275842961547787746324163594572697634605250977434548015081503826"
    +"85269006571608614747965903308253511034583");

    private final static BigInteger E = BigInteger.valueOf(65537);

    private final static BigInteger D = new BigInteger
    ("559658959270449023652159986632594861346314765962941829914811303419116045"
    +"486272857832294696380057096672262714220410818939360476461317579410769250"
    +"330981320689411092912185059149606517928125605236733543203155054153225543"
    +"370812803235323701309554652228655108862291812277980776744407549833834128"
    +"186640306349843950814414209051640048163781518404082259622597528271617305"
    +"214590875955949331568915021275293633454662841999317653268823194135508673"
    +"577887397954709453731172900970199673444683653554380810128925964066225098"
    +"009484055412274405773246950554037029408478181447349886871279557912030178"
    +"079306593910311097342934485929224862873");

    private final static Random RANDOM = new Random();

    private static Provider p;

    private static void testEncDec(String alg, int len, Key encKey, Key decKey) throws Exception {
        System.out.println("Testing en/decryption using " + alg + "...");
        Cipher c = Cipher.getInstance(alg, p);

        byte[] b = new byte[len];
        RANDOM.nextBytes(b);
        b[0] &= 0x3f;
        b[0] |= 1;

        c.init(Cipher.ENCRYPT_MODE, encKey);
        byte[] enc = c.doFinal(b);

        c.init(Cipher.DECRYPT_MODE, decKey);
        byte[] dec = c.doFinal(enc);

        if (Arrays.equals(b, dec) == false) {
            System.out.println("in:  " + toString(b));
            System.out.println("dec: " + toString(dec));
            throw new Exception("Failure");
        }
    }

    public static void testKat(String alg, int mode, Key key, String in, String out, boolean result) throws Exception {
        System.out.println("Testing known values for " + alg + "...");
        Cipher c = Cipher.getInstance(alg, p);
        c.init(mode, key);
        byte[] r = c.doFinal(parse(in));
        byte[] s = parse(out);
        if (Arrays.equals(r, s) != result) {
            throw new Exception("Unexpected test result");
        }
    }

    private final static String in2  = "0f:7d:6c:20:75:99:a5:bc:c1:53:b0:4e:8d:ef:98:fb:cf:2d:e5:1d:d4:bf:71:56:12:b7:a3:c3:e4:53:1b:07:d3:bb:94:a7:a7:28:75:1e:83:46:c9:80:4e:3f:ac:b2:47:06:9f:1b:68:38:73:b8:69:9e:6b:8b:8b:23:60:31:ae:ea:36:24:6f:85:af:de:a5:2a:88:7d:6a:9f:8a:9f:61:f6:59:3f:a8:ce:91:75:49:e9:34:b8:9f:b6:21:8c";
    private final static String out2 = "4d:17:15:23:d9:f6:97:4d:4b:5b:9b:37:bd:a7:c5:33:b9:40:1f:c4:63:fa:7c:2a:fb:19:0b:d8:c4:3a:bd:e7:46:6b:1b:09:20:93:39:7c:e5:5f:7b:83:a7:a6:f6:f5:42:20:e7:7f:d3:14:9a:14:25:f9:31:9e:3c:c9:04:20:be:31:ac:77:45:37:4d:76:1b:10:3a:aa:42:c7:df:4c:61:a4:35:4d:28:41:c2:f9:b7:ce:00:94:42:06:c7:35:06:ca:f2:9e:96:c3:89:54:10:82:d8:de:f3:6c:23:8c:47:41:5a:13:fa:33:e0:a5:7f:ec:43:5d:b0:ea:c9:43:17:72:73:ce:11:48:fb:19:ee:13:6a:92:13:06:5c:55:dc:9e:86:b9:fb:44:62:44:9e:a9:e8:bd:6a:c0:c1:64:4b:fd:a9:5d:ef:59:1e:16:fe:64:c1:07:31:9e:9f:4d:4e:28:34:ea:39:e0:65:68:d4:8b:02:0b:8b:ed:bb:a6:a6:4a:29:b9:b5:08:f3:7a:a8:fd:03:3e:0d:d0:9e:25:47:2c:45:f2:40:39:58:e8:95:64:04:2b:50:1e:a5:ff:00:a4:cf:a9:13:4b:17:3a:e8:d1:2c:c1:4a:ab:1c:07:b4:b5:f6:c9:3f:38:48:89:55:59:00:c1:25:c9:d7:68";

    private final static String in1  = "17:a3:a7:b1:86:29:06:c5:81:33:cd:2f:da:32:7c:0e:26:a8:18:aa:37:9b:dd:4a:b0:b0:a7:1c:14:82:6c:d9:c9:14:9f:55:19:91:02:0d:d9:d7:95:c2:2b:a6:fa:ba:a3:51:00:83:6b:ec:97:27:40:a3:8f:ba:b1:09:15:11:44:33:c6:3c:47:95:50:71:50:5a:f4:aa:00:4e:b9:48:6a:b1:34:e9:d0:c8:b8:92:bf:95:f3:3d:91:66:93:2b";
    private final static String out1 = "18:6d:d2:89:43:cb:ec:5c:ff:3d:fd:d5:23:2d:aa:fc:db:a7:63:5f:c7:2d:6f:81:c2:9f:aa:47:ed:fc:79:39:8a:6d:8f:c3:d0:f9:64:c3:e1:5f:1a:b3:20:03:1e:8a:3a:c5:58:ef:78:6b:fc:50:98:0a:11:d3:30:d9:68:44:9b:93:a6:b3:92:8f:09:0c:7a:d0:64:ac:e2:c7:b5:6a:37:35:00:3b:4e:d7:64:fb:54:c2:54:90:b9:71:6a:48:c4:6c:1e:e4:e6:4c:3f:fc:34:69:16:b9:53:8c:9f:30:4e:2e:7e:9c:fb:5f:26:18:c0:6e:69:32:18:30:40:59:8c:d1:c2:7a:41:75:06:9d:1c:0f:14:74:a9:f0:47:3a:97:0d:c4:c6:3f:24:ee:ed:c5:f8:2c:b6:ae:1d:e5:64:33:cd:e1:e0:21:d6:10:c0:8b:59:06:59:81:73:28:b4:f4:ef:fa:e8:67:a8:65:a5:e4:3c:c3:7e:99:f8:55:7a:e9:0d:41:3a:bf:c1:8c:41:f3:71:32:b6:c0:05:8b:91:8a:90:35:60:95:52:78:8e:a7:e5:a9:a1:bf:a3:de:55:c6:02:03:d5:98:01:59:fb:91:da:37:9e:3f:39:85:e1:3f:79:23:6c:0e:68:25:4c:13:3a:52:a2:f8:d9:4c:ce";

    private final static String rin1  = "09:01:06:53:a7:96:09:63:ef:e1:3f:e9:8d:95:22:d1:0e:1b:87:c1:a2:41:b2:09:97:a3:5e:e0:a4:1d:59:91:21:e4:ca:87:bf:77:4a:7e:a2:22:ff:59:1e:bd:a4:80:aa:93:4a:41:56:95:5b:f4:57:df:fc:52:2f:46:9b:45:d7:03:ae:22:8e:67:9e:6c:b9:95:4f:bd:8e:e8:67:90:5b:fe:de:2f:11:22:2e:9d:30:93:6d:c0:48:00:cb:08:b9:c4:36:e9:03:7c:08:2d:68:42:cb:71:d0:7d:47:22:c1:58:c5:b8:2f:28:3e:98:78:11:6d:71:5b:3b:36:3c:09:01:06:53:a7:96:09:63:ef:e1:3f:e9:8d:95:22:d1:0e:1b:87:c1:a2:41:b2:09:97:a3:5e:e0:a4:1d:59:91:21:e4:ca:87:bf:77:4a:7e:a2:22:ff:59:1e:bd:a4:80:aa:93:4a:41:56:95:5b:f4:57:df:fc:52:2f:46:9b:45:d7:03:ae:22:8e:67:9e:6c:b9:95:4f:bd:8e:e8:67:90:5b:fe:de:2f:11:22:2e:9d:30:93:6d:c0:48:00:cb:08:b9:c4:36:e9:03:7c:08:2d:68:42:cb:71:d0:7d:47:22:c1:58:c5:b8:2f:28:3e:98:78:11:6d:71:5b:3b:36:3c";
    private final static String rout1 = "19:dd:a2:f9:57:d4:6b:60:85:ec:2d:5d:f9:64:f8:a0:c0:33:36:a2:8c:59:0f:74:9b:62:a8:ad:42:ed:be:34:0e:dc:13:db:d5:b9:aa:64:38:35:18:d7:6c:1d:da:5b:ff:f2:98:f5:fc:67:36:fb:9f:84:df:84:a3:af:ce:02:e5:05:ca:a7:e4:29:c0:5c:55:6a:8d:dc:8f:f7:6e:d4:ee:2e:6c:5b:ea:f8:bf:4c:7d:5f:af:6a:c3:77:02:80:33:be:13:4c:98:cf:dc:aa:e8:7d:73:69:6e:30:2c:35:c5:90:83:45:0d:64:04:af:b6:94:c3:a8:e2:d4:08:98:1d:b1:73:e3:fc:10:1f:71:0f:d0:13:f3:58:80:c4:a3:a9:02:52:cf:aa:41:b6:9b:69:33:9d:2a:d6:f6:02:07:ec:ce:19:01:f1:2f:90:27:fe:00:a5:d7:8d:01:97:36:fd:88:34:2f:f3:ab:38:ed:9d:69:91:af:b2:0d:ca:92:ca:9e:e7:24:37:d6:e3:c7:02:30:69:5b:ea:b4:b2:68:5f:4e:8c:cc:fd:bb:2e:96:2f:a3:c6:f7:71:93:24:5c:ca:8f:bc:f9:d8:bd:d3:b9:d1:16:ba:5a:ac:62:41:b4:d8:56:45:74:55:c2:a5:ef:23:f5:e3:27:ce:99:97:e9";

    private final static String rin2  = "1b:49:a6:7a:83:1c:b6:28:47:16:2f:be:6a:d3:28:a6:83:07:4f:50:be:5c:99:26:2a:15:b8:21:a8:cc:8a:45:93:07:ff:32:67:3c:a4:92:d2:cd:43:eb:f5:2e:09:79:c8:32:3a:9d:00:4c:f5:6e:65:b2:ca:9c:c2:d5:35:8e:fe:6c:ba:1a:7b:65:c1:4f:e9:6c:cb:5d:9f:13:5d:5f:be:32:cd:91:ed:8b:d7:d7:e9:d6:5c:cc:11:7b:d9:ff:7a:93:de:e4:81:92:56:0c:52:47:75:56:a8:e0:9a:55:16:0c:43:df:ae:be:a1:6a:9d:5a:be:fc:51:ea:52:0c:1b:49:a6:7a:83:1c:b6:28:47:16:2f:be:6a:d3:28:a6:83:07:4f:50:be:5c:99:26:2a:15:b8:21:a8:cc:8a:45:93:07:ff:32:67:3c:a4:92:d2:cd:43:eb:f5:2e:09:79:c8:32:3a:9d:00:4c:f5:6e:65:b2:ca:9c:c2:d5:35:8e:fe:6c:ba:1a:7b:65:c1:4f:e9:6c:cb:5d:9f:13:5d:5f:be:32:cd:91:ed:8b:d7:d7:e9:d6:5c:cc:11:7b:d9:ff:7a:93:de:e4:81:92:56:0c:52:47:75:56:a8:e0:9a:55:16:0c:43:df:ae:be:a1:6a:9d:5a:be:fc:51:ea:52:0c";
    private final static String rout2 = "7a:11:19:cf:76:97:4b:29:48:66:69:e7:f0:db:18:53:d4:50:71:a4:9d:90:47:9f:e6:8a:f3:ba:2e:96:fd:c8:4b:02:7e:06:a9:2b:47:0d:68:3c:6a:f9:21:62:77:0d:4e:e1:1b:82:97:66:13:01:c2:3b:b2:d3:f8:9e:cc:c9:2a:1a:76:05:3f:d4:f7:fb:9d:9b:bf:a8:2d:fd:81:e5:f4:bb:ca:3b:5f:93:ea:ef:88:1c:c1:18:52:38:be:50:42:29:08:d9:65:43:5f:01:7d:50:22:7a:2f:f1:29:14:95:30:c1:b8:fd:eb:da:c1:4e:8a:ef:97:84:f9:cf:34:ab:89:a6:3c:4a:ff:a4:98:a8:7c:c6:2c:c3:e3:10:a9:8b:67:32:47:35:37:15:03:3b:d0:f3:23:fc:bb:42:64:a2:ba:63:3e:94:6e:7a:e6:94:05:79:29:28:d5:99:5b:f9:67:fd:ea:d3:5f:b5:7b:f4:10:9b:0a:1c:20:6b:0c:59:56:76:45:07:56:cb:d0:ab:08:fc:19:8e:f1:27:03:22:f1:e9:23:d3:01:b1:4d:cf:96:f7:a6:44:59:de:2a:52:fd:bb:14:ae:39:c4:e4:0f:4e:10:f7:c6:61:79:0a:a6:4c:ed:ee:d7:40:fe:ef:f3:85:ae:3e:f3:bb:6e:de";

    public static void main(String[] args) throws Exception {
        long start = System.currentTimeMillis();

        p = Security.getProvider(
                System.getProperty("test.provider.name", "SunJCE"));
        System.out.println("Testing provider " + p.getName() + "...");

        KeyFactory kf;
        try {
            kf = KeyFactory.getInstance("RSA", p);
        } catch (NoSuchAlgorithmException e) {
            kf = KeyFactory.getInstance("RSA");
        }

        RSAPublicKeySpec pubSpec = new RSAPublicKeySpec(N, E);
        PublicKey publicKey = kf.generatePublic(pubSpec);

        RSAPrivateKeySpec privSpec = new RSAPrivateKeySpec(N, D);
        PrivateKey privateKey = kf.generatePrivate(privSpec);

        // blocktype 2
        testEncDec("RSA/ECB/PKCS1Padding", 96, publicKey, privateKey);
        // blocktype 1
        testEncDec("RSA/ECB/PKCS1Padding", 96, privateKey, publicKey);

        testEncDec("RSA/ECB/NoPadding", 256, publicKey, privateKey);
        testEncDec("RSA/ECB/NoPadding", 256, privateKey, publicKey);

        // expected failure, blocktype 2 random padding bytes are different
        testKat("RSA/ECB/PKCS1Padding", Cipher.ENCRYPT_MODE, publicKey, in2, out2, false);
        testKat("RSA/ECB/PKCS1Padding", Cipher.DECRYPT_MODE, privateKey, out2, in2, true);

        testKat("RSA/ECB/PKCS1Padding", Cipher.ENCRYPT_MODE, privateKey, in1, out1, true);
        testKat("RSA/ECB/PKCS1Padding", Cipher.DECRYPT_MODE, publicKey, out1, in1, true);

        testKat("RSA/ECB/NoPadding", Cipher.ENCRYPT_MODE, publicKey, rin1, rout1, true);
        testKat("RSA/ECB/NoPadding", Cipher.DECRYPT_MODE, privateKey, rout1, rin1, true);

        testKat("RSA/ECB/NoPadding", Cipher.ENCRYPT_MODE, privateKey, rin2, rout2, true);
        testKat("RSA/ECB/NoPadding", Cipher.DECRYPT_MODE, publicKey, rout2, rin2, true);

        System.out.println("Testing error cases...");
        try {
            // decrypt something not PKCS#1 formatted
            testKat("RSA/ECB/PKCS1Padding", Cipher.DECRYPT_MODE, privateKey, rout1, rin1, true);
            throw new Exception("Unexpected success");
        } catch (BadPaddingException e) {
            // ok
        }

        try {
            // decrypt with wrong key
            testKat("RSA/ECB/PKCS1Padding", Cipher.DECRYPT_MODE, privateKey, out1, in1, true);
            throw new Exception("Unexpected success");
        } catch (BadPaddingException e) {
            // ok
        }

        try {
            // encrypt data that is too long
            testKat("RSA/ECB/PKCS1Padding", Cipher.ENCRYPT_MODE, privateKey, out1, in1, true);
            throw new Exception("Unexpected success");
        } catch (IllegalBlockSizeException e) {
            // ok
        }

        long stop = System.currentTimeMillis();
        System.out.println("Done (" + (stop - start) + " ms).");
    }

}
