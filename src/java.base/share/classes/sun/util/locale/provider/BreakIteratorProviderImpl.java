/*
 * Copyright (c) 1999, 2022, Oracle and/or its affiliates. All rights reserved.
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

package sun.util.locale.provider;

import java.text.BreakIterator;
import java.text.CharacterIterator;
import java.text.spi.BreakIteratorProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.Set;

import jdk.internal.util.regex.Grapheme;
import sun.text.DictionaryBasedBreakIterator;
import sun.text.RuleBasedBreakIterator;

/**
 * Concrete implementation of the  {@link java.text.spi.BreakIteratorProvider
 * BreakIteratorProvider} class for the JRE LocaleProviderAdapter.
 *
 * @author Naoto Sato
 * @author Masayoshi Okutsu
 */
public class BreakIteratorProviderImpl extends BreakIteratorProvider
                                       implements AvailableLanguageTags {

    private static final int WORD_INDEX = 0;
    private static final int LINE_INDEX = 1;
    private static final int SENTENCE_INDEX = 2;

    private final LocaleProviderAdapter.Type type;
    private final Set<String> langtags;

    public BreakIteratorProviderImpl(LocaleProviderAdapter.Type type, Set<String> langtags) {
        this.type = type;
        this.langtags = langtags;
    }

    /**
     * Returns an array of all locales for which this locale service provider
     * can provide localized objects or names.
     *
     * @return An array of all locales for which this locale service provider
     * can provide localized objects or names.
     */
    @Override
    public Locale[] getAvailableLocales() {
        return LocaleProviderAdapter.toLocaleArray(langtags);
    }

    /**
     * Returns a new <code>BreakIterator</code> instance
     * for <a href="../BreakIterator.html#word">word breaks</a>
     * for the given locale.
     * @param locale the desired locale
     * @return A break iterator for word breaks
     * @exception NullPointerException if <code>locale</code> is null
     * @exception IllegalArgumentException if <code>locale</code> isn't
     *     one of the locales returned from
     *     {@link java.util.spi.LocaleServiceProvider#getAvailableLocales()
     *     getAvailableLocales()}.
     * @see java.text.BreakIterator#getWordInstance(java.util.Locale)
     */
    @Override
    public BreakIterator getWordInstance(Locale locale) {
        return getBreakInstance(locale,
                                WORD_INDEX,
                                "WordData",
                                "WordDictionary");
    }

    /**
     * Returns a new <code>BreakIterator</code> instance
     * for <a href="../BreakIterator.html#line">line breaks</a>
     * for the given locale.
     * @param locale the desired locale
     * @return A break iterator for line breaks
     * @exception NullPointerException if <code>locale</code> is null
     * @exception IllegalArgumentException if <code>locale</code> isn't
     *     one of the locales returned from
     *     {@link java.util.spi.LocaleServiceProvider#getAvailableLocales()
     *     getAvailableLocales()}.
     * @see java.text.BreakIterator#getLineInstance(java.util.Locale)
     */
    @Override
    public BreakIterator getLineInstance(Locale locale) {
        return getBreakInstance(locale,
                                LINE_INDEX,
                                "LineData",
                                "LineDictionary");
    }

    /**
     * Returns a new <code>BreakIterator</code> instance
     * for <a href="../BreakIterator.html#character">character breaks</a>
     * for the given locale.
     * @param locale the desired locale
     * @return A break iterator for character breaks
     * @exception NullPointerException if <code>locale</code> is null
     * @exception IllegalArgumentException if <code>locale</code> isn't
     *     one of the locales returned from
     *     {@link java.util.spi.LocaleServiceProvider#getAvailableLocales()
     *     getAvailableLocales()}.
     * @see java.text.BreakIterator#getCharacterInstance(java.util.Locale)
     */
    @Override
    public BreakIterator getCharacterInstance(Locale locale) {
        return new GraphemeBreakIterator();
    }

    /**
     * Returns a new <code>BreakIterator</code> instance
     * for <a href="../BreakIterator.html#sentence">sentence breaks</a>
     * for the given locale.
     * @param locale the desired locale
     * @return A break iterator for sentence breaks
     * @exception NullPointerException if <code>locale</code> is null
     * @exception IllegalArgumentException if <code>locale</code> isn't
     *     one of the locales returned from
     *     {@link java.util.spi.LocaleServiceProvider#getAvailableLocales()
     *     getAvailableLocales()}.
     * @see java.text.BreakIterator#getSentenceInstance(java.util.Locale)
     */
    @Override
    public BreakIterator getSentenceInstance(Locale locale) {
        return getBreakInstance(locale,
                                SENTENCE_INDEX,
                                "SentenceData",
                                "SentenceDictionary");
    }

    private BreakIterator getBreakInstance(Locale locale,
                                           int type,
                                           String ruleName,
                                           String dictionaryName) {
        Objects.requireNonNull(locale);

        LocaleResources lr = LocaleProviderAdapter.forJRE().getLocaleResources(locale);
        String[] classNames = (String[]) lr.getBreakIteratorInfo("BreakIteratorClasses");
        String ruleFile = (String) lr.getBreakIteratorInfo(ruleName);
        byte[] ruleData = lr.getBreakIteratorResources(ruleName);

        try {
            switch (classNames[type]) {
            case "RuleBasedBreakIterator":
                return new RuleBasedBreakIterator(ruleFile, ruleData);

            case "DictionaryBasedBreakIterator":
                String dictionaryFile = (String) lr.getBreakIteratorInfo(dictionaryName);
                byte[] dictionaryData = lr.getBreakIteratorResources(dictionaryName);
                return new DictionaryBasedBreakIterator(ruleFile, ruleData,
                                                        dictionaryFile, dictionaryData);
            default:
                throw new IllegalArgumentException("Invalid break iterator class \"" +
                                classNames[type] + "\"");
            }
        } catch (MissingResourceException | IllegalArgumentException e) {
            throw new InternalError(e.toString(), e);
        }
    }

    @Override
    public Set<String> getAvailableLanguageTags() {
        return langtags;
    }

    @Override
    public boolean isSupportedLocale(Locale locale) {
        return LocaleProviderAdapter.forType(type).isSupportedProviderLocale(locale, langtags);
    }

    static final class GraphemeBreakIterator extends BreakIterator {
        CharacterIterator ci;
        int offset;
        List<Integer> boundaries;
        int boundaryIndex;

        GraphemeBreakIterator() {
            setText("");
        }

        @Override
        public int first() {
            boundaryIndex = 0;
            return current();
        }

        @Override
        public int last() {
            boundaryIndex = boundaries.size() - 1;
            return current();
        }

        @Override
        public int next(int n) {
            if (n == 0) {
                return offset;
            }

            boundaryIndex = boundaryIndex + n;
            if (boundaryIndex < 0) {
                boundaryIndex = 0;
                current();
                return DONE;
            } else if (boundaryIndex >= boundaries.size()) {
                boundaryIndex = boundaries.size() - 1;
                current();
                return DONE;
            } else {
                return current();
            }
        }

        @Override
        public int next() {
            return next(1);
        }

        @Override
        public int previous() {
            return next(-1);
        }

        @Override
        public int following(int offset) {
            var lastBoundary = boundaries.get(boundaries.size() - 1);

            if (offset < boundaries.get(0) || offset > lastBoundary) {
                throw new IllegalArgumentException("offset is out of bounds: " + offset);
            } else if (offset == this.offset && this.offset == lastBoundary) {
                return DONE;
            }

            boundaryIndex = Collections.binarySearch(boundaries, Math.min(offset + 1, lastBoundary));
            if (boundaryIndex < 0) {
                boundaryIndex = -boundaryIndex - 1;
            }

            return current();
        }

        @Override
        public int current() {
            offset = boundaries.get(boundaryIndex);
            return offset;
        }

        @Override
        public CharacterIterator getText() {
            return ci;
        }

        @Override
        public void setText(CharacterIterator newText) {
            ci = newText;
            var text = new CharacterIteratorCharSequence(ci);
            var end = ci.getEndIndex();
            boundaries = new ArrayList<>();

            for (int b = ci.getBeginIndex(); b < end;) {
                boundaries.add(b);
                b = Grapheme.nextBoundary(text, b, end);
            }
            boundaries.add(end);
            boundaryIndex = 0;
            offset = ci.getIndex();
        }

        // Had to override to suppress the bug in the BreakIterator's default impl.
        // See the comments in the default impl.
        @Override
        public boolean isBoundary(int offset) {
            if (offset < boundaries.get(0) || offset > boundaries.get(boundaries.size() - 1)) {
                throw new IllegalArgumentException("offset is out of bounds: " + offset);
            }
            return Collections.binarySearch(boundaries, offset) >= 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(ci, offset, boundaries, boundaryIndex);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof GraphemeBreakIterator that &&
                    ci.equals(that.ci) &&
                    offset == that.offset &&
                    boundaries.equals(that.boundaries) &&
                    boundaryIndex == that.boundaryIndex;
        }
    }

    /**
     * Implementation only for calling Grapheme.nextBoundary().
     *
     * This is a special-purpose CharSequence that represents characters in the
     * index range [0..endIndex) of the underlying CharacterIterator, even if
     * that CharacterIterator represents the subrange of some string. The calling
     * code in GraphemeBreakIterator takes care to ensure that only valid indexes
     * into the src are used.
     */
    static final class CharacterIteratorCharSequence implements CharSequence {
        CharacterIterator src;
        CharacterIteratorCharSequence(CharacterIterator ci) {
            src = ci;
        }

        @Override
        public int length() {
            // Return the entire CharSequence length (0 to endIndex), not to
            // be confused with the text range length (beginIndex to endIndex)
            // of the underlying CharacterIterator.
            return src.getEndIndex();
        }

        @Override
        public char charAt(int index) {
            src.setIndex(index);
            return src.current();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            // not expected to be called
            throw new UnsupportedOperationException();
        }
    }
}
