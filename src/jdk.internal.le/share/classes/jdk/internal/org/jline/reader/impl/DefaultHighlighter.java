/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.reader.impl;

import java.util.regex.Pattern;

import jdk.internal.org.jline.reader.Highlighter;
import jdk.internal.org.jline.reader.LineReader;
import jdk.internal.org.jline.reader.LineReader.RegionType;
import jdk.internal.org.jline.utils.AttributedString;
import jdk.internal.org.jline.utils.AttributedStringBuilder;
import jdk.internal.org.jline.utils.AttributedStyle;
import jdk.internal.org.jline.utils.WCWidth;

/**
 * Default implementation of the {@link Highlighter} interface.
 * <p>
 * This highlighter provides basic syntax highlighting capabilities for the LineReader,
 * including:
 * <ul>
 *   <li>Highlighting of search matches</li>
 *   <li>Highlighting of errors based on patterns or indices</li>
 *   <li>Highlighting of selected regions</li>
 * </ul>
 * <p>
 * The highlighting is applied using {@link AttributedStyle} to change the appearance
 * of text in the terminal, such as colors, bold, underline, etc.
 * <p>
 * Applications can customize the highlighting behavior by extending this class
 * and overriding the {@link #highlight(LineReader, String)} method.
 *
 * @see Highlighter
 * @see AttributedStyle
 * @see org.jline.reader.LineReader
 */
public class DefaultHighlighter implements Highlighter {
    protected Pattern errorPattern;
    protected int errorIndex = -1;

    /**
     * Creates a new DefaultHighlighter.
     */
    public DefaultHighlighter() {
        // Default constructor
    }

    @Override
    public void setErrorPattern(Pattern errorPattern) {
        this.errorPattern = errorPattern;
    }

    @Override
    public void setErrorIndex(int errorIndex) {
        this.errorIndex = errorIndex;
    }

    @Override
    public AttributedString highlight(LineReader reader, String buffer) {
        int underlineStart = -1;
        int underlineEnd = -1;
        int negativeStart = -1;
        int negativeEnd = -1;
        boolean first = true;
        String search = reader.getSearchTerm();
        if (search != null && search.length() > 0) {
            underlineStart = buffer.indexOf(search);
            if (underlineStart >= 0) {
                underlineEnd = underlineStart + search.length() - 1;
            }
        }
        if (reader.getRegionActive() != RegionType.NONE) {
            negativeStart = reader.getRegionMark();
            negativeEnd = reader.getBuffer().cursor();
            if (negativeStart > negativeEnd) {
                int x = negativeEnd;
                negativeEnd = negativeStart;
                negativeStart = x;
            }
            if (reader.getRegionActive() == RegionType.LINE) {
                while (negativeStart > 0 && reader.getBuffer().atChar(negativeStart - 1) != '\n') {
                    negativeStart--;
                }
                while (negativeEnd < reader.getBuffer().length() - 1
                        && reader.getBuffer().atChar(negativeEnd + 1) != '\n') {
                    negativeEnd++;
                }
            }
            // Convert code point indices to char indices
            negativeStart =
                    buffer.offsetByCodePoints(0, Math.min(negativeStart, buffer.codePointCount(0, buffer.length())));
            negativeEnd =
                    buffer.offsetByCodePoints(0, Math.min(negativeEnd, buffer.codePointCount(0, buffer.length())));
        }

        // Convert errorIndex from code point index to char index
        int charErrorIndex = -1;
        if (errorIndex >= 0 && errorIndex < buffer.codePointCount(0, buffer.length())) {
            charErrorIndex = buffer.offsetByCodePoints(0, errorIndex);
        }

        AttributedStringBuilder sb = new AttributedStringBuilder();
        commandStyle(reader, sb, true);
        for (int i = 0; i < buffer.length(); ) {
            if (i == underlineStart) {
                sb.style(AttributedStyle::underline);
            }
            if (i == negativeStart) {
                sb.style(AttributedStyle::inverse);
            }
            if (i == charErrorIndex) {
                sb.style(AttributedStyle::inverse);
            }

            int cp = buffer.codePointAt(i);
            int charCount = Character.charCount(cp);
            if (first && Character.isSpaceChar(cp)) {
                first = false;
                commandStyle(reader, sb, false);
            }
            if (cp == '\t' || cp == '\n') {
                sb.append((char) cp);
            } else if (cp < 32) {
                sb.style(AttributedStyle::inverseNeg)
                        .append('^')
                        .append((char) (cp + '@'))
                        .style(AttributedStyle::inverseNeg);
            } else {
                int w = WCWidth.wcwidth(cp);
                if (w >= 0) {
                    sb.append(buffer, i, i + charCount);
                }
            }
            if (i == underlineEnd) {
                sb.style(AttributedStyle::underlineOff);
            }
            if (i == negativeEnd) {
                sb.style(AttributedStyle::inverseOff);
            }
            if (i == charErrorIndex) {
                sb.style(AttributedStyle::inverseOff);
            }
            i += charCount;
        }
        if (errorPattern != null) {
            sb.styleMatches(errorPattern, AttributedStyle.INVERSE);
        }
        return sb.toAttributedString();
    }

    protected void commandStyle(LineReader reader, AttributedStringBuilder sb, boolean enable) {}
}
