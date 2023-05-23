// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
******************************************************************************
* Copyright (C) 2003-2011, International Business Machines Corporation and   *
* others. All Rights Reserved.                                               *
******************************************************************************
*/

package jdk.internal.icu.impl;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import jdk.internal.icu.impl.locale.AsciiUtil;

/**
 * Utility class to parse and normalize locale ids (including POSIX style)
 */
public final class LocaleIDParser {

    /**
     * Char array representing the locale ID.
     */
    private char[] id;

    /**
     * Current position in {@link #id} (while parsing).
     */
    private int index;

    /**
     * Temporary buffer for parsed sections of data.
     */
    private StringBuilder buffer;

    // um, don't handle POSIX ids unless we request it.  why not?  well... because.
    private boolean canonicalize;
    private boolean hadCountry;

    // used when canonicalizing
    Map<String, String> keywords;
    String baseName;

    /**
     * Parsing constants.
     */
    private static final char KEYWORD_SEPARATOR     = '@';
    private static final char HYPHEN                = '-';
    private static final char KEYWORD_ASSIGN        = '=';
    private static final char COMMA                 = ',';
    private static final char ITEM_SEPARATOR        = ';';
    private static final char DOT                   = '.';
    private static final char UNDERSCORE            = '_';

    public LocaleIDParser(String localeID) {
        this(localeID, false);
    }

    public LocaleIDParser(String localeID, boolean canonicalize) {
        id = localeID.toCharArray();
        index = 0;
        buffer = new StringBuilder(id.length + 5);
        this.canonicalize = canonicalize;
    }

    private void reset() {
        index = 0;
        buffer = new StringBuilder(id.length + 5);
    }

    // utilities for working on text in the buffer

    /**
     * Append c to the buffer.
     */
    private void append(char c) {
        buffer.append(c);
    }

    private void addSeparator() {
        append(UNDERSCORE);
    }

    /**
     * Returns the text in the buffer from start to blen as a String.
     */
    private String getString(int start) {
        return buffer.substring(start);
    }

    /**
     * Set the length of the buffer to pos, then append the string.
     */
    private void set(int pos, String s) {
        buffer.delete(pos, buffer.length());
        buffer.insert(pos, s);
    }

    /**
     * Append the string to the buffer.
     */
    private void append(String s) {
        buffer.append(s);
    }

    // utilities for parsing text out of the id

    /**
     * Character to indicate no more text is available in the id.
     */
    private static final char DONE = '\uffff';

    /**
     * Returns the character at index in the id, and advance index.  The returned character
     * is DONE if index was at the limit of the buffer.  The index is advanced regardless
     * so that decrementing the index will always 'unget' the last character returned.
     */
    private char next() {
        if (index == id.length) {
            index++;
            return DONE;
        }

        return id[index++];
    }

    /**
     * Advance index until the next terminator or id separator, and leave it there.
     */
    private void skipUntilTerminatorOrIDSeparator() {
        while (!isTerminatorOrIDSeparator(next()));
        --index;
    }

    /**
     * Returns true if the character at index in the id is a terminator.
     */
    private boolean atTerminator() {
        return index >= id.length || isTerminator(id[index]);
    }

    /**
     * Returns true if the character is a terminator (keyword separator, dot, or DONE).
     * Dot is a terminator because of the POSIX form, where dot precedes the codepage.
     */
    private boolean isTerminator(char c) {
        // always terminate at DOT, even if not handling POSIX.  It's an error...
        return c == KEYWORD_SEPARATOR || c == DONE || c == DOT;
    }

    /**
     * Returns true if the character is a terminator or id separator.
     */
    private boolean isTerminatorOrIDSeparator(char c) {
        return c == UNDERSCORE || c == HYPHEN || isTerminator(c);
    }

