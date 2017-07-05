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
package com.sun.codemodel.internal.writer;

import java.io.OutputStream;
import java.io.IOException;
import java.io.Writer;

import com.sun.codemodel.internal.CodeWriter;
import com.sun.codemodel.internal.JPackage;

/**
 * {@link CodeWriter} that delegates to another {@link CodeWriter}.
 *
 * @author Kohsuke Kawaguchi
 */
public class FilterCodeWriter extends CodeWriter {
    protected CodeWriter core;

    public FilterCodeWriter(CodeWriter core) {
        this.core = core;
    }

    public OutputStream openBinary( JPackage pkg, String fileName ) throws IOException {
        return core.openBinary(pkg, fileName);
    }

    public Writer openSource( JPackage pkg, String fileName ) throws IOException {
        return core.openSource(pkg, fileName);
    }

    public void close() throws IOException {
        core.close();
    }
}
