/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.spec.Argon2ParameterSpec;
import javax.crypto.KDF;
import javax.crypto.SecretKey;
import static javax.crypto.spec.Argon2ParameterSpec.Type;
import static javax.crypto.spec.Argon2ParameterSpec.Builder;
import com.sun.crypto.provider.Argon2Impl;

/**
 * @test
 * @bug 8253914
 * @modules java.base/com.sun.crypto.provider:+open
 * @summary Test the Argon2id KDF impl with test values from RFC 9106
 *     and self generated ones.
 */
public class TestArgon2KAT {

    // return a byte[] with length = 'len' and filled w/ 'byteVal'
    private static byte[] genData(int len, byte byteVal) {
        byte[] res = new byte[len];
        Arrays.fill(res, byteVal);
        return res;
    }

    private static Argon2ParameterSpec genParams(Type type, byte[] msg,
            byte[] salt, int parallelism, int memory, int iteration,
            byte[] secret, byte[] ad, int tagLen) {
        Builder b = Argon2ParameterSpec.newBuilder(type).nonce(salt)
                .parallelism(parallelism).memoryKiB(memory)
                .iterations(iteration).tagLen(tagLen);
        if (secret != null && secret.length > 0) {
            b = b.secret(secret);
        }
        if (ad != null && ad.length > 0) {
            b = b.ad(ad);
        }
        return b.build(msg);
    }

    private static void checkOutputs(String expectedHex, byte[]... res) {
        byte[] expected = HexFormat.of().parseHex(expectedHex);
        int i = 0;
        for (byte[] b : res) {
            i++;
            if (!Arrays.equals(expected, b)) {
                System.out.println("Expected: " + expectedHex);
                System.out.println("Actual:   " + HexFormat.of().formatHex(b));
                throw new RuntimeException(i + "th tag check failed!");
            }
        }
    }

    private static void run(Argon2ParameterSpec spec, String expected)
            throws Exception {
        System.out.println("test against: " + spec);
        Type t = spec.type();
        switch (t) {
            case ARGON2I:
            case ARGON2D:
                byte[] res = new Argon2Impl(t).derive(spec);
                checkOutputs(expected, res);
                break;
            case ARGON2ID:
                byte[] res1 = new Argon2Impl(t).derive(spec);
                SecretKey res2 = KDF.getInstance(t.name()).deriveKey("Generic", spec);
                try {
                    checkOutputs(expected, res1, res2.getEncoded());
                } catch (RuntimeException re) {
                    System.out.println("Failed: "  + res2);
                    throw re;
                }
                break;
        }
    }

    // Argon2id only
    private static void runPHC(Argon2ParameterSpec spec, String expected)
            throws Exception {
        System.out.println("test against: " + spec);
        Type t = spec.type();
        switch (t) {
            case ARGON2ID:
                SecretKey res = KDF.getInstance(t.name()).deriveKey("Generic", spec);
                String actual = res.toString();
                if (!actual.equalsIgnoreCase(expected)) {
                    System.out.println("Expected: " + expected);
                    System.out.println("Actual:   " + actual);
                    throw new RuntimeException("PHC diff check failed!");
                }
                break;
            case ARGON2I:
            case ARGON2D:
                throw new RuntimeException("Unsupported type");
        }
    }

    private static void rfc9106KAT() throws Exception {
        int mKiB = 32;
        int passes = 3;
        int parallelism = 4;
        int tagLen = 32;
        byte[] passwd = genData(32, (byte) 1);
        byte[] salt = genData(16, (byte) 2);
        byte[] secret = genData(8, (byte) 3);
        byte[] ad = genData(12, (byte) 4);

        Argon2ParameterSpec kdfParams = genParams(Type.ARGON2D,
                passwd, salt, parallelism, mKiB, passes, secret, ad, tagLen);
        String expected = "512b391b6f1162975371d30919734294" +
                "f868e3be3984f3c1a13a4db9fabe4acb";
        run(kdfParams, expected);

        kdfParams = genParams(Type.ARGON2I, passwd, salt, parallelism, mKiB,
                passes, secret, ad, tagLen);
        expected = "c814d9d1dc7f37aa13f0d77f2494bda1" +
                "c8de6b016dd388d29952a4c4672b6ce8";
        run(kdfParams, expected);

        kdfParams = genParams(Type.ARGON2ID, passwd, salt, parallelism, mKiB,
                passes, secret, ad, tagLen);
        expected = "0d640df58d78766c08c037a34a8b53c9d0" +
                "1ef0452d75b65eb52520e96b01e659";
        run(kdfParams, expected);
    }

