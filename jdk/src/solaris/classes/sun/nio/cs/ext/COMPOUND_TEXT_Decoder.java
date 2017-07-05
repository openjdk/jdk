/*
 * Copyright (c) 2001, 2005, Oracle and/or its affiliates. All rights reserved.
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

/**
 * An algorithmic conversion from COMPOUND_TEXT to Unicode.
 */

public class COMPOUND_TEXT_Decoder extends CharsetDecoder {

    private static final int NORMAL_BYTES             =  0;
    private static final int NONSTANDARD_BYTES        =  1;
    private static final int VERSION_SEQUENCE_V       =  2;
    private static final int VERSION_SEQUENCE_TERM    =  3;
    private static final int ESCAPE_SEQUENCE          =  4;
    private static final int CHARSET_NGIIF            =  5;
    private static final int CHARSET_NLIIF            =  6;
    private static final int CHARSET_NLIF             =  7;
    private static final int CHARSET_NRIIF            =  8;
    private static final int CHARSET_NRIF             =  9;
    private static final int CHARSET_NONSTANDARD_FOML = 10;
    private static final int CHARSET_NONSTANDARD_OML  = 11;
    private static final int CHARSET_NONSTANDARD_ML   = 12;
    private static final int CHARSET_NONSTANDARD_L    = 13;
    private static final int CHARSET_NONSTANDARD      = 14;
    private static final int CHARSET_LIIF             = 15;
    private static final int CHARSET_LIF              = 16;
    private static final int CHARSET_RIIF             = 17;
    private static final int CHARSET_RIF              = 18;
    private static final int CONTROL_SEQUENCE_PIF     = 19;
    private static final int CONTROL_SEQUENCE_IF      = 20;
    private static final int EXTENSION_ML             = 21;
    private static final int EXTENSION_L              = 22;
    private static final int EXTENSION                = 23;
    private static final int ESCAPE_SEQUENCE_OTHER    = 24;

    private static final String ERR_LATIN1 = "ISO8859_1 unsupported";
    private static final String ERR_ILLSTATE = "Illegal state";
    private static final String ERR_ESCBYTE =
        "Illegal byte in 0x1B escape sequence";
    private static final String ERR_ENCODINGBYTE =
        "Illegal byte in non-standard character set name";
    private static final String ERR_CTRLBYTE =
        "Illegal byte in 0x9B control sequence";
    private static final String ERR_CTRLPI =
        "P following I in 0x9B control sequence";
    private static final String ERR_VERSTART =
        "Versioning escape sequence can only appear at start of byte stream";
    private static final String ERR_VERMANDATORY =
        "Cannot parse mandatory extensions";
    private static final String ERR_ENCODING = "Unknown encoding: ";
    private static final String ERR_FLUSH =
        "Escape sequence, control sequence, or ML extension not terminated";

    private int state = NORMAL_BYTES ;
    private int ext_count, ext_offset;
    private boolean versionSequenceAllowed = true;
    private byte[] byteBuf = new byte[1];
    private ByteBuffer inBB = ByteBuffer.allocate(16);
    private ByteArrayOutputStream queue = new ByteArrayOutputStream(),
        encodingQueue = new ByteArrayOutputStream();

    private CharsetDecoder glDecoder, grDecoder, nonStandardDecoder,
        lastDecoder;
    private boolean glHigh = false, grHigh = true;


    public COMPOUND_TEXT_Decoder(Charset cs) {
        super(cs, 1.0f, 1.0f);
        try {
            // Initial state in ISO 2022 designates Latin-1 charset.
            glDecoder = Charset.forName("ASCII").newDecoder();
            grDecoder = Charset.forName("ISO8859_1").newDecoder();
        } catch (IllegalArgumentException e) {
            error(ERR_LATIN1);
        }
        initDecoder(glDecoder);
        initDecoder(grDecoder);
    }

