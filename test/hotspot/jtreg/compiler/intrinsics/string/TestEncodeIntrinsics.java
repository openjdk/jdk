/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @key randomness
 * @bug 6896617 8274242
 * @summary Verify potentially intrinsified encoders behave well before and after compilation
 * @library /test/lib
 *
 * @run main/othervm/timeout=1200 --add-opens=java.base/sun.nio.cs=ALL-UNNAMED -Xbatch -Xmx256m compiler.intrinsics.string.TestEncodeIntrinsics
 */

package compiler.intrinsics.string;

import jdk.test.lib.Utils;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;
import java.util.Random;

public class TestEncodeIntrinsics {
    final static int SIZE = 256;

    public static void main(String[] args) {

        test("ISO-8859-1", false);
        test("UTF-8", true);
        test("US-ASCII", true);
        test("CESU-8", true);
    }

    private static void test(String csn, boolean asciiOnly) {
        try {
            System.out.println("Testing " + csn);
            Charset cs = Charset.forName(csn);
            CharsetEncoder enc = cs.newEncoder();
            enc.onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE);
            CharsetDecoder dec = cs.newDecoder();
            dec.onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE);

            byte repl = (byte) '?';
            enc.replaceWith(new byte[]{repl});

            // Populate char[] with chars which can be encoded by ISO_8859_1 (<= 0xFF)
            // - or ASCII (<= 0x7F) if requested
            Random rnd = Utils.getRandomInstance();
            int maxchar = asciiOnly ? 0x7F : 0xFF;
            char[] a = new char[SIZE];
            byte[] b = new byte[SIZE];
            char[] at = new char[SIZE];
            byte[] bt = new byte[SIZE];
            for (int i = 0; i < SIZE; i++) {
                char c = (char) rnd.nextInt(maxchar);
                if (!enc.canEncode(c)) {
                    System.out.printf("Something wrong: can't encode c=%03x\n", (int) c);
                    System.exit(97);
                }
                a[i] = c;
                b[i] = (byte) c;
                at[i] = (char) -1;
                bt[i] = (byte) -1;
            }

            Method encodeArray = null;
            if (csn.equals("ISO-8859-1")) {
                // Use internal API for tests
                encodeArray = enc.getClass().getDeclaredMethod("encodeISOArray",
                        char[].class, int.class, byte[].class, int.class, int.class);
                encodeArray.setAccessible(true);
                if ((int) encodeArray.invoke(enc, a, 0, bt, 0, SIZE) != SIZE || !Arrays.equals(b, bt)) {
                    System.out.println("Something wrong: ArrayEncoder.encode failed");
                    System.exit(97);
                }
                for (int i = 0; i < SIZE; i++) {
                    at[i] = (char) -1;
                }
            }

            ByteBuffer bb = ByteBuffer.wrap(b);
            CharBuffer ba = CharBuffer.wrap(a);
            ByteBuffer bbt = ByteBuffer.wrap(bt);
            CharBuffer bat = CharBuffer.wrap(at);
            if (!enc.encode(ba, bbt, true).isUnderflow() || !Arrays.equals(b, bt)) {
                System.out.println("Something wrong: Encoder.encode failed");
                System.exit(97);
            }
            if (!dec.decode(bb, bat, true).isUnderflow() || !Arrays.equals(a, at)) {
                System.out.println("Something wrong: Decoder.decode failed (a == at: " + !Arrays.equals(a, at) + ")");
                System.exit(97);
            }
            for (int i = 0; i < SIZE; i++) {
                at[i] = (char) -1;
                bt[i] = (byte) -1;
            }

            // Warm up
            boolean failed = false;

            if (csn.equals("ISO-8859-1")) {
                for (int i = 0; i < 10000; i++) {
                    failed |= (int) encodeArray.invoke(enc, a, 0, bt, 0, SIZE) != SIZE;
                }
                for (int i = 0; i < 10000; i++) {
                    failed |= (int) encodeArray.invoke(enc, a, 0, bt, 0, SIZE) != SIZE;
                }
                for (int i = 0; i < 10000; i++) {
                    failed |= (int) encodeArray.invoke(enc, a, 0, bt, 0, SIZE) != SIZE;
                }
                if (failed || !Arrays.equals(b, bt)) {
                    failed = true;
                    System.out.println("Failed: ISO_8859_1$Encoder.encode char[" + SIZE + "]");
                }
            }

