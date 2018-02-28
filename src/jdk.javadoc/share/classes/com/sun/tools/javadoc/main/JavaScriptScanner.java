/*
 * Copyright (c) 2012,2016, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javadoc.main;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.sun.tools.javadoc.main.JavaScriptScanner.TagParser.Kind;

import static com.sun.tools.javac.util.LayoutCharacters.EOI;

/**
 * Parser to detect use of JavaScript in documentation comments.
 */
@Deprecated(since="9", forRemoval=true)
@SuppressWarnings("removal")
public class JavaScriptScanner {
    public static interface Reporter {
        void report();
    }

    static class ParseException extends Exception {
        private static final long serialVersionUID = 0;
        ParseException(String key) {
            super(key);
        }
    }

    private Reporter reporter;

    /** The input buffer, index of most recent character read,
     *  index of one past last character in buffer.
     */
    protected char[] buf;
    protected int bp;
    protected int buflen;

    /** The current character.
     */
    protected char ch;

    private boolean newline = true;

    Map<String, TagParser> tagParsers;
    Set<String> eventAttrs;
    Set<String> uriAttrs;

    public JavaScriptScanner() {
        initTagParsers();
        initEventAttrs();
        initURIAttrs();
    }

    public void parse(String comment, Reporter r) {
        reporter = r;
        String c = comment;
        buf = new char[c.length() + 1];
        c.getChars(0, c.length(), buf, 0);
        buf[buf.length - 1] = EOI;
        buflen = buf.length - 1;
        bp = -1;
        newline = true;
        nextChar();

        blockContent();
        blockTags();
    }

    private void checkHtmlTag(String tag) {
        if (tag.equalsIgnoreCase("script")) {
            reporter.report();
        }
    }

    private void checkHtmlAttr(String name, String value) {
        String n = name.toLowerCase(Locale.ENGLISH);
        if (eventAttrs.contains(n)
                || uriAttrs.contains(n)
                    && value != null && value.toLowerCase(Locale.ENGLISH).trim().startsWith("javascript:")) {
            reporter.report();
        }
    }

    void nextChar() {
        ch = buf[bp < buflen ? ++bp : buflen];
        switch (ch) {
            case '\f': case '\n': case '\r':
                newline = true;
        }
    }

    /**
     * Read block content, consisting of text, html and inline tags.
     * Terminated by the end of input, or the beginning of the next block tag:
     * i.e. @ as the first non-whitespace character on a line.
     */
    @SuppressWarnings("fallthrough")
    protected void blockContent() {

        loop:
        while (bp < buflen) {
            switch (ch) {
                case '\n': case '\r': case '\f':
                    newline = true;
                    // fallthrough

                case ' ': case '\t':
                    nextChar();
                    break;

                case '&':
                    entity(null);
                    break;

                case '<':
                    html();
                    break;

                case '>':
                    newline = false;
                    nextChar();
                    break;

                case '{':
                    inlineTag(null);
                    break;

                case '@':
                    if (newline) {
                        break loop;
                    }
                    // fallthrough

                default:
                    newline = false;
                    nextChar();
            }
        }
    }

    /**
     * Read a series of block tags, including their content.
     * Standard tags parse their content appropriately.
     * Non-standard tags are represented by {@link UnknownBlockTag}.
     */
    protected void blockTags() {
        while (ch == '@')
            blockTag();
    }

    /**
     * Read a single block tag, including its content.
     * Standard tags parse their content appropriately.
     * Non-standard tags are represented by {@link UnknownBlockTag}.
     */
    protected void blockTag() {
        int p = bp;
        try {
            nextChar();
            if (isIdentifierStart(ch)) {
                String name = readTagName();
                TagParser tp = tagParsers.get(name);
                if (tp == null) {
                    blockContent();
                } else {
                    switch (tp.getKind()) {
                        case BLOCK:
                            tp.parse(p);
                            return;
                        case INLINE:
                            return;
                    }
                }
            }
            blockContent();
        } catch (ParseException e) {
            blockContent();
        }
    }

    protected void inlineTag(Void list) {
        newline = false;
        nextChar();
        if (ch == '@') {
            inlineTag();
        }
    }

