/*
 * Copyright (c) 1998, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.formats.html;

import java.io.*;
import java.util.*;

import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlConstants;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.ClassUseMapper;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.DocletAbortException;

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

    final PackageElement packageElement;
    final SortedMap<String, Set<TypeElement>> usingPackageToUsedClasses = new TreeMap<>();
    protected HtmlTree mainTree = HtmlTree.MAIN();

    /**
     * Constructor.
     *
     * @param filename the file to be generated.
     * @throws IOException
     * @throws DocletAbortException
     */
    public PackageUseWriter(ConfigurationImpl configuration,
                            ClassUseMapper mapper, DocPath filename,
                            PackageElement pkgElement) throws IOException {
        super(configuration, DocPath.forPackage(pkgElement).resolve(filename));
        this.packageElement = pkgElement;

        // by examining all classes in this package, find what packages
        // use these classes - produce a map between using package and
        // used classes.
        for (TypeElement usedClass : utils.getEnclosedTypeElements(pkgElement)) {
            Set<TypeElement> usingClasses = mapper.classToClass.get(usedClass);
            if (usingClasses != null) {
                for (TypeElement usingClass : usingClasses) {
                    PackageElement usingPackage = utils.containingPackage(usingClass);
                    Set<TypeElement> usedClasses = usingPackageToUsedClasses
                            .get(utils.getPackageName(usingPackage));
                    if (usedClasses == null) {
                        usedClasses = new TreeSet<>(utils.makeGeneralPurposeComparator());
                        usingPackageToUsedClasses.put(utils.getPackageName(usingPackage),
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
     * @param pkgElement    the package being documented.
     */
    public static void generate(ConfigurationImpl configuration,
                                ClassUseMapper mapper, PackageElement pkgElement) {
        PackageUseWriter pkgusegen;
        DocPath filename = DocPaths.PACKAGE_USE;
        try {
            pkgusegen = new PackageUseWriter(configuration, mapper, filename, pkgElement);
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
        HtmlTree body = getPackageUseHeader();
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.addStyle(HtmlStyle.contentContainer);
        if (usingPackageToUsedClasses.isEmpty()) {
            div.addContent(getResource("doclet.ClassUse_No.usage.of.0", utils.getPackageName(packageElement)));
        } else {
            addPackageUse(div);
        }
        if (configuration.allowTag(HtmlTag.MAIN)) {
            mainTree.addContent(div);
            body.addContent(mainTree);
        } else {
            body.addContent(div);
        }
        HtmlTree tree = (configuration.allowTag(HtmlTag.FOOTER))
                ? HtmlTree.FOOTER()
                : body;
        addNavLinks(false, tree);
        addBottom(tree);
        if (configuration.allowTag(HtmlTag.FOOTER)) {
            body.addContent(tree);
        }
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
        if (configuration.packages.size() > 1) {
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
        Content caption = getTableCaption(configuration.getResource(
                "doclet.ClassUse_Packages.that.use.0",
                getPackageLink(packageElement, utils.getPackageName(packageElement))));
        Content table = (configuration.isOutputHtml5())
                ? HtmlTree.TABLE(HtmlStyle.useSummary, caption)
                : HtmlTree.TABLE(HtmlStyle.useSummary, useTableSummary, caption);
        table.addContent(getSummaryTableHeader(packageTableHeader, "col"));
        Content tbody = new HtmlTree(HtmlTag.TBODY);
        boolean altColor = true;
        for (String pkgname: usingPackageToUsedClasses.keySet()) {
            PackageElement pkg = utils.elementUtils.getPackageElement(pkgname);
            HtmlTree tr = new HtmlTree(HtmlTag.TR);
            tr.addStyle(altColor ? HtmlStyle.altColor : HtmlStyle.rowColor);
            altColor = !altColor;
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
        List<String> classTableHeader = Arrays.asList(
            configuration.getText("doclet.0_and_1",
                    configuration.getText("doclet.Class"),
                    configuration.getText("doclet.Description")));
        for (String packageName : usingPackageToUsedClasses.keySet()) {
            PackageElement usingPackage = utils.elementUtils.getPackageElement(packageName);
            HtmlTree li = new HtmlTree(HtmlTag.LI);
            li.addStyle(HtmlStyle.blockList);
            if (usingPackage != null) {
                li.addContent(getMarkerAnchor(utils.getPackageName(usingPackage)));
            }
            String tableSummary = configuration.getText("doclet.Use_Table_Summary",
                                                        configuration.getText("doclet.classes"));
            Content caption = getTableCaption(configuration.getResource(
                    "doclet.ClassUse_Classes.in.0.used.by.1",
                    getPackageLink(packageElement, utils.getPackageName(packageElement)),
                    getPackageLink(usingPackage, utils.getPackageName(usingPackage))));
            Content table = (configuration.isOutputHtml5())
                    ? HtmlTree.TABLE(HtmlStyle.useSummary, caption)
                    : HtmlTree.TABLE(HtmlStyle.useSummary, tableSummary, caption);
            table.addContent(getSummaryTableHeader(classTableHeader, "col"));
            Content tbody = new HtmlTree(HtmlTag.TBODY);
            boolean altColor = true;
            for (TypeElement te : usingPackageToUsedClasses.get(packageName)) {
                HtmlTree tr = new HtmlTree(HtmlTag.TR);
                tr.addStyle(altColor ? HtmlStyle.altColor : HtmlStyle.rowColor);
                altColor = !altColor;
                addClassRow(te, usingPackage, tr);
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
     * @param pkg  the package to which the class belongs
     * @param contentTree the content tree to which the row will be added
     */
    protected void addClassRow(TypeElement usedClass, PackageElement pkg,
            Content contentTree) {
        DocPath dp = pathString(usedClass,
                DocPaths.CLASS_USE.resolve(DocPath.forName(utils, usedClass)));
        StringContent stringContent = new StringContent(utils.getSimpleName(usedClass));
        Content td = HtmlTree.TD(HtmlStyle.colOne,
                getHyperLink(dp.fragment(getPackageAnchorName(pkg)), stringContent));
        addIndexComment(usedClass, td);
        contentTree.addContent(td);
    }

    /**
     * Add the package use information.
     *
     * @param pkg the package that used the given package
     * @param contentTree the content tree to which the information will be added
     */
    protected void addPackageUse(PackageElement pkg, Content contentTree) throws IOException {
        Content tdFirst = HtmlTree.TD(HtmlStyle.colFirst,
                getHyperLink(utils.getPackageName(pkg),
                new StringContent(utils.getPackageName(pkg))));
        contentTree.addContent(tdFirst);
        HtmlTree tdLast = new HtmlTree(HtmlTag.TD);
        tdLast.addStyle(HtmlStyle.colLast);
        if (pkg != null && !pkg.isUnnamed()) {
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
    protected HtmlTree getPackageUseHeader() {
        String packageText = configuration.getText("doclet.Package");
        String name = packageElement.isUnnamed() ? "" : utils.getPackageName(packageElement);
        String title = configuration.getText("doclet.Window_ClassUse_Header", packageText, name);
        HtmlTree bodyTree = getBody(true, getWindowTitle(title));
        HtmlTree htmlTree = (configuration.allowTag(HtmlTag.HEADER))
                ? HtmlTree.HEADER()
                : bodyTree;
        addTop(htmlTree);
        addNavLinks(true, htmlTree);
        if (configuration.allowTag(HtmlTag.HEADER)) {
            bodyTree.addContent(htmlTree);
        }
        ContentBuilder headContent = new ContentBuilder();
        headContent.addContent(getResource("doclet.ClassUse_Title", packageText));
        headContent.addContent(new HtmlTree(HtmlTag.BR));
        headContent.addContent(name);
        Content heading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, true,
                HtmlStyle.title, headContent);
        Content div = HtmlTree.DIV(HtmlStyle.header, heading);
        if (configuration.allowTag(HtmlTag.MAIN)) {
            mainTree.addContent(div);
        } else {
            bodyTree.addContent(div);
        }
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
