/*
 * Copyright (c) 1996, 2014, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.util;

import java.io.InputStream;
import java.io.IOException;
import java.io.EOFException;
import java.util.Date;
import java.util.Vector;
import java.math.BigInteger;
import java.io.DataInputStream;

/**
 * A DER input stream, used for parsing ASN.1 DER-encoded data such as
 * that found in X.509 certificates.  DER is a subset of BER/1, which has
 * the advantage that it allows only a single encoding of primitive data.
 * (High level data such as dates still support many encodings.)  That is,
 * it uses the "Definite" Encoding Rules (DER) not the "Basic" ones (BER).
 *
 * <P>Note that, like BER/1, DER streams are streams of explicitly
 * tagged data values.  Accordingly, this programming interface does
 * not expose any variant of the java.io.InputStream interface, since
 * that kind of input stream holds untagged data values and using that
 * I/O model could prevent correct parsing of the DER data.
 *
 * <P>At this time, this class supports only a subset of the types of DER
 * data encodings which are defined.  That subset is sufficient for parsing
 * most X.509 certificates.
 *
 *
 * @author David Brownell
 * @author Amit Kapoor
 * @author Hemma Prafullchandra
 */

public class DerInputStream {

    /*
     * This version only supports fully buffered DER.  This is easy to
     * work with, though if large objects are manipulated DER becomes
     * awkward to deal with.  That's where BER is useful, since BER
     * handles streaming data relatively well.
     */
    DerInputBuffer      buffer;

    /** The DER tag of the value; one of the tag_ constants. */
    public byte         tag;

    /**
     * Create a DER input stream from a data buffer.  The buffer is not
     * copied, it is shared.  Accordingly, the buffer should be treated
     * as read-only.
     *
     * @param data the buffer from which to create the string (CONSUMED)
     */
    public DerInputStream(byte[] data) throws IOException {
        init(data, 0, data.length);
    }

    /**
     * Create a DER input stream from part of a data buffer.
     * The buffer is not copied, it is shared.  Accordingly, the
     * buffer should be treated as read-only.
     *
     * @param data the buffer from which to create the string (CONSUMED)
     * @param offset the first index of <em>data</em> which will
     *          be read as DER input in the new stream
     * @param len how long a chunk of the buffer to use,
     *          starting at "offset"
     */
    public DerInputStream(byte[] data, int offset, int len) throws IOException {
        init(data, offset, len);
    }

    /*
     * private helper routine
     */
    private void init(byte[] data, int offset, int len) throws IOException {
        if ((offset+2 > data.length) || (offset+len > data.length)) {
            throw new IOException("Encoding bytes too short");
        }
        // check for indefinite length encoding
        if (DerIndefLenConverter.isIndefinite(data[offset+1])) {
            byte[] inData = new byte[len];
            System.arraycopy(data, offset, inData, 0, len);

            DerIndefLenConverter derIn = new DerIndefLenConverter();
            buffer = new DerInputBuffer(derIn.convert(inData));
        } else
            buffer = new DerInputBuffer(data, offset, len);
        buffer.mark(Integer.MAX_VALUE);
    }

    DerInputStream(DerInputBuffer buf) {
        buffer = buf;
        buffer.mark(Integer.MAX_VALUE);
    }

    /**
     * Creates a new DER input stream from part of this input stream.
     *
     * @param len how long a chunk of the current input stream to use,
     *          starting at the current position.
     * @param do_skip true if the existing data in the input stream should
     *          be skipped.  If this value is false, the next data read
     *          on this stream and the newly created stream will be the
     *          same.
     */
    public DerInputStream subStream(int len, boolean do_skip)
    throws IOException {
        DerInputBuffer  newbuf = buffer.dup();

        newbuf.truncate(len);
        if (do_skip) {
            buffer.skip(len);
        }
        return new DerInputStream(newbuf);
    }

    /**
     * Return what has been written to this DerInputStream
     * as a byte array. Useful for debugging.
     */
    public byte[] toByteArray() {
        return buffer.toByteArray();
    }

    /*
     * PRIMITIVES -- these are "universal" ASN.1 simple types.
     *
     *  INTEGER, ENUMERATED, BIT STRING, OCTET STRING, NULL
     *  OBJECT IDENTIFIER, SEQUENCE (OF), SET (OF)
     *  UTF8String, PrintableString, T61String, IA5String, UTCTime,
     *  GeneralizedTime, BMPString.
     * Note: UniversalString not supported till encoder is available.
     */

