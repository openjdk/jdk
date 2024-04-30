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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.javadoc.internal.doclets.formats.html.Content;

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
        return new RawHtml(rawHtml) {
            @Override
            public int charCount() {
                return charCount(rawHtmlContent);
            }
        };
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
        return new RawHtml(sb);
    }

    /**
     * Creates HTML for the end of an element.
     *
     * @param name the name of the element
     * @return the HTML
     */
    public static RawHtml endElement(CharSequence name) {
        return new RawHtml("</" + name + ">");
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
        return new RawHtml(body.startsWith(prefix) && body.endsWith(suffix) ? body : prefix + body + suffix);
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

    Pattern tag = Pattern.compile("<(?<tag>[A-Za-z0-9]+)(\\s|>)");
    @Override
    public boolean isPhrasingContent() {
        Matcher m = tag.matcher(rawHtmlContent);
        while (m.find()) {
            try {
                var tn = TagName.of(m.group("tag"));
                if (!tn.phrasingContent) {
                    return false;
                }
            } catch (IllegalArgumentException e) {
                // unknown tag
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return rawHtmlContent;
    }

    private enum State { TEXT, ENTITY, TAG, STRING }

    protected static int charCount(CharSequence htmlText) {
        State state = State.TEXT;
        int count = 0;
        for (int i = 0; i < htmlText.length(); i++) {
            char c = htmlText.charAt(i);
            switch (state) {
                case TEXT:
                    switch (c) {
                        case '<':
                            state = State.TAG;
                            break;
                        case '&':
                            state = State.ENTITY;
                            count++;
                            break;
                        default:
                            count++;
                    }
                    break;

                case ENTITY:
                    if (!Character.isLetterOrDigit(c))
                        state = State.TEXT;
                    break;

                case TAG:
                    switch (c) {
                        case '"':
                            state = State.STRING;
                            break;
                        case '>':
                            state = State.TEXT;
                            break;
                    }
                    break;

                case STRING:
                    switch (c) {
                        case '"':
                            state = State.TAG;
                            break;
                    }
            }
        }
        return count;
    }

    @Override
    public boolean write(Writer out, String newline, boolean atNewline) throws IOException {
        out.write(rawHtmlContent.replace("\n", newline));
        return rawHtmlContent.endsWith("\n");
    }
}
