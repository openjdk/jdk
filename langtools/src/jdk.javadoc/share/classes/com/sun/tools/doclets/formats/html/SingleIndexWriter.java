/*
 * Copyright (c) 1998, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.formats.html;

import java.io.*;
import java.util.*;

import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

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
 * @author Atul M Dambalkar
 * @author Bhavesh Patel (Modified)
 */
public class SingleIndexWriter extends AbstractIndexWriter {

    private List<Object> elements;

    /**
     * Construct the SingleIndexWriter with filename "index-all.html" and the
     * {@link IndexBuilder}
     *
     * @param filename     Name of the index file to be generated.
     * @param indexbuilder Unicode based Index from {@link IndexBuilder}
     */
    public SingleIndexWriter(ConfigurationImpl configuration,
                             DocPath filename,
                             IndexBuilder indexbuilder) throws IOException {
        super(configuration, filename, indexbuilder);
    }

    /**
     * Generate single index file, for all Unicode characters.
     *
     * @param indexbuilder IndexBuilder built by {@link IndexBuilder}
     * @throws DocletAbortException
     */
    public static void generate(ConfigurationImpl configuration,
                                IndexBuilder indexbuilder) {
        SingleIndexWriter indexgen;
        DocPath filename = DocPaths.INDEX_ALL;
        try {
            indexgen = new SingleIndexWriter(configuration,
                                             filename, indexbuilder);
            indexgen.generateIndexFile();
            indexgen.close();
        } catch (IOException exc) {
            configuration.standardmessage.error(
                        "doclet.exception_encountered",
                        exc.toString(), filename);
            throw new DocletAbortException(exc);
        }
    }

    /**
     * Generate the contents of each index file, with Header, Footer,
     * Member Field, Method and Constructor Description.
     */
    protected void generateIndexFile() throws IOException {
        String title = configuration.getText("doclet.Window_Single_Index");
        HtmlTree body = getBody(true, getWindowTitle(title));
        HtmlTree htmlTree = (configuration.allowTag(HtmlTag.HEADER))
                ? HtmlTree.HEADER()
                : body;
        addTop(htmlTree);
        addNavLinks(true, htmlTree);
        if (configuration.allowTag(HtmlTag.HEADER)) {
            body.addContent(htmlTree);
        }
        HtmlTree divTree = new HtmlTree(HtmlTag.DIV);
        divTree.addStyle(HtmlStyle.contentContainer);
        Set<Object> keys = new TreeSet<>(Arrays.asList(indexbuilder.elements()));
        keys.addAll(configuration.tagSearchIndexKeys);
        elements = new ArrayList<>(keys);
        addLinksForIndexes(divTree);
        for (Object ch : elements) {
            Character unicode = (Character) ch;
            if (configuration.tagSearchIndexMap.get(unicode) == null) {
                addContents(unicode, indexbuilder.getMemberList(unicode), divTree);
            } else if (indexbuilder.getMemberList(unicode) == null) {
                addSearchContents(unicode, configuration.tagSearchIndexMap.get(unicode), divTree);
            } else {
                addContents(unicode, indexbuilder.getMemberList(unicode),
                        configuration.tagSearchIndexMap.get(unicode), divTree);
            }
        }
        addLinksForIndexes(divTree);
        body.addContent((configuration.allowTag(HtmlTag.MAIN))
                ? HtmlTree.MAIN(divTree)
                : divTree);
        if (configuration.allowTag(HtmlTag.FOOTER)) {
            htmlTree = HtmlTree.FOOTER();
        }
        addNavLinks(false, htmlTree);
        addBottom(htmlTree);
        if (configuration.allowTag(HtmlTag.FOOTER)) {
            body.addContent(htmlTree);
        }
        createSearchIndexFiles();
        printHtmlDocument(null, true, body);
    }

    /**
     * Add links for all the Index Files per unicode character.
     *
     * @param contentTree the content tree to which the links for indexes will be added
     */
    protected void addLinksForIndexes(Content contentTree) {
        for (Object ch : elements) {
            String unicode = ch.toString();
            contentTree.addContent(
                    getHyperLink(getNameForIndex(unicode),
                            new StringContent(unicode)));
            contentTree.addContent(getSpace());
        }
    }
}
