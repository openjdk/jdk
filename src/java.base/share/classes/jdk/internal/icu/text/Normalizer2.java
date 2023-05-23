// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 *   Copyright (C) 2009-2016, International Business Machines
 *   Corporation and others.  All Rights Reserved.
 *******************************************************************************
 */

package jdk.internal.icu.text;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import jdk.internal.icu.impl.ICUBinary;
import jdk.internal.icu.impl.Norm2AllModes;

/**
 * Unicode normalization functionality for standard Unicode normalization or
 * for using custom mapping tables.
 * All instances of this class are unmodifiable/immutable.
 * The Normalizer2 class is not intended for public subclassing.
 * <p>
 * The primary functions are to produce a normalized string and to detect whether
 * a string is already normalized.
 * The most commonly used normalization forms are those defined in
 * https://www.unicode.org/reports/tr15/
 * However, this API supports additional normalization forms for specialized purposes.
 * For example, NFKC_Casefold is provided via getInstance("nfkc_cf", COMPOSE)
 * and can be used in implementations of UTS #46.
 * <p>
 * Not only are the standard compose and decompose modes supplied,
 * but additional modes are provided as documented in the Mode enum.
 * <p>
 * Some of the functions in this class identify normalization boundaries.
 * At a normalization boundary, the portions of the string
 * before it and starting from it do not interact and can be handled independently.
 * <p>
 * The spanQuickCheckYes() stops at a normalization boundary.
 * When the goal is a normalized string, then the text before the boundary
 * can be copied, and the remainder can be processed with normalizeSecondAndAppend().
 * <p>
 * The hasBoundaryBefore(), hasBoundaryAfter() and isInert() functions test whether
 * a character is guaranteed to be at a normalization boundary,
 * regardless of context.
 * This is used for moving from one normalization boundary to the next
 * or preceding boundary, and for performing iterative normalization.
 * <p>
 * Iterative normalization is useful when only a small portion of a
 * longer string needs to be processed.
 * For example, in ICU, iterative normalization is used by the NormalizationTransliterator
 * (to avoid replacing already-normalized text) and ucol_nextSortKeyPart()
 * (to process only the substring for which sort key bytes are computed).
 * <p>
 * The set of normalization boundaries returned by these functions may not be
 * complete: There may be more boundaries that could be returned.
 * Different functions may return different boundaries.
 * @stable ICU 4.4
 * @author Markus W. Scherer
 */
public abstract class Normalizer2 {
    /**
     * Constants for normalization modes.
     * For details about standard Unicode normalization forms
     * and about the algorithms which are also used with custom mapping tables
     * see https://www.unicode.org/reports/tr15/
     * @stable ICU 4.4
     */
    public enum Mode {
        /**
         * Decomposition followed by composition.
         * Same as standard NFC when using an "nfc" instance.
         * Same as standard NFKC when using an "nfkc" instance.
         * For details about standard Unicode normalization forms
         * see https://www.unicode.org/reports/tr15/
         * @stable ICU 4.4
         */
        COMPOSE,
        /**
         * Map, and reorder canonically.
         * Same as standard NFD when using an "nfc" instance.
         * Same as standard NFKD when using an "nfkc" instance.
         * For details about standard Unicode normalization forms
         * see https://www.unicode.org/reports/tr15/
         * @stable ICU 4.4
         */
        DECOMPOSE,
        /**
         * "Fast C or D" form.
         * If a string is in this form, then further decomposition <i>without reordering</i>
         * would yield the same form as DECOMPOSE.
         * Text in "Fast C or D" form can be processed efficiently with data tables
         * that are "canonically closed", that is, that provide equivalent data for
         * equivalent text, without having to be fully normalized.<br>
         * Not a standard Unicode normalization form.<br>
         * Not a unique form: Different FCD strings can be canonically equivalent.<br>
         * For details see http://www.unicode.org/notes/tn5/#FCD
         * @stable ICU 4.4
         */
        FCD,
        /**
         * Compose only contiguously.
         * Also known as "FCC" or "Fast C Contiguous".
         * The result will often but not always be in NFC.
         * The result will conform to FCD which is useful for processing.<br>
         * Not a standard Unicode normalization form.<br>
         * For details see http://www.unicode.org/notes/tn5/#FCC
         * @stable ICU 4.4
         */
        COMPOSE_CONTIGUOUS
    };

