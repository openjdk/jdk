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
public class ClassScope implements Scope {
    private final String packageName;
    private final String className;

    private CodeStream stream;
    private StringBuilder code;
    private int indentation;
    private boolean lastWasNewline;

    // TODO public or hidden in the API? - well we probably want to be able to use it programmatically...
    public ClassScope(String packageName, String className) {
        this.packageName = packageName;
        this.className = className;

        this.stream = new CodeStream();

        this.code = new StringBuilder();
        this.indentation = 0;
        this.lastWasNewline = false;
        openClass();
        addNewline();
    }

    public CodeStream outputStream() {
        return stream;
    }

    public void addCodeToLine(String snippet) {
        // If we just had a newline, and we are now pushing code,
	// then we have to set the correct indentation.
        if (lastWasNewline) {
            this.code.append(" ".repeat(indentation));
        }
        this.code.append(snippet);
        lastWasNewline = false;
    }

    public void addNewline() {
        this.code.append("\n");
        this.lastWasNewline = true;
    }

    public void indent() {
        this.indentation += 4;
        if (indentation > 100) {
            throw new TemplateFrameworkException("Indentation should not be too deep, is " + indentation);
        }
    }

    public void indentPop() {
        this.indentation -= 4;
        if (indentation < 0) {
            throw new TemplateFrameworkException("Indentation should not go negative");
        }
    }

    /**
     * Collect all the generated code and return it as a String.
     */
    public String toString() {
        closeClass();
        return code.toString();
    }

    private void openClass() {
        addCodeToLine("package ");
        addCodeToLine(packageName);
        addCodeToLine(";");
        addNewline();
        addNewline();
        addCodeToLine("public class ");
        addCodeToLine(className);
        addCodeToLine("{");
        indent();
    }

    private void closeClass() {
        indentPop();
        if (indentation != 0) {
            throw new TemplateFrameworkException("Indentation should be zero but is " + indentation);
        }
        addNewline();
        addCodeToLine("}");
    }
}
