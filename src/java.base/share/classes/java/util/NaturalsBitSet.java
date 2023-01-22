package java.util;

import java.io.*;
import java.nio.*;

/**
 * This class implements a set of non-negative integers that grows as needed.
 * Individual integers can be checked if they are contained, added, or removed.
 * One {@code NaturalsBitSet} may be used to modify the contents of another
 * {@code NaturalsBitSet} through logical AND, logical inclusive OR, and logical
 * exclusive OR operations.
 *
 * <p>
 * Every {@code NaturalsBitSet} has a current size, which is the number of bits
 * of space currently in use by the bit set. Note that the size is related to
 * the implementation of a bit set, so it may change with implementation. The
 * length of a bit set relates to logical length of a bit set and is defined
 * independently of implementation.
 *
 * <p>
 * Unless otherwise noted, passing a null parameter to any of the methods in a
 * {@code NaturalsBitSet} will result in a {@code NullPointerException}.
 *
 * <p>
 * A {@code NaturalsBitSet} is not safe for multithreaded use without external
 * synchronization.
 *
 * @author Fabio Romano
 * @since 21
 */
public class NaturalsBitSet extends BitSet {

    /**
     * The number of bits set to {@code true} in this {@code BitSet}.
     */
    private transient int cardinality = 0;

    /* use serialVersionUID from JDK 21 for interoperability */
    @java.io.Serial
    private static final long serialVersionUID = -212903409561554139L;

    /**
     * Every public method must preserve this invariant.
     */
    private void checkCardinality() {
        // avoid overflow if get(Integer.MAX_VALUE) == true
        assert (cardinality >= 0 && cardinality - 1 <= length() - 1);
    }

    /**
     * Creates a new empty {@code NaturalsBitSet}.
     */
    public NaturalsBitSet() {
        super();
    }

    /**
     * Creates an empty {@code NaturalsBitSet} whose initial size is large enough to
     * explicitly represent naturals in the range {@code 0} through {@code nbits-1}.
     *
     * @param nbits the initial size of the bit set
     * @throws NegativeArraySizeException if the specified initial size is negative
     */
    public NaturalsBitSet(int nbits) {
        super(nbits);
    }

    /**
     * Constructs a new {@code NaturalsBitSet} containing the integers in the
     * specified collection. It is created with an initial capacity sufficient to
     * contain the integers in the specified collection.
     *
     * @param c the collection whose integers are to be placed into this set
     * @throws NullPointerException      if the specified collection is null, or if
     *                                   some integer in the collection is null
     * @throws IndexOutOfBoundsException if some integer in the specified collection
     *                                   is negative
     */
    public NaturalsBitSet(Collection<Integer> c) {
        this(Collections.max(c) + 1);

        for (int i : c)
            set(i);
    }

    private NaturalsBitSet(long[] words) {
        super(words);
        computeCardinality();
        checkCardinality();
    }

    /**
     * Returns a new {@code NaturalsBitSet} containing all the bits in the given
     * long array.
     *
     * <p>
     * More precisely, <br>
     * {@code NaturalsBitSet.valueOf(longs).get(n) == ((longs[n/64] & (1L<<(n%64))) != 0)}
     * <br>
     * for all {@code n < 64 * longs.length}.
     *
     * <p>
     * This method is equivalent to
     * {@code NaturalsBitSet.valueOf(LongBuffer.wrap(longs))}.
     *
     * @param longs a long array containing a little-endian representation of a
     *              sequence of bits to be used as the initial bits of the new bit
     *              set
     * @return a {@code NaturalsBitSet} containing all the bits in the long array
     */
    public static NaturalsBitSet valueOf(long[] longs) {
        return new NaturalsBitSet(getWords(longs));
    }

    /**
     * Returns a new {@code NaturalsBitSet} containing all the bits in the given
     * long buffer between its position and limit.
     *
     * <p>
     * More precisely, <br>
     * {@code NaturalsBitSet.valueOf(lb).get(n) == ((lb.get(lb.position()+n/64) & (1L<<(n%64))) != 0)}
     * <br>
     * for all {@code n < 64 * lb.remaining()}.
     *
     * <p>
     * The long buffer is not modified by this method, and no reference to the
     * buffer is retained by the bit set.
     *
     * @param lb a long buffer containing a little-endian representation of a
     *           sequence of bits between its position and limit, to be used as the
     *           initial bits of the new bit set
     * @return a {@code NaturalsBitSet} containing all the bits in the buffer in the
     *         specified range
     */
    public static NaturalsBitSet valueOf(LongBuffer lb) {
        return new NaturalsBitSet(getWords(lb));
    }

