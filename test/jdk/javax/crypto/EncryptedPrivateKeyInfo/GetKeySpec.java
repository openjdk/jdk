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
 * @bug 4508341
 * @summary Test the EncryptedPrivateKeyInfo.getKeySpec(...) methods.
 * @author Valerie Peng
 */
import java.util.*;
import java.nio.*;
import java.io.*;
import java.security.*;
import java.util.Arrays;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.*;

public class GetKeySpec {
    private static final String cipherAlg = "PBEWithSHA1AndDESede";
    private static final char[] passwd = { 'p','a','s','s','w','d' };
    private static AlgorithmParameters GOOD_PARAMS;

    static {
        try {
            PBEParameterSpec goodParamSpec =
                new PBEParameterSpec(new byte[8], 1024);
            GOOD_PARAMS = AlgorithmParameters.getInstance
                    (cipherAlg, System.getProperty("test.provider.name", "SunJCE"));
            GOOD_PARAMS.init(goodParamSpec);
        } catch (Exception ex) {
            // should never happen
            GOOD_PARAMS = null;
        }
    }

    private static String pkcs8Encoded = "30:82:01:53:02:01:00:30:0D:06:09:2A:86:48:86:F7:0D:01:01:01:05:00:04:82:01:3D:30:82:01:39:02:01:00:02:40:6E:A4:13:65:97:A2:C2:47:5E:F2:23:6B:94:D8:D7:25:13:BB:A4:AE:8A:AA:A7:27:A4:9A:04:DC:15:F7:9B:E4:39:18:99:9E:27:EA:92:BB:D0:0E:F3:26:F4:95:89:33:02:65:6D:84:69:2C:CE:B7:FA:68:8E:FE:8D:63:44:6B:02:03:01:00:01:02:40:59:6E:1C:13:98:FE:C1:04:89:75:35:36:27:29:22:B5:E0:7E:62:BD:86:6E:2C:10:7A:16:D8:68:C1:04:D4:A7:10:41:F7:B9:B4:84:05:03:A5:C0:28:73:24:A7:24:F1:1B:C3:4F:BF:05:20:D0:D9:00:08:7F:C3:29:64:1B:29:02:21:00:C4:63:4D:0C:32:51:44:AE:DD:90:A9:B7:B6:C2:6B:11:BE:D2:07:E7:B5:C2:4A:9F:4D:0F:2F:30:5F:E6:1C:6D:02:21:00:90:39:A4:2D:93:0B:08:AF:2F:6F:18:CC:1A:EF:B6:E6:01:E7:21:3A:7F:45:C7:3F:39:12:B8:CC:DF:44:2D:37:02:21:00:B3:9B:61:9E:B2:F2:12:4F:9E:C1:2C:06:A1:B5:A3:38:62:7D:31:CF:9F:32:67:0E:D3:E9:FC:2D:50:B7:61:ED:02:20:5B:FD:77:FB:5D:A3:97:09:6E:1E:D5:59:32:01:1D:CE:7C:FE:38:12:80:A5:38:1D:DA:40:57:C0:CC:D3:46:67:02:20:52:EC:61:05:0D:EC:8A:ED:F7:1E:95:67:D0:7C:8B:D9:AA:A5:33:B8:26:26:2E:8F:D7:A7:18:16:2A:83:63:5C";
    private static String encryptedPKCS8 = "0D:CA:00:8F:64:91:9C:60:36:F5:9B:BD:DD:C5:A9:A2:27:9E:6B:AE:CB:23:0E:2F:DA:76:03:A5:B7:C0:D5:3E:B9:03:60:62:41:2C:D6:51:37:F0:D9:ED:B2:CC:E7:99:28:03:CD:20:5D:EC:56:77:FC:61:57:D7:8C:F3:F6:10:F7:E5:BA:88:04:FE:1A:17:B3:8C:36:BF:70:2D:CD:6F:BF:83:ED:03:41:22:95:68:E3:08:90:76:B5:97:CB:FF:CE:51:27:14:F6:38:00:22:E9:0F:86:9F:64:D2:47:34:F6:50:DA:A9:80:F5:67:BF:C7:51:B3:38:AF:CD:15:96:50:8F:33:F3:8B:43:4C:AF:ED:DD:37:03:EC:B1:CC:57:53:0A:AF:0D:53:CD:D7:2B:A2:20:C5:37:AF:09:78:8E:3F:A0:E4:EC:22:C6:71:EC:D1:42:15:9D:1D:E9:E3:9D:8F:D6:0B:2A:99:C9:C8:90:B1:CD:AB:17:DD:A3:6F:64:43:23:26:25:7B:A5:E0:1F:2E:AF:18:89:C8:D6:97:28:32:A1:01:22:6F:14:B6:6C:4E:8A:83:47:16:99:51:B4:8D:85:9E:AB:00:B5:18:BB:49:97:47:59:F8:A7:A8:64:76:3F:41:5F:71:1A:F3:4A:96:F2:B4:44:38:42:4B:AE:0F:08:83:5C:33:F8:6A:8F:B9:6A:3D:1C:06:02:4E:07:48:46:E0:6D:6D:ED:E8:19:CB:3F:B0:6F:10:68:3A:5E:F5:8F:94:EF:B4:8B:58:5F:50:0A:E5:F2:13:54:59:14:99:C5:74:02:A2:B1:73:16:7F:F2:D4:DE:E0:12:86:55:46:9C:57:D1:7A:5C:8B:46:E1:7E:C3:32:14:31:52:64:07:52:9D:65:04:9D:54:89";

