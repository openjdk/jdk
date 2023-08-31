/*
 * Copyright (c) 1995, 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.util;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import jdk.internal.ValueBased;
import jdk.internal.util.ArraysSupport;
import jdk.internal.vm.annotation.Stable;

/**
 * This class implements a vector of bits that grows as needed. Each
 * component of the bit set has a {@code boolean} value. The
 * bits of a {@code BitSet} are indexed by nonnegative integers.
 * Individual indexed bits can be examined, set, or cleared. One
 * {@code BitSet} may be used to modify the contents of another
 * {@code BitSet} through logical AND, logical inclusive OR, and
 * logical exclusive OR operations.
 *
 * <p>By default, all bits in the set initially have the value
 * {@code false}.
 *
 * <p>Every bit set has a current size, which is the number of bits
 * of space currently in use by the bit set. Note that the size is
 * related to the implementation of a bit set, so it may change with
 * implementation. The length of a bit set relates to logical length
 * of a bit set and is defined independently of implementation.
 *
 * <p>Unless otherwise noted, passing a null parameter to any of the
 * methods in a {@code BitSet} will result in a
 * {@code NullPointerException}.
 *
 * <p>A {@code BitSet} is not safe for multithreaded use without
 * external synchronization.
 *
 * @author  Arthur van Hoff
 * @author  Michael McCloskey
 * @author  Martin Buchholz
 * @since   1.0
 */
