/*
 * Copyright 1997-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

import com.sun.tools.doclets.internal.toolkit.util.DeprecatedAPIListBuilder;
import com.sun.tools.doclets.internal.toolkit.util.*;
import java.io.*;

/**
 * Generate File to list all the deprecated classes and class members with the
 * appropriate links.
 *
 * @see java.util.List
 * @author Atul M Dambalkar
 * @author Bhavesh Patel (Modified)
 */
public class DeprecatedListWriter extends SubWriterHolderWriter {

    private static final String[] ANCHORS = new String[] {
        "interface", "class", "enum", "exception", "error", "annotation_type",
         "field", "method", "constructor", "enum_constant",
        "annotation_type_member"
    };

    private static final String[] HEADING_KEYS = new String[] {
        "doclet.Deprecated_Interfaces", "doclet.Deprecated_Classes",
        "doclet.Deprecated_Enums", "doclet.Deprecated_Exceptions",
        "doclet.Deprecated_Errors",
        "doclet.Deprecated_Annotation_Types",
        "doclet.Deprecated_Fields",
        "doclet.Deprecated_Methods", "doclet.Deprecated_Constructors",
        "doclet.Deprecated_Enum_Constants",
        "doclet.Deprecated_Annotation_Type_Members"
    };

    private static final String[] SUMMARY_KEYS = new String[] {
        "doclet.deprecated_interfaces", "doclet.deprecated_classes",
        "doclet.deprecated_enums", "doclet.deprecated_exceptions",
        "doclet.deprecated_errors",
        "doclet.deprecated_annotation_types",
        "doclet.deprecated_fields",
        "doclet.deprecated_methods", "doclet.deprecated_constructors",
        "doclet.deprecated_enum_constants",
        "doclet.deprecated_annotation_type_members"
    };

    private static final String[] HEADER_KEYS = new String[] {
        "doclet.Interface", "doclet.Class",
        "doclet.Enum", "doclet.Exceptions",
        "doclet.Errors",
        "doclet.AnnotationType",
        "doclet.Field",
        "doclet.Method", "doclet.Constructor",
        "doclet.Enum_Constant",
        "doclet.Annotation_Type_Member"
    };

    private AbstractMemberWriter[] writers;

    private ConfigurationImpl configuration;

    /**
     * Constructor.
     *
     * @param filename the file to be generated.
     */
    public DeprecatedListWriter(ConfigurationImpl configuration,
                                String filename) throws IOException {
        super(configuration, filename);
        this.configuration = configuration;
        NestedClassWriterImpl classW = new NestedClassWriterImpl(this);
        writers = new AbstractMemberWriter[]
            {classW, classW, classW, classW, classW, classW,
            new FieldWriterImpl(this),
            new MethodWriterImpl(this),
            new ConstructorWriterImpl(this),
            new EnumConstantWriterImpl(this),
            new AnnotationTypeOptionalMemberWriterImpl(this, null)};
    }

    /**
     * Get list of all the deprecated classes and members in all the Packages
     * specified on the Command Line.
     * Then instantiate DeprecatedListWriter and generate File.
     *
     * @param configuration the current configuration of the doclet.
     */
    public static void generate(ConfigurationImpl configuration) {
        String filename = "deprecated-list.html";
        try {
            DeprecatedListWriter depr =
                   new DeprecatedListWriter(configuration, filename);
            depr.generateDeprecatedListFile(
                   new DeprecatedAPIListBuilder(configuration.root));
            depr.close();
        } catch (IOException exc) {
            configuration.standardmessage.error(
                        "doclet.exception_encountered",
                        exc.toString(), filename);
            throw new DocletAbortException();
        }
    }

    /**
     * Print the deprecated API list. Separately print all class kinds and
     * member kinds.
     *
     * @param deprapi list of deprecated API built already.
     */
    protected void generateDeprecatedListFile(DeprecatedAPIListBuilder deprapi)
             throws IOException {
        writeHeader();

        strong(configuration.getText("doclet.Contents"));
        ul();
        for (int i = 0; i < DeprecatedAPIListBuilder.NUM_TYPES; i++) {
            writeIndexLink(deprapi, i);
        }
        ulEnd();
        println();

        String memberTableSummary;
        String[] memberTableHeader = new String[1];
        for (int i = 0; i < DeprecatedAPIListBuilder.NUM_TYPES; i++) {
            if (deprapi.hasDocumentation(i)) {
                writeAnchor(deprapi, i);
                memberTableSummary =
                        configuration.getText("doclet.Member_Table_Summary",
                        configuration.getText(HEADING_KEYS[i]),
                        configuration.getText(SUMMARY_KEYS[i]));
                memberTableHeader[0] = configuration.getText("doclet.0_and_1",
                        configuration.getText(HEADER_KEYS[i]),
                        configuration.getText("doclet.Description"));
                writers[i].printDeprecatedAPI(deprapi.getList(i),
                    HEADING_KEYS[i], memberTableSummary, memberTableHeader);
            }
        }
        printDeprecatedFooter();
    }

    private void writeIndexLink(DeprecatedAPIListBuilder builder,
            int type) {
        if (builder.hasDocumentation(type)) {
            li();
            printHyperLink("#" + ANCHORS[type],
                configuration.getText(HEADING_KEYS[type]));
            println();
        }
    }

    private void writeAnchor(DeprecatedAPIListBuilder builder, int type) {
        if (builder.hasDocumentation(type)) {
            anchor(ANCHORS[type]);
        }
    }

    /**
     * Print the navigation bar and header for the deprecated API Listing.
     */
    protected void writeHeader() {
        printHtmlHeader(configuration.getText("doclet.Window_Deprecated_List"),
            null, true);
        printTop();
        navLinks(true);
        hr();
        center();
        h2();
        strongText("doclet.Deprecated_API");
        h2End();
        centerEnd();

        hr(4, "noshade");
    }

    /**
     * Print the navigation bar and the footer for the deprecated API Listing.
     */
    protected void printDeprecatedFooter() {
        hr();
        navLinks(false);
        printBottom();
        printBodyHtmlEnd();
    }

    /**
     * Highlight the word "Deprecated" in the navigation bar as this is the same
     * page.
     */
    protected void navLinkDeprecated() {
        navCellRevStart();
        fontStyle("NavBarFont1Rev");
        strongText("doclet.navDeprecated");
        fontEnd();
        navCellEnd();
    }
}
