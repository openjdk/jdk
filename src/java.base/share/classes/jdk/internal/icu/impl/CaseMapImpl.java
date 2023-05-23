// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
package jdk.internal.icu.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.CharacterIterator;
import java.util.Locale;

import jdk.internal.icu.lang.UCharacter;
import jdk.internal.icu.lang.UCharacterCategory;
import jdk.internal.icu.text.BreakIterator;
import jdk.internal.icu.text.Edits;
import jdk.internal.icu.util.ULocale;

public final class CaseMapImpl {
    /**
     * Implementation of UCaseProps.ContextIterator, iterates over a String.
     * See ustrcase.c/utf16_caseContextIterator().
     */
    public static final class StringContextIterator implements UCaseProps.ContextIterator {
        /**
         * Constructor.
         * @param src String to iterate over.
         */
        public StringContextIterator(CharSequence src) {
            this.s=src;
            limit=src.length();
            cpStart=cpLimit=index=0;
            dir=0;
        }

        /**
         * Constructor.
         * @param src String to iterate over.
         * @param cpStart Start index of the current code point.
         * @param cpLimit Limit index of the current code point.
         */
        public StringContextIterator(CharSequence src, int cpStart, int cpLimit) {
            s = src;
            index = 0;
            limit = src.length();
            this.cpStart = cpStart;
            this.cpLimit = cpLimit;
            dir = 0;
        }

        /**
         * Set the iteration limit for nextCaseMapCP() to an index within the string.
         * If the limit parameter is negative or past the string, then the
         * string length is restored as the iteration limit.
         *
         * <p>This limit does not affect the next() function which always
         * iterates to the very end of the string.
         *
         * @param lim The iteration limit.
         */
        public void setLimit(int lim) {
            if(0<=lim && lim<=s.length()) {
                limit=lim;
            } else {
                limit=s.length();
            }
        }

        /**
         * Move to the iteration limit without fetching code points up to there.
         */
        public void moveToLimit() {
            cpStart=cpLimit=limit;
        }

        public void moveTo(int i) {
            cpStart=cpLimit=i;
        }

        /**
         * Iterate forward through the string to fetch the next code point
         * to be case-mapped, and set the context indexes for it.
         *
         * <p>When the iteration limit is reached (and -1 is returned),
         * getCPStart() will be at the iteration limit.
         *
         * <p>Iteration with next() does not affect the position for nextCaseMapCP().
         *
         * @return The next code point to be case-mapped, or <0 when the iteration is done.
         */
        public int nextCaseMapCP() {
            cpStart=cpLimit;
            if(cpLimit<limit) {
                int c=Character.codePointAt(s, cpLimit);
                cpLimit+=Character.charCount(c);
                return c;
            } else {
                return -1;
            }
        }

        public void setCPStartAndLimit(int s, int l) {
            cpStart = s;
            cpLimit = l;
            dir = 0;
        }
        /**
         * Returns the start of the code point that was last returned
         * by nextCaseMapCP().
         */
        public int getCPStart() {
            return cpStart;
        }

        /**
         * Returns the limit of the code point that was last returned
         * by nextCaseMapCP().
         */
        public int getCPLimit() {
            return cpLimit;
        }

        public int getCPLength() {
            return cpLimit-cpStart;
        }

        // implement UCaseProps.ContextIterator
        // The following code is not used anywhere in this private class
        @Override
        public void reset(int direction) {
            if(direction>0) {
                /* reset for forward iteration */
                dir=1;
                index=cpLimit;
            } else if(direction<0) {
                /* reset for backward iteration */
                dir=-1;
                index=cpStart;
            } else {
                // not a valid direction
                dir=0;
                index=0;
            }
        }

        @Override
        public int next() {
            int c;

            if(dir>0 && index<s.length()) {
                c=Character.codePointAt(s, index);
                index+=Character.charCount(c);
                return c;
            } else if(dir<0 && index>0) {
                c=Character.codePointBefore(s, index);
                index-=Character.charCount(c);
                return c;
            }
            return -1;
        }

        // variables
        protected CharSequence s;
        protected int index, limit, cpStart, cpLimit;
        protected int dir; // 0=initial state  >0=forward  <0=backward
    }

    public static final int TITLECASE_WHOLE_STRING = 0x20;
    public static final int TITLECASE_SENTENCES = 0x40;

    /**
     * Bit mask for the titlecasing iterator options bit field.
     * Currently only 3 out of 8 values are used:
     * 0 (words), TITLECASE_WHOLE_STRING, TITLECASE_SENTENCES.
     * See stringoptions.h.
     * @internal
     */
    private static final int TITLECASE_ITERATOR_MASK = 0xe0;

    public static final int TITLECASE_ADJUST_TO_CASED = 0x400;

    /**
     * Bit mask for the titlecasing index adjustment options bit set.
     * Currently two bits are defined:
     * TITLECASE_NO_BREAK_ADJUSTMENT, TITLECASE_ADJUST_TO_CASED.
     * See stringoptions.h.
     * @internal
     */
    private static final int TITLECASE_ADJUSTMENT_MASK = 0x600;

    public static int addTitleAdjustmentOption(int options, int newOption) {
        int adjOptions = options & TITLECASE_ADJUSTMENT_MASK;
        if (adjOptions !=0 && adjOptions != newOption) {
            throw new IllegalArgumentException("multiple titlecasing index adjustment options");
        }
        return options | newOption;
    }

    private static final char ACUTE = '\u0301';

    private static final int U_GC_M_MASK =
            (1 << UCharacterCategory.NON_SPACING_MARK) |
            (1 << UCharacterCategory.COMBINING_SPACING_MARK) |
            (1 << UCharacterCategory.ENCLOSING_MARK);

    private static final int LNS =
            (1 << UCharacterCategory.UPPERCASE_LETTER) |
            (1 << UCharacterCategory.LOWERCASE_LETTER) |
            (1 << UCharacterCategory.TITLECASE_LETTER) |
            // Not MODIFIER_LETTER: We count only cased modifier letters.
            (1 << UCharacterCategory.OTHER_LETTER) |

            (1 << UCharacterCategory.DECIMAL_DIGIT_NUMBER) |
            (1 << UCharacterCategory.LETTER_NUMBER) |
            (1 << UCharacterCategory.OTHER_NUMBER) |

            (1 << UCharacterCategory.MATH_SYMBOL) |
            (1 << UCharacterCategory.CURRENCY_SYMBOL) |
            (1 << UCharacterCategory.MODIFIER_SYMBOL) |
            (1 << UCharacterCategory.OTHER_SYMBOL) |

            (1 << UCharacterCategory.PRIVATE_USE);

    private static boolean isLNS(int c) {
        // Letter, number, symbol,
        // or a private use code point because those are typically used as letters or numbers.
        // Consider modifier letters only if they are cased.
        int gc = UCharacterProperty.INSTANCE.getType(c);
        return ((1 << gc) & LNS) != 0 ||
                (gc == UCharacterCategory.MODIFIER_LETTER &&
                    UCaseProps.INSTANCE.getType(c) != UCaseProps.NONE);
    }

    public static int addTitleIteratorOption(int options, int newOption) {
        int iterOptions = options & TITLECASE_ITERATOR_MASK;
        if (iterOptions !=0 && iterOptions != newOption) {
            throw new IllegalArgumentException("multiple titlecasing iterator options");
        }
        return options | newOption;
    }

