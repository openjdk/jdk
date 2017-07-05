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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import static com.sun.java.util.jar.pack.Constants.*;

/**
 * Population-based coding.
 * See the section "Encodings of Uncorrelated Values" in the Pack200 spec.
 * @author John Rose
 */
// This tactic alone reduces the final zipped rt.jar by about a percent.
class PopulationCoding implements CodingMethod {
    Histogram vHist;   // histogram of all values
    int[]     fValues; // list of favored values
    int       fVlen;   // inclusive max index
    long[]    symtab;  // int map of favored value -> token [1..#fValues]

    CodingMethod favoredCoding;
    CodingMethod tokenCoding;
    CodingMethod unfavoredCoding;

    int L = -1;  //preferred L value for tokenCoding

    public void setFavoredValues(int[] fValues, int fVlen) {
        // Note:  {f} is allFavoredValues[1..fvlen], not [0..fvlen-1].
        // This is because zero is an exceptional favored value index.
        assert(fValues[0] == 0);  // must be empty
        assert(this.fValues == null);  // do not do this twice
        this.fValues = fValues;
        this.fVlen   = fVlen;
        if (L >= 0) {
            setL(L);  // reassert
        }
    }
    public void setFavoredValues(int[] fValues) {
        int lfVlen = fValues.length-1;
        setFavoredValues(fValues, lfVlen);
    }
    public void setHistogram(Histogram vHist) {
        this.vHist = vHist;
    }
    public void setL(int L) {
        this.L = L;
        if (L >= 0 && fValues != null && tokenCoding == null) {
            tokenCoding = fitTokenCoding(fVlen, L);
            assert(tokenCoding != null);
        }
    }

    public static Coding fitTokenCoding(int fVlen, int L) {
        // Find the smallest B s.t. (B,H,0) covers fVlen.
        if (fVlen < 256)
            // H/L do not matter when B==1
            return BandStructure.BYTE1;
        Coding longest = BandStructure.UNSIGNED5.setL(L);
        if (!longest.canRepresentUnsigned(fVlen))
            return null;  // failure; L is too sharp and fVlen too large
        Coding tc = longest;
        for (Coding shorter = longest; ; ) {
            shorter = shorter.setB(shorter.B()-1);
            if (shorter.umax() < fVlen)
                break;
            tc = shorter;  // shorten it by reducing B
        }
        return tc;
    }

    public void setFavoredCoding(CodingMethod favoredCoding) {
        this.favoredCoding = favoredCoding;
    }
    public void setTokenCoding(CodingMethod tokenCoding) {
        this.tokenCoding = tokenCoding;
        this.L = -1;
        if (tokenCoding instanceof Coding && fValues != null) {
            Coding tc = (Coding) tokenCoding;
            if (tc == fitTokenCoding(fVlen, tc.L()))
                this.L = tc.L();
            // Otherwise, it's a non-default coding.
        }
    }
    public void setUnfavoredCoding(CodingMethod unfavoredCoding) {
        this.unfavoredCoding = unfavoredCoding;
    }

    public int favoredValueMaxLength() {
        if (L == 0)
            return Integer.MAX_VALUE;
        else
            return BandStructure.UNSIGNED5.setL(L).umax();
    }

    public void resortFavoredValues() {
        Coding tc = (Coding) tokenCoding;
        // Make a local copy before reordering.
        fValues = BandStructure.realloc(fValues, 1+fVlen);
        // Resort favoredValues within each byte-size cadre.
        int fillp = 1;  // skip initial zero
        for (int n = 1; n <= tc.B(); n++) {
            int nmax = tc.byteMax(n);
            if (nmax > fVlen)
                nmax = fVlen;
            if (nmax < tc.byteMin(n))
                break;
            int low = fillp;
            int high = nmax+1;
            if (high == low)  continue;
            assert(high > low)
                : high+"!>"+low;
            assert(tc.getLength(low) == n)
                : n+" != len("+(low)+") == "+
                  tc.getLength(low);
            assert(tc.getLength(high-1) == n)
                : n+" != len("+(high-1)+") == "+
                  tc.getLength(high-1);
            int midTarget = low + (high-low)/2;
            int mid = low;
            // Divide the values into cadres, and sort within each.
            int prevCount = -1;
            int prevLimit = low;
            for (int i = low; i < high; i++) {
                int val = fValues[i];
                int count = vHist.getFrequency(val);
                if (prevCount != count) {
                    if (n == 1) {
                        // For the single-byte encoding, keep strict order
                        // among frequency groups.
                        Arrays.sort(fValues, prevLimit, i);
                    } else if (Math.abs(mid - midTarget) >
                               Math.abs(i   - midTarget)) {
                        // Find a single inflection point
                        // close to the middle of the byte-size cadre.
                        mid = i;
                    }
                    prevCount = count;
                    prevLimit = i;
                }
            }
            if (n == 1) {
                Arrays.sort(fValues, prevLimit, high);
            } else {
                // Sort up to the midpoint, if any.
                Arrays.sort(fValues, low, mid);
                Arrays.sort(fValues, mid, high);
            }
            assert(tc.getLength(low) == tc.getLength(mid));
            assert(tc.getLength(low) == tc.getLength(high-1));
            fillp = nmax+1;
        }
        assert(fillp == fValues.length);

        // Reset symtab.
        symtab = null;
    }

