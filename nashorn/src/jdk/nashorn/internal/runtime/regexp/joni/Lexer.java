/*
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package jdk.nashorn.internal.runtime.regexp.joni;

import static jdk.nashorn.internal.runtime.regexp.joni.Option.isSingleline;
import static jdk.nashorn.internal.runtime.regexp.joni.ast.QuantifierNode.isRepeatInfinite;

import jdk.nashorn.internal.runtime.regexp.joni.ast.QuantifierNode;
import jdk.nashorn.internal.runtime.regexp.joni.constants.AnchorType;
import jdk.nashorn.internal.runtime.regexp.joni.constants.MetaChar;
import jdk.nashorn.internal.runtime.regexp.joni.constants.TokenType;
import jdk.nashorn.internal.runtime.regexp.joni.encoding.CharacterType;
import jdk.nashorn.internal.runtime.regexp.joni.exception.ErrorMessages;

class Lexer extends ScannerSupport {
    protected final ScanEnvironment env;
    protected final Syntax syntax;              // fast access to syntax
    protected final Token token = new Token();  // current token

    protected Lexer(ScanEnvironment env, char[] chars, int p, int end) {
        super(chars, p, end);
        this.env = env;
        this.syntax = env.syntax;
    }

    /**
     * @return 0: normal {n,m}, 2: fixed {n}
     * !introduce returnCode here
     */
    private int fetchRangeQuantifier() {
        mark();
        boolean synAllow = syntax.allowInvalidInterval();

        if (!left()) {
            if (synAllow) {
                return 1; /* "....{" : OK! */
            } else {
                newSyntaxException(ERR_END_PATTERN_AT_LEFT_BRACE);
            }
        }

        if (!synAllow) {
            c = peek();
            if (c == ')' || c == '(' || c == '|') {
                newSyntaxException(ERR_END_PATTERN_AT_LEFT_BRACE);
            }
        }

        int low = scanUnsignedNumber();
        if (low < 0) newSyntaxException(ErrorMessages.ERR_TOO_BIG_NUMBER_FOR_REPEAT_RANGE);
        if (low > Config.MAX_REPEAT_NUM) newSyntaxException(ErrorMessages.ERR_TOO_BIG_NUMBER_FOR_REPEAT_RANGE);

        boolean nonLow = false;
        if (p == _p) { /* can't read low */
            if (syntax.allowIntervalLowAbbrev()) {
                low = 0;
                nonLow = true;
            } else {
                return invalidRangeQuantifier(synAllow);
            }
        }

        if (!left()) return invalidRangeQuantifier(synAllow);

        fetch();
        int up;
        int ret = 0;
        if (c == ',') {
            int prev = p; // ??? last
            up = scanUnsignedNumber();
            if (up < 0) newValueException(ERR_TOO_BIG_NUMBER_FOR_REPEAT_RANGE);
            if (up > Config.MAX_REPEAT_NUM) newValueException(ERR_TOO_BIG_NUMBER_FOR_REPEAT_RANGE);

            if (p == prev) {
                if (nonLow) return invalidRangeQuantifier(synAllow);
                up = QuantifierNode.REPEAT_INFINITE; /* {n,} : {n,infinite} */
            }
        } else {
            if (nonLow) return invalidRangeQuantifier(synAllow);
            unfetch();
            up = low; /* {n} : exact n times */
            ret = 2; /* fixed */
        }

        if (!left()) return invalidRangeQuantifier(synAllow);
        fetch();

        if (syntax.opEscBraceInterval()) {
            if (c != syntax.metaCharTable.esc) return invalidRangeQuantifier(synAllow);
            fetch();
        }

        if (c != '}') return invalidRangeQuantifier(synAllow);

        if (!isRepeatInfinite(up) && low > up) {
            newValueException(ERR_UPPER_SMALLER_THAN_LOWER_IN_REPEAT_RANGE);
        }

        token.type = TokenType.INTERVAL;
        token.setRepeatLower(low);
        token.setRepeatUpper(up);

        return ret; /* 0: normal {n,m}, 2: fixed {n} */
    }

    private int invalidRangeQuantifier(boolean synAllow) {
        if (synAllow) {
            restore();
            return 1;
        } else {
            newSyntaxException(ERR_INVALID_REPEAT_RANGE_PATTERN);
            return 0; // not reached
        }
    }

    /* \M-, \C-, \c, or \... */
    private int fetchEscapedValue() {
        if (!left()) newSyntaxException(ERR_END_PATTERN_AT_ESCAPE);
        fetch();

        switch(c) {

        case 'M':
            if (syntax.op2EscCapitalMBarMeta()) {
                if (!left()) newSyntaxException(ERR_END_PATTERN_AT_META);
                fetch();
                if (c != '-') newSyntaxException(ERR_META_CODE_SYNTAX);
                if (!left()) newSyntaxException(ERR_END_PATTERN_AT_META);
                fetch();
                if (c == syntax.metaCharTable.esc) {
                    c = fetchEscapedValue();
                }
                c = ((c & 0xff) | 0x80);
            } else {
                fetchEscapedValueBackSlash();
            }
            break;

        case 'C':
            if (syntax.op2EscCapitalCBarControl()) {
                if (!left()) newSyntaxException(ERR_END_PATTERN_AT_CONTROL);
                fetch();
                if (c != '-') newSyntaxException(ERR_CONTROL_CODE_SYNTAX);
                fetchEscapedValueControl();
            } else {
                fetchEscapedValueBackSlash();
            }
            break;

        case 'c':
            if (syntax.opEscCControl()) {
                fetchEscapedValueControl();
            }
            /* fall through */

        default:
            fetchEscapedValueBackSlash();
        } // switch

        return c; // ???
    }

    private void fetchEscapedValueBackSlash() {
        c = env.convertBackslashValue(c);
    }

    private void fetchEscapedValueControl() {
        if (!left()) newSyntaxException(ERR_END_PATTERN_AT_CONTROL);
        fetch();
        if (c == '?') {
            c = 0177;
        } else {
            if (c == syntax.metaCharTable.esc) {
                c = fetchEscapedValue();
            }
            c &= 0x9f;
        }
    }

    private int nameEndCodePoint(int start) {
        switch(start) {
        case '<':
            return '>';
        case '\'':
            return '\'';
        default:
            return 0;
        }
    }

    // USE_NAMED_GROUP && USE_BACKREF_AT_LEVEL
    /*
        \k<name+n>, \k<name-n>
        \k<num+n>,  \k<num-n>
        \k<-num+n>, \k<-num-n>
     */

    // #else USE_NAMED_GROUP
    // make it return nameEnd!
    private final int fetchNameForNoNamedGroup(int startCode, boolean ref) {
        int src = p;
        value = 0;

        int isNum = 0;
        int sign = 1;

        int endCode = nameEndCodePoint(startCode);
        int pnumHead = p;
        int nameEnd = stop;

        String err = null;
        if (!left()) {
            newValueException(ERR_EMPTY_GROUP_NAME);
        } else {
            fetch();
            if (c == endCode) newValueException(ERR_EMPTY_GROUP_NAME);

            if (EncodingHelper.isDigit(c)) {
                isNum = 1;
            } else if (c == '-') {
                isNum = 2;
                sign = -1;
                pnumHead = p;
            } else {
                err = ERR_INVALID_CHAR_IN_GROUP_NAME;
            }
        }

        while(left()) {
            nameEnd = p;

            fetch();
            if (c == endCode || c == ')') break;
            if (!EncodingHelper.isDigit(c)) err = ERR_INVALID_CHAR_IN_GROUP_NAME;
        }

        if (err == null && c != endCode) {
            err = ERR_INVALID_GROUP_NAME;
            nameEnd = stop;
        }

        if (err == null) {
            mark();
            p = pnumHead;
            int backNum = scanUnsignedNumber();
            restore();
            if (backNum < 0) {
                newValueException(ERR_TOO_BIG_NUMBER);
            } else if (backNum == 0){
                newValueException(ERR_INVALID_GROUP_NAME, src, nameEnd);
            }
            backNum *= sign;

            value = nameEnd;
            return backNum;
        } else {
            newValueException(err, src, nameEnd);
            return 0; // not reached
        }
    }

    protected final int fetchName(int startCode, boolean ref) {
        return fetchNameForNoNamedGroup(startCode, ref);
    }

    private boolean strExistCheckWithEsc(int[]s, int n, int bad) {
        int p = this.p;
        int to = this.stop;

        boolean inEsc = false;
        int i=0;
        while(p < to) {
            if (inEsc) {
                inEsc = false;
                p ++;
            } else {
                int x = chars[p];
                int q = p + 1;
                if (x == s[0]) {
                    for (i=1; i<n && q < to; i++) {
                        x = chars[q];
                        if (x != s[i]) break;
                        q++;
                    }
                    if (i >= n) return true;
                    p++;
                } else {
                    x = chars[p];
                    if (x == bad) return false;
                    else if (x == syntax.metaCharTable.esc) inEsc = true;
                    p = q;
                }
            }
        }
        return false;
    }

    private static final int send[] = new int[]{':', ']'};

    private void fetchTokenInCCFor_charType(boolean flag, int type) {
        token.type = TokenType.CHAR_TYPE;
        token.setPropCType(type);
        token.setPropNot(flag);
    }

    private void fetchTokenInCCFor_x() {
        if (!left()) return;
        int last = p;

        if (peekIs('{') && syntax.opEscXBraceHex8()) {
            inc();
            int num = scanUnsignedHexadecimalNumber(8);
            if (num < 0) newValueException(ERR_TOO_BIG_WIDE_CHAR_VALUE);
            if (left()) {
                int c2 = peek();
                if (EncodingHelper.isXDigit(c2)) newValueException(ERR_TOO_LONG_WIDE_CHAR_VALUE);
            }

            if (p > last + 1 && left() && peekIs('}')) {
                inc();
                token.type = TokenType.CODE_POINT;
                token.base = 16;
                token.setCode(num);
            } else {
                /* can't read nothing or invalid format */
                p = last;
            }
        } else if (syntax.opEscXHex2()) {
            int num = scanUnsignedHexadecimalNumber(2);
            if (num < 0) newValueException(ERR_TOO_BIG_NUMBER);
            if (p == last) { /* can't read nothing. */
                num = 0; /* but, it's not error */
            }
            token.type = TokenType.RAW_BYTE;
            token.base = 16;
            token.setC(num);
        }
    }

    private void fetchTokenInCCFor_u() {
        if (!left()) return;
        int last = p;

        if (syntax.op2EscUHex4()) {
            int num = scanUnsignedHexadecimalNumber(4);
            if (num < 0) newValueException(ERR_TOO_BIG_NUMBER);
            if (p == last) {  /* can't read nothing. */
                num = 0; /* but, it's not error */
            }
            token.type = TokenType.CODE_POINT;
            token.base = 16;
            token.setCode(num);
        }
    }

    private void fetchTokenInCCFor_digit() {
        if (syntax.opEscOctal3()) {
            unfetch();
            int last = p;
            int num = scanUnsignedOctalNumber(3);
            if (num < 0) newValueException(ERR_TOO_BIG_NUMBER);
            if (p == last) {  /* can't read nothing. */
                num = 0; /* but, it's not error */
            }
            token.type = TokenType.RAW_BYTE;
            token.base = 8;
            token.setC(num);
        }
    }

    private void fetchTokenInCCFor_and() {
        if (syntax.op2CClassSetOp() && left() && peekIs('&')) {
            inc();
            token.type = TokenType.CC_AND;
        }
    }

    protected final TokenType fetchTokenInCC() {
        if (!left()) {
            token.type = TokenType.EOT;
            return token.type;
        }

        fetch();
        token.type = TokenType.CHAR;
        token.base = 0;
        token.setC(c);
        token.escaped = false;

        if (c == ']') {
            token.type = TokenType.CC_CLOSE;
        } else if (c == '-') {
            token.type = TokenType.CC_RANGE;
        } else if (c == syntax.metaCharTable.esc) {
            if (!syntax.backSlashEscapeInCC()) return token.type;
            if (!left()) newSyntaxException(ERR_END_PATTERN_AT_ESCAPE);
            fetch();
            token.escaped = true;
            token.setC(c);

            switch (c) {
            case 'w':
                fetchTokenInCCFor_charType(false, Config.NON_UNICODE_SDW ? CharacterType.W : CharacterType.WORD);
                break;
            case 'W':
                fetchTokenInCCFor_charType(true, Config.NON_UNICODE_SDW ? CharacterType.W : CharacterType.WORD);
                break;
            case 'd':
                fetchTokenInCCFor_charType(false, Config.NON_UNICODE_SDW ? CharacterType.D : CharacterType.DIGIT);
                break;
            case 'D':
                fetchTokenInCCFor_charType(true, Config.NON_UNICODE_SDW ? CharacterType.D : CharacterType.DIGIT);
                break;
            case 's':
                fetchTokenInCCFor_charType(false, Config.NON_UNICODE_SDW ? CharacterType.S : CharacterType.SPACE);
                break;
            case 'S':
                fetchTokenInCCFor_charType(true, Config.NON_UNICODE_SDW ? CharacterType.S : CharacterType.SPACE);
                break;
            case 'h':
                if (syntax.op2EscHXDigit()) fetchTokenInCCFor_charType(false, CharacterType.XDIGIT);
                break;
            case 'H':
                if (syntax.op2EscHXDigit()) fetchTokenInCCFor_charType(true, CharacterType.XDIGIT);
                break;
            case 'x':
                fetchTokenInCCFor_x();
                break;
            case 'u':
                fetchTokenInCCFor_u();
                break;
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
                fetchTokenInCCFor_digit();
                break;

            default:
                unfetch();
                int num = fetchEscapedValue();
                if (token.getC() != num) {
                    token.setCode(num);
                    token.type = TokenType.CODE_POINT;
                }
                break;
            } // switch

        } else if (c == '&') {
            fetchTokenInCCFor_and();
        }
        return token.type;
    }

    private void fetchTokenFor_repeat(int lower, int upper) {
        token.type = TokenType.OP_REPEAT;
        token.setRepeatLower(lower);
        token.setRepeatUpper(upper);
        greedyCheck();
    }

    private void fetchTokenFor_openBrace() {
        switch (fetchRangeQuantifier()) {
        case 0:
            greedyCheck();
            break;
        case 2:
            if (syntax.fixedIntervalIsGreedyOnly()) {
                possessiveCheck();
            } else {
                greedyCheck();
            }
            break;
        default: /* 1 : normal char */
        } // inner switch
    }

    private void fetchTokenFor_anchor(int subType) {
        token.type = TokenType.ANCHOR;
        token.setAnchor(subType);
    }

    private void fetchTokenFor_xBrace() {
        if (!left()) return;

        int last = p;
        if (peekIs('{') && syntax.opEscXBraceHex8()) {
            inc();
            int num = scanUnsignedHexadecimalNumber(8);
            if (num < 0) newValueException(ERR_TOO_BIG_WIDE_CHAR_VALUE);
            if (left()) {
                if (EncodingHelper.isXDigit(peek())) newValueException(ERR_TOO_LONG_WIDE_CHAR_VALUE);
            }

            if (p > last + 1 && left() && peekIs('}')) {
                inc();
                token.type = TokenType.CODE_POINT;
                token.setCode(num);
            } else {
                /* can't read nothing or invalid format */
                p = last;
            }
        } else if (syntax.opEscXHex2()) {
            int num = scanUnsignedHexadecimalNumber(2);
            if (num < 0) newValueException(ERR_TOO_BIG_NUMBER);
            if (p == last) { /* can't read nothing. */
                num = 0; /* but, it's not error */
            }
            token.type = TokenType.RAW_BYTE;
            token.base = 16;
            token.setC(num);
        }
    }

    private void fetchTokenFor_uHex() {
        if (!left()) return;
        int last = p;

        if (syntax.op2EscUHex4()) {
            int num = scanUnsignedHexadecimalNumber(4);
            if (num < 0) newValueException(ERR_TOO_BIG_NUMBER);
            if (p == last) { /* can't read nothing. */
                num = 0; /* but, it's not error */
            }
            token.type = TokenType.CODE_POINT;
            token.base = 16;
            token.setCode(num);
        }
    }

    private void fetchTokenFor_digit() {
        unfetch();
        int last = p;
        int num = scanUnsignedNumber();
        if (num < 0 || num > Config.MAX_BACKREF_NUM) { // goto skip_backref
        } else if (syntax.opDecimalBackref() && (num <= env.numMem || num <= 9)) { /* This spec. from GNU regex */
            if (syntax.strictCheckBackref()) {
                if (num > env.numMem || env.memNodes == null || env.memNodes[num] == null) newValueException(ERR_INVALID_BACKREF);
            }
            token.type = TokenType.BACKREF;
            token.setBackrefNum(1);
            token.setBackrefRef1(num);
            token.setBackrefByName(false);
            return;
        }

        if (c == '8' || c == '9') { /* normal char */ // skip_backref:
            p = last;
            inc();
            return;
        }
        p = last;

        fetchTokenFor_zero(); /* fall through */
    }

    private void fetchTokenFor_zero() {
        if (syntax.opEscOctal3()) {
            int last = p;
            int num = scanUnsignedOctalNumber(c == '0' ? 2 : 3);
            if (num < 0) newValueException(ERR_TOO_BIG_NUMBER);
            if (p == last) { /* can't read nothing. */
                num = 0; /* but, it's not error */
            }
            token.type = TokenType.RAW_BYTE;
            token.base = 8;
            token.setC(num);
        } else if (c != '0') {
            inc();
        }
    }

    private void fetchTokenFor_subexpCall() {
        if (syntax.op2EscGSubexpCall()) {
            if (left()) {
                fetch();
                if (c == '<' || c == '\'') {
                    int last = p;
                    int gNum = fetchName(c, true);
                    int nameEnd = value;
                    token.type = TokenType.CALL;
                    token.setCallNameP(last);
                    token.setCallNameEnd(nameEnd);
                    token.setCallGNum(gNum);
                } else {
                    unfetch();
                    syntaxWarn(Warnings.INVALID_SUBEXP_CALL);
                }
            } else {
                syntaxWarn(Warnings.INVALID_SUBEXP_CALL);
            }
        }
    }

    private void fetchTokenFor_metaChars() {
        if (c == syntax.metaCharTable.anyChar) {
            token.type = TokenType.ANYCHAR;
        } else if (c == syntax.metaCharTable.anyTime) {
            fetchTokenFor_repeat(0, QuantifierNode.REPEAT_INFINITE);
        }  else if (c == syntax.metaCharTable.zeroOrOneTime) {
            fetchTokenFor_repeat(0, 1);
        } else if (c == syntax.metaCharTable.oneOrMoreTime) {
            fetchTokenFor_repeat(1, QuantifierNode.REPEAT_INFINITE);
        } else if (c == syntax.metaCharTable.anyCharAnyTime) {
            token.type = TokenType.ANYCHAR_ANYTIME;
            // goto out
        }
    }

    protected final TokenType fetchToken() {
        // mark(); // out
        start:
        while(true) {
            if (!left()) {
                token.type = TokenType.EOT;
                return token.type;
            }

            token.type = TokenType.STRING;
            token.base = 0;
            token.backP = p;

            fetch();

            if (c == syntax.metaCharTable.esc && !syntax.op2IneffectiveEscape()) { // IS_MC_ESC_CODE(code, syn)
                if (!left()) newSyntaxException(ERR_END_PATTERN_AT_ESCAPE);

                token.backP = p;
                fetch();

                token.setC(c);
                token.escaped = true;
                switch(c) {

                case '*':
                    if (syntax.opEscAsteriskZeroInf()) fetchTokenFor_repeat(0, QuantifierNode.REPEAT_INFINITE);
                    break;
                case '+':
                    if (syntax.opEscPlusOneInf()) fetchTokenFor_repeat(1, QuantifierNode.REPEAT_INFINITE);
                    break;
                case '?':
                    if (syntax.opEscQMarkZeroOne()) fetchTokenFor_repeat(0, 1);
                    break;
                case '{':
                    if (syntax.opEscBraceInterval()) fetchTokenFor_openBrace();
                    break;
                case '|':
                    if (syntax.opEscVBarAlt()) token.type = TokenType.ALT;
                    break;
                case '(':
                    if (syntax.opEscLParenSubexp()) token.type = TokenType.SUBEXP_OPEN;
                    break;
                case ')':
                    if (syntax.opEscLParenSubexp()) token.type = TokenType.SUBEXP_CLOSE;
                    break;
                case 'w':
                    if (syntax.opEscWWord()) fetchTokenInCCFor_charType(false, Config.NON_UNICODE_SDW ? CharacterType.W : CharacterType.WORD);
                    break;
                case 'W':
                    if (syntax.opEscWWord()) fetchTokenInCCFor_charType(true, Config.NON_UNICODE_SDW ? CharacterType.W : CharacterType.WORD);
                    break;
                case 'b':
                    if (syntax.opEscBWordBound()) fetchTokenFor_anchor(AnchorType.WORD_BOUND);
                    break;
                case 'B':
                    if (syntax.opEscBWordBound()) fetchTokenFor_anchor(AnchorType.NOT_WORD_BOUND);
                    break;
                case '<':
                    if (Config.USE_WORD_BEGIN_END && syntax.opEscLtGtWordBeginEnd()) fetchTokenFor_anchor(AnchorType.WORD_BEGIN);
                    break;
                case '>':
                    if (Config.USE_WORD_BEGIN_END && syntax.opEscLtGtWordBeginEnd()) fetchTokenFor_anchor(AnchorType.WORD_END);
                    break;
                case 's':
                    if (syntax.opEscSWhiteSpace()) fetchTokenInCCFor_charType(false, Config.NON_UNICODE_SDW ? CharacterType.S : CharacterType.SPACE);
                    break;
                case 'S':
                    if (syntax.opEscSWhiteSpace()) fetchTokenInCCFor_charType(true, Config.NON_UNICODE_SDW ? CharacterType.S : CharacterType.SPACE);
                    break;
                case 'd':
                    if (syntax.opEscDDigit()) fetchTokenInCCFor_charType(false, Config.NON_UNICODE_SDW ? CharacterType.D : CharacterType.DIGIT);
                    break;
                case 'D':
                    if (syntax.opEscDDigit()) fetchTokenInCCFor_charType(true, Config.NON_UNICODE_SDW ? CharacterType.D : CharacterType.DIGIT);
                    break;
                case 'h':
                    if (syntax.op2EscHXDigit()) fetchTokenInCCFor_charType(false, CharacterType.XDIGIT);
                    break;
                case 'H':
                    if (syntax.op2EscHXDigit()) fetchTokenInCCFor_charType(true, CharacterType.XDIGIT);
                    break;
                case 'A':
                    if (syntax.opEscAZBufAnchor()) fetchTokenFor_anchor(AnchorType.BEGIN_BUF);
                    break;
                case 'Z':
                    if (syntax.opEscAZBufAnchor()) fetchTokenFor_anchor(AnchorType.SEMI_END_BUF);
                    break;
                case 'z':
                    if (syntax.opEscAZBufAnchor()) fetchTokenFor_anchor(AnchorType.END_BUF);
                    break;
                case 'G':
                    if (syntax.opEscCapitalGBeginAnchor()) fetchTokenFor_anchor(AnchorType.BEGIN_POSITION);
                    break;
                case '`':
                    if (syntax.op2EscGnuBufAnchor()) fetchTokenFor_anchor(AnchorType.BEGIN_BUF);
                    break;
                case '\'':
                    if (syntax.op2EscGnuBufAnchor()) fetchTokenFor_anchor(AnchorType.END_BUF);
                    break;
                case 'x':
                    fetchTokenFor_xBrace();
                    break;
                case 'u':
                    fetchTokenFor_uHex();
                    break;
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    fetchTokenFor_digit();
                    break;
                case '0':
                    fetchTokenFor_zero();
                    break;

                default:
                    unfetch();
                    int num = fetchEscapedValue();

                    /* set_raw: */
                    if (token.getC() != num) {
                        token.type = TokenType.CODE_POINT;
                        token.setCode(num);
                    } else { /* string */
                        p = token.backP + 1;
                    }
                    break;

                } // switch (c)

            } else {
                token.setC(c);
                token.escaped = false;

                if (Config.USE_VARIABLE_META_CHARS && (c != MetaChar.INEFFECTIVE_META_CHAR && syntax.opVariableMetaCharacters())) {
                    fetchTokenFor_metaChars();
                    break;
                }

                {
                    switch(c) {
                    case '.':
                        if (syntax.opDotAnyChar()) token.type = TokenType.ANYCHAR;
                        break;
                    case '*':
                        if (syntax.opAsteriskZeroInf()) fetchTokenFor_repeat(0, QuantifierNode.REPEAT_INFINITE);
                        break;
                    case '+':
                        if (syntax.opPlusOneInf()) fetchTokenFor_repeat(1, QuantifierNode.REPEAT_INFINITE);
                        break;
                    case '?':
                        if (syntax.opQMarkZeroOne()) fetchTokenFor_repeat(0, 1);
                        break;
                    case '{':
                        if (syntax.opBraceInterval()) fetchTokenFor_openBrace();
                        break;
                    case '|':
                        if (syntax.opVBarAlt()) token.type = TokenType.ALT;
                        break;

                    case '(':
                        if (peekIs('?') && syntax.op2QMarkGroupEffect()) {
                            inc();
                            if (peekIs('#')) {
                                fetch();
                                while (true) {
                                    if (!left()) newSyntaxException(ERR_END_PATTERN_IN_GROUP);
                                    fetch();
                                    if (c == syntax.metaCharTable.esc) {
                                        if (left()) fetch();
                                    } else {
                                        if (c == ')') break;
                                    }
                                }
                                continue start; // goto start
                            }
                            unfetch();
                        }

                        if (syntax.opLParenSubexp()) token.type = TokenType.SUBEXP_OPEN;
                        break;
                    case ')':
                        if (syntax.opLParenSubexp()) token.type = TokenType.SUBEXP_CLOSE;
                        break;
                    case '^':
                        if (syntax.opLineAnchor()) fetchTokenFor_anchor(isSingleline(env.option) ? AnchorType.BEGIN_BUF : AnchorType.BEGIN_LINE);
                        break;
                    case '$':
                        if (syntax.opLineAnchor()) fetchTokenFor_anchor(isSingleline(env.option) ? AnchorType.SEMI_END_BUF : AnchorType.END_LINE);
                        break;
                    case '[':
                        if (syntax.opBracketCC()) token.type = TokenType.CC_CC_OPEN;
                        break;
                    case ']':
                        //if (*src > env->pattern)   /* /].../ is allowed. */
                        //CLOSE_BRACKET_WITHOUT_ESC_WARN(env, (UChar* )"]");
                        break;
                    case '#':
                        if (Option.isExtend(env.option)) {
                            while (left()) {
                                fetch();
                                if (EncodingHelper.isNewLine(c)) break;
                            }
                            continue start; // goto start
                        }
                        break;

                    case ' ':
                    case '\t':
                    case '\n':
                    case '\r':
                    case '\f':
                        if (Option.isExtend(env.option)) continue start; // goto start
                        break;

                    default: // string
                        break;

                    } // switch
                }
            }

            break;
        } // while
        return token.type;
    }

    private void greedyCheck() {
        if (left() && peekIs('?') && syntax.opQMarkNonGreedy()) {

            fetch();

            token.setRepeatGreedy(false);
            token.setRepeatPossessive(false);
        } else {
            possessiveCheck();
        }
    }

    private void possessiveCheck() {
        if (left() && peekIs('+') &&
            (syntax.op2PlusPossessiveRepeat() && token.type != TokenType.INTERVAL ||
             syntax.op2PlusPossessiveInterval() && token.type == TokenType.INTERVAL)) {

            fetch();

            token.setRepeatGreedy(true);
            token.setRepeatPossessive(true);
        } else {
            token.setRepeatGreedy(true);
            token.setRepeatPossessive(false);
        }
    }

    protected final void syntaxWarn(String message, char c) {
        syntaxWarn(message.replace("<%n>", Character.toString(c)));
    }

    protected final void syntaxWarn(String message) {
        if (Config.USE_WARN) {
            env.reg.warnings.warn(message + ": /" + new String(chars, getBegin(), getEnd()) + "/");
        }
    }
}
