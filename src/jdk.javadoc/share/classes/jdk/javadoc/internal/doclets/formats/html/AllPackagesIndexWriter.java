/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.element.PackageElement;

import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlConstants;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Navigation;
import jdk.javadoc.internal.doclets.formats.html.markup.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.formats.html.markup.Table;
import jdk.javadoc.internal.doclets.formats.html.markup.TableHeader;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;

/**
 * Generate the file with list of all the packages in this run.
 */
public class AllPackagesIndexWriter extends HtmlDocletWriter {

    /**
     * The HTML tree for main tag.
     */
    protected HtmlTree mainTree = HtmlTree.MAIN();

    private final Navigation navBar;

    /**
     * Construct AllPackagesIndexWriter object.
     *
     * @param configuration The current configuration
     * @param filename Path to the file which is getting generated.
     */
    public AllPackagesIndexWriter(HtmlConfiguration configuration, DocPath filename) {
        super(configuration, filename);
        this.navBar = new Navigation(null, configuration, fixedNavDiv, PageMode.ALLPACKAGES, path);
    }

    /**
     * Create AllPackagesIndexWriter object.
     *
     * @param configuration The current configuration
     * @throws DocFileIOException
     */
    public static void generate(HtmlConfiguration configuration) throws DocFileIOException {
        generate(configuration, DocPaths.ALLPACKAGES_INDEX);
    }

    private static void generate(HtmlConfiguration configuration, DocPath fileName) throws DocFileIOException {
        AllPackagesIndexWriter allPkgGen = new AllPackagesIndexWriter(configuration, fileName);
        allPkgGen.buildAllPackagesFile();
    }

    /**
     * Print all the packages in the file.
     */
    protected void buildAllPackagesFile() throws DocFileIOException {
        String label = resources.getText("doclet.All_Packages");
        HtmlTree bodyTree = getBody(true, getWindowTitle(label));
        HtmlTree header = HtmlTree.HEADER();
        addTop(header);
        navBar.setUserHeader(getUserHeaderFooter(true));
        header.addContent(navBar.getContent(true));
        bodyTree.addContent(header);
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.setStyle(HtmlStyle.allPackagesContainer);
        addPackages(div);
        Content titleContent = contents.allPackagesLabel;
        Content pHeading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, true,
                HtmlStyle.title, titleContent);
        Content headerDiv = HtmlTree.DIV(HtmlStyle.header, pHeading);
        mainTree.addContent(headerDiv);
        mainTree.addContent(div);
        bodyTree.addContent(mainTree);
        Content footer = HtmlTree.FOOTER();
        navBar.setUserFooter(getUserHeaderFooter(false));
        footer.addContent(navBar.getContent(false));
        addBottom(footer);
        bodyTree.addContent(footer);
        printHtmlDocument(null, "package index", bodyTree);
    }

    /**
     * Add all the packages to the content tree.
     *
     * @param content HtmlTree content to which the links will be added
     */
    protected void addPackages(Content content) {
        Table table = new Table(HtmlStyle.packagesSummary)
                .setCaption(getTableCaption(new StringContent(resources.packageSummary)))
                .setHeader(new TableHeader(contents.packageLabel, contents.descriptionLabel))
                .setColumnStyles(HtmlStyle.colFirst, HtmlStyle.colLast);
        for (PackageElement pkg : configuration.packages) {
            if (!(configuration.nodeprecated && utils.isDeprecated(pkg))) {
                Content packageLinkContent = getPackageLink(pkg, getPackageName(pkg));
                Content summaryContent = new ContentBuilder();
                addSummaryComment(pkg, summaryContent);
                table.addRow(pkg, packageLinkContent, summaryContent);
            }
        }
        HtmlTree li = HtmlTree.LI(HtmlStyle.blockList, table.toContent());
        content.addContent(HtmlTree.UL(HtmlStyle.blockList, li));
    }
}
