/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.util.*;


import static com.sun.tools.javac.parser.Token.*;
import static com.sun.tools.javac.util.LayoutCharacters.*;

/** The lexical analyzer maps an input stream consisting of
 *  ASCII characters and Unicode escapes into a token sequence.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Scanner implements Lexer {

    private static boolean scannerDebug = false;

    /* Output variables; set by nextToken():
     */

    /** The token, set by nextToken().
     */
    private Token token;

    /** Allow hex floating-point literals.
     */
    private boolean allowHexFloats;

    /** Allow binary literals.
     */
    private boolean allowBinaryLiterals;

    /** Allow underscores in literals.
     */
    private boolean allowUnderscoresInLiterals;

    /** Allow exotic identifiers.
     */
    private boolean allowExoticIdentifiers;

    /** The source language setting.
     */
    private Source source;

    /** The token's position, 0-based offset from beginning of text.
     */
    private int pos;

    /** Character position just after the last character of the token.
     */
    private int endPos;

    /** The last character position of the previous token.
     */
    private int prevEndPos;

    /** The position where a lexical error occurred;
     */
    private int errPos = Position.NOPOS;

    /** The name of an identifier or token:
     */
    private Name name;

    /** The radix of a numeric literal token.
     */
    private int radix;

    /** Has a @deprecated been encountered in last doc comment?
     *  this needs to be reset by client.
     */
    protected boolean deprecatedFlag = false;

    /** A character buffer for literals.
     */
    private char[] sbuf = new char[128];
    private int sp;

    /** The input buffer, index of next chacter to be read,
     *  index of one past last character in buffer.
     */
    private char[] buf;
    private int bp;
    private int buflen;
    private int eofPos;

    /** The current character.
     */
    private char ch;

    /** The buffer index of the last converted unicode character
     */
    private int unicodeConversionBp = -1;

    /** The log to be used for error reporting.
     */
    private final Log log;

    /** The name table. */
    private final Names names;

    /** The keyword table. */
    private final Keywords keywords;

    /** Common code for constructors. */
    private Scanner(ScannerFactory fac) {
        log = fac.log;
        names = fac.names;
        keywords = fac.keywords;
        source = fac.source;
        allowBinaryLiterals = source.allowBinaryLiterals();
        allowHexFloats = source.allowHexFloats();
        allowUnderscoresInLiterals = source.allowBinaryLiterals();
        allowExoticIdentifiers = source.allowExoticIdentifiers();  // for invokedynamic
    }

    private static final boolean hexFloatsWork = hexFloatsWork();
    private static boolean hexFloatsWork() {
        try {
            Float.valueOf("0x1.0p1");
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    /** Create a scanner from the input buffer.  buffer must implement
     *  array() and compact(), and remaining() must be less than limit().
     */
    protected Scanner(ScannerFactory fac, CharBuffer buffer) {
        this(fac, JavacFileManager.toArray(buffer), buffer.limit());
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
    protected Scanner(ScannerFactory fac, char[] input, int inputLength) {
        this(fac);
        eofPos = inputLength;
        if (inputLength == input.length) {
            if (input.length > 0 && Character.isWhitespace(input[input.length - 1])) {
                inputLength--;
            } else {
                char[] newInput = new char[inputLength + 1];
                System.arraycopy(input, 0, newInput, 0, input.length);
                input = newInput;
            }
        }
        buf = input;
        buflen = inputLength;
        buf[buflen] = EOI;
        bp = -1;
        scanChar();
    }

    /** Report an error at the given position using the provided arguments.
     */
    private void lexError(int pos, String key, Object... args) {
        log.error(pos, key, args);
        token = ERROR;
        errPos = pos;
    }

    /** Report an error at the current token position using the provided
     *  arguments.
     */
    private void lexError(String key, Object... args) {
        lexError(pos, key, args);
    }

    /** Convert an ASCII digit from its base (8, 10, or 16)
     *  to its value.
     */
    private int digit(int base) {
        char c = ch;
        int result = Character.digit(c, base);
        if (result >= 0 && c > 0x7f) {
            lexError(pos+1, "illegal.nonascii.digit");
            ch = "0123456789abcdef".charAt(result);
        }
        return result;
    }

    /** Convert unicode escape; bp points to initial '\' character
     *  (Spec 3.3).
     */
    private void convertUnicode() {
        if (ch == '\\' && unicodeConversionBp != bp) {
            bp++; ch = buf[bp];
            if (ch == 'u') {
                do {
                    bp++; ch = buf[bp];
                } while (ch == 'u');
                int limit = bp + 3;
                if (limit < buflen) {
                    int d = digit(16);
                    int code = d;
                    while (bp < limit && d >= 0) {
                        bp++; ch = buf[bp];
                        d = digit(16);
                        code = (code << 4) + d;
                    }
                    if (d >= 0) {
                        ch = (char)code;
                        unicodeConversionBp = bp;
                        return;
                    }
                }
                lexError(bp, "illegal.unicode.esc");
            } else {
                bp--;
                ch = '\\';
            }
        }
    }

    /** Read next character.
     */
    private void scanChar() {
        ch = buf[++bp];
        if (ch == '\\') {
            convertUnicode();
        }
    }

    /** Read next character in comment, skipping over double '\' characters.
     */
    private void scanCommentChar() {
        scanChar();
        if (ch == '\\') {
            if (buf[bp+1] == '\\' && unicodeConversionBp != bp) {
                bp++;
            } else {
                convertUnicode();
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

    /** For debugging purposes: print character.
     */
    private void dch() {
        System.err.print(ch); System.out.flush();
    }

    /** Read next character in character or string literal and copy into sbuf.
     */
    private void scanLitChar(boolean forBytecodeName) {
        if (ch == '\\') {
            if (buf[bp+1] == '\\' && unicodeConversionBp != bp) {
                bp++;
                putChar('\\');
                scanChar();
            } else {
                scanChar();
                switch (ch) {
                case '0': case '1': case '2': case '3':
                case '4': case '5': case '6': case '7':
                    char leadch = ch;
                    int oct = digit(8);
                    scanChar();
                    if ('0' <= ch && ch <= '7') {
                        oct = oct * 8 + digit(8);
                        scanChar();
                        if (leadch <= '3' && '0' <= ch && ch <= '7') {
                            oct = oct * 8 + digit(8);
                            scanChar();
                        }
                    }
                    putChar((char)oct);
                    break;
                case 'b':
                    putChar('\b'); scanChar(); break;
                case 't':
                    putChar('\t'); scanChar(); break;
                case 'n':
                    putChar('\n'); scanChar(); break;
                case 'f':
                    putChar('\f'); scanChar(); break;
                case 'r':
                    putChar('\r'); scanChar(); break;
                case '\'':
                    putChar('\''); scanChar(); break;
                case '\"':
                    putChar('\"'); scanChar(); break;
                case '\\':
                    putChar('\\'); scanChar(); break;
                case '|': case ',': case '?': case '%':
                case '^': case '_': case '{': case '}':
                case '!': case '-': case '=':
                    if (forBytecodeName) {
                        // Accept escape sequences for dangerous bytecode chars.
                        // This is illegal in normal Java string or character literals.
                        // Note that the escape sequence itself is passed through.
                        putChar('\\'); putChar(ch); scanChar();
                    } else {
                        lexError(bp, "illegal.esc.char");
                    }
                    break;
                default:
                    lexError(bp, "illegal.esc.char");
                }
            }
        } else if (bp != buflen) {
            putChar(ch); scanChar();
        }
    }
    private void scanLitChar() {
        scanLitChar(false);
    }

    /** Read next character in an exotic name #"foo"
     */
    private void scanBytecodeNameChar() {
        switch (ch) {
        // reject any "dangerous" char which is illegal somewhere in the JVM spec
        // cf. http://blogs.sun.com/jrose/entry/symbolic_freedom_in_the_vm
        case '/': case '.': case ';':  // illegal everywhere
        case '<': case '>':  // illegal in methods, dangerous in classes
        case '[':  // illegal in classes
            lexError(bp, "illegal.bytecode.ident.char", String.valueOf((int)ch));
            break;
        }
        scanLitChar(true);
    }

    private void scanDigits(int digitRadix) {
        char saveCh;
        int savePos;
        do {
            if (ch != '_') {
                putChar(ch);
            } else {
                if (!allowUnderscoresInLiterals) {
                    lexError("unsupported.underscore.lit", source.name);
                    allowUnderscoresInLiterals = true;
                }
            }
            saveCh = ch;
            savePos = bp;
            scanChar();
        } while (digit(digitRadix) >= 0 || ch == '_');
        if (saveCh == '_')
            lexError(savePos, "illegal.underscore");
    }

    /** Read fractional part of hexadecimal floating point number.
     */
    private void scanHexExponentAndSuffix() {
        if (ch == 'p' || ch == 'P') {
            putChar(ch);
            scanChar();
            skipIllegalUnderscores();
            if (ch == '+' || ch == '-') {
                putChar(ch);
                scanChar();
            }
            skipIllegalUnderscores();
            if ('0' <= ch && ch <= '9') {
                scanDigits(10);
                if (!allowHexFloats) {
                    lexError("unsupported.fp.lit", source.name);
                    allowHexFloats = true;
                }
                else if (!hexFloatsWork)
                    lexError("unsupported.cross.fp.lit");
            } else
                lexError("malformed.fp.lit");
        } else {
            lexError("malformed.fp.lit");
        }
        if (ch == 'f' || ch == 'F') {
            putChar(ch);
            scanChar();
            token = FLOATLITERAL;
        } else {
            if (ch == 'd' || ch == 'D') {
                putChar(ch);
                scanChar();
            }
            token = DOUBLELITERAL;
        }
    }

    /** Read fractional part of floating point number.
     */
    private void scanFraction() {
        skipIllegalUnderscores();
        if ('0' <= ch && ch <= '9') {
            scanDigits(10);
        }
        int sp1 = sp;
        if (ch == 'e' || ch == 'E') {
            putChar(ch);
            scanChar();
            skipIllegalUnderscores();
            if (ch == '+' || ch == '-') {
                putChar(ch);
                scanChar();
            }
            skipIllegalUnderscores();
            if ('0' <= ch && ch <= '9') {
                scanDigits(10);
                return;
            }
            lexError("malformed.fp.lit");
            sp = sp1;
        }
    }

    /** Read fractional part and 'd' or 'f' suffix of floating point number.
     */
    private void scanFractionAndSuffix() {
        this.radix = 10;
        scanFraction();
        if (ch == 'f' || ch == 'F') {
            putChar(ch);
            scanChar();
            token = FLOATLITERAL;
        } else {
            if (ch == 'd' || ch == 'D') {
                putChar(ch);
                scanChar();
            }
            token = DOUBLELITERAL;
        }
    }

    /** Read fractional part and 'd' or 'f' suffix of floating point number.
     */
    private void scanHexFractionAndSuffix(boolean seendigit) {
        this.radix = 16;
        assert ch == '.';
        putChar(ch);
        scanChar();
        skipIllegalUnderscores();
        if (digit(16) >= 0) {
            seendigit = true;
            scanDigits(16);
        }
        if (!seendigit)
            lexError("invalid.hex.number");
        else
            scanHexExponentAndSuffix();
    }

    private void skipIllegalUnderscores() {
        if (ch == '_') {
            lexError(bp, "illegal.underscore");
            while (ch == '_')
                scanChar();
        }
    }

    /** Read a number.
     *  @param radix  The radix of the number; one of 2, j8, 10, 16.
     */
    private void scanNumber(int radix) {
        this.radix = radix;
        // for octal, allow base-10 digit in case it's a float literal
        int digitRadix = (radix == 8 ? 10 : radix);
        boolean seendigit = false;
        if (digit(digitRadix) >= 0) {
            seendigit = true;
            scanDigits(digitRadix);
        }
        if (radix == 16 && ch == '.') {
            scanHexFractionAndSuffix(seendigit);
        } else if (seendigit && radix == 16 && (ch == 'p' || ch == 'P')) {
            scanHexExponentAndSuffix();
        } else if (digitRadix == 10 && ch == '.') {
            putChar(ch);
            scanChar();
            scanFractionAndSuffix();
        } else if (digitRadix == 10 &&
                   (ch == 'e' || ch == 'E' ||
                    ch == 'f' || ch == 'F' ||
                    ch == 'd' || ch == 'D')) {
            scanFractionAndSuffix();
        } else {
            if (ch == 'l' || ch == 'L') {
                scanChar();
                token = LONGLITERAL;
            } else {
                token = INTLITERAL;
            }
        }
    }

    /** Read an identifier.
     */
    private void scanIdent() {
        boolean isJavaIdentifierPart;
        char high;
        do {
            if (sp == sbuf.length) putChar(ch); else sbuf[sp++] = ch;
            // optimization, was: putChar(ch);

            scanChar();
            switch (ch) {
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
                if (bp >= buflen) {
                    name = names.fromChars(sbuf, 0, sp);
                    token = keywords.key(name);
                    return;
                }
                break;
            default:
                if (ch < '\u0080') {
                    // all ASCII range chars already handled, above
                    isJavaIdentifierPart = false;
                } else {
                    high = scanSurrogates();
                    if (high != 0) {
                        if (sp == sbuf.length) {
                            putChar(high);
                        } else {
                            sbuf[sp++] = high;
                        }
                        isJavaIdentifierPart = Character.isJavaIdentifierPart(
                            Character.toCodePoint(high, ch));
                    } else {
                        isJavaIdentifierPart = Character.isJavaIdentifierPart(ch);
                    }
                }
                if (!isJavaIdentifierPart) {
                    name = names.fromChars(sbuf, 0, sp);
                    token = keywords.key(name);
                    return;
                }
            }
        } while (true);
    }

    /** Are surrogates supported?
     */
    final static boolean surrogatesSupported = surrogatesSupported();
    private static boolean surrogatesSupported() {
        try {
            Character.isHighSurrogate('a');
            return true;
        } catch (NoSuchMethodError ex) {
            return false;
        }
    }

    /** Scan surrogate pairs.  If 'ch' is a high surrogate and
     *  the next character is a low surrogate, then put the low
     *  surrogate in 'ch', and return the high surrogate.
     *  otherwise, just return 0.
     */
    private char scanSurrogates() {
        if (surrogatesSupported && Character.isHighSurrogate(ch)) {
            char high = ch;

            scanChar();

            if (Character.isLowSurrogate(ch)) {
                return high;
            }

            ch = high;
        }

        return 0;
    }

    /** Return true if ch can be part of an operator.
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
            putChar(ch);
            Name newname = names.fromChars(sbuf, 0, sp);
            if (keywords.key(newname) == IDENTIFIER) {
                sp--;
                break;
            }
            name = newname;
            token = keywords.key(newname);
            scanChar();
            if (!isSpecial(ch)) break;
        }
    }

    /**
     * Scan a documention comment; determine if a deprecated tag is present.
     * Called once the initial /, * have been skipped, positioned at the second *
     * (which is treated as the beginning of the first line).
     * Stops positioned at the closing '/'.
     */
    @SuppressWarnings("fallthrough")
    private void scanDocComment() {
        boolean deprecatedPrefix = false;

        forEachLine:
        while (bp < buflen) {

            // Skip optional WhiteSpace at beginning of line
            while (bp < buflen && (ch == ' ' || ch == '\t' || ch == FF)) {
                scanCommentChar();
            }

            // Skip optional consecutive Stars
            while (bp < buflen && ch == '*') {
                scanCommentChar();
                if (ch == '/') {
                    return;
                }
            }

            // Skip optional WhiteSpace after Stars
            while (bp < buflen && (ch == ' ' || ch == '\t' || ch == FF)) {
                scanCommentChar();
            }

            deprecatedPrefix = false;
            // At beginning of line in the JavaDoc sense.
            if (bp < buflen && ch == '@' && !deprecatedFlag) {
                scanCommentChar();
                if (bp < buflen && ch == 'd') {
                    scanCommentChar();
                    if (bp < buflen && ch == 'e') {
                        scanCommentChar();
                        if (bp < buflen && ch == 'p') {
                            scanCommentChar();
                            if (bp < buflen && ch == 'r') {
                                scanCommentChar();
                                if (bp < buflen && ch == 'e') {
                                    scanCommentChar();
                                    if (bp < buflen && ch == 'c') {
                                        scanCommentChar();
                                        if (bp < buflen && ch == 'a') {
                                            scanCommentChar();
                                            if (bp < buflen && ch == 't') {
                                                scanCommentChar();
                                                if (bp < buflen && ch == 'e') {
                                                    scanCommentChar();
                                                    if (bp < buflen && ch == 'd') {
                                                        deprecatedPrefix = true;
                                                        scanCommentChar();
                                                    }}}}}}}}}}}
            if (deprecatedPrefix && bp < buflen) {
                if (Character.isWhitespace(ch)) {
                    deprecatedFlag = true;
                } else if (ch == '*') {
                    scanCommentChar();
                    if (ch == '/') {
                        deprecatedFlag = true;
                        return;
                    }
                }
            }

            // Skip rest of line
            while (bp < buflen) {
                switch (ch) {
                case '*':
                    scanCommentChar();
                    if (ch == '/') {
                        return;
                    }
                    break;
                case CR: // (Spec 3.4)
                    scanCommentChar();
                    if (ch != LF) {
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

    /** The value of a literal token, recorded as a string.
     *  For integers, leading 0x and 'l' suffixes are suppressed.
     */
    public String stringVal() {
        return new String(sbuf, 0, sp);
    }

    /** Read token.
     */
    public void nextToken() {

        try {
            prevEndPos = endPos;
            sp = 0;

            while (true) {
                pos = bp;
                switch (ch) {
                case ' ': // (Spec 3.6)
                case '\t': // (Spec 3.6)
                case FF: // (Spec 3.6)
                    do {
                        scanChar();
                    } while (ch == ' ' || ch == '\t' || ch == FF);
                    endPos = bp;
                    processWhiteSpace();
                    break;
                case LF: // (Spec 3.4)
                    scanChar();
                    endPos = bp;
                    processLineTerminator();
                    break;
                case CR: // (Spec 3.4)
                    scanChar();
                    if (ch == LF) {
                        scanChar();
                    }
                    endPos = bp;
                    processLineTerminator();
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
                    return;
                case '0':
                    scanChar();
                    if (ch == 'x' || ch == 'X') {
                        scanChar();
                        skipIllegalUnderscores();
                        if (ch == '.') {
                            scanHexFractionAndSuffix(false);
                        } else if (digit(16) < 0) {
                            lexError("invalid.hex.number");
                        } else {
                            scanNumber(16);
                        }
                    } else if (ch == 'b' || ch == 'B') {
                        if (!allowBinaryLiterals) {
                            lexError("unsupported.binary.lit", source.name);
                            allowBinaryLiterals = true;
                        }
                        scanChar();
                        skipIllegalUnderscores();
                        if (digit(2) < 0) {
                            lexError("invalid.binary.number");
                        } else {
                            scanNumber(2);
                        }
                    } else {
                        putChar('0');
                        if (ch == '_') {
                            int savePos = bp;
                            do {
                                scanChar();
                            } while (ch == '_');
                            if (digit(10) < 0) {
                                lexError(savePos, "illegal.underscore");
                            }
                        }
                        scanNumber(8);
                    }
                    return;
                case '1': case '2': case '3': case '4':
                case '5': case '6': case '7': case '8': case '9':
                    scanNumber(10);
                    return;
                case '.':
                    scanChar();
                    if ('0' <= ch && ch <= '9') {
                        putChar('.');
                        scanFractionAndSuffix();
                    } else if (ch == '.') {
                        putChar('.'); putChar('.');
                        scanChar();
                        if (ch == '.') {
                            scanChar();
                            putChar('.');
                            token = ELLIPSIS;
                        } else {
                            lexError("malformed.fp.lit");
                        }
                    } else {
                        token = DOT;
                    }
                    return;
                case ',':
                    scanChar(); token = COMMA; return;
                case ';':
                    scanChar(); token = SEMI; return;
                case '(':
                    scanChar(); token = LPAREN; return;
                case ')':
                    scanChar(); token = RPAREN; return;
                case '[':
                    scanChar(); token = LBRACKET; return;
                case ']':
                    scanChar(); token = RBRACKET; return;
                case '{':
                    scanChar(); token = LBRACE; return;
                case '}':
                    scanChar(); token = RBRACE; return;
                case '/':
                    scanChar();
                    if (ch == '/') {
                        do {
                            scanCommentChar();
                        } while (ch != CR && ch != LF && bp < buflen);
                        if (bp < buflen) {
                            endPos = bp;
                            processComment(CommentStyle.LINE);
                        }
                        break;
                    } else if (ch == '*') {
                        scanChar();
                        CommentStyle style;
                        if (ch == '*') {
                            style = CommentStyle.JAVADOC;
                            scanDocComment();
                        } else {
                            style = CommentStyle.BLOCK;
                            while (bp < buflen) {
                                if (ch == '*') {
                                    scanChar();
                                    if (ch == '/') break;
                                } else {
                                    scanCommentChar();
                                }
                            }
                        }
                        if (ch == '/') {
                            scanChar();
                            endPos = bp;
                            processComment(style);
                            break;
                        } else {
                            lexError("unclosed.comment");
                            return;
                        }
                    } else if (ch == '=') {
                        name = names.slashequals;
                        token = SLASHEQ;
                        scanChar();
                    } else {
                        name = names.slash;
                        token = SLASH;
                    }
                    return;
                case '\'':
                    scanChar();
                    if (ch == '\'') {
                        lexError("empty.char.lit");
                    } else {
                        if (ch == CR || ch == LF)
                            lexError(pos, "illegal.line.end.in.char.lit");
                        scanLitChar();
                        if (ch == '\'') {
                            scanChar();
                            token = CHARLITERAL;
                        } else {
                            lexError(pos, "unclosed.char.lit");
                        }
                    }
                    return;
                case '\"':
                    scanChar();
                    while (ch != '\"' && ch != CR && ch != LF && bp < buflen)
                        scanLitChar();
                    if (ch == '\"') {
                        token = STRINGLITERAL;
                        scanChar();
                    } else {
                        lexError(pos, "unclosed.str.lit");
                    }
                    return;
                case '#':
                    scanChar();
                    if (ch == '\"') {
                        if (!allowExoticIdentifiers) {
                            lexError("unsupported.exotic.id", source.name);
                            allowExoticIdentifiers = true;
                        }
                        scanChar();
                        if (ch == '\"')
                            lexError(pos, "empty.bytecode.ident");
                        while (ch != '\"' && ch != CR && ch != LF && bp < buflen) {
                            scanBytecodeNameChar();
                        }
                        if (ch == '\"') {
                            name = names.fromChars(sbuf, 0, sp);
                            token = IDENTIFIER;  // even if #"int" or #"do"
                            scanChar();
                        } else {
                            lexError(pos, "unclosed.bytecode.ident");
                        }
                    } else {
                        lexError("illegal.char", String.valueOf((int)'#'));
                    }
                    return;
                default:
                    if (isSpecial(ch)) {
                        scanOperator();
                    } else {
                        boolean isJavaIdentifierStart;
                        if (ch < '\u0080') {
                            // all ASCII range chars already handled, above
                            isJavaIdentifierStart = false;
                        } else {
                            char high = scanSurrogates();
                            if (high != 0) {
                                if (sp == sbuf.length) {
                                    putChar(high);
                                } else {
                                    sbuf[sp++] = high;
                                }

                                isJavaIdentifierStart = Character.isJavaIdentifierStart(
                                    Character.toCodePoint(high, ch));
                            } else {
                                isJavaIdentifierStart = Character.isJavaIdentifierStart(ch);
                            }
                        }
                        if (isJavaIdentifierStart) {
                            scanIdent();
                        } else if (bp == buflen || ch == EOI && bp+1 == buflen) { // JLS 3.5
                            token = EOF;
                            pos = bp = eofPos;
                        } else {
                            lexError("illegal.char", String.valueOf((int)ch));
                            scanChar();
                        }
                    }
                    return;
                }
            }
        } finally {
            endPos = bp;
            if (scannerDebug)
                System.out.println("nextToken(" + pos
                                   + "," + endPos + ")=|" +
                                   new String(getRawCharacters(pos, endPos))
                                   + "|");
        }
    }

    /** Return the current token, set by nextToken().
     */
    public Token token() {
        return token;
    }

    /** Sets the current token.
     */
    public void token(Token token) {
        this.token = token;
    }

    /** Return the current token's position: a 0-based
     *  offset from beginning of the raw input stream
     *  (before unicode translation)
     */
    public int pos() {
        return pos;
    }

    /** Return the last character position of the current token.
     */
    public int endPos() {
        return endPos;
    }

    /** Return the last character position of the previous token.
     */
    public int prevEndPos() {
        return prevEndPos;
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

    /** Return the name of an identifier or token for the current token.
     */
    public Name name() {
        return name;
    }

    /** Return the radix of a numeric literal token.
     */
    public int radix() {
        return radix;
    }

    /** Has a @deprecated been encountered in last doc comment?
     *  This needs to be reset by client with resetDeprecatedFlag.
     */
    public boolean deprecatedFlag() {
        return deprecatedFlag;
    }

    public void resetDeprecatedFlag() {
        deprecatedFlag = false;
    }

    /**
     * Returns the documentation string of the current token.
     */
    public String docComment() {
        return null;
    }

    /**
     * Returns a copy of the input buffer, up to its inputLength.
     * Unicode escape sequences are not translated.
     */
    public char[] getRawCharacters() {
        char[] chars = new char[buflen];
        System.arraycopy(buf, 0, chars, 0, buflen);
        return chars;
    }

    /**
     * Returns a copy of a character array subset of the input buffer.
     * The returned array begins at the <code>beginIndex</code> and
     * extends to the character at index <code>endIndex - 1</code>.
     * Thus the length of the substring is <code>endIndex-beginIndex</code>.
     * This behavior is like
     * <code>String.substring(beginIndex, endIndex)</code>.
     * Unicode escape sequences are not translated.
     *
     * @param beginIndex the beginning index, inclusive.
     * @param endIndex the ending index, exclusive.
     * @throws IndexOutOfBounds if either offset is outside of the
     *         array bounds
     */
    public char[] getRawCharacters(int beginIndex, int endIndex) {
        int length = endIndex - beginIndex;
        char[] chars = new char[length];
        System.arraycopy(buf, beginIndex, chars, 0, length);
        return chars;
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
    protected void processComment(CommentStyle style) {
        if (scannerDebug)
            System.out.println("processComment(" + pos
                               + "," + endPos + "," + style + ")=|"
                               + new String(getRawCharacters(pos, endPos))
                               + "|");
    }

    /**
     * Called when a complete whitespace run has been scanned. pos and endPos
     * will mark the whitespace boundary.
     */
    protected void processWhiteSpace() {
        if (scannerDebug)
            System.out.println("processWhitespace(" + pos
                               + "," + endPos + ")=|" +
                               new String(getRawCharacters(pos, endPos))
                               + "|");
    }

    /**
     * Called when a line terminator has been processed.
     */
    protected void processLineTerminator() {
        if (scannerDebug)
            System.out.println("processTerminator(" + pos
                               + "," + endPos + ")=|" +
                               new String(getRawCharacters(pos, endPos))
                               + "|");
    }

    /** Build a map for translating between line numbers and
     * positions in the input.
     *
     * @return a LineMap */
    public Position.LineMap getLineMap() {
        return Position.makeLineMap(buf, buflen, false);
    }

}
