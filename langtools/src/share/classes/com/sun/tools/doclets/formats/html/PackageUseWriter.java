/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.util.*;

import com.sun.javadoc.*;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * Generate package usage information.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Robert G. Field
 * @author Bhavesh Patel (Modified)
 */
public class PackageUseWriter extends SubWriterHolderWriter {

    final PackageDoc pkgdoc;
    final SortedMap<String,Set<ClassDoc>> usingPackageToUsedClasses = new TreeMap<String,Set<ClassDoc>>();

    /**
     * Constructor.
     *
     * @param filename the file to be generated.
     * @throws IOException
     * @throws DocletAbortException
     */
    public PackageUseWriter(ConfigurationImpl configuration,
                            ClassUseMapper mapper, DocPath filename,
                            PackageDoc pkgdoc) throws IOException {
        super(configuration, DocPath.forPackage(pkgdoc).resolve(filename));
        this.pkgdoc = pkgdoc;

        // by examining all classes in this package, find what packages
        // use these classes - produce a map between using package and
        // used classes.
        ClassDoc[] content = pkgdoc.allClasses();
        for (int i = 0; i < content.length; ++i) {
            ClassDoc usedClass = content[i];
            Set<ClassDoc> usingClasses = mapper.classToClass.get(usedClass.qualifiedName());
            if (usingClasses != null) {
                for (Iterator<ClassDoc> it = usingClasses.iterator(); it.hasNext(); ) {
                    ClassDoc usingClass = it.next();
                    PackageDoc usingPackage = usingClass.containingPackage();
                    Set<ClassDoc> usedClasses = usingPackageToUsedClasses
                        .get(usingPackage.name());
                    if (usedClasses == null) {
                        usedClasses = new TreeSet<ClassDoc>();
                        usingPackageToUsedClasses.put(Util.getPackageName(usingPackage),
                                                      usedClasses);
                    }
                    usedClasses.add(usedClass);
                }
            }
        }
    }

    /**
     * Generate a class page.
     *
     * @param configuration the current configuration of the doclet.
     * @param mapper        the mapping of the class usage.
     * @param pkgdoc        the package doc being documented.
     */
    public static void generate(ConfigurationImpl configuration,
                                ClassUseMapper mapper, PackageDoc pkgdoc) {
        PackageUseWriter pkgusegen;
        DocPath filename = DocPaths.PACKAGE_USE;
        try {
            pkgusegen = new PackageUseWriter(configuration,
                                             mapper, filename, pkgdoc);
            pkgusegen.generatePackageUseFile();
            pkgusegen.close();
        } catch (IOException exc) {
            configuration.standardmessage.error(
                "doclet.exception_encountered",
                exc.toString(), filename);
            throw new DocletAbortException(exc);
        }
    }


    /**
     * Generate the package use list.
     */
    protected void generatePackageUseFile() throws IOException {
        Content body = getPackageUseHeader();
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.addStyle(HtmlStyle.contentContainer);
        if (usingPackageToUsedClasses.isEmpty()) {
            div.addContent(getResource(
                    "doclet.ClassUse_No.usage.of.0", pkgdoc.name()));
        } else {
            addPackageUse(div);
        }
        body.addContent(div);
        addNavLinks(false, body);
        addBottom(body);
        printHtmlDocument(null, true, body);
    }

    /**
     * Add the package use information.
     *
     * @param contentTree the content tree to which the package use information will be added
     */
    protected void addPackageUse(Content contentTree) throws IOException {
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.addStyle(HtmlStyle.blockList);
        if (configuration.packages.length > 1) {
            addPackageList(ul);
        }
        addClassList(ul);
        contentTree.addContent(ul);
    }

    /**
     * Add the list of packages that use the given package.
     *
     * @param contentTree the content tree to which the package list will be added
     */
    protected void addPackageList(Content contentTree) throws IOException {
        Content table = HtmlTree.TABLE(0, 3, 0, useTableSummary,
                getTableCaption(configuration.getResource(
                "doclet.ClassUse_Packages.that.use.0",
                getPackageLink(pkgdoc, Util.getPackageName(pkgdoc)))));
        table.addContent(getSummaryTableHeader(packageTableHeader, "col"));
        Content tbody = new HtmlTree(HtmlTag.TBODY);
        Iterator<String> it = usingPackageToUsedClasses.keySet().iterator();
        for (int i = 0; it.hasNext(); i++) {
            PackageDoc pkg = configuration.root.packageNamed(it.next());
            HtmlTree tr = new HtmlTree(HtmlTag.TR);
            if (i % 2 == 0) {
                tr.addStyle(HtmlStyle.altColor);
            } else {
                tr.addStyle(HtmlStyle.rowColor);
            }
            addPackageUse(pkg, tr);
            tbody.addContent(tr);
        }
        table.addContent(tbody);
        Content li = HtmlTree.LI(HtmlStyle.blockList, table);
        contentTree.addContent(li);
    }

