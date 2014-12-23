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
 * (C) Copyright IBM Corp. 1998-2013 - All Rights Reserved
 *
 */

#ifndef __LAYOUTENGINE_H
#define __LAYOUTENGINE_H

#include "LETypes.h"

/**
 * \file
 * \brief C++ API: Virtual base class for complex text layout.
 */

U_NAMESPACE_BEGIN

class LEFontInstance;
class LEGlyphFilter;
class LEGlyphStorage;

/**
 * This is a virtual base class used to do complex text layout. The text must all
 * be in a single font, script, and language. An instance of a LayoutEngine can be
 * created by calling the layoutEngineFactory method. Fonts are identified by
 * instances of the LEFontInstance class. Script and language codes are identified
 * by integer codes, which are defined in ScriptAndLanuageTags.h.
 *
 * Note that this class is not public API. It is declared public so that it can be
 * exported from the library that it is a part of.
 *
 * The input to the layout process is an array of characters in logical order,
 * and a starting X, Y position for the text. The output is an array of glyph indices,
 * an array of character indices for the glyphs, and an array of glyph positions.
 * These arrays are protected members of LayoutEngine which can be retreived by a
 * public method. The reset method can be called to free these arrays so that the
 * LayoutEngine can be reused.
 *
 * The layout process is done in three steps. There is a protected virtual method
 * for each step. These methods have a default implementation which only does
 * character to glyph mapping and default positioning using the glyph's advance
 * widths. Subclasses can override these methods for more advanced layout.
 * There is a public method which invokes the steps in the correct order.
 *
 * The steps are:
 *
 * 1) Glyph processing - character to glyph mapping and any other glyph processing
 *    such as ligature substitution and contextual forms.
 *
 * 2) Glyph positioning - position the glyphs based on their advance widths.
 *
 * 3) Glyph position adjustments - adjustment of glyph positions for kerning,
 *    accent placement, etc.
 *
 * NOTE: in all methods below, output parameters are references to pointers so
 * the method can allocate and free the storage as needed. All storage allocated
 * in this way is owned by the object which created it, and will be freed when it
 * is no longer needed, or when the object's destructor is invoked.
 *
 * @see LEFontInstance
 * @see ScriptAndLanguageTags.h
 *
 * @stable ICU 2.8
 */
class U_LAYOUT_API LayoutEngine : public UObject {
public:
#ifndef U_HIDE_INTERNAL_API
    /** @internal Flag to request kerning. Use LE_Kerning_FEATURE_FLAG instead. */
    static const le_int32 kTypoFlagKern;
    /** @internal Flag to request ligatures. Use LE_Ligatures_FEATURE_FLAG instead. */
    static const le_int32 kTypoFlagLiga;
#endif  /* U_HIDE_INTERNAL_API */

protected:
    /**
     * The object which holds the glyph storage
     *
     * @internal
     */
    LEGlyphStorage *fGlyphStorage;

    /**
     * The font instance for the text font.
     *
     * @see LEFontInstance
     *
     * @internal
     */
    const LEFontInstance *fFontInstance;

    /**
     * The script code for the text
     *
     * @see ScriptAndLanguageTags.h for script codes.
     *
     * @internal
     */
    le_int32 fScriptCode;

    /**
     * The langauge code for the text
     *
     * @see ScriptAndLanguageTags.h for language codes.
     *
     * @internal
     */
    le_int32 fLanguageCode;

    /**
     * The typographic control flags
     *
     * @internal
     */
    le_int32 fTypoFlags;

    /**
     * <code>TRUE</code> if <code>mapCharsToGlyphs</code> should replace ZWJ / ZWNJ with a glyph
     * with no contours.
     *
     * @internal
     */
    le_bool fFilterZeroWidth;

#ifndef U_HIDE_INTERNAL_API
    /**
     * This constructs an instance for a given font, script and language. Subclass constructors
     * must call this constructor.
     *
     * @param fontInstance - the font for the text
     * @param scriptCode - the script for the text
     * @param languageCode - the language for the text
     * @param typoFlags - the typographic control flags for the text (a bitfield).  Use kTypoFlagKern
     * if kerning is desired, kTypoFlagLiga if ligature formation is desired.  Others are reserved.
     * @param success - set to an error code if the operation fails
     *
     * @see LEFontInstance
     * @see ScriptAndLanguageTags.h
     *
     * @internal
     */
    LayoutEngine(const LEFontInstance *fontInstance,
                 le_int32 scriptCode,
                 le_int32 languageCode,
                 le_int32 typoFlags,
                 LEErrorCode &success);
#endif  /* U_HIDE_INTERNAL_API */

