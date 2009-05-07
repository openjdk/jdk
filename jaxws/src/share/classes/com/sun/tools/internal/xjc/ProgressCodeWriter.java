/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

    public Writer openSource(JPackage pkg, String fileName) throws IOException {
        report(pkg,fileName);
        return super.openSource(pkg, fileName);
    }

    public OutputStream openBinary(JPackage pkg, String fileName) throws IOException {
        report(pkg,fileName);
        return super.openBinary(pkg,fileName);
    }

    private void report(JPackage pkg, String fileName) {
        String name = pkg.name().replace('.', File.separatorChar);
        if(name.length()!=0)    name +=     File.separatorChar;
        name += fileName;

        if(progress.isCanceled())
            throw new AbortException();
        progress.generatedFile(name,current++,totalFileCount);
    }
}
