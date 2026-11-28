/*
 * Copyright (c) 2010, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.javac.api.Entity;

import java.io.IOException;

/**
 * Class for generating raw HTML content to be added to HTML pages of javadoc output.
 */
public class RawHtml extends Content {

    protected final String rawHtmlContent;

    /**
     * Creates HTML for an arbitrary string of HTML.
     * The string is accepted as-is and is not validated in any way.
     * It should be syntactically well-formed and contain matching {@code <} and {@code >},
     * and matching quotes for attributes.
     *
     * @param rawHtml the string
     * @return the HTML
     */
    public static RawHtml of(CharSequence rawHtml) {
        return new RawHtml(rawHtml);
    }

    /**
     * Creates HTML for a fragment of Markdown output.
     *
     * @param markdownOutput the fragment
     * @return the HTML
     */
    public static RawHtml markdown(CharSequence markdownOutput) {
        return of(markdownOutput);
    }

    /**
     * Creates HTML with known plain text representation, allowing to skip
     * costly implementations of {@code toPlainText()} and {@code charCount()}.
     */
    private static RawHtml withPlainText(CharSequence rawHtml, String plainText) {
        return new RawHtml(rawHtml) {
            @Override
            public String toPlainText() {
                return plainText;
            }

            @Override
            public int charCount() {
                return plainText.length();
            }
        };
    }

    /**
     * Creates HTML for the start of an element.
     *
     * @param name the name of the element
     * @param attrs content containing any attributes
     * @param selfClosing whether this is a self-closing element.
     * @return the HTML
     */
    public static RawHtml startElement(CharSequence name, Content attrs, boolean selfClosing) {
        StringBuilder sb = new StringBuilder("<" + name);
        if (!attrs.isEmpty()) {
            sb.append(" ");
            sb.append(attrs);
        }
        sb.append(selfClosing ? "/>" : ">");
        // <br> tag is converted to " " in plain text
        return withPlainText(sb, name.toString().equalsIgnoreCase("br") ? " " : "");
    }

    /**
     * Creates HTML for the end of an element.
     *
     * @param name the name of the element
     * @return the HTML
     */
    public static RawHtml endElement(CharSequence name) {
        return withPlainText("</" + name + ">", "");
    }

    /**
     * Creates HTML for an HTML comment.
     *
     * The body will be enclosed in {@code <!--} and {@code -->} if it does not
     * already begin and end with those sequences.
     *
     * @param body the body of the comment
     *
     * @return the HTML
     */
    public static RawHtml comment(String body) {
        return section("<!--", body, "-->");
    }

    /**
     * Creates HTML for an HTML CDATA section.
     *
     * The body will be enclosed in {@code <![CDATA]} and {@code ]]>} if it does not
     * already begin and end with those sequences.
     *
     * @param body the body of the CDATA section
     *
     * @return the HTML
     */
    public static RawHtml cdata(String body) {
        return section("<![CDATA[", body, "]]>");
    }

    private static RawHtml section(String prefix, String body, String suffix) {
        // Character references are not interpreted inside comments or CDATA,
        // so write their contents without character-reference substitution.
        // Since CDATA is treated like a comment in ordinary HTML content, the
        // empty string is used as plain text representation.
        return new RawHtml(body.startsWith(prefix) && body.endsWith(suffix) ? body : prefix + body + suffix) {
            @Override
            public boolean isPhrasingContent() {
                return true; // Not actual phrasing content, but does not require block treatment.
            }

            @Override
            public int charCount() {
                return 0;
            }

            @Override
            public String toPlainText() {
                return "";
            }

            @Override
            public boolean write(ContentWriter out, boolean atNewline) throws IOException {
                return out.writeRaw(rawHtmlContent);
            }
        };
    }

    /**
     * Constructor to construct a RawHtml object.
     *
     * @param rawHtml raw HTML text to be added
     */
    private RawHtml(CharSequence rawHtml) {
        assert Text.checkNewlines(rawHtml);
        rawHtmlContent = rawHtml.toString();
    }

    @Override
    public boolean isEmpty() {
        return rawHtmlContent.isEmpty();
    }

    @Override
    public boolean isPhrasingContent() {
        return scan(rawHtmlContent, new ScanHandler<>() {
            boolean isPhrasing = true;

            @Override
            public void text(int start, int end) {}

            @Override
            public void entity(int start, int end) {}

            @Override
            public void tag(int nameStart, int nameEnd, boolean closing) {
                if (!closing) {
                    String tagName = rawHtmlContent.substring(nameStart, nameEnd);
                    HtmlTag tag = HtmlTag.of(tagName);
                    isPhrasing &= tag != null && tag.blockType == HtmlTag.BlockType.INLINE;
                }
            }

            @Override
            public Boolean result() {
                return isPhrasing;
            }
        });
    }

    @Override
    public int charCount() {
        return scan(rawHtmlContent, new ScanHandler<>() {
            int count = 0;

            @Override
            public void text(int start, int end) {
                count += end - start;
            }

            @Override
            public void entity(int start, int end) {
                String name = rawHtmlContent.substring(start, end);
                String str = Entity.getCharacters(name);
                count += str != null ? str.length() : name.length() + 2;
            }

            @Override
            public void tag(int nameStart, int nameEnd, boolean closing) {
                if (!closing && nameEnd - nameStart == 2
                        && rawHtmlContent.regionMatches(true, nameStart, "br", 0, 2)) {
                    count++;
                }
            }

            @Override
            public Integer result() {
                return count;
            }
        });
    }

