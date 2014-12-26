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

import java.text.CharacterIterator;
import java.text.Normalizer;

/**
 * Unicode Normalization
 *
 * <h2>Unicode normalization API</h2>
 *
 * <code>normalize</code> transforms Unicode text into an equivalent composed or
 * decomposed form, allowing for easier sorting and searching of text.
 * <code>normalize</code> supports the standard normalization forms described in
 * <a href="http://www.unicode.org/unicode/reports/tr15/" target="unicode">
 * Unicode Standard Annex #15 &mdash; Unicode Normalization Forms</a>.
 *
 * Characters with accents or other adornments can be encoded in
 * several different ways in Unicode.  For example, take the character A-acute.
 * In Unicode, this can be encoded as a single character (the
 * "composed" form):
 *
 * <p>
 *      00C1    LATIN CAPITAL LETTER A WITH ACUTE
 * </p>
 *
 * or as two separate characters (the "decomposed" form):
 *
 * <p>
 *      0041    LATIN CAPITAL LETTER A
 *      0301    COMBINING ACUTE ACCENT
 * </p>
 *
 * To a user of your program, however, both of these sequences should be
 * treated as the same "user-level" character "A with acute accent".  When you
 * are searching or comparing text, you must ensure that these two sequences are
 * treated equivalently.  In addition, you must handle characters with more than
 * one accent.  Sometimes the order of a character's combining accents is
 * significant, while in other cases accent sequences in different orders are
 * really equivalent.
 *
 * Similarly, the string "ffi" can be encoded as three separate letters:
 *
 * <p>
 *      0066    LATIN SMALL LETTER F
 *      0066    LATIN SMALL LETTER F
 *      0069    LATIN SMALL LETTER I
 * </p>
 *
 * or as the single character
 *
 * <p>
 *      FB03    LATIN SMALL LIGATURE FFI
 * </p>
 *
 * The ffi ligature is not a distinct semantic character, and strictly speaking
 * it shouldn't be in Unicode at all, but it was included for compatibility
 * with existing character sets that already provided it.  The Unicode standard
 * identifies such characters by giving them "compatibility" decompositions
 * into the corresponding semantic characters.  When sorting and searching, you
 * will often want to use these mappings.
 *
 * <code>normalize</code> helps solve these problems by transforming text into
 * the canonical composed and decomposed forms as shown in the first example
 * above. In addition, you can have it perform compatibility decompositions so
 * that you can treat compatibility characters the same as their equivalents.
 * Finally, <code>normalize</code> rearranges accents into the proper canonical
 * order, so that you do not have to worry about accent rearrangement on your
 * own.
 *
 * Form FCD, "Fast C or D", is also designed for collation.
 * It allows to work on strings that are not necessarily normalized
 * with an algorithm (like in collation) that works under "canonical closure",
 * i.e., it treats precomposed characters and their decomposed equivalents the
 * same.
 *
 * It is not a normalization form because it does not provide for uniqueness of
 * representation. Multiple strings may be canonically equivalent (their NFDs
 * are identical) and may all conform to FCD without being identical themselves.
 *
 * The form is defined such that the "raw decomposition", the recursive
 * canonical decomposition of each character, results in a string that is
 * canonically ordered. This means that precomposed characters are allowed for
 * as long as their decompositions do not need canonical reordering.
 *
 * Its advantage for a process like collation is that all NFD and most NFC texts
 * - and many unnormalized texts - already conform to FCD and do not need to be
 * normalized (NFD) for such a process. The FCD quick check will return YES for
 * most strings in practice.
 *
 * normalize(FCD) may be implemented with NFD.
 *
 * For more details on FCD see the collation design document:
 * http://source.icu-project.org/repos/icu/icuhtml/trunk/design/collation/ICU_collation_design.htm
 *
 * ICU collation performs either NFD or FCD normalization automatically if
 * normalization is turned on for the collator object. Beyond collation and
 * string search, normalized strings may be useful for string equivalence
 * comparisons, transliteration/transcription, unique representations, etc.
 *
 * The W3C generally recommends to exchange texts in NFC.
 * Note also that most legacy character encodings use only precomposed forms and
 * often do not encode any combining marks by themselves. For conversion to such
 * character encodings the Unicode text needs to be normalized to NFC.
 * For more usage examples, see the Unicode Standard Annex.
 * @stable ICU 2.8
 */

public final class NormalizerBase implements Cloneable {

    //-------------------------------------------------------------------------
    // Private data
    //-------------------------------------------------------------------------
    private char[] buffer = new char[100];
    private int bufferStart = 0;
    private int bufferPos   = 0;
    private int bufferLimit = 0;

    // The input text and our position in it
    private UCharacterIterator  text;
    private Mode                mode = NFC;
    private int                 options = 0;
    private int                 currentIndex;
    private int                 nextIndex;

    /**
     * Options bit set value to select Unicode 3.2 normalization
     * (except NormalizationCorrections).
     * At most one Unicode version can be selected at a time.
     * @stable ICU 2.6
     */
    public static final int UNICODE_3_2=0x20;

    /**
     * Constant indicating that the end of the iteration has been reached.
     * This is guaranteed to have the same value as {@link UCharacterIterator#DONE}.
     * @stable ICU 2.8
     */
    public static final int DONE = UCharacterIterator.DONE;

    /**
     * Constants for normalization modes.
     * @stable ICU 2.8
     */
    public static class Mode {
        private int modeValue;
        private Mode(int value) {
            modeValue = value;
        }

        /**
         * This method is used for method dispatch
         * @stable ICU 2.6
         */
        protected int normalize(char[] src, int srcStart, int srcLimit,
                                char[] dest,int destStart,int destLimit,
                                UnicodeSet nx) {
            int srcLen = (srcLimit - srcStart);
            int destLen = (destLimit - destStart);
            if( srcLen > destLen ) {
                return srcLen;
            }
            System.arraycopy(src,srcStart,dest,destStart,srcLen);
            return srcLen;
        }

        /**
         * This method is used for method dispatch
         * @stable ICU 2.6
         */
        protected int normalize(char[] src, int srcStart, int srcLimit,
                                char[] dest,int destStart,int destLimit,
                                int options) {
            return normalize(   src, srcStart, srcLimit,
                                dest,destStart,destLimit,
                                NormalizerImpl.getNX(options)
                                );
        }

        /**
         * This method is used for method dispatch
         * @stable ICU 2.6
         */
        protected String normalize(String src, int options) {
            return src;
        }

        /**
         * This method is used for method dispatch
         * @stable ICU 2.8
         */
        protected int getMinC() {
            return -1;
        }

        /**
         * This method is used for method dispatch
         * @stable ICU 2.8
         */
        protected int getMask() {
            return -1;
        }

