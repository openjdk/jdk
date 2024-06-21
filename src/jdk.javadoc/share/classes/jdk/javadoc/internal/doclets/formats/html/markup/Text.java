/*
 * Copyright (c) 2010, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.formats.html.markup;

import java.io.IOException;
import java.io.Writer;

import jdk.javadoc.internal.doclets.formats.html.Content;

/**
 * Class for containing immutable string content for HTML tags of javadoc output.
 * Newlines are always represented by {@code \n}.
 * Any special HTML characters will be escaped if and when the content is written out.
 */
public class Text extends Content {

    private final String string;

    public static final Text EMPTY = Text.of("");

    /**
     * Creates a new object containing immutable text.
     *
     * @param content the text content
     * @return the object
     */
    public static Text of(CharSequence content) {
        return new Text(content);
    }

    /**
     * Constructs an immutable text object.
     *
     * @param content content for the object
     */
    private Text(CharSequence content) {
        assert checkNewlines(content);
        string = content.toString();
    }

    @Override
    public boolean isEmpty() {
        return string.isEmpty();
    }

    @Override
    public boolean isPhrasingContent() {
        return true;
    }

    @Override
    public int charCount() {
        return charCount(string);
    }

    static int charCount(CharSequence cs) {
        return cs.length();
    }

    @Override
    public String toString() {
        return string;
    }

    @Override
    public boolean write(Writer out, String newline, boolean atNewline) throws IOException {
        out.write(Entity.escapeHtmlChars(string).replace("\n", newline));
        return string.endsWith("\n");
    }

    /**
     * The newline character, to be used when creating {@code Content} nodes.
     */
    public static final String NL = "\n";

    /**
     * Returns a given string with all newlines in the form {@code \n}.
     *
     * The sequences of interest are {@code \n}, {@code \r\n}, and {@code \r}.
     * {@code \n} is already in the right form, so can be ignored,
     * leaving code to handle {@code \r\n}, and {@code \r}.
     *
     * @param text the string
     * @return the string with newlines in the form {@code \n}
     */
    public static CharSequence normalizeNewlines(CharSequence text) {
        // fast-track when the input is a string with no \r characters
        if (text instanceof String s && s.indexOf('\r') != -1) {
            return text;
        } else {
            var sb = new StringBuilder();
            var s = text.toString();
            int sLen = s.length();
            int start = 0;
            int pos;
            while ((pos = s.indexOf('\r', start)) != -1) {
                sb.append(s, start, pos);
                sb.append('\n');
                pos++;
                if (pos < sLen && s.charAt(pos) == '\n') {
                    pos++;
                }
                start = pos;
            }
            sb.append(s.substring(start));
            return sb;
        }
    }

    /**
     * Check for the absence of {@code \r} characters.
     * @param cs the characters to be checked
     * @return {@code true} if there are no {@code \r} characters, and {@code false} otherwise
     */
    static boolean checkNewlines(CharSequence cs) {
        return !cs.toString().contains("\r");
    }

}
