/*
 * Copyright (c) 2003, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.util.links;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleTypeVisitor9;

import jdk.javadoc.internal.doclets.formats.html.LinkInfoImpl;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

/**
 * A factory that constructs links from given link information.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 */
public abstract class LinkFactory {

    /**
     * Return an empty instance of a content object.
     *
     * @return an empty instance of a content object.
     */
    protected abstract Content newContent();

    /**
     * Constructs a link from the given link information.
     *
     * @param linkInfo the information about the link.
     * @return the output of the link.
     */
    public Content getLink(LinkInfo linkInfo) {
        Utils utils = ((LinkInfoImpl) linkInfo).configuration.utils;
        if (linkInfo.type != null) {
            SimpleTypeVisitor9<Content, LinkInfo> linkVisitor =
                    new SimpleTypeVisitor9<Content, LinkInfo>() {

                TypeMirror componentType = utils.getComponentType(linkInfo.type);
                Content link = newContent();

                // handles primitives, no types and error types
                @Override
                protected Content defaultAction(TypeMirror type, LinkInfo linkInfo) {
                    link.addContent(utils.getTypeName(type, false));
                    return link;
                }

                int currentDepth = 0;
                @Override
                public Content visitArray(ArrayType type, LinkInfo linkInfo) {
                    // keep track of the dimension depth and replace the last dimension
                    // specifier with vararags, when the stack is fully unwound.
                    currentDepth++;
                    linkInfo.type = type.getComponentType();
                    visit(linkInfo.type, linkInfo);
                    currentDepth--;
                    if (utils.isAnnotated(type)) {
                        linkInfo.type = type;
                        link.addContent(" ");
                        link.addContent(getTypeAnnotationLinks(linkInfo));
                    }
                    // use vararg if required
                    if (linkInfo.isVarArg && currentDepth == 0) {
                        link.addContent("...");
                    } else {
                        link.addContent("[]");
                    }
                    return link;
                }

                @Override
                public Content visitWildcard(WildcardType type, LinkInfo linkInfo) {
                    linkInfo.isTypeBound = true;
                    link.addContent("?");
                    TypeMirror extendsBound = type.getExtendsBound();
                    if (extendsBound != null) {
                        link.addContent(" extends ");
                        setBoundsLinkInfo(linkInfo, extendsBound);
                        link.addContent(getLink(linkInfo));
                    }
                    TypeMirror superBound = type.getSuperBound();
                    if (superBound != null) {
                        link.addContent(" super ");
                        setBoundsLinkInfo(linkInfo, superBound);
                        link.addContent(getLink(linkInfo));
                    }
                    return link;
                }

                @Override
                public Content visitTypeVariable(TypeVariable type, LinkInfo linkInfo) {
                    link.addContent(getTypeAnnotationLinks(linkInfo));
                    linkInfo.isTypeBound = true;
                    TypeVariable typevariable = (utils.isArrayType(type))
                            ? (TypeVariable) componentType
                            : type;
                    Element owner = typevariable.asElement().getEnclosingElement();
                    if ((!linkInfo.excludeTypeParameterLinks) && utils.isTypeElement(owner)) {
                        linkInfo.typeElement = (TypeElement) owner;
                        Content label = newContent();
                        label.addContent(utils.getTypeName(type, false));
                        linkInfo.label = label;
                        link.addContent(getClassLink(linkInfo));
                    } else {
                        // No need to link method type parameters.
                        link.addContent(utils.getTypeName(typevariable, false));
                    }

                    if (!linkInfo.excludeTypeBounds) {
                        linkInfo.excludeTypeBounds = true;
                        TypeParameterElement tpe = ((TypeParameterElement) typevariable.asElement());
                        boolean more = false;
                        List<? extends TypeMirror> bounds = utils.getBounds(tpe);
                        for (TypeMirror bound : bounds) {
                            // we get everything as extends java.lang.Object we suppress
                            // all of them except those that have multiple extends
                            if (bounds.size() == 1 &&
                                    bound.equals(utils.getObjectType()) &&
                                    !utils.isAnnotated(bound)) {
                                continue;
                            }
                            link.addContent(more ? " & " : " extends ");
                            setBoundsLinkInfo(linkInfo, bound);
                            link.addContent(getLink(linkInfo));
                            more = true;
                        }
                    }
                    return link;
                }

                @Override
                public Content visitDeclared(DeclaredType type, LinkInfo linkInfo) {
                    if (linkInfo.isTypeBound && linkInfo.excludeTypeBoundsLinks) {
                        // Since we are excluding type parameter links, we should not
                        // be linking to the type bound.
                        link.addContent(utils.getTypeName(type, false));
                        link.addContent(getTypeParameterLinks(linkInfo));
                        return link;
                    } else {
                        link = newContent();
                        link.addContent(getTypeAnnotationLinks(linkInfo));
                        linkInfo.typeElement = utils.asTypeElement(type);
                        link.addContent(getClassLink(linkInfo));
                        if (linkInfo.includeTypeAsSepLink) {
                            link.addContent(getTypeParameterLinks(linkInfo, false));
                        }
                    }
                    return link;
                }
            };
            return linkVisitor.visit(linkInfo.type, linkInfo);
        } else if (linkInfo.typeElement != null) {
            Content link = newContent();
            link.addContent(getClassLink(linkInfo));
            if (linkInfo.includeTypeAsSepLink) {
                link.addContent(getTypeParameterLinks(linkInfo, false));
            }
            return link;
        } else {
            return null;
        }
    }

