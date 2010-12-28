/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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

package javax.imageio.stream;

import java.io.Closeable;
import java.io.DataInput;
import java.io.IOException;
import java.nio.ByteOrder;

/**
 * A seekable input stream interface for use by
 * <code>ImageReader</code>s.  Various input sources, such as
 * <code>InputStream</code>s and <code>File</code>s,
 * as well as future fast I/O sources may be "wrapped" by a suitable
 * implementation of this interface for use by the Image I/O API.
 *
 * @see ImageInputStreamImpl
 * @see FileImageInputStream
 * @see FileCacheImageInputStream
 * @see MemoryCacheImageInputStream
 *
 */
public interface ImageInputStream extends DataInput, Closeable {

    /**
     * Sets the desired byte order for future reads of data values
     * from this stream.  For example, the sequence of bytes '0x01
     * 0x02 0x03 0x04' if read as a 4-byte integer would have the
     * value '0x01020304' using network byte order and the value
     * '0x04030201' under the reverse byte order.
     *
     * <p> The enumeration class <code>java.nio.ByteOrder</code> is
     * used to specify the byte order.  A value of
     * <code>ByteOrder.BIG_ENDIAN</code> specifies so-called
     * big-endian or network byte order, in which the high-order byte
     * comes first.  Motorola and Sparc processors store data in this
     * format, while Intel processors store data in the reverse
     * <code>ByteOrder.LITTLE_ENDIAN</code> order.
     *
     * <p> The byte order has no effect on the results returned from
     * the <code>readBits</code> method (or the value written by
     * <code>ImageOutputStream.writeBits</code>).
     *
     * @param byteOrder one of <code>ByteOrder.BIG_ENDIAN</code> or
     * <code>java.nio.ByteOrder.LITTLE_ENDIAN</code>, indicating whether
     * network byte order or its reverse will be used for future
     * reads.
     *
     * @see java.nio.ByteOrder
     * @see #getByteOrder
     * @see #readBits(int)
     */
    void setByteOrder(ByteOrder byteOrder);

    /**
     * Returns the byte order with which data values will be read from
     * this stream as an instance of the
     * <code>java.nio.ByteOrder</code> enumeration.
     *
     * @return one of <code>ByteOrder.BIG_ENDIAN</code> or
     * <code>ByteOrder.LITTLE_ENDIAN</code>, indicating which byte
     * order is being used.
     *
     * @see java.nio.ByteOrder
     * @see #setByteOrder
     */
    ByteOrder getByteOrder();

    /**
     * Reads a single byte from the stream and returns it as an
     * integer between 0 and 255.  If the end of the stream is
     * reached, -1 is returned.
     *
     * <p> The bit offset within the stream is reset to zero before
     * the read occurs.
     *
     * @return a byte value from the stream, as an int, or -1 to
     * indicate EOF.
     *
     * @exception IOException if an I/O error occurs.
     */
    int read() throws IOException;

    /**
     * Reads up to <code>b.length</code> bytes from the stream, and
     * stores them into <code>b</code> starting at index 0.  The
     * number of bytes read is returned.  If no bytes can be read
     * because the end of the stream has been reached, -1 is returned.
     *
     * <p> The bit offset within the stream is reset to zero before
     * the read occurs.
     *
     * @param b an array of bytes to be written to.
     *
     * @return the number of bytes actually read, or <code>-1</code>
     * to indicate EOF.
     *
     * @exception NullPointerException if <code>b</code> is
     * <code>null</code>.
     *
     * @exception IOException if an I/O error occurs.
     */
    int read(byte[] b) throws IOException;

    /**
     * Reads up to <code>len</code> bytes from the stream, and stores
     * them into <code>b</code> starting at index <code>off</code>.
     * The number of bytes read is returned.  If no bytes can be read
     * because the end of the stream has been reached, <code>-1</code>
     * is returned.
     *
     * <p> The bit offset within the stream is reset to zero before
     * the read occurs.
     *
     * @param b an array of bytes to be written to.
     * @param off the starting position within <code>b</code> to write to.
     * @param len the maximum number of <code>byte</code>s to read.
     *
     * @return the number of bytes actually read, or <code>-1</code>
     * to indicate EOF.
     *
     * @exception NullPointerException if <code>b</code> is
     * <code>null</code>.
     * @exception IndexOutOfBoundsException if <code>off</code> is
     * negative, <code>len</code> is negative, or <code>off +
     * len</code> is greater than <code>b.length</code>.
     * @exception IOException if an I/O error occurs.
     */
    int read(byte[] b, int off, int len) throws IOException;

