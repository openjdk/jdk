// Copyright 2018 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html

// created: 2018may04 Markus W. Scherer

package jdk.internal.icu.util;

import java.util.Arrays;

/**
 * Mutable Unicode code point trie.
 * Fast map from Unicode code points (U+0000..U+10FFFF) to 32-bit integer values.
 * For details see https://icu.unicode.org/design/struct/utrie
 *
 * <p>Setting values (especially ranges) and lookup is fast.
 * The mutable trie is only somewhat space-efficient.
 * It builds a compacted, immutable {@link CodePointTrie}.
 *
 * <p>This trie can be modified while iterating over its contents.
 * For example, it is possible to merge its values with those from another
 * set of ranges (e.g., another @{link CodePointMap}):
 * Iterate over those source ranges; for each of them iterate over this trie;
 * add the source value into the value of each trie range.
 *
 * @stable ICU 63
 */
public final class MutableCodePointTrie extends CodePointMap implements Cloneable {
    /**
     * Constructs a mutable trie that initially maps each Unicode code point to the same value.
     * It uses 32-bit data values until
     * {@link #buildImmutable(jdk.internal.icu.util.CodePointTrie.Type, jdk.internal.icu.util.CodePointTrie.ValueWidth)}
     * is called.
     * buildImmutable() takes a valueWidth parameter which
     * determines the number of bits in the data value in the resulting {@link CodePointTrie}.
     *
     * @param initialValue the initial value that is set for all code points
     * @param errorValue the value for out-of-range code points and ill-formed UTF-8/16
     * @stable ICU 63
     */
    public MutableCodePointTrie(int initialValue, int errorValue) {
        index = new int[BMP_I_LIMIT];
        index3NullOffset = -1;
        data = new int[INITIAL_DATA_LENGTH];
        dataNullOffset = -1;
        origInitialValue = initialValue;
        this.initialValue = initialValue;
        this.errorValue = errorValue;
        highValue = initialValue;
    }

    /**
     * Clones this mutable trie.
     *
     * @return the clone
     * @stable ICU 63
     */
    @Override
    public MutableCodePointTrie clone() {
        try {
            MutableCodePointTrie builder = (MutableCodePointTrie) super.clone();
            int iCapacity = highStart <= BMP_LIMIT ? BMP_I_LIMIT : I_LIMIT;
            builder.index = new int[iCapacity];
            builder.flags = new byte[UNICODE_LIMIT >> CodePointTrie.SHIFT_3];
            for (int i = 0, iLimit = highStart >> CodePointTrie.SHIFT_3; i < iLimit; ++i) {
                builder.index[i] = index[i];
                builder.flags[i] = flags[i];
            }
            builder.index3NullOffset = index3NullOffset;
            builder.data = data.clone();
            builder.dataLength = dataLength;
            builder.dataNullOffset = dataNullOffset;
            builder.origInitialValue = origInitialValue;
            builder.initialValue = initialValue;
            builder.errorValue = errorValue;
            builder.highStart = highStart;
            builder.highValue = highValue;
            assert index16 == null;
            return builder;
        } catch (CloneNotSupportedException ignored) {
            // Unreachable: Cloning *is* supported.
            return null;
        }
    }

    /**
     * Creates a mutable trie with the same contents as the {@link CodePointMap}.
     *
     * @param map the source map or trie
     * @return the mutable trie
     * @stable ICU 63
     */
    public static MutableCodePointTrie fromCodePointMap(CodePointMap map) {
        // TODO: Consider special code branch for map instanceof CodePointTrie?
        // Use the highValue as the initialValue to reduce the highStart.
        int errorValue = map.get(-1);
        int initialValue = map.get(MAX_UNICODE);
        MutableCodePointTrie mutableTrie = new MutableCodePointTrie(initialValue, errorValue);
        CodePointMap.Range range = new CodePointMap.Range();
        int start = 0;
        while (map.getRange(start, null, range)) {
            int end = range.getEnd();
            int value = range.getValue();
            if (value != initialValue) {
                if (start == end) {
                    mutableTrie.set(start, value);
                } else {
                    mutableTrie.setRange(start, end, value);
                }
            }
            start = end + 1;
        }
        return mutableTrie;
    }

    private void clear() {
        index3NullOffset = dataNullOffset = -1;
        dataLength = 0;
        highValue = initialValue = origInitialValue;
        highStart = 0;
        index16 = null;
    }

    /**
     * {@inheritDoc}
     * @stable ICU 63
     */
    @Override
    public int get(int c) {
        if (c < 0 || MAX_UNICODE < c) {
            return errorValue;
        }
        if (c >= highStart) {
            return highValue;
        }
        int i = c >> CodePointTrie.SHIFT_3;
        if (flags[i] == ALL_SAME) {
            return index[i];
        } else {
            return data[index[i] + (c & CodePointTrie.SMALL_DATA_MASK)];
        }
    }

