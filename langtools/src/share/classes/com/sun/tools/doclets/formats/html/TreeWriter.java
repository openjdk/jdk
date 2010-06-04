/*
 * Copyright (c) 1997, 2005, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * Generate Class Hierarchy page for all the Classes in this run.  Use
 * ClassTree for building the Tree. The name of
 * the generated file is "overview-tree.html" and it is generated in the
 * current or the destination directory.
 *
 * @author Atul M Dambalkar
 */
public class TreeWriter extends AbstractTreeWriter {

    /**
     * Packages in this run.
     */
    private PackageDoc[] packages;

    /**
     * True if there are no packages specified on the command line,
     * False otherwise.
     */
    private boolean classesonly;

    /**
     * Constructor to construct TreeWriter object.
     *
     * @param configuration the current configuration of the doclet.
     * @param filename String filename
     * @param classtree the tree being built.
     */
    public TreeWriter(ConfigurationImpl configuration,
            String filename, ClassTree classtree)
    throws IOException {
        super(configuration, filename, classtree);
        packages = configuration.packages;
    classesonly = packages.length == 0;
    }

    /**
     * Create a TreeWriter object and use it to generate the
     * "overview-tree.html" file.
     *
     * @param classtree the class tree being documented.
     * @throws  DocletAbortException
     */
    public static void generate(ConfigurationImpl configuration,
                                ClassTree classtree) {
        TreeWriter treegen;
        String filename = "overview-tree.html";
        try {
            treegen = new TreeWriter(configuration, filename, classtree);
            treegen.generateTreeFile();
            treegen.close();
        } catch (IOException exc) {
            configuration.standardmessage.error(
                        "doclet.exception_encountered",
                        exc.toString(), filename);
            throw new DocletAbortException();
        }
    }

    /**
     * Print the interface hierarchy and class hierarchy in the file.
     */
    public void generateTreeFile() throws IOException {
        printHtmlHeader(configuration.getText("doclet.Window_Class_Hierarchy"),
            null, true);

        printTreeHeader();

        printPageHeading();

        printPackageTreeLinks();

        generateTree(classtree.baseclasses(), "doclet.Class_Hierarchy");
        generateTree(classtree.baseinterfaces(), "doclet.Interface_Hierarchy");
        generateTree(classtree.baseAnnotationTypes(), "doclet.Annotation_Type_Hierarchy");
        generateTree(classtree.baseEnums(), "doclet.Enum_Hierarchy");

        printTreeFooter();
    }

    /**
     * Generate the links to all the package tree files.
     */
    protected void printPackageTreeLinks() {
        //Do nothing if only unnamed package is used
        if (packages.length == 1 && packages[0].name().length() == 0) {
            return;
        }
        if (!classesonly) {
            dl();
            dt();
            strongText("doclet.Package_Hierarchies");
            dtEnd();
            dd();
            for (int i = 0; i < packages.length; i++) {
                if (packages[i].name().length() == 0) {
                    continue;
                }
                String filename = pathString(packages[i], "package-tree.html");
                printHyperLink(filename, "", packages[i].name());
                if (i < packages.length - 1) {
                    print(", ");
                }
            }
            ddEnd();
            dlEnd();
            hr();
        }
    }

    /**
     * Print the top text (from the -top option) and
     * navigation bar at the top of page.
     */
    protected void printTreeHeader() {
        printTop();
        navLinks(true);
        hr();
    }

    /**
     * Print the navigation bar and bottom text (from the -bottom option)
     * at the bottom of page.
     */
    protected void printTreeFooter() {
        hr();
        navLinks(false);
        printBottom();
        printBodyHtmlEnd();
    }

    /**
     * Print the page title "Hierarchy For All Packages" at the top of the tree
     * page.
     */
    protected void printPageHeading() {
        center();
        h2();
        printText("doclet.Hierarchy_For_All_Packages");
        h2End();
        centerEnd();
    }
}
