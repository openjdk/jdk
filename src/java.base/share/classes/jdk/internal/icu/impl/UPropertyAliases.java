// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 **********************************************************************
 * Copyright (c) 2002-2015, International Business Machines
 * Corporation and others.  All Rights Reserved.
 **********************************************************************
 * Author: Alan Liu
 * Created: November 5 2002
 * Since: ICU 2.4
 * 2010nov19 Markus Scherer  Rewrite for formatVersion 2.
 **********************************************************************
 */

package jdk.internal.icu.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.MissingResourceException;

import jdk.internal.icu.lang.UProperty;
import jdk.internal.icu.util.BytesTrie;

/**
 * Wrapper for the pnames.icu binary data file.  This data file is
 * imported from icu4c.  It contains property and property value
 * aliases from the UCD files PropertyAliases.txt and
 * PropertyValueAliases.txt.  The file is built by the icu4c tool
 * genpname.  It must be an ASCII big-endian file to be
 * usable in icu4j.
 *
 * This class performs two functions.
 *
 * (1) It can import the flat binary data into usable objects.
 *
 * (2) It provides an API to access the tree of objects.
 *
 * Needless to say, this class is tightly coupled to the binary format
 * of icu4c's pnames.icu file.
 *
 * Each time a UPropertyAliases is constructed, the pnames.icu file is
 * read, parsed, and data structures assembled.  Clients should create one
 * singleton instance and cache it.
 *
 * @author Alan Liu
 * @since ICU 2.4
 */
@SuppressWarnings("deprecation")
public final class UPropertyAliases {
    // Byte offsets from the start of the data, after the generic header.
    private static final int IX_VALUE_MAPS_OFFSET=0;
    private static final int IX_BYTE_TRIES_OFFSET=1;
    private static final int IX_NAME_GROUPS_OFFSET=2;
    private static final int IX_RESERVED3_OFFSET=3;
    // private static final int IX_RESERVED4_OFFSET=4;
    // private static final int IX_TOTAL_SIZE=5;

    // Other values.
    // private static final int IX_MAX_NAME_LENGTH=6;
    // private static final int IX_RESERVED7=7;
    // private static final int IX_COUNT=8;

    //----------------------------------------------------------------
    // Runtime data.  This is an unflattened representation of the
    // data in pnames.icu.

    private int[] valueMaps;
    private byte[] bytesTries;
    private String nameGroups;

    private static final class IsAcceptable implements ICUBinary.Authenticate {
        @Override
        public boolean isDataVersionAcceptable(byte version[]) {
            return version[0]==2;
        }
    }
    private static final IsAcceptable IS_ACCEPTABLE=new IsAcceptable();
    private static final int DATA_FORMAT=0x706E616D;  // "pnam"

    private void load(ByteBuffer bytes) throws IOException {
        //dataVersion=ICUBinary.readHeaderAndDataVersion(bytes, DATA_FORMAT, IS_ACCEPTABLE);
        ICUBinary.readHeader(bytes, DATA_FORMAT, IS_ACCEPTABLE);
        int indexesLength=bytes.getInt()/4;  // inIndexes[IX_VALUE_MAPS_OFFSET]/4
        if(indexesLength<8) {  // formatVersion 2 initially has 8 indexes
            throw new IOException("pnames.icu: not enough indexes");
        }
        int[] inIndexes=new int[indexesLength];
        inIndexes[0]=indexesLength*4;
        for(int i=1; i<indexesLength; ++i) {
            inIndexes[i]=bytes.getInt();
        }

        // Read the valueMaps.
        int offset=inIndexes[IX_VALUE_MAPS_OFFSET];
        int nextOffset=inIndexes[IX_BYTE_TRIES_OFFSET];
        int numInts=(nextOffset-offset)/4;
        valueMaps=ICUBinary.getInts(bytes, numInts, 0);

        // Read the bytesTries.
        offset=nextOffset;
        nextOffset=inIndexes[IX_NAME_GROUPS_OFFSET];
        int numBytes=nextOffset-offset;
        bytesTries=new byte[numBytes];
        bytes.get(bytesTries);

        // Read the nameGroups and turn them from ASCII bytes into a Java String.
        offset=nextOffset;
        nextOffset=inIndexes[IX_RESERVED3_OFFSET];
        numBytes=nextOffset-offset;
        StringBuilder sb=new StringBuilder(numBytes);
        for(int i=0; i<numBytes; ++i) {
            sb.append((char)bytes.get());
        }
        nameGroups=sb.toString();
    }

