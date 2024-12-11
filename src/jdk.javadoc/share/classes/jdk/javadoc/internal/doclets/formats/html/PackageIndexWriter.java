/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import javax.lang.model.element.PackageElement;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyles;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.Group;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.ContentBuilder;
import jdk.javadoc.internal.html.Text;

/**
 * Generate the package index page "index.html".
 */
public class PackageIndexWriter extends AbstractOverviewIndexWriter {

    /**
     * The Set of Packages to be documented.
     */
    protected SortedSet<PackageElement> packages;

    /**
     * Construct the PackageIndexWriter. Also constructs the grouping
     * information as provided on the command line by "-group" option. Stores
     * the order of groups specified by the user.
     *
     * @param configuration the configuration for this doclet
     * @see Group
     */
    public PackageIndexWriter(HtmlConfiguration configuration) {
        super(configuration, DocPaths.INDEX);
        packages = configuration.packages;
    }

    @Override
    public String getDescription() {
        return "package index";
    }

    @Override
    public String getTitleKey() {
        return "doclet.Window_Overview_Summary";
    }

    /**
     * Adds the packages list to the documentation tree.
     *
     * @param target the content to which the packages list will be added
     */
    @Override
    protected void addIndex(Content target) {
        Map<String, SortedSet<PackageElement>> groupPackageMap
                = configuration.group.groupPackages(packages);

        if (!groupPackageMap.keySet().isEmpty()) {
            var table = new Table<PackageElement>(HtmlStyles.summaryTable)
                    .setHeader(getPackageTableHeader())
                    .setColumnStyles(HtmlStyles.colFirst, HtmlStyles.colLast)
                    .setId(HtmlIds.ALL_PACKAGES_TABLE)
                    .setDefaultTab(contents.getContent("doclet.All_Packages"));

            // add the tabs in command-line order
            for (String groupName : configuration.group.getGroupList()) {
                Set<PackageElement> groupPackages = groupPackageMap.get(groupName);
                if (groupPackages != null) {
                    table.addTab(Text.of(groupName), groupPackages::contains);
                }
            }

            for (PackageElement pkg : configuration.packages) {
                if (!(options.noDeprecated() && utils.isDeprecated(pkg))) {
                    Content packageLinkContent = getPackageLink(pkg, getLocalizedPackageName(pkg));
                    Content summaryContent = new ContentBuilder();
                    addSummaryComment(pkg, summaryContent);
                    table.addRow(pkg, packageLinkContent, summaryContent);
                }
            }

            target.add(table);
        }
    }
}