    /**
     * Reads up to <code>len</code> bytes from the stream, and
     * modifies the supplied <code>IIOByteBuffer</code> to indicate
     * the byte array, offset, and length where the data may be found.
     * The caller should not attempt to modify the data found in the
     * <code>IIOByteBuffer</code>.
     *
     * <p> The bit offset within the stream is reset to zero before
     * the read occurs.
     *
     * @param buf an IIOByteBuffer object to be modified.
     * @param len the maximum number of <code>byte</code>s to read.
     *
     * @exception IndexOutOfBoundsException if <code>len</code> is
     * negative.
     * @exception NullPointerException if <code>buf</code> is
     * <code>null</code>.
     *
     * @exception IOException if an I/O error occurs.
     */
    void readBytes(IIOByteBuffer buf, int len) throws IOException;

    /**
     * Reads a byte from the stream and returns a <code>boolean</code>
     * value of <code>true</code> if it is nonzero, <code>false</code>
     * if it is zero.
     *
     * <p> The bit offset within the stream is reset to zero before
     * the read occurs.
     *
     * @return a boolean value from the stream.
     *
     * @exception EOFException if the end of the stream is reached.
     * @exception IOException if an I/O error occurs.
     */
    boolean readBoolean() throws IOException;

    /**
     * Reads a byte from the stream and returns it as a
     * <code>byte</code> value.  Byte values between <code>0x00</code>
     * and <code>0x7f</code> represent integer values between
     * <code>0</code> and <code>127</code>.  Values between
     * <code>0x80</code> and <code>0xff</code> represent negative
     * values from <code>-128</code> to <code>/1</code>.
     *
     * <p> The bit offset within the stream is reset to zero before
     * the read occurs.
     *
     * @return a signed byte value from the stream.
     *
     * @exception EOFException if the end of the stream is reached.
     * @exception IOException if an I/O error occurs.
     */
    byte readByte() throws IOException;

    /**
     * Reads a byte from the stream, and (conceptually) converts it to
     * an int, masks it with <code>0xff</code> in order to strip off
     * any sign-extension bits, and returns it as a <code>byte</code>
     * value.
     *
     * <p> Thus, byte values between <code>0x00</code> and
     * <code>0x7f</code> are simply returned as integer values between
     * <code>0</code> and <code>127</code>.  Values between
     * <code>0x80</code> and <code>0xff</code>, which normally
     * represent negative <code>byte</code>values, will be mapped into
     * positive integers between <code>128</code> and
     * <code>255</code>.
     *
     * <p> The bit offset within the stream is reset to zero before
     * the read occurs.
     *
     * @return an unsigned byte value from the stream.
     *
     * @exception EOFException if the end of the stream is reached.
     * @exception IOException if an I/O error occurs.
     */
    int readUnsignedByte() throws IOException;

    /**
     * Reads two bytes from the stream, and (conceptually)
     * concatenates them according to the current byte order, and
     * returns the result as a <code>short</code> value.
     *
     * <p> The bit offset within the stream is reset to zero before
     * the read occurs.
     *
     * @return a signed short value from the stream.
     *
     * @exception EOFException if the stream reaches the end before
     * reading all the bytes.
     * @exception IOException if an I/O error occurs.
     *
     * @see #getByteOrder
     */
    short readShort() throws IOException;

    /**
     * Reads two bytes from the stream, and (conceptually)
     * concatenates them according to the current byte order, converts
     * the resulting value to an <code>int</code>, masks it with
     * <code>0xffff</code> in order to strip off any sign-extension
     * buts, and returns the result as an unsigned <code>int</code>
     * value.
     *
     * <p> The bit offset within the stream is reset to zero before
     * the read occurs.
     *
     * @return an unsigned short value from the stream, as an int.
     *
     * @exception EOFException if the stream reaches the end before
     * reading all the bytes.
     * @exception IOException if an I/O error occurs.
     *
     * @see #getByteOrder
     */
    int readUnsignedShort() throws IOException;

