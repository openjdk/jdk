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

package com.sun.source.doctree;


/**
 * A visitor of trees, in the style of the visitor design pattern.
 * Classes implementing this interface are used to operate
 * on a tree when the kind of tree is unknown at compile time.
 * When a visitor is passed to a tree's {@link DocTree#accept
 * accept} method, the <code>visit<i>Xyz</i></code> method most applicable
 * to that tree is invoked.
 *
 * <p> Classes implementing this interface may or may not throw a
 * {@code NullPointerException} if the additional parameter {@code p}
 * is {@code null}; see documentation of the implementing class for
 * details.
 *
 * <p> <b>WARNING:</b> It is possible that methods will be added to
 * this interface to accommodate new, currently unknown, doc comment
 * structures added to future versions of the Java programming
 * language.  Therefore, visitor classes directly implementing this
 * interface may be source incompatible with future versions of the
 * platform.
 *
 * @param <R> the return type of this visitor's methods.  Use {@link
 *            Void} for visitors that do not need to return results.
 * @param <P> the type of the additional parameter to this visitor's
 *            methods.  Use {@code Void} for visitors that do not need an
 *            additional parameter.
 *
 * @since 1.8
 */
public interface DocTreeVisitor<R,P> {

    /**
     * Visits an {@code AttributeTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitAttribute(AttributeTree node, P p);

    /**
     * Visits an {@code AuthorTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitAuthor(AuthorTree node, P p);

    /**
     * Visits a {@code CommentTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitComment(CommentTree node, P p);

    /**
     * Visits a {@code DeprecatedTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitDeprecated(DeprecatedTree node, P p);

    /**
     * Visits a {@code DocCommentTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitDocComment(DocCommentTree node, P p);

    /**
     * Visits a {@code DocRootTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitDocRoot(DocRootTree node, P p);

    /**
     * Visits a {@code DocTypeTree} node.
     *
     * @implSpec Visits the provided {@code DocTypeTree} node
     * by calling {@code visitOther(node, p)}.
     *
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     * @since 10
     */
    default R visitDocType(DocTypeTree node, P p) {
        return visitOther(node, p);
    }

    /**
     * Visits an {@code EndElementTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitEndElement(EndElementTree node, P p);

    /**
     * Visits an {@code EntityTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitEntity(EntityTree node, P p);

    /**
     * Visits an {@code ErroneousTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitErroneous(ErroneousTree node, P p);

    /**
     * Visits an {@code EscapeTree} node.
     *
     * @implSpec Visits the provided {@code EscapeTree} node
     * by calling {@code visitOther(node, p)}.
     *
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     *
     * @since 21
     */
    default R visitEscape(EscapeTree node, P p)  {
        return visitOther(node, p);
    }

    /**
     * Visits a {@code HiddenTree} node.
     *
     * @implSpec Visits the provided {@code HiddenTree} node
     * by calling {@code visitOther(node, p)}.
     *
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     *
     * @since 9
     */
    default R visitHidden(HiddenTree node, P p)  {
        return visitOther(node, p);
    }

    /**
     * Visits an {@code IdentifierTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitIdentifier(IdentifierTree node, P p);

    /**
     * Visits an {@code IndexTree} node.
     *
     * @implSpec Visits the provided {@code IndexTree} node
     * by calling {@code visitOther(node, p)}.
     *
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     *
     * @since 9
     */
    default R visitIndex(IndexTree node, P p) {
        return visitOther(node, p);
    }

    /**
     * Visits an {@code InheritDocTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitInheritDoc(InheritDocTree node, P p);

    /**
     * Visits a {@code LinkTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitLink(LinkTree node, P p);

    /**
     * Visits an {@code LiteralTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitLiteral(LiteralTree node, P p);

    /**
     * Visits a {@code ParamTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitParam(ParamTree node, P p);

    /**
     * Visits a {@code ProvidesTree} node.
     *
     * @implSpec Visits the provided {@code ProvidesTree} node
     * by calling {@code visitOther(node, p)}.
     *
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     *
     * @since 9
     */
    default R visitProvides(ProvidesTree node, P p) {
        return visitOther(node, p);
    }

    /**
     * Visits a {@code RawTextTree} node.
     *
     * @implSpec Visits the provided {@code RawTextTree} node
     * by calling {@code visitOther(node, p)}.
     *
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     *
     * @since 23
     */
    default R visitRawText(RawTextTree node, P p) {
        return visitOther(node, p);
    }

    /**
     * Visits a {@code ReferenceTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitReference(ReferenceTree node, P p);

    /**
     * Visits a {@code ReturnTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitReturn(ReturnTree node, P p);

    /**
     * Visits a {@code SeeTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitSee(SeeTree node, P p);

    /**
     * Visits a {@code SerialTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitSerial(SerialTree node, P p);

    /**
     * Visits a {@code SerialDataTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitSerialData(SerialDataTree node, P p);

    /**
     * Visits a {@code SerialFieldTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitSerialField(SerialFieldTree node, P p);

    /**
     * Visits a {@code SinceTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitSince(SinceTree node, P p);

    /**
     * Visits a {@code SnippetTree} node.
     *
     * @implSpec Visits the provided {@code SnippetTree} node
     * by calling {@code visitOther(node, p)}.
     *
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     * @since 18
     */
    default R visitSnippet(SnippetTree node, P p) {
        return visitOther(node, p);
    }

    /**
     * Visits a {@code SpecTree} node.
     *
     * @implSpec Visits the provided {@code SpecTree} node
     * by calling {@code visitOther(node, p)}.
     *
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     *
     * @since 20
     */
    default R visitSpec(SpecTree node, P p) {
        return visitOther(node, p);
    }

    /**
     * Visits a {@code StartElementTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitStartElement(StartElementTree node, P p);

    /**
     * Visits a {@code SummaryTree} node.
     *
     * @implSpec Visits the provided {@code SummaryTree} node
     * by calling {@code visitOther(node, p)}.
     *
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     * @since 10
     */
    default R visitSummary(SummaryTree node, P p) {
        return visitOther(node, p);
    }

    /**
     * Visits a {@code SystemPropertyTree} node.
     *
     * @implSpec Visits the provided {@code SystemPropertyTree} node
     * by calling {@code visitOther(node, p)}.
     *
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     * @since 12
     */
    default R visitSystemProperty(SystemPropertyTree node, P p) {
        return visitOther(node, p);
    }

    /**
     * Visits a {@code TextTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitText(TextTree node, P p);

    /**
     * Visits a {@code ThrowsTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitThrows(ThrowsTree node, P p);

    /**
     * Visits an {@code UnknownBlockTagTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitUnknownBlockTag(UnknownBlockTagTree node, P p);

    /**
     * Visits an {@code UnknownInlineTagTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitUnknownInlineTag(UnknownInlineTagTree node, P p);

    /**
     * Visits a {@code UsesTree} node.
     *
     * @implSpec Visits a {@code UsesTree} node
     * by calling {@code visitOther(node, p)}.
     *
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     *
     * @since 9
     */
    default R visitUses(UsesTree node, P p) {
        return visitOther(node, p);
    }

    /**
     * Visits a {@code ValueTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitValue(ValueTree node, P p);

    /**
     * Visits a {@code VersionTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitVersion(VersionTree node, P p);

    /**
     * Visits an unknown type of {@code DocTree} node.
     * This can occur if the set of tags evolves and new kinds
     * of nodes are added to the {@code DocTree} hierarchy.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitOther(DocTree node, P p);
}
