/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collection;
import java.util.EnumSet;
import java.util.ListIterator;

import com.sun.source.doctree.AttributeTree.ValueKind;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.DocTree.Kind;
import com.sun.source.doctree.EndElementTree;
import com.sun.source.doctree.StartElementTree;
import com.sun.source.doctree.TextTree;
import com.sun.tools.doclint.HtmlTag;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.parser.Tokens.Comment;
import com.sun.tools.javac.tree.DCTree.DCAttribute;
import com.sun.tools.javac.tree.DCTree.DCAuthor;
import com.sun.tools.javac.tree.DCTree.DCComment;
import com.sun.tools.javac.tree.DCTree.DCDeprecated;
import com.sun.tools.javac.tree.DCTree.DCDocComment;
import com.sun.tools.javac.tree.DCTree.DCDocRoot;
import com.sun.tools.javac.tree.DCTree.DCEndElement;
import com.sun.tools.javac.tree.DCTree.DCEntity;
import com.sun.tools.javac.tree.DCTree.DCErroneous;
import com.sun.tools.javac.tree.DCTree.DCIdentifier;
import com.sun.tools.javac.tree.DCTree.DCInheritDoc;
import com.sun.tools.javac.tree.DCTree.DCLink;
import com.sun.tools.javac.tree.DCTree.DCLiteral;
import com.sun.tools.javac.tree.DCTree.DCParam;
import com.sun.tools.javac.tree.DCTree.DCReference;
import com.sun.tools.javac.tree.DCTree.DCReturn;
import com.sun.tools.javac.tree.DCTree.DCSee;
import com.sun.tools.javac.tree.DCTree.DCSerial;
import com.sun.tools.javac.tree.DCTree.DCSerialData;
import com.sun.tools.javac.tree.DCTree.DCSerialField;
import com.sun.tools.javac.tree.DCTree.DCSince;
import com.sun.tools.javac.tree.DCTree.DCStartElement;
import com.sun.tools.javac.tree.DCTree.DCText;
import com.sun.tools.javac.tree.DCTree.DCThrows;
import com.sun.tools.javac.tree.DCTree.DCUnknownBlockTag;
import com.sun.tools.javac.tree.DCTree.DCUnknownInlineTag;
import com.sun.tools.javac.tree.DCTree.DCValue;
import com.sun.tools.javac.tree.DCTree.DCVersion;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.DiagnosticSource;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Pair;
import com.sun.tools.javac.util.Position;

import static com.sun.tools.doclint.HtmlTag.*;

