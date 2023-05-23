// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2000-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */
package jdk.internal.icu.text;
import java.nio.CharBuffer;
import java.text.CharacterIterator;

import jdk.internal.icu.impl.Norm2AllModes;
import jdk.internal.icu.impl.Normalizer2Impl;
import jdk.internal.icu.impl.UCaseProps;
import jdk.internal.icu.lang.UCharacter;
import jdk.internal.icu.util.ICUCloneNotSupportedException;

/**
 * Old Unicode normalization API.
 *
 * <p>This API has been replaced by the {@link Normalizer2} class and is only available
 * for backward compatibility. This class simply delegates to the Normalizer2 class.
 * There are two exceptions: The new API does not provide a replacement for
 * <code>QuickCheckResult</code> and <code>compare()</code>.
 *
 * <p><code>normalize</code> transforms Unicode text into an equivalent composed or
 * decomposed form, allowing for easier sorting and searching of text.
 * <code>normalize</code> supports the standard normalization forms described in
 * <a href="https://www.unicode.org/reports/tr15/" target="unicode">
 * Unicode Standard Annex #15 &mdash; Unicode Normalization Forms</a>.
 *
 * <p>Characters with accents or other adornments can be encoded in
 * several different ways in Unicode.  For example, take the character A-acute.
 * In Unicode, this can be encoded as a single character (the
 * "composed" form):
 *
 * <pre>
 *      00C1    LATIN CAPITAL LETTER A WITH ACUTE
 * </pre>
 *
 * or as two separate characters (the "decomposed" form):
 *
 * <pre>
 *      0041    LATIN CAPITAL LETTER A
 *      0301    COMBINING ACUTE ACCENT
 * </pre>
 *
 * <p>To a user of your program, however, both of these sequences should be
 * treated as the same "user-level" character "A with acute accent".  When you
 * are searching or comparing text, you must ensure that these two sequences are
 * treated equivalently.  In addition, you must handle characters with more than
 * one accent.  Sometimes the order of a character's combining accents is
 * significant, while in other cases accent sequences in different orders are
 * really equivalent.
 *
 * <p>Similarly, the string "ffi" can be encoded as three separate letters:
 *
 * <pre>
 *      0066    LATIN SMALL LETTER F
 *      0066    LATIN SMALL LETTER F
 *      0069    LATIN SMALL LETTER I
 * </pre>
 *
 * or as the single character
 *
 * <pre>
 *      FB03    LATIN SMALL LIGATURE FFI
 * </pre>
 *
 * <p>The ffi ligature is not a distinct semantic character, and strictly speaking
 * it shouldn't be in Unicode at all, but it was included for compatibility
 * with existing character sets that already provided it.  The Unicode standard
 * identifies such characters by giving them "compatibility" decompositions
 * into the corresponding semantic characters.  When sorting and searching, you
 * will often want to use these mappings.
 *
 * <p><code>normalize</code> helps solve these problems by transforming text into
 * the canonical composed and decomposed forms as shown in the first example
 * above. In addition, you can have it perform compatibility decompositions so
 * that you can treat compatibility characters the same as their equivalents.
 * Finally, <code>normalize</code> rearranges accents into the proper canonical
 * order, so that you do not have to worry about accent rearrangement on your
 * own.
 *
 * <p>Form FCD, "Fast C or D", is also designed for collation.
 * It allows to work on strings that are not necessarily normalized
 * with an algorithm (like in collation) that works under "canonical closure",
 * i.e., it treats precomposed characters and their decomposed equivalents the
 * same.
 *
 * <p>It is not a normalization form because it does not provide for uniqueness of
 * representation. Multiple strings may be canonically equivalent (their NFDs
 * are identical) and may all conform to FCD without being identical themselves.
 *
 * <p>The form is defined such that the "raw decomposition", the recursive
 * canonical decomposition of each character, results in a string that is
 * canonically ordered. This means that precomposed characters are allowed for
 * as long as their decompositions do not need canonical reordering.
 *
 * <p>Its advantage for a process like collation is that all NFD and most NFC texts
 * - and many unnormalized texts - already conform to FCD and do not need to be
 * normalized (NFD) for such a process. The FCD quick check will return YES for
 * most strings in practice.
 *
 * <p>normalize(FCD) may be implemented with NFD.
 *
 * <p>For more details on FCD see Unicode Technical Note #5 (Canonical Equivalence in Applications):
 * http://www.unicode.org/notes/tn5/#FCD
 *
 * <p>ICU collation performs either NFD or FCD normalization automatically if
 * normalization is turned on for the collator object. Beyond collation and
 * string search, normalized strings may be useful for string equivalence
 * comparisons, transliteration/transcription, unique representations, etc.
 *
 * <p>The W3C generally recommends to exchange texts in NFC.
 * Note also that most legacy character encodings use only precomposed forms and
 * often do not encode any combining marks by themselves. For conversion to such
 * character encodings the Unicode text needs to be normalized to NFC.
 * For more usage examples, see the Unicode Standard Annex.
 *
 * <p>Note: The Normalizer class also provides API for iterative normalization.
 * While the setIndex() and getIndex() refer to indices in the
 * underlying Unicode input text, the next() and previous() methods
 * iterate through characters in the normalized output.
 * This means that there is not necessarily a one-to-one correspondence
 * between characters returned by next() and previous() and the indices
 * passed to and returned from setIndex() and getIndex().
 * It is for this reason that Normalizer does not implement the CharacterIterator interface.
 *
 * @stable ICU 2.8
 */
public final class Normalizer implements Cloneable {
    // The input text and our position in it
    private UCharacterIterator  text;
    private Normalizer2         norm2;
    private Mode                mode;
    private int                 options;

    // The normalization buffer is the result of normalization
    // of the source in [currentIndex..nextIndex[ .
    private int                 currentIndex;
    private int                 nextIndex;

    // A buffer for holding intermediate results
    private StringBuilder       buffer;
    private int                 bufferPos;

    // Helper classes to defer loading of normalization data.
    private static final class ModeImpl {
        private ModeImpl(Normalizer2 n2) {
            normalizer2 = n2;
        }
        private final Normalizer2 normalizer2;
    }
    private static final class NFDModeImpl {
        private static final ModeImpl INSTANCE = new ModeImpl(Normalizer2.getNFDInstance());
    }
    private static final class NFKDModeImpl {
        private static final ModeImpl INSTANCE = new ModeImpl(Normalizer2.getNFKDInstance());
    }
    private static final class NFCModeImpl {
        private static final ModeImpl INSTANCE = new ModeImpl(Normalizer2.getNFCInstance());
    }
    private static final class NFKCModeImpl {
        private static final ModeImpl INSTANCE = new ModeImpl(Normalizer2.getNFKCInstance());
    }
    private static final class FCDModeImpl {
        private static final ModeImpl INSTANCE = new ModeImpl(Norm2AllModes.getFCDNormalizer2());
    }

    private static final class Unicode32 {
        private static final UnicodeSet INSTANCE = new UnicodeSet("[:age=3.2:]").freeze();
    }
    private static final class NFD32ModeImpl {
        private static final ModeImpl INSTANCE =
            new ModeImpl(new FilteredNormalizer2(Normalizer2.getNFDInstance(),
                                                 Unicode32.INSTANCE));
    }
    private static final class NFKD32ModeImpl {
        private static final ModeImpl INSTANCE =
            new ModeImpl(new FilteredNormalizer2(Normalizer2.getNFKDInstance(),
                                                 Unicode32.INSTANCE));
    }
    private static final class NFC32ModeImpl {
        private static final ModeImpl INSTANCE =
            new ModeImpl(new FilteredNormalizer2(Normalizer2.getNFCInstance(),
                                                 Unicode32.INSTANCE));
    }
    private static final class NFKC32ModeImpl {
        private static final ModeImpl INSTANCE =
            new ModeImpl(new FilteredNormalizer2(Normalizer2.getNFKCInstance(),
                                                 Unicode32.INSTANCE));
    }
    private static final class FCD32ModeImpl {
        private static final ModeImpl INSTANCE =
            new ModeImpl(new FilteredNormalizer2(Norm2AllModes.getFCDNormalizer2(),
                                                 Unicode32.INSTANCE));
    }

    /**
     * Options bit set value to select Unicode 3.2 normalization
     * (except NormalizationCorrections).
     * At most one Unicode version can be selected at a time.
     *
     * @deprecated ICU 56 Use {@link FilteredNormalizer2} instead.
     */
    @Deprecated
    public static final int UNICODE_3_2=0x20;

    /**
     * Constant indicating that the end of the iteration has been reached.
     * This is guaranteed to have the same value as {@link UCharacterIterator#DONE}.
     *
     * @deprecated ICU 56
     */
    @Deprecated
    public static final int DONE = UCharacterIterator.DONE;

    /**
     * Constants for normalization modes.
     * <p>
     * The Mode class is not intended for public subclassing.
     * Only the Mode constants provided by the Normalizer class should be used,
     * and any fields or methods should not be called or overridden by users.
     *
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static abstract class Mode {
        /**
         * Sole constructor
         * @internal
         * @deprecated This API is ICU internal only.
         */
        @Deprecated
        protected Mode() {
        }

