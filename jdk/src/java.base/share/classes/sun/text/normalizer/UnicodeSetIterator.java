/*
 * Copyright (c) 2005, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
/*
 *******************************************************************************
 * (C) Copyright IBM Corp. and others, 1996-2009 - All Rights Reserved         *
 *                                                                             *
 * The original version of this source code and documentation is copyrighted   *
 * and owned by IBM, These materials are provided under terms of a License     *
 * Agreement between IBM and Sun. This technology is protected by multiple     *
 * US and International patents. This notice and attribution to IBM may not    *
 * to removed.                                                                 *
 *******************************************************************************
 */

package sun.text.normalizer;

import java.util.Iterator;

/**
 * UnicodeSetIterator iterates over the contents of a UnicodeSet.  It
 * iterates over either code points or code point ranges.  After all
 * code points or ranges have been returned, it returns the
 * multicharacter strings of the UnicodSet, if any.
 *
 * <p>To iterate over code points, use a loop like this:
 * <pre>
 * UnicodeSetIterator it(set);
 * while (set.next()) {
 *   if (set.codepoint != UnicodeSetIterator::IS_STRING) {
 *     processCodepoint(set.codepoint);
 *   } else {
 *     processString(set.string);
 *   }
 * }
 * </pre>
 *
 * <p>To iterate over code point ranges, use a loop like this:
 * <pre>
 * UnicodeSetIterator it(set);
 * while (set.nextRange()) {
 *   if (set.codepoint != UnicodeSetIterator::IS_STRING) {
 *     processCodepointRange(set.codepoint, set.codepointEnd);
 *   } else {
 *     processString(set.string);
 *   }
 * }
 * </pre>
 * @author M. Davis
 * @stable ICU 2.0
 */
public class UnicodeSetIterator {

    /**
     * Value of {@code codepoint} if the iterator points to a string.
     * If {@code codepoint == IS_STRING}, then examine
     * {@code string} for the current iteration result.
     * @stable ICU 2.0
     */
    public static int IS_STRING = -1;

    /**
     * Current code point, or the special value {@code IS_STRING}, if
     * the iterator points to a string.
     * @stable ICU 2.0
     */
    public int codepoint;

    /**
     * When iterating over ranges using {@code nextRange()},
     * {@code codepointEnd} contains the inclusive end of the
     * iteration range, if {@code codepoint != IS_STRING}.  If
     * iterating over code points using {@code next()}, or if
     * {@code codepoint == IS_STRING}, then the value of
     * {@code codepointEnd} is undefined.
     * @stable ICU 2.0
     */
    public int codepointEnd;

    /**
     * If {@code codepoint == IS_STRING}, then {@code string} points
     * to the current string.  If {@code codepoint != IS_STRING}, the
     * value of {@code string} is undefined.
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
     * Returns the next element in the set, either a code point range
     * or a string.  If there are no more elements in the set, return
     * false.  If {@code codepoint == IS_STRING}, the value is a
     * string in the {@code string} field.  Otherwise the value is a
     * range of one or more code points from {@code codepoint} to
     * {@code codepointeEnd} inclusive.
     *
     * <p>The order of iteration is all code points ranges in sorted
     * order, followed by all strings sorted order.  Ranges are
     * disjoint and non-contiguous.  {@code string} is undefined
     * unless {@code codepoint == IS_STRING}.  Do not mix calls to
     * {@code next()} and {@code nextRange()} without calling
     * {@code reset()} between them.  The results of doing so are
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

        if (stringIterator == null) return false;
        codepoint = IS_STRING; // signal that value is actually a string
        string = stringIterator.next();
        if (!stringIterator.hasNext()) stringIterator = null;
        return true;
    }

    /**
     * Sets this iterator to visit the elements of the given set and
     * resets it to the start of that set.  The iterator is valid only
     * so long as {@code set} is valid.
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
        stringIterator = null;
        if (set.strings != null) {
            stringIterator = set.strings.iterator();
            if (!stringIterator.hasNext()) stringIterator = null;
        }
    }

    // ======================= PRIVATES ===========================

    private UnicodeSet set;
    private int endRange = 0;
    private int range = 0;
    /**
     * @internal
     */
    protected int endElement;
    /**
     * @internal
     */
    protected int nextElement;
    private Iterator<String> stringIterator = null;

    /**
     * Invariant: stringIterator is null when there are no (more) strings remaining
     */

    /**
     * @internal
     */
    protected void loadRange(int aRange) {
        nextElement = set.getRangeStart(aRange);
        endElement = set.getRangeEnd(aRange);
    }
}
