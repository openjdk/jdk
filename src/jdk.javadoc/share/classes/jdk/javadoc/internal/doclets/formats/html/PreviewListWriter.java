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
import java.util.Set;

import javax.lang.model.element.Element;

import com.sun.source.doctree.DocTree;

import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlAttr;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlId;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Text;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.PreviewAPIListBuilder;

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
            int index = 0;
            target.add(HtmlTree.P(contents.getContent("doclet.Preview_API_Checkbox_Label")));
            Content list = HtmlTree.UL(HtmlStyle.previewFeatureList);
            for (var jep : jeps) {
                index++;
                HtmlId htmlId = HtmlId.of("feature-" + index);
                String jepUrl = resources.getText("doclet.Preview_JEP_URL", jep.number());
                list.add(HtmlTree.LI(HtmlTree.LABEL(htmlId.name(),
                                HtmlTree.INPUT(HtmlAttr.InputType.CHECKBOX, htmlId)
                                        .put(HtmlAttr.CHECKED, "")
                                        .put(HtmlAttr.ONCLICK,
                                                "toggleGlobal(this, '" + index + "', 3)"))
                        .add(HtmlTree.SPAN(Text.of(jep.number() + ": "))
                                .add(HtmlTree.A(jepUrl, Text.of(jep.title() + " (" + jep.status() + ")"))))));
            }
            target.add(list);
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
        table.setGridStyle(HtmlStyle.threeColumnSummary)
                .setDefaultTab(getTableCaption(headingKey))
                .setAlwaysShowDefaultTab(true)
                .setRenderTabs(false);
        for (PreviewAPIListBuilder.JEP jep : builder.getJEPs()) {
            table.addTab(Text.EMPTY, element -> jep == builder.getJEP(element));
        }
    }

    @Override
    protected Content getExtraContent(Element element) {
        PreviewAPIListBuilder.JEP jep = configuration.previewAPIListBuilder.getJEP(element);
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
        return new HtmlStyle[]{ HtmlStyle.colSummaryItemName, HtmlStyle.colSecond, HtmlStyle.colLast };
    }
}