    protected CoderResult decodeLoop(ByteBuffer src, CharBuffer des) {
        CoderResult cr = CoderResult.UNDERFLOW;
        byte[] input = src.array();
        int inOff = src.arrayOffset() + src.position();
        int inEnd = src.arrayOffset() + src.limit();

        try {
            while (inOff < inEnd && cr.isUnderflow()) {
                // Byte parsing is done with shorts instead of bytes because
                // Java bytes are signed, while COMPOUND_TEXT bytes are not. If
                // we used the Java byte type, the > and < tests during parsing
                // would not work correctly.
                cr = handleByte((short)(input[inOff] & 0xFF), des);
                inOff++;
            }
            return cr;
        } finally {
            src.position(inOff - src.arrayOffset());
        }
    }

    private CoderResult handleByte(short newByte, CharBuffer cb) {
        CoderResult cr = CoderResult.UNDERFLOW;
        switch (state) {
        case NORMAL_BYTES:
            cr= normalBytes(newByte, cb);
            break;
        case NONSTANDARD_BYTES:
            cr = nonStandardBytes(newByte, cb);
            break;
        case VERSION_SEQUENCE_V:
        case VERSION_SEQUENCE_TERM:
            cr = versionSequence(newByte);
            break;
        case ESCAPE_SEQUENCE:
            cr = escapeSequence(newByte);
            break;
        case CHARSET_NGIIF:
            cr = charset94N(newByte);
            break;
        case CHARSET_NLIIF:
        case CHARSET_NLIF:
            cr = charset94NL(newByte, cb);
            break;
        case CHARSET_NRIIF:
        case CHARSET_NRIF:
            cr = charset94NR(newByte, cb);
            break;
        case CHARSET_NONSTANDARD_FOML:
        case CHARSET_NONSTANDARD_OML:
        case CHARSET_NONSTANDARD_ML:
        case CHARSET_NONSTANDARD_L:
        case CHARSET_NONSTANDARD:
            cr = charsetNonStandard(newByte, cb);
            break;
        case CHARSET_LIIF:
        case CHARSET_LIF:
            cr = charset9496L(newByte, cb);
            break;
        case CHARSET_RIIF:
        case CHARSET_RIF:
            cr = charset9496R(newByte, cb);
            break;
        case CONTROL_SEQUENCE_PIF:
        case CONTROL_SEQUENCE_IF:
            cr = controlSequence(newByte);
            break;
        case EXTENSION_ML:
        case EXTENSION_L:
        case EXTENSION:
            cr = extension(newByte);
            break;
        case ESCAPE_SEQUENCE_OTHER:
            cr = escapeSequenceOther(newByte);
            break;
        default:
            error(ERR_ILLSTATE);
        }
        return cr;
    }

    private CoderResult normalBytes(short newByte, CharBuffer cb) {
        CoderResult cr = CoderResult.UNDERFLOW;
        if ((newByte >= 0x00 && newByte <= 0x1F) || // C0
            (newByte >= 0x80 && newByte <= 0x9F)) { // C1
            char newChar;

            switch (newByte) {
            case 0x1B:
                state = ESCAPE_SEQUENCE;
                queue.write(newByte);
                return cr;
            case 0x9B:
                state = CONTROL_SEQUENCE_PIF;
                versionSequenceAllowed = false;
                queue.write(newByte);
                return cr;
            case 0x09:
                versionSequenceAllowed = false;
                newChar = '\t';
                break;
            case 0x0A:
                versionSequenceAllowed = false;
                newChar = '\n';
                break;
            default:
                versionSequenceAllowed = false;
                return cr;
            }
            if (!cb.hasRemaining())
                return CoderResult.OVERFLOW;
            else
                cb.put(newChar);
        } else {
            CharsetDecoder decoder;
            boolean high;
            versionSequenceAllowed = false;

            if (newByte >= 0x20 && newByte <= 0x7F) {
                decoder = glDecoder;
                high = glHigh;
            } else /* if (newByte >= 0xA0 && newByte <= 0xFF) */ {
                decoder = grDecoder;
                high = grHigh;
            }
            if (lastDecoder != null && decoder != lastDecoder) {
                cr = flushDecoder(lastDecoder, cb);
            }
            lastDecoder = decoder;

            if (decoder != null) {
                byte b = (byte)newByte;
                if (high) {
                    b |= 0x80;
                } else {
                    b &= 0x7F;
                }
                inBB.put(b);
                inBB.flip();
                cr = decoder.decode(inBB, cb, false);
                if (!inBB.hasRemaining() || cr.isMalformed()) {
                    inBB.clear();
                } else {
                  int pos = inBB.limit();
                  inBB.clear();
                  inBB.position(pos);
                }
            } else if (cb.remaining() < replacement().length()) {
                cb.put(replacement());
            } else {
                return CoderResult.OVERFLOW;
            }
        }
        return cr;
    }

