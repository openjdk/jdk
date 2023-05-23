// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 *
 *   Copyright (C) 2004-2015, International Business Machines
 *   Corporation and others.  All Rights Reserved.
 *
 *******************************************************************************
 *   file name:  UCaseProps.java
 *   encoding:   US-ASCII
 *   tab size:   8 (not used)
 *   indentation:4
 *
 *   created on: 2005jan29
 *   created by: Markus W. Scherer
 *
 *   Low-level Unicode character/string case mapping code.
 *   Java port of ucase.h/.c.
 */

package jdk.internal.icu.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Locale;

import jdk.internal.icu.lang.UCharacter;
import jdk.internal.icu.lang.UProperty;
import jdk.internal.icu.text.UTF16;
import jdk.internal.icu.text.UnicodeSet;
import jdk.internal.icu.util.ULocale;

public final class UCaseProps {

    // constructors etc. --------------------------------------------------- ***

    // port of ucase_openProps()
    private UCaseProps() throws IOException {
        ByteBuffer bytes=ICUBinary.getRequiredData(DATA_FILE_NAME);
        readData(bytes);
    }

    private final void readData(ByteBuffer bytes) throws IOException {
        // read the header
        ICUBinary.readHeader(bytes, FMT, new IsAcceptable());

        // read indexes[]
        int count=bytes.getInt();
        if(count<IX_TOP) {
            throw new IOException("indexes[0] too small in "+DATA_FILE_NAME);
        }
        indexes=new int[count];

        indexes[0]=count;
        for(int i=1; i<count; ++i) {
            indexes[i]=bytes.getInt();
        }

        // read the trie
        trie=Trie2_16.createFromSerialized(bytes);
        int expectedTrieLength=indexes[IX_TRIE_SIZE];
        int trieLength=trie.getSerializedLength();
        if(trieLength>expectedTrieLength) {
            throw new IOException(DATA_FILE_NAME+": not enough bytes for the trie");
        }
        // skip padding after trie bytes
        ICUBinary.skipBytes(bytes, expectedTrieLength-trieLength);

        // read exceptions[]
        count=indexes[IX_EXC_LENGTH];
        if(count>0) {
            exceptions=ICUBinary.getString(bytes, count, 0);
        }

        // read unfold[]
        count=indexes[IX_UNFOLD_LENGTH];
        if(count>0) {
            unfold=ICUBinary.getChars(bytes, count, 0);
        }
    }

    // implement ICUBinary.Authenticate
    private final static class IsAcceptable implements ICUBinary.Authenticate {
        @Override
        public boolean isDataVersionAcceptable(byte version[]) {
            return version[0]==4;
        }
    }

    // set of property starts for UnicodeSet ------------------------------- ***

    public final void addPropertyStarts(UnicodeSet set) {
        /* add the start code point of each same-value range of the trie */
        Iterator<Trie2.Range> trieIterator=trie.iterator();
        Trie2.Range range;
        while(trieIterator.hasNext() && !(range=trieIterator.next()).leadSurrogate) {
            set.add(range.startCodePoint);
        }

        /* add code points with hardcoded properties, plus the ones following them */

        /* (none right now, see comment below) */

        /*
         * Omit code points with hardcoded specialcasing properties
         * because we do not build property UnicodeSets for them right now.
         */
    }

    // data access primitives ---------------------------------------------- ***
    private static final int getExceptionsOffset(int props) {
        return props>>EXC_SHIFT;
    }

    static final boolean propsHasException(int props) {
        return (props&EXCEPTION)!=0;
    }

    /* number of bits in an 8-bit integer value */
    private static final byte flagsOffset[/*256*/]={
        0, 1, 1, 2, 1, 2, 2, 3, 1, 2, 2, 3, 2, 3, 3, 4,
        1, 2, 2, 3, 2, 3, 3, 4, 2, 3, 3, 4, 3, 4, 4, 5,
        1, 2, 2, 3, 2, 3, 3, 4, 2, 3, 3, 4, 3, 4, 4, 5,
        2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
        1, 2, 2, 3, 2, 3, 3, 4, 2, 3, 3, 4, 3, 4, 4, 5,
        2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
        2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
        3, 4, 4, 5, 4, 5, 5, 6, 4, 5, 5, 6, 5, 6, 6, 7,
        1, 2, 2, 3, 2, 3, 3, 4, 2, 3, 3, 4, 3, 4, 4, 5,
        2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
        2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
        3, 4, 4, 5, 4, 5, 5, 6, 4, 5, 5, 6, 5, 6, 6, 7,
        2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
        3, 4, 4, 5, 4, 5, 5, 6, 4, 5, 5, 6, 5, 6, 6, 7,
        3, 4, 4, 5, 4, 5, 5, 6, 4, 5, 5, 6, 5, 6, 6, 7,
        4, 5, 5, 6, 5, 6, 6, 7, 5, 6, 6, 7, 6, 7, 7, 8
    };

    private static final boolean hasSlot(int flags, int index) {
        return (flags&(1<<index))!=0;
    }
    private static final byte slotOffset(int flags, int index) {
        return flagsOffset[flags&((1<<index)-1)];
    }

    /*
     * Get the value of an optional-value slot where hasSlot(excWord, index).
     *
     * @param excWord (in) initial exceptions word
     * @param index (in) desired slot index
     * @param excOffset (in) offset into exceptions[] after excWord=exceptions.charAt(excOffset++);
     * @return bits 31..0: slot value
     *             63..32: modified excOffset, moved to the last char of the value, use +1 for beginning of next slot
     */
    private final long getSlotValueAndOffset(int excWord, int index, int excOffset) {
        long value;
        if((excWord&EXC_DOUBLE_SLOTS)==0) {
            excOffset+=slotOffset(excWord, index);
            value=exceptions.charAt(excOffset);
        } else {
            excOffset+=2*slotOffset(excWord, index);
            value=exceptions.charAt(excOffset++);
            value=(value<<16)|exceptions.charAt(excOffset);
        }
        return value |((long)excOffset<<32);
    }

    /* same as getSlotValueAndOffset() but does not return the slot offset */
    private final int getSlotValue(int excWord, int index, int excOffset) {
        int value;
        if((excWord&EXC_DOUBLE_SLOTS)==0) {
            excOffset+=slotOffset(excWord, index);
            value=exceptions.charAt(excOffset);
        } else {
            excOffset+=2*slotOffset(excWord, index);
            value=exceptions.charAt(excOffset++);
            value=(value<<16)|exceptions.charAt(excOffset);
        }
        return value;
    }

    // simple case mappings ------------------------------------------------ ***

    public final int tolower(int c) {
        int props=trie.get(c);
        if(!propsHasException(props)) {
            if(isUpperOrTitleFromProps(props)) {
                c+=getDelta(props);
            }
        } else {
            int excOffset=getExceptionsOffset(props);
            int excWord=exceptions.charAt(excOffset++);
            if(hasSlot(excWord, EXC_DELTA) && isUpperOrTitleFromProps(props)) {
                int delta=getSlotValue(excWord, EXC_DELTA, excOffset);
                return (excWord&EXC_DELTA_IS_NEGATIVE)==0 ? c+delta : c-delta;
            }
            if(hasSlot(excWord, EXC_LOWER)) {
                c=getSlotValue(excWord, EXC_LOWER, excOffset);
            }
        }
        return c;
    }

    public final int toupper(int c) {
        int props=trie.get(c);
        if(!propsHasException(props)) {
            if(getTypeFromProps(props)==LOWER) {
                c+=getDelta(props);
            }
        } else {
            int excOffset=getExceptionsOffset(props);
            int excWord=exceptions.charAt(excOffset++);
            if(hasSlot(excWord, EXC_DELTA) && getTypeFromProps(props)==LOWER) {
                int delta=getSlotValue(excWord, EXC_DELTA, excOffset);
                return (excWord&EXC_DELTA_IS_NEGATIVE)==0 ? c+delta : c-delta;
            }
            if(hasSlot(excWord, EXC_UPPER)) {
                c=getSlotValue(excWord, EXC_UPPER, excOffset);
            }
        }
        return c;
    }

