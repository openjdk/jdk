/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.html;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Locale;

/**
 * Writes HTML content to an enclosed writer, escaping characters with special meaning in HTML
 * and characters that can't be represented in the writer's output encoding with character
 * references (entities) as appropriate for the content being written. It also converts newlines
 * to the configured newline sequence.
 *
 * <p>The class provides write methods for plain HTML text, HTML markup, and raw content
 * to apply the appropriate escaping.
 *
 * @apiNote HtmlWriter might have been a more fitting name for this class, but it is too
 * similar to HtmlDocletWriter. The name ContentWriter also reflects the connection to the
 * Content class hierarchy.
 */
public class ContentWriter {

    private final Writer writer;
    private final CharsetEncoder encoder;
    private final String newline;

    /**
     * Creates a new content writer with the given enclosed writer and newline sequence,
     * which should be either {@code \n} or the platform line separator. The new content
     * writer assumes the enclosed writer instance is able to write all Unicode characters.
     *
     * @param writer the enclosed writer
     * @param newline the newline sequence to use
     */
    public ContentWriter(Writer writer, String newline) {
        this(writer, newline, "UTF-16");
    }

    /**
     * Creates a new content writer with the given enclosed writer, character encoding
     * and newline sequence, which should be either {@code \n} or the platform line separator.
     * The encoding is used to determine which characters require numeric character references;
     * it does not configure the enclosed writer.
     *
     * @param writer the enclosed writer
     * @param newline the newline sequence to use
     * @param encoding the output encoding
     */
    public ContentWriter(Writer writer, String newline, String encoding) {
        this.writer = writer;
        this.encoder = Charset.forName(encoding).newEncoder();
        this.newline = newline;
    }

    /**
     * Writes plain text as HTML text, escaping {@code <}, {@code >}, {@code &}, and
     * non-breaking spaces with character references. Characters that cannot be represented
     * in the output encoding are replaced with numeric character references.
     * Newlines are replaced with the configured newline sequence.
     *
     * @param text the plain text
     * @return true if text ends with a newline
     * @throws IOException if an I/O exception occurs
     */
    public boolean writeText(CharSequence text) throws IOException {
        String output = replaceNewline(escapeCharacters(text.toString(), true));
        writer.write(output);
        return output.endsWith(newline);
    }

    /**
     * Writes HTML content, preserving markup and existing character
     * references. Characters that cannot be represented in the output
     * encoding are replaced with numeric character references.
     * Newlines are replaced with the configured newline sequence.
     *
     * @param markup the HTML content
     * @return true if text ends with a newline
     * @throws IOException if an I/O exception occurs
     */
    public boolean writeMarkup(String markup) throws IOException {
        String output = replaceNewline(escapeCharacters(markup, false));
        writer.write(output);
        return output.endsWith(newline);
    }

    /**
     * Writes raw content without HTML escaping or replacement of characters
     * that cannot be represented in the output encoding. Newlines are converted
     * to the configured newline sequence.
     *
     * @param text the raw content
     * @return true if text ends with a newline
     * @throws IOException if an I/O exception occurs
     */
    public boolean writeRaw(String text) throws IOException {
        String output = replaceNewline(text);
        writer.write(output);
        return output.endsWith(newline);
    }

    /**
     * Writes an HTML attribute with the given name and value, preceded by a space.
     * A non-empty value is enclosed in double quotes, with HTML special characters
     * and double quotes escaped using character references. Characters that cannot
     * be represented in the output encoding are replaced with numeric character
     * references. If the value is empty, only the attribute name is written.
     *
     * @param attr the attribute name
     * @param value the unescaped attribute value
     * @throws IOException if an I/O exception occurs
     */
    public void writeAttribute(HtmlAttr attr, String value) throws IOException {
        writer.write(" ");
        writer.write(attr.getName());
        if (!value.isEmpty()) {
            writer.write("=\"");
            writer.write(escapeDoubleQuotes(escapeCharacters(value, true)));
            writer.write("\"");
        }
    }

    /**
     * Writes the configured newline sequence.
     *
     * @throws IOException if an I/O exception occurs
     */
    public void writeNewline() throws IOException {
        writer.write(newline);
    }

    /**
     * Escapes the characters in {@code str}. Characters that can't be represented in
     * the configured encoding are replaced with numeric character references.
     * If {@code isHtmlText} is true, also characters with special meaning in HTML
     * are escaped with character references.
     *
     * @param str string to escape
     * @param isHtmlText true if {@code str} represents HTML text
     * @return text with escaped characters
     */
    private String escapeCharacters(String str, boolean isHtmlText) {
        if (!encoder.canEncode(str)) {
            StringBuilder sb = new StringBuilder();
            escapeCharacters(str, 0, sb, isHtmlText);
            return sb.toString();
        }

        if (isHtmlText) {
            for (int i = 0; i < str.length(); ) {
                int cp = str.codePointAt(i);

                if (cp == '<' || cp == '>' || cp == '&' || cp == 0xA0) {
                    StringBuilder sb = new StringBuilder(str.substring(0, i));
                    escapeCharacters(str, i, sb, isHtmlText);
                    return sb.toString();
                }

                i = i + Character.charCount(cp);

            }
        }
        return str;
    }

    private void escapeCharacters(String s, int start, StringBuilder sb, boolean isHtmlText) {
        for (int i = start ; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i = i + Character.charCount(cp);

            if (isHtmlText) {
                switch (cp) {
                    case '<' -> sb.append(Entity.LESS_THAN.text);
                    case '>' -> sb.append(Entity.GREATER_THAN.text);
                    case '&' -> sb.append(Entity.AMPERSAND.text);
                    case 0xA0 -> sb.append(Entity.NO_BREAK_SPACE.text);
                    // other characters are replaced by numeric references as required by the writer's encoding
                    default -> appendCodePoint(cp, encoder, sb);
                }
            } else {
                appendCodePoint(cp, encoder, sb);
            }
        }
    }

    private static void appendCodePoint(int cp, CharsetEncoder encoder, StringBuilder sb) {
        if (Character.isBmpCodePoint(cp)) {
            if (encoder.canEncode((char) cp)) {
                sb.append((char) cp);
                return;
            }
        } else {
            String str = new String(Character.toChars(cp));
            if (encoder.canEncode(str)) {
                sb.append(str);
                return;
            }
        }

        sb.append("&#x")
                .append(Integer.toHexString(cp).toUpperCase(Locale.ROOT))
                .append(";");
    }


    private String replaceNewline(String text) {
        return newline.equals("\n") ? text : text.replace("\n", newline);
    }

    private static String escapeDoubleQuotes(String str) {
        return str.replace("\"", Entity.DOUBLE_QUOTE.text);
    }
}