    /**
     * Returns a new {@code NaturalsBitSet} containing all the bits in the given
     * byte array.
     *
     * <p>
     * More precisely, <br>
     * {@code NaturalsBitSet.valueOf(bytes).get(n) == ((bytes[n/8] & (1<<(n%8))) != 0)}
     * <br>
     * for all {@code n <  8 * bytes.length}.
     *
     * <p>
     * This method is equivalent to
     * {@code NaturalsBitSet.valueOf(ByteBuffer.wrap(bytes))}.
     *
     * @param bytes a byte array containing a little-endian representation of a
     *              sequence of bits to be used as the initial bits of the new bit
     *              set
     * @return a {@code NaturalsBitSet} containing all the bits in the byte array
     */
    public static NaturalsBitSet valueOf(byte[] bytes) {
        return new NaturalsBitSet(getWords(bytes));
    }

    /**
     * Returns a new {@code NaturalsBitSet} containing all the bits in the given
     * byte buffer between its position and limit.
     *
     * <p>
     * More precisely, <br>
     * {@code NaturalsBitSet.valueOf(bb).get(n) == ((bb.get(bb.position()+n/8) & (1<<(n%8))) != 0)}
     * <br>
     * for all {@code n < 8 * bb.remaining()}.
     *
     * <p>
     * The byte buffer is not modified by this method, and no reference to the
     * buffer is retained by the bit set.
     *
     * @param bb a byte buffer containing a little-endian representation of a
     *           sequence of bits between its position and limit, to be used as the
     *           initial bits of the new bit set
     * @return a {@code NaturalsBitSet} containing all the bits in the buffer in the
     *         specified range
     */
    public static NaturalsBitSet valueOf(ByteBuffer bb) {
        return new NaturalsBitSet(getWords(bb));
    }

    private void computeCardinality() {
        cardinality = bitCount(0, wordsInUse);
    }

    /**
     * Returns the number of bits set to true, starting from startWordIndex
     * (inclusive) to endWordIndex (exclusive). Word indices may be greater
     * than {@code wordsInUse}.
     */
    private int bitCount(int startWordIndex, int endWordIndex) {
        endWordIndex = Math.min(endWordIndex, wordsInUse);
        int sum = 0;

        for (int i = startWordIndex; i < endWordIndex; i++)
            sum += Long.bitCount(words[i]);

        return sum;
    }

    /**
     * Returns the number of bits set to true at the specified word.
     * {@code wordIndex} may be greater than or equal to {@code wordsInUse}.
     */
    private int bitCount(int wordIndex) {
        return wordIndex < wordsInUse ? Long.bitCount(words[wordIndex]) : 0;
    }

    /**
     * If the specified value is contained in this set, removes it; otherwise adds
     * it to this set.
     *
     * @param n the integer to flip
     * @throws IndexOutOfBoundsException if the specified integer is negative
     */
    @Override
    public void flip(int n) {
        super.flip(n);
        cardinality += (words[wordIndex(n)] & (1L << n)) != 0 ? 1 : -1;
        checkCardinality();
    }

    /**
     * Flip each integer (as pecified in {@link #flip(int)} from the specified
     * {@code start} (inclusive) to the specified {@code end} (exclusive).
     *
     * @param start the first integer to flip
     * @param end   value after the last integer to flip
     * @throws IndexOutOfBoundsException if {@code start} is negative, or
     *                                   {@code end} is negative, or {@code start}
     *                                   is larger than {@code end}
     */
    @Override
    public void flip(int start, int end) {
        checkRange(start, end);

        if (start == end)
            return;
        
        final int startWordIndex = wordIndex(start);
        final int endWordIndex = wordIndex(end - 1);

        if (startWordIndex == endWordIndex) {
            cardinality -= bitCount(startWordIndex);
            flipOneWord(start, end, startWordIndex);
            cardinality += bitCount(startWordIndex);
        } else {
            cardinality -= bitCount(startWordIndex);
            cardinality -= bitCount(endWordIndex);

            flipMultipleWords(start, end, startWordIndex, endWordIndex);

            cardinality += bitCount(startWordIndex);
            cardinality += bitCount(endWordIndex);

            for (int i = startWordIndex + 1; i < endWordIndex; i++)
                cardinality += (bitCount(i) << 1) - BITS_PER_WORD;
        }

        checkCardinality();
    }