    private CoderResult nonStandardBytes(short newByte, CharBuffer cb)
    {
        CoderResult cr = CoderResult.UNDERFLOW;
        if (nonStandardDecoder != null) {
            //byteBuf[0] = (byte)newByte;
            inBB.put((byte)newByte);
            inBB.flip();
            cr = nonStandardDecoder.decode(inBB, cb, false);
            if (!inBB.hasRemaining()) {
                inBB.clear();
            } else {
                int pos = inBB.limit();
                inBB.clear();
                inBB.position(pos);
            }
        } else if (cb.remaining() < replacement().length()) {
            cb.put(replacement());
        } else {
            return CoderResult.OVERFLOW;
        }

        ext_offset++;
        if (ext_offset >= ext_count) {
            ext_offset = ext_count = 0;
            state = NORMAL_BYTES;
            cr = flushDecoder(nonStandardDecoder, cb);
            nonStandardDecoder = null;
        }
        return cr;
    }

    private CoderResult escapeSequence(short newByte) {
        switch (newByte) {
        case 0x23:
            state = VERSION_SEQUENCE_V;
            break;
        case 0x24:
            state = CHARSET_NGIIF;
            versionSequenceAllowed = false;
            break;
        case 0x25:
            state = CHARSET_NONSTANDARD_FOML;
            versionSequenceAllowed = false;
            break;
        case 0x28:
            state = CHARSET_LIIF;
            versionSequenceAllowed = false;
            break;
        case 0x29:
        case 0x2D:
            state = CHARSET_RIIF;
            versionSequenceAllowed = false;
            break;
        default:
            // escapeSequenceOther will write to queue if appropriate
            return escapeSequenceOther(newByte);
        }

        queue.write(newByte);
        return CoderResult.UNDERFLOW;
    }

    /**
     * Test for unknown, but valid, escape sequences.
     */
    private CoderResult escapeSequenceOther(short newByte) {
        if (newByte >= 0x20 && newByte <= 0x2F) {
            // {I}
            state = ESCAPE_SEQUENCE_OTHER;
            versionSequenceAllowed = false;
            queue.write(newByte);
        } else if (newByte >= 0x30 && newByte <= 0x7E) {
            // F -- end of sequence
            state = NORMAL_BYTES;
            versionSequenceAllowed = false;
            queue.reset();
        } else {
            return malformedInput(ERR_ESCBYTE);
        }
        return CoderResult.UNDERFLOW;
    }

    /**
     * Parses directionality, as well as unknown, but valid, control sequences.
     */
    private CoderResult controlSequence(short newByte) {
        if (newByte >= 0x30 && newByte <= 0x3F) {
            // {P}
            if (state == CONTROL_SEQUENCE_IF) {
                // P no longer allowed
                return malformedInput(ERR_CTRLPI);
            }
            queue.write(newByte);
        } else if (newByte >= 0x20 && newByte <= 0x2F) {
            // {I}
            state = CONTROL_SEQUENCE_IF;
            queue.write(newByte);
        } else if (newByte >= 0x40 && newByte <= 0x7E) {
            // F -- end of sequence
            state = NORMAL_BYTES;
            queue.reset();
        } else {
            return malformedInput(ERR_CTRLBYTE);
        }
        return CoderResult.UNDERFLOW;
    }

