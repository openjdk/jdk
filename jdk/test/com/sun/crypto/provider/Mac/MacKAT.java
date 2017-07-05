/*
 * Copyright (c) 2003, 2007, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4846410 6313661
 * @summary Basic known-answer-test for Hmac and SslMac algorithms
 * @author Andreas Sterbenz
 */

import java.io.*;
import java.util.*;

import java.security.*;

import javax.crypto.*;
import javax.crypto.spec.*;

public class MacKAT {

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

    static abstract class Test {
        abstract void run(Provider p) throws Exception;
    }

    static class MacTest extends Test {
        private final String alg;
        private final byte[] input;
        private final byte[] macvalue;
        private final byte[] key;
        MacTest(String alg, byte[] input, byte[] macvalue, byte[] key) {
            this.alg = alg;
            this.input = input;
            this.macvalue = macvalue;
            this.key = key;
        }
        void run(Provider p) throws Exception {
            Mac mac = Mac.getInstance(alg, p);
            SecretKey keySpec = new SecretKeySpec(key, alg);
            mac.init(keySpec);
            mac.update(input);
            byte[] macv = mac.doFinal();
            if (Arrays.equals(macvalue, macv) == false) {
                System.out.println("Mac test for " + alg + " failed:");
                if (input.length < 256) {
                    System.out.println("input:       " + toString(input));
                }
                System.out.println("key:        " + toString(key));
                System.out.println("macvalue:   " + toString(macvalue));
                System.out.println("calculated: " + toString(macv));
                throw new Exception("Mac test for " + alg + " failed");
            }
            System.out.println("passed: " + alg);
        }
        private static String toString(byte[] b) {
            return MacKAT.toString(b);
        }
    }

