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
 *
 * (C) Copyright IBM Corp. 1998-2004 - All Rights Reserved
 *
 */

#ifndef __CHARSUBSTITUTIONFILTER_H
#define __CHARSUBSTITUTIONFILTER_H

#include "LETypes.h"
#include "LEGlyphFilter.h"

U_NAMESPACE_BEGIN

class LEFontInstance;

/**
 * This filter is used by character-based GSUB processors. It
 * accepts only those characters which the given font can display.
 *
 * Note: Implementation is in ArabicLayoutEngine.cpp
 *
 * @internal
 */
class CharSubstitutionFilter : public UMemory, public LEGlyphFilter
{
private:
    /**
     * Holds the font which is used to test the characters.
     *
     * @internal
     */
    const LEFontInstance *fFontInstance;

    /**
     * The copy constructor. Not allowed!
     *
     * @internal
     */
    CharSubstitutionFilter(const CharSubstitutionFilter &other); // forbid copying of this class

    /**
     * The replacement operator. Not allowed!
     *
     * @internal
     */
    CharSubstitutionFilter &operator=(const CharSubstitutionFilter &other); // forbid copying of this class

public:
    /**
     * The constructor.
     *
     * @param fontInstance - the font to use to test the characters.
     *
     * @internal
     */
    CharSubstitutionFilter(const LEFontInstance *fontInstance);

    /**
     * The destructor.
     *
     * @internal
     */
    ~CharSubstitutionFilter();

    /**
     * This method is used to test if a particular
     * character can be displayed by the filter's
     * font.
     *
     * @param glyph - the Unicode character code to be tested
     *
     * @return TRUE if the filter's font can display this character.
     *
     * @internal
     */
    le_bool accept(LEGlyphID glyph, LEErrorCode &success) const;
};

U_NAMESPACE_END
#endif


