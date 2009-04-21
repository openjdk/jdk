/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
import com.sun.javadoc.*;
import java.io.*;
import java.util.*;

/**
 * Generate the package index page "overview-summary.html" for the right-hand
 * frame. A click on the package name on this page will update the same frame
 * with the "pacakge-summary.html" file for the clicked package.
 *
 * @author Atul M Dambalkar
 * @author Bhavesh Patel (Modified)
 */
public class PackageIndexWriter extends AbstractPackageIndexWriter {

    /**
     * Root of the program structure. Used for "overview" documentation.
     */
    private RootDoc root;

    /**
     * Map representing the group of packages as specified on the command line.
     *
     * @see Group
     */
    private Map<String,List<PackageDoc>> groupPackageMap;

    /**
     * List to store the order groups as specified on the command line.
     */
    private List<String> groupList;

    /**
     * Construct the PackageIndexWriter. Also constructs the grouping
     * information as provided on the command line by "-group" option. Stores
     * the order of groups specified by the user.
     *
     * @see Group
     */
    public PackageIndexWriter(ConfigurationImpl configuration,
                              String filename)
                       throws IOException {
        super(configuration, filename);
        this.root = configuration.root;
        groupPackageMap = configuration.group.groupPackages(packages);
        groupList = configuration.group.getGroupList();
    }

    /**
     * Generate the package index page for the right-hand frame.
     *
     * @param configuration the current configuration of the doclet.
     */
    public static void generate(ConfigurationImpl configuration) {
        PackageIndexWriter packgen;
        String filename = "overview-summary.html";
        try {
            packgen = new PackageIndexWriter(configuration, filename);
            packgen.generatePackageIndexFile("doclet.Window_Overview_Summary", true);
            packgen.close();
        } catch (IOException exc) {
            configuration.standardmessage.error(
                        "doclet.exception_encountered",
                        exc.toString(), filename);
            throw new DocletAbortException();
        }
    }

    /**
     * Print each package in separate rows in the index table. Generate link
     * to each package.
     *
     * @param pkg Package to which link is to be generated.
     */
    protected void printIndexRow(PackageDoc pkg) {
        if(pkg != null && pkg.name().length() > 0) {
            trBgcolorStyle("white", "TableRowColor");
            summaryRow(20);
            strong();
            printPackageLink(pkg, Util.getPackageName(pkg), false);
            strongEnd();
            summaryRowEnd();
            summaryRow(0);
            printSummaryComment(pkg);
            summaryRowEnd();
            trEnd();
       }
    }

    /**
     * Depending upon the grouping information and their titles, generate
     * separate table indices for each package group.
     */
    protected void generateIndex() {
        for (int i = 0; i < groupList.size(); i++) {
        String groupname = groupList.get(i);
        List<PackageDoc> list = groupPackageMap.get(groupname);
            if (list != null && list.size() > 0) {
                printIndexContents(list.toArray(new PackageDoc[list.size()]),
                        groupname,
                        configuration.getText("doclet.Member_Table_Summary",
                        groupname,
                        configuration.getText("doclet.packages")));
            }
        }
    }

    /**
     * Print the overview summary comment for this documentation. Print one line
     * summary at the top of the page and generate a link to the description,
     * which is generated at the end of this page.
     */
    protected void printOverviewHeader() {
        if (root.inlineTags().length > 0) {
            printSummaryComment(root);
            p();
            strong(configuration.getText("doclet.See"));
            br();
            printNbsps();
            printHyperLink("", "overview_description",
                configuration.getText("doclet.Description"), true);
            p();
        }
    }

    /**
     * Print Html tags for the table for this package index.
     */
    protected void printIndexHeader(String text, String tableSummary) {
        tableIndexSummary(tableSummary);
        tableCaptionStart();
        print(text);
        tableCaptionEnd();
        summaryTableHeader(packageTableHeader, "col");
    }

    /**
     * Print Html closing tags for the table for this package index.
     */
    protected void printIndexFooter() {
        tableEnd();
        p();
        space();
    }

    /**
     * Print the overview comment as provided in the file specified by the
     * "-overview" option on the command line.
     */
    protected void printOverviewComment() {
        if (root.inlineTags().length > 0) {
            anchor("overview_description");
            p();
            printInlineComment(root);
            p();
        }
    }

    /**
     * Call {@link #printOverviewComment()} and then genrate the tag information
     * as provided in the file specified by the "-overview" option on the
     * command line.
     */
    protected void printOverview() throws IOException {
        printOverviewComment();
        printTags(root);
    }

    /**
     * Print the top text (from the -top option), the upper
     * navigation bar, and then the title (from the"-title"
     * option), at the top of page.
     */
    protected void printNavigationBarHeader() {
        printTop();
        navLinks(true);
        hr();
        printConfigurationTitle();
    }

    /**
     * Print the lower navigation bar and the bottom text
     * (from the -bottom option) at the bottom of page.
     */
    protected void printNavigationBarFooter() {
        hr();
        navLinks(false);
        printBottom();
    }
}
