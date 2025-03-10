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

package jdk.internal.org.commonmark.renderer.markdown;

import jdk.internal.org.commonmark.text.CharMatcher;

import java.io.IOException;
import java.util.LinkedList;

/**
 * Writer for Markdown (CommonMark) text.
 */
public class MarkdownWriter {

    private final Appendable buffer;

    private int blockSeparator = 0;
    private char lastChar;
    private boolean atLineStart = true;

    // Stacks of settings that affect various rendering behaviors. The common pattern here is that callers use "push" to
    // change a setting, render some nodes, and then "pop" the setting off the stack again to restore previous state.
    private final LinkedList<String> prefixes = new LinkedList<>();
    private final LinkedList<Boolean> tight = new LinkedList<>();
    private final LinkedList<CharMatcher> rawEscapes = new LinkedList<>();

    public MarkdownWriter(Appendable out) {
        buffer = out;
    }

    /**
     * Write the supplied string (raw/unescaped except if {@link #pushRawEscape} was used).
     */
    public void raw(String s) {
        flushBlockSeparator();
        write(s, null);
    }

    /**
     * Write the supplied character (raw/unescaped except if {@link #pushRawEscape} was used).
     */
    public void raw(char c) {
        flushBlockSeparator();
        write(c);
    }

    /**
     * Write the supplied string with escaping.
     *
     * @param s      the string to write
     * @param escape which characters to escape
     */
    public void text(String s, CharMatcher escape) {
        if (s.isEmpty()) {
            return;
        }
        flushBlockSeparator();
        write(s, escape);

        lastChar = s.charAt(s.length() - 1);
        atLineStart = false;
    }

    /**
     * Write a newline (line terminator).
     */
    public void line() {
        write('\n');
        writePrefixes();
        atLineStart = true;
    }

    /**
     * Enqueue a block separator to be written before the next text is written. Block separators are not written
     * straight away because if there are no more blocks to write we don't want a separator (at the end of the document).
     */
    public void block() {
        // Remember whether this should be a tight or loose separator now because tight could get changed in between
        // this and the next flush.
        blockSeparator = isTight() ? 1 : 2;
        atLineStart = true;
    }

    /**
     * Push a prefix onto the top of the stack. All prefixes are written at the beginning of each line, until the
     * prefix is popped again.
     *
     * @param prefix the raw prefix string
     */
    public void pushPrefix(String prefix) {
        prefixes.addLast(prefix);
    }

    /**
     * Write a prefix.
     *
     * @param prefix the raw prefix string to write
     */
    public void writePrefix(String prefix) {
        boolean tmp = atLineStart;
        raw(prefix);
        atLineStart = tmp;
    }

    /**
     * Remove the last prefix from the top of the stack.
     */
    public void popPrefix() {
        prefixes.removeLast();
    }

    /**
     * Change whether blocks are tight or loose. Loose is the default where blocks are separated by a blank line. Tight
     * is where blocks are not separated by a blank line. Tight blocks are used in lists, if there are no blank lines
     * within the list.
     * <p>
     * Note that changing this does not affect block separators that have already been enqueued with {@link #block()},
     * only future ones.
     */
    public void pushTight(boolean tight) {
        this.tight.addLast(tight);
    }

    /**
     * Remove the last "tight" setting from the top of the stack.
     */
    public void popTight() {
        this.tight.removeLast();
    }

    /**
     * Escape the characters matching the supplied matcher, in all text (text and raw). This might be useful to
     * extensions that add another layer of syntax, e.g. the tables extension that uses `|` to separate cells and needs
     * all `|` characters to be escaped (even in code spans).
     *
     * @param rawEscape the characters to escape in raw text
     */
    public void pushRawEscape(CharMatcher rawEscape) {
        rawEscapes.add(rawEscape);
    }

    /**
     * Remove the last raw escape from the top of the stack.
     */
    public void popRawEscape() {
        rawEscapes.removeLast();
    }

    /**
     * @return the last character that was written
     */
    public char getLastChar() {
        return lastChar;
    }

    /**
     * @return whether we're at the line start (not counting any prefixes), i.e. after a {@link #line} or {@link #block}.
     */
    public boolean isAtLineStart() {
        return atLineStart;
    }

    private void write(String s, CharMatcher escape) {
        try {
            if (rawEscapes.isEmpty() && escape == null) {
                // Normal fast path
                buffer.append(s);
            } else {
                for (int i = 0; i < s.length(); i++) {
                    append(s.charAt(i), escape);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        int length = s.length();
        if (length != 0) {
            lastChar = s.charAt(length - 1);
        }
        atLineStart = false;
    }

    private void write(char c) {
        try {
            append(c, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        lastChar = c;
        atLineStart = false;
    }

    private void writePrefixes() {
        if (!prefixes.isEmpty()) {
            for (String prefix : prefixes) {
                write(prefix, null);
            }
        }
    }

    /**
     * If a block separator has been enqueued with {@link #block()} but not yet written, write it now.
     */
    private void flushBlockSeparator() {
        if (blockSeparator != 0) {
            write('\n');
            writePrefixes();
            if (blockSeparator > 1) {
                write('\n');
                writePrefixes();
            }
            blockSeparator = 0;
        }
    }

    private void append(char c, CharMatcher escape) throws IOException {
        if (needsEscaping(c, escape)) {
            if (c == '\n') {
                // Can't escape this with \, use numeric character reference
                buffer.append("&#10;");
            } else {
                buffer.append('\\');
                buffer.append(c);
            }
        } else {
            buffer.append(c);
        }
    }

    private boolean isTight() {
        return !tight.isEmpty() && tight.getLast();
    }

    private boolean needsEscaping(char c, CharMatcher escape) {
        return (escape != null && escape.matches(c)) || rawNeedsEscaping(c);
    }

    private boolean rawNeedsEscaping(char c) {
        for (CharMatcher rawEscape : rawEscapes) {
            if (rawEscape.matches(c)) {
                return true;
            }
        }
        return false;
    }
}