    /**
     * Read a single inline tag, including its content.
     * Standard tags parse their content appropriately.
     * Non-standard tags are represented by {@link UnknownBlockTag}.
     * Malformed tags may be returned as {@link Erroneous}.
     */
    protected void inlineTag() {
        int p = bp - 1;
        try {
            nextChar();
            if (isIdentifierStart(ch)) {
                String name = readTagName();
                TagParser tp = tagParsers.get(name);

                if (tp == null) {
                    skipWhitespace();
                    inlineText(WhitespaceRetentionPolicy.REMOVE_ALL);
                    nextChar();
                } else {
                    skipWhitespace();
                    if (tp.getKind() == TagParser.Kind.INLINE) {
                        tp.parse(p);
                    } else { // handle block tags (ex: @see) in inline content
                        inlineText(WhitespaceRetentionPolicy.REMOVE_ALL); // skip content
                        nextChar();
                    }
                }
            }
        } catch (ParseException e) {
        }
    }

    private static enum WhitespaceRetentionPolicy {
        RETAIN_ALL,
        REMOVE_FIRST_SPACE,
        REMOVE_ALL
    }

    /**
     * Read plain text content of an inline tag.
     * Matching pairs of { } are skipped; the text is terminated by the first
     * unmatched }. It is an error if the beginning of the next tag is detected.
     */
    private void inlineText(WhitespaceRetentionPolicy whitespacePolicy) throws ParseException {
        switch (whitespacePolicy) {
            case REMOVE_ALL:
                skipWhitespace();
                break;
            case REMOVE_FIRST_SPACE:
                if (ch == ' ')
                    nextChar();
                break;
            case RETAIN_ALL:
            default:
                // do nothing
                break;

        }
        int pos = bp;
        int depth = 1;

        loop:
        while (bp < buflen) {
            switch (ch) {
                case '\n': case '\r': case '\f':
                    newline = true;
                    break;

                case ' ': case '\t':
                    break;

                case '{':
                    newline = false;
                    depth++;
                    break;

                case '}':
                    if (--depth == 0) {
                        return;
                    }
                    newline = false;
                    break;

                case '@':
                    if (newline)
                        break loop;
                    newline = false;
                    break;

                default:
                    newline = false;
                    break;
            }
            nextChar();
        }
        throw new ParseException("dc.unterminated.inline.tag");
    }

    /**
     * Read Java class name, possibly followed by member
     * Matching pairs of {@literal < >} are skipped. The text is terminated by the first
     * unmatched }. It is an error if the beginning of the next tag is detected.
     */
    // TODO: boolean allowMember should be enum FORBID, ALLOW, REQUIRE
    // TODO: improve quality of parse to forbid bad constructions.
    // TODO: update to use ReferenceParser
    @SuppressWarnings("fallthrough")
    protected void reference(boolean allowMember) throws ParseException {
        int pos = bp;
        int depth = 0;

        // scan to find the end of the signature, by looking for the first
        // whitespace not enclosed in () or <>, or the end of the tag
        loop:
        while (bp < buflen) {
            switch (ch) {
                case '\n': case '\r': case '\f':
                    newline = true;
                    // fallthrough

                case ' ': case '\t':
                    if (depth == 0)
                        break loop;
                    break;

                case '(':
                case '<':
                    newline = false;
                    depth++;
                    break;

                case ')':
                case '>':
                    newline = false;
                    --depth;
                    break;

                case '}':
                    if (bp == pos)
                        return;
                    newline = false;
                    break loop;

                case '@':
                    if (newline)
                        break loop;
                    // fallthrough

                default:
                    newline = false;

            }
            nextChar();
        }

        if (depth != 0)
            throw new ParseException("dc.unterminated.signature");
    }

    /**
     * Read Java identifier
     * Matching pairs of { } are skipped; the text is terminated by the first
     * unmatched }. It is an error if the beginning of the next tag is detected.
     */
    @SuppressWarnings("fallthrough")
    protected void identifier() throws ParseException {
        skipWhitespace();
        int pos = bp;

        if (isJavaIdentifierStart(ch)) {
            readJavaIdentifier();
            return;
        }

        throw new ParseException("dc.identifier.expected");
    }

