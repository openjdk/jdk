/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;
import com.sun.tools.doclets.formats.html.markup.*;

/**
 * This abstract class exists to provide functionality needed in the
 * the formatting of member information.  Since AbstractSubWriter and its
 * subclasses control this, they would be the logical place to put this.
 * However, because each member type has its own subclass, subclassing
 * can not be used effectively to change formatting.  The concrete
 * class subclass of this class can be subclassed to change formatting.
 *
 * @see AbstractMemberWriter
 * @see ClassWriterImpl
 *
 * @author Robert Field
 * @author Atul M Dambalkar
 * @author Bhavesh Patel (Modified)
 */
public abstract class SubWriterHolderWriter extends HtmlDocletWriter {

    public SubWriterHolderWriter(ConfigurationImpl configuration,
                                 String filename) throws IOException {
        super(configuration, filename);
    }


    public SubWriterHolderWriter(ConfigurationImpl configuration,
                                 String path, String filename, String relpath)
                                 throws IOException {
        super(configuration, path, filename, relpath);
    }

    public void printTypeSummaryHeader() {
        tdIndex();
        font("-1");
        code();
    }

    public void printTypeSummaryFooter() {
        codeEnd();
        fontEnd();
        tdEnd();
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
     * @return the content tree for the summary table
     */
    public Content getSummaryTableTree(AbstractMemberWriter mw, ClassDoc cd) {
        Content table = HtmlTree.TABLE(HtmlStyle.overviewSummary, 0, 3, 0,
                mw.getTableSummary(), getTableCaption(mw.getCaption()));
        table.addContent(getSummaryTableHeader(mw.getSummaryTableHeader(cd), "col"));
        return table;
    }

    public void printTableHeadingBackground(String str) {
        tableIndexDetail();
        tableHeaderStart("#CCCCFF", 1);
        strong(str);
        tableHeaderEnd();
        tableEnd();
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

    public void printSummaryFooter(AbstractMemberWriter mw, ClassDoc cd) {
        tableEnd();
        space();
    }

    public void printInheritedSummaryFooter(AbstractMemberWriter mw, ClassDoc cd) {
        codeEnd();
        summaryRowEnd();
        trEnd();
        tableEnd();
        space();
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

    protected void printIndexComment(Doc member, Tag[] firstSentenceTags) {
        Tag[] deprs = member.tags("deprecated");
        if (Util.isDeprecated((ProgramElementDoc) member)) {
            strongText("doclet.Deprecated");
            space();
            if (deprs.length > 0) {
                printInlineDeprecatedComment(member, deprs[0]);
            }
            return;
        } else {
            ClassDoc cd = ((ProgramElementDoc)member).containingClass();
            if (cd != null && Util.isDeprecated(cd)) {
                strongText("doclet.Deprecated"); space();
            }
        }
        printSummaryComment(member, firstSentenceTags);
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
            Content strong = HtmlTree.STRONG(deprecatedPhrase);
            div = HtmlTree.DIV(HtmlStyle.block, strong);
            div.addContent(getSpace());
            if (deprs.length > 0) {
                addInlineDeprecatedComment(member, deprs[0], div);
            }
            tdSummary.addContent(div);
            return;
        } else {
            ClassDoc cd = ((ProgramElementDoc)member).containingClass();
            if (cd != null && Util.isDeprecated(cd)) {
                Content strong = HtmlTree.STRONG(deprecatedPhrase);
                div = HtmlTree.DIV(HtmlStyle.block, strong);
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

    public void printSummaryLinkComment(AbstractMemberWriter mw,
                                        ProgramElementDoc member,
                                        Tag[] firstSentenceTags) {
        codeEnd();
        println();
        br();
        printNbsps();
        printIndexComment(member, firstSentenceTags);
        summaryRowEnd();
        trEnd();
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

    public void printMemberHeader() {
        hr();
    }

    public void printMemberFooter() {
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
