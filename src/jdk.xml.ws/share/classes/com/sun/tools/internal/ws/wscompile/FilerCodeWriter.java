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

package com.sun.tools.internal.ws.wscompile;

import com.sun.codemodel.internal.CodeWriter;
import com.sun.codemodel.internal.JPackage;

import javax.annotation.processing.Filer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

/**
 * Writes all the source files using the specified Filer.
 *
 * @author WS Development Team
 */
public class FilerCodeWriter extends CodeWriter {

    /** The Filer used to create files. */
    private final Filer filer;

    private Writer w;

    public FilerCodeWriter(Options options) throws IOException {
        this.filer = options.filer;
    }

    @Override
    public OutputStream openBinary(JPackage pkg, String fileName) throws IOException {
        throw new UnsupportedOperationException("Not supported.");
    }

    public Writer openSource(JPackage pkg, String fileName) throws IOException {
        String tmp = fileName.substring(0, fileName.length()-5);
        if (pkg.name() != null && ! "".equals(pkg.name())) {
                w = filer.createSourceFile(pkg.name() + "." + tmp).openWriter();
        } else {
                w = filer.createSourceFile(tmp).openWriter();
        }
        return w;
    }


    public void close() throws IOException {
        if (w != null)
            w.close();
        w = null;
    }
}
