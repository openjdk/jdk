/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Navigation;
import jdk.javadoc.internal.doclets.formats.html.markup.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.formats.html.markup.Table;
import jdk.javadoc.internal.doclets.formats.html.markup.TableHeader;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Generates the file with the summary of all the system properties.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class SystemPropertiesWriter extends HtmlDocletWriter {

    /**
     * The HTML tree for main tag.
     */
    private final HtmlTree mainTree = HtmlTree.MAIN();

    private final Navigation navBar;

    /**
     * Constructs SystemPropertiesWriter object.
     *
     * @param configuration The current configuration
     * @param filename Path to the file which is getting generated.
     */
    public SystemPropertiesWriter(HtmlConfiguration configuration, DocPath filename) {
        super(configuration, filename);
        this.navBar = new Navigation(null, configuration, PageMode.SYSTEMPROPERTIES, path);
    }

    /**
     * Creates SystemPropertiesWriter object.
     *
     * @param configuration The current configuration
     * @throws DocFileIOException
     */
    public static void generate(HtmlConfiguration configuration) throws DocFileIOException {
        generate(configuration, DocPaths.SYSTEM_PROPERTIES);
    }

    private static void generate(HtmlConfiguration configuration, DocPath fileName) throws DocFileIOException {
        SystemPropertiesWriter systemPropertiesGen = new SystemPropertiesWriter(configuration, fileName);
        systemPropertiesGen.buildSystemPropertiesPage();
    }

    /**
     * Prints all the system properties to the file.
     */
    protected void buildSystemPropertiesPage() throws DocFileIOException {
        String label = resources.getText("doclet.systemProperties");
        HtmlTree bodyTree = getBody(getWindowTitle(label));
        HtmlTree header = HtmlTree.HEADER();
        addTop(header);
        navBar.setUserHeader(getUserHeaderFooter(true));
        header.add(navBar.getContent(true));
        bodyTree.add(header);
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.setStyle(HtmlStyle.systemPropertiesContainer);
        addSystemProperties(div);
        Content titleContent = new StringContent(resources.getText("doclet.systemProperties"));
        Content pHeading = HtmlTree.HEADING(Headings.PAGE_TITLE_HEADING, true,
                HtmlStyle.title, titleContent);
        Content headerDiv = HtmlTree.DIV(HtmlStyle.header, pHeading);
        mainTree.add(headerDiv);
        mainTree.add(div);
        bodyTree.add(mainTree);
        Content footer = HtmlTree.FOOTER();
        navBar.setUserFooter(getUserHeaderFooter(false));
        footer.add(navBar.getContent(false));
        addBottom(footer);
        bodyTree.add(footer);
        printHtmlDocument(null, "system properties", bodyTree);
    }

    /**
     * Add all the system properties to the content tree.
     *
     * @param content HtmlTree content to which the links will be added
     */
    protected void addSystemProperties(Content content) {
        Map<String, List<SearchIndexItem>> searchIndexMap = groupSystemProperties();
        Content separator = new StringContent(", ");
        Table table = new Table(HtmlStyle.systemPropertiesSummary)
                .setCaption(getTableCaption(contents.systemPropertiesSummaryLabel))
                .setHeader(new TableHeader(contents.propertyLabel, contents.referencedIn))
                .setColumnStyles(HtmlStyle.colFirst, HtmlStyle.colLast);
        for (Entry<String, List<SearchIndexItem>> entry : searchIndexMap.entrySet()) {
            Content propertyName = new StringContent(entry.getKey());
            List<SearchIndexItem> searchIndexItems = entry.getValue();
            Content separatedReferenceLinks = new ContentBuilder();
            separatedReferenceLinks.add(links.createLink(
                    pathToRoot.resolve(searchIndexItems.get(0).getUrl()),
                    getLinkLabel(searchIndexItems.get(0))));
            for (int i = 1; i < searchIndexItems.size(); i++) {
                separatedReferenceLinks.add(separator);
                separatedReferenceLinks.add(links.createLink(
                        pathToRoot.resolve(searchIndexItems.get(i).getUrl()),
                        getLinkLabel(searchIndexItems.get(i))));
            }
            table.addRow(propertyName, separatedReferenceLinks);
        }
        content.add(table.toContent());
    }

    private Map<String, List<SearchIndexItem>> groupSystemProperties() {
        Map<String, List<SearchIndexItem>> searchIndexMap = new TreeMap<>();
        for (SearchIndexItem searchIndex : searchItems.get(SearchIndexItem.Category.SEARCH_TAGS)) {
            if (searchIndex.isSystemProperty()) {
                List<SearchIndexItem> list = searchIndexMap
                        .computeIfAbsent(searchIndex.getLabel(), k -> new ArrayList<>());
                list.add(searchIndex);
            }
        }
        return searchIndexMap;
    }

    private String getLinkLabel(SearchIndexItem searchIndexItem) {
        String holder = searchIndexItem.getHolder();
        String url = searchIndexItem.getUrl();
        final String docFiles = "/doc-files/";
        if (url.contains(docFiles)) {
            final int idx = url.indexOf(docFiles);
            final int len = docFiles.length();
            return url.substring(idx + len, url.indexOf("#", idx));
        }
        return holder;
    }
}
