/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * Generate Class Hierarchy page for all the Classes in this run.  Use
 * ClassTree for building the Tree. The name of
 * the generated file is "overview-tree.html" and it is generated in the
 * current or the destination directory.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Atul M Dambalkar
 * @author Bhavesh Patel (Modified)
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
            DocPath filename, ClassTree classtree)
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
        DocPath filename = DocPaths.OVERVIEW_TREE;
        try {
            treegen = new TreeWriter(configuration, filename, classtree);
            treegen.generateTreeFile();
            treegen.close();
        } catch (IOException exc) {
            configuration.standardmessage.error(
                        "doclet.exception_encountered",
                        exc.toString(), filename);
            throw new DocletAbortException(exc);
        }
    }

    /**
     * Generate the interface hierarchy and class hierarchy.
     */
    public void generateTreeFile() throws IOException {
        Content body = getTreeHeader();
        Content headContent = getResource("doclet.Hierarchy_For_All_Packages");
        Content heading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, false,
                HtmlStyle.title, headContent);
        Content div = HtmlTree.DIV(HtmlStyle.header, heading);
        addPackageTreeLinks(div);
        body.addContent(div);
        HtmlTree divTree = new HtmlTree(HtmlTag.DIV);
        divTree.addStyle(HtmlStyle.contentContainer);
        addTree(classtree.baseclasses(), "doclet.Class_Hierarchy", divTree);
        addTree(classtree.baseinterfaces(), "doclet.Interface_Hierarchy", divTree);
        addTree(classtree.baseAnnotationTypes(), "doclet.Annotation_Type_Hierarchy", divTree);
        addTree(classtree.baseEnums(), "doclet.Enum_Hierarchy", divTree);
        body.addContent(divTree);
        addNavLinks(false, body);
        addBottom(body);
        printHtmlDocument(null, true, body);
    }

    /**
     * Add the links to all the package tree files.
     *
     * @param contentTree the content tree to which the links will be added
     */
    protected void addPackageTreeLinks(Content contentTree) {
        //Do nothing if only unnamed package is used
        if (packages.length == 1 && packages[0].name().length() == 0) {
            return;
        }
        if (!classesonly) {
            Content span = HtmlTree.SPAN(HtmlStyle.packageHierarchyLabel,
                    getResource("doclet.Package_Hierarchies"));
            contentTree.addContent(span);
            HtmlTree ul = new HtmlTree(HtmlTag.UL);
            ul.addStyle(HtmlStyle.horizontal);
            for (int i = 0; i < packages.length; i++) {
                // If the package name length is 0 or if -nodeprecated option
                // is set and the package is marked as deprecated, do not include
                // the page in the list of package hierarchies.
                if (packages[i].name().length() == 0 ||
                        (configuration.nodeprecated && Util.isDeprecated(packages[i]))) {
                    continue;
                }
                DocPath link = pathString(packages[i], DocPaths.PACKAGE_TREE);
                Content li = HtmlTree.LI(getHyperLink(
                        link, new StringContent(packages[i].name())));
                if (i < packages.length - 1) {
                    li.addContent(", ");
                }
                ul.addContent(li);
            }
            contentTree.addContent(ul);
        }
    }

    /**
     * Get the tree header.
     *
     * @return a content tree for the tree header
     */
    protected Content getTreeHeader() {
        String title = configuration.getText("doclet.Window_Class_Hierarchy");
        Content bodyTree = getBody(true, getWindowTitle(title));
        addTop(bodyTree);
        addNavLinks(true, bodyTree);
        return bodyTree;
    }
}
