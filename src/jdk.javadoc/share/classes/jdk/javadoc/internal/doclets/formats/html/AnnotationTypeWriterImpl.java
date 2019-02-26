/*
 * Copyright (c) 2003, 2019, Oracle and/or its affiliates. All rights reserved.
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
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlConstants;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Navigation;
import jdk.javadoc.internal.doclets.formats.html.markup.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.AnnotationTypeWriter;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;

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
 *
 * @author Atul M Dambalkar
 * @author Robert Field
 * @author Bhavesh Patel (Modified)
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
        this.navBar = new Navigation(annotationType, configuration, fixedNavDiv, PageMode.CLASS, path);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getHeader(String header) {
        HtmlTree bodyTree = getBody(true, getWindowTitle(utils.getSimpleName(annotationType)));
        HtmlTree htmlTree = HtmlTree.HEADER();
        addTop(htmlTree);
        Content linkContent = getModuleLink(utils.elementUtils.getModuleOf(annotationType),
                contents.moduleLabel);
        navBar.setNavLinkModule(linkContent);
        navBar.setMemberSummaryBuilder(configuration.getBuilderFactory().getMemberSummaryBuilder(this));
        navBar.setUserHeader(getUserHeaderFooter(true));
        htmlTree.addContent(navBar.getContent(true));
        bodyTree.addContent(htmlTree);
        bodyTree.addContent(HtmlConstants.START_OF_CLASS_DATA);
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.setStyle(HtmlStyle.header);
        if (configuration.showModules) {
            ModuleElement mdle = configuration.docEnv.getElementUtils().getModuleOf(annotationType);
            Content typeModuleLabel = HtmlTree.SPAN(HtmlStyle.moduleLabelInType, contents.moduleLabel);
            Content moduleNameDiv = HtmlTree.DIV(HtmlStyle.subTitle, typeModuleLabel);
            moduleNameDiv.addContent(Contents.SPACE);
            moduleNameDiv.addContent(getModuleLink(mdle, new StringContent(mdle.getQualifiedName())));
            div.addContent(moduleNameDiv);
        }
        PackageElement pkg = utils.containingPackage(annotationType);
        if (!pkg.isUnnamed()) {
            Content typePackageLabel = HtmlTree.SPAN(HtmlStyle.packageLabelInType, contents.packageLabel);
            Content pkgNameDiv = HtmlTree.DIV(HtmlStyle.subTitle, typePackageLabel);
            pkgNameDiv.addContent(Contents.SPACE);
            Content pkgNameContent = getPackageLink(pkg, new StringContent(utils.getPackageName(pkg)));
            pkgNameDiv.addContent(pkgNameContent);
            div.addContent(pkgNameDiv);
        }
        LinkInfoImpl linkInfo = new LinkInfoImpl(configuration,
                LinkInfoImpl.Kind.CLASS_HEADER, annotationType);
        Content headerContent = new StringContent(header);
        Content heading = HtmlTree.HEADING(HtmlConstants.CLASS_PAGE_HEADING, true,
                HtmlStyle.title, headerContent);
        heading.addContent(getTypeParameterLinks(linkInfo));
        div.addContent(heading);
        mainTree.addContent(div);
        return bodyTree;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getAnnotationContentHeader() {
        return getContentHeader();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addFooter(Content contentTree) {
        contentTree.addContent(HtmlConstants.END_OF_CLASS_DATA);
        Content htmlTree = HtmlTree.FOOTER();
        navBar.setUserFooter(getUserHeaderFooter(false));
        htmlTree.addContent(navBar.getContent(false));
        addBottom(htmlTree);
        contentTree.addContent(htmlTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void printDocument(Content contentTree) throws DocFileIOException {
        String description = getDescription("declaration", annotationType);
        PackageElement pkg = utils.containingPackage(this.annotationType);
        Content stylesheetContent = getLocalStylesheetContent(pkg);
        printHtmlDocument(configuration.metakeywords.getMetaKeywords(annotationType),
                description, stylesheetContent, contentTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getAnnotationInfoTreeHeader() {
        return getMemberTreeHeader();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getAnnotationInfo(Content annotationInfoTree) {
        return getMemberTree(HtmlStyle.description, annotationInfoTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAnnotationTypeSignature(String modifiers, Content annotationInfoTree) {
        Content hr = new HtmlTree(HtmlTag.HR);
        annotationInfoTree.addContent(hr);
        Content pre = new HtmlTree(HtmlTag.PRE);
        addAnnotationInfo(annotationType, pre);
        pre.addContent(modifiers);
        LinkInfoImpl linkInfo = new LinkInfoImpl(configuration,
                LinkInfoImpl.Kind.CLASS_SIGNATURE, annotationType);
        Content annotationName = new StringContent(utils.getSimpleName(annotationType));
        Content parameterLinks = getTypeParameterLinks(linkInfo);
        if (configuration.linksource) {
            addSrcLink(annotationType, annotationName, pre);
            pre.addContent(parameterLinks);
        } else {
            Content span = HtmlTree.SPAN(HtmlStyle.memberNameLabel, annotationName);
            span.addContent(parameterLinks);
            pre.addContent(span);
        }
        annotationInfoTree.addContent(pre);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAnnotationTypeDescription(Content annotationInfoTree) {
        if (!configuration.nocomment) {
            if (!utils.getFullBody(annotationType).isEmpty()) {
                addInlineComment(annotationType, annotationInfoTree);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAnnotationTypeTagInfo(Content annotationInfoTree) {
        if (!configuration.nocomment) {
            addTagsInfo(annotationType, annotationInfoTree);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAnnotationTypeDeprecationInfo(Content annotationInfoTree) {
        List<? extends DocTree> deprs = utils.getBlockTags(annotationType, DocTree.Kind.DEPRECATED);
        if (utils.isDeprecated(annotationType)) {
            CommentHelper ch = utils.getCommentHelper(annotationType);
            Content deprLabel = HtmlTree.SPAN(HtmlStyle.deprecatedLabel, getDeprecatedPhrase(annotationType));
            Content div = HtmlTree.DIV(HtmlStyle.deprecationBlock, deprLabel);
            if (!deprs.isEmpty()) {

                List<? extends DocTree> commentTags = ch.getDescription(configuration, deprs.get(0));
                if (!commentTags.isEmpty()) {
                    addInlineDeprecatedComment(annotationType, deprs.get(0), div);
                }
            }
            annotationInfoTree.addContent(div);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TypeElement getAnnotationTypeElement() {
        return annotationType;
    }
}
