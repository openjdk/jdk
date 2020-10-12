/*
 * Copyright (c) 1998, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Set;
import java.util.TreeSet;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.internal.doclets.formats.html.markup.BodyContents;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.Entity;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.TagName;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.IndexBuilder;
import jdk.javadoc.internal.doclets.toolkit.util.IndexItem.Category;


/**
 * Generate only one index file for all the Member Names with Indexing in
 * Unicode Order. The name of the generated file is "index-all.html" and it is
 * generated in current or the destination directory.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @see java.lang.Character
 */
public class SingleIndexWriter extends AbstractIndexWriter {

    private Set<Character> firstCharacters;

    /**
     * Construct the SingleIndexWriter with filename "index-all.html" and the
     * {@link IndexBuilder}
     *
     * @param configuration the configuration for this doclet
     */
    public SingleIndexWriter(HtmlConfiguration configuration) {
        super(configuration, DocPaths.INDEX_ALL);
    }

    /**
     * Generate single index file, for all Unicode characters.
     *
     * @param configuration the configuration for this doclet
     * @throws DocFileIOException if there is a problem generating the index
     */
    public static void generate(HtmlConfiguration configuration) throws DocFileIOException {
        SingleIndexWriter indexgen = new SingleIndexWriter(configuration);
        indexgen.generateIndexFile();
    }

    /**
     * Generate the contents of each index file, with Header, Footer,
     * Member Field, Method and Constructor Description.
     * @throws DocFileIOException if there is a problem generating the index
     */
    protected void generateIndexFile() throws DocFileIOException {
        String title = resources.getText("doclet.Window_Single_Index");
        HtmlTree body = getBody(getWindowTitle(title));
        Content headerContent = new ContentBuilder();
        addTop(headerContent);
        navBar.setUserHeader(getUserHeaderFooter(true));
        headerContent.add(navBar.getContent(Navigation.Position.TOP));
        Content mainContent = new ContentBuilder();
        firstCharacters = new TreeSet<>(mainIndex.getFirstCharacters());
        addLinksForIndexes(mainContent);
        for (Character ch : firstCharacters) {
            addContents(ch, mainIndex.getItems(ch), mainContent);
        }
        addLinksForIndexes(mainContent);
        HtmlTree footer = HtmlTree.FOOTER();
        navBar.setUserFooter(getUserHeaderFooter(false));
        footer.add(navBar.getContent(Navigation.Position.BOTTOM));
        addBottom(footer);
        body.add(new BodyContents()
                .setHeader(headerContent)
                .addMainContent(HtmlTree.DIV(HtmlStyle.header,
                        HtmlTree.HEADING(Headings.PAGE_TITLE_HEADING,
                                contents.getContent("doclet.Index"))))
                .addMainContent(mainContent)
                .setFooter(footer));
        printHtmlDocument(null, "index", body);
    }

    /**
     * Add links for all the Index Files per unicode character.
     *
     * @param contentTree the content tree to which the links for indexes will be added
     */
    protected void addLinksForIndexes(Content contentTree) {
        for (Character ch : firstCharacters) {
            String unicode = ch.toString();
            contentTree.add(
                    links.createLink(getNameForIndex(unicode),
                                     new StringContent(unicode)));
            contentTree.add(Entity.NO_BREAK_SPACE);
        }
        contentTree.add(new HtmlTree(TagName.BR));
        contentTree.add(links.createLink(DocPaths.ALLCLASSES_INDEX,
                                         contents.allClassesLabel));
        if (!configuration.packages.isEmpty()) {
            contentTree.add(getVerticalSeparator());
            contentTree.add(links.createLink(DocPaths.ALLPACKAGES_INDEX,
                                             contents.allPackagesLabel));
        }
        boolean anySystemProperties = !mainIndex.getItems(DocTree.Kind.SYSTEM_PROPERTY).isEmpty();
        if (anySystemProperties) {
            contentTree.add(getVerticalSeparator());
            contentTree.add(links.createLink(DocPaths.SYSTEM_PROPERTIES, contents.systemPropertiesLabel));
        }
    }
}
