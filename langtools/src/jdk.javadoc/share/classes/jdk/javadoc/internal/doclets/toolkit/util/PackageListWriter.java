/*
 * Copyright (c) 1998, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.util;

import java.io.*;

import javax.lang.model.element.PackageElement;

import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;


/**
 * Write out the package index.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Atul M Dambalkar
 */
public class PackageListWriter {

    private final BaseConfiguration configuration;
    private final Utils utils;
    private final DocFile file;

    /**
     * Constructor.
     *
     * @param configuration the current configuration of the doclet.
     */
    public PackageListWriter(BaseConfiguration configuration) {
        file = DocFile.createFileForOutput(configuration, DocPaths.PACKAGE_LIST);
        this.configuration = configuration;
        this.utils = configuration.utils;
    }

    /**
     * Generate the package index.
     *
     * @param configuration the current configuration of the doclet.
     * @throws DocFileIOException if there is a problem writing the output
     */
    public static void generate(BaseConfiguration configuration) throws DocFileIOException {
        PackageListWriter packgen = new PackageListWriter(configuration);
        packgen.generatePackageListFile(configuration.docEnv);
    }

    protected void generatePackageListFile(DocletEnvironment docEnv) throws DocFileIOException {
        try (BufferedWriter out = new BufferedWriter(file.openWriter())) {
            for (PackageElement pkg : configuration.packages) {
                // if the -nodeprecated option is set and the package is marked as
                // deprecated, do not include it in the packages list.
                if (!(configuration.nodeprecated && utils.isDeprecated(pkg))) {
                    out.write(pkg.toString());
                    out.newLine();
                }
            }
        } catch (IOException e) {
            throw new DocFileIOException(file, DocFileIOException.Mode.WRITE, e);
        }
    }
}