    private static byte[] parse(String s) {
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

    public static void main(String[] argv) throws Exception {
        if (GOOD_PARAMS == null) {
            throw new Exception("Static parameter generation failed");
        }
        byte[] encodedKey = parse(pkcs8Encoded);
        byte[] encryptedData = parse(encryptedPKCS8);
        boolean result = true;

        Provider p = Security.getProvider(System.getProperty("test.provider.name", "SunJCE"));

        // generate encrypted data and EncryptedPrivateKeyInfo object
        EncryptedPrivateKeyInfo epki =
            new EncryptedPrivateKeyInfo(GOOD_PARAMS, encryptedData);

        PKCS8EncodedKeySpec pkcs8Spec;
        // TEST#1 getKeySpec(Cipher)
        System.out.println("Testing getKeySpec(Cipher)...");
        // Prepare Cipher for decryption
        PBEKeySpec pbeKeySpec = new PBEKeySpec(passwd);
        SecretKeyFactory skf = SecretKeyFactory.getInstance(cipherAlg, p);
        SecretKey cipherKey = skf.generateSecret(pbeKeySpec);
        Cipher cipher = Cipher.getInstance(cipherAlg, p);
        cipher.init(Cipher.DECRYPT_MODE, cipherKey, GOOD_PARAMS);
        pkcs8Spec = epki.getKeySpec(cipher);
        if (Arrays.equals(pkcs8Spec.getEncoded(), encodedKey)) {
            System.out.println("passed");
        } else {
            result = false;
        }

        // TEST#2 getKeySpec(Key)
        System.out.println("Testing getKeySpec(Key)...");
        pkcs8Spec = epki.getKeySpec(cipherKey);
        if (Arrays.equals(pkcs8Spec.getEncoded(), encodedKey)) {
            System.out.println("passed");
        } else {
            result = false;
        }

        // TEST#3 getKeySpec(Key, String)
        System.out.println("Testing getKeySpec(Key, String)...");
        pkcs8Spec = epki.getKeySpec(cipherKey, p.getName());
        if (Arrays.equals(pkcs8Spec.getEncoded(), encodedKey)) {
            System.out.println("passed");
        } else {
            result = false;
        }

        // TEST#4 getKeySpec(Key, Provider)
        System.out.println("Testing getKeySpec(Key, Provider)...");
        pkcs8Spec = epki.getKeySpec(cipherKey, p);
        if (Arrays.equals(pkcs8Spec.getEncoded(), encodedKey)) {
            System.out.println("passed");
        } else {
            result = false;
        }

        if (result) {
            System.out.println("All Tests Passed");
        } else {
            throw new Exception("One or More Test Failed");
        }
    }
}
