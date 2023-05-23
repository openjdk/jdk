// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2009-2015, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */

package jdk.internal.icu.impl;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;
import java.util.NoSuchElementException;


/**
 * This is the interface and common implementation of a Unicode Trie2.
 * It is a kind of compressed table that maps from Unicode code points (0..0x10ffff)
 * to 16- or 32-bit integer values.  It works best when there are ranges of
 * characters with the same value, which is generally the case with Unicode
 * character properties.
 *
 * This is the second common version of a Unicode trie (hence the name Trie2).
 *
 */
public abstract class Trie2 implements Iterable<Trie2.Range> {


    /**
     * Create a Trie2 from its serialized form.  Inverse of utrie2_serialize().
     *
     * Reads from the current position and leaves the buffer after the end of the trie.
     *
     * The serialized format is identical between ICU4C and ICU4J, so this function
     * will work with serialized Trie2s from either.
     *
     * The actual type of the returned Trie2 will be either Trie2_16 or Trie2_32, depending
     * on the width of the data.
     *
     * To obtain the width of the Trie2, check the actual class type of the returned Trie2.
     * Or use the createFromSerialized() function of Trie2_16 or Trie2_32, which will
     * return only Tries of their specific type/size.
     *
     * The serialized Trie2 on the stream may be in either little or big endian byte order.
     * This allows using serialized Tries from ICU4C without needing to consider the
     * byte order of the system that created them.
     *
     * @param bytes a byte buffer to the serialized form of a UTrie2.
     * @return An unserialized Trie2, ready for use.
     * @throws IllegalArgumentException if the stream does not contain a serialized Trie2.
     * @throws IOException if a read error occurs in the buffer.
     *
     */
    public static Trie2 createFromSerialized(ByteBuffer bytes) throws IOException {
         //    From ICU4C utrie2_impl.h
         //    * Trie2 data structure in serialized form:
         //     *
         //     * UTrie2Header header;
         //     * uint16_t index[header.index2Length];
         //     * uint16_t data[header.shiftedDataLength<<2];  -- or uint32_t data[...]
         //     * @internal
         //     */
         //    typedef struct UTrie2Header {
         //        /** "Tri2" in big-endian US-ASCII (0x54726932) */
         //        uint32_t signature;

         //       /**
         //         * options bit field:
         //         * 15.. 4   reserved (0)
         //         *  3.. 0   UTrie2ValueBits valueBits
         //         */
         //        uint16_t options;
         //
         //        /** UTRIE2_INDEX_1_OFFSET..UTRIE2_MAX_INDEX_LENGTH */
         //        uint16_t indexLength;
         //
         //        /** (UTRIE2_DATA_START_OFFSET..UTRIE2_MAX_DATA_LENGTH)>>UTRIE2_INDEX_SHIFT */
         //        uint16_t shiftedDataLength;
         //
         //        /** Null index and data blocks, not shifted. */
         //        uint16_t index2NullOffset, dataNullOffset;
         //
         //        /**
         //         * First code point of the single-value range ending with U+10ffff,
         //         * rounded up and then shifted right by UTRIE2_SHIFT_1.
         //         */
         //        uint16_t shiftedHighStart;
         //    } UTrie2Header;

        ByteOrder outerByteOrder = bytes.order();
        try {
            UTrie2Header header = new UTrie2Header();

            /* check the signature */
            header.signature = bytes.getInt();
            switch (header.signature) {
            case 0x54726932:
                // The buffer is already set to the trie data byte order.
                break;
            case 0x32697254:
                // Temporarily reverse the byte order.
                boolean isBigEndian = outerByteOrder == ByteOrder.BIG_ENDIAN;
                bytes.order(isBigEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
                header.signature = 0x54726932;
                break;
            default:
                throw new IllegalArgumentException("Buffer does not contain a serialized UTrie2");
            }

            header.options = bytes.getChar();
            header.indexLength = bytes.getChar();
            header.shiftedDataLength = bytes.getChar();
            header.index2NullOffset = bytes.getChar();
            header.dataNullOffset   = bytes.getChar();
            header.shiftedHighStart = bytes.getChar();

            // Trie2 data width - 0: 16 bits
            //                    1: 32 bits
            if ((header.options & UTRIE2_OPTIONS_VALUE_BITS_MASK) > 1) {
                throw new IllegalArgumentException("UTrie2 serialized format error.");
            }
            ValueWidth width;
            Trie2 This;
            if ((header.options & UTRIE2_OPTIONS_VALUE_BITS_MASK) == 0) {
                width = ValueWidth.BITS_16;
                This = new Trie2_16();
            } else {
                width = ValueWidth.BITS_32;
                This = new Trie2_32();
            }
            This.header = header;

            /* get the length values and offsets */
            This.indexLength      = header.indexLength;
            This.dataLength       = header.shiftedDataLength << UTRIE2_INDEX_SHIFT;
            This.index2NullOffset = header.index2NullOffset;
            This.dataNullOffset   = header.dataNullOffset;
            This.highStart        = header.shiftedHighStart << UTRIE2_SHIFT_1;
            This.highValueIndex   = This.dataLength - UTRIE2_DATA_GRANULARITY;
            if (width == ValueWidth.BITS_16) {
                This.highValueIndex += This.indexLength;
            }

            // Allocate the Trie2 index array. If the data width is 16 bits, the array also
            // includes the space for the data.

            int indexArraySize = This.indexLength;
            if (width == ValueWidth.BITS_16) {
                indexArraySize += This.dataLength;
            }

            /* Read in the index */
            This.index = ICUBinary.getChars(bytes, indexArraySize, 0);

            /* Read in the data. 16 bit data goes in the same array as the index.
             * 32 bit data goes in its own separate data array.
             */
            if (width == ValueWidth.BITS_16) {
                This.data16 = This.indexLength;
            } else {
                This.data32 = ICUBinary.getInts(bytes, This.dataLength, 0);
            }

            switch(width) {
            case BITS_16:
                This.data32 = null;
                This.initialValue = This.index[This.dataNullOffset];
                This.errorValue   = This.index[This.data16+UTRIE2_BAD_UTF8_DATA_OFFSET];
                break;
            case BITS_32:
                This.data16=0;
                This.initialValue = This.data32[This.dataNullOffset];
                This.errorValue   = This.data32[UTRIE2_BAD_UTF8_DATA_OFFSET];
                break;
            default:
                throw new IllegalArgumentException("UTrie2 serialized format error.");
            }

            return This;
        } finally {
            bytes.order(outerByteOrder);
        }
    }

    /**
     * Get the UTrie version from an InputStream containing the serialized form
     * of either a Trie (version 1) or a Trie2 (version 2).
     *
     * @param is   an InputStream containing the serialized form
     *             of a UTrie, version 1 or 2.  The stream must support mark() and reset().
     *             The position of the input stream will be left unchanged.
     * @param littleEndianOk If false, only big-endian (Java native) serialized forms are recognized.
     *                    If true, little-endian serialized forms are recognized as well.
     * @return     the Trie version of the serialized form, or 0 if it is not
     *             recognized as a serialized UTrie
     * @throws     IOException on errors in reading from the input stream.
     */
    public static int getVersion(InputStream is, boolean littleEndianOk) throws IOException {
        if (! is.markSupported()) {
            throw new IllegalArgumentException("Input stream must support mark().");
            }
        is.mark(4);
        byte sig[] = new byte[4];
        int read = is.read(sig);
        is.reset();

        if (read != sig.length) {
            return 0;
        }

        if (sig[0]=='T' && sig[1]=='r' && sig[2]=='i' && sig[3]=='e') {
            return 1;
        }
        if (sig[0]=='T' && sig[1]=='r' && sig[2]=='i' && sig[3]=='2') {
            return 2;
        }
        if (littleEndianOk) {
            if (sig[0]=='e' && sig[1]=='i' && sig[2]=='r' && sig[3]=='T') {
                return 1;
            }
            if (sig[0]=='2' && sig[1]=='i' && sig[2]=='r' && sig[3]=='T') {
                return 2;
            }
        }
        return 0;
    }

    /**
     * Get the value for a code point as stored in the Trie2.
     *
     * @param codePoint the code point
     * @return the value
     */
    abstract public int get(int codePoint);


    /**
     * Get the trie value for a UTF-16 code unit.
     *
     * A Trie2 stores two distinct values for input in the lead surrogate
     * range, one for lead surrogates, which is the value that will be
     * returned by this function, and a second value that is returned
     * by Trie2.get().
     *
     * For code units outside of the lead surrogate range, this function
     * returns the same result as Trie2.get().
     *
     * This function, together with the alternate value for lead surrogates,
     * makes possible very efficient processing of UTF-16 strings without
     * first converting surrogate pairs to their corresponding 32 bit code point
     * values.
     *
     * At build-time, enumerate the contents of the Trie2 to see if there
     * is non-trivial (non-initialValue) data for any of the supplementary
     * code points associated with a lead surrogate.
     * If so, then set a special (application-specific) value for the
     * lead surrogate code _unit_, with Trie2Writable.setForLeadSurrogateCodeUnit().
     *
     * At runtime, use Trie2.getFromU16SingleLead(). If there is non-trivial
     * data and the code unit is a lead surrogate, then check if a trail surrogate
     * follows. If so, assemble the supplementary code point and look up its value
     * with Trie2.get(); otherwise reset the lead
     * surrogate's value or do a code point lookup for it.
     *
     * If there is only trivial data for lead and trail surrogates, then processing
     * can often skip them. For example, in normalization or case mapping
     * all characters that do not have any mappings are simply copied as is.
     *
     * @param c the code point or lead surrogate value.
     * @return the value
     */
    abstract public int getFromU16SingleLead(char c);


    /**
     * Equals function.  Two Tries are equal if their contents are equal.
     * The type need not be the same, so a Trie2Writable will be equal to
     * (read-only) Trie2_16 or Trie2_32 so long as they are storing the same values.
     *
     */
    @Override
    public final boolean equals(Object other) {
        if(!(other instanceof Trie2)) {
            return false;
        }
        Trie2 OtherTrie = (Trie2)other;
        Range  rangeFromOther;

        Iterator<Trie2.Range> otherIter = OtherTrie.iterator();
        for (Trie2.Range rangeFromThis: this) {
            if (otherIter.hasNext() == false) {
                return false;
            }
            rangeFromOther = otherIter.next();
            if (!rangeFromThis.equals(rangeFromOther)) {
                return false;
            }
        }
        if (otherIter.hasNext()) {
            return false;
        }

        if (errorValue   != OtherTrie.errorValue ||
            initialValue != OtherTrie.initialValue) {
            return false;
        }

        return true;
    }


    @Override
    public int hashCode() {
        if (fHash == 0) {
            int hash = initHash();
            for (Range r: this) {
                hash = hashInt(hash, r.hashCode());
            }
            if (hash == 0) {
                hash = 1;
            }
            fHash = hash;
        }
        return fHash;
    }

    /**
     * When iterating over the contents of a Trie2, Elements of this type are produced.
     * The iterator will return one item for each contiguous range of codepoints  having the same value.
     *
     * When iterating, the same Trie2EnumRange object will be reused and returned for each range.
     * If you need to retain complete iteration results, clone each returned Trie2EnumRange,
     * or save the range in some other way, before advancing to the next iteration step.
     */
    public static class Range {
        public int     startCodePoint;
        public int     endCodePoint;     // Inclusive.
        public int     value;
        public boolean leadSurrogate;

        @Override
        public boolean equals(Object other) {
            if (other == null || !(other.getClass().equals(getClass()))) {
                return false;
            }
            Range tother = (Range)other;
            return this.startCodePoint == tother.startCodePoint &&
                   this.endCodePoint   == tother.endCodePoint   &&
                   this.value          == tother.value          &&
                   this.leadSurrogate  == tother.leadSurrogate;
        }


        @Override
        public int hashCode() {
            int h = initHash();
            h = hashUChar32(h, startCodePoint);
            h = hashUChar32(h, endCodePoint);
            h = hashInt(h, value);
            h = hashByte(h, leadSurrogate? 1: 0);
            return h;
        }
    }


    /**
     *  Create an iterator over the value ranges in this Trie2.
     *  Values from the Trie2 are not remapped or filtered, but are returned as they
     *  are stored in the Trie2.
     *
     * @return an Iterator
     */
    @Override
    public Iterator<Range> iterator() {
        return iterator(defaultValueMapper);
    }

    private static ValueMapper defaultValueMapper = new ValueMapper() {
        @Override
        public int map(int in) {
            return in;
        }
    };

    /**
     * Create an iterator over the value ranges from this Trie2.
     * Values from the Trie2 are passed through a caller-supplied remapping function,
     * and it is the remapped values that determine the ranges that
     * will be produced by the iterator.
     *
     *
     * @param mapper provides a function to remap values obtained from the Trie2.
     * @return an Iterator
     */
    public Iterator<Range> iterator(ValueMapper mapper) {
        return new Trie2Iterator(mapper);
    }


    /**
     * Create an iterator over the Trie2 values for the 1024=0x400 code points
     * corresponding to a given lead surrogate.
     * For example, for the lead surrogate U+D87E it will enumerate the values
     * for [U+2F800..U+2FC00[.
     * Used by data builder code that sets special lead surrogate code unit values
     * for optimized UTF-16 string processing.
     *
     * Do not modify the Trie2 during the iteration.
     *
     * Except for the limited code point range, this functions just like Trie2.iterator().
     *
     */
    public Iterator<Range> iteratorForLeadSurrogate(char lead, ValueMapper mapper) {
        return new Trie2Iterator(lead, mapper);
    }

    /**
     * Create an iterator over the Trie2 values for the 1024=0x400 code points
     * corresponding to a given lead surrogate.
     * For example, for the lead surrogate U+D87E it will enumerate the values
     * for [U+2F800..U+2FC00[.
     * Used by data builder code that sets special lead surrogate code unit values
     * for optimized UTF-16 string processing.
     *
     * Do not modify the Trie2 during the iteration.
     *
     * Except for the limited code point range, this functions just like Trie2.iterator().
     *
     */
    public Iterator<Range> iteratorForLeadSurrogate(char lead) {
        return new Trie2Iterator(lead, defaultValueMapper);
    }

    /**
     * When iterating over the contents of a Trie2, an instance of TrieValueMapper may
     * be used to remap the values from the Trie2.  The remapped values will be used
     * both in determining the ranges of codepoints and as the value to be returned
     * for each range.
     *
     * Example of use, with an anonymous subclass of TrieValueMapper:
     *
     *
     * ValueMapper m = new ValueMapper() {
     *    int map(int in) {return in & 0x1f;};
     * }
     * for (Iterator<Trie2EnumRange> iter = trie.iterator(m); i.hasNext(); ) {
     *     Trie2EnumRange r = i.next();
     *     ...  // Do something with the range r.
     * }
     *
     */
    public interface ValueMapper {
        public int  map(int originalVal);
    }


   /**
     * Serialize a trie2 Header and Index onto an OutputStream.  This is
     * common code used for  both the Trie2_16 and Trie2_32 serialize functions.
     * @param dos the stream to which the serialized Trie2 data will be written.
     * @return the number of bytes written.
     */
    protected int serializeHeader(DataOutputStream dos) throws IOException {
        // Write the header.  It is already set and ready to use, having been
        //  created when the Trie2 was unserialized or when it was frozen.
        int  bytesWritten = 0;

        dos.writeInt(header.signature);
        dos.writeShort(header.options);
        dos.writeShort(header.indexLength);
        dos.writeShort(header.shiftedDataLength);
        dos.writeShort(header.index2NullOffset);
        dos.writeShort(header.dataNullOffset);
        dos.writeShort(header.shiftedHighStart);
        bytesWritten += 16;

        // Write the index
        int i;
        for (i=0; i< header.indexLength; i++) {
            dos.writeChar(index[i]);
        }
        bytesWritten += header.indexLength;
        return bytesWritten;
    }


    /**
     * Struct-like class for holding the results returned by a UTrie2 CharSequence iterator.
     * The iteration walks over a CharSequence, and for each Unicode code point therein
     * returns the character and its associated Trie2 value.
     */
    public static class CharSequenceValues {
        /** string index of the current code point. */
        public int index;
        /** The code point at index.  */
        public int codePoint;
        /** The Trie2 value for the current code point */
        public int value;
    }


    /**
     *  Create an iterator that will produce the values from the Trie2 for
     *  the sequence of code points in an input text.
     *
     * @param text A text string to be iterated over.
     * @param index The starting iteration position within the input text.
     * @return the CharSequenceIterator
     */
    public CharSequenceIterator charSequenceIterator(CharSequence text, int index) {
        return new CharSequenceIterator(text, index);
    }

    // TODO:  Survey usage of the equivalent of CharSequenceIterator in ICU4C
    //        and if there is none, remove it from here.
    //        Don't waste time testing and maintaining unused code.

    /**
     * An iterator that operates over an input CharSequence, and for each Unicode code point
     * in the input returns the associated value from the Trie2.
     *
     * The iterator can move forwards or backwards, and can be reset to an arbitrary index.
     *
     * Note that Trie2_16 and Trie2_32 subclass Trie2.CharSequenceIterator.  This is done
     * only for performance reasons.  It does require that any changes made here be propagated
     * into the corresponding code in the subclasses.
     */
    public class CharSequenceIterator implements Iterator<CharSequenceValues> {
        /**
         * Internal constructor.
         */
        CharSequenceIterator(CharSequence t, int index) {
            text = t;
            textLength = text.length();
            set(index);
        }

        private CharSequence text;
        private int textLength;
        private int index;
        private Trie2.CharSequenceValues fResults = new Trie2.CharSequenceValues();


        public void set(int i) {
            if (i < 0 || i > textLength) {
                throw new IndexOutOfBoundsException();
            }
            index = i;
        }


        @Override
        public final boolean hasNext() {
            return index<textLength;
        }


        public final boolean hasPrevious() {
            return index>0;
        }


        @Override
        public Trie2.CharSequenceValues next() {
            int c = Character.codePointAt(text, index);
            int val = get(c);

            fResults.index = index;
            fResults.codePoint = c;
            fResults.value = val;
            index++;
            if (c >= 0x10000) {
                index++;
            }
            return fResults;
        }


        public Trie2.CharSequenceValues previous() {
            int c = Character.codePointBefore(text, index);
            int val = get(c);
            index--;
            if (c >= 0x10000) {
                index--;
            }
            fResults.index = index;
            fResults.codePoint = c;
            fResults.value = val;
            return fResults;
        }

        /**
         * Iterator.remove() is not supported by Trie2.CharSequenceIterator.
         * @throws UnsupportedOperationException Always thrown because this operation is not supported
         * @see java.util.Iterator#remove()
         */
        @Override
        public void remove() {
            throw new UnsupportedOperationException("Trie2.CharSequenceIterator does not support remove().");
        }
    }


    //--------------------------------------------------------------------------------
    //
    // Below this point are internal implementation items.  No further public API.
    //
    //--------------------------------------------------------------------------------


    /**
     * Selectors for the width of a UTrie2 data value.
     */
     enum ValueWidth {
         BITS_16,
         BITS_32
     }

     /**
     * Trie2 data structure in serialized form:
     *
     * UTrie2Header header;
     * uint16_t index[header.index2Length];
     * uint16_t data[header.shiftedDataLength<<2];  -- or uint32_t data[...]
     *
     * For Java, this is read from the stream into an instance of UTrie2Header.
     * (The C version just places a struct over the raw serialized data.)
     *
     * @internal
     */
    static class UTrie2Header {
        /** "Tri2" in big-endian US-ASCII (0x54726932) */
        int signature;

        /**
         * options bit field (uint16_t):
         * 15.. 4   reserved (0)
         *  3.. 0   UTrie2ValueBits valueBits
         */
        int  options;

        /** UTRIE2_INDEX_1_OFFSET..UTRIE2_MAX_INDEX_LENGTH  (uint16_t) */
        int  indexLength;

        /** (UTRIE2_DATA_START_OFFSET..UTRIE2_MAX_DATA_LENGTH)>>UTRIE2_INDEX_SHIFT  (uint16_t) */
        int  shiftedDataLength;

        /** Null index and data blocks, not shifted.  (uint16_t) */
        int  index2NullOffset, dataNullOffset;

        /**
         * First code point of the single-value range ending with U+10ffff,
         * rounded up and then shifted right by UTRIE2_SHIFT_1.  (uint16_t)
         */
        int shiftedHighStart;
    }

    //
    //  Data members of UTrie2.
    //
    UTrie2Header  header;
    char          index[];           // Index array.  Includes data for 16 bit Tries.
    int           data16;            // Offset to data portion of the index array, if 16 bit data.
                                     //    zero if 32 bit data.
    int           data32[];          // NULL if 16b data is used via index

    int           indexLength;
    int           dataLength;
    int           index2NullOffset;  // 0xffff if there is no dedicated index-2 null block
    int           initialValue;

    /** Value returned for out-of-range code points and illegal UTF-8. */
    int           errorValue;

    /* Start of the last range which ends at U+10ffff, and its value. */
    int           highStart;
    int           highValueIndex;

    int           dataNullOffset;

    int           fHash;              // Zero if not yet computed.
                                      //  Shared by Trie2Writable, Trie2_16, Trie2_32.
                                      //  Thread safety:  if two racing threads compute
                                      //     the same hash on a frozen Trie2, no damage is done.


    /**
     * Trie2 constants, defining shift widths, index array lengths, etc.
     *
     * These are needed for the runtime macros but users can treat these as
     * implementation details and skip to the actual public API further below.
     */

    static final int UTRIE2_OPTIONS_VALUE_BITS_MASK=0x000f;


    /** Shift size for getting the index-1 table offset. */
    static final int UTRIE2_SHIFT_1=6+5;

    /** Shift size for getting the index-2 table offset. */
    static final int UTRIE2_SHIFT_2=5;

    /**
     * Difference between the two shift sizes,
     * for getting an index-1 offset from an index-2 offset. 6=11-5
     */
    static final int UTRIE2_SHIFT_1_2=UTRIE2_SHIFT_1-UTRIE2_SHIFT_2;

    /**
     * Number of index-1 entries for the BMP. 32=0x20
     * This part of the index-1 table is omitted from the serialized form.
     */
    static final int UTRIE2_OMITTED_BMP_INDEX_1_LENGTH=0x10000>>UTRIE2_SHIFT_1;

    /** Number of code points per index-1 table entry. 2048=0x800 */
    static final int UTRIE2_CP_PER_INDEX_1_ENTRY=1<<UTRIE2_SHIFT_1;

    /** Number of entries in an index-2 block. 64=0x40 */
    static final int UTRIE2_INDEX_2_BLOCK_LENGTH=1<<UTRIE2_SHIFT_1_2;

    /** Mask for getting the lower bits for the in-index-2-block offset. */
    static final int UTRIE2_INDEX_2_MASK=UTRIE2_INDEX_2_BLOCK_LENGTH-1;

    /** Number of entries in a data block. 32=0x20 */
    static final int UTRIE2_DATA_BLOCK_LENGTH=1<<UTRIE2_SHIFT_2;

    /** Mask for getting the lower bits for the in-data-block offset. */
    static final int UTRIE2_DATA_MASK=UTRIE2_DATA_BLOCK_LENGTH-1;

    /**
     * Shift size for shifting left the index array values.
     * Increases possible data size with 16-bit index values at the cost
     * of compactability.
     * This requires data blocks to be aligned by UTRIE2_DATA_GRANULARITY.
     */
    static final int UTRIE2_INDEX_SHIFT=2;

    /** The alignment size of a data block. Also the granularity for compaction. */
    static final int UTRIE2_DATA_GRANULARITY=1<<UTRIE2_INDEX_SHIFT;

    /* Fixed layout of the first part of the index array. ------------------- */

    /**
     * The BMP part of the index-2 table is fixed and linear and starts at offset 0.
     * Length=2048=0x800=0x10000>>UTRIE2_SHIFT_2.
     */
    static final int UTRIE2_INDEX_2_OFFSET=0;

    /**
     * The part of the index-2 table for U+D800..U+DBFF stores values for
     * lead surrogate code _units_ not code _points_.
     * Values for lead surrogate code _points_ are indexed with this portion of the table.
     * Length=32=0x20=0x400>>UTRIE2_SHIFT_2. (There are 1024=0x400 lead surrogates.)
     */
    static final int UTRIE2_LSCP_INDEX_2_OFFSET=0x10000>>UTRIE2_SHIFT_2;
    static final int UTRIE2_LSCP_INDEX_2_LENGTH=0x400>>UTRIE2_SHIFT_2;

    /** Count the lengths of both BMP pieces. 2080=0x820 */
    static final int UTRIE2_INDEX_2_BMP_LENGTH=UTRIE2_LSCP_INDEX_2_OFFSET+UTRIE2_LSCP_INDEX_2_LENGTH;

    /**
     * The 2-byte UTF-8 version of the index-2 table follows at offset 2080=0x820.
     * Length 32=0x20 for lead bytes C0..DF, regardless of UTRIE2_SHIFT_2.
     */
    static final int UTRIE2_UTF8_2B_INDEX_2_OFFSET=UTRIE2_INDEX_2_BMP_LENGTH;
    static final int UTRIE2_UTF8_2B_INDEX_2_LENGTH=0x800>>6;  /* U+0800 is the first code point after 2-byte UTF-8 */

    /**
     * The index-1 table, only used for supplementary code points, at offset 2112=0x840.
     * Variable length, for code points up to highStart, where the last single-value range starts.
     * Maximum length 512=0x200=0x100000>>UTRIE2_SHIFT_1.
     * (For 0x100000 supplementary code points U+10000..U+10ffff.)
     *
     * The part of the index-2 table for supplementary code points starts
     * after this index-1 table.
     *
     * Both the index-1 table and the following part of the index-2 table
     * are omitted completely if there is only BMP data.
     */
    static final int UTRIE2_INDEX_1_OFFSET=UTRIE2_UTF8_2B_INDEX_2_OFFSET+UTRIE2_UTF8_2B_INDEX_2_LENGTH;
    static final int UTRIE2_MAX_INDEX_1_LENGTH=0x100000>>UTRIE2_SHIFT_1;

    /*
     * Fixed layout of the first part of the data array. -----------------------
     * Starts with 4 blocks (128=0x80 entries) for ASCII.
     */

    /**
     * The illegal-UTF-8 data block follows the ASCII block, at offset 128=0x80.
     * Used with linear access for single bytes 0..0xbf for simple error handling.
     * Length 64=0x40, not UTRIE2_DATA_BLOCK_LENGTH.
     */
    static final int UTRIE2_BAD_UTF8_DATA_OFFSET=0x80;

    /** The start of non-linear-ASCII data blocks, at offset 192=0xc0. */
    static final int UTRIE2_DATA_START_OFFSET=0xc0;

    /* Building a Trie2 ---------------------------------------------------------- */

    /*
     * These definitions are mostly needed by utrie2_builder.c, but also by
     * utrie2_get32() and utrie2_enum().
     */

    /*
     * At build time, leave a gap in the index-2 table,
     * at least as long as the maximum lengths of the 2-byte UTF-8 index-2 table
     * and the supplementary index-1 table.
     * Round up to UTRIE2_INDEX_2_BLOCK_LENGTH for proper compacting.
     */
    static final int UNEWTRIE2_INDEX_GAP_OFFSET = UTRIE2_INDEX_2_BMP_LENGTH;
    static final int UNEWTRIE2_INDEX_GAP_LENGTH =
        ((UTRIE2_UTF8_2B_INDEX_2_LENGTH + UTRIE2_MAX_INDEX_1_LENGTH) + UTRIE2_INDEX_2_MASK) &
        ~UTRIE2_INDEX_2_MASK;

    /**
     * Maximum length of the build-time index-2 array.
     * Maximum number of Unicode code points (0x110000) shifted right by UTRIE2_SHIFT_2,
     * plus the part of the index-2 table for lead surrogate code points,
     * plus the build-time index gap,
     * plus the null index-2 block.
     */
    static final int UNEWTRIE2_MAX_INDEX_2_LENGTH=
        (0x110000>>UTRIE2_SHIFT_2)+
        UTRIE2_LSCP_INDEX_2_LENGTH+
        UNEWTRIE2_INDEX_GAP_LENGTH+
        UTRIE2_INDEX_2_BLOCK_LENGTH;

    static final int UNEWTRIE2_INDEX_1_LENGTH = 0x110000>>UTRIE2_SHIFT_1;

    /**
     * Maximum length of the build-time data array.
     * One entry per 0x110000 code points, plus the illegal-UTF-8 block and the null block,
     * plus values for the 0x400 surrogate code units.
     */
    static final int  UNEWTRIE2_MAX_DATA_LENGTH = (0x110000+0x40+0x40+0x400);



    /**
     * Implementation class for an iterator over a Trie2.
     *
     *   Iteration over a Trie2 first returns all of the ranges that are indexed by code points,
     *   then returns the special alternate values for the lead surrogates
     *
     * @internal
     */
    class Trie2Iterator implements Iterator<Range> {
        // The normal constructor that configures the iterator to cover the complete
        //   contents of the Trie2
        Trie2Iterator(ValueMapper vm) {
            mapper    = vm;
            nextStart = 0;
            limitCP   = 0x110000;
            doLeadSurrogates = true;
        }

        // An alternate constructor that configures the iterator to cover only the
        //   code points corresponding to a particular Lead Surrogate value.
        Trie2Iterator(char leadSurrogate, ValueMapper vm) {
            if (leadSurrogate < 0xd800 || leadSurrogate > 0xdbff) {
                throw new IllegalArgumentException("Bad lead surrogate value.");
            }
            mapper    = vm;
            nextStart = (leadSurrogate - 0xd7c0) << 10;
            limitCP   = nextStart + 0x400;
            doLeadSurrogates = false;   // Do not iterate over lead the special lead surrogate
                                        //   values after completing iteration over code points.
        }

        /**
         *  The main next() function for Trie2 iterators
         *
         */
        @Override
        public Range next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            if (nextStart >= limitCP) {
                // Switch over from iterating normal code point values to
                //   doing the alternate lead-surrogate values.
                doingCodePoints = false;
                nextStart = 0xd800;
            }
            int   endOfRange = 0;
            int   val = 0;
            int   mappedVal = 0;

            if (doingCodePoints) {
                // Iteration over code point values.
                val = get(nextStart);
                mappedVal = mapper.map(val);
                endOfRange = rangeEnd(nextStart, limitCP, val);
                // Loop once for each range in the Trie2 with the same raw (unmapped) value.
                // Loop continues so long as the mapped values are the same.
                for (;;) {
                    if (endOfRange >= limitCP-1) {
                        break;
                    }
                    val = get(endOfRange+1);
                    if (mapper.map(val) != mappedVal) {
                        break;
                    }
                    endOfRange = rangeEnd(endOfRange+1, limitCP, val);
                }
            } else {
                // Iteration over the alternate lead surrogate values.
                val = getFromU16SingleLead((char)nextStart);
                mappedVal = mapper.map(val);
                endOfRange = rangeEndLS((char)nextStart);
                // Loop once for each range in the Trie2 with the same raw (unmapped) value.
                // Loop continues so long as the mapped values are the same.
                for (;;) {
                    if (endOfRange >= 0xdbff) {
                        break;
                    }
                    val = getFromU16SingleLead((char)(endOfRange+1));
                    if (mapper.map(val) != mappedVal) {
                        break;
                    }
                    endOfRange = rangeEndLS((char)(endOfRange+1));
                }
            }
            returnValue.startCodePoint = nextStart;
            returnValue.endCodePoint   = endOfRange;
            returnValue.value          = mappedVal;
            returnValue.leadSurrogate  = !doingCodePoints;
            nextStart                  = endOfRange+1;
            return returnValue;
        }

        /**
         *
         */
        @Override
        public boolean hasNext() {
            return doingCodePoints && (doLeadSurrogates || nextStart < limitCP) || nextStart < 0xdc00;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }


        /**
         * Find the last lead surrogate in a contiguous range  with the
         * same Trie2 value as the input character.
         *
         * Use the alternate Lead Surrogate values from the Trie2,
         * not the code-point values.
         *
         * Note: Trie2_16 and Trie2_32 override this implementation with optimized versions,
         *       meaning that the implementation here is only being used with
         *       Trie2Writable.  The code here is logically correct with any type
         *       of Trie2, however.
         *
         * @param c  The character to begin with.
         * @return   The last contiguous character with the same value.
         */
        private int rangeEndLS(char startingLS) {
            if (startingLS >= 0xdbff) {
                return 0xdbff;
            }

            int c;
            int val = getFromU16SingleLead(startingLS);
            for (c = startingLS+1; c <= 0x0dbff; c++) {
                if (getFromU16SingleLead((char)c) != val) {
                    break;
                }
            }
            return c-1;
        }

        //
        //   Iteration State Variables
        //
        private ValueMapper    mapper;
        private Range          returnValue = new Range();
        // The starting code point for the next range to be returned.
        private int            nextStart;
        // The upper limit for the last normal range to be returned.  Normally 0x110000, but
        //   may be lower when iterating over the code points for a single lead surrogate.
        private int            limitCP;

        // True while iterating over the the Trie2 values for code points.
        // False while iterating over the alternate values for lead surrogates.
        private boolean        doingCodePoints = true;

        // True if the iterator should iterate the special values for lead surrogates in
        //   addition to the normal values for code points.
        private boolean        doLeadSurrogates = true;
    }

