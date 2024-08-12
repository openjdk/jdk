/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyles;
import jdk.javadoc.internal.doclets.toolkit.BaseOptions;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.ContentBuilder;
import jdk.javadoc.internal.html.HtmlTree;
import jdk.javadoc.internal.html.Text;

/**
 * Writes enum constant documentation in HTML format.
 */
public class EnumConstantWriter extends AbstractMemberWriter {

    /**
     * The current enum constant that is being documented.
     */
    private VariableElement currentElement;

    public EnumConstantWriter(ClassWriter classWriter) {
        super(classWriter, classWriter.typeElement, VisibleMemberTable.Kind.ENUM_CONSTANTS);
    }

    public EnumConstantWriter(SubWriterHolderWriter writer) {
        super(writer);
    }

    @Override
    public void buildDetails(Content target) {
        buildEnumConstant(target);
    }

    /**
     * Build the enum constant documentation.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildEnumConstant(Content target) {
        var enumConstants = getVisibleMembers(VisibleMemberTable.Kind.ENUM_CONSTANTS);
        if (!enumConstants.isEmpty()) {
            Content enumConstantsDetailsHeader = getEnumConstantsDetailsHeader(target);
            Content memberList = getMemberList();
            writer.tableOfContents.addLink(HtmlIds.ENUM_CONSTANT_DETAIL, contents.enumConstantDetailLabel);
            writer.tableOfContents.pushNestedList();

            for (Element enumConstant : enumConstants) {
                currentElement = (VariableElement)enumConstant;
                Content enumConstantContent = getEnumConstantsHeader(currentElement);
                Content div = HtmlTree.DIV(HtmlStyles.horizontalScroll);
                buildSignature(div);
                buildDeprecationInfo(div);
                buildPreviewInfo(div);
                buildEnumConstantComments(div);
                buildTagInfo(div);
                enumConstantContent.add(div);
                memberList.add(getMemberListItem(enumConstantContent));
                writer.tableOfContents.addLink(htmlIds.forMember(currentElement), Text.of(name(currentElement)));
            }
            Content enumConstantDetails = getEnumConstantsDetails(
                    enumConstantsDetailsHeader, memberList);
            target.add(enumConstantDetails);
            writer.tableOfContents.popNestedList();
        }
    }

    @Override
    protected void buildSignature(Content target) {
        target.add(getSignature(currentElement));
    }

    @Override
    protected void buildDeprecationInfo(Content target) {
        addDeprecated(currentElement, target);
    }

    @Override
    protected void buildPreviewInfo(Content target) {
        addPreview(currentElement, target);
    }

    /**
     * Build the comments for the enum constant.  Do nothing if
     * {@link BaseOptions#noComment()} is set to true.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildEnumConstantComments(Content target) {
        if (!options.noComment()) {
            addComments(currentElement, target);
        }
    }

    /**
     * Build the tag information.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildTagInfo(Content target) {
        addTags(currentElement, target);
    }

    @Override
    public Content getMemberSummaryHeader(Content content) {
        content.add(MarkerComments.START_OF_ENUM_CONSTANT_SUMMARY);
        Content memberContent = new ContentBuilder();
        writer.addSummaryHeader(this, memberContent);
        return memberContent;
    }

    @Override
    public void buildSummary(Content summariesList, Content content) {
        writer.addSummary(HtmlStyles.constantsSummary,
                HtmlIds.ENUM_CONSTANT_SUMMARY, summariesList, content);
    }

    protected Content getEnumConstantsDetailsHeader(Content memberDetails) {
        memberDetails.add(MarkerComments.START_OF_ENUM_CONSTANT_DETAILS);
        var enumConstantsDetailsContent = new ContentBuilder();
        var heading = HtmlTree.HEADING(Headings.TypeDeclaration.DETAILS_HEADING,
                contents.enumConstantDetailLabel);
        enumConstantsDetailsContent.add(heading);
        return enumConstantsDetailsContent;
    }

    protected Content getEnumConstantsHeader(VariableElement enumConstant) {
        Content enumConstantsContent = new ContentBuilder();
        var heading = HtmlTree.HEADING(Headings.TypeDeclaration.MEMBER_HEADING,
                Text.of(name(enumConstant)));
        enumConstantsContent.add(heading);
        return HtmlTree.SECTION(HtmlStyles.detail, enumConstantsContent)
                .setId(htmlIds.forMember(enumConstant));
    }

    protected Content getSignature(VariableElement enumConstant) {
        return new Signatures.MemberSignature(enumConstant, this)
                .setType(enumConstant.asType())
                .setAnnotations(writer.getAnnotationInfo(enumConstant, true))
                .toContent();
    }

    protected void addDeprecated(VariableElement enumConstant, Content content) {
        addDeprecatedInfo(enumConstant, content);
    }

    protected void addPreview(VariableElement enumConstant, Content content) {
        addPreviewInfo(enumConstant, content);
    }

    protected void addComments(VariableElement enumConstant, Content enumConstants) {
        addComment(enumConstant, enumConstants);
    }

    protected void addTags(VariableElement enumConstant, Content content) {
        writer.addTagsInfo(enumConstant, content);
    }

    protected Content getEnumConstantsDetails(Content memberDetailsHeader,
            Content content) {
        return writer.getDetailsListItem(
                HtmlTree.SECTION(HtmlStyles.constantDetails)
                        .setId(HtmlIds.ENUM_CONSTANT_DETAIL)
                        .add(memberDetailsHeader)
                        .add(content));
    }

    @Override
    public void addSummaryLabel(Content content) {
        var label = HtmlTree.HEADING(Headings.TypeDeclaration.SUMMARY_HEADING,
                contents.enumConstantSummary);
        content.add(label);
    }

    @Override
    public TableHeader getSummaryTableHeader(Element member) {
        return new TableHeader(contents.enumConstantLabel, contents.descriptionLabel);
    }

    @Override
    protected Table<Element> createSummaryTable() {
        return new Table<Element>(HtmlStyles.summaryTable)
                .setCaption(contents.getContent("doclet.Enum_Constants"))
                .setHeader(getSummaryTableHeader(typeElement))
                .setColumnStyles(HtmlStyles.colFirst, HtmlStyles.colLast);
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
    protected void addInheritedSummaryLink(TypeElement typeElement, Element member, Content target) {
    }

    @Override
    protected void addSummaryType(Element member, Content content) {
        //Not applicable.
    }

    @Override
    protected Content getSummaryLink(Element member) {
        String name = utils.getFullyQualifiedName(member) + "." + member.getSimpleName();
        return writer.getDocLink(HtmlLinkInfo.Kind.SHOW_PREVIEW, member, name);
    }
}
