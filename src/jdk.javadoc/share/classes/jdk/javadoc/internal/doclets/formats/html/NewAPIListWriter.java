/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.source.doctree.SinceTree;

import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyles;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.NewAPIBuilder;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.HtmlStyle;
import jdk.javadoc.internal.html.HtmlTree;
import jdk.javadoc.internal.html.Text;

import static com.sun.source.doctree.DocTree.Kind.SINCE;

/**
 * Generates a file containing a list of new API elements with the appropriate links.
 */
public class NewAPIListWriter extends SummaryListWriter<NewAPIBuilder> {

    /**
     * Constructor.
     *
     * @param configuration the configuration for this doclet
     */
    public NewAPIListWriter(HtmlConfiguration configuration) {
        super(configuration, DocPaths.NEW_LIST, configuration.newAPIPageBuilder);
    }

    @Override
    protected PageMode getPageMode() {
        return PageMode.NEW;
    }

    @Override
    protected String getDescription() {
        return "new elements";
    }

    @Override
    protected Content getHeadContent() {
        return Text.of(getHeading(configuration));
    }

    @Override
    protected String getTitleKey() {
        return "doclet.Window_New_List";
    }

    @Override
    protected void addContentSelectors(Content content) {
        List<String> releases = builder.releases;
        if (releases.size() > 1) {
            Content tabs = HtmlTree.DIV(HtmlStyles.checkboxes,
                    contents.getContent("doclet.New_API_Checkbox_Label"));
            // Table column ids are 1-based
            int index = 1;
            for (String release : releases) {
                tabs.add(Text.of(" ")).add(getCheckbox(Text.of(release), String.valueOf(index++), "release-"));
            }
            Content label = contents.getContent("doclet.New_API_Checkbox_All_Releases");
            tabs.add(Text.of(" ")).add(getCheckbox(label, ID_ALL, "release-"));
            content.add(tabs);
        }
    }

    @Override
    protected void addTableTabs(Table<Element> table, String headingKey) {
        table.setGridStyle(HtmlStyles.threeColumnReleaseSummary);
        List<String> releases = builder.releases;
        if (releases.size() > 1) {
            table.setDefaultTab(getTableCaption(headingKey))
                    .setRenderTabs(false);
            for (String release : releases) {
                table.addTab(
                        releases.size() == 1
                                ? getTableCaption(headingKey)
                                : Text.of(release),
                        element -> {
                            List<? extends SinceTree> since = utils.getBlockTags(element, SINCE, SinceTree.class);
                            if (since.isEmpty()) {
                                return false;
                            }
                            CommentHelper ch = utils.getCommentHelper(element);
                            return since.stream().anyMatch(tree -> release.equals(ch.getBody(tree).toString()));
                        });
            }
        }
    }

    @Override
    protected void addComments(Element e, Content desc) {
        addSummaryComment(e, desc);
    }

    @Override
    protected Content getTableCaption(String headingKey) {
        return contents.getContent("doclet.New_Elements", super.getTableCaption(headingKey));
    }

    @Override
    protected Content getExtraContent(Element element) {
        var sinceTrees = utils.getBlockTags(element, SINCE, SinceTree.class);
        if (!sinceTrees.isEmpty()) {
            // assumes a simple string value with no formatting
            return Text.of(sinceTrees.getFirst().getBody().getFirst().toString());
        }
        return Text.EMPTY;
    }

    @Override
    protected TableHeader getTableHeader(String headerKey) {
        return new TableHeader(
                contents.getContent(headerKey),
                contents.getContent("doclet.New_Elements_Release_Column_Header"),
                contents.descriptionLabel)
                .sortable(true, true, false); // Allow sorting by element name and release
    }

    @Override
    protected HtmlStyle[] getColumnStyles() {
        return new HtmlStyle[]{ HtmlStyles.colSummaryItemName, HtmlStyles.colSecond, HtmlStyles.colLast };
    }

    private static String getHeading(HtmlConfiguration configuration) {
        String label = configuration.getOptions().sinceLabel();
        return label == null ? configuration.docResources.getText("doclet.New_API") : label;
    }
}