    /**
     * Read a quoted string.
     * It is an error if the beginning of the next tag is detected.
     */
    @SuppressWarnings("fallthrough")
    protected void quotedString() {
        int pos = bp;
        nextChar();

        loop:
        while (bp < buflen) {
            switch (ch) {
                case '\n': case '\r': case '\f':
                    newline = true;
                    break;

                case ' ': case '\t':
                    break;

                case '"':
                    nextChar();
                    // trim trailing white-space?
                    return;

                case '@':
                    if (newline)
                        break loop;

            }
            nextChar();
        }
    }

    /**
     * Read a term ie. one word.
     * It is an error if the beginning of the next tag is detected.
     */
    @SuppressWarnings("fallthrough")
    protected void inlineWord() {
        int pos = bp;
        int depth = 0;
        loop:
        while (bp < buflen) {
            switch (ch) {
                case '\n':
                    newline = true;
                    // fallthrough

                case '\r': case '\f': case ' ': case '\t':
                    return;

                case '@':
                    if (newline)
                        break loop;

                case '{':
                    depth++;
                    break;

                case '}':
                    if (depth == 0 || --depth == 0)
                        return;
                    break;
            }
            newline = false;
            nextChar();
        }
    }

    /**
     * Read general text content of an inline tag, including HTML entities and elements.
     * Matching pairs of { } are skipped; the text is terminated by the first
     * unmatched }. It is an error if the beginning of the next tag is detected.
     */
    @SuppressWarnings("fallthrough")
    private void inlineContent() {

        skipWhitespace();
        int pos = bp;
        int depth = 1;

        loop:
        while (bp < buflen) {

            switch (ch) {
                case '\n': case '\r': case '\f':
                    newline = true;
                    // fall through

                case ' ': case '\t':
                    nextChar();
                    break;

                case '&':
                    entity(null);
                    break;

                case '<':
                    newline = false;
                    html();
                    break;

                case '{':
                    newline = false;
                    depth++;
                    nextChar();
                    break;

                case '}':
                    newline = false;
                    if (--depth == 0) {
                        nextChar();
                        return;
                    }
                    nextChar();
                    break;

                case '@':
                    if (newline)
                        break loop;
                    // fallthrough

                default:
                    nextChar();
                    break;
            }
        }

    }

    protected void entity(Void list) {
        newline = false;
        entity();
    }

    /**
     * Read an HTML entity.
     * {@literal &identifier; } or {@literal &#digits; } or {@literal &#xhex-digits; }
     */
    protected void entity() {
        nextChar();
        String name = null;
        if (ch == '#') {
            int namep = bp;
            nextChar();
            if (isDecimalDigit(ch)) {
                nextChar();
                while (isDecimalDigit(ch))
                    nextChar();
                name = new String(buf, namep, bp - namep);
            } else if (ch == 'x' || ch == 'X') {
                nextChar();
                if (isHexDigit(ch)) {
                    nextChar();
                    while (isHexDigit(ch))
                        nextChar();
                    name = new String(buf, namep, bp - namep);
                }
            }
        } else if (isIdentifierStart(ch)) {
            name = readIdentifier();
        }

        if (name != null) {
            if (ch != ';')
                return;
            nextChar();
        }
    }

    /**
     * Read the start or end of an HTML tag, or an HTML comment
     * {@literal <identifier attrs> } or {@literal </identifier> }
     */
    protected void html() {
        int p = bp;
        nextChar();
        if (isIdentifierStart(ch)) {
            String name = readIdentifier();
            checkHtmlTag(name);
            htmlAttrs();
            if (ch == '/') {
                nextChar();
            }
            if (ch == '>') {
                nextChar();
                return;
            }
        } else if (ch == '/') {
            nextChar();
            if (isIdentifierStart(ch)) {
                readIdentifier();
                skipWhitespace();
                if (ch == '>') {
                    nextChar();
                    return;
                }
            }
        } else if (ch == '!') {
            nextChar();
            if (ch == '-') {
                nextChar();
                if (ch == '-') {
                    nextChar();
                    while (bp < buflen) {
                        int dash = 0;
                        while (ch == '-') {
                            dash++;
                            nextChar();
                        }
                        // Strictly speaking, a comment should not contain "--"
                        // so dash > 2 is an error, dash == 2 implies ch == '>'
                        // See http://www.w3.org/TR/html-markup/syntax.html#syntax-comments
                        // for more details.
                        if (dash >= 2 && ch == '>') {
                            nextChar();
                            return;
                        }

                        nextChar();
                    }
                }
            }
        }

        bp = p + 1;
        ch = buf[bp];
    }