    /**
     * Adds the specified integer to this set.
     *
     * @param n a non-negative integer
     * @throws IndexOutOfBoundsException if the specified integer is negative
     */
    @Override
    public void set(int n) {
        final int wordIndex = wordIndex(n);
        if (wordIndex >= wordsInUse || (words[wordIndex] & (1L << n)) == 0) {
            super.set(n);
            cardinality++;
            checkCardinality();
        }
    }

    /**
     * Adds the integers from the specified {@code start} (inclusive) to the
     * specified {@code end} (exclusive).
     *
     * @param start the first integer to be added
     * @param end   the value after the last integer to be added
     * @throws IndexOutOfBoundsException if {@code start} is negative, or
     *                                   {@code end} is negative, or {@code start}
     *                                   is larger than {@code end}
     */
    @Override
    public void set(int start, int end) {
        checkRange(start, end);

        if (start == end)
            return;
        
        final int startWordIndex = wordIndex(start);
        final int endWordIndex = wordIndex(end - 1);

        if (startWordIndex == endWordIndex) {
            cardinality -= bitCount(startWordIndex);
            setOneWord(start, end, startWordIndex);
            cardinality += bitCount(startWordIndex);
        } else {
            cardinality -= bitCount(startWordIndex);
            cardinality -= bitCount(endWordIndex);

            for (int i = startWordIndex + 1; i < endWordIndex; i++)
                cardinality += BITS_PER_WORD - bitCount(i);

            setMultipleWords(start, end, startWordIndex, endWordIndex);

            cardinality += bitCount(startWordIndex);
            cardinality += bitCount(endWordIndex);
        }

        checkCardinality();
    }

    /**
     * Removes the specified integer.
     *
     * @param n the integer to be removed
     * @throws IndexOutOfBoundsException if the specified integer is negative
     */
    @Override
    public void clear(int n) {
        final int wordIndex = wordIndex(n);
        if (wordIndex < wordsInUse && (words[wordIndex] & (1L << n)) != 0) {
            super.clear(n);
            cardinality--;
            checkCardinality();
        }
    }

    /**
     * Removes the integers from the specified {@code start} (inclusive) to the
     * specified {@code end} (exclusive).
     *
     * @param start the first integer to be removed
     * @param end   value after the last integer to be removed
     * @throws IndexOutOfBoundsException if {@code start} is negative, or
     *                                   {@code end} is negative, or {@code start}
     *                                   is larger than {@code end}
     */
    @Override
    public void clear(int start, int end) {
        checkRange(start, end);

        if (start == end)
            return;
        
        final int startWordIndex = wordIndex(start);
        if (startWordIndex >= wordsInUse)
            return;

        final int endWordIndex;
        final int len = length();
        
        if (end < len) {
            endWordIndex = wordIndex(end - 1);
        } else {
            end = len;
            endWordIndex = wordsInUse - 1;
        }

        if (startWordIndex == endWordIndex) {
            cardinality -= bitCount(startWordIndex);
            clearOneWord(start, end, startWordIndex);
            cardinality += bitCount(startWordIndex);
        } else {
            cardinality -= bitCount(startWordIndex);
            cardinality -= bitCount(endWordIndex);

            for (int i = startWordIndex + 1; i < endWordIndex; i++)
                cardinality -= bitCount(i);

            clearMultipleWords(start, end, startWordIndex, endWordIndex);

            cardinality += bitCount(startWordIndex);
            cardinality += bitCount(endWordIndex);
        }

        checkCardinality();
    }

    /**
     * Removes all of the integers in this set.
     */
    @Override
    public void clear() {
        super.clear();
        cardinality = 0;
    }

    /**
     * Returns a new {@code NaturalsBitSet} composed of bits from this
     * {@code BitSet} from {@code fromIndex} (inclusive) to {@code toIndex}
     * (exclusive).
     *
     * @param fromIndex index of the first bit to include
     * @param toIndex   index after the last bit to include
     * @return a new {@code NaturalsBitSet} from a range of this
     *         {@code NaturalsBitSet}
     * @throws IndexOutOfBoundsException if {@code fromIndex} is negative, or
     *                                   {@code toIndex} is negative, or
     *                                   {@code fromIndex} is larger than
     *                                   {@code toIndex}
     */
    @Override
    public NaturalsBitSet get(int fromIndex, int toIndex) {
        return new NaturalsBitSet(getWords(fromIndex, toIndex));
    }

    /**
     * Returns true if this {@code NaturalsBitSet} contains no integers.
     *
     * @return boolean indicating whether this {@code NaturalsBitSet} is empty
     */
    @Override
    public boolean isEmpty() {
        return cardinality == 0;
    }

