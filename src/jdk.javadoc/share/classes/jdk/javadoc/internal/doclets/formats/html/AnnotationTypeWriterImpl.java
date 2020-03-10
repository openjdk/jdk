/*
 * Copyright (c) 2003, 2020, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.Entity;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.AnnotationTypeWriter;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;

/**
 * Generate the Class Information Page.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @see java.util.Collections
 * @see java.util.List
 * @see java.util.ArrayList
 * @see java.util.HashMap
 */
public class AnnotationTypeWriterImpl extends SubWriterHolderWriter
        implements AnnotationTypeWriter {

    protected TypeElement annotationType;

    private final Navigation navBar;

    /**
     * @param configuration the configuration
     * @param annotationType the annotation type being documented.
     */
    public AnnotationTypeWriterImpl(HtmlConfiguration configuration,
            TypeElement annotationType) {
        super(configuration, configuration.docPaths.forClass(annotationType));
        this.annotationType = annotationType;
        configuration.currentTypeElement = annotationType;
        this.navBar = new Navigation(annotationType, configuration, PageMode.CLASS, path);
    }

    @Override
    public Content getHeader(String header) {
        Content headerContent = new ContentBuilder();
        addTop(headerContent);
        Content linkContent = getModuleLink(utils.elementUtils.getModuleOf(annotationType),
                contents.moduleLabel);
        navBar.setNavLinkModule(linkContent);
        navBar.setMemberSummaryBuilder(configuration.getBuilderFactory().getMemberSummaryBuilder(this));
        navBar.setUserHeader(getUserHeaderFooter(true));
        headerContent.add(navBar.getContent(Navigation.Position.TOP));

        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.setStyle(HtmlStyle.header);
        if (configuration.showModules) {
            ModuleElement mdle = configuration.docEnv.getElementUtils().getModuleOf(annotationType);
            Content typeModuleLabel = HtmlTree.SPAN(HtmlStyle.moduleLabelInType, contents.moduleLabel);
            Content moduleNameDiv = HtmlTree.DIV(HtmlStyle.subTitle, typeModuleLabel);
            moduleNameDiv.add(Entity.NO_BREAK_SPACE);
            moduleNameDiv.add(getModuleLink(mdle, new StringContent(mdle.getQualifiedName())));
            div.add(moduleNameDiv);
        }
        PackageElement pkg = utils.containingPackage(annotationType);
        if (!pkg.isUnnamed()) {
            Content typePackageLabel = HtmlTree.SPAN(HtmlStyle.packageLabelInType, contents.packageLabel);
            Content pkgNameDiv = HtmlTree.DIV(HtmlStyle.subTitle, typePackageLabel);
            pkgNameDiv.add(Entity.NO_BREAK_SPACE);
            Content pkgNameContent = getPackageLink(pkg, new StringContent(utils.getPackageName(pkg)));
            pkgNameDiv.add(pkgNameContent);
            div.add(pkgNameDiv);
        }
        LinkInfoImpl linkInfo = new LinkInfoImpl(configuration,
                LinkInfoImpl.Kind.CLASS_HEADER, annotationType);
        Content heading = HtmlTree.HEADING(Headings.PAGE_TITLE_HEADING, true,
                HtmlStyle.title, new StringContent(header));
        heading.add(getTypeParameterLinks(linkInfo));
        div.add(heading);
        bodyContents.setHeader(headerContent)
                .addMainContent(MarkerComments.START_OF_CLASS_DATA)
                .addMainContent(div);
        return getBody(getWindowTitle(utils.getSimpleName(annotationType)));
    }

    @Override
    public Content getAnnotationContentHeader() {
        return getContentHeader();
    }

    @Override
    public void addFooter() {
        Content htmlTree = HtmlTree.FOOTER();
        navBar.setUserFooter(getUserHeaderFooter(false));
        htmlTree.add(navBar.getContent(Navigation.Position.BOTTOM));
        addBottom(htmlTree);
        bodyContents.addMainContent(MarkerComments.END_OF_CLASS_DATA)
                    .setFooter(htmlTree);
    }

    @Override
    public void printDocument(Content contentTree) throws DocFileIOException {
        String description = getDescription("declaration", annotationType);
        PackageElement pkg = utils.containingPackage(this.annotationType);
        List<DocPath> localStylesheets = getLocalStylesheets(pkg);
        contentTree.add(bodyContents);
        printHtmlDocument(configuration.metakeywords.getMetaKeywords(annotationType),
                description, localStylesheets, contentTree);
    }

    @Override
    public Content getAnnotationInfoTreeHeader() {
        return getMemberTreeHeader();
    }

    @Override
    public Content getAnnotationInfo(Content annotationInfoTree) {
        return HtmlTree.SECTION(HtmlStyle.description, annotationInfoTree);
    }

    @Override
    public void addAnnotationTypeSignature(String modifiers, Content annotationInfoTree) {
        Content hr = new HtmlTree(HtmlTag.HR);
        annotationInfoTree.add(hr);
        Content pre = new HtmlTree(HtmlTag.PRE);
        addAnnotationInfo(annotationType, pre);
        pre.add(modifiers);
        LinkInfoImpl linkInfo = new LinkInfoImpl(configuration,
                LinkInfoImpl.Kind.CLASS_SIGNATURE, annotationType);
        Content annotationName = new StringContent(utils.getSimpleName(annotationType));
        Content parameterLinks = getTypeParameterLinks(linkInfo);
        if (options.linkSource()) {
            addSrcLink(annotationType, annotationName, pre);
            pre.add(parameterLinks);
        } else {
            Content span = HtmlTree.SPAN(HtmlStyle.memberNameLabel, annotationName);
            span.add(parameterLinks);
            pre.add(span);
        }
        annotationInfoTree.add(pre);
    }

    @Override
    public void addAnnotationTypeDescription(Content annotationInfoTree) {
        if (!options.noComment()) {
            if (!utils.getFullBody(annotationType).isEmpty()) {
                addInlineComment(annotationType, annotationInfoTree);
            }
        }
    }

    @Override
    public void addAnnotationTypeTagInfo(Content annotationInfoTree) {
        if (!options.noComment()) {
            addTagsInfo(annotationType, annotationInfoTree);
        }
    }

    @Override
    public void addAnnotationTypeDeprecationInfo(Content annotationInfoTree) {
        List<? extends DocTree> deprs = utils.getBlockTags(annotationType, DocTree.Kind.DEPRECATED);
        if (utils.isDeprecated(annotationType)) {
            CommentHelper ch = utils.getCommentHelper(annotationType);
            Content deprLabel = HtmlTree.SPAN(HtmlStyle.deprecatedLabel, getDeprecatedPhrase(annotationType));
            Content div = HtmlTree.DIV(HtmlStyle.deprecationBlock, deprLabel);
            if (!deprs.isEmpty()) {

                List<? extends DocTree> commentTags = ch.getDescription(deprs.get(0));
                if (!commentTags.isEmpty()) {
                    addInlineDeprecatedComment(annotationType, deprs.get(0), div);
                }
            }
            annotationInfoTree.add(div);
        }
    }

    @Override
    public TypeElement getAnnotationTypeElement() {
        return annotationType;
    }

    @Override
    public Content getMemberDetailsTree(Content contentTree) {
        return HtmlTree.SECTION(HtmlStyle.details, contentTree)
                .setId(SectionName.ANNOTATION_TYPE_ELEMENT_DETAIL.getName());
    }
}
