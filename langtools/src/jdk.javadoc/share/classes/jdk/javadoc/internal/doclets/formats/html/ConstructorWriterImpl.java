/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlAttr;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlConstants;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.ConstructorWriter;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.MemberSummaryWriter;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberMap;


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
     * @param typeElement the class being documented.
     */
    public ConstructorWriterImpl(SubWriterHolderWriter writer, TypeElement typeElement) {
        super(writer, typeElement);

        VisibleMemberMap visibleMemberMap = configuration.getVisibleMemberMap(
                typeElement,
                VisibleMemberMap.Kind.CONSTRUCTORS);
        List<Element> constructors = visibleMemberMap.getMembers(typeElement);
        for (Element constructor : constructors) {
            if (utils.isProtected(constructor) || utils.isPrivate(constructor)) {
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
    @Override
    public Content getMemberSummaryHeader(TypeElement typeElement,
            Content memberSummaryTree) {
        memberSummaryTree.addContent(HtmlConstants.START_OF_CONSTRUCTOR_SUMMARY);
        Content memberTree = writer.getMemberTreeHeader();
        writer.addSummaryHeader(this, typeElement, memberTree);
        return memberTree;
    }

    /**
     * {@inheritDoc}
     */
    public void addMemberTree(Content memberSummaryTree, Content memberTree) {
        writer.addMemberTree(memberSummaryTree, memberTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getConstructorDetailsTreeHeader(TypeElement typeElement,
            Content memberDetailsTree) {
        memberDetailsTree.addContent(HtmlConstants.START_OF_CONSTRUCTOR_DETAILS);
        Content constructorDetailsTree = writer.getMemberTreeHeader();
        constructorDetailsTree.addContent(writer.getMarkerAnchor(
                SectionName.CONSTRUCTOR_DETAIL));
        Content heading = HtmlTree.HEADING(HtmlConstants.DETAILS_HEADING,
                contents.constructorDetailsLabel);
        constructorDetailsTree.addContent(heading);
        return constructorDetailsTree;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getConstructorDocTreeHeader(ExecutableElement constructor,
            Content constructorDetailsTree) {
        String erasureAnchor;
        if ((erasureAnchor = getErasureAnchor(constructor)) != null) {
            constructorDetailsTree.addContent(writer.getMarkerAnchor((erasureAnchor)));
        }
        constructorDetailsTree.addContent(
                writer.getMarkerAnchor(writer.getAnchor(constructor)));
        Content constructorDocTree = writer.getMemberTreeHeader();
        Content heading = new HtmlTree(HtmlConstants.MEMBER_HEADING);
        heading.addContent(name(constructor));
        constructorDocTree.addContent(heading);
        return constructorDocTree;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getSignature(ExecutableElement constructor) {
        Content pre = new HtmlTree(HtmlTag.PRE);
        writer.addAnnotationInfo(constructor, pre);
        int annotationLength = pre.charCount();
        addModifiers(constructor, pre);
        if (configuration.linksource) {
            Content constructorName = new StringContent(name(constructor));
            writer.addSrcLink(constructor, constructorName, pre);
        } else {
            addName(name(constructor), pre);
        }
        int indent = pre.charCount() - annotationLength;
        addParameters(constructor, pre, indent);
        addExceptions(constructor, pre, indent);
        return pre;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSummaryColumnStyleAndScope(HtmlTree thTree) {
        if (foundNonPubConstructor) {
            thTree.addStyle(HtmlStyle.colSecond);
        } else {
            thTree.addStyle(HtmlStyle.colFirst);
        }
        thTree.addAttr(HtmlAttr.SCOPE, "row");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addDeprecated(ExecutableElement constructor, Content constructorDocTree) {
        addDeprecatedInfo(constructor, constructorDocTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addComments(ExecutableElement constructor, Content constructorDocTree) {
        addComment(constructor, constructorDocTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addTags(ExecutableElement constructor, Content constructorDocTree) {
        writer.addTagsInfo(constructor, constructorDocTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getConstructorDetails(Content constructorDetailsTree) {
        if (configuration.allowTag(HtmlTag.SECTION)) {
            HtmlTree htmlTree = HtmlTree.SECTION(getMemberTree(constructorDetailsTree));
            return htmlTree;
        }
        return getMemberTree(constructorDetailsTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getConstructorDoc(Content constructorDocTree,
            boolean isLastContent) {
        return getMemberTree(constructorDocTree, isLastContent);
    }

    /**
     * Let the writer know whether a non public constructor was found.
     *
     * @param foundNonPubConstructor true if we found a non public constructor.
     */
    @Override
    public void setFoundNonPubConstructor(boolean foundNonPubConstructor) {
        this.foundNonPubConstructor = foundNonPubConstructor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addSummaryLabel(Content memberTree) {
        Content label = HtmlTree.HEADING(HtmlConstants.SUMMARY_HEADING,
                contents.constructorSummaryLabel);
        memberTree.addContent(label);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTableSummary() {
        return resources.getText("doclet.Member_Table_Summary",
                resources.getText("doclet.Constructor_Summary"),
                resources.getText("doclet.constructors"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getCaption() {
        return contents.constructors;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getSummaryTableHeader(Element member) {
        List<String> header = new ArrayList<>();
        if (foundNonPubConstructor) {
            header.add(resources.getText("doclet.Modifier"));
        }
        header.add(resources.getText("doclet.Constructor"));
        header.add(resources.getText("doclet.Description"));
        return header;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addSummaryAnchor(TypeElement typeElement, Content memberTree) {
        memberTree.addContent(writer.getMarkerAnchor(
                SectionName.CONSTRUCTOR_SUMMARY));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addInheritedSummaryAnchor(TypeElement typeElement, Content inheritedTree) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addInheritedSummaryLabel(TypeElement typeElement, Content inheritedTree) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Content getNavSummaryLink(TypeElement typeElement, boolean link) {
        if (link) {
            return writer.getHyperLink(SectionName.CONSTRUCTOR_SUMMARY,
                    contents.navConstructor);
        } else {
            return contents.navConstructor;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addNavDetailLink(boolean link, Content liNav) {
        if (link) {
            liNav.addContent(writer.getHyperLink(
                    SectionName.CONSTRUCTOR_DETAIL,
                    contents.navConstructor));
        } else {
            liNav.addContent(contents.navConstructor);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addSummaryType(Element member, Content tdSummaryType) {
        if (foundNonPubConstructor) {
            Content code = new HtmlTree(HtmlTag.CODE);
            if (utils.isProtected(member)) {
                code.addContent("protected ");
            } else if (utils.isPrivate(member)) {
                code.addContent("private ");
            } else if (utils.isPublic(member)) {
                code.addContent(Contents.SPACE);
            } else {
                code.addContent(
                        configuration.getText("doclet.Package_private"));
            }
            tdSummaryType.addContent(code);
        }
    }
}