    public int getToken(int value) {
        if (symtab == null)
            symtab = makeSymtab();
        int pos = Arrays.binarySearch(symtab, (long)value << 32);
        if (pos < 0)  pos = -pos-1;
        if (pos < symtab.length && value == (int)(symtab[pos] >>> 32))
            return (int)symtab[pos];
        else
            return 0;
    }

    public int[][] encodeValues(int[] values, int start, int end) {
        // Compute token sequence.
        int[] tokens = new int[end-start];
        int nuv = 0;
        for (int i = 0; i < tokens.length; i++) {
            int val = values[start+i];
            int tok = getToken(val);
            if (tok != 0)
                tokens[i] = tok;
            else
                nuv += 1;
        }
        // Compute unfavored value sequence.
        int[] unfavoredValues = new int[nuv];
        nuv = 0;  // reset
        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i] != 0)  continue;  // already covered
            int val = values[start+i];
            unfavoredValues[nuv++] = val;
        }
        assert(nuv == unfavoredValues.length);
        return new int[][]{ tokens, unfavoredValues };
    }

    private long[] makeSymtab() {
        long[] lsymtab = new long[fVlen];
        for (int token = 1; token <= fVlen; token++) {
            lsymtab[token-1] = ((long)fValues[token] << 32) | token;
        }
        // Index by value:
        Arrays.sort(lsymtab);
        return lsymtab;
    }

    private Coding getTailCoding(CodingMethod c) {
        while (c instanceof AdaptiveCoding)
            c = ((AdaptiveCoding)c).tailCoding;
        return (Coding) c;
    }

    // CodingMethod methods.
    public void writeArrayTo(OutputStream out, int[] a, int start, int end) throws IOException {
        int[][] vals = encodeValues(a, start, end);
        writeSequencesTo(out, vals[0], vals[1]);
    }
    void writeSequencesTo(OutputStream out, int[] tokens, int[] uValues) throws IOException {
        favoredCoding.writeArrayTo(out, fValues, 1, 1+fVlen);
        getTailCoding(favoredCoding).writeTo(out, computeSentinelValue());
        tokenCoding.writeArrayTo(out, tokens, 0, tokens.length);
        if (uValues.length > 0)
            unfavoredCoding.writeArrayTo(out, uValues, 0, uValues.length);
    }

   int computeSentinelValue() {
        Coding fc = getTailCoding(favoredCoding);
        if (fc.isDelta()) {
            // repeat the last favored value, using delta=0
            return 0;
        } else {
            // else repeat the shorter of the min or last value
            int min = fValues[1];
            int last = min;
            // (remember that fVlen is an inclusive limit in fValues)
            for (int i = 2; i <= fVlen; i++) {
                last = fValues[i];
                min = moreCentral(min, last);
            }
            int endVal;
            if (fc.getLength(min) <= fc.getLength(last))
                return min;
            else
                return last;
        }
   }

    public void readArrayFrom(InputStream in, int[] a, int start, int end) throws IOException {
        // Parameters are fCode, L, uCode.
        setFavoredValues(readFavoredValuesFrom(in, end-start));
        // Read the tokens.  Read them into the final array, for the moment.
        tokenCoding.readArrayFrom(in, a, start, end);
        // Decode the favored tokens.
        int headp = 0, tailp = -1;
        int uVlen = 0;
        for (int i = start; i < end; i++) {
            int tok = a[i];
            if (tok == 0) {
                // Make a linked list, and decode in a second pass.
                if (tailp < 0) {
                    headp = i;
                } else {
                    a[tailp] = i;
                }
                tailp = i;
                uVlen += 1;
            } else {
                a[i] = fValues[tok];
            }
        }
        // Walk the linked list of "zero" locations, decoding unfavored vals.
        int[] uValues = new int[uVlen];
        if (uVlen > 0)
            unfavoredCoding.readArrayFrom(in, uValues, 0, uVlen);
        for (int i = 0; i < uVlen; i++) {
            int nextp = a[headp];
            a[headp] = uValues[i];
            headp = nextp;
        }
    }

    int[] readFavoredValuesFrom(InputStream in, int maxForDebug) throws IOException {
        int[] lfValues = new int[1000];  // realloc as needed
        // The set uniqueValuesForDebug records all favored values.
        // As each new value is added, we assert that the value
        // was not already in the set.
        Set<Integer> uniqueValuesForDebug = null;
        assert((uniqueValuesForDebug = new HashSet<>()) != null);
        int fillp = 1;
        maxForDebug += fillp;
        int min = Integer.MIN_VALUE;  // farthest from the center
        //int min2 = Integer.MIN_VALUE;  // emulate buggy 150.7 spec.
        int last = 0;
        CodingMethod fcm = favoredCoding;
        while (fcm instanceof AdaptiveCoding) {
            AdaptiveCoding ac = (AdaptiveCoding) fcm;
            int len = ac.headLength;
            while (fillp + len > lfValues.length) {
                lfValues = BandStructure.realloc(lfValues);
            }
            int newFillp = fillp + len;
            ac.headCoding.readArrayFrom(in, lfValues, fillp, newFillp);
            while (fillp < newFillp) {
                int val = lfValues[fillp++];
                assert(uniqueValuesForDebug.add(val));
                assert(fillp <= maxForDebug);
                last = val;
                min = moreCentral(min, val);
                //min2 = moreCentral2(min2, val, min);
            }
            fcm = ac.tailCoding;
        }
        Coding fc = (Coding) fcm;
        if (fc.isDelta()) {
            for (long state = 0;;) {
                // Read a new value:
                state += fc.readFrom(in);
                int val;
                if (fc.isSubrange())
                    val = fc.reduceToUnsignedRange(state);
                else
                    val = (int)state;
                state = val;
                if (fillp > 1 && (val == last || val == min)) //|| val == min2
                    break;
                if (fillp == lfValues.length)
                    lfValues = BandStructure.realloc(lfValues);
                lfValues[fillp++] = val;
                assert(uniqueValuesForDebug.add(val));
                assert(fillp <= maxForDebug);
                last = val;
                min = moreCentral(min, val);
                //min2 = moreCentral(min2, val);
            }
        } else {
            for (;;) {
                int val = fc.readFrom(in);
                if (fillp > 1 && (val == last || val == min)) //|| val == min2
                    break;
                if (fillp == lfValues.length)
                    lfValues = BandStructure.realloc(lfValues);
                lfValues[fillp++] = val;
                assert(uniqueValuesForDebug.add(val));
                assert(fillp <= maxForDebug);
                last = val;
                min = moreCentral(min, val);
                //min2 = moreCentral2(min2, val, min);
            }
        }
        return BandStructure.realloc(lfValues, fillp);
    }

    private static int moreCentral(int x, int y) {
        int kx = (x >> 31) ^ (x << 1);
        int ky = (y >> 31) ^ (y << 1);
        // bias kx/ky to get an unsigned comparison:
        kx -= Integer.MIN_VALUE;
        ky -= Integer.MIN_VALUE;
        int xy = (kx < ky? x: y);
        // assert that this ALU-ish version is the same:
        assert(xy == moreCentralSlow(x, y));
        return xy;
    }
