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

import java.util.ArrayList;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyles;
import jdk.javadoc.internal.doclets.toolkit.BaseOptions;
import jdk.javadoc.internal.doclets.toolkit.CommentUtils;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.ContentBuilder;
import jdk.javadoc.internal.html.Entity;
import jdk.javadoc.internal.html.HtmlTree;
import jdk.javadoc.internal.html.Text;

/**
 * Writes property documentation in HTML format.
 */
public class PropertyWriter extends AbstractMemberWriter {

    /**
     * The index of the current property that is being documented at this point
     * in time.
     */
    private ExecutableElement currentProperty;

    public PropertyWriter(ClassWriter writer) {
        super(writer, writer.typeElement, VisibleMemberTable.Kind.PROPERTIES);
    }

    @Override
    public void buildDetails(Content target) {
        buildPropertyDoc(target);
    }

    /**
     * Build the property documentation.
     *
     * @param detailsList the content to which the documentation will be added
     */
    protected void buildPropertyDoc(Content detailsList) {
        var properties  = getVisibleMembers(VisibleMemberTable.Kind.PROPERTIES);
        if (!properties.isEmpty()) {
            Content propertyDetailsHeader = getPropertyDetailsHeader(detailsList);
            Content memberList = getMemberList();
            writer.tableOfContents.addLink(HtmlIds.PROPERTY_DETAIL, contents.propertyDetailsLabel,
                    TableOfContents.Level.FIRST);

            for (Element property : properties) {
                currentProperty = (ExecutableElement)property;
                Content propertyContent = getPropertyHeaderContent(currentProperty);
                Content div = HtmlTree.DIV(HtmlStyles.horizontalScroll);
                buildSignature(div);
                buildDeprecationInfo(div);
                buildPreviewInfo(div);
                buildPropertyComments(div);
                buildTagInfo(div);
                propertyContent.add(div);
                memberList.add(getMemberListItem(propertyContent));
                writer.tableOfContents.addLink(htmlIds.forProperty(currentProperty),
                        Text.of(utils.getPropertyLabel(name(property))), TableOfContents.Level.SECOND);
            }
            Content propertyDetails = getPropertyDetails(propertyDetailsHeader, memberList);
            detailsList.add(propertyDetails);
        }
    }

    @Override
    protected void buildSignature(Content target) {
        target.add(getSignature(currentProperty));
    }

    @Override
    protected void buildDeprecationInfo(Content target) {
        addDeprecated(currentProperty, target);
    }

    @Override
    protected void buildPreviewInfo(Content target) {
        addPreview(currentProperty, target);
    }

    /**
     * Build the comments for the property.  Do nothing if
     * {@link BaseOptions#noComment()} is set to true.
     *
     * @param propertyContent the content to which the documentation will be added
     */
    protected void buildPropertyComments(Content propertyContent) {
        if (!options.noComment()) {
            addComments(currentProperty, propertyContent);
        }
    }

    /**
     * Build the tag information.
     *
     * @param propertyContent the content to which the documentation will be added
     */
    protected void buildTagInfo(Content propertyContent) {
        CommentUtils cmtUtils = configuration.cmtUtils;
        DocCommentTree dct = utils.getDocCommentTree(currentProperty);
        var fullBody = dct.getFullBody();
        ArrayList<DocTree> blockTags = dct.getBlockTags().stream()
                .filter(t -> t.getKind() != DocTree.Kind.RETURN)
                .collect(Collectors.toCollection(ArrayList::new));
        String sig = "#" + currentProperty.getSimpleName() + "()";
        blockTags.add(cmtUtils.makeSeeTree(sig, currentProperty));
        // The property method is used as a proxy for the property
        // (which does not have an explicit element of its own.)
        // Temporarily override the doc comment for the property method
        // by removing the `@return` tag, which should not be displayed for
        // the property.
        CommentUtils.DocCommentInfo prev = cmtUtils.setDocCommentTree(currentProperty, fullBody, blockTags);
        try {
            addTags(currentProperty, propertyContent);
        } finally {
            cmtUtils.setDocCommentInfo(currentProperty, prev);
        }
    }

    @Override
    public Content getMemberSummaryHeader(Content content) {
        content.add(MarkerComments.START_OF_PROPERTY_SUMMARY);
        Content memberContent = new ContentBuilder();
        writer.addSummaryHeader(this, memberContent);
        return memberContent;
    }

    @Override
    public void buildSummary(Content summariesList, Content content) {
        writer.addSummary(HtmlStyles.propertySummary,
                HtmlIds.PROPERTY_SUMMARY, summariesList, content);
    }

    protected Content getPropertyDetailsHeader(Content memberDetails) {
        memberDetails.add(MarkerComments.START_OF_PROPERTY_DETAILS);
        Content propertyDetailsContent = new ContentBuilder();
        var heading = HtmlTree.HEADING(Headings.TypeDeclaration.DETAILS_HEADING,
                contents.propertyDetailsLabel);
        propertyDetailsContent.add(heading);
        return propertyDetailsContent;
    }

    protected Content getPropertyHeaderContent(ExecutableElement property) {
        Content content = new ContentBuilder();
        var heading = HtmlTree.HEADING(Headings.TypeDeclaration.MEMBER_HEADING,
                Text.of(utils.getPropertyLabel(name(property))));
        content.add(heading);
        return HtmlTree.SECTION(HtmlStyles.detail, content)
                .setId(htmlIds.forProperty(property));
    }

