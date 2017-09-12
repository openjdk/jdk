/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

import com.sun.codemodel.internal.CodeWriter;
import com.sun.codemodel.internal.JPackage;
import com.sun.codemodel.internal.writer.FilterCodeWriter;

/**
 * {@link CodeWriter} that reports progress to {@link XJCListener}.
 */
final class ProgressCodeWriter extends FilterCodeWriter {

    private int current;
    private final int totalFileCount;

    public ProgressCodeWriter( CodeWriter output, XJCListener progress, int totalFileCount ) {
        super(output);
        this.progress = progress;
        this.totalFileCount = totalFileCount;
        if(progress==null)
            throw new IllegalArgumentException();
    }

    private final XJCListener progress;

    @Override
    public Writer openSource(JPackage pkg, String fileName) throws IOException {
        report(pkg,fileName);
        return super.openSource(pkg, fileName);
    }

    @Override
    public OutputStream openBinary(JPackage pkg, String fileName) throws IOException {
        report(pkg,fileName);
        return super.openBinary(pkg,fileName);
    }

    /**
     * Report progress to {@link XJCListener}.
     * @param pkg The package of file being written. Value of {@code null} means that file has no package.
     * @param fileName The file name being written. Value can't be {@code null}.
     */
    private void report(final JPackage pkg, final String fileName) {
        if (fileName == null) {
            throw new IllegalArgumentException("File name is null");
        }

        final String pkgName;
        final String fileNameOut;
        if (pkg != null && (pkgName = pkg.name().replace('.', File.separatorChar)).length() > 0 ) {
            final StringBuilder sb = new StringBuilder(fileName.length() + pkgName.length() + 1);
            sb.append(pkgName);
            sb.append(File.separatorChar);
            sb.append(fileName);
            fileNameOut = sb.toString();
        } else {
            fileNameOut = fileName;
        }

        if(progress.isCanceled())
            throw new AbortException();
        progress.generatedFile(fileNameOut, current++, totalFileCount);
    }
}