    /**
     * Get an integer from the input stream as an integer.
     *
     * @return the integer held in this DER input stream.
     */
    public int getInteger() throws IOException {
        if (buffer.read() != DerValue.tag_Integer) {
            throw new IOException("DER input, Integer tag error");
        }
        return buffer.getInteger(getDefiniteLength(buffer));
    }

    /**
     * Get a integer from the input stream as a BigInteger object.
     *
     * @return the integer held in this DER input stream.
     */
    public BigInteger getBigInteger() throws IOException {
        if (buffer.read() != DerValue.tag_Integer) {
            throw new IOException("DER input, Integer tag error");
        }
        return buffer.getBigInteger(getDefiniteLength(buffer), false);
    }

    /**
     * Returns an ASN.1 INTEGER value as a positive BigInteger.
     * This is just to deal with implementations that incorrectly encode
     * some values as negative.
     *
     * @return the integer held in this DER value as a BigInteger.
     */
    public BigInteger getPositiveBigInteger() throws IOException {
        if (buffer.read() != DerValue.tag_Integer) {
            throw new IOException("DER input, Integer tag error");
        }
        return buffer.getBigInteger(getDefiniteLength(buffer), true);
    }

    /**
     * Get an enumerated from the input stream.
     *
     * @return the integer held in this DER input stream.
     */
    public int getEnumerated() throws IOException {
        if (buffer.read() != DerValue.tag_Enumerated) {
            throw new IOException("DER input, Enumerated tag error");
        }
        return buffer.getInteger(getDefiniteLength(buffer));
    }

    /**
     * Get a bit string from the input stream. Padded bits (if any)
     * will be stripped off before the bit string is returned.
     */
    public byte[] getBitString() throws IOException {
        if (buffer.read() != DerValue.tag_BitString)
            throw new IOException("DER input not an bit string");

        return buffer.getBitString(getDefiniteLength(buffer));
    }

    /**
     * Get a bit string from the input stream.  The bit string need
     * not be byte-aligned.
     */
    public BitArray getUnalignedBitString() throws IOException {
        if (buffer.read() != DerValue.tag_BitString) {
            throw new IOException("DER input not a bit string");
        }

        int length = getDefiniteLength(buffer);

        if (length == 0) {
            return new BitArray(0);
        }

        /*
         * First byte = number of excess bits in the last octet of the
         * representation.
         */
        length--;
        int validBits = length*8 - buffer.read();
        if (validBits < 0) {
            throw new IOException("valid bits of bit string invalid");
        }

        byte[] repn = new byte[length];

        if ((length != 0) && (buffer.read(repn) != length)) {
            throw new IOException("short read of DER bit string");
        }

        return new BitArray(validBits, repn);
    }

    /**
     * Returns an ASN.1 OCTET STRING from the input stream.
     */
    public byte[] getOctetString() throws IOException {
        if (buffer.read() != DerValue.tag_OctetString)
            throw new IOException("DER input not an octet string");

        int length = getDefiniteLength(buffer);
        byte[] retval = new byte[length];
        if ((length != 0) && (buffer.read(retval) != length))
            throw new IOException("short read of DER octet string");

        return retval;
    }

    /**
     * Returns the asked number of bytes from the input stream.
     */
    public void getBytes(byte[] val) throws IOException {
        if ((val.length != 0) && (buffer.read(val) != val.length)) {
            throw new IOException("short read of DER octet string");
        }
    }

    /**
     * Reads an encoded null value from the input stream.
     */
    public void getNull() throws IOException {
        if (buffer.read() != DerValue.tag_Null || buffer.read() != 0)
            throw new IOException("getNull, bad data");
    }

    /**
     * Reads an X.200 style Object Identifier from the stream.
     */
    public ObjectIdentifier getOID() throws IOException {
        return new ObjectIdentifier(this);
    }

    /**
     * Return a sequence of encoded entities.  ASN.1 sequences are
     * ordered, and they are often used, like a "struct" in C or C++,
     * to group data values.  They may have optional or context
     * specific values.
     *
     * @param startLen guess about how long the sequence will be
     *          (used to initialize an auto-growing data structure)
     * @return array of the values in the sequence
     */
    public DerValue[] getSequence(int startLen) throws IOException {
        tag = (byte)buffer.read();
        if (tag != DerValue.tag_Sequence)
            throw new IOException("Sequence tag error");
        return readVector(startLen);
    }

