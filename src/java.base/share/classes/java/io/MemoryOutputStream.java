/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

package java.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * This class implements an output stream in which the data is written into a
 * series of byte arrays ("segments"). Data can be accessed by reconstructing a
 * unified {@code byte[]} via {@code toByteArray}, or efficiently writing the
 * segments to another {@code OutputStream} via {@code writeTo}. {@code writeTo}
 * is preferred for large payloads because no further object allocation is
 * required.
 * <p>
 * This class is interchangeable with {@code ByteArrayOutputStream}, with the
 * following exceptions:
 * <ol>
 * <li>Methods are not synchronized, so the class is not thread-safe.</li>
 * <li>The exposed fields from {@code ByteArrayOutputStream} are invalid. Reads
 * will return 0 or null, and writes will have no effect.</li>
 * </ol>
 * <p>
 * In exchange for these incompatibilities, performance is significantly
 * improved, especially when performing large numbers of primitive writes or
 * working with large payloads. Gains are achieved by eliminating
 * synchronization, reduced object allocation, smaller {@code byte[]}, and fewer
 * array copies.
 * <p>
 * Closing a {@code MemoryOutputStream} has no effect. The methods in this class
 * can be called after the stream has been closed without generating an
 * {@code IOException}.
 *
 * @since 29
 * @author John Engebretson
 */

public final class MemoryOutputStream extends ByteArrayOutputStream {

    /**
     * Reusable zero-length array. Used where necessary to avoid recreating a
     * byte[0].
     */
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    /**
     * The absolute minimum buffer size, silently overriding the constructor
     * argument when the argument is functionally acceptable but unreasonably small.
     * Values less than ENFORCED_MINIMUM_SEGMENT_SIZE create poor performance
     * tradeoffs.
     */
    private static final int ENFORCED_MINIMUM_SEGMENT_SIZE = 8;

    /**
     * The size of the completedSegments array when it is lazy-initialized.
     */
    private static final int INITIAL_SEGMENT_ARRAY_SIZE = 16;

    /**
     * The minimum size of each allocated segment. Value can increase over time as
     * the structure scales up.
     */
    private int minimumSegmentSize;

    /**
     * The total number of valid bytes written into completed segments. Used for
     * calculating overall size.
     */
    private int completedSegmentsTotalBytes;

    /**
     * The number of completed segments stored within {@code completedSegments}.
     */
    private int completedSegmentCount;

    /**
     * The set of arrays containing previous segments of the inputs. Each is
     * guaranteed to be full, and size may vary as each must be >=
     * {@code minimumSegmentSize} at the time it is allocated.
     * <p>
     * Doubles in length when extra capacity is required.
     */
    private byte[][] completedSegments;

    /**
     * The in-progress buffer where the most recent data is stored.
     */
    private byte[] currentSegment;

    /**
     * The number of valid bytes in the current segment.
     */
    private int currentSegmentCount;

    /**
     * The size of the current segment.
     */
    private int currentSegmentLengthInBytes;

    /**
     * Creates a new {@code MemoryOutputStream} with a default initial size.
     *
     * @since 29
     */
    public MemoryOutputStream() {
        this(32);
    }