        /**
         * This method is used for method dispatch
         * @stable ICU 2.8
         */
        protected IsPrevBoundary getPrevBoundary() {
            return null;
        }

        /**
         * This method is used for method dispatch
         * @stable ICU 2.8
         */
        protected IsNextBoundary getNextBoundary() {
            return null;
        }

        /**
         * This method is used for method dispatch
         * @stable ICU 2.6
         */
        protected QuickCheckResult quickCheck(char[] src,int start, int limit,
                                              boolean allowMaybe,UnicodeSet nx) {
            if(allowMaybe) {
                return MAYBE;
            }
            return NO;
        }

        /**
         * This method is used for method dispatch
         * @stable ICU 2.8
         */
        protected boolean isNFSkippable(int c) {
            return true;
        }
    }

    /**
     * No decomposition/composition.
     * @stable ICU 2.8
     */
    public static final Mode NONE = new Mode(1);

    /**
     * Canonical decomposition.
     * @stable ICU 2.8
     */
    public static final Mode NFD = new NFDMode(2);

    private static final class NFDMode extends Mode {
        private NFDMode(int value) {
            super(value);
        }

        protected int normalize(char[] src, int srcStart, int srcLimit,
                                char[] dest,int destStart,int destLimit,
                                UnicodeSet nx) {
            int[] trailCC = new int[1];
            return NormalizerImpl.decompose(src,  srcStart,srcLimit,
                                            dest, destStart,destLimit,
                                            false, trailCC,nx);
        }

        protected String normalize( String src, int options) {
            return decompose(src,false,options);
        }

        protected int getMinC() {
            return NormalizerImpl.MIN_WITH_LEAD_CC;
        }

        protected IsPrevBoundary getPrevBoundary() {
            return new IsPrevNFDSafe();
        }

        protected IsNextBoundary getNextBoundary() {
            return new IsNextNFDSafe();
        }

        protected int getMask() {
            return (NormalizerImpl.CC_MASK|NormalizerImpl.QC_NFD);
        }

        protected QuickCheckResult quickCheck(char[] src,int start,
                                              int limit,boolean allowMaybe,
                                              UnicodeSet nx) {
            return NormalizerImpl.quickCheck(
                                             src, start,limit,
                                             NormalizerImpl.getFromIndexesArr(
                                                                              NormalizerImpl.INDEX_MIN_NFD_NO_MAYBE
                                                                              ),
                                             NormalizerImpl.QC_NFD,
                                             0,
                                             allowMaybe,
                                             nx
                                             );
        }

        protected boolean isNFSkippable(int c) {
            return NormalizerImpl.isNFSkippable(c,this,
                                                (NormalizerImpl.CC_MASK|NormalizerImpl.QC_NFD)
                                                );
        }
    }

    /**
     * Compatibility decomposition.
     * @stable ICU 2.8
     */
    public static final Mode NFKD = new NFKDMode(3);

    private static final class NFKDMode extends Mode {
        private NFKDMode(int value) {
            super(value);
        }

        protected int normalize(char[] src, int srcStart, int srcLimit,
                                char[] dest,int destStart,int destLimit,
                                UnicodeSet nx) {
            int[] trailCC = new int[1];
            return NormalizerImpl.decompose(src,  srcStart,srcLimit,
                                            dest, destStart,destLimit,
                                            true, trailCC, nx);
        }

        protected String normalize( String src, int options) {
            return decompose(src,true,options);
        }

        protected int getMinC() {
            return NormalizerImpl.MIN_WITH_LEAD_CC;
        }

        protected IsPrevBoundary getPrevBoundary() {
            return new IsPrevNFDSafe();
        }

        protected IsNextBoundary getNextBoundary() {
            return new IsNextNFDSafe();
        }

        protected int getMask() {
            return (NormalizerImpl.CC_MASK|NormalizerImpl.QC_NFKD);
        }

        protected QuickCheckResult quickCheck(char[] src,int start,
                                              int limit,boolean allowMaybe,
                                              UnicodeSet nx) {
            return NormalizerImpl.quickCheck(
                                             src,start,limit,
                                             NormalizerImpl.getFromIndexesArr(
                                                                              NormalizerImpl.INDEX_MIN_NFKD_NO_MAYBE
                                                                              ),
                                             NormalizerImpl.QC_NFKD,
                                             NormalizerImpl.OPTIONS_COMPAT,
                                             allowMaybe,
                                             nx
                                             );
        }

        protected boolean isNFSkippable(int c) {
            return NormalizerImpl.isNFSkippable(c, this,
                                                (NormalizerImpl.CC_MASK|NormalizerImpl.QC_NFKD)
                                                );
        }
    }

    /**
     * Canonical decomposition followed by canonical composition.
     * @stable ICU 2.8
     */
    public static final Mode NFC = new NFCMode(4);

    private static final class NFCMode extends Mode{
        private NFCMode(int value) {
            super(value);
        }
        protected int normalize(char[] src, int srcStart, int srcLimit,
                                char[] dest,int destStart,int destLimit,
                                UnicodeSet nx) {
            return NormalizerImpl.compose( src, srcStart, srcLimit,
                                           dest,destStart,destLimit,
                                           0, nx);
        }

        protected String normalize( String src, int options) {
            return compose(src, false, options);
        }

        protected int getMinC() {
            return NormalizerImpl.getFromIndexesArr(
                                                    NormalizerImpl.INDEX_MIN_NFC_NO_MAYBE
                                                    );
        }
        protected IsPrevBoundary getPrevBoundary() {
            return new IsPrevTrueStarter();
        }
        protected IsNextBoundary getNextBoundary() {
            return new IsNextTrueStarter();
        }
        protected int getMask() {
            return (NormalizerImpl.CC_MASK|NormalizerImpl.QC_NFC);
        }
        protected QuickCheckResult quickCheck(char[] src,int start,
                                              int limit,boolean allowMaybe,
                                              UnicodeSet nx) {
            return NormalizerImpl.quickCheck(
                                             src,start,limit,
                                             NormalizerImpl.getFromIndexesArr(
                                                                              NormalizerImpl.INDEX_MIN_NFC_NO_MAYBE
                                                                              ),
                                             NormalizerImpl.QC_NFC,
                                             0,
                                             allowMaybe,
                                             nx
                                             );
        }
        protected boolean isNFSkippable(int c) {
            return NormalizerImpl.isNFSkippable(c,this,
                                                ( NormalizerImpl.CC_MASK|NormalizerImpl.COMBINES_ANY|
                                                  (NormalizerImpl.QC_NFC & NormalizerImpl.QC_ANY_NO)
                                                  )
                                                );
        }
    };

