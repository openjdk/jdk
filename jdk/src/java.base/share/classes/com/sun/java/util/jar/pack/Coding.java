/*
 * Copyright (c) 2001, 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import static com.sun.java.util.jar.pack.Constants.*;
/**
 * Define the conversions between sequences of small integers and raw bytes.
 * This is a schema of encodings which incorporates varying lengths,
 * varying degrees of length variability, and varying amounts of signed-ness.
 * @author John Rose
 */
class Coding implements Comparable<Coding>, CodingMethod, Histogram.BitMetric {
    /*
      Coding schema for single integers, parameterized by (B,H,S):

      Let B in [1,5], H in [1,256], S in [0,3].
      (S limit is arbitrary.  B follows the 32-bit limit.  H is byte size.)

      A given (B,H,S) code varies in length from 1 to B bytes.

      The 256 values a byte may take on are divided into L=(256-H) and H
      values, with all the H values larger than the L values.
      (That is, the L values are [0,L) and the H are [L,256).)

      The last byte is always either the B-th byte, a byte with "L value"
      (<L), or both.  There is no other byte that satisfies these conditions.
      All bytes before the last always have "H values" (>=L).

      Therefore, if L==0, the code always has the full length of B bytes.
      The coding then becomes a classic B-byte little-endian unsigned integer.
      (Also, if L==128, the high bit of each byte acts signals the presence
      of a following byte, up to the maximum length.)

      In the unsigned case (S==0), the coding is compact and monotonic
      in the ordering of byte sequences defined by appending zero bytes
      to pad them to a common length B, reversing them, and ordering them
      lexicographically.  (This agrees with "little-endian" byte order.)

      Therefore, the unsigned value of a byte sequence may be defined as:
      <pre>
        U(b0)           == b0
                           in [0..L)
                           or [0..256) if B==1 (**)

        U(b0,b1)        == b0 + b1*H
                           in [L..L*(1+H))
                           or [L..L*(1+H) + H^2) if B==2

        U(b0,b1,b2)     == b0 + b1*H + b2*H^2
                           in [L*(1+H)..L*(1+H+H^2))
                           or [L*(1+H)..L*(1+H+H^2) + H^3) if B==3

        U(b[i]: i<n)    == Sum[i<n]( b[i] * H^i )
                           up to  L*Sum[i<n]( H^i )
                           or to  L*Sum[i<n]( H^i ) + H^n if n==B
      </pre>

      (**) If B==1, the values H,L play no role in the coding.
      As a convention, we require that any (1,H,S) code must always
      encode values less than H.  Thus, a simple unsigned byte is coded
      specifically by the code (1,256,0).

      (Properly speaking, the unsigned case should be parameterized as
      S==Infinity.  If the schema were regular, the case S==0 would really
      denote a numbering in which all coded values are negative.)

      If S>0, the unsigned value of a byte sequence is regarded as a binary
      integer.  If any of the S low-order bits are zero, the corresponding
      signed value will be non-negative.  If all of the S low-order bits
      (S>0) are one, the the corresponding signed value will be negative.

      The non-negative signed values are compact and monotonically increasing
      (from 0) in the ordering of the corresponding unsigned values.

      The negative signed values are compact and monotonically decreasing
      (from -1) in the ordering of the corresponding unsigned values.

      In essence, the low-order S bits function as a collective sign bit
      for negative signed numbers, and as a low-order base-(2^S-1) digit
      for non-negative signed numbers.

      Therefore, the signed value corresponding to an unsigned value is:
      <pre>
        Sgn(x)  == x                               if S==0
        Sgn(x)  == (x / 2^S)*(2^S-1) + (x % 2^S),  if S>0, (x % 2^S) < 2^S-1
        Sgn(x)  == -(x / 2^S)-1,                   if S>0, (x % 2^S) == 2^S-1
      </pre>

      Finally, the value of a byte sequence, given the coding parameters
      (B,H,S), is defined as:
      <pre>
        V(b[i]: i<n)  == Sgn(U(b[i]: i<n))
      </pre>

      The extremal positive and negative signed value for a given range
      of unsigned values may be found by sign-encoding the largest unsigned
      value which is not 2^S-1 mod 2^S, and that which is, respectively.

      Because B,H,S are variable, this is not a single coding but a schema
      of codings.  For optimal compression, it is necessary to adaptively
      select specific codings to the data being compressed.

      For example, if a sequence of values happens never to be negative,
      S==0 is the best choice.  If the values are equally balanced between
      negative and positive, S==1.  If negative values are rare, then S>1
      is more appropriate.

      A (B,H,S) encoding is called a "subrange" if it does not encode
      the largest 32-bit value, and if the number R of values it does
      encode can be expressed as a positive 32-bit value.  (Note that
      B=1 implies R<=256, B=2 implies R<=65536, etc.)

      A delta version of a given (B,H,S) coding encodes an array of integers
      by writing their successive differences in the (B,H,S) coding.
      The original integers themselves may be recovered by making a
      running accumulation of sum of the differences as they are read.

      As a special case, if a (B,H,S) encoding is a subrange, its delta
      version will only encode arrays of numbers in the coding's unsigned
      range, [0..R-1].  The coding of deltas is still in the normal signed
      range, if S!=0.  During delta encoding, all subtraction results are
      reduced to the signed range, by adding multiples of R.  Likewise,
.     during encoding, all addition results are reduced to the unsigned range.
      This special case for subranges allows the benefits of wraparound
      when encoding correlated sequences of very small positive numbers.
     */

