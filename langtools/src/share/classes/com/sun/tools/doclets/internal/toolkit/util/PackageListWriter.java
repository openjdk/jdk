/*
 * Copyright 1998-2003 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.doclets.internal.toolkit.util;

import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.javadoc.*;
import java.io.*;
import java.util.*;

/**
 * Write out the package index.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
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
        super(Util.genWriter(configuration, configuration.destDirName,
            DocletConstants.PACKAGE_LIST_FILE_NAME, configuration.docencoding));
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
                exc.toString(), DocletConstants.PACKAGE_LIST_FILE_NAME);
            throw new DocletAbortException();
        }
    }

    protected void generatePackageListFile(RootDoc root) {
        PackageDoc[] packages = configuration.packages;
        String[] names = new String[packages.length];
        for (int i = 0; i < packages.length; i++) {
            names[i] = packages[i].name();
        }
        Arrays.sort(names);
        for (int i = 0; i < packages.length; i++) {
            println(names[i]);
        }
    }
}