    /**
     * Compatibility decomposition followed by canonical composition.
     * @stable ICU 2.8
     */
    public static final Mode NFKC =new NFKCMode(5);

    private static final class NFKCMode extends Mode{
        private NFKCMode(int value) {
            super(value);
        }
        protected int normalize(char[] src, int srcStart, int srcLimit,
                                char[] dest,int destStart,int destLimit,
                                UnicodeSet nx) {
            return NormalizerImpl.compose(src,  srcStart,srcLimit,
                                          dest, destStart,destLimit,
                                          NormalizerImpl.OPTIONS_COMPAT, nx);
        }

        protected String normalize( String src, int options) {
            return compose(src, true, options);
        }
        protected int getMinC() {
            return NormalizerImpl.getFromIndexesArr(
                                                    NormalizerImpl.INDEX_MIN_NFKC_NO_MAYBE
                                                    );
        }
        protected IsPrevBoundary getPrevBoundary() {
            return new IsPrevTrueStarter();
        }
        protected IsNextBoundary getNextBoundary() {
            return new IsNextTrueStarter();
        }
        protected int getMask() {
            return (NormalizerImpl.CC_MASK|NormalizerImpl.QC_NFKC);
        }
        protected QuickCheckResult quickCheck(char[] src,int start,
                                              int limit,boolean allowMaybe,
                                              UnicodeSet nx) {
            return NormalizerImpl.quickCheck(
                                             src,start,limit,
                                             NormalizerImpl.getFromIndexesArr(
                                                                              NormalizerImpl.INDEX_MIN_NFKC_NO_MAYBE
                                                                              ),
                                             NormalizerImpl.QC_NFKC,
                                             NormalizerImpl.OPTIONS_COMPAT,
                                             allowMaybe,
                                             nx
                                             );
        }
        protected boolean isNFSkippable(int c) {
            return NormalizerImpl.isNFSkippable(c, this,
                                                ( NormalizerImpl.CC_MASK|NormalizerImpl.COMBINES_ANY|
                                                  (NormalizerImpl.QC_NFKC & NormalizerImpl.QC_ANY_NO)
                                                  )
                                                );
        }
    };

    /**
     * Result values for quickCheck().
     * For details see Unicode Technical Report 15.
     * @stable ICU 2.8
     */
    public static final class QuickCheckResult{
        private int resultValue;
        private QuickCheckResult(int value) {
            resultValue=value;
        }
    }
    /**
     * Indicates that string is not in the normalized format
     * @stable ICU 2.8
     */
    public static final QuickCheckResult NO = new QuickCheckResult(0);

    /**
     * Indicates that string is in the normalized format
     * @stable ICU 2.8
     */
    public static final QuickCheckResult YES = new QuickCheckResult(1);

    /**
     * Indicates it cannot be determined if string is in the normalized
     * format without further thorough checks.
     * @stable ICU 2.8
     */
    public static final QuickCheckResult MAYBE = new QuickCheckResult(2);

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    /**
     * Creates a new <tt>Normalizer</tt> object for iterating over the
     * normalized form of a given string.
     * <p>
     * The <tt>options</tt> parameter specifies which optional
     * <tt>Normalizer</tt> features are to be enabled for this object.
     * <p>
     * @param str  The string to be normalized.  The normalization
     *              will start at the beginning of the string.
     *
     * @param mode The normalization mode.
     *
     * @param opt Any optional features to be enabled.
     *            Currently the only available option is {@link #UNICODE_3_2}.
     *            If you want the default behavior corresponding to one of the
     *            standard Unicode Normalization Forms, use 0 for this argument.
     * @stable ICU 2.6
     */
    public NormalizerBase(String str, Mode mode, int opt) {
        this.text = UCharacterIterator.getInstance(str);
        this.mode = mode;
        this.options=opt;
    }

    /**
     * Creates a new <tt>Normalizer</tt> object for iterating over the
     * normalized form of the given text.
     * <p>
     * @param iter  The input text to be normalized.  The normalization
     *              will start at the beginning of the string.
     *
     * @param mode  The normalization mode.
     */
    public NormalizerBase(CharacterIterator iter, Mode mode) {
          this(iter, mode, UNICODE_LATEST);
    }

    /**
     * Creates a new <tt>Normalizer</tt> object for iterating over the
     * normalized form of the given text.
     * <p>
     * @param iter  The input text to be normalized.  The normalization
     *              will start at the beginning of the string.
     *
     * @param mode  The normalization mode.
     *
     * @param opt Any optional features to be enabled.
     *            Currently the only available option is {@link #UNICODE_3_2}.
     *            If you want the default behavior corresponding to one of the
     *            standard Unicode Normalization Forms, use 0 for this argument.
     * @stable ICU 2.6
     */
    public NormalizerBase(CharacterIterator iter, Mode mode, int opt) {
        this.text = UCharacterIterator.getInstance(
                                                   (CharacterIterator)iter.clone()
                                                   );
        this.mode = mode;
        this.options = opt;
    }

    /**
     * Clones this <tt>Normalizer</tt> object.  All properties of this
     * object are duplicated in the new object, including the cloning of any
     * {@link CharacterIterator} that was passed in to the constructor
     * or to {@link #setText(CharacterIterator) setText}.
     * However, the text storage underlying
     * the <tt>CharacterIterator</tt> is not duplicated unless the
     * iterator's <tt>clone</tt> method does so.
     * @stable ICU 2.8
     */
    public Object clone() {
        try {
            NormalizerBase copy = (NormalizerBase) super.clone();
            copy.text = (UCharacterIterator) text.clone();
            //clone the internal buffer
            if (buffer != null) {
                copy.buffer = new char[buffer.length];
                System.arraycopy(buffer,0,copy.buffer,0,buffer.length);
            }
            return copy;
        }
        catch (CloneNotSupportedException e) {
            throw new InternalError(e.toString(), e);
        }
    }

    //--------------------------------------------------------------------------
    // Static Utility methods
    //--------------------------------------------------------------------------