    // Code-specific limits:
    private static int saturate32(long x) {
        if (x > Integer.MAX_VALUE)   return Integer.MAX_VALUE;
        if (x < Integer.MIN_VALUE)   return Integer.MIN_VALUE;
        return (int)x;
    }
    private static long codeRangeLong(int B, int H) {
        return codeRangeLong(B, H, B);
    }
    private static long codeRangeLong(int B, int H, int nMax) {
        // Code range for a all (B,H) codes of length <=nMax (<=B).
        // n < B:   L*Sum[i<n]( H^i )
        // n == B:  L*Sum[i<B]( H^i ) + H^B
        assert(nMax >= 0 && nMax <= B);
        assert(B >= 1 && B <= 5);
        assert(H >= 1 && H <= 256);
        if (nMax == 0)  return 0;  // no codes of zero length
        if (B == 1)     return H;  // special case; see (**) above
        int L = 256-H;
        long sum = 0;
        long H_i = 1;
        for (int n = 1; n <= nMax; n++) {
            sum += H_i;
            H_i *= H;
        }
        sum *= L;
        if (nMax == B)
            sum += H_i;
        return sum;
    }
    /** Largest int representable by (B,H,S) in up to nMax bytes. */
    public static int codeMax(int B, int H, int S, int nMax) {
        //assert(S >= 0 && S <= S_MAX);
        long range = codeRangeLong(B, H, nMax);
        if (range == 0)
            return -1;  // degenerate max value for empty set of codes
        if (S == 0 || range >= (long)1<<32)
            return saturate32(range-1);
        long maxPos = range-1;
        while (isNegativeCode(maxPos, S)) {
            --maxPos;
        }
        if (maxPos < 0)  return -1;  // No positive codings at all.
        int smax = decodeSign32(maxPos, S);
        // check for 32-bit wraparound:
        if (smax < 0)
            return Integer.MAX_VALUE;
        return smax;
    }
    /** Smallest int representable by (B,H,S) in up to nMax bytes.
        Returns Integer.MIN_VALUE if 32-bit wraparound covers
        the entire negative range.
     */
    public static int codeMin(int B, int H, int S, int nMax) {
        //assert(S >= 0 && S <= S_MAX);
        long range = codeRangeLong(B, H, nMax);
        if (range >= (long)1<<32 && nMax == B) {
            // Can code negative values via 32-bit wraparound.
            return Integer.MIN_VALUE;
        }
        if (S == 0) {
            return 0;
        }
        long maxNeg = range-1;
        while (!isNegativeCode(maxNeg, S))
            --maxNeg;

        if (maxNeg < 0)  return 0;  // No negative codings at all.
        return decodeSign32(maxNeg, S);
    }

