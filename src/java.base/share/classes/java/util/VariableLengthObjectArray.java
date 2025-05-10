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

package java.util;

import java.io.IOException;
import java.lang.reflect.Array;

/**
 * Efficient and scalable structure for storing a variable number of Objects,
 * conceptually an array but with a different implementation underneath.
 *
 * @param <T> type
 */
public class VariableLengthObjectArray<T> {

    /**
     * Functional interface to act upon each segment. Internal only.
     */
    private static interface ApplyBySegmentFunction<T> {
        public void apply(T[] segment, int offset, int length) throws IOException;
    }

    /**
     * Function interface to act upon the specific entry.
     */
    private static interface ApplyToIndexFunction<T> {
        public T apply(T currentValue);
    }

    /**
     * Iterator<T> to access the data. Needs optimization and fixing, but hey, it's
     * a prototype.
     */
    private class EntryIterator implements Iterator<T> {
        int segmentContentIndex;
        int segmentIndex;
        int segmentLength;
        T[] segment;

        /**
         * Default constructor.
         */
        public EntryIterator() {
            advanceToNextSegment();
        }

        @Override
        public boolean hasNext() {
            return segment != null;
        }

        @Override
        public T next() {

            if (!hasNext()) {
                throw new IllegalStateException("Iterator has exceeded length - pay attention to hasNext()!");
            }
            T entry;

            entry = segment[segmentContentIndex++];

            if (segmentContentIndex == segmentLength) {
                advanceToNextSegment();
            }

            return entry;
        }

        private void advanceToNextSegment() {
            if (segment == currentSegment) {
                // finished the last segment
                segment = null;
                return;
            } else if (segmentIndex < completedSegmentCount) {
                // use the next completed segment
                segment = completedSegments[segmentIndex++];
                segmentLength = segment.length;
                segmentContentIndex = 0;
            } else {
                // use the current segment
                if (currentSegmentCount > 0) {
                    segment = currentSegment;
                    segmentLength = currentSegmentCount;
                    segmentContentIndex = 0;
                } else {
                    // the current segment has 0 bytes
                    segment = null;
                }
            }
        }
    }

    private class ListView implements List<T> {

        @Override
        public void add(int index, T element) {
            throw new UnsupportedOperationException("Not yet prototyped - anticipate log(n)");
        }

        @Override
        public boolean add(T e) {
            VariableLengthObjectArray.this.add(e);
            return true;
        }

        @Override
        public boolean addAll(Collection<? extends T> c) {
            for (T entry : c) {
                VariableLengthObjectArray.this.add(entry);
            }
            return true;
        }

        @Override
        public boolean addAll(int index, Collection<? extends T> c) {
            throw new UnsupportedOperationException("Not yet prototyped - anticipate log(n)");
        }

        @Override
        public void clear() {
            VariableLengthObjectArray.this.clear();
        }

        @Override
        public boolean contains(Object o) {
            throw new UnsupportedOperationException("Not yet implemented");
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            throw new UnsupportedOperationException("Not yet implemented");
        }

        @Override
        public T get(int index) {
            return VariableLengthObjectArray.this.get(index);
        }

        @Override
        public int indexOf(Object o) {
            throw new UnsupportedOperationException("Not yet implemented");
        }

        @Override
        public boolean isEmpty() {
            return (size() == 0);
        }

        @Override
        public Iterator<T> iterator() {
            return VariableLengthObjectArray.this.iterator();
        }

        @Override
        public int lastIndexOf(Object o) {
            throw new UnsupportedOperationException("Not yet implemented");
        }

        @Override
        public ListIterator<T> listIterator() {
            throw new UnsupportedOperationException("Not yet implemented");
        }

        @Override
        public ListIterator<T> listIterator(int index) {
            throw new UnsupportedOperationException("Not yet implemented");
        }

        @Override
        public T remove(int index) {
            throw new UnsupportedOperationException("Not yet implemented");
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException("Not yet implemented");
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException("Not yet implemented");
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException("Not yet implemented");
        }

        @Override
        public T set(int index, T element) {
            return VariableLengthObjectArray.this.applyToIndex(index, (entry) -> element);
        }

        @Override
        public int size() {
            long size = VariableLengthObjectArray.this.size();
            if (size >= Integer.MAX_VALUE) {
                throw new IllegalStateException(
                        "Payload size exceeds the maximum value returnable as an int (" + size + ")");
            }
            return (int) size;
        }

        @Override
        public List<T> subList(int fromIndex, int toIndex) {
            throw new UnsupportedOperationException("Not yet implemented");
        }

        @Override
        public Object[] toArray() {
            return VariableLengthObjectArray.this.toArray();
        }

        @Override
        public <S> S[] toArray(S[] a) {
            throw new UnsupportedOperationException("Not yet implemented");
        }

    }

    private class WriteToArrayFunction implements ApplyBySegmentFunction<T> {
        private int nextWritePosition;
        public T[] output;

        @SuppressWarnings("unchecked")
        public WriteToArrayFunction(int size) {
            output = (T[]) Array.newInstance(clazz, size);
        }

