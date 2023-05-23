// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 1996-2014, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */
package jdk.internal.icu.text;

import java.util.Iterator;

/**
 * UnicodeSetIterator iterates over the contents of a UnicodeSet.  It
 * iterates over either code points or code point ranges.  After all
 * code points or ranges have been returned, it returns the
 * multicharacter strings of the UnicodeSet, if any.
 *
 * <p>This class is not intended for public subclassing.
 *
 * <p>To iterate over code points and multicharacter strings,
 * use a loop like this:
 * <pre>
 * for (UnicodeSetIterator it = new UnicodeSetIterator(set); it.next();) {
 *   processString(it.getString());
 * }
 * </pre>
 *
 * <p>To iterate over code point ranges, use a loop like this:
 * <pre>
 * for (UnicodeSetIterator it = new UnicodeSetIterator(set); it.nextRange();) {
 *   if (it.codepoint != UnicodeSetIterator.IS_STRING) {
 *     processCodepointRange(it.codepoint, it.codepointEnd);
 *   } else {
 *     processString(it.getString());
 *   }
 * }
 * </pre>
 *
 * <p>To iterate over only the strings, start with <code>new UnicodeSetIterator(set).skipToStrings()</code>.
 *
 * <p><b>Warning: </b>For speed, UnicodeSet iteration does not check for concurrent modification.
 * Do not alter the UnicodeSet while iterating.
 * @author M. Davis
 * @stable ICU 2.0
 * @see UnicodeSet#ranges()
 * @see UnicodeSet#strings()
 * @see UnicodeSet#iterator()
 */
public final class UnicodeSetIterator {

    /**
     * Value of <tt>codepoint</tt> if the iterator points to a string.
     * If <tt>codepoint == IS_STRING</tt>, then examine
     * <tt>string</tt> for the current iteration result.
     * @stable ICU 2.0
     */
    public static final int IS_STRING = -1;

    /**
     * Current code point, or the special value <tt>IS_STRING</tt>, if
     * the iterator points to a string.
     * @stable ICU 2.0
     */
    public int codepoint;

    /**
     * When iterating over ranges using <tt>nextRange()</tt>,
     * <tt>codepointEnd</tt> contains the inclusive end of the
     * iteration range, if <tt>codepoint != IS_STRING</tt>.  If
     * iterating over code points using <tt>next()</tt>, or if
     * <tt>codepoint == IS_STRING</tt>, then the value of
     * <tt>codepointEnd</tt> is undefined.
     * @stable ICU 2.0
     */
    public int codepointEnd;

    /**
     * If <tt>codepoint == IS_STRING</tt>, then <tt>string</tt> points
     * to the current string.  If <tt>codepoint != IS_STRING</tt>, the
     * value of <tt>string</tt> is undefined.
     * @stable ICU 2.0
     */
    public String string;

    /**
     * Create an iterator over the given set.
     * @param set set to iterate over
     * @stable ICU 2.0
     */
    public UnicodeSetIterator(UnicodeSet set) {
        reset(set);
    }

    /**
     * Create an iterator over nothing.  <tt>next()</tt> and
     * <tt>nextRange()</tt> return false. This is a convenience
     * constructor allowing the target to be set later.
     * @stable ICU 2.0
     */
    public UnicodeSetIterator() {
        reset(new UnicodeSet());
    }

    /**
     * Skips over the remaining code points/ranges, if any.
     * A following call to next() or nextRange() will yield a string, if there is one.
     * No-op if next() would return false, or if it would yield a string anyway.
     *
     * @return this
     * @stable ICU 70
     * @see UnicodeSet#strings()
     */
    public UnicodeSetIterator skipToStrings() {
        // Finish code point/range iteration.
        range = endRange;
        endElement = -1;
        nextElement = 0;
        return this;
    }