    // Some of the arithmetic below is on unsigned 32-bit integers.
    // These must be represented in Java as longs in the range [0..2^32-1].
    // The conversion to a signed int is just the Java cast (int), but
    // the conversion to an unsigned int is the following little method:
    private static long toUnsigned32(int sx) {
        return ((long)sx << 32) >>> 32;
    }

    // Sign encoding:
    private static boolean isNegativeCode(long ux, int S) {
        assert(S > 0);
        assert(ux >= -1);  // can be out of 32-bit range; who cares
        int Smask = (1<<S)-1;
        return (((int)ux+1) & Smask) == 0;
    }
    private static boolean hasNegativeCode(int sx, int S) {
        assert(S > 0);
        // If S>=2 very low negatives are coded by 32-bit-wrapped positives.
        // The lowest negative representable by a negative coding is
        // ~(umax32 >> S), and the next lower number is coded by wrapping
        // the highest positive:
        //    CodePos(umax32-1)  ->  (umax32-1)-((umax32-1)>>S)
        // which simplifies to ~(umax32 >> S)-1.
        return (0 > sx) && (sx >= ~(-1>>>S));
    }
    private static int decodeSign32(long ux, int S) {
        assert(ux == toUnsigned32((int)ux))  // must be unsigned 32-bit number
            : (Long.toHexString(ux));
        if (S == 0) {
            return (int) ux;  // cast to signed int
        }
        int sx;
        if (isNegativeCode(ux, S)) {
            // Sgn(x)  == -(x / 2^S)-1
            sx = ~((int)ux >>> S);
        } else {
            // Sgn(x)  == (x / 2^S)*(2^S-1) + (x % 2^S)
            sx = (int)ux - ((int)ux >>> S);
        }
        // Assert special case of S==1:
        assert(!(S == 1) || sx == (((int)ux >>> 1) ^ -((int)ux & 1)));
        return sx;
    }
    private static long encodeSign32(int sx, int S) {
        if (S == 0) {
            return toUnsigned32(sx);  // unsigned 32-bit int
        }
        int Smask = (1<<S)-1;
        long ux;
        if (!hasNegativeCode(sx, S)) {
            // InvSgn(sx) = (sx / (2^S-1))*2^S + (sx % (2^S-1))
            ux = sx + (toUnsigned32(sx) / Smask);
        } else {
            // InvSgn(sx) = (-sx-1)*2^S + (2^S-1)
            ux = (-sx << S) - 1;
        }
        ux = toUnsigned32((int)ux);
        assert(sx == decodeSign32(ux, S))
            : (Long.toHexString(ux)+" -> "+
               Integer.toHexString(sx)+" != "+
               Integer.toHexString(decodeSign32(ux, S)));
        return ux;
    }