    private UPropertyAliases() throws IOException {
        ByteBuffer bytes = ICUBinary.getRequiredData("pnames.icu");
        load(bytes);
    }

    private int findProperty(int property) {
        int i=1;  // valueMaps index, initially after numRanges
        for(int numRanges=valueMaps[0]; numRanges>0; --numRanges) {
            // Read and skip the start and limit of this range.
            int start=valueMaps[i];
            int limit=valueMaps[i+1];
            i+=2;
            if(property<start) {
                break;
            }
            if(property<limit) {
                return i+(property-start)*2;
            }
            i+=(limit-start)*2;  // Skip all entries for this range.
        }
        return 0;
    }

    private int findPropertyValueNameGroup(int valueMapIndex, int value) {
        if(valueMapIndex==0) {
            return 0;  // The property does not have named values.
        }
        ++valueMapIndex;  // Skip the BytesTrie offset.
        int numRanges=valueMaps[valueMapIndex++];
        if(numRanges<0x10) {
            // Ranges of values.
            for(; numRanges>0; --numRanges) {
                // Read and skip the start and limit of this range.
                int start=valueMaps[valueMapIndex];
                int limit=valueMaps[valueMapIndex+1];
                valueMapIndex+=2;
                if(value<start) {
                    break;
                }
                if(value<limit) {
                    return valueMaps[valueMapIndex+value-start];
                }
                valueMapIndex+=limit-start;  // Skip all entries for this range.
            }
        } else {
            // List of values.
            int valuesStart=valueMapIndex;
            int nameGroupOffsetsStart=valueMapIndex+numRanges-0x10;
            do {
                int v=valueMaps[valueMapIndex];
                if(value<v) {
                    break;
                }
                if(value==v) {
                    return valueMaps[nameGroupOffsetsStart+valueMapIndex-valuesStart];
                }
            } while(++valueMapIndex<nameGroupOffsetsStart);
        }
        return 0;
    }

    private String getName(int nameGroupsIndex, int nameIndex) {
        int numNames=nameGroups.charAt(nameGroupsIndex++);
        if(nameIndex<0 || numNames<=nameIndex) {
            throw new IllegalIcuArgumentException("Invalid property (value) name choice");
        }
        // Skip nameIndex names.
        for(; nameIndex>0; --nameIndex) {
            while(0!=nameGroups.charAt(nameGroupsIndex++)) {}
        }
        // Find the end of this name.
        int nameStart=nameGroupsIndex;
        while(0!=nameGroups.charAt(nameGroupsIndex)) {
            ++nameGroupsIndex;
        }
        if(nameStart==nameGroupsIndex) {
            return null;  // no name (Property[Value]Aliases.txt has "n/a")
        }
        return nameGroups.substring(nameStart, nameGroupsIndex);
    }

    private static int asciiToLowercase(int c) {
        return 'A'<=c && c<='Z' ? c+0x20 : c;
    }

    private boolean containsName(BytesTrie trie, CharSequence name) {
        BytesTrie.Result result=BytesTrie.Result.NO_VALUE;
        for(int i=0; i<name.length(); ++i) {
            int c=name.charAt(i);
            // Ignore delimiters '-', '_', and ASCII White_Space.
            if(c=='-' || c=='_' || c==' ' || (0x09<=c && c<=0x0d)) {
                continue;
            }
            if(!result.hasNext()) {
                return false;
            }
            c=asciiToLowercase(c);
            result=trie.next(c);
        }
        return result.hasValue();
    }

    //----------------------------------------------------------------
    // Public API

    public static final UPropertyAliases INSTANCE;

    static {
        try {
            INSTANCE = new UPropertyAliases();
        } catch(IOException e) {
            ///CLOVER:OFF
            MissingResourceException mre = new MissingResourceException(
                    "Could not construct UPropertyAliases. Missing pnames.icu", "", "");
            mre.initCause(e);
            throw mre;
            ///CLOVER:ON
        }
    }

    /**
     * Returns a property name given a property enum.
     * Multiple names may be available for each property;
     * the nameChoice selects among them.
     */
    public String getPropertyName(int property, int nameChoice) {
        int valueMapIndex=findProperty(property);
        if(valueMapIndex==0) {
            throw new IllegalArgumentException(
                    "Invalid property enum "+property+" (0x"+Integer.toHexString(property)+")");
        }
        return getName(valueMaps[valueMapIndex], nameChoice);
    }

