/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.apple.internal.jobjc.generator.classes;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

public class GeneratedClassFile extends OutputFile {
    protected final String className;
    protected final String genericArgs;
    protected final String superClass;

    public GeneratedClassFile(final String pkg, final String classname, final String superClass) {
        this(pkg, classname, null, superClass);
    }

    public GeneratedClassFile(final String pkg, final String classname, final String genericArgs, final String superClass) {
        super(pkg, classname + ".java");
        this.className = classname;
        this.genericArgs = genericArgs;
        this.superClass = superClass;
    }

    @Override
    public void write(final File parentDir) {
        try {
            final PrintStream out = open(parentDir);
            out.println("package " + pkg + ";");
            out.println();
            out.print("public " + (isFinal() ? "final" : "") + " class " + className);
            if(genericArgs != null) out.print("<" + genericArgs + ">");
            if (superClass != null) out.print(" extends " + superClass);
            out.println(" {");
            writeBeginning(out);
            writeBody(out);
            writeEnd(out);
            out.println("}");
            close(out);
        } catch (final IOException e) { throw new RuntimeException(e); }
    }

    public void writeBeginning(final PrintStream out) {

    }

    public void writeBody(final PrintStream out) {

    }

    public void writeEnd(final PrintStream out) {

    }

    protected boolean isFinal(){ return false; }
}