            for (int i = 0; i < SIZE; i++) {
                at[i] = (char) -1;
                bt[i] = (byte) -1;
            }

            boolean is_underflow = true;
            for (int i = 0; i < 10000; i++) {
                ba.clear();
                bb.clear();
                bat.clear();
                bbt.clear();
                boolean enc_res = enc.encode(ba, bbt, true).isUnderflow();
                boolean dec_res = dec.decode(bb, bat, true).isUnderflow();
                is_underflow = is_underflow && enc_res && dec_res;
            }
            for (int i = 0; i < SIZE; i++) {
                at[i] = (char) -1;
                bt[i] = (byte) -1;
            }
            for (int i = 0; i < 10000; i++) {
                ba.clear();
                bb.clear();
                bat.clear();
                bbt.clear();
                boolean enc_res = enc.encode(ba, bbt, true).isUnderflow();
                boolean dec_res = dec.decode(bb, bat, true).isUnderflow();
                is_underflow = is_underflow && enc_res && dec_res;
            }
            for (int i = 0; i < SIZE; i++) {
                at[i] = (char) -1;
                bt[i] = (byte) -1;
            }
            for (int i = 0; i < 10000; i++) {
                ba.clear();
                bb.clear();
                bat.clear();
                bbt.clear();
                boolean enc_res = enc.encode(ba, bbt, true).isUnderflow();
                boolean dec_res = dec.decode(bb, bat, true).isUnderflow();
                is_underflow = is_underflow && enc_res && dec_res;
            }
            if (!is_underflow) {
                failed = true;
                System.out.println("Failed: got a non-underflow");
            }
            if (!Arrays.equals(b, bt)) {
                failed = true;
                System.out.println("Failed: b != bt");
            }
            if (!Arrays.equals(a, at)) {
                failed = true;
                System.out.println("Failed: a != at");
            }

            // Test encoder with chars outside of the range the intrinsic deals with
            System.out.println("Testing big char");

            bt = new byte[SIZE + 10]; // add some spare room to deal with encoding multi-byte
            ba = CharBuffer.wrap(a);
            bbt = ByteBuffer.wrap(bt);
            for (int i = 1; i <= SIZE; i++) {
                for (int j = 0; j < i; j++) {
                    char bigChar = (char)((asciiOnly ? 0x7F : 0xFF) + 1 + rnd.nextInt(0x100));
                    char aOrig = a[j];
                    a[j] = bigChar;
                    // make sure to replace with a different byte
                    bt[j] = (byte)(bt[j] + 1);
                    ba.clear();
                    ba.limit(i);
                    bbt.clear();
                    if (!enc.encode(ba, bbt, true).isUnderflow()) {
                        failed = true;
                        System.out.println("Failed: encode char[" + i + "] to byte[" + i + "]: expected underflow");
                    }
                    if (bt[j] == b[j] && b[j] != repl) { // b[j] can be equal to repl; ignore
                        failed = true;
                        System.out.println("Failed: different byte expected at pos bt[" + j + "]");
                    }
                    if (!enc.canEncode(bigChar) && bt[j] != repl) {
                        failed = true;
                        System.out.println("Failed: encoded replace byte[" + j + "] (" + bt[j] + ") != " + repl);
                    }

                    // Check that all bytes prior to the replaced one was encoded properly
                    for (int k = 0; k < j; k++) {
                        if (bt[k] != b[k]) {
                            failed = true;
                            System.out.println("Failed: encoded byte[" + k + "] (" + bt[k] + ") != " + b[k]);
                        }
                    }
                    a[j] = aOrig; // Restore
                }
            }

            if (failed) {
                System.out.println("FAILED");
                System.exit(97);
            }
            System.out.println("PASSED");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("FAILED");
            System.exit(97);
        }
    }
}