    /**
     * Find the last character in a contiguous range of characters with the
     * same Trie2 value as the input character.
     *
     * @param c  The character to begin with.
     * @return   The last contiguous character with the same value.
     */
    int rangeEnd(int start, int limitp, int val) {
        int c;
        int limit = Math.min(highStart, limitp);

        for (c = start+1; c < limit; c++) {
            if (get(c) != val) {
                break;
            }
        }
        if (c >= highStart) {
            c = limitp;
        }
        return c - 1;
    }


    //
    //  Hashing implementation functions.  FNV hash.  Respected public domain algorithm.
    //
    private static int initHash() {
        return 0x811c9DC5;  // unsigned 2166136261
    }

    private static int hashByte(int h, int b) {
        h = h * 16777619;
        h = h ^ b;
        return h;
    }

    private static int hashUChar32(int h, int c) {
        h = Trie2.hashByte(h, c & 255);
        h = Trie2.hashByte(h, (c>>8) & 255);
        h = Trie2.hashByte(h, c>>16);
        return h;
    }

    private static int hashInt(int h, int i) {
        h = Trie2.hashByte(h, i & 255);
        h = Trie2.hashByte(h, (i>>8) & 255);
        h = Trie2.hashByte(h, (i>>16) & 255);
        h = Trie2.hashByte(h, (i>>24) & 255);
        return h;
    }

}