    public static BreakIterator getTitleBreakIterator(
            Locale locale, int options, BreakIterator iter) {
        options &= TITLECASE_ITERATOR_MASK;
        if (options != 0 && iter != null) {
            throw new IllegalArgumentException(
                    "titlecasing iterator option together with an explicit iterator");
        }
        if (iter == null) {
            switch (options) {
            case 0:
                iter = BreakIterator.getWordInstance(locale);
                break;
            case TITLECASE_WHOLE_STRING:
                iter = new WholeStringBreakIterator();
                break;
            case TITLECASE_SENTENCES:
                iter = BreakIterator.getSentenceInstance(locale);
                break;
            default:
                throw new IllegalArgumentException("unknown titlecasing iterator option");
            }
        }
        return iter;
    }

    public static BreakIterator getTitleBreakIterator(
            ULocale locale, int options, BreakIterator iter) {
        options &= TITLECASE_ITERATOR_MASK;
        if (options != 0 && iter != null) {
            throw new IllegalArgumentException(
                    "titlecasing iterator option together with an explicit iterator");
        }
        if (iter == null) {
            switch (options) {
            case 0:
                iter = BreakIterator.getWordInstance(locale);
                break;
            case TITLECASE_WHOLE_STRING:
                iter = new WholeStringBreakIterator();
                break;
            case TITLECASE_SENTENCES:
                iter = BreakIterator.getSentenceInstance(locale);
                break;
            default:
                throw new IllegalArgumentException("unknown titlecasing iterator option");
            }
        }
        return iter;
    }

    /**
     * Omit unchanged text when case-mapping with Edits.
     */
    public static final int OMIT_UNCHANGED_TEXT = 0x4000;

    private static final class WholeStringBreakIterator extends BreakIterator {
        private int length;

        private static void notImplemented() {
            throw new UnsupportedOperationException("should not occur");
        }

        @Override
        public int first() {
            return 0;
        }

        @Override
        public int last() {
            notImplemented();
            return 0;
        }

        @Override
        public int next(int n) {
            notImplemented();
            return 0;
        }

        @Override
        public int next() {
            return length;
        }

        @Override
        public int previous() {
            notImplemented();
            return 0;
        }

        @Override
        public int following(int offset) {
            notImplemented();
            return 0;
        }

        @Override
        public int current() {
            notImplemented();
            return 0;
        }

        @Override
        public CharacterIterator getText() {
            notImplemented();
            return null;
        }

        @Override
        public void setText(CharacterIterator newText) {
            length = newText.getEndIndex();
        }

        @Override
        public void setText(CharSequence newText) {
            length = newText.length();
        }

        @Override
        public void setText(String newText) {
            length = newText.length();
        }
    }

    private static int appendCodePoint(Appendable a, int c) throws IOException {
        if (c <= Character.MAX_VALUE) {
            a.append((char)c);
            return 1;
        } else {
            a.append((char)(0xd7c0 + (c >> 10)));
            a.append((char)(Character.MIN_LOW_SURROGATE + (c & 0x3ff)));
            return 2;
        }
    }

    /**
     * Appends a full case mapping result, see {@link UCaseProps#MAX_STRING_LENGTH}.
     * @throws IOException
     */
    private static void appendResult(int result, Appendable dest,
            int cpLength, int options, Edits edits) throws IOException {
        // Decode the result.
        if (result < 0) {
            // (not) original code point
            if (edits != null) {
                edits.addUnchanged(cpLength);
            }
            if ((options & OMIT_UNCHANGED_TEXT) != 0) {
                return;
            }
            appendCodePoint(dest, ~result);
        } else if (result <= UCaseProps.MAX_STRING_LENGTH) {
            // The mapping has already been appended to result.
            if (edits != null) {
                edits.addReplace(cpLength, result);
            }
        } else {
            // Append the single-code point mapping.
            int length = appendCodePoint(dest, result);
            if (edits != null) {
                edits.addReplace(cpLength, length);
            }
        }
    }

    private static final void appendUnchanged(CharSequence src, int start, int length,
            Appendable dest, int options, Edits edits) throws IOException {
        if (length > 0) {
            if (edits != null) {
                edits.addUnchanged(length);
            }
            if ((options & OMIT_UNCHANGED_TEXT) != 0) {
                return;
            }
            dest.append(src, start, start + length);
        }
    }

    private static String applyEdits(CharSequence src, StringBuilder replacementChars, Edits edits) {
        if (!edits.hasChanges()) {
            return src.toString();
        }
        StringBuilder result = new StringBuilder(src.length() + edits.lengthDelta());
        for (Edits.Iterator ei = edits.getCoarseIterator(); ei.next();) {
            if (ei.hasChange()) {
                int i = ei.replacementIndex();
                result.append(replacementChars, i, i + ei.newLength());
            } else {
                int i = ei.sourceIndex();
                result.append(src, i, i + ei.oldLength());
            }
        }
        return result.toString();
    }

    private static final Trie2_16 CASE_TRIE = UCaseProps.getTrie();

    /**
     * caseLocale >= 0: Lowercases [srcStart..srcLimit[ but takes context [0..srcLength[ into account.
     * caseLocale < 0: Case-folds [srcStart..srcLimit[.
     */
    private static void internalToLower(int caseLocale, int options,
            CharSequence src, int srcStart, int srcLimit, StringContextIterator iter,
            Appendable dest, Edits edits) throws IOException {
        byte[] latinToLower;
        if (caseLocale == UCaseProps.LOC_ROOT ||
                (caseLocale >= 0 ?
                    !(caseLocale == UCaseProps.LOC_TURKISH || caseLocale == UCaseProps.LOC_LITHUANIAN) :
                    (options & UCaseProps.FOLD_CASE_OPTIONS_MASK) == UCharacter.FOLD_CASE_DEFAULT)) {
            latinToLower = UCaseProps.LatinCase.TO_LOWER_NORMAL;
        } else {
            latinToLower = UCaseProps.LatinCase.TO_LOWER_TR_LT;
        }
        int prev = srcStart;
        int srcIndex = srcStart;
        outerLoop:
        for (;;) {
            // fast path for simple cases
            char lead;
            for (;;) {
                if (srcIndex >= srcLimit) {
                    break outerLoop;
                }
                lead = src.charAt(srcIndex);
                int delta;
                if (lead < UCaseProps.LatinCase.LONG_S) {
                    byte d = latinToLower[lead];
                    if (d == UCaseProps.LatinCase.EXC) { break; }
                    ++srcIndex;
                    if (d == 0) { continue; }
                    delta = d;
                } else if (lead >= 0xd800) {
                    break;  // surrogate or higher
                } else {
                    int props = CASE_TRIE.getFromU16SingleLead(lead);
                    if (UCaseProps.propsHasException(props)) { break; }
                    ++srcIndex;
                    if (!UCaseProps.isUpperOrTitleFromProps(props) ||
                            (delta = UCaseProps.getDelta(props)) == 0) {
                        continue;
                    }
                }
                lead += (char)delta;
                appendUnchanged(src, prev, srcIndex - 1 - prev, dest, options, edits);
                dest.append(lead);
                if (edits != null) {
                    edits.addReplace(1, 1);
                }
                prev = srcIndex;
            }
            // slow path
            int cpStart = srcIndex++;
            char trail;
            int c;
            if (Character.isHighSurrogate(lead) && srcIndex < srcLimit &&
                    Character.isLowSurrogate(trail = src.charAt(srcIndex))) {
                c = Character.toCodePoint(lead, trail);
                ++srcIndex;
            } else {
                c = lead;
            }
            // We need to append unchanged text before calling the UCaseProps.toFullXyz() methods
            // because they will sometimes append their mapping to dest,
            // and that must be after copying the previous text.
            appendUnchanged(src, prev, cpStart - prev, dest, options, edits);
            prev = cpStart;
            if (caseLocale >= 0) {
                if (iter == null) {
                    iter = new StringContextIterator(src, cpStart, srcIndex);
                } else {
                    iter.setCPStartAndLimit(cpStart, srcIndex);
                }
                c = UCaseProps.INSTANCE.toFullLower(c, iter, dest, caseLocale);
            } else {
                c = UCaseProps.INSTANCE.toFullFolding(c, dest, options);
            }
            if (c >= 0) {
                appendResult(c, dest, srcIndex - cpStart, options, edits);
                prev = srcIndex;
            }
        }
        appendUnchanged(src, prev, srcIndex - prev, dest, options, edits);
    }

