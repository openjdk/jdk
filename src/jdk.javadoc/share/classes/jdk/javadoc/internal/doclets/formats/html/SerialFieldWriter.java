/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.SerialFieldTree;
import com.sun.source.doctree.SerialTree;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyles;
import jdk.javadoc.internal.doclets.formats.html.taglets.TagletWriter;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.HtmlTag;
import jdk.javadoc.internal.html.HtmlTree;
import jdk.javadoc.internal.html.Text;

/**
 * Generate serialized form for serializable fields.
 * Documentation denoted by the tags <code>serial</code> and
 * <code>serialField</code> is processed.
 */
public class SerialFieldWriter extends FieldWriter {

    public SerialFieldWriter(SubWriterHolderWriter writer, TypeElement typeElement) {
        super(writer, typeElement);
    }

    protected Content getSerializableFieldsHeader() {
        return HtmlTree.UL(HtmlStyles.blockList);
    }

    protected Content getFieldsContentHeader() {
        return new HtmlTree(HtmlTag.LI).setStyle(HtmlStyles.blockList);
    }

    protected Content getSerializableFields(String heading, Content source) {
        var section = HtmlTree.SECTION(HtmlStyles.detail);
        if (!source.isEmpty()) {
            Content headingContent = Text.of(heading);
            var serialHeading = HtmlTree.HEADING(Headings.SerializedForm.CLASS_SUBHEADING, headingContent);
            section.add(serialHeading);
            section.add(source);
        }
        return HtmlTree.LI(section);
    }

    protected void addMemberHeader(TypeMirror fieldType, String fieldName, Content content) {
        Content nameContent = Text.of(fieldName);
        var heading = HtmlTree.HEADING(Headings.SerializedForm.MEMBER_HEADING, nameContent);
        content.add(heading);
        var pre = new HtmlTree(HtmlTag.PRE);
        Content fieldContent = writer.getLink(new HtmlLinkInfo(
                configuration, HtmlLinkInfo.Kind.LINK_TYPE_PARAMS_AND_BOUNDS, fieldType));
        pre.add(fieldContent);
        pre.add(" ");
        pre.add(fieldName);
        content.add(pre);
    }

    /**
     * Add the deprecated information for this member.
     *
     * @param field the field to document.
     * @param content the content to which the deprecated info will be added
     */
    protected void addMemberDeprecatedInfo(VariableElement field, Content content) {
        addDeprecatedInfo(field, content);
    }

    /**
     * Add the description text for this member.
     *
     * @param field the field to document.
     * @param content the content to which the deprecated info will be added
     */
    protected void addMemberDescription(VariableElement field, Content content) {
        if (!utils.getFullBody(field).isEmpty()) {
            writer.addInlineComment(field, content);
        }
        List<? extends SerialTree> tags = utils.getSerialTrees(field);
        if (!tags.isEmpty() && !tags.get(0).getDescription().isEmpty()) {
            writer.addInlineComment(field, tags.get(0), content);
        }
    }

    /**
     * Add the description text for this member represented by the tag.
     *
     * @param serialFieldTag the field to document (represented by tag)
     * @param content the content to which the deprecated info will be added
     */
    protected void addMemberDescription(VariableElement field, SerialFieldTree serialFieldTag, Content content) {
        List<? extends DocTree> description = serialFieldTag.getDescription();
        if (!description.isEmpty()) {
            Content serialFieldContent = writer.commentTagsToContent(field,
                    description,
                    new TagletWriter.Context(false, false));
            var div = HtmlTree.DIV(HtmlStyles.block, serialFieldContent);
            content.add(div);
        }
    }

    /**
     * Add the tag information for this member.
     *
     * @param field the field to document.
     * @param content the content to which the member tags info will be added
     */
    protected void addMemberTags(VariableElement field, Content content) {
        Content tagContent = writer.getBlockTagOutput(field);
        if (!tagContent.isEmpty()) {
            var dl = HtmlTree.DL(HtmlStyles.notes);
            dl.add(tagContent);
            content.add(dl);
        }
    }

    /**
     * Check to see if overview details should be printed. If
     * nocomment option set or if there is no text to be printed
     * for deprecation info, comment or tags, do not print overview details.
     *
     * @param field the field to check overview details for.
     * @return true if overview details need to be printed
     */
    protected boolean shouldPrintOverview(VariableElement field) {
        if (!options.noComment()) {
            if(!utils.getFullBody(field).isEmpty() ||
                    writer.hasSerializationOverviewTags(field))
                return true;
        }
        if (utils.isDeprecated(field))
            return true;
        return false;
    }
}
