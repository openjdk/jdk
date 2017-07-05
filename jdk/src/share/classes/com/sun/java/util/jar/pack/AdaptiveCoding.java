/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.sun.java.util.jar.pack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import static com.sun.java.util.jar.pack.Constants.*;

/**
 * Adaptive coding.
 * See the section "Adaptive Encodings" in the Pack200 spec.
 * @author John Rose
 */
class AdaptiveCoding implements CodingMethod {
    CodingMethod headCoding;
    int          headLength;
    CodingMethod tailCoding;

    public AdaptiveCoding(int headLength, CodingMethod headCoding, CodingMethod tailCoding) {
        assert(isCodableLength(headLength));
        this.headLength = headLength;
        this.headCoding = headCoding;
        this.tailCoding = tailCoding;
    }

    public void setHeadCoding(CodingMethod headCoding) {
        this.headCoding = headCoding;
    }
    public void setHeadLength(int headLength) {
        assert(isCodableLength(headLength));
        this.headLength = headLength;
    }
    public void setTailCoding(CodingMethod tailCoding) {
        this.tailCoding = tailCoding;
    }

    public boolean isTrivial() {
        return headCoding == tailCoding;
    }

    // CodingMethod methods.
    public void writeArrayTo(OutputStream out, int[] a, int start, int end) throws IOException {
        writeArray(this, out, a, start, end);
    }
    // writeArrayTo must be coded iteratively, not recursively:
    private static void writeArray(AdaptiveCoding run, OutputStream out, int[] a, int start, int end) throws IOException {
        for (;;) {
            int mid = start+run.headLength;
            assert(mid <= end);
            run.headCoding.writeArrayTo(out, a, start, mid);
            start = mid;
            if (run.tailCoding instanceof AdaptiveCoding) {
                run = (AdaptiveCoding) run.tailCoding;
                continue;
            }
            break;
        }
        run.tailCoding.writeArrayTo(out, a, start, end);
    }

    public void readArrayFrom(InputStream in, int[] a, int start, int end) throws IOException {
        readArray(this, in, a, start, end);
    }
    private static void readArray(AdaptiveCoding run, InputStream in, int[] a, int start, int end) throws IOException {
        for (;;) {
            int mid = start+run.headLength;
            assert(mid <= end);
            run.headCoding.readArrayFrom(in, a, start, mid);
            start = mid;
            if (run.tailCoding instanceof AdaptiveCoding) {
                run = (AdaptiveCoding) run.tailCoding;
                continue;
            }
            break;
        }
        run.tailCoding.readArrayFrom(in, a, start, end);
    }

    public static final int KX_MIN = 0;
    public static final int KX_MAX = 3;
    public static final int KX_LG2BASE = 4;
    public static final int KX_BASE = 16;

    public static final int KB_MIN = 0x00;
    public static final int KB_MAX = 0xFF;
    public static final int KB_OFFSET = 1;
    public static final int KB_DEFAULT = 3;

    static int getKXOf(int K) {
        for (int KX = KX_MIN; KX <= KX_MAX; KX++) {
            if (((K - KB_OFFSET) & ~KB_MAX) == 0)
                return KX;
            K >>>= KX_LG2BASE;
        }
        return -1;
    }

    static int getKBOf(int K) {
        int KX = getKXOf(K);
        if (KX < 0)  return -1;
        K >>>= (KX * KX_LG2BASE);
        return K-1;
    }

    static int decodeK(int KX, int KB) {
        assert(KX_MIN <= KX && KX <= KX_MAX);
        assert(KB_MIN <= KB && KB <= KB_MAX);
        return (KB+KB_OFFSET) << (KX * KX_LG2BASE);
    }

    static int getNextK(int K) {
        if (K <= 0)  return 1;  // 1st K value
        int KX = getKXOf(K);
        if (KX < 0)  return Integer.MAX_VALUE;
        // This is the increment we expect to apply:
        int unit = 1      << (KX * KX_LG2BASE);
        int mask = KB_MAX << (KX * KX_LG2BASE);
        int K1 = K + unit;
        K1 &= ~(unit-1);  // cut off stray low-order bits
        if (((K1 - unit) & ~mask) == 0) {
            assert(getKXOf(K1) == KX);
            return K1;
        }
        if (KX == KX_MAX)  return Integer.MAX_VALUE;
        KX += 1;
        int mask2 = KB_MAX << (KX * KX_LG2BASE);
        K1 |= (mask & ~mask2);
        K1 += unit;
        assert(getKXOf(K1) == KX);
        return K1;
    }

    // Is K of the form ((KB:[0..255])+1) * 16^(KX:{0..3])?
    public static boolean isCodableLength(int K) {
        int KX = getKXOf(K);
        if (KX < 0)  return false;
        int unit = 1      << (KX * KX_LG2BASE);
        int mask = KB_MAX << (KX * KX_LG2BASE);
        return ((K - unit) & ~mask) == 0;
    }

