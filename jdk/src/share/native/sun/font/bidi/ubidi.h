/*
 * Portions Copyright 2000-2003 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * (C) Copyright IBM Corp. 1999-2003 - All Rights Reserved
 *
 * The original version of this source code and documentation is
 * copyrighted and owned by IBM. These materials are provided
 * under terms of a License Agreement between IBM and Sun.
 * This technology is protected by multiple US and International
 * patents. This notice and attribution to IBM may not be removed.
 */

/*
*   file name:  ubidi.h
*   encoding:   US-ASCII
*   tab size:   8 (not used)
*   indentation:4
*
*   created on: 1999jul27
*   created by: Markus W. Scherer
*/

#ifndef UBIDI_H
#define UBIDI_H

#include "utypes.h"
#include "uchardir.h"

/*
 * javadoc-style comments are intended to be transformed into HTML
 * using DOC++ - see
 * http://www.zib.de/Visual/software/doc++/index.html .
 *
 * The HTML documentation is created with
 *  doc++ -H ubidi.h
 *
 * The following #define trick allows us to do it all in one file
 * and still be able to compile it.
 */
#define DOCXX_TAG
#define BIDI_SAMPLE_CODE

/**
 * @name BiDi algorithm for ICU
 *
 * <h2>BiDi algorithm for ICU</h2>
 *
 * This is an implementation of the Unicode Bidirectional algorithm.
 * The algorithm is defined in the
 * <a href="http://www.unicode.org/unicode/reports/tr9/">Unicode Technical Report 9</a>,
 * version 5, also described in The Unicode Standard, Version 3.0 .<p>
 *
 * <h3>General remarks about the API:</h3>
 *
 * In functions with an error code parameter,
 * the <code>pErrorCode</code> pointer must be valid
 * and the value that it points to must not indicate a failure before
 * the function call. Otherwise, the function returns immediately.
 * After the function call, the value indicates success or failure.<p>
 *
 * The <quote>limit</quote> of a sequence of characters is the position just after their
 * last character, i.e., one more than that position.<p>
 *
 * Some of the API functions provide access to <quote>runs</quote>.
 * Such a <quote>run</quote> is defined as a sequence of characters
 * that are at the same embedding level
 * after performing the BiDi algorithm.<p>
 *
 * @author Markus W. Scherer
 */
DOCXX_TAG
/*@{*/

/**
 * UBiDiLevel is the type of the level values in this
 * BiDi implementation.
 * It holds an embedding level and indicates the visual direction
 * by its bit&nbsp;0 (even/odd value).<p>
 *
 * It can also hold non-level values for the
 * <code>paraLevel</code> and <code>embeddingLevels</code>
 * arguments of <code>ubidi_setPara()</code>; there:
 * <ul>
 * <li>bit&nbsp;7 of an <code>embeddingLevels[]</code>
 * value indicates whether the using application is
 * specifying the level of a character to <i>override</i> whatever the
 * BiDi implementation would resolve it to.</li>
 * <li><code>paraLevel</code> can be set to the
 * pesudo-level values <code>UBIDI_DEFAULT_LTR</code>
 * and <code>UBIDI_DEFAULT_RTL</code>.</li>
 *
 * @see ubidi_setPara
 *
 * <p>The related constants are not real, valid level values.
 * <code>UBIDI_DEFAULT_XXX</code> can be used to specify
 * a default for the paragraph level for
 * when the <code>ubidi_setPara()</code> function
 * shall determine it but there is no
 * strongly typed character in the input.<p>
 *
 * Note that the value for <code>UBIDI_DEFAULT_LTR</code> is even
 * and the one for <code>UBIDI_DEFAULT_RTL</code> is odd,
 * just like with normal LTR and RTL level values -
 * these special values are designed that way. Also, the implementation
 * assumes that UBIDI_MAX_EXPLICIT_LEVEL is odd.
 *
 * @see UBIDI_DEFAULT_LTR
 * @see UBIDI_DEFAULT_RTL
 * @see UBIDI_LEVEL_OVERRIDE
 * @see UBIDI_MAX_EXPLICIT_LEVEL
 */
typedef uint8_t UBiDiLevel;

/** @memo If there is no strong character, then set the paragraph level to 0 (left-to-right). */
#define UBIDI_DEFAULT_LTR 0xfe

/** @memo If there is no strong character, then set the paragraph level to 1 (right-to-left). */
#define UBIDI_DEFAULT_RTL 0xff

/**
 * @memo Maximum explicit embedding level
 * (The maximum resolved level can be up to <code>UBIDI_MAX_EXPLICIT_LEVEL+1</code>).
 */
#define UBIDI_MAX_EXPLICIT_LEVEL 61

/** @memo Bit flag for level input: overrides directional properties. */
#define UBIDI_LEVEL_OVERRIDE 0x80