    /**
     * Returns a Normalizer2 instance for Unicode NFC normalization.
     * Same as getInstance(null, "nfc", Mode.COMPOSE).
     * Returns an unmodifiable singleton instance.
     * @return the requested Normalizer2, if successful
     * @stable ICU 49
     */
    public static Normalizer2 getNFCInstance() {
        return Norm2AllModes.getNFCInstance().comp;
    }

    /**
     * Returns a Normalizer2 instance for Unicode NFD normalization.
     * Same as getInstance(null, "nfc", Mode.DECOMPOSE).
     * Returns an unmodifiable singleton instance.
     * @return the requested Normalizer2, if successful
     * @stable ICU 49
     */
    public static Normalizer2 getNFDInstance() {
        return Norm2AllModes.getNFCInstance().decomp;
    }

    /**
     * Returns a Normalizer2 instance for Unicode NFKC normalization.
     * Same as getInstance(null, "nfkc", Mode.COMPOSE).
     * Returns an unmodifiable singleton instance.
     * @return the requested Normalizer2, if successful
     * @stable ICU 49
     */
    public static Normalizer2 getNFKCInstance() {
        return Norm2AllModes.getNFKCInstance().comp;
    }

    /**
     * Returns a Normalizer2 instance for Unicode NFKD normalization.
     * Same as getInstance(null, "nfkc", Mode.DECOMPOSE).
     * Returns an unmodifiable singleton instance.
     * @return the requested Normalizer2, if successful
     * @stable ICU 49
     */
    public static Normalizer2 getNFKDInstance() {
        return Norm2AllModes.getNFKCInstance().decomp;
    }

    /**
     * Returns a Normalizer2 instance for Unicode NFKC_Casefold normalization.
     * Same as getInstance(null, "nfkc_cf", Mode.COMPOSE).
     * Returns an unmodifiable singleton instance.
     * @return the requested Normalizer2, if successful
     * @stable ICU 49
     */
    public static Normalizer2 getNFKCCasefoldInstance() {
        return Norm2AllModes.getNFKC_CFInstance().comp;
    }

