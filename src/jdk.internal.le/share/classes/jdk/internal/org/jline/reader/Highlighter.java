/*
 * Copyright (c) 2002-2021, the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.reader;

import java.util.regex.Pattern;

import jdk.internal.org.jline.utils.AttributedString;

public interface Highlighter {

    /**
     * Highlight buffer
     * @param reader LineReader
     * @param buffer the buffer to be highlighted
     * @return highlighted buffer
     */
    AttributedString highlight(LineReader reader, String buffer);

    /**
     * Refresh highlight configuration
     */
    default void refresh(LineReader reader) {}

    /**
     * Set error pattern to be highlighted
     * @param errorPattern error pattern to be highlighted
     */
    void setErrorPattern(Pattern errorPattern);

    /**
     * Set error index to be highlighted
     * @param errorIndex error index to be highlighted
     */
    void setErrorIndex(int errorIndex);
}