    public final int totitle(int c) {
        int props=trie.get(c);
        if(!propsHasException(props)) {
            if(getTypeFromProps(props)==LOWER) {
                c+=getDelta(props);
            }
        } else {
            int excOffset=getExceptionsOffset(props);
            int excWord=exceptions.charAt(excOffset++);
            if(hasSlot(excWord, EXC_DELTA) && getTypeFromProps(props)==LOWER) {
                int delta=getSlotValue(excWord, EXC_DELTA, excOffset);
                return (excWord&EXC_DELTA_IS_NEGATIVE)==0 ? c+delta : c-delta;
            }
            int index;
            if(hasSlot(excWord, EXC_TITLE)) {
                index=EXC_TITLE;
            } else if(hasSlot(excWord, EXC_UPPER)) {
                index=EXC_UPPER;
            } else {
                return c;
            }
            c=getSlotValue(excWord, index, excOffset);
        }
        return c;
    }

    /**
     * Adds all simple case mappings and the full case folding for c to sa,
     * and also adds special case closure mappings.
     * c itself is not added.
     * For example, the mappings
     * - for s include long s
     * - for sharp s include ss
     * - for k include the Kelvin sign
     */
    public final void addCaseClosure(int c, UnicodeSet set) {
        int props=trie.get(c);
        if(!propsHasException(props)) {
            if(getTypeFromProps(props)!=NONE) {
                /* add the one simple case mapping, no matter what type it is */
                int delta=getDelta(props);
                if(delta!=0) {
                    set.add(c+delta);
                }
            }
        } else {
            /*
             * c has exceptions, so there may be multiple simple and/or
             * full case mappings. Add them all.
             */
            int excOffset=getExceptionsOffset(props);
            int excWord=exceptions.charAt(excOffset++);
            int excOffset0=excOffset;

            // Hardcode the case closure of i and its relatives and ignore the
            // data file data for these characters.
            // The Turkic dotless i and dotted I with their case mapping conditions
            // and case folding option make the related characters behave specially.
            // This code matches their closure behavior to their case folding behavior.
            if ((excWord&EXC_CONDITIONAL_FOLD) != 0) {
                // These characters have Turkic case foldings. Hardcode their closure.
                if (c == 0x49) {
                    // Regular i and I are in one equivalence class.
                    set.add(0x69);
                    return;
                } else if (c == 0x130) {
                    // Dotted I is in a class with <0069 0307>
                    // (for canonical equivalence with <0049 0307>).
                    set.add(iDot);
                    return;
                }
            } else if (c == 0x69) {
                set.add(0x49);
                return;
            } else if (c == 0x131) {
                // Dotless i is in a class by itself.
                return;
            }

            /* add all simple case mappings */
            for(int index=EXC_LOWER; index<=EXC_TITLE; ++index) {
                if(hasSlot(excWord, index)) {
                    excOffset=excOffset0;
                    int mapping=getSlotValue(excWord, index, excOffset);
                    set.add(mapping);
                }
            }
            if(hasSlot(excWord, EXC_DELTA)) {
                excOffset=excOffset0;
                int delta=getSlotValue(excWord, EXC_DELTA, excOffset);
                set.add((excWord&EXC_DELTA_IS_NEGATIVE)==0 ? c+delta : c-delta);
            }

            /* get the closure string pointer & length */
            int closureOffset, closureLength;
            if(hasSlot(excWord, EXC_CLOSURE)) {
                excOffset=excOffset0;
                long value=getSlotValueAndOffset(excWord, EXC_CLOSURE, excOffset);
                closureLength=(int)value&CLOSURE_MAX_LENGTH; /* higher bits are reserved */
                closureOffset=(int)(value>>32)+1; /* behind this slot, unless there are full case mappings */
            } else {
                closureLength=0;
                closureOffset=0;
            }

            /* add the full case folding */
            if(hasSlot(excWord, EXC_FULL_MAPPINGS)) {
                excOffset=excOffset0;
                long value=getSlotValueAndOffset(excWord, EXC_FULL_MAPPINGS, excOffset);
                int fullLength=(int)value;

                /* start of full case mapping strings */
                excOffset=(int)(value>>32)+1;

                fullLength&=0xffff; /* bits 16 and higher are reserved */

                /* skip the lowercase result string */
                excOffset+=fullLength&FULL_LOWER;
                fullLength>>=4;

                /* add the full case folding string */
                int length=fullLength&0xf;
                if(length!=0) {
                    set.add(exceptions.substring(excOffset, excOffset+length));
                    excOffset+=length;
                }

                /* skip the uppercase and titlecase strings */
                fullLength>>=4;
                excOffset+=fullLength&0xf;
                fullLength>>=4;
                excOffset+=fullLength;

                closureOffset=excOffset; /* behind full case mappings */
            }

            /* add each code point in the closure string */
            int limit=closureOffset+closureLength;
            for(int index=closureOffset; index<limit; index+=UTF16.getCharCount(c)) {
                int mapping=exceptions.codePointAt(index);
                set.add(mapping);
            }
        }
    }

    /**
     * Add the simple case closure mapping,
     * except if there is not actually an scf relationship between the two characters.
     * TODO: Unicode should probably add the corresponding scf mappings.
     * See https://crbug.com/v8/13377 and Unicode-internal PAG issue #23.
     * If & when those scf mappings are added, we should be able to remove all of these exceptions.
     */
    private static void addOneSimpleCaseClosure(int c, int t, UnicodeSet set) {
        switch (c) {
        case 0x0390:
            if (t == 0x1FD3) { return; }
            break;
        case 0x03B0:
            if (t == 0x1FE3) { return; }
            break;
        case 0x1FD3:
            if (t == 0x0390) { return; }
            break;
        case 0x1FE3:
            if (t == 0x03B0) { return; }
            break;
        case 0xFB05:
            if (t == 0xFB06) { return; }
            break;
        case 0xFB06:
            if (t == 0xFB05) { return; }
            break;
        default:
            break;
        }
        set.add(t);
    }

    public final void addSimpleCaseClosure(int c, UnicodeSet set) {
        int props=trie.get(c);
        if(!propsHasException(props)) {
            if(getTypeFromProps(props)!=NONE) {
                /* add the one simple case mapping, no matter what type it is */
                int delta=getDelta(props);
                if(delta!=0) {
                    set.add(c+delta);
                }
            }
        } else {
            // c has exceptions. Add the mappings relevant for scf=Simple_Case_Folding.
            int excOffset=getExceptionsOffset(props);
            int excWord=exceptions.charAt(excOffset++);
            int excOffset0=excOffset;

            // Hardcode the case closure of i and its relatives and ignore the
            // data file data for these characters, like in ucase_addCaseClosure().
            if ((excWord&EXC_CONDITIONAL_FOLD) != 0) {
                // These characters have Turkic case foldings. Hardcode their closure.
                if (c == 0x49) {
                    // Regular i and I are in one equivalence class.
                    set.add(0x69);
                    return;
                } else if (c == 0x130) {
                    // For scf=Simple_Case_Folding, dotted I is in a class by itself.
                    return;
                }
            } else if (c == 0x69) {
                set.add(0x49);
                return;
            } else if (c == 0x131) {
                // Dotless i is in a class by itself.
                return;
            }

            // Add all simple case mappings.
            for(int index=EXC_LOWER; index<=EXC_TITLE; ++index) {
                if(hasSlot(excWord, index)) {
                    excOffset=excOffset0;
                    int mapping=getSlotValue(excWord, index, excOffset);
                    addOneSimpleCaseClosure(c, mapping, set);
                }
            }
            if(hasSlot(excWord, EXC_DELTA)) {
                excOffset=excOffset0;
                int delta=getSlotValue(excWord, EXC_DELTA, excOffset);
                int mapping = (excWord&EXC_DELTA_IS_NEGATIVE)==0 ? c+delta : c-delta;
                addOneSimpleCaseClosure(c, mapping, set);
            }

            /* get the closure string pointer & length */
            int closureOffset, closureLength;
            if(hasSlot(excWord, EXC_CLOSURE)) {
                excOffset=excOffset0;
                long value=getSlotValueAndOffset(excWord, EXC_CLOSURE, excOffset);
                closureLength=(int)value&CLOSURE_MAX_LENGTH; /* higher bits are reserved */
                closureOffset=(int)(value>>32)+1; /* behind this slot, unless there are full case mappings */
            } else {
                closureLength=0;
                closureOffset=0;
            }

            // Skip the full case mappings.
            if(closureLength > 0 && hasSlot(excWord, EXC_FULL_MAPPINGS)) {
                excOffset=excOffset0;
                long value=getSlotValueAndOffset(excWord, EXC_FULL_MAPPINGS, excOffset);
                int fullLength=(int)value;

                /* start of full case mapping strings */
                excOffset=(int)(value>>32)+1;

                fullLength&=0xffff; /* bits 16 and higher are reserved */

                // Skip all 4 full case mappings.
                excOffset+=fullLength&FULL_LOWER;
                fullLength>>=4;
                excOffset+=fullLength&0xf;
                fullLength>>=4;
                excOffset+=fullLength&0xf;
                fullLength>>=4;
                excOffset+=fullLength;

                closureOffset=excOffset; /* behind full case mappings */
            }

            // Add each code point in the closure string whose scf maps back to c.
            int limit=closureOffset+closureLength;
            for(int index=closureOffset; index<limit; index+=UTF16.getCharCount(c)) {
                int mapping=exceptions.codePointAt(index);
                addOneSimpleCaseClosure(c, mapping, set);
            }
        }
    }

