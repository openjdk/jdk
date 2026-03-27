/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.reader;

/**
 * An extension of {@link ParsedLine} that, being aware of the quoting and escaping rules
 * of the {@link org.jline.reader.Parser} that produced it, knows if and how a completion candidate
 * should be escaped/quoted.
 * <p>
 * This interface adds methods to handle the raw (unprocessed) form of words, including
 * any quotes and escape characters that may be present in the original input. It also
 * provides functionality to properly escape completion candidates according to the
 * parser's syntax rules.
 * <p>
 * Implementations of this interface are crucial for proper tab completion in shells
 * that support complex quoting and escaping mechanisms, ensuring that completed text
 * is properly formatted according to the shell's syntax.
 *
 * @see ParsedLine
 * @see Parser
 */
public interface CompletingParsedLine extends ParsedLine {

    /**
     * Escapes a completion candidate according to the parser's quoting and escaping rules.
     * <p>
     * This method ensures that special characters in the candidate are properly escaped
     * or quoted according to the syntax rules of the parser, maintaining consistency with
     * the current input line's quoting style.
     *
     * @param candidate the completion candidate that may need escaping
     * @param complete true if this is a complete word, false if it's a partial completion
     * @return the properly escaped/quoted candidate ready for insertion
     */
    CharSequence escape(CharSequence candidate, boolean complete);

    /**
     * Returns the cursor position within the raw (unprocessed) current word.
     * <p>
     * Unlike {@link ParsedLine#wordCursor()}, this method returns the cursor position
     * in the original word text, including any quotes and escape characters.
     *
     * @return the cursor position within the raw current word
     */
    int rawWordCursor();

    /**
     * Returns the length of the raw (unprocessed) current word.
     * <p>
     * This is the length of the original word text, including any quotes and
     * escape characters that may have been removed during parsing.
     *
     * @return the length of the raw current word
     */
    int rawWordLength();
}
