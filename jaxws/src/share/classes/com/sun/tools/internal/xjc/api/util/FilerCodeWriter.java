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
package com.sun.tools.internal.xjc.api.util;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

import com.sun.codemodel.internal.CodeWriter;
import com.sun.codemodel.internal.JPackage;
import com.sun.mirror.apt.Filer;

import static com.sun.mirror.apt.Filer.Location.CLASS_TREE;
import static com.sun.mirror.apt.Filer.Location.SOURCE_TREE;

/**
 * {@link CodeWriter} that generates source code to {@link Filer}.
 *
 * @author Kohsuke Kawaguchi
 */
public final class FilerCodeWriter extends CodeWriter {

    private final Filer filer;

    public FilerCodeWriter(Filer filer) {
        this.filer = filer;
    }

    public OutputStream openBinary(JPackage pkg, String fileName) throws IOException {
        Filer.Location loc;
        if(fileName.endsWith(".java")) {
            // APT doesn't do the proper Unicode escaping on Java source files,
            // so we can't rely on Filer.createSourceFile.
            loc = SOURCE_TREE;
        } else {
            // put non-Java files directly to the output folder
            loc = CLASS_TREE;
        }
        return filer.createBinaryFile(loc,pkg.name(),new File(fileName));
    }

    public Writer openSource(JPackage pkg, String fileName) throws IOException {
        String name;
        if(pkg.isUnnamed())
            name = fileName;
        else
            name = pkg.name()+'.'+fileName;

        name = name.substring(0,name.length()-5);   // strip ".java"

        return filer.createSourceFile(name);
    }

    public void close() {
        ; // noop
    }
}