    // Do not enclose the protected default constructor with #ifndef U_HIDE_INTERNAL_API
    // or else the compiler will create a public default constructor.
    /**
     * This overrides the default no argument constructor to make it
     * difficult for clients to call it. Clients are expected to call
     * layoutEngineFactory.
     *
     * @internal
     */
    LayoutEngine();

    /**
     * This method does any required pre-processing to the input characters. It
     * may generate output characters that differ from the input charcters due to
     * insertions, deletions, or reorderings. In such cases, it will also generate an
     * output character index array reflecting these changes.
     *
     * Subclasses must override this method.
     *
     * Input parameters:
     * @param chars - the input character context
     * @param offset - the index of the first character to process
     * @param count - the number of characters to process
     * @param max - the number of characters in the input context
     * @param rightToLeft - TRUE if the characters are in a right to left directional run
     * @param outChars - the output character array, if different from the input
     * @param glyphStorage - the object that holds the per-glyph storage. The character index array may be set.
     * @param success - set to an error code if the operation fails
     *
     * @return the output character count (input character count if no change)
     *
     * @internal
     */
    virtual le_int32 characterProcessing(const LEUnicode chars[], le_int32 offset, le_int32 count, le_int32 max, le_bool rightToLeft,
            LEUnicode *&outChars, LEGlyphStorage &glyphStorage, LEErrorCode &success);

    /**
     * This method does the glyph processing. It converts an array of characters
     * into an array of glyph indices and character indices. The characters to be
     * processed are passed in a surrounding context. The context is specified as
     * a starting address and a maximum character count. An offset and a count are
     * used to specify the characters to be processed.
     *
     * The default implementation of this method only does character to glyph mapping.
     * Subclasses needing more elaborate glyph processing must override this method.
     *
     * Input parameters:
     * @param chars - the character context
     * @param offset - the offset of the first character to process
     * @param count - the number of characters to process
     * @param max - the number of characters in the context.
     * @param rightToLeft - TRUE if the text is in a right to left directional run
     * @param glyphStorage - the object which holds the per-glyph storage. The glyph and char indices arrays
     *                       will be set.
     *
     * Output parameters:
     * @param success - set to an error code if the operation fails
     *
     * @return the number of glyphs in the glyph index array
     *
     * @internal
     */
    virtual le_int32 computeGlyphs(const LEUnicode chars[], le_int32 offset, le_int32 count, le_int32 max, le_bool rightToLeft, LEGlyphStorage &glyphStorage, LEErrorCode &success);

    /**
     * This method does basic glyph positioning. The default implementation positions
     * the glyphs based on their advance widths. This is sufficient for most uses. It
     * is not expected that many subclasses will override this method.
     *
     * Input parameters:
     * @param glyphStorage - the object which holds the per-glyph storage. The glyph position array will be set.
     * @param x - the starting X position
     * @param y - the starting Y position
     * @param success - set to an error code if the operation fails
     *
     * @internal
     */
    virtual void positionGlyphs(LEGlyphStorage &glyphStorage, float x, float y, LEErrorCode &success);

    /**
     * This method does positioning adjustments like accent positioning and
     * kerning. The default implementation does nothing. Subclasses needing
     * position adjustments must override this method.
     *
     * Note that this method has both characters and glyphs as input so that
     * it can use the character codes to determine glyph types if that information
     * isn't directly available. (e.g. Some Arabic OpenType fonts don't have a GDEF
     * table)
     *
     * @param chars - the input character context
     * @param offset - the offset of the first character to process
     * @param count - the number of characters to process
     * @param reverse - <code>TRUE</code> if the glyphs in the glyph array have been reordered
     * @param glyphStorage - the object which holds the per-glyph storage. The glyph positions will be
     *                       adjusted as needed.
     * @param success - output parameter set to an error code if the operation fails
     *
     * @internal
     */
    virtual void adjustGlyphPositions(const LEUnicode chars[], le_int32 offset, le_int32 count, le_bool reverse, LEGlyphStorage &glyphStorage, LEErrorCode &success);