//  private static int moreCentral2(int x, int y, int min) {
//      // Strict implementation of buggy 150.7 specification.
//      // The bug is that the spec. says absolute-value ties are broken
//      // in favor of positive numbers, but the suggested implementation
//      // (also mentioned in the spec.) breaks ties in favor of negatives.
//      if (x + y == 0)  return (x > y? x : y);
//      return min;
//  }
    private static int moreCentralSlow(int x, int y) {
        int ax = x;
        if (ax < 0)  ax = -ax;
        if (ax < 0)  return y;  //x is MIN_VALUE
        int ay = y;
        if (ay < 0)  ay = -ay;
        if (ay < 0)  return x;  //y is MIN_VALUE
        if (ax < ay)  return x;
        if (ax > ay)  return y;
        // At this point the absolute values agree, and the negative wins.
        return x < y ? x : y;
    }

    static final int[] LValuesCoded
        = { -1, 4, 8, 16, 32, 64, 128, 192, 224, 240, 248, 252 };

    public byte[] getMetaCoding(Coding dflt) {
        int K = fVlen;
        int LCoded = 0;
        if (tokenCoding instanceof Coding) {
            Coding tc = (Coding) tokenCoding;
            if (tc.B() == 1) {
                LCoded = 1;
            } else if (L >= 0) {
                assert(L == tc.L());
                for (int i = 1; i < LValuesCoded.length; i++) {
                    if (LValuesCoded[i] == L) { LCoded = i; break; }
                }
            }
        }
        CodingMethod tokenDflt = null;
        if (LCoded != 0 && tokenCoding == fitTokenCoding(fVlen, L)) {
            // A simple L value is enough to recover the tokenCoding.
            tokenDflt = tokenCoding;
        }
        int FDef = (favoredCoding == dflt)?1:0;
        int UDef = (unfavoredCoding == dflt || unfavoredCoding == null)?1:0;
        int TDef = (tokenCoding == tokenDflt)?1:0;
        int TDefL = (TDef == 1) ? LCoded : 0;
        assert(TDef == ((TDefL>0)?1:0));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(10);
        bytes.write(_meta_pop + FDef + 2*UDef + 4*TDefL);
        try {
            if (FDef == 0)  bytes.write(favoredCoding.getMetaCoding(dflt));
            if (TDef == 0)  bytes.write(tokenCoding.getMetaCoding(dflt));
            if (UDef == 0)  bytes.write(unfavoredCoding.getMetaCoding(dflt));
        } catch (IOException ee) {
            throw new RuntimeException(ee);
        }
        return bytes.toByteArray();
    }
    public static int parseMetaCoding(byte[] bytes, int pos, Coding dflt, CodingMethod res[]) {
        int op = bytes[pos++] & 0xFF;
        if (op < _meta_pop || op >= _meta_limit)  return pos-1; // backup
        op -= _meta_pop;
        int FDef = op % 2;
        int UDef = (op / 2) % 2;
        int TDefL = (op / 4);
        int TDef = (TDefL > 0)?1:0;
        int L = LValuesCoded[TDefL];
        CodingMethod[] FCode = {dflt}, TCode = {null}, UCode = {dflt};
        if (FDef == 0)
            pos = BandStructure.parseMetaCoding(bytes, pos, dflt, FCode);
        if (TDef == 0)
            pos = BandStructure.parseMetaCoding(bytes, pos, dflt, TCode);
        if (UDef == 0)
            pos = BandStructure.parseMetaCoding(bytes, pos, dflt, UCode);
        PopulationCoding pop = new PopulationCoding();
        pop.L = L;  // might be -1
        pop.favoredCoding   = FCode[0];
        pop.tokenCoding     = TCode[0];  // might be null!
        pop.unfavoredCoding = UCode[0];
        res[0] = pop;
        return pos;
    }

    private String keyString(CodingMethod m) {
        if (m instanceof Coding)
            return ((Coding)m).keyString();
        if (m == null)
            return "none";
        return m.toString();
    }
    public String toString() {
        PropMap p200 = Utils.currentPropMap();
        boolean verbose
            = (p200 != null &&
               p200.getBoolean(Utils.COM_PREFIX+"verbose.pop"));
        StringBuilder res = new StringBuilder(100);
        res.append("pop(").append("fVlen=").append(fVlen);
        if (verbose && fValues != null) {
            res.append(" fV=[");
            for (int i = 1; i <= fVlen; i++) {
                res.append(i==1?"":",").append(fValues[i]);
            }
            res.append(";").append(computeSentinelValue());
            res.append("]");
        }
        res.append(" fc=").append(keyString(favoredCoding));
        res.append(" tc=").append(keyString(tokenCoding));
        res.append(" uc=").append(keyString(unfavoredCoding));
        res.append(")");
        return res.toString();
    }
}
