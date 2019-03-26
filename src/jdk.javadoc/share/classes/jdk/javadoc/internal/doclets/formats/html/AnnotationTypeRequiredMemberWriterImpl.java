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

import jdk.javadoc.internal.doclets.formats.html.markup.Table;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import jdk.javadoc.internal.doclets.formats.html.markup.TableHeader;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.AnnotationTypeRequiredMemberWriter;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.MemberSummaryWriter;


/**
 * Writes annotation type required member documentation in HTML format.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 */
public class AnnotationTypeRequiredMemberWriterImpl extends AbstractMemberWriter
    implements AnnotationTypeRequiredMemberWriter, MemberSummaryWriter {

    /**
     * Construct a new AnnotationTypeRequiredMemberWriterImpl.
     *
     * @param writer         the writer that will write the output.
     * @param annotationType the AnnotationType that holds this member.
     */
    public AnnotationTypeRequiredMemberWriterImpl(SubWriterHolderWriter writer,
            TypeElement annotationType) {
        super(writer, annotationType);
    }

    /**
     * {@inheritDoc}
     */
    public Content getMemberSummaryHeader(TypeElement typeElement,
            Content memberSummaryTree) {
        memberSummaryTree.add(
                MarkerComments.START_OF_ANNOTATION_TYPE_REQUIRED_MEMBER_SUMMARY);
        Content memberTree = writer.getMemberTreeHeader();
        writer.addSummaryHeader(this, typeElement, memberTree);
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
    public void addMemberTree(Content memberSummaryTree, Content memberTree) {
        writer.addMemberTree(memberSummaryTree, memberTree);
    }

    /**
     * {@inheritDoc}
     */
    public void addAnnotationDetailsMarker(Content memberDetails) {
        memberDetails.add(MarkerComments.START_OF_ANNOTATION_TYPE_DETAILS);
    }

    /**
     * {@inheritDoc}
     */
    public void addAnnotationDetailsTreeHeader(TypeElement te,
            Content memberDetailsTree) {
        if (!writer.printedAnnotationHeading) {
            memberDetailsTree.add(links.createAnchor(
                    SectionName.ANNOTATION_TYPE_ELEMENT_DETAIL));
            Content heading = HtmlTree.HEADING(Headings.TypeDeclaration.DETAILS_HEADING,
                    contents.annotationTypeDetailsLabel);
            memberDetailsTree.add(heading);
            writer.printedAnnotationHeading = true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Content getAnnotationDocTreeHeader(Element member, Content annotationDetailsTree) {
        String simpleName = name(member);
        annotationDetailsTree.add(links.createAnchor(
                simpleName + utils.signature((ExecutableElement) member)));
        Content annotationDocTree = writer.getMemberTreeHeader();
        Content heading = new HtmlTree(Headings.TypeDeclaration.MEMBER_HEADING);
        heading.add(simpleName);
        annotationDocTree.add(heading);
        return annotationDocTree;
    }

    /**
     * {@inheritDoc}
     */
    public Content getSignature(Element member) {
        Content pre = new HtmlTree(HtmlTag.PRE);
        writer.addAnnotationInfo(member, pre);
        addModifiers(member, pre);
        Content link =
                writer.getLink(new LinkInfoImpl(configuration,
                        LinkInfoImpl.Kind.MEMBER, getType(member)));
        pre.add(link);
        pre.add(Contents.SPACE);
        if (configuration.linksource) {
            Content memberName = new StringContent(name(member));
            writer.addSrcLink(member, memberName, pre);
        } else {
            addName(name(member), pre);
        }
        return pre;
    }

    /**
     * {@inheritDoc}
     */
    public void addDeprecated(Element member, Content annotationDocTree) {
        addDeprecatedInfo(member, annotationDocTree);
    }

    /**
     * {@inheritDoc}
     */
    public void addComments(Element member, Content annotationDocTree) {
        addComment(member, annotationDocTree);
    }

    /**
     * {@inheritDoc}
     */
    public void addTags(Element member, Content annotationDocTree) {
        writer.addTagsInfo(member, annotationDocTree);
    }

    /**
     * {@inheritDoc}
     */
    public Content getAnnotationDetails(Content annotationDetailsTree) {
        return HtmlTree.SECTION(getMemberTree(annotationDetailsTree));
    }

    /**
     * {@inheritDoc}
     */
    public Content getAnnotationDoc(Content annotationDocTree,
            boolean isLastContent) {
        return getMemberTree(annotationDocTree, isLastContent);
    }

    /**
     * {@inheritDoc}
     */
    public void addSummaryLabel(Content memberTree) {
        Content label = HtmlTree.HEADING(Headings.TypeDeclaration.SUMMARY_HEADING,
                contents.annotateTypeRequiredMemberSummaryLabel);
        memberTree.add(label);
    }

    /**
     * Get the caption for the summary table.
     * @return the caption
     */
    // Overridden by AnnotationTypeOptionalMemberWriterImpl
    protected Content getCaption() {
        return contents.getContent("doclet.Annotation_Type_Required_Members");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TableHeader getSummaryTableHeader(Element member) {
        return new TableHeader(contents.modifierAndTypeLabel,
                contents.annotationTypeRequiredMemberLabel, contents.descriptionLabel);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Table createSummaryTable() {
        return new Table(HtmlStyle.memberSummary)
                .setCaption(getCaption())
                .setHeader(getSummaryTableHeader(typeElement))
                .setRowScopeColumn(1)
                .setColumnStyles(HtmlStyle.colFirst, HtmlStyle.colSecond, HtmlStyle.colLast);
    }

    /**
     * {@inheritDoc}
     */
    public void addSummaryAnchor(TypeElement typeElement, Content memberTree) {
        memberTree.add(links.createAnchor(
                SectionName.ANNOTATION_TYPE_REQUIRED_ELEMENT_SUMMARY));
    }

    /**
     * {@inheritDoc}
     */
    public void addInheritedSummaryAnchor(TypeElement typeElement, Content inheritedTree) {
    }

    /**
     * {@inheritDoc}
     */
    public void addInheritedSummaryLabel(TypeElement typeElement, Content inheritedTree) {
    }

    /**
     * {@inheritDoc}
     */
    protected void addSummaryLink(LinkInfoImpl.Kind context, TypeElement typeElement, Element member,
            Content tdSummary) {
        Content memberLink = HtmlTree.SPAN(HtmlStyle.memberNameLink,
                writer.getDocLink(context, member, name(member), false));
        Content code = HtmlTree.CODE(memberLink);
        tdSummary.add(code);
    }

    /**
     * {@inheritDoc}
     */
    protected void addInheritedSummaryLink(TypeElement typeElement,
            Element member, Content linksTree) {
        //Not applicable.
    }

    /**
     * {@inheritDoc}
     */
    protected void addSummaryType(Element member, Content tdSummaryType) {
        addModifierAndType(member, getType(member), tdSummaryType);
    }

    /**
     * {@inheritDoc}
     */
    protected Content getDeprecatedLink(Element member) {
        String name = utils.getFullyQualifiedName(member) + "." + member.getSimpleName();
        return writer.getDocLink(LinkInfoImpl.Kind.MEMBER, member, name);
    }

    private TypeMirror getType(Element member) {
        return utils.isExecutableElement(member)
                ? utils.getReturnType((ExecutableElement) member)
                : member.asType();
    }
}