    /**
     * Equivalent to <code>readUnsignedShort</code>, except that the
     * result is returned using the <code>char</code> datatype.
     *
     * <p> The bit offset within the stream is reset to zero before
     * the read occurs.
     *
     * @return an unsigned char value from the stream.
     *
     * @exception EOFException if the stream reaches the end before
     * reading all the bytes.
     * @exception IOException if an I/O error occurs.
     *
     * @see #readUnsignedShort
     */
    char readChar() throws IOException;

    /**
     * Reads 4 bytes from the stream, and (conceptually) concatenates
     * them according to the current byte order and returns the result
     * as an <code>int</code>.
     *
     * <p> The bit offset within the stream is ignored and treated as
     * though it were zero.
     *
     * @return a signed int value from the stream.
     *
     * @exception EOFException if the stream reaches the end before
     * reading all the bytes.
     * @exception IOException if an I/O error occurs.
     *
     * @see #getByteOrder
     */
    int readInt() throws IOException;

    /**
     * Reads 4 bytes from the stream, and (conceptually) concatenates
     * them according to the current byte order, converts the result
     * to a long, masks it with <code>0xffffffffL</code> in order to
     * strip off any sign-extension bits, and returns the result as an
     * unsigned <code>long</code> value.
     *
     * <p> The bit offset within the stream is reset to zero before
     * the read occurs.
     *
     * @return an unsigned int value from the stream, as a long.
     *
     * @exception EOFException if the stream reaches the end before
     * reading all the bytes.
     * @exception IOException if an I/O error occurs.
     *
     * @see #getByteOrder
     */
    long readUnsignedInt() throws IOException;

    /**
     * Reads 8 bytes from the stream, and (conceptually) concatenates
     * them according to the current byte order and returns the result
     * as a <code>long</code>.
     *
     * <p> The bit offset within the stream is reset to zero before
     * the read occurs.
     *
     * @return a signed long value from the stream.
     *
     * @exception EOFException if the stream reaches the end before
     * reading all the bytes.
     * @exception IOException if an I/O error occurs.
     *
     * @see #getByteOrder
     */
    long readLong() throws IOException;

    /**
     * Reads 4 bytes from the stream, and (conceptually) concatenates
     * them according to the current byte order and returns the result
     * as a <code>float</code>.
     *
     * <p> The bit offset within the stream is reset to zero before
     * the read occurs.
     *
     * @return a float value from the stream.
     *
     * @exception EOFException if the stream reaches the end before
     * reading all the bytes.
     * @exception IOException if an I/O error occurs.
     *
     * @see #getByteOrder
     */
    float readFloat() throws IOException;

    /**
     * Reads 8 bytes from the stream, and (conceptually) concatenates
     * them according to the current byte order and returns the result
     * as a <code>double</code>.
     *
     * <p> The bit offset within the stream is reset to zero before
     * the read occurs.
     *
     * @return a double value from the stream.
     *
     * @exception EOFException if the stream reaches the end before
     * reading all the bytes.
     * @exception IOException if an I/O error occurs.
     *
     * @see #getByteOrder
     */
    double readDouble() throws IOException;

    /**
     * Reads the next line of text from the input stream.  It reads
     * successive bytes, converting each byte separately into a
     * character, until it encounters a line terminator or end of
     * file; the characters read are then returned as a
     * <code>String</code>. Note that because this method processes
     * bytes, it does not support input of the full Unicode character
     * set.
     *
     * <p> If end of file is encountered before even one byte can be
     * read, then <code>null</code> is returned. Otherwise, each byte
     * that is read is converted to type <code>char</code> by
     * zero-extension. If the character <code>'\n'</code> is
     * encountered, it is discarded and reading ceases. If the
     * character <code>'\r'</code> is encountered, it is discarded
     * and, if the following byte converts &#32;to the character
     * <code>'\n'</code>, then that is discarded also; reading then
     * ceases. If end of file is encountered before either of the
     * characters <code>'\n'</code> and <code>'\r'</code> is
     * encountered, reading ceases. Once reading has ceased, a
     * <code>String</code> is returned that contains all the
     * characters read and not discarded, taken in order.  Note that
     * every character in this string will have a value less than
     * <code>&#92;u0100</code>, that is, <code>(char)256</code>.
     *
     * <p> The bit offset within the stream is reset to zero before
     * the read occurs.
     *
     * @return a String containing a line of text from the stream.
     *
     * @exception IOException if an I/O error occurs.
     */
    String readLine() throws IOException;

