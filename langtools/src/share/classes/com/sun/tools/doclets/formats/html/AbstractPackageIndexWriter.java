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

import com.sun.javadoc.*;
import java.io.*;
import java.util.*;

/**
 * Abstract class to generate the overview files in
 * Frame and Non-Frame format. This will be sub-classed by to
 * generate overview-frame.html as well as overview-summary.html.
 *
 * @author Atul M Dambalkar
 * @author Bhavesh Patel (Modified)
 */
public abstract class AbstractPackageIndexWriter extends HtmlDocletWriter {

    /**
     * Array of Packages to be documented.
     */
    protected PackageDoc[] packages;

    /**
     * Constructor. Also initialises the packages variable.
     *
     * @param filename Name of the package index file to be generated.
     */
    public AbstractPackageIndexWriter(ConfigurationImpl configuration,
                                      String filename) throws IOException {
        super(configuration, filename);
        this.relativepathNoSlash = ".";
        packages = configuration.packages;
    }

    protected abstract void printNavigationBarHeader();

    protected abstract void printNavigationBarFooter();

    protected abstract void printOverviewHeader();

    protected abstract void printIndexHeader(String text, String tableSummary);

    protected abstract void printIndexRow(PackageDoc pkg);

    protected abstract void printIndexFooter();

    /**
     * Generate the contants in the package index file. Call appropriate
     * methods from the sub-class in order to generate Frame or Non
     * Frame format.
     * @param title the title of the window.
     * @param includeScript boolean set true if windowtitle script is to be included
     */
    protected void generatePackageIndexFile(String title, boolean includeScript) throws IOException {
        String windowOverview = configuration.getText(title);
        printHtmlHeader(windowOverview,
            configuration.metakeywords.getOverviewMetaKeywords(title,
                configuration.doctitle),
            includeScript);
        printNavigationBarHeader();
        printOverviewHeader();

        generateIndex();

        printOverview();

        printNavigationBarFooter();
        printBodyHtmlEnd();
    }

    /**
     * Default to no overview, overwrite to add overview.
     */
    protected void printOverview() throws IOException {
    }

    /**
     * Generate the frame or non-frame package index.
     */
    protected void generateIndex() {
        printIndexContents(packages, "doclet.Package_Summary",
                configuration.getText("doclet.Member_Table_Summary",
                configuration.getText("doclet.Package_Summary"),
                configuration.getText("doclet.packages")));
    }

    /**
     * Generate code for package index contents. Call appropriate methods from
     * the sub-classes.
     *
     * @param packages Array of packages to be documented.
     * @param text     String which will be used as the heading.
     */
    protected void printIndexContents(PackageDoc[] packages, String text, String tableSummary) {
        if (packages.length > 0) {
            Arrays.sort(packages);
            printIndexHeader(text, tableSummary);
            printAllClassesPackagesLink();
            for(int i = 0; i < packages.length; i++) {
                if (packages[i] != null) {
                    printIndexRow(packages[i]);
                }
            }
            printIndexFooter();
        }
    }

    /**
     * Print the doctitle, if it is specified on the command line.
     */
    protected void printConfigurationTitle() {
        if (configuration.doctitle.length() > 0) {
            center();
            h1(configuration.doctitle);
            centerEnd();
        }
    }

    /**
     * Highlight "Overview" in the strong format, in the navigation bar as this
     * is the overview page.
     */
    protected void navLinkContents() {
        navCellRevStart();
        fontStyle("NavBarFont1Rev");
        strongText("doclet.Overview");
        fontEnd();
        navCellEnd();
    }

    /**
     * Do nothing. This will be overridden in PackageIndexFrameWriter.
     */
    protected void printAllClassesPackagesLink() {
    }
}