    /**
     * Compose a string.
     * The string will be composed according to the specified mode.
     * @param str        The string to compose.
     * @param compat     If true the string will be composed according to
     *                    NFKC rules and if false will be composed according to
     *                    NFC rules.
     * @param options    The only recognized option is UNICODE_3_2
     * @return String    The composed string
     * @stable ICU 2.6
     */
    public static String compose(String str, boolean compat, int options) {

        char[] dest, src;
        if (options == UNICODE_3_2_0_ORIGINAL) {
            String mappedStr = NormalizerImpl.convert(str);
            dest = new char[mappedStr.length()*MAX_BUF_SIZE_COMPOSE];
            src = mappedStr.toCharArray();
        } else {
            dest = new char[str.length()*MAX_BUF_SIZE_COMPOSE];
            src = str.toCharArray();
        }
        int destSize=0;

        UnicodeSet nx = NormalizerImpl.getNX(options);

        /* reset options bits that should only be set here or inside compose() */
        options&=~(NormalizerImpl.OPTIONS_SETS_MASK|NormalizerImpl.OPTIONS_COMPAT|NormalizerImpl.OPTIONS_COMPOSE_CONTIGUOUS);

        if(compat) {
            options|=NormalizerImpl.OPTIONS_COMPAT;
        }

        for(;;) {
            destSize=NormalizerImpl.compose(src,0,src.length,
                                            dest,0,dest.length,options,
                                            nx);
            if(destSize<=dest.length) {
                return new String(dest,0,destSize);
            } else {
                dest = new char[destSize];
            }
        }
    }

    private static final int MAX_BUF_SIZE_COMPOSE = 2;
    private static final int MAX_BUF_SIZE_DECOMPOSE = 3;

    /**
     * Decompose a string.
     * The string will be decomposed according to the specified mode.
     * @param str       The string to decompose.
     * @param compat    If true the string will be decomposed according to NFKD
     *                   rules and if false will be decomposed according to NFD
     *                   rules.
     * @return String   The decomposed string
     * @stable ICU 2.8
     */
    public static String decompose(String str, boolean compat) {
        return decompose(str,compat,UNICODE_LATEST);
    }

    /**
     * Decompose a string.
     * The string will be decomposed according to the specified mode.
     * @param str     The string to decompose.
     * @param compat  If true the string will be decomposed according to NFKD
     *                 rules and if false will be decomposed according to NFD
     *                 rules.
     * @param options The normalization options, ORed together (0 for no options).
     * @return String The decomposed string
     * @stable ICU 2.6
     */
    public static String decompose(String str, boolean compat, int options) {

        int[] trailCC = new int[1];
        int destSize=0;
        UnicodeSet nx = NormalizerImpl.getNX(options);
        char[] dest;

        if (options == UNICODE_3_2_0_ORIGINAL) {
            String mappedStr = NormalizerImpl.convert(str);
            dest = new char[mappedStr.length()*MAX_BUF_SIZE_DECOMPOSE];

            for(;;) {
                destSize=NormalizerImpl.decompose(mappedStr.toCharArray(),0,mappedStr.length(),
                                                  dest,0,dest.length,
                                                  compat,trailCC, nx);
                if(destSize<=dest.length) {
                    return new String(dest,0,destSize);
                } else {
                    dest = new char[destSize];
                }
            }
        } else {
            dest = new char[str.length()*MAX_BUF_SIZE_DECOMPOSE];

            for(;;) {
                destSize=NormalizerImpl.decompose(str.toCharArray(),0,str.length(),
                                                  dest,0,dest.length,
                                                  compat,trailCC, nx);
                if(destSize<=dest.length) {
                    return new String(dest,0,destSize);
                } else {
                    dest = new char[destSize];
                }
            }
        }
    }

    /**
     * Normalize a string.
     * The string will be normalized according to the specified normalization
     * mode and options.
     * @param src       The char array to compose.
     * @param srcStart  Start index of the source
     * @param srcLimit  Limit index of the source
     * @param dest      The char buffer to fill in
     * @param destStart Start index of the destination buffer
     * @param destLimit End index of the destination buffer
     * @param mode      The normalization mode; one of Normalizer.NONE,
     *                   Normalizer.NFD, Normalizer.NFC, Normalizer.NFKC,
     *                   Normalizer.NFKD, Normalizer.DEFAULT
     * @param options The normalization options, ORed together (0 for no options).
     * @return int      The total buffer size needed;if greater than length of
     *                   result, the output was truncated.
     * @exception       IndexOutOfBoundsException if the target capacity is
     *                   less than the required length
     * @stable ICU 2.6
     */
    public static int normalize(char[] src,int srcStart, int srcLimit,
                                char[] dest,int destStart, int destLimit,
                                Mode  mode, int options) {
        int length = mode.normalize(src,srcStart,srcLimit,dest,destStart,destLimit, options);

        if(length<=(destLimit-destStart)) {
            return length;
        } else {
            throw new IndexOutOfBoundsException(Integer.toString(length));
        }
    }

    //-------------------------------------------------------------------------
    // Iteration API
    //-------------------------------------------------------------------------

    /**
     * Return the current character in the normalized text->
     * @return The codepoint as an int
     * @stable ICU 2.8
     */
    public int current() {
        if(bufferPos<bufferLimit || nextNormalize()) {
            return getCodePointAt(bufferPos);
        } else {
            return DONE;
        }
    }

    /**
     * Return the next character in the normalized text and advance
     * the iteration position by one.  If the end
     * of the text has already been reached, {@link #DONE} is returned.
     * @return The codepoint as an int
     * @stable ICU 2.8
     */
    public int next() {
        if(bufferPos<bufferLimit ||  nextNormalize()) {
            int c=getCodePointAt(bufferPos);
            bufferPos+=(c>0xFFFF) ? 2 : 1;
            return c;
        } else {
            return DONE;
        }
    }


    /**
     * Return the previous character in the normalized text and decrement
     * the iteration position by one.  If the beginning
     * of the text has already been reached, {@link #DONE} is returned.
     * @return The codepoint as an int
     * @stable ICU 2.8
     */
    public int previous() {
        if(bufferPos>0 || previousNormalize()) {
            int c=getCodePointAt(bufferPos-1);
            bufferPos-=(c>0xFFFF) ? 2 : 1;
            return c;
        } else {
            return DONE;
        }
    }

    /**
     * Reset the index to the beginning of the text.
     * This is equivalent to setIndexOnly(startIndex)).
     * @stable ICU 2.8
     */
    public void reset() {
        text.setIndex(0);
        currentIndex=nextIndex=0;
        clearBuffer();
    }

    /**
     * Set the iteration position in the input text that is being normalized,
     * without any immediate normalization.
     * After setIndexOnly(), getIndex() will return the same index that is
     * specified here.
     *
     * @param index the desired index in the input text.
     * @stable ICU 2.8
     */
    public void setIndexOnly(int index) {
        text.setIndex(index);
        currentIndex=nextIndex=index; // validates index
        clearBuffer();
    }

