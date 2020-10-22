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

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.WeakHashMap;
import javax.lang.model.element.Element;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.SpecTree;
import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.BodyContents;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.FixedStringContent;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.formats.html.markup.Table;
import jdk.javadoc.internal.doclets.formats.html.markup.TableHeader;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.DocletElement;
import jdk.javadoc.internal.doclets.toolkit.OverviewElement;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.IndexItem;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

/**
 * Generates the file with the summary of all the references to other specifications.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class OtherSpecsWriter extends HtmlDocletWriter {

    private final Navigation navBar;

    /**
     * Cached contents of {@code <title>...</title>} tags of the HTML pages.
     */
    final Map<Element, String> titles = new WeakHashMap<>();

    /**
     * Constructs OtherSpecWriter object.
     *
     * @param configuration The current configuration
     * @param filename Path to the file which is getting generated.
     */
    public OtherSpecsWriter(HtmlConfiguration configuration, DocPath filename) {
        super(configuration, filename);
        this.navBar = new Navigation(null, configuration, PageMode.OTHER_SPECS, path);
    }

    public static void generate(HtmlConfiguration configuration) throws DocFileIOException {
        generate(configuration, DocPaths.OTHER_SPECS);
    }

    private static void generate(HtmlConfiguration configuration, DocPath fileName) throws DocFileIOException {
        boolean hasOtherSpecs = configuration.mainIndex != null
                && !configuration.mainIndex.getItems(DocTree.Kind.SPEC).isEmpty();
        if (!hasOtherSpecs) {
            return;
        }
        OtherSpecsWriter otherSpecWriter = new OtherSpecsWriter(configuration, fileName);
        otherSpecWriter.buildOtherSpecsPage();
        configuration.conditionalPages.add(HtmlConfiguration.ConditionalPage.OTHER_SPECS);
    }

    /**
     * Prints all the system properties to the file.
     */
    protected void buildOtherSpecsPage() throws DocFileIOException {
        String title = resources.getText("doclet.Other_Specifications");
        HtmlTree body = getBody(getWindowTitle(title));
        Content headerContent = new ContentBuilder();
        addTop(headerContent);
        navBar.setUserHeader(getUserHeaderFooter(true));
        headerContent.add(navBar.getContent(Navigation.Position.TOP));
        Content mainContent = new ContentBuilder();
        addOtherSpecs(mainContent);
        Content footer = HtmlTree.FOOTER();
        navBar.setUserFooter(getUserHeaderFooter(false));
        footer.add(navBar.getContent(Navigation.Position.BOTTOM));
        addBottom(footer);
        body.add(new BodyContents()
                .setHeader(headerContent)
                .addMainContent(HtmlTree.DIV(HtmlStyle.header,
                        HtmlTree.HEADING(Headings.PAGE_TITLE_HEADING,
                                contents.getContent("doclet.Other_Specifications"))))
                .addMainContent(mainContent)
                .setFooter(footer));
        printHtmlDocument(null, "other specifications", body);

        if (configuration.mainIndex != null) {
            configuration.mainIndex.add(IndexItem.of(IndexItem.Category.TAGS, title, path));
        }
    }

    /**
     * Adds all the references to other specifications to the content tree.
     *
     * @param content HtmlTree content to which the links will be added
     */
    protected void addOtherSpecs(Content content) {
        Map<String, List<IndexItem>> searchIndexMap = groupOtherSpecs();
        Content separator = new StringContent(", ");
        Table table = new Table(HtmlStyle.summaryTable)
                .setCaption(contents.otherSpecifications)
                .setHeader(new TableHeader(contents.specificationLabel, contents.referencedIn))
                .setColumnStyles(HtmlStyle.colFirst, HtmlStyle.colLast);
        for (Entry<String, List<IndexItem>> entry : searchIndexMap.entrySet()) {
            List<IndexItem> searchIndexItems = entry.getValue();
            Content specName = createSpecLink(searchIndexItems.get(0));
            Content separatedReferenceLinks = new ContentBuilder();
            separatedReferenceLinks.add(createLink(searchIndexItems.get(0)));
            for (int i = 1; i < searchIndexItems.size(); i++) {
                separatedReferenceLinks.add(separator);
                separatedReferenceLinks.add(createLink(searchIndexItems.get(i)));
            }
            table.addRow(specName, HtmlTree.DIV(HtmlStyle.block, separatedReferenceLinks));
        }
        content.add(table);
    }

    private Map<String, List<IndexItem>> groupOtherSpecs() {
        return configuration.mainIndex.getItems(DocTree.Kind.SPEC).stream()
                .collect(groupingBy(IndexItem::getLabel, TreeMap::new, toList()));
    }

    private Content createLink(IndexItem i) {
        assert i.getDocTree().getKind() == DocTree.Kind.SPEC : i;
        Element element = i.getElement();
        if (element instanceof OverviewElement) {
            return links.createLink(pathToRoot.resolve(i.getUrl()),
                    resources.getText("doclet.Overview"));
        } else if (element instanceof DocletElement) {
            DocletElement e = (DocletElement) element;
            // Implementations of DocletElement do not override equals and
            // hashCode; putting instances of DocletElement in a map is not
            // incorrect, but might well be inefficient
            String t = titles.computeIfAbsent(element, utils::getHTMLTitle);
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

    private Content createSpecLink(IndexItem i) {
        assert i.getDocTree().getKind() == DocTree.Kind.SPEC : i;
        SpecTree specTree = (SpecTree) i.getDocTree();

        Content label = commentTagsToContent(specTree, i.getElement(), specTree.getLabel(), false);

        URI specURI;
        try {
            specURI = new URI(specTree.getURI().getBody());
        } catch (URISyntaxException e) {
            // should not happen: items with bad URIs should not make it into the index
            return label;
        }

        if (!specURI.isAbsolute()) {
            URI baseURI = configuration.getOptions().specBaseURI();
            if (baseURI != null) {
                if (!baseURI.isAbsolute() && !pathToRoot.isEmpty()) {
                    baseURI = URI.create(pathToRoot.getPath() + "/").resolve(baseURI);
                }
                specURI = baseURI.resolve(specURI);
            }
        }

        return HtmlTree.A(specURI, label);
    }
}
