// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2009-2014, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */

package jdk.internal.icu.impl;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * @author aheninger
 *
 * A read-only Trie2, holding 32 bit data values.
 * 
 * A Trie2 is a highly optimized data structure for mapping from Unicode
 * code points (values ranging from 0 to 0x10ffff) to a 16 or 32 bit value.
 *
 * See class Trie2 for descriptions of the API for accessing the contents of a trie.
 * 
 * The fundamental data access methods are declared final in this class, with
 * the intent that applications might gain a little extra performance, when compared
 * with calling the same methods via the abstract UTrie2 base class.
 */

public class Trie2_32 extends Trie2 {
    
    /**
     * Internal constructor, not for general use.
     */
    Trie2_32() {
    }
    
    
    /**
     * Create a Trie2 from its serialized form.  Inverse of utrie2_serialize().
     * The serialized format is identical between ICU4C and ICU4J, so this function
     * will work with serialized Trie2s from either.
     *
     * The serialized Trie2 in the bytes may be in either little or big endian byte order.
     * This allows using serialized Tries from ICU4C without needing to consider the
     * byte order of the system that created them.
     *
     * @param bytes a byte buffer to the serialized form of a UTrie2.
     * @return An unserialized Trie_32, ready for use.
     * @throws IllegalArgumentException if the stream does not contain a serialized Trie2.
     * @throws IOException if a read error occurs in the buffer.
     * @throws ClassCastException if the bytes contains a serialized Trie2_16
     */
    public static Trie2_32 createFromSerialized(ByteBuffer bytes) throws IOException {
        return (Trie2_32) Trie2.createFromSerialized(bytes);
    }

    /**
     * Get the value for a code point as stored in the Trie2.
     *
     * @param codePoint the code point
     * @return the value
     */
    @Override
    public final int get(int codePoint) {
        int value;
        int ix;
        
        if (codePoint >= 0) {
            if (codePoint < 0x0d800 || (codePoint > 0x0dbff && codePoint <= 0x0ffff)) {
                // Ordinary BMP code point, excluding leading surrogates.
                // BMP uses a single level lookup.  BMP index starts at offset 0 in the Trie2 index.
                // 32 bit data is stored in the index array itself.
                ix = index[codePoint >> UTRIE2_SHIFT_2];
                ix = (ix << UTRIE2_INDEX_SHIFT) + (codePoint & UTRIE2_DATA_MASK);
                value = data32[ix];
                return value;
            } 
            if (codePoint <= 0xffff) {
                // Lead Surrogate Code Point.  A Separate index section is stored for
                // lead surrogate code units and code points.
                //   The main index has the code unit data.
                //   For this function, we need the code point data.
                // Note: this expression could be refactored for slightly improved efficiency, but
                //       surrogate code points will be so rare in practice that it's not worth it.
                ix = index[UTRIE2_LSCP_INDEX_2_OFFSET + ((codePoint - 0xd800) >> UTRIE2_SHIFT_2)];
                ix = (ix << UTRIE2_INDEX_SHIFT) + (codePoint & UTRIE2_DATA_MASK);
                value = data32[ix];
                return value;
            }
            if (codePoint < highStart) {
                // Supplemental code point, use two-level lookup.
                ix = (UTRIE2_INDEX_1_OFFSET - UTRIE2_OMITTED_BMP_INDEX_1_LENGTH) + (codePoint >> UTRIE2_SHIFT_1);
                ix = index[ix];
                ix += (codePoint >> UTRIE2_SHIFT_2) & UTRIE2_INDEX_2_MASK;
                ix = index[ix];
                ix = (ix << UTRIE2_INDEX_SHIFT) + (codePoint & UTRIE2_DATA_MASK);
                value = data32[ix];
                return value;
            }
            if (codePoint <= 0x10ffff) {
                value = data32[highValueIndex];
                return value;
            }
        }
        
        // Fall through.  The code point is outside of the legal range of 0..0x10ffff.
        return errorValue;
    }

    
    /**
     * Get a Trie2 value for a UTF-16 code unit.
     * 
     * This function returns the same value as get() if the input 
     * character is outside of the lead surrogate range
     * 
     * There are two values stored in a Trie2 for inputs in the lead
     * surrogate range.  This function returns the alternate value,
     * while Trie2.get() returns the main value.
     * 
     * @param codeUnit a 16 bit code unit or lead surrogate value.
     * @return the value
     */
    @Override
    public int getFromU16SingleLead(char codeUnit){
        int value;
        int ix;
        
        ix = index[codeUnit >> UTRIE2_SHIFT_2];
        ix = (ix << UTRIE2_INDEX_SHIFT) + (codeUnit & UTRIE2_DATA_MASK);
        value = data32[ix];
        return value;

    }
    
