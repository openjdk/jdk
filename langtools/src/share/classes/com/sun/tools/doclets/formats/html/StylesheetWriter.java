/*
 * Copyright 1998-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

import com.sun.tools.doclets.internal.toolkit.util.*;

import java.io.*;

/**
 * Writes the style sheet for the doclet output.
 *
 * @author Atul M Dambalkar
 * @author Bhavesh Patel (Modified)
 */
public class StylesheetWriter extends HtmlDocletWriter {

    /**
     * Constructor.
     */
    public StylesheetWriter(ConfigurationImpl configuration,
                            String filename) throws IOException {
        super(configuration, filename);
    }

    /**
     * Generate the style file contents.
     * @throws DocletAbortException
     */
    public static void generate(ConfigurationImpl configuration) {
        StylesheetWriter stylegen;
        String filename = "";
        try {
            filename = "stylesheet.css";
            stylegen = new StylesheetWriter(configuration, filename);
            stylegen.generateStyleFile();
            stylegen.close();
        } catch (IOException exc) {
            configuration.standardmessage.error(
                        "doclet.exception_encountered",
                        exc.toString(), filename);
            throw new DocletAbortException();
        }
    }

    /**
     * Generate the style file contents.
     */
    protected void generateStyleFile() {
        print("/* "); printText("doclet.Style_line_1"); println(" */");
        println("");

        print("/* "); printText("doclet.Style_line_2"); println(" */");
        println("");

        print("/* "); printText("doclet.Style_line_3"); println(" */");
        println("body { background-color: #FFFFFF; color:#000000 }");
        println("");

        print("/* "); printText("doclet.Style_Headings"); println(" */");
        println("h1 { font-size: 145% }");
        println("");

        print("/* "); printText("doclet.Style_line_4"); println(" */");
        print(".TableHeadingColor     { background: #CCCCFF; color:#000000 }");
        print(" /* "); printText("doclet.Style_line_5"); println(" */");
        print(".TableSubHeadingColor  { background: #EEEEFF; color:#000000 }");
        print(" /* "); printText("doclet.Style_line_6"); println(" */");
        print(".TableRowColor         { background: #FFFFFF; color:#000000 }");
        print(" /* "); printText("doclet.Style_line_7"); println(" */");
        println("");

        print("/* "); printText("doclet.Style_line_8"); println(" */");
        println(".FrameTitleFont   { font-size: 100%; font-family: Helvetica, Arial, sans-serif; color:#000000 }");
        println(".FrameHeadingFont { font-size:  90%; font-family: Helvetica, Arial, sans-serif; color:#000000 }");
        println(".FrameItemFont    { font-size:  90%; font-family: Helvetica, Arial, sans-serif; color:#000000 }");
        println("");

       // Removed doclet.Style_line_9 as no longer needed

        print("/* "); printText("doclet.Style_line_10"); println(" */");
        print(".NavBarCell1    { background-color:#EEEEFF; color:#000000}");
        print(" /* "); printText("doclet.Style_line_6"); println(" */");
        print(".NavBarCell1Rev { background-color:#00008B; color:#FFFFFF}");
        print(" /* "); printText("doclet.Style_line_11"); println(" */");

        print(".NavBarFont1    { font-family: Arial, Helvetica, sans-serif; color:#000000;");
        println("color:#000000;}");
        print(".NavBarFont1Rev { font-family: Arial, Helvetica, sans-serif; color:#FFFFFF;");
        println("color:#FFFFFF;}");
        println("");

        print(".NavBarCell2    { font-family: Arial, Helvetica, sans-serif; ");
        println("background-color:#FFFFFF; color:#000000}");
        print(".NavBarCell3    { font-family: Arial, Helvetica, sans-serif; ");
        println("background-color:#FFFFFF; color:#000000}");

        print("/* "); printText("doclet.Style_line_12"); println(" */");
        print(".TableCaption     { background: #CCCCFF; color:#000000; text-align: left; font-size: 150%; font-weight: bold; border-left: 2px ridge; border-right: 2px ridge; border-top: 2px ridge; padding-left: 5px; }");
        print(" /* "); printText("doclet.Style_line_5"); println(" */");
        print(".TableSubCaption  { background: #EEEEFF; color:#000000; text-align: left; font-weight: bold; border-left: 2px ridge; border-right: 2px ridge; border-top: 2px ridge; padding-left: 5px; }");
        print(" /* "); printText("doclet.Style_line_6"); println(" */");
        print(".TableHeader     { text-align: center; font-size: 80%; font-weight: bold; }");
        println("");

    }

}
