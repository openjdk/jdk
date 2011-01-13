/*
 * Copyright (c) 1998, 2010, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.tools.doclets.internal.toolkit.util.*;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.*;

/**
 * Generate Separate Index Files for all the member names with Indexing in
 * Unicode Order. This will create "index-files" directory in the current or
 * destination directory and will generate separate file for each unicode index.
 *
 * @see java.lang.Character
 * @author Atul M Dambalkar
 * @author Bhavesh Patel (Modified)
 */
public class SplitIndexWriter extends AbstractIndexWriter {

    /**
     * Previous unicode character index in the built index.
     */
    protected int prev;

    /**
     * Next unicode character in the built index.
     */
    protected int next;

    /**
     * Construct the SplitIndexWriter. Uses path to this file and relative path
     * from this file.
     *
     * @param path       Path to the file which is getting generated.
     * @param filename   Name of the file which is getting genrated.
     * @param relpath    Relative path from this file to the current directory.
     * @param indexbuilder Unicode based Index from {@link IndexBuilder}
     */
    public SplitIndexWriter(ConfigurationImpl configuration,
                            String path, String filename,
                            String relpath, IndexBuilder indexbuilder,
                            int prev, int next) throws IOException {
        super(configuration, path, filename, relpath, indexbuilder);
        this.prev = prev;
        this.next = next;
    }

    /**
     * Generate separate index files, for each Unicode character, listing all
     * the members starting with the particular unicode character.
     *
     * @param indexbuilder IndexBuilder built by {@link IndexBuilder}
     * @throws DocletAbortException
     */
    public static void generate(ConfigurationImpl configuration,
                                IndexBuilder indexbuilder) {
        SplitIndexWriter indexgen;
        String filename = "";
        String path = DirectoryManager.getPath("index-files");
        String relpath = DirectoryManager.getRelativePath("index-files");
        try {
            for (int i = 0; i < indexbuilder.elements().length; i++) {
                int j = i + 1;
                int prev = (j == 1)? -1: i;
                int next = (j == indexbuilder.elements().length)? -1: j + 1;
                filename = "index-" + j +".html";
                indexgen = new SplitIndexWriter(configuration,
                                                path, filename, relpath,
                                                indexbuilder, prev, next);
                indexgen.generateIndexFile((Character)indexbuilder.
                                                                 elements()[i]);
                indexgen.close();
            }
        } catch (IOException exc) {
            configuration.standardmessage.error(
                        "doclet.exception_encountered",
                        exc.toString(), filename);
            throw new DocletAbortException();
        }
    }

    /**
     * Generate the contents of each index file, with Header, Footer,
     * Member Field, Method and Constructor Description.
     *
     * @param unicode Unicode character referring to the character for the
     * index.
     */
    protected void generateIndexFile(Character unicode) throws IOException {
        String title = configuration.getText("doclet.Window_Split_Index",
                unicode.toString());
        Content body = getBody(true, getWindowTitle(title));
        addTop(body);
        addNavLinks(true, body);
        HtmlTree divTree = new HtmlTree(HtmlTag.DIV);
        divTree.addStyle(HtmlStyle.contentContainer);
        addLinksForIndexes(divTree);
        addContents(unicode, indexbuilder.getMemberList(unicode), divTree);
        addLinksForIndexes(divTree);
        body.addContent(divTree);
        addNavLinks(false, body);
        addBottom(body);
        printHtmlDocument(null, true, body);
    }

    /**
     * Add links for all the Index Files per unicode character.
     *
     * @param contentTree the content tree to which the links for indexes will be added
     */
    protected void addLinksForIndexes(Content contentTree) {
        Object[] unicodeChars = indexbuilder.elements();
        for (int i = 0; i < unicodeChars.length; i++) {
            int j = i + 1;
            contentTree.addContent(getHyperLink("index-" + j + ".html",
                    new StringContent(unicodeChars[i].toString())));
            contentTree.addContent(getSpace());
        }
    }

    /**
     * Get link to the previous unicode character.
     *
     * @return a content tree for the link
     */
    public Content getNavLinkPrevious() {
        Content prevletterLabel = getResource("doclet.Prev_Letter");
        if (prev == -1) {
            return HtmlTree.LI(prevletterLabel);
        }
        else {
            Content prevLink = getHyperLink("index-" + prev + ".html", "",
                    prevletterLabel);
            return HtmlTree.LI(prevLink);
        }
    }

    /**
     * Get link to the next unicode character.
     *
     * @return a content tree for the link
     */
    public Content getNavLinkNext() {
        Content nextletterLabel = getResource("doclet.Next_Letter");
        if (next == -1) {
            return HtmlTree.LI(nextletterLabel);
        }
        else {
            Content nextLink = getHyperLink("index-" + next + ".html","",
                    nextletterLabel);
            return HtmlTree.LI(nextLink);
        }
    }
}