    private static void selfTest() throws Exception {
        int iterations = 3;
        int memory = 1 << 12;
        int parallelism = 1;
        int tagLen = 32;

        byte[] testpwd = "pasword".getBytes();
        byte[] testsalt = "somesalt".getBytes();

        Argon2ParameterSpec kdfParams = genParams(Type.ARGON2I, testpwd,
            testsalt, parallelism, memory, iterations, null, null, tagLen);
        String expected =
            "957fc0727d83f4060bb0f1071eb590a19a8c448fc0209497ee4f54ca241f3c90";
        run(kdfParams, expected);

        kdfParams = genParams(Type.ARGON2D, testpwd,
            testsalt, parallelism, memory, iterations, null, null, tagLen);
        expected =
            "0b3f09e7b8d036e58ccd08f08cb6babf7e5e2463c26bcf2a9e4ea70d747c4098";
        run(kdfParams, expected);

        kdfParams = genParams(Type.ARGON2ID, testpwd,
            testsalt, parallelism, memory, iterations, null, null, tagLen);
        expected =
            "f55535bfe948710051424c7424b11ba9a13a50239b0459f56ca695ea14bc195e";
        run(kdfParams, expected);

        Base64.Decoder dec = Base64.getDecoder();

        iterations = 2;
        memory = 1 << 16;
        testpwd = "hunter2".getBytes();
        testsalt = dec.decode("gZiV/M1gPc22ElAH/Jh1Hw");
        byte[] secret = "pepper".getBytes();
        kdfParams = genParams(Type.ARGON2ID, testpwd,
            testsalt, parallelism, memory, iterations, secret, null, tagLen);
        expected =
            "0963ab928a3ba09050fe2ca1eee2742ced9a2c47eb1f04d6965480c53d33467a";
        run(kdfParams, expected);
    }

    private static void selfTest2() throws Exception {
        byte[] msg = "somepasswordvalues".getBytes();
        String[] expectedPHCs = {
            "$argon2id$v=19$m=65536,t=1,p=4$MDkwOTA5MDkwOTA5$4UOEHDZiPmXffE2xQjfvCugdzPNOfHhw/Lz8sDj8uY0",
            "$argon2id$v=19$m=65536,t=1,p=4$MDUwNTA1MDUwNTA1MDUwNQ$QGNATFLKRkNRzDx2x48tZ3ImZHPhqMJIOItXzHmzHvA",
            "$argon2id$v=19$m=65536,t=2,p=4$MDUwNTA1MDUwNTA1MDUwNTA2$ewqaCfiXjuogIl3E6ZMxmFVn4ovDISEbIYxYgpMMmdM",
            "$argon2id$v=19$m=32,t=3,p=3$MDUwNTA1MDUwNTA1MDUwNTAxMDEwMTAxMDEwMTAxMDE$4FLFMPU5r7ITZ6KIusKkWJUUXoU80c5+bszfl+Md4KQ",
            "$argon2id$v=19$m=130072,t=3,p=3$MDUwNTA1MDUwNTA1MDUwNTAxMDEwMTAxMDEwMTAxMDE$zqKZdwMOgPTL2lglwnXeJiYwnduI3g8pRYuh55A4ikY",
            "$argon2id$v=19$m=66560,t=3,p=3$MDUwNTA1MDUwNTA1MDUwNTAxMDEwMTAxMDEwMTAxMDE$lABKLpy9mARU8P5gKIh0eWwBjCB7yq1NRPufJEiow/g"
        };
        for (String e : expectedPHCs) {
            Argon2ParameterSpec params =
                Argon2ParameterSpec.newBuilder(e).build(msg);
            runPHC(params, e);
        }
    }

    public static void main(String[] argv) throws Exception {
        rfc9106KAT();
        selfTest();
        selfTest2();
        System.out.println("Tests passed");
    }
}
