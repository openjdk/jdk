/*
 * Copyright (c) 1998, 2005, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.doclets.internal.toolkit.util.*;

import java.io.*;

/**
 * Generate Separate Index Files for all the member names with Indexing in
 * Unicode Order. This will create "index-files" directory in the current or
 * destination directory and will generate separate file for each unicode index.
 *
 * @see java.lang.Character
 * @author Atul M Dambalkar
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
        printHtmlHeader(configuration.getText("doclet.Window_Split_Index",
            unicode.toString()), null, true);
        printTop();
        navLinks(true);
        printLinksForIndexes();

        hr();

        generateContents(unicode, indexbuilder.getMemberList(unicode));

        navLinks(false);
        printLinksForIndexes();

        printBottom();
        printBodyHtmlEnd();
    }

    /**
     * Print Links for all the Index Files per unicode character.
     */
    protected void printLinksForIndexes() {
        for (int i = 0; i < indexbuilder.elements().length; i++) {
            int j = i + 1;
            printHyperLink("index-" + j + ".html",
                           indexbuilder.elements()[i].toString());
            print(' ');
        }
    }

    /**
     * Print the previous unicode character index link.
     */
    protected void navLinkPrevious() {
        if (prev == -1) {
            printText("doclet.Prev_Letter");
        } else {
            printHyperLink("index-" + prev + ".html", "",
                configuration.getText("doclet.Prev_Letter"), true);
        }
    }

    /**
     * Print the next unicode character index link.
     */
    protected void navLinkNext() {
        if (next == -1) {
            printText("doclet.Next_Letter");
        } else {
            printHyperLink("index-" + next + ".html","",
                configuration.getText("doclet.Next_Letter"), true);
        }
    }
}
