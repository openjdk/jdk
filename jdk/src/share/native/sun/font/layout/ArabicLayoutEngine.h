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
 * (C) Copyright IBM Corp. 1998-2005 - All Rights Reserved
 *
 */

#ifndef __ARABICLAYOUTENGINE_H
#define __ARABICLAYOUTENGINE_H

#include "LETypes.h"
#include "LEFontInstance.h"
#include "LEGlyphFilter.h"
#include "LayoutEngine.h"
#include "OpenTypeLayoutEngine.h"

#include "GlyphSubstitutionTables.h"
#include "GlyphDefinitionTables.h"
#include "GlyphPositioningTables.h"

U_NAMESPACE_BEGIN

/**
 * This class implements OpenType layout for Arabic fonts. It overrides
 * the characerProcessing method to assign the correct OpenType feature
 * tags for the Arabic contextual forms. It also overrides the adjustGlyphPositions
 * method to guarantee that all vowel and accent glyphs have zero advance width.
 *
 * @internal
 */
class ArabicOpenTypeLayoutEngine : public OpenTypeLayoutEngine
{
public:
    /**
     * This is the main constructor. It constructs an instance of ArabicOpenTypeLayoutEngine for
     * a particular font, script and language. It takes the GSUB table as a parameter since
     * LayoutEngine::layoutEngineFactory has to read the GSUB table to know that it has an
     * Indic OpenType font.
     *
     * @param fontInstance - the font
     * @param scriptCode - the script
     * @param langaugeCode - the language
     * @param gsubTable - the GSUB table
     * @param success - set to an error code if the operation fails
     *
     * @see LayoutEngine::layoutEngineFactory
     * @see OpenTypeLayoutEngine
     * @see ScriptAndLanguageTags.h for script and language codes
     *
     * @internal
     */
    ArabicOpenTypeLayoutEngine(const LEFontInstance *fontInstance, le_int32 scriptCode, le_int32 languageCode,
                            le_int32 typoFlags, const LEReferenceTo<GlyphSubstitutionTableHeader> &gsubTable, LEErrorCode &success);

    /**
     * This constructor is used when the font requires a "canned" GSUB table which can't be known
     * until after this constructor has been invoked.
     *
     * @param fontInstance - the font
     * @param scriptCode - the script
     * @param langaugeCode - the language
     * @param success - set to an error code if the operation fails
     *
     * @see OpenTypeLayoutEngine
     * @see ScriptAndLanguageTags.h for script and language codes
     *
     * @internal
     */
    ArabicOpenTypeLayoutEngine(const LEFontInstance *fontInstance, le_int32 scriptCode, le_int32 languageCode,
                               le_int32 typoFlags, LEErrorCode &success);

    /**
     * The destructor, virtual for correct polymorphic invocation.
     *
     * @internal
     */
    virtual ~ArabicOpenTypeLayoutEngine();

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

protected:

    /**
     * This method does Arabic OpenType character processing. It assigns the OpenType feature
     * tags to the characters to generate the correct contextual forms and ligatures.
     *
     * Input parameters:
     * @param chars - the input character context
     * @param offset - the index of the first character to process
     * @param count - the number of characters to process
     * @param max - the number of characters in the input context
     * @param rightToLeft - <code>TRUE</code> if the characters are in a right to left directional run
     *
     * Output parameters:
     * @param outChars - the output character arrayt
     * @param charIndices - the output character index array
     * @param featureTags - the output feature tag array
     * @param success - set to an error code if the operation fails
     *
     * @return the output character count
     *
     * @internal
     */
    virtual le_int32 characterProcessing(const LEUnicode chars[], le_int32 offset, le_int32 count, le_int32 max, le_bool rightToLeft,
            LEUnicode *&outChars, LEGlyphStorage &glyphStorage, LEErrorCode &success);

    /**
     * This method applies the GPOS table if it is present, otherwise it ensures that all vowel
     * and accent glyphs have a zero advance width by calling the adjustMarkGlyphs method.
     * If the font contains a GDEF table, that is used to identify voewls and accents. Otherwise
     * the character codes are used.
     *
     * @param chars - the input character context
     * @param offset - the offset of the first character to process
     * @param count - the number of characters to process
     * @param reverse - <code>TRUE</code> if the glyphs in the glyph array have been reordered
     * @param glyphs - the input glyph array
     * @param glyphCount - the number of glyphs
     * @param positions - the position array, will be updated as needed
     * @param success - output parameter set to an error code if the operation fails
     *
     * @internal
     */
    virtual void adjustGlyphPositions(const LEUnicode chars[], le_int32 offset, le_int32 count, le_bool reverse, LEGlyphStorage &glyphStorage, LEErrorCode &success);

