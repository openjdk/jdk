/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.util.*;

import com.sun.javadoc.*;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

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

    public SubWriterHolderWriter(ConfigurationImpl configuration, DocPath filename)
            throws IOException {
        super(configuration, filename);
    }

    /**
     * Add the summary header.
     *
     * @param mw the writer for the member being documented
     * @param cd the classdoc to be documented
     * @param memberTree the content tree to which the summary header will be added
     */
    public void addSummaryHeader(AbstractMemberWriter mw, ClassDoc cd,
            Content memberTree) {
        mw.addSummaryAnchor(cd, memberTree);
        mw.addSummaryLabel(memberTree);
    }

    /**
     * Get the summary table.
     *
     * @param mw the writer for the member being documented
     * @param cd the classdoc to be documented
     * @param tableContents list of summary table contents
     * @param showTabs true if the table needs to show tabs
     * @return the content tree for the summary table
     */
    public Content getSummaryTableTree(AbstractMemberWriter mw, ClassDoc cd,
            List<Content> tableContents, boolean showTabs) {
        Content caption;
        if (showTabs) {
            caption = getTableCaption(mw.methodTypes);
            generateMethodTypesScript(mw.typeMap, mw.methodTypes);
        }
        else {
            caption = getTableCaption(mw.getCaption());
        }
        Content table = HtmlTree.TABLE(HtmlStyle.memberSummary, 0, 3, 0,
                mw.getTableSummary(), caption);
        table.addContent(getSummaryTableHeader(mw.getSummaryTableHeader(cd), "col"));
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
                captionSpan = HtmlTree.SPAN(new StringContent(type.text()));
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
        HtmlTree link = HtmlTree.A(jsShow, new StringContent(methodType.text()));
        return link;
    }

    /**
     * Add the inherited summary header.
     *
     * @param mw the writer for the member being documented
     * @param cd the classdoc to be documented
     * @param inheritedTree the content tree to which the inherited summary header will be added
     */
    public void addInheritedSummaryHeader(AbstractMemberWriter mw, ClassDoc cd,
            Content inheritedTree) {
        mw.addInheritedSummaryAnchor(cd, inheritedTree);
        mw.addInheritedSummaryLabel(cd, inheritedTree);
    }

    /**
     * Add the index comment.
     *
     * @param member the member being documented
     * @param contentTree the content tree to which the comment will be added
     */
    protected void addIndexComment(Doc member, Content contentTree) {
        addIndexComment(member, member.firstSentenceTags(), contentTree);
    }

    /**
     * Add the index comment.
     *
     * @param member the member being documented
     * @param firstSentenceTags the first sentence tags for the member to be documented
     * @param tdSummary the content tree to which the comment will be added
     */
    protected void addIndexComment(Doc member, Tag[] firstSentenceTags,
            Content tdSummary) {
        Tag[] deprs = member.tags("deprecated");
        Content div;
        if (Util.isDeprecated((ProgramElementDoc) member)) {
            Content deprLabel = HtmlTree.SPAN(HtmlStyle.deprecatedLabel, deprecatedPhrase);
            div = HtmlTree.DIV(HtmlStyle.block, deprLabel);
            div.addContent(getSpace());
            if (deprs.length > 0) {
                addInlineDeprecatedComment(member, deprs[0], div);
            }
            tdSummary.addContent(div);
            return;
        } else {
            ClassDoc cd = ((ProgramElementDoc)member).containingClass();
            if (cd != null && Util.isDeprecated(cd)) {
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
    public void addSummaryType(AbstractMemberWriter mw, ProgramElementDoc member,
            Content tdSummaryType) {
        mw.addSummaryType(member, tdSummaryType);
    }

    /**
     * Add the summary link for the member.
     *
     * @param mw the writer for the member being documented
     * @param member the member to be documented
     * @param contentTree the content tree to which the link will be added
     */
    public void addSummaryLinkComment(AbstractMemberWriter mw,
            ProgramElementDoc member, Content contentTree) {
        addSummaryLinkComment(mw, member, member.firstSentenceTags(), contentTree);
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
            ProgramElementDoc member, Tag[] firstSentenceTags, Content tdSummary) {
        addIndexComment(member, firstSentenceTags, tdSummary);
    }

    /**
     * Add the inherited member summary.
     *
     * @param mw the writer for the member being documented
     * @param cd the class being documented
     * @param member the member being documented
     * @param isFirst true if its the first link being documented
     * @param linksTree the content tree to which the summary will be added
     */
    public void addInheritedMemberSummary(AbstractMemberWriter mw, ClassDoc cd,
            ProgramElementDoc member, boolean isFirst, Content linksTree) {
        if (! isFirst) {
            linksTree.addContent(", ");
        }
        mw.addInheritedSummaryLink(cd, member, linksTree);
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
