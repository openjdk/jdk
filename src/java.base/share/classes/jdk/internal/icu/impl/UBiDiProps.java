// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 *
 *   Copyright (C) 2004-2015, International Business Machines
 *   Corporation and others.  All Rights Reserved.
 *
 *******************************************************************************
 *   file name:  UBiDiProps.java
 *   encoding:   US-ASCII
 *   tab size:   8 (not used)
 *   indentation:4
 *
 *   created on: 2005jan16
 *   created by: Markus W. Scherer
 *
 *   Low-level Unicode bidi/shaping properties access.
 *   Java port of ubidi_props.h/.c.
 */

package jdk.internal.icu.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Iterator;

import jdk.internal.icu.lang.UCharacter;
import jdk.internal.icu.lang.UProperty;
import jdk.internal.icu.text.UnicodeSet;

public final class UBiDiProps {
    // constructors etc. --------------------------------------------------- ***

    // port of ubidi_openProps()
    private UBiDiProps() throws IOException{
        ByteBuffer bytes=ICUBinary.getData(DATA_FILE_NAME);
        readData(bytes);
    }

    private void readData(ByteBuffer bytes) throws IOException {
        // read the header
        ICUBinary.readHeader(bytes, FMT, new IsAcceptable());

        // read indexes[]
        int i, count;
        count=bytes.getInt();
        if(count<IX_TOP) {
            throw new IOException("indexes[0] too small in "+DATA_FILE_NAME);
        }
        indexes=new int[count];

        indexes[0]=count;
        for(i=1; i<count; ++i) {
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

        // read mirrors[]
        count=indexes[IX_MIRROR_LENGTH];
        if(count>0) {
            mirrors=ICUBinary.getInts(bytes, count, 0);
        }

        // read jgArray[]
        count=indexes[IX_JG_LIMIT]-indexes[IX_JG_START];
        jgArray=new byte[count];
        bytes.get(jgArray);

        // read jgArray2[]
        count=indexes[IX_JG_LIMIT2]-indexes[IX_JG_START2];
        jgArray2=new byte[count];
        bytes.get(jgArray2);
    }

    // implement ICUBinary.Authenticate
    private final static class IsAcceptable implements ICUBinary.Authenticate {
        @Override
        public boolean isDataVersionAcceptable(byte version[]) {
            return version[0]==2;
        }
    }

    // set of property starts for UnicodeSet ------------------------------- ***

    public final void addPropertyStarts(UnicodeSet set) {
        int i, length;
        int c, start, limit;

        byte prev, jg;

        /* add the start code point of each same-value range of the trie */
        Iterator<Trie2.Range> trieIterator=trie.iterator();
        Trie2.Range range;
        while(trieIterator.hasNext() && !(range=trieIterator.next()).leadSurrogate) {
            set.add(range.startCodePoint);
        }

        /* add the code points from the bidi mirroring table */
        length=indexes[IX_MIRROR_LENGTH];
        for(i=0; i<length; ++i) {
            c=getMirrorCodePoint(mirrors[i]);
            set.add(c, c+1);
        }

        /* add the code points from the Joining_Group array where the value changes */
        start=indexes[IX_JG_START];
        limit=indexes[IX_JG_LIMIT];
        byte[] jga=jgArray;
        for(;;) {
            length=limit-start;
            prev=0;
            for(i=0; i<length; ++i) {
                jg=jga[i];
                if(jg!=prev) {
                    set.add(start);
                    prev=jg;
                }
                ++start;
            }
            if(prev!=0) {
                /* add the limit code point if the last value was not 0 (it is now start==limit) */
                set.add(limit);
            }
            if(limit==indexes[IX_JG_LIMIT]) {
                /* switch to the second Joining_Group range */
                start=indexes[IX_JG_START2];
                limit=indexes[IX_JG_LIMIT2];
                jga=jgArray2;
            } else {
                break;
            }
        }

        /* add code points with hardcoded properties, plus the ones following them */

        /* (none right now) */
    }

    // property access functions ------------------------------------------- ***

    public final int getMaxValue(int which) {
        int max;

        max=indexes[IX_MAX_VALUES];
        switch(which) {
        case UProperty.BIDI_CLASS:
            return (max&CLASS_MASK);
        case UProperty.JOINING_GROUP:
            return (max&MAX_JG_MASK)>>MAX_JG_SHIFT;
        case UProperty.JOINING_TYPE:
            return (max&JT_MASK)>>JT_SHIFT;
        case UProperty.BIDI_PAIRED_BRACKET_TYPE:
            return (max&BPT_MASK)>>BPT_SHIFT;
        default:
            return -1; /* undefined */
        }
    }

    public final int getClass(int c) {
        return getClassFromProps(trie.get(c));
    }

    public final boolean isMirrored(int c) {
        return getFlagFromProps(trie.get(c), IS_MIRRORED_SHIFT);
    }

    private final int getMirror(int c, int props) {
        int delta=getMirrorDeltaFromProps(props);
        if(delta!=ESC_MIRROR_DELTA) {
            return c+delta;
        } else {
            /* look for mirror code point in the mirrors[] table */
            int m;
            int i, length;
            int c2;

            length=indexes[IX_MIRROR_LENGTH];

            /* linear search */
            for(i=0; i<length; ++i) {
                m=mirrors[i];
                c2=getMirrorCodePoint(m);
                if(c==c2) {
                    /* found c, return its mirror code point using the index in m */
                    return getMirrorCodePoint(mirrors[getMirrorIndex(m)]);
                } else if(c<c2) {
                    break;
                }
            }

            /* c not found, return it itself */
            return c;
        }
    }

    public final int getMirror(int c) {
        int props=trie.get(c);
        return getMirror(c, props);
    }

    public final boolean isBidiControl(int c) {
        return getFlagFromProps(trie.get(c), BIDI_CONTROL_SHIFT);
    }

    public final boolean isJoinControl(int c) {
        return getFlagFromProps(trie.get(c), JOIN_CONTROL_SHIFT);
    }

    public final int getJoiningType(int c) {
        return (trie.get(c)&JT_MASK)>>JT_SHIFT;
    }

    public final int getJoiningGroup(int c) {
        int start, limit;

        start=indexes[IX_JG_START];
        limit=indexes[IX_JG_LIMIT];
        if(start<=c && c<limit) {
            return jgArray[c-start]&0xff;
        }
        start=indexes[IX_JG_START2];
        limit=indexes[IX_JG_LIMIT2];
        if(start<=c && c<limit) {
            return jgArray2[c-start]&0xff;
        }
        return UCharacter.JoiningGroup.NO_JOINING_GROUP;
    }

    public final int getPairedBracketType(int c) {
        return (trie.get(c)&BPT_MASK)>>BPT_SHIFT;
    }

    public final int getPairedBracket(int c) {
        int props=trie.get(c);
        if((props&BPT_MASK)==0) {
            return c;
        } else {
            return getMirror(c, props);
        }
    }

    // data members -------------------------------------------------------- ***
    private int indexes[];
    private int mirrors[];
    private byte jgArray[];
    private byte jgArray2[];

    private Trie2_16 trie;

    // data format constants ----------------------------------------------- ***
    private static final String DATA_NAME="ubidi";
    private static final String DATA_TYPE="icu";
    private static final String DATA_FILE_NAME=DATA_NAME+"."+DATA_TYPE;

    /* format "BiDi" */
    private static final int FMT=0x42694469;

    /* indexes into indexes[] */
    //private static final int IX_INDEX_TOP=0;
    //private static final int IX_LENGTH=1;
    private static final int IX_TRIE_SIZE=2;
    private static final int IX_MIRROR_LENGTH=3;

    private static final int IX_JG_START=4;
    private static final int IX_JG_LIMIT=5;
    private static final int IX_JG_START2=6;  /* new in format version 2.2, ICU 54 */
    private static final int IX_JG_LIMIT2=7;

    private static final int IX_MAX_VALUES=15;
    private static final int IX_TOP=16;

    // definitions for 16-bit bidi/shaping properties word ----------------- ***

                          /* CLASS_SHIFT=0, */     /* bidi class: 5 bits (4..0) */
    private static final int JT_SHIFT=5;           /* joining type: 3 bits (7..5) */

    private static final int BPT_SHIFT=8;          /* Bidi_Paired_Bracket_Type(bpt): 2 bits (9..8) */

    private static final int JOIN_CONTROL_SHIFT=10;
    private static final int BIDI_CONTROL_SHIFT=11;

    private static final int IS_MIRRORED_SHIFT=12;         /* 'is mirrored' */
    private static final int MIRROR_DELTA_SHIFT=13;        /* bidi mirroring delta: 3 bits (15..13) */

    private static final int MAX_JG_SHIFT=16;              /* max JG value in indexes[MAX_VALUES_INDEX] bits 23..16 */

    private static final int CLASS_MASK=    0x0000001f;
    private static final int JT_MASK=       0x000000e0;
    private static final int BPT_MASK=      0x00000300;

    private static final int MAX_JG_MASK=   0x00ff0000;

    private static final int getClassFromProps(int props) {
        return props&CLASS_MASK;
    }
    private static final boolean getFlagFromProps(int props, int shift) {
        return ((props>>shift)&1)!=0;
    }
    private static final int getMirrorDeltaFromProps(int props) {
        return (short)props>>MIRROR_DELTA_SHIFT;
    }

    private static final int ESC_MIRROR_DELTA=-4;
    //private static final int MIN_MIRROR_DELTA=-3;
    //private static final int MAX_MIRROR_DELTA=3;

    // definitions for 32-bit mirror table entry --------------------------- ***

    /* the source Unicode code point takes 21 bits (20..0) */
    private static final int MIRROR_INDEX_SHIFT=21;
    //private static final int MAX_MIRROR_INDEX=0x7ff;

    private static final int getMirrorCodePoint(int m) {
        return m&0x1fffff;
    }
    private static final int getMirrorIndex(int m) {
        return m>>>MIRROR_INDEX_SHIFT;
    }


    /*
     * public singleton instance
     */
    public static final UBiDiProps INSTANCE;

    // This static initializer block must be placed after
    // other static member initialization
    static {
        try {
            INSTANCE = new UBiDiProps();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