    /**
     * Reads in a string that has been encoded using a
     * <a href="../../../java/io/DataInput.html#modified-utf-8">modified
     * UTF-8</a>
     * format.  The general contract of <code>readUTF</code> is that
     * it reads a representation of a Unicode character string encoded
     * in modified UTF-8 format; this string of characters is
     * then returned as a <code>String</code>.
     *
     * <p> First, two bytes are read and used to construct an unsigned
     * 16-bit integer in the manner of the
     * <code>readUnsignedShort</code> method, using network byte order
     * (regardless of the current byte order setting). This integer
     * value is called the <i>UTF length</i> and specifies the number
     * of additional bytes to be read. These bytes are then converted
     * to characters by considering them in groups. The length of each
     * group is computed from the value of the first byte of the
     * group. The byte following a group, if any, is the first byte of
     * the next group.
     *
     * <p> If the first byte of a group matches the bit pattern
     * <code>0xxxxxxx</code> (where <code>x</code> means "may be
     * <code>0</code> or <code>1</code>"), then the group consists of
     * just that byte. The byte is zero-extended to form a character.
     *
     * <p> If the first byte of a group matches the bit pattern
     * <code>110xxxxx</code>, then the group consists of that byte
     * <code>a</code> and a second byte <code>b</code>. If there is no
     * byte <code>b</code> (because byte <code>a</code> was the last
     * of the bytes to be read), or if byte <code>b</code> does not
     * match the bit pattern <code>10xxxxxx</code>, then a
     * <code>UTFDataFormatException</code> is thrown. Otherwise, the
     * group is converted to the character:
     *
     * <p> <pre><code>
     * (char)(((a&amp; 0x1F) &lt;&lt; 6) | (b &amp; 0x3F))
     * </code></pre>
     *
     * If the first byte of a group matches the bit pattern
     * <code>1110xxxx</code>, then the group consists of that byte
     * <code>a</code> and two more bytes <code>b</code> and
     * <code>c</code>.  If there is no byte <code>c</code> (because
     * byte <code>a</code> was one of the last two of the bytes to be
     * read), or either byte <code>b</code> or byte <code>c</code>
     * does not match the bit pattern <code>10xxxxxx</code>, then a
     * <code>UTFDataFormatException</code> is thrown. Otherwise, the
     * group is converted to the character:
     *
     * <p> <pre><code>
     * (char)(((a &amp; 0x0F) &lt;&lt; 12) | ((b &amp; 0x3F) &lt;&lt; 6) | (c &amp; 0x3F))
     * </code></pre>
     *
     * If the first byte of a group matches the pattern
     * <code>1111xxxx</code> or the pattern <code>10xxxxxx</code>,
     * then a <code>UTFDataFormatException</code> is thrown.
     *
     * <p> If end of file is encountered at any time during this
     * entire process, then an <code>EOFException</code> is thrown.
     *
     * <p> After every group has been converted to a character by this
     * process, the characters are gathered, in the same order in
     * which their corresponding groups were read from the input
     * stream, to form a <code>String</code>, which is returned.
     *
     * <p> The current byte order setting is ignored.
     *
     * <p> The bit offset within the stream is reset to zero before
     * the read occurs.
     *
     * <p><strong>Note:</strong> This method should not be used in
     * the  implementation of image formats that use standard UTF-8,
     * because  the modified UTF-8 used here is incompatible with
     * standard UTF-8.
     *
     * @return a String read from the stream.
     *
     * @exception  EOFException  if this stream reaches the end
     * before reading all the bytes.
     * @exception  UTFDataFormatException if the bytes do not represent a
     * valid modified UTF-8 encoding of a string.
     * @exception IOException if an I/O error occurs.
     */
    String readUTF() throws IOException;

    /**
     * Reads <code>len</code> bytes from the stream, and stores them
     * into <code>b</code> starting at index <code>off</code>.
     * If the end of the stream is reached, an <code>EOFException</code>
     * will be thrown.
     *
     * <p> The bit offset within the stream is reset to zero before
     * the read occurs.
     *
     * @param b an array of bytes to be written to.
     * @param off the starting position within <code>b</code> to write to.
     * @param len the maximum number of <code>byte</code>s to read.
     *
     * @exception IndexOutOfBoundsException if <code>off</code> is
     * negative, <code>len</code> is negative, or <code>off +
     * len</code> is greater than <code>b.length</code>.
     * @exception NullPointerException if <code>b</code> is
     * <code>null</code>.
     * @exception EOFException if the stream reaches the end before
     * reading all the bytes.
     * @exception IOException if an I/O error occurs.
     */
    void readFully(byte[] b, int off, int len) throws IOException;