    /**
     * Returns true if the start of the buffer has an experimental or private language
     * prefix, the pattern '[ixIX][-_].' shows the syntax checked.
     */
    private boolean haveExperimentalLanguagePrefix() {
        if (id.length > 2) {
            char c = id[1];
            if (c == HYPHEN || c == UNDERSCORE) {
                c = id[0];
                return c == 'x' || c == 'X' || c == 'i' || c == 'I';
            }
        }
        return false;
    }

    /**
     * Returns true if a value separator occurs at or after index.
     */
    private boolean haveKeywordAssign() {
        // assume it is safe to start from index
        for (int i = index; i < id.length; ++i) {
            if (id[i] == KEYWORD_ASSIGN) {
                return true;
            }
        }
        return false;
    }

    /**
     * Advance index past language, and accumulate normalized language code in buffer.
     * Index must be at 0 when this is called.  Index is left at a terminator or id
     * separator.  Returns the start of the language code in the buffer.
     */
    private int parseLanguage() {
        int startLength = buffer.length();

        if (haveExperimentalLanguagePrefix()) {
            append(AsciiUtil.toLower(id[0]));
            append(HYPHEN);
            index = 2;
        }

        char c;
        while(!isTerminatorOrIDSeparator(c = next())) {
            append(AsciiUtil.toLower(c));
        }
        --index; // unget

        if (buffer.length() - startLength == 3) {
            String lang = LocaleIDs.threeToTwoLetterLanguage(getString(0));
            if (lang != null) {
                set(0, lang);
            }
        }

        return 0;
    }

    /**
     * Advance index past language.  Index must be at 0 when this is called.  Index
     * is left at a terminator or id separator.
     */
    private void skipLanguage() {
        if (haveExperimentalLanguagePrefix()) {
            index = 2;
        }
        skipUntilTerminatorOrIDSeparator();
    }

    /**
     * Advance index past script, and accumulate normalized script in buffer.
     * Index must be immediately after the language.
     * If the item at this position is not a script (is not four characters
     * long) leave index and buffer unchanged.  Otherwise index is left at
     * a terminator or id separator.  Returns the start of the script code
     * in the buffer (this may be equal to the buffer length, if there is no
     * script).
     */
    private int parseScript() {
        if (!atTerminator()) {
            int oldIndex = index; // save original index
            ++index;

            int oldBlen = buffer.length(); // get before append hyphen, if we truncate everything is undone
            char c;
            boolean firstPass = true;
            while(!isTerminatorOrIDSeparator(c = next()) && AsciiUtil.isAlpha(c)) {
                if (firstPass) {
                    addSeparator();
                    append(AsciiUtil.toUpper(c));
                    firstPass = false;
                } else {
                    append(AsciiUtil.toLower(c));
                }
            }
            --index; // unget

            /* If it's not exactly 4 characters long, then it's not a script. */
            if (index - oldIndex != 5) { // +1 to account for separator
                index = oldIndex;
                buffer.delete(oldBlen, buffer.length());
            } else {
                oldBlen++; // index past hyphen, for clients who want to extract just the script
            }

            return oldBlen;
        }
        return buffer.length();
    }

    /**
     * Advance index past script.
     * Index must be immediately after the language and IDSeparator.
     * If the item at this position is not a script (is not four characters
     * long) leave index.  Otherwise index is left at a terminator or
     * id separator.
     */
    private void skipScript() {
        if (!atTerminator()) {
            int oldIndex = index;
            ++index;

            char c;
            while (!isTerminatorOrIDSeparator(c = next()) && AsciiUtil.isAlpha(c));
            --index;

            if (index - oldIndex != 5) { // +1 to account for separator
                index = oldIndex;
            }
        }
    }

