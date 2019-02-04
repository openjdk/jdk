/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import javax.lang.model.element.PackageElement;

import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.Group;

/**
 * Generate the package index page "overview-summary.html" for the right-hand
 * frame. A click on the package name on this page will update the same frame
 * with the "package-summary.html" file for the clicked package.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Atul M Dambalkar
 * @author Bhavesh Patel (Modified)
 */
public class PackageIndexWriter extends AbstractPackageIndexWriter {

    /**
     * Construct the PackageIndexWriter. Also constructs the grouping
     * information as provided on the command line by "-group" option. Stores
     * the order of groups specified by the user.
     *
     * @param configuration the configuration for this doclet
     * @param filename the path of the page to be generated
     * @see Group
     */
    public PackageIndexWriter(HtmlConfiguration configuration, DocPath filename) {
        super(configuration, filename);
    }

    /**
     * Generate the package index page for the right-hand frame.
     *
     * @param configuration the current configuration of the doclet.
     * @throws DocFileIOException if there is a problem generating the package index page
     */
    public static void generate(HtmlConfiguration configuration) throws DocFileIOException {
        DocPath filename = DocPaths.overviewSummary(configuration.frames);
        PackageIndexWriter packgen = new PackageIndexWriter(configuration, filename);
        packgen.buildPackageIndexFile("doclet.Window_Overview_Summary", true);
    }

    /**
     * Depending upon the grouping information and their titles, add
     * separate table indices for each package group.
     *
     * @param header the documentation tree to which the navigational links will be added
     * @param main the documentation tree to which the packages list will be added
     */
    @Override
    protected void addIndex(Content header, Content main) {
        addIndexContents(header, main);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addPackagesList(Content main) {
        Map<String, SortedSet<PackageElement>> groupPackageMap
                = configuration.group.groupPackages(packages);

        if (!groupPackageMap.keySet().isEmpty()) {
            Table table =  new Table(HtmlStyle.overviewSummary)
                    .setHeader(getPackageTableHeader())
                    .setColumnStyles(HtmlStyle.colFirst, HtmlStyle.colLast)
                    .setDefaultTab(resources.getText("doclet.All_Packages"))
                    .setTabScript(i -> "show(" + i + ");")
                    .setTabId(i -> (i == 0) ? "t0" : ("t" + (1 << (i - 1))));

            // add the tabs in command-line order
            for (String groupName : configuration.group.getGroupList()) {
                Set<PackageElement> groupPackages = groupPackageMap.get(groupName);
                if (groupPackages != null) {
                    table.addTab(groupName, groupPackages::contains);
                }
            }

            for (PackageElement pkg : configuration.packages) {
                if (!pkg.isUnnamed()) {
                    if (!(configuration.nodeprecated && utils.isDeprecated(pkg))) {
                        Content packageLinkContent = getPackageLink(pkg, getPackageName(pkg));
                        Content summaryContent = new ContentBuilder();
                        addSummaryComment(pkg, summaryContent);
                        table.addRow(pkg, packageLinkContent, summaryContent);
                    }
                }
            }

            Content div = HtmlTree.DIV(HtmlStyle.contentContainer, table.toContent());
            main.addContent(div);

            if (table.needsScript()) {
                getMainBodyScript().append(table.getScript());
            }
        }
    }

    /**
     * Adds the overview summary comment for this documentation. Add one line
     * summary at the top of the page and generate a link to the description,
     * which is added at the end of this page.
     *
     * @param main the documentation tree to which the overview header will be added
     */
    @Override
    protected void addOverviewHeader(Content main) {
        addConfigurationTitle(main);
        if (!utils.getFullBody(configuration.overviewElement).isEmpty()) {
            HtmlTree div = new HtmlTree(HtmlTag.DIV);
            div.setStyle(HtmlStyle.contentContainer);
            addOverviewComment(div);
            main.addContent(div);
        }
    }

    /**
     * Adds the overview comment as provided in the file specified by the
     * "-overview" option on the command line.
     *
     * @param htmltree the documentation tree to which the overview comment will
     *                 be added
     */
    protected void addOverviewComment(Content htmltree) {
        if (!utils.getFullBody(configuration.overviewElement).isEmpty()) {
            addInlineComment(configuration.overviewElement, htmltree);
        }
    }

    /**
     * Adds the top text (from the -top option), the upper
     * navigation bar, and then the title (from the"-title"
     * option), at the top of page.
     *
     * @param header the documentation tree to which the navigation bar header will be added
     */
    @Override
    protected void addNavigationBarHeader(Content header) {
        addTop(header);
        navBar.setUserHeader(getUserHeaderFooter(true));
        header.addContent(navBar.getContent(true));
    }

    /**
     * Adds the lower navigation bar and the bottom text
     * (from the -bottom option) at the bottom of page.
     *
     * @param footer the documentation tree to which the navigation bar footer will be added
     */
    @Override
    protected void addNavigationBarFooter(Content footer) {
        navBar.setUserFooter(getUserHeaderFooter(false));
        footer.addContent(navBar.getContent(false));
        addBottom(footer);
    }
}
