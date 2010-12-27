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

import java.io.*;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.util.*;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.*;

/**
 * Class to generate Tree page for a package. The name of the file generated is
 * "package-tree.html" and it is generated in the respective package directory.
 *
 * @author Atul M Dambalkar
 * @author Bhavesh Patel (Modified)
 */
public class PackageTreeWriter extends AbstractTreeWriter {

    /**
     * Package for which tree is to be generated.
     */
    protected PackageDoc packagedoc;

    /**
     * The previous package name in the alpha-order list.
     */
    protected PackageDoc prev;

    /**
     * The next package name in the alpha-order list.
     */
    protected PackageDoc next;

    /**
     * Constructor.
     * @throws IOException
     * @throws DocletAbortException
     */
    public PackageTreeWriter(ConfigurationImpl configuration,
                             String path, String filename,
                             PackageDoc packagedoc,
                             PackageDoc prev, PackageDoc next)
                      throws IOException {
        super(configuration, path, filename,
              new ClassTree(
                configuration.classDocCatalog.allClasses(packagedoc),
                configuration),
              packagedoc);
        this.packagedoc = packagedoc;
        this.prev = prev;
        this.next = next;
    }

    /**
     * Construct a PackageTreeWriter object and then use it to generate the
     * package tree page.
     *
     * @param pkg      Package for which tree file is to be generated.
     * @param prev     Previous package in the alpha-ordered list.
     * @param next     Next package in the alpha-ordered list.
     * @param noDeprecated  If true, do not generate any information for
     * deprecated classe or interfaces.
     * @throws DocletAbortException
     */
    public static void generate(ConfigurationImpl configuration,
                                PackageDoc pkg, PackageDoc prev,
                                PackageDoc next, boolean noDeprecated) {
        PackageTreeWriter packgen;
        String path = DirectoryManager.getDirectoryPath(pkg);
        String filename = "package-tree.html";
        try {
            packgen = new PackageTreeWriter(configuration, path, filename, pkg,
                prev, next);
            packgen.generatePackageTreeFile();
            packgen.close();
        } catch (IOException exc) {
            configuration.standardmessage.error(
                        "doclet.exception_encountered",
                        exc.toString(), filename);
            throw new DocletAbortException();
        }
    }

    /**
     * Generate a separate tree file for each package.
     */
    protected void generatePackageTreeFile() throws IOException {
        Content body = getPackageTreeHeader();
        Content headContent = getResource("doclet.Hierarchy_For_Package",
                Util.getPackageName(packagedoc));
        Content heading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, false,
                HtmlStyle.title, headContent);
        Content div = HtmlTree.DIV(HtmlStyle.header, heading);
        if (configuration.packages.length > 1) {
            addLinkToMainTree(div);
        }
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
     * Get the package tree header.
     *
     * @return a content tree for the header
     */
    protected Content getPackageTreeHeader() {
        String title = packagedoc.name() + " " +
                configuration.getText("doclet.Window_Class_Hierarchy");
        Content bodyTree = getBody(true, getWindowTitle(title));
        addTop(bodyTree);
        addNavLinks(true, bodyTree);
        return bodyTree;
    }

    /**
     * Add a link to the tree for all the packages.
     *
     * @param div the content tree to which the link will be added
     */
    protected void addLinkToMainTree(Content div) {
        Content span = HtmlTree.SPAN(HtmlStyle.strong,
                getResource("doclet.Package_Hierarchies"));
        div.addContent(span);
        HtmlTree ul = new HtmlTree (HtmlTag.UL);
        ul.addStyle(HtmlStyle.horizontal);
        ul.addContent(getNavLinkMainTree(configuration.getText("doclet.All_Packages")));
        div.addContent(ul);
    }

    /**
     * Get link for the previous package tree file.
     *
     * @return a content tree for the link
     */
    protected Content getNavLinkPrevious() {
        if (prev == null) {
            return getNavLinkPrevious(null);
        } else {
            String path = DirectoryManager.getRelativePath(packagedoc.name(),
                    prev.name());
            return getNavLinkPrevious(path + "package-tree.html");
        }
    }

    /**
     * Get link for the next package tree file.
     *
     * @return a content tree for the link
     */
    protected Content getNavLinkNext() {
        if (next == null) {
            return getNavLinkNext(null);
        } else {
            String path = DirectoryManager.getRelativePath(packagedoc.name(),
                    next.name());
            return getNavLinkNext(path + "package-tree.html");
        }
    }

    /**
     * Get link to the package summary page for the package of this tree.
     *
     * @return a content tree for the package link
     */
    protected Content getNavLinkPackage() {
        Content linkContent = getHyperLink("package-summary.html", "",
                packageLabel);
        Content li = HtmlTree.LI(linkContent);
        return li;
    }
}
