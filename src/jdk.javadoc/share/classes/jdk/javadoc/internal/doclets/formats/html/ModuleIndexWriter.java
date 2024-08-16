/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.element.ModuleElement;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyles;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.ContentBuilder;
import jdk.javadoc.internal.html.Text;

/**
 * Generate the module index page "index.html".
 */
public class ModuleIndexWriter extends AbstractOverviewIndexWriter {

    /**
     * Modules to be documented.
     */
    protected SortedSet<ModuleElement> modules;

    /**
     * Construct the ModuleIndexWriter.
     *
     * @param configuration the configuration object
     */
    public ModuleIndexWriter(HtmlConfiguration configuration) {
        super(configuration, DocPaths.INDEX);
        modules = configuration.modules;
    }

    @Override
    public String getDescription() {
        return "module index";
    }

    @Override
    public String getTitleKey() {
        return "doclet.Window_Overview_Summary";
    }

    /**
     * Adds the list of modules.
     *
     * @param target the content to which the modules list will be added
     */
    @Override
    protected void addIndex(Content target) {
        Map<String, SortedSet<ModuleElement>> groupModuleMap
                = configuration.group.groupModules(modules);

        if (!groupModuleMap.keySet().isEmpty()) {
            TableHeader tableHeader = new TableHeader(contents.moduleLabel, contents.descriptionLabel);
            var table = new Table<ModuleElement>(HtmlStyles.summaryTable)
                    .setHeader(tableHeader)
                    .setColumnStyles(HtmlStyles.colFirst, HtmlStyles.colLast)
                    .setId(HtmlIds.ALL_MODULES_TABLE)
                    .setDefaultTab(contents.getContent("doclet.All_Modules"));

            // add the tabs in command-line order
            for (String groupName : configuration.group.getGroupList()) {
                Set<ModuleElement> groupModules = groupModuleMap.get(groupName);
                if (groupModules != null) {
                    table.addTab(Text.of(groupName), groupModules::contains);
                }
            }

            for (ModuleElement mdle : modules) {
                if (!mdle.isUnnamed()) {
                    if (!(options.noDeprecated() && utils.isDeprecated(mdle))) {
                        Content moduleLinkContent = getModuleLink(mdle, Text.of(mdle.getQualifiedName().toString()));
                        Content summaryContent = new ContentBuilder();
                        addPreviewSummary(mdle, summaryContent);
                        addSummaryComment(mdle, summaryContent);
                        table.addRow(mdle, moduleLinkContent, summaryContent);
                    }
                }
            }

            target.add(table);
        }
    }
}
