/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.reader;

import java.util.List;

/**
 * <code>ParsedLine</code> objects are returned by the {@link Parser}
 * during completion or when accepting the line.
 * <p>
 * This interface represents a command line that has been tokenized into words
 * according to the syntax rules of the parser. It provides access to the individual
 * words, the current word being completed, cursor positions, and the original
 * unparsed line.
 * <p>
 * ParsedLine objects are used extensively during tab completion to determine
 * what the user is trying to complete and to provide the appropriate context
 * to {@link Completer} implementations.
 * <p>
 * The instances should implement the {@link CompletingParsedLine}
 * interface so that escape chars and quotes can be correctly handled during
 * completion.
 *
 * @see Parser
 * @see CompletingParsedLine
 * @see Completer
 */
public interface ParsedLine {

    /**
     * The current word being completed.
     * If the cursor is after the last word, an empty string is returned.
     *
     * @return the word being completed or an empty string
     */
    String word();

    /**
     * The cursor position within the current word.
     *
     * @return the cursor position within the current word
     */
    int wordCursor();

    /**
     * The index of the current word in the list of words.
     *
     * @return the index of the current word in the list of words
     */
    int wordIndex();

    /**
     * The list of words.
     *
     * @return the list of words
     */
    List<String> words();

    /**
     * The unparsed line.
     *
     * @return the unparsed line
     */
    String line();

    /**
     * The cursor position within the line.
     *
     * @return the cursor position within the line
     */
    int cursor();
}