public non-sealed class BitSet implements BitSetReadOps, Cloneable, java.io.Serializable {
    /*
     * BitSets are packed into arrays of "words."  Currently, a word is
     * a long, which consists of 64 bits, requiring 6 address bits.
     * The choice of word size is determined purely by performance concerns.
     */
    private static final int ADDRESS_BITS_PER_WORD = 6;
    private static final int BITS_PER_WORD = 1 << ADDRESS_BITS_PER_WORD;
    private static final int BIT_INDEX_MASK = BITS_PER_WORD - 1;

    /* Used to shift left or right for a partial word mask */
    private static final long WORD_MASK = 0xffffffffffffffffL;

    /**
     * @serialField bits long[]
     *
     * The bits in this BitSet.  The ith bit is stored in bits[i/64] at
     * bit position i % 64 (where bit position 0 refers to the least
     * significant bit and 63 refers to the most significant bit).
     */
    @java.io.Serial
    private static final ObjectStreamField[] serialPersistentFields = {
        new ObjectStreamField("bits", long[].class),
    };

    /**
     * The internal field corresponding to the serialField "bits".
     */
    private long[] words;

    /**
     * The number of words in the logical size of this BitSet.
     */
    private transient int wordsInUse = 0;

    /**
     * Whether the size of "words" is user-specified.  If so, we assume
     * the user knows what he's doing and try harder to preserve it.
     */
    private transient boolean sizeIsSticky = false;

    /* use serialVersionUID from JDK 1.0.2 for interoperability */
    @java.io.Serial
    private static final long serialVersionUID = 7997698588986878753L;

    /**
     * Given a bit index, return word index containing it.
     */
    private static int wordIndex(int bitIndex) {
        return bitIndex >> ADDRESS_BITS_PER_WORD;
    }

    /**
     * Every public method must preserve these invariants.
     */
    private void checkInvariants() {
        assert(wordsInUse == 0 || words[wordsInUse - 1] != 0);
        assert(wordsInUse >= 0 && wordsInUse <= words.length);
        assert(wordsInUse == words.length || words[wordsInUse] == 0);
    }

    /**
     * Sets the field wordsInUse to the logical size in words of the bit set.
     * WARNING:This method assumes that the number of words actually in use is
     * less than or equal to the current value of wordsInUse!
     */
    private void recalculateWordsInUse() {
        // Traverse the bitset until a used word is found
        int i;
        for (i = wordsInUse-1; i >= 0; i--)
            if (words[i] != 0)
                break;

        wordsInUse = i+1; // The new logical size
    }

    /**
     * Creates a new bit set. All bits are initially {@code false}.
     */
    public BitSet() {
        initWords(BITS_PER_WORD);
        sizeIsSticky = false;
    }

    /**
     * Creates a bit set whose initial size is large enough to explicitly
     * represent bits with indices in the range {@code 0} through
     * {@code nbits-1}. All bits are initially {@code false}.
     *
     * @param  nbits the initial size of the bit set
     * @throws NegativeArraySizeException if the specified initial size
     *         is negative
     */
    public BitSet(int nbits) {
        // nbits can't be negative; size 0 is OK
        if (nbits < 0)
            throw new NegativeArraySizeException("nbits < 0: " + nbits);

        initWords(nbits);
        sizeIsSticky = true;
    }

    private void initWords(int nbits) {
        words = new long[wordIndex(nbits-1) + 1];
    }

    /**
     * Creates a bit set using words as the internal representation.
     * The last word (if there is one) must be non-zero.
     */
    private BitSet(long[] words) {
        this.words = words;
        this.wordsInUse = words.length;
        checkInvariants();
    }

    /**
     * Returns a new bit set containing all the bits in the given long array.
     *
     * <p>More precisely,
     * <br>{@code BitSet.valueOf(longs).get(n) == ((longs[n/64] & (1L<<(n%64))) != 0)}
     * <br>for all {@code n < 64 * longs.length}.
     *
     * <p>This method is equivalent to
     * {@code BitSet.valueOf(LongBuffer.wrap(longs))}.
     *
     * @param longs a long array containing a little-endian representation
     *        of a sequence of bits to be used as the initial bits of the
     *        new bit set
     * @return a {@code BitSet} containing all the bits in the long array
     * @since 1.7
     */
    public static BitSet valueOf(long[] longs) {
        int n;
        for (n = longs.length; n > 0 && longs[n - 1] == 0; n--)
            ;
        return new BitSet(Arrays.copyOf(longs, n));
    }

    /**
     * Returns a new bit set containing all the bits in the given long
     * buffer between its position and limit.
     *
     * <p>More precisely,
     * <br>{@code BitSet.valueOf(lb).get(n) == ((lb.get(lb.position()+n/64) & (1L<<(n%64))) != 0)}
     * <br>for all {@code n < 64 * lb.remaining()}.
     *
     * <p>The long buffer is not modified by this method, and no
     * reference to the buffer is retained by the bit set.
     *
     * @param lb a long buffer containing a little-endian representation
     *        of a sequence of bits between its position and limit, to be
     *        used as the initial bits of the new bit set
     * @return a {@code BitSet} containing all the bits in the buffer in the
     *         specified range
     * @since 1.7
     */
    public static BitSet valueOf(LongBuffer lb) {
        lb = lb.slice();
        int n;
        for (n = lb.remaining(); n > 0 && lb.get(n - 1) == 0; n--)
            ;
        long[] words = new long[n];
        lb.get(words);
        return new BitSet(words);
    }

    /**
     * Returns a new bit set containing all the bits in the given byte array.
     *
     * <p>More precisely,
     * <br>{@code BitSet.valueOf(bytes).get(n) == ((bytes[n/8] & (1<<(n%8))) != 0)}
     * <br>for all {@code n <  8 * bytes.length}.
     *
     * <p>This method is equivalent to
     * {@code BitSet.valueOf(ByteBuffer.wrap(bytes))}.
     *
     * @param bytes a byte array containing a little-endian
     *        representation of a sequence of bits to be used as the
     *        initial bits of the new bit set
     * @return a {@code BitSet} containing all the bits in the byte array
     * @since 1.7
     */
    public static BitSet valueOf(byte[] bytes) {
        return BitSet.valueOf(ByteBuffer.wrap(bytes));
    }

    /**
     * Returns a new bit set containing all the bits in the given byte
     * buffer between its position and limit.
     *
     * <p>More precisely,
     * <br>{@code BitSet.valueOf(bb).get(n) == ((bb.get(bb.position()+n/8) & (1<<(n%8))) != 0)}
     * <br>for all {@code n < 8 * bb.remaining()}.
     *
     * <p>The byte buffer is not modified by this method, and no
     * reference to the buffer is retained by the bit set.
     *
     * @param bb a byte buffer containing a little-endian representation
     *        of a sequence of bits between its position and limit, to be
     *        used as the initial bits of the new bit set
     * @return a {@code BitSet} containing all the bits in the buffer in the
     *         specified range
     * @since 1.7
     */
    public static BitSet valueOf(ByteBuffer bb) {
        bb = bb.slice().order(ByteOrder.LITTLE_ENDIAN);
        int n;
        for (n = bb.remaining(); n > 0 && bb.get(n - 1) == 0; n--)
            ;
        long[] words = new long[(n + 7) / 8];
        bb.limit(n);
        int i = 0;
        while (bb.remaining() >= 8)
            words[i++] = bb.getLong();
        for (int remaining = bb.remaining(), j = 0; j < remaining; j++)
            words[i] |= (bb.get() & 0xffL) << (8 * j);
        return new BitSet(words);
    }

    @Override
    public byte[] toByteArray() {
        return toByteArray(words, wordsInUse);
    }

    @Override
    public long[] toLongArray() {
        return Arrays.copyOf(words, wordsInUse);
    }

    /**
     * Ensures that the BitSet can hold enough words.
     * @param wordsRequired the minimum acceptable number of words.
     */
    private void ensureCapacity(int wordsRequired) {
        if (words.length < wordsRequired) {
            // Allocate larger of doubled size or required size
            int request = Math.max(2 * words.length, wordsRequired);
            words = Arrays.copyOf(words, request);
            sizeIsSticky = false;
        }
    }

    /**
     * Ensures that the BitSet can accommodate a given wordIndex,
     * temporarily violating the invariants.  The caller must
     * restore the invariants before returning to the user,
     * possibly using recalculateWordsInUse().
     * @param wordIndex the index to be accommodated.
     */
    private void expandTo(int wordIndex) {
        int wordsRequired = wordIndex+1;
        if (wordsInUse < wordsRequired) {
            ensureCapacity(wordsRequired);
            wordsInUse = wordsRequired;
        }
    }

    /**
     * Checks that fromIndex ... toIndex is a valid range of bit indices.
     */
    private static void checkRange(int fromIndex, int toIndex) {
        requireNonNegative("fromIndex", fromIndex);
        requireNonNegative("toIndex", toIndex);
        if (fromIndex > toIndex)
            throw new IndexOutOfBoundsException("fromIndex: " + fromIndex +
                                                " > toIndex: " + toIndex);
    }

    /**
     * Sets the bit at the specified index to the complement of its
     * current value.
     *
     * @param  bitIndex the index of the bit to flip
     * @throws IndexOutOfBoundsException if the specified index is negative
     * @since  1.4
     */
    public void flip(int bitIndex) {
        requireNonNegative("bitIndex", bitIndex);

        int wordIndex = wordIndex(bitIndex);
        expandTo(wordIndex);

        words[wordIndex] ^= (1L << bitIndex);

        recalculateWordsInUse();
        checkInvariants();
    }

    /**
     * Sets each bit from the specified {@code fromIndex} (inclusive) to the
     * specified {@code toIndex} (exclusive) to the complement of its current
     * value.
     *
     * @param  fromIndex index of the first bit to flip
     * @param  toIndex index after the last bit to flip
     * @throws IndexOutOfBoundsException if {@code fromIndex} is negative,
     *         or {@code toIndex} is negative, or {@code fromIndex} is
     *         larger than {@code toIndex}
     * @since  1.4
     */
    public void flip(int fromIndex, int toIndex) {
        checkRange(fromIndex, toIndex);

        if (fromIndex == toIndex)
            return;

        int startWordIndex = wordIndex(fromIndex);
        int endWordIndex   = wordIndex(toIndex - 1);
        expandTo(endWordIndex);

        long firstWordMask = WORD_MASK << fromIndex;
        long lastWordMask  = WORD_MASK >>> -toIndex;
        if (startWordIndex == endWordIndex) {
            // Case 1: One word
            words[startWordIndex] ^= (firstWordMask & lastWordMask);
        } else {
            // Case 2: Multiple words
            // Handle first word
            words[startWordIndex] ^= firstWordMask;

            // Handle intermediate words, if any
            for (int i = startWordIndex+1; i < endWordIndex; i++)
                words[i] ^= WORD_MASK;

            // Handle last word
            words[endWordIndex] ^= lastWordMask;
        }

        recalculateWordsInUse();
        checkInvariants();
    }

    /**
     * Sets the bit at the specified index to {@code true}.
     *
     * @param  bitIndex a bit index
     * @throws IndexOutOfBoundsException if the specified index is negative
     * @since  1.0
     */
    public void set(int bitIndex) {
        requireNonNegative("bitIndex", bitIndex);

        int wordIndex = wordIndex(bitIndex);
        expandTo(wordIndex);

        words[wordIndex] |= (1L << bitIndex); // Restores invariants

        checkInvariants();
    }

    /**
     * Sets the bit at the specified index to the specified value.
     *
     * @param  bitIndex a bit index
     * @param  value a boolean value to set
     * @throws IndexOutOfBoundsException if the specified index is negative
     * @since  1.4
     */
    public void set(int bitIndex, boolean value) {
        if (value)
            set(bitIndex);
        else
            clear(bitIndex);
    }

    /**
     * Sets the bits from the specified {@code fromIndex} (inclusive) to the
     * specified {@code toIndex} (exclusive) to {@code true}.
     *
     * @param  fromIndex index of the first bit to be set
     * @param  toIndex index after the last bit to be set
     * @throws IndexOutOfBoundsException if {@code fromIndex} is negative,
     *         or {@code toIndex} is negative, or {@code fromIndex} is
     *         larger than {@code toIndex}
     * @since  1.4
     */
    public void set(int fromIndex, int toIndex) {
        checkRange(fromIndex, toIndex);

        if (fromIndex == toIndex)
            return;

        // Increase capacity if necessary
        int startWordIndex = wordIndex(fromIndex);
        int endWordIndex   = wordIndex(toIndex - 1);
        expandTo(endWordIndex);

        long firstWordMask = WORD_MASK << fromIndex;
        long lastWordMask  = WORD_MASK >>> -toIndex;
        if (startWordIndex == endWordIndex) {
            // Case 1: One word
            words[startWordIndex] |= (firstWordMask & lastWordMask);
        } else {
            // Case 2: Multiple words
            // Handle first word
            words[startWordIndex] |= firstWordMask;

            // Handle intermediate words, if any
            for (int i = startWordIndex+1; i < endWordIndex; i++)
                words[i] = WORD_MASK;

            // Handle last word (restores invariants)
            words[endWordIndex] |= lastWordMask;
        }

        checkInvariants();
    }

    /**
     * Sets the bits from the specified {@code fromIndex} (inclusive) to the
     * specified {@code toIndex} (exclusive) to the specified value.
     *
     * @param  fromIndex index of the first bit to be set
     * @param  toIndex index after the last bit to be set
     * @param  value value to set the selected bits to
     * @throws IndexOutOfBoundsException if {@code fromIndex} is negative,
     *         or {@code toIndex} is negative, or {@code fromIndex} is
     *         larger than {@code toIndex}
     * @since  1.4
     */
    public void set(int fromIndex, int toIndex, boolean value) {
        if (value)
            set(fromIndex, toIndex);
        else
            clear(fromIndex, toIndex);
    }

    /**
     * Sets the bit specified by the index to {@code false}.
     *
     * @param  bitIndex the index of the bit to be cleared
     * @throws IndexOutOfBoundsException if the specified index is negative
     * @since  1.0
     */
    public void clear(int bitIndex) {
        requireNonNegative("bitIndex", bitIndex);

        int wordIndex = wordIndex(bitIndex);
        if (wordIndex >= wordsInUse)
            return;

        words[wordIndex] &= ~(1L << bitIndex);

        recalculateWordsInUse();
        checkInvariants();
    }

    /**
     * Sets the bits from the specified {@code fromIndex} (inclusive) to the
     * specified {@code toIndex} (exclusive) to {@code false}.
     *
     * @param  fromIndex index of the first bit to be cleared
     * @param  toIndex index after the last bit to be cleared
     * @throws IndexOutOfBoundsException if {@code fromIndex} is negative,
     *         or {@code toIndex} is negative, or {@code fromIndex} is
     *         larger than {@code toIndex}
     * @since  1.4
     */
    public void clear(int fromIndex, int toIndex) {
        checkRange(fromIndex, toIndex);

        if (fromIndex == toIndex)
            return;

        int startWordIndex = wordIndex(fromIndex);
        if (startWordIndex >= wordsInUse)
            return;

        int endWordIndex = wordIndex(toIndex - 1);
        if (endWordIndex >= wordsInUse) {
            toIndex = length();
            endWordIndex = wordsInUse - 1;
        }

        long firstWordMask = WORD_MASK << fromIndex;
        long lastWordMask  = WORD_MASK >>> -toIndex;
        if (startWordIndex == endWordIndex) {
            // Case 1: One word
            words[startWordIndex] &= ~(firstWordMask & lastWordMask);
        } else {
            // Case 2: Multiple words
            // Handle first word
            words[startWordIndex] &= ~firstWordMask;

            // Handle intermediate words, if any
            for (int i = startWordIndex+1; i < endWordIndex; i++)
                words[i] = 0;

            // Handle last word
            words[endWordIndex] &= ~lastWordMask;
        }

        recalculateWordsInUse();
        checkInvariants();
    }

    /**
     * Sets all of the bits in this BitSet to {@code false}.
     *
     * @since 1.4
     */
    public void clear() {
        while (wordsInUse > 0)
            words[--wordsInUse] = 0;
    }

    /**
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    @Override
    public boolean get(int bitIndex) {
        checkInvariants();
        return get(words, wordsInUse, bitIndex);
    }

    /**
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    @Override
    public BitSet get(int fromIndex, int toIndex) {
        checkInvariants();
        return get(words, length(), fromIndex, toIndex);
    }

    /**
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    @Override
    public int nextSetBit(int fromIndex) {
        checkInvariants();
        return nextSetBit(words, wordsInUse, fromIndex);
    }

    /**
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    @Override
    public int nextClearBit(int fromIndex) {
        checkInvariants();
        return nextClearBit(words, wordsInUse, fromIndex);
    }

    /**
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    @Override
    public int previousSetBit(int fromIndex) {
        checkInvariants();
        return previousSetBit(words, wordsInUse, this::length, fromIndex);
    }

    /**
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    @Override
    public int previousClearBit(int fromIndex) {
        checkInvariants();
        return previousClearBit(words, wordsInUse, fromIndex);
    }

    @Override
    public int length() {
        return length(words, wordsInUse);
    }

    @Override
    public boolean isEmpty() {
        return wordsInUse == 0;
    }

    @Override
    public boolean intersects(BitSet set) {
        return intersects(words, wordsInUse, set.words, set.wordsInUse);
    }

    @Override
    public boolean intersects(BitSetReadOps set) {
        return switch (set) {
            case BitSet otherBs -> intersects(otherBs);
            case OfImmutable ro -> intersects(words, wordsInUse, ro.words, ro.words.length);
        };
    }

    @Override
    public int cardinality() {
        return cardinality(words, wordsInUse);
    }

    /**
     * Performs a logical <b>AND</b> of this target bit set with the
     * argument bit set. This bit set is modified so that each bit in it
     * has the value {@code true} if and only if it both initially
     * had the value {@code true} and the corresponding bit in the
     * bit set argument also had the value {@code true}.
     *
     * @param set a bit set
     */
    public void and(BitSet set) {
        if (this == set)
            return;

        while (wordsInUse > set.wordsInUse)
            words[--wordsInUse] = 0;

        // Perform logical AND on words in common
        for (int i = 0; i < wordsInUse; i++)
            words[i] &= set.words[i];

        recalculateWordsInUse();
        checkInvariants();
    }

    /**
     * Performs a logical <b>OR</b> of this bit set with the bit set
     * argument. This bit set is modified so that a bit in it has the
     * value {@code true} if and only if it either already had the
     * value {@code true} or the corresponding bit in the bit set
     * argument has the value {@code true}.
     *
     * @param set a bit set
     */
    public void or(BitSet set) {
        if (this == set)
            return;

        int wordsInCommon = Math.min(wordsInUse, set.wordsInUse);

        if (wordsInUse < set.wordsInUse) {
            ensureCapacity(set.wordsInUse);
            wordsInUse = set.wordsInUse;
        }

        // Perform logical OR on words in common
        for (int i = 0; i < wordsInCommon; i++)
            words[i] |= set.words[i];

        // Copy any remaining words
        if (wordsInCommon < set.wordsInUse)
            System.arraycopy(set.words, wordsInCommon,
                             words, wordsInCommon,
                             wordsInUse - wordsInCommon);

        // recalculateWordsInUse() is unnecessary
        checkInvariants();
    }

    /**
     * Performs a logical <b>XOR</b> of this bit set with the bit set
     * argument. This bit set is modified so that a bit in it has the
     * value {@code true} if and only if one of the following
     * statements holds:
     * <ul>
     * <li>The bit initially has the value {@code true}, and the
     *     corresponding bit in the argument has the value {@code false}.
     * <li>The bit initially has the value {@code false}, and the
     *     corresponding bit in the argument has the value {@code true}.
     * </ul>
     *
     * @param  set a bit set
     */
    public void xor(BitSet set) {
        int wordsInCommon = Math.min(wordsInUse, set.wordsInUse);

        if (wordsInUse < set.wordsInUse) {
            ensureCapacity(set.wordsInUse);
            wordsInUse = set.wordsInUse;
        }

        // Perform logical XOR on words in common
        for (int i = 0; i < wordsInCommon; i++)
            words[i] ^= set.words[i];

        // Copy any remaining words
        if (wordsInCommon < set.wordsInUse)
            System.arraycopy(set.words, wordsInCommon,
                             words, wordsInCommon,
                             set.wordsInUse - wordsInCommon);

        recalculateWordsInUse();
        checkInvariants();
    }

    /**
     * Clears all of the bits in this {@code BitSet} whose corresponding
     * bit is set in the specified {@code BitSet}.
     *
     * @param  set the {@code BitSet} with which to mask this
     *         {@code BitSet}
     * @since  1.2
     */
    public void andNot(BitSet set) {
        // Perform logical (a & !b) on words in common
        for (int i = Math.min(wordsInUse, set.wordsInUse) - 1; i >= 0; i--)
            words[i] &= ~set.words[i];

        recalculateWordsInUse();
        checkInvariants();
    }

    @Override
    public int hashCode() {
        return hashCode(words, wordsInUse);
    }

    @Override
    public int size() {
        return words.length * BITS_PER_WORD;
    }

    @Override
    public boolean equals(Object obj) {
        return switch (obj) {
            case BitSet set -> {
                if (this == set) {
                    yield true;
                }
                checkInvariants();
                set.checkInvariants();
                yield equals(words, wordsInUse, set.words, set.wordsInUse);
            }
            case OfImmutable roSet -> {
                checkInvariants();
                yield equals(words, wordsInUse, roSet.words, roSet.words.length);
            }
            default -> false;
        };
    }

    @Override
    public Object clone() {
        if (! sizeIsSticky)
            trimToSize();

        try {
            BitSet result = (BitSet) super.clone();
            result.words = words.clone();
            result.checkInvariants();
            return result;
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    /**
     * Attempts to reduce internal storage used for the bits in this bit set.
     * Calling this method may, but is not required to, affect the value
     * returned by a subsequent call to the {@link #size()} method.
     */
    private void trimToSize() {
        if (wordsInUse != words.length) {
            words = Arrays.copyOf(words, wordsInUse);
            checkInvariants();
        }
    }

    /**
     * Save the state of the {@code BitSet} instance to a stream (i.e.,
     * serialize it).
     */
    @java.io.Serial
    private void writeObject(ObjectOutputStream s)
        throws IOException {

        checkInvariants();

        if (! sizeIsSticky)
            trimToSize();

        ObjectOutputStream.PutField fields = s.putFields();
        fields.put("bits", words);
        s.writeFields();
    }

    /**
     * Reconstitute the {@code BitSet} instance from a stream (i.e.,
     * deserialize it).
     */
    @java.io.Serial
    private void readObject(ObjectInputStream s)
        throws IOException, ClassNotFoundException {

        ObjectInputStream.GetField fields = s.readFields();
        words = (long[]) fields.get("bits", null);

        // Assume maximum length then find real length
        // because recalculateWordsInUse assumes maintenance
        // or reduction in logical size
        wordsInUse = words.length;
        recalculateWordsInUse();
        sizeIsSticky = (words.length > 0 && words[words.length-1] == 0L); // heuristic
        checkInvariants();
    }

    @Override
    public String toString() {
        checkInvariants();
        return toString(this, wordsInUse);
    }

    @Override
    public IntStream stream() {
        class BitSetSpliterator implements Spliterator.OfInt {
            private int index; // current bit index for a set bit
            private int fence; // -1 until used; then one past last bit index
            private int est;   // size estimate
            private boolean root; // true if root and not split
            // root == true then size estimate is accurate
            // index == -1 or index >= fence if fully traversed
            // Special case when the max bit set is Integer.MAX_VALUE

            BitSetSpliterator(int origin, int fence, int est, boolean root) {
                this.index = origin;
                this.fence = fence;
                this.est = est;
                this.root = root;
            }

            private int getFence() {
                int hi;
                if ((hi = fence) < 0) {
                    // Round up fence to maximum cardinality for allocated words
                    // This is sufficient and cheap for sequential access
                    // When splitting this value is lowered
                    hi = fence = (wordsInUse >= wordIndex(Integer.MAX_VALUE))
                                 ? Integer.MAX_VALUE
                                 : wordsInUse << ADDRESS_BITS_PER_WORD;
                    est = cardinality();
                    index = nextSetBit(0);
                }
                return hi;
            }

            @Override
            public boolean tryAdvance(IntConsumer action) {
                Objects.requireNonNull(action);

                int hi = getFence();
                int i = index;
                if (i < 0 || i >= hi) {
                    // Check if there is a final bit set for Integer.MAX_VALUE
                    if (i == Integer.MAX_VALUE && hi == Integer.MAX_VALUE) {
                        index = -1;
                        action.accept(Integer.MAX_VALUE);
                        return true;
                    }
                    return false;
                }

                index = nextSetBit(i + 1, wordIndex(hi - 1));
                action.accept(i);
                return true;
            }

            @Override
            public void forEachRemaining(IntConsumer action) {
                Objects.requireNonNull(action);

                int hi = getFence();
                int i = index;
                index = -1;

                if (i >= 0 && i < hi) {
                    action.accept(i++);

                    int u = wordIndex(i);      // next lower word bound
                    int v = wordIndex(hi - 1); // upper word bound

                    words_loop:
                    for (; u <= v && i <= hi; u++, i = u << ADDRESS_BITS_PER_WORD) {
                        long word = words[u] & (WORD_MASK << i);
                        while (word != 0) {
                            i = (u << ADDRESS_BITS_PER_WORD) + Long.numberOfTrailingZeros(word);
                            if (i >= hi) {
                                // Break out of outer loop to ensure check of
                                // Integer.MAX_VALUE bit set
                                break words_loop;
                            }

                            // Flip the set bit
                            word &= ~(1L << i);

                            action.accept(i);
                        }
                    }
                }

                // Check if there is a final bit set for Integer.MAX_VALUE
                if (i == Integer.MAX_VALUE && hi == Integer.MAX_VALUE) {
                    action.accept(Integer.MAX_VALUE);
                }
            }

            @Override
            public OfInt trySplit() {
                int hi = getFence();
                int lo = index;
                if (lo < 0) {
                    return null;
                }

                // Lower the fence to be the upper bound of last bit set
                // The index is the first bit set, thus this spliterator
                // covers one bit and cannot be split, or two or more
                // bits
                hi = fence = (hi < Integer.MAX_VALUE || !get(Integer.MAX_VALUE))
                        ? previousSetBit(hi - 1) + 1
                        : Integer.MAX_VALUE;

                // Find the mid point
                int mid = (lo + hi) >>> 1;
                if (lo >= mid) {
                    return null;
                }

                // Raise the index of this spliterator to be the next set bit
                // from the mid point
                index = nextSetBit(mid, wordIndex(hi - 1));
                root = false;

                // Don't lower the fence (mid point) of the returned spliterator,
                // traversal or further splitting will do that work
                return new BitSetSpliterator(lo, mid, est >>>= 1, false);
            }

            @Override
            public long estimateSize() {
                getFence(); // force init
                return est;
            }

            @Override
            public int characteristics() {
                // Only sized when root and not split
                return (root ? Spliterator.SIZED : 0) |
                    Spliterator.ORDERED | Spliterator.DISTINCT | Spliterator.SORTED;
            }

            @Override
            public Comparator<? super Integer> getComparator() {
                return null;
            }
        }
        return StreamSupport.intStream(new BitSetSpliterator(0, -1, 0, true), false);
    }

    /**
     * Returns the index of the first bit that is set to {@code true}
     * that occurs on or after the specified starting index and up to and
     * including the specified word index
     * If no such bit exists then {@code -1} is returned.
     *
     * @param  fromIndex the index to start checking from (inclusive)
     * @param  toWordIndex the last word index to check (inclusive)
     * @return the index of the next set bit, or {@code -1} if there
     *         is no such bit
     */
    private int nextSetBit(int fromIndex, int toWordIndex) {
        int u = wordIndex(fromIndex);
        // Check if out of bounds
        if (u > toWordIndex)
            return -1;

        long word = words[u] & (WORD_MASK << fromIndex);

        while (true) {
            if (word != 0)
                return (u * BITS_PER_WORD) + Long.numberOfTrailingZeros(word);
            // Check if out of bounds
            if (++u > toWordIndex)
                return -1;
            word = words[u];
        }
    }

    /**
     * {@return a new immutable snapshot of this {@code BitSet}}.
     * <p>
     * A {@code ImmutableBitSet} is threadsafe and can be used without external
     * synchronisation.
     * <p>
     * ImmutableBitSet instances are eligible for constant-folding optimization
     * by the VM.
     *
     * @implNote The method is free to return a {@link ValueBased} implementation.
     *
     * @since 22
     */
    public BitSetReadOps asImmutable() {
        return OfImmutable.of(this);
    }

    // Support methods that allows code sharing of the two implementations.

    private static int length(long[] words, int wordsInUse) {
        if (wordsInUse == 0)
            return 0;

        return BITS_PER_WORD * (wordsInUse - 1) +
                (BITS_PER_WORD - Long.numberOfLeadingZeros(words[wordsInUse - 1]));
    }

    private static int cardinality(long[] words, int wordsInUse) {
        int sum = 0;
        for (int i = 0; i < wordsInUse; i++)
            sum += Long.bitCount(words[i]);
        return sum;
    }

    private static boolean get(long[] words, int wordsInUse, int bitIndex) {
        requireNonNegative("bitIndex", bitIndex);
        int wordIndex = wordIndex(bitIndex);
        return (wordIndex < wordsInUse)
                && ((words[wordIndex] & (1L << bitIndex)) != 0);
    }

    private static boolean intersects(long[] words, int wordsInUse, long[] otherWords, int otherWordsInUse) {
        for (int i = Math.min(wordsInUse, otherWordsInUse) - 1; i >= 0; i--)
            if ((words[i] & otherWords[i]) != 0)
                return true;
        return false;
    }

    private static byte[] toByteArray(long[] words, int wordsInUse) {
        int n = wordsInUse;
        if (n == 0)
            return new byte[0];
        int len = 8 * (n-1);
        for (long x = words[n - 1]; x != 0; x >>>= 8)
            len++;
        byte[] bytes = new byte[len];
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < n - 1; i++)
            bb.putLong(words[i]);
        for (long x = words[n - 1]; x != 0; x >>>= 8)
            bb.put((byte) (x & 0xff));
        return bytes;
    }

    private static BitSet get(long[] words, int len,  int fromIndex, int toIndex) {
        checkRange(fromIndex, toIndex);

        // If no set bits in range return empty bitset
        if (len <= fromIndex || fromIndex == toIndex)
            return new BitSet(0);

        // An optimization
        if (toIndex > len)
            toIndex = len;

        BitSet result = new BitSet(toIndex - fromIndex);
        int targetWords = wordIndex(toIndex - fromIndex - 1) + 1;
        int sourceIndex = wordIndex(fromIndex);
        boolean wordAligned = ((fromIndex & BIT_INDEX_MASK) == 0);

        // Process all words but the last word
        for (int i = 0; i < targetWords - 1; i++, sourceIndex++)
            result.words[i] = wordAligned ? words[sourceIndex] :
                    (words[sourceIndex] >>> fromIndex) |
                            (words[sourceIndex+1] << -fromIndex);

        // Process the last word
        long lastWordMask = WORD_MASK >>> -toIndex;
        result.words[targetWords - 1] =
                ((toIndex-1) & BIT_INDEX_MASK) < (fromIndex & BIT_INDEX_MASK)
                        ? /* straddles source words */
                        ((words[sourceIndex] >>> fromIndex) |
                                (words[sourceIndex+1] & lastWordMask) << -fromIndex)
                        :
                        ((words[sourceIndex] & lastWordMask) >>> fromIndex);

        // Set wordsInUse correctly
        result.wordsInUse = targetWords;
        result.recalculateWordsInUse();
        result.checkInvariants();

        return result;
    }

    private static int nextSetBit(long[] words, int wordsInUse, int fromIndex) {
        requireNonNegative("fromIndex", fromIndex);

        int u = wordIndex(fromIndex);
        if (u >= wordsInUse)
            return -1;

        long word = words[u] & (WORD_MASK << fromIndex);

        while (true) {
            if (word != 0)
                return (u * BITS_PER_WORD) + Long.numberOfTrailingZeros(word);
            if (++u == wordsInUse)
                return -1;
            word = words[u];
        }
    }

    private static int nextClearBit(long[] words, int wordsInUse,int fromIndex) {
        // Neither spec nor implementation handle bitsets of maximal length.
        // See 4816253.
        requireNonNegative("fromIndex", fromIndex);
        int u = wordIndex(fromIndex);
        if (u >= wordsInUse)
            return fromIndex;

        long word = ~words[u] & (WORD_MASK << fromIndex);

        while (true) {
            if (word != 0)
                return (u * BITS_PER_WORD) + Long.numberOfTrailingZeros(word);
            if (++u == wordsInUse)
                return wordsInUse * BITS_PER_WORD;
            word = ~words[u];
        }
    }

    private static int previousSetBit(long[] words, int wordsInUse, IntSupplier lengthSupplier, int fromIndex) {
        if (fromIndex < 0) {
            if (fromIndex == -1)
                return -1;
            throw new IndexOutOfBoundsException(
                    "fromIndex < -1: " + fromIndex);
        }

        int u = wordIndex(fromIndex);
        if (u >= wordsInUse)
            return lengthSupplier.getAsInt() - 1;

        long word = words[u] & (WORD_MASK >>> -(fromIndex + 1));

        while (true) {
            if (word != 0)
                return (u + 1) * BITS_PER_WORD - 1 - Long.numberOfLeadingZeros(word);
            if (u-- == 0)
                return -1;
            word = words[u];
        }
    }

    private static int previousClearBit(long[] words, int wordsInUse, int fromIndex) {
        if (fromIndex < 0) {
            if (fromIndex == -1)
                return -1;
            throw new IndexOutOfBoundsException(
                    "fromIndex < -1: " + fromIndex);
        }
        int u = wordIndex(fromIndex);
        if (u >= wordsInUse)
            return fromIndex;

        long word = ~words[u] & (WORD_MASK >>> -(fromIndex+1));

        while (true) {
            if (word != 0)
                return (u+1) * BITS_PER_WORD -1 - Long.numberOfLeadingZeros(word);
            if (u-- == 0)
                return -1;
            word = ~words[u];
        }
    }

    private static int hashCode(long[] words, int wordsInUse) {
        long h = 1234;
        for (int i = wordsInUse; --i >= 0; )
            h ^= words[i] * (i + 1);

        return (int)((h >> 32) ^ h);
    }

    private static boolean equals(long[] words, int wordsInUse, long[] otherWords, int otherWordsInUse) {
        if (wordsInUse != otherWordsInUse)
            return false;

        // Check words in use by both BitSets
        return ArraysSupport.mismatch(words, 0, otherWords, 0, wordsInUse) == -1;
    }

    private static String toString(BitSetReadOps bs, int wordsInUse) {
        final int MAX_INITIAL_CAPACITY = Integer.MAX_VALUE - 8;
        int numBits = (wordsInUse > 128) ?
                bs.cardinality() : wordsInUse * BITS_PER_WORD;
        // Avoid overflow in the case of a humongous numBits
        int initialCapacity = (numBits <= (MAX_INITIAL_CAPACITY - 2) / 6) ?
                6 * numBits + 2 : MAX_INITIAL_CAPACITY;
        StringBuilder b = new StringBuilder(initialCapacity);
        b.append('{');

        int i = bs.nextSetBit(0);
        if (i != -1) {
            b.append(i);
            while (true) {
                if (++i < 0) break;
                if ((i = bs.nextSetBit(i)) < 0) break;
                int endOfRun = bs.nextClearBit(i);
                do { b.append(", ").append(i); }
                while (++i != endOfRun);
            }
        }

        b.append('}');
        return b.toString();
    }

    private static int requireNonNegative(String label, int index) {
        if (index < 0)
            throw new IndexOutOfBoundsException(label + " < 0: " + index);
        return index;
    }

    @ValueBased
    static final class OfImmutable implements BitSetReadOps {

        @Stable
        private final long[] words;
        private final int cardinality;
        private final int length;

        private OfImmutable(BitSet original) {
            this.words = original.toLongArray();

            // These are eagerly computed.
            // It would be possible to lazily compute them declaring
            // @Stable int fields holding the values.
            //
            // For TOCTOU reasons, we calculate these values from the array copy
            this.cardinality = cardinality0();
            this.length = length0();
        }

        /**
         * @throws IndexOutOfBoundsException {@inheritDoc}
         */
        @Override
        public boolean get(int bitIndex) {
            return BitSet.get(words, words.length, bitIndex);
        }

        @Override
        public int cardinality() {
            return cardinality;
        }

        @Override
        public int length() {
            return length;
        }

        @Override
        public boolean isEmpty() {
            return cardinality == 0;
        }

        /**
         * @throws IndexOutOfBoundsException {@inheritDoc}
         */
        @Override
        public int nextSetBit(int fromIndex) {
            return BitSet.nextSetBit(words, words.length, fromIndex);
        }

        /**
         * @throws IndexOutOfBoundsException {@inheritDoc}
         */
        @Override
        public int nextClearBit(int fromIndex) {
            return BitSet.nextClearBit(words, words.length, fromIndex);
        }

        /**
         * @throws IndexOutOfBoundsException {@inheritDoc}
         */
        @Override
        public int previousSetBit(int fromIndex) {
            return BitSet.previousSetBit(words, words.length, this::length, fromIndex);
        }

        /**
         * @throws IndexOutOfBoundsException {@inheritDoc}
         */
        @Override
        public int previousClearBit(int fromIndex) {
            return BitSet.previousClearBit(words, words.length, fromIndex);
        }

        @Override
        public IntStream stream() {
            // Todo: Improve on this
            if (isEmpty()) {
                return IntStream.empty();
            }
            return IntStream.iterate(nextSetBit(0), i -> i != -1, this::nextSetBit);
        }

        @Override
        public byte[] toByteArray() {
            return BitSet.toByteArray(words, words.length);
        }

        @Override
        public long[] toLongArray() {
            return words.clone();
        }

        /**
         * @throws IndexOutOfBoundsException {@inheritDoc}
         */
        @Override
        public BitSetReadOps get(int fromIndex, int toIndex) {
            BitSet bs = BitSet.get(words, length(), fromIndex, toIndex);
            return of(bs);
        }

        @Override
        public boolean intersects(BitSet set) {
            return BitSet.intersects(words, words.length, set.words, set.wordsInUse);
        }

        @Override
        public boolean intersects(BitSetReadOps set) {
            return switch (set) {
                case BitSet otherBs -> intersects(otherBs);
                case OfImmutable ro -> BitSet.intersects(words, words.length, ro.words, ro.words.length);
            };
        }

        @Override
        public int size() {
            return words.length * BITS_PER_WORD;
        }

        @Override
        public Object clone() {
            try {
                BitSet result = (BitSet) super.clone();
                // As the array never changes, we can reuse it
                result.words = words;
                return result;
            } catch (CloneNotSupportedException e) {
                throw new InternalError(e);
            }
        }

        @Override
        public boolean equals(Object o) {
            return switch (o) {
                case BitSet bs -> BitSet.equals(words, words.length, bs.words, bs.wordsInUse);
                case OfImmutable ro -> BitSet.equals(words, words.length, ro.words, ro.length);
                default -> false;
            };
        }

        @Override
        public int hashCode() {
            return BitSet.hashCode(words, words.length);
        }

        @Override
        public String toString() {
            return BitSet.toString(this, words.length);
        }

        private int cardinality0() {
            return BitSet.cardinality(words, words.length);
        }

        private int length0() {
            // Scan backwards
            return BitSet.length(words, words.length);
        }

        private static BitSetReadOps of(BitSet original) {
            return new OfImmutable(original);
        }

    }

}
