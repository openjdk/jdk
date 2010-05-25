/*
 * Copyright (c) 1998, 2005, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.doclets.internal.toolkit.util.*;
import java.io.*;

/**
 * Generate the Help File for the generated API documentation. The help file
 * contents are helpful for browsing the generated documentation.
 *
 * @author Atul M Dambalkar
 */
public class HelpWriter extends HtmlDocletWriter {

    /**
     * Constructor to construct HelpWriter object.
     * @param filename File to be generated.
     */
    public HelpWriter(ConfigurationImpl configuration,
                      String filename) throws IOException {
        super(configuration, filename);
    }

    /**
     * Construct the HelpWriter object and then use it to generate the help
     * file. The name of the generated file is "help-doc.html". The help file
     * will get generated if and only if "-helpfile" and "-nohelp" is not used
     * on the command line.
     * @throws DocletAbortException
     */
    public static void generate(ConfigurationImpl configuration) {
        HelpWriter helpgen;
        String filename = "";
        try {
            filename = "help-doc.html";
            helpgen = new HelpWriter(configuration, filename);
            helpgen.generateHelpFile();
            helpgen.close();
        } catch (IOException exc) {
            configuration.standardmessage.error(
                        "doclet.exception_encountered",
                        exc.toString(), filename);
            throw new DocletAbortException();
        }
    }

    /**
     * Generate the help file contents.
     */
    protected void generateHelpFile() {
        printHtmlHeader(configuration.getText("doclet.Window_Help_title"),
            null, true);
        printTop();
        navLinks(true);  hr();

        printHelpFileContents();

        navLinks(false);
        printBottom();
        printBodyHtmlEnd();
    }

    /**
     * Print the help file contents from the resource file. While generating the
     * help file contents it also keeps track of user options. If "-notree"
     * is used, then the "overview-tree.html" will not get generated and hence
     * help information also will not get generated.
     */
    protected void printHelpFileContents() {
        center(); h1(); printText("doclet.Help_line_1"); h1End(); centerEnd();
        printText("doclet.Help_line_2");
        if (configuration.createoverview) {
            h3(); printText("doclet.Overview"); h3End();
            blockquote(); p();
            printText("doclet.Help_line_3",
                getHyperLink("overview-summary.html",
                configuration.getText("doclet.Overview")));
            blockquoteEnd();
        }
        h3(); printText("doclet.Package"); h3End();
        blockquote(); p(); printText("doclet.Help_line_4");
        ul();
        li(); printText("doclet.Interfaces_Italic");
        li(); printText("doclet.Classes");
        li(); printText("doclet.Enums");
        li(); printText("doclet.Exceptions");
        li(); printText("doclet.Errors");
        li(); printText("doclet.AnnotationTypes");
        ulEnd();
        blockquoteEnd();
        h3(); printText("doclet.Help_line_5"); h3End();
        blockquote(); p(); printText("doclet.Help_line_6");
        ul();
        li(); printText("doclet.Help_line_7");
        li(); printText("doclet.Help_line_8");
        li(); printText("doclet.Help_line_9");
        li(); printText("doclet.Help_line_10");
        li(); printText("doclet.Help_line_11");
        li(); printText("doclet.Help_line_12");
        p();
        li(); printText("doclet.Nested_Class_Summary");
        li(); printText("doclet.Field_Summary");
        li(); printText("doclet.Constructor_Summary");
        li(); printText("doclet.Method_Summary");
        p();
        li(); printText("doclet.Field_Detail");
        li(); printText("doclet.Constructor_Detail");
        li(); printText("doclet.Method_Detail");
        ulEnd();
        printText("doclet.Help_line_13");
        blockquoteEnd();

        //Annotation Types
        blockquoteEnd();
        h3(); printText("doclet.AnnotationType"); h3End();
        blockquote(); p(); printText("doclet.Help_annotation_type_line_1");
        ul();
        li(); printText("doclet.Help_annotation_type_line_2");
        li(); printText("doclet.Help_annotation_type_line_3");
        li(); printText("doclet.Annotation_Type_Required_Member_Summary");
        li(); printText("doclet.Annotation_Type_Optional_Member_Summary");
        li(); printText("doclet.Annotation_Type_Member_Detail");
        ulEnd();
        blockquoteEnd();

        //Enums
        blockquoteEnd();
        h3(); printText("doclet.Enum"); h3End();
        blockquote(); p(); printText("doclet.Help_enum_line_1");
        ul();
        li(); printText("doclet.Help_enum_line_2");
        li(); printText("doclet.Help_enum_line_3");
        li(); printText("doclet.Enum_Constant_Summary");
        li(); printText("doclet.Enum_Constant_Detail");
        ulEnd();
        blockquoteEnd();

        if (configuration.classuse) {
            h3(); printText("doclet.Help_line_14"); h3End();
            blockquote();
            printText("doclet.Help_line_15");
            blockquoteEnd();
        }
        if (configuration.createtree) {
            h3(); printText("doclet.Help_line_16"); h3End();
            blockquote();
            printText("doclet.Help_line_17_with_tree_link",
                 getHyperLink("overview-tree.html",
                 configuration.getText("doclet.Class_Hierarchy")));
            ul();
            li(); printText("doclet.Help_line_18");
            li(); printText("doclet.Help_line_19");
            ulEnd();
            blockquoteEnd();
        }
        if (!(configuration.nodeprecatedlist ||
                  configuration.nodeprecated)) {
            h3(); printText("doclet.Deprecated_API"); h3End();
            blockquote();
            printText("doclet.Help_line_20_with_deprecated_api_link",
                getHyperLink("deprecated-list.html",
                configuration.getText("doclet.Deprecated_API")));
            blockquoteEnd();
        }
        if (configuration.createindex) {
            String indexlink;
            if (configuration.splitindex) {
                indexlink = getHyperLink("index-files/index-1.html",
                    configuration.getText("doclet.Index"));
            } else {
                indexlink = getHyperLink("index-all.html",
                    configuration.getText("doclet.Index"));
            }
            h3(); printText("doclet.Help_line_21"); h3End();
            blockquote();
            printText("doclet.Help_line_22", indexlink);
            blockquoteEnd();
        }
        h3(); printText("doclet.Help_line_23"); h3End();
        printText("doclet.Help_line_24");
        h3(); printText("doclet.Help_line_25"); h3End();
        printText("doclet.Help_line_26"); p();

        h3(); printText("doclet.Serialized_Form"); h3End();
        printText("doclet.Help_line_27"); p();

        h3(); printText("doclet.Constants_Summary"); h3End();
        printText("doclet.Help_line_28"); p();

        font("-1"); em();
        printText("doclet.Help_line_29");
        emEnd(); fontEnd(); br();
        hr();
    }

    /**
     * Highlight the word "Help" in the navigation bar as this is the help file.
     */
    protected void navLinkHelp() {
        navCellRevStart();
        fontStyle("NavBarFont1Rev");
        strongText("doclet.Help");
        fontEnd();
        navCellEnd();
    }
}
