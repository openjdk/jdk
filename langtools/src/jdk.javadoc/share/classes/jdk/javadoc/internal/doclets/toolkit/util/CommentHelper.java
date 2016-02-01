/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import com.sun.source.doctree.AttributeTree;
import com.sun.source.doctree.AttributeTree.ValueKind;
import com.sun.source.doctree.AuthorTree;
import com.sun.source.doctree.BlockTagTree;
import com.sun.source.doctree.CommentTree;
import com.sun.source.doctree.DeprecatedTree;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.EndElementTree;
import com.sun.source.doctree.EntityTree;
import com.sun.source.doctree.IdentifierTree;
import com.sun.source.doctree.InlineTagTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.LiteralTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.doctree.ReferenceTree;
import com.sun.source.doctree.ReturnTree;
import com.sun.source.doctree.SeeTree;
import com.sun.source.doctree.SerialDataTree;
import com.sun.source.doctree.SerialFieldTree;
import com.sun.source.doctree.SerialTree;
import com.sun.source.doctree.SinceTree;
import com.sun.source.doctree.StartElementTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.doctree.ThrowsTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import com.sun.source.doctree.ValueTree;
import com.sun.source.doctree.VersionTree;
import com.sun.source.util.DocTreePath;
import com.sun.source.util.DocTrees;
import com.sun.source.util.SimpleDocTreeVisitor;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.util.DefinedBy;
import com.sun.tools.javac.util.DefinedBy.Api;
import jdk.javadoc.internal.doclets.toolkit.Configuration;

import static com.sun.source.doctree.DocTree.Kind.*;

