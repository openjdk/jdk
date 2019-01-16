/*
 * Copyright (c) 1998, 2018, Oracle and/or its affiliates. All rights reserved.
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

import jdk.javadoc.internal.doclets.formats.html.markup.Table;
import jdk.javadoc.internal.doclets.formats.html.markup.TableHeader;

import java.util.*;

import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlConstants;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Navigation;
import jdk.javadoc.internal.doclets.formats.html.markup.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.ClassUseMapper;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;

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
    final String packageUseTableSummary;
    private final Navigation navBar;

    /**
     * Constructor.
     *
     * @param configuration the configuration
     * @param mapper a mapper to provide details of where elements are used
     * @param filename the file to be generated
     * @param pkgElement the package element to be documented
     */
    public PackageUseWriter(HtmlConfiguration configuration,
                            ClassUseMapper mapper, DocPath filename,
                            PackageElement pkgElement) {
        super(configuration, configuration.docPaths.forPackage(pkgElement).resolve(filename));
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

        packageUseTableSummary = resources.getText("doclet.Use_Table_Summary",
                resources.getText("doclet.packages"));
        this.navBar = new Navigation(packageElement, configuration, fixedNavDiv, PageMode.USE, path);
    }

    /**
     * Generate a class page.
     *
     * @param configuration the current configuration of the doclet.
     * @param mapper        the mapping of the class usage.
     * @param pkgElement    the package being documented.
     * @throws DocFileIOException if there is a problem generating the package use page
     */
    public static void generate(HtmlConfiguration configuration,
                                ClassUseMapper mapper, PackageElement pkgElement)
            throws DocFileIOException {
        DocPath filename = DocPaths.PACKAGE_USE;
        PackageUseWriter pkgusegen = new PackageUseWriter(configuration, mapper, filename, pkgElement);
        pkgusegen.generatePackageUseFile();
    }

    /**
     * Generate the package use list.
     * @throws DocFileIOException if there is a problem generating the package use page
     */
    protected void generatePackageUseFile() throws DocFileIOException {
        HtmlTree body = getPackageUseHeader();
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.setStyle(HtmlStyle.contentContainer);
        if (usingPackageToUsedClasses.isEmpty()) {
            div.addContent(contents.getContent("doclet.ClassUse_No.usage.of.0", utils.getPackageName(packageElement)));
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
        navBar.setUserFooter(getUserHeaderFooter(false));
        tree.addContent(navBar.getContent(false));
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
    protected void addPackageUse(Content contentTree) {
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.setStyle(HtmlStyle.blockList);
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
    protected void addPackageList(Content contentTree) {
        Content caption = contents.getContent(
                "doclet.ClassUse_Packages.that.use.0",
                getPackageLink(packageElement, utils.getPackageName(packageElement)));
        Table table = new Table(configuration.htmlVersion, HtmlStyle.useSummary)
                .setSummary(packageUseTableSummary)
                .setCaption(caption)
                .setHeader(getPackageTableHeader())
                .setColumnStyles(HtmlStyle.colFirst, HtmlStyle.colLast);
        for (String pkgname: usingPackageToUsedClasses.keySet()) {
            PackageElement pkg = utils.elementUtils.getPackageElement(pkgname);
            Content packageLink = links.createLink(getPackageAnchorName(pkg),
                    new StringContent(utils.getPackageName(pkg)));
            Content summary = new ContentBuilder();
            if (pkg != null && !pkg.isUnnamed()) {
                addSummaryComment(pkg, summary);
            } else {
                summary.addContent(Contents.SPACE);
            }
            table.addRow(packageLink, summary);
        }
        Content li = HtmlTree.LI(HtmlStyle.blockList, table.toContent());
        contentTree.addContent(li);
    }

    /**
     * Add the list of classes that use the given package.
     *
     * @param contentTree the content tree to which the class list will be added
     */
    protected void addClassList(Content contentTree) {
        TableHeader classTableHeader = new TableHeader(
                contents.classLabel, contents.descriptionLabel);
        for (String packageName : usingPackageToUsedClasses.keySet()) {
            PackageElement usingPackage = utils.elementUtils.getPackageElement(packageName);
            HtmlTree li = new HtmlTree(HtmlTag.LI);
            li.setStyle(HtmlStyle.blockList);
            li.addContent(links.createAnchor(getPackageAnchorName(usingPackage)));
            String tableSummary = resources.getText("doclet.Use_Table_Summary",
                                                        resources.getText("doclet.classes"));
            Content caption = contents.getContent(
                    "doclet.ClassUse_Classes.in.0.used.by.1",
                    getPackageLink(packageElement, utils.getPackageName(packageElement)),
                    getPackageLink(usingPackage, utils.getPackageName(usingPackage)));
            Table table = new Table(configuration.htmlVersion, HtmlStyle.useSummary)
                    .setSummary(tableSummary)
                    .setCaption(caption)
                    .setHeader(classTableHeader)
                    .setColumnStyles(HtmlStyle.colFirst, HtmlStyle.colLast);
            for (TypeElement te : usingPackageToUsedClasses.get(packageName)) {
                DocPath dp = pathString(te,
                        DocPaths.CLASS_USE.resolve(docPaths.forName(te)));
                Content stringContent = new StringContent(utils.getSimpleName(te));
                Content typeContent = links.createLink(dp.fragment(getPackageAnchorName(usingPackage)),
                        stringContent);
                Content summary = new ContentBuilder();
                addIndexComment(te, summary);

                table.addRow(typeContent, summary);
            }
            li.addContent(table.toContent());
            contentTree.addContent(li);
        }
    }

    /**
     * Get the header for the package use listing.
     *
     * @return a content tree representing the package use header
     */
    private HtmlTree getPackageUseHeader() {
        String packageText = resources.getText("doclet.Package");
        String name = packageElement.isUnnamed() ? "" : utils.getPackageName(packageElement);
        String title = resources.getText("doclet.Window_ClassUse_Header", packageText, name);
        HtmlTree bodyTree = getBody(true, getWindowTitle(title));
        HtmlTree htmlTree = (configuration.allowTag(HtmlTag.HEADER))
                ? HtmlTree.HEADER()
                : bodyTree;
        addTop(htmlTree);
        Content linkContent = getModuleLink(utils.elementUtils.getModuleOf(packageElement),
                contents.moduleLabel);
        navBar.setNavLinkModule(linkContent);
        navBar.setUserHeader(getUserHeaderFooter(true));
        htmlTree.addContent(navBar.getContent(true));
        if (configuration.allowTag(HtmlTag.HEADER)) {
            bodyTree.addContent(htmlTree);
        }
        ContentBuilder headContent = new ContentBuilder();
        headContent.addContent(contents.getContent("doclet.ClassUse_Title", packageText));
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
}
