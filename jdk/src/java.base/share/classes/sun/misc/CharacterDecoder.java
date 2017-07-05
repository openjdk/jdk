/*
 * Copyright (c) 1995, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.misc;

import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * This class defines the decoding half of character encoders.
 * A character decoder is an algorithim for transforming 8 bit
 * binary data that has been encoded into text by a character
 * encoder, back into original binary form.
 *
 * The character encoders, in general, have been structured
 * around a central theme that binary data can be encoded into
 * text that has the form:
 *
 * <pre>
 *      [Buffer Prefix]
 *      [Line Prefix][encoded data atoms][Line Suffix]
 *      [Buffer Suffix]
 * </pre>
 *
 * Of course in the simplest encoding schemes, the buffer has no
 * distinct prefix of suffix, however all have some fixed relationship
 * between the text in an 'atom' and the binary data itself.
 *
 * In the CharacterEncoder and CharacterDecoder classes, one complete
 * chunk of data is referred to as a <i>buffer</i>. Encoded buffers
 * are all text, and decoded buffers (sometimes just referred to as
 * buffers) are binary octets.
 *
 * To create a custom decoder, you must, at a minimum,  overide three
 * abstract methods in this class.
 * <DL>
 * <DD>bytesPerAtom which tells the decoder how many bytes to
 * expect from decodeAtom
 * <DD>decodeAtom which decodes the bytes sent to it as text.
 * <DD>bytesPerLine which tells the encoder the maximum number of
 * bytes per line.
 * </DL>
 *
 * In general, the character decoders return error in the form of a
 * CEFormatException. The syntax of the detail string is
 * <pre>
 *      DecoderClassName: Error message.
 * </pre>
 *
 * Several useful decoders have already been written and are
 * referenced in the See Also list below.
 *
 * @author      Chuck McManis
 * @see         CEFormatException
 * @see         CharacterEncoder
 * @see         UCDecoder
 * @see         UUDecoder
 * @see         BASE64Decoder
 */

public abstract class CharacterDecoder {

    /** Return the number of bytes per atom of decoding */
    protected abstract int bytesPerAtom();

    /** Return the maximum number of bytes that can be encoded per line */
    protected abstract int bytesPerLine();

    /** decode the beginning of the buffer, by default this is a NOP. */
    protected void decodeBufferPrefix(PushbackInputStream aStream, OutputStream bStream) throws IOException { }

    /** decode the buffer suffix, again by default it is a NOP. */
    protected void decodeBufferSuffix(PushbackInputStream aStream, OutputStream bStream) throws IOException { }

    /**
     * This method should return, if it knows, the number of bytes
     * that will be decoded. Many formats such as uuencoding provide
     * this information. By default we return the maximum bytes that
     * could have been encoded on the line.
     */
    protected int decodeLinePrefix(PushbackInputStream aStream, OutputStream bStream) throws IOException {
        return (bytesPerLine());
    }

    /**
     * This method post processes the line, if there are error detection
     * or correction codes in a line, they are generally processed by
     * this method. The simplest version of this method looks for the
     * (newline) character.
     */
    protected void decodeLineSuffix(PushbackInputStream aStream, OutputStream bStream) throws IOException { }

    /**
     * This method does an actual decode. It takes the decoded bytes and
     * writes them to the OutputStream. The integer <i>l</i> tells the
     * method how many bytes are required. This is always {@literal <=} bytesPerAtom().
     */
    protected void decodeAtom(PushbackInputStream aStream, OutputStream bStream, int l) throws IOException {
        throw new CEStreamExhausted();
    }

    /**
     * This method works around the bizarre semantics of BufferedInputStream's
     * read method.
     */
    protected int readFully(InputStream in, byte buffer[], int offset, int len)
        throws java.io.IOException {
        for (int i = 0; i < len; i++) {
            int q = in.read();
            if (q == -1)
                return ((i == 0) ? -1 : i);
            buffer[i+offset] = (byte)q;
        }
        return len;
    }

    /**
     * Decode the text from the InputStream and write the decoded
     * octets to the OutputStream. This method runs until the stream
     * is exhausted.
     * @exception CEFormatException An error has occurred while decoding
     * @exception CEStreamExhausted The input stream is unexpectedly out of data
     */
    public void decodeBuffer(InputStream aStream, OutputStream bStream) throws IOException {
        int     i;
        int     totalBytes = 0;

        PushbackInputStream ps = new PushbackInputStream (aStream);
        decodeBufferPrefix(ps, bStream);
        while (true) {
            int length;

            try {
                length = decodeLinePrefix(ps, bStream);
                for (i = 0; (i+bytesPerAtom()) < length; i += bytesPerAtom()) {
                    decodeAtom(ps, bStream, bytesPerAtom());
                    totalBytes += bytesPerAtom();
                }
                if ((i + bytesPerAtom()) == length) {
                    decodeAtom(ps, bStream, bytesPerAtom());
                    totalBytes += bytesPerAtom();
                } else {
                    decodeAtom(ps, bStream, length - i);
                    totalBytes += (length - i);
                }
                decodeLineSuffix(ps, bStream);
            } catch (CEStreamExhausted e) {
                break;
            }
        }
        decodeBufferSuffix(ps, bStream);
    }

    /**
     * Alternate decode interface that takes a String containing the encoded
     * buffer and returns a byte array containing the data.
     * @exception CEFormatException An error has occurred while decoding
     */
    public byte[] decodeBuffer(String inputString) throws IOException {
        byte inputBuffer[] = inputString.getBytes("ISO-8859-1");
        ByteArrayInputStream inStream = new ByteArrayInputStream(inputBuffer);
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        decodeBuffer(inStream, outStream);
        return outStream.toByteArray();
    }

    /**
     * Decode the contents of the inputstream into a buffer.
     */
    public byte[] decodeBuffer(InputStream in) throws IOException {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        decodeBuffer(in, outStream);
        return outStream.toByteArray();
    }

    /**
     * Decode the contents of the String into a ByteBuffer.
     */
    public ByteBuffer decodeBufferToByteBuffer(String inputString)
        throws IOException {
        return ByteBuffer.wrap(decodeBuffer(inputString));
    }

    /**
     * Decode the contents of the inputStream into a ByteBuffer.
     */
    public ByteBuffer decodeBufferToByteBuffer(InputStream in)
        throws IOException {
        return ByteBuffer.wrap(decodeBuffer(in));
    }
}