/**
 * @memo <code>UBiDiDirection</code> values indicate the text direction.
 */
enum UBiDiDirection {
    /** @memo All left-to-right text. This is a 0 value. */
    UBIDI_LTR,
    /** @memo All right-to-left text. This is a 1 value. */
    UBIDI_RTL,
    /** @memo Mixed-directional text. */
    UBIDI_MIXED
};

typedef enum UBiDiDirection UBiDiDirection;

/**
 * Forward declaration of the <code>UBiDi</code> structure for the declaration of
 * the API functions. Its fields are implementation-specific.<p>
 * This structure holds information about a paragraph of text
 * with BiDi-algorithm-related details, or about one line of
 * such a paragraph.<p>
 * Reordering can be done on a line, or on a paragraph which is
 * then interpreted as one single line.
 */
struct UBiDi;

typedef struct UBiDi UBiDi;

/**
 * Allocate a <code>UBiDi</code> structure.
 * Such an object is initially empty. It is assigned
 * the BiDi properties of a paragraph by <code>ubidi_setPara()</code>
 * or the BiDi properties of a line of a paragraph by
 * <code>ubidi_getLine()</code>.<p>
 * This object can be reused for as long as it is not deallocated
 * by calling <code>ubidi_close()</code>.<p>
 * <code>ubidi_set()</code> will allocate additional memory for
 * internal structures as necessary.
 *
 * @return An empty <code>UBiDi</code> object.
 */
U_CAPI UBiDi * U_EXPORT2
ubidi_open();

/**
 * Allocate a <code>UBiDi</code> structure with preallocated memory
 * for internal structures.
 * This function provides a <code>UBiDi</code> object like <code>ubidi_open()</code>
 * with no arguments, but it also preallocates memory for internal structures
 * according to the sizings supplied by the caller.<p>
 * Subsequent functions will not allocate any more memory, and are thus
 * guaranteed not to fail because of lack of memory.<p>
 * The preallocation can be limited to some of the internal memory
 * by setting some values to 0 here. That means that if, e.g.,
 * <code>maxRunCount</code> cannot be reasonably predetermined and should not
 * be set to <code>maxLength</code> (the only failproof value) to avoid
 * wasting memory, then <code>maxRunCount</code> could be set to 0 here
 * and the internal structures that are associated with it will be allocated
 * on demand, just like with <code>ubidi_open()</code>.
 *
 * @param maxLength is the maximum paragraph or line length that internal memory
 *        will be preallocated for. An attempt to associate this object with a
 *        longer text will fail, unless this value is 0, which leaves the allocation
 *        up to the implementation.
 *
 * @param maxRunCount is the maximum anticipated number of same-level runs
 *        that internal memory will be preallocated for. An attempt to access
 *        visual runs on an object that was not preallocated for as many runs
 *        as the text was actually resolved to will fail,
 *        unless this value is 0, which leaves the allocation up to the implementation.<p>
 *        The number of runs depends on the actual text and maybe anywhere between
 *        1 and <code>maxLength</code>. It is typically small.<p>
 *
 * @param pErrorCode must be a valid pointer to an error code value,
 *        which must not indicate a failure before the function call.
 *
 * @return An empty <code>UBiDi</code> object with preallocated memory.
 */
U_CAPI UBiDi * U_EXPORT2
ubidi_openSized(int32_t maxLength, int32_t maxRunCount, UErrorCode *pErrorCode);

/**
 * <code>ubidi_close()</code> must be called to free the memory
 * associated with a UBiDi object.<p>
 *
 * <strong>Important: </strong>
 * If a <code>UBiDi</code> object is the <quote>child</quote>
 * of another one (its <quote>parent</quote>), after calling
 * <code>ubidi_setLine()</code>, then the child object must
 * be destroyed (closed) or reused (by calling
 * <code>ubidi_setPara()</code> or <code>ubidi_setLine()</code>)
 * before the parent object.
 *
 * @param pBiDi is a <code>UBiDi</code> object.
 *
 * @see ubidi_setPara
 * @see ubidi_setLine
 */
U_CAPI void U_EXPORT2
ubidi_close(UBiDi *pBiDi);

