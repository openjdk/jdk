/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.util.*;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.MethodTypes;

/**
 * This abstract class exists to provide functionality needed in the
 * the formatting of member information.  Since AbstractSubWriter and its
 * subclasses control this, they would be the logical place to put this.
 * However, because each member type has its own subclass, subclassing
 * can not be used effectively to change formatting.  The concrete
 * class subclass of this class can be subclassed to change formatting.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @see AbstractMemberWriter
 * @see ClassWriterImpl
 *
 * @author Robert Field
 * @author Atul M Dambalkar
 * @author Bhavesh Patel (Modified)
 */
public abstract class SubWriterHolderWriter extends HtmlDocletWriter {

    /**
     * The HTML tree for main tag.
     */
    protected HtmlTree mainTree = HtmlTree.MAIN();

    public SubWriterHolderWriter(ConfigurationImpl configuration, DocPath filename)
            throws IOException {
        super(configuration, filename);
    }

    /**
     * Add the summary header.
     *
     * @param mw the writer for the member being documented
     * @param typeElement the te to be documented
     * @param memberTree the content tree to which the summary header will be added
     */
    public void addSummaryHeader(AbstractMemberWriter mw, TypeElement typeElement,
            Content memberTree) {
        mw.addSummaryAnchor(typeElement, memberTree);
        mw.addSummaryLabel(memberTree);
    }

    /**
     * Get the summary table.
     *
     * @param mw the writer for the member being documented
     * @param typeElement the te to be documented
     * @param tableContents list of summary table contents
     * @param showTabs true if the table needs to show tabs
     * @return the content tree for the summary table
     */
    public Content getSummaryTableTree(AbstractMemberWriter mw, TypeElement typeElement,
            List<Content> tableContents, boolean showTabs) {
        Content caption;
        if (showTabs) {
            caption = getTableCaption(mw.methodTypes);
            generateMethodTypesScript(mw.typeMap, mw.methodTypes);
        }
        else {
            caption = getTableCaption(mw.getCaption());
        }
        Content table = (configuration.isOutputHtml5())
                ? HtmlTree.TABLE(HtmlStyle.memberSummary, caption)
                : HtmlTree.TABLE(HtmlStyle.memberSummary, mw.getTableSummary(), caption);
        table.addContent(getSummaryTableHeader(mw.getSummaryTableHeader(typeElement), "col"));
        for (Content tableContent : tableContents) {
            table.addContent(tableContent);
        }
        return table;
    }

    /**
     * Get the summary table caption.
     *
     * @param methodTypes set comprising of method types to show as table caption
     * @return the caption for the summary table
     */
    public Content getTableCaption(Set<MethodTypes> methodTypes) {
        Content tabbedCaption = new HtmlTree(HtmlTag.CAPTION);
        for (MethodTypes type : methodTypes) {
            Content captionSpan;
            Content span;
            if (type.isDefaultTab()) {
                captionSpan = HtmlTree.SPAN(configuration.getResource(type.resourceKey()));
                span = HtmlTree.SPAN(type.tabId(),
                        HtmlStyle.activeTableTab, captionSpan);
            } else {
                captionSpan = HtmlTree.SPAN(getMethodTypeLinks(type));
                span = HtmlTree.SPAN(type.tabId(),
                        HtmlStyle.tableTab, captionSpan);
            }
            Content tabSpan = HtmlTree.SPAN(HtmlStyle.tabEnd, getSpace());
            span.addContent(tabSpan);
            tabbedCaption.addContent(span);
        }
        return tabbedCaption;
    }

    /**
     * Get the method type links for the table caption.
     *
     * @param methodType the method type to be displayed as link
     * @return the content tree for the method type link
     */
    public Content getMethodTypeLinks(MethodTypes methodType) {
        String jsShow = "javascript:show(" + methodType.value() +");";
        HtmlTree link = HtmlTree.A(jsShow, configuration.getResource(methodType.resourceKey()));
        return link;
    }

    /**
     * Add the inherited summary header.
     *
     * @param mw the writer for the member being documented
     * @param typeElement the te to be documented
     * @param inheritedTree the content tree to which the inherited summary header will be added
     */
    public void addInheritedSummaryHeader(AbstractMemberWriter mw, TypeElement typeElement,
            Content inheritedTree) {
        mw.addInheritedSummaryAnchor(typeElement, inheritedTree);
        mw.addInheritedSummaryLabel(typeElement, inheritedTree);
    }

    /**
     * Add the index comment.
     *
     * @param member the member being documented
     * @param contentTree the content tree to which the comment will be added
     */
    protected void addIndexComment(Element member, Content contentTree) {
        List<? extends DocTree> tags = utils.getFirstSentenceTrees(member);
        addIndexComment(member, tags, contentTree);
    }