        /**
         * @internal
         * @deprecated This API is ICU internal only.
         */
        @Deprecated
        protected abstract Normalizer2 getNormalizer2(int options);
    }

    private static final class NONEMode extends Mode {
        @Override
        protected Normalizer2 getNormalizer2(int options) { return Norm2AllModes.NOOP_NORMALIZER2; }
    }
    private static final class NFDMode extends Mode {
        @Override
        protected Normalizer2 getNormalizer2(int options) {
            return (options&UNICODE_3_2) != 0 ?
                    NFD32ModeImpl.INSTANCE.normalizer2 : NFDModeImpl.INSTANCE.normalizer2;
        }
    }
    private static final class NFKDMode extends Mode {
        @Override
        protected Normalizer2 getNormalizer2(int options) {
            return (options&UNICODE_3_2) != 0 ?
                    NFKD32ModeImpl.INSTANCE.normalizer2 : NFKDModeImpl.INSTANCE.normalizer2;
        }
    }
    private static final class NFCMode extends Mode {
        @Override
        protected Normalizer2 getNormalizer2(int options) {
            return (options&UNICODE_3_2) != 0 ?
                    NFC32ModeImpl.INSTANCE.normalizer2 : NFCModeImpl.INSTANCE.normalizer2;
        }
    }
    private static final class NFKCMode extends Mode {
        @Override
        protected Normalizer2 getNormalizer2(int options) {
            return (options&UNICODE_3_2) != 0 ?
                    NFKC32ModeImpl.INSTANCE.normalizer2 : NFKCModeImpl.INSTANCE.normalizer2;
        }
    }
    private static final class FCDMode extends Mode {
        @Override
        protected Normalizer2 getNormalizer2(int options) {
            return (options&UNICODE_3_2) != 0 ?
                    FCD32ModeImpl.INSTANCE.normalizer2 : FCDModeImpl.INSTANCE.normalizer2;
        }
    }

    /**
     * No decomposition/composition.
     *
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static final Mode NONE = new NONEMode();

    /**
     * Canonical decomposition.
     *
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static final Mode NFD = new NFDMode();

    /**
     * Compatibility decomposition.
     *
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static final Mode NFKD = new NFKDMode();

    /**
     * Canonical decomposition followed by canonical composition.
     *
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static final Mode NFC = new NFCMode();

    /**
     * Default normalization.
     *
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static final Mode DEFAULT = NFC;

    /**
     * Compatibility decomposition followed by canonical composition.
     *
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static final Mode NFKC =new NFKCMode();

    /**
     * "Fast C or D" form.
     *
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static final Mode FCD = new FCDMode();

    /**
     * Null operation for use with the {@link jdk.internal.icu.text.Normalizer constructors}
     * and the static {@link #normalize normalize} method.  This value tells
     * the <tt>Normalizer</tt> to do nothing but return unprocessed characters
     * from the underlying String or CharacterIterator.  If you have code which
     * requires raw text at some times and normalized text at others, you can
     * use <tt>NO_OP</tt> for the cases where you want raw text, rather
     * than having a separate code path that bypasses <tt>Normalizer</tt>
     * altogether.
     * <p>
     * @see #setMode
     * @deprecated ICU 2.8. Use Nomalizer.NONE
     * @see #NONE
     */
    @Deprecated
    public static final Mode NO_OP = NONE;

    /**
     * Canonical decomposition followed by canonical composition.  Used with the
     * {@link jdk.internal.icu.text.Normalizer constructors} and the static
     * {@link #normalize normalize} method to determine the operation to be
     * performed.
     * <p>
     * If all optional features (<i>e.g.</i> {@link #IGNORE_HANGUL}) are turned
     * off, this operation produces output that is in
     * <a href=https://www.unicode.org/reports/tr15/>Unicode Canonical
     * Form</a>
     * <b>C</b>.
     * <p>
     * @see #setMode
     * @deprecated ICU 2.8. Use Normalier.NFC
     * @see #NFC
     */
    @Deprecated
    public static final Mode COMPOSE = NFC;

    /**
     * Compatibility decomposition followed by canonical composition.
     * Used with the {@link jdk.internal.icu.text.Normalizer constructors} and the static
     * {@link #normalize normalize} method to determine the operation to be
     * performed.
     * <p>
     * If all optional features (<i>e.g.</i> {@link #IGNORE_HANGUL}) are turned
     * off, this operation produces output that is in
     * <a href=https://www.unicode.org/reports/tr15/>Unicode Canonical
     * Form</a>
     * <b>KC</b>.
     * <p>
     * @see #setMode
     * @deprecated ICU 2.8. Use Normalizer.NFKC
     * @see #NFKC
     */
    @Deprecated
    public static final Mode COMPOSE_COMPAT = NFKC;

    /**
     * Canonical decomposition.  This value is passed to the
     * {@link jdk.internal.icu.text.Normalizer constructors} and the static
     * {@link #normalize normalize}
     * method to determine the operation to be performed.
     * <p>
     * If all optional features (<i>e.g.</i> {@link #IGNORE_HANGUL}) are turned
     * off, this operation produces output that is in
     * <a href=https://www.unicode.org/reports/tr15/>Unicode Canonical
     * Form</a>
     * <b>D</b>.
     * <p>
     * @see #setMode
     * @deprecated ICU 2.8. Use Normalizer.NFD
     * @see #NFD
     */
    @Deprecated
    public static final Mode DECOMP = NFD;

    /**
     * Compatibility decomposition.  This value is passed to the
     * {@link jdk.internal.icu.text.Normalizer constructors} and the static
     * {@link #normalize normalize}
     * method to determine the operation to be performed.
     * <p>
     * If all optional features (<i>e.g.</i> {@link #IGNORE_HANGUL}) are turned
     * off, this operation produces output that is in
     * <a href=https://www.unicode.org/reports/tr15/>Unicode Canonical
     * Form</a>
     * <b>KD</b>.
     * <p>
     * @see #setMode
     * @deprecated ICU 2.8. Use Normalizer.NFKD
     * @see #NFKD
     */
    @Deprecated
    public static final Mode DECOMP_COMPAT = NFKD;

    /**
     * Option to disable Hangul/Jamo composition and decomposition.
     * This option applies to Korean text,
     * which can be represented either in the Jamo alphabet or in Hangul
     * characters, which are really just two or three Jamo combined
     * into one visual glyph.  Since Jamo takes up more storage space than
     * Hangul, applications that process only Hangul text may wish to turn
     * this option on when decomposing text.
     * <p>
     * The Unicode standard treats Hangul to Jamo conversion as a
     * canonical decomposition, so this option must be turned <b>off</b> if you
     * wish to transform strings into one of the standard
     * <a href="https://www.unicode.org/reports/tr15/" target="unicode">
     * Unicode Normalization Forms</a>.
     * <p>
     * @see #setOption
     * @deprecated ICU 2.8. This option is no longer supported.
     */
    @Deprecated
    public static final int IGNORE_HANGUL = 0x0001;

    /**
     * Result values for quickCheck().
     * For details see Unicode Technical Report 15.
     * @stable ICU 2.8
     */
    public static final class QuickCheckResult{
        //private int resultValue;
        private QuickCheckResult(int value) {
            //resultValue=value;
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

    /**
     * Option bit for compare:
     * Case sensitively compare the strings
     * @stable ICU 2.8
     */
    public static final int FOLD_CASE_DEFAULT =  UCharacter.FOLD_CASE_DEFAULT;

    /**
     * Option bit for compare:
     * Both input strings are assumed to fulfill FCD conditions.
     * @stable ICU 2.8
     */
    public static final int INPUT_IS_FCD    =      0x20000;

    /**
     * Option bit for compare:
     * Perform case-insensitive comparison.
     * @stable ICU 2.8
     */
    public static final int COMPARE_IGNORE_CASE  =     0x10000;

    /**
     * Option bit for compare:
     * Compare strings in code point order instead of code unit order.
     * @stable ICU 2.8
     */
    public static final int COMPARE_CODE_POINT_ORDER = 0x8000;

    /**
     * Option value for case folding:
     * Use the modified set of mappings provided in CaseFolding.txt to handle dotted I
     * and dotless i appropriately for Turkic languages (tr, az).
     * @see UCharacter#FOLD_CASE_EXCLUDE_SPECIAL_I
     * @stable ICU 2.8
     */
    public static final int FOLD_CASE_EXCLUDE_SPECIAL_I = UCharacter.FOLD_CASE_EXCLUDE_SPECIAL_I;

    /**
     * Lowest-order bit number of compare() options bits corresponding to
     * normalization options bits.
     *
     * The options parameter for compare() uses most bits for
     * itself and for various comparison and folding flags.
     * The most significant bits, however, are shifted down and passed on
     * to the normalization implementation.
     * (That is, from compare(..., options, ...),
     * options&gt;&gt;COMPARE_NORM_OPTIONS_SHIFT will be passed on to the
     * internal normalization functions.)
     *
     * @see #compare
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static final int COMPARE_NORM_OPTIONS_SHIFT  = 20;

    //-------------------------------------------------------------------------
    // Iterator constructors
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
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public Normalizer(String str, Mode mode, int opt) {
        this.text = UCharacterIterator.getInstance(str);
        this.mode = mode;
        this.options=opt;
        norm2 = mode.getNormalizer2(opt);
        buffer = new StringBuilder();
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
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public Normalizer(CharacterIterator iter, Mode mode, int opt) {
        this.text = UCharacterIterator.getInstance((CharacterIterator)iter.clone());
        this.mode = mode;
        this.options = opt;
        norm2 = mode.getNormalizer2(opt);
        buffer = new StringBuilder();
    }

    /**
     * Creates a new <tt>Normalizer</tt> object for iterating over the
     * normalized form of the given text.
     * <p>
     * @param iter  The input text to be normalized.  The normalization
     *              will start at the beginning of the string.
     *
     * @param mode  The normalization mode.
     * @param options The normalization options, ORed together (0 for no options).
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public Normalizer(UCharacterIterator iter, Mode mode, int options) {
        try {
            this.text     = (UCharacterIterator)iter.clone();
            this.mode     = mode;
            this.options  = options;
            norm2 = mode.getNormalizer2(options);
            buffer = new StringBuilder();
        } catch (CloneNotSupportedException e) {
            throw new ICUCloneNotSupportedException(e);
        }
    }

    /**
     * Clones this <tt>Normalizer</tt> object.  All properties of this
     * object are duplicated in the new object, including the cloning of any
     * {@link CharacterIterator} that was passed in to the constructor
     * or to {@link #setText(CharacterIterator) setText}.
     * However, the text storage underlying
     * the <tt>CharacterIterator</tt> is not duplicated unless the
     * iterator's <tt>clone</tt> method does so.
     *
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    @Override
    public Object clone() {
        try {
            Normalizer copy = (Normalizer) super.clone();
            copy.text = (UCharacterIterator) text.clone();
            copy.mode = mode;
            copy.options = options;
            copy.norm2 = norm2;
            copy.buffer = new StringBuilder(buffer);
            copy.bufferPos = bufferPos;
            copy.currentIndex = currentIndex;
            copy.nextIndex = nextIndex;
            return copy;
        }
        catch (CloneNotSupportedException e) {
            throw new ICUCloneNotSupportedException(e);
        }
    }

    //--------------------------------------------------------------------------
    // Static Utility methods
    //--------------------------------------------------------------------------

    private static final Normalizer2 getComposeNormalizer2(boolean compat, int options) {
        return (compat ? NFKC : NFC).getNormalizer2(options);
    }
    private static final Normalizer2 getDecomposeNormalizer2(boolean compat, int options) {
        return (compat ? NFKD : NFD).getNormalizer2(options);
    }

    /**
     * Compose a string.
     * The string will be composed to according to the specified mode.
     * @param str        The string to compose.
     * @param compat     If true the string will be composed according to
     *                    NFKC rules and if false will be composed according to
     *                    NFC rules.
     * @return String    The composed string
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static String compose(String str, boolean compat) {
        return compose(str,compat,0);
    }

    /**
     * Compose a string.
     * The string will be composed to according to the specified mode.
     * @param str        The string to compose.
     * @param compat     If true the string will be composed according to
     *                    NFKC rules and if false will be composed according to
     *                    NFC rules.
     * @param options    The only recognized option is UNICODE_3_2
     * @return String    The composed string
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static String compose(String str, boolean compat, int options) {
        return getComposeNormalizer2(compat, options).normalize(str);
    }

    /**
     * Compose a string.
     * The string will be composed to according to the specified mode.
     * @param source The char array to compose.
     * @param target A char buffer to receive the normalized text.
     * @param compat If true the char array will be composed according to
     *                NFKC rules and if false will be composed according to
     *                NFC rules.
     * @param options The normalization options, ORed together (0 for no options).
     * @return int   The total buffer size needed;if greater than length of
     *                result, the output was truncated.
     * @exception IndexOutOfBoundsException if target.length is less than the
     *             required length
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static int compose(char[] source,char[] target, boolean compat, int options) {
        return compose(source, 0, source.length, target, 0, target.length, compat, options);
    }

    /**
     * Compose a string.
     * The string will be composed to according to the specified mode.
     * @param src       The char array to compose.
     * @param srcStart  Start index of the source
     * @param srcLimit  Limit index of the source
     * @param dest      The char buffer to fill in
     * @param destStart Start index of the destination buffer
     * @param destLimit End index of the destination buffer
     * @param compat If true the char array will be composed according to
     *                NFKC rules and if false will be composed according to
     *                NFC rules.
     * @param options The normalization options, ORed together (0 for no options).
     * @return int   The total buffer size needed;if greater than length of
     *                result, the output was truncated.
     * @exception IndexOutOfBoundsException if target.length is less than the
     *             required length
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static int compose(char[] src,int srcStart, int srcLimit,
                              char[] dest,int destStart, int destLimit,
                              boolean compat, int options) {
        CharBuffer srcBuffer = CharBuffer.wrap(src, srcStart, srcLimit - srcStart);
        CharsAppendable app = new CharsAppendable(dest, destStart, destLimit);
        getComposeNormalizer2(compat, options).normalize(srcBuffer, app);
        return app.length();
    }

    /**
     * Decompose a string.
     * The string will be decomposed to according to the specified mode.
     * @param str       The string to decompose.
     * @param compat    If true the string will be decomposed according to NFKD
     *                   rules and if false will be decomposed according to NFD
     *                   rules.
     * @return String   The decomposed string
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static String decompose(String str, boolean compat) {
        return decompose(str,compat,0);
    }

    /**
     * Decompose a string.
     * The string will be decomposed to according to the specified mode.
     * @param str     The string to decompose.
     * @param compat  If true the string will be decomposed according to NFKD
     *                 rules and if false will be decomposed according to NFD
     *                 rules.
     * @param options The normalization options, ORed together (0 for no options).
     * @return String The decomposed string
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static String decompose(String str, boolean compat, int options) {
        return getDecomposeNormalizer2(compat, options).normalize(str);
    }

    /**
     * Decompose a string.
     * The string will be decomposed to according to the specified mode.
     * @param source The char array to decompose.
     * @param target A char buffer to receive the normalized text.
     * @param compat If true the char array will be decomposed according to NFKD
     *                rules and if false will be decomposed according to
     *                NFD rules.
     * @return int   The total buffer size needed;if greater than length of
     *                result,the output was truncated.
     * @param options The normalization options, ORed together (0 for no options).
     * @exception IndexOutOfBoundsException if the target capacity is less than
     *             the required length
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static int decompose(char[] source,char[] target, boolean compat, int options) {
        return decompose(source, 0, source.length, target, 0, target.length, compat, options);
    }

    /**
     * Decompose a string.
     * The string will be decomposed to according to the specified mode.
     * @param src       The char array to compose.
     * @param srcStart  Start index of the source
     * @param srcLimit  Limit index of the source
     * @param dest      The char buffer to fill in
     * @param destStart Start index of the destination buffer
     * @param destLimit End index of the destination buffer
     * @param compat If true the char array will be decomposed according to NFKD
     *                rules and if false will be decomposed according to
     *                NFD rules.
     * @param options The normalization options, ORed together (0 for no options).
     * @return int   The total buffer size needed;if greater than length of
     *                result,the output was truncated.
     * @exception IndexOutOfBoundsException if the target capacity is less than
     *             the required length
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static int decompose(char[] src,int srcStart, int srcLimit,
                                char[] dest,int destStart, int destLimit,
                                boolean compat, int options) {
        CharBuffer srcBuffer = CharBuffer.wrap(src, srcStart, srcLimit - srcStart);
        CharsAppendable app = new CharsAppendable(dest, destStart, destLimit);
        getDecomposeNormalizer2(compat, options).normalize(srcBuffer, app);
        return app.length();
    }

    /**
     * Normalizes a <tt>String</tt> using the given normalization operation.
     * <p>
     * The <tt>options</tt> parameter specifies which optional
     * <tt>Normalizer</tt> features are to be enabled for this operation.
     * Currently the only available option is {@link #UNICODE_3_2}.
     * If you want the default behavior corresponding to one of the standard
     * Unicode Normalization Forms, use 0 for this argument.
     * <p>
     * @param str       the input string to be normalized.
     * @param mode      the normalization mode
     * @param options   the optional features to be enabled.
     * @return String   the normalized string
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static String normalize(String str, Mode mode, int options) {
        return mode.getNormalizer2(options).normalize(str);
    }

    /**
     * Normalize a string.
     * The string will be normalized according to the specified normalization
     * mode and options.
     * @param src        The string to normalize.
     * @param mode       The normalization mode; one of Normalizer.NONE,
     *                    Normalizer.NFD, Normalizer.NFC, Normalizer.NFKC,
     *                    Normalizer.NFKD, Normalizer.DEFAULT
     * @return the normalized string
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static String normalize(String src,Mode mode) {
        return normalize(src, mode, 0);
    }
    /**
     * Normalize a string.
     * The string will be normalized according to the specified normalization
     * mode and options.
     * @param source The char array to normalize.
     * @param target A char buffer to receive the normalized text.
     * @param mode   The normalization mode; one of Normalizer.NONE,
     *                Normalizer.NFD, Normalizer.NFC, Normalizer.NFKC,
     *                Normalizer.NFKD, Normalizer.DEFAULT
     * @param options The normalization options, ORed together (0 for no options).
     * @return int   The total buffer size needed;if greater than length of
     *                result, the output was truncated.
     * @exception    IndexOutOfBoundsException if the target capacity is less
     *                than the required length
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static int normalize(char[] source,char[] target, Mode  mode, int options) {
        return normalize(source,0,source.length,target,0,target.length,mode, options);
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
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static int normalize(char[] src,int srcStart, int srcLimit,
                                char[] dest,int destStart, int destLimit,
                                Mode  mode, int options) {
        CharBuffer srcBuffer = CharBuffer.wrap(src, srcStart, srcLimit - srcStart);
        CharsAppendable app = new CharsAppendable(dest, destStart, destLimit);
        mode.getNormalizer2(options).normalize(srcBuffer, app);
        return app.length();
    }

    /**
     * Normalize a codepoint according to the given mode
     * @param char32    The input string to be normalized.
     * @param mode      The normalization mode
     * @param options   Options for use with exclusion set and tailored Normalization
     *                                   The only option that is currently recognized is UNICODE_3_2
     * @return String   The normalized string
     * @see #UNICODE_3_2
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static String normalize(int char32, Mode mode, int options) {
        if(mode == NFD && options == 0) {
            String decomposition = Normalizer2.getNFCInstance().getDecomposition(char32);
            if(decomposition == null) {
                decomposition = UTF16.valueOf(char32);
            }
            return decomposition;
        }
        return normalize(UTF16.valueOf(char32), mode, options);
    }

    /**
     * Convenience method to normalize a codepoint according to the given mode
     * @param char32    The input string to be normalized.
     * @param mode      The normalization mode
     * @return String   The normalized string
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static String normalize(int char32, Mode mode) {
        return normalize(char32, mode, 0);
    }

    /**
     * Convenience method.
     *
     * @param source   string for determining if it is in a normalized format
     * @param mode     normalization format (Normalizer.NFC,Normalizer.NFD,
     *                  Normalizer.NFKC,Normalizer.NFKD)
     * @return         Return code to specify if the text is normalized or not
     *                     (Normalizer.YES, Normalizer.NO or Normalizer.MAYBE)
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static QuickCheckResult quickCheck(String source, Mode mode) {
        return quickCheck(source, mode, 0);
    }

    /**
     * Performing quick check on a string, to quickly determine if the string is
     * in a particular normalization format.
     * Three types of result can be returned Normalizer.YES, Normalizer.NO or
     * Normalizer.MAYBE. Result Normalizer.YES indicates that the argument
     * string is in the desired normalized format, Normalizer.NO determines that
     * argument string is not in the desired normalized format. A
     * Normalizer.MAYBE result indicates that a more thorough check is required,
     * the user may have to put the string in its normalized form and compare
     * the results.
     *
     * @param source   string for determining if it is in a normalized format
     * @param mode     normalization format (Normalizer.NFC,Normalizer.NFD,
     *                  Normalizer.NFKC,Normalizer.NFKD)
     * @param options   Options for use with exclusion set and tailored Normalization
     *                                   The only option that is currently recognized is UNICODE_3_2
     * @return         Return code to specify if the text is normalized or not
     *                     (Normalizer.YES, Normalizer.NO or Normalizer.MAYBE)
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static QuickCheckResult quickCheck(String source, Mode mode, int options) {
        return mode.getNormalizer2(options).quickCheck(source);
    }

    /**
     * Convenience method.
     *
     * @param source Array of characters for determining if it is in a
     *                normalized format
     * @param mode   normalization format (Normalizer.NFC,Normalizer.NFD,
     *                Normalizer.NFKC,Normalizer.NFKD)
     * @param options   Options for use with exclusion set and tailored Normalization
     *                                   The only option that is currently recognized is UNICODE_3_2
     * @return       Return code to specify if the text is normalized or not
     *                (Normalizer.YES, Normalizer.NO or Normalizer.MAYBE)
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static QuickCheckResult quickCheck(char[] source, Mode mode, int options) {
        return quickCheck(source, 0, source.length, mode, options);
    }

    /**
     * Performing quick check on a string, to quickly determine if the string is
     * in a particular normalization format.
     * Three types of result can be returned Normalizer.YES, Normalizer.NO or
     * Normalizer.MAYBE. Result Normalizer.YES indicates that the argument
     * string is in the desired normalized format, Normalizer.NO determines that
     * argument string is not in the desired normalized format. A
     * Normalizer.MAYBE result indicates that a more thorough check is required,
     * the user may have to put the string in its normalized form and compare
     * the results.
     *
     * @param source    string for determining if it is in a normalized format
     * @param start     the start index of the source
     * @param limit     the limit index of the source it is equal to the length
     * @param mode      normalization format (Normalizer.NFC,Normalizer.NFD,
     *                   Normalizer.NFKC,Normalizer.NFKD)
     * @param options   Options for use with exclusion set and tailored Normalization
     *                                   The only option that is currently recognized is UNICODE_3_2
     * @return          Return code to specify if the text is normalized or not
     *                   (Normalizer.YES, Normalizer.NO or
     *                   Normalizer.MAYBE)
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static QuickCheckResult quickCheck(char[] source,int start,
                                              int limit, Mode mode,int options) {
        CharBuffer srcBuffer = CharBuffer.wrap(source, start, limit - start);
        return mode.getNormalizer2(options).quickCheck(srcBuffer);
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
     * @param src       The input array of characters to be checked to see if
     *                   it is normalized
     * @param start     The strart index in the source
     * @param limit     The limit index in the source
     * @param mode      the normalization mode
     * @param options   Options for use with exclusion set and tailored Normalization
     *                                   The only option that is currently recognized is UNICODE_3_2
     * @return Boolean value indicating whether the source string is in the
     *         "mode" normalization form
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static boolean isNormalized(char[] src,int start,
                                       int limit, Mode mode,
                                       int options) {
        CharBuffer srcBuffer = CharBuffer.wrap(src, start, limit - start);
        return mode.getNormalizer2(options).isNormalized(srcBuffer);
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
     * @param str       the input string to be checked to see if it is
     *                   normalized
     * @param mode      the normalization mode
     * @param options   Options for use with exclusion set and tailored Normalization
     *                  The only option that is currently recognized is UNICODE_3_2
     * @see #isNormalized
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static boolean isNormalized(String str, Mode mode, int options) {
        return mode.getNormalizer2(options).isNormalized(str);
    }

    /**
     * Convenience Method
     * @param char32    the input code point to be checked to see if it is
     *                   normalized
     * @param mode      the normalization mode
     * @param options   Options for use with exclusion set and tailored Normalization
     *                  The only option that is currently recognized is UNICODE_3_2
     *
     * @see #isNormalized
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static boolean isNormalized(int char32, Mode mode,int options) {
        return isNormalized(UTF16.valueOf(char32), mode, options);
    }

    /**
     * Compare two strings for canonical equivalence.
     * Further options include case-insensitive comparison and
     * code point order (as opposed to code unit order).
     *
     * Canonical equivalence between two strings is defined as their normalized
     * forms (NFD or NFC) being identical.
     * This function compares strings incrementally instead of normalizing
     * (and optionally case-folding) both strings entirely,
     * improving performance significantly.
     *
     * Bulk normalization is only necessary if the strings do not fulfill the
     * FCD conditions. Only in this case, and only if the strings are relatively
     * long, is memory allocated temporarily.
     * For FCD strings and short non-FCD strings there is no memory allocation.
     *
     * Semantically, this is equivalent to
     *   strcmp[CodePointOrder](foldCase(NFD(s1)), foldCase(NFD(s2)))
     * where code point order and foldCase are all optional.
     *
     * @param s1        First source character array.
     * @param s1Start   start index of source
     * @param s1Limit   limit of the source
     *
     * @param s2        Second source character array.
     * @param s2Start   start index of the source
     * @param s2Limit   limit of the source
     *
     * @param options A bit set of options:
     *   - FOLD_CASE_DEFAULT or 0 is used for default options:
     *     Case-sensitive comparison in code unit order, and the input strings
     *     are quick-checked for FCD.
     *
     *   - INPUT_IS_FCD
     *     Set if the caller knows that both s1 and s2 fulfill the FCD
     *     conditions.If not set, the function will quickCheck for FCD
     *     and normalize if necessary.
     *
     *   - COMPARE_CODE_POINT_ORDER
     *     Set to choose code point order instead of code unit order
     *
     *   - COMPARE_IGNORE_CASE
     *     Set to compare strings case-insensitively using case folding,
     *     instead of case-sensitively.
     *     If set, then the following case folding options are used.
     *
     *
     * @return &lt;0 or 0 or &gt;0 as usual for string comparisons
     *
     * @see #normalize
     * @see #FCD
     * @stable ICU 2.8
     */
    public static int compare(char[] s1, int s1Start, int s1Limit,
                              char[] s2, int s2Start, int s2Limit,
                              int options) {
        if( s1==null || s1Start<0 || s1Limit<0 ||
            s2==null || s2Start<0 || s2Limit<0 ||
            s1Limit<s1Start || s2Limit<s2Start
        ) {
            throw new IllegalArgumentException();
        }
        return internalCompare(CharBuffer.wrap(s1, s1Start, s1Limit-s1Start),
                               CharBuffer.wrap(s2, s2Start, s2Limit-s2Start),
                               options);
    }

    /**
     * Compare two strings for canonical equivalence.
     * Further options include case-insensitive comparison and
     * code point order (as opposed to code unit order).
     *
     * Canonical equivalence between two strings is defined as their normalized
     * forms (NFD or NFC) being identical.
     * This function compares strings incrementally instead of normalizing
     * (and optionally case-folding) both strings entirely,
     * improving performance significantly.
     *
     * Bulk normalization is only necessary if the strings do not fulfill the
     * FCD conditions. Only in this case, and only if the strings are relatively
     * long, is memory allocated temporarily.
     * For FCD strings and short non-FCD strings there is no memory allocation.
     *
     * Semantically, this is equivalent to
     *   strcmp[CodePointOrder](foldCase(NFD(s1)), foldCase(NFD(s2)))
     * where code point order and foldCase are all optional.
     *
     * @param s1 First source string.
     * @param s2 Second source string.
     *
     * @param options A bit set of options:
     *   - FOLD_CASE_DEFAULT or 0 is used for default options:
     *     Case-sensitive comparison in code unit order, and the input strings
     *     are quick-checked for FCD.
     *
     *   - INPUT_IS_FCD
     *     Set if the caller knows that both s1 and s2 fulfill the FCD
     *     conditions. If not set, the function will quickCheck for FCD
     *     and normalize if necessary.
     *
     *   - COMPARE_CODE_POINT_ORDER
     *     Set to choose code point order instead of code unit order
     *
     *   - COMPARE_IGNORE_CASE
     *     Set to compare strings case-insensitively using case folding,
     *     instead of case-sensitively.
     *     If set, then the following case folding options are used.
     *
     * @return &lt;0 or 0 or &gt;0 as usual for string comparisons
     *
     * @see #normalize
     * @see #FCD
     * @stable ICU 2.8
     */
    public static int compare(String s1, String s2, int options) {
        return internalCompare(s1, s2, options);
    }

    /**
     * Compare two strings for canonical equivalence.
     * Further options include case-insensitive comparison and
     * code point order (as opposed to code unit order).
     * Convenience method.
     *
     * @param s1 First source string.
     * @param s2 Second source string.
     *
     * @param options A bit set of options:
     *   - FOLD_CASE_DEFAULT or 0 is used for default options:
     *     Case-sensitive comparison in code unit order, and the input strings
     *     are quick-checked for FCD.
     *
     *   - INPUT_IS_FCD
     *     Set if the caller knows that both s1 and s2 fulfill the FCD
     *     conditions. If not set, the function will quickCheck for FCD
     *     and normalize if necessary.
     *
     *   - COMPARE_CODE_POINT_ORDER
     *     Set to choose code point order instead of code unit order
     *
     *   - COMPARE_IGNORE_CASE
     *     Set to compare strings case-insensitively using case folding,
     *     instead of case-sensitively.
     *     If set, then the following case folding options are used.
     *
     * @return &lt;0 or 0 or &gt;0 as usual for string comparisons
     *
     * @see #normalize
     * @see #FCD
     * @stable ICU 2.8
     */
    public static int compare(char[] s1, char[] s2, int options) {
        return internalCompare(CharBuffer.wrap(s1), CharBuffer.wrap(s2), options);
    }

    /**
     * Convenience method that can have faster implementation
     * by not allocating buffers.
     * @param char32a    the first code point to be checked against the
     * @param char32b    the second code point
     * @param options    A bit set of options
     * @stable ICU 2.8
     */
    public static int compare(int char32a, int char32b, int options) {
        return internalCompare(UTF16.valueOf(char32a), UTF16.valueOf(char32b), options|INPUT_IS_FCD);
    }

    /**
     * Convenience method that can have faster implementation
     * by not allocating buffers.
     * @param char32a   the first code point to be checked against
     * @param str2      the second string
     * @param options   A bit set of options
     * @stable ICU 2.8
     */
    public static int compare(int char32a, String str2, int options) {
        return internalCompare(UTF16.valueOf(char32a), str2, options);
    }

    /* Concatenation of normalized strings --------------------------------- */
    /**
     * Concatenate normalized strings, making sure that the result is normalized
     * as well.
     *
     * If both the left and the right strings are in
     * the normalization form according to "mode",
     * then the result will be
     *
     * <code>
     *     dest=normalize(left+right, mode)
     * </code>
     *
     * With the input strings already being normalized,
     * this function will use next() and previous()
     * to find the adjacent end pieces of the input strings.
     * Only the concatenation of these end pieces will be normalized and
     * then concatenated with the remaining parts of the input strings.
     *
     * It is allowed to have dest==left to avoid copying the entire left string.
     *
     * @param left Left source array, may be same as dest.
     * @param leftStart start in the left array.
     * @param leftLimit limit in the left array (==length)
     * @param right Right source array.
     * @param rightStart start in the right array.
     * @param rightLimit limit in the right array (==length)
     * @param dest The output buffer; can be null if destStart==destLimit==0
     *              for pure preflighting.
     * @param destStart start in the destination array
     * @param destLimit limit in the destination array (==length)
     * @param mode The normalization mode.
     * @param options The normalization options, ORed together (0 for no options).
     * @return Length of output (number of chars) when successful or
     *          IndexOutOfBoundsException
     * @exception IndexOutOfBoundsException whose message has the string
     *             representation of destination capacity required.
     * @see #normalize
     * @see #next
     * @see #previous
     * @exception IndexOutOfBoundsException if target capacity is less than the
     *             required length
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static int concatenate(char[] left,  int leftStart,  int leftLimit,
                                  char[] right, int rightStart, int rightLimit,
                                  char[] dest,  int destStart,  int destLimit,
                                  Normalizer.Mode mode, int options) {
        if(dest == null) {
            throw new IllegalArgumentException();
        }

        /* check for overlapping right and destination */
        if (right == dest && rightStart < destLimit && destStart < rightLimit) {
            throw new IllegalArgumentException("overlapping right and dst ranges");
        }

        /* allow left==dest */
        StringBuilder destBuilder=new StringBuilder(leftLimit-leftStart+rightLimit-rightStart+16);
        destBuilder.append(left, leftStart, leftLimit-leftStart);
        CharBuffer rightBuffer=CharBuffer.wrap(right, rightStart, rightLimit-rightStart);
        mode.getNormalizer2(options).append(destBuilder, rightBuffer);
        int destLength=destBuilder.length();
        if(destLength<=(destLimit-destStart)) {
            destBuilder.getChars(0, destLength, dest, destStart);
            return destLength;
        } else {
            throw new IndexOutOfBoundsException(Integer.toString(destLength));
        }
    }

    /**
     * Concatenate normalized strings, making sure that the result is normalized
     * as well.
     *
     * If both the left and the right strings are in
     * the normalization form according to "mode",
     * then the result will be
     *
     * <code>
     *     dest=normalize(left+right, mode)
     * </code>
     *
     * For details see concatenate
     *
     * @param left Left source string.
     * @param right Right source string.
     * @param mode The normalization mode.
     * @param options The normalization options, ORed together (0 for no options).
     * @return result
     *
     * @see #concatenate
     * @see #normalize
     * @see #next
     * @see #previous
     * @see #concatenate
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static String concatenate(char[] left, char[] right,Mode mode, int options) {
        StringBuilder dest=new StringBuilder(left.length+right.length+16).append(left);
        return mode.getNormalizer2(options).append(dest, CharBuffer.wrap(right)).toString();
    }

    /**
     * Concatenate normalized strings, making sure that the result is normalized
     * as well.
     *
     * If both the left and the right strings are in
     * the normalization form according to "mode",
     * then the result will be
     *
     * <code>
     *     dest=normalize(left+right, mode)
     * </code>
     *
     * With the input strings already being normalized,
     * this function will use next() and previous()
     * to find the adjacent end pieces of the input strings.
     * Only the concatenation of these end pieces will be normalized and
     * then concatenated with the remaining parts of the input strings.
     *
     * @param left Left source string.
     * @param right Right source string.
     * @param mode The normalization mode.
     * @param options The normalization options, ORed together (0 for no options).
     * @return result
     *
     * @see #concatenate
     * @see #normalize
     * @see #next
     * @see #previous
     * @see #concatenate
     * @deprecated ICU 56 Use {@link Normalizer2} instead.
     */
    @Deprecated
    public static String concatenate(String left, String right, Mode mode, int options) {
        StringBuilder dest=new StringBuilder(left.length()+right.length()+16).append(left);
        return mode.getNormalizer2(options).append(dest, right).toString();
    }

    /**
     * Gets the FC_NFKC closure value.
     * @param c The code point whose closure value is to be retrieved
     * @param dest The char array to receive the closure value
     * @return the length of the closure value; 0 if there is none
     * @deprecated ICU 56
     */
    @Deprecated
    public static int getFC_NFKC_Closure(int c,char[] dest) {
        String closure=getFC_NFKC_Closure(c);
        int length=closure.length();
        if(length!=0 && dest!=null && length<=dest.length) {
            closure.getChars(0, length, dest, 0);
        }
        return length;
    }
    /**
     * Gets the FC_NFKC closure value.
     * @param c The code point whose closure value is to be retrieved
     * @return String representation of the closure value; "" if there is none
     * @deprecated ICU 56
     */
    @Deprecated
    public static String getFC_NFKC_Closure(int c) {
        // Compute the FC_NFKC_Closure on the fly:
        // We have the API for complete coverage of Unicode properties, although
        // this value by itself is not useful via API.
        // (What could be useful is a custom normalization table that combines
        // case folding and NFKC.)
        // For the derivation, see Unicode's DerivedNormalizationProps.txt.
        Normalizer2 nfkc=NFKCModeImpl.INSTANCE.normalizer2;
        UCaseProps csp=UCaseProps.INSTANCE;
        // first: b = NFKC(Fold(a))
        StringBuilder folded=new StringBuilder();
        int folded1Length=csp.toFullFolding(c, folded, 0);
        if(folded1Length<0) {
            Normalizer2Impl nfkcImpl=((Norm2AllModes.Normalizer2WithImpl)nfkc).impl;
            if(nfkcImpl.getCompQuickCheck(nfkcImpl.getNorm16(c))!=0) {
                return "";  // c does not change at all under CaseFolding+NFKC
            }
            folded.appendCodePoint(c);
        } else {
            if(folded1Length>UCaseProps.MAX_STRING_LENGTH) {
                folded.appendCodePoint(folded1Length);
            }
        }
        String kc1=nfkc.normalize(folded);
        // second: c = NFKC(Fold(b))
        String kc2=nfkc.normalize(UCharacter.foldCase(kc1, 0));
        // if (c != b) add the mapping from a to c
        if(kc1.equals(kc2)) {
            return "";
        } else {
            return kc2;
        }
    }

    //-------------------------------------------------------------------------
    // Iteration API
    //-------------------------------------------------------------------------

    /**
     * Return the current character in the normalized text.
     * @return The codepoint as an int
     * @deprecated ICU 56
     */
    @Deprecated
    public int current() {
        if(bufferPos<buffer.length() || nextNormalize()) {
            return buffer.codePointAt(bufferPos);
        } else {
            return DONE;
        }
    }

    /**
     * Return the next character in the normalized text and advance
     * the iteration position by one.  If the end
     * of the text has already been reached, {@link #DONE} is returned.
     * @return The codepoint as an int
     * @deprecated ICU 56
     */
    @Deprecated
    public int next() {
        if(bufferPos<buffer.length() ||  nextNormalize()) {
            int c=buffer.codePointAt(bufferPos);
            bufferPos+=Character.charCount(c);
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
     * @deprecated ICU 56
     */
    @Deprecated
    public int previous() {
        if(bufferPos>0 || previousNormalize()) {
            int c=buffer.codePointBefore(bufferPos);
            bufferPos-=Character.charCount(c);
            return c;
        } else {
            return DONE;
        }
    }

    /**
     * Reset the index to the beginning of the text.
     * This is equivalent to setIndexOnly(startIndex)).
     * @deprecated ICU 56
     */
    @Deprecated
    public void reset() {
        text.setToStart();
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
     * @deprecated ICU 56
     */
    @Deprecated
    public void setIndexOnly(int index) {
        text.setIndex(index);  // validates index
        currentIndex=nextIndex=index;
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
     * @param index the desired index in the input text.
     *
     * @return   the first normalized character that is the result of iterating
     *            forward starting at the given index.
     *
     * @throws IllegalArgumentException if the given index is less than
     *          {@link #getBeginIndex} or greater than {@link #getEndIndex}.
     * @deprecated ICU 3.2
     * @obsolete ICU 3.2
     */
    @Deprecated
     ///CLOVER:OFF
     public int setIndex(int index) {
         setIndexOnly(index);
         return current();
     }
     ///CLOVER:ON
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
     * Return the first character in the normalized text.  This resets
     * the <tt>Normalizer's</tt> position to the beginning of the text.
     * @return The codepoint as an int
     * @deprecated ICU 56
     */
    @Deprecated
    public int first() {
        reset();
        return next();
    }

    /**
     * Return the last character in the normalized text.  This resets
     * the <tt>Normalizer's</tt> position to be just before the
     * the input text corresponding to that normalized character.
     * @return The codepoint as an int
     * @deprecated ICU 56
     */
    @Deprecated
    public int last() {
        text.setToLimit();
        currentIndex=nextIndex=text.getIndex();
        clearBuffer();
        return previous();
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
     * @deprecated ICU 56
     */
    @Deprecated
    public int getIndex() {
        if(bufferPos<buffer.length()) {
            return currentIndex;
        } else {
            return nextIndex;
        }
    }

    /**
     * Retrieve the index of the start of the input text. This is the begin
     * index of the <tt>CharacterIterator</tt> or the start (i.e. 0) of the
     * <tt>String</tt> over which this <tt>Normalizer</tt> is iterating
     * @return The current iteration position
     * @deprecated ICU 56
     */
    @Deprecated
    public int startIndex() {
        return 0;
    }

    /**
     * Retrieve the index of the end of the input text.  This is the end index
     * of the <tt>CharacterIterator</tt> or the length of the <tt>String</tt>
     * over which this <tt>Normalizer</tt> is iterating
     * @return The current iteration position
     * @deprecated ICU 56
     */
    @Deprecated
    public int endIndex() {
        return text.getLength();
    }

    //-------------------------------------------------------------------------
    // Iterator attributes
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
     *  <li>{@link #NFC}    - Unicode canonical decompositiion
     *                        followed by canonical composition.
     *  <li>{@link #NFKC}   - Unicode compatibility decompositiion
     *                        followed by canonical composition.
     *  <li>{@link #NFD}    - Unicode canonical decomposition
     *  <li>{@link #NFKD}   - Unicode compatibility decomposition.
     *  <li>{@link #NONE}   - Do nothing but return characters
     *                        from the underlying input text.
     * </ul>
     *
     * @see #getMode
     * @deprecated ICU 56
     */
    @Deprecated
    public void setMode(Mode newMode) {
        mode = newMode;
        norm2 = mode.getNormalizer2(options);
    }
    /**
     * Return the basic operation performed by this <tt>Normalizer</tt>
     *
     * @see #setMode
     * @deprecated ICU 56
     */
    @Deprecated
    public Mode getMode() {
        return mode;
    }
    /**
     * Set options that affect this <tt>Normalizer</tt>'s operation.
     * Options do not change the basic composition or decomposition operation
     * that is being performed , but they control whether
     * certain optional portions of the operation are done.
     * Currently the only available option is:
     *
     * <ul>
     *   <li>{@link #UNICODE_3_2} - Use Normalization conforming to Unicode version 3.2.
     * </ul>
     *
     * @param   option  the option whose value is to be set.
     * @param   value   the new setting for the option.  Use <tt>true</tt> to
     *                  turn the option on and <tt>false</tt> to turn it off.
     *
     * @see #getOption
     * @deprecated ICU 56
     */
    @Deprecated
    public void setOption(int option,boolean value) {
        if (value) {
            options |= option;
        } else {
            options &= (~option);
        }
        norm2 = mode.getNormalizer2(options);
    }

    /**
     * Determine whether an option is turned on or off.
     * <p>
     * @see #setOption
     * @deprecated ICU 56
     */
    @Deprecated
    public int getOption(int option) {
        if((options & option)!=0) {
            return 1 ;
        } else {
            return 0;
        }
    }

    /**
     * Gets the underlying text storage
     * @param fillIn the char buffer to fill the UTF-16 units.
     *         The length of the buffer should be equal to the length of the
     *         underlying text storage
     * @throws IndexOutOfBoundsException If the index passed for the array is invalid.
     * @see   #getLength
     * @deprecated ICU 56
     */
    @Deprecated
    public int getText(char[] fillIn) {
        return text.getText(fillIn);
    }

    /**
     * Gets the length of underlying text storage
     * @return the length
     * @deprecated ICU 56
     */
    @Deprecated
    public int getLength() {
        return text.getLength();
    }

    /**
     * Returns the text under iteration as a string
     * @return a copy of the text under iteration.
     * @deprecated ICU 56
     */
    @Deprecated
    public String getText() {
        return text.getText();
    }

    /**
     * Set the input text over which this <tt>Normalizer</tt> will iterate.
     * The iteration position is set to the beginning of the input text.
     * @param newText   The new string to be normalized.
     * @deprecated ICU 56
     */
    @Deprecated
    public void setText(StringBuffer newText) {
        UCharacterIterator newIter = UCharacterIterator.getInstance(newText);
        if (newIter == null) {
            throw new IllegalStateException("Could not create a new UCharacterIterator");
        }
        text = newIter;
        reset();
    }

    /**
     * Set the input text over which this <tt>Normalizer</tt> will iterate.
     * The iteration position is set to the beginning of the input text.
     * @param newText   The new string to be normalized.
     * @deprecated ICU 56
     */
    @Deprecated
    public void setText(char[] newText) {
        UCharacterIterator newIter = UCharacterIterator.getInstance(newText);
        if (newIter == null) {
            throw new IllegalStateException("Could not create a new UCharacterIterator");
        }
        text = newIter;
        reset();
    }

    /**
     * Set the input text over which this <tt>Normalizer</tt> will iterate.
     * The iteration position is set to the beginning of the input text.
     * @param newText   The new string to be normalized.
     * @deprecated ICU 56
     */
    @Deprecated
    public void setText(String newText) {
        UCharacterIterator newIter = UCharacterIterator.getInstance(newText);
        if (newIter == null) {
            throw new IllegalStateException("Could not create a new UCharacterIterator");
        }
        text = newIter;
        reset();
    }

    /**
     * Set the input text over which this <tt>Normalizer</tt> will iterate.
     * The iteration position is set to the beginning of the input text.
     * @param newText   The new string to be normalized.
     * @deprecated ICU 56
     */
    @Deprecated
    public void setText(CharacterIterator newText) {
        UCharacterIterator newIter = UCharacterIterator.getInstance(newText);
        if (newIter == null) {
            throw new IllegalStateException("Could not create a new UCharacterIterator");
        }
        text = newIter;
        reset();
    }

    /**
     * Set the input text over which this <tt>Normalizer</tt> will iterate.
     * The iteration position is set to the beginning of the string.
     * @param newText   The new string to be normalized.
     * @deprecated ICU 56
     */
    @Deprecated
    public void setText(UCharacterIterator newText) {
        try{
            UCharacterIterator newIter = (UCharacterIterator)newText.clone();
            if (newIter == null) {
                throw new IllegalStateException("Could not create a new UCharacterIterator");
            }
            text = newIter;
            reset();
        }catch(CloneNotSupportedException e) {
            throw new ICUCloneNotSupportedException("Could not clone the UCharacterIterator", e);
        }
    }

    private void clearBuffer() {
        buffer.setLength(0);
        bufferPos=0;
    }

    private boolean nextNormalize() {
        clearBuffer();
        currentIndex=nextIndex;
        text.setIndex(nextIndex);
        // Skip at least one character so we make progress.
        int c=text.nextCodePoint();
        if(c<0) {
            return false;
        }
        StringBuilder segment=new StringBuilder().appendCodePoint(c);
        while((c=text.nextCodePoint())>=0) {
            if(norm2.hasBoundaryBefore(c)) {
                text.moveCodePointIndex(-1);
                break;
            }
            segment.appendCodePoint(c);
        }
        nextIndex=text.getIndex();
        norm2.normalize(segment, buffer);
        return buffer.length()!=0;
    }

    private boolean previousNormalize() {
        clearBuffer();
        nextIndex=currentIndex;
        text.setIndex(currentIndex);
        StringBuilder segment=new StringBuilder();
        int c;
        while((c=text.previousCodePoint())>=0) {
            if(c<=0xffff) {
                segment.insert(0, (char)c);
            } else {
                segment.insert(0, Character.toChars(c));
            }
            if(norm2.hasBoundaryBefore(c)) {
                break;
            }
        }
        currentIndex=text.getIndex();
        norm2.normalize(segment, buffer);
        bufferPos=buffer.length();
        return buffer.length()!=0;
    }

    /* compare canonically equivalent ------------------------------------------- */

    // TODO: Broaden the public compare(String, String, options) API like this. Ticket #7407
    private static int internalCompare(CharSequence s1, CharSequence s2, int options) {
        int normOptions=options>>>COMPARE_NORM_OPTIONS_SHIFT;
        options|= COMPARE_EQUIV;

        /*
         * UAX #21 Case Mappings, as fixed for Unicode version 4
         * (see Jitterbug 2021), defines a canonical caseless match as
         *
         * A string X is a canonical caseless match
         * for a string Y if and only if
         * NFD(toCasefold(NFD(X))) = NFD(toCasefold(NFD(Y)))
         *
         * For better performance, we check for FCD (or let the caller tell us that
         * both strings are in FCD) for the inner normalization.
         * BasicNormalizerTest::FindFoldFCDExceptions() makes sure that
         * case-folding preserves the FCD-ness of a string.
         * The outer normalization is then only performed by NormalizerImpl.cmpEquivFold()
         * when there is a difference.
         *
         * Exception: When using the Turkic case-folding option, we do perform
         * full NFD first. This is because in the Turkic case precomposed characters
         * with 0049 capital I or 0069 small i fold differently whether they
         * are first decomposed or not, so an FCD check - a check only for
         * canonical order - is not sufficient.
         */
        if((options&INPUT_IS_FCD)==0 || (options&FOLD_CASE_EXCLUDE_SPECIAL_I)!=0) {
            Normalizer2 n2;
            if((options&FOLD_CASE_EXCLUDE_SPECIAL_I)!=0) {
                n2=NFD.getNormalizer2(normOptions);
            } else {
                n2=FCD.getNormalizer2(normOptions);
            }

            // check if s1 and/or s2 fulfill the FCD conditions
            int spanQCYes1=n2.spanQuickCheckYes(s1);
            int spanQCYes2=n2.spanQuickCheckYes(s2);

            /*
             * ICU 2.4 had a further optimization:
             * If both strings were not in FCD, then they were both NFD'ed,
             * and the COMPARE_EQUIV option was turned off.
             * It is not entirely clear that this is valid with the current
             * definition of the canonical caseless match.
             * Therefore, ICU 2.6 removes that optimization.
             */

            if(spanQCYes1<s1.length()) {
                StringBuilder fcd1=new StringBuilder(s1.length()+16).append(s1, 0, spanQCYes1);
                s1=n2.normalizeSecondAndAppend(fcd1, s1.subSequence(spanQCYes1, s1.length()));
            }
            if(spanQCYes2<s2.length()) {
                StringBuilder fcd2=new StringBuilder(s2.length()+16).append(s2, 0, spanQCYes2);
                s2=n2.normalizeSecondAndAppend(fcd2, s2.subSequence(spanQCYes2, s2.length()));
            }
        }

        return cmpEquivFold(s1, s2, options);
    }

    /*
     * Compare two strings for canonical equivalence.
     * Further options include case-insensitive comparison and
     * code point order (as opposed to code unit order).
     *
     * In this function, canonical equivalence is optional as well.
     * If canonical equivalence is tested, then both strings must fulfill
     * the FCD check.
     *
     * Semantically, this is equivalent to
     *   strcmp[CodePointOrder](NFD(foldCase(s1)), NFD(foldCase(s2)))
     * where code point order, NFD and foldCase are all optional.
     *
     * String comparisons almost always yield results before processing both strings
     * completely.
     * They are generally more efficient working incrementally instead of
     * performing the sub-processing (strlen, normalization, case-folding)
     * on the entire strings first.
     *
     * It is also unnecessary to not normalize identical characters.
     *
     * This function works in principle as follows:
     *
     * loop {
     *   get one code unit c1 from s1 (-1 if end of source)
     *   get one code unit c2 from s2 (-1 if end of source)
     *
     *   if(either string finished) {
     *     return result;
     *   }
     *   if(c1==c2) {
     *     continue;
     *   }
     *
     *   // c1!=c2
     *   try to decompose/case-fold c1/c2, and continue if one does;
     *
     *   // still c1!=c2 and neither decomposes/case-folds, return result
     *   return c1-c2;
     * }
     *
     * When a character decomposes, then the pointer for that source changes to
     * the decomposition, pushing the previous pointer onto a stack.
     * When the end of the decomposition is reached, then the code unit reader
     * pops the previous source from the stack.
     * (Same for case-folding.)
     *
     * This is complicated further by operating on variable-width UTF-16.
     * The top part of the loop works on code units, while lookups for decomposition
     * and case-folding need code points.
     * Code points are assembled after the equality/end-of-source part.
     * The source pointer is only advanced beyond all code units when the code point
     * actually decomposes/case-folds.
     *
     * If we were on a trail surrogate unit when assembling a code point,
     * and the code point decomposes/case-folds, then the decomposition/folding
     * result must be compared with the part of the other string that corresponds to
     * this string's lead surrogate.
     * Since we only assemble a code point when hitting a trail unit when the
     * preceding lead units were identical, we back up the other string by one unit
     * in such a case.
     *
     * The optional code point order comparison at the end works with
     * the same fix-up as the other code point order comparison functions.
     * See ustring.c and the comment near the end of this function.
     *
     * Assumption: A decomposition or case-folding result string never contains
     * a single surrogate. This is a safe assumption in the Unicode Standard.
     * Therefore, we do not need to check for surrogate pairs across
     * decomposition/case-folding boundaries.
     *
     * Further assumptions (see verifications tstnorm.cpp):
     * The API function checks for FCD first, while the core function
     * first case-folds and then decomposes. This requires that case-folding does not
     * un-FCD any strings.
     *
     * The API function may also NFD the input and turn off decomposition.
     * This requires that case-folding does not un-NFD strings either.
     *
     * TODO If any of the above two assumptions is violated,
     * then this entire code must be re-thought.
     * If this happens, then a simple solution is to case-fold both strings up front
     * and to turn off UNORM_INPUT_IS_FCD.
     * We already do this when not both strings are in FCD because makeFCD
     * would be a partial NFD before the case folding, which does not work.
     * Note that all of this is only a problem when case-folding _and_
     * canonical equivalence come together.
     * (Comments in unorm_compare() are more up to date than this TODO.)
     */

    /* stack element for previous-level source/decomposition pointers */
    private static final class CmpEquivLevel {
        CharSequence cs;
        int s;
    };
    private static final CmpEquivLevel[] createCmpEquivLevelStack() {
        return new CmpEquivLevel[] {
            new CmpEquivLevel(), new CmpEquivLevel()
        };
    }

    /**
     * Internal option for unorm_cmpEquivFold() for decomposing.
     * If not set, just do strcasecmp().
     */
    private static final int COMPARE_EQUIV=0x80000;

    /* internal function; package visibility for use by UTF16.StringComparator */
    /*package*/ static int cmpEquivFold(CharSequence cs1, CharSequence cs2, int options) {
        Normalizer2Impl nfcImpl;
        UCaseProps csp;

        /* current-level start/limit - s1/s2 as current */
        int s1, s2, limit1, limit2;

        /* decomposition and case folding variables */
        int length;

        /* stacks of previous-level start/current/limit */
        CmpEquivLevel[] stack1=null, stack2=null;

        /* buffers for algorithmic decompositions */
        String decomp1, decomp2;

        /* case folding buffers, only use current-level start/limit */
        StringBuilder fold1, fold2;

        /* track which is the current level per string */
        int level1, level2;

        /* current code units, and code points for lookups */
        int c1, c2, cp1, cp2;

        /* no argument error checking because this itself is not an API */

        /*
         * assume that at least one of the options _COMPARE_EQUIV and U_COMPARE_IGNORE_CASE is set
         * otherwise this function must behave exactly as uprv_strCompare()
         * not checking for that here makes testing this function easier
         */

        /* normalization/properties data loaded? */
        if((options&COMPARE_EQUIV)!=0) {
            nfcImpl=Norm2AllModes.getNFCInstance().impl;
        } else {
            nfcImpl=null;
        }
        if((options&COMPARE_IGNORE_CASE)!=0) {
            csp=UCaseProps.INSTANCE;
            fold1=new StringBuilder();
            fold2=new StringBuilder();
        } else {
            csp=null;
            fold1=fold2=null;
        }

        /* initialize */
        s1=0;
        limit1=cs1.length();
        s2=0;
        limit2=cs2.length();

        level1=level2=0;
        c1=c2=-1;

        /* comparison loop */
        for(;;) {
            /*
             * here a code unit value of -1 means "get another code unit"
             * below it will mean "this source is finished"
             */

            if(c1<0) {
                /* get next code unit from string 1, post-increment */
                for(;;) {
                    if(s1==limit1) {
                        if(level1==0) {
                            c1=-1;
                            break;
                        }
                    } else {
                        c1=cs1.charAt(s1++);
                        break;
                    }

                    /* reached end of level buffer, pop one level */
                    do {
                        --level1;
                        cs1=stack1[level1].cs;
                    } while(cs1==null);
                    s1=stack1[level1].s;
                    limit1=cs1.length();
                }
            }

            if(c2<0) {
                /* get next code unit from string 2, post-increment */
                for(;;) {
                    if(s2==limit2) {
                        if(level2==0) {
                            c2=-1;
                            break;
                        }
                    } else {
                        c2=cs2.charAt(s2++);
                        break;
                    }

                    /* reached end of level buffer, pop one level */
                    do {
                        --level2;
                        cs2=stack2[level2].cs;
                    } while(cs2==null);
                    s2=stack2[level2].s;
                    limit2=cs2.length();
                }
            }

            /*
             * compare c1 and c2
             * either variable c1, c2 is -1 only if the corresponding string is finished
             */
            if(c1==c2) {
                if(c1<0) {
                    return 0;   /* c1==c2==-1 indicating end of strings */
                }
                c1=c2=-1;       /* make us fetch new code units */
                continue;
            } else if(c1<0) {
                return -1;      /* string 1 ends before string 2 */
            } else if(c2<0) {
                return 1;       /* string 2 ends before string 1 */
            }
            /* c1!=c2 && c1>=0 && c2>=0 */

            /* get complete code points for c1, c2 for lookups if either is a surrogate */
            cp1=c1;
            if(UTF16.isSurrogate(c1)) {
                char c;

                if(Normalizer2Impl.UTF16Plus.isSurrogateLead(c1)) {
                    if(s1!=limit1 && Character.isLowSurrogate(c=cs1.charAt(s1))) {
                        /* advance ++s1; only below if cp1 decomposes/case-folds */
                        cp1=Character.toCodePoint((char)c1, c);
                    }
                } else /* isTrail(c1) */ {
                    if(0<=(s1-2) && Character.isHighSurrogate(c=cs1.charAt(s1-2))) {
                        cp1=Character.toCodePoint(c, (char)c1);
                    }
                }
            }

            cp2=c2;
            if(UTF16.isSurrogate(c2)) {
                char c;

                if(Normalizer2Impl.UTF16Plus.isSurrogateLead(c2)) {
                    if(s2!=limit2 && Character.isLowSurrogate(c=cs2.charAt(s2))) {
                        /* advance ++s2; only below if cp2 decomposes/case-folds */
                        cp2=Character.toCodePoint((char)c2, c);
                    }
                } else /* isTrail(c2) */ {
                    if(0<=(s2-2) && Character.isHighSurrogate(c=cs2.charAt(s2-2))) {
                        cp2=Character.toCodePoint(c, (char)c2);
                    }
                }
            }

            /*
             * go down one level for each string
             * continue with the main loop as soon as there is a real change
             */

            if( level1==0 && (options&COMPARE_IGNORE_CASE)!=0 &&
                (length=csp.toFullFolding(cp1, fold1, options))>=0
            ) {
                /* cp1 case-folds to the code point "length" or to p[length] */
                if(UTF16.isSurrogate(c1)) {
                    if(Normalizer2Impl.UTF16Plus.isSurrogateLead(c1)) {
                        /* advance beyond source surrogate pair if it case-folds */
                        ++s1;
                    } else /* isTrail(c1) */ {
                        /*
                         * we got a supplementary code point when hitting its trail surrogate,
                         * therefore the lead surrogate must have been the same as in the other string;
                         * compare this decomposition with the lead surrogate in the other string
                         * remember that this simulates bulk text replacement:
                         * the decomposition would replace the entire code point
                         */
                        --s2;
                        c2=cs2.charAt(s2-1);
                    }
                }

                /* push current level pointers */
                if(stack1==null) {
                    stack1=createCmpEquivLevelStack();
                }
                stack1[0].cs=cs1;
                stack1[0].s=s1;
                ++level1;

                /* copy the folding result to fold1[] */
                /* Java: the buffer was probably not empty, remove the old contents */
                if(length<=UCaseProps.MAX_STRING_LENGTH) {
                    fold1.delete(0, fold1.length()-length);
                } else {
                    fold1.setLength(0);
                    fold1.appendCodePoint(length);
                }

                /* set next level pointers to case folding */
                cs1=fold1;
                s1=0;
                limit1=fold1.length();

                /* get ready to read from decomposition, continue with loop */
                c1=-1;
                continue;
            }

            if( level2==0 && (options&COMPARE_IGNORE_CASE)!=0 &&
                (length=csp.toFullFolding(cp2, fold2, options))>=0
            ) {
                /* cp2 case-folds to the code point "length" or to p[length] */
                if(UTF16.isSurrogate(c2)) {
                    if(Normalizer2Impl.UTF16Plus.isSurrogateLead(c2)) {
                        /* advance beyond source surrogate pair if it case-folds */
                        ++s2;
                    } else /* isTrail(c2) */ {
                        /*
                         * we got a supplementary code point when hitting its trail surrogate,
                         * therefore the lead surrogate must have been the same as in the other string;
                         * compare this decomposition with the lead surrogate in the other string
                         * remember that this simulates bulk text replacement:
                         * the decomposition would replace the entire code point
                         */
                        --s1;
                        c1=cs1.charAt(s1-1);
                    }
                }

                /* push current level pointers */
                if(stack2==null) {
                    stack2=createCmpEquivLevelStack();
                }
                stack2[0].cs=cs2;
                stack2[0].s=s2;
                ++level2;

                /* copy the folding result to fold2[] */
                /* Java: the buffer was probably not empty, remove the old contents */
                if(length<=UCaseProps.MAX_STRING_LENGTH) {
                    fold2.delete(0, fold2.length()-length);
                } else {
                    fold2.setLength(0);
                    fold2.appendCodePoint(length);
                }

                /* set next level pointers to case folding */
                cs2=fold2;
                s2=0;
                limit2=fold2.length();

                /* get ready to read from decomposition, continue with loop */
                c2=-1;
                continue;
            }

            if( level1<2 && (options&COMPARE_EQUIV)!=0 &&
                (decomp1=nfcImpl.getDecomposition(cp1))!=null
            ) {
                /* cp1 decomposes into p[length] */
                if(UTF16.isSurrogate(c1)) {
                    if(Normalizer2Impl.UTF16Plus.isSurrogateLead(c1)) {
                        /* advance beyond source surrogate pair if it decomposes */
                        ++s1;
                    } else /* isTrail(c1) */ {
                        /*
                         * we got a supplementary code point when hitting its trail surrogate,
                         * therefore the lead surrogate must have been the same as in the other string;
                         * compare this decomposition with the lead surrogate in the other string
                         * remember that this simulates bulk text replacement:
                         * the decomposition would replace the entire code point
                         */
                        --s2;
                        c2=cs2.charAt(s2-1);
                    }
                }

                /* push current level pointers */
                if(stack1==null) {
                    stack1=createCmpEquivLevelStack();
                }
                stack1[level1].cs=cs1;
                stack1[level1].s=s1;
                ++level1;

                /* set empty intermediate level if skipped */
                if(level1<2) {
                    stack1[level1++].cs=null;
                }

                /* set next level pointers to decomposition */
                cs1=decomp1;
                s1=0;
                limit1=decomp1.length();

                /* get ready to read from decomposition, continue with loop */
                c1=-1;
                continue;
            }

            if( level2<2 && (options&COMPARE_EQUIV)!=0 &&
                (decomp2=nfcImpl.getDecomposition(cp2))!=null
            ) {
                /* cp2 decomposes into p[length] */
                if(UTF16.isSurrogate(c2)) {
                    if(Normalizer2Impl.UTF16Plus.isSurrogateLead(c2)) {
                        /* advance beyond source surrogate pair if it decomposes */
                        ++s2;
                    } else /* isTrail(c2) */ {
                        /*
                         * we got a supplementary code point when hitting its trail surrogate,
                         * therefore the lead surrogate must have been the same as in the other string;
                         * compare this decomposition with the lead surrogate in the other string
                         * remember that this simulates bulk text replacement:
                         * the decomposition would replace the entire code point
                         */
                        --s1;
                        c1=cs1.charAt(s1-1);
                    }
                }

                /* push current level pointers */
                if(stack2==null) {
                    stack2=createCmpEquivLevelStack();
                }
                stack2[level2].cs=cs2;
                stack2[level2].s=s2;
                ++level2;

                /* set empty intermediate level if skipped */
                if(level2<2) {
                    stack2[level2++].cs=null;
                }

                /* set next level pointers to decomposition */
                cs2=decomp2;
                s2=0;
                limit2=decomp2.length();

                /* get ready to read from decomposition, continue with loop */
                c2=-1;
                continue;
            }

            /*
             * no decomposition/case folding, max level for both sides:
             * return difference result
             *
             * code point order comparison must not just return cp1-cp2
             * because when single surrogates are present then the surrogate pairs
             * that formed cp1 and cp2 may be from different string indexes
             *
             * example: { d800 d800 dc01 } vs. { d800 dc00 }, compare at second code units
             * c1=d800 cp1=10001 c2=dc00 cp2=10000
             * cp1-cp2>0 but c1-c2<0 and in fact in UTF-32 it is { d800 10001 } < { 10000 }
             *
             * therefore, use same fix-up as in ustring.c/uprv_strCompare()
             * except: uprv_strCompare() fetches c=*s while this functions fetches c=*s++
             * so we have slightly different pointer/start/limit comparisons here
             */

            if(c1>=0xd800 && c2>=0xd800 && (options&COMPARE_CODE_POINT_ORDER)!=0) {
                /* subtract 0x2800 from BMP code points to make them smaller than supplementary ones */
                if(
                    (c1<=0xdbff && s1!=limit1 && Character.isLowSurrogate(cs1.charAt(s1))) ||
                    (Character.isLowSurrogate((char)c1) && 0!=(s1-1) && Character.isHighSurrogate(cs1.charAt(s1-2)))
                ) {
                    /* part of a surrogate pair, leave >=d800 */
                } else {
                    /* BMP code point - may be surrogate code point - make <d800 */
                    c1-=0x2800;
                }

                if(
                    (c2<=0xdbff && s2!=limit2 && Character.isLowSurrogate(cs2.charAt(s2))) ||
                    (Character.isLowSurrogate((char)c2) && 0!=(s2-1) && Character.isHighSurrogate(cs2.charAt(s2-2)))
                ) {
                    /* part of a surrogate pair, leave >=d800 */
                } else {
                    /* BMP code point - may be surrogate code point - make <d800 */
                    c2-=0x2800;
                }
            }

            return c1-c2;
        }
    }

    /**
     * An Appendable that writes into a char array with a capacity that may be
     * less than array.length.
     * (By contrast, CharBuffer will write beyond destLimit all the way up to array.length.)
     * <p>
     * An overflow is only reported at the end, for the old Normalizer API functions that write
     * to char arrays.
     */
    private static final class CharsAppendable implements Appendable {
        public CharsAppendable(char[] dest, int destStart, int destLimit) {
            chars=dest;
            start=offset=destStart;
            limit=destLimit;
        }
        public int length() {
            int len=offset-start;
            if(offset<=limit) {
                return len;
            } else {
                throw new IndexOutOfBoundsException(Integer.toString(len));
            }
        }
        @Override
        public Appendable append(char c) {
            if(offset<limit) {
                chars[offset]=c;
            }
            ++offset;
            return this;
        }
        @Override
        public Appendable append(CharSequence s) {
            return append(s, 0, s.length());
        }
        @Override
        public Appendable append(CharSequence s, int sStart, int sLimit) {
            int len=sLimit-sStart;
            if(len<=(limit-offset)) {
                while(sStart<sLimit) {  // TODO: Is there a better way to copy the characters?
                    chars[offset++]=s.charAt(sStart++);
                }
            } else {
                offset+=len;
            }
            return this;
        }

        private final char[] chars;
        private final int start, limit;
        private int offset;
    }
}
