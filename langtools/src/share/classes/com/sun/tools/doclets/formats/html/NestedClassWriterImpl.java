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
 * Writes nested class documentation in HTML format.
 *
 * @author Robert Field
 * @author Atul M Dambalkar
 * @author Jamie Ho (rewrite)
 * @author Bhavesh Patel (Modified)
 */
public class NestedClassWriterImpl extends AbstractMemberWriter
    implements MemberSummaryWriter {

    private boolean printedSummaryHeader = false;

    public NestedClassWriterImpl(SubWriterHolderWriter writer,
            ClassDoc classdoc) {
        super(writer, classdoc);
    }

    public NestedClassWriterImpl(SubWriterHolderWriter writer) {
        super(writer);
    }

    /**
     * Write the classes summary header for the given class.
     *
     * @param classDoc the class the summary belongs to.
     */
    public void writeMemberSummaryHeader(ClassDoc classDoc) {
        printedSummaryHeader = true;
        writer.println("<!-- ======== NESTED CLASS SUMMARY ======== -->");
        writer.println();
        writer.printSummaryHeader(this, classDoc);
    }

    /**
     * Write the classes summary footer for the given class.
     *
     * @param classDoc the class the summary belongs to.
     */
    public void writeMemberSummaryFooter(ClassDoc classDoc) {
        writer.printSummaryFooter(this, classDoc);
    }

    /**
     * Write the inherited classes summary header for the given class.
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
            ProgramElementDoc nestedClass, boolean isFirst, boolean isLast) {
        writer.printInheritedSummaryMember(this, classDoc, nestedClass, isFirst);
    }

    /**
     * Write the inherited classes summary footer for the given class.
     *
     * @param classDoc the class the summary belongs to.
     */
    public void writeInheritedMemberSummaryFooter(ClassDoc classDoc) {
        writer.printInheritedSummaryFooter(this, classDoc);
        writer.println();
    }

    /**
     * Write the header for the nested class documentation.
     *
     * @param classDoc the class that the classes belong to.
     */
    public void writeHeader(ClassDoc classDoc, String header) {
        writer.anchor("nested class_detail");
        writer.printTableHeadingBackground(header);
    }

    /**
     * Write the nested class header for the given nested class.
     *
     * @param nestedClass the nested class being documented.
     * @param isFirst the flag to indicate whether or not the nested class is the
     *        first to be documented.
     */
    public void writeClassHeader(ClassDoc nestedClass, boolean isFirst) {
        if (! isFirst) {
            writer.printMemberHeader();
            writer.println("");
        }
        writer.anchor(nestedClass.name());
        writer.h3();
        writer.print(nestedClass.name());
        writer.h3End();
    }



    /**
     * Close the writer.
     */
    public void close() throws IOException {
        writer.close();
    }

    public int getMemberKind() {
        return VisibleMemberMap.INNERCLASSES;
    }

    public void printSummaryLabel() {
        writer.printText("doclet.Nested_Class_Summary");
    }

    public void printTableSummary() {
        writer.tableIndexSummary(configuration().getText("doclet.Member_Table_Summary",
                configuration().getText("doclet.Nested_Class_Summary"),
                configuration().getText("doclet.nested_classes")));
    }

    public void printSummaryTableHeader(ProgramElementDoc member) {
        String[] header;
        if (member.isInterface()) {
            header = new String[] {
                writer.getModifierTypeHeader(),
                configuration().getText("doclet.0_and_1",
                        configuration().getText("doclet.Interface"),
                        configuration().getText("doclet.Description"))
            };
        }
        else {
            header = new String[] {
                writer.getModifierTypeHeader(),
                configuration().getText("doclet.0_and_1",
                        configuration().getText("doclet.Class"),
                        configuration().getText("doclet.Description"))
            };
        }
        writer.summaryTableHeader(header, "col");
    }

    public void printSummaryAnchor(ClassDoc cd) {
        writer.anchor("nested_class_summary");
    }

    public void printInheritedSummaryAnchor(ClassDoc cd) {
        writer.anchor("nested_classes_inherited_from_class_" +
                       cd.qualifiedName());
    }

    public void printInheritedSummaryLabel(ClassDoc cd) {
        String clslink = writer.getPreQualifiedClassLink(
            LinkInfoImpl.CONTEXT_MEMBER, cd, false);
        writer.strong();
        writer.printText(cd.isInterface() ?
            "doclet.Nested_Classes_Interface_Inherited_From_Interface" :
            "doclet.Nested_Classes_Interfaces_Inherited_From_Class",
            clslink);
        writer.strongEnd();
    }

    protected void writeSummaryLink(int context, ClassDoc cd, ProgramElementDoc member) {
        writer.strong();
        writer.printLink(new LinkInfoImpl(context, (ClassDoc)member, false));
        writer.strongEnd();
    }

    protected void writeInheritedSummaryLink(ClassDoc cd,
            ProgramElementDoc member) {
        writer.printLink(new LinkInfoImpl(LinkInfoImpl.CONTEXT_MEMBER,
            (ClassDoc)member, false));
    }

    protected void printSummaryType(ProgramElementDoc member) {
        ClassDoc cd = (ClassDoc)member;
        printModifierAndType(cd, null);
    }

    protected void printHeader(ClassDoc cd) {
        // N.A.
    }

    protected void printBodyHtmlEnd(ClassDoc cd) {
        // N.A.
    }

    protected void printMember(ProgramElementDoc member) {
        // N.A.
    }

    protected void writeDeprecatedLink(ProgramElementDoc member) {
        writer.printQualifiedClassLink(LinkInfoImpl.CONTEXT_MEMBER,
            (ClassDoc)member);
    }

    protected void printNavSummaryLink(ClassDoc cd, boolean link) {
        if (link) {
            writer.printHyperLink("", (cd == null) ? "nested_class_summary":
                    "nested_classes_inherited_from_class_" +
                cd.qualifiedName(),
                ConfigurationImpl.getInstance().getText("doclet.navNested"));
        } else {
            writer.printText("doclet.navNested");
        }
    }

    protected void printNavDetailLink(boolean link) {
    }

    protected void printMemberLink(ProgramElementDoc member) {
    }

    protected void printMembersSummaryLink(ClassDoc cd, ClassDoc icd,
                                           boolean link) {
        if (link) {
            writer.printHyperLink(cd.name() + ".html",
                (cd == icd)?
                    "nested_class_summary":
                    "nested_classes_inherited_from_class_" +
                    icd.qualifiedName(),
                    ConfigurationImpl.getInstance().getText(
                        "doclet.Nested_Class_Summary"));
        } else {
            writer.printText("doclet.Nested_Class_Summary");
        }
    }
}