    // Top-level coding of single integers:
    public static void writeInt(byte[] out, int[] outpos, int sx, int B, int H, int S) {
        long ux = encodeSign32(sx, S);
        assert(ux == toUnsigned32((int)ux));
        assert(ux < codeRangeLong(B, H))
            : Long.toHexString(ux);
        int L = 256-H;
        long sum = ux;
        int pos = outpos[0];
        for (int i = 0; i < B-1; i++) {
            if (sum < L)
                break;
            sum -= L;
            int b_i = (int)( L + (sum % H) );
            sum /= H;
            out[pos++] = (byte)b_i;
        }
        out[pos++] = (byte)sum;
        // Report number of bytes written by updating outpos[0]:
        outpos[0] = pos;
        // Check right away for mis-coding.
        //assert(sx == readInt(out, new int[1], B, H, S));
    }
    public static int readInt(byte[] in, int[] inpos, int B, int H, int S) {
        // U(b[i]: i<n) == Sum[i<n]( b[i] * H^i )
        int L = 256-H;
        long sum = 0;
        long H_i = 1;
        int pos = inpos[0];
        for (int i = 0; i < B; i++) {
            int b_i = in[pos++] & 0xFF;
            sum += b_i*H_i;
            H_i *= H;
            if (b_i < L)  break;
        }
        //assert(sum >= 0 && sum < codeRangeLong(B, H));
        // Report number of bytes read by updating inpos[0]:
        inpos[0] = pos;
        return decodeSign32(sum, S);
    }
    // The Stream version doesn't fetch a byte unless it is needed for coding.
    public static int readIntFrom(InputStream in, int B, int H, int S) throws IOException {
        // U(b[i]: i<n) == Sum[i<n]( b[i] * H^i )
        int L = 256-H;
        long sum = 0;
        long H_i = 1;
        for (int i = 0; i < B; i++) {
            int b_i = in.read();
            if (b_i < 0)  throw new RuntimeException("unexpected EOF");
            sum += b_i*H_i;
            H_i *= H;
            if (b_i < L)  break;
        }
        assert(sum >= 0 && sum < codeRangeLong(B, H));
        return decodeSign32(sum, S);
    }

    public static final int B_MAX = 5;    /* B: [1,5] */
    public static final int H_MAX = 256;  /* H: [1,256] */
    public static final int S_MAX = 2;    /* S: [0,2] */

    // END OF STATICS.

    private final int B; /*1..5*/       // # bytes (1..5)
    private final int H; /*1..256*/     // # codes requiring a higher byte
    private final int L; /*0..255*/     // # codes requiring a higher byte
    private final int S; /*0..3*/       // # low-order bits representing sign
    private final int del; /*0..2*/     // type of delta encoding (0 == none)
    private final int min;              // smallest representable value
    private final int max;              // largest representable value
    private final int umin;             // smallest representable uns. value
    private final int umax;             // largest representable uns. value
    private final int[] byteMin;        // smallest repr. value, given # bytes
    private final int[] byteMax;        // largest repr. value, given # bytes

    private Coding(int B, int H, int S) {
        this(B, H, S, 0);
    }
    private Coding(int B, int H, int S, int del) {
        this.B = B;
        this.H = H;
        this.L = 256-H;
        this.S = S;
        this.del = del;
        this.min = codeMin(B, H, S, B);
        this.max = codeMax(B, H, S, B);
        this.umin = codeMin(B, H, 0, B);
        this.umax = codeMax(B, H, 0, B);
        this.byteMin = new int[B];
        this.byteMax = new int[B];

        for (int nMax = 1; nMax <= B; nMax++) {
            byteMin[nMax-1] = codeMin(B, H, S, nMax);
            byteMax[nMax-1] = codeMax(B, H, S, nMax);
        }
    }

    public boolean equals(Object x) {
        if (!(x instanceof Coding))  return false;
        Coding that = (Coding) x;
        if (this.B != that.B)  return false;
        if (this.H != that.H)  return false;
        if (this.S != that.S)  return false;
        if (this.del != that.del)  return false;
        return true;
    }

    public int hashCode() {
        return (del<<14)+(S<<11)+(B<<8)+(H<<0);
    }

    private static Map<Coding, Coding> codeMap;

    private static synchronized Coding of(int B, int H, int S, int del) {
        if (codeMap == null)  codeMap = new HashMap<>();
        Coding x0 = new Coding(B, H, S, del);
        Coding x1 = codeMap.get(x0);
        if (x1 == null)  codeMap.put(x0, x1 = x0);
        return x1;
    }

    public static Coding of(int B, int H) {
        return of(B, H, 0, 0);
    }

    public static Coding of(int B, int H, int S) {
        return of(B, H, S, 0);
    }

