/*
 * Copyright (c) 1994, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Hashtable;

/**
 * A Scanner for Java tokens. Errors are reported
 * to the environment object.<p>
 *
 * The scanner keeps track of the current token,
 * the value of the current token (if any), and the start
 * position of the current token.<p>
 *
 * The scan() method advances the scanner to the next
 * token in the input.<p>
 *
 * The match() method is used to quickly match opening
 * brackets (ie: '(', '{', or '[') with their closing
 * counter part. This is useful during error recovery.<p>
 *
 * An position consists of: ((linenr << WHEREOFFSETBITS) | offset)
 * this means that both the line number and the exact offset into
 * the file are encoded in each position value.<p>
 *
 * The compiler treats either "\n", "\r" or "\r\n" as the
 * end of a line.<p>
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 *
 * @author      Arthur van Hoff
 */

@SuppressWarnings("deprecation")
public
class Scanner implements Constants {
    /**
     * The increment for each character.
     */
    public static final long OFFSETINC = 1;

    /**
     * The increment for each line.
     */
    public static final long LINEINC = 1L << WHEREOFFSETBITS;

    /**
     * End of input
     */
    public static final int EOF = -1;

    /**
     * Where errors are reported
     */
    public Environment env;

    /**
     * Input reader
     */
    protected ScannerInputReader in;

    /**
     * If true, present all comments as tokens.
     * Contents are not saved, but positions are recorded accurately,
     * so the comment can be recovered from the text.
     * Line terminations are also returned as comment tokens,
     * and may be distinguished by their start and end positions,
     * which are equal (meaning, these tokens contain no chars).
     */
   public boolean scanComments = false;

    /**
     * Current token
     */
    public int token;

    /**
     * The position of the current token
     */
    public long pos;

    /**
     * The position of the previous token
     */
    public long prevPos;

    /**
     * The current character
     */
    protected int ch;

    /*
     * Token values.
     */
    public char charValue;
    public int intValue;
    public long longValue;
    public float floatValue;
    public double doubleValue;
    public String stringValue;
    public Identifier idValue;
    public int radix;   // Radix, when reading int or long

    /*
     * A doc comment preceding the most recent token
     */
    public String docComment;

    /*
     * A growable character buffer.
     */
    private int count;
    private char buffer[] = new char[1024];
    private void growBuffer() {
        char newBuffer[] = new char[buffer.length * 2];
        System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
        buffer = newBuffer;
    }

    // The following two methods have been hand-inlined in
    // scanDocComment.  If you make changes here, you should
    // check to see if scanDocComment also needs modification.
    private void putc(int ch) {
        if (count == buffer.length) {
            growBuffer();
        }
        buffer[count++] = (char)ch;
    }

    private String bufferString() {
        return new String(buffer, 0, count);
    }

    /**
     * Create a scanner to scan an input stream.
     */
    public Scanner(Environment env, InputStream in) throws IOException {
        this.env = env;
        useInputStream(in);
    }

    /**
     * Setup input from the given input stream,
     * and scan the first token from it.
     */
    protected void useInputStream(InputStream in) throws IOException {
        try {
            this.in = new ScannerInputReader(env, in);
        } catch (Exception e) {
            env.setCharacterEncoding(null);
            this.in = new ScannerInputReader(env, in);
        }

        ch = this.in.read();
        prevPos = this.in.pos;

        scan();
    }

    /**
     * Create a scanner to scan an input stream.
     */
    protected Scanner(Environment env) {
        this.env = env;
        // Expect the subclass to call useInputStream at the right time.
    }

    /**
     * Define a keyword.
     */
    private static void defineKeyword(int val) {
        Identifier.lookup(opNames[val]).setType(val);
    }

