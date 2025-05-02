/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyles;
import jdk.javadoc.internal.doclets.toolkit.BaseOptions;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.ContentBuilder;
import jdk.javadoc.internal.html.Entity;
import jdk.javadoc.internal.html.HtmlStyle;
import jdk.javadoc.internal.html.HtmlTree;
import jdk.javadoc.internal.html.Text;

/**
 * Writes field documentation in HTML format.
 */
public class FieldWriter extends AbstractMemberWriter {

    /**
     * The index of the current field that is being documented at this point
     * in time.
     */
    private VariableElement currentElement;

    public FieldWriter(ClassWriter writer) {
        super(writer, VisibleMemberTable.Kind.FIELDS);
    }

    public FieldWriter(SubWriterHolderWriter writer, TypeElement typeElement) {
        super(writer, typeElement, VisibleMemberTable.Kind.FIELDS);
    }

    // used in ClassUseWriter and SummaryUseWriter
    public FieldWriter(SubWriterHolderWriter writer) {
        super(writer);
    }

    @Override
    public void buildDetails(Content target) {
        buildFieldDoc(target);
    }

    /**
     * Build the field documentation.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildFieldDoc(Content target) {
        var fields = getVisibleMembers(VisibleMemberTable.Kind.FIELDS);
        if (!fields.isEmpty()) {
            Content fieldDetailsHeader = getFieldDetailsHeader(target);
            Content memberList = getMemberList();
            writer.tableOfContents.addLink(HtmlIds.FIELD_DETAIL, contents.fieldDetailsLabel,
                    TableOfContents.Level.FIRST);

            for (Element element : fields) {
                currentElement = (VariableElement)element;
                Content fieldContent = getFieldHeaderContent(currentElement);
                Content div = HtmlTree.DIV(HtmlStyles.horizontalScroll);
                buildSignature(div);
                buildDeprecationInfo(div);
                buildPreviewInfo(div);
                buildFieldComments(div);
                buildTagInfo(div);
                fieldContent.add(div);
                memberList.add(getMemberListItem(fieldContent));
                writer.tableOfContents.addLink(htmlIds.forMember(currentElement), Text.of(name(element)),
                        TableOfContents.Level.SECOND);
            }
            Content fieldDetails = getFieldDetails(fieldDetailsHeader, memberList);
            target.add(fieldDetails);
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
     * Build the comments for the field.  Do nothing if
     * {@link BaseOptions#noComment()} is set to true.
     *
     * @param fieldContent the content to which the documentation will be added
     */
    protected void buildFieldComments(Content fieldContent) {
        if (!options.noComment()) {
            addComments(currentElement, fieldContent);
        }
    }

    /**
     * Build the tag information.
     *
     * @param fieldContent the content to which the documentation will be added
     */
    protected void buildTagInfo(Content fieldContent) {
        addTags(currentElement, fieldContent);
    }

    @Override
    public Content getMemberSummaryHeader(Content content) {
        content.add(MarkerComments.START_OF_FIELD_SUMMARY);
        Content memberContent = new ContentBuilder();
        writer.addSummaryHeader(this, memberContent);
        return memberContent;
    }

    @Override
    public void buildSummary(Content summariesList, Content content) {
        writer.addSummary(HtmlStyles.fieldSummary,
                HtmlIds.FIELD_SUMMARY, summariesList, content);
    }

    protected Content getFieldDetailsHeader(Content content) {
        content.add(MarkerComments.START_OF_FIELD_DETAILS);
        Content fieldDetailsContent = new ContentBuilder();
        var heading = HtmlTree.HEADING(Headings.TypeDeclaration.DETAILS_HEADING,
                contents.fieldDetailsLabel);
        fieldDetailsContent.add(heading);
        return fieldDetailsContent;
    }

    protected Content getFieldHeaderContent(VariableElement field) {
        Content content = new ContentBuilder();
        var heading = HtmlTree.HEADING(Headings.TypeDeclaration.MEMBER_HEADING,
                Text.of(name(field)));
        content.add(heading);
        return HtmlTree.SECTION(HtmlStyles.detail, content)
                .setId(htmlIds.forMember(field));
    }