/**
 * Perform the Unicode BiDi algorithm. It is defined in the
 * <a href="http://www.unicode.org/unicode/reports/tr9/">Unicode Technical Report 9</a>,
 * version 5,
 * also described in The Unicode Standard, Version 3.0 .<p>
 *
 * This function takes a single plain text paragraph with or without
 * externally specified embedding levels from <quote>styled</quote> text
 * and computes the left-right-directionality of each character.<p>
 *
 * If the entire paragraph consists of text of only one direction, then
 * the function may not perform all the steps described by the algorithm,
 * i.e., some levels may not be the same as if all steps were performed.
 * This is not relevant for unidirectional text.<br>
 * For example, in pure LTR text with numbers the numbers would get
 * a resolved level of 2 higher than the surrounding text according to
 * the algorithm. This implementation may set all resolved levels to
 * the same value in such a case.<p>
 *
 * The text must be externally split into separate paragraphs (rule P1).
 * Paragraph separators (B) should appear at most at the very end.
 *
 * @param pBiDi A <code>UBiDi</code> object allocated with <code>ubidi_open()</code>
 *        which will be set to contain the reordering information,
 *        especially the resolved levels for all the characters in <code>text</code>.
 *
 * @param text is a pointer to the single-paragraph text that the
 *        BiDi algorithm will be performed on
 *        (step (P1) of the algorithm is performed externally).
 *        <strong>The text must be (at least) <code>length</code> long.</strong>
 *
 * @param length is the length of the text; if <code>length==-1</code> then
 *        the text must be zero-terminated.
 *
 * @param paraLevel specifies the default level for the paragraph;
 *        it is typically 0 (LTR) or 1 (RTL).
 *        If the function shall determine the paragraph level from the text,
 *        then <code>paraLevel</code> can be set to
 *        either <code>UBIDI_DEFAULT_LTR</code>
 *        or <code>UBIDI_DEFAULT_RTL</code>;
 *        if there is no strongly typed character, then
 *        the desired default is used (0 for LTR or 1 for RTL).
 *        Any other value between 0 and <code>UBIDI_MAX_EXPLICIT_LEVEL</code> is also valid,
 *        with odd levels indicating RTL.
 *
 * @param embeddingLevels (in) may be used to preset the embedding and override levels,
 *        ignoring characters like LRE and PDF in the text.
 *        A level overrides the directional property of its corresponding
 *        (same index) character if the level has the
 *        <code>UBIDI_LEVEL_OVERRIDE</code> bit set.<p>
 *        Except for that bit, it must be
 *        <code>paraLevel&lt;=embeddingLevels[]&lt;=UBIDI_MAX_EXPLICIT_LEVEL</code>.<p>
 *        <strong>Caution: </strong>A copy of this pointer, not of the levels,
 *        will be stored in the <code>UBiDi</code> object;
 *        the <code>embeddingLevels</code> array must not be
 *        deallocated before the <code>UBiDi</code> structure is destroyed or reused,
 *        and the <code>embeddingLevels</code>
 *        should not be modified to avoid unexpected results on subsequent BiDi operations.
 *        However, the <code>ubidi_setPara()</code> and
 *        <code>ubidi_setLine()</code> functions may modify some or all of the levels.<p>
 *        After the <code>UBiDi</code> object is reused or destroyed, the caller
 *        must take care of the deallocation of the <code>embeddingLevels</code> array.<p>
 *        <strong>The <code>embeddingLevels</code> array must be
 *        at least <code>length</code> long.</strong>
 *
 * @param pErrorCode must be a valid pointer to an error code value,
 *        which must not indicate a failure before the function call.
 */
U_CAPI void U_EXPORT2
ubidi_setPara(UBiDi *pBiDi, const UChar *text, int32_t length,
              UBiDiLevel paraLevel, UBiDiLevel *embeddingLevels,
              UErrorCode *pErrorCode);

/**
 * <code>ubidi_getLine()</code> sets a <code>UBiDi</code> to
 * contain the reordering information, especially the resolved levels,
 * for all the characters in a line of text. This line of text is
 * specified by referring to a <code>UBiDi</code> object representing
 * this information for a paragraph of text, and by specifying
 * a range of indexes in this paragraph.<p>
 * In the new line object, the indexes will range from 0 to <code>limit-start</code>.<p>
 *
 * This is used after calling <code>ubidi_setPara()</code>
 * for a paragraph, and after line-breaking on that paragraph.
 * It is not necessary if the paragraph is treated as a single line.<p>
 *
 * After line-breaking, rules (L1) and (L2) for the treatment of
 * trailing WS and for reordering are performed on
 * a <code>UBiDi</code> object that represents a line.<p>
 *
 * <strong>Important: </strong><code>pLineBiDi</code> shares data with
 * <code>pParaBiDi</code>.
 * You must destroy or reuse <code>pLineBiDi</code> before <code>pParaBiDi</code>.
 * In other words, you must destroy or reuse the <code>UBiDi</code> object for a line
 * before the object for its parent paragraph.
 *
 * @param pParaBiDi is the parent paragraph object.
 *
 * @param start is the line's first index into the paragraph text.
 *
 * @param limit is just behind the line's last index into the paragraph text
 *        (its last index +1).<br>
 *        It must be <code>0&lt;=start&lt;=limit&lt;=</code>paragraph length.
 *
 * @param pLineBiDi is the object that will now represent a line of the paragraph.
 *
 * @param pErrorCode must be a valid pointer to an error code value,
 *        which must not indicate a failure before the function call.
 *
 * @see ubidi_setPara
 */
