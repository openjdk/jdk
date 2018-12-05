/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal.tool;

import java.io.PrintWriter;

abstract class StructuredWriter {
    private final static String LINE_SEPARATOR = String.format("%n");

    private final PrintWriter out;
    private final StringBuilder builder = new StringBuilder(4000);

    private char[] indentionArray = new char[0];
    private int indent = 0;
    private int column;
    // print first event immediately so tool feels responsive
    private boolean first = true;

    StructuredWriter(PrintWriter p) {
        out = p;
    }

    final protected int getColumn() {
        return column;
    }

    // Flush to print writer
    public final void flush(boolean hard) {
        if (hard) {
            out.print(builder.toString());
            builder.setLength(0);
            return;
        }
        if (first || builder.length() > 100_000) {
            out.print(builder.toString());
            builder.setLength(0);
            first = false;
        }
    }

    final public void printIndent() {
        builder.append(indentionArray, 0, indent);
        column += indent;
    }

    final public void println() {
        builder.append(LINE_SEPARATOR);
        column = 0;
    }

    final public void print(String... texts) {
        for (String text : texts) {
            print(text);
        }
    }

    final public void printAsString(Object o) {
        print(String.valueOf(o));
    }

    final public void print(String text) {
        builder.append(text);
        column += text.length();
    }

    final public void print(char c) {
        builder.append(c);
        column++;
    }

    final public void print(int value) {
        print(String.valueOf(value));
    }

    final public void indent() {
        indent += 2;
        updateIndent();
    }

    final public void retract() {
        indent -= 2;
        updateIndent();
    }

    final public void println(String text) {
        print(text);
        println();
    }

    private void updateIndent() {
        if (indent > indentionArray.length) {
            indentionArray = new char[indent];
            for (int i = 0; i < indentionArray.length; i++) {
                indentionArray[i] = ' ';
            }
        }
    }
}
