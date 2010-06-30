/*
 * Copyright (c) 2004, 2008, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.parser;

import java.nio.*;

import com.sun.tools.javac.util.*;
import static com.sun.tools.javac.util.LayoutCharacters.*;

/** An extension to the base lexical analyzer that captures
 *  and processes the contents of doc comments.  It does so by
 *  translating Unicode escape sequences and by stripping the
 *  leading whitespace and starts from each line of the comment.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class DocCommentScanner extends Scanner {

    /** A factory for creating scanners. */
    public static class Factory extends Scanner.Factory {

        public static void preRegister(final Context context) {
            context.put(scannerFactoryKey, new Context.Factory<Scanner.Factory>() {
                public Factory make() {
                    return new Factory(context);
                }
            });
        }

        /** Create a new scanner factory. */
        protected Factory(Context context) {
            super(context);
        }

        @Override
        public Scanner newScanner(CharSequence input) {
            if (input instanceof CharBuffer) {
                return new DocCommentScanner(this, (CharBuffer)input);
            } else {
                char[] array = input.toString().toCharArray();
                return newScanner(array, array.length);
            }
        }

        @Override
        public Scanner newScanner(char[] input, int inputLength) {
            return new DocCommentScanner(this, input, inputLength);
        }
    }


    /** Create a scanner from the input buffer.  buffer must implement
     *  array() and compact(), and remaining() must be less than limit().
     */
    protected DocCommentScanner(Factory fac, CharBuffer buffer) {
        super(fac, buffer);
    }

    /** Create a scanner from the input array.  The array must have at
     *  least a single character of extra space.
     */
    protected DocCommentScanner(Factory fac, char[] input, int inputLength) {
        super(fac, input, inputLength);
    }

    /** Starting position of the comment in original source
     */
    private int pos;

    /** The comment input buffer, index of next chacter to be read,
     *  index of one past last character in buffer.
     */
    private char[] buf;
    private int bp;
    private int buflen;

    /** The current character.
     */
    private char ch;

    /** The column number position of the current character.
     */
    private int col;

    /** The buffer index of the last converted Unicode character
     */
    private int unicodeConversionBp = 0;

    /**
     * Buffer for doc comment.
     */
    private char[] docCommentBuffer = new char[1024];

    /**
     * Number of characters in doc comment buffer.
     */
    private int docCommentCount;

    /**
     * Translated and stripped contents of doc comment
     */
    private String docComment = null;


    /** Unconditionally expand the comment buffer.
     */
    private void expandCommentBuffer() {
        char[] newBuffer = new char[docCommentBuffer.length * 2];
        System.arraycopy(docCommentBuffer, 0, newBuffer,
                         0, docCommentBuffer.length);
        docCommentBuffer = newBuffer;
    }

    /** Convert an ASCII digit from its base (8, 10, or 16)
     *  to its value.
     */
    private int digit(int base) {
        char c = ch;
        int result = Character.digit(c, base);
        if (result >= 0 && c > 0x7f) {
            ch = "0123456789abcdef".charAt(result);
        }
        return result;
    }

    /** Convert Unicode escape; bp points to initial '\' character
     *  (Spec 3.3).
     */
    private void convertUnicode() {
        if (ch == '\\' && unicodeConversionBp != bp) {
            bp++; ch = buf[bp]; col++;
            if (ch == 'u') {
                do {
                    bp++; ch = buf[bp]; col++;
                } while (ch == 'u');
                int limit = bp + 3;
                if (limit < buflen) {
                    int d = digit(16);
                    int code = d;
                    while (bp < limit && d >= 0) {
                        bp++; ch = buf[bp]; col++;
                        d = digit(16);
                        code = (code << 4) + d;
                    }
                    if (d >= 0) {
                        ch = (char)code;
                        unicodeConversionBp = bp;
                        return;
                    }
                }
                // "illegal.Unicode.esc", reported by base scanner
            } else {
                bp--;
                ch = '\\';
                col--;
            }
        }
    }


    /** Read next character.
     */
    private void scanChar() {
        bp++;
        ch = buf[bp];
        switch (ch) {
        case '\r': // return
            col = 0;
            break;
        case '\n': // newline
            if (bp == 0 || buf[bp-1] != '\r') {
                col = 0;
            }
            break;
        case '\t': // tab
            col = (col / TabInc * TabInc) + TabInc;
            break;
        case '\\': // possible Unicode
            col++;
            convertUnicode();
            break;
        default:
            col++;
            break;
        }
    }

    /**
     * Read next character in doc comment, skipping over double '\' characters.
     * If a double '\' is skipped, put in the buffer and update buffer count.
     */
    private void scanDocCommentChar() {
        scanChar();
        if (ch == '\\') {
            if (buf[bp+1] == '\\' && unicodeConversionBp != bp) {
                if (docCommentCount == docCommentBuffer.length)
                    expandCommentBuffer();
                docCommentBuffer[docCommentCount++] = ch;
                bp++; col++;
            } else {
                convertUnicode();
            }
        }
    }

    /* Reset doc comment before reading each new token
     */
    public void nextToken() {
        docComment = null;
        super.nextToken();
    }

    /**
     * Returns the documentation string of the current token.
     */
    public String docComment() {
        return docComment;
    }

    /**
     * Process a doc comment and make the string content available.
     * Strips leading whitespace and stars.
     */
    @SuppressWarnings("fallthrough")
    protected void processComment(CommentStyle style) {
        if (style != CommentStyle.JAVADOC) {
            return;
        }

        pos = pos();
        buf = getRawCharacters(pos, endPos());
        buflen = buf.length;
        bp = 0;
        col = 0;

        docCommentCount = 0;

        boolean firstLine = true;

        // Skip over first slash
        scanDocCommentChar();
        // Skip over first star
        scanDocCommentChar();

        // consume any number of stars
        while (bp < buflen && ch == '*') {
            scanDocCommentChar();
        }
        // is the comment in the form /**/, /***/, /****/, etc. ?
        if (bp < buflen && ch == '/') {
            docComment = "";
            return;
        }

        // skip a newline on the first line of the comment.
        if (bp < buflen) {
            if (ch == LF) {
                scanDocCommentChar();
                firstLine = false;
            } else if (ch == CR) {
                scanDocCommentChar();
                if (ch == LF) {
                    scanDocCommentChar();
                    firstLine = false;
                }
            }
        }

    outerLoop:

        // The outerLoop processes the doc comment, looping once
        // for each line.  For each line, it first strips off
        // whitespace, then it consumes any stars, then it
        // puts the rest of the line into our buffer.
        while (bp < buflen) {

            // The wsLoop consumes whitespace from the beginning
            // of each line.
        wsLoop:

            while (bp < buflen) {
                switch(ch) {
                case ' ':
                    scanDocCommentChar();
                    break;
                case '\t':
                    col = ((col - 1) / TabInc * TabInc) + TabInc;
                    scanDocCommentChar();
                    break;
                case FF:
                    col = 0;
                    scanDocCommentChar();
                    break;
// Treat newline at beginning of line (blank line, no star)
// as comment text.  Old Javadoc compatibility requires this.
/*---------------------------------*
                case CR: // (Spec 3.4)
                    scanDocCommentChar();
                    if (ch == LF) {
                        col = 0;
                        scanDocCommentChar();
                    }
                    break;
                case LF: // (Spec 3.4)
                    scanDocCommentChar();
                    break;
*---------------------------------*/
                default:
                    // we've seen something that isn't whitespace;
                    // jump out.
                    break wsLoop;
                }
            }

            // Are there stars here?  If so, consume them all
            // and check for the end of comment.
            if (ch == '*') {
                // skip all of the stars
                do {
                    scanDocCommentChar();
                } while (ch == '*');

                // check for the closing slash.
                if (ch == '/') {
                    // We're done with the doc comment
                    // scanChar() and breakout.
                    break outerLoop;
                }
            } else if (! firstLine) {
                //The current line does not begin with a '*' so we will indent it.
                for (int i = 1; i < col; i++) {
                    if (docCommentCount == docCommentBuffer.length)
                        expandCommentBuffer();
                    docCommentBuffer[docCommentCount++] = ' ';
                }
            }

            // The textLoop processes the rest of the characters
            // on the line, adding them to our buffer.
        textLoop:
            while (bp < buflen) {
                switch (ch) {
                case '*':
                    // Is this just a star?  Or is this the
                    // end of a comment?
                    scanDocCommentChar();
                    if (ch == '/') {
                        // This is the end of the comment,
                        // set ch and return our buffer.
                        break outerLoop;
                    }
                    // This is just an ordinary star.  Add it to
                    // the buffer.
                    if (docCommentCount == docCommentBuffer.length)
                        expandCommentBuffer();
                    docCommentBuffer[docCommentCount++] = '*';
                    break;
                case ' ':
                case '\t':
                    if (docCommentCount == docCommentBuffer.length)
                        expandCommentBuffer();
                    docCommentBuffer[docCommentCount++] = ch;
                    scanDocCommentChar();
                    break;
                case FF:
                    scanDocCommentChar();
                    break textLoop; // treat as end of line
                case CR: // (Spec 3.4)
                    scanDocCommentChar();
                    if (ch != LF) {
                        // Canonicalize CR-only line terminator to LF
                        if (docCommentCount == docCommentBuffer.length)
                            expandCommentBuffer();
                        docCommentBuffer[docCommentCount++] = (char)LF;
                        break textLoop;
                    }
                    /* fall through to LF case */
                case LF: // (Spec 3.4)
                    // We've seen a newline.  Add it to our
                    // buffer and break out of this loop,
                    // starting fresh on a new line.
                    if (docCommentCount == docCommentBuffer.length)
                        expandCommentBuffer();
                    docCommentBuffer[docCommentCount++] = ch;
                    scanDocCommentChar();
                    break textLoop;
                default:
                    // Add the character to our buffer.
                    if (docCommentCount == docCommentBuffer.length)
                        expandCommentBuffer();
                    docCommentBuffer[docCommentCount++] = ch;
                    scanDocCommentChar();
                }
            } // end textLoop
            firstLine = false;
        } // end outerLoop

        if (docCommentCount > 0) {
            int i = docCommentCount - 1;
        trailLoop:
            while (i > -1) {
                switch (docCommentBuffer[i]) {
                case '*':
                    i--;
                    break;
                default:
                    break trailLoop;
                }
            }
            docCommentCount = i + 1;

            // Store the text of the doc comment
            docComment = new String(docCommentBuffer, 0 , docCommentCount);
        } else {
            docComment = "";
        }
    }

    /** Build a map for translating between line numbers and
     * positions in the input.
     *
     * @return a LineMap */
    public Position.LineMap getLineMap() {
        char[] buf = getRawCharacters();
        return Position.makeLineMap(buf, buf.length, true);
    }
}