    /**
     * Read a series of HTML attributes, terminated by {@literal > }.
     * Each attribute is of the form {@literal identifier[=value] }.
     * "value" may be unquoted, single-quoted, or double-quoted.
     */
    protected void htmlAttrs() {
        skipWhitespace();

        loop:
        while (isIdentifierStart(ch)) {
            int namePos = bp;
            String name = readAttributeName();
            skipWhitespace();
            StringBuilder value = new StringBuilder();
            if (ch == '=') {
                nextChar();
                skipWhitespace();
                if (ch == '\'' || ch == '"') {
                    char quote = ch;
                    nextChar();
                    while (bp < buflen && ch != quote) {
                        if (newline && ch == '@') {
                            // No point trying to read more.
                            // In fact, all attrs get discarded by the caller
                            // and superseded by a malformed.html node because
                            // the html tag itself is not terminated correctly.
                            break loop;
                        }
                        value.append(ch);
                        nextChar();
                    }
                    nextChar();
                } else {
                    while (bp < buflen && !isUnquotedAttrValueTerminator(ch)) {
                        value.append(ch);
                        nextChar();
                    }
                }
                skipWhitespace();
            }
            checkHtmlAttr(name, value.toString());
        }
    }

    protected void attrValueChar(Void list) {
        switch (ch) {
            case '&':
                entity(list);
                break;

            case '{':
                inlineTag(list);
                break;

            default:
                nextChar();
        }
    }

    protected boolean isIdentifierStart(char ch) {
        return Character.isUnicodeIdentifierStart(ch);
    }

    protected String readIdentifier() {
        int start = bp;
        nextChar();
        while (bp < buflen && Character.isUnicodeIdentifierPart(ch))
            nextChar();
        return new String(buf, start, bp - start);
    }

    protected String readAttributeName() {
        int start = bp;
        nextChar();
        while (bp < buflen && (Character.isUnicodeIdentifierPart(ch) || ch == '-'))
            nextChar();
        return new String(buf, start, bp - start);
    }

    protected String readTagName() {
        int start = bp;
        nextChar();
        while (bp < buflen
                && (Character.isUnicodeIdentifierPart(ch) || ch == '.'
                || ch == '-' || ch == ':')) {
            nextChar();
        }
        return new String(buf, start, bp - start);
    }

    protected boolean isJavaIdentifierStart(char ch) {
        return Character.isJavaIdentifierStart(ch);
    }

    protected String readJavaIdentifier() {
        int start = bp;
        nextChar();
        while (bp < buflen && Character.isJavaIdentifierPart(ch))
            nextChar();
        return new String(buf, start, bp - start);
    }

    protected boolean isDecimalDigit(char ch) {
        return ('0' <= ch && ch <= '9');
    }

    protected boolean isHexDigit(char ch) {
        return ('0' <= ch && ch <= '9')
                || ('a' <= ch && ch <= 'f')
                || ('A' <= ch && ch <= 'F');
    }

    protected boolean isUnquotedAttrValueTerminator(char ch) {
        switch (ch) {
            case '\f': case '\n': case '\r': case '\t':
            case ' ':
            case '"': case '\'': case '`':
            case '=': case '<': case '>':
                return true;
            default:
                return false;
        }
    }

    protected boolean isWhitespace(char ch) {
        return Character.isWhitespace(ch);
    }

    protected void skipWhitespace() {
        while (isWhitespace(ch)) {
            nextChar();
        }
    }

    /**
     * @param start position of first character of string
     * @param end position of character beyond last character to be included
     */
    String newString(int start, int end) {
        return new String(buf, start, end - start);
    }

    static abstract class TagParser {
        enum Kind { INLINE, BLOCK }

        final Kind kind;
        final String name;


        TagParser(Kind k, String tk) {
            kind = k;
            name = tk;
        }

        TagParser(Kind k, String tk, boolean retainWhiteSpace) {
            this(k, tk);
        }

        Kind getKind() {
            return kind;
        }

        String getName() {
            return name;
        }

        abstract void parse(int pos) throws ParseException;
    }