    @Override
    public String toPlainText() {
        return scan(rawHtmlContent, new ScanHandler<>() {

            final StringBuilder sb = new StringBuilder();

            @Override
            public void text(int start, int end) {
                sb.append(rawHtmlContent, start, end);
            }

            @Override
            public void entity(int start, int end) {
                String name = rawHtmlContent.substring(start, end);
                String chars = Entity.getCharacters(name);
                // Preserve references the decoder does not recognize.
                sb.append(chars != null ? chars : "&" + name + ";");
            }

            @Override
            public void tag(int nameStart, int nameEnd, boolean closing) {
                if (!closing && nameEnd - nameStart == 2
                        && rawHtmlContent.regionMatches(true, nameStart, "br", 0, 2)) {
                    sb.append(" ");
                }
            }

            @Override
            public String result() {
                return sb.toString();
            }
        });
    }

    @Override
    public String toString() {
        return rawHtmlContent;
    }

    @Override
    public boolean write(ContentWriter out, boolean atNewline) throws IOException {
        return out.writeMarkup(rawHtmlContent);
    }

    /**
     * A generic callback interface to pass to the {@link #scan} method.
     *
     * @param <R> the type of the result produced by the handler
     */
    protected interface ScanHandler<R> {
        void text(int start, int end);
        void entity(int start, int end);
        void tag(int nameStart, int nameEnd, boolean closing);
        R result();
    }

    private enum State { TEXT, ENTITY, TAG, ATTR, COMMENT }

    /**
     * Scans the content of a raw HTML fragment.
     *
     * @param htmlText an HTML fragment
     * @param handler callback handler
     * @return the result generated by the handler
     * @param <R> the type of the generated result
     */
    protected static <R> R scan(String htmlText, ScanHandler<R> handler) {
        State state = State.TEXT;
        int start = 0;
        char quote = 0;

        for (int i = 0; i < htmlText.length(); i++) {
            char c = htmlText.charAt(i);
            switch (state) {
                case TEXT:
                    switch (c) {
                        case '<':
                            state = State.TAG;
                            handler.text(start, i);
                            start = i;
                            break;
                        case '&':
                            state = State.ENTITY;
                            handler.text(start, i);
                            start = i;
                            break;
                    }
                    break;

                case ENTITY:
                    switch (c) {
                        case '#':
                            if (i > start + 1) {
                                state = State.TEXT;
                            }
                            break;
                        case ';':
                            handler.entity(start + 1, i);
                            start = i + 1;
                            state = State.TEXT;
                            break;
                        case '<':
                        case '&':
                            handler.text(start, i);
                            state = c == '<' ? State.TAG : State.ENTITY;
                            start = i;
                            break;
                        default:
                            if (!Character.isLetterOrDigit(c)) {
                                state = State.TEXT;
                            }
                    }
                    break;

                case TAG:
                    switch (c) {
                        case '"':
                        case '\'':
                            state = State.ATTR;
                            quote = c;
                            break;
                        case '>':
                            if (i > start + 1) {
                                int nameStart = start + 1;
                                boolean closing = htmlText.charAt(nameStart) == '/';
                                if (closing) {
                                    nameStart++;
                                }
                                int nameEnd = nameStart;
                                if (nameEnd < i && isTagNameStart(htmlText.charAt(nameEnd))) {
                                    do {
                                        nameEnd++;
                                    } while (nameEnd < i && isTagNamePart(htmlText.charAt(nameEnd)));
                                }
                                if (nameEnd > nameStart
                                        && (nameEnd == i || (nameEnd == i - 1 && htmlText.charAt(nameEnd) == '/')
                                                         || Character.isWhitespace(htmlText.charAt(nameEnd)))) {
                                    handler.tag(nameStart, nameEnd, closing);
                                    start = i + 1;
                                }
                            }

                            state = State.TEXT;
                            break;
                        case '<':
                            handler.text(start, i);
                            start = i;
                            break;
                        case '-':
                            if (i == start + 3 && htmlText.charAt(i - 2) == '!' && htmlText.charAt(i - 1) == '-') {
                                state = State.COMMENT;
                            }
                            break;
                    }
                    break;

                case ATTR:
                    switch (c) {
                        case '"':
                        case '\'':
                            if (c == quote) {
                                quote = 0;
                                state = State.TAG;
                            }
                            break;
                    }
                    break;

                case COMMENT:
                    if (c == '>' && i > start + 5 && htmlText.charAt(i - 1) == '-' && htmlText.charAt(i - 2) == '-') {
                        state = State.TEXT;
                        start = i + 1;
                    }
                    break;
            }
        }

        if (start < htmlText.length()) {
            // Handle incomplete tags, entities and comments as plain text.
            handler.text(start, htmlText.length());
        }

        return handler.result();
    }

    private static boolean isTagNameStart(char c) {
        return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z';
    }

    private static boolean isTagNamePart(char c) {
        return isTagNameStart(c) || c >= '0' && c <= '9' || c == ':' || c == '-';
    }
}