    /**
     * Reads <code>b.length</code> bytes from the stream, and stores them
     * into <code>b</code> starting at index <code>0</code>.
     * If the end of the stream is reached, an <code>EOFException</code>
     * will be thrown.
     *
     * <p> The bit offset within the stream is reset to zero before
     * the read occurs.
     *
     * @param b an array of <code>byte</code>s.
     *
     * @exception NullPointerException if <code>b</code> is
     * <code>null</code>.
     * @exception EOFException if the stream reaches the end before
     * reading all the bytes.
     * @exception IOException if an I/O error occurs.
     */
    void readFully(byte[] b) throws IOException;

    /**
     * Reads <code>len</code> shorts (signed 16-bit integers) from the
     * stream according to the current byte order, and
     * stores them into <code>s</code> starting at index
     * <code>off</code>.  If the end of the stream is reached, an
     * <code>EOFException</code> will be thrown.
     *
     * <p> The bit offset within the stream is reset to zero before
     * the read occurs.
     *
     * @param s an array of shorts to be written to.
     * @param off the starting position withinb to write to.
     * @param len the maximum number of <code>short</code>s to read.
     *
     * @exception IndexOutOfBoundsException if <code>off</code> is
     * negative, <code>len</code> is negative, or <code>off +
     * len</code> is greater than <code>s.length</code>.
     * @exception NullPointerException if <code>s</code> is
     * <code>null</code>.
     * @exception EOFException if the stream reaches the end before
     * reading all the bytes.
     * @exception IOException if an I/O error occurs.
     */
    void readFully(short[] s, int off, int len) throws IOException;

    /**
     * Reads <code>len</code> chars (unsigned 16-bit integers) from the
     * stream according to the current byte order, and
     * stores them into <code>c</code> starting at index
     * <code>off</code>.  If the end of the stream is reached, an
     * <code>EOFException</code> will be thrown.
     *
     * <p> The bit offset within the stream is reset to zero before
     * the read occurs.
     *
     * @param c an array of chars to be written to.
     * @param off the starting position withinb to write to.
     * @param len the maximum number of <code>char</code>s to read.
     *
     * @exception IndexOutOfBoundsException if <code>off</code> is
     * negative, <code>len</code> is negative, or <code>off +
     * len</code> is greater than <code>c.length</code>.
     * @exception NullPointerException if <code>c</code> is
     * <code>null</code>.
     * @exception EOFException if the stream reaches the end before
     * reading all the bytes.
     * @exception IOException if an I/O error occurs.
     */
    void readFully(char[] c, int off, int len) throws IOException;

    /**
     * Reads <code>len</code> ints (signed 32-bit integers) from the
     * stream according to the current byte order, and
     * stores them into <code>i</code> starting at index
     * <code>off</code>.  If the end of the stream is reached, an
     * <code>EOFException</code> will be thrown.
     *
     * <p> The bit offset within the stream is reset to zero before
     * the read occurs.
     *
     * @param i an array of ints to be written to.
     * @param off the starting position withinb to write to.
     * @param len the maximum number of <code>int</code>s to read.
     *
     * @exception IndexOutOfBoundsException if <code>off</code> is
     * negative, <code>len</code> is negative, or <code>off +
     * len</code> is greater than <code>i.length</code>.
     * @exception NullPointerException if <code>i</code> is
     * <code>null</code>.
     * @exception EOFException if the stream reaches the end before
     * reading all the bytes.
     * @exception IOException if an I/O error occurs.
     */
    void readFully(int[] i, int off, int len) throws IOException;

