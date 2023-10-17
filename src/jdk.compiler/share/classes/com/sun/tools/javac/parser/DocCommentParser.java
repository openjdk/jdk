/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.parser;

import java.io.Serial;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.sun.source.doctree.AttributeTree.ValueKind;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.ErroneousTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import com.sun.source.doctree.UnknownInlineTagTree;
import com.sun.tools.javac.parser.Tokens.Comment;
import com.sun.tools.javac.tree.DCTree;
import com.sun.tools.javac.tree.DCTree.DCAttribute;
import com.sun.tools.javac.tree.DCTree.DCDocComment;
import com.sun.tools.javac.tree.DCTree.DCEndPosTree;
import com.sun.tools.javac.tree.DCTree.DCErroneous;
import com.sun.tools.javac.tree.DCTree.DCIdentifier;
import com.sun.tools.javac.tree.DCTree.DCReference;
import com.sun.tools.javac.tree.DCTree.DCText;
import com.sun.tools.javac.tree.DocTreeMaker;
import com.sun.tools.javac.util.DiagnosticSource;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Position;
import com.sun.tools.javac.util.StringUtils;

import static com.sun.tools.javac.util.LayoutCharacters.EOI;

/**
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class DocCommentParser {
    static class ParseException extends Exception {
        @Serial
        private static final long serialVersionUID = 0;
        final int pos;

        ParseException(String key) {
            this(Position.NOPOS, key);
        }
        ParseException(int pos, String key) {
            super(key);
            this.pos = pos;
        }
    }

    private enum Phase {
        /** The initial part of an HTML file up to and including the {@code body} and possible {@code <main>} tag. */
        PREAMBLE,
        /** The initial part of a doc comment, or the rich-text content of a block tag. */
        BODY,
        /** The end of an HTML file, from and including the {@code </main>} or {@code </body>} tag. */
        POSTAMBLE,
        /** The rich-text content of an inline documentation comment tag. */
        INLINE
    }

    private final ParserFactory fac;
    private final JCDiagnostic.Factory diags;
    private final DiagnosticSource diagSource;
    private final Comment comment;
    private final DocTreeMaker m;
    private final Names names;
    private final boolean isHtmlFile;
    private final DocTree.Kind textKind;

    /** The input buffer, index of most recent character read,
     *  index of one past last character in buffer.
     */
    private char[] buf;
    private int bp;
    private int buflen;

    /** The current character.
     */
    private char ch;

    private int textStart = -1;
    private int lastNonWhite = -1;
    private boolean newline = true;

    private final Map<Name, TagParser> tagParsers;

    /**
     * Creates a parser for a documentation comment.
     *
     * @param fac a parser factory, for a doc-tree maker and for reference parsers
     * @param diagSource the source in which the comment was found
     * @param comment the comment
     */
    public DocCommentParser(ParserFactory fac, DiagnosticSource diagSource, Comment comment) {
        this(fac, diagSource, comment, false);
    }

    /**
     * Create a parser for a documentation comment.
     *
     * If the comment is the content of a standalone HTML file, it will be parsed
     * in three parts: a preamble (up to and including the {@code <main>} tag,
     * or {@code <body>} tag if there is no {@code <main>} tag, then the main content
     * of the file, and then finally the end part of the file starting at the
     * end tag matching the tag that ended the preamble.
     *
     * @param fac a parser factory, for a doc-tree maker and for reference parsers
     * @param diagSource the source in which the comment was found
     * @param comment the comment
     * @param isHtmlFile whether the comment is the entire content of an HTML file
     */
    public DocCommentParser(ParserFactory fac, DiagnosticSource diagSource,
                            Comment comment, boolean isHtmlFile) {
        this.fac = fac;
        this.diags = fac.log.diags;
        this.diagSource = diagSource;
        this.comment = comment;
        names = fac.names;
        this.isHtmlFile = isHtmlFile;
        textKind = isHtmlFile ? DocTree.Kind.TEXT : getTextKind(comment);
        m = fac.docTreeMaker;
        tagParsers = createTagParsers();
    }

    private static DocTree.Kind getTextKind(Comment c) {
        return switch (c.getStyle()) {
            case JAVADOC_BLOCK -> DocTree.Kind.TEXT;
            case JAVADOC_LINE -> DocTree.Kind.MARKDOWN;
            default -> throw new IllegalArgumentException(c.getStyle().name());
        };
    }

    public DCDocComment parse() {
        String c = comment.getText();
        buf = new char[c.length() + 1];
        c.getChars(0, c.length(), buf, 0);
        buf[buf.length - 1] = EOI;
        buflen = buf.length - 1;
        bp = -1;
        nextChar();

        List<DCTree> preamble = isHtmlFile ? content(Phase.PREAMBLE) : List.nil();
        List<DCTree> body = content(Phase.BODY);
        List<DCTree> tags = blockTags();
        List<DCTree> postamble = isHtmlFile ? content(Phase.POSTAMBLE) : List.nil();

        int pos = textKind == DocTree.Kind.MARKDOWN  ? 0
                : !preamble.isEmpty() ? preamble.head.pos
                : !body.isEmpty() ? body.head.pos
                : !tags.isEmpty() ? tags.head.pos
                : !postamble.isEmpty() ? postamble.head.pos
                : 0;

        return m.at(pos).newDocCommentTree(comment, body, tags, preamble, postamble);
    }

    void nextChar() {
        ch = buf[bp < buflen ? ++bp : buflen];
        switch (ch) {
            case '\n' -> {
                newline = true;
            }

            case '\r' -> {
                if (bp + 1 < buflen && buf[bp + 1] == '\n') {
                    bp++;
                    ch = '\n';
                }
                newline = true;
            }
        }
    }

    char peekChar() {
        return buf[bp < buflen ? bp + 1 : buflen];
    }

    String peekLine() {
        int p = bp;
        while (p < buflen) {
             switch (buf[p]) {
                 case '\n', '\r' -> {
                     return newString(bp, p);
                 }
                 default -> p++;
             }
        }
        return newString(bp, buflen);
    }

    protected List<DCTree> blockContent() {
        while (ch == ' ' && bp < buflen) {
            nextChar();
        }
        return content(Phase.BODY);
    }

    /**
     * Reads "rich text" content, consisting of text, html and inline tags,
     * according to the given {@code phase}.
     *
     * Inline tags are only recognized in {@code BODY} and {@code INLINE}
     * phases, and not in {@code PREAMBLE} and {@code POSTAMBLE} phases.
     *
     * The end of the content is dependent on the phase:
     *
     * <ul>
     * <li>{@code PREAMBLE}: the appearance of {@code <body>} (or {@code <main>}),
     *      as determined by {@link #isEndPreamble()}
     * <li>{@code BODY}: the beginning of a block tag, or when readung from
     *      an HTML file, the appearance of {@code </main>} (or {@code </body>},
     *       as determined by {@link #isEndBody()}
     * <li>{@code INLINE}: '}', after skipping any matching {@code { }}
     * <li>{@code PREAMBLE}: end of file
     * </ul>
     *
     *
     *
     */
    protected List<DCTree> content(Phase phase) {
        ListBuffer<DCTree> trees = new ListBuffer<>();
        textStart = -1;

        int depth = 1;                  // only used when phase is INLINE
        int pos = bp;                   // only used when phase is INLINE
        LineKind lineKind = textKind == DocTree.Kind.MARKDOWN ? peekLineKind() : null;

        if (DEBUG) System.err.println("starting content " + showPos(bp) + " " + newline);

        loop:
        while (bp < buflen) {
            if (DEBUG) System.err.println("   in content " + showPos(bp) + " " + newline);
            switch (ch) {
                case '\n', '\r' -> {
                    nextChar();
                    if (textKind == DocTree.Kind.MARKDOWN) {
//                        // FIXME?
//                        if (textStart == -1) {
//                            textStart = bp;
//                        }
                        int indent = readIndent();
                        // in the following, the evaluation of INDENTED_CODE_BLOCK is
                        // inductively a sequence of indented lines following any
                        // line that is not OTHER
                        lineKind = (ch == '\n' || ch == '\r') ? LineKind.BLANK
                                : (indent <= 3) ? peekLineKind()
                                : lineKind != LineKind.OTHER ? LineKind.INDENTED_CODE_BLOCK
                                : LineKind.OTHER;
                        if (lineKind == LineKind.INDENTED_CODE_BLOCK) {
                            skipLine();
                        }
                    }
                }

                case ' ', '\t' -> {
                    if (textKind == DocTree.Kind.MARKDOWN && textStart == -1) {
                        textStart = bp;
                    }
                    nextChar();
                }


                case '&' -> {
                    switch (textKind) {
                        case MARKDOWN -> defaultContentCharacter();
                        case TEXT -> entity(trees);
                        default -> throw unknownTextKind(textKind);
                    }
                }

                case '<' -> {
                    switch (textKind) {
                        case MARKDOWN -> {
                            defaultContentCharacter();
                        }
                        case TEXT -> {
                            newline = false;
                            if (isHtmlFile) {
                                switch (phase) {
                                    case PREAMBLE -> {
                                        if (isEndPreamble()) {
                                            trees.add(html());
                                            if (textStart == -1) {
                                                textStart = bp;
                                                lastNonWhite = -1;
                                            }
                                            // mark this as the start, for processing purposes
                                            newline = true;
                                            break loop;
                                        }
                                    }
                                    case BODY -> {
                                        if (isEndBody()) {
                                            addPendingText(trees, lastNonWhite);
                                            break loop;
                                        }
                                    }
                                    default -> { }
                                }
                            }
                            addPendingText(trees, bp - 1);
                            trees.add(html());

                            if (phase == Phase.PREAMBLE || phase == Phase.POSTAMBLE) {
                                break; // Ignore newlines after html tags, in the meta content
                            }
                            if (textStart == -1) {
                                textStart = bp;
                                lastNonWhite = -1;
                            }
                        }
                        default -> throw unknownTextKind(textKind);
                    }
                }

                case '{' -> {
                    switch (phase) {
                        case PREAMBLE, POSTAMBLE -> defaultContentCharacter();
                        case BODY -> inlineTag(trees);
                        case INLINE -> {
                            if (!inlineTag(trees)) {
                                depth++;
                            }
                        }
                    }
                }

                case '}' -> {
                    if (phase == Phase.INLINE) {
                        newline = false;
                        if (--depth == 0) {
                            addPendingText(trees, bp - 1);
                            nextChar();
                            return trees.toList();
                        }
                        nextChar();
                    } else {
                        defaultContentCharacter();
                    }
                }

                case '@' -> {
                    if (DEBUG) System.err.println("  content @");
                    // check for context-sensitive escape sequences:
                    //   newline whitespace @@
                    //   newline whitespace @*
                    //   *@/
                    if (newline) {
                        if (DEBUG) System.err.println("  content @ newline");
                        char peek = peekChar();
                        if (peek == '@' || peek == '*') {
                            if (DEBUG) System.err.println("  content @ newline escape1 " + peek);
                            addPendingText(trees, bp - 1);
                            nextChar();
                            trees.add(m.at(bp - 1).newEscapeTree(ch));
                            newline = false;
                            nextChar();
                            textStart = bp;
                            break;
                        } else if (phase == Phase.BODY) {
                            if (DEBUG) System.err.println("  content @ newline BODY will break loop");
                            addPendingText(trees, lastNonWhite);
                            break loop;
                        }
                    } else if (textStart != -1 && buf[bp - 1] == '*' && peekChar() == '/') {
                        if (DEBUG) System.err.println("  content @ newline escape2");
                        addPendingText(trees, bp - 1);
                        nextChar();
                        trees.add(m.at(bp - 1).newEscapeTree('/'));
                        newline = false;
                        nextChar();
                        textStart = bp;
                        break;
                    }
                    if (DEBUG) System.err.println("  content @ final default");
                    defaultContentCharacter();
                }

                case '`', '~' -> {
                    switch (textKind) {
                        case MARKDOWN -> {
                            newline = false;
                            if (textStart == -1) {
                                textStart = bp;
                            }
                            lastNonWhite = bp;
                            if (ch == '`' || ch == '~' && lineKind == LineKind.CODE_FENCE) {
                                int end = skipMarkdownCode(ch, count(ch), lineKind);
                                if (end == -1) {
                                    bp = lastNonWhite;
                                    nextChar();
                                }
                            } else {
                                nextChar();
                            }
                        }
                        case TEXT -> {
                            defaultContentCharacter();
                        }
                    }
                }

                default -> {
                    defaultContentCharacter();
                }
            }
        }

        if (lastNonWhite != -1)
            addPendingText(trees, lastNonWhite);

        return (phase == Phase.INLINE)
                ? List.of(erroneous("dc.unterminated.inline.tag", pos))
                : trees.toList();
    }

    void defaultContentCharacter() {
        newline = false;
        if (textStart == -1)
            textStart = bp;
        lastNonWhite = bp;
        nextChar();
    }

    private IllegalStateException unknownTextKind(DocTree.Kind textKind) {
        return new IllegalStateException(textKind.toString());
    }

    /**
     * Read a series of block tags, including their content.
     * Standard tags parse their content appropriately.
     * Non-standard tags are represented by {@link UnknownBlockTagTree}.
     */
    protected List<DCTree> blockTags() {
        ListBuffer<DCTree> tags = new ListBuffer<>();
        while (bp < buflen && ch == '@')
            tags.add(blockTag());
        return tags.toList();
    }

    /**
     * Read a single block tag, including its content.
     * Standard tags parse their content appropriately.
     * Non-standard tags are represented by {@link UnknownBlockTagTree}.
     */
    protected DCTree blockTag() {
        newline = false;
        int p = bp;
        try {
            nextChar();
            if (isIdentifierStart(ch)) {
                Name name = readTagName();
                TagParser tp = tagParsers.get(name);
                if (tp == null) {
                    List<DCTree> content = blockContent();
                    return m.at(p).newUnknownBlockTagTree(name, content);
                } else {
                    if (DEBUG) System.err.println("blockTag " + tp + " " + showPos(bp) + " " + textStart);
                    if (tp.allowsBlock()) {
                        return tp.parse(p, TagParser.Kind.BLOCK);
                    } else {
                        return erroneous("dc.bad.inline.tag", p);
                    }
                }
            }
            int prefPos = bp;
            blockContent();

            return erroneous("dc.no.tag.name", p, prefPos);
        } catch (ParseException e) {
            blockContent();
            return erroneous(e.getMessage(), p, e.pos);
        }
    }

    private static final boolean DEBUG = false;

    //DEBUG
    String showPos(int p) {
        var sb = new StringBuilder();
        sb.append("[").append(p).append("] ");
        if (p >= 0) {
            for (int i = Math.max(p - 10, 0); i < Math.min(p + 10, buflen); i++) {
                if (i == p) sb.append("[");
                var c = buf[i];
                sb.append(switch (c) {
                    case '\n' -> '|';
                    case ' ' -> '_';
                    default -> c;
                });
                if (i == p) sb.append("]");
            }
        }
        return sb.toString();
    }

    /**
     * Reads a possible inline tag, after finding an opening brace <code>{</code> character.
     *
     * If the next character is {@code @}, an opening tag is read and added to the
     * given {@code list}, and the result is {@code true}.
     *
     * Otherwise, the {@code list} is updated with the characters that have been read,
     * and the result is {@code false}. The result also indicates that a single
     * opening brace was read, and that a corresponding closing brace should eventually
     * be read.
     *
     * @param list the list of trees being accumulated
     * @return {@code true} if an inline tag was read, and {@code false} otherwise
     */
    protected boolean inlineTag(ListBuffer<DCTree> list) {
        newline = false;
        nextChar();
        if (ch == '@') {
            // check for context-sensitive escape-sequence
            //   {@@
            if (peekChar() == '@') {
                if (textStart == -1) {
                    textStart = bp - 1;
                }
                addPendingText(list, bp - 1);
                nextChar();
                list.add(m.at(bp - 1).newEscapeTree('@'));
                nextChar();
                textStart = -1;
                lastNonWhite = bp;
            } else {
                addPendingText(list, bp - 2);
                list.add(inlineTag());
                textStart = bp;
                lastNonWhite = -1;
                return true;
            }
        } else {
            if (textStart == -1)
                textStart = bp - 1;
            lastNonWhite = bp;
        }
        return false;
    }

    /**
     * Read a single inline tag, including its content.
     * Standard tags parse their content appropriately.
     * Non-standard tags are represented by {@link UnknownInlineTagTree}.
     * Malformed tags may be returned as {@link ErroneousTree}.
     */
    protected DCTree inlineTag() {
        int p = bp - 1;
        try {
            nextChar();
            if (!isIdentifierStart(ch)) {
                return erroneous("dc.no.tag.name", p, bp);
            }
            Name name = readTagName();
            TagParser tp = tagParsers.get(name);
            if (tp == null) {
                skipWhitespace();
                DCTree text = inlineText(WhitespaceRetentionPolicy.REMOVE_ALL);
                nextChar();
                return m.at(p).newUnknownInlineTagTree(name, List.of(text)).setEndPos(bp);
            } else {
                if (!tp.retainWhiteSpace) {
                    skipWhitespace();
                }
                if (tp.allowsInline()) {
                    DCEndPosTree<?> tree = (DCEndPosTree<?>) tp.parse(p, TagParser.Kind.INLINE);
                    return tree.setEndPos(bp);
                } else { // handle block tags (for example, @see) in inline content
                    DCTree text = inlineText(WhitespaceRetentionPolicy.REMOVE_ALL); // skip content
                    nextChar();
                    return m.at(p).newUnknownInlineTagTree(name, List.of(text)).setEndPos(bp);
                }
            }
        } catch (ParseException e) {
            return erroneous(e.getMessage(), p, e.pos);
        }
    }

    private enum WhitespaceRetentionPolicy {
        RETAIN_ALL,
        REMOVE_FIRST_SPACE,
        REMOVE_ALL
    }

    /**
     * Read plain text content of an inline tag.
     * Matching pairs of '{' '}' are skipped; the text is terminated by the first
     * unmatched '}'. It is an error if the beginning of the next tag is detected.
     */
    private DCText inlineText(WhitespaceRetentionPolicy whitespacePolicy) throws ParseException {
        switch (whitespacePolicy) {
            case REMOVE_ALL -> {
                skipWhitespace();
            }

            case REMOVE_FIRST_SPACE -> {
                if (ch == ' ')
                    nextChar();
            }

            case RETAIN_ALL -> { }
        }
        int pos = bp;
        int depth = 1;

        while (bp < buflen) {
            switch (ch) {
                case '\n', '\r', '\f', ' ', '\t' -> {
                }

                case '{' -> {
                    newline = false;
                    lastNonWhite = bp;
                    depth++;
                }

                case '}' -> {
                    if (--depth == 0) {
                        return m.at(pos).newTextTree(newString(pos, bp));
                    }
                    newline = false;
                    lastNonWhite = bp;
                }

                default -> {
                    newline = false;
                    lastNonWhite = bp;
                }
            }
            nextChar();
        }
        throw new ParseException("dc.unterminated.inline.tag");
    }

    /**
     * Read Java class name, possibly followed by member
     * Matching pairs of {@literal < >} are skipped. The text is terminated by the first
     * unmatched '}'. It is an error if the beginning of the next tag is detected.
     */
    // TODO: improve quality of parse to forbid bad constructions.
    protected DCReference reference(ReferenceParser.Mode mode) throws ParseException {
        int pos = bp;
        int depth = 0;

        // scan to find the end of the signature, by looking for the first
        // whitespace not enclosed in () or <>, or the end of the tag
        loop:
        while (bp < buflen) {
            switch (ch) {

                case '\n', '\r', '\f', ' ', '\t' -> {
                    if (depth == 0)
                        break loop;
                }

                case '(', '<' -> {
                    newline = false;
                    depth++;
                }

                case ')', '>' -> {
                    newline = false;
                    --depth;
                }

                case '}' -> {
                    if (bp == pos)
                        return null;
                    newline = false;
                    break loop;
                }

                case '@' -> {
                    if (newline)
                        break loop;
                }

                default -> {
                    newline = false;
                }

            }
            nextChar();
        }

        // depth < 0 will be caught and reported by ReferenceParser#parse
        if (depth > 0)
            throw new ParseException("dc.unterminated.signature");

        String sig = newString(pos, bp);

        try {
            ReferenceParser.Reference ref = new ReferenceParser(fac).parse(sig, mode);
            return m.at(pos).newReferenceTree(sig, ref).setEndPos(bp);
        } catch (ReferenceParser.ParseException pe) {
            throw new ParseException(pos + pe.pos, pe.getMessage());
        }

    }

    /**
     * Reads a Java identifier.
     */
    protected DCIdentifier identifier() throws ParseException {
        skipWhitespace();
        int pos = bp;

        if (isJavaIdentifierStart(ch)) {
            Name name = readJavaIdentifier();
            return m.at(pos).newIdentifierTree(name);
        }

        throw new ParseException("dc.identifier.expected");
    }

    /**
     * Reads a quoted string.
     * It is an error if the beginning of the next tag is detected.
     */
    protected DCText quotedString() {
        newline = false;
        int pos = bp;
        nextChar();

        loop:
        while (bp < buflen) {
            switch (ch) {
                case '\n', '\r', '\f', ' ', '\t' -> { }

                case '"' -> {
                    nextChar();
                    // trim trailing white-space?
                    return m.at(pos).newTextTree(newString(pos, bp));
                }

                case '@' -> {
                    if (newline)
                        break loop;
                }
            }
            nextChar();
        }
        return null;
    }

    /**
     * Reads a term (that is, one word).
     * It is an error if the beginning of the next tag is detected.
     */
    protected DCText inlineWord() {
        int pos = bp;
        int depth = 0;
        loop:
        while (bp < buflen) {
            switch (ch) {
                case '\n', '\r', '\f', ' ', '\t' -> {
                    return m.at(pos).newTextTree(newString(pos, bp));
                }

                case '@' -> {
                    if (newline)
                        break loop;
                }

                case '{' -> {
                    depth++;
                }

                case '}' -> {
                    if (depth == 0)
                        return m.at(pos).newTextTree(newString(pos, bp));
                    depth--;
                }
            }
            newline = false;
            nextChar();
        }
        return null;
    }

    /**
     * Reads general text content of an inline tag, including HTML entities and elements.
     * Matching pairs of '{' '}' are skipped; the text is terminated by the first
     * unmatched '}'. It is an error if the beginning of the next tag is detected.
     */
    private List<DCTree> inlineContent() {
        skipWhitespace();
        return content(Phase.INLINE);
    }

    protected void entity(ListBuffer<DCTree> list) {
        newline = false;
        addPendingText(list, bp - 1);
        list.add(entity());
        if (textStart == -1) {
            textStart = bp;
            lastNonWhite = -1;
        }
    }

    /**
     * Reads an HTML entity.
     * {@literal &identifier; } or {@literal &#digits; } or {@literal &#xhex-digits; }
     */
    protected DCTree entity() {
        int p = bp;
        nextChar();
        Name name = null;
        if (ch == '#') {
            int namep = bp;
            nextChar();
            if (isDecimalDigit(ch)) {
                nextChar();
                while (bp < buflen && isDecimalDigit(ch))
                    nextChar();
                name = names.fromChars(buf, namep, bp - namep);
            } else if (ch == 'x' || ch == 'X') {
                nextChar();
                if (isHexDigit(ch)) {
                    nextChar();
                    while (bp < buflen && isHexDigit(ch))
                        nextChar();
                    name = names.fromChars(buf, namep, bp - namep);
                }
            }
        } else if (isIdentifierStart(ch)) {
            name = readIdentifier();
        }

        if (name == null)
            return erroneous("dc.bad.entity", p);
        else {
            if (ch != ';')
                return erroneous("dc.missing.semicolon", p);
            nextChar();
            return m.at(p).newEntityTree(name);
        }
    }

    /**
     * Returns whether this is the end of the preamble of an HTML file.
     * The preamble ends with start of {@code body} element followed by
     * possible whitespace and the start of a {@code main} element.
     *
     * @return whether this is the end of the preamble
     */
    boolean isEndPreamble() {
        final int savedpos = bp;
        try {
            if (ch == '<')
                nextChar();

            if (isIdentifierStart(ch)) {
                String name = StringUtils.toLowerCase(readIdentifier().toString());
                switch (name) {
                    case "body" -> {
                        // Check if also followed by <main>
                        // 1. skip rest of <body>
                        while (bp < buflen && ch != '>') {
                            nextChar();
                        }
                        if (ch == '>') {
                            nextChar();
                        }
                        // 2. skip any whitespace
                        while (bp < buflen && isWhitespace(ch)) {
                            nextChar();
                        }
                        // 3. check if looking at "<main..."
                        if (ch == '<') {
                            nextChar();
                            if (isIdentifierStart(ch)) {
                                name = StringUtils.toLowerCase(readIdentifier().toString());
                                if (name.equals("main")) {
                                    return false;
                                }
                            }
                        }
                        // if <body> is _not_ followed by <main> then this is the
                        // end of the preamble
                        return true;
                    }

                    case "main" -> {
                        // <main> is unconditionally the end of the preamble
                        return true;
                    }
                }
            }
            return false;
        } finally {
            bp = savedpos;
            ch = buf[bp];
        }
    }

    /**
     * Returns whether this is the end of the main body of the content in a standalone
     * HTML file.
     * The content ends with the closing tag for a {@code main} or {@code body} element.
     *
     * @return whether this is the end of the main body of the content
     */
    boolean isEndBody() {
        final int savedpos = bp;
        try {
            if (ch == '<')
                nextChar();

            if (ch == '/') {
                nextChar();
                if (isIdentifierStart(ch)) {
                    String name = StringUtils.toLowerCase(readIdentifier().toString());
                    switch (name) {
                        case "body", "main" -> {
                            return true;
                        }
                    }
                }
            }

            return false;
        } finally {
            bp = savedpos;
            ch = buf[bp];
        }

    }

    boolean peek(String s) {
        final int savedpos = bp;
        try {
            if (ch == '<')
                nextChar();

            if (ch == '/') {
                if (s.charAt(0) != ch) {
                    return false;
                } else {
                    s = s.substring(1);
                    nextChar();
                }
            }

            if (isIdentifierStart(ch)) {
                Name name = readIdentifier();
                return StringUtils.toLowerCase(name.toString()).equals(s);
            }
            return false;
        } finally {
            bp = savedpos;
            ch = buf[bp];
        }
    }

    /**
     * Reads an HTML construct, beginning with {@code <}.
     *
     * <ul>
     * <li>start element: {@code <identifier attrs> }
     * <li>end element: {@code </identifier> }
     * <li>comment: {@code <!-- ... -->}
     * <li>doctype: {@code <!doctype ... >}
     * <li>cdata: {@code <![CDATA[ ... ]]>}
     * </ul>
     *  or
     */
    private DCTree html() {
        int p = bp;
        nextChar();
        if (isIdentifierStart(ch)) {
            Name name = readIdentifier();
            List<DCTree> attrs = htmlAttrs();
            if (attrs != null) {
                boolean selfClosing = false;
                if (ch == '/') {
                    nextChar();
                    selfClosing = true;
                }
                if (ch == '>') {
                    nextChar();
                    return m.at(p).newStartElementTree(name, attrs, selfClosing).setEndPos(bp);
                }
            }
        } else if (ch == '/') {
            nextChar();
            if (isIdentifierStart(ch)) {
                Name name = readIdentifier();
                skipWhitespace();
                if (ch == '>') {
                    nextChar();
                    return m.at(p).newEndElementTree(name).setEndPos(bp);
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
                        while (bp < buflen && ch == '-') {
                            dash++;
                            nextChar();
                        }
                        // Strictly speaking, a comment should not contain "--"
                        // so dash > 2 is an error, dash == 2 implies ch == '>'
                        // See http://www.w3.org/TR/html-markup/syntax.html#syntax-comments
                        // for more details.
                        if (dash >= 2 && ch == '>') {
                            nextChar();
                            return m.at(p).newCommentTree(newString(p, bp));
                        }

                        nextChar();
                    }
                }
            } else if (isIdentifierStart(ch) && peek("doctype")) {
                readIdentifier();
                nextChar();
                skipWhitespace();
                int d = bp;
                while (bp < buflen) {
                    if (ch == '>') {
                        int mark = bp;
                        nextChar();
                        return m.at(d).newDocTypeTree(newString(d, mark));
                    }
                    nextChar();
                }
            } else {
                String CDATA = "[CDATA[";  // full prefix is <![CDATA[
                for (int i = 0; i < CDATA.length(); i++) {
                    if (ch == CDATA.charAt(i)) {
                        nextChar();
                    } else {
                        return erroneous("dc.invalid.html", p);
                    }
                }
                // suffix is ]]>
                while (bp < buflen) {
                    if (ch == ']') {
                        int n = 0;
                        while (bp < buflen && ch == ']') {
                            n++;
                            nextChar();
                        }
                        if (n >= 2 && ch == '>') {
                            nextChar();
                            return m.at(p).newTextTree(newString(p, bp));
                        }
                    } else {
                        nextChar();
                    }
                }
                return erroneous("dc.invalid.html", p);
            }
        }

        bp = p + 1;
        ch = buf[bp];
        return erroneous("dc.malformed.html", p);
    }

    /**
     * Read a series of HTML attributes, terminated by {@literal > }.
     * Each attribute is of the form {@literal identifier[=value] }.
     * "value" may be unquoted, single-quoted, or double-quoted.
     */
    protected List<DCTree> htmlAttrs() {
        ListBuffer<DCTree> attrs = new ListBuffer<>();
        skipWhitespace();

        loop:
        while (bp < buflen && isIdentifierStart(ch)) {
            int namePos = bp;
            Name name = readAttributeName();
            skipWhitespace();
            List<DCTree> value = null;
            ValueKind vkind = ValueKind.EMPTY;
            if (ch == '=') {
                ListBuffer<DCTree> v = new ListBuffer<>();
                nextChar();
                skipWhitespace();
                if (ch == '\'' || ch == '"') {
                    newline = false;
                    vkind = (ch == '\'') ? ValueKind.SINGLE : ValueKind.DOUBLE;
                    char quote = ch;
                    nextChar();
                    textStart = bp;
                    while (bp < buflen && ch != quote) {
                        if (newline && ch == '@') {
                            attrs.add(erroneous("dc.unterminated.string", namePos));
                            // No point trying to read more.
                            // In fact, all attrs get discarded by the caller
                            // and superseded by a malformed.html node because
                            // the html tag itself is not terminated correctly.
                            break loop;
                        }
                        attrValueChar(v);
                    }
                    addPendingText(v, bp - 1, DocTree.Kind.TEXT);
                    nextChar();
                } else {
                    vkind = ValueKind.UNQUOTED;
                    textStart = bp;
                    while (bp < buflen && !isUnquotedAttrValueTerminator(ch)) {
                        attrValueChar(v);
                    }
                    addPendingText(v, bp - 1, DocTree.Kind.TEXT);
                }
                skipWhitespace();
                value = v.toList();
            }
            DCAttribute attr = m.at(namePos).newAttributeTree(name, vkind, value);
            attrs.add(attr);
        }

        return attrs.toList();
    }

    protected void attrValueChar(ListBuffer<DCTree> list) {
        switch (ch) {
            case '&' -> entity(list);
            case '{' -> inlineTag(list);
            default  -> nextChar();
        }
    }


    protected void addPendingText(ListBuffer<DCTree> list, int textEnd) {
        addPendingText(list, textEnd, textKind);
    }

    protected void addPendingText(ListBuffer<DCTree> list, int textEnd, DocTree.Kind kind) {
        if (textStart != -1) {
            if (textStart <= textEnd) {
                switch (kind) {
                    case TEXT ->
                            list.add(m.at(textStart).newTextTree(newString(textStart, textEnd + 1)));
                    case MARKDOWN ->
                            list.add(m.at(textStart).newRawTextTree(DocTree.Kind.MARKDOWN, newString(textStart, textEnd + 1)));
                    default ->
                        throw new IllegalArgumentException(kind.toString());
                }
            }
            textStart = -1;
        }
    }

    /**
     * Creates an {@code ErroneousTree} node, for a range of text starting at a given position,
     * ending at the last non-whitespace character before the current position,
     * and with the preferred position set to the last character within that range.
     *
     * @param code the resource key for the error message
     * @param pos  the starting position
     *
     * @return the {@code ErroneousTree} node
     */
    protected DCErroneous erroneous(String code, int pos) {
        return erroneous(code, pos, Position.NOPOS);
    }

    /**
     * Creates an {@code ErroneousTree} node, for a range of text starting at a given position,
     * ending at the last non-whitespace character before the current position,
     * and with a given preferred position.
     *
     * @param code the resource key for the error message
     * @param pos  the starting position
     * @param pref the preferred position for the node, or {@code NOPOS} to use the default value
     *             as the last character of the range
     *
     * @return the {@code ErroneousTree} node
     */
    protected DCErroneous erroneous(String code, int pos, int pref) {
        int i = bp - 1;
        loop:
        while (i > pos) {
            switch (buf[i]) {
                case '\f', '\n', '\r' -> {
                    newline = true;
                }

                case '\t', ' ' -> { }

                default -> {
                    break loop;
                }
            }
            i--;
        }
        if (pref == Position.NOPOS) {
            pref = i;
        }
        int end = i + 1;
        textStart = -1;
        JCDiagnostic.DiagnosticPosition dp = DCTree.createDiagnosticPosition(comment, pos, pref, end);
        JCDiagnostic diag = diags.error(null, diagSource, dp, code);
        return m.at(pos).newErroneousTree(newString(pos, end), diag).setPrefPos(pref);
    }

    protected boolean isIdentifierStart(char ch) {
        return Character.isUnicodeIdentifierStart(ch);
    }

    protected Name readIdentifier() {
        int start = bp;
        nextChar();
        while (bp < buflen && Character.isUnicodeIdentifierPart(ch))
            nextChar();
        return names.fromChars(buf, start, bp - start);
    }

    protected Name readAttributeName() {
        int start = bp;
        nextChar();
        while (bp < buflen && (Character.isUnicodeIdentifierPart(ch) || ch == '-'))
            nextChar();
        return names.fromChars(buf, start, bp - start);
    }

    protected Name readTagName() {
        int start = bp;
        nextChar();
        while (bp < buflen
                && (Character.isUnicodeIdentifierPart(ch) || ch == '.'
                || ch == '-' || ch == ':')) {
            nextChar();
        }
        return names.fromChars(buf, start, bp - start);
    }

    protected boolean isJavaIdentifierStart(char ch) {
        return Character.isJavaIdentifierStart(ch);
    }

    protected Name readJavaIdentifier() {
        int start = bp;
        nextChar();
        while (bp < buflen && Character.isJavaIdentifierPart(ch))
            nextChar();
        return names.fromChars(buf, start, bp - start);
    }

    protected Name readSystemPropertyName() {
        int pos = bp;
        nextChar();
        while (bp < buflen && Character.isUnicodeIdentifierPart(ch) || ch == '.')
            nextChar();
        return names.fromChars(buf, pos, bp - pos);
    }

    protected int readIndent() {
        int indent = 0;
        while (bp < buflen) {
            switch (ch) {
                case ' ' -> indent++;
                case '\t' -> indent = 4;
                default -> {
                    return indent;
                }
            }
            nextChar();
        }
        return indent;
    }

    int count(char c) {
        int n = 1;
        nextChar();
        while (bp < buflen && ch == c) {
            n++;
            nextChar();
        }
        return n;
    }

    void skipLine() {
        while (bp < buflen) {
            if (ch == '\n' || ch == '\r') {
                return;
            }
            nextChar();
        }
    }

    int skipMarkdownCode(char term, int count, LineKind initialLineKind) {
        LineKind lineKind = null;
        while (bp < buflen) {
            switch (ch) {
                case '\n', '\r' -> {
                    nextChar();
                    int indent = readIndent();
                    lineKind = (ch == '\n' || ch == '\r') ? LineKind.BLANK
                            : (indent <= 3) ? peekLineKind()
                            : LineKind.OTHER;
                    switch (initialLineKind) {
                        case CODE_FENCE -> {
                            if (lineKind == LineKind.CODE_FENCE && ch == term && count(ch) == count) {
                                return bp;
                            }
                        }

                        case OTHER -> {
                            if (lineKind != LineKind.OTHER) {
                                return -1;
                            }
                        }

                        default -> {
                            return -1;
                        }

                    }

                }

                default -> {
                    if (ch == term && initialLineKind != LineKind.CODE_FENCE ) {
                        if (count(ch) == count) {
                            return bp;
                        }
                    }
                    nextChar();
                }

            }
        }
        // found end of input
        return -1;
    }

    enum LineKind {
        BLANK(Pattern.compile("[ \t]*")),

        /**
         * ATX header: starts with 1 to 6 # characters, followed by space or end of line.
         * @see <a href="https://spec.commonmark.org/0.30/#atx-headings">ATX Headings</a>
         */
        ATX_HEADER(Pattern.compile("#{1,6}( .*|$)")),

        /** Setext header: underline is sequence of = or - followed by optional spaces and tabs.
         *  @see <a href="https://spec.commonmark.org/0.30/#setext-headings">Setext Headings</a>
         */
        SETEXT_UNDERLINE(Pattern.compile("[=-]+[ \t]*")),

        /**
         * Thematic break: a line of + - _ interspersed with optional spaces and tabs
         * @see <a href="https://spec.commonmark.org/0.30/#thematic-breaks">Thematic Break</a>
         */
        THEMATIC_BREAK(Pattern.compile("((\\+[ \t]*){3,})|((-[ \t]*){3,})|((_[ \t]*){3,})")),

        /**
         * Code fence: 3 or more back ticks or tildes; back tick fence cannot have back ticks
         * in the info string.
         * Note potential conflict with strikeout for similar reasons if strikeout is supported.
         * @see <a href="https://spec.commonmark.org/0.30/#code-fence">Code Fence</a>
         */
        CODE_FENCE(Pattern.compile("(`{3,}[^`]*)|(~{3,}.*)")),

        /**
         * Indented code blocks are defined by preceding lines and indentation,
         * not by any line-specific pattern.
         * @see <a href="https://spec.commonmark.org/0.30/#indented-code-block">Indented Code Block</a>
         */
        INDENTED_CODE_BLOCK(null),

        /**
         * Everything else...
         */
        OTHER(Pattern.compile(".*"));

        LineKind(Pattern p) {
            this.pattern = p;
        }

        final Pattern pattern;
    }

    LineKind peekLineKind() {
        switch (ch) {
            case '#', '=', '-', '+', '_', '`', '~' -> {
                String line = peekLine();
                for (LineKind lk : LineKind.values()) {
                    if (lk.pattern != null) {
                        if (lk.pattern.matcher(line).matches()) {
                            return lk;
                        }
                    }
                }
            }
        }
        return LineKind.OTHER;
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
        return switch (ch) {
            case '\f', '\n', '\r', '\t', ' ',
                    '"', '\'', '`', '=', '<', '>' -> true;
            default -> false;
        };
    }

    protected boolean isWhitespace(char ch) {
        return Character.isWhitespace(ch);
    }

    protected boolean isHorizontalWhitespace(char ch) {
        // This parser treats `\f` as a line break (see `nextChar`).
        // To be consistent with that behaviour, this method does the same.
        // (see JDK-8273809)
        return ch == ' ' || ch == '\t';
    }

    protected void skipWhitespace() {
        while (bp < buflen && isWhitespace(ch)) {
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

    private abstract static class TagParser {
        enum Kind { INLINE, BLOCK, EITHER }

        final Kind kind;
        final DCTree.Kind treeKind;
        final boolean retainWhiteSpace;

        TagParser(Kind k, DCTree.Kind tk) {
            kind = k;
            treeKind = tk;
            retainWhiteSpace = false;
        }

        TagParser(Kind k, DCTree.Kind tk, boolean retainWhiteSpace) {
            kind = k;
            treeKind = tk;
            this.retainWhiteSpace = retainWhiteSpace;
        }

        boolean allowsBlock() {
            return kind != Kind.INLINE;
        }

        boolean allowsInline() {
            return kind != Kind.BLOCK;
        }

        DCTree.Kind getTreeKind() {
            return treeKind;
        }

        DCTree parse(int pos, Kind kind) throws ParseException {
            if (kind != this.kind && this.kind != Kind.EITHER) {
                throw new IllegalArgumentException(kind.toString());
            }
            return parse(pos);
        }

        DCTree parse(int pos) throws ParseException {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * @see <a href="https://docs.oracle.com/en/java/javase/15/docs/specs/javadoc/doc-comment-spec.html">JavaDoc Tags</a>
     */
    private Map<Name, TagParser> createTagParsers() {
        TagParser[] parsers = {
            // @author name-text
            new TagParser(TagParser.Kind.BLOCK, DCTree.Kind.AUTHOR) {
                @Override
                public DCTree parse(int pos) {
                    List<DCTree> name = blockContent();
                    return m.at(pos).newAuthorTree(name);
                }
            },

            // {@code text}
            new TagParser(TagParser.Kind.INLINE, DCTree.Kind.CODE, true) {
                @Override
                public DCTree parse(int pos) throws ParseException {
                    DCText text = inlineText(WhitespaceRetentionPolicy.REMOVE_FIRST_SPACE);
                    nextChar();
                    return m.at(pos).newCodeTree(text);
                }
            },

            // @deprecated deprecated-text
            new TagParser(TagParser.Kind.BLOCK, DCTree.Kind.DEPRECATED) {
                @Override
                public DCTree parse(int pos) {
                    List<DCTree> reason = blockContent();
                    return m.at(pos).newDeprecatedTree(reason);
                }
            },

            // {@docRoot}
            new TagParser(TagParser.Kind.INLINE, DCTree.Kind.DOC_ROOT) {
                @Override
                public DCTree parse(int pos) throws ParseException {
                    if (ch == '}') {
                        nextChar();
                        return m.at(pos).newDocRootTree();
                    }
                    final int savedPos = bp;
                    inlineText(WhitespaceRetentionPolicy.REMOVE_ALL); // skip unexpected content
                    nextChar();
                    throw new ParseException(savedPos, "dc.unexpected.content");
                }
            },

            // @exception class-name description
            new TagParser(TagParser.Kind.BLOCK, DCTree.Kind.EXCEPTION) {
                @Override
                public DCTree parse(int pos) throws ParseException {
                    skipWhitespace();
                    DCReference ref = reference(ReferenceParser.Mode.MEMBER_DISALLOWED);
                    List<DCTree> description = blockContent();
                    return m.at(pos).newExceptionTree(ref, description);
                }
            },

            // @hidden hidden-text
            new TagParser(TagParser.Kind.BLOCK, DCTree.Kind.HIDDEN) {
                @Override
                public DCTree parse(int pos) {
                    List<DCTree> reason = blockContent();
                    return m.at(pos).newHiddenTree(reason);
                }
            },

            // {@index search-term options-description}
            new TagParser(TagParser.Kind.INLINE, DCTree.Kind.INDEX) {
                @Override
                public DCTree parse(int pos) throws ParseException {
                    skipWhitespace();
                    if (ch == '}') {
                        throw new ParseException("dc.no.content");
                    }
                    DCText term = ch == '"' ? quotedString() : inlineWord();
                    if (term == null) {
                        throw new ParseException("dc.no.content");
                    }
                    skipWhitespace();
                    List<DCTree> description = List.nil();
                    if (ch != '}') {
                        description = inlineContent();
                    } else {
                        nextChar();
                    }
                    return m.at(pos).newIndexTree(term, description);
                }
            },

            // {@inheritDoc class-name}
            new TagParser(TagParser.Kind.INLINE, DCTree.Kind.INHERIT_DOC) {
                @Override
                public DCTree parse(int pos) throws ParseException {
                    DCReference ref = reference(ReferenceParser.Mode.MEMBER_DISALLOWED);
                    skipWhitespace();
                    if (ch == '}') {
                        nextChar();
                        // for backward compatibility, use the original legacy
                        // method if no ref is given
                        if (ref == null) {
                            return m.at(pos).newInheritDocTree();
                        } else {
                            return m.at(pos).newInheritDocTree(ref);
                        }
                    }
                    final int errorPos = bp;
                    inlineText(WhitespaceRetentionPolicy.REMOVE_ALL); // skip unexpected content
                    nextChar();
                    throw new ParseException(errorPos, "dc.unexpected.content");
                }
            },

            // {@link package.class#member label}
            new TagParser(TagParser.Kind.INLINE, DCTree.Kind.LINK) {
                @Override
                public DCTree parse(int pos) throws ParseException {
                    DCReference ref = reference(ReferenceParser.Mode.MEMBER_OPTIONAL);
                    List<DCTree> label = inlineContent();
                    return m.at(pos).newLinkTree(ref, label);
                }
            },

            // {@linkplain package.class#member label}
            new TagParser(TagParser.Kind.INLINE, DCTree.Kind.LINK_PLAIN) {
                @Override
                public DCTree parse(int pos) throws ParseException {
                    DCReference ref = reference(ReferenceParser.Mode.MEMBER_OPTIONAL);
                    List<DCTree> label = inlineContent();
                    return m.at(pos).newLinkPlainTree(ref, label);
                }
            },

            // {@literal text}
            new TagParser(TagParser.Kind.INLINE, DCTree.Kind.LITERAL, true) {
                @Override
                public DCTree parse(int pos) throws ParseException {
                    DCText text = inlineText(WhitespaceRetentionPolicy.REMOVE_FIRST_SPACE);
                    nextChar();
                    return m.at(pos).newLiteralTree(text);
                }
            },

            // @param parameter-name description
            new TagParser(TagParser.Kind.BLOCK, DCTree.Kind.PARAM) {
                @Override
                public DCTree parse(int pos) throws ParseException {
                    skipWhitespace();

                    boolean typaram = false;
                    if (ch == '<') {
                        typaram = true;
                        nextChar();
                    }

                    DCIdentifier id = identifier();

                    if (typaram) {
                        if (ch != '>')
                            throw new ParseException(bp, "dc.gt.expected");
                        nextChar();
                    }

                    skipWhitespace();
                    List<DCTree> desc = blockContent();
                    return m.at(pos).newParamTree(typaram, id, desc);
                }
            },

            // @provides service-name description
            new TagParser(TagParser.Kind.BLOCK, DCTree.Kind.PROVIDES) {
                @Override
                public DCTree parse(int pos) throws ParseException {
                    skipWhitespace();
                    DCReference ref = reference(ReferenceParser.Mode.MEMBER_DISALLOWED);
                    List<DCTree> description = blockContent();
                    return m.at(pos).newProvidesTree(ref, description);
                }
            },

            // @return description  -or-  {@return description}
            new TagParser(TagParser.Kind.EITHER, DCTree.Kind.RETURN) {
                @Override
                public DCTree parse(int pos, Kind kind) {
                    List<DCTree> description = switch (kind) {
                        case BLOCK -> blockContent();
                        case INLINE -> inlineContent();
                        default -> throw new IllegalArgumentException(kind.toString());
                    };
                    return m.at(pos).newReturnTree(kind == Kind.INLINE, description);
                }
            },

            // @see reference | quoted-string | HTML
            new TagParser(TagParser.Kind.BLOCK, DCTree.Kind.SEE) {
                @Override
                public DCTree parse(int pos) throws ParseException {
                    skipWhitespace();
                    switch (ch) {
                        case '"' -> {
                            DCText string = quotedString();
                            if (string != null) {
                                skipWhitespace();
                                if (ch == '@'
                                        || ch == EOI && bp == buf.length - 1) {
                                    return m.at(pos).newSeeTree(List.<DCTree>of(string));
                                }
                            }
                        }

                        case '<' -> {
                            List<DCTree> html = blockContent();
                            if (html != null)
                                return m.at(pos).newSeeTree(html);
                        }

                        case '@' -> {
                            if (newline)
                                throw new ParseException("dc.no.content");
                        }

                        case EOI -> {
                            if (bp == buf.length - 1)
                                throw new ParseException("dc.no.content");
                        }

                        default -> {
                            if (isJavaIdentifierStart(ch) || ch == '#') {
                                DCReference ref = reference(ReferenceParser.Mode.MEMBER_OPTIONAL);
                                List<DCTree> description = blockContent();
                                return m.at(pos).newSeeTree(description.prepend(ref));
                            }
                        }
                    }
                    throw new ParseException("dc.unexpected.content");
                }
            },

            // @serialData data-description
            new TagParser(TagParser.Kind.BLOCK, DCTree.Kind.SERIAL_DATA) {
                @Override
                public DCTree parse(int pos) {
                    List<DCTree> description = blockContent();
                    return m.at(pos).newSerialDataTree(description);
                }
            },

            // @serialField field-name field-type description
            new TagParser(TagParser.Kind.BLOCK, DCTree.Kind.SERIAL_FIELD) {
                @Override
                public DCTree parse(int pos) throws ParseException {
                    skipWhitespace();
                    DCIdentifier name = identifier();
                    skipWhitespace();
                    DCReference type = reference(ReferenceParser.Mode.MEMBER_DISALLOWED);
                    List<DCTree> description = null;
                    if (isWhitespace(ch)) {
                        skipWhitespace();
                        description = blockContent();
                    }
                    return m.at(pos).newSerialFieldTree(name, type, description);
                }
            },

            // @serial field-description | include | exclude
            new TagParser(TagParser.Kind.BLOCK, DCTree.Kind.SERIAL) {
                @Override
                public DCTree parse(int pos) {
                    List<DCTree> description = blockContent();
                    return m.at(pos).newSerialTree(description);
                }
            },

            // @since since-text
            new TagParser(TagParser.Kind.BLOCK, DCTree.Kind.SINCE) {
                @Override
                public DCTree parse(int pos) {
                    List<DCTree> description = blockContent();
                    return m.at(pos).newSinceTree(description);
                }
            },

            // {@snippet attributes :
            //  body}
            new TagParser(TagParser.Kind.INLINE, DCTree.Kind.SNIPPET) {
                @Override
                DCTree parse(int pos) throws ParseException {
                    skipWhitespace();
                    List<DCTree> attributes = tagAttrs();
                    // expect "}" or ":"
                    if (ch == '}') {
                        nextChar();
                        return m.at(pos).newSnippetTree(attributes, null);
                    } else if (ch == ':') {
                        newline = false;
                        // consume ':'
                        nextChar();
                        // expect optional whitespace followed by mandatory newline
                        while (bp < buflen && isHorizontalWhitespace(ch)) {
                            nextChar();
                        }
                        // check that we are looking at newline
                        if (!newline) {
                            if (bp >= buf.length - 1) {
                                throw new ParseException("dc.no.content");
                            }
                            throw new ParseException("dc.unexpected.content");
                        }
                        // consume newline
                        nextChar();
                        DCText text = inlineText(WhitespaceRetentionPolicy.RETAIN_ALL);
                        nextChar();
                        return m.at(pos).newSnippetTree(attributes, text);
                    } else if (bp >= buf.length - 1) {
                        throw new ParseException("dc.no.content");
                    } else {
                        throw new ParseException("dc.unexpected.content");
                    }
                }

                /*
                 * Reads a series of inline snippet tag attributes.
                 *
                 * Attributes are terminated by the first of ":" (colon) or
                 * an unmatched "}" (closing curly).
                 */
                private List<DCTree> tagAttrs() {
                    ListBuffer<DCTree> attrs = new ListBuffer<>();
                    skipWhitespace();
                    while (bp < buflen && isIdentifierStart(ch)) {
                        int namePos = bp;
                        Name name = readAttributeName();
                        skipWhitespace();
                        List<DCTree> value = null;
                        ValueKind vkind = ValueKind.EMPTY;
                        if (ch == '=') {
                            ListBuffer<DCTree> v = new ListBuffer<>();
                            nextChar();
                            skipWhitespace();
                            if (ch == '\'' || ch == '"') {
                                newline = false;
                                vkind = (ch == '\'') ? ValueKind.SINGLE : ValueKind.DOUBLE;
                                char quote = ch;
                                nextChar();
                                textStart = bp;
                                while (bp < buflen && ch != quote) {
                                    nextChar();
                                }
                                addPendingText(v, bp - 1, DocTree.Kind.TEXT);
                                nextChar();
                            } else {
                                vkind = ValueKind.UNQUOTED;
                                textStart = bp;
                                // Stop on '}' and ':' for them to be re-consumed by non-attribute parts of tag
                                while (bp < buflen && (ch != '}' && ch != ':' && !isUnquotedAttrValueTerminator(ch))) {
                                    nextChar();
                                }
                                addPendingText(v, bp - 1, DocTree.Kind.TEXT);
                            }
                            skipWhitespace();
                            value = v.toList();
                        }
                        DCAttribute attr = m.at(namePos).newAttributeTree(name, vkind, value);
                        attrs.add(attr);
                    }
                    return attrs.toList();
                }
            },

            // @spec url label
            new TagParser(TagParser.Kind.BLOCK, DCTree.Kind.SPEC) {
                @Override
                public DCTree parse(int pos) throws ParseException {
                    skipWhitespace();
                    DCText url = inlineWord();
                    if (url == null || url.isBlank()) {
                        throw new ParseException("dc.no.url");
                    }
                    skipWhitespace();
                    List<DCTree> title = blockContent();
                    if (title.isEmpty() || DCTree.isBlank(title)) {
                        throw new ParseException("dc.no.title");
                    }
                    return m.at(pos).newSpecTree(url, title);
                }
            },

            // {@summary summary-text}
            new TagParser(TagParser.Kind.INLINE, DCTree.Kind.SUMMARY) {
                @Override
                public DCTree parse(int pos) {
                    List<DCTree> summary = inlineContent();
                    return m.at(pos).newSummaryTree(summary);
                }
            },

            // {@systemProperty property-name}
            new TagParser(TagParser.Kind.INLINE, DCTree.Kind.SYSTEM_PROPERTY) {
                @Override
                public DCTree parse(int pos) throws ParseException {
                    skipWhitespace();
                    if (ch == '}') {
                        throw new ParseException("dc.no.content");
                    }
                    Name propertyName = readSystemPropertyName();
                    if (propertyName == null) {
                        throw new ParseException("dc.no.content");
                    }
                    skipWhitespace();
                    if (ch != '}') {
                        nextChar();
                        throw new ParseException("dc.unexpected.content");
                    } else {
                        nextChar();
                        return m.at(pos).newSystemPropertyTree(propertyName);
                    }
                }
            },

            // @throws class-name description
            new TagParser(TagParser.Kind.BLOCK, DCTree.Kind.THROWS) {
                @Override
                public DCTree parse(int pos) throws ParseException {
                    skipWhitespace();
                    DCReference ref = reference(ReferenceParser.Mode.MEMBER_DISALLOWED);
                    List<DCTree> description = blockContent();
                    return m.at(pos).newThrowsTree(ref, description);
                }
            },

            // @uses service-name description
            new TagParser(TagParser.Kind.BLOCK, DCTree.Kind.USES) {
                @Override
                public DCTree parse(int pos) throws ParseException {
                    skipWhitespace();
                    DCReference ref = reference(ReferenceParser.Mode.MEMBER_DISALLOWED);
                    List<DCTree> description = blockContent();
                    return m.at(pos).newUsesTree(ref, description);
                }
            },

            // {@value [format-string] package.class#field}
            new TagParser(TagParser.Kind.INLINE, DCTree.Kind.VALUE) {
                @Override
                public DCTree parse(int pos) throws ParseException {
                    skipWhitespace();
                    DCText format;
                    switch (ch) {
                        case '%' -> {
                            format = inlineWord();
                            skipWhitespace();
                        }
                        case '"' -> {
                            format = quotedString();
                            skipWhitespace();
                        }
                        default -> {
                            format = null;
                        }
                    }
                    DCReference ref = reference(ReferenceParser.Mode.MEMBER_REQUIRED);
                    skipWhitespace();
                    if (ch == '}') {
                        nextChar();
                        return format == null
                                ? m.at(pos).newValueTree(ref)
                                : m.at(pos).newValueTree(format, ref);
                    }
                    nextChar();
                    throw new ParseException("dc.unexpected.content");
                }
            },

            // @version version-text
            new TagParser(TagParser.Kind.BLOCK, DCTree.Kind.VERSION) {
                @Override
                public DCTree parse(int pos) {
                    List<DCTree> description = blockContent();
                    return m.at(pos).newVersionTree(description);
                }
            },
        };

        Map<Name, TagParser> tagParsers = new HashMap<>();
        for (TagParser p: parsers)
            tagParsers.put(names.fromString(p.getTreeKind().tagName), p);

        return tagParsers;
    }

}
