/*
 * Copyright (c) 2002-2018, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.org.jline.reader.impl;

import java.util.*;
import java.util.function.Predicate;

import jdk.internal.org.jline.reader.CompletingParsedLine;
import jdk.internal.org.jline.reader.EOFError;
import jdk.internal.org.jline.reader.ParsedLine;
import jdk.internal.org.jline.reader.Parser;

public class DefaultParser implements Parser {

    private char[] quoteChars = {'\'', '"'};

    private char[] escapeChars = {'\\'};

    private boolean eofOnUnclosedQuote;

    private boolean eofOnEscapedNewLine;

    //
    // Chainable setters
    //

    public DefaultParser quoteChars(final char[] chars) {
        this.quoteChars = chars;
        return this;
    }

    public DefaultParser escapeChars(final char[] chars) {
        this.escapeChars = chars;
        return this;
    }

    public DefaultParser eofOnUnclosedQuote(boolean eofOnUnclosedQuote) {
        this.eofOnUnclosedQuote = eofOnUnclosedQuote;
        return this;
    }

    public DefaultParser eofOnEscapedNewLine(boolean eofOnEscapedNewLine) {
        this.eofOnEscapedNewLine = eofOnEscapedNewLine;
        return this;
    }

    //
    // Java bean getters and setters
    //

    public void setQuoteChars(final char[] chars) {
        this.quoteChars = chars;
    }

    public char[] getQuoteChars() {
        return this.quoteChars;
    }

    public void setEscapeChars(final char[] chars) {
        this.escapeChars = chars;
    }

    public char[] getEscapeChars() {
        return this.escapeChars;
    }

    public void setEofOnUnclosedQuote(boolean eofOnUnclosedQuote) {
        this.eofOnUnclosedQuote = eofOnUnclosedQuote;
    }

    public boolean isEofOnUnclosedQuote() {
        return eofOnUnclosedQuote;
    }

    public void setEofOnEscapedNewLine(boolean eofOnEscapedNewLine) {
        this.eofOnEscapedNewLine = eofOnEscapedNewLine;
    }

    public boolean isEofOnEscapedNewLine() {
        return eofOnEscapedNewLine;
    }

    public ParsedLine parse(final String line, final int cursor, ParseContext context) {
        List<String> words = new LinkedList<>();
        StringBuilder current = new StringBuilder();
        int wordCursor = -1;
        int wordIndex = -1;
        int quoteStart = -1;
        int rawWordCursor = -1;
        int rawWordLength = -1;
        int rawWordStart = 0;

        for (int i = 0; (line != null) && (i < line.length()); i++) {
            // once we reach the cursor, set the
            // position of the selected index
            if (i == cursor) {
                wordIndex = words.size();
                // the position in the current argument is just the
                // length of the current argument
                wordCursor = current.length();
                rawWordCursor = i - rawWordStart;
            }

            if (quoteStart < 0 && isQuoteChar(line, i)) {
                // Start a quote block
                quoteStart = i;
            } else if (quoteStart >= 0) {
                // In a quote block
                if (line.charAt(quoteStart) == line.charAt(i) && !isEscaped(line, i)) {
                    // End the block; arg could be empty, but that's fine
                    words.add(current.toString());
                    current.setLength(0);
                    quoteStart = -1;
                    if (rawWordCursor >= 0 && rawWordLength < 0) {
                        rawWordLength = i - rawWordStart + 1;
                    }
                } else {
                    if (!isEscapeChar(line, i)) {
                        // Take the next character
                        current.append(line.charAt(i));
                    }
                }
            } else {
                // Not in a quote block
                if (isDelimiter(line, i)) {
                    if (current.length() > 0) {
                        words.add(current.toString());
                        current.setLength(0); // reset the arg
                        if (rawWordCursor >= 0 && rawWordLength < 0) {
                            rawWordLength = i - rawWordStart;
                        }
                    }
                    rawWordStart = i + 1;
                } else {
                    if (!isEscapeChar(line, i)) {
                        current.append(line.charAt(i));
                    }
                }
            }
        }

        if (current.length() > 0 || cursor == line.length()) {
            words.add(current.toString());
            if (rawWordCursor >= 0 && rawWordLength < 0) {
                rawWordLength = line.length() - rawWordStart;
            }
        }

        if (cursor == line.length()) {
            wordIndex = words.size() - 1;
            wordCursor = words.get(words.size() - 1).length();
            rawWordCursor = cursor - rawWordStart;
            rawWordLength = rawWordCursor;
        }

        if (eofOnEscapedNewLine && isEscapeChar(line, line.length() - 1)) {
            throw new EOFError(-1, -1, "Escaped new line", "newline");
        }
        if (eofOnUnclosedQuote && quoteStart >= 0 && context != ParseContext.COMPLETE) {
            throw new EOFError(-1, -1, "Missing closing quote", line.charAt(quoteStart) == '\''
                    ? "quote" : "dquote");
        }

        String openingQuote = quoteStart >= 0 ? line.substring(quoteStart, quoteStart + 1) : null;
        return new ArgumentList(line, words, wordIndex, wordCursor, cursor, openingQuote, rawWordCursor, rawWordLength);
    }

    /**
     * Returns true if the specified character is a whitespace parameter. Check to ensure that the character is not
     * escaped by any of {@link #getQuoteChars}, and is not escaped by ant of the {@link #getEscapeChars}, and
     * returns true from {@link #isDelimiterChar}.
     *
     * @param buffer    The complete command buffer
     * @param pos       The index of the character in the buffer
     * @return          True if the character should be a delimiter
     */
    public boolean isDelimiter(final CharSequence buffer, final int pos) {
        return !isQuoted(buffer, pos) && !isEscaped(buffer, pos) && isDelimiterChar(buffer, pos);
    }

    public boolean isQuoted(final CharSequence buffer, final int pos) {
        return false;
    }

    public boolean isQuoteChar(final CharSequence buffer, final int pos) {
        if (pos < 0) {
            return false;
        }
        if (quoteChars != null) {
            for (char e : quoteChars) {
                if (e == buffer.charAt(pos)) {
                    return !isEscaped(buffer, pos);
                }
            }
        }
        return false;
    }

    /**
     * Check if this character is a valid escape char (i.e. one that has not been escaped)
     *
     * @param buffer
     *          the buffer to check in
     * @param pos
     *          the position of the character to check
     * @return true if the character at the specified position in the given buffer is an escape
     *         character and the character immediately preceding it is not an escape character.
     */
    public boolean isEscapeChar(final CharSequence buffer, final int pos) {
        if (pos < 0) {
            return false;
        }
        if (escapeChars != null) {
            for (char e : escapeChars) {
                if (e == buffer.charAt(pos)) {
                    return !isEscaped(buffer, pos);
                }
            }
        }
        return false;
    }

    /**
     * Check if a character is escaped (i.e. if the previous character is an escape)
     *
     * @param buffer
     *          the buffer to check in
     * @param pos
     *          the position of the character to check
     * @return true if the character at the specified position in the given buffer is an escape
     *         character and the character immediately preceding it is an escape character.
     */
    public boolean isEscaped(final CharSequence buffer, final int pos) {
        if (pos <= 0) {
            return false;
        }
        return isEscapeChar(buffer, pos - 1);
    }

    /**
     * Returns true if the character at the specified position if a delimiter. This method will only be called if
     * the character is not enclosed in any of the {@link #getQuoteChars}, and is not escaped by ant of the
     * {@link #getEscapeChars}. To perform escaping manually, override {@link #isDelimiter} instead.
     *
     * @param buffer
     *          the buffer to check in
     * @param pos
     *          the position of the character to check
     * @return true if the character at the specified position in the given buffer is a delimiter.
     */
    public boolean isDelimiterChar(CharSequence buffer, int pos) {
        return Character.isWhitespace(buffer.charAt(pos));
    }

    private boolean isRawEscapeChar(char key) {
        if (escapeChars != null) {
            for (char e : escapeChars) {
                if (e == key) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isRawQuoteChar(char key) {
        if (quoteChars != null) {
            for (char e : quoteChars) {
                if (e == key) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The result of a delimited buffer.
     *
     * @author <a href="mailto:mwp1@cornell.edu">Marc Prud'hommeaux</a>
     */
    public class ArgumentList implements ParsedLine, CompletingParsedLine
    {
        private final String line;

        private final List<String> words;

        private final int wordIndex;

        private final int wordCursor;

        private final int cursor;

        private final String openingQuote;

        private final int rawWordCursor;

        private final int rawWordLength;

        @Deprecated
        public ArgumentList(final String line, final List<String> words,
                            final int wordIndex, final int wordCursor,
                            final int cursor) {
            this(line, words, wordIndex, wordCursor, cursor,
                    null, wordCursor, words.get(wordIndex).length());
        }

        /**
         *
         * @param line the command line being edited
         * @param words the list of words
         * @param wordIndex the index of the current word in the list of words
         * @param wordCursor the cursor position within the current word
         * @param cursor the cursor position within the line
         * @param openingQuote the opening quote (usually '\"' or '\'') or null
         * @param rawWordCursor the cursor position inside the raw word (i.e. including quotes and escape characters)
         * @param rawWordLength the raw word length, including quotes and escape characters
         */
        public ArgumentList(final String line, final List<String> words,
                            final int wordIndex, final int wordCursor,
                            final int cursor, final String openingQuote,
                            final int rawWordCursor, final int rawWordLength) {
            this.line = line;
            this.words = Collections.unmodifiableList(Objects.requireNonNull(words));
            this.wordIndex = wordIndex;
            this.wordCursor = wordCursor;
            this.cursor = cursor;
            this.openingQuote = openingQuote;
            this.rawWordCursor = rawWordCursor;
            this.rawWordLength = rawWordLength;
        }

        public int wordIndex() {
            return this.wordIndex;
        }

        public String word() {
            // TODO: word() should always be contained in words()
            if ((wordIndex < 0) || (wordIndex >= words.size())) {
                return "";
            }
            return words.get(wordIndex);
        }

        public int wordCursor() {
            return this.wordCursor;
        }

        public List<String> words() {
            return this.words;
        }

        public int cursor() {
            return this.cursor;
        }

        public String line() {
            return line;
        }

        public CharSequence escape(CharSequence candidate, boolean complete) {
            StringBuilder sb = new StringBuilder(candidate);
            Predicate<Integer> needToBeEscaped;
            // Completion is protected by an opening quote:
            // Delimiters (spaces) don't need to be escaped, nor do other quotes, but everything else does.
            // Also, close the quote at the end
            if (openingQuote != null) {
                needToBeEscaped = i -> isRawEscapeChar(sb.charAt(i)) || String.valueOf(sb.charAt(i)).equals(openingQuote);
            }
            // No quote protection, need to escape everything: delimiter chars (spaces), quote chars
            // and escapes themselves
            else {
                needToBeEscaped = i -> isDelimiterChar(sb, i) || isRawEscapeChar(sb.charAt(i)) || isRawQuoteChar(sb.charAt(i));
            }
            for (int i = 0; i < sb.length(); i++) {
                if (needToBeEscaped.test(i)) {
                    sb.insert(i++, escapeChars[0]);
                }
            }
            if (openingQuote != null) {
                sb.insert(0, openingQuote);
                if (complete) {
                    sb.append(openingQuote);
                }
            }
            return sb;
        }

        @Override
        public int rawWordCursor() {
            return rawWordCursor;
        }

        @Override
        public int rawWordLength() {
            return rawWordLength;
        }
    }

}
