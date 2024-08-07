/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

import com.sun.source.doctree.DocTree;

import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.BodyContents;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyles;
import jdk.javadoc.internal.doclets.toolkit.DocFileElement;
import jdk.javadoc.internal.doclets.toolkit.OverviewElement;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.IndexItem;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.ContentBuilder;
import jdk.javadoc.internal.html.HtmlTree;
import jdk.javadoc.internal.html.Text;

import static java.util.stream.Collectors.groupingBy;

/**
 * Generates the file with the summary of all the system properties.
 */
public class SystemPropertiesWriter extends HtmlDocletWriter {

    /**
     * Cached contents of {@code <title>...</title>} tags of the HTML pages.
     */
    final Map<DocFileElement, String> titles = new WeakHashMap<>();

    /**
     * Constructs SystemPropertiesWriter object.
     *
     * @param configuration The current configuration
     */
    public SystemPropertiesWriter(HtmlConfiguration configuration) {
        super(configuration, DocPaths.SYSTEM_PROPERTIES, false);
    }

    @Override
    public void buildPage() throws DocFileIOException {
        boolean hasSystemProperties = configuration.indexBuilder != null
                && !configuration.indexBuilder.getItems(DocTree.Kind.SYSTEM_PROPERTY).isEmpty();
        if (!hasSystemProperties) {
            return;
        }

        writeGenerating();
        configuration.conditionalPages.add(HtmlConfiguration.ConditionalPage.SYSTEM_PROPERTIES);

        String title = resources.getText("doclet.systemProperties");
        HtmlTree body = getBody(getWindowTitle(title));
        Content mainContent = new ContentBuilder();
        addSystemProperties(mainContent);
        body.add(new BodyContents()
                .setHeader(getHeader(PageMode.SYSTEM_PROPERTIES))
                .addMainContent(HtmlTree.DIV(HtmlStyles.header,
                        HtmlTree.HEADING(Headings.PAGE_TITLE_HEADING,
                                contents.getContent("doclet.systemProperties"))))
                .addMainContent(mainContent)
                .setFooter(getFooter()));
        printHtmlDocument(null, "system properties", body);

        if (configuration.indexBuilder != null) {
            configuration.indexBuilder.add(IndexItem.of(IndexItem.Category.TAGS, title, path));
        }
    }

    /**
     * Adds all the system properties to the content.
     *
     * @param target the content to which the links will be added
     */
    protected void addSystemProperties(Content target) {
        Map<String, List<IndexItem>> searchIndexMap = groupSystemProperties();
        Content separator = Text.of(", ");
        var table = new Table<Void>(HtmlStyles.summaryTable)
                .setCaption(contents.systemPropertiesSummaryLabel)
                .setHeader(new TableHeader(contents.propertyLabel, contents.referencedIn))
                .setColumnStyles(HtmlStyles.colFirst, HtmlStyles.colLast);
        for (Entry<String, List<IndexItem>> entry : searchIndexMap.entrySet()) {
            Content propertyName = Text.of(entry.getKey());
            List<IndexItem> searchIndexItems = entry.getValue();
            Content separatedReferenceLinks = new ContentBuilder();
            separatedReferenceLinks.add(createLink(searchIndexItems.get(0)));
            for (int i = 1; i < searchIndexItems.size(); i++) {
                separatedReferenceLinks.add(separator);
                separatedReferenceLinks.add(createLink(searchIndexItems.get(i)));
            }
            table.addRow(propertyName, HtmlTree.DIV(HtmlStyles.block, separatedReferenceLinks));
        }
        target.add(table);
    }

    private Map<String, List<IndexItem>> groupSystemProperties() {
        return configuration.indexBuilder.getItems(DocTree.Kind.SYSTEM_PROPERTY).stream()
                .collect(groupingBy(IndexItem::getLabel, TreeMap::new, Collectors.toCollection(ArrayList::new)));
    }

    private Content createLink(IndexItem i) {
        assert i.getDocTree().getKind() == DocTree.Kind.SYSTEM_PROPERTY : i;
        var element = i.getElement();
        if (element instanceof OverviewElement) {
            return links.createLink(pathToRoot.resolve(i.getUrl()),
                    resources.getText("doclet.Overview"));
        } else if (element instanceof DocFileElement e) {
            var fo = e.getFileObject();
            var t = titles.computeIfAbsent(e, this::getFileTitle);
            if (t.isBlank()) {
                // The user should probably be notified (a warning?) that this
                // file does not have a title
                var p = Path.of(fo.toUri());
                t = p.getFileName().toString();
            }
            var b = new ContentBuilder()
                    .add(HtmlTree.CODE(Text.of(i.getHolder() + ": ")))
                    .add(t);
            return links.createLink(pathToRoot.resolve(i.getUrl()), b);
        } else {
            // program elements should be displayed using a code font
            var link = links.createLink(pathToRoot.resolve(i.getUrl()), i.getHolder());
            return HtmlTree.CODE(link);
        }
    }
}