    /**
     * Initialized keyword and token Hashtables
     */
    static {
        // Statement keywords
        defineKeyword(FOR);
        defineKeyword(IF);
        defineKeyword(ELSE);
        defineKeyword(WHILE);
        defineKeyword(DO);
        defineKeyword(SWITCH);
        defineKeyword(CASE);
        defineKeyword(DEFAULT);
        defineKeyword(BREAK);
        defineKeyword(CONTINUE);
        defineKeyword(RETURN);
        defineKeyword(TRY);
        defineKeyword(CATCH);
        defineKeyword(FINALLY);
        defineKeyword(THROW);

        // Type defineKeywords
        defineKeyword(BYTE);
        defineKeyword(CHAR);
        defineKeyword(SHORT);
        defineKeyword(INT);
        defineKeyword(LONG);
        defineKeyword(FLOAT);
        defineKeyword(DOUBLE);
        defineKeyword(VOID);
        defineKeyword(BOOLEAN);

        // Expression keywords
        defineKeyword(INSTANCEOF);
        defineKeyword(TRUE);
        defineKeyword(FALSE);
        defineKeyword(NEW);
        defineKeyword(THIS);
        defineKeyword(SUPER);
        defineKeyword(NULL);

        // Declaration keywords
        defineKeyword(IMPORT);
        defineKeyword(CLASS);
        defineKeyword(EXTENDS);
        defineKeyword(IMPLEMENTS);
        defineKeyword(INTERFACE);
        defineKeyword(PACKAGE);
        defineKeyword(THROWS);

        // Modifier keywords
        defineKeyword(PRIVATE);
        defineKeyword(PUBLIC);
        defineKeyword(PROTECTED);
        defineKeyword(STATIC);
        defineKeyword(TRANSIENT);
        defineKeyword(SYNCHRONIZED);
        defineKeyword(NATIVE);
        defineKeyword(ABSTRACT);
        defineKeyword(VOLATILE);
        defineKeyword(FINAL);
        defineKeyword(STRICTFP);

        // reserved keywords
        defineKeyword(CONST);
        defineKeyword(GOTO);
    }

    /**
     * Scan a comment. This method should be
     * called once the initial /, * and the next
     * character have been read.
     */
    private void skipComment() throws IOException {
        while (true) {
            switch (ch) {
              case EOF:
                env.error(pos, "eof.in.comment");
                return;

              case '*':
                if ((ch = in.read()) == '/')  {
                    ch = in.read();
                    return;
                }
                break;

              default:
                ch = in.read();
                break;
            }
        }
    }

