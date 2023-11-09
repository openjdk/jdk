/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.Entity;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlId;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.TagName;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Text;
import jdk.javadoc.internal.doclets.toolkit.BaseOptions;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable;


/**
 * Writes constructor documentation.
 */
public class ConstructorWriter extends AbstractExecutableMemberWriter {

    /**
     * The current constructor that is being documented at this point in time.
     */
    private ExecutableElement currentConstructor;

    private boolean foundNonPubConstructor = false;

    /**
     * Construct a new member writer for constructors.
     *
     * @param writer the writer for the class to which the constructors belong
     */
    public ConstructorWriter(ClassWriter writer) {
        super(writer, writer.typeElement, VisibleMemberTable.Kind.CONSTRUCTORS);

        // the following must be done before the summary table is generated
        var constructors = getVisibleMembers(VisibleMemberTable.Kind.CONSTRUCTORS);
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
    public ConstructorWriter(SubWriterHolderWriter writer) {
        super(writer);
    }

    @Override
    public void buildDetails(Content target) {
        buildConstructorDoc(target);
    }

    /**
     * Build the constructor documentation.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildConstructorDoc(Content target) {
        var constructors = getVisibleMembers(VisibleMemberTable.Kind.CONSTRUCTORS);
        if (!constructors.isEmpty()) {
            for (Element constructor : constructors) {
                if (utils.isProtected(constructor) || utils.isPrivate(constructor)) {
                    setFoundNonPubConstructor(true);
                }
            }

            Content constructorDetailsHeader = getConstructorDetailsHeader(target);
            Content memberList = getMemberList();

            for (Element constructor : constructors) {
                currentConstructor = (ExecutableElement)constructor;
                Content constructorContent = getConstructorHeaderContent(currentConstructor);
                Content div = HtmlTree.DIV(HtmlStyle.horizontalScroll);
                buildSignature(div);
                buildDeprecationInfo(div);
                buildPreviewInfo(div);
                buildConstructorComments(div);
                buildTagInfo(div);
                constructorContent.add(div);
                memberList.add(getMemberListItem(constructorContent));
            }
            Content constructorDetails = getConstructorDetails(constructorDetailsHeader, memberList);
            target.add(constructorDetails);
        }
    }

    @Override
    protected void buildSignature(Content target) {
        target.add(getSignature(currentConstructor));
    }

    @Override
    protected void buildDeprecationInfo(Content target) {
        addDeprecated(currentConstructor, target);
    }

    @Override
    protected void buildPreviewInfo(Content target) {
        addPreview(currentConstructor, target);
    }

    /**
     * Build the comments for the constructor.  Do nothing if
     * {@link BaseOptions#noComment()} is set to true.
     *
     * @param constructorContent the content to which the documentation will be added
     */
    protected void buildConstructorComments(Content constructorContent) {
        if (!options.noComment()) {
            addComments(currentConstructor, constructorContent);
        }
    }

    /**
     * Build the tag information.
     *
     * @param constructorContent the content to which the documentation will be added
     */
    protected void buildTagInfo(Content constructorContent) {
        addTags(currentConstructor, constructorContent);
    }

    @Override
    public Content getMemberSummaryHeader(Content content) {
        content.add(MarkerComments.START_OF_CONSTRUCTOR_SUMMARY);
        Content c = new ContentBuilder();
        writer.addSummaryHeader(this, c);
        return c;
    }

    @Override
    public void buildSummary(Content summariesList, Content content) {
        writer.addSummary(HtmlStyle.constructorSummary,
                HtmlIds.CONSTRUCTOR_SUMMARY, summariesList, content);
    }

    protected Content getConstructorDetailsHeader(Content content) {
        content.add(MarkerComments.START_OF_CONSTRUCTOR_DETAILS);
        Content constructorDetails = new ContentBuilder();
        var heading = HtmlTree.HEADING(Headings.TypeDeclaration.DETAILS_HEADING,
                contents.constructorDetailsLabel);
        constructorDetails.add(heading);
        return constructorDetails;
    }

    protected Content getConstructorHeaderContent(ExecutableElement constructor) {
        Content content = new ContentBuilder();
        var heading = HtmlTree.HEADING(Headings.TypeDeclaration.MEMBER_HEADING,
                Text.of(name(constructor)));
        HtmlId erasureAnchor = htmlIds.forErasure(constructor);
        if (erasureAnchor != null) {
            heading.setId(erasureAnchor);
        }
        content.add(heading);
        return HtmlTree.SECTION(HtmlStyle.detail, content)
                .setId(htmlIds.forMember(constructor));
    }

    protected Content getSignature(ExecutableElement constructor) {
        return new Signatures.MemberSignature(constructor, this)
                .setParameters(getParameters(constructor, true))
                .setExceptions(getExceptions(constructor))
                .setAnnotations(writer.getAnnotationInfo(constructor, true))
                .toContent();
    }

    protected void addDeprecated(ExecutableElement constructor, Content constructorContent) {
        addDeprecatedInfo(constructor, constructorContent);
    }

    protected void addPreview(ExecutableElement constructor, Content content) {
        addPreviewInfo(constructor, content);
    }

    protected void addComments(ExecutableElement constructor, Content constructorContent) {
        addComment(constructor, constructorContent);
    }

    protected void addTags(ExecutableElement constructor, Content constructorContent) {
        writer.addTagsInfo(constructor, constructorContent);
    }

    protected Content getConstructorDetails(Content memberDetailsHeader, Content memberDetails) {
        return writer.getDetailsListItem(
                HtmlTree.SECTION(HtmlStyle.constructorDetails)
                        .setId(HtmlIds.CONSTRUCTOR_DETAIL)
                        .add(memberDetailsHeader)
                        .add(memberDetails));
    }

    protected void setFoundNonPubConstructor(boolean foundNonPubConstructor) {
        this.foundNonPubConstructor = foundNonPubConstructor;
    }

    @Override
    public void addSummaryLabel(Content content) {
        var label = HtmlTree.HEADING(Headings.TypeDeclaration.SUMMARY_HEADING,
                contents.constructorSummaryLabel);
        content.add(label);
    }

    @Override
    public TableHeader getSummaryTableHeader(Element member) {
        if (foundNonPubConstructor) {
            return new TableHeader(contents.modifierLabel, contents.constructorLabel,
                    contents.descriptionLabel);
        } else {
            return new TableHeader(contents.constructorLabel, contents.descriptionLabel);
        }
    }

    @Override
    protected Table<Element> createSummaryTable() {
        List<HtmlStyle> bodyRowStyles;

        if (foundNonPubConstructor) {
            bodyRowStyles = Arrays.asList(HtmlStyle.colFirst, HtmlStyle.colConstructorName,
                    HtmlStyle.colLast);
        } else {
            bodyRowStyles = Arrays.asList(HtmlStyle.colConstructorName, HtmlStyle.colLast);
        }

        return new Table<Element>(
                HtmlStyle.summaryTable)
                .setCaption(contents.constructors)
                .setHeader(getSummaryTableHeader(typeElement))
                .setColumnStyles(bodyRowStyles);
    }

    @Override
    public void addInheritedSummaryLabel(TypeElement typeElement, Content content) {
    }

    @Override
    protected void addSummaryType(Element member, Content content) {
        if (foundNonPubConstructor) {
            var code = new HtmlTree(TagName.CODE);
            if (utils.isProtected(member)) {
                code.add("protected ");
            } else if (utils.isPrivate(member)) {
                code.add("private ");
            } else if (utils.isPublic(member)) {
                code.add(Entity.NO_BREAK_SPACE);
            } else {
                code.add(
                        resources.getText("doclet.Package_private"));
            }
            content.add(code);
        }
    }
}
