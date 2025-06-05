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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.IndexItem;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.ContentBuilder;
import jdk.javadoc.internal.html.HtmlTree;
import jdk.javadoc.internal.html.Text;

import static java.util.stream.Collectors.groupingBy;

/**
 * Generates summary files for tags represented by {@code IndexItem}.
 * Supported items are search tags and system properties.
 */
public abstract class IndexItemListWriter extends HtmlDocletWriter {

    /**
     * Cached contents of {@code <title>...</title>} tags of the HTML pages.
     */
    final Map<DocFileElement, String> titles = new WeakHashMap<>();

    /**
     * Constructs a IndexItemListWriter object.
     *
     * @param configuration The current configuration
     * @param path the doc path of the file to write
     */
    protected IndexItemListWriter(HtmlConfiguration configuration, DocPath path) {
        super(configuration, path, false);
    }

    @Override
    public void buildPage() throws DocFileIOException {
        boolean hasTags = configuration.indexBuilder != null
                && !configuration.indexBuilder.getItems(getKind()).isEmpty();
        if (!hasTags) {
            return;
        }

        writeGenerating();
        configuration.conditionalPages.add(getConditionalPage());

        String title = getPageLabel().toString();
        HtmlTree body = getBody(getWindowTitle(title));
        Content mainContent = new ContentBuilder();
        addIndexItems(mainContent);
        body.add(new BodyContents()
                .setHeader(getHeader(getPageMode()))
                .addMainContent(HtmlTree.DIV(HtmlStyles.header,
                        HtmlTree.HEADING(Headings.PAGE_TITLE_HEADING, getPageLabel())))
                .addMainContent(mainContent)
                .setFooter(getFooter()));
        printHtmlDocument(null, title.toLowerCase(Locale.ROOT), body);

        if (configuration.indexBuilder != null) {
            configuration.indexBuilder.add(IndexItem.of(IndexItem.Category.TAGS, title, path));
        }
    }

    protected Map<String, List<IndexItem>> groupTags() {
        return configuration.indexBuilder.getItems(getKind()).stream()
                .collect(groupingBy(IndexItem::getLabel, TreeMap::new, Collectors.toCollection(ArrayList::new)));
    }

    protected Content createLink(IndexItem i) {
        assert i.getDocTree().getKind() == getKind() : i;
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

    /**
     * Adds a table with the included index items to the content.
     *
     * @param target the content to which the table will be added
     */
    protected abstract void addIndexItems(Content target);

    /**
     * {@return the kind of index item to list in this page}
     */
    protected abstract DocTree.Kind getKind();

    /**
     * {@return the conditional page value}
     */
    protected abstract HtmlConfiguration.ConditionalPage getConditionalPage();

    /**
     * {@return the label for the page heading}
     */
    protected abstract Content getPageLabel();

    /**
     * {@return the Navigation.PageMode value}
     */
    protected abstract Navigation.PageMode getPageMode();

    // Note: The reason we can't use anonymous classes below is that HtmlDocletWriter.getBodyStyle()
    // uses the writer's class name to deduce the CSS body style name.

    public static IndexItemListWriter createSystemPropertiesWriter(HtmlConfiguration configuration) {
        return new SystemPropertiesWriter(configuration);
    }

    public static IndexItemListWriter createSearchTagsWriter(HtmlConfiguration configuration) {
        return new SearchTagsWriter(configuration);
    }

    static class SystemPropertiesWriter extends IndexItemListWriter {
        SystemPropertiesWriter(HtmlConfiguration configuration) {
            super(configuration, DocPaths.SYSTEM_PROPERTIES);
        }

        @Override
        protected DocTree.Kind getKind() {
            return DocTree.Kind.SYSTEM_PROPERTY;
        }

        @Override
        protected HtmlConfiguration.ConditionalPage getConditionalPage() {
            return HtmlConfiguration.ConditionalPage.SYSTEM_PROPERTIES;
        }

        @Override
        protected Content getPageLabel() {
            return contents.systemPropertiesLabel;
        }

        @Override
        protected Navigation.PageMode getPageMode() {
            return PageMode.SYSTEM_PROPERTIES;
        }

        /**
         * Creates a 2-column table containing system properties.
         *
         * @param target the content to which the links will be added
         */
        @Override
        protected void addIndexItems(Content target) {
            Map<String, List<IndexItem>> searchIndexMap = groupTags();
            Content separator = Text.of(", ");
            var table = new Table<Void>(HtmlStyles.summaryTable)
                    .setCaption(contents.systemPropertiesSummaryLabel)
                    .setHeader(new TableHeader(contents.propertyLabel, contents.referencedIn))
                    .setColumnStyles(HtmlStyles.colFirst, HtmlStyles.colLast);
            searchIndexMap.forEach((key, searchIndexItems) -> {
                Content propertyName = Text.of(key);
                Content referenceLinks = new ContentBuilder();
                for (IndexItem searchIndexItem : searchIndexItems) {
                    if (!referenceLinks.isEmpty()) {
                        referenceLinks.add(separator);
                    }
                    referenceLinks.add(createLink(searchIndexItem));
                }
                table.addRow(propertyName, HtmlTree.DIV(HtmlStyles.block, referenceLinks));
            });
            target.add(table);
        }
    }

    static class SearchTagsWriter extends IndexItemListWriter {
        SearchTagsWriter(HtmlConfiguration configuration) {
            super(configuration, DocPaths.SEARCH_TAGS);
        }

        @Override
        protected DocTree.Kind getKind() {
            return DocTree.Kind.INDEX;
        }

        @Override
        protected HtmlConfiguration.ConditionalPage getConditionalPage() {
            return HtmlConfiguration.ConditionalPage.SEARCH_TAGS;
        }

        @Override
        protected Content getPageLabel() {
            return contents.searchTagsLabel;
        }

        @Override
        protected Navigation.PageMode getPageMode() {
            return PageMode.SEARCH_TAGS;
        }

        /**
         * Creates a 3-column table containing search tags.
         *
         * @param target the content to which the links will be added
         */
        @Override
        protected void addIndexItems(Content target) {
            Map<String, List<IndexItem>> searchIndexMap = groupTags();
            Content separator = Text.of(", ");
            var table = new Table<Void>(HtmlStyles.summaryTable)
                    .setCaption(contents.getContent("doclet.searchTagsSummary"))
                    .setHeader(new TableHeader(contents.getContent("doclet.searchTag"),
                            contents.descriptionLabel,
                            contents.getContent("doclet.DefinedIn")))
                    .setColumnStyles(HtmlStyles.colFirst, HtmlStyles.colSecond, HtmlStyles.colLast);
            searchIndexMap.forEach((key, searchIndexItems) -> {
                Content propertyName = Text.of(key);
                Content referenceLinks = new ContentBuilder();
                String description = "";
                for (IndexItem searchIndexItem : searchIndexItems) {
                    if (!referenceLinks.isEmpty()) {
                        referenceLinks.add(separator);
                    }
                    referenceLinks.add(createLink(searchIndexItem));
                    if (description.isEmpty()) {
                        description = searchIndexItem.getDescription();
                        Objects.requireNonNull(description);
                    }
                }
                table.addRow(propertyName, Text.of(description), HtmlTree.DIV(HtmlStyles.block, referenceLinks));
            });
            target.add(table);
        }
    }
}
