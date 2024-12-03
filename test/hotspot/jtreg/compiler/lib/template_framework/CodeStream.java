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

import java.util.List;
import java.util.ArrayList;

/**
 * TODO public?
 */
public class CodeStream {

    private sealed interface Token permits CodeSegment, Newline, Indent, Outdent, NestedCodeStream {}
    private record CodeSegment(String code) implements Token {}
    private record Newline() implements Token {}
    private record Indent() implements Token {}
    private record Outdent() implements Token {}
    private record NestedCodeStream(CodeStream stream) implements Token {}

    // To avoid allocating repeatedly.
    private static final Newline NEWLINE = new Newline();
    private static final Indent  INDENT  = new Indent();
    private static final Outdent OUTDENT = new Outdent();

    private List<Token> stream;
    public boolean closed;

    public CodeStream() {
        this.stream = new ArrayList<Token>();
        this.closed = false;
        checkOpen();
    }

    public void checkOpen() {
        if (closed) {
            throw new TemplateFrameworkException("Stream is already closed.");
        }
    }

    public void checkClosed() {
        if (closed) {
            throw new TemplateFrameworkException("Stream is still open.");
        }
    }

    /**
     * Add any code string, including newlines "\n".
     */
    public void addCode(String code) {
        checkOpen();
        String[] snippets = code.split("\n");
        for (int i = 0; i < snippets.length; i++) {
            if (i > 0) { addNewline(); }
            addCodeToLine(snippets[i]);
        }
    }

    /**
     * Add code to the current line, no newline allowed.
     */
    public void addCodeToLine(String code) {
        checkOpen();
        if (code.contains("\n")) {
            throw new TemplateFrameworkException("No newline allowed. Got: " + code);
        }
        stream.add(new CodeSegment(code));
    }

    public void addNewline() {
        checkOpen();
        stream.add(NEWLINE);
    }

    public void indent() {
        checkOpen();
        stream.add(INDENT);
    }

    public void outdent() {
        checkOpen();
        stream.add(OUTDENT);
    }

    public void addCodeStream(CodeStream nestedStream) {
        checkOpen();
        nestedStream.checkClosed();
        stream.add(new NestedCodeStream(nestedStream));
    }

    public void close() {
        checkOpen();
        closed = true;
        checkClosed();
    }

    public String toString() {
        checkClosed();
        // TODO
        return "";
    }
} 
