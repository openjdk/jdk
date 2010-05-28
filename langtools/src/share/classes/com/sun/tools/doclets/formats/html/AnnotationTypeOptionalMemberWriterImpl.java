/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Writes annotation type optional member documentation in HTML format.
 *
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 */
public class AnnotationTypeOptionalMemberWriterImpl extends
        AnnotationTypeRequiredMemberWriterImpl
    implements AnnotationTypeOptionalMemberWriter, MemberSummaryWriter {

    /**
     * Construct a new AnnotationTypeOptionalMemberWriterImpl.
     *
     * @param writer         the writer that will write the output.
     * @param annotationType the AnnotationType that holds this member.
     */
    public AnnotationTypeOptionalMemberWriterImpl(SubWriterHolderWriter writer,
        AnnotationTypeDoc annotationType) {
        super(writer, annotationType);
    }

    /**
     * {@inheritDoc}
     */
    public void writeMemberSummaryHeader(ClassDoc classDoc) {
        writer.println("<!-- =========== ANNOTATION TYPE OPTIONAL MEMBER SUMMARY =========== -->");
        writer.println();
        writer.printSummaryHeader(this, classDoc);
    }

    /**
     * {@inheritDoc}
     */
    public void writeDefaultValueInfo(MemberDoc member) {
        if (((AnnotationTypeElementDoc) member).defaultValue() != null) {
            writer.printMemberDetailsListStartTag();
            writer.dd();
            writer.dl();
            writer.dt();
            writer.strong(ConfigurationImpl.getInstance().
                getText("doclet.Default"));
            writer.dtEnd();
            writer.dd();
            writer.print(((AnnotationTypeElementDoc) member).defaultValue());
            writer.ddEnd();
            writer.dlEnd();
            writer.ddEnd();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        writer.close();
    }

    /**
     * {@inheritDoc}
     */
    public void printSummaryLabel() {
        writer.printText("doclet.Annotation_Type_Optional_Member_Summary");
    }

    /**
     * {@inheritDoc}
     */
    public void printTableSummary() {
        writer.tableIndexSummary(configuration().getText("doclet.Member_Table_Summary",
                configuration().getText("doclet.Annotation_Type_Optional_Member_Summary"),
                configuration().getText("doclet.annotation_type_optional_members")));
    }

    public void printSummaryTableHeader(ProgramElementDoc member) {
        String[] header = new String[] {
            writer.getModifierTypeHeader(),
            configuration().getText("doclet.0_and_1",
                    configuration().getText("doclet.Annotation_Type_Optional_Member"),
                    configuration().getText("doclet.Description"))
        };
        writer.summaryTableHeader(header, "col");
    }

    /**
     * {@inheritDoc}
     */
    public void printSummaryAnchor(ClassDoc cd) {
        writer.anchor("annotation_type_optional_element_summary");
    }

    /**
     * {@inheritDoc}
     */
    protected void printNavSummaryLink(ClassDoc cd, boolean link) {
        if (link) {
            writer.printHyperLink("", "annotation_type_optional_element_summary",
                    configuration().getText("doclet.navAnnotationTypeOptionalMember"));
        } else {
            writer.printText("doclet.navAnnotationTypeOptionalMember");
        }
    }
}