    /**
     * Add the index comment.
     *
     * @param member the member being documented
     * @param firstSentenceTags the first sentence tags for the member to be documented
     * @param tdSummary the content tree to which the comment will be added
     */
    protected void addIndexComment(Element member, List<? extends DocTree> firstSentenceTags,
            Content tdSummary) {
        List<? extends DocTree> deprs = utils.getBlockTags(member, DocTree.Kind.DEPRECATED);
        Content div;
        if (utils.isDeprecated(member)) {
            Content deprLabel = HtmlTree.SPAN(HtmlStyle.deprecatedLabel, deprecatedPhrase);
            div = HtmlTree.DIV(HtmlStyle.block, deprLabel);
            div.addContent(getSpace());
            if (!deprs.isEmpty()) {
                addInlineDeprecatedComment(member, deprs.get(0), div);
            }
            tdSummary.addContent(div);
            return;
        } else {
            Element te = member.getEnclosingElement();
            if (te != null &&  utils.isTypeElement(te) && utils.isDeprecated(te)) {
                Content deprLabel = HtmlTree.SPAN(HtmlStyle.deprecatedLabel, deprecatedPhrase);
                div = HtmlTree.DIV(HtmlStyle.block, deprLabel);
                div.addContent(getSpace());
                tdSummary.addContent(div);
            }
        }
        addSummaryComment(member, firstSentenceTags, tdSummary);
    }

    /**
     * Add the summary type for the member.
     *
     * @param mw the writer for the member being documented
     * @param member the member to be documented
     * @param tdSummaryType the content tree to which the type will be added
     */
    public void addSummaryType(AbstractMemberWriter mw, Element member, Content tdSummaryType) {
        mw.addSummaryType(member, tdSummaryType);
    }

    /**
     * Add the summary link for the member.
     *
     * @param mw the writer for the member being documented
     * @param member the member to be documented
     * @param contentTree the content tree to which the link will be added
     */
    public void addSummaryLinkComment(AbstractMemberWriter mw, Element member, Content contentTree) {
        List<? extends DocTree> tags = utils.getFirstSentenceTrees(member);
        addSummaryLinkComment(mw, member, tags, contentTree);
    }

    /**
     * Add the summary link comment.
     *
     * @param mw the writer for the member being documented
     * @param member the member being documented
     * @param firstSentenceTags the first sentence tags for the member to be documented
     * @param tdSummary the content tree to which the comment will be added
     */
    public void addSummaryLinkComment(AbstractMemberWriter mw,
            Element member, List<? extends DocTree> firstSentenceTags, Content tdSummary) {
        addIndexComment(member, firstSentenceTags, tdSummary);
    }

    /**
     * Add the inherited member summary.
     *
     * @param mw the writer for the member being documented
     * @param typeElement the class being documented
     * @param member the member being documented
     * @param isFirst true if its the first link being documented
     * @param linksTree the content tree to which the summary will be added
     */
    public void addInheritedMemberSummary(AbstractMemberWriter mw, TypeElement typeElement,
            Element member, boolean isFirst, Content linksTree) {
        if (! isFirst) {
            linksTree.addContent(", ");
        }
        mw.addInheritedSummaryLink(typeElement, member, linksTree);
    }

    /**
     * Get the document content header tree
     *
     * @return a content tree the document content header
     */
    public Content getContentHeader() {
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.addStyle(HtmlStyle.contentContainer);
        return div;
    }

    /**
     * Add the class content tree.
     *
     * @param contentTree content tree to which the class content will be added
     * @param classContentTree class content tree which will be added to the content tree
     */
    public void addClassContentTree(Content contentTree, Content classContentTree) {
        if (configuration.allowTag(HtmlTag.MAIN)) {
            mainTree.addContent(classContentTree);
            contentTree.addContent(mainTree);
        } else {
            contentTree.addContent(classContentTree);
        }
    }

    /**
     * Add the annotation content tree.
     *
     * @param contentTree content tree to which the annotation content will be added
     * @param annotationContentTree annotation content tree which will be added to the content tree
     */
    public void addAnnotationContentTree(Content contentTree, Content annotationContentTree) {
        addClassContentTree(contentTree, annotationContentTree);
    }

    /**
     * Get the member header tree
     *
     * @return a content tree the member header
     */
    public Content getMemberTreeHeader() {
        HtmlTree li = new HtmlTree(HtmlTag.LI);
        li.addStyle(HtmlStyle.blockList);
        return li;
    }

    /**
     * Add the member tree.
     *
     * @param memberSummaryTree the content tree representing the member summary
     * @param memberTree the content tree representing the member
     */
    public void addMemberTree(Content memberSummaryTree, Content memberTree) {
        if (configuration.allowTag(HtmlTag.SECTION)) {
            HtmlTree htmlTree = HtmlTree.SECTION(getMemberTree(memberTree));
            memberSummaryTree.addContent(htmlTree);
        } else {
            memberSummaryTree.addContent(getMemberTree(memberTree));
        }
    }

    /**
     * Get the member tree
     *
     * @param contentTree the tree used to generate the complete member tree
     * @return a content tree for the member
     */
    public Content getMemberTree(Content contentTree) {
        Content ul = HtmlTree.UL(HtmlStyle.blockList, contentTree);
        return ul;
    }

    /**
     * Get the member summary tree
     *
     * @param contentTree the tree used to generate the member summary tree
     * @return a content tree for the member summary
     */
    public Content getMemberSummaryTree(Content contentTree) {
        return getMemberTree(HtmlStyle.summary, contentTree);
    }

    /**
     * Get the member details tree
     *
     * @param contentTree the tree used to generate the member details tree
     * @return a content tree for the member details
     */
    public Content getMemberDetailsTree(Content contentTree) {
        return getMemberTree(HtmlStyle.details, contentTree);
    }

    /**
     * Get the member tree
     *
     * @param style the style class to be added to the content tree
     * @param contentTree the tree used to generate the complete member tree
     */
    public Content getMemberTree(HtmlStyle style, Content contentTree) {
        Content div = HtmlTree.DIV(style, getMemberTree(contentTree));
        return div;
    }
}
