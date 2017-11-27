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

import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlConstants;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Links;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.AnnotationTypeWriter;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.builders.MemberSummaryBuilder;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberMap;

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

    protected TypeMirror prev;

    protected TypeMirror next;

    /**
     * @param configuration the configuration
     * @param annotationType the annotation type being documented.
     * @param prevType the previous class that was documented.
     * @param nextType the next class being documented.
     */
    public AnnotationTypeWriterImpl(HtmlConfiguration configuration,
            TypeElement annotationType, TypeMirror prevType, TypeMirror nextType) {
        super(configuration, DocPath.forClass(configuration.utils, annotationType));
        this.annotationType = annotationType;
        configuration.currentTypeElement = annotationType;
        this.prev = prevType;
        this.next = nextType;
    }

    /**
     * Get the module link.
     *
     * @return a content tree for the module link
     */
    @Override
    protected Content getNavLinkModule() {
        Content linkContent = getModuleLink(utils.elementUtils.getModuleOf(annotationType),
                contents.moduleLabel);
        Content li = HtmlTree.LI(linkContent);
        return li;
    }

    /**
     * Get this package link.
     *
     * @return a content tree for the package link
     */
    @Override
    protected Content getNavLinkPackage() {
        Content linkContent = Links.createLink(DocPaths.PACKAGE_SUMMARY,
                contents.packageLabel);
        Content li = HtmlTree.LI(linkContent);
        return li;
    }

    /**
     * Get the class link.
     *
     * @return a content tree for the class link
     */
    @Override
    protected Content getNavLinkClass() {
        Content li = HtmlTree.LI(HtmlStyle.navBarCell1Rev, contents.classLabel);
        return li;
    }

    /**
     * Get the class use link.
     *
     * @return a content tree for the class use link
     */
    @Override
    protected Content getNavLinkClassUse() {
        Content linkContent = Links.createLink(DocPaths.CLASS_USE.resolve(filename), contents.useLabel);
        Content li = HtmlTree.LI(linkContent);
        return li;
    }

    /**
     * Get link to previous class.
     *
     * @return a content tree for the previous class link
     */
    @Override
    public Content getNavLinkPrevious() {
        Content li;
        if (prev != null) {
            Content prevLink = getLink(new LinkInfoImpl(configuration,
                    LinkInfoImpl.Kind.CLASS, utils.asTypeElement(prev))
                    .label(contents.prevClassLabel).strong(true));
            li = HtmlTree.LI(prevLink);
        }
        else
            li = HtmlTree.LI(contents.prevClassLabel);
        return li;
    }

    /**
     * Get link to next class.
     *
     * @return a content tree for the next class link
     */
    @Override
    public Content getNavLinkNext() {
        Content li;
        if (next != null) {
            Content nextLink = getLink(new LinkInfoImpl(configuration,
                    LinkInfoImpl.Kind.CLASS, utils.asTypeElement(next))
                    .label(contents.nextClassLabel).strong(true));
            li = HtmlTree.LI(nextLink);
        }
        else
            li = HtmlTree.LI(contents.nextClassLabel);
        return li;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getHeader(String header) {
        HtmlTree bodyTree = getBody(true, getWindowTitle(utils.getSimpleName(annotationType)));
        HtmlTree htmlTree = (configuration.allowTag(HtmlTag.HEADER))
                ? HtmlTree.HEADER()
                : bodyTree;
        addTop(htmlTree);
        addNavLinks(true, htmlTree);
        if (configuration.allowTag(HtmlTag.HEADER)) {
            bodyTree.addContent(htmlTree);
        }
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
        if (configuration.allowTag(HtmlTag.MAIN)) {
            mainTree.addContent(div);
        } else {
            bodyTree.addContent(div);
        }
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
        Content htmlTree = (configuration.allowTag(HtmlTag.FOOTER))
                ? HtmlTree.FOOTER()
                : contentTree;
        addNavLinks(false, htmlTree);
        addBottom(htmlTree);
        if (configuration.allowTag(HtmlTag.FOOTER)) {
            contentTree.addContent(htmlTree);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void printDocument(Content contentTree) throws DocFileIOException {
        printHtmlDocument(configuration.metakeywords.getMetaKeywords(annotationType),
                true, contentTree);
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
    protected Content getNavLinkTree() {
        Content treeLinkContent = Links.createLink(DocPaths.PACKAGE_TREE,
                contents.treeLabel, "", "");
        Content li = HtmlTree.LI(treeLinkContent);
        return li;
    }

    /**
     * Add summary details to the navigation bar.
     *
     * @param subDiv the content tree to which the summary detail links will be added
     */
    @Override
    protected void addSummaryDetailLinks(Content subDiv) {
        Content div = HtmlTree.DIV(getNavSummaryLinks());
        div.addContent(getNavDetailLinks());
        subDiv.addContent(div);
    }

    /**
     * Get summary links for navigation bar.
     *
     * @return the content tree for the navigation summary links
     */
    protected Content getNavSummaryLinks() {
        Content li = HtmlTree.LI(contents.summaryLabel);
        li.addContent(Contents.SPACE);
        Content ulNav = HtmlTree.UL(HtmlStyle.subNavList, li);
        MemberSummaryBuilder memberSummaryBuilder =
                configuration.getBuilderFactory().getMemberSummaryBuilder(this);
        Content liNavField = new HtmlTree(HtmlTag.LI);
        addNavSummaryLink(memberSummaryBuilder,
                "doclet.navField",
                VisibleMemberMap.Kind.ANNOTATION_TYPE_FIELDS, liNavField);
        addNavGap(liNavField);
        ulNav.addContent(liNavField);
        Content liNavReq = new HtmlTree(HtmlTag.LI);
        addNavSummaryLink(memberSummaryBuilder,
                "doclet.navAnnotationTypeRequiredMember",
                VisibleMemberMap.Kind.ANNOTATION_TYPE_MEMBER_REQUIRED, liNavReq);
        addNavGap(liNavReq);
        ulNav.addContent(liNavReq);
        Content liNavOpt = new HtmlTree(HtmlTag.LI);
        addNavSummaryLink(memberSummaryBuilder,
                "doclet.navAnnotationTypeOptionalMember",
                VisibleMemberMap.Kind.ANNOTATION_TYPE_MEMBER_OPTIONAL, liNavOpt);
        ulNav.addContent(liNavOpt);
        return ulNav;
    }

    /**
     * Add the navigation summary link.
     *
     * @param builder builder for the member to be documented
     * @param label the label for the navigation
     * @param type type to be documented
     * @param liNav the content tree to which the navigation summary link will be added
     */
    protected void addNavSummaryLink(MemberSummaryBuilder builder,
            String label, VisibleMemberMap.Kind type, Content liNav) {
        AbstractMemberWriter writer = ((AbstractMemberWriter) builder.
                getMemberSummaryWriter(type));
        if (writer == null) {
            liNav.addContent(contents.getContent(label));
        } else {
            liNav.addContent(writer.getNavSummaryLink(null,
                    ! builder.getVisibleMemberMap(type).noVisibleMembers()));
        }
    }

    /**
     * Get detail links for the navigation bar.
     *
     * @return the content tree for the detail links
     */
    protected Content getNavDetailLinks() {
        Content li = HtmlTree.LI(contents.detailLabel);
        li.addContent(Contents.SPACE);
        Content ulNav = HtmlTree.UL(HtmlStyle.subNavList, li);
        MemberSummaryBuilder memberSummaryBuilder =
                configuration.getBuilderFactory().getMemberSummaryBuilder(this);
        AbstractMemberWriter writerField =
                ((AbstractMemberWriter) memberSummaryBuilder.
                getMemberSummaryWriter(VisibleMemberMap.Kind.ANNOTATION_TYPE_FIELDS));
        AbstractMemberWriter writerOptional =
                ((AbstractMemberWriter) memberSummaryBuilder.
                getMemberSummaryWriter(VisibleMemberMap.Kind.ANNOTATION_TYPE_MEMBER_OPTIONAL));
        AbstractMemberWriter writerRequired =
                ((AbstractMemberWriter) memberSummaryBuilder.
                getMemberSummaryWriter(VisibleMemberMap.Kind.ANNOTATION_TYPE_MEMBER_REQUIRED));
        Content liNavField = new HtmlTree(HtmlTag.LI);
        if (writerField != null) {
            writerField.addNavDetailLink(!utils.getAnnotationFields(annotationType).isEmpty(), liNavField);
        } else {
            liNavField.addContent(contents.navField);
        }
        addNavGap(liNavField);
        ulNav.addContent(liNavField);
        if (writerOptional != null){
            Content liNavOpt = new HtmlTree(HtmlTag.LI);
            writerOptional.addNavDetailLink(!annotationType.getAnnotationMirrors().isEmpty(), liNavOpt);
            ulNav.addContent(liNavOpt);
        } else if (writerRequired != null){
            Content liNavReq = new HtmlTree(HtmlTag.LI);
            writerRequired.addNavDetailLink(!annotationType.getAnnotationMirrors().isEmpty(), liNavReq);
            ulNav.addContent(liNavReq);
        } else {
            Content liNav = HtmlTree.LI(contents.navAnnotationTypeMember);
            ulNav.addContent(liNav);
        }
        return ulNav;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TypeElement getAnnotationTypeElement() {
        return annotationType;
    }
}