    public boolean canRepresentValue(int x) {
        if (isSubrange())
            return canRepresentUnsigned(x);
        else
            return canRepresentSigned(x);
    }
    /** Can this coding represent a single value, possibly a delta?
     *  This ignores the D property.  That is, for delta codings,
     *  this tests whether a delta value of 'x' can be coded.
     *  For signed delta codings which produce unsigned end values,
     *  use canRepresentUnsigned.
     */
    public boolean canRepresentSigned(int x) {
        return (x >= min && x <= max);
    }
    /** Can this coding, apart from its S property,
     *  represent a single value?  (Negative values
     *  can only be represented via 32-bit overflow,
     *  so this returns true for negative values
     *  if isFullRange is true.)
     */
    public boolean canRepresentUnsigned(int x) {
        return (x >= umin && x <= umax);
    }

    // object-oriented code/decode
    public int readFrom(byte[] in, int[] inpos) {
        return readInt(in, inpos, B, H, S);
    }
    public void writeTo(byte[] out, int[] outpos, int x) {
        writeInt(out, outpos, x, B, H, S);
    }

    // Stream versions
    public int readFrom(InputStream in) throws IOException {
        return readIntFrom(in, B, H, S);
    }
    public void writeTo(OutputStream out, int x) throws IOException {
        byte[] buf = new byte[B];
        int[] pos = new int[1];
        writeInt(buf, pos, x, B, H, S);
        out.write(buf, 0, pos[0]);
    }

    // Stream/array versions
    public void readArrayFrom(InputStream in, int[] a, int start, int end) throws IOException {
        // %%% use byte[] buffer
        for (int i = start; i < end; i++)
            a[i] = readFrom(in);

        for (int dstep = 0; dstep < del; dstep++) {
            long state = 0;
            for (int i = start; i < end; i++) {
                state += a[i];
                // Reduce array values to the required range.
                if (isSubrange()) {
                    state = reduceToUnsignedRange(state);
                }
                a[i] = (int) state;
            }
        }
    }
    public void writeArrayTo(OutputStream out, int[] a, int start, int end) throws IOException {
        if (end <= start)  return;
        for (int dstep = 0; dstep < del; dstep++) {
            int[] deltas;
            if (!isSubrange())
                deltas = makeDeltas(a, start, end, 0, 0);
            else
                deltas = makeDeltas(a, start, end, min, max);
            a = deltas;
            start = 0;
            end = deltas.length;
        }
        // The following code is a buffered version of this loop:
        //    for (int i = start; i < end; i++)
        //        writeTo(out, a[i]);
        byte[] buf = new byte[1<<8];
        final int bufmax = buf.length-B;
        int[] pos = { 0 };
        for (int i = start; i < end; ) {
            while (pos[0] <= bufmax) {
                writeTo(buf, pos, a[i++]);
                if (i >= end)  break;
            }
            out.write(buf, 0, pos[0]);
            pos[0] = 0;
        }
    }

    /** Tell if the range of this coding (number of distinct
     *  representable values) can be expressed in 32 bits.
     */
    boolean isSubrange() {
        return max < Integer.MAX_VALUE
            && ((long)max - (long)min + 1) <= Integer.MAX_VALUE;
    }

    /** Tell if this coding can represent all 32-bit values.
     *  Note:  Some codings, such as unsigned ones, can be neither
     *  subranges nor full-range codings.
     */
    boolean isFullRange() {
        return max == Integer.MAX_VALUE && min == Integer.MIN_VALUE;
    }

    /** Return the number of values this coding (a subrange) can represent. */
    int getRange() {
        assert(isSubrange());
        return (max - min) + 1;  // range includes both min & max
    }

    Coding setB(int B) { return Coding.of(B, H, S, del); }
    Coding setH(int H) { return Coding.of(B, H, S, del); }
    Coding setS(int S) { return Coding.of(B, H, S, del); }
    Coding setL(int L) { return setH(256-L); }
    Coding setD(int del) { return Coding.of(B, H, S, del); }
    Coding getDeltaCoding() { return setD(del+1); }