    /**
     * Advance index past country, and accumulate normalized country in buffer.
     * Index must be immediately after the script (if there is one, else language)
     * and IDSeparator.  Return the start of the country code in the buffer.
     */
    private int parseCountry() {
        if (!atTerminator()) {
            int oldIndex = index;
            ++index;

            int oldBlen = buffer.length();
            char c;
            boolean firstPass = true;
            while (!isTerminatorOrIDSeparator(c = next())) {
                if (firstPass) { // first, add hyphen
                    hadCountry = true; // we have a country, let variant parsing know
                    addSeparator();
                    ++oldBlen; // increment past hyphen
                    firstPass = false;
                }
                append(AsciiUtil.toUpper(c));
            }
            --index; // unget

            int charsAppended = buffer.length() - oldBlen;

            if (charsAppended == 0) {
                // Do nothing.
            }
            else if (charsAppended < 2 || charsAppended > 3) {
                // It's not a country, so return index and blen to
                // their previous values.
                index = oldIndex;
                --oldBlen;
                buffer.delete(oldBlen, buffer.length());
                hadCountry = false;
            }
            else if (charsAppended == 3) {
                String region = LocaleIDs.threeToTwoLetterRegion(getString(oldBlen));
                if (region != null) {
                    set(oldBlen, region);
                }
            }

            return oldBlen;
        }

        return buffer.length();
    }

    /**
     * Advance index past country.
     * Index must be immediately after the script (if there is one, else language)
     * and IDSeparator.
     */
    private void skipCountry() {
        if (!atTerminator()) {
            if (id[index] == UNDERSCORE || id[index] == HYPHEN) {
                ++index;
            }
            /*
             * Save the index point after the separator, since the format
             * requires two separators if the country is not present.
             */
            int oldIndex = index;

            skipUntilTerminatorOrIDSeparator();
            int charsSkipped = index - oldIndex;
            if (charsSkipped < 2 || charsSkipped > 3) {
                index = oldIndex;
            }
        }
    }

    /**
     * Advance index past variant, and accumulate normalized variant in buffer.  This ignores
     * the codepage information from POSIX ids.  Index must be immediately after the country
     * or script.  Index is left at the keyword separator or at the end of the text.  Return
     * the start of the variant code in the buffer.
     *
     * In standard form, we can have the following forms:
     * ll__VVVV
     * ll_CC_VVVV
     * ll_Ssss_VVVV
     * ll_Ssss_CC_VVVV
     *
     * This also handles POSIX ids, which can have the following forms (pppp is code page id):
     * ll_CC.pppp          --> ll_CC
     * ll_CC.pppp@VVVV     --> ll_CC_VVVV
     * ll_CC@VVVV          --> ll_CC_VVVV
     *
     * We identify this use of '@' in POSIX ids by looking for an '=' following
     * the '@'.  If there is one, we consider '@' to start a keyword list, instead of
     * being part of a POSIX id.
     *
     * Note:  since it was decided that we want an option to not handle POSIX ids, this
     * becomes a bit more complex.
     */
    private int parseVariant() {
        int oldBlen = buffer.length();

        boolean start = true;
        boolean needSeparator = true;
        boolean skipping = false;
        char c;
        boolean firstPass = true;

        while ((c = next()) != DONE) {
            if (c == DOT) {
                start = false;
                skipping = true;
            } else if (c == KEYWORD_SEPARATOR) {
                if (haveKeywordAssign()) {
                    break;
                }
                skipping = false;
                start = false;
                needSeparator = true; // add another underscore if we have more text
            } else if (start) {
                start = false;
                if (c != UNDERSCORE && c != HYPHEN) {
                    index--;
                }
            } else if (!skipping) {
                if (needSeparator) {
                    needSeparator = false;
                    if (firstPass && !hadCountry) { // no country, we'll need two
                        addSeparator();
                        ++oldBlen; // for sure
                    }
                    addSeparator();
                    if (firstPass) { // only for the first separator
                        ++oldBlen;
                        firstPass = false;
                    }
                }
                c = AsciiUtil.toUpper(c);
                if (c == HYPHEN || c == COMMA) {
                    c = UNDERSCORE;
                }
                append(c);
            }
        }
        --index; // unget

        return oldBlen;
    }