    /**
     * Set the iteration position in the input text that is being normalized
     * and return the first normalized character at that position.
     * <p>
     * <b>Note:</b> This method sets the position in the <em>input</em> text,
     * while {@link #next} and {@link #previous} iterate through characters
     * in the normalized <em>output</em>.  This means that there is not
     * necessarily a one-to-one correspondence between characters returned
     * by <tt>next</tt> and <tt>previous</tt> and the indices passed to and
     * returned from <tt>setIndex</tt> and {@link #getIndex}.
     * <p>
     * @param index the desired index in the input text->
     *
     * @return   the first normalized character that is the result of iterating
     *            forward starting at the given index.
     *
     * @throws IllegalArgumentException if the given index is less than
     *          {@link #getBeginIndex} or greater than {@link #getEndIndex}.
     * @return The codepoint as an int
     * @deprecated ICU 3.2
     * @obsolete ICU 3.2
     */
     @Deprecated
     public int setIndex(int index) {
         setIndexOnly(index);
         return current();
     }

    /**
     * Retrieve the index of the start of the input text. This is the begin
     * index of the <tt>CharacterIterator</tt> or the start (i.e. 0) of the
     * <tt>String</tt> over which this <tt>Normalizer</tt> is iterating
     * @deprecated ICU 2.2. Use startIndex() instead.
     * @return The codepoint as an int
     * @see #startIndex
     */
    @Deprecated
    public int getBeginIndex() {
        return 0;
    }

    /**
     * Retrieve the index of the end of the input text.  This is the end index
     * of the <tt>CharacterIterator</tt> or the length of the <tt>String</tt>
     * over which this <tt>Normalizer</tt> is iterating
     * @deprecated ICU 2.2. Use endIndex() instead.
     * @return The codepoint as an int
     * @see #endIndex
     */
    @Deprecated
    public int getEndIndex() {
        return endIndex();
    }

    /**
     * Retrieve the current iteration position in the input text that is
     * being normalized.  This method is useful in applications such as
     * searching, where you need to be able to determine the position in
     * the input text that corresponds to a given normalized output character.
     * <p>
     * <b>Note:</b> This method sets the position in the <em>input</em>, while
     * {@link #next} and {@link #previous} iterate through characters in the
     * <em>output</em>.  This means that there is not necessarily a one-to-one
     * correspondence between characters returned by <tt>next</tt> and
     * <tt>previous</tt> and the indices passed to and returned from
     * <tt>setIndex</tt> and {@link #getIndex}.
     * @return The current iteration position
     * @stable ICU 2.8
     */
    public int getIndex() {
        if(bufferPos<bufferLimit) {
            return currentIndex;
        } else {
            return nextIndex;
        }
    }

    /**
     * Retrieve the index of the end of the input text->  This is the end index
     * of the <tt>CharacterIterator</tt> or the length of the <tt>String</tt>
     * over which this <tt>Normalizer</tt> is iterating
     * @return The current iteration position
     * @stable ICU 2.8
     */
    public int endIndex() {
        return text.getLength();
    }

    //-------------------------------------------------------------------------
    // Property access methods
    //-------------------------------------------------------------------------
    /**
     * Set the normalization mode for this object.
     * <p>
     * <b>Note:</b>If the normalization mode is changed while iterating
     * over a string, calls to {@link #next} and {@link #previous} may
     * return previously buffers characters in the old normalization mode
     * until the iteration is able to re-sync at the next base character.
     * It is safest to call {@link #setText setText()}, {@link #first},
     * {@link #last}, etc. after calling <tt>setMode</tt>.
     * <p>
     * @param newMode the new mode for this <tt>Normalizer</tt>.
     * The supported modes are:
     * <ul>
     *  <li>{@link #COMPOSE}        - Unicode canonical decompositiion
     *                                  followed by canonical composition.
     *  <li>{@link #COMPOSE_COMPAT} - Unicode compatibility decompositiion
     *                                  follwed by canonical composition.
     *  <li>{@link #DECOMP}         - Unicode canonical decomposition
     *  <li>{@link #DECOMP_COMPAT}  - Unicode compatibility decomposition.
     *  <li>{@link #NO_OP}          - Do nothing but return characters
     *                                  from the underlying input text.
     * </ul>
     *
     * @see #getMode
     * @stable ICU 2.8
     */
    public void setMode(Mode newMode) {
        mode = newMode;
    }
    /**
     * Return the basic operation performed by this <tt>Normalizer</tt>
     *
     * @see #setMode
     * @stable ICU 2.8
     */
    public Mode getMode() {
        return mode;
    }

    /**
     * Set the input text over which this <tt>Normalizer</tt> will iterate.
     * The iteration position is set to the beginning of the input text->
     * @param newText   The new string to be normalized.
     * @stable ICU 2.8
     */
    public void setText(String newText) {

        UCharacterIterator newIter = UCharacterIterator.getInstance(newText);
        if (newIter == null) {
            throw new InternalError("Could not create a new UCharacterIterator");
        }
        text = newIter;
        reset();
    }

    /**
     * Set the input text over which this <tt>Normalizer</tt> will iterate.
     * The iteration position is set to the beginning of the input text->
     * @param newText   The new string to be normalized.
     * @stable ICU 2.8
     */
    public void setText(CharacterIterator newText) {

        UCharacterIterator newIter = UCharacterIterator.getInstance(newText);
        if (newIter == null) {
            throw new InternalError("Could not create a new UCharacterIterator");
        }
        text = newIter;
        currentIndex=nextIndex=0;
        clearBuffer();
    }

    //-------------------------------------------------------------------------
    // Private utility methods
    //-------------------------------------------------------------------------


    /* backward iteration --------------------------------------------------- */

    /*
     * read backwards and get norm32
     * return 0 if the character is <minC
     * if c2!=0 then (c2, c) is a surrogate pair (reversed - c2 is first
     * surrogate but read second!)
     */

    private static  long getPrevNorm32(UCharacterIterator src,
                                       int/*unsigned*/ minC,
                                       int/*unsigned*/ mask,
                                       char[] chars) {
        long norm32;
        int ch=0;
        /* need src.hasPrevious() */
        if((ch=src.previous()) == UCharacterIterator.DONE) {
            return 0;
        }
        chars[0]=(char)ch;
        chars[1]=0;

        /* check for a surrogate before getting norm32 to see if we need to
         * predecrement further */
        if(chars[0]<minC) {
            return 0;
        } else if(!UTF16.isSurrogate(chars[0])) {
            return NormalizerImpl.getNorm32(chars[0]);
        } else if(UTF16.isLeadSurrogate(chars[0]) || (src.getIndex()==0)) {
            /* unpaired surrogate */
            chars[1]=(char)src.current();
            return 0;
        } else if(UTF16.isLeadSurrogate(chars[1]=(char)src.previous())) {
            norm32=NormalizerImpl.getNorm32(chars[1]);
            if((norm32&mask)==0) {
                /* all surrogate pairs with this lead surrogate have irrelevant
                 * data */
                return 0;
            } else {
                /* norm32 must be a surrogate special */
                return NormalizerImpl.getNorm32FromSurrogatePair(norm32,chars[0]);
            }
        } else {
            /* unpaired second surrogate, undo the c2=src.previous() movement */
            src.moveIndex( 1);
            return 0;
        }
    }

