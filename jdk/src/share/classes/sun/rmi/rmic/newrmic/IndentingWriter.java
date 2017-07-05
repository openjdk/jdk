/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.rmi.rmic.newrmic;

import java.io.Writer;
import java.io.BufferedWriter;
import java.io.IOException;

/**
 * A BufferedWriter that supports automatic indentation of lines of
 * text written to the underlying Writer.
 *
 * Methods are provided for compact/convenient indenting in and out,
 * writing text, and writing lines of text in various combinations.
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 *
 * @author Peter Jones
 **/
public class IndentingWriter extends BufferedWriter {

    /** number of spaces to change indent when indenting in or out */
    private final int indentStep;

    /** number of spaces to convert into tabs (use MAX_VALUE to disable) */
    private final int tabSize;

    /** true if the next character written is the first on a line */
    private boolean beginningOfLine = true;

    /** current number of spaces to prepend to lines */
    private int currentIndent = 0;

    /**
     * Creates a new IndentingWriter that writes indented text to the
     * given Writer.  Use the default indent step of four spaces.
     **/
    public IndentingWriter(Writer out) {
        this(out, 4);
    }

    /**
     * Creates a new IndentingWriter that writes indented text to the
     * given Writer and uses the supplied indent step.
     **/
    public IndentingWriter(Writer out, int indentStep) {
        this(out, indentStep, 8);
    }

    /**
     * Creates a new IndentingWriter that writes indented text to the
     * given Writer and uses the supplied indent step and tab size.
     **/
    public IndentingWriter(Writer out, int indentStep, int tabSize) {
        super(out);
        if (indentStep < 0) {
            throw new IllegalArgumentException("negative indent step");
        }
        if (tabSize < 0) {
            throw new IllegalArgumentException("negative tab size");
        }
        this.indentStep = indentStep;
        this.tabSize = tabSize;
    }

    /**
     * Writes a single character.
     **/
    public void write(int c) throws IOException {
        checkWrite();
        super.write(c);
    }

    /**
     * Writes a portion of an array of characters.
     **/
    public void write(char[] cbuf, int off, int len) throws IOException {
        if (len > 0) {
            checkWrite();
        }
        super.write(cbuf, off, len);
    }

    /**
     * Writes a portion of a String.
     **/
    public void write(String s, int off, int len) throws IOException {
        if (len > 0) {
            checkWrite();
        }
        super.write(s, off, len);
    }

    /**
     * Writes a line separator.  The next character written will be
     * preceded by an indent.
     **/
    public void newLine() throws IOException {
        super.newLine();
        beginningOfLine = true;
    }

    /**
     * Checks if an indent needs to be written before writing the next
     * character.
     *
     * The indent generation is optimized (and made consistent with
     * certain coding conventions) by condensing groups of eight
     * spaces into tab characters.
     **/
    protected void checkWrite() throws IOException {
        if (beginningOfLine) {
            beginningOfLine = false;
            int i = currentIndent;
            while (i >= tabSize) {
                super.write('\t');
                i -= tabSize;
            }
            while (i > 0) {
                super.write(' ');
                i--;
            }
        }
    }

    /**
     * Increases the current indent by the indent step.
     **/
    protected void indentIn() {
        currentIndent += indentStep;
    }

    /**
     * Decreases the current indent by the indent step.
     **/
    protected void indentOut() {
        currentIndent -= indentStep;
        if (currentIndent < 0)
            currentIndent = 0;
    }

    /**
     * Indents in.
     **/
    public void pI() {
        indentIn();
    }

    /**
     * Indents out.
     **/
    public void pO() {
        indentOut();
    }

    /**
     * Writes string.
     **/
    public void p(String s) throws IOException {
        write(s);
    }

    /**
     * Ends current line.
     **/
    public void pln() throws IOException {
        newLine();
    }

    /**
     * Writes string; ends current line.
     **/
    public void pln(String s) throws IOException {
        p(s);
        pln();
    }

    /**
     * Writes string; ends current line; indents in.
     **/
    public void plnI(String s) throws IOException {
        p(s);
        pln();
        pI();
    }

    /**
     * Indents out; writes string.
     **/
    public void pO(String s) throws IOException {
        pO();
        p(s);
    }

    /**
     * Indents out; writes string; ends current line.
     **/
    public void pOln(String s) throws IOException {
        pO(s);
        pln();
    }

    /**
     * Indents out; writes string; ends current line; indents in.
     *
     * This method is useful for generating lines of code that both
     * end and begin nested blocks, like "} else {".
     **/
    public void pOlnI(String s) throws IOException {
        pO(s);
        pln();
        pI();
    }

    /**
     * Writes object.
     **/
    public void p(Object o) throws IOException {
        write(o.toString());
    }

    /**
     * Writes object; ends current line.
     **/
    public void pln(Object o) throws IOException {
        p(o.toString());
        pln();
    }

    /**
     * Writes object; ends current line; indents in.
     **/
    public void plnI(Object o) throws IOException {
        p(o.toString());
        pln();
        pI();
    }

    /**
     * Indents out; writes object.
     **/
    public void pO(Object o) throws IOException {
        pO();
        p(o.toString());
    }

    /**
     * Indents out; writes object; ends current line.
     **/
    public void pOln(Object o) throws IOException {
        pO(o.toString());
        pln();
    }

    /**
     * Indents out; writes object; ends current line; indents in.
     *
     * This method is useful for generating lines of code that both
     * end and begin nested blocks, like "} else {".
     **/
    public void pOlnI(Object o) throws IOException {
        pO(o.toString());
        pln();
        pI();
    }
}
