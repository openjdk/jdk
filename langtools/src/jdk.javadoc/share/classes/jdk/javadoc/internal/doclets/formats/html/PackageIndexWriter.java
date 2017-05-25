/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import javax.lang.model.element.PackageElement;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlConstants;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.RawHtml;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
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
     * Map representing the group of packages as specified on the command line.
     *
     * @see Group
     */
    private final Map<String, SortedSet<PackageElement>> groupPackageMap;

    /**
     * List to store the order groups as specified on the command line.
     */
    private final List<String> groupList;

    /**
     * HTML tree for main tag.
     */
    private final HtmlTree htmlTree = HtmlTree.MAIN();

    /**
     * Construct the PackageIndexWriter. Also constructs the grouping
     * information as provided on the command line by "-group" option. Stores
     * the order of groups specified by the user.
     *
     * @param configuration the configuration for this doclet
     * @param filename the path of the page to be generated
     * @see Group
     */
    public PackageIndexWriter(ConfigurationImpl configuration, DocPath filename) {
        super(configuration, filename);
        groupPackageMap = configuration.group.groupPackages(packages);
        groupList = configuration.group.getGroupList();
    }

    /**
     * Generate the package index page for the right-hand frame.
     *
     * @param configuration the current configuration of the doclet.
     * @throws DocFileIOException if there is a problem generating the package index page
     */
    public static void generate(ConfigurationImpl configuration) throws DocFileIOException {
        DocPath filename = DocPaths.overviewSummary(configuration.frames);
        PackageIndexWriter packgen = new PackageIndexWriter(configuration, filename);
        packgen.buildPackageIndexFile("doclet.Window_Overview_Summary", true);
    }

    /**
     * Depending upon the grouping information and their titles, add
     * separate table indices for each package group.
     *
     * @param body the documentation tree to which the index will be added
     */
    @Override
    protected void addIndex(Content body) {
        for (String groupname : groupList) {
            SortedSet<PackageElement> list = groupPackageMap.get(groupname);
            if (list != null && !list.isEmpty()) {
                addIndexContents(list,
                        groupname, configuration.getText("doclet.Member_Table_Summary",
                                groupname, configuration.getText("doclet.packages")), body);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addPackagesList(Collection<PackageElement> packages, String text,
            String tableSummary, Content body) {
        Content table = (configuration.isOutputHtml5())
                ? HtmlTree.TABLE(HtmlStyle.overviewSummary, getTableCaption(new RawHtml(text)))
                : HtmlTree.TABLE(HtmlStyle.overviewSummary, tableSummary, getTableCaption(new RawHtml(text)));
        table.addContent(getSummaryTableHeader(packageTableHeader, "col"));
        Content tbody = new HtmlTree(HtmlTag.TBODY);
        addPackagesList(packages, tbody);
        table.addContent(tbody);
        Content anchor = getMarkerAnchor(text);
        Content div = HtmlTree.DIV(HtmlStyle.contentContainer, anchor);
        div.addContent(table);
        if (configuration.allowTag(HtmlTag.MAIN)) {
            htmlTree.addContent(div);
        } else {
            body.addContent(div);
        }
    }

    /**
     * Adds list of packages in the index table. Generate link to each package.
     *
     * @param packages Packages to which link is to be generated
     * @param tbody the documentation tree to which the list will be added
     */
    protected void addPackagesList(Collection<PackageElement> packages, Content tbody) {
        boolean altColor = true;
        for (PackageElement pkg : packages) {
            if (!pkg.isUnnamed()) {
                if (!(configuration.nodeprecated && utils.isDeprecated(pkg))) {
                    Content packageLinkContent = getPackageLink(pkg, getPackageName(pkg));
                    Content thPackage = HtmlTree.TH_ROW_SCOPE(HtmlStyle.colFirst, packageLinkContent);
                    HtmlTree tdSummary = new HtmlTree(HtmlTag.TD);
                    tdSummary.addStyle(HtmlStyle.colLast);
                    addSummaryComment(pkg, tdSummary);
                    HtmlTree tr = HtmlTree.TR(thPackage);
                    tr.addContent(tdSummary);
                    tr.addStyle(altColor ? HtmlStyle.altColor : HtmlStyle.rowColor);
                    tbody.addContent(tr);
                }
            }
            altColor = !altColor;
        }
    }

    /**
     * Adds the overview summary comment for this documentation. Add one line
     * summary at the top of the page and generate a link to the description,
     * which is added at the end of this page.
     *
     * @param body the documentation tree to which the overview header will be added
     */
    @Override
    protected void addOverviewHeader(Content body) {
        addConfigurationTitle(body);
        if (!utils.getFullBody(configuration.overviewElement).isEmpty()) {
            HtmlTree div = new HtmlTree(HtmlTag.DIV);
            div.addStyle(HtmlStyle.contentContainer);
            addOverviewComment(div);
            if (configuration.allowTag(HtmlTag.MAIN)) {
                htmlTree.addContent(div);
            } else {
                body.addContent(div);
            }
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
     * For HTML 5, add the htmlTree to the body. For HTML 4, do nothing.
     *
     * @param body the documentation tree to which the overview will be added
     */
    @Override
    protected void addOverview(Content body) {
        if (configuration.allowTag(HtmlTag.MAIN)) {
            body.addContent(htmlTree);
        }
    }

    /**
     * Adds the top text (from the -top option), the upper
     * navigation bar, and then the title (from the"-title"
     * option), at the top of page.
     *
     * @param body the documentation tree to which the navigation bar header will be added
     */
    @Override
    protected void addNavigationBarHeader(Content body) {
        Content htmlTree = (configuration.allowTag(HtmlTag.HEADER))
                ? HtmlTree.HEADER()
                : body;
        addTop(htmlTree);
        addNavLinks(true, htmlTree);
        if (configuration.allowTag(HtmlTag.HEADER)) {
            body.addContent(htmlTree);
        }
    }

    /**
     * Adds the lower navigation bar and the bottom text
     * (from the -bottom option) at the bottom of page.
     *
     * @param body the documentation tree to which the navigation bar footer will be added
     */
    @Override
    protected void addNavigationBarFooter(Content body) {
        Content htmlTree = (configuration.allowTag(HtmlTag.FOOTER))
                ? HtmlTree.FOOTER()
                : body;
        addNavLinks(false, htmlTree);
        addBottom(htmlTree);
        if (configuration.allowTag(HtmlTag.FOOTER)) {
            body.addContent(htmlTree);
        }
    }
}