    /**
     * Scan a doc comment. This method should be called
     * once the initial /, * and * have been read. It gathers
     * the content of the comment (witout leading spaces and '*'s)
     * in the string buffer.
     */
    private String scanDocComment() throws IOException {
        // Note: this method has been hand-optimized to yield
        // better performance.  This was done after it was noted
        // that javadoc spent a great deal of its time here.
        // This should also help the performance of the compiler
        // as well -- it scans the doc comments to find
        // @deprecated tags.
        //
        // The logic of the method has been completely rewritten
        // to avoid the use of flags that need to be looked at
        // for every character read.  Members that are accessed
        // more than once have been stored in local variables.
        // The methods putc() and bufferString() have been
        // inlined by hand.  Extra cases have been added to
        // switch statements to trick the compiler into generating
        // a tableswitch instead of a lookupswitch.
        //
        // This implementation aims to preserve the previous
        // behavior of this method.

        int c;

        // Put `in' in a local variable.
        final ScannerInputReader in = this.in;

        // We maintain the buffer locally rather than calling putc().
        char[] buffer = this.buffer;
        int count = 0;

        // We are called pointing at the second star of the doc
        // comment:
        //
        // Input: /** the rest of the comment ... */
        //          ^
        //
        // We rely on this in the code below.

        // Consume any number of stars.
        while ((c = in.read()) == '*')
            ;

        // Is the comment of the form /**/, /***/, /****/, etc.?
        if (c == '/') {
            // Set ch and return
            ch = in.read();
            return "";
        }

        // Skip a newline on the first line of the comment.
        if (c == '\n') {
            c = in.read();
        }

    outerLoop:
        // The outerLoop processes the doc comment, looping once
        // for each line.  For each line, it first strips off
        // whitespace, then it consumes any stars, then it
        // puts the rest of the line into our buffer.
        while (true) {

            // The wsLoop consumes whitespace from the beginning
            // of each line.
        wsLoop:
            while (true) {
                switch (c) {
                case ' ':
                case '\t':
                    // We could check for other forms of whitespace
                    // as well, but this is left as is for minimum
                    // disturbance of functionality.
                    //
                    // Just skip whitespace.
                    c = in.read();
                    break;

                // We have added extra cases here to trick the
                // compiler into using a tableswitch instead of
                // a lookupswitch.  They can be removed without
                // a change in meaning.
                case 10: case 11: case 12: case 13: case 14: case 15:
                case 16: case 17: case 18: case 19: case 20: case 21:
                case 22: case 23: case 24: case 25: case 26: case 27:
                case 28: case 29: case 30: case 31:
                default:
                    // We've seen something that isn't whitespace,
                    // jump out.
                    break wsLoop;
                }
            } // end wsLoop.

            // Are there stars here?  If so, consume them all
            // and check for the end of comment.
            if (c == '*') {
                // Skip all of the stars...
                do {
                    c = in.read();
                } while (c == '*');

                // ...then check for the closing slash.
                if (c == '/') {
                    // We're done with the doc comment.
                    // Set ch and break out.
                    ch = in.read();
                    break outerLoop;
                }
            }

            // The textLoop processes the rest of the characters
            // on the line, adding them to our buffer.
        textLoop:
            while (true) {
                switch (c) {
                case EOF:
                    // We've seen a premature EOF.  Break out
                    // of the loop.
                    env.error(pos, "eof.in.comment");
                    ch = EOF;
                    break outerLoop;

                case '*':
                    // Is this just a star?  Or is this the
                    // end of a comment?
                    c = in.read();
                    if (c == '/') {
                        // This is the end of the comment,
                        // set ch and return our buffer.
                        ch = in.read();
                        break outerLoop;
                    }
                    // This is just an ordinary star.  Add it to
                    // the buffer.
                    if (count == buffer.length) {
                        growBuffer();
                        buffer = this.buffer;
                    }
                    buffer[count++] = '*';
                    break;

                case '\n':
                    // We've seen a newline.  Add it to our
                    // buffer and break out of this loop,
                    // starting fresh on a new line.
                    if (count == buffer.length) {
                        growBuffer();
                        buffer = this.buffer;
                    }
                    buffer[count++] = '\n';
                    c = in.read();
                    break textLoop;

                // Again, the extra cases here are a trick
                // to get the compiler to generate a tableswitch.
                case 0: case 1: case 2: case 3: case 4: case 5:
                case 6: case 7: case 8: case 11: case 12: case 13:
                case 14: case 15: case 16: case 17: case 18: case 19:
                case 20: case 21: case 22: case 23: case 24: case 25:
                case 26: case 27: case 28: case 29: case 30: case 31:
                case 32: case 33: case 34: case 35: case 36: case 37:
                case 38: case 39: case 40:
                default:
                    // Add the character to our buffer.
                    if (count == buffer.length) {
                        growBuffer();
                        buffer = this.buffer;
                    }
                    buffer[count++] = (char)c;
                    c = in.read();
                    break;
                }
            } // end textLoop
        } // end outerLoop

        // We have scanned our doc comment.  It is stored in
        // buffer.  The previous implementation of scanDocComment
        // stripped off all trailing spaces and stars from the comment.
        // We will do this as well, so as to cause a minimum of
        // disturbance.  Is this what we want?
        if (count > 0) {
            int i = count - 1;
        trailLoop:
            while (i > -1) {
                switch (buffer[i]) {
                case ' ':
                case '\t':
                case '*':
                    i--;
                    break;
                // And again, the extra cases here are a trick
                // to get the compiler to generate a tableswitch.
                case 0: case 1: case 2: case 3: case 4: case 5:
                case 6: case 7: case 8: case 10: case 11: case 12:
                case 13: case 14: case 15: case 16: case 17: case 18:
                case 19: case 20: case 21: case 22: case 23: case 24:
                case 25: case 26: case 27: case 28: case 29: case 30:
                case 31: case 33: case 34: case 35: case 36: case 37:
                case 38: case 39: case 40:
                default:
                    break trailLoop;
                }
            }
            count = i + 1;

            // Return the text of the doc comment.
            return new String(buffer, 0, count);
        } else {
            return "";
        }
    }