    /** Return a coding suitable for representing summed, modulo-reduced values. */
    Coding getValueCoding() {
        if (isDelta())
            return Coding.of(B, H, 0, del-1);
        else
            return this;
    }

    /** Reduce the given value to be within this coding's unsigned range,
     *  by adding or subtracting a multiple of (max-min+1).
     */
    int reduceToUnsignedRange(long value) {
        if (value == (int)value && canRepresentUnsigned((int)value))
            // already in unsigned range
            return (int)value;
        int range = getRange();
        assert(range > 0);
        value %= range;
        if (value < 0)  value += range;
        assert(canRepresentUnsigned((int)value));
        return (int)value;
    }

    int reduceToSignedRange(int value) {
        if (canRepresentSigned(value))
            // already in signed range
            return value;
        return reduceToSignedRange(value, min, max);
    }
    static int reduceToSignedRange(int value, int min, int max) {
        int range = (max-min+1);
        assert(range > 0);
        int value0 = value;
        value -= min;
        if (value < 0 && value0 >= 0) {
            // 32-bit overflow, but the next '%=' op needs to be unsigned
            value -= range;
            assert(value >= 0);
        }
        value %= range;
        if (value < 0)  value += range;
        value += min;
        assert(min <= value && value <= max);
        return value;
    }

    /** Does this coding support at least one negative value?
        Includes codings that can do so via 32-bit wraparound.
     */
    boolean isSigned() {
        return min < 0;
    }
    /** Does this coding code arrays by making successive differences? */
    boolean isDelta() {
        return del != 0;
    }

    public int B() { return B; }
    public int H() { return H; }
    public int L() { return L; }
    public int S() { return S; }
    public int del() { return del; }
    public int min() { return min; }
    public int max() { return max; }
    public int umin() { return umin; }
    public int umax() { return umax; }
    public int byteMin(int b) { return byteMin[b-1]; }
    public int byteMax(int b) { return byteMax[b-1]; }

    public int compareTo(Coding that) {
        int dkey = this.del - that.del;
        if (dkey == 0)
            dkey = this.B - that.B;
        if (dkey == 0)
            dkey = this.H - that.H;
        if (dkey == 0)
            dkey = this.S - that.S;
        return dkey;
    }

    /** Heuristic measure of the difference between two codings. */
    public int distanceFrom(Coding that) {
        int diffdel = this.del - that.del;
        if (diffdel < 0)  diffdel = -diffdel;
        int diffS = this.S - that.S;
        if (diffS < 0)  diffS = -diffS;
        int diffB = this.B - that.B;
        if (diffB < 0)  diffB = -diffB;
        int diffHL;
        if (this.H == that.H) {
            diffHL = 0;
        } else {
            // Distance in log space of H (<=128) and L (<128).
            int thisHL = this.getHL();
            int thatHL = that.getHL();
            // Double the accuracy of the log:
            thisHL *= thisHL;
            thatHL *= thatHL;
            if (thisHL > thatHL)
                diffHL = ceil_lg2(1+(thisHL-1)/thatHL);
            else
                diffHL = ceil_lg2(1+(thatHL-1)/thisHL);
        }
        int norm = 5*(diffdel + diffS + diffB) + diffHL;
        assert(norm != 0 || this.compareTo(that) == 0);
        return norm;
    }
    private int getHL() {
        // Follow H in log space by the multiplicative inverse of L.
        if (H <= 128)  return H;
        if (L >= 1)    return 128*128/L;
        return 128*256;
    }

    /** ceiling(log[2](x)): {1->0, 2->1, 3->2, 4->2, ...} */
    static int ceil_lg2(int x) {
        assert(x-1 >= 0);  // x in range (int.MIN_VALUE -> 32)
        x -= 1;
        int lg = 0;
        while (x != 0) {
            lg++;
            x >>= 1;
        }
        return lg;
    }

