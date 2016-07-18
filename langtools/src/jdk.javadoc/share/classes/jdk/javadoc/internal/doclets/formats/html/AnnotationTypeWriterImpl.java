/*
 * Copyright (c) 2003, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.List;

import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlConstants;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.AnnotationTypeWriter;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.builders.MemberSummaryBuilder;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.DocletAbortException;
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
     * @throws java.lang.Exception
     */
    public AnnotationTypeWriterImpl(ConfigurationImpl configuration,
            TypeElement annotationType, TypeMirror prevType, TypeMirror nextType)
            throws Exception {
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
                moduleLabel);
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
        Content linkContent = getHyperLink(DocPaths.PACKAGE_SUMMARY,
                packageLabel);
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
        Content li = HtmlTree.LI(HtmlStyle.navBarCell1Rev, classLabel);
        return li;
    }

    /**
     * Get the class use link.
     *
     * @return a content tree for the class use link
     */
    @Override
    protected Content getNavLinkClassUse() {
        Content linkContent = getHyperLink(DocPaths.CLASS_USE.resolve(filename), useLabel);
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
                    .label(prevclassLabel).strong(true));
            li = HtmlTree.LI(prevLink);
        }
        else
            li = HtmlTree.LI(prevclassLabel);
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
                    .label(nextclassLabel).strong(true));
            li = HtmlTree.LI(nextLink);
        }
        else
            li = HtmlTree.LI(nextclassLabel);
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
        div.addStyle(HtmlStyle.header);
        PackageElement pkg = utils.containingPackage(annotationType);
        if (!pkg.isUnnamed()) {
            Content pkgNameContent = new StringContent(utils.getPackageName(pkg));
            Content pkgNameDiv = HtmlTree.DIV(HtmlStyle.subTitle, pkgNameContent);
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
    public void printDocument(Content contentTree) throws IOException {
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
        annotationInfoTree.addContent(new HtmlTree(HtmlTag.BR));
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
        if(!configuration.nocomment) {
            if (!utils.getBody(annotationType).isEmpty()) {
                addInlineComment(annotationType, annotationInfoTree);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAnnotationTypeTagInfo(Content annotationInfoTree) {
        if(!configuration.nocomment) {
            addTagsInfo(annotationType, annotationInfoTree);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAnnotationTypeDeprecationInfo(Content annotationInfoTree) {
        Content hr = new HtmlTree(HtmlTag.HR);
        annotationInfoTree.addContent(hr);
        List<? extends DocTree> deprs = utils.getBlockTags(annotationType, DocTree.Kind.DEPRECATED);
        if (utils.isDeprecated(annotationType)) {
            CommentHelper ch = utils.getCommentHelper(annotationType);
            Content deprLabel = HtmlTree.SPAN(HtmlStyle.deprecatedLabel, deprecatedPhrase);
            Content div = HtmlTree.DIV(HtmlStyle.block, deprLabel);
            if (!deprs.isEmpty()) {

                List<? extends DocTree> commentTags = ch.getDescription(configuration, deprs.get(0));
                if (!commentTags.isEmpty()) {
                    div.addContent(getSpace());
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
        Content treeLinkContent = getHyperLink(DocPaths.PACKAGE_TREE,
                treeLabel, "", "");
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
        try {
            Content div = HtmlTree.DIV(getNavSummaryLinks());
            div.addContent(getNavDetailLinks());
            subDiv.addContent(div);
        } catch (Exception e) {
            throw new DocletAbortException(e);
        }
    }

    /**
     * Get summary links for navigation bar.
     *
     * @return the content tree for the navigation summary links
     * @throws java.lang.Exception
     */
    protected Content getNavSummaryLinks() throws Exception {
        Content li = HtmlTree.LI(summaryLabel);
        li.addContent(getSpace());
        Content ulNav = HtmlTree.UL(HtmlStyle.subNavList, li);
        MemberSummaryBuilder memberSummaryBuilder = (MemberSummaryBuilder)
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
            liNav.addContent(getResource(label));
        } else {
            liNav.addContent(writer.getNavSummaryLink(null,
                    ! builder.getVisibleMemberMap(type).noVisibleMembers()));
        }
    }

    /**
     * Get detail links for the navigation bar.
     *
     * @return the content tree for the detail links
     * @throws java.lang.Exception
     */
    protected Content getNavDetailLinks() throws Exception {
        Content li = HtmlTree.LI(detailLabel);
        li.addContent(getSpace());
        Content ulNav = HtmlTree.UL(HtmlStyle.subNavList, li);
        MemberSummaryBuilder memberSummaryBuilder = (MemberSummaryBuilder)
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
            liNavField.addContent(getResource("doclet.navField"));
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
            Content liNav = HtmlTree.LI(getResource("doclet.navAnnotationTypeMember"));
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