    // static void adjustMarkGlyphs(const LEUnicode chars[], le_int32 offset, le_int32 count, le_bool rightToLeft, LEGlyphStorage &glyphStorage, LEErrorCode &success);

};

/**
 * The class implements OpenType layout for Arabic fonts which don't
 * contain a GSUB table, using a canned GSUB table based on Unicode
 * Arabic Presentation Forms. It overrides the mapCharsToGlyphs method
 * to use the Presentation Forms as logical glyph indices. It overrides the
 * glyphPostProcessing method to convert the Presentation Forms to actual
 * glyph indices.
 *
 * @see ArabicOpenTypeLayoutEngine
 *
 * @internal
 */
class UnicodeArabicOpenTypeLayoutEngine : public ArabicOpenTypeLayoutEngine
{
public:
    /**
     * This constructs an instance of UnicodeArabicOpenTypeLayoutEngine for a specific font,
     * script and language.
     *
     * @param fontInstance - the font
     * @param scriptCode - the script
     * @param languageCode - the language
     * @param success - set to an error code if the operation fails
     *
     * @see LEFontInstance
     * @see ScriptAndLanguageTags.h for script and language codes
     *
     * @internal
     */
    UnicodeArabicOpenTypeLayoutEngine(const LEFontInstance *fontInstance, le_int32 scriptCode, le_int32 languageCode,
                le_int32 typoFlags, LEErrorCode &success);

    /**
     * The destructor, virtual for correct polymorphic invocation.
     *
     * @internal
     */
    virtual ~UnicodeArabicOpenTypeLayoutEngine();

protected:

    /**
     * This method converts the Arabic Presentation Forms in the temp glyph array
     * into actual glyph indices using ArabicOpenTypeLayoutEngine::mapCharsToGlyps.
     *
     * Input parameters:
     * @param tempGlyphs - the input presentation forms
     * @param tempCharIndices - the input character index array
     * @param tempGlyphCount - the number of Presentation Froms
     *
     * Output parameters:
     * @param glyphs - the output glyph index array
     * @param charIndices - the output character index array
     * @param success - set to an error code if the operation fails
     *
     * @return the number of glyph indices in the output glyph index array
     *
     * @internal
     */
    virtual le_int32 glyphPostProcessing(LEGlyphStorage &tempGlyphStorage, LEGlyphStorage &glyphStorage, LEErrorCode &success);

    /**
     * This method copies the input characters into the output glyph index array,
     * for use by the canned GSUB table. It also generates the character index array.
     *
     * Input parameters:
     * @param chars - the input character context
     * @param offset - the offset of the first character to be mapped
     * @param count - the number of characters to be mapped
     * @param reverse - if <code>TRUE</code>, the output will be in reverse order
     * @param mirror - if <code>TRUE</code>, do character mirroring
     * @param glyphStorage - the glyph storage object. Glyph and char index arrays will be updated.
     *
     * @param success - set to an error code if the operation fails
     *
     * @internal
     */
    virtual void mapCharsToGlyphs(const LEUnicode chars[], le_int32 offset, le_int32 count, le_bool reverse, le_bool mirror,
        LEGlyphStorage &glyphStorage, LEErrorCode &success);

    /**
     * This method ensures that all vowel and accent glyphs have a zero advance width by calling
     * the adjustMarkGlyphs method. The character codes are used to identify the vowel and mark
     * glyphs.
     *
     * @param chars - the input character context
     * @param offset - the offset of the first character to process
     * @param count - the number of characters to process
     * @param reverse - <code>TRUE</code> if the glyphs in the glyph array have been reordered
     * @param glyphStorage - the glyph storage object. The glyph positions will be updated as needed.
     * @param success - output parameter set to an error code if the operation fails
     *
     * @internal
     */
    virtual void adjustGlyphPositions(const LEUnicode chars[], le_int32 offset, le_int32 count, le_bool reverse, LEGlyphStorage &glyphStorage, LEErrorCode &success);
};

U_NAMESPACE_END
#endif

