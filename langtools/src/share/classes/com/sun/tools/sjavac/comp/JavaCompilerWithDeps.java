/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.tools.sjavac.comp;

import java.util.StringTokenizer;

import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.sjavac.server.CompilerThread;
import java.io.File;

/** Subclass to Resolve that overrides collect.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public class JavaCompilerWithDeps extends JavaCompiler {

    /** The dependency database
     */
    protected Dependencies deps;
    protected CompilerThread compilerThread;

    public JavaCompilerWithDeps(Context context, CompilerThread t) {
        super(context);
        deps = Dependencies.instance(context);
        compilerThread = t;
        needRootClasses = true;
    }

    public static void preRegister(Context context, final CompilerThread t) {
        context.put(compilerKey, new Context.Factory<JavaCompiler>() {
            public JavaCompiler make(Context c) {
                JavaCompiler instance = new JavaCompilerWithDeps(c, t);
                c.put(JavaCompiler.class, instance);
                return instance;
            }
        });
    }

    /** Collect the public apis of classes supplied explicitly for compilation.
     * @param sym The class to visit.
     */
    @Override
    public void reportPublicApi(ClassSymbol sym) {
        // The next test will catch when source files are located in the wrong directory!
        // This ought to be moved into javac as a new warning, or perhaps as part
        // of the auxiliary class warning.

        // For example if sun.swing.BeanInfoUtils
        // is in fact stored in: /mybuild/jdk/gensrc/javax/swing/beaninfo/BeanInfoUtils.java

        // We do not need to test that BeanInfoUtils is stored in a file named BeanInfoUtils
        // since this is checked earlier.
        if (sym.sourcefile != null) {
            // Rewrite sun.swing.BeanInfoUtils into /sun/swing/
            StringBuilder pathb = new StringBuilder();
            StringTokenizer qn = new StringTokenizer(sym.packge().toString(), ".");
            boolean first = true;
            while (qn.hasMoreTokens()) {
                String o = qn.nextToken();
                pathb.append("/");
                pathb.append(o);
                first = false;
            }
            pathb.append("/");
            String path = pathb.toString();

            // Now cut the uri to be: file:///mybuild/jdk/gensrc/javax/swing/beaninfo/
            String p = sym.sourcefile.toUri().getPath();
            // Do not use File.separatorChar here, a URI always uses slashes /.
            int i = p.lastIndexOf("/");
            String pp = p.substring(0,i+1);

            // Now check if the truncated uri ends with the path. (It does not == failure!)
            if (path.length() > 0 && !path.equals("/unnamed package/") && !pp.endsWith(path)) {
                compilerThread.logError("Error: The source file "+sym.sourcefile.getName()+
                                        " is located in the wrong package directory, because it contains the class "+
                                        sym.getQualifiedName());
            }
        }
        deps.visitPubapi(sym);
    }
}
