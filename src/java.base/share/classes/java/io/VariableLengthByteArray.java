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

import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * Efficient and scalable structure for storing a variable number of bytes,
 * conceptually an array but with a different implementation underneath.
 */
public class VariableLengthByteArray {
    /**
     * Functional interface to act upon each segment. Internal only.
     */
    private static interface ApplyBySegmentFunction {
        public void apply(byte[] segment, int offset, int length);
    }

    /**
     * Function interface to act upon the specific byte.
     */
    private static interface ApplyToIndexFunction {
        public byte apply(byte currentValue);
    }

    /**
     * A ByteArrayOutputStream backed by this VLBA. Not thread-safe.
     */
    private class ByteArrayOutputStreamView extends ByteArrayOutputStream {

        /**
         * {@inheritDoc}
         */
        @Override
        public void reset() {
            VariableLengthByteArray.this.clear();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int size() {
            return VariableLengthByteArray.this.sizeAsInt();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public byte[] toByteArray() {
            return VariableLengthByteArray.this.toArray();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return VariableLengthByteArray.this.toString();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString(Charset charset) {
            return VariableLengthByteArray.this.toString(charset);
        }

        /**
         * {@inheritDoc}
         */
        @SuppressWarnings("deprecation")
        @Override
        public String toString(int hibyte) {
            if (completedSegmentCount == 0) {
                return new String(currentSegment, hibyte, 0, currentSegmentCount);
            }
            return new String(VariableLengthByteArray.this.toArray(), hibyte, 0, count);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString(String charsetName) throws UnsupportedEncodingException {
            return VariableLengthByteArray.this.toString(charsetName);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void write(byte[] b, int off, int len) {
            VariableLengthByteArray.this.add(b, off, len);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void write(int b) {
            VariableLengthByteArray.this.add((byte) b);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeTo(OutputStream out) throws IOException {
            VariableLengthByteArray.this
                    .applyBySegmentWithException((segment, offset, length) -> out.write(segment, offset, length));
        }

    }

    private class SeekableByteChannelView implements SeekableByteChannel {

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public void close() throws IOException {
            // no-op
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            throw new UnsupportedOperationException("Not yet implemented");
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            return VariableLengthByteArray.this.add(src);
        }

        @Override
        public long position() throws IOException {
            throw new UnsupportedOperationException("Not yet implemented");
        }

        @Override
        public SeekableByteChannel position(long newPosition) throws IOException {
            throw new UnsupportedOperationException("Not yet implemented");
        }

        @Override
        public long size() throws IOException {
            return VariableLengthByteArray.this.size();
        }

        @Override
        public SeekableByteChannel truncate(long size) throws IOException {
            throw new UnsupportedOperationException("Not yet implemented");
        }

    }

    /**
     * This view is NOT thread-safe, mutations during the lifetime of the view will
     * cause problems.
     */
    private class InputStreamView extends ByteArrayInputStream {
        public InputStreamView() {
            super(null);
        }

        private long position;

        private long mark;

        private long size;

        /**
         * {@inheritDoc}
         */
        @Override
        public int available() {
            // check whether response fits into available()
            long remaining = (size - position);
            return (int) Math.min(remaining, Integer.MAX_VALUE);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void close() {
            // no-op
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void mark(int readlimit) {
            this.mark = position;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean markSupported() {
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int read() {
            return get(position++);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int read(byte[] b, int off, int len) {
            throw new UnsupportedOperationException("Not yet implemented");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public byte[] readAllBytes() {
            return VariableLengthByteArray.this.toArray();

        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int readNBytes(byte[] b, int off, int len) {
            throw new UnsupportedOperationException("Not yet implemented");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public byte[] readNBytes(int len) {
            throw new UnsupportedOperationException("Not yet implemented");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void reset() {
            position = mark;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long skip(long n) {
            long currentPosition = position;
            position = Math.min(VariableLengthByteArray.this.size(), position + n);
            return position - currentPosition;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void skipNBytes(long n) {
            throw new UnsupportedOperationException("Not yet implemented");
        }

        /**
         * {@inheritDoc}
         */
        @SuppressWarnings("resource")
        @Override
        public long transferTo(OutputStream out) throws IOException {
            new ByteArrayOutputStreamView().writeTo(out);
            return size();
        }

    }

    private class WriteToArrayFunction implements ApplyBySegmentFunction {
        private int nextWritePosition;
        public byte[] output;

        public WriteToArrayFunction(int size) {
            output = new byte[size];
        }

        @Override
        public void apply(byte[] segment, int offset, int length) {
            System.arraycopy(segment, 0, output, nextWritePosition, length);
            nextWritePosition += length;
        }
    }

    /**
     * Functional interface to act upon each segment. Internal only.
     */
    private static interface ApplyBySegmentWithExceptionFunction {
        public void apply(byte[] segment, int offset, int length) throws IOException;
    }

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
     * Factory method to get a ByteArrayOutputStream based on this new
     * datastructure.
     *
     * @return instance
     */
    public static ByteArrayOutputStream createByteArrayOutputStream() {
        return new VariableLengthByteArray().new ByteArrayOutputStreamView();
    }

    /**
     * Factory method to get a InputStream based on this new datastructure.
     *
     * @return instance
     */
    public static InputStream createInputStream() {
        return new VariableLengthByteArray().new InputStreamView();
    }

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
     * Array of start indexes, allowing faster seeks.
     */
    private long[] completedSegmentStartPositions;

    /**
     * The total number of valid bytes written into completed segments. Used for
     * calculating overall size.
     */
    private long completedSegmentsTotalLength;

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
    private int currentSegmentLength;

    /**
     * The minimum size of each allocated segment. Value can increase over time as
     * the structure scales up.
     */
    private int minimumSegmentSize;

    /**
     * Creates a new {@code VariableLengthByteArray} with a default initial
     * capacity.
     */
    public VariableLengthByteArray() {
        this(32);
    }

    /**
     * Creates a new {@code VariableLengthArray} with the specified initial
     * capacity.
     *
     * @param size the minimum segment.
     * @throws IllegalArgumentException if size is negative.
     */
    public VariableLengthByteArray(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("Negative initial size: " + size);
        } else {
            this.minimumSegmentSize = Math.max(size, ENFORCED_MINIMUM_SEGMENT_SIZE);
        }
        this.currentSegment = new byte[minimumSegmentSize];
        this.currentSegmentLength = minimumSegmentSize;
    }

    /**
     * Adds one byte.
     *
     * @param entry content
     */
    public void add(byte entry) {
        if (currentSegmentCount == currentSegmentLength) {
            allocateExtraCapacity(1);
        }
        currentSegment[currentSegmentCount] = entry;
        currentSegmentCount = (currentSegmentCount + 1);
    }

    /**
     * Writes {@code len} bytes from the specified byte array starting at offset
     * {@code off} to this {@code VariableLengthByteArray}. Guaranteed to allocate 0
     * or 1 segments.
     *
     * @param b   {@inheritDoc}
     * @param off {@inheritDoc}
     * @param len {@inheritDoc}
     * @throws NullPointerException      if {@code b} is {@code null}.
     * @throws IndexOutOfBoundsException if {@code off} is negative, {@code len} is
     *                                   negative, or {@code len} is greater than
     *                                   {@code b.length - off}
     */
    public void add(byte[] b, int off, int len) {
        int currentSegmentSpaceAvailable = currentSegmentLength - currentSegmentCount;
        if (currentSegmentSpaceAvailable >= len) {
            // Sufficient space exists, copy it in
            System.arraycopy(b, off, currentSegment, currentSegmentCount, len);
            currentSegmentCount += len;
        } else {
            // some portion must overflow across segments
            int overflowLength = len - currentSegmentSpaceAvailable;
            int remainingContentOffset;
            if (currentSegmentSpaceAvailable > 0) {
                // copy what we can
                System.arraycopy(b, off, currentSegment, currentSegmentCount, currentSegmentSpaceAvailable);
                currentSegmentCount = currentSegmentLength;
                remainingContentOffset = off + currentSegmentSpaceAvailable;
            } else {
                remainingContentOffset = off;
            }

            // calculate how much data overflowed and allocate a new segment large enough to
            // accommodate
            allocateExtraCapacity(overflowLength);

            // calculate the offset of the remaining data, then copy into the new segment
            System.arraycopy(b, remainingContentOffset, currentSegment, 0, overflowLength);
            currentSegmentCount = overflowLength;
        }
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
            completedSegmentStartPositions = new long[INITIAL_SEGMENT_ARRAY_SIZE];
        } else if (completedSegments.length == completedSegmentCount) {
            completedSegments = Arrays.copyOf(completedSegments, completedSegmentCount * 2);
            completedSegmentStartPositions = Arrays.copyOf(completedSegmentStartPositions, completedSegmentCount * 2);
            // also increase the minimum segment size to limit the overall number of
            // segments
            minimumSegmentSize *= 4;
        }
        rolloverSegment(Math.max(minimumSegmentSize, extraSpaceRequired));
    }

    /**
     * Writes the contents of this {@code VariableLengthByteArray} to the specified
     * output stream argument by calling the output stream's
     * {@code out.write(buf, 0, count)} once per segment. Note that segment lengths
     * are variable, so the calls to {@code out.write} may as well.
     *
     * @param out the output stream to which to write the data.
     * @throws NullPointerException if {@code out} is {@code null}.
     * @throws IOException          if an I/O error occurs.
     */
    private void applyBySegment(ApplyBySegmentFunction f) {
        // write each of the previous, fully-populated segments
        for (int i = 0; i < completedSegmentCount; i++) {
            byte[] segment = completedSegments[i];
            f.apply(segment, 0, segment.length);
        }

        // write the valid portion of the current segment
        f.apply(currentSegment, 0, currentSegmentCount);
    }

    /**
     * @param index
     * @param applyToIndexFunction
     * @return
     */
    private byte applyToIndex(long index, ApplyToIndexFunction applyToIndexFunction) {
        verifyRangeWithinPayload(index);
        int entryIndex;
        byte[] segment;
        if (completedSegments == null || index >= completedSegmentsTotalLength) {
            // use the current segment
            segment = currentSegment;
            entryIndex = (int) (index - completedSegmentsTotalLength);
        } else {
            // find the matching completed segment
            int segmentIndex = findTargetSegment(index);

            if (segmentIndex >= 0) {
                segment = completedSegments[segmentIndex];
                entryIndex = (int) (index - completedSegmentStartPositions[segmentIndex]);
            } else {
                // exact match not found, calculate the preceding match
                segmentIndex = -1 * (segmentIndex + 2);
                if (segmentIndex >= completedSegmentCount) {
                    segment = currentSegment;
                    entryIndex = (int) (index - completedSegmentsTotalLength);
                } else {
                    segment = completedSegments[segmentIndex];
                    entryIndex = (int) (index - completedSegmentStartPositions[segmentIndex]);
                }
            }
        }
        byte oldEntry = segment[entryIndex];
        byte newEntry = applyToIndexFunction.apply(oldEntry);
        if (newEntry != oldEntry) {
            segment[entryIndex] = newEntry;
        }
        return oldEntry;

    }

    /**
     * Returns a {@code ByteArrayOutputStream} based on this instance's current
     * state.
     *
     * @return ByteArrayOutputStream view
     */
    public ByteArrayOutputStream asOutputStream() {
        return new ByteArrayOutputStreamView();
    }

    /**
     * Returns an {@code InputStream} based on this instance's current state.
     *
     * @return InputStream view
     */
    public InputStream asInputStream() {
        return new InputStreamView();
    }

    /**
     * View this data as a {@code ByteBuffer}.
     *
     * @return ByteBuffer
     */
    public ByteBuffer asByteBuffer() {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * View this data as a {@code ByteChannel}.
     *
     * @return SeekableByteChannel
     */
    public SeekableByteChannel asByteChannel() {
        return new SeekableByteChannelView();
    }

    /**
     * Resets the various fields of this {@code VariableLengthByteArray} such that
     * length is zero and most objects are released.
     */
    public void clear() {
        // release all completed segments
        completedSegments = null;
        completedSegmentCount = 0;
        completedSegmentsTotalLength = 0;

        // reset the index for current segment but keep it around for reuse
        currentSegmentCount = 0;
    }

    /**
     * @param position
     * @return
     */
    private int findTargetSegment(long position) {
        if (position < 0) {
            throw new IllegalArgumentException("Cannot search for a negative position");
        }
        if (position > size()) {
            throw new IllegalArgumentException("Cannot search for a position greater than current payload size");
        }
        if (completedSegments == null) {
            return 0;
        } else {
            int segmentIndex = Arrays.binarySearch(completedSegmentStartPositions, 0, completedSegmentCount, position);
            return segmentIndex;
        }
    }

    /**
     * Returns the value at the specified position.
     *
     * @param index location to read
     * @return content at that position
     */
    public byte get(long index) {
        return applyToIndex(index, (entry) -> entry);
    }

    /**
     * @param newSegmentLength
     */
    private void rolloverSegment(int newSegmentLength) {
        completedSegments[completedSegmentCount] = currentSegment;
        completedSegmentStartPositions[completedSegmentCount] = completedSegmentsTotalLength;
        completedSegmentsTotalLength += currentSegmentLength;
        completedSegmentCount++;
        currentSegment = new byte[newSegmentLength];
        currentSegmentCount = 0;
        currentSegmentLength = newSegmentLength;

    }

    /**
     * Random-access update to the specified index.
     *
     * @param entry new content
     * @param index target position
     * @return previous value
     */
    public byte set(byte entry, long index) {
        return applyToIndex(index, (oldValue) -> entry);
    }

    /**
     * Size of the current payload.
     *
     * @return size
     */
    public long size() {
        long size = completedSegmentsTotalLength + currentSegmentCount;
        return size;
    }

    /**
     * Size of the current payload, when expressible as an int.
     *
     * @return size
     * @throws IllegalStateException when the size is larger than int
     */
    public int sizeAsInt() {
        long size = size();
        if (size > Integer.MAX_VALUE) {
            throw new IllegalStateException("Payload is too large to report size as an int (" + size + "L)");
        }
        return (int) size;
    }

    /**
     * Creates a newly allocated byte array populated with the accumulated data of
     * this {@code VariableLengthByteArray}. The output of this method is likely to
     * be the single largest object associated with the structure.
     * <p>
     * Throws {@code  IllegalStateException} if the payload size is greater than the
     * maximum possible length of an array ({@code Integer.MAX_VALUE}).
     *
     * @return the current contents of this {@code VariableLengthByteArray} as a
     *         byte array
     * @throws java.lang.IllegalStateException when the payload is larger than can
     *                                         be stored in an array.
     */
    public byte[] toArray() {
        int currentSize = sizeAsInt();
        WriteToArrayFunction writeFunction = new WriteToArrayFunction(currentSize);
        applyBySegment(writeFunction);
        byte[] out = writeFunction.output;

        return out;
    }

    /**
     * @param index
     */
    private void verifyRangeWithinPayload(long index) {
        if (index >= size() || index < 0) {
            throw new IndexOutOfBoundsException("Index was " + index
                    + " but must be non-negative and less than the current length (" + size() + ")");
        }
    }

    /**
     * Writes the contents of this {@code VariableLengthByteArray} to the specified
     * output stream argument by calling the output stream's
     * {@code out.write(buf, 0, count)} once per segment. Note that segment lengths
     * are variable, so the calls to {@code out.write} may as well.
     *
     * @param out the output stream to which to write the data.
     * @throws NullPointerException if {@code out} is {@code null}.
     * @throws IOException          if an I/O error occurs.
     */
    private void applyBySegmentWithException(ApplyBySegmentWithExceptionFunction f) throws IOException {
        // write each of the previous, fully-populated segments
        for (int i = 0; i < completedSegmentCount; i++) {
            byte[] segment = completedSegments[i];
            f.apply(segment, 0, segment.length);
        }

        // write the valid portion of the current segment
        f.apply(currentSegment, 0, currentSegmentCount);
    }

    /**
     * Writes the valid contents of the buffer.
     *
     * @param buffer input data
     * @return number of bytes added
     */
    public int add(ByteBuffer buffer) {
        // same logic as with add(byte[]) - just read the channel instead of a byte[]

        return buffer.position();

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        if (completedSegmentCount == 0) {
            return new String(currentSegment, 0, currentSegmentCount);
        }
        return new String(toArray());
    }

    /**
     * Creates a String using the specified charset.
     *
     * @param charset Which charset to use
     * @return completed string
     * @throws IllegalStateException if payload size is too large to express as a
     *                               string
     */
    public String toString(Charset charset) {
        if (completedSegmentCount == 0) {
            return new String(currentSegment, 0, currentSegmentCount, charset);
        }
        return new String(toArray(), charset);
    }

    /**
     * Creates a String using the specified charset.
     *
     * @param charsetName Which charset to use
     * @return completed string
     * @throws IllegalStateException        if payload size is too large to express
     *                                      as a string
     * @throws UnsupportedEncodingException if an invalid charset name is specified
     */
    public String toString(String charsetName) throws UnsupportedEncodingException {
        if (completedSegmentCount == 0) {
            return new String(currentSegment, 0, currentSegmentCount, charsetName);
        }
        return new String(toArray(), charsetName);
    }

}
