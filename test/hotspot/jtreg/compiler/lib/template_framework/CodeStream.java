/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * The {@link CodeStream} collects code {@link Token}s that are generated during {@link CodeGenerator} instantiation.
 * It allows addition of plane {@link String}s, newlines, indentation, and even adding the whole content of another
 * {@link CodeStream}. Once a {@link CodeStream} is closed, its {@link String} representation can be requested with
 * {@link toString}.
 */
public final class CodeStream {

    // Token definition.
    private sealed interface Token permits CodeSegment, Newline, Indent, Outdent, MultiIndent, NestedCodeStream {}
    private record CodeSegment(String code) implements Token {}
    private record Newline() implements Token {}
    private record Indent() implements Token {}
    private record Outdent() implements Token {}
    private record MultiIndent(int difference) implements Token {}
    private record NestedCodeStream(CodeStream stream) implements Token {}

    // To avoid allocating repeatedly.
    private static final Newline NEWLINE = new Newline();
    private static final Indent  INDENT  = new Indent();
    private static final Outdent OUTDENT = new Outdent();

    private List<Token> stream;
    private boolean closed;
    private int indentation;

    /**
     * Create a new empty {@link CodeStream}.
     */
    public CodeStream() {
        this.stream = new ArrayList<Token>();
        this.closed = false;
        this.indentation = 0;
        checkOpen();
    }

    /**
     * Check if the {@link CodeStream} is still open.
     *
     * @throws TemplateFrameworkException If the stream is already closed.
     */
    public void checkOpen() {
        if (closed) {
            throw new TemplateFrameworkException("Stream is already closed.");
        }
    }

    /**
     * Check if the {@link CodeStream} is already closed.
     *
     * @throws TemplateFrameworkException If the stream is still open.
     */
    public void checkClosed() {
        if (!closed) {
            throw new TemplateFrameworkException("Stream is still open.");
        }
    }

    /**
     * Add code to the current line, no newline allowed.
     *
     * @param code The code to be added to the current line.
     */
    public void addCodeToLine(String code) {
        checkOpen();
        if (code.contains("\n")) {
            throw new TemplateFrameworkException("No newline allowed. Got: " + code);
        }
        if (!code.equals("")) {
            stream.add(new CodeSegment(code));
        }
    }

    /**
     * Add a newline to the stream.
     */
    public void addNewline() {
        checkOpen();
        stream.add(NEWLINE);
    }

    /**
     * Get the (local) indentation of the stream.
     *
     * @return The indentation of the stream.
     */
    public int getIndentation() {
        return indentation;
    }

    /**
     * Increase the indentation of the next line.
     */
    public void indent() {
        checkOpen();
        stream.add(INDENT);
        indentation++;
        if (indentation > 100) {
            throw new TemplateFrameworkException("Indentation should not be too deep, is " + indentation);
        }
    }

    /**
     * Decrease the indentation of the next line.
     */
    public void outdent() {
        checkOpen();
        stream.add(OUTDENT);
        indentation--;
        if (indentation < 0) {
            throw new TemplateFrameworkException("Indentation should not become negative.");
        }
    }

    /**
     * Set the indentation to a specific depth.
     *
     * @param indentation The requested indentation depth.
     */
    public void setIndentation(int indentation) {
        checkOpen();
        if (indentation < 0 || indentation > 100) {
            throw new TemplateFrameworkException("Indentation unreasonable: " + indentation);
        }
        if (indentation == this.indentation) {
            // Nothing
        } else if (indentation == this.indentation + 1) {
            stream.add(INDENT);
            this.indentation++;
        } else if (indentation == this.indentation - 1) {
            stream.add(OUTDENT);
            this.indentation--;
        } else {
            stream.add(new MultiIndent(indentation - this.indentation));
            this.indentation = indentation;
        }
    }

    /**
     * Add the content of a {@link CodeStream} to the current {@link CodeStream}.
     *
     * @param other The stream to be added to this stream.
     */
    public void addCodeStream(CodeStream other) {
        checkOpen();
        other.checkClosed();
        stream.add(new NestedCodeStream(other));
    }

    /**
     * Prepend the content of a {@link CodeStream} to the current {@link CodeStream}.
     *
     * @param other The stream to be prepended to this stream.
     */
    public void prependCodeStream(CodeStream other) {
        checkOpen();
        other.checkClosed();
        stream.addFirst(new NestedCodeStream(other));
    }

    /**
     * Close the stream, after which no more code can be added, but after which this stream can be added to
     * other streams or converted to a String with {@link toString}.
     */
    public void close() {
        checkOpen();
        if (indentation != 0) {
            throw new TemplateFrameworkException("Indentation must be zero when closing, is " + indentation);
        }
        closed = true;
        checkClosed();
    }

    /**
     * Helper class for state for {@link String} generation in {@link toString}.
     */
    private final class CollectionState {
        private StringBuilder stringBuilder = new StringBuilder();
        private boolean lastWasNewline = false;
        private int indentation = 0;

        public CollectionState() {}

        public void addCodeToLine(String code) {
            // If we just had a newline, and we are now pushing code,
            // then we have to set the correct indentation.
            if (lastWasNewline) {
                stringBuilder.append(" ".repeat(4 * indentation));
            }
            stringBuilder.append(code);
            lastWasNewline = false;
        }

        public void addNewline() {
            stringBuilder.append("\n");
            lastWasNewline = true;
        }

        public void indent() {
            indentation++;
        }

        public void outdent() {
            indentation--;
        }

        public void multiIndent(int difference) {
            indentation += difference;
        }

        public String toString() {
            return stringBuilder.toString();
        }
    }

    /**
     * Collect a closed {@link CodeStream} to a {@link String}.
     */
    public String toString() {
        checkClosed();
        CollectionState state = new CollectionState();
        collect(state);
        return state.toString();
    }

    private void collect(CollectionState state) {
        for (Token t : stream) {
            switch (t) {
                case CodeSegment(String code)            -> { state.addCodeToLine(code); }
                case Newline()                           -> { state.addNewline();  }
                case Indent()                            -> { state.indent(); }
                case Outdent()                           -> { state.outdent(); }
                case MultiIndent(int difference)         -> { state.multiIndent(difference); }
                case NestedCodeStream(CodeStream stream) -> { stream.collect(state); }
            }
        }
    }
} 
