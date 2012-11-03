/*
 * Copyright (c) 1998, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.internal.toolkit.util;

import java.io.*;
import java.util.*;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.*;

/**
 * Write out the package index.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @see com.sun.javadoc.PackageDoc
 * @author Atul M Dambalkar
 */
public class PackageListWriter extends PrintWriter {

    private Configuration configuration;

    /**
     * Constructor.
     *
     * @param configuration the current configuration of the doclet.
     */
    public PackageListWriter(Configuration configuration) throws IOException {
        super(DocFile.createFileForOutput(configuration, DocPaths.PACKAGE_LIST).openWriter());
        this.configuration = configuration;
    }

    /**
     * Generate the package index.
     *
     * @param configuration the current configuration of the doclet.
     * @throws DocletAbortException
     */
    public static void generate(Configuration configuration) {
        PackageListWriter packgen;
        try {
            packgen = new PackageListWriter(configuration);
            packgen.generatePackageListFile(configuration.root);
            packgen.close();
        } catch (IOException exc) {
            configuration.message.error("doclet.exception_encountered",
                exc.toString(), DocPaths.PACKAGE_LIST);
            throw new DocletAbortException();
        }
    }

    protected void generatePackageListFile(RootDoc root) {
        PackageDoc[] packages = configuration.packages;
        ArrayList<String> names = new ArrayList<String>();
        for (int i = 0; i < packages.length; i++) {
            // if the -nodeprecated option is set and the package is marked as
            // deprecated, do not include it in the packages list.
            if (!(configuration.nodeprecated && Util.isDeprecated(packages[i])))
                names.add(packages[i].name());
        }
        Collections.sort(names);
        for (int i = 0; i < names.size(); i++) {
            println(names.get(i));
        }
    }
}