/**
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class DocTreeMaker {

    /** The context key for the tree factory. */
    protected static final Context.Key<DocTreeMaker> treeMakerKey = new Context.Key<>();

    // A subset of block tags, which acts as sentence breakers, appearing
    // anywhere but the zero'th position in the first sentence.
    final EnumSet<HtmlTag> sentenceBreakTags;

    /** Get the TreeMaker instance. */
    public static DocTreeMaker instance(Context context) {
        DocTreeMaker instance = context.get(treeMakerKey);
        if (instance == null)
            instance = new DocTreeMaker(context);
        return instance;
    }

    /** The position at which subsequent trees will be created.
     */
    public int pos = Position.NOPOS;

    /** Access to diag factory for ErroneousTrees. */
    private final JCDiagnostic.Factory diags;

    private final JavacTrees trees;

    /** Create a tree maker with NOPOS as initial position.
     */
    protected DocTreeMaker(Context context) {
        context.put(treeMakerKey, this);
        diags = JCDiagnostic.Factory.instance(context);
        this.pos = Position.NOPOS;
        trees = JavacTrees.instance(context);
        sentenceBreakTags = EnumSet.of(H1, H2, H3, H4, H5, H6, PRE, P);
    }

    /** Reassign current position.
     */
    public DocTreeMaker at(int pos) {
        this.pos = pos;
        return this;
    }

    /** Reassign current position.
     */
    public DocTreeMaker at(DiagnosticPosition pos) {
        this.pos = (pos == null ? Position.NOPOS : pos.getStartPosition());
        return this;
    }

    public DCAttribute Attribute(Name name, ValueKind vkind, List<DCTree> value) {
        DCAttribute tree = new DCAttribute(name, vkind, value);
        tree.pos = pos;
        return tree;
    }

    public DCAuthor Author(List<DCTree> name) {
        DCAuthor tree = new DCAuthor(name);
        tree.pos = pos;
        return tree;
    }

    public DCLiteral Code(DCText text) {
        DCLiteral tree = new DCLiteral(Kind.CODE, text);
        tree.pos = pos;
        return tree;
    }

    public DCComment Comment(String text) {
        DCComment tree = new DCComment(text);
        tree.pos = pos;
        return tree;
    }

    public DCDeprecated Deprecated(List<DCTree> text) {
        DCDeprecated tree = new DCDeprecated(text);
        tree.pos = pos;
        return tree;
    }

    public DCDocComment DocComment(Comment comment, List<DCTree> fullBody, List<DCTree> tags) {
        Pair<List<DCTree>, List<DCTree>> pair = splitBody(fullBody);
        DCDocComment tree = new DCDocComment(comment, fullBody, pair.fst, pair.snd, tags);
        tree.pos = pos;
        return tree;
    }

    /*
     * Primarily to produce a DocCommenTree when given a
     * first sentence and a body, this is useful, in cases
     * where the trees are being synthesized by a tool.
     */
    public DCDocComment DocComment(List<DCTree> firstSentence, List<DCTree> body, List<DCTree> tags) {
        ListBuffer<DCTree> lb = new ListBuffer<>();
        lb.addAll(firstSentence);
        lb.addAll(body);
        List<DCTree> fullBody = lb.toList();
        DCDocComment tree = new DCDocComment(null, fullBody, firstSentence, body, tags);
        return tree;
    }

    public DCDocRoot DocRoot() {
        DCDocRoot tree = new DCDocRoot();
        tree.pos = pos;
        return tree;
    }

    public DCEndElement EndElement(Name name) {
        DCEndElement tree = new DCEndElement(name);
        tree.pos = pos;
        return tree;
    }

    public DCEntity Entity(Name name) {
        DCEntity tree = new DCEntity(name);
        tree.pos = pos;
        return tree;
    }

    public DCErroneous Erroneous(String text, DiagnosticSource diagSource, String code, Object... args) {
        DCErroneous tree = new DCErroneous(text, diags, diagSource, code, args);
        tree.pos = pos;
        return tree;
    }

    public DCThrows Exception(DCReference name, List<DCTree> description) {
        DCThrows tree = new DCThrows(Kind.EXCEPTION, name, description);
        tree.pos = pos;
        return tree;
    }

    public DCIdentifier Identifier(Name name) {
        DCIdentifier tree = new DCIdentifier(name);
        tree.pos = pos;
        return tree;
    }

    public DCInheritDoc InheritDoc() {
        DCInheritDoc tree = new DCInheritDoc();
        tree.pos = pos;
        return tree;
    }

    public DCLink Link(DCReference ref, List<DCTree> label) {
        DCLink tree = new DCLink(Kind.LINK, ref, label);
        tree.pos = pos;
        return tree;
    }

    public DCLink LinkPlain(DCReference ref, List<DCTree> label) {
        DCLink tree = new DCLink(Kind.LINK_PLAIN, ref, label);
        tree.pos = pos;
        return tree;
    }

    public DCLiteral Literal(DCText text) {
        DCLiteral tree = new DCLiteral(Kind.LITERAL, text);
        tree.pos = pos;
        return tree;
    }

    public DCParam Param(boolean isTypeParameter, DCIdentifier name, List<DCTree> description) {
        DCParam tree = new DCParam(isTypeParameter, name, description);
        tree.pos = pos;
        return tree;
    }

    public DCReference Reference(String signature,
            JCTree qualExpr, Name member, List<JCTree> paramTypes) {
        DCReference tree = new DCReference(signature, qualExpr, member, paramTypes);
        tree.pos = pos;
        return tree;
    }

    public DCReturn Return(List<DCTree> description) {
        DCReturn tree = new DCReturn(description);
        tree.pos = pos;
        return tree;
    }

    public DCSee See(List<DCTree> reference) {
        DCSee tree = new DCSee(reference);
        tree.pos = pos;
        return tree;
    }

    public DCSerial Serial(List<DCTree> description) {
        DCSerial tree = new DCSerial(description);
        tree.pos = pos;
        return tree;
    }

    public DCSerialData SerialData(List<DCTree> description) {
        DCSerialData tree = new DCSerialData(description);
        tree.pos = pos;
        return tree;
    }

    public DCSerialField SerialField(DCIdentifier name, DCReference type, List<DCTree> description) {
        DCSerialField tree = new DCSerialField(name, type, description);
        tree.pos = pos;
        return tree;
    }

    public DCSince Since(List<DCTree> text) {
        DCSince tree = new DCSince(text);
        tree.pos = pos;
        return tree;
    }

    public DCStartElement StartElement(Name name, List<DCTree> attrs, boolean selfClosing) {
        DCStartElement tree = new DCStartElement(name, attrs, selfClosing);
        tree.pos = pos;
        return tree;
    }

    public DCText Text(String text) {
        DCText tree = new DCText(text);
        tree.pos = pos;
        return tree;
    }

    public DCThrows Throws(DCReference name, List<DCTree> description) {
        DCThrows tree = new DCThrows(Kind.THROWS, name, description);
        tree.pos = pos;
        return tree;
    }

    public DCUnknownBlockTag UnknownBlockTag(Name name, List<DCTree> content) {
        DCUnknownBlockTag tree = new DCUnknownBlockTag(name, content);
        tree.pos = pos;
        return tree;
    }

    public DCUnknownInlineTag UnknownInlineTag(Name name, List<DCTree> content) {
        DCUnknownInlineTag tree = new DCUnknownInlineTag(name, content);
        tree.pos = pos;
        return tree;
    }

    public DCValue Value(DCReference ref) {
        DCValue tree = new DCValue(ref);
        tree.pos = pos;
        return tree;
    }

    public DCVersion Version(List<DCTree> text) {
        DCVersion tree = new DCVersion(text);
        tree.pos = pos;
        return tree;
    }

    public java.util.List<DocTree> getFirstSentence(java.util.List<? extends DocTree> list) {
        Pair<List<DCTree>, List<DCTree>> pair = splitBody(list);
        return new ArrayList<>(pair.fst);
    }

    /*
     * Breaks up the body tags into the first sentence and its successors.
     * The first sentence is determined with the presence of a period,
     * block tag, or a sentence break, as returned by the BreakIterator.
     * Trailing whitespaces are trimmed.
     */
    private Pair<List<DCTree>, List<DCTree>> splitBody(Collection<? extends DocTree> list) {
        // pos is modified as we create trees, therefore
        // we save the pos and restore it later.
        final int savedpos = this.pos;
        try {
            ListBuffer<DCTree> body = new ListBuffer<>();
            // split body into first sentence and body
            ListBuffer<DCTree> fs = new ListBuffer<>();
            if (list.isEmpty()) {
                return new Pair<>(fs.toList(), body.toList());
            }
            boolean foundFirstSentence = false;
            ArrayList<DocTree> alist = new ArrayList<>(list);
            ListIterator<DocTree> itr = alist.listIterator();
            while (itr.hasNext()) {
                boolean isFirst = !itr.hasPrevious();
                DocTree dt = itr.next();
                int spos = ((DCTree) dt).pos;
                if (foundFirstSentence) {
                    body.add((DCTree) dt);
                    continue;
                }
                switch (dt.getKind()) {
                    case TEXT:
                        DCText tt = (DCText) dt;
                        String s = tt.getBody();
                        DocTree peekedNext = itr.hasNext()
                                ? alist.get(itr.nextIndex())
                                : null;
                        int sbreak = getSentenceBreak(s, peekedNext);
                        if (sbreak > 0) {
                            s = removeTrailingWhitespace(s.substring(0, sbreak));
                            DCText text = this.at(spos).Text(s);
                            fs.add(text);
                            foundFirstSentence = true;
                            int nwPos = skipWhiteSpace(tt.getBody(), sbreak);
                            if (nwPos > 0) {
                                DCText text2 = this.at(spos + nwPos).Text(tt.getBody().substring(nwPos));
                                body.add(text2);
                            }
                            continue;
                        } else if (itr.hasNext()) {
                            // if the next doctree is a break, remove trailing spaces
                            peekedNext = alist.get(itr.nextIndex());
                            boolean sbrk = isSentenceBreak(peekedNext, false);
                            if (sbrk) {
                                DocTree next = itr.next();
                                s = removeTrailingWhitespace(s);
                                DCText text = this.at(spos).Text(s);
                                fs.add(text);
                                body.add((DCTree) next);
                                foundFirstSentence = true;
                                continue;
                            }
                        }
                        break;
                    default:
                        if (isSentenceBreak(dt, isFirst)) {
                            body.add((DCTree) dt);
                            foundFirstSentence = true;
                            continue;
                        }
                        break;
                }
                fs.add((DCTree) dt);
            }
            return new Pair<>(fs.toList(), body.toList());
        } finally {
            this.pos = savedpos;
        }
    }

    private boolean isTextTree(DocTree tree) {
        return tree.getKind() == Kind.TEXT;
    }

    /*
     * Computes the first sentence break, a simple dot-space algorithm.
     */
    int defaultSentenceBreak(String s) {
        // scan for period followed by whitespace
        int period = -1;
        for (int i = 0; i < s.length(); i++) {
            switch (s.charAt(i)) {
                case '.':
                    period = i;
                    break;

                case ' ':
                case '\f':
                case '\n':
                case '\r':
                case '\t':
                    if (period >= 0) {
                        return i;
                    }
                    break;

                default:
                    period = -1;
                    break;
            }
        }
        return -1;
    }

    /*
     * Computes the first sentence, if using a default breaker,
     * the break is returned, if not then a -1, indicating that
     * more doctree elements are required to be examined.
     *
     * BreakIterator.next points to the the start of the following sentence,
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
    int getSentenceBreak(String s, DocTree dt) {
        BreakIterator breakIterator = trees.getBreakIterator();
        if (breakIterator == null) {
            return defaultSentenceBreak(s);
        }
        breakIterator.setText(s);
        final int sbrk = breakIterator.next();
        // This is the last doctree, found the droid we are looking for
        if (dt == null) {
            return sbrk;
        }

        // If the break is well within the span of the string ie. not
        // at EOL, then we have a clear break.
        if (sbrk < s.length() - 1) {
            return sbrk;
        }

        if (isTextTree(dt)) {
            // Two adjacent text trees, a corner case, perhaps
            // produced by a tool synthesizing a doctree. In
            // this case, does the break lie within the first span,
            // then we have the droid, otherwise allow the callers
            // logic to handle the break in the adjacent doctree.
            TextTree ttnext = (TextTree) dt;
            String combined = s + ttnext.getBody();
            breakIterator.setText(combined);
            int sbrk2 = breakIterator.next();
            if (sbrk < sbrk2) {
                return sbrk;
            }
        }

        // Is the adjacent tree a sentence breaker ?
        if (isSentenceBreak(dt, false)) {
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

    boolean isSentenceBreak(javax.lang.model.element.Name tagName) {
        return sentenceBreakTags.contains(get(tagName));
    }

    boolean isSentenceBreak(DocTree dt, boolean isFirstDocTree) {
        switch (dt.getKind()) {
            case START_ELEMENT:
                    StartElementTree set = (StartElementTree)dt;
                    return !isFirstDocTree && ((DCTree) dt).pos > 1 && isSentenceBreak(set.getName());
            case END_ELEMENT:
                    EndElementTree eet = (EndElementTree)dt;
                    return !isFirstDocTree && ((DCTree) dt).pos > 1 && isSentenceBreak(eet.getName());
            default:
                return false;
        }
    }

    /*
     * Returns the position of the the first non-white space
     */
    int skipWhiteSpace(String s, int start) {
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isWhitespace(c)) {
                return i;
            }
        }
        return -1;
    }

    String removeTrailingWhitespace(String s) {
        for (int i = s.length() - 1 ; i >= 0 ; i--) {
            char ch = s.charAt(i);
            if (!Character.isWhitespace(ch)) {
                return s.substring(0, i + 1);
            }
        }
        return s;
    }
}
