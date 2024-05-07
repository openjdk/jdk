/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 4185537
 * @summary  javax.swing.text.AbstractWriter: TOTAL REWRITE COMPLETE.
 */

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import javax.swing.text.AbstractWriter;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;

public class bug4185537 {
    static char[] chars = {'a', 'b', 'c', 'd', 'e'};
    static StringWriter wr = new StringWriter();

    public static void main(String[] argv) {
        DefaultStyledDocument doc = new DefaultStyledDocument();

        SimpleWriter sw = new SimpleWriter(wr, doc, 5, 200);
        sw.test_getWriter();

        if (sw.getStartOffset() != 5) {
            throw new RuntimeException("getStartOffset fails...");
        }
        if (sw.getEndOffset() != 205) {
            throw new RuntimeException("getEndOffset fails...");
        }

        sw.setLineSeparator("+");
        if (!sw.getLineSeparator().equals("+")) {
            throw new RuntimeException("Doesn't set line separator correctly...");
        }
        sw.test_writeLineSeparator();

        sw.test_write_output();
        sw.test_CurrentLineLength();
        sw.test_getLineLength();
        sw.test_getIndentLevel();
        sw.test_CanWrapLines();
        if (!wr.toString().equals("+abcde")) {
            throw new RuntimeException("Test fails...");
        }
        try {
            wr.close();
        } catch (Exception e) {
            System.out.println("Exception...");
        }
    }

    static class SimpleWriter extends AbstractWriter {

        public SimpleWriter(Writer w, Document d, int pos, int len) {
            super(w, d, pos, len);
        }

        void test_writeLineSeparator() {
            try {
                writeLineSeparator();
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException("writeLineSeparator fails...");
            }
        }

        void test_getWriter() {
            if (getWriter() != wr) throw new RuntimeException("Writer gets incorrect...");
        }

        void test_CurrentLineLength() {
            setCurrentLineLength(0);
            if (getCurrentLineLength() != 0) throw new RuntimeException("Doesn't set CurrentLineLength...");
            if (!isLineEmpty()) {
                throw new RuntimeException("isLineEmpty() should return false...");
            }
        }

        void test_getLineLength() {
            setLineLength(80);
            if (getLineLength() != 80) {
                throw new RuntimeException("Default line length doesn't set...");
            }
        }

        void test_CanWrapLines() {
            setCanWrapLines(true);
            if (!getCanWrapLines()) {
                throw new RuntimeException("Doesn't set wrapping lines correctly");
            }
        }

        void test_getIndentLevel() {
            incrIndent();
            if (getIndentLevel() != 1) {
                throw new RuntimeException("getIndentLevel() fails...");
            }
        }

        void test_write_output() {
            try {
                write(chars, 0, 3);
            } catch (IOException e) {
                throw new RuntimeException("write(char[], int, int): unexpected IOException...");
            }
            try {
                output(chars, 3, 2);
            } catch (IOException e) {
                throw new RuntimeException("output(char[], int, int): unexpected IOException...");
            }
        }

        public void write() throws IOException, BadLocationException {
        }
    }
}
