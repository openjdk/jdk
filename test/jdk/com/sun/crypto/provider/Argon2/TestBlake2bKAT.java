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
import java.util.HexFormat;
import com.sun.crypto.provider.Blake2b;

/**
 * @test
 * @bug 8253914
 * @modules java.base/com.sun.crypto.provider:+open
 *          java.base/sun.security.provider
 * @summary Test the Blake2b impl with various known answer tests vectors
 */
public class TestBlake2bKAT {

    private static final HexFormat formatter = HexFormat.of();

    private static final String B96 =
            "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f" +
            "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f" +
            "404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f";

    public static void main(String[] argv) {
        // RFC 7693, appendix A
        byte[] abc = { 'a', 'b', 'c' };
        test(512, abc, "BA80A53F981C4D0D6A2797B69F12F6E9" +
                "4C212F14685AC4B74B12BB6FDBFFA2D1" +
                "7D87C5392AAB792DC252D5DE4533CC95" +
                "18D38AA8DBF1925AB92386EDD4009923");

        // RFC 7693, appendix E
        selfTest();

        // self-generate
        testWithKey(512, null, B96.substring(0,32),
                "be9ba4d16e01a2bcdfad4ae51984e0bb9fe88a5e34da8387ffd1152bd0" +
                "bbb7d9118bb368a6e6ddd8dd0adf46d58fcd368b744ca8452c8c20d225" +
                "eb3741f307bb");
        testWithKey(512, "11", B96.substring(0,64),
                "303dabde01c4ef2e7bd08661930283eb4151f2b6499e2daa9ba9d07b75" +
                "69a2a156d0cef9a8b7fa0ac53458badd469343d68c9c6beedd525a371d" +
                "8dbc8716387b");
        testWithKey(512, "2222", B96.substring(0,80),
                "15d64cfa1d15c918f003bb2c414e9b9a6b439f491912687fbe97140389" +
                "37cdf2c8573bd4fcb0d96ddd957fdc13223a27c28ff262200224c41cde" +
                "10ef3abf2060");
        testWithKey(512, B96 + B96 + B96, B96.substring(0, 128),
                "c647fa415a3b87a1fd672a28a46483cb9396e8f10a264cb0d99254296e" +
                "f631473ef898d30437e943b0f02cae54b24bb78c996226c759205683ad" +
                "d35e9fa4116b");
    }

    private static byte[] doIt(int outLen, byte[] key, byte[] in, int inOfs,
            int inLen) {
        byte[] out = new byte[outLen];
        doIt(outLen, key, in, inOfs, inLen, out, 0);
        return out;
    }

    private static int doIt(int outLen, byte[] key, byte[] in, int inOfs,
            int inLen, byte[] outBuf, int outOfs) {
        Blake2b res = new Blake2b(outLen, key);
        res.update(in, inOfs, inLen);
        return res.doFinal(outBuf, outOfs);
    }

    public static void test(int outLenBits, byte[] in, String outHex) {
        System.out.println("test Blake2b-" + outLenBits);
        int digestLen = outLenBits >>> 3;
        byte[] expOut = formatter.parseHex(outHex.toLowerCase());
        in = (in == null ? new byte[0] : in);
        System.out.println("=> inLen = " + in.length);

        byte[] out = doIt(digestLen, null, in, 0, in.length);
        if (Arrays.compare(out, expOut) != 0) {
            System.out.println("got: " + formatter.formatHex(out));
            System.out.println("exp: " + outHex);
            throw new RuntimeException("Failed diff check!");
        }
    }

    public static void testWithKey(int outLenBits, String inHex, String keyHex,
            String outHex) {
        System.out.println("testWithKey Blake2b-" + outLenBits);
        int digestLen = outLenBits >>> 3;
        byte[] expOut = formatter.parseHex(outHex);
        if (digestLen != expOut.length) {
            throw new RuntimeException("Output Len mismatches!");
        }

        byte[] in = (inHex == null || inHex.isEmpty()) ?
                new byte[0] : formatter.parseHex(inHex);
        byte[] key = formatter.parseHex(keyHex);

        System.out.println(String.format("=> inLen=%d, keyLen = %d",
                in.length, key.length));

        byte[] out = doIt(digestLen, key, in, 0, in.length);
        if (Arrays.compare(out, expOut) != 0) {
            System.out.println("got: " + formatter.formatHex(out));
            System.out.println("exp: " + outHex);
            throw new RuntimeException("Failed diff check!");
        }
    }

    private static long INT_MASK = 0x0FFFFFFFFL;

    // testing util 'selftest_seq':
    // fills 'out' with 'len' bytes of self-generated data using seed
    private static void generateData(byte[] out, int len, int seed) {
        long t, a, b;

        a = (0xDEAD4BADL * seed);              // prime
        b = 1L;

        for (int i = 0; i < len; i++) {         // fill the buf
            t = (a + b) & INT_MASK;
            a = b;
            b = t;
            out[i] = (byte) ((t >>> 24) & 0xFF);
        }
    }

    // blake2b_selftest() in RFC 7693
    public static void selfTest() {
        System.out.println("test from Appendix E");

        // grand hash of hash results
        byte[] blake2b_res = {
            (byte)0xC2, (byte)0x3A, (byte)0x78, (byte)0x00,
            (byte)0xD9, (byte)0x81, (byte)0x23, (byte)0xBD,
            (byte)0x10, (byte)0xF5, (byte)0x06, (byte)0xC6,
            (byte)0x1E, (byte)0x29, (byte)0xDA, (byte)0x56,
            (byte)0x03, (byte)0xD7, (byte)0x63, (byte)0xB8,
            (byte)0xBB, (byte)0xAD, (byte)0x2E, (byte)0x73,
            (byte)0x7F, (byte)0x5E, (byte)0x76, (byte)0x5A,
            (byte)0x7B, (byte)0xCC, (byte)0xD4, (byte)0x75
        };
        // parameter sets
        int[] b2b_md_len = { 20, 32, 48, 64 };
        int[] b2b_in_len = { 0, 3, 128, 129, 255, 1024 };

        byte[] in = new byte[1024];
        byte[] md = new byte[64];
        byte[] key = new byte[64];
        Blake2b ctx = new Blake2b(32);

        for (int outlen : b2b_md_len) {
            for (int inlen : b2b_in_len) {
                // unkeyed hash
                generateData(in, inlen, inlen);

                int actLen = doIt(outlen, null, in, 0, inlen, md, 0);
                ctx.update(md, 0, outlen);   // hash the hash

                generateData(key, outlen, outlen);  // keyed hash
                actLen = doIt(outlen, Arrays.copyOf(key, outlen),
                        in, 0, inlen, md, 0);
                ctx.update(md, 0, outlen);   // hash the hash
            }
        }

        // compute and compare the hash of hashes
        int finalLen = ctx.doFinal(md, 0);
        System.out.println("finalLen = " + finalLen);

        for (int i = 0; i < 32; i++) {
            if (md[i] != blake2b_res[i]) {
                System.out.println("got: " + formatter.formatHex(md));
                System.out.println("exp: " + formatter.formatHex(blake2b_res));
                throw new RuntimeException("Failed diff check! offset = " + i);
            }
        }
    }
}