    /**
     * This method gets a table from the font associated with
     * the text. The default implementation gets the table from
     * the font instance. Subclasses which need to get the tables
     * some other way must override this method.
     *
     * @param tableTag - the four byte table tag.
     * @param length - length to use
     *
     * @return the address of the table.
     *
     * @internal
     */
    virtual const void *getFontTable(LETag tableTag, size_t &length) const;

    /**
     * @deprecated
     */
    virtual const void *getFontTable(LETag tableTag) const { size_t ignored; return getFontTable(tableTag, ignored); }

    /**
     * This method does character to glyph mapping. The default implementation
     * uses the font instance to do the mapping. It will allocate the glyph and
     * character index arrays if they're not already allocated. If it allocates the
     * character index array, it will fill it.
     *
     * This method supports right to left
     * text with the ability to store the glyphs in reverse order, and by supporting
     * character mirroring, which will replace a character which has a left and right
     * form, such as parens, with the opposite form before mapping it to a glyph index.
     *
     * Input parameters:
     * @param chars - the input character context
     * @param offset - the offset of the first character to be mapped
     * @param count - the number of characters to be mapped
     * @param reverse - if <code>TRUE</code>, the output will be in reverse order
     * @param mirror - if <code>TRUE</code>, do character mirroring
     * @param glyphStorage - the object which holds the per-glyph storage. The glyph and char
     *                       indices arrays will be filled in.
     * @param success - set to an error code if the operation fails
     *
     * @see LEFontInstance
     *
     * @internal
     */
    virtual void mapCharsToGlyphs(const LEUnicode chars[], le_int32 offset, le_int32 count, le_bool reverse, le_bool mirror, LEGlyphStorage &glyphStorage, LEErrorCode &success);

#ifndef U_HIDE_INTERNAL_API
    /**
     * This is a convenience method that forces the advance width of mark
     * glyphs to be zero, which is required for proper selection and highlighting.
     *
     * @param glyphStorage - the object containing the per-glyph storage. The positions array will be modified.
     * @param markFilter - used to identify mark glyphs
     * @param success - output parameter set to an error code if the operation fails
     *
     * @see LEGlyphFilter
     *
     * @internal
     */
    static void adjustMarkGlyphs(LEGlyphStorage &glyphStorage, LEGlyphFilter *markFilter, LEErrorCode &success);


    /**
     * This is a convenience method that forces the advance width of mark
     * glyphs to be zero, which is required for proper selection and highlighting.
     * This method uses the input characters to identify marks. This is required in
     * cases where the font does not contain enough information to identify them based
     * on the glyph IDs.
     *
     * @param chars - the array of input characters
     * @param charCount - the number of input characers
     * @param glyphStorage - the object containing the per-glyph storage. The positions array will be modified.
     * @param reverse - <code>TRUE</code> if the glyph array has been reordered
     * @param markFilter - used to identify mark glyphs
     * @param success - output parameter set to an error code if the operation fails
     *
     * @see LEGlyphFilter
     *
     * @internal
     */
    static void adjustMarkGlyphs(const LEUnicode chars[], le_int32 charCount, le_bool reverse, LEGlyphStorage &glyphStorage, LEGlyphFilter *markFilter, LEErrorCode &success);
#endif  /* U_HIDE_INTERNAL_API */

public:
    /**
     * The destructor. It will free any storage allocated for the
     * glyph, character index and position arrays by calling the reset
     * method. It is declared virtual so that it will be invoked by the
     * subclass destructors.
     *
     * @stable ICU 2.8
     */
    virtual ~LayoutEngine();

    /**
     * This method will invoke the layout steps in their correct order by calling
     * the computeGlyphs, positionGlyphs and adjustGlyphPosition methods. It will
     * compute the glyph, character index and position arrays.
     *
     * @param chars - the input character context
     * @param offset - the offset of the first character to process
     * @param count - the number of characters to process
     * @param max - the number of characters in the input context
     * @param rightToLeft - TRUE if the characers are in a right to left directional run
     * @param x - the initial X position
     * @param y - the initial Y position
     * @param success - output parameter set to an error code if the operation fails
     *
     * @return the number of glyphs in the glyph array
     *
     * Note: The glyph, character index and position array can be accessed
     * using the getter methods below.
     *
     * Note: If you call this method more than once, you must call the reset()
     * method first to free the glyph, character index and position arrays
     * allocated by the previous call.
     *
     * @stable ICU 2.8
     */
    virtual le_int32 layoutChars(const LEUnicode chars[], le_int32 offset, le_int32 count, le_int32 max, le_bool rightToLeft, float x, float y, LEErrorCode &success);