U_CAPI void U_EXPORT2
ubidi_setLine(const UBiDi *pParaBiDi,
              int32_t start, int32_t limit,
              UBiDi *pLineBiDi,
              UErrorCode *pErrorCode);

/**
 * Get the directionality of the text.
 *
 * @param pBiDi is the paragraph or line <code>UBiDi</code> object.
 *
 * @return A <code>UBIDI_XXX</code> value that indicates if the entire text
 *         represented by this object is unidirectional,
 *         and which direction, or if it is mixed-directional.
 *
 * @see UBiDiDirection
 */
U_CAPI UBiDiDirection U_EXPORT2
ubidi_getDirection(const UBiDi *pBiDi);

/**
 * Get the length of the text.
 *
 * @param pBiDi is the paragraph or line <code>UBiDi</code> object.
 *
 * @return The length of the text that the UBiDi object was created for.
 */
U_CAPI int32_t U_EXPORT2
ubidi_getLength(const UBiDi *pBiDi);

/**
 * Get the paragraph level of the text.
 *
 * @param pBiDi is the paragraph or line <code>UBiDi</code> object.
 *
 * @return The paragraph level.
 *
 * @see UBiDiLevel
 */
U_CAPI UBiDiLevel U_EXPORT2
ubidi_getParaLevel(const UBiDi *pBiDi);

/**
 * Get the level for one character.
 *
 * @param pBiDi is the paragraph or line <code>UBiDi</code> object.
 *
 * @param charIndex the index of a character.
 *
 * @return The level for the character at charIndex.
 *
 * @see UBiDiLevel
 */
U_CAPI UBiDiLevel U_EXPORT2
ubidi_getLevelAt(const UBiDi *pBiDi, int32_t charIndex);

/**
 * Get an array of levels for each character.<p>
 *
 * Note that this function may allocate memory under some
 * circumstances, unlike <code>ubidi_getLevelAt()</code>.
 *
 * @param pBiDi is the paragraph or line <code>UBiDi</code> object.
 *
 * @param pErrorCode must be a valid pointer to an error code value,
 *        which must not indicate a failure before the function call.
 *
 * @return The levels array for the text,
 *         or <code>NULL</code> if an error occurs.
 *
 * @see UBiDiLevel
 */
U_CAPI const UBiDiLevel * U_EXPORT2
ubidi_getLevels(UBiDi *pBiDi, UErrorCode *pErrorCode);

/**
 * Get a logical run.
 * This function returns information about a run and is used
 * to retrieve runs in logical order.<p>
 * This is especially useful for line-breaking on a paragraph.
 *
 * @param pBiDi is the paragraph or line <code>UBiDi</code> object.
 *
 * @param logicalStart is the first character of the run.
 *
 * @param pLogicalLimit will receive the limit of the run.
 *        The l-value that you point to here may be the
 *        same expression (variable) as the one for
 *        <code>logicalStart</code>.
 *        This pointer can be <code>NULL</code> if this
 *        value is not necessary.
 *
 * @param pLevel will receive the level of the run.
 *        This pointer can be <code>NULL</code> if this
 *        value is not necessary.
 */
U_CAPI void U_EXPORT2
ubidi_getLogicalRun(const UBiDi *pBiDi, int32_t logicalStart,
                    int32_t *pLogicalLimit, UBiDiLevel *pLevel);

/**
 * Get the number of runs.
 * This function may invoke the actual reordering on the
 * <code>UBiDi</code> object, after <code>ubidi_setPara()</code>
 * may have resolved only the levels of the text. Therefore,
 * <code>ubidi_countRuns()</code> may have to allocate memory,
 * and may fail doing so.
 *
 * @param pBiDi is the paragraph or line <code>UBiDi</code> object.
 *
 * @param pErrorCode must be a valid pointer to an error code value,
 *        which must not indicate a failure before the function call.
 *
 * @return The number of runs.
 */
U_CAPI int32_t U_EXPORT2
ubidi_countRuns(UBiDi *pBiDi, UErrorCode *pErrorCode);