    private void setBoundsLinkInfo(LinkInfo linkInfo, TypeMirror bound) {
        linkInfo.typeElement = null;
        linkInfo.label = null;
        linkInfo.type = bound;
    }

    /**
     * Return the link to the given class.
     *
     * @param linkInfo the information about the link to construct.
     *
     * @return the link for the given class.
     */
    protected abstract Content getClassLink(LinkInfo linkInfo);

    /**
     * Return the link to the given type parameter.
     *
     * @param linkInfo     the information about the link to construct.
     * @param typeParam the type parameter to link to.
     */
    protected abstract Content getTypeParameterLink(LinkInfo linkInfo, TypeMirror typeParam);

    /**
     * Return the links to the type parameters.
     *
     * @param linkInfo     the information about the link to construct.
     * @return the links to the type parameters.
     */
    public Content getTypeParameterLinks(LinkInfo linkInfo) {
        return getTypeParameterLinks(linkInfo, true);
    }

    /**
     * Return the links to the type parameters.
     *
     * @param linkInfo     the information about the link to construct.
     * @param isClassLabel true if this is a class label.  False if it is
     *                     the type parameters portion of the link.
     * @return the links to the type parameters.
     */
    public Content getTypeParameterLinks(LinkInfo linkInfo, boolean isClassLabel) {
        Utils utils = ((LinkInfoImpl)linkInfo).utils;
        Content links = newContent();
        List<TypeMirror> vars = new ArrayList<>();
        TypeMirror ctype = linkInfo.type != null
                ? utils.getComponentType(linkInfo.type)
                : null;
        if (linkInfo.executableElement != null) {
            linkInfo.executableElement.getTypeParameters().stream().forEach((t) -> {
                vars.add(t.asType());
            });
        } else if (linkInfo.type != null && utils.isDeclaredType(linkInfo.type)) {
            ((DeclaredType)linkInfo.type).getTypeArguments().stream().forEach(vars::add);
        } else if (ctype != null && utils.isDeclaredType(ctype)) {
            ((DeclaredType)ctype).getTypeArguments().stream().forEach(vars::add);
        } else if (linkInfo.typeElement != null) {
            linkInfo.typeElement.getTypeParameters().stream().forEach((t) -> {
                vars.add(t.asType());
            });
        } else {
            // Nothing to document.
            return links;
        }
        if (((linkInfo.includeTypeInClassLinkLabel && isClassLabel)
                || (linkInfo.includeTypeAsSepLink && !isClassLabel)) && !vars.isEmpty()) {
            links.addContent("<");
            boolean many = false;
            for (TypeMirror t : vars) {
                if (many) {
                    links.addContent(",");
                }
                links.addContent(getTypeParameterLink(linkInfo, t));
                many = true;
            }
            links.addContent(">");
        }
        return links;
    }

    public abstract Content getTypeAnnotationLinks(LinkInfo linkInfo);
}
