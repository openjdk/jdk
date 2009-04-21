/*
 * Copyright 2003-2004 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.doclets.formats.html;

import java.io.*;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * Writes enum constant documentation in HTML format.
 *
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 */
public class EnumConstantWriterImpl extends AbstractMemberWriter
    implements EnumConstantWriter, MemberSummaryWriter {

    private boolean printedSummaryHeader = false;

    public EnumConstantWriterImpl(SubWriterHolderWriter writer,
        ClassDoc classdoc) {
        super(writer, classdoc);
    }

    public EnumConstantWriterImpl(SubWriterHolderWriter writer) {
        super(writer);
    }

    /**
     * Write the enum constant summary header for the given class.
     *
     * @param classDoc the class the summary belongs to.
     */
    public void writeMemberSummaryHeader(ClassDoc classDoc) {
        printedSummaryHeader = true;
        writer.println("<!-- =========== ENUM CONSTANT SUMMARY =========== -->");
        writer.println();
        writer.printSummaryHeader(this, classDoc);
    }

    /**
     * Write the enum constant summary footer for the given class.
     *
     * @param classDoc the class the summary belongs to.
     */
    public void writeMemberSummaryFooter(ClassDoc classDoc) {
        writer.printSummaryFooter(this, classDoc);
    }

    /**
     * Write the inherited enum constant summary header for the given class.
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
        ProgramElementDoc enumConstant, boolean isFirst, boolean isLast) {
        writer.printInheritedSummaryMember(this, classDoc, enumConstant, isFirst);
    }

    /**
     * Write the inherited enum constant summary footer for the given class.
     *
     * @param classDoc the class the summary belongs to.
     */
    public void writeInheritedMemberSummaryFooter(ClassDoc classDoc) {
        writer.printInheritedSummaryFooter(this, classDoc);
    }

    /**
     * {@inheritDoc}
     */
    public void writeHeader(ClassDoc classDoc, String header) {
        writer.println();
        writer.println("<!-- ============ ENUM CONSTANT DETAIL =========== -->");
        writer.println();
        writer.anchor("enum_constant_detail");
        writer.printTableHeadingBackground(header);
        writer.println();
    }

    /**
     * {@inheritDoc}
     */
    public void writeEnumConstantHeader(FieldDoc enumConstant, boolean isFirst) {
        if (! isFirst) {
            writer.printMemberHeader();
            writer.println("");
        }
        writer.anchor(enumConstant.name());
        writer.h3();
        writer.print(enumConstant.name());
        writer.h3End();
    }

    /**
     * {@inheritDoc}
     */
    public void writeSignature(FieldDoc enumConstant) {
        writer.pre();
        writer.writeAnnotationInfo(enumConstant);
        printModifiers(enumConstant);
        writer.printLink(new LinkInfoImpl(LinkInfoImpl.CONTEXT_MEMBER,
            enumConstant.type()));
        print(' ');
        if (configuration().linksource) {
            writer.printSrcLink(enumConstant, enumConstant.name());
        } else {
            strong(enumConstant.name());
        }
        writer.preEnd();
        assert !writer.getMemberDetailsListPrinted();
    }

    /**
     * {@inheritDoc}
     */
    public void writeDeprecated(FieldDoc enumConstant) {
        printDeprecated(enumConstant);
    }

    /**
     * {@inheritDoc}
     */
    public void writeComments(FieldDoc enumConstant) {
        printComment(enumConstant);
    }

    /**
     * {@inheritDoc}
     */
    public void writeTags(FieldDoc enumConstant) {
        writer.printTags(enumConstant);
    }

    /**
     * {@inheritDoc}
     */
    public void writeEnumConstantFooter() {
        printMemberFooter();
    }

    /**
     * {@inheritDoc}
     */
    public void writeFooter(ClassDoc classDoc) {
        //No footer to write for enum constant documentation
    }

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        writer.close();
    }

    public int getMemberKind() {
        return VisibleMemberMap.ENUM_CONSTANTS;
    }

    public void printSummaryLabel() {
        writer.printText("doclet.Enum_Constant_Summary");
    }

    public void printTableSummary() {
        writer.tableIndexSummary(configuration().getText("doclet.Member_Table_Summary",
                configuration().getText("doclet.Enum_Constant_Summary"),
                configuration().getText("doclet.enum_constants")));
    }

    public void printSummaryTableHeader(ProgramElementDoc member) {
        String[] header = new String[] {
            configuration().getText("doclet.0_and_1",
                    configuration().getText("doclet.Enum_Constant"),
                    configuration().getText("doclet.Description"))
        };
        writer.summaryTableHeader(header, "col");
    }

    public void printSummaryAnchor(ClassDoc cd) {
        writer.anchor("enum_constant_summary");
    }

    public void printInheritedSummaryAnchor(ClassDoc cd) {
    }   // no such

    public void printInheritedSummaryLabel(ClassDoc cd) {
        // no such
    }

    protected void writeSummaryLink(int context, ClassDoc cd, ProgramElementDoc member) {
        writer.strong();
        writer.printDocLink(context, (MemberDoc) member, member.name(), false);
        writer.strongEnd();
    }

    protected void writeInheritedSummaryLink(ClassDoc cd,
            ProgramElementDoc member) {
        writer.printDocLink(LinkInfoImpl.CONTEXT_MEMBER, (MemberDoc)member,
            member.name(), false);
    }

    protected void printSummaryType(ProgramElementDoc member) {
        //Not applicable.
    }

    protected void writeDeprecatedLink(ProgramElementDoc member) {
        writer.printDocLink(LinkInfoImpl.CONTEXT_MEMBER,
            (MemberDoc) member, ((FieldDoc)member).qualifiedName(), false);
    }

    protected void printNavSummaryLink(ClassDoc cd, boolean link) {
        if (link) {
            writer.printHyperLink("", (cd == null)?
                        "enum_constant_summary":
                        "enum_constants_inherited_from_class_" +
                        configuration().getClassName(cd),
                    configuration().getText("doclet.navEnum"));
        } else {
            writer.printText("doclet.navEnum");
        }
    }

    protected void printNavDetailLink(boolean link) {
        if (link) {
            writer.printHyperLink("", "enum_constant_detail",
                configuration().getText("doclet.navEnum"));
        } else {
            writer.printText("doclet.navEnum");
        }
    }
}
