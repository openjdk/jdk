// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 1996-2016, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */
package jdk.internal.icu.text;

/**
 * <code>UnicodeFilter</code> defines a protocol for selecting a
 * subset of the full range (U+0000 to U+FFFF) of Unicode characters.
 * Currently, filters are used in conjunction with classes like
 * {@link jdk.internal.icu.text.Transliterator}
 * to only process selected characters through a
 * transformation.
 * @stable ICU 2.0
 */
@SuppressWarnings("javadoc")    // com.imb.icu.text.Transliterator is in another project
public abstract class UnicodeFilter implements UnicodeMatcher {

    /**
     * Returns <tt>true</tt> for characters that are in the selected
     * subset.  In other words, if a character is <b>to be
     * filtered</b>, then <tt>contains()</tt> returns
     * <b><tt>false</tt></b>.
     * @stable ICU 2.0
     */
    public abstract boolean contains(int c);

    /**
     * Default implementation of UnicodeMatcher::matches() for Unicode
     * filters.  Matches a single 16-bit code unit at offset.
     * @stable ICU 2.0
     */
    @Override
    public int matches(Replaceable text,
                       int[] offset,
                       int limit,
                       boolean incremental) {
        int c;
        if (offset[0] < limit &&
            contains(c = text.char32At(offset[0]))) {
            offset[0] += UTF16.getCharCount(c);
            return U_MATCH;
        }
        if (offset[0] > limit && contains(text.char32At(offset[0]))) {
            // Backup offset by 1, unless the preceding character is a
            // surrogate pair -- then backup by 2 (keep offset pointing at
            // the lead surrogate).
            --offset[0];
            if (offset[0] >= 0) {
                offset[0] -= UTF16.getCharCount(text.char32At(offset[0])) - 1;
            }
            return U_MATCH;
        }
        if (incremental && offset[0] == limit) {
            return U_PARTIAL_MATCH;
        }
        return U_MISMATCH;
    }

    protected UnicodeFilter() {}
}
