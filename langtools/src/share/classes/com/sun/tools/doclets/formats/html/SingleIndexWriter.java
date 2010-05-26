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
 * Generate only one index file for all the Member Names with Indexing in
 * Unicode Order. The name of the generated file is "index-all.html" and it is
 * generated in current or the destination directory.
 *
 * @see java.lang.Character
 * @author Atul M Dambalkar
 */
public class SingleIndexWriter extends AbstractIndexWriter {

    /**
     * Construct the SingleIndexWriter with filename "index-all.html" and the
     * {@link IndexBuilder}
     *
     * @param filename     Name of the index file to be generated.
     * @param indexbuilder Unicode based Index from {@link IndexBuilder}
     */
    public SingleIndexWriter(ConfigurationImpl configuration,
                             String filename,
                             IndexBuilder indexbuilder) throws IOException {
        super(configuration, filename, indexbuilder);
        relativepathNoSlash = ".";
        relativePath = "./";
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
        String filename = "index-all.html";
        try {
            indexgen = new SingleIndexWriter(configuration,
                                             filename, indexbuilder);
            indexgen.generateIndexFile();
            indexgen.close();
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
     */
    protected void generateIndexFile() throws IOException {
        printHtmlHeader(configuration.getText("doclet.Window_Single_Index"),
            null, true);
        printTop();
        navLinks(true);
        printLinksForIndexes();

        hr();

        for (int i = 0; i < indexbuilder.elements().length; i++) {
            Character unicode = (Character)((indexbuilder.elements())[i]);
            generateContents(unicode, indexbuilder.getMemberList(unicode));
        }

        printLinksForIndexes();
        navLinks(false);

        printBottom();
        printBodyHtmlEnd();
    }

    /**
     * Print Links for all the Index Files per unicode character.
     */
    protected void printLinksForIndexes() {
        for (int i = 0; i < indexbuilder.elements().length; i++) {
            String unicode = (indexbuilder.elements())[i].toString();
            printHyperLink("#_" + unicode + "_", unicode);
            print(' ');
        }
    }
}
