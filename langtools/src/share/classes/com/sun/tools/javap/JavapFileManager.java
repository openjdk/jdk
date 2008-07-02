/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.javap;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;

import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;

/**
 *  javap's implementation of JavaFileManager.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
class JavapFileManager extends JavacFileManager {
    private JavapFileManager(Context context, Charset charset) {
        super(context, true, charset);
    }

    static JavapFileManager create(final DiagnosticListener<? super JavaFileObject> dl, PrintWriter log, Options options) {
        Context javac_context = new Context();

        if (dl != null) {
            // Workaround bug 6625520: javac handles missing entries on classpath badly
            // Ignore spurious errors for missing files
            DiagnosticListener<JavaFileObject> wrapper = new DiagnosticListener<JavaFileObject>() {
                public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
                    if (diagnostic instanceof JCDiagnostic) {
                        JCDiagnostic jcd = (JCDiagnostic) diagnostic;
                        if (jcd.getCode().equals("compiler.err.error.reading.file")) {
                            Object[] args = jcd.getArgs();
                            if (args.length > 0 && args[0] != null && args[0].toString().length() > 0) {
                                File f = new File(args[0].toString());
                                if (!f.exists())
                                    return;
                            }
                        }

                    }
                    dl.report(diagnostic);
                }
            };
            javac_context.put(DiagnosticListener.class, wrapper);
        }

        javac_context.put(com.sun.tools.javac.util.Log.outKey, log);

        return new JavapFileManager(javac_context, null);
    }

    void setIgnoreSymbolFile(boolean b) {
        ignoreSymbolFile = b;
    }
}
