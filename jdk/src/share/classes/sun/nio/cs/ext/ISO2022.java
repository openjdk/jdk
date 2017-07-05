/*
 * Copyright 2002-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 */

package sun.nio.cs.ext;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import sun.nio.cs.Surrogate;

abstract class ISO2022
    extends Charset
{

    private static final byte ISO_ESC = 0x1b;
    private static final byte ISO_SI = 0x0f;
    private static final byte ISO_SO = 0x0e;
    private static final byte ISO_SS2_7 = 0x4e;
    private static final byte ISO_SS3_7 = 0x4f;
    private static final byte MSB = (byte)0x80;
    private static final char REPLACE_CHAR = '\uFFFD';
    private static final byte minDesignatorLength = 3;

    public ISO2022(String csname, String[] aliases) {
        super(csname, aliases);
    }

    public CharsetDecoder newDecoder() {
        return new Decoder(this);
    }

    public CharsetEncoder newEncoder() {
        return new Encoder(this);
    }

    protected static class Decoder extends CharsetDecoder {

        // Value to be filled by subclass
        protected byte SODesig[][];
        protected byte SS2Desig[][] = null;
        protected byte SS3Desig[][] = null;

        protected CharsetDecoder SODecoder[];
        protected CharsetDecoder SS2Decoder[] = null;
        protected CharsetDecoder SS3Decoder[] = null;

        private static final byte SOFlag = 0;
        private static final byte SS2Flag = 1;
        private static final byte SS3Flag = 2;

        private int curSODes, curSS2Des, curSS3Des;
        private boolean shiftout;
        private CharsetDecoder tmpDecoder[];

        protected Decoder(Charset cs) {
            super(cs, 1.0f, 1.0f);
        }

        protected void implReset() {
            curSODes = 0;
            curSS2Des = 0;
            curSS3Des = 0;
            shiftout = false;
        }

        private char decode(byte byte1, byte byte2, byte shiftFlag)
        {
            byte1 |= MSB;
            byte2 |= MSB;

            byte[] tmpByte = { byte1,byte2 };
            char[] tmpChar = new char[1];
            int     i = 0,
                    tmpIndex = 0;

            switch(shiftFlag) {
            case SOFlag:
                tmpIndex = curSODes;
                tmpDecoder = (CharsetDecoder [])SODecoder;
                break;
            case SS2Flag:
                tmpIndex = curSS2Des;
                tmpDecoder = (CharsetDecoder [])SS2Decoder;
                break;
            case SS3Flag:
                tmpIndex = curSS3Des;
                tmpDecoder = (CharsetDecoder [])SS3Decoder;
                break;
            }

            if (tmpDecoder != null) {
                for(i = 0; i < tmpDecoder.length; i++) {
                    if(tmpIndex == i) {
                        try {
                            ByteBuffer bb = ByteBuffer.wrap(tmpByte,0,2);
                            CharBuffer cc = CharBuffer.wrap(tmpChar,0,1);
                            tmpDecoder[i].decode(bb, cc, true);
                            cc.flip();
                            return cc.get();
                        } catch (Exception e) {}
                    }
                }
            }
            return REPLACE_CHAR;
        }

        private int findDesig(byte[] in, int sp, int sl, byte[][] desigs) {
            if (desigs == null) return -1;
            int i = 0;
            while (i < desigs.length) {
                if (desigs[i] != null && sl - sp >= desigs[i].length) {
                    int j = 0;
                    while (j < desigs[i].length && in[sp+j] == desigs[i][j]) { j++; }
                    if (j == desigs[i].length)
                        return i;
                }
                i++;
            }
            return -1;
        }

        private int findDesigBuf(ByteBuffer in, byte[][] desigs) {
            if (desigs == null) return -1;
            int i = 0;
            while (i < desigs.length) {
                if (desigs[i] != null && in.remaining() >= desigs[i].length) {
                    int j = 0;
                    in.mark();
                    while (j < desigs[i].length && in.get() == desigs[i][j]) { j++; }
                    if (j == desigs[i].length)
                        return i;
                    in.reset();
                }
                i++;
            }
            return -1;
        }

        private CoderResult decodeArrayLoop(ByteBuffer src,
                                            CharBuffer dst)
        {
            byte[] sa = src.array();
            int sp = src.arrayOffset() + src.position();
            int sl = src.arrayOffset() + src.limit();
            assert (sp <= sl);
            sp = (sp <= sl ? sp : sl);

            char[] da = dst.array();
            int dp = dst.arrayOffset() + dst.position();
            int dl = dst.arrayOffset() + dst.limit();
            assert (dp <= dl);
            dp = (dp <= dl ? dp : dl);

            int b1 = 0, b2 = 0, b3 = 0;

            try {
                while (sp < sl) {
                    b1 = sa[sp] & 0xff;
                    int inputSize = 1;
                    switch (b1) {
                        case ISO_SO:
                            shiftout = true;
                            inputSize = 1;
                            break;
                        case ISO_SI:
                            shiftout = false;
                            inputSize = 1;
                            break;
                        case ISO_ESC:
                            if (sl - sp - 1 < minDesignatorLength)
                                return CoderResult.UNDERFLOW;

                            int desig = findDesig(sa, sp + 1, sl, SODesig);
                            if (desig != -1) {
                                curSODes = desig;
                                inputSize = SODesig[desig].length + 1;
                                break;
                            }
                            desig = findDesig(sa, sp + 1, sl, SS2Desig);
                            if (desig != -1) {
                                curSS2Des = desig;
                                inputSize = SS2Desig[desig].length + 1;
                                break;
                            }
                            desig = findDesig(sa, sp + 1, sl, SS3Desig);
                            if (desig != -1) {
                                curSS3Des = desig;
                                inputSize = SS3Desig[desig].length + 1;
                                break;
                            }
                            if (sl - sp < 2)
                                return CoderResult.UNDERFLOW;
                            b1 = sa[sp + 1];
                            switch(b1) {
                            case ISO_SS2_7:
                                if (sl - sp < 4)
                                    return CoderResult.UNDERFLOW;
                                b2 = sa[sp +2];
                                b3 = sa[sp +3];
                                if (dl - dp <1)
                                    return CoderResult.OVERFLOW;
                                da[dp] = decode((byte)b2,
                                                (byte)b3,
                                                SS2Flag);
                                dp++;
                                inputSize = 4;
                                break;
                            case ISO_SS3_7:
                                if (sl - sp < 4)
                                    return CoderResult.UNDERFLOW;
                                b2 = sa[sp + 2];
                                b3 = sa[sp + 3];
                                if (dl - dp <1)
                                    return CoderResult.OVERFLOW;
                                da[dp] = decode((byte)b2,
                                                (byte)b3,
                                                SS3Flag);
                                dp++;
                                inputSize = 4;
                                break;
                            default:
                                return CoderResult.malformedForLength(2);
                            }
                            break;
                        default:
                            if (dl - dp < 1)
                                return CoderResult.OVERFLOW;
                            if (!shiftout) {
                                da[dp++]=(char)(sa[sp] & 0xff);
                            } else {
                                if (dl - dp < 1)
                                    return CoderResult.OVERFLOW;
                                if (sl - sp < 2)
                                    return CoderResult.UNDERFLOW;
                                b2 = sa[sp+1] & 0xff;
                                da[dp++] = decode((byte)b1,
                                                  (byte)b2,
                                                   SOFlag);
                                inputSize = 2;
                            }
                            break;
                    }
                    sp += inputSize;
                }
                return CoderResult.UNDERFLOW;
            } finally {
                src.position(sp - src.arrayOffset());
                dst.position(dp - dst.arrayOffset());
            }
        }

        private CoderResult decodeBufferLoop(ByteBuffer src,
                                             CharBuffer dst)
        {
            int mark = src.position();
            int b1 = 0, b2 = 0, b3 = 0;

            try {
                while (src.hasRemaining()) {
                    b1 = src.get();
                    int inputSize = 1;
                    switch (b1) {
                        case ISO_SO:
                            shiftout = true;
                            break;
                        case ISO_SI:
                            shiftout = false;
                            break;
                        case ISO_ESC:
                            if (src.remaining() < minDesignatorLength)
                                return CoderResult.UNDERFLOW;

                            int desig = findDesigBuf(src, SODesig);
                            if (desig != -1) {
                                curSODes = desig;
                                inputSize = SODesig[desig].length + 1;
                                break;
                            }
                            desig = findDesigBuf(src, SS2Desig);
                            if (desig != -1) {
                                curSS2Des = desig;
                                inputSize = SS2Desig[desig].length + 1;
                                break;
                            }
                            desig = findDesigBuf(src, SS3Desig);
                            if (desig != -1) {
                                curSS3Des = desig;
                                inputSize = SS3Desig[desig].length + 1;
                                break;
                            }

                            if (src.remaining() < 1)
                                return CoderResult.UNDERFLOW;
                            b1 = src.get();
                            switch(b1) {
                                case ISO_SS2_7:
                                    if (src.remaining() < 2)
                                        return CoderResult.UNDERFLOW;
                                    b2 = src.get();
                                    b3 = src.get();
                                    if (dst.remaining() < 1)
                                        return CoderResult.OVERFLOW;
                                    dst.put(decode((byte)b2,
                                                   (byte)b3,
                                                   SS2Flag));
                                    inputSize = 4;
                                    break;
                                case ISO_SS3_7:
                                    if (src.remaining() < 2)
                                        return CoderResult.UNDERFLOW;
                                    b2 = src.get();
                                    b3 = src.get();
                                    if (dst.remaining() < 1)
                                        return CoderResult.OVERFLOW;
                                    dst.put(decode((byte)b2,
                                                   (byte)b3,
                                                   SS3Flag));
                                    inputSize = 4;
                                    break;
                                default:
                                    return CoderResult.malformedForLength(2);
                            }
                            break;
                        default:
                            if (dst.remaining() < 1)
                                return CoderResult.OVERFLOW;
                            if (!shiftout) {
                                dst.put((char)(b1 & 0xff));
                            } else {
                                if (dst.remaining() < 1)
                                    return CoderResult.OVERFLOW;
                                if (src.remaining() < 1)
                                    return CoderResult.UNDERFLOW;
                                b2 = src.get() & 0xff;
                                dst.put(decode((byte)b1,
                                                      (byte)b2,
                                                        SOFlag));
                                inputSize = 2;
                            }
                            break;
                    }
                    mark += inputSize;
                }
                return CoderResult.UNDERFLOW;
            } catch (Exception e) { e.printStackTrace(); return CoderResult.OVERFLOW; }
            finally {
                src.position(mark);
            }
        }

        protected CoderResult decodeLoop(ByteBuffer src,
                                         CharBuffer dst)
        {
            if (src.hasArray() && dst.hasArray())
                return decodeArrayLoop(src, dst);
            else
                return decodeBufferLoop(src, dst);
        }
    }

    protected static class Encoder extends CharsetEncoder {
        private final Surrogate.Parser sgp = new Surrogate.Parser();
        private final byte SS2 = (byte)0x8e;
        private final byte PLANE2 = (byte)0xA2;
        private final byte PLANE3 = (byte)0xA3;
        private final byte MSB = (byte)0x80;

        protected final byte maximumDesignatorLength = 4;

        protected String SODesig,
                         SS2Desig = null,
                         SS3Desig = null;

        protected CharsetEncoder ISOEncoder;

        private boolean shiftout = false;
        private boolean SODesDefined = false;
        private boolean SS2DesDefined = false;
        private boolean SS3DesDefined = false;

        private boolean newshiftout = false;
        private boolean newSODesDefined = false;
        private boolean newSS2DesDefined = false;
        private boolean newSS3DesDefined = false;

        protected Encoder(Charset cs) {
            super(cs, 4.0f, 8.0f);
        }

        public boolean canEncode(char c) {
            return (ISOEncoder.canEncode(c));
        }

        protected void implReset() {
            shiftout = false;
            SODesDefined = false;
            SS2DesDefined = false;
            SS3DesDefined = false;
        }

        private int unicodeToNative(char unicode, byte ebyte[])
        {
            int index = 0;
            byte        tmpByte[];
            char        convChar[] = {unicode};
            byte        convByte[] = new byte[4];
            int         converted;

            try{
                CharBuffer cc = CharBuffer.wrap(convChar);
                ByteBuffer bb = ByteBuffer.allocate(4);
                ISOEncoder.encode(cc, bb, true);
                bb.flip();
                converted = bb.remaining();
                bb.get(convByte,0,converted);
            } catch(Exception e) {
                return -1;
            }

            if (converted == 2) {
                if (!SODesDefined) {
                    newSODesDefined = true;
                    ebyte[0] = ISO_ESC;
                    tmpByte = SODesig.getBytes();
                    System.arraycopy(tmpByte,0,ebyte,1,tmpByte.length);
                    index = tmpByte.length+1;
                }
                if (!shiftout) {
                    newshiftout = true;
                    ebyte[index++] = ISO_SO;
                }
                ebyte[index++] = (byte)(convByte[0] & 0x7f);
                ebyte[index++] = (byte)(convByte[1] & 0x7f);
            } else {
                if(convByte[0] == SS2) {
                    if (convByte[1] == PLANE2) {
                        if (!SS2DesDefined) {
                            newSS2DesDefined = true;
                            ebyte[0] = ISO_ESC;
                            tmpByte = SS2Desig.getBytes();
                            System.arraycopy(tmpByte, 0, ebyte, 1, tmpByte.length);
                            index = tmpByte.length+1;
                        }
                        ebyte[index++] = ISO_ESC;
                        ebyte[index++] = ISO_SS2_7;
                        ebyte[index++] = (byte)(convByte[2] & 0x7f);
                        ebyte[index++] = (byte)(convByte[3] & 0x7f);
                    } else if (convByte[1] == PLANE3) {
                        if(!SS3DesDefined){
                            newSS3DesDefined = true;
                            ebyte[0] = ISO_ESC;
                            tmpByte = SS3Desig.getBytes();
                            System.arraycopy(tmpByte, 0, ebyte, 1, tmpByte.length);
                            index = tmpByte.length+1;
                        }
                        ebyte[index++] = ISO_ESC;
                        ebyte[index++] = ISO_SS3_7;
                        ebyte[index++] = (byte)(convByte[2] & 0x7f);
                        ebyte[index++] = (byte)(convByte[3] & 0x7f);
                    }
                }
            }
            return index;
        }

        private CoderResult encodeArrayLoop(CharBuffer src,
                                            ByteBuffer dst)
        {
            char[] sa = src.array();
            int sp = src.arrayOffset() + src.position();
            int sl = src.arrayOffset() + src.limit();
            assert (sp <= sl);
            sp = (sp <= sl ? sp : sl);
            byte[] da = dst.array();
            int dp = dst.arrayOffset() + dst.position();
            int dl = dst.arrayOffset() + dst.limit();
            assert (dp <= dl);
            dp = (dp <= dl ? dp : dl);

            int outputSize = 0;
            byte[]  outputByte = new byte[8];
            newshiftout = shiftout;
            newSODesDefined = SODesDefined;
            newSS2DesDefined = SS2DesDefined;
            newSS3DesDefined = SS3DesDefined;

            try {
                while (sp < sl) {
                    char c = sa[sp];
                    if (Surrogate.is(c)) {
                        if (sgp.parse(c, sa, sp, sl) < 0)
                            return sgp.error();
                        return sgp.unmappableResult();
                    }

                    if (c < 0x80) {     // ASCII
                        if (shiftout){
                            newshiftout = false;
                            outputSize = 2;
                            outputByte[0] = ISO_SI;
                            outputByte[1] = (byte)(c & 0x7f);
                        } else {
                            outputSize = 1;
                            outputByte[0] = (byte)(c & 0x7f);
                        }
                        if(sa[sp] == '\n'){
                            newSODesDefined = false;
                            newSS2DesDefined = false;
                            newSS3DesDefined = false;
                        }
                    } else {
                        outputSize = unicodeToNative(c, outputByte);
                        if (outputSize == 0) {
                            return CoderResult.unmappableForLength(1);
                        }
                    }
                    if (dl - dp < outputSize)
                        return CoderResult.OVERFLOW;

                    for (int i = 0; i < outputSize; i++)
                        da[dp++] = outputByte[i];
                    sp++;
                    shiftout = newshiftout;
                    SODesDefined = newSODesDefined;
                    SS2DesDefined = newSS2DesDefined;
                    SS3DesDefined = newSS3DesDefined;
                }
                return CoderResult.UNDERFLOW;
             } finally {
                src.position(sp - src.arrayOffset());
                dst.position(dp - dst.arrayOffset());
             }
        }


        private CoderResult encodeBufferLoop(CharBuffer src,
                                             ByteBuffer dst)
        {
            int outputSize = 0;
            byte[]  outputByte = new byte[8];
            int     inputSize = 0;                 // Size of input
            newshiftout = shiftout;
            newSODesDefined = SODesDefined;
            newSS2DesDefined = SS2DesDefined;
            newSS3DesDefined = SS3DesDefined;
            int mark = src.position();

            try {
                while (src.hasRemaining()) {
                    char inputChar = src.get();
                    if (Surrogate.is(inputChar)) {
                        if (sgp.parse(inputChar, src) < 0)
                            return sgp.error();
                        return sgp.unmappableResult();
                    }
                    if (inputChar < 0x80) {     // ASCII
                        if (shiftout){
                            newshiftout = false;
                            outputSize = 2;
                            outputByte[0] = ISO_SI;
                            outputByte[1] = (byte)(inputChar & 0x7f);
                        } else {
                            outputSize = 1;
                            outputByte[0] = (byte)(inputChar & 0x7f);
                        }
                        if(inputChar == '\n'){
                            newSODesDefined = false;
                            newSS2DesDefined = false;
                            newSS3DesDefined = false;
                        }
                    } else {
                        outputSize = unicodeToNative(inputChar, outputByte);
                        if (outputSize == 0) {
                            return CoderResult.unmappableForLength(1);
                        }
                    }

                    if (dst.remaining() < outputSize)
                        return CoderResult.OVERFLOW;
                    for (int i = 0; i < outputSize; i++)
                        dst.put(outputByte[i]);
                    mark++;
                    shiftout = newshiftout;
                    SODesDefined = newSODesDefined;
                    SS2DesDefined = newSS2DesDefined;
                    SS3DesDefined = newSS3DesDefined;
                }
                return CoderResult.UNDERFLOW;
            } finally {
                src.position(mark);
            }
        }

        protected CoderResult encodeLoop(CharBuffer src,
                                         ByteBuffer dst)
        {
            if (src.hasArray() && dst.hasArray())
                return encodeArrayLoop(src, dst);
            else
                return encodeBufferLoop(src, dst);
        }
    }
}