        @Override
        public void apply(T[] segment, int offset, int length) {
            System.arraycopy(segment, 0, output, nextWritePosition, length);
            nextWritePosition += length;
        }
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
     * Factory method to get a List based on this new datastructure.
     *
     * @param <T>   type
     * @param clazz class for type
     * @return instance
     */
    public static <T> List<T> asList(Class<T> clazz) {
        return new VariableLengthObjectArray<T>(clazz).new ListView();
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
    private T[][] completedSegments;

    /**
     * Array of start indexes, allowing faster seeks.
     */
    private long[] completedSegmentStartPositions;

    /**
     * The total number of valid entries written into completed segments. Used for
     * calculating overall size.
     */
    private long completedSegmentsTotalLength;

    /**
     * The in-progress buffer where the most recent data is stored.
     */
    private T[] currentSegment;

    /**
     * The number of valid entries in the current segment.
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

    private final Class<T> clazz;

    /**
     * Creates a new {@code MemoryOutputStream} with a default initial capacity.
     */
    VariableLengthObjectArray(Class<T> clazz) {
        this(clazz, 32);
    }

    /**
     * Creates a new {@code VariableLengthArray} with the specified initial
     * capacity.
     *
     * @param size the minimum segment.
     * @throws IllegalArgumentException if size is negative.
     */
    @SuppressWarnings("unchecked")
    VariableLengthObjectArray(Class<T> clazz, int size) {
        if (size < 0) {
            throw new IllegalArgumentException("Negative initial size: " + size);
        } else {
            this.minimumSegmentSize = Math.max(size, ENFORCED_MINIMUM_SEGMENT_SIZE);
        }
        this.clazz = clazz;
        this.currentSegment = (T[]) Array.newInstance(clazz, minimumSegmentSize);
        this.currentSegmentLength = minimumSegmentSize;
    }

    /**
     * @param entry
     */
    void add(T entry) {
        if (currentSegmentCount == currentSegmentLength) {
            allocateExtraCapacity(1);
        }
        currentSegment[currentSegmentCount] = entry;
        currentSegmentCount = (currentSegmentCount + 1);
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
    @SuppressWarnings("unchecked")
    private void allocateExtraCapacity(int extraSpaceRequired) {
        if (completedSegments == null) {
            // tests show that rapid initial expansion is critical to avoid unnecessarily
            // small segments
            minimumSegmentSize *= 8;
            completedSegments = (T[][]) Array.newInstance(clazz, INITIAL_SEGMENT_ARRAY_SIZE, 0);
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
     * Writes the contents of this {@code MemoryOutputStream} to the specified
     * output stream argument by calling the output stream's
     * {@code out.write(buf, 0, count)} once per segment. Note that segment lengths
     * are variable, so the calls to {@code out.write} may as well.
     *
     * @param out the output stream to which to write the data.
     * @throws NullPointerException if {@code out} is {@code null}.
     * @throws IOException          if an I/O error occurs.
     */
    private void applyBySegment(ApplyBySegmentFunction<T> f) throws IOException {
        // write each of the previous, fully-populated segments
        for (int i = 0; i < completedSegmentCount; i++) {
            T[] segment = completedSegments[i];
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
    private T applyToIndex(long index, ApplyToIndexFunction<T> applyToIndexFunction) {
        verifyRangeWithinPayload(index);
        int entryIndex;
        T[] segment;
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
        T oldEntry = segment[entryIndex];
        T newEntry = applyToIndexFunction.apply(oldEntry);
        if (newEntry != oldEntry) {
            segment[entryIndex] = newEntry;
        }
        return oldEntry;

    }

    /**
     * Resets the various fields of this {@code MemoryOutputStream} such that length
     * is zero and most objects are released.
     */
    void clear() {
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
     * @param index
     * @return
     */
    T get(long index) {
        return applyToIndex(index, (entry) -> entry);
    }

    /**
     * Iterator
     *
     * @return iterator
     */
    Iterator<T> iterator() {
        return new EntryIterator();
    }

    /**
     * @param newSegmentLength
     */
    @SuppressWarnings("unchecked")
    private void rolloverSegment(int newSegmentLength) {
        completedSegments[completedSegmentCount] = currentSegment;
        completedSegmentStartPositions[completedSegmentCount] = completedSegmentsTotalLength;
        completedSegmentsTotalLength += currentSegmentLength;
        completedSegmentCount++;
        currentSegment = (T[]) Array.newInstance(clazz, newSegmentLength);
        currentSegmentCount = 0;
        currentSegmentLength = newSegmentLength;

    }

    /**
     * @param entry
     * @param index
     * @return
     */
    T set(T entry, long index) {
        return applyToIndex(index, (oldValue) -> entry);
    }

    /**
     * @return
     */
    long size() {
        long size = completedSegmentsTotalLength + currentSegmentCount;
        return size;
    }

    /**
     * @return
     */
    int sizeAsInt() {
        long size = size();
        if (size > Integer.MAX_VALUE) {
            throw new IllegalStateException("Payload is too large to report size as an int (" + size + "L)");
        }
        return (int) size;
    }

    /**
     * Creates a newly allocated T array populated with the accumulated data of this
     * {@code MemoryOutputStream}. The output of this method is likely to be the
     * single largest object associated with the stream.
     * <p>
     * Throws {@code  IllegalStateException} if the payload size is greater than the
     * maximum possible length of an array ({@code Integer.MAX_VALUE}).
     *
     * @return the current contents of this {@code VariableLengthObjectArray} as a T
     *         array
     * @throws java.lang.IllegalStateException when the payload is larger than can
     *                                         be stored in an array.
     */
    T[] toArray() {
        int currentSize = sizeAsInt();
        WriteToArrayFunction writeFunction = new WriteToArrayFunction(currentSize);
        try {
            applyBySegment(writeFunction);
        } catch (IOException ioe) {
            throw new RuntimeException("toArray failed", ioe);
        }
        T[] out = writeFunction.output;

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

}
