/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.tree;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.lang.model.element.Name;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import com.sun.source.doctree.AttributeTree.ValueKind;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.DocTree.Kind;
import com.sun.source.doctree.EndElementTree;
import com.sun.source.doctree.IdentifierTree;
import com.sun.source.doctree.ReferenceTree;
import com.sun.source.doctree.StartElementTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.util.DocTreeFactory;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.parser.ReferenceParser;
import com.sun.tools.javac.parser.Tokens.Comment;
import com.sun.tools.javac.tree.DCTree.DCAttribute;
import com.sun.tools.javac.tree.DCTree.DCAuthor;
import com.sun.tools.javac.tree.DCTree.DCComment;
import com.sun.tools.javac.tree.DCTree.DCDeprecated;
import com.sun.tools.javac.tree.DCTree.DCDocComment;
import com.sun.tools.javac.tree.DCTree.DCDocRoot;
import com.sun.tools.javac.tree.DCTree.DCDocType;
import com.sun.tools.javac.tree.DCTree.DCEndElement;
import com.sun.tools.javac.tree.DCTree.DCEntity;
import com.sun.tools.javac.tree.DCTree.DCErroneous;
import com.sun.tools.javac.tree.DCTree.DCEscape;
import com.sun.tools.javac.tree.DCTree.DCHidden;
import com.sun.tools.javac.tree.DCTree.DCIdentifier;
import com.sun.tools.javac.tree.DCTree.DCIndex;
import com.sun.tools.javac.tree.DCTree.DCInheritDoc;
import com.sun.tools.javac.tree.DCTree.DCLink;
import com.sun.tools.javac.tree.DCTree.DCLiteral;
import com.sun.tools.javac.tree.DCTree.DCParam;
import com.sun.tools.javac.tree.DCTree.DCProvides;
import com.sun.tools.javac.tree.DCTree.DCReference;
import com.sun.tools.javac.tree.DCTree.DCRawText;
import com.sun.tools.javac.tree.DCTree.DCReturn;
import com.sun.tools.javac.tree.DCTree.DCSee;
import com.sun.tools.javac.tree.DCTree.DCSerial;
import com.sun.tools.javac.tree.DCTree.DCSerialData;
import com.sun.tools.javac.tree.DCTree.DCSerialField;
import com.sun.tools.javac.tree.DCTree.DCSince;
import com.sun.tools.javac.tree.DCTree.DCSnippet;
import com.sun.tools.javac.tree.DCTree.DCSpec;
import com.sun.tools.javac.tree.DCTree.DCStartElement;
import com.sun.tools.javac.tree.DCTree.DCSummary;
import com.sun.tools.javac.tree.DCTree.DCSystemProperty;
import com.sun.tools.javac.tree.DCTree.DCText;
import com.sun.tools.javac.tree.DCTree.DCThrows;
import com.sun.tools.javac.tree.DCTree.DCUnknownBlockTag;
import com.sun.tools.javac.tree.DCTree.DCUnknownInlineTag;
import com.sun.tools.javac.tree.DCTree.DCUses;
import com.sun.tools.javac.tree.DCTree.DCValue;
import com.sun.tools.javac.tree.DCTree.DCVersion;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.DefinedBy;
import com.sun.tools.javac.util.DefinedBy.Api;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Pair;
import com.sun.tools.javac.util.Position;
import com.sun.tools.javac.util.StringUtils;


