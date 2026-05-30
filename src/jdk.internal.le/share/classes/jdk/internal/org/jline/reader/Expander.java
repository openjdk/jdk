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
 * The Expander interface provides functionality for expanding special syntax in command lines.
 * <p>
 * Expanders are responsible for processing and expanding various types of expressions
 * in the input line before it is executed. This includes:
 * <ul>
 *   <li>History expansions (e.g., !! to repeat the last command)</li>
 *   <li>Variable expansions (e.g., $HOME or ${HOME})</li>
 *   <li>Other shell-like expansions</li>
 * </ul>
 * <p>
 * The expander is called by the LineReader after the user has accepted a line but before
 * it is executed or added to the history. This allows the user to see the unexpanded form
 * while editing, but ensures that the expanded form is what gets executed and stored in history.
 * <p>
 * The default implementation is {@link org.jline.reader.impl.DefaultExpander}.
 *
 * @see org.jline.reader.impl.DefaultExpander
 * @see LineReader#getExpander()
 * @see LineReaderBuilder#expander(Expander)
 */
public interface Expander {

    /**
     * Expands history references in the input line.
     * <p>
     * This method processes history designators such as !!, !$, !n, etc., replacing
     * them with the corresponding entries from the command history.
     *
     * @param history the command history to use for expansion
     * @param line the input line containing history references
     * @return the line with history references expanded
     */
    String expandHistory(History history, String line);

    /**
     * Expands variables in the input word.
     * <p>
     * This method processes variable references such as $VAR or ${VAR}, replacing
     * them with their values. The specific syntax and behavior depends on the
     * implementation.
     *
     * @param word the word containing variable references
     * @return the word with variables expanded
     */
    String expandVar(String word);
}
