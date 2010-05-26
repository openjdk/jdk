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

import com.sun.javadoc.*;
import java.io.*;

/**
 * Generate the package index for the left-hand frame in the generated output.
 * A click on the package name in this frame will update the page in the bottom
 * left hand frame with the listing of contents of the clicked package.
 *
 * @author Atul M Dambalkar
 */
public class PackageIndexFrameWriter extends AbstractPackageIndexWriter {

    /**
     * Construct the PackageIndexFrameWriter object.
     *
     * @param filename Name of the package index file to be generated.
     */
    public PackageIndexFrameWriter(ConfigurationImpl configuration,
                                   String filename) throws IOException {
        super(configuration, filename);
    }

    /**
     * Generate the package index file named "overview-frame.html".
     * @throws DocletAbortException
     */
    public static void generate(ConfigurationImpl configuration) {
        PackageIndexFrameWriter packgen;
        String filename = "overview-frame.html";
        try {
            packgen = new PackageIndexFrameWriter(configuration, filename);
            packgen.generatePackageIndexFile("doclet.Window_Overview", false);
            packgen.close();
        } catch (IOException exc) {
            configuration.standardmessage.error(
                        "doclet.exception_encountered",
                        exc.toString(), filename);
            throw new DocletAbortException();
        }
    }

    /**
     * Print each package name on separate rows.
     *
     * @param pd PackageDoc
     */
    protected void printIndexRow(PackageDoc pd) {
        fontStyle("FrameItemFont");
        if (pd.name().length() > 0) {
            print(getHyperLink(pathString(pd, "package-frame.html"), "",
                pd.name(), false, "", "", "packageFrame"));
        } else {
            print(getHyperLink("package-frame.html", "", "&lt;unnamed package>",
                false, "", "", "packageFrame"));
        }
        fontEnd();
        br();
    }

    /**
     * Print the "-packagesheader" string in strong format, at top of the page,
     * if it is not the empty string.  Otherwise print the "-header" string.
     * Despite the name, there is actually no navigation bar for this page.
     */
    protected void printNavigationBarHeader() {
        printTableHeader(true);
        fontSizeStyle("+1", "FrameTitleFont");
        if (configuration.packagesheader.length() > 0) {
            strong(replaceDocRootDir(configuration.packagesheader));
        } else {
            strong(replaceDocRootDir(configuration.header));
        }
        fontEnd();
        printTableFooter(true);
    }

    /**
     * Do nothing as there is no overview information in this page.
     */
    protected void printOverviewHeader() {
    }

    /**
     * Print Html "table" tag for the package index format.
     *
     * @param text Text string will not be used in this method.
     */
    protected void printIndexHeader(String text, String tableSummary) {
        printTableHeader(false);
    }

    /**
     * Print Html closing "table" tag at the end of the package index.
     */
    protected void printIndexFooter() {
        printTableFooter(false);
    }

    /**
     * Print "All Classes" link at the top of the left-hand frame page.
     */
    protected void printAllClassesPackagesLink() {
        fontStyle("FrameItemFont");
        print(getHyperLink("allclasses-frame.html", "",
            configuration.getText("doclet.All_Classes"), false, "", "",
            "packageFrame"));
        fontEnd();
        p();
        fontSizeStyle("+1", "FrameHeadingFont");
        printText("doclet.Packages");
        fontEnd();
        br();
    }

    /**
     * Just print some space, since there is no navigation bar for this page.
     */
    protected void printNavigationBarFooter() {
        p();
        space();
    }

    /**
     * Print Html closing tags for the table for package index.
     *
     * @param isHeading true if this is a table for a heading.
     */
    private void printTableFooter(boolean isHeading) {
        if (isHeading) {
            thEnd();
        } else {
            tdEnd();
        }
        trEnd();
        tableEnd();
    }

    /**
     * Print Html tags for the table for package index.
     *
     * @param isHeading true if this is a table for a heading.
     */
    private void printTableHeader(boolean isHeading) {
        table();
        tr();
        if (isHeading) {
            thAlignNowrap("left");
        } else {
            tdNowrap();
        }

    }
}