    /**
     * Serialize a Trie2_32 onto an OutputStream.
     * 
     * A Trie2 can be serialized multiple times.
     * The serialized data is compatible with ICU4C UTrie2 serialization.
     * Trie2 serialization is unrelated to Java object serialization.
     *  
     * @param os the stream to which the serialized Trie2 data will be written.
     * @return the number of bytes written.
     * @throw IOException on an error writing to the OutputStream.
     */
    public int serialize(OutputStream os) throws IOException {
        DataOutputStream dos = new DataOutputStream(os);
        int  bytesWritten = 0;
        
        bytesWritten += serializeHeader(dos);        
        for (int i=0; i<dataLength; i++) {
            dos.writeInt(data32[i]);
        }
        bytesWritten += dataLength*4;
        return bytesWritten;
    }

    /**
     * @return the number of bytes of the serialized trie
     */
    public int getSerializedLength() {
        return 16+header.indexLength*2+dataLength*4;
    }

    /**
     * Given a starting code point, find the last in a range of code points,
     * all with the same value.
     * 
     * This function is part of the implementation of iterating over the
     * Trie2's contents.
     * @param startingCP The code point at which to begin looking.
     * @return The last code point with the same value as the starting code point.
     */
    @Override
    int rangeEnd(int startingCP, int limit, int value) {
        int   cp = startingCP;
        int   block = 0;
        int   index2Block = 0;
        
        // Loop runs once for each of
        //   - a partial data block
        //   - a reference to the null (default) data block.
        //   - a reference to the index2 null block
        
      outerLoop:
        for (;;) {
            if (cp >= limit) {
                break;
            }
            if (cp < 0x0d800 || (cp > 0x0dbff && cp <= 0x0ffff)) {
                // Ordinary BMP code point, excluding leading surrogates.
                // BMP uses a single level lookup.  BMP index starts at offset 0 in the Trie2 index.
                // 16 bit data is stored in the index array itself.
                index2Block = 0;
                block       = index[cp >> UTRIE2_SHIFT_2] << UTRIE2_INDEX_SHIFT;
            } else if (cp < 0xffff) {
                // Lead Surrogate Code Point, 0xd800 <= cp < 0xdc00
                index2Block = UTRIE2_LSCP_INDEX_2_OFFSET;
                block       = index[index2Block + ((cp - 0xd800) >> UTRIE2_SHIFT_2)] << UTRIE2_INDEX_SHIFT;
            } else if (cp < highStart) {
                // Supplemental code point, use two-level lookup.
                int ix = (UTRIE2_INDEX_1_OFFSET - UTRIE2_OMITTED_BMP_INDEX_1_LENGTH) + (cp >> UTRIE2_SHIFT_1);
                index2Block = index[ix];
                block = index[index2Block + ((cp >> UTRIE2_SHIFT_2) & UTRIE2_INDEX_2_MASK)] << UTRIE2_INDEX_SHIFT;
            } else  {
                // Code point above highStart.
                if (value == data32[highValueIndex]) {
                    cp = limit;
                }
                break;
            } 
            
            if (index2Block == index2NullOffset) {
                if (value != initialValue) {
                    break;
                }
                cp += UTRIE2_CP_PER_INDEX_1_ENTRY;
            } else if (block == dataNullOffset) {
                // The block at dataNullOffset has all values == initialValue.
                // Because Trie2 iteration always proceeds in ascending order, we will always
                //   encounter a null block at its beginning, and can skip over
                //   a number of code points equal to the length of the block.
                if (value != initialValue) {
                    break;
                }
                cp += UTRIE2_DATA_BLOCK_LENGTH;
            } else {
                // Current position refers to an ordinary data block.
                // Walk over the data entries, checking the values.
                int startIx = block + (cp & UTRIE2_DATA_MASK);
                int limitIx = block + UTRIE2_DATA_BLOCK_LENGTH;
                for (int ix = startIx; ix<limitIx; ix++) {
                    if (data32[ix] != value) {
                        // We came to an entry with a different value.
                        //   We are done.
                        cp += (ix - startIx);
                        break outerLoop;
                    }
                }
                // The ordinary data block contained our value until its end.
                //  Advance the current code point, and continue the outer loop.
                cp += limitIx - startIx;
            }
        }
        if (cp > limit) {
            cp = limit;
        }
    
        return cp - 1;
    }

}

