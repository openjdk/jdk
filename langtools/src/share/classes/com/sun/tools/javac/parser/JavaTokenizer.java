/*
 * Copyright (c) 1999, 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.CharBuffer;
import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.util.*;


import static com.sun.tools.javac.parser.Tokens.*;
import static com.sun.tools.javac.util.LayoutCharacters.*;

/** The lexical analyzer maps an input stream consisting of
 *  ASCII characters and Unicode escapes into a token sequence.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class JavaTokenizer {

    private static boolean scannerDebug = false;

    /** Allow hex floating-point literals.
     */
    private boolean allowHexFloats;

    /** Allow binary literals.
     */
    private boolean allowBinaryLiterals;

    /** Allow underscores in literals.
     */
    private boolean allowUnderscoresInLiterals;

    /** The source language setting.
     */
    private Source source;

    /** The log to be used for error reporting.
     */
    private final Log log;

    /** The name table. */
    private final Names names;

    /** The token factory. */
    private final Tokens tokens;

    /** The token kind, set by nextToken().
     */
    protected TokenKind tk;

    /** The token's radix, set by nextToken().
     */
    protected int radix;

    /** The token's name, set by nextToken().
     */
    protected Name name;

    /** The position where a lexical error occurred;
     */
    protected int errPos = Position.NOPOS;

    /** Has a @deprecated been encountered in last doc comment?
     *  this needs to be reset by client.
     */
    protected boolean deprecatedFlag = false;

    /** A character buffer for saved chars.
     */
    protected char[] sbuf = new char[128];
    protected int sp;

    protected UnicodeReader reader;

    private static final boolean hexFloatsWork = hexFloatsWork();
    private static boolean hexFloatsWork() {
        try {
            Float.valueOf("0x1.0p1");
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    /**
     * Create a scanner from the input array.  This method might
     * modify the array.  To avoid copying the input array, ensure
     * that {@code inputLength < input.length} or
     * {@code input[input.length -1]} is a white space character.
     *
     * @param fac the factory which created this Scanner
     * @param input the input, might be modified
     * @param inputLength the size of the input.
     * Must be positive and less than or equal to input.length.
     */
    protected JavaTokenizer(ScannerFactory fac, CharBuffer buf) {
        this(fac, new UnicodeReader(fac, buf));
    }

    protected JavaTokenizer(ScannerFactory fac, char[] buf, int inputLength) {
        this(fac, new UnicodeReader(fac, buf, inputLength));
    }

    protected JavaTokenizer(ScannerFactory fac, UnicodeReader reader) {
        log = fac.log;
        names = fac.names;
        tokens = fac.tokens;
        source = fac.source;
        this.reader = reader;
        allowBinaryLiterals = source.allowBinaryLiterals();
        allowHexFloats = source.allowHexFloats();
        allowUnderscoresInLiterals = source.allowUnderscoresInLiterals();
    }

    /** Report an error at the given position using the provided arguments.
     */
    protected void lexError(int pos, String key, Object... args) {
        log.error(pos, key, args);
        tk = TokenKind.ERROR;
        errPos = pos;
    }

    /** Read next character in comment, skipping over double '\' characters.
     */
    protected void scanCommentChar() {
        reader.scanChar();
        if (reader.ch == '\\') {
            if (reader.peekChar() == '\\' && !reader.isUnicode()) {
                reader.skipChar();
            } else {
                reader.convertUnicode();
            }
        }
    }

    /** Append a character to sbuf.
     */
    private void putChar(char ch) {
        if (sp == sbuf.length) {
            char[] newsbuf = new char[sbuf.length * 2];
            System.arraycopy(sbuf, 0, newsbuf, 0, sbuf.length);
            sbuf = newsbuf;
        }
        sbuf[sp++] = ch;
    }

    /** Read next character in character or string literal and copy into sbuf.
     */
    private void scanLitChar(int pos) {
        if (reader.ch == '\\') {
            if (reader.peekChar() == '\\' && !reader.isUnicode()) {
                reader.skipChar();
                putChar('\\');
                reader.scanChar();
            } else {
                reader.scanChar();
                switch (reader.ch) {
                case '0': case '1': case '2': case '3':
                case '4': case '5': case '6': case '7':
                    char leadch = reader.ch;
                    int oct = reader.digit(pos, 8);
                    reader.scanChar();
                    if ('0' <= reader.ch && reader.ch <= '7') {
                        oct = oct * 8 + reader.digit(pos, 8);
                        reader.scanChar();
                        if (leadch <= '3' && '0' <= reader.ch && reader.ch <= '7') {
                            oct = oct * 8 + reader.digit(pos, 8);
                            reader.scanChar();
                        }
                    }
                    putChar((char)oct);
                    break;
                case 'b':
                    putChar('\b'); reader.scanChar(); break;
                case 't':
                    putChar('\t'); reader.scanChar(); break;
                case 'n':
                    putChar('\n'); reader.scanChar(); break;
                case 'f':
                    putChar('\f'); reader.scanChar(); break;
                case 'r':
                    putChar('\r'); reader.scanChar(); break;
                case '\'':
                    putChar('\''); reader.scanChar(); break;
                case '\"':
                    putChar('\"'); reader.scanChar(); break;
                case '\\':
                    putChar('\\'); reader.scanChar(); break;
                default:
                    lexError(reader.bp, "illegal.esc.char");
                }
            }
        } else if (reader.bp != reader.buflen) {
            putChar(reader.ch); reader.scanChar();
        }
    }

    private void scanDigits(int pos, int digitRadix) {
        char saveCh;
        int savePos;
        do {
            if (reader.ch != '_') {
                putChar(reader.ch);
            } else {
                if (!allowUnderscoresInLiterals) {
                    lexError(pos, "unsupported.underscore.lit", source.name);
                    allowUnderscoresInLiterals = true;
                }
            }
            saveCh = reader.ch;
            savePos = reader.bp;
            reader.scanChar();
        } while (reader.digit(pos, digitRadix) >= 0 || reader.ch == '_');
        if (saveCh == '_')
            lexError(savePos, "illegal.underscore");
    }

    /** Read fractional part of hexadecimal floating point number.
     */
    private void scanHexExponentAndSuffix(int pos) {
        if (reader.ch == 'p' || reader.ch == 'P') {
            putChar(reader.ch);
            reader.scanChar();
            skipIllegalUnderscores();
            if (reader.ch == '+' || reader.ch == '-') {
                putChar(reader.ch);
                reader.scanChar();
            }
            skipIllegalUnderscores();
            if ('0' <= reader.ch && reader.ch <= '9') {
                scanDigits(pos, 10);
                if (!allowHexFloats) {
                    lexError(pos, "unsupported.fp.lit", source.name);
                    allowHexFloats = true;
                }
                else if (!hexFloatsWork)
                    lexError(pos, "unsupported.cross.fp.lit");
            } else
                lexError(pos, "malformed.fp.lit");
        } else {
            lexError(pos, "malformed.fp.lit");
        }
        if (reader.ch == 'f' || reader.ch == 'F') {
            putChar(reader.ch);
            reader.scanChar();
            tk = TokenKind.FLOATLITERAL;
            radix = 16;
        } else {
            if (reader.ch == 'd' || reader.ch == 'D') {
                putChar(reader.ch);
                reader.scanChar();
            }
            tk = TokenKind.DOUBLELITERAL;
            radix = 16;
        }
    }

    /** Read fractional part of floating point number.
     */
    private void scanFraction(int pos) {
        skipIllegalUnderscores();
        if ('0' <= reader.ch && reader.ch <= '9') {
            scanDigits(pos, 10);
        }
        int sp1 = sp;
        if (reader.ch == 'e' || reader.ch == 'E') {
            putChar(reader.ch);
            reader.scanChar();
            skipIllegalUnderscores();
            if (reader.ch == '+' || reader.ch == '-') {
                putChar(reader.ch);
                reader.scanChar();
            }
            skipIllegalUnderscores();
            if ('0' <= reader.ch && reader.ch <= '9') {
                scanDigits(pos, 10);
                return;
            }
            lexError(pos, "malformed.fp.lit");
            sp = sp1;
        }
    }

    /** Read fractional part and 'd' or 'f' suffix of floating point number.
     */
    private void scanFractionAndSuffix(int pos) {
        radix = 10;
        scanFraction(pos);
        if (reader.ch == 'f' || reader.ch == 'F') {
            putChar(reader.ch);
            reader.scanChar();
            tk = TokenKind.FLOATLITERAL;
        } else {
            if (reader.ch == 'd' || reader.ch == 'D') {
                putChar(reader.ch);
                reader.scanChar();
            }
            tk = TokenKind.DOUBLELITERAL;
        }
    }

    /** Read fractional part and 'd' or 'f' suffix of floating point number.
     */
    private void scanHexFractionAndSuffix(int pos, boolean seendigit) {
        radix = 16;
        Assert.check(reader.ch == '.');
        putChar(reader.ch);
        reader.scanChar();
        skipIllegalUnderscores();
        if (reader.digit(pos, 16) >= 0) {
            seendigit = true;
            scanDigits(pos, 16);
        }
        if (!seendigit)
            lexError(pos, "invalid.hex.number");
        else
            scanHexExponentAndSuffix(pos);
    }

    private void skipIllegalUnderscores() {
        if (reader.ch == '_') {
            lexError(reader.bp, "illegal.underscore");
            while (reader.ch == '_')
                reader.scanChar();
        }
    }

    /** Read a number.
     *  @param radix  The radix of the number; one of 2, j8, 10, 16.
     */
    private void scanNumber(int pos, int radix) {
        // for octal, allow base-10 digit in case it's a float literal
        this.radix = radix;
        int digitRadix = (radix == 8 ? 10 : radix);
        boolean seendigit = false;
        if (reader.digit(pos, digitRadix) >= 0) {
            seendigit = true;
            scanDigits(pos, digitRadix);
        }
        if (radix == 16 && reader.ch == '.') {
            scanHexFractionAndSuffix(pos, seendigit);
        } else if (seendigit && radix == 16 && (reader.ch == 'p' || reader.ch == 'P')) {
            scanHexExponentAndSuffix(pos);
        } else if (digitRadix == 10 && reader.ch == '.') {
            putChar(reader.ch);
            reader.scanChar();
            scanFractionAndSuffix(pos);
        } else if (digitRadix == 10 &&
                   (reader.ch == 'e' || reader.ch == 'E' ||
                    reader.ch == 'f' || reader.ch == 'F' ||
                    reader.ch == 'd' || reader.ch == 'D')) {
            scanFractionAndSuffix(pos);
        } else {
            if (reader.ch == 'l' || reader.ch == 'L') {
                reader.scanChar();
                tk = TokenKind.LONGLITERAL;
            } else {
                tk = TokenKind.INTLITERAL;
            }
        }
    }

    /** Read an identifier.
     */
    private void scanIdent() {
        boolean isJavaIdentifierPart;
        char high;
        do {
            if (sp == sbuf.length) putChar(reader.ch); else sbuf[sp++] = reader.ch;
            // optimization, was: putChar(reader.ch);

            reader.scanChar();
            switch (reader.ch) {
            case 'A': case 'B': case 'C': case 'D': case 'E':
            case 'F': case 'G': case 'H': case 'I': case 'J':
            case 'K': case 'L': case 'M': case 'N': case 'O':
            case 'P': case 'Q': case 'R': case 'S': case 'T':
            case 'U': case 'V': case 'W': case 'X': case 'Y':
            case 'Z':
            case 'a': case 'b': case 'c': case 'd': case 'e':
            case 'f': case 'g': case 'h': case 'i': case 'j':
            case 'k': case 'l': case 'm': case 'n': case 'o':
            case 'p': case 'q': case 'r': case 's': case 't':
            case 'u': case 'v': case 'w': case 'x': case 'y':
            case 'z':
            case '$': case '_':
            case '0': case '1': case '2': case '3': case '4':
            case '5': case '6': case '7': case '8': case '9':
            case '\u0000': case '\u0001': case '\u0002': case '\u0003':
            case '\u0004': case '\u0005': case '\u0006': case '\u0007':
            case '\u0008': case '\u000E': case '\u000F': case '\u0010':
            case '\u0011': case '\u0012': case '\u0013': case '\u0014':
            case '\u0015': case '\u0016': case '\u0017':
            case '\u0018': case '\u0019': case '\u001B':
            case '\u007F':
                break;
            case '\u001A': // EOI is also a legal identifier part
                if (reader.bp >= reader.buflen) {
                    name = names.fromChars(sbuf, 0, sp);
                    tk = tokens.lookupKind(name);
                    return;
                }
                break;
            default:
                if (reader.ch < '\u0080') {
                    // all ASCII range chars already handled, above
                    isJavaIdentifierPart = false;
                } else {
                    high = reader.scanSurrogates();
                    if (high != 0) {
                        if (sp == sbuf.length) {
                            putChar(high);
                        } else {
                            sbuf[sp++] = high;
                        }
                        isJavaIdentifierPart = Character.isJavaIdentifierPart(
                            Character.toCodePoint(high, reader.ch));
                    } else {
                        isJavaIdentifierPart = Character.isJavaIdentifierPart(reader.ch);
                    }
                }
                if (!isJavaIdentifierPart) {
                    name = names.fromChars(sbuf, 0, sp);
                    tk = tokens.lookupKind(name);
                    return;
                }
            }
        } while (true);
    }

    /** Return true if reader.ch can be part of an operator.
     */
    private boolean isSpecial(char ch) {
        switch (ch) {
        case '!': case '%': case '&': case '*': case '?':
        case '+': case '-': case ':': case '<': case '=':
        case '>': case '^': case '|': case '~':
        case '@':
            return true;
        default:
            return false;
        }
    }

    /** Read longest possible sequence of special characters and convert
     *  to token.
     */
    private void scanOperator() {
        while (true) {
            putChar(reader.ch);
            Name newname = names.fromChars(sbuf, 0, sp);
            TokenKind tk1 = tokens.lookupKind(newname);
            if (tk1 == TokenKind.IDENTIFIER) {
                sp--;
                break;
            }
            tk = tk1;
            reader.scanChar();
            if (!isSpecial(reader.ch)) break;
        }
    }

    /**
     * Scan a documentation comment; determine if a deprecated tag is present.
     * Called once the initial /, * have been skipped, positioned at the second *
     * (which is treated as the beginning of the first line).
     * Stops positioned at the closing '/'.
     */
    @SuppressWarnings("fallthrough")
    private void scanDocComment() {
        boolean deprecatedPrefix = false;

        forEachLine:
        while (reader.bp < reader.buflen) {

            // Skip optional WhiteSpace at beginning of line
            while (reader.bp < reader.buflen && (reader.ch == ' ' || reader.ch == '\t' || reader.ch == FF)) {
                scanCommentChar();
            }

            // Skip optional consecutive Stars
            while (reader.bp < reader.buflen && reader.ch == '*') {
                scanCommentChar();
                if (reader.ch == '/') {
                    return;
                }
            }

            // Skip optional WhiteSpace after Stars
            while (reader.bp < reader.buflen && (reader.ch == ' ' || reader.ch == '\t' || reader.ch == FF)) {
                scanCommentChar();
            }

            deprecatedPrefix = false;
            // At beginning of line in the JavaDoc sense.
            if (reader.bp < reader.buflen && reader.ch == '@' && !deprecatedFlag) {
                scanCommentChar();
                if (reader.bp < reader.buflen && reader.ch == 'd') {
                    scanCommentChar();
                    if (reader.bp < reader.buflen && reader.ch == 'e') {
                        scanCommentChar();
                        if (reader.bp < reader.buflen && reader.ch == 'p') {
                            scanCommentChar();
                            if (reader.bp < reader.buflen && reader.ch == 'r') {
                                scanCommentChar();
                                if (reader.bp < reader.buflen && reader.ch == 'e') {
                                    scanCommentChar();
                                    if (reader.bp < reader.buflen && reader.ch == 'c') {
                                        scanCommentChar();
                                        if (reader.bp < reader.buflen && reader.ch == 'a') {
                                            scanCommentChar();
                                            if (reader.bp < reader.buflen && reader.ch == 't') {
                                                scanCommentChar();
                                                if (reader.bp < reader.buflen && reader.ch == 'e') {
                                                    scanCommentChar();
                                                    if (reader.bp < reader.buflen && reader.ch == 'd') {
                                                        deprecatedPrefix = true;
                                                        scanCommentChar();
                                                    }}}}}}}}}}}
            if (deprecatedPrefix && reader.bp < reader.buflen) {
                if (Character.isWhitespace(reader.ch)) {
                    deprecatedFlag = true;
                } else if (reader.ch == '*') {
                    scanCommentChar();
                    if (reader.ch == '/') {
                        deprecatedFlag = true;
                        return;
                    }
                }
            }

            // Skip rest of line
            while (reader.bp < reader.buflen) {
                switch (reader.ch) {
                case '*':
                    scanCommentChar();
                    if (reader.ch == '/') {
                        return;
                    }
                    break;
                case CR: // (Spec 3.4)
                    scanCommentChar();
                    if (reader.ch != LF) {
                        continue forEachLine;
                    }
                    /* fall through to LF case */
                case LF: // (Spec 3.4)
                    scanCommentChar();
                    continue forEachLine;
                default:
                    scanCommentChar();
                }
            } // rest of line
        } // forEachLine
        return;
    }

    /** Read token.
     */
    public Token readToken() {

        sp = 0;
        name = null;
        deprecatedFlag = false;
        radix = 0;
        int pos = 0;
        int endPos = 0;

        try {
            loop: while (true) {
                pos = reader.bp;
                switch (reader.ch) {
                case ' ': // (Spec 3.6)
                case '\t': // (Spec 3.6)
                case FF: // (Spec 3.6)
                    do {
                        reader.scanChar();
                    } while (reader.ch == ' ' || reader.ch == '\t' || reader.ch == FF);
                    processWhiteSpace(pos, reader.bp);
                    break;
                case LF: // (Spec 3.4)
                    reader.scanChar();
                    processLineTerminator(pos, reader.bp);
                    break;
                case CR: // (Spec 3.4)
                    reader.scanChar();
                    if (reader.ch == LF) {
                        reader.scanChar();
                    }
                    processLineTerminator(pos, reader.bp);
                    break;
                case 'A': case 'B': case 'C': case 'D': case 'E':
                case 'F': case 'G': case 'H': case 'I': case 'J':
                case 'K': case 'L': case 'M': case 'N': case 'O':
                case 'P': case 'Q': case 'R': case 'S': case 'T':
                case 'U': case 'V': case 'W': case 'X': case 'Y':
                case 'Z':
                case 'a': case 'b': case 'c': case 'd': case 'e':
                case 'f': case 'g': case 'h': case 'i': case 'j':
                case 'k': case 'l': case 'm': case 'n': case 'o':
                case 'p': case 'q': case 'r': case 's': case 't':
                case 'u': case 'v': case 'w': case 'x': case 'y':
                case 'z':
                case '$': case '_':
                    scanIdent();
                    break loop;
                case '0':
                    reader.scanChar();
                    if (reader.ch == 'x' || reader.ch == 'X') {
                        reader.scanChar();
                        skipIllegalUnderscores();
                        if (reader.ch == '.') {
                            scanHexFractionAndSuffix(pos, false);
                        } else if (reader.digit(pos, 16) < 0) {
                            lexError(pos, "invalid.hex.number");
                        } else {
                            scanNumber(pos, 16);
                        }
                    } else if (reader.ch == 'b' || reader.ch == 'B') {
                        if (!allowBinaryLiterals) {
                            lexError(pos, "unsupported.binary.lit", source.name);
                            allowBinaryLiterals = true;
                        }
                        reader.scanChar();
                        skipIllegalUnderscores();
                        if (reader.digit(pos, 2) < 0) {
                            lexError(pos, "invalid.binary.number");
                        } else {
                            scanNumber(pos, 2);
                        }
                    } else {
                        putChar('0');
                        if (reader.ch == '_') {
                            int savePos = reader.bp;
                            do {
                                reader.scanChar();
                            } while (reader.ch == '_');
                            if (reader.digit(pos, 10) < 0) {
                                lexError(savePos, "illegal.underscore");
                            }
                        }
                        scanNumber(pos, 8);
                    }
                    break loop;
                case '1': case '2': case '3': case '4':
                case '5': case '6': case '7': case '8': case '9':
                    scanNumber(pos, 10);
                    break loop;
                case '.':
                    reader.scanChar();
                    if ('0' <= reader.ch && reader.ch <= '9') {
                        putChar('.');
                        scanFractionAndSuffix(pos);
                    } else if (reader.ch == '.') {
                        putChar('.'); putChar('.');
                        reader.scanChar();
                        if (reader.ch == '.') {
                            reader.scanChar();
                            putChar('.');
                            tk = TokenKind.ELLIPSIS;
                        } else {
                            lexError(pos, "malformed.fp.lit");
                        }
                    } else {
                        tk = TokenKind.DOT;
                    }
                    break loop;
                case ',':
                    reader.scanChar(); tk = TokenKind.COMMA; break loop;
                case ';':
                    reader.scanChar(); tk = TokenKind.SEMI; break loop;
                case '(':
                    reader.scanChar(); tk = TokenKind.LPAREN; break loop;
                case ')':
                    reader.scanChar(); tk = TokenKind.RPAREN; break loop;
                case '[':
                    reader.scanChar(); tk = TokenKind.LBRACKET; break loop;
                case ']':
                    reader.scanChar(); tk = TokenKind.RBRACKET; break loop;
                case '{':
                    reader.scanChar(); tk = TokenKind.LBRACE; break loop;
                case '}':
                    reader.scanChar(); tk = TokenKind.RBRACE; break loop;
                case '/':
                    reader.scanChar();
                    if (reader.ch == '/') {
                        do {
                            scanCommentChar();
                        } while (reader.ch != CR && reader.ch != LF && reader.bp < reader.buflen);
                        if (reader.bp < reader.buflen) {
                            processComment(pos, reader.bp, CommentStyle.LINE);
                        }
                        break;
                    } else if (reader.ch == '*') {
                        reader.scanChar();
                        CommentStyle style;
                        if (reader.ch == '*') {
                            style = CommentStyle.JAVADOC;
                            scanDocComment();
                        } else {
                            style = CommentStyle.BLOCK;
                            while (reader.bp < reader.buflen) {
                                if (reader.ch == '*') {
                                    reader.scanChar();
                                    if (reader.ch == '/') break;
                                } else {
                                    scanCommentChar();
                                }
                            }
                        }
                        if (reader.ch == '/') {
                            reader.scanChar();
                            processComment(pos, reader.bp, style);
                            break;
                        } else {
                            lexError(pos, "unclosed.comment");
                            break loop;
                        }
                    } else if (reader.ch == '=') {
                        tk = TokenKind.SLASHEQ;
                        reader.scanChar();
                    } else {
                        tk = TokenKind.SLASH;
                    }
                    break loop;
                case '\'':
                    reader.scanChar();
                    if (reader.ch == '\'') {
                        lexError(pos, "empty.char.lit");
                    } else {
                        if (reader.ch == CR || reader.ch == LF)
                            lexError(pos, "illegal.line.end.in.char.lit");
                        scanLitChar(pos);
                        char ch2 = reader.ch;
                        if (reader.ch == '\'') {
                            reader.scanChar();
                            tk = TokenKind.CHARLITERAL;
                        } else {
                            lexError(pos, "unclosed.char.lit");
                        }
                    }
                    break loop;
                case '\"':
                    reader.scanChar();
                    while (reader.ch != '\"' && reader.ch != CR && reader.ch != LF && reader.bp < reader.buflen)
                        scanLitChar(pos);
                    if (reader.ch == '\"') {
                        tk = TokenKind.STRINGLITERAL;
                        reader.scanChar();
                    } else {
                        lexError(pos, "unclosed.str.lit");
                    }
                    break loop;
                default:
                    if (isSpecial(reader.ch)) {
                        scanOperator();
                    } else {
                        boolean isJavaIdentifierStart;
                        if (reader.ch < '\u0080') {
                            // all ASCII range chars already handled, above
                            isJavaIdentifierStart = false;
                        } else {
                            char high = reader.scanSurrogates();
                            if (high != 0) {
                                if (sp == sbuf.length) {
                                    putChar(high);
                                } else {
                                    sbuf[sp++] = high;
                                }

                                isJavaIdentifierStart = Character.isJavaIdentifierStart(
                                    Character.toCodePoint(high, reader.ch));
                            } else {
                                isJavaIdentifierStart = Character.isJavaIdentifierStart(reader.ch);
                            }
                        }
                        if (isJavaIdentifierStart) {
                            scanIdent();
                        } else if (reader.bp == reader.buflen || reader.ch == EOI && reader.bp + 1 == reader.buflen) { // JLS 3.5
                            tk = TokenKind.EOF;
                            pos = reader.buflen;
                        } else {
                            lexError(pos, "illegal.char", String.valueOf((int)reader.ch));
                            reader.scanChar();
                        }
                    }
                    break loop;
                }
            }
            endPos = reader.bp;
            switch (tk.tag) {
                case DEFAULT: return new Token(tk, pos, endPos, deprecatedFlag);
                case NAMED: return new NamedToken(tk, pos, endPos, name, deprecatedFlag);
                case STRING: return new StringToken(tk, pos, endPos, new String(sbuf, 0, sp), deprecatedFlag);
                case NUMERIC: return new NumericToken(tk, pos, endPos, new String(sbuf, 0, sp), radix, deprecatedFlag);
                default: throw new AssertionError();
            }
        }
        finally {
            if (scannerDebug) {
                    System.out.println("nextToken(" + pos
                                       + "," + endPos + ")=|" +
                                       new String(reader.getRawCharacters(pos, endPos))
                                       + "|");
            }
        }
    }

    /** Return the position where a lexical error occurred;
     */
    public int errPos() {
        return errPos;
    }

    /** Set the position where a lexical error occurred;
     */
    public void errPos(int pos) {
        errPos = pos;
    }

    public enum CommentStyle {
        LINE,
        BLOCK,
        JAVADOC,
    }

    /**
     * Called when a complete comment has been scanned. pos and endPos
     * will mark the comment boundary.
     */
    protected void processComment(int pos, int endPos, CommentStyle style) {
        if (scannerDebug)
            System.out.println("processComment(" + pos
                               + "," + endPos + "," + style + ")=|"
                               + new String(reader.getRawCharacters(pos, endPos))
                               + "|");
    }

    /**
     * Called when a complete whitespace run has been scanned. pos and endPos
     * will mark the whitespace boundary.
     */
    protected void processWhiteSpace(int pos, int endPos) {
        if (scannerDebug)
            System.out.println("processWhitespace(" + pos
                               + "," + endPos + ")=|" +
                               new String(reader.getRawCharacters(pos, endPos))
                               + "|");
    }

    /**
     * Called when a line terminator has been processed.
     */
    protected void processLineTerminator(int pos, int endPos) {
        if (scannerDebug)
            System.out.println("processTerminator(" + pos
                               + "," + endPos + ")=|" +
                               new String(reader.getRawCharacters(pos, endPos))
                               + "|");
    }

    /** Build a map for translating between line numbers and
     * positions in the input.
     *
     * @return a LineMap */
    public Position.LineMap getLineMap() {
        return Position.makeLineMap(reader.getRawCharacters(), reader.buflen, false);
    }
}