    /**
     * Reads <code>len</code> longs (signed 64-bit integers) from the
     * stream according to the current byte order, and
     * stores them into <code>l</code> starting at index
     * <code>off</code>.  If the end of the stream is reached, an
     * <code>EOFException</code> will be thrown.
     *
     * <p> The bit offset within the stream is reset to zero before
     * the read occurs.
     *
     * @param l an array of longs to be written to.
     * @param off the starting position withinb to write to.
     * @param len the maximum number of <code>long</code>s to read.
     *
     * @exception IndexOutOfBoundsException if <code>off</code> is
     * negative, <code>len</code> is negative, or <code>off +
     * len</code> is greater than <code>l.length</code>.
     * @exception NullPointerException if <code>l</code> is
     * <code>null</code>.
     * @exception EOFException if the stream reaches the end before
     * reading all the bytes.
     * @exception IOException if an I/O error occurs.
     */
    void readFully(long[] l, int off, int len) throws IOException;

    /**
     * Reads <code>len</code> floats (32-bit IEEE single-precision
     * floats) from the stream according to the current byte order,
     * and stores them into <code>f</code> starting at
     * index <code>off</code>.  If the end of the stream is reached,
     * an <code>EOFException</code> will be thrown.
     *
     * <p> The bit offset within the stream is reset to zero before
     * the read occurs.
     *
     * @param f an array of floats to be written to.
     * @param off the starting position withinb to write to.
     * @param len the maximum number of <code>float</code>s to read.
     *
     * @exception IndexOutOfBoundsException if <code>off</code> is
     * negative, <code>len</code> is negative, or <code>off +
     * len</code> is greater than <code>f.length</code>.
     * @exception NullPointerException if <code>f</code> is
     * <code>null</code>.
     * @exception EOFException if the stream reaches the end before
     * reading all the bytes.
     * @exception IOException if an I/O error occurs.
     */
    void readFully(float[] f, int off, int len) throws IOException;

    /**
     * Reads <code>len</code> doubles (64-bit IEEE double-precision
     * floats) from the stream according to the current byte order,
     * and stores them into <code>d</code> starting at
     * index <code>off</code>.  If the end of the stream is reached,
     * an <code>EOFException</code> will be thrown.
     *
     * <p> The bit offset within the stream is reset to zero before
     * the read occurs.
     *
     * @param d an array of doubles to be written to.
     * @param off the starting position withinb to write to.
     * @param len the maximum number of <code>double</code>s to read.
     *
     * @exception IndexOutOfBoundsException if <code>off</code> is
     * negative, <code>len</code> is negative, or <code>off +
     * len</code> is greater than <code>d.length</code>.
     * @exception NullPointerException if <code>d</code> is
     * <code>null</code>.
     * @exception EOFException if the stream reaches the end before
     * reading all the bytes.
     * @exception IOException if an I/O error occurs.
     */
    void readFully(double[] d, int off, int len) throws IOException;

    /**
     * Returns the current byte position of the stream.  The next read
     * will take place starting at this offset.
     *
     * @return a long containing the position of the stream.
     *
     * @exception IOException if an I/O error occurs.
     */
    long getStreamPosition() throws IOException;

    /**
     * Returns the current bit offset, as an integer between 0 and 7,
     * inclusive.  The bit offset is updated implicitly by calls to
     * the <code>readBits</code> method.  A value of 0 indicates the
     * most-significant bit, and a value of 7 indicates the least
     * significant bit, of the byte being read.
     *
     * <p> The bit offset is set to 0 when a stream is first
     * opened, and is reset to 0 by calls to <code>seek</code>,
     * <code>skipBytes</code>, or any <code>read</code> or
     * <code>readFully</code> method.
     *
     * @return an <code>int</code> containing the bit offset between
     * 0 and 7, inclusive.
     *
     * @exception IOException if an I/O error occurs.
     *
     * @see #setBitOffset
     */
    int getBitOffset() throws IOException;

    /**
     * Sets the bit offset to an integer between 0 and 7, inclusive.
     * The byte offset within the stream, as returned by
     * <code>getStreamPosition</code>, is left unchanged.
     * A value of 0 indicates the
     * most-significant bit, and a value of 7 indicates the least
     * significant bit, of the byte being read.
     *
     * @param bitOffset the desired offset, as an <code>int</code>
     * between 0 and 7, inclusive.
     *
     * @exception IllegalArgumentException if <code>bitOffset</code>
     * is not between 0 and 7, inclusive.
     * @exception IOException if an I/O error occurs.
     *
     * @see #getBitOffset
     */
    void setBitOffset(int bitOffset) throws IOException;

