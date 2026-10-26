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

package jdk.internal.org.commonmark.renderer.text;

import java.io.IOException;
import java.util.LinkedList;

public class TextContentWriter {

    private final Appendable buffer;
    private final LineBreakRendering lineBreakRendering;

    private final LinkedList<String> prefixes = new LinkedList<>();
    private final LinkedList<Boolean> tight = new LinkedList<>();

    private String blockSeparator = null;
    private char lastChar;

    public TextContentWriter(Appendable out) {
        this(out, LineBreakRendering.COMPACT);
    }

    public TextContentWriter(Appendable out, LineBreakRendering lineBreakRendering) {
        this.buffer = out;
        this.lineBreakRendering = lineBreakRendering;
    }

    public void whitespace() {
        if (lastChar != 0 && lastChar != ' ') {
            write(' ');
        }
    }

    public void colon() {
        if (lastChar != 0 && lastChar != ':') {
            write(':');
        }
    }

    public void line() {
        append('\n');
        writePrefixes();
    }

    public void block() {
        blockSeparator = lineBreakRendering == LineBreakRendering.STRIP ? " " : //
                lineBreakRendering == LineBreakRendering.COMPACT || isTight() ? "\n" : "\n\n";
    }

    public void resetBlock() {
        blockSeparator = null;
    }

    public void writeStripped(String s) {
        write(s.replaceAll("[\\r\\n\\s]+", " "));
    }

    public void write(String s) {
        flushBlockSeparator();
        append(s);
    }

    public void write(char c) {
        flushBlockSeparator();
        append(c);
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
        write(prefix);
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

    private boolean isTight() {
        return !tight.isEmpty() && tight.getLast();
    }

    private void writePrefixes() {
        for (String prefix : prefixes) {
            append(prefix);
        }
    }

    /**
     * If a block separator has been enqueued with {@link #block()} but not yet written, write it now.
     */
    private void flushBlockSeparator() {
        if (blockSeparator != null) {
            if (blockSeparator.equals("\n") || blockSeparator.equals("\n\n")) {
                for (int i = 0; i < blockSeparator.length(); i++) {
                    var sep = blockSeparator.charAt(i);
                    append(sep);
                    writePrefixes();
                }
            } else {
                append(blockSeparator);
            }
            blockSeparator = null;
        }
    }

    private void append(String s) {
        try {
            buffer.append(s);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        int length = s.length();
        if (length != 0) {
            lastChar = s.charAt(length - 1);
        }
    }

    private void append(char c) {
        try {
            buffer.append(c);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        lastChar = c;
    }
}
