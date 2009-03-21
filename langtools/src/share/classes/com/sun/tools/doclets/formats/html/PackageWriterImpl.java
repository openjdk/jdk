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

import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

import com.sun.javadoc.*;
import java.io.*;
import java.util.*;

/**
 * Class to generate file for each package contents in the right-hand
 * frame. This will list all the Class Kinds in the package. A click on any
 * class-kind will update the frame with the clicked class-kind page.
 *
 * @author Atul M Dambalkar
 * @author Bhavesh Patel (Modified)
 */
public class PackageWriterImpl extends HtmlDocletWriter
    implements PackageSummaryWriter {

    /**
     * The prev package name in the alpha-order list.
     */
    protected PackageDoc prev;

    /**
     * The next package name in the alpha-order list.
     */
    protected PackageDoc next;

    /**
     * The package being documented.
     */
    protected PackageDoc packageDoc;

    /**
     * The name of the output file.
     */
    private static final String OUTPUT_FILE_NAME = "package-summary.html";

    /**
     * Constructor to construct PackageWriter object and to generate
     * "package-summary.html" file in the respective package directory.
     * For example for package "java.lang" this will generate file
     * "package-summary.html" file in the "java/lang" directory. It will also
     * create "java/lang" directory in the current or the destination directory
     * if it doesen't exist.
     *
     * @param configuration the configuration of the doclet.
     * @param packageDoc    PackageDoc under consideration.
     * @param prev          Previous package in the sorted array.
     * @param next            Next package in the sorted array.
     */
    public PackageWriterImpl(ConfigurationImpl configuration,
        PackageDoc packageDoc, PackageDoc prev, PackageDoc next)
    throws IOException {
        super(configuration, DirectoryManager.getDirectoryPath(packageDoc), OUTPUT_FILE_NAME,
             DirectoryManager.getRelativePath(packageDoc.name()));
        this.prev = prev;
        this.next = next;
        this.packageDoc = packageDoc;
    }

    /**
     * Return the name of the output file.
     *
     * @return the name of the output file.
     */
    public String getOutputFileName() {
        return OUTPUT_FILE_NAME;
    }

    /**
     * {@inheritDoc}
     */
    public void writeSummaryHeader() {}

    /**
     * {@inheritDoc}
     */
    public void writeSummaryFooter() {}

    /**
     * {@inheritDoc}
     */
    public void writeClassesSummary(ClassDoc[] classes, String label, String tableSummary, String[] tableHeader) {
        if(classes.length > 0) {
            Arrays.sort(classes);
            tableIndexSummary(tableSummary);
            boolean printedHeading = false;
            for (int i = 0; i < classes.length; i++) {
                if (!printedHeading) {
                    printTableCaption(label);
                    printFirstRow(tableHeader);
                    printedHeading = true;
                }
                if (!Util.isCoreClass(classes[i]) ||
                    !configuration.isGeneratedDoc(classes[i])) {
                    continue;
                }
                trBgcolorStyle("white", "TableRowColor");
                summaryRow(15);
                strong();
                printLink(new LinkInfoImpl(LinkInfoImpl.CONTEXT_PACKAGE,
                    classes[i], false));
                strongEnd();
                summaryRowEnd();
                summaryRow(0);
                if (Util.isDeprecated(classes[i])) {
                    strongText("doclet.Deprecated");
                    if (classes[i].tags("deprecated").length > 0) {
                        space();
                        printSummaryDeprecatedComment(classes[i],
                            classes[i].tags("deprecated")[0]);
                    }
                } else {
                    printSummaryComment(classes[i]);
                }
                summaryRowEnd();
                trEnd();
            }
            tableEnd();
            println("&nbsp;");
            p();
        }
    }

    /**
     * Print the table caption for the class-listing.
     *
     * @param label label for the Class kind listing.
     */
    protected void printTableCaption(String label) {
        tableCaptionStart();
        print(label);
        tableCaptionEnd();
    }

    /**
     * Print the table heading for the class-listing.
     *
     * @param tableHeader table header string for the Class listing.
     */
    protected void printFirstRow(String[] tableHeader) {
        summaryTableHeader(tableHeader, "col");
    }

    /**
     * {@inheritDoc}
     */
    public void writePackageDescription() {
        if (packageDoc.inlineTags().length > 0) {
            anchor("package_description");
            h2(configuration.getText("doclet.Package_Description", packageDoc.name()));
            p();
            printInlineComment(packageDoc);
            p();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void writePackageTags() {
        printTags(packageDoc);
    }

    /**
     * {@inheritDoc}
     */
    public void writePackageHeader(String heading) {
        String pkgName = packageDoc.name();
        printHtmlHeader(pkgName,
            configuration.metakeywords.getMetaKeywords(packageDoc), true);
        printTop();
        navLinks(true);
        hr();
        writeAnnotationInfo(packageDoc);
        h2(configuration.getText("doclet.Package") + " " + heading);
        if (packageDoc.inlineTags().length > 0 && ! configuration.nocomment) {
            printSummaryComment(packageDoc);
            p();
            strong(configuration.getText("doclet.See"));
            br();
            printNbsps();
            printHyperLink("", "package_description",
                configuration.getText("doclet.Description"), true);
            p();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void writePackageFooter() {
        hr();
        navLinks(false);
        printBottom();
        printBodyHtmlEnd();
    }

    /**
     * Print "Use" link for this pacakge in the navigation bar.
     */
    protected void navLinkClassUse() {
        navCellStart();
        printHyperLink("package-use.html", "", configuration.getText("doclet.navClassUse"),
                       true, "NavBarFont1");
        navCellEnd();
    }

    /**
     * Print "PREV PACKAGE" link in the navigation bar.
     */
    protected void navLinkPrevious() {
        if (prev == null) {
            printText("doclet.Prev_Package");
        } else {
            String path = DirectoryManager.getRelativePath(packageDoc.name(),
                                                           prev.name());
            printHyperLink(path + "package-summary.html", "",
                configuration.getText("doclet.Prev_Package"), true);
        }
    }

    /**
     * Print "NEXT PACKAGE" link in the navigation bar.
     */
    protected void navLinkNext() {
        if (next == null) {
            printText("doclet.Next_Package");
        } else {
            String path = DirectoryManager.getRelativePath(packageDoc.name(),
                                                           next.name());
            printHyperLink(path + "package-summary.html", "",
                configuration.getText("doclet.Next_Package"), true);
        }
    }

    /**
     * Print "Tree" link in the navigation bar. This will be link to the package
     * tree file.
     */
    protected void navLinkTree() {
        navCellStart();
        printHyperLink("package-tree.html", "", configuration.getText("doclet.Tree"),
                       true, "NavBarFont1");
        navCellEnd();
    }

    /**
     * Highlight "Package" in the navigation bar, as this is the package page.
     */
    protected void navLinkPackage() {
        navCellRevStart();
        fontStyle("NavBarFont1Rev");
        strongText("doclet.Package");
        fontEnd();
        navCellEnd();
    }
}
