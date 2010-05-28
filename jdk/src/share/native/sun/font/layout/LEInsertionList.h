/*
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
 *
 */

/*
 **********************************************************************
 *   Copyright (C) 1998-2004, International Business Machines
 *   Corporation and others.  All Rights Reserved.
 **********************************************************************
 */

#ifndef __LEINSERTIONLIST_H
#define __LEINSERTIONLIST_H

#include "LETypes.h"

U_NAMESPACE_BEGIN

struct InsertionRecord;

/**
 * This class encapsulates the callback used by <code>LEInsertionList</code>
 * to apply an insertion from the insertion list.
 *
 * @internal
 */
class LEInsertionCallback
{
public:
    /**
     * This method will be called by <code>LEInsertionList::applyInsertions</code> for each
     * entry on the insertion list.
     *
     * @param atPosition the position of the insertion
     * @param count the number of glyphs to insert
     * @param newGlyphs the address of the glyphs to insert
     *
     * @return <code>TRUE</code> if <code>LEInsertions::applyInsertions</code> should
     *         stop after applying this insertion.
     *
     * @internal
     */
    virtual le_bool applyInsertion(le_int32 atPosition, le_int32 count, LEGlyphID newGlyphs[]) = 0;
};

/**
 * This class is used to keep track of insertions to an array of
 * <code>LEGlyphIDs</code>. The insertions are kept on a linked
 * list of <code>InsertionRecords</code> so that the glyph array
 * doesn't have to be grown for each insertion. The insertions are
 * stored on the list from leftmost to rightmost to make it easier
 * to do the insertions.
 *
 * The insertions are applied to the array by calling the
 * <code>applyInsertions</code> method, which calls a client
 * supplied <code>LEInsertionCallback</code> object to actually
 * apply the individual insertions.
 *
 * @internal
 */
class LEInsertionList : public UObject
{
public:
    /**
     * Construct an empty insertion list.
     *
     * @param rightToLeft <code>TRUE</code> if the glyphs are stored
     *                    in the array in right to left order.
     *
     * @internal
     */
    LEInsertionList(le_bool rightToLeft);

    /**
     * The destructor.
     */
    ~LEInsertionList();

    /**
     * Add an entry to the insertion list.
     *
     * @param position the glyph at this position in the array will be
     *                 replaced by the new glyphs.
     * @param count the number of new glyphs
     *
     * @return the address of an array in which to store the new glyphs. This will
     *         <em>not</em> be in the glyph array.
     *
     * @internal
     */
    LEGlyphID *insert(le_int32 position, le_int32 count);

    /**
     * Return the number of new glyphs that have been inserted.
     *
     * @return the number of new glyphs which have been inserted
     *
     * @internal
     */
    le_int32 getGrowAmount();

    /**
     * Call the <code>LEInsertionCallback</code> once for each
     * entry on the insertion list.
     *
     * @param callback the <code>LEInsertionCallback</code> to call for each insertion.
     *
     * @return <code>TRUE</code> if <code>callback</code> returned <code>TRUE</code> to
     *         terminate the insertion list processing.
     *
     * @internal
     */
    le_bool applyInsertions(LEInsertionCallback *callback);

    /**
     * Empty the insertion list and free all associated
     * storage.
     *
     * @internal
     */
    void reset();

    /**
     * ICU "poor man's RTTI", returns a UClassID for the actual class.
     *
     * @stable ICU 2.8
     */
    virtual UClassID getDynamicClassID() const;

    /**
     * ICU "poor man's RTTI", returns a UClassID for this class.
     *
     * @stable ICU 2.8
     */
    static UClassID getStaticClassID();

private:

    /**
     * The head of the insertion list.
     *
     * @internal
     */
    InsertionRecord *head;

    /**
     * The tail of the insertion list.
     *
     * @internal
     */
    InsertionRecord *tail;

    /**
     * The total number of new glyphs on the insertion list.
     *
     * @internal
     */
    le_int32 growAmount;

    /**
     * Set to <code>TRUE</code> if the glyphs are in right
     * to left order. Since we want the rightmost insertion
     * to be first on the list, we need to append the
     * insertions in this case. Otherwise they're prepended.
     *
     * @internal
     */
    le_bool  append;
};

U_NAMESPACE_END
#endif