/**
 *  A utility class.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class CommentHelper {
    public final TreePath path;
    public final DocCommentTree dctree;
    public final Element element;
    private Element overriddenElement;

    public static final String SPACER = " ";

    public CommentHelper(Configuration configuration, Element element, TreePath path, DocCommentTree dctree) {
        //this.configuration = configuration;
        this.element = element;
        this.path = path;
        this.dctree = dctree;
    }

    public void setOverrideElement(Element ove) {
        if (this.element == ove) {
            throw new AssertionError("cannot set given element as overriden element");
        }
        overriddenElement = ove;
    }

    @SuppressWarnings("fallthrough")
    public String getTagName(DocTree dtree) {
        switch (dtree.getKind()) {
            case AUTHOR:
            case DEPRECATED:
            case PARAM:
            case RETURN:
            case SEE:
            case SERIAL_DATA:
            case SERIAL_FIELD:
            case THROWS:
            case UNKNOWN_BLOCK_TAG:
            case VERSION:
                return ((BlockTagTree)dtree).getTagName();
            case UNKNOWN_INLINE_TAG:
                return ((InlineTagTree)dtree).getTagName();
            case ERRONEOUS:
                return "erroneous";
            default:
                return dtree.getKind().tagName;
        }
    }

    public boolean isTypeParameter(DocTree dtree) {
        if (dtree.getKind() == PARAM) {
            return ((ParamTree)dtree).isTypeParameter();
        }
        return false;
    }

    public String getParameterName(DocTree dtree) {
        if (dtree.getKind() == PARAM) {
            return ((ParamTree) dtree).getName().toString();
        } else {
            return null;
        }
    }

    Element getElement(Configuration c, ReferenceTree rtree) {
        // likely a synthesized tree
        if (path == null) {
            TypeMirror symbol = c.utils.getSymbol(rtree.getSignature());
            if (symbol == null) {
                return null;
            }
            return  c.root.getTypeUtils().asElement(symbol);
        }
        // case A: the element contains no comments associated and
        // the comments need to be copied from ancestor
        // case B: the element has @inheritDoc, then the ancestral comment
        // as appropriate has to be copied over.

        // Case A.
        if (dctree == null && overriddenElement != null) {
            CommentHelper ovch = c.utils.getCommentHelper(overriddenElement);
            return ovch.getElement(c, rtree);
        }
        if (dctree == null) {
            return null;
        }
        DocTreePath docTreePath = DocTreePath.getPath(path, dctree, rtree);
        if (docTreePath == null) {
            // Case B.
            if (overriddenElement != null) {
                CommentHelper ovch = c.utils.getCommentHelper(overriddenElement);
                return ovch.getElement(c, rtree);
            }
            return null;
        }
        DocTrees doctrees = c.root.getDocTrees();
        return doctrees.getElement(docTreePath);
    }

    public Element getException(Configuration c, DocTree dtree) {
        if (dtree.getKind() == THROWS || dtree.getKind() == EXCEPTION) {
            ThrowsTree tt = (ThrowsTree)dtree;
            ReferenceTree exceptionName = tt.getExceptionName();
            return getElement(c, exceptionName);
        }
        return null;
    }

    public List<? extends DocTree> getDescription(Configuration c, DocTree dtree) {
        return getTags(c, dtree);
    }

    public String getText(List<? extends DocTree> list) {
        StringBuilder sb = new StringBuilder();
        for (DocTree dt : list) {
            sb.append(getText0(dt));
        }
        return sb.toString();
    }

    public String getText(DocTree dt) {
        return getText0(dt).toString();
    }

    private StringBuilder getText0(DocTree dt) {
        final StringBuilder sb = new StringBuilder();
        new SimpleDocTreeVisitor<Void, Void>() {
            @Override @DefinedBy(Api.COMPILER_TREE)
            public Void visitAttribute(AttributeTree node, Void p) {
                sb.append(SPACER).append(node.getName());
                if (node.getValueKind() == ValueKind.EMPTY) {
                    return null;
                }

                sb.append("=");
                String quote;
                switch (node.getValueKind()) {
                    case DOUBLE:
                        quote = "\"";
                        break;
                    case SINGLE:
                        quote = "\'";
                        break;
                    default:
                        quote = "";
                        break;
                }
                sb.append(quote);
                node.getValue().stream().forEach((dt) -> {
                    dt.accept(this, null);
                });
                sb.append(quote);
                return null;
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public Void visitEndElement(EndElementTree node, Void p) {
                sb.append("</")
                        .append(node.getName())
                        .append(">");
                return null;
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public Void visitEntity(EntityTree node, Void p) {
                sb.append(node.toString());
                return null;
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public Void visitLink(LinkTree node, Void p) {
                if (node.getReference() == null) {
                    return null;
                }

                node.getReference().accept(this, null);
                node.getLabel().stream().forEach((dt) -> {
                    dt.accept(this, null);
                });
                return null;
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public Void visitLiteral(LiteralTree node, Void p) {
                if (node.getKind() == CODE) {
                    sb.append("<").append(node.getKind().tagName).append(">");
                }
                sb.append(node.getBody().toString());
                if (node.getKind() == CODE) {
                    sb.append("</").append(node.getKind().tagName).append(">");
                }
                return null;
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public Void visitReference(ReferenceTree node, Void p) {
                sb.append(node.getSignature());
                return null;
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public Void visitSee(SeeTree node, Void p) {
                node.getReference().stream().forEach((dt) -> {
                    dt.accept(this, null);
                });
                return null;
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public Void visitSerial(SerialTree node, Void p) {
                node.getDescription().stream().forEach((dt) -> {
                    dt.accept(this, null);
                });
                return null;
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public Void visitStartElement(StartElementTree node, Void p) {
                sb.append("<");
                sb.append(node.getName());
                node.getAttributes().stream().forEach((dt) -> {
                    dt.accept(this, null);
                });
                sb.append((node.isSelfClosing() ? "/>" : ">"));
                return null;
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public Void visitText(TextTree node, Void p) {
                sb.append(node.getBody());
                return null;
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public Void visitUnknownBlockTag(UnknownBlockTagTree node, Void p) {
                node.getContent().stream().forEach((dt) -> {
                    dt.accept(this, null);
                });
                return null;
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public Void visitValue(ValueTree node, Void p) {
                return node.getReference().accept(this, null);
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            protected Void defaultAction(DocTree node, Void p) {
                sb.append(node.toString());
                return null;
            }
        }.visit(dt, null);
        return sb;
    }

    public String getLabel(Configuration c, DocTree dtree) {
        return new SimpleDocTreeVisitor<String, Void>() {
            @Override @DefinedBy(Api.COMPILER_TREE)
            public String visitLink(LinkTree node, Void p) {
                StringBuilder sb = new StringBuilder();
                node.getLabel().stream().forEach((dt) -> {
                    sb.append(getText(dt));
                });
                return sb.toString();
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public String visitSee(SeeTree node, Void p) {
                StringBuilder sb = new StringBuilder();
                node.getReference().stream().filter((dt) -> (c.utils.isText(dt))).forEach((dt) -> {
                    sb.append(((TextTree)dt).getBody());
                });
                return sb.toString();
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            protected String defaultAction(DocTree node, Void p) {
                return "";
            }
        }.visit(dtree, null);
    }

    public TypeElement getReferencedClass(Configuration c, DocTree dtree) {
        Element e = getReferencedElement(c, dtree);
        if (e == null) {
            return null;
        } else if (c.utils.isTypeElement(e)) {
            return (TypeElement) e;
        } else if (!c.utils.isPackage(e)) {
            return c.utils.getEnclosingTypeElement(e);
        }
        return null;
    }

    public String getReferencedClassName(Configuration c, DocTree dtree) {
        Element e = getReferencedClass(c, dtree);
        if (e != null) {
            return c.utils.isTypeElement(e) ? c.utils.getSimpleName(e) : null;
        }
        String s = getReferencedSignature(dtree);
        if (s == null) {
            return null;
        }
        int n = s.indexOf("#");
        return (n == -1) ? s : s.substring(0, n);
    }

    public Element getReferencedMember(Configuration c, DocTree dtree) {
        Element e = getReferencedElement(c, dtree);
        if (e == null) {
            return null;
        }
        return (c.utils.isExecutableElement(e) || c.utils.isVariableElement(e)) ? e : null;
    }

    public String getReferencedMemberName(DocTree dtree) {
        String s = getReferencedSignature(dtree);
        if (s == null) {
            return null;
        }
        int n = s.indexOf("#");
        return (n == -1) ? null : s.substring(n + 1);
    }

    public String getReferencedMemberName(Configuration c, Element e) {
        if (e == null) {
            return null;
        }
        return c.utils.isExecutableElement(e)
                ? c.utils.getSimpleName(e) + c.utils.makeSignature((ExecutableElement) e, true, true)
                : c.utils.getSimpleName(e);
    }

    public PackageElement getReferencedPackage(Configuration c, DocTree dtree) {
        Element e = getReferencedElement(c, dtree);
        if (e != null) {
            return c.utils.containingPackage(e);
        }
        return null;
    }

    public List<? extends DocTree> getFirstSentenceTrees(Configuration c, List<? extends DocTree> body) {
        List<DocTree> firstSentence = c.root.getDocTrees().getFirstSentence(body);
        return firstSentence;
    }

    public List<? extends DocTree> getFirstSentenceTrees(Configuration c, DocTree dtree) {
        return getFirstSentenceTrees(c, getBody(c, dtree));
    }

    private Element getReferencedElement(Configuration c, DocTree dtree) {
        return new SimpleDocTreeVisitor<Element, Void>() {
            @Override @DefinedBy(Api.COMPILER_TREE)
            public Element visitSee(SeeTree node, Void p) {
                for (DocTree dt : node.getReference()) {
                    return visit(dt, null);
                }
                return null;
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public Element visitLink(LinkTree node, Void p) {
                return visit(node.getReference(), null);
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public Element visitValue(ValueTree node, Void p) {
                return visit(node.getReference(), null);
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public Element visitReference(ReferenceTree node, Void p) {
                return getElement(c, node);
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public Element visitSerialField(SerialFieldTree node, Void p) {
                return visit(node.getType(), null);
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            protected Element defaultAction(DocTree node, Void p) {
               return null;
            }
        }.visit(dtree, null);
    }

    public  String getReferencedSignature(DocTree dtree) {
        return new SimpleDocTreeVisitor<String, Void>() {
            @Override @DefinedBy(Api.COMPILER_TREE)
            public String visitSee(SeeTree node, Void p) {
                for (DocTree dt : node.getReference()) {
                    return visit(dt, null);
                }
                return null;
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public String visitLink(LinkTree node, Void p) {
                return visit(node.getReference(), null);
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public String visitValue(ValueTree node, Void p) {
                return visit(node.getReference(), null);
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public String visitReference(ReferenceTree node, Void p) {
                return node.getSignature();
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public String visitSerialField(SerialFieldTree node, Void p) {
                return visit(node.getType(), null);
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            protected String defaultAction(DocTree node, Void p) {
               return null;
            }
        }.visit(dtree, null);
    }

    public List<? extends DocTree> getReference(DocTree dtree) {
        return dtree.getKind() == SEE ? ((SeeTree)dtree).getReference() : null;
    }

    public ReferenceTree getExceptionName(DocTree dtree) {
        return (dtree.getKind() == THROWS || dtree.getKind() == EXCEPTION)
                ? ((ThrowsTree)dtree).getExceptionName()
                : null;
    }

    public IdentifierTree getName(DocTree dtree) {
        switch (dtree.getKind()) {
            case PARAM:
                return ((ParamTree)dtree).getName();
            case SERIAL_FIELD:
                return ((SerialFieldTree)dtree).getName();
            default:
                return null;
            }
    }

    public List<? extends DocTree> getTags(Configuration c, DocTree dtree) {
        return new SimpleDocTreeVisitor<List<? extends DocTree>, Void>() {
            List<? extends DocTree> asList(String content) {
                List<DocTree> out = new ArrayList<>();
                out.add((TextTree)c.cmtUtils.makeTextTree(content));
                return out;
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public List<? extends DocTree> visitAuthor(AuthorTree node, Void p) {
                return node.getName();
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public List<? extends DocTree> visitComment(CommentTree node, Void p) {
                return asList(node.getBody());
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public List<? extends DocTree> visitDeprecated(DeprecatedTree node, Void p) {
                return node.getBody();
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public List<? extends DocTree> visitDocComment(DocCommentTree node, Void p) {
                return node.getBody();
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public List<? extends DocTree> visitLiteral(LiteralTree node, Void p) {
                return asList(node.getBody().getBody());
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public List<? extends DocTree> visitSince(SinceTree node, Void p) {
                return node.getBody();
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public List<? extends DocTree> visitText(TextTree node, Void p) {
                return asList(node.getBody());
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public List<? extends DocTree> visitVersion(VersionTree node, Void p) {
                return node.getBody();
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public List<? extends DocTree> visitParam(ParamTree node, Void p) {
               return node.getDescription();
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public List<? extends DocTree> visitReturn(ReturnTree node, Void p) {
                return node.getDescription();
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public List<? extends DocTree> visitSee(SeeTree node, Void p) {
                return node.getReference();
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public List<? extends DocTree> visitSerial(SerialTree node, Void p) {
                return node.getDescription();
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public List<? extends DocTree> visitSerialData(SerialDataTree node, Void p) {
                return node.getDescription();
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public List<? extends DocTree> visitSerialField(SerialFieldTree node, Void p) {
                return node.getDescription();
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public List<? extends DocTree> visitThrows(ThrowsTree node, Void p) {
                 return node.getDescription();
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            public List<? extends DocTree> visitUnknownBlockTag(UnknownBlockTagTree node, Void p) {
                return node.getContent();
            }

            @Override @DefinedBy(Api.COMPILER_TREE)
            protected List<? extends DocTree> defaultAction(DocTree node, Void p) {
               return Collections.emptyList();
            }
        }.visit(dtree, null);
    }

    public List<? extends DocTree> getBody(Configuration c, DocTree dtree) {
        return getTags(c, dtree);
    }

    public ReferenceTree getType(DocTree dtree) {
        if (dtree.getKind() == SERIAL_FIELD) {
            return ((SerialFieldTree)dtree).getType();
        } else {
            return null;
        }
    }

    public DocTreePath getDocTreePath(DocTree dtree) {
        if (path == null || dctree == null || dtree == null)
            return null;
        return DocTreePath.getPath(path, dctree, dtree);
    }

    public Element getOverriddenElement() {
        return overriddenElement;
    }


    /**
     * For debugging purposes only. Do not rely on this for other things.
     * @return a string representation.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("CommentHelper{" + "path=" + path + ", dctree=" + dctree);
        sb.append(", element=");
        sb.append(element.getEnclosingElement());
        sb.append("::");
        sb.append(element);
        sb.append(", overriddenElement=");
        if (overriddenElement != null) {
            sb.append(overriddenElement.getEnclosingElement());
            sb.append("::");
            sb.append(overriddenElement);
        } else {
            sb.append("<none>");
        }
        sb.append('}');
        return sb.toString();
    }
}