    public byte[] getMetaCoding(Coding dflt) {
        //assert(!isTrivial()); // can happen
        // See the isCodableLength restriction in CodingChooser.
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(10);
        try {
            makeMetaCoding(this, dflt, bytes);
        } catch (IOException ee) {
            throw new RuntimeException(ee);
        }
        return bytes.toByteArray();
    }
    private static void makeMetaCoding(AdaptiveCoding run, Coding dflt,
                                       ByteArrayOutputStream bytes)
                                      throws IOException {
        for (;;) {
            CodingMethod headCoding = run.headCoding;
            int          headLength = run.headLength;
            CodingMethod tailCoding = run.tailCoding;
            int K = headLength;
            assert(isCodableLength(K));
            int ADef   = (headCoding == dflt)?1:0;
            int BDef   = (tailCoding == dflt)?1:0;
            if (ADef+BDef > 1)  BDef = 0;  // arbitrary choice
            int ABDef  = 1*ADef + 2*BDef;
            assert(ABDef < 3);
            int KX     = getKXOf(K);
            int KB     = getKBOf(K);
            assert(decodeK(KX, KB) == K);
            int KBFlag = (KB != KB_DEFAULT)?1:0;
            bytes.write(_meta_run + KX + 4*KBFlag + 8*ABDef);
            if (KBFlag != 0)    bytes.write(KB);
            if (ADef == 0)  bytes.write(headCoding.getMetaCoding(dflt));
            if (tailCoding instanceof AdaptiveCoding) {
                run = (AdaptiveCoding) tailCoding;
                continue; // tail call, to avoid deep stack recursion
            }
            if (BDef == 0)  bytes.write(tailCoding.getMetaCoding(dflt));
            break;
        }
    }
    public static int parseMetaCoding(byte[] bytes, int pos, Coding dflt, CodingMethod res[]) {
        int op = bytes[pos++] & 0xFF;
        if (op < _meta_run || op >= _meta_pop)  return pos-1; // backup
        AdaptiveCoding prevc = null;
        for (boolean keepGoing = true; keepGoing; ) {
            keepGoing = false;
            assert(op >= _meta_run);
            op -= _meta_run;
            int KX = op % 4;
            int KBFlag = (op / 4) % 2;
            int ABDef = (op / 8);
            assert(ABDef < 3);
            int ADef = (ABDef & 1);
            int BDef = (ABDef & 2);
            CodingMethod[] ACode = {dflt}, BCode = {dflt};
            int KB = KB_DEFAULT;
            if (KBFlag != 0)
                KB = bytes[pos++] & 0xFF;
            if (ADef == 0) {
                pos = BandStructure.parseMetaCoding(bytes, pos, dflt, ACode);
            }
            if (BDef == 0 &&
                ((op = bytes[pos] & 0xFF) >= _meta_run) && op < _meta_pop) {
                pos++;
                keepGoing = true;
            } else if (BDef == 0) {
                pos = BandStructure.parseMetaCoding(bytes, pos, dflt, BCode);
            }
            AdaptiveCoding newc = new AdaptiveCoding(decodeK(KX, KB),
                                                     ACode[0], BCode[0]);
            if (prevc == null) {
                res[0] = newc;
            } else {
                prevc.tailCoding = newc;
            }
            prevc = newc;
        }
        return pos;
    }

    private String keyString(CodingMethod m) {
        if (m instanceof Coding)
            return ((Coding)m).keyString();
        return m.toString();
    }
    public String toString() {
        StringBuilder res = new StringBuilder(20);
        AdaptiveCoding run = this;
        res.append("run(");
        for (;;) {
            res.append(run.headLength).append("*");
            res.append(keyString(run.headCoding));
            if (run.tailCoding instanceof AdaptiveCoding) {
                run = (AdaptiveCoding) run.tailCoding;
                res.append(" ");
                continue;
            }
            break;
        }
        res.append(" **").append(keyString(run.tailCoding));
        res.append(")");
        return res.toString();
    }

/*
    public static void main(String av[]) {
        int[][] samples = {
            {1,2,3,4,5},
            {254,255,256,256+1*16,256+2*16},
            {0xfd,0xfe,0xff,0x100,0x110,0x120,0x130},
            {0xfd0,0xfe0,0xff0,0x1000,0x1100,0x1200,0x1300},
            {0xfd00,0xfe00,0xff00,0x10000,0x11000,0x12000,0x13000},
            {0xfd000,0xfe000,0xff000,0x100000}
        };
        for (int i = 0; i < samples.length; i++) {
            for (int j = 0; j < samples[i].length; j++) {
                int K = samples[i][j];
                int KX = getKXOf(K);
                int KB = getKBOf(K);
                System.out.println("K="+Integer.toHexString(K)+
                                   " KX="+KX+" KB="+KB);
                assert(isCodableLength(K));
                assert(K == decodeK(KX, KB));
                if (j == 0)  continue;
                int K1 = samples[i][j-1];
                assert(K == getNextK(K1));
            }
        }
    }
//*/

}
