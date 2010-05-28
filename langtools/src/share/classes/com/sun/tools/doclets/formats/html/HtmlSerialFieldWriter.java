/*
 * Copyright (c) 1998, 2009, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.taglets.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * Generate serialized form for serializable fields.
 * Documentation denoted by the tags <code>serial</code> and
 * <code>serialField</code> is processed.
 *
 * @author Joe Fialli
 * @author Bhavesh Patel (Modified)
 */
public class HtmlSerialFieldWriter extends FieldWriterImpl
    implements SerializedFormWriter.SerialFieldWriter {
    ProgramElementDoc[] members = null;

    private boolean printedOverallAnchor = false;

    private boolean printedFirstMember = false;

    public HtmlSerialFieldWriter(SubWriterHolderWriter writer,
                                    ClassDoc classdoc) {
        super(writer, classdoc);
    }

    public List<FieldDoc> members(ClassDoc cd) {
        return Util.asList(cd.serializableFields());
    }

    protected void printTypeLinkNoDimension(Type type) {
        ClassDoc cd = type.asClassDoc();
        //Linking to package private classes in serialized for causes
        //broken links.  Don't link to them.
        if (type.isPrimitive() || cd.isPackagePrivate()) {
            print(type.typeName());
        } else {
            writer.printLink(new LinkInfoImpl(
                LinkInfoImpl.CONTEXT_SERIAL_MEMBER, type));
        }
    }

    public void writeHeader(String heading) {
        if (! printedOverallAnchor) {
            writer.anchor("serializedForm");
            printedOverallAnchor = true;
            writer.printTableHeadingBackground(heading);
            writer.println();
            if (heading.equals(
                   configuration().getText("doclet.Serialized_Form_class"))) {
                assert !writer.getMemberDetailsListPrinted();
            }
        } else {
            writer.printTableHeadingBackground(heading);
            writer.println();
        }
    }

    public void writeMemberHeader(ClassDoc fieldType, String fieldTypeStr,
            String fieldDimensions, String fieldName) {
        if (printedFirstMember) {
            writer.printMemberHeader();
        }
        printedFirstMember = true;
        writer.h3();
        writer.print(fieldName);
        writer.h3End();
        writer.pre();
        if (fieldType == null) {
            writer.print(fieldTypeStr);
        } else {
            writer.printLink(new LinkInfoImpl(LinkInfoImpl.CONTEXT_SERIAL_MEMBER,
                fieldType));
        }
        print(fieldDimensions + ' ');
        strong(fieldName);
        writer.preEnd();
        assert !writer.getMemberDetailsListPrinted();
    }

    /**
     * Write the deprecated information for this member.
     *
     * @param field the field to document.
     */
    public void writeMemberDeprecatedInfo(FieldDoc field) {
        printDeprecated(field);
    }

    /**
     * Write the description text for this member.
     *
     * @param field the field to document.
     */
    public void writeMemberDescription(FieldDoc field) {
        if (field.inlineTags().length > 0) {
            writer.printMemberDetailsListStartTag();
            writer.dd();
            writer.printInlineComment(field);
            writer.ddEnd();
        }
        Tag[] tags = field.tags("serial");
        if (tags.length > 0) {
            writer.printMemberDetailsListStartTag();
            writer.dd();
            writer.printInlineComment(field, tags[0]);
            writer.ddEnd();
        }
    }

    /**
     * Write the description text for this member represented by the tag.
     *
     * @param serialFieldTag the field to document (represented by tag).
     */
    public void writeMemberDescription(SerialFieldTag serialFieldTag) {
        String serialFieldTagDesc = serialFieldTag.description().trim();
        if (!serialFieldTagDesc.isEmpty()) {
            writer.dl();
            writer.dd();
            writer.print(serialFieldTagDesc);
            writer.ddEnd();
            writer.dlEnd();
        }
    }

    /**
     * Write the tag information for this member.
     *
     * @param field the field to document.
     */
    public void writeMemberTags(FieldDoc field) {
        TagletOutputImpl output = new TagletOutputImpl("");
        TagletWriter.genTagOuput(configuration().tagletManager, field,
            configuration().tagletManager.getCustomTags(field),
                writer.getTagletWriterInstance(false), output);
        String outputString = output.toString().trim();
        if (!outputString.isEmpty()) {
            writer.printMemberDetailsListStartTag();
            writer.dd();
            writer.dl();
            print(outputString);
            writer.dlEnd();
            writer.ddEnd();
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
    public boolean shouldPrintOverview(FieldDoc field) {
        if (!configuration().nocomment) {
            if(!field.commentText().isEmpty() ||
                    writer.hasSerializationOverviewTags(field))
                return true;
        }
        if (field.tags("deprecated").length > 0)
            return true;
        return false;
    }

    public void writeMemberFooter() {
        printMemberFooter();
    }

    /**
     * Write the footer information. If the serilization overview section was
     * printed, check for definition list and close list tag.
     *
     * @param heading the heading that was written.
     */
    public void writeFooter(String heading) {
        if (printedOverallAnchor) {
            if (heading.equals(
                   configuration().getText("doclet.Serialized_Form_class"))) {
                writer.printMemberDetailsListEndTag();
                assert !writer.getMemberDetailsListPrinted();
            }
        }
    }
}