/**
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class DocTreeMaker implements DocTreeFactory {

    /** The context key for the tree factory. */
    protected static final Context.Key<DocTreeMaker> treeMakerKey = new Context.Key<>();

    /** Get the TreeMaker instance. */
    public static DocTreeMaker instance(Context context) {
        DocTreeMaker instance = context.get(treeMakerKey);
        if (instance == null)
            instance = new DocTreeMaker(context);
        return instance;
    }

    /** The position at which subsequent trees will be created.
     */
    public int pos;

    private final JavacTrees trees;
    private final SentenceBreaker breaker;

    /** Utility class to parse reference signatures. */
    private final ReferenceParser referenceParser;

    /** Create a tree maker with NOPOS as initial position.
     */
    @SuppressWarnings("this-escape")
    protected DocTreeMaker(Context context) {
        context.put(treeMakerKey, this);
        this.pos = Position.NOPOS;
        trees = JavacTrees.instance(context);
        referenceParser = new ReferenceParser(ParserFactory.instance(context));
        breaker = new SentenceBreaker(this);
    }

    /** Reassign current position.
     */
    @Override @DefinedBy(Api.COMPILER_TREE)
    public DocTreeMaker at(int pos) {
        this.pos = pos;
        return this;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCAttribute newAttributeTree(Name name, ValueKind vkind, List<? extends DocTree> value) {
        DCAttribute tree = new DCAttribute(name, vkind, cast(value));
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCAuthor newAuthorTree(List<? extends DocTree> name) {
        DCAuthor tree = new DCAuthor(cast(name));
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCLiteral newCodeTree(TextTree text) {
        DCLiteral tree = new DCLiteral(Kind.CODE, (DCText) text);
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCComment newCommentTree(String text) {
        DCComment tree = new DCComment(text);
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCDeprecated newDeprecatedTree(List<? extends DocTree> text) {
        DCDeprecated tree = new DCDeprecated(cast(text));
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCDocComment newDocCommentTree(List<? extends DocTree> fullBody, List<? extends DocTree> tags) {
        return newDocCommentTree(fullBody, tags, Collections.emptyList(), Collections.emptyList());
    }

    public DCDocComment newDocCommentTree(Comment comment,
                                          List<? extends DocTree> fullBody,
                                          List<? extends DocTree> tags,
                                          List<? extends DocTree> preamble,
                                          List<? extends DocTree> postamble) {
        Pair<List<DCTree>, List<DCTree>> pair = splitBody(fullBody);
        DCDocComment tree = new DCDocComment(comment, cast(fullBody), pair.fst, pair.snd,
                cast(tags), cast(preamble), cast(postamble));
        tree.pos = pos;
        return tree;
    }

    /*
     * Primarily to produce a DocCommentTree when given a
     * first sentence and a body, this is useful, in cases
     * where the trees are being synthesized by a tool.
     */
    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCDocComment newDocCommentTree(List<? extends DocTree> fullBody,
                                          List<? extends DocTree> tags,
                                          List<? extends DocTree> preamble,
                                          List<? extends DocTree> postamble) {
        ListBuffer<DCTree> lb = new ListBuffer<>();
        lb.addAll(cast(fullBody));
        List<DCTree> fBody = lb.toList();

        // A dummy comment that returns Position.NOPOS for any source position.
        // A different solution would be to replace the Comment field
        // in DCDocComment with a narrower type equivalent to Function<int,int>
        // so that here in this code we can just supply a lambda as follows:
        //   i -> Position.NOPOS
        Comment c = new Comment() {

            @Override
            public JCDiagnostic.DiagnosticPosition getPos() {
                return null;
            }

            @Override
            public int getSourcePos(int index) {
                return Position.NOPOS;
            }

            @Override
            public String getText() {
                throw new UnsupportedOperationException(getClass() + ".getText");
            }

            @Override
            public CommentStyle getStyle() {
                throw new UnsupportedOperationException(getClass() + ".getStyle");
            }

            @Override
            public boolean isDeprecated() {
                throw new UnsupportedOperationException(getClass() + ".isDeprecated");
            }
        };
        Pair<List<DCTree>, List<DCTree>> pair = splitBody(fullBody);
        return new DCDocComment(c, fBody, pair.fst, pair.snd, cast(tags),
                                             cast(preamble), cast(postamble));
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCDocRoot newDocRootTree() {
        DCDocRoot tree = new DCDocRoot();
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCDocType newDocTypeTree(String text) {
        DCDocType tree = new DCDocType(text);
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCEndElement newEndElementTree(Name name) {
        DCEndElement tree = new DCEndElement(name);
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCEntity newEntityTree(Name name) {
        DCEntity tree = new DCEntity(name);
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCErroneous newErroneousTree(String text, Diagnostic<JavaFileObject> diag) {
        DCErroneous tree = new DCErroneous(text, (JCDiagnostic) diag);
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCEscape newEscapeTree(char ch) {
        DCEscape tree = new DCEscape(ch);
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCThrows newExceptionTree(ReferenceTree name, List<? extends DocTree> description) {
        // TODO: verify the reference is just to a type (not a field or method)
        DCThrows tree = new DCThrows(Kind.EXCEPTION, (DCReference) name, cast(description));
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCHidden newHiddenTree(List<? extends DocTree> text) {
        DCHidden tree = new DCHidden(cast(text));
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCIdentifier newIdentifierTree(Name name) {
        DCIdentifier tree = new DCIdentifier(name);
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCIndex newIndexTree(DocTree term, List<? extends DocTree> description) {
        DCIndex tree = new DCIndex((DCTree) term, cast(description));
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCInheritDoc newInheritDocTree() {
        return newInheritDocTree(null);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCInheritDoc newInheritDocTree(ReferenceTree supertype) {
        DCInheritDoc tree = new DCInheritDoc((DCReference) supertype);
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCLink newLinkTree(ReferenceTree ref, List<? extends DocTree> label) {
        DCLink tree = new DCLink(Kind.LINK, (DCReference) ref, cast(label));
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCLink newLinkPlainTree(ReferenceTree ref, List<? extends DocTree> label) {
        DCLink tree = new DCLink(Kind.LINK_PLAIN, (DCReference) ref, cast(label));
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCLiteral newLiteralTree(TextTree text) {
        DCLiteral tree = new DCLiteral(Kind.LITERAL, (DCText) text);
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCRawText newRawTextTree(DocTree.Kind kind, String text) {
        DCTree.DCRawText tree = new DCRawText(kind, text);
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCParam newParamTree(boolean isTypeParameter, IdentifierTree name, List<? extends DocTree> description) {
        DCParam tree = new DCParam(isTypeParameter, (DCIdentifier) name, cast(description));
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCProvides newProvidesTree(ReferenceTree name, List<? extends DocTree> description) {
        DCProvides tree = new DCProvides((DCReference) name, cast(description));
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCReference newReferenceTree(String signature) {
        try {
            ReferenceParser.Reference ref = referenceParser.parse(signature, ReferenceParser.Mode.MEMBER_OPTIONAL);
            DCReference tree = newReferenceTree(signature, ref);
            tree.pos = pos;
            return tree;
        } catch (ReferenceParser.ParseException e) {
            throw new IllegalArgumentException("invalid signature", e);
        }
    }

    public DCReference newReferenceTree(String signature, ReferenceParser.Reference ref) {
        DCReference tree = new DCReference(signature, ref.moduleName, ref.qualExpr, ref.member, ref.paramTypes);
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCReturn newReturnTree(List<? extends DocTree> description) {
        return newReturnTree(false, description);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCReturn newReturnTree(boolean isInline, List<? extends DocTree> description) {
        DCReturn tree = new DCReturn(isInline, cast(description));
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCSee newSeeTree(List<? extends DocTree> reference) {
        DCSee tree = new DCSee(cast(reference));
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCSerial newSerialTree(List<? extends DocTree> description) {
        DCSerial tree = new DCSerial(cast(description));
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCSerialData newSerialDataTree(List<? extends DocTree> description) {
        DCSerialData tree = new DCSerialData(cast(description));
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCSerialField newSerialFieldTree(IdentifierTree name, ReferenceTree type, List<? extends DocTree> description) {
        DCSerialField tree = new DCSerialField((DCIdentifier) name, (DCReference) type, cast(description));
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCSince newSinceTree(List<? extends DocTree> text) {
        DCSince tree = new DCSince(cast(text));
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCSnippet newSnippetTree(List<? extends DocTree> attributes, TextTree text) {
        DCSnippet tree = new DCSnippet(cast(attributes), (DCText) text);
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCSpec newSpecTree(TextTree url, List<? extends DocTree> title) {
        DCSpec tree = new DCSpec((DCText) url, cast(title));
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCStartElement newStartElementTree(Name name, List<? extends DocTree> attrs, boolean selfClosing) {
        DCStartElement tree = new DCStartElement(name, cast(attrs), selfClosing);
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCSummary newSummaryTree(List<? extends DocTree> text) {
        DCSummary tree = new DCSummary(cast(text));
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCSystemProperty newSystemPropertyTree(Name propertyName) {
        DCSystemProperty tree = new DCSystemProperty(propertyName);
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCText newTextTree(String text) {
        DCText tree = new DCText(text);
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCThrows newThrowsTree(ReferenceTree name, List<? extends DocTree> description) {
        // TODO: verify the reference is just to a type (not a field or method)
        DCThrows tree = new DCThrows(Kind.THROWS, (DCReference) name, cast(description));
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCUnknownBlockTag newUnknownBlockTagTree(Name name, List<? extends DocTree> content) {
        DCUnknownBlockTag tree = new DCUnknownBlockTag(name, cast(content));
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCUnknownInlineTag newUnknownInlineTagTree(Name name, List<? extends DocTree> content) {
        DCUnknownInlineTag tree = new DCUnknownInlineTag(name, cast(content));
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCUses newUsesTree(ReferenceTree name, List<? extends DocTree> description) {
        DCUses tree = new DCUses((DCReference) name, cast(description));
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCValue newValueTree(ReferenceTree ref) {
        return newValueTree(null, ref);
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCValue newValueTree(TextTree format, ReferenceTree ref) {
        // TODO: verify the reference is to a constant value
        DCValue tree = new DCValue((DCText) format, (DCReference) ref);
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public DCVersion newVersionTree(List<? extends DocTree> text) {
        DCVersion tree = new DCVersion(cast(text));
        tree.pos = pos;
        return tree;
    }

    @Override @DefinedBy(Api.COMPILER_TREE)
    public List<DocTree> getFirstSentence(List<? extends DocTree> list) {
        Pair<List<DCTree>, List<DCTree>> pair = splitBody(list);
        return new ArrayList<>(pair.fst);
    }

    @SuppressWarnings("unchecked")
    private static List<DCTree> cast(List<? extends DocTree> list) {
        return (List<DCTree>) list;
    }

    Pair<List<DCTree>, List<DCTree>> splitBody(List<? extends DocTree> list) {
        return breaker.splitBody(list);
    }

    static class SentenceBreaker {
        final DocTreeMaker m;

        // A subset of block tags, which acts as sentence breakers, appearing
        // anywhere but the zero'th position in the first sentence.
        static final Set<String> sentenceBreakTags = Set.of(
                "H1", "H2", "H3", "H4", "H5", "H6",
                "PRE", "P");

        SentenceBreaker(DocTreeMaker m) {
            this.m = m;
        }

        /*
         * Breaks up the body tags into the first sentence and its successors.
         * The first sentence is determined with the presence of a period,
         * block tag, or a sentence break, as returned by the BreakIterator.
         * Trailing whitespaces are trimmed.
         */
        Pair<List<DCTree>, List<DCTree>> splitBody(List<? extends DocTree> list) {
            if (list.isEmpty()) {
                return new Pair<>(List.of(), List.of());
            }
            // pos is modified as we create trees, therefore
            // we save the pos and restore it later.
            final var savedPos = m.pos;
            try {
                // split list into first sentence and body
                var fs = new ListBuffer<DCTree>();
                var body = new ListBuffer<DCTree>();
                var alist = new ArrayList<>(cast(list)); // copy to allow indexed access for peeking
                var iter = alist.listIterator();
                var foundFirstSentence = false;
                while (iter.hasNext() && !foundFirstSentence) {
                    boolean isFirst = !iter.hasPrevious();
                    DCTree dt = iter.next();
                    switch (dt.getKind()) {
                        case RETURN, SUMMARY -> {
                            fs.add(dt);
                            foundFirstSentence = true;
                        }

                        case TEXT, MARKDOWN -> {
                            var peekedNext = iter.hasNext() ? alist.get(iter.nextIndex()) : null;
                            var content = getContent(dt);
                            int breakOffset = getSentenceBreak(dt.getKind(), content, peekedNext);
                            if (breakOffset > 0) {
                                // the end of sentence is within the current node;
                                // split it, skipping whitespace in between the two parts
                                var fsPart = newNode(dt.getKind(), dt.pos, content.substring(0, breakOffset).stripTrailing());
                                fs.add(fsPart);
                                int wsOffset = skipWhiteSpace(content, breakOffset);
                                if (wsOffset > 0) {
                                    var bodyPart = newNode(dt.getKind(), dt.pos + wsOffset, content.substring(wsOffset));
                                    body.add(bodyPart);
                                }
                                foundFirstSentence = true;
                            } else if (peekedNext != null && isSentenceBreak(peekedNext, false)) {
                                // the next node is a sentence break, so this is the end of the first sentence;
                                // remove trailing spaces
                                var fsPart = newNode(dt.getKind(), dt.pos, content.stripTrailing());
                                fs.add(fsPart);
                                foundFirstSentence = true;
                            } else {
                                // no sentence break found; keep scanning
                                fs.add(dt);
                            }
                        }

                        default -> {
                            // This ignores certain block tags if they appear first in the list,
                            // allowing the content of that tag to provide the first sentence.
                            // It would be better if other block tags always terminated the
                            // first sentence as well, like lists and tables.
                            if (isSentenceBreak(dt, isFirst)) {
                                body.add(dt);
                                foundFirstSentence = true;
                            } else {
                                fs.add(dt);
                            }
                        }
                    }
                }

                // if there are remaining elements, then we have found the first
                // sentence, and remaining elements are for the body.
                while (iter.hasNext()) {
                    body.add(iter.next());
                }

                return new Pair<>(fs.toList(), body.toList());
            } finally {
                m.pos = savedPos;
            }
        }

        private String getContent(DCTree dt) {
            return switch (dt.getKind()) {
                case TEXT -> ((DCText) dt).text;
                case MARKDOWN -> ((DCRawText) dt).code;
                default -> throw new IllegalArgumentException(dt.getKind().toString());
            };
        }

        private DCTree newNode(DocTree.Kind kind, int pos, String text) {
            return switch (kind) {
                case TEXT -> m.at(pos).newTextTree(text);
                case MARKDOWN -> m.at(pos).newRawTextTree(kind, text);
                default -> throw new IllegalArgumentException(kind.toString());
            };
        }

        /*
         * Computes the first sentence, if using a default breaker,
         * the break is returned, if not then a -1, indicating that
         * more doctree elements are required to be examined.
         *
         * BreakIterator.next points to the start of the following sentence,
         * and does not provide an easy way to disambiguate between "sentence break",
         * "possible sentence break" and "not a sentence break" at the end of the input.
         * For example, BreakIterator.next returns the index for the end
         * of the string for all of these examples,
         * using vertical bars to delimit the bounds of the example text
         * |Abc|        (not a valid end of sentence break, if followed by more text)
         * |Abc.|       (maybe a valid end of sentence break, depending on the following text)
         * |Abc. |      (maybe a valid end of sentence break, depending on the following text)
         * |"Abc." |    (maybe a valid end of sentence break, depending on the following text)
         * |Abc.  |     (definitely a valid end of sentence break)
         * |"Abc."  |   (definitely a valid end of sentence break)
         * Therefore, we have to probe further to determine whether
         * there really is a sentence break or not at the end of this run of text.
         */
        private int getSentenceBreak(DocTree.Kind kind, String s, DCTree nextTree) {
            BreakIterator breakIterator = m.trees.getBreakIterator();
            if (breakIterator == null) {
                return defaultSentenceBreak(kind, s);
            }

            // If there is a paragraph break in a run of Markdown text, restrict the
            // search to the first paragraph, to avoid beginning-of-line Markdown constructs
            // confusing the sentence breaker.
            String s2 = normalize(kind, kind == Kind.MARKDOWN ? firstParaText(s) : s);
            breakIterator.setText(s2);
            final int sbrk = breakIterator.next();

            switch (kind) {
                case MARKDOWN -> {
                    int endParaPos = endParaPos(s2);
                    if (endParaPos != -1) {
                        return Math.min(sbrk, endParaPos);
                    }
                }
            }

            // If this is the last doctree, or if there was a paragraph break in a run
            // of Markdown text, then we found the droid we are looking for
            if (nextTree == null || kind == Kind.MARKDOWN && s2.length() < s.length()) {
                return sbrk;
            }

            // If the break is well within the span of the string i.e. not
            // at EOL, then we have a clear break.
            if (sbrk < s.length() - 1) {
                return sbrk;
            }

            switch (nextTree.getKind()) {
                case TEXT, MARKDOWN -> {
                    // Two adjacent text trees, a corner case, perhaps
                    // produced by a tool synthesizing a doctree. In
                    // this case, does the break lie within the first span,
                    // then we have the droid, otherwise allow the callers
                    // logic to handle the break in the adjacent doctree.
                    String combined = s2 + normalize(nextTree.getKind(), getContent(nextTree));
                    breakIterator.setText(combined);
                    int sbrk2 = breakIterator.next();
                    if (sbrk < sbrk2) {
                        return sbrk;
                    }
                }
            }

            // Is the adjacent tree a sentence breaker ?
            if (isSentenceBreak(nextTree, false)) {
                return sbrk;
            }

            // At this point the adjacent tree is either a javadoc tag ({@..),
            // html tag (<..) or an entity (&..). Perform a litmus test, by
            // concatenating a sentence, to validate the break earlier identified.
            String combined = s + "Dummy Sentence.";
            breakIterator.setText(combined);
            int sbrk2 = breakIterator.next();
            if (sbrk2 <= sbrk) {
                return sbrk2;
            }
            return -1; // indeterminate at this time
        }

        /*
         * Computes the first sentence break, a simple dot-space algorithm.
         */
        private int defaultSentenceBreak(DocTree.Kind kind, String s) {
            String s2 = normalize(kind, s);

            // scan for period followed by whitespace
            int period = -1;
            for (int i = 0; i < s2.length(); i++) {
                switch (s2.charAt(i)) {
                    case '.':
                        period = i;
                        break;

                    case ' ':
                    case '\f':
                    case '\n':
                    case '\r':
                    case '\t':
                        if (period >= 0) {
                            switch (kind) {
                                case MARKDOWN -> {
                                    int endParaPos = endParaPos(s2);
                                    return endParaPos == -1 || i < endParaPos ? i : endParaPos;
                                }
                                case TEXT -> {
                                    return i;
                                }
                                default -> throw new IllegalArgumentException(kind.toString());
                            }
                        }
                        break;

                    default:
                        period = -1;
                        break;
                }
            }

            return switch (kind) {
                case MARKDOWN -> endParaPos(s2); // may be -1
                case TEXT -> -1;
                default -> throw new IllegalArgumentException(kind.toString());
            };
        }

        // End of paragraph is newline, followed by a blank line or the beginning of the next block.
        // - + * are list markers
        // # = - are for headings
        // - _ * are for thematic breaks
        // >     is for block quotes
        private static final Pattern endPara = Pattern.compile("\n(([ \t]*\n)|( {0,3}[-+*#=_>]))");

        private static int endParaPos(String s) {
            Matcher m = endPara.matcher(s);
            return m.find() ? m.start() : -1;
        }

        private static String firstParaText(String s) {
            int endParaPos = endParaPos(s);
            return endParaPos == -1 ? s : s.substring(0, endParaPos);
        }

        private boolean isSentenceBreak(DCTree dt, boolean isFirstDocTree) {
            switch (dt.getKind()) {
                case START_ELEMENT:
                    StartElementTree set = (StartElementTree) dt;
                    return !isFirstDocTree && dt.pos > 1 && isSentenceBreak(set.getName());
                case END_ELEMENT:
                    EndElementTree eet = (EndElementTree) dt;
                    return !isFirstDocTree && dt.pos > 1 && isSentenceBreak(eet.getName());
                default:
                    return false;
            }
        }

        private boolean isSentenceBreak(Name tagName) {
            return sentenceBreakTags.contains(StringUtils.toUpperCase(tagName.toString()));
        }

        /*
         * Returns the position of the first non-whitespace character.
         */
        private int skipWhiteSpace(String s, int start) {
            for (int i = start; i < s.length(); i++) {
                char c = s.charAt(i);
                if (!Character.isWhitespace(c)) {
                    return i;
                }
            }
            return -1;
        }

        private String normalize(DocTree.Kind kind, String s) {
            return switch (kind) {
                case TEXT -> s;
                case MARKDOWN -> normalizeMarkdown(s);
                default -> throw new IllegalArgumentException(kind.toString());
            };
        }

        // Returns a string in which any periods that should not be considered
        // as ending a sentence are replaced by dashes.  This specifically
        // includes periods in code spans and links.
        private String normalizeMarkdown(String s) {
            StringBuilder sb = new StringBuilder();
            int slen = s.length();
            int i = 0;
            while (i < slen) {
                char ch = s.charAt(i);
                switch (ch) {
                    case '\\' -> {
                        sb.append(ch);
                        i++;
                        if (i < slen) {
                            sb.append(s.charAt(i));
                            i++;
                        }
                    }

                    case '<' -> i = skip(sb, s, i, ch, '>');
                    case '[' -> i = skip(sb, s, i, ch, ']');
                    case '(' -> i = skip(sb, s, i, ch, ')');

                    case '`' -> {
                        int start = i;
                        i++;
                        while (i < slen && s.charAt(i) == '`') {
                            i++;
                        }
                        String prefix = s.substring(start, i);
                        sb.append(prefix);
                        int j = s.indexOf(prefix, i);
                        if (j > i) {
                            sb.append(s.substring(i, j).replace('.', '-'));
                            sb.append(prefix);
                            i = j + prefix.length();
                        }
                    }

                    default -> {
                        sb.append(ch);
                        i++;
                    }
                }
            }

            return sb.toString();
        }

        private int skip(StringBuilder sb, String s, int i, char ch, char term) {
            sb.append(ch);
            i++;
            int j = s.indexOf(term, i);
            if (j != -1) {
                sb.append(s.substring(i, j).replace('.', '-'));
                return j;
            } else {
                return i;
            }
        }
    }

}
