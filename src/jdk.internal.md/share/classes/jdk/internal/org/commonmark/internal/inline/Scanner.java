/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, a notice that is now available elsewhere in this distribution
 * accompanied the original version of this file, and, per its terms,
 * should not be removed.
 */

package jdk.internal.org.commonmark.internal.inline;

import jdk.internal.org.commonmark.internal.util.CharMatcher;
import jdk.internal.org.commonmark.node.SourceSpan;
import jdk.internal.org.commonmark.parser.SourceLine;
import jdk.internal.org.commonmark.parser.SourceLines;

import java.util.List;

public class Scanner {

    /**
     * Character representing the end of input source (or outside of the text in case of the "previous" methods).
     * <p>
     * Note that we can use NULL to represent this because CommonMark does not allow those in the input (we replace them
     * in the beginning of parsing).
     */
    public static final char END = '\0';

    // Lines without newlines at the end. The scanner will yield `\n` between lines because they're significant for
    // parsing and the final output. There is no `\n` after the last line.
    private final List<SourceLine> lines;
    // Which line we're at.
    private int lineIndex;
    // The index within the line. If index == length(), we pretend that there's a `\n` and only advance after we yield
    // that.
    private int index;

    // Current line or "" if at the end of the lines (using "" instead of null saves a null check)
    private SourceLine line = SourceLine.of("", null);
    private int lineLength = 0;

    Scanner(List<SourceLine> lines, int lineIndex, int index) {
        this.lines = lines;
        this.lineIndex = lineIndex;
        this.index = index;
        if (!lines.isEmpty()) {
            checkPosition(lineIndex, index);
            setLine(lines.get(lineIndex));
        }
    }

    public static Scanner of(SourceLines lines) {
        return new Scanner(lines.getLines(), 0, 0);
    }

    public char peek() {
        if (index < lineLength) {
            return line.getContent().charAt(index);
        } else {
            if (lineIndex < lines.size() - 1) {
                return '\n';
            } else {
                // Don't return newline for end of last line
                return END;
            }
        }
    }

    public int peekCodePoint() {
        if (index < lineLength) {
            char c = line.getContent().charAt(index);
            if (Character.isHighSurrogate(c) && index + 1 < lineLength) {
                char low = line.getContent().charAt(index + 1);
                if (Character.isLowSurrogate(low)) {
                    return Character.toCodePoint(c, low);
                }
            }
            return c;
        } else {
            if (lineIndex < lines.size() - 1) {
                return '\n';
            } else {
                // Don't return newline for end of last line
                return END;
            }
        }
    }

    public int peekPreviousCodePoint() {
        if (index > 0) {
            int prev = index - 1;
            char c = line.getContent().charAt(prev);
            if (Character.isLowSurrogate(c) && prev > 0) {
                char high = line.getContent().charAt(prev - 1);
                if (Character.isHighSurrogate(high)) {
                    return Character.toCodePoint(high, c);
                }
            }
            return c;
        } else {
            if (lineIndex > 0) {
                return '\n';
            } else {
                return END;
            }
        }
    }

    public boolean hasNext() {
        if (index < lineLength) {
            return true;
        } else {
            // No newline at end of last line
            return lineIndex < lines.size() - 1;
        }
    }

    public void next() {
        index++;
        if (index > lineLength) {
            lineIndex++;
            if (lineIndex < lines.size()) {
                setLine(lines.get(lineIndex));
            } else {
                setLine(SourceLine.of("", null));
            }
            index = 0;
        }
    }

    /**
     * Check if the specified char is next and advance the position.
     *
     * @param c the char to check (including newline characters)
     * @return true if matched and position was advanced, false otherwise
     */
    public boolean next(char c) {
        if (peek() == c) {
            next();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Check if we have the specified content on the line and advanced the position. Note that if you want to match
     * newline characters, use {@link #next(char)}.
     *
     * @param content the text content to match on a single line (excluding newline characters)
     * @return true if matched and position was advanced, false otherwise
     */
    public boolean next(String content) {
        if (index < lineLength && index + content.length() <= lineLength) {
            // Can't use startsWith because it's not available on CharSequence
            for (int i = 0; i < content.length(); i++) {
                if (line.getContent().charAt(index + i) != content.charAt(i)) {
                    return false;
                }
            }
            index += content.length();
            return true;
        } else {
            return false;
        }
    }

    public int matchMultiple(char c) {
        int count = 0;
        while (peek() == c) {
            count++;
            next();
        }
        return count;
    }

    public int match(CharMatcher matcher) {
        int count = 0;
        while (matcher.matches(peek())) {
            count++;
            next();
        }
        return count;
    }

    public int whitespace() {
        int count = 0;
        while (true) {
            switch (peek()) {
                case ' ':
                case '\t':
                case '\n':
                case '\u000B':
                case '\f':
                case '\r':
                    count++;
                    next();
                    break;
                default:
                    return count;
            }
        }
    }

    public int find(char c) {
        int count = 0;
        while (true) {
            char cur = peek();
            if (cur == Scanner.END) {
                return -1;
            } else if (cur == c) {
                return count;
            }
            count++;
            next();
        }
    }

    public int find(CharMatcher matcher) {
        int count = 0;
        while (true) {
            char c = peek();
            if (c == END) {
                return -1;
            } else if (matcher.matches(c)) {
                return count;
            }
            count++;
            next();
        }
    }

    // Don't expose the int index, because it would be good if we could switch input to a List<String> of lines later
    // instead of one contiguous String.
    public Position position() {
        return new Position(lineIndex, index);
    }

    public void setPosition(Position position) {
        checkPosition(position.lineIndex, position.index);
        this.lineIndex = position.lineIndex;
        this.index = position.index;
        setLine(lines.get(this.lineIndex));
    }

    // For cases where the caller appends the result to a StringBuilder, we could offer another method to avoid some
    // unnecessary copying.
    public SourceLines getSource(Position begin, Position end) {
        if (begin.lineIndex == end.lineIndex) {
            // Shortcut for common case of text from a single line
            SourceLine line = lines.get(begin.lineIndex);
            CharSequence newContent = line.getContent().subSequence(begin.index, end.index);
            SourceSpan newSourceSpan = null;
            SourceSpan sourceSpan = line.getSourceSpan();
            if (sourceSpan != null) {
                newSourceSpan = SourceSpan.of(sourceSpan.getLineIndex(), sourceSpan.getColumnIndex() + begin.index, newContent.length());
            }
            return SourceLines.of(SourceLine.of(newContent, newSourceSpan));
        } else {
            SourceLines sourceLines = SourceLines.empty();

            SourceLine firstLine = lines.get(begin.lineIndex);
            sourceLines.addLine(firstLine.substring(begin.index, firstLine.getContent().length()));

            // Lines between begin and end (we are appending the full line)
            for (int line = begin.lineIndex + 1; line < end.lineIndex; line++) {
                sourceLines.addLine(lines.get(line));
            }

            SourceLine lastLine = lines.get(end.lineIndex);
            sourceLines.addLine(lastLine.substring(0, end.index));
            return sourceLines;
        }
    }

    private void setLine(SourceLine line) {
        this.line = line;
        this.lineLength = line.getContent().length();
    }

    private void checkPosition(int lineIndex, int index) {
        if (lineIndex < 0 || lineIndex >= lines.size()) {
            throw new IllegalArgumentException("Line index " + lineIndex + " out of range, number of lines: " + lines.size());
        }
        SourceLine line = lines.get(lineIndex);
        if (index < 0 || index > line.getContent().length()) {
            throw new IllegalArgumentException("Index " + index + " out of range, line length: " + line.getContent().length());
        }
    }
}
