/*
 * Copyright (c) 2004, 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.javac.parser.Tokens.Comment;
import com.sun.tools.javac.parser.Tokens.Comment.CommentStyle;
import com.sun.tools.javac.util.*;

import java.nio.*;

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
public class JavadocTokenizer extends JavaTokenizer {

    /** Create a scanner from the input buffer.  buffer must implement
     *  array() and compact(), and remaining() must be less than limit().
     */
    protected JavadocTokenizer(ScannerFactory fac, CharBuffer buffer) {
        super(fac, buffer);
    }

    /** Create a scanner from the input array.  The array must have at
     *  least a single character of extra space.
     */
    protected JavadocTokenizer(ScannerFactory fac, char[] input, int inputLength) {
        super(fac, input, inputLength);
    }

    @Override
    protected Comment processComment(int pos, int endPos, CommentStyle style) {
        char[] buf = reader.getRawCharacters(pos, endPos);
        return new JavadocComment(new ColReader(fac, buf, buf.length), style);
    }

    /**
     * This is a specialized version of UnicodeReader that keeps track of the
     * column position within a given character stream (used for Javadoc processing).
     */
    static class ColReader extends UnicodeReader {

         int col;

         ColReader(ScannerFactory fac, char[] input, int inputLength) {
             super(fac, input, inputLength);
         }

         @Override
         protected void convertUnicode() {
             if (ch == '\\' && unicodeConversionBp != bp) {
                 bp++; ch = buf[bp]; col++;
                 if (ch == 'u') {
                     do {
                         bp++; ch = buf[bp]; col++;
                     } while (ch == 'u');
                     int limit = bp + 3;
                     if (limit < buflen) {
                         int d = digit(bp, 16);
                         int code = d;
                         while (bp < limit && d >= 0) {
                             bp++; ch = buf[bp]; col++;
                             d = digit(bp, 16);
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

         @Override
         protected void scanCommentChar() {
             scanChar();
             if (ch == '\\') {
                 if (peekChar() == '\\' && !isUnicode()) {
                     putChar(ch, false);
                     bp++; col++;
                 } else {
                     convertUnicode();
                 }
             }
         }

         @Override
         protected void scanChar() {
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
     }

     protected class JavadocComment extends JavaTokenizer.BasicComment<ColReader> {

        /**
        * Translated and stripped contents of doc comment
        */
        private String docComment = null;

        JavadocComment(ColReader comment_reader, CommentStyle cs) {
            super(comment_reader, cs);
        }

        public String getText() {
            if (!scanned && cs == CommentStyle.JAVADOC) {
                scanDocComment();
            }
            return docComment;
        }

        @Override
        @SuppressWarnings("fallthrough")
        protected void scanDocComment() {
             try {
                 boolean firstLine = true;

                 // Skip over first slash
                 comment_reader.scanCommentChar();
                 // Skip over first star
                 comment_reader.scanCommentChar();

                 // consume any number of stars
                 while (comment_reader.bp < comment_reader.buflen && comment_reader.ch == '*') {
                     comment_reader.scanCommentChar();
                 }
                 // is the comment in the form /**/, /***/, /****/, etc. ?
                 if (comment_reader.bp < comment_reader.buflen && comment_reader.ch == '/') {
                     docComment = "";
                     return;
                 }

                 // skip a newline on the first line of the comment.
                 if (comment_reader.bp < comment_reader.buflen) {
                     if (comment_reader.ch == LF) {
                         comment_reader.scanCommentChar();
                         firstLine = false;
                     } else if (comment_reader.ch == CR) {
                         comment_reader.scanCommentChar();
                         if (comment_reader.ch == LF) {
                             comment_reader.scanCommentChar();
                             firstLine = false;
                         }
                     }
                 }

             outerLoop:

                 // The outerLoop processes the doc comment, looping once
                 // for each line.  For each line, it first strips off
                 // whitespace, then it consumes any stars, then it
                 // puts the rest of the line into our buffer.
                 while (comment_reader.bp < comment_reader.buflen) {

                     // The wsLoop consumes whitespace from the beginning
                     // of each line.
                 wsLoop:

                     while (comment_reader.bp < comment_reader.buflen) {
                         switch(comment_reader.ch) {
                         case ' ':
                             comment_reader.scanCommentChar();
                             break;
                         case '\t':
                             comment_reader.col = ((comment_reader.col - 1) / TabInc * TabInc) + TabInc;
                             comment_reader.scanCommentChar();
                             break;
                         case FF:
                             comment_reader.col = 0;
                             comment_reader.scanCommentChar();
                             break;
         // Treat newline at beginning of line (blank line, no star)
         // as comment text.  Old Javadoc compatibility requires this.
         /*---------------------------------*
                         case CR: // (Spec 3.4)
                             doc_reader.scanCommentChar();
                             if (ch == LF) {
                                 col = 0;
                                 doc_reader.scanCommentChar();
                             }
                             break;
                         case LF: // (Spec 3.4)
                             doc_reader.scanCommentChar();
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
                     if (comment_reader.ch == '*') {
                         // skip all of the stars
                         do {
                             comment_reader.scanCommentChar();
                         } while (comment_reader.ch == '*');

                         // check for the closing slash.
                         if (comment_reader.ch == '/') {
                             // We're done with the doc comment
                             // scanChar() and breakout.
                             break outerLoop;
                         }
                     } else if (! firstLine) {
                         //The current line does not begin with a '*' so we will indent it.
                         for (int i = 1; i < comment_reader.col; i++) {
                             comment_reader.putChar(' ', false);
                         }
                     }
                     // The textLoop processes the rest of the characters
                     // on the line, adding them to our buffer.
                 textLoop:
                     while (comment_reader.bp < comment_reader.buflen) {
                         switch (comment_reader.ch) {
                         case '*':
                             // Is this just a star?  Or is this the
                             // end of a comment?
                             comment_reader.scanCommentChar();
                             if (comment_reader.ch == '/') {
                                 // This is the end of the comment,
                                 // set ch and return our buffer.
                                 break outerLoop;
                             }
                             // This is just an ordinary star.  Add it to
                             // the buffer.
                             comment_reader.putChar('*', false);
                             break;
                         case ' ':
                         case '\t':
                             comment_reader.putChar(comment_reader.ch, false);
                             comment_reader.scanCommentChar();
                             break;
                         case FF:
                             comment_reader.scanCommentChar();
                             break textLoop; // treat as end of line
                         case CR: // (Spec 3.4)
                             comment_reader.scanCommentChar();
                             if (comment_reader.ch != LF) {
                                 // Canonicalize CR-only line terminator to LF
                                 comment_reader.putChar((char)LF, false);
                                 break textLoop;
                             }
                             /* fall through to LF case */
                         case LF: // (Spec 3.4)
                             // We've seen a newline.  Add it to our
                             // buffer and break out of this loop,
                             // starting fresh on a new line.
                             comment_reader.putChar(comment_reader.ch, false);
                             comment_reader.scanCommentChar();
                             break textLoop;
                         default:
                             // Add the character to our buffer.
                             comment_reader.putChar(comment_reader.ch, false);
                             comment_reader.scanCommentChar();
                         }
                     } // end textLoop
                     firstLine = false;
                 } // end outerLoop

                 if (comment_reader.sp > 0) {
                     int i = comment_reader.sp - 1;
                 trailLoop:
                     while (i > -1) {
                         switch (comment_reader.sbuf[i]) {
                         case '*':
                             i--;
                             break;
                         default:
                             break trailLoop;
                         }
                     }
                     comment_reader.sp = i + 1;

                     // Store the text of the doc comment
                    docComment = comment_reader.chars();
                } else {
                    docComment = "";
                }
            } finally {
                scanned = true;
                if (docComment != null &&
                        docComment.matches("(?sm).*^\\s*@deprecated( |$).*")) {
                    deprecatedFlag = true;
                }
            }
        }
    }

    @Override
    public Position.LineMap getLineMap() {
        char[] buf = reader.getRawCharacters();
        return Position.makeLineMap(buf, buf.length, true);
    }
}