    // no need for skipvariant, to get the keywords we'll just scan directly for
    // the keyword separator

    /**
     * Returns the normalized language id, or the empty string.
     */
    public String getLanguage() {
        reset();
        return getString(parseLanguage());
    }

    /**
     * Returns the normalized script id, or the empty string.
     */
    public String getScript() {
        reset();
        skipLanguage();
        return getString(parseScript());
    }

    /**
     * return the normalized country id, or the empty string.
     */
    public String getCountry() {
        reset();
        skipLanguage();
        skipScript();
        return getString(parseCountry());
    }

    /**
     * Returns the normalized variant id, or the empty string.
     */
    public String getVariant() {
        reset();
        skipLanguage();
        skipScript();
        skipCountry();
        return getString(parseVariant());
    }

    /**
     * Returns the language, script, country, and variant as separate strings.
     */
    public String[] getLanguageScriptCountryVariant() {
        reset();
        return new String[] {
                getString(parseLanguage()),
                getString(parseScript()),
                getString(parseCountry()),
                getString(parseVariant())
        };
    }

    public void setBaseName(String baseName) {
        this.baseName = baseName;
    }

    public void parseBaseName() {
        if (baseName != null) {
            set(0, baseName);
        } else {
            reset();
            parseLanguage();
            parseScript();
            parseCountry();
            parseVariant();

            // catch unwanted trailing underscore after country if there was no variant
            int len = buffer.length();
            if (len > 0 && buffer.charAt(len - 1) == UNDERSCORE) {
                buffer.deleteCharAt(len - 1);
            }
        }
    }

    /**
     * Returns the normalized base form of the locale id.  The base
     * form does not include keywords.
     */
    public String getBaseName() {
        if (baseName != null) {
            return baseName;
        }
        parseBaseName();
        return getString(0);
    }

    /**
     * Returns the normalized full form of the locale id.  The full
     * form includes keywords if they are present.
     */
    public String getName() {
        parseBaseName();
        parseKeywords();
        return getString(0);
    }

    // keyword utilities

    /**
     * If we have keywords, advance index to the start of the keywords and return true,
     * otherwise return false.
     */
    private boolean setToKeywordStart() {
        for (int i = index; i < id.length; ++i) {
            if (id[i] == KEYWORD_SEPARATOR) {
                if (canonicalize) {
                    for (int j = ++i; j < id.length; ++j) { // increment i past separator for return
                        if (id[j] == KEYWORD_ASSIGN) {
                            index = i;
                            return true;
                        }
                    }
                } else {
                    if (++i < id.length) {
                        index = i;
                        return true;
                    }
                }
                break;
            }
        }
        return false;
    }

    private static boolean isDoneOrKeywordAssign(char c) {
        return c == DONE || c == KEYWORD_ASSIGN;
    }

    private static boolean isDoneOrItemSeparator(char c) {
        return c == DONE || c == ITEM_SEPARATOR;
    }

    private String getKeyword() {
        int start = index;
        while (!isDoneOrKeywordAssign(next())) {
        }
        --index;
        return AsciiUtil.toLowerString(new String(id, start, index-start).trim());
    }

    private String getValue() {
        int start = index;
        while (!isDoneOrItemSeparator(next())) {
        }
        --index;
        return new String(id, start, index-start).trim(); // leave case alone
    }

    private Comparator<String> getKeyComparator() {
        final Comparator<String> comp = new Comparator<String>() {
            @Override
            public int compare(String lhs, String rhs) {
                return lhs.compareTo(rhs);
            }
        };
        return comp;
    }