    /*
     * compare s, which has a length, with t=unfold[unfoldOffset..], which has a maximum length or is NUL-terminated
     * must be s.length()>0 and max>0 and s.length()<=max
     */
    private final int strcmpMax(String s, int unfoldOffset, int max) {
        int i1, length, c1, c2;

        length=s.length();
        max-=length; /* we require length<=max, so no need to decrement max in the loop */
        i1=0;
        do {
            c1=s.charAt(i1++);
            c2=unfold[unfoldOffset++];
            if(c2==0) {
                return 1; /* reached the end of t but not of s */
            }
            c1-=c2;
            if(c1!=0) {
                return c1; /* return difference result */
            }
        } while(--length>0);
        /* ends with length==0 */

        if(max==0 || unfold[unfoldOffset]==0) {
            return 0; /* equal to length of both strings */
        } else {
            return -max; /* return length difference */
        }
    }

    /**
     * Maps the string to single code points and adds the associated case closure
     * mappings.
     * The string is mapped to code points if it is their full case folding string.
     * In other words, this performs a reverse full case folding and then
     * adds the case closure items of the resulting code points.
     * If the string is found and its closure applied, then
     * the string itself is added as well as part of its code points' closure.
     *
     * @return true if the string was found
     */
    public final boolean addStringCaseClosure(String s, UnicodeSet set) {
        int i, length, start, limit, result, unfoldOffset, unfoldRows, unfoldRowWidth, unfoldStringWidth;

        if(unfold==null || s==null) {
            return false; /* no reverse case folding data, or no string */
        }
        length=s.length();
        if(length<=1) {
            /* the string is too short to find any match */
            /*
             * more precise would be:
             * if(!u_strHasMoreChar32Than(s, length, 1))
             * but this does not make much practical difference because
             * a single supplementary code point would just not be found
             */
            return false;
        }

        unfoldRows=unfold[UNFOLD_ROWS];
        unfoldRowWidth=unfold[UNFOLD_ROW_WIDTH];
        unfoldStringWidth=unfold[UNFOLD_STRING_WIDTH];
        //unfoldCPWidth=unfoldRowWidth-unfoldStringWidth;

        if(length>unfoldStringWidth) {
            /* the string is too long to find any match */
            return false;
        }

        /* do a binary search for the string */
        start=0;
        limit=unfoldRows;
        while(start<limit) {
            i=(start+limit)/2;
            unfoldOffset=((i+1)*unfoldRowWidth); // +1 to skip the header values above
            result=strcmpMax(s, unfoldOffset, unfoldStringWidth);

            if(result==0) {
                /* found the string: add each code point, and its case closure */
                int c;

                for(i=unfoldStringWidth; i<unfoldRowWidth && unfold[unfoldOffset+i]!=0; i+=UTF16.getCharCount(c)) {
                    c=UTF16.charAt(unfold, unfoldOffset, unfold.length, i);
                    set.add(c);
                    addCaseClosure(c, set);
                }
                return true;
            } else if(result<0) {
                limit=i;
            } else /* result>0 */ {
                start=i+1;
            }
        }

        return false; /* string not found */
    }

    /** @return NONE, LOWER, UPPER, TITLE */
    public final int getType(int c) {
        return getTypeFromProps(trie.get(c));
    }

    /** @return like getType() but also sets IGNORABLE if c is case-ignorable */
    public final int getTypeOrIgnorable(int c) {
        return getTypeAndIgnorableFromProps(trie.get(c));
    }

    /** @return NO_DOT, SOFT_DOTTED, ABOVE, OTHER_ACCENT */
    public final int getDotType(int c) {
        int props=trie.get(c);
        if(!propsHasException(props)) {
            return props&DOT_MASK;
        } else {
            return (exceptions.charAt(getExceptionsOffset(props))>>EXC_DOT_SHIFT)&DOT_MASK;
        }
    }

    public final boolean isSoftDotted(int c) {
        return getDotType(c)==SOFT_DOTTED;
    }

    public final boolean isCaseSensitive(int c) {
        int props=trie.get(c);
        if(!propsHasException(props)) {
            return (props&SENSITIVE)!=0;
        } else {
            return (exceptions.charAt(getExceptionsOffset(props))&EXC_SENSITIVE)!=0;
        }
    }

    // string casing ------------------------------------------------------- ***

    /*
     * These internal functions form the core of string case mappings.
     * They map single code points to result code points or strings and take
     * all necessary conditions (context, locale ID, options) into account.
     *
     * They do not iterate over the source or write to the destination
     * so that the same functions are useful for non-standard string storage,
     * such as in a Replaceable (for Transliterator) or UTF-8/32 strings etc.
     * For the same reason, the "surrounding text" context is passed in as a
     * ContextIterator which does not make any assumptions about
     * the underlying storage.
     *
     * This section contains helper functions that check for conditions
     * in the input text surrounding the current code point
     * according to SpecialCasing.txt.
     *
     * Each helper function gets the index
     * - after the current code point if it looks at following text
     * - before the current code point if it looks at preceding text
     *
     * Unicode 3.2 UAX 21 "Case Mappings" defines the conditions as follows:
     *
     * Final_Sigma
     *   C is preceded by a sequence consisting of
     *     a cased letter and a case-ignorable sequence,
     *   and C is not followed by a sequence consisting of
     *     an ignorable sequence and then a cased letter.
     *
     * More_Above
     *   C is followed by one or more characters of combining class 230 (ABOVE)
     *   in the combining character sequence.
     *
     * After_Soft_Dotted
     *   The last preceding character with combining class of zero before C
     *   was Soft_Dotted,
     *   and there is no intervening combining character class 230 (ABOVE).
     *
     * Before_Dot
     *   C is followed by combining dot above (U+0307).
     *   Any sequence of characters with a combining class that is neither 0 nor 230
     *   may intervene between the current character and the combining dot above.
     *
     * The erratum from 2002-10-31 adds the condition
     *
     * After_I
     *   The last preceding base character was an uppercase I, and there is no
     *   intervening combining character class 230 (ABOVE).
     *
     *   (See Jitterbug 2344 and the comments on After_I below.)
     *
     * Helper definitions in Unicode 3.2 UAX 21:
     *
     * D1. A character C is defined to be cased
     *     if it meets any of the following criteria:
     *
     *   - The general category of C is Titlecase Letter (Lt)
     *   - In [CoreProps], C has one of the properties Uppercase, or Lowercase
     *   - Given D = NFD(C), then it is not the case that:
     *     D = UCD_lower(D) = UCD_upper(D) = UCD_title(D)
     *     (This third criterion does not add any characters to the list
     *      for Unicode 3.2. Ignored.)
     *
     * D2. A character C is defined to be case-ignorable
     *     if it meets either of the following criteria:
     *
     *   - The general category of C is
     *     Nonspacing Mark (Mn), or Enclosing Mark (Me), or Format Control (Cf), or
     *     Letter Modifier (Lm), or Symbol Modifier (Sk)
     *   - C is one of the following characters
     *     U+0027 APOSTROPHE
     *     U+00AD SOFT HYPHEN (SHY)
     *     U+2019 RIGHT SINGLE QUOTATION MARK
     *            (the preferred character for apostrophe)
     *
     * D3. A case-ignorable sequence is a sequence of
     *     zero or more case-ignorable characters.
     */