    private interface IsPrevBoundary{
        public boolean isPrevBoundary(UCharacterIterator src,
                                      int/*unsigned*/ minC,
                                      int/*unsigned*/ mask,
                                      char[] chars);
    }
    private static final class IsPrevNFDSafe implements IsPrevBoundary{
        /*
         * for NF*D:
         * read backwards and check if the lead combining class is 0
         * if c2!=0 then (c2, c) is a surrogate pair (reversed - c2 is first
         * surrogate but read second!)
         */
        public boolean isPrevBoundary(UCharacterIterator src,
                                      int/*unsigned*/ minC,
                                      int/*unsigned*/ ccOrQCMask,
                                      char[] chars) {

            return NormalizerImpl.isNFDSafe(getPrevNorm32(src, minC,
                                                          ccOrQCMask, chars),
                                            ccOrQCMask,
                                            ccOrQCMask& NormalizerImpl.QC_MASK);
        }
    }

    private static final class IsPrevTrueStarter implements IsPrevBoundary{
        /*
         * read backwards and check if the character is (or its decomposition
         * begins with) a "true starter" (cc==0 and NF*C_YES)
         * if c2!=0 then (c2, c) is a surrogate pair (reversed - c2 is first
         * surrogate but read second!)
         */
        public boolean isPrevBoundary(UCharacterIterator src,
                                      int/*unsigned*/ minC,
                                      int/*unsigned*/ ccOrQCMask,
                                      char[] chars) {
            long norm32;
            int/*unsigned*/ decompQCMask;

            decompQCMask=(ccOrQCMask<<2)&0xf; /*decomposition quick check mask*/
            norm32=getPrevNorm32(src, minC, ccOrQCMask|decompQCMask, chars);
            return NormalizerImpl.isTrueStarter(norm32,ccOrQCMask,decompQCMask);
        }
    }

    private static int findPreviousIterationBoundary(UCharacterIterator src,
                                                     IsPrevBoundary obj,
                                                     int/*unsigned*/ minC,
                                                     int/*mask*/ mask,
                                                     char[] buffer,
                                                     int[] startIndex) {
        char[] chars=new char[2];
        boolean isBoundary;

        /* fill the buffer from the end backwards */
        startIndex[0] = buffer.length;
        chars[0]=0;
        while(src.getIndex()>0 && chars[0]!=UCharacterIterator.DONE) {
            isBoundary=obj.isPrevBoundary(src, minC, mask, chars);

            /* always write this character to the front of the buffer */
            /* make sure there is enough space in the buffer */
            if(startIndex[0] < (chars[1]==0 ? 1 : 2)) {

                // grow the buffer
                char[] newBuf = new char[buffer.length*2];
                /* move the current buffer contents up */
                System.arraycopy(buffer,startIndex[0],newBuf,
                                 newBuf.length-(buffer.length-startIndex[0]),
                                 buffer.length-startIndex[0]);
                //adjust the startIndex
                startIndex[0]+=newBuf.length-buffer.length;

                buffer=newBuf;
                newBuf=null;

            }

            buffer[--startIndex[0]]=chars[0];
            if(chars[1]!=0) {
                buffer[--startIndex[0]]=chars[1];
            }

            /* stop if this just-copied character is a boundary */
            if(isBoundary) {
                break;
            }
        }

        /* return the length of the buffer contents */
        return buffer.length-startIndex[0];
    }

    private static int previous(UCharacterIterator src,
                                char[] dest, int destStart, int destLimit,
                                Mode mode,
                                boolean doNormalize,
                                boolean[] pNeededToNormalize,
                                int options) {

        IsPrevBoundary isPreviousBoundary;
        int destLength, bufferLength;
        int/*unsigned*/ mask;
        int c,c2;

        char minC;
        int destCapacity = destLimit-destStart;
        destLength=0;

        if(pNeededToNormalize!=null) {
            pNeededToNormalize[0]=false;
        }
        minC = (char)mode.getMinC();
        mask = mode.getMask();
        isPreviousBoundary = mode.getPrevBoundary();

        if(isPreviousBoundary==null) {
            destLength=0;
            if((c=src.previous())>=0) {
                destLength=1;
                if(UTF16.isTrailSurrogate((char)c)) {
                    c2= src.previous();
                    if(c2!= UCharacterIterator.DONE) {
                        if(UTF16.isLeadSurrogate((char)c2)) {
                            if(destCapacity>=2) {
                                dest[1]=(char)c; // trail surrogate
                                destLength=2;
                            }
                            // lead surrogate to be written below
                            c=c2;
                        } else {
                            src.moveIndex(1);
                        }
                    }
                }

                if(destCapacity>0) {
                    dest[0]=(char)c;
                }
            }
            return destLength;
        }

        char[] buffer = new char[100];
        int[] startIndex= new int[1];
        bufferLength=findPreviousIterationBoundary(src,
                                                   isPreviousBoundary,
                                                   minC, mask,buffer,
                                                   startIndex);
        if(bufferLength>0) {
            if(doNormalize) {
                destLength=NormalizerBase.normalize(buffer,startIndex[0],
                                                startIndex[0]+bufferLength,
                                                dest, destStart,destLimit,
                                                mode, options);

                if(pNeededToNormalize!=null) {
                    pNeededToNormalize[0]=destLength!=bufferLength ||
                                          Utility.arrayRegionMatches(
                                            buffer,0,dest,
                                            destStart,destLimit
                                          );
                }
            } else {
                /* just copy the source characters */
                if(destCapacity>0) {
                    System.arraycopy(buffer,startIndex[0],dest,0,
                                     (bufferLength<destCapacity) ?
                                     bufferLength : destCapacity
                                     );
                }
            }
        }


        return destLength;
    }