    static private final byte[] byteBitWidths = new byte[0x100];
    static {
        for (int b = 0; b < byteBitWidths.length; b++) {
            byteBitWidths[b] = (byte) ceil_lg2(b + 1);
        }
        for (int i = 10; i >= 0; i = (i << 1) - (i >> 3)) {
            assert(bitWidth(i) == ceil_lg2(i + 1));
        }
    }

    /** Number of significant bits in i, not counting sign bits.
     *  For positive i, it is ceil_lg2(i + 1).
     */
    static int bitWidth(int i) {
        if (i < 0)  i = ~i;  // change sign
        int w = 0;
        int lo = i;
        if (lo < byteBitWidths.length)
            return byteBitWidths[lo];
        int hi;
        hi = (lo >>> 16);
        if (hi != 0) {
            lo = hi;
            w += 16;
        }
        hi = (lo >>> 8);
        if (hi != 0) {
            lo = hi;
            w += 8;
        }
        w += byteBitWidths[lo];
        //assert(w == ceil_lg2(i + 1));
        return w;
    }

    /** Create an array of successive differences.
     *  If min==max, accept any and all 32-bit overflow.
     *  Otherwise, avoid 32-bit overflow, and reduce all differences
     *  to a value in the given range, by adding or subtracting
     *  multiples of the range cardinality (max-min+1).
     *  Also, the values are assumed to be in the range [0..(max-min)].
     */
    static int[] makeDeltas(int[] values, int start, int end,
                            int min, int max) {
        assert(max >= min);
        int count = end-start;
        int[] deltas = new int[count];
        int state = 0;
        if (min == max) {
            for (int i = 0; i < count; i++) {
                int value = values[start+i];
                deltas[i] = value - state;
                state = value;
            }
        } else {
            for (int i = 0; i < count; i++) {
                int value = values[start+i];
                assert(value >= 0 && value+min <= max);
                int delta = value - state;
                assert(delta == (long)value - (long)state); // no overflow
                state = value;
                // Reduce delta values to the required range.
                delta = reduceToSignedRange(delta, min, max);
                deltas[i] = delta;
            }
        }
        return deltas;
    }

    boolean canRepresent(int minValue, int maxValue) {
        assert(minValue <= maxValue);
        if (del > 0) {
            if (isSubrange()) {
                // We will force the values to reduce to the right subrange.
                return canRepresentUnsigned(maxValue)
                    && canRepresentUnsigned(minValue);
            } else {
                // Huge range; delta values must assume full 32-bit range.
                return isFullRange();
            }
        }
        else
            // final values must be representable
            return canRepresentSigned(maxValue)
                && canRepresentSigned(minValue);
    }

    boolean canRepresent(int[] values, int start, int end) {
        int len = end-start;
        if (len == 0)       return true;
        if (isFullRange())  return true;
        // Calculate max, min:
        int lmax = values[start];
        int lmin = lmax;
        for (int i = 1; i < len; i++) {
            int value = values[start+i];
            if (lmax < value)  lmax = value;
            if (lmin > value)  lmin = value;
        }
        return canRepresent(lmin, lmax);
    }

    public double getBitLength(int value) {  // implements BitMetric
        return (double) getLength(value) * 8;
    }

    /** How many bytes are in the coding of this value?
     *  Returns Integer.MAX_VALUE if the value has no coding.
     *  The coding must not be a delta coding, since there is no
     *  definite size for a single value apart from its context.
     */
    public int getLength(int value) {
        if (isDelta() && isSubrange()) {
            if (!canRepresentUnsigned(value))
                return Integer.MAX_VALUE;
            value = reduceToSignedRange(value);
        }
        if (value >= 0) {
            for (int n = 0; n < B; n++) {
                if (value <= byteMax[n])  return n+1;
            }
        } else {
            for (int n = 0; n < B; n++) {
                if (value >= byteMin[n])  return n+1;
            }
        }
        return Integer.MAX_VALUE;
    }