    /**
     * Iterator for string case mappings, which need to look at the
     * context (surrounding text) of a given character for conditional mappings.
     *
     * The iterator only needs to go backward or forward away from the
     * character in question. It does not use any indexes on this interface.
     * It does not support random access or an arbitrary change of
     * iteration direction.
     *
     * The code point being case-mapped itself is never returned by
     * this iterator.
     */
    public interface ContextIterator {
        /**
         * Reset the iterator for forward or backward iteration.
         * @param dir >0: Begin iterating forward from the first code point
         * after the one that is being case-mapped.
         *            <0: Begin iterating backward from the first code point
         * before the one that is being case-mapped.
         */
        public void reset(int dir);
        /**
         * Iterate and return the next code point, moving in the direction
         * determined by the reset() call.
         * @return Next code point, or <0 when the iteration is done.
         */
        public int next();
    }

    /**
     * Fast case mapping data for ASCII/Latin.
     * Linear arrays of delta bytes: 0=no mapping; EXC=exception.
     * Deltas must not cross the ASCII boundary, or else they cannot be easily used
     * in simple UTF-8 code.
     */
    static final class LatinCase {
        /** Case mapping/folding data for code points up to U+017F. */
        static final char LIMIT = 0x180;
        /** U+017F case-folds and uppercases crossing the ASCII boundary. */
        static final char LONG_S = 0x17f;
        /** Exception: Complex mapping, or too-large delta. */
        static final byte EXC = -0x80;

        /** Deltas for lowercasing for most locales, and default case folding. */
        static final byte[] TO_LOWER_NORMAL = {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,

            0, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
            32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,

            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, EXC, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,

            32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
            32, 32, 32, 32, 32, 32, 32, 0, 32, 32, 32, 32, 32, 32, 32, EXC,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,

            1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0,
            1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0,
            1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0,
            EXC, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 1, 0, 1,

            0, 1, 0, 1, 0, 1, 0, 1, 0, EXC, 1, 0, 1, 0, 1, 0,
            1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0,
            1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0,
            1, 0, 1, 0, 1, 0, 1, 0, -121, 1, 0, 1, 0, 1, 0, EXC
        };

        /** Deltas for lowercasing for tr/az/lt, and Turkic case folding. */
        static final byte[] TO_LOWER_TR_LT = {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,

            0, 32, 32, 32, 32, 32, 32, 32, 32, EXC, EXC, 32, 32, 32, 32, 32,
            32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,

            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, EXC, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,

            32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, EXC, EXC, 32, 32,
            32, 32, 32, 32, 32, 32, 32, 0, 32, 32, 32, 32, 32, 32, 32, EXC,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,

            1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0,
            1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0,
            1, 0, 1, 0, 1, 0, 1, 0, EXC, 0, 1, 0, 1, 0, EXC, 0,
            EXC, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 1, 0, 1,

            0, 1, 0, 1, 0, 1, 0, 1, 0, EXC, 1, 0, 1, 0, 1, 0,
            1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0,
            1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0,
            1, 0, 1, 0, 1, 0, 1, 0, -121, 1, 0, 1, 0, 1, 0, EXC
        };

        /** Deltas for uppercasing for most locales. */
        static final byte[] TO_UPPER_NORMAL = {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,

            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, -32, -32, -32, -32, -32, -32, -32, -32, -32, -32, -32, -32, -32, -32, -32,
            -32, -32, -32, -32, -32, -32, -32, -32, -32, -32, -32, 0, 0, 0, 0, 0,

            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, EXC, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,

            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, EXC,
            -32, -32, -32, -32, -32, -32, -32, -32, -32, -32, -32, -32, -32, -32, -32, -32,
            -32, -32, -32, -32, -32, -32, -32, 0, -32, -32, -32, -32, -32, -32, -32, 121,

            0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1,
            0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1,
            0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1,
            0, EXC, 0, -1, 0, -1, 0, -1, 0, 0, -1, 0, -1, 0, -1, 0,

            -1, 0, -1, 0, -1, 0, -1, 0, -1, EXC, 0, -1, 0, -1, 0, -1,
            0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1,
            0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1,
            0, -1, 0, -1, 0, -1, 0, -1, 0, 0, -1, 0, -1, 0, -1, EXC
        };

        /** Deltas for uppercasing for tr/az. */
        static final byte[] TO_UPPER_TR = {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,

            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, -32, -32, -32, -32, -32, -32, -32, -32, EXC, -32, -32, -32, -32, -32, -32,
            -32, -32, -32, -32, -32, -32, -32, -32, -32, -32, -32, 0, 0, 0, 0, 0,

            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, EXC, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,

            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, EXC,
            -32, -32, -32, -32, -32, -32, -32, -32, -32, -32, -32, -32, -32, -32, -32, -32,
            -32, -32, -32, -32, -32, -32, -32, 0, -32, -32, -32, -32, -32, -32, -32, 121,

            0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1,
            0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1,
            0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1,
            0, EXC, 0, -1, 0, -1, 0, -1, 0, 0, -1, 0, -1, 0, -1, 0,

            -1, 0, -1, 0, -1, 0, -1, 0, -1, EXC, 0, -1, 0, -1, 0, -1,
            0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1,
            0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1,
            0, -1, 0, -1, 0, -1, 0, -1, 0, 0, -1, 0, -1, 0, -1, EXC
        };
    }

    /**
     * For string case mappings, a single character (a code point) is mapped
     * either to itself (in which case in-place mapping functions do nothing),
     * or to another single code point, or to a string.
     * Aside from the string contents, these are indicated with a single int
     * value as follows:
     *
     * Mapping to self: Negative values (~self instead of -self to support U+0000)
     *
     * Mapping to another code point: Positive values >MAX_STRING_LENGTH
     *
     * Mapping to a string: The string length (0..MAX_STRING_LENGTH) is
     * returned. Note that the string result may indeed have zero length.
     */
    public static final int MAX_STRING_LENGTH=0x1f;

    //ivate static final int LOC_UNKNOWN=0;
    public static final int LOC_ROOT=1;
    static final int LOC_TURKISH=2;
    static final int LOC_LITHUANIAN=3;
    static final int LOC_GREEK=4;
    public static final int LOC_DUTCH=5;
    static final int LOC_ARMENIAN=6;

    public static final int getCaseLocale(Locale locale) {
        return getCaseLocale(locale.getLanguage());
    }
    public static final int getCaseLocale(ULocale locale) {
        return getCaseLocale(locale.getLanguage());
    }
    /** Accepts both 2- and 3-letter language subtags. */
    private static final int getCaseLocale(String language) {
        // Check the subtag length to reduce the number of comparisons
        // for locales without special behavior.
        // Fastpath for English "en" which is often used for default (=root locale) case mappings,
        // and for Chinese "zh": Very common but no special case mapping behavior.
        if(language.length()==2) {
            if(language.equals("en") || language.charAt(0)>'t') {
                return LOC_ROOT;
            } else if(language.equals("tr") || language.equals("az")) {
                return LOC_TURKISH;
            } else if(language.equals("el")) {
                return LOC_GREEK;
            } else if(language.equals("lt")) {
                return LOC_LITHUANIAN;
            } else if(language.equals("nl")) {
                return LOC_DUTCH;
            } else if(language.equals("hy")) {
                return LOC_ARMENIAN;
            }
        } else if(language.length()==3) {
            if(language.equals("tur") || language.equals("aze")) {
                return LOC_TURKISH;
            } else if(language.equals("ell")) {
                return LOC_GREEK;
            } else if(language.equals("lit")) {
                return LOC_LITHUANIAN;
            } else if(language.equals("nld")) {
                return LOC_DUTCH;
            } else if(language.equals("hye")) {  // *not* hyw
                return LOC_ARMENIAN;
            }
        }
        return LOC_ROOT;
    }

