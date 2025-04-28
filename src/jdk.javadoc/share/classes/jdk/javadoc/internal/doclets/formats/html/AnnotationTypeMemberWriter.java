/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyles;
import jdk.javadoc.internal.doclets.toolkit.BaseOptions;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable;
import jdk.javadoc.internal.html.Comment;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.ContentBuilder;
import jdk.javadoc.internal.html.HtmlTree;
import jdk.javadoc.internal.html.Text;


/**
 * Writes annotation interface member documentation in HTML format.
 */
public class AnnotationTypeMemberWriter extends AbstractMemberWriter {

    /**
     * The index of the current member that is being documented at this point
     * in time.
     */
    protected Element currentMember;

    /**
     * Constructs a new AnnotationTypeMemberWriterImpl for a specific kind of member.
     *
     * We generate separate summaries for required and optional annotation interface members,
     * so we need dedicated writer instances for each kind. For the details section, a single
     * shared list is generated
     *
     * @param writer         the writer that will write the output.
     * @param kind           the kind of annotation interface members to handle.
     */
    public AnnotationTypeMemberWriter(ClassWriter writer, VisibleMemberTable.Kind kind) {
        super(writer, kind);
        assert switch (kind) {
            case ANNOTATION_TYPE_MEMBER_REQUIRED,
                    ANNOTATION_TYPE_MEMBER_OPTIONAL,
                    ANNOTATION_TYPE_MEMBER -> true;
            default -> false;
        };
    }

    @Override
    public void buildDetails(Content target) {
        buildAnnotationTypeMember(target);
    }

    /**
     * Build the member documentation.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildAnnotationTypeMember(Content target) {
        // In contrast to the annotation interface member summaries the details generated
        // by this builder share a single list for both required and optional members.
        var members = getVisibleMembers(VisibleMemberTable.Kind.ANNOTATION_TYPE_MEMBER);
        if (!members.isEmpty()) {
            addAnnotationDetailsMarker(target);
            Content annotationDetailsHeader = getAnnotationDetailsHeader();
            Content memberList = getMemberList();
            writer.tableOfContents.addLink(HtmlIds.ANNOTATION_TYPE_ELEMENT_DETAIL,
                    contents.annotationTypeDetailsLabel, TableOfContents.Level.FIRST);

            for (Element member : members) {
                currentMember = member;
                Content annotationContent = getAnnotationHeaderContent(currentMember);
                Content div = HtmlTree.DIV(HtmlStyles.horizontalScroll);
                buildAnnotationTypeMemberChildren(div);
                annotationContent.add(div);
                memberList.add(writer.getMemberListItem(annotationContent));
                writer.tableOfContents.addLink(htmlIds.forMember((ExecutableElement) member).getFirst(),
                        Text.of(name(member)), TableOfContents.Level.SECOND);
            }
            Content annotationDetails = getAnnotationDetails(annotationDetailsHeader, memberList);
            target.add(annotationDetails);
        }
    }

    protected void buildAnnotationTypeMemberChildren(Content annotationContent) {
        buildSignature(annotationContent);
        buildDeprecationInfo(annotationContent);
        buildPreviewInfo(annotationContent);
        buildMemberComments(annotationContent);
        buildTagInfo(annotationContent);
        buildDefaultValueInfo(annotationContent);
    }

    @Override
    protected void buildSignature(Content target) {
        target.add(getSignature(currentMember));
    }

    @Override
    protected void buildDeprecationInfo(Content target) {
        addDeprecated(currentMember, target);
    }

    @Override
    protected void buildPreviewInfo(Content target) {
        addPreview(currentMember, target);
    }

    /**
     * Build the comments for the member.  Do nothing if
     * {@link BaseOptions#noComment()} is set to true.
     *
     * @param annotationContent the content to which the documentation will be added
     */
    protected void buildMemberComments(Content annotationContent) {
        if (!options.noComment()) {
            addComments(currentMember, annotationContent);
        }
    }

