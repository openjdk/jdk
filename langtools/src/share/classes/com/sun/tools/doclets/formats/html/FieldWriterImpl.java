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

import com.sun.javadoc.*;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * Writes field documentation in HTML format.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Robert Field
 * @author Atul M Dambalkar
 * @author Jamie Ho (rewrite)
 * @author Bhavesh Patel (Modified)
 */
public class FieldWriterImpl extends AbstractMemberWriter
    implements FieldWriter, MemberSummaryWriter {

    public FieldWriterImpl(SubWriterHolderWriter writer, ClassDoc classdoc) {
        super(writer, classdoc);
    }

    public FieldWriterImpl(SubWriterHolderWriter writer) {
        super(writer);
    }

    /**
     * {@inheritDoc}
     */
    public Content getMemberSummaryHeader(ClassDoc classDoc,
            Content memberSummaryTree) {
        memberSummaryTree.addContent(HtmlConstants.START_OF_FIELD_SUMMARY);
        Content memberTree = writer.getMemberTreeHeader();
        writer.addSummaryHeader(this, classDoc, memberTree);
        return memberTree;
    }

    /**
     * {@inheritDoc}
     */
    public Content getFieldDetailsTreeHeader(ClassDoc classDoc,
            Content memberDetailsTree) {
        memberDetailsTree.addContent(HtmlConstants.START_OF_FIELD_DETAILS);
        Content fieldDetailsTree = writer.getMemberTreeHeader();
        fieldDetailsTree.addContent(writer.getMarkerAnchor(
                SectionName.FIELD_DETAIL));
        Content heading = HtmlTree.HEADING(HtmlConstants.DETAILS_HEADING,
                writer.fieldDetailsLabel);
        fieldDetailsTree.addContent(heading);
        return fieldDetailsTree;
    }

    /**
     * {@inheritDoc}
     */
    public Content getFieldDocTreeHeader(FieldDoc field,
            Content fieldDetailsTree) {
        fieldDetailsTree.addContent(
                writer.getMarkerAnchor(field.name()));
        Content fieldDocTree = writer.getMemberTreeHeader();
        Content heading = new HtmlTree(HtmlConstants.MEMBER_HEADING);
        heading.addContent(field.name());
        fieldDocTree.addContent(heading);
        return fieldDocTree;
    }

    /**
     * {@inheritDoc}
     */
    public Content getSignature(FieldDoc field) {
        Content pre = new HtmlTree(HtmlTag.PRE);
        writer.addAnnotationInfo(field, pre);
        addModifiers(field, pre);
        Content fieldlink = writer.getLink(new LinkInfoImpl(
                configuration, LinkInfoImpl.Kind.MEMBER, field.type()));
        pre.addContent(fieldlink);
        pre.addContent(" ");
        if (configuration.linksource) {
            Content fieldName = new StringContent(field.name());
            writer.addSrcLink(field, fieldName, pre);
        } else {
            addName(field.name(), pre);
        }
        return pre;
    }

    /**
     * {@inheritDoc}
     */
    public void addDeprecated(FieldDoc field, Content fieldDocTree) {
        addDeprecatedInfo(field, fieldDocTree);
    }

    /**
     * {@inheritDoc}
     */
    public void addComments(FieldDoc field, Content fieldDocTree) {
        ClassDoc holder = field.containingClass();
        if (field.inlineTags().length > 0) {
            if (holder.equals(classdoc) ||
                    (! (holder.isPublic() || Util.isLinkable(holder, configuration)))) {
                writer.addInlineComment(field, fieldDocTree);
            } else {
                Content link =
                        writer.getDocLink(LinkInfoImpl.Kind.FIELD_DOC_COPY,
                        holder, field,
                        holder.isIncluded() ?
                            holder.typeName() : holder.qualifiedTypeName(),
                            false);
                Content codeLink = HtmlTree.CODE(link);
                Content descfrmLabel = HtmlTree.SPAN(HtmlStyle.descfrmTypeLabel, holder.isClass()?
                   writer.descfrmClassLabel : writer.descfrmInterfaceLabel);
                descfrmLabel.addContent(writer.getSpace());
                descfrmLabel.addContent(codeLink);
                fieldDocTree.addContent(HtmlTree.DIV(HtmlStyle.block, descfrmLabel));
                writer.addInlineComment(field, fieldDocTree);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void addTags(FieldDoc field, Content fieldDocTree) {
        writer.addTagsInfo(field, fieldDocTree);
    }

    /**
     * {@inheritDoc}
     */
    public Content getFieldDetails(Content fieldDetailsTree) {
        return getMemberTree(fieldDetailsTree);
    }

    /**
     * {@inheritDoc}
     */
    public Content getFieldDoc(Content fieldDocTree,
            boolean isLastContent) {
        return getMemberTree(fieldDocTree, isLastContent);
    }

    /**
     * Close the writer.
     */
    public void close() throws IOException {
        writer.close();
    }

    public int getMemberKind() {
        return VisibleMemberMap.FIELDS;
    }

    /**
     * {@inheritDoc}
     */
    public void addSummaryLabel(Content memberTree) {
        Content label = HtmlTree.HEADING(HtmlConstants.SUMMARY_HEADING,
                writer.getResource("doclet.Field_Summary"));
        memberTree.addContent(label);
    }

    /**
     * {@inheritDoc}
     */
    public String getTableSummary() {
        return configuration.getText("doclet.Member_Table_Summary",
                configuration.getText("doclet.Field_Summary"),
                configuration.getText("doclet.fields"));
    }

    /**
     * {@inheritDoc}
     */
    public Content getCaption() {
        return configuration.getResource("doclet.Fields");
    }

    /**
     * {@inheritDoc}
     */
    public String[] getSummaryTableHeader(ProgramElementDoc member) {
        String[] header = new String[] {
            writer.getModifierTypeHeader(),
            configuration.getText("doclet.0_and_1",
                    configuration.getText("doclet.Field"),
                    configuration.getText("doclet.Description"))
        };
        return header;
    }

    /**
     * {@inheritDoc}
     */
    public void addSummaryAnchor(ClassDoc cd, Content memberTree) {
        memberTree.addContent(writer.getMarkerAnchor(
                SectionName.FIELD_SUMMARY));
    }

    /**
     * {@inheritDoc}
     */
    public void addInheritedSummaryAnchor(ClassDoc cd, Content inheritedTree) {
        inheritedTree.addContent(writer.getMarkerAnchor(
                SectionName.FIELDS_INHERITANCE, configuration.getClassName(cd)));
    }

    /**
     * {@inheritDoc}
     */
    public void addInheritedSummaryLabel(ClassDoc cd, Content inheritedTree) {
        Content classLink = writer.getPreQualifiedClassLink(
                LinkInfoImpl.Kind.MEMBER, cd, false);
        Content label = new StringContent(cd.isClass() ?
            configuration.getText("doclet.Fields_Inherited_From_Class") :
            configuration.getText("doclet.Fields_Inherited_From_Interface"));
        Content labelHeading = HtmlTree.HEADING(HtmlConstants.INHERITED_SUMMARY_HEADING,
                label);
        labelHeading.addContent(writer.getSpace());
        labelHeading.addContent(classLink);
        inheritedTree.addContent(labelHeading);
    }

    /**
     * {@inheritDoc}
     */
    protected void addSummaryLink(LinkInfoImpl.Kind context, ClassDoc cd, ProgramElementDoc member,
            Content tdSummary) {
        Content memberLink = HtmlTree.SPAN(HtmlStyle.memberNameLink,
                writer.getDocLink(context, cd , (MemberDoc) member, member.name(), false));
        Content code = HtmlTree.CODE(memberLink);
        tdSummary.addContent(code);
    }

    /**
     * {@inheritDoc}
     */
    protected void addInheritedSummaryLink(ClassDoc cd,
            ProgramElementDoc member, Content linksTree) {
        linksTree.addContent(
                writer.getDocLink(LinkInfoImpl.Kind.MEMBER, cd, (MemberDoc)member,
                member.name(), false));
    }

    /**
     * {@inheritDoc}
     */
    protected void addSummaryType(ProgramElementDoc member, Content tdSummaryType) {
        FieldDoc field = (FieldDoc)member;
        addModifierAndType(field, field.type(), tdSummaryType);
    }

    /**
     * {@inheritDoc}
     */
    protected Content getDeprecatedLink(ProgramElementDoc member) {
        return writer.getDocLink(LinkInfoImpl.Kind.MEMBER,
                (MemberDoc) member, ((FieldDoc)member).qualifiedName());
    }

    /**
     * {@inheritDoc}
     */
    protected Content getNavSummaryLink(ClassDoc cd, boolean link) {
        if (link) {
            if (cd == null) {
                return writer.getHyperLink(
                        SectionName.FIELD_SUMMARY,
                        writer.getResource("doclet.navField"));
            } else {
                return writer.getHyperLink(
                        SectionName.FIELDS_INHERITANCE,
                        configuration.getClassName(cd), writer.getResource("doclet.navField"));
            }
        } else {
            return writer.getResource("doclet.navField");
        }
    }

    /**
     * {@inheritDoc}
     */
    protected void addNavDetailLink(boolean link, Content liNav) {
        if (link) {
            liNav.addContent(writer.getHyperLink(
                    SectionName.FIELD_DETAIL,
                    writer.getResource("doclet.navField")));
        } else {
            liNav.addContent(writer.getResource("doclet.navField"));
        }
    }
}