    /* forward iteration ---------------------------------------------------- */
    /*
     * read forward and check if the character is a next-iteration boundary
     * if c2!=0 then (c, c2) is a surrogate pair
     */
    private interface IsNextBoundary{
        boolean isNextBoundary(UCharacterIterator src,
                               int/*unsigned*/ minC,
                               int/*unsigned*/ mask,
                               int[] chars);
    }
    /*
     * read forward and get norm32
     * return 0 if the character is <minC
     * if c2!=0 then (c2, c) is a surrogate pair
     * always reads complete characters
     */
    private static long /*unsigned*/ getNextNorm32(UCharacterIterator src,
                                                   int/*unsigned*/ minC,
                                                   int/*unsigned*/ mask,
                                                   int[] chars) {
        long norm32;

        /* need src.hasNext() to be true */
        chars[0]=src.next();
        chars[1]=0;

        if(chars[0]<minC) {
            return 0;
        }

        norm32=NormalizerImpl.getNorm32((char)chars[0]);
        if(UTF16.isLeadSurrogate((char)chars[0])) {
            if(src.current()!=UCharacterIterator.DONE &&
               UTF16.isTrailSurrogate((char)(chars[1]=src.current()))) {
                src.moveIndex(1); /* skip the c2 surrogate */
                if((norm32&mask)==0) {
                    /* irrelevant data */
                    return 0;
                } else {
                    /* norm32 must be a surrogate special */
                    return NormalizerImpl.getNorm32FromSurrogatePair(norm32,(char)chars[1]);
                }
            } else {
                /* unmatched surrogate */
                return 0;
            }
        }
        return norm32;
    }


    /*
     * for NF*D:
     * read forward and check if the lead combining class is 0
     * if c2!=0 then (c, c2) is a surrogate pair
     */
    private static final class IsNextNFDSafe implements IsNextBoundary{
        public boolean isNextBoundary(UCharacterIterator src,
                                      int/*unsigned*/ minC,
                                      int/*unsigned*/ ccOrQCMask,
                                      int[] chars) {
            return NormalizerImpl.isNFDSafe(getNextNorm32(src,minC,ccOrQCMask,chars),
                                            ccOrQCMask, ccOrQCMask&NormalizerImpl.QC_MASK);
        }
    }

    /*
     * for NF*C:
     * read forward and check if the character is (or its decomposition begins
     * with) a "true starter" (cc==0 and NF*C_YES)
     * if c2!=0 then (c, c2) is a surrogate pair
     */
    private static final class IsNextTrueStarter implements IsNextBoundary{
        public boolean isNextBoundary(UCharacterIterator src,
                                      int/*unsigned*/ minC,
                                      int/*unsigned*/ ccOrQCMask,
                                      int[] chars) {
            long norm32;
            int/*unsigned*/ decompQCMask;

            decompQCMask=(ccOrQCMask<<2)&0xf; /*decomposition quick check mask*/
            norm32=getNextNorm32(src, minC, ccOrQCMask|decompQCMask, chars);
            return NormalizerImpl.isTrueStarter(norm32, ccOrQCMask, decompQCMask);
        }
    }

    private static int findNextIterationBoundary(UCharacterIterator src,
                                                 IsNextBoundary obj,
                                                 int/*unsigned*/ minC,
                                                 int/*unsigned*/ mask,
                                                 char[] buffer) {
        if(src.current()==UCharacterIterator.DONE) {
            return 0;
        }

        /* get one character and ignore its properties */
        int[] chars = new int[2];
        chars[0]=src.next();
        buffer[0]=(char)chars[0];
        int bufferIndex = 1;

        if(UTF16.isLeadSurrogate((char)chars[0])&&
           src.current()!=UCharacterIterator.DONE) {
            if(UTF16.isTrailSurrogate((char)(chars[1]=src.next()))) {
                buffer[bufferIndex++]=(char)chars[1];
            } else {
                src.moveIndex(-1); /* back out the non-trail-surrogate */
            }
        }

        /* get all following characters until we see a boundary */
        /* checking hasNext() instead of c!=DONE on the off-chance that U+ffff
         * is part of the string */
        while( src.current()!=UCharacterIterator.DONE) {
            if(obj.isNextBoundary(src, minC, mask, chars)) {
                /* back out the latest movement to stop at the boundary */
                src.moveIndex(chars[1]==0 ? -1 : -2);
                break;
            } else {
                if(bufferIndex+(chars[1]==0 ? 1 : 2)<=buffer.length) {
                    buffer[bufferIndex++]=(char)chars[0];
                    if(chars[1]!=0) {
                        buffer[bufferIndex++]=(char)chars[1];
                    }
                } else {
                    char[] newBuf = new char[buffer.length*2];
                    System.arraycopy(buffer,0,newBuf,0,bufferIndex);
                    buffer = newBuf;
                    buffer[bufferIndex++]=(char)chars[0];
                    if(chars[1]!=0) {
                        buffer[bufferIndex++]=(char)chars[1];
                    }
                }
            }
        }

        /* return the length of the buffer contents */
        return bufferIndex;
    }

    private static int next(UCharacterIterator src,
                            char[] dest, int destStart, int destLimit,
                            NormalizerBase.Mode mode,
                            boolean doNormalize,
                            boolean[] pNeededToNormalize,
                            int options) {

        IsNextBoundary isNextBoundary;
        int /*unsigned*/ mask;
        int /*unsigned*/ bufferLength;
        int c,c2;
        char minC;
        int destCapacity = destLimit - destStart;
        int destLength = 0;
        if(pNeededToNormalize!=null) {
            pNeededToNormalize[0]=false;
        }

        minC = (char)mode.getMinC();
        mask = mode.getMask();
        isNextBoundary = mode.getNextBoundary();

        if(isNextBoundary==null) {
            destLength=0;
            c=src.next();
            if(c!=UCharacterIterator.DONE) {
                destLength=1;
                if(UTF16.isLeadSurrogate((char)c)) {
                    c2= src.next();
                    if(c2!= UCharacterIterator.DONE) {
                        if(UTF16.isTrailSurrogate((char)c2)) {
                            if(destCapacity>=2) {
                                dest[1]=(char)c2; // trail surrogate
                                destLength=2;
                            }
                            // lead surrogate to be written below
                        } else {
                            src.moveIndex(-1);
                        }
                    }
                }

                if(destCapacity>0) {
                    dest[0]=(char)c;
                }
            }
            return destLength;
        }

        char[] buffer=new char[100];
        int[] startIndex = new int[1];
        bufferLength=findNextIterationBoundary(src,isNextBoundary, minC, mask,
                                               buffer);
        if(bufferLength>0) {
            if(doNormalize) {
                destLength=mode.normalize(buffer,startIndex[0],bufferLength,
                                          dest,destStart,destLimit, options);

                if(pNeededToNormalize!=null) {
                    pNeededToNormalize[0]=destLength!=bufferLength ||
                                          Utility.arrayRegionMatches(buffer,startIndex[0],
                                            dest,destStart,
                                            destLength);
                }
            } else {
                /* just copy the source characters */
                if(destCapacity>0) {
                    System.arraycopy(buffer,0,dest,destStart,
                                     Math.min(bufferLength,destCapacity)
                                     );
                }


            }
        }
        return destLength;
    }