/**
 * Get one run's logical start, length, and directionality,
 * which can be 0 for LTR or 1 for RTL.
 * In an RTL run, the character at the logical start is
 * visually on the right of the displayed run.
 * The length is the number of characters in the run.<p>
 * <code>ubidi_countRuns()</code> should be called
 * before the runs are retrieved.
 *
 * @param pBiDi is the paragraph or line <code>UBiDi</code> object.
 *
 * @param runIndex is the number of the run in visual order, in the
 *        range <code>[0..ubidi_countRuns(pBiDi)-1]</code>.
 *
 * @param pLogicalStart is the first logical character index in the text.
 *        The pointer may be <code>NULL</code> if this index is not needed.
 *
 * @param pLength is the number of characters (at least one) in the run.
 *        The pointer may be <code>NULL</code> if this is not needed.
 *
 * @return the directionality of the run,
 *         <code>UBIDI_LTR==0</code> or <code>UBIDI_RTL==1</code>,
 *         never <code>UBIDI_MIXED</code>.
 *
 * @see ubidi_countRuns
 *
 * Example:
 * <pre>
 *&nbsp; int32_t i, count=ubidi_countRuns(pBiDi),
 *&nbsp;         logicalStart, visualIndex=0, length;
 *&nbsp; for(i=0; i&lt;count; ++i) {
 *&nbsp;     if(UBIDI_LTR==ubidi_getVisualRun(pBiDi, i, &logicalStart, &length)) {
 *&nbsp;         do { // LTR
 *&nbsp;             show_char(text[logicalStart++], visualIndex++);
 *&nbsp;         } while(--length>0);
 *&nbsp;     } else {
 *&nbsp;         logicalStart+=length;  // logicalLimit
 *&nbsp;         do { // RTL
 *&nbsp;             show_char(text[--logicalStart], visualIndex++);
 *&nbsp;         } while(--length>0);
 *&nbsp;     }
 *&nbsp; }
 * </pre>
 *
 * Note that in right-to-left runs, code like this places
 * modifier letters before base characters and second surrogates
 * before first ones.
 */
U_CAPI UBiDiDirection U_EXPORT2
ubidi_getVisualRun(UBiDi *pBiDi, int32_t runIndex,
                   int32_t *pLogicalStart, int32_t *pLength);

/**
 * Get the visual position from a logical text position.
 * If such a mapping is used many times on the same
 * <code>UBiDi</code> object, then calling
 * <code>ubidi_getLogicalMap()</code> is more efficient.<p>
 *
 * Note that in right-to-left runs, this mapping places
 * modifier letters before base characters and second surrogates
 * before first ones.
 *
 * @param pBiDi is the paragraph or line <code>UBiDi</code> object.
 *
 * @param logicalIndex is the index of a character in the text.
 *
 * @param pErrorCode must be a valid pointer to an error code value,
 *        which must not indicate a failure before the function call.
 *
 * @return The visual position of this character.
 *
 * @see ubidi_getLogicalMap
 * @see ubidi_getLogicalIndex
 */
U_CAPI int32_t U_EXPORT2
ubidi_getVisualIndex(UBiDi *pBiDi, int32_t logicalIndex, UErrorCode *pErrorCode);

/**
 * Get the logical text position from a visual position.
 * If such a mapping is used many times on the same
 * <code>UBiDi</code> object, then calling
 * <code>ubidi_getVisualMap()</code> is more efficient.<p>
 *
 * This is the inverse function to <code>ubidi_getVisualIndex()</code>.
 *
 * @param pBiDi is the paragraph or line <code>UBiDi</code> object.
 *
 * @param visualIndex is the visual position of a character.
 *
 * @param pErrorCode must be a valid pointer to an error code value,
 *        which must not indicate a failure before the function call.
 *
 * @return The index of this character in the text.
 *
 * @see ubidi_getVisualMap
 * @see ubidi_getVisualIndex
 */
U_CAPI int32_t U_EXPORT2
ubidi_getLogicalIndex(UBiDi *pBiDi, int32_t visualIndex, UErrorCode *pErrorCode);

/**
 * Get a logical-to-visual index map (array) for the characters in the UBiDi
 * (paragraph or line) object.
 *
 * @param pBiDi is the paragraph or line <code>UBiDi</code> object.
 *
 * @param indexMap is a pointer to an array of <code>ubidi_getLength()</code>
 *        indexes which will reflect the reordering of the characters.
 *        The array does not need to be initialized.<p>
 *        The index map will result in <code>indexMap[logicalIndex]==visualIndex</code>.<p>
 *
 * @param pErrorCode must be a valid pointer to an error code value,
 *        which must not indicate a failure before the function call.
 *
 * @see ubidi_getVisualMap
 * @see ubidi_getVisualIndex
 */
U_CAPI void U_EXPORT2
ubidi_getLogicalMap(UBiDi *pBiDi, int32_t *indexMap, UErrorCode *pErrorCode);

/**
 * Get a visual-to-logical index map (array) for the characters in the UBiDi
 * (paragraph or line) object.
 *
 * @param pBiDi is the paragraph or line <code>UBiDi</code> object.
 *
 * @param indexMap is a pointer to an array of <code>ubidi_getLength()</code>
 *        indexes which will reflect the reordering of the characters.
 *        The array does not need to be initialized.<p>
 *        The index map will result in <code>indexMap[visualIndex]==logicalIndex</code>.<p>
 *
 * @param pErrorCode must be a valid pointer to an error code value,
 *        which must not indicate a failure before the function call.
 *
 * @see ubidi_getLogicalMap
 * @see ubidi_getLogicalIndex
 */