    /**
     * Scan a number. The first digit of the number should be the current
     * character.  We may be scanning hex, decimal, or octal at this point
     */
    @SuppressWarnings("fallthrough")
    private void scanNumber() throws IOException {
        boolean seenNonOctal = false;
        boolean overflow = false;
        boolean seenDigit = false; // used to detect invalid hex number 0xL
        radix = (ch == '0' ? 8 : 10);
        long value = ch - '0';
        count = 0;
        putc(ch);               // save character in buffer
    numberLoop:
        for (;;) {
            switch (ch = in.read()) {
              case '.':
                if (radix == 16)
                    break numberLoop; // an illegal character
                scanReal();
                return;

              case '8': case '9':
                // We can't yet throw an error if reading an octal.  We might
                // discover we're really reading a real.
                seenNonOctal = true;
                // Fall through
              case '0': case '1': case '2': case '3':
              case '4': case '5': case '6': case '7':
                seenDigit = true;
                putc(ch);
                if (radix == 10) {
                    overflow = overflow || (value * 10)/10 != value;
                    value = (value * 10) + (ch - '0');
                    overflow = overflow || (value - 1 < -1);
                } else if (radix == 8) {
                    overflow = overflow || (value >>> 61) != 0;
                    value = (value << 3) + (ch - '0');
                } else {
                    overflow = overflow || (value >>> 60) != 0;
                    value = (value << 4) + (ch - '0');
                }
                break;

              case 'd': case 'D': case 'e': case 'E': case 'f': case 'F':
                if (radix != 16) {
                    scanReal();
                    return;
                }
                // fall through
              case 'a': case 'A': case 'b': case 'B': case 'c': case 'C':
                seenDigit = true;
                putc(ch);
                if (radix != 16)
                    break numberLoop; // an illegal character
                overflow = overflow || (value >>> 60) != 0;
                value = (value << 4) + 10 +
                         Character.toLowerCase((char)ch) - 'a';
                break;

              case 'l': case 'L':
                ch = in.read(); // skip over 'l'
                longValue = value;
                token = LONGVAL;
                break numberLoop;

              case 'x': case 'X':
                // if the first character is a '0' and this is the second
                // letter, then read in a hexadecimal number.  Otherwise, error.
                if (count == 1 && radix == 8) {
                    radix = 16;
                    seenDigit = false;
                    break;
                } else {
                    // we'll get an illegal character error
                    break numberLoop;
                }

              default:
                intValue = (int)value;
                token = INTVAL;
                break numberLoop;
            }
        } // while true

        // We have just finished reading the number.  The next thing better
        // not be a letter or digit.
        // Note:  There will be deprecation warnings against these uses
        // of Character.isJavaLetterOrDigit and Character.isJavaLetter.
        // Do not fix them yet; allow the compiler to run on pre-JDK1.1 VMs.
        if (Character.isJavaLetterOrDigit((char)ch) || ch == '.') {
            env.error(in.pos, "invalid.number");
            do { ch = in.read(); }
            while (Character.isJavaLetterOrDigit((char)ch) || ch == '.');
            intValue = 0;
            token = INTVAL;
        } else if (radix == 8 && seenNonOctal) {
            // A bogus octal literal.
            intValue = 0;
            token = INTVAL;
            env.error(pos, "invalid.octal.number");
        } else if (radix == 16 && seenDigit == false) {
            // A hex literal with no digits, 0xL, for example.
            intValue = 0;
            token = INTVAL;
            env.error(pos, "invalid.hex.number");
        } else {
            if (token == INTVAL) {
                // Check for overflow.  Note that base 10 literals
                // have different rules than base 8 and 16.
                overflow = overflow ||
                    (value & 0xFFFFFFFF00000000L) != 0 ||
                    (radix == 10 && value > 2147483648L);

                if (overflow) {
                    intValue = 0;

                    // Give a specific error message which tells
                    // the user the range.
                    switch (radix) {
                    case 8:
                        env.error(pos, "overflow.int.oct");
                        break;
                    case 10:
                        env.error(pos, "overflow.int.dec");
                        break;
                    case 16:
                        env.error(pos, "overflow.int.hex");
                        break;
                    default:
                        throw new CompilerError("invalid radix");
                    }
                }
            } else {
                if (overflow) {
                    longValue = 0;

                    // Give a specific error message which tells
                    // the user the range.
                    switch (radix) {
                    case 8:
                        env.error(pos, "overflow.long.oct");
                        break;
                    case 10:
                        env.error(pos, "overflow.long.dec");
                        break;
                    case 16:
                        env.error(pos, "overflow.long.hex");
                        break;
                    default:
                        throw new CompilerError("invalid radix");
                    }
                }
            }
        }
    }