    /**
     * Returns a map of the keywords and values, or null if there are none.
     */
    public Map<String, String> getKeywordMap() {
        if (keywords == null) {
            TreeMap<String, String> m = null;
            if (setToKeywordStart()) {
                // trim spaces and convert to lower case, both keywords and values.
                do {
                    String key = getKeyword();
                    if (key.length() == 0) {
                        break;
                    }
                    char c = next();
                    if (c != KEYWORD_ASSIGN) {
                        // throw new IllegalArgumentException("key '" + key + "' missing a value.");
                        if (c == DONE) {
                            break;
                        } else {
                            continue;
                        }
                    }
                    String value = getValue();
                    if (value.length() == 0) {
                        // throw new IllegalArgumentException("key '" + key + "' missing a value.");
                        continue;
                    }
                    if (m == null) {
                        m = new TreeMap<String, String>(getKeyComparator());
                    } else if (m.containsKey(key)) {
                        // throw new IllegalArgumentException("key '" + key + "' already has a value.");
                        continue;
                    }
                    m.put(key, value);
                } while (next() == ITEM_SEPARATOR);
            }
            keywords = m != null ? m : Collections.<String, String>emptyMap();
        }

        return keywords;
    }


    /**
     * Parse the keywords and return start of the string in the buffer.
     */
    private int parseKeywords() {
        int oldBlen = buffer.length();
        Map<String, String> m = getKeywordMap();
        if (!m.isEmpty()) {
            boolean first = true;
            for (Map.Entry<String, String> e : m.entrySet()) {
                append(first ? KEYWORD_SEPARATOR : ITEM_SEPARATOR);
                first = false;
                append(e.getKey());
                append(KEYWORD_ASSIGN);
                append(e.getValue());
            }
            if (first == false) {
                ++oldBlen;
            }
        }
        return oldBlen;
    }

    /**
     * Returns an iterator over the keywords, or null if we have an empty map.
     */
    public Iterator<String> getKeywords() {
        Map<String, String> m = getKeywordMap();
        return m.isEmpty() ? null : m.keySet().iterator();
    }

    /**
     * Returns the value for the named keyword, or null if the keyword is not
     * present.
     */
    public String getKeywordValue(String keywordName) {
        Map<String, String> m = getKeywordMap();
        return m.isEmpty() ? null : m.get(AsciiUtil.toLowerString(keywordName.trim()));
    }

    /**
     * Set the keyword value only if it is not already set to something else.
     */
    public void defaultKeywordValue(String keywordName, String value) {
        setKeywordValue(keywordName, value, false);
    }

    /**
     * Set the value for the named keyword, or unset it if value is null.  If
     * keywordName itself is null, unset all keywords.  If keywordName is not null,
     * value must not be null.
     */
    public void setKeywordValue(String keywordName, String value) {
        setKeywordValue(keywordName, value, true);
    }

    /**
     * Set the value for the named keyword, or unset it if value is null.  If
     * keywordName itself is null, unset all keywords.  If keywordName is not null,
     * value must not be null.  If reset is true, ignore any previous value for
     * the keyword, otherwise do not change the keyword (including removal of
     * one or all keywords).
     */
    private void setKeywordValue(String keywordName, String value, boolean reset) {
        if (keywordName == null) {
            if (reset) {
                // force new map, ignore value
                keywords = Collections.<String, String>emptyMap();
            }
        } else {
            keywordName = AsciiUtil.toLowerString(keywordName.trim());
            if (keywordName.length() == 0) {
                throw new IllegalArgumentException("keyword must not be empty");
            }
            if (value != null) {
                value = value.trim();
                if (value.length() == 0) {
                    throw new IllegalArgumentException("value must not be empty");
                }
            }
            Map<String, String> m = getKeywordMap();
            if (m.isEmpty()) { // it is EMPTY_MAP
                if (value != null) {
                    // force new map
                    keywords = new TreeMap<String, String>(getKeyComparator());
                    keywords.put(keywordName, value.trim());
                }
            } else {
                if (reset || !m.containsKey(keywordName)) {
                    if (value != null) {
                        m.put(keywordName, value);
                    } else {
                        m.remove(keywordName);
                        if (m.isEmpty()) {
                            // force new map
                            keywords = Collections.<String, String>emptyMap();
                        }
                    }
                }
            }
        }
    }
}