    public int getLength(int[] values, int start, int end) {
        int len = end-start;
        if (B == 1)  return len;
        if (L == 0)  return len * B;
        if (isDelta()) {
            int[] deltas;
            if (!isSubrange())
                deltas = makeDeltas(values, start, end, 0, 0);
            else
                deltas = makeDeltas(values, start, end, min, max);
            //return Coding.of(B, H, S).getLength(deltas, 0, len);
            values = deltas;
            start = 0;
        }
        int sum = len;  // at least 1 byte per
        // add extra bytes for extra-long values
        for (int n = 1; n <= B; n++) {
            // what is the coding interval [min..max] for n bytes?
            int lmax = byteMax[n-1];
            int lmin = byteMin[n-1];
            int longer = 0;  // count of guys longer than n bytes
            for (int i = 0; i < len; i++) {
                int value = values[start+i];
                if (value >= 0) {
                    if (value > lmax)  longer++;
                } else {
                    if (value < lmin)  longer++;
                }
            }
            if (longer == 0)  break;  // no more passes needed
            if (n == B)  return Integer.MAX_VALUE;  // cannot represent!
            sum += longer;
        }
        return sum;
    }

    public byte[] getMetaCoding(Coding dflt) {
        if (dflt == this)  return new byte[]{ (byte) _meta_default };
        int canonicalIndex = BandStructure.indexOf(this);
        if (canonicalIndex > 0)
            return new byte[]{ (byte) canonicalIndex };
        return new byte[]{
            (byte)_meta_arb,
            (byte)(del + 2*S + 8*(B-1)),
            (byte)(H-1)
        };
    }
    public static int parseMetaCoding(byte[] bytes, int pos, Coding dflt, CodingMethod res[]) {
        int op = bytes[pos++] & 0xFF;
        if (_meta_canon_min <= op && op <= _meta_canon_max) {
            Coding c = BandStructure.codingForIndex(op);
            assert(c != null);
            res[0] = c;
            return pos;
        }
        if (op == _meta_arb) {
            int dsb = bytes[pos++] & 0xFF;
            int H_1 = bytes[pos++] & 0xFF;
            int del = dsb % 2;
            int S = (dsb / 2) % 4;
            int B = (dsb / 8)+1;
            int H = H_1+1;
            if (!((1 <= B && B <= B_MAX) &&
                  (0 <= S && S <= S_MAX) &&
                  (1 <= H && H <= H_MAX) &&
                  (0 <= del && del <= 1))
                || (B == 1 && H != 256)
                || (B == 5 && H == 256)) {
                throw new RuntimeException("Bad arb. coding: ("+B+","+H+","+S+","+del);
            }
            res[0] = Coding.of(B, H, S, del);
            return pos;
        }
        return pos-1;  // backup
    }


    public String keyString() {
        return "("+B+","+H+","+S+","+del+")";
    }

    public String toString() {
        String str = "Coding"+keyString();
        // If -ea, print out more informative strings!
        //assert((str = stringForDebug()) != null);
        return str;
    }

    static boolean verboseStringForDebug = false;
    String stringForDebug() {
        String minS = (min == Integer.MIN_VALUE ? "min" : ""+min);
        String maxS = (max == Integer.MAX_VALUE ? "max" : ""+max);
        String str = keyString()+" L="+L+" r=["+minS+","+maxS+"]";
        if (isSubrange())
            str += " subrange";
        else if (!isFullRange())
            str += " MIDRANGE";
        if (verboseStringForDebug) {
            str += " {";
            int prev_range = 0;
            for (int n = 1; n <= B; n++) {
                int range_n = saturate32((long)byteMax[n-1] - byteMin[n-1] + 1);
                assert(range_n == saturate32(codeRangeLong(B, H, n)));
                range_n -= prev_range;
                prev_range = range_n;
                String rngS = (range_n == Integer.MAX_VALUE ? "max" : ""+range_n);
                str += " #"+n+"="+rngS;
            }
            str += " }";
        }
        return str;
    }
}