    /* Is followed by {case-ignorable}* cased  ? (dir determines looking forward/backward) */
    private final boolean isFollowedByCasedLetter(ContextIterator iter, int dir) {
        int c;

        if(iter==null) {
            return false;
        }

        for(iter.reset(dir); (c=iter.next())>=0;) {
            int type=getTypeOrIgnorable(c);
            if((type&4)!=0) {
                /* case-ignorable, continue with the loop */
            } else if(type!=NONE) {
                return true; /* followed by cased letter */
            } else {
                return false; /* uncased and not case-ignorable */
            }
        }

        return false; /* not followed by cased letter */
    }

    /* Is preceded by Soft_Dotted character with no intervening cc=230 ? */
    private final boolean isPrecededBySoftDotted(ContextIterator iter) {
        int c;
        int dotType;

        if(iter==null) {
            return false;
        }

        for(iter.reset(-1); (c=iter.next())>=0;) {
            dotType=getDotType(c);
            if(dotType==SOFT_DOTTED) {
                return true; /* preceded by TYPE_i */
            } else if(dotType!=OTHER_ACCENT) {
                return false; /* preceded by different base character (not TYPE_i), or intervening cc==230 */
            }
        }

        return false; /* not preceded by TYPE_i */
    }

    /*
     * See Jitterbug 2344:
     * The condition After_I for Turkic-lowercasing of U+0307 combining dot above
     * is checked in ICU 2.0, 2.1, 2.6 but was not in 2.2 & 2.4 because
     * we made those releases compatible with Unicode 3.2 which had not fixed
     * a related bug in SpecialCasing.txt.
     *
     * From the Jitterbug 2344 text:
     * ... this bug is listed as a Unicode erratum
     * from 2002-10-31 at http://www.unicode.org/uni2errata/UnicodeErrata.html
     * <quote>
     * There are two errors in SpecialCasing.txt.
     * 1. Missing semicolons on two lines. ... [irrelevant for ICU]
     * 2. An incorrect context definition. Correct as follows:
     * < 0307; ; 0307; 0307; tr After_Soft_Dotted; # COMBINING DOT ABOVE
     * < 0307; ; 0307; 0307; az After_Soft_Dotted; # COMBINING DOT ABOVE
     * ---
     * > 0307; ; 0307; 0307; tr After_I; # COMBINING DOT ABOVE
     * > 0307; ; 0307; 0307; az After_I; # COMBINING DOT ABOVE
     * where the context After_I is defined as:
     * The last preceding base character was an uppercase I, and there is no
     * intervening combining character class 230 (ABOVE).
     * </quote>
     *
     * Note that SpecialCasing.txt even in Unicode 3.2 described the condition as:
     *
     * # When lowercasing, remove dot_above in the sequence I + dot_above, which will turn into i.
     * # This matches the behavior of the canonically equivalent I-dot_above
     *
     * See also the description in this place in older versions of uchar.c (revision 1.100).
     *
     * Markus W. Scherer 2003-feb-15
     */

    /* Is preceded by base character 'I' with no intervening cc=230 ? */
    private final boolean isPrecededBy_I(ContextIterator iter) {
        int c;
        int dotType;

        if(iter==null) {
            return false;
        }

        for(iter.reset(-1); (c=iter.next())>=0;) {
            if(c==0x49) {
                return true; /* preceded by I */
            }
            dotType=getDotType(c);
            if(dotType!=OTHER_ACCENT) {
                return false; /* preceded by different base character (not I), or intervening cc==230 */
            }
        }

        return false; /* not preceded by I */
    }

    /* Is followed by one or more cc==230 ? */
    private final boolean isFollowedByMoreAbove(ContextIterator iter) {
        int c;
        int dotType;

        if(iter==null) {
            return false;
        }

        for(iter.reset(1); (c=iter.next())>=0;) {
            dotType=getDotType(c);
            if(dotType==ABOVE) {
                return true; /* at least one cc==230 following */
            } else if(dotType!=OTHER_ACCENT) {
                return false; /* next base character, no more cc==230 following */
            }
        }

        return false; /* no more cc==230 following */
    }

    /* Is followed by a dot above (without cc==230 in between) ? */
    private final boolean isFollowedByDotAbove(ContextIterator iter) {
        int c;
        int dotType;

        if(iter==null) {
            return false;
        }

        for(iter.reset(1); (c=iter.next())>=0; ) {
            if(c==0x307) {
                return true;
            }
            dotType=getDotType(c);
            if(dotType!=OTHER_ACCENT) {
                return false; /* next base character or cc==230 in between */
            }
        }

        return false; /* no dot above following */
    }

    private static final String
        iDot=       "i\u0307",
        jDot=       "j\u0307",
        iOgonekDot= "\u012f\u0307",
        iDotGrave=  "i\u0307\u0300",
        iDotAcute=  "i\u0307\u0301",
        iDotTilde=  "i\u0307\u0303";

