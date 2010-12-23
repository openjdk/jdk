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
 * (C) Copyright IBM Corp. 1998-2010 - All Rights Reserved
 *
 */

#ifndef __OPENTYPELAYOUTENGINE_H
#define __OPENTYPELAYOUTENGINE_H

#include "LETypes.h"
#include "LEGlyphFilter.h"
#include "LEFontInstance.h"
#include "LayoutEngine.h"

#include "GlyphSubstitutionTables.h"
#include "GlyphDefinitionTables.h"
#include "GlyphPositioningTables.h"

U_NAMESPACE_BEGIN

/**
 * OpenTypeLayoutEngine implements complex text layout for OpenType fonts - that is
 * fonts which have GSUB and GPOS tables associated with them. In order to do this,
 * the glyph processsing step described for LayoutEngine is further broken into three
 * steps:
 *
 * 1) Character processing - this step analyses the characters and assigns a list of OpenType
 *    feature tags to each one. It may also change, remove or add characters, and change
 *    their order.
 *
 * 2) Glyph processing - This step performs character to glyph mapping,and uses the GSUB
 *    table associated with the font to perform glyph substitutions, such as ligature substitution.
 *
 * 3) Glyph post processing - in cases where the font doesn't directly contain a GSUB table,
 *    the previous two steps may have generated "fake" glyph indices to use with a "canned" GSUB
 *    table. This step turns those glyph indices into actual font-specific glyph indices, and may
 *    perform any other adjustments requried by the previous steps.
 *
 * OpenTypeLayoutEngine will also use the font's GPOS table to apply position adjustments
 * such as kerning and accent positioning.
 *
 * @see LayoutEngine
 *
 * @internal
 */
class U_LAYOUT_API OpenTypeLayoutEngine : public LayoutEngine
{
public:
    /**
     * This is the main constructor. It constructs an instance of OpenTypeLayoutEngine for
     * a particular font, script and language. It takes the GSUB table as a parameter since
     * LayoutEngine::layoutEngineFactory has to read the GSUB table to know that it has an
     * OpenType font.
     *
     * @param fontInstance - the font
     * @param scriptCode - the script
     * @param langaugeCode - the language
     * @param gsubTable - the GSUB table
     * @param success - set to an error code if the operation fails
     *
     * @see LayoutEngine::layoutEngineFactory
     * @see ScriptAndLangaugeTags.h for script and language codes
     *
     * @internal
     */
    OpenTypeLayoutEngine(const LEFontInstance *fontInstance, le_int32 scriptCode, le_int32 languageCode,
                            le_int32 typoFlags, const GlyphSubstitutionTableHeader *gsubTable, LEErrorCode &success);

    /**
     * This constructor is used when the font requires a "canned" GSUB table which can't be known
     * until after this constructor has been invoked.
     *
     * @param fontInstance - the font
     * @param scriptCode - the script
     * @param langaugeCode - the language
     * @param success - set to an error code if the operation fails
     *
     * @internal
     */
    OpenTypeLayoutEngine(const LEFontInstance *fontInstance, le_int32 scriptCode, le_int32 languageCode,
                         le_int32 typoFlags, LEErrorCode &success);

    /**
     * The destructor, virtual for correct polymorphic invocation.
     *
     * @internal
     */
    virtual ~OpenTypeLayoutEngine();

    /**
     * A convenience method used to convert the script code into
     * the four byte script tag required by OpenType.
         * For Indic languages where multiple script tags exist,
         * the version 1 (old style) tag is returned.
     *
     * @param scriptCode - the script code
     *
     * @return the four byte script tag
     *
     * @internal
     */
    static LETag getScriptTag(le_int32 scriptCode);
    /**
     * A convenience method used to convert the script code into
     * the four byte script tag required by OpenType.
         * For Indic languages where multiple script tags exist,
         * the version 2 tag is returned.
     *
     * @param scriptCode - the script code
     *
     * @return the four byte script tag
     *
     * @internal
     */
    static LETag getV2ScriptTag(le_int32 scriptCode);

    /**
     * A convenience method used to convert the langauge code into
     * the four byte langauge tag required by OpenType.
     *
     * @param languageCode - the language code
     *
     * @return the four byte language tag
     *
     * @internal
     */
    static LETag getLangSysTag(le_int32 languageCode);

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

    /**
     * The array of language tags, indexed by language code.
     *
     * @internal
     */
    static const LETag languageTags[];

private:

    /**
     * This method is used by the constructors to convert the script
     * and language codes to four byte tags and save them.
     */
    void setScriptAndLanguageTags();

    /**
     * The array of script tags, indexed by script code.
     */
    static const LETag scriptTags[];

protected:
    /**
     * A set of "default" features. The default characterProcessing method
     * will apply all of these features to every glyph.
     *
     * @internal
     */
    FeatureMask fFeatureMask;

    /**
     * A set of mappings from feature tags to feature masks. These may
     * be in the order in which the featues should be applied, but they
     * don't need to be.
     *
     * @internal
     */
    const FeatureMap *fFeatureMap;

    /**
     * The length of the feature map.
     *
     * @internal
     */
    le_int32 fFeatureMapCount;

    /**
     * <code>TRUE</code> if the features in the
     * feature map are in the order in which they
     * must be applied.
     *
     * @internal
     */
    le_bool fFeatureOrder;

    /**
     * The address of the GSUB table.
     *
     * @internal
     */
    const GlyphSubstitutionTableHeader *fGSUBTable;

    /**
     * The address of the GDEF table.
     *
     * @internal
     */
    const GlyphDefinitionTableHeader   *fGDEFTable;

