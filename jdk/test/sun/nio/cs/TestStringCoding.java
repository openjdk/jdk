/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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
   @bug 6636323 6636319 7040220 7096080 7183053 8080248
   @summary Test if StringCoding and NIO result have the same de/encoding result
 * @run main/othervm/timeout=2000 TestStringCoding
 * @key randomness
 */

import java.util.*;
import java.nio.*;
import java.nio.charset.*;

public class TestStringCoding {
    public static void main(String[] args) throws Throwable {

        for (Boolean hasSM: new boolean[] { false, true }) {
            if (hasSM)
                System.setSecurityManager(new PermissiveSecurityManger());
            for (Charset cs:  Charset.availableCharsets().values()) {
                if ("ISO-2022-CN".equals(cs.name()) ||
                    "x-COMPOUND_TEXT".equals(cs.name()) ||
                    "x-JISAutoDetect".equals(cs.name()))
                    continue;
                System.out.printf("Testing(sm=%b) " + cs.name() + "....", hasSM);
                // full bmp first
                char[] bmpCA = new char[0x10000];
                for (int i = 0; i < 0x10000; i++) {
                     bmpCA[i] = (char)i;
                }
                byte[] sbBA = new byte[0x100];
                for (int i = 0; i < 0x100; i++) {
                    sbBA[i] = (byte)i;
                }
                test(cs, bmpCA, sbBA);
                // "randomed" sizes
                Random rnd = new Random();
                for (int i = 0; i < 10; i++) {
                    int clen = rnd.nextInt(0x10000);
                    int blen = rnd.nextInt(0x100);
                    //System.out.printf("    blen=%d, clen=%d%n", blen, clen);
                    test(cs, Arrays.copyOf(bmpCA, clen), Arrays.copyOf(sbBA, blen));
                    //add a pair of surrogates
                    int pos = clen / 2;
                    if ((pos + 1) < blen) {
                        bmpCA[pos] = '\uD800';
                        bmpCA[pos+1] = '\uDC00';
                    }
                    test(cs, Arrays.copyOf(bmpCA, clen), Arrays.copyOf(sbBA, blen));
                }

                testMixed(cs);
                System.out.println("done!");
            }
        }
    }