    protected Content getSignature(VariableElement field) {
        return new Signatures.MemberSignature(field, this)
                .setType(utils.asInstantiatedFieldType(typeElement, field))
                .setAnnotations(writer.getAnnotationInfo(field, true))
                .toContent();
    }

    protected void addDeprecated(VariableElement field, Content fieldContent) {
        addDeprecatedInfo(field, fieldContent);
    }

    protected void addPreview(VariableElement field, Content content) {
        addPreviewInfo(field, content);
    }

    protected void addComments(VariableElement field, Content fieldContent) {
        if (!utils.getFullBody(field).isEmpty()) {
            writer.addInlineComment(field, fieldContent);
        }
    }

    protected void addTags(VariableElement field, Content fieldContent) {
        writer.addTagsInfo(field, fieldContent);
    }

    protected Content getFieldDetails(Content memberDetailsHeaderContent, Content memberContent) {
        return writer.getDetailsListItem(
                HtmlTree.SECTION(HtmlStyles.fieldDetails)
                        .setId(HtmlIds.FIELD_DETAIL)
                        .add(memberDetailsHeaderContent)
                        .add(memberContent));
    }

    @Override
    public void addSummaryLabel(Content content) {
        var label = HtmlTree.HEADING(Headings.TypeDeclaration.SUMMARY_HEADING,
                contents.fieldSummaryLabel);
        content.add(label);
    }

    @Override
    public TableHeader getSummaryTableHeader(Element member) {
        return new TableHeader(contents.modifierAndTypeLabel, contents.fieldLabel,
                contents.descriptionLabel);
    }

    @Override
    protected Table<Element> createSummaryTable() {
        List<HtmlStyle> bodyRowStyles = Arrays.asList(HtmlStyles.colFirst, HtmlStyles.colSecond,
                HtmlStyles.colLast);

        return new Table<Element>(HtmlStyles.summaryTable)
                .setCaption(contents.fields)
                .setHeader(getSummaryTableHeader(typeElement))
                .setColumnStyles(bodyRowStyles);
    }

    @Override
    public void addInheritedSummaryLabel(TypeElement typeElement, Content content) {
        Content classLink = getMemberSummaryLinkOrFQN(typeElement, VisibleMemberTable.Kind.FIELDS);
        Content label;
        if (options.summarizeOverriddenMethods()) {
            label = Text.of(utils.isClass(typeElement)
                    ? resources.getText("doclet.Fields_Declared_In_Class")
                    : resources.getText("doclet.Fields_Declared_In_Interface"));
        } else {
            label = Text.of(utils.isClass(typeElement)
                    ? resources.getText("doclet.Fields_Inherited_From_Class")
                    : resources.getText("doclet.Fields_Inherited_From_Interface"));
        }
        var labelHeading = HtmlTree.HEADING(Headings.TypeDeclaration.INHERITED_SUMMARY_HEADING,
                label);
        labelHeading.setId(htmlIds.forInheritedFields(typeElement));
        labelHeading.add(Entity.NO_BREAK_SPACE);
        labelHeading.add(classLink);
        content.add(labelHeading);
    }

    @Override
    protected void addSummaryLink(HtmlLinkInfo.Kind context, TypeElement typeElement, Element member,
                                  Content content) {
        Content memberLink = writer.getDocLink(context, typeElement , member, name(member),
                HtmlStyles.memberNameLink);
        var code = HtmlTree.CODE(memberLink);
        content.add(code);
    }

    @Override
    protected void addInheritedSummaryLink(TypeElement typeElement, Element member, Content target) {
        target.add(
                writer.getDocLink(HtmlLinkInfo.Kind.PLAIN, typeElement, member, name(member)));
    }

    @Override
    protected void addSummaryType(Element member, Content content) {
        addModifiersAndType(member, utils.asInstantiatedFieldType(typeElement, (VariableElement)member), content);
    }

    @Override
    protected Content getSummaryLink(Element member) {
        String name = utils.getFullyQualifiedName(member) + "." + member.getSimpleName();
        return writer.getDocLink(HtmlLinkInfo.Kind.SHOW_PREVIEW, member, name);
    }
}