    /**
     * Returns the next element in the set, either a single code point
     * or a string.  If there are no more elements in the set, return
     * false.  If <tt>codepoint == IS_STRING</tt>, the value is a
     * string in the <tt>string</tt> field.  Otherwise the value is a
     * single code point in the <tt>codepoint</tt> field.
     *
     * <p>The order of iteration is all code points in sorted order,
     * followed by all strings sorted order.  <tt>codepointEnd</tt> is
     * undefined after calling this method.  <tt>string</tt> is
     * undefined unless <tt>codepoint == IS_STRING</tt>.  Do not mix
     * calls to <tt>next()</tt> and <tt>nextRange()</tt> without
     * calling <tt>reset()</tt> between them.  The results of doing so
     * are undefined.
     * <p><b>Warning: </b>For speed, UnicodeSet iteration does not check for concurrent modification.
     * Do not alter the UnicodeSet while iterating.
     * @return true if there was another element in the set and this
     * object contains the element.
     * @stable ICU 2.0
     */
    public boolean next() {
        if (nextElement <= endElement) {
            codepoint = codepointEnd = nextElement++;
            return true;
        }
        if (range < endRange) {
            loadRange(++range);
            codepoint = codepointEnd = nextElement++;
            return true;
        }

        // stringIterator == null iff there are no string elements remaining

        if (stringIterator == null) {
            return false;
        }
        codepoint = IS_STRING; // signal that value is actually a string
        string = stringIterator.next();
        if (!stringIterator.hasNext()) {
            stringIterator = null;
        }
        return true;
    }

    /**
     * Returns the next element in the set, either a code point range
     * or a string.  If there are no more elements in the set, return
     * false.  If <tt>codepoint == IS_STRING</tt>, the value is a
     * string in the <tt>string</tt> field.  Otherwise the value is a
     * range of one or more code points from <tt>codepoint</tt> to
     * <tt>codepointeEnd</tt> inclusive.
     *
     * <p>The order of iteration is all code points ranges in sorted
     * order, followed by all strings sorted order.  Ranges are
     * disjoint and non-contiguous.  <tt>string</tt> is undefined
     * unless <tt>codepoint == IS_STRING</tt>.  Do not mix calls to
     * <tt>next()</tt> and <tt>nextRange()</tt> without calling
     * <tt>reset()</tt> between them.  The results of doing so are
     * undefined.
     *
     * @return true if there was another element in the set and this
     * object contains the element.
     * @stable ICU 2.0
     */
    public boolean nextRange() {
        if (nextElement <= endElement) {
            codepointEnd = endElement;
            codepoint = nextElement;
            nextElement = endElement+1;
            return true;
        }
        if (range < endRange) {
            loadRange(++range);
            codepointEnd = endElement;
            codepoint = nextElement;
            nextElement = endElement+1;
            return true;
        }

        // stringIterator == null iff there are no string elements remaining

        if (stringIterator == null) {
            return false;
        }
        codepoint = IS_STRING; // signal that value is actually a string
        string = stringIterator.next();
        if (!stringIterator.hasNext()) {
            stringIterator = null;
        }
        return true;
    }

    /**
     * Sets this iterator to visit the elements of the given set and
     * resets it to the start of that set.  The iterator is valid only
     * so long as <tt>set</tt> is valid.
     * @param uset the set to iterate over.
     * @stable ICU 2.0
     */
    public void reset(UnicodeSet uset) {
        set = uset;
        reset();
    }

    /**
     * Resets this iterator to the start of the set.
     * @stable ICU 2.0
     */
    public void reset() {
        endRange = set.getRangeCount() - 1;
        range = 0;
        endElement = -1;
        nextElement = 0;
        if (endRange >= 0) {
            loadRange(range);
        }
        if (set.hasStrings()) {
            stringIterator = set.strings.iterator();
        } else {
            stringIterator = null;
        }
    }

    /**
     * Gets the current string from the iterator. Only use after calling next(), not nextRange().
     * @stable ICU 4.0
     */
    public String getString() {
        if (codepoint != IS_STRING) {
            return UTF16.valueOf(codepoint);
        }
        return string;
    }

    // ======================= PRIVATES ===========================

    private UnicodeSet set;
    private int endRange = 0;
    private int range = 0;

    private int endElement;
    private int nextElement;

    /**
     * Invariant: stringIterator is null when there are no (more) strings remaining
     */
    private Iterator<String> stringIterator = null;

    private void loadRange(int aRange) {
        nextElement = set.getRangeStart(aRange);
        endElement = set.getRangeEnd(aRange);
    }
}
