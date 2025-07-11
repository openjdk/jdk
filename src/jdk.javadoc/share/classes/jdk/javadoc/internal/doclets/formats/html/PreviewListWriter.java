/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Set;

import javax.lang.model.element.Element;

import com.sun.source.doctree.DocTree;

import com.sun.source.doctree.UnknownBlockTagTree;
import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyles;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.PreviewAPIListBuilder;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.ContentBuilder;
import jdk.javadoc.internal.html.HtmlId;
import jdk.javadoc.internal.html.HtmlStyle;
import jdk.javadoc.internal.html.HtmlTree;
import jdk.javadoc.internal.html.Text;

/**
 * Generate File to list all the preview elements with the
 * appropriate links.
 */
public class PreviewListWriter extends SummaryListWriter<PreviewAPIListBuilder> {

    /**
     * Constructor.
     *
     * @param configuration the configuration for this doclet
     */
    public PreviewListWriter(HtmlConfiguration configuration) {
        super(configuration, DocPaths.PREVIEW_LIST, configuration.previewAPIListBuilder);
    }

    @Override
    protected PageMode getPageMode() {
        return PageMode.PREVIEW;
    }

    @Override
    protected String getDescription() {
        return "preview elements";
    }

    @Override
    protected Content getHeadContent() {
        return configuration.contents.previewAPI;
    }

    @Override
    protected String getTitleKey() {
        return "doclet.Window_Preview_List";
    }

    @Override
    protected void addContentSelectors(Content target) {
        Set<PreviewAPIListBuilder.JEP> jeps = builder.getJEPs();
        if (!jeps.isEmpty()) {
            int index = 1;
            target.add(HtmlTree.P(contents.getContent("doclet.Preview_API_Checkbox_Label")));
            Content list = HtmlTree.UL(HtmlStyles.previewFeatureList).addStyle(HtmlStyles.checkboxes);
            for (var jep : jeps) {
                Content label;
                if (jep.number() != 0) {
                    String jepUrl = resources.getText("doclet.Preview_JEP_URL", String.valueOf(jep.number()));
                    label = new ContentBuilder(Text.of(jep.number() + ": "))
                            .add(HtmlTree.A(jepUrl, Text.of(jep.title() + " (" + jep.status() + ")")));
                } else {
                    // Pseudo-JEP created from javadoc tag - use description as label
                    label = Text.of(jep.title());
                }
                list.add(HtmlTree.LI(getCheckbox(label, String.valueOf(index++), "feature-")));
            }
            Content label = contents.getContent("doclet.Preview_API_Checkbox_Toggle_All");
            list.add(HtmlTree.LI(getCheckbox(label, ID_ALL, "feature-")));
            target.add(list);
        }
    }

    @Override
    protected List<Content> getIndexLinks() {
        var list = super.getIndexLinks();
        var notes = builder.getElementNotes();
        if (!notes.isEmpty()) {
            list.add(getIndexLink(HtmlId.of("preview-api-notes"), "doclet.Preview_Notes"));
        }
        return list;
    }

    @Override
    protected void addSummaries(Content content) {
        var notes = builder.getElementNotes();
        super.addSummaries(content);
        // Add permanent APIs with preview notes below preview API tables
        if (!notes.isEmpty()) {
            addSummaryAPI(notes, HtmlId.of("preview-api-notes"),
                    "doclet.Preview_Notes", "doclet.Element", content);
        }
    }

    @Override
    protected void addComments(Element e, Content desc) {
        List<? extends DocTree> tags = utils.getFirstSentenceTrees(e);
        if (!tags.isEmpty()) {
            addPreviewComment(e, tags, desc);
        } else {
            desc.add(Text.EMPTY);
        }
    }

    @Override
    protected void addTableTabs(Table<Element> table, String headingKey) {
        table.setGridStyle(HtmlStyles.threeColumnSummary)
                .setDefaultTab(getTableCaption(headingKey))
                .setRenderTabs(false);
        for (PreviewAPIListBuilder.JEP jep : builder.getJEPs()) {
            table.addTab(Text.EMPTY, element -> jep.equals(builder.getJEP(element)));
        }
    }

    @Override
    protected Content getExtraContent(Element element) {
        PreviewAPIListBuilder.JEP jep = builder.getJEP(element);
        return jep == null ? Text.EMPTY : Text.of(jep.title());
    }

    @Override
    protected TableHeader getTableHeader(String headerKey) {
        return new TableHeader(
                contents.getContent(headerKey),
                Text.of("Preview Feature"),
                contents.descriptionLabel)
                .sortable(true, true, false); // Allow sorting by element name and feature
    }

    @Override
    protected HtmlStyle[] getColumnStyles() {
        return new HtmlStyle[]{ HtmlStyles.colSummaryItemName, HtmlStyles.colSecond, HtmlStyles.colLast };
    }
}