    protected Content getSignature(ExecutableElement property) {
        return new Signatures.MemberSignature(property, this)
                .setType(utils.getReturnType(typeElement, property))
                .setAnnotations(writer.getAnnotationInfo(property, true))
                .toContent();
    }

    protected void addDeprecated(ExecutableElement property, Content propertyContent) {
    }

    protected void addPreview(ExecutableElement property, Content content) {
    }

    protected void addComments(ExecutableElement property, Content propertyContent) {
        TypeElement holder = (TypeElement)property.getEnclosingElement();
        if (!utils.getFullBody(property).isEmpty()) {
            if (holder.equals(typeElement) || !utils.isVisible(holder)) {
                writer.addInlineComment(property, propertyContent);
            } else {
                if (!utils.isHidden(holder) && !utils.isHidden(property)) {
                    Content link =
                            writer.getDocLink(HtmlLinkInfo.Kind.PLAIN,
                                    holder, property,
                                    utils.isIncluded(holder)
                                            ? holder.getSimpleName() : holder.getQualifiedName());
                    var codeLink = HtmlTree.CODE(link);
                    var descriptionFromLabel = HtmlTree.SPAN(HtmlStyles.descriptionFromTypeLabel,
                            utils.isClass(holder)
                                    ? contents.descriptionFromClassLabel
                                    : contents.descriptionFromInterfaceLabel);
                    descriptionFromLabel.add(Entity.NO_BREAK_SPACE);
                    descriptionFromLabel.add(codeLink);
                    propertyContent.add(HtmlTree.DIV(HtmlStyles.block, descriptionFromLabel));
                }
                writer.addInlineComment(property, propertyContent);
            }
        }
    }

    protected void addTags(ExecutableElement property, Content propertyContent) {
        writer.addTagsInfo(property, propertyContent);
    }

    protected Content getPropertyDetails(Content memberDetailsHeader, Content memberDetails) {
        return writer.getDetailsListItem(
                HtmlTree.SECTION(HtmlStyles.propertyDetails)
                        .setId(HtmlIds.PROPERTY_DETAIL)
                        .add(memberDetailsHeader)
                        .add(memberDetails));
    }

    @Override
    public void addSummaryLabel(Content content) {
        var label = HtmlTree.HEADING(Headings.TypeDeclaration.SUMMARY_HEADING,
                contents.propertySummaryLabel);
        content.add(label);
    }

    @Override
    public TableHeader getSummaryTableHeader(Element member) {
        return new TableHeader(contents.typeLabel, contents.propertyLabel,
                contents.descriptionLabel);
    }

    @Override
    protected Table<Element> createSummaryTable() {
        return new Table<Element>(HtmlStyles.summaryTable)
                .setCaption(contents.properties)
                .setHeader(getSummaryTableHeader(typeElement))
                .setColumnStyles(HtmlStyles.colFirst, HtmlStyles.colSecond, HtmlStyles.colLast);
    }

    @Override
    public void addInheritedSummaryLabel(TypeElement typeElement, Content content) {
        Content classLink = getMemberSummaryLinkOrFQN(typeElement, VisibleMemberTable.Kind.PROPERTIES);
        Content label;
        if (options.summarizeOverriddenMethods()) {
            label = Text.of(utils.isClass(typeElement)
                    ? resources.getText("doclet.Properties_Declared_In_Class")
                    : resources.getText("doclet.Properties_Declared_In_Interface"));
        } else {
            label = Text.of(utils.isClass(typeElement)
                    ? resources.getText("doclet.Properties_Inherited_From_Class")
                    : resources.getText("doclet.Properties_Inherited_From_Interface"));
        }
        var labelHeading =
                HtmlTree.HEADING(Headings.TypeDeclaration.INHERITED_SUMMARY_HEADING, label)
                        .setId(htmlIds.forInheritedProperties(typeElement))
                        .add(Entity.NO_BREAK_SPACE)
                        .add(classLink);
        content.add(labelHeading);
    }

    @Override
    protected void addSummaryLink(HtmlLinkInfo.Kind context, TypeElement typeElement, Element member,
                                  Content content) {
        Content memberLink = writer.getDocLink(context, typeElement,
                member,
                Text.of(utils.getPropertyLabel(name(member))),
                HtmlStyles.memberNameLink,
                true);

        var code = HtmlTree.CODE(memberLink);
        content.add(code);
    }

    @Override
    protected void addInheritedSummaryLink(TypeElement typeElement, Element member, Content target) {
        String mname = name(member);
        Content content = writer.getDocLink(HtmlLinkInfo.Kind.PLAIN, typeElement, member,
                utils.isProperty(mname) ? utils.getPropertyName(mname) : mname, true);
        target.add(content);
    }

    @Override
    protected void addSummaryType(Element member, Content content) {
        addModifiersAndType(member, utils.getReturnType(typeElement, (ExecutableElement)member), content);
    }

    @Override
    protected Content getSummaryLink(Element member) {
        return writer.getDocLink(HtmlLinkInfo.Kind.SHOW_PREVIEW, member,
                utils.getFullyQualifiedName(member));
    }

}
