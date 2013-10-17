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

package com.sun.source.doctree;


/**
 * A visitor of trees, in the style of the visitor design pattern.
 * Classes implementing this interface are used to operate
 * on a tree when the kind of tree is unknown at compile time.
 * When a visitor is passed to an tree's {@link DocTree#accept
 * accept} method, the <tt>visit<i>XYZ</i></tt> method most applicable
 * to that tree is invoked.
 *
 * <p> Classes implementing this interface may or may not throw a
 * {@code NullPointerException} if the additional parameter {@code p}
 * is {@code null}; see documentation of the implementing class for
 * details.
 *
 * <p> <b>WARNING:</b> It is possible that methods will be added to
 * this interface to accommodate new, currently unknown, doc comment
 * structures added to future versions of the Java&trade; programming
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
@jdk.Exported
public interface DocTreeVisitor<R,P> {
    R visitAttribute(AttributeTree node, P p);
    R visitAuthor(AuthorTree node, P p);
    R visitComment(CommentTree node, P p);
    R visitDeprecated(DeprecatedTree node, P p);
    R visitDocComment(DocCommentTree node, P p);
    R visitDocRoot(DocRootTree node, P p);
    R visitEndElement(EndElementTree node, P p);
    R visitEntity(EntityTree node, P p);
    R visitErroneous(ErroneousTree node, P p);
    R visitIdentifier(IdentifierTree node, P p);
    R visitInheritDoc(InheritDocTree node, P p);
    R visitLink(LinkTree node, P p);
    R visitLiteral(LiteralTree node, P p);
    R visitParam(ParamTree node, P p);
    R visitReference(ReferenceTree node, P p);
    R visitReturn(ReturnTree node, P p);
    R visitSee(SeeTree node, P p);
    R visitSerial(SerialTree node, P p);
    R visitSerialData(SerialDataTree node, P p);
    R visitSerialField(SerialFieldTree node, P p);
    R visitSince(SinceTree node, P p);
    R visitStartElement(StartElementTree node, P p);
    R visitText(TextTree node, P p);
    R visitThrows(ThrowsTree node, P p);
    R visitUnknownBlockTag(UnknownBlockTagTree node, P p);
    R visitUnknownInlineTag(UnknownInlineTagTree node, P p);
    R visitValue(ValueTree node, P p);
    R visitVersion(VersionTree node, P p);
    R visitOther(DocTree node, P p);
}