    /**
     * Scan a float.  We are either looking at the decimal, or we have already
     * seen it and put it into the buffer.  We haven't seen an exponent.
     * Scan a float.  Should be called with the current character is either
     * the 'e', 'E' or '.'
     */
    @SuppressWarnings("fallthrough")
    private void scanReal() throws IOException {
        boolean seenExponent = false;
        boolean isSingleFloat = false;
        char lastChar;
        if (ch == '.') {
            putc(ch);
            ch = in.read();
        }

    numberLoop:
        for ( ; ; ch = in.read()) {
            switch (ch) {
                case '0': case '1': case '2': case '3': case '4':
                case '5': case '6': case '7': case '8': case '9':
                    putc(ch);
                    break;

                case 'e': case 'E':
                    if (seenExponent)
                        break numberLoop; // we'll get a format error
                    putc(ch);
                    seenExponent = true;
                    break;

                case '+': case '-':
                    lastChar = buffer[count - 1];
                    if (lastChar != 'e' && lastChar != 'E')
                        break numberLoop; // this isn't an error, though!
                    putc(ch);
                    break;

                case 'f': case 'F':
                    ch = in.read(); // skip over 'f'
                    isSingleFloat = true;
                    break numberLoop;

                case 'd': case 'D':
                    ch = in.read(); // skip over 'd'
                    // fall through
                default:
                    break numberLoop;
            } // sswitch
        } // loop

        // we have just finished reading the number.  The next thing better
        // not be a letter or digit.
        if (Character.isJavaLetterOrDigit((char)ch) || ch == '.') {
            env.error(in.pos, "invalid.number");
            do { ch = in.read(); }
            while (Character.isJavaLetterOrDigit((char)ch) || ch == '.');
            doubleValue = 0;
            token = DOUBLEVAL;
        } else {
            token = isSingleFloat ? FLOATVAL : DOUBLEVAL;
            try {
                lastChar = buffer[count - 1];
                if (lastChar == 'e' || lastChar == 'E'
                       || lastChar == '+' || lastChar == '-') {
                    env.error(in.pos -1, "float.format");
                } else if (isSingleFloat) {
                    String string = bufferString();
                    floatValue = Float.valueOf(string).floatValue();
                    if (Float.isInfinite(floatValue)) {
                        env.error(pos, "overflow.float");
                    } else if (floatValue == 0 && !looksLikeZero(string)) {
                        env.error(pos, "underflow.float");
                    }
                } else {
                    String string = bufferString();
                    doubleValue = Double.valueOf(string).doubleValue();
                    if (Double.isInfinite(doubleValue)) {
                        env.error(pos, "overflow.double");
                    } else if (doubleValue == 0 && !looksLikeZero(string)) {
                        env.error(pos, "underflow.double");
                    }
                }
            } catch (NumberFormatException ee) {
                env.error(pos, "float.format");
                doubleValue = 0;
                floatValue = 0;
            }
        }
        return;
    }

    // We have a token that parses as a number.  Is this token possibly zero?
    // i.e. does it have a non-zero value in the mantissa?
    private static boolean looksLikeZero(String token) {
        int length = token.length();
        for (int i = 0; i < length; i++) {
            switch (token.charAt(i)) {
                case 0: case '.':
                    continue;
                case '1': case '2': case '3': case '4': case '5':
                case '6': case '7': case '8': case '9':
                    return false;
                case 'e': case 'E': case 'f': case 'F':
                    return true;
            }
        }
        return true;
    }