    private static byte[] s(String s) {
        try {
            return s.getBytes("UTF8");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Test t(String alg, String input, String macvalue, String key) {
        return new MacTest(alg, b(input), b(macvalue), b(key));
    }

    private static Test t(String alg, byte[] input, String macvalue, String key) {
        return new MacTest(alg, input, b(macvalue), b(key));
    }

    private static Test t(String alg, byte[] input, String macvalue, byte[] key) {
        return new MacTest(alg, input, b(macvalue), key);
    }

    private final static byte[] ALONG, BLONG, BKEY;

    static {
        ALONG = new byte[1024 * 128];
        Arrays.fill(ALONG, (byte)'a');
        BLONG = new byte[1024 * 128];
        Random random = new Random(12345678);
        random.nextBytes(BLONG);
        BKEY = new byte[128];
        random.nextBytes(BKEY);
    }

    private final static Test[] tests = {
        t("SslMacMD5", ALONG, "f4:ad:01:71:51:f6:89:56:72:a3:32:bf:d9:2a:f2:a5",
                "1b:34:61:29:05:0d:73:db:25:d0:dd:64:06:29:f6:8a"),
        t("SslMacMD5", BLONG, "34:1c:ad:a0:95:57:32:f8:8e:80:8f:ee:b2:d8:23:e5",
                "76:00:4a:72:98:9b:65:ec:2e:f1:43:c4:65:4a:13:71"),
        t("SslMacSHA1", ALONG, "11:c1:71:2e:61:be:4b:cf:bc:6d:e2:4c:58:ae:27:30:0b:24:a4:87",
                "23:ae:dd:61:87:6c:7a:45:47:2f:2c:8f:ea:64:99:3e:27:5f:97:a5"),
        t("SslMacSHA1", BLONG, "84:af:57:0a:af:ef:16:93:90:50:da:88:f8:ad:1a:c5:66:6c:94:d0",
                "9b:bb:e2:aa:9b:28:1c:95:0e:ea:30:21:98:a5:7e:31:9e:bf:5f:51"),

        t("HmacMD5", ALONG, "76:00:4a:72:98:9b:65:ec:2e:f1:43:c4:65:4a:13:71",
                "1b:34:61:29:05:0d:73:db:25:d0:dd:64:06:29:f6:8a"),
        t("HmacMD5", BLONG, "6c:22:79:bb:34:9e:da:f4:f5:cf:df:0c:62:3d:59:e0",
                "76:00:4a:72:98:9b:65:ec:2e:f1:43:c4:65:4a:13:71"),
        t("HmacMD5", BLONG, "e6:ad:00:c9:49:6b:98:fe:53:a2:b9:2d:7d:41:a2:03",
                BKEY),
        t("HmacSHA1", ALONG, "9e:b3:6e:35:fa:fb:17:2e:2b:f3:b0:4a:9d:38:83:c4:5f:6d:d9:00",
                "1b:34:61:29:05:0d:73:db:25:d0:dd:64:06:29:f6:8a"),
        t("HmacSHA1", BLONG, "80:2d:5b:ea:08:df:a4:1f:e5:3e:1c:fa:fc:ad:dd:31:da:15:60:2c",
                "76:00:4a:72:98:9b:65:ec:2e:f1:43:c4:65:4a:13:71"),
        t("HmacSHA1", BLONG, "a2:fa:2a:85:18:0e:94:b2:a5:e2:17:8b:2a:29:7a:95:cd:e8:aa:82",
                BKEY),

        t("HmacSHA256", ALONG, "3f:6d:08:df:0c:90:b0:e9:ed:13:4a:2e:c3:48:1d:3d:3e:61:2e:f1:30:c2:63:c4:58:57:03:c2:cb:87:15:07",
                "1b:34:61:29:05:0d:73:db:25:d0:dd:64:06:29:f6:8a"),
        t("HmacSHA256", BLONG, "e2:4e:a3:b9:0b:b8:99:e4:71:cf:ca:9f:f8:4e:f0:34:8b:19:9f:33:4b:1a:b7:13:f7:c8:57:92:e3:03:74:78",
                BKEY),
        t("HmacSHA384", ALONG, "d0:f0:d4:54:1c:0a:6d:81:ed:15:20:d7:0c:96:06:61:a0:ff:c9:ff:91:e9:a0:cd:e2:45:64:9d:93:4c:a9:fa:89:ae:c0:90:e6:0b:a1:a0:56:80:57:3b:ed:4b:b0:71",
                "1b:34:61:29:05:0d:73:db:25:d0:dd:64:06:29:f6:8a"),
        t("HmacSHA384", BLONG, "75:c4:ca:c7:f7:58:9d:d3:23:b1:1b:5c:93:2d:ec:7a:03:dc:8c:eb:8d:fe:79:46:4f:30:e7:99:62:de:44:e2:38:95:0e:79:91:78:2f:a4:05:0a:f0:17:10:38:a1:8e",
                BKEY),
        t("HmacSHA512", ALONG, "41:ea:4c:e5:31:3f:7c:18:0e:5e:95:a9:25:0a:10:58:e6:40:53:88:82:4f:5a:da:6f:29:de:04:7b:8e:d7:ed:7c:4d:b8:2a:48:2d:17:2a:2d:59:bb:81:9c:bf:33:40:04:77:44:fb:45:25:1f:fd:b9:29:f4:a6:69:a3:43:6f",
                "1b:34:61:29:05:0d:73:db:25:d0:dd:64:06:29:f6:8a"),
        t("HmacSHA512", BLONG, "fb:cf:4b:c6:d5:49:5a:5b:0b:d9:2a:32:f5:fa:68:d2:68:a4:0f:ae:53:fc:49:12:e6:1d:53:cf:b2:cb:c5:c5:f2:2d:86:bd:14:61:30:c3:a6:6f:44:1f:77:9b:aa:a1:22:48:a9:dd:d0:45:86:d1:a1:82:53:13:c4:03:06:a3",
                BKEY),
    };

    static void runTests(Test[] tests) throws Exception {
        long start = System.currentTimeMillis();
        Provider p = Security.getProvider("SunJCE");
        System.out.println("Testing provider " + p.getName() + "...");
        Mac.getInstance("HmacSHA256", p);
        Mac.getInstance("HmacSHA384", p);
        Mac.getInstance("HmacSHA512", p);
        KeyGenerator.getInstance("HmacSHA256", p);
        KeyGenerator.getInstance("HmacSHA384", p);
        KeyGenerator.getInstance("HmacSHA512", p);
        for (int i = 0; i < tests.length; i++) {
            Test test = tests[i];
            test.run(p);
        }
        System.out.println("All tests passed");
        long stop = System.currentTimeMillis();
        System.out.println("Done (" + (stop - start) + " ms).");
    }

    public static void main(String[] args) throws Exception {
        runTests(tests);
    }

}