    /**
     * Creates a new {@code MemoryOutputStream}, with a minimum segment size of the
     * specified size, in bytes.
     *
     * @param size the minimum segment.
     * @throws IllegalArgumentException if size is negative.
     * @since 29
     */
    public MemoryOutputStream(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("Negative initial size: " + size);
        } else {
            this.minimumSegmentSize = Math.max(size, ENFORCED_MINIMUM_SEGMENT_SIZE);
        }
        this.currentSegment = new byte[minimumSegmentSize];
        this.currentSegmentLengthInBytes = minimumSegmentSize;
    }

    /**
     * Adds the current segment to {$code completedSegments} and creates a new
     * segment of at least the requested length.
     * <p>
     * The new segment length will be the larger of {@code minimumSegmentSize} or
     * the requested length, guaranteeing:
     * <ol>
     * <li>The segment will never be smaller than {@code minimumSegmentSize} at the
     * time the segment is created.</li>
     * <li>No input byte[] will result in more than one new segment.</li>
     * </ol>
     *
     * @param extraSpaceRequired Minimum number of extra bytes required
     */
    private void allocateExtraCapacity(int extraSpaceRequired) {
        if (completedSegments == null) {
            // tests show that rapid initial expansion is critical to avoid unnecessarily
            // small segments
            minimumSegmentSize *= 8;
            completedSegments = new byte[INITIAL_SEGMENT_ARRAY_SIZE][];
        } else if (completedSegments.length == completedSegmentCount) {
            completedSegments = Arrays.copyOf(completedSegments, completedSegmentCount * 2);
            // also increase the minimum segment size to limit the overall number of
            // segments
            minimumSegmentSize *= 4;
        }
        completedSegments[completedSegmentCount++] = currentSegment;
        completedSegmentsTotalBytes += currentSegmentCount;

        currentSegmentLengthInBytes = Math.max(minimumSegmentSize, extraSpaceRequired);
        currentSegment = new byte[currentSegmentLengthInBytes];
        currentSegmentCount = 0;
    }

    /**
     * Writes the specified byte to this {@code MemoryOutputStream}.
     *
     * @param b the byte to be written.
     * @since 29
     */
    @Override
    public void write(int b) {
        if (currentSegmentCount == currentSegmentLengthInBytes) {
            allocateExtraCapacity(1);
        }
        currentSegment[currentSegmentCount] = (byte) b;
        currentSegmentCount = (currentSegmentCount + 1);
    }

    /**
     * Writes the complete contents of the specified byte array to this
     * {@code MemoryOutputStream}.
     *
     * @apiNote This method is equivalent to {@link #write(byte[],int,int) write(b,
     *          0, b.length)}.
     *
     * @param b the data.
     * @throws NullPointerException if {@code b} is {@code null}.
     * @since 29
     */
    public void writeBytes(byte b[]) {
        write(b, 0, b.length);
    }

    /**
     * Writes {@code len} bytes from the specified byte array starting at offset
     * {@code off} to this {@code MemoryOutputStream}.
     *
     * @param b   {@inheritDoc}
     * @param off {@inheritDoc}
     * @param len {@inheritDoc}
     * @throws NullPointerException      if {@code b} is {@code null}.
     * @throws IndexOutOfBoundsException if {@code off} is negative, {@code len} is
     *                                   negative, or {@code len} is greater than
     *                                   {@code b.length - off}
     * @since 29
     */
    @Override
    public void write(byte[] b, int off, int len) {
        int currentSegmentSpaceAvailable = currentSegmentLengthInBytes - currentSegmentCount;
        if (currentSegmentSpaceAvailable >= len) {
            // Sufficient space exists, copy it in
            System.arraycopy(b, off, currentSegment, currentSegmentCount, len);
            currentSegmentCount += len;
        } else {
            // some portion must overflow across segments
            if (currentSegmentSpaceAvailable > 0) {
                // copy what we can
                System.arraycopy(b, off, currentSegment, currentSegmentCount, currentSegmentSpaceAvailable);
            }

            // calculate how much data overflowed and allocate a new segment large enough to
            // accommodate
            int overflowLength = len - currentSegmentSpaceAvailable;
            allocateExtraCapacity(overflowLength);

            // calculate the offset of the remaining data, then copy into the new segment
            int remainingInputOffset = off + currentSegmentSpaceAvailable;
            System.arraycopy(b, remainingInputOffset, currentSegment, 0, overflowLength);
            currentSegmentCount = overflowLength;
        }
    }

    /**
     * Writes the contents of this {@code MemoryOutputStream} to the specified
     * output stream argument by calling the output stream's
     * {@code out.write(buf, 0, count)} once per segment.
     *
     * @param out the output stream to which to write the data.
     * @throws NullPointerException if {@code out} is {@code null}.
     * @throws IOException          if an I/O error occurs.
     * @since 29
     */
    public void writeTo(OutputStream out) throws IOException {
        // write each of the previous, fully-populated segments
        for (int i = 0; i < completedSegmentCount; i++) {
            byte[] segment = completedSegments[i];
            out.write(segment, 0, segment.length);
        }

        // write the valid portion of the current segment
        out.write(currentSegment, 0, currentSegmentCount);
    }

    /**
     * Resets the various fields of this {@code MemoryOutputStream} such that length
     * is zero and unnecessary objects are released.
     * 
     * @since 29
     */
    public void reset() {
        // reset the index for current segment but keep it around for reuse
        currentSegmentCount = 0;

        // release the past segments but keep the the array
        Arrays.fill(completedSegments, 0, completedSegmentCount, null);
        completedSegmentCount = 0;

        completedSegmentsTotalBytes = 0;
    }

    /**
     * Creates a newly allocated byte array populated with the accumulated data of
     * this {@code MemoryOutputStream}.
     * <p>
     * The output of this method is likely to be the single largest object
     * associated with the stream.
     *
     * @return the current contents of this {@code MemoryOutputStream}, as a byte
     *         array.
     * @see java.io.MemoryOutputStream#size()
     * @since 29
     */
    public byte[] toByteArray() {
        if (size() == 0) {
            return EMPTY_BYTE_ARRAY;
        }
        byte[] out = new byte[size()];
        int nextWritePosition = 0;

        // copy in each previous segment, remembering they are guaranteed to be full
        for (int i = 0; i < completedSegmentCount; i++) {
            byte[] segment = completedSegments[i];
            System.arraycopy(segment, 0, out, nextWritePosition, segment.length);
            nextWritePosition += segment.length;
        }

        // copy in the current segment, but only up to the populated length
        System.arraycopy(currentSegment, 0, out, nextWritePosition, currentSegmentCount);

        return out;
    }

    /**
     * Returns the current size of the accumulated contents.
     *
     * @return the value of the {@code overallLength} field, which is the number of
     *         valid bytes in this {@code MemoryOutputStream}.
     * @see java.io.MemoryOutputStream#currentSegmentCount
     * @see java.io.MemoryOutputStream#completedSegmentsTotalBytes
     * @since 29
     */
    public int size() {
        return completedSegmentsTotalBytes + currentSegmentCount;
    }

    /**
     * Converts this {@code MemoryOutputStream} contents into a string decoding
     * bytes using the default charset. The length of the new {@code String} is a
     * function of the charset, and hence may not be equal to the size of the
     * buffer.
     * <p>
     * This method always replaces malformed-input and unmappable-character
     * sequences with the default replacement string for the default charset. The
     * {@linkplain java.nio.charset.CharsetDecoder} class should be used when more
     * control over the decoding process is required.
     *
     * @see Charset#defaultCharset()
     * @return String decoded from this {@code MemoryOutputStream} contents.
     * @since 29
     */
    @Override
    public String toString() {
        return new String(toByteArray(), 0, size());
    }

    /**
     * Converts this {@code MemoryOutputStream}'s contents into a string by decoding
     * the bytes using the named {@link Charset charset}.
     * <p>
     * This method is equivalent to {@code #toString(charset)} that takes a
     * {@link Charset charset}. *
     *
     * @param charsetName the name of a supported {@link Charset charset}
     * @return String decoded from this {@code MemoryOutputStream}'s contents.
     * @throws UnsupportedEncodingException If the named charset is not supported
     * @since 29
     */
    public String toString(String charsetName) throws UnsupportedEncodingException {
        return new String(toByteArray(), 0, size(), charsetName);
    }

    /**
     * Converts this {@code MemoryOutputStream}'s contents into a string by decoding
     * the bytes using the specified {@link Charset charset}. The length of the new
     * {@code String} is a function of the charset, and hence may not be equal to
     * the length of the byte array.
     *
     * <p>
     * This method always replaces malformed-input and unmappable-character
     * sequences with the charset's default replacement string. The
     * {@link java.nio.charset.CharsetDecoder} class should be used when more
     * control over the decoding process is required.
     *
     * @param charset the {@linkplain Charset charset} to be used to decode the
     *                {@code bytes}
     * @return String decoded from this {@code MemoryOutputStream}'s contents.
     * @since 29
     */
    public String toString(Charset charset) {
        return new String(toByteArray(), 0, size(), charset);
    }

    /**
     * Creates a newly allocated string containing the contents of this
     * {@code MemoryOutputStream}. Each character <i>c</i> in the resulting string
     * is constructed from the corresponding element <i>b</i> in the byte array such
     * that:
     *
     * @deprecated This method does not properly convert bytes into characters. As
     *             of JDK&nbsp;1.1, the preferred way to do this is via the
     *             {@link #toString(String charsetName)} or
     *             {@link #toString(Charset charset)} method, which takes an
     *             encoding-name or charset argument, or the {@code toString()}
     *             method, which uses the default charset.
     *
     * @param hibyte the high byte of each resulting Unicode character.
     * @return the current contents of the output stream, as a string.
     * @see java.io.MemoryOutputStream#size()
     * @see java.io.MemoryOutputStream#toString(String)
     * @see java.io.MemoryOutputStream#toString()
     * @see Charset#defaultCharset()
     * @since 29
     */
    @Deprecated
    public String toString(int hibyte) {
        return new String(toByteArray(), hibyte, 0, size());
    }

}