    /**
     * The address of the GPOS table.
     *
     * @internal
     */
    const GlyphPositioningTableHeader  *fGPOSTable;

    /**
     * An optional filter used to inhibit substitutions
     * preformed by the GSUB table. This is used for some
     * "canned" GSUB tables to restrict substitutions to
     * glyphs that are in the font.
     *
     * @internal
     */
    LEGlyphFilter *fSubstitutionFilter;

    /**
     * The four byte script tag.
     *
     * @internal
     */
    LETag fScriptTag;

    /**
     * The four byte script tag for V2 fonts.
     *
     * @internal
     */
    LETag fScriptTagV2;

    /**
     * The four byte language tag
     *
     * @internal
     */
    LETag fLangSysTag;

    /**
     * This method does the OpenType character processing. It assigns the OpenType feature
     * tags to the characters, and may generate output characters that differ from the input
     * charcters due to insertions, deletions, or reorderings. In such cases, it will also
     * generate an output character index array reflecting these changes.
     *
     * Subclasses must override this method.
     *
     * Input parameters:
     * @param chars - the input character context
     * @param offset - the index of the first character to process
     * @param count - the number of characters to process
     * @param max - the number of characters in the input context
     * @param rightToLeft - TRUE if the characters are in a right to left directional run
     *
     * Output parameters:
     * @param outChars - the output character array, if different from the input
     * @param charIndices - the output character index array
     * @param featureTags - the output feature tag array
     * @param success - set to an error code if the operation fails
     *
     * @return the output character count (input character count if no change)
     *
     * @internal
     */
    virtual le_int32 characterProcessing(const LEUnicode /*chars*/[], le_int32 offset, le_int32 count, le_int32 max, le_bool /*rightToLeft*/,
            LEUnicode *&/*outChars*/, LEGlyphStorage &glyphStorage, LEErrorCode &success);

    /**
     * This method does character to glyph mapping, and applies the GSUB table. The
     * default implementation calls mapCharsToGlyphs and then applies the GSUB table,
     * if there is one.
     *
     * Note that in the case of "canned" GSUB tables, the output glyph indices may be
     * "fake" glyph indices that need to be converted to "real" glyph indices by the
     * glyphPostProcessing method.
     *
     * Input parameters:
     * @param chars - the input character context
     * @param offset - the index of the first character to process
     * @param count - the number of characters to process
     * @param max - the number of characters in the input context
     * @param rightToLeft - TRUE if the characters are in a right to left directional run
     * @param featureTags - the feature tag array
     *
     * Output parameters:
     * @param glyphs - the output glyph index array
     * @param charIndices - the output character index array
     * @param success - set to an error code if the operation fails
     *
     * @return the number of glyphs in the output glyph index array
     *
     * Note: if the character index array was already set by the characterProcessing
     * method, this method won't change it.
     *
     * @internal
     */
    virtual le_int32 glyphProcessing(const LEUnicode chars[], le_int32 offset, le_int32 count, le_int32 max, le_bool rightToLeft,
            LEGlyphStorage &glyphStorage, LEErrorCode &success);

    virtual le_int32 glyphSubstitution(le_int32 count, le_int32 max, le_bool rightToLeft, LEGlyphStorage &glyphStorage, LEErrorCode &success);

    /**
     * This method does any processing necessary to convert "fake"
     * glyph indices used by the glyphProcessing method into "real" glyph
     * indices which can be used to render the text. Note that in some
     * cases, such as CDAC Indic fonts, several "real" glyphs may be needed
     * to render one "fake" glyph.
     *
     * The default implementation of this method just returns the input glyph
     * index and character index arrays, assuming that no "fake" glyph indices
     * were needed to do GSUB processing.
     *
     * Input paramters:
     * @param tempGlyphs - the input "fake" glyph index array
     * @param tempCharIndices - the input "fake" character index array
     * @param tempGlyphCount - the number of "fake" glyph indices
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
     * This method applies the characterProcessing, glyphProcessing and glyphPostProcessing
     * methods. Most subclasses will not need to override this method.
     *
     * Input parameters:
     * @param chars - the input character context
     * @param offset - the index of the first character to process
     * @param count - the number of characters to process
     * @param max - the number of characters in the input context
     * @param rightToLeft - TRUE if the text is in a right to left directional run
     *
     * Output parameters:
     * @param glyphs - the glyph index array
     * @param charIndices - the character index array
     * @param success - set to an error code if the operation fails
     *
     * @return the number of glyphs in the glyph index array
     *
     * @see LayoutEngine::computeGlyphs
     *
     * @internal
     */
    virtual le_int32 computeGlyphs(const LEUnicode chars[], le_int32 offset, le_int32 count, le_int32 max, le_bool rightToLeft, LEGlyphStorage &glyphStorage, LEErrorCode &success);

    /**
     * This method uses the GPOS table, if there is one, to adjust the glyph positions.
     *
     * Input parameters:
     * @param glyphs - the input glyph array
     * @param glyphCount - the number of glyphs in the glyph array
     * @param x - the starting X position
     * @param y - the starting Y position
     *
     * Output parameters:
     * @param positions - the output X and Y positions (two entries per glyph)
     * @param success - set to an error code if the operation fails
     *
     * @internal
     */
    virtual void adjustGlyphPositions(const LEUnicode chars[], le_int32 offset, le_int32 count, le_bool reverse, LEGlyphStorage &glyphStorage, LEErrorCode &success);

    /**
     * This method frees the feature tag array so that the
     * OpenTypeLayoutEngine can be reused for different text.
     * It is also called from our destructor.
     *
     * @internal
     */
    virtual void reset();
};

U_NAMESPACE_END
#endif

