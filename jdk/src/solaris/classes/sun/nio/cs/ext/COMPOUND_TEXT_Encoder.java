/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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
package sun.nio.cs.ext;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class COMPOUND_TEXT_Encoder extends CharsetEncoder {

    /**
     * NOTE: The following four static variables should be used *only* for
     * testing whether a encoder can encode a specific character. They
     * cannot be used for actual encoding because they are shared across all
     * COMPOUND_TEXT encoders and may be stateful.
     */
    private static final Map<String,CharsetEncoder> encodingToEncoderMap =
      Collections.synchronizedMap(new HashMap<String,CharsetEncoder>(21, 1.0f));
    private static final CharsetEncoder latin1Encoder;
    private static final CharsetEncoder defaultEncoder;
    private static final boolean defaultEncodingSupported;

    static {
        CharsetEncoder encoder = Charset.defaultCharset().newEncoder();
        String encoding = encoder.charset().name();
        if ("ISO8859_1".equals(encoding)) {
            latin1Encoder = encoder;
            defaultEncoder = encoder;
            defaultEncodingSupported = true;
        } else {
            try {
                latin1Encoder =
                    Charset.forName("ISO8859_1").newEncoder();
            } catch (IllegalArgumentException e) {
                throw new ExceptionInInitializerError
                    ("ISO8859_1 unsupported");
            }
            defaultEncoder = encoder;
            defaultEncodingSupported = CompoundTextSupport.getEncodings().
                contains(defaultEncoder.charset().name());
        }
    }

    private CharsetEncoder encoder;
    private char[] charBuf = new char[1];
    private CharBuffer charbuf = CharBuffer.wrap(charBuf);
    private ByteArrayOutputStream nonStandardCharsetBuffer;
    private byte[] byteBuf;
    private ByteBuffer bytebuf;
    private int numNonStandardChars, nonStandardEncodingLen;

    public COMPOUND_TEXT_Encoder(Charset cs) {
        super(cs,
              (float)(CompoundTextSupport.MAX_CONTROL_SEQUENCE_LEN + 2),
              (float)(CompoundTextSupport.MAX_CONTROL_SEQUENCE_LEN + 2));
        try {
            encoder = Charset.forName("ISO8859_1").newEncoder();
        } catch (IllegalArgumentException cannotHappen) {}
        initEncoder(encoder);
    }

    protected CoderResult encodeLoop(CharBuffer src, ByteBuffer des) {
        CoderResult cr = CoderResult.UNDERFLOW;
        char[] input = src.array();
        int inOff = src.arrayOffset() + src.position();
        int inEnd = src.arrayOffset() + src.limit();

        try {
            while (inOff < inEnd && cr.isUnderflow()) {
                charBuf[0] = input[inOff];
                if (charBuf[0] <= '\u0008' ||
                    (charBuf[0] >= '\u000B' && charBuf[0] <= '\u001F') ||
                    (charBuf[0] >= '\u0080' && charBuf[0] <= '\u009F')) {
                    // The compound text specification only permits the octets
                    // 0x09, 0x0A, 0x1B, and 0x9B in C0 and C1. Of these, 1B and
                    // 9B must also be removed because they initiate control
                    // sequences.
                    charBuf[0] = '?';
                }

                CharsetEncoder enc = getEncoder(charBuf[0]);
                //System.out.println("char=" + charBuf[0] + ", enc=" + enc);
                if (enc == null) {
                    if (unmappableCharacterAction()
                        == CodingErrorAction.REPORT) {
                        charBuf[0] = '?';
                        enc = latin1Encoder;
                    } else {
                        return CoderResult.unmappableForLength(1);
                    }
                }
                if (enc != encoder) {
                    if (nonStandardCharsetBuffer != null) {
                        cr = flushNonStandardCharsetBuffer(des);
                    } else {
                        //cr= encoder.flush(des);
                        flushEncoder(encoder, des);
                    }
                    if (!cr.isUnderflow())
                        return cr;
                    byte[] escSequence = CompoundTextSupport.
                        getEscapeSequence(enc.charset().name());
                    if (escSequence == null) {
                        throw new InternalError("Unknown encoding: " +
                                                enc.charset().name());
                    } else if (escSequence[1] == (byte)0x25 &&
                               escSequence[2] == (byte)0x2F) {
                        initNonStandardCharsetBuffer(enc, escSequence);
                    } else if (des.remaining() >= escSequence.length) {
                        des.put(escSequence, 0, escSequence.length);
                    } else {
                        return CoderResult.OVERFLOW;
                    }
                    encoder = enc;
                    continue;
                }
                charbuf.rewind();
                if (nonStandardCharsetBuffer == null) {
                    cr = encoder.encode(charbuf, des, false);
                } else {
                    bytebuf.clear();
                    cr = encoder.encode(charbuf, bytebuf, false);
                    bytebuf.flip();
                    nonStandardCharsetBuffer.write(byteBuf,
                                                   0, bytebuf.limit());
                    numNonStandardChars++;
                }
                inOff++;
            }
            return cr;
        } finally {
            src.position(inOff - src.arrayOffset());
        }
    }

    protected CoderResult implFlush(ByteBuffer out) {
        CoderResult cr = (nonStandardCharsetBuffer != null)
            ? flushNonStandardCharsetBuffer(out)
            //: encoder.flush(out);
            : flushEncoder(encoder, out);
        reset();
        return cr;
    }

    private void initNonStandardCharsetBuffer(CharsetEncoder c,
                                              byte[] escSequence)
    {
        nonStandardCharsetBuffer = new ByteArrayOutputStream();
        byteBuf = new byte[(int)c.maxBytesPerChar()];
        bytebuf = ByteBuffer.wrap(byteBuf);
        nonStandardCharsetBuffer.write(escSequence, 0, escSequence.length);
        nonStandardCharsetBuffer.write(0); // M placeholder
        nonStandardCharsetBuffer.write(0); // L placeholder
        byte[] encoding = CompoundTextSupport.
            getEncoding(c.charset().name());
        if (encoding == null) {
            throw new InternalError
                ("Unknown encoding: " + encoder.charset().name());
        }
        nonStandardCharsetBuffer.write(encoding, 0, encoding.length);
        nonStandardCharsetBuffer.write(0x02); // divider
        nonStandardEncodingLen = encoding.length + 1;
    }

    private CoderResult flushNonStandardCharsetBuffer(ByteBuffer out) {
        if (numNonStandardChars > 0) {
            byte[] flushBuf = new byte[(int)encoder.maxBytesPerChar() *
                                       numNonStandardChars];
            ByteBuffer bb = ByteBuffer.wrap(flushBuf);
            flushEncoder(encoder, bb);
            bb.flip();
            nonStandardCharsetBuffer.write(flushBuf, 0, bb.limit());
            numNonStandardChars = 0;
        }

        int numBytes = nonStandardCharsetBuffer.size();
        int nonStandardBytesOff = 6 + nonStandardEncodingLen;

        if (out.remaining() < (numBytes - nonStandardBytesOff) +
            nonStandardBytesOff * (((numBytes - nonStandardBytesOff) /
                                    ((1 << 14) - 1)) + 1))
        {
            return CoderResult.OVERFLOW;
        }

        byte[] nonStandardBytes =
            nonStandardCharsetBuffer.toByteArray();

        // The non-standard charset header only supports 2^14-1 bytes of data.
        // If we have more than that, we have to repeat the header.
        do {
            out.put((byte)0x1B);
            out.put((byte)0x25);
            out.put((byte)0x2F);
            out.put(nonStandardBytes[3]);

            int toWrite = Math.min(numBytes - nonStandardBytesOff,
                                   (1 << 14) - 1 - nonStandardEncodingLen);

            out.put((byte)
                (((toWrite + nonStandardEncodingLen) / 0x80) | 0x80)); // M
            out.put((byte)
                (((toWrite + nonStandardEncodingLen) % 0x80) | 0x80)); // L
            out.put(nonStandardBytes, 6, nonStandardEncodingLen);
            out.put(nonStandardBytes, nonStandardBytesOff, toWrite);
            nonStandardBytesOff += toWrite;
        } while (nonStandardBytesOff < numBytes);

        nonStandardCharsetBuffer = null;
        byteBuf = null;
        nonStandardEncodingLen = 0;
        return CoderResult.UNDERFLOW;
    }

    /**
     * Resets the encoder.
     * Call this method to reset the encoder to its initial state
     */
    protected void implReset() {
        numNonStandardChars = nonStandardEncodingLen = 0;
        nonStandardCharsetBuffer = null;
        byteBuf = null;
        try {
            encoder = Charset.forName("ISO8859_1").newEncoder();
        } catch (IllegalArgumentException cannotHappen) {
        }
        initEncoder(encoder);
    }

    /**
     * Return whether a character is mappable or not
     * @return true if a character is mappable
     */
    public boolean canEncode(char ch) {
        return getEncoder(ch) != null;
    }

    protected void implOnMalformedInput(CodingErrorAction newAction) {
        encoder.onUnmappableCharacter(newAction);
    }

    protected void implOnUnmappableCharacter(CodingErrorAction newAction) {
        encoder.onUnmappableCharacter(newAction);
    }

    protected void implReplaceWith(byte[] newReplacement) {
        if (encoder != null)
            encoder.replaceWith(newReplacement);
    }

    /**
     * Try to figure out which CharsetEncoder to use for conversion
     * of the specified Unicode character. The target character encoding
     * of the returned encoder is approved to be used with Compound Text.
     *
     * @param ch Unicode character
     * @return CharsetEncoder to convert the given character
     */
    private CharsetEncoder getEncoder(char ch) {
        // 1. Try the current encoder.
        if (encoder.canEncode(ch)) {
            return encoder;
        }

        // 2. Try the default encoder.
        if (defaultEncodingSupported && defaultEncoder.canEncode(ch)) {
            CharsetEncoder retval = null;
            try {
                retval = defaultEncoder.charset().newEncoder();
            } catch (UnsupportedOperationException cannotHappen) {
            }
            initEncoder(retval);
            return retval;
        }

        // 3. Try ISO8859-1.
        if (latin1Encoder.canEncode(ch)) {
            CharsetEncoder retval = null;
            try {
                retval = latin1Encoder.charset().newEncoder();
            } catch (UnsupportedOperationException cannotHappen) {}
            initEncoder(retval);
            return retval;
        }

        // 4. Brute force search of all supported encodings.
        for (String encoding : CompoundTextSupport.getEncodings())
        {
            CharsetEncoder enc = encodingToEncoderMap.get(encoding);
            if (enc == null) {
                enc = CompoundTextSupport.getEncoder(encoding);
                if (enc == null) {
                    throw new InternalError("Unsupported encoding: " +
                                            encoding);
                }
                encodingToEncoderMap.put(encoding, enc);
            }
            if (enc.canEncode(ch)) {
                CharsetEncoder retval = CompoundTextSupport.getEncoder(encoding);
                initEncoder(retval);
                return retval;
            }
        }

        return null;
    }

    private void initEncoder(CharsetEncoder enc) {
        try {
            enc.onUnmappableCharacter(CodingErrorAction.REPLACE)
                .replaceWith(replacement());
        } catch (IllegalArgumentException x) {}
    }

    private CharBuffer fcb= CharBuffer.allocate(0);
    private CoderResult flushEncoder(CharsetEncoder enc, ByteBuffer bb) {
        enc.encode(fcb, bb, true);
        return enc.flush(bb);
    }
}