    /**
     * Reads a single bit from the stream and returns it as an
     * <code>int</code> with the value <code>0</code> or
     * <code>1</code>.  The bit offset is advanced by one and reduced
     * modulo 8.
     *
     * @return an <code>int</code> containing the value <code>0</code>
     * or <code>1</code>.
     *
     * @exception EOFException if the stream reaches the end before
     * reading all the bits.
     * @exception IOException if an I/O error occurs.
     */
    int readBit() throws IOException;

    /**
     * Reads a bitstring from the stream and returns it as a
     * <code>long</code>, with the first bit read becoming the most
     * significant bit of the output.  The read starts within the byte
     * indicated by <code>getStreamPosition</code>, at the bit given
     * by <code>getBitOffset</code>.  The bit offset is advanced by
     * <code>numBits</code> and reduced modulo 8.
     *
     * <p> The byte order of the stream has no effect on this
     * method.  The return value of this method is constructed as
     * though the bits were read one at a time, and shifted into
     * the right side of the return value, as shown by the following
     * pseudo-code:
     *
     * <pre>
     * long accum = 0L;
     * for (int i = 0; i < numBits; i++) {
     *   accum <<= 1; // Shift left one bit to make room
     *   accum |= readBit();
     * }
     * </pre>
     *
     * Note that the result of <code>readBits(32)</code> may thus not
     * be equal to that of <code>readInt()</code> if a reverse network
     * byte order is being used (i.e., <code>getByteOrder() ==
     * false</code>).
     *
     * <p> If the end of the stream is encountered before all the bits
     * have been read, an <code>EOFException</code> is thrown.
     *
     * @param numBits the number of bits to read, as an <code>int</code>
     * between 0 and 64, inclusive.
     * @return the bitstring, as a <code>long</code> with the last bit
     * read stored in the least significant bit.
     *
     * @exception IllegalArgumentException if <code>numBits</code>
     * is not between 0 and 64, inclusive.
     * @exception EOFException if the stream reaches the end before
     * reading all the bits.
     * @exception IOException if an I/O error occurs.
     */
    long readBits(int numBits) throws IOException;

    /**
     * Returns the total length of the stream, if known.  Otherwise,
     * <code>-1</code> is returned.
     *
     * @return a <code>long</code> containing the length of the
     * stream, if known, or else <code>-1</code>.
     *
     * @exception IOException if an I/O error occurs.
     */
    long length() throws IOException;

    /**
     * Moves the stream position forward by a given number of bytes.  It
     * is possible that this method will only be able to skip forward
     * by a smaller number of bytes than requested, for example if the
     * end of the stream is reached.  In all cases, the actual number
     * of bytes skipped is returned.  The bit offset is set to zero
     * prior to advancing the position.
     *
     * @param n an <code>int</code> containing the number of bytes to
     * be skipped.
     *
     * @return an <code>int</code> representing the number of bytes skipped.
     *
     * @exception IOException if an I/O error occurs.
     */
    int skipBytes(int n) throws IOException;

    /**
     * Moves the stream position forward by a given number of bytes.
     * This method is identical to <code>skipBytes(int)</code> except
     * that it allows for a larger skip distance.
     *
     * @param n a <code>long</code> containing the number of bytes to
     * be skipped.
     *
     * @return a <code>long</code> representing the number of bytes
     * skipped.
     *
     * @exception IOException if an I/O error occurs.
     */
    long skipBytes(long n) throws IOException;

    /**
     * Sets the current stream position to the desired location.  The
     * next read will occur at this location.  The bit offset is set
     * to 0.
     *
     * <p> An <code>IndexOutOfBoundsException</code> will be thrown if
     * <code>pos</code> is smaller than the flushed position (as
     * returned by <code>getflushedPosition</code>).
     *
     * <p> It is legal to seek past the end of the file; an
     * <code>EOFException</code> will be thrown only if a read is
     * performed.
     *
     * @param pos a <code>long</code> containing the desired file
     * pointer position.
     *
     * @exception IndexOutOfBoundsException if <code>pos</code> is smaller
     * than the flushed position.
     * @exception IOException if any other I/O error occurs.
     */
    void seek(long pos) throws IOException;