    /**
     * Returns a value name given a property enum and a value enum.
     * Multiple names may be available for each value;
     * the nameChoice selects among them.
     */
    public String getPropertyValueName(int property, int value, int nameChoice) {
        int valueMapIndex=findProperty(property);
        if(valueMapIndex==0) {
            throw new IllegalArgumentException(
                    "Invalid property enum "+property+" (0x"+Integer.toHexString(property)+")");
        }
        int nameGroupOffset=findPropertyValueNameGroup(valueMaps[valueMapIndex+1], value);
        if(nameGroupOffset==0) {
            throw new IllegalArgumentException(
                    "Property "+property+" (0x"+Integer.toHexString(property)+
                    ") does not have named values");
        }
        return getName(nameGroupOffset, nameChoice);
    }

    private int getPropertyOrValueEnum(int bytesTrieOffset, CharSequence alias) {
        BytesTrie trie=new BytesTrie(bytesTries, bytesTrieOffset);
        if(containsName(trie, alias)) {
            return trie.getValue();
        } else {
            return UProperty.UNDEFINED;
        }
    }

    /**
     * Returns a property enum given one of its property names.
     * If the property name is not known, this method returns
     * UProperty.UNDEFINED.
     */
    public int getPropertyEnum(CharSequence alias) {
        return getPropertyOrValueEnum(0, alias);
    }

    /**
     * Returns a value enum given a property enum and one of its value names.
     */
    public int getPropertyValueEnum(int property, CharSequence alias) {
        int valueMapIndex=findProperty(property);
        if(valueMapIndex==0) {
            throw new IllegalArgumentException(
                    "Invalid property enum "+property+" (0x"+Integer.toHexString(property)+")");
        }
        valueMapIndex=valueMaps[valueMapIndex+1];
        if(valueMapIndex==0) {
            throw new IllegalArgumentException(
                    "Property "+property+" (0x"+Integer.toHexString(property)+
                    ") does not have named values");
        }
        // valueMapIndex is the start of the property's valueMap,
        // where the first word is the BytesTrie offset.
        return getPropertyOrValueEnum(valueMaps[valueMapIndex], alias);
    }

    /**
     * Returns a value enum given a property enum and one of its value names. Does not throw.
     * @return value enum, or UProperty.UNDEFINED if not defined for that property
     */
    public int getPropertyValueEnumNoThrow(int property, CharSequence alias) {
        int valueMapIndex=findProperty(property);
        if(valueMapIndex==0) {
            return UProperty.UNDEFINED;
        }
        valueMapIndex=valueMaps[valueMapIndex+1];
        if(valueMapIndex==0) {
            return UProperty.UNDEFINED;
        }
        // valueMapIndex is the start of the property's valueMap,
        // where the first word is the BytesTrie offset.
        return getPropertyOrValueEnum(valueMaps[valueMapIndex], alias);
    }

    /**
     * Compare two property names, returning <0, 0, or >0.  The
     * comparison is that described as "loose" matching in the
     * Property*Aliases.txt files.
     */
    public static int compare(String stra, String strb) {
        // Note: This implementation is a literal copy of
        // uprv_comparePropertyNames.  It can probably be improved.
        int istra=0, istrb=0, rc;
        int cstra=0, cstrb=0;
        for (;;) {
            /* Ignore delimiters '-', '_', and ASCII White_Space */
            while (istra<stra.length()) {
                cstra = stra.charAt(istra);
                switch (cstra) {
                case '-':  case '_':  case ' ':  case '\t':
                case '\n': case 0xb/*\v*/: case '\f': case '\r':
                    ++istra;
                    continue;
                }
                break;
            }

            while (istrb<strb.length()) {
                cstrb = strb.charAt(istrb);
                switch (cstrb) {
                case '-':  case '_':  case ' ':  case '\t':
                case '\n': case 0xb/*\v*/: case '\f': case '\r':
                    ++istrb;
                    continue;
                }
                break;
            }

            /* If we reach the ends of both strings then they match */
            boolean endstra = istra==stra.length();
            boolean endstrb = istrb==strb.length();
            if (endstra) {
                if (endstrb) return 0;
                cstra = 0;
            } else if (endstrb) {
                cstrb = 0;
            }

            rc = asciiToLowercase(cstra) - asciiToLowercase(cstrb);
            if (rc != 0) {
                return rc;
            }

            ++istra;
            ++istrb;
        }
    }
}
