/*
 * Portions Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 *******************************************************************************
 * (C) Copyright IBM Corp. 1996-2005 - All Rights Reserved                     *
 *                                                                             *
 * The original version of this source code and documentation is copyrighted   *
 * and owned by IBM, These materials are provided under terms of a License     *
 * Agreement between IBM and Sun. This technology is protected by multiple     *
 * US and International patents. This notice and attribution to IBM may not    *
 * to removed.                                                                 *
 *******************************************************************************
 */

package sun.text.normalizer;

import java.io.InputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
* <p>Internal reader class for ICU data file uprops.icu containing
* Unicode codepoint data.</p>
* <p>This class simply reads uprops.icu, authenticates that it is a valid
* ICU data file and split its contents up into blocks of data for use in
* <a href=UCharacterProperty.html>com.ibm.icu.impl.UCharacterProperty</a>.
* </p>
* <p>uprops.icu which is in big-endian format is jared together with this
* package.</p>
* @author Syn Wee Quek
* @since release 2.1, February 1st 2002
* @draft 2.1
*/
/* Unicode character properties file format ------------------------------------

The file format prepared and written here contains several data
structures that store indexes or data.



The following is a description of format version 3 .

Data contents:

The contents is a parsed, binary form of several Unicode character
database files, most prominently UnicodeData.txt.

Any Unicode code point from 0 to 0x10ffff can be looked up to get
the properties, if any, for that code point. This means that the input
to the lookup are 21-bit unsigned integers, with not all of the
21-bit range used.

It is assumed that client code keeps a uint32_t pointer
to the beginning of the data:

    const uint32_t *p32;

Formally, the file contains the following structures:

    const int32_t indexes[16] with values i0..i15:

    i0 propsIndex; -- 32-bit unit index to the table of 32-bit properties words
    i1 exceptionsIndex;  -- 32-bit unit index to the table of 32-bit exception words
    i2 exceptionsTopIndex; -- 32-bit unit index to the array of UChars for special mappings

    i3 additionalTrieIndex; -- 32-bit unit index to the additional trie for more properties
    i4 additionalVectorsIndex; -- 32-bit unit index to the table of properties vectors
    i5 additionalVectorsColumns; -- number of 32-bit words per properties vector

    i6 reservedItemIndex; -- 32-bit unit index to the top of the properties vectors table
    i7..i9 reservedIndexes; -- reserved values; 0 for now

    i10 maxValues; -- maximum code values for vector word 0, see uprops.h (format version 3.1+)
    i11 maxValues2; -- maximum code values for vector word 2, see uprops.h (format version 3.2)
    i12..i15 reservedIndexes; -- reserved values; 0 for now

    PT serialized properties trie, see utrie.h (byte size: 4*(i0-16))

    P  const uint32_t props32[i1-i0];
    E  const uint32_t exceptions[i2-i1];
    U  const UChar uchars[2*(i3-i2)];

    AT serialized trie for additional properties (byte size: 4*(i4-i3))
    PV const uint32_t propsVectors[(i6-i4)/i5][i5]==uint32_t propsVectors[i6-i4];

Trie lookup and properties:

In order to condense the data for the 21-bit code space, several properties of
the Unicode code assignment are exploited:
- The code space is sparse.
- There are several 10k of consecutive codes with the same properties.
- Characters and scripts are allocated in groups of 16 code points.
- Inside blocks for scripts the properties are often repetitive.
- The 21-bit space is not fully used for Unicode.

The lookup of properties for a given code point is done with a trie lookup,
using the UTrie implementation.
The trie lookup result is a 16-bit index in the props32[] table where the
actual 32-bit properties word is stored. This is done to save space.

(There are thousands of 16-bit entries in the trie data table, but
only a few hundred unique 32-bit properties words.
If the trie data table contained 32-bit words directly, then that would be
larger because the length of the table would be the same as now but the
width would be 32 bits instead of 16. This saves more than 10kB.)

With a given Unicode code point

    UChar32 c;

and 0<=c<0x110000, the lookup is done like this:

    uint16_t i;
    UTRIE_GET16(c, i);
    uint32_t props=p32[i];

For some characters, not all of the properties can be efficiently encoded
using 32 bits. For them, the 32-bit word contains an index into the exceptions[]
array:

    if(props&EXCEPTION_BIT)) {
        uint16_t e=(uint16_t)(props>>VALUE_SHIFT);
        ...
    }

The exception values are a variable number of uint32_t starting at

    const uint32_t *pe=p32+exceptionsIndex+e;

The first uint32_t there contains flags about what values actually follow it.
Some of the exception values are UChar32 code points for the case mappings,
others are numeric values etc.

32-bit properties sets:

Each 32-bit properties word contains:

 0.. 4  general category
 5      has exception values
 6..10  BiDi category
11      is mirrored
12..14  numericType:
            0 no numeric value
            1 decimal digit value
            2 digit value
            3 numeric value
            ### TODO: type 4 for Han digits & numbers?!
15..19  reserved
20..31  value according to bits 0..5:
        if(has exception) {
            exception index;
        } else switch(general category) {
        case Ll: delta to uppercase; -- same as titlecase
        case Lu: -delta to lowercase; -- titlecase is same as c
        case Lt: -delta to lowercase; -- uppercase is same as c
        default:
            if(is mirrored) {
                delta to mirror;
            } else if(numericType!=0) {
                numericValue;
            } else {
                0;
            };
        }

Exception values:

In the first uint32_t exception word for a code point,
bits
31..16  reserved
15..0   flags that indicate which values follow:

bit
 0      has uppercase mapping
 1      has lowercase mapping
 2      has titlecase mapping
 3      unused
 4      has numeric value (numerator)
            if numericValue=0x7fffff00+x then numericValue=10^x
 5      has denominator value
 6      has a mirror-image Unicode code point
 7      has SpecialCasing.txt entries
 8      has CaseFolding.txt entries

According to the flags in this word, one or more uint32_t words follow it
in the sequence of the bit flags in the flags word; if a flag is not set,
then the value is missing or 0:

For the case mappings and the mirror-image Unicode code point,
one uint32_t or UChar32 each is the code point.
If the titlecase mapping is missing, then it is the same as the uppercase mapping.

For the digit values, bits 31..16 contain the decimal digit value, and
bits 15..0 contain the digit value. A value of -1 indicates that
this value is missing.

For the numeric/numerator value, an int32_t word contains the value directly,
except for when there is no numerator but a denominator, then the numerator
is implicitly 1. This means:
    numerator denominator result
    none      none        none
    x         none        x
    none      y           1/y
    x         y           x/y

If the numerator value is 0x7fffff00+x then it is replaced with 10^x.

For the denominator value, a uint32_t word contains the value directly.

For special casing mappings, the 32-bit exception word contains:
31      if set, this character has complex, conditional mappings
        that are not stored;
        otherwise, the mappings are stored according to the following bits
30..24  number of UChars used for mappings
23..16  reserved
15.. 0  UChar offset from the beginning of the UChars array where the
        UChars for the special case mappings are stored in the following format:

Format of special casing UChars:
One UChar value with lengths as follows:
14..10  number of UChars for titlecase mapping
 9.. 5  number of UChars for uppercase mapping
 4.. 0  number of UChars for lowercase mapping

Followed by the UChars for lowercase, uppercase, titlecase mappings in this order.

For case folding mappings, the 32-bit exception word contains:
31..24  number of UChars used for the full mapping
23..16  reserved
15.. 0  UChar offset from the beginning of the UChars array where the
        UChars for the special case mappings are stored in the following format:

Format of case folding UChars:
Two UChars contain the simple mapping as follows:
    0,  0   no simple mapping
    BMP,0   a simple mapping to a BMP code point
    s1, s2  a simple mapping to a supplementary code point stored as two surrogates
This is followed by the UChars for the full case folding mappings.

Example:
U+2160, ROMAN NUMERAL ONE, needs an exception because it has a lowercase
mapping and a numeric value.
Its exception values would be stored as 3 uint32_t words:

- flags=0x0a (see above) with combining class 0
- lowercase mapping 0x2170
- numeric value=1

--- Additional properties (new in format version 2.1) ---

The second trie for additional properties (AT) is also a UTrie with 16-bit data.
The data words consist of 32-bit unit indexes (not row indexes!) into the
table of unique properties vectors (PV).
Each vector contains a set of properties.
The width of a vector (number of uint32_t per row) may change
with the formatVersion, it is stored in i5.

Current properties: see icu/source/common/uprops.h

--- Changes in format version 3.1 ---

See i10 maxValues above, contains only UBLOCK_COUNT and USCRIPT_CODE_LIMIT.

--- Changes in format version 3.2 ---

- The tries use linear Latin-1 ranges.
- The additional properties bits store full properties XYZ instead
  of partial Other_XYZ, so that changes in the derivation formulas
  need not be tracked in runtime library code.
- Joining Type and Line Break are also stored completely, so that uprops.c
  needs no runtime formulas for enumerated properties either.
- Store the case-sensitive flag in the main properties word.
- i10 also contains U_LB_COUNT and U_EA_COUNT.
- i11 contains maxValues2 for vector word 2.

----------------------------------------------------------------------------- */