    /**
     * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/unix/javadoc.html#CHDJGIJB">Javadoc Tags</a>
     */
    @SuppressWarnings("deprecation")
    private void initTagParsers() {
        TagParser[] parsers = {
            // @author name-text
            new TagParser(Kind.BLOCK, "author") {
                @Override
                public void parse(int pos) {
                    blockContent();
                }
            },

            // {@code text}
            new TagParser(Kind.INLINE, "code", true) {
                @Override
                public void parse(int pos) throws ParseException {
                    inlineText(WhitespaceRetentionPolicy.REMOVE_FIRST_SPACE);
                    nextChar();
                }
            },

            // @deprecated deprecated-text
            new TagParser(Kind.BLOCK, "deprecated") {
                @Override
                public void parse(int pos) {
                    blockContent();
                }
            },

            // {@docRoot}
            new TagParser(Kind.INLINE, "docRoot") {
                @Override
                public void parse(int pos) throws ParseException {
                    if (ch == '}') {
                        nextChar();
                        return;
                    }
                    inlineText(WhitespaceRetentionPolicy.REMOVE_ALL); // skip unexpected content
                    nextChar();
                    throw new ParseException("dc.unexpected.content");
                }
            },

            // @exception class-name description
            new TagParser(Kind.BLOCK, "exception") {
                @Override
                public void parse(int pos) throws ParseException {
                    skipWhitespace();
                    reference(false);
                    blockContent();
                }
            },

            // @hidden hidden-text
            new TagParser(Kind.BLOCK, "hidden") {
                @Override
                public void parse(int pos) {
                    blockContent();
                }
            },

            // @index search-term options-description
            new TagParser(Kind.INLINE, "index") {
                @Override
                public void parse(int pos) throws ParseException {
                    skipWhitespace();
                    if (ch == '}') {
                        throw new ParseException("dc.no.content");
                    }
                    if (ch == '"') quotedString(); else inlineWord();
                    skipWhitespace();
                    if (ch != '}') {
                        inlineContent();
                    } else {
                        nextChar();
                    }
                }
            },

            // {@inheritDoc}
            new TagParser(Kind.INLINE, "inheritDoc") {
                @Override
                public void parse(int pos) throws ParseException {
                    if (ch == '}') {
                        nextChar();
                        return;
                    }
                    inlineText(WhitespaceRetentionPolicy.REMOVE_ALL); // skip unexpected content
                    nextChar();
                    throw new ParseException("dc.unexpected.content");
                }
            },

            // {@link package.class#member label}
            new TagParser(Kind.INLINE, "link") {
                @Override
                public void parse(int pos) throws ParseException {
                    reference(true);
                    inlineContent();
                }
            },

            // {@linkplain package.class#member label}
            new TagParser(Kind.INLINE, "linkplain") {
                @Override
                public void parse(int pos) throws ParseException {
                    reference(true);
                    inlineContent();
                }
            },

            // {@literal text}
            new TagParser(Kind.INLINE, "literal", true) {
                @Override
                public void parse(int pos) throws ParseException {
                    inlineText(WhitespaceRetentionPolicy.REMOVE_FIRST_SPACE);
                    nextChar();
                }
            },

            // @param parameter-name description
            new TagParser(Kind.BLOCK, "param") {
                @Override
                public void parse(int pos) throws ParseException {
                    skipWhitespace();

                    boolean typaram = false;
                    if (ch == '<') {
                        typaram = true;
                        nextChar();
                    }

                    identifier();

                    if (typaram) {
                        if (ch != '>')
                            throw new ParseException("dc.gt.expected");
                        nextChar();
                    }

                    skipWhitespace();
                    blockContent();
                }
            },

            // @return description
            new TagParser(Kind.BLOCK, "return") {
                @Override
                public void parse(int pos) {
                    blockContent();
                }
            },

            // @see reference | quoted-string | HTML
            new TagParser(Kind.BLOCK, "see") {
                @Override
                public void parse(int pos) throws ParseException {
                    skipWhitespace();
                    switch (ch) {
                        case '"':
                            quotedString();
                            skipWhitespace();
                            if (ch == '@'
                                    || ch == EOI && bp == buf.length - 1) {
                                return;
                            }
                            break;

                        case '<':
                            blockContent();
                            return;

                        case '@':
                            if (newline)
                                throw new ParseException("dc.no.content");
                            break;

                        case EOI:
                            if (bp == buf.length - 1)
                                throw new ParseException("dc.no.content");
                            break;

                        default:
                            if (isJavaIdentifierStart(ch) || ch == '#') {
                                reference(true);
                                blockContent();
                            }
                    }
                    throw new ParseException("dc.unexpected.content");
                }
            },

            // @serialData data-description
            new TagParser(Kind.BLOCK, "@serialData") {
                @Override
                public void parse(int pos) {
                    blockContent();
                }
            },

            // @serialField field-name field-type description
            new TagParser(Kind.BLOCK, "serialField") {
                @Override
                public void parse(int pos) throws ParseException {
                    skipWhitespace();
                    identifier();
                    skipWhitespace();
                    reference(false);
                    if (isWhitespace(ch)) {
                        skipWhitespace();
                        blockContent();
                    }
                }
            },

            // @serial field-description | include | exclude
            new TagParser(Kind.BLOCK, "serial") {
                @Override
                public void parse(int pos) {
                    blockContent();
                }
            },

            // @since since-text
            new TagParser(Kind.BLOCK, "since") {
                @Override
                public void parse(int pos) {
                    blockContent();
                }
            },

            // @throws class-name description
            new TagParser(Kind.BLOCK, "throws") {
                @Override
                public void parse(int pos) throws ParseException {
                    skipWhitespace();
                    reference(false);
                    blockContent();
                }
            },

            // {@value package.class#field}
            new TagParser(Kind.INLINE, "value") {
                @Override
                public void parse(int pos) throws ParseException {
                    reference(true);
                    skipWhitespace();
                    if (ch == '}') {
                        nextChar();
                        return;
                    }
                    nextChar();
                    throw new ParseException("dc.unexpected.content");
                }
            },

            // @version version-text
            new TagParser(Kind.BLOCK, "version") {
                @Override
                public void parse(int pos) {
                    blockContent();
                }
            },
        };

        tagParsers = new HashMap<>();
        for (TagParser p: parsers)
            tagParsers.put(p.getName(), p);

    }