    /**
     * Scan an escape character.
     * @return the character or -1 if it escaped an
     * end-of-line.
     */
    private int scanEscapeChar() throws IOException {
        long p = in.pos;

        switch (ch = in.read()) {
          case '0': case '1': case '2': case '3':
          case '4': case '5': case '6': case '7': {
            int n = ch - '0';
            for (int i = 2 ; i > 0 ; i--) {
                switch (ch = in.read()) {
                  case '0': case '1': case '2': case '3':
                  case '4': case '5': case '6': case '7':
                    n = (n << 3) + ch - '0';
                    break;

                  default:
                    if (n > 0xFF) {
                        env.error(p, "invalid.escape.char");
                    }
                    return n;
                }
            }
            ch = in.read();
            if (n > 0xFF) {
                env.error(p, "invalid.escape.char");
            }
            return n;
          }

          case 'r':  ch = in.read(); return '\r';
          case 'n':  ch = in.read(); return '\n';
          case 'f':  ch = in.read(); return '\f';
          case 'b':  ch = in.read(); return '\b';
          case 't':  ch = in.read(); return '\t';
          case '\\': ch = in.read(); return '\\';
          case '\"': ch = in.read(); return '\"';
          case '\'': ch = in.read(); return '\'';
        }

        env.error(p, "invalid.escape.char");
        ch = in.read();
        return -1;
    }

    /**
     * Scan a string. The current character
     * should be the opening " of the string.
     */
    private void scanString() throws IOException {
        token = STRINGVAL;
        count = 0;
        ch = in.read();

        // Scan a String
        while (true) {
            switch (ch) {
              case EOF:
                env.error(pos, "eof.in.string");
                stringValue = bufferString();
                return;

              case '\r':
              case '\n':
                ch = in.read();
                env.error(pos, "newline.in.string");
                stringValue = bufferString();
                return;

              case '"':
                ch = in.read();
                stringValue = bufferString();
                return;

              case '\\': {
                int c = scanEscapeChar();
                if (c >= 0) {
                    putc((char)c);
                }
                break;
              }

              default:
                putc(ch);
                ch = in.read();
                break;
            }
        }
    }

    /**
     * Scan a character. The current character should be
     * the opening ' of the character constant.
     */
    private void scanCharacter() throws IOException {
        token = CHARVAL;

        switch (ch = in.read()) {
          case '\\':
            int c = scanEscapeChar();
            charValue = (char)((c >= 0) ? c : 0);
            break;

        case '\'':
            // There are two standard problems this case deals with.  One
            // is the malformed single quote constant (i.e. the programmer
            // uses ''' instead of '\'') and the other is the empty
            // character constant (i.e. '').  Just consume any number of
            // single quotes and emit an error message.
            charValue = 0;
            env.error(pos, "invalid.char.constant");
            ch = in.read();
            while (ch == '\'') {
                ch = in.read();
            }
            return;

          case '\r':
          case '\n':
            charValue = 0;
            env.error(pos, "invalid.char.constant");
            return;

          default:
            charValue = (char)ch;
            ch = in.read();
            break;
        }

        if (ch == '\'') {
            ch = in.read();
        } else {
            env.error(pos, "invalid.char.constant");
            while (true) {
                switch (ch) {
                  case '\'':
                    ch = in.read();
                    return;
                  case ';':
                  case '\n':
                  case EOF:
                    return;
                  default:
                    ch = in.read();
                }
            }
        }
    }

    /**
     * Scan an Identifier. The current character should
     * be the first character of the identifier.
     */
    private void scanIdentifier() throws IOException {
        count = 0;

        while (true) {
            putc(ch);
            switch (ch = in.read()) {
              case 'a': case 'b': case 'c': case 'd': case 'e':
              case 'f': case 'g': case 'h': case 'i': case 'j':
              case 'k': case 'l': case 'm': case 'n': case 'o':
              case 'p': case 'q': case 'r': case 's': case 't':
              case 'u': case 'v': case 'w': case 'x': case 'y':
              case 'z':
              case 'A': case 'B': case 'C': case 'D': case 'E':
              case 'F': case 'G': case 'H': case 'I': case 'J':
              case 'K': case 'L': case 'M': case 'N': case 'O':
              case 'P': case 'Q': case 'R': case 'S': case 'T':
              case 'U': case 'V': case 'W': case 'X': case 'Y':
              case 'Z':
              case '0': case '1': case '2': case '3': case '4':
              case '5': case '6': case '7': case '8': case '9':
              case '$': case '_':
                break;

              default:
                if (!Character.isJavaLetterOrDigit((char)ch)) {
                    idValue = Identifier.lookup(bufferString());
                    token = idValue.getType();
                    return;
                }
            }
        }
    }