final class UCharacterPropertyReader implements ICUBinary.Authenticate
{
    // public methods ----------------------------------------------------

    public boolean isDataVersionAcceptable(byte version[])
    {
        return version[0] == DATA_FORMAT_VERSION_[0]
               && version[2] == DATA_FORMAT_VERSION_[2]
               && version[3] == DATA_FORMAT_VERSION_[3];
    }

    // protected constructor ---------------------------------------------

    /**
    * <p>Protected constructor.</p>
    * @param inputStream ICU uprop.dat file input stream
    * @exception IOException throw if data file fails authentication
    * @draft 2.1
    */
    protected UCharacterPropertyReader(InputStream inputStream)
                                                        throws IOException
    {
        m_unicodeVersion_ = ICUBinary.readHeader(inputStream, DATA_FORMAT_ID_,
                                                 this);
        m_dataInputStream_ = new DataInputStream(inputStream);
    }

    // protected methods -------------------------------------------------

    /**
    * <p>Reads uprops.icu, parse it into blocks of data to be stored in
    * UCharacterProperty.</P
    * @param ucharppty UCharacterProperty instance
    * @exception thrown when data reading fails
    * @draft 2.1
    */
    protected void read(UCharacterProperty ucharppty) throws IOException
    {
        // read the indexes
        int count = INDEX_SIZE_;
        m_propertyOffset_          = m_dataInputStream_.readInt();
        count --;
        m_exceptionOffset_         = m_dataInputStream_.readInt();
        count --;
        m_caseOffset_              = m_dataInputStream_.readInt();
        count --;
        m_additionalOffset_        = m_dataInputStream_.readInt();
        count --;
        m_additionalVectorsOffset_ = m_dataInputStream_.readInt();
        count --;
        m_additionalColumnsCount_  = m_dataInputStream_.readInt();
        count --;
        m_reservedOffset_          = m_dataInputStream_.readInt();
        count --;
        m_dataInputStream_.skipBytes(3 << 2);
        count -= 3;
        ucharppty.m_maxBlockScriptValue_ = m_dataInputStream_.readInt();
        count --; // 10
        ucharppty.m_maxJTGValue_ = m_dataInputStream_.readInt();
        count --; // 11
        m_dataInputStream_.skipBytes(count << 2);

        // read the trie index block
        // m_props_index_ in terms of ints
        ucharppty.m_trie_ = new CharTrie(m_dataInputStream_, ucharppty);

        // reads the 32 bit properties block
        int size = m_exceptionOffset_ - m_propertyOffset_;
        ucharppty.m_property_ = new int[size];
        for (int i = 0; i < size; i ++) {
            ucharppty.m_property_[i] = m_dataInputStream_.readInt();
        }

        // reads the 32 bit exceptions block
        size = m_caseOffset_ - m_exceptionOffset_;
        ucharppty.m_exception_ = new int[size];
        for (int i = 0; i < size; i ++) {
            ucharppty.m_exception_[i] = m_dataInputStream_.readInt();
        }

        // reads the 32 bit case block
        size = (m_additionalOffset_ - m_caseOffset_) << 1;
        ucharppty.m_case_ = new char[size];
        for (int i = 0; i < size; i ++) {
            ucharppty.m_case_[i] = m_dataInputStream_.readChar();
        }

        // reads the additional property block
        ucharppty.m_additionalTrie_ = new CharTrie(m_dataInputStream_,
                                                   ucharppty);

        // additional properties
        size = m_reservedOffset_ - m_additionalVectorsOffset_;
        ucharppty.m_additionalVectors_ = new int[size];
        for (int i = 0; i < size; i ++) {
            ucharppty.m_additionalVectors_[i] = m_dataInputStream_.readInt();
        }

        m_dataInputStream_.close();
        ucharppty.m_additionalColumnsCount_ = m_additionalColumnsCount_;
        ucharppty.m_unicodeVersion_ = VersionInfo.getInstance(
                         (int)m_unicodeVersion_[0], (int)m_unicodeVersion_[1],
                         (int)m_unicodeVersion_[2], (int)m_unicodeVersion_[3]);
    }

    // private variables -------------------------------------------------

    /**
    * Index size
    */
    private static final int INDEX_SIZE_ = 16;

    /**
    * ICU data file input stream
    */
    private DataInputStream m_dataInputStream_;

    /**
    * Offset information in the indexes.
    */
    private int m_propertyOffset_;
    private int m_exceptionOffset_;
    private int m_caseOffset_;
    private int m_additionalOffset_;
    private int m_additionalVectorsOffset_;
    private int m_additionalColumnsCount_;
    private int m_reservedOffset_;
    private byte m_unicodeVersion_[];

    /**
    * File format version that this class understands.
    * No guarantees are made if a older version is used
    */
    private static final byte DATA_FORMAT_ID_[] = {(byte)0x55, (byte)0x50,
                                                    (byte)0x72, (byte)0x6F};
    private static final byte DATA_FORMAT_VERSION_[] = {(byte)0x3, (byte)0x1,
                                             (byte)Trie.INDEX_STAGE_1_SHIFT_,
                                             (byte)Trie.INDEX_STAGE_2_SHIFT_};
}