    /**
     * Add the list of classes that use the given package.
     *
     * @param contentTree the content tree to which the class list will be added
     */
    protected void addClassList(Content contentTree) throws IOException {
        String[] classTableHeader = new String[] {
            configuration.getText("doclet.0_and_1",
                    configuration.getText("doclet.Class"),
                    configuration.getText("doclet.Description"))
        };
        Iterator<String> itp = usingPackageToUsedClasses.keySet().iterator();
        while (itp.hasNext()) {
            String packageName = itp.next();
            PackageDoc usingPackage = configuration.root.packageNamed(packageName);
            HtmlTree li = new HtmlTree(HtmlTag.LI);
            li.addStyle(HtmlStyle.blockList);
            if (usingPackage != null) {
                li.addContent(getMarkerAnchor(usingPackage.name()));
            }
            String tableSummary = configuration.getText("doclet.Use_Table_Summary",
                    configuration.getText("doclet.classes"));
            Content table = HtmlTree.TABLE(0, 3, 0, tableSummary,
                    getTableCaption(configuration.getResource(
                    "doclet.ClassUse_Classes.in.0.used.by.1",
                    getPackageLink(pkgdoc, Util.getPackageName(pkgdoc)),
                    getPackageLink(usingPackage, Util.getPackageName(usingPackage)))));
            table.addContent(getSummaryTableHeader(classTableHeader, "col"));
            Content tbody = new HtmlTree(HtmlTag.TBODY);
            Iterator<ClassDoc> itc =
                    usingPackageToUsedClasses.get(packageName).iterator();
            for (int i = 0; itc.hasNext(); i++) {
                HtmlTree tr = new HtmlTree(HtmlTag.TR);
                if (i % 2 == 0) {
                    tr.addStyle(HtmlStyle.altColor);
                } else {
                    tr.addStyle(HtmlStyle.rowColor);
                }
                addClassRow(itc.next(), packageName, tr);
                tbody.addContent(tr);
            }
            table.addContent(tbody);
            li.addContent(table);
            contentTree.addContent(li);
        }
    }

    /**
     * Add a row for the class that uses the given package.
     *
     * @param usedClass the class that uses the given package
     * @param packageName the name of the package to which the class belongs
     * @param contentTree the content tree to which the row will be added
     */
    protected void addClassRow(ClassDoc usedClass, String packageName,
            Content contentTree) {
        DocPath dp = pathString(usedClass,
                DocPaths.CLASS_USE.resolve(DocPath.forName(usedClass)));
        Content td = HtmlTree.TD(HtmlStyle.colOne,
                getHyperLink(dp.fragment(packageName), new StringContent(usedClass.name())));
        addIndexComment(usedClass, td);
        contentTree.addContent(td);
    }

    /**
     * Add the package use information.
     *
     * @param pkg the package that used the given package
     * @param contentTree the content tree to which the information will be added
     */
    protected void addPackageUse(PackageDoc pkg, Content contentTree) throws IOException {
        Content tdFirst = HtmlTree.TD(HtmlStyle.colFirst,
                getHyperLink(Util.getPackageName(pkg),
                new StringContent(Util.getPackageName(pkg))));
        contentTree.addContent(tdFirst);
        HtmlTree tdLast = new HtmlTree(HtmlTag.TD);
        tdLast.addStyle(HtmlStyle.colLast);
        if (pkg != null && pkg.name().length() != 0) {
            addSummaryComment(pkg, tdLast);
        } else {
            tdLast.addContent(getSpace());
        }
        contentTree.addContent(tdLast);
    }

    /**
     * Get the header for the package use listing.
     *
     * @return a content tree representing the package use header
     */
    protected Content getPackageUseHeader() {
        String packageText = configuration.getText("doclet.Package");
        String name = pkgdoc.name();
        String title = configuration.getText("doclet.Window_ClassUse_Header",
                packageText, name);
        Content bodyTree = getBody(true, getWindowTitle(title));
        addTop(bodyTree);
        addNavLinks(true, bodyTree);
        ContentBuilder headContent = new ContentBuilder();
        headContent.addContent(getResource("doclet.ClassUse_Title", packageText));
        headContent.addContent(new HtmlTree(HtmlTag.BR));
        headContent.addContent(name);
        Content heading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, true,
                HtmlStyle.title, headContent);
        Content div = HtmlTree.DIV(HtmlStyle.header, heading);
        bodyTree.addContent(div);
        return bodyTree;
    }

    /**
     * Get this package link.
     *
     * @return a content tree for the package link
     */
    protected Content getNavLinkPackage() {
        Content linkContent = getHyperLink(DocPaths.PACKAGE_SUMMARY,
                packageLabel);
        Content li = HtmlTree.LI(linkContent);
        return li;
    }

    /**
     * Get the use link.
     *
     * @return a content tree for the use link
     */
    protected Content getNavLinkClassUse() {
        Content li = HtmlTree.LI(HtmlStyle.navBarCell1Rev, useLabel);
        return li;
    }

    /**
     * Get the tree link.
     *
     * @return a content tree for the tree link
     */
    protected Content getNavLinkTree() {
        Content linkContent = getHyperLink(DocPaths.PACKAGE_TREE,
                treeLabel);
        Content li = HtmlTree.LI(linkContent);
        return li;
    }
}