    static void testMixed(Charset cs) throws Throwable {
        CharsetDecoder dec = cs.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE);
        CharsetEncoder enc = cs.newEncoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE);
        List<Integer> cps = new ArrayList<>(0x10000);
        int off = 0;
        int cp = 0;
        while (cp < 0x10000) {
            if (enc.canEncode((char)cp)) {
               cps.add(cp);
            }
            cp++;
        }
        Collections.shuffle(cps);
        char[] bmpCA = new char[cps.size()];
        for (int i = 0; i < cps.size(); i++)
            bmpCA[i] = (char)(int)cps.get(i);
        String bmpStr = new String(bmpCA);
        //getBytes(csn);
        byte[] bmpBA = bmpStr.getBytes(cs.name());
        ByteBuffer bf = enc.reset().encode(CharBuffer.wrap(bmpCA));
        byte[] baNIO = new byte[bf.limit()];
        bf.get(baNIO, 0, baNIO.length);
        if (!Arrays.equals(bmpBA, baNIO)) {
            throw new RuntimeException("getBytes(csn) failed  -> " + cs.name());
        }

        //getBytes(cs);
        bmpBA = bmpStr.getBytes(cs);
        if (!Arrays.equals(bmpBA, baNIO))
            throw new RuntimeException("getBytes(cs) failed  -> " + cs.name());

        //new String(csn);
        String strSC = new String(bmpBA, cs.name());
        String strNIO = dec.reset().decode(ByteBuffer.wrap(bmpBA)).toString();
        if(!strNIO.equals(strSC)) {
            throw new RuntimeException("new String(csn) failed  -> " + cs.name());
        }

        //new String(cs);
        strSC = new String(bmpBA, cs);
        if (!strNIO.equals(strSC))
            throw new RuntimeException("new String(cs) failed  -> " + cs.name());

    }

    static void test(Charset cs, char[] bmpCA, byte[] sbBA) throws Throwable {
        String bmpStr = new String(bmpCA);
        CharsetDecoder dec = cs.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE);
        CharsetEncoder enc = cs.newEncoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE);

        //getBytes(csn);
        byte[] baSC = bmpStr.getBytes(cs.name());
        ByteBuffer bf = enc.reset().encode(CharBuffer.wrap(bmpCA));
        byte[] baNIO = new byte[bf.limit()];
        bf.get(baNIO, 0, baNIO.length);
        if (!Arrays.equals(baSC, baNIO))
            throw new RuntimeException("getBytes(csn) failed  -> " + cs.name());

        //getBytes(cs);
        baSC = bmpStr.getBytes(cs);
        if (!Arrays.equals(baSC, baNIO))
            throw new RuntimeException("getBytes(cs) failed  -> " + cs.name());

        //new String(csn);
        String strSC = new String(sbBA, cs.name());
        String strNIO = dec.reset().decode(ByteBuffer.wrap(sbBA)).toString();

        if(!strNIO.equals(strSC))
            throw new RuntimeException("new String(csn) failed  -> " + cs.name());

        //new String(cs);
        strSC = new String(sbBA, cs);
        if (!strNIO.equals(strSC))
            throw new RuntimeException("new String(cs) failed  -> " + cs.name());

        //encode unmappable surrogates
        if (enc instanceof sun.nio.cs.ArrayEncoder &&
            cs.contains(Charset.forName("ASCII"))) {
            if (cs.name().equals("UTF-8") ||     // utf8 handles surrogates
                cs.name().equals("CESU-8"))      // utf8 handles surrogates
                return;
            enc.replaceWith(new byte[] { (byte)'A'});
            sun.nio.cs.ArrayEncoder cae = (sun.nio.cs.ArrayEncoder)enc;

            String str = "ab\uD800\uDC00\uD800\uDC00cd";
            byte[] ba = new byte[str.length() - 2];
            int n = cae.encode(str.toCharArray(), 0, str.length(), ba);
            if (n != 6 || !"abAAcd".equals(new String(ba, cs.name())))
                throw new RuntimeException("encode1(surrogates) failed  -> "
                                           + cs.name());

            ba = new byte[str.length()];
            n = cae.encode(str.toCharArray(), 0, str.length(), ba);
            if (n != 6 || !"abAAcd".equals(new String(ba, 0, n,
                                                     cs.name())))
                throw new RuntimeException("encode2(surrogates) failed  -> "
                                           + cs.name());
            str = "ab\uD800B\uDC00Bcd";
            ba = new byte[str.length()];
            n = cae.encode(str.toCharArray(), 0, str.length(), ba);
            if (n != 8 || !"abABABcd".equals(new String(ba, 0, n,
                                                       cs.name())))
                throw new RuntimeException("encode3(surrogates) failed  -> "
                                           + cs.name());
            /* sun.nio.cs.ArrayDeEncoder works on the assumption that the
               invoker (StringCoder) allocates enough output buf, utf8
               and double-byte coder does not check the output buffer limit.
            ba = new byte[str.length() - 1];
            n = cae.encode(str.toCharArray(), 0, str.length(), ba);
            if (n != 7 || !"abABABc".equals(new String(ba, 0, n, cs.name()))) {
                throw new RuntimeException("encode4(surrogates) failed  -> "
                                           + cs.name());
            }
            */
        }

        //encode mappable surrogates for hkscs
        if (cs.name().equals("Big5-HKSCS") || cs.name().equals("x-MS950-HKSCS")) {
            String str = "ab\uD840\uDD0Ccd";
            byte[] expected = new byte[] {(byte)'a', (byte)'b',
                (byte)0x88, (byte)0x45, (byte)'c', (byte)'d' };
            if (!Arrays.equals(str.getBytes(cs.name()), expected) ||
                !Arrays.equals(str.getBytes(cs), expected)) {
                throw new RuntimeException("encode(surrogates) failed  -> "
                                           + cs.name());
            }
        }
    }

    static class PermissiveSecurityManger extends SecurityManager {
        @Override public void checkPermission(java.security.Permission p) {}
    }
}