    /**
     * Marks a position in the stream to be returned to by a
     * subsequent call to <code>reset</code>.  Unlike a standard
     * <code>InputStream</code>, all <code>ImageInputStream</code>s
     * support marking.  Additionally, calls to <code>mark</code> and
     * <code>reset</code> may be nested arbitrarily.
     *
     * <p> Unlike the <code>mark</code> methods declared by the
     * <code>Reader</code> and <code>InputStream</code> interfaces, no
     * <code>readLimit</code> parameter is used.  An arbitrary amount
     * of data may be read following the call to <code>mark</code>.
     *
     * <p> The bit position used by the <code>readBits</code> method
     * is saved and restored by each pair of calls to
     * <code>mark</code> and <code>reset</code>.
     *
     * <p> Note that it is valid for an <code>ImageReader</code> to call
     * <code>flushBefore</code> as part of a read operation.
     * Therefore, if an application calls <code>mark</code> prior to
     * passing that stream to an <code>ImageReader</code>, the application
     * should not assume that the marked position will remain valid after
     * the read operation has completed.
     */
    void mark();

    /**
     * Returns the stream pointer to its previous position, including
     * the bit offset, at the time of the most recent unmatched call
     * to <code>mark</code>.
     *
     * <p> Calls to <code>reset</code> without a corresponding call
     * to <code>mark</code> have no effect.
     *
     * <p> An <code>IOException</code> will be thrown if the previous
     * marked position lies in the discarded portion of the stream.
     *
     * @exception IOException if an I/O error occurs.
     */
    void reset() throws IOException;

    /**
     * Discards the initial portion of the stream prior to the
     * indicated postion.  Attempting to seek to an offset within the
     * flushed portion of the stream will result in an
     * <code>IndexOutOfBoundsException</code>.
     *
     * <p> Calling <code>flushBefore</code> may allow classes
     * implementing this interface to free up resources such as memory
     * or disk space that are being used to store data from the
     * stream.
     *
     * @param pos a <code>long</code> containing the length of the
     * stream prefix that may be flushed.
     *
     * @exception IndexOutOfBoundsException if <code>pos</code> lies
     * in the flushed portion of the stream or past the current stream
     * position.
     * @exception IOException if an I/O error occurs.
     */
    void flushBefore(long pos) throws IOException;

    /**
     * Discards the initial position of the stream prior to the current
     * stream position.  Equivalent to
     * <code>flushBefore(getStreamPosition())</code>.
     *
     * @exception IOException if an I/O error occurs.
     */
    void flush() throws IOException;

    /**
     * Returns the earliest position in the stream to which seeking
     * may be performed.  The returned value will be the maximum of
     * all values passed into previous calls to
     * <code>flushBefore</code>.
     *
     * @return the earliest legal position for seeking, as a
     * <code>long</code>.
     */
    long getFlushedPosition();

    /**
     * Returns <code>true</code> if this <code>ImageInputStream</code>
     * caches data itself in order to allow seeking backwards.
     * Applications may consult this in order to decide how frequently,
     * or whether, to flush in order to conserve cache resources.
     *
     * @return <code>true</code> if this <code>ImageInputStream</code>
     * caches data.
     *
     * @see #isCachedMemory
     * @see #isCachedFile
     */
    boolean isCached();

    /**
     * Returns <code>true</code> if this <code>ImageInputStream</code>
     * caches data itself in order to allow seeking backwards, and
     * the cache is kept in main memory.  Applications may consult
     * this in order to decide how frequently, or whether, to flush
     * in order to conserve cache resources.
     *
     * @return <code>true</code> if this <code>ImageInputStream</code>
     * caches data in main memory.
     *
     * @see #isCached
     * @see #isCachedFile
     */
    boolean isCachedMemory();

    /**
     * Returns <code>true</code> if this <code>ImageInputStream</code>
     * caches data itself in order to allow seeking backwards, and
     * the cache is kept in a temporary file.  Applications may consult
     * this in order to decide how frequently, or whether, to flush
     * in order to conserve cache resources.
     *
     * @return <code>true</code> if this <code>ImageInputStream</code>
     * caches data in a temporary file.
     *
     * @see #isCached
     * @see #isCachedMemory
     */
    boolean isCachedFile();

    /**
     * Closes the stream.  Attempts to access a stream that has been
     * closed may result in <code>IOException</code>s or incorrect
     * behavior.  Calling this method may allow classes implementing
     * this interface to release resources associated with the stream
     * such as memory, disk space, or file descriptors.
     *
     * @exception IOException if an I/O error occurs.
     */
    void close() throws IOException;
}
