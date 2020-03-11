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

import jdk.javadoc.internal.doclets.formats.html.SearchIndexItem.Category;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.FixedStringContent;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.formats.html.markup.Table;
import jdk.javadoc.internal.doclets.formats.html.markup.TableHeader;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.DocletElement;
import jdk.javadoc.internal.doclets.toolkit.OverviewElement;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;

import javax.lang.model.element.Element;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.WeakHashMap;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

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
     * Cached contents of {@code <title>...</title>} tags of the HTML pages.
     */
    final Map<Element, String> titles = new WeakHashMap<>();

    /**
     * Constructs SystemPropertiesWriter object.
     *
     * @param configuration The current configuration
     * @param filename Path to the file which is getting generated.
     */
    public SystemPropertiesWriter(HtmlConfiguration configuration, DocPath filename) {
        super(configuration, filename);
        this.navBar = new Navigation(null, configuration, PageMode.SYSTEM_PROPERTIES, path);
    }

    public static void generate(HtmlConfiguration configuration) throws DocFileIOException {
        generate(configuration, DocPaths.SYSTEM_PROPERTIES);
    }

    private static void generate(HtmlConfiguration configuration, DocPath fileName) throws DocFileIOException {
        boolean hasSystemProperties = configuration.searchItems
                .containsAnyOfCategories(Category.SYSTEM_PROPERTY);
        if (!hasSystemProperties) {
            // Cannot defer this check any further, because of the super() call
            // that prints out notices on creating files, etc.
            //
            // There is probably a better place for this kind of checks (see how
            // this is achieved in other "optional" pages, like Constant Values
            // and Serialized Form).
            return;
        }
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
        header.add(navBar.getContent(Navigation.Position.TOP));
        bodyTree.add(header);
        Content mainContent = new ContentBuilder();
        addSystemProperties(mainContent);
        Content titleContent = new StringContent(resources.getText("doclet.systemProperties"));
        Content pHeading = HtmlTree.HEADING(Headings.PAGE_TITLE_HEADING, true,
                HtmlStyle.title, titleContent);
        Content headerDiv = HtmlTree.DIV(HtmlStyle.header, pHeading);
        mainTree.add(headerDiv);
        mainTree.add(mainContent);
        bodyTree.add(mainTree);
        Content footer = HtmlTree.FOOTER();
        navBar.setUserFooter(getUserHeaderFooter(false));
        footer.add(navBar.getContent(Navigation.Position.BOTTOM));
        addBottom(footer);
        bodyTree.add(footer);
        printHtmlDocument(null, "system properties", bodyTree);
    }

    /**
     * Adds all the system properties to the content tree.
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
            separatedReferenceLinks.add(createLink(searchIndexItems.get(0)));
            for (int i = 1; i < searchIndexItems.size(); i++) {
                separatedReferenceLinks.add(separator);
                separatedReferenceLinks.add(createLink(searchIndexItems.get(i)));
            }
            table.addRow(propertyName, HtmlTree.DIV(HtmlStyle.block, separatedReferenceLinks));
        }
        content.add(table);
    }

    private Map<String, List<SearchIndexItem>> groupSystemProperties() {
        return searchItems
                .itemsOfCategories(Category.SYSTEM_PROPERTY)
                .collect(groupingBy(SearchIndexItem::getLabel, TreeMap::new, toList()));
    }

    private Content createLink(SearchIndexItem i) {
        assert i.getCategory() == Category.SYSTEM_PROPERTY : i;
        if (i.getElement() != null) {
            if (i.getElement() instanceof OverviewElement) {
                return links.createLink(pathToRoot.resolve(i.getUrl()),
                                        resources.getText("doclet.Overview"));
            }
            DocletElement e = ((DocletElement) i.getElement());
            // Implementations of DocletElement do not override equals and
            // hashCode; putting instances of DocletElement in a map is not
            // incorrect, but might well be inefficient
            String t = titles.computeIfAbsent(i.getElement(), utils::getHTMLTitle);
            if (t.isBlank()) {
                // The user should probably be notified (a warning?) that this
                // file does not have a title
                Path p = Path.of(e.getFileObject().toUri());
                t = p.getFileName().toString();
            }
            ContentBuilder b = new ContentBuilder();
            b.add(HtmlTree.CODE(new FixedStringContent(i.getHolder() + ": ")));
            // non-program elements should be displayed using a normal font
            b.add(t);
            return links.createLink(pathToRoot.resolve(i.getUrl()), b);
        } else {
            // program elements should be displayed using a code font
            Content link = links.createLink(pathToRoot.resolve(i.getUrl()), i.getHolder());
            return HtmlTree.CODE(link);
        }
    }
}