    private static void internalToUpper(int caseLocale, int options,
            CharSequence src, Appendable dest, Edits edits) throws IOException {
        StringContextIterator iter = null;
        byte[] latinToUpper;
        if (caseLocale == UCaseProps.LOC_TURKISH) {
            latinToUpper = UCaseProps.LatinCase.TO_UPPER_TR;
        } else {
            latinToUpper = UCaseProps.LatinCase.TO_UPPER_NORMAL;
        }
        int prev = 0;
        int srcIndex = 0;
        int srcLength = src.length();
        outerLoop:
        for (;;) {
            // fast path for simple cases
            char lead;
            for (;;) {
                if (srcIndex >= srcLength) {
                    break outerLoop;
                }
                lead = src.charAt(srcIndex);
                int delta;
                if (lead < UCaseProps.LatinCase.LONG_S) {
                    byte d = latinToUpper[lead];
                    if (d == UCaseProps.LatinCase.EXC) { break; }
                    ++srcIndex;
                    if (d == 0) { continue; }
                    delta = d;
                } else if (lead >= 0xd800) {
                    break;  // surrogate or higher
                } else {
                    int props = CASE_TRIE.getFromU16SingleLead(lead);
                    if (UCaseProps.propsHasException(props)) { break; }
                    ++srcIndex;
                    if (UCaseProps.getTypeFromProps(props) != UCaseProps.LOWER ||
                            (delta = UCaseProps.getDelta(props)) == 0) {
                        continue;
                    }
                }
                lead += (char)delta;
                appendUnchanged(src, prev, srcIndex - 1 - prev, dest, options, edits);
                dest.append(lead);
                if (edits != null) {
                    edits.addReplace(1, 1);
                }
                prev = srcIndex;
            }
            // slow path
            int cpStart = srcIndex++;
            char trail;
            int c;
            if (Character.isHighSurrogate(lead) && srcIndex < srcLength &&
                    Character.isLowSurrogate(trail = src.charAt(srcIndex))) {
                c = Character.toCodePoint(lead, trail);
                ++srcIndex;
            } else {
                c = lead;
            }
            if (iter == null) {
                iter = new StringContextIterator(src, cpStart, srcIndex);
            } else {
                iter.setCPStartAndLimit(cpStart, srcIndex);
            }
            // We need to append unchanged text before calling UCaseProps.toFullUpper()
            // because it will sometimes append its mapping to dest,
            // and that must be after copying the previous text.
            appendUnchanged(src, prev, cpStart - prev, dest, options, edits);
            prev = cpStart;
            c = UCaseProps.INSTANCE.toFullUpper(c, iter, dest, caseLocale);
            if (c >= 0) {
                appendResult(c, dest, srcIndex - cpStart, options, edits);
                prev = srcIndex;
            }
        }
        appendUnchanged(src, prev, srcIndex - prev, dest, options, edits);
    }

    public static String toLower(int caseLocale, int options, CharSequence src) {
        if (src.length() <= 100 && (options & OMIT_UNCHANGED_TEXT) == 0) {
            if (src.length() == 0) {
                return src.toString();
            }
            // Collect and apply only changes.
            // Good if no or few changes. Bad (slow) if many changes.
            Edits edits = new Edits();
            StringBuilder replacementChars = toLower(
                    caseLocale, options | OMIT_UNCHANGED_TEXT, src, new StringBuilder(), edits);
            return applyEdits(src, replacementChars, edits);
        } else {
            return toLower(caseLocale, options, src,
                    new StringBuilder(src.length()), null).toString();
        }
    }