    private void initEventAttrs() {
        eventAttrs = new HashSet<>(Arrays.asList(
            // See https://www.w3.org/TR/html-markup/global-attributes.html#common.attrs.event-handler
            "onabort",  "onblur",  "oncanplay",  "oncanplaythrough",
            "onchange",  "onclick",  "oncontextmenu",  "ondblclick",
            "ondrag",  "ondragend",  "ondragenter",  "ondragleave",
            "ondragover",  "ondragstart",  "ondrop",  "ondurationchange",
            "onemptied",  "onended",  "onerror",  "onfocus",  "oninput",
            "oninvalid",  "onkeydown",  "onkeypress",  "onkeyup",
            "onload",  "onloadeddata",  "onloadedmetadata",  "onloadstart",
            "onmousedown",  "onmousemove",  "onmouseout",  "onmouseover",
            "onmouseup",  "onmousewheel",  "onpause",  "onplay",
            "onplaying",  "onprogress",  "onratechange",  "onreadystatechange",
            "onreset",  "onscroll",  "onseeked",  "onseeking",
            "onselect",  "onshow",  "onstalled",  "onsubmit",  "onsuspend",
            "ontimeupdate",  "onvolumechange",  "onwaiting",

            // See https://www.w3.org/TR/html4/sgml/dtd.html
            // Most of the attributes that take a %Script are also defined as event handlers
            // in HTML 5. The one exception is onunload.
            // "onchange",  "onclick",   "ondblclick",  "onfocus",
            // "onkeydown",  "onkeypress",  "onkeyup",  "onload",
            // "onmousedown",  "onmousemove",  "onmouseout",  "onmouseover",
            // "onmouseup",  "onreset",  "onselect",  "onsubmit",
            "onunload"
        ));
    }

    private void initURIAttrs() {
        uriAttrs = new HashSet<>(Arrays.asList(
            // See https://www.w3.org/TR/html4/sgml/dtd.html
            //     https://www.w3.org/TR/html5/
            // These are all the attributes that take a %URI or a valid URL potentially surrounded
            // by spaces
            "action",  "cite",  "classid",  "codebase",  "data",
            "datasrc",  "for",  "href",  "longdesc",  "profile",
            "src",  "usemap"
        ));
    }

}