    private CoderResult versionSequence(short newByte) {
        if (state == VERSION_SEQUENCE_V) {
            if (newByte >= 0x20 && newByte <= 0x2F) {
                state = VERSION_SEQUENCE_TERM;
                queue.write(newByte);
            } else {
                return escapeSequenceOther(newByte);
            }
        } else /* if (state == VERSION_SEQUENCE_TERM) */ {
            switch (newByte) {
            case 0x30:
                if (!versionSequenceAllowed) {
                    return malformedInput(ERR_VERSTART);
                }

                // OK to ignore extensions
                versionSequenceAllowed = false;
                state = NORMAL_BYTES;
                queue.reset();
                break;
            case 0x31:
                return malformedInput((versionSequenceAllowed)
                               ? ERR_VERMANDATORY : ERR_VERSTART);
            default:
                return escapeSequenceOther(newByte);
            }
        }
        return CoderResult.UNDERFLOW;
    }

    private CoderResult charset94N(short newByte) {
        switch (newByte) {
        case 0x28:
            state = CHARSET_NLIIF;
            break;
        case 0x29:
            state = CHARSET_NRIIF;
            break;
        default:
            // escapeSequenceOther will write byte if appropriate
            return escapeSequenceOther(newByte);
        }

        queue.write(newByte);
        return CoderResult.UNDERFLOW;
    }

    private CoderResult charset94NL(short newByte, CharBuffer cb) {
        if (newByte >= 0x21 &&
            newByte <= (state == CHARSET_NLIIF ? 0x23 : 0x2F)) {
            // {I}
            state = CHARSET_NLIF;
            queue.write(newByte);
        } else if (newByte >= 0x40 && newByte <= 0x7E) {
            // F
            return switchDecoder(newByte, cb);
        } else {
            return escapeSequenceOther(newByte);
        }
        return CoderResult.UNDERFLOW;
    }

    private CoderResult charset94NR(short newByte, CharBuffer cb)
    {
        if (newByte >= 0x21 &&
            newByte <= (state == CHARSET_NRIIF ? 0x23 : 0x2F)) {
            // {I}
            state = CHARSET_NRIF;
            queue.write(newByte);
        } else if (newByte >= 0x40 && newByte <= 0x7E) {
            // F
            return switchDecoder(newByte, cb);
        } else {
            return escapeSequenceOther(newByte);
        }
        return CoderResult.UNDERFLOW;
    }

    private CoderResult charset9496L(short newByte, CharBuffer cb) {
        if (newByte >= 0x21 &&
            newByte <= (state == CHARSET_LIIF ? 0x23 : 0x2F)) {
            // {I}
            state = CHARSET_LIF;
            queue.write(newByte);
            return CoderResult.UNDERFLOW;
        } else if (newByte >= 0x40 && newByte <= 0x7E) {
            // F
            return switchDecoder(newByte, cb);
        } else {
            return escapeSequenceOther(newByte);
        }
    }

    private CoderResult charset9496R(short newByte, CharBuffer cb) {
        if (newByte >= 0x21 &&
            newByte <= (state == CHARSET_RIIF ? 0x23 : 0x2F)) {
            // {I}
            state = CHARSET_RIF;
            queue.write(newByte);
            return CoderResult.UNDERFLOW;
        } else if (newByte >= 0x40 && newByte <= 0x7E) {
            // F
            return switchDecoder(newByte, cb);
        } else {
            return escapeSequenceOther(newByte);
        }
    }