    public static <A extends Appendable> A toLower(int caseLocale, int options,
            CharSequence src, A dest, Edits edits) {
        try {
            if (edits != null) {
                edits.reset();
            }
            internalToLower(caseLocale, options, src, 0, src.length(), null, dest, edits);
            return dest;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String toUpper(int caseLocale, int options, CharSequence src) {
        if (src.length() <= 100 && (options & OMIT_UNCHANGED_TEXT) == 0) {
            if (src.length() == 0) {
                return src.toString();
            }
            // Collect and apply only changes.
            // Good if no or few changes. Bad (slow) if many changes.
            Edits edits = new Edits();
            StringBuilder replacementChars = toUpper(
                    caseLocale, options | OMIT_UNCHANGED_TEXT, src, new StringBuilder(), edits);
            return applyEdits(src, replacementChars, edits);
        } else {
            return toUpper(caseLocale, options, src,
                    new StringBuilder(src.length()), null).toString();
        }
    }

    public static <A extends Appendable> A toUpper(int caseLocale, int options,
            CharSequence src, A dest, Edits edits) {
        try {
            if (edits != null) {
                edits.reset();
            }
            if (caseLocale == UCaseProps.LOC_GREEK) {
                return GreekUpper.toUpper(options, src, dest, edits);
            }
            internalToUpper(caseLocale, options, src, dest, edits);
            return dest;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String toTitle(int caseLocale, int options, BreakIterator iter, CharSequence src) {
        if (src.length() <= 100 && (options & OMIT_UNCHANGED_TEXT) == 0) {
            if (src.length() == 0) {
                return src.toString();
            }
            // Collect and apply only changes.
            // Good if no or few changes. Bad (slow) if many changes.
            Edits edits = new Edits();
            StringBuilder replacementChars = toTitle(
                    caseLocale, options | OMIT_UNCHANGED_TEXT, iter, src,
                    new StringBuilder(), edits);
            return applyEdits(src, replacementChars, edits);
        } else {
            return toTitle(caseLocale, options, iter, src,
                    new StringBuilder(src.length()), null).toString();
        }
    }

    public static <A extends Appendable> A toTitle(
            int caseLocale, int options, BreakIterator titleIter,
            CharSequence src, A dest, Edits edits) {
        try {
            if (edits != null) {
                edits.reset();
            }

            /* set up local variables */
            StringContextIterator iter = new StringContextIterator(src);
            int srcLength = src.length();
            int prev=0;
            boolean isFirstIndex=true;

            /* titlecasing loop */
            while(prev<srcLength) {
                /* find next index where to titlecase */
                int index;
                if(isFirstIndex) {
                    isFirstIndex=false;
                    index=titleIter.first();
                } else {
                    index=titleIter.next();
                }
                if(index==BreakIterator.DONE || index>srcLength) {
                    index=srcLength;
                }

                /*
                 * Segment [prev..index[ into 3 parts:
                 * a) skipped characters (copy as-is) [prev..titleStart[
                 * b) first letter (titlecase)              [titleStart..titleLimit[
                 * c) subsequent characters (lowercase)                 [titleLimit..index[
                 */
                if(prev<index) {
                    // Find and copy skipped characters [prev..titleStart[
                    int titleStart=prev;
                    iter.setLimit(index);
                    int c=iter.nextCaseMapCP();
                    if ((options&UCharacter.TITLECASE_NO_BREAK_ADJUSTMENT)==0) {
                        // Adjust the titlecasing index to the next cased character,
                        // or to the next letter/number/symbol/private use.
                        // Stop with titleStart<titleLimit<=index
                        // if there is a character to be titlecased,
                        // or else stop with titleStart==titleLimit==index.
                        boolean toCased = (options&CaseMapImpl.TITLECASE_ADJUST_TO_CASED) != 0;
                        while ((toCased ?
                                    UCaseProps.NONE==UCaseProps.INSTANCE.getType(c) :
                                        !CaseMapImpl.isLNS(c)) &&
                                (c=iter.nextCaseMapCP())>=0) {}
                        // If c<0 then we have only uncased characters in [prev..index[
                        // and stopped with titleStart==titleLimit==index.
                        titleStart=iter.getCPStart();
                        if (prev < titleStart) {
                            appendUnchanged(src, prev, titleStart-prev, dest, options, edits);
                        }
                    }

                    if(titleStart<index) {
                        // titlecase c which is from [titleStart..titleLimit[
                        c = UCaseProps.INSTANCE.toFullTitle(c, iter, dest, caseLocale);
                        appendResult(c, dest, iter.getCPLength(), options, edits);

                        // Special case Dutch IJ titlecasing
                        int titleLimit;
                        if (titleStart+1 < index && caseLocale == UCaseProps.LOC_DUTCH) {
                            if (c < 0) {
                                c = ~c;
                            }
                            if (c == 'I' || c == '\u00cd') {
                                titleLimit = maybeTitleDutchIJ(src, c, titleStart + 1, index, dest, options, edits);
                                iter.moveTo(titleLimit);
                            }
                            else {
                                titleLimit = iter.getCPLimit();
                            }
                        } else {
                            titleLimit = iter.getCPLimit();
                        }

                        // lowercase [titleLimit..index[
                        if(titleLimit<index) {
                            if((options&UCharacter.TITLECASE_NO_LOWERCASE)==0) {
                                // Normal operation: Lowercase the rest of the word.
                                internalToLower(caseLocale, options,
                                        src, titleLimit, index, iter, dest, edits);
                            } else {
                                // Optionally just copy the rest of the word unchanged.
                                appendUnchanged(src, titleLimit, index-titleLimit, dest, options, edits);
                            }
                            iter.moveToLimit();
                        }
                    }
                }

                prev=index;
            }
            return dest;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Input: c is a letter I with or without acute accent.
     * start is the index in src after c, and is less than segmentLimit.
     * If a plain i/I is followed by a plain j/J,
     * or an i/I with acute (precomposed or decomposed) is followed by a j/J with acute,
     * then we output accordingly.
     *
     * @return the src index after the titlecased sequence, or the start index if no Dutch IJ
     * @throws IOException
     */
    private static <A extends Appendable> int maybeTitleDutchIJ(
            CharSequence src, int c, int start, int segmentLimit,
            A dest, int options, Edits edits) throws IOException {
        assert start < segmentLimit;

        int index = start;
        boolean withAcute = false;

        // If the conditions are met, then the following variables tell us what to output.
        int unchanged1 = 0;  // code units before the j, or the whole sequence (0..3)
        boolean doTitleJ = false;  // true if the j needs to be titlecased
        int unchanged2 = 0;  // after the j (0 or 1)

        // next character after the first letter
        char c2 = src.charAt(index++);

        // Is the first letter an i/I with accent?
        if (c == 'I') {
            if (c2 == ACUTE) {
                withAcute = true;
                unchanged1 = 1;
                if (index == segmentLimit) { return start; }
                c2 = src.charAt(index++);
            }
        } else {  // \u00cd
            withAcute = true;
        }
        // Is the next character a j/J?
        if (c2 == 'j') {
            doTitleJ = true;
        } else if (c2 == 'J') {
            ++unchanged1;
        } else {
            return start;
        }
        // A plain i/I must be followed by a plain j/J.
        // An i/I with acute must be followed by a j/J with acute.
        if (withAcute) {
            if (index == segmentLimit || src.charAt(index++) != ACUTE) { return start; }
            if (doTitleJ) {
                unchanged2 = 1;
            } else {
                ++unchanged1;
            }
        }
        // There must not be another combining mark.
        if (index < segmentLimit) {
            int cp = Character.codePointAt(src, index);
            int bit = 1 << UCharacter.getType(cp);
            if ((bit & U_GC_M_MASK) != 0) {
                return start;
            }
        }
        // Output the rest of the Dutch IJ.
        appendUnchanged(src, start, unchanged1, dest, options, edits);
        start += unchanged1;
        if (doTitleJ) {
            dest.append('J');
            if (edits != null) {
                edits.addReplace(1, 1);
            }
            ++start;
        }
        appendUnchanged(src, start, unchanged2, dest, options, edits);
        assert start + unchanged2 == index;
        return index;
    }

    public static String fold(int options, CharSequence src) {
        if (src.length() <= 100 && (options & OMIT_UNCHANGED_TEXT) == 0) {
            if (src.length() == 0) {
                return src.toString();
            }
            // Collect and apply only changes.
            // Good if no or few changes. Bad (slow) if many changes.
            Edits edits = new Edits();
            StringBuilder replacementChars = fold(
                    options | OMIT_UNCHANGED_TEXT, src, new StringBuilder(), edits);
            return applyEdits(src, replacementChars, edits);
        } else {
            return fold(options, src, new StringBuilder(src.length()), null).toString();
        }
    }

    public static <A extends Appendable> A fold(int options,
            CharSequence src, A dest, Edits edits) {
        try {
            if (edits != null) {
                edits.reset();
            }
            internalToLower(-1, options, src, 0, src.length(), null, dest, edits);
            return dest;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final class GreekUpper {
        // Data bits.
        private static final int UPPER_MASK = 0x3ff;
        private static final int HAS_VOWEL = 0x1000;
        private static final int HAS_YPOGEGRAMMENI = 0x2000;
        private static final int HAS_ACCENT = 0x4000;
        private static final int HAS_DIALYTIKA = 0x8000;
        // Further bits during data building and processing, not stored in the data map.
        private static final int HAS_COMBINING_DIALYTIKA = 0x10000;
        private static final int HAS_OTHER_GREEK_DIACRITIC = 0x20000;

        private static final int HAS_VOWEL_AND_ACCENT = HAS_VOWEL | HAS_ACCENT;
        private static final int HAS_VOWEL_AND_ACCENT_AND_DIALYTIKA =
                HAS_VOWEL_AND_ACCENT | HAS_DIALYTIKA;
        private static final int HAS_EITHER_DIALYTIKA = HAS_DIALYTIKA | HAS_COMBINING_DIALYTIKA;

        // State bits.
        private static final int AFTER_CASED = 1;
        private static final int AFTER_VOWEL_WITH_ACCENT = 2;

        // Data generated by prototype code, see
        // https://icu.unicode.org/design/case/greek-upper
        // TODO: Move this data into ucase.icu.
        private static final char[] data0370 = {
            // U+0370..03FF
            0x0370,  // \u0370
            0x0370,  // \u0371
            0x0372,  // \u0372
            0x0372,  // \u0373
            0,
            0,
            0x0376,  // \u0376
            0x0376,  // \u0377
            0,
            0,
            0x037A,  // \u037a
            0x03FD,  // \u037b
            0x03FE,  // \u037c
            0x03FF,  // \u037d
            0,
            0x037F,  // \u037f
            0,
            0,
            0,
            0,
            0,
            0,
            0x0391 | HAS_VOWEL | HAS_ACCENT,  // \u0386
            0,
            0x0395 | HAS_VOWEL | HAS_ACCENT,  // \u0388
            0x0397 | HAS_VOWEL | HAS_ACCENT,  // \u0389
            0x0399 | HAS_VOWEL | HAS_ACCENT,  // \u038a
            0,
            0x039F | HAS_VOWEL | HAS_ACCENT,  // \u038c
            0,
            0x03A5 | HAS_VOWEL | HAS_ACCENT,  // \u038e
            0x03A9 | HAS_VOWEL | HAS_ACCENT,  // \u038f
            0x0399 | HAS_VOWEL | HAS_ACCENT | HAS_DIALYTIKA,  // \u0390
            0x0391 | HAS_VOWEL,  // \u0391
            0x0392,  // \u0392
            0x0393,  // \u0393
            0x0394,  // \u0394
            0x0395 | HAS_VOWEL,  // \u0395
            0x0396,  // \u0396
            0x0397 | HAS_VOWEL,  // \u0397
            0x0398,  // \u0398
            0x0399 | HAS_VOWEL,  // \u0399
            0x039A,  // \u039a
            0x039B,  // \u039b
            0x039C,  // \u039c
            0x039D,  // \u039d
            0x039E,  // \u039e
            0x039F | HAS_VOWEL,  // \u039f
            0x03A0,  // \u03a0
            0x03A1,  // \u03a1
            0,
            0x03A3,  // \u03a3
            0x03A4,  // \u03a4
            0x03A5 | HAS_VOWEL,  // \u03a5
            0x03A6,  // \u03a6
            0x03A7,  // \u03a7
            0x03A8,  // \u03a8
            0x03A9 | HAS_VOWEL,  // \u03a9
            0x0399 | HAS_VOWEL | HAS_DIALYTIKA,  // \u03aa
            0x03A5 | HAS_VOWEL | HAS_DIALYTIKA,  // \u03ab
            0x0391 | HAS_VOWEL | HAS_ACCENT,  // \u03ac
            0x0395 | HAS_VOWEL | HAS_ACCENT,  // \u03ad
            0x0397 | HAS_VOWEL | HAS_ACCENT,  // \u03ae
            0x0399 | HAS_VOWEL | HAS_ACCENT,  // \u03af
            0x03A5 | HAS_VOWEL | HAS_ACCENT | HAS_DIALYTIKA,  // \u03b0
            0x0391 | HAS_VOWEL,  // \u03b1
            0x0392,  // \u03b2
            0x0393,  // \u03b3
            0x0394,  // \u03b4
            0x0395 | HAS_VOWEL,  // \u03b5
            0x0396,  // \u03b6
            0x0397 | HAS_VOWEL,  // \u03b7
            0x0398,  // \u03b8
            0x0399 | HAS_VOWEL,  // \u03b9
            0x039A,  // \u03ba
            0x039B,  // \u03bb
            0x039C,  // \u03bc
            0x039D,  // \u03bd
            0x039E,  // \u03be
            0x039F | HAS_VOWEL,  // \u03bf
            0x03A0,  // \u03c0
            0x03A1,  // \u03c1
            0x03A3,  // \u03c2
            0x03A3,  // \u03c3
            0x03A4,  // \u03c4
            0x03A5 | HAS_VOWEL,  // \u03c5
            0x03A6,  // \u03c6
            0x03A7,  // \u03c7
            0x03A8,  // \u03c8
            0x03A9 | HAS_VOWEL,  // \u03c9
            0x0399 | HAS_VOWEL | HAS_DIALYTIKA,  // \u03ca
            0x03A5 | HAS_VOWEL | HAS_DIALYTIKA,  // \u03cb
            0x039F | HAS_VOWEL | HAS_ACCENT,  // \u03cc
            0x03A5 | HAS_VOWEL | HAS_ACCENT,  // \u03cd
            0x03A9 | HAS_VOWEL | HAS_ACCENT,  // \u03ce
            0x03CF,  // \u03cf
            0x0392,  // \u03d0
            0x0398,  // \u03d1
            0x03D2,  // \u03d2
            0x03D2 | HAS_ACCENT,  // \u03d3
            0x03D2 | HAS_DIALYTIKA,  // \u03d4
            0x03A6,  // \u03d5
            0x03A0,  // \u03d6
            0x03CF,  // \u03d7
            0x03D8,  // \u03d8
            0x03D8,  // \u03d9
            0x03DA,  // \u03da
            0x03DA,  // \u03db
            0x03DC,  // \u03dc
            0x03DC,  // \u03dd
            0x03DE,  // \u03de
            0x03DE,  // \u03df
            0x03E0,  // \u03e0
            0x03E0,  // \u03e1
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0x039A,  // \u03f0
            0x03A1,  // \u03f1
            0x03F9,  // \u03f2
            0x037F,  // \u03f3
            0x03F4,  // \u03f4
            0x0395 | HAS_VOWEL,  // \u03f5
            0,
            0x03F7,  // \u03f7
            0x03F7,  // \u03f8
            0x03F9,  // \u03f9
            0x03FA,  // \u03fa
            0x03FA,  // \u03fb
            0x03FC,  // \u03fc
            0x03FD,  // \u03fd
            0x03FE,  // \u03fe
            0x03FF,  // \u03ff
        };

        private static final char[] data1F00 = {
            // U+1F00..1FFF
            0x0391 | HAS_VOWEL,  // \u1f00
            0x0391 | HAS_VOWEL,  // \u1f01
            0x0391 | HAS_VOWEL | HAS_ACCENT,  // \u1f02
            0x0391 | HAS_VOWEL | HAS_ACCENT,  // \u1f03
            0x0391 | HAS_VOWEL | HAS_ACCENT,  // \u1f04
            0x0391 | HAS_VOWEL | HAS_ACCENT,  // \u1f05
            0x0391 | HAS_VOWEL | HAS_ACCENT,  // \u1f06
            0x0391 | HAS_VOWEL | HAS_ACCENT,  // \u1f07
            0x0391 | HAS_VOWEL,  // \u1f08
            0x0391 | HAS_VOWEL,  // \u1f09
            0x0391 | HAS_VOWEL | HAS_ACCENT,  // \u1f0a
            0x0391 | HAS_VOWEL | HAS_ACCENT,  // \u1f0b
            0x0391 | HAS_VOWEL | HAS_ACCENT,  // \u1f0c
            0x0391 | HAS_VOWEL | HAS_ACCENT,  // \u1f0d
            0x0391 | HAS_VOWEL | HAS_ACCENT,  // \u1f0e
            0x0391 | HAS_VOWEL | HAS_ACCENT,  // \u1f0f
            0x0395 | HAS_VOWEL,  // \u1f10
            0x0395 | HAS_VOWEL,  // \u1f11
            0x0395 | HAS_VOWEL | HAS_ACCENT,  // \u1f12
            0x0395 | HAS_VOWEL | HAS_ACCENT,  // \u1f13
            0x0395 | HAS_VOWEL | HAS_ACCENT,  // \u1f14
            0x0395 | HAS_VOWEL | HAS_ACCENT,  // \u1f15
            0,
            0,
            0x0395 | HAS_VOWEL,  // \u1f18
            0x0395 | HAS_VOWEL,  // \u1f19
            0x0395 | HAS_VOWEL | HAS_ACCENT,  // \u1f1a
            0x0395 | HAS_VOWEL | HAS_ACCENT,  // \u1f1b
            0x0395 | HAS_VOWEL | HAS_ACCENT,  // \u1f1c
            0x0395 | HAS_VOWEL | HAS_ACCENT,  // \u1f1d
            0,
            0,
            0x0397 | HAS_VOWEL,  // \u1f20
            0x0397 | HAS_VOWEL,  // \u1f21
            0x0397 | HAS_VOWEL | HAS_ACCENT,  // \u1f22
            0x0397 | HAS_VOWEL | HAS_ACCENT,  // \u1f23
            0x0397 | HAS_VOWEL | HAS_ACCENT,  // \u1f24
            0x0397 | HAS_VOWEL | HAS_ACCENT,  // \u1f25
            0x0397 | HAS_VOWEL | HAS_ACCENT,  // \u1f26
            0x0397 | HAS_VOWEL | HAS_ACCENT,  // \u1f27
            0x0397 | HAS_VOWEL,  // \u1f28
            0x0397 | HAS_VOWEL,  // \u1f29
            0x0397 | HAS_VOWEL | HAS_ACCENT,  // \u1f2a
            0x0397 | HAS_VOWEL | HAS_ACCENT,  // \u1f2b
            0x0397 | HAS_VOWEL | HAS_ACCENT,  // \u1f2c
            0x0397 | HAS_VOWEL | HAS_ACCENT,  // \u1f2d
            0x0397 | HAS_VOWEL | HAS_ACCENT,  // \u1f2e
            0x0397 | HAS_VOWEL | HAS_ACCENT,  // \u1f2f
            0x0399 | HAS_VOWEL,  // \u1f30
            0x0399 | HAS_VOWEL,  // \u1f31
            0x0399 | HAS_VOWEL | HAS_ACCENT,  // \u1f32
            0x0399 | HAS_VOWEL | HAS_ACCENT,  // \u1f33
            0x0399 | HAS_VOWEL | HAS_ACCENT,  // \u1f34
            0x0399 | HAS_VOWEL | HAS_ACCENT,  // \u1f35
            0x0399 | HAS_VOWEL | HAS_ACCENT,  // \u1f36
            0x0399 | HAS_VOWEL | HAS_ACCENT,  // \u1f37
            0x0399 | HAS_VOWEL,  // \u1f38
            0x0399 | HAS_VOWEL,  // \u1f39
            0x0399 | HAS_VOWEL | HAS_ACCENT,  // \u1f3a
            0x0399 | HAS_VOWEL | HAS_ACCENT,  // \u1f3b
            0x0399 | HAS_VOWEL | HAS_ACCENT,  // \u1f3c
            0x0399 | HAS_VOWEL | HAS_ACCENT,  // \u1f3d
            0x0399 | HAS_VOWEL | HAS_ACCENT,  // \u1f3e
            0x0399 | HAS_VOWEL | HAS_ACCENT,  // \u1f3f
            0x039F | HAS_VOWEL,  // \u1f40
            0x039F | HAS_VOWEL,  // \u1f41
            0x039F | HAS_VOWEL | HAS_ACCENT,  // \u1f42
            0x039F | HAS_VOWEL | HAS_ACCENT,  // \u1f43
            0x039F | HAS_VOWEL | HAS_ACCENT,  // \u1f44
            0x039F | HAS_VOWEL | HAS_ACCENT,  // \u1f45
            0,
            0,
            0x039F | HAS_VOWEL,  // \u1f48
            0x039F | HAS_VOWEL,  // \u1f49
            0x039F | HAS_VOWEL | HAS_ACCENT,  // \u1f4a
            0x039F | HAS_VOWEL | HAS_ACCENT,  // \u1f4b
            0x039F | HAS_VOWEL | HAS_ACCENT,  // \u1f4c
            0x039F | HAS_VOWEL | HAS_ACCENT,  // \u1f4d
            0,
            0,
            0x03A5 | HAS_VOWEL,  // \u1f50
            0x03A5 | HAS_VOWEL,  // \u1f51
            0x03A5 | HAS_VOWEL | HAS_ACCENT,  // \u1f52
            0x03A5 | HAS_VOWEL | HAS_ACCENT,  // \u1f53
            0x03A5 | HAS_VOWEL | HAS_ACCENT,  // \u1f54
            0x03A5 | HAS_VOWEL | HAS_ACCENT,  // \u1f55
            0x03A5 | HAS_VOWEL | HAS_ACCENT,  // \u1f56
            0x03A5 | HAS_VOWEL | HAS_ACCENT,  // \u1f57
            0,
            0x03A5 | HAS_VOWEL,  // \u1f59
            0,
            0x03A5 | HAS_VOWEL | HAS_ACCENT,  // \u1f5b
            0,
            0x03A5 | HAS_VOWEL | HAS_ACCENT,  // \u1f5d
            0,
            0x03A5 | HAS_VOWEL | HAS_ACCENT,  // \u1f5f
            0x03A9 | HAS_VOWEL,  // \u1f60
            0x03A9 | HAS_VOWEL,  // \u1f61
            0x03A9 | HAS_VOWEL | HAS_ACCENT,  // \u1f62
            0x03A9 | HAS_VOWEL | HAS_ACCENT,  // \u1f63
            0x03A9 | HAS_VOWEL | HAS_ACCENT,  // \u1f64
            0x03A9 | HAS_VOWEL | HAS_ACCENT,  // \u1f65
            0x03A9 | HAS_VOWEL | HAS_ACCENT,  // \u1f66
            0x03A9 | HAS_VOWEL | HAS_ACCENT,  // \u1f67
            0x03A9 | HAS_VOWEL,  // \u1f68
            0x03A9 | HAS_VOWEL,  // \u1f69
            0x03A9 | HAS_VOWEL | HAS_ACCENT,  // \u1f6a
            0x03A9 | HAS_VOWEL | HAS_ACCENT,  // \u1f6b
            0x03A9 | HAS_VOWEL | HAS_ACCENT,  // \u1f6c
            0x03A9 | HAS_VOWEL | HAS_ACCENT,  // \u1f6d
            0x03A9 | HAS_VOWEL | HAS_ACCENT,  // \u1f6e
            0x03A9 | HAS_VOWEL | HAS_ACCENT,  // \u1f6f
            0x0391 | HAS_VOWEL | HAS_ACCENT,  // \u1f70
            0x0391 | HAS_VOWEL | HAS_ACCENT,  // \u1f71
            0x0395 | HAS_VOWEL | HAS_ACCENT,  // \u1f72
            0x0395 | HAS_VOWEL | HAS_ACCENT,  // \u1f73
            0x0397 | HAS_VOWEL | HAS_ACCENT,  // \u1f74
            0x0397 | HAS_VOWEL | HAS_ACCENT,  // \u1f75
            0x0399 | HAS_VOWEL | HAS_ACCENT,  // \u1f76
            0x0399 | HAS_VOWEL | HAS_ACCENT,  // \u1f77
            0x039F | HAS_VOWEL | HAS_ACCENT,  // \u1f78
            0x039F | HAS_VOWEL | HAS_ACCENT,  // \u1f79
            0x03A5 | HAS_VOWEL | HAS_ACCENT,  // \u1f7a
            0x03A5 | HAS_VOWEL | HAS_ACCENT,  // \u1f7b
            0x03A9 | HAS_VOWEL | HAS_ACCENT,  // \u1f7c
            0x03A9 | HAS_VOWEL | HAS_ACCENT,  // \u1f7d
            0,
            0,
            0x0391 | HAS_VOWEL | HAS_YPOGEGRAMMENI,  // \u1f80
            0x0391 | HAS_VOWEL | HAS_YPOGEGRAMMENI,  // \u1f81
            0x0391 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1f82
            0x0391 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1f83
            0x0391 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1f84
            0x0391 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1f85
            0x0391 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1f86
            0x0391 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1f87
            0x0391 | HAS_VOWEL | HAS_YPOGEGRAMMENI,  // \u1f88
            0x0391 | HAS_VOWEL | HAS_YPOGEGRAMMENI,  // \u1f89
            0x0391 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1f8a
            0x0391 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1f8b
            0x0391 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1f8c
            0x0391 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1f8d
            0x0391 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1f8e
            0x0391 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1f8f
            0x0397 | HAS_VOWEL | HAS_YPOGEGRAMMENI,  // \u1f90
            0x0397 | HAS_VOWEL | HAS_YPOGEGRAMMENI,  // \u1f91
            0x0397 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1f92
            0x0397 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1f93
            0x0397 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1f94
            0x0397 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1f95
            0x0397 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1f96
            0x0397 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1f97
            0x0397 | HAS_VOWEL | HAS_YPOGEGRAMMENI,  // \u1f98
            0x0397 | HAS_VOWEL | HAS_YPOGEGRAMMENI,  // \u1f99
            0x0397 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1f9a
            0x0397 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1f9b
            0x0397 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1f9c
            0x0397 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1f9d
            0x0397 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1f9e
            0x0397 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1f9f
            0x03A9 | HAS_VOWEL | HAS_YPOGEGRAMMENI,  // \u1fa0
            0x03A9 | HAS_VOWEL | HAS_YPOGEGRAMMENI,  // \u1fa1
            0x03A9 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1fa2
            0x03A9 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1fa3
            0x03A9 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1fa4
            0x03A9 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1fa5
            0x03A9 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1fa6
            0x03A9 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1fa7
            0x03A9 | HAS_VOWEL | HAS_YPOGEGRAMMENI,  // \u1fa8
            0x03A9 | HAS_VOWEL | HAS_YPOGEGRAMMENI,  // \u1fa9
            0x03A9 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1faa
            0x03A9 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1fab
            0x03A9 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1fac
            0x03A9 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1fad
            0x03A9 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1fae
            0x03A9 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1faf
            0x0391 | HAS_VOWEL,  // \u1fb0
            0x0391 | HAS_VOWEL,  // \u1fb1
            0x0391 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1fb2
            0x0391 | HAS_VOWEL | HAS_YPOGEGRAMMENI,  // \u1fb3
            0x0391 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1fb4
            0,
            0x0391 | HAS_VOWEL | HAS_ACCENT,  // \u1fb6
            0x0391 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1fb7
            0x0391 | HAS_VOWEL,  // \u1fb8
            0x0391 | HAS_VOWEL,  // \u1fb9
            0x0391 | HAS_VOWEL | HAS_ACCENT,  // \u1fba
            0x0391 | HAS_VOWEL | HAS_ACCENT,  // \u1fbb
            0x0391 | HAS_VOWEL | HAS_YPOGEGRAMMENI,  // \u1fbc
            0,
            0x0399 | HAS_VOWEL,  // \u1fbe
            0,
            0,
            0,
            0x0397 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1fc2
            0x0397 | HAS_VOWEL | HAS_YPOGEGRAMMENI,  // \u1fc3
            0x0397 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1fc4
            0,
            0x0397 | HAS_VOWEL | HAS_ACCENT,  // \u1fc6
            0x0397 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1fc7
            0x0395 | HAS_VOWEL | HAS_ACCENT,  // \u1fc8
            0x0395 | HAS_VOWEL | HAS_ACCENT,  // \u1fc9
            0x0397 | HAS_VOWEL | HAS_ACCENT,  // \u1fca
            0x0397 | HAS_VOWEL | HAS_ACCENT,  // \u1fcb
            0x0397 | HAS_VOWEL | HAS_YPOGEGRAMMENI,  // \u1fcc
            0,
            0,
            0,
            0x0399 | HAS_VOWEL,  // \u1fd0
            0x0399 | HAS_VOWEL,  // \u1fd1
            0x0399 | HAS_VOWEL | HAS_ACCENT | HAS_DIALYTIKA,  // \u1fd2
            0x0399 | HAS_VOWEL | HAS_ACCENT | HAS_DIALYTIKA,  // \u1fd3
            0,
            0,
            0x0399 | HAS_VOWEL | HAS_ACCENT,  // \u1fd6
            0x0399 | HAS_VOWEL | HAS_ACCENT | HAS_DIALYTIKA,  // \u1fd7
            0x0399 | HAS_VOWEL,  // \u1fd8
            0x0399 | HAS_VOWEL,  // \u1fd9
            0x0399 | HAS_VOWEL | HAS_ACCENT,  // \u1fda
            0x0399 | HAS_VOWEL | HAS_ACCENT,  // \u1fdb
            0,
            0,
            0,
            0,
            0x03A5 | HAS_VOWEL,  // \u1fe0
            0x03A5 | HAS_VOWEL,  // \u1fe1
            0x03A5 | HAS_VOWEL | HAS_ACCENT | HAS_DIALYTIKA,  // \u1fe2
            0x03A5 | HAS_VOWEL | HAS_ACCENT | HAS_DIALYTIKA,  // \u1fe3
            0x03A1,  // \u1fe4
            0x03A1,  // \u1fe5
            0x03A5 | HAS_VOWEL | HAS_ACCENT,  // \u1fe6
            0x03A5 | HAS_VOWEL | HAS_ACCENT | HAS_DIALYTIKA,  // \u1fe7
            0x03A5 | HAS_VOWEL,  // \u1fe8
            0x03A5 | HAS_VOWEL,  // \u1fe9
            0x03A5 | HAS_VOWEL | HAS_ACCENT,  // \u1fea
            0x03A5 | HAS_VOWEL | HAS_ACCENT,  // \u1feb
            0x03A1,  // \u1fec
            0,
            0,
            0,
            0,
            0,
            0x03A9 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1ff2
            0x03A9 | HAS_VOWEL | HAS_YPOGEGRAMMENI,  // \u1ff3
            0x03A9 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1ff4
            0,
            0x03A9 | HAS_VOWEL | HAS_ACCENT,  // \u1ff6
            0x03A9 | HAS_VOWEL | HAS_YPOGEGRAMMENI | HAS_ACCENT,  // \u1ff7
            0x039F | HAS_VOWEL | HAS_ACCENT,  // \u1ff8
            0x039F | HAS_VOWEL | HAS_ACCENT,  // \u1ff9
            0x03A9 | HAS_VOWEL | HAS_ACCENT,  // \u1ffa
            0x03A9 | HAS_VOWEL | HAS_ACCENT,  // \u1ffb
            0x03A9 | HAS_VOWEL | HAS_YPOGEGRAMMENI,  // \u1ffc
            0,
            0,
            0,
        };

        // U+2126 Ohm sign
        private static final char data2126 = 0x03A9 | HAS_VOWEL;  // \u2126

        private static final int getLetterData(int c) {
            if (c < 0x370 || 0x2126 < c || (0x3ff < c && c < 0x1f00)) {
                return 0;
            } else if (c <= 0x3ff) {
                return data0370[c - 0x370];
            } else if (c <= 0x1fff) {
                return data1F00[c - 0x1f00];
            } else if (c == 0x2126) {
                return data2126;
            } else {
                return 0;
            }
        }

        /**
         * Returns a non-zero value for each of the Greek combining diacritics
         * listed in The Unicode Standard, version 8, chapter 7.2 Greek,
         * plus some perispomeni look-alikes.
         */
        private static final int getDiacriticData(int c) {
            switch (c) {
            case '\u0300':  // varia
            case '\u0301':  // tonos = oxia
            case '\u0342':  // perispomeni
            case '\u0302':  // circumflex can look like perispomeni
            case '\u0303':  // tilde can look like perispomeni
            case '\u0311':  // inverted breve can look like perispomeni
                return HAS_ACCENT;
            case '\u0308':  // dialytika = diaeresis
                return HAS_COMBINING_DIALYTIKA;
            case '\u0344':  // dialytika tonos
                return HAS_COMBINING_DIALYTIKA | HAS_ACCENT;
            case '\u0345':  // ypogegrammeni = iota subscript
                return HAS_YPOGEGRAMMENI;
            case '\u0304':  // macron
            case '\u0306':  // breve
            case '\u0313':  // comma above
            case '\u0314':  // reversed comma above
            case '\u0343':  // koronis
                return HAS_OTHER_GREEK_DIACRITIC;
            default:
                return 0;
            }
        }

        private static boolean isFollowedByCasedLetter(CharSequence s, int i) {
            while (i < s.length()) {
                int c = Character.codePointAt(s, i);
                int type = UCaseProps.INSTANCE.getTypeOrIgnorable(c);
                if ((type & UCaseProps.IGNORABLE) != 0) {
                    // Case-ignorable, continue with the loop.
                    i += Character.charCount(c);
                } else if (type != UCaseProps.NONE) {
                    return true;  // Followed by cased letter.
                } else {
                    return false;  // Uncased and not case-ignorable.
                }
            }
            return false;  // Not followed by cased letter.
        }

        /**
         * Greek string uppercasing with a state machine.
         * Probably simpler than a stateless function that has to figure out complex context-before
         * for each character.
         * TODO: Try to re-consolidate one way or another with the non-Greek function.
         *
         * <p>Keep this consistent with the C++ versions in ustrcase.cpp (UTF-16) and ucasemap.cpp (UTF-8).
         * @throws IOException
         */
        private static <A extends Appendable> A toUpper(int options,
                CharSequence src, A dest, Edits edits) throws IOException {
            int state = 0;
            for (int i = 0; i < src.length();) {
                int c = Character.codePointAt(src, i);
                int nextIndex = i + Character.charCount(c);
                int nextState = 0;
                int type = UCaseProps.INSTANCE.getTypeOrIgnorable(c);
                if ((type & UCaseProps.IGNORABLE) != 0) {
                    // c is case-ignorable
                    nextState |= (state & AFTER_CASED);
                } else if (type != UCaseProps.NONE) {
                    // c is cased
                    nextState |= AFTER_CASED;
                }
                int data = getLetterData(c);
                if (data > 0) {
                    int upper = data & UPPER_MASK;
                    // Add a dialytika to this iota or ypsilon vowel
                    // if we removed a tonos from the previous vowel,
                    // and that previous vowel did not also have (or gain) a dialytika.
                    // Adding one only to the final vowel in a longer sequence
                    // (which does not occur in normal writing) would require lookahead.
                    // Set the same flag as for preserving an existing dialytika.
                    if ((data & HAS_VOWEL) != 0 && (state & AFTER_VOWEL_WITH_ACCENT) != 0 &&
                            (upper == '\u0399' || upper == '\u03a5')) {
                        data |= HAS_DIALYTIKA;
                    }
                    int numYpogegrammeni = 0;  // Map each one to a trailing, spacing, capital iota.
                    if ((data & HAS_YPOGEGRAMMENI) != 0) {
                        numYpogegrammeni = 1;
                    }
                    // Skip combining diacritics after this Greek letter.
                    while (nextIndex < src.length()) {
                        int diacriticData = getDiacriticData(src.charAt(nextIndex));
                        if (diacriticData != 0) {
                            data |= diacriticData;
                            if ((diacriticData & HAS_YPOGEGRAMMENI) != 0) {
                                ++numYpogegrammeni;
                            }
                            ++nextIndex;
                        } else {
                            break;  // not a Greek diacritic
                        }
                    }
                    if ((data & HAS_VOWEL_AND_ACCENT_AND_DIALYTIKA) == HAS_VOWEL_AND_ACCENT) {
                        nextState |= AFTER_VOWEL_WITH_ACCENT;
                    }
                    // Map according to Greek rules.
                    boolean addTonos = false;
                    if (upper == '\u0397' &&
                            (data & HAS_ACCENT) != 0 &&
                            numYpogegrammeni == 0 &&
                            (state & AFTER_CASED) == 0 &&
                            !isFollowedByCasedLetter(src, nextIndex)) {
                        // Keep disjunctive "or" with (only) a tonos.
                        // We use the same "word boundary" conditions as for the Final_Sigma test.
                        if (i == nextIndex) {
                            upper = '\u0389';  // Preserve the precomposed form.
                        } else {
                            addTonos = true;
                        }
                    } else if ((data & HAS_DIALYTIKA) != 0) {
                        // Preserve a vowel with dialytika in precomposed form if it exists.
                        if (upper == '\u0399') {
                            upper = '\u03aa';
                            data &= ~HAS_EITHER_DIALYTIKA;
                        } else if (upper == '\u03a5') {
                            upper = '\u03ab';
                            data &= ~HAS_EITHER_DIALYTIKA;
                        }
                    }

                    boolean change;
                    if (edits == null && (options & OMIT_UNCHANGED_TEXT) == 0) {
                        change = true;  // common, simple usage
                    } else {
                        // Find out first whether we are changing the text.
                        change = src.charAt(i) != upper || numYpogegrammeni > 0;
                        int i2 = i + 1;
                        if ((data & HAS_EITHER_DIALYTIKA) != 0) {
                            change |= i2 >= nextIndex || src.charAt(i2) != 0x308;
                            ++i2;
                        }
                        if (addTonos) {
                            change |= i2 >= nextIndex || src.charAt(i2) != 0x301;
                            ++i2;
                        }
                        int oldLength = nextIndex - i;
                        int newLength = (i2 - i) + numYpogegrammeni;
                        change |= oldLength != newLength;
                        if (change) {
                            if (edits != null) {
                                edits.addReplace(oldLength, newLength);
                            }
                        } else {
                            if (edits != null) {
                                edits.addUnchanged(oldLength);
                            }
                            // Write unchanged text?
                            change = (options & OMIT_UNCHANGED_TEXT) == 0;
                        }
                    }

                    if (change) {
                        dest.append((char)upper);
                        if ((data & HAS_EITHER_DIALYTIKA) != 0) {
                            dest.append('\u0308');  // restore or add a dialytika
                        }
                        if (addTonos) {
                            dest.append('\u0301');
                        }
                        while (numYpogegrammeni > 0) {
                            dest.append('\u0399');
                            --numYpogegrammeni;
                        }
                    }
                } else {
                    c = UCaseProps.INSTANCE.toFullUpper(c, null, dest, UCaseProps.LOC_GREEK);
                    appendResult(c, dest, nextIndex - i, options, edits);
                }
                i = nextIndex;
                state = nextState;
            }
            return dest;
        }
    }
}