U_CAPI void U_EXPORT2
ubidi_getVisualMap(UBiDi *pBiDi, int32_t *indexMap, UErrorCode *pErrorCode);

/**
 * This is a convenience function that does not use a UBiDi object.
 * It is intended to be used for when an application has determined the levels
 * of objects (character sequences) and just needs to have them reordered (L2).
 * This is equivalent to using <code>ubidi_getLogicalMap</code> on a
 * <code>UBiDi</code> object.
 *
 * @param levels is an array with <code>length</code> levels that have been determined by
 *        the application.
 *
 * @param length is the number of levels in the array, or, semantically,
 *        the number of objects to be reordered.
 *        It must be <code>length&gt;0</code>.
 *
 * @param indexMap is a pointer to an array of <code>length</code>
 *        indexes which will reflect the reordering of the characters.
 *        The array does not need to be initialized.<p>
 *        The index map will result in <code>indexMap[logicalIndex]==visualIndex</code>.
 */
U_CAPI void U_EXPORT2
ubidi_reorderLogical(const UBiDiLevel *levels, int32_t length, int32_t *indexMap);

/**
 * This is a convenience function that does not use a UBiDi object.
 * It is intended to be used for when an application has determined the levels
 * of objects (character sequences) and just needs to have them reordered (L2).
 * This is equivalent to using <code>ubidi_getVisualMap</code> on a
 * <code>UBiDi</code> object.
 *
 * @param levels is an array with <code>length</code> levels that have been determined by
 *        the application.
 *
 * @param length is the number of levels in the array, or, semantically,
 *        the number of objects to be reordered.
 *        It must be <code>length&gt;0</code>.
 *
 * @param indexMap is a pointer to an array of <code>length</code>
 *        indexes which will reflect the reordering of the characters.
 *        The array does not need to be initialized.<p>
 *        The index map will result in <code>indexMap[visualIndex]==logicalIndex</code>.
 */
U_CAPI void U_EXPORT2
ubidi_reorderVisual(const UBiDiLevel *levels, int32_t length, int32_t *indexMap);

/**
 * Invert an index map.
 * The one-to-one index mapping of the first map is inverted and written to
 * the second one.
 *
 * @param srcMap is an array with <code>length</code> indexes
 *        which define the original mapping.
 *
 * @param destMap is an array with <code>length</code> indexes
 *        which will be filled with the inverse mapping.
 *
 * @param length is the length of each array.
 */
U_CAPI void U_EXPORT2
ubidi_invertMap(const int32_t *srcMap, int32_t *destMap, int32_t length);

