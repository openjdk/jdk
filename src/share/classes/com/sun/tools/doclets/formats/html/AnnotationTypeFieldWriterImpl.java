/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Writes annotation type field documentation in HTML format.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Bhavesh Patel
 */
public class AnnotationTypeFieldWriterImpl extends AbstractMemberWriter
    implements AnnotationTypeFieldWriter, MemberSummaryWriter {

    /**
     * Construct a new AnnotationTypeFieldWriterImpl.
     *
     * @param writer         the writer that will write the output.
     * @param annotationType the AnnotationType that holds this member.
     */
    public AnnotationTypeFieldWriterImpl(SubWriterHolderWriter writer,
            AnnotationTypeDoc annotationType) {
        super(writer, annotationType);
    }

    /**
     * {@inheritDoc}
     */
    public Content getMemberSummaryHeader(ClassDoc classDoc,
            Content memberSummaryTree) {
        memberSummaryTree.addContent(
                HtmlConstants.START_OF_ANNOTATION_TYPE_FIELD_SUMMARY);
        Content memberTree = writer.getMemberTreeHeader();
        writer.addSummaryHeader(this, classDoc, memberTree);
        return memberTree;
    }

    /**
     * {@inheritDoc}
     */
    public Content getMemberTreeHeader() {
        return writer.getMemberTreeHeader();
    }

    /**
     * {@inheritDoc}
     */
    public void addAnnotationFieldDetailsMarker(Content memberDetails) {
        memberDetails.addContent(HtmlConstants.START_OF_ANNOTATION_TYPE_FIELD_DETAILS);
    }

    /**
     * {@inheritDoc}
     */
    public void addAnnotationDetailsTreeHeader(ClassDoc classDoc,
            Content memberDetailsTree) {
        if (!writer.printedAnnotationFieldHeading) {
            memberDetailsTree.addContent(writer.getMarkerAnchor(
                    "annotation_type_field_detail"));
            Content heading = HtmlTree.HEADING(HtmlConstants.DETAILS_HEADING,
                    writer.fieldDetailsLabel);
            memberDetailsTree.addContent(heading);
            writer.printedAnnotationFieldHeading = true;
        }
    }

    /**
     * {@inheritDoc}
     */
    public Content getAnnotationDocTreeHeader(MemberDoc member,
            Content annotationDetailsTree) {
        annotationDetailsTree.addContent(
                writer.getMarkerAnchor(member.name()));
        Content annotationDocTree = writer.getMemberTreeHeader();
        Content heading = new HtmlTree(HtmlConstants.MEMBER_HEADING);
        heading.addContent(member.name());
        annotationDocTree.addContent(heading);
        return annotationDocTree;
    }

    /**
     * {@inheritDoc}
     */
    public Content getSignature(MemberDoc member) {
        Content pre = new HtmlTree(HtmlTag.PRE);
        writer.addAnnotationInfo(member, pre);
        addModifiers(member, pre);
        Content link =
                writer.getLink(new LinkInfoImpl(configuration,
                        LinkInfoImpl.Kind.MEMBER, getType(member)));
        pre.addContent(link);
        pre.addContent(writer.getSpace());
        if (configuration.linksource) {
            Content memberName = new StringContent(member.name());
            writer.addSrcLink(member, memberName, pre);
        } else {
            addName(member.name(), pre);
        }
        return pre;
    }

    /**
     * {@inheritDoc}
     */
    public void addDeprecated(MemberDoc member, Content annotationDocTree) {
        addDeprecatedInfo(member, annotationDocTree);
    }

    /**
     * {@inheritDoc}
     */
    public void addComments(MemberDoc member, Content annotationDocTree) {
        addComment(member, annotationDocTree);
    }

    /**
     * {@inheritDoc}
     */
    public void addTags(MemberDoc member, Content annotationDocTree) {
        writer.addTagsInfo(member, annotationDocTree);
    }

    /**
     * {@inheritDoc}
     */
    public Content getAnnotationDetails(Content annotationDetailsTree) {
        return getMemberTree(annotationDetailsTree);
    }

    /**
     * {@inheritDoc}
     */
    public Content getAnnotationDoc(Content annotationDocTree,
            boolean isLastContent) {
        return getMemberTree(annotationDocTree, isLastContent);
    }

    /**
     * Close the writer.
     */
    public void close() throws IOException {
        writer.close();
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
                    configuration.getText("doclet.Fields"),
                    configuration.getText("doclet.Description"))
        };
        return header;
    }

    /**
     * {@inheritDoc}
     */
    public void addSummaryAnchor(ClassDoc cd, Content memberTree) {
        memberTree.addContent(writer.getMarkerAnchor(
                "annotation_type_field_summary"));
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

    /**
     * {@inheritDoc}
     */
    protected void addSummaryLink(LinkInfoImpl.Kind context, ClassDoc cd, ProgramElementDoc member,
            Content tdSummary) {
        Content strong = HtmlTree.SPAN(HtmlStyle.strong,
                writer.getDocLink(context, (MemberDoc) member, member.name(), false));
        Content code = HtmlTree.CODE(strong);
        tdSummary.addContent(code);
    }

    /**
     * {@inheritDoc}
     */
    protected void addInheritedSummaryLink(ClassDoc cd,
            ProgramElementDoc member, Content linksTree) {
        //Not applicable.
    }

    /**
     * {@inheritDoc}
     */
    protected void addSummaryType(ProgramElementDoc member, Content tdSummaryType) {
        MemberDoc m = (MemberDoc)member;
        addModifierAndType(m, getType(m), tdSummaryType);
    }

    /**
     * {@inheritDoc}
     */
    protected Content getDeprecatedLink(ProgramElementDoc member) {
        return writer.getDocLink(LinkInfoImpl.Kind.MEMBER,
                (MemberDoc) member, ((MemberDoc)member).qualifiedName());
    }

    /**
     * {@inheritDoc}
     */
    protected Content getNavSummaryLink(ClassDoc cd, boolean link) {
        if (link) {
            return writer.getHyperLink("annotation_type_field_summary",
                    writer.getResource("doclet.navField"));
        } else {
            return writer.getResource("doclet.navField");
        }
    }

    /**
     * {@inheritDoc}
     */
    protected void addNavDetailLink(boolean link, Content liNav) {
        if (link) {
            liNav.addContent(writer.getHyperLink("annotation_type_field_detail",
                    writer.getResource("doclet.navField")));
        } else {
            liNav.addContent(writer.getResource("doclet.navField"));
        }
    }

    private Type getType(MemberDoc member) {
        if (member instanceof FieldDoc) {
            return ((FieldDoc) member).type();
        } else {
            return ((MethodDoc) member).returnType();
        }
    }
}