    private void clearBuffer() {
        bufferLimit=bufferStart=bufferPos=0;
    }

    private boolean nextNormalize() {

        clearBuffer();
        currentIndex=nextIndex;
        text.setIndex(nextIndex);

        bufferLimit=next(text,buffer,bufferStart,buffer.length,mode,true,null,options);

        nextIndex=text.getIndex();
        return (bufferLimit>0);
    }

    private boolean previousNormalize() {

        clearBuffer();
        nextIndex=currentIndex;
        text.setIndex(currentIndex);
        bufferLimit=previous(text,buffer,bufferStart,buffer.length,mode,true,null,options);

        currentIndex=text.getIndex();
        bufferPos = bufferLimit;
        return bufferLimit>0;
    }

    private int getCodePointAt(int index) {
        if( UTF16.isSurrogate(buffer[index])) {
            if(UTF16.isLeadSurrogate(buffer[index])) {
                if((index+1)<bufferLimit &&
                   UTF16.isTrailSurrogate(buffer[index+1])) {
                    return UCharacterProperty.getRawSupplementary(
                                                                  buffer[index],
                                                                  buffer[index+1]
                                                                  );
                }
            }else if(UTF16.isTrailSurrogate(buffer[index])) {
                if(index>0 && UTF16.isLeadSurrogate(buffer[index-1])) {
                    return UCharacterProperty.getRawSupplementary(
                                                                  buffer[index-1],
                                                                  buffer[index]
                                                                  );
                }
            }
        }
        return buffer[index];

    }

    /**
     * Internal API
     * @internal
     */
    public static boolean isNFSkippable(int c, Mode mode) {
        return mode.isNFSkippable(c);
    }

    //
    // Options
    //

    /*
     * Default option for Unicode 3.2.0 normalization.
     * Corrigendum 4 was fixed in Unicode 3.2.0 but isn't supported in
     * IDNA/StringPrep.
     * The public review issue #29 was fixed in Unicode 4.1.0. Corrigendum 5
     * allowed Unicode 3.2 to 4.0.1 to apply the fix for PRI #29, but it isn't
     * supported by IDNA/StringPrep as well as Corrigendum 4.
     */
    public static final int UNICODE_3_2_0_ORIGINAL =
                               UNICODE_3_2 |
                               NormalizerImpl.WITHOUT_CORRIGENDUM4_CORRECTIONS |
                               NormalizerImpl.BEFORE_PRI_29;

    /*
     * Default option for the latest Unicode normalization. This option is
     * provided mainly for testing.
     * The value zero means that normalization is done with the fixes for
     *   - Corrigendum 4 (Five CJK Canonical Mapping Errors)
     *   - Corrigendum 5 (Normalization Idempotency)
     */
    public static final int UNICODE_LATEST = 0x00;

    //
    // public constructor and methods for java.text.Normalizer and
    // sun.text.Normalizer
    //

    /**
     * Creates a new <tt>Normalizer</tt> object for iterating over the
     * normalized form of a given string.
     *
     * @param str  The string to be normalized.  The normalization
     *              will start at the beginning of the string.
     *
     * @param mode The normalization mode.
     */
    public NormalizerBase(String str, Mode mode) {
          this(str, mode, UNICODE_LATEST);
    }

    /**
     * Normalizes a <code>String</code> using the given normalization form.
     *
     * @param str      the input string to be normalized.
     * @param form     the normalization form
     */
    public static String normalize(String str, Normalizer.Form form) {
        return normalize(str, form, UNICODE_LATEST);
    }

    /**
     * Normalizes a <code>String</code> using the given normalization form.
     *
     * @param str      the input string to be normalized.
     * @param form     the normalization form
     * @param options   the optional features to be enabled.
     */
    public static String normalize(String str, Normalizer.Form form, int options) {
        int len = str.length();
        boolean asciiOnly = true;
        if (len < 80) {
            for (int i = 0; i < len; i++) {
                if (str.charAt(i) > 127) {
                    asciiOnly = false;
                    break;
                }
            }
        } else {
            char[] a = str.toCharArray();
            for (int i = 0; i < len; i++) {
                if (a[i] > 127) {
                    asciiOnly = false;
                    break;
                }
            }
        }

        switch (form) {
        case NFC :
            return asciiOnly ? str : NFC.normalize(str, options);
        case NFD :
            return asciiOnly ? str : NFD.normalize(str, options);
        case NFKC :
            return asciiOnly ? str : NFKC.normalize(str, options);
        case NFKD :
            return asciiOnly ? str : NFKD.normalize(str, options);
        }

        throw new IllegalArgumentException("Unexpected normalization form: " +
                                           form);
    }

    /**
     * Test if a string is in a given normalization form.
     * This is semantically equivalent to source.equals(normalize(source, mode)).
     *
     * Unlike quickCheck(), this function returns a definitive result,
     * never a "maybe".
     * For NFD, NFKD, and FCD, both functions work exactly the same.
     * For NFC and NFKC where quickCheck may return "maybe", this function will
     * perform further tests to arrive at a true/false result.
     * @param str       the input string to be checked to see if it is normalized
     * @param form      the normalization form
     * @param options   the optional features to be enabled.
     */
    public static boolean isNormalized(String str, Normalizer.Form form) {
        return isNormalized(str, form, UNICODE_LATEST);
    }

    /**
     * Test if a string is in a given normalization form.
     * This is semantically equivalent to source.equals(normalize(source, mode)).
     *
     * Unlike quickCheck(), this function returns a definitive result,
     * never a "maybe".
     * For NFD, NFKD, and FCD, both functions work exactly the same.
     * For NFC and NFKC where quickCheck may return "maybe", this function will
     * perform further tests to arrive at a true/false result.
     * @param str       the input string to be checked to see if it is normalized
     * @param form      the normalization form
     * @param options   the optional features to be enabled.
     */
    public static boolean isNormalized(String str, Normalizer.Form form, int options) {
        switch (form) {
        case NFC:
            return (NFC.quickCheck(str.toCharArray(),0,str.length(),false,NormalizerImpl.getNX(options))==YES);
        case NFD:
            return (NFD.quickCheck(str.toCharArray(),0,str.length(),false,NormalizerImpl.getNX(options))==YES);
        case NFKC:
            return (NFKC.quickCheck(str.toCharArray(),0,str.length(),false,NormalizerImpl.getNX(options))==YES);
        case NFKD:
            return (NFKD.quickCheck(str.toCharArray(),0,str.length(),false,NormalizerImpl.getNX(options))==YES);
        }

        throw new IllegalArgumentException("Unexpected normalization form: " +
                                           form);
    }
}