/**
 * @name Sample code for the ICU BiDi API
 *
 * <h2>Rendering a paragraph with the ICU BiDi API</h2>
 *
 * This is (hypothetical) sample code that illustrates
 * how the ICU BiDi API could be used to render a paragraph of text.
 * Rendering code depends highly on the graphics system,
 * therefore this sample code must make a lot of assumptions,
 * which may or may not match any existing graphics system's properties.
 *
 * <p>The basic assumptions are:</p>
 * <ul>
 * <li>Rendering is done from left to right on a horizontal line.</li>
 * <li>A run of single-style, unidirectional text can be rendered at once.</li>
 * <li>Such a run of text is passed to the graphics system with
 *     characters (code units) in logical order.</li>
 * <li>The line-breaking algorithm is very complicated
 *     and Locale-dependent -
 *     and therefore its implementation omitted from this sample code.</li>
 * </ul>
 *
 * <pre>
 *&nbsp; #include "ubidi.h"
 *&nbsp;
 *&nbsp; typedef enum {
 *&nbsp;     styleNormal=0, styleSelected=1,
 *&nbsp;     styleBold=2, styleItalics=4,
 *&nbsp;     styleSuper=8, styleSub=16
 *&nbsp; } Style;
 *&nbsp;
 *&nbsp; typedef struct { int32_t limit; Style style; } StyleRun;
 *&nbsp;
 *&nbsp; int getTextWidth(const UChar *text, int32_t start, int32_t limit,
 *&nbsp;                  const StyleRun *styleRuns, int styleRunCount);
 *&nbsp;
 *&nbsp; // set *pLimit and *pStyleRunLimit for a line
 *&nbsp; // from text[start] and from styleRuns[styleRunStart]
 *&nbsp; // using ubidi_getLogicalRun(para, ...)
 *&nbsp; void getLineBreak(const UChar *text, int32_t start, int32_t *pLimit,
 *&nbsp;                   UBiDi *para,
 *&nbsp;                   const StyleRun *styleRuns, int styleRunStart, int *pStyleRunLimit,
 *&nbsp;                   int *pLineWidth);
 *&nbsp;
 *&nbsp; // render runs on a line sequentially, always from left to right
 *&nbsp;
 *&nbsp; // prepare rendering a new line
 *&nbsp; void startLine(UBiDiDirection textDirection, int lineWidth);
 *&nbsp;
 *&nbsp; // render a run of text and advance to the right by the run width
 *&nbsp; // the text[start..limit-1] is always in logical order
 *&nbsp; void renderRun(const UChar *text, int32_t start, int32_t limit,
 *&nbsp;                UBiDiDirection textDirection, Style style);
 *&nbsp;
 *&nbsp; // We could compute a cross-product
 *&nbsp; // from the style runs with the directional runs
 *&nbsp; // and then reorder it.
 *&nbsp; // Instead, here we iterate over each run type
 *&nbsp; // and render the intersections -
 *&nbsp; // with shortcuts in simple (and common) cases.
 *&nbsp; // renderParagraph() is the main function.
 *&nbsp;
 *&nbsp; // render a directional run with
 *&nbsp; // (possibly) multiple style runs intersecting with it
 *&nbsp; void renderDirectionalRun(const UChar *text,
 *&nbsp;                           int32_t start, int32_t limit,
 *&nbsp;                           UBiDiDirection direction,
 *&nbsp;                           const StyleRun *styleRuns, int styleRunCount) {
 *&nbsp;     int i;
 *&nbsp;
 *&nbsp;     // iterate over style runs
 *&nbsp;     if(direction==UBIDI_LTR) {
 *&nbsp;         int styleLimit;
 *&nbsp;
 *&nbsp;         for(i=0; i&lt;styleRunCount; ++i) {
 *&nbsp;             styleLimit=styleRun[i].limit;
 *&nbsp;             if(start&lt;styleLimit) {
 *&nbsp;                 if(styleLimit>limit) { styleLimit=limit; }
 *&nbsp;                 renderRun(text, start, styleLimit,
 *&nbsp;                           direction, styleRun[i].style);
 *&nbsp;                 if(styleLimit==limit) { break; }
 *&nbsp;                 start=styleLimit;
 *&nbsp;             }
 *&nbsp;         }
 *&nbsp;     } else {
 *&nbsp;         int styleStart;
 *&nbsp;
 *&nbsp;         for(i=styleRunCount-1; i>=0; --i) {
 *&nbsp;             if(i>0) {
 *&nbsp;                 styleStart=styleRun[i-1].limit;
 *&nbsp;             } else {
 *&nbsp;                 styleStart=0;
 *&nbsp;             }
 *&nbsp;             if(limit>=styleStart) {
 *&nbsp;                 if(styleStart&lt;start) { styleStart=start; }
 *&nbsp;                 renderRun(text, styleStart, limit,
 *&nbsp;                           direction, styleRun[i].style);
 *&nbsp;                 if(styleStart==start) { break; }
 *&nbsp;                 limit=styleStart;
 *&nbsp;             }
 *&nbsp;         }
 *&nbsp;     }
 *&nbsp; }
 *&nbsp;
 *&nbsp; // the line object represents text[start..limit-1]
 *&nbsp; void renderLine(UBiDi *line, const UChar *text,
 *&nbsp;                 int32_t start, int32_t limit,
 *&nbsp;                 const StyleRun *styleRuns, int styleRunCount) {
 *&nbsp;     UBiDiDirection direction=ubidi_getDirection(line);
 *&nbsp;     if(direction!=UBIDI_MIXED) {
 *&nbsp;         // unidirectional
 *&nbsp;         if(styleRunCount&lt;=1) {
 *&nbsp;             renderRun(text, start, limit, direction, styleRuns[0].style);
 *&nbsp;         } else {
 *&nbsp;             renderDirectionalRun(text, start, limit,
 *&nbsp;                                  direction, styleRuns, styleRunCount);
 *&nbsp;         }
 *&nbsp;     } else {
 *&nbsp;         // mixed-directional
 *&nbsp;         int32_t count, i, length;
 *&nbsp;         UBiDiLevel level;
 *&nbsp;
 *&nbsp;         count=ubidi_countRuns(para, pErrorCode);
 *&nbsp;         if(U_SUCCESS(*pErrorCode)) {
 *&nbsp;             if(styleRunCount&lt;=1) {
 *&nbsp;                 Style style=styleRuns[0].style;
 *&nbsp;
 *&nbsp;                 // iterate over directional runs
 *&nbsp;                 for(i=0; i&lt;count; ++i) {
 *&nbsp;                     direction=ubidi_getVisualRun(para, i, &start, &length);
 *&nbsp;                     renderRun(text, start, start+length, direction, style);
 *&nbsp;                 }
 *&nbsp;             } else {
 *&nbsp;                 int32_t j;
 *&nbsp;
 *&nbsp;                 // iterate over both directional and style runs
 *&nbsp;                 for(i=0; i&lt;count; ++i) {
 *&nbsp;                     direction=ubidi_getVisualRun(line, i, &start, &length);
 *&nbsp;                     renderDirectionalRun(text, start, start+length,
 *&nbsp;                                          direction, styleRuns, styleRunCount);
 *&nbsp;                 }
 *&nbsp;             }
 *&nbsp;         }
 *&nbsp;     }
 *&nbsp; }
 *&nbsp;
 *&nbsp; void renderParagraph(const UChar *text, int32_t length,
 *&nbsp;                      UBiDiDirection textDirection,
 *&nbsp;                      const StyleRun *styleRuns, int styleRunCount,
 *&nbsp;                      int lineWidth,
 *&nbsp;                      UErrorCode *pErrorCode) {
 *&nbsp;     UBiDi *para;
 *&nbsp;
 *&nbsp;     if(pErrorCode==NULL || U_FAILURE(*pErrorCode) || length&lt;=0) {
 *&nbsp;         return;
 *&nbsp;     }
 *&nbsp;
 *&nbsp;     para=ubidi_openSized(length, 0, pErrorCode);
 *&nbsp;     if(para==NULL) { return; }
 *&nbsp;
 *&nbsp;     ubidi_setPara(para, text, length,
 *&nbsp;                   textDirection ? UBIDI_DEFAULT_RTL : UBIDI_DEFAULT_LTR,
 *&nbsp;                   NULL, pErrorCode);
 *&nbsp;     if(U_SUCCESS(*pErrorCode)) {
 *&nbsp;         UBiDiLevel paraLevel=1&ubidi_getParaLevel(para);
 *&nbsp;         StyleRun styleRun={ length, styleNormal };
 *&nbsp;         int width;
 *&nbsp;
 *&nbsp;         if(styleRuns==NULL || styleRunCount&lt;=0) {
 *&nbsp;             styleRunCount=1;
 *&nbsp;             styleRuns=&styleRun;
 *&nbsp;         }
 *&nbsp;
 *&nbsp;         // assume styleRuns[styleRunCount-1].limit>=length
 *&nbsp;
 *&nbsp;         width=getTextWidth(text, 0, length, styleRuns, styleRunCount);
 *&nbsp;         if(width&lt;=lineWidth) {
 *&nbsp;             // everything fits onto one line
 *&nbsp;
 *&nbsp;             // prepare rendering a new line from either left or right
 *&nbsp;             startLine(paraLevel, width);
 *&nbsp;
 *&nbsp;             renderLine(para, text, 0, length,
 *&nbsp;                        styleRuns, styleRunCount);
 *&nbsp;         } else {
 *&nbsp;             UBiDi *line;
 *&nbsp;
 *&nbsp;             // we need to render several lines
 *&nbsp;             line=ubidi_openSized(length, 0, pErrorCode);
 *&nbsp;             if(line!=NULL) {
 *&nbsp;                 int32_t start=0, limit;
 *&nbsp;                 int styleRunStart=0, styleRunLimit;
 *&nbsp;
 *&nbsp;                 for(;;) {
 *&nbsp;                     limit=length;
 *&nbsp;                     styleRunLimit=styleRunCount;
 *&nbsp;                     getLineBreak(text, start, &limit, para,
 *&nbsp;                                  styleRuns, styleRunStart, &styleRunLimit,
 *&nbsp;                                  &width);
 *&nbsp;                     ubidi_setLine(para, start, limit, line, pErrorCode);
 *&nbsp;                     if(U_SUCCESS(*pErrorCode)) {
 *&nbsp;                         // prepare rendering a new line
 *&nbsp;                         // from either left or right
 *&nbsp;                         startLine(paraLevel, width);
 *&nbsp;
 *&nbsp;                         renderLine(line, text, start, limit,
 *&nbsp;                                    styleRuns+styleRunStart,
 *&nbsp;                                    styleRunLimit-styleRunStart);
 *&nbsp;                     }
 *&nbsp;                     if(limit==length) { break; }
 *&nbsp;                     start=limit;
 *&nbsp;                     styleRunStart=styleRunLimit-1;
 *&nbsp;                     if(start>=styleRuns[styleRunStart].limit) {
 *&nbsp;                         ++styleRunStart;
 *&nbsp;                     }
 *&nbsp;                 }
 *&nbsp;
 *&nbsp;                 ubidi_close(line);
 *&nbsp;             }
 *&nbsp;         }
 *&nbsp;     }
 *&nbsp;
 *&nbsp;     ubidi_close(para);
 *&nbsp; }
 * </pre>
 */
BIDI_SAMPLE_CODE
/*@{*/
/*@}*/

/*@}*/

#endif