    /**
     * Returns a Normalizer2 instance which uses the specified data file
     * (an ICU data file if data=null, or else custom binary data)
     * and which composes or decomposes text according to the specified mode.
     * Returns an unmodifiable singleton instance.
     * <ul>
     * <li>Use data=null for data files that are part of ICU's own data.
     * <li>Use name="nfc" and COMPOSE/DECOMPOSE for Unicode standard NFC/NFD.
     * <li>Use name="nfkc" and COMPOSE/DECOMPOSE for Unicode standard NFKC/NFKD.
     * <li>Use name="nfkc_cf" and COMPOSE for Unicode standard NFKC_CF=NFKC_Casefold.
     * </ul>
     * If data!=null, then the binary data is read once and cached using the provided
     * name as the key.
     * If you know or expect the data to be cached already, you can use data!=null
     * for non-ICU data as well.
     * <p>Any {@link java.io.IOException} is wrapped into a {@link java.io.UncheckedIOException}.
     * @param data the binary, big-endian normalization (.nrm file) data, or null for ICU data
     * @param name "nfc" or "nfkc" or "nfkc_cf" or name of custom data file
     * @param mode normalization mode (compose or decompose etc.)
     * @return the requested Normalizer2, if successful
     * @stable ICU 4.4
     */
    public static Normalizer2 getInstance(InputStream data, String name, Mode mode) {
        // TODO: If callers really use this API, then we should add an overload that takes a ByteBuffer.
        ByteBuffer bytes = null;
        if (data != null) {
            try {
                bytes = ICUBinary.getByteBufferFromInputStreamAndCloseStream(data);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        Norm2AllModes all2Modes=Norm2AllModes.getInstance(bytes, name);
        switch(mode) {
        case COMPOSE: return all2Modes.comp;
        case DECOMPOSE: return all2Modes.decomp;
        case FCD: return all2Modes.fcd;
        case COMPOSE_CONTIGUOUS: return all2Modes.fcc;
        default: return null;  // will not occur
        }
    }

    /**
     * Returns the normalized form of the source string.
     * @param src source string
     * @return normalized src
     * @stable ICU 4.4
     */
    public String normalize(CharSequence src) {
        if(src instanceof String) {
            // Fastpath: Do not construct a new String if the src is a String
            // and is already normalized.
            int spanLength=spanQuickCheckYes(src);
            if(spanLength==src.length()) {
                return (String)src;
            }
            if (spanLength != 0) {
                StringBuilder sb=new StringBuilder(src.length()).append(src, 0, spanLength);
                return normalizeSecondAndAppend(sb, src.subSequence(spanLength, src.length())).toString();
            }
        }
        return normalize(src, new StringBuilder(src.length())).toString();
    }

    /**
     * Writes the normalized form of the source string to the destination string
     * (replacing its contents) and returns the destination string.
     * The source and destination strings must be different objects.
     * @param src source string
     * @param dest destination string; its contents is replaced with normalized src
     * @return dest
     * @stable ICU 4.4
     */
    public abstract StringBuilder normalize(CharSequence src, StringBuilder dest);

    /**
     * Writes the normalized form of the source string to the destination Appendable
     * and returns the destination Appendable.
     * The source and destination strings must be different objects.
     *
     * <p>Any {@link java.io.IOException} is wrapped into a {@link java.io.UncheckedIOException}.
     *
     * @param src source string
     * @param dest destination Appendable; gets normalized src appended
     * @return dest
     * @stable ICU 4.6
     */
    public abstract Appendable normalize(CharSequence src, Appendable dest);

    /**
     * Appends the normalized form of the second string to the first string
     * (merging them at the boundary) and returns the first string.
     * The result is normalized if the first string was normalized.
     * The first and second strings must be different objects.
     * @param first string, should be normalized
     * @param second string, will be normalized
     * @return first
     * @stable ICU 4.4
     */
    public abstract StringBuilder normalizeSecondAndAppend(
            StringBuilder first, CharSequence second);

    /**
     * Appends the second string to the first string
     * (merging them at the boundary) and returns the first string.
     * The result is normalized if both the strings were normalized.
     * The first and second strings must be different objects.
     * @param first string, should be normalized
     * @param second string, should be normalized
     * @return first
     * @stable ICU 4.4
     */
    public abstract StringBuilder append(StringBuilder first, CharSequence second);

    /**
     * Gets the decomposition mapping of c.
     * Roughly equivalent to normalizing the String form of c
     * on a DECOMPOSE Normalizer2 instance, but much faster, and except that this function
     * returns null if c does not have a decomposition mapping in this instance's data.
     * This function is independent of the mode of the Normalizer2.
     * @param c code point
     * @return c's decomposition mapping, if any; otherwise null
     * @stable ICU 4.6
     */
    public abstract String getDecomposition(int c);

    /**
     * Gets the raw decomposition mapping of c.
     *
     * <p>This is similar to the getDecomposition() method but returns the
     * raw decomposition mapping as specified in UnicodeData.txt or
     * (for custom data) in the mapping files processed by the gennorm2 tool.
     * By contrast, getDecomposition() returns the processed,
     * recursively-decomposed version of this mapping.
     *
     * <p>When used on a standard NFKC Normalizer2 instance,
     * getRawDecomposition() returns the Unicode Decomposition_Mapping (dm) property.
     *
     * <p>When used on a standard NFC Normalizer2 instance,
     * it returns the Decomposition_Mapping only if the Decomposition_Type (dt) is Canonical (Can);
     * in this case, the result contains either one or two code points (=1..4 Java chars).
     *
     * <p>This function is independent of the mode of the Normalizer2.
     * The default implementation returns null.
     * @param c code point
     * @return c's raw decomposition mapping, if any; otherwise null
     * @stable ICU 49
     */
    public String getRawDecomposition(int c) { return null; }

    /**
     * Performs pairwise composition of a &amp; b and returns the composite if there is one.
     *
     * <p>Returns a composite code point c only if c has a two-way mapping to a+b.
     * In standard Unicode normalization, this means that
     * c has a canonical decomposition to a+b
     * and c does not have the Full_Composition_Exclusion property.
     *
     * <p>This function is independent of the mode of the Normalizer2.
     * The default implementation returns a negative value.
     * @param a A (normalization starter) code point.
     * @param b Another code point.
     * @return The non-negative composite code point if there is one; otherwise a negative value.
     * @stable ICU 49
     */
    public int composePair(int a, int b) { return -1; }

    /**
     * Gets the combining class of c.
     * The default implementation returns 0
     * but all standard implementations return the Unicode Canonical_Combining_Class value.
     * @param c code point
     * @return c's combining class
     * @stable ICU 49
     */
    public int getCombiningClass(int c) { return 0; }

    /**
     * Tests if the string is normalized.
     * Internally, in cases where the quickCheck() method would return "maybe"
     * (which is only possible for the two COMPOSE modes) this method
     * resolves to "yes" or "no" to provide a definitive result,
     * at the cost of doing more work in those cases.
     * @param s input string
     * @return true if s is normalized
     * @stable ICU 4.4
     */
    public abstract boolean isNormalized(CharSequence s);

    /**
     * Tests if the string is normalized.
     * For the two COMPOSE modes, the result could be "maybe" in cases that
     * would take a little more work to resolve definitively.
     * Use spanQuickCheckYes() and normalizeSecondAndAppend() for a faster
     * combination of quick check + normalization, to avoid
     * re-checking the "yes" prefix.
     * @param s input string
     * @return the quick check result
     * @stable ICU 4.4
     */
    public abstract Normalizer.QuickCheckResult quickCheck(CharSequence s);

    /**
     * Returns the end of the normalized substring of the input string.
     * In other words, with <code>end=spanQuickCheckYes(s);</code>
     * the substring <code>s.subSequence(0, end)</code>
     * will pass the quick check with a "yes" result.
     * <p>
     * The returned end index is usually one or more characters before the
     * "no" or "maybe" character: The end index is at a normalization boundary.
     * (See the class documentation for more about normalization boundaries.)
     * <p>
     * When the goal is a normalized string and most input strings are expected
     * to be normalized already, then call this method,
     * and if it returns a prefix shorter than the input string,
     * copy that prefix and use normalizeSecondAndAppend() for the remainder.
     * @param s input string
     * @return "yes" span end index
     * @stable ICU 4.4
     */
    public abstract int spanQuickCheckYes(CharSequence s);

    /**
     * Tests if the character always has a normalization boundary before it,
     * regardless of context.
     * If true, then the character does not normalization-interact with
     * preceding characters.
     * In other words, a string containing this character can be normalized
     * by processing portions before this character and starting from this
     * character independently.
     * This is used for iterative normalization. See the class documentation for details.
     * @param c character to test
     * @return true if c has a normalization boundary before it
     * @stable ICU 4.4
     */
    public abstract boolean hasBoundaryBefore(int c);

    /**
     * Tests if the character always has a normalization boundary after it,
     * regardless of context.
     * If true, then the character does not normalization-interact with
     * following characters.
     * In other words, a string containing this character can be normalized
     * by processing portions up to this character and after this
     * character independently.
     * This is used for iterative normalization. See the class documentation for details.
     * <p>
     * Note that this operation may be significantly slower than hasBoundaryBefore().
     * @param c character to test
     * @return true if c has a normalization boundary after it
     * @stable ICU 4.4
     */
    public abstract boolean hasBoundaryAfter(int c);

    /**
     * Tests if the character is normalization-inert.
     * If true, then the character does not change, nor normalization-interact with
     * preceding or following characters.
     * In other words, a string containing this character can be normalized
     * by processing portions before this character and after this
     * character independently.
     * This is used for iterative normalization. See the class documentation for details.
     * <p>
     * Note that this operation may be significantly slower than hasBoundaryBefore().
     * @param c character to test
     * @return true if c is normalization-inert
     * @stable ICU 4.4
     */
    public abstract boolean isInert(int c);

    /**
     * Sole constructor.  (For invocation by subclass constructors,
     * typically implicit.)
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    protected Normalizer2() {
    }
}
