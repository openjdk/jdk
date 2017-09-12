/*
 * Copyright (c) 1995, 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.java;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.FilterReader;
import java.io.UnsupportedEncodingException;

/**
 * An input stream for java programs. The stream treats either "\n", "\r"
 * or "\r\n" as the end of a line, it always returns \n. It also parses
 * UNICODE characters expressed as \uffff. However, if it sees "\\", the
 * second slash cannot begin a unicode sequence. It keeps track of the current
 * position in the input stream.
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 *
 * @author      Arthur van Hoff
 */

public
class ScannerInputReader extends FilterReader implements Constants {
    // A note.  This class does not really properly subclass FilterReader.
    // Since this class only overrides the single character read method,
    // and not the multi-character read method, any use of the latter
    // will not work properly.  Any attempt to use this code outside of
    // the compiler should take that into account.
    //
    // For efficiency, it might be worth moving this code to Scanner and
    // getting rid of this class.

    Environment env;
    long pos;

    private long chpos;
    private int pushBack = -1;

    public ScannerInputReader(Environment env, InputStream in)
        throws UnsupportedEncodingException
    {
        // ScannerInputStream has been modified to no longer use
        // BufferedReader.  It now does its own buffering for
        // performance.
        super(env.getCharacterEncoding() != null ?
              new InputStreamReader(in, env.getCharacterEncoding()) :
              new InputStreamReader(in));

        // Start out the buffer empty.
        currentIndex = 0;
        numChars = 0;

        this.env = env;
        chpos = Scanner.LINEINC;
    }

    //------------------------------------------------------------
    // Buffering code.

    // The size of our buffer.
    private static final int BUFFERLEN = 10 * 1024;

    // A character buffer.
    private final char[] buffer = new char[BUFFERLEN];

    // The index of the next character to be "read" from the buffer.
    private int currentIndex;

    // The number of characters in the buffer.  -1 if EOF is reached.
    private int numChars;

    /**
     * Get the next character from our buffer.
     * Note: this method has been inlined by hand in the `read' method
     * below.  Any changes made to this method should be equally applied
     * to that code.
     */
    private int getNextChar() throws IOException {
        // Check to see if we have either run out of characters in our
        // buffer or gotten to EOF on a previous call.
        if (currentIndex >= numChars) {
            numChars = in.read(buffer);
            if (numChars == -1) {
                // We have reached EOF.
                return -1;
            }

            // No EOF.  currentIndex points to first char in buffer.
            currentIndex = 0;
        }

        return buffer[currentIndex++];
    }

    //------------------------------------------------------------

    public int read(char[] buffer, int off, int len) {
        throw new CompilerError(
                   "ScannerInputReader is not a fully implemented reader.");
    }

    public int read() throws IOException {
        pos = chpos;
        chpos += Scanner.OFFSETINC;

        int c = pushBack;
        if (c == -1) {
        getchar: try {
                // Here the call...
                //     c = getNextChar();
                // has been inlined by hand for performance.

                if (currentIndex >= numChars) {
                    numChars = in.read(buffer);
                    if (numChars == -1) {
                        // We have reached EOF.
                        c = -1;
                        break getchar;
                    }

                    // No EOF.  currentIndex points to first char in buffer.
                    currentIndex = 0;
                }
                c = buffer[currentIndex++];

            } catch (java.io.CharConversionException e) {
                env.error(pos, "invalid.encoding.char");
                // this is fatal error
                return -1;
            }
        } else {
            pushBack = -1;
        }

        // parse special characters
        switch (c) {
          case -2:
            // -2 is a special code indicating a pushback of a backslash that
            // definitely isn't the start of a unicode sequence.
            return '\\';

          case '\\':
            if ((c = getNextChar()) != 'u') {
                pushBack = (c == '\\' ? -2 : c);
                return '\\';
            }
            // we have a unicode sequence
            chpos += Scanner.OFFSETINC;
            while ((c = getNextChar()) == 'u') {
                chpos += Scanner.OFFSETINC;
            }

            // unicode escape sequence
            int d = 0;
            for (int i = 0 ; i < 4 ; i++, chpos += Scanner.OFFSETINC, c = getNextChar()) {
                switch (c) {
                  case '0': case '1': case '2': case '3': case '4':
                  case '5': case '6': case '7': case '8': case '9':
                    d = (d << 4) + c - '0';
                    break;

                  case 'a': case 'b': case 'c': case 'd': case 'e': case 'f':
                    d = (d << 4) + 10 + c - 'a';
                    break;

                  case 'A': case 'B': case 'C': case 'D': case 'E': case 'F':
                    d = (d << 4) + 10 + c - 'A';
                    break;

                  default:
                    env.error(pos, "invalid.escape.char");
                    pushBack = c;
                    return d;
                }
            }
            pushBack = c;

            // To read the following line, switch \ and /...
            // Handle /u000a, /u000A, /u000d, /u000D properly as
            // line terminators as per JLS 3.4, even though they are encoded
            // (this properly respects the order given in JLS 3.2).
            switch (d) {
                case '\n':
                   chpos += Scanner.LINEINC;
                    return '\n';
                case '\r':
                    if ((c = getNextChar()) != '\n') {
                        pushBack = c;
                    } else {
                        chpos += Scanner.OFFSETINC;
                    }
                    chpos += Scanner.LINEINC;
                    return '\n';
                default:
                    return d;
            }

          case '\n':
            chpos += Scanner.LINEINC;
            return '\n';

          case '\r':
            if ((c = getNextChar()) != '\n') {
                pushBack = c;
            } else {
                chpos += Scanner.OFFSETINC;
            }
            chpos += Scanner.LINEINC;
            return '\n';

          default:
            return c;
        }
    }
}