    /**
     * Return a set of encoded entities.  ASN.1 sets are unordered,
     * though DER may specify an order for some kinds of sets (such
     * as the attributes in an X.500 relative distinguished name)
     * to facilitate binary comparisons of encoded values.
     *
     * @param startLen guess about how large the set will be
     *          (used to initialize an auto-growing data structure)
     * @return array of the values in the sequence
     */
    public DerValue[] getSet(int startLen) throws IOException {
        tag = (byte)buffer.read();
        if (tag != DerValue.tag_Set)
            throw new IOException("Set tag error");
        return readVector(startLen);
    }

    /**
     * Return a set of encoded entities.  ASN.1 sets are unordered,
     * though DER may specify an order for some kinds of sets (such
     * as the attributes in an X.500 relative distinguished name)
     * to facilitate binary comparisons of encoded values.
     *
     * @param startLen guess about how large the set will be
     *          (used to initialize an auto-growing data structure)
     * @param implicit if true tag is assumed implicit.
     * @return array of the values in the sequence
     */
    public DerValue[] getSet(int startLen, boolean implicit)
        throws IOException {
        tag = (byte)buffer.read();
        if (!implicit) {
            if (tag != DerValue.tag_Set) {
                throw new IOException("Set tag error");
            }
        }
        return (readVector(startLen));
    }

    /*
     * Read a "vector" of values ... set or sequence have the
     * same encoding, except for the initial tag, so both use
     * this same helper routine.
     */
    protected DerValue[] readVector(int startLen) throws IOException {
        DerInputStream  newstr;

        byte lenByte = (byte)buffer.read();
        int len = getLength((lenByte & 0xff), buffer);

        if (len == -1) {
           // indefinite length encoding found
           int readLen = buffer.available();
           int offset = 2;     // for tag and length bytes
           byte[] indefData = new byte[readLen + offset];
           indefData[0] = tag;
           indefData[1] = lenByte;
           DataInputStream dis = new DataInputStream(buffer);
           dis.readFully(indefData, offset, readLen);
           dis.close();
           DerIndefLenConverter derIn = new DerIndefLenConverter();
           buffer = new DerInputBuffer(derIn.convert(indefData));
           if (tag != buffer.read())
                throw new IOException("Indefinite length encoding" +
                        " not supported");
           len = DerInputStream.getDefiniteLength(buffer);
        }

        if (len == 0)
            // return empty array instead of null, which should be
            // used only for missing optionals
            return new DerValue[0];

        /*
         * Create a temporary stream from which to read the data,
         * unless it's not really needed.
         */
        if (buffer.available() == len)
            newstr = this;
        else
            newstr = subStream(len, true);

        /*
         * Pull values out of the stream.
         */
        Vector<DerValue> vec = new Vector<DerValue>(startLen);
        DerValue value;

        do {
            value = new DerValue(newstr.buffer);
            vec.addElement(value);
        } while (newstr.available() > 0);

        if (newstr.available() != 0)
            throw new IOException("extra data at end of vector");

        /*
         * Now stick them into the array we're returning.
         */
        int             i, max = vec.size();
        DerValue[]      retval = new DerValue[max];

        for (i = 0; i < max; i++)
            retval[i] = vec.elementAt(i);

        return retval;
    }

    /**
     * Get a single DER-encoded value from the input stream.
     * It can often be useful to pull a value from the stream
     * and defer parsing it.  For example, you can pull a nested
     * sequence out with one call, and only examine its elements
     * later when you really need to.
     */
    public DerValue getDerValue() throws IOException {
        return new DerValue(buffer);
    }

    /**
     * Read a string that was encoded as a UTF8String DER value.
     */
    public String getUTF8String() throws IOException {
        return readString(DerValue.tag_UTF8String, "UTF-8", "UTF8");
    }

    /**
     * Read a string that was encoded as a PrintableString DER value.
     */
    public String getPrintableString() throws IOException {
        return readString(DerValue.tag_PrintableString, "Printable",
                          "ASCII");
    }

    /**
     * Read a string that was encoded as a T61String DER value.
     */
    public String getT61String() throws IOException {
        /*
         * Works for common characters between T61 and ASCII.
         */
        return readString(DerValue.tag_T61String, "T61", "ISO-8859-1");
    }

    /**
     * Read a string that was encoded as a IA5tring DER value.
     */
    public String getIA5String() throws IOException {
        return readString(DerValue.tag_IA5String, "IA5", "ASCII");
    }

    /**
     * Read a string that was encoded as a BMPString DER value.
     */
    public String getBMPString() throws IOException {
        return readString(DerValue.tag_BMPString, "BMP",
                          "UnicodeBigUnmarked");
    }

