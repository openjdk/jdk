/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.javac.code.Lint;
import com.sun.tools.javac.code.Lint.LintCategory;
import com.sun.tools.javac.code.Preview;
import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.code.Source.Feature;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.parser.Tokens.Comment.CommentStyle;
import com.sun.tools.javac.resources.CompilerProperties.Errors;
import com.sun.tools.javac.resources.CompilerProperties.Warnings;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.*;

import java.nio.CharBuffer;
import java.util.Iterator;
import java.util.Set;

import static com.sun.tools.javac.parser.Tokens.*;
import static com.sun.tools.javac.util.LayoutCharacters.EOI;

/**
 * The lexical analyzer maps an input stream consisting of UTF-8 characters and unicode
 * escape sequences into a token sequence.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class JavaTokenizer extends UnicodeReader {
    /**
     * If true then prints token information after each nextToken().
     */
    private static final boolean scannerDebug = false;

    /**
     * Sentinel for non-value.
     */
    private static final int NOT_FOUND = -1;

    /**
     * The source language setting. Copied from scanner factory.
     */
    private final Source source;

    /**
     * The preview language setting. Copied from scanner factory.
     */
    private final Preview preview;

    /**
     * The log to be used for error reporting. Copied from scanner factory.
     */
    private final Log log;

    /**
     * The token factory. Copied from scanner factory.
     */
    private final Tokens tokens;

    /**
     * The names factory. Copied from scanner factory.
     */
    private final Names names;

    /**
     * Origin scanner factory.
     */
    protected final ScannerFactory fac;

    /**
     * Buffer for building literals, used by nextToken().
     */
    protected final StringBuilder sb;

    /**
     * Tokens pending to be read from string template embedded expressions.
     */
    protected List<Token> pendingTokens;

    /**
     * String template fragment ranges; end-endPos pairs.
     */
    protected List<Integer> fragmentRanges;

    /**
     * The token kind, set by nextToken().
     */
    protected TokenKind tk;

    /**
     * The token's radix, set by nextToken().
     */
    protected int radix;

    /**
     * The token's name, set by nextToken().
     */
    protected Name name;

    /**
     * The position where a lexical error occurred;
     */
    protected int errPos = Position.NOPOS;

    /**
     * true if is a text block, set by nextToken().
     */
    protected boolean isTextBlock;

    /**
     * true if contains escape sequences, set by nextToken().
     */
    protected boolean hasEscapeSequences;

    /**
     * true if contains templated string escape sequences, set by nextToken().
     */
    protected boolean isStringTemplate;

    /**
     * true if errors are pending from embedded expressions.
     */
    protected boolean hasStringTemplateErrors;

    /**
     * The set of lint options currently in effect. It is initialized
     * from the context, and then is set/reset as needed by Attr as it
     * visits all the various parts of the trees during attribution.
     */
    protected final Lint lint;

    /**
     * Construct a Java token scanner from the input character buffer.
     *
     * @param fac  the factory which created this Scanner.
     * @param cb   the input character buffer.
     */
    protected JavaTokenizer(ScannerFactory fac, CharBuffer cb) {
        this(fac, JavacFileManager.toArray(cb), cb.limit());
    }

    /**
     * Construct a Java token scanner from the input character array.
     *
     * @param fac      factory which created this Scanner
     * @param array    input character array
     * @param length   length of the meaningful content in the array
     */
    protected JavaTokenizer(ScannerFactory fac, char[] array, int length) {
        super(fac, array, length);
        this.fac = fac;
        this.log = fac.log;
        this.names = fac.names;
        this.tokens = fac.tokens;
        this.source = fac.source;
        this.preview = fac.preview;
        this.lint = fac.lint;
        this.sb = new StringBuilder(256);
        this.pendingTokens = List.nil();
        this.fragmentRanges = List.nil();
    }

    /**
     * Check the source level for a lexical feature.
     *
     * @param pos      position in input buffer.
     * @param feature  feature to verify.
     */
    protected void checkSourceLevel(int pos, Feature feature) {
        if (preview.isPreview(feature) && !preview.isEnabled()) {
            //preview feature without --preview flag, error
            lexError(DiagnosticFlag.SOURCE_LEVEL, pos, preview.disabledError(feature));
        } else if (!feature.allowedInSource(source)) {
            //incompatible source level, error
            lexError(DiagnosticFlag.SOURCE_LEVEL, pos, feature.error(source.name));
        } else if (preview.isPreview(feature)) {
            //use of preview feature, warn
            preview.warnPreview(pos, feature);
        }
    }

    /**
     * Report an error at the given position using the provided arguments.
     *
     * @param pos  position in input buffer.
     * @param key  error key to report.
     */
    protected void lexError(int pos, JCDiagnostic.Error key) {
        log.error(pos, key);
        tk = TokenKind.ERROR;
        errPos = pos;
    }

    /**
     * Report an error at the given position using the provided arguments.
     *
     * @param flags  diagnostic flags.
     * @param pos    position in input buffer.
     * @param key    error key to report.
     */
    protected void lexError(DiagnosticFlag flags, int pos, JCDiagnostic.Error key) {
        log.error(flags, pos, key);
        if (flags != DiagnosticFlag.SOURCE_LEVEL) {
            tk = TokenKind.ERROR;
        }
        errPos = pos;
    }

    /**
     * Report an error at the given position using the provided arguments.
     *
     * @param lc     lint category.
     * @param pos    position in input buffer.
     * @param key    error key to report.
     */
    protected void lexWarning(LintCategory lc, int pos, JCDiagnostic.Warning key) {
        DiagnosticPosition dp = new SimpleDiagnosticPosition(pos) ;
        log.warning(lc, dp, key);
    }

    /**
     * Add a character to the literal buffer.
     *
     * @param ch  character to add.
     */
    protected void put(char ch) {
        sb.append(ch);
    }

    /**
     * Add a codepoint to the literal buffer.
     *
     * @param codePoint  codepoint to add.
     */
    protected void putCodePoint(int codePoint) {
        sb.appendCodePoint(codePoint);
    }

    /**
     * Add current character or codepoint to the literal buffer.
     */
    protected void put() {
        if (isSurrogate()) {
            putCodePoint(getCodepoint());
        } else {
            put(get());
        }
    }

    /**
     * Add a string to the literal buffer.
     */
    protected void put(String string) {
        sb.append(string);
    }

    /**
     * Add current character or codepoint to the literal buffer then return next character.
     */
    protected char putThenNext() {
        put();

        return next();
    }

    /**
     * If the specified character ch matches the current character then add current character
     * to the literal buffer and then advance.
     *
     * @param ch  character to match.
     *
     * @return true if ch matches current character.
     */
    protected boolean acceptThenPut(char ch) {
        if (is(ch)) {
            put(get());
            next();

            return true;
        }

        return false;
    }

    /**
     * If either ch1 or ch2 matches the current character then add current character
     * to the literal buffer and then advance.
     *
     * @param ch1  first character to match.
     * @param ch2  second character to match.
     *
     * @return true if either ch1 or ch2 matches current character.
     */
    protected boolean acceptOneOfThenPut(char ch1, char ch2) {
        if (isOneOf(ch1, ch2)) {
            put(get());
            next();

            return true;
        }

        return false;
    }

    /**
     * Skip and process a line terminator sequence.
     */
    private void skipLineTerminator() {
        int start = position();
        accept('\r');
        accept('\n');
        processLineTerminator(start, position());
    }

    /**
     * Scan the content of a string template expression.
     *
     * @param pos     start of literal
     * @param endPos  start of embedded expression
     */
    private void scanEmbeddedExpression(int pos, int endPos) {
        // If first embedded expression.
        if (!isStringTemplate) {
            checkSourceLevel(pos, Feature.STRING_TEMPLATES);
            fragmentRanges = fragmentRanges.append(pos);
            isStringTemplate = true;
        }
        // Track end of previous fragment.
        fragmentRanges = fragmentRanges.append(endPos);
        // Keep backslash and add rest of placeholder.
        sb.append("{}");

        // Separate tokenizer for the embedded expression.
        JavaTokenizer tokenizer = new JavaTokenizer(fac, buffer(), length());
        tokenizer.reset(position());

        // Track brace depth.
        int braceCount = 0;

        // Accumulate tokens.
        List<Token> tokens = List.nil();

        // Stash first left brace.
        Token token = tokenizer.readToken();
        tokens = tokens.append(token);

        while (isAvailable()) {
            // Read and stash next token.
            token = tokenizer.readToken();
            tokens = tokens.append(token);

            // Intercept errors
            if (token.kind == TokenKind.ERROR) {
                // Track start of next fragment.
                if (isTextBlock) {
                    reset(length());
                } else {
                    skipToEOLN();
                }
                hasStringTemplateErrors = true;
                return;
            }

            if (token.kind == TokenKind.RBRACE) {
                // Potential closing brace.
                if (braceCount == 0) {
                    break;
                }

                braceCount--;
            } else if (token.kind == TokenKind.LBRACE) {
                // Nesting deeper.
                braceCount++;
            } else if (token.kind == TokenKind.STRINGFRAGMENT) {
                tokens = tokens.appendList(tokenizer.pendingTokens);
                tokenizer.pendingTokens = List.nil();
            } else if (token.kind == TokenKind.EOF) {
                break;
            }
        }

        // If no closing brace will be picked up as an unterminated string.

        // Set main tokenizer to continue at next position.
        int position = tokenizer.position();
        reset(position);

        // Track start of next fragment.
        fragmentRanges = fragmentRanges.append(position);

        // Pend the expression tokens after the STRINGFRAGMENT.
        pendingTokens = pendingTokens.appendList(tokens);
    }

    /**
     * Processes the current character and places in the literal buffer. If the current
     * character is a backslash then the next character is assumed to be a proper
     * escape character. Actual conversion of escape sequences takes place
     * during at the end of readToken.
     *
     * @param pos       position of the first character in literal.
     * @param isString  true if is a string literal
     */
    private void scanLitChar(int pos, boolean isString) {
        int backslash = position();
        if (acceptThenPut('\\')) {
            hasEscapeSequences = true;
            switch (get()) {
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                    char leadch = get();
                    putThenNext();

                    if (inRange('0', '7')) {
                        putThenNext();

                        if (leadch <= '3' && inRange('0', '7')) {
                            putThenNext();
                        }
                    }
                    break;

                case 'b':
                case 't':
                case 'n':
                case 'f':
                case 'r':
                case '\'':
                case '\"':
                case '\\':
                    putThenNext();
                    break;

                case 's':
                    checkSourceLevel(position(), Feature.TEXT_BLOCKS);
                    putThenNext();
                    break;

                case '\n':
                case '\r':
                    if (isTextBlock) {
                        skipLineTerminator();
                        // Normalize line terminator.
                        put('\n');
                    } else {
                        lexError(position(), Errors.IllegalEscChar);
                    }
                    break;

                case '{':
                    if (isString) {
                        scanEmbeddedExpression(pos, backslash);
                        if (hasStringTemplateErrors) {
                            return;
                        }
                    } else {
                        lexError(position(), Errors.IllegalEscChar);
                    }
                    break;

                default:
                    lexError(position(), Errors.IllegalEscChar);
                    break;
            }
        } else {
            putThenNext();
        }
    }

    /**
     * Scan a string literal or text block.
     *
     * @param pos  position of the first character in literal.
     */
    private void scanString(int pos) {
        // Track the end of first line for error recovery.
        int firstEOLN = NOT_FOUND;
        tk = TokenKind.STRINGLITERAL;

        // Check for text block delimiter.
        isTextBlock = accept("\"\"\"");

        if (isTextBlock) {
            // Check if preview feature is enabled for text blocks.
            checkSourceLevel(pos, Feature.TEXT_BLOCKS);

            // Verify the open delimiter sequence.
            // Error if the open delimiter sequence is not """<white space>*<LineTerminator>.
            skipWhitespace();

            if (isEOLN()) {
                skipLineTerminator();
            } else {
                lexError(position(), Errors.IllegalTextBlockOpen);
                return;
            }

            // While characters are available.
            while (isAvailable()) {
                if (hasStringTemplateErrors) {
                    break;
                } else if (accept("\"\"\"")) {
                    if (isStringTemplate && tk == TokenKind.STRINGLITERAL) {
                        tk = TokenKind.STRINGFRAGMENT;
                    }

                    return;
                }

                if (isEOLN()) {
                    skipLineTerminator();
                    // Add normalized line terminator to literal buffer.
                    put('\n');

                    // Record first line terminator for error recovery.
                    if (firstEOLN == NOT_FOUND) {
                        firstEOLN = position();
                    }
                } else {
                    // Add character to string buffer.
                    scanLitChar(pos, true);
                }
            }
        } else {
            // Skip first quote.
            next();

            // While characters are available.
            while (isAvailable()) {
                if (hasStringTemplateErrors) {
                    break;
                } else if (accept('\"')) {
                    if (isStringTemplate && tk == TokenKind.STRINGLITERAL) {
                        tk = TokenKind.STRINGFRAGMENT;
                    }
                    return;
                }

                if (isEOLN()) {
                    // Line terminator in string literal is an error.
                    // Fall out to unclosed string literal error.
                    break;
                } else {
                    // Add character to string buffer.
                    scanLitChar(pos, true);
                }
            }
        }

        // String ended without close delimiter sequence or has embedded expression errors.
        if (isStringTemplate) {
            lexError(pos, isTextBlock ? Errors.TextBlockTemplateIsNotWellFormed
                                      : Errors.StringTemplateIsNotWellFormed);
            fragmentRanges = List.nil();
            pendingTokens = List.nil();
        } else {
            lexError(pos, isTextBlock ? Errors.UnclosedTextBlock
                                      : Errors.UnclosedStrLit);
        }

        if (!hasStringTemplateErrors && firstEOLN  != NOT_FOUND) {
            // Reset recovery position to point after text block open delimiter sequence.
            reset(firstEOLN);
        }
    }

    /**
     * Scan sequence of digits.
     *
     * @param pos         position of the first character in literal.
     * @param digitRadix  radix of numeric literal.
     */
    private void scanDigits(int pos, int digitRadix) {
        int leadingUnderscorePos = is('_') ? position() : NOT_FOUND;
        int trailingUnderscorePos;

        do {
            if (!is('_')) {
                put();
                trailingUnderscorePos = NOT_FOUND;
            } else {
                trailingUnderscorePos = position();
            }

            next();
        } while (digit(pos, digitRadix) >= 0 || is('_'));

        if (leadingUnderscorePos != NOT_FOUND) {
            lexError(leadingUnderscorePos, Errors.IllegalUnderscore);
        } else if (trailingUnderscorePos != NOT_FOUND) {
            lexError(trailingUnderscorePos, Errors.IllegalUnderscore);
        }
    }

    /**
     * Read fractional part of hexadecimal floating point number.
     *
     * @param pos  position of the first character in literal.
     */
    private void scanHexExponentAndSuffix(int pos) {
        if (acceptOneOfThenPut('p', 'P')) {
            skipIllegalUnderscores();
            acceptOneOfThenPut('+', '-');
            skipIllegalUnderscores();

            if (digit(pos, 10) >= 0) {
                scanDigits(pos, 10);
            } else {
                lexError(pos, Errors.MalformedFpLit);
            }
        } else {
            lexError(pos, Errors.MalformedFpLit);
        }

        if (acceptOneOfThenPut('f', 'F')) {
            tk = TokenKind.FLOATLITERAL;
            radix = 16;
        } else {
            acceptOneOfThenPut('d', 'D');
            tk = TokenKind.DOUBLELITERAL;
            radix = 16;
        }
    }

    /**
     * Read fractional part of floating point number.
     *
     * @param pos  position of the first character in literal.
     */
    private void scanFraction(int pos) {
        skipIllegalUnderscores();

        if (digit(pos, 10) >= 0) {
            scanDigits(pos, 10);
        }

        int index = sb.length();

        if (acceptOneOfThenPut('e', 'E')) {
            skipIllegalUnderscores();
            acceptOneOfThenPut('+', '-');
            skipIllegalUnderscores();

            if (digit(pos, 10) >= 0) {
                scanDigits(pos, 10);
                return;
            }

            lexError(pos, Errors.MalformedFpLit);
            sb.setLength(index);
        }
    }

    /**
     * Read fractional part and 'd' or 'f' suffix of floating point number.
     *
     * @param pos  position of the first character in literal.
     */
    private void scanFractionAndSuffix(int pos) {
        radix = 10;
        scanFraction(pos);

        if (acceptOneOfThenPut('f', 'F')) {
             tk = TokenKind.FLOATLITERAL;
        } else {
            acceptOneOfThenPut('d', 'D');
            tk = TokenKind.DOUBLELITERAL;
        }
    }

    /**
     * Read fractional part and 'd' or 'f' suffix of hexadecimal floating point number.
     *
     * @param pos  position of the first character in literal.
     */
    private void scanHexFractionAndSuffix(int pos, boolean seendigit) {
        radix = 16;
        Assert.check(is('.'));
        putThenNext();
        skipIllegalUnderscores();

        if (digit(pos, 16) >= 0) {
            seendigit = true;
            scanDigits(pos, 16);
        }

        if (!seendigit)
            lexError(pos, Errors.InvalidHexNumber);
        else
            scanHexExponentAndSuffix(pos);
    }

    /**
     * Skip over underscores and report as a error if found.
     */
    private void skipIllegalUnderscores() {
        if (is('_')) {
            lexError(position(), Errors.IllegalUnderscore);
            skip('_');
        }
    }

    /**
     * Read a number. (Spec. 3.10)
     *
     * @param pos    position of the first character in literal.
     * @param radix  the radix of the number; one of 2, 8, 10, 16.
     */
    private void scanNumber(int pos, int radix) {
        // for octal, allow base-10 digit in case it's a float literal
        this.radix = radix;
        boolean permitFloatingPoint = radix == 8 || radix == 10;
        int digitRadix = Math.max(10, radix);
        int firstDigit = digit(pos, digitRadix);
        boolean seendigit = firstDigit >= 0;
        boolean seenValidDigit = firstDigit >= 0 && firstDigit < digitRadix;

        if (seendigit) {
            scanDigits(pos, digitRadix);
        }

        if (radix == 16 && is('.')) {
            scanHexFractionAndSuffix(pos, seendigit);
        } else if (seendigit && radix == 16 && isOneOf('p', 'P')) {
            scanHexExponentAndSuffix(pos);
        } else if (permitFloatingPoint && is('.')) {
            putThenNext();
            scanFractionAndSuffix(pos);
        } else if (permitFloatingPoint && isOneOf('e', 'E', 'f', 'F', 'd', 'D')) {
            scanFractionAndSuffix(pos);
        } else {
            if (!seenValidDigit) {
                switch (radix) {
                case 2:
                    lexError(pos, Errors.InvalidBinaryNumber);
                    break;
                case 16:
                    lexError(pos, Errors.InvalidHexNumber);
                    break;
                }
            }

            if (acceptOneOf('l', 'L')) {
                tk = TokenKind.LONGLITERAL;
            } else {
                tk = TokenKind.INTLITERAL;
            }
        }
    }

    /**
     * Determines if the sequence in the literal buffer is a token (keyword, operator.)
     */
    private void checkIdent() {
        name = names.fromString(sb.toString());
        tk = tokens.lookupKind(name);
    }

    /**
     * Read an identifier. (Spec. 3.8)
     */
    private void scanIdent() {
        putThenNext();

        do {
            switch (get()) {
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
                break;

            case '\u0000': case '\u0001': case '\u0002': case '\u0003':
            case '\u0004': case '\u0005': case '\u0006': case '\u0007':
            case '\u0008': case '\u000E': case '\u000F': case '\u0010':
            case '\u0011': case '\u0012': case '\u0013': case '\u0014':
            case '\u0015': case '\u0016': case '\u0017':
            case '\u0018': case '\u0019': case '\u001B':
            case '\u007F':
                next();
                continue;

            case '\u001A': // EOI is also a legal identifier part
                if (isAvailable()) {
                    next();
                    continue;
                }

                checkIdent();
                return;

            default:
                boolean isJavaIdentifierPart;

                if (isASCII()) {
                    // all ASCII range chars already handled, above
                    isJavaIdentifierPart = false;
                } else {
                    if (Character.isIdentifierIgnorable(get())) {
                        next();
                        continue;
                    }

                    isJavaIdentifierPart = isSurrogate()
                            ? Character.isJavaIdentifierPart(getCodepoint())
                            : Character.isJavaIdentifierPart(get());
                }

                if (!isJavaIdentifierPart) {
                    checkIdent();
                    return;
                }
            }

            putThenNext();
        } while (true);
    }

    /**
     * Return true if ch can be part of an operator.
     *
     * @param ch  character to check.
     *
     * @return true if ch can be part of an operator.
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

    /**
     * Read longest possible sequence of special characters and convert to token.
     */
    private void scanOperator() {
        while (true) {
            put();
            TokenKind newtk = tokens.lookupKind(sb.toString());

            if (newtk == TokenKind.IDENTIFIER) {
                sb.setLength(sb.length() - 1);
                break;
            }

            tk = newtk;
            next();

            if (!isSpecial(get())) {
                break;
            }
        }
    }

    /**
     * Read token (main entrypoint.)
     */
    public Token readToken() {
        if (pendingTokens.nonEmpty()) {
            Token token = pendingTokens.head;
            pendingTokens = pendingTokens.tail;
            return token;
        }

        sb.setLength(0);
        name = null;
        radix = 0;
        isTextBlock = false;
        hasEscapeSequences = false;
        isStringTemplate = false;
        hasStringTemplateErrors = false;
        fragmentRanges = List.nil();

        int pos;
        List<Comment> comments = null;

        try {
            loop: while (true) {
                pos = position();

                switch (get()) {
                case ' ':  // (Spec 3.6)
                case '\t': // (Spec 3.6)
                case '\f': // (Spec 3.6)
                    skipWhitespace();
                    processWhiteSpace(pos, position());
                    break;

                case '\n': // (Spec 3.4)
                    next();
                    processLineTerminator(pos, position());
                    break;

                case '\r': // (Spec 3.4)
                    next();
                    accept('\n');
                    processLineTerminator(pos, position());
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
                case '$': case '_': // (Spec. 3.8)
                    scanIdent();
                    break loop;

                case '0': // (Spec. 3.10)
                    next();

                    if (acceptOneOf('x', 'X')) {
                        skipIllegalUnderscores();
                        scanNumber(pos, 16);
                    } else if (acceptOneOf('b', 'B')) {
                        skipIllegalUnderscores();
                        scanNumber(pos, 2);
                    } else {
                        put('0');

                        if (is('_')) {
                            int savePos = position();
                            skip('_');

                            if (digit(pos, 10) < 0) {
                                lexError(savePos, Errors.IllegalUnderscore);
                            }
                        }

                        scanNumber(pos, 8);
                    }
                    break loop;

                case '1': case '2': case '3': case '4':
                case '5': case '6': case '7': case '8': case '9':  // (Spec. 3.10)
                    scanNumber(pos, 10);
                    break loop;

                case '.': // (Spec. 3.12)
                    if (accept("...")) {
                        put("...");
                        tk = TokenKind.ELLIPSIS;
                    } else {
                        next();
                        int savePos = position();

                        if (accept('.')) {
                            lexError(savePos, Errors.IllegalDot);
                        } else if (digit(pos, 10) >= 0) {
                            put('.');
                            scanFractionAndSuffix(pos); // (Spec. 3.10)
                        } else {
                            tk = TokenKind.DOT;
                        }
                    }
                    break loop;

                case ',': // (Spec. 3.12)
                    next();
                    tk = TokenKind.COMMA;
                    break loop;

                case ';': // (Spec. 3.12)
                    next();
                    tk = TokenKind.SEMI;
                    break loop;

                case '(': // (Spec. 3.12)
                    next();
                    tk = TokenKind.LPAREN;
                    break loop;

                case ')': // (Spec. 3.12)
                    next();
                    tk = TokenKind.RPAREN;
                    break loop;

                case '[': // (Spec. 3.12)
                    next();
                    tk = TokenKind.LBRACKET;
                    break loop;

                case ']': // (Spec. 3.12)
                    next();
                    tk = TokenKind.RBRACKET;
                    break loop;

                case '{': // (Spec. 3.12)
                    next();
                    tk = TokenKind.LBRACE;
                    break loop;

                case '}': // (Spec. 3.12)
                    next();
                    tk = TokenKind.RBRACE;
                    break loop;

                case '/':
                    next();

                    if (accept('/')) { // (Spec. 3.7)
                        skipToEOLN();

                        if (isAvailable()) {
                            comments = appendComment(comments, processComment(pos, position(), CommentStyle.LINE));
                        }
                        break;
                    } else if (accept('*')) { // (Spec. 3.7)
                        boolean isEmpty = false;
                        CommentStyle style;

                        if (accept('*')) {
                            style = CommentStyle.JAVADOC;

                            if (is('/')) {
                                isEmpty = true;
                            }
                        } else {
                            style = CommentStyle.BLOCK;
                        }

                        if (!isEmpty) {
                            while (isAvailable()) {
                                if (accept('*')) {
                                    if (is('/')) {
                                        break;
                                    }
                                } else {
                                    next();
                                }
                            }
                        }

                        if (accept('/')) {
                            comments = appendComment(comments, processComment(pos, position(), style));

                            break;
                        } else {
                            lexError(pos, Errors.UnclosedComment);

                            break loop;
                        }
                    } else if (accept('=')) {
                        tk = TokenKind.SLASHEQ; // (Spec. 3.12)
                    } else {
                        tk = TokenKind.SLASH; // (Spec. 3.12)
                    }
                    break loop;

                case '\'': // (Spec. 3.10)
                    next();

                    if (accept('\'')) {
                        lexError(pos, Errors.EmptyCharLit);
                    } else {
                        if (isEOLN()) {
                            lexError(pos, Errors.IllegalLineEndInCharLit);
                        }

                        scanLitChar(pos, false);

                        if (tk == TokenKind.ERROR) {
                            while (isAvailable() && !is('\'')) {
                                if (is('\\')) {
                                    next();
                                }
                                next();
                            }
                            accept('\'');
                        } else if (accept('\'')) {
                            tk = TokenKind.CHARLITERAL;
                        } else {
                            lexError(pos, Errors.UnclosedCharLit);
                        }
                    }
                    break loop;
                case '\"': // (Spec. 3.10)
                    scanString(pos);
                    break loop;

                default:
                    if (isSpecial(get())) {
                        scanOperator();
                    } else {
                        boolean isJavaIdentifierStart;

                        if (isASCII()) {
                            // all ASCII range chars already handled, above
                            isJavaIdentifierStart = false;
                        } else {
                            isJavaIdentifierStart = isSurrogate()
                                    ? Character.isJavaIdentifierStart(getCodepoint())
                                    : Character.isJavaIdentifierStart(get());
                        }

                        if (isJavaIdentifierStart) {
                            scanIdent();
                        } else if (digit(pos, 10) >= 0) {
                            scanNumber(pos, 10);
                        } else if (is((char)EOI) || !isAvailable()) {
                            tk = TokenKind.EOF;
                            pos = position();
                        } else {
                            String arg;

                            if (isSurrogate()) {
                                int codePoint = getCodepoint();
                                char hi = Character.highSurrogate(codePoint);
                                char lo = Character.lowSurrogate(codePoint);
                                arg = String.format("\\u%04x\\u%04x", (int) hi, (int) lo);
                            } else {
                                char ch = get();
                                arg = (32 < ch && ch < 127) ? String.valueOf(ch) :
                                                              "\\u%04x".formatted((int) ch);
                            }

                            lexError(pos, Errors.IllegalChar(arg));
                            next();
                        }
                    }
                    break loop;
                }
            }

            int endPos = position();

            // Track end of final fragment.
            if (isStringTemplate) {
                fragmentRanges = fragmentRanges.append(endPos);
            }

            if (tk.tag == Token.Tag.DEFAULT) {
                return new Token(tk, pos, endPos, comments);
            } else  if (tk.tag == Token.Tag.NAMED) {
                return new NamedToken(tk, pos, endPos, name, comments);
            } else {
                // Get characters from string buffer.
                String string = sb.toString();

                // If a text block.
                if (isTextBlock) {
                    // Verify that the incidental indentation is consistent.
                    if (lint.isEnabled(LintCategory.TEXT_BLOCKS)) {
                        Set<TextBlockSupport.WhitespaceChecks> checks =
                                TextBlockSupport.checkWhitespace(string);
                        if (checks.contains(TextBlockSupport.WhitespaceChecks.INCONSISTENT)) {
                            lexWarning(LintCategory.TEXT_BLOCKS, pos,
                                    Warnings.InconsistentWhiteSpaceIndentation);
                        }
                        if (checks.contains(TextBlockSupport.WhitespaceChecks.TRAILING)) {
                            lexWarning(LintCategory.TEXT_BLOCKS, pos,
                                    Warnings.TrailingWhiteSpaceWillBeRemoved);
                        }
                    }
                    // Remove incidental indentation.
                    try {
                        string = string.stripIndent();
                    } catch (Exception ex) {
                        // Error already reported, just use unstripped string.
                    }
                }

                if (isStringTemplate) {
                    // Break string into fragments and then return the first of the framents.
                    return getFragments(string, comments);
                }

                // Translate escape sequences if present.
                if (hasEscapeSequences) {
                    try {
                        string = string.translateEscapes();
                    } catch (Exception ex) {
                        // Error already reported, just use untranslated string.
                    }
                }

                if (tk.tag == Token.Tag.STRING) {
                    // Build string token.
                    return new StringToken(tk, pos, endPos, string, comments);
                } else {
                    // Build numeric token.
                    return new NumericToken(tk, pos, endPos, string, radix, comments);
                }
            }
        } finally {
            int endPos = position();

            if (scannerDebug) {
                    System.out.println("nextToken(" + pos
                                       + "," + endPos + ")=|" +
                                       getRawString(pos, endPos)
                                       + "|");
            }
        }
    }

    /**
     * Convert the string into a list of pending tokens to precede embedded
     * expressions.
     *
     * @param string    string to fragment
     * @param comments  comments for first token
     *
     * @return first pending token.
     */
    private Token getFragments(String string, List<Comment> comments) {
        List<Token> tokens = List.nil();
        Iterator<Integer> rangeIter = fragmentRanges.iterator();
        for (String fragment : fragment(string)) {
            fragment = fragment.translateEscapes();
            int fragmentPos = rangeIter.next();
            int fragmentEndPos = rangeIter.next();
            Token token = new StringToken(TokenKind.STRINGFRAGMENT,
                    fragmentPos, fragmentEndPos, fragment, comments);
            comments = null;
            tokens = tokens.append(token);
        }
        pendingTokens = tokens.appendList(pendingTokens);
        Token first = pendingTokens.head;
        pendingTokens = pendingTokens.tail;
        return first;
    }

    /**
     * Break string template up into fragments. "\{}" indicates where
     * embedded expressions occur.
     *
     * @param string string template
     *
     * @return list of fragment strings
     */
    List<String> fragment(String string) {
        List<String> fragments = List.nil();
        StringBuilder sb = new StringBuilder();
        int length = string.length();
        for (int i = 0; i < length; i++) {
            char ch = string.charAt(i);
            if (ch != '\\') {
                sb.append(ch);
            } else if (i + 2 < length && string.charAt(i + 1) == '{'
                    && string.charAt(i + 2) == '}') {
                fragments = fragments.append(sb.toString());
                sb.setLength(0);
                i += 2;
            } else if (i + 1 < length){
                sb.append('\\');
                sb.append(string.charAt(i + 1));
                i++;
            } else {
                // Error already reported.
            }
        }
        fragments = fragments.append(sb.toString());
        return fragments;
    }

    /**
     * Appends a comment to the list of comments preceding the current token.
     *
     * @param comments  existing list of comments.
     * @param comment   comment to append.
     *
     * @return new list with comment prepended to the existing list.
     */
    List<Comment> appendComment(List<Comment> comments, Comment comment) {
        return comments == null ?
                List.of(comment) :
                comments.prepend(comment);
    }

    /**
     * Return the position where a lexical error occurred.
     *
     * @return position in the input buffer of where the error occurred.
     */
    public int errPos() {
        return errPos;
    }

    /**
     * Set the position where a lexical error occurred.
     *
     * @param pos  position in the input buffer of where the error occurred.
     */
    public void errPos(int pos) {
        errPos = pos;
    }

    /**
     * Called when a complete comment has been scanned. pos and endPos
     * will mark the comment boundary.
     *
     * @param pos     position of the opening / in the input buffer.
     * @param endPos  position + 1 of the closing / in the input buffer.
     * @param style   style of comment.
     *
     * @return the constructed BasicComment.
     */
    protected Tokens.Comment processComment(int pos, int endPos, CommentStyle style) {
        if (scannerDebug) {
            System.out.println("processComment(" + pos
                                + "," + endPos + "," + style + ")=|"
                                + getRawString(pos, endPos)
                                + "|");
        }

        return new BasicComment(style,this, pos, endPos);
    }

    /**
     * Called when a complete whitespace run has been scanned. pos and endPos
     * will mark the whitespace boundary.
     *
     * (Spec 3.6)
     *
     * @param pos     position in input buffer of first whitespace character.
     * @param endPos  position + 1 in input buffer of last whitespace character.
     */
    protected void processWhiteSpace(int pos, int endPos) {
        if (scannerDebug) {
            System.out.println("processWhitespace(" + pos
                                + "," + endPos + ")=|"
                                + getRawString(pos, endPos)
                                + "|");
        }
    }

    /**
     * Called when a line terminator has been processed.
     *
     * @param pos     position in input buffer of first character in sequence.
     * @param endPos  position + 1 in input buffer of last character in sequence.
     */
    protected void processLineTerminator(int pos, int endPos) {
        if (scannerDebug) {
            System.out.println("processTerminator(" + pos
                                + "," + endPos + ")=|"
                                + getRawString(pos, endPos)
                                + "|");
        }
    }

    /**
     * Build a map for translating between line numbers and positions in the input.
     *
     * @return a LineMap
     */
    public Position.LineMap getLineMap() {
        return Position.makeLineMap(getRawCharacters(), length(), false);
    }

    /**
     * Scan a documentation comment; determine if a deprecated tag is present.
     * Called once the initial /, * have been skipped, positioned at the second *
     * (which is treated as the beginning of the first line).
     * Stops positioned at the closing '/'.
     */
    protected static class BasicComment extends PositionTrackingReader implements Comment {
        /**
         * Style of comment
         */
        CommentStyle cs;

        /**
         * true if comment contains @deprecated at beginning of a line.
         */
        protected boolean deprecatedFlag = false;

        /**
         * true if comment has been fully scanned.
         */
        protected boolean scanned = false;

        /**
         * Constructor.
         *
         * @param cs     comment style
         * @param reader existing reader
         * @param pos    start of meaningful content in buffer.
         * @param endPos end of meaningful content in buffer.
         */
        protected BasicComment(CommentStyle cs, UnicodeReader reader, int pos, int endPos) {
            super(reader, pos, endPos);
            this.cs = cs;
        }

        /**
         * Return comment body text minus comment adornments or null if not scanned.
         *
         * @return comment body text.
         */
        public String getText() {
            return null;
        }

        /**
         * Return buffer position in original buffer mapped from buffer position in comment.
         *
         * @param pos buffer position in comment.
         * @return buffer position in original buffer.
         */
        public int getSourcePos(int pos) {
            return -1;
        }

        /**
         * Return style of comment.
         *
         * @return style of comment.
         */
        public CommentStyle getStyle() {
            return cs;
        }

        /**
         * true if comment contains @deprecated at beginning of a line.
         *
         * @return true if comment contains @deprecated.
         */
        public boolean isDeprecated() {
            if (!scanned) {
                scanDocComment();
            }
            return deprecatedFlag;
        }

        /**
         * Remove closing star(s) slash from comment.
         *
         * @param line line reader
         *
         * @return new line reader if detected otherwise original line reader.
         */
        UnicodeReader trimEndOfComment(UnicodeReader line) {
            int pos = line.position();
            boolean allWhitespace = true;

            while (line.isAvailable()) {
                int endPos = line.position();

                if (line.skip('*') != 0 && line.is('/')) {
                    return line.lineReader(allWhitespace ? endPos : pos, endPos);
                } else {
                    allWhitespace = allWhitespace && line.isWhitespace();
                    line.next();
                }
            }

            line.reset(pos);

            return line;
        }

        /**
         * Trim the first part of the JavaDoc comment.
         *
         * @param line line reader
         *
         * @return modified line reader
         */
        UnicodeReader trimJavadocComment(UnicodeReader line) {
            line = trimEndOfComment(line);
            int pos = line.position();
            line.skipWhitespace();

            if (!line.isAvailable()) {
                return line;
            }

            if (line.skip('*') == 0) {
                line.reset(pos);
            }

            return line;
        }

        /**
         * Put the line into the buffer.
         *
         * @param line line reader
         */
        protected void putLine(UnicodeReader line) {
            // ignore, overridden in subclass
        }

        /**
         * Scan document comment for content.
         */
        protected void scanDocComment() {
            if (!scanned) {
                deprecatedFlag = false;
                scanned = true;

                if (!accept("/**")) {
                    return;
                }

                if (skip('*') != 0 && is('/')) {
                    return ;
                }

                skipWhitespace();

                if (isEOLN()) {
                    accept('\r');
                    accept('\n');
                }

                while (isAvailable()) {
                    UnicodeReader line = lineReader();
                    line = trimJavadocComment(line);

                    // If standalone @deprecated tag
                    int pos = line.position();
                    line.skipWhitespace();

                    if (line.accept("@deprecated") &&
                            (!line.isAvailable() ||
                                    line.isWhitespace() ||
                                    line.isEOLN() ||
                                    line.get() == EOI)) {
                        deprecatedFlag = true;
                    }

                    line.reset(pos);
                    putLine(line);
                }
            }
        }
    }
}