    /**
     * Returns the number of integers in this {@code NaturalsBitSet}.
     *
     * @return the number of integers in this {@code NaturalsBitSet}
     */
    @Override
    public int cardinality() {
        return cardinality;
    }

    @Override
    public void and(BitSet set) {
        if (this != set) {
            super.and(set);
            computeCardinality();
            checkCardinality();
        }
    }

    @Override
    public void or(BitSet set) {
        if (this != set) {
            if (set.wordsInUse <= wordsInUse / 2) { // An optimization
                cardinality -= bitCount(0, set.wordsInUse);
                super.or(set);
                cardinality += bitCount(0, set.wordsInUse);
            } else {
                super.or(set);
                computeCardinality();
            }

            checkCardinality();
        }
    }

    @Override
    public void xor(BitSet set) {
        if (this == set) { // An optimization
            clear();
        } else {
            if (set.wordsInUse <= wordsInUse / 2) { // An optimization
                cardinality -= bitCount(0, set.wordsInUse);
                super.xor(set);
                cardinality += bitCount(0, set.wordsInUse);
            } else {
                super.xor(set);
                computeCardinality();
            }

            checkCardinality();
        }
    }

    @Override
    public void andNot(BitSet set) {
        if (this == set) { // An optimization
            clear();
        } else {
            if (set.wordsInUse <= wordsInUse / 2) { // An optimization
                cardinality -= bitCount(0, set.wordsInUse);
                super.andNot(set);
                cardinality += bitCount(0, set.wordsInUse);
            } else {
                super.andNot(set);
                computeCardinality();
            }

            checkCardinality();
        }
    }

    @Override
    public boolean equals(Object obj) {
        // compare the cardinalities if obj is a NaturalsBitSet
        return !((obj instanceof NaturalsBitSet set) && cardinality != set.cardinality) && super.equals(obj);
    }

    /**
     * Cloning this {@code NaturalsBitSet} produces a new {@code NaturalsBitSet}
     * that is equal to it. The clone of the bit set is another bit set that has
     * exactly the same integers as this bit set.
     *
     * @return a clone of this bit set
     * @see #size()
     */
    @Override
    public NaturalsBitSet clone() {
        return (NaturalsBitSet) super.clone();
    }

    /**
     * Reconstitute the {@code NaturalsBitSet} instance from a stream (i.e.,
     * deserialize it).
     */
    @java.io.Serial
    private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
        computeCardinality();
        checkCardinality();
    }

    /**
     * Returns a string representation of this {@code NaturalsBitSet}. For every
     * integer for which this {@code NaturalsBitSet} contains it, the decimal
     * representation of that integer is included in the result. Such integers are
     * listed in order from lowest to highest, separated by ",&nbsp;" (a comma and a
     * space) and surrounded by braces, resulting in the usual mathematical notation
     * for a set of integers.
     *
     * <p>
     * Example:
     * 
     * <pre>
     * BitSet drPepper = new BitSet();
     * </pre>
     * 
     * Now {@code drPepper.toString()} returns "{@code {}}".
     * 
     * <pre>
     * drPepper.set(2);
     * </pre>
     * 
     * Now {@code drPepper.toString()} returns "{@code {2}}".
     * 
     * <pre>
     * drPepper.set(4);
     * drPepper.set(10);
     * </pre>
     * 
     * Now {@code drPepper.toString()} returns "{@code {2, 4, 10}}".
     *
     * @return a string representation of this bit set
     */
    public String toString() {
        final int MAX_INITIAL_CAPACITY = Integer.MAX_VALUE - 8;
        // Avoid overflow in the case of a humongous cardinality
        int initialCapacity = (cardinality <= (MAX_INITIAL_CAPACITY - 2) / 6) ? 6 * cardinality + 2
                : MAX_INITIAL_CAPACITY;
        StringBuilder b = new StringBuilder(initialCapacity);
        b.append('{');

        final int len = length();
        if (len != 0) {
            int i = nextSetBit(0);
            b.append(i++);
            
            if (i != len) {
                i = nextSetBit(i);
                do {
                    int endOfRun = i + 1 < 0 ? len : nextClearBit(i + 1); // avoid overflow
                    
                    do {
                        b.append(", ").append(i++);
                    } while (i != endOfRun);
                    
                    if (i != len)
                        i = nextSetBit(i + 1);
                } while (i != len);
            }
        }

        return b.append('}').toString();
    }
}