    /**
     * Read a string that was encoded as a GeneralString DER value.
     */
    public String getGeneralString() throws IOException {
        return readString(DerValue.tag_GeneralString, "General",
                          "ASCII");
    }

    /**
     * Private helper routine to read an encoded string from the input
     * stream.
     * @param stringTag the tag for the type of string to read
     * @param stringName a name to display in error messages
     * @param enc the encoder to use to interpret the data. Should
     * correspond to the stringTag above.
     */
    private String readString(byte stringTag, String stringName,
                              String enc) throws IOException {

        if (buffer.read() != stringTag)
            throw new IOException("DER input not a " +
                                  stringName + " string");

        int length = getDefiniteLength(buffer);
        byte[] retval = new byte[length];
        if ((length != 0) && (buffer.read(retval) != length))
            throw new IOException("short read of DER " +
                                  stringName + " string");

        return new String(retval, enc);
    }

    /**
     * Get a UTC encoded time value from the input stream.
     */
    public Date getUTCTime() throws IOException {
        if (buffer.read() != DerValue.tag_UtcTime)
            throw new IOException("DER input, UTCtime tag invalid ");
        return buffer.getUTCTime(getDefiniteLength(buffer));
    }

    /**
     * Get a Generalized encoded time value from the input stream.
     */
    public Date getGeneralizedTime() throws IOException {
        if (buffer.read() != DerValue.tag_GeneralizedTime)
            throw new IOException("DER input, GeneralizedTime tag invalid ");
        return buffer.getGeneralizedTime(getDefiniteLength(buffer));
    }

    /*
     * Get a byte from the input stream.
     */
    // package private
    int getByte() throws IOException {
        return (0x00ff & buffer.read());
    }

    public int peekByte() throws IOException {
        return buffer.peek();
    }

    // package private
    int getLength() throws IOException {
        return getLength(buffer);
    }

    /*
     * Get a length from the input stream, allowing for at most 32 bits of
     * encoding to be used.  (Not the same as getting a tagged integer!)
     *
     * @return the length or -1 if indefinite length found.
     * @exception IOException on parsing error or unsupported lengths.
     */
    static int getLength(InputStream in) throws IOException {
        return getLength(in.read(), in);
    }

    /*
     * Get a length from the input stream, allowing for at most 32 bits of
     * encoding to be used.  (Not the same as getting a tagged integer!)
     *
     * @return the length or -1 if indefinite length found.
     * @exception IOException on parsing error or unsupported lengths.
     */
    static int getLength(int lenByte, InputStream in) throws IOException {
        int value, tmp;

        tmp = lenByte;
        if ((tmp & 0x080) == 0x00) { // short form, 1 byte datum
            value = tmp;
        } else {                     // long form or indefinite
            tmp &= 0x07f;

            /*
             * NOTE:  tmp == 0 indicates indefinite length encoded data.
             * tmp > 4 indicates more than 4Gb of data.
             */
            if (tmp == 0)
                return -1;
            if (tmp < 0 || tmp > 4)
                throw new IOException("DerInputStream.getLength(): lengthTag="
                    + tmp + ", "
                    + ((tmp < 0) ? "incorrect DER encoding." : "too big."));

            for (value = 0; tmp > 0; tmp --) {
                value <<= 8;
                value += 0x0ff & in.read();
            }
            if (value < 0) {
                throw new IOException("DerInputStream.getLength(): "
                        + "Invalid length bytes");
            }
        }
        return value;
    }

    int getDefiniteLength() throws IOException {
        return getDefiniteLength(buffer);
    }

    /*
     * Get a length from the input stream.
     *
     * @return the length
     * @exception IOException on parsing error or if indefinite length found.
     */
    static int getDefiniteLength(InputStream in) throws IOException {
        int len = getLength(in);
        if (len < 0) {
            throw new IOException("Indefinite length encoding not supported");
        }
        return len;
    }

    /**
     * Mark the current position in the buffer, so that
     * a later call to <code>reset</code> will return here.
     */
    public void mark(int value) { buffer.mark(value); }


    /**
     * Return to the position of the last <code>mark</code>
     * call.  A mark is implicitly set at the beginning of
     * the stream when it is created.
     */
    public void reset() { buffer.reset(); }


    /**
     * Returns the number of bytes available for reading.
     * This is most useful for testing whether the stream is
     * empty.
     */
    public int available() { return buffer.available(); }
}