    /**
     * Build the tag information.
     *
     * @param annotationContent the content to which the documentation will be added
     */
    protected void buildTagInfo(Content annotationContent) {
        addTags(currentMember, annotationContent);
    }

    /**
     * Build the default value for this optional member.
     *
     * @param annotationContent the content to which the documentation will be added
     */
    protected void buildDefaultValueInfo(Content annotationContent) {
        addDefaultValueInfo(currentMember, annotationContent);
    }

    @Override
    public Content getMemberSummaryHeader(Content content) {
        switch (kind) {
            case ANNOTATION_TYPE_MEMBER_REQUIRED -> content.add(selectComment(
                    MarkerComments.START_OF_ANNOTATION_TYPE_REQUIRED_MEMBER_SUMMARY,
                    MarkerComments.START_OF_ANNOTATION_INTERFACE_REQUIRED_MEMBER_SUMMARY));
            case ANNOTATION_TYPE_MEMBER_OPTIONAL -> content.add(selectComment(
                    MarkerComments.START_OF_ANNOTATION_TYPE_OPTIONAL_MEMBER_SUMMARY,
                    MarkerComments.START_OF_ANNOTATION_INTERFACE_OPTIONAL_MEMBER_SUMMARY));
            default -> throw new IllegalStateException(kind.toString());
        }
        Content c = new ContentBuilder();
        writer.addSummaryHeader(this, c);
        return c;
    }

    @Override
    public void buildSummary(Content summariesList, Content content) {
        writer.addSummary(HtmlStyles.memberSummary,
                switch (kind) {
                    case ANNOTATION_TYPE_MEMBER_REQUIRED -> HtmlIds.ANNOTATION_TYPE_REQUIRED_ELEMENT_SUMMARY;
                    case ANNOTATION_TYPE_MEMBER_OPTIONAL -> HtmlIds.ANNOTATION_TYPE_OPTIONAL_ELEMENT_SUMMARY;
                    default -> throw new IllegalStateException(kind.toString());
                },
                summariesList, content);
    }

    protected void addAnnotationDetailsMarker(Content memberDetails) {
        memberDetails.add(selectComment(
                MarkerComments.START_OF_ANNOTATION_TYPE_DETAILS,
                MarkerComments.START_OF_ANNOTATION_INTERFACE_DETAILS));
    }

    protected Content getAnnotationDetailsHeader() {
        Content memberDetails = new ContentBuilder();
        var heading = HtmlTree.HEADING(Headings.TypeDeclaration.DETAILS_HEADING,
                contents.annotationTypeDetailsLabel);
        memberDetails.add(heading);
        return memberDetails;
    }

    protected Content getAnnotationHeaderContent(Element member) {
        Content content = new ContentBuilder();
        var heading = HtmlTree.HEADING(Headings.TypeDeclaration.MEMBER_HEADING,
                Text.of(name(member)));
        content.add(heading);
        return HtmlTree.SECTION(HtmlStyles.detail, content)
                .setId(htmlIds.forMember((ExecutableElement) member).getFirst());
    }

    protected Content getSignature(Element member) {
        return new Signatures.MemberSignature(member, this)
                .setType(getType(member))
                .setAnnotations(writer.getAnnotationInfo(member, true))
                .toContent();
    }

    protected void addDeprecated(Element member, Content target) {
        addDeprecatedInfo(member, target);
    }

    protected void addPreview(Element member, Content content) {
        addPreviewInfo(member, content);
    }

    protected void addComments(Element member, Content annotationContent) {
        addComment(member, annotationContent);
    }

    protected void addTags(Element member, Content annotationContent) {
        writer.addTagsInfo(member, annotationContent);
    }

    protected Content getAnnotationDetails(Content annotationDetailsHeader, Content annotationDetails) {
        Content c = new ContentBuilder(annotationDetailsHeader, annotationDetails);
        return getMember(HtmlTree.SECTION(HtmlStyles.memberDetails, c));
    }

