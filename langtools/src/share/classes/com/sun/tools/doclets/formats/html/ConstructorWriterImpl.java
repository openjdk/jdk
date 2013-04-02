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
 * Writes constructor documentation.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Robert Field
 * @author Atul M Dambalkar
 * @author Bhavesh Patel (Modified)
 */
public class ConstructorWriterImpl extends AbstractExecutableMemberWriter
    implements ConstructorWriter, MemberSummaryWriter {

    private boolean foundNonPubConstructor = false;

    /**
     * Construct a new ConstructorWriterImpl.
     *
     * @param writer The writer for the class that the constructors belong to.
     * @param classDoc the class being documented.
     */
    public ConstructorWriterImpl(SubWriterHolderWriter writer,
            ClassDoc classDoc) {
        super(writer, classDoc);
        VisibleMemberMap visibleMemberMap = new VisibleMemberMap(classDoc,
            VisibleMemberMap.CONSTRUCTORS, configuration);
        List<ProgramElementDoc> constructors = new ArrayList<ProgramElementDoc>(visibleMemberMap.getMembersFor(classDoc));
        for (int i = 0; i < constructors.size(); i++) {
            if ((constructors.get(i)).isProtected() ||
                (constructors.get(i)).isPrivate()) {
                setFoundNonPubConstructor(true);
            }
        }
    }

    /**
     * Construct a new ConstructorWriterImpl.
     *
     * @param writer The writer for the class that the constructors belong to.
     */
    public ConstructorWriterImpl(SubWriterHolderWriter writer) {
        super(writer);
    }

    /**
     * {@inheritDoc}
     */
    public Content getMemberSummaryHeader(ClassDoc classDoc,
            Content memberSummaryTree) {
        memberSummaryTree.addContent(HtmlConstants.START_OF_CONSTRUCTOR_SUMMARY);
        Content memberTree = writer.getMemberTreeHeader();
        writer.addSummaryHeader(this, classDoc, memberTree);
        return memberTree;
    }

    /**
     * {@inheritDoc}
     */
    public Content getConstructorDetailsTreeHeader(ClassDoc classDoc,
            Content memberDetailsTree) {
        memberDetailsTree.addContent(HtmlConstants.START_OF_CONSTRUCTOR_DETAILS);
        Content constructorDetailsTree = writer.getMemberTreeHeader();
        constructorDetailsTree.addContent(writer.getMarkerAnchor("constructor_detail"));
        Content heading = HtmlTree.HEADING(HtmlConstants.DETAILS_HEADING,
                writer.constructorDetailsLabel);
        constructorDetailsTree.addContent(heading);
        return constructorDetailsTree;
    }

    /**
     * {@inheritDoc}
     */
    public Content getConstructorDocTreeHeader(ConstructorDoc constructor,
            Content constructorDetailsTree) {
        String erasureAnchor;
        if ((erasureAnchor = getErasureAnchor(constructor)) != null) {
            constructorDetailsTree.addContent(writer.getMarkerAnchor((erasureAnchor)));
        }
        constructorDetailsTree.addContent(
                writer.getMarkerAnchor(writer.getAnchor(constructor)));
        Content constructorDocTree = writer.getMemberTreeHeader();
        Content heading = new HtmlTree(HtmlConstants.MEMBER_HEADING);
        heading.addContent(constructor.name());
        constructorDocTree.addContent(heading);
        return constructorDocTree;
    }

    /**
     * {@inheritDoc}
     */
    public Content getSignature(ConstructorDoc constructor) {
        writer.displayLength = 0;
        Content pre = new HtmlTree(HtmlTag.PRE);
        writer.addAnnotationInfo(constructor, pre);
        addModifiers(constructor, pre);
        if (configuration.linksource) {
            Content constructorName = new StringContent(constructor.name());
            writer.addSrcLink(constructor, constructorName, pre);
        } else {
            addName(constructor.name(), pre);
        }
        addParameters(constructor, pre);
        writer.addReceiverAnnotationInfo(constructor, pre);
        addExceptions(constructor, pre);
        return pre;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSummaryColumnStyle(HtmlTree tdTree) {
        if (foundNonPubConstructor)
            tdTree.addStyle(HtmlStyle.colLast);
        else
            tdTree.addStyle(HtmlStyle.colOne);
    }

    /**
     * {@inheritDoc}
     */
    public void addDeprecated(ConstructorDoc constructor, Content constructorDocTree) {
        addDeprecatedInfo(constructor, constructorDocTree);
    }

    /**
     * {@inheritDoc}
     */
    public void addComments(ConstructorDoc constructor, Content constructorDocTree) {
        addComment(constructor, constructorDocTree);
    }

    /**
     * {@inheritDoc}
     */
    public void addTags(ConstructorDoc constructor, Content constructorDocTree) {
        writer.addTagsInfo(constructor, constructorDocTree);
    }

    /**
     * {@inheritDoc}
     */
    public Content getConstructorDetails(Content constructorDetailsTree) {
        return getMemberTree(constructorDetailsTree);
    }

    /**
     * {@inheritDoc}
     */
    public Content getConstructorDoc(Content constructorDocTree,
            boolean isLastContent) {
        return getMemberTree(constructorDocTree, isLastContent);
    }

    /**
     * Close the writer.
     */
    public void close() throws IOException {
        writer.close();
    }

    /**
     * Let the writer know whether a non public constructor was found.
     *
     * @param foundNonPubConstructor true if we found a non public constructor.
     */
    public void setFoundNonPubConstructor(boolean foundNonPubConstructor) {
        this.foundNonPubConstructor = foundNonPubConstructor;
    }

    /**
     * {@inheritDoc}
     */
    public void addSummaryLabel(Content memberTree) {
        Content label = HtmlTree.HEADING(HtmlConstants.SUMMARY_HEADING,
                writer.getResource("doclet.Constructor_Summary"));
        memberTree.addContent(label);
    }

    /**
     * {@inheritDoc}
     */
    public String getTableSummary() {
        return configuration.getText("doclet.Member_Table_Summary",
                configuration.getText("doclet.Constructor_Summary"),
                configuration.getText("doclet.constructors"));
    }

    /**
     * {@inheritDoc}
     */
    public String getCaption() {
        return configuration.getText("doclet.Constructors");
    }

    /**
     * {@inheritDoc}
     */
    public String[] getSummaryTableHeader(ProgramElementDoc member) {
        String[] header;
        if (foundNonPubConstructor) {
            header = new String[] {
                configuration.getText("doclet.Modifier"),
                configuration.getText("doclet.0_and_1",
                        configuration.getText("doclet.Constructor"),
                        configuration.getText("doclet.Description"))
            };
        }
        else {
            header = new String[] {
                configuration.getText("doclet.0_and_1",
                        configuration.getText("doclet.Constructor"),
                        configuration.getText("doclet.Description"))
            };
        }
        return header;
    }

    /**
     * {@inheritDoc}
     */
    public void addSummaryAnchor(ClassDoc cd, Content memberTree) {
        memberTree.addContent(writer.getMarkerAnchor("constructor_summary"));
    }

    /**
     * {@inheritDoc}
     */
    public void addInheritedSummaryAnchor(ClassDoc cd, Content inheritedTree) {
    }

    /**
     * {@inheritDoc}
     */
    public void addInheritedSummaryLabel(ClassDoc cd, Content inheritedTree) {
    }

    public int getMemberKind() {
        return VisibleMemberMap.CONSTRUCTORS;
    }

    /**
     * {@inheritDoc}
     */
    protected Content getNavSummaryLink(ClassDoc cd, boolean link) {
        if (link) {
            return writer.getHyperLink("constructor_summary",
                    writer.getResource("doclet.navConstructor"));
        } else {
            return writer.getResource("doclet.navConstructor");
        }
    }

    /**
     * {@inheritDoc}
     */
    protected void addNavDetailLink(boolean link, Content liNav) {
        if (link) {
            liNav.addContent(writer.getHyperLink("constructor_detail",
                    writer.getResource("doclet.navConstructor")));
        } else {
            liNav.addContent(writer.getResource("doclet.navConstructor"));
        }
    }

    /**
     * {@inheritDoc}
     */
    protected void addSummaryType(ProgramElementDoc member, Content tdSummaryType) {
        if (foundNonPubConstructor) {
            Content code = new HtmlTree(HtmlTag.CODE);
            if (member.isProtected()) {
                code.addContent("protected ");
            } else if (member.isPrivate()) {
                code.addContent("private ");
            } else if (member.isPublic()) {
                code.addContent(writer.getSpace());
            } else {
                code.addContent(
                        configuration.getText("doclet.Package_private"));
            }
            tdSummaryType.addContent(code);
        }
    }
}
