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

package jdk.javadoc.internal.doclets.formats.html;

import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;
import jdk.javadoc.internal.doclets.toolkit.util.links.LinkFactory;
import jdk.javadoc.internal.doclets.toolkit.util.links.LinkInfo;

/**
 * A factory that returns a link given the information about it.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 */
public class LinkFactoryImpl extends LinkFactory {

    private final HtmlDocletWriter m_writer;

    public LinkFactoryImpl(HtmlDocletWriter writer) {
        m_writer = writer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Content newContent() {
        return new ContentBuilder();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Content getClassLink(LinkInfo linkInfo) {
        BaseConfiguration configuration = m_writer.configuration;
        Utils utils = configuration.utils;
        LinkInfoImpl classLinkInfo = (LinkInfoImpl) linkInfo;
        boolean noLabel = linkInfo.label == null || linkInfo.label.isEmpty();
        TypeElement typeElement = classLinkInfo.typeElement;
        // Create a tool tip if we are linking to a class or interface.  Don't
        // create one if we are linking to a member.
        String title = "";
        if (classLinkInfo.where == null || classLinkInfo.where.length() == 0) {
            boolean isTypeLink = classLinkInfo.type != null &&
                     utils.isTypeVariable(utils.getComponentType(classLinkInfo.type));
            title = getClassToolTip(typeElement, isTypeLink);
        }
        Content label = classLinkInfo.getClassLinkLabel(m_writer.configuration);

        Content link = new ContentBuilder();
        if (utils.isIncluded(typeElement)) {
            if (configuration.isGeneratedDoc(typeElement)) {
                DocPath filename = getPath(classLinkInfo);
                if (linkInfo.linkToSelf ||
                                !(DocPath.forName(utils, typeElement)).equals(m_writer.filename)) {
                        link.addContent(m_writer.getHyperLink(
                                filename.fragment(classLinkInfo.where),
                            label,
                            classLinkInfo.isStrong, classLinkInfo.styleName,
                            title, classLinkInfo.target));
                        if (noLabel && !classLinkInfo.excludeTypeParameterLinks) {
                            link.addContent(getTypeParameterLinks(linkInfo));
                        }
                        return link;
                }
            }
        } else {
            Content crossLink = m_writer.getCrossClassLink(
                typeElement.getQualifiedName().toString(), classLinkInfo.where,
                label, classLinkInfo.isStrong, classLinkInfo.styleName,
                true);
            if (crossLink != null) {
                link.addContent(crossLink);
                if (noLabel && !classLinkInfo.excludeTypeParameterLinks) {
                    link.addContent(getTypeParameterLinks(linkInfo));
                }
                return link;
            }
        }
        // Can't link so just write label.
        link.addContent(label);
        if (noLabel && !classLinkInfo.excludeTypeParameterLinks) {
            link.addContent(getTypeParameterLinks(linkInfo));
        }
        return link;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Content getTypeParameterLink(LinkInfo linkInfo, TypeMirror typeParam) {
        LinkInfoImpl typeLinkInfo = new LinkInfoImpl(m_writer.configuration,
                ((LinkInfoImpl) linkInfo).getContext(), typeParam);
        typeLinkInfo.excludeTypeBounds = linkInfo.excludeTypeBounds;
        typeLinkInfo.excludeTypeParameterLinks = linkInfo.excludeTypeParameterLinks;
        typeLinkInfo.linkToSelf = linkInfo.linkToSelf;
        typeLinkInfo.isJava5DeclarationLocation = false;
        return getLink(typeLinkInfo);
    }

    @Override
    protected Content getTypeAnnotationLink(LinkInfo linkInfo, AnnotationMirror annotation) {
        throw new RuntimeException("Not implemented yet!");
    }

    @Override
    public Content getTypeAnnotationLinks(LinkInfo linkInfo) {
        Utils utils = ((LinkInfoImpl)linkInfo).utils;
        ContentBuilder links = new ContentBuilder();
        List<? extends AnnotationMirror> annotations;
        if (utils.isAnnotated(linkInfo.type)) {
            annotations = linkInfo.type.getAnnotationMirrors();
        } else if (utils.isTypeVariable(linkInfo.type)) {
            // TODO: use the context for now, and special case for Receiver_Types,
            // which takes the default case.
            switch (((LinkInfoImpl)linkInfo).context) {
                case MEMBER_TYPE_PARAMS:
                case EXECUTABLE_MEMBER_PARAM:
                case CLASS_SIGNATURE:
                    Element element = utils.typeUtils.asElement(linkInfo.type);
                    annotations = element.getAnnotationMirrors();
                    break;
                default:
                    annotations = linkInfo.type.getAnnotationMirrors();
                    break;
            }

        } else {
            return links;
        }

        if (annotations.isEmpty())
            return links;

        List<Content> annos = m_writer.getAnnotations(0, annotations, false, linkInfo.isJava5DeclarationLocation);

        boolean isFirst = true;
        for (Content anno : annos) {
            if (!isFirst) {
                links.addContent(" ");
            }
            links.addContent(anno);
            isFirst = false;
        }
        if (!annos.isEmpty()) {
            links.addContent(" ");
        }

        return links;
    }

    /**
     * Given a class, return the appropriate tool tip.
     *
     * @param typeElement the class to get the tool tip for.
     * @return the tool tip for the appropriate class.
     */
    private String getClassToolTip(TypeElement typeElement, boolean isTypeLink) {
        BaseConfiguration configuration = m_writer.configuration;
        Utils utils = configuration.utils;
        if (isTypeLink) {
            return configuration.getText("doclet.Href_Type_Param_Title",
                    utils.getSimpleName(typeElement));
        } else if (utils.isInterface(typeElement)){
            return configuration.getText("doclet.Href_Interface_Title",
                utils.getPackageName(utils.containingPackage(typeElement)));
        } else if (utils.isAnnotationType(typeElement)) {
            return configuration.getText("doclet.Href_Annotation_Title",
                utils.getPackageName(utils.containingPackage(typeElement)));
        } else if (utils.isEnum(typeElement)) {
            return configuration.getText("doclet.Href_Enum_Title",
                utils.getPackageName(utils.containingPackage(typeElement)));
        } else {
            return configuration.getText("doclet.Href_Class_Title",
                utils.getPackageName(utils.containingPackage(typeElement)));
        }
    }

    /**
     * Return path to the given file name in the given package. So if the name
     * passed is "Object.html" and the name of the package is "java.lang", and
     * if the relative path is "../.." then returned string will be
     * "../../java/lang/Object.html"
     *
     * @param linkInfo the information about the link.
     */
    private DocPath getPath(LinkInfoImpl linkInfo) {
        if (linkInfo.context == LinkInfoImpl.Kind.PACKAGE_FRAME) {
            //Not really necessary to do this but we want to be consistent
            //with 1.4.2 output.
            return DocPath.forName(linkInfo.utils, linkInfo.typeElement);
        }
        return m_writer.pathToRoot.resolve(DocPath.forClass(linkInfo.utils, linkInfo.typeElement));
    }
}