    @Override
    public void addSummaryLabel(Content content) {
        var label = HtmlTree.HEADING(Headings.TypeDeclaration.SUMMARY_HEADING,
                switch (kind) {
                    case ANNOTATION_TYPE_MEMBER_REQUIRED -> contents.annotateTypeRequiredMemberSummaryLabel;
                    case ANNOTATION_TYPE_MEMBER_OPTIONAL -> contents.annotateTypeOptionalMemberSummaryLabel;
                    default -> throw new IllegalStateException(kind.toString());
                });
        content.add(label);
    }

    /**
     * Get the caption for the summary table.
     * @return the caption
     */
    protected Content getCaption() {
        return contents.getContent(
                switch (kind) {
                    case ANNOTATION_TYPE_MEMBER_REQUIRED -> "doclet.Annotation_Type_Required_Members";
                    case ANNOTATION_TYPE_MEMBER_OPTIONAL -> "doclet.Annotation_Type_Optional_Members";
                    default -> throw new IllegalStateException(kind.toString());
                });
    }

    @Override
    public TableHeader getSummaryTableHeader(Element member) {
        return new TableHeader(contents.modifierAndTypeLabel,
                switch (kind) {
                    case ANNOTATION_TYPE_MEMBER_REQUIRED -> contents.annotationTypeRequiredMemberLabel;
                    case ANNOTATION_TYPE_MEMBER_OPTIONAL -> contents.annotationTypeOptionalMemberLabel;
                    default -> throw new IllegalStateException(kind.toString());
                },
                contents.descriptionLabel);
    }

    @Override
    protected Table<Element> createSummaryTable() {
        return new Table<Element>(HtmlStyles.summaryTable)
                .setCaption(getCaption())
                .setHeader(getSummaryTableHeader(typeElement))
                .setColumnStyles(HtmlStyles.colFirst, HtmlStyles.colSecond, HtmlStyles.colLast);
    }

    @Override
    public void addInheritedSummaryLabel(TypeElement typeElement, Content content) {
    }

    @Override
    protected void addSummaryLink(HtmlLinkInfo.Kind context, TypeElement typeElement, Element member,
                                  Content content) {
        Content memberLink = writer.getDocLink(context, utils.getEnclosingTypeElement(member), member,
                name(member), HtmlStyles.memberNameLink);
        var code = HtmlTree.CODE(memberLink);
        content.add(code);
    }

    @Override
    protected void addInheritedSummaryLink(TypeElement typeElement,
            Element member, Content target) {
        //Not applicable.
    }

    @Override
    protected void addSummaryType(Element member, Content content) {
        addModifiersAndType(member, getType(member), content);
    }

    @Override
    protected Content getSummaryLink(Element member) {
        String name = utils.getFullyQualifiedName(member) + "." + member.getSimpleName();
        return writer.getDocLink(HtmlLinkInfo.Kind.SHOW_PREVIEW, member, name);
    }

    protected Comment selectComment(Comment c1, Comment c2) {
        HtmlConfiguration configuration = writer.configuration;
        SourceVersion sv = configuration.docEnv.getSourceVersion();
        return sv.compareTo(SourceVersion.RELEASE_16) < 0 ? c1 : c2;
    }

    private TypeMirror getType(Element member) {
        return utils.isExecutableElement(member)
                ? utils.getReturnType(typeElement, (ExecutableElement) member)
                : member.asType();
    }

    protected void addDefaultValueInfo(Element member, Content annotationContent) {
        if (utils.isAnnotationInterface(member.getEnclosingElement())) {
            ExecutableElement ee = (ExecutableElement) member;
            AnnotationValue value = ee.getDefaultValue();
            if (value != null) {
                var dl = HtmlTree.DL(HtmlStyles.notes);
                dl.add(HtmlTree.DT(contents.default_));
                dl.add(HtmlTree.DD(HtmlTree.CODE(Text.of(value.toString()))));
                annotationContent.add(dl);
            }
        }
    }
}
