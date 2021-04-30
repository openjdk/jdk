/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/* @test
   @bug 8266013
   @summary Checks stateful encoder's replacement logic
*/

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.CoderResult;
import java.util.Arrays;
import java.io.UnsupportedEncodingException;

public class StatefulEncoder {
    static int errCnt = 0;
    final static String bugID = "8266013";

    private static void checkGetBytes(Charset cs, char[] chsA, char[] chsB) {
        byte[] a = (new String(chsA)).getBytes(cs);
        byte[] b = (new String(chsB)).getBytes(cs);
        if (!Arrays.equals(a, b)) {
            errCnt++;
            System.err.print("getBytes: "+cs.name()+": ");
            for (byte b1 : a) System.err.printf("\\x%02X", (int)b1&0xFF);
            System.err.print("<->");
            for (byte b1 : b) System.err.printf("\\x%02X", (int)b1&0xFF);
            System.err.println();
        }
    }

    private static void checkGetBytes1(Charset cs, char[] chsA, char[] chsB) {
        String csName = cs.name();
        try {
            byte[] a = (new String(chsA)).getBytes(csName);
            byte[] b = (new String(chsB)).getBytes(csName);
            if (!Arrays.equals(a, b)) {
                errCnt++;
                System.err.print("getBytes1:"+csName+": ");
                for (byte b1 : a) System.err.printf("\\x%02X", (int)b1&0xFF);
                System.err.print("<->");
                for (byte b1 : b) System.err.printf("\\x%02X", (int)b1&0xFF);
                System.err.println();
            }
        } catch (UnsupportedEncodingException uee) {
            new Error(uee);
        }
    }

    private static void checkEncode(Charset cs, char[] chsA, char[] chsB, byte[] repl) {
        CharsetEncoder ce = cs.newEncoder()
                              .onMalformedInput(CodingErrorAction.REPLACE)
                              .onUnmappableCharacter(CodingErrorAction.REPLACE);
        if (repl != null) {
            try {
                ce.replaceWith(repl);
            } catch (IllegalArgumentException iae) {
                System.err.println(cs+":");
                for (byte b: repl) System.err.printf("\\x%02X", (int)b&0xFF);
                System.err.println(":"+iae);
                return;
            }
        }
        try {
            ce.reset();
            ByteBuffer bbA = ce.encode(CharBuffer.wrap(chsA));
            byte[] a = Arrays.copyOf(bbA.array(), bbA.limit());
            ce.reset();
            ByteBuffer bbB = ce.encode(CharBuffer.wrap(chsB));
            byte[] b = Arrays.copyOf(bbB.array(), bbB.limit());
            if (!Arrays.equals(a, b)) {
                errCnt++;
                System.err.print("encode:   "+cs.name()+": ");
                for (byte b1 : a) System.err.printf("\\x%02X", (int)b1&0xFF);
                System.err.print("<->");
                for (byte b1 : b) System.err.printf("\\x%02X", (int)b1&0xFF);
                System.err.println();
            }
        } catch (CharacterCodingException cce) {
            errCnt++;
            System.err.println(cce);
        }
    }

    public static void main(String[] args) throws Exception {
        for (Charset cs : Charset.availableCharsets().values()) {
            if (cs.canEncode()) {
                CharsetEncoder ce = cs.newEncoder();
                if (ce.canEncode(' ') && ce.canEncode('\u3000')) {
                    byte[] repl = ce.replacement();
                    String replStr = new String(repl, cs);
                    char[] replChsA = new char[2];
                    char[] replChsB = new char[2];
                    replChsA[1] = '\uD800';
                    replChsA[0] = ' ';
                    replChsB[0] = ' ';
                    replChsB[1] = replStr.length() > 1 ? '\uFFFD' : replStr.charAt(0);
                    checkGetBytes(cs, replChsA, replChsB);
                    checkGetBytes1(cs, replChsA, replChsB);
                    checkEncode(cs, replChsA, replChsB, null);
                    if (ce.canEncode('?')) {
                        replChsB[1] = '?';
                        checkEncode(cs, replChsA, replChsB, "?".getBytes(cs));
                    }
                    if (ce.canEncode('\uff1f')) {
                        replChsB[1] = '\uff1f';
                        checkEncode(cs, replChsA, replChsB, "\uff1f".getBytes(cs));
                    }
                    replChsA[0] = '\u3000';
                    replChsB[0] = '\u3000';
                    replChsB[1] = replStr.length() > 1 ? '\uFFFD' : replStr.charAt(0);
                    checkGetBytes(cs, replChsA, replChsB);
                    checkGetBytes1(cs, replChsA, replChsB);
                    checkEncode(cs, replChsA, replChsB, null);
                    if (ce.canEncode('?')) {
                        replChsB[1] = '?';
                        checkEncode(cs, replChsA, replChsB, "?".getBytes(cs));
                    }
                    if (ce.canEncode('\uff1f')) {
                        replChsB[1] = '\uff1f';
                        checkEncode(cs, replChsA, replChsB, "\uff1f".getBytes(cs));
                    }
                }
            }
        }
        if (errCnt > 0)
            throw new Exception("failure of test for bug " + bugID);

    }
}