    private CoderResult charsetNonStandard(short newByte, CharBuffer cb) {
        switch (state) {
        case CHARSET_NONSTANDARD_FOML:
            if (newByte == 0x2F) {
                state = CHARSET_NONSTANDARD_OML;
                queue.write(newByte);
            } else {
                return escapeSequenceOther(newByte);
            }
            break;
        case CHARSET_NONSTANDARD_OML:
            if (newByte >= 0x30 && newByte <= 0x34) {
                state = CHARSET_NONSTANDARD_ML;
                queue.write(newByte);
            } else if (newByte >= 0x35 && newByte <= 0x3F) {
                state = EXTENSION_ML;
                queue.write(newByte);
            } else {
                return escapeSequenceOther(newByte);
            }
            break;
        case CHARSET_NONSTANDARD_ML:
            ext_count = (newByte & 0x7F) * 0x80;
            state = CHARSET_NONSTANDARD_L;
            break;
        case CHARSET_NONSTANDARD_L:
            ext_count = ext_count + (newByte & 0x7F);
            state = (ext_count > 0) ? CHARSET_NONSTANDARD : NORMAL_BYTES;
            break;
        case CHARSET_NONSTANDARD:
            if (newByte == 0x3F || newByte == 0x2A) {
                queue.reset(); // In this case, only current byte is bad.
                return malformedInput(ERR_ENCODINGBYTE);
            }
            ext_offset++;
            if (ext_offset >= ext_count) {
                ext_offset = ext_count = 0;
                state = NORMAL_BYTES;
                queue.reset();
                encodingQueue.reset();
            } else if (newByte == 0x02) {
                // encoding name terminator
                return switchDecoder((short)0, cb);
            } else {
                encodingQueue.write(newByte);
            }
            break;
        default:
            error(ERR_ILLSTATE);
        }
        return CoderResult.UNDERFLOW;
    }

    private CoderResult extension(short newByte) {
        switch (state) {
        case EXTENSION_ML:
            ext_count = (newByte & 0x7F) * 0x80;
            state = EXTENSION_L;
            break;
        case EXTENSION_L:
            ext_count = ext_count + (newByte & 0x7F);
            state = (ext_count > 0) ? EXTENSION : NORMAL_BYTES;
            break;
        case EXTENSION:
            // Consume 'count' bytes. Don't bother putting them on the queue.
            // There may be too many and we can't do anything with them anyway.
            ext_offset++;
            if (ext_offset >= ext_count) {
                ext_offset = ext_count = 0;
                state = NORMAL_BYTES;
                queue.reset();
            }
            break;
        default:
            error(ERR_ILLSTATE);
        }
        return CoderResult.UNDERFLOW;
    }

    /**
     * Preconditions:
     *   1. 'queue' contains ControlSequence.escSequence
     *   2. 'encodingQueue' contains ControlSequence.encoding
     */
    private CoderResult switchDecoder(short lastByte, CharBuffer cb) {
        CoderResult cr = CoderResult.UNDERFLOW;
        CharsetDecoder decoder = null;
        boolean high = false;
        byte[] escSequence;
        byte[] encoding = null;

        if (lastByte != 0) {
            queue.write(lastByte);
        }

        escSequence = queue.toByteArray();
        queue.reset();

        if (state == CHARSET_NONSTANDARD) {
            encoding = encodingQueue.toByteArray();
            encodingQueue.reset();
            decoder = CompoundTextSupport.
                getNonStandardDecoder(escSequence, encoding);
        } else {
            decoder = CompoundTextSupport.getStandardDecoder(escSequence);
            high = CompoundTextSupport.getHighBit(escSequence);
        }
        if (decoder != null) {
            initDecoder(decoder);
        } else if (unmappableCharacterAction() == CodingErrorAction.REPORT) {
            int badInputLength = 1;
            if (encoding != null) {
                badInputLength = encoding.length;
            } else if (escSequence.length > 0) {
                badInputLength = escSequence.length;
            }
            return CoderResult.unmappableForLength(badInputLength);
        }

        if (state == CHARSET_NLIIF || state == CHARSET_NLIF ||
            state == CHARSET_LIIF || state == CHARSET_LIF)
        {
            if (lastDecoder == glDecoder) {
                cr = flushDecoder(glDecoder, cb);
            }
            glDecoder = lastDecoder = decoder;
            glHigh = high;
            state = NORMAL_BYTES;
        } else if (state == CHARSET_NRIIF || state == CHARSET_NRIF ||
                   state == CHARSET_RIIF || state == CHARSET_RIF) {
            if (lastDecoder == grDecoder) {
                cr = flushDecoder(grDecoder, cb);
            }
            grDecoder = lastDecoder = decoder;
            grHigh = high;
            state = NORMAL_BYTES;
        } else if (state == CHARSET_NONSTANDARD) {
            if (lastDecoder != null) {
                cr = flushDecoder(lastDecoder, cb);
                lastDecoder = null;
            }
            nonStandardDecoder = decoder;
            state = NONSTANDARD_BYTES;
        } else {
            error(ERR_ILLSTATE);
        }
        return cr;
    }

