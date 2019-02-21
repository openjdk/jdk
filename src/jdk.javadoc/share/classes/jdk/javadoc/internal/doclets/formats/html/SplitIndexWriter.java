/*
 * Copyright (c) 1998, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.TreeSet;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.IndexBuilder;


/**
 * Generate Separate Index Files for all the member names with Indexing in
 * Unicode Order. This will create "index-files" directory in the current or
 * destination directory and will generate separate file for each unicode index.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @see java.lang.Character
 * @author Atul M Dambalkar
 * @author Bhavesh Patel (Modified)
 */
public class SplitIndexWriter extends AbstractIndexWriter {

    private final List<Character> indexElements;

    /**
     * Construct the SplitIndexWriter. Uses path to this file and relative path
     * from this file.
     *
     * @param configuration the configuration for this doclet
     * @param path       Path to the file which is getting generated.
     * @param indexbuilder Unicode based Index from {@link IndexBuilder}
     * @param elements the collection of characters for which to generate index files
     */
    public SplitIndexWriter(HtmlConfiguration configuration,
                            DocPath path,
                            IndexBuilder indexbuilder,
                            Collection<Character> elements) {
        super(configuration, path, indexbuilder);
        this.indexElements = new ArrayList<>(elements);
    }

    /**
     * Generate separate index files, for each Unicode character, listing all
     * the members starting with the particular unicode character.
     *
     * @param configuration the configuration for this doclet
     * @param indexbuilder IndexBuilder built by {@link IndexBuilder}
     * @throws DocFileIOException if there is a problem generating the index files
     */
    public static void generate(HtmlConfiguration configuration,
                                IndexBuilder indexbuilder) throws DocFileIOException {
        DocPath path = DocPaths.INDEX_FILES;
        Set<Character> keys = new TreeSet<>(indexbuilder.getIndexMap().keySet());
        keys.addAll(configuration.tagSearchIndexKeys);
        ListIterator<Character> li = new ArrayList<>(keys).listIterator();
        int prev;
        int next;
        while (li.hasNext()) {
            prev = (li.hasPrevious()) ? li.previousIndex() + 1 : -1;
            Object ch = li.next();
            next = (li.hasNext()) ? li.nextIndex() + 1 : -1;
            DocPath filename = DocPaths.indexN(li.nextIndex());
            SplitIndexWriter indexgen = new SplitIndexWriter(configuration,
                    path.resolve(filename),
                    indexbuilder, keys);
            indexgen.generateIndexFile((Character) ch);
            if (!li.hasNext()) {
                indexgen.createSearchIndexFiles();
            }
        }
    }

    /**
     * Generate the contents of each index file, with Header, Footer,
     * Member Field, Method and Constructor Description.
     *
     * @param unicode Unicode character referring to the character for the
     * index.
     * @throws DocFileIOException if there is a problem generating an index file
     */
    protected void generateIndexFile(Character unicode) throws DocFileIOException {
        String title = resources.getText("doclet.Window_Split_Index",
                unicode.toString());
        HtmlTree body = getBody(true, getWindowTitle(title));
        HtmlTree header = HtmlTree.HEADER();
        addTop(header);
        navBar.setUserHeader(getUserHeaderFooter(true));
        header.addContent(navBar.getContent(true));
        body.addContent(header);
        HtmlTree divTree = new HtmlTree(HtmlTag.DIV);
        divTree.setStyle(HtmlStyle.contentContainer);
        addLinksForIndexes(divTree);
        if (configuration.tagSearchIndexMap.get(unicode) == null) {
            addContents(unicode, indexbuilder.getMemberList(unicode), divTree);
        } else if (indexbuilder.getMemberList(unicode) == null) {
            addSearchContents(unicode, configuration.tagSearchIndexMap.get(unicode), divTree);
        } else {
            addContents(unicode, indexbuilder.getMemberList(unicode),
                    configuration.tagSearchIndexMap.get(unicode), divTree);
        }
        addLinksForIndexes(divTree);
        body.addContent(HtmlTree.MAIN(divTree));
        HtmlTree footer = HtmlTree.FOOTER();
        navBar.setUserFooter(getUserHeaderFooter(false));
        footer.addContent(navBar.getContent(false));
        addBottom(footer);
        body.addContent(footer);
        String description = "index: " + unicode;
        printHtmlDocument(null, description, true, body);
    }

    /**
     * Add links for all the Index Files per unicode character.
     *
     * @param contentTree the content tree to which the links for indexes will be added
     */
    protected void addLinksForIndexes(Content contentTree) {
        for (int i = 0; i < indexElements.size(); i++) {
            int j = i + 1;
            contentTree.addContent(links.createLink(DocPaths.indexN(j),
                    new StringContent(indexElements.get(i).toString())));
            contentTree.addContent(Contents.SPACE);
        }
        contentTree.addContent(new HtmlTree(HtmlTag.BR));
        contentTree.addContent(links.createLink(pathToRoot.resolve(DocPaths.ALLCLASSES_INDEX),
                contents.allClassesLabel));
        if (!configuration.packages.isEmpty()) {
            contentTree.addContent(Contents.SPACE);
            contentTree.addContent(links.createLink(pathToRoot.resolve(DocPaths.ALLPACKAGES_INDEX),
                    contents.allPackagesLabel));
    }
}
}
