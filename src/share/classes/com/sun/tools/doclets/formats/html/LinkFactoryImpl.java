/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.formats.html;

import java.util.List;

import com.sun.javadoc.*;
import com.sun.tools.doclets.formats.html.markup.ContentBuilder;
import com.sun.tools.doclets.formats.html.markup.RawHtml;
import com.sun.tools.doclets.formats.html.markup.StringContent;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;
import com.sun.tools.doclets.internal.toolkit.util.links.*;

/**
 * A factory that returns a link given the information about it.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 * @since 1.5
 */
public class LinkFactoryImpl extends LinkFactory {

    private HtmlDocletWriter m_writer;

    public LinkFactoryImpl(HtmlDocletWriter writer) {
        m_writer = writer;
    }

    /**
     * {@inheritDoc}
     */
    protected Content newContent() {
        return new ContentBuilder();
    }

    /**
     * {@inheritDoc}
     */
    protected Content getClassLink(LinkInfo linkInfo) {
        LinkInfoImpl classLinkInfo = (LinkInfoImpl) linkInfo;
        boolean noLabel = linkInfo.label == null || linkInfo.label.isEmpty();
        ClassDoc classDoc = classLinkInfo.classDoc;
        //Create a tool tip if we are linking to a class or interface.  Don't
        //create one if we are linking to a member.
        String title =
            (classLinkInfo.where == null || classLinkInfo.where.length() == 0) ?
                getClassToolTip(classDoc,
                    classLinkInfo.type != null &&
                    !classDoc.qualifiedTypeName().equals(classLinkInfo.type.qualifiedTypeName())) :
            "";
        Content label = classLinkInfo.getClassLinkLabel(m_writer.configuration);
        Configuration configuration = m_writer.configuration;
        Content link = new ContentBuilder();
        if (classDoc.isIncluded()) {
            if (configuration.isGeneratedDoc(classDoc)) {
                DocPath filename = getPath(classLinkInfo);
                if (linkInfo.linkToSelf ||
                                !(DocPath.forName(classDoc)).equals(m_writer.filename)) {
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
                classDoc.qualifiedName(), classLinkInfo.where,
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
        link.addContent(label.toString());
        if (noLabel && !classLinkInfo.excludeTypeParameterLinks) {
            link.addContent(getTypeParameterLinks(linkInfo));
        }
        return link;
    }

    /**
     * {@inheritDoc}
     */
    protected Content getTypeParameterLink(LinkInfo linkInfo,
        Type typeParam) {
        LinkInfoImpl typeLinkInfo = new LinkInfoImpl(m_writer.configuration,
                ((LinkInfoImpl) linkInfo).getContext(), typeParam);
        typeLinkInfo.excludeTypeBounds = linkInfo.excludeTypeBounds;
        typeLinkInfo.excludeTypeParameterLinks = linkInfo.excludeTypeParameterLinks;
        typeLinkInfo.linkToSelf = linkInfo.linkToSelf;
        typeLinkInfo.isJava5DeclarationLocation = false;
        return getLink(typeLinkInfo);
    }

    protected Content getTypeAnnotationLink(LinkInfo linkInfo,
            AnnotationDesc annotation) {
        throw new RuntimeException("Not implemented yet!");
    }

    public Content getTypeAnnotationLinks(LinkInfo linkInfo) {
        ContentBuilder links = new ContentBuilder();
        AnnotationDesc[] annotations;
        if (linkInfo.type instanceof AnnotatedType) {
            annotations = linkInfo.type.asAnnotatedType().annotations();
        } else if (linkInfo.type instanceof TypeVariable) {
            annotations = linkInfo.type.asTypeVariable().annotations();
        } else {
            return links;
        }

        if (annotations.length == 0)
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
     * @param classDoc the class to get the tool tip for.
     * @return the tool tip for the appropriate class.
     */
    private String getClassToolTip(ClassDoc classDoc, boolean isTypeLink) {
        Configuration configuration = m_writer.configuration;
        if (isTypeLink) {
            return configuration.getText("doclet.Href_Type_Param_Title",
                classDoc.name());
        } else if (classDoc.isInterface()){
            return configuration.getText("doclet.Href_Interface_Title",
                Util.getPackageName(classDoc.containingPackage()));
        } else if (classDoc.isAnnotationType()) {
            return configuration.getText("doclet.Href_Annotation_Title",
                Util.getPackageName(classDoc.containingPackage()));
        } else if (classDoc.isEnum()) {
            return configuration.getText("doclet.Href_Enum_Title",
                Util.getPackageName(classDoc.containingPackage()));
        } else {
            return configuration.getText("doclet.Href_Class_Title",
                Util.getPackageName(classDoc.containingPackage()));
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
            return DocPath.forName(linkInfo.classDoc);
        }
        return m_writer.pathToRoot.resolve(DocPath.forClass(linkInfo.classDoc));
    }
}
