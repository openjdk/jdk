/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package compiler.lib.template_framework;

/**
 * TODO public?
 */
public class ClassScope extends Scope {
    private final String packageName;
    private final String className;
    private final CodeGeneratorLibrary codeGeneratorLibrary;

    // TODO public or hidden in the API? - well we probably want to be able to use it programmatically...
    public ClassScope(String packageName, String className, CodeGeneratorLibrary codeGeneratorLibrary) {
        super(null);
        this.packageName = packageName;
        this.className = className;
        this.codeGeneratorLibrary = codeGeneratorLibrary;

        openClass();
    }

    public ClassScope(String packageName, String className) {
        this(packageName, className, CodeGeneratorLibrary.standard());
    }


    @Override
    public CodeGeneratorLibrary library() {
        return codeGeneratorLibrary;
    }

    // TODO not that smart...
    @Override
    public String toString() {
        closeClass();
        close();
        return stream.toString();
    }

    private void openClass() {
        stream.addCodeToLine("package ");
        stream.addCodeToLine(packageName);
        stream.addCodeToLine(";");
        stream.addNewline();
        stream.addNewline();
        stream.addCodeToLine("public class ");
        stream.addCodeToLine(className);
        stream.addCodeToLine("{");
        stream.indent();
        stream.addNewline();
    }

    private void closeClass() {
        stream.outdent();
        stream.addNewline();
        stream.addCodeToLine("}");
    }
}
