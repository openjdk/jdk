/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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


import javax.tools.Diagnostic;

import com.sun.source.doctree.*;
import com.sun.tools.javac.parser.Tokens.Comment;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.DiagnosticSource;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.JCDiagnostic.SimpleDiagnosticPosition;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Position;
import java.io.IOException;
import java.io.StringWriter;
import javax.tools.JavaFileObject;

/**
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public abstract class DCTree implements DocTree {

    /**
     * The position in the comment string.
     * Use {@link #getSourcePosition getSourcePosition} to convert
     * it to a position in the source file.
     *
     * TODO: why not simply translate all these values into
     * source file positions? Is it useful to have string-offset
     * positions as well?
     */
    public int pos;

    public long getSourcePosition(DCDocComment dc) {
        return dc.comment.getSourcePos(pos);
    }

    public JCDiagnostic.DiagnosticPosition pos(DCDocComment dc) {
        return new SimpleDiagnosticPosition(dc.comment.getSourcePos(pos));
    }

    /** Convert a tree to a pretty-printed string. */
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

    public static abstract class DCEndPosTree<T extends DCEndPosTree<T>> extends DCTree {

        private int endPos = Position.NOPOS;

        public int getEndPos(DCDocComment dc) {
            return dc.comment.getSourcePos(endPos);
        }

        @SuppressWarnings("unchecked")
        public T setEndPos(int endPos) {
            this.endPos = endPos;
            return (T) this;
        }

    }

    public static class DCDocComment extends DCTree implements DocCommentTree {
        public final Comment comment; // required for the implicit source pos table

        public final List<DCTree> firstSentence;
        public final List<DCTree> body;
        public final List<DCTree> tags;

        public DCDocComment(Comment comment,
                List<DCTree> firstSentence, List<DCTree> body, List<DCTree> tags) {
            this.comment = comment;
            this.firstSentence = firstSentence;
            this.body = body;
            this.tags = tags;
        }

        public Kind getKind() {
            return Kind.DOC_COMMENT;
        }

        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitDocComment(this, d);
        }

        public List<? extends DocTree> getFirstSentence() {
            return firstSentence;
        }

        public List<? extends DocTree> getBody() {
            return body;
        }

        public List<? extends DocTree> getBlockTags() {
            return tags;
        }

    }

    public static abstract class DCBlockTag extends DCTree implements BlockTagTree {
        public String getTagName() {
            return getKind().tagName;
        }
    }

    public static abstract class DCInlineTag extends DCEndPosTree<DCInlineTag> implements InlineTagTree {
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

        @Override
        public Kind getKind() {
            return Kind.ATTRIBUTE;
        }

        @Override
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitAttribute(this, d);
        }

        @Override
        public Name getName() {
            return name;
        }

        @Override
        public ValueKind getValueKind() {
            return vkind;
        }

        @Override
        public List<DCTree> getValue() {
            return value;
        }
    }

    public static class DCAuthor extends DCBlockTag implements AuthorTree {
        public final List<DCTree> name;

        DCAuthor(List<DCTree> name) {
            this.name = name;
        }

        @Override
        public Kind getKind() {
            return Kind.AUTHOR;
        }

        @Override
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitAuthor(this, d);
        }

        @Override
        public List<? extends DocTree> getName() {
            return name;
        }
    }

    public static class DCComment extends DCTree implements CommentTree {
        public final String body;

        DCComment(String body) {
            this.body = body;
        }

        @Override
        public Kind getKind() {
            return Kind.COMMENT;
        }

        @Override
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitComment(this, d);
        }

        @Override
        public String getBody() {
            return body;
        }
    }

    public static class DCDeprecated extends DCBlockTag implements DeprecatedTree {
        public final List<DCTree> body;

        DCDeprecated(List<DCTree> body) {
            this.body = body;
        }

        @Override
        public Kind getKind() {
            return Kind.DEPRECATED;
        }

        @Override
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitDeprecated(this, d);
        }

        @Override
        public List<? extends DocTree> getBody() {
            return body;
        }
    }

    public static class DCDocRoot extends DCInlineTag implements DocRootTree {

        @Override
        public Kind getKind() {
            return Kind.DOC_ROOT;
        }

        @Override
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitDocRoot(this, d);
        }
    }

    public static class DCEndElement extends DCTree implements EndElementTree {
        public final Name name;

        DCEndElement(Name name) {
            this.name = name;
        }

        @Override
        public Kind getKind() {
            return Kind.END_ELEMENT;
        }

        @Override
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitEndElement(this, d);
        }

        @Override
        public Name getName() {
            return name;
        }
    }

    public static class DCEntity extends DCTree implements EntityTree {
        public final Name name;

        DCEntity(Name name) {
            this.name = name;
        }

        @Override
        public Kind getKind() {
            return Kind.ENTITY;
        }

        @Override
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitEntity(this, d);
        }

        @Override
        public Name getName() {
            return name;
        }
    }

    public static class DCErroneous extends DCTree implements ErroneousTree, JCDiagnostic.DiagnosticPosition {
        public final String body;
        public final JCDiagnostic diag;

        DCErroneous(String body, JCDiagnostic.Factory diags, DiagnosticSource diagSource, String code, Object... args) {
            this.body = body;
            this.diag = diags.error(diagSource, this, code, args);
        }

        @Override
        public Kind getKind() {
            return Kind.ERRONEOUS;
        }

        @Override
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitErroneous(this, d);
        }

        @Override
        public String getBody() {
            return body;
        }

        @Override
        public Diagnostic<JavaFileObject> getDiagnostic() {
            return diag;
        }

        @Override
        public JCTree getTree() {
            return null;
        }

        @Override
        public int getStartPosition() {
            return pos;
        }

        @Override
        public int getPreferredPosition() {
            return pos + body.length() - 1;
        }

        @Override
        public int getEndPosition(EndPosTable endPosTable) {
            return pos + body.length();
        }

    }

    public static class DCIdentifier extends DCTree implements IdentifierTree {
        public final Name name;

        DCIdentifier(Name name) {
            this.name = name;
        }

        @Override
        public Kind getKind() {
            return Kind.IDENTIFIER;
        }

        @Override
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitIdentifier(this, d);
        }

        @Override
        public Name getName() {
            return name;
        }
    }

    public static class DCInheritDoc extends DCInlineTag implements InheritDocTree {
        @Override
        public Kind getKind() {
            return Kind.INHERIT_DOC;
        }

        @Override
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

        @Override
        public Kind getKind() {
            return kind;
        }

        @Override
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitLink(this, d);
        }

        @Override
        public ReferenceTree getReference() {
            return ref;
        }

        @Override
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

        @Override
        public Kind getKind() {
            return kind;
        }

        @Override
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitLiteral(this, d);
        }

        @Override
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

        @Override
        public Kind getKind() {
            return Kind.PARAM;
        }

        @Override
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitParam(this, d);
        }

        @Override
        public boolean isTypeParameter() {
            return isTypeParameter;
        }

        @Override
        public IdentifierTree getName() {
            return name;
        }

        @Override
        public List<? extends DocTree> getDescription() {
            return description;
        }
    }

    public static class DCReference extends DCEndPosTree<DCReference> implements ReferenceTree {
        public final String signature;

        // The following are not directly exposed through ReferenceTree
        // use DocTrees.getElement(TreePath,ReferenceTree)
        public final JCTree qualifierExpression;
        public final Name memberName;
        public final List<JCTree> paramTypes;


        DCReference(String signature, JCTree qualExpr, Name member, List<JCTree> paramTypes) {
            this.signature = signature;
            qualifierExpression = qualExpr;
            memberName = member;
            this.paramTypes = paramTypes;
        }

        @Override
        public Kind getKind() {
            return Kind.REFERENCE;
        }

        @Override
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitReference(this, d);
        }

        @Override
        public String getSignature() {
            return signature;
        }
    }

    public static class DCReturn extends DCBlockTag implements ReturnTree {
        public final List<DCTree> description;

        DCReturn(List<DCTree> description) {
            this.description = description;
        }

        @Override
        public Kind getKind() {
            return Kind.RETURN;
        }

        @Override
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitReturn(this, d);
        }

        @Override
        public List<? extends DocTree> getDescription() {
            return description;
        }
    }

    public static class DCSee extends DCBlockTag implements SeeTree {
        public final List<DCTree> reference;

        DCSee(List<DCTree> reference) {
            this.reference = reference;
        }

        @Override
        public Kind getKind() {
            return Kind.SEE;
        }

        @Override
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitSee(this, d);
        }

        @Override
        public List<? extends DocTree> getReference() {
            return reference;
        }
    }

    public static class DCSerial extends DCBlockTag implements SerialTree {
        public final List<DCTree> description;

        DCSerial(List<DCTree> description) {
            this.description = description;
        }

        @Override
        public Kind getKind() {
            return Kind.SERIAL;
        }

        @Override
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitSerial(this, d);
        }

        @Override
        public List<? extends DocTree> getDescription() {
            return description;
        }
    }

    public static class DCSerialData extends DCBlockTag implements SerialDataTree {
        public final List<DCTree> description;

        DCSerialData(List<DCTree> description) {
            this.description = description;
        }

        @Override
        public Kind getKind() {
            return Kind.SERIAL_DATA;
        }

        @Override
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitSerialData(this, d);
        }

        @Override
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

        @Override
        public Kind getKind() {
            return Kind.SERIAL_FIELD;
        }

        @Override
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitSerialField(this, d);
        }

        @Override
        public List<? extends DocTree> getDescription() {
            return description;
        }

        @Override
        public IdentifierTree getName() {
            return name;
        }

        @Override
        public ReferenceTree getType() {
            return type;
        }
    }

    public static class DCSince extends DCBlockTag implements SinceTree {
        public final List<DCTree> body;

        DCSince(List<DCTree> body) {
            this.body = body;
        }

        @Override
        public Kind getKind() {
            return Kind.SINCE;
        }

        @Override
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitSince(this, d);
        }

        @Override
        public List<? extends DocTree> getBody() {
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

        @Override
        public Kind getKind() {
            return Kind.START_ELEMENT;
        }

        @Override
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitStartElement(this, d);
        }

        @Override
        public Name getName() {
            return name;
        }

        @Override
        public List<? extends DocTree> getAttributes() {
            return attrs;
        }

        @Override
        public boolean isSelfClosing() {
            return selfClosing;
        }
    }

    public static class DCText extends DCTree implements TextTree {
        public final String text;

        DCText(String text) {
            this.text = text;
        }

        @Override
        public Kind getKind() {
            return Kind.TEXT;
        }

        @Override
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitText(this, d);
        }

        @Override
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

        @Override
        public Kind getKind() {
            return kind;
        }

        @Override
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitThrows(this, d);
        }

        @Override
        public ReferenceTree getExceptionName() {
            return name;
        }

        @Override
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

        @Override
        public Kind getKind() {
            return Kind.UNKNOWN_BLOCK_TAG;
        }

        @Override
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitUnknownBlockTag(this, d);
        }

        @Override
        public String getTagName() {
            return name.toString();
        }

        @Override
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

        @Override
        public Kind getKind() {
            return Kind.UNKNOWN_INLINE_TAG;
        }

        @Override
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitUnknownInlineTag(this, d);
        }

        @Override
        public String getTagName() {
            return name.toString();
        }

        @Override
        public List<? extends DocTree> getContent() {
            return content;
        }
    }

    public static class DCValue extends DCInlineTag implements ValueTree {
        public final DCReference ref;

        DCValue(DCReference ref) {
            this.ref = ref;
        }

        @Override
        public Kind getKind() {
            return Kind.VALUE;
        }

        @Override
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitValue(this, d);
        }

        @Override
        public ReferenceTree getReference() {
            return ref;
        }
    }

    public static class DCVersion extends DCBlockTag implements VersionTree {
        public final List<DCTree> body;

        DCVersion(List<DCTree> body) {
            this.body = body;
        }

        @Override
        public Kind getKind() {
            return Kind.VERSION;
        }

        @Override
        public <R, D> R accept(DocTreeVisitor<R, D> v, D d) {
            return v.visitVersion(this, d);
        }

        @Override
        public List<? extends DocTree> getBody() {
            return body;
        }
    }

}
