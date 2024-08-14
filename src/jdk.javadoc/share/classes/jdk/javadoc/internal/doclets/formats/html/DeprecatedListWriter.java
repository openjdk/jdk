/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import javax.lang.model.element.Element;

import com.sun.source.doctree.DeprecatedTree;

import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyles;
import jdk.javadoc.internal.doclets.toolkit.util.DeprecatedAPIListBuilder;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.HtmlStyle;
import jdk.javadoc.internal.html.HtmlTree;
import jdk.javadoc.internal.html.Text;

/**
 * Generate File to list all the deprecated classes and class members with the
 * appropriate links.
 */
public class DeprecatedListWriter extends SummaryListWriter<DeprecatedAPIListBuilder> {

    private static final String TERMINALLY_DEPRECATED_KEY = "doclet.Terminally_Deprecated_Elements";

    /**
     * Constructor.
     *
     * @param configuration the configuration for this doclet
     */
    public DeprecatedListWriter(HtmlConfiguration configuration) {
        super(configuration, DocPaths.DEPRECATED_LIST, configuration.deprecatedAPIListBuilder);
    }

    @Override
    protected PageMode getPageMode() {
        return PageMode.DEPRECATED;
    }

    @Override
    protected String getDescription() {
        return "deprecated elements";
    }

    @Override
    protected Content getHeadContent() {
        return configuration.contents.deprecatedAPI;
    }

    @Override
    protected String getTitleKey() {
        return "doclet.Window_Deprecated_List";
    }

    @Override
    protected void addContentSelectors(Content target) {
        List<String> releases = builder.releases;
        if (releases.size() > 1) {
            Content tabs = HtmlTree.DIV(HtmlStyles.checkboxes, contents.getContent(
                    "doclet.Deprecated_API_Checkbox_Label"));
            // Table column ids are 1-based
            int index = 1;
            for (String release : releases) {
                // Empty string represents other/uncategorized releases. Since we can't make any assumptions
                // about release names this is arguably the safest way to avoid naming collisions.
                Content label = !release.isEmpty()
                        ? Text.of(release)
                        : contents.getContent("doclet.Deprecated_API_Checkbox_Other_Releases");
                String id = release.isEmpty() ? ID_OTHER : String.valueOf(index++);
                tabs.add(Text.of(" ")).add(getCheckbox(label, id, "release-"));
            }
            tabs.add(Text.of(" ")).add(getCheckbox(
                    contents.getContent("doclet.Deprecated_API_Checkbox_All_Releases"), ID_ALL, "release-"));
            target.add(tabs);
        }
    }

    @Override
    protected void addExtraSection(Content content) {
        addSummaryAPI(builder.getForRemoval(), HtmlIds.FOR_REMOVAL,
                TERMINALLY_DEPRECATED_KEY, "doclet.Element", content);
    }

    @Override
    protected void addExtraIndexLink(Content target) {
        if (!builder.getForRemoval().isEmpty()) {
            addIndexLink(HtmlIds.FOR_REMOVAL, "doclet.Terminally_Deprecated", target);
        }
    }

    @Override
    protected void addComments(Element e, Content desc) {
        List<? extends DeprecatedTree> tags = utils.getDeprecatedTrees(e);
        if (!tags.isEmpty()) {
            addInlineComment(e, tags.get(0), desc);
        } else {
            desc.add(Text.EMPTY);
        }
    }

    @Override
    protected void addTableTabs(Table<Element> table, String headingKey) {
        List<String> releases = builder.releases;
        if (!releases.isEmpty()) {
            table.setGridStyle(HtmlStyles.threeColumnReleaseSummary);
        }
        if (releases.size() > 1) {
            table.setDefaultTab(getTableCaption(headingKey))
                    .setRenderTabs(false);
            for (String release : releases) {
                Content tab = TERMINALLY_DEPRECATED_KEY.equals(headingKey)
                        ? contents.getContent("doclet.Terminally_Deprecated_In_Release", release)
                        : contents.getContent("doclet.Deprecated_In_Release", release);
                table.addTab(tab,
                        element -> release.equals(utils.getDeprecatedSince(element)));
            }
        }
    }

    @Override
    protected Content getExtraContent(Element element) {
        List<String> releases = builder.releases;
        if (releases.isEmpty()) {
            return null;
        }
        String deprecatedSince = utils.getDeprecatedSince(element);
        return deprecatedSince == null || deprecatedSince.isEmpty()
                ? Text.EMPTY : Text.of(deprecatedSince);
    }

    @Override
    protected TableHeader getTableHeader(String headerKey) {
        List<String> releases = builder.releases;
        if (releases.isEmpty()) {
            return super.getTableHeader(headerKey);
        }
        return new TableHeader(
                contents.getContent(headerKey),
                contents.getContent("doclet.Deprecated_Elements_Release_Column_Header"),
                contents.descriptionLabel)
                .sortable(true, true, false); // Allow sorting by element name and release
    }

    @Override
    protected HtmlStyle[] getColumnStyles() {
        List<String> releases = builder.releases;
        if (releases.isEmpty()) {
            return super.getColumnStyles();
        }
        return new HtmlStyle[]{ HtmlStyles.colSummaryItemName, HtmlStyles.colSecond, HtmlStyles.colLast };
    }

    @Override
    protected Content getTableCaption(String headingKey) {
        Content caption = contents.getContent(headingKey);
        return TERMINALLY_DEPRECATED_KEY.equals(headingKey)
                ? caption : contents.getContent("doclet.Deprecated_Elements", caption);
    }
}