    /**
     * The ending position of the current token
     */
    // Note: This should be part of the pos itself.
    public long getEndPos() {
        return in.pos;
    }

    /**
     * If the current token is IDENT, return the identifier occurrence.
     * It will be freshly allocated.
     */
    public IdentifierToken getIdToken() {
        return (token != IDENT) ? null : new IdentifierToken(pos, idValue);
    }

    /**
     * Scan the next token.
     * @return the position of the previous token.
     */
   public long scan() throws IOException {
       return xscan();
   }

    @SuppressWarnings("fallthrough")
    protected long xscan() throws IOException {
        final ScannerInputReader in = this.in;
        long retPos = pos;
        prevPos = in.pos;
        docComment = null;
        while (true) {
            pos = in.pos;

            switch (ch) {
              case EOF:
                token = EOF;
                return retPos;

              case '\n':
                if (scanComments) {
                    ch = ' ';
                    // Avoid this path the next time around.
                    // Do not just call in.read; we want to present
                    // a null token (and also avoid read-ahead).
                    token = COMMENT;
                    return retPos;
                }
                // Fall through
              case ' ':
              case '\t':
              case '\f':
                ch = in.read();
                break;

              case '/':
                switch (ch = in.read()) {
                  case '/':
                    // Parse a // comment
                    while (((ch = in.read()) != EOF) && (ch != '\n'));
                    if (scanComments) {
                        token = COMMENT;
                        return retPos;
                    }
                    break;

                  case '*':
                    ch = in.read();
                    if (ch == '*') {
                        docComment = scanDocComment();
                    } else {
                        skipComment();
                    }
                    if (scanComments) {
                        return retPos;
                    }
                    break;

                  case '=':
                    ch = in.read();
                    token = ASGDIV;
                    return retPos;

                  default:
                    token = DIV;
                    return retPos;
                }
                break;

              case '"':
                scanString();
                return retPos;

              case '\'':
                scanCharacter();
                return retPos;

              case '0': case '1': case '2': case '3': case '4':
              case '5': case '6': case '7': case '8': case '9':
                scanNumber();
                return retPos;

              case '.':
                switch (ch = in.read()) {
                  case '0': case '1': case '2': case '3': case '4':
                  case '5': case '6': case '7': case '8': case '9':
                    count = 0;
                    putc('.');
                    scanReal();
                    break;
                  default:
                    token = FIELD;
                }
                return retPos;

              case '{':
                ch = in.read();
                token = LBRACE;
                return retPos;

              case '}':
                ch = in.read();
                token = RBRACE;
                return retPos;

              case '(':
                ch = in.read();
                token = LPAREN;
                return retPos;

              case ')':
                ch = in.read();
                token = RPAREN;
                return retPos;

              case '[':
                ch = in.read();
                token = LSQBRACKET;
                return retPos;

              case ']':
                ch = in.read();
                token = RSQBRACKET;
                return retPos;

              case ',':
                ch = in.read();
                token = COMMA;
                return retPos;

              case ';':
                ch = in.read();
                token = SEMICOLON;
                return retPos;

              case '?':
                ch = in.read();
                token = QUESTIONMARK;
                return retPos;

              case '~':
                ch = in.read();
                token = BITNOT;
                return retPos;

              case ':':
                ch = in.read();
                token = COLON;
                return retPos;

              case '-':
                switch (ch = in.read()) {
                  case '-':
                    ch = in.read();
                    token = DEC;
                    return retPos;

                  case '=':
                    ch = in.read();
                    token = ASGSUB;
                    return retPos;
                }
                token = SUB;
                return retPos;

              case '+':
                switch (ch = in.read()) {
                  case '+':
                    ch = in.read();
                    token = INC;
                    return retPos;

                  case '=':
                    ch = in.read();
                    token = ASGADD;
                    return retPos;
                }
                token = ADD;
                return retPos;

              case '<':
                switch (ch = in.read()) {
                  case '<':
                    if ((ch = in.read()) == '=') {
                        ch = in.read();
                        token = ASGLSHIFT;
                        return retPos;
                    }
                    token = LSHIFT;
                    return retPos;

                  case '=':
                    ch = in.read();
                    token = LE;
                    return retPos;
                }
                token = LT;
                return retPos;

              case '>':
                switch (ch = in.read()) {
                  case '>':
                    switch (ch = in.read()) {
                      case '=':
                        ch = in.read();
                        token = ASGRSHIFT;
                        return retPos;

                      case '>':
                        if ((ch = in.read()) == '=') {
                            ch = in.read();
                            token = ASGURSHIFT;
                            return retPos;
                        }
                        token = URSHIFT;
                        return retPos;
                    }
                    token = RSHIFT;
                    return retPos;

                  case '=':
                    ch = in.read();
                    token = GE;
                    return retPos;
                }
                token = GT;
                return retPos;

              case '|':
                switch (ch = in.read()) {
                  case '|':
                    ch = in.read();
                    token = OR;
                    return retPos;

                  case '=':
                    ch = in.read();
                    token = ASGBITOR;
                    return retPos;
                }
                token = BITOR;
                return retPos;

              case '&':
                switch (ch = in.read()) {
                  case '&':
                    ch = in.read();
                    token = AND;
                    return retPos;

                  case '=':
                    ch = in.read();
                    token = ASGBITAND;
                    return retPos;
                }
                token = BITAND;
                return retPos;

              case '=':
                if ((ch = in.read()) == '=') {
                    ch = in.read();
                    token = EQ;
                    return retPos;
                }
                token = ASSIGN;
                return retPos;

              case '%':
                if ((ch = in.read()) == '=') {
                    ch = in.read();
                    token = ASGREM;
                    return retPos;
                }
                token = REM;
                return retPos;

              case '^':
                if ((ch = in.read()) == '=') {
                    ch = in.read();
                    token = ASGBITXOR;
                    return retPos;
                }
                token = BITXOR;
                return retPos;

              case '!':
                if ((ch = in.read()) == '=') {
                    ch = in.read();
                    token = NE;
                    return retPos;
                }
                token = NOT;
                return retPos;

              case '*':
                if ((ch = in.read()) == '=') {
                    ch = in.read();
                    token = ASGMUL;
                    return retPos;
                }
                token = MUL;
                return retPos;

              case 'a': case 'b': case 'c': case 'd': case 'e': case 'f':
              case 'g': case 'h': case 'i': case 'j': case 'k': case 'l':
              case 'm': case 'n': case 'o': case 'p': case 'q': case 'r':
              case 's': case 't': case 'u': case 'v': case 'w': case 'x':
              case 'y': case 'z':
              case 'A': case 'B': case 'C': case 'D': case 'E': case 'F':
              case 'G': case 'H': case 'I': case 'J': case 'K': case 'L':
              case 'M': case 'N': case 'O': case 'P': case 'Q': case 'R':
              case 'S': case 'T': case 'U': case 'V': case 'W': case 'X':
              case 'Y': case 'Z':
              case '$': case '_':
                scanIdentifier();
                return retPos;

              case '\u001a':
                // Our one concession to DOS.
                if ((ch = in.read()) == EOF) {
                    token = EOF;
                    return retPos;
                }
                env.error(pos, "funny.char");
                ch = in.read();
                break;


              default:
                if (Character.isJavaLetter((char)ch)) {
                    scanIdentifier();
                    return retPos;
                }
                env.error(pos, "funny.char");
                ch = in.read();
                break;
            }
        }
    }

    /**
     * Scan to a matching '}', ']' or ')'. The current token must be
     * a '{', '[' or '(';
     */
    public void match(int open, int close) throws IOException {
        int depth = 1;

        while (true) {
            scan();
            if (token == open) {
                depth++;
            } else if (token == close) {
                if (--depth == 0) {
                    return;
                }
            } else if (token == EOF) {
                env.error(pos, "unbalanced.paren");
                return;
            }
        }
    }
}
