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
import java.util.*;

import com.sun.javadoc.*;
import com.sun.tools.javac.jvm.Profile;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

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
     * Root of the program structure. Used for "overview" documentation.
     */
    private RootDoc root;

    /**
     * Map representing the group of packages as specified on the command line.
     *
     * @see Group
     */
    private Map<String,List<PackageDoc>> groupPackageMap;

    /**
     * List to store the order groups as specified on the command line.
     */
    private List<String> groupList;

    /**
     * Construct the PackageIndexWriter. Also constructs the grouping
     * information as provided on the command line by "-group" option. Stores
     * the order of groups specified by the user.
     *
     * @see Group
     */
    public PackageIndexWriter(ConfigurationImpl configuration,
                              DocPath filename)
                       throws IOException {
        super(configuration, filename);
        this.root = configuration.root;
        groupPackageMap = configuration.group.groupPackages(packages);
        groupList = configuration.group.getGroupList();
    }

    /**
     * Generate the package index page for the right-hand frame.
     *
     * @param configuration the current configuration of the doclet.
     */
    public static void generate(ConfigurationImpl configuration) {
        PackageIndexWriter packgen;
        DocPath filename = DocPaths.OVERVIEW_SUMMARY;
        try {
            packgen = new PackageIndexWriter(configuration, filename);
            packgen.buildPackageIndexFile("doclet.Window_Overview_Summary", true);
            packgen.close();
        } catch (IOException exc) {
            configuration.standardmessage.error(
                        "doclet.exception_encountered",
                        exc.toString(), filename);
            throw new DocletAbortException();
        }
    }

    /**
     * Depending upon the grouping information and their titles, add
     * separate table indices for each package group.
     *
     * @param body the documentation tree to which the index will be added
     */
    protected void addIndex(Content body) {
        for (int i = 0; i < groupList.size(); i++) {
        String groupname = groupList.get(i);
        List<PackageDoc> list = groupPackageMap.get(groupname);
            if (list != null && list.size() > 0) {
                addIndexContents(list.toArray(new PackageDoc[list.size()]),
                        groupname, configuration.getText("doclet.Member_Table_Summary",
                        groupname, configuration.getText("doclet.packages")), body);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    protected void addProfilesList(String profileSummary, String profilesTableSummary,
            Content body) {
        Content table = HtmlTree.TABLE(HtmlStyle.overviewSummary, 0, 3, 0, profilesTableSummary,
                getTableCaption(profileSummary));
        table.addContent(getSummaryTableHeader(profileTableHeader, "col"));
        Content tbody = new HtmlTree(HtmlTag.TBODY);
        addProfilesList(tbody);
        table.addContent(tbody);
        Content div = HtmlTree.DIV(HtmlStyle.contentContainer, table);
        body.addContent(div);
    }

    /**
     * {@inheritDoc}
     */
    protected void addPackagesList(PackageDoc[] packages, String text,
            String tableSummary, Content body) {
        Content table = HtmlTree.TABLE(HtmlStyle.overviewSummary, 0, 3, 0, tableSummary,
                getTableCaption(text));
        table.addContent(getSummaryTableHeader(packageTableHeader, "col"));
        Content tbody = new HtmlTree(HtmlTag.TBODY);
        addPackagesList(packages, tbody);
        table.addContent(tbody);
        Content div = HtmlTree.DIV(HtmlStyle.contentContainer, table);
        body.addContent(div);
    }

    /**
     * Adds list of profiles in the index table. Generate link to each profile.
     *
     * @param tbody the documentation tree to which the list will be added
     */
    protected void addProfilesList(Content tbody) {
        for (int i = 1; i < configuration.profiles.getProfileCount(); i++) {
            String profileName = Profile.lookup(i).name;
            Content profileLinkContent = getTargetProfileLink("classFrame",
                    new StringContent(profileName), profileName);
            Content tdProfile = HtmlTree.TD(HtmlStyle.colFirst, profileLinkContent);
            HtmlTree tdSummary = new HtmlTree(HtmlTag.TD);
            tdSummary.addStyle(HtmlStyle.colLast);
            tdSummary.addContent(getSpace());
            HtmlTree tr = HtmlTree.TR(tdProfile);
            tr.addContent(tdSummary);
            if (i % 2 == 0) {
                tr.addStyle(HtmlStyle.altColor);
            } else {
                tr.addStyle(HtmlStyle.rowColor);
            }
            tbody.addContent(tr);
        }
    }

    /**
     * Adds list of packages in the index table. Generate link to each package.
     *
     * @param packages Packages to which link is to be generated
     * @param tbody the documentation tree to which the list will be added
     */
    protected void addPackagesList(PackageDoc[] packages, Content tbody) {
        for (int i = 0; i < packages.length; i++) {
            if (packages[i] != null && packages[i].name().length() > 0) {
                if (configuration.nodeprecated && Util.isDeprecated(packages[i]))
                    continue;
                Content packageLinkContent = getPackageLink(packages[i],
                        getPackageName(packages[i]));
                Content tdPackage = HtmlTree.TD(HtmlStyle.colFirst, packageLinkContent);
                HtmlTree tdSummary = new HtmlTree(HtmlTag.TD);
                tdSummary.addStyle(HtmlStyle.colLast);
                addSummaryComment(packages[i], tdSummary);
                HtmlTree tr = HtmlTree.TR(tdPackage);
                tr.addContent(tdSummary);
                if (i%2 == 0)
                    tr.addStyle(HtmlStyle.altColor);
                else
                    tr.addStyle(HtmlStyle.rowColor);
                tbody.addContent(tr);
            }
        }
    }

    /**
     * Adds the overview summary comment for this documentation. Add one line
     * summary at the top of the page and generate a link to the description,
     * which is added at the end of this page.
     *
     * @param body the documentation tree to which the overview header will be added
     */
    protected void addOverviewHeader(Content body) {
        if (root.inlineTags().length > 0) {
            HtmlTree subTitleDiv = new HtmlTree(HtmlTag.DIV);
            subTitleDiv.addStyle(HtmlStyle.subTitle);
            addSummaryComment(root, subTitleDiv);
            Content div = HtmlTree.DIV(HtmlStyle.header, subTitleDiv);
            Content see = seeLabel;
            see.addContent(" ");
            Content descPara = HtmlTree.P(see);
            Content descLink = getHyperLink(DocLink.fragment("overview_description"),
                descriptionLabel, "", "");
            descPara.addContent(descLink);
            div.addContent(descPara);
            body.addContent(div);
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
        if (root.inlineTags().length > 0) {
            htmltree.addContent(getMarkerAnchor("overview_description"));
            addInlineComment(root, htmltree);
        }
    }

    /**
     * Adds the tag information as provided in the file specified by the
     * "-overview" option on the command line.
     *
     * @param body the documentation tree to which the overview will be added
     */
    protected void addOverview(Content body) throws IOException {
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.addStyle(HtmlStyle.contentContainer);
        addOverviewComment(div);
        addTagsInfo(root, div);
        body.addContent(div);
    }

    /**
     * Adds the top text (from the -top option), the upper
     * navigation bar, and then the title (from the"-title"
     * option), at the top of page.
     *
     * @body the documentation tree to which the navigation bar header will be added
     */
    protected void addNavigationBarHeader(Content body) {
        addTop(body);
        addNavLinks(true, body);
        addConfigurationTitle(body);
    }

    /**
     * Adds the lower navigation bar and the bottom text
     * (from the -bottom option) at the bottom of page.
     *
     * @param body the documentation tree to which the navigation bar footer will be added
     */
    protected void addNavigationBarFooter(Content body) {
        addNavLinks(false, body);
        addBottom(body);
    }
}
