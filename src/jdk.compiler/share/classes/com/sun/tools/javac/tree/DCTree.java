/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

import javax.lang.model.element.Name;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import com.sun.source.doctree.*;
import com.sun.source.util.DocTreeScanner;

import com.sun.tools.javac.parser.Tokens.Comment;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.DefinedBy;
import com.sun.tools.javac.util.DefinedBy.Api;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.Position;

import static com.sun.tools.javac.util.Position.NOPOS;

/**
 *
 * Root class for abstract syntax documentation tree nodes. It provides definitions
 * for specific tree nodes as subclasses nested inside.
 *
 * Apart from the top-level {@link DCDocComment} node, generally nodes fall into
 * three groups:
 * <ul>
 * <li>Leaf nodes, such as {@link DCIdentifier}, {@link DCText}
 * <li>Inline tag nodes, such as {@link DCLink}, {@link DCLiteral}
 * <li>Block tag nodes, such as {@link DCParam}, {@link DCThrows}
 * </ul>
 *
 * Trees are typically wide and shallow, without a significant amount of nesting.
 *
 * Nodes have various associated positions:
 * <ul>
 * <li>the {@link #pos position} of the first character that is unique to this node,
 *      and not part of any child node
 * <li>the {@link #getStartPosition start} of the range of characters for this node
 * <li>the "{@link #getPreferredPosition() preferred}" position in the range of characters
 *      for this node
 * <li>the {@link #getEndPosition() end} of the range of characters for this node
 * </ul>
 *
 * All values are relative to the beginning of the
 * {@link Elements#getDocComment comment text} in which they appear.
 * To convert a value to the position in the enclosing source text,
 * use {@link DCDocComment#getSourcePosition(int)}.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public abstract class DCTree implements DocTree {

    /**
     * The position of the first character that is unique to this node.
     * It is normally set by the methods in {@link DocTreeMaker}.
     *
     * The value is relative to the beginning of the comment text.
     * Use {@link DCDocComment#getSourcePosition(int)} to convert
     * it to a position in the source file.
     *
     * @see #getStartPosition()
     * @see #getPreferredPosition()
     * @see #getEndPosition()
     */
    public int pos;

    /**
     * {@return a {@code DiagnosticPosition} for this node}
     * The method may be used when reporting diagnostics for this node.
     *
     * @param dc the enclosing comment, used to convert comment-based positions
     *           to file-based positions
     */
    public JCDiagnostic.DiagnosticPosition pos(DCDocComment dc) {
        return createDiagnosticPosition(dc.comment, getStartPosition(), getPreferredPosition(), getEndPosition());
    }

    /**
     * {@return the start position of this tree node}
     *
     * For most nodes, this is the position of the first character that is unique to this node.
     *
     * The value is relative to the beginning of the comment text.
     * Use {@link DCDocComment#getSourcePosition(int)} to convert
     * it to a position in the source file.
     */
    public int getStartPosition() {
        return pos;
    }

    /**
     * {@return the "preferred" position of this tree node}
     *
     * It is typically the position of the first character that is unique to this node.
     * It is the position that is used for the caret in "line and caret" diagnostic messages.
     *
     * The value is relative to the beginning of the comment text.
     * Use {@link DCDocComment#getSourcePosition(int)} to convert
     * it to a position in the source file.
     */
    public int getPreferredPosition() {
        return pos;
    }

    /**
     * {@return the end position of the tree node}
     *
     * The value is typically derived in one of three ways:
     * <ul>
     * <li>computed from the start and length of "leaf" nodes, such as {@link TextTree},
     * <li>computed recursively from the end of the last child node, such as for most {@link DCBlockTag block tags}, or
     * <li>provided explicitly, such as for subtypes of {@link DCEndPosTree}
     * </ul>
     *
     * The value is relative to the beginning of the comment text.
     * Use {@link DCDocComment#getSourcePosition(int)} to convert
     * it to a position in the source file.
     */
    public int getEndPosition() {
        if (this instanceof DCEndPosTree<?> dcEndPosTree) {
            int endPos = dcEndPosTree.getEndPos();

            if (endPos != NOPOS) {
                return endPos;
            }
        }

        switch (getKind()) {
            case TEXT -> {
                DCText text = (DCText) this;
                return text.pos + text.text.length();
            }

            case ERRONEOUS -> {
                DCErroneous err = (DCErroneous) this;
                return err.pos + err.body.length();
            }

            case IDENTIFIER -> {
                DCIdentifier ident = (DCIdentifier) this;
                return ident.pos + ident.name.length();
            }

            case AUTHOR, DEPRECATED, HIDDEN, PARAM, PROVIDES, RETURN, SEE, SERIAL, SERIAL_DATA, SERIAL_FIELD, SINCE,
                    THROWS, UNKNOWN_BLOCK_TAG, USES, VERSION -> {
                DCTree last = getLastChild();

                if (last != null) {
                    int correction = (this instanceof DCParam p && p.isTypeParameter && p.getDescription().isEmpty()) ? 1 : 0;
                    return last.getEndPosition() + correction;
                }

                String name = ((BlockTagTree) this).getTagName();
                return this.pos + name.length() + 1;
            }

            case ENTITY -> {
                DCEntity endEl = (DCEntity) this;
                return endEl.pos + endEl.name.length() + 2;
            }

            case COMMENT -> {
                DCComment endEl = (DCComment) this;
                return endEl.pos + endEl.body.length();
            }

            case ATTRIBUTE -> {
                DCAttribute attr = (DCAttribute) this;
                if (attr.vkind == AttributeTree.ValueKind.EMPTY) {
                    return attr.pos + attr.name.length();
                }
                DCTree last = getLastChild();
                if (last != null) {
                    return last.getEndPosition() + (attr.vkind == AttributeTree.ValueKind.UNQUOTED ? 0 : 1);
                }
            }

            case DOC_COMMENT ->  {
                DCDocComment dc = (DCDocComment) this;
                DCTree last = getLastChild();
                return last == null ? dc.pos : last.getEndPosition();
            }

            default -> {
                DCTree last = getLastChild();
                if (last != null) {
                    return last.getEndPosition();
                }
            }
        }

        return NOPOS;
    }

    /**
     * Convert a tree to a pretty-printed string.
     */
    @Override
    public String toString() {
        StringWriter s = new StringWriter();
        try {
            new DocPretty(s).print(this);
        }
        catch (IOException e) {
            // should never happen, because StringWriter is defined
            // never to throw any IOExceptions
            throw new AssertionError(e);
        }
        return s.toString();
    }

    /**
     * {@return the last (right-most) child of this node}
     */
    private DCTree getLastChild() {
        final DCTree[] last = new DCTree[] {null};

        accept(new DocTreeScanner<Void, Void>() {
            @Override @DefinedBy(Api.COMPILER_TREE)
            public Void scan(DocTree node, Void p) {
                if (node instanceof DCTree dcTree) last[0] = dcTree;
                return null;
            }
        }, null);

        return last[0];
    }

    /**
     * {@return a diagnostic position based on the positions in a comment}
     *
     * The positions are lazily converted to file-based positions, as needed.
     *
     * @param comment the enclosing comment
     * @param start the start position in the comment
     * @param pref the preferred position in the comment
     * @param end the end position in the comment
     */
    public static JCDiagnostic.DiagnosticPosition createDiagnosticPosition(Comment comment, int start, int pref, int end) {
        return new JCDiagnostic.DiagnosticPosition() {

            @Override
            public JCTree getTree() {
                return null;
            }

            @Override
            public int getStartPosition() {
                return comment.getSourcePos(start);
            }

            @Override
            public int getPreferredPosition() {
                return comment.getSourcePos(pref);
            }

            @Override
            public int getEndPosition(EndPosTable endPosTable) {
                return comment.getSourcePos(end);
            }
        };
    }

    public abstract static class DCEndPosTree<T extends DCEndPosTree<T>> extends DCTree {

        private int endPos = NOPOS;

        public int getEndPos() {
            return endPos;
        }

        @SuppressWarnings("unchecked")
        public T setEndPos(int endPos) {
            this.endPos = endPos;
            return (T) this;
        }

    }

    public static class DCDocComment extends DCTree implements DocCommentTree {
        public final Comment comment; // required for the implicit source pos table

        public final List<DCTree> fullBody;
        public final List<DCTree> firstSentence;
        public final List<DCTree> body;
        public final List<DCTree> tags;
        public final List<DCTree> preamble;
        public final List<DCTree> postamble;

        public DCDocComment(Comment comment,
                            List<DCTree> fullBody,
                            List<DCTree> firstSentence,
                            List<DCTree> body,
                            List<DCTree> tags,
                            List<DCTree> preamble,
                            List<DCTree> postamble) {
            this.comment = comment;
            this.firstSentence = firstSentence;
            this.fullBody = fullBody;
            this.body = body;
            this.tags = tags;
            this.preamble = preamble;
            this.postamble = postamble;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.DOC_COMMENT;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitDocComment(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getFirstSentence() {
            return firstSentence;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getFullBody() {
            return fullBody;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getBody() {
            return body;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getBlockTags() {
            return tags;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getPreamble() {
            return preamble;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getPostamble() {
            return postamble;
        }

        public int getSourcePosition(int index) {
            return comment.getSourcePos(index);
        }
    }

    public abstract static class DCBlockTag extends DCTree implements BlockTagTree {
        @Override @DefinedBy(Api.COMPILER_TREE)
        public String getTagName() {
            return getKind().tagName;
        }
    }

    public abstract static class DCInlineTag extends DCEndPosTree<DCInlineTag> implements InlineTagTree {
        @Override @DefinedBy(Api.COMPILER_TREE)
        public String getTagName() {
            return getKind().tagName;
        }
    }

    public static class DCAttribute extends DCTree implements AttributeTree {
        public final Name name;
        public final ValueKind vkind;
        public final List<DCTree> value;

        DCAttribute(Name name, ValueKind vkind, List<DCTree> value) {
            Assert.check((vkind == ValueKind.EMPTY) ? (value == null) : (value != null));
            this.name = name;
            this.vkind = vkind;
            this.value = value;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.ATTRIBUTE;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitAttribute(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Name getName() {
            return name;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public ValueKind getValueKind() {
            return vkind;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<DCTree> getValue() {
            return value;
        }
    }

    public static class DCAuthor extends DCBlockTag implements AuthorTree {
        public final List<DCTree> name;

        DCAuthor(List<DCTree> name) {
            this.name = name;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.AUTHOR;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitAuthor(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getName() {
            return name;
        }
    }

    public static class DCComment extends DCTree implements CommentTree {
        public final String body;

        DCComment(String body) {
            this.body = body;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.COMMENT;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitComment(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public String getBody() {
            return body;
        }
    }

    public static class DCDeprecated extends DCBlockTag implements DeprecatedTree {
        public final List<DCTree> body;

        DCDeprecated(List<DCTree> body) {
            this.body = body;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.DEPRECATED;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitDeprecated(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getBody() {
            return body;
        }
    }

    public static class DCDocRoot extends DCInlineTag implements DocRootTree {

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.DOC_ROOT;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitDocRoot(this, d);
        }
    }

    public static class DCDocType extends DCTree implements DocTypeTree {
        public final String text;

        DCDocType(String text) {
            this.text = text;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.DOC_TYPE;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitDocType(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public String getText() {
            return text;
        }
    }

    public static class DCEndElement extends DCEndPosTree<DCEndElement> implements EndElementTree {
        public final Name name;

        DCEndElement(Name name) {
            this.name = name;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.END_ELEMENT;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitEndElement(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Name getName() {
            return name;
        }
    }

    public static class DCEntity extends DCTree implements EntityTree {
        public final Name name;

        DCEntity(Name name) {
            this.name = name;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.ENTITY;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitEntity(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Name getName() {
            return name;
        }
    }

    public static class DCErroneous extends DCTree implements ErroneousTree {
        public final String body;
        public final JCDiagnostic diag;

        private int prefPos = NOPOS;

        DCErroneous(String body, JCDiagnostic diag) {
            this.body = body;
            this.diag = diag;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.ERRONEOUS;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitErroneous(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public String getBody() {
            return body;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Diagnostic<JavaFileObject> getDiagnostic() {
            return diag;
        }

        @Override
        public int getStartPosition() {
            return pos;
        }

        @Override
        public int getPreferredPosition() {
            return prefPos == NOPOS ? pos + body.length() - 1 : prefPos;
        }

        @Override
        public int getEndPosition() {
            return pos + body.length();
        }

        public DCErroneous setPrefPos(int prefPos) {
            this.prefPos = prefPos;
            return this;
        }

    }

    public static class DCHidden extends DCBlockTag implements HiddenTree {
        public final List<DCTree> body;

        DCHidden(List<DCTree> body) {
            this.body = body;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.HIDDEN;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitHidden(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getBody() {
            return body;
        }
    }

    public static class DCIdentifier extends DCTree implements IdentifierTree {
        public final Name name;

        DCIdentifier(Name name) {
            this.name = name;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.IDENTIFIER;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitIdentifier(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Name getName() {
            return name;
        }
    }

    public static class DCIndex extends DCInlineTag implements IndexTree {
        public final DCTree term;
        public final List<DCTree> description;

        DCIndex(DCTree term, List<DCTree> description) {
            this.term = term;
            this.description = description;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.INDEX;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitIndex(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public DocTree getSearchTerm() {
            return term;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public java.util.List<? extends DocTree> getDescription() {
            return description;
        }
    }

    public static class DCInheritDoc extends DCInlineTag implements InheritDocTree {
        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.INHERIT_DOC;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitInheritDoc(this, d);
        }
    }

    public static class DCLink extends DCInlineTag implements LinkTree {
        public final Kind kind;
        public final DCReference ref;
        public final List<DCTree> label;

        DCLink(Kind kind, DCReference ref, List<DCTree> label) {
            Assert.check(kind == Kind.LINK || kind == Kind.LINK_PLAIN);
            this.kind = kind;
            this.ref = ref;
            this.label = label;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return kind;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitLink(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public ReferenceTree getReference() {
            return ref;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getLabel() {
            return label;
        }
    }

    public static class DCLiteral extends DCInlineTag implements LiteralTree {
        public final Kind kind;
        public final DCText body;

        DCLiteral(Kind kind, DCText body) {
            Assert.check(kind == Kind.CODE || kind == Kind.LITERAL);
            this.kind = kind;
            this.body = body;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return kind;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitLiteral(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public DCText getBody() {
            return body;
        }
    }

    public static class DCParam extends DCBlockTag implements ParamTree {
        public final boolean isTypeParameter;
        public final DCIdentifier name;
        public final List<DCTree> description;

        DCParam(boolean isTypeParameter, DCIdentifier name, List<DCTree> description) {
            this.isTypeParameter = isTypeParameter;
            this.name = name;
            this.description = description;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.PARAM;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitParam(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public boolean isTypeParameter() {
            return isTypeParameter;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public IdentifierTree getName() {
            return name;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getDescription() {
            return description;
        }
    }

    public static class DCProvides extends DCBlockTag implements ProvidesTree {
        public final DCReference serviceType;
        public final List<DCTree> description;

        DCProvides(DCReference serviceType, List<DCTree> description) {
            this.serviceType = serviceType;
            this.description = description;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.PROVIDES;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitProvides(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public ReferenceTree getServiceType() {
            return serviceType;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getDescription() {
            return description;
        }
    }

    public static class DCReference extends DCEndPosTree<DCReference> implements ReferenceTree {
        public final String signature;

        // The following are not directly exposed through ReferenceTree
        // use DocTrees.getElement(DocTreePath)
        public final JCTree.JCExpression moduleName;
        public final JCTree qualifierExpression;
        public final Name memberName;
        public final List<JCTree> paramTypes;


        DCReference(String signature, JCTree.JCExpression moduleName, JCTree qualExpr, Name member, List<JCTree> paramTypes) {
            this.signature = signature;
            this.moduleName = moduleName;
            qualifierExpression = qualExpr;
            memberName = member;
            this.paramTypes = paramTypes;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.REFERENCE;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitReference(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public String getSignature() {
            return signature;
        }
    }

    public static class DCReturn extends DCEndPosTree<DCReturn> implements ReturnTree {
        public final boolean inline;
        public final List<DCTree> description;

        DCReturn(boolean inline, List<DCTree> description) {
            this.inline = inline;
            this.description = description;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public String getTagName() {
            return "return";
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.RETURN;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitReturn(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public boolean isInline() {
            return inline;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getDescription() {
            return description;
        }
    }

    public static class DCSee extends DCBlockTag implements SeeTree {
        public final List<DCTree> reference;

        DCSee(List<DCTree> reference) {
            this.reference = reference;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.SEE;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitSee(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getReference() {
            return reference;
        }
    }

    public static class DCSerial extends DCBlockTag implements SerialTree {
        public final List<DCTree> description;

        DCSerial(List<DCTree> description) {
            this.description = description;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.SERIAL;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitSerial(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getDescription() {
            return description;
        }
    }

    public static class DCSerialData extends DCBlockTag implements SerialDataTree {
        public final List<DCTree> description;

        DCSerialData(List<DCTree> description) {
            this.description = description;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.SERIAL_DATA;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitSerialData(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getDescription() {
            return description;
        }
    }

    public static class DCSerialField extends DCBlockTag implements SerialFieldTree {
        public final DCIdentifier name;
        public final DCReference type;
        public final List<DCTree> description;

        DCSerialField(DCIdentifier name, DCReference type, List<DCTree> description) {
            this.description = description;
            this.name = name;
            this.type = type;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.SERIAL_FIELD;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitSerialField(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getDescription() {
            return description;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public IdentifierTree getName() {
            return name;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public ReferenceTree getType() {
            return type;
        }
    }

    public static class DCSince extends DCBlockTag implements SinceTree {
        public final List<DCTree> body;

        DCSince(List<DCTree> body) {
            this.body = body;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.SINCE;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitSince(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getBody() {
            return body;
        }
    }

    public static class DCSnippet extends DCInlineTag implements SnippetTree {
        public final List<? extends DocTree> attributes;
        public final DCText body;

        public DCSnippet(List<DCTree> attributes, DCText body) {
            this.body = body;
            this.attributes = attributes;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.SNIPPET;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitSnippet(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getAttributes() {
            return attributes;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public TextTree getBody() {
            return body;
        }
    }

    public static class DCStartElement extends DCEndPosTree<DCStartElement> implements StartElementTree {
        public final Name name;
        public final List<DCTree> attrs;
        public final boolean selfClosing;

        DCStartElement(Name name, List<DCTree> attrs, boolean selfClosing) {
            this.name = name;
            this.attrs = attrs;
            this.selfClosing = selfClosing;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.START_ELEMENT;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitStartElement(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Name getName() {
            return name;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getAttributes() {
            return attrs;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public boolean isSelfClosing() {
            return selfClosing;
        }
    }

    public static class DCSummary extends DCInlineTag implements SummaryTree {
        public final List<DCTree> summary;

        DCSummary(List<DCTree> summary) {
            this.summary = summary;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.SUMMARY;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitSummary(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getSummary() {
            return summary;
        }
    }

    public static class DCSystemProperty extends DCInlineTag implements SystemPropertyTree {
        public final Name propertyName;

        DCSystemProperty(Name propertyName) {
            this.propertyName = propertyName;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.SYSTEM_PROPERTY;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitSystemProperty(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Name getPropertyName() {
            return propertyName;
        }
    }

    public static class DCText extends DCTree implements TextTree {
        public final String text;

        DCText(String text) {
            this.text = text;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.TEXT;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitText(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public String getBody() {
            return text;
        }
    }

    public static class DCThrows extends DCBlockTag implements ThrowsTree {
        public final Kind kind;
        public final DCReference name;
        public final List<DCTree> description;

        DCThrows(Kind kind, DCReference name, List<DCTree> description) {
            Assert.check(kind == Kind.EXCEPTION || kind == Kind.THROWS);
            this.kind = kind;
            this.name = name;
            this.description = description;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return kind;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitThrows(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public ReferenceTree getExceptionName() {
            return name;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getDescription() {
            return description;
        }
    }

    public static class DCUnknownBlockTag extends DCBlockTag implements UnknownBlockTagTree {
        public final Name name;
        public final List<DCTree> content;

        DCUnknownBlockTag(Name name, List<DCTree> content) {
            this.name = name;
            this.content = content;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.UNKNOWN_BLOCK_TAG;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitUnknownBlockTag(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public String getTagName() {
            return name.toString();
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getContent() {
            return content;
        }
    }

    public static class DCUnknownInlineTag extends DCInlineTag implements UnknownInlineTagTree {
        public final Name name;
        public final List<DCTree> content;

        DCUnknownInlineTag(Name name, List<DCTree> content) {
            this.name = name;
            this.content = content;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.UNKNOWN_INLINE_TAG;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitUnknownInlineTag(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public String getTagName() {
            return name.toString();
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getContent() {
            return content;
        }
    }

    public static class DCUses extends DCBlockTag implements UsesTree {
        public final DCReference serviceType;
        public final List<DCTree> description;

        DCUses(DCReference serviceType, List<DCTree> description) {
            this.serviceType = serviceType;
            this.description = description;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.USES;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitUses(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public ReferenceTree getServiceType() {
            return serviceType;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getDescription() {
            return description;
        }
    }

    public static class DCValue extends DCInlineTag implements ValueTree {
        public final DCReference ref;

        DCValue(DCReference ref) {
            this.ref = ref;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.VALUE;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitValue(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public ReferenceTree getReference() {
            return ref;
        }
    }

    public static class DCVersion extends DCBlockTag implements VersionTree {
        public final List<DCTree> body;

        DCVersion(List<DCTree> body) {
            this.body = body;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public Kind getKind() {
            return Kind.VERSION;
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitVersion(this, d);
        }

        @Override @DefinedBy(Api.COMPILER_TREE)
        public List<? extends DocTree> getBody() {
            return body;
        }
    }

}
