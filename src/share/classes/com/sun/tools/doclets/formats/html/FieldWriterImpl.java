/*
 * Copyright (c) 1997, 2004, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * Writes field documentation in HTML format.
 *
 * @author Robert Field
 * @author Atul M Dambalkar
 * @author Jamie Ho (rewrite)
 * @author Bhavesh Patel (Modified)
 */
public class FieldWriterImpl extends AbstractMemberWriter
    implements FieldWriter, MemberSummaryWriter {

    private boolean printedSummaryHeader = false;

    public FieldWriterImpl(SubWriterHolderWriter writer, ClassDoc classdoc) {
        super(writer, classdoc);
    }

    public FieldWriterImpl(SubWriterHolderWriter writer) {
        super(writer);
    }

    /**
     * Write the fields summary header for the given class.
     *
     * @param classDoc the class the summary belongs to.
     */
    public void writeMemberSummaryHeader(ClassDoc classDoc) {
        printedSummaryHeader = true;
        writer.println("<!-- =========== FIELD SUMMARY =========== -->");
        writer.println();
        writer.printSummaryHeader(this, classDoc);
    }

    /**
     * Write the fields summary footer for the given class.
     *
     * @param classDoc the class the summary belongs to.
     */
    public void writeMemberSummaryFooter(ClassDoc classDoc) {
        writer.tableEnd();
        writer.space();
    }

    /**
     * Write the inherited fields summary header for the given class.
     *
     * @param classDoc the class the summary belongs to.
     */
    public void writeInheritedMemberSummaryHeader(ClassDoc classDoc) {
        if(! printedSummaryHeader){
            //We don't want inherited summary to not be under heading.
            writeMemberSummaryHeader(classDoc);
            writeMemberSummaryFooter(classDoc);
            printedSummaryHeader = true;
        }
        writer.printInheritedSummaryHeader(this, classDoc);
    }

    /**
     * {@inheritDoc}
     */
    public void writeInheritedMemberSummary(ClassDoc classDoc,
        ProgramElementDoc field, boolean isFirst, boolean isLast) {
        writer.printInheritedSummaryMember(this, classDoc, field, isFirst);
    }

    /**
     * Write the inherited fields summary footer for the given class.
     *
     * @param classDoc the class the summary belongs to.
     */
    public void writeInheritedMemberSummaryFooter(ClassDoc classDoc) {
        writer.printInheritedSummaryFooter(this, classDoc);
    }

    /**
     * Write the header for the field documentation.
     *
     * @param classDoc the class that the fields belong to.
     */
    public void writeHeader(ClassDoc classDoc, String header) {
        writer.println();
        writer.println("<!-- ============ FIELD DETAIL =========== -->");
        writer.println();
        writer.anchor("field_detail");
        writer.printTableHeadingBackground(header);
        writer.println();
    }

    /**
     * Write the field header for the given field.
     *
     * @param field the field being documented.
     * @param isFirst the flag to indicate whether or not the field is the
     *        first to be documented.
     */
    public void writeFieldHeader(FieldDoc field, boolean isFirst) {
        if (! isFirst) {
            writer.printMemberHeader();
            writer.println("");
        }
        writer.anchor(field.name());
        writer.h3();
        writer.print(field.name());
        writer.h3End();
    }

    /**
     * Write the signature for the given field.
     *
     * @param field the field being documented.
     */
    public void writeSignature(FieldDoc field) {
        writer.pre();
        writer.writeAnnotationInfo(field);
        printModifiers(field);
        writer.printLink(new LinkInfoImpl(LinkInfoImpl.CONTEXT_MEMBER,
            field.type()));
        print(' ');
        if (configuration().linksource) {
            writer.printSrcLink(field, field.name());
        } else {
            strong(field.name());
        }
        writer.preEnd();
        assert !writer.getMemberDetailsListPrinted();
    }

    /**
     * Write the deprecated output for the given field.
     *
     * @param field the field being documented.
     */
    public void writeDeprecated(FieldDoc field) {
        printDeprecated(field);
    }

    /**
     * Write the comments for the given field.
     *
     * @param field the field being documented.
     */
    public void writeComments(FieldDoc field) {
        ClassDoc holder = field.containingClass();
        if (field.inlineTags().length > 0) {
            writer.printMemberDetailsListStartTag();
            if (holder.equals(classdoc) ||
                (! (holder.isPublic() || Util.isLinkable(holder, configuration())))) {
                writer.dd();
                writer.printInlineComment(field);
                writer.ddEnd();
            } else {
                String classlink = writer.codeText(
                    writer.getDocLink(LinkInfoImpl.CONTEXT_FIELD_DOC_COPY,
                        holder, field,
                        holder.isIncluded() ?
                            holder.typeName() : holder.qualifiedTypeName(),
                        false));
                writer.dd();
                writer.strong(configuration().getText(holder.isClass()?
                   "doclet.Description_From_Class" :
                    "doclet.Description_From_Interface", classlink));
                writer.ddEnd();
                writer.dd();
                writer.printInlineComment(field);
                writer.ddEnd();
            }
        }
    }

    /**
     * Write the tag output for the given field.
     *
     * @param field the field being documented.
     */
    public void writeTags(FieldDoc field) {
        writer.printTags(field);
    }

    /**
     * Write the field footer.
     */
    public void writeFieldFooter() {
        printMemberFooter();
    }

    /**
     * Write the footer for the field documentation.
     *
     * @param classDoc the class that the fields belong to.
     */
    public void writeFooter(ClassDoc classDoc) {
        //No footer to write for field documentation
    }

    /**
     * Close the writer.
     */
    public void close() throws IOException {
        writer.close();
    }

    public int getMemberKind() {
        return VisibleMemberMap.FIELDS;
    }

    public void printSummaryLabel() {
        writer.printText("doclet.Field_Summary");
    }

    public void printTableSummary() {
        writer.tableIndexSummary(configuration().getText("doclet.Member_Table_Summary",
                configuration().getText("doclet.Field_Summary"),
                configuration().getText("doclet.fields")));
    }

    public void printSummaryTableHeader(ProgramElementDoc member) {
        String[] header = new String[] {
            writer.getModifierTypeHeader(),
            configuration().getText("doclet.0_and_1",
                    configuration().getText("doclet.Field"),
                    configuration().getText("doclet.Description"))
        };
        writer.summaryTableHeader(header, "col");
    }

    public void printSummaryAnchor(ClassDoc cd) {
        writer.anchor("field_summary");
    }

    public void printInheritedSummaryAnchor(ClassDoc cd) {
        writer.anchor("fields_inherited_from_class_" + configuration().getClassName(cd));
    }

    public void printInheritedSummaryLabel(ClassDoc cd) {
        String classlink = writer.getPreQualifiedClassLink(
            LinkInfoImpl.CONTEXT_MEMBER, cd, false);
        writer.strong();
        String key = cd.isClass()?
            "doclet.Fields_Inherited_From_Class" :
            "doclet.Fields_Inherited_From_Interface";
        writer.printText(key, classlink);
        writer.strongEnd();
    }

    protected void writeSummaryLink(int context, ClassDoc cd, ProgramElementDoc member) {
        writer.strong();
        writer.printDocLink(context, cd , (MemberDoc) member, member.name(), false);
        writer.strongEnd();
    }

    protected void writeInheritedSummaryLink(ClassDoc cd,
            ProgramElementDoc member) {
        writer.printDocLink(LinkInfoImpl.CONTEXT_MEMBER, cd, (MemberDoc)member,
            member.name(), false);
    }

    protected void printSummaryType(ProgramElementDoc member) {
        FieldDoc field = (FieldDoc)member;
        printModifierAndType(field, field.type());
    }

    protected void writeDeprecatedLink(ProgramElementDoc member) {
        writer.printDocLink(LinkInfoImpl.CONTEXT_MEMBER,
            (MemberDoc) member, ((FieldDoc)member).qualifiedName(), false);
    }

    protected void printNavSummaryLink(ClassDoc cd, boolean link) {
        if (link) {
            writer.printHyperLink("", (cd == null)?
                        "field_summary":
                        "fields_inherited_from_class_" +
                        configuration().getClassName(cd),
                    configuration().getText("doclet.navField"));
        } else {
            writer.printText("doclet.navField");
        }
    }

    protected void printNavDetailLink(boolean link) {
        if (link) {
            writer.printHyperLink("", "field_detail",
                configuration().getText("doclet.navField"));
        } else {
            writer.printText("doclet.navField");
        }
    }
}
