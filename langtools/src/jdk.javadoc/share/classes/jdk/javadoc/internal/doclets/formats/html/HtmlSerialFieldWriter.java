/*
 * Copyright (c) 1998, 2016, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import com.sun.source.doctree.DocTree;

import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlConstants;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.RawHtml;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.SerializedFormWriter;
import jdk.javadoc.internal.doclets.toolkit.taglets.TagletWriter;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;

/**
 * Generate serialized form for serializable fields.
 * Documentation denoted by the tags <code>serial</code> and
 * <code>serialField</code> is processed.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Joe Fialli
 * @author Bhavesh Patel (Modified)
 */
public class HtmlSerialFieldWriter extends FieldWriterImpl
        implements SerializedFormWriter.SerialFieldWriter {

    public HtmlSerialFieldWriter(SubWriterHolderWriter writer, TypeElement typeElement) {
        super(writer, typeElement);
    }

    public SortedSet<VariableElement> members(TypeElement te) {
        return utils.serializableFields(te);
    }

    /**
     * Return the header for serializable fields section.
     *
     * @return a content tree for the header
     */
    public Content getSerializableFieldsHeader() {
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.addStyle(HtmlStyle.blockList);
        return ul;
    }

    /**
     * Return the header for serializable fields content section.
     *
     * @param isLastContent true if the cotent being documented is the last content.
     * @return a content tree for the header
     */
    public Content getFieldsContentHeader(boolean isLastContent) {
        HtmlTree li = new HtmlTree(HtmlTag.LI);
        if (isLastContent)
            li.addStyle(HtmlStyle.blockListLast);
        else
            li.addStyle(HtmlStyle.blockList);
        return li;
    }

    /**
     * Add serializable fields.
     *
     * @param heading the heading for the section
     * @param serializableFieldsTree the tree to be added to the serializable fileds
     *        content tree
     * @return a content tree for the serializable fields content
     */
    public Content getSerializableFields(String heading, Content serializableFieldsTree) {
        HtmlTree li = new HtmlTree(HtmlTag.LI);
        li.addStyle(HtmlStyle.blockList);
        if (serializableFieldsTree.isValid()) {
            Content headingContent = new StringContent(heading);
            Content serialHeading = HtmlTree.HEADING(HtmlConstants.SERIALIZED_MEMBER_HEADING,
                    headingContent);
            li.addContent(serialHeading);
            li.addContent(serializableFieldsTree);
        }
        return li;
    }

    /**
     * Add the member header.
     *
     * @param fieldType the class document to be listed
     * @param fieldTypeStr the string for the field type to be documented
     * @param fieldDimensions the dimensions of the field string to be added
     * @param fieldName name of the field to be added
     * @param contentTree the content tree to which the member header will be added
     */
    public void addMemberHeader(TypeElement fieldType, String fieldTypeStr,
            String fieldDimensions, String fieldName, Content contentTree) {
        Content nameContent = new RawHtml(fieldName);
        Content heading = HtmlTree.HEADING(HtmlConstants.MEMBER_HEADING, nameContent);
        contentTree.addContent(heading);
        Content pre = new HtmlTree(HtmlTag.PRE);
        if (fieldType == null) {
            pre.addContent(fieldTypeStr);
        } else {
            Content fieldContent = writer.getLink(new LinkInfoImpl(
                    configuration, LinkInfoImpl.Kind.SERIAL_MEMBER, fieldType));
            pre.addContent(fieldContent);
        }
        pre.addContent(fieldDimensions + " ");
        pre.addContent(fieldName);
        contentTree.addContent(pre);
    }

    /**
     * Add the deprecated information for this member.
     *
     * @param field the field to document.
     * @param contentTree the tree to which the deprecated info will be added
     */
    public void addMemberDeprecatedInfo(VariableElement field, Content contentTree) {
        addDeprecatedInfo(field, contentTree);
    }

    /**
     * Add the description text for this member.
     *
     * @param field the field to document.
     * @param contentTree the tree to which the deprecated info will be added
     */
    public void addMemberDescription(VariableElement field, Content contentTree) {
        if (!utils.getBody(field).isEmpty()) {
            writer.addInlineComment(field, contentTree);
        }
        List<? extends DocTree> tags = utils.getBlockTags(field, DocTree.Kind.SERIAL);
        if (!tags.isEmpty()) {
            writer.addInlineComment(field, tags.get(0), contentTree);
        }
    }

    /**
     * Add the description text for this member represented by the tag.
     *
     * @param serialFieldTag the field to document (represented by tag)
     * @param contentTree the tree to which the deprecated info will be added
     */
    public void addMemberDescription(VariableElement field, DocTree serialFieldTag, Content contentTree) {
        CommentHelper ch = utils.getCommentHelper(field);
        List<? extends DocTree> description = ch.getDescription(configuration, serialFieldTag);
        if (!description.isEmpty()) {
            Content serialFieldContent = new RawHtml(ch.getText(description));
            Content div = HtmlTree.DIV(HtmlStyle.block, serialFieldContent);
            contentTree.addContent(div);
        }
    }

    /**
     * Add the tag information for this member.
     *
     * @param field the field to document.
     * @param contentTree the tree to which the member tags info will be added
     */
    public void addMemberTags(VariableElement field, Content contentTree) {
        Content tagContent = new ContentBuilder();
        TagletWriter.genTagOutput(configuration.tagletManager, field,
                configuration.tagletManager.getCustomTaglets(field),
                writer.getTagletWriterInstance(false), tagContent);
        Content dlTags = new HtmlTree(HtmlTag.DL);
        dlTags.addContent(tagContent);
        contentTree.addContent(dlTags);  // TODO: what if empty?
    }

    /**
     * Check to see if overview details should be printed. If
     * nocomment option set or if there is no text to be printed
     * for deprecation info, comment or tags, do not print overview details.
     *
     * @param field the field to check overview details for.
     * @return true if overview details need to be printed
     */
    public boolean shouldPrintOverview(VariableElement field) {
        if (!configuration.nocomment) {
            if(!utils.getBody(field).isEmpty() ||
                    writer.hasSerializationOverviewTags(field))
                return true;
        }
        if (utils.isDeprecated(field))
            return true;
        return false;
    }
}