    private static final int maybeFilterValue(int value, int initialValue, int nullValue,
            ValueFilter filter) {
        if (value == initialValue) {
            value = nullValue;
        } else if (filter != null) {
            value = filter.apply(value);
        }
        return value;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The trie can be modified between calls to this function.
     *
     * @stable ICU 63
     */
    @Override
    public boolean getRange(int start, CodePointTrie.ValueFilter filter,
            CodePointTrie.Range range) {
        if (start < 0 || MAX_UNICODE < start) {
            return false;
        }
        if (start >= highStart) {
            int value = highValue;
            if (filter != null) { value = filter.apply(value); }
            range.set(start, MAX_UNICODE, value);
            return true;
        }
        int nullValue = initialValue;
        if (filter != null) { nullValue = filter.apply(nullValue); }
        int c = start;
        // Initialize to make compiler happy. Real value when haveValue is true.
        int trieValue = 0, value = 0;
        boolean haveValue = false;
        int i = c >> CodePointTrie.SHIFT_3;
        do {
            if (flags[i] == ALL_SAME) {
                int trieValue2 = index[i];
                if (haveValue) {
                    if (trieValue2 != trieValue) {
                        if (filter == null ||
                                maybeFilterValue(trieValue2, initialValue, nullValue,
                                        filter) != value) {
                            range.set(start, c - 1, value);
                            return true;
                        }
                        trieValue = trieValue2;  // may or may not help
                    }
                } else {
                    trieValue = trieValue2;
                    value = maybeFilterValue(trieValue2, initialValue, nullValue, filter);
                    haveValue = true;
                }
                c = (c + CodePointTrie.SMALL_DATA_BLOCK_LENGTH) & ~CodePointTrie.SMALL_DATA_MASK;
            } else /* MIXED */ {
                int di = index[i] + (c & CodePointTrie.SMALL_DATA_MASK);
                int trieValue2 = data[di];
                if (haveValue) {
                    if (trieValue2 != trieValue) {
                        if (filter == null ||
                                maybeFilterValue(trieValue2, initialValue, nullValue,
                                        filter) != value) {
                            range.set(start, c - 1, value);
                            return true;
                        }
                        trieValue = trieValue2;  // may or may not help
                    }
                } else {
                    trieValue = trieValue2;
                    value = maybeFilterValue(trieValue2, initialValue, nullValue, filter);
                    haveValue = true;
                }
                while ((++c & CodePointTrie.SMALL_DATA_MASK) != 0) {
                    trieValue2 = data[++di];
                    if (trieValue2 != trieValue) {
                        if (filter == null ||
                                maybeFilterValue(trieValue2, initialValue, nullValue,
                                        filter) != value) {
                            range.set(start, c - 1, value);
                            return true;
                        }
                        trieValue = trieValue2;  // may or may not help
                    }
                }
            }
            ++i;
        } while (c < highStart);
        assert(haveValue);
        if (maybeFilterValue(highValue, initialValue, nullValue, filter) != value) {
            range.set(start, c - 1, value);
        } else {
            range.set(start, MAX_UNICODE, value);
        }
        return true;
    }

    private void writeBlock(int block, int value) {
        int limit = block + CodePointTrie.SMALL_DATA_BLOCK_LENGTH;
        Arrays.fill(data, block, limit, value);
    }

    /**
     * Sets a value for a code point.
     *
     * @param c the code point
     * @param value the value
     * @stable ICU 63
     */
    public void set(int c, int value) {
        if (c < 0 || MAX_UNICODE < c) {
            throw new IllegalArgumentException("invalid code point");
        }

        ensureHighStart(c);
        int block = getDataBlock(c >> CodePointTrie.SHIFT_3);
        data[block + (c & CodePointTrie.SMALL_DATA_MASK)] = value;
    }

    private void fillBlock(int block, int start, int limit, int value) {
        Arrays.fill(data, block + start, block + limit, value);
    }

    /**
     * Sets a value for each code point [start..end].
     * Faster and more space-efficient than setting the value for each code point separately.
     *
     * @param start the first code point to get the value
     * @param end the last code point to get the value (inclusive)
     * @param value the value
     * @stable ICU 63
     */
    public void setRange(int start, int end, int value) {
        if (start < 0 || MAX_UNICODE < start || end < 0 || MAX_UNICODE < end || start > end) {
            throw new IllegalArgumentException("invalid code point range");
        }
        ensureHighStart(end);

        int limit = end + 1;
        if ((start & CodePointTrie.SMALL_DATA_MASK) != 0) {
            // Set partial block at [start..following block boundary[.
            int block = getDataBlock(start >> CodePointTrie.SHIFT_3);
            int nextStart = (start + CodePointTrie.SMALL_DATA_MASK) & ~CodePointTrie.SMALL_DATA_MASK;
            if (nextStart <= limit) {
                fillBlock(block, start & CodePointTrie.SMALL_DATA_MASK,
                          CodePointTrie.SMALL_DATA_BLOCK_LENGTH, value);
                start = nextStart;
            } else {
                fillBlock(block, start & CodePointTrie.SMALL_DATA_MASK,
                          limit & CodePointTrie.SMALL_DATA_MASK, value);
                return;
            }
        }

        // Number of positions in the last, partial block.
        int rest = limit & CodePointTrie.SMALL_DATA_MASK;

        // Round down limit to a block boundary.
        limit &= ~CodePointTrie.SMALL_DATA_MASK;

        // Iterate over all-value blocks.
        while (start < limit) {
            int i = start >> CodePointTrie.SHIFT_3;
            if (flags[i] == ALL_SAME) {
                index[i] = value;
            } else /* MIXED */ {
                fillBlock(index[i], 0, CodePointTrie.SMALL_DATA_BLOCK_LENGTH, value);
            }
            start += CodePointTrie.SMALL_DATA_BLOCK_LENGTH;
        }

        if (rest > 0) {
            // Set partial block at [last block boundary..limit[.
            int block = getDataBlock(start >> CodePointTrie.SHIFT_3);
            fillBlock(block, 0, rest, value);
        }
    }

    /**
     * Compacts the data and builds an immutable {@link CodePointTrie} according to the parameters.
     * After this, the mutable trie will be empty.
     *
     * <p>The mutable trie stores 32-bit values until buildImmutable() is called.
     * If values shorter than 32 bits are to be stored in the immutable trie,
     * then the upper bits are discarded.
     * For example, when the mutable trie contains values 0x81, -0x7f, and 0xa581,
     * and the value width is 8 bits, then each of these is stored as 0x81
     * and the immutable trie will return that as an unsigned value.
     * (Some implementations may want to make productive temporary use of the upper bits
     * until buildImmutable() discards them.)
     *
     * <p>Not every possible set of mappings can be built into a CodePointTrie,
     * because of limitations resulting from speed and space optimizations.
     * Every Unicode assigned character can be mapped to a unique value.
     * Typical data yields data structures far smaller than the limitations.
     *
     * <p>It is possible to construct extremely unusual mappings that exceed the
     * data structure limits.
     * In such a case this function will throw an exception.
     *
     * @param type selects the trie type
     * @param valueWidth selects the number of bits in a trie data value; if smaller than 32 bits,
     *                   then the values stored in the trie will be truncated first
     *
     * @see #fromCodePointMap(CodePointMap)
     * @stable ICU 63
     */
    public CodePointTrie buildImmutable(CodePointTrie.Type type, CodePointTrie.ValueWidth valueWidth) {
        if (type == null || valueWidth == null) {
            throw new IllegalArgumentException("The type and valueWidth must be specified.");
        }

        try {
            return build(type, valueWidth);
        } finally {
            clear();
        }
    }

    private static final int MAX_UNICODE = 0x10ffff;

    private static final int UNICODE_LIMIT = 0x110000;
    private static final int BMP_LIMIT = 0x10000;
    private static final int ASCII_LIMIT = 0x80;

    private static final int I_LIMIT = UNICODE_LIMIT >> CodePointTrie.SHIFT_3;
    private static final int BMP_I_LIMIT = BMP_LIMIT >> CodePointTrie.SHIFT_3;
    private static final int ASCII_I_LIMIT = ASCII_LIMIT >> CodePointTrie.SHIFT_3;

    private static final int SMALL_DATA_BLOCKS_PER_BMP_BLOCK = (1 << (CodePointTrie.FAST_SHIFT - CodePointTrie.SHIFT_3));

    // Flag values for data blocks.
    private static final byte ALL_SAME = 0;
    private static final byte MIXED = 1;
    private static final byte SAME_AS = 2;

    /** Start with allocation of 16k data entries. */
    private static final int INITIAL_DATA_LENGTH = (1 << 14);

    /** Grow about 8x each time. */
    private static final int MEDIUM_DATA_LENGTH = (1 << 17);

    /**
     * Maximum length of the build-time data array.
     * One entry per 0x110000 code points.
     */
    private static final int MAX_DATA_LENGTH = UNICODE_LIMIT;

    // Flag values for index-3 blocks while compacting/building.
    private static final byte I3_NULL = 0;
    private static final byte I3_BMP = 1;
    private static final byte I3_16 = 2;
    private static final byte I3_18 = 3;

    private static final int INDEX_3_18BIT_BLOCK_LENGTH = CodePointTrie.INDEX_3_BLOCK_LENGTH + CodePointTrie.INDEX_3_BLOCK_LENGTH / 8;

    private int[] index;
    private int index3NullOffset;
    private int[] data;
    private int dataLength;
    private int dataNullOffset;

    private int origInitialValue;
    private int initialValue;
    private int errorValue;
    private int highStart;
    private int highValue;

    /** Temporary array while building the final data. */
    private char[] index16;
    private byte[] flags = new byte[UNICODE_LIMIT >> CodePointTrie.SHIFT_3];

    private void ensureHighStart(int c) {
        if (c >= highStart) {
            // Round up to a CodePointTrie.CP_PER_INDEX_2_ENTRY boundary to simplify compaction.
            c = (c + CodePointTrie.CP_PER_INDEX_2_ENTRY) & ~(CodePointTrie.CP_PER_INDEX_2_ENTRY - 1);
            int i = highStart >> CodePointTrie.SHIFT_3;
            int iLimit = c >> CodePointTrie.SHIFT_3;
            if (iLimit > index.length) {
                int[] newIndex = new int[I_LIMIT];
                for (int j = 0; j < i; ++j) { newIndex[j] = index[j]; }
                index = newIndex;
            }
            do {
                flags[i] = ALL_SAME;
                index[i] = initialValue;
            } while(++i < iLimit);
            highStart = c;
        }
    }

    private int allocDataBlock(int blockLength) {
        int newBlock = dataLength;
        int newTop = newBlock + blockLength;
        if (newTop > data.length) {
            int capacity;
            if (data.length < MEDIUM_DATA_LENGTH) {
                capacity = MEDIUM_DATA_LENGTH;
            } else if (data.length < MAX_DATA_LENGTH) {
                capacity = MAX_DATA_LENGTH;
            } else {
                // Should never occur.
                // Either MAX_DATA_LENGTH is incorrect,
                // or the code writes more values than should be possible.
                throw new AssertionError();
            }
            int[] newData = new int[capacity];
            for (int j = 0; j < dataLength; ++j) { newData[j] = data[j]; }
            data = newData;
        }
        dataLength = newTop;
        return newBlock;
    }

    /**
     * No error checking for illegal arguments.
     * The Java version always returns non-negative values.
     */
    private int getDataBlock(int i) {
        if (flags[i] == MIXED) {
            return index[i];
        }
        if (i < BMP_I_LIMIT) {
            int newBlock = allocDataBlock(CodePointTrie.FAST_DATA_BLOCK_LENGTH);
            int iStart = i & ~(SMALL_DATA_BLOCKS_PER_BMP_BLOCK -1);
            int iLimit = iStart + SMALL_DATA_BLOCKS_PER_BMP_BLOCK;
            do {
                assert(flags[iStart] == ALL_SAME);
                writeBlock(newBlock, index[iStart]);
                flags[iStart] = MIXED;
                index[iStart++] = newBlock;
                newBlock += CodePointTrie.SMALL_DATA_BLOCK_LENGTH;
            } while (iStart < iLimit);
            return index[i];
        } else {
            int newBlock = allocDataBlock(CodePointTrie.SMALL_DATA_BLOCK_LENGTH);
            if (newBlock < 0) { return newBlock; }
            writeBlock(newBlock, index[i]);
            flags[i] = MIXED;
            index[i] = newBlock;
            return newBlock;
        }
    }

    // compaction --------------------------------------------------------------

    private void maskValues(int mask) {
        initialValue &= mask;
        errorValue &= mask;
        highValue &= mask;
        int iLimit = highStart >> CodePointTrie.SHIFT_3;
        for (int i = 0; i < iLimit; ++i) {
            if (flags[i] == ALL_SAME) {
                index[i] &= mask;
            }
        }
        for (int i = 0; i < dataLength; ++i) {
            data[i] &= mask;
        }
    }

    private static boolean equalBlocks(int[] s, int si, int[] t, int ti, int length) {
        while (length > 0 && s[si] == t[ti]) {
            ++si;
            ++ti;
            --length;
        }
        return length == 0;
    }

    private static boolean equalBlocks(char[] s, int si, int[] t, int ti, int length) {
        while (length > 0 && s[si] == t[ti]) {
            ++si;
            ++ti;
            --length;
        }
        return length == 0;
    }

    private static boolean equalBlocks(char[] s, int si, char[] t, int ti, int length) {
        while (length > 0 && s[si] == t[ti]) {
            ++si;
            ++ti;
            --length;
        }
        return length == 0;
    }

    private static boolean allValuesSameAs(int[] p, int pi, int length, int value) {
        int pLimit = pi + length;
        while (pi < pLimit && p[pi] == value) { ++pi; }
        return pi == pLimit;
    }

    /** Search for an identical block. */
    private static int findSameBlock(char[] p, int pStart, int length,
            char[] q, int qStart, int blockLength) {
        // Ensure that we do not even partially get past length.
        length -= blockLength;

        while (pStart <= length) {
            if (equalBlocks(p, pStart, q, qStart, blockLength)) {
                return pStart;
            }
            ++pStart;
        }
        return -1;
    }

    private static int findAllSameBlock(int[] p, int start, int limit,
            int value, int blockLength) {
        // Ensure that we do not even partially get past limit.
        limit -= blockLength;

        for (int block = start; block <= limit; ++block) {
            if (p[block] == value) {
                for (int i = 1;; ++i) {
                    if (i == blockLength) {
                        return block;
                    }
                    if (p[block + i] != value) {
                        block += i;
                        break;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Look for maximum overlap of the beginning of the other block
     * with the previous, adjacent block.
     */
    private static int getOverlap(int[] p, int length, int[] q, int qStart, int blockLength) {
        int overlap = blockLength - 1;
        assert(overlap <= length);
        while (overlap > 0 && !equalBlocks(p, length - overlap, q, qStart, overlap)) {
            --overlap;
        }
        return overlap;
    }

    private static int getOverlap(char[] p, int length, int[] q, int qStart, int blockLength) {
        int overlap = blockLength - 1;
        assert(overlap <= length);
        while (overlap > 0 && !equalBlocks(p, length - overlap, q, qStart, overlap)) {
            --overlap;
        }
        return overlap;
    }

    private static int getOverlap(char[] p, int length, char[] q, int qStart, int blockLength) {
        int overlap = blockLength - 1;
        assert(overlap <= length);
        while (overlap > 0 && !equalBlocks(p, length - overlap, q, qStart, overlap)) {
            --overlap;
        }
        return overlap;
    }

    private static int getAllSameOverlap(int[] p, int length, int value, int blockLength) {
        int min = length - (blockLength - 1);
        int i = length;
        while (min < i && p[i - 1] == value) { --i; }
        return length - i;
    }

    private static boolean isStartOfSomeFastBlock(int dataOffset, int[] index, int fastILimit) {
        for (int i = 0; i < fastILimit; i += SMALL_DATA_BLOCKS_PER_BMP_BLOCK) {
            if (index[i] == dataOffset) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the start of the last range in the trie by enumerating backward.
     * Indexes for code points higher than this will be omitted.
     */
    private int findHighStart() {
        int i = highStart >> CodePointTrie.SHIFT_3;
        while (i > 0) {
            boolean match;
            if (flags[--i] == ALL_SAME) {
                match = index[i] == highValue;
            } else /* MIXED */ {
                int p = index[i];
                for (int j = 0;; ++j) {
                    if (j == CodePointTrie.SMALL_DATA_BLOCK_LENGTH) {
                        match = true;
                        break;
                    }
                    if (data[p + j] != highValue) {
                        match = false;
                        break;
                    }
                }
            }
            if (!match) {
                return (i + 1) << CodePointTrie.SHIFT_3;
            }
        }
        return 0;
    }

    private static final class AllSameBlocks {
        static final int NEW_UNIQUE = -1;
        static final int OVERFLOW = -2;

        AllSameBlocks() {
            mostRecent = -1;
        }

        int findOrAdd(int index, int count, int value) {
            if (mostRecent >= 0 && values[mostRecent] == value) {
                refCounts[mostRecent] += count;
                return indexes[mostRecent];
            }
            for (int i = 0; i < length; ++i) {
                if (values[i] == value) {
                    mostRecent = i;
                    refCounts[i] += count;
                    return indexes[i];
                }
            }
            if (length == CAPACITY) {
                return OVERFLOW;
            }
            mostRecent = length;
            indexes[length] = index;
            values[length] = value;
            refCounts[length++] = count;
            return NEW_UNIQUE;
        }

        /** Replaces the block which has the lowest reference count. */
        void add(int index, int count, int value) {
            assert(length == CAPACITY);
            int least = -1;
            int leastCount = I_LIMIT;
            for (int i = 0; i < length; ++i) {
                assert(values[i] != value);
                if (refCounts[i] < leastCount) {
                    least = i;
                    leastCount = refCounts[i];
                }
            }
            assert(least >= 0);
            mostRecent = least;
            indexes[least] = index;
            values[least] = value;
            refCounts[least] = count;
        }

        int findMostUsed() {
            if (length == 0) { return -1; }
            int max = -1;
            int maxCount = 0;
            for (int i = 0; i < length; ++i) {
                if (refCounts[i] > maxCount) {
                    max = i;
                    maxCount = refCounts[i];
                }
            }
            return indexes[max];
        }

        private static final int CAPACITY = 32;

        private int length;
        private int mostRecent;

        private int[] indexes = new int[CAPACITY];
        private int[] values = new int[CAPACITY];
        private int[] refCounts = new int[CAPACITY];
    }

    // Custom hash table for mixed-value blocks to be found anywhere in the
    // compacted data or index so far.
    private static final class MixedBlocks {
        void init(int maxLength, int newBlockLength) {
            // We store actual data indexes + 1 to reserve 0 for empty entries.
            int maxDataIndex = maxLength - newBlockLength + 1;
            int newLength;
            if (maxDataIndex <= 0xfff) {  // 4k
                newLength = 6007;
                shift = 12;
                mask = 0xfff;
            } else if (maxDataIndex <= 0x7fff) {  // 32k
                newLength = 50021;
                shift = 15;
                mask = 0x7fff;
            } else if (maxDataIndex <= 0x1ffff) {  // 128k
                newLength = 200003;
                shift = 17;
                mask = 0x1ffff;
            } else {
                // maxDataIndex up to around MAX_DATA_LENGTH, ca. 1.1M
                newLength = 1500007;
                shift = 21;
                mask = 0x1fffff;
            }
            if (table == null || newLength > table.length) {
                table = new int[newLength];
            } else {
                Arrays.fill(table, 0, newLength, 0);
            }
            length = newLength;

            blockLength = newBlockLength;
        }

        void extend(int[] data, int minStart, int prevDataLength, int newDataLength) {
            int start = prevDataLength - blockLength;
            if (start >= minStart) {
                ++start;  // Skip the last block that we added last time.
            } else {
                start = minStart;  // Begin with the first full block.
            }
            for (int end = newDataLength - blockLength; start <= end; ++start) {
                int hashCode = makeHashCode(data, start);
                addEntry(data, null, start, hashCode, start);
            }
        }

        void extend(char[] data, int minStart, int prevDataLength, int newDataLength) {
            int start = prevDataLength - blockLength;
            if (start >= minStart) {
                ++start;  // Skip the last block that we added last time.
            } else {
                start = minStart;  // Begin with the first full block.
            }
            for (int end = newDataLength - blockLength; start <= end; ++start) {
                int hashCode = makeHashCode(data, start);
                addEntry(null, data, start, hashCode, start);
            }
        }

        int findBlock(int[] data, int[] blockData, int blockStart) {
            int hashCode = makeHashCode(blockData, blockStart);
            int entryIndex = findEntry(data, null, blockData, null, blockStart, hashCode);
            if (entryIndex >= 0) {
                return (table[entryIndex] & mask) - 1;
            } else {
                return -1;
            }
        }

        int findBlock(char[] data, int[] blockData, int blockStart) {
            int hashCode = makeHashCode(blockData, blockStart);
            int entryIndex = findEntry(null, data, blockData, null, blockStart, hashCode);
            if (entryIndex >= 0) {
                return (table[entryIndex] & mask) - 1;
            } else {
                return -1;
            }
        }

        int findBlock(char[] data, char[] blockData, int blockStart) {
            int hashCode = makeHashCode(blockData, blockStart);
            int entryIndex = findEntry(null, data, null, blockData, blockStart, hashCode);
            if (entryIndex >= 0) {
                return (table[entryIndex] & mask) - 1;
            } else {
                return -1;
            }
        }

        int findAllSameBlock(int[] data, int blockValue) {
            int hashCode = makeHashCode(blockValue);
            int entryIndex = findEntry(data, blockValue, hashCode);
            if (entryIndex >= 0) {
                return (table[entryIndex] & mask) - 1;
            } else {
                return -1;
            }
        }

        private int makeHashCode(int[] blockData, int blockStart) {
            int blockLimit = blockStart + blockLength;
            int hashCode = blockData[blockStart++];
            do {
                hashCode = 37 * hashCode + blockData[blockStart++];
            } while (blockStart < blockLimit);
            return hashCode;
        }

        private int makeHashCode(char[] blockData, int blockStart) {
            int blockLimit = blockStart + blockLength;
            int hashCode = blockData[blockStart++];
            do {
                hashCode = 37 * hashCode + blockData[blockStart++];
            } while (blockStart < blockLimit);
            return hashCode;
        }

        private int makeHashCode(int blockValue) {
            int hashCode = blockValue;
            for (int i = 1; i < blockLength; ++i) {
                hashCode = 37 * hashCode + blockValue;
            }
            return hashCode;
        }

        private void addEntry(int[] data32, char[] data16, int blockStart, int hashCode, int dataIndex) {
            assert(0 <= dataIndex && dataIndex < mask);
            int entryIndex = findEntry(data32, data16, data32, data16, blockStart, hashCode);
            if (entryIndex < 0) {
                table[~entryIndex] = (hashCode << shift) | (dataIndex + 1);
            }
        }

        private int findEntry(int[] data32, char[] data16,
                int[] blockData32, char[] blockData16, int blockStart, int hashCode) {
            int shiftedHashCode = hashCode << shift;
            int initialEntryIndex = modulo(hashCode, length - 1) + 1;  // 1..length-1
            for (int entryIndex = initialEntryIndex;;) {
                int entry = table[entryIndex];
                if (entry == 0) {
                    return ~entryIndex;
                }
                if ((entry & ~mask) == shiftedHashCode) {
                    int dataIndex = (entry & mask) - 1;
                    if (data32 != null ?
                            equalBlocks(data32, dataIndex, blockData32, blockStart, blockLength) :
                            blockData32 != null ?
                                equalBlocks(data16, dataIndex, blockData32, blockStart, blockLength) :
                                equalBlocks(data16, dataIndex, blockData16, blockStart, blockLength)) {
                        return entryIndex;
                    }
                }
                entryIndex = nextIndex(initialEntryIndex, entryIndex);
            }
        }

        private int findEntry(int[] data, int blockValue, int hashCode) {
            int shiftedHashCode = hashCode << shift;
            int initialEntryIndex = modulo(hashCode, length - 1) + 1;  // 1..length-1
            for (int entryIndex = initialEntryIndex;;) {
                int entry = table[entryIndex];
                if (entry == 0) {
                    return ~entryIndex;
                }
                if ((entry & ~mask) == shiftedHashCode) {
                    int dataIndex = (entry & mask) - 1;
                    if (allValuesSameAs(data, dataIndex, blockLength, blockValue)) {
                        return entryIndex;
                    }
                }
                entryIndex = nextIndex(initialEntryIndex, entryIndex);
            }
        }

        private int nextIndex(int initialEntryIndex, int entryIndex) {
            // U_ASSERT(0 < initialEntryIndex && initialEntryIndex < length);
            return (entryIndex + initialEntryIndex) % length;
        }

        /** Ensures non-negative n % m (that is 0..m-1). */
        private int modulo(int n, int m) {
            int i = n % m;
            if (i < 0) {
                i += m;
            }
            return i;
        }

        // Hash table.
        // The length is a prime number, larger than the maximum data length.
        // The "shift" lower bits store a data index + 1.
        // The remaining upper bits store a partial hashCode of the block data values.
        private int[] table;
        private int length;
        private int shift;
        private int mask;

        private int blockLength;
    }

    private int compactWholeDataBlocks(int fastILimit, AllSameBlocks allSameBlocks) {
        // ASCII data will be stored as a linear table, even if the following code
        // does not yet count it that way.
        int newDataCapacity = ASCII_LIMIT;
        // Add room for a small data null block in case it would match the start of
        // a fast data block where dataNullOffset must not be set in that case.
        newDataCapacity += CodePointTrie.SMALL_DATA_BLOCK_LENGTH;
        // Add room for special values (errorValue, highValue) and padding.
        newDataCapacity += 4;
        int iLimit = highStart >> CodePointTrie.SHIFT_3;
        int blockLength = CodePointTrie.FAST_DATA_BLOCK_LENGTH;
        int inc = SMALL_DATA_BLOCKS_PER_BMP_BLOCK;
        for (int i = 0; i < iLimit; i += inc) {
            if (i == fastILimit) {
                blockLength = CodePointTrie.SMALL_DATA_BLOCK_LENGTH;
                inc = 1;
            }
            int value = index[i];
            if (flags[i] == MIXED) {
                // Really mixed?
                int p = value;
                value = data[p];
                if (allValuesSameAs(data, p + 1, blockLength - 1, value)) {
                    flags[i] = ALL_SAME;
                    index[i] = value;
                    // Fall through to ALL_SAME handling.
                } else {
                    newDataCapacity += blockLength;
                    continue;
                }
            } else {
                assert(flags[i] == ALL_SAME);
                if (inc > 1) {
                    // Do all of the fast-range data block's ALL_SAME parts have the same value?
                    boolean allSame = true;
                    int next_i = i + inc;
                    for (int j = i + 1; j < next_i; ++j) {
                        assert(flags[j] == ALL_SAME);
                        if (index[j] != value) {
                            allSame = false;
                            break;
                        }
                    }
                    if (!allSame) {
                        // Turn it into a MIXED block.
                        if (getDataBlock(i) < 0) {
                            return -1;
                        }
                        newDataCapacity += blockLength;
                        continue;
                    }
                }
            }
            // Is there another ALL_SAME block with the same value?
            int other = allSameBlocks.findOrAdd(i, inc, value);
            if (other == AllSameBlocks.OVERFLOW) {
                // The fixed-size array overflowed. Slow check for a duplicate block.
                int jInc = SMALL_DATA_BLOCKS_PER_BMP_BLOCK;
                for (int j = 0;; j += jInc) {
                    if (j == i) {
                        allSameBlocks.add(i, inc, value);
                        break;
                    }
                    if (j == fastILimit) {
                        jInc = 1;
                    }
                    if (flags[j] == ALL_SAME && index[j] == value) {
                        allSameBlocks.add(j, jInc + inc, value);
                        other = j;
                        break;
                        // We could keep counting blocks with the same value
                        // before we add the first one, which may improve compaction in rare cases,
                        // but it would make it slower.
                    }
                }
            }
            if (other >= 0) {
                flags[i] = SAME_AS;
                index[i] = other;
            } else {
                // New unique same-value block.
                newDataCapacity += blockLength;
            }
        }
        return newDataCapacity;
    }

    /**
     * Compacts a build-time trie.
     *
     * The compaction
     * - removes blocks that are identical with earlier ones
     * - overlaps each new non-duplicate block as much as possible with the previously-written one
     * - works with fast-range data blocks whose length is a multiple of that of
     *   higher-code-point data blocks
     *
     * It does not try to find an optimal order of writing, deduplicating, and overlapping blocks.
     */
    private int compactData(
            int fastILimit, int[] newData, int dataNullIndex, MixedBlocks mixedBlocks) {
        // The linear ASCII data has been copied into newData already.
        int newDataLength = 0;
        for (int i = 0; newDataLength < ASCII_LIMIT;
                newDataLength += CodePointTrie.FAST_DATA_BLOCK_LENGTH, i += SMALL_DATA_BLOCKS_PER_BMP_BLOCK) {
            index[i] = newDataLength;
        }

        int blockLength = CodePointTrie.FAST_DATA_BLOCK_LENGTH;
        mixedBlocks.init(newData.length, blockLength);
        mixedBlocks.extend(newData, 0, 0, newDataLength);

        int iLimit = highStart >> CodePointTrie.SHIFT_3;
        int inc = SMALL_DATA_BLOCKS_PER_BMP_BLOCK;
        int fastLength = 0;
        for (int i = ASCII_I_LIMIT; i < iLimit; i += inc) {
            if (i == fastILimit) {
                blockLength = CodePointTrie.SMALL_DATA_BLOCK_LENGTH;
                inc = 1;
                fastLength = newDataLength;
                mixedBlocks.init(newData.length, blockLength);
                mixedBlocks.extend(newData, 0, 0, newDataLength);
            }
            if (flags[i] == ALL_SAME) {
                int value = index[i];
                // Find an earlier part of the data array of length blockLength
                // that is filled with this value.
                int n = mixedBlocks.findAllSameBlock(newData, value);
                // If we find a match, and the current block is the data null block,
                // and it is not a fast block but matches the start of a fast block,
                // then we need to continue looking.
                // This is because this small block is shorter than the fast block,
                // and not all of the rest of the fast block is filled with this value.
                // Otherwise trie.getRange() would detect that the fast block starts at
                // dataNullOffset and assume incorrectly that it is filled with the null value.
                while (n >= 0 && i == dataNullIndex && i >= fastILimit && n < fastLength &&
                        isStartOfSomeFastBlock(n, index, fastILimit)) {
                    n = findAllSameBlock(newData, n + 1, newDataLength, value, blockLength);
                }
                if (n >= 0) {
                    index[i] = n;
                } else {
                    n = getAllSameOverlap(newData, newDataLength, value, blockLength);
                    index[i] = newDataLength - n;
                    int prevDataLength = newDataLength;
                    while (n < blockLength) {
                        newData[newDataLength++] = value;
                        ++n;
                    }
                    mixedBlocks.extend(newData, 0, prevDataLength, newDataLength);
                }
            } else if (flags[i] == MIXED) {
                int block = index[i];
                int n = mixedBlocks.findBlock(newData, data, block);
                if (n >= 0) {
                    index[i] = n;
                } else {
                    n = getOverlap(newData, newDataLength, data, block, blockLength);
                    index[i] = newDataLength - n;
                    int prevDataLength = newDataLength;
                    while (n < blockLength) {
                        newData[newDataLength++] = data[block + n++];
                    }
                    mixedBlocks.extend(newData, 0, prevDataLength, newDataLength);
                }
            } else /* SAME_AS */ {
                int j = index[i];
                index[i] = index[j];
            }
        }

        return newDataLength;
    }

    private int compactIndex(int fastILimit, MixedBlocks mixedBlocks) {
        int fastIndexLength = fastILimit >> (CodePointTrie.FAST_SHIFT - CodePointTrie.SHIFT_3);
        if ((highStart >> CodePointTrie.FAST_SHIFT) <= fastIndexLength) {
            // Only the linear fast index, no multi-stage index tables.
            index3NullOffset = CodePointTrie.NO_INDEX3_NULL_OFFSET;
            return fastIndexLength;
        }

        // Condense the fast index table.
        // Also, does it contain an index-3 block with all dataNullOffset?
        char[] fastIndex = new char[fastIndexLength];
        int i3FirstNull = -1;
        for (int i = 0, j = 0; i < fastILimit; ++j) {
            int i3 = index[i];
            fastIndex[j] = (char)i3;
            if (i3 == dataNullOffset) {
                if (i3FirstNull < 0) {
                    i3FirstNull = j;
                } else if (index3NullOffset < 0 &&
                        (j - i3FirstNull + 1) == CodePointTrie.INDEX_3_BLOCK_LENGTH) {
                    index3NullOffset = i3FirstNull;
                }
            } else {
                i3FirstNull = -1;
            }
            // Set the index entries that compactData() skipped.
            // Needed when the multi-stage index covers the fast index range as well.
            int iNext = i + SMALL_DATA_BLOCKS_PER_BMP_BLOCK;
            while (++i < iNext) {
                i3 += CodePointTrie.SMALL_DATA_BLOCK_LENGTH;
                index[i] = i3;
            }
        }

        mixedBlocks.init(fastIndexLength, CodePointTrie.INDEX_3_BLOCK_LENGTH);
        mixedBlocks.extend(fastIndex, 0, 0, fastIndexLength);

        // Examine index-3 blocks. For each determine one of:
        // - same as the index-3 null block
        // - same as a fast-index block
        // - 16-bit indexes
        // - 18-bit indexes
        // We store this in the first flags entry for the index-3 block.
        //
        // Also determine an upper limit for the index-3 table length.
        int index3Capacity = 0;
        i3FirstNull = index3NullOffset;
        boolean hasLongI3Blocks = false;
        // If the fast index covers the whole BMP, then
        // the multi-stage index is only for supplementary code points.
        // Otherwise, the multi-stage index covers all of Unicode.
        int iStart = fastILimit < BMP_I_LIMIT ? 0 : BMP_I_LIMIT;
        int iLimit = highStart >> CodePointTrie.SHIFT_3;
        for (int i = iStart; i < iLimit;) {
            int j = i;
            int jLimit = i + CodePointTrie.INDEX_3_BLOCK_LENGTH;
            int oredI3 = 0;
            boolean isNull = true;
            do {
                int i3 = index[j];
                oredI3 |= i3;
                if (i3 != dataNullOffset) {
                    isNull = false;
                }
            } while (++j < jLimit);
            if (isNull) {
                flags[i] = I3_NULL;
                if (i3FirstNull < 0) {
                    if (oredI3 <= 0xffff) {
                        index3Capacity += CodePointTrie.INDEX_3_BLOCK_LENGTH;
                    } else {
                        index3Capacity += INDEX_3_18BIT_BLOCK_LENGTH;
                        hasLongI3Blocks = true;
                    }
                    i3FirstNull = 0;
                }
            } else {
                if (oredI3 <= 0xffff) {
                    int n = mixedBlocks.findBlock(fastIndex, index, i);
                    if (n >= 0) {
                        flags[i] = I3_BMP;
                        index[i] = n;
                    } else {
                        flags[i] = I3_16;
                        index3Capacity += CodePointTrie.INDEX_3_BLOCK_LENGTH;
                    }
                } else {
                    flags[i] = I3_18;
                    index3Capacity += INDEX_3_18BIT_BLOCK_LENGTH;
                    hasLongI3Blocks = true;
                }
            }
            i = j;
        }

        int index2Capacity = (iLimit - iStart) >> CodePointTrie.SHIFT_2_3;

        // Length of the index-1 table, rounded up.
        int index1Length = (index2Capacity + CodePointTrie.INDEX_2_MASK) >> CodePointTrie.SHIFT_1_2;

        // Index table: Fast index, index-1, index-3, index-2.
        // +1 for possible index table padding.
        int index16Capacity = fastIndexLength + index1Length + index3Capacity + index2Capacity + 1;
        index16 = Arrays.copyOf(fastIndex, index16Capacity);

        mixedBlocks.init(index16Capacity, CodePointTrie.INDEX_3_BLOCK_LENGTH);
        MixedBlocks longI3Blocks = null;
        if (hasLongI3Blocks) {
            longI3Blocks = new MixedBlocks();
            longI3Blocks.init(index16Capacity, INDEX_3_18BIT_BLOCK_LENGTH);
        }

        // Compact the index-3 table and write an uncompacted version of the index-2 table.
        char[] index2 = new char[index2Capacity];
        int i2Length = 0;
        i3FirstNull = index3NullOffset;
        int index3Start = fastIndexLength + index1Length;
        int indexLength = index3Start;
        for (int i = iStart; i < iLimit; i += CodePointTrie.INDEX_3_BLOCK_LENGTH) {
            int i3;
            byte f = flags[i];
            if (f == I3_NULL && i3FirstNull < 0) {
                // First index-3 null block. Write & overlap it like a normal block, then remember it.
                f = dataNullOffset <= 0xffff ? I3_16 : I3_18;
                i3FirstNull = 0;
            }
            if (f == I3_NULL) {
                i3 = index3NullOffset;
            } else if (f == I3_BMP) {
                i3 = index[i];
            } else if (f == I3_16) {
                int n = mixedBlocks.findBlock(index16, index, i);
                if (n >= 0) {
                    i3 = n;
                } else {
                    if (indexLength == index3Start) {
                        // No overlap at the boundary between the index-1 and index-3 tables.
                        n = 0;
                    } else {
                        n = getOverlap(index16, indexLength,
                                       index, i, CodePointTrie.INDEX_3_BLOCK_LENGTH);
                    }
                    i3 = indexLength - n;
                    int prevIndexLength = indexLength;
                    while (n < CodePointTrie.INDEX_3_BLOCK_LENGTH) {
                        index16[indexLength++] = (char)index[i + n++];
                    }
                    mixedBlocks.extend(index16, index3Start, prevIndexLength, indexLength);
                    if (hasLongI3Blocks) {
                        longI3Blocks.extend(index16, index3Start, prevIndexLength, indexLength);
                    }
                }
            } else {
                assert(f == I3_18);
                assert(hasLongI3Blocks);
                // Encode an index-3 block that contains one or more data indexes exceeding 16 bits.
                int j = i;
                int jLimit = i + CodePointTrie.INDEX_3_BLOCK_LENGTH;
                int k = indexLength;
                do {
                    ++k;
                    int v = index[j++];
                    int upperBits = (v & 0x30000) >> 2;
                    index16[k++] = (char)v;
                    v = index[j++];
                    upperBits |= (v & 0x30000) >> 4;
                    index16[k++] = (char)v;
                    v = index[j++];
                    upperBits |= (v & 0x30000) >> 6;
                    index16[k++] = (char)v;
                    v = index[j++];
                    upperBits |= (v & 0x30000) >> 8;
                    index16[k++] = (char)v;
                    v = index[j++];
                    upperBits |= (v & 0x30000) >> 10;
                    index16[k++] = (char)v;
                    v = index[j++];
                    upperBits |= (v & 0x30000) >> 12;
                    index16[k++] = (char)v;
                    v = index[j++];
                    upperBits |= (v & 0x30000) >> 14;
                    index16[k++] = (char)v;
                    v = index[j++];
                    upperBits |= (v & 0x30000) >> 16;
                    index16[k++] = (char)v;
                    index16[k - 9] = (char)upperBits;
                } while (j < jLimit);
                int n = longI3Blocks.findBlock(index16, index16, indexLength);
                if (n >= 0) {
                    i3 = n | 0x8000;
                } else {
                    if (indexLength == index3Start) {
                        // No overlap at the boundary between the index-1 and index-3 tables.
                        n = 0;
                    } else {
                        n = getOverlap(index16, indexLength,
                                       index16, indexLength, INDEX_3_18BIT_BLOCK_LENGTH);
                    }
                    i3 = (indexLength - n) | 0x8000;
                    int prevIndexLength = indexLength;
                    if (n > 0) {
                        int start = indexLength;
                        while (n < INDEX_3_18BIT_BLOCK_LENGTH) {
                            index16[indexLength++] = index16[start + n++];
                        }
                    } else {
                        indexLength += INDEX_3_18BIT_BLOCK_LENGTH;
                    }
                    mixedBlocks.extend(index16, index3Start, prevIndexLength, indexLength);
                    if (hasLongI3Blocks) {
                        longI3Blocks.extend(index16, index3Start, prevIndexLength, indexLength);
                    }
                }
            }
            if (index3NullOffset < 0 && i3FirstNull >= 0) {
                index3NullOffset = i3;
            }
            // Set the index-2 table entry.
            index2[i2Length++] = (char)i3;
        }
        assert(i2Length == index2Capacity);
        assert(indexLength <= index3Start + index3Capacity);

        if (index3NullOffset < 0) {
            index3NullOffset = CodePointTrie.NO_INDEX3_NULL_OFFSET;
        }
        if (indexLength >= (CodePointTrie.NO_INDEX3_NULL_OFFSET + CodePointTrie.INDEX_3_BLOCK_LENGTH)) {
            // The index-3 offsets exceed 15 bits, or
            // the last one cannot be distinguished from the no-null-block value.
            throw new IndexOutOfBoundsException(
                    "The trie data exceeds limitations of the data structure.");
        }

        // Compact the index-2 table and write the index-1 table.
        // assert(CodePointTrie.INDEX_2_BLOCK_LENGTH == CodePointTrie.INDEX_3_BLOCK_LENGTH) :
        //     "must re-init mixedBlocks";
        int blockLength = CodePointTrie.INDEX_2_BLOCK_LENGTH;
        int i1 = fastIndexLength;
        for (int i = 0; i < i2Length; i += blockLength) {
            int n;
            if ((i2Length - i) >= blockLength) {
                // normal block
                assert(blockLength == CodePointTrie.INDEX_2_BLOCK_LENGTH);
                n = mixedBlocks.findBlock(index16, index2, i);
            } else {
                // highStart is inside the last index-2 block. Shorten it.
                blockLength = i2Length - i;
                n = findSameBlock(index16, index3Start, indexLength,
                        index2, i, blockLength);
            }
            int i2;
            if (n >= 0) {
                i2 = n;
            } else {
                if (indexLength == index3Start) {
                    // No overlap at the boundary between the index-1 and index-3/2 tables.
                    n = 0;
                } else {
                    n = getOverlap(index16, indexLength, index2, i, blockLength);
                }
                i2 = indexLength - n;
                int prevIndexLength = indexLength;
                while (n < blockLength) {
                    index16[indexLength++] = index2[i + n++];
                }
                mixedBlocks.extend(index16, index3Start, prevIndexLength, indexLength);
            }
            // Set the index-1 table entry.
            index16[i1++] = (char)i2;
        }
        assert(i1 == index3Start);
        assert(indexLength <= index16Capacity);

        return indexLength;
    }

    private int compactTrie(int fastILimit) {
        // Find the real highStart and round it up.
        assert((highStart & (CodePointTrie.CP_PER_INDEX_2_ENTRY - 1)) == 0);
        highValue = get(MAX_UNICODE);
        int realHighStart = findHighStart();
        realHighStart = (realHighStart + (CodePointTrie.CP_PER_INDEX_2_ENTRY - 1)) &
            ~(CodePointTrie.CP_PER_INDEX_2_ENTRY - 1);
        if (realHighStart == UNICODE_LIMIT) {
            highValue = initialValue;
        }

        // We always store indexes and data values for the fast range.
        // Pin highStart to the top of that range while building.
        int fastLimit = fastILimit << CodePointTrie.SHIFT_3;
        if (realHighStart < fastLimit) {
            for (int i = (realHighStart >> CodePointTrie.SHIFT_3); i < fastILimit; ++i) {
                flags[i] = ALL_SAME;
                index[i] = highValue;
            }
            highStart = fastLimit;
        } else {
            highStart = realHighStart;
        }

        int[] asciiData = new int[ASCII_LIMIT];
        for (int i = 0; i < ASCII_LIMIT; ++i) {
            asciiData[i] = get(i);
        }

        // First we look for which data blocks have the same value repeated over the whole block,
        // deduplicate such blocks, find a good null data block (for faster enumeration),
        // and get an upper bound for the necessary data array length.
        AllSameBlocks allSameBlocks = new AllSameBlocks();
        int newDataCapacity = compactWholeDataBlocks(fastILimit, allSameBlocks);
        int[] newData = Arrays.copyOf(asciiData, newDataCapacity);

        int dataNullIndex = allSameBlocks.findMostUsed();

        MixedBlocks mixedBlocks = new MixedBlocks();
        int newDataLength = compactData(fastILimit, newData, dataNullIndex, mixedBlocks);
        assert(newDataLength <= newDataCapacity);
        data = newData;
        dataLength = newDataLength;
        if (dataLength > (0x3ffff + CodePointTrie.SMALL_DATA_BLOCK_LENGTH)) {
            // The offset of the last data block is too high to be stored in the index table.
            throw new IndexOutOfBoundsException(
                    "The trie data exceeds limitations of the data structure.");
        }

        if (dataNullIndex >= 0) {
            dataNullOffset = index[dataNullIndex];
            initialValue = data[dataNullOffset];
        } else {
            dataNullOffset = CodePointTrie.NO_DATA_NULL_OFFSET;
        }

        int indexLength = compactIndex(fastILimit, mixedBlocks);
        highStart = realHighStart;
        return indexLength;
    }

    private CodePointTrie build(CodePointTrie.Type type, CodePointTrie.ValueWidth valueWidth) {
        // The mutable trie always stores 32-bit values.
        // When we build a UCPTrie for a smaller value width, we first mask off unused bits
        // before compacting the data.
        switch (valueWidth) {
        case BITS_32:
            break;
        case BITS_16:
            maskValues(0xffff);
            break;
        case BITS_8:
            maskValues(0xff);
            break;
        default:
            // Should be unreachable.
            throw new IllegalArgumentException();
        }

        int fastLimit = type == CodePointTrie.Type.FAST ? BMP_LIMIT : CodePointTrie.SMALL_LIMIT;
        int indexLength = compactTrie(fastLimit >> CodePointTrie.SHIFT_3);

        // Ensure data table alignment: The index length must be even for uint32_t data.
        if (valueWidth == CodePointTrie.ValueWidth.BITS_32 && (indexLength & 1) != 0) {
            index16[indexLength++] = 0xffee;  // arbitrary value
        }

        // Make the total trie structure length a multiple of 4 bytes by padding the data table,
        // and store special values as the last two data values.
        int length = indexLength * 2;
        if (valueWidth == CodePointTrie.ValueWidth.BITS_16) {
            if (((indexLength ^ dataLength) & 1) != 0) {
                // padding
                data[dataLength++] = errorValue;
            }
            if (data[dataLength - 1] != errorValue || data[dataLength - 2] != highValue) {
                data[dataLength++] = highValue;
                data[dataLength++] = errorValue;
            }
            length += dataLength * 2;
        } else if (valueWidth == CodePointTrie.ValueWidth.BITS_32) {
            // 32-bit data words never need padding to a multiple of 4 bytes.
            if (data[dataLength - 1] != errorValue || data[dataLength - 2] != highValue) {
                if (data[dataLength - 1] != highValue) {
                    data[dataLength++] = highValue;
                }
                data[dataLength++] = errorValue;
            }
            length += dataLength * 4;
        } else {
            int and3 = (length + dataLength) & 3;
            if (and3 == 0 && data[dataLength - 1] == errorValue && data[dataLength - 2] == highValue) {
                // all set
            } else if(and3 == 3 && data[dataLength - 1] == highValue) {
                data[dataLength++] = errorValue;
            } else {
                while (and3 != 2) {
                    data[dataLength++] = highValue;
                    and3 = (and3 + 1) & 3;
                }
                data[dataLength++] = highValue;
                data[dataLength++] = errorValue;
            }
            length += dataLength;
        }
        assert((length & 3) == 0);

        // Fill the index and data arrays.
        char[] trieIndex;
        if (highStart <= fastLimit) {
            // Condense only the fast index from the mutable-trie index.
            trieIndex = new char[indexLength];
            for (int i = 0, j = 0; j < indexLength; i += SMALL_DATA_BLOCKS_PER_BMP_BLOCK, ++j) {
                trieIndex[j] = (char)index[i];
            }
        } else {
            if (indexLength == index16.length) {
                trieIndex = index16;
                index16 = null;
            } else {
                trieIndex = Arrays.copyOf(index16, indexLength);
            }
        }

        // Write the data array.
        switch (valueWidth) {
        case BITS_16: {
            // Write 16-bit data values.
            char[] data16 = new char[dataLength];
            for (int i = 0; i < dataLength; ++i) { data16[i] = (char)data[i]; }
            return type == CodePointTrie.Type.FAST ?
                    new CodePointTrie.Fast16(trieIndex, data16, highStart,
                            index3NullOffset, dataNullOffset) :
                    new CodePointTrie.Small16(trieIndex, data16, highStart,
                            index3NullOffset, dataNullOffset);
        }
        case BITS_32: {
            // Write 32-bit data values.
            int[] data32 = Arrays.copyOf(data, dataLength);
            return type == CodePointTrie.Type.FAST ?
                    new CodePointTrie.Fast32(trieIndex, data32, highStart,
                            index3NullOffset, dataNullOffset) :
                    new CodePointTrie.Small32(trieIndex, data32, highStart,
                            index3NullOffset, dataNullOffset);
        }
        case BITS_8: {
            // Write 8-bit data values.
            byte[] data8 = new byte[dataLength];
            for (int i = 0; i < dataLength; ++i) { data8[i] = (byte)data[i]; }
            return type == CodePointTrie.Type.FAST ?
                    new CodePointTrie.Fast8(trieIndex, data8, highStart,
                            index3NullOffset, dataNullOffset) :
                    new CodePointTrie.Small8(trieIndex, data8, highStart,
                            index3NullOffset, dataNullOffset);
        }
        default:
            // Should be unreachable.
            throw new IllegalArgumentException();
        }
    }
}