    /**
     * This method returns the number of glyphs in the glyph array. Note
     * that the number of glyphs will be greater than or equal to the number
     * of characters used to create the LayoutEngine.
     *
     * @return the number of glyphs in the glyph array
     *
     * @stable ICU 2.8
     */
    le_int32 getGlyphCount() const;

    /**
     * This method copies the glyph array into a caller supplied array.
     * The caller must ensure that the array is large enough to hold all
     * the glyphs.
     *
     * @param glyphs - the destiniation glyph array
     * @param success - set to an error code if the operation fails
     *
     * @stable ICU 2.8
     */
    void getGlyphs(LEGlyphID glyphs[], LEErrorCode &success) const;

    /**
     * This method copies the glyph array into a caller supplied array,
     * ORing in extra bits. (This functionality is needed by the JDK,
     * which uses 32 bits pre glyph idex, with the high 16 bits encoding
     * the composite font slot number)
     *
     * @param glyphs - the destination (32 bit) glyph array
     * @param extraBits - this value will be ORed with each glyph index
     * @param success - set to an error code if the operation fails
     *
     * @stable ICU 2.8
     */
    virtual void getGlyphs(le_uint32 glyphs[], le_uint32 extraBits, LEErrorCode &success) const;

    /**
     * This method copies the character index array into a caller supplied array.
     * The caller must ensure that the array is large enough to hold a
     * character index for each glyph.
     *
     * @param charIndices - the destiniation character index array
     * @param success - set to an error code if the operation fails
     *
     * @stable ICU 2.8
     */
    void getCharIndices(le_int32 charIndices[], LEErrorCode &success) const;

    /**
     * This method copies the character index array into a caller supplied array.
     * The caller must ensure that the array is large enough to hold a
     * character index for each glyph.
     *
     * @param charIndices - the destiniation character index array
     * @param indexBase - an offset which will be added to each index
     * @param success - set to an error code if the operation fails
     *
     * @stable ICU 2.8
     */
    void getCharIndices(le_int32 charIndices[], le_int32 indexBase, LEErrorCode &success) const;

    /**
     * This method copies the position array into a caller supplied array.
     * The caller must ensure that the array is large enough to hold an
     * X and Y position for each glyph, plus an extra X and Y for the
     * advance of the last glyph.
     *
     * @param positions - the destiniation position array
     * @param success - set to an error code if the operation fails
     *
     * @stable ICU 2.8
     */
    void getGlyphPositions(float positions[], LEErrorCode &success) const;

    /**
     * This method returns the X and Y position of the glyph at
     * the given index.
     *
     * Input parameters:
     * @param glyphIndex - the index of the glyph
     *
     * Output parameters:
     * @param x - the glyph's X position
     * @param y - the glyph's Y position
     * @param success - set to an error code if the operation fails
     *
     * @stable ICU 2.8
     */
    void getGlyphPosition(le_int32 glyphIndex, float &x, float &y, LEErrorCode &success) const;

    /**
     * This method frees the glyph, character index and position arrays
     * so that the LayoutEngine can be reused to layout a different
     * characer array. (This method is also called by the destructor)
     *
     * @stable ICU 2.8
     */
    virtual void reset();

    /**
     * This method returns a LayoutEngine capable of laying out text
     * in the given font, script and langauge. Note that the LayoutEngine
     * returned may be a subclass of LayoutEngine.
     *
     * @param fontInstance - the font of the text
     * @param scriptCode - the script of the text
     * @param languageCode - the language of the text
     * @param success - output parameter set to an error code if the operation fails
     *
     * @return a LayoutEngine which can layout text in the given font.
     *
     * @see LEFontInstance
     *
     * @stable ICU 2.8
     */
    static LayoutEngine *layoutEngineFactory(const LEFontInstance *fontInstance, le_int32 scriptCode, le_int32 languageCode, LEErrorCode &success);

    /**
     * Override of existing call that provides flags to control typography.
     * @stable ICU 3.4
     */
    static LayoutEngine *layoutEngineFactory(const LEFontInstance *fontInstance, le_int32 scriptCode, le_int32 languageCode, le_int32 typo_flags, LEErrorCode &success);

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

};

U_NAMESPACE_END
#endif
