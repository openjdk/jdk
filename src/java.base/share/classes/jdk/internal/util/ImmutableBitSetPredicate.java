package jdk.internal.util;

import jdk.internal.ValueBased;
import jdk.internal.vm.annotation.Stable;

import java.util.BitSet;
import java.util.function.IntPredicate;

/**
 * Class for working with immutable BitSets.
 */
@ValueBased
public class ImmutableBitSetPredicate implements IntPredicate {

    @Stable
    private final long[] words;

    private ImmutableBitSetPredicate(BitSet original) {
        // If this class is made public, we need to do
        // a defensive array copy as certain BitSet implementations
        // may return a shared array. The spec says the array should be _new_ though but
        // the consequences might be unspecified for a malicious BitSet.
        this.words = original.toLongArray();
    }

    @Override
    public boolean test(int bitIndex) {
        if (bitIndex < 0)
            throw new IndexOutOfBoundsException("bitIndex < 0: " + bitIndex);

        int wordIndex = wordIndex(bitIndex);
        return (wordIndex < words.length)
                && ((words[wordIndex] & (1L << bitIndex)) != 0);
    }

    /**
     * Given a bit index, return word index containing it.
     */
    private static int wordIndex(int bitIndex) {
        return bitIndex >> 6;
    }

    /**
     * {@return a new {@link IntPredicate } representing the {@link BitSet#get(int)} method applied
     * on an immutable snapshot of the current state of this BitSet}.
     * <p>
     * If the returned predicate is invoked with a {@code bitIndex} that is negative, the predicate
     * will throw an IndexOutOfBoundsException just as the {@link BitSet#get(int)} method would.
     * <p>
     * The method only supports BitSets where the highest bit that is set has an index less than {@link Integer#MAX_VALUE}.
     * <p>
     * Returned predicates are threadsafe and can be used without external synchronisation.
     *
     * @implNote The method is free to return a {@link ValueBased} implementation.
     *
     * @since 22
     */
    public static IntPredicate of(BitSet original) {
        // Do not propagate the Integer.MAX_VALUE issue
        if (Integer.toUnsignedLong(original.length()) > Integer.MAX_VALUE) {
            throw new IllegalArgumentException();
        }
        return new ImmutableBitSetPredicate(original);
    }

}