    /**
     * Get the full lowercase mapping for c.
     *
     * @param c Character to be mapped.
     * @param iter Character iterator, used for context-sensitive mappings.
     *             See ContextIterator for details.
     *             If iter==null then a context-independent result is returned.
     * @param out If the mapping result is a string, then it is appended to out.
     * @param caseLocale Case locale value from ucase_getCaseLocale().
     * @return Output code point or string length, see MAX_STRING_LENGTH.
     *
     * @see ContextIterator
     * @see #MAX_STRING_LENGTH
     * @internal
     */
    public final int toFullLower(int c, ContextIterator iter, Appendable out, int caseLocale) {
        int result, props;

        result=c;
        props=trie.get(c);
        if(!propsHasException(props)) {
            if(isUpperOrTitleFromProps(props)) {
                result=c+getDelta(props);
            }
        } else {
            int excOffset=getExceptionsOffset(props), excOffset2;
            int excWord=exceptions.charAt(excOffset++);
            int full;

            excOffset2=excOffset;

            if((excWord&EXC_CONDITIONAL_SPECIAL)!=0) {
                /* use hardcoded conditions and mappings */
                /*
                 * Test for conditional mappings first
                 *   (otherwise the unconditional default mappings are always taken),
                 * then test for characters that have unconditional mappings in SpecialCasing.txt,
                 * then get the UnicodeData.txt mappings.
                 */
                if( caseLocale==LOC_LITHUANIAN &&
                        /* base characters, find accents above */
                        (((c==0x49 || c==0x4a || c==0x12e) &&
                            isFollowedByMoreAbove(iter)) ||
                        /* precomposed with accent above, no need to find one */
                        (c==0xcc || c==0xcd || c==0x128))
                ) {
                    /*
                        # Lithuanian

                        # Lithuanian retains the dot in a lowercase i when followed by accents.

                        # Introduce an explicit dot above when lowercasing capital I's and J's
                        # whenever there are more accents above.
                        # (of the accents used in Lithuanian: grave, acute, tilde above, and ogonek)

                        0049; 0069 0307; 0049; 0049; lt More_Above; # LATIN CAPITAL LETTER I
                        004A; 006A 0307; 004A; 004A; lt More_Above; # LATIN CAPITAL LETTER J
                        012E; 012F 0307; 012E; 012E; lt More_Above; # LATIN CAPITAL LETTER I WITH OGONEK
                        00CC; 0069 0307 0300; 00CC; 00CC; lt; # LATIN CAPITAL LETTER I WITH GRAVE
                        00CD; 0069 0307 0301; 00CD; 00CD; lt; # LATIN CAPITAL LETTER I WITH ACUTE
                        0128; 0069 0307 0303; 0128; 0128; lt; # LATIN CAPITAL LETTER I WITH TILDE
                     */
                    try {
                        switch(c) {
                        case 0x49:  /* LATIN CAPITAL LETTER I */
                            out.append(iDot);
                            return 2;
                        case 0x4a:  /* LATIN CAPITAL LETTER J */
                            out.append(jDot);
                            return 2;
                        case 0x12e: /* LATIN CAPITAL LETTER I WITH OGONEK */
                            out.append(iOgonekDot);
                            return 2;
                        case 0xcc:  /* LATIN CAPITAL LETTER I WITH GRAVE */
                            out.append(iDotGrave);
                            return 3;
                        case 0xcd:  /* LATIN CAPITAL LETTER I WITH ACUTE */
                            out.append(iDotAcute);
                            return 3;
                        case 0x128: /* LATIN CAPITAL LETTER I WITH TILDE */
                            out.append(iDotTilde);
                            return 3;
                        default:
                            return 0; /* will not occur */
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                /* # Turkish and Azeri */
                } else if(caseLocale==LOC_TURKISH && c==0x130) {
                    /*
                        # I and i-dotless; I-dot and i are case pairs in Turkish and Azeri
                        # The following rules handle those cases.

                        0130; 0069; 0130; 0130; tr # LATIN CAPITAL LETTER I WITH DOT ABOVE
                        0130; 0069; 0130; 0130; az # LATIN CAPITAL LETTER I WITH DOT ABOVE
                     */
                    return 0x69;
                } else if(caseLocale==LOC_TURKISH && c==0x307 && isPrecededBy_I(iter)) {
                    /*
                        # When lowercasing, remove dot_above in the sequence I + dot_above, which will turn into i.
                        # This matches the behavior of the canonically equivalent I-dot_above

                        0307; ; 0307; 0307; tr After_I; # COMBINING DOT ABOVE
                        0307; ; 0307; 0307; az After_I; # COMBINING DOT ABOVE
                     */
                    return 0; /* remove the dot (continue without output) */
                } else if(caseLocale==LOC_TURKISH && c==0x49 && !isFollowedByDotAbove(iter)) {
                    /*
                        # When lowercasing, unless an I is before a dot_above, it turns into a dotless i.

                        0049; 0131; 0049; 0049; tr Not_Before_Dot; # LATIN CAPITAL LETTER I
                        0049; 0131; 0049; 0049; az Not_Before_Dot; # LATIN CAPITAL LETTER I
                     */
                    return 0x131;
                } else if(c==0x130) {
                    /*
                        # Preserve canonical equivalence for I with dot. Turkic is handled below.

                        0130; 0069 0307; 0130; 0130; # LATIN CAPITAL LETTER I WITH DOT ABOVE
                     */
                    try {
                        out.append(iDot);
                        return 2;
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                } else if(  c==0x3a3 &&
                            !isFollowedByCasedLetter(iter, 1) &&
                            isFollowedByCasedLetter(iter, -1) /* -1=preceded */
                ) {
                    /* greek capital sigma maps depending on surrounding cased letters (see SpecialCasing.txt) */
                    /*
                        # Special case for final form of sigma

                        03A3; 03C2; 03A3; 03A3; Final_Sigma; # GREEK CAPITAL LETTER SIGMA
                     */
                    return 0x3c2; /* greek small final sigma */
                } else {
                    /* no known conditional special case mapping, use a normal mapping */
                }
            } else if(hasSlot(excWord, EXC_FULL_MAPPINGS)) {
                long value=getSlotValueAndOffset(excWord, EXC_FULL_MAPPINGS, excOffset);
                full=(int)value&FULL_LOWER;
                if(full!=0) {
                    /* start of full case mapping strings */
                    excOffset=(int)(value>>32)+1;

                    try {
                        // append the lowercase mapping
                        out.append(exceptions, excOffset, excOffset+full);

                        /* return the string length */
                        return full;
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }

            if(hasSlot(excWord, EXC_DELTA) && isUpperOrTitleFromProps(props)) {
                int delta=getSlotValue(excWord, EXC_DELTA, excOffset2);
                return (excWord&EXC_DELTA_IS_NEGATIVE)==0 ? c+delta : c-delta;
            }
            if(hasSlot(excWord, EXC_LOWER)) {
                result=getSlotValue(excWord, EXC_LOWER, excOffset2);
            }
        }

        return (result==c) ? ~result : result;
    }

    /* internal */
    private final int toUpperOrTitle(int c, ContextIterator iter,
                                     Appendable out,
                                     int loc,
                                     boolean upperNotTitle) {
        int result;
        int props;

        result=c;
        props=trie.get(c);
        if(!propsHasException(props)) {
            if(getTypeFromProps(props)==LOWER) {
                result=c+getDelta(props);
            }
        } else {
            int excOffset=getExceptionsOffset(props), excOffset2;
            int excWord=exceptions.charAt(excOffset++);
            int full, index;

            excOffset2=excOffset;

            if((excWord&EXC_CONDITIONAL_SPECIAL)!=0) {
                /* use hardcoded conditions and mappings */
                if(loc==LOC_TURKISH && c==0x69) {
                    /*
                        # Turkish and Azeri

                        # I and i-dotless; I-dot and i are case pairs in Turkish and Azeri
                        # The following rules handle those cases.

                        # When uppercasing, i turns into a dotted capital I

                        0069; 0069; 0130; 0130; tr; # LATIN SMALL LETTER I
                        0069; 0069; 0130; 0130; az; # LATIN SMALL LETTER I
                    */
                    return 0x130;
                } else if(loc==LOC_LITHUANIAN && c==0x307 && isPrecededBySoftDotted(iter)) {
                    /*
                        # Lithuanian

                        # Lithuanian retains the dot in a lowercase i when followed by accents.

                        # Remove DOT ABOVE after "i" with upper or titlecase

                        0307; 0307; ; ; lt After_Soft_Dotted; # COMBINING DOT ABOVE
                     */
                    return 0; /* remove the dot (continue without output) */
                } else if(c==0x0587) {
                    // See ICU-13416:
                    // \u0587 ligature ech-yiwn
                    // uppercases to \u0535\u0552=ech+yiwn by default and in Western Armenian,
                    // but to \u0535\u054e=ech+vew in Eastern Armenian.
                    try {
                        if(loc==LOC_ARMENIAN) {
                            out.append(upperNotTitle ? "\u0535\u054e" : "\u0535\u057e");
                        } else {
                            out.append(upperNotTitle ? "\u0535\u0552" : "\u0535\u0582");
                        }
                        return 2;
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                } else {
                    /* no known conditional special case mapping, use a normal mapping */
                }
            } else if(hasSlot(excWord, EXC_FULL_MAPPINGS)) {
                long value=getSlotValueAndOffset(excWord, EXC_FULL_MAPPINGS, excOffset);
                full=(int)value&0xffff;

                /* start of full case mapping strings */
                excOffset=(int)(value>>32)+1;

                /* skip the lowercase and case-folding result strings */
                excOffset+=full&FULL_LOWER;
                full>>=4;
                excOffset+=full&0xf;
                full>>=4;

                if(upperNotTitle) {
                    full&=0xf;
                } else {
                    /* skip the uppercase result string */
                    excOffset+=full&0xf;
                    full=(full>>4)&0xf;
                }

                if(full!=0) {
                    try {
                        // append the result string
                        out.append(exceptions, excOffset, excOffset+full);

                        /* return the string length */
                        return full;
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }

            if(hasSlot(excWord, EXC_DELTA) && getTypeFromProps(props)==LOWER) {
                int delta=getSlotValue(excWord, EXC_DELTA, excOffset2);
                return (excWord&EXC_DELTA_IS_NEGATIVE)==0 ? c+delta : c-delta;
            }
            if(!upperNotTitle && hasSlot(excWord, EXC_TITLE)) {
                index=EXC_TITLE;
            } else if(hasSlot(excWord, EXC_UPPER)) {
                /* here, titlecase is same as uppercase */
                index=EXC_UPPER;
            } else {
                return ~c;
            }
            result=getSlotValue(excWord, index, excOffset2);
        }

        return (result==c) ? ~result : result;
    }

    public final int toFullUpper(int c, ContextIterator iter,
                                 Appendable out,
                                 int caseLocale) {
        return toUpperOrTitle(c, iter, out, caseLocale, true);
    }

    public final int toFullTitle(int c, ContextIterator iter,
                                 Appendable out,
                                 int caseLocale) {
        return toUpperOrTitle(c, iter, out, caseLocale, false);
    }

    /* case folding ------------------------------------------------------------- */

    /*
     * Case folding is similar to lowercasing.
     * The result may be a simple mapping, i.e., a single code point, or
     * a full mapping, i.e., a string.
     * If the case folding for a code point is the same as its simple (1:1) lowercase mapping,
     * then only the lowercase mapping is stored.
     *
     * Some special cases are hardcoded because their conditions cannot be
     * parsed and processed from CaseFolding.txt.
     *
     * Unicode 3.2 CaseFolding.txt specifies for its status field:

    # C: common case folding, common mappings shared by both simple and full mappings.
    # F: full case folding, mappings that cause strings to grow in length. Multiple characters are separated by spaces.
    # S: simple case folding, mappings to single characters where different from F.
    # T: special case for uppercase I and dotted uppercase I
    #    - For non-Turkic languages, this mapping is normally not used.
    #    - For Turkic languages (tr, az), this mapping can be used instead of the normal mapping for these characters.
    #
    # Usage:
    #  A. To do a simple case folding, use the mappings with status C + S.
    #  B. To do a full case folding, use the mappings with status C + F.
    #
    #    The mappings with status T can be used or omitted depending on the desired case-folding
    #    behavior. (The default option is to exclude them.)

     * Unicode 3.2 has 'T' mappings as follows:

    0049; T; 0131; # LATIN CAPITAL LETTER I
    0130; T; 0069; # LATIN CAPITAL LETTER I WITH DOT ABOVE

     * while the default mappings for these code points are:

    0049; C; 0069; # LATIN CAPITAL LETTER I
    0130; F; 0069 0307; # LATIN CAPITAL LETTER I WITH DOT ABOVE

     * U+0130 has no simple case folding (simple-case-folds to itself).
     */

    /**
     * Bit mask for getting just the options from a string compare options word
     * that are relevant for case folding (of a single string or code point).
     *
     * Currently only bit 0 for FOLD_CASE_EXCLUDE_SPECIAL_I.
     * It is conceivable that at some point we might use one more bit for using uppercase sharp s.
     * It is conceivable that at some point we might want the option to use only simple case foldings
     * when operating on strings.
     *
     * @internal
     */
    static final int FOLD_CASE_OPTIONS_MASK = 7;

    /* return the simple case folding mapping for c */
    public final int fold(int c, int options) {
        int props=trie.get(c);
        if(!propsHasException(props)) {
            if(isUpperOrTitleFromProps(props)) {
                c+=getDelta(props);
            }
        } else {
            int excOffset=getExceptionsOffset(props);
            int excWord=exceptions.charAt(excOffset++);
            int index;
            if((excWord&EXC_CONDITIONAL_FOLD)!=0) {
                /* special case folding mappings, hardcoded */
                if((options&FOLD_CASE_OPTIONS_MASK)==UCharacter.FOLD_CASE_DEFAULT) {
                    /* default mappings */
                    if(c==0x49) {
                        /* 0049; C; 0069; # LATIN CAPITAL LETTER I */
                        return 0x69;
                    } else if(c==0x130) {
                        /* no simple case folding for U+0130 */
                        return c;
                    }
                } else {
                    /* Turkic mappings */
                    if(c==0x49) {
                        /* 0049; T; 0131; # LATIN CAPITAL LETTER I */
                        return 0x131;
                    } else if(c==0x130) {
                        /* 0130; T; 0069; # LATIN CAPITAL LETTER I WITH DOT ABOVE */
                        return 0x69;
                    }
                }
            }
            if((excWord&EXC_NO_SIMPLE_CASE_FOLDING)!=0) {
                return c;
            }
            if(hasSlot(excWord, EXC_DELTA) && isUpperOrTitleFromProps(props)) {
                int delta=getSlotValue(excWord, EXC_DELTA, excOffset);
                return (excWord&EXC_DELTA_IS_NEGATIVE)==0 ? c+delta : c-delta;
            }
            if(hasSlot(excWord, EXC_FOLD)) {
                index=EXC_FOLD;
            } else if(hasSlot(excWord, EXC_LOWER)) {
                index=EXC_LOWER;
            } else {
                return c;
            }
            c=getSlotValue(excWord, index, excOffset);
        }
        return c;
    }

    /*
     * Issue for canonical caseless match (UAX #21):
     * Turkic casefolding (using "T" mappings in CaseFolding.txt) does not preserve
     * canonical equivalence, unlike default-option casefolding.
     * For example, I-grave and I + grave fold to strings that are not canonically
     * equivalent.
     * For more details, see the comment in unorm_compare() in unorm.cpp
     * and the intermediate prototype changes for Jitterbug 2021.
     * (For example, revision 1.104 of uchar.c and 1.4 of CaseFolding.txt.)
     *
     * This did not get fixed because it appears that it is not possible to fix
     * it for uppercase and lowercase characters (I-grave vs. i-grave)
     * together in a way that they still fold to common result strings.
     */

    public final int toFullFolding(int c, Appendable out, int options) {
        int result;
        int props;

        result=c;
        props=trie.get(c);
        if(!propsHasException(props)) {
            if(isUpperOrTitleFromProps(props)) {
                result=c+getDelta(props);
            }
        } else {
            int excOffset=getExceptionsOffset(props), excOffset2;
            int excWord=exceptions.charAt(excOffset++);
            int full, index;

            excOffset2=excOffset;

            if((excWord&EXC_CONDITIONAL_FOLD)!=0) {
                /* use hardcoded conditions and mappings */
                if((options&FOLD_CASE_OPTIONS_MASK)==UCharacter.FOLD_CASE_DEFAULT) {
                    /* default mappings */
                    if(c==0x49) {
                        /* 0049; C; 0069; # LATIN CAPITAL LETTER I */
                        return 0x69;
                    } else if(c==0x130) {
                        /* 0130; F; 0069 0307; # LATIN CAPITAL LETTER I WITH DOT ABOVE */
                        try {
                            out.append(iDot);
                            return 2;
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                } else {
                    /* Turkic mappings */
                    if(c==0x49) {
                        /* 0049; T; 0131; # LATIN CAPITAL LETTER I */
                        return 0x131;
                    } else if(c==0x130) {
                        /* 0130; T; 0069; # LATIN CAPITAL LETTER I WITH DOT ABOVE */
                        return 0x69;
                    }
                }
            } else if(hasSlot(excWord, EXC_FULL_MAPPINGS)) {
                long value=getSlotValueAndOffset(excWord, EXC_FULL_MAPPINGS, excOffset);
                full=(int)value&0xffff;

                /* start of full case mapping strings */
                excOffset=(int)(value>>32)+1;

                /* skip the lowercase result string */
                excOffset+=full&FULL_LOWER;
                full=(full>>4)&0xf;

                if(full!=0) {
                    try {
                        // append the result string
                        out.append(exceptions, excOffset, excOffset+full);

                        /* return the string length */
                        return full;
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }

            if((excWord&EXC_NO_SIMPLE_CASE_FOLDING)!=0) {
                return ~c;
            }
            if(hasSlot(excWord, EXC_DELTA) && isUpperOrTitleFromProps(props)) {
                int delta=getSlotValue(excWord, EXC_DELTA, excOffset2);
                return (excWord&EXC_DELTA_IS_NEGATIVE)==0 ? c+delta : c-delta;
            }
            if(hasSlot(excWord, EXC_FOLD)) {
                index=EXC_FOLD;
            } else if(hasSlot(excWord, EXC_LOWER)) {
                index=EXC_LOWER;
            } else {
                return ~c;
            }
            result=getSlotValue(excWord, index, excOffset2);
        }

        return (result==c) ? ~result : result;
    }

    /* case mapping properties API ---------------------------------------------- */

    /*
     * We need a StringBuilder for multi-code point output from the
     * full case mapping functions. However, we do not actually use that output,
     * we just check whether the input character was mapped to anything else.
     * We use a shared StringBuilder to avoid allocating a new one in each call.
     * We remove its contents each time so that it does not grow large over time.
     *
     * @internal
     */
    public static final StringBuilder dummyStringBuilder = new StringBuilder();

    public final boolean hasBinaryProperty(int c, int which) {
        switch(which) {
        case UProperty.LOWERCASE:
            return LOWER==getType(c);
        case UProperty.UPPERCASE:
            return UPPER==getType(c);
        case UProperty.SOFT_DOTTED:
            return isSoftDotted(c);
        case UProperty.CASE_SENSITIVE:
            return isCaseSensitive(c);
        case UProperty.CASED:
            return NONE!=getType(c);
        case UProperty.CASE_IGNORABLE:
            return (getTypeOrIgnorable(c)>>2)!=0;
        /*
         * Note: The following Changes_When_Xyz are defined as testing whether
         * the NFD form of the input changes when Xyz-case-mapped.
         * However, this simpler implementation of these properties,
         * ignoring NFD, passes the tests.
         * The implementation needs to be changed if the tests start failing.
         * When that happens, optimizations should be used to work with the
         * per-single-code point ucase_toFullXyz() functions unless
         * the NFD form has more than one code point,
         * and the property starts set needs to be the union of the
         * start sets for normalization and case mappings.
         */
        case UProperty.CHANGES_WHEN_LOWERCASED:
            dummyStringBuilder.setLength(0);
            return toFullLower(c, null, dummyStringBuilder, LOC_ROOT)>=0;
        case UProperty.CHANGES_WHEN_UPPERCASED:
            dummyStringBuilder.setLength(0);
            return toFullUpper(c, null, dummyStringBuilder, LOC_ROOT)>=0;
        case UProperty.CHANGES_WHEN_TITLECASED:
            dummyStringBuilder.setLength(0);
            return toFullTitle(c, null, dummyStringBuilder, LOC_ROOT)>=0;
        /* case UProperty.CHANGES_WHEN_CASEFOLDED: -- in UCharacterProperty.java */
        case UProperty.CHANGES_WHEN_CASEMAPPED:
            dummyStringBuilder.setLength(0);
            return
                toFullLower(c, null, dummyStringBuilder, LOC_ROOT)>=0 ||
                toFullUpper(c, null, dummyStringBuilder, LOC_ROOT)>=0 ||
                toFullTitle(c, null, dummyStringBuilder, LOC_ROOT)>=0;
        default:
            return false;
        }
    }

    // data members -------------------------------------------------------- ***
    private int indexes[];
    private String exceptions;
    private char unfold[];

    private Trie2_16 trie;

    // data format constants ----------------------------------------------- ***
    private static final String DATA_NAME="ucase";
    private static final String DATA_TYPE="icu";
    private static final String DATA_FILE_NAME=DATA_NAME+"."+DATA_TYPE;

    /* format "cAsE" */
    private static final int FMT=0x63415345;

    /* indexes into indexes[] */
    //private static final int IX_INDEX_TOP=0;
    //private static final int IX_LENGTH=1;
    private static final int IX_TRIE_SIZE=2;
    private static final int IX_EXC_LENGTH=3;
    private static final int IX_UNFOLD_LENGTH=4;

    //private static final int IX_MAX_FULL_LENGTH=15;
    private static final int IX_TOP=16;

    // definitions for 16-bit case properties word ------------------------- ***

    static Trie2_16 getTrie() {
        return INSTANCE.trie;
    }

    /* 2-bit constants for types of cased characters */
    public static final int TYPE_MASK=3;
    public static final int NONE=0;
    public static final int LOWER=1;
    public static final int UPPER=2;
    public static final int TITLE=3;

    /** @return NONE, LOWER, UPPER, TITLE */
    static final int getTypeFromProps(int props) {
        return props&TYPE_MASK;
    }

    /** @return like getTypeFromProps() but also sets IGNORABLE if props indicate case-ignorable */
    private static final int getTypeAndIgnorableFromProps(int props) {
        return props&7;
    }

    static final boolean isUpperOrTitleFromProps(int props) {
        return (props & 2) != 0;
    }

    static final int IGNORABLE=4;
    private static final int EXCEPTION=     8;
    private static final int SENSITIVE=     0x10;

    private static final int DOT_MASK=      0x60;
    //private static final int NO_DOT=        0;      /* normal characters with cc=0 */
    private static final int SOFT_DOTTED=   0x20;   /* soft-dotted characters with cc=0 */
    private static final int ABOVE=         0x40;   /* "above" accents with cc=230 */
    private static final int OTHER_ACCENT=  0x60;   /* other accent character (0<cc!=230) */

    /* no exception: bits 15..7 are a 9-bit signed case mapping delta */
    private static final int DELTA_SHIFT=   7;
    //private static final int DELTA_MASK=    0xff80;
    //private static final int MAX_DELTA=     0xff;
    //private static final int MIN_DELTA=     (-MAX_DELTA-1);

    static final int getDelta(int props) {
        return (short)props>>DELTA_SHIFT;
    }

    /* exception: bits 15..4 are an unsigned 12-bit index into the exceptions array */
    private static final int EXC_SHIFT=     4;
    //private static final int EXC_MASK=      0xfff0;
    //private static final int MAX_EXCEPTIONS=((EXC_MASK>>EXC_SHIFT)+1);

    /* definitions for 16-bit main exceptions word ------------------------------ */

    /* first 8 bits indicate values in optional slots */
    private static final int EXC_LOWER=0;
    private static final int EXC_FOLD=1;
    private static final int EXC_UPPER=2;
    private static final int EXC_TITLE=3;
    private static final int EXC_DELTA=4;
    //private static final int EXC_5=5;           /* reserved */
    private static final int EXC_CLOSURE=6;
    private static final int EXC_FULL_MAPPINGS=7;
    //private static final int EXC_ALL_SLOTS=8;   /* one past the last slot */

    /* each slot is 2 uint16_t instead of 1 */
    private static final int EXC_DOUBLE_SLOTS=          0x100;

    private static final int EXC_NO_SIMPLE_CASE_FOLDING=0x200;
    private static final int EXC_DELTA_IS_NEGATIVE=0x400;
    private static final int EXC_SENSITIVE=0x800;

    /* EXC_DOT_MASK=DOT_MASK<<EXC_DOT_SHIFT */
    private static final int EXC_DOT_SHIFT=7;

    /* normally stored in the main word, but pushed out for larger exception indexes */
    //private static final int EXC_DOT_MASK=              0x3000;
    //private static final int EXC_NO_DOT=                0;
    //private static final int EXC_SOFT_DOTTED=           0x1000;
    //private static final int EXC_ABOVE=                 0x2000; /* "above" accents with cc=230 */
    //private static final int EXC_OTHER_ACCENT=          0x3000; /* other character (0<cc!=230) */

    /* complex/conditional mappings */
    private static final int EXC_CONDITIONAL_SPECIAL=   0x4000;
    private static final int EXC_CONDITIONAL_FOLD=      0x8000;

    /* definitions for lengths word for full case mappings */
    private static final int FULL_LOWER=    0xf;
    //private static final int FULL_FOLDING=  0xf0;
    //private static final int FULL_UPPER=    0xf00;
    //private static final int FULL_TITLE=    0xf000;

    /* maximum lengths */
    //private static final int FULL_MAPPINGS_MAX_LENGTH=4*0xf;
    private static final int CLOSURE_MAX_LENGTH=0xf;

    /* constants for reverse case folding ("unfold") data */
    private static final int UNFOLD_ROWS=0;
    private static final int UNFOLD_ROW_WIDTH=1;
    private static final int UNFOLD_STRING_WIDTH=2;

    /*
     * public singleton instance
     */
    public static final UCaseProps INSTANCE;

    // This static initializer block must be placed after
    // other static member initialization
    static {
        try {
            INSTANCE = new UCaseProps();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
