/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.reader;

import java.util.regex.Pattern;

import jdk.internal.org.jline.utils.AttributedString;

/**
 * The Highlighter interface provides syntax highlighting functionality for the LineReader.
 * <p>
 * Highlighters are responsible for applying visual styling to the command line text as
 * the user types. This can include syntax highlighting for programming languages,
 * highlighting matching brackets, marking errors, or any other visual cues that help
 * users understand the structure and validity of their input.
 * <p>
 * Implementations convert plain text into {@link AttributedString} instances that contain
 * both the text and its visual styling information. The LineReader will then render
 * these styled strings to the terminal with the appropriate colors and text attributes.
 * <p>
 * The default implementation is {@link org.jline.reader.impl.DefaultHighlighter}.
 *
 * @see org.jline.utils.AttributedString
 * @see org.jline.reader.impl.DefaultHighlighter
 * @see LineReaderBuilder#highlighter(Highlighter)
 */
public interface Highlighter {

    /**
     * Highlights the provided text buffer with appropriate styling.
     * <p>
     * This method is called by the LineReader to apply syntax highlighting to the
     * current input line. It should analyze the buffer content and return an
     * AttributedString with appropriate styling applied based on the content's
     * syntax, structure, or other relevant characteristics.
     *
     * @param reader The LineReader instance requesting highlighting
     * @param buffer The text buffer to be highlighted
     * @return An AttributedString containing the highlighted buffer with styling applied
     */
    AttributedString highlight(LineReader reader, String buffer);

    /**
     * Refreshes the highlighter's configuration.
     * <p>
     * This method is called when the highlighter should reload or refresh its
     * configuration, such as when color schemes change or when syntax rules are updated.
     * The default implementation does nothing.
     *
     * @param reader The LineReader instance associated with this highlighter
     */
    default void refresh(LineReader reader) {}

    /**
     * Sets a regular expression pattern that identifies errors to be highlighted.
     * <p>
     * Text matching this pattern will typically be highlighted with error styling
     * (often red or with a distinctive background color) to indicate problematic input.
     *
     * @param errorPattern A regular expression pattern that matches text to be highlighted as errors
     */
    default void setErrorPattern(Pattern errorPattern) {}

    /**
     * Sets a specific character position in the buffer to be highlighted as an error.
     * <p>
     * This is typically used to indicate the exact position of a syntax error or
     * other issue in the input line. The highlighter will apply error styling at
     * this position.
     *
     * @param errorIndex The character index in the buffer to be highlighted as an error
     */
    default void setErrorIndex(int errorIndex) {}
}
