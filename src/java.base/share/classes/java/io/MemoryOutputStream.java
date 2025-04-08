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

import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * A {@code ByteArrayOutputStream} built on a different datastructure, relying
 * on a series of {@code byte[]} that can be iterated or reconstituted as
 * necessary. Capacity growth is performed by creating an additional segment,
 * thus avoiding the redundant copying and large objects that are
 * characteristics of {@code ByteArrayOutputStream}. Performance approaches 4x
 * faster for payloads that exceed the initial capacity hint.
 * <p>
 * Also unlike {@code ByteArrayOutputStream}, this class is entirely
 * unsynchronized, and concurrent accesses require external handling.
 * <p>
 * 
 * Closing a {@code MemoryOutputStream} has no effect. The methods in this class
 * can be called after the stream has been closed without generating an
 * {@code IOException}.
 *
 * @see java.lang.IllegalStateException
 * @see java.io.ByteArrayOutputStream
 * @see java.io.OutputStream
 * @since 25
 * @author John Engebretson
 */

class MemoryOutputStream extends ByteArrayOutputStream {

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
     * The total number of valid bytes written into completed segments. Used for
     * calculating overall size.
     */
    private long completedSegmentsTotalBytes;

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
     * The minimum size of each allocated segment. Value can increase over time as
     * the structure scales up.
     */
    private int minimumSegmentSize;

    /**
     * Creates a new {@code MemoryOutputStream} with a default initial capacity.
     */
    MemoryOutputStream() {
        this(32);
    }

    /**
     * Creates a new {@code MemoryOutputStream} with the specified initial capacity.
     *
     * @param size the minimum segment.
     * @throws IllegalArgumentException if size is negative.
     */
    MemoryOutputStream(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("Negative initial size: " + size);
        } else {
            this.minimumSegmentSize = Math.max(size, ENFORCED_MINIMUM_SEGMENT_SIZE);
        }
        this.currentSegment = new byte[minimumSegmentSize];
        this.currentSegmentLengthInBytes = minimumSegmentSize;
    }

    /**
     * Adds the current segment to {@code completedSegments} and creates a new
     * segment of at least the requested length.
     * <p>
     * The new segment length will be the larger of {@code minimumSegmentSize} or
     * the requested length, guaranteeing:
     * <ol>
     * <li>The segment will never be smaller than the value of
     * {@code minimumSegmentSize}, at the time the segment is created.</li>
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
        completedSegmentsTotalBytes += currentSegmentLengthInBytes;

        currentSegmentLengthInBytes = Math.max(minimumSegmentSize, extraSpaceRequired);
        currentSegment = new byte[currentSegmentLengthInBytes];
        currentSegmentCount = 0;
    }

    /**
     * Resets the various fields of this {@code MemoryOutputStream} such that length
     * is zero and most objects are released.
     */
    public void reset() {
        // release all completed segments
        completedSegments = null;
        completedSegmentCount = 0;
        completedSegmentsTotalBytes = 0;

        // reset the index for current segment but keep it around for reuse
        currentSegmentCount = 0;
    }

    /**
     * Returns the accumulated payload size. Calling this method for payloads larger
     * than Integer.MAX_VALUE will result in {@code IllegalStateException}.
     *
     * @return current payload size
     */
    public int size() {
        long size = completedSegmentsTotalBytes + currentSegmentLengthInBytes;
        if (size > Integer.MAX_VALUE) {
            throw new IllegalStateException("Current size of this stream cannot be expressed as an int");
        }

        return (int) size;
    }

    /**
     * Creates a newly allocated byte array populated with the accumulated data of
     * this {@code MemoryOutputStream}. The output of this method is likely to be
     * the single largest object associated with the stream.
     * <p>
     * Throws {@code  IllegalStateException} if the payload size is greater than the
     * maximum possible length of an array ({@code Integer.MAX_VALUE}).
     *
     * @return the current contents of this {@code MemoryOutputStream} as a byte
     *         array
     * @throws java.lang.IllegalStateException when the payload is larger than can
     *                                         be stored in an array.
     */
    public byte[] toByteArray() {
        int currentSize = size();
        if (currentSize == 0) {
            return EMPTY_BYTE_ARRAY;
        }

        byte[] out = new byte[currentSize];
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
     * {@inheritDoc}
     *
     * @throws java.lang.IllegalStateException when the payload is larger than can
     *                                         be stored in an array.
     */
    @Override
    public String toString() {
        return new String(toByteArray());
    }

    /**
     * {@inheritDoc}
     *
     * @throws java.lang.IllegalStateException when the payload is larger than can
     *                                         be stored in an array.
     */
    public String toString(Charset charset) {
        return new String(toByteArray(), charset);
    }

    /**
     * {@inheritDoc}
     *
     * @throws java.lang.IllegalStateException when the payload is larger than can
     *                                         be stored in an array.
     */
    public String toString(String charsetName) throws UnsupportedEncodingException {
        return new String(toByteArray(), charsetName);
    }

    /**
     * {@inheritDoc}
     *
     *
     * @deprecated This method does not properly convert bytes into characters.
     * As of JDK&nbsp;1.1, the preferred way to do this is via the
     * {@link #toString(String charsetName)} or {@link #toString(Charset charset)}
     * method, which takes an encoding-name or charset argument,
     * or the {@code toString()} method, which uses the default charset.
     *
     * @throws java.lang.IllegalStateException when the payload is larger than can
     *                                         be stored in an array.
     */
    @Deprecated
    public String toString(int hibyte) {
        return new String(buf, hibyte, 0, count);
    }

    /**
     * Writes {@code len} bytes from the specified byte array starting at offset
     * {@code off} to this {@code MemoryOutputStream}. Guaranteed to allocate 0 or 1
     * segments.
     *
     * @param b   {@inheritDoc}
     * @param off {@inheritDoc}
     * @param len {@inheritDoc}
     * @throws NullPointerException      if {@code b} is {@code null}.
     * @throws IndexOutOfBoundsException if {@code off} is negative, {@code len} is
     *                                   negative, or {@code len} is greater than
     *                                   {@code b.length - off}
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
                currentSegmentSpaceAvailable = 0;
                currentSegmentCount = currentSegmentLengthInBytes;
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
     * {@inheritDoc}
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
     * Writes the contents of this {@code MemoryOutputStream} to the specified
     * output stream argument by calling the output stream's
     * {@code out.write(buf, 0, count)} once per segment. Note that segment lengths
     * are variable, so the calls to {@code out.write} may as well.
     *
     * @param out the output stream to which to write the data.
     * @throws NullPointerException if {@code out} is {@code null}.
     * @throws IOException          if an I/O error occurs.
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

}