    private ByteBuffer fbb= ByteBuffer.allocate(0);
    private CoderResult flushDecoder(CharsetDecoder dec, CharBuffer cb) {
        dec.decode(fbb, cb, true);
        CoderResult cr = dec.flush(cb);
        dec.reset();  //reuse
        return cr;
    }

    private CoderResult malformedInput(String msg) {
        int badInputLength = queue.size() + 1 /* current byte */ ;
        queue.reset();
        //TBD: nowhere to put the msg in CoderResult
        return CoderResult.malformedForLength(badInputLength);
    }

    private void error(String msg) {
        // For now, throw InternalError. Convert to 'assert' keyword later.
        throw new InternalError(msg);
    }

    protected CoderResult implFlush(CharBuffer out) {
        CoderResult cr = CoderResult.UNDERFLOW;
        if (lastDecoder != null)
          cr = flushDecoder(lastDecoder, out);
        if (state != NORMAL_BYTES)
            //TBD message ERR_FLUSH;
            cr = CoderResult.malformedForLength(0);
        reset();
        return cr;
    }

    /**
     * Resets the decoder.
     * Call this method to reset the decoder to its initial state
     */
    protected void implReset() {
        state = NORMAL_BYTES;
        ext_count = ext_offset = 0;
        versionSequenceAllowed = true;
        queue.reset();
        encodingQueue.reset();
        nonStandardDecoder = lastDecoder = null;
        glHigh = false;
        grHigh = true;
        try {
            // Initial state in ISO 2022 designates Latin-1 charset.
            glDecoder = Charset.forName("ASCII").newDecoder();
            grDecoder = Charset.forName("ISO8859_1").newDecoder();
        } catch (IllegalArgumentException e) {
            error(ERR_LATIN1);
        }
        initDecoder(glDecoder);
        initDecoder(grDecoder);
    }

    protected void implOnMalformedInput(CodingErrorAction newAction) {
        if (glDecoder != null)
            glDecoder.onMalformedInput(newAction);
        if (grDecoder != null)
            grDecoder.onMalformedInput(newAction);
        if (nonStandardDecoder != null)
            nonStandardDecoder.onMalformedInput(newAction);
    }

    protected void implOnUnmappableCharacter(CodingErrorAction newAction) {
        if (glDecoder != null)
            glDecoder.onUnmappableCharacter(newAction);
        if (grDecoder != null)
            grDecoder.onUnmappableCharacter(newAction);
        if (nonStandardDecoder != null)
            nonStandardDecoder.onUnmappableCharacter(newAction);
    }

    protected void implReplaceWith(String newReplacement) {
        if (glDecoder != null)
            glDecoder.replaceWith(newReplacement);
        if (grDecoder != null)
            grDecoder.replaceWith(newReplacement);
        if (nonStandardDecoder != null)
            nonStandardDecoder.replaceWith(newReplacement);
    }

    private void initDecoder(CharsetDecoder dec) {
        dec.onUnmappableCharacter(CodingErrorAction.REPLACE)
            .replaceWith(replacement());
    }
}